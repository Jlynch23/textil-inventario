-- V35__usuarios_prueba_y_username.sql
-- Modelo de usuarios para copias nuevas:
--   * Una sola cuenta usable: jlynch (Joseph Lynch, SUPERADMIN) -- el proveedor.
--   * Cuatro cuentas de PRUEBA (una por rol), OCULTAS para el ADMIN e INACTIVAS
--     por defecto: solo el SUPERADMIN las ve, y solo puede loguear con ellas si
--     las activa (login bloquea usuarios inactivos). Inactivas = sin puerta
--     trasera en la copia del cliente aunque su contraseña sea adivinable.
--   * NO hay cuenta 'dueno' pre-armada: al entregar una copia, el SUPERADMIN
--     crea la cuenta ADMIN del dueño (su nombre -> usuario autogenerado).

-- Marca de "cuenta de prueba" (oculta para el ADMIN, igual que las SUPERADMIN).
ALTER TABLE usuarios ADD COLUMN es_prueba BOOLEAN NOT NULL DEFAULT FALSE;

-- La cuenta SUPERADMIN pasa a ser la del proveedor real: jlynch / Joseph Lynch.
-- Mantiene su contraseña de arranque (V33: "superadmin"), a rotar en Mi cuenta.
UPDATE usuarios SET username = 'jlynch', nombre = 'Joseph Lynch' WHERE username = 'admin';

-- Ya no se pre-arma la cuenta del dueño: la crea el proveedor al entregar.
DELETE FROM usuarios WHERE username = 'dueno';

-- adminprueba (creada en V32, contraseña "admin" en V33) pasa a ser cuenta de
-- prueba oculta e inactiva.
UPDATE usuarios SET es_prueba = TRUE, activo = FALSE WHERE username = 'adminprueba';

-- Cuentas de prueba restantes, una por rol (GERENTE=2, SUPERVISOR=3, VENDEDOR=4).
-- Contraseña = nombre del rol. Ocultas (es_prueba) e inactivas (activo=FALSE).
INSERT IGNORE INTO usuarios (nombre, username, password_hash, rol_id, activo, es_prueba) VALUES
('Gerente de Prueba',    'gerenteprueba',    '$2a$10$VXH5wnAsqeGOHX1fDgMrBukYClgwH0A/f9IEYcCeoiUIgY0Jv8Hs6', 2, FALSE, TRUE),
('Supervisor de Prueba', 'supervisorprueba', '$2a$10$SMbBXQGScG2EmIiHvfr38efzrh9T/A781om1Jv34LyqJNlKOl4v6u', 3, FALSE, TRUE),
('Vendedor de Prueba',   'vendedorprueba',   '$2a$10$Jtjya0tW5nw3gHMUlsuLCOdczEJbF4gcIlC0FbcomPpyUymIECyS2', 4, FALSE, TRUE);
