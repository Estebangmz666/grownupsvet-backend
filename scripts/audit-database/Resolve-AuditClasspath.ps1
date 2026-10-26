#Requires -Version 7.4
[CmdletBinding()]
param([Parameter(Mandatory)] [string]$OutputFile)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '../../mvnw.cmd') '-B' '-ntp' 'dependency:build-classpath' '-DincludeScope=runtime' "-Dmdep.outputFile=$OutputFile"
exit $LASTEXITCODE
