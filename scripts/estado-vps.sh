#!/bin/bash
# Foto rapida del estado del VPS: CPU, RAM, disco y consumo por contenedor
# Docker, todo de un solo comando. Pensado para correr en el VPS y saber de un
# vistazo cuanto se esta usando (y cuanto queda del techo de ~4 GB de RAM).
#
# Solo LEE: no reinicia, no borra ni cambia nada. Uso (en el VPS):
#   ./scripts/estado-vps.sh
set -euo pipefail

linea() { printf '%s\n' "-----------------------------------------------------------------"; }

echo ""
echo "==================  ESTADO DEL VPS  =================="
echo "Host : $(hostname)"
echo "Fecha: $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo "Encendido: $(uptime -p 2>/dev/null || echo 'n/d')"
linea

# --- CPU: nucleos y carga ---
nucleos="$(nproc 2>/dev/null || echo '?')"
echo "CPU: ${nucleos} nucleo(s)"
echo "  Carga promedio (1/5/15 min):$(uptime | awk -F'load average:' '{print $2}')"
echo "  (referencia: si la carga supera al numero de nucleos, esta saturado)"
linea

# --- RAM ---
echo "MEMORIA (RAM):"
free -h | awk '
    NR==1 { printf "  %-9s %-9s %-9s %-9s\n", "", "total", "usada", "libre" }
    NR==2 { printf "  %-9s %-9s %-9s %-9s\n", $1, $2, $3, $7 }'
echo "  (mira 'libre': es lo que te queda antes de tocar el techo)"
linea

# --- Disco de la particion raiz ---
echo "DISCO (particion /):"
df -h / | awk '
    NR==1 { printf "  %-14s %-6s %-6s %-6s %-5s\n", $1, $2, $3, $4, $5 }
    NR==2 { printf "  %-14s %-6s %-6s %-6s %-5s\n", $1, $2, $3, $4, $5 }'
linea

# --- Contenedores Docker ---
if command -v docker >/dev/null 2>&1; then
    echo "CONTENEDORES DOCKER (foto en vivo):"
    docker stats --no-stream --format \
        "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" 2>/dev/null \
        || echo "  (no se pudo leer 'docker stats')"

    # Suma total de RAM usada por los contenedores (normaliza KiB/MiB/GiB a MiB).
    total_mib="$(docker stats --no-stream --format '{{.MemUsage}}' 2>/dev/null | awk '
        {
            campo=$1; num=campo;
            gsub(/[^0-9.]/, "", num);        # deja solo el numero
            if      (campo ~ /GiB/) num*=1024;
            else if (campo ~ /KiB/) num/=1024;
            # MiB se queda igual
            suma+=num;
        }
        END { if (suma > 0) printf "%.0f", suma }' || true)"
    if [ -n "${total_mib:-}" ]; then
        echo ""
        echo "  >> RAM total usada por contenedores: ~${total_mib} MiB"
    fi
    linea

    echo "USO DE DISCO POR DOCKER (imagenes / volumenes / build cache):"
    docker system df 2>/dev/null || echo "  (no se pudo leer 'docker system df')"
    echo "  (para liberar lo no usado: 'docker system prune')"
else
    echo "Docker no esta instalado o no esta en el PATH de este usuario."
fi

echo "====================================================="
echo ""
