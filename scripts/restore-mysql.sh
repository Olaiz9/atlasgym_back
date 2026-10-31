#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Restauración Segura de Base de Datos MySQL
# Uso:
#   ./scripts/restore-mysql.sh backups/atlas_gym_20261031_033000.sql.gz [--yes]
#
# Seguridad:
#   1. Valida existencia e integridad del archivo (.gz o .sql).
#   2. Exige confirmación interactiva expresa (salvo que se pase --yes).
#   3. Genera un backup de seguridad automático previo a la restauración
#      (pre_restore_YYYYMMDD_HHMMSS.sql.gz) para evitar pérdidas irreversibles.
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

if [[ $# -lt 1 ]]; then
  echo "Uso: $0 <ruta_al_backup.sql.gz|.sql> [--yes]" >&2
  exit 1
fi

RESTORE_FILE="$1"
CONFIRM="${2:-}"

if [[ ! -f "${RESTORE_FILE}" ]]; then
  echo "[ERROR] El archivo de backup '${RESTORE_FILE}' no existe." >&2
  exit 1
fi

FILE_SIZE="$(wc -c < "${RESTORE_FILE}")"
if [[ "${FILE_SIZE}" -lt 100 ]]; then
  echo "[ERROR] El archivo de backup '${RESTORE_FILE}' está vacío o truncado (${FILE_SIZE} bytes)." >&2
  exit 1
fi

# Validar integridad si es archivo comprimido gzip
if [[ "${RESTORE_FILE}" =~ \.gz$ ]]; then
  if ! gzip -t "${RESTORE_FILE}" >/dev/null 2>&1; then
    echo "[ERROR] El archivo gzip '${RESTORE_FILE}' está corrupto (falló gzip -t). Abortando." >&2
    exit 1
  fi
fi

CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-atlas-mysql}"
DB_NAME="${MYSQL_DATABASE:-atlas_gym}"
DB_USER_NAME="${DB_USER:-root}"
DB_PASS_VAL="${DB_PASSWORD:-}"
DB_HOST_NAME="${DB_HOST:-127.0.0.1}"
DB_PORT_NUM="${DB_HOST_PORT:-3307}"
BACKUP_DIR="${BACKUP_DIR:-${PROJECT_DIR}/backups}"

echo "============================================================================"
echo " ADVERTENCIA CRÍTICA - RESTAURACIÓN DE BASE DE DATOS"
echo " Base destino:  ${DB_NAME}"
echo " Archivo origen: ${RESTORE_FILE} (${FILE_SIZE} bytes)"
echo "============================================================================"

if [[ "${CONFIRM}" != "--yes" && "${CONFIRM}" != "-y" ]]; then
  read -r -p "¿Está seguro de sobreescribir la base de datos '${DB_NAME}'? [s/N]: " RESP
  if [[ "${RESP}" != "s" && "${RESP}" != "S" && "${RESP}" != "si" && "${RESP}" != "SI" ]]; then
    echo "Operación cancelada por el usuario."
    exit 0
  fi
fi

# Tomar snapshot de seguridad previo por si la restauración falla o es revertida
mkdir -p "${BACKUP_DIR}"
PRE_TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
PRE_RESTORE_FILE="${BACKUP_DIR}/pre_restore_${DB_NAME}_${PRE_TIMESTAMP}.sql.gz"
echo "[$(date -Iseconds)] Generando backup de seguridad previo en '${PRE_RESTORE_FILE}'..."

USE_DOCKER=false
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  USE_DOCKER=true
fi

if [[ "${USE_DOCKER}" == "true" ]]; then
  docker exec -e MYSQL_PWD="${DB_PASS_VAL}" "${CONTAINER_NAME}" \
    mysqldump --single-transaction --quick --routines --triggers \
    -u "${DB_USER_NAME}" "${DB_NAME}" 2>/dev/null | gzip -9 > "${PRE_RESTORE_FILE}" || true
else
  MYSQL_PWD="${DB_PASS_VAL}" mysqldump \
    --single-transaction --quick --routines --triggers \
    -h "${DB_HOST_NAME}" -P "${DB_PORT_NUM}" -u "${DB_USER_NAME}" "${DB_NAME}" 2>/dev/null | gzip -9 > "${PRE_RESTORE_FILE}" || true
fi

if [[ -f "${PRE_RESTORE_FILE}" && $(wc -c < "${PRE_RESTORE_FILE}") -gt 100 ]]; then
  echo "[$(date -Iseconds)] Snapshot previo guardado exitosamente."
else
  echo "[WARN] No se pudo generar snapshot previo (¿base vacía?). Continuando con la restauración..."
fi

echo "[$(date -Iseconds)] Restaurando datos en '${DB_NAME}'..."

if [[ "${RESTORE_FILE}" =~ \.gz$ ]]; then
  if [[ "${USE_DOCKER}" == "true" ]]; then
    gzip -dc "${RESTORE_FILE}" | docker exec -i -e MYSQL_PWD="${DB_PASS_VAL}" "${CONTAINER_NAME}" \
      mysql -u "${DB_USER_NAME}" "${DB_NAME}"
  else
    gzip -dc "${RESTORE_FILE}" | MYSQL_PWD="${DB_PASS_VAL}" mysql \
      -h "${DB_HOST_NAME}" -P "${DB_PORT_NUM}" -u "${DB_USER_NAME}" "${DB_NAME}"
  fi
else
  if [[ "${USE_DOCKER}" == "true" ]]; then
    docker exec -i -e MYSQL_PWD="${DB_PASS_VAL}" "${CONTAINER_NAME}" \
      mysql -u "${DB_USER_NAME}" "${DB_NAME}" < "${RESTORE_FILE}"
  else
    MYSQL_PWD="${DB_PASS_VAL}" mysql \
      -h "${DB_HOST_NAME}" -P "${DB_PORT_NUM}" -u "${DB_USER_NAME}" "${DB_NAME}" < "${RESTORE_FILE}"
  fi
fi

echo "============================================================================"
echo " RESTAURACIÓN COMPLETADA EXITOSAMENTE en '${DB_NAME}'"
echo " Origen: ${RESTORE_FILE}"
echo " Snapshot previo: ${PRE_RESTORE_FILE}"
echo "============================================================================"
