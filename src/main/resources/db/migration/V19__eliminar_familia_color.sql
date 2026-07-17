-- Se elimina el campo "familia" de colores: nunca se llego a usar en la
-- practica (filtro/clasificacion que no aportaba valor real al negocio).
ALTER TABLE colores DROP COLUMN familia;
