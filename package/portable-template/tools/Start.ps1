param([switch]$InfrastructureOnly)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
if ($root.ToCharArray() | Where-Object { [int][char]$_ -gt 127 } | Select-Object -First 1) {
    throw 'MySQL requires an English-only package path. Move the package and retry.'
}

$data = Join-Path $root 'data'
$logs = Join-Path $root 'logs'
$pidFile = Join-Path $data 'processes.json'
$settingsFile = Join-Path $data 'settings.env'
$markerFile = Join-Path $data 'mysql-initialized.marker'
$bootstrapFile = Join-Path $data 'mysql-bootstrap.sql'
$mysqlPort = 13306
$redisPort = 16379
$appPort = 18090

$mysqlHome = Join-Path $root 'runtime\mysql'
$mysqlData = Join-Path $data 'mysql'
$mysqlIni = Join-Path $data 'my.ini'
$mysql = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysqlClient = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqlAdmin = Join-Path $mysqlHome 'bin\mysqladmin.exe'
$redis = Join-Path $root 'runtime\redis\redis-server.exe'
$redisCli = Join-Path $root 'runtime\redis\redis-cli.exe'
$java = Join-Path $root 'runtime\java\bin\java.exe'
$jar = Join-Path $root 'app\stu-manage.jar'

function Test-Port([int]$Port) {
    $tcp = New-Object Net.Sockets.TcpClient
    try { $tcp.Connect('127.0.0.1', $Port); return $true }
    catch { return $false }
    finally { $tcp.Close() }
}

function Read-State {
    $state = @{}
    if (Test-Path -LiteralPath $pidFile) {
        try {
            $saved = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
            foreach ($name in @('mysql', 'redis', 'app')) {
                if ($saved.$name) { $state[$name] = $saved.$name }
            }
        } catch { Write-Warning 'Ignoring unreadable process records.' }
    }
    return $state
}

function Save-State($State) {
    ([pscustomobject]$State | ConvertTo-Json) | Set-Content -LiteralPath $pidFile -Encoding UTF8
}

function New-Record($Process, [string]$ExpectedPath) {
    [pscustomobject]@{
        id = $Process.Id
        startedAt = $Process.StartTime.ToUniversalTime().ToString('o')
        path = if ($Process.Path) { $Process.Path } else { $ExpectedPath }
    }
}

function Test-Owned($Record, [string]$ExpectedPath) {
    if ($null -eq $Record -or $null -eq $Record.id) { return $false }
    $process = Get-Process -Id ([int]$Record.id) -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $false }
    try {
        $recordedStart = ([datetime]$Record.startedAt).ToUniversalTime()
        return [math]::Abs(($process.StartTime.ToUniversalTime() - $recordedStart).TotalMilliseconds) -lt 1 -and
            [IO.Path]::GetFullPath($process.Path) -eq [IO.Path]::GetFullPath($ExpectedPath)
    } catch { return $false }
}

function Assert-Port([string]$Name, [int]$Port, $State, [string]$ExpectedPath) {
    if ((Test-Port $Port) -and -not (Test-Owned $State[$Name] $ExpectedPath)) {
        throw "Port $Port is used by another program. This package will not stop external processes."
    }
}

function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([Convert]::ToBase64String($bytes).Replace('+', 'A').Replace('/', 'B').Replace('=', ''))
}

function Read-Settings {
    if (-not (Test-Path -LiteralPath $settingsFile)) { return $null }
    $settings = @{}
    Get-Content -LiteralPath $settingsFile | ForEach-Object {
        if ($_ -match '^([A-Z_]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
    }
    if (-not $settings.MYSQL_ROOT_PASSWORD -or -not $settings.MYSQL_PASSWORD -or -not $settings.JWT_SECRET) {
        throw 'data\settings.env is incomplete. Leave data untouched and extract a fresh package.'
    }
    return $settings
}

function Write-Settings($Settings) {
    @(
        "MYSQL_ROOT_PASSWORD=$($Settings.MYSQL_ROOT_PASSWORD)"
        "MYSQL_PASSWORD=$($Settings.MYSQL_PASSWORD)"
        "JWT_SECRET=$($Settings.JWT_SECRET)"
    ) | Set-Content -LiteralPath $settingsFile -Encoding ASCII
}

function Test-MySqlLogin([string]$User, [string]$Password) {
    $previous = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $old = $ErrorActionPreference
        $ErrorActionPreference = 'SilentlyContinue'
        & $mysqlClient --protocol=TCP --host=127.0.0.1 --port=$mysqlPort --user=$User --database=stu_manage -N -e 'SELECT 1' 2>$null | Out-Null
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = $old
        return $exitCode -eq 0
    } finally {
        if ($null -eq $previous) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        else { $env:MYSQL_PWD = $previous }
    }
}

function Wait-MySql {
    for ($i = 0; $i -lt 80; $i++) {
        $old = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'SilentlyContinue'
            & $mysqlAdmin --protocol=TCP --host=127.0.0.1 --port=$mysqlPort ping 2>$null | Out-Null
            $exitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $old }
        if ($exitCode -eq 0) { return }
        Start-Sleep -Milliseconds 750
    }
    throw 'MySQL did not become ready. See logs\mysql.err.'
}

function Wait-Redis {
    for ($i = 0; $i -lt 40; $i++) {
        $old = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'SilentlyContinue'
            & $redisCli -h 127.0.0.1 -p $redisPort ping 2>$null | Out-Null
            $exitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $old }
        if ($exitCode -eq 0) { return }
        Start-Sleep -Milliseconds 500
    }
    throw 'Redis did not become ready. See logs\redis.log.'
}

function Wait-App {
    for ($i = 0; $i -lt 100; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$appPort/actuator/health" -TimeoutSec 2
            $content = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { $response.Content }
            $health = $content | ConvertFrom-Json
            if ($health.status -eq 'UP') { return }
        } catch {}
        Start-Sleep -Milliseconds 750
    }
    throw 'Application health is not UP. See logs\app.out.log and logs\app.err.log.'
}

foreach ($file in @($mysql, $mysqlClient, $mysqlAdmin, $redis, $redisCli)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Package file missing: $file" }
}
if (-not $InfrastructureOnly) {
    foreach ($file in @($java, $jar)) {
        if (-not (Test-Path -LiteralPath $file)) { throw "Package file missing: $file" }
    }
}

New-Item -ItemType Directory -Force -Path $data, $logs, (Join-Path $data 'uploads'), (Join-Path $data 'tmp'), (Join-Path $data 'mysql-tmp') | Out-Null
$state = Read-State
Assert-Port 'mysql' $mysqlPort $state $mysql
Assert-Port 'redis' $redisPort $state $redis
if (-not $InfrastructureOnly) { Assert-Port 'app' $appPort $state $java }

# 每次启动重建可移动目录的绝对路径配置。
$slash = { param($Path) $Path.Replace('\', '/') }
$ini = @"
[mysqld]
basedir="$(& $slash $mysqlHome)"
datadir="$(& $slash $mysqlData)"
port=$mysqlPort
bind-address=127.0.0.1
skip-name-resolve
skip-networking=0
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
tmpdir="$(& $slash (Join-Path $data 'mysql-tmp'))"
log-error="$(& $slash (Join-Path $logs 'mysql.err'))"
"@
[IO.File]::WriteAllText($mysqlIni, $ini, (New-Object Text.UTF8Encoding($false)))

$settings = Read-Settings
$systemTables = Join-Path $mysqlData 'mysql'
$needsBootstrap = -not (Test-Path -LiteralPath $markerFile)

if (-not (Test-Path -LiteralPath $systemTables)) {
    $files = @(Get-ChildItem -LiteralPath $mysqlData -Force -ErrorAction SilentlyContinue)
    if ($files.Count -gt 0) {
        throw 'MySQL data is incomplete and was preserved. Extract a fresh package instead of deleting user data.'
    }
    if ($null -eq $settings) {
        $settings = @{
            MYSQL_ROOT_PASSWORD = New-Secret
            MYSQL_PASSWORD = New-Secret
            JWT_SECRET = New-Secret
        }
        # 先保存随机密钥，初始化重试不会换一套凭据。
        Write-Settings $settings
    }
    New-Item -ItemType Directory -Force -Path $mysqlData | Out-Null
    & $mysql "--defaults-file=$mysqlIni" --initialize-insecure --console
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL initialization failed. Data was preserved; see logs\mysql.err and extract a fresh package.'
    }
} elseif ($null -eq $settings) {
    throw 'MySQL system tables exist but settings.env is absent. Refusing to guess credentials; extract a fresh package.'
}

if ($needsBootstrap) {
    # 账号创建、密码重设与授权均可重复运行。
    @(
        "CREATE DATABASE IF NOT EXISTS stu_manage CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
        "CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '$($settings.MYSQL_ROOT_PASSWORD)';"
        "ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY '$($settings.MYSQL_ROOT_PASSWORD)';"
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '$($settings.MYSQL_ROOT_PASSWORD)';"
        "GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;"
        "CREATE USER IF NOT EXISTS 'stu_app'@'127.0.0.1' IDENTIFIED BY '$($settings.MYSQL_PASSWORD)';"
        "CREATE USER IF NOT EXISTS 'stu_app'@'localhost' IDENTIFIED BY '$($settings.MYSQL_PASSWORD)';"
        "ALTER USER 'stu_app'@'127.0.0.1' IDENTIFIED BY '$($settings.MYSQL_PASSWORD)';"
        "ALTER USER 'stu_app'@'localhost' IDENTIFIED BY '$($settings.MYSQL_PASSWORD)';"
        "GRANT ALL PRIVILEGES ON stu_manage.* TO 'stu_app'@'127.0.0.1';"
        "GRANT ALL PRIVILEGES ON stu_manage.* TO 'stu_app'@'localhost';"
        "FLUSH PRIVILEGES;"
    ) | Set-Content -LiteralPath $bootstrapFile -Encoding ASCII
}

if (-not (Test-Owned $state['mysql'] $mysql)) {
    $args = '--defaults-file="' + $mysqlIni + '"'
    if ($needsBootstrap) { $args += ' --init-file="' + $bootstrapFile + '"' }
    $process = Start-Process -FilePath $mysql -ArgumentList $args -WindowStyle Hidden -PassThru
    # 每个新进程立即记录；Stop.ps1 会再次核验 PID、启动时间和路径。
    $state['mysql'] = New-Record $process $mysql
    Save-State $state
}
Wait-MySql

if ($needsBootstrap) {
    if (-not (Test-MySqlLogin 'root' $settings.MYSQL_ROOT_PASSWORD)) { throw 'Root SELECT verification failed; bootstrap files were preserved.' }
    if (-not (Test-MySqlLogin 'stu_app' $settings.MYSQL_PASSWORD)) { throw 'Application-account SELECT verification failed; bootstrap files were preserved.' }
    Remove-Item -LiteralPath $bootstrapFile -Force -ErrorAction SilentlyContinue
    Set-Content -LiteralPath $markerFile -Value (Get-Date).ToUniversalTime().ToString('o') -Encoding ASCII
}

$redisData = Join-Path $data 'redis'
New-Item -ItemType Directory -Force -Path $redisData | Out-Null
if (-not (Test-Owned $state['redis'] $redis)) {
    $args = '--bind 127.0.0.1 --port ' + $redisPort + ' --appendonly yes --dir "' + $redisData + '" --logfile "' + (Join-Path $logs 'redis.log') + '"'
    $process = Start-Process -FilePath $redis -ArgumentList $args -WindowStyle Hidden -PassThru
    $state['redis'] = New-Record $process $redis
    Save-State $state
}
Wait-Redis
if ($InfrastructureOnly) { Write-Host 'MySQL and Redis are ready; application JAR was skipped.'; exit 0 }

if (-not (Test-Owned $state['app'] $java)) {
    $old = @{}
    foreach ($name in @('MYSQL_URL','MYSQL_USER','MYSQL_PASSWORD','REDIS_HOST','REDIS_PORT','JWT_SECRET','SERVER_PORT','UPLOAD_PATH','SPRING_PROFILES_ACTIVE')) {
        $old[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    try {
        $env:SPRING_PROFILES_ACTIVE = 'demo'
        $env:MYSQL_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/stu_manage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
        $env:MYSQL_USER = 'stu_app'
        $env:MYSQL_PASSWORD = $settings.MYSQL_PASSWORD
        $env:REDIS_HOST = '127.0.0.1'
        $env:REDIS_PORT = "$redisPort"
        $env:JWT_SECRET = $settings.JWT_SECRET
        $env:SERVER_PORT = "$appPort"
        $env:UPLOAD_PATH = Join-Path $data 'uploads'
        $tmp = Join-Path $data 'tmp'
        $args = '-Djava.io.tmpdir="' + $tmp + '" -Djdk.net.unixdomain.tmpdir="' + $tmp + '" -jar "' + $jar + '" --server.address=127.0.0.1'
        $process = Start-Process -FilePath $java -ArgumentList $args -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs 'app.out.log') -RedirectStandardError (Join-Path $logs 'app.err.log')
        $state['app'] = New-Record $process $java
        Save-State $state
    } finally {
        foreach ($name in $old.Keys) {
            if ($null -eq $old[$name]) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
            else { Set-Item "Env:$name" $old[$name] }
        }
    }
}
Wait-App
Write-Host "Ready: http://127.0.0.1:$appPort"

