-- El procesamiento de Archivo Historico corre en un hilo @Async, donde
-- Spring Security NO tiene el contexto de sesion del usuario que subio el
-- ZIP (el contexto vive en el hilo de la peticion HTTP original, no se
-- propaga solo a hilos nuevos). Se guarda explicitamente quien subio cada
-- documento para poder usarlo como "usuario actual" al crear la Recepcion
-- automatica, sin depender de SecurityContextHolder en ese hilo.
ALTER TABLE documentos_historicos
    ADD COLUMN subido_por_usuario_id BIGINT NULL AFTER recepcion_creada_id,
    ADD CONSTRAINT fk_doc_historico_usuario FOREIGN KEY (subido_por_usuario_id) REFERENCES usuarios(id);
