#!/bin/bash
# Restaura un backup de UN cliente del modelo multi-cliente: base de datos y,
# si existe, sus documentos. Es la contraparte de backup-cliente.sh, que hasta
# ahora no tenia ninguna -- se podia respaldar pero no recuperar.
#
# Uso:
#   ./scripts/restaurar-cliente.sh <slug>                    # el backup MAS RECIENTE
#   ./scripts/restaurar-cliente.sh <slug> <ruta/al/db.sql.gz>  # uno especifico
#   ./scripts/restaurar-cliente.sh --listar <slug>           # que backups hay
#
# ADVERTENCIA: SOBREESCRIBE por completo la base del cliente. Antes de tocar
# nada hace un dump de seguridad del estado ACTUAL, por si el backup elegido
# resulta no ser el que se queria.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

BD="textil_inventario"

uso() {
    echo "Uso: $0 <slug> [ruta/al/backup_db.sql.gz]" >&2
    echo "     $0 --listar <slug>" >&2
    exit 1
}

[ $# -ge 1 ] || uso

# --- Listar los backups disponibles y salir -------------------------------
if [ "$1" = "--listar" ]; then
    [ $# -eq 2 ] || uso
    CARPETA="${HOME}/backups/$2"
    if [ ! -d "$CARPETA" ]; then
        echo "No hay backups de '$2' (falta $CARPETA)." >&2
        exit 1
    fi
    echo "Backups de '$2' en $CARPETA:"
    ls -1t "$CARPETA"/${2}_db_*.sql.gz 2>/dev/null | while read -r f; do
        printf "  %-58s %s\n" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
    done
    exit 0
fi

SLUG="$1"
mc_validar_slug "$SLUG" || exit 1

ENV_CLIENTE="$RAIZ/clientes/$SLUG/.env"
if [ ! -f "$ENV_CLIENTE" ]; then
    echo "ERROR: no existe el cliente '$SLUG' ($ENV_CLIENTE)." >&2
    echo "       Clientes dados de alta: $(ls -1 "$RAIZ/clientes" 2>/dev/null | tr '\n' ' ')" >&2
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "db_$SLUG"; then
    echo "ERROR: el contenedor db_$SLUG no esta corriendo. Levanta el stack del" >&2
    echo "       cliente primero (nuevo-cliente.sh o actualizar-clientes.sh $SLUG)." >&2
    exit 1
fi

# Lectura LITERAL (grep|cut), no `source`: una clave con $, & o comillas la
# interpretaria el shell. Mismo criterio que backup-cliente.sh y deploy-dev.sh.
MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_CLIENTE" | cut -d= -f2-)"

# --- Elegir el backup -----------------------------------------------------
CARPETA="${HOME}/backups/$SLUG"
if [ $# -ge 2 ]; then
    DUMP="$2"
else
    DUMP="$(ls -1t "$CARPETA"/${SLUG}_db_*.sql.gz 2>/dev/null | head -1 || true)"
    if [ -z "$DUMP" ]; then
        echo "ERROR: no hay ningun backup de '$SLUG' en $CARPETA." >&2
        echo "       Genera uno con: ./scripts/backup-cliente.sh $SLUG" >&2
        exit 1
    fi
    echo "Usando el backup mas reciente: $(basename "$DUMP")"
fi

if [ ! -f "$DUMP" ]; then
    echo "ERROR: no existe el archivo $DUMP" >&2
    exit 1
fi
# Un .gz truncado (backup cortado a la mitad) restauraria una base incompleta
# sin decir nada. Se comprueba ANTES de borrar la base actual.
if ! gzip -t "$DUMP" 2>/dev/null; then
    echo "ERROR: $DUMP esta corrupto o incompleto (gzip -t falla)." >&2
    echo "       NO se toco la base. Prueba con otro backup (--listar)." >&2
    exit 1
fi

echo
echo "Cliente:  $SLUG"
echo "Backup:   $DUMP  ($(du -h "$DUMP" | cut -f1), $(date -r "$DUMP" '+%Y-%m-%d %H:%M'))"
echo "Destino:  db_$SLUG / $BD  -- SE SOBREESCRIBE POR COMPLETO"
echo
read -r -p "Escribe RESTAURAR para continuar: " respuesta
if [ "$respuesta" != "RESTAURAR" ]; then
    echo "Cancelado. No se toco nada."
    exit 0
fi

# --- Red de seguridad: dump del estado ACTUAL antes de pisarlo ------------
mkdir -p "$CARPETA"
PREVIO="${CARPETA}/${SLUG}_db_ANTES-DE-RESTAURAR_$(date +%Y-%m-%d_%H%M%S).sql.gz"
echo "Guardando el estado actual en $(basename "$PREVIO") ..."
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "db_$SLUG" mysqldump -u root \
    --single-transaction --routines --triggers "$BD" | gzip > "$PREVIO"

# --- Restaurar ------------------------------------------------------------
# La app se para durante la restauracion: si sigue escribiendo mientras se
# reemplazan las tablas, el resultado es una mezcla de dos estados.
APP_ESTABA_ARRIBA=0
if docker ps --format '{{.Names}}' | grep -qx "app_$SLUG"; then
    APP_ESTABA_ARRIBA=1
    echo "Parando app_$SLUG durante la restauracion..."
    docker stop "app_$SLUG" >/dev/null
fi

echo "Restaurando la base..."
# DROP + CREATE deja la base en el estado exacto del dump: sin esto, las tablas
# que existan hoy y NO esten en el backup sobrevivirian, y Flyway las validaria
# contra un esquema que no les corresponde.
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "db_$SLUG" \
    mysql -u root -e "DROP DATABASE IF EXISTS \`$BD\`; CREATE DATABASE \`$BD\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gunzip -c "$DUMP" | docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "db_$SLUG" mysql -u root "$BD"

# --- Documentos (si el backup trae el tar de la misma fecha) --------------
FECHA="$(basename "$DUMP" | sed -E "s/^${SLUG}_db_(.*)\.sql\.gz$/\1/")"
DOCS="${CARPETA}/${SLUG}_documentos_${FECHA}.tar.gz"
if [ -f "$DOCS" ]; then
    echo "Restaurando documentos desde $(basename "$DOCS") ..."
    rm -rf "$RAIZ/clientes/$SLUG/documentos"
    tar -xzf "$DOCS" -C "$RAIZ/clientes/$SLUG"
else
    echo "AVISO: no hay tar de documentos para esa fecha ($(basename "$DOCS"))."
    echo "       La base quedo restaurada; los PDFs siguen como estaban."
fi

# --- Levantar la app y comprobar que arranca ------------------------------
if [ "$APP_ESTABA_ARRIBA" -eq 1 ]; then
    echo "Levantando app_$SLUG..."
    docker start "app_$SLUG" >/dev/null
    # Que arranque es la prueba real de que el esquema restaurado le sirve:
    # con ddl-auto=validate, si no calza con las entidades la app no levanta.
    mc_esperar_app "$SLUG" || {
        echo "AVISO: app_$SLUG no respondio a tiempo. Revisa: docker logs app_$SLUG" >&2
    }
fi

echo
echo "Listo. '$SLUG' restaurado desde $(basename "$DUMP")."
echo "El estado anterior quedo guardado en:"
echo "  $PREVIO"
