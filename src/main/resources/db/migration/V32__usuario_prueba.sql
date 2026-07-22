-- V32__usuario_prueba.sql
-- ⚠️ CUENTA DE PRUEBA TEMPORAL ⚠️
-- Usuario 'adminprueba' (rol ADMIN, id=5) con contraseña "admin" para probar el
-- flujo del dueño-cliente en el VPS. La contraseña es DEBIL y esta por debajo del
-- minimo de la app (6 caracteres) a proposito: solo entra sembrada por hash.
--
-- BORRAR ANTES DE ENTREGAR COPIAS A CLIENTES REALES: como toda migracion, esta
-- corre en CADA instancia, asi que dejarla convierte a 'adminprueba'/'admin' en
-- una puerta trasera. Para quitarla en el futuro, agregar una migracion nueva:
--   DELETE FROM usuarios WHERE username = 'adminprueba';
-- (no editar/eliminar esta migracion ya aplicada).
INSERT IGNORE INTO usuarios (nombre, username, password_hash, rol_id, activo) VALUES
('Admin de Prueba', 'adminprueba', '$2a$10$eWodm7IHzQLASr2Iy6JkFuZeZq8jLCR7uN3z.SW7v9zTKKeX6Vucm', 5, TRUE);
