#!/bin/bash
# Resetea el AMBIENTE DEMO a foja cero: borra TODO lo que los prospectos
# cargaron (recepciones, transferencias, stock, usuarios que hayan creado, etc.)
# y lo deja como recien sembrado (catalogo + stock + las 10 cuentas demo).
#
# Reset MANUAL (no hay cron): se corre cuando quieras limpiar la demo.
#
# Como: elimina el volumen de la BD del cliente 'demo' (foja cero garantizada,
# sin tener que enumerar tablas ni respetar orden de FKs), la vuelve a levantar
# vacia (Flyway re-migra el esquema al arrancar) y re-ejecuta nuevo-demo.sh para
# re-sembrar y re-endurecer.
#
# OJO: la clave PRIVADA de jlynch se ROTA de nuevo (sale una distinta); guarda la
# que imprime al final. Los documentos subidos en clientes/demo/documentos NO se
# borran aca (borralos a mano si quieres: son PDFs, no afectan la BD).
#
# Uso:
#   ./scripts/resetear-demo.sh [slug] [--si]
#     slug  por defecto: demo
#     --si  omite la confirmacion (para scripting)
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

SLUG="demo"
CONFIRMADO=0
for arg in "$@"; do
    case "$arg" in
        --si|--yes|-y) CONFIRMADO=1 ;;
        *) SLUG="$arg" ;;
    esac
done

mc_validar_slug "$SLUG" || exit 1
if [ ! -d "$RAIZ/clientes/$SLUG" ]; then
    echo "ERROR: no existe el cliente demo '$SLUG'. Crealo con ./scripts/nuevo-demo.sh" >&2
    exit 1
fi

# --- Confirmacion (es destructivo) -----------------------------------------
if [ "$CONFIRMADO" -ne 1 ]; then
    echo "Esto BORRA toda la base de datos del demo '$SLUG' (db_$SLUG) y la re-siembra."
    echo "Se pierde lo que cargaron los prospectos. Los clientes reales NO se tocan."
    printf "Escribe 'demo' para confirmar: "
    read -r respuesta
    if [ "$respuesta" != "demo" ]; then
        echo "Cancelado."
        exit 1
    fi
fi

# --- 1. Borrar el stack + su volumen de datos (foja cero) ------------------
echo "Bajando el stack '$SLUG' y borrando su volumen de datos..."
mc_compose "$RAIZ" "$SLUG" down -v

# --- 2. Re-levantar, re-sembrar y re-endurecer -----------------------------
# nuevo-demo.sh detecta que db_$SLUG no esta corriendo, lo levanta vacio
# (Flyway migra), aplica el seed y endurece (rota jlynch).
echo "Re-creando la demo desde cero..."
NOMBRE="$(grep -E '^NOMBRE_EMPRESA=' "$RAIZ/clientes/$SLUG/.env" | cut -d= -f2- | tr -d '"')"
exec ./scripts/nuevo-demo.sh "$SLUG" "${NOMBRE:-TexControl Demo}"
