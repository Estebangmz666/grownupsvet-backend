#Requires -Version 7.4
param([switch]$Uninstall)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'MonitoringRuntime.Common.ps1')
$shell = $null
function Get-ControlLinkProperties {
    param([string]$Path)
    $link = $shell.CreateShortcut($Path)
    try { return [pscustomobject]@{target=$link.TargetPath;arguments=$link.Arguments;workingDirectory=$link.WorkingDirectory;windowStyle=$link.WindowStyle} }
    finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($link) }
}
function Assert-ControlLinkMatches {
    param([string]$Path,$Expected)
    $actual = Get-ControlLinkProperties $Path
    if (-not [string]::Equals($actual.target,$Expected.target,[StringComparison]::OrdinalIgnoreCase) -or $actual.arguments -cne $Expected.arguments -or
        -not [string]::Equals($actual.workingDirectory,$Expected.workingDirectory,[StringComparison]::OrdinalIgnoreCase) -or $actual.windowStyle -ne $Expected.windowStyle) { throw 'MONITORING_SHORTCUT_COLLISION' }
}
try {
    $desktop = [Environment]::GetFolderPath([Environment+SpecialFolder]::DesktopDirectory)
    if (-not $desktop -or -not (Test-Path -LiteralPath $desktop -PathType Container)) { throw 'MONITORING_DESKTOP_UNAVAILABLE' }
    if ((Get-Item -LiteralPath $desktop -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'MONITORING_DESKTOP_REPARSE_POINT' }
    $shell = New-Object -ComObject WScript.Shell
    $registrationPath = Get-MonitoringPath 'controls\shortcuts.json'
    $previous = if (Test-Path -LiteralPath $registrationPath) {Get-Content -LiteralPath $registrationPath -Raw|ConvertFrom-Json} else {$null}
    $definitions = @(@{name='Encender monitoreo GrownupsVet.lnk';action='Start'},@{name='Apagar monitoreo GrownupsVet.lnk';action='Stop'},@{name='Abrir panel GrownupsVet.lnk';action='OpenPanel'})
    foreach ($definition in $definitions) {
        $path = Join-Path $desktop $definition.name
        if (Test-Path -LiteralPath $path) {
            if ($null -eq $previous) { throw 'MONITORING_SHORTCUT_COLLISION' }
            $owned = @($previous.shortcuts | Where-Object name -CEQ $definition.name)
            if ($owned.Count -ne 1 -or -not [string]::Equals($previous.desktop,$desktop,[StringComparison]::OrdinalIgnoreCase)) { throw 'MONITORING_SHORTCUT_COLLISION' }
            Assert-ControlLinkMatches $path $owned[0]
        }
    }
    if ($Uninstall) {
        $removed = 0
        if ($null -ne $previous) {
            foreach ($definition in $definitions) {
                $path = Join-Path $desktop $definition.name
                if (Test-Path -LiteralPath $path -PathType Leaf) {Remove-Item -LiteralPath $path; $removed++}
            }
            Remove-Item -LiteralPath $registrationPath
        }
        [pscustomobject]@{status='SHORTCUTS_REMOVED';removed=$removed;monitoringDataPreserved=$true;apiChanged=$false}|ConvertTo-Json
        exit 0
    }
    Assert-MonitoringInstalledFiles (Get-MonitoringManifest)
    $powerShell = Join-Path $PSHOME 'pwsh.exe'
    $signature = Get-AuthenticodeSignature -LiteralPath $powerShell
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') { throw 'MONITORING_POWERSHELL_NOT_TRUSTED' }
    $files = @('MonitoringRuntime.Common.ps1','Install-LocalMonitoring.ps1','Start-LocalMonitoring.ps1','Stop-LocalMonitoring.ps1','Test-LocalMonitoring.ps1','Test-LocalMonitoringGuards.ps1','Invoke-LocalMonitoringControl.ps1')
    $entries = @($files|Sort-Object|ForEach-Object {@{name=$_;sha256=(Get-MonitoringFileHash (Join-Path $PSScriptRoot $_))}})
    $contentIdentity = ($entries|ForEach-Object {$_.name+'='+$_.sha256}) -join "`n"
    $contentIdentity += "`npowerShell=$powerShell`nsha256=$(Get-MonitoringFileHash $powerShell)"
    $version = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($contentIdentity))).ToLowerInvariant().Substring(0,16)
    [void](Set-MonitoringPrivateDirectory 'controls')
    [void](Set-MonitoringPrivateDirectory 'controls\versions')
    $relativeBundle = "controls\versions\$version"
    $bundle = Get-MonitoringPath $relativeBundle
    if (-not (Test-Path -LiteralPath $bundle)) {
        $stagingRelative = 'controls\versions\.staging-'+[Guid]::NewGuid().ToString('N')
        $staging = Set-MonitoringPrivateDirectory $stagingRelative
        foreach ($file in $files) {
            Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination (Join-Path $staging $file)
            Set-MonitoringPrivateFile "$stagingRelative\$file"
        }
        $bundleManifest = [ordered]@{formatVersion=1;version=$version;createdAtUtc=[DateTimeOffset]::UtcNow.ToString('o');powerShellExecutable=$powerShell;powerShellSha256=(Get-MonitoringFileHash $powerShell);files=$entries}
        Write-MonitoringText "$stagingRelative\bundle-manifest.json" (($bundleManifest|ConvertTo-Json -Depth 5)+"`n")
        Set-MonitoringPrivateFile "$stagingRelative\bundle-manifest.json"
        [IO.Directory]::Move($staging,$bundle)
    }
    $bundleManifest = Get-Content -LiteralPath (Join-Path $bundle 'bundle-manifest.json') -Raw|ConvertFrom-Json
    foreach ($entry in $entries) {
        if ((Get-MonitoringFileHash (Join-Path $bundle $entry.name)) -cne $entry.sha256) {throw 'MONITORING_CONTROL_HASH_MISMATCH'}
        Assert-MonitoringPrivateFile "$relativeBundle\$($entry.name)"
    }
    if ($bundleManifest.powerShellExecutable -cne $powerShell -or $bundleManifest.powerShellSha256 -cne (Get-MonitoringFileHash $powerShell)) { throw 'MONITORING_CONTROL_ENGINE_CHANGED' }
    $wrapper = Join-Path $bundle 'Invoke-LocalMonitoringControl.ps1'
    if ($wrapper -match '["\r\n]') {throw 'MONITORING_CONTROL_ARGUMENT_INVALID'}
    $records = @()
    foreach ($definition in $definitions) {
        $arguments = '-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "'+$wrapper+'" -Action '+$definition.action
        $record = [pscustomobject]@{name=$definition.name;target=$powerShell;arguments=$arguments;workingDirectory=$bundle;windowStyle=7}
        $path = Join-Path $desktop $definition.name
        $link = $shell.CreateShortcut($path)
        try {
            $link.TargetPath=$record.target; $link.Arguments=$record.arguments; $link.WorkingDirectory=$record.workingDirectory; $link.WindowStyle=$record.windowStyle
            $link.Description='Solo monitoreo local de GrownupsVet. La API permanece disponible.'
            $link.IconLocation=$powerShell+',0'
            $link.Save()
        } finally {[void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($link)}
        Assert-ControlLinkMatches $path $record
        $records += $record
    }
    Write-MonitoringText 'controls\shortcuts.json' (([ordered]@{formatVersion=1;desktop=$desktop;bundle=$bundle;installedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');shortcuts=$records}|ConvertTo-Json -Depth 5)+"`n")
    Set-MonitoringPrivateFile 'controls\shortcuts.json'
    [pscustomobject]@{status='SHORTCUTS_INSTALLED';desktop=$desktop;names=@($definitions.name);stableBundle=$bundle;linksVerified=$true;apiChanged=$false}|ConvertTo-Json -Depth 4
} catch { [Console]::Error.WriteLine("MONITORING_SHORTCUT_INSTALL_FAILED category=$(Get-MonitoringSafeErrorCategory $_); no se modificó la API."); exit 1 }
finally {if($null -ne $shell){[void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($shell)}}
