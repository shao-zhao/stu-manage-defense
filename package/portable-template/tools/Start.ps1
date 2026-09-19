param([switch]$InfrastructureOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$nonAscii = $root.ToCharArray() | Where-Object { [int][char]$_ -gt 127 } | Select-Object -First 1
if ($nonAscii) {
    throw 'MySQL 8.0 for Windows only supports the current ANSI code page in its installation/data path. Move this package to an English-only path, then start again. See docs/V2-PORTABLE.md.'
}
$data = Join-Path $root 'data'; $logs = Join-Path $root 'logs'
$pidFile = Join-Path $data 'processes.json'; $settingsFile = Join-Path $data 'settings.env'; $initMarker = Join-Path $data 'mysql-initialized.marker'
$mysqlPort = 13306; $redisPort = 16379; $appPort = 18090
New-Item -ItemType Directory -Force -Path $data, $logs, (Join-Path $data 'uploads'), (Join-Path $data 'tmp'), (Join-Path $data 'mysql-tmp') | Out-Null
function Test-Port([int]$Port) { $c=New-Object Net.Sockets.TcpClient; try{$c.Connect('127.0.0.1',$Port);return $true}catch{return $false}finally{$c.Close()} }
function Read-State { $s=@{}; if(Test-Path $pidFile){try{$o=Get-Content $pidFile -Raw|ConvertFrom-Json;foreach($n in @('mysql','redis','app')){if($o.$n){$s[$n]=$o.$n}}}catch{}};return $s }
function Save-State($State) { ([pscustomobject]$State|ConvertTo-Json)|Set-Content -LiteralPath $pidFile -Encoding UTF8 }
function New-Record($P) { [pscustomobject]@{id=$P.Id;startedAt=$P.StartTime.ToUniversalTime().ToString('o');path=$P.Path} }
function Is-OwnedRunning($R,[string]$Expected) { if($null -eq $R -or $null -eq $R.id){return $false};$p=Get-Process -Id ([int]$R.id) -ErrorAction SilentlyContinue;if($null -eq $p){return $false};try{return $p.StartTime.ToUniversalTime().ToString('o') -eq $R.startedAt -and [IO.Path]::GetFullPath($p.Path) -eq [IO.Path]::GetFullPath($Expected)}catch{return $false} }
function Assert-FreeOrOwned([string]$Name,[int]$Port,$State,[string]$Expected) { if((Test-Port $Port) -and -not(Is-OwnedRunning $State[$Name] $Expected)){throw "Port $Port is in use by another program. This package will not stop external processes."} }
function New-Secret { $b=New-Object byte[] 32;$r=[Security.Cryptography.RandomNumberGenerator]::Create();try{$r.GetBytes($b)}finally{$r.Dispose()};([Convert]::ToBase64String($b).Replace('+','A').Replace('/','B').Replace('=','')) }
function Read-Settings { $m=@{};if(-not(Test-Path $settingsFile)){return $null};Get-Content $settingsFile|ForEach-Object{if($_ -match '^([A-Z_]+)=(.*)$'){$m[$Matches[1]]=$Matches[2]}};return $m }
function Write-Settings($M) { @("MYSQL_ROOT_PASSWORD=$($M.MYSQL_ROOT_PASSWORD)","MYSQL_PASSWORD=$($M.MYSQL_PASSWORD)","JWT_SECRET=$($M.JWT_SECRET)")|Set-Content -LiteralPath $settingsFile -Encoding ASCII }
function Wait-MySql([string]$Admin) {
    for ($i=0; $i -lt 60; $i++) {
        # mysqladmin reports connection attempts on stderr; that is expected while booting.
        $savedPreference = $ErrorActionPreference; $ErrorActionPreference = 'SilentlyContinue'
        & $Admin --protocol=TCP --host=127.0.0.1 --port=$mysqlPort ping 2>$null | Out-Null
        $exitCode = $LASTEXITCODE; $ErrorActionPreference = $savedPreference
        if ($exitCode -eq 0) { return }
        Start-Sleep -Milliseconds 750
    }
    throw 'MySQL did not become ready. See logs\\mysql.err.'
}
function Invoke-RootSql([string]$Client,[string]$Password,[string]$Sql) { $before=$env:MYSQL_PWD;try{if($Password){$env:MYSQL_PWD=$Password}else{Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue};& $Client --protocol=TCP --host=127.0.0.1 --port=$mysqlPort --user=root --default-character-set=utf8mb4 -e $Sql;if($LASTEXITCODE -ne 0){throw 'MySQL bootstrap command failed.'}}finally{if($null -eq $before){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$before}} }
$mysqlHome=Join-Path $root 'runtime\mysql';$mysqlData=Join-Path $data 'mysql';$mysqlIni=Join-Path $data 'my.ini';$mysqlExe=Join-Path $mysqlHome 'bin\mysqld.exe';$mysqlClient=Join-Path $mysqlHome 'bin\mysql.exe';$mysqlAdmin=Join-Path $mysqlHome 'bin\mysqladmin.exe';$redisExe=Join-Path $root 'runtime\redis\redis-server.exe';$java=Join-Path $root 'runtime\java\bin\java.exe';$jar=Join-Path $root 'app\stu-manage.jar'
foreach($f in @($mysqlExe,$mysqlClient,$mysqlAdmin,$redisExe)){if(-not(Test-Path -LiteralPath $f)){throw "Package file missing: $f"}}
if(-not $InfrastructureOnly){foreach($f in @($java,$jar)){if(-not(Test-Path -LiteralPath $f)){throw "Package file missing: $f"}}}
$state=Read-State;Assert-FreeOrOwned 'mysql' $mysqlPort $state $mysqlExe;Assert-FreeOrOwned 'redis' $redisPort $state $redisExe;if(-not $InfrastructureOnly){Assert-FreeOrOwned 'app' $appPort $state $java};$env:SPRING_PROFILES_ACTIVE='demo'
# Recreate portable absolute paths on each start. UTF-8 without BOM preserves Chinese directory names after moving the package.
$slash={param($p)$p.Replace('\','/')};$ini=@"
[mysqld]
basedir="$(& $slash $mysqlHome)"
datadir="$(& $slash $mysqlData)"
port=$mysqlPort
bind-address=127.0.0.1
skip-name-resolve
skip-networking=0
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
tmpdir="$(& $slash (Join-Path $data 'mysql-tmp'))"
log-error="$(& $slash (Join-Path $logs 'mysql.err'))"
"@;[IO.File]::WriteAllText($mysqlIni,$ini,(New-Object Text.UTF8Encoding($false)))
$fresh=-not(Test-Path $initMarker);$secrets=Read-Settings;$bootstrapFile=Join-Path $data 'mysql-bootstrap.sql'
if($fresh){if((Test-Path $settingsFile) -or (Test-Path (Join-Path $mysqlData 'mysql'))){throw 'Incomplete first initialization detected. Create a fresh package copy rather than reusing this data directory.'};New-Item -ItemType Directory -Force -Path $mysqlData|Out-Null;& $mysqlExe ("--defaults-file=`"$mysqlIni`"") --initialize-insecure --console;if($LASTEXITCODE -ne 0){throw 'MySQL initialization failed. See logs\\mysql.err.'};$secrets=@{MYSQL_ROOT_PASSWORD=New-Secret;MYSQL_PASSWORD=New-Secret;JWT_SECRET=New-Secret};@("CREATE DATABASE IF NOT EXISTS stu_manage CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;","CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '$($secrets.MYSQL_ROOT_PASSWORD)';","GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;","ALTER USER 'root'@'localhost' IDENTIFIED BY '$($secrets.MYSQL_ROOT_PASSWORD)';","CREATE USER IF NOT EXISTS 'stu_app'@'127.0.0.1' IDENTIFIED BY '$($secrets.MYSQL_PASSWORD)';","CREATE USER IF NOT EXISTS 'stu_app'@'localhost' IDENTIFIED BY '$($secrets.MYSQL_PASSWORD)';","GRANT ALL PRIVILEGES ON stu_manage.* TO 'stu_app'@'127.0.0.1';","GRANT ALL PRIVILEGES ON stu_manage.* TO 'stu_app'@'localhost';","FLUSH PRIVILEGES;")|Set-Content -LiteralPath $bootstrapFile -Encoding ASCII}
if(-not(Is-OwnedRunning $state['mysql'] $mysqlExe)){$mysqlArgs="--defaults-file=`"$mysqlIni`"";if($fresh){$mysqlArgs+=" --init-file=`"$bootstrapFile`""};$p=Start-Process -FilePath $mysqlExe -ArgumentList $mysqlArgs -WindowStyle Hidden -PassThru;$state['mysql']=New-Record $p;Save-State $state};Wait-MySql $mysqlAdmin
if($fresh){Remove-Item -LiteralPath $bootstrapFile -Force;Write-Settings $secrets;Set-Content -LiteralPath $initMarker -Value (Get-Date).ToUniversalTime().ToString('o') -Encoding ASCII}elseif($null -eq $secrets){throw 'Initialized database has no local settings file; refusing to guess credentials.'}
$redisData=Join-Path $data 'redis';New-Item -ItemType Directory -Force -Path $redisData|Out-Null;if(-not(Is-OwnedRunning $state['redis'] $redisExe)){$args="--bind 127.0.0.1 --port $redisPort --appendonly yes --dir `"$redisData`" --logfile `"$(Join-Path $logs 'redis.log')`"";$p=Start-Process -FilePath $redisExe -ArgumentList $args -WindowStyle Hidden -PassThru;$state['redis']=New-Record $p;Save-State $state};for($i=0;$i -lt 30 -and -not(Test-Port $redisPort);$i++){Start-Sleep -Milliseconds 500};if(-not(Test-Port $redisPort)){throw 'Redis did not become ready. See logs\\redis.log.'}
if(-not $InfrastructureOnly){if(-not(Is-OwnedRunning $state['app'] $java)){$old=@{MYSQL_URL=$env:MYSQL_URL;MYSQL_USER=$env:MYSQL_USER;MYSQL_PASSWORD=$env:MYSQL_PASSWORD;REDIS_HOST=$env:REDIS_HOST;REDIS_PORT=$env:REDIS_PORT;JWT_SECRET=$env:JWT_SECRET;SERVER_PORT=$env:SERVER_PORT;UPLOAD_PATH=$env:UPLOAD_PATH};try{$env:MYSQL_URL="jdbc:mysql://127.0.0.1:$mysqlPort/stu_manage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";$env:MYSQL_USER='stu_app';$env:MYSQL_PASSWORD=$secrets.MYSQL_PASSWORD;$env:REDIS_HOST='127.0.0.1';$env:REDIS_PORT="$redisPort";$env:JWT_SECRET=$secrets.JWT_SECRET;$env:SERVER_PORT="$appPort";$env:UPLOAD_PATH=(Join-Path $data 'uploads');$tmp=Join-Path $data 'tmp';$args="-Djava.io.tmpdir=`"$tmp`" -Djdk.net.unixdomain.tmpdir=`"$tmp`" -jar `"$jar`"";$p=Start-Process -FilePath $java -ArgumentList $args -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs 'app.out.log') -RedirectStandardError (Join-Path $logs 'app.err.log');$state['app']=New-Record $p;Save-State $state}finally{foreach($n in $old.Keys){if($null -eq $old[$n]){Remove-Item "Env:$n" -ErrorAction SilentlyContinue}else{Set-Item "Env:$n" $old[$n]}}}};$healthy=$false;for($i=0;$i -lt 100;$i++){try{$h=(Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$appPort/actuator/health" -TimeoutSec 2).Content|ConvertFrom-Json;if($h.status -eq 'UP'){$healthy=$true;break}}catch{};Start-Sleep -Milliseconds 750};if(-not $healthy){throw 'Application health is not UP. See logs\\app.out.log and app.err.log.'};Write-Host "Ready: http://127.0.0.1:$appPort (all data remains under $data)"}else{Write-Host 'MySQL and Redis are ready; application JAR was intentionally skipped.'}
