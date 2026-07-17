#!/bin/bash
# Backup de la base de datos MySQL de textil-inventario (DEVOPS-03, auditoria 17-jul-2026).
#
# Uso manual:
#   ./scripts/backup-db.sh
#
# Uso automatico (cron diario a las 2am, ejemplo -- ajustar la ruta):
#   0 2 * * * cd /home/textil_laura/textil-inventario && DB_PASSWORD=xxx ./scripts/backup-db.sh >> ~/backups/textil-inventario/backup.log 2>&1
#
# Requiere que DB_PASSWORD este definida en el entorno (ya vive en ~/.bashrc
# en las maquinas de desarrollo; en cron hay que pasarla explicitamente
# porque cron no carga ~/.bashrc por defecto).
set -euo pipefail

CONTENEDOR="textil_mysql"
BASE_DATOS="textil_inventario"
CARPETA_BACKUPS="${HOME}/backups/textil-inventario"
RETENCION_DIAS=30

mkdir -p "$CARPETA_BACKUPS"

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: la variable DB_PASSWORD no esta definida." >&2
    exit 1
fi

FECHA=$(date +%Y-%m-%d_%H%M%S)
ARCHIVO="${CARPETA_BACKUPS}/textil_inventario_${FECHA}.sql.gz"

echo "Generando backup: $ARCHIVO"
docker exec "$CONTENEDOR" mysqldump -u root -p"$DB_PASSWORD" \
    --single-transaction --routines --triggers "$BASE_DATOS" | gzip > "$ARCHIVO"

echo "Backup completado: $(du -h "$ARCHIVO" | cut -f1)"

echo "Limpiando backups con mas de $RETENCION_DIAS dias..."
find "$CARPETA_BACKUPS" -name "textil_inventario_*.sql.gz" -mtime +$RETENCION_DIAS -delete

echo "Backups actuales:"
ls -lh "$CARPETA_BACKUPS"
