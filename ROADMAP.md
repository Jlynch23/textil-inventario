# Roadmap de TexControl

Definido por el dueño (ago-2026), sobre la base del diseño original V1→V5
(documentos de Drive, jun-2026) pero **reordenado**: la cadena productiva
(hilado → tejido → teñido) va ANTES que las ventas. La BD se diseñó para que
cada versión sea **aditiva** (tablas nuevas, sin reescribir stock ni kardex).

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
- **Correo en el alta/edición de usuarios** (como el celular de V44) y alertas
  dirigidas a los ADMIN/GERENTE de cada instancia en vez de lista fija en config.
- Marketing en `texcontrol.pe` (hoy la raíz redirige al lanzador).
- Upgrade VPS a 8 GB al entrar el 3.er cliente pagando (~US$40/mes).

---

## 🔜 V2 — Hilado y Producción (compra de hilo → tejido → teñido)

**Objetivo**: controlar la cadena productiva completa ANTES de la venta —
del hilo comprado a la tela teñida que ya recibe V1.

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

- Diseño original (Drive, jun-2026): documentos 01 (arquitectura y modelo de
  datos), 02 (especificación funcional), 03 (roadmap V1→V5 original,
  escalabilidad, estrategia QR e IA).
- Operación e infraestructura: `CLAUDE.md`, `DEPLOY.md`, `DEMO.md`, `STAGING.md`.
