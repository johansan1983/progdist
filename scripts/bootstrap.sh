#!/usr/bin/env bash
# SuperChat bootstrap: instala Docker (si falta), arranca el daemon y levanta
# el stack completo. Pensado para una WSL2 / Linux Debian-Ubuntu desde cero.
#
# Uso:
#   ./scripts/bootstrap.sh           # install + up (default)
#   ./scripts/bootstrap.sh --no-up   # solo instalar deps, no levantar el stack
#
# El script es idempotente: se puede re-ejecutar sin romper nada.

set -euo pipefail

# ── Helpers ──────────────────────────────────────────────────────────────────

C_GREEN="\033[0;32m"
C_YELLOW="\033[0;33m"
C_RED="\033[0;31m"
C_CYAN="\033[0;36m"
C_RESET="\033[0m"

log()  { echo -e "${C_CYAN}==>${C_RESET} $*"; }
ok()   { echo -e "${C_GREEN}✓${C_RESET} $*"; }
warn() { echo -e "${C_YELLOW}!${C_RESET} $*"; }
err()  { echo -e "${C_RED}✗${C_RESET} $*" >&2; }

# Use sudo only if not already root
SUDO=""
if [[ $EUID -ne 0 ]]; then
  if ! command -v sudo >/dev/null 2>&1; then
    err "Se requiere sudo. Instálalo o ejecuta este script como root."
    exit 1
  fi
  SUDO="sudo"
fi

# Run docker either with sudo (if user not in docker group yet) or directly
docker_cmd() {
  if id -nG "$USER" 2>/dev/null | grep -qw docker || [[ $EUID -eq 0 ]]; then
    docker "$@"
  else
    $SUDO docker "$@"
  fi
}

compose_cmd() {
  if id -nG "$USER" 2>/dev/null | grep -qw docker || [[ $EUID -eq 0 ]]; then
    docker compose "$@"
  else
    $SUDO docker compose "$@"
  fi
}

# ── Parse args ───────────────────────────────────────────────────────────────

DO_UP=1
DO_UFW=0
for arg in "$@"; do
  case "$arg" in
    --no-up) DO_UP=0 ;;
    --open-firewall) DO_UFW=1 ;;
    -h|--help)
      sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
      echo
      echo "Flags adicionales:"
      echo "  --no-up           Solo instalar deps, no levantar el stack"
      echo "  --open-firewall   Si ufw esta activo, abre los puertos del stack"
      exit 0
      ;;
    *) warn "Argumento desconocido: $arg" ;;
  esac
done

# ── Step 1: validar OS ───────────────────────────────────────────────────────

log "Verificando sistema operativo..."
if ! command -v apt-get >/dev/null 2>&1; then
  err "Este script asume Debian/Ubuntu (apt-get). Instala Docker manualmente y vuelve a ejecutar con --no-install."
  exit 1
fi
. /etc/os-release 2>/dev/null || true
ok "Detectado: ${PRETTY_NAME:-Linux apt-based}"

# Detectar familia (ubuntu vs debian) para elegir el repo Docker correcto.
OS_FAMILY=""
case "${ID:-}" in
  ubuntu) OS_FAMILY=ubuntu ;;
  debian) OS_FAMILY=debian ;;
  *)
    case "${ID_LIKE:-}" in
      *ubuntu*) OS_FAMILY=ubuntu ;;
      *debian*) OS_FAMILY=debian ;;
    esac
    ;;
esac
if [[ -z "$OS_FAMILY" ]]; then
  err "Distro no soportada por el bootstrap (ID=${ID:-?}, ID_LIKE=${ID_LIKE:-?})."
  err "Soportadas: Ubuntu, Debian (o derivados). Instala Docker a mano y corre con --no-install."
  exit 1
fi
ok "Familia detectada: $OS_FAMILY (repo Docker: https://download.docker.com/linux/${OS_FAMILY})"

# Detectar WSL2
IS_WSL=0
if grep -qiE "microsoft|wsl" /proc/version 2>/dev/null; then
  IS_WSL=1
  ok "Entorno WSL2 detectado."
fi

# ── Step 2: instalar dependencias del sistema ────────────────────────────────

log "Instalando dependencias del sistema (curl, gpg, git, make, gettext, ca-certificates)..."
$SUDO apt-get update -qq
$SUDO apt-get install -y -qq ca-certificates curl gnupg lsb-release make git gettext-base >/dev/null
ok "Dependencias base instaladas."

# ── Step 3: instalar Docker si no está ───────────────────────────────────────

if command -v docker >/dev/null 2>&1; then
  ok "Docker ya está instalado ($(docker --version))."
else
  log "Instalando Docker Engine + Compose plugin (familia: $OS_FAMILY)..."
  $SUDO install -m 0755 -d /etc/apt/keyrings
  if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
    curl -fsSL "https://download.docker.com/linux/${OS_FAMILY}/gpg" \
      | $SUDO gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    $SUDO chmod a+r /etc/apt/keyrings/docker.gpg
  fi
  DISTRO_CODENAME="$(lsb_release -cs)"
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/${OS_FAMILY} ${DISTRO_CODENAME} stable" \
    | $SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
  $SUDO apt-get update -qq
  $SUDO apt-get install -y -qq docker-ce docker-ce-cli containerd.io \
        docker-buildx-plugin docker-compose-plugin >/dev/null
  ok "Docker instalado: $($SUDO docker --version)"
fi

# Verificar plugin compose
if ! $SUDO docker compose version >/dev/null 2>&1; then
  err "docker compose v2 no está disponible. Aborta."
  exit 1
fi
ok "Docker Compose: $($SUDO docker compose version | head -1)"

# ── Step 4: asegurar que el daemon esté corriendo ────────────────────────────

start_docker_daemon() {
  # Caso 1: systemd disponible
  if command -v systemctl >/dev/null 2>&1 && systemctl list-units --type=service 2>/dev/null | grep -q .; then
    if ! systemctl is-active --quiet docker 2>/dev/null; then
      $SUDO systemctl enable --now docker || true
    fi
    return 0
  fi
  # Caso 2: WSL2 sin systemd → service o dockerd en background
  if command -v service >/dev/null 2>&1; then
    if ! $SUDO service docker status >/dev/null 2>&1; then
      $SUDO service docker start || true
    fi
  fi
  # Caso 3: dockerd directo si los anteriores no aplican
  if ! $SUDO docker info >/dev/null 2>&1; then
    warn "Iniciando dockerd manualmente en background..."
    $SUDO nohup dockerd >/tmp/dockerd.log 2>&1 &
    sleep 4
  fi
}

log "Asegurando que el daemon de Docker esté activo..."
start_docker_daemon
for i in $(seq 1 15); do
  if $SUDO docker info >/dev/null 2>&1; then
    ok "Docker daemon activo."
    break
  fi
  if [[ $i -eq 15 ]]; then
    err "El daemon de Docker no respondió tras 15s."
    if [[ $IS_WSL -eq 1 ]]; then
      err "En WSL2: habilita systemd en /etc/wsl.conf con:"
      err "  [boot]"
      err "  systemd=true"
      err "Y reinicia con 'wsl --shutdown' desde Windows."
    fi
    exit 1
  fi
  sleep 1
done

# ── Step 5: agregar usuario al grupo docker ──────────────────────────────────

if [[ $EUID -ne 0 ]] && ! id -nG "$USER" | grep -qw docker; then
  log "Agregando $USER al grupo docker..."
  $SUDO usermod -aG docker "$USER"
  warn "Tu usuario fue agregado al grupo 'docker'. Para que aplique en sesiones futuras"
  warn "cierra y reabre la terminal (o ejecuta 'newgrp docker'). Este script usará sudo"
  warn "para los comandos docker restantes."
fi

# ── Step 6: crear volumen externo de RabbitMQ ────────────────────────────────

if docker_cmd volume inspect rabbitmq_data >/dev/null 2>&1; then
  ok "Volumen 'rabbitmq_data' ya existe."
else
  log "Creando volumen externo 'rabbitmq_data'..."
  docker_cmd volume create rabbitmq_data >/dev/null
  ok "Volumen creado."
fi

# ── Step 7: levantar el stack ────────────────────────────────────────────────

cd "$(dirname "$0")/.."
PROJECT_DIR="$(pwd)"
log "Directorio del proyecto: $PROJECT_DIR"

# ── Step 6b: abrir firewall (ufw) si se pidió ────────────────────────────────

if [[ $DO_UFW -eq 1 ]]; then
  if command -v ufw >/dev/null 2>&1; then
    if $SUDO ufw status 2>/dev/null | grep -q "Status: active"; then
      log "Abriendo puertos del stack en ufw..."
      for port in 3000 8080 8090 9080 9999 3001 9090 15672 9001; do
        $SUDO ufw allow "${port}/tcp" >/dev/null 2>&1 || true
      done
      ok "Puertos abiertos: 3000 8080 8090 9080 9999 3001 9090 15672 9001"
    else
      warn "ufw está instalado pero inactivo — no se abrieron puertos (no es necesario)."
    fi
  else
    warn "ufw no está instalado en este sistema — se ignora --open-firewall."
  fi
fi

# ── Step 7: renderizar el realm de Keycloak con PUBLIC_HOST/PUBLIC_DOMAIN ────

log "Renderizando realm de Keycloak..."
bash scripts/render-realm.sh

if [[ $DO_UP -eq 0 ]]; then
  ok "Bootstrap completado (sin levantar el stack porque pasaste --no-up)."
  echo
  echo "Para levantar el stack ahora ejecuta:"
  echo "  cd $PROJECT_DIR && docker compose up -d --build"
  exit 0
fi

log "Construyendo imágenes y levantando el stack (esto tarda varios minutos la primera vez)..."
compose_cmd up -d --build

# ── Step 8: esperar a Keycloak ───────────────────────────────────────────────

log "Esperando a que Keycloak esté listo (puede tomar 90–120s la primera vez)..."
TIMEOUT=180
WAITED=0
until curl -sf http://localhost:8080/realms/superchat >/dev/null 2>&1; do
  if [[ $WAITED -ge $TIMEOUT ]]; then
    warn "Keycloak no respondió en ${TIMEOUT}s. Revisa 'docker compose logs keycloak'."
    break
  fi
  sleep 3
  WAITED=$((WAITED+3))
  printf "."
done
echo
if [[ $WAITED -lt $TIMEOUT ]]; then
  ok "Keycloak listo en ${WAITED}s."
fi

# ── Step 9: resumen ──────────────────────────────────────────────────────────

echo
ok "Stack SuperChat levantado."
echo
echo -e "${C_CYAN}URLs principales${C_RESET}"
echo "  Frontend           http://localhost:3000"
echo "  API Gateway        http://localhost:8090"
echo "  Keycloak           http://localhost:8080  (admin/admin)"
echo "  RabbitMQ Mgmt      http://localhost:15672 (superchat/superchat123)"
echo "  MinIO Console      http://localhost:9001  (superchat/superchat123)"
echo "  Prometheus         http://localhost:9090"
echo "  Grafana            http://localhost:3001  (admin/admin)"
echo "  Dozzle (logs)      http://localhost:9999"
echo "  Portainer          http://localhost:9080"
echo
echo -e "${C_CYAN}Usuarios precargados${C_RESET} (password: demo123)"
echo "  alice  bob  charlie  dave  eve  frank"
echo
echo -e "${C_CYAN}Próximos pasos${C_RESET}"
echo "  make ps       # estado de contenedores"
echo "  make logs     # logs en vivo"
echo "  make health   # health-check de los servicios Spring"
echo "  make down     # detener el stack"
