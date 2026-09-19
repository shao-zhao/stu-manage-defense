param([switch]$ApplicationOnly)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$data = Join-Path $root 'data'
$pidFile = Join-Path $data 'processes.json'
$settingsFile = Join-Path $data 'settings.env'
$mysqlPort = 13306
$redisPort = 16379

$mysqlAdmin = Join-Path $root 'runtime\mysql\bin\mysqladmin.exe'
$redisCli = Join-Path $root 'runtime\redis\redis-cli.exe'
$expected = @{
    app = Join-Path $root 'runtime\java\bin\java.exe'
    redis = Join-Path $root 'runtime\redis\redis-server.exe'
    mysql = Join-Path $root 'runtime\mysql\bin\mysqld.exe'
}

function Read-State {
    $state = @{}
    if (Test-Path -LiteralPath $pidFile) {
        try {
            $saved = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
            foreach ($name in @('mysql', 'redis', 'app')) {
                if ($saved.$name) { $state[$name] = $saved.$name }
            }
        } catch { throw 'Process records cannot be read; refusing to stop any process.' }
    }
    return $state
}

function Save-State($State) {
    if ($State.Count -eq 0) {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    } else {
        ([pscustomobject]$State | ConvertTo-Json) | Set-Content -LiteralPath $pidFile -Encoding UTF8
    }
}

function Get-OwnedProcess($Record, [string]$ExpectedPath) {
    if ($null -eq $Record -or $null -eq $Record.id) { return $null }
    $process = Get-Process -Id ([int]$Record.id) -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    try {
        $recordedStart = ([datetime]$Record.startedAt).ToUniversalTime()
        if ([math]::Abs(($process.StartTime.ToUniversalTime() - $recordedStart).TotalMilliseconds) -lt 1 -and
            [IO.Path]::GetFullPath($process.Path) -eq [IO.Path]::GetFullPath($ExpectedPath)) {
            return $process
        }
    } catch {}
    return $null
}

function Wait-Exit($Process, [int]$Seconds) {
    $until = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $until) {
        if (-not (Get-Process -Id $Process.Id -ErrorAction SilentlyContinue)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Force-OwnedStop([string]$Name, $Record) {
    $process = Get-OwnedProcess $Record $expected[$Name]
    if ($null -eq $process) {
        if (-not (Get-Process -Id ([int]$Record.id) -ErrorAction SilentlyContinue)) {
            # 已优雅退出的进程没有必要保留过期记录。
            return $true
        }
        Write-Warning "Not stopping ${Name}: process identity no longer matches this package."
        return $false
    }
    if (-not (Wait-Exit $process 8)) {
        # 超时后再次取进程并校验，避免 PID 重用时误杀外部程序。
        $process = Get-OwnedProcess $Record $expected[$Name]
        if ($null -ne $process) { Stop-Process -Id $process.Id -Force }
    }
    return $true
}

function Read-RootPassword {
    if (-not (Test-Path -LiteralPath $settingsFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $settingsFile) {
        if ($line -match '^MYSQL_ROOT_PASSWORD=(.*)$') { return $Matches[1] }
    }
    return $null
}

function Try-MySqlShutdown($Password) {
    if (-not (Test-Path -LiteralPath $mysqlAdmin) -or -not $Password) { return }
    $previous = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $old = $ErrorActionPreference
        $ErrorActionPreference = 'SilentlyContinue'
        & $mysqlAdmin --protocol=TCP --host=127.0.0.1 --port=$mysqlPort --user=root shutdown 2>$null | Out-Null
        $ErrorActionPreference = $old
    } catch {
        # 认证或连接失败后仍由已核验的强制停止作为兜底。
    } finally {
        $ErrorActionPreference = 'Stop'
        if ($null -eq $previous) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        else { $env:MYSQL_PWD = $previous }
    }
}

$state = Read-State
if ($state.Count -eq 0) {
    Write-Host 'No package-owned processes are recorded.'
    exit 0
}

# ApplicationOnly 只停止本包 Java 进程，并保留 MySQL/Redis 所有权记录。
if ($ApplicationOnly) {
    if ($state.ContainsKey('app') -and (Force-OwnedStop 'app' $state['app'])) { $state.Remove('app') }
    Save-State $state
    exit 0
}

if (Force-OwnedStop 'app' $state['app']) { $state.Remove('app') }

$redisProcess = Get-OwnedProcess $state['redis'] $expected['redis']
if ($null -ne $redisProcess -and (Test-Path -LiteralPath $redisCli)) {
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & $redisCli -h 127.0.0.1 -p $redisPort shutdown save 2>$null | Out-Null
    } finally { $ErrorActionPreference = $old }
}
if (Force-OwnedStop 'redis' $state['redis']) { $state.Remove('redis') }

$mysqlProcess = Get-OwnedProcess $state['mysql'] $expected['mysql']
if ($null -ne $mysqlProcess) { Try-MySqlShutdown (Read-RootPassword) }
if (Force-OwnedStop 'mysql' $state['mysql']) { $state.Remove('mysql') }

Save-State $state

