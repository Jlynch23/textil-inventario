-- Guarda la lista de guias que una factura menciona (extraida por la IA al
-- procesar el documento), para poder vincular automaticamente cada factura
-- con las guias correspondientes ya subidas a Archivo Historico, sin
-- importar el orden en que se suban.
ALTER TABLE documentos_historicos
    ADD COLUMN guias_referenciadas TEXT NULL AFTER razon_social_detectada;
