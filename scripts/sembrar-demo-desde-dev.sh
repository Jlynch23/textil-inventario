#!/bin/bash
# Siembra el AMBIENTE DEMO con los datos ORIGINALES de la base de DEV:
# catalogo real (tipos de tela, titulos, composiciones, acabados, colores con
# sus codigos FAST DYE), articulos, empresas, ubicaciones, PROGRAMAS con sus
# lineas y el stock actual.
#
# ¿Para que? El matching del OCR cruza la guia contra el catalogo y el programa.
# Con el catalogo sintetico del demo-seed, una guia REAL de FAST DYE no calza y
# no descuenta nada. Con los datos originales de dev, subir una guia real en el
# demo matchea y descuenta el saldo del programa automaticamente -> demo veraz.
#
# Que hace:
#   1. BORRA en db_demo todos los datos de negocio (catalogo, stock, kardex,
#      recepciones, transferencias, programas, documentos) -- las CUENTAS DEMO
#      (usuarios/roles) NO se tocan: los prospectos siguen entrando igual.
#   2. Copia de textil_mysql_dev -> db_demo las tablas de negocio "seguras"
#      (las que no referencian usuarios): empresas, ubicaciones, tipos_tela,
#      titulos, composiciones, acabados, colores, articulos, programas,
#      programa_detalles, stock_actual.
#      (Kardex, recepciones y transferencias NO se copian: referencian a los
#      usuarios de dev, que en el demo no existen. El kardex del demo arranca
#      vacio y se va llenando con lo que hagan los prospectos.)
#
# Requisitos: correr desde el clon que tiene al demo (clientes/demo/.env) y con
# el stack dev arriba (textil_mysql_dev corriendo). Re-ejecutable: siempre parte
# de un borrado limpio, no duplica.
#
# Uso:
#   ./scripts/sembrar-demo-desde-dev.sh [slug]     # slug por defecto: demo
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"

SLUG="${1:-demo}"

# --- 0. Credenciales y contenedores ----------------------------------------
ENV_DEMO="$RAIZ/clientes/$SLUG/.env"
ENV_DEV="$RAIZ/.env.dev"
if [ ! -f "$ENV_DEMO" ]; then
    echo "ERROR: no encuentro $ENV_DEMO (¿el demo existe en ESTE clon?)." >&2
    exit 1
fi
if [ ! -f "$ENV_DEV" ]; then
    echo "ERROR: no encuentro $ENV_DEV (correr desde el clon dev, donde vive .env.dev)." >&2
    exit 1
fi
ROOT_DEMO="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_DEMO" | cut -d= -f2-)"
ROOT_DEV="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_DEV" | cut -d= -f2-)"

for c in "db_$SLUG" textil_mysql_dev; do
    if ! docker ps --format '{{.Names}}' | grep -q "^$c$"; then
        echo "ERROR: el contenedor $c no esta corriendo." >&2
        exit 1
    fi
done

# Tablas SEGURAS de copiar (sin FK a usuarios). El orden no importa: el dump
# de mysqldump desactiva FOREIGN_KEY_CHECKS durante el import.
TABLAS="empresas ubicaciones tipos_tela titulos composiciones acabados colores articulos programas programa_detalles stock_actual"

echo "Vas a REEMPLAZAR el catalogo/stock/programas del demo '$SLUG' con los datos"
echo "ORIGINALES de dev (textil_mysql_dev). Las cuentas demo NO se tocan."
printf "Escribe 'si' para continuar: "
read -r resp
if [ "$resp" != "si" ]; then
    echo "Cancelado."
    exit 1
fi

# --- 1. Limpiar los datos de negocio del demo (conserva usuarios/roles) -----
# Mismo criterio que la migracion V34 (limpiar_datos_semilla).
echo "Limpiando datos de negocio en db_$SLUG..."
docker exec -e MYSQL_PWD="$ROOT_DEMO" "db_$SLUG" mysql -u root textil_inventario -e "
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM log_eventos;
DELETE FROM kardex_movimientos;
DELETE FROM stock_actual;
DELETE FROM salidas_rapidas;
DELETE FROM entradas_rapidas;
DELETE FROM transferencia_distribucion;
DELETE FROM transferencia_detalle;
DELETE FROM transferencias;
DELETE FROM recepcion_detalles;
DELETE FROM recepcion_documentos;
DELETE FROM recepciones;
DELETE FROM programa_detalles;
DELETE FROM programas;
DELETE FROM documentos_historicos;
DELETE FROM documentos;
DELETE FROM articulos;
DELETE FROM colores;
DELETE FROM acabados;
DELETE FROM composiciones;
DELETE FROM titulos;
DELETE FROM tipos_tela;
DELETE FROM ubicaciones;
DELETE FROM empresas;
SET FOREIGN_KEY_CHECKS = 1;"

# --- 2. Copiar los datos originales de dev ----------------------------------
echo "Copiando de textil_mysql_dev -> db_$SLUG: $TABLAS"
docker exec -e MYSQL_PWD="$ROOT_DEV" textil_mysql_dev \
    mysqldump -u root --no-create-info --skip-triggers --single-transaction \
    --default-character-set=utf8mb4 textil_inventario $TABLAS \
  | docker exec -i -e MYSQL_PWD="$ROOT_DEMO" "db_$SLUG" \
    mysql -u root --default-character-set=utf8mb4 textil_inventario

# --- 3. Resumen -------------------------------------------------------------
echo ""
echo "Listo. Filas en db_$SLUG tras la siembra:"
docker exec -e MYSQL_PWD="$ROOT_DEMO" "db_$SLUG" mysql -u root -N textil_inventario -e "
SELECT CONCAT('  colores:            ', COUNT(*)) FROM colores
UNION ALL SELECT CONCAT('  articulos:          ', COUNT(*)) FROM articulos
UNION ALL SELECT CONCAT('  programas:          ', COUNT(*)) FROM programas
UNION ALL SELECT CONCAT('  lineas de programa: ', COUNT(*)) FROM programa_detalles
UNION ALL SELECT CONCAT('  stock_actual:       ', COUNT(*)) FROM stock_actual
UNION ALL SELECT CONCAT('  cuentas (intactas): ', COUNT(*)) FROM usuarios;"
echo ""
echo "El demo ahora tiene el catalogo/programas ORIGINALES de dev: una guia real"
echo "de FAST DYE subida en el demo va a matchear y descontar automaticamente."
echo "OJO: resetear-demo.sh vuelve al seed sintetico; para volver a los datos"
echo "originales, re-corre este script despues del reset."
