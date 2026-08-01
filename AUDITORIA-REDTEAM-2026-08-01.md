# Red-Team / Auditoría de despliegue — TexControl

- **Rol:** Principal Engineer / Security Engineer con veredicto de aprobar/rechazar despliegue.
- **Fecha:** 2026-08-01 · **Rama:** `develop` (HEAD `d24f542`, superconjunto de `main`).
- **Encargo:** intentar **romper** el sistema. Este informe contiene SOLO hallazgos **nuevos**, no cubiertos por `AUDITORIA-2026-08-01.md` (que ya recoge A1–A6, M1–M18, B1–B18). Cada afirmación fue verificada leyendo el código real; se descartan falsos positivos.
- **Método:** 5 frentes de ataque (concurrencia/integridad, OCR/archivos/recursos, auth/sesión/IDOR, NPE/JS/smells, git/CI/DevOps).

---

## 0. VEREDICTO DE DESPLIEGUE

### 🔴 RECHAZADO para el escenario planteado ("empresa grande, miles de usuarios en una semana").

Motivo en una frase: **la corrección de idempotencia de stock (los 4 críticos ya "cerrados") NO resiste concurrencia real** — es un guard TOCTOU sin `@Version`, así que con usuarios concurrentes el stock se vuelve a duplicar en silencio. A eso se suman **dos DoS por OOM que cualquier usuario autenticado dispara** (zip-bomb y export Excel sin tope) y que **desactivar a un empleado no corta su sesión viva** (hasta 8 h de acceso tras el despido). Ninguno es aceptable en un entorno multiusuario.

### 🟢 CONDICIONALMENTE OPERABLE en el uso real de hoy (single-tenant `textillaura`, 1–3 operarios, baja concurrencia).

En ese contexto la ventana de concurrencia de R-C1 casi no se abre y los OOM son accidentes de un solo ADMIN, no ataques. Es la razón por la que el sistema **funciona en producción hoy** sin incidentes. Pero **el modelo de negocio del roadmap (vender instancias, app móvil, multicliente) empuja justo hacia el escenario que este informe rechaza.**

**Lo que hay que cerrar antes de liberar a más usuarios:** R-C1 (`@Version`), R-F1 (zip-bomb), R-F2 (Excel OOM), R-A1 (expiración de sesión), R-B4 (mass assignment) + todo el P0 de la auditoría previa (A1 password, A3/A4 XSS OCR).

---

## 1. Hallazgos nuevos — ranking por severidad

| # | Sev | Título | Archivo:línea | Rompe |
|---|---|---|---|---|
| **R-C1** | 🔴 **CRÍTICO** | Idempotencia TOCTOU: sin `@Version` en Recepcion/Transferencia → doble aplicación de stock bajo concurrencia | `Recepcion.java`, `Transferencia.java` (sin `@Version`; solo `StockActual` lo tiene) | Corrupción silenciosa de inventario |
| **R-F1** | 🟠 **ALTO** | Zip-bomb: `readAllBytes()` por entrada antes de evaluar el cap acumulado | `ArchivoHistoricoService.java:87-94` | OOM → caída |
| **R-F2** | 🟠 **ALTO** | Export Excel 100% en memoria (`XSSFWorkbook`), sin tope de filas ni rango obligatorio | `ExcelExportService.java:15` · `ReporteController.java:89-108` | OOM → caída |
| **R-A1** | 🟠 **ALTO** | Desactivar/degradar/resetear un usuario NO termina su sesión viva (sin `SessionRegistry`) | `SecurityConfig.java` (sin `sessionManagement`) · `UsuarioController.java:187,117,161` | Acceso tras revocación (≤8 h) |
| **R-B4** | 🟠 **ALTO** | Mass assignment: `@ModelAttribute` sobre entidades JPA + `save()` → inyectar `id` sobrescribe fila arbitraria | `CatalogoController.java:42,120,177,230,283,336,581,651` (cero `@InitBinder`) | Escritura no intencional |
| **R-C2** | 🟡 MEDIO | Integer overflow en `stock.rollos`/kardex (solo se valida `< 0`, no cota superior) | `RecepcionService.java:265-278` · `TransferenciaService.java:94-221` | Stock negativo/basura |
| **R-B5** | 🟡 MEDIO | Denegaciones `@PreAuthorize` → HTTP 500 + falso `ERROR_SISTEMA` que envenena el panel del SUPERADMIN | `GlobalExceptionHandler.java:37-52` (sin handler de `AccessDeniedException`) | 403→500 + diagnóstico contaminado |
| **R-P1** | 🟡 MEDIO | `actualizarPrograma`: listas paralelas sin validar tamaño ni `cantidad>0`/null → `IndexOutOfBounds`/`NOT NULL` (500) y negativos | `ProgramaService.java:200-224` | 500 + dato inconsistente |
| **R-F6** | 🟡 MEDIO | Prompt injection del PDF → `intentarEnriquecerCatalogo` crea Color/Articulo con datos dictados; con `crearRecepcionAutomatica=true`, stock fabricado | `ArchivoHistoricoService.java:182-303,447-479` | Corrupción catálogo/inventario |
| **R-F4** | 🟡 MEDIO | Parsing de la respuesta OCR sin defensa (NPE si `content:[]`, `ClassCast`, JSON truncado por `max_tokens`, 429/529 sin retry) | `AnthropicOcrService.java:168-175` | Error opaco (`null`) |
| **R-F3** | 🟡 MEDIO | `descargarZip` arma el ZIP entero en heap, sin cota de `ids`/bytes, sin `@PreAuthorize` | `DocumentoController.java:86-116` | OOM |
| **R-J2** | 🟡 MEDIO | `nueva.html`: `resp.json()` sin try/catch → si el backend responde HTML de error, la creación de recepción **falla en absoluto silencio** | `recepciones/nueva.html:451,472` | UX rota + confusión de stock |
| **R-F5** | 🟡 MEDIO | `readTimeout` es SO_TIMEOUT por-socket, no un deadline total → un upstream "slow-drip" agota el pool `@Async` (refina SEC-03) | `AnthropicOcrService.java:28-33` · `AsyncConfig.java:25` | Degradación |
| **R-S1** | 🟡 MEDIO | `catch(Exception)` que **traga sin loguear** en el enriquecimiento de catálogo → líneas de import perdidas sin rastro | `ArchivoHistoricoService.java:454-457,481-484` | Pérdida silenciosa de datos |
| **R-C3** | 🟢 BAJO | Deadlock potencial en `confirmarLlegada` por locks `PESSIMISTIC_WRITE` en orden de `HashMap` variable | `TransferenciaService.java:209-223` | 500 (rollback, sin corrupción) |
| **R-F7** | 🟢 BAJO | Sin cuota de disco para `DOCUMENTOS_PATH` → agotamiento sostenido (cruzado entre clientes en multicliente) | `ArchivoHistoricoService.java:100-107` | DoS almacenamiento |
| **R-F8** | 🟢 BAJO | `servirFoto` fija content-type por extensión, sin `Content-Disposition` ni magic-bytes; depende del `nosniff` default | `AlmaceneroController.java:105-112` | XSS latente |
| **R-A2** | 🟢 BAJO | Remember-me `TokenBased`: cookie de 30 días **no revocable individualmente** (solo cambiando password o rotando la key global) | `SecurityConfig.java:133-138` | Robo de cookie persistente |
| **R-A3** | 🟢 BAJO | Política de clave débil (mín. 6, UI sugiere "el DNI") + usernames deterministas/enumerables + sin lockout in-app | `UsuarioController.java:252-260` · `usuarios/lista.html:31` | Fuerza bruta (amplifica A1) |
| **R-J1** | 🟢 BAJO | `sw.js`: estáticos propios cache-first → JS/CSS obsoleto tras deploy; `respondWith(undefined)` si falla un no-cacheado | `static/sw.js:59-70` | Staleness / error de red |
| **R-J3** | 🟢 BAJO | Submit real sin bloqueo durante los `await` en serie → ventana de doble-POST agrandada (refuerza M10) | `recepciones/nueva.html:391-451` | Doble creación |
| **R-D1** | 🟢 BAJO | CI sin `permissions:` (GITHUB_TOKEN amplio), sin `timeout-minutes`, sin escaneo de dependencias/imagen | `.github/workflows/ci.yml` | Supply-chain / CI colgado |
| **R-D2** | 🟢 BAJO | Dockerfile sin `HEALTHCHECK` ni límite de heap JVM (`-XX:MaxRAMPercentage`) — riesgo en VPS de 4 GB con 3 stacks | `Dockerfile` | OOM del contenedor |

**Smells transversales (mantenibilidad):** antipatrón de **listas paralelas** sistémico (`confirmarRecepcion`/`confirmarSalida`/`crearPrograma`/`actualizarPrograma` indexan varias `List<>` por posición; con fallback `i<size` pegan datos a la línea equivocada **en silencio**) → migrar a `List<DTO>`; `normalizar()` duplicado en 4 servicios → utilitario en `common`; strings mágicos de roles/`"LISO"`/`"GUIA"`/`"FACTURA"` sin enum central.

---

## 2. Los 5 hallazgos que bloquean el despliegue — detalle y fix

### 🔴 R-C1 · Idempotencia TOCTOU (el que reabre los "críticos cerrados")
**Verificado:** solo `StockActual` tiene `@Version`; `Recepcion` y `Transferencia` **no**.
Los guards C1–C4 hacen `findById → if(estado != X) throw → … → save`. Es SELECT-check-UPDATE **sin lock ni versión sobre la entidad padre**. Protegen el doble-click **secuencial** (el 2.º request ve el estado ya cambiado), no la **concurrencia real**.
**PoC:** dos POST concurrentes a `/recepciones/{id}/confirmar` sobre un artículo que YA tiene fila de stock. Ambos leen `estado=PENDIENTE` y pasan el guard. El `@Lock(PESSIMISTIC_WRITE)` sobre `stock_actual` **serializa** pero no deduplica: T1 hace `rollos+=100`, commitea, libera; T2 lee el valor ya incrementado y suma **otros 100**. Resultado: **+200 por una recepción**, kardex duplicado, `cantidadRecibida` doblada. El `@Version` de `StockActual` no ayuda (T2 lee la versión nueva limpiamente). La ventana la abre la falta de versión en el **padre**.
**Fix:** `@Version private Integer version;` en `Recepcion` y `Transferencia` (T2 falla con `OptimisticLockException` y revierte); o `UPDATE … SET estado='CONFIRMADA' WHERE id=? AND estado='PENDIENTE'` condicional y abortar si afecta 0 filas; o `@Lock(PESSIMISTIC_WRITE)` en el `findById` del padre. **Esfuerzo:** Bajo (una anotación + migración de columna). **Impacto:** cierra de verdad la integridad de stock.

### 🟠 R-F1 · Zip-bomb
`ArchivoHistoricoService.java:87`: `byte[] contenido = zis.readAllBytes();` descomprime **la entrada entera a heap** antes de que la línea 90 evalúe el cap acumulado de 200 MB. El límite es post-facto y global, nunca por-entrada.
**PoC:** ZIP de 25 MB (cabe en `max-request-size`) con un solo `bomba.pdf` = 25 GB de ceros (DEFLATE ~1000:1). `readAllBytes` → `OutOfMemoryError` en la primera entrada. En VPS de 4 GB tumba el contenedor.
**Fix:** copiar en streaming con tope por-entrada y abortar durante la copia (`Math.min(restante, MAX_POR_ENTRADA)`); guard de ratio de compresión. **Esfuerzo:** Bajo-Medio.

### 🟠 R-F2 · Export Excel OOM
`XSSFWorkbook` mantiene todo el libro en heap; `ReporteController` materializa la `List<KardexMovimiento>` completa (entidades EAGER, ver M5 previo) + la matriz de objetos + el workbook = 3 copias vivas. Los `/excel` de kardex/stock/recepciones aceptan rango de fechas `required=false`.
**PoC:** `GET /reportes/kardex/excel` sin `desde`/`hasta` con meses de import histórico → cientos de miles de filas EAGER + DOM en memoria → OOM. Lo dispara un ADMIN con un click.
**Fix:** `SXSSFWorkbook` (streaming), proyecciones DTO en los repos de reporte, tope de filas o rango obligatorio. **Esfuerzo:** Medio.

### 🟠 R-A1 · La sesión viva sobrevive a la revocación
**Verificado:** cero `SessionRegistry`/`maximumSessions`/`HttpSessionEventPublisher`. Las authorities se cachean en la `HttpSession` (timeout 8 h) al login; la autorización por URL evalúa la sesión, no la BD. `loadUserByUsername` (que sí chequea `activo`) solo corre al autenticar.
**Vectores:** (1) empleado despedido con pestaña abierta → acceso total ≤8 h tras `inactivar`; (2) degradación ADMIN→VENDEDOR no surte efecto en la sesión viva; (3) reset de clave ante cuenta comprometida invalida el remember-me pero **no expulsa la sesión del atacante**.
**Fix:** `HttpSessionEventPublisher` + `SessionRegistry`; en `inactivar`/cambio-de-rol/reset, `sessionRegistry.getAllSessions(username,false).forEach(SessionInformation::expireNow)`. Mínimo inmediato: bajar el timeout. **Esfuerzo:** Medio.
*(Nota: el remember-me en sí está bien — SÍ honra la desactivación, porque `loadUserByUsername` lanza y cancela la cookie. El riesgo se trasladó a la sesión de servidor.)*

### 🟠 R-B4 · Mass assignment
**Verificado:** 8 endpoints de `CatalogoController` bindean `@ModelAttribute <EntidadJPA>` y llaman `repository.save()`; cero `@InitBinder`/`setDisallowedFields`. El binder acepta cualquier propiedad del body, incluido `id`.
**PoC:** al **formulario de "crear color"**, `POST /catalogo/colores/guardar` con `id=7&nombreOficial=BLANCO&activo=false` → `save()` es un **UPDATE de la fila 7** (renombrada+inactivada), no un alta. Igual para reactivar una empresa (`activo=true`) o cambiar `ruc`/`carpeta` desde el form de alta, saltándose `/reactivar`. Acotado a ADMIN + single-tenant, pero es una vía de escritura silenciosa no intencional.
**Fix:** DTOs con solo los campos del form (el patrón correcto ya existe en los `crear-rapido` con `record`), o `@InitBinder` con `setDisallowedFields("id","activo","createdAt",…)`. **Esfuerzo:** Medio.

---

## 3. Calificaciones (0–10) — recalibradas por el red-team

| Dimensión | Nota | Δ vs auditoría previa | Motivo del ajuste |
|---|:--:|:--:|---|
| Arquitectura | 6.5 | ▼0.5 | Mass assignment sistémico + listas paralelas como antipatrón de diseño. |
| Backend | 6.5 | ▼1.0 | R-C1 reabre la integridad de stock; overflow, `actualizarPrograma` frágil. |
| Frontend | 5.5 | = | Sin cambios; R-J2 (fallo silencioso) confirma la fragilidad ya señalada. |
| Base de datos | 6.5 | ▼0.5 | Falta `@Version` en padres transaccionales; deadlock potencial. |
| **Seguridad** | **6.0** | ▼1.5 | R-A1 (revocación no efectiva), R-B4 (mass assignment), R-F6 (prompt injection), R-B5. Más superficie de la que la auditoría amable reflejaba. |
| Performance | 5.0 | ▼1.0 | Dos OOM triviales (R-F1, R-F2) + Excel/descarga en memoria + EAGER. |
| Escalabilidad | 5.0 | ▼1.0 | R-C1 escala con la concurrencia; OOM escalan con el volumen. |
| Testing | 4.5 | ▼ | Ningún test de concurrencia, límites, overflow, autorización ni recursos. |
| UX | 6.5 | ▼0.5 | R-J2 (creación que falla en silencio) + `alert()` como canal. |
| UI | 7.5 | = | Sin cambios. |
| Documentación | 6.5 | = | Sin cambios (9+3 hallazgos abiertos del informe previo). |
| Mantenibilidad | 6.0 | ▼0.5 | Listas paralelas, duplicación, strings mágicos, clases grandes. |
| Código | 6.0 | ▼0.5 | Legibilidad sigue alta (8.5), pero smells estructurales bajan el conjunto. |
| **Preparación producción** | **4.5** | ▼1.5 | Para el escenario "empresa grande": RECHAZO. Para single-tenant actual: ~7 con el P0. |

**Nota global (escenario empresa grande): ~5.8 / 10.** Proyecto de buen artesano con integridad y disponibilidad **no probadas bajo carga**. La legibilidad y el criterio de dominio son notables; la resistencia a concurrencia, recursos y abuso es la deuda que impide aprobar.

---

## 4. Plan de acción (roadmap)

### 🔴 Crítico (bloquea despliegue multiusuario)
| Problema | Riesgo | Archivos | Solución | Esfuerzo | Impacto |
|---|---|---|---|---|---|
| **R-C1** idempotencia TOCTOU | Doble stock silencioso bajo concurrencia | `Recepcion.java`, `Transferencia.java` + migración | `@Version` en ambos padres (o UPDATE condicional por estado) | Bajo | Cierra integridad de stock |
| **R-F1** zip-bomb | OOM → caída con 1 archivo | `ArchivoHistoricoService.java:87` | Streaming con tope por-entrada + ratio guard | Bajo-Medio | Elimina DoS trivial |
| **R-F2** Excel OOM | OOM con un click | `ExcelExportService`, `ReporteController` | `SXSSFWorkbook` + DTO + tope filas/rango | Medio | Elimina DoS accidental |
| **R-A1** sesión no revocable | Acceso tras despido ≤8 h | `SecurityConfig`, `UsuarioController` | `SessionRegistry` + `expireNow` | Medio | Revocación real |
| **R-B4** mass assignment | Sobrescritura de filas | `CatalogoController` (8) | DTOs / `@InitBinder` disallowed | Medio | Binding explícito |
| *(previos P0)* A1 password, A3/A4 XSS OCR | Acceso total / RCE en cliente | migraciones V33/V35, `nueva.html`/`facturar.html` | Rotar + `textContent`/CSP | Medio | — |

### 🟠 Alto
R-C2 overflow (`Math.addExact` + cota) · R-B5 handler de `AccessDeniedException` (403, no 500, y no registrar `ERROR_SISTEMA`) · R-P1 validar listas/cantidades en `actualizarPrograma` · R-F6 tratar OCR como propuesta a validar (rangos sanos, whitelist, revisión humana antes de confirmar stock) · R-F4 parseo defensivo del OCR + retry 429/529 · R-J2 try/catch alrededor de `resp.json()`.

### 🟡 Medio
R-F3 descargarZip en streaming + cap + `@PreAuthorize` · R-F5 deadline total del RestClient · R-S1 loguear antes de degradar · R-A2 remember-me persistente/revocable + `.disabled(!activo)` explícito · R-A3 política de clave (mín. 10-12, sin "DNI", lockout) · R-D1 `permissions:`/`timeout`/dep-scan en CI · migrar listas paralelas a `List<DTO>`.

### 🟢 Bajo
R-C3 ordenar claves antes de bloquear · R-F7 cuota de disco · R-F8 `Content-Disposition` + magic-bytes · R-J1 SW network-first para propios · R-J3 bloquear submit · R-D2 `HEALTHCHECK` + heap JVM · dedupe `normalizar()` · enums de roles/tipos.

---

## 5. Informe final

**Resumen.** El sistema resolvió bien los críticos "de manual" (idempotencia secuencial, secreto remember-me) y tiene un dominio de negocio sólido y legible. Pero un red-team con foco en concurrencia, recursos y abuso encuentra que **la integridad de stock y la disponibilidad no están probadas bajo condiciones multiusuario**, que es exactamente donde el roadmap quiere llevar el producto.

**Fortalezas.** Legibilidad y comentarios "por qué" excepcionales; modelo de roles y aislamiento SUPERADMIN correctos (sin escalada, sin IDOR entre clientes por ser single-tenant, session-fixation cubierto, CORS cerrado); remember-me criptográficamente correcto y que honra la desactivación; Dockerfile no-root multi-stage; SQL parametrizado; zip-slip y sanitización de fórmulas Excel bien hechos; disciplina de git y `.gitignore` limpios.

**Debilidades principales.** (1) Guards de estado sin `@Version` → integridad de stock frágil bajo carga. (2) Varios flujos que cargan datos ilimitados en heap (ZIP, Excel, descarga) → OOM triviales. (3) Ausencia de gestión de sesión → revocación de acceso no efectiva. (4) Binding de entidades JPA sin whitelist → mass assignment. (5) OCR tratado como fuente de verdad (prompt injection). (6) Denegaciones de permiso convertidas en 500 que contaminan el diagnóstico. (7) Testing sin concurrencia/límites/autorización.

**Riesgos para producción.** Corrupción silenciosa de inventario (la peor: no hay error), caída por OOM disparable por cualquier autenticado, y acceso persistente de usuarios revocados. En single-tenant de baja concurrencia el riesgo real es bajo; en el escenario objetivo es inaceptable.

**Deuda técnica estimada.** ~3–4 semanas-persona para el P0+P1 (R-C1 es horas; los OOM y la sesión, días cada uno; el resto, la cola larga de medios/bajos). Es deuda **acotada y bien localizada**, no una reescritura.

**Decisión.** **RECHAZO** el despliegue para "empresa grande / miles de usuarios" en el estado actual. **APRUEBO condicionalmente** la operación single-tenant actual (ya en vivo) siempre que se cierre el P0 antes de sumar usuarios concurrentes o vender instancias.

**Antes de liberar:** R-C1, R-F1, R-F2, R-A1, R-B4 + A1/A3/A4 previos.
**Puede esperar:** la mayoría de los 🟡 (OCR defensivo, remember-me persistente, CI hardening).
**Puede posponerse:** los 🟢 (staleness del SW, cuota de disco, enums, dedupe) y los refactors estructurales.

**Nota final del proyecto: 5.8 / 10** para el estándar "aprobar despliegue en empresa grande"; **~7.5** para el estándar "herramienta interna single-tenant en operación". La diferencia entre ambas notas ES el trabajo pendiente de este informe.

---

*Red-team multi-agente (5 frentes) verificado contra `develop`@`d24f542`. Complementa —no reemplaza— a `AUDITORIA-2026-08-01.md`.*
