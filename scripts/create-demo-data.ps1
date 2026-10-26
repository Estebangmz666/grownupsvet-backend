#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $ApiBaseUrl,
    [Parameter(Mandatory = $true)] [Security.SecureString] $AdministratorAccessToken,
    [Parameter(Mandatory = $true)] [Guid] $VeterinarianId,
    [Parameter(Mandatory = $true)] [DateTimeOffset] $FirstSlotStartsAt,
    [Parameter(Mandatory = $true)] [Security.SecureString] $OwnerPassword
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$runId = [Guid]::NewGuid().ToString('N')
$owners = [Collections.Generic.List[object]]::new()
$slots = [Collections.Generic.List[object]]::new()
$appointments = [Collections.Generic.List[object]]::new()
$checks = [Collections.Generic.List[object]]::new()
$reportDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\target\demo-data'))
$reportPath = Join-Path $reportDirectory ('demo-' + $runId + '.json')
$script:currentStage = 'ValidateParameters'
$script:failureCode = 'VALIDATION_FAILED'
$script:apiOrigin = $null
$script:httpClient = $null
$script:success = $false
$script:reportError = $null
$startedAt = [DateTimeOffset]::UtcNow.ToString('o')
$administratorTokenText = $null
$ownerPasswordText = $null
$ownerAToken = $null
$ownerBToken = $null

function Save-DemoReport {
    $report = [pscustomobject][ordered]@{
        RunId = $runId
        ApiBaseUrl = $script:apiOrigin
        StartedAt = $startedAt
        CompletedAt = if ($script:success -or $null -ne $script:reportError) { [DateTimeOffset]::UtcNow.ToString('o') } else { $null }
        Success = $script:success
        Owners = $owners.ToArray()
        Slots = $slots.ToArray()
        Appointments = $appointments.ToArray()
        Checks = $checks.ToArray()
        Error = $script:reportError
        MailtrapCaptureVerified = $false
        ReportPath = $reportPath
    }
    $json = $report | ConvertTo-Json -Depth 10
    if (-not [IO.Directory]::Exists($reportDirectory)) { [void][IO.Directory]::CreateDirectory($reportDirectory) }
    [IO.File]::WriteAllText($reportPath, $json, [Text.UTF8Encoding]::new($false))
    return $json
}

function Convert-SecureValueToText([Security.SecureString] $SecureValue) {
    if ($null -eq $SecureValue -or $SecureValue.Length -eq 0) { throw 'Se requiere un valor seguro no vacio.' }
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Add-DemoAssertion([string] $Name, [bool] $Condition) {
    $checks.Add([pscustomobject][ordered]@{
        name = $Name; expectedStatus = $null; actualStatus = $null; passed = $Condition
        errorCode = if ($Condition) { $null } else { 'RESPONSE_INVARIANT_FAILED' }
    })
    if (-not $Condition) {
        $script:failureCode = 'RESPONSE_INVARIANT_FAILED'
        throw 'La respuesta no cumple la comprobacion del recorrido.'
    }
}

function Get-PropertyValue($ResponseBody, [string] $Name) {
    if ($null -eq $ResponseBody -or $null -eq $ResponseBody.PSObject.Properties[$Name]) {
        $script:failureCode = 'INVALID_RESPONSE_CONTRACT'
        throw 'La respuesta no tiene la estructura requerida.'
    }
    return $ResponseBody.PSObject.Properties[$Name].Value
}

function Get-ResponseGuid($ResponseBody, [string] $Name) {
    [Guid] $value = [Guid]::Empty
    if (-not [Guid]::TryParse([string](Get-PropertyValue $ResponseBody $Name), [ref]$value) -or $value -eq [Guid]::Empty) {
        $script:failureCode = 'INVALID_RESPONSE_CONTRACT'
        throw 'La respuesta no contiene un identificador valido.'
    }
    return $value.ToString()
}

function Get-ResponseVersion($ResponseBody) {
    $value = Get-PropertyValue $ResponseBody 'version'
    if ($null -eq $value -or $value -is [string] -or $value -is [bool]) {
        $script:failureCode = 'INVALID_RESPONSE_CONTRACT'
        throw 'La respuesta no contiene una version numerica.'
    }
    $version = [Convert]::ToInt64($value, [Globalization.CultureInfo]::InvariantCulture)
    if ($version -lt 0 -or [decimal]$value -ne [decimal]$version) { throw 'La version de respuesta no es un entero valido.' }
    return $version
}

function Get-ResponseInstant($ResponseBody, [string] $Name) {
    $value = Get-PropertyValue $ResponseBody $Name
    if ($value -is [DateTime]) { return ([DateTimeOffset]$value).ToUniversalTime().ToString('o') }
    return [DateTimeOffset]::Parse([string]$value, [Globalization.CultureInfo]::InvariantCulture).ToUniversalTime().ToString('o')
}

function Get-SafeApiErrorCode($ResponseBody) {
    $known = @('VALIDATION_FAILED', 'MALFORMED_JSON', 'TYPE_MISMATCH', 'UNKNOWN_PROPERTY',
        'EMAIL_ALREADY_REGISTERED', 'AUTHENTICATION_REQUIRED', 'ACCESS_DENIED', 'PET_NOT_FOUND',
        'APPOINTMENT_NOT_FOUND', 'CONCURRENT_UPDATE', 'AVAILABILITY_SLOT_CONFLICT', 'VETERINARIAN_NOT_ACTIVE',
        'APPOINTMENT_SLOT_OCCUPIED', 'APPOINTMENT_PET_ALREADY_SCHEDULED', 'APPOINTMENT_OUTSIDE_REQUEST_WINDOW',
        'APPOINTMENT_CONFIRMATION_CUTOFF_PASSED', 'APPOINTMENT_TRANSITION_NOT_ALLOWED',
        'APPOINTMENT_ALREADY_STARTED', 'APPOINTMENT_DESTINATION_NOT_AVAILABLE', 'APPOINTMENT_PARTICIPANT_INACTIVE',
        'APPOINTMENT_IDEMPOTENCY_CONFLICT', 'INVALID_APPOINTMENT_REQUEST', 'INTERNAL_ERROR', 'INVALID_CREDENTIALS')
    if ($null -ne $ResponseBody -and $null -ne $ResponseBody.PSObject.Properties['errorCode']) {
        $code = [string]$ResponseBody.PSObject.Properties['errorCode'].Value
        if ($known -contains $code) { return $code }
    }
    return 'UNRECOGNIZED_API_ERROR'
}

function Invoke-DemoApi([string] $Name, [string] $Method, [string] $Path, [int] $ExpectedStatus,
                        [string] $AccessToken = '', $Body = $null) {
    $script:currentStage = $Name
    $script:failureCode = 'HTTP_REQUEST_FAILED'
    $request = $null
    $response = $null
    $actualStatus = $null
    $responseBody = $null
    $safeCode = $null
    try {
        $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), [Uri]($script:apiOrigin + $Path))
        if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
            $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $AccessToken)
        }
        if ($null -ne $Body) {
            $request.Content = [Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 8 -Compress),
                [Text.Encoding]::UTF8, 'application/json')
        }
        $response = $script:httpClient.SendAsync($request).GetAwaiter().GetResult()
        $actualStatus = [int]$response.StatusCode
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            try { $responseBody = ConvertFrom-Json -InputObject $content }
            catch { $script:failureCode = 'INVALID_RESPONSE_CONTRACT'; throw 'Respuesta JSON invalida.' }
        }
        if ($actualStatus -ge 400) { $safeCode = Get-SafeApiErrorCode $responseBody }
        if ($actualStatus -ne $ExpectedStatus) {
            $script:failureCode = if ($actualStatus -ge 300 -and $actualStatus -lt 400) { 'REDIRECT_REFUSED' } else { 'UNEXPECTED_HTTP_STATUS' }
            throw 'El estado HTTP no coincide con el esperado.'
        }
        $checks.Add([pscustomobject][ordered]@{ name=$Name; expectedStatus=$ExpectedStatus; actualStatus=$actualStatus; passed=$true; errorCode=$safeCode })
        return [pscustomobject]@{ Body=$responseBody; StatusCode=$actualStatus }
    } catch {
        $checks.Add([pscustomobject][ordered]@{ name=$Name; expectedStatus=$ExpectedStatus; actualStatus=$actualStatus; passed=$false; errorCode=$safeCode })
        throw 'La llamada API fallo; se conserva solo diagnostico saneado en el reporte.'
    } finally {
        if ($null -ne $request) { $request.Headers.Clear(); $request.Dispose() }
        if ($null -ne $response) { $response.Dispose() }
    }
}

function Update-DemoAppointment($Record, $ResponseBody, [string] $ExpectedState) {
    Add-DemoAssertion ($Record.label + 'AppointmentIdentity') ((Get-ResponseGuid $ResponseBody 'id') -eq $Record.id -and
        (Get-ResponseGuid $ResponseBody 'ownerId') -eq $Record.ownerId)
    $state = [string](Get-PropertyValue $ResponseBody 'status')
    Add-DemoAssertion ($Record.label + 'AppointmentState' + $ExpectedState) ($state -eq $ExpectedState)
    $Record.status = $state
    $Record.version = Get-ResponseVersion $ResponseBody
    [void](Save-DemoReport)
}

try {
    [Uri] $apiUri = $null
    if (-not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$apiUri) -or
        $apiUri.Scheme -notin @('http', 'https') -or -not [string]::IsNullOrEmpty($apiUri.UserInfo) -or
        -not [string]::IsNullOrEmpty($apiUri.Query) -or -not [string]::IsNullOrEmpty($apiUri.Fragment) -or
        $apiUri.AbsolutePath -ne '/' -or $apiUri.Port -lt 1) { throw 'ApiBaseUrl requiere un origen HTTP/HTTPS local sin datos adicionales.' }
    $apiHostName = $apiUri.DnsSafeHost.Trim('[', ']')
    [Net.IPAddress] $parsedAddress = $null
    $loopback = $apiHostName.Equals('localhost', [StringComparison]::OrdinalIgnoreCase) -or
        ([Net.IPAddress]::TryParse($apiHostName, [ref]$parsedAddress) -and [Net.IPAddress]::IsLoopback($parsedAddress))
    if (-not $loopback -or $VeterinarianId -eq [Guid]::Empty) { throw 'Solo se permite loopback y un veterinario existente valido.' }
    $script:apiOrigin = $apiUri.GetLeftPart([UriPartial]::Authority).TrimEnd('/')
    try { $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('SA Pacific Standard Time') }
    catch { $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('America/Bogota') }
    $now = [DateTimeOffset]::UtcNow
    $today = [TimeZoneInfo]::ConvertTime($now, $businessZone).Date
    $secondSlotStartsAt = $FirstSlotStartsAt.AddMinutes(30)
    foreach ($slotStart in @($FirstSlotStartsAt, $secondSlotStartsAt)) {
        $local = [TimeZoneInfo]::ConvertTime($slotStart, $businessZone)
        if ($local.Date -lt $today.AddDays(1) -or $slotStart -gt $now.AddDays(60) -or
            $local.Minute -notin @(0, 30) -or ($local.Ticks % [TimeSpan]::TicksPerMinute) -ne 0) {
            throw 'Los dos turnos deben ser solicitables desde manana hasta 60 dias y alineados a treinta minutos en Colombia.'
        }
    }
    $administratorTokenText = Convert-SecureValueToText $AdministratorAccessToken
    $ownerPasswordText = Convert-SecureValueToText $OwnerPassword
    if ([string]::IsNullOrWhiteSpace($ownerPasswordText) -or $administratorTokenText -match '[\r\n\s]') {
        throw 'Se requieren contrasena no vacia y solamente el valor del token administrador.'
    }
    Add-Type -AssemblyName System.Net.Http
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseProxy = $false
    $script:httpClient = [Net.Http.HttpClient]::new($handler)
    $script:httpClient.Timeout = [TimeSpan]::FromSeconds(20)
    $script:httpClient.MaxResponseContentBufferSize = 2 * 1024 * 1024
    [void](Save-DemoReport)

    $contract = (Invoke-DemoApi 'BackendContract' 'GET' '/v3/api-docs' 200).Body
    Add-DemoAssertion 'SupportedBackendContractVersion' (@('0.9.0', '0.10.0', '0.11.0') -contains [string]$contract.info.version)
    [void](Invoke-DemoApi 'UnauthenticatedProfileDenied' 'GET' '/api/v1/users/me' 401)
    $veterinarian = (Invoke-DemoApi 'AdministratorAndVeterinarianPreflight' 'GET' ('/api/v1/veterinarians/' + $VeterinarianId) 200 $administratorTokenText).Body
    Add-DemoAssertion 'ActiveExistingVeterinarian' ((Get-ResponseGuid $veterinarian 'id') -eq $VeterinarianId.ToString() -and
        [string](Get-PropertyValue $veterinarian 'status') -eq 'ACTIVE')

    foreach ($label in @('A', 'B')) {
        $email = 'demo-' + $label.ToLowerInvariant() + '-' + $runId + '@example.test'
        $fullName = if ($label -eq 'A') { 'Maria Demostracion' } else { 'Pedro Demostracion' }
        $signupBody = @{ email=$email; fullName=$fullName; dateOfBirth='1960-05-20'; phoneNumber='+573001234567'; password=$ownerPasswordText }
        $signup = (Invoke-DemoApi ('Owner' + $label + 'Registration') 'POST' '/api/v1/auth/registrations' 201 '' $signupBody).Body
        $owner = [pscustomobject][ordered]@{ label=$label; id=(Get-ResponseGuid $signup 'id'); email=$email; petId=$null }
        $owners.Add($owner)
        [void](Save-DemoReport)
        $login = (Invoke-DemoApi ('Owner' + $label + 'Login') 'POST' '/api/v1/auth/sessions' 200 '' @{ email=$email; password=$ownerPasswordText }).Body
        Add-DemoAssertion ('Owner' + $label + 'ServerAssignedRole') ([string]$login.user.role -eq 'OWNER' -and
            (Get-ResponseGuid $login.user 'id') -eq $owner.id)
        $token = [string](Get-PropertyValue $login 'accessToken')
        if ([string]::IsNullOrWhiteSpace($token) -or $token -match '[\r\n\s]') { throw 'La sesion no contiene un token utilizable.' }
        if ($label -eq 'A') { $ownerAToken=$token } else { $ownerBToken=$token }
        $petName = if ($label -eq 'A') { 'Luna demo' } else { 'Michi demo' }
        $species = if ($label -eq 'A') { 'DOG' } else { 'CAT' }
        $pet = (Invoke-DemoApi ('Owner' + $label + 'PetCreation') 'POST' '/api/v1/pets' 201 $token @{ name=$petName; species=$species }).Body
        $owner.petId = Get-ResponseGuid $pet 'id'
        [void](Save-DemoReport)
    }
    [void](Invoke-DemoApi 'OtherOwnerPetDenied' 'GET' ('/api/v1/pets/' + $owners[0].petId) 404 $ownerBToken)
    foreach ($slotStart in @($FirstSlotStartsAt, $secondSlotStartsAt)) {
        $slotPath = '/api/v1/veterinarians/' + $VeterinarianId + '/availability-slots'
        $created = (Invoke-DemoApi ('AvailabilitySlot' + ($slots.Count + 1)) 'POST' $slotPath 201 $administratorTokenText @{ startsAt=$slotStart.ToString('o') }).Body
        $slot = [pscustomobject][ordered]@{ id=(Get-ResponseGuid $created 'id'); veterinarianId=$VeterinarianId.ToString()
            startsAt=$null; endsAt=$null; status=$null; version=$null }
        $slots.Add($slot)
        [void](Save-DemoReport)
        $slot.startsAt=Get-ResponseInstant $created 'startsAt'; $slot.endsAt=Get-ResponseInstant $created 'endsAt'
        $slot.status=[string](Get-PropertyValue $created 'status'); $slot.version=Get-ResponseVersion $created
        Add-DemoAssertion 'PublishedSlotIdentity' ((Get-ResponseGuid $created 'veterinarianId') -eq $VeterinarianId.ToString() -and $slot.status -eq 'PUBLISHED')
        [void](Save-DemoReport)
    }
    $query = '?from=' + [TimeZoneInfo]::ConvertTime($FirstSlotStartsAt, $businessZone).ToString('yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture) +
        '&to=' + [TimeZoneInfo]::ConvertTime($secondSlotStartsAt, $businessZone).ToString('yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture) + '&page=0&size=100'
    $available = (Invoke-DemoApi 'OwnerAvailableSlots' 'GET' ('/api/v1/availability-slots' + $query + '&veterinarianId=' + $VeterinarianId) 200 $ownerAToken).Body
    $availableIds = @($available.items | ForEach-Object { Get-ResponseGuid $_ 'id' })
    Add-DemoAssertion 'BothSlotsAvailable' (($availableIds -contains $slots[0].id) -and ($availableIds -contains $slots[1].id))

    $requestA = @{ clientRequestId=[Guid]::NewGuid().ToString(); petId=$owners[0].petId; availabilitySlotId=$slots[0].id
        expectedAvailabilitySlotVersion=$slots[0].version; reason='Consulta ficticia para demostracion A.' }
    $responseA = (Invoke-DemoApi 'AppointmentARequest' 'POST' '/api/v1/appointments' 201 $ownerAToken $requestA).Body
    $appointmentA = [pscustomobject][ordered]@{ label='A'; id=(Get-ResponseGuid $responseA 'id'); ownerId=$owners[0].id
        status='REQUESTED'; version=(Get-ResponseVersion $responseA) }
    $appointments.Add($appointmentA); [void](Save-DemoReport)
    Update-DemoAppointment $appointmentA $responseA 'REQUESTED'
    $replayA = (Invoke-DemoApi 'AppointmentAReplay' 'POST' '/api/v1/appointments' 200 $ownerAToken $requestA).Body
    Add-DemoAssertion 'ReplayPreservesIdentityAndVersion' ((Get-ResponseGuid $replayA 'id') -eq $appointmentA.id -and
        (Get-ResponseVersion $replayA) -eq $appointmentA.version)
    [void](Invoke-DemoApi 'OtherOwnerAppointmentDenied' 'GET' ('/api/v1/appointments/' + $appointmentA.id) 404 $ownerBToken)
    $appointmentAStatusPath = '/api/v1/appointments/' + $appointmentA.id + '/status'
    [void](Invoke-DemoApi 'OwnerCannotConfirm' 'PATCH' $appointmentAStatusPath 403 $ownerAToken @{ status='CONFIRMED'; expectedVersion=$appointmentA.version })
    $beforeConfirmation = $appointmentA.version
    $confirmedA = (Invoke-DemoApi 'AdministratorConfirmsA' 'PATCH' $appointmentAStatusPath 200 $administratorTokenText @{ status='CONFIRMED'; expectedVersion=$appointmentA.version }).Body
    Update-DemoAppointment $appointmentA $confirmedA 'CONFIRMED'
    [void](Invoke-DemoApi 'StaleCancellationVersionDenied' 'PATCH' $appointmentAStatusPath 409 $administratorTokenText @{ status='CANCELLED'; expectedVersion=$beforeConfirmation; reason='Prueba ficticia de version anterior.' })
    $currentA = (Invoke-DemoApi 'AppointmentAUnchangedAfterConflict' 'GET' ('/api/v1/appointments/' + $appointmentA.id) 200 $administratorTokenText).Body
    Add-DemoAssertion 'ConflictPreservesConfirmedAppointment' ((Get-ResponseVersion $currentA) -eq $appointmentA.version -and
        [string](Get-PropertyValue $currentA 'status') -eq 'CONFIRMED')
    $reschedulingBody = @{ clientRequestId=[Guid]::NewGuid().ToString(); availabilitySlotId=$slots[1].id
            expectedAvailabilitySlotVersion=$slots[1].version; expectedVersion=(Get-ResponseVersion $currentA)
            reason='Cambio ficticio para demostracion.'; agreementChannel='OTHER'
            agreementContactedAt=[DateTimeOffset]::UtcNow.AddSeconds(-1).ToString('o')
            agreementNote='SIMULACION: el propietario ficticio acepta el turno consecutivo. No hubo contacto con una persona real.' }
    $rescheduledA = (Invoke-DemoApi 'AdministratorReschedulesA' 'POST' ('/api/v1/appointments/' + $appointmentA.id + '/reschedulings') 200 $administratorTokenText $reschedulingBody).Body
    Update-DemoAppointment $appointmentA $rescheduledA 'CONFIRMED'
    Add-DemoAssertion 'ReprogrammingUsesSecondSlotTime' ((Get-ResponseInstant $rescheduledA 'startsAt') -eq $slots[1].startsAt)
    $cancelledA = (Invoke-DemoApi 'AdministratorCancelsA' 'PATCH' $appointmentAStatusPath 200 $administratorTokenText @{ status='CANCELLED'; expectedVersion=$appointmentA.version; reason='Cierre ficticio del recorrido A.' }).Body
    Update-DemoAppointment $appointmentA $cancelledA 'CANCELLED'

    $requestB = @{ clientRequestId=[Guid]::NewGuid().ToString(); petId=$owners[1].petId; availabilitySlotId=$slots[0].id
        expectedAvailabilitySlotVersion=$slots[0].version; reason='Consulta ficticia para dejar en agenda B.' }
    $responseB = (Invoke-DemoApi 'AppointmentBRequestOnFreedSlot' 'POST' '/api/v1/appointments' 201 $ownerBToken $requestB).Body
    $appointmentB = [pscustomobject][ordered]@{ label='B'; id=(Get-ResponseGuid $responseB 'id'); ownerId=$owners[1].id
        status='REQUESTED'; version=(Get-ResponseVersion $responseB) }
    $appointments.Add($appointmentB); [void](Save-DemoReport)
    Update-DemoAppointment $appointmentB $responseB 'REQUESTED'
    $confirmedB = (Invoke-DemoApi 'AdministratorConfirmsB' 'PATCH' ('/api/v1/appointments/' + $appointmentB.id + '/status') 200 $administratorTokenText @{ status='CONFIRMED'; expectedVersion=$appointmentB.version }).Body
    Update-DemoAppointment $appointmentB $confirmedB 'CONFIRMED'
    $history = (Invoke-DemoApi 'AppointmentAHistory' 'GET' ('/api/v1/appointments/' + $appointmentA.id + '/events?page=0&size=100') 200 $ownerAToken).Body
    $eventTypes = @($history.items | ForEach-Object { [string]$_.eventType })
    Add-DemoAssertion 'AdministrativeHistoryPreserved' (@('REQUESTED', 'CONFIRMED', 'RESCHEDULED', 'CANCELLED' |
        Where-Object { $eventTypes -notcontains $_ }).Count -eq 0)
    foreach ($appointment in @($appointmentA, $appointmentB)) {
        $notificationPath = '/api/v1/appointments/' + $appointment.id + '/notifications?page=0&size=100'
        $notices = (Invoke-DemoApi ('Appointment' + $appointment.label + 'Notifications') 'GET' $notificationPath 200 $administratorTokenText).Body
        $expectedType = if ($appointment.label -eq 'A') { 'OWNER_CANCELLED' } else { 'OWNER_CONFIRMED' }
        Add-DemoAssertion ('Appointment' + $appointment.label + 'NotificationIntent') (@($notices.items |
            Where-Object { $_.notificationType -eq $expectedType }).Count -gt 0)
    }
    $agenda = (Invoke-DemoApi 'OwnerBConfirmedAgenda' 'GET' ('/api/v1/appointments' + $query) 200 $ownerBToken).Body
    Add-DemoAssertion 'ConfirmedBVisibleToItsOwner' (@($agenda.items |
        Where-Object { $_.id -eq $appointmentB.id -and $_.status -eq 'CONFIRMED' }).Count -eq 1)
    $script:success = $true
} catch {
    # Never forward a response body, request JSON, token, password or raw exception diagnostics.
    $script:reportError = [pscustomobject][ordered]@{ stage=$script:currentStage; code=$script:failureCode }
} finally {
    if ($null -ne $script:httpClient) { $script:httpClient.Dispose() }
    $administratorTokenText=$null; $ownerPasswordText=$null; $ownerAToken=$null; $ownerBToken=$null
}

Write-Output (Save-DemoReport)
if (-not $script:success) { exit 1 }
