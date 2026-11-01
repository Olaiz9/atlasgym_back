$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configDirectory = Join-Path $projectRoot 'config'
$configPath = Join-Path $configDirectory 'atlas-local.properties'

if (Test-Path -LiteralPath $configPath) {
    Write-Host 'La configuracion JWT local ya existe. No se modifica ni se muestra su contenido.'
    exit 0
}

# 48 bytes criptograficamente aleatorios, representados en Base64.
$randomBytes = New-Object byte[] 48
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $generator.GetBytes($randomBytes)
} finally {
    $generator.Dispose()
}
$localSecret = [Convert]::ToBase64String($randomBytes)
New-Item -ItemType Directory -Force -Path $configDirectory | Out-Null
$contents = "# Solo para este equipo. No compartir ni subir a Git.`nATLAS_JWT_SECRET=$localSecret`n"
# CreateNew impide sobrescribir una configuracion creada por otra ejecucion.
$stream = [System.IO.File]::Open($configPath, [System.IO.FileMode]::CreateNew)
try {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($contents)
    $stream.Write($bytes, 0, $bytes.Length)
} finally {
    $stream.Dispose()
}
Write-Host 'Clave local generada. Ejecute el backend desde la raiz del proyecto y vuelva a iniciar sesion.'
