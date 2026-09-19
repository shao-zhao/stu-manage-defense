$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$settings = Join-Path $root 'data\settings.env'
$failed = $false
function Fail([string]$Message) { Write-Host "[DOWN] $Message"; $script:failed = $true }
function Pass([string]$Message) { Write-Host "[OK] $Message" }
function Test-Port([int]$Port) { $c=New-Object Net.Sockets.TcpClient;try{$c.Connect('127.0.0.1',$Port);$true}catch{$false}finally{$c.Close()} }
if(Test-Port 13306){Pass 'MySQL 13306 listening'}else{Fail 'MySQL 13306'}
if(Test-Port 16379){$pong=& (Join-Path $root 'runtime\redis\redis-cli.exe') -h 127.0.0.1 -p 16379 ping 2>$null;if($pong -eq 'PONG'){Pass 'Redis PONG'}else{Fail 'Redis did not PONG'}}else{Fail 'Redis 16379'}
try{$h=(Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18090/actuator/health' -TimeoutSec 3).Content|ConvertFrom-Json;if($h.status -eq 'UP'){Pass 'Application health UP'}else{Fail 'Application health'}}catch{Fail 'Application health endpoint'}
if($failed){exit 1}
