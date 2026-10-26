#Requires -Version 7.4
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'MonitoringRuntime.Common.ps1')
$checks = [Collections.Generic.List[string]]::new()
$fixtureDirectory = 'fixtures\guards-' + [Guid]::NewGuid().ToString('N')
$fixtureFile = "$fixtureDirectory\synthetic-secret.txt"
$listener = $null
function Assert-Rejected {
    param([string]$Name, [scriptblock]$Action, [string]$Expected)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $_.Exception.Message -ceq $Expected }
    if (-not $rejected) { throw 'MONITORING_GUARD_EXPECTATION_FAILED' }
    $checks.Add($Name)
}
try {
    foreach ($url in @('http://example.run.app','https://example.org','https://x.a.run.app/api','https://x.a.run.app/?token=fake','https://fake:fake@x.a.run.app')) {
        Assert-Rejected 'invalid_target' { Assert-MonitoringTarget $url } 'MONITORING_TARGET_INVALID'
    }
    if ((Assert-MonitoringTarget 'https://service.a.run.app') -ne 'service.a.run.app') { throw 'MONITORING_VALID_TARGET_FAILED' }
    $checks.Add('valid_https_target')
    foreach ($value in @('',('a'*42),('a'*43+'='),('a'*42+'+'))) {
        Assert-Rejected 'invalid_token' { Assert-MonitoringTokenFormat $value } 'MONITORING_TOKEN_FORMAT'
    }
    Assert-MonitoringTokenFormat (New-MonitoringRandomValue)
    $checks.Add('random_token_format')
    Assert-Rejected 'path_outside_private_root' {Get-MonitoringPath '..\backups'} 'MONITORING_PATH_OUTSIDE_ROOT'
    $manifest = Get-MonitoringManifest
    $fakeRecord = [pscustomobject]@{name='grafana';processId=$PID;executable=(Get-MonitoringPath $manifest.grafanaExecutable);startTimeUtcTicks=1}
    Assert-Rejected 'foreign_pid_never_owned' {Get-MonitoringOwnedProcess $fakeRecord} 'MONITORING_PROCESS_IDENTITY_MISMATCH'
    $fakeRecord.executable = (Get-Process -Id $PID).Path
    Assert-Rejected 'foreign_executable_never_owned' {Get-MonitoringOwnedProcess $fakeRecord} 'MONITORING_PROCESS_EXECUTABLE_MISMATCH'
    $modifiedManifest = $manifest | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    $modifiedManifest.artifacts[0].sha256='0'*64
    Assert-Rejected 'changed_artifact_not_blessed' {Assert-MonitoringInstalledFiles $modifiedManifest} 'MONITORING_ARTIFACT_HASH_MISMATCH'
    [void](Set-MonitoringPrivateDirectory 'fixtures')
    [void](Set-MonitoringPrivateDirectory $fixtureDirectory)
    Write-MonitoringText $fixtureFile ('a'*43)
    Set-MonitoringPrivateFile $fixtureFile
    Assert-MonitoringStoredRandomValue $fixtureFile
    $checks.Add('private_synthetic_file_valid')
    $file = [IO.FileInfo]::new((Get-MonitoringPath $fixtureFile))
    $security = [IO.FileSystemAclExtensions]::GetAccessControl($file,[Security.AccessControl.AccessControlSections]::Access)
    $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new('S-1-1-0'),'Read','Allow'))
    [IO.FileSystemAclExtensions]::SetAccessControl($file,$security)
    Assert-Rejected 'explicit_other_reader_rejected' {Assert-MonitoringPrivateFile $fixtureFile} 'MONITORING_SECRET_ACL_INVALID'
    Set-MonitoringPrivateFile $fixtureFile
    Assert-MonitoringPrivateFile $fixtureFile
    $checks.Add('explicit_other_reader_removed')
    Write-MonitoringText $fixtureFile ''
    Assert-Rejected 'empty_secret_rejected' {Assert-MonitoringStoredRandomValue $fixtureFile} 'MONITORING_SECRET_FORMAT_INVALID'
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $listener.Start()
    $occupiedPort = $listener.LocalEndpoint.Port
    Assert-Rejected 'occupied_port_rejected' {Assert-MonitoringPortFree $occupiedPort} 'MONITORING_PORT_OCCUPIED'
    Assert-MonitoringInstalledFiles $manifest
    $checks.Add('real_installation_unchanged')
    $report = [ordered]@{status='PASSED';checkedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');checksPassed=$checks.Count;checks=@($checks);foreignProcessesStopped=0;realCredentialsChanged=$false}
    Write-MonitoringText 'guard-verification.json' (($report|ConvertTo-Json -Depth 4)+"`n")
    $report|ConvertTo-Json -Depth 4
} catch { [Console]::Error.WriteLine("MONITORING_GUARDS_FAILED category=$(Get-MonitoringSafeErrorCategory $_); detalles privados omitidos."); exit 1 }
finally {
    if ($null -ne $listener) {$listener.Stop()}
    $path = Get-MonitoringPath $fixtureFile
    if (Test-Path -LiteralPath $path -PathType Leaf) {Remove-Item -LiteralPath $path -Force}
    $directory = Get-MonitoringPath $fixtureDirectory
    if (Test-Path -LiteralPath $directory -PathType Container) {Remove-Item -LiteralPath $directory}
}
