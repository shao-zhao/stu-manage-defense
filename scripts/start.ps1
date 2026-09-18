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

function Ensure-DockerDaemon {
    docker version --format '{{.Server.Version}}' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { return }
    $desktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path $desktop)) { throw 'Docker Desktop is required to run the local Redis container.' }
    if (-not (Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue)) {
        Start-Process -FilePath $desktop -WindowStyle Hidden
    }
    $deadline = (Get-Date).AddSeconds(60)
    do {
        docker version --format '{{.Server.Version}}' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw 'Docker Desktop did not become ready within 60 seconds.'
}

function Start-PortableRedis {
    $redisDir = Join-Path $runtime 'redis/Redis-8.10.2-Windows-x64-cygwin'
    $server = Join-Path $redisDir 'redis-server.exe'
    $cli = Join-Path $redisDir 'redis-cli.exe'
    if (-not (Test-Path $server) -or -not (Test-Path $cli)) {
        throw 'Redis is unavailable. Start Docker Desktop, or install the documented portable Redis release under runtime/redis.'
    }
    $dataDir = Join-Path $runtime 'redis-data'
    $redisLog = Join-Path $logDir 'redis.log'
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    $process = Start-Process -FilePath $server -ArgumentList @('--bind', '127.0.0.1', '--port', '6379', '--appendonly', 'yes', '--dir', $dataDir, '--logfile', $redisLog) -WorkingDirectory $redisDir -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds 2
    & $cli -h 127.0.0.1 -p 6379 ping | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Portable Redis did not respond to PING.' }
    $process.Id | Set-Content -Encoding ascii (Join-Path $runtime 'redis.pid')
}

if ($BackendOnly -and $FrontendOnly) { throw 'BackendOnly and FrontendOnly cannot be used together.' }
New-Item -ItemType Directory -Force -Path $runtime, $logDir | Out-Null
Import-LocalEnvironment

if (-not $SkipRedis) {
    $redisReady = Test-NetConnection -ComputerName '127.0.0.1' -Port 6379 -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $redisReady) {
        $portableServer = Join-Path $runtime 'redis/Redis-8.10.2-Windows-x64-cygwin/redis-server.exe'
        if (Test-Path $portableServer) {
            Start-PortableRedis
        } else {
            Ensure-DockerDaemon
            docker compose -f (Join-Path $root 'compose.yaml') up -d redis
            if ($LASTEXITCODE -ne 0) { throw 'Docker Redis start failed.' }
        }
    }
    $deadline = (Get-Date).AddSeconds(40)
    do {
        $redisReady = Test-NetConnection -ComputerName '127.0.0.1' -Port 6379 -InformationLevel Quiet -WarningAction SilentlyContinue
        if ($redisReady) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if (-not $redisReady) { throw 'Redis did not become reachable on 127.0.0.1:6379.' }
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
    $backendDir = Join-Path $root 'stu-backend'
    $socketTmp = Join-Path $backendDir 'target/socket-tmp'
    New-Item -ItemType Directory -Force -Path $socketTmp | Out-Null
    $socketTmp = (Resolve-Path $socketTmp).Path
    $backendLog = Join-Path $logDir 'backend.log'
    $java17Home = 'C:\Users\Administrator\.jdks\dragonwell-17.0.19'
    $javaHomePrefix = ''
    if (Test-Path (Join-Path $java17Home 'bin/java.exe')) {
        $javaHomePrefix = "set `"JAVA_HOME=$java17Home`" && set `"PATH=$java17Home\bin;%PATH%`" && "
    }
    # JAVA_TOOL_OPTIONS is inherited by Maven and its Spring Boot child JVM only;
    # it avoids the JDK 26 Windows loopback Unix-domain socket failure.
    $backendCommand = "$javaHomePrefix" + "set `"JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=$socketTmp -Djdk.net.unixdomain.tmpdir=$socketTmp`" && call `"$backendDir\mvnw.cmd`" spring-boot:run 1>> `"$backendLog`" 2>>&1"
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', $backendCommand) -WorkingDirectory $backendDir -WindowStyle Hidden -PassThru
    $pids.backend = $process.Id
}

if (-not $BackendOnly -and -not $pids.frontend) {
    $frontendDir = Join-Path $root 'stu-frontend'
    $frontendLog = Join-Path $logDir 'frontend.log'
    if (-not (Test-Path (Join-Path $frontendDir 'node_modules/.bin/vite'))) {
        Write-Host 'Installing frontend dependencies from package-lock.json...'
        & npm.cmd ci --prefix $frontendDir
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    }
    $frontendCommand = "npm.cmd run dev -- --host 127.0.0.1 --strictPort 1>> `"$frontendLog`" 2>>&1"
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', $frontendCommand) -WorkingDirectory $frontendDir -WindowStyle Hidden -PassThru
    $pids.frontend = $process.Id
}

$pids | ConvertTo-Json | Set-Content -Encoding utf8 $pidFile

$deadline = (Get-Date).AddSeconds(100)
do {
    $backendReady = $BackendOnly -eq $false
    $frontendReady = $FrontendOnly -eq $false
    if (-not $FrontendOnly) {
        try { $backendReady = (Invoke-RestMethod 'http://127.0.0.1:9090/actuator/health' -TimeoutSec 4).status -eq 'UP' } catch { $backendReady = $false }
    }
    if (-not $BackendOnly) {
        $frontendReady = Test-NetConnection -ComputerName '127.0.0.1' -Port 5173 -InformationLevel Quiet -WarningAction SilentlyContinue
    }
    if ($backendReady -and $frontendReady) { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

if (-not ($backendReady -and $frontendReady)) {
    throw "Services did not become ready. See $logDir and run scripts/check-environment.ps1 -WaitForApplications."
}
$readyNames = @()
if (-not $FrontendOnly) { $readyNames += 'backend' }
if (-not $BackendOnly) { $readyNames += 'frontend' }
Write-Host ("Ready: " + ($readyNames -join ', ') + '.')
