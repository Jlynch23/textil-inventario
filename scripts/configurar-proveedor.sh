#!/bin/bash
# Guarda UNA sola vez las credenciales del PROVEEDOR en el VPS (hoy: la
# ANTHROPIC_API_KEY del OCR, la MISMA para todos los clientes), para NO tener
# que anteponer ANTHROPIC_API_KEY=... en cada comando:
#
#   ./scripts/nuevo-cliente.sh ...      # ya no necesita el prefijo
#   ./scripts/nuevo-demo.sh             # idem
#   ./scripts/resetear-demo.sh          # idem
#
# La clave queda en ~/.texcontrol/proveedor.env (permisos 600, solo tu usuario;
# fuera del repo, asi que jamas se commitea). lib-cliente.sh la carga sola si
# ANTHROPIC_API_KEY no viene ya en el entorno (el entorno siempre gana).
#
# Uso:
#   ./scripts/configurar-proveedor.sh             # pide la clave (oculta) y la guarda
#   ./scripts/configurar-proveedor.sh --aplicar   # ademas la copia a los clientes
#                                                 # YA creados (clientes/*/.env de
#                                                 # ESTE repo) y reinicia sus apps
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
ARCHIVO="$HOME/.texcontrol/proveedor.env"

APLICAR=0
if [ "${1:-}" = "--aplicar" ]; then
    APLICAR=1
fi

# --- 1. Pedir la clave (oculta: no queda en pantalla ni en el historial) ----
echo "Configuracion del proveedor (se guarda en $ARCHIVO)"
printf "Pega la ANTHROPIC_API_KEY y presiona Enter (no se muestra al escribir): "
read -r -s CLAVE
echo ""
if [ -z "$CLAVE" ]; then
    echo "ERROR: la clave quedo vacia. No se guardo nada." >&2
    exit 1
fi
case "$CLAVE" in
    sk-ant-*) : ;;  # formato esperado de una clave de Anthropic
    *) echo "AVISO: la clave no empieza con 'sk-ant-'. La guardo igual, pero revisala." ;;
esac

# --- 2. Guardar con permisos restrictivos (600, solo tu usuario) -----------
mkdir -p "$(dirname "$ARCHIVO")"
umask 077
cat > "$ARCHIVO" <<EOF
# Credenciales del PROVEEDOR TexControl. NO commitear, NO compartir.
# Las carga solo lib-cliente.sh cuando ANTHROPIC_API_KEY no viene en el entorno.
ANTHROPIC_API_KEY=$CLAVE
EOF
umask 022
echo "Guardada (permisos 600). Los proximos nuevo-cliente/nuevo-demo la usan solos."

# --- 3. Opcional: aplicarla a los clientes YA creados ----------------------
# Los clientes existentes tienen su copia de la clave en clientes/<slug>/.env
# (se copio al darlos de alta). Con --aplicar se actualiza esa copia y se
# reinicia la app para que la tome.
if [ "$APLICAR" -eq 1 ]; then
    if ! ls "$RAIZ"/clientes/*/.env >/dev/null 2>&1; then
        echo "No hay clientes creados en $RAIZ/clientes/ — nada que aplicar."
        exit 0
    fi
    for env_file in "$RAIZ"/clientes/*/.env; do
        slug="$(basename "$(dirname "$env_file")")"
        if grep -q '^ANTHROPIC_API_KEY=' "$env_file"; then
            sed -i "s|^ANTHROPIC_API_KEY=.*|ANTHROPIC_API_KEY=$CLAVE|" "$env_file"
        else
            echo "ANTHROPIC_API_KEY=$CLAVE" >> "$env_file"
        fi
        if docker ps --format '{{.Names}}' | grep -q "^app_$slug$"; then
            # OJO: `docker restart` NO relee el .env (las variables se fijan al
            # CREAR el contenedor). Hay que RECREARLO via compose, que detecta el
            # cambio de entorno y recrea solo el servicio app (la BD no se toca).
            echo "[$slug] clave actualizada; recreando app_$slug para que la tome..."
            docker compose -p "texcontrol_$slug" --env-file "$env_file" \
                -f "$RAIZ/multicliente/docker-compose.cliente.yml" up -d app
        else
            echo "[$slug] clave actualizada (app apagada; la tomara al arrancar)."
        fi
    done
    echo "Listo: clave aplicada a los clientes existentes de este repo."
fi
