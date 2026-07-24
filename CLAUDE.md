# CLAUDE.md

Guía para trabajar en este repositorio. **TexControl** (artifactId `inventario`) es una
plataforma de gestión de inventario para una empresa textil (telas RIB de algodón peinado),
que cubre el flujo completo: recepción de tela teñida → almacenamiento → transferencias entre
ubicaciones/tiendas, con OCR por IA, trazabilidad por kardex y reportes.

## Stack

- **Java 21** · **Spring Boot 3.3.5** (MVC server-side, no SPA)
- **Thymeleaf** + Bootstrap 5 / Bootstrap Icons para las vistas
- **Spring Security** (form login, sesión)
- **Spring Data JPA** + **MySQL 8**
- **Flyway** para migraciones (`ddl-auto: validate` — Hibernate NO crea el esquema)
- **Apache POI** para exportar Excel
- **API de Anthropic (Claude)** para OCR de guías/facturas (`AnthropicOcrService`)
- **Lombok** · Spring Actuator (solo `health,info`)

## Comandos

```bash
# Base de datos local (MySQL en :3307, Adminer en :8081)
docker compose up -d

# Ejecutar la app (http://localhost:8080). Flyway migra al arrancar.
mvn spring-boot:run

# Compilar / tests (lo mismo que corre CI en push/PR a main)
mvn -B clean compile
mvn -B test
```

### Variables de entorno requeridas
`DB_PASSWORD` es obligatoria (sin default). Para el OCR se necesita `ANTHROPIC_API_KEY`
(si falta, el OCR no funciona pero la app arranca). Ver `.env.example` / README. Otras:
`DB_USERNAME` (def. `textil_user`), `DOCUMENTOS_PATH` (def. `./documentos`),
`MAX_UPLOAD_SIZE` (def. `25MB`), `NOMBRE_EMPRESA`, y en prod `MYSQL_ROOT_PASSWORD` / `BIND_IP`.

### Login por defecto (multi-tenant por instancia)
Cada copia se alquila como **instancia propia** (BD + despliegue por cliente; el nombre del
negocio se personaliza con `NOMBRE_EMPRESA`). Cuentas semilla (estado tras V35):
- **`jlynch`** (Joseph Lynch, rol **SUPERADMIN**): la **única cuenta usable** de arranque, es el
  **proveedor** (soporte, oculta para el cliente). Contraseña de arranque `superadmin`, a rotar.
- **Cuentas de prueba** (una por rol): `adminprueba`, `gerenteprueba`, `supervisorprueba`,
  `vendedorprueba` (contraseña = nombre del rol). Van **`es_prueba = true`** e **inactivas**:
  ocultas para el ADMIN y sin login posible hasta que el SUPERADMIN las active para probar.
- **No hay cuenta del dueño pre-armada**: al entregar una copia, el SUPERADMIN crea la cuenta
  ADMIN del dueño (su nombre → username autogenerado).

**Username autogenerado** (`GeneradorUsername`): inicial del primer nombre + primer apellido, sin
tildes, único ("Oscar Clemente" → `oclemente`). Alta y **edición** de usuarios (nombre → regenera
username, + rol + contraseña) en `UsuarioController`. Cada quien rota su clave en **Mi cuenta**
(`/usuarios/mi-cuenta`).

## Arquitectura

Organización **por dominio de negocio**: un paquete por módulo bajo
`src/main/java/com/textil/inventario/`, cada uno con el patrón
`Controller → Service → Repository → Entity`. Las plantillas Thymeleaf viven en
`src/main/resources/templates/<modulo>/`.

Módulos (paquete → ruta base del controlador):

| Paquete | Ruta | Qué hace |
|---|---|---|
| `catalogo` | `/catalogo` | Tipos de tela, títulos, colores (código FAST DYE), composiciones, acabados, artículos, ubicaciones, empresas. Borrado protegido ante relaciones. |
| `recepciones` | `/recepciones`, `/programas`, `/documentos`, `/almacen` | Recepción en 4 pasos (documento → conteo físico → validación → confirmación) con OCR. Programas de teñido, entradas/salidas rápidas móviles del almacenero, cola de revisión. |
| `transferencias` | `/transferencias` | Traslados entre ubicaciones con doble confirmación (salida → llegada) y reparto de una línea a varios destinos. |
| `inventario` | `/inventario` | Stock actual por ubicación y kardex (historial de movimientos). |
| `reportes` | `/reportes` | Stock, kardex, recepciones, transferencias, stock bajo — exportables a Excel (POI). |
| `archivohistorico` | `/archivo-historico` | Importación masiva de guías/facturas antiguas vía ZIP, leídas por IA en 2º plano; enriquece catálogo sin afectar stock. |
| `seguridad` | `/usuarios` | Usuarios y roles, integración con Spring Security. |
| `auditoria` | `/log` | Registro de eventos (`AuditLogService`, `LogEvento`). |
| `dashboard` | `/`, `/dashboard` | Indicadores en tiempo real. |
| `config` | — | `SecurityConfig`, `AsyncConfig` (OCR async), `GlobalExceptionHandler`, `GlobalModelAttributes`. |
| `common` | — | `BaseEntity` (id + timestamps, base de las entidades). |

## Roles y seguridad (`config/SecurityConfig.java`)

Jerarquía (mayor → menor): **SUPERADMIN** (proveedor) > **ADMIN** (dueño-cliente) >
GERENTE / SUPERVISOR / VENDEDOR. El `anyRequest()` por defecto es **ADMIN + SUPERADMIN**.

- **SUPERADMIN**: el **proveedor** (tú). Cuenta oculta de soporte que entra a cada copia.
  Acceso total. **Reservado exclusivamente**: Reporte de Errores del Sistema
  (`/reportes/errores`) y la gestión de cuentas SUPERADMIN (invisibles/intocables para el ADMIN,
  ocultadas dentro de `UsuarioController`, no por URL). En el Log de Auditoría, sus acciones se
  ocultan al ADMIN (filtro en `LogEventoRepository.buscarConFiltros`).
- **ADMIN**: el **dueño del negocio cliente**. Controla todo lo operativo (recepciones,
  transferencias, catálogo, revisión de almacén, reportes salvo errores, archivo histórico,
  usuarios de su equipo). Puede asignar cualquier rol **menos SUPERADMIN**.
- **SUPERVISOR**: personal de almacén. Accede a `/almacen/**` (entrada/salida rápida móvil);
  sus movimientos entran a la cola de revisión antes de afectar el stock. Al loguear se
  redirige a `/almacen`.
- **GERENTE**: **solo lectura** (GET) de áreas operativas. Las páginas GET que son punto de
  entrada a una escritura (p.ej. `/recepciones/nueva`, `/programas/*/editar`, `/catalogo/empresas`)
  se bloquean para GERENTE (quedan ADMIN+SUPERADMIN). NUNCA accede a `/log/**` ni `/reportes/**`.
- **VENDEDOR**: reservado para el futuro módulo de Ventas; hoy **sin permisos** en SecurityConfig.

**Autoservicio**: cualquier usuario autenticado cambia su propia contraseña en
`/usuarios/mi-cuenta` (rutas permitidas antes de `/usuarios/**` en SecurityConfig).

**Defensa en profundidad**: la autorización de las páginas de escritura no depende solo del
orden de las reglas de URL — los métodos de entrada a escritura (controladores) y los borrados
(servicios) llevan `@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")`. Igual, al tocar rutas
revisar el orden en `SecurityConfig`: se evalúan en secuencia y las excepciones GER­ENTE/reservadas
deben ir antes de la regla amplia.

## OCR con IA (`recepciones/AnthropicOcrService.java`)

Llama directamente a la API de Anthropic vía `RestClient` (connect 30s / read 90s — hay comentario
SEC-03 explicando por qué el timeout importa: corre en `@Async`, sin él un proveedor caído agota el
pool de hilos). El `SYSTEM_PROMPT` contiene reglas de normalización muy específicas del dominio
(tipoTela, título, composición MELANGE/MLG, acabado) para las guías de **FAST DYE**. Si se ajusta el
parsing de guías, ese prompt es la fuente de verdad.

## Base de datos / migraciones

- Todo cambio de esquema es una migración Flyway nueva en
  `src/main/resources/db/migration/V<n>__descripcion.sql`. **Nunca** editar una migración ya
  aplicada; sumar una nueva con el siguiente número (actualmente van hasta **V36**).
- `ddl-auto: validate`: si una entidad no calza con el esquema migrado, la app no arranca.
- `baseline-on-migrate: true`.

## Tests

`src/test/java/...`, JUnit 5 + Spring Boot Test. Concentrados en la lógica de servicio:
`RecepcionServiceTest`, `ArticuloMatchingServiceTest`, `TransferenciaServiceTest`,
`CatalogoServiceTest`, `ArchivoHistoricoServiceTest`. CI (`.github/workflows/ci.yml`) corre
`mvn -B clean compile` + `mvn -B test` en cada push/PR a `main`.

## Flujo de trabajo (ramas)

**Solo existen DOS ramas y NO se crean otras** (nada de `feature/*`, `claude/*`, ni ramas efímeras
por tarea — es una molestia explícita del dueño):
- **`develop`**: rama de **trabajo y pruebas**. Todo lo nuevo pasa primero por acá.
- **`main`**: **producción**. El VPS despliega de esta rama (`scripts/deploy.sh` hace
  `git reset --hard origin/main`). **Solo** se mergea `develop → main` cuando está probado y estable.

Regla: nunca pushear features a medio hacer a `main`; probar en `develop`, y recién cuando anda,
promover a `main` (dispara el redeploy). CI (`.github/workflows/ci.yml`) corre en push/PR a **ambas**.

> **Pruebas en la nube, no en local**: el objetivo es dejar de levantar MySQL+app en cada PC
> (casa/trabajo) y probar `develop` contra un entorno de **staging en el propio VPS** (ver "entrada
> secreta" en el Roadmap), con UNA sola base de datos en la nube. Así el trabajo vive en el servidor
> y no hay que copiar bases entre máquinas.

> **Futuro (más adelante, no ahora)**: se sumará una tercera rama **"limpia"** = la plantilla base que
> se copia cada vez que se vende una instancia nueva. Se define cuando toque; hasta entonces, solo dos ramas.

## Convenciones

- Código, nombres de paquete, comentarios y textos de UI están **en español** — mantener ese idioma.
- Los **textos de UI** (labels, botones, mensajes al usuario) van en **español neutro/peruano (tuteo)**,
  NO en voseo argentino: "Ingresa"/"Escribe"/"Selecciona", nunca "Ingresá"/"Escribí"/"Seleccioná".
- **NO usar `placeholder` (marca de agua) en los campos de formulario** — preferencia explícita del
  cliente. Nada de texto de ejemplo gris dentro de los `<input>`; alcanza con el `<label>` (y un
  `<small>` de ayuda debajo si de verdad hace falta), pero el campo va vacío.
- Credenciales **nunca** hardcodeadas: siempre variables de entorno (ver `application.yml`).
- Despliegue en VPS documentado en `DEPLOY.md` (Docker: MySQL + app + Nginx, `docker-compose.prod.yml`).
  **Acceso admin: SSH por clave** (`ssh texcontrol` → `linuxuser@64.176.3.149`); password deshabilitado
  y `fail2ban` activo. **Tailscale fue REMOVIDO** (jul-2026): la web es pública por dominio y el SSH va
  directo por IP. Ojo: Docker tenía un override de systemd que lo ataba a `tailscaled` — ya se quitó, si
  algo similar reaparece revisar `/etc/systemd/system/docker.service.d/override.conf`.

## Infraestructura (producción) — resumen; el detalle vive en `DEPLOY.md`

- **VPS**: Vultr, Ubuntu 24.04, IP pública `64.176.3.149`. Acceso admin por
  **SSH con clave** (`ssh texcontrol`), password deshabilitado, `fail2ban`. **Sin
  Tailscale** (removido jul-2026). Consola web de Vultr = salvavidas si te bloqueás.
- **Dominio**: `texcontrol.pe`, **Cloudflare DNS-only** (nube gris) con dos `A`:
  `texcontrol.pe` y **`*.texcontrol.pe`** → IP del VPS. El **wildcard** hace que
  CUALQUIER subdominio nuevo (`dev.texcontrol.pe`, `<empresa>.texcontrol.pe`)
  resuelva solo, **sin tocar DNS**.
- **HTTPS**: certificado **wildcard** Let's Encrypt (`*.texcontrol.pe`, DNS-01 vía
  API de Cloudflare) en `/etc/letsencrypt/live/texcontrol.pe/`, renovación
  automática. Un subdominio nuevo ya queda cubierto sin emitir nada.
- **Rutas**: `login.texcontrol.pe` = lanzador; `<empresa>.texcontrol.pe` = la app.
- **Modelo actual = SINGLE-cliente**: UN stack (`docker-compose.yml` +
  `docker-compose.prod.yml`) = `textil_app` + `textil_mysql` + `textil_nginx`,
  corriendo `main`, sirviendo a `textillaura`. `BIND_IP=0.0.0.0`. La BD arranca de
  cero con las 36 migraciones + cuentas semilla. **Usar clave de BD sin símbolos**
  (hex): `deploy.sh` sincroniza leyendo el `.env` literal, pero el hex evita todo
  problema de escaping.
- **Multicliente** (roadmap, scaffolding listo en `multicliente/`): un stack
  aislado por empresa (`app_<slug>` + `db_<slug>`, BD propia, red `interna`) tras
  el proxy compartido **`texcontrol_proxy_nginx`** (red `texcontrol_red`). Alta con
  `scripts/nuevo-cliente.sh <slug> "<nombre>"`. Techo ~3 clientes en 4 GB de RAM.
  **OJO**: el proxy NO debe llamarse `textil_nginx` (ese nombre es del single-cliente
  y chocan) — es `texcontrol_proxy_nginx`.
- **Deploy** (NO es automático): `ssh texcontrol` → `cd ~/textil-inventario` →
  `./scripts/deploy.sh` (trae `main`, reconstruye la imagen, reinicia; los datos de
  MySQL no se tocan).

## Roadmap / pendientes

Estado actual (jul-2026): **en vivo** en `texcontrol.pe` (dominio + HTTPS wildcard; `login.texcontrol.pe`
= lanzador, `<empresa>.texcontrol.pe` = la app). **Modelo single-cliente**: hoy hay UN solo stack
(app + MySQL + nginx de `docker-compose.prod.yml`) sirviendo a `textillaura`. Infra: SSH por clave (sin
Tailscale), `fail2ban`, y Docker ya NO depende de Tailscale.

**Clientes** (una instancia por cliente, cada uno con su BD propia):
- **`textillaura`** — cliente actual = **Textil Laura + Textil Clemente** juntos (una sola instancia/BD).
- Futuro (todavía no vendidos): **Textil Camargo**, **Textil Emilio**.

### Estado de trabajo (dónde quedamos — sesión 24-jul-2026)

**✅ Entrada secreta / staging YA EN VIVO y en uso desde casa**: `dev.texcontrol.pe` (OCULTO, Basic Auth)
corre `develop` con su propia BD aislada (setup y uso en `STAGING.md`). El Basic Auth es usuario **`jlynch`**
(el archivo untracked `nginx/dev.htpasswd`; se resetea con `htpasswd -B nginx/dev.htpasswd jlynch` + `docker
exec textil_nginx nginx -s reload`). **Flujo de trabajo nuevo**: pushear a `develop` → en el VPS
`cd ~/textil-inventario && ./scripts/deploy-dev.sh` → probar en `dev.texcontrol.pe` → cuando anda, promover
`develop → main` + `./scripts/deploy.sh`. Se acabó levantar MySQL/app en local.

**SSH desde casa**: el alias `ssh texcontrol` de la PC de casa apuntaba a la vieja IP de Tailscale (ya
removido) → daba timeout. Corregido a la IP pública `64.176.3.149` en `~/.ssh/config` (clave
`~/.ssh/texcontrol_vps`, ya autorizada). Recordatorio: ese alias vive en cada PC, no dentro del VPS.

**Promovido a `main` el 24-jul** (ya en `origin/main`; falta correr `./scripts/deploy.sh` en el VPS para que
producción lo tome):
- **Limpieza**: se eliminó `texcontrol-logo-completo.png` (2.1 MB, sin uso) y se quitaron los consejos
  Tailscale obsoletos de la doc (`.env.example`, `DEPLOY.md`, ambos compose): hoy prod es pública por dominio
  con `BIND_IP=0.0.0.0`.
- **Empresas**: la carpeta de documentos se auto-genera del nombre (slug); el formulario quedó Nombre + RUC;
  el nombre bajo el logo TEXCONTROL ahora sale de las **empresas activas** (unidas por " & "). **Sin fallback**:
  si no hay empresas activas cargadas, el subtítulo no se muestra (nada de `NOMBRE_EMPRESA` hardcodeado, para que
  una copia recién entregada no exhiba el nombre de otro cliente). *Decisión (24-jul, confirmada)*: se deja en
  **MAYÚSCULAS** (así lo guarda el catálogo). La lista de Empresas ahora muestra **también las inactivas**
  (marcadas, con botón de **reactivar**): el ojito inactiva (`activo=false`) pero el RUC sigue ocupado por el
  constraint único, así que si quedaban ocultas trababan crear otra con el mismo RUC sin que se viera por qué.
- **Recepción**: "Crear artículo" ahora **crea las piezas base que falten** (tipo de tela/título/composición/
  acabado) en vez de cortar con "no existe en el catálogo base".

**⚠️ Tema de fondo (clave para multicliente): el sistema asume un catálogo YA poblado.** Un cliente nuevo
arranca con el catálogo vacío y choca con "no existe en el catálogo" en varios flujos. Hay que permitir crear
entradas al vuelo desde los flujos (como ya hace "Crear color"). Hecho: "Crear artículo". **Abierto:**
- **BUG a diagnosticar**: en la página de crear **Programa**, al "Crear color" nuevo el color no aparece en el
  desplegable de la línea. El código (`templates/programas/nuevo.html`, handler `btnGuardarColorRapidoPrograma`)
  se ve correcto; falta el síntoma exacto / error de consola del usuario para cazarlo.
- El form de Programa solo ofrece colores existentes → en dev vacío un programa queda con pocas líneas
  (esperado). Se resuelve con el crear-color-al-vuelo de arriba, o poblando el catálogo.

**Para probar flujos con datos reales en dev** (en vez del catálogo vacío): copiar prod → dev con
`mysqldump` de `textil_mysql` restaurado en `textil_mysql_dev` (aislado, `--ignore-table=...flyway_schema_history`,
no toca prod). Pendiente dejarlo como `scripts/sembrar-dev-desde-prod.sh`.

Falta, por orden de prioridad:

1. **Multi-cliente real (BD aislada por empresa)**: migrar de forma deliberada al modelo `multicliente/`
   (proxy `texcontrol_proxy_nginx` + un stack `app_<cliente>` + `db_<cliente>` por empresa, ruteados por
   subdominio). El scaffolding ya existe en `multicliente/` y `scripts/nuevo-cliente.sh`. Clientes arriba.
   Es la pieza que habilita el modelo de negocio.
2. **App móvil iOS/Android**: una app para celular para que **los usuarios ingresen desde el móvil**
   (los vendedores/almaceneros/gerentes de cada empresa). A definir: nativa contra una API REST
   (que hay que construir), PWA instalable sobre la web actual, o wrapper WebView. Pedido explícito
   del cliente. (Ya hay PWA instalable + sesión persistente móvil implementada.)
3. **Antes del primer cliente que pague**: backups automáticos (cron por cliente), rotar `jlynch`
   con clave única por copia, limpiar/cerrar cuentas de prueba, `NOMBRE_EMPRESA` por cliente.
4. **Marketing** en `texcontrol.pe` (hoy la raíz redirige a `login.`).
5. **Módulo de Ventas** (rol `VENDEDOR`, hoy sin permisos).
