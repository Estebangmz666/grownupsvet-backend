#Requires -Version 7.2
[CmdletBinding()]
param(
    [string]$PostgresBinDirectory = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$LocalDatabaseName = 'grownupsvet_test',
    [string]$TestOutputRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$helper = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\backup-render-database.ps1'))
. $helper -PostgresBinDirectory $PostgresBinDirectory
$checks = [Collections.Generic.List[string]]::new()

function Assert-BackupCheck {
    param([bool]$Condition, [string]$Name)
    if (-not $Condition) { throw "Falló la verificación: $Name" }
    $checks.Add($Name)
}

function Invoke-BackupFixtureProcess {
    param([string]$Url, [string[]]$Arguments)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Process -Id $PID).Path
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $essentials = @{}
    foreach ($key in @('SystemRoot','WINDIR','SystemDrive','COMSPEC','PATH','PATHEXT','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA')) {
        if ($start.Environment.ContainsKey($key)) { $essentials[$key] = $start.Environment[$key] }
    }
    $start.Environment.Clear()
    foreach ($key in $essentials.Keys) { $start.Environment[$key] = $essentials[$key] }
    $start.Environment['RENDER_DATABASE_URL'] = $Url
    foreach ($argument in @('-NoLogo','-NoProfile','-NonInteractive','-File',$helper,'-UseEnvironmentConnection') + $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(60000)) { $process.Kill($true); throw 'La prueba del helper excedió un minuto.' }
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout.GetAwaiter().GetResult(); Error = $stderr.GetAwaiter().GetResult() }
    } finally { $process.Dispose() }
}

function Assert-OwnedLocalSchema {
    param([string]$Schema, [hashtable]$Environment)
    if ($Schema -notmatch '^backup_render_test_[0-9a-f]{32}$' -or $Environment.PGHOST -ne '127.0.0.1' -or $Environment.PGDATABASE -notmatch '_test$') {
        throw 'La limpieza solo admite el esquema UUID y la base local de esta prueba.'
    }
    $connectedDatabase = (Invoke-BackupPostgresCommand -Executable (Join-Path $PostgresBinDirectory 'psql.exe') -Arguments @('--no-password','--no-psqlrc','--tuples-only','--no-align','--command','select current_database()') -Environment $Environment).Trim()
    if ($connectedDatabase -cne $LocalDatabaseName) { throw 'La base local efectiva cambió; no se permite limpiar.' }
}

function Invoke-BackupFixtureSql {
    param([string]$Sql, [hashtable]$Environment)
    return Invoke-BackupPostgresCommand -Executable (Join-Path $PostgresBinDirectory 'psql.exe') -Arguments @('--no-password','--no-psqlrc','--tuples-only','--no-align','--quiet','--set','ON_ERROR_STOP=1','--set','VERBOSITY=sqlstate','--command',$Sql) -Environment $Environment -TimeoutSeconds 60
}

function Remove-OwnedLocalSchema {
    param([string]$Schema, [hashtable]$Environment)
    Assert-OwnedLocalSchema -Schema $Schema -Environment $Environment
    [void](Invoke-BackupFixtureSql -Sql "DROP SCHEMA IF EXISTS $Schema CASCADE" -Environment $Environment)
}

$schema = 'backup_render_test_' + [Guid]::NewGuid().ToString('N')
$privateTemporary = $null; $databaseEnvironment = $null; $schemaCreated = $false
$testStage = 'OFFLINE_GUARDS'
$temporaryRoot = [IO.Path]::GetTempPath()
try {
    $businessTime = [TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow, [TimeZoneInfo]::FindSystemTimeZoneById('SA Pacific Standard Time'))
    if ([string]::IsNullOrWhiteSpace($TestOutputRoot)) {
        $TestOutputRoot = Join-Path ([Environment]::GetFolderPath('MyDocuments')) ('GrownupsVetBackups\verification-' + $businessTime.ToString('yyyy-MM-dd') + '\' + [Guid]::NewGuid().ToString('N'))
    }
    $testRoot = Resolve-SafeBackupOutputDirectory -Directory $TestOutputRoot
    Set-PrivateBackupDirectory -Path $testRoot
    $guardOutput = Join-Path $testRoot 'guards'
    $marker = 'GuardSecretMarker2026'
    $fakeRenderUrl = "postgresql://backup_fixture:$marker@dpg-fictional.ohio-postgres.render.com/grownupsvet"
    $baselineArguments = @('-ValidateOnly','-ExpectedDatabaseName','grownupsvet','-OutputDirectory',$guardOutput)
    $positive = Invoke-BackupFixtureProcess -Url $fakeRenderUrl -Arguments ($baselineArguments + @('-PostgresBinDirectory',$PostgresBinDirectory))
    Assert-BackupCheck -Condition ($positive.ExitCode -eq 0) -Name 'Validación offline acepta un origen externo compatible sin conectarse'
    $offline = $positive.Output | ConvertFrom-Json
    Assert-BackupCheck -Condition (-not $offline.Connected -and $offline.TlsMode -eq 'verify-full') -Name 'Validación offline exige verify-full y declara Connected=false'
    $repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
    $guardCases = @(
        @{ Name = 'Destino Git rechazado'; Url = $fakeRenderUrl; Arguments = @('-ValidateOnly','-ExpectedDatabaseName','grownupsvet','-OutputDirectory',(Join-Path $repository 'backup-forbidden')) },
        @{ Name = 'Base esperada incompatible rechazada'; Url = $fakeRenderUrl; Arguments = @('-ValidateOnly','-ExpectedDatabaseName','another_database','-OutputDirectory',$guardOutput) },
        @{ Name = 'Host externo ajeno rechazado'; Url = "postgresql://backup_fixture:$marker@untrusted.example.com/grownupsvet"; Arguments = $baselineArguments },
        @{ Name = 'Opciones TLS inseguras rechazadas'; Url = "$fakeRenderUrl`?sslmode=disable"; Arguments = $baselineArguments },
        @{ Name = 'Modo local nunca permite host Render'; Url = $fakeRenderUrl; Arguments = @('-ValidateOnly','-LocalTestOnly','-LimitToExpectedSchema','-ExpectedDatabaseName','grownupsvet','-ExpectedSchemaName',$schema,'-OutputDirectory',$guardOutput) },
        @{ Name = 'Modo local no admite public'; Url = "postgresql://backup_fixture:$marker@127.0.0.1:5432/$LocalDatabaseName"; Arguments = @('-ValidateOnly','-LocalTestOnly','-LimitToExpectedSchema','-ExpectedDatabaseName',$LocalDatabaseName,'-ExpectedSchemaName','public','-OutputDirectory',$guardOutput) },
        @{ Name = 'Modo local exige selección exclusiva de esquema'; Url = "postgresql://backup_fixture:$marker@127.0.0.1:5432/$LocalDatabaseName"; Arguments = @('-ValidateOnly','-LocalTestOnly','-ExpectedDatabaseName',$LocalDatabaseName,'-ExpectedSchemaName',$schema,'-OutputDirectory',$guardOutput) }
    )
    foreach ($case in $guardCases) {
        $result = Invoke-BackupFixtureProcess -Url $case.Url -Arguments ($case.Arguments + @('-PostgresBinDirectory',$PostgresBinDirectory))
        Assert-BackupCheck -Condition ($result.ExitCode -ne 0 -and ($result.Output + $result.Error).Contains('BACKUP_')) -Name $case.Name
        Assert-BackupCheck -Condition (-not ($result.Output + $result.Error).Contains($marker)) -Name ($case.Name + ': sin credencial en diagnósticos')
    }
    $junction = Join-Path $testRoot 'repository-junction'
    try {
        [void](New-Item -ItemType Junction -Path $junction -Target $repository)
        $junctionResult = Invoke-BackupFixtureProcess -Url $fakeRenderUrl -Arguments @('-ValidateOnly','-OutputDirectory',(Join-Path $junction 'forbidden'))
        Assert-BackupCheck -Condition ($junctionResult.ExitCode -ne 0) -Name 'Junction hacia repositorio rechazada antes de escribir'
    } finally {
        if (Test-Path -LiteralPath $junction) { Assert-BackupChildPath -Root $testRoot -Child $junction; Remove-Item -LiteralPath $junction -Force }
    }

    $rsa = [Security.Cryptography.RSA]::Create(2048)
    $leafCertificate = $null
    try {
        $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=fictional.invalid', $rsa,
            [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
        $leafCertificate = $request.CreateSelfSigned([DateTimeOffset]::UtcNow.AddMinutes(-1), [DateTimeOffset]::UtcNow.AddDays(1))
        $leafPath = Join-Path $testRoot 'untrusted-leaf-public.pem'
        [IO.File]::WriteAllText($leafPath, $leafCertificate.ExportCertificatePem(), [Text.ASCIIEncoding]::new())
        $rejectedLeaf = $false
        try { Write-BackupTrustedRoots -Path (Join-Path $testRoot 'should-not-trust-leaf.pem') -ExplicitCertificatePath $leafPath }
        catch { $rejectedLeaf = $_.Exception.Message.StartsWith('BACKUP_TLS:') }
        Assert-BackupCheck -Condition $rejectedLeaf -Name 'Un certificado de servidor no se convierte en CA confiable'
    } finally {
        if ($null -ne $leafCertificate) { $leafCertificate.Dispose() }
        $rsa.Dispose()
    }

    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        throw 'Faltan credenciales locales de prueba; no se simula una restauración aprobada.'
    }
    if ($LocalDatabaseName -notmatch '_test$') { throw 'El ensayo exige una base terminada en _test.' }
    $connection = [pscustomobject]@{ Host = '127.0.0.1'; Port = 5432; Database = $LocalDatabaseName;
        Username = $env:DB_USERNAME; Password = (ConvertTo-SecureString $env:DB_PASSWORD -AsPlainText -Force) }
    Assert-BackupConnection -Connection $connection -Database $LocalDatabaseName -Schema $schema -Local $true -SchemaSelection $true
    $privateTemporary = Join-Path $temporaryRoot ('grownupsvet-backup-tests-' + [Guid]::NewGuid().ToString('N'))
    Set-PrivateBackupDirectory -Path $privateTemporary
    $passFile = Join-Path $privateTemporary 'pgpass.conf'
    Write-BackupPassFile -Connection $connection -Path $passFile
    $databaseEnvironment = New-BackupPostgresEnvironment -Connection $connection -PassFile $passFile -RootCertificate '' -Local $true
    $testStage = 'LOCAL_SOURCE_CHECK'
    Assert-OwnedLocalSchema -Schema $schema -Environment $databaseEnvironment
    $alreadyExists = (Invoke-BackupFixtureSql -Sql "select exists(select 1 from pg_namespace where nspname='$schema')" -Environment $databaseEnvironment).Trim()
    if ($alreadyExists -ne 'f') { throw 'El esquema de prueba ya existía; no se modificará.' }
    $ownerId = [Guid]::NewGuid(); $petId = [Guid]::NewGuid(); $appointmentId = [Guid]::NewGuid()
    $create = "BEGIN; CREATE SCHEMA $schema; " +
        "CREATE TABLE $schema.flyway_schema_history(installed_rank integer primary key,version text,checksum integer,success boolean); " +
        "CREATE TABLE $schema.users(id uuid primary key,email text,password_hash text); " +
        "CREATE TABLE $schema.pets(id uuid primary key,owner_id uuid references $schema.users(id),name text,photo bytea); " +
        "CREATE TABLE $schema.appointments(id uuid primary key,pet_id uuid references $schema.pets(id),starts_at timestamptz); " +
        "INSERT INTO $schema.flyway_schema_history VALUES(1,'22',12345,true); " +
        "INSERT INTO $schema.users VALUES('$ownerId','fixture@example.test','fixture-hash-for-restore-only'); " +
        "INSERT INTO $schema.pets VALUES('$petId','$ownerId',U&'Luna <prueba> & restauraci\00F3n',decode('00ff10ab','hex')); " +
        "INSERT INTO $schema.appointments VALUES('$appointmentId','$petId','2026-11-13T09:00:00-05:00'); COMMIT;"
    $testStage = 'LOCAL_FIXTURE_CREATE'
    [void](Invoke-BackupFixtureSql -Sql $create -Environment $databaseEnvironment)
    $schemaCreated = $true
    $snapshotQuery = "select json_build_object('users',(select count(*) from $schema.users),'pets',(select count(*) from $schema.pets)," +
        "'appointments',(select count(*) from $schema.appointments),'photo',(select encode(photo,'hex') from $schema.pets where id='$petId')," +
        "'name',(select name from $schema.pets where id='$petId'),'flyway',(select version||':'||checksum::text||':'||success::text from $schema.flyway_schema_history where installed_rank=1))"
    $testStage = 'LOCAL_SNAPSHOT'
    $before = (Invoke-BackupFixtureSql -Sql $snapshotQuery -Environment $databaseEnvironment).Trim()
    $localUrl = 'postgresql://' + [Uri]::EscapeDataString($env:DB_USERNAME) + ':' + [Uri]::EscapeDataString($env:DB_PASSWORD) + '@127.0.0.1:5432/' + $LocalDatabaseName
    $temporaryFoldersBefore = @(Get-ChildItem -LiteralPath $temporaryRoot -Directory -Filter 'grownupsvet-backup-credentials-*' | ForEach-Object FullName)
    $failureOutput = Join-Path $testRoot 'wrong-schema'
    $wrongSchema = 'backup_render_test_' + [Guid]::NewGuid().ToString('N')
    $wrongSchemaResult = Invoke-BackupFixtureProcess -Url $localUrl -Arguments @('-LocalTestOnly','-LimitToExpectedSchema','-ExpectedDatabaseName',$LocalDatabaseName,
        '-ExpectedSchemaName',$wrongSchema,'-OutputDirectory',$failureOutput,'-PostgresBinDirectory',$PostgresBinDirectory)
    Assert-BackupCheck -Condition ($wrongSchemaResult.ExitCode -ne 0) -Name 'La comprobación real de esquema detiene un origen inexistente'
    Assert-BackupCheck -Condition (@(Get-ChildItem -LiteralPath $failureOutput -File -Recurse -ErrorAction SilentlyContinue).Count -eq 0) -Name 'Un origen inválido no promociona archivos parciales'
    $result = Invoke-BackupFixtureProcess -Url $localUrl -Arguments @('-LocalTestOnly','-LimitToExpectedSchema','-ExpectedDatabaseName',$LocalDatabaseName,
        '-ExpectedSchemaName',$schema,'-OutputDirectory',(Join-Path $testRoot 'archive'),'-PostgresBinDirectory',$PostgresBinDirectory)
    Assert-BackupCheck -Condition ($result.ExitCode -eq 0) -Name 'Dump custom real del esquema propio completado'
    Assert-BackupCheck -Condition (-not ($result.Output + $result.Error).Contains($env:DB_PASSWORD)) -Name 'Dump sin contraseña en stdout o stderr'
    $backup = $result.Output | ConvertFrom-Json
    $manifest = Get-Content -LiteralPath $backup.ManifestPath -Raw | ConvertFrom-Json
    Assert-BackupCheck -Condition ($manifest.catalogVerified -and -not $manifest.restoreVerified -and $manifest.scope -eq 'schema') -Name 'Manifiesto distingue catálogo de restauración'
    Assert-BackupCheck -Condition ((Get-FileHash -LiteralPath $backup.DumpPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $backup.Sha256) -Name 'SHA-256 coincide con el archivo final'
    $privateText = (Get-Content -LiteralPath $backup.ManifestPath -Raw) + $result.Output + $result.Error
    Assert-BackupCheck -Condition (-not $privateText.Contains($env:DB_PASSWORD) -and -not $privateText.Contains($localUrl)) -Name 'Manifiesto saneado sin URI ni contraseña'
    Assert-BackupCheck -Condition (@(Get-ChildItem -LiteralPath $backup.BackupDirectory -File).Count -eq 4) -Name 'Solo dump, catálogo, SHA y manifiesto fueron promocionados'
    $temporaryFoldersAfter = @(Get-ChildItem -LiteralPath $temporaryRoot -Directory -Filter 'grownupsvet-backup-credentials-*' | ForEach-Object FullName)
    Assert-BackupCheck -Condition ((($temporaryFoldersBefore | Sort-Object) -join "`n") -ceq (($temporaryFoldersAfter | Sort-Object) -join "`n")) -Name 'Temporales de credenciales eliminados tras fallo y éxito'
    $collisionResult = Invoke-BackupFixtureProcess -Url $localUrl -Arguments @('-LocalTestOnly','-LimitToExpectedSchema','-ExpectedDatabaseName',$LocalDatabaseName,
        '-ExpectedSchemaName',$schema,'-OutputDirectory',$repository,'-PostgresBinDirectory',$PostgresBinDirectory)
    Assert-BackupCheck -Condition ($collisionResult.ExitCode -ne 0) -Name 'Origen válido tampoco permite respaldo dentro de Git'

    Remove-OwnedLocalSchema -Schema $schema -Environment $databaseEnvironment
    $absent = (Invoke-BackupFixtureSql -Sql "select exists(select 1 from pg_namespace where nspname='$schema')" -Environment $databaseEnvironment).Trim()
    Assert-BackupCheck -Condition ($absent -eq 'f') -Name 'Solo el esquema propio fue retirado antes de restaurar'
    [void](Invoke-BackupPostgresCommand -Executable (Join-Path $PostgresBinDirectory 'pg_restore.exe') -Arguments @('--no-password','--exit-on-error','--single-transaction','--no-owner','--no-privileges','--dbname',$LocalDatabaseName,$backup.DumpPath) -Environment $databaseEnvironment -TimeoutSeconds 60)
    $after = (Invoke-BackupFixtureSql -Sql $snapshotQuery -Environment $databaseEnvironment).Trim()
    Assert-BackupCheck -Condition ($before -ceq $after) -Name 'Restauración conserva filas, FK, texto Unicode, bytea e historial ficticio'
    Remove-OwnedLocalSchema -Schema $schema -Environment $databaseEnvironment
    $schemaCreated = $false
    $absentAfterCleanup = (Invoke-BackupFixtureSql -Sql "select exists(select 1 from pg_namespace where nspname='$schema')" -Environment $databaseEnvironment).Trim()
    Assert-BackupCheck -Condition ($absentAfterCleanup -eq 'f') -Name 'Limpieza final confirma ausencia del esquema propio'
    $report = [ordered]@{ success = $true; checksPassed = $checks.Count; checkedAtUtc = [DateTimeOffset]::UtcNow.ToString('o');
        checks = $checks; localDatabase = $LocalDatabaseName; sourceScope = 'synthetic-isolated-schema';
        sourceDatabaseUnchangedOutsideOwnedSchema = $true; catalogVerified = $true; restoreVerified = $true; schemaCleaned = $true;
        backupDirectory = $backup.BackupDirectory; dumpSha256 = $backup.Sha256; productionRestoreVerified = $false }
    [IO.File]::WriteAllText((Join-Path $testRoot 'verification.json'), ($report | ConvertTo-Json -Depth 5) + "`n", [Text.UTF8Encoding]::new($false))
    [pscustomobject]@{ Success = $true; ChecksPassed = $checks.Count; ReportPath = (Join-Path $testRoot 'verification.json');
        CatalogVerified = $true; LocalRestoreVerified = $true; ProductionRestoreVerified = $false } | ConvertTo-Json
} catch {
    $safeMessage = if ($_.Exception.Message.StartsWith('Falló la verificación:') -or $_.Exception.Message.StartsWith('BACKUP_')) { $_.Exception.Message } else { 'Se interrumpió el ensayo; se omitieron diagnósticos privados.' }
    [Console]::Error.WriteLine("$safeMessage Etapa: $testStage. Comprobaciones completadas: $($checks.Count). Tipo: $($_.Exception.GetType().Name). Línea de herramienta: $($_.InvocationInfo.ScriptLineNumber).")
    exit 1
} finally {
    if ($schemaCreated -and $null -ne $databaseEnvironment) {
        try { Remove-OwnedLocalSchema -Schema $schema -Environment $databaseEnvironment }
        catch { [Console]::Error.WriteLine('La limpieza del esquema local propio requiere revisión; no se muestran datos privados.') }
    }
    if ($null -ne $privateTemporary) {
        try { Remove-OwnedBackupTemporaryDirectory -Root $temporaryRoot -Path $privateTemporary -FileNames @('pgpass.conf') }
        catch { [Console]::Error.WriteLine('La limpieza del archivo privado de prueba requiere revisión.') }
    }
}
