#Requires -Version 7.4
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'MonitoringRuntime.Common.ps1')
$started = @()
$retained = @()
$maximumStartupWaitSeconds = 90
try {
    $manifest = Get-MonitoringManifest
    Assert-MonitoringInstalledFiles $manifest
    foreach ($record in Get-MonitoringProcessState) {
        $process = Get-MonitoringOwnedProcess $record
        if ($null -ne $process) { $process.Dispose(); $retained += $record }
    }
    if (@($retained | Group-Object name | Where-Object Count -gt 1).Count -ne 0) { throw 'MONITORING_DUPLICATE_PROCESS_STATE' }
    foreach ($definition in @(@{name='prometheus';port=9090},@{name='grafana';port=3000})) {
        $existing = @($retained | Where-Object name -eq $definition.name)
        if ($existing.Count -eq 0) { Assert-MonitoringPortFree $definition.port }
        else {
            $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $definition.port -ErrorAction SilentlyContinue)
            if ($listeners.Count -ne 1 -or $listeners[0].LocalAddress -ne '127.0.0.1' -or $listeners[0].OwningProcess -ne $existing[0].processId) { throw 'MONITORING_EXISTING_LISTENER_MISMATCH' }
        }
    }
    if (-not (@($retained | Select-Object -ExpandProperty name) -contains 'prometheus')) {
        $executable = Get-MonitoringPath $manifest.prometheusExecutable
        $arguments = @(('--config.file='+(Get-MonitoringPath 'config\prometheus.yaml')),('--storage.tsdb.path='+(Get-MonitoringPath 'data\prometheus')),
            '--storage.tsdb.retention.time=30d','--storage.tsdb.retention.size=512MB','--web.listen-address=127.0.0.1:9090','--log.level=warn')
        $started += Start-MonitoringOwnedProcess 'prometheus' $executable $arguments ([IO.Path]::GetDirectoryName($executable))
        Write-MonitoringProcessState @($retained + $started)
    }
    if (-not (@($retained | Select-Object -ExpandProperty name) -contains 'grafana')) {
        $executable = Get-MonitoringPath $manifest.grafanaExecutable
        $grafanaHomeDirectory = [IO.Path]::GetDirectoryName([IO.Path]::GetDirectoryName($executable))
        $started += Start-MonitoringOwnedProcess 'grafana' $executable @('server','--homepath',$grafanaHomeDirectory,'--config',(Get-MonitoringPath 'config\grafana.ini')) $grafanaHomeDirectory
        Write-MonitoringProcessState @($retained + $started)
    }
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($maximumStartupWaitSeconds)
    $ready = $false
    do {
        try {
            $prometheus = Invoke-WebRequest -Uri 'http://127.0.0.1:9090/-/ready' -TimeoutSec 2
            $grafana = Invoke-RestMethod -Uri 'http://127.0.0.1:3000/api/health' -TimeoutSec 2
            $ready = $prometheus.StatusCode -eq 200 -and $grafana.database -eq 'ok'
        } catch { $ready=$false }
        if (-not $ready) { Start-Sleep -Milliseconds 500 }
    } while (-not $ready -and [DateTimeOffset]::UtcNow -lt $deadline)
    if (-not $ready) { throw 'MONITORING_START_TIMEOUT' }
    $global:LASTEXITCODE = 0
    & (Join-Path $PSScriptRoot 'Test-LocalMonitoring.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'MONITORING_VERIFICATION_FAILED' }
} catch {
    foreach ($record in $started) { try { Stop-MonitoringOwnedProcess $record } catch {} }
    try { Write-MonitoringProcessState $retained } catch {}
    [Console]::Error.WriteLine("MONITORING_START_FAILED category=$(Get-MonitoringSafeErrorCategory $_); no se detuvieron procesos ajenos ni se mostraron secretos.")
    exit 1
}
