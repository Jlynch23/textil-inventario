-- =====================================================
-- TEXTIL INVENTARIO - Datos Semilla V2
-- =====================================================

-- ROLES
INSERT INTO roles (nombre, descripcion) VALUES
('SUPERADMIN', 'Propietario con acceso total al sistema'),
('ALMACENERO', 'Encargado de recepciones y transferencias en Praderas'),
('VENDEDOR', 'Vendedor en tienda, consulta stock y registra ventas');

-- USUARIO ADMIN: credencial de arranque. Debe rotarse en el primer login.
-- La contraseña original NO se documenta aquí por seguridad (ver AUDIT.md, SEC-01).
INSERT INTO usuarios (nombre, email, password_hash, rol_id) VALUES
('Administrador', 'admin@textil.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE.B4yMnSqOuKQVAa', 1);

-- EMPRESAS
INSERT INTO empresas (nombre, ruc) VALUES
('Textil Laura E.I.R.L.', '20549819028'),
('Textil Clemente E.I.R.L.', '20549819029');

-- UBICACIONES
INSERT INTO ubicaciones (codigo, nombre, tipo, es_principal) VALUES
('PRADERAS', 'Praderas', 'ALMACEN', TRUE),
('1006', 'Almacén 1006', 'ALMACEN', FALSE),
('213', 'Almacén 213', 'ALMACEN', FALSE),
('2127', 'Almacén 2127', 'ALMACEN', FALSE),
('138', 'Tienda 138', 'TIENDA', FALSE),
('139', 'Tienda 139', 'TIENDA', FALSE);

-- TIPOS DE TELA
INSERT INTO tipos_tela (nombre) VALUES
('RIB 2x1'),
('RIB 1x1'),
('RIB Acanalado'),
('RIB Listado');

-- TÍTULOS
INSERT INTO titulos (valor, descripcion) VALUES
('24/1', 'Hilo peinado 24/1'),
('30/1', 'Hilo peinado 30/1');

-- COLORES BASE
INSERT INTO colores (nombre_oficial, codigo_fast_dye, familia) VALUES
('Negro', '100001', 'Oscuros'),
('Blanco', '200001', 'Claros'),
('Verde Botella', '132015', 'Verdes'),
('Turqueza Medio', '221963', 'Azules'),
('Rata', '300001', 'Jaspeados'),
('Melange 10%', '300002', 'Jaspeados'),
('Melange 3%', '300003', 'Jaspeados'),
('Melange 1%', '300004', 'Jaspeados');
