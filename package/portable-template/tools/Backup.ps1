param([switch]$NoRestart)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$data = Join-Path $root 'data'
$settingsFile = Join-Path $data 'settings.env'
$processFile = Join-Path $data 'processes.json'
$uploads = Join-Path $data 'uploads'
$dump = Join-Path $root 'runtime\mysql\bin\mysqldump.exe'
$java = Join-Path $root 'runtime\java\bin\java.exe'

function Read-Settings {
    if (-not (Test-Path -LiteralPath $settingsFile)) { throw '未找到 data\settings.env，请先运行 START.cmd 初始化。' }
    $values = @{}
    Get-Content -LiteralPath $settingsFile | ForEach-Object {
        if ($_ -match '^([A-Z_]+)=(.*)$') { $values[$Matches[1]] = $Matches[2] }
    }
    if ([string]::IsNullOrWhiteSpace($values.MYSQL_ROOT_PASSWORD)) { throw '本包 MySQL 管理凭据缺失。' }
    return $values
}

function Test-RecordedApplicationRunning {
    if (-not (Test-Path -LiteralPath $processFile)) { return $false }
    try {
        $record = (Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json).app
        if ($null -eq $record -or $null -eq $record.id) { return $false }
        $process = Get-Process -Id ([int]$record.id) -ErrorAction SilentlyContinue
        if ($null -eq $process) { return $false }
        $recordStart = ([datetime]$record.startedAt).ToUniversalTime()
        $startDifference = [Math]::Abs(($process.StartTime.ToUniversalTime() - $recordStart).TotalMilliseconds)
        return $startDifference -lt 1 -and
            [IO.Path]::GetFullPath($process.Path) -eq [IO.Path]::GetFullPath($java) -and
            [IO.Path]::GetFullPath($record.path) -eq [IO.Path]::GetFullPath($java)
    } catch { return $false }
}

function Get-UploadManifest([string]$Directory) {
    if (-not (Test-Path -LiteralPath $Directory)) { return @() }
    return @(Get-ChildItem -LiteralPath $Directory -File -Recurse | ForEach-Object {
        [pscustomobject]@{
            path = $_.FullName.Substring($Directory.Length).TrimStart('\')
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            length = $_.Length
        }
    })
}

$settings = Read-Settings
if (-not (Test-Path -LiteralPath $dump)) { throw "找不到 mysqldump：$dump" }
$wasRunning = Test-RecordedApplicationRunning
if ($wasRunning) {
    # 只暂停本包记录的应用，MySQL/Redis 保持运行，保证 SQL 和上传文件来自同一业务时点。
    & (Join-Path $PSScriptRoot 'Stop.ps1') -ApplicationOnly
}

$backupRoot = Join-Path $data 'backups'
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
# 同秒连续恢复也不能共用目录；目录已存在时明确中止，绝不覆盖旧备份。
$backupName = 'stu-manage-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff') + '-' + ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$backup = Join-Path $backupRoot $backupName
if (Test-Path -LiteralPath $backup) { throw "备份目录已存在：$backup" }
$sqlFile = Join-Path $backup 'database.sql'
$backupUploads = Join-Path $backup 'uploads'
$oldPassword = $env:MYSQL_PWD
try {
    New-Item -ItemType Directory -Force -Path $backup, $backupUploads | Out-Null
    $env:MYSQL_PWD = $settings.MYSQL_ROOT_PASSWORD
    # 直接由 mysqldump 写文件，避免 PowerShell 管道把中文 SQL 转码。
    & $dump --protocol=TCP --host=127.0.0.1 --port=13306 --user=root `
        --default-character-set=utf8mb4 --single-transaction --routines --events --triggers `
        --add-drop-database "--result-file=$sqlFile" --databases stu_manage
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 备份失败。请确认 13306 正在运行。' }
    if (Test-Path -LiteralPath $uploads) {
        # LiteralPath 不会展开通配符；逐项复制才会保留空目录以外的所有媒体文件。
        Get-ChildItem -LiteralPath $uploads -Force | ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination $backupUploads -Recurse -Force
        }
    }
    # 先固定为数组；空上传目录也要写成 JSON []，恢复端才能验证其完整性。
    $uploadManifest = @(Get-UploadManifest $backupUploads)
    [pscustomobject]@{
        createdAt = (Get-Date).ToUniversalTime().ToString('o')
        databaseFile = 'database.sql'
        databaseSha256 = (Get-FileHash -LiteralPath $sqlFile -Algorithm SHA256).Hash
        uploads = $uploadManifest
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding UTF8
    Write-Host "备份完成：$backup"
} finally {
    if ($null -eq $oldPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $oldPassword }
    if ($wasRunning -and -not $NoRestart) { & (Join-Path $PSScriptRoot 'Start.ps1') }
}
