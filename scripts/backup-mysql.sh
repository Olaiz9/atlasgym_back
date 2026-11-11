#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Backup Diario Automatizado de MySQL (Linux / VPS / Cron)
# Incluye validación de integridad gzip y hooks de resguardo offsite (nube / NAS).
# Ejemplo crontab (todos los días a las 03:30 AM):
#   30 3 * * * /opt/atlas/atlasgym_back/scripts/backup-mysql.sh >> /opt/atlas/backup.log 2>&1
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
PING_URL="${BACKUP_PING_URL:-}"
# Solo para pruebas (CI, una PC de desarrollo): aceptar un backup sin copia fuera del servidor.
SOLO_LOCAL="${BACKUP_SOLO_LOCAL:-}"
# Espacio libre minimo (MB) en el disco de BACKUP_DIR para arrancar: con el disco casi lleno,
# el dump corta a mitad y MySQL tambien sufre. Mejor fallar antes y que el monitor avise.
MIN_LIBRE_MB="${BACKUP_MIN_LIBRE_MB:-1024}"
# Dias que se guardan los backups en el remoto rclone. Vacio = no se borra nada alla.
RCLONE_RETENCION="${RCLONE_RETENTION_DAYS:-}"
TMP_FILE=""

# Al salir: si fallo, borrar el .tmp a medio escribir (la limpieza solo mira *.sql.gz y
# los .tmp se juntaban hasta llenar el disco). Despues, aviso al monitor externo.
# Monitor (healthchecks.io): ping al terminar bien, <url>/fail si algo fallo.
# Si el ping no llega, el monitor avisa igual (no recibio el ping del dia). Nunca corta el backup.
avisar_monitor() {
  local rc=$?
  if [[ ${rc} -ne 0 && -n "${TMP_FILE}" ]]; then
    rm -f "${TMP_FILE}" || true
  fi
  if [[ -n "${PING_URL}" ]] && command -v curl >/dev/null 2>&1; then
    local destino="${PING_URL}"
    [[ ${rc} -ne 0 ]] && destino="${PING_URL%/}/fail"
    curl -fsS -m 10 --retry 3 -o /dev/null "${destino}"       || echo "[WARN] No se pudo avisar al monitor de backups (${destino})." >&2
  fi
  return ${rc}
}
trap avisar_monitor EXIT

fallar() {
  echo "[ERROR] $1" >&2
  exit 1
}

[[ "${MIN_LIBRE_MB}" =~ ^[0-9]+$ ]] || fallar "BACKUP_MIN_LIBRE_MB='${MIN_LIBRE_MB}' tiene que ser un numero de MB."
if [[ -n "${RCLONE_RETENCION}" && ! "${RCLONE_RETENCION}" =~ ^[1-9][0-9]*$ ]]; then
  fallar "RCLONE_RETENTION_DAYS='${RCLONE_RETENCION}' tiene que ser un numero de dias mayor que 0 (o vacio para no borrar nada)."
fi

mkdir -p "${BACKUP_DIR}"

# Espacio libre antes de empezar
LIBRE_MB="$(df -Pm "${BACKUP_DIR}" 2>/dev/null | awk 'NR==2 {print $4}' || true)"
if [[ "${LIBRE_MB}" =~ ^[0-9]+$ ]]; then
  if [[ "${LIBRE_MB}" -lt "${MIN_LIBRE_MB}" ]]; then
    fallar "Quedan ${LIBRE_MB} MB libres en el disco de '${BACKUP_DIR}' (minimo: ${MIN_LIBRE_MB} MB). Libera espacio: no se hizo el backup."
  fi
else
  echo "[WARN] No pude medir el espacio libre de '${BACKUP_DIR}' (df). Sigo igual." >&2
fi

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql.gz"
TMP_FILE="${BACKUP_FILE}.tmp"

echo "[$(date -Iseconds)] Iniciando backup consistente de '${DB_NAME}'..."

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  docker exec -e MYSQL_PWD="${DB_PASS_VAL}" "${CONTAINER_NAME}" \
    mysqldump --single-transaction --no-tablespaces --quick --routines --triggers \
    -u "${DB_USER_NAME}" "${DB_NAME}" | gzip -9 > "${TMP_FILE}"
else
  MYSQL_PWD="${DB_PASS_VAL}" mysqldump \
    --single-transaction --no-tablespaces --quick --routines --triggers \
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

# Copia fuera del servidor (obligatoria): si se rompe el disco, se pierden tambien los backups
# locales. Sin copia externa configurada el backup termina con error, para que el monitor avise.
if [[ -z "${OFFSITE_DIR}" && -z "${RCLONE_TARGET}" && "${SOLO_LOCAL}" != "1" ]]; then
  fallar "No hay copia fuera del servidor: configura RCLONE_REMOTE o BACKUP_OFFSITE_DIR en el .env (docs/DEPLOY.md, 7.1). El backup local quedo en ${BACKUP_FILE}."
fi

# Copia fuera del servidor: si esta configurada y NO llega, el script termina con error.
# El backup local se conserva igual. Un aviso [WARN] que nadie lee no sirve: la copia externa
# podria estar rota semanas sin que nadie se entere.

# Copia externa 1: carpeta sincronizada (Google Drive Desktop, OneDrive o NAS montado)
if [[ -n "${OFFSITE_DIR}" ]]; then
  [[ -d "${OFFSITE_DIR}" ]] || fallar "BACKUP_OFFSITE_DIR='${OFFSITE_DIR}' no es una carpeta accesible. El backup local quedo en ${BACKUP_FILE}."
  echo "[$(date -Iseconds)] Copiando a la carpeta externa '${OFFSITE_DIR}'..."
  cp -p "${BACKUP_FILE}" "${OFFSITE_DIR}/" || fallar "No se pudo copiar el backup a '${OFFSITE_DIR}'. El backup local quedo en ${BACKUP_FILE}."
  cmp -s "${BACKUP_FILE}" "${OFFSITE_DIR}/$(basename "${BACKUP_FILE}")" || fallar "La copia en '${OFFSITE_DIR}' no coincide con el backup local."
  find "${OFFSITE_DIR}" -maxdepth 1 -type f -name "${DB_NAME}_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete || true
  echo "[$(date -Iseconds)] Copia en carpeta externa OK."
fi

# Copia externa 2: remoto rclone (Google Drive, S3, B2...)
if [[ -n "${RCLONE_TARGET}" ]]; then
  command -v rclone >/dev/null 2>&1 || fallar "RCLONE_REMOTE='${RCLONE_TARGET}' esta configurado pero 'rclone' no esta instalado. El backup local quedo en ${BACKUP_FILE}."
  echo "[$(date -Iseconds)] Subiendo copia a '${RCLONE_TARGET}'..."
  rclone copy "${BACKUP_FILE}" "${RCLONE_TARGET}" || fallar "Fallo la subida a '${RCLONE_TARGET}'. El backup local quedo en ${BACKUP_FILE}."
  # Confirmar que el archivo esta del otro lado y pesa lo mismo
  rclone check "${BACKUP_DIR}" "${RCLONE_TARGET}" --one-way --size-only     --include "$(basename "${BACKUP_FILE}")" >/dev/null 2>&1     || fallar "El backup no aparece completo en '${RCLONE_TARGET}' despues de subirlo."
  echo "[$(date -Iseconds)] Copia en '${RCLONE_TARGET}' verificada OK."

  # Poda del remoto (opcional): sin esto los backups se acumulan para siempre en Drive.
  # Solo toca archivos con el nombre de los backups de esta base, en la carpeta de arriba.
  if [[ -n "${RCLONE_RETENCION}" ]]; then
    if rclone delete "${RCLONE_TARGET}" --min-age "${RCLONE_RETENCION}d" --max-depth 1 \
         --include "${DB_NAME}_*.sql.gz" >/dev/null 2>&1; then
      echo "[$(date -Iseconds)] Remoto podado: se borraron los backups de mas de ${RCLONE_RETENCION} dias."
    else
      echo "[WARN] No se pudieron borrar los backups viejos en '${RCLONE_TARGET}'. El backup de hoy esta OK." >&2
    fi
  fi
fi

echo "[$(date -Iseconds)] Backup terminado OK."
