[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$deploymentScript = Join-Path $PSScriptRoot '../deploy-google-cloud.ps1'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('grownupsvet-deployment-tests-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($testRoot)
$global:DeploymentFixture = @{}

function Assert-DeploymentTest([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function New-ServiceFixture {
    return @{
        metadata = @{ name = 'fixture-api'; annotations = @{ 'run.googleapis.com/maxScale' = '1' } }
        spec = @{ template = @{
            metadata = @{ annotations = @{ 'run.googleapis.com/cpu-throttling' = 'false'; 'autoscaling.knative.dev/maxScale' = '20' } }
            spec = @{ serviceAccountName = 'runtime@audit-fixture-123.iam.gserviceaccount.com'; containerConcurrency = 8;
                timeoutSeconds = 300; containers = @(@{
                    image = 'us-central1-docker.pkg.dev/audit-fixture-123/images/api@sha256:' + ('a' * 64)
                    resources = @{ limits = @{ cpu = '1'; memory = '2Gi' } }
                    ports = @(@{ containerPort = 8080 })
                    startupProbe = @{ httpGet = @{ path = '/actuator/health/readiness'; port = 8080 }; periodSeconds = 10 }
                    livenessProbe = @{ httpGet = @{ path = '/actuator/health/liveness'; port = 8080 }; periodSeconds = 30 }
                    env = @(
                        @{ name = 'SPRING_PROFILES_ACTIVE'; value = 'prod,external-postgres,monitoring' },
                        @{ name = 'DB_PASSWORD'; valueFrom = @{ secretKeyRef = @{ name = 'database'; key = '1' } } },
                        @{ name = 'JWT_KEYSTORE_PASSWORD'; valueFrom = @{ secretKeyRef = @{ name = 'jwt-password'; key = '1' } } },
                        @{ name = 'JWT_KEYSTORE_PATH'; value = '/var/secrets/jwt/jwt-signing.p12' },
                        @{ name = 'MONITORING_TOKEN_SHA256'; value = ('c' * 64) },
                        @{ name = 'LOGIN_RATE_LIMIT_ENABLED'; value = 'true' },
                        @{ name = 'LOGIN_RATE_LIMIT_HMAC_SECRET'; valueFrom = @{ secretKeyRef = @{ name = 'login-hmac'; key = '1' } } },
                        @{ name = 'TRUSTED_PROXY_PATTERN'; value = '^$' },
                        @{ name = 'PRIVATE_FIXTURE_MARKER'; value = 'value-that-must-never-appear-in-evidence' }
                    )
                    volumeMounts = @(@{ name = 'jwt'; mountPath = '/var/secrets/jwt' })
                })
                volumes = @(@{ name = 'jwt'; secret = @{ secretName = 'jwt-store'; items = @(@{ key = '1'; path = 'jwt-signing.p12' }) } })
            }
        } }
        status = @{ url = 'https://fixture-api-abc-uc.a.run.app'; latestReadyRevisionName = 'fixture-api-00001';
            latestCreatedRevisionName = 'fixture-api-00001'; traffic = @(@{ revisionName = 'fixture-api-00001'; percent = 100 }) }
    }
}

# Functional boundary doubles: all command/HTTP operations remain in this process, without cloud or credentials.
function gcloud {
    $global:DeploymentFixture.Calls.Add(@($args))
    if ($args[2] -eq 'update') {
        $global:DeploymentFixture.Service.spec.template.spec.containers[0].image = $args[([Array]::IndexOf($args, '--image') + 1)]
        $global:DeploymentFixture.Service.status.latestCreatedRevisionName = 'fixture-api-00002'
        $global:DeploymentFixture.Service.status.latestReadyRevisionName = 'fixture-api-00002'
        $global:DeploymentFixture.Service.status.traffic = @(@{ revisionName = 'fixture-api-00002'; percent = 100 })
        $global:DeploymentFixture.Version = '0.11.0'
        if ($global:DeploymentFixture.Drift) {
            $global:DeploymentFixture.Service.spec.template.spec.containers[0].resources.limits.memory = '4Gi'
        }
    }
    $global:LASTEXITCODE = 0
    $global:DeploymentFixture.Service | ConvertTo-Json -Depth 100
}
function Invoke-RestMethod {
    param([string]$Uri, [string]$Method, [int]$TimeoutSec, [object]$ErrorAction)
    $global:DeploymentFixture.HttpCalls++
    if ($global:DeploymentFixture.ContractFailure) { throw 'Controlled unavailable contract.' }
    return [pscustomobject]@{ info = [pscustomobject]@{ version = $global:DeploymentFixture.Version };
        paths = [pscustomobject]@{ '/api/v1/auth/sessions' = @{} } }
}
function Reset-DeploymentFixture {
    $global:DeploymentFixture = @{ Service = New-ServiceFixture; Calls = [Collections.Generic.List[object]]::new();
        HttpCalls = 0; Version = '0.10.0'; Drift = $false; ContractFailure = $false }
}
function Assert-RejectedBeforeUpdate([hashtable]$Parameters) {
    $failed = $false
    try { $null = & $deploymentScript @Parameters }
    catch { $failed = $true }
    Assert-DeploymentTest $failed 'Se esperaba rechazo de la operación.'
    Assert-DeploymentTest (@($global:DeploymentFixture.Calls | Where-Object { $_[2] -eq 'update' }).Count -eq 0) 'Un rechazo de preparación no debe actualizar el servicio.'
}

try {
    $parameters = @{ ProjectId = 'audit-fixture-123'; Region = 'us-central1'; ServiceName = 'fixture-api';
        ImageUri = ('us-central1-docker.pkg.dev/audit-fixture-123/images/api@sha256:' + ('b' * 64)) }
    Reset-DeploymentFixture
    $result = & $deploymentScript @parameters -ValidateOnly
    Assert-DeploymentTest (-not $result.CloudActionsExecuted -and $global:DeploymentFixture.Calls.Count -eq 0 -and $global:DeploymentFixture.HttpCalls -eq 0) 'ValidateOnly debe permanecer offline.'

    Reset-DeploymentFixture
    Assert-RejectedBeforeUpdate ($parameters + @{ SecretBindings = @{ DB_PASSWORD = 'fixture:1' } })
    Assert-RejectedBeforeUpdate ($parameters + @{ Execute = $true; ExpectedContractVersion = '0.11.0' })
    Assert-RejectedBeforeUpdate ($parameters + @{ DeploymentMode = 'ConfigurationTemplate'; Execute = $true })

    Reset-DeploymentFixture
    $execution = $parameters + @{ Execute = $true; ExpectedContractVersion = '0.11.0'; OutputPlanPath = (Join-Path $testRoot 'success.json') }
    $result = & $deploymentScript @execution
    Assert-DeploymentTest $result.Verified 'La actualización de imagen debe superar las comprobaciones.'
    Assert-DeploymentTest ($global:DeploymentFixture.Calls.Count -eq 3 -and $global:DeploymentFixture.HttpCalls -eq 2) 'Se requieren descripción antes/después, actualización y dos contratos.'
    $before = Get-Content -LiteralPath ($execution.OutputPlanPath + '.before.json') -Raw | ConvertFrom-Json -AsHashtable
    $after = Get-Content -LiteralPath ($execution.OutputPlanPath + '.after.json') -Raw | ConvertFrom-Json -AsHashtable
    Assert-DeploymentTest ($before.Service.ConfigurationFingerprint -eq $after.ConfigurationFingerprint) 'Deben conservarse perfiles, secretos, probes y recursos.'
    Assert-DeploymentTest ($before.Service.ServiceMaximumInstances -eq '1' -and $after.RevisionMaximumInstances -eq '20') 'Cap efectivo 1 debe aceptar y preservar la revisión histórica con máximo 20.'
    $evidence = (Get-ChildItem -LiteralPath $testRoot -File | Get-Content -Raw) -join ''
    Assert-DeploymentTest (-not $evidence.Contains('value-that-must-never-appear-in-evidence') -and -not $evidence.Contains(('c' * 64))) 'La evidencia no debe revelar valores de entorno ni el hash de monitoring.'

    Reset-DeploymentFixture
    $global:DeploymentFixture.ContractFailure = $true
    Assert-RejectedBeforeUpdate ($parameters + @{ Execute = $true; ExpectedContractVersion = '0.11.0'; OutputPlanPath = (Join-Path $testRoot 'offline-contract.json') })

    foreach ($variant in @('unbounded', 'missing-service-cap', 'raw-secret', 'missing-monitoring')) {
        Reset-DeploymentFixture
        if ($variant -eq 'unbounded') {
            $global:DeploymentFixture.Service.metadata.annotations['run.googleapis.com/maxScale'] = '2'
            $global:DeploymentFixture.Service.spec.template.metadata.annotations['autoscaling.knative.dev/maxScale'] = '1'
        }
        if ($variant -eq 'missing-service-cap') { $global:DeploymentFixture.Service.metadata.annotations.Remove('run.googleapis.com/maxScale') }
        if ($variant -eq 'raw-secret') {
            $entry = $global:DeploymentFixture.Service.spec.template.spec.containers[0].env | Where-Object { $_.name -eq 'LOGIN_RATE_LIMIT_HMAC_SECRET' }
            $entry.Remove('valueFrom'); $entry.value = 'unacceptable-literal'
        }
        if ($variant -eq 'missing-monitoring') {
            $global:DeploymentFixture.Service.spec.template.spec.containers[0].env = @($global:DeploymentFixture.Service.spec.template.spec.containers[0].env | Where-Object { $_.name -ne 'MONITORING_TOKEN_SHA256' })
        }
        Assert-RejectedBeforeUpdate ($parameters + @{ Execute = $true; ExpectedContractVersion = '0.11.0'; OutputPlanPath = (Join-Path $testRoot ($variant + '.json')) })
    }

    Reset-DeploymentFixture
    $global:DeploymentFixture.Drift = $true
    $failed = $false
    $driftPath = Join-Path $testRoot 'drift.json'
    try { $null = & $deploymentScript @parameters -Execute -ExpectedContractVersion '0.11.0' -OutputPlanPath $driftPath }
    catch { $failed = $true }
    $drift = Get-Content -LiteralPath ($driftPath + '.result.json') -Raw | ConvertFrom-Json
    Assert-DeploymentTest ($failed -and -not $drift.Verified -and -not $drift.ConfigurationPreserved) 'Un cambio de memoria no puede acreditarse como despliegue verificado.'

    Reset-DeploymentFixture
    $failed = $false
    $contractPath = Join-Path $testRoot 'contract-mismatch.json'
    try { $null = & $deploymentScript @parameters -Execute -ExpectedContractVersion '9.9.9' -OutputPlanPath $contractPath }
    catch { $failed = $true }
    $mismatch = Get-Content -LiteralPath ($contractPath + '.result.json') -Raw | ConvertFrom-Json
    Assert-DeploymentTest ($failed -and -not $mismatch.Verified -and $mismatch.ConfigurationPreserved) 'Un contrato distinto del esperado debe fallar aunque la imagen y configuración coincidan.'

    Reset-DeploymentFixture
    $bindings = @{ DB_PASSWORD = 'database:1'; MAIL_USERNAME = 'mail-user:1'; MAIL_PASSWORD = 'mail-password:1';
        JWT_KEYSTORE_PASSWORD = 'jwt-password:1'; '/var/secrets/jwt/jwt-signing.p12' = 'jwt-store:1';
        MONITORING_TOKEN_SHA256 = 'monitoring-hash:1'; LOGIN_RATE_LIMIT_HMAC_SECRET = 'login-hmac:1' }
    $template = & $deploymentScript @parameters -DeploymentMode ConfigurationTemplate -ValidateOnly -DatabaseProvider ExternalPostgres `
        -ExternalDatabaseHost 'database.render.com' -ServiceAccountEmail 'runtime-fixture@audit-fixture-123.iam.gserviceaccount.com' `
        -DatabaseName grownupsvet -DatabaseUsername fixture -MailFrom 'fixture@gmail.com' -MailProvider Gmail `
        -ApprovedMonthlyBudgetUsd 1 -DisablePasswordRecovery -EnableMonitoring -EnableLoginRateLimit -SecretBindings $bindings
    Assert-DeploymentTest ($template.Profiles -eq 'prod,external-postgres,monitoring' -and $template.LoginRateLimitEnabled -and $global:DeploymentFixture.Calls.Count -eq 0) 'La plantilla offline debe representar monitoring/admisión con referencias y sin nube.'
    [pscustomobject]@{ Status = 'PASS'; FunctionalScenarios = 13; CloudActionsExecuted = $false }
} finally {
    Remove-Item Function:\gcloud -ErrorAction SilentlyContinue
    Remove-Item Function:\Invoke-RestMethod -ErrorAction SilentlyContinue
    Remove-Variable DeploymentFixture -Scope Global -ErrorAction SilentlyContinue
    $absoluteTestRoot = [IO.Path]::GetFullPath($testRoot)
    $absoluteTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if ($absoluteTestRoot.StartsWith($absoluteTempRoot, [StringComparison]::OrdinalIgnoreCase) -and
            [IO.Path]::GetFileName($absoluteTestRoot).StartsWith('grownupsvet-deployment-tests-')) {
        Remove-Item -LiteralPath $absoluteTestRoot -Recurse -Force
    }
}
