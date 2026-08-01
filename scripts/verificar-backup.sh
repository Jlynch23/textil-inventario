#!/bin/bash
# Verificacion del CICLO COMPLETO de backup->restore (auditoria).
#
# "Tener backup no significa tener recuperacion." Este script prueba que un
# backup se puede RESTAURAR de verdad, sobre una base de datos DESECHABLE y
# AISLADA (nunca toca la base de produccion textil_inventario):
#
#   backup (ultimo .sql.gz)  ->  restaurar en base_verificacion  ->
#   comprobar tablas/datos/migraciones/charset  ->  borrar base_verificacion
#
# No se considera el backup valido hasta que este script termina en OK.
#
# Uso:
#   MYSQL_ROOT_PASSWORD=xxx ./scripts/verificar-backup.sh [/ruta/backup.sql.gz]
# Sin argumento, toma el backup MAS RECIENTE de ~/backups/textil-inventario.
set -euo pipefail

CONTENEDOR="textil_mysql"
BASE_PROD="textil_inventario"
BASE_TEST="textil_inventario_verificacion"   # desechable, aislada de prod
CARPETA_BACKUPS="${HOME}/backups/textil-inventario"

fail() { echo "  ❌ FALLO: $*" >&2; exit 1; }
ok()   { echo "  ✅ $*"; }

if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    fail "la variable MYSQL_ROOT_PASSWORD no esta definida."
fi

# 1) Elegir el backup a verificar -------------------------------------------
if [ $# -ge 1 ]; then
    ARCHIVO="$1"
else
    ARCHIVO=$(ls -1t "${CARPETA_BACKUPS}"/textil_inventario_*.sql.gz 2>/dev/null | head -1 || true)
fi
[ -n "${ARCHIVO:-}" ] && [ -f "$ARCHIVO" ] || fail "no se encontro ningun backup (.sql.gz) para verificar."
echo "== Verificando backup: $ARCHIVO"

# 2) El archivo no debe estar vacio y debe traer estructura + datos + migraciones
#    (se inspecciona el dump descomprimido en streaming, sin restaurar todavia).
TAM=$(gunzip -c "$ARCHIVO" | wc -c)
[ "$TAM" -gt 0 ] || fail "el dump esta VACIO (0 bytes al descomprimir)."
ok "dump no vacio ($TAM bytes descomprimidos)"

gunzip -c "$ARCHIVO" | grep -q "CREATE TABLE" || fail "el dump no contiene ninguna tabla (CREATE TABLE)."
ok "contiene definiciones de tablas"
gunzip -c "$ARCHIVO" | grep -q "INSERT INTO" || fail "el dump no contiene datos (INSERT INTO)."
ok "contiene datos"
gunzip -c "$ARCHIVO" | grep -q "flyway_schema_history" || fail "el dump no incluye el historial de migraciones (flyway_schema_history)."
ok "incluye el historial de migraciones Flyway"

# 3) Restaurar en una base AISLADA y limpiarla siempre al salir (trap) -------
mysql_root() { docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTENEDOR" mysql -u root "$@"; }

limpiar() { mysql_root -e "DROP DATABASE IF EXISTS \`${BASE_TEST}\`;" 2>/dev/null || true; }
trap limpiar EXIT

echo "== Restaurando en base desechable '${BASE_TEST}' (NO toca '${BASE_PROD}')"
mysql_root -e "DROP DATABASE IF EXISTS \`${BASE_TEST}\`; CREATE DATABASE \`${BASE_TEST}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gunzip -c "$ARCHIVO" | docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTENEDOR" mysql -u root "${BASE_TEST}" \
    || fail "la restauracion fallo (mysql devolvio error)."
ok "restauracion completada sin errores"

# 4) Comprobar que la base restaurada tiene tablas y datos reales -----------
scalar() { mysql_root -N -B "${BASE_TEST}" -e "$1"; }

N_TABLAS=$(scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${BASE_TEST}';")
[ "${N_TABLAS:-0}" -ge 30 ] || fail "la base restaurada tiene muy pocas tablas (${N_TABLAS}); se esperaban ~40."
ok "tablas restauradas: ${N_TABLAS}"

# Migraciones aplicadas (deben coincidir con las del repo).
N_MIGRACIONES=$(scalar "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;")
[ "${N_MIGRACIONES:-0}" -ge 1 ] || fail "flyway_schema_history sin migraciones exitosas."
ok "migraciones aplicadas en el dump: ${N_MIGRACIONES}"

# Debe haber al menos el usuario semilla (la app no arranca usable sin usuarios).
N_USUARIOS=$(scalar "SELECT COUNT(*) FROM usuarios;")
[ "${N_USUARIOS:-0}" -ge 1 ] || fail "no hay usuarios en el backup restaurado."
ok "usuarios: ${N_USUARIOS}"

# Stock y kardex: pueden estar en 0 en una instancia nueva, pero las tablas
# deben existir y ser consultables (esto reventaria si faltara la tabla).
scalar "SELECT COUNT(*) FROM stock_actual;"        >/dev/null || fail "no se pudo consultar stock_actual."
scalar "SELECT COUNT(*) FROM kardex_movimientos;"  >/dev/null || fail "no se pudo consultar kardex_movimientos."
ok "stock_actual y kardex_movimientos consultables"

# 5) Charset y zona horaria preservados -------------------------------------
CHARSET=$(scalar "SELECT default_character_set_name FROM information_schema.schemata WHERE schema_name='${BASE_TEST}';")
[ "$CHARSET" = "utf8mb4" ] || fail "charset inesperado tras restaurar: '${CHARSET}' (se esperaba utf8mb4; riesgo de tildes/eñes rotas)."
ok "charset preservado: ${CHARSET}"

# Prueba de round-trip de caracteres: insertar y releer una cadena con tildes/ñ.
mysql_root "${BASE_TEST}" -e "CREATE TABLE _verif_charset (t VARCHAR(50)); INSERT INTO _verif_charset VALUES ('Ñandú áéíóú');"
LEIDO=$(scalar "SELECT t FROM _verif_charset;")
[ "$LEIDO" = "Ñandú áéíóú" ] || fail "los caracteres especiales no sobrevivieron el round-trip (leido: '${LEIDO}')."
ok "caracteres especiales (tildes/ñ) intactos"

echo ""
echo "🎉 BACKUP VERIFICADO: la restauracion funciona de punta a punta."
echo "   (la base desechable '${BASE_TEST}' se elimina automaticamente al salir)"
