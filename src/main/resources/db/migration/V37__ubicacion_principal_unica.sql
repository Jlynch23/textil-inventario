-- Solo puede existir UNA ubicacion principal: es el almacen por donde entra
-- obligatoriamente toda la mercaderia de FAST DYE, y findByEsPrincipalTrue()
-- devuelve Optional -- con dos filas marcadas la app revienta entera con
-- NonUniqueResultException (paso en dev el 30-jul-2026).
--
-- MySQL no tiene indices parciales como Postgres. El truco es una columna
-- generada que vale 1 solo cuando es_principal = 1, y NULL en el resto: los
-- NULL NO cuentan para un UNIQUE, asi que se admiten infinitas ubicaciones
-- no principales y una sola principal.
--
-- Red de seguridad: si por lo que sea quedaran varias marcadas, deja como
-- principal la de id mas bajo antes de crear el indice (si no, el ALTER falla
-- y la app no arranca).
UPDATE ubicaciones SET es_principal = 0
WHERE es_principal = 1
  AND id > (SELECT * FROM (SELECT MIN(id) FROM ubicaciones WHERE es_principal = 1) AS t);

ALTER TABLE ubicaciones
  ADD COLUMN principal_unica TINYINT
    GENERATED ALWAYS AS (CASE WHEN es_principal = 1 THEN 1 ELSE NULL END) VIRTUAL,
  ADD UNIQUE KEY uq_ubicacion_principal (principal_unica);
