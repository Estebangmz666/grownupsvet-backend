#Requires -Version 7.4
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot '../observability/local/Test-LocalMonitoring.ps1'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('grownupsvet-monitoring-tests-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($testRoot)
$fixtureScript = Join-Path $testRoot 'Test-LocalMonitoring.ps1'
Copy-Item -LiteralPath $source -Destination $fixtureScript
$originalCulture = [Globalization.CultureInfo]::CurrentCulture
$results = [Collections.Generic.List[object]]::new()

# Execute the real checker with an isolated common boundary: no real files,
# processes, sockets, credentials or HTTP requests are consulted by these tests.
$fixtureCommon = @'
function Get-MonitoringManifest {
    [pscustomobject]@{grafanaVersion='fixture';grafanaEdition='oss';prometheusVersion='fixture';cloudRunUrl='https://fixture-api-abc-uc.a.run.app'}
}
function Assert-MonitoringInstalledFiles { param($Manifest) }
function Get-MonitoringProcessState {
    @([pscustomobject]@{name='prometheus';processId=111;startTimeUtcTicks=$global:MonitoringFixture.Start.UtcTicks;startedAtUtc=$global:MonitoringFixture.Start.ToString('o')},
      [pscustomobject]@{name='grafana';processId=222;startTimeUtcTicks=$global:MonitoringFixture.Start.UtcTicks;startedAtUtc=$global:MonitoringFixture.Start.ToString('o')})
}
function Get-MonitoringOwnedProcess { param($Record) [IO.MemoryStream]::new() }
function Get-NetTCPConnection {
    param($State,$LocalPort,$ErrorAction)
    [pscustomobject]@{LocalAddress='127.0.0.1';LocalPort=$LocalPort;OwningProcess=$(if($LocalPort -eq 9090){111}else{222})}
}
function Invoke-RestMethod {
    param([string]$Uri,[int]$TimeoutSec)
    $global:MonitoringFixture.RequestCount++
    switch -Wildcard ($Uri) {
        'http://127.0.0.1:3000/api/health' { return [pscustomobject]@{database='ok';version='fixture'} }
        'http://127.0.0.1:3000/api/datasources/uid/*' { return [pscustomobject]@{type='prometheus';url='http://127.0.0.1:9090';readOnly=$true} }
        'http://127.0.0.1:3000/api/dashboards/uid/*' { return [pscustomobject]@{dashboard=[pscustomobject]@{editable=$false;panels=@(1..10)};meta=[pscustomobject]@{canSave=$false}} }
        'http://127.0.0.1:9090/api/v1/status/flags' { return [pscustomobject]@{data=@{'web.listen-address'='127.0.0.1:9090';'storage.tsdb.retention.time'='30d';'storage.tsdb.retention.size'='512MiB';'web.enable-admin-api'='false';'web.enable-lifecycle'='false'}} }
        'http://127.0.0.1:9090/api/v1/targets' { return [pscustomobject]@{data=[pscustomobject]@{activeTargets=@([pscustomobject]@{labels=@{service_name='grownupsvet-backend'};scrapeUrl='https://fixture-api-abc-uc.a.run.app/actuator/prometheus';lastScrape=$global:MonitoringFixture.Timestamp;health=$global:MonitoringFixture.Health})}} }
        'http://127.0.0.1:3000/api/datasources/proxy/uid/*' { return [pscustomobject]@{status='success';data=[pscustomobject]@{result=@([pscustomobject]@{value=@(0,$global:MonitoringFixture.Up)})}} }
        default { throw 'UNEXPECTED_HTTP_REQUEST' }
    }
}
function Write-MonitoringText { param($RelativePath,$Content) $global:MonitoringFixture.Report=$Content }
function Get-MonitoringSafeErrorCategory { param($Failure) 'CONTROLLED_FIXTURE_REJECTION' }
'@
[IO.File]::WriteAllText((Join-Path $testRoot 'MonitoringRuntime.Common.ps1'), $fixtureCommon)

function Invoke-MonitoringFixture {
    param([string]$Name,$Timestamp,[bool]$ShouldPass,[bool]$RequireRemote=$true,
          [DateTimeOffset]$Start=[DateTimeOffset]::UtcNow.AddHours(-1),[string]$Health='up',[string]$Up='1')
    $global:MonitoringFixture = @{Timestamp=$Timestamp;Start=$Start;Health=$Health;Up=$Up;Report=$null;RequestCount=0}
    $global:LASTEXITCODE=0
    $null = & $fixtureScript -RequireRemoteUp:$RequireRemote 2>$null
    $passed = $LASTEXITCODE -eq 0
    if ($passed -ne $ShouldPass) { throw "MONITORING_TEST_UNEXPECTED_RESULT: $Name" }
    if ($global:MonitoringFixture.RequestCount -ne 6) { throw "MONITORING_TEST_UNEXPECTED_REQUEST_COUNT: $Name" }
    if ($ShouldPass) {
        $report=$global:MonitoringFixture.Report | ConvertFrom-Json
        if ($report.remoteMetricsVerified -ne $RequireRemote) { throw "MONITORING_TEST_INCORRECT_REMOTE_CLAIM: $Name" }
    } elseif ($null -ne $global:MonitoringFixture.Report) { throw "MONITORING_TEST_REJECTED_REPORT_WRITTEN: $Name" }
    $results.Add([pscustomobject]@{name=$Name;expected=$(if($ShouldPass){'ACCEPT'}else{'REJECT'});passed=$true})
}

try {
    $instant=[DateTimeOffset]::UtcNow.AddSeconds(-5)
    foreach ($culture in @('es-CO','en-US')) {
        [Globalization.CultureInfo]::CurrentCulture=[Globalization.CultureInfo]::GetCultureInfo($culture)
        Invoke-MonitoringFixture "$culture ISO timestamp" $instant.ToString('o',[Globalization.CultureInfo]::InvariantCulture) $true
        Invoke-MonitoringFixture "$culture UTC DateTime" $instant.UtcDateTime $true
        Invoke-MonitoringFixture "$culture local DateTime" $instant.LocalDateTime $true
        Invoke-MonitoringFixture "$culture offset DateTimeOffset" $instant.ToOffset([TimeSpan]::FromHours(-5)) $true
        $nanosecondTimestamp = $instant.UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss.fffffff",[Globalization.CultureInfo]::InvariantCulture)+'89Z'
        Invoke-MonitoringFixture "$culture UTC nanosecond timestamp" $nanosecondTimestamp $true
        $offsetNanosecondTimestamp = $instant.ToOffset([TimeSpan]::FromHours(-5)).ToString("yyyy-MM-dd'T'HH:mm:ss.fffffff",[Globalization.CultureInfo]::InvariantCulture)+'89-05:00'
        Invoke-MonitoringFixture "$culture offset nanosecond timestamp" $offsetNanosecondTimestamp $true
    }
    Invoke-MonitoringFixture 'Invalid timestamp rejected' 'not-a-timestamp' $false
    Invoke-MonitoringFixture 'Stale scrape rejected' ([DateTimeOffset]::UtcNow.AddMinutes(-5)) $false
    Invoke-MonitoringFixture 'Sample before current Prometheus start rejected' $instant $false -Start ([DateTimeOffset]::UtcNow.AddSeconds(-1))
    Invoke-MonitoringFixture 'Down target rejected' $instant $false -Health 'down'
    Invoke-MonitoringFixture 'Zero up rejected' $instant $false -Up '0'
    Invoke-MonitoringFixture 'Optional remote check reports stale sample honestly' ([DateTimeOffset]::UtcNow.AddMinutes(-5)) $true -RequireRemote $false
    [pscustomobject]@{status='PASSED';testCount=$results.Count;realHttpRequests=0;credentialsRead=$false;cases=@($results)} | ConvertTo-Json -Depth 5
} finally {
    [Globalization.CultureInfo]::CurrentCulture=$originalCulture
    Remove-Variable -Name MonitoringFixture -Scope Global -ErrorAction SilentlyContinue
    $resolvedRoot=[IO.Path]::GetFullPath($testRoot)
    $expectedParent=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
    if ($resolvedRoot.StartsWith($expectedParent,[StringComparison]::OrdinalIgnoreCase) -and [IO.Path]::GetFileName($resolvedRoot).StartsWith('grownupsvet-monitoring-tests-')) {
        Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
    }
}
