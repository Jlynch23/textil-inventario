#!/bin/bash
# Da de BAJA un cliente del VPS (modelo multi-cliente). Apaga y borra su stack,
# su base de datos, su bloque de nginx y (opcionalmente) sus datos.
#
# Uso:
#   ./scripts/eliminar-cliente.sh <slug>
#
# ADVERTENCIA: por defecto BORRA la base de datos del cliente (su volumen) y su
# carpeta de datos. Haz un backup antes: ./scripts/backup-cliente.sh <slug>
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"

COMPOSE_CLIENTE="multicliente/docker-compose.cliente.yml"
NGINX_CLIENTES="multicliente/nginx/clientes"

if [ $# -ne 1 ]; then
    echo "Uso: $0 <slug>" >&2
    exit 1
fi

SLUG="$1"
DIR_CLIENTE="$RAIZ/clientes/$SLUG"
ENV_CLIENTE="$DIR_CLIENTE/.env"
CONF_NGINX="$NGINX_CLIENTES/cliente-$SLUG.conf"

if [ ! -d "$DIR_CLIENTE" ]; then
    echo "ERROR: no existe el cliente '$SLUG' ($DIR_CLIENTE)." >&2
    exit 1
fi

echo "Vas a ELIMINAR el cliente '$SLUG', incluida su base de datos y documentos."
read -p "Escribe el slug '$SLUG' para confirmar: " CONFIRMACION
if [ "$CONFIRMACION" != "$SLUG" ]; then
    echo "Cancelado."
    exit 0
fi

# -v borra tambien el volumen de datos (el MySQL del cliente).
echo "Apagando y borrando el stack de '$SLUG'..."
docker compose -p "texcontrol_$SLUG" --env-file "$ENV_CLIENTE" \
    -f "$COMPOSE_CLIENTE" down -v || true

echo "Quitando el bloque de nginx..."
rm -f "$CONF_NGINX"
if docker ps --format '{{.Names}}' | grep -q '^textil_nginx$'; then
    docker exec textil_nginx nginx -t && docker exec textil_nginx nginx -s reload
fi

echo "Borrando la carpeta de datos del cliente..."
rm -rf "$DIR_CLIENTE"

echo "Cliente '$SLUG' eliminado."
