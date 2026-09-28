<#
  run-local.ps1  -  start the whole ShopFlow backend WITHOUT Docker.

  Prereqs (one-time, see docs/RUN-WITHOUT-DOCKER.md):
    * PostgreSQL 16 installed  (psql on disk)
    * Redis listening on localhost:6379  (Memurai on Windows)
    * A JDK 17-21 on disk  (this repo targets 17; JDK 24 is NOT supported by Spring Boot 3.4)
    * The service jars already built:  services\*\target\*-0.0.1-SNAPSHOT.jar

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
    powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1 -SkipDbSetup
    powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1 -WithKafka        # only if a broker is on localhost:29092

  Stop everything again:
    powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
#>
[CmdletBinding()]
param(
  [string]$PgBin           = "C:\Program Files\PostgreSQL\16\bin",
  [string]$PgSuperUser     = "postgres",
  [string]$PgSuperPassword = "",
  [string]$JavaHome        = "C:\Program Files\Java\jdk-21",
  [int]$Xmx                = 384,
  [switch]$Minimal,          # only user + product + inventory + gateway (enough to browse & add products)
  [string]$Only = "",        # explicit comma-separated subset, e.g. "user-service,product-service"
  [switch]$SkipDbSetup,
  [switch]$WithKafka
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root "logs"
New-Item -ItemType Directory -Force -Path $logs | Out-Null
Get-ChildItem $logs -Filter *.log -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

function Test-Port([string]$h,[int]$p){
  try { $c = New-Object Net.Sockets.TcpClient; $c.Connect($h,$p); $c.Close(); return $true } catch { return $false }
}

# ---------- Java ----------
$java = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path $java)) { throw "java.exe not found at $java  (pass -JavaHome 'C:\path\to\jdk-17')" }
Write-Host "== Java ==" -ForegroundColor Cyan
& $java -version
$env:JAVA_HOME = $JavaHome

# ---------- Redis ----------
Write-Host "`n== Redis ==" -ForegroundColor Cyan
if (-not (Test-Port "localhost" 6379)) {
  throw "Nothing is listening on localhost:6379. Install Memurai (https://www.memurai.com/get-memurai) and make sure the service is running."
}
Write-Host "redis reachable on localhost:6379"

# ---------- Postgres databases ----------
$dbs = @("shopflow_user","shopflow_product","shopflow_order","shopflow_inventory","shopflow_payment","shopflow_notification")
if (-not $SkipDbSetup) {
  Write-Host "`n== PostgreSQL ==" -ForegroundColor Cyan
  $psql = Join-Path $PgBin "psql.exe"
  if (-not (Test-Path $psql)) { throw "psql.exe not found at $psql  (pass -PgBin 'C:\Program Files\PostgreSQL\<ver>\bin')" }
  if (-not (Test-Port "localhost" 5432)) { throw "Nothing is listening on localhost:5432 - is the PostgreSQL service running?" }

  if ([string]::IsNullOrEmpty($PgSuperPassword)) {
    $sec = Read-Host "Password for Postgres user '$PgSuperUser'" -AsSecureString
    $PgSuperPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
      [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
  }
  $env:PGPASSWORD = $PgSuperPassword

  # sanity: can we connect?
  & $psql -U $PgSuperUser -h localhost -d postgres -tAc "SELECT version()" | Out-Null
  if ($LASTEXITCODE -ne 0) { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue; throw "Could not connect to Postgres as '$PgSuperUser'." }

  $roleExists = ((& $psql -U $PgSuperUser -h localhost -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='shopflow'") | Out-String).Trim()
  if ($roleExists -ne "1") {
    & $psql -U $PgSuperUser -h localhost -d postgres -c "CREATE ROLE shopflow LOGIN PASSWORD 'shopflow' CREATEDB"
    Write-Host "created role 'shopflow'"
  } else { Write-Host "role 'shopflow' already exists" }

  foreach ($db in $dbs) {
    $exists = ((& $psql -U $PgSuperUser -h localhost -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$db'") | Out-String).Trim()
    if ($exists -ne "1") {
      & $psql -U $PgSuperUser -h localhost -d postgres -c "CREATE DATABASE $db OWNER shopflow"
      Write-Host "created database $db"
    } else { Write-Host "database $db already exists" }
  }
  Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
} else {
  Write-Host "`n== PostgreSQL == (skipped - assuming the 6 shopflow_* databases already exist)" -ForegroundColor Cyan
}

# ---------- shared environment ----------
$env:POSTGRES_HOST     = "localhost"
$env:POSTGRES_PORT     = "5432"
$env:POSTGRES_USER     = "shopflow"
$env:POSTGRES_PASSWORD = "shopflow"
$env:REDIS_HOST        = "localhost"
$env:REDIS_PORT        = "6379"
$env:JWT_SECRET        = "dev-only-secret-change-me-please-0123456789abcdef"
if ($WithKafka) {
  if (-not (Test-Port "localhost" 29092)) { throw "-WithKafka given but nothing is on localhost:29092" }
  $env:SPRING_KAFKA_BOOTSTRAP_SERVERS = "localhost:29092"
  Write-Host "`nKafka mode ON (localhost:29092) - the order saga will run end to end"
} else {
  Remove-Item Env:\SPRING_KAFKA_BOOTSTRAP_SERVERS -ErrorAction SilentlyContinue
  Write-Host "`nKafka mode OFF - services boot fine; orders will sit at INVENTORY_PENDING (no broker to drive the saga)"
}

# ---------- launch services ----------
# order matters: schema owners first, gateway last
$svc = [ordered]@{
  "user-service"         = 8081
  "product-service"      = 8082
  "inventory-service"    = 8085
  "cart-service"         = 8083
  "order-service"        = 8084
  "payment-service"      = 8086
  "notification-service" = 8087
  "api-gateway"          = 8080
}

$names = @($svc.Keys)
if ($Only)     { $names = $Only.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ } }
elseif ($Minimal) { $names = @("user-service","product-service","inventory-service","api-gateway") }

Write-Host "`n== Starting services ($($names.Count)) ==" -ForegroundColor Cyan
$procs = @()
foreach ($name in $names) {
  if (-not $svc.Contains($name)) { throw "unknown service '$name'" }
  $port = $svc[$name]
  $jar  = Join-Path $root "services\$name\target\$name-0.0.1-SNAPSHOT.jar"
  if (-not (Test-Path $jar)) {
    throw "missing $jar`nBuild the jars first (see docs/RUN-WITHOUT-DOCKER.md), then re-run."
  }

  # Everything the service needs is passed as -D system properties. DB name is hard-coded
  # per service; host/user/pass default to localhost / shopflow / shopflow (what run-local
  # created), so no environment block is required.
  $log = Join-Path $logs "$name.log"
  $err = Join-Path $logs "$name.err.log"
  $jargs = @("-Xmx${Xmx}m", "-Dserver.port=$port")
  if ($name -eq "user-service") { $jargs += "-Dshopflow.security.bootstrap-admin-emails=admin@shopflow.local" }
  if ($name -eq "api-gateway")  { $jargs += "-DCORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:5174,http://localhost:5175" }
  if ($WithKafka)               { $jargs += "-Dspring.kafka.bootstrap-servers=localhost:29092" }
  $jargs += @("-jar", $jar)

  # Launch java.exe DIRECTLY - no cmd wrapper. A cmd -> java -> file-redirect chain looks
  # like fileless malware to AVG's Behaviour Shield and gets killed ~2s in; plain java.exe
  # does not. Hidden window gives it its own console, so it survives this script exiting.
  $p = Start-Process -FilePath $java -ArgumentList $jargs -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $log -RedirectStandardError $err
  $procs += [pscustomobject]@{ Name = $name; Port = $port; ProcId = $p.Id }
  Write-Host ("  {0,-22} :{1}  pid {2}  (log: logs\{0}.log)" -f $name, $port, $p.Id)
  Start-Sleep -Seconds 10     # stagger: don't start N JVMs at the same instant on a slow box
}

($procs | ForEach-Object { $_.ProcId }) -join " " | Out-File (Join-Path $root ".local-pids") -Encoding ascii

# ---------- health ----------
Write-Host "`n== Health (each service boots slowly on a busy machine - be patient) ==" -ForegroundColor Cyan
foreach ($s in $procs) {
  $url = "http://localhost:$($s.Port)/actuator/health"
  $ok = $false
  for ($i = 0; $i -lt 80 -and -not $ok; $i++) {
    try { if ((Invoke-RestMethod -Uri $url -TimeoutSec 3).status -eq "UP") { $ok = $true } } catch { }
    if (-not $ok) { Start-Sleep -Seconds 3 }
  }
  $state = if ($ok) { "UP" } else { "not up yet - check its window / logs\$($s.Name).log" }
  Write-Host ("  {0,-22} {1}" -f $s.Name, $state)
}

Write-Host "`nEach service is running in its own console window - you can close THIS launcher." -ForegroundColor Green
Write-Host "Gateway:    http://localhost:8080/api/products"
Write-Host "Logs:       $logs"
Write-Host "Storefront: cd frontend ; npm install ; npm run dev     (http://localhost:5173)"
Write-Host "Stop all:   powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1"
