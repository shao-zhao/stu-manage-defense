[CmdletBinding()]
param([switch]$WithRedis)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root 'runtime/processes.json'

if (Test-Path $pidFile) {
    $processes = Get-Content -Raw $pidFile | ConvertFrom-Json
    foreach ($name in @('backend', 'frontend')) {
        $id = $processes.$name
        if ($id -and (Get-Process -Id $id -ErrorAction SilentlyContinue)) {
            & taskkill.exe /PID $id /T /F | Out-Null
            Write-Host "Stopped $name."
        }
    }
    Remove-Item -LiteralPath $pidFile -Force
}

if ($WithRedis) {
    docker compose -f (Join-Path $root 'compose.yaml') stop redis
    if ($LASTEXITCODE -ne 0) { throw 'Redis could not be stopped.' }
    Write-Host 'Stopped Redis; its named volume remains intact.'
}
