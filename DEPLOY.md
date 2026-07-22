# Despliegue en el VPS

Guía para poner TexControl a correr en el servidor (por IP, sin dominio todavía). Todo corre en Docker: MySQL, la app y Nginx como reverse proxy.

## 1. Prerrequisitos en el VPS

```bash
# Docker + plugin de compose (Ubuntu/Debian)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# cerrar sesión y volver a entrar para que el grupo tome efecto
```

**No abras el puerto 80 al público en el firewall de la instancia.** El acceso está pensado para ser solo por Tailscale (o la VPN que uses) — `docker-compose.prod.yml` publica nginx únicamente en la IP que definas como `BIND_IP` en el `.env` (ej. tu IP de Tailscale), nunca en `0.0.0.0`. Si `BIND_IP` no se define, cae por defecto en `0.0.0.0` (todas las interfaces, incluida la pública) — definila siempre en producción. Ver sección 6 para el día que agregues un dominio público de verdad, que es el único caso en el que sí correspondería abrir el 80/443 públicamente (con HTTPS).

Los puertos 3307 (MySQL) y 8081 (Adminer) **no hace falta abrirlos** — `docker-compose.yml` ya los deja bindeados solo a `127.0.0.1`, así que ni siquiera están expuestos fuera del servidor. El puerto 22 (SSH) sí necesita estar abierto para poder conectarte; restringirlo a tu rango de Tailscale (en vez de "Anywhere") es una mejora recomendada pero no forma parte de este documento.

## 2. Primera vez

```bash
git clone https://github.com/Jlynch23/textil-inventario.git
cd textil-inventario
```

Creá el archivo `.env` (Docker Compose lo lee automáticamente si está en la misma carpeta — **no** hace falta exportar las variables a mano):

```bash
cp .env.example .env
nano .env
```

Completá con valores reales:

```bash
DB_USERNAME=textil_user
DB_PASSWORD=<elegí una contraseña fuerte>
MYSQL_ROOT_PASSWORD=<otra contraseña fuerte, DISTINTA de DB_PASSWORD>
ANTHROPIC_API_KEY=<tu API key de Anthropic, si vas a usar el OCR>
DOCUMENTOS_PATH=./documentos
MAX_UPLOAD_SIZE=25MB
NOMBRE_EMPRESA=<nombre del negocio que va bajo el logo TEXCONTROL>
BIND_IP=<tu IP de Tailscale, ej. 100.x.x.x -- sin esto, nginx queda expuesto a 0.0.0.0>
```

Levantá todo (la primera vez construye la imagen de la app, tarda unos minutos):

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Verificá que los 4 contenedores estén corriendo:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

Entrá a `http://<IP-del-servidor>/` — debería aparecer el login. El usuario admin es el que sembró Flyway en `V2__seed_data.sql` (o el que hayas creado desde `/usuarios`).

## 3. Actualizar (redeploy)

Cada vez que haya cambios nuevos en `main`:

```bash
./scripts/deploy.sh
```

Esto trae el código nuevo, levanta MySQL primero y sincroniza el password de `textil_user` con el `DB_PASSWORD` actual del `.env` (por si quedó desincronizado por cualquier motivo), reconstruye la imagen de la app y reinicia los contenedores — los datos de MySQL no se tocan. **No edites archivos a mano en el servidor** (salvo el `.env`, que no está versionado): `deploy.sh` hace `git reset --hard origin/main`, así que cualquier cambio local a un archivo del repo se pierde en el próximo redeploy.

## 4. Logs y diagnóstico

```bash
# Logs de la app en vivo
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f app

# Logs de nginx
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f nginx

# Estado de salud de MySQL
docker compose ps mysql
```

## 5. Backups

Los backups de base de datos ya están cubiertos por `scripts/backup-db.sh` (ver README). Sumá la carpeta `./documentos/` (los PDFs de guías/facturas) a lo que sea que uses para respaldar el servidor — es una carpeta real en disco, no un volumen Docker aislado, así que un `rsync` o `tar` normal la cubre.

## 6. Dominio + HTTPS (Cloudflare + Let's Encrypt wildcard)

El dominio es **`texcontrol.pe`** y cada cliente entra por un **subdominio**
(`textillaura.texcontrol.pe`, etc.). El DNS lo maneja **Cloudflare en modo
DNS-only** (nube gris): dos registros `A` — `texcontrol.pe` y `*.texcontrol.pe`
— apuntando a la IP pública del VPS. El HTTPS lo damos nosotros con un
**certificado wildcard** de Let's Encrypt (validación DNS-01 vía API de Cloudflare).

**Requisito previo:** dominio en estado **Active** en Cloudflare y los dos
registros `A` creados (en gris / DNS-only).

### 6.1 Abrir el firewall del VPS (Vultr)
Abrir **80 y 443** al público (antes solo se accedía por Tailscale). En el panel
de Vultr (Firewall) o con `ufw`: permitir 80 y 443 desde `0.0.0.0/0`. Dejar 22
(SSH) como estaba.

### 6.2 Poner la app en modo público
En el `.env`, fijar `BIND_IP=0.0.0.0` para que nginx escuche en la interfaz
pública (no solo en Tailscale).

### 6.3 Emitir el certificado wildcard (una sola vez)
1. En Cloudflare crear un **API token** (My Profile → API Tokens → Create Token →
   plantilla **Edit zone DNS**), con permiso *Zone : DNS : Edit* sobre la zona
   `texcontrol.pe`. Copiar el token.
2. En el VPS:
   ```bash
   sudo apt update && sudo apt install -y certbot python3-certbot-dns-cloudflare
   sudo mkdir -p /root/.secrets
   echo "dns_cloudflare_api_token = <TU_TOKEN>" | sudo tee /root/.secrets/cloudflare.ini
   sudo chmod 600 /root/.secrets/cloudflare.ini
   sudo certbot certonly \
     --dns-cloudflare \
     --dns-cloudflare-credentials /root/.secrets/cloudflare.ini \
     -d texcontrol.pe -d '*.texcontrol.pe' \
     --agree-tos -m <tu-email> --non-interactive
   ```
   El certificado queda en `/etc/letsencrypt/live/texcontrol.pe/`.

### 6.4 Desplegar la config con HTTPS
**Recién con el certificado emitido**, promover `develop → main` (trae el
`nginx.conf` con TLS, el mapeo del 443 y `forward-headers-strategy`) y desplegar:
```bash
./scripts/deploy.sh
```
El `nginx.conf` ya monta `/etc/letsencrypt` y apunta a
`/etc/letsencrypt/live/texcontrol.pe/`. Probar en `https://texcontrol.pe`.
> ⚠️ No promover el `nginx.conf` con TLS a `main` **antes** de emitir el
> certificado, o nginx no arranca (no encuentra el `.pem`).

### 6.5 Renovación automática
certbot deja un timer que renueva solo. Para que nginx tome el cert renovado,
agregar un hook que lo reinicie tras la renovación:
```bash
sudo tee /etc/letsencrypt/renewal-hooks/deploy/restart-nginx.sh > /dev/null <<'EOF'
#!/bin/sh
cd /home/linuxuser/textil-inventario && docker compose -f docker-compose.yml -f docker-compose.prod.yml restart nginx
EOF
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/restart-nginx.sh
```

### 6.6 Multi-cliente (siguiente etapa)
Con el wildcard, sumar un cliente **no** requiere tocar el certificado. Falta
parametrizar los stacks (un `docker-compose` por cliente, con su BD/puerto/nombres
de contenedor) y darle a nginx un `server` block por subdominio que apunte al
`app` de ese cliente. Es la próxima iteración del deploy.

## 7. Rollback

Si un redeploy rompe algo:

```bash
git log --oneline -10          # ubicar el commit bueno anterior
git checkout <sha-del-commit-bueno>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```
