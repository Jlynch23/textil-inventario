#!/bin/bash
# Instala (o actualiza) el cron diario que respalda TODOS los clientes
# (roadmap punto 3: backups automaticos). Idempotente: si ya existe la entrada,
# la reemplaza en vez de duplicarla.
#
# Uso:
#   ./scripts/instalar-cron-backups.sh          # 2am por defecto
#   ./scripts/instalar-cron-backups.sh 4        # a las 4am
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
HORA="${1:-2}"

if ! printf '%s' "$HORA" | grep -qE '^([0-9]|1[0-9]|2[0-3])$'; then
    echo "ERROR: la hora debe ser un numero de 0 a 23 (recibido: '$HORA')." >&2
    exit 1
fi

LOG_DIR="${HOME}/backups"
mkdir -p "$LOG_DIR"

MARCA="# texcontrol-backups"
LINEA="0 $HORA * * * cd $RAIZ && ./scripts/backup-cliente.sh --todos >> $LOG_DIR/backup.log 2>&1 $MARCA"

# Reescribe el crontab quitando cualquier entrada previa nuestra (por la marca)
# y agregando la nueva. crontab -l puede fallar si no hay crontab: se ignora.
( crontab -l 2>/dev/null | grep -v "$MARCA" || true; echo "$LINEA" ) | crontab -

echo "Cron de backups instalado:"
echo "  $LINEA"
echo ""
echo "Backups diarios a las ${HORA}:00 -> $LOG_DIR/<slug>/  (log: $LOG_DIR/backup.log)"
echo "Ver el cron actual con: crontab -l"
