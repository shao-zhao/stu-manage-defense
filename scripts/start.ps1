[CmdletBinding()]
param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SkipRedis
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root 'runtime'
$pidFile = Join-Path $runtime 'processes.json'
$logDir = Join-Path $runtime 'logs'

function Import-LocalEnvironment {
    $envFile = Join-Path $root '.env'
    if (-not (Test-Path $envFile)) { return }
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $name, $value = $Matches[1], $Matches[2]
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

function Test-RecordedProcess([int]$Id) {
    if ($Id -le 0) { return $false }
    return $null -ne (Get-Process -Id $Id -ErrorAction SilentlyContinue)
}

if ($BackendOnly -and $FrontendOnly) { throw 'BackendOnly and FrontendOnly cannot be used together.' }
New-Item -ItemType Directory -Force -Path $runtime, $logDir, (Join-Path $runtime 'socket-tmp') | Out-Null
Import-LocalEnvironment

if (-not $SkipRedis) {
    docker compose -f (Join-Path $root 'compose.yaml') up -d redis
    if ($LASTEXITCODE -ne 0) { throw 'Redis could not be started. Run scripts/check-environment.ps1 for details.' }
    $deadline = (Get-Date).AddSeconds(40)
    do {
        $redisState = docker inspect --format '{{.State.Health.Status}}' stu-manage-redis 2>$null
        if ($redisState -eq 'healthy') { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ($redisState -ne 'healthy') { throw 'Redis container did not become healthy within 40 seconds.' }
}

$existing = @{}
if (Test-Path $pidFile) {
    try { $existing = Get-Content -Raw $pidFile | ConvertFrom-Json -AsHashtable } catch { $existing = @{} }
}
$pids = @{}
if ($existing.backend -and (Test-RecordedProcess $existing.backend)) { $pids.backend = $existing.backend }
if ($existing.frontend -and (Test-RecordedProcess $existing.frontend)) { $pids.frontend = $existing.frontend }

if (-not $FrontendOnly -and -not $pids.backend) {
    if (-not $env:MYSQL_PASSWORD) { throw 'MYSQL_PASSWORD is required. Copy .env.example to .env and set it, or set the environment variable for this session.' }
    if (-not $env:JWT_SECRET -or $env:JWT_SECRET -eq 'replace-with-a-long-random-secret') { throw 'JWT_SECRET must be set to a non-placeholder value in .env or this session.' }
    $backendDir = Join-Path $root 'stu-backend'
    $socketTmp = (Resolve-Path (Join-Path $runtime 'socket-tmp')).Path
    $backendLog = Join-Path $logDir 'backend.log'
    $backendCommand = "call `"$backendDir\mvnw.cmd`" `"-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=$socketTmp`" spring-boot:run 1>> `"$backendLog`" 2>>&1"
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', $backendCommand) -WorkingDirectory $backendDir -WindowStyle Hidden -PassThru
    $pids.backend = $process.Id
}

if (-not $BackendOnly -and -not $pids.frontend) {
    $frontendDir = Join-Path $root 'stu-frontend'
    $frontendLog = Join-Path $logDir 'frontend.log'
    $frontendCommand = "npm.cmd run dev -- --host 127.0.0.1 1>> `"$frontendLog`" 2>>&1"
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', $frontendCommand) -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru
    $pids.frontend = $process.Id
}

$pids | ConvertTo-Json | Set-Content -Encoding utf8 $pidFile
Write-Host 'Services are starting in the background. Run scripts/check-environment.ps1 to verify readiness.'
