-- V26__articulo_sin_color_con_composicion.sql
-- Rediseño de Articulo: se saca Color (pasa a vivir en cada movimiento real
-- -- stock, programa, recepcion, transferencia, kardex -- no como parte del
-- catalogo de Articulo), se agrega Composicion (ALGODON, MELANGE 10%, etc).
-- El codigo interno del Articulo ya NO incluye color; el color se combina
-- "al vuelo" con el codigo del articulo a nivel de cada movimiento.
--
-- Como el sistema arranca de cero en produccion (sin datos reales que
-- preservar), no hace falta logica de migracion de datos: se elimina la
-- columna color_id directamente.

ALTER TABLE articulos DROP FOREIGN KEY fk_articulos_color;
ALTER TABLE articulos DROP INDEX uq_articulo;
ALTER TABLE articulos DROP COLUMN color_id;

ALTER TABLE articulos ADD COLUMN composicion_id BIGINT NOT NULL;
ALTER TABLE articulos ADD CONSTRAINT fk_articulos_composicion
    FOREIGN KEY (composicion_id) REFERENCES composiciones(id);

ALTER TABLE articulos ADD CONSTRAINT uq_articulo
    UNIQUE (tipo_tela_id, titulo_id, composicion_id);
