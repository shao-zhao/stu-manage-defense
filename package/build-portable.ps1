[CmdletBinding()]
param(
    [string]$OutputDirectory,
    [string]$JarPath,
    [string]$JavaHome = 'C:\Users\Administrator\.jdks\dragonwell-17.0.19',
    [string]$MySqlHome = 'C:\mysql80-codex\mysql-8.0.29-winx64',
    [string]$RedisHome,
    [string]$VcRedistPath
)

# This is a packaging script, not an installer.  It deliberately never removes
# an existing output directory so a wrong path cannot erase user files.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$template = Join-Path $PSScriptRoot 'portable-template'
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot 'out' }
if (-not $VcRedistPath) { $VcRedistPath = Join-Path $PSScriptRoot 'vendor\vc_redist.x64.exe' }
if (-not $RedisHome) { $RedisHome = Join-Path $repoRoot 'runtime\redis\Redis-8.10.2-Windows-x64-cygwin' }

function Require-Directory([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) { throw "$Label directory was not found: $Path" }
    return (Resolve-Path -LiteralPath $Path).Path
}
function Require-File([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Label file was not found: $Path" }
    return (Resolve-Path -LiteralPath $Path).Path
}

$template = Require-Directory $template 'Portable template'
$JavaHome = Require-Directory $JavaHome 'Java 17'
$MySqlHome = Require-Directory $MySqlHome 'MySQL'
$RedisHome = Require-Directory $RedisHome 'Redis'
$VcRedistPath = Require-File $VcRedistPath 'Microsoft Visual C++ redistributable'

if (-not $JarPath) {
    $candidate = Get-ChildItem (Join-Path $repoRoot 'stu-backend\target') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '\.original$' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $candidate) { throw 'No executable backend JAR found. Build the finalized backend first.' }
    $JarPath = $candidate.FullName
}
$JarPath = Require-File $JarPath 'Executable backend JAR'
$jarTool = Require-File (Join-Path $JavaHome 'bin\jar.exe') 'Java jar tool'
$entries = & $jarTool tf $JarPath
if ($LASTEXITCODE -ne 0 -or $entries -notcontains 'BOOT-INF/classes/static/index.html') {
    throw 'The JAR does not contain the built frontend at BOOT-INF/classes/static/index.html. Build the integrated JAR before packaging.'
}

$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$packageRoot = Join-Path $OutputDirectory 'stu-manage-portable'
$zipPath = Join-Path $OutputDirectory 'stu-manage-portable-windows-x64.zip'
if (Test-Path -LiteralPath $packageRoot) { throw "Refusing to overwrite existing package directory: $packageRoot" }
if (Test-Path -LiteralPath $zipPath) { throw "Refusing to overwrite existing ZIP: $zipPath" }

New-Item -ItemType Directory -Force -Path $packageRoot | Out-Null
Copy-Item -Path (Join-Path $template '*') -Destination $packageRoot -Recurse -Force
# 离线包同时携带教程，用户无需联网也可查看安装、演示和恢复说明。
Copy-Item -LiteralPath (Join-Path $repoRoot 'docs') -Destination (Join-Path $packageRoot 'docs') -Recurse -Force
New-Item -ItemType Directory -Force -Path (Join-Path $packageRoot 'app'), (Join-Path $packageRoot 'runtime') | Out-Null
Copy-Item -LiteralPath $JarPath -Destination (Join-Path $packageRoot 'app\stu-manage.jar') -Force
Copy-Item -LiteralPath $JavaHome -Destination (Join-Path $packageRoot 'runtime\java') -Recurse -Force
Copy-Item -LiteralPath $MySqlHome -Destination (Join-Path $packageRoot 'runtime\mysql') -Recurse -Force
Copy-Item -LiteralPath $RedisHome -Destination (Join-Path $packageRoot 'runtime\redis') -Recurse -Force
Copy-Item -LiteralPath $VcRedistPath -Destination (Join-Path $packageRoot 'runtime\vc_redist.x64.exe') -Force

New-Item -ItemType Directory -Force -Path (Join-Path $packageRoot 'licenses\mysql'), (Join-Path $packageRoot 'licenses\java'), (Join-Path $packageRoot 'licenses\redis') | Out-Null
Copy-Item -LiteralPath (Join-Path $MySqlHome 'LICENSE') -Destination (Join-Path $packageRoot 'licenses\mysql\LICENSE') -Force
Copy-Item -LiteralPath (Join-Path $JavaHome 'legal') -Destination (Join-Path $packageRoot 'licenses\java\legal') -Recurse -Force
Copy-Item -LiteralPath (Join-Path $RedisHome 'README.md') -Destination (Join-Path $packageRoot 'licenses\redis\README.md') -Force

$hashLines = @(
    '# Package source fingerprints (SHA-256)',
    ('Java release metadata: ' + (Get-FileHash (Join-Path $JavaHome 'release') -Algorithm SHA256).Hash),
    ('MySQL mysqld.exe: ' + (Get-FileHash (Join-Path $MySqlHome 'bin\mysqld.exe') -Algorithm SHA256).Hash),
    ('Redis redis-server.exe: ' + (Get-FileHash (Join-Path $RedisHome 'redis-server.exe') -Algorithm SHA256).Hash),
    ('VC_redist.x64.exe: ' + (Get-FileHash $VcRedistPath -Algorithm SHA256).Hash),
    ('Integrated application JAR: ' + (Get-FileHash $JarPath -Algorithm SHA256).Hash),
    'Redis upstream release: redis-windows/redis-windows 8.10.2 Windows x64 Cygwin ZIP',
    'Redis upstream ZIP SHA-256: 6de5cc7f5adbf97b5928b13766383d4ad424626ef3d8b313ffff12d820ec6fc1'
)
$hashLines | Set-Content -LiteralPath (Join-Path $packageRoot 'SOURCES-SHA256.txt') -Encoding UTF8

# 打包前确认离线教程没有在复制过程中遗漏，避免生成可启动但缺少演示说明的半成品。
$requiredDocs = @(
    'DEFENSE.md',
    'LEARNING.md',
    'REDIS-RUNTIME.md',
    'SCORING-EVIDENCE.md',
    'TECH-DEMO.md',
    'TESTING.md',
    'V2-PORTABLE.md'
)
foreach ($document in $requiredDocs) {
    $documentPath = Join-Path $packageRoot (Join-Path 'docs' $document)
    if (-not (Test-Path -LiteralPath $documentPath -PathType Leaf)) {
        throw "Required offline document was not copied: $documentPath"
    }
}

Compress-Archive -LiteralPath $packageRoot -DestinationPath $zipPath -CompressionLevel Optimal
Write-Host "Created: $zipPath"
Write-Host ('ZIP SHA-256: ' + (Get-FileHash $zipPath -Algorithm SHA256).Hash)
