-- M6 (auditoria): dedup e indexacion de documentos historicos.
--
-- buscarYaImportado / vinculacion factura<->guia cargaban TODA la tabla del tipo
-- y comparaban el numero normalizado en memoria (O(n^2) sobre el total de docs
-- importados). Se agrega una columna con el numero YA normalizado + indice, para
-- que la busqueda de duplicados sea un lookup indexado.
--
-- La normalizacion replica ArchivoHistoricoService.normalizarNumeroGuia:
--   * GUIA (con guion): prefijo (hasta el PRIMER guion) + '-' + sufijo SIN ceros
--     a la izquierda. Ej: "TG01-00021376" -> "TG01-21376".
--   * GUIA (sin guion): el numero en MAYUSCULAS y sin espacios.
--   * FACTURA: numero de factura en MAYUSCULAS, sin espacios.
-- Los docs nuevos setean esta columna desde Java (misma funcion), asi que para
-- ellos la normalizacion es exacta; este backfill cubre los ya importados.

ALTER TABLE documentos_historicos ADD COLUMN numero_normalizado VARCHAR(60);

-- GUIAS con guion: se toma el PRIMER guion (LOCATE), igual que indexOf('-') en Java.
UPDATE documentos_historicos
SET numero_normalizado = CONCAT(
        SUBSTRING_INDEX(UPPER(TRIM(numero_guia)), '-', 1),
        '-',
        TRIM(LEADING '0' FROM SUBSTRING(UPPER(TRIM(numero_guia)), LOCATE('-', UPPER(TRIM(numero_guia))) + 1))
    )
WHERE tipo_documento = 'GUIA'
  AND numero_guia IS NOT NULL AND TRIM(numero_guia) <> ''
  AND LOCATE('-', UPPER(TRIM(numero_guia))) > 0;

-- GUIAS sin guion: tal cual, en mayusculas.
UPDATE documentos_historicos
SET numero_normalizado = UPPER(TRIM(numero_guia))
WHERE tipo_documento = 'GUIA'
  AND numero_guia IS NOT NULL AND TRIM(numero_guia) <> ''
  AND LOCATE('-', UPPER(TRIM(numero_guia))) = 0;

-- FACTURAS: numero de factura en mayusculas, sin espacios.
UPDATE documentos_historicos
SET numero_normalizado = UPPER(TRIM(numero_factura))
WHERE tipo_documento = 'FACTURA'
  AND numero_factura IS NOT NULL AND TRIM(numero_factura) <> '';

-- Indice para el lookup de duplicados: (tipo, numero_normalizado, estado).
CREATE INDEX idx_dh_tipo_norm_estado
    ON documentos_historicos (tipo_documento, numero_normalizado, estado_proceso);
