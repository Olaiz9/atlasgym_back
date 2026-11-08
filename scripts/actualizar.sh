#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Instalar o actualizar el sistema en el servidor (un solo comando)
#
# Uso (desde la carpeta del back, en el servidor):
#   ./scripts/actualizar.sh            # backup + git pull de back y front + rebuild
#   ./scripts/actualizar.sh --sin-git  # igual, pero sin git pull (lo usa el ensayo de CI)
#
# Pasos:
#   1. Revisa el .env: corta si quedaron valores de ejemplo o falta algo obligatorio.
#   2. Si la base ya esta corriendo, hace un backup ANTES de tocar nada.
#   3. Trae el codigo nuevo de los dos repos (rama que tenga cada uno, solo fast-forward).
#   4. Reconstruye y levanta los contenedores, pasando el commit del front para el login.
#   5. Espera a que backend y frontend respondan.
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

SIN_GIT=0
[[ "${1:-}" == "--sin-git" ]] && SIN_GIT=1

paso() { echo; echo "==> $*"; }
falla() { echo "ERROR: $*" >&2; exit 1; }
aviso() { echo "AVISO: $*" >&2; }

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

# --- 2. Backup previo ---------------------------------------------------------
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${MYSQL_CONTAINER_NAME:-atlas-mysql}"; then
  paso "Backup previo de la base"
  bash "${SCRIPT_DIR}/backup-mysql.sh"
else
  paso "La base todavía no está corriendo (primera instalación): no hay nada que respaldar"
fi

# --- 3. Código nuevo -----------------------------------------------------------
if [[ "${SIN_GIT}" == "0" ]]; then
  paso "Trayendo código nuevo (back: $(git rev-parse --abbrev-ref HEAD), front: $(git -C "${FRONT_DIR}" rev-parse --abbrev-ref HEAD))"
  git pull --ff-only
  git -C "${FRONT_DIR}" pull --ff-only
else
  paso "Sin git pull (--sin-git)"
fi

ATLAS_BACK_COMMIT="$(git rev-parse --short HEAD 2>/dev/null || true)"
export ATLAS_BACK_COMMIT
ATLAS_FRONT_COMMIT="$(git -C "${FRONT_DIR}" rev-parse --short HEAD 2>/dev/null || true)"
export ATLAS_FRONT_COMMIT
echo "Back:  ${ATLAS_BACK_COMMIT:-?}"
echo "Front: ${ATLAS_FRONT_COMMIT:-?}"

# --- 4. Construir y levantar -------------------------------------------------------
paso "Construyendo y levantando contenedores (puede tardar varios minutos)"
docker compose up -d --build

# --- 5. Esperar a que responda -----------------------------------------------------
paso "Esperando a que el sistema responda"
BACK="http://127.0.0.1:${BACKEND_HOST_PORT:-8080}/api/planes"
FRONT="http://127.0.0.1:${FRONTEND_HOST_PORT:-3000}/login"
for i in $(seq 1 90); do
  if curl -sf -o /dev/null "${BACK}" && curl -sf -o /dev/null "${FRONT}"; then
    echo "Listo: backend y frontend responden."
    docker image prune -f >/dev/null 2>&1 || true
    echo
    echo "Comprobá en el navegador: https://${ATLAS_DOMAIN:-localhost}/login (abajo debe decir el commit ${ATLAS_FRONT_COMMIT:-})."
    exit 0
  fi
  sleep 4
done

docker compose ps
docker compose logs --tail 60 backend frontend
falla "El sistema no respondió a tiempo. Revisá los logs de arriba (docs/DEPLOY.md, 'Si algo falla')."
