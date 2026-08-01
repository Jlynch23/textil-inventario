# Auditoría técnica completa — TexControl

- **Rama auditada:** `develop` (superconjunto de `main`; `main` = `d24f542`, `develop` +6 commits, fast-forward limpio).
- **Fecha:** 2026-08-01
- **Auditor:** revisión Lead Software Engineer (nivel aprobar/rechazar despliegue).
- **Método:** 6 auditorías paralelas (backend/lógica, seguridad, BD/infra/CI/scripts, frontend/UX, docs/tests) + verificación independiente de los hallazgos críticos. Cada afirmación fue contrastada contra el código real de hoy; se marcan los hallazgos de la auditoría previa (`AUDITORIA.md`, 24-jul) como **CORREGIDO / PARCIAL / ABIERTO**.

---

## 1. Veredicto ejecutivo

**El proyecto mejoró de forma sustancial desde el 24-jul.** Los **4 hallazgos CRÍTICOS** de la auditoría anterior —idempotencia de los movimientos de stock, el mayor riesgo del sistema— están **cerrados con guardas de estado y cubiertos con tests de regresión**. El bloque ALTO del secreto `remember-me` (A2) está **cableado punta a punta**. Se añadió `@PreAuthorize` de defensa en profundidad a **todos los controladores de escritura**. Es trabajo real, bien comentado y con criterio.

**No obstante, NO está listo para "miles de usuarios en una semana".** El framing del encargo (producción masiva) choca con tres realidades:

1. La app se vende como **instancia single-tenant** (una BD por cliente), no como SaaS multitenant masivo. Eso neutraliza los IDOR entre clientes pero **no** los problemas de reproducibilidad entre copias.
2. Quedan **2 focos que exigen acción antes del primer cliente que pague**: la **credencial por defecto de máximo privilegio versionada** (A1) y el **XSS por `innerHTML` con datos de OCR** (A3/A4 — el único camino de ejecución de código en el cliente).
3. Hay **deuda de rendimiento estructural** recién visible ahora que el catálogo se puebla: `@ManyToOne` masivamente `EAGER` → N+1 en reportes/inventario, y escaneos O(n²) en memoria en Archivo Histórico.

**Conteo de hallazgos (estado actual):** 0 críticos abiertos · 4 altos · 12 medios · ~15 bajos. Los 4 críticos previos: **CORREGIDOS**.

### Preparación para producción — semáforo

| | |
|---|---|
| 🟢 **Integridad transaccional (stock)** | Cerrada. Guards de estado + tests. |
| 🟡 **Seguridad** | Sólida en fundamentos; falla en 2 puntos accionables (A1, A3/A4). |
| 🟡 **Rendimiento/escalabilidad** | Techo real por EAGER/N+1; no bloquea hoy, sí al crecer. |
| 🔴 **Antes de vender instancias** | Rotar `jlynch`, cerrar XSS OCR, arreglar backup por cron (falla en silencio). |

---

## 2. Estado de los hallazgos críticos previos (C1–C4) — TODOS CORREGIDOS

| ID | Verificación | Test de regresión |
|---|---|---|
| **C1** `confirmarRecepcion` idempotente | `RecepcionService.java:243-247` — guard `if (estado != PENDIENTE) throw IllegalStateException`. | `RecepcionServiceTest.confirmarRecepcion_yaConfirmada_lanzaYNoTocaStock` |
| **C2** `confirmarSalida` verifica estado | `TransferenciaService.java:81-85` — exige `BORRADOR`. | `TransferenciaServiceTest.confirmarSalida_yaConfirmada` |
| **C3** `confirmarLlegada` exige salida previa | `TransferenciaService.java:156-160` — exige `CONFIRMADA_SALIDA`; además itera `findByTransferenciaId` (cierra M1 en este método). | `confirmarLlegada_sinSalidaConfirmada` |
| **C4** reparto ≤ despachado | `TransferenciaService.java:172-177` — `if (totalRepartido > despachado) throw`. | `confirmarLlegada_repartoMayorQueSalida` |

Los controladores mapean estas excepciones a mensajes flash sin exponer stacktrace. **Este es el fix de mayor impacto del proyecto y está bien hecho.**

---

## 3. ALTOS (abiertos)

### A1 · Contraseña SUPERADMIN por defecto, hardcodeada y reproducible — **ABIERTO**
`db/migration/V33__reset_passwords_arranque.sql:11` · `V35:16`
El hash de `jlynch` (SUPERADMIN, máximo privilegio, oculto al cliente → nunca se rota) sigue correspondiendo a `superadmin`, versionado e idéntico en toda copia. App pública.
**Riesgo:** `POST /login` con `jlynch`/`superadmin` en cualquier instancia no rotada = acceso total como proveedor.
**Fix:** rotar YA en `textillaura`; flag `must_change_password` en primer arranque o provisión de clave única por instancia; no versionar hashes reales. Neutralizar/limpiar cuentas `*prueba` antes de vender (hoy mitigadas por `activo=FALSE`, correcto).
**Archivos:** `V33`, `V35`, `UsuarioController`, flujo de provisión (`scripts/lib-cliente.sh`). **Esfuerzo:** Medio. **Impacto:** Crítico operacional.

### A3 / A4 · XSS por `innerHTML` con datos de OCR — **ABIERTO**
`recepciones/nueva.html:165,168,182,205-206,239-246` · `recepciones/facturar.html:136,139`
Datos leídos del PDF por IA (`empresaNombreDetectado`, `motivoNoMatch`, `tipoTela`, `titulo`, `composicion`, `acabado`, `colorNombre`, `colorCodigo`, `programaTenido`, `advertencia`) se concatenan en `innerHTML` sin escapar.
**Riesgo:** una guía cuyo texto contenga `<img src=x onerror=...>` ejecuta JS en la sesión del ADMIN que sube el PDF. El OCR es entrada **no confiable**. Es el único vector de ejecución de código en cliente.
**Fix:** `createElement` + `textContent`/`setAttribute`, o un `escapeHtml()` por valor. Nunca concatenar OCR en `innerHTML`. Añadir una CSP mitigaría el impacto residual.
**Esfuerzo:** Medio. **Impacto:** Alto.

### A5 · `generarNumero` con `count()+1` viola el UNIQUE — **ABIERTO**
`TransferenciaService.java:56-58` (verificado idéntico).
`long total = count(); return String.format("TRF-%06d", total+1)`. Borrar una transferencia BORRADOR intermedia (permitido) baja el count → colisión con `Transferencia.numero` UNIQUE; dos altas concurrentes → mismo número → `DataIntegrityViolationException` que **bloquea crear transferencias**.
**Nota:** el equipo YA aplicó el patrón correcto (validación previa + `catch(DataIntegrityViolationException)`) al **número de Programa** (`ProgramaService.java:90-99,202-207` + `ProgramaController`), pero **no lo replicó en Transferencia**.
**Fix:** derivar de `MAX(numero)` o secuencia dedicada/columna autoincremental; nunca de `count()`. **Esfuerzo:** Bajo. **Impacto:** Alto (disponibilidad).

### A6 · Carrera al insertar la primera fila de stock — **ABIERTO (mitigado por UNIQUE)**
`RecepcionService.java:287-297` · `TransferenciaService.java:209-219` · `StockActualRepository:17-18`
`@Lock(PESSIMISTIC_WRITE)` sobre un `findBy...` que no encuentra fila no bloquea nada. Dos confirmaciones concurrentes del primer stock de artículo+color+ubicación → ambas ven vacío, una gana, la otra revienta contra el UNIQUE (V27) y hace rollback de toda la confirmación. Sin corrupción silenciosa; sí fallo de concurrencia no manejado. Poco probable en single-tenant de baja concurrencia.
**Fix:** retry sobre `DataIntegrityViolationException`, o pre-crear la fila con lock (upsert). El `@Version` de `StockActual` ya existe para migrar a lock optimista. **Esfuerzo:** Medio. **Impacto:** Medio.

---

## 4. MEDIOS

### Backend / integridad de datos
- **M1 · Pertenencia del detalle — PARCIAL.** `confirmarLlegada` ya es seguro (itera los detalles del padre). `confirmarRecepcion` (`RecepcionService.java:259`) y `confirmarSalida` (`TransferenciaService.java:88`) siguen con `findById(detalleId).orElseThrow()` sin verificar que el detalle pertenece a la entidad del path. POST manipulado (ADMIN) con IDs de otra entidad afecta stock ajeno. **Fix:** validar `d.getRecepcion().getId().equals(recepcionId)`.
- **M2 · `actualizarPrograma` permite `solicitada < recibida` — ABIERTO.** `ProgramaService.java:200-204`. Hay guard `if (p.isCompleto()) throw` (180), pero una línea **parcialmente** recibida queda con pendiente negativo. **Fix:** validar por línea `nuevaSolicitada >= pd.getCantidadRecibida()`.
- **M3 · Peso de referencia en la llegada no discrimina por color — ABIERTO.** `TransferenciaService.java:188` usa `findFirstByTransferenciaIdAndArticuloIdAndTipoMovimiento` sin `colorId` (verificado). Dos líneas del mismo artículo con distinto color toman el peso de la primera salida → `peso_kg` mal en una línea (rollos correctos). **Fix:** incluir `colorId` en la query.
- **M4 · Recepción automática — PARCIAL (mitigada).** `ArchivoHistoricoService.java:243-303` ahora chequea `recepcionCreadaId` y el guard de guía duplicada evita duplicar stock; puede quedar una Recepción PENDIENTE huérfana ante fallo parcial. Riesgo residual bajo.

### Rendimiento / escalabilidad (NUEVOS — los más relevantes a futuro)
- **M5 (NUEVO) · `@ManyToOne` masivamente EAGER → N+1.** `StockActual.java:20,23,26`, `Articulo.java:21-33` (que a su vez trae EAGER tipoTela/titulo/composicion/acabado/color), `KardexMovimiento`, `*Detalle`. Listar `StockActual` en `/reportes` e `/inventario` dispara una cascada de SELECTs por fila (Hibernate no join-fetchea EAGER en colecciones). **Es el mayor techo de rendimiento del sistema**, amplificado por el modelo multicliente (3 apps en 4 GB). **Fix:** pasar los `@ManyToOne` de listados a `LAZY` + `JOIN FETCH` explícito, o proyecciones DTO en los repos de reporte. **Esfuerzo:** Medio-Alto. **Impacto:** Alto (escalabilidad).
- **M6 (NUEVO) · Escaneos O(n²) en memoria en Archivo Histórico.** `ArchivoHistoricoService.java:284-303,336-359,383-398` hacen `findByTipoDocumento(tipo)` (cargan TODA la tabla) y filtran en Java, una vez por documento dentro del bucle async → O(n²) con la tabla entera materializada repetidamente, justo en el módulo de importación masiva. **Fix:** query derivada indexada / columna `numero_normalizado` indexada. **Esfuerzo:** Medio. **Impacto:** Medio-Alto en imports grandes.

### Seguridad / defensa en profundidad
- **M7 · `@PreAuthorize` — PARCIAL.** Todos los **controladores** de escritura lo tienen (mejora real). **Falta:** (a) los **borrados de servicio** (`CatalogoService:232-246` y demás, verificado: 0 anotaciones) — dependen del controlador; (b) **`ArchivoHistoricoController` no tiene NINGÚN `@PreAuthorize`** (verificado: 0) — `/subir-zip`, `/eliminar` quedan solo bajo `anyRequest()`. No explotable con la config actual, pero es la única familia de escritura sin la anotación **y la doc (`CLAUDE.md:102-104`) promete que los servicios la llevan** (falso). **Fix:** anotar `ArchivoHistoricoController` y los 3 `confirmar*` de servicio, o corregir la doc.
- **M8 · Sin protección de fuerza bruta en `/login` — ABIERTO.** `fail2ban` cubre SSH, no HTTP. App pública + credencial por defecto conocida (A1). **Fix:** `limit_req` en nginx sobre `/login` o lockout tras N fallos. **Esfuerzo:** Bajo.
- **M9 · "Descargar .zip" sin token CSRF — ABIERTO.** `documentos/lista.html:119-131` arma POST dinámico sin `_csrf`. CSRF está habilitado (sin `.csrf(disable)`) → 403, **función rota** (falla-cerrado, no vulnerabilidad). **Fix:** hidden desde `meta[name=_csrf]`.

### Frontend
- **M10 · Doble-submit del form real — MAYORMENTE ABIERTO.** Solo se blindaron los modales "crear al vuelo". Ningún submit real (confirmar-salida/llegada, transferencias/nueva, recepciones/confirmar, facturar, almacen) deshabilita su botón. Es el refuerzo cliente de los CRÍTICOS de idempotencia. **Fix:** deshabilitar botón en el `submit` tras validar.
- **M11 (NUEVO) · UI depende 100% de CDN externo (jsdelivr) → rompe PWA offline.** `layout/base.html:18-19,283` y `almacen/*.html` cargan Bootstrap/íconos del CDN; `sw.js` solo precachea assets del **mismo origen** → offline/CDN bloqueado/red móvil intermitente = app sin estilos ni JS. Contradice el pedido "app móvil" del roadmap. **Fix:** servir Bootstrap/íconos locales e incluirlos en `PRECACHE` (habilita además CSP estricta + SRI). **Esfuerzo:** Bajo-Medio. **Impacto:** Alto para uso móvil.
- **M12 (NUEVO) · Nombres de ubicación del cliente hardcodeados en plantillas.** `transferencias/nueva.html:14-15` ("Praderas → Gamarra… 1006, 213…"), `confirmar-salida.html`, `dashboard/index.html`. Una instancia nueva (Camargo/Emilio) muestra texto mentiroso. **Fix:** derivar del catálogo de ubicaciones. **Bloqueante para multicliente.**
- **M13 · `<option>` por concatenación de strings — ABIERTO.** `programas/nuevo.html:296,304,312,332`, `editar.html:201-235`, `recepciones/nueva.html:200`. Frágil ante `<`, `&`, comillas en valores creados al vuelo por el usuario. **Fix:** `new Option(texto, valor)`.
- **M14 (NUEVO) · Duplicación ~350 líneas JS `programas/nuevo.html` ↔ `editar.html`.** Divergen sutilmente; un fix ya se olvidó en `editar.html` (guard anti-doble-click). No hay ningún `th:fragment` reutilizable salvo el layout. **Fix:** fragmento Thymeleaf + `.js` compartido parametrizado por modo.

### BD / Infra / CI
- **M15 · CI no detecta drift entidad↔esquema — ABIERTO.** `ci.yml` solo `mvn compile` + `mvn test` (Mockito puro). No bootea Spring ni corre Flyway `validate` contra MySQL. Con `ddl-auto: validate` y 37 migraciones, un mismatch pasa CI en verde y **revienta en el deploy**. **Fix:** job `@SpringBootTest` con Testcontainers MySQL (o MySQL de service). **Esfuerzo:** Bajo. **Impacto:** Alto (protege cada instancia nueva).
- **M16 · Backup por cron falla en silencio — ABIERTO.** `lib-cliente.sh:70` escribe `NOMBRE_EMPRESA=$nombre` sin comillas; `backup-cliente.sh:44` y `migrar-cliente.sh` hacen `source .env` bajo `set -euo pipefail` → un nombre con `&` (p.ej. "Laura & Clemente") aborta el `source`. `deploy.sh`/`deploy-dev.sh` ya migraron a `grep|cut`, estos no. **Fix:** `grep|cut` + entrecomillar `NOMBRE_EMPRESA="$nombre"`. **Esfuerzo:** Bajo. **Impacto:** Alto (backups).
- **M17 (NUEVO) · TZ de contenedores MySQL sin fijar.** `ZonaHorariaConfig` (develop) pone la JVM en `America/Lima`, y los 4 datasources llevan `serverTimezone=America/Lima` (coherentes). Pero los contenedores MySQL corren en UTC, así que columnas con `DEFAULT CURRENT_TIMESTAMP` server-side se llenan en UTC → filas con timestamp de app (Lima) conviven con filas de default (UTC) = hasta 5 h de descuadre. **Fix:** `TZ: America/Lima` + `command: --default-time-zone=-05:00` en los compose de MySQL.
- **M18 · Spring Boot 3.3.5 detrás de parches — ABIERTO.** `pom.xml:11`. CVE-2025-22228 (BCrypt >72) sigue mitigada por el tope en `UsuarioController:256`. **Fix:** subir al último 3.3.x en `develop`.
- **M12-prev · Checksum drift V26/V27 — NO VERIFICABLE** (clon shallow). Riesgo estructural persiste (`validateOnMigrate` activo). Verificar checksums en cada BD desplegada.

---

## 5. BAJOS (selección)

**Backend:** B1 · 62 `orElseThrow()` sin mensaje → 500 opacos; añadir mensaje en las entradas de flujo. · B3 · `procesarPendientesAsync` sin tope de reintentos (cada doc atrapa su excepción, aceptable).
**Frontend/UX:** B5 · **Voseo (REGRESIÓN):** `empresas.html:79`, + nuevos `recepciones/nueva.html:462` ("Podés"), `programas/nuevo.html:395` ("Completá/quitá") → tuteo. · B6 · imágenes sin `alt` (`almacen/revision.html:17,55`). · B7 · `templates/index.html` huérfano con cifras falsas → borrar. · B8 · `almacen/*.html` no extienden `layout/base.html` (head/CSS duplicado) + "Textil Inventario". · B9 · comentarios "solo SUPERADMIN" desfasados (real ADMIN/SUPERADMIN). · N5 · accesibilidad mínima (1/46 plantillas con `aria-*`; modales sin `aria-labelledby`; botones-ícono sin `aria-label`; contraste flojo en `.nav-section`). · N6 · `alert()`/`confirm()` nativos como canal de validación. · B11 · `confirmar-llegada.html` `novalidate` sin validación cliente del reparto.
**Seguridad/Infra:** B12 · `/reportes/errores` sin `@PreAuthorize` (protegido solo por URL). · B13 · logout por GET (CSRF de logout). · B14 · `kardex_movimientos.ubicacion_*_id` sin FK ni índice (V1:179-180). · B15 · ENUM nativos (`recepciones.estado`, `ubicaciones.tipo`, `documentos.tipo`, `kardex.tipo_movimiento`) rompen el INSERT al ampliar el enum Java sin `ALTER`; `transferencias.estado` (VARCHAR+CHECK) es el patrón seguro. · B17 · `deploy-dev.sh` no corta ante MySQL no-healthy. · B18 · Adminer en prod (bound a 127.0.0.1) con `:latest` sin pinear → mover a override solo-dev.

---

## 6. Documentación (D1–D12) — los 9 previos siguen ABIERTOS + 3 nuevos

Impacto real: **D5 · README omite `REMEMBER_ME_KEY`** (hoy obligatoria → quien siga el README no arranca la app). **D4 · README describe HTTPS como pendiente** cuando ya está en prod (contradicción interna). **D1 · `NOMBRE_EMPRESA` mal descrita** (solo alimenta el manifest PWA, no el subtítulo — que sale de empresas activas en `GlobalModelAttributes`), propagada a un **tercer** archivo (`DEPLOY.md:56`). **D3 · README omite el rol ADMIN.** **D2 · login inicial equivocado en `DEPLOY.md:73`.**
Nuevos: **D10 · `CLAUDE.md:181` "36 migraciones"** (real 37). **D11 · `application.yml:82` comentario engañoso** ("el default es solo para dev" cuando ya no hay default). **D12 · `CLAUDE.md:102-104` claim de `@PreAuthorize` en servicios es falso** (solo en controladores).
Ausencias: sin manual de usuario (relevante al vender por instancia), sin diagrama de estados (Recepción/Transferencia — valioso dado que la idempotencia gira sobre esas transiciones), sin CHANGELOG (por eso el worklog vive embebido en `CLAUDE.md`, D8).

---

## 7. Tests — cobertura ~45–55 % de la lógica crítica

**Fuerte y de alta calidad** en el corazón transaccional: stock (recepción/salida/llegada), **idempotencia C1–C4** (con `verify(...never()).save()` en los caminos de rechazo), matching OCR→artículo, desempate FAST DYE, username. AAA claro, asserts de negocio concretos, `ArgumentCaptor`.
**Cero cobertura** en: seguridad/autorización (aislamiento SUPERADMIN, **bloqueo de escalada de rol**, tope BCrypt, filtro de auditoría — invariantes que la doc marca como núcleo), seguridad de archivos (zip-slip, sanitización Excel), `ProgramaService`, `generarNumero`/concurrencia, y casi todo `ArchivoHistoricoService`.
**100 % Mockito puro:** ni un `@SpringBootTest`/`@WebMvcTest`/`@DataJpaTest`/`@WithMockUser`. → M15 (drift de esquema) sin red.
**Fix prioritario:** (1) `@SpringBootTest` + Testcontainers que arranque el contexto y valide las 37 migraciones (alto valor, bajo esfuerzo); (2) `@WebMvcTest` + `@WithMockUser` sobre `UsuarioController` (escalada de rol); (3) unit tests de `DocumentoStorageService`/`ExcelExportService`.

---

## 8. Verificado y correcto (sin hallazgo)

Inyección SQL/JPQL (cero concatenación en `@Query`) · aislamiento SUPERADMIN y **bloqueo de escalada de rol** sólidos (`UsuarioController`) · CSRF habilitado + `fetchConCsrf` en AJAX mutante · path traversal (UUID + whitelist) y zip-slip mitigados · secretos por env sin defaults en `DB_PASSWORD`/`REMEMBER_ME_KEY` · Actuator solo `health,info` · cero `th:utext` · cero `placeholder` (convención cliente) · Dockerfile multi-stage no-root · PKs/UNIQUE bien pensados, columna generada de V37 elegante, `@Version` en stock presente · numeración Flyway V1–V37 sin saltos · **A2 (remember-me) cerrado end-to-end**.

**Delta `develop` (6 commits):** promovible, sin regresiones de seguridad. `ZonaHorariaConfig` limpio; unicidad de número de Programa con patrón correcto (check + `catch DataIntegrityViolation`); refactor DRY de detección de empresa (reusa `matchEmpresa`); fecha `dd/MM/yyyy` en programas; renombrado de menú a vocabulario de almacén. Único pero: introduce acoplamiento archivohistorico→recepciones y el test lo instancia con `new ArticuloMatchingService(null×6)` (frágil).

---

## 9. Calificaciones (0–10)

| Dimensión | Nota | Comentario |
|---|:--:|---|
| **Arquitectura** | **7.0** | Capas limpias, DI por constructor, organización por dominio. Bajan: God-controller `CatalogoController` (8 responsabilidades), `RecepcionService` (14 dependencias), acoplamiento archivohistorico→recepciones. |
| **Backend (lógica)** | **7.5** | 4 críticos cerrados con guardas sólidas + validaciones extra. Restan A5, A6, M1/M2/M3 y O(n²). |
| **Frontend** | **5.5** | Server-side sin XSS por `th:text`, pero cero fragmentos reutilizables, ~350 líneas duplicadas, `<option>` por string-concat, dependencia total de CDN sin precache. |
| **Base de datos** | **7.0** | Fundamentos sólidos; lastran EAGER masivo, FK/índice de kardex faltante, ENUM nativos, descuadre de TZ. |
| **Seguridad** | **7.5** | Subió con fuerza; baja por credencial por defecto (A1) y XSS de OCR (A3/A4) + sin rate-limit login. |
| **Escalabilidad** | **6.0** | EAGER/N+1 es techo real; CI ciego a drift escala a incidente por instancia. |
| **Legibilidad** | **8.5** | Lo mejor del código: comentarios que explican el *porqué*, nombres consistentes, métodos cortos. |
| **Mantenibilidad** | **6.5** | Módulos cohesivos + tests de servicio; penalizan duplicación, clases grandes, full-table scans, test frágil. |
| **Performance** | **6.0** | Aritmética correcta, pero EAGER/N+1 y O(n²) de import sin resolver. |
| **UX** | **7.0** | Flujos bien pensados (suma en vivo, dedupe, guía duplicada); restan doble-submit, `alert()`, textos hardcodeados. |
| **UI** | **7.5** | Bootstrap coherente, dark mode sin flash, sidebar colapsable; baja por index huérfano y vocabulario no unificado. |
| **Código (general)** | **6.5** | Excelente legibilidad, deuda de duplicación/clases grandes. |
| **Documentación** | **6.5** | DEPLOY/STAGING de referencia; README desactualizado (falla la primera lectura de un tercero). |
| **Organización** | **7.5** | Paquete por dominio muy coherente. |
| **Calidad profesional** | **7.0** | Criterio de senior visible (comentarios SEC/ARQ, decisiones justificadas); flecos de disciplina (doc, voseo, duplicación). |
| **Preparación para producción** | **6.0** | Apto para **seguir operando `textillaura`**; **NO apto para vender instancias** sin cerrar el P0. Con P0 hecho → ~8. |

**Nota global ponderada: ~6.9 / 10.** Proyecto sólido de un desarrollador con buen criterio, con los riesgos graves ya cerrados y una lista clara y acotada de deuda antes de escalar el negocio.

---

## 10. Plan de remediación priorizado

### 🔴 CRÍTICO (antes del primer cliente que pague)
1. **A1 · Rotar `jlynch` en `textillaura` + sacar la clave por defecto del flujo de provisión.** Riesgo: acceso total como proveedor en toda copia. Fix: `must_change_password` / clave por instancia; no versionar hashes. Archivos: `V33`, `V35`, `UsuarioController`, `lib-cliente.sh`. **Esfuerzo:** Medio. **Impacto:** Crítico.
2. **A3/A4 · Cerrar el XSS de OCR** (`textContent`/`escapeHtml` en `nueva.html`, `facturar.html`). Riesgo: ejecución de JS en la sesión del ADMIN. **Esfuerzo:** Medio. **Impacto:** Alto.
3. **M16 · Arreglar el backup por cron** (`grep|cut` + entrecomillar en `backup-cliente.sh`/`migrar-cliente.sh`/`lib-cliente.sh`). Riesgo: pérdida de datos silenciosa. **Esfuerzo:** Bajo. **Impacto:** Alto.

### 🟠 ALTO
4. **A5 · `generarNumero` sin `count()`** (replicar el patrón ya usado en Programa). Riesgo: bloquea crear transferencias. **Esfuerzo:** Bajo.
5. **M15 · Job de CI con MySQL/Testcontainers** que arranque Spring + Flyway `validate`. Riesgo: drift esquema estalla en deploy. **Esfuerzo:** Bajo.
6. **M5 · EAGER → LAZY + JOIN FETCH** en listados de reporte/inventario. Riesgo: degradación no lineal al crecer. **Esfuerzo:** Medio-Alto.
7. **M11 · Bootstrap local + precache** (PWA offline real). **Esfuerzo:** Bajo-Medio.
8. **M8 · Rate-limit de `/login`** en nginx. **Esfuerzo:** Bajo.
9. **D5/D4/D3 · Corregir README** (`REMEMBER_ME_KEY`, HTTPS ya en prod, rol ADMIN). Riesgo: nadie nuevo arranca la app. **Esfuerzo:** Bajo.

### 🟡 MEDIO
10. **M1** pertenencia de detalles · **M3** `colorId` en peso · **M2** `solicitada>=recibida` · **A6** race de INSERT stock.
11. **M6** indexar dedup de Archivo Histórico (quitar full-table scans) · **M17** TZ de contenedores MySQL.
12. **M7** `@PreAuthorize` en `ArchivoHistoricoController` + servicios (o corregir la doc, D12) · **M9** CSRF del zip · **M10** doble-submit en forms reales.
13. **M12(front)** de-hardcodear ubicaciones · **M14** unificar JS de programas (fragmento + `.js` compartido) — ambos **antes de multicliente**.
14. **M18** subir parche Spring Boot 3.3.x · verificar checksums V26/V27.
15. **Tests:** `@WebMvcTest`+`@WithMockUser` de escalada de rol; unit de zip-slip/Excel.

### 🟢 BAJO
16. B5 voseo→tuteo (3 sitios) · B7 borrar `index.html` huérfano · B8 almacen extiende base · B9 comentarios · B6/N5 accesibilidad (`alt`, `aria-label`, `aria-labelledby`, contraste) · B1 mensajes en `orElseThrow` · B12/B13 `@PreAuthorize` en errores + logout POST · B14 FK/índice kardex · B15 enums→VARCHAR · B18 Adminer fuera de prod · B11/N6 validación cliente e inline en vez de `alert()`.
17. **Refactor estructural (deuda, no urgente):** dividir `CatalogoController` (680 líneas) en controladores por entidad + helper `resolverOCrear(nombre, buscar, fabricar)` para los `crear-rapido` duplicados; extraer `RecepcionDocumentoService` de `RecepcionService` (14 dependencias); extraer el matcher de nombres a `common` sin dependencias cruzadas.
18. **Docs:** manual de usuario del ADMIN, diagrama de estados Recepción/Transferencia, CHANGELOG (mover el worklog de `CLAUDE.md`), corregir D1/D2/D6/D7/D8/D10/D11.

---

*Informe generado por auditoría multi-agente (6 auditores paralelos) verificada contra el código de `develop`@`d24f542` + verificación independiente de los hallazgos críticos.*
