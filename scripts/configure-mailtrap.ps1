# Run locally after creating an Email Sandbox. Secrets are never printed or saved in Git.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Read-SecretText([string] $Prompt) {
    $secureValue = Read-Host $Prompt -AsSecureString
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
        $secureValue.Dispose()
    }
}

$mailtrapUsername = Read-Host 'Usuario SMTP de Integration en tu Sandbox de Mailtrap'
$mailtrapPassword = Read-SecretText 'Contraseña SMTP del Sandbox (entrada oculta)'
$mailSenderAddress = Read-Host 'Correo remitente de desarrollo, por ejemplo no-reply@grownupsvet.example'
if ([string]::IsNullOrWhiteSpace($mailtrapUsername) -or [string]::IsNullOrWhiteSpace($mailtrapPassword)) {
    throw 'Se necesitan el usuario y la contraseña SMTP del Sandbox.'
}
try {
    $parsedSenderAddress = [System.Net.Mail.MailAddress]::new($mailSenderAddress)
    if ($parsedSenderAddress.Address -ne $mailSenderAddress -or $mailSenderAddress -match '[\r\n]') {
        throw 'Invalid sender'
    }
} catch {
    throw 'Introduce únicamente una dirección de correo válida para el remitente.'
}

$recoveryHmacSecret = [Environment]::GetEnvironmentVariable('PASSWORD_RECOVERY_HMAC_SECRET', 'User')
if ([string]::IsNullOrWhiteSpace($recoveryHmacSecret)) {
    $recoverySecretBytes = [byte[]]::new(32)
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($recoverySecretBytes)
        $recoveryHmacSecret = [Convert]::ToBase64String($recoverySecretBytes)
    } finally {
        $randomGenerator.Dispose()
        [Array]::Clear($recoverySecretBytes, 0, $recoverySecretBytes.Length)
    }
}

$mailtrapSettings = @{
    MAIL_HOST = 'sandbox.smtp.mailtrap.io'
    MAIL_PORT = '2525'
    MAIL_USERNAME = $mailtrapUsername.Trim()
    MAIL_PASSWORD = $mailtrapPassword
    MAIL_FROM = $mailSenderAddress
    PASSWORD_RECOVERY_HMAC_SECRET = $recoveryHmacSecret
    PASSWORD_RECOVERY_ENABLED = 'true'
}
foreach ($settingName in $mailtrapSettings.Keys) {
    [Environment]::SetEnvironmentVariable($settingName, $mailtrapSettings[$settingName], 'User')
    [Environment]::SetEnvironmentVariable($settingName, $mailtrapSettings[$settingName], 'Process')
}
$mailtrapSettings.Clear()
Remove-Variable mailtrapPassword, recoveryHmacSecret
Write-Host 'Configuración guardada en las variables del usuario de Windows y de esta terminal.'
Write-Host 'Reinicia IntelliJ o abre una terminal nueva antes de iniciar el backend desde allí.'
Write-Host 'Este script no envía correos. Comprueba el flujo de recuperación y abre el mensaje en Mailtrap.'
