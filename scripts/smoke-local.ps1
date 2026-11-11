# ============================================================================
# ATLAS Gym - Smoke test local contra MySQL 8 real, perfil prod.
#
# 1. Borra y recrea la base de PRUEBA (por defecto atlas_gym_smoke; se niega con cualquier
#    nombre que no termine en "_smoke", para no tocar nunca la base real o la de desarrollo).
# 2. Empaqueta el JAR (salvo -SkipBuild) y lo levanta con SPRING_PROFILES_ACTIVE=prod
#    (Flyway + ddl-auto=validate), en un puerto aparte (8089) para no chocar con el backend de dev.
# 3. Corre scripts\smoke-api.ps1 y guarda salida + log del backend en reportes\smoke\.
# 4. Apaga el backend.
#
# Uso:
#   $env:SMOKE_DB_PASSWORD = "<clave del usuario MySQL>"
#   .\scripts\smoke-local.ps1 -DbUser root
# ============================================================================
param(
    [string]$DbUser = "root",
    [string]$DbPassword = $env:SMOKE_DB_PASSWORD,
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "atlas_gym_smoke",
    [int]$Port = 8089,
    [string]$MysqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    [switch]$SkipBuild
)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot

if ($DbName -notmatch '_smoke$') { throw "Por seguridad, -DbName debe terminar en '_smoke' (recibido: '$DbName')." }
if (-not (Test-Path $MysqlExe)) { throw "No se encontro mysql.exe en '$MysqlExe'. Pasar -MysqlExe." }
if ($null -eq $DbPassword) { $DbPassword = "" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $repo "reportes\smoke"
New-Item -ItemType Directory -Force $outDir | Out-Null
$smokeLog = Join-Path $outDir "smoke-$stamp.txt"
$backLog = Join-Path $outDir "backend-$stamp.log"

Write-Host "==> Recreando base $DbName en ${DbHost}:$DbPort"
$env:MYSQL_PWD = $DbPassword
& $MysqlExe -u $DbUser -h $DbHost -P $DbPort -e "DROP DATABASE IF EXISTS ``$DbName``; CREATE DATABASE ``$DbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
$rc = $LASTEXITCODE
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
if ($rc -ne 0) { throw "No se pudo recrear la base (mysql exit $rc)." }

if (-not $SkipBuild) {
    Write-Host "==> Empaquetando JAR (sin tests)"
    Push-Location $repo
    try { & .\mvnw.cmd -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw "Fallo mvnw package." } } finally { Pop-Location }
}
$jar = Get-ChildItem (Join-Path $repo "target") -Filter "backend-*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $jar) { throw "No se encontro el JAR en target\." }

$cfg = @{
    SPRING_PROFILES_ACTIVE    = "prod"
    SERVER_PORT               = "$Port"
    SPRING_DATASOURCE_URL     = "jdbc:mysql://${DbHost}:$DbPort/${DbName}?serverTimezone=America/Argentina/Buenos_Aires"
    DB_USER                   = $DbUser
    DB_PASSWORD               = $DbPassword
    ATLAS_JWT_SECRET          = (-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) }))
    ATLAS_ADMIN_BOOTSTRAP_ENABLED = "true"
    ATLAS_ADMIN_EMAIL         = "titular1@smoke.local"
    ATLAS_ADMIN_PASSWORD      = "SmokeTitular1!"
    ATLAS_ADMIN_NOMBRE        = "Titular 1"
    ATLAS_ADMIN2_EMAIL        = "titular2@smoke.local"
    ATLAS_ADMIN2_PASSWORD     = "SmokeTitular2!"
    ATLAS_ADMIN2_NOMBRE       = "Titular 2"
    ATLAS_TOTEM_SECRET        = "secreto-tablet-smoke"
    ATLAS_CORS_ALLOWED_ORIGIN = "http://localhost:3000"
    TZ                        = "America/Argentina/Buenos_Aires"
}
$saved = @{}
foreach ($k in $cfg.Keys) { $saved[$k] = [Environment]::GetEnvironmentVariable($k); [Environment]::SetEnvironmentVariable($k, $cfg[$k]) }

$proc = $null
try {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        throw "El puerto $Port ya esta en uso (¿quedo un backend de una corrida anterior?). Liberalo o usa -Port."
    }
    Write-Host "==> Levantando $($jar.Name) en puerto $Port (log: $backLog)"
    $proc = Start-Process java -ArgumentList "-Duser.timezone=America/Argentina/Buenos_Aires", "-jar", "`"$($jar.FullName)`"" `
        -RedirectStandardOutput $backLog -RedirectStandardError "$backLog.err" -PassThru -WindowStyle Hidden

    $base = "http://127.0.0.1:$Port"
    $up = $false
    # No alcanza con que el puerto abra: Tomcat escucha ANTES de que DataInitializer (CommandLineRunner)
    # cree las cuentas de los titulares. Esperamos su ultima linea de log (prod o dev).
    $listo = 'DataInitializer: (se omiten datos de demostraci.n|atlas\.seed\.enabled=false|Verificaci.n de datos semilla completada)'
    for ($i = 0; $i -lt 120; $i++) {
        if ($proc.HasExited) { break }
        if ((Test-Path $backLog) -and (Select-String -Path $backLog -Pattern $listo -Quiet)) { $up = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $up) {
        Write-Host "El backend no levanto. Ultimas lineas del log:" -ForegroundColor Red
        Get-Content $backLog -Tail 40
        throw "Backend no disponible."
    }

    Write-Host "==> Corriendo smoke-api.ps1 contra $base"
    & (Join-Path $PSScriptRoot "smoke-api.ps1") -Base $base `
        -Admin1Email $cfg.ATLAS_ADMIN_EMAIL -Admin1Pass $cfg.ATLAS_ADMIN_PASSWORD -Admin1Nombre $cfg.ATLAS_ADMIN_NOMBRE `
        -Admin2Email $cfg.ATLAS_ADMIN2_EMAIL -Admin2Pass $cfg.ATLAS_ADMIN2_PASSWORD -TotemSecret $cfg.ATLAS_TOTEM_SECRET *>&1 |
        Tee-Object -FilePath $smokeLog
    $smokeRc = $LASTEXITCODE
    Write-Host "==> Salida guardada en $smokeLog"
    exit $smokeRc
}
finally {
    # taskkill /T: el java.exe del PATH suele ser un lanzador (javapath) que abre el JVM real como hijo.
    if ($proc) { & taskkill.exe /PID $proc.Id /T /F 2>&1 | Out-Null; Write-Host "==> Backend detenido." }
    foreach ($k in $saved.Keys) { [Environment]::SetEnvironmentVariable($k, $saved[$k]) }
}
