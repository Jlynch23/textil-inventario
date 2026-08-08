#!/bin/bash
# Deja la base de DEV o del DEMO con UNA sola cuenta: jlynch (el proveedor).
# Borra todas las demas -- las de prueba (adminprueba, gerenteprueba, ...), las
# cuentas demo repartidas a prospectos, y cualquiera creada a mano.
#
# Uso (en el VPS):
#   ./scripts/borrar-usuarios-dev-demo.sh dev              # VISTA PREVIA, no toca nada
#   ./scripts/borrar-usuarios-dev-demo.sh dev --ejecutar   # borra de verdad
#   ./scripts/borrar-usuarios-dev-demo.sh demo --ejecutar
#
# Sin --ejecutar SOLO muestra que pasaria. Es el modo por defecto a proposito.
#
# ============================ REGLA DE ORO ================================
# NUNCA se borra `jlynch`, ni ninguna cuenta con rol SUPERADMIN.
#
# No es una preferencia: es lo unico que impide dejar la instancia SIN NINGUNA
# cuenta capaz de entrar. Si se borra, no hay como recuperarla desde la web --
# habria que meter un INSERT a mano en MySQL con un hash de bcrypt generado
# aparte. Por eso la proteccion esta CLAVADA en el codigo (no es un parametro
# que se pueda pisar desde la linea de comandos) y ademas se vuelve a comprobar
# despues de armar la lista, antes de borrar.
# ==========================================================================
#
# SOLO dev y demo. Un cliente de pago no se puede elegir ni por error: los
# unicos destinos posibles estan escritos abajo y cualquier otra cosa se rechaza.
set -euo pipefail

USUARIO_PROTEGIDO="jlynch"
ROL_PROTEGIDO="SUPERADMIN"
BD="textil_inventario"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"

# --- 1. Destino: dev o demo, nada mas -------------------------------------
AMBIENTE="${1:-}"
EJECUTAR="${2:-}"

case "$AMBIENTE" in
    dev)
        CONTENEDOR="textil_mysql_dev"
        ENV_FILE="$RAIZ/.env.dev"
        ;;
    demo)
        CONTENEDOR="db_demo"
        ENV_FILE="$RAIZ/clientes/demo/.env"
        ;;
    *)
        echo "Uso: $0 <dev|demo> [--ejecutar]" >&2
        echo "" >&2
        echo "Solo acepta 'dev' o 'demo'. Los clientes de pago NO son un destino" >&2
        echo "valido de este script, ni escribiendo su slug." >&2
        exit 1
        ;;
esac

if [ -n "$EJECUTAR" ] && [ "$EJECUTAR" != "--ejecutar" ]; then
    echo "ERROR: segundo argumento desconocido: '$EJECUTAR' (solo --ejecutar)." >&2
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR"; then
    echo "ERROR: no encuentro el contenedor '$CONTENEDOR' corriendo." >&2
    exit 1
fi
if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: falta $ENV_FILE (de ahi sale MYSQL_ROOT_PASSWORD)." >&2
    exit 1
fi

# Lectura LITERAL (grep/cut), igual que deploy-dev.sh: con `source`, una clave
# con $, comillas o backticks la interpretaria el shell y quedaria distinta.
MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
if [ -z "$MYSQL_ROOT_PASSWORD" ]; then
    echo "ERROR: $ENV_FILE no tiene MYSQL_ROOT_PASSWORD." >&2
    exit 1
fi

# OJO: SIN `-i`. El SQL va por `-e`, asi que no hace falta stdin -- y con `-i`
# el `docker exec` SE COME la entrada estandar. Como mas abajo estas funciones se
# llaman DENTRO de un `while read` alimentado por stdin, `-i` vaciaba la lista de
# FKs en la primera vuelta: el bucle procesaba una sola tabla, las demas quedaban
# sin reasignar y el DELETE moria por foreign key. Es el clasico de ssh/docker
# dentro de un while-read. No volver a ponerlo.
sql() { docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTENEDOR" mysql -uroot -N -B "$BD" -e "$1"; }
sql_tabla() { docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTENEDOR" mysql -uroot -B "$BD" -e "$1"; }

# --- 2. La cuenta protegida tiene que existir ------------------------------
# Si no esta, algo no es lo que creemos (base equivocada, o a jlynch ya lo
# borraron). En cualquiera de los dos casos, parar: sin ella no hay a quien
# dejarle la instancia ni a quien reasignarle el historial.
JLYNCH_ID="$(sql "SELECT id FROM usuarios WHERE username = '$USUARIO_PROTEGIDO' LIMIT 1;")"
if [ -z "$JLYNCH_ID" ]; then
    echo "ERROR: no existe el usuario '$USUARIO_PROTEGIDO' en $CONTENEDOR." >&2
    echo "       Se aborta: es la cuenta que debe quedar en pie." >&2
    exit 1
fi

# --- 3. Quienes se van -----------------------------------------------------
# Doble candado: por username Y por rol. Con que cualquiera de los dos aplique,
# la cuenta se queda.
DONDE_BORRABLES="u.username <> '$USUARIO_PROTEGIDO' AND r.nombre <> '$ROL_PROTEGIDO'"

echo "=========================================================="
echo " Ambiente : $AMBIENTE   (contenedor $CONTENEDOR)"
echo " Se queda : $USUARIO_PROTEGIDO (id $JLYNCH_ID) y todo rol $ROL_PROTEGIDO"
echo "=========================================================="
echo
echo "--- Cuentas que se BORRARIAN ---"
sql_tabla "SELECT u.id, u.username, u.nombre, r.nombre AS rol, u.activo
           FROM usuarios u JOIN roles r ON r.id = u.rol_id
           WHERE $DONDE_BORRABLES ORDER BY u.id;"

A_BORRAR="$(sql "SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE $DONDE_BORRABLES;")"
echo
echo "Total a borrar: $A_BORRAR"
if [ "$A_BORRAR" = "0" ]; then
    echo "No hay nada que hacer."
    exit 0
fi

# --- 4. Que cuelga de esos usuarios ---------------------------------------
# Las FK se DESCUBREN del esquema, no se escriben a mano. Motivo concreto: V3
# borro y recreo `transferencias`, asi que las columnas que nombraba V1 ya no
# existen -- una lista hardcodeada nace vieja. Ademas V2 (hilo) va a sumar
# tablas nuevas y este script las va a tomar solo.
FKS="$(sql "SELECT CONCAT(TABLE_NAME, ' ', COLUMN_NAME)
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = '$BD'
              AND REFERENCED_TABLE_NAME = 'usuarios'
              AND REFERENCED_COLUMN_NAME = 'id'
            ORDER BY TABLE_NAME, COLUMN_NAME;")"

echo
echo "--- Filas que apuntan a esas cuentas (pasan a $USUARIO_PROTEGIDO) ---"
TOTAL_REF=0
while read -r TABLA COLUMNA; do
    [ -z "${TABLA:-}" ] && continue
    N="$(sql "SELECT COUNT(*) FROM \`$TABLA\` WHERE \`$COLUMNA\` IN
              (SELECT u.id FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE $DONDE_BORRABLES);")"
    printf "  %-28s %-28s %s\n" "$TABLA" "$COLUMNA" "$N"
    TOTAL_REF=$((TOTAL_REF + N))
done <<< "$FKS"
echo "  Total de filas a reasignar: $TOTAL_REF"

echo
echo "Que pasa con esas filas: NO se borran. Se reasignan a $USUARIO_PROTEGIDO."
echo "Cuatro de esas columnas son NOT NULL (recepciones, kardex, entradas y"
echo "salidas rapidas), asi que no se pueden dejar en NULL: o se reasignan o el"
echo "DELETE revienta por FK. Reasignar conserva el movimiento; el precio es que"
echo "la autoria queda en $USUARIO_PROTEGIDO. En dev y demo eso no es dato real."

if [ "$EJECUTAR" != "--ejecutar" ]; then
    echo
    echo "=== VISTA PREVIA: no se toco nada. ==="
    echo "Para hacerlo de verdad:  $0 $AMBIENTE --ejecutar"
    exit 0
fi

# --- 5. Confirmacion a mano ------------------------------------------------
echo
read -r -p "Escribi BORRAR para continuar: " RESPUESTA
if [ "$RESPUESTA" != "BORRAR" ]; then
    echo "Cancelado."
    exit 1
fi

# --- 6. Backup ANTES ------------------------------------------------------
# MySQL no tiene DDL transaccional y esto son varios statements: si se corta a
# la mitad queda a medio hacer. El backup es la unica vuelta atras.
DIR_BACKUP="$HOME/backups-usuarios"
mkdir -p "$DIR_BACKUP"
ARCHIVO="$DIR_BACKUP/${AMBIENTE}-antes-de-borrar-usuarios-$(date +%Y%m%d-%H%M%S).sql"
echo "Backup -> $ARCHIVO"
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTENEDOR" \
    mysqldump -uroot --single-transaction --routines "$BD" > "$ARCHIVO"
if [ ! -s "$ARCHIVO" ]; then
    echo "ERROR: el backup salio vacio. Se aborta sin tocar la base." >&2
    exit 1
fi
echo "Backup OK ($(du -h "$ARCHIVO" | cut -f1))"

# --- 7. Reasignar y borrar ------------------------------------------------
while read -r TABLA COLUMNA; do
    [ -z "${TABLA:-}" ] && continue
    sql "UPDATE \`$TABLA\` SET \`$COLUMNA\` = $JLYNCH_ID WHERE \`$COLUMNA\` IN
         (SELECT id FROM (SELECT u.id FROM usuarios u JOIN roles r ON r.id = u.rol_id
                          WHERE $DONDE_BORRABLES) AS x);"
done <<< "$FKS"

# Ultimo candado antes del DELETE: que la lista NO contenga a la cuenta
# protegida. Es redundante con el WHERE -- justamente por eso esta.
CHEQUEO="$(sql "SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id = u.rol_id
                WHERE ($DONDE_BORRABLES) AND (u.username = '$USUARIO_PROTEGIDO' OR r.nombre = '$ROL_PROTEGIDO');")"
if [ "$CHEQUEO" != "0" ]; then
    echo "ERROR: la lista a borrar incluye una cuenta protegida. Se aborta." >&2
    exit 1
fi

sql "DELETE u FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE $DONDE_BORRABLES;"

# --- 8. Estado final ------------------------------------------------------
echo
echo "--- Cuentas que quedaron ---"
sql_tabla "SELECT u.id, u.username, u.nombre, r.nombre AS rol, u.activo
           FROM usuarios u JOIN roles r ON r.id = u.rol_id ORDER BY u.id;"

QUEDA_JLYNCH="$(sql "SELECT COUNT(*) FROM usuarios WHERE username = '$USUARIO_PROTEGIDO';")"
if [ "$QUEDA_JLYNCH" != "1" ]; then
    echo "ERROR GRAVE: $USUARIO_PROTEGIDO no quedo en la base. Restaura YA:" >&2
    echo "  docker exec -i -e MYSQL_PWD=... $CONTENEDOR mysql -uroot $BD < $ARCHIVO" >&2
    exit 1
fi

echo
echo "Listo. Backup en $ARCHIVO"
