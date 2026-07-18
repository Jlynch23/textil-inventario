-- V24__colores_uppercase_existentes.sql
-- Los colores creados por el formulario ya se guardan en mayusculas
-- (CatalogoService.normalizar()), pero los importados directo a la base
-- antes de esa logica quedaron en minuscula. Se uniformiza lo existente.

UPDATE colores SET nombre_oficial = UPPER(nombre_oficial) WHERE nombre_oficial IS NOT NULL;
UPDATE colores SET apodo = UPPER(apodo) WHERE apodo IS NOT NULL;
