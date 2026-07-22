-- V31__rol_admin.sql
-- Nuevo rol ADMIN (id=5): el "dueño" de cada copia alquilada del sistema.
-- Controla todo su negocio EXCEPTO lo reservado al proveedor (SUPERADMIN):
--   * Reporte de Errores del Sistema (/reportes/errores)
--   * La gestion de cuentas SUPERADMIN, que ni siquiera puede ver.
-- SUPERADMIN queda reservado EXCLUSIVAMENTE al proveedor (cuenta oculta de
-- soporte que entra a cada copia). La jerarquia queda:
--   SUPERADMIN (proveedor) > ADMIN (dueño-cliente) > GERENTE / SUPERVISOR / VENDEDOR

INSERT IGNORE INTO roles (id, nombre, descripcion) VALUES
(5, 'ADMIN', 'Dueño de la empresa: control total del negocio, salvo las vistas reservadas al proveedor');

-- Cuenta ADMIN de arranque para entregar al cliente (rol_id=5).
-- Reutiliza el mismo hash de arranque que la cuenta semilla original; DEBE
-- rotarse en la entrega. El propio ADMIN puede cambiar su contraseña desde
-- "Mi cuenta" (autoservicio). La cuenta SUPERADMIN 'admin' (id=1) queda como
-- cuenta oculta del proveedor, invisible e intocable para el cliente.
INSERT IGNORE INTO usuarios (nombre, username, password_hash, rol_id, activo) VALUES
('Dueño', 'dueno', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE.B4yMnSqOuKQVAa', 5, TRUE);
