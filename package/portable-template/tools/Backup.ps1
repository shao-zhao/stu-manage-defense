$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$settings = Join-Path $root 'data\settings.env'
if (-not (Test-Path $settings)) { throw 'No initialized package data found. Start the package once first.' }
$values = @{}; Get-Content $settings | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.*)$') { $values[$Matches[1]]=$Matches[2] } }
$backupDir = Join-Path $root 'data\backups'; New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$file = Join-Path $backupDir ('stu_manage-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
$old = $env:MYSQL_PWD; $env:MYSQL_PWD = $values.MYSQL_ROOT_PASSWORD
try { & (Join-Path $root 'runtime\mysql\bin\mysqldump.exe') --protocol=TCP --host=127.0.0.1 --port=13306 --user=root --databases stu_manage --single-transaction --routines --events | Set-Content -LiteralPath $file -Encoding UTF8; if ($LASTEXITCODE -ne 0) { throw 'Backup failed.' } } finally { if ($null -eq $old) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD=$old } }
Write-Host "Backup created: $file"
