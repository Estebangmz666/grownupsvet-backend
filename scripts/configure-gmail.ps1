# Store Gmail credentials with Windows DPAPI, outside the repository.
# Dot-source with -UseSavedCredential before starting the local backend.
[CmdletBinding()]
param(
    [switch]$ShowWindow,
    [switch]$UseSavedCredential
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Este configurador requiere Windows para proteger la credencial con DPAPI.' }
if ($ShowWindow -and $UseSavedCredential) { throw 'Elige ShowWindow o UseSavedCredential.' }

# A Windows PowerShell child can inherit a PowerShell 7 module search path.
# Import the security module bundled with this host before accepting credentials.
$gmailSecurityModulePath = [IO.Path]::Combine($PSHOME, 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1')
try {
    if (-not [IO.File]::Exists($gmailSecurityModulePath)) { throw 'Modulo de seguridad no disponible.' }
    Import-Module -Name $gmailSecurityModulePath -ErrorAction Stop
} catch {
    throw 'No se pudo inicializar el modulo de seguridad de esta instalacion de PowerShell.'
}

$gmailPrivateDirectory = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.grownupsvet\gmail'
$gmailCredentialPath = Join-Path $gmailPrivateDirectory 'smtp-credential.clixml'
$gmailSetupStatusPath = Join-Path $gmailPrivateDirectory 'setup-status.json'
$gmailCredential = $null

function ConvertTo-GmailCredential([string]$EmailAddress, [Security.SecureString]$ApplicationPassword) {
    $normalizedEmailAddress = $EmailAddress.Trim()
    if ($normalizedEmailAddress -notmatch '^[A-Za-z0-9._%+-]+@(gmail|googlemail)\.com$') {
        throw 'Escribe la direccion completa de tu cuenta Gmail.'
    }
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ApplicationPassword)
    try {
        $normalizedPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) -replace '\s', ''
        if ($normalizedPassword -notmatch '^[A-Za-z]{16}$') {
            throw 'La contrasena de aplicacion de Google debe contener 16 letras; puedes pegarla con espacios.'
        }
        $protectedPassword = ConvertTo-SecureString $normalizedPassword -AsPlainText -Force
        return [Management.Automation.PSCredential]::new($normalizedEmailAddress, $protectedPassword)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        $normalizedPassword = $null
    }
}

if ($UseSavedCredential) {
    if (-not (Test-Path -LiteralPath $gmailCredentialPath -PathType Leaf)) {
        throw 'Primero ejecuta scripts/configure-gmail.ps1 para guardar la cuenta y su contrasena de aplicacion.'
    }
    try { $gmailCredential = Import-Clixml -LiteralPath $gmailCredentialPath }
    catch { throw 'No se pudo abrir la credencial privada con este usuario de Windows.' }
    if ($gmailCredential -isnot [Management.Automation.PSCredential]) { throw 'La credencial privada no tiene el formato esperado.' }
} elseif ($ShowWindow) {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [Windows.Forms.Application]::EnableVisualStyles()
    $gmailDialog = [Windows.Forms.Form]::new()
    $gmailDialog.Text = 'GrownupsVet - configurar Gmail'
    $gmailDialog.ClientSize = [Drawing.Size]::new(520, 300)
    $gmailDialog.StartPosition = 'CenterScreen'
    $gmailDialog.FormBorderStyle = 'FixedDialog'
    $gmailDialog.MaximizeBox = $false
    $gmailDialog.MinimizeBox = $false
    $gmailDialog.TopMost = $true

    $gmailInstructions = [Windows.Forms.Label]::new()
    $gmailInstructions.Text = "Introduce la cuenta Gmail y su contrasena de aplicacion.`r`nLa contrasena se guardara cifrada, fuera del proyecto.`r`nEste formulario no envia correos ni cambia la version publicada."
    $gmailInstructions.SetBounds(20, 15, 480, 65)
    $gmailDialog.Controls.Add($gmailInstructions)
    $gmailEmailLabel = [Windows.Forms.Label]::new()
    $gmailEmailLabel.Text = 'Cuenta Gmail'
    $gmailEmailLabel.SetBounds(20, 88, 480, 22)
    $gmailDialog.Controls.Add($gmailEmailLabel)
    $gmailEmailInput = [Windows.Forms.TextBox]::new()
    $gmailEmailInput.SetBounds(20, 112, 480, 25)
    $gmailDialog.Controls.Add($gmailEmailInput)
    $gmailPasswordLabel = [Windows.Forms.Label]::new()
    $gmailPasswordLabel.Text = 'Contrasena de aplicacion (16 letras)'
    $gmailPasswordLabel.SetBounds(20, 150, 480, 22)
    $gmailDialog.Controls.Add($gmailPasswordLabel)
    $gmailPasswordInput = [Windows.Forms.TextBox]::new()
    $gmailPasswordInput.UseSystemPasswordChar = $true
    $gmailPasswordInput.SetBounds(20, 174, 480, 25)
    $gmailDialog.Controls.Add($gmailPasswordInput)
    $gmailSaveButton = [Windows.Forms.Button]::new()
    $gmailSaveButton.Text = 'Guardar credencial'
    $gmailSaveButton.SetBounds(300, 230, 200, 35)
    $gmailSaveButton.Add_Click({
        $enteredSecurePassword = $null
        try {
            if ([string]::IsNullOrWhiteSpace($gmailPasswordInput.Text)) {
                throw 'Introduce la contrasena de aplicacion de Google.'
            }
            $enteredSecurePassword = ConvertTo-SecureString $gmailPasswordInput.Text -AsPlainText -Force
            $gmailDialog.Tag = ConvertTo-GmailCredential $gmailEmailInput.Text $enteredSecurePassword
            $gmailPasswordInput.Clear()
            $gmailDialog.DialogResult = [Windows.Forms.DialogResult]::OK
        } catch {
            [void][Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Revisa los datos')
        } finally { if ($null -ne $enteredSecurePassword) { $enteredSecurePassword.Dispose() } }
    })
    $gmailDialog.Controls.Add($gmailSaveButton)
    $gmailDialog.AcceptButton = $gmailSaveButton
    try {
        if ($gmailDialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) { return }
        $gmailCredential = $gmailDialog.Tag
    } finally { $gmailDialog.Dispose() }
} else {
    $gmailEmailAddress = Read-Host 'Cuenta Gmail que enviara los mensajes'
    $gmailSecurePassword = Read-Host 'Contrasena de aplicacion de Google (entrada oculta)' -AsSecureString
    try { $gmailCredential = ConvertTo-GmailCredential $gmailEmailAddress $gmailSecurePassword }
    finally { $gmailSecurePassword.Dispose() }
}

if (-not $UseSavedCredential) {
    [void][IO.Directory]::CreateDirectory($gmailPrivateDirectory)
    $gmailTemporaryPath = Join-Path $gmailPrivateDirectory ('credential-' + [Guid]::NewGuid().ToString('N') + '.clixml')
    try {
        $gmailCredential | Export-Clixml -LiteralPath $gmailTemporaryPath
        Move-Item -LiteralPath $gmailTemporaryPath -Destination $gmailCredentialPath -Force
        @{ CredentialSaved = $true; SavedAtUtc = [DateTime]::UtcNow.ToString('o'); Host = 'smtp.gmail.com'; Port = 587 } |
            ConvertTo-Json | Set-Content -LiteralPath $gmailSetupStatusPath -Encoding UTF8
    } finally {
        if (Test-Path -LiteralPath $gmailTemporaryPath) { Remove-Item -LiteralPath $gmailTemporaryPath -Force }
    }
}

# Process scope avoids storing the application password in the Windows environment registry.
$env:MAIL_HOST = 'smtp.gmail.com'
$env:MAIL_PORT = '587'
$env:MAIL_USERNAME = $gmailCredential.UserName
$env:MAIL_FROM = $gmailCredential.UserName
$env:MAIL_PASSWORD = $gmailCredential.GetNetworkCredential().Password
$gmailCredential = $null
Write-Host 'Gmail preparado en esta terminal. No se ha enviado ningun correo.'
Write-Host 'En una terminal nueva: . .\scripts\configure-gmail.ps1 -UseSavedCredential'
