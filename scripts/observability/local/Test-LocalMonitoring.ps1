#Requires -Version 7.4
param([switch]$RequireRemoteUp)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'MonitoringRuntime.Common.ps1')
$stage = 'INSTALLATION'
try {
    $manifest = Get-MonitoringManifest
    Assert-MonitoringInstalledFiles $manifest
    $records = @(Get-MonitoringProcessState)
    if ($records.Count -ne 2) { throw 'MONITORING_PROCESSES_MISSING' }
    foreach ($record in $records) {
        $process = Get-MonitoringOwnedProcess $record
        if ($null -eq $process) { throw 'MONITORING_PROCESS_EXITED' }
        $process.Dispose()
        $port = if ($record.name -eq 'grafana') {3000} else {9090}
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
        if ($listeners.Count -ne 1 -or $listeners[0].LocalAddress -ne '127.0.0.1' -or $listeners[0].OwningProcess -ne $record.processId) { throw 'MONITORING_LISTENER_NOT_PRIVATE' }
    }
    $stage = 'GRAFANA_HEALTH'
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:3000/api/health' -TimeoutSec 5
    # hide_version deliberately omits build metadata from anonymous health responses.
    if ($health.database -ne 'ok' -or (($health.PSObject.Properties.Name -contains 'version') -and $health.version -ne $manifest.grafanaVersion)) { throw 'MONITORING_GRAFANA_HEALTH' }
    $stage = 'DATASOURCE'
    $source = Invoke-RestMethod -Uri 'http://127.0.0.1:3000/api/datasources/uid/grownupsvet-local-prometheus' -TimeoutSec 5
    if ($source.type -ne 'prometheus' -or $source.url -ne 'http://127.0.0.1:9090' -or -not $source.readOnly) { throw 'MONITORING_DATASOURCE_INVALID' }
    $stage = 'DASHBOARD'
    $dashboard = Invoke-RestMethod -Uri 'http://127.0.0.1:3000/api/dashboards/uid/grownupsvet-observability' -TimeoutSec 5
    if ($dashboard.dashboard.editable -or $dashboard.meta.canSave -or $dashboard.dashboard.panels.Count -lt 10) { throw 'MONITORING_DASHBOARD_NOT_READ_ONLY' }
    $stage = 'PROMETHEUS_FLAGS'
    $flags = (Invoke-RestMethod -Uri 'http://127.0.0.1:9090/api/v1/status/flags' -TimeoutSec 5).data
    if ($flags.'web.listen-address' -ne '127.0.0.1:9090' -or $flags.'storage.tsdb.retention.time' -ne '30d' -or
        $flags.'storage.tsdb.retention.size' -ne '512MiB' -or $flags.'web.enable-admin-api' -ne 'false' -or $flags.'web.enable-lifecycle' -ne 'false') { throw 'MONITORING_PROMETHEUS_FLAGS_INVALID' }
    $stage = 'TARGET'
    $targets = (Invoke-RestMethod -Uri 'http://127.0.0.1:9090/api/v1/targets' -TimeoutSec 5).data.activeTargets
    if ($targets.Count -ne 1 -or $targets[0].labels.service_name -ne 'grownupsvet-backend' -or $targets[0].scrapeUrl -ne ($manifest.cloudRunUrl.TrimEnd('/')+'/actuator/prometheus')) { throw 'MONITORING_SCRAPE_TARGET_INVALID' }
    $stage = 'DATASOURCE_QUERY'
    $query = [Uri]::EscapeDataString('up{job="grownupsvet-backend-cloud-run"}')
    $result = Invoke-RestMethod -Uri "http://127.0.0.1:3000/api/datasources/proxy/uid/grownupsvet-local-prometheus/api/v1/query?query=$query" -TimeoutSec 10
    if ($result.status -ne 'success') { throw 'MONITORING_DATASOURCE_QUERY_FAILED' }
    $up = if ($result.data.result.Count -eq 1) {[string]$result.data.result[0].value[1]} else {'PENDING_FIRST_SCRAPE'}
    $prometheusRecord = @($records | Where-Object name -eq 'prometheus')[0]
    $scrapeTimestamp = $targets[0].lastScrape
    $lastScrape = if ($scrapeTimestamp -is [DateTimeOffset]) { $scrapeTimestamp }
        elseif ($scrapeTimestamp -is [DateTime]) { [DateTimeOffset]::new($scrapeTimestamp) }
        else {
            # Go timestamps may contain nanoseconds; .NET represents 100-nanosecond ticks.
            $isoTimestamp = [regex]::Replace([string]$scrapeTimestamp, '(\.\d{7})\d+(Z|[+-]\d{2}:\d{2})$', '$1$2')
            [DateTimeOffset]::Parse($isoTimestamp, [Globalization.CultureInfo]::InvariantCulture)
        }
    $freshScrape = $lastScrape.UtcTicks -gt [long]$prometheusRecord.startTimeUtcTicks -and $lastScrape -gt [DateTimeOffset]::UtcNow.AddSeconds(-90)
    $remoteVerified = $up -eq '1' -and $targets[0].health -eq 'up' -and $freshScrape
    if ($RequireRemoteUp -and -not $remoteVerified) { throw 'MONITORING_REMOTE_SCRAPE_NOT_UP' }
    $report = [ordered]@{status='LOCAL_RUNTIME_VERIFIED';checkedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');loopbackOnly=$true;grafanaEdition=$manifest.grafanaEdition;
        grafanaVersion=$manifest.grafanaVersion;prometheusVersion=$manifest.prometheusVersion;datasourceVerified=$true;dashboardReadOnly=$true;panelCount=$dashboard.dashboard.panels.Count;
        scrapeHealth=$targets[0].health;remoteUp=$up;freshScrape=$freshScrape;remoteMetricsVerified=$remoteVerified;retentionMaximum='30d / 512MB';
        processes=@($records|Select-Object name,processId,startedAtUtc);dashboardUrl='http://127.0.0.1:3000/d/grownupsvet-observability'}
    Write-MonitoringText 'verification.json' (($report|ConvertTo-Json -Depth 5)+"`n")
    $report|ConvertTo-Json -Depth 5
} catch { [Console]::Error.WriteLine("MONITORING_CHECK_FAILED stage=$stage category=$(Get-MonitoringSafeErrorCategory $_); se omitieron diagnósticos y credenciales privados."); exit 1 }
