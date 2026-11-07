#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Instalar, actualizar o volver atrás el sistema en el servidor
#
# Uso (desde la carpeta del back, en el servidor):
#   ./scripts/actualizar.sh                     # backup + git pull de back y front + rebuild
#   ./scripts/actualizar.sh --version v1.2.0    # backup + checkout del tag en back y front + rebuild
#   ./scripts/actualizar.sh --volver            # vuelve a la versión guardada en .version-anterior
#   ./scripts/actualizar.sh --sin-git           # igual, pero sin tocar git (lo usa el ensayo de CI)
#
# Pasos:
#   1. Revisa los argumentos, el .env y (si corresponde) que el tag exista en ambos repos.
#   2. Si la base ya está corriendo, hace un backup ANTES de tocar nada.
#   3. Guarda la versión actual en .version-anterior y actualiza/cambia el código de back y front.
#   4. Reconstruye y levanta los contenedores, pasando el commit del front para el login.
#   5. Espera a que backend y frontend respondan.
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

paso() { echo; echo "==> $*"; }
falla() { echo "ERROR: $*" >&2; exit 1; }
aviso() { echo "AVISO: $*" >&2; }
uso() { falla "Uso: $0 [--sin-git | --version vMAYOR.MENOR.PARCHE | --volver]"; }

MODO="pull"
VERSION_DESTINO=""
VERSION_ANTERIOR_FILE="${PROJECT_DIR}/.version-anterior"

case "${1:-}" in
  "")
    [[ $# -eq 0 ]] || uso
    MODO="pull"
    ;;
  "--sin-git")
    [[ $# -eq 1 ]] || uso
    MODO="sin-git"
    ;;
  "--version")
    [[ $# -eq 2 && -n "${2:-}" ]] || falla "Indicá el tag de la versión. Ejemplo: $0 --version v1.0.0"
    MODO="version"
    VERSION_DESTINO="$2"
    ;;
  --version=*)
    [[ $# -eq 1 ]] || uso
    MODO="version"
    VERSION_DESTINO="${1#--version=}"
    [[ -n "${VERSION_DESTINO}" ]] || falla "Indicá el tag de la versión. Ejemplo: $0 --version v1.0.0"
    ;;
  "--volver")
    [[ $# -eq 1 ]] || uso
    MODO="volver"
    if [[ ! -s "${VERSION_ANTERIOR_FILE}" ]]; then
      falla "No existe '${VERSION_ANTERIOR_FILE}' (o está vacío): no hay una versión anterior registrada a la cual volver."
    fi
    VERSION_DESTINO="$(tr -d '[:space:]' < "${VERSION_ANTERIOR_FILE}")"
    [[ -n "${VERSION_DESTINO}" ]] || falla "El archivo '${VERSION_ANTERIOR_FILE}' está vacío: no hay una versión anterior registrada."
    ;;
  *)
    uso
    ;;
esac

version_actual_repo() {
  git describe --tags --exact-match HEAD 2>/dev/null \
    || git symbolic-ref --short -q HEAD 2>/dev/null \
    || git rev-parse --short HEAD 2>/dev/null \
    || true
}

verificar_tag_en_ambos_repos() {
  local tag="$1"
  paso "Verificando que la versión '${tag}' exista en back y front"

  if git remote | grep -q .; then
    git fetch --tags --quiet
  fi
  if git -C "${FRONT_DIR}" remote | grep -q .; then
    git -C "${FRONT_DIR}" fetch --tags --quiet
  fi

  if [[ "${MODO}" == "version" && ! "${tag}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    if ! git rev-parse -q --verify "refs/tags/${tag}" >/dev/null 2>&1; then
      falla "El tag '${tag}' no existe en el repositorio del backend (formato esperado: vMAYOR.MENOR.PARCHE, ej. v1.0.0). No se tocó nada."
    fi
    falla "El tag '${tag}' debe tener formato vMAYOR.MENOR.PARCHE (ej. v1.0.0). No se tocó nada."
  fi

  if [[ "${MODO}" == "volver" && ! "${tag}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    # Si la versión previa guardada era una rama o commit anterior al primer tag, verificar que exista en ambos repos.
    if ! git rev-parse -q --verify "${tag}^{commit}" >/dev/null 2>&1; then
      falla "La versión '${tag}' no existe en el repositorio del backend. No se tocó nada."
    fi
    if ! git -C "${FRONT_DIR}" rev-parse -q --verify "${tag}^{commit}" >/dev/null 2>&1; then
      falla "La versión '${tag}' no existe en el repositorio del frontend ('${FRONT_DIR}'). No se tocó nada."
    fi
    return 0
  fi

  if ! git rev-parse -q --verify "refs/tags/${tag}" >/dev/null 2>&1; then
    falla "El tag '${tag}' no existe en el repositorio del backend. No se tocó nada."
  fi
  if ! git -C "${FRONT_DIR}" rev-parse -q --verify "refs/tags/${tag}" >/dev/null 2>&1; then
    falla "El tag '${tag}' no existe en el repositorio del frontend ('${FRONT_DIR}'). No se tocó nada."
  fi
}

# --- 1. Revisar el .env ------------------------------------------------------
paso "Revisando .env"
[[ -f .env ]] || falla "No existe .env. Copiá .env.example a .env y completalo (ver docs/DEPLOY.md)."

# Leer el .env como datos (mismo criterio que backup-mysql.sh: nunca con "source").
while IFS= read -r linea || [[ -n "${linea}" ]]; do
  linea="${linea%$'\r'}"
  [[ -z "${linea}" || "${linea}" =~ ^[[:space:]]*# || "${linea}" != *=* ]] && continue
  clave="${linea%%=*}"
  valor="${linea#*=}"
  [[ "${clave}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
  if [[ "${valor}" =~ ^\"(.*)\"$ || "${valor}" =~ ^\'(.*)\'$ ]]; then valor="${BASH_REMATCH[1]}"; fi
  export "${clave}=${valor}"
done < .env

if grep -qE '=(.*)(CAMBIAR_|REEMPLAZAR_)' .env; then
  grep -nE '=(.*)(CAMBIAR_|REEMPLAZAR_)' .env | cut -d= -f1 | sed 's/^/   linea /' >&2
  falla "Quedaron valores de ejemplo (CAMBIAR_/REEMPLAZAR_) en el .env. Reemplazalos por valores reales."
fi

[[ -n "${ATLAS_TOTEM_SECRET:-}" ]] || falla "ATLAS_TOTEM_SECRET está vacío: cualquiera podría marcar asistencias. Generalo con: openssl rand -hex 24"

if [[ -n "${ATLAS_TOTEM_PIN:-}" ]]; then
  [[ "${ATLAS_TOTEM_PIN}" =~ ^[0-9]{4}$ ]] || falla "ATLAS_TOTEM_PIN debe tener exactamente 4 dígitos."
  [[ "${ATLAS_TOTEM_PIN}" != "1234" ]] || aviso "ATLAS_TOTEM_PIN es 1234 (el de fábrica). Conviene elegir otro."
else
  aviso "ATLAS_TOTEM_PIN no está definido: el tótem va a usar el PIN de fábrica 1234."
fi

if [[ "${ATLAS_ADMIN_BOOTSTRAP_ENABLED:-false}" == "true" && -n "${ATLAS_ADMIN_PASSWORD:-}" ]]; then
  aviso "El alta inicial de titulares sigue activa. Después del primer ingreso, apagala (docs/DEPLOY.md, paso 5)."
fi

FRONT_DIR="${ATLAS_FRONT_DIR:-../atlasgym_front}"
[[ -d "${FRONT_DIR}" ]] || falla "No encuentro el front en '${FRONT_DIR}' (ATLAS_FRONT_DIR)."

if [[ "${MODO}" == "version" || "${MODO}" == "volver" ]]; then
  verificar_tag_en_ambos_repos "${VERSION_DESTINO}"
fi

# --- 2. Backup previo ---------------------------------------------------------
BACKUP_SEARCH_DIR="${BACKUP_DIR:-backups}"
DB_BACKUP_PREFIX="${MYSQL_DATABASE:-atlas_gym}"
BACKUP_PRE_CAMBIO=""
if [[ -d "${BACKUP_SEARCH_DIR}" ]]; then
  BACKUP_PRE_CAMBIO="$(find "${BACKUP_SEARCH_DIR}" -maxdepth 1 -type f -name "${DB_BACKUP_PREFIX}_*.sql.gz" 2>/dev/null | sort | tail -n 1 || true)"
fi

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${MYSQL_CONTAINER_NAME:-atlas-mysql}"; then
  paso "Backup previo de la base"
  bash "${SCRIPT_DIR}/backup-mysql.sh"
else
  paso "La base todavía no está corriendo (primera instalación): no hay nada que respaldar"
fi

ULTIMO_BACKUP=""
if [[ -d "${BACKUP_SEARCH_DIR}" ]]; then
  ULTIMO_BACKUP="$(find "${BACKUP_SEARCH_DIR}" -maxdepth 1 -type f -name "${DB_BACKUP_PREFIX}_*.sql.gz" 2>/dev/null | sort | tail -n 1 || true)"
fi

mostrar_aviso_migracion_volver() {
  local ruta_mostrar="${BACKUP_PRE_CAMBIO:-${ULTIMO_BACKUP:-backups/${DB_BACKUP_PREFIX}_AAAAMMDD_HHMMSS.sql.gz}}"
  echo
  aviso "Si la versión nueva traía una migración de base de datos, hay que restaurar el backup que el script hizo justo antes:"
  aviso "  ./scripts/restore-mysql.sh ${ruta_mostrar}"
  if [[ -n "${ULTIMO_BACKUP}" && "${ULTIMO_BACKUP}" != "${ruta_mostrar}" ]]; then
    aviso "  (Backup tomado recién antes de volver: ${ULTIMO_BACKUP})"
  fi
}

# --- 3. Código nuevo -----------------------------------------------------------
if [[ "${MODO}" == "version" || "${MODO}" == "volver" ]]; then
  VERSION_ACTUAL_ANTES="$(version_actual_repo)"
  if [[ -n "${VERSION_ACTUAL_ANTES}" ]]; then
    printf '%s\n' "${VERSION_ACTUAL_ANTES}" > "${VERSION_ANTERIOR_FILE}"
    echo "Versión anterior guardada en .version-anterior: ${VERSION_ACTUAL_ANTES}"
  fi

  if [[ "${MODO}" == "volver" ]]; then
    paso "Volviendo a la versión '${VERSION_DESTINO}' en back y front"
  else
    paso "Cambiando a la versión '${VERSION_DESTINO}' en back y front"
  fi

  git -c advice.detachedHead=false checkout "${VERSION_DESTINO}"
  git -C "${FRONT_DIR}" -c advice.detachedHead=false checkout "${VERSION_DESTINO}"

  if [[ "${MODO}" == "volver" ]]; then
    mostrar_aviso_migracion_volver
  fi
elif [[ "${MODO}" == "pull" ]]; then
  VERSION_ACTUAL_ANTES="$(version_actual_repo)"
  if [[ -n "${VERSION_ACTUAL_ANTES}" ]]; then
    printf '%s\n' "${VERSION_ACTUAL_ANTES}" > "${VERSION_ANTERIOR_FILE}"
  fi
  paso "Trayendo código nuevo (back: $(git rev-parse --abbrev-ref HEAD), front: $(git -C "${FRONT_DIR}" rev-parse --abbrev-ref HEAD))"
  git pull --ff-only
  git -C "${FRONT_DIR}" pull --ff-only
else
  paso "Sin git pull (--sin-git)"
fi

VERSION_EN_CURSO="$(version_actual_repo)"
ATLAS_FRONT_COMMIT="$(git -C "${FRONT_DIR}" rev-parse --short HEAD 2>/dev/null || true)"
export ATLAS_FRONT_COMMIT
echo "Versión: ${VERSION_EN_CURSO:-?}"
echo "Back:    $(git rev-parse --short HEAD 2>/dev/null || echo '?')"
echo "Front:   ${ATLAS_FRONT_COMMIT:-?}"

# --- 4. Construir y levantar -------------------------------------------------------
paso "Construyendo y levantando contenedores (puede tardar varios minutos)"
docker compose up -d --build

# --- 5. Esperar a que responda -----------------------------------------------------
paso "Esperando a que el sistema responda"
BACK="http://127.0.0.1:${BACKEND_HOST_PORT:-8080}/api/planes"
FRONT="http://127.0.0.1:${FRONTEND_HOST_PORT:-3000}/login"
for _ in $(seq 1 90); do
  if curl -sf -o /dev/null "${BACK}" && curl -sf -o /dev/null "${FRONT}"; then
    echo "Listo: backend y frontend responden."
    docker image prune -f >/dev/null 2>&1 || true
    if [[ "${MODO}" == "volver" ]]; then
      mostrar_aviso_migracion_volver
    fi
    echo
    echo "Comprobá en el navegador: https://${ATLAS_DOMAIN:-localhost}/login (abajo debe decir el commit ${ATLAS_FRONT_COMMIT:-})."
    exit 0
  fi
  sleep 4
done

docker compose ps
docker compose logs --tail 60 backend frontend
falla "El sistema no respondió a tiempo. Revisá los logs de arriba (docs/DEPLOY.md, 'Si algo falla')."
