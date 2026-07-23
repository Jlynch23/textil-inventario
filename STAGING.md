# Entorno de pruebas (staging) — `dev.texcontrol.pe`

La "entrada secreta": un entorno en el VPS que corre la rama **`develop`** con su
**propia base de datos**, aislada de produccion, para probar cambios desde casa
o trabajo **sin levantar nada en local** y sin copiar bases. Es OCULTO: no hay
enlace en ninguna pagina; se entra escribiendo la URL y pasando **Basic Auth**.

## Cómo funciona

- **URL**: `https://dev.texcontrol.pe` — cubierta por el **wildcard** de DNS
  (`*.texcontrol.pe`) y de HTTPS (cert wildcard), así que **no** hay que tocar
  DNS ni emitir certificado.
- El **nginx de produccion** (`textil_nginx`) rutea `dev.texcontrol.pe` →
  contenedor `textil_app_dev`, protegido con **Basic Auth** (`nginx/nginx.conf`).
- El stack dev (`docker-compose.dev.yml`) = `textil_app_dev` (build desde
  `develop`) + `textil_mysql_dev` (BD y volumen propios). El MySQL dev vive en
  una red privada `dev_interna` (nunca compartida) → **datos aislados**.
- Corre desde un **clon aparte** del repo en el VPS (`~/textil-inventario-dev`,
  en `develop`), para no chocar con el de produccion (en `main`).
- Costo: ~0.8–1 GB de RAM extra (igual que un cliente). Entra en el techo de 4 GB.

## Alta (una sola vez)

Todo en el VPS (`ssh texcontrol`). Requiere el stack de **produccion corriendo**
(crea la red `textil-inventario_default` que el dev usa).

1. **Basic Auth** — crear el archivo de credenciales (elegí usuario y clave):
   ```bash
   cd ~/textil-inventario
   sudo apt-get install -y apache2-utils        # trae 'htpasswd' (si falta)
   htpasswd -Bc nginx/dev.htpasswd <usuario>    # pide la clave dos veces
   ```
   > El archivo es untracked: sobrevive el `git reset --hard` de `deploy.sh` y
   > NO se versiona. DEBE existir ANTES de redeployar produccion (paso 2), o
   > Docker crea un directorio vacio en su lugar.

2. **Publicar el ruteo dev en produccion** — promover `develop → main` y
   redeployar (trae el bloque `dev` de `nginx.conf` y el mount del htpasswd):
   ```bash
   ./scripts/deploy.sh
   # verificar que nginx tomo la config nueva:
   docker exec textil_nginx nginx -t && docker exec textil_nginx nginx -s reload
   ```

3. **Levantar el stack dev** — clon aparte + credenciales + deploy:
   ```bash
   ./scripts/deploy-dev.sh          # clona ~/textil-inventario-dev la 1a vez
   # -> se detiene pidiendo el .env.dev; crearlo:
   cd ~/textil-inventario-dev
   cp .env.dev.example .env.dev
   nano .env.dev                    # completar con claves hex (openssl rand -hex 24/32)
   cd ~/textil-inventario
   ./scripts/deploy-dev.sh          # ahora sí construye y levanta el stack dev
   ```

4. **Probar**: entrar a `https://dev.texcontrol.pe` → Basic Auth → login de la
   app (cuentas semilla de la BD dev: `jlynch`/`superadmin`, etc.).

## Uso diario

- **Actualizar dev** con lo último de `develop` (desde casa o trabajo):
  ```bash
  ssh texcontrol
  cd ~/textil-inventario && ./scripts/deploy-dev.sh
  ```
- Probás en `dev.texcontrol.pe`. Cuando anda, promovés `develop → main` y
  `./scripts/deploy.sh` para llevarlo a produccion.

## Notas

- **Aislado**: la BD dev (`textil_mysql_dev` / volumen `mysql_data_dev`) es
  independiente de produccion. Romper cosas en dev NO toca los datos reales.
- **Apagar dev** sin afectar produccion (nginx sigue arrancando gracias al
  `resolver` por variable; `dev.texcontrol.pe` solo dara 502):
  ```bash
  cd ~/textil-inventario-dev && docker compose --env-file .env.dev -f docker-compose.dev.yml down
  ```
- **Cambiar la clave de Basic Auth**: `htpasswd -B nginx/dev.htpasswd <usuario>`
  y `docker exec textil_nginx nginx -s reload`.
