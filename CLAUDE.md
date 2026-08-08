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

Opcionales, todas con default (si no se setean, la app arranca igual):
- `STOCK_BAJO_UMBRAL` (def. `10`) — cuántos rollos, sumando TODAS las ubicaciones por
  artículo+color, marcan un ítem como "stock bajo" en el **Dashboard** y en el **reporte**.
  Es umbral de *visualización* y lo leen los dos desde `inventario.stock-bajo.umbral`.
- `ALERTA_STOCK_ENABLED` (def. `false`), `ALERTA_STOCK_UMBRAL` (def. `5`),
  `ALERTA_STOCK_UBICACION` — la **alerta** de stock bajo. Es otra cosa: mira UNA ubicación y
  salta en el cruce hacia abajo. Que el Dashboard liste un ítem y no haya llegado aviso es lo
  esperado, no una falla.
- `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` — SMTP de esa alerta. Sin
  credenciales el canal queda apagado y solo se loguea el aviso no enviado.

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

> ## ⛔ REGLA DE ORO: `jlynch` NUNCA se borra
>
> Ni en dev, ni en el demo, ni en un cliente, ni "para limpiar", ni en un script. Tampoco
> ninguna otra cuenta con rol **SUPERADMIN**.
>
> Es la **única** cuenta capaz de entrar a una copia recién entregada, y en las que ya están
> endurecidas (`endurecer-cliente.sh` borra las de prueba) suele ser la única que existe. Si se
> borra, **la instancia queda inaccesible desde la web**: no hay pantalla de recuperación, hay
> que meter un `INSERT` a mano en MySQL con un hash de bcrypt generado por fuera.
>
> Todo script que toque `usuarios` lleva la protección **clavada en el código**, nunca como
> parámetro que se pueda pisar desde la línea de comandos —
> ver `scripts/borrar-usuarios-dev-demo.sh`.

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
| `alertas` | — | Aviso de **stock bajo**. `AlertaStockPublisher` detecta el **cruce hacia abajo** (venía ≥ umbral y quedó por debajo) en UNA ubicación; `AlertaStockListener` lo despacha `AFTER_COMMIT` y `@Async` (nunca dentro de la transacción que mueve el stock). Canal activo: **correo** (`NotificadorEmailSmtp`, `@Primary`); `NotificadorSmsTwilio` implementa la misma interfaz pero no está cableado. Agregar WhatsApp = otra implementación de `NotificadorStockBajo`, sin tocar el resto. |
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

## OCR con IA (`ocr/AnthropicOcrService.java`)

Llama directamente a la API de Anthropic vía `RestClient` (connect 30s / read 90s — hay comentario
SEC-03 explicando por qué el timeout importa: corre en `@Async`, sin él un proveedor caído agota el
pool de hilos). El `SYSTEM_PROMPT` contiene reglas de normalización muy específicas del dominio
(tipoTela, título, composición MELANGE/MLG, acabado) para las guías de **FAST DYE**. Si se ajusta el
parsing de guías, ese prompt es la fuente de verdad.

### El prompt no es una garantía: hay que VERIFICAR lo que la IA devuelve

Mordió de verdad (6-ago, guía TG01-00020379 del programa 472). La descripción decía
`Tela RIB 2X1 24/1 ALG LIST BLANCO Color 732631 NEGRO 2` y la lectura devolvió acabado **LISO**.
El prompt **ya describía bien** esa regla (`LIST X` → `LISTADO X`) y hasta usaba esa misma guía como
ejemplo; falló igual. Consecuencias: 18 rollos entraron al stock como LISO NEGRO en vez de LISTADO
BLANCO NEGRO, y como el artículo resultante era otro, la línea no vinculó con su línea del programa,
que quedó pendiente para siempre.

Lo que lo hacía **invisible**: el defecto de `ArticuloMatchingService` cuando no se lee acabado es
también `LISO`, así que un acabado mal leído y un LISO real eran indistinguibles.

Por eso el OCR pide además `descripcionOriginal` (el texto del ítem **literal**, sin interpretar) y
`ArticuloMatchingService.desajusteDeAcabado()` lo contrasta: si el texto dice LIST/LISTADO o
ACANALADO y se leyó otra cosa (o al revés), la línea vuelve **sin resolver** con el motivo, para que
una persona elija. Solo mira la parte **anterior a `Color`** — lo que sigue es el nombre del color,
texto libre donde un "AZUL LISTÓN" daría falso positivo.

> **Regla general**: la IA extrae y sugiere; **no confirma inventario sin una verificación
> determinista**. Al agregar un campo que la IA lea y que decida a qué artículo entra la tela,
> pensá cómo comprobarlo contra el texto original — no alcanza con pedirlo mejor en el prompt.

También por eso la pantalla de recepción muestra **siempre** `Guía dice: <tela> <título> ·
<composición> · <ACABADO>` debajo de cada artículo: el `<select>` muestra el artículo del catálogo
(y oculta el acabado LISO por convención), así que sin esa línea no hay forma de contrastar a ojo lo
que se leyó contra lo que quedó elegido.

## Base de datos / migraciones

- Todo cambio de esquema es una migración Flyway nueva en
  `src/main/resources/db/migration/V<n>__descripcion.sql`. **Nunca** editar una migración ya
  aplicada; sumar una nueva con el siguiente número (actualmente van hasta **V47**). Las últimas:
  V43 (`@Version` en `programa_detalle`), V44 (celular de usuario), V45 (emisor de la guía en
  `recepciones`), V46 (blancos `''` → NULL en guía/factura/color), V47 (kardex generalizado).

### El kardex no tiene una columna por tipo de documento (V47)
El vínculo con el papel que originó el movimiento es el par **`(tipo_documento, documento_id)`**,
no una columna por tipo. Antes eran dos (`recepcion_detalle_id`, `transferencia_id`); V2 suma
compra de hilo, orden de tejido y envío a tintorería, o sea **cinco columnas con cuatro siempre
NULL** en cada fila y un `ALTER TABLE` sobre la tabla más grande del sistema por cada módulo nuevo.

El precio consciente es que **se perdieron las FK reales**: la integridad la sostiene la app, no
la base. Por eso el par **no tiene setters sueltos** — se escribe con `vincularDocumento(tipo, id)`
(o los atajos `vincularRecepcionDetalle` / `vincularTransferencia`), que exigen los dos datos
juntos, y se lee con `getRecepcionDetalleId()` / `getTransferenciaId()`, que devuelven el id **solo
si el tipo calza**. Leer `documentoId` a secas invita a asumir un tipo que no es: el id 7 puede ser
una recepción o una transferencia. Al sumar un tipo de documento, agregalo al enum `TipoDocumento`
y usá los mismos accesores; **no** vuelvas a agregar una columna.

Por el mismo criterio, `tipo_movimiento` y `tipo_documento` se guardan como **VARCHAR, no como
`ENUM` de MySQL**: sumar un valor no debe obligar a un `ALTER`. A cambio MySQL ya no valida el
conjunto, así que el largo del nombre lo cuida un test (`KardexDocumentoTest`, columnas de 30).

`TipoMovimiento` incluye **`TRANSFORMACION_IN` / `TRANSFORMACION_OUT`**: los otros tipos mueven el
*mismo* material o lo ajustan, y una transformación es otra cosa (entran 100 kg de hilo, salen 80
de tela cruda — un material se consume y aparece otro). Las dos caras del mismo hecho se unen por
**`operacion_id`**, que no es lo mismo que el documento: una orden de tejido puede tener varias
transformaciones y sin ese id no se sabe qué hilo salió para qué rollo. Queda NULL hasta V2.

> **Pendiente de diseño, a propósito**: `color_id` sigue **NOT NULL** y el stock sigue exigiendo
> rollos. El hilo crudo no tiene color y se compra en **kilos**, así que V2 lo va a necesitar —
> pero depende de una decisión abierta (generalizar `Articulo` a "material" vs. una entidad por
> etapa) y no se prejuzgó. Ver `ROADMAP.md`, "Tres cosas que NO aguantan V2", punto 3.

### Blanco = NULL (no `''`)
Un campo opcional vacío se guarda **NULL**, nunca cadena vacía: `''` rompe en silencio las
consultas `IsNull` y los `findBy`. Ya mordió dos veces — `numero_factura = ''` dejaba la recepción
invisible en **Facturar**, y `codigo_fast_dye = ''` hacía que el OCR eligiera un color al azar
entre todos los que tenían el código vacío. Los servicios normalizan blanco → NULL y **V46** reparó
las filas viejas. Al agregar un campo opcional, mantener el criterio.

### Fechas del papel: `dd/MM/yyyy`, NO ISO (`common/FechaDocumento`)
Las guías y facturas peruanas traen la fecha **día/mes/año** (`06/03/2026` = 6 de marzo). El
navegador y la BD la quieren en **ISO** (`2026-03-06`). Convertir en el lugar equivocado invierte
día y mes **sin error visible**: `06/03` se guardaba como 3 de junio. Solo se nota cuando el día
es ≤ 12; con `13/03` en adelante revienta o queda vacío, que es como se descubrió.

Mordió **dos veces el mismo día** (6-ago): primero en la fecha de la guía, y el mismo defecto
estaba clonado en la de la factura. Toda conversión pasa por `common/FechaDocumento`
(`parse` entiende los dos formatos, `aIso` devuelve lo que espera un `<input type="date">`).
**No parsear fechas de documentos a mano en un servicio o en JS.** Ver `FechaDocumentoTest`.

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

`src/test/java/...`, JUnit 5 + Spring Boot Test — **171 tests** (`./mvnw -B test`). El test vive en
el paquete de lo que prueba (al mover una clase de paquete, mové su test).

- **Servicios**: `RecepcionServiceTest` (incluye los guards de confirmación: líneas repetidas,
  líneas faltantes en el POST, factura de una sola empresa, y la constancia de las líneas
  excluidas al registrar), `ProgramaServiceTest` (cambiar el artículo/color de una línea
  ya existente, el guard que lo impide si esa línea ya recibió tela, y la traducción de los
  filtros de la lista a la consulta, y los vecinos anterior/siguiente), `TransferenciaServiceTest`,
  `CatalogoServiceTest`, `ArchivoHistoricoServiceTest`, `DocumentoHistoricoClasificadorTest`,
  `DocumentoStorageServiceTest`, `StockPorColorTest`.
- **OCR**: `ArticuloMatchingServiceTest` — matching de artículo/color/empresa **y** la verificación
  del acabado contra el texto literal de la guía (`desajusteDeAcabado`).
- **Inventario**: `KardexDocumentoTest` — el par `(tipo_documento, documento_id)` de V47: que los
  accesores tipados no confundan una recepción con una transferencia del mismo id, que el vínculo
  exija los dos datos juntos, y que los nombres de los enums entren en su columna.
- **`common`**: `FechaDocumentoTest` (día/mes vs ISO), `NumeroDocumentoTest`, `ValidadorPdfTest`,
  `ValidadorImagenTest`, `VersionOptimistaTest`.
- **Catálogo / seguridad**: `ArticuloDescripcionTest`, `GeneradorUsernameTest`, `CelularUsuarioTest`.
- **Anti-regresión**: `AppendOnlyTest` (el kardex no se edita).

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
en push/PR a **ambas**. Nota: el despliegue de producción es el bucle multicliente; el
despliegue de producción es el bucle multicliente de arriba (el `deploy-dev.sh` sí sigue vigente para staging).

> **Pruebas en la nube, no en local**: el objetivo es dejar de levantar MySQL+app en cada PC
> (casa/trabajo) y probar `develop` contra un entorno de **staging en el propio VPS** (ver "entrada
> secreta" en el Roadmap), con UNA sola base de datos en la nube. Así el trabajo vive en el servidor
> y no hay que copiar bases entre máquinas.

**Dejar dev en foja cero para probar un flujo**: `scripts/limpiar-dev-dejando-programas.sh` borra
todo el movimiento (recepciones, stock, kardex, transferencias, entradas/salidas rápidas, archivo
histórico y los PDFs de `documentos-dev/`) y **deja los programas y el catálogo**, con
`cantidad_recibida` vuelta a 0 — para cargar las guías de a una y ver bajar el pendiente. Solo toca
`textil_mysql_dev` (el contenedor está fijo en el código, no llega a ningún cliente), pide escribir
`LIMPIAR`, y hace `mysqldump` a `backups-dev/` antes de borrar. **Correlo desde el clon de dev**
(`~/textil-inventario-dev`), que es el que tiene el `.env.dev`.

> **Futuro (más adelante, no ahora)**: se sumará una tercera rama **"limpia"** = la plantilla base que
> se copia cada vez que se vende una instancia nueva. Se define cuando toque; hasta entonces, solo dos ramas.

## Convenciones

- Código, nombres de paquete, comentarios y textos de UI están **en español** — mantener ese idioma.
- Muchos comentarios citan hallazgos de auditoría por su código (`INV-02`, `P0-1 C3`, `SEC-03`,
  `M6`, `OCR-01`…). Los informes completos **se borraron del repo el 6-ago** por estar cerrados y
  no ser referenciados por nada activo; siguen en el historial:
  `git log --diff-filter=D --name-only -- 'AUDITORIA*.md'` da el commit, y
  `git show <commit>^:AUDITORIA-CONSOLIDADA-2026-08-01.md` recupera el contenido. En general el
  comentario del código ya explica el porqué y no hace falta ir al informe.
- Los **textos de UI** (labels, botones, mensajes al usuario) van en **español neutro/peruano (tuteo)**,
  NO en voseo argentino: "Ingresa"/"Escribe"/"Selecciona", nunca "Ingresá"/"Escribí"/"Seleccioná".
- **Fragmentos Thymeleaf antes que markup repetido**: `templates/fragments/` tiene piezas
  compartidas — `guias.html` (`:: ojoGuia(docId)` / `:: ojoFactura(docId)`, el ojito que abre el
  PDF; no renderiza nada si el id es null) y `modales-catalogo.html` (altas rápidas). Escribir el
  ojito a mano hace que el mismo botón se vea distinto según la pantalla, que es lo que pasó en
  Programas. Si necesitás uno igual en otro lado, **llamá al fragmento**.
- **NO usar `placeholder` (marca de agua) en los campos de formulario** — preferencia explícita del
  cliente. Nada de texto de ejemplo gris dentro de los `<input>`; alcanza con el `<label>` (y un
  `<small>` de ayuda debajo si de verdad hace falta), pero el campo va vacío.
- Credenciales **nunca** hardcodeadas: siempre variables de entorno (ver `application.yml`).
- Despliegue en VPS documentado en `DEPLOY.md` (Docker: proxy nginx compartido + un `app_<slug>`+`db_<slug>` por cliente).
  **Acceso admin: SSH por clave** (`ssh texcontrol` → `linuxuser@64.176.3.149`); password deshabilitado
  y `fail2ban` activo. **Tailscale fue REMOVIDO** (jul-2026): la web es pública por dominio y el SSH va
  directo por IP. Ojo: Docker tenía un override de systemd que lo ataba a `tailscaled` — ya se quitó, si
  algo similar reaparece revisar `/etc/systemd/system/docker.service.d/override.conf`.

### Actualizar un webjar (Bootstrap / bootstrap-icons): NO se mergea con el botón

Los estáticos de Bootstrap se sirven **localmente** desde webjars (M11: antes venían del CDN de
jsdelivr, que el service worker no podía cachear por ser otro origen → la PWA quedaba sin estilos
offline). El precio es que **la versión va también en la URL** — `/webjars/bootstrap/5.3.8/css/…` —
escrita a mano en **6 archivos**: `layout/base.html`, `error.html`, los tres de `almacen/`
(`home`/`entrada`/`salida`) y `static/sw.js`.

Un PR de Dependabot **solo toca el `pom.xml`**. Mergeado tal cual, el classpath pasa a servir la
ruta nueva mientras las plantillas piden la vieja → **404 en todo el CSS y el JS**. La app arranca
y responde; simplemente se ve sin un solo estilo.

> **Y CI pasa en verde**: `mvn compile` + `mvn test` no abren un navegador, así que no hay ningún
> test que lo agarre. Es el mismo patrón que el acabado del OCR — el fallo no avisa, hay que ir a
> buscarlo. **Verificalo a ojo en `dev.texcontrol.pe` antes de promover.**

Procedimiento (así se hizo el 8-ago con 5.3.0→5.3.8 y 1.11.0→1.13.1):
1. Confirmar que el jar nuevo sirve las mismas rutas, no asumirlo:
   `unzip -l ~/.m2/repository/org/webjars/bootstrap/<ver>/bootstrap-<ver>.jar | grep bootstrap.min.css`
2. Subir la versión en `pom.xml`.
3. Reemplazar la versión en las URLs de los 6 archivos (`grep -rn "webjars/bootstrap/<vieja>" src/`
   debe quedar en cero).
4. **Subir `CACHE_VERSION` en `sw.js`**. Si no, un celular que ya instaló la PWA sigue sirviendo el
   CSS viejo desde su cache, aunque el servidor ya tenga el nuevo.
5. `./mvnw -B clean compile test` y verificación visual en dev.

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
  El viejo stack single-cliente (`textil_app` + `textil_mysql` + `textil_nginx`) **fue
  decomisionado** en la migración y sus archivos se **eliminaron del repo** el 6-ago
  (`docker-compose.prod.yml`, `nginx/nginx.conf`, `scripts/deploy.sh`, `backup-db.sh`,
  `restore-db.sh`, `migrar-cliente.sh`). Están en el historial de git si hicieran falta.
- **Alta / gestión de clientes** (scripts en `scripts/*-cliente.sh`, detalle en
  DEPLOY.md 6.6): `nuevo-cliente.sh <slug> "<Nombre>"` (crea BD aislada, levanta el
  stack, genera el bloque nginx, recarga el proxy y ENDURECE — rota `jlynch` a una
  clave única e imprime UNA vez, borra cuentas de prueba). Otros: `listar-clientes.sh`,
  `backup-cliente.sh --todos` (cron diario 2am via `instalar-cron-backups.sh`),
  `eliminar-cliente.sh`, `endurecer-cliente.sh` (re-rota `jlynch`), `restaurar-cliente.sh`
  (contraparte de backup, sobreescribe la base y guarda antes el estado actual) y
  `verificar-backup.sh` (restaura en una base desechable y valida que el backup sirva).
  **OCR**: `ANTHROPIC_API_KEY` (del proveedor, la MISMA para todos) se guarda UNA vez en
  el VPS con `configurar-proveedor.sh` (queda en `~/.texcontrol/proveedor.env`, 600;
  `lib-cliente.sh` la carga sola) — ya no hace falta anteponerla en cada comando.
  `--aplicar` además la copia a los clientes ya creados y reinicia sus apps.
- **Ambiente DEMO** (`demo.texcontrol.pe`, público, para prospectos): `nuevo-demo.sh`
  (alta + seed + endurecer), `resetear-demo.sh` (foja cero manual), `foto-demo.sh` (guarda el
  estado exacto —BD + documentos— con un nombre y permite VOLVER a él: sirve para ensuciar el
  demo en una presentación y restaurarlo después) y `sembrar-demo-desde-dev.sh`. Ver `DEMO.md`.
  Cuesta ~0.8–1 GB de RAM como cualquier cliente — cuenta para el techo del VPS.
- **Otros scripts útiles**: `estado-vps.sh` (foto de CPU/RAM/disco y consumo por contenedor, para
  ver cuánto queda del techo de ~4 GB).
- **Actualizar el código de TODOS los clientes** (comparten imagen): en el VPS, con
  el clon en **`main`**, `git pull` → `docker build -t texcontrol-app:latest .` →
  reiniciar cada app: `for e in clientes/*/.env; do s=$(basename $(dirname $e)); \
  docker compose -p texcontrol_$s --env-file $e -f multicliente/docker-compose.cliente.yml up -d; done`.
  (Reemplaza al viejo `deploy.sh`, que era del modelo single-cliente.)
- **`dev.texcontrol.pe`** (staging): su stack (`docker-compose.dev.yml`: `textil_app_dev`
  + `textil_mysql_dev`) se une a `texcontrol_red` para que el proxy lo alcance; el
  bloque `dev.` (Basic Auth) vive en `multicliente/nginx/00-texcontrol.conf`.

## Roadmap / pendientes

> **La hoja de ruta vive en `ROADMAP.md`, no acá.** Existe además un **«Roadmap Oficial de Producto
> y Tecnología 2026-2027 v1.0»** (PDF, 5-ago, 22 pág., con auditoría del repo, gates G0–G5, métricas
> y riesgos). Su secuencia se adopta; `ROADMAP.md` guarda el mapa de numeración (el PDF usa
> V0.1–V1.0, el repo V1–V5) y **cuatro correcciones de ejecución**: (0) el cuello de botella no es
> programar sino tener un cliente operando — los gates G3/G4 piden ciclos productivos y muestra
> física reales, y hoy hay cero clientes; (1) WhatsApp sale del camino crítico de V0.1 porque la
> verificación en Meta es un trámite externo; (2) el kardex generalizado va ANTES de la primera
> tabla de hilo; (3) V0.2 en tres sub-gates; (4) costo estimado antes que costo real.
> **Si el PDF y `ROADMAP.md` difieren, gana `ROADMAP.md`** (se actualiza con cada commit).

Estado actual (ago-2026): **en vivo** en `texcontrol.pe` (dominio + HTTPS wildcard; `login.texcontrol.pe`
= lanzador, `<empresa>.texcontrol.pe` = la app). **Modelo MULTICLIENTE en vivo**: proxy
`texcontrol_proxy_nginx` + un stack `app_<slug>`+`db_<slug>` aislado por cliente (ver sección
"Infraestructura"). El single-cliente fue decomisionado. Infra: SSH por clave (sin Tailscale),
`fail2ban`, y Docker ya NO depende de Tailscale.

**Clientes** (una instancia por cliente, cada uno con su BD propia y su clave de `jlynch`):
- **Ningún cliente de pago al 7-ago-2026.** El único inquilino corriendo es el **demo**
  (`listar-clientes.sh` devuelve `demo` y nada más). `textillaura` (Textil Laura + Textil Clemente)
  y `textilcamargo` (Textil Camargo) estuvieron en vivo y **se borraron el 5-ago**; no quedan
  contenedores ni volúmenes suyos. Se vuelven a levantar con `nuevo-cliente.sh <slug> "<Nombre>"`.
- Futuro: **Textil Emilio**. Ojo al techo de RAM (~3 clientes en 4 GB): al sumar
  el 3.º pagando, subir la RAM del VPS.

> **Verificá antes de creer esta lista**: `./scripts/listar-clientes.sh` (o `docker ps`) es la
> fuente de verdad. Este archivo se desactualiza cada vez que se da de alta o de baja a alguien.

### Estado de trabajo (dónde quedamos — sesión 8-ago-2026)

**`main` = `develop` = `c21f3fc`, CI verde en ambas, y los TRES ambientes al día.** Sesión corta,
de mantenimiento: se aplicaron los PRs de Dependabot que estaban abiertos desde el 4-ago.

**1. Bootstrap 5.3.0 → 5.3.8, bootstrap-icons 1.11.0 → 1.13.1, `actions/upload-artifact` v4 → v7.**
Los dos de webjars **no se mergearon con el botón**, a propósito: el PR automático solo toca el
`pom.xml` y la versión va también en la URL, escrita a mano en 6 archivos. Mergeado tal cual =
404 en todo el CSS y el JS, con CI en verde. El procedimiento completo quedó arriba, en
"Actualizar un webjar". Se subió `CACHE_VERSION` de `sw.js` a `texcontrol-v4`.

**2. Se descartó el 4.º PR (#8, `eclipse-temurin` 21 → 22)** y se le puso una regla `ignore` de
**major** en `dependabot.yml`. Cerrarlo a mano no alcanzaba: Dependabot corre semanal y lo reabría.
La regla bloquea SOLO el salto de major — los parches de la línea 21 (21.0.x) siguen llegando.
Al migrar a la próxima LTS hay que quitar la regla **y** subir `<java.version>` en el mismo commit.

**3. `dependabot.yml` ahora apunta a `develop`, no a `main`.** Sin `target-branch` usaba la rama por
defecto del repo, así que una actualización de dependencia entraba directo a la rama que corren los
clientes sin pasar por staging — al revés del flujo de dos ramas. Ojo: Dependabot lee su config
**desde `main`**, así que un cambio ahí no hace efecto hasta promoverlo.

**4. Se corrigieron las referencias al proxy `textil_nginx`** (decomisionado en la migración a
multicliente; hoy es `texcontrol_proxy_nginx`) en `STAGING.md`, `CLAUDE.md` y el encabezado de
`docker-compose.dev.yml`. No era cosmético: la doc mandaba a `docker exec textil_nginx nginx -s
reload` para cambiar la clave de Basic Auth de dev — el `htpasswd` funcionaba y el reload fallaba
con "no such container", dejando a nginx sirviendo **la clave vieja**.

> **Cómo se verificó lo de los webjars**, ya que ni CI ni los 171 tests pueden ver ese fallo: se
> listaron las rutas dentro de los jars resueltos del classpath y se cruzaron contra las URLs que
> pide el código (las 3 distintas resuelven), después prueba visual en `dev.texcontrol.pe`
> —incluidas las tres de `/almacen`, que tienen `<head>` propio y **no** tienen enlace en el menú,
> hay que entrar por URL— y por último, ya con el demo actualizado, `5.3.8` → 200 y `5.3.0` → 404.

### Estado anterior (sesión 7-ago-2026)

**`main` = `develop` = `2762bce`, CI verde, y los TRES ambientes al día.** No quedó nada a medio
promover. Lo de esa sesión, en orden:

**1. Se promovió la tanda del 6-ago** (19 commits) con fast-forward → `50236ca`. Ver la sección
siguiente para el detalle de qué traía.

**2. Kardex generalizado — `V47__kardex_documento_generalizado.sql`.** Es el paso que `ROADMAP.md`
marca como obligatorio ANTES de la primera tabla de hilo de V2 (corrección 2). El detalle técnico
está arriba, en "El kardex no tiene una columna por tipo de documento (V47)". En una línea: el
vínculo con el documento pasó de una columna por tipo al par `(tipo_documento, documento_id)`, se
sumaron `TRANSFORMACION_IN/OUT` + `operacion_id`, y los dos enums pasaron de `ENUM` de MySQL a
VARCHAR. 171 tests (+6 en `KardexDocumentoTest`).

> **Se hizo ahora justamente porque no hay clientes de pago**: `kardex_movimientos` es la tabla más
> grande del sistema, y hoy tenía cero historial real encima. El mismo `ALTER` con datos de
> producción de varios clientes adentro es otra cosa. Si aparece otro cambio estructural de este
> tipo, el mismo razonamiento aplica: **cuanto antes, más barato**.

**3. V47 se verificó contra bases reales, no solo en CI.** Importa el matiz: el `validar-esquema`
de CI arranca con el **esquema vacío**, así que valida el DDL pero **los `UPDATE` del backfill
corren sobre cero filas**. Las dos ramas se ejercitaron a mano:

| Rama del backfill | Dónde | Filas |
|---|---|---|
| `RECEPCION_DETALLE` | `dev` | 5 |
| `TRANSFERENCIA` | `demo` (`db_demo`) | 1 |

Además se hizo una transferencia real en dev (salida → llegada) para probar la consulta reescrita
`findFirstByTipoDocumentoAndDocumentoId...`, que en `confirmarLlegada` saca el peso unitario del
movimiento de salida. **Ese es el punto frágil**: si no encuentra el `OUT` no falla a la vista, el
peso simplemente llega en `0.00`. Salió bien (10 rollos, 229.97 kg, peso por rollo conservado
exacto entre origen y destino). Al tocar esa consulta, probarla así — el test unitario usa mocks.

**4. El `clientes/demo/` volvió al clon de producción** y el demo se actualizó a `2762bce`
(ver "En el VPS hay DOS clones del repo"). Backups tomados antes de migrar: `~/backups-dev/
post-V47.sql` y `backup-cliente.sh demo`.

**Lo único abierto del kardex es una DECISIÓN, no una tarea**: `color_id` sigue `NOT NULL` y el
stock sigue exigiendo rollos. Antes de la primera tabla de hilo hay que definir con el dueño si
`Articulo` se generaliza a "material" (hilo / tela cruda / tela teñida, cada uno con su unidad) o
si cada etapa lleva su propia entidad. No se prejuzgó a propósito — ver `ROADMAP.md`, "Tres cosas
que NO aguantan V2", punto 3.

> **Y el punto de fondo sigue igual** (corrección 0 del `ROADMAP.md`): el cuello de botella no es
> programar, es **tener un cliente operando**. V2 se puede construir sin cliente; sus gates piden
> ciclos productivos reales y no se pueden cerrar sin uno.

#### Verificar una migración en dev o en un cliente

Salió útil esta sesión y conviene tenerlo a mano. La clave sale del `.env`, no se tipea:

```bash
# dev
cd ~/textil-inventario-dev
PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env.dev | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$PW" textil_mysql_dev mysql -uroot textil_inventario -e "..."

# un cliente (demo, o el slug que sea)
cd ~/textil-inventario
PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' clientes/demo/.env | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$PW" db_demo mysql -uroot textil_inventario -e "..."
```

Qué mirar: `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC` (la
tabla es la fuente autoritativa — el perfil `prod` del stack de clientes no imprime las líneas
INFO de Flyway en el log, así que no verlas ahí no significa nada), y después una consulta que
compruebe el *dato*, no solo el esquema. **MySQL no tiene DDL transaccional**: si una migración se
corta a la mitad queda medio aplicada y Flyway la marca fallida, así que **sacar backup antes** de
cualquier migración que borre o transforme columnas.

### Estado anterior (sesión 6-ago-2026)

**✅ PROMOVIDO a `main` el 7-ago-2026.** Los 19 commits de la sesión del 6-ago pasaron la prueba
manual de punta a punta en `dev.texcontrol.pe` (confirmar una recepción y cargar guías de a una
sobre los programas) y se promovieron con fast-forward — `main` y `develop` quedaron en el mismo
commit. 165 tests verdes. No había clientes de pago corriendo, así que la promoción no desplegó a
nadie: deja `main` sano para el próximo cliente que se dé de alta. **El demo (`demo.texcontrol.pe`)
corre `main`, así que para que tome estos cambios hay que reconstruir su stack** (ver `DEMO.md`).

Lo que entró en esa tanda, por si hay que revisar algo puntual:
- **Programas — botones de programa anterior/siguiente** en Seguimiento, con el **número del
  vecino** en el botón y la posición («3 de 17»). Recorren el MISMO orden de la lista y **dentro
  del filtro con el que se llegó** (los parámetros viajan en el link, y «Volver a Programas»
  también los arrastra). Ojo con el defecto del estado: en el detalle es **TODOS**, no PROCESO —
  entrando por un link pelado no hay filtro que respetar. Si el programa cae fuera del filtro, la
  navegación se oculta en vez de mentir sobre la posición.
- **Programas — la lista se filtra por empresa, estado y número** (`programas/lista.html`).
  Chips de empresa (solo si hay más de una), chips de estado con **«En proceso» por defecto**
  —lo completado sigue a un click, con su contador— y buscador por número. Todo viaja en la URL
  (`/programas?empresa=3&estado=completos&q=62`) y **se filtra en la consulta**, no en memoria:
  la lista recorre las líneas de cada programa para pintar la barra de progreso, así que filtrar
  después significaría cargar el historial entero para mostrar tres filas. «Completo» no es una
  columna — la regla de `Programa.isCompleto()` está replicada en SQL en `ProgramaRepository`.
- **Programas — el artículo y el color de una línea ya existente ahora SE PUEDEN cambiar**
  (`programas/editar.html`). Antes solo se editaba la cantidad: un error de tipeo en un
  desplegable obligaba a quitar la línea, cargarla de nuevo y recalcular el total de rollos a
  mano. **Pero solo mientras la línea no haya recibido tela**: si ya tiene recepciones
  vinculadas (`cantidadRecibida > 0` o `existsByProgramaDetalleId`), cambiar lo que pide las
  dejaría acreditadas a un artículo que nunca entró por esa puerta — ahí la línea va de solo
  lectura, con candado, y hay que quitarla y agregar una nueva. El cambio queda auditado
  (`EDITAR_LINEA_PROGRAMA`, con el antes → después).
- **Recepciones — una línea de la guía no se puede perder en silencio**: en *Productos Detectados*
  (`recepciones/nueva.html`), la línea sin artículo o color se caía del POST con un `return` mudo
  y la recepción se creaba con MENOS líneas que el papel (el guard del backend nunca llegaba a
  dispararse porque esa línea sencillamente no se enviaba). Ahora cada línea tiene UNO de dos
  destinos: se registra, o se marca **"No incluir"** a propósito — y esa exclusión queda escrita en
  las observaciones de la recepción (visibles en Detalle y Confirmar) y en el log de auditoría
  (`EXCLUIR_LINEAS`). De paso, `sincronizarLineasDesdeDom()` arregla otro descarte silencioso: cada
  re-dibujo de la tabla (crear color/artículo) borraba las ediciones manuales de las demás filas.
- **OCR**: la fecha de la **guía** se leía con día y mes cambiados, y el **mismo defecto** estaba
  clonado en la de la **factura** → todo pasa por `common/FechaDocumento`. Y la verificación del
  acabado contra el texto literal (ver "El prompt no es una garantía").
- **Recepciones**: se podía confirmar con el POST **incompleto** — las líneas que faltaban no movían
  stock pero la recepción quedaba CONFIRMADA, dejándolas sin arreglo posible. `asignarFactura` no
  exigía misma empresa (su gemela `guardarDocumentoFactura` sí), y el front se **tragaba** el fallo
  del PDF (`fetch` no lanza ante un 400).
- **Inventario**: "stock bajo" estaba definido dos veces y el del Dashboard no se podía cambiar sin
  recompilar → `STOCK_BAJO_UMBRAL`.
- **Archivo histórico**: la vinculación factura↔guía seguía escaneando la tabla entera, una vez por
  factura; V40 había creado el índice justo para eso y solo se había migrado la mitad.
- **Programas**: composición y acabado en Seguimiento, aviso de **tela recibida que no descontó**
  (con el porqué), y botón **Vincular** manual (idempotente, solo ADMIN, auditado, no toca stock).
- **Estructura**: `recepciones` (2943 líneas) se partió en `ocr` / `programas` / `almacen` y los
  validadores a `common`. Cero cambio de lógica.
- **Docs**: `ROADMAP.md` con las correcciones al PDF oficial; este archivo con clientes reales,
  los dos clones del VPS y el `.gitignore` de credenciales.

**✅ Resuelto el 7-ago (VPS)**: `clientes/demo/` se movió del clon de dev al de producción, y el
demo se actualizó a `main` (`50236ca`) con `actualizar-clientes.sh demo`. `listar-clientes.sh`
corrido desde `~/textil-inventario` ya lo lista, así que el demo volvió a quedar bajo los scripts
de actualización, backup y endurecimiento.

**✅ Limpieza del stack decomisionado (6-ago)**: se eliminaron los archivos del modelo
single-cliente (`docker-compose.prod.yml`, `nginx/nginx.conf`, `scripts/deploy.sh`, `backup-db.sh`,
`restore-db.sh`, `migrar-cliente.sh`) y se reescribieron las secciones 2–7 de `DEPLOY.md`, que
todavía describían ese despliegue como el vigente. Se sumaron las dos piezas que faltaban del
modelo multicliente: **`restaurar-cliente.sh`** (no existía contraparte de `backup-cliente.sh`: se
podía respaldar pero no recuperar) y **`verificar-backup.sh`** reescrito — el anterior exigía ≥30
tablas cuando el esquema real tiene 26, así que habría fallado siempre, y nadie lo notó porque
apuntaba al contenedor decomisionado.

### Estado anterior (sesión 24-jul-2026)

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
exec texcontrol_proxy_nginx nginx -s reload` — **corregido el 8-ago**: acá decía `textil_nginx`, que es el
proxy single-cliente ya decomisionado, así que el reload fallaba y nginx seguía sirviendo la clave vieja).
**Flujo de trabajo nuevo**: pushear a `develop` → en el VPS
`cd ~/textil-inventario && ./scripts/deploy-dev.sh` → probar en `dev.texcontrol.pe` → cuando anda, promover
`develop → main` + actualizar los clientes. Se acabó levantar MySQL/app en local.

**SSH desde casa**: el alias `ssh texcontrol` de la PC de casa apuntaba a la vieja IP de Tailscale (ya
removido) → daba timeout. Corregido a la IP pública `64.176.3.149` en `~/.ssh/config` (clave
`~/.ssh/texcontrol_vps`, ya autorizada). Recordatorio: ese alias vive en cada PC, no dentro del VPS.

**Promovido a `main` el 24-jul** (ya en `origin/main`; en su momento faltaba desplegarlo en el VPS):
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

> ~~Para probar flujos con datos reales en dev: copiar prod → dev con `mysqldump` de
> `textil_mysql`~~. **Obsoleto (6-ago)**: `textil_mysql` era el stack single-cliente, ya
> decomisionado, y hoy **no hay ningún cliente de pago del cual copiar**. Dev ya tiene datos
> propios; para dejarlo en foja cero conservando programas y catálogo está
> `scripts/limpiar-dev-dejando-programas.sh`, y para sembrar el demo desde dev,
> `scripts/sembrar-demo-desde-dev.sh` (la dirección quedó al revés de lo que decía esta nota).

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

### ⚠️ En el VPS hay DOS clones del repo

`scripts/deploy-dev.sh` usa un **clon separado** para staging, así que en el servidor conviven:

| Directorio | Rama | Para qué | Tiene |
|---|---|---|---|
| `~/textil-inventario` | **`main`** | Producción: build de la imagen y `actualizar-clientes.sh` | `.env`, `clientes/` (**demo** y los clientes de pago) |
| `~/textil-inventario-dev` | **`develop`** | Lo que corre en `dev.texcontrol.pe` | `.env.dev` |

Es a propósito: desplegar dev no obliga a cambiar de rama en el clon de producción.

**El riesgo a no repetir**: `actualizar-clientes.sh` reconstruye la imagen **desde el clon donde se
lo corra**. Corrido desde `~/textil-inventario-dev` le metería código de `develop` a un cliente que
debe correr `main`. El script avisa si la rama no es `main`, pero es un aviso, no un freno. Los
`clientes/*/.env` van **siempre en el clon de producción**; si aparecen en el de dev,
`listar-clientes.sh` y `actualizar-clientes.sh` corridos desde producción dicen "no hay clientes"
aunque los contenedores estén corriendo (le pasó al demo hasta el 7-ago-2026).

> Ojo también: `deploy-dev.sh` hace `git reset --hard origin/develop` sobre el clon de dev.
> Lo versionado que edites a mano ahí se pierde en el siguiente despliegue.

Falta, por orden de prioridad:

0. **Dar de alta un cliente que opere de verdad** (corrección 0 del `ROADMAP.md`). Está primero a
   propósito: no es una tarea de programación, pero es la que destraba V2 — sus gates piden ciclos
   productivos reales y hoy hay cero clientes de pago. Próximo candidato: **Textil Emilio**.
0.b **Decidir el modelo de material** (`Articulo` generalizado vs. una entidad por etapa, con
   `color_id` NULL-able y unidad de medida). Es lo único que bloquea la primera tabla de hilo de
   V2 ahora que V47 cerró el kardex. Ver `ROADMAP.md`, "Tres cosas que NO aguantan V2", punto 3.
1. **App móvil iOS/Android**: una app para celular para que **los usuarios ingresen desde el móvil**
   (los vendedores/almaceneros/gerentes de cada empresa). A definir: nativa contra una API REST
   (que hay que construir), PWA instalable sobre la web actual, o wrapper WebView. Pedido explícito
   del cliente. (Ya hay PWA instalable + sesión persistente móvil implementada.)
2. **Al sumar el 3.er cliente (Emilio) pagando**: subir la RAM del VPS (techo ~3 clientes en 4 GB) o
   evaluar MySQL compartido. Crear cada cliente con `NOMBRE_EMPRESA` propio (ya lo hace el script).
3. **Marketing** en `texcontrol.pe` (hoy la raíz redirige a `login.`).
4. **Módulo de Ventas** (rol `VENDEDOR`, hoy sin permisos).
