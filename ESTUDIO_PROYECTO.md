# Estudio integral de TexControl (textil-inventario)

> Notas de preparación para cerrar la auditoría **Auditoría Final Integral (2026-08-02)**.
> Estudiadas a mano las 5 áreas (inventario, seguridad, migraciones, DevOps, frontend) sobre la
> rama **`develop`** (37 migraciones, V1–V37). Cada hallazgo lleva archivo:línea para ir directo.
> Fecha del estudio: 2026-08-03.

---

## 0. Veredicto rápido sobre la auditoría

La auditoría es **seria y mayormente correcta** (nota 6.4/10, "no liberable a clientes nuevos sin
cerrar P0"). Verifiqué los exploits P0 uno por uno: **los de inventario son reales y siguen vivos**.

**PERO el informe se hizo sobre un snapshot distinto** y algunas citas no calzan con este repo:
- Cita **V39 y "41 migraciones"** → en `develop` hay **hasta V37** (37 migraciones).
- Cita clases que **NO existen**: `CspNonceFilter`, `GestorSesiones`, `ZipSeguroExtractor`,
  `UsuarioService`. En este repo: no hay filtro CSP (de hecho **no hay CSP en absoluto**), la
  extracción ZIP está inline en `ArchivoHistoricoService`, y la lógica de usuarios vive toda en
  `UsuarioController`.
- Menciona `alwaysRemember`/30 días → **sí es cierto** (verificado).

**Conclusión**: tratar cada cita de archivo/versión del PDF con pinza y confirmar contra el código
(como hice acá) antes de tocar. Los hallazgos de fondo, sin embargo, son válidos.

---

## 1. P0 — Confirmados en el código (los que importan mañana)

### INV-01 · Cantidades negativas fabrican stock — **REAL, EXPLOTABLE** 🔴
`transferencias/TransferenciaService.java` — `confirmarLlegada` (L148–247).
- L167: `totalRepartido = suma de todas las cantidades del reparto` (los negativos **sí** suman).
- L173: tope `if (totalRepartido > despachado) throw` → un reparto `+100/-90` suma `10`, pasa el
  tope contra una salida de 10.
- L198: en el loop, la entrada `-90` se descarta con `continue` (`cantidad <= 0`), pero la `+100`
  **sí** se agrega al stock destino (L221). Resultado: **+90 rollos fabricados**.
- El controller también filtra `<= 0` (`TransferenciaController.java:130`), así que el vector real
  es un POST manipulado directo al service/endpoint.
- **Fix**: rechazar `null`/negativos **antes** de sumar (lanzar excepción, no `continue`), usar
  `Math.addExact`, validar máximo, y test de regresión `+100/-90` que debe fallar sin mover stock.

### INV-02 · `numero_guia` sin UNIQUE — **REAL** 🔴
- Entidad `Recepcion.java:24`: `@Column(name="numero_guia", nullable=false, length=50)` **sin `unique=true`**.
- BD: ninguna migración crea `UNIQUE(numero_guia)`.
- Chequeo actual es aplicativo (read-then-write) en `RecepcionService.crearRecepcion` (L156–160,
  `findFirstByNumeroGuia`) → dos requests simultáneos pasan ambos el `findFirst` e insertan duplicado.
- **Fix**: limpiar duplicados existentes → migración `UNIQUE(numero_guia)` → capturar
  `DataIntegrityViolationException` en el service → conservar la validación previa solo para UX.

### INV-03 · `ProgramaDetalle` pierde updates (last-write-wins) — **REAL** 🔴
- `recepciones/ProgramaDetalle.java`: **sin `@Version` ni lock**. `cantidadRecibida` (L35–36).
- `RecepcionService.confirmarRecepcion` L303–307: `pd.setCantidadRecibida(pd.getCantidadRecibida()+rollos)`
  es read-modify-write sin protección → dos recepciones del mismo programa en paralelo pisan el avance.
  (El guard de idempotencia en L243 protege el re-envío de la *misma* recepción, no dos distintas.)
- **Fix**: `@Version` + migración de columna `version`, **o** cargar `ProgramaDetalle` con
  `PESSIMISTIC_WRITE`. Test concurrente que verifique suma exacta.

### DB-01 · V34 borra todos los datos operativos — **EXISTE** 🔴
- `V34__limpiar_datos_semilla.sql`: `DELETE` masivo (catálogo/inventario/movimientos) con
  `FOREIGN_KEY_CHECKS=0`. Conserva solo `usuarios` y `roles`.
- Diseñada para correr **una vez en BD nueva**; el peligro real es un **upgrade sobre una base ya
  poblada** que no la tenía aplicada, o un rebase/re-baseline que la re-dispare.
- Agravante: `application.yml` tiene `baseline-on-migrate: true` → sobre BD legada Flyway puede
  auto-baselinear. Combinación peligrosa.
- **Fix**: sacar el "vaciar semilla" del flujo de migración universal (aprovisionamiento aparte);
  congelar upgrades no ensayados; backup verificado + restore drill antes de cualquier migración.

### DB-02 · V21 puede intercambiar roles — **EXISTE (riesgo condicional)** 🔴
- `V21__roles_gerente_supervisor.sql` (L5–24): renumera PKs de `roles` con `FOREIGN_KEY_CHECKS=0`
  (ALMACENERO→100→3/SUPERVISOR, VENDEDOR→101→4, GERENTE→2).
- **NO** actualiza `usuarios.rol_id`. Con FK checks off, mover `roles.id` no propaga: un usuario que
  apuntaba a ALMACENERO queda con `rol_id` inconsistente. Seguro **solo** porque en la práctica el
  único usuario con rol asignado era `admin` (rol_id=1, no tocado).
- **Fix**: auditar roles reales en cada base antes de upgrade; migración correctiva si hay usuarios
  con rol 2/3/4 previos a V21; nunca reciclar PK de catálogos referenciados.

### SEC-01 · Credenciales conocidas sembradas por Flyway — **REAL** 🔴
- `V33__reset_passwords_arranque.sql` L12–14 fija hashes de contraseñas **documentadas en el propio
  comentario**: `admin`→`superadmin`, `dueno`→`duenocliente`, `adminprueba`→`admin`.
- `V35` renombra `admin`→`jlynch` **conservando** `superadmin` → **jlynch/superadmin es credencial
  pública del repo** en toda copia recién creada.
- **No hay forzado de cambio de contraseña**: no existe flag `debeCambiar` en `Usuario.java` ni
  lógica en login; la "rotación obligatoria" es solo un comentario. `endurecer-cliente.sh` rota
  jlynch y borra cuentas de prueba, pero **corre DESPUÉS de publicar** el cliente (ver OPS-01).
- Mitigación parcial: cuentas de prueba quedan `activo=FALSE` y `UsuarioDetailsService` rechaza
  inactivos (L21–23).
- **Fix**: bootstrap fuera de Flyway con secreto aleatorio por instancia y expiración de primer uso;
  rotar todas las instalaciones ya creadas.

### OPS-01 · Cliente se publica antes de endurecer — **REAL** 🔴
- `scripts/nuevo-cliente.sh`: levanta el stack → genera nginx → **recarga el proxy (L73, ya
  accesible en `<slug>.texcontrol.pe`)** → **recién luego** llama a `endurecer-cliente.sh` (L94).
- Ventana pública con `jlynch/superadmin` (hash común) + cuentas de prueba hasta que termina el
  endurecimiento.
- **Fix**: endurecer en red interna **antes** de publicar en nginx; bloquear login durante provisioning.

### OPS-02 · Rollout masivo sin canario ni backup — **REAL** 🔴
- Nota: **no existe `actualizar-clientes.sh`** como script; la actualización masiva es un bucle
  documentado a mano en `DEPLOY.md:232–240` (`git pull` + `docker build` + `for e in clientes/*/.env; up -d`).
- `scripts/deploy.sh` reconstruye la imagen (`up -d --build`, L65) y arranca la app → **Flyway migra
  automáticamente** sin dump previo, sin staging, sin canario, sin smoke test, sin rollback.
- **Fix**: backup verificado + restore en staging + canario + validación de integridad + rollout por
  lotes con detención automática.

### BUILD-01 · Sin Maven Wrapper — **REAL** 🔴
- No existen `mvnw`, `mvnw.cmd` ni `.mvn/wrapper/`. Build depende del Maven del entorno
  (CI usa `setup-java`; Dockerfile usa imagen `maven:3.9`, tag mutable).
- **Fix**: agregar Maven Wrapper + Enforcer, usar `./mvnw clean verify` en CI y Docker.

### AUD-01 · El cliente no ve acciones del SUPERADMIN — **REAL (por diseño)** 🔴
- `LogEventoRepository.buscarConFiltros` (L28–29) oculta eventos de rol SUPERADMIN y `esPrueba`
  cuando `ocultarSuperadmin=TRUE` (`LogEventoController.java:42–46`, activo para no-superadmin).
- **Fix (según auditoría)**: mostrar actividad de soporte al cliente, exigir ticket/motivo, acceso
  temporal con MFA. (Decisión de negocio — hablar con el dueño; el propio Anexo B advierte "no vender
  la existencia de SUPERADMIN invisible como función de soporte".)

---

## 2. Mapa por módulo

### Inventario (recepciones / transferencias / inventario)
- **Concurrencia sobre stock**: bien resuelto. `StockActual` tiene `@Version` (L33–34) **y** su finder
  `findByArticuloIdAndUbicacionIdAndColorId` usa `@Lock(PESSIMISTIC_WRITE)`
  (`StockActualRepository.java:17–18`). UNIQUE `(articulo_id, ubicacion_id, color_id)`.
- **Idempotencia**: por guards de estado (`RecepcionService:243`, `TransferenciaService:81` y `:156`),
  no por constraints de BD.
- **Kardex**: append-only *de facto* (solo `save(new ...)`, nunca update/delete), pero el repo hereda
  `JpaRepository` completo (update/delete disponibles a código futuro → KDX-01). No guarda saldos
  antes/después ni `operationId` (KDX-02). Campo `recepcionDetalleId` existe pero **no se setea** en
  la recepción.
- **Validación de cantidades — asimétrica**: negativo bloqueado con **excepción** en recepción-confirmar
  (`RecepcionService:265`, acepta 0) y en transferencia-salida (`TransferenciaService:94`); en
  transferencia-llegada el `<=0` se **omite con `continue`** (L198) — esa asimetría es justo lo que
  habilita INV-01.
- **Almacén rápido** (`AlmaceneroController`, entradas/salidas rápidas): **no validan cantidad**
  (acepta null/negativos, FAST-01) y **no tocan stock/kardex** (solo registro fotográfico
  PENDIENTE→REVISADO). La conciliación en la cola de revisión permite vincular a cualquier
  recepción/transferencia sin comparar (FAST-02).

### Seguridad (config / seguridad / auditoria)
- `SecurityConfig`: **no hay `RoleHierarchy`** — la jerarquía se enumera a mano en cada regla; un
  olvido no se cubre por herencia. Orden de reglas load-bearing (autoservicio antes de `/usuarios/**`;
  `/reportes/errones` SUPERADMIN antes del `anyRequest`).
- **CSRF**: habilitado (default de Spring, no se toca). Logout forzado a **GET** (L140).
- **Remember-me**: `.alwaysRemember(true)` (cookie persistente en **todo** login) + **30 días**
  (L135–136), igual para ADMIN/SUPERADMIN (AUTH-02). La `remember-me-key` sí es obligatoria por env
  (arranque falla si falta).
- **NO hay CSP ni cabeceras de seguridad custom** (`SecurityConfig` sin `.headers(...)`): solo los
  defaults de Spring (X-Frame DENY, X-Content-Type-Options). Contradice la premisa de la auditoría.
- `UsuarioController`: `@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")` en todas las mutaciones
  (defensa en profundidad). Oculta cuentas SUPERADMIN/prueba al ADMIN (`esOculto`, `bloqueadoPorOculto`
  responde "no encontrado" para evitar enumeración). Password mínimo **6** (SEC-02: débil).
- **Auditoría no es REQUIRES_NEW**: `AuditLogService` se une a la tx del caller → **se pierde en
  rollback** (AUD-02); el `try/catch` solo cubre el `save`, no el flush diferido al commit.

### Base de datos / Flyway
- `ddl-auto: validate`, `baseline-on-migrate: true`, OSIV en default (**true**, no configurado),
  Thymeleaf `cache: false` **global** (también en prod → CFG-01).
- **Migraciones frágiles que asumen tablas vacías** (sin backfill, `ADD COLUMN NOT NULL` sin default):
  V17, V26, V27. Correctas solo porque las tablas estaban vacías al migrar.
- **Sin ningún CHECK numérico** en toda la BD (stock, recepciones, transferencias, programas,
  rápidas): la no-negatividad vive solo en Java (DATA-02). Único CHECK real: `chk_transf_estado` (V3).
- UNIQUE de negocio presentes: `transferencias.numero`, `programas.numero`, `stock_actual.uq_stock`,
  `usuarios.username`, `empresas.ruc`, etc. **Falta**: `recepciones.numero_guia`.

### DevOps / infra
- **CI** (`.github/workflows/ci.yml`): solo `mvn clean compile` + `mvn test`. **No** hace `package`,
  **no** construye ni publica imagen. **El VPS reempaqueta con `-DskipTests`** (`Dockerfile:13`) → el
  artefacto probado ≠ el desplegado (CI-01). Sin escaneo CVE/SBOM (solo Dependabot reactivo).
- **Docker**: multi-stage ✓, usuario no-root ✓. **Sin `HEALTHCHECK`** de la app (solo MySQL ping);
  se espera por línea de log `Started ... in ...` (DEVOPS-02). Tags **mutables sin digest**
  (`texcontrol-app:latest`, `adminer:latest`, bases sin `@sha256`).
- **Nginx**: **sin rate limiting, sin HSTS/CSP/X-Frame, sin `ssl_protocols`, sin `server_tokens off`**
  en ninguna config (single ni multicliente). Solo `client_max_body_size 30m` + timeouts OCR.
- **Scripts**: `set -euo pipefail` en todos; manejo de contraseñas cuidadoso (`MYSQL_PWD` no `-p`,
  `.env` a 600). `eliminar-cliente.sh` hace `down -v` + `rm -rf` **sin backup automático** (OPS-05).
  `migrar-cliente.sh` respeta el orden Flyway correcto (restore antes de arrancar) — bien hecho.

### Frontend / reportes / OCR / archivo histórico
- **Reportes**: patrón `findAll` + filtro en memoria, **sin paginación ni DTOs** (PERF-01). El kardex
  se materializa entero por cada visita.
- **Excel**: usa **`XSSFWorkbook` (DOM en memoria), NO streaming** — el CLAUDE.md/auditoría dicen
  SXSSF pero el código real es XSSF (`ExcelExportService.java:15`) → **peor de lo reportado**, sin
  límite de filas → riesgo OOM. (Positivo: `sanitizarCeldaTexto` neutraliza inyección de fórmulas.)
- **OCR** (`AnthropicOcrService`): carga PDF completo + Base64 en memoria (OCR-01). Timeouts sí
  (connect 30s / read 90s). **Los endpoints interactivos NO son @Async** — corren en el hilo del
  servlet hasta 90s (contradice el comentario). Solo Archivo Histórico usa `@Async` con pool acotado.
- **ZIP histórico**: límites zip-bomb (1000 entradas / 200MB) ✓, zip-slip neutralizado por
  `getFileName()` ✓, pero **valida solo extensión `.pdf`, no firma mágica** (FILE-03). `readAllBytes`
  por entrada antes de acumular → una entrada gigante se materializa antes del chequeo total.
- **Fotos** (`DocumentoStorageService`): **solo lista blanca de extensión**, sin firma/MIME/dimensiones
  (FILE-01). Nombre en disco server-side (sin traversal). Se sirven con content-type adivinado por
  extensión, sin `nosniff`.
- **Descargas**: rutas desde BD (sin path traversal explotable por request), pero **sin control
  por-documento** (cualquier autorizado baja cualquier id).
- **DOM-XSS** en `recepciones/nueva.html` (L200–246): datos de OCR de PDFs de terceros inyectados en
  `innerHTML` **sin escapar**, agravado por ausencia total de CSP (WEB-02 + el hallazgo más notable).
- JS duplicado programas nuevo/editar (7 funciones idénticas). 96/102 `<label>` sin `for` (A11Y-01).
  25 handlers inline en 16 plantillas.

---

## 3. Plan de ataque sugerido para mañana

**Bloque 1 — P0 de inventario (código, bajo riesgo de tocar, alto valor):**
1. INV-01: validar reparto en `TransferenciaService.confirmarLlegada` (rechazar null/negativos antes
   de sumar) + test `+100/-90`.
2. INV-02: migración `UNIQUE(numero_guia)` (V38) + limpiar duplicados + capturar
   `DataIntegrityViolationException`.
3. INV-03: `@Version` en `ProgramaDetalle` + migración de columna + test concurrente.
4. FAST-01: validar cantidades en entradas/salidas rápidas (DTO `@Min`, y quizá CHECK SQL).

**Bloque 2 — P0 de datos/operación (requiere decisiones con el dueño):**
5. DB-01/DB-02: estrategia de aprovisionamiento separada de Flyway; congelar upgrades no ensayados.
6. SEC-01/OPS-01: bootstrap con secreto aleatorio; endurecer antes de publicar.
7. AUD-01: decisión de negocio sobre visibilidad del soporte.
8. BUILD-01: agregar Maven Wrapper (rápido, desbloquea CI reproducible).

**Bloque 3 — P1 (30–60 días):** InventoryEngine único, kardex append-only real con operationId,
validación de archivos por contenido, reportes paginados, pruebas MVC por rol, CI que produzca la
imagen desplegada, backups cifrados fuera del VPS, CSP estricta.

---

## 4. Discrepancias auditoría ↔ repo (para no perder tiempo mañana)

| La auditoría dice | En `develop` realmente |
|---|---|
| 41 migraciones, cita V39 | 37 migraciones (V1–V37) |
| `CspNonceFilter` + nonce por request | No existe; **no hay CSP en absoluto** |
| `GestorSesiones`, revocación de sesiones | No existe; sesiones en default de Spring |
| `ZipSeguroExtractor` (clase) | Lógica inline en `ArchivoHistoricoService` |
| `UsuarioService` | Todo en `UsuarioController` |
| Excel con `SXSSFWorkbook` (streaming) | `XSSFWorkbook` (DOM en memoria) — peor |
| `actualizar-clientes.sh` (script) | No existe; bucle manual en `DEPLOY.md` |
| OCR corre en `@Async` | Interactivo corre en hilo del servlet |

Los hallazgos de **fondo** siguen siendo válidos; solo las **citas exactas** están desalineadas.
