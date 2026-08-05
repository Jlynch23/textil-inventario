# Ambiente DEMO — `demo.texcontrol.pe`

Un ambiente **para que los clientes potenciales prueben** el sistema por su
cuenta, con datos de ejemplo ya cargados y cuentas listas para repartir. A
diferencia de `dev.texcontrol.pe` (staging privado, oculto tras Basic Auth, para
el proveedor), la demo es **PÚBLICA**: se entra por la URL y se loguea con una de
las cuentas demo.

Es una instancia más del modelo multi-cliente (BD propia y aislada), así que
romper la demo **no toca a ningún cliente real**.

## Qué trae

- **Datos de ejemplo** (`scripts/demo-seed.sql`): una empresa (`TEXTIL DEMO
  S.A.C.`), ubicaciones (Praderas + almacén + tiendas), catálogo poblado (tipos
  de tela, títulos, composiciones, acabados, colores, 5 artículos) y algo de
  **stock** repartido, para que el prospecto vea Inventario y Reportes con datos.
- **10 cuentas demo**, una por persona (todas activas, clave = palabra del rol):

  | Rol | Usuario | Clave |
  |---|---|---|
  | ADMIN | `admindemo` | `admin` |
  | GERENTE | `gerente1demo` · `gerente2demo` · `gerente3demo` | `gerente` |
  | SUPERVISOR | `supervisor1demo` · `supervisor2demo` · `supervisor3demo` | `supervisor` |
  | ALMACENERO | `almacen1demo` · `almacen2demo` · `almacen3demo` | `supervisor` |

  > **"Almacenero" = rol SUPERVISOR** en el código (es quien entra a `/almacen`,
  > entrada/salida rápida móvil). No hay un rol "almacenero" aparte; por eso su
  > clave es `supervisor`. El rol VENDEDOR no se incluye (hoy sin permisos).

- **jlynch (SUPERADMIN)**: existe, pero con la clave **rotada a una privada** (la
  imprime el alta, una sola vez). **No se reparte** a los prospectos — es tu
  cuenta de soporte. Sin esta rotación, un demo público dejaría el superadmin con
  la clave de arranque conocida.

## Alta (una sola vez)

En el VPS (`ssh texcontrol`), con el **proxy multi-cliente corriendo** y el cert
wildcard emitido (igual que cualquier cliente; ver `DEPLOY.md`):

```bash
cd ~/textil-inventario        # clon en main (o la rama que quieras demostrar)
ANTHROPIC_API_KEY=... ./scripts/nuevo-demo.sh
#   -> queda en https://demo.texcontrol.pe
```

`nuevo-demo.sh` se apoya en los scripts que ya existen: crea el stack con
`nuevo-cliente.sh --prueba demo`, aplica el seed y llama a `endurecer-cliente.sh`
(rota jlynch, borra las cuentas `*prueba` inactivas; las 10 cuentas demo son
`es_prueba=FALSE`, así que sobreviven). Al final imprime la lista de cuentas para
repartir y la clave privada de jlynch. **Guarda esa clave**: no se vuelve a mostrar.

> `ANTHROPIC_API_KEY` es opcional: sin ella el OCR de guías no anda en la demo,
> pero todo lo demás sí. Si quieres mostrar el OCR, expórtala antes de correr el
> script (se copia al `.env` del demo, igual que a cualquier cliente).

## Resetear (manual)

Cuando la demo quede sucia con lo que cargaron los prospectos, vuélvela a foja
cero:

```bash
cd ~/textil-inventario
./scripts/resetear-demo.sh          # pide confirmar (escribir "demo")
./scripts/resetear-demo.sh --si     # sin confirmación (scripting)
```

Borra el volumen de la BD del demo (foja cero garantizada, sin enumerar tablas),
la levanta vacía (Flyway re-migra), y re-siembra + re-endurece. **La clave privada
de jlynch se rota de nuevo** (sale una distinta): guárdala del output.

> No hay reset automático (cron) a propósito: se resetea a mano cuando decidas.
> Los PDFs subidos en `clientes/demo/documentos` no se borran en el reset (son
> archivos, no afectan la BD); bórralos a mano si quieres limpiarlos también.

## Notas

- **Aislado**: `db_demo` y su volumen (`texcontrol_demo_db_data`) son propios. La
  demo no comparte datos con `textillaura`, `textilcamargo` ni con `dev`.
- **RAM**: cuesta ~0.8–1 GB como cualquier cliente. Ojo al techo (~3 clientes +
  dev en 4 GB): si la RAM aprieta, apaga la demo cuando no la uses.
- **Apagar la demo** sin borrarla (nginx sigue; `demo.texcontrol.pe` dará 502):
  ```bash
  docker compose -p texcontrol_demo --env-file clientes/demo/.env \
      -f multicliente/docker-compose.cliente.yml down
  ```
  Para volver a encenderla (sin resetear): el mismo comando con `up -d`, o
  `./scripts/nuevo-demo.sh` (re-aplica el seed idempotente y re-endurece).
- **Eliminar la demo** por completo: `./scripts/eliminar-cliente.sh demo`.
- **Cambiar los datos de ejemplo**: edita `scripts/demo-seed.sql` (es idempotente,
  `INSERT IGNORE` + subconsultas por nombre) y corre `./scripts/resetear-demo.sh`
  para que la demo tome el seed nuevo desde cero.
