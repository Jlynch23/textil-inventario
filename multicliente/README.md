# Modelo multi-cliente (BD aislada por empresa)

Cada empresa corre en su **propio stack aislado** en el mismo VPS: su contenedor
de app + su propio MySQL + su base de datos + su carpeta de documentos + sus
credenciales. Un único nginx rutea `<empresa>.texcontrol.pe` a `app_<empresa>`.

Esta carpeta **convive** con el modelo actual de un solo cliente
(`docker-compose.prod.yml` en la raíz) sin pisarlo. La producción actual sigue
igual hasta que se migre de forma deliberada.

## Arquitectura

```
                 nginx (textil_nginx)  ── red compartida: texcontrol_red ──┐
                 rutea por subdominio                                       │
   laura.texcontrol.pe ─────────► app_laura ─┐   juan.texcontrol.pe ──► app_juan ─┐
                                             │                                    │
                                    red privada "interna"              red privada "interna"
                                             │                                    │
                                          db_laura                             db_juan
```

- `texcontrol_red` (compartida): solo une **nginx ↔ apps**. Los MySQL NO están aquí.
- `interna` (privada por cliente): une **app ↔ su propio MySQL**. Aísla los datos.

## Piezas

| Archivo | Qué es |
|---|---|
| `docker-compose.proxy.yml` | El nginx compartido (el "portero"). Se levanta **una vez**. |
| `docker-compose.cliente.yml` | Plantilla del stack de **un** cliente (app + su MySQL). La usa el script. |
| `nginx/00-texcontrol.conf` | Config base de nginx: marketing, lanzador, fallback + `include` de clientes. |
| `nginx/clientes/cliente-<slug>.conf` | Bloque `server` de cada cliente (generado, en `.gitignore`). |
| `../scripts/lib-cliente.sh` | Librería común (constantes + funciones). La cargan los demás con `source`; no se ejecuta sola. |
| `../scripts/nuevo-cliente.sh` | Da de alta un cliente con un comando (endurece por defecto; `--prueba` para testeo interno). |
| `../scripts/migrar-cliente.sh` | Migra un despliegue actual (un solo cliente) a este modelo, sin perder datos. |
| `../scripts/endurecer-cliente.sh` | Rota la clave de `jlynch` (única por copia) y elimina las cuentas de prueba. |
| `../scripts/eliminar-cliente.sh` | Da de baja un cliente. |
| `../scripts/backup-cliente.sh` | Backup por cliente (`<slug>` o `--todos`). |
| `../scripts/instalar-cron-backups.sh` | Instala el cron diario de backups de todos los clientes. |
| `../scripts/listar-clientes.sh` | Lista clientes, su estado y consumo de RAM. |
| `../clientes/<slug>/` | Estado por cliente: `.env` (credenciales) + `documentos/`. En `.gitignore`. |

## Uso rápido

```bash
# 1) Una sola vez: red compartida + proxy
docker network create texcontrol_red
docker compose -p texcontrol_proxy -f multicliente/docker-compose.proxy.yml up -d

# 2) Dar de alta un cliente
ANTHROPIC_API_KEY=sk-ant-... ./scripts/nuevo-cliente.sh laura "Laura & Clemente"

# 3) Backup
./scripts/backup-cliente.sh --todos
```

El detalle completo (dimensionamiento en 4 GB, actualización de todos los
clientes, migración de la Laura actual, cron de backups) está en
`DEPLOY.md`, sección **6.6**.

## Probar en local (sin VPS, sin certificados)

Para una prueba de humo del stack de un cliente sin nginx/HTTPS: se puede
levantar solo el `docker-compose.cliente.yml` con un `.env` de prueba y publicar
el puerto de la app a mano, o correr `nuevo-cliente.sh` (que igual arma el bloque
de nginx pero avisa si el proxy no está corriendo). En local no hay wildcard TLS,
así que el ruteo por subdominio real solo se ve en el VPS.
