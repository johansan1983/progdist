#!/usr/bin/env bash
# Renderiza keycloak/superchat-realm.template.json -> keycloak/superchat-realm.json
# sustituyendo $PUBLIC_HOST y $PUBLIC_DOMAIN desde el entorno (o .env si existe).
#
# Uso:
#   ./scripts/render-realm.sh                          # localhost por defecto
#   PUBLIC_HOST=10.0.0.50 ./scripts/render-realm.sh
#   PUBLIC_DOMAIN=chat.example.com ./scripts/render-realm.sh

set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f keycloak/superchat-realm.template.json ]]; then
  echo "ERROR: keycloak/superchat-realm.template.json no existe" >&2
  exit 1
fi

# Cargar .env si está presente (sin pisar variables ya exportadas)
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# Defaults seguros si la variable no está definida
export PUBLIC_HOST="${PUBLIC_HOST:-localhost}"
export PUBLIC_DOMAIN="${PUBLIC_DOMAIN:-localhost}"

if ! command -v envsubst >/dev/null 2>&1; then
  echo "ERROR: 'envsubst' no está disponible. Instala 'gettext-base': sudo apt-get install -y gettext-base" >&2
  exit 1
fi

# Restringir envsubst a las dos variables — evita pisar otros $... que aparezcan en el JSON
envsubst '$PUBLIC_HOST $PUBLIC_DOMAIN' \
  < keycloak/superchat-realm.template.json \
  > keycloak/superchat-realm.json

echo "Realm renderizado: keycloak/superchat-realm.json"
echo "  PUBLIC_HOST=$PUBLIC_HOST"
echo "  PUBLIC_DOMAIN=$PUBLIC_DOMAIN"
