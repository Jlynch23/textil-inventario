-- V46__normalizar_blancos_a_null.sql
-- Repara los datos que quedaron guardados como cadena vacia ('') donde el
-- significado real era "sin dato" (NULL). El codigo ya no los genera (los
-- servicios normalizan blanco -> NULL), pero las filas viejas siguen ahi y
-- cada una rompe algo distinto:
--
--   * recepciones.numero_factura = '' : la pantalla Facturar lista solo las
--     recepciones con numero_factura IS NULL, asi que esas recepciones nunca
--     aparecian para asignarles factura -- quedaban invisibles para siempre.
--
--   * colores.codigo_fast_dye = '' : findByCodigoFastDye('') devolvia TODOS
--     los colores sin codigo juntos y el matching del OCR se quedaba con uno
--     arbitrario (el de id mas alto) dandolo por confiable, es decir, podia
--     imputar el color equivocado a una linea de recepcion.
--
--   * colores.apodo = '' : getNombreMostrar() antepone el apodo si "existe";
--     con '' mostraba vacio en vez del nombre oficial.
--
-- Idempotente y acotado: solo toca filas cuyo valor es blanco.
UPDATE recepciones SET numero_factura = NULL WHERE numero_factura IS NOT NULL AND TRIM(numero_factura) = '';
UPDATE recepciones SET numero_guia    = NULL WHERE numero_guia    IS NOT NULL AND TRIM(numero_guia)    = '';

UPDATE colores SET codigo_fast_dye = NULL WHERE codigo_fast_dye IS NOT NULL AND TRIM(codigo_fast_dye) = '';
UPDATE colores SET apodo           = NULL WHERE apodo           IS NOT NULL AND TRIM(apodo)           = '';
