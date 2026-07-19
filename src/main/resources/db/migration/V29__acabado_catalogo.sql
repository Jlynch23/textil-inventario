-- V29__acabado_catalogo.sql
-- Catalogo de acabados (LISO, ACANALADO, LISTADO BLANCO, etc). Primer paso
-- del rediseño Tejido+Titulo+Composicion+Acabado, basado en el analisis de
-- 20 guias reales de FAST DYE: el acanalado y el listado son acabados que
-- se aplican sobre un tejido base (siempre RIB 2X1 en la practica), no
-- tipos de tela independientes como estaban modelados hasta ahora.

CREATE TABLE acabados (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO acabados (nombre, descripcion) VALUES
('LISO', 'Acabado estandar, sin textura ni listas (defecto)'),
('ACANALADO', 'Acanalado (en la practica solo sobre RIB 2X1)'),
('LISTADO BLANCO', 'Listado con base/listas blancas'),
('LISTADO NEGRO', 'Listado con base/listas negras'),
('LISTADO MELANGE 10%', 'Listado con listas melange 10%'),
('LISTADO MELANGE 3%', 'Listado con listas melange 3%');
