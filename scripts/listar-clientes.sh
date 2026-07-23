#!/bin/bash
# Lista los clientes dados de alta en el VPS (modelo multi-cliente) con su
# estado y consumo de memoria. Util para operar y para vigilar el techo de RAM.
#
# Uso:
#   ./scripts/listar-clientes.sh
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

if ! ls "$RAIZ"/clientes/*/.env >/dev/null 2>&1; then
    echo "No hay clientes dados de alta todavia."
    exit 0
fi

printf "%-14s %-24s %-10s %-10s\n" "SLUG" "EMPRESA" "APP" "DB"
printf "%-14s %-24s %-10s %-10s\n" "----" "-------" "---" "--"

total=0
for env_file in "$RAIZ"/clientes/*/.env; do
    slug="$(basename "$(dirname "$env_file")")"
    # Leer el NOMBRE_EMPRESA del .env sin volcar el resto de variables.
    nombre="$(grep -E '^NOMBRE_EMPRESA=' "$env_file" | cut -d= -f2-)"

    estado_app="detenido"
    estado_db="detenido"
    docker ps --format '{{.Names}}' | grep -q "^app_$slug$" && estado_app="corriendo"
    docker ps --format '{{.Names}}' | grep -q "^db_$slug$"  && estado_db="corriendo"

    printf "%-14s %-24s %-10s %-10s\n" "$slug" "${nombre:0:24}" "$estado_app" "$estado_db"
    total=$((total + 1))
done

echo ""
echo "Total de clientes: $total"
echo ""
echo "Consumo de memoria por contenedor (en vivo):"
# docker stats con --no-stream saca una sola foto (no se queda en bucle).
docker stats --no-stream --format \
    "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}" \
    2>/dev/null | grep -E "NAME|_" || echo "  (no hay contenedores corriendo)"
