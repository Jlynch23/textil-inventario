-- V30__articulo_con_acabado.sql
-- Articulo pasa a ser Tejido + Titulo + Composicion + Acabado.
-- "RIB ACANALADO" y "RIB LISTADO" dejan de ser tipos de tela: se remapean
-- a tejido RIB 2X1 + acabado correspondiente (analisis de guias reales:
-- el acanalado/listado siempre acompaña a un tejido base, en la practica
-- RIB 2X1). Se agregan los tejidos VERYGATE y FRANELA (reservados para uso
-- futuro) y el titulo 20/1 (franela).

-- 1. Columna nueva (nullable primero para poder rellenarla)
ALTER TABLE articulos ADD COLUMN acabado_id BIGINT NULL;

-- 2. Todos los articulos existentes arrancan como LISO
UPDATE articulos SET acabado_id = (SELECT id FROM acabados WHERE nombre = 'LISO');

-- 3. Remapeo: articulos de "RIB ACANALADO" -> tejido RIB 2X1 + acabado ACANALADO
UPDATE articulos a
JOIN tipos_tela tt ON a.tipo_tela_id = tt.id
SET a.acabado_id  = (SELECT id FROM acabados WHERE nombre = 'ACANALADO'),
    a.tipo_tela_id = (SELECT id FROM tipos_tela t2 WHERE UPPER(t2.nombre) = 'RIB 2X1')
WHERE UPPER(tt.nombre) = 'RIB ACANALADO';

-- 4. Remapeo: articulos de "RIB LISTADO" -> RIB 2X1 + LISTADO BLANCO.
-- Los datos existentes (prueba) no distinguen variante de listado; se asigna
-- LISTADO BLANCO como valor por defecto. El VPS esta limpio, no le afecta.
UPDATE articulos a
JOIN tipos_tela tt ON a.tipo_tela_id = tt.id
SET a.acabado_id  = (SELECT id FROM acabados WHERE nombre = 'LISTADO BLANCO'),
    a.tipo_tela_id = (SELECT id FROM tipos_tela t2 WHERE UPPER(t2.nombre) = 'RIB 2X1')
WHERE UPPER(tt.nombre) = 'RIB LISTADO';

-- 5. Desactivar los tipos de tela que ya no son tejidos (no se borran por
-- seguridad referencial entre entornos; quedan invisibles en la app)
UPDATE tipos_tela SET activo = FALSE WHERE UPPER(nombre) IN ('RIB ACANALADO', 'RIB LISTADO');

-- 6. Normalizar mayusculas de los tejidos vigentes
UPDATE tipos_tela SET nombre = 'RIB 2X1' WHERE UPPER(nombre) = 'RIB 2X1';
UPDATE tipos_tela SET nombre = 'RIB 1X1' WHERE UPPER(nombre) = 'RIB 1X1';

-- 7. Tejidos nuevos (reservados) y titulo 20/1, idempotentes
INSERT INTO tipos_tela (nombre, descripcion)
SELECT 'VERYGATE', 'Tejido VERYGATE (reservado, sin uso actual)'
WHERE NOT EXISTS (SELECT 1 FROM tipos_tela WHERE UPPER(nombre) = 'VERYGATE');

INSERT INTO tipos_tela (nombre, descripcion)
SELECT 'FRANELA', 'Franela (reservado, titulo tipico 20/1)'
WHERE NOT EXISTS (SELECT 1 FROM tipos_tela WHERE UPPER(nombre) = 'FRANELA');

INSERT INTO titulos (valor, descripcion)
SELECT '20/1', 'Hilo peinado 20/1 (franela)'
WHERE NOT EXISTS (SELECT 1 FROM titulos WHERE valor = '20/1');

-- 8. Cerrar la columna: NOT NULL + FK
ALTER TABLE articulos MODIFY acabado_id BIGINT NOT NULL;
ALTER TABLE articulos ADD CONSTRAINT fk_articulos_acabado
    FOREIGN KEY (acabado_id) REFERENCES acabados(id);

-- 9. Nuevo unique con acabado. Mismo truco que V26/V27: uq_articulo es el
-- indice de soporte de la FK de tipo_tela_id, hace falta un indice temporal
-- antes de poder borrarlo (error 1553 si no).
ALTER TABLE articulos ADD INDEX idx_articulos_tipo_tela_temp (tipo_tela_id);
ALTER TABLE articulos DROP INDEX uq_articulo;
ALTER TABLE articulos ADD CONSTRAINT uq_articulo
    UNIQUE (tipo_tela_id, titulo_id, composicion_id, acabado_id);
ALTER TABLE articulos DROP INDEX idx_articulos_tipo_tela_temp;
