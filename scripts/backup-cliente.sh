#!/bin/bash
# Backup GRATIS de UN cliente (modelo multi-cliente): dump de su base MySQL +
# su carpeta de documentos, comprimidos. Reemplaza al backup pago de Vultr y,
# a diferencia de aquel, permite restaurar a UN solo cliente sin tocar al resto.
#
# Uso manual:
#   ./scripts/backup-cliente.sh <slug>
#
# Uso para TODOS los clientes (cron diario a las 2am, ejemplo):
#   0 2 * * * cd /ruta/textil-inventario && ./scripts/backup-cliente.sh --todos \
#     >> ~/backups/backup.log 2>&1
#
# La clave de root de cada cliente se lee de su propio clientes/<slug>/.env;
# no hace falta pasar nada por el entorno.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"

RETENCION_DIAS=30

backup_uno() {
    local slug="$1"
    local dir_cliente="$RAIZ/clientes/$slug"
    local env_cliente="$dir_cliente/.env"

    if [ ! -f "$env_cliente" ]; then
        echo "ERROR: no existe el cliente '$slug' ($env_cliente)." >&2
        return 1
    fi

    # Lee MYSQL_ROOT_PASSWORD (y demas) del .env del cliente.
    set -a; source "$env_cliente"; set +a

    local carpeta="${HOME}/backups/$slug"
    mkdir -p "$carpeta"
    local fecha; fecha="$(date +%Y-%m-%d_%H%M%S)"
    local sql="${carpeta}/${slug}_db_${fecha}.sql.gz"
    local docs="${carpeta}/${slug}_documentos_${fecha}.tar.gz"

    echo "[$slug] Dump de la base de datos..."
    # Password por MYSQL_PWD (no por -p"...") para que no quede visible en la
    # lista de procesos mientras corre mysqldump (misma regla que backup-db.sh).
    docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "db_$slug" mysqldump -u root \
        --single-transaction --routines --triggers textil_inventario | gzip > "$sql"

    echo "[$slug] Empaquetando documentos..."
    if [ -d "$dir_cliente/documentos" ]; then
        tar -czf "$docs" -C "$dir_cliente" documentos
    fi

    echo "[$slug] Backup: $(du -h "$sql" | cut -f1) (db) + documentos"
    echo "[$slug] Limpiando backups con mas de $RETENCION_DIAS dias..."
    find "$carpeta" -name "${slug}_*" -mtime +$RETENCION_DIAS -delete
}

if [ $# -ne 1 ]; then
    echo "Uso: $0 <slug> | --todos" >&2
    exit 1
fi

if [ "$1" = "--todos" ]; then
    for env_file in "$RAIZ"/clientes/*/.env; do
        [ -e "$env_file" ] || { echo "No hay clientes que respaldar."; exit 0; }
        slug="$(basename "$(dirname "$env_file")")"
        backup_uno "$slug"
    done
else
    backup_uno "$1"
fi

echo "Listo."
