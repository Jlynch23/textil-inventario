-- V23__usuario_email_opcional.sql
-- El login pasa a ser por username; el email queda como dato opcional
-- (se usara mas adelante para notificaciones/recuperacion de cuenta).

ALTER TABLE usuarios MODIFY COLUMN email VARCHAR(100) NULL;
