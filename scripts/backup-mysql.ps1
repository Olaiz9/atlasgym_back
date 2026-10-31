# ============================================================================
# ATLAS Gym - Backup Diario Automatizado de MySQL (Windows PowerShell / Task Scheduler)
# Soporta contenedor Docker (atlas-mysql) o instalación local de MySQL/XAMPP.
# ============================================================================

param(
    [string]$BackupDir = "",
    [int]$RetentionDays = 14,
    [string]$ContainerName = "atlas-mysql"
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

$DbName = if ($EnvVars["MYSQL_DATABASE"]) { $EnvVars["MYSQL_DATABASE"] } else { "atlas_gym" }
$DbUser = if ($EnvVars["DB_USER"]) { $EnvVars["DB_USER"] } else { "root" }
$DbPass = if ($EnvVars["DB_PASSWORD"]) { $EnvVars["DB_PASSWORD"] } else { "" }
$DbPort = if ($EnvVars["DB_HOST_PORT"]) { $EnvVars["DB_HOST_PORT"] } else { "3307" }

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
    $env:MYSQL_PWD = $DbPass
    try {
        mysqldump --single-transaction --quick --routines --triggers `
            -h 127.0.0.1 -P $DbPort -u $DbUser $DbName `
            | Out-File -FilePath $SqlTempFile -Encoding utf8
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path $SqlTempFile) -or ((Get-Item $SqlTempFile).Length -lt 100)) {
    Remove-Item $SqlTempFile -Force -ErrorAction SilentlyContinue
    throw "El backup SQL generado está vacío o incompleto."
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

# Purgar backups antiguos según retención
$LimitDate = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $BackupDir -Filter "${DbName}_*.sql.gz" |
    Where-Object { $_.LastWriteTime -lt $LimitDate } |
    Remove-Item -Force

$FinalSize = (Get-Item $GzFinalFile).Length
Write-Host "[$(Get-Date -Format o)] Backup completado OK: $GzFinalFile ($FinalSize bytes). Retención: $RetentionDays días."
