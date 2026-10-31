#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Backup Diario Automatizado de MySQL (Linux / VPS / Cron)
# Incluye validación de integridad gzip y hooks de resguardo offsite (nube / NAS).
# Ejemplo crontab (todos los días a las 03:30 AM):
#   30 3 * * * /opt/atlasgym_back/scripts/backup-mysql.sh >> /var/log/atlas-backup.log 2>&1
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ -f "${PROJECT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${PROJECT_DIR}/.env"
  set +a
fi

BACKUP_DIR="${BACKUP_DIR:-${PROJECT_DIR}/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-atlas-mysql}"
DB_NAME="${MYSQL_DATABASE:-atlas_gym}"
DB_USER_NAME="${DB_USER:-root}"
DB_PASS_VAL="${DB_PASSWORD:-}"
DB_HOST_NAME="${DB_HOST:-127.0.0.1}"
DB_PORT_NUM="${DB_HOST_PORT:-3307}"
OFFSITE_DIR="${BACKUP_OFFSITE_DIR:-}"
RCLONE_TARGET="${RCLONE_REMOTE:-}"

mkdir -p "${BACKUP_DIR}"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql.gz"
TMP_FILE="${BACKUP_FILE}.tmp"

echo "[$(date -Iseconds)] Iniciando backup consistente de '${DB_NAME}'..."

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  docker exec -e MYSQL_PWD="${DB_PASS_VAL}" "${CONTAINER_NAME}" \
    mysqldump --single-transaction --quick --routines --triggers \
    -u "${DB_USER_NAME}" "${DB_NAME}" | gzip -9 > "${TMP_FILE}"
else
  MYSQL_PWD="${DB_PASS_VAL}" mysqldump \
    --single-transaction --quick --routines --triggers \
    -h "${DB_HOST_NAME}" -P "${DB_PORT_NUM}" -u "${DB_USER_NAME}" "${DB_NAME}" | gzip -9 > "${TMP_FILE}"
fi

# Verificar que el backup comprimido exista y no esté vacío (> 100 bytes)
FILE_SIZE="$(wc -c < "${TMP_FILE}")"
if [[ "${FILE_SIZE}" -lt 100 ]]; then
  rm -f "${TMP_FILE}"
  echo "[ERROR] El backup generado está vacío o truncado (${FILE_SIZE} bytes)." >&2
  exit 1
fi

# Verificación de integridad de descompresión gzip
if ! gzip -t "${TMP_FILE}" >/dev/null 2>&1; then
  rm -f "${TMP_FILE}"
  echo "[ERROR] El archivo de backup gzip está corrupto o truncado (falló gzip -t)." >&2
  exit 1
fi

mv "${TMP_FILE}" "${BACKUP_FILE}"
chmod 600 "${BACKUP_FILE}"

# Limpieza automática local de backups mayores a RETENTION_DAYS días
find "${BACKUP_DIR}" -maxdepth 1 -type f -name "${DB_NAME}_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete

echo "[$(date -Iseconds)] Backup local verificado OK: ${BACKUP_FILE} (${FILE_SIZE} bytes). Retención: ${RETENTION_DAYS} días."

# Hook Offsite 1: Carpeta sincronizada externa (Google Drive Desktop, OneDrive o NAS)
if [[ -n "${OFFSITE_DIR}" ]]; then
  if [[ -d "${OFFSITE_DIR}" ]]; then
    echo "[$(date -Iseconds)] Sincronizando copia offsite a '${OFFSITE_DIR}'..."
    cp -p "${BACKUP_FILE}" "${OFFSITE_DIR}/"
    find "${OFFSITE_DIR}" -maxdepth 1 -type f -name "${DB_NAME}_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete || true
    echo "[$(date -Iseconds)] Copia offsite sincronizada exitosamente."
  else
    echo "[WARN] BACKUP_OFFSITE_DIR='${OFFSITE_DIR}' configurado pero no es un directorio accesible." >&2
  fi
fi

# Hook Offsite 2: Remoto rclone hacia nube (S3, B2, Google Drive)
if [[ -n "${RCLONE_TARGET}" ]]; then
  if command -v rclone >/dev/null 2>&1; then
    echo "[$(date -Iseconds)] Subiendo copia offsite a remoto rclone '${RCLONE_TARGET}'..."
    rclone copy "${BACKUP_FILE}" "${RCLONE_TARGET}" || echo "[WARN] Falló el comando rclone copy hacia '${RCLONE_TARGET}'." >&2
  else
    echo "[WARN] RCLONE_REMOTE='${RCLONE_TARGET}' configurado pero el binario 'rclone' no está en PATH." >&2
  fi
fi
