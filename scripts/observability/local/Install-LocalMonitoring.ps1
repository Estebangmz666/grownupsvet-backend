#Requires -Version 7.4
param(
    [string]$CloudRunUrl = 'https://grownupsvet-backend-w5glrcsyda-uc.a.run.app',
    [string]$PrometheusToolsDirectory = (Join-Path $PSScriptRoot '..\..\..\..\output\backend-cierre-2026-10-25\observability-tools')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'MonitoringRuntime.Common.ps1')
$stage = 'PREFLIGHT'
try {
    $target = Assert-MonitoringTarget $CloudRunUrl
    if (Test-Path -LiteralPath (Get-MonitoringPath 'installation.json')) {
        # Reinstallation must not bless a modified executable/configuration with a new hash.
        Assert-MonitoringInstalledFiles (Get-MonitoringManifest)
    } elseif ((Test-Path -LiteralPath (Get-MonitoringPath 'bin\prometheus-3.15.1.windows-amd64')) -or
              (Test-Path -LiteralPath (Get-MonitoringPath 'bin\grafana-13.2.4'))) { throw 'MONITORING_UNTRACKED_TOOL_DIRECTORY' }
    foreach ($record in Get-MonitoringProcessState) {
        $process = Get-MonitoringOwnedProcess $record
        if ($null -ne $process) { $process.Dispose(); throw 'MONITORING_STOP_BEFORE_INSTALL' }
    }
    foreach ($directory in @('','downloads','bin','config','secrets','data','data\prometheus','data\grafana','logs','provisioning','provisioning\datasources','provisioning\dashboards','dashboards')) {
        [void](Set-MonitoringPrivateDirectory $directory)
    }
    $prometheusVersion = '3.15.1'; $prometheusHash = 'ba25b5e590520cb9fb028edef99bdc8523244d2d489b8c629e0d453d94fb030b'
    $grafanaVersion = '13.2.4'; $grafanaHash = '3e005ff50fe3bb6b8cb428ef127636fac4ee676bf9b6d819c6a2297d21f4bcbd'
    $stage = 'VERIFIED_TOOLS'
    $prometheusArchive = Join-Path $PrometheusToolsDirectory 'prometheus-3.15.1.windows-amd64.zip'
    if (-not (Test-Path -LiteralPath $prometheusArchive -PathType Leaf) -or (Get-MonitoringFileHash $prometheusArchive) -cne $prometheusHash) { throw 'MONITORING_PROMETHEUS_ARCHIVE_INVALID' }
    $prometheusHome = Get-MonitoringPath 'bin\prometheus-3.15.1.windows-amd64'
    if (-not (Test-Path -LiteralPath $prometheusHome)) { Expand-Archive -LiteralPath $prometheusArchive -DestinationPath (Get-MonitoringPath 'bin') }
    $grafanaArchive = Get-MonitoringPath 'downloads\grafana_13.2.4_38252365904_windows_amd64.tar.gz'
    $grafanaSource = 'https://dl.grafana.com/grafana/release/13.2.4/grafana_13.2.4_38252365904_windows_amd64.tar.gz'
    if (-not (Test-Path -LiteralPath $grafanaArchive -PathType Leaf)) { Invoke-WebRequest -Uri $grafanaSource -OutFile $grafanaArchive -TimeoutSec 600 }
    if ((Get-MonitoringFileHash $grafanaArchive) -cne $grafanaHash) { throw 'MONITORING_GRAFANA_ARCHIVE_INVALID' }
    $grafanaHome = Get-MonitoringPath 'bin\grafana-13.2.4'
    if (-not (Test-Path -LiteralPath $grafanaHome)) {
        $entries = @(& tar -tzf $grafanaArchive 2>$null)
        if ($LASTEXITCODE -ne 0 -or $entries.Count -lt 10) { throw 'MONITORING_GRAFANA_ARCHIVE_UNREADABLE' }
        foreach ($entry in $entries) {
            if ($entry -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$)|\\)') { throw 'MONITORING_ARCHIVE_PATH_INVALID' }
        }
        & tar -xzf $grafanaArchive -C (Get-MonitoringPath 'bin') 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'MONITORING_GRAFANA_EXTRACTION_FAILED' }
    }
    $prometheusExecutable = Join-Path $prometheusHome 'prometheus.exe'
    $promtoolExecutable = Join-Path $prometheusHome 'promtool.exe'
    $grafanaExecutable = Join-Path $grafanaHome 'bin\grafana.exe'
    foreach ($executable in @($prometheusExecutable,$promtoolExecutable,$grafanaExecutable)) {
        if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) { throw 'MONITORING_TOOL_MISSING' }
    }
    $stage = 'PRIVATE_CREDENTIALS'
    $tokenFile = Get-MonitoringPath 'secrets\scrape-token.txt'
    $backendEnvironment = Get-MonitoringPath 'secrets\backend-monitoring.env'
    foreach ($existingSecret in @('scrape-token.txt','backend-monitoring.env','grafana-admin-password.txt','grafana-secret-key.txt')) {
        if (Test-Path -LiteralPath (Get-MonitoringPath "secrets\$existingSecret")) { Set-MonitoringPrivateFile "secrets\$existingSecret" }
    }
    if (-not (Test-Path -LiteralPath $tokenFile)) {
        if (Test-Path -LiteralPath $backendEnvironment) { throw 'MONITORING_EXISTING_TOKEN_MISSING' }
        Write-MonitoringText 'secrets\scrape-token.txt' (New-MonitoringRandomValue)
    }
    $token = [IO.File]::ReadAllText($tokenFile)
    try {
        Assert-MonitoringTokenFormat $token
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($token))).ToLowerInvariant()
        $content = "MONITORING_TOKEN_SHA256=$hash`n"
        if ((Test-Path -LiteralPath $backendEnvironment) -and [IO.File]::ReadAllText($backendEnvironment) -cne $content) { throw 'MONITORING_EXISTING_TOKEN_HASH_MISMATCH' }
        Write-MonitoringText 'secrets\backend-monitoring.env' $content
    } finally { $token=$null; $hash=$null; $content=$null }
    foreach ($secret in @('grafana-admin-password.txt','grafana-secret-key.txt')) {
        if (-not (Test-Path -LiteralPath (Get-MonitoringPath "secrets\$secret"))) { Write-MonitoringText "secrets\$secret" (New-MonitoringRandomValue) }
    }
    foreach ($secret in @('scrape-token.txt','backend-monitoring.env','grafana-admin-password.txt','grafana-secret-key.txt')) { Set-MonitoringPrivateFile "secrets\$secret" }
    $stage = 'CONFIGURATION'
    $tokenPath = $tokenFile.Replace('\','/')
    Write-MonitoringText 'config\prometheus.yaml' @"
global:
  scrape_interval: 60s
  evaluation_interval: 60s
scrape_configs:
  - job_name: grownupsvet-backend-cloud-run
    scheme: https
    metrics_path: /actuator/prometheus
    scrape_interval: 60s
    scrape_timeout: 15s
    follow_redirects: false
    authorization:
      type: Bearer
      credentials_file: '$tokenPath'
    tls_config:
      min_version: TLS12
      insecure_skip_verify: false
    static_configs:
      - targets: ['$target']
        labels:
          service_name: grownupsvet-backend
"@
    $paths = @{}
    foreach ($relative in @('data\grafana','logs','provisioning','dashboards','secrets\grafana-admin-password.txt','secrets\grafana-secret-key.txt')) { $paths[$relative] = (Get-MonitoringPath $relative).Replace('\','/') }
    $adminProvider = '$__file{' + $paths['secrets\grafana-admin-password.txt'] + '}'
    $keyProvider = '$__file{' + $paths['secrets\grafana-secret-key.txt'] + '}'
    Write-MonitoringText 'config\grafana.ini' @"
[paths]
data = $($paths['data\grafana'])
logs = $($paths['logs'])
provisioning = $($paths['provisioning'])
[server]
protocol = http
http_addr = 127.0.0.1
http_port = 3000
domain = 127.0.0.1
root_url = http://127.0.0.1:3000/
router_logging = false
[security]
admin_user = local-admin
admin_password = $adminProvider
secret_key = $keyProvider
disable_gravatar = true
cookie_samesite = strict
allow_embedding = false
data_source_proxy_whitelist = 127.0.0.1:9090
[users]
allow_sign_up = false
allow_org_create = false
viewers_can_edit = false
[auth.anonymous]
enabled = true
org_name = Main Org.
org_role = Viewer
hide_version = true
[analytics]
reporting_enabled = false
check_for_updates = false
check_for_plugin_updates = false
feedback_links_enabled = false
[news]
news_feed_enabled = false
[plugins]
plugin_admin_enabled = false
preinstall_disabled = true
[log]
mode = file
level = warn
[log.file]
log_rotate = true
max_size_shift = 23
max_days = 7
"@
    Write-MonitoringText 'provisioning\datasources\prometheus.yaml' @'
apiVersion: 1
datasources:
  - name: GrownupsVet local Prometheus
    uid: grownupsvet-local-prometheus
    type: prometheus
    access: proxy
    url: http://127.0.0.1:9090
    isDefault: true
    editable: false
    jsonData:
      httpMethod: POST
      prometheusType: Prometheus
      prometheusVersion: 3.15.1
      timeInterval: 60s
'@
    Write-MonitoringText 'provisioning\dashboards\grownupsvet.yaml' @"
apiVersion: 1
providers:
  - name: GrownupsVet local
    orgId: 1
    folder: GrownupsVet
    type: file
    disableDeletion: true
    allowUiUpdates: false
    updateIntervalSeconds: 60
    options:
      path: $($paths['dashboards'])
"@
    $dashboardSource = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\grownupsvet-dashboard.json'))
    $dashboard = [IO.File]::ReadAllText($dashboardSource).Replace('${datasource}', 'grownupsvet-local-prometheus') | ConvertFrom-Json
    $dashboard.editable = $false
    $dashboard.refresh = '60s'
    $dashboard.templating.list = @($dashboard.templating.list | Where-Object {$_.name -ne 'datasource'})
    foreach ($variable in $dashboard.templating.list) {
        if ($variable.name -eq 'service') { $variable | Add-Member -NotePropertyName current -NotePropertyValue @{text='grownupsvet-backend';value='grownupsvet-backend'} -Force }
    }
    foreach ($panel in $dashboard.panels) {
        if ($panel.PSObject.Properties.Name -contains 'targets') {
            foreach ($query in $panel.targets) {
                if ($query.expr.StartsWith('up{')) { $query.expr = 'up{service_name=~"$service"}'; $query.legendFormat = '{{instance}}'; $panel.title = 'Scrape Cloud Run del servicio' }
            }
        }
    }
    Write-MonitoringText 'dashboards\grownupsvet-dashboard.json' (($dashboard | ConvertTo-Json -Depth 80) + "`n")
    $validation = & $promtoolExecutable check config (Get-MonitoringPath 'config\prometheus.yaml') 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'MONITORING_PROMETHEUS_CONFIG_INVALID' }
    $validation=$null
    $stage = 'MANIFEST'
    $relativeFiles = @('bin\prometheus-3.15.1.windows-amd64\prometheus.exe','bin\prometheus-3.15.1.windows-amd64\promtool.exe','bin\grafana-13.2.4\bin\grafana.exe',
        'config\prometheus.yaml','config\grafana.ini','provisioning\datasources\prometheus.yaml','provisioning\dashboards\grownupsvet.yaml','dashboards\grownupsvet-dashboard.json')
    $artifacts = @($relativeFiles | ForEach-Object { @{relativePath=$_;sha256=(Get-MonitoringFileHash (Get-MonitoringPath $_))} })
    $manifest = [ordered]@{formatVersion=1;installedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');grafanaEdition='oss';grafanaVersion=$grafanaVersion;grafanaArchiveSha256=$grafanaHash;
        grafanaSource=$grafanaSource;prometheusVersion=$prometheusVersion;prometheusArchiveSha256=$prometheusHash;prometheusSource='https://github.com/prometheus/prometheus/releases/tag/v3.15.1';
        cloudRunUrl=$CloudRunUrl;prometheusExecutable=$relativeFiles[0];grafanaExecutable=$relativeFiles[2];dashboardSourceSha256=(Get-MonitoringFileHash $dashboardSource);artifacts=$artifacts}
    Write-MonitoringText 'installation.json' (($manifest | ConvertTo-Json -Depth 6) + "`n")
    Assert-MonitoringInstalledFiles (Get-MonitoringManifest)
    [pscustomobject]@{status='INSTALLED';runtimeDirectory=$script:MonitoringRoot;grafanaEdition='oss';grafanaVersion=$grafanaVersion;prometheusVersion=$prometheusVersion;
        backendEnvironmentFile=$backendEnvironment;dashboardUrl='http://127.0.0.1:3000/d/grownupsvet-observability';remoteScrapeVerified=$false} | ConvertTo-Json
} catch { [Console]::Error.WriteLine("MONITORING_INSTALL_FAILED stage=$stage category=$(Get-MonitoringSafeErrorCategory $_); detalles privados omitidos."); exit 1 }
