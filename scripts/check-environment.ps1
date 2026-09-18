[CmdletBinding()]
param([switch]$WaitForApplications)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
function Report([string]$Label, [bool]$Ok, [string]$Detail) {
    $state = if ($Ok) { 'OK' } else { 'FAIL' }
    Write-Host ("[{0}] {1} {2}" -f $state, $Label, $Detail)
    return $Ok
}

$allOk = $true
$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
$allOk = (Report 'Java' ($LASTEXITCODE -eq 0) $javaVersion) -and $allOk
$nodeVersion = (& node --version 2>$null)
$allOk = (Report 'Node.js' ($LASTEXITCODE -eq 0) $nodeVersion) -and $allOk
$dockerVersion = (& docker version --format '{{.Server.Version}}' 2>$null)
$allOk = (Report 'Docker daemon' ($LASTEXITCODE -eq 0) $dockerVersion) -and $allOk

$mysqlPort = Test-NetConnection -ComputerName '127.0.0.1' -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue
$allOk = (Report 'MySQL port 3306' $mysqlPort 'local connection') -and $allOk
$redisPort = Test-NetConnection -ComputerName '127.0.0.1' -Port 6379 -InformationLevel Quiet -WarningAction SilentlyContinue
$allOk = (Report 'Redis port 6379' $redisPort 'loopback only') -and $allOk

if ($WaitForApplications) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        try { $health = Invoke-RestMethod 'http://127.0.0.1:9090/actuator/health' -TimeoutSec 4; $backendOk = $health.status -eq 'UP' } catch { $backendOk = $false }
        $frontendOk = Test-NetConnection -ComputerName '127.0.0.1' -Port 5173 -InformationLevel Quiet -WarningAction SilentlyContinue
        if ($backendOk -and $frontendOk) { break }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    $allOk = (Report 'Backend health' $backendOk 'http://127.0.0.1:9090/actuator/health') -and $allOk
    $allOk = (Report 'Frontend port' $frontendOk 'http://127.0.0.1:5173') -and $allOk
}

if (-not $allOk) { exit 1 }
