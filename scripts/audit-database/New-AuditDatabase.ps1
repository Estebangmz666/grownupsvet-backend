#Requires -Version 7.4
[CmdletBinding()]
param(
    [ValidateSet('127.0.0.1', 'localhost', '::1')] [string]$DatabaseHost = '127.0.0.1',
    [ValidateRange(1, 65535)] [int]$DatabasePort = 5432,
    [string]$ResolvedClasspathFile = '',
    [switch]$Execute
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'AUDIT_DATABASE_WINDOWS_DPAPI_REQUIRED: use this launcher on Windows with PowerShell 7.4.' }
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$migrationDirectory = Join-Path $repository 'src/main/resources/db/migration'
$migrationFiles = @(Get-ChildItem -LiteralPath $migrationDirectory -Filter 'V*__*.sql' -File)
if ($migrationFiles.Count -lt 22) { throw 'AUDIT_DATABASE_MIGRATION_INVENTORY_INCOMPLETE' }
if (-not $Execute) {
    [pscustomobject]@{ Status = 'VALIDATED_NOT_EXECUTED'; Host = $DatabaseHost; Port = $DatabasePort; MigrationCount = $migrationFiles.Count; DatabaseConnected = $false } | ConvertTo-Json
    return
}
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'AUDIT_DATABASE_LOCAL_ADMIN_ENVIRONMENT_REQUIRED' }

$runId = [Guid]::NewGuid().ToString('N')
$privateDirectory = [IO.Path]::GetFullPath((Join-Path ([Environment]::GetFolderPath('UserProfile')) ".grownupsvet/audit-database/$runId"))
# Refuse a private location redirected into another tree or Git checkout.
for ($ancestor = [IO.DirectoryInfo]::new($privateDirectory); $null -ne $ancestor; $ancestor = $ancestor.Parent) {
    if ($ancestor.Exists -and ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'AUDIT_DATABASE_PRIVATE_PATH_REDIRECTED' }
    if (Test-Path -LiteralPath (Join-Path $ancestor.FullName '.git')) { throw 'AUDIT_DATABASE_CREDENTIAL_DESTINATION_INSIDE_GIT' }
}
[void][IO.Directory]::CreateDirectory($privateDirectory)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$permissions = [Security.AccessControl.DirectorySecurity]::new()
$permissions.SetAccessRuleProtection($true, $false)
foreach ($principal in @($identity, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $permissions.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
}
Set-Acl -LiteralPath $privateDirectory -AclObject $permissions
$readerPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
$writerPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
$accountPassword = 'Audit! ' + [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
$credentials = [pscustomobject]@{
    RunId = $runId; Host = $DatabaseHost; Port = $DatabasePort
    ReadDatabase = "grownupsvet_audit_$runId"; WriteDatabase = "grownupsvet_audit_write_$runId"
    ReaderUsername = "gv_audit_reader_$runId"; WriterUsername = "gv_audit_writer_$runId"
    ReaderPassword = ConvertTo-SecureString $readerPassword -AsPlainText -Force
    WriterPassword = ConvertTo-SecureString $writerPassword -AsPlainText -Force
    FixtureAccountPassword = ConvertTo-SecureString $accountPassword -AsPlainText -Force
}
$credentials | Export-Clixml -LiteralPath (Join-Path $privateDirectory 'credentials.clixml')

function Invoke-AuditChild {
    param([string]$Executable, [string[]]$Arguments, [hashtable]$AdditionalEnvironment = @{}, [string]$LogName)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Executable; $start.WorkingDirectory = $repository
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $essential = @{}
    foreach ($key in @('SystemRoot','WINDIR','COMSPEC','PATH','PATHEXT','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','JAVA_HOME')) {
        if ($start.Environment.ContainsKey($key)) { $essential[$key] = $start.Environment[$key] }
    }
    $start.Environment.Clear()
    foreach ($key in $essential.Keys) { $start.Environment[$key] = $essential[$key] }
    foreach ($key in $AdditionalEnvironment.Keys) { $start.Environment[$key] = [string]$AdditionalEnvironment[$key] }
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(600000)) { $process.Kill($true); throw 'AUDIT_DATABASE_CHILD_TIMEOUT' }
        [IO.File]::WriteAllText((Join-Path $privateDirectory $LogName), ($stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()), [Text.UTF8Encoding]::new($false))
        if ($process.ExitCode -ne 0) { throw "AUDIT_DATABASE_CHILD_FAILED: inspect the private $LogName (exit $($process.ExitCode))." }
    } finally { $process.Dispose() }
}

try {
    $classpathFile = Join-Path $privateDirectory 'classpath.txt'
    $powershell = (Get-Process -Id $PID).Path
    if ([string]::IsNullOrWhiteSpace($ResolvedClasspathFile)) {
        Invoke-AuditChild -Executable $powershell -Arguments @('-NoLogo','-NoProfile','-NonInteractive','-File',(Join-Path $PSScriptRoot 'Resolve-AuditClasspath.ps1'),'-OutputFile',$classpathFile) -LogName 'maven-private.log'
    } else {
        $resolvedClasspath = [IO.File]::ReadAllText([IO.Path]::GetFullPath($ResolvedClasspathFile)).Trim()
        foreach ($entry in $resolvedClasspath.Split([IO.Path]::PathSeparator)) {
            if (-not [IO.Path]::IsPathFullyQualified($entry) -or -not [IO.File]::Exists($entry) -or [IO.Path]::GetExtension($entry) -cne '.jar') { throw 'AUDIT_DATABASE_CLASSPATH_INVALID' }
        }
        [IO.File]::WriteAllText($classpathFile, $resolvedClasspath, [Text.UTF8Encoding]::new($false))
    }
    $java = (Get-Command java -ErrorAction Stop).Source
    Invoke-AuditChild -Executable $java -Arguments @('-cp', [IO.File]::ReadAllText($classpathFile).Trim(), (Join-Path $PSScriptRoot 'PrepareAuditDatabase.java')) -LogName 'database-private.log' -AdditionalEnvironment @{
        AUDIT_DATABASE_HOST = $DatabaseHost; AUDIT_DATABASE_PORT = $DatabasePort; AUDIT_DATABASE_RUN_ID = $runId
        AUDIT_DATABASE_ADMIN_USERNAME = $env:DB_USERNAME; AUDIT_DATABASE_ADMIN_PASSWORD = $env:DB_PASSWORD
        AUDIT_DATABASE_READER_PASSWORD = $readerPassword; AUDIT_DATABASE_WRITER_PASSWORD = $writerPassword; AUDIT_DATABASE_ACCOUNT_PASSWORD = $accountPassword
        AUDIT_DATABASE_MIGRATIONS = $migrationDirectory; AUDIT_DATABASE_FIXTURES = (Join-Path $PSScriptRoot 'fixtures.sql')
        AUDIT_DATABASE_REPORT = (Join-Path $privateDirectory 'report-sanitized.json')
    }
    Get-Content -LiteralPath (Join-Path $privateDirectory 'report-sanitized.json') -Raw
    Write-Information "Private DPAPI credentials and diagnostic logs: $privateDirectory" -InformationAction Continue
} finally {
    $readerPassword = $null; $writerPassword = $null; $accountPassword = $null
}
