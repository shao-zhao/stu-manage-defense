[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root 'runtime'
$zip = Join-Path $runtime 'Redis-8.10.2-Windows-x64-cygwin.zip'
$target = Join-Path $runtime 'redis'
$url = 'https://github.com/redis-windows/redis-windows/releases/download/8.10.2/Redis-8.10.2-Windows-x64-cygwin.zip'
$expected = '6de5cc7f5adbf97b5928b13766383d4ad424626ef3d8b313ffff12d820ec6fc1'

New-Item -ItemType Directory -Force -Path $runtime | Out-Null
Invoke-WebRequest -Uri $url -OutFile $zip
$actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw "Redis archive SHA-256 mismatch: $actual" }
Expand-Archive -LiteralPath $zip -DestinationPath $target -Force
if (-not (Test-Path (Join-Path $target 'Redis-8.10.2-Windows-x64-cygwin/redis-server.exe'))) {
    throw 'Redis archive did not contain redis-server.exe at the expected path.'
}
Write-Host 'Portable Redis 8.10.2 installed and SHA-256 verified.'
