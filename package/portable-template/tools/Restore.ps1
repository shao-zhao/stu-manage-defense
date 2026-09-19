param([Parameter(Mandatory=$true)][string]$BackupFile)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$settings = Join-Path $root 'data\settings.env'
if (-not (Test-Path $BackupFile -PathType Leaf)) { throw "Backup file not found: $BackupFile" }
if (-not (Test-Path $settings)) { throw 'No initialized package data found.' }
$values = @{}; Get-Content $settings | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { $values[$Matches[1]]=$Matches[2] } }
$old=$env:MYSQL_PWD; $env:MYSQL_PWD=$values.MYSQL_ROOT_PASSWORD
try { Get-Content -LiteralPath $BackupFile -Raw | & (Join-Path $root 'runtime\mysql\bin\mysql.exe') --protocol=TCP --host=127.0.0.1 --port=13306 --user=root --default-character-set=utf8mb4; if ($LASTEXITCODE -ne 0) { throw 'Restore failed.' } } finally { if ($null -eq $old) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD=$old } }
Write-Host 'Restore completed. Restart the application if it is running.'
