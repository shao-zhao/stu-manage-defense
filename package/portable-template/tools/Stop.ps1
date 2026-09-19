$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root 'data\processes.json'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No package-owned processes are recorded.'; exit 0 }
$processes = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
foreach ($name in @('app','redis','mysql')) {
    $record = $processes.$name
    if ($null -eq $record -or $null -eq $record.id) { continue }
    $process = Get-Process -Id ([int]$record.id) -ErrorAction SilentlyContinue
    $owned = $false
    if ($process) {
        try {
            # PID reuse is possible after a restart. Require both start time and executable path.
            $owned = $process.StartTime.ToUniversalTime().ToString('o') -eq $record.startedAt -and
                [IO.Path]::GetFullPath($process.Path) -eq [IO.Path]::GetFullPath($record.path)
        } catch { $owned = $false }
    }
    if ($owned) { Stop-Process -Id $process.Id -Force; Write-Host "Stopped package-owned $name." }
    elseif ($process) { Write-Warning "Not stopping $name: PID identity no longer matches this package." }
}
Remove-Item -LiteralPath $pidFile -Force
