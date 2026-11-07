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
#   1. Revisa los argumentos, el .env y (si corresponde) que el tag o commit exista en ambos repos.
#   2. Si la base ya está corriendo, hace un backup ANTES de tocar nada y registra su ruta exacta.
#   3. Guarda las versiones previas (back, front) y la ruta del backup en .version-anterior, y cambia el código.
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
VERSION_BACK_DESTINO=""
VERSION_FRONT_DESTINO=""
BACKUP_GUARDADO_ANTERIOR=""
VERSION_ANTERIOR_FILE="${PROJECT_DIR}/.version-anterior"

leer_version_anterior() {
  if [[ ! -s "${VERSION_ANTERIOR_FILE}" ]]; then
    falla "No existe '${VERSION_ANTERIOR_FILE}' (o está vacío): no hay una versión anterior registrada a la cual volver."
  fi

  if grep -qE '^(back|front|backup)=' "${VERSION_ANTERIOR_FILE}"; then
    while IFS= read -r linea || [[ -n "${linea}" ]]; do
      linea="${linea%$'\r'}"
      [[ -z "${linea}" || "${linea}" != *=* ]] && continue
      local clave="${linea%%=*}"
      local valor="${linea#*=}"
      case "${clave}" in
        back)   VERSION_BACK_DESTINO="${valor}" ;;
        front)  VERSION_FRONT_DESTINO="${valor}" ;;
        backup) BACKUP_GUARDADO_ANTERIOR="${valor}" ;;
      esac
    done < "${VERSION_ANTERIOR_FILE}"
    [[ -n "${VERSION_BACK_DESTINO}" && -n "${VERSION_FRONT_DESTINO}" ]] \
      || falla "El archivo '${VERSION_ANTERIOR_FILE}' está incompleto: faltan 'back=' o 'front='."
  else
    local unica
    unica="$(tr -d '[:space:]' < "${VERSION_ANTERIOR_FILE}")"
    [[ -n "${unica}" ]] || falla "El archivo '${VERSION_ANTERIOR_FILE}' está vacío: no hay una versión anterior registrada."
    VERSION_BACK_DESTINO="${unica}"
    VERSION_FRONT_DESTINO="${unica}"
    BACKUP_GUARDADO_ANTERIOR=""
  fi
}

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
    VERSION_BACK_DESTINO="$2"
    VERSION_FRONT_DESTINO="$2"
    ;;
  --version=*)
    [[ $# -eq 1 ]] || uso
    MODO="version"
    VERSION_DESTINO="${1#--version=}"
    [[ -n "${VERSION_DESTINO}" ]] || falla "Indicá el tag de la versión. Ejemplo: $0 --version v1.0.0"
    VERSION_BACK_DESTINO="${VERSION_DESTINO}"
    VERSION_FRONT_DESTINO="${VERSION_DESTINO}"
    ;;
  "--volver")
    [[ $# -eq 1 ]] || uso
    MODO="volver"
    leer_version_anterior
    ;;
  *)
    uso
    ;;
esac

# Devuelve el tag exacto si HEAD coincide con un tag; si no, el SHA completo (nunca un nombre de rama).
version_para_guardar() {
  local repo_dir="${1:-.}"
  git -C "${repo_dir}" describe --tags --exact-match HEAD 2>/dev/null \
    || git -C "${repo_dir}" rev-parse HEAD 2>/dev/null \
    || true
}

version_actual_repo() {
  local repo_dir="${1:-.}"
  git -C "${repo_dir}" describe --tags --exact-match HEAD 2>/dev/null \
    || git -C "${repo_dir}" symbolic-ref --short -q HEAD 2>/dev/null \
    || git -C "${repo_dir}" rev-parse --short HEAD 2>/dev/null \
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

  if [[ ! "${tag}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    if ! git rev-parse -q --verify "refs/tags/${tag}" >/dev/null 2>&1; then
      falla "El tag '${tag}' no existe en el repositorio del backend (formato esperado: vMAYOR.MENOR.PARCHE, ej. v1.0.0). No se tocó nada."
    fi
    falla "El tag '${tag}' debe tener formato vMAYOR.MENOR.PARCHE (ej. v1.0.0). No se tocó nada."
  fi

  if ! git rev-parse -q --verify "refs/tags/${tag}" >/dev/null 2>&1; then
    falla "El tag '${tag}' no existe en el repositorio del backend. No se tocó nada."
  fi
  if ! git -C "${FRONT_DIR}" rev-parse -q --verify "refs/tags/${tag}" >/dev/null 2>&1; then
    falla "El tag '${tag}' no existe en el repositorio del frontend ('${FRONT_DIR}'). No se tocó nada."
  fi
}

verificar_destino_volver() {
  local ref_back="$1"
  local ref_front="$2"
  paso "Verificando versión anterior (back: '${ref_back}', front: '${ref_front}')"

  if git remote | grep -q .; then
    git fetch --tags --quiet
  fi
  if git -C "${FRONT_DIR}" remote | grep -q .; then
    git -C "${FRONT_DIR}" fetch --tags --quiet
  fi

  if ! git rev-parse -q --verify "${ref_back}^{commit}" >/dev/null 2>&1; then
    falla "La versión '${ref_back}' no existe en el repositorio del backend. No se tocó nada."
  fi
  if ! git -C "${FRONT_DIR}" rev-parse -q --verify "${ref_front}^{commit}" >/dev/null 2>&1; then
    falla "La versión '${ref_front}' no existe en el repositorio del frontend ('${FRONT_DIR}'). No se tocó nada."
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

if [[ "${MODO}" == "pull" ]]; then
  if ! git symbolic-ref -q HEAD >/dev/null 2>&1; then
    TAG_DETACHED="$(git describe --tags --exact-match HEAD 2>/dev/null || git rev-parse --short HEAD 2>/dev/null || echo 'detached')"
    falla "Estás en una versión etiquetada (${TAG_DETACHED}). Usá --version vX.Y.Z para actualizar."
  fi
  if ! git -C "${FRONT_DIR}" symbolic-ref -q HEAD >/dev/null 2>&1; then
    TAG_FRONT_DETACHED="$(git -C "${FRONT_DIR}" describe --tags --exact-match HEAD 2>/dev/null || git -C "${FRONT_DIR}" rev-parse --short HEAD 2>/dev/null || echo 'detached')"
    falla "Estás en una versión etiquetada (${TAG_FRONT_DETACHED}). Usá --version vX.Y.Z para actualizar."
  fi
elif [[ "${MODO}" == "version" ]]; then
  verificar_tag_en_ambos_repos "${VERSION_DESTINO}"
elif [[ "${MODO}" == "volver" ]]; then
  verificar_destino_volver "${VERSION_BACK_DESTINO}" "${VERSION_FRONT_DESTINO}"
fi

# --- 2. Backup previo ---------------------------------------------------------
BACKUP_SEARCH_DIR="${BACKUP_DIR:-backups}"
DB_BACKUP_PREFIX="${MYSQL_DATABASE:-atlas_gym}"
BACKUP_RECIEN_HECHO=""

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${MYSQL_CONTAINER_NAME:-atlas-mysql}"; then
  paso "Backup previo de la base"
  bash "${SCRIPT_DIR}/backup-mysql.sh"
  if [[ -d "${BACKUP_SEARCH_DIR}" ]]; then
    BACKUP_RECIEN_HECHO="$(find "${BACKUP_SEARCH_DIR}" -maxdepth 1 -type f -name "${DB_BACKUP_PREFIX}_*.sql.gz" 2>/dev/null | sort | tail -n 1 || true)"
  fi
else
  paso "La base todavía no está corriendo (primera instalación): no hay nada que respaldar"
fi

guardar_estado_version_anterior() {
  local back_ref="$1"
  local front_ref="$2"
  local backup_path="$3"
  if [[ -n "${back_ref}" && -n "${front_ref}" ]]; then
    {
      printf 'back=%s\n' "${back_ref}"
      printf 'front=%s\n' "${front_ref}"
      printf 'backup=%s\n' "${backup_path}"
    } > "${VERSION_ANTERIOR_FILE}"
    echo "Versión anterior guardada en .version-anterior (back=${back_ref}, front=${front_ref}, backup=${backup_path:-ninguno})"
  fi
}

mostrar_aviso_migracion_volver() {
  local ruta_mostrar="${BACKUP_GUARDADO_ANTERIOR:-backups/${DB_BACKUP_PREFIX}_AAAAMMDD_HHMMSS.sql.gz}"
  echo
  aviso "Si la versión nueva traía una migración de base de datos, hay que restaurar el backup que el script hizo justo antes:"
  aviso "  ./scripts/restore-mysql.sh ${ruta_mostrar}"
  aviso "Restaurar el backup previo BORRA los cobros, altas y asistencias registrados desde la actualización. Antes de restaurar, anotá o exportá lo que se cargó en ese lapso."
}

# --- 3. Código nuevo -----------------------------------------------------------
if [[ "${MODO}" == "version" || "${MODO}" == "volver" ]]; then
  BACK_ANTES="$(version_para_guardar ".")"
  FRONT_ANTES="$(version_para_guardar "${FRONT_DIR}")"
  guardar_estado_version_anterior "${BACK_ANTES}" "${FRONT_ANTES}" "${BACKUP_RECIEN_HECHO}"

  if [[ "${MODO}" == "volver" ]]; then
    paso "Volviendo a la versión anterior (back: '${VERSION_BACK_DESTINO}', front: '${VERSION_FRONT_DESTINO}')"
  else
    paso "Cambiando a la versión '${VERSION_DESTINO}' en back y front"
  fi

  git -c advice.detachedHead=false checkout "${VERSION_BACK_DESTINO}"
  git -C "${FRONT_DIR}" -c advice.detachedHead=false checkout "${VERSION_FRONT_DESTINO}"

  if [[ "${MODO}" == "volver" ]]; then
    mostrar_aviso_migracion_volver
  fi
elif [[ "${MODO}" == "pull" ]]; then
  BACK_ANTES="$(version_para_guardar ".")"
  FRONT_ANTES="$(version_para_guardar "${FRONT_DIR}")"
  guardar_estado_version_anterior "${BACK_ANTES}" "${FRONT_ANTES}" "${BACKUP_RECIEN_HECHO}"

  paso "Trayendo código nuevo (back: $(git rev-parse --abbrev-ref HEAD), front: $(git -C "${FRONT_DIR}" rev-parse --abbrev-ref HEAD))"
  git pull --ff-only
  git -C "${FRONT_DIR}" pull --ff-only
else
  paso "Sin git pull (--sin-git)"
fi

VERSION_EN_CURSO="$(version_actual_repo ".")"
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
