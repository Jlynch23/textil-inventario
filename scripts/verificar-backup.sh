#!/bin/bash
# Verificacion del CICLO COMPLETO backup -> restore de UN cliente.
#
# "Tener backup no significa tener recuperacion." Este script prueba que un
# backup se puede RESTAURAR de verdad, sobre una base DESECHABLE y AISLADA
# dentro del mismo MySQL del cliente (nunca toca su base real).
#
# No se considera el backup valido hasta que este script termina en OK.
#
# Uso:
#   ./scripts/verificar-backup.sh <slug>                 # el backup mas reciente
#   ./scripts/verificar-backup.sh <slug> <backup.sql.gz> # uno especifico
#   ./scripts/verificar-backup.sh --todos                # el ultimo de cada cliente
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"

BASE_TEST="verificacion_backup"   # desechable, aislada de la base del cliente
BD="textil_inventario"

fail() { echo "  ❌ FALLO: $*" >&2; exit 1; }
ok()   { echo "  ✅ $*"; }

# Tablas que SI o SI tienen que estar. Es mas util que contar: un numero fijo
# ("se esperaban ~40") se desactualiza con cada migracion y da falsos fallos --
# de hecho la version anterior de este script exigia >=30 tablas cuando el
# esquema real tiene 26, asi que habria fallado SIEMPRE. Nadie lo noto porque
# apuntaba al contenedor del modelo single-cliente, ya decomisionado.
TABLAS_CRITICAS=(usuarios roles empresas articulos colores ubicaciones
                 recepciones recepcion_detalles programas programa_detalles
                 stock_actual kardex_movimientos transferencias flyway_schema_history)

verificar_uno() {
    local slug="$1" archivo="${2:-}"
    local env_cliente="$RAIZ/clientes/$slug/.env"
    local contenedor="db_$slug"

    echo "══ Cliente: $slug"

    [ -f "$env_cliente" ] || fail "no existe el cliente '$slug' ($env_cliente)."
    docker ps --format '{{.Names}}' | grep -qx "$contenedor" \
        || fail "el contenedor $contenedor no esta corriendo."

    # Lectura LITERAL (grep|cut), no `source`: una clave con $ o & la
    # interpretaria el shell. Mismo criterio que backup-cliente.sh.
    local root
    root="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$env_cliente" | cut -d= -f2-)"
    [ -n "$root" ] || fail "el .env de '$slug' no tiene MYSQL_ROOT_PASSWORD."

    # 1) Elegir el backup ---------------------------------------------------
    if [ -z "$archivo" ]; then
        archivo="$(ls -1t "${HOME}/backups/$slug"/${slug}_db_*.sql.gz 2>/dev/null | head -1 || true)"
    fi
    [ -n "$archivo" ] && [ -f "$archivo" ] \
        || fail "no se encontro ningun backup (.sql.gz) de '$slug'. Genera uno con backup-cliente.sh."
    echo "   backup: $(basename "$archivo")  ($(du -h "$archivo" | cut -f1), $(date -r "$archivo" '+%Y-%m-%d %H:%M'))"

    # 2) El archivo, antes de restaurar nada --------------------------------
    gzip -t "$archivo" 2>/dev/null || fail "el .gz esta corrupto o truncado."
    ok "gzip integro"

    local tam; tam=$(gunzip -c "$archivo" | wc -c)
    [ "$tam" -gt 0 ] || fail "el dump esta VACIO (0 bytes al descomprimir)."
    gunzip -c "$archivo" | grep -q "CREATE TABLE" || fail "el dump no trae ninguna tabla."
    gunzip -c "$archivo" | grep -q "INSERT INTO"  || fail "el dump no trae datos."
    gunzip -c "$archivo" | grep -q "flyway_schema_history" || fail "el dump no trae el historial de migraciones."
    ok "dump con estructura, datos e historial Flyway ($tam bytes)"

    # 3) Restaurar en una base desechable, y borrarla pase lo que pase -------
    mysql_root() { docker exec -i -e MYSQL_PWD="$root" "$contenedor" mysql -u root "$@"; }
    local scalar_db="$BASE_TEST"
    scalar() { docker exec -e MYSQL_PWD="$root" "$contenedor" mysql -u root -N -B "$scalar_db" -e "$1"; }

    limpiar() { mysql_root -e "DROP DATABASE IF EXISTS \`${BASE_TEST}\`;" 2>/dev/null || true; }
    trap limpiar RETURN

    mysql_root -e "DROP DATABASE IF EXISTS \`${BASE_TEST}\`; CREATE DATABASE \`${BASE_TEST}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    gunzip -c "$archivo" | docker exec -i -e MYSQL_PWD="$root" "$contenedor" mysql -u root "$BASE_TEST" \
        || fail "la restauracion fallo (mysql devolvio error)."
    ok "restauracion completada sin errores (base aislada, no se toco $BD)"

    # 4) Las tablas que importan tienen que estar y ser consultables --------
    local faltantes=()
    for t in "${TABLAS_CRITICAS[@]}"; do
        scalar "SELECT 1 FROM \`$t\` LIMIT 1;" >/dev/null 2>&1 || faltantes+=("$t")
    done
    [ ${#faltantes[@]} -eq 0 ] || fail "faltan o no se pueden consultar: ${faltantes[*]}"
    ok "las ${#TABLAS_CRITICAS[@]} tablas criticas existen y responden"

    # Migraciones del dump vs las del repo: si el backup es mas viejo que el
    # codigo actual, avisa (no es un fallo -- un backup de ayer es valido).
    local mig_dump mig_repo
    mig_dump=$(scalar "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;")
    mig_repo=$(ls -1 "$RAIZ"/src/main/resources/db/migration/*.sql 2>/dev/null | wc -l)
    [ "${mig_dump:-0}" -ge 1 ] || fail "flyway_schema_history sin migraciones exitosas."
    if [ "$mig_dump" -lt "$mig_repo" ]; then
        echo "  ⚠️  migraciones: $mig_dump en el backup vs $mig_repo en el repo (backup anterior al codigo actual)"
    else
        ok "migraciones aplicadas: $mig_dump"
    fi

    # Sin usuarios la app queda inaccesible aunque los datos esten.
    local n_usuarios; n_usuarios=$(scalar "SELECT COUNT(*) FROM usuarios;")
    [ "${n_usuarios:-0}" -ge 1 ] || fail "no hay usuarios en el backup restaurado."
    ok "usuarios: $n_usuarios"

    # 5) Charset: si no sobrevive, las tildes y eñes vuelven rotas ----------
    local charset
    charset=$(docker exec -e MYSQL_PWD="$root" "$contenedor" mysql -u root -N -B \
        -e "SELECT default_character_set_name FROM information_schema.schemata WHERE schema_name='${BASE_TEST}';")
    [ "$charset" = "utf8mb4" ] || fail "charset inesperado: '$charset' (se esperaba utf8mb4)."

    mysql_root "$BASE_TEST" -e "CREATE TABLE _verif (t VARCHAR(50)); INSERT INTO _verif VALUES ('Ñandú áéíóú');"
    local leido; leido=$(scalar "SELECT t FROM _verif;")
    [ "$leido" = "Ñandú áéíóú" ] || fail "los caracteres especiales no sobrevivieron (leido: '$leido')."
    ok "charset utf8mb4 y tildes/ñ intactos"

    limpiar
    trap - RETURN
    echo "  🎉 '$slug' VERIFICADO: la restauracion funciona de punta a punta."
    echo
}

# --- Entrada --------------------------------------------------------------
if [ "${1:-}" = "--todos" ]; then
    shopt -s nullglob
    envs=("$RAIZ"/clientes/*/.env)
    if [ ${#envs[@]} -eq 0 ]; then
        echo "No hay clientes dados de alta (clientes/*/.env vacio). Nada que verificar."
        exit 0
    fi
    fallidos=0
    for e in "${envs[@]}"; do
        slug="$(basename "$(dirname "$e")")"
        verificar_uno "$slug" || fallidos=$((fallidos + 1))
    done
    [ "$fallidos" -eq 0 ] || { echo "❌ $fallidos cliente(s) con backup NO verificable." >&2; exit 1; }
    echo "Todos los backups verificados."
elif [ $# -ge 1 ]; then
    verificar_uno "$1" "${2:-}"
else
    echo "Uso: $0 <slug> [backup.sql.gz]   |   $0 --todos" >&2
    exit 1
fi
