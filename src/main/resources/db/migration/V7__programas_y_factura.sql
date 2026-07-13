-- Factura como dato de trazabilidad en cada recepción (guía)
ALTER TABLE recepciones ADD COLUMN numero_factura VARCHAR(50) NULL AFTER numero_guia;

-- Programa: el pedido que se envía a FAST DYE
CREATE TABLE programas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero VARCHAR(20) NOT NULL UNIQUE,
    empresa_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    observaciones TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_programa_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id)
);

-- Detalle del programa: color esperado + cantidad solicitada
CREATE TABLE programa_detalles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    programa_id BIGINT NOT NULL,
    color_id BIGINT NOT NULL,
    cantidad_solicitada INT NOT NULL,
    cantidad_recibida INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_progdet_programa FOREIGN KEY (programa_id) REFERENCES programas(id) ON DELETE CASCADE,
    CONSTRAINT fk_progdet_color FOREIGN KEY (color_id) REFERENCES colores(id)
);
CREATE INDEX idx_progdet_programa ON programa_detalles(programa_id);

-- Vínculo: cada línea de recepción puede quedar ligada a la línea del programa que cumple
ALTER TABLE recepcion_detalles ADD COLUMN programa_detalle_id BIGINT NULL AFTER articulo_id;
ALTER TABLE recepcion_detalles ADD CONSTRAINT fk_recdet_progdet FOREIGN KEY (programa_detalle_id) REFERENCES programa_detalles(id);
