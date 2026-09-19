$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$settingsFile = Join-Path $root 'data\settings.env'
$mysql = Join-Path $root 'runtime\mysql\bin\mysql.exe'
$redisCli = Join-Path $root 'runtime\redis\redis-cli.exe'
$failed = $false

function Write-Pass([string]$Message) {
    Write-Host "[OK]   $Message"
}

function Write-Failure([string]$Message) {
    Write-Host "[DOWN] $Message"
    $script:failed = $true
}

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('127.0.0.1', $Port)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-Setting([string]$Name) {
    if (-not (Test-Path -LiteralPath $settingsFile)) { return $null }
    $line = Get-Content -LiteralPath $settingsFile | Where-Object { $_ -match ("^{0}=" -f [regex]::Escape($Name)) } | Select-Object -First 1
    if ($null -eq $line) { return $null }
    return $line.Substring($line.IndexOf('=') + 1)
}

# 每项独立检查；即使某一项失败也输出其余服务状态，方便现场排障。
if (Test-Port 13306) {
    Write-Pass 'MySQL 13306 正在监听'
} else {
    Write-Failure 'MySQL 13306 未监听'
}

$appPassword = Get-Setting 'MYSQL_PASSWORD'
if (-not (Test-Path -LiteralPath $settingsFile)) {
    Write-Failure '缺少 data\settings.env，请先运行 START.cmd 初始化本包'
} elseif ([string]::IsNullOrWhiteSpace($appPassword)) {
    Write-Failure 'settings.env 缺少 MYSQL_PASSWORD，无法验证应用数据库账号'
} elseif (-not (Test-Path -LiteralPath $mysql)) {
    Write-Failure '缺少本包 mysql.exe，无法验证应用数据库账号'
} else {
    $oldPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $appPassword
        # 原生命令的 stderr 在连接失败时是正常诊断，不能让 Stop 偏好提前中断检查。
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $mysql --protocol=TCP --host=127.0.0.1 --port=13306 --user=stu_app `
                --default-character-set=utf8mb4 --database=stu_manage -N -e 'SELECT 1' 2>$null | Out-Null
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        if ($exitCode -eq 0) {
            Write-Pass 'MySQL 应用账号可查询 stu_manage'
        } else {
            Write-Failure 'MySQL 应用账号不能查询 stu_manage'
        }
    } finally {
        if ($null -eq $oldPassword) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $oldPassword
        }
    }
}

if (-not (Test-Port 16379)) {
    Write-Failure 'Redis 16379 未监听'
} elseif (-not (Test-Path -LiteralPath $redisCli)) {
    Write-Failure '缺少本包 redis-cli.exe，无法验证 Redis'
} else {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $pong = & $redisCli -h 127.0.0.1 -p 16379 ping 2>$null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -eq 0 -and ($pong | Select-Object -Last 1) -eq 'PONG') {
        Write-Pass 'Redis PONG'
    } else {
        Write-Failure 'Redis 未返回 PONG'
    }
}

try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18090/actuator/health' -TimeoutSec 3
    # Windows PowerShell 5.1 在 application/octet-stream 响应中返回 byte[]，需要先按 UTF-8 解码。
    $content = $response.Content
    if ($content -is [byte[]]) {
        $content = [Text.Encoding]::UTF8.GetString($content)
    }
    $health = $content | ConvertFrom-Json
    if ($health.status -eq 'UP') {
        Write-Pass '应用健康检查 UP'
    } else {
        Write-Failure '应用健康检查未返回 UP'
    }
} catch {
    Write-Failure '应用健康检查端点不可用'
}

if ($failed) { exit 1 }
