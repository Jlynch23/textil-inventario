#!/bin/bash
# Librería común del modelo multi-cliente. NO se ejecuta sola: la cargan con
# `source` los scripts nuevo-cliente.sh / migrar-cliente.sh / eliminar-cliente.sh
# / backup-cliente.sh / listar-clientes.sh, para no duplicar constantes ni lógica.

# --- Constantes compartidas ------------------------------------------------
MC_RED_COMPARTIDA="texcontrol_red"
MC_IMAGEN="texcontrol-app:latest"
MC_COMPOSE_CLIENTE="multicliente/docker-compose.cliente.yml"
MC_NGINX_CLIENTES="multicliente/nginx/clientes"
MC_NGINX_CONTENEDOR="texcontrol_proxy_nginx"
# Subdominios que NO se pueden asignar como empresa (chocan con la infra).
MC_RESERVADOS="login www adminer api mail ftp admin"

# --- Validación del slug (subdominio) --------------------------------------
# El slug debe ser exactamente lo que aceptaría el lanzador: [a-z0-9].
mc_validar_slug() {
    local slug="$1"
    if ! printf '%s' "$slug" | grep -qE '^[a-z0-9]+$'; then
        echo "ERROR: el slug '$slug' es invalido. Solo minusculas y numeros, sin" >&2
        echo "       espacios ni tildes (ej: laura, textiljuan, acme2)." >&2
        return 1
    fi
    local r
    for r in $MC_RESERVADOS; do
        if [ "$slug" = "$r" ]; then
            echo "ERROR: '$slug' es un subdominio reservado, no se puede usar como empresa." >&2
            return 1
        fi
    done
    return 0
}

# --- Red compartida e imagen de la app -------------------------------------
mc_asegurar_red_e_imagen() {
    local raiz="$1"
    if ! docker network inspect "$MC_RED_COMPARTIDA" >/dev/null 2>&1; then
        echo "Creando la red compartida $MC_RED_COMPARTIDA..."
        docker network create "$MC_RED_COMPARTIDA"
    fi
    if ! docker image inspect "$MC_IMAGEN" >/dev/null 2>&1; then
        echo "Construyendo la imagen compartida $MC_IMAGEN (una sola vez para todos)..."
        docker build -t "$MC_IMAGEN" "$raiz"
    fi
}

# --- Crear carpeta + .env del cliente (credenciales aleatorias) ------------
# Deja creado clientes/<slug>/ con documentos/ y .env (permisos 600).
mc_generar_env() {
    local raiz="$1" slug="$2" nombre="$3"
    local dir_cliente="$raiz/clientes/$slug"
    local env_cliente="$dir_cliente/.env"

    mkdir -p "$dir_cliente/documentos"
    # openssl rand -hex: secretos aleatorios; cada cliente tiene los suyos.
    local db_pass root_pass
    db_pass="$(openssl rand -hex 24)"
    root_pass="$(openssl rand -hex 24)"

    # umask 077: el .env con credenciales queda solo-lectura del dueño (600).
    umask 077
    cat > "$env_cliente" <<EOF
# Generado por el modelo multi-cliente. Contiene credenciales: NO commitear.
CLIENTE_SLUG=$slug
NOMBRE_EMPRESA=$nombre
DB_PASSWORD=$db_pass
MYSQL_ROOT_PASSWORD=$root_pass
ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}
MAX_UPLOAD_SIZE=${MAX_UPLOAD_SIZE:-25MB}
DOCUMENTOS_DIR=$dir_cliente/documentos
JVM_XMX=${JVM_XMX:-384m}
EOF
    umask 022

    if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
        echo "AVISO: ANTHROPIC_API_KEY no estaba en el entorno; el OCR quedara"
        echo "       desactivado para este cliente. Se puede agregar luego en"
        echo "       $env_cliente y reiniciar el stack del cliente."
    fi
}

# --- Wrapper de docker compose para el proyecto de un cliente --------------
# Uso: mc_compose <raiz> <slug> <args de compose...>
mc_compose() {
    local raiz="$1" slug="$2"; shift 2
    docker compose -p "texcontrol_$slug" \
        --env-file "$raiz/clientes/$slug/.env" \
        -f "$raiz/$MC_COMPOSE_CLIENTE" "$@"
}

# --- Esperar a que el MySQL del cliente esté healthy -----------------------
mc_esperar_db() {
    local slug="$1" i estado
    echo "Esperando a que db_$slug este healthy..."
    for i in $(seq 1 40); do
        estado="$(docker inspect --format='{{.State.Health.Status}}' "db_$slug" 2>/dev/null || echo "")"
        if [ "$estado" = "healthy" ]; then
            return 0
        fi
        sleep 3
    done
    echo "ERROR: db_$slug no llego a estar healthy a tiempo. Revisa: docker logs db_$slug" >&2
    return 1
}

# --- Esperar a que la app del cliente termine de arrancar -------------------
# La imagen JRE no trae curl/wget, asi que en vez de un healthcheck HTTP se
# espera a la linea "Started" de Spring en los logs (y se aborta si el
# contenedor se cae en el intento).
mc_esperar_app() {
    local slug="$1" i
    echo "Esperando a que app_$slug termine de arrancar..."
    for i in $(seq 1 40); do
        if ! docker ps --format '{{.Names}}' | grep -q "^app_$slug$"; then
            echo "ERROR: app_$slug se detuvo al arrancar. Revisa: docker logs app_$slug" >&2
            return 1
        fi
        if docker logs "app_$slug" 2>&1 | grep -q "Started .* in .* seconds"; then
            return 0
        fi
        sleep 3
    done
    echo "AVISO: no confirme el arranque de app_$slug en el tiempo esperado."
    echo "       Puede seguir levantando. Revisa: docker logs -f app_$slug"
    return 0
}

# --- Generar el bloque de nginx del cliente --------------------------------
mc_generar_nginx() {
    local raiz="$1" slug="$2" nombre="$3"
    local conf="$raiz/$MC_NGINX_CLIENTES/cliente-$slug.conf"
    cat > "$conf" <<EOF
# Cliente: $nombre ($slug.texcontrol.pe). Generado por el modelo multi-cliente.
server {
    listen 443 ssl;
    http2 on;
    server_name $slug.texcontrol.pe;

    ssl_certificate     /etc/letsencrypt/live/texcontrol.pe/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/texcontrol.pe/privkey.pem;

    # >= MAX_UPLOAD_SIZE (25MB) o nginx corta la subida de guias/ZIP con 413.
    client_max_body_size 30m;

    location / {
        proxy_pass http://app_$slug:8080;
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
}

# --- Hash bcrypt compatible con Spring Security ----------------------------
# La imagen JRE de la app no expone un CLI de hashing y en el VPS no se puede
# asumir htpasswd/bcrypt instalados; se usa un contenedor efimero httpd:alpine
# (trae htpasswd). htpasswd -B genera bcrypt ($2y$), que BCryptPasswordEncoder
# de Spring acepta igual que $2a$. -i lee la clave por STDIN para que NO quede
# en la lista de procesos. Cost 10 = el strength por defecto de la app.
MC_IMAGEN_BCRYPT="httpd:2.4-alpine"

mc_hash_bcrypt() {
    local pass="$1"
    if ! docker image inspect "$MC_IMAGEN_BCRYPT" >/dev/null 2>&1; then
        docker pull "$MC_IMAGEN_BCRYPT" >/dev/null
    fi
    printf '%s' "$pass" | docker run --rm -i "$MC_IMAGEN_BCRYPT" \
        htpasswd -niBC 10 "" | cut -d: -f2
}

# --- Ejecutar SQL contra la base de UN cliente (como root) -----------------
# Lee la clave de root del propio clientes/<slug>/.env. -N quita los encabezados.
mc_sql_cliente() {
    local raiz="$1" slug="$2" sql="$3"
    local root
    root="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$raiz/clientes/$slug/.env" | cut -d= -f2-)"
    docker exec -e MYSQL_PWD="$root" "db_$slug" \
        mysql -u root -N -e "$sql" textil_inventario
}

# --- Validar y recargar nginx (si el proxy está corriendo) -----------------
mc_recargar_nginx() {
    if docker ps --format '{{.Names}}' | grep -q "^${MC_NGINX_CONTENEDOR}$"; then
        echo "Validando y recargando nginx..."
        if docker exec "$MC_NGINX_CONTENEDOR" nginx -t; then
            docker exec "$MC_NGINX_CONTENEDOR" nginx -s reload
            return 0
        fi
        echo "ERROR: nginx -t fallo. Revisa el bloque generado. El proxy NO se recargo." >&2
        return 1
    fi
    echo "AVISO: el proxy ($MC_NGINX_CONTENEDOR) no esta corriendo. Levantalo con:"
    echo "       docker compose -p texcontrol_proxy -f multicliente/docker-compose.proxy.yml up -d"
    return 0
}
