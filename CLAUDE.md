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
  aplicada; sumar una nueva con el siguiente número (actualmente van hasta **V35**).
- `ddl-auto: validate`: si una entidad no calza con el esquema migrado, la app no arranca.
- `baseline-on-migrate: true`.

## Tests

`src/test/java/...`, JUnit 5 + Spring Boot Test. Concentrados en la lógica de servicio:
`RecepcionServiceTest`, `ArticuloMatchingServiceTest`, `TransferenciaServiceTest`,
`CatalogoServiceTest`, `ArchivoHistoricoServiceTest`. CI (`.github/workflows/ci.yml`) corre
`mvn -B clean compile` + `mvn -B test` en cada push/PR a `main`.

## Convenciones

- Código, nombres de paquete, comentarios y textos de UI están **en español** — mantener ese idioma.
- Credenciales **nunca** hardcodeadas: siempre variables de entorno (ver `application.yml`).
- Despliegue en VPS documentado en `DEPLOY.md` (Docker: MySQL + app + Nginx, acceso por Tailscale,
  `docker-compose.prod.yml`).
