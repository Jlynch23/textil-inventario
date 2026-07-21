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

## 6. Agregar dominio + HTTPS más adelante

Cuando tengas un dominio apuntando a la IP **pública** del servidor (registro A):

0. Cambiá `BIND_IP` en el `.env` a `0.0.0.0` (o eliminá la línea, que es el default) y abrí el puerto 80/443 en el firewall de la instancia recién en este punto — antes de esto, el acceso debía seguir siendo solo por Tailscale.
1. Instalá certbot en el host (no en un contenedor, para simplificar): `sudo apt install certbot`.
2. Pará nginx un momento, generá el certificado en modo standalone, y volvé a levantarlo:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml stop nginx
   sudo certbot certonly --standalone -d tu-dominio.com
   docker compose -f docker-compose.yml -f docker-compose.prod.yml start nginx
   ```
3. Editá `nginx/nginx.conf`: cambiá `server_name _;` por `server_name tu-dominio.com;`, agregá un `server` block en el puerto 443 con `ssl_certificate`/`ssl_certificate_key` apuntando a `/etc/letsencrypt/live/tu-dominio.com/`, y montá `/etc/letsencrypt` como volumen de solo lectura en el servicio `nginx` de `docker-compose.prod.yml`.
4. Agregá una tarea de renovación automática (`sudo certbot renew` vía cron, con un `docker compose restart nginx` después).

## 7. Rollback

Si un redeploy rompe algo:

```bash
git log --oneline -10          # ubicar el commit bueno anterior
git checkout <sha-del-commit-bueno>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```
