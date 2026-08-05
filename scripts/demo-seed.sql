-- ============================================================================
-- SEED DEL AMBIENTE DEMO (clientes potenciales) -- demo.texcontrol.pe
-- ============================================================================
-- Datos de ejemplo para que un prospecto vea el sistema "lleno" y funcionando
-- (catalogo poblado + algo de stock) y 10 cuentas de prueba, una por persona.
--
-- NO es una migracion Flyway: se aplica SOLO a la base del cliente 'demo'
-- (db_demo) con scripts/nuevo-demo.sh o scripts/resetear-demo.sh. Ponerlo como
-- migracion lo sembraria en TODOS los clientes reales -- que es justo lo que no
-- queremos.
--
-- Es IDEMPOTENTE: usa INSERT IGNORE y subconsultas por nombre (no IDs fijos),
-- asi que correrlo dos veces no duplica ni rompe nada. Los IDs auto_increment
-- se resuelven al vuelo (SELECT ... WHERE nombre=...), robusto ante el orden.
--
-- El esquema lo define Flyway (hasta V44); este archivo asume ese esquema ya
-- migrado. Roles: SUPERADMIN=1, GERENTE=2, SUPERVISOR=3, VENDEDOR=4, ADMIN=5.
-- ============================================================================

-- --- EMPRESA -----------------------------------------------------------------
-- El subtitulo bajo el logo TEXCONTROL sale de las empresas ACTIVAS (unidas por
-- " & "). Con una sola, el prospecto ve un nombre de negocio de ejemplo.
INSERT IGNORE INTO empresas (nombre, ruc, carpeta, activo) VALUES
  ('TEXTIL DEMO S.A.C.', '20000000001', 'demo', TRUE);

-- --- UBICACIONES -------------------------------------------------------------
-- Una principal (Praderas, de donde salen las salidas) + almacenes y tiendas.
INSERT IGNORE INTO ubicaciones (codigo, nombre, tipo, es_principal, activo) VALUES
  ('PRADERAS', 'Praderas',      'ALMACEN', TRUE,  TRUE),
  ('1006',     'Almacen 1006',  'ALMACEN', FALSE, TRUE),
  ('138',      'Tienda 138',    'TIENDA',  FALSE, TRUE),
  ('139',      'Tienda 139',    'TIENDA',  FALSE, TRUE);

-- --- CATALOGO BASE -----------------------------------------------------------
INSERT IGNORE INTO tipos_tela (nombre, descripcion, activo) VALUES
  ('RIB 2X1',       'Rib 2x1',          TRUE),
  ('RIB 1X1',       'Rib 1x1',          TRUE),
  ('RIB ACANALADO', 'Rib acanalado',    TRUE);

INSERT IGNORE INTO titulos (valor, descripcion, activo) VALUES
  ('24/1', 'Hilo peinado 24/1', TRUE),
  ('30/1', 'Hilo peinado 30/1', TRUE),
  ('20/1', 'Hilo peinado 20/1', TRUE);

-- Mismos nombres que el catalogo real (V25) para que el matching del OCR calce.
INSERT IGNORE INTO composiciones (nombre, descripcion, activo) VALUES
  ('ALGODON',     'Algodon peinado 100%',            TRUE),
  ('MELANGE 10%', 'Mezcla con 10% de fibra adicional', TRUE),
  ('MELANGE 3%',  'Mezcla con 3% de fibra adicional',  TRUE);

-- Mismos nombres que el catalogo real (V29).
INSERT IGNORE INTO acabados (nombre, descripcion, activo) VALUES
  ('LISO',           'Acabado estandar, sin textura ni listas (defecto)', TRUE),
  ('ACANALADO',      'Acanalado (en la practica solo sobre RIB 2X1)',     TRUE),
  ('LISTADO BLANCO', 'Listado con base/listas blancas',                   TRUE);

-- Colores: nombre_oficial (el que usa el OCR) + apodo corto (lo que ve la gente).
INSERT IGNORE INTO colores (nombre_oficial, codigo_fast_dye, apodo, activo) VALUES
  ('Negro',           '100001', NULL,  TRUE),
  ('Blanco',          '200001', NULL,  TRUE),
  ('Verde Botella',   '132015', NULL,  TRUE),
  ('Turqueza Medio',  '221963', 'TURQ', TRUE),
  ('Rata',            '300001', NULL,  TRUE),
  ('Melange 10%',     '300002', 'MLG10', TRUE),
  ('Rojo',            '400001', NULL,  TRUE),
  ('Azulino',         '221001', NULL,  TRUE);

-- --- ARTICULOS ---------------------------------------------------------------
-- Un articulo = combinacion unica (tipo_tela, titulo, composicion, acabado).
-- Se insertan por SELECT para resolver los IDs por nombre (idempotente por el
-- codigo_interno unico y por la constraint de la combinacion).
INSERT IGNORE INTO articulos (codigo_interno, tipo_tela_id, titulo_id, composicion_id, acabado_id, activo)
SELECT 'DEMO-001', tt.id, t.id, c.id, a.id, TRUE
FROM tipos_tela tt, titulos t, composiciones c, acabados a
WHERE tt.nombre='RIB 2X1' AND t.valor='30/1' AND c.nombre='ALGODON' AND a.nombre='LISO';

INSERT IGNORE INTO articulos (codigo_interno, tipo_tela_id, titulo_id, composicion_id, acabado_id, activo)
SELECT 'DEMO-002', tt.id, t.id, c.id, a.id, TRUE
FROM tipos_tela tt, titulos t, composiciones c, acabados a
WHERE tt.nombre='RIB 1X1' AND t.valor='24/1' AND c.nombre='ALGODON' AND a.nombre='LISO';

INSERT IGNORE INTO articulos (codigo_interno, tipo_tela_id, titulo_id, composicion_id, acabado_id, activo)
SELECT 'DEMO-003', tt.id, t.id, c.id, a.id, TRUE
FROM tipos_tela tt, titulos t, composiciones c, acabados a
WHERE tt.nombre='RIB 2X1' AND t.valor='30/1' AND c.nombre='MELANGE 10%' AND a.nombre='LISO';

INSERT IGNORE INTO articulos (codigo_interno, tipo_tela_id, titulo_id, composicion_id, acabado_id, activo)
SELECT 'DEMO-004', tt.id, t.id, c.id, a.id, TRUE
FROM tipos_tela tt, titulos t, composiciones c, acabados a
WHERE tt.nombre='RIB ACANALADO' AND t.valor='30/1' AND c.nombre='ALGODON' AND a.nombre='ACANALADO';

INSERT IGNORE INTO articulos (codigo_interno, tipo_tela_id, titulo_id, composicion_id, acabado_id, activo)
SELECT 'DEMO-005', tt.id, t.id, c.id, a.id, TRUE
FROM tipos_tela tt, titulos t, composiciones c, acabados a
WHERE tt.nombre='RIB 2X1' AND t.valor='20/1' AND c.nombre='ALGODON' AND a.nombre='LISTADO BLANCO';

-- --- STOCK ACTUAL ------------------------------------------------------------
-- Algo de stock repartido en Praderas y una tienda, para que Inventario y los
-- reportes muestren datos. version=0 (control optimista); updated_at=ahora.
-- Unico por (articulo, ubicacion, color): INSERT IGNORE evita choques al re-sembrar.
INSERT IGNORE INTO stock_actual (articulo_id, color_id, ubicacion_id, rollos, peso_kg, version, updated_at)
SELECT ar.id, co.id, ub.id, s.rollos, s.peso, 0, NOW()
FROM (
    SELECT 'DEMO-001' AS art, 'Negro'          AS col, 'PRADERAS' AS ubi, 40 AS rollos, 920.00 AS peso UNION ALL
    SELECT 'DEMO-001',         'Blanco',              'PRADERAS',        35,        805.00        UNION ALL
    SELECT 'DEMO-001',         'Rojo',                'PRADERAS',         8,        184.00        UNION ALL
    SELECT 'DEMO-002',         'Negro',               'PRADERAS',        22,        506.00        UNION ALL
    SELECT 'DEMO-002',         'Turqueza Medio',      'PRADERAS',        15,        345.00        UNION ALL
    SELECT 'DEMO-003',         'Melange 10%',         'PRADERAS',        28,        644.00        UNION ALL
    SELECT 'DEMO-004',         'Verde Botella',       'PRADERAS',        12,        276.00        UNION ALL
    SELECT 'DEMO-005',         'Blanco',              'PRADERAS',        18,        414.00        UNION ALL
    SELECT 'DEMO-001',         'Negro',               '138',              6,        138.00        UNION ALL
    SELECT 'DEMO-002',         'Turqueza Medio',      '139',              4,         92.00
) s
JOIN articulos   ar ON ar.codigo_interno = s.art
JOIN colores     co ON co.nombre_oficial = s.col
JOIN ubicaciones ub ON ub.codigo         = s.ubi;

-- --- PROGRAMAS DE TEÑIDO -----------------------------------------------------
-- Tres programas COMPLETOS (todas sus lineas cargadas, total_rollos = suma de
-- las lineas, como valida ProgramaService). Estados distintos para que la
-- pantalla de seguimiento luzca con datos reales:
--   PROG-2026-001: terminado (recibido 100%).
--   PROG-2026-002: en curso (recibido parcial).
--   PROG-2026-003: pendiente (sin recibir) -> sirve para DEMOSTRAR una
--                  recepcion contra programa con saldo disponible.
-- Fechas relativas a hoy (CURDATE()) para que el demo nunca se vea viejo.
INSERT IGNORE INTO programas (numero, empresa_id, fecha, total_rollos, observaciones, created_at)
SELECT 'PROG-2026-001', e.id, CURDATE() - INTERVAL 21 DAY, 60,
       'Programa de ejemplo terminado: todo recibido.', NOW()
FROM empresas e WHERE e.ruc = '20000000001';

INSERT IGNORE INTO programas (numero, empresa_id, fecha, total_rollos, observaciones, created_at)
SELECT 'PROG-2026-002', e.id, CURDATE() - INTERVAL 10 DAY, 60,
       'Programa de ejemplo en curso: recibido parcial.', NOW()
FROM empresas e WHERE e.ruc = '20000000001';

INSERT IGNORE INTO programas (numero, empresa_id, fecha, total_rollos, observaciones, created_at)
SELECT 'PROG-2026-003', e.id, CURDATE() - INTERVAL 3 DAY, 60,
       'Programa de ejemplo pendiente: usar para probar una recepcion.', NOW()
FROM empresas e WHERE e.ruc = '20000000001';

-- Lineas de los programas. programa_detalles NO tiene clave unica natural, asi
-- que la idempotencia se garantiza con NOT EXISTS por (programa, articulo,
-- color): re-correr el seed no duplica lineas. version=0 (lock optimista V43).
INSERT INTO programa_detalles (programa_id, articulo_id, color_id, cantidad_solicitada, cantidad_recibida, version, created_at)
SELECT p.id, ar.id, co.id, s.solicitado, s.recibido, 0, NOW()
FROM (
    -- PROG-2026-001 (terminado: recibido = solicitado)
    SELECT 'PROG-2026-001' AS num, 'DEMO-001' AS art, 'Negro'          AS col, 20 AS solicitado, 20 AS recibido UNION ALL
    SELECT 'PROG-2026-001',         'DEMO-001',        'Blanco',               15,               15             UNION ALL
    SELECT 'PROG-2026-001',         'DEMO-002',        'Turqueza Medio',       10,               10             UNION ALL
    SELECT 'PROG-2026-001',         'DEMO-003',        'Melange 10%',          15,               15             UNION ALL
    -- PROG-2026-002 (en curso: avance parcial)
    SELECT 'PROG-2026-002',         'DEMO-001',        'Rojo',                 12,                8             UNION ALL
    SELECT 'PROG-2026-002',         'DEMO-002',        'Negro',                18,               12             UNION ALL
    SELECT 'PROG-2026-002',         'DEMO-004',        'Verde Botella',        10,                6             UNION ALL
    SELECT 'PROG-2026-002',         'DEMO-005',        'Blanco',               14,                8             UNION ALL
    SELECT 'PROG-2026-002',         'DEMO-001',        'Azulino',               6,                0             UNION ALL
    -- PROG-2026-003 (pendiente: nada recibido, listo para demo de recepcion)
    SELECT 'PROG-2026-003',         'DEMO-001',        'Negro',                25,                0             UNION ALL
    SELECT 'PROG-2026-003',         'DEMO-002',        'Blanco',               15,                0             UNION ALL
    SELECT 'PROG-2026-003',         'DEMO-003',        'Melange 10%',          12,                0             UNION ALL
    SELECT 'PROG-2026-003',         'DEMO-004',        'Verde Botella',         8,                0
) s
JOIN programas  p  ON p.numero          = s.num
JOIN articulos  ar ON ar.codigo_interno = s.art
JOIN colores    co ON co.nombre_oficial = s.col
WHERE NOT EXISTS (
    SELECT 1 FROM programa_detalles pd
    WHERE pd.programa_id = p.id AND pd.articulo_id = ar.id AND pd.color_id = co.id
);

-- --- CUENTAS DEMO ------------------------------------------------------------
-- 10 cuentas activas y usables (una por persona/prospecto). No son es_prueba
-- (esas quedan ocultas e inactivas): estas se ven en el equipo y entran directo.
-- debe_cambiar_password=FALSE a proposito: son cuentas COMPARTIDAS de demo; si
-- se forzara el cambio, el primer prospecto que entre dejaria afuera al resto.
-- Contrasena = palabra del rol (clave conocida, para repartir facil):
--   * ADMIN       -> clave "admin"
--   * GERENTE     -> clave "gerente"
--   * SUPERVISOR  -> clave "supervisor"   (incluye a los "almaceneros": en el
--                     codigo el almacenero ES el rol SUPERVISOR, /almacen).
-- jlynch (SUPERADMIN) NO se toca aca: nuevo-demo.sh le rota la clave a una
-- privada (endurecer-cliente.sh) para que el superadmin no quede publico.
INSERT IGNORE INTO usuarios (nombre, username, password_hash, rol_id, activo, es_prueba, debe_cambiar_password) VALUES
  ('Administrador Demo', 'admindemo',       '$2a$10$FrhmZiXmDPB1TtqZOWuIRePcy/3NLE5dmnpYBLl15AnegxpvKxEe2', 5, TRUE, FALSE, FALSE),
  ('Gerente Demo 1',     'gerente1demo',    '$2a$10$VXH5wnAsqeGOHX1fDgMrBukYClgwH0A/f9IEYcCeoiUIgY0Jv8Hs6', 2, TRUE, FALSE, FALSE),
  ('Gerente Demo 2',     'gerente2demo',    '$2a$10$VXH5wnAsqeGOHX1fDgMrBukYClgwH0A/f9IEYcCeoiUIgY0Jv8Hs6', 2, TRUE, FALSE, FALSE),
  ('Gerente Demo 3',     'gerente3demo',    '$2a$10$VXH5wnAsqeGOHX1fDgMrBukYClgwH0A/f9IEYcCeoiUIgY0Jv8Hs6', 2, TRUE, FALSE, FALSE),
  ('Supervisor Demo 1',  'supervisor1demo', '$2a$10$SMbBXQGScG2EmIiHvfr38efzrh9T/A781om1Jv34LyqJNlKOl4v6u', 3, TRUE, FALSE, FALSE),
  ('Supervisor Demo 2',  'supervisor2demo', '$2a$10$SMbBXQGScG2EmIiHvfr38efzrh9T/A781om1Jv34LyqJNlKOl4v6u', 3, TRUE, FALSE, FALSE),
  ('Supervisor Demo 3',  'supervisor3demo', '$2a$10$SMbBXQGScG2EmIiHvfr38efzrh9T/A781om1Jv34LyqJNlKOl4v6u', 3, TRUE, FALSE, FALSE),
  ('Almacenero Demo 1',  'almacen1demo',    '$2a$10$SMbBXQGScG2EmIiHvfr38efzrh9T/A781om1Jv34LyqJNlKOl4v6u', 3, TRUE, FALSE, FALSE),
  ('Almacenero Demo 2',  'almacen2demo',    '$2a$10$SMbBXQGScG2EmIiHvfr38efzrh9T/A781om1Jv34LyqJNlKOl4v6u', 3, TRUE, FALSE, FALSE),
  ('Almacenero Demo 3',  'almacen3demo',    '$2a$10$SMbBXQGScG2EmIiHvfr38efzrh9T/A781om1Jv34LyqJNlKOl4v6u', 3, TRUE, FALSE, FALSE);
