-- V26__articulo_sin_color_con_composicion.sql
-- Rediseño de Articulo: se saca Color (pasa a vivir en cada movimiento real
-- -- stock, programa, recepcion, transferencia, kardex -- no como parte del
-- catalogo de Articulo), se agrega Composicion (ALGODON, MELANGE 10%, etc).
-- El codigo interno del Articulo ya NO incluye color; el color se combina
-- "al vuelo" con el codigo del articulo a nivel de cada movimiento.
--
-- Nota tecnica: el indice unico uq_articulo (tipo_tela_id, titulo_id, color_id)
-- tambien servia como indice de soporte de la FK de tipo_tela_id (nunca
-- existio un indice dedicado solo para esa columna). Antes de poder borrar
-- uq_articulo hay que crear un indice de reemplazo para esa FK, si no MySQL
-- rechaza el DROP INDEX con error 1553.

ALTER TABLE articulos DROP FOREIGN KEY fk_articulos_color;

ALTER TABLE articulos ADD INDEX idx_articulos_tipo_tela_temp (tipo_tela_id);
ALTER TABLE articulos DROP INDEX uq_articulo;
ALTER TABLE articulos DROP COLUMN color_id;

ALTER TABLE articulos ADD COLUMN composicion_id BIGINT NOT NULL;
ALTER TABLE articulos ADD CONSTRAINT fk_articulos_composicion
    FOREIGN KEY (composicion_id) REFERENCES composiciones(id);

ALTER TABLE articulos ADD CONSTRAINT uq_articulo
    UNIQUE (tipo_tela_id, titulo_id, composicion_id);

-- El indice temporal ya no hace falta: el nuevo uq_articulo vuelve a
-- servir de soporte para la FK de tipo_tela_id (empieza con esa columna).
ALTER TABLE articulos DROP INDEX idx_articulos_tipo_tela_temp;
