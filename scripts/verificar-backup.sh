#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Comprobar que un backup SIRVE (sin tocar la base real)
# Restaura el backup en un MySQL 8 temporal y separado (otro contenedor, sin puertos
# publicados), muestra cuantas filas tiene cada tabla importante y borra el contenedor.
#
# Uso:
#   ./scripts/verificar-backup.sh                         # el ultimo de backups/
#   ./scripts/verificar-backup.sh backups/atlas_gym_AAAAMMDD_HHMMSS.sql.gz
#
# Termina con error si el archivo no existe, esta vacio, no se descomprime o la
# restauracion falla. Recomendado: una vez por mes (docs/DEPLOY.md, seccion 7).
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-${PROJECT_DIR}/backups}"
IMAGEN="${VERIFICAR_IMAGEN_MYSQL:-mysql:8.0}"
TABLAS=(usuarios alumnos planes pagos movimientos_caja asistencias)

fallar() {
  echo "[ERROR] $1" >&2
  exit 1
}

ARCHIVO="${1:-}"
if [[ -z "${ARCHIVO}" ]]; then
  # Solo los backups diarios (no los pre_restore_* que deja restore-mysql.sh)
  ARCHIVO="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name "${MYSQL_DATABASE:-atlas_gym}_*.sql.gz" \
    -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2-)"
  [[ -n "${ARCHIVO}" ]] || fallar "No hay backups diarios en ${BACKUP_DIR}."
fi

[[ -f "${ARCHIVO}" ]] || fallar "No existe el archivo '${ARCHIVO}'."
TAMANO="$(wc -c < "${ARCHIVO}")"
[[ "${TAMANO}" -ge 100 ]] || fallar "El backup '${ARCHIVO}' esta vacio o truncado (${TAMANO} bytes)."
gzip -t "${ARCHIVO}" >/dev/null 2>&1 || fallar "El backup '${ARCHIVO}' no se puede descomprimir (gzip corrupto o truncado)."
command -v docker >/dev/null 2>&1 || fallar "Hace falta Docker para levantar el MySQL temporal."

echo "[$(date -Iseconds)] Verificando ${ARCHIVO} (${TAMANO} bytes)..."

CONTENEDOR="atlas-verificar-backup-$$"
CLAVE="$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
BASE="atlas_verificacion"

limpiar() {
  docker rm -f "${CONTENEDOR}" >/dev/null 2>&1 || true
}
trap limpiar EXIT

# Sin -p: no publica puertos, asi nunca choca con la base real ni queda expuesto.
docker run -d --name "${CONTENEDOR}" \
  -e MYSQL_ROOT_PASSWORD="${CLAVE}" -e MYSQL_DATABASE="${BASE}" \
  "${IMAGEN}" --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci \
  --default-time-zone=-03:00 >/dev/null \
  || fallar "No se pudo levantar el MySQL temporal (${IMAGEN})."

# Se espera por TCP: durante la inicializacion MySQL arranca un servidor provisorio sin red,
# y un ping por socket daria "listo" antes de tiempo.
listo=false
for _ in $(seq 1 90); do
  if docker exec -e MYSQL_PWD="${CLAVE}" "${CONTENEDOR}" \
       mysql -h 127.0.0.1 -u root -N -e "SELECT 1" "${BASE}" >/dev/null 2>&1; then
    listo=true; break
  fi
  sleep 2
done
[[ "${listo}" == true ]] || fallar "El MySQL temporal no arranco a tiempo."

echo "[$(date -Iseconds)] Restaurando en el MySQL temporal..."
gunzip -c "${ARCHIVO}" | docker exec -i -e MYSQL_PWD="${CLAVE}" "${CONTENEDOR}" \
  mysql -h 127.0.0.1 -u root "${BASE}" \
  || fallar "La restauracion del backup fallo: el archivo no es un backup valido de la base."

CONTEOS=()
for tabla in "${TABLAS[@]}"; do
  n="$(docker exec -e MYSQL_PWD="${CLAVE}" "${CONTENEDOR}" \
        mysql -h 127.0.0.1 -u root -N -e "SELECT COUNT(*) FROM \`${tabla}\`" "${BASE}" 2>/dev/null)" \
    || fallar "El backup no tiene la tabla '${tabla}'."
  printf '  %-18s %s filas\n' "${tabla}" "${n}"
  CONTEOS+=("${n}")
done

[[ "${CONTEOS[0]}" -gt 0 ]] || fallar "El backup no tiene usuarios: no sirve para levantar el sistema."

# Linea para comparar en el CI (mismo orden que TABLAS)
echo "CONTEOS ${CONTEOS[*]}"
echo "[$(date -Iseconds)] Backup verificado OK: se restaura completo."
