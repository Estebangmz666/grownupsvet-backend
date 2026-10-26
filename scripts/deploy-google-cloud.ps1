[CmdletBinding()]
param(
    [string]$ProjectId,
    [string]$Region,
    [string]$ServiceName,
    [string]$ImageUri,
    [ValidateSet('CloudSql', 'ExternalPostgres')]
    [string]$DatabaseProvider = 'CloudSql',
    [string]$CloudSqlInstanceConnectionName,
    [string]$ExternalDatabaseHost,
    [int]$ExternalDatabasePort = 5432,
    [string]$ServiceAccountEmail,
    [string]$DatabaseName,
    [string]$DatabaseUsername,
    [ValidateSet('Mailtrap', 'Gmail')]
    [string]$MailProvider = 'Mailtrap',
    [string]$MailFrom,
    [decimal]$ApprovedMonthlyBudgetUsd,
    [hashtable]$SecretBindings = @{},
    [string]$FrontendBaseUrl = '',
    [string[]]$CorsAllowedOrigins = @(),
    [string]$StaffActivationUrl = '',
    [string]$BackendBaseUrl = '',
    [string]$TrustedProxyPattern = '^$',
    [string]$JwtKeyAlias = 'grownupsvet-jwt-signing-2026-09',
    [switch]$EnableInvitations,
    [switch]$EnableBootstrap,
    [switch]$DisablePasswordRecovery,
    [switch]$ValidateOnly,
    [switch]$Execute
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Matches([string]$Value, [string]$Pattern, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -notmatch $Pattern) {
        throw "Falta un valor válido para $Label."
    }
}

function Assert-PublicHttpsUrl([string]$Value, [bool]$OriginOnly, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return }
    $parsedUri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$parsedUri) -or
        $parsedUri.Scheme -ne 'https' -or $parsedUri.UserInfo -ne '' -or
        $parsedUri.Query -ne '' -or $parsedUri.Fragment -ne '' -or
        $parsedUri.IsLoopback -or $Value.Contains('*') -or
        $parsedUri.Host -match '(^|\.)(example\.(com|net|org)|invalid|example|test)$' -or
        ($OriginOnly -and $Value -ne $parsedUri.GetLeftPart([UriPartial]::Authority))) {
        throw "$Label requiere una URL HTTPS real; CORS admite únicamente orígenes exactos."
    }
}

if ($Execute -and $ValidateOnly) { throw 'Elige Execute o ValidateOnly.' }
Assert-Matches $ProjectId '^[a-z][a-z0-9-]{4,28}[a-z0-9]$' 'ProjectId'
Assert-Matches $Region '^[a-z]+-[a-z]+[0-9]+$' 'Region'
Assert-Matches $ServiceName '^[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$' 'ServiceName'
Assert-Matches $ImageUri '^[a-z0-9-]+-docker\.pkg\.dev/[a-z][a-z0-9-]{4,28}[a-z0-9]/[a-z0-9._/-]+(:[a-zA-Z0-9._-]+|@sha256:[a-f0-9]{64})$' 'ImageUri'
Assert-Matches $ServiceAccountEmail '^[a-z][a-z0-9-]{4,28}[a-z0-9]@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$' 'ServiceAccountEmail'
Assert-Matches $DatabaseName '^[a-z][a-z0-9_]{0,62}$' 'DatabaseName'
Assert-Matches $DatabaseUsername '^[a-z][a-z0-9_]{0,62}$' 'DatabaseUsername'
Assert-Matches $MailFrom '^[^\s@,;<>]+@[^\s@,;<>]+\.[^\s@,;<>]+$' 'MailFrom'
Assert-Matches $JwtKeyAlias '^[a-zA-Z0-9._-]+$' 'JwtKeyAlias'
if ($ApprovedMonthlyBudgetUsd -le 0) { throw 'Indica el presupuesto mensual aprobado; esto no configura un corte automático de gasto.' }
if (-not $ServiceAccountEmail.EndsWith("@$ProjectId.iam.gserviceaccount.com")) {
    throw 'La cuenta de servicio debe pertenecer al proyecto seleccionado.'
}
if ($DatabaseProvider -eq 'CloudSql') {
    Assert-Matches $CloudSqlInstanceConnectionName '^[a-z][a-z0-9-]{4,28}[a-z0-9]:[a-z0-9-]+:[a-z][a-z0-9-]{0,96}$' 'CloudSqlInstanceConnectionName'
    if ($CloudSqlInstanceConnectionName.Split(':')[0] -ne $ProjectId -or
        $CloudSqlInstanceConnectionName.Split(':')[1] -ne $Region) {
        throw 'Proyecto y región SQL deben coincidir con el despliegue seleccionado.'
    }
    if (-not [string]::IsNullOrWhiteSpace($ExternalDatabaseHost)) {
        throw 'CloudSql no admite un host PostgreSQL externo.'
    }
} else {
    Assert-Matches $ExternalDatabaseHost '^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$' 'ExternalDatabaseHost'
    if ($ExternalDatabaseHost -match '(^|\.)(localhost|local|internal|invalid|example|test|example\.(com|net|org))$' -or
        $ExternalDatabasePort -lt 1 -or $ExternalDatabasePort -gt 65535) {
        throw 'PostgreSQL externo requiere un hostname remoto real y un puerto válido.'
    }
    if (-not [string]::IsNullOrWhiteSpace($CloudSqlInstanceConnectionName)) {
        throw 'ExternalPostgres no admite una instancia Cloud SQL.'
    }
}
if ([string]::IsNullOrWhiteSpace($TrustedProxyPattern) -or $TrustedProxyPattern.Contains('.*') -or
    $TrustedProxyPattern.Contains("`n") -or $TrustedProxyPattern.Contains("`r")) {
    throw 'No se permite confiar en cualquier proxy.'
}
try { [void][regex]::new($TrustedProxyPattern) }
catch { throw 'TrustedProxyPattern no es una expresión válida.' }
Assert-PublicHttpsUrl $FrontendBaseUrl $false 'FrontendBaseUrl'
Assert-PublicHttpsUrl $BackendBaseUrl $false 'BackendBaseUrl'
foreach ($origin in $CorsAllowedOrigins) { Assert-PublicHttpsUrl $origin $true 'CorsAllowedOrigins' }
if ($EnableInvitations) {
    if ([string]::IsNullOrWhiteSpace($StaffActivationUrl)) { throw 'EnableInvitations requiere StaffActivationUrl real.' }
    Assert-PublicHttpsUrl $StaffActivationUrl $false 'StaffActivationUrl'
}

$jwtMountPath = '/var/secrets/jwt/jwt-signing.p12'
$requiredBindings = @('DB_PASSWORD', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'JWT_KEYSTORE_PASSWORD', $jwtMountPath)
if (-not $DisablePasswordRecovery) {
    $requiredBindings += @('PASSWORD_RECOVERY_HMAC_SECRET', 'PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY')
}
if ($EnableInvitations) { $requiredBindings += 'STAFF_INVITATION_ENCRYPTION_KEY' }
if ($EnableBootstrap) { $requiredBindings += @('SUPER_ADMIN_EMAIL', 'SUPER_ADMIN_PASSWORD') }
foreach ($binding in $requiredBindings) {
    if (-not $SecretBindings.ContainsKey($binding)) { throw "Falta referencia Secret Manager para $binding." }
}
$allowedSecretBindings = @('DB_PASSWORD', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'JWT_KEYSTORE_PASSWORD', 'JWT_KEY_PASSWORD',
    'PASSWORD_RECOVERY_HMAC_SECRET', 'PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY', 'STAFF_INVITATION_ENCRYPTION_KEY',
    'SUPER_ADMIN_EMAIL', 'SUPER_ADMIN_PASSWORD', $jwtMountPath)
foreach ($binding in $SecretBindings.GetEnumerator()) {
    if ($binding.Key -notin $allowedSecretBindings -or [string]$binding.Value -notmatch '^[a-zA-Z0-9_-]{1,255}:[1-9][0-9]*$') {
        throw 'SecretBindings admite únicamente los nombres documentados y referencias secret-name:version numérica; nunca valores de secretos.'
    }
}
if (-not $EnableBootstrap -and ($SecretBindings.ContainsKey('SUPER_ADMIN_EMAIL') -or
    $SecretBindings.ContainsKey('SUPER_ADMIN_PASSWORD'))) {
    throw 'Incluye referencias de bootstrap únicamente al habilitar el aprovisionamiento inicial.'
}

$smtpEndpoint = switch ($MailProvider) {
    'Mailtrap' { @{ Host = 'sandbox.smtp.mailtrap.io'; Port = '2525' } }
    'Gmail' { @{ Host = 'smtp.gmail.com'; Port = '587' } }
}

$runtimeEnvironment = [ordered]@{
    SPRING_PROFILES_ACTIVE = 'prod'
    DB_NAME = $DatabaseName
    DB_USERNAME = $DatabaseUsername
    MAIL_HOST = $smtpEndpoint.Host
    MAIL_PORT = $smtpEndpoint.Port
    MAIL_FROM = $MailFrom
    JWT_KEYSTORE_PATH = $jwtMountPath
    JWT_KEY_ALIAS = $JwtKeyAlias
    PASSWORD_RECOVERY_ENABLED = (-not $DisablePasswordRecovery).ToString().ToLowerInvariant()
    STAFF_INVITATIONS_ENABLED = $EnableInvitations.IsPresent.ToString().ToLowerInvariant()
    SUPER_ADMIN_BOOTSTRAP_ENABLED = $EnableBootstrap.IsPresent.ToString().ToLowerInvariant()
    APPOINTMENT_WORKDAY_START_TIME = '06:00'
    FRONTEND_BASE_URL = $FrontendBaseUrl
    CORS_ALLOWED_ORIGINS = ($CorsAllowedOrigins -join ',')
    STAFF_ACTIVATION_URL = $StaffActivationUrl
    BACKEND_BASE_URL = $BackendBaseUrl
    TRUSTED_PROXY_PATTERN = $TrustedProxyPattern
}
if ($DatabaseProvider -eq 'CloudSql') {
    $runtimeEnvironment.CLOUD_SQL_INSTANCE_CONNECTION_NAME = $CloudSqlInstanceConnectionName
    $runtimeEnvironment.CLOUD_SQL_IP_TYPES = 'PUBLIC'
} else {
    $runtimeEnvironment.SPRING_PROFILES_ACTIVE = 'prod,external-postgres'
    $runtimeEnvironment.SPRING_DATASOURCE_URL = "jdbc:postgresql://${ExternalDatabaseHost}:${ExternalDatabasePort}/${DatabaseName}"
}

if (-not $Execute) {
    [pscustomobject]@{
        Validation = 'OK'
        ProjectId = $ProjectId
        Region = $Region
        ServiceName = $ServiceName
        DatabaseProvider = $DatabaseProvider
        MailProvider = $MailProvider
        MailHost = $smtpEndpoint.Host
        MailPort = $smtpEndpoint.Port
        ApprovedMonthlyBudgetUsd = $ApprovedMonthlyBudgetUsd
        Cpu = 1
        Memory = '2Gi'
        MinimumInstances = 1
        MaximumInstances = 1
        Concurrency = 8
        SecretBindingCount = $SecretBindings.Count
        CloudActionsExecuted = $false
    }
    return
}

# Only Execute reaches gcloud. This script neither creates prerequisites nor installs/authenticates tools.
$gcloudCommand = Get-Command gcloud -ErrorAction Stop
$environmentFile = [IO.Path]::GetTempFileName()
try {
    [IO.File]::WriteAllText($environmentFile, ($runtimeEnvironment | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    $secretArguments = ($SecretBindings.GetEnumerator() | Sort-Object Key | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ','
    $deploymentArguments = @('run', 'deploy', $ServiceName, '--project', $ProjectId, '--region', $Region,
        '--image', $ImageUri, '--service-account', $ServiceAccountEmail, '--execution-environment', 'gen2',
        '--no-cpu-throttling', '--min', '1', '--max', '1', '--cpu', '1', '--memory', '2Gi', '--concurrency', '8',
        '--port', '8080', '--allow-unauthenticated', '--env-vars-file', $environmentFile,
        '--update-secrets', $secretArguments,
        '--startup-probe', 'httpGet.path=/actuator/health/readiness,httpGet.port=8080,timeoutSeconds=5,periodSeconds=10,failureThreshold=24',
        '--liveness-probe', 'httpGet.path=/actuator/health/liveness,httpGet.port=8080,timeoutSeconds=5,periodSeconds=30,failureThreshold=3',
        '--readiness-probe', 'httpGet.path=/actuator/health/readiness,httpGet.port=8080,timeoutSeconds=5,periodSeconds=10,failureThreshold=3',
        '--quiet')
    if (-not $EnableBootstrap) { $deploymentArguments += @('--remove-secrets', 'SUPER_ADMIN_EMAIL,SUPER_ADMIN_PASSWORD') }
    & $gcloudCommand.Source @deploymentArguments
    if ($LASTEXITCODE -ne 0) { throw 'Cloud Run no confirmó el despliegue; revisa sus diagnósticos sin compartir credenciales.' }
    & $gcloudCommand.Source run services describe $ServiceName --project $ProjectId --region $Region --format 'value(status.url)'
    if ($LASTEXITCODE -ne 0) { throw 'El despliegue terminó, pero no se pudo consultar su URL.' }
} finally {
    Remove-Item -LiteralPath $environmentFile -Force -ErrorAction SilentlyContinue
}
