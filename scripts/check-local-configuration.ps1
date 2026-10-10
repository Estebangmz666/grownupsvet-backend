[CmdletBinding()]
param(
    [ValidateRange(1, 65535)] [int]$Port = 8080,
    [string]$DatabaseHost,
    [ValidateRange(1, 65535)] [int]$DatabasePort = 5432,
    [ValidateRange(100, 10000)] [int]$ConnectionTimeoutMilliseconds = 1500,
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checks = [Collections.Generic.List[object]]::new()

function Add-Check([string]$Name, [string]$Status, [string]$Detail) {
    $checks.Add([pscustomobject]@{ Check = $Name; Status = $Status; Detail = $Detail })
}

function Get-ProcessSetting([string]$Name) {
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Test-SettingPresence([string]$Name, [bool]$Required) {
    if (-not [string]::IsNullOrWhiteSpace((Get-ProcessSetting $Name))) {
        Add-Check $Name 'OK' 'Presente en el proceso; valor oculto y contenido no validado.'
        return
    }
    $userSettingPresent = $false
    try { $userSettingPresent = -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name, 'User')) }
    catch { }
    $status = if ($Required) { 'ERROR' } else { 'WARNING' }
    $detail = if ($userSettingPresent) {
        'Ausente en este proceso, pero presente para el usuario; abre una terminal nueva antes de iniciar.'
    } elseif ($Required) {
        'Falta en este proceso para la preparación solicitada; consulta su necesidad de arranque o ensayo en la guía.'
    } else {
        'No configurada; la funcionalidad asociada permanece deshabilitada o usa su valor predeterminado.'
    }
    Add-Check $Name $status $detail
}

function Get-JavaMajorVersion([string]$Executable) {
    try {
        # Capture all output: JAVA_TOOL_OPTIONS can contain private properties and must never be echoed.
        $versionOutput = @(& $Executable -version 2>&1)
        if ($LASTEXITCODE -ne 0) { return $null }
        foreach ($line in $versionOutput) {
            if ($line.ToString() -match '^(?:openjdk|java) version "(?<major>[0-9]+)(?:\.|"|-)') {
                return [int]$Matches.major
            }
        }
    } catch { }
    return $null
}

function Test-JavaExecutable([string]$Name, [string]$Executable) {
    if ([string]::IsNullOrWhiteSpace($Executable) -or -not [IO.File]::Exists($Executable)) {
        Add-Check $Name 'ERROR' 'No se encontró un ejecutable Java accesible.'
        return
    }
    $major = Get-JavaMajorVersion $Executable
    if ($major -eq 25) { Add-Check $Name 'OK' 'Java 25 disponible; no se inició la aplicación.' }
    elseif ($null -eq $major) { Add-Check $Name 'ERROR' 'No se pudo comprobar la versión; se ocultó la salida del lanzador.' }
    else { Add-Check $Name 'ERROR' "El lanzador usa Java $major; el proyecto requiere Java 25." }
}

function Get-FeatureEnabled([string]$Name) {
    $value = Get-ProcessSetting $Name
    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    $enabled = $false
    if (-not [bool]::TryParse($value, [ref]$enabled)) {
        Add-Check $Name 'ERROR' 'El indicador debe ser true o false; no se mostró su valor.'
        return $false
    }
    return $enabled
}

$activeProfiles = Get-ProcessSetting 'SPRING_PROFILES_ACTIVE'
if ([string]::IsNullOrWhiteSpace($activeProfiles)) { Add-Check 'Perfil' 'OK' 'Sin override de perfiles; el backend usa dev por defecto.' }
elseif ($activeProfiles.Split(',').Trim() -contains 'dev' -and $activeProfiles.Split(',').Trim() -notcontains 'prod') {
    Add-Check 'Perfil' 'OK' 'dev seleccionado explícitamente.'
} else { Add-Check 'Perfil' 'ERROR' 'Este diagnóstico corresponde a dev; hay un override de perfiles diferente o productivo.' }

$javaCommand = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
Test-JavaExecutable 'Java del PATH' $(if ($null -ne $javaCommand) { $javaCommand.Source } else { '' })
$javaHome = Get-ProcessSetting 'JAVA_HOME'
$javaExecutableName = if ([IO.Path]::DirectorySeparatorChar -eq '\') { 'java.exe' } else { 'java' }
$javacExecutableName = if ([IO.Path]::DirectorySeparatorChar -eq '\') { 'javac.exe' } else { 'javac' }
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    Add-Check 'JAVA_HOME' 'WARNING' 'No está configurado en este proceso; Maven dependerá del Java del PATH.'
    $javacCommand = Get-Command javac -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $javacCommand) { Add-Check 'JDK' 'ERROR' 'No se encontró javac; el desarrollo requiere un JDK, no solo un JRE.' }
    else { Add-Check 'JDK' 'OK' 'javac disponible por PATH; Maven comprobará su compatibilidad al compilar.' }
} else {
    Test-JavaExecutable 'Java de JAVA_HOME' (Join-Path $javaHome "bin/$javaExecutableName")
    if ([IO.File]::Exists((Join-Path $javaHome "bin/$javacExecutableName"))) { Add-Check 'JDK' 'OK' 'JAVA_HOME contiene javac.' }
    else { Add-Check 'JDK' 'ERROR' 'JAVA_HOME no contiene javac; selecciona la instalación del JDK 25.' }
}

try {
    $listeners = @([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -eq $Port })
    if ($listeners.Count -eq 0) { Add-Check 'Puerto HTTP' 'OK' "El puerto $Port no tiene un listener TCP local en este instante." }
    else { Add-Check 'Puerto HTTP' 'ERROR' "El puerto $Port está ocupado; elige otro puerto o gestiona el proceso por tu cuenta. No se detuvo nada." }
} catch { Add-Check 'Puerto HTTP' 'WARNING' 'No se pudo consultar listeners; no se afirmó que el puerto esté libre.' }

$databaseEndpointSource = 'application-dev.yaml'
$databaseEndpointUsable = $true
$datasourceOverride = Get-ProcessSetting 'SPRING_DATASOURCE_URL'
if (-not $PSBoundParameters.ContainsKey('DatabaseHost')) {
    $DatabaseHost = 'localhost'
    if (-not [string]::IsNullOrWhiteSpace($datasourceOverride)) {
        $databaseEndpointSource = 'SPRING_DATASOURCE_URL'
        try {
            if (-not $datasourceOverride.StartsWith('jdbc:postgresql://', [StringComparison]::Ordinal)) { throw 'Unsupported URL' }
            $databaseUri = [Uri]::new($datasourceOverride.Substring('jdbc:'.Length), [UriKind]::Absolute)
            if ([string]::IsNullOrWhiteSpace($databaseUri.Host)) { throw 'No TCP host' }
            $DatabaseHost = $databaseUri.Host
            if (-not $PSBoundParameters.ContainsKey('DatabasePort') -and $databaseUri.Port -gt 0) { $DatabasePort = $databaseUri.Port }
        } catch {
            $databaseEndpointUsable = $false
            Add-Check 'Destino PostgreSQL' 'ERROR' 'El override no identifica un destino TCP PostgreSQL interpretable; no se mostró la URL.'
        }
    }
} else {
    $databaseEndpointSource = 'Parameters'
    if (-not [string]::IsNullOrWhiteSpace($datasourceOverride)) {
        Add-Check 'Destino PostgreSQL' 'WARNING' 'Hay un override de datasource y parámetros explícitos; verifica que ambos apunten al mismo destino.'
    }
}
if ([string]::IsNullOrWhiteSpace($DatabaseHost)) {
    $databaseEndpointUsable = $false
    Add-Check 'Destino PostgreSQL' 'ERROR' 'DatabaseHost no puede estar vacío.'
}
if ($databaseEndpointUsable) {
    $tcpClient = [Net.Sockets.TcpClient]::new()
    try {
        $connection = $tcpClient.ConnectAsync($DatabaseHost, $DatabasePort)
        if ($connection.Wait($ConnectionTimeoutMilliseconds) -and $tcpClient.Connected) {
            Add-Check 'TCP PostgreSQL' 'OK' 'El destino acepta TCP. No se verificaron protocolo, autenticación, base ni permisos.'
        } else { Add-Check 'TCP PostgreSQL' 'ERROR' 'El destino no respondió dentro del timeout; no se intentó autenticar.' }
    } catch { Add-Check 'TCP PostgreSQL' 'ERROR' 'No se pudo establecer TCP con el destino; no se intentó autenticar ni se imprimió el error interno.' }
    finally { $tcpClient.Dispose() }
}

foreach ($setting in @('DB_USERNAME', 'DB_PASSWORD', 'JWT_KEYSTORE_PATH', 'JWT_KEYSTORE_PASSWORD', 'APPOINTMENT_WORKDAY_START_TIME')) {
    Test-SettingPresence $setting $true
}
$keyStorePath = Get-ProcessSetting 'JWT_KEYSTORE_PATH'
if (-not [string]::IsNullOrWhiteSpace($keyStorePath)) {
    if ([IO.File]::Exists($keyStorePath)) { Add-Check 'Archivo PKCS12' 'OK' 'El archivo configurado existe; no se abrió ni se verificaron clave, contraseña o alias.' }
    else { Add-Check 'Archivo PKCS12' 'ERROR' 'El archivo configurado no existe o no es accesible; no se mostró su ruta.' }
}
Test-SettingPresence 'JWT_KEY_ALIAS' $false
Test-SettingPresence 'JWT_KEY_PASSWORD' $false

$recoveryEnabled = Get-FeatureEnabled 'PASSWORD_RECOVERY_ENABLED'
$invitationsEnabled = Get-FeatureEnabled 'STAFF_INVITATIONS_ENABLED'
Add-Check 'Recuperación' $(if ($recoveryEnabled) { 'OK' } else { 'WARNING' }) $(if ($recoveryEnabled) { 'Habilitada; se revisa presencia de su configuración.' } else { 'Deshabilitada; no acredita el recorrido completo P1.' })
Add-Check 'Invitaciones' $(if ($invitationsEnabled) { 'OK' } else { 'WARNING' }) $(if ($invitationsEnabled) { 'Habilitadas; se revisa presencia de su configuración.' } else { 'Deshabilitadas; no acreditan el recorrido completo de personal P1.' })
foreach ($setting in @('MAIL_HOST', 'MAIL_PORT')) { Test-SettingPresence $setting $false }
foreach ($setting in @('MAIL_USERNAME', 'MAIL_PASSWORD', 'MAIL_FROM')) { Test-SettingPresence $setting $true }
foreach ($setting in @('PASSWORD_RECOVERY_HMAC_SECRET', 'PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY')) { Test-SettingPresence $setting $recoveryEnabled }
foreach ($setting in @('STAFF_INVITATION_ENCRYPTION_KEY', 'STAFF_ACTIVATION_URL')) { Test-SettingPresence $setting $invitationsEnabled }

$report = [pscustomobject]@{
    Profile = 'dev'
    RequestedHttpPort = $Port
    DatabaseEndpointSource = $databaseEndpointSource
    PreliminaryConfigurationChecksPassed = @($checks | Where-Object { $_.Status -eq 'ERROR' }).Count -eq 0
    DatabaseAuthenticationVerified = $false
    SmtpDeliveryVerified = $false
    ApplicationStarted = $false
    ConfigurationModified = $false
    Checks = @($checks.ToArray())
}
if ($AsJson) { $report | ConvertTo-Json -Depth 4 }
else {
    $report.Checks | Format-Table Check, Status, Detail -Wrap
    if ($report.PreliminaryConfigurationChecksPassed) { Write-Output 'Comprobaciones preliminares de preparación satisfactorias. Todavía falta arrancar y verificar la aplicación, DB y Sandbox.' }
    else { Write-Output 'Hay faltantes o incompatibilidades para preparar la demostración local. Revisa los elementos ERROR; no se modificó configuración.' }
    Write-Output 'Diagnóstico de lectura: no inicia servicios, detiene procesos, crea claves, cambia variables ni prueba autenticación DB/SMTP.'
}
