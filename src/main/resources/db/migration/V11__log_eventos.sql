CREATE TABLE log_eventos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NULL,
    accion VARCHAR(50) NOT NULL,
    entidad VARCHAR(50) NULL,
    entidad_id BIGINT NULL,
    descripcion TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_logevento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
CREATE INDEX idx_logevento_fecha ON log_eventos(created_at);
CREATE INDEX idx_logevento_usuario ON log_eventos(usuario_id);
