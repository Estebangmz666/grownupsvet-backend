#Requires -Version 7.4
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'MonitoringRuntime.Common.ps1')
try {
    $records = @(Get-MonitoringProcessState)
    # Verify all identities before stopping any process; PID reuse must fail closed.
    foreach ($record in $records) {
        $process = Get-MonitoringOwnedProcess $record
        if ($null -ne $process) { $process.Dispose() }
    }
    foreach ($record in $records) { Stop-MonitoringOwnedProcess $record }
    Write-MonitoringProcessState @()
    [pscustomobject]@{status='STOPPED';processesStopped=$records.Count;dataPreserved=$true} | ConvertTo-Json
} catch { [Console]::Error.WriteLine("MONITORING_STOP_REFUSED category=$(Get-MonitoringSafeErrorCategory $_); identidad no comprobada, sin detener procesos ajenos."); exit 1 }
