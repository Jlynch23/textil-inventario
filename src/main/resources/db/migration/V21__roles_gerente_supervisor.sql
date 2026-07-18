-- V21__roles_gerente_supervisor.sql
-- Renombra ALMACENERO -> SUPERVISOR (mismo rol funcional, nombre mas apropiado).
-- Asegura GERENTE (solo lectura). Reordena para: 1 SUPERADMIN, 2 GERENTE, 3 SUPERVISOR, 4 VENDEDOR.

SET FOREIGN_KEY_CHECKS = 0;

-- Sacar del medio a ALMACENERO, VENDEDOR y GERENTE (si ya existiera de un intento previo)
UPDATE roles SET id = 100 WHERE nombre = 'ALMACENERO';
UPDATE roles SET id = 101 WHERE nombre = 'VENDEDOR';
DELETE FROM roles WHERE nombre = 'GERENTE' AND id <> 2;

-- GERENTE queda fijo en id=2
INSERT IGNORE INTO roles (id, nombre, descripcion) VALUES
(2, 'GERENTE', 'Acceso de solo lectura a todo el sistema, excepto Log de Eventos y Reportes de Errores');

-- ALMACENERO (en id=100) se renombra a SUPERVISOR y pasa a id=3
UPDATE roles SET id = 3, nombre = 'SUPERVISOR',
    descripcion = 'Recepcion, validacion y transferencias en Praderas'
    WHERE id = 100;

-- VENDEDOR (en id=101) pasa a id=4
UPDATE roles SET id = 4 WHERE id = 101;

SET FOREIGN_KEY_CHECKS = 1;
