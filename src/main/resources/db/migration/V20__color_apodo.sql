-- Apodo/alias corto opcional para colores (ej. "PPT" en vez del nombre
-- oficial largo que trae la guia de FAST DYE). El nombre oficial se
-- conserva intacto para el matching de OCR; el apodo solo afecta lo que
-- se muestra en pantalla (Color.getNombreMostrar()).
ALTER TABLE colores ADD COLUMN apodo VARCHAR(100) NULL AFTER nombre_oficial;
