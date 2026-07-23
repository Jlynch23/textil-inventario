#!/bin/bash
# Redeploy del ENTORNO DE PRUEBAS (staging) — corre la rama `develop`.
#
# Usa un clon SEPARADO del repo (por defecto ~/textil-inventario-dev) en la rama
# develop, para no chocar con el clon de produccion (~/textil-inventario, en
# main, que deploy.sh maneja con `git reset --hard origin/main`).
#
# Requisitos previos (una sola vez, ver STAGING.md):
#   - El stack de PRODUCCION corriendo (crea la red textil-inventario_default).
#   - .env.dev completo dentro del clon dev (credenciales del entorno dev).
#
# Uso:  ./scripts/deploy-dev.sh
set -euo pipefail

DEV_DIR="${DEV_DIR:-$HOME/textil-inventario-dev}"
# Se toma la URL del remoto del repo actual (mismo metodo que produccion);
# se puede forzar con REPO_URL=...
REPO_URL="${REPO_URL:-$(git -C "$(dirname "$0")/.." remote get-url origin 2>/dev/null || echo "git@github.com:Jlynch23/textil-inventario.git")}"

# 1. Clon dev (primera vez) o actualizar a lo ultimo de develop.
if [ ! -d "$DEV_DIR/.git" ]; then
    echo "Clonando el repo dev en $DEV_DIR ..."
    git clone "$REPO_URL" "$DEV_DIR"
fi
cd "$DEV_DIR"
echo "Trayendo develop..."
git fetch origin develop
git checkout develop
git reset --hard origin/develop

# 2. .env.dev debe existir (credenciales del entorno dev, no versionadas).
if [ ! -f .env.dev ]; then
    echo "ERROR: falta $DEV_DIR/.env.dev"
    echo "Copiar de .env.dev.example y completar con claves hex (openssl rand -hex 24/32)."
    exit 1
fi

# 3. Levantar MySQL dev primero y esperar healthy.
echo "Levantando MySQL dev..."
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d mysql_dev
echo "Esperando a que MySQL dev este healthy..."
for i in $(seq 1 30); do
    estado=$(docker inspect --format='{{.State.Health.Status}}' textil_mysql_dev 2>/dev/null || echo "")
    [ "$estado" = "healthy" ] && break
    sleep 3
done

# 4. Sincronizar el password de textil_user con el .env.dev. Lectura LITERAL
#    (grep/cut), igual que deploy.sh, para no desincronizar por caracteres
#    especiales. Idempotente.
echo "Sincronizando password de textil_user (dev)..."
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' .env.dev | cut -d= -f2-)"
MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env.dev | cut -d= -f2-)"
DB_PASSWORD_SQL=${DB_PASSWORD//\'/\'\'}
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" textil_mysql_dev \
    mysql -uroot -e "ALTER USER 'textil_user'@'%' IDENTIFIED BY '${DB_PASSWORD_SQL}'; FLUSH PRIVILEGES;"

# 5. Reconstruir y levantar el stack dev (app_dev toma el codigo de develop).
echo "Reconstruyendo y levantando el stack dev..."
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d --build

echo "Limpiando imagenes viejas sin usar..."
docker image prune -f

echo ""
echo "Listo. Estado del stack dev:"
docker compose --env-file .env.dev -f docker-compose.dev.yml ps
echo ""
echo "Probar: https://dev.texcontrol.pe  (Basic Auth + login de la app)"
