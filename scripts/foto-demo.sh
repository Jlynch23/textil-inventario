#!/bin/bash
# "FOTOS" del AMBIENTE DEMO: guarda el estado EXACTO de ahora (base de datos
# completa + documentos subidos) con un nombre, y permite VOLVER a el despues.
#
# ¿Para que? Para ensuciar el demo tranquilo (subir guias de prueba, recepciones,
# etc. -- que despues no se pueden borrar) y regresar al estado limpio antes de
# mostrarlo al publico. A diferencia de resetear-demo.sh (que vuelve al seed
# SINTETICO y rota jlynch), la foto restaura TODO tal cual estaba: catalogo
# original, programas con sus saldos, cuentas y claves incluidas.
#
# Uso:
#   ./scripts/foto-demo.sh tomar [nombre]    # foto del estado actual (def: limpia)
#   ./scripts/foto-demo.sh listar            # que fotos hay
#   ./scripts/foto-demo.sh volver [nombre]   # restaurar esa foto (pide confirmar)
#
# Las fotos viven en ~/backups/demo/fotos/ (misma zona que backup-cliente.sh).
# No caducan solas (la retencion de 30 dias de backup-cliente NO las toca).
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"

SLUG="demo"
ACCION="${1:-}"
NOMBRE="${2:-limpia}"
DIR_CLIENTE="$RAIZ/clientes/$SLUG"
ENV_CLIENTE="$DIR_CLIENTE/.env"
CARPETA="$HOME/backups/$SLUG/fotos"
SQL="$CARPETA/${NOMBRE}_db.sql.gz"
DOCS="$CARPETA/${NOMBRE}_documentos.tar.gz"

if [ -z "$ACCION" ]; then
    echo "Uso: $0 tomar|listar|volver [nombre]" >&2
    exit 1
fi
if [ "$ACCION" = "listar" ]; then
    echo "Fotos del demo en $CARPETA:"
    ls -lh "$CARPETA" 2>/dev/null || echo "  (ninguna todavia)"
    exit 0
fi
if [ ! -f "$ENV_CLIENTE" ]; then
    echo "ERROR: no existe el demo en este clon ($ENV_CLIENTE)." >&2
    exit 1
fi
if ! docker ps --format '{{.Names}}' | grep -q "^db_$SLUG$"; then
    echo "ERROR: db_$SLUG no esta corriendo." >&2
    exit 1
fi
ROOT="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_CLIENTE" | cut -d= -f2-)"

case "$ACCION" in

tomar)
    mkdir -p "$CARPETA"
    if [ -f "$SQL" ]; then
        printf "La foto '%s' ya existe. ¿Sobrescribirla? Escribe 'si': " "$NOMBRE"
        read -r r
        [ "$r" = "si" ] || { echo "Cancelado."; exit 1; }
    fi
    echo "[foto:$NOMBRE] Dump completo de db_$SLUG (datos + cuentas)..."
    docker exec -e MYSQL_PWD="$ROOT" "db_$SLUG" mysqldump -u root \
        --single-transaction --routines --triggers \
        --default-character-set=utf8mb4 textil_inventario | gzip > "$SQL"
    echo "[foto:$NOMBRE] Empaquetando documentos subidos..."
    tar -czf "$DOCS" -C "$DIR_CLIENTE" documentos
    echo "[foto:$NOMBRE] Guardada: $(du -h "$SQL" | cut -f1) (db) + $(du -h "$DOCS" | cut -f1) (docs)"
    echo "Para volver a este estado: ./scripts/foto-demo.sh volver $NOMBRE"
    ;;

volver)
    if [ ! -f "$SQL" ]; then
        echo "ERROR: no existe la foto '$NOMBRE' ($SQL)." >&2
        echo "Fotos disponibles:"; ls "$CARPETA" 2>/dev/null || echo "  (ninguna)"
        exit 1
    fi
    echo "Vas a RESTAURAR el demo a la foto '$NOMBRE'. Se pierde TODO lo hecho"
    echo "despues de esa foto (recepciones, guias subidas, cambios de catalogo...)."
    printf "Escribe 'si' para confirmar: "
    read -r r
    [ "$r" = "si" ] || { echo "Cancelado."; exit 1; }

    echo "[foto:$NOMBRE] Restaurando la base de datos (reemplazo completo)..."
    # El dump trae DROP TABLE + CREATE: deja la BD identica a la foto.
    gunzip -c "$SQL" | docker exec -i -e MYSQL_PWD="$ROOT" "db_$SLUG" \
        mysql -u root --default-character-set=utf8mb4 textil_inventario

    echo "[foto:$NOMBRE] Restaurando la carpeta de documentos..."
    # Se limpia y desempaqueta con un contenedor efimero (root): la carpeta es
    # del uid 999 (appuser) y el usuario del host no puede borrarla directo.
    docker run --rm -v "$DIR_CLIENTE/documentos:/d" alpine sh -c 'rm -rf /d/* /d/..?* /d/.[!.]* 2>/dev/null || true'
    if [ -f "$DOCS" ]; then
        tar -xzf "$DOCS" -C "$DIR_CLIENTE"
        docker run --rm -v "$DIR_CLIENTE/documentos:/d" alpine chown -R 999:999 /d
    fi
    echo "Listo: el demo volvio a la foto '$NOMBRE'. No hace falta reiniciar nada."
    ;;

*)
    echo "Accion desconocida: '$ACCION'. Usa tomar | listar | volver." >&2
    exit 1
    ;;
esac
