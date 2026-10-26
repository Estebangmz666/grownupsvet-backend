#Requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidatePattern('^[0-9a-f]{32}$')] [string]$RunId,
    [string]$PostgresBinDirectory = 'C:\Program Files\PostgreSQL\18\bin',
    [switch]$Execute
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$privateDirectory = [IO.Path]::GetFullPath((Join-Path ([Environment]::GetFolderPath('UserProfile')) ".grownupsvet/audit-database/$RunId"))
for ($ancestor = [IO.DirectoryInfo]::new($privateDirectory); $null -ne $ancestor; $ancestor = $ancestor.Parent) {
    if ($ancestor.Exists -and ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'AUDIT_DATABASE_PRIVATE_PATH_REDIRECTED' }
    if (Test-Path -LiteralPath (Join-Path $ancestor.FullName '.git')) { throw 'AUDIT_DATABASE_CREDENTIAL_DESTINATION_INSIDE_GIT' }
}
$settings = Import-Clixml -LiteralPath (Join-Path $privateDirectory 'credentials.clixml')
if ($settings.RunId -cne $RunId -or $settings.Host -notin @('127.0.0.1','localhost','::1') -or [int]$settings.Port -notin 1..65535) { throw 'AUDIT_DATABASE_REMOVAL_IDENTITY_MISMATCH' }
$databases = @("grownupsvet_audit_$RunId", "grownupsvet_audit_write_$RunId")
$roles = @("gv_audit_reader_$RunId", "gv_audit_writer_$RunId")
if ($settings.ReadDatabase -cne $databases[0] -or $settings.WriteDatabase -cne $databases[1] -or $settings.ReaderUsername -cne $roles[0] -or $settings.WriterUsername -cne $roles[1]) { throw 'AUDIT_DATABASE_REMOVAL_NAMES_MISMATCH' }
if (-not $Execute) {
    [pscustomobject]@{ Status='VALIDATED_NOT_EXECUTED'; RunId=$RunId; Databases=$databases; DatabaseConnected=$false } | ConvertTo-Json
    return
}
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'AUDIT_DATABASE_LOCAL_ADMIN_ENVIRONMENT_REQUIRED' }
function Invoke-AuditRemovalSql([string]$Sql) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = Join-Path $PostgresBinDirectory 'psql.exe'
    $start.UseShellExecute=$false; $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
    $essential=@{}
    foreach($key in @('SystemRoot','WINDIR','PATH','TEMP','TMP')) { if($start.Environment.ContainsKey($key)){$essential[$key]=$start.Environment[$key]} }
    $start.Environment.Clear(); foreach($key in $essential.Keys){$start.Environment[$key]=$essential[$key]}
    $start.Environment['PGPASSWORD']=$env:DB_PASSWORD
    foreach($argument in @('--no-password','--no-psqlrc','--quiet','--tuples-only','--no-align','--host',[string]$settings.Host,'--port',[string]$settings.Port,'--username',$env:DB_USERNAME,'--dbname','postgres','--set','ON_ERROR_STOP=1','--set','VERBOSITY=sqlstate','--command',$Sql)) { $start.ArgumentList.Add($argument) }
    $process=[Diagnostics.Process]::new();$process.StartInfo=$start
    try{
        [void]$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if(-not $process.WaitForExit(30000)){$process.Kill($true);throw 'AUDIT_DATABASE_REMOVAL_TIMEOUT'}
        $output=$stdout.GetAwaiter().GetResult();[void]$stderr.GetAwaiter().GetResult()
        if($process.ExitCode -ne 0){throw 'AUDIT_DATABASE_REMOVAL_SQL_REJECTED: no FORCE or unrelated connection termination will be attempted.'}
        return $output.Trim()
    }finally{$process.Dispose()}
}
$identity=Invoke-AuditRemovalSql "SELECT (inet_server_addr() IN ('127.0.0.1'::inet,'::1'::inet) AND current_setting('server_version_num')::int / 10000=18)::int"
if($identity -cne '1'){throw 'AUDIT_DATABASE_REMOVAL_NOT_LOCAL_POSTGRES18'}
$marker="GrownupsVet isolated audit $RunId"
foreach($database in $databases){
    $owned=Invoke-AuditRemovalSql "SELECT CASE WHEN datdba=(SELECT oid FROM pg_roles WHERE rolname=current_user) AND shobj_description(oid,'pg_database')='$marker' THEN 'OWNED' ELSE 'REFUSE' END FROM pg_database WHERE datname='$database'"
    if($owned -eq ''){continue}
    if($owned -cne 'OWNED'){throw 'AUDIT_DATABASE_REMOVAL_DATABASE_NOT_OWNED'}
    [void](Invoke-AuditRemovalSql "DROP DATABASE `"$database`"")
}
foreach($role in $roles){
    $owned=Invoke-AuditRemovalSql "SELECT CASE WHEN NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls) AND shobj_description(oid,'pg_authid')='$marker' THEN 'OWNED' ELSE 'REFUSE' END FROM pg_roles WHERE rolname='$role'"
    if($owned -eq ''){continue}
    if($owned -cne 'OWNED'){throw 'AUDIT_DATABASE_REMOVAL_ROLE_NOT_OWNED'}
    [void](Invoke-AuditRemovalSql "DROP ROLE `"$role`"")
}
$report=[pscustomobject]@{Status='REMOVED';RunId=$RunId;LocalOnly=$true;Databases=$databases;Roles=$roles;ForceUsed=$false}
$report | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $privateDirectory 'cleanup-sanitized.json') -Encoding utf8
$report | ConvertTo-Json
