<#
.SYNOPSIS
  One-click local stack for lcu-delivery on Windows:
  MySQL(3307) + Redis(6380) + API(8082) + admin-web(5176) + H5(5175).

.DESCRIPTION
  - Recreates the %TEMP% junctions that hide the CJK project path from MySQL/Redis.
  - Starts only what is missing (idempotent), waits for health, prints a summary.
  - Services are launched through WMI (Win32_Process.Create) so they are detached
    from the calling shell/job and keep running after this script exits.
  - Actions: start (default) / stop / restart / status.
  - Toolchain: D:\develop\waimai-env by default, override with WLB_ENV_ROOT.

.EXAMPLE
  pwsh -File ops\dev-windows.ps1 status
  pwsh -File ops\dev-windows.ps1 restart
  pwsh -File ops\dev-windows.ps1 start -Build
#>
param(
  [ValidateSet('start', 'stop', 'restart', 'status')]
  [string]$Action = 'start',
  [switch]$Build,
  [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'

# ---------- paths ----------
$RepoRoot  = Split-Path -Parent $PSScriptRoot
$Workspace = Split-Path -Parent $RepoRoot
$EnvRoot   = if ($env:WLB_ENV_ROOT) { $env:WLB_ENV_ROOT } else { 'D:\develop\waimai-env' }
$Local     = Join-Path $Workspace '.local'
$LogDir    = Join-Path $Local 'logs'
$RunDir    = Join-Path $Local 'run'

$Link      = Join-Path $env:TEMP 'lcu-link'   # -> $Local (ASCII path for MySQL/Redis)
$ProjLink  = Join-Path $env:TEMP 'lcu-proj'   # -> $RepoRoot (ASCII path for java/node)

$MysqlHome = Join-Path $EnvRoot 'mysql'
$RedisHome = Join-Path $EnvRoot 'redis'
$NodeHome  = Join-Path $EnvRoot 'node22'
$JdkHome   = Join-Path $EnvRoot 'jdk'
$MavenCmd  = Join-Path $EnvRoot 'maven\bin\mvn.cmd'

# ---------- ports / accounts ----------
$MysqlPort = 3307
$RedisPort = 6380
$ApiPort   = 8082
$AdminPort = 5176
$H5Port    = 5175
$DbName    = 'lcu_delivery'
$DbUser    = 'lcu'
$DbPass    = 'lcu123456'

# ---------- helpers ----------
function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  OK  $msg" -ForegroundColor Green }
function Write-Skip($msg) { Write-Host "  ..  $msg" -ForegroundColor DarkGray }
function Write-Warn2($msg){ Write-Host "  !!  $msg" -ForegroundColor Yellow }

function Test-Port([int]$Port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $iar = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
    if (-not $iar.AsyncWaitHandle.WaitOne(800, $false)) { return $false }
    $client.EndConnect($iar)
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Wait-Port([int]$Port, [int]$TimeoutSec = 60) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    if (Test-Port $Port) { return $true }
    Start-Sleep -Milliseconds 500
  }
  return $false
}

function Wait-Http([string]$Url, [int]$TimeoutSec = 90) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
      if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) { return $true }
    } catch {
      # not ready yet
    }
    Start-Sleep -Milliseconds 800
  }
  return $false
}

function Ensure-Junction([string]$Path, [string]$Target) {
  if (Test-Path -LiteralPath $Path) {
    $item = Get-Item -LiteralPath $Path -Force
    $current = ''
    if ($item.Target) { $current = (@($item.Target) -join '') }
    if ($item.LinkType -eq 'Junction' -and $current -eq $Target) { return }
    cmd /c rmdir "$Path" | Out-Null
  }
  New-Item -ItemType Junction -Path $Path -Target $Target | Out-Null
}

# Write an ASCII .cmd launcher (one command per line) and start it detached
# from the caller's process tree via WMI.
function Start-Launcher([string]$Name, [string]$WorkDir, [string[]]$BodyLines) {
  $launcher = Join-Path $RunDir "$Name.cmd"
  Set-Content -Path $launcher -Value $BodyLines -Encoding ascii

  $cmdLine = 'cmd.exe /c ""' + $launcher + '""'
  $res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
    CommandLine      = $cmdLine
    CurrentDirectory = $WorkDir
  }
  if ($res.ReturnValue -ne 0) {
    throw "failed to launch $Name (Win32_Process.Create returned $($res.ReturnValue))"
  }
  Set-Content -Path (Join-Path $RunDir "$Name.pid") -Value $res.ProcessId -Encoding ascii
  return $res.ProcessId
}

function Stop-Port([int]$Port) {
  $listeners = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
  foreach ($l in $listeners) {
    Stop-Process -Id $l.OwningProcess -Force -ErrorAction SilentlyContinue
  }
}

function Get-ServiceRow([string]$Name, [int]$Port, [string]$HealthUrl) {
  $up = Test-Port $Port
  $health = ''
  if ($up -and $HealthUrl) {
    try {
      $r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3
      $health = "HTTP $($r.StatusCode)"
    } catch {
      $health = 'no response'
    }
  } elseif ($up) {
    $health = 'listening'
  } else {
    $health = 'DOWN'
  }
  [pscustomobject]@{ Service = $Name; Port = $Port; Status = $health }
}

# ---------- actions ----------
function Invoke-Stop {
  Write-Step 'Stopping local stack'
  Stop-Port $AdminPort
  Stop-Port $H5Port
  Stop-Port $ApiPort
  Stop-Port $RedisPort
  Stop-Port $MysqlPort
  Start-Sleep -Seconds 2
  if (Test-Path $RunDir) { Remove-Item (Join-Path $RunDir '*.pid') -Force -ErrorAction SilentlyContinue }
  Write-Ok 'stopped (frontends, api, redis, mysql)'
}

function Invoke-Status {
  Write-Step 'Status'
  @(
    Get-ServiceRow 'MySQL' $MysqlPort ''
    Get-ServiceRow 'Redis' $RedisPort ''
    Get-ServiceRow 'API'   $ApiPort   "http://127.0.0.1:$ApiPort/actuator/health"
    Get-ServiceRow 'admin' $AdminPort "http://127.0.0.1:$AdminPort/"
    Get-ServiceRow 'H5'    $H5Port    "http://127.0.0.1:$H5Port/"
  ) | Format-Table -AutoSize | Out-String | Write-Host
}

function Invoke-Start {
  foreach ($d in @($Local, $LogDir, $RunDir, (Join-Path $Local 'mysql-tmp'), (Join-Path $Local 'redis'))) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
  }

  Write-Step 'Junctions'
  Ensure-Junction $Link $Local
  Ensure-Junction $ProjLink $RepoRoot
  Write-Ok "$Link -> $Local"
  Write-Ok "$ProjLink -> $RepoRoot"

  Push-Location $RepoRoot
  git config --local http.sslBackend openssl 2>$null | Out-Null
  Pop-Location

  if ($Build) {
    Write-Step 'Build backend (mvn package)'
    $oldJava = $env:JAVA_HOME
    $env:JAVA_HOME = $JdkHome
    Push-Location (Join-Path $RepoRoot 'server')
    & $MavenCmd -B -q -DskipTests "-Dmaven.repo.local=$(Join-Path $Local 'm2repo')" package
    $code = $LASTEXITCODE
    Pop-Location
    $env:JAVA_HOME = $oldJava
    if ($code -ne 0) { throw "maven build failed (exit $code)" }
    Write-Ok 'backend built'
  }

  # ---- MySQL ----
  Write-Step "MySQL :$MysqlPort"
  if (Test-Port $MysqlPort) {
    Write-Skip 'already listening'
  } else {
    $dataDir = Join-Path $Link 'mysql-data'
    if (-not (Test-Path (Join-Path $dataDir 'mysql'))) {
      throw "MySQL data dir not initialized: $dataDir (run mysqld --initialize-insecure once)"
    }
    $mysqld = Join-Path $MysqlHome 'bin\mysqld.exe'
    $mysqlFlags = '--no-defaults --basedir="' + $MysqlHome + '" --datadir="' + $dataDir + '" ' +
                  '--port=' + $MysqlPort + ' --console --tmpdir="' + (Join-Path $Link 'mysql-tmp') + '" ' +
                  '--log-error="' + (Join-Path $Link 'logs\mysql.err') + '" --pid-file="' +
                  (Join-Path $Link 'logs\mysql.pid') + '" --skip-log-bin --bind-address=127.0.0.1 ' +
                  '--character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --default-time-zone=+08:00'
    $mysqlLine = '"' + $mysqld + '" ' + $mysqlFlags + ' >> "' + (Join-Path $Link 'logs\mysql.out.log') + '" 2>&1'
    Start-Launcher 'mysql' $Link @('@echo off', $mysqlLine) | Out-Null
    if (-not (Wait-Port $MysqlPort 60)) { throw "MySQL did not start, see $Link\logs\mysql.out.log" }
    Write-Ok 'started'
  }

  # ---- Redis ----
  Write-Step "Redis :$RedisPort"
  if (Test-Port $RedisPort) {
    Write-Skip 'already listening'
  } else {
    $conf = Join-Path $Link 'redis\redis-6380.conf'
    if (-not (Test-Path $conf)) {
      $confLines = @(
        "port $RedisPort",
        'bind 127.0.0.1',
        'protected-mode yes',
        ('dir ' + ($Link -replace '\\', '/') + '/redis'),
        'save ""',
        'appendonly no',
        'logfile ""',
        'databases 16'
      )
      Set-Content -Path $conf -Value $confLines -Encoding ascii
    }
    $redis = Join-Path $RedisHome 'redis-server.exe'
    $redisLine = '"' + $redis + '" "' + $conf + '" >> "' + (Join-Path $Link 'logs\redis.out.log') + '" 2>&1'
    Start-Launcher 'redis' $Link @('@echo off', $redisLine) | Out-Null
    if (-not (Wait-Port $RedisPort 30)) { throw "Redis did not start, see $Link\logs\redis.out.log" }
    Write-Ok 'started'
  }

  # ---- API ----
  Write-Step "API :$ApiPort"
  if (Test-Port $ApiPort) {
    Write-Skip 'already listening'
  } else {
    $classes = Join-Path $ProjLink 'server\target\classes'
    $appDeps = Join-Path $Link 'app-deps\lib'
    $jar     = Join-Path $ProjLink 'server\target\delivery-0.1.0.jar'
    $java    = Join-Path $JdkHome 'bin\java.exe'
    $outLog  = Join-Path $Link 'logs\api.out.log'

    $dbUrl = "jdbc:mysql://127.0.0.1:$MysqlPort/$DbName" +
             '?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'

    $apiLines = @(
      '@echo off',
      ('set "DB_URL=' + $dbUrl + '"'),
      ('set "DB_USERNAME=' + $DbUser + '"'),
      ('set "DB_PASSWORD=' + $DbPass + '"'),
      'set "REDIS_HOST=127.0.0.1"',
      ('set "REDIS_PORT=' + $RedisPort + '"'),
      'set "REDIS_PASSWORD="',
      'set "ADMIN_INIT_PASSWORD=Admin@123456"',
      'set "APP_MOCK_LOGIN=true"',
      'set "APP_MOCK_PAY=true"',
      ('set "APP_UPLOAD_DIR=' + (Join-Path $ProjLink 'server\uploads') + '"')
    )

    if ((Test-Path $classes) -and (Test-Path $appDeps)) {
      $cp = $classes + ';' + $appDeps + '\*'
      $runLine = '"' + $java + '" -cp "' + $cp + '" com.lcu.delivery.DeliveryApplication --server.port=' +
                 $ApiPort + ' >> "' + $outLog + '" 2>&1'
      $workDir = Join-Path $ProjLink 'server'
      Write-Skip 'mode: target\classes + .local\app-deps'
    } elseif (Test-Path $jar) {
      $runLine = '"' + $java + '" -jar "' + $jar + '" --server.port=' + $ApiPort +
                 ' >> "' + $outLog + '" 2>&1'
      $workDir = Join-Path $ProjLink 'server'
      Write-Skip 'mode: fat jar'
    } else {
      throw 'no backend artifact found: build first (ops\dev-windows.ps1 start -Build)'
    }

    $apiLines = $apiLines + $runLine
    Start-Launcher 'api' $workDir $apiLines | Out-Null

    if (-not (Wait-Http "http://127.0.0.1:$ApiPort/actuator/health" 150)) {
      throw "API did not become healthy, see $outLog"
    }
    Write-Ok 'healthy'
  }

  # ---- frontends ----
  if (-not $SkipFrontend) {
    $nodeExe = Join-Path $NodeHome 'node.exe'
    $serveJs = Join-Path $ProjLink 'ops\serve-static.mjs'
    $apiBase = "http://127.0.0.1:$ApiPort"

    $adminDist = Join-Path $ProjLink 'apps\admin-web\dist'
    Write-Step "admin-web :$AdminPort"
    if (Test-Port $AdminPort) {
      Write-Skip 'already listening'
    } elseif (-not (Test-Path (Join-Path $adminDist 'index.html'))) {
      Write-Warn2 "skip: $adminDist not built"
    } else {
      $line = '"' + $nodeExe + '" "' + $serveJs + '" ' + $AdminPort + ' "' + $adminDist + '" ' + $apiBase +
              ' >> "' + (Join-Path $Link 'logs\admin.out.log') + '" 2>&1'
      Start-Launcher 'admin' $ProjLink @('@echo off', $line) | Out-Null
      if (-not (Wait-Http "http://127.0.0.1:$AdminPort/" 30)) { throw 'admin-web did not start' }
      Write-Ok 'serving'
    }

    $h5Dist = Join-Path $ProjLink 'apps\lcu-mobile\dist\build\h5'
    Write-Step "H5 :$H5Port"
    if (Test-Port $H5Port) {
      Write-Skip 'already listening'
    } elseif (-not (Test-Path (Join-Path $h5Dist 'index.html'))) {
      Write-Warn2 "skip: $h5Dist not built"
    } else {
      $line = '"' + $nodeExe + '" "' + $serveJs + '" ' + $H5Port + ' "' + $h5Dist + '" ' + $apiBase +
              ' >> "' + (Join-Path $Link 'logs\h5.out.log') + '" 2>&1'
      Start-Launcher 'h5' $ProjLink @('@echo off', $line) | Out-Null
      if (-not (Wait-Http "http://127.0.0.1:$H5Port/" 30)) { throw 'H5 did not start' }
      Write-Ok 'serving'
    }
  }

  Write-Host ''
  Invoke-Status
  Write-Host ('  admin: http://127.0.0.1:{0}   (admin / Admin@123456)' -f $AdminPort) -ForegroundColor DarkGray
  Write-Host ('  H5   : http://127.0.0.1:{0}' -f $H5Port) -ForegroundColor DarkGray
  Write-Host ('  logs : {0}' -f $LogDir) -ForegroundColor DarkGray
}

switch ($Action) {
  'start'   { Invoke-Start }
  'stop'    { Invoke-Stop }
  'restart' { Invoke-Stop; Start-Sleep -Seconds 1; Invoke-Start }
  'status'  { Invoke-Status }
}
