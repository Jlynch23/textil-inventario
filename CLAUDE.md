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
./mvnw spring-boot:run

# Compilar / tests (lo mismo que corre CI en push/PR a main)
./mvnw -B clean compile
./mvnw -B test
```

> Usar el **Maven Wrapper** (`./mvnw`, `.mvn/wrapper/`), no un `mvn` del sistema:
> fija la versión de Maven y hace el build autocontenido (auditoría BUILD-01).
> CI y el Dockerfile también lo usan.

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
| `recepciones` | `/recepciones`, `/documentos` | Recepción en 4 pasos (documento → conteo físico → validación → confirmación). Incluye `RecepcionDocumento` y el visor/descarga de PDFs: el documento es parte del agregado recepción. |
| `ocr` | — | Lectura de guías/facturas con IA (`AnthropicOcrService`, `SYSTEM_PROMPT`) y sugerencia de artículo/color/empresa (`ArticuloMatchingService`). **Compartido** por `recepciones` y `archivohistorico`; no depende de ninguno de los dos. |
| `programas` | `/programas` | Programas de teñido: su propio ciclo de vida (líneas pedidas → recepciones que las van cumpliendo → completo). |
| `almacen` | `/almacen` | Entradas/salidas rápidas móviles del SUPERVISOR y la cola de revisión del ADMIN. |
| `transferencias` | `/transferencias` | Traslados entre ubicaciones con doble confirmación (salida → llegada) y reparto de una línea a varios destinos. |
| `inventario` | `/inventario` | Stock actual por ubicación y kardex (historial de movimientos). |
| `reportes` | `/reportes` | Stock, kardex, recepciones, transferencias, stock bajo — exportables a Excel (POI). |
| `archivohistorico` | `/archivo-historico` | Importación masiva de guías/facturas antiguas vía ZIP, leídas por IA en 2º plano; enriquece el catálogo. **Ojo**: con `crearRecepcionAutomatica` activo SÍ afecta stock — crea la recepción y la **confirma** (`crearYConfirmarRecepcionAutomatica`), o sea mueve stock y escribe kardex. Sin ese flag, solo catálogo. |
| `seguridad` | `/usuarios` | Usuarios y roles, integración con Spring Security. |
| `auditoria` | `/log` | Registro de eventos (`AuditLogService`, `LogEvento`). |
| `dashboard` | `/`, `/dashboard` | Indicadores en tiempo real. |
| `config` | — | `SecurityConfig`, `AsyncConfig` (OCR async), `GlobalExceptionHandler`, `GlobalModelAttributes`. |
| `common` | — | `BaseEntity` (id + timestamps), `RespuestaJson` (errores de los endpoints JSON), `FechaDocumento` / `NumeroDocumento` (formatos de guía y factura), `ValidadorPdf` / `ValidadorImagen`. Todo lo que usa más de un módulo y no es de ninguno. |

**Dónde poner una clase nueva**: si la usa UN módulo, va en ese módulo. Si la usan
dos o más y no tiene dominio propio (formato, validación, utilidades), va en `common`.
Si la usan dos o más y SÍ tiene dominio propio, va en su propio paquete — así salió
`ocr`, que estaba enterrado dentro de `recepciones` mientras `archivohistorico` lo
usaba igual (6 de sus 9 imports a `recepciones` eran clases de OCR).

**Ciclos entre paquetes** (los hay, y conviene no sumar más): `recepciones ↔ programas`,
`recepciones ↔ inventario`, `seguridad ↔ auditoria`. Ninguno es un ciclo de *entidades*
— las FK van en una sola dirección (`RecepcionDetalle → ProgramaDetalle`); son consultas
de una pantalla que necesita leer del otro lado. Al agregar un módulo, fijate que la FK
apunte para un solo lado y que la lectura cruzada sea eso, una lectura.

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
  aplicada; sumar una nueva con el siguiente número (actualmente van hasta **V46**). Las últimas:
  V42 (UNIQUE en `numero_guia`), V43 (`@Version` en `programa_detalle`), V44 (celular de usuario),
  V45 (emisor de la guía en `recepciones`), V46 (blancos `''` → NULL en guía/factura/color).

### Blanco = NULL (no `''`)
Un campo opcional vacío se guarda **NULL**, nunca cadena vacía: `''` rompe en silencio las
consultas `IsNull` y los `findBy`. Ya mordió dos veces — `numero_factura = ''` dejaba la recepción
invisible en **Facturar**, y `codigo_fast_dye = ''` hacía que el OCR eligiera un color al azar
entre todos los que tenían el código vacío. Los servicios normalizan blanco → NULL y **V46** reparó
las filas viejas. Al agregar un campo opcional, mantener el criterio.

### Empresas de la guía: destinatario por RUC, emisor leído del documento (V45)
Una guía tiene **dos** empresas y el sistema ya no confunde ninguna:
- **Destinatario** (empresa del cliente): el OCR extrae también su **RUC** y `ArticuloMatchingService.
  matchEmpresa(ruc, razonSocial, empresas)` cruza **por RUC** (es `unique` en `empresas`), exacto y sin
  ambigüedad. La razón social quedó solo como respaldo, y **ante un empate no sugiere nada** (antes
  ganaba la primera de la lista: eso imputaba recepciones a la empresa equivocada).
- **Emisor** (la tintorería que tiñó): se lee del documento y se guarda en `recepciones.emisor_nombre`
  / `emisor_ruc`. **No se asume FAST DYE** — el `SYSTEM_PROMPT` del OCR ya es genérico, porque en el
  modelo multicliente cada cliente trabaja con su propia tintorería (ver `ROADMAP.md`, V2).
- `ddl-auto: validate`: si una entidad no calza con el esquema migrado, la app no arranca.
- `baseline-on-migrate: true`.

### Rendimiento JPA (importante)
- **`open-in-view: false`** (apagado a propósito): la sesión de Hibernate se cierra al terminar la
  capa de servicio, NO durante el render. Por eso las vistas reciben entidades ya cargadas: los
  finders de detalle usan `@EntityGraph(type = LOAD)` (OJO: el tipo por defecto FETCH degrada a
  LAZY toda asociación NO listada, ¡incluidas las EAGER! — usar LOAD) o se fuerza la init dentro
  de `@Transactional`. Si agregás una pantalla que navega una asociación no precargada, va a
  reventar con `LazyInitializationException` — precargala en el finder, no reactives OSIV.
- `hibernate.default_batch_fetch_size: 16`: agrupa las cargas LAZY en `IN(:ids)` (mata N+1).

## Tests

`src/test/java/...`, JUnit 5 + Spring Boot Test. Lógica de servicio:
`RecepcionServiceTest`, `ArticuloMatchingServiceTest`, `TransferenciaServiceTest`,
`CatalogoServiceTest`, `ArchivoHistoricoServiceTest`, `DocumentoHistoricoClasificadorTest`,
`GeneradorUsernameTest`, `ValidadorPdfTest`, `VersionOptimistaTest`.

CI (`.github/workflows/ci.yml`), en cada push/PR a `develop` y `main`, corre **dos jobs**:
- `build-and-test`: `./mvnw -B clean compile` + `./mvnw -B test` (tests con Mockito, sin BD).
- `validar-esquema`: levanta un **MySQL 8** de servicio y corre los tests marcados
  `@EnabledIfEnvironmentVariable(RUN_DB_IT=true)` contra BD real: `EsquemaFlywayTest` (valida que
  el esquema Flyway calce con las entidades, `ddl-auto: validate`), `ConfirmacionConcurrenteTest`
  (concurrencia real de confirmación) y `OsivFetchGraphTest` (guarda anti-regresión de OSIV:
  verifica que las pantallas de detalle traigan sus asociaciones inicializadas). Al sumar un test
  de este tipo, agregalo al `-Dtest=...` del job `validar-esquema`.

### Seguridad de front (CSP)
`CspNonceFilter` emite una Content-Security-Policy con **nonce por request**: `script-src 'self'
'nonce-XXX'` bloquea cualquier `<script>` inyectado (XSS). Los `<script>` inline propios llevan
`nonce=${cspNonce}` (lo puentea `GlobalModelAttributes` al modelo). Se mantiene a propósito
`script-src-attr 'unsafe-inline'` para no reescribir los ~30 `onclick/onchange` inline. Al agregar
un `<script>` inline nuevo, ponele `th:attr="nonce=${cspNonce}"` o la CSP lo bloqueará.

## Flujo de trabajo (ramas y ambientes)

**Solo existen DOS ramas y NO se crean otras** (nada de `feature/*`, `claude/*`, ni ramas efímeras
por tarea — es una molestia explícita del dueño):
- **`develop`**: rama de **trabajo y pruebas**. Todo lo nuevo pasa primero por acá.
- **`main`**: **producción**. Es la rama que corren los clientes: en el VPS el clon
  `~/textil-inventario` debe estar en **`main`**, y al actualizar se reconstruye la imagen desde ahí y
  se reinician las apps de los clientes (ver "Actualizar el código de TODOS los clientes" en
  Infraestructura). **Solo** se mergea `develop → main` cuando está probado y estable.

Sobre esas dos ramas corren **TRES ambientes** (ambiente ≠ rama: el demo NO tiene rama propia,
es la misma app con datos/config distintos):

| Ambiente | URL | Rama que corre | Para quién | Acceso |
|---|---|---|---|---|
| **DEV** (staging) | `dev.texcontrol.pe` | `develop` | El proveedor (probar lo nuevo) | Oculto, Basic Auth (`STAGING.md`) |
| **DEMO** | `demo.texcontrol.pe` | `main` (estable) | **Clientes potenciales** (prospectos) | Público, cuentas demo (`DEMO.md`) |
| **PRODUCCIÓN** | `<empresa>.texcontrol.pe` | `main` | Clientes que pagan | Cada uno su instancia y BD |

El **DEMO** es una instancia más del modelo multi-cliente (BD `db_demo` aislada) sembrada con
datos de ejemplo y 10 cuentas para repartir (`scripts/nuevo-demo.sh`, reset manual con
`scripts/resetear-demo.sh`, seed en `scripts/demo-seed.sql`; jlynch queda con clave rotada
privada porque el ambiente es público). Detalle completo en `DEMO.md`.

Regla: nunca pushear features a medio hacer a `main`; probar en `develop` (staging `dev.texcontrol.pe`),
y recién cuando anda, promover a `main` y reconstruir los clientes (el demo, al correr `main`, se
actualiza reconstruyendo su stack igual que un cliente). CI (`.github/workflows/ci.yml`) corre
en push/PR a **ambas**. Nota: `scripts/deploy.sh`/`deploy-dev.sh` eran del modelo single-cliente; hoy el
despliegue de producción es el bucle multicliente de arriba (el `deploy-dev.sh` sí sigue vigente para staging).

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
- **Modelo actual = MULTICLIENTE (EN VIVO desde ago-2026)**: un stack aislado por
  empresa (`app_<slug>` + `db_<slug>`, BD propia, red privada `interna`) tras el
  proxy compartido **`texcontrol_proxy_nginx`** (red `texcontrol_red`), que rutea
  `<slug>.texcontrol.pe` → `app_<slug>`. **Hoy (6-ago-2026) NO hay clientes de pago
  dados de alta**: `textillaura` y `textilcamargo` se dieron de baja el 5-ago y
  `clientes/` quedó vacío. El único inquilino corriendo es el **demo**. Techo ~3
  clientes en 4 GB.
  **OJO**: el proxy NO debe llamarse `textil_nginx` — es `texcontrol_proxy_nginx`.
  El viejo stack single-cliente (`docker-compose.prod.yml` = `textil_app` +
  `textil_mysql` + `textil_nginx`) **fue decomisionado** en la migración; sus
  archivos siguen en el repo por historia pero YA NO se usan.
- **Alta / gestión de clientes** (scripts en `scripts/*-cliente.sh`, detalle en
  DEPLOY.md 6.6): `nuevo-cliente.sh <slug> "<Nombre>"` (crea BD aislada, levanta el
  stack, genera el bloque nginx, recarga el proxy y ENDURECE — rota `jlynch` a una
  clave única e imprime UNA vez, borra cuentas de prueba). Otros: `listar-clientes.sh`,
  `backup-cliente.sh --todos` (cron diario 2am via `instalar-cron-backups.sh`),
  `eliminar-cliente.sh`, `endurecer-cliente.sh` (re-rota `jlynch`), `migrar-cliente.sh`.
  **OCR**: `ANTHROPIC_API_KEY` (del proveedor, la MISMA para todos) se guarda UNA vez en
  el VPS con `configurar-proveedor.sh` (queda en `~/.texcontrol/proveedor.env`, 600;
  `lib-cliente.sh` la carga sola) — ya no hace falta anteponerla en cada comando.
  `--aplicar` además la copia a los clientes ya creados y reinicia sus apps.
- **Ambiente DEMO** (`demo.texcontrol.pe`, público, para prospectos): `nuevo-demo.sh`
  (alta + seed + endurecer) y `resetear-demo.sh` (foja cero manual). Ver `DEMO.md`.
  Cuesta ~0.8–1 GB de RAM como cualquier cliente — cuenta para el techo del VPS.
- **Actualizar el código de TODOS los clientes** (comparten imagen): en el VPS, con
  el clon en **`main`**, `git pull` → `docker build -t texcontrol-app:latest .` →
  reiniciar cada app: `for e in clientes/*/.env; do s=$(basename $(dirname $e)); \
  docker compose -p texcontrol_$s --env-file $e -f multicliente/docker-compose.cliente.yml up -d; done`.
  (Reemplaza al viejo `deploy.sh`, que era del modelo single-cliente.)
- **`dev.texcontrol.pe`** (staging): su stack (`docker-compose.dev.yml`: `textil_app_dev`
  + `textil_mysql_dev`) se une a `texcontrol_red` para que el proxy lo alcance; el
  bloque `dev.` (Basic Auth) vive en `multicliente/nginx/00-texcontrol.conf`.

## Roadmap / pendientes

Estado actual (ago-2026): **en vivo** en `texcontrol.pe` (dominio + HTTPS wildcard; `login.texcontrol.pe`
= lanzador, `<empresa>.texcontrol.pe` = la app). **Modelo MULTICLIENTE en vivo**: proxy
`texcontrol_proxy_nginx` + un stack `app_<slug>`+`db_<slug>` aislado por cliente (ver sección
"Infraestructura"). El single-cliente fue decomisionado. Infra: SSH por clave (sin Tailscale),
`fail2ban`, y Docker ya NO depende de Tailscale.

**Clientes** (una instancia por cliente, cada uno con su BD propia y su clave de `jlynch`):
- **Ninguno dado de alta al 6-ago-2026.** `textillaura` (Textil Laura + Textil Clemente) y
  `textilcamargo` (Textil Camargo) estuvieron en vivo y **se borraron el 5-ago**; no quedan
  contenedores ni volúmenes suyos. Se vuelven a levantar con `nuevo-cliente.sh <slug> "<Nombre>"`.
- Futuro: **Textil Emilio**. Ojo al techo de RAM (~3 clientes en 4 GB): al sumar
  el 3.º pagando, subir la RAM del VPS.

> **Verificá antes de creer esta lista**: `./scripts/listar-clientes.sh` (o `docker ps`) es la
> fuente de verdad. Este archivo se desactualiza cada vez que se da de alta o de baja a alguien.

### Estado de trabajo (dónde quedamos — sesión 24-jul-2026)

**✅ Sprint de hardening COMPLETO (ago-2026, en `develop` → promovido a `main`)**: cerrada la auditoría
red-team + Sprint 2-5. Concurrencia (`@Version` + lock optimista en confirmaciones), tabla `correlativo`
(numeración de transferencias sin colisiones), DTOs anti mass-assignment en todo el catálogo, logout por
POST + CSRF, split de `CatalogoController`/`ArchivoHistoricoService`, Excel por streaming, JS extraído a
`/js/*.js`. **Rendimiento JPA**: `open-in-view` APAGADO con fetch-graphs `type=LOAD` en las vistas de
detalle + `default_batch_fetch_size` (ver sección "Rendimiento JPA"). **Seguridad front**: CSP con nonce
por request (`CspNonceFilter`). **Estáticos cacheables** (cadena de seguridad dedicada, sin `no-store`).
Guarda de CI `OsivFetchGraphTest` contra MySQL real. Validación de PDF (`%PDF-`), límite de OCR concurrente
(Semaphore), `JAVA_TOOL_OPTIONS` de memoria, `scripts/verificar-backup.sh`.

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
- **"Crear al vuelo" en Programa** (color/tipo de tela/título/composición): revisado, el código estaba correcto
  (reconstruye TODOS los `<select>` y preserva lo elegido en las demás líneas). El fallo puntual no reproducible
  ("un color nuevo no apareció") se atribuyó a doble-submit; se endurecieron los 4 botones: se bloquean durante
  el alta + dedupe por id. `templates/programas/nuevo.html`.
- **Borrar Programa** (nuevo): botón en la lista, solo ADMIN/SUPERADMIN, borrado **PROTEGIDO** — si el programa
  ya tiene recepciones asociadas no se borra y avisa (`existsByProgramaDetalle_ProgramaId`). Si no, cascade borra
  sus líneas. `ProgramaController/Service`, `programas/lista.html`.
- El form de Programa solo ofrece colores existentes → en dev vacío un programa queda con pocas líneas
  (esperado). Se resuelve con el crear-color-al-vuelo de arriba, o poblando el catálogo.

**Para probar flujos con datos reales en dev** (en vez del catálogo vacío): copiar prod → dev con
`mysqldump` de `textil_mysql` restaurado en `textil_mysql_dev` (aislado, `--ignore-table=...flyway_schema_history`,
no toca prod). Pendiente dejarlo como `scripts/sembrar-dev-desde-prod.sh`.

**✅ Multi-cliente real EN VIVO (ago-2026)**: migrado del single-cliente al modelo `multicliente/`
(proxy `texcontrol_proxy_nginx` + `app_<slug>`+`db_<slug>` aislado por cliente, ruteados por subdominio).
`textillaura` y `textilcamargo` se dieron de alta, se endurecieron y **se borraron el 5-ago**; el
mecanismo quedó probado de punta a punta. Backups diarios (cron 2am), `jlynch` con clave única por
copia, cuentas de prueba eliminadas, `dev.texcontrol.pe` reconectado al proxy nuevo.
Es la pieza que habilita el modelo de negocio — **ya está**.

> **Ojo con el cron de backups**: `backup-cliente.sh --todos` itera `clientes/*/.env` y, si no hay
> ninguno, imprime "No hay clientes que respaldar" y sale con código **0**. Sin clientes eso es
> correcto, pero significa que un `clientes/` vacío por error se ve igual que "todo bien" en el log.
> Al dar de alta un cliente, confirmá que su backup aparezca en `~/backups/backup.log`.

**El demo quedó huérfano de los scripts (6-ago)**: `app_demo` y `db_demo` corren, pero su
`clientes/demo/.env` no está, así que `actualizar-clientes.sh`, `backup-cliente.sh` y
`endurecer-cliente.sh` no lo alcanzan. Las credenciales siguen dentro de los contenedores
(`docker inspect app_demo --format '{{range .Config.Env}}{{println .}}{{end}}'`) y con eso se
puede rearmar el `.env`; la otra opción es recrearlo con `nuevo-demo.sh` cuando haga falta.

Falta, por orden de prioridad:

1. **App móvil iOS/Android**: una app para celular para que **los usuarios ingresen desde el móvil**
   (los vendedores/almaceneros/gerentes de cada empresa). A definir: nativa contra una API REST
   (que hay que construir), PWA instalable sobre la web actual, o wrapper WebView. Pedido explícito
   del cliente. (Ya hay PWA instalable + sesión persistente móvil implementada.)
2. **Al sumar el 3.er cliente (Emilio) pagando**: subir la RAM del VPS (techo ~3 clientes en 4 GB) o
   evaluar MySQL compartido. Crear cada cliente con `NOMBRE_EMPRESA` propio (ya lo hace el script).
3. **Marketing** en `texcontrol.pe` (hoy la raíz redirige a `login.`).
4. **Módulo de Ventas** (rol `VENDEDOR`, hoy sin permisos).
