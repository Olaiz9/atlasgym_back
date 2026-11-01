# ============================================================================
# ATLAS Gym - Restauracion Segura de Base de Datos MySQL (PowerShell)
# Uso:
#   .\scripts\restore-mysql.ps1 -Path backups\atlas_gym_20261031_033000.sql.gz [-Force]
#
# Seguridad:
#   1. Valida existencia e integridad del archivo (.gz o .sql).
#   2. Exige confirmacion interactiva expresa (salvo que se pase -Force).
#   3. Genera un backup de seguridad automatico previo a la restauracion
#      (pre_restore_YYYYMMDD_HHMMSS.sql.gz) para evitar perdidas irreversibles.
# ============================================================================

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Path,
    [switch]$Force,
    [string]$ContainerName = "atlas-mysql"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

if (-not (Test-Path $Path)) {
    throw "El archivo de backup '$Path' no existe."
}

$RestoreFile = (Get-Item $Path).FullName
$FileSize = (Get-Item $RestoreFile).Length
if ($FileSize -lt 100) {
    throw "El archivo de backup '$RestoreFile' esta vacio o incompleto ($FileSize bytes)."
}

# Cargar variables desde .env si existe
$EnvFile = Join-Path $ProjectDir ".env"
$EnvVars = @{}
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $EnvVars[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
}

$DbName = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } elseif ($EnvVars["MYSQL_DATABASE"]) { $EnvVars["MYSQL_DATABASE"] } else { "atlas_gym" }
$DbUser = if ($env:DB_USER) { $env:DB_USER } elseif ($EnvVars["DB_USER"]) { $EnvVars["DB_USER"] } else { "root" }
$DbPass = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } elseif ($EnvVars["DB_PASSWORD"]) { $EnvVars["DB_PASSWORD"] } else { "" }
$DbPort = if ($env:DB_HOST_PORT) { $env:DB_HOST_PORT } elseif ($EnvVars["DB_HOST_PORT"]) { $EnvVars["DB_HOST_PORT"] } else { "3307" }
$BackupDir = Join-Path $ProjectDir "backups"

function Resolver-MysqlBin($nombre) {
    $cmd = Get-Command $nombre -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $rutas = @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\$nombre.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\$nombre.exe",
        "C:\xampp\mysql\bin\$nombre.exe"
    )
    foreach ($r in $rutas) { if (Test-Path $r) { return $r } }
    $glob = Get-ChildItem "C:\Program Files\MySQL" -Filter "$nombre.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($glob) { return $glob.FullName }
    return $nombre
}

# Validar integridad si es archivo comprimido gzip
$IsGzip = $RestoreFile.EndsWith(".gz", [System.StringComparison]::OrdinalIgnoreCase)
if ($IsGzip) {
    try {
        $testIn = [System.IO.File]::OpenRead($RestoreFile)
        $testGz = New-Object System.IO.Compression.GZipStream($testIn, [System.IO.Compression.CompressionMode]::Decompress)
        $nullStream = [System.IO.Stream]::Null
        $testGz.CopyTo($nullStream)
        $testGz.Dispose()
        $testIn.Dispose()
    } catch {
        throw "El archivo gzip '$RestoreFile' esta corrupto o fallo la verificacion de integridad: $_"
    }
}

Write-Host "============================================================================" -ForegroundColor Yellow
Write-Host " ADVERTENCIA CRITICA - RESTAURACION DE BASE DE DATOS" -ForegroundColor Yellow
Write-Host " Base destino:   $DbName"
Write-Host " Archivo origen: $RestoreFile ($FileSize bytes)"
Write-Host "============================================================================" -ForegroundColor Yellow

if (-not $Force) {
    $confirm = Read-Host "Seguro de sobreescribir la base de datos '$DbName'? (s/N)"
    if ($confirm -notmatch '^(s|si|y|yes)$') {
        Write-Host "Operacion cancelada por el usuario." -ForegroundColor Gray
        exit 0
    }
}

# Generar snapshot de seguridad previo automatico
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$PreRestoreSql = Join-Path $BackupDir "pre_restore_${DbName}_${Timestamp}.sql"
$PreRestoreGz = "${PreRestoreSql}.gz"

Write-Host "[$(Get-Date -Format o)] Generando backup de seguridad previo en '$PreRestoreGz'..." -ForegroundColor Cyan

$UseDocker = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $running = docker ps --format "{{.Names}}" 2>$null
    if ($running -contains $ContainerName) {
        $UseDocker = $true
    }
}

try {
    if ($UseDocker) {
        docker exec -e "MYSQL_PWD=$DbPass" $ContainerName `
            mysqldump --single-transaction --quick --routines --triggers -u $DbUser $DbName 2>$null `
            | Out-File -FilePath $PreRestoreSql -Encoding utf8
    } else {
        $mysqldumpBin = Resolver-MysqlBin "mysqldump"
        $env:MYSQL_PWD = $DbPass
        try {
            & $mysqldumpBin --single-transaction --quick --routines --triggers `
                -h 127.0.0.1 -P $DbPort -u $DbUser $DbName 2>$null `
                | Out-File -FilePath $PreRestoreSql -Encoding utf8
        } finally {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }

    if ((Test-Path $PreRestoreSql) -and ((Get-Item $PreRestoreSql).Length -gt 100)) {
        $inStream = [System.IO.File]::OpenRead($PreRestoreSql)
        $outStream = [System.IO.File]::Create($PreRestoreGz)
        $gz = New-Object System.IO.Compression.GZipStream($outStream, [System.IO.Compression.CompressionLevel]::Optimal)
        $inStream.CopyTo($gz)
        $gz.Dispose()
        $outStream.Dispose()
        $inStream.Dispose()
        Remove-Item $PreRestoreSql -Force -ErrorAction SilentlyContinue
        Write-Host "[$(Get-Date -Format o)] Snapshot previo guardado exitosamente." -ForegroundColor Green
    } else {
        Remove-Item $PreRestoreSql -Force -ErrorAction SilentlyContinue
        Write-Host "[WARN] No se pudo generar snapshot previo (base vacia?). Continuando con la restauracion..." -ForegroundColor Yellow
    }
} catch {
    Write-Warning "No se pudo tomar el snapshot previo: $_. Continuando con la restauracion..."
}

Write-Host "[$(Get-Date -Format o)] Restaurando datos en '$DbName'..." -ForegroundColor Cyan

# Descomprimir si es necesario en un archivo temporal
$SqlToExecute = $RestoreFile
$TempExtracted = $null

if ($IsGzip) {
    $TempExtracted = Join-Path $BackupDir "temp_restore_${Timestamp}.sql"
    $inGz = [System.IO.File]::OpenRead($RestoreFile)
    $decompressor = New-Object System.IO.Compression.GZipStream($inGz, [System.IO.Compression.CompressionMode]::Decompress)
    $outSql = [System.IO.File]::Create($TempExtracted)
    $decompressor.CopyTo($outSql)
    $outSql.Dispose()
    $decompressor.Dispose()
    $inGz.Dispose()
    $SqlToExecute = $TempExtracted
}

try {
    if ($UseDocker) {
        Get-Content $SqlToExecute -Raw | docker exec -i -e "MYSQL_PWD=$DbPass" $ContainerName mysql -u $DbUser $DbName
    } else {
        $mysqlBin = Resolver-MysqlBin "mysql"
        $env:MYSQL_PWD = $DbPass
        try {
            Get-Content $SqlToExecute -Raw | & $mysqlBin -h 127.0.0.1 -P $DbPort -u $DbUser $DbName
        } finally {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }
} finally {
    if ($TempExtracted -and (Test-Path $TempExtracted)) {
        Remove-Item $TempExtracted -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "============================================================================" -ForegroundColor Green
Write-Host " RESTAURACION COMPLETADA EXITOSAMENTE en '$DbName'" -ForegroundColor Green
Write-Host " Origen: $RestoreFile"
Write-Host " Snapshot de seguridad previo: $PreRestoreGz"
Write-Host "============================================================================" -ForegroundColor Green
