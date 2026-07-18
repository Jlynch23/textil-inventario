-- V25__composicion_catalogo.sql
-- Catalogo de composiciones/variantes de fibra (ALGODON, MELANGE 10%,
-- MELANGE 3%, MELANGE 1%, etc). Primer paso del rediseño de Articulo:
-- separar tejido base (TipoTela), titulo, y composicion como atributos
-- independientes, sacando Color del Articulo (el color pasa a vivir a
-- nivel de cada movimiento real: stock, programa, recepcion, transferencia,
-- kardex).

CREATE TABLE composiciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO composiciones (nombre, descripcion) VALUES
('ALGODON', 'Algodon peinado 100%'),
('MELANGE 10%', 'Mezcla con 10% de fibra adicional'),
('MELANGE 3%', 'Mezcla con 3% de fibra adicional'),
('MELANGE 1%', 'Mezcla con 1% de fibra adicional');
