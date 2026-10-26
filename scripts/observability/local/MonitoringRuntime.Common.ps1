#Requires -Version 7.4
Set-StrictMode -Version Latest
$script:MonitoringRoot = [IO.Path]::GetFullPath((Join-Path $env:USERPROFILE '.grownupsvet\monitoring'))

function Get-MonitoringPath {
    param([string]$RelativePath = '')
    $path = [IO.Path]::GetFullPath((Join-Path $script:MonitoringRoot $RelativePath))
    if ($path -ne $script:MonitoringRoot -and -not $path.StartsWith($script:MonitoringRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'MONITORING_PATH_OUTSIDE_ROOT' }
    for ($current = $path; $current; $current = [IO.Path]::GetDirectoryName($current)) {
        if (Test-Path -LiteralPath $current) {
            if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'MONITORING_REPARSE_POINT' }
        }
    }
    return $path
}

function Set-MonitoringPrivateDirectory {
    param([string]$RelativePath = '')
    $path = Get-MonitoringPath $RelativePath
    [void][IO.Directory]::CreateDirectory($path)
    $directory = [IO.DirectoryInfo]::new($path)
    $security = [IO.FileSystemAclExtensions]::GetAccessControl($directory, [Security.AccessControl.AccessControlSections]::Access)
    $security.SetAccessRuleProtection($true, $false)
    foreach ($rule in $security.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) { [void]$security.RemoveAccessRuleSpecific($rule) }
    $inherit = [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
    $propagate = [Security.AccessControl.PropagationFlags]::None
    foreach ($identity in @([Security.Principal.WindowsIdentity]::GetCurrent().User, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', $inherit, $propagate, 'Allow'))
    }
    [IO.FileSystemAclExtensions]::SetAccessControl($directory, $security)
    return $path
}

function Write-MonitoringText {
    param([string]$RelativePath, [string]$Content)
    $path = Get-MonitoringPath $RelativePath
    [IO.File]::WriteAllText($path, $Content, [Text.UTF8Encoding]::new($false))
}

function Set-MonitoringPrivateFile {
    param([string]$RelativePath)
    $path = Get-MonitoringPath $RelativePath
    $file = [IO.FileInfo]::new($path)
    $security = [IO.FileSystemAclExtensions]::GetAccessControl($file, [Security.AccessControl.AccessControlSections]::Access)
    $security.SetAccessRuleProtection($true, $false)
    foreach ($rule in $security.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) { [void]$security.RemoveAccessRuleSpecific($rule) }
    foreach ($identity in @([Security.Principal.WindowsIdentity]::GetCurrent().User, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'Allow'))
    }
    [IO.FileSystemAclExtensions]::SetAccessControl($file, $security)
}

function Get-MonitoringSafeErrorCategory {
    param($Failure)
    $message = [string]$Failure.Exception.Message
    if ($message -cmatch '^MONITORING_[A-Z_]+$') { return $message }
    return $Failure.Exception.GetType().Name
}

function Get-MonitoringFileHash {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function New-MonitoringRandomValue {
    return [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).TrimEnd('=').Replace('+','-').Replace('/','_')
}

function Assert-MonitoringTokenFormat {
    param([string]$Token)
    if ($Token -cnotmatch '^[A-Za-z0-9_-]{43}$') { throw 'MONITORING_TOKEN_FORMAT' }
}

function Assert-MonitoringPrivateFile {
    param([string]$RelativePath)
    $file = [IO.FileInfo]::new((Get-MonitoringPath $RelativePath))
    $security = [IO.FileSystemAclExtensions]::GetAccessControl($file, [Security.AccessControl.AccessControlSections]::Access)
    $rules = @($security.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    $allowed = @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value, 'S-1-5-18')
    if (-not $security.AreAccessRulesProtected -or $rules.Count -ne 2) { throw 'MONITORING_SECRET_ACL_INVALID' }
    foreach ($rule in $rules) {
        if ($rule.IdentityReference.Value -notin $allowed -or $rule.IsInherited -or $rule.AccessControlType -ne 'Allow' -or
            ($rule.FileSystemRights -band [Security.AccessControl.FileSystemRights]::FullControl) -ne [Security.AccessControl.FileSystemRights]::FullControl) { throw 'MONITORING_SECRET_ACL_INVALID' }
    }
}

function Assert-MonitoringStoredRandomValue {
    param([string]$RelativePath)
    Assert-MonitoringPrivateFile $RelativePath
    $path = Get-MonitoringPath $RelativePath
    if ((Get-Item -LiteralPath $path).Length -ne 43) { throw 'MONITORING_SECRET_FORMAT_INVALID' }
    $value = [IO.File]::ReadAllText($path)
    try { Assert-MonitoringTokenFormat $value } finally { $value=$null }
}

function Assert-MonitoringTarget {
    param([string]$Url)
    $uri = $null
    if (-not [Uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -cne 'https' -or $uri.Port -ne 443 -or
        $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/' -or
        $uri.DnsSafeHost -cnotmatch '^[a-z0-9][a-z0-9-]*\.[a-z0-9-]+\.run\.app$') { throw 'MONITORING_TARGET_INVALID' }
    return $uri.DnsSafeHost
}

function Get-MonitoringManifest {
    $path = Get-MonitoringPath 'installation.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'MONITORING_NOT_INSTALLED' }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}

function Assert-MonitoringInstalledFiles {
    param($Manifest)
    foreach ($artifact in $Manifest.artifacts) {
        $path = Get-MonitoringPath $artifact.relativePath
        if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-MonitoringFileHash $path) -cne $artifact.sha256) { throw 'MONITORING_ARTIFACT_HASH_MISMATCH' }
    }
    foreach ($relative in @('secrets\scrape-token.txt','secrets\grafana-admin-password.txt','secrets\grafana-secret-key.txt')) { Assert-MonitoringStoredRandomValue $relative }
    Assert-MonitoringPrivateFile 'secrets\backend-monitoring.env'
    $token = [IO.File]::ReadAllText((Get-MonitoringPath 'secrets\scrape-token.txt'))
    try {
        Assert-MonitoringTokenFormat $token
        $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($token))).ToLowerInvariant()
        if ([IO.File]::ReadAllText((Get-MonitoringPath 'secrets\backend-monitoring.env')) -cne "MONITORING_TOKEN_SHA256=$hash`n") { throw 'MONITORING_TOKEN_HASH_MISMATCH' }
    } finally { $token = $null; $hash = $null }
}

function Get-MonitoringProcessState {
    $path = Get-MonitoringPath 'processes.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return @() }
    return @((Get-Content -LiteralPath $path -Raw | ConvertFrom-Json).processes)
}

function Write-MonitoringProcessState {
    param([object[]]$Processes)
    Write-MonitoringText 'processes.json' (([ordered]@{ formatVersion=1; processes=@($Processes) } | ConvertTo-Json -Depth 5) + "`n")
}

function Get-MonitoringOwnedProcess {
    param($Record)
    if ($Record.name -notin @('prometheus','grafana')) { throw 'MONITORING_PROCESS_NAME_INVALID' }
    $manifest = Get-MonitoringManifest
    $relative = if ($Record.name -eq 'prometheus') { $manifest.prometheusExecutable } else { $manifest.grafanaExecutable }
    $expected = Get-MonitoringPath $relative
    if (-not [string]::Equals([IO.Path]::GetFullPath($Record.executable), $expected, [StringComparison]::OrdinalIgnoreCase)) { throw 'MONITORING_PROCESS_EXECUTABLE_MISMATCH' }
    $process = Get-Process -Id $Record.processId -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    try {
        [void]$process.SafeHandle
        if (-not [string]::Equals($process.Path, $expected, [StringComparison]::OrdinalIgnoreCase) -or
            $process.StartTime.ToUniversalTime().Ticks -ne [long]$Record.startTimeUtcTicks) { throw 'MONITORING_PROCESS_IDENTITY_MISMATCH' }
        return $process
    } catch { $process.Dispose(); throw 'MONITORING_PROCESS_IDENTITY_MISMATCH' }
}

function Start-MonitoringOwnedProcess {
    param([string]$Name, [string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory)
    # All arguments are public paths/flags. Credentials are read from private files by the applications.
    $quoted = @($Arguments | ForEach-Object {
        if ($_ -match '["\r\n]' -or $_.EndsWith('\')) { throw 'MONITORING_PROCESS_ARGUMENT_INVALID' }
        '"' + $_ + '"'
    }) -join ' '
    $environment = @{}
    $allowed = @('SystemRoot','WINDIR','SystemDrive','COMSPEC','PATH','PATHEXT','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA')
    foreach ($entry in [Environment]::GetEnvironmentVariables().GetEnumerator()) {
        $environment[$entry.Key] = if ($entry.Key -in $allowed) { $entry.Value } else { $null }
    }
    $process = Start-Process -FilePath $Executable -ArgumentList $quoted -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -UseNewEnvironment -Environment $environment -PassThru `
        -RedirectStandardOutput (Get-MonitoringPath "logs\$Name.stdout.log") -RedirectStandardError (Get-MonitoringPath "logs\$Name.stderr.log")
    try {
        return [pscustomobject]@{ name=$Name; processId=$process.Id; executable=$Executable; startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks; startedAtUtc=$process.StartTime.ToUniversalTime().ToString('o') }
    } finally { $process.Dispose() }
}

function Stop-MonitoringOwnedProcess {
    param($Record)
    $process = Get-MonitoringOwnedProcess $Record
    if ($null -ne $process) {
        try { $process.Kill(); if (-not $process.WaitForExit(15000)) { throw 'MONITORING_PROCESS_STOP_TIMEOUT' } }
        finally { $process.Dispose() }
    }
}

function Assert-MonitoringPortFree {
    param([int]$Port)
    if (@(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue).Count -ne 0) { throw 'MONITORING_PORT_OCCUPIED' }
}
