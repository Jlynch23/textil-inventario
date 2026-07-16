-- Un mismo color puede aparecer varias veces dentro del mismo programa,
-- una vez por cada combinacion de tipo de tela + gramaje (ej. "RIB
-- Acanalado 30/1 Negro" y "RIB 2x1 24/1 Negro" en el mismo programa, con
-- cantidades distintas). ProgramaDetalle referenciaba solo un Color, lo
-- que hacia imposible representar ese caso real y ademas duplicaba lo que
-- Articulo ya modela (tipo_tela + titulo + color juntos).
-- La tabla estaba vacia al momento de esta migracion (dato de prueba
-- reseteado), por eso se puede reemplazar la columna sin backfill.
ALTER TABLE programa_detalles
    DROP FOREIGN KEY fk_progdet_color,
    DROP COLUMN color_id,
    ADD COLUMN articulo_id BIGINT NOT NULL AFTER programa_id,
    ADD CONSTRAINT fk_progdet_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id);
