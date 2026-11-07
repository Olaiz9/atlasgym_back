#!/usr/bin/env bash
# ============================================================================
# ATLAS Gym - Preparar un servidor Ubuntu 22.04 / 24.04 desde cero (idempotente)
#
# Uso (como root en el servidor):
#   sudo ./scripts/preparar-servidor.sh                # SSH abierto en ufw, sin 80/443
#   sudo ./scripts/preparar-servidor.sh --abrir-web    # abre además 80 y 443 en ufw
#   ./scripts/preparar-servidor.sh --simular           # muestra qué haría sin cambiar nada
#
# Qué hace:
#   1. Zona horaria America/Argentina/Buenos_Aires.
#   2. Docker Engine + plugin compose, git, curl y rclone.
#   3. Swap de 2 GB (/swapfile) si el servidor no tiene swap activa.
#   4. Usuario 'atlas' en el grupo 'docker' y carpeta /opt/atlas de su propiedad.
#   5. Firewall ufw: puerto real de SSH por defecto (y 80/443 si se pasa --abrir-web).
#   6. Parches de seguridad automáticos (unattended-upgrades) y fail2ban para SSH.
#   7. SSH solo con llave (/etc/ssh/sshd_config.d/00-atlas-hardening.conf:
#      PasswordAuthentication no, PermitRootLogin prohibit-password), verificado con sshd -T,
#      ÚNICAMENTE si ya hay al menos una llave en authorized_keys de root o de atlas.
#   8. Resumen final: qué se instaló, qué ya estaba y qué falta hacer a mano.
# ============================================================================

set -euo pipefail

ABRIR_WEB=0
SIMULAR=0
SYSROOT="${ATLAS_SYSROOT:-}"

uso() {
  echo "Uso: sudo $0 [--abrir-web] [--simular]" >&2
  exit 1
}

for arg in "$@"; do
  case "${arg}" in
    --abrir-web)
      ABRIR_WEB=1
      ;;
    --simular)
      SIMULAR=1
      ;;
    -h|--help)
      echo "Uso: sudo $0 [--abrir-web] [--simular]"
      exit 0
      ;;
    *)
      echo "ERROR: Argumento no reconocido: '${arg}'" >&2
      uso
      ;;
  esac
done

paso() { echo; echo "==> $*"; }
info() { echo "    $*"; }
aviso() { echo "AVISO: $*" >&2; }
falla() { echo "ERROR: $*" >&2; exit 1; }

if [[ "${SIMULAR}" -eq 0 && "${EUID}" -ne 0 && -z "${SYSROOT}" ]]; then
  falla "Este script debe ejecutarse como root (ej.: sudo $0). Para probar sin tocar nada usá --simular."
fi

if [[ "${SIMULAR}" -eq 1 ]]; then
  echo "============================================================================"
  echo " MODO SIMULACIÓN (--simular): no se modificará nada en el sistema"
  echo "============================================================================"
fi

INSTALADO=()
YA_ESTABA=()
FALTA_MANUAL=()

APT_ACTUALIZADO=0
apt_actualizar_si_hace_falta() {
  if [[ "${APT_ACTUALIZADO}" -eq 0 ]]; then
    DEBIAN_FRONTEND=noninteractive apt-get update -qq
    APT_ACTUALIZADO=1
  fi
}

SSHD_CONFIG_FILE="${ATLAS_SSHD_CONFIG:-/etc/ssh/sshd_config}"

tiene_sshd_real() {
  if [[ -n "${SYSROOT}" && -z "${ATLAS_SSHD_CONFIG:-}" ]]; then
    return 1
  fi
  command -v sshd >/dev/null 2>&1 && [[ -f "${SSHD_CONFIG_FILE}" ]]
}

ejecutar_sshd() {
  if [[ -n "${ATLAS_SSHD_CONFIG:-}" ]]; then
    sshd -f "${ATLAS_SSHD_CONFIG}" "$@"
  else
    sshd "$@"
  fi
}

obtener_puerto_ssh() {
  local p=""
  if tiene_sshd_real; then
    mkdir -p /run/sshd 2>/dev/null || true
    p="$(ejecutar_sshd -T 2>/dev/null | awk '/^port /{print $2; exit}' || true)"
  fi
  if [[ ! "${p}" =~ ^[0-9]+$ ]]; then
    p="22"
  fi
  printf '%s\n' "${p}"
}

sshd_efectivo_endurecido() {
  local salida pass_auth permit_root
  mkdir -p /run/sshd 2>/dev/null || true
  salida="$(ejecutar_sshd -T 2>/dev/null || true)"
  [[ -n "${salida}" ]] || return 1
  pass_auth="$(awk '/^passwordauthentication /{print tolower($2); exit}' <<< "${salida}")"
  permit_root="$(awk '/^permitrootlogin /{print tolower($2); exit}' <<< "${salida}")"
  [[ "${pass_auth}" == "no" ]] && [[ "${permit_root}" == "without-password" || "${permit_root}" == "prohibit-password" ]]
}

# Verificar sistema operativo
OS_RELEASE_FILE="${SYSROOT}/etc/os-release"
if [[ -f "${OS_RELEASE_FILE}" ]]; then
  # shellcheck disable=SC1090,SC1091
  . "${OS_RELEASE_FILE}"
  if [[ "${ID:-}" != "ubuntu" ]]; then
    aviso "Este script está pensado para Ubuntu 22.04/24.04 (detectado: ${PRETTY_NAME:-desconocido})."
  fi
fi

# --- 1. Zona horaria ---------------------------------------------------------
ZONA_OBJETIVO="America/Argentina/Buenos_Aires"
paso "1/7 Zona horaria (${ZONA_OBJETIVO})"

TIMEZONE_FILE="${SYSROOT}/etc/timezone"
LOCALTIME_LINK="${SYSROOT}/etc/localtime"
ZONA_ACTUAL=""
if [[ -z "${SYSROOT}" ]] && command -v timedatectl >/dev/null 2>&1; then
  ZONA_ACTUAL="$(timedatectl show -p Timezone --value 2>/dev/null || true)"
fi
if [[ -z "${ZONA_ACTUAL}" && -f "${TIMEZONE_FILE}" ]]; then
  ZONA_ACTUAL="$(tr -d '[:space:]' < "${TIMEZONE_FILE}" || true)"
fi
if [[ -z "${ZONA_ACTUAL}" && -L "${LOCALTIME_LINK}" ]]; then
  ZONA_ACTUAL="$(readlink -f "${LOCALTIME_LINK}" 2>/dev/null | sed 's|^.*/zoneinfo/||' || true)"
fi

if [[ "${ZONA_ACTUAL}" == "${ZONA_OBJETIVO}" ]]; then
  info "Ya estaba configurada en ${ZONA_OBJETIVO}."
  YA_ESTABA+=("Zona horaria (${ZONA_OBJETIVO})")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Configuraría la zona horaria en ${ZONA_OBJETIVO} (actual: ${ZONA_ACTUAL:-desconocida})."
  INSTALADO+=("[simulado] Zona horaria (${ZONA_OBJETIVO})")
else
  if [[ -z "${SYSROOT}" ]] && command -v timedatectl >/dev/null 2>&1 && timedatectl set-timezone "${ZONA_OBJETIVO}" 2>/dev/null; then
    :
  else
    mkdir -p "$(dirname "${TIMEZONE_FILE}")"
    if [[ -f "/usr/share/zoneinfo/${ZONA_OBJETIVO}" ]]; then
      ln -snf "/usr/share/zoneinfo/${ZONA_OBJETIVO}" "${LOCALTIME_LINK}"
    fi
    echo "${ZONA_OBJETIVO}" > "${TIMEZONE_FILE}"
  fi
  info "Zona horaria configurada en ${ZONA_OBJETIVO}."
  INSTALADO+=("Zona horaria (${ZONA_OBJETIVO})")
fi

# --- 2. Herramientas base y Docker -------------------------------------------
paso "2/7 Herramientas base (git, curl, rclone) y Docker Engine + Compose"

FALTAN_BASE=()
for cmd in curl git rclone; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    FALTAN_BASE+=("${cmd}")
  fi
done

if [[ "${#FALTAN_BASE[@]}" -eq 0 ]]; then
  info "git, curl y rclone ya están instalados."
  YA_ESTABA+=("Paquetes base (git, curl, rclone)")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Instalaría paquetes faltantes: ${FALTAN_BASE[*]}."
  INSTALADO+=("[simulado] Paquetes base (${FALTAN_BASE[*]})")
else
  apt_actualizar_si_hace_falta
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ca-certificates curl git rclone
  info "Instalados: ${FALTAN_BASE[*]}."
  INSTALADO+=("Paquetes base (${FALTAN_BASE[*]})")
fi

if command -v docker >/dev/null 2>&1 && docker --version >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  info "Docker Engine y el plugin 'docker compose' ya están instalados."
  if [[ "${SIMULAR}" -eq 0 ]]; then
    systemctl enable --now docker >/dev/null 2>&1 || service docker start >/dev/null 2>&1 || true
  fi
  YA_ESTABA+=("Docker Engine + plugin docker compose")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Instalaría Docker Engine y el plugin docker compose usando el script oficial (https://get.docker.com)."
  INSTALADO+=("[simulado] Docker Engine + plugin docker compose")
else
  if ! command -v docker >/dev/null 2>&1 || ! docker --version >/dev/null 2>&1; then
    info "Instalando Docker Engine con el script oficial..."
    curl -fsSL https://get.docker.com | sh
  fi
  if ! docker compose version >/dev/null 2>&1; then
    apt_actualizar_si_hace_falta
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker-compose-plugin
  fi
  systemctl enable --now docker >/dev/null 2>&1 || service docker start >/dev/null 2>&1 || true
  info "Docker Engine y docker compose listos."
  INSTALADO+=("Docker Engine + plugin docker compose")
fi

# --- 3. Swap de 2 GB ---------------------------------------------------------
paso "3/7 Memoria de intercambio (swap de 2 GB)"

SWAPFILE_PATH="${SYSROOT}/swapfile"
FSTAB_PATH="${SYSROOT}/etc/fstab"
PROC_SWAPS_PATH="${SYSROOT}/proc/swaps"

SWAP_ACTIVA=0
if [[ -z "${SYSROOT}" ]] && command -v swapon >/dev/null 2>&1 && swapon --show --noheadings 2>/dev/null | grep -q .; then
  SWAP_ACTIVA=1
elif [[ -f "${PROC_SWAPS_PATH}" ]] && awk 'NR>1 {found=1} END {exit !found}' "${PROC_SWAPS_PATH}" 2>/dev/null; then
  SWAP_ACTIVA=1
elif [[ -f "${SWAPFILE_PATH}" && -f "${FSTAB_PATH}" ]] && grep -qE '^[[:space:]]*/swapfile[[:space:]]' "${FSTAB_PATH}" 2>/dev/null; then
  SWAP_ACTIVA=1
fi

if [[ "${SWAP_ACTIVA}" -eq 1 ]]; then
  info "El servidor ya tiene swap activa."
  YA_ESTABA+=("Swap activa")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Crearía /swapfile de 2 GB, lo activaría y lo registraría en /etc/fstab."
  INSTALADO+=("[simulado] Swap de 2 GB (/swapfile)")
else
  if [[ ! -f "${SWAPFILE_PATH}" ]]; then
    if ! fallocate -l 2G "${SWAPFILE_PATH}" 2>/dev/null; then
      dd if=/dev/zero of="${SWAPFILE_PATH}" bs=1M count=2048 status=none
    fi
  fi
  chmod 600 "${SWAPFILE_PATH}"
  mkswap "${SWAPFILE_PATH}" >/dev/null
  if ! swapon "${SWAPFILE_PATH}" 2>/dev/null; then
    aviso "No se pudo activar /swapfile en vivo (¿entorno sin privilegios de kernel?). Queda creado y registrado en /etc/fstab."
  fi
  mkdir -p "$(dirname "${FSTAB_PATH}")"
  touch "${FSTAB_PATH}"
  if ! grep -qE '^[[:space:]]*/swapfile[[:space:]]' "${FSTAB_PATH}"; then
    echo "/swapfile none swap sw 0 0" >> "${FSTAB_PATH}"
  fi
  info "Swap de 2 GB configurada en /swapfile."
  INSTALADO+=("Swap de 2 GB (/swapfile)")
fi

# --- 4. Usuario 'atlas' y carpeta /opt/atlas ---------------------------------
paso "4/7 Usuario 'atlas' y carpeta /opt/atlas"

USUARIO_ATLAS="atlas"
CARPETA_ATLAS="${SYSROOT}/opt/atlas"

if id -u "${USUARIO_ATLAS}" >/dev/null 2>&1; then
  info "El usuario '${USUARIO_ATLAS}' ya existe."
  YA_ESTABA+=("Usuario '${USUARIO_ATLAS}'")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Crearía el usuario '${USUARIO_ATLAS}' con home y shell /bin/bash."
  INSTALADO+=("[simulado] Usuario '${USUARIO_ATLAS}'")
else
  useradd -m -s /bin/bash "${USUARIO_ATLAS}"
  info "Usuario '${USUARIO_ATLAS}' creado."
  INSTALADO+=("Usuario '${USUARIO_ATLAS}'")
fi

if id -nG "${USUARIO_ATLAS}" 2>/dev/null | tr ' ' '\n' | grep -qx "docker"; then
  info "El usuario '${USUARIO_ATLAS}' ya pertenece al grupo 'docker'."
  YA_ESTABA+=("Usuario '${USUARIO_ATLAS}' en grupo docker")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Agregaría al usuario '${USUARIO_ATLAS}' al grupo 'docker'."
  INSTALADO+=("[simulado] Usuario '${USUARIO_ATLAS}' en grupo docker")
else
  groupadd -f docker
  usermod -aG docker "${USUARIO_ATLAS}"
  info "Usuario '${USUARIO_ATLAS}' agregado al grupo 'docker'."
  INSTALADO+=("Usuario '${USUARIO_ATLAS}' en grupo docker")
fi

DUENIO_ACTUAL="$(stat -c '%U:%G' "${CARPETA_ATLAS}" 2>/dev/null || true)"
if [[ -d "${CARPETA_ATLAS}" && "${DUENIO_ACTUAL}" == "${USUARIO_ATLAS}:${USUARIO_ATLAS}" ]]; then
  info "La carpeta /opt/atlas ya existe y pertenece a ${USUARIO_ATLAS}:${USUARIO_ATLAS}."
  YA_ESTABA+=("Carpeta /opt/atlas (${USUARIO_ATLAS}:${USUARIO_ATLAS})")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Crearía /opt/atlas con propietario ${USUARIO_ATLAS}:${USUARIO_ATLAS}."
  INSTALADO+=("[simulado] Carpeta /opt/atlas (${USUARIO_ATLAS}:${USUARIO_ATLAS})")
else
  mkdir -p "${CARPETA_ATLAS}"
  chown -R "${USUARIO_ATLAS}:${USUARIO_ATLAS}" "${CARPETA_ATLAS}"
  info "Carpeta /opt/atlas lista (propietario ${USUARIO_ATLAS}:${USUARIO_ATLAS})."
  INSTALADO+=("Carpeta /opt/atlas (${USUARIO_ATLAS}:${USUARIO_ATLAS})")
fi

# --- 5. Firewall ufw ---------------------------------------------------------
PUERTO_SSH="$(obtener_puerto_ssh)"
if [[ "${ABRIR_WEB}" -eq 1 ]]; then
  DESC_UFW="SSH (${PUERTO_SSH}/tcp) + puertos 80 y 443 (--abrir-web)"
else
  DESC_UFW="solo SSH (${PUERTO_SSH}/tcp, sin 80/443)"
fi
paso "5/7 Firewall ufw (${DESC_UFW})"

ESTADO_UFW=""
if command -v ufw >/dev/null 2>&1; then
  ESTADO_UFW="$(LANG=C ufw status 2>/dev/null || true)"
fi

UFW_YA_LISTO=0
if echo "${ESTADO_UFW}" | grep -q "^Status: active" && echo "${ESTADO_UFW}" | grep -qE "${PUERTO_SSH}/tcp[[:space:]]+ALLOW"; then
  if [[ "${ABRIR_WEB}" -eq 1 ]]; then
    if echo "${ESTADO_UFW}" | grep -qE "80/tcp[[:space:]]+ALLOW" && echo "${ESTADO_UFW}" | grep -qE "443/tcp[[:space:]]+ALLOW"; then
      UFW_YA_LISTO=1
    fi
  else
    UFW_YA_LISTO=1
  fi
fi

if [[ "${UFW_YA_LISTO}" -eq 1 ]]; then
  info "ufw ya está activo con las reglas requeridas (${DESC_UFW})."
  YA_ESTABA+=("Firewall ufw (${DESC_UFW})")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Configuraría y activaría ufw permitiendo ${PUERTO_SSH}/tcp (${DESC_UFW})."
  INSTALADO+=("[simulado] Firewall ufw (${DESC_UFW})")
else
  if ! command -v ufw >/dev/null 2>&1; then
    apt_actualizar_si_hace_falta
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ufw
  fi
  ufw default deny incoming >/dev/null
  ufw default allow outgoing >/dev/null
  ufw allow "${PUERTO_SSH}/tcp" >/dev/null
  if [[ "${ABRIR_WEB}" -eq 1 ]]; then
    ufw allow 80/tcp >/dev/null
    ufw allow 443/tcp >/dev/null
  fi
  ufw --force enable >/dev/null
  info "Firewall ufw activo (${DESC_UFW})."
  INSTALADO+=("Firewall ufw (${DESC_UFW})")
fi

# --- 6. unattended-upgrades y fail2ban ---------------------------------------
paso "6/7 Parches automáticos (unattended-upgrades) y protección SSH (fail2ban)"

AUTO_UPGRADES_FILE="${SYSROOT}/etc/apt/apt.conf.d/20auto-upgrades"
if dpkg -s unattended-upgrades >/dev/null 2>&1 \
   && [[ -f "${AUTO_UPGRADES_FILE}" ]] \
   && grep -q 'APT::Periodic::Unattended-Upgrade "1";' "${AUTO_UPGRADES_FILE}"; then
  info "unattended-upgrades ya está instalado y activo."
  YA_ESTABA+=("Parches automáticos (unattended-upgrades)")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Instalaría unattended-upgrades y activaría actualizaciones automáticas en /etc/apt/apt.conf.d/20auto-upgrades."
  INSTALADO+=("[simulado] Parches automáticos (unattended-upgrades)")
else
  if ! dpkg -s unattended-upgrades >/dev/null 2>&1; then
    apt_actualizar_si_hace_falta
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq unattended-upgrades
  fi
  mkdir -p "$(dirname "${AUTO_UPGRADES_FILE}")"
  cat > "${AUTO_UPGRADES_FILE}" <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
  systemctl enable --now unattended-upgrades >/dev/null 2>&1 || true
  info "unattended-upgrades instalado y configurado."
  INSTALADO+=("Parches automáticos (unattended-upgrades)")
fi

FAIL2BAN_JAIL="${SYSROOT}/etc/fail2ban/jail.d/atlas-sshd.conf"
if dpkg -s fail2ban >/dev/null 2>&1 && [[ -f "${FAIL2BAN_JAIL}" ]]; then
  info "fail2ban ya está instalado y configurado para SSH (/etc/fail2ban/jail.d/atlas-sshd.conf)."
  YA_ESTABA+=("Protección SSH (fail2ban)")
elif [[ "${SIMULAR}" -eq 1 ]]; then
  info "[SIMULAR] Instalaría fail2ban y crearía /etc/fail2ban/jail.d/atlas-sshd.conf para proteger SSH."
  INSTALADO+=("[simulado] Protección SSH (fail2ban)")
else
  if ! dpkg -s fail2ban >/dev/null 2>&1; then
    apt_actualizar_si_hace_falta
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq fail2ban
  fi
  mkdir -p "$(dirname "${FAIL2BAN_JAIL}")"
  cat > "${FAIL2BAN_JAIL}" <<'EOF'
[sshd]
enabled = true
port = ssh
maxretry = 5
findtime = 10m
bantime = 1h
EOF
  systemctl enable --now fail2ban >/dev/null 2>&1 || service fail2ban restart >/dev/null 2>&1 || true
  info "fail2ban instalado y activo para SSH."
  INSTALADO+=("Protección SSH (fail2ban)")
fi

# --- 7. SSH solo con llave (únicamente si ya hay llave cargada) --------------
paso "7/7 Endurecimiento de SSH (solo con llave)"

ROOT_AUTH_KEYS="${ATLAS_ROOT_AUTHORIZED_KEYS:-${SYSROOT}/root/.ssh/authorized_keys}"
ATLAS_AUTH_KEYS="${ATLAS_USER_AUTHORIZED_KEYS:-${SYSROOT}/home/${USUARIO_ATLAS}/.ssh/authorized_keys}"

tiene_llave_ssh() {
  local archivo="$1"
  [[ -f "${archivo}" && -r "${archivo}" ]] || return 1
  grep -qE '^[[:space:]]*(ssh-(rsa|ed25519|ecdsa|dss)|ecdsa-sha2-[^[:space:]]+|sk-(ssh|ecdsa)-[^[:space:]]+)[[:space:]]+' "${archivo}"
}

HAY_LLAVE_ROOT=0
HAY_LLAVE_ATLAS=0
tiene_llave_ssh "${ROOT_AUTH_KEYS}" && HAY_LLAVE_ROOT=1
tiene_llave_ssh "${ATLAS_AUTH_KEYS}" && HAY_LLAVE_ATLAS=1

if [[ "${HAY_LLAVE_ROOT}" -eq 0 && "${HAY_LLAVE_ATLAS}" -eq 0 ]]; then
  echo
  aviso "============================================================================"
  aviso "NO se encontró ninguna llave pública SSH en /root/.ssh/authorized_keys"
  aviso "ni en /home/${USUARIO_ATLAS}/.ssh/authorized_keys."
  aviso "NO se modificó la configuración de SSH para evitar perder acceso al servidor."
  aviso "Cargá tu llave SSH primero y volvé a ejecutar este script."
  aviso "============================================================================"
  FALTA_MANUAL+=("Cargar al menos una llave pública SSH en /home/${USUARIO_ATLAS}/.ssh/authorized_keys (o /root/.ssh/authorized_keys) y volver a correr ./scripts/preparar-servidor.sh para deshabilitar el login SSH por contraseña.")
else
  # Si root tiene llave pero atlas todavía no, copiarla para que atlas también pueda entrar con llave.
  if [[ "${HAY_LLAVE_ROOT}" -eq 1 && "${HAY_LLAVE_ATLAS}" -eq 0 ]]; then
    if [[ "${SIMULAR}" -eq 1 ]]; then
      info "[SIMULAR] Copiaría las llaves de /root/.ssh/authorized_keys a /home/${USUARIO_ATLAS}/.ssh/authorized_keys para el usuario '${USUARIO_ATLAS}'."
    elif [[ -d "${SYSROOT}/home/${USUARIO_ATLAS}" ]]; then
      mkdir -p "$(dirname "${ATLAS_AUTH_KEYS}")"
      cp "${ROOT_AUTH_KEYS}" "${ATLAS_AUTH_KEYS}"
      chmod 700 "$(dirname "${ATLAS_AUTH_KEYS}")"
      chmod 600 "${ATLAS_AUTH_KEYS}"
      chown -R "${USUARIO_ATLAS}:${USUARIO_ATLAS}" "$(dirname "${ATLAS_AUTH_KEYS}")"
      info "Llaves SSH copiadas de root a /home/${USUARIO_ATLAS}/.ssh/authorized_keys."
    fi
  fi

  SSH_HARDENING_DIR="${SYSROOT}/etc/ssh/sshd_config.d"
  SSH_HARDENING_FILE="${ATLAS_SSHD_HARDENING_FILE:-${SSH_HARDENING_DIR}/00-atlas-hardening.conf}"
  SSH_OLD_HARDENING_FILE="${SSH_HARDENING_DIR}/99-atlas-hardening.conf"

  # Migración idempotente: eliminar 99-atlas-hardening.conf si existía de una versión previa
  if [[ -f "${SSH_OLD_HARDENING_FILE}" ]]; then
    if [[ "${SIMULAR}" -eq 1 ]]; then
      info "[SIMULAR] Eliminaría el archivo viejo ${SSH_OLD_HARDENING_FILE} (reemplazado por 00-atlas-hardening.conf)."
    else
      rm -f "${SSH_OLD_HARDENING_FILE}"
      info "Archivo obsoleto eliminado: ${SSH_OLD_HARDENING_FILE}."
    fi
  fi

  SSH_YA_ENDURECIDO=0
  if tiene_sshd_real; then
    if sshd_efectivo_endurecido; then
      SSH_YA_ENDURECIDO=1
    fi
  else
    info "No hay sshd real disponible (modo SYSROOT/simulación): se saltea la verificación con 'sshd -T'."
    if [[ -f "${SSH_HARDENING_FILE}" ]] \
       && grep -q "^PasswordAuthentication no$" "${SSH_HARDENING_FILE}" \
       && grep -q "^PermitRootLogin prohibit-password$" "${SSH_HARDENING_FILE}"; then
      SSH_YA_ENDURECIDO=1
    fi
  fi

  if [[ "${SSH_YA_ENDURECIDO}" -eq 1 ]]; then
    info "SSH ya estaba configurado solo con llave (verificado)."
    YA_ESTABA+=("SSH solo con llave (PasswordAuthentication no, PermitRootLogin prohibit-password)")
  elif [[ "${SIMULAR}" -eq 1 ]]; then
    info "[SIMULAR] Escribiría /etc/ssh/sshd_config.d/00-atlas-hardening.conf con 'PasswordAuthentication no' y 'PermitRootLogin prohibit-password', recargaría sshd y verificaría con 'sshd -T'."
    INSTALADO+=("[simulado] SSH solo con llave (/etc/ssh/sshd_config.d/00-atlas-hardening.conf)")
  else
    mkdir -p "$(dirname "${SSH_HARDENING_FILE}")"
    cat > "${SSH_HARDENING_FILE}" <<'EOF'
# Generado por scripts/preparar-servidor.sh (ATLAS Gym)
PasswordAuthentication no
PermitRootLogin prohibit-password
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
EOF
    if tiene_sshd_real; then
      mkdir -p /run/sshd 2>/dev/null || true
      if ! ejecutar_sshd -t 2>/dev/null; then
        rm -f "${SSH_HARDENING_FILE}"
        falla "La validación 'sshd -t' falló; se revirtió ${SSH_HARDENING_FILE} para no afectar SSH."
      fi
      systemctl reload ssh >/dev/null 2>&1 \
        || systemctl reload sshd >/dev/null 2>&1 \
        || service ssh reload >/dev/null 2>&1 \
        || true
      if ! sshd_efectivo_endurecido; then
        SALIDA_SSHD_T="$(ejecutar_sshd -T 2>/dev/null || true)"
        PASS_EFECTIVO="$(awk '/^passwordauthentication /{print tolower($2); exit}' <<< "${SALIDA_SSHD_T}")"
        ROOT_EFECTIVO="$(awk '/^permitrootlogin /{print tolower($2); exit}' <<< "${SALIDA_SSHD_T}")"
        ARCHIVOS_PISANDO="$(grep -il 'passwordauthentication' "${SSH_HARDENING_DIR}"/* 2>/dev/null | tr '\n' ' ' || true)"
        falla "La configuración efectiva de sshd ('sshd -T') sigue teniendo passwordauthentication=${PASS_EFECTIVO:-?} y permitrootlogin=${ROOT_EFECTIVO:-?}. Archivos con PasswordAuthentication en /etc/ssh/sshd_config.d/*: ${ARCHIVOS_PISANDO:-ninguno}"
      fi
    fi
    info "SSH configurado solo con llave (/etc/ssh/sshd_config.d/00-atlas-hardening.conf)."
    INSTALADO+=("SSH solo con llave (/etc/ssh/sshd_config.d/00-atlas-hardening.conf)")
  fi
fi

# --- 8. Pasos que siempre son manuales ---------------------------------------
if [[ "${ABRIR_WEB}" -eq 1 ]]; then
  FALTA_MANUAL+=("Apuntar el registro DNS A del dominio a la IP pública de este servidor (docs/DEPLOY.md, sección 2).")
else
  FALTA_MANUAL+=("Si el servidor va a exponer HTTPS directo con Caddy, correr de nuevo con --abrir-web y apuntar el DNS (docs/DEPLOY.md, sección 2 y 9).")
  FALTA_MANUAL+=("Ojo: Docker publica 80/443 de Caddy aunque ufw no los permita. Para cerrarlos de verdad (ej. con Cloudflare Tunnel) hay que dejar de publicarlos en docker-compose.yml.")
fi
FALTA_MANUAL+=("Entrar como '${USUARIO_ATLAS}' y clonar atlasgym_back y atlasgym_front en /opt/atlas (docs/DEPLOY.md, sección 3).")
FALTA_MANUAL+=("Completar /opt/atlas/atlasgym_back/.env y ejecutar ./scripts/actualizar.sh (docs/DEPLOY.md, secciones 4 y 5).")
FALTA_MANUAL+=("Configurar la copia externa de backups (rclone config o BACKUP_OFFSITE_DIR) y el cron diario (docs/DEPLOY.md, sección 7).")

# --- 9. Resumen final --------------------------------------------------------
echo
echo "============================================================================"
echo " RESUMEN DE PREPARACIÓN DEL SERVIDOR"
echo "============================================================================"

echo "Qué se instaló / configuró:"
if [[ "${#INSTALADO[@]}" -eq 0 ]]; then
  echo "  - (nada nuevo en esta corrida)"
else
  for item in "${INSTALADO[@]}"; do
    echo "  - ${item}"
  done
fi

echo
echo "Qué ya estaba listo (sin cambios):"
if [[ "${#YA_ESTABA[@]}" -eq 0 ]]; then
  echo "  - (nada previo)"
else
  for item in "${YA_ESTABA[@]}"; do
    echo "  - ${item}"
  done
fi

echo
echo "Qué falta hacer a mano:"
for item in "${FALTA_MANUAL[@]}"; do
  echo "  - ${item}"
done
echo "============================================================================"
