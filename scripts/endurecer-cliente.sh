#!/bin/bash
# Endurece un cliente para PRODUCCION (roadmap punto 3): rota la clave de jlynch
# a una UNICA de esta copia y elimina las cuentas de prueba. Ejecutar antes de
# entregar una copia a un cliente que paga.
#
# Por que: el hash de arranque de jlynch/superadmin es el MISMO en todas las
# copias (migracion V33). Dejarlo asi = misma llave maestra en cada cliente. Y
# las cuentas *prueba (aunque inactivas) no deben viajar a una copia vendida.
#
# Uso:
#   ./scripts/endurecer-cliente.sh <slug>
#
# Imprime la clave nueva de jlynch UNA sola vez: guardala en tu gestor de
# contraseñas. No se almacena en ningun lado.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

if [ $# -ne 1 ]; then
    echo "Uso: $0 <slug>" >&2
    exit 1
fi

SLUG="$1"
DIR_CLIENTE="$RAIZ/clientes/$SLUG"

if [ ! -d "$DIR_CLIENTE" ]; then
    echo "ERROR: no existe el cliente '$SLUG' ($DIR_CLIENTE)." >&2
    exit 1
fi
if ! docker ps --format '{{.Names}}' | grep -q "^db_$SLUG$"; then
    echo "ERROR: db_$SLUG no esta corriendo. Levanta el stack del cliente primero." >&2
    exit 1
fi

# --- 1. Rotar la clave de jlynch (unica por copia) -------------------------
existe_jlynch="$(mc_sql_cliente "$RAIZ" "$SLUG" "SELECT COUNT(*) FROM usuarios WHERE username='jlynch';")"
if [ "$existe_jlynch" != "1" ]; then
    echo "AVISO: no encontre la cuenta 'jlynch' en db_$SLUG; omito la rotacion de clave."
    NUEVA_CLAVE=""
else
    echo "Rotando la clave de jlynch (unica de esta copia)..."
    NUEVA_CLAVE="$(openssl rand -hex 12)"
    HASH="$(mc_hash_bcrypt "$NUEVA_CLAVE")"
    if [ -z "$HASH" ]; then
        echo "ERROR: no se pudo generar el hash bcrypt (revisa Docker / imagen $MC_IMAGEN_BCRYPT)." >&2
        exit 1
    fi
    # El hash contiene '$'; va dentro de comillas simples en SQL y su valor NO
    # se re-expande en bash (una sola expansion), asi que es seguro interpolarlo.
    mc_sql_cliente "$RAIZ" "$SLUG" \
        "UPDATE usuarios SET password_hash='$HASH' WHERE username='jlynch';"
fi

# --- 2. Eliminar las cuentas de prueba -------------------------------------
n_prueba="$(mc_sql_cliente "$RAIZ" "$SLUG" "SELECT COUNT(*) FROM usuarios WHERE es_prueba = TRUE;")"
if [ "${n_prueba:-0}" -gt 0 ]; then
    echo "Eliminando $n_prueba cuenta(s) de prueba..."
    mc_sql_cliente "$RAIZ" "$SLUG" "DELETE FROM usuarios WHERE es_prueba = TRUE;"
else
    echo "No habia cuentas de prueba que eliminar."
fi

# --- 3. Resumen (credenciales, una sola vez) -------------------------------
echo ""
echo "======================================================================"
echo " Cliente '$SLUG' endurecido para produccion."
echo " - Cuentas de prueba: eliminadas."
if [ -n "$NUEVA_CLAVE" ]; then
    echo " - Clave NUEVA de jlynch (SUPERADMIN, soporte del proveedor):"
    echo ""
    echo "       usuario:  jlynch"
    echo "       clave:    $NUEVA_CLAVE"
    echo ""
    echo "   GUARDALA AHORA en tu gestor de contraseñas: no se vuelve a mostrar"
    echo "   ni se almacena en ningun lado. Rotala tambien desde 'Mi cuenta'."
fi
echo "======================================================================"
