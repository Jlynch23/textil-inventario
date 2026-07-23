#!/bin/bash
# Migra un despliegue ACTUAL (modelo de un solo cliente, docker-compose.prod.yml)
# al modelo multi-cliente AISLADO, sin perder datos.
#
# Uso:
#   ./scripts/migrar-cliente.sh <slug> "<Nombre>" <dump.sql.gz> [ruta-documentos]
#
# Ejemplo (migrar la instalacion actual a laura.texcontrol.pe):
#   ./scripts/backup-db.sh                       # 1) respaldar la BD vieja
#   ./scripts/migrar-cliente.sh laura "Laura & Clemente" \
#       ~/backups/textil-inventario/textil_inventario_XXXX.sql.gz ./documentos
#
# ORDEN correcto (importante): se restaura el dump ANTES de arrancar la app. El
# dump ya trae el esquema, los datos y el historial de Flyway (hasta V35), asi
# que cuando la app arranca, Flyway ve que ya esta al dia y NO re-migra ni
# choca; solo valida el esquema (ddl-auto: validate) y sigue.
#
# NO toca la instalacion vieja: podes dejarla corriendo hasta confirmar que la
# nueva anda, y recien ahi apagarla.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
# shellcheck source=scripts/lib-cliente.sh
source "$RAIZ/scripts/lib-cliente.sh"

# --- 1. Validar argumentos -------------------------------------------------
if [ $# -lt 3 ] || [ $# -gt 4 ]; then
    echo "Uso: $0 <slug> \"<Nombre>\" <dump.sql.gz> [ruta-documentos]" >&2
    exit 1
fi

SLUG="$1"
NOMBRE_EMPRESA="$2"
DUMP="$3"
DOCUMENTOS_ORIGEN="${4:-}"

mc_validar_slug "$SLUG" || exit 1

DIR_CLIENTE="$RAIZ/clientes/$SLUG"
if [ -d "$DIR_CLIENTE" ]; then
    echo "ERROR: el cliente '$SLUG' ya existe ($DIR_CLIENTE). Migrar es para uno nuevo." >&2
    exit 1
fi
if [ ! -f "$DUMP" ]; then
    echo "ERROR: no existe el dump $DUMP" >&2
    exit 1
fi

echo "Vas a migrar la instalacion actual a un cliente aislado:"
echo "  slug:        $SLUG   ->   https://$SLUG.texcontrol.pe"
echo "  nombre:      $NOMBRE_EMPRESA"
echo "  dump:        $DUMP"
echo "  documentos:  ${DOCUMENTOS_ORIGEN:-(ninguno)}"
read -p "Continuar? Escribe 'si': " CONFIRMACION
if [ "$CONFIRMACION" != "si" ]; then
    echo "Cancelado."
    exit 0
fi

# --- 2. Preparar el cliente y levantar SOLO su MySQL -----------------------
mc_asegurar_red_e_imagen "$RAIZ"

echo "Creando el cliente '$SLUG'..."
mc_generar_env "$RAIZ" "$SLUG" "$NOMBRE_EMPRESA"

echo "Levantando SOLO db_$SLUG (la app se arranca despues del restore)..."
mc_compose "$RAIZ" "$SLUG" up -d db
mc_esperar_db "$SLUG" || exit 1

# --- 3. Restaurar el dump dentro de db_<slug> ------------------------------
# La clave de root del cliente nuevo esta en su propio .env recien generado.
set -a; source "$DIR_CLIENTE/.env"; set +a

echo "Restaurando el dump dentro de db_$SLUG..."
# Password por MYSQL_PWD (no por -p"...") para no exponerla en la lista de
# procesos, misma regla que backup-db.sh/restore-db.sh.
gunzip -c "$DUMP" | docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
    "db_$SLUG" mysql -u root textil_inventario
echo "Restore completado."

# --- 4. Copiar documentos (PDFs de guias/facturas) -------------------------
if [ -n "$DOCUMENTOS_ORIGEN" ]; then
    if [ -d "$DOCUMENTOS_ORIGEN" ]; then
        echo "Copiando documentos desde $DOCUMENTOS_ORIGEN..."
        # El "/." copia el CONTENIDO de la carpeta, no la carpeta en si.
        cp -a "$DOCUMENTOS_ORIGEN/." "$DIR_CLIENTE/documentos/"
    else
        echo "AVISO: la ruta de documentos '$DOCUMENTOS_ORIGEN' no existe; se omite."
    fi
fi

# --- 5. Arrancar la app (Flyway ve V35 ya aplicado y no re-migra) -----------
echo "Arrancando app_$SLUG..."
mc_compose "$RAIZ" "$SLUG" up -d
mc_esperar_app "$SLUG"

# --- 6. Bloque de nginx + recarga ------------------------------------------
echo "Generando el bloque de nginx para $SLUG.texcontrol.pe..."
mc_generar_nginx "$RAIZ" "$SLUG" "$NOMBRE_EMPRESA"
mc_recargar_nginx || exit 1

# --- 7. Resumen ------------------------------------------------------------
echo ""
echo "======================================================================"
echo " Migracion completada: $NOMBRE_EMPRESA"
echo " URL:        https://$SLUG.texcontrol.pe"
echo " Contenedor: app_$SLUG  +  db_$SLUG (datos restaurados del dump)"
echo ""
echo " La instalacion VIEJA sigue intacta. Verifica que la nueva anda"
echo " (login, stock, kardex) ANTES de apagar la vieja con:"
echo "   docker compose -f docker-compose.yml -f docker-compose.prod.yml down"
echo "======================================================================"
