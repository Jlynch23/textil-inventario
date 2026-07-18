-- V22__usuario_username.sql
-- Agrega columna username para login (reemplaza el uso de email como credencial).
-- Se puebla con un valor temporal derivado del email para que la columna quede
-- consistente para filas existentes antes de marcarla UNIQUE.

ALTER TABLE usuarios ADD COLUMN username VARCHAR(50) NULL;

UPDATE usuarios SET username = SUBSTRING_INDEX(email, '@', 1) WHERE username IS NULL;

ALTER TABLE usuarios MODIFY COLUMN username VARCHAR(50) NOT NULL;
ALTER TABLE usuarios ADD CONSTRAINT uq_usuario_username UNIQUE (username);
