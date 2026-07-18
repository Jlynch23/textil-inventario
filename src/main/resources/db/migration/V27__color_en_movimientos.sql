-- V27__color_en_movimientos.sql
-- Capa 2 del rediseño de Articulo: como Articulo ya no incluye Color
-- (ver V26), cada movimiento real que antes obtenia el color indirectamente
-- a traves del Articulo ahora necesita su propio color_id:
-- recepcion_detalles, programa_detalles, stock_actual, transferencia_detalle,
-- kardex_movimientos.

ALTER TABLE recepcion_detalles ADD COLUMN color_id BIGINT NOT NULL;
ALTER TABLE recepcion_detalles ADD CONSTRAINT fk_recepdet_color
    FOREIGN KEY (color_id) REFERENCES colores(id);

ALTER TABLE programa_detalles ADD COLUMN color_id BIGINT NOT NULL;
ALTER TABLE programa_detalles ADD CONSTRAINT fk_progdet_color
    FOREIGN KEY (color_id) REFERENCES colores(id);

ALTER TABLE stock_actual DROP INDEX uq_stock;
ALTER TABLE stock_actual ADD COLUMN color_id BIGINT NOT NULL;
ALTER TABLE stock_actual ADD CONSTRAINT fk_stock_color
    FOREIGN KEY (color_id) REFERENCES colores(id);
ALTER TABLE stock_actual ADD CONSTRAINT uq_stock
    UNIQUE (articulo_id, ubicacion_id, color_id);

ALTER TABLE transferencia_detalle ADD COLUMN color_id BIGINT NOT NULL;
ALTER TABLE transferencia_detalle ADD CONSTRAINT fk_transdet_color
    FOREIGN KEY (color_id) REFERENCES colores(id);

ALTER TABLE kardex_movimientos ADD COLUMN color_id BIGINT NOT NULL;
ALTER TABLE kardex_movimientos ADD CONSTRAINT fk_kardex_color
    FOREIGN KEY (color_id) REFERENCES colores(id);
