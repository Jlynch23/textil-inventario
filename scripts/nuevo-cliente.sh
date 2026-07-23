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

RED_COMPARTIDA="texcontrol_red"
IMAGEN="texcontrol-app:latest"
COMPOSE_CLIENTE="multicliente/docker-compose.cliente.yml"
NGINX_CLIENTES="multicliente/nginx/clientes"
RESERVADOS="login www adminer api mail ftp admin"

# --- 1. Validar argumentos -------------------------------------------------
if [ $# -ne 2 ]; then
    echo "Uso: $0 <slug> \"<Nombre de la empresa>\"" >&2
    echo "Ej:  $0 laura \"Laura & Clemente\"" >&2
    exit 1
fi

SLUG="$1"
NOMBRE_EMPRESA="$2"

# El slug debe ser exactamente lo que aceptaria el lanzador: [a-z0-9].
if ! printf '%s' "$SLUG" | grep -qE '^[a-z0-9]+$'; then
    echo "ERROR: el slug '$SLUG' es invalido. Solo minusculas y numeros, sin" >&2
    echo "       espacios ni tildes (ej: laura, textiljuan, acme2)." >&2
    exit 1
fi

for r in $RESERVADOS; do
    if [ "$SLUG" = "$r" ]; then
        echo "ERROR: '$SLUG' es un subdominio reservado, no se puede usar como empresa." >&2
        exit 1
    fi
done

DIR_CLIENTE="$RAIZ/clientes/$SLUG"
ENV_CLIENTE="$DIR_CLIENTE/.env"
CONF_NGINX="$NGINX_CLIENTES/cliente-$SLUG.conf"

if [ -d "$DIR_CLIENTE" ]; then
    echo "ERROR: el cliente '$SLUG' ya existe ($DIR_CLIENTE)." >&2
    echo "       Para recrearlo, primero eliminalo: ./scripts/eliminar-cliente.sh $SLUG" >&2
    exit 1
fi

# --- 2. Red compartida e imagen de la app ----------------------------------
if ! docker network inspect "$RED_COMPARTIDA" >/dev/null 2>&1; then
    echo "Creando la red compartida $RED_COMPARTIDA..."
    docker network create "$RED_COMPARTIDA"
fi

if ! docker image inspect "$IMAGEN" >/dev/null 2>&1; then
    echo "Construyendo la imagen compartida $IMAGEN (una sola vez para todos)..."
    docker build -t "$IMAGEN" "$RAIZ"
fi

# --- 3. Credenciales propias del cliente -----------------------------------
# openssl rand -hex genera secretos aleatorios; cada cliente tiene los suyos.
DB_PASSWORD="$(openssl rand -hex 24)"
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"

# --- 4. Carpeta y .env del cliente -----------------------------------------
echo "Creando el cliente '$SLUG' ($NOMBRE_EMPRESA)..."
mkdir -p "$DIR_CLIENTE/documentos"

# El .env lleva credenciales: se crea con permisos 600 (solo el dueño lo lee)
# y vive bajo clientes/ que esta en .gitignore (NUNCA se commitea).
umask 077
cat > "$ENV_CLIENTE" <<EOF
# Generado por nuevo-cliente.sh. Contiene credenciales: NO commitear.
CLIENTE_SLUG=$SLUG
NOMBRE_EMPRESA=$NOMBRE_EMPRESA
DB_PASSWORD=$DB_PASSWORD
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}
MAX_UPLOAD_SIZE=${MAX_UPLOAD_SIZE:-25MB}
DOCUMENTOS_DIR=$DIR_CLIENTE/documentos
JVM_XMX=${JVM_XMX:-384m}
EOF
umask 022

if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
    echo "AVISO: ANTHROPIC_API_KEY no estaba en el entorno; el OCR quedara"
    echo "       desactivado para este cliente. Se puede agregar luego en $ENV_CLIENTE"
    echo "       y reiniciar el stack del cliente."
fi

# --- 5. Levantar el stack del cliente --------------------------------------
echo "Levantando el stack de '$SLUG' (app + su MySQL)..."
docker compose -p "texcontrol_$SLUG" --env-file "$ENV_CLIENTE" \
    -f "$COMPOSE_CLIENTE" up -d

# --- 6. Bloque de nginx + recarga ------------------------------------------
echo "Generando el bloque de nginx para $SLUG.texcontrol.pe..."
cat > "$CONF_NGINX" <<EOF
# Cliente: $NOMBRE_EMPRESA ($SLUG.texcontrol.pe). Generado por nuevo-cliente.sh.
server {
    listen 443 ssl;
    http2 on;
    server_name $SLUG.texcontrol.pe;

    ssl_certificate     /etc/letsencrypt/live/texcontrol.pe/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/texcontrol.pe/privkey.pem;

    # >= MAX_UPLOAD_SIZE (25MB) o nginx corta la subida de guias/ZIP con 413.
    client_max_body_size 30m;

    location / {
        proxy_pass http://app_$SLUG:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;

        # El OCR puede tardar hasta 90s por PDF; sin esto nginx corta a los 60s.
        proxy_read_timeout 120s;
        proxy_connect_timeout 30s;
    }
}
EOF

if docker ps --format '{{.Names}}' | grep -q '^textil_nginx$'; then
    echo "Validando y recargando nginx..."
    if docker exec textil_nginx nginx -t; then
        docker exec textil_nginx nginx -s reload
    else
        echo "ERROR: nginx -t fallo. Revisa $CONF_NGINX. El cliente quedo levantado" >&2
        echo "       pero el proxy NO se recargo." >&2
        exit 1
    fi
else
    echo "AVISO: el proxy (textil_nginx) no esta corriendo. Levantalo con:"
    echo "       docker compose -p texcontrol_proxy -f multicliente/docker-compose.proxy.yml up -d"
fi

# --- 7. Resumen ------------------------------------------------------------
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
