#!/bin/bash
# Deja la base de DEV con los PROGRAMAS y el CATALOGO, y borra todo el
# movimiento: recepciones (guias), stock, kardex, transferencias, entradas y
# salidas rapidas, y documentos del archivo historico.
#
# Para que sirve: cargar las guias UNA POR UNA sobre los programas ya metidos y
# ver como avanza el pendiente de cada linea, sin el ruido de las pruebas viejas.
#
# Uso (en el VPS):
#   ./scripts/limpiar-dev-dejando-programas.sh
#
# SOLO toca el contenedor textil_mysql_dev (staging). Se niega a correr contra
# cualquier otra base: los clientes y el demo viven en contenedores db_<slug> y
# este script no los conoce ni los puede alcanzar.
set -euo pipefail

CONTENEDOR="textil_mysql_dev"
BD="textil_inventario"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"

# --- 1. Guardas -----------------------------------------------------------
# El nombre del contenedor esta fijo en el codigo, no viene por parametro: no hay
# forma de apuntar esto a la base de un cliente ni por error ni a proposito.
if ! docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR"; then
    echo "ERROR: no encuentro el contenedor '$CONTENEDOR' corriendo." >&2
    echo "       Levanta dev primero:  ./scripts/deploy-dev.sh" >&2
    exit 1
fi

if [ ! -f "$RAIZ/.env.dev" ]; then
    echo "ERROR: falta $RAIZ/.env.dev (de ahi sale MYSQL_ROOT_PASSWORD)." >&2
    exit 1
fi
# Lectura LITERAL, igual que deploy-dev.sh: con `source`, una clave que traiga
# $, comillas o backticks la interpretaria el shell y quedaria distinta.
MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$RAIZ/.env.dev" | cut -d= -f2-)"
if [ -z "$MYSQL_ROOT_PASSWORD" ]; then
    echo "ERROR: .env.dev no tiene MYSQL_ROOT_PASSWORD." >&2
    exit 1
fi

mysql_dev() {
    docker exec -i "$CONTENEDOR" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$BD" "$@"
}

# --- 2. Foto del estado actual -------------------------------------------
echo "Estado ACTUAL de la base de dev:"
mysql_dev -N -e "
SELECT '  programas            ', COUNT(*) FROM programas
UNION ALL SELECT '  lineas de programa   ', COUNT(*) FROM programa_detalles
UNION ALL SELECT '  recepciones (guias)  ', COUNT(*) FROM recepciones
UNION ALL SELECT '  lineas de recepcion  ', COUNT(*) FROM recepcion_detalles
UNION ALL SELECT '  filas de stock       ', COUNT(*) FROM stock_actual
UNION ALL SELECT '  movimientos kardex   ', COUNT(*) FROM kardex_movimientos
UNION ALL SELECT '  transferencias       ', COUNT(*) FROM transferencias
UNION ALL SELECT '  entradas rapidas     ', COUNT(*) FROM entradas_rapidas
UNION ALL SELECT '  salidas rapidas      ', COUNT(*) FROM salidas_rapidas
UNION ALL SELECT '  docs archivo hist.   ', COUNT(*) FROM documentos_historicos
UNION ALL SELECT '  articulos (SE QUEDAN)', COUNT(*) FROM articulos
UNION ALL SELECT '  colores   (SE QUEDAN)', COUNT(*) FROM colores;"

echo
echo "Se BORRA: recepciones y sus documentos, stock, kardex, transferencias,"
echo "          entradas/salidas rapidas y documentos del archivo historico."
echo "Se DEJA:  programas y sus lineas (con el recibido vuelto a 0), todo el"
echo "          catalogo, usuarios y el log de auditoria."
echo
read -r -p "Escribe LIMPIAR para continuar: " respuesta
if [ "$respuesta" != "LIMPIAR" ]; then
    echo "Cancelado. No se toco nada."
    exit 0
fi

# --- 3. Respaldo antes de tocar nada -------------------------------------
# Barato y evita el arrepentimiento: si algo sale mal, se restaura y listo.
mkdir -p "$RAIZ/backups-dev"
RESPALDO="$RAIZ/backups-dev/dev-antes-de-limpiar.sql"
echo "Respaldando la base de dev en $RESPALDO ..."
docker exec "$CONTENEDOR" mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$BD" > "$RESPALDO"
echo "Respaldo listo ($(du -h "$RESPALDO" | cut -f1))."

# --- 4. Limpieza ----------------------------------------------------------
# En UNA transaccion y en orden de dependencias (hijo antes que padre), sin
# desactivar las FK: si el orden estuviera mal, quiero que falle y revierta,
# no que borre a medias dejando filas colgadas.
echo "Limpiando..."
mysql_dev <<'SQL'
START TRANSACTION;

-- Kardex primero: apunta a recepcion_detalles y a transferencias.
DELETE FROM kardex_movimientos;

-- Cadena de transferencias, de la hoja a la raiz.
DELETE FROM transferencia_distribucion;
DELETE FROM transferencia_detalle;
DELETE FROM salidas_rapidas;      -- apunta a transferencias
DELETE FROM transferencias;

-- Cadena de recepciones.
DELETE FROM entradas_rapidas;     -- apunta a recepciones
DELETE FROM documentos_historicos; -- recepcion_creada_id apunta a recepciones
DELETE FROM recepcion_documentos;
DELETE FROM recepcion_detalles;
DELETE FROM recepciones;

-- Stock: no lo arrastra ningun borrado de arriba, hay que vaciarlo aparte.
DELETE FROM stock_actual;

-- El avance de los programas vuelve a cero. Sin esto los programas seguirian
-- mostrando tela recibida de recepciones que ya no existen, que es justo lo
-- que se quiere evitar para cargar las guias una por una.
UPDATE programa_detalles SET cantidad_recibida = 0;

-- Correlativo de transferencias a cero: no quedan transferencias, asi que la
-- proxima vuelve a empezar en TRF-000001 sin riesgo de pisar ninguna.
UPDATE correlativo SET ultimo_valor = 0 WHERE nombre = 'transferencia';

COMMIT;
SQL

# --- 5. PDFs archivados ---------------------------------------------------
# Las filas que apuntaban a estos archivos ya no existen; quedarian huerfanos.
#
# OJO con los permisos: los PDF los escribe la APP desde dentro del contenedor,
# como uid 999 (appuser), asi que `linuxuser` NO puede borrarlos desde el host
# -- da "Permission denied" y, con `set -e`, cortaba el script justo antes del
# resumen final (la base ya estaba limpia, pero uno no se enteraba). Por eso el
# borrado se hace DENTRO del contenedor como root (-u 0), que si puede.
DOCS="$RAIZ/documentos-dev"
if [ -d "$DOCS" ]; then
    CUANTOS=$(find "$DOCS" -type f 2>/dev/null | wc -l)
    if [ "$CUANTOS" -gt 0 ]; then
        if docker ps --format '{{.Names}}' | grep -qx "textil_app_dev"; then
            docker exec -u 0 textil_app_dev sh -c 'rm -rf /app/documentos/* /app/documentos/.[!.]* 2>/dev/null' || true
        else
            # Sin la app arriba: contenedor desechable montando la misma carpeta.
            docker run --rm -v "$DOCS:/docs" --entrypoint sh mysql:8.4 \
                -c 'rm -rf /docs/* /docs/.[!.]* 2>/dev/null' || true
        fi
        QUEDAN=$(find "$DOCS" -type f 2>/dev/null | wc -l)
        if [ "$QUEDAN" -eq 0 ]; then
            echo "Borrados $CUANTOS archivo(s) PDF de $DOCS (sus filas ya no existen)."
        else
            # Aviso, no error: la base -- que es lo que importa -- ya quedo limpia.
            echo "AVISO: quedaron $QUEDAN archivo(s) sin borrar en $DOCS."
            echo "       Borralos con: docker exec -u 0 textil_app_dev sh -c 'rm -rf /app/documentos/*'"
        fi
    fi
fi

# --- 6. Como quedo --------------------------------------------------------
echo
echo "Estado FINAL:"
mysql_dev -N -e "
SELECT '  programas            ', COUNT(*) FROM programas
UNION ALL SELECT '  lineas de programa   ', COUNT(*) FROM programa_detalles
UNION ALL SELECT '  con recibido > 0     ', COUNT(*) FROM programa_detalles WHERE cantidad_recibida > 0
UNION ALL SELECT '  recepciones (guias)  ', COUNT(*) FROM recepciones
UNION ALL SELECT '  filas de stock       ', COUNT(*) FROM stock_actual
UNION ALL SELECT '  movimientos kardex   ', COUNT(*) FROM kardex_movimientos
UNION ALL SELECT '  articulos            ', COUNT(*) FROM articulos
UNION ALL SELECT '  colores              ', COUNT(*) FROM colores;"

echo
echo "Listo. Los programas quedaron en cero recibido y el stock vacio."
echo "Ya puedes cargar las guias de a una y ver como baja el pendiente."
echo
echo "Si algo salio mal, se restaura con:"
echo "  docker exec -i $CONTENEDOR mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" $BD < $RESPALDO"
