param(
    [Parameter(Mandatory = $true)]
    [Alias('BackupFile')]
    [string]$BackupPath,
    [switch]$Confirm,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$data = Join-Path $root 'data'
$settingsFile = Join-Path $data 'settings.env'
$processFile = Join-Path $data 'processes.json'
$uploads = [IO.Path]::GetFullPath((Join-Path $data 'uploads'))
$mysql = Join-Path $root 'runtime\mysql\bin\mysql.exe'
$redisCli = Join-Path $root 'runtime\redis\redis-cli.exe'
$java = Join-Path $root 'runtime\java\bin\java.exe'

function Read-Settings {
    if (-not (Test-Path -LiteralPath $settingsFile)) { throw '未找到 data\settings.env，不能恢复。' }
    $values = @{}
    Get-Content -LiteralPath $settingsFile | ForEach-Object {
        if ($_ -match '^([A-Z_]+)=(.*)$') { $values[$Matches[1]] = $Matches[2] }
    }
    if ([string]::IsNullOrWhiteSpace($values.MYSQL_ROOT_PASSWORD)) { throw 'MySQL 管理凭据缺失。' }
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

function Assert-ManagedUploadsPath {
    $dataPath = [IO.Path]::GetFullPath($data)
    if (-not $uploads.StartsWith($dataPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw '上传目录不在本便携包 data 下，已拒绝删除。'
    }
}

function Assert-BackupIntegrity([string]$Directory) {
    $manifestFile = Join-Path $Directory 'manifest.json'
    if (-not (Test-Path -LiteralPath $manifestFile -PathType Leaf)) { throw '备份目录中缺少 manifest.json。' }
    $manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($manifest.databaseSha256)) { throw '备份清单缺少数据库校验值。' }
    $actualSqlHash = (Get-FileHash -LiteralPath (Join-Path $Directory 'database.sql') -Algorithm SHA256).Hash
    if ($actualSqlHash -ne $manifest.databaseSha256) { throw '备份数据库校验失败，已拒绝恢复。' }

    $backupUploadsDirectory = Join-Path $Directory 'uploads'
    $expected = @($manifest.uploads)
    $actual = @()
    if (Test-Path -LiteralPath $backupUploadsDirectory) {
        $actual = @(Get-ChildItem -LiteralPath $backupUploadsDirectory -File -Recurse | ForEach-Object {
            [pscustomobject]@{
                path = $_.FullName.Substring($backupUploadsDirectory.Length).TrimStart('\\')
                sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
                length = $_.Length
            }
        })
    }
    if ($actual.Count -ne $expected.Count) { throw '备份媒体文件数量与清单不一致，已拒绝恢复。' }
    foreach ($item in $expected) {
        $match = @($actual | Where-Object { $_.path -eq $item.path })
        if ($match.Count -ne 1 -or $match[0].sha256 -ne $item.sha256 -or [int64]$match[0].length -ne [int64]$item.length) {
            throw "备份媒体校验失败：$($item.path)"
        }
    }
}

function Invoke-MySqlRestore([string]$SqlFile, [string]$Password) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $mysql
    $info.Arguments = '--protocol=TCP --host=127.0.0.1 --port=13306 --user=root --default-character-set=utf8mb4'
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardError = $true
    $info.EnvironmentVariables['MYSQL_PWD'] = $Password
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    [void]$process.Start()
    # 先异步持续读取 stderr，避免 MySQL 大量错误输出塞满管道造成等待死锁。
    $stderrTask = $process.StandardError.ReadToEndAsync()
    # Windows PowerShell 5.1 的 ProcessStartInfo 没有 StandardInputEncoding。
    # 直接复制 UTF-8 原始字节，配合 mysql 的 utf8mb4 客户端编码，避免中文经控制台代码页损坏。
    $input = [IO.File]::OpenRead($SqlFile)
    try {
        $input.CopyTo($process.StandardInput.BaseStream)
        $process.StandardInput.BaseStream.Close()
        $process.WaitForExit()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "MySQL 恢复失败：$stderr" }
    } finally {
        $input.Dispose()
        $process.Dispose()
    }
}

$backup = [IO.Path]::GetFullPath($BackupPath)
$sqlFile = Join-Path $backup 'database.sql'
$backupUploads = Join-Path $backup 'uploads'
if (-not (Test-Path -LiteralPath $sqlFile -PathType Leaf)) { throw '备份目录中缺少 database.sql。' }
if (-not (Test-Path -LiteralPath $mysql)) { throw "找不到 mysql 客户端：$mysql" }
Assert-BackupIntegrity $backup
if (-not $Force -and -not $Confirm) {
    $answer = Read-Host '恢复会覆盖当前业务数据库和上传文件。请输入 RESTORE 确认'
    if ($answer -ne 'RESTORE') { throw '未确认，恢复已取消。' }
}

$settings = Read-Settings
$wasRunning = Test-RecordedApplicationRunning
Assert-ManagedUploadsPath
# 自动备份当前状态，并保持应用停止；数据库和 Redis 不会被停止。
& (Join-Path $PSScriptRoot 'Backup.ps1') -NoRestart
try {
    Invoke-MySqlRestore $sqlFile $settings.MYSQL_ROOT_PASSWORD
    New-Item -ItemType Directory -Force -Path $uploads | Out-Null
    # 目标已验证在本包 data\uploads 下，才允许递归清空后恢复媒体。
    Get-ChildItem -LiteralPath $uploads -Force | Remove-Item -Recurse -Force
    if (Test-Path -LiteralPath $backupUploads) {
        Get-ChildItem -LiteralPath $backupUploads -Force | ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination $uploads -Recurse -Force
        }
    }
    if (Test-Path -LiteralPath $redisCli) {
        # 只清理本项目命名空间，不影响同机其它 Redis 使用者。
        & $redisCli -h 127.0.0.1 -p 16379 --scan --pattern 'stu_manage:*' |
            ForEach-Object { if ($_.Trim()) { & $redisCli -h 127.0.0.1 -p 16379 DEL $_.Trim() | Out-Null } }
    }
    Write-Host "恢复完成：$backup"
} finally {
    if ($wasRunning) { & (Join-Path $PSScriptRoot 'Start.ps1') }
}
