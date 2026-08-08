# Entorno de pruebas (staging) — `dev.texcontrol.pe`

La "entrada secreta": un entorno en el VPS que corre la rama **`develop`** con su
**propia base de datos**, aislada de produccion, para probar cambios desde casa
o trabajo **sin levantar nada en local** y sin copiar bases. Es OCULTO: no hay
enlace en ninguna pagina; se entra escribiendo la URL y pasando **Basic Auth**.

## Cómo funciona

- **URL**: `https://dev.texcontrol.pe` — cubierta por el **wildcard** de DNS
  (`*.texcontrol.pe`) y de HTTPS (cert wildcard), así que **no** hay que tocar
  DNS ni emitir certificado.
- El **proxy multicliente** (`texcontrol_proxy_nginx`) rutea `dev.texcontrol.pe` →
  contenedor `textil_app_dev`, protegido con **Basic Auth** (`multicliente/nginx/00-texcontrol.conf`).
- El stack dev (`docker-compose.dev.yml`) = `textil_app_dev` (build desde
  `develop`) + `textil_mysql_dev` (BD y volumen propios). El MySQL dev vive en
  una red privada `dev_interna` (nunca compartida) → **datos aislados**.
- Corre desde un **clon aparte** del repo en el VPS (`~/textil-inventario-dev`,
  en `develop`), para no chocar con el de produccion (en `main`).
- Costo: ~0.8–1 GB de RAM extra (igual que un cliente). Entra en el techo de 4 GB.

## Alta (una sola vez)

Todo en el VPS (`ssh texcontrol` **desde tu PC** — ese alias vive en el
`~/.ssh/config` de cada maquina, no adentro del servidor; si ya estas dentro,
saltealo). Requiere el **proxy multicliente corriendo**
(`texcontrol_proxy_nginx`), que es quien crea la red compartida `texcontrol_red`
a la que se une el stack dev.

1. **Basic Auth** — crear el archivo de credenciales (elegí usuario y clave).
   Va en el clon de **produccion**, que es de donde el proxy lo monta
   (`../nginx/dev.htpasswd` en `multicliente/docker-compose.proxy.yml`):
   ```bash
   cd ~/textil-inventario
   sudo apt-get install -y apache2-utils        # trae 'htpasswd' (si falta)
   htpasswd -Bc nginx/dev.htpasswd <usuario>    # pide la clave dos veces
   ```
   > El archivo NO se versiona (esta en `.gitignore`), asi que el `git pull` de
   > `actualizar-clientes.sh` no lo pisa. DEBE existir ANTES de levantar el
   > proxy (paso 2): si falta, Docker crea un DIRECTORIO vacio en su lugar y el
   > login de dev da error (produccion no se ve afectada).
   >
   > Ese directorio vacio NO se arregla solo con crear el archivo despues:
   > Docker ya monto el directorio. Hay que borrarlo, crear el htpasswd y
   > **recrear el contenedor del proxy** (un `nginx -s reload` no alcanza,
   > porque el mount se resuelve al arrancar el contenedor, no al recargar).

2. **Publicar el ruteo dev** — el bloque `dev.texcontrol.pe` y el mount del
   htpasswd viven en `multicliente/`, asi que hay que recargar el **proxy**:
   ```bash
   # verificar que la config es valida ANTES de recargar (si `-t` falla, no
   # recargues: nginx se queda con la config vieja, que al menos funciona):
   docker exec texcontrol_proxy_nginx nginx -t && \
   docker exec texcontrol_proxy_nginx nginx -s reload
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
  ssh texcontrol          # SOLO desde tu PC; si ya estas en el VPS, saltealo
                          # (adentro no existe el alias: "Could not resolve hostname")
  cd ~/textil-inventario && ./scripts/deploy-dev.sh
  ```
  > Corre igual desde cualquiera de los dos clones: `deploy-dev.sh` trabaja
  > siempre sobre `~/textil-inventario-dev` (variable `DEV_DIR`), no sobre el
  > directorio en el que estes parado. Es la excepcion — `actualizar-clientes.sh`
  > SI depende del clon desde donde se lo corra (ver CLAUDE.md).
- Probás en `dev.texcontrol.pe`. Cuando anda, promovés `develop → main` y
  `./scripts/actualizar-clientes.sh` (desde el clon de produccion, en `main`) para llevarlo a produccion.

## Notas

- **Aislado**: la BD dev (`textil_mysql_dev` / volumen `mysql_data_dev`) es
  independiente de produccion. Romper cosas en dev NO toca los datos reales.
- **Apagar dev** sin afectar produccion (nginx sigue arrancando gracias al
  `resolver` por variable; `dev.texcontrol.pe` solo dara 502):
  ```bash
  cd ~/textil-inventario-dev && docker compose --env-file .env.dev -f docker-compose.dev.yml down
  ```
- **Cambiar la clave de Basic Auth** (el usuario es `jlynch`), desde el clon de
  **produccion**, que es de donde el proxy monta el archivo:
  ```bash
  cd ~/textil-inventario
  htpasswd -B nginx/dev.htpasswd jlynch
  docker exec texcontrol_proxy_nginx nginx -s reload
  ```
  > El reload NO es opcional: sin el, nginx sigue sirviendo la clave vieja y
  > parece que el `htpasswd` no hubiera hecho nada.
  > Ojo con el `-B` a secas vs. `-Bc`: **`-c` CREA el archivo desde cero** y
  > borra lo que hubiera. Para cambiar una clave va sin `-c`.
