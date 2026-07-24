# Auditoría técnica completa — TexControl

- **Rama auditada:** `main` (commit `a471205`)
- **Fecha:** 2026-07-24
- **Alcance:** backend (Java 21 / Spring Boot 3.3.5), frontend (46 plantillas Thymeleaf + JS), base de datos / migraciones (Flyway V1–V36), infraestructura (Docker, scripts, CI) y documentación.
- **Método:** 5 auditorías paralelas especializadas (seguridad, lógica backend, frontend, BD/infra/CI, documentación). Cada hallazgo fue verificado contra el código real; se excluyeron falsos positivos.

---

## 1. Resumen ejecutivo

La base está **sólida en los fundamentos**: sin inyección SQL (queries parametrizadas), sin XSS server-side (`th:text`, cero `th:utext`), zip-slip y path-traversal mitigados, sanitización de fórmulas Excel, aislamiento SUPERADMIN correcto (sin escalada de rol), esquema Flyway alineado con las entidades, y se respetan las convenciones del cliente (sin placeholders).

Los riesgos reales se concentran en **cuatro focos**:

1. **Idempotencia de los movimientos de stock (CRÍTICO).** Confirmar recepción / salida / llegada de transferencia no verifica el estado previo → un doble-click o reenvío **duplica stock** o **crea rollos de la nada**. Es el hallazgo más importante.
2. **Credenciales/secretos reproducibles (ALTO).** La contraseña de `jlynch` (SUPERADMIN) es `superadmin`, hardcodeada en migración e igual en toda copia; y la clave `remember-me` nunca llega al contenedor en prod → se firma con el default público del repo.
3. **XSS por `innerHTML` con datos del OCR (ALTO).** Datos leídos de PDFs (no confiables) se inyectan sin escapar en la pantalla de recepción.
4. **Deuda de robustez/defensa en profundidad (MEDIO).** `@PreAuthorize` faltante en borrados de servicio (contradice la doc), CI que no detecta drift entidad↔esquema, migraciones editadas post-commit, y varios scripts frágiles.

**Conteo de hallazgos:** 4 CRÍTICOS · 6 ALTOS · 13 MEDIOS · 18 BAJOS.

> Nota de contexto: el modelo es **single-tenant** (una BD/instancia por cliente), lo que neutraliza los IDOR entre clientes. Varios riesgos son *reproducibilidad entre copias*, críticos justo ahora que se prepara la venta multi-instancia.

---

## 2. CRÍTICOS

### C1 · `confirmarRecepcion` no es idempotente → doble confirmación duplica stock
`recepciones/RecepcionService.java:224-303` · POST `recepciones/RecepcionController.java:114`
No se valida el estado antes de aplicar el movimiento; el estado `CONFIRMADA`/`CON_DIFERENCIAS` se fija al final. Un doble-click / reenvío / reintento de POST corre el método dos veces → `stock.rollos` sube dos veces, se duplican los movimientos de kardex INGRESO y `ProgramaDetalle.cantidadRecibida`. Inventario inflado sin error.
**Fix:** exigir `estado == PENDIENTE` al entrar (lanzar `IllegalStateException` si no); idealmente `@Version` optimista sobre la Recepción.

### C2 · `confirmarSalida` no verifica estado → descuenta stock dos veces
`transferencias/TransferenciaService.java:73-133` · POST `TransferenciaController.java:84`
Mismo patrón: descuenta origen y crea kardex OUT antes de fijar `CONFIRMADA_SALIDA`. Reenvío del form → descuento doble, posible stock negativo.
**Fix:** exigir `estado == BORRADOR` al entrar.

### C3 · `confirmarLlegada` puede correr sin salida y/o dos veces → crea stock de la nada
`transferencias/TransferenciaService.java:139-218`
No valida `estado == CONFIRMADA_SALIDA`. Suma a destino sin haber descontado de origen (rollos de la nada, con `pesoUnitario = 0`), o duplica al reenviar.
**Fix:** exigir `estado == CONFIRMADA_SALIDA` al entrar.

### C4 · `confirmarLlegada` no valida que lo repartido ≤ lo despachado
`transferencias/TransferenciaService.java:145-208`
`totalRepartido` solo marca `tieneDiferencias`; el bucle suma **todo** lo repartido al destino. Repartir 12 sobre una salida de 10 → +2 rollos creados, la transferencia solo queda etiquetada `CON_DIFERENCIA`.
**Fix:** validar `totalRepartido <= cantidadConfirmadaSalida` por detalle y rechazar/topar. El servicio además acepta cualquier `ubicacionId` (la UI filtra Praderas, el servicio no).

---

## 3. ALTOS

### A1 · Contraseña SUPERADMIN por defecto, hardcodeada y reproducible
`db/migration/V33__reset_passwords_arranque.sql:12` · `V35__usuarios_prueba_y_username.sql:16`
El hash sembrado corresponde (verificado con bcrypt) a la contraseña **`superadmin`** para el usuario **`jlynch`** (SUPERADMIN, máximo privilegio, oculto al cliente → nunca se rota). La app es pública; cualquiera con el repo o una copia entregada tiene acceso total.
**Fix:** no versionar hashes reales; forzar cambio en primer arranco (`must_change_password`) o clave por instancia vía provisión. **Rotar YA en `textillaura`.** Neutralizar también `*prueba`/`dueno` antes del primer cliente que pague.

### A2 · Clave `remember-me` con default público **y** no inyectada en prod
`SecurityConfig.java:32-34,124` · `application.yml:79` · `docker-compose.prod.yml:18-29` · `multicliente/docker-compose.cliente.yml:54-67` · `scripts/lib-cliente.sh`
Doble falla: (a) el default `texcontrol-remember-me-dev` es público; (b) los compose de prod y multicliente **no pasan `REMEMBER_ME_KEY`** al contenedor y `mc_generar_env` no la genera → aunque el operador la ponga en `.env`, **nunca llega a la app**. Con `alwaysRemember(true)` y 30 días de validez, todas las instancias firman con la misma clave pública. El control documentado ("clave única por instancia") es **no funcional**.
**Fix:** exigir `REMEMBER_ME_KEY` sin default (fallar arranque si falta en prod, como `DB_PASSWORD`); añadirla al `environment` de `app` en ambos compose; generarla en `mc_generar_env` (`openssl rand -hex 32`).

### A3 · XSS por `innerHTML` con datos del OCR (guía)
`recepciones/nueva.html:156,159,168,170,196-197,230-237`
Valores leídos del PDF por IA (`empresaNombreDetectado`, `advertencia`, `programaTenido`, `colorCodigo`, `motivoNoMatch`, `tipoTela`, etc.) se inyectan en `<small>/<div>/<td>` vía `innerHTML` sin escapar. Una guía con `<img src=x onerror=...>` ejecuta JS en la sesión del ADMIN que sube el PDF (destinos que sí renderizan, no `<option>`).
**Fix:** construir con `createElement` + `textContent`/`setAttribute`, o escapar cada valor (`escapeHtml`). Nunca concatenar datos de OCR en `innerHTML`.

### A4 · Mismo XSS en la pantalla de facturación
`recepciones/facturar.html:98,131,134,139`
`data.error`, `guiasRef.join(', ')`, `data.advertencia` (derivados del PDF de factura) en `estadoDiv.innerHTML`. Menor superficie, mismo riesgo.
**Fix:** idem A3.

### A5 · `generarNumero` con `count()+1` genera números duplicados (viola UNIQUE)
`transferencias/TransferenciaService.java:56-59` · `Transferencia.java:19`
`numero` es UNIQUE. Borrar una transferencia BORRADOR intermedia (permitido) baja el `count` → el siguiente alta reusa un `TRF-NNNNNN` existente → `DataIntegrityViolationException` y no se puede crear ninguna. Dos altas concurrentes → mismo número.
**Fix:** derivar de `MAX(numero)`, secuencia dedicada o columna autoincremental; nunca de `count()`.

### A6 · Carrera al insertar una fila de stock nueva (el lock pesimista no cubre el INSERT)
`recepciones/RecepcionService.java:261-275` · `transferencias/TransferenciaService.java:180-194` · `inventario/StockActualRepository.java:17-18`
`@Lock(PESSIMISTIC_WRITE)` sobre un `findBy...` que no encuentra fila no bloquea nada. Dos confirmaciones concurrentes del primer stock de un artículo+color+ubicación → ambas ven vacío, una gana y la otra revienta con violación del UNIQUE (V27), haciendo rollback de toda la confirmación. No hay corrupción silenciosa (gracias al UNIQUE) pero sí fallo de concurrencia no manejado en el flujo crítico.
**Fix:** manejar el conflicto (retry / upsert / pre-crear la fila con lock).

---

## 4. MEDIOS

### Backend
- **M1 · Confirmaciones no verifican pertenencia del detalle** — `RecepcionService.java:232-233`, `TransferenciaService.java:78-79`: `findById(detalleId).orElseThrow()` sin comprobar que el detalle pertenece a la entidad padre. Un POST manipulado con IDs de otra recepción/transferencia afecta stock ajeno. Solo ADMIN, pero es brecha de integridad. **Fix:** validar pertenencia.
- **M2 · `actualizarPrograma` permite `cantidadSolicitada < cantidadRecibida`** — `ProgramaService.java:170-174`: deja la línea incoherente (recibido > pedido). **Fix:** validar `solicitada >= recibida`.
- **M3 · Peso de referencia en la llegada no discrimina por color** — `TransferenciaService.java:158-164`, `KardexMovimientoRepository.java:17`: dos líneas del mismo artículo con distinto color usan el peso de la primera salida → `peso_kg` mal en una línea. **Fix:** incluir `colorId` en la consulta.
- **M4 · Recepción automática no idempotente ante fallo parcial** — `ArchivoHistoricoService.java:258-303`: si `confirmarRecepcion` falla tras crear la recepción (que commitea aparte), queda una Recepción PENDIENTE huérfana y el doc se marca PROCESADO. **Fix:** crear+confirmar en una sola transacción, o registrar `recepcionCreadaId` al crear y reconciliar.
- **M5 · `subirZip` escribe a disco fuera de transacción** — `ArchivoHistoricoService.java:68-125`: fallo a mitad del ZIP deja PDFs sin registro. **Fix:** aceptar el diseño explícitamente o reconciliar huérfanos.

### Seguridad / defensa en profundidad
- **M6 · `@PreAuthorize` faltante en borrados de servicio y POST de escritura** — servicios `CatalogoService.java:213,215,217`, `RecepcionService.java:320`, `TransferenciaService.java:222`, `ProgramaService.java:50`; POST `RecepcionController.java:114`, `TransferenciaController.java:84,111`, y varios de `CatalogoController`. La doc promete `@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")` en escritura/borrado; hoy dependen **solo** del orden de reglas de URL (`anyRequest`). No explotable con la config actual, pero sin la red de seguridad documentada. **Fix:** anotar los métodos.
- **M7 · Sin protección de fuerza bruta en el login HTTP** — `SecurityConfig.java:109-114`: `fail2ban` cubre SSH, no `/login`. App pública + credencial por defecto conocida. **Fix:** rate-limit por IP/usuario (nginx o lockout tras N fallos).

### Frontend
- **M8 · Botón "Descargar .zip" sin token CSRF** — `documentos/lista.html:116-132`: arma un form POST dinámico sin `_csrf` → 403 (función rota). **Fix:** añadir el hidden desde `meta[name="_csrf"]`, como hace `recepciones/nueva.html`.
- **M9 · `<option>` por concatenación de strings** — `programas/nuevo.html:263,271,279,287`, `programas/editar.html:201-225`, `recepciones/nueva.html:191`: frágil ante `<`, `&`, comillas. **Fix:** `new Option(texto, valor)`.
- **M10 · Doble-submit en los formularios grandes** — el blindaje anti-doble-click solo se aplicó a los modales "crear al vuelo", no al submit real (`programas/nuevo.html:112`, `transferencias/nueva.html:26`, confirmaciones). Refuerza los CRÍTICOS de idempotencia por el lado del cliente. **Fix:** deshabilitar el botón tras validar en el `submit`.

### BD / Infra / CI
- **M11 · `source` del `.env` rompe con nombres con espacios/`&`** — `scripts/lib-cliente.sh` escribe `NOMBRE_EMPRESA=$nombre` sin comillas; `backup-cliente.sh:44` y `migrar-cliente.sh` hacen `source` → el `&` de "Laura & Clemente" aborta bajo `set -e`. **El backup por cron falla en silencio** para casi todos los clientes. **Fix:** leer con `grep|cut` (como `deploy.sh`) o entrecomillar al escribir.
- **M12 · Migraciones V26/V27 editadas post-commit** — `git log` muestra un segundo commit que cambió el SQL (índices temporales). Viola "nunca editar una migración aplicada"; con `validateOnMigrate` cualquier BD con el checksum viejo no arranca. **Fix:** verificar checksums en cada entorno; `flyway repair` si aplica; a futuro, siempre migración nueva.
- **M13 · CI no detecta drift entidad↔esquema** — los 6 tests son Mockito puro; `ci.yml` nunca bootea Spring ni corre Flyway `validate` contra MySQL. Un mismatch pasa CI en verde y revienta en el deploy. **Fix:** job `@SpringBootTest` con Testcontainers MySQL (o smoke con MySQL de servicio).
- **M14 · Spring Boot 3.3.5 detrás de parches** — `pom.xml:10`. La serie 3.3.x tiene parches posteriores (Tomcat/Spring Framework). *Positivo:* CVE-2025-22228 (BCrypt >72 bytes) ya mitigada por el tope de 72 en `UsuarioController.java:256`. **Fix:** subir al último 3.3.x (probar en `develop`).

---

## 5. BAJOS

### Backend
- **B1 · Múltiples `orElseThrow()` sin mensaje** → 500 opaco (RecepcionService, TransferenciaService, ProgramaService, CatalogoService). Usar `orElseThrow(() -> new IllegalArgumentException("... id="+id))`.
- **B2 · Colisión de slug de carpeta entre empresas** — `CatalogoService.java:34-55`: "TEXTIL LAURA" y "Textil Láura" → mismo `textil-laura` → mezclan documentos. Garantizar unicidad (sufijo por id/RUC).
- **B3 · `procesarPendientesAsync` sin backoff ni tope de reintentos** — `ArchivoHistoricoService.java:131-141`: si el `save` de estado final falla, aborta todo el `@Async`.
- **B4 · POST de confirmación sin `@PreAuthorize`** (defensa en profundidad) — ver M6.

### Frontend
- **B5 · Voseo** — `catalogo/empresas.html:79`: "podés reactivarla" → "puedes" (tuteo). *(Introducido en un cambio reciente de esta sesión.)*
- **B6 · Imágenes sin `alt`** — `almacen/revision.html:17,55`.
- **B7 · Plantilla huérfana con datos falsos** — `templates/index.html` (no la usa ningún controlador; cifras inventadas). Eliminar.
- **B8 · Nombre de producto inconsistente** — `almacen/home.html:22` ("Textil Inventario"); además `almacen/home|entrada|salida.html` no extienden `layout/base.html` (head/CSS duplicados).
- **B9 · Comentarios de plantilla desactualizados** — `catalogo/articulos.html:17`, `colores.html:25`, `ubicaciones.html:17` dicen "solo SUPERADMIN" pero el `sec:authorize` real es `ADMIN/SUPERADMIN`.
- **B10 · Sugerencia de contraseña débil** — `usuarios/lista.html:31,131` sugiere "Ej: el DNI" (dato público). Quitar la sugerencia o subir el mínimo (hoy 6).
- **B11 · `confirmar-llegada.html` con `novalidate` y sin validación JS del reparto** — se delega 100% al backend; bloquear submit si la suma ≠ salida.

### Seguridad / Infra
- **B12 · `/reportes/errores` solo protegido por regla de URL** — `ReporteController.java:212-217`: agregar `@PreAuthorize("hasRole('SUPERADMIN')")` (cinturón y tirantes).
- **B13 · Logout por GET = CSRF de cierre de sesión** — `SecurityConfig.java:129-135`: `<img src=".../logout">` desloguea. Pasar a POST con CSRF.
- **B14 · `kardex_movimientos.ubicacion_*_id` sin FK ni índice** — `V1:179-180`. Agregar en migración nueva si se filtra kardex por ubicación.
- **B15 · Columnas ENUM nativas con `@Enumerated(STRING)`** — `recepciones.estado`, `ubicaciones.tipo`, `documentos.tipo`: ampliar el enum Java sin `ALTER TABLE` rompe el INSERT. `transferencias.estado` (VARCHAR) es el patrón seguro.
- **B16 · Default `NOMBRE_EMPRESA = "Laura & Clemente"`** en `application.yml:75` / `.env.example:38` — nombre de un cliente concreto como default. Usar neutro (vacío o "TexControl").
- **B17 · `deploy-dev.sh` no valida el timeout de healthcheck** (a diferencia de `deploy.sh`).
- **B18 · Adminer corre en prod** (bound a `127.0.0.1:8081`) — `docker-compose.yml:28-36`. Superficie extra en el host; mover a override solo-dev.

---

## 6. Documentación

### Alto
- **D1 · `NOMBRE_EMPRESA` mal descrita** — `.env.example:33-38`, `README.md:182`: dicen que controla el nombre del sidebar. **Real:** el subtítulo sale de las empresas activas (`GlobalModelAttributes`); la variable hoy solo alimenta el manifest de la PWA (`PwaController.java:18`). Corregir la descripción.
- **D2 · Login inicial equivocado en `DEPLOY.md:73`** — dice "usuario admin de V2". **Real:** V35 lo renombra a `jlynch`/`superadmin`. Corregir a `jlynch`/`superadmin`.

### Medio
- **D3 · `README.md:76,87-88` — roles desactualizados** — falta **ADMIN**; la aprobación de almacén es ADMIN+SUPERADMIN, no solo SUPERADMIN.
- **D4 · `README.md:169` — HTTPS/dominio como pendiente** cuando ya está en producción (`texcontrol.pe`). Contradice `CLAUDE.md`/`DEPLOY.md`.
- **D5 · `README.md` omite `REMEMBER_ME_KEY`** en el bloque de variables (importante para prod).

### Bajo
- **D6 · `CLAUDE.md:129` — CI descrita solo para `main`** (real: `main` y `develop`; el propio `:140` dice "ambas").
- **D7 · `CLAUDE.md:126-128` — lista de tests incompleta** (falta `GeneradorUsernameTest`).
- **D8 · Worklog de sesión embebido en `CLAUDE.md:205-248`** — estado efímero ("falta correr `deploy.sh`") que envejece mal. Mover a CHANGELOG/notas o marcar como snapshot fechado.
- **D9 · Defaults `nombre-empresa` inconsistentes** — `application.yml` ("Laura & Clemente") vs `PwaController` ("TexControl"). Unificar a neutro.

---

## 7. Verificado y correcto (sin hallazgos)

- **Inyección SQL/JPQL:** todas las `@Query` con parámetros nombrados; sin concatenación.
- **Inyección de fórmulas Excel (CWE-1236):** `ExcelExportService.sanitizarCeldaTexto` antepone apóstrofe a `= + - @`.
- **Zip-slip:** `Paths.get(...).getFileName()` + solo `.pdf` + límites anti zip-bomb.
- **Path traversal en subida:** extensión en lista blanca, nombre por UUID, carpeta = slug `[a-z0-9-]`.
- **Aislamiento SUPERADMIN:** `UsuarioController` oculta cuentas y **bloquea escalada de rol**; `LogEventoRepository` oculta acciones SUPERADMIN al ADMIN.
- **OCR:** API key por env; timeouts connect 30s/read 90s (relevante por `@Async`).
- **Secretos:** sin credenciales de infra hardcodeadas; `.env`/`htpasswd` no versionados; errores sin stacktrace al usuario.
- **Actuator:** solo `health,info`, sin detalles.
- **XSS server-side:** cero `th:utext`; todo por `th:text`/`th:attr` (escapado).
- **Convención sin placeholder:** cero `placeholder` en toda la UI.
- **CSRF AJAX:** helper `fetchConCsrf` bien usado en todas las llamadas mutantes salvo M8.
- **Esquema Flyway ↔ entidades:** alineado columna por columna (V1–V36 secuenciales, sin saltos); `ddl-auto: validate` no rompe.
- **BCrypt >72 bytes (CVE-2025-22228):** mitigado por el tope de 72 en `UsuarioController`.
- **`@Async` + SecurityContext:** propagación manual correcta en `ArchivoHistoricoService`.
- **Borrados protegidos:** programa (`existsByProgramaDetalle_ProgramaId`), recepción/transferencia por estado — correctos.

---

## 8. Plan de remediación priorizado

**P0 — Ya (antes de cualquier cliente que pague):**
1. **Idempotencia de stock (C1–C4):** guard de estado al entrar en confirmar recepción/salida/llegada + validar reparto ≤ salida. *(El fix de mayor impacto; incluye reforzar doble-submit en el front, M10.)*
2. **Rotar `jlynch` en `textillaura`** y quitar la clave por defecto del flujo de provisión (A1).
3. **`REMEMBER_ME_KEY` (A2):** exigirla sin default + wirearla en los compose de prod/multicliente + generarla en `mc_generar_env`.
4. **XSS del OCR (A3, A4):** escapar/`textContent` en recepción y facturación.

**P1 — Pronto:**
5. `generarNumero` sin `count()` (A5); manejar la carrera de INSERT de stock (A6).
6. CSRF del "Descargar .zip" (M8); `source` → `grep|cut` en scripts de cliente/backup (M11).
7. `@PreAuthorize` en borrados de servicio y POST de escritura (M6); rate-limit de login (M7).
8. Verificar checksums de V26/V27 en los entornos (M12); job de CI con MySQL/Testcontainers (M13).

**P2 — Deuda / mejoras:**
9. Pertenencia de detalles (M1), `solicitada >= recibida` (M2), peso por color (M3), robustez de archivo histórico (M4, M5, B3).
10. Subir parche de Spring Boot (M14); FKs/índices de kardex (B14); enums→VARCHAR o ALTER (B15); Adminer fuera de prod (B18).
11. Limpieza front: voseo (B5), `index.html` huérfano (B7), comentarios (B9), `alt` (B6), nombre "Textil Inventario" (B8).
12. **Documentación (D1–D9):** corregir `NOMBRE_EMPRESA`, login de `DEPLOY.md`, roles/HTTPS/`REMEMBER_ME_KEY` del README, y separar el worklog de sesión de la guía.

---

*Informe generado por auditoría automatizada multi-agente (5 auditores paralelos), verificado contra el código fuente de `main@a471205`.*
