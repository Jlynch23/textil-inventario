# Despliegue en el VPS

Guía para poner TexControl a correr en el servidor. Todo corre en Docker: MySQL, la app y Nginx como reverse proxy.

> **ESTADO ACTUAL (jul-2026) — leer primero.** La app YA está **en vivo** en
> `texcontrol.pe` con dominio + HTTPS wildcard. Cambios respecto a lo que dicen
> las secciones históricas de abajo:
> - **Tailscale fue REMOVIDO.** El acceso admin es por **SSH con clave pública**
>   directo a la IP pública (`ssh texcontrol` → `linuxuser@64.176.3.149`), con
>   login por contraseña **deshabilitado** y `fail2ban` activo. La sección 8
>   ("SSH solo por Tailscale") quedó **reescrita** con el estado real.
> - **`BIND_IP=0.0.0.0`** (la web es pública por dominio). Ya NO se usa la IP de
>   Tailscale.
> - **Docker** tenía un override de systemd que lo ataba a `tailscaled`; al sacar
>   Tailscale eso rompió el arranque de Docker. Ya se quitó (ver
>   `/etc/systemd/system/docker.service.d/override.conf`).
> - Las secciones **1 y 2** describen el bootstrap histórico "por IP"; siguen
>   siendo útiles como referencia de instalación desde cero.

## 1. Prerrequisitos en el VPS

```bash
# Docker + plugin de compose (Ubuntu/Debian)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# cerrar sesión y volver a entrar para que el grupo tome efecto
```

**Exposición de red.** Hoy producción es **pública por dominio** (`texcontrol.pe`) con HTTPS, así que en el firewall de la instancia van abiertos **80 y 443** (ver sección 6) y en el `.env` va `BIND_IP=0.0.0.0` — nginx escucha en todas las interfaces y termina el TLS. El puerto lo controla `BIND_IP` en `multicliente/docker-compose.proxy.yml`: si algún día quisieras exponer la app solo por una interfaz o VPN interna, fijá ahí esa IP en vez de `0.0.0.0`. Si `BIND_IP` no se define, cae por defecto en `0.0.0.0`.

Los puertos 3307 (MySQL) y 8081 (Adminer) **no hace falta abrirlos** — `docker-compose.yml` ya los deja bindeados solo a `127.0.0.1`, así que ni siquiera están expuestos fuera del servidor. El puerto 22 (SSH) sí necesita estar abierto para poder conectarte; restringirlo a tu IP/rango de confianza (en vez de "Anywhere") es una mejora recomendada pero no forma parte de este documento.

## 2. Primera vez

> **El despliegue es MULTICLIENTE**: un stack aislado por empresa (`app_<slug>` + `db_<slug>`,
> con su propia base y su propia carpeta de documentos) detrás de un proxy nginx compartido que
> rutea por subdominio. El modelo viejo de un solo cliente (`docker-compose.prod.yml` +
> `scripts/deploy.sh`) fue **decomisionado** y sus archivos ya no están en el repo.
> El detalle vive en `multicliente/README.md`.

```bash
git clone https://github.com/Jlynch23/textil-inventario.git
cd textil-inventario
git checkout main          # producción corre main, siempre
```

**a) La clave del OCR, una sola vez para todos los clientes.** Es la del proveedor y se guarda en
el VPS (`~/.texcontrol/proveedor.env`, permisos 600); los scripts la toman solos:

```bash
./scripts/configurar-proveedor.sh
```

**b) Levantar el proxy compartido** (nginx + red `texcontrol_red`, sirve a todos los clientes):

```bash
docker compose -p texcontrol_proxy -f multicliente/docker-compose.proxy.yml up -d
```

**c) Dar de alta el primer cliente.** El script hace todo: crea la base aislada, genera el `.env`
con claves únicas, levanta el stack, escribe el bloque de nginx, recarga el proxy y **endurece**
(rota la clave de `jlynch` y la imprime UNA vez, borra las cuentas de prueba):

```bash
./scripts/nuevo-cliente.sh laura "Textil Laura"
```

Queda servido en `https://laura.texcontrol.pe` — el wildcard de DNS y el certificado ya lo cubren,
no hay que tocar nada más (sección 6). **Anotá la clave de `jlynch` que imprime: no se vuelve a
mostrar.**

```bash
./scripts/listar-clientes.sh     # qué clientes hay y cómo están
```

## 3. Actualizar (redeploy)

Todos los clientes comparten la misma imagen, así que se reconstruye una vez y se reinician todos:

```bash
cd ~/textil-inventario        # el clon de PRODUCCIÓN, en main
./scripts/actualizar-clientes.sh              # git pull + build + reiniciar TODOS
./scripts/actualizar-clientes.sh laura        # solo ese cliente (probar de a uno)
./scripts/actualizar-clientes.sh --no-pull    # usar el código ya presente
```

**Los datos no se tocan**: cada MySQL y su volumen quedan intactos. Si el cambio trae una migración
Flyway nueva, cada app la aplica sola en SU base al arrancar.

> ⚠️ El script reconstruye la imagen **desde el clon donde se lo corra**. En el VPS hay dos
> (`~/textil-inventario` en `main` y `~/textil-inventario-dev` en `develop`); correlo siempre desde
> el de producción o le meterás código de `develop` a los clientes. Avisa si la rama no es `main`,
> pero es un aviso, no un freno.

## 4. Logs y diagnóstico

```bash
docker logs -f app_<slug>              # la app de un cliente
docker logs -f db_<slug>               # su MySQL
docker logs -f texcontrol_proxy_nginx  # el proxy compartido
docker ps                              # todo lo que corre
./scripts/estado-vps.sh                # CPU, RAM, disco y consumo por contenedor
```

Al arrancar, buscá `Started InventarioApplication` en el log de la app. Si en cambio aparece un
`SchemaManagementException`, es que el esquema no calza con las entidades (`ddl-auto: validate`).

## 5. Backups

```bash
./scripts/backup-cliente.sh laura       # un cliente
./scripts/backup-cliente.sh --todos     # todos (es lo que corre el cron)
./scripts/instalar-cron-backups.sh      # deja el cron diario a las 2am
```

Deja en `~/backups/<slug>/` dos archivos por corrida: el dump de la base (`.sql.gz`) y los
documentos (`.tar.gz`), con retención de 30 días.

**Un backup no sirve hasta que se probó restaurarlo**:

```bash
./scripts/verificar-backup.sh laura     # restaura en una base desechable y valida
./scripts/verificar-backup.sh --todos
```

Restaura sobre una base aislada dentro del mismo MySQL del cliente (nunca toca la real) y comprueba
que estén las tablas críticas, el historial de Flyway, los usuarios y que las tildes/eñes
sobrevivan.

**Restaurar de verdad** (sobreescribe la base del cliente; guarda antes un dump del estado actual y
para la app durante la operación):

```bash
./scripts/restaurar-cliente.sh --listar laura   # qué backups hay
./scripts/restaurar-cliente.sh laura            # el más reciente
./scripts/restaurar-cliente.sh laura ~/backups/laura/laura_db_2026-08-06_020000.sql.gz
```

> ⚠️ `backup-cliente.sh --todos` sale con código **0** e imprime "No hay clientes que respaldar"
> cuando `clientes/` está vacío. Sin clientes es correcto, pero en el log del cron un `clientes/`
> vaciado por error se ve **igual** que un día normal. Al dar de alta un cliente, confirmá que
> aparezca su línea en `~/backups/backup.log`.

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
**Recién con el certificado emitido**, promover `develop → main` y recrear el proxy, que es
quien termina el TLS:
```bash
docker compose -p texcontrol_proxy -f multicliente/docker-compose.proxy.yml up -d
```
`multicliente/nginx/00-texcontrol.conf` ya monta `/etc/letsencrypt` y apunta a
`/etc/letsencrypt/live/texcontrol.pe/`. Probar en `https://texcontrol.pe`.
> ⚠️ No promover la config con TLS a `main` **antes** de emitir el certificado, o nginx no
> arranca (no encuentra el `.pem`).

### 6.5 Renovación automática
certbot deja un timer que renueva solo. Para que nginx tome el cert renovado,
agregar un hook que lo reinicie tras la renovación:
```bash
sudo tee /etc/letsencrypt/renewal-hooks/deploy/restart-nginx.sh > /dev/null <<'EOF'
#!/bin/sh
docker restart texcontrol_proxy_nginx
EOF
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/restart-nginx.sh
```

### 6.6 Multi-cliente (BD aislada por empresa)
Modelo: **cada empresa corre en su propio stack aislado** (su contenedor de app
+ su propio MySQL + su propia base de datos + su carpeta de documentos + sus
credenciales). Un único nginx (el "portero") rutea cada `<empresa>.texcontrol.pe`
al contenedor `app_<empresa>`. Con el wildcard TLS, sumar un cliente **no**
requiere tocar el certificado ni el DNS. Todo vive en `multicliente/` + los
scripts `scripts/*-cliente.sh`.

**Aislamiento**: el MySQL de cada cliente solo está en la red privada de ese
cliente (`interna`); nunca en la red compartida (`texcontrol_red`), que solo une
nginx con las apps. Así ningún cliente puede alcanzar la BD de otro.

**Dimensionamiento (VPS de 4 GB)**: cada cliente consume ~0.8–1 GB de RAM
(app `-Xmx384m` + MySQL con `innodb-buffer-pool-size=128M`, ya afinados en el
compose). Techo práctico **~3 clientes** en 4 GB; al llegar al 3.º/4.º cliente
pagando, subir la RAM del VPS (o migrar a MySQL compartido). CPU y disco sobran.

**Levantar el proxy (una sola vez):**
```bash
docker network create texcontrol_red
docker compose -p texcontrol_proxy -f multicliente/docker-compose.proxy.yml up -d
```

**Dar de alta un cliente (un comando):**
```bash
# El OCR usa la API key del proveedor; se pasa por el entorno y queda en el
# .env del cliente. Sin ella, el cliente arranca igual pero sin OCR.
ANTHROPIC_API_KEY=sk-ant-... ./scripts/nuevo-cliente.sh laura "Laura & Clemente"
#   -> crea BD aislada, levanta app_laura + db_laura, genera el bloque nginx,
#      recarga el proxy y ENDURECE la copia. Queda en https://laura.texcontrol.pe
```
El script genera credenciales propias del cliente (`openssl rand`), las guarda en
`clientes/<slug>/.env` (permisos 600, en `.gitignore`), y deja la BD lista con las
cuentas semilla (Flyway migra al arrancar). Por defecto **endurece** la copia (ver
abajo) e imprime la clave única de `jlynch` **una sola vez** — guardala en tu gestor
de contraseñas. Con `--prueba` se omite el endurecimiento (copia de testeo interno,
`jlynch`/`superadmin` + cuentas de prueba).

**Endurecer para producción** (rotar `jlynch` a una clave única de esta copia +
eliminar las cuentas de prueba). `nuevo-cliente.sh` ya lo hace por defecto; este
script sirve para endurecer una copia de `--prueba`, o re-rotar `jlynch`:
```bash
./scripts/endurecer-cliente.sh laura
```
Sin esto, `jlynch`/`superadmin` sería la **misma llave maestra en todas las copias**
(el hash de arranque viene fijo de la migración V33). El hash bcrypt se genera con un
contenedor efímero `httpd:alpine` (compatible con Spring Security), sin instalar nada.

**Cron de backups automáticos** (idempotente; instala/actualiza la entrada):
```bash
./scripts/instalar-cron-backups.sh        # diario a las 2am (o pasar la hora: ... 4)
```

**Backups por cliente (gratis, reemplazan al backup pago de Vultr):**
```bash
./scripts/backup-cliente.sh laura      # un cliente
./scripts/backup-cliente.sh --todos    # todos (para el cron diario)
```
A diferencia del backup de máquina de Vultr, esto permite **restaurar a un solo
cliente** sin tocar a los demás. Cron diario sugerido:
```
0 2 * * * cd /ruta/textil-inventario && ./scripts/backup-cliente.sh --todos >> ~/backups/backup.log 2>&1
```

**Dar de baja un cliente:**
```bash
./scripts/backup-cliente.sh laura      # respaldar ANTES
./scripts/eliminar-cliente.sh laura    # apaga stack, borra BD/documentos y su bloque nginx
```

**Actualizar el código de todos los clientes** (comparten la imagen
`texcontrol-app:latest`):
```bash
git pull                                       # traer la version nueva
docker build -t texcontrol-app:latest .        # reconstruir la imagen una vez
# reiniciar la app de cada cliente para tomar la imagen nueva:
for e in clientes/*/.env; do s=$(basename $(dirname $e)); \
  docker compose -p texcontrol_$s --env-file $e -f multicliente/docker-compose.cliente.yml up -d; done
```

**Ver los clientes dados de alta** (estado + consumo de RAM, para vigilar el techo):
```bash
./scripts/listar-clientes.sh
```

> **La migración desde el modelo viejo YA SE HIZO** (ago-2026): `textillaura` y `textilcamargo`
> se pasaron al esquema multicliente y el stack de un solo cliente quedó decomisionado. Por eso
> `migrar-cliente.sh` —que hacía ese pase una única vez— se eliminó junto con
> `docker-compose.prod.yml`, `nginx/nginx.conf`, `scripts/deploy.sh`, `backup-db.sh` y
> `restore-db.sh`: ya no queda ninguna instalación vieja que migrar y mantenerlos solo confundía.
> Si alguna vez hicieran falta, siguen en el historial de git.

## 7. Rollback

Si un redeploy rompe algo:

```bash
git log --oneline -10                          # ubicar el commit bueno anterior
git checkout <sha-del-commit-bueno>
./scripts/actualizar-clientes.sh --no-pull     # reconstruye y reinicia con ESE código
```
`--no-pull` es lo que hace la diferencia: sin él, el script vuelve a traer `main` y deshace el
checkout. Los datos no se tocan.

> **Ojo con las migraciones**: volver a un commit anterior NO revierte las migraciones Flyway ya
> aplicadas. Si el commit malo traía una migración, el esquema queda adelantado respecto del código
> y la app puede no arrancar (`ddl-auto: validate`). En ese caso hay que restaurar el backup:
> `./scripts/restaurar-cliente.sh <slug>`.

## 8. Acceso admin y hardening del SSH (estado real, jul-2026)

**Tailscale fue removido.** El acceso administrativo es por **SSH con clave
pública**, directo a la IP pública del VPS. Estado actual:

- **Firewall (`ufw`)**: `80/tcp`, `443/tcp` y `22/tcp` desde `Anywhere`. (Ya NO
  hay reglas atadas a `tailscale0`.)
- **SSH**: login por **contraseña deshabilitado** (`PasswordAuthentication no`
  en `/etc/ssh/sshd_config.d/00-hardening.conf`) — **solo clave**.
- **fail2ban** activo (banea IPs que intentan fuerza bruta).
- **Alias de conexión** (en el `~/.ssh/config` de tu PC):
  ```
  Host texcontrol
      HostName 64.176.3.149
      User linuxuser
      IdentityFile ~/.ssh/id_ed25519
  ```
  Se entra con `ssh texcontrol`.

**Salvavidas final:** la **consola web de Vultr** ("View Console") entra sin SSH
aunque te bloquees; desde ahí se corrige `ufw`, `sshd` o se resetea una clave con
`sudo passwd linuxuser`.

**Habilitar una PC nueva (casa/trabajo)** — como el password está deshabilitado,
no sirve `ssh-copy-id` a ciegas; agregá la clave pública de la PC nueva desde una
sesión ya abierta:
```bash
# en la PC nueva: generar clave y mostrar la publica
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -N ""
cat ~/.ssh/id_ed25519.pub          # copiar esta linea
# en una sesion ya logueada al VPS (ssh texcontrol desde la PC vieja): pegarla
echo "ssh-ed25519 AAAA... comentario" >> ~/.ssh/authorized_keys
```

> ⚠️ **Regla de oro:** nunca cerrar/cambiar el acceso SSH sin confirmar en OTRA
> terminal que el método nuevo entra. Y ojo con Docker: tenía un override que lo
> ataba a `tailscaled` — si Docker no arranca tras tocar la red, revisar
> `/etc/systemd/system/docker.service.d/override.conf`.

> Pendiente opcional de hardening extra: restringir el `22/tcp` a tu IP/rango en
> vez de `Anywhere`, y aplicar actualizaciones del SO (`sudo apt update && sudo apt upgrade`).
