# Roadmap de TexControl

Definido por el dueño (ago-2026), sobre la base del diseño original V1→V5
(documentos de Drive, jun-2026) pero **reordenado**: la cadena productiva
(hilado → tejido → teñido) va ANTES que las ventas. La BD se diseñó para que
cada versión sea **aditiva** (tablas nuevas, sin reescribir stock ni kardex).

### Relación con el «Roadmap Oficial v1.0» (PDF, 5-ago-2026)

Existe un documento formal de 22 páginas con auditoría, gates, métricas y
calendario. **Numera distinto**, así que antes que nada, el mapa:

| Este archivo | PDF oficial | Contenido |
|---|---|---|
| V1 | **V0.1** | Núcleo actual (+ WhatsApp, según el PDF) |
| V2 | **V0.2** | Hilo, tejeduría, teñido |
| V3 | **V0.3** | Clientes, ventas, rollos abiertos |
| V4 | **V0.4** | Créditos y cobranzas |
| V5 | **V0.5–V0.7** | Automatización, QR, planeamiento/IA |
| — | **V1.0** | Liberación comercial |

La auditoría del PDF **se verificó contra el código y da**: SBOM+Trivy no
bloqueantes, 46 migraciones, los cuatro tipos de kardex, el peso promedio por
rollo en `TransferenciaService`, ausencia de adaptador Meta, `VENDEDOR` sin
permisos. Su sección 4.1 llegó por su cuenta a los mismos dos problemas de
modelo que están más abajo en «Tres cosas que NO aguantan V2» — dos lecturas
independientes, el mismo diagnóstico.

**La secuencia del PDF es correcta y se adopta.** Lo que sigue son cuatro
correcciones de *ejecución*, no de dirección.

---

## ⚠️ Correcciones al roadmap oficial v1.0

### 0. El cuello de botella no es programar: es tener un cliente operando

Esto condiciona todo lo demás y el PDF no lo dice. Mirá qué exigen sus propios
gates:

- **G3** (cierre V0.2): *«3 ciclos reales E2E y conciliaciones firmadas»*
- **G4** (cierre V0.3): *«cierre diario, muestra física»*
- Métrica: *«exactitud física vs. sistema ≥99% en muestra controlada»*

Los tres necesitan **un cliente real operando**. Al 6-ago-2026 hay **cero**
(`textillaura` y `textilcamargo` se dieron de baja el 5-ago; corre solo el demo).

V0.2 se puede **construir** sin cliente. **No se puede cerrar**: su gate pide tres
ciclos productivos reales conciliados, y un ciclo textil no dura una semana.
Arrancar V0.2 en octubre sin cliente da, en febrero, el código listo y el gate
igual de abierto.

Lo mismo con el riesgo R2 y la política de pesaje: *«el software no puede inventar
el peso que la operación no captura»*. Vender por kilo exacto (V0.3) necesita
balanzas y disciplina en el almacén **del cliente**. No se resuelve programando.

> **Consecuencia práctica**: dar de alta un cliente que opere de verdad es tarea
> del camino crítico, a la par del desarrollo — no algo que se acomoda después.

### 1. WhatsApp sale del camino crítico de V0.1

El PDF lo pone como **P0 bloqueante** para cerrar V0.1 (WA-01…WA-05). No conviene:
la verificación de negocio en Meta es un trámite **externo**, tarda semanas y puede
fallar — el propio PDF lo marca como riesgo R1, de impacto alto.

El correo ya funciona (`NotificadorEmailSmtp` es `@Primary`, canal activo). Meter
una dependencia externa que no controlamos como bloqueo para certificar el núcleo
es agregarle riesgo ajeno al gate propio.

→ **Certificar V0.1 con correo.** WhatsApp entra como V1.x cuando Meta apruebe, con
el mismo estándar (outbox persistente, idempotency key, webhooks de estado): el
requisito no se baja, se desacopla del gate.

### 2. El kardex generalizado va PRIMERO, antes de cualquier tabla de V0.2

El PDF identifica el problema (4.1, «Kardex limitado») pero **no lo agenda**. Es lo
primero que hay que hacer, no un pendiente transversal.

Si se construyen las ~26 tablas de V0.2 sobre el kardex actual, se termina con
cinco columnas de documento —cuatro siempre NULL— y migrando la tabla más grande
del sistema **con datos reales encima**. El detalle está abajo, en «Tres cosas que
NO aguantan V2».

→ **Orden obligatorio**: `(tipo_documento, documento_id)` + `TRANSFORMACION_IN/OUT`
+ la decisión sobre `color_id`/unidad de medida **antes** de la primera tabla de hilo.

### 3. V0.2 se parte en tres gates, no uno

Hilo + tejeduría + teñido + costos bajo un solo gate es demasiado: si los costos se
atrasan —y son la parte más riesgosa, riesgo R7 del PDF— se traba todo lo demás,
que ya podría estar dando valor.

| Sub-gate | Contenido | Por qué corta ahí |
|---|---|---|
| **V0.2a** | Proveedores desacoplados + kardex generalizado + lotes de hilo | Es la base: sin esto lo demás se construye torcido |
| **V0.2b** | Tejeduría, tela cruda, gramaje, mermas | Cierra la primera transformación completa |
| **V0.2c** | Teñido genérico, partidas, reprocesos, costos | Enchufa con los programas y recepciones que V1 ya tiene |

Cada uno cerrable por separado, con su propia conciliación.

### 4. Costo estimado primero, costo real después

La sección 7.5 del PDF pide costo real completo dentro de V0.2: versionado de
costos, multi-moneda, tipo de cambio y prorrateo de gastos indirectos. Eso es una
especialidad en sí misma y es lo que más puede atrasar V0.2c.

→ Arrancar con **costo estimado** (hilo + tarifa de servicio, sin prorrateos) y
dejar el costo real versionado para un gate propio. La regla que sí es innegociable
desde el día uno es la del PDF: *«cada corrección crea una nueva versión de costo
sin alterar el valor histórico usado por ventas anteriores»* — el modelo tiene que
nacer preparado para versionar aunque al principio guarde una sola versión.

### Sobre las fechas

El PDF asume *«un desarrollador principal con asistencia de IA»* y da 5 meses a
V0.2. Las secciones 7.3 y 7.4 listan **~26 tablas nuevas**; el sistema entero hoy
tiene **29 entidades**. O sea: V0.2 casi duplica el modelo de dominio completo en
cinco meses.

Además, la sección 12.1 lista **ocho decisiones funcionales sin cerrar** (gramajes,
tratamiento del blanco, recetas de polycotton, mermas toleradas, política de
pesaje…). Diseñar un dominio que todavía no está definido es donde la IA menos
acelera: no es escribir código, es decidir reglas de negocio con el dueño del proceso.

→ Estimación realista: **V0.2 en 8–10 meses**, no 5. Y **V1.0 hacia mediados de
2028**, no Q4 2027. La secuencia no cambia; el calendario sí.

> Ninguna de estas cuatro correcciones baja el estándar del PDF. Reordenan qué va
> antes y separan lo que no depende de nosotros de lo que sí.

---

## ✅ V1 — Control de Inventario (EN PRODUCCIÓN)

Todo lo desarrollado hasta hoy:

- **Núcleo**: recepciones en 4 pasos con **OCR por IA** (autocompletado desde la
  guía), programas de teñido, transferencias con doble confirmación, stock por
  ubicación, kardex, reportes Excel, archivo histórico masivo.
- **Plataforma**: usuarios/roles con jerarquía (SUPERADMIN→ADMIN→GERENTE→
  SUPERVISOR→VENDEDOR), auditoría, dashboard, PWA instalable.
- **Modelo de negocio**: multicliente (instancia + BD aislada por cliente,
  subdominio propio), demo público (`demo.texcontrol.pe`), staging (`dev.`),
  toolbox completo de operación (alta/baja/backup/fotos de clientes).
- **Alertas**: stock bajo por **correo** (SMTP); SMS Twilio quedó como
  implementación alternativa sin cablear.
- **Solidez**: sprint de hardening completo (locks optimistas, CSRF, CSP,
  validación PDF, límite OCR concurrente, backups verificados, CI con MySQL real).

### V1.x — Mejoras continuas (sin cambiar de versión)

- Notificaciones por **WhatsApp** (Meta Cloud API, tarifa PEN 0.0665/msg
  verificada): despachador genérico de eventos — stock bajo, movimiento por
  validar (con foto del almacenero), tela en tránsito. Requiere trámite de
  verificación del negocio en Meta (arrancar en paralelo, demora días/semanas).
  **Queda en V1.x a propósito, no como bloqueante del cierre de V0.1** — ver
  corrección 1. El estándar del PDF se mantiene: outbox persistente con
  idempotency key, webhooks de estado (enviado/entregado/leído/fallido) y dedupe
  durable, no en memoria.
- **Correo en el alta/edición de usuarios** (como el celular de V44) y alertas
  dirigidas a los ADMIN/GERENTE de cada instancia en vez de lista fija en config.
- Marketing en `texcontrol.pe` (hoy la raíz redirige al lanzador).
- Upgrade VPS a 8 GB al entrar el 3.er cliente pagando (~US$40/mes).

---

## 🔜 V2 — Hilado y Producción (compra de hilo → tejido → teñido)

**Objetivo**: controlar la cadena productiva completa ANTES de la venta —
del hilo comprado a la tela teñida que ya recibe V1.

> **Se ejecuta en tres sub-gates** (V0.2a base + V0.2b tejeduría + V0.2c teñido y
> costos), no de una sola vez — ver corrección 3. Y el kardex generalizado va
> ANTES de la primera tabla de acá — ver corrección 2.

### Alcance

1. **Compra de hilo**
   - Registro de compras por lote/contenedor (proveedor, kg, título del hilo).
   - Stock de hilo crudo disponible.

2. **Transformación hilo → tejido (tejeduría)**
   - Órdenes de tejido: hilo consumido → tela cruda producida.
   - Diferenciando en cada orden:
     - **Tipo de tela**: RIB 2x1, RIB 1x1.
     - **Gramaje/título**: 20/1, 30/1 (y los que se sumen).
     - **Acabado**: LISO, LISTADO BLANCO, LISTADO… — y si es **LISTADO**,
       registrar además el **polycotton** correspondiente (el material de las
       listas se consume aparte del hilo principal y debe quedar trazado).
   - Stock de tela cruda por artículo.

3. **Envío a teñido (tintorería)**
   - La tela cruda sale a la tintorería vinculada a un **programa de teñido**
     — cierra el círculo con los programas y recepciones que V1 ya maneja:
     `hilo → tejido → tela cruda → programa → recepción teñida (V1)`.

### ⚠️ Regla de diseño clave: proveedores CONFIGURABLES por cliente

**FRAHANS (tejeduría) y FAST DYE (tintorería) son los proveedores de UN
cliente, no del sistema.** Cada instancia registra SUS propias empresas de
servicio (catálogo de proveedores por tipo: hilo / tejeduría / tintorería).
Nada de nombres hardcodeados en flujos ni pantallas.

- Implicancia OCR: el `SYSTEM_PROMPT` actual está afinado a las guías de
  FAST DYE. Para clientes con otra tintorería se necesita plantilla/prompt de
  extracción **por proveedor** (configurable o seleccionable).

### Tablas nuevas (aditivas)

`proveedores` (tipo: hilo/tejeduria/tintoreria), `compras_hilo`,
`ordenes_tejido` (+ detalle con tipo tela, título, acabado, polycotton),
`stock_tela_cruda`, `envios_tinte` (vínculo a `programas`).

### ⚠️ Tres cosas del modelo actual que NO aguantan V2

Esto hay que resolverlo **en el diseño de V2, antes de escribir código**. Migrar
estas tablas con historial real de tres clientes cargado se paga una sola vez, y
conviene que sea antes de que ese historial exista.

**1. El kardex tiene una columna por tipo de documento.**
```java
private Long recepcionDetalleId;   // FK a recepcion_detalles
private Long transferenciaId;      // FK a transferencias
```
Hoy son dos. V2 suma compra de hilo, orden de tejido y envío a tintorería: serían
**cinco columnas, cuatro siempre NULL en cada fila**, más cinco FK y cinco índices.
Cada módulo nuevo obliga a un `ALTER TABLE` sobre la tabla más grande del sistema.
→ Reemplazar por `(tipo_documento VARCHAR, documento_id BIGINT)`, con la migración
que traduzca las dos columnas actuales. Se pierde la FK real; a cambio, agregar un
tipo de movimiento deja de tocar el esquema.

**2. `TipoMovimiento` no sabe expresar una transformación.**
Hoy: `INGRESO, TRANSFERENCIA_OUT, TRANSFERENCIA_IN, AJUSTE`. Todos mueven **el
mismo material** de un lado a otro o lo ajustan. V2 es otra cosa: entran 100 kg de
hilo y salen 80 kg de tela cruda — **un material se consume y aparece otro
distinto**, y los dos movimientos son las dos caras de un mismo hecho.
→ Sumar `TRANSFORMACION_IN` / `TRANSFORMACION_OUT` ligados por un id de operación,
para que el kardex pueda responder "de qué hilo salió este rollo" (que es la
trazabilidad que pide V5).

**3. El stock exige color y exige rollos.**
```sql
color_id BIGINT NOT NULL      -- el hilo crudo NO tiene color: se tiñe después
rollos   INT    NOT NULL      -- el hilo se compra y se consume en KILOS
```
Con el modelo de hoy hay que inventar un color "CRUDO" y guardar `rollos = 0` con
peso real. Lo primero es el mismo parche silencioso que ya mordió dos veces con
`''` vs NULL (ver "Blanco = NULL"); lo segundo hace que **todo reporte que sume
rollos mienta**.
→ Decidir en el diseño: o `color_id` pasa a NULL-able y se agrega una unidad de
medida al material, o el hilo y la tela cruda llevan su propia tabla de stock. Lo
que NO se puede es meterlos a la fuerza en `stock_actual` como está.

**Decisión de fondo que ordena las tres**: si `Articulo` se generaliza a "material"
(hilo / tela cruda / tela teñida, cada uno con su unidad y sus atributos) o si cada
etapa lleva su propia entidad. Generalizar cuesta más al principio y hace que kardex,
stock, reportes y alertas sirvan para los tres sin duplicarse. Separar es más rápido
de arrancar y termina en tres kardex y tres reportes de stock que hay que mantener
sincronizados a mano.

---

## V3 — Ventas

(El "V2" del diseño original de Drive.)

- Clientes (identificados y mostrador), ventas, venta_detalle.
- **Regla del rollo abierto/sobrante**: venta por kg — si hay un rollo abierto
  con 8 kg y piden 15, se abre rollo nuevo y los 8 quedan como sobrante.
  Requiere tabla `rollos_abiertos` (rollos individuales con peso restante).
- Nuevo movimiento de kardex: VENTA.
- Activa por fin el rol **VENDEDOR** (existe desde V1 sin permisos).

## V4 — Créditos y cobranzas

- Cuentas por cobrar, pagos parciales.
- Reporte de antigüedad de deuda (30/60/90 días).
- Reportes de consumo histórico por artículo/color que alimenten la
  programación de teñido.

## V5 — Costos, planeamiento y trazabilidad

- **Costos y rentabilidad**: con la cadena de V2 (hilo + tejido + teñido) y
  las ventas de V3 ya registradas → costo unitario por artículo/lote vs
  precio de venta.
- **Planeamiento de teñido proactivo**: sugerencias automáticas según demanda
  histórica y stock mínimo.
- **Trazabilidad individual por rollo** con QR (tabla `rollos`, escaneo con la
  cámara desde la PWA, balanzas digitales opcionales).
- Posibles integraciones: facturación electrónica, contabilidad.

> Nota: del V5 original ya se adelantaron a V1 el **OCR/IA** y la **PWA**.

---

## Referencias

- **Roadmap Oficial de Producto y Tecnología 2026-2027, v1.0** (PDF, 5-ago-2026,
  22 pág.): auditoría del repo, matriz de capacidades, gates G0–G5, métricas
  objetivo, riesgos R1–R8 y backlog priorizado. Es la referencia formal; este
  archivo es la versión que vive junto al código, con las cuatro correcciones de
  ejecución de arriba. **Si los dos difieren, gana este** (se actualiza con cada
  commit; el PDF es una foto del 5-ago).
- Diseño original (Drive, jun-2026): documentos 01 (arquitectura y modelo de
  datos), 02 (especificación funcional), 03 (roadmap V1→V5 original,
  escalabilidad, estrategia QR e IA).
- Operación e infraestructura: `CLAUDE.md`, `DEPLOY.md`, `DEMO.md`, `STAGING.md`.
