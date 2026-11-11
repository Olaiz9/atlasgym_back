#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Restauración Segura de Base de Datos MySQL
# Uso:
#   ./scripts/restore-mysql.sh backups/atlas_gym_20261031_033000.sql.gz [--yes]
#
# Seguridad:
#   1. Valida existencia e integridad del archivo (.gz o .sql).
#   2. Exige confirmación interactiva expresa (salvo que se pase --yes).
#   3. Genera un backup de seguridad previo (pre_restore_<base>_YYYYMMDD_HHMMSS.sql.gz).
#      Es OBLIGATORIO: si la base tiene tablas y la copia falla, no se toca nada.
#   4. Frena el backend, vacía la base (DROP/CREATE) y recién ahí carga el backup.
#      Cargar encima de la base dejaba tablas huérfanas de migraciones más nuevas:
#      después de un rollback, la siguiente actualización fallaba con "table already exists".
#   5. Vuelve a levantar el backend (si estaba corriendo) y espera a /api/salud.
#   6. Borra los pre_restore_* más viejos que RETENTION_DAYS.
#
# Restaurá siempre con la MISMA versión del sistema con la que se hizo el backup;
# recién después actualizá (docs/DEPLOY.md).
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Cargar .env como DATOS, no como codigo: con "source", una linea como
# ATLAS_ADMIN_NOMBRE=Titular 1 se ejecuta como el comando "1" y el script se corta.
if [[ -f "${PROJECT_DIR}/.env" ]]; then
  while IFS= read -r linea || [[ -n "${linea}" ]]; do
    linea="${linea%$'\r'}"
    [[ -z "${linea}" || "${linea}" =~ ^[[:space:]]*# || "${linea}" != *=* ]] && continue
    clave="${linea%%=*}"
    valor="${linea#*=}"
    [[ "${clave}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    # Comillas envolventes opcionales: CLAVE="valor con espacios"
    if [[ "${valor}" =~ ^\"(.*)\"$ || "${valor}" =~ ^\'(.*)\'$ ]]; then valor="${BASH_REMATCH[1]}"; fi
    export "${clave}=${valor}"
  done < "${PROJECT_DIR}/.env"
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
BACKEND_CONTAINER="${BACKEND_CONTAINER_NAME:-atlas-backend}"
DB_NAME="${MYSQL_DATABASE:-atlas_gym}"
DB_USER_NAME="${DB_USER:-root}"
DB_PASS_VAL="${DB_PASSWORD:-}"
DB_HOST_NAME="${DB_HOST:-127.0.0.1}"
DB_PORT_NUM="${DB_HOST_PORT:-3307}"
BACKUP_DIR="${BACKUP_DIR:-${PROJECT_DIR}/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
SALUD_URL="http://127.0.0.1:${BACKEND_HOST_PORT:-8080}/api/salud"

# El nombre de la base va dentro de SQL (DROP/CREATE): solo letras, números y _.
if [[ ! "${DB_NAME}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "[ERROR] MYSQL_DATABASE='${DB_NAME}' tiene caracteres no permitidos (solo letras, números y _)." >&2
  exit 1
fi

USE_DOCKER=false
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  USE_DOCKER=true
fi

# mysql y mysqldump, dentro del contenedor o contra un MySQL local. stdin pasa de largo.
ejecutar_mysql() {
  if [[ "${USE_DOCKER}" == "true" ]]; then
    docker exec -i -e MYSQL_PWD="${DB_PASS_VAL}" "${CONTAINER_NAME}" mysql -u "${DB_USER_NAME}" "$@"
  else
    MYSQL_PWD="${DB_PASS_VAL}" mysql -h "${DB_HOST_NAME}" -P "${DB_PORT_NUM}" -u "${DB_USER_NAME}" "$@"
  fi
}

ejecutar_mysqldump() {
  if [[ "${USE_DOCKER}" == "true" ]]; then
    docker exec -e MYSQL_PWD="${DB_PASS_VAL}" "${CONTAINER_NAME}" \
      mysqldump --single-transaction --no-tablespaces --quick --routines --triggers \
      -u "${DB_USER_NAME}" "${DB_NAME}"
  else
    MYSQL_PWD="${DB_PASS_VAL}" mysqldump \
      --single-transaction --no-tablespaces --quick --routines --triggers \
      -h "${DB_HOST_NAME}" -P "${DB_PORT_NUM}" -u "${DB_USER_NAME}" "${DB_NAME}"
  fi
}

backend_corriendo() {
  [[ "${USE_DOCKER}" == "true" ]] && docker ps --format '{{.Names}}' | grep -qx "${BACKEND_CONTAINER}"
}

# Sin conectarse a la base (puede no existir): cuenta sus tablas en information_schema.
TABLAS_ACTUALES="$(ejecutar_mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${DB_NAME}'" < /dev/null)" || {
  echo "[ERROR] No me pude conectar a MySQL para revisar la base '${DB_NAME}'. No se tocó nada." >&2
  exit 1
}

echo "============================================================================"
echo " ADVERTENCIA CRÍTICA - RESTAURACIÓN DE BASE DE DATOS"
echo " Base destino:   ${DB_NAME} (${TABLAS_ACTUALES} tablas hoy; se BORRA entera antes de cargar)"
echo " Archivo origen: ${RESTORE_FILE} (${FILE_SIZE} bytes)"
echo " Restaurá con la misma versión del sistema con la que se hizo este backup."
echo "============================================================================"

if [[ "${CONFIRM}" != "--yes" && "${CONFIRM}" != "-y" ]]; then
  read -r -p "¿Seguro que querés borrar la base '${DB_NAME}' y reemplazarla por este backup? [s/N]: " RESP
  if [[ "${RESP}" != "s" && "${RESP}" != "S" && "${RESP}" != "si" && "${RESP}" != "SI" ]]; then
    echo "Operación cancelada."
    exit 0
  fi
fi

# --- 1. Copia previa (obligatoria si la base tiene datos) ---------------------
mkdir -p "${BACKUP_DIR}"
PRE_TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
PRE_RESTORE_FILE="${BACKUP_DIR}/pre_restore_${DB_NAME}_${PRE_TIMESTAMP}.sql.gz"

if [[ "${TABLAS_ACTUALES}" -gt 0 ]]; then
  echo "[$(date -Iseconds)] Copia de seguridad previa en '${PRE_RESTORE_FILE}'..."
  if ! ejecutar_mysqldump | gzip -9 > "${PRE_RESTORE_FILE}" \
     || [[ $(wc -c < "${PRE_RESTORE_FILE}") -lt 100 ]] \
     || ! gzip -t "${PRE_RESTORE_FILE}" >/dev/null 2>&1; then
    rm -f "${PRE_RESTORE_FILE}" 2>/dev/null || true
    echo "[ERROR] No se pudo hacer la copia previa de '${DB_NAME}' (tiene ${TABLAS_ACTUALES} tablas)." >&2
    echo "[ERROR] Sin esa copia no hay vuelta atrás: no se tocó la base ni el backend." >&2
    exit 1
  fi
  chmod 600 "${PRE_RESTORE_FILE}"
  echo "[$(date -Iseconds)] Copia previa guardada."
else
  PRE_RESTORE_FILE=""
  echo "[$(date -Iseconds)] La base '${DB_NAME}' está vacía o no existe: no hace falta copia previa."
fi

# --- 2. Frenar el backend -------------------------------------------------------
# Si sigue andando mientras se vacía la base, registra cobros que se pierden o falla a mitad.
BACKEND_ESTABA_CORRIENDO=false
if backend_corriendo; then
  BACKEND_ESTABA_CORRIENDO=true
  echo "[$(date -Iseconds)] Frenando el backend (${BACKEND_CONTAINER})..."
  docker stop "${BACKEND_CONTAINER}" >/dev/null
fi

fallar_restauracion() {
  echo "[ERROR] $1" >&2
  if [[ -n "${PRE_RESTORE_FILE}" ]]; then
    echo "[ERROR] Para volver a como estaba antes: $0 ${PRE_RESTORE_FILE} --yes" >&2
  fi
  if [[ "${BACKEND_ESTABA_CORRIENDO}" == "true" ]]; then
    echo "[ERROR] El backend quedó FRENADO a propósito (la base puede estar incompleta)." >&2
    echo "[ERROR] Se vuelve a levantar solo cuando una restauración termina bien." >&2
  fi
  exit 1
}

# --- 3. Vaciar la base ----------------------------------------------------------
echo "[$(date -Iseconds)] Vaciando la base '${DB_NAME}' (DROP/CREATE)..."
ejecutar_mysql -e "DROP DATABASE IF EXISTS \`${DB_NAME}\`; CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" < /dev/null \
  || fallar_restauracion "No se pudo vaciar la base '${DB_NAME}' (¿el usuario ${DB_USER_NAME} tiene permiso de DROP/CREATE?)."

# --- 4. Cargar el backup ----------------------------------------------------------
echo "[$(date -Iseconds)] Cargando '${RESTORE_FILE}' en '${DB_NAME}'..."
if [[ "${RESTORE_FILE}" =~ \.gz$ ]]; then
  gzip -dc "${RESTORE_FILE}" | ejecutar_mysql "${DB_NAME}" \
    || fallar_restauracion "Falló la carga del backup '${RESTORE_FILE}'."
else
  ejecutar_mysql "${DB_NAME}" < "${RESTORE_FILE}" \
    || fallar_restauracion "Falló la carga del backup '${RESTORE_FILE}'."
fi

# --- 5. Levantar el backend y esperar a que responda ---------------------------
if [[ "${BACKEND_ESTABA_CORRIENDO}" == "true" ]]; then
  echo "[$(date -Iseconds)] Levantando el backend..."
  docker start "${BACKEND_CONTAINER}" >/dev/null
  if command -v curl >/dev/null 2>&1; then
    SALUD_OK=false
    for _ in $(seq 1 60); do
      if curl -sf -o /dev/null "${SALUD_URL}"; then SALUD_OK=true; break; fi
      sleep 3
    done
    if [[ "${SALUD_OK}" != "true" ]]; then
      echo "[ERROR] Los datos se restauraron, pero el backend no responde en ${SALUD_URL} después de 3 minutos." >&2
      echo "[ERROR] Revisá: docker logs --tail 100 ${BACKEND_CONTAINER}" >&2
      exit 1
    fi
    echo "[$(date -Iseconds)] El backend responde (${SALUD_URL})."
  else
    echo "[WARN] No hay curl: comprobá a mano que el backend responda en ${SALUD_URL}."
  fi
fi

# --- 6. Limpiar copias previas viejas ---------------------------------------------
find "${BACKUP_DIR}" -maxdepth 1 -type f -name "pre_restore_${DB_NAME}_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete || true

echo "============================================================================"
echo " RESTAURACIÓN COMPLETADA en '${DB_NAME}'"
echo " Origen: ${RESTORE_FILE}"
echo " Copia previa: ${PRE_RESTORE_FILE:-(no hacía falta: la base estaba vacía)}"
echo "============================================================================"
