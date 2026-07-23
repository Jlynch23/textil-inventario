#!/bin/bash
# Da de alta un cliente NUEVO en el VPS (modelo multi-cliente, MySQL propio).
#
# Con un solo comando: crea la base de datos aislada, levanta el stack del
# cliente (app + su MySQL), genera su bloque de nginx y recarga el proxy.
#
# Uso:
#   ./scripts/nuevo-cliente.sh <slug> "<Nombre de la empresa>"
#
# Ejemplo:
#   ./scripts/nuevo-cliente.sh laura "Laura & Clemente"
#     -> queda en https://laura.texcontrol.pe
#
# El <slug> es el subdominio: solo letras minusculas y numeros, sin espacios
# ni tildes (igual que el lanzador). El "Nombre de la empresa" es lo que ve el
# usuario en la UI (NOMBRE_EMPRESA), puede llevar espacios y mayusculas.
#
# ANTHROPIC_API_KEY (para el OCR) se toma del entorno si esta definida y se
# copia al .env del cliente. Si falta, el cliente arranca igual pero sin OCR.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

# --- 1. Validar argumentos -------------------------------------------------
if [ $# -ne 2 ]; then
    echo "Uso: $0 <slug> \"<Nombre de la empresa>\"" >&2
    echo "Ej:  $0 laura \"Laura & Clemente\"" >&2
    exit 1
fi

SLUG="$1"
NOMBRE_EMPRESA="$2"

mc_validar_slug "$SLUG" || exit 1

DIR_CLIENTE="$RAIZ/clientes/$SLUG"
if [ -d "$DIR_CLIENTE" ]; then
    echo "ERROR: el cliente '$SLUG' ya existe ($DIR_CLIENTE)." >&2
    echo "       Para recrearlo, primero eliminalo: ./scripts/eliminar-cliente.sh $SLUG" >&2
    exit 1
fi

# --- 2. Red compartida, imagen, .env y stack -------------------------------
mc_asegurar_red_e_imagen "$RAIZ"

echo "Creando el cliente '$SLUG' ($NOMBRE_EMPRESA)..."
mc_generar_env "$RAIZ" "$SLUG" "$NOMBRE_EMPRESA"

echo "Levantando el stack de '$SLUG' (app + su MySQL)..."
mc_compose "$RAIZ" "$SLUG" up -d

mc_esperar_db "$SLUG" || exit 1
mc_esperar_app "$SLUG"

# --- 3. Bloque de nginx + recarga ------------------------------------------
echo "Generando el bloque de nginx para $SLUG.texcontrol.pe..."
mc_generar_nginx "$RAIZ" "$SLUG" "$NOMBRE_EMPRESA"
mc_recargar_nginx || exit 1

# --- 4. Resumen ------------------------------------------------------------
echo ""
echo "======================================================================"
echo " Cliente dado de alta: $NOMBRE_EMPRESA"
echo " URL:        https://$SLUG.texcontrol.pe"
echo " Contenedor: app_$SLUG  +  db_$SLUG"
echo " Datos:      $DIR_CLIENTE (documentos + .env con credenciales)"
echo ""
echo " Login inicial: usuario 'jlynch' / clave 'superadmin' (ROTAR de inmediato)."
echo " Desde ahi, el SUPERADMIN crea la cuenta ADMIN del dueño."
echo "======================================================================"
