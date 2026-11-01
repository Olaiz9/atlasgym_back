# ============================================================================
# ATLAS Gym - Backup Diario Automatizado de MySQL (Windows PowerShell / Task Scheduler)
# Soporta contenedor Docker (atlas-mysql) o instalacion local de MySQL/XAMPP.
# Incluye validacion de integridad gzip y hooks de resguardo offsite (nube / NAS).
# ============================================================================

param(
    [string]$BackupDir = "",
    [int]$RetentionDays = 14,
    [string]$ContainerName = "atlas-mysql",
    [string]$BackupOffsiteDir = "",
    [string]$RcloneRemote = ""
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

if ([string]::IsNullOrWhiteSpace($BackupDir)) {
    $BackupDir = Join-Path $ProjectDir "backups"
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
$OffsiteDir = if ($BackupOffsiteDir) { $BackupOffsiteDir } elseif ($env:BACKUP_OFFSITE_DIR) { $env:BACKUP_OFFSITE_DIR } elseif ($EnvVars["BACKUP_OFFSITE_DIR"]) { $EnvVars["BACKUP_OFFSITE_DIR"] } else { "" }
$RcloneTarget = if ($RcloneRemote) { $RcloneRemote } elseif ($env:RCLONE_REMOTE) { $env:RCLONE_REMOTE } elseif ($EnvVars["RCLONE_REMOTE"]) { $EnvVars["RCLONE_REMOTE"] } else { "" }

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

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$SqlTempFile = Join-Path $BackupDir "${DbName}_${Timestamp}.sql"
$GzFinalFile = "${SqlTempFile}.gz"

Write-Host "[$(Get-Date -Format o)] Iniciando backup consistente de '$DbName'..."

$UseDocker = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $running = docker ps --format "{{.Names}}" 2>$null
    if ($running -contains $ContainerName) {
        $UseDocker = $true
    }
}

if ($UseDocker) {
    docker exec -e "MYSQL_PWD=$DbPass" $ContainerName `
        mysqldump --single-transaction --quick --routines --triggers -u $DbUser $DbName `
        | Out-File -FilePath $SqlTempFile -Encoding utf8
} else {
    $mysqldumpBin = Resolver-MysqlBin "mysqldump"
    $env:MYSQL_PWD = $DbPass
    try {
        & $mysqldumpBin --single-transaction --quick --routines --triggers `
            -h 127.0.0.1 -P $DbPort -u $DbUser $DbName `
            | Out-File -FilePath $SqlTempFile -Encoding utf8
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path $SqlTempFile) -or ((Get-Item $SqlTempFile).Length -lt 100)) {
    Remove-Item $SqlTempFile -Force -ErrorAction SilentlyContinue
    throw "El backup SQL generado esta vacio o incompleto (< 100 bytes)."
}

# Comprimir a .sql.gz
$inStream = [System.IO.File]::OpenRead($SqlTempFile)
$outStream = [System.IO.File]::Create($GzFinalFile)
$gzipStream = New-Object System.IO.Compression.GZipStream($outStream, [System.IO.Compression.CompressionLevel]::Optimal)
try {
    $inStream.CopyTo($gzipStream)
} finally {
    $gzipStream.Dispose()
    $outStream.Dispose()
    $inStream.Dispose()
}

Remove-Item $SqlTempFile -Force

# Verificacion de integridad: descompresion de prueba
try {
    $testIn = [System.IO.File]::OpenRead($GzFinalFile)
    $testGz = New-Object System.IO.Compression.GZipStream($testIn, [System.IO.Compression.CompressionMode]::Decompress)
    $nullStream = [System.IO.Stream]::Null
    $testGz.CopyTo($nullStream)
    $testGz.Dispose()
    $testIn.Dispose()
} catch {
    Remove-Item $GzFinalFile -Force -ErrorAction SilentlyContinue
    throw "El archivo gzip generado esta corrupto o fallo la verificacion de integridad: $_"
}

# Purgar backups locales antiguos segun retencion
$LimitDate = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $BackupDir -Filter "${DbName}_*.sql.gz" |
    Where-Object { $_.LastWriteTime -lt $LimitDate } |
    Remove-Item -Force

$FinalSize = (Get-Item $GzFinalFile).Length
Write-Host "[$(Get-Date -Format o)] Backup local verificado OK: $GzFinalFile ($FinalSize bytes). Retencion: $RetentionDays dias."

# Hook Offsite 1: Carpeta sincronizada externa (OneDrive / Google Drive / NAS)
if (-not [string]::IsNullOrWhiteSpace($OffsiteDir)) {
    if (Test-Path $OffsiteDir) {
        Write-Host "[$(Get-Date -Format o)] Sincronizando copia offsite a '$OffsiteDir'..."
        Copy-Item -Path $GzFinalFile -Destination $OffsiteDir -Force
        Get-ChildItem -Path $OffsiteDir -Filter "${DbName}_*.sql.gz" |
            Where-Object { $_.LastWriteTime -lt $LimitDate } |
            Remove-Item -Force -ErrorAction SilentlyContinue
        Write-Host "[$(Get-Date -Format o)] Copia offsite sincronizada exitosamente."
    } else {
        Write-Warning "BACKUP_OFFSITE_DIR='$OffsiteDir' configurado pero la ruta no existe o no es accesible."
    }
}

# Hook Offsite 2: Remoto rclone hacia nube
if (-not [string]::IsNullOrWhiteSpace($RcloneTarget)) {
    if (Get-Command rclone -ErrorAction SilentlyContinue) {
        Write-Host "[$(Get-Date -Format o)] Subiendo copia offsite a remoto rclone '$RcloneTarget'..."
        rclone copy $GzFinalFile $RcloneTarget
    } else {
        Write-Warning "RCLONE_REMOTE='$RcloneTarget' configurado pero el comando 'rclone' no esta en PATH."
    }
}
