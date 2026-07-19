-- El login es por username desde V22; el campo email quedo sin uso
-- y confunde en el formulario de creacion de usuarios.
ALTER TABLE usuarios DROP COLUMN email;
