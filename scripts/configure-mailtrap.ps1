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

function New-RandomRecoveryKey {
    $secretBytes = [byte[]]::new(32)
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($secretBytes)
        return [Convert]::ToBase64String($secretBytes)
    } finally {
        $randomGenerator.Dispose()
        [Array]::Clear($secretBytes, 0, $secretBytes.Length)
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
    $recoveryHmacSecret = New-RandomRecoveryKey
}
$recoveryEmailEncryptionKey = [Environment]::GetEnvironmentVariable('PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY', 'User')
if ([string]::IsNullOrWhiteSpace($recoveryEmailEncryptionKey)) {
    $recoveryEmailEncryptionKey = New-RandomRecoveryKey
}
try {
    $emailKeyBytes = [Convert]::FromBase64String($recoveryEmailEncryptionKey)
    if ($emailKeyBytes.Length -ne 32) { throw 'Invalid key length' }
    $recoveryEmailEncryptionKey = [Convert]::ToBase64String($emailKeyBytes)
} catch {
    throw 'La clave existente de correo de recuperación debe ser Base64 de 32 bytes; no se reemplazó.'
} finally {
    if ($null -ne $emailKeyBytes) { [Array]::Clear($emailKeyBytes, 0, $emailKeyBytes.Length) }
}
if ($recoveryEmailEncryptionKey -eq $recoveryHmacSecret) {
    throw 'La clave de cifrado del correo debe ser distinta del secreto HMAC; no se reemplazó ninguna clave existente.'
}

$mailtrapSettings = @{
    MAIL_HOST = 'sandbox.smtp.mailtrap.io'
    MAIL_PORT = '2525'
    MAIL_USERNAME = $mailtrapUsername.Trim()
    MAIL_PASSWORD = $mailtrapPassword
    MAIL_FROM = $mailSenderAddress
    PASSWORD_RECOVERY_HMAC_SECRET = $recoveryHmacSecret
    PASSWORD_RECOVERY_EMAIL_ENCRYPTION_KEY = $recoveryEmailEncryptionKey
    PASSWORD_RECOVERY_ENABLED = 'true'
}
foreach ($settingName in $mailtrapSettings.Keys) {
    [Environment]::SetEnvironmentVariable($settingName, $mailtrapSettings[$settingName], 'User')
    [Environment]::SetEnvironmentVariable($settingName, $mailtrapSettings[$settingName], 'Process')
}
$mailtrapSettings.Clear()
Remove-Variable mailtrapPassword, recoveryHmacSecret, recoveryEmailEncryptionKey
Write-Host 'Configuración guardada en las variables del usuario de Windows y de esta terminal.'
Write-Host 'Reinicia IntelliJ o abre una terminal nueva antes de iniciar el backend desde allí.'
Write-Host 'Este script no envía correos. Comprueba el flujo de recuperación y abre el mensaje en Mailtrap.'
