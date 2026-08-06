#!/bin/bash
# Actualiza el CODIGO de los clientes multi-cliente a la version ACTUAL del repo
# (en produccion, la rama `main`): reconstruye la imagen compartida UNA sola vez
# y reinicia la app de cada cliente para que la tome.
#
# NO toca los datos: cada MySQL y su volumen quedan intactos. Si el cambio trae
# una migracion Flyway nueva, cada app la aplica sola en SU propia BD al arrancar.
#
# Reemplaza al viejo scripts/deploy.sh del modelo single-cliente (eliminado ago-2026).
#
# Uso (en el VPS, con el clon en `main`):
#   ./scripts/actualizar-clientes.sh              # git pull + reconstruye + reinicia TODOS
#   ./scripts/actualizar-clientes.sh --no-pull    # NO hace git pull (usa el codigo ya presente)
#   ./scripts/actualizar-clientes.sh <slug>       # solo ese cliente (util para probar de a uno)
#   ./scripts/actualizar-clientes.sh --no-pull <slug>
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

# --- Argumentos ------------------------------------------------------------
PULL=1
SOLO_SLUG=""
for arg in "$@"; do
    case "$arg" in
        --no-pull) PULL=0 ;;
        --*) echo "Opcion desconocida: $arg" >&2; exit 1 ;;
        *) SOLO_SLUG="$arg" ;;
    esac
done

# --- 1. Traer el codigo (rama actual; en produccion deberia ser main) ------
if [ "$PULL" -eq 1 ]; then
    RAMA="$(git rev-parse --abbrev-ref HEAD)"
    if [ "$RAMA" != "main" ]; then
        echo "AVISO: el clon esta en '$RAMA', no en 'main'. Los clientes de produccion"
        echo "       deberian correr 'main'. (Segui igual si sabes lo que haces.)"
    fi
    echo "Trayendo la ultima version de '$RAMA'..."
    git pull --ff-only
fi
echo "Version del repo: $(git rev-parse --short HEAD) (rama $(git rev-parse --abbrev-ref HEAD))"

# --- 2. Reconstruir la imagen compartida (una sola vez para todos) ---------
echo "Reconstruyendo la imagen compartida $MC_IMAGEN..."
docker build -t "$MC_IMAGEN" "$RAIZ"

# --- 3. Reiniciar la app de cada cliente para que tome la imagen nueva -----
shopt -s nullglob
ENVS=("$RAIZ"/clientes/*/.env)
if [ ${#ENVS[@]} -eq 0 ]; then
    echo "No hay clientes dados de alta (clientes/*/.env vacio). Nada que reiniciar."
    exit 0
fi

ACTUALIZADOS=0
for e in "${ENVS[@]}"; do
    slug="$(basename "$(dirname "$e")")"
    if [ -n "$SOLO_SLUG" ] && [ "$slug" != "$SOLO_SLUG" ]; then
        continue
    fi
    echo "-> Reiniciando app_$slug con la imagen nueva (sus datos NO se tocan)..."
    mc_compose "$RAIZ" "$slug" up -d
    ACTUALIZADOS=$((ACTUALIZADOS + 1))
done

if [ -n "$SOLO_SLUG" ] && [ "$ACTUALIZADOS" -eq 0 ]; then
    echo "ERROR: no existe el cliente '$SOLO_SLUG'. Clientes dados de alta: $(ls -1 "$RAIZ/clientes" 2>/dev/null | tr '\n' ' ')" >&2
    exit 1
fi

echo ""
echo "Listo. $ACTUALIZADOS cliente(s) actualizados a $(git rev-parse --short HEAD)."
