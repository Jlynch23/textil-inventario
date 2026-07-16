-- Permite que una carga masiva en Archivo Historico (solo si se marca
-- explicitamente al subir el ZIP) cree Recepciones reales y las confirme
-- automaticamente, usando los rollos de la guia como si fueran el conteo
-- fisico. Pensado unicamente para poblar datos de prueba rapidamente;
-- por defecto queda en FALSE y el comportamiento original (nunca tocar
-- stock_actual ni kardex_movimientos) no cambia.
ALTER TABLE documentos_historicos
    ADD COLUMN crear_recepcion_automatica BOOLEAN NOT NULL DEFAULT FALSE AFTER estado_proceso,
    ADD COLUMN recepcion_creada_id BIGINT NULL AFTER crear_recepcion_automatica,
    ADD CONSTRAINT fk_doc_historico_recepcion FOREIGN KEY (recepcion_creada_id) REFERENCES recepciones(id);
