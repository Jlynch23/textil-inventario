#!/bin/bash
# Da de alta el AMBIENTE DEMO para clientes potenciales: una instancia mas del
# modelo multi-cliente (BD propia, aislada) servida en https://demo.texcontrol.pe,
# PUBLICA (sin Basic Auth) y sembrada con datos + cuentas de ejemplo, para que un
# prospecto pruebe el sistema "lleno" sin tocar ningun cliente real.
#
# Que hace, apoyandose en los scripts que ya existen:
#   1. Crea el stack del cliente 'demo' con nuevo-cliente.sh --prueba (BD aislada
#      db_demo + app_demo + bloque nginx publico), si aun no existe.
#   2. Aplica scripts/demo-seed.sql: catalogo de ejemplo, algo de stock y las 10
#      cuentas demo (1 admin, 3 gerente, 3 supervisor, 3 almacenero-supervisor).
#   3. Endurece con endurecer-cliente.sh: rota la clave de jlynch (SUPERADMIN) a
#      una PRIVADA -- imprescindible porque la demo es publica -- y borra las
#      cuentas *prueba inactivas. Las 10 cuentas demo (es_prueba=FALSE) sobreviven.
#
# Es re-ejecutable: si el cliente 'demo' ya existe, re-aplica el seed (idempotente)
# y re-endurece sin recrear el stack. Para limpiar lo que cargaron los prospectos
# y volver a foja cero, usar scripts/resetear-demo.sh.
#
# Uso:
#   ANTHROPIC_API_KEY=... ./scripts/nuevo-demo.sh [slug] ["Nombre de la empresa"]
#     slug   por defecto: demo   (-> demo.texcontrol.pe)
#     nombre por defecto: "TexControl Demo"
#
# Requisitos (ver DEMO.md): proxy multi-cliente corriendo y cert wildcard emitido.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

SLUG="${1:-demo}"
NOMBRE="${2:-TexControl Demo}"
SEED_SQL="$RAIZ/scripts/demo-seed.sql"

mc_validar_slug "$SLUG" || exit 1
if [ ! -f "$SEED_SQL" ]; then
    echo "ERROR: no encuentro el seed $SEED_SQL" >&2
    exit 1
fi

# --- 1. Crear el stack del cliente demo (si no existe) ---------------------
if [ -d "$RAIZ/clientes/$SLUG" ]; then
    echo "El cliente '$SLUG' ya existe: re-aplico seed y re-endurezco (no recreo el stack)."
    if ! docker ps --format '{{.Names}}' | grep -q "^db_$SLUG$"; then
        echo "Levantando el stack '$SLUG' (estaba apagado)..."
        mc_compose "$RAIZ" "$SLUG" up -d
        mc_esperar_db "$SLUG"
        mc_esperar_app "$SLUG"
    fi
else
    echo "Dando de alta el cliente demo '$SLUG' (modo --prueba)..."
    ./scripts/nuevo-cliente.sh --prueba "$SLUG" "$NOMBRE"
fi

# --- 2. Aplicar el seed de datos + cuentas demo ----------------------------
# El seed es idempotente (INSERT IGNORE). Se aplica el archivo completo por
# STDIN; mc_sql_cliente solo corre un statement, asi que aca se pipea el .sql.
echo "Aplicando el seed de la demo ($SEED_SQL)..."
ROOT="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$RAIZ/clientes/$SLUG/.env" | cut -d= -f2-)"
docker exec -i -e MYSQL_PWD="$ROOT" "db_$SLUG" \
    mysql -u root textil_inventario < "$SEED_SQL"
echo "Seed aplicado."

# --- 3. Endurecer: rotar jlynch (privado) + borrar cuentas *prueba ---------
# La demo es PUBLICA, asi que el SUPERADMIN no puede quedar con la clave de
# arranque. endurecer-cliente.sh rota jlynch e imprime la clave UNA vez.
# Las 10 cuentas demo son es_prueba=FALSE -> NO las borra.
echo ""
echo "Endureciendo (rota la clave de jlynch; guardala del output de abajo)..."
./scripts/endurecer-cliente.sh "$SLUG"

# --- 4. Repartir credenciales de las cuentas demo --------------------------
cat <<'EOF'

======================================================================
 AMBIENTE DEMO listo. Cuentas para repartir a los prospectos
 (todas activas; clave = palabra del rol; se restauran con resetear-demo.sh):

   ROL         USUARIO           CLAVE
   ---------   ---------------   -----------
   ADMIN       admindemo         admin
   GERENTE     gerente1demo      gerente
   GERENTE     gerente2demo      gerente
   GERENTE     gerente3demo      gerente
   SUPERVISOR  supervisor1demo   supervisor
   SUPERVISOR  supervisor2demo   supervisor
   SUPERVISOR  supervisor3demo   supervisor
   ALMACENERO  almacen1demo      supervisor   (rol SUPERVISOR: /almacen)
   ALMACENERO  almacen2demo      supervisor
   ALMACENERO  almacen3demo      supervisor

 SUPERADMIN (jlynch): clave PRIVADA impresa arriba por endurecer-cliente.sh
 -- NO se reparte a los prospectos.
======================================================================
EOF

echo "URL: https://${SLUG}.texcontrol.pe"
