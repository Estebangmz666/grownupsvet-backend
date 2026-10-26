#Requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateSet('ImageOnly', 'ConfigurationTemplate')]
    [string]$DeploymentMode = 'ImageOnly',
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
    [switch]$EnableMonitoring,
    [switch]$EnableLoginRateLimit,
    [string]$ExpectedContractVersion,
    [string]$CurrentServiceConfigurationPath,
    [string]$OutputPlanPath,
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

function Write-DeploymentEvidence([string]$Path, [object]$Value) {
    $json = $Value | ConvertTo-Json -Depth 100
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json)
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
}

function ConvertTo-CanonicalValue([object]$Value) {
    if ($Value -is [Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | Sort-Object)) { $ordered[$key] = ConvertTo-CanonicalValue $Value[$key] }
        return $ordered
    }
    if ($Value -is [Collections.IEnumerable] -and $Value -isnot [string]) {
        return ,@($Value | ForEach-Object { ConvertTo-CanonicalValue $_ })
    }
    return $Value
}

function Get-PreservedConfiguration([hashtable]$Service) {
    $copy = $Service.spec.template.spec | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
    foreach ($container in @($copy.containers)) { $container.Remove('image') }
    $templateAnnotations = [ordered]@{}
    foreach ($key in @($Service.spec.template.metadata.annotations.Keys | Sort-Object)) {
        if ($key -notin @('run.googleapis.com/client-name', 'run.googleapis.com/client-version', 'client.knative.dev/user-image')) {
            $templateAnnotations[$key] = $Service.spec.template.metadata.annotations[$key]
        }
    }
    $serviceAnnotations = [ordered]@{}
    foreach ($key in @($Service.metadata.annotations.Keys | Sort-Object)) {
        if ($key -notin @('run.googleapis.com/operation-id', 'run.googleapis.com/urls', 'serving.knative.dev/creator',
                'serving.knative.dev/lastModifier', 'run.googleapis.com/client-name', 'run.googleapis.com/client-version')) {
            $serviceAnnotations[$key] = $Service.metadata.annotations[$key]
        }
    }
    return @{ ContainerSpecification = $copy; TemplateAnnotations = $templateAnnotations; ServiceAnnotations = $serviceAnnotations }
}

function Get-ConfigurationFingerprint([hashtable]$Service) {
    $canonical = ConvertTo-CanonicalValue (Get-PreservedConfiguration $Service)
    $bytes = [Text.Encoding]::UTF8.GetBytes(($canonical | ConvertTo-Json -Depth 100 -Compress))
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Get-EnvironmentValue([object[]]$Environment, [string]$Name) {
    $entry = @($Environment | Where-Object { $_.name -eq $Name })
    if ($entry.Count -gt 1) { throw 'La configuración contiene nombres de entorno duplicados.' }
    if ($entry.Count -eq 0 -or -not $entry[0].ContainsKey('value')) { return '' }
    return [string]$entry[0].value
}

function Assert-ExistingConfiguration([hashtable]$Service) {
    if (-not $Service.ContainsKey('spec') -or -not $Service.spec.ContainsKey('template')) {
        throw 'Se requiere la descripción JSON v1 completa de un servicio Cloud Run existente.'
    }
    $containers = @($Service.spec.template.spec.containers)
    if ($containers.Count -ne 1) { throw 'ImageOnly admite exclusivamente la topología actual de un contenedor.' }
    $environment = @($containers[0].env)
    $profiles = (Get-EnvironmentValue $environment 'SPRING_PROFILES_ACTIVE').Split(',').Trim()
    if ('prod' -notin $profiles -or @($profiles | Where-Object { $_ -in @('dev', 'test', 'observability') }).Count -gt 0) {
        throw 'La actualización requiere prod y no admite perfiles dev, test u observability.'
    }
    $serviceMaximum = [string]$Service.metadata.annotations['run.googleapis.com/maxScale']
    $revisionMaximum = [string]$Service.spec.template.metadata.annotations['autoscaling.knative.dev/maxScale']
    # Cloud Run applies the lesser cap. A service cap of 1 safely preserves an older revision cap (e.g. 20).
    # Conservatively reject an explicit service cap other than 1, even if a revision currently caps at 1.
    if (($serviceMaximum -ne '' -and $serviceMaximum -ne '1') -or
            ($serviceMaximum -eq '' -and $revisionMaximum -ne '1') -or
            ($revisionMaximum -ne '' -and $revisionMaximum -notmatch '^[1-9][0-9]{0,8}$')) {
        throw 'Se requiere cap de servicio 1, o cap de revisión 1 si el servicio no lo declara.'
    }
    if ([string]$Service.spec.template.metadata.annotations['run.googleapis.com/cpu-throttling'] -ne 'false') {
        throw 'Los workers requieren conservar CPU fuera de solicitudes.'
    }
    if (-not $containers[0].ContainsKey('startupProbe') -or -not $containers[0].ContainsKey('livenessProbe')) {
        throw 'El servicio debe conservar sus probes de startup y liveness.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$Service.spec.template.spec.serviceAccountName)) {
        throw 'No se admite una identidad de ejecución implícita.'
    }
    foreach ($name in @('DB_PASSWORD', 'JWT_KEYSTORE_PASSWORD', 'LOGIN_RATE_LIMIT_HMAC_SECRET')) {
        $entry = @($environment | Where-Object { $_.name -eq $name })
        if ($entry.Count -gt 0 -and ($entry[0].ContainsKey('value') -or -not $entry[0].ContainsKey('valueFrom'))) {
            throw 'Las credenciales deben conservar referencias Secret Manager, no valores literales.'
        }
    }
    foreach ($name in @('DB_PASSWORD', 'JWT_KEYSTORE_PASSWORD')) {
        if (@($environment | Where-Object { $_.name -eq $name -and $_.ContainsKey('valueFrom') }).Count -ne 1) {
            throw 'La base y el almacén JWT requieren sus referencias Secret Manager existentes.'
        }
    }
    foreach ($entry in $environment) {
        if ($entry.ContainsKey('valueFrom') -and
                [string]$entry.valueFrom.secretKeyRef.key -notmatch '^[1-9][0-9]*$') {
            throw 'Las referencias Secret Manager deben usar versiones numéricas.'
        }
    }
    if ('monitoring' -in $profiles) {
        $monitoringEntry = @($environment | Where-Object { $_.name -eq 'MONITORING_TOKEN_SHA256' })
        if ($monitoringEntry.Count -ne 1 -or ($monitoringEntry[0].ContainsKey('value') -and
                [string]$monitoringEntry[0].value -notmatch '^[a-fA-F0-9]{64}$')) {
            throw 'El perfil monitoring requiere conservar una referencia o hash SHA-256 válido.'
        }
    }
    if ((Get-EnvironmentValue $environment 'LOGIN_RATE_LIMIT_ENABLED') -eq 'true') {
        if (@($environment | Where-Object { $_.name -eq 'LOGIN_RATE_LIMIT_HMAC_SECRET' -and $_.ContainsKey('valueFrom') }).Count -ne 1) {
            throw 'La admisión habilitada requiere una referencia Secret Manager para su HMAC.'
        }
        $proxy = Get-EnvironmentValue $environment 'TRUSTED_PROXY_PATTERN'
        if ($proxy -ne '' -and $proxy -ne '^$') { throw 'La admisión PEER no admite confianza en proxies.' }
    }
}

function Get-SanitizedServiceManifest([hashtable]$Service) {
    $container = $Service.spec.template.spec.containers[0]
    return [ordered]@{
        ServiceName = $Service.metadata.name
        Image = $container.image
        ConfigurationFingerprint = Get-ConfigurationFingerprint $Service
        ServiceAccount = $Service.spec.template.spec.serviceAccountName
        Profiles = Get-EnvironmentValue @($container.env) 'SPRING_PROFILES_ACTIVE'
        ServiceMaximumInstances = $Service.metadata.annotations['run.googleapis.com/maxScale']
        RevisionMaximumInstances = $Service.spec.template.metadata.annotations['autoscaling.knative.dev/maxScale']
        Environment = @($container.env | ForEach-Object {
            if ($_.ContainsKey('valueFrom')) {
                @{ Name = $_.name; Kind = 'SecretManagerReference'; Secret = $_.valueFrom.secretKeyRef.name; Version = $_.valueFrom.secretKeyRef.key }
            } else { @{ Name = $_.name; Kind = 'LiteralValueOmitted' } }
        })
        LatestReadyRevision = $Service.status.latestReadyRevisionName
        LatestCreatedRevision = $Service.status.latestCreatedRevisionName
        Traffic = $Service.status.traffic
    }
}

function Invoke-GcloudJson([Management.Automation.CommandInfo]$Command, [string[]]$Arguments) {
    try { $diagnostics = @(& $Command @Arguments 2>$null) }
    catch { throw 'gcloud no confirmó la operación; los diagnósticos privados no se imprimen.' }
    if ($LASTEXITCODE -ne 0) { throw 'gcloud no confirmó la operación; los diagnósticos privados no se imprimen.' }
    try { return (($diagnostics -join "`n") | ConvertFrom-Json -AsHashtable) }
    catch { throw 'gcloud no devolvió una descripción JSON verificable.' }
}

function Get-PublicContract([hashtable]$Service) {
    $url = [string]$Service.status.url
    $uri = $null
    if (-not [Uri]::TryCreate($url, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne 'https' -or
            $uri.Host -notmatch '\.run\.app$' -or $uri.UserInfo -ne '' -or $uri.Query -ne '' -or $uri.Fragment -ne '') {
        throw 'La descripción no contiene la URL HTTPS canónica de Cloud Run.'
    }
    try { $contract = Invoke-RestMethod -Uri ($url.TrimEnd('/') + '/v3/api-docs') -Method Get -TimeoutSec 30 -ErrorAction Stop }
    catch { throw 'No se pudo verificar el contrato público; no se continúa ni se imprime el cuerpo remoto.' }
    return @{ Version = [string]$contract.info.version; PathCount = @($contract.paths.PSObject.Properties).Count }
}

if ($OutputPlanPath) {
    if (-not [IO.Path]::IsPathFullyQualified($OutputPlanPath) -or [IO.Path]::GetExtension($OutputPlanPath) -ne '.json') {
        throw 'OutputPlanPath requiere una ruta absoluta JSON fuera del repositorio.'
    }
    $OutputPlanPath = [IO.Path]::GetFullPath($OutputPlanPath)
    $repositoryPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\', '/')
    if ($OutputPlanPath.StartsWith($repositoryPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'El plan y las evidencias deben quedar fuera del repositorio.'
    }
    foreach ($path in @($OutputPlanPath, "$OutputPlanPath.before.json", "$OutputPlanPath.after.json", "$OutputPlanPath.result.json")) {
        if (Test-Path -LiteralPath $path) { throw 'Usa rutas de evidencia nuevas; no se sobrescribe un expediente anterior.' }
    }
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($OutputPlanPath))
}

if ($Execute -and $ValidateOnly) { throw 'Elige Execute o ValidateOnly.' }
Assert-Matches $ProjectId '^[a-z][a-z0-9-]{4,28}[a-z0-9]$' 'ProjectId'
Assert-Matches $Region '^[a-z]+-[a-z]+[0-9]+$' 'Region'
Assert-Matches $ServiceName '^[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$' 'ServiceName'
Assert-Matches $ImageUri '^[a-z0-9-]+-docker\.pkg\.dev/[a-z][a-z0-9-]{4,28}[a-z0-9]/[a-z0-9._/-]+(:[a-zA-Z0-9._-]+|@sha256:[a-f0-9]{64})$' 'ImageUri'
if ($DeploymentMode -eq 'ImageOnly') {
    $configurationParameters = @('DatabaseProvider', 'CloudSqlInstanceConnectionName', 'ExternalDatabaseHost', 'ExternalDatabasePort',
        'ServiceAccountEmail', 'DatabaseName', 'DatabaseUsername', 'MailProvider', 'MailFrom', 'ApprovedMonthlyBudgetUsd',
        'SecretBindings', 'FrontendBaseUrl', 'CorsAllowedOrigins', 'StaffActivationUrl', 'BackendBaseUrl', 'TrustedProxyPattern',
        'JwtKeyAlias', 'EnableInvitations', 'EnableBootstrap', 'DisablePasswordRecovery', 'EnableMonitoring', 'EnableLoginRateLimit')
    if (@($configurationParameters | Where-Object { $PSBoundParameters.ContainsKey($_) }).Count -gt 0) {
        throw 'ImageOnly no admite parámetros que cambien la configuración existente.'
    }
    if ($ExpectedContractVersion) { Assert-Matches $ExpectedContractVersion '^\d+\.\d+\.\d+$' 'ExpectedContractVersion' }
    $plan = [ordered]@{ Validation = 'OK'; Mode = 'ImageOnly'; ProjectId = $ProjectId; Region = $Region;
        ServiceName = $ServiceName; ImageUri = $ImageUri; ExpectedContractVersion = $ExpectedContractVersion;
        PreservedConfiguration = 'Environment, secret references, profiles, probes, CPU, resources, max=1, identity, JWT, TLS';
        CloudActionsExecuted = $false }
    $approvedService = $null
    if ($CurrentServiceConfigurationPath) {
        try { $approvedService = Get-Content -LiteralPath $CurrentServiceConfigurationPath -Raw | ConvertFrom-Json -AsHashtable }
        catch { throw 'No se pudo leer la descripción privada JSON del servicio.' }
        Assert-ExistingConfiguration $approvedService
        $plan.ApprovedConfigurationFingerprint = Get-ConfigurationFingerprint $approvedService
    }
    if (-not $Execute) {
        if ($OutputPlanPath) { Write-DeploymentEvidence $OutputPlanPath $plan }
        [pscustomobject]$plan
        return
    }
    if (-not $OutputPlanPath -or -not $ExpectedContractVersion -or $ImageUri -notmatch '@sha256:[a-f0-9]{64}$') {
        throw 'Execute exige OutputPlanPath, ExpectedContractVersion e imagen fijada por digest.'
    }
    $gcloudCommand = Get-Command gcloud -ErrorAction Stop
    $describeArguments = @('run', 'services', 'describe', $ServiceName, '--project', $ProjectId, '--region', $Region, '--format=json')
    $before = Invoke-GcloudJson $gcloudCommand $describeArguments
    Assert-ExistingConfiguration $before
    if ([string]$before.metadata.name -ne $ServiceName) { throw 'La descripción no corresponde al servicio seleccionado.' }
    $beforeFingerprint = Get-ConfigurationFingerprint $before
    if ($approvedService -and $beforeFingerprint -ne (Get-ConfigurationFingerprint $approvedService)) {
        throw 'La configuración remota cambió desde el expediente aprobado; vuelve a revisarla.'
    }
    $beforeContract = Get-PublicContract $before
    Write-DeploymentEvidence $OutputPlanPath $plan
    Write-DeploymentEvidence "$OutputPlanPath.before.json" @{ Service = Get-SanitizedServiceManifest $before; Contract = $beforeContract }
    # Only the image changes. No env/secrets/resource/IAM flags or historical template can reach this command.
    $null = Invoke-GcloudJson $gcloudCommand @('run', 'services', 'update', $ServiceName, '--project', $ProjectId,
        '--region', $Region, '--image', $ImageUri, '--quiet', '--format=json')
    $after = Invoke-GcloudJson $gcloudCommand $describeArguments
    Write-DeploymentEvidence "$OutputPlanPath.after.json" (Get-SanitizedServiceManifest $after)
    Assert-ExistingConfiguration $after
    $configurationPreserved = $beforeFingerprint -eq (Get-ConfigurationFingerprint $after)
    $imageVerified = [string]$after.spec.template.spec.containers[0].image -eq $ImageUri
    $revisionReady = [string]$after.status.latestReadyRevisionName -eq [string]$after.status.latestCreatedRevisionName
    $activeTraffic = @($after.status.traffic | Where-Object { $_.ContainsKey('percent') -and [int]$_.percent -gt 0 })
    $trafficVerified = $activeTraffic.Count -eq 1 -and [int]$activeTraffic[0].percent -eq 100 -and
        [string]$activeTraffic[0].revisionName -eq [string]$after.status.latestReadyRevisionName
    $afterContract = Get-PublicContract $after
    $verified = $configurationPreserved -and $imageVerified -and $revisionReady -and $trafficVerified -and $afterContract.Version -eq $ExpectedContractVersion
    Write-DeploymentEvidence "$OutputPlanPath.result.json" @{ CloudActionsExecuted = $true; Verified = $verified;
        ConfigurationPreserved = $configurationPreserved; ImageVerified = $imageVerified; RevisionReady = $revisionReady;
        TrafficVerified = $trafficVerified; Contract = $afterContract }
    if (-not $verified) { throw 'La revisión no superó la verificación; conserva el expediente y revisa sin aplicar un rollback histórico automático.' }
    [pscustomobject]@{ Validation = 'OK'; Mode = 'ImageOnly'; CloudActionsExecuted = $true; Verified = $true }
    return
}
if ($Execute) { throw 'ConfigurationTemplate solo prepara/valida. No puede reemplazar un servicio existente; usa ImageOnly.' }
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
if ($EnableMonitoring) { $requiredBindings += 'MONITORING_TOKEN_SHA256' }
if ($EnableLoginRateLimit) {
    $requiredBindings += 'LOGIN_RATE_LIMIT_HMAC_SECRET'
    if ($TrustedProxyPattern -ne '^$') { throw 'EnableLoginRateLimit requiere TRUSTED_PROXY_PATTERN=^$ para PEER.' }
}
foreach ($binding in $requiredBindings) {
    if (-not $SecretBindings.ContainsKey($binding)) { throw "Falta referencia Secret Manager para $binding." }
}
$allowedSecretBindings = @('DB_PASSWORD', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'JWT_KEYSTORE_PASSWORD', 'JWT_KEY_PASSWORD',
    'PASSWORD_RECOVERY_HMAC_SECRET', 'PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY', 'STAFF_INVITATION_ENCRYPTION_KEY',
    'SUPER_ADMIN_EMAIL', 'SUPER_ADMIN_PASSWORD', 'MONITORING_TOKEN_SHA256', 'LOGIN_RATE_LIMIT_HMAC_SECRET', $jwtMountPath)
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
    LOGIN_RATE_LIMIT_ENABLED = $EnableLoginRateLimit.IsPresent.ToString().ToLowerInvariant()
    LOGIN_IDENTIFIER_ORIGIN_MAXIMUM = '10'
    LOGIN_IDENTIFIER_ORIGIN_WINDOW = 'PT15M'
    LOGIN_ORIGIN_MAXIMUM = '60'
    LOGIN_ORIGIN_WINDOW = 'PT1M'
}
if ($DatabaseProvider -eq 'CloudSql') {
    $runtimeEnvironment.CLOUD_SQL_INSTANCE_CONNECTION_NAME = $CloudSqlInstanceConnectionName
    $runtimeEnvironment.CLOUD_SQL_IP_TYPES = 'PUBLIC'
} else {
    $runtimeEnvironment.SPRING_PROFILES_ACTIVE = 'prod,external-postgres'
    $runtimeEnvironment.SPRING_DATASOURCE_URL = "jdbc:postgresql://${ExternalDatabaseHost}:${ExternalDatabasePort}/${DatabaseName}"
}
if ($EnableMonitoring) { $runtimeEnvironment.SPRING_PROFILES_ACTIVE += ',monitoring' }

$templatePlan = [ordered]@{
        Validation = 'OK'
        Mode = 'ConfigurationTemplate'
        ProjectId = $ProjectId
        Region = $Region
        ServiceName = $ServiceName
        DatabaseProvider = $DatabaseProvider
        MailProvider = $MailProvider
        MailHost = $smtpEndpoint.Host
        MailPort = $smtpEndpoint.Port
        ApprovedMonthlyBudgetUsd = $ApprovedMonthlyBudgetUsd
        Profiles = $runtimeEnvironment.SPRING_PROFILES_ACTIVE
        EnvironmentNames = @($runtimeEnvironment.Keys)
        SecretReferences = $SecretBindings
        MonitoringEnabled = $EnableMonitoring.IsPresent
        LoginRateLimitEnabled = $EnableLoginRateLimit.IsPresent
        SecretBindingCount = $SecretBindings.Count
        CloudActionsExecuted = $false
}
if ($OutputPlanPath) { Write-DeploymentEvidence $OutputPlanPath $templatePlan }
[pscustomobject]$templatePlan
