-- V33__reset_passwords_arranque.sql
-- Fija contraseñas de ARRANQUE conocidas para las cuentas semilla, para que una
-- instancia recien levantada pueda loguear. Los hashes previos de 'admin' (V2) y
-- 'dueno' (V31) no tenian su texto plano documentado (bcrypt es de una sola via),
-- asi que sin esto nadie podria entrar a esas cuentas en una BD nueva.
--
-- SON CREDENCIALES DE ARRANQUE -- cada usuario DEBE cambiarlas desde "Mi cuenta"
-- apenas entre, y guardarlas en un gestor de contraseñas:
--   admin (SUPERADMIN)  -> superadmin
--   dueno (ADMIN)       -> duenocliente
--   adminprueba (ADMIN) -> admin      (cuenta de PRUEBA temporal; borrar antes de vender)
UPDATE usuarios SET password_hash = '$2a$10$4HsNPXVs7ZbQaM7/BCUxFe4oz.RiesL4Rqhb.3UpZHgt31tEJniO6' WHERE username = 'admin';
UPDATE usuarios SET password_hash = '$2a$10$BvLUcVbVtLT4fGtg.EoXE.LP.2UyXivxppwi1cAUcGJMEHJPmHuAK' WHERE username = 'dueno';
UPDATE usuarios SET password_hash = '$2a$10$FrhmZiXmDPB1TtqZOWuIRePcy/3NLE5dmnpYBLl15AnegxpvKxEe2' WHERE username = 'adminprueba';
