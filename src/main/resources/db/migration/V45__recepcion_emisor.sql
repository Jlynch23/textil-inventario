-- V45__recepcion_emisor.sql
-- El EMISOR de la guia (la tintoreria que tiño y despacho la tela) pasa a ser
-- un dato leido del propio documento, no un supuesto del sistema.
--
-- Por que: hasta ahora el sistema asumia que el emisor era siempre FAST DYE
-- (estaba escrito en el prompt del OCR). En el modelo multi-cliente eso es
-- falso: cada cliente trabaja con SU tintoreria. Guardar el emisor tal como
-- viene en la guia deja trazabilidad real de quien despacho cada recepcion y
-- prepara el catalogo de proveedores de la V2 (hilado -> tejido -> teñido).
--
-- Nullable a proposito: las recepciones ya existentes no tienen el dato (se
-- cargaron cuando no se extraia), y una guia puede no traerlo legible. El RUC
-- del emisor es informacion publica (SUNAT), no es dato sensible del cliente.
ALTER TABLE recepciones
    ADD COLUMN emisor_nombre VARCHAR(150) NULL AFTER empresa_id,
    ADD COLUMN emisor_ruc VARCHAR(11) NULL AFTER emisor_nombre;

-- Indice por RUC del emisor: permite responder "todas las recepciones de esta
-- tintoreria" sin recorrer la tabla entera cuando el historico crezca.
CREATE INDEX idx_recepciones_emisor_ruc ON recepciones(emisor_ruc);
