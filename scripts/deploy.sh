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

echo "Levantando MySQL primero, para sincronizar credenciales antes de la app..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d mysql

echo "Esperando a que MySQL este healthy..."
mysql_healthy=""
for i in $(seq 1 30); do
    estado=$(docker inspect --format='{{.State.Health.Status}}' textil_mysql 2>/dev/null || echo "")
    if [ "$estado" = "healthy" ]; then
        mysql_healthy="1"
        break
    fi
    sleep 3
done
if [ -z "$mysql_healthy" ]; then
    echo "MySQL no llego a estar healthy a tiempo. Revisa: docker logs textil_mysql"
    exit 1
fi

# Re-aplica el password de textil_user segun el DB_PASSWORD actual del .env
# en CADA despliegue. Es idempotente (si ya coincide, no cambia nada) y
# evita que la app quede en "Access denied" si por cualquier motivo la
# contrasena de MySQL y la del .env quedaron desincronizadas entre un
# despliegue y el siguiente.
echo "Sincronizando password de textil_user con el .env..."
# Leemos los valores del .env de forma LITERAL (grep/cut), NO con `source`.
# `source` expande $, backticks y comillas del shell, dejando un valor distinto
# al que docker-compose le pasa a MySQL y a la app (que es el texto crudo del
# .env). Si el password tiene simbolos, `source` lo desincroniza y la app queda
# en "Access denied" en cada deploy. Con grep/cut leemos exactamente los mismos
# bytes que ve docker-compose, asi la clave siempre coincide.
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' .env | cut -d= -f2-)"
MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
# Escapamos comillas simples para el literal SQL (' -> '').
DB_PASSWORD_SQL=${DB_PASSWORD//\'/\'\'}
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" textil_mysql \
    mysql -uroot -e "ALTER USER 'textil_user'@'%' IDENTIFIED BY '${DB_PASSWORD_SQL}'; FLUSH PRIVILEGES;"

echo "Reconstruyendo y reiniciando contenedores..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

echo "Limpiando imagenes viejas sin usar..."
docker image prune -f

echo ""
echo "Listo. Estado de los contenedores:"
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
