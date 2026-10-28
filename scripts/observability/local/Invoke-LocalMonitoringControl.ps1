#Requires -Version 7.4
param(
    [Parameter(Mandatory)][ValidateSet('Start','Stop','OpenPanel')][string]$Action,
    [switch]$NoBrowser,
    [switch]$NoDialog
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$mutex = $null
$locked = $false
$stateFile = $null
$status = 'FAILED'
$category = 'CONTROL_NOT_COMPLETED'
try {
    $privateRoot = [IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.grownupsvet\monitoring'))
    $versionRoot = Join-Path $privateRoot 'controls\versions'
    $bundle = [IO.Path]::GetFullPath($PSScriptRoot)
    if (-not $bundle.StartsWith($versionRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($bundle) -cnotmatch '^[a-f0-9]{16}$') { throw 'MONITORING_CONTROL_BUNDLE_LOCATION' }
    for ($current=$bundle; $current; $current=[IO.Path]::GetDirectoryName($current)) {
        if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'MONITORING_CONTROL_REPARSE_POINT' }
    }
    $candidateStateFile = Join-Path $privateRoot 'controls\last-action.json'
    $stateItem = Get-Item -LiteralPath $candidateStateFile -Force -ErrorAction SilentlyContinue
    if ($null -ne $stateItem -and ($stateItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'MONITORING_CONTROL_STATE_REPARSE_POINT' }
    $stateFile = $candidateStateFile
    $manifest = Get-Content -LiteralPath (Join-Path $bundle 'bundle-manifest.json') -Raw | ConvertFrom-Json
    $expected = @('MonitoringRuntime.Common.ps1','Install-LocalMonitoring.ps1','Start-LocalMonitoring.ps1','Stop-LocalMonitoring.ps1','Test-LocalMonitoring.ps1','Test-LocalMonitoringGuards.ps1','Invoke-LocalMonitoringControl.ps1')
    if ($manifest.formatVersion -ne 1 -or $manifest.files.Count -ne $expected.Count) { throw 'MONITORING_CONTROL_MANIFEST_INVALID' }
    foreach ($file in $manifest.files) {
        if ($file.name -cnotin $expected -or $file.sha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'MONITORING_CONTROL_MANIFEST_INVALID' }
        if ((Get-FileHash -LiteralPath (Join-Path $bundle $file.name) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $file.sha256) { throw 'MONITORING_CONTROL_HASH_MISMATCH' }
    }
    if (@($manifest.files.name | Sort-Object -Unique).Count -ne $expected.Count) { throw 'MONITORING_CONTROL_MANIFEST_INVALID' }
    if (-not [string]::Equals([Environment]::ProcessPath,$manifest.powerShellExecutable,[StringComparison]::OrdinalIgnoreCase) -or
        (Get-FileHash -LiteralPath ([Environment]::ProcessPath) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $manifest.powerShellSha256) { throw 'MONITORING_CONTROL_ENGINE_CHANGED' }
    $mutexName = 'Local\GrownupsVetMonitoringControl-'+[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $mutex = [Threading.Mutex]::new($false,$mutexName)
    try { $locked = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $locked=$true }
    if (-not $locked) { throw 'MONITORING_CONTROL_BUSY' }
    . (Join-Path $bundle 'MonitoringRuntime.Common.ps1')
    $global:LASTEXITCODE = 0
    switch ($Action) {
        'Start' {
            $privateOutput = & (Join-Path $bundle 'Start-LocalMonitoring.ps1') 2>&1
            if ($LASTEXITCODE -ne 0) { throw 'MONITORING_CONTROL_START_FAILED' }
            $status = 'MONITORING_STARTED'
        }
        'Stop' {
            $privateOutput = & (Join-Path $bundle 'Stop-LocalMonitoring.ps1') 2>&1
            if ($LASTEXITCODE -ne 0) { throw 'MONITORING_CONTROL_STOP_FAILED' }
            $status = 'MONITORING_STOPPED'
        }
        'OpenPanel' {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:3000/api/health' -TimeoutSec 3
            if ($health.database -ne 'ok') { throw 'MONITORING_CONTROL_PANEL_UNAVAILABLE' }
            $status = 'PANEL_READY'
        }
    }
    $privateOutput = $null
    if ($Action -in @('Start','OpenPanel') -and -not $NoBrowser) {
        # The user explicitly requested a visible panel through this desktop button.
        Start-Process -FilePath 'http://127.0.0.1:3000/d/grownupsvet-observability'
    }
    $category = 'NONE'
} catch {
    $status = 'FAILED'
    $category = if ($_.Exception.Message -cmatch '^MONITORING_[A-Z_]+$') { $_.Exception.Message } else { $_.Exception.GetType().Name }
} finally {
    if ($null -ne $stateFile) {
        try {
            # Recheck the leaf immediately before writing, including failures before Common was loaded.
            $stateItem = Get-Item -LiteralPath $stateFile -Force -ErrorAction SilentlyContinue
            if ($null -ne $stateItem -and ($stateItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'MONITORING_CONTROL_STATE_REPARSE_POINT' }
            $report = [ordered]@{action=$Action;status=$status;category=$category;checkedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');scope='LOCAL_MONITORING_ONLY';cloudRunChanged=$false}
            [IO.File]::WriteAllText($stateFile,($report|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
        } catch { $status='FAILED'; $category='MONITORING_CONTROL_STATE_WRITE_FAILED' }
    }
    if ($locked) { $mutex.ReleaseMutex() }
    if ($null -ne $mutex) { $mutex.Dispose() }
}
if ($status -eq 'FAILED') {
    if (-not $NoDialog) {
        try {
            $notice = New-Object -ComObject WScript.Shell
            [void]$notice.Popup('No se pudo completar la acción de monitoreo. Revisa el estado local o vuelve a intentarlo. La API permanece disponible.',0,'GrownupsVet · monitoreo',16)
            [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($notice)
        } catch { [Console]::Error.WriteLine('No se pudo completar el control de monitoreo. La API permanece disponible.') }
    }
    [Console]::Error.WriteLine("MONITORING_CONTROL_FAILED category=$category; detalles privados omitidos.")
    exit 1
}
[pscustomobject]@{status=$status;scope='LOCAL_MONITORING_ONLY';apiChanged=$false}|ConvertTo-Json
