-- Fuerza el cambio de la contraseña semilla en el primer login (auditoria A1).
--
-- El hash de arranque de las cuentas sembradas (jlynch/superadmin y las de
-- prueba) esta hardcodeado en las migraciones e IGUAL en toda copia entregada:
-- cualquiera con el repo o una instancia sin rotar entra con la clave por
-- defecto. En vez de versionar hashes reales o depender de que el operador
-- recuerde rotar, se marca a esas cuentas con debe_cambiar_password = TRUE: al
-- entrar, un interceptor las obliga a fijar una clave nueva antes de usar el
-- sistema (ver CambioPasswordInterceptor). Al cambiarla, el flag se apaga.
--
-- Las cuentas creadas despues por el ADMIN (con una clave elegida por el, no un
-- default compartido) arrancan en FALSE y no se ven afectadas.
ALTER TABLE usuarios
  ADD COLUMN debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Marca las cuentas sembradas con hash reproducible conocido.
UPDATE usuarios
   SET debe_cambiar_password = TRUE
 WHERE username IN ('jlynch', 'admin', 'dueno',
                    'adminprueba', 'gerenteprueba', 'supervisorprueba', 'vendedorprueba');
