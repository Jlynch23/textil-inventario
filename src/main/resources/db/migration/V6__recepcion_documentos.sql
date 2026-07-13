CREATE TABLE recepcion_documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recepcion_id BIGINT NOT NULL,
    tipo_documento VARCHAR(20) NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recdoc_recepcion FOREIGN KEY (recepcion_id) REFERENCES recepciones(id) ON DELETE CASCADE
);
CREATE INDEX idx_recdoc_recepcion ON recepcion_documentos(recepcion_id);
