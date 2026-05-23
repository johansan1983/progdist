#!/usr/bin/env bash
# SuperChat remote installer — pensado para curl-pipe-bash desde una WSL2/Linux
# completamente limpio (ni git, ni docker, ni nada).
#
# Uso:
#   curl -fsSL https://raw.githubusercontent.com/johansan1983/progdist/main/scripts/install.sh | bash
#
# Variables de entorno opcionales:
#   INSTALL_DIR     Directorio destino del clone. Default: $HOME/progdist
#   REPO_URL        URL del repo. Default: https://github.com/johansan1983/progdist.git
#   BRANCH          Rama a checkear. Default: main
#   NO_UP=1         No levantar el stack al final (solo clone + instalar deps)
#
# Lo que hace:
#   1. Instala curl/git si faltan (apt-get)
#   2. Clona el repo a $INSTALL_DIR (o hace git pull si ya existe)
#   3. cd y ejecuta scripts/bootstrap.sh — que a su vez instala Docker
#      y levanta el stack completo.

set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/johansan1983/progdist.git}"
BRANCH="${BRANCH:-main}"
INSTALL_DIR="${INSTALL_DIR:-$HOME/progdist}"
NO_UP="${NO_UP:-0}"

# ── Colores ──────────────────────────────────────────────────────────────────
C_GREEN="\033[0;32m"; C_YELLOW="\033[0;33m"; C_RED="\033[0;31m"
C_CYAN="\033[0;36m"; C_RESET="\033[0m"
log()  { echo -e "${C_CYAN}==>${C_RESET} $*"; }
ok()   { echo -e "${C_GREEN}✓${C_RESET} $*"; }
warn() { echo -e "${C_YELLOW}!${C_RESET} $*"; }
err()  { echo -e "${C_RED}✗${C_RESET} $*" >&2; }

# ── sudo helper ──────────────────────────────────────────────────────────────
SUDO=""
if [[ $EUID -ne 0 ]]; then
  if ! command -v sudo >/dev/null 2>&1; then
    err "Se requiere sudo. Instálalo o ejecuta este script como root."
    exit 1
  fi
  SUDO="sudo"
fi

# ── 1. Validar SO ────────────────────────────────────────────────────────────
if ! command -v apt-get >/dev/null 2>&1; then
  err "Este instalador soporta sólo distros Debian/Ubuntu (apt-get)."
  err "En otras distros: instala git manualmente, clona $REPO_URL y corre scripts/bootstrap.sh."
  exit 1
fi

# ── 2. Instalar curl y git si faltan ─────────────────────────────────────────
NEED_INSTALL=()
command -v curl >/dev/null 2>&1 || NEED_INSTALL+=("curl")
command -v git  >/dev/null 2>&1 || NEED_INSTALL+=("git")
command -v ca-certificates >/dev/null 2>&1 || true

if [[ ${#NEED_INSTALL[@]} -gt 0 ]]; then
  log "Instalando paquetes faltantes: ${NEED_INSTALL[*]}"
  $SUDO apt-get update -qq
  $SUDO apt-get install -y -qq ca-certificates "${NEED_INSTALL[@]}" >/dev/null
  ok "Paquetes instalados."
else
  ok "curl y git ya están presentes."
fi

# ── 3. Clonar o actualizar repo ──────────────────────────────────────────────
if [[ -d "$INSTALL_DIR/.git" ]]; then
  log "El repo ya existe en $INSTALL_DIR — actualizando (git pull)..."
  git -C "$INSTALL_DIR" fetch --quiet origin "$BRANCH"
  git -C "$INSTALL_DIR" checkout --quiet "$BRANCH"
  git -C "$INSTALL_DIR" pull --quiet --ff-only origin "$BRANCH" || warn "git pull no aplicó cambios (rama divergida?)"
  ok "Repo actualizado en $INSTALL_DIR"
elif [[ -e "$INSTALL_DIR" ]]; then
  err "$INSTALL_DIR existe pero no es un repo git. Borralo o usa INSTALL_DIR=<otra-ruta>."
  exit 1
else
  log "Clonando $REPO_URL (rama $BRANCH) en $INSTALL_DIR ..."
  git clone --quiet --branch "$BRANCH" "$REPO_URL" "$INSTALL_DIR"
  ok "Repo clonado."
fi

cd "$INSTALL_DIR"

# ── 4. Pasar la antorcha a bootstrap.sh ──────────────────────────────────────
if [[ ! -x scripts/bootstrap.sh ]]; then
  chmod +x scripts/bootstrap.sh
fi

log "Lanzando bootstrap.sh para instalar Docker y levantar el stack..."
if [[ "$NO_UP" == "1" ]]; then
  bash scripts/bootstrap.sh --no-up
else
  bash scripts/bootstrap.sh
fi
