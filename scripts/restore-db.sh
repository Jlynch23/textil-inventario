#!/bin/bash
# Restaura un backup de textil-inventario (DEVOPS-03, auditoria 17-jul-2026).
#
# Uso:
#   ./scripts/restore-db.sh /ruta/al/backup.sql.gz
#
# ADVERTENCIA: esto SOBREESCRIBE la base de datos actual por completo.
set -euo pipefail

CONTENEDOR="textil_mysql"
BASE_DATOS="textil_inventario"

if [ $# -ne 1 ]; then
    echo "Uso: $0 /ruta/al/backup.sql.gz" >&2
    exit 1
fi

ARCHIVO="$1"
if [ ! -f "$ARCHIVO" ]; then
    echo "ERROR: no existe el archivo $ARCHIVO" >&2
    exit 1
fi

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: la variable DB_PASSWORD no esta definida." >&2
    exit 1
fi

read -p "Esto SOBREESCRIBE la base de datos '$BASE_DATOS' actual. Escribe 'si' para continuar: " CONFIRMACION
if [ "$CONFIRMACION" != "si" ]; then
    echo "Cancelado."
    exit 0
fi

echo "Restaurando desde $ARCHIVO..."
gunzip -c "$ARCHIVO" | docker exec -i "$CONTENEDOR" mysql -u root -p"$DB_PASSWORD" "$BASE_DATOS"
echo "Restauracion completada."
