-- V36__roles_jerarquia.sql
-- Orden de PODER de los roles, independiente del id. Los id quedaron en el
-- orden en que se fueron agregando las migraciones (SUPERADMIN=1, GERENTE=2,
-- SUPERVISOR=3, VENDEDOR=4, ADMIN=5), con ADMIN al final aunque sea el segundo
-- en jerarquia. La UI ordena los roles por esta columna para que siempre se
-- muestren de mayor a menor poder:
--   SUPERADMIN > ADMIN > GERENTE > SUPERVISOR > VENDEDOR
--
-- NO se renumeran las llaves primarias (id) a proposito: son referenciadas por
-- usuarios.rol_id y por migraciones ya aplicadas; renumerar PK para un orden de
-- visualizacion es fragil y mala practica. El orden se resuelve con esta columna.
ALTER TABLE roles ADD COLUMN jerarquia INT NOT NULL DEFAULT 0;

UPDATE roles SET jerarquia = 1 WHERE nombre = 'SUPERADMIN';
UPDATE roles SET jerarquia = 2 WHERE nombre = 'ADMIN';
UPDATE roles SET jerarquia = 3 WHERE nombre = 'GERENTE';
UPDATE roles SET jerarquia = 4 WHERE nombre = 'SUPERVISOR';
UPDATE roles SET jerarquia = 5 WHERE nombre = 'VENDEDOR';
