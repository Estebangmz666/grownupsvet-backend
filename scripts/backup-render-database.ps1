#Requires -Version 7.2
[CmdletBinding()]
param(
    [string]$ExpectedDatabaseName = 'grownupsvet',
    [string]$ExpectedSchemaName = 'public',
    [string]$OutputDirectory = '',
    [string]$ConnectionPath = (Join-Path $env:USERPROFILE '.grownupsvet\cloud-runtime\render-connection.clixml'),
    [string]$PostgresBinDirectory = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$SslRootCertificatePath = '',
    [switch]$UseEnvironmentConnection,
    [switch]$LimitToExpectedSchema,
    [switch]$LocalTestOnly,
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-BackupIdentifier {
    param([string]$Value, [string]$Kind)
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value.Length -gt 63 -or $Value -notmatch '^[A-Za-z_][A-Za-z0-9_-]*$') {
        throw "BACKUP_INPUT: El identificador de $Kind no es válido."
    }
}

function Assert-BackupChildPath {
    param([string]$Root, [string]$Child)
    $rootPrefix = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not [IO.Path]::GetFullPath($Child).StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'BACKUP_PATH: Una ruta temporal no pertenece al directorio comprobado.'
    }
}

function Resolve-SafeBackupOutputDirectory {
    param([string]$Directory)
    if ([string]::IsNullOrWhiteSpace($Directory)) { throw 'BACKUP_PATH: Indica un directorio de respaldo fuera de Git.' }
    $resolved = [IO.Path]::GetFullPath($Directory)
    if (Test-Path -LiteralPath $resolved -PathType Leaf) { throw 'BACKUP_PATH: El destino debe ser un directorio.' }
    $ancestor = $resolved
    while (-not [string]::IsNullOrEmpty($ancestor)) {
        if (Test-Path -LiteralPath (Join-Path $ancestor '.git')) { throw 'BACKUP_PATH: El respaldo no se puede escribir dentro de un repositorio Git.' }
        if (Test-Path -LiteralPath $ancestor) {
            $item = Get-Item -LiteralPath $ancestor -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'BACKUP_PATH: El destino no admite enlaces simbólicos ni junctions.'
            }
        }
        $parent = [IO.Directory]::GetParent($ancestor)
        $ancestor = if ($null -eq $parent) { '' } else { $parent.FullName }
    }
    return $resolved
}

function Set-PrivateBackupDirectory {
    param([string]$Path)
    if (-not $IsWindows) { throw 'BACKUP_PLATFORM: Este helper requiere Windows para DPAPI y ACL privadas.' }
    [void][IO.Directory]::CreateDirectory($Path)
    $access = [Security.AccessControl.DirectorySecurity]::new()
    $access.SetAccessRuleProtection($true, $false)
    $currentUserSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $access.SetOwner($currentUserSid)
    $inheritance = [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
    foreach ($sid in @($currentUserSid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $access.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,
                [Security.AccessControl.FileSystemRights]::FullControl, $inheritance,
                [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow))
    }
    Set-Acl -LiteralPath $Path -AclObject $access
}

function Get-BackupConnection {
    param([string]$PrivatePath, [bool]$FromEnvironment)
    if (-not $FromEnvironment) {
        if (-not (Test-Path -LiteralPath $PrivatePath -PathType Leaf)) {
            throw 'BACKUP_CREDENTIAL: No está disponible la conexión DPAPI privada esperada.'
        }
        try {
            $saved = Import-Clixml -LiteralPath $PrivatePath
            if ($saved.Password -isnot [Security.SecureString]) { throw 'Unexpected credential type' }
            return [pscustomobject]@{ Host = [string]$saved.Host; Port = [int]$saved.Port; Database = [string]$saved.Database;
                Username = [string]$saved.Username; Password = $saved.Password }
        } catch { throw 'BACKUP_CREDENTIAL: No se pudo leer la conexión DPAPI con este usuario de Windows.' }
    }
    try {
        if ([string]::IsNullOrWhiteSpace($env:RENDER_DATABASE_URL)) { throw 'Missing environment connection' }
        $uri = [Uri]::new($env:RENDER_DATABASE_URL, [UriKind]::Absolute)
        if ($uri.Scheme -notin @('postgres', 'postgresql') -or -not [string]::IsNullOrEmpty($uri.Fragment)) { throw 'Invalid URI' }
        $credentials = $uri.UserInfo.Split(':', 2)
        if ($credentials.Length -ne 2 -or [string]::IsNullOrEmpty($credentials[1])) { throw 'Missing credentials' }
        if ($uri.AbsolutePath -notmatch '^/[^/]+$') { throw 'Invalid database path' }
        if (-not [string]::IsNullOrEmpty($uri.Query) -and $uri.Query -notmatch '^\?sslmode=(require|verify-full)$') {
            throw 'Unsupported connection options'
        }
        $password = ConvertTo-SecureString -String ([Uri]::UnescapeDataString($credentials[1])) -AsPlainText -Force
        return [pscustomobject]@{ Host = $uri.DnsSafeHost; Port = $(if ($uri.Port -lt 0) { 5432 } else { $uri.Port });
            Database = [Uri]::UnescapeDataString($uri.AbsolutePath.Substring(1));
            Username = [Uri]::UnescapeDataString($credentials[0]); Password = $password }
    } catch { throw 'BACKUP_CREDENTIAL: RENDER_DATABASE_URL no tiene una conexión PostgreSQL admitida; no se muestran sus valores.' }
}

function Assert-BackupConnection {
    param($Connection, [string]$Database, [string]$Schema, [bool]$Local, [bool]$SchemaSelection)
    Assert-BackupIdentifier -Value $Database -Kind 'base de datos'
    Assert-BackupIdentifier -Value $Schema -Kind 'esquema'
    if ($Schema -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'BACKUP_INPUT: El esquema requiere un identificador simple sin guiones.' }
    Assert-BackupIdentifier -Value $Connection.Username -Kind 'usuario'
    if ($Connection.Database -cne $Database -or $Connection.Port -lt 1 -or $Connection.Port -gt 65535 -or
            $Connection.Password -isnot [Security.SecureString] -or $Connection.Password.Length -eq 0) {
        throw 'BACKUP_SOURCE: La conexión no corresponde a la base esperada o está incompleta.'
    }
    if ($Local) {
        if ($Connection.Host -notin @('localhost', '127.0.0.1', '::1') -or $Database -notmatch '_test$' -or
                -not $SchemaSelection -or $Schema -notmatch '^backup_render_test_[0-9a-f]{32}$') {
            throw 'BACKUP_SOURCE: El modo de prueba exige loopback, base _test y selección de un esquema UUID propio.'
        }
    } elseif ($Connection.Host -notmatch '^(?i)[a-z0-9][a-z0-9-]*\.ohio-postgres\.render\.com$') {
        throw 'BACKUP_SOURCE: El respaldo normal requiere el host externo esperado de Render Ohio.'
    }
}

function ConvertTo-BackupPassField {
    param([string]$Value)
    if ($Value.Contains("`n") -or $Value.Contains("`r") -or $Value.Contains([char]0)) {
        throw 'BACKUP_CREDENTIAL: La credencial contiene un carácter no admitido por PGPASSFILE.'
    }
    return $Value.Replace('\', '\\').Replace(':', '\:')
}

function Write-BackupPassFile {
    param($Connection, [string]$Path)
    $pointer = [IntPtr]::Zero
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Connection.Password)
        $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        $fields = @($Connection.Host, [string]$Connection.Port, $Connection.Database, $Connection.Username, $plainPassword)
        $line = ($fields | ForEach-Object { ConvertTo-BackupPassField -Value $_ }) -join ':'
        [IO.File]::WriteAllText($Path, $line + "`n", [Text.UTF8Encoding]::new($false))
    } finally {
        $plainPassword = $null; $line = $null; $fields = $null
        if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
}

function Write-BackupTrustedRoots {
    param([string]$Path, [string]$ExplicitCertificatePath)
    if (-not [string]::IsNullOrWhiteSpace($ExplicitCertificatePath)) {
        if (-not (Test-Path -LiteralPath $ExplicitCertificatePath -PathType Leaf)) { throw 'BACKUP_TLS: No existe el PEM de CA indicado.' }
        $pem = [IO.File]::ReadAllText([IO.Path]::GetFullPath($ExplicitCertificatePath))
        if ($pem -match 'PRIVATE KEY' -or $pem -notmatch '-----BEGIN CERTIFICATE-----') { throw 'BACKUP_TLS: Se requiere un PEM de certificados CA públicos.' }
        $certificates = [Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
        try { $certificates.ImportFromPemFile([IO.Path]::GetFullPath($ExplicitCertificatePath)) } catch { throw 'BACKUP_TLS: El PEM de CA no es válido.' }
        if ($certificates.Count -eq 0) { throw 'BACKUP_TLS: El PEM de CA está vacío.' }
        foreach ($certificate in $certificates) {
            $constraints = @($certificate.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.19' })
            if ($constraints.Count -ne 1 -or -not ([Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($constraints[0], $constraints[0].Critical)).CertificateAuthority) {
                throw 'BACKUP_TLS: El PEM explícito debe contener CA; no se admite confiar directamente en el certificado del servidor.'
            }
        }
        [IO.File]::WriteAllText($Path, $pem, [Text.UTF8Encoding]::new($false))
        return
    }
    $roots = @(Get-ChildItem -Path Cert:\CurrentUser\Root,Cert:\LocalMachine\Root | Sort-Object Thumbprint -Unique)
    if ($roots.Count -eq 0) { throw 'BACKUP_TLS: Windows no proporcionó raíces de confianza; no se reducirá la validación TLS.' }
    $builder = [Text.StringBuilder]::new()
    foreach ($certificate in $roots) {
        [void]$builder.AppendLine('-----BEGIN CERTIFICATE-----')
        [void]$builder.AppendLine([Convert]::ToBase64String($certificate.RawData, [Base64FormattingOptions]::InsertLineBreaks))
        [void]$builder.AppendLine('-----END CERTIFICATE-----')
    }
    [IO.File]::WriteAllText($Path, $builder.ToString(), [Text.ASCIIEncoding]::new())
}

function New-BackupPostgresEnvironment {
    param($Connection, [string]$PassFile, [string]$RootCertificate, [bool]$Local)
    return @{ PGHOST = $Connection.Host; PGPORT = [string]$Connection.Port; PGDATABASE = $Connection.Database;
        PGUSER = $Connection.Username; PGPASSFILE = $PassFile; PGCONNECT_TIMEOUT = '15';
        PGSSLMODE = $(if ($Local) { 'disable' } else { 'verify-full' }); PGSSLROOTCERT = $RootCertificate;
        PGGSSENCMODE = 'disable'; PGSSLMINPROTOCOLVERSION = 'TLSv1.2'; PGAPPNAME = 'grownupsvet-render-backup'; PGCLIENTENCODING = 'UTF8' }
}

function Invoke-BackupPostgresCommand {
    param([string]$Executable, [string[]]$Arguments, [hashtable]$Environment = @{}, [int]$TimeoutSeconds = 1800)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Executable; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    # Child tools receive OS essentials and this connection only, never other application secrets.
    $operatingSystemEnvironment = @{}
    foreach ($key in @('SystemRoot','WINDIR','SystemDrive','COMSPEC','PATH','PATHEXT','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA')) {
        if ($start.Environment.ContainsKey($key)) { $operatingSystemEnvironment[$key] = $start.Environment[$key] }
    }
    $start.Environment.Clear()
    foreach ($key in $operatingSystemEnvironment.Keys) { $start.Environment[$key] = $operatingSystemEnvironment[$key] }
    foreach ($key in $Environment.Keys) { $start.Environment[$key] = [string]$Environment[$key] }
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) { $process.Kill($true); throw 'BACKUP_PROCESS: La herramienta PostgreSQL excedió su tiempo máximo.' }
        $output = $stdout.GetAwaiter().GetResult(); $privateError = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            $reason = switch -Regex ($privateError) {
                '\b([0-9]{2}[0-9A-Z]{3})\b' { 'SQLSTATE_' + $Matches[1]; break }
                'no password supplied' { 'PASSWORD_FILE_NOT_USED'; break }
                'password authentication failed' { 'AUTHENTICATION_FAILED'; break }
                'permission denied' { 'PERMISSION_DENIED'; break }
                'certificate verify failed|root certificate|certificate verification' { 'TLS_CERTIFICATE_VALIDATION'; break }
                'could not translate host name|connection.*refused|connection.*timed out' { 'CONNECTION_UNAVAILABLE'; break }
                'syntax error' { 'SQL_SYNTAX'; break }
                'unrecognized option|invalid option' { 'UNRECOGNIZED_OPTION'; break }
                'extra command-line argument|too many command-line arguments' { 'ARGUMENT_ERROR'; break }
                'does not exist' { 'MISSING_OBJECT'; break }
                'invalid.*encoding|invalid.*parameter' { 'CLIENT_CONFIGURATION'; break }
                default { 'TOOL_FAILED' }
            }
            throw "BACKUP_PROCESS: PostgreSQL no completó la operación ($reason, salida $($process.ExitCode)); se omitieron diagnósticos privados."
        }
        return $output
    } finally { $process.Dispose() }
}

function Remove-OwnedBackupTemporaryDirectory {
    param([string]$Root, [string]$Path, [string[]]$FileNames)
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) { return }
    Assert-BackupChildPath -Root $Root -Child $Path
    foreach ($name in $FileNames) {
        $file = Join-Path $Path $name
        Assert-BackupChildPath -Root $Path -Child $file
        if (Test-Path -LiteralPath $file -PathType Leaf) { Remove-Item -LiteralPath $file -Force }
    }
    Remove-Item -LiteralPath $Path -Force
}

function Invoke-RenderDatabaseBackup {
    param([string]$ExpectedDatabaseName, [string]$ExpectedSchemaName, [string]$OutputDirectory, [string]$ConnectionPath,
        [string]$PostgresBinDirectory, [string]$SslRootCertificatePath, [bool]$UseEnvironmentConnection,
        [bool]$LimitToExpectedSchema, [bool]$LocalTestOnly, [bool]$ValidateOnly)
    $stage = 'INPUT'; $temporaryCredentials = $null; $temporaryBackup = $null; $promoted = $false
    $temporaryRoot = [IO.Path]::GetTempPath()
    $dumpFiles = @()
    try {
        Assert-BackupIdentifier -Value $ExpectedDatabaseName -Kind 'base de datos'
        Assert-BackupIdentifier -Value $ExpectedSchemaName -Kind 'esquema'
        $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('SA Pacific Standard Time')
        $now = [DateTimeOffset]::UtcNow; $businessTime = [TimeZoneInfo]::ConvertTime($now, $businessZone)
        if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
            $OutputDirectory = Join-Path ([Environment]::GetFolderPath('MyDocuments')) ('GrownupsVetBackups\' + $businessTime.ToString('yyyy-MM-dd'))
        }
        $output = Resolve-SafeBackupOutputDirectory -Directory $OutputDirectory
        $connection = Get-BackupConnection -PrivatePath $ConnectionPath -FromEnvironment $UseEnvironmentConnection
        Assert-BackupConnection -Connection $connection -Database $ExpectedDatabaseName -Schema $ExpectedSchemaName -Local $LocalTestOnly -SchemaSelection $LimitToExpectedSchema
        $tools = @{}
        foreach ($name in @('psql', 'pg_dump', 'pg_restore')) {
            $executable = Join-Path ([IO.Path]::GetFullPath($PostgresBinDirectory)) "$name.exe"
            if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) { throw 'BACKUP_TOOL: Faltan herramientas PostgreSQL 18.' }
            $version = Invoke-BackupPostgresCommand -Executable $executable -Arguments @('--version')
            if ($version -notmatch '\(PostgreSQL\) 18\.') { throw 'BACKUP_TOOL: Las tres herramientas deben ser PostgreSQL 18.' }
            $tools[$name] = $executable
        }
        if ($ValidateOnly) {
            return [pscustomobject]@{ Status = 'VALIDATED_OFFLINE'; ExpectedDatabase = $ExpectedDatabaseName;
                ExpectedSchema = $ExpectedSchemaName; Scope = $(if ($LimitToExpectedSchema) { 'schema' } else { 'database' });
                TlsMode = $(if ($LocalTestOnly) { 'local-test-only' } else { 'verify-full' }); OutputDirectory = $output; Connected = $false }
        }
        $stage = 'PRIVATE_TEMPORARIES'
        Set-PrivateBackupDirectory -Path $output
        [void](Resolve-SafeBackupOutputDirectory -Directory $output)
        $temporaryCredentials = Join-Path $temporaryRoot ('grownupsvet-backup-credentials-' + [Guid]::NewGuid().ToString('N'))
        Assert-BackupChildPath -Root $temporaryRoot -Child $temporaryCredentials
        Set-PrivateBackupDirectory -Path $temporaryCredentials
        $passFile = Join-Path $temporaryCredentials 'pgpass.conf'; $rootCertificate = Join-Path $temporaryCredentials 'trusted-roots.pem'
        Write-BackupPassFile -Connection $connection -Path $passFile
        if (-not $LocalTestOnly) { Write-BackupTrustedRoots -Path $rootCertificate -ExplicitCertificatePath $SslRootCertificatePath }
        $postgresEnvironment = New-BackupPostgresEnvironment -Connection $connection -PassFile $passFile -RootCertificate $rootCertificate -Local $LocalTestOnly
        $stage = 'SOURCE_IDENTITY'
        $query = "select json_build_object('database',current_database(),'serverVersionNum',current_setting('server_version_num')::integer," +
            "'schemaExists',exists(select 1 from pg_namespace where nspname='$ExpectedSchemaName')," +
            "'requiredTablesPresent',(to_regclass('$ExpectedSchemaName.flyway_schema_history') is not null and to_regclass('$ExpectedSchemaName.users') is not null " +
            "and to_regclass('$ExpectedSchemaName.pets') is not null and to_regclass('$ExpectedSchemaName.appointments') is not null)," +
            "'tls',coalesce((select ssl from pg_stat_ssl where pid=pg_backend_pid()),false),'tlsVersion',(select version from pg_stat_ssl where pid=pg_backend_pid()))"
        $metadata = (Invoke-BackupPostgresCommand -Executable $tools.psql -Arguments @('--no-password','--no-psqlrc','--tuples-only','--no-align','--quiet','--set','ON_ERROR_STOP=1','--set','VERBOSITY=sqlstate','--command',$query) -Environment $postgresEnvironment -TimeoutSeconds 60) | ConvertFrom-Json
        if ($metadata.database -cne $ExpectedDatabaseName -or -not $metadata.schemaExists -or -not $metadata.requiredTablesPresent) {
            throw 'BACKUP_SOURCE: La base o el esquema conectado no coincide con el origen GrownupsVet esperado.'
        }
        if (-not $LocalTestOnly -and -not $metadata.tls) { throw 'BACKUP_TLS: El origen no confirmó TLS; no se realizará el respaldo.' }
        $backupName = 'grownupsvet-render-' + $businessTime.ToString('yyyyMMdd-HHmmss')
        $finalDirectory = Join-Path $output $backupName
        if (Test-Path -LiteralPath $finalDirectory) { throw 'BACKUP_COLLISION: Ya existe un respaldo con esa marca temporal; no se sobrescribe.' }
        $temporaryBackup = Join-Path $output ('.inprogress-' + [Guid]::NewGuid().ToString('N'))
        Assert-BackupChildPath -Root $output -Child $temporaryBackup
        Set-PrivateBackupDirectory -Path $temporaryBackup
        $dumpName = "$backupName.dump"; $listName = "$backupName.catalog.txt"; $hashName = "$backupName.sha256"; $manifestName = "$backupName.manifest.json"
        $dumpFiles = @($dumpName, $listName, $hashName, $manifestName)
        $dumpPath = Join-Path $temporaryBackup $dumpName
        $stage = 'DUMP'
        $arguments = @('--no-password','--format=custom','--no-owner','--no-privileges','--lock-wait-timeout=30s','--file',$dumpPath)
        if ($LimitToExpectedSchema) { $arguments += @('--schema', $ExpectedSchemaName) }
        [void](Invoke-BackupPostgresCommand -Executable $tools.pg_dump -Arguments $arguments -Environment $postgresEnvironment)
        $stage = 'ARCHIVE_VERIFICATION'
        $stream = [IO.File]::OpenRead($dumpPath)
        try { $header = [byte[]]::new(5); if ($stream.Read($header, 0, 5) -ne 5 -or [Text.Encoding]::ASCII.GetString($header) -ne 'PGDMP') { throw 'BACKUP_ARCHIVE: El archivo no es un dump custom válido.' } }
        finally { $stream.Dispose() }
        $catalog = Invoke-BackupPostgresCommand -Executable $tools.pg_restore -Arguments @('--list', $dumpPath)
        if ($catalog -notmatch ('TABLE DATA\s+' + [regex]::Escape($ExpectedSchemaName) + '\s+users\s')) { throw 'BACKUP_ARCHIVE: El catálogo no contiene los datos esperados.' }
        [IO.File]::WriteAllText((Join-Path $temporaryBackup $listName), $catalog, [Text.UTF8Encoding]::new($false))
        $hash = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash.ToLowerInvariant()
        [IO.File]::WriteAllText((Join-Path $temporaryBackup $hashName), "$hash  $dumpName`n", [Text.UTF8Encoding]::new($false))
        $manifest = [ordered]@{ formatVersion = 1; provider = $(if ($LocalTestOnly) { 'local-isolated-test' } else { 'Render' });
            expectedDatabase = $ExpectedDatabaseName; expectedSchema = $ExpectedSchemaName; scope = $(if ($LimitToExpectedSchema) { 'schema' } else { 'database' });
            createdAtUtc = $now.ToString('o'); createdAtBogota = $businessTime.ToString('o'); postgresClientMajor = 18;
            sourceServerVersionNum = $metadata.serverVersionNum; tlsMode = $(if ($LocalTestOnly) { 'local-test-only' } else { 'verify-full' });
            tlsProtocol = $metadata.tlsVersion; dumpFile = $dumpName; bytes = (Get-Item -LiteralPath $dumpPath).Length; sha256 = $hash;
            sourceIdentityVerified = $true; catalogVerified = $true; catalogEntryCount = @($catalog -split "`r?`n" | Where-Object { $_ -and -not $_.StartsWith(';') }).Count;
            restoreVerified = $false; restoreVerification = 'NOT_PERFORMED'; globalRolesIncluded = $false }
        [IO.File]::WriteAllText((Join-Path $temporaryBackup $manifestName), ($manifest | ConvertTo-Json -Depth 5) + "`n", [Text.UTF8Encoding]::new($false))
        $stage = 'PRIVATE_TEMPORARY_CLEANUP'
        Remove-OwnedBackupTemporaryDirectory -Root $temporaryRoot -Path $temporaryCredentials -FileNames @('pgpass.conf','trusted-roots.pem')
        $temporaryCredentials = $null
        $stage = 'PROMOTION'
        Assert-BackupChildPath -Root $output -Child $temporaryBackup
        Assert-BackupChildPath -Root $output -Child $finalDirectory
        [void](Resolve-SafeBackupOutputDirectory -Directory $output)
        [IO.Directory]::Move($temporaryBackup, $finalDirectory)
        $promoted = $true
        return [pscustomobject]@{ Status = 'BACKUP_VERIFIED'; BackupDirectory = $finalDirectory; DumpPath = (Join-Path $finalDirectory $dumpName);
            ManifestPath = (Join-Path $finalDirectory $manifestName); Sha256 = $hash; Bytes = $manifest.bytes;
            CatalogVerified = $true; RestoreVerified = $false; TlsMode = $manifest.tlsMode }
    } catch {
        $known = [string]$_.Exception.Message
        if (-not $known.StartsWith('BACKUP_')) { $known = 'BACKUP_FAILED: No se completó la operación; se omitieron diagnósticos privados.' }
        throw [InvalidOperationException]::new("Etapa $stage. $known")
    } finally {
        if ($null -ne $temporaryCredentials) {
            try { Remove-OwnedBackupTemporaryDirectory -Root $temporaryRoot -Path $temporaryCredentials -FileNames @('pgpass.conf','trusted-roots.pem') }
            catch { [Console]::Error.WriteLine('BACKUP_CLEANUP: Revisa los temporales privados del respaldo; no se muestran sus contenidos.') }
        }
        if (-not $promoted -and $null -ne $temporaryBackup) {
            try { Remove-OwnedBackupTemporaryDirectory -Root $OutputDirectory -Path $temporaryBackup -FileNames $dumpFiles }
            catch { [Console]::Error.WriteLine('BACKUP_CLEANUP: Quedó un directorio temporal privado sin promocionar.') }
        }
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    try {
        Invoke-RenderDatabaseBackup -ExpectedDatabaseName $ExpectedDatabaseName -ExpectedSchemaName $ExpectedSchemaName -OutputDirectory $OutputDirectory `
            -ConnectionPath $ConnectionPath -PostgresBinDirectory $PostgresBinDirectory -SslRootCertificatePath $SslRootCertificatePath `
            -UseEnvironmentConnection $UseEnvironmentConnection.IsPresent -LimitToExpectedSchema $LimitToExpectedSchema.IsPresent `
            -LocalTestOnly $LocalTestOnly.IsPresent -ValidateOnly $ValidateOnly.IsPresent | ConvertTo-Json -Depth 6
    } catch { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }
}
