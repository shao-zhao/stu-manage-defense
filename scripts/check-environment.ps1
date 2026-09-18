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
$javaMajor = if ($javaVersion -match 'version "(\d+)') { [int]$Matches[1] } else { 0 }
$allOk = (Report 'Java 17+' ($LASTEXITCODE -eq 0 -and $javaMajor -ge 17) $javaVersion) -and $allOk
$nodeVersion = (& node --version 2>$null)
try { $nodeParsed = [version]$nodeVersion.TrimStart('v'); $nodeOk = (($nodeParsed.Major -eq 22 -and $nodeParsed.Minor -ge 18) -or $nodeParsed.Major -ge 24) } catch { $nodeOk = $false }
$allOk = (Report 'Node.js (22.18+ or 24.12+)' $nodeOk $nodeVersion) -and $allOk
$dockerVersion = (& docker version --format '{{.Server.Version}}' 2>$null)
$dockerOk = $LASTEXITCODE -eq 0
if (-not $dockerOk) {
    $portableRedis = Test-Path (Join-Path $root 'runtime/redis/Redis-8.10.2-Windows-x64-cygwin/redis-server.exe')
    $dockerOk = $portableRedis
    $dockerVersion = if ($portableRedis) { 'unavailable; portable Redis fallback installed' } else { 'unavailable' }
}
$allOk = (Report 'Docker daemon or Redis fallback' $dockerOk $dockerVersion) -and $allOk

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
