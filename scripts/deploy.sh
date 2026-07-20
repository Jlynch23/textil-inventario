#!/bin/bash
# Redeploy de TexControl en el VPS (DEPLOY.md).
#
# Trae el ultimo main, reconstruye la imagen de la app y reinicia los
# contenedores. MySQL y su volumen de datos NO se tocan.
#
# Uso (parado en la carpeta del proyecto en el VPS):
#   ./scripts/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Trayendo la ultima version de main..."
git fetch origin main
git checkout main
git reset --hard origin/main

echo "Reconstruyendo y reiniciando contenedores..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

echo "Limpiando imagenes viejas sin usar..."
docker image prune -f

echo ""
echo "Listo. Estado de los contenedores:"
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
