# Auditoría consolidada — TexControl (panorama completo)

- **Fecha:** 2026-08-01 · **Rama:** `develop` (HEAD `d24f542`, superconjunto de `main`).
- **Qué es este documento:** unifica en un solo panorama las **dos auditorías** hechas sobre el proyecto, reconciliando sus hallazgos y resolviendo dónde una corrige o reabre a la otra.
  1. **Auditoría técnica completa** (`AUDITORIA-2026-08-01.md`) — revisión 360° por dimensiones (arquitectura, backend, frontend, BD, seguridad, docs, tests), que además verificó el estado de la auditoría previa del 24-jul.
  2. **Red-team / auditoría de despliegue** (`AUDITORIA-REDTEAM-2026-08-01.md`) — enfoque adversarial ("romper el sistema"), centrado en concurrencia, recursos, abuso y bugs latentes; solo hallazgos nuevos.
- **Método (ambas):** auditoría multi-agente en paralelo, cada afirmación verificada contra el código real; sin falsos positivos.
- **Linaje:** los dos informes parten de un tercero, la auditoría del 24-jul (`AUDITORIA.md`, `main@a471205`). Los prefijos de IDs: `A/M/B/D` = auditoría del 24-jul; `R-*` = red-team; el resto es de la auditoría completa.

---

## 1. VEREDICTO ÚNICO DE DESPLIEGUE

### 🔴 RECHAZADO para "empresa grande / miles de usuarios".
### 🟢 Condicionalmente operable en el uso real actual (single-tenant `textillaura`, baja concurrencia).

**Por qué el matiz importa.** El sistema **funciona hoy en producción sin incidentes** porque el uso real es de 1–3 operarios sobre una instancia por cliente: la concurrencia casi no existe y los abusos de recursos serían accidentes de un ADMIN, no ataques. Pero **el roadmap del negocio (vender instancias, app móvil, multicliente) empuja justo hacia el escenario multiusuario que se rechaza.** La brecha entre "operable hoy" y "aprobable para escalar" es, exactamente, el P0 de abajo.

**La conclusión que reconcilia las dos auditorías:** la auditoría completa confirmó que los **4 críticos de idempotencia de stock (C1–C4) estaban corregidos** — y es cierto para el **doble-click secuencial**. El red-team demostró que esa corrección **es TOCTOU y no resiste concurrencia real** (falta `@Version` en las entidades padre). Es decir: *los críticos están cerrados para el usuario distraído, pero abiertos para dos usuarios simultáneos.* Ese es el hallazgo central del panorama.

---

## 2. Cómo evolucionó el proyecto (línea de tiempo de auditorías)

| Auditoría | Fecha | Titular |
|---|---|---|
| Previa (`AUDITORIA.md`) | 24-jul | 4 críticos de idempotencia de stock + secreto remember-me + XSS OCR. |
| Completa (`AUDITORIA-2026-08-01.md`) | 01-ago | **C1–C4 CORREGIDOS con tests**, A2 remember-me cableado, `@PreAuthorize` en controllers. Quedan A1/A3/A4 + deuda de rendimiento (EAGER/N+1). |
| Red-team (`AUDITORIA-REDTEAM-...`) | 01-ago | C1–C4 **reabiertos bajo concurrencia** (TOCTOU). Nuevos DoS por OOM, sesión no revocable, mass assignment. |

**Trabajo real de remediación confirmado desde el 24-jul:** guards de estado en las 3 confirmaciones (con tests de regresión), `REMEMBER_ME_KEY` obligatoria y cableada punta a punta, `@PreAuthorize` en todos los controllers de escritura, unicidad de número de Programa, zona horaria Perú. Es progreso genuino y bien comentado.

---

## 3. Estado de los CRÍTICOS (reconciliado)

| ID | Origen | Estado según auditoría completa | Estado tras red-team | Neto |
|---|---|---|---|---|
| C1 confirmarRecepcion | 24-jul | ✅ CORREGIDO (guard + test) | ⚠️ TOCTOU bajo concurrencia (R-C1) | **Parcial** |
| C2 confirmarSalida | 24-jul | ✅ CORREGIDO | ⚠️ TOCTOU (R-C1) | **Parcial** |
| C3 confirmarLlegada | 24-jul | ✅ CORREGIDO | ⚠️ TOCTOU (R-C1) | **Parcial** |
| C4 reparto ≤ despachado | 24-jul | ✅ CORREGIDO | ✅ (validación de valor, no de carrera) | **Cerrado** |

**Fix único que los cierra de verdad:** `@Version` en `Recepcion` y `Transferencia` (hoy solo `StockActual` lo tiene). Con lock optimista, la 2.ª confirmación concurrente falla con `OptimisticLockException` y revierte. Esfuerzo: horas.

---

## 4. Tabla maestra de hallazgos abiertos (ambas auditorías, deduplicada)

### 🔴 Críticos / bloqueantes de despliegue multiusuario
| ID | Título | Archivo:línea | Rompe | Esf. |
|---|---|---|---|---|
| **R-C1** | Idempotencia TOCTOU: sin `@Version` en padres → doble stock concurrente | `Recepcion.java`, `Transferencia.java` | Corrupción silenciosa de inventario | Bajo |
| **A1** | Password SUPERADMIN `superadmin` hardcodeada y reproducible (varios hashes en V2/V31/V33/V35) | `db/migration/V33:11`, `V35:16` | Acceso total como proveedor | Medio |
| **A3/A4** | XSS por `innerHTML` con datos de OCR | `recepciones/nueva.html`, `facturar.html` | Ejecución de JS en sesión ADMIN | Medio |
| **R-F1** | Zip-bomb: `readAllBytes()` por entrada antes del cap | `ArchivoHistoricoService.java:87-94` | OOM → caída | Bajo-Medio |
| **R-F2** | Export Excel en memoria sin tope de filas ni rango | `ExcelExportService.java:15`, `ReporteController.java:89-108` | OOM → caída | Medio |
| **R-A1** | Desactivar/degradar/resetear no corta la sesión viva (≤8 h) | `SecurityConfig.java` (sin `SessionRegistry`) | Acceso tras revocación | Medio |
| **R-B4** | Mass assignment: `@ModelAttribute` sobre entidades JPA | `CatalogoController.java` (8 endpoints) | Sobrescritura de filas | Medio |

### 🟠 Altos
| ID | Título | Archivo:línea | Rompe |
|---|---|---|---|
| A5 | `generarNumero` con `count()+1` viola UNIQUE | `TransferenciaService.java:56-58` | Bloquea crear transferencias |
| M5 | `@ManyToOne` masivamente EAGER → N+1 | `StockActual`, `Articulo`, `KardexMovimiento`… | Degradación no lineal de reportes |
| M15 | CI no valida esquema (solo Mockito) | `.github/workflows/ci.yml` | Drift entidad↔esquema estalla en deploy |
| M16 | Backup por cron falla en silencio (`source .env` con `&`) | `lib-cliente.sh:70`, `backup-cliente.sh:44` | Pérdida de datos |
| R-C2 | Integer overflow en `stock.rollos` (solo valida `<0`) | `RecepcionService.java:265-278` | Stock negativo/basura |
| R-B5 | Denegaciones `@PreAuthorize` → 500 + falso `ERROR_SISTEMA` | `GlobalExceptionHandler.java:37-52` | 403→500 + panel contaminado |
| R-P1 | `actualizarPrograma`: listas paralelas sin validar → 500/null/negativos | `ProgramaService.java:200-224` | 500 + dato inconsistente |
| R-F6 | Prompt injection del PDF → catálogo/stock fabricado | `ArchivoHistoricoService.java:182-303` | Corrupción de inventario |
| R-F4 | Parsing OCR sin defensa (NPE/ClassCast/JSON truncado/429) | `AnthropicOcrService.java:168-175` | Error opaco |
| R-J2 | Creación de recepción falla en silencio ante HTML de error | `recepciones/nueva.html:451` | UX rota + confusión stock |
| M11 (front) | UI depende 100% de CDN → rompe PWA offline | `layout/base.html:18-19` | App sin estilos offline/móvil |
| M8 | Sin rate-limit ni lockout en `/login` | nginx / `SecurityConfig` | Fuerza bruta (amplifica A1) |
| D5/D4/D3 | README: omite `REMEMBER_ME_KEY`, HTTPS "pendiente", falta rol ADMIN | `README.md` | Nadie nuevo arranca la app |

### 🟡 Medios (resumen)
A6 race INSERT stock · M1 pertenencia de detalle (recepción/salida) · M2 `solicitada≥recibida` · M3 peso por color · M6 O(n²) en Archivo Histórico · M7 `@PreAuthorize` en `ArchivoHistoricoController`+servicios · M9 CSRF del zip · M10 doble-submit en forms reales · M12(front) ubicaciones hardcodeadas · M14 duplicación nuevo/editar · M17 TZ de contenedores MySQL · M18 Spring Boot 3.3.5 · R-F3 descargarZip en memoria · R-F5 timeout socket no total · R-S1 catch sin log · R-A2 remember-me irrevocable individual · R-A3 política de clave débil · R-D1 CI sin `permissions`/`timeout`/dep-scan · listas paralelas → `List<DTO>`.

### 🟢 Bajos (resumen)
B1 `orElseThrow` sin mensaje · B5 voseo (regresión) · B6 `alt` · B7 `index.html` huérfano · B8 almacen no extiende base · B9 comentarios · B12/B13 `@PreAuthorize` errores + logout POST · B14 FK/índice kardex · B15 enums nativos · B18 Adminer en prod · N5 accesibilidad (aria) · R-C3 deadlock por orden de locks · R-F7 cuota de disco · R-F8 `Content-Disposition`/magic-bytes · R-J1 SW cache-first stale · R-J3 submit sin bloqueo · R-D2 Dockerfile sin HEALTHCHECK/heap · dedupe `normalizar()` · enums de roles/tipos.

---

## 5. Calificaciones unificadas (0–10)

Se toma la calificación **del red-team** donde recalibró a la baja (es la más exigente y actual); el resto viene de la auditoría completa.

| Dimensión | Nota | Comentario |
|---|:--:|---|
| Arquitectura | 6.5 | Capas limpias y DI correcta; bajan mass assignment, listas paralelas, God-controller. |
| Backend | 6.5 | Críticos secuenciales cerrados; R-C1 reabre integridad, overflow, `actualizarPrograma`. |
| Frontend | 5.5 | Sin XSS server-side; duplicación, CDN sin precache, fallo silencioso R-J2. |
| Base de datos | 6.5 | Buen esquema; falta `@Version` en padres, EAGER, FK/índice kardex, TZ. |
| **Seguridad** | **6.0** | Fundamentos sólidos; A1, A3/A4, R-A1, R-B4, R-F6 pesan. |
| Performance | 5.0 | Dos OOM triviales + EAGER/N+1 + export en memoria. |
| Escalabilidad | 5.0 | R-C1 escala con concurrencia; OOM con volumen. |
| Testing | 4.5 | Núcleo transaccional bien cubierto; 0 en concurrencia/límites/autorización/integración. |
| UX | 6.5 | Flujos pensados; `alert()`, fallo silencioso, textos hardcodeados. |
| UI | 7.5 | Bootstrap coherente, dark mode, sidebar; index huérfano. |
| Documentación | 6.5 | DEPLOY/STAGING de referencia; README desactualizado. |
| Mantenibilidad | 6.0 | Módulos cohesivos; duplicación, clases grandes, strings mágicos. |
| Código | 6.0 | Legibilidad alta (8.5) contrapesada por smells estructurales. |
| Organización | 7.5 | Paquete por dominio muy coherente. |
| **Preparación producción** | **4.5** | Empresa grande: RECHAZO. Single-tenant actual: ~7 con el P0. |

**Nota global — estándar "empresa grande": ~5.8 / 10.**
**Nota global — estándar "herramienta interna single-tenant": ~7.5 / 10.**
La diferencia entre ambas ES el trabajo pendiente de este documento.

---

## 6. Roadmap único priorizado

### 🔴 Crítico — cerrar ANTES de sumar usuarios concurrentes o vender instancias
1. **R-C1** — `@Version` en `Recepcion` y `Transferencia` (+migración). *Cierra la integridad de stock de verdad.* **Bajo.**
2. **A1** — rotar `jlynch` en `textillaura` + sacar la clave por defecto del flujo de provisión (`must_change_password`). **Medio.**
3. **A3/A4** — `textContent`/`escapeHtml` en el OCR (recepción/facturación) + CSP. **Medio.**
4. **R-F1** — zip-bomb: streaming con tope por-entrada + ratio guard. **Bajo-Medio.**
5. **R-F2** — Excel: `SXSSFWorkbook` + DTO + tope de filas/rango obligatorio. **Medio.**
6. **R-A1** — `SessionRegistry` + `expireNow` en inactivar/cambio-de-rol/reset. **Medio.**
7. **R-B4** — DTOs o `@InitBinder` en `CatalogoController`. **Medio.**

### 🟠 Alto
A5 `generarNumero` sin `count()` · M15 CI con MySQL/Testcontainers · M5 EAGER→LAZY+JOIN FETCH · M16 backup `grep|cut` · R-C2 overflow (`Math.addExact`) · R-B5 handler de `AccessDeniedException` (403, sin `ERROR_SISTEMA`) · R-P1 validar `actualizarPrograma` · R-F6 OCR como propuesta a validar · R-F4 parseo OCR defensivo · R-J2 try/catch en `resp.json()` · M11 Bootstrap local + precache · M8 rate-limit login · corregir README (D3/D4/D5).

### 🟡 Medio
M1 pertenencia · M3 peso por color · M2 `solicitada≥recibida` · A6 race INSERT · M6 indexar dedup Archivo Histórico · M17 TZ contenedores · M7/M9/M10 defensa en profundidad + CSRF zip + doble-submit · M12/M14 de-hardcodear ubicaciones + unificar JS programas · M18 Spring Boot · R-F3/R-F5/R-S1/R-A2/R-A3/R-D1 · migrar listas paralelas a `List<DTO>`.

### 🟢 Bajo
B5 voseo · B7 index huérfano · B8 almacen base · accesibilidad (aria/alt) · enums→VARCHAR · Adminer fuera de prod · B12/B13 · R-C3/R-F7/R-F8/R-J1/R-J3/R-D2 · dedupe `normalizar()` · enums de roles/tipos · refactors estructurales (partir `CatalogoController` 680 líneas, extraer `RecepcionDocumentoService`) · docs (manual de usuario, diagrama de estados, CHANGELOG).

---

## 7. Informe ejecutivo final

**Resumen.** TexControl es un proyecto de **buen artesano**: dominio de negocio bien modelado, código muy legible, y los críticos "de manual" (idempotencia secuencial, secreto remember-me) resueltos con criterio y tests. La auditoría completa lo confirma. El red-team, atacando concurrencia, recursos y abuso, encuentra que **la integridad de stock y la disponibilidad no están probadas bajo condiciones multiusuario** — justo el terreno al que apunta el negocio.

**Fortalezas.** Legibilidad y comentarios "por qué" excepcionales; roles y aislamiento SUPERADMIN correctos (sin escalada, sin IDOR entre clientes, session-fixation cubierto, CORS cerrado); remember-me criptográficamente correcto **y que honra la desactivación**; SQL parametrizado; zip-slip y sanitización de fórmulas Excel bien hechos; Dockerfile no-root multi-stage; disciplina de git y `.gitignore` limpios; documentación de infraestructura (DEPLOY/STAGING) de referencia.

**Debilidades principales.** (1) Guards de estado sin `@Version` → integridad frágil bajo carga. (2) Flujos que cargan datos ilimitados en heap (ZIP, Excel, descarga) → OOM triviales. (3) Sin gestión de sesión → revocación no efectiva. (4) Binding de entidades JPA sin whitelist → mass assignment. (5) OCR tratado como fuente de verdad → prompt injection. (6) Denegaciones de permiso convertidas en 500 que contaminan el diagnóstico. (7) Testing sin concurrencia/límites/autorización. (8) README desactualizado y deuda de rendimiento (EAGER/N+1).

**Riesgos para producción.** Corrupción silenciosa de inventario (la peor: sin error), caída por OOM disparable por cualquier autenticado, y acceso persistente de usuarios revocados. Bajo en single-tenant actual; inaceptable en el escenario objetivo.

**Deuda técnica estimada.** ~3–4 semanas-persona para P0+P1. Acotada y bien localizada — **no** una reescritura. R-C1 es horas; los OOM y la sesión, días cada uno; el resto es cola larga.

**Decisión.** **RECHAZO** para "empresa grande / miles de usuarios" en el estado actual. **APRUEBO condicionalmente** la operación single-tenant ya en vivo, siempre que se cierre el P0 antes de sumar usuarios concurrentes o vender instancias.

- **Antes de liberar:** R-C1, A1, A3/A4, R-F1, R-F2, R-A1, R-B4.
- **Puede esperar:** la mayoría de los 🟠/🟡 (OCR defensivo, remember-me persistente, CI hardening, rendimiento EAGER).
- **Puede posponerse:** los 🟢 y los refactors estructurales.

**Nota final: 5.8 / 10** para el estándar de despliegue en empresa grande; **~7.5 / 10** para el estándar de herramienta interna single-tenant en operación. La distancia entre ambas es medible y el camino para cerrarla está en el roadmap de la sección 6.

**Opinión profesional.** No es un proyecto para rechazar y reescribir — es un proyecto para **endurecer antes de escalar**. El autor demuestra criterio (los comentarios SEC/ARQ, las decisiones justificadas, la remediación real entre auditorías lo prueban). Lo que falta no es talento sino la disciplina de ingeniería que solo se vuelve obligatoria al pasar de "una instancia que yo opero" a "un producto que le vendo a terceros con usuarios concurrentes": pruebas de concurrencia y de límites, gestión de sesión, binding explícito, y presupuestos de recursos. Cerrado el P0, este sistema pasa con holgura la barra de "producto vendible por instancia".

---

## 8. Documentos fuente

- `AUDITORIA.md` — auditoría del 24-jul (base histórica, `main@a471205`).
- `AUDITORIA-2026-08-01.md` — auditoría técnica completa (dimensiones + estado de hallazgos previos).
- `AUDITORIA-REDTEAM-2026-08-01.md` — red-team / auditoría de despliegue (hallazgos nuevos + veredicto).
- **Este documento** — consolidación de las dos últimas para panorama completo.

*Consolidado sobre `develop`@`d24f542`. Cada hallazgo verificado contra el código fuente real.*
