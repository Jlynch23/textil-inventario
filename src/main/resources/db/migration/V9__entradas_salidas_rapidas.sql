CREATE TABLE entradas_rapidas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    total_rollos INT NOT NULL,
    foto_ruta VARCHAR(500) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones_admin TEXT NULL,
    recepcion_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entrapida_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_entrapida_recepcion FOREIGN KEY (recepcion_id) REFERENCES recepciones(id)
);

CREATE TABLE salidas_rapidas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    cantidad INT NOT NULL,
    tipo_tela_id BIGINT NOT NULL,
    color_id BIGINT NOT NULL,
    foto_ruta VARCHAR(500) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones_admin TEXT NULL,
    transferencia_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_salrapida_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_salrapida_tipotela FOREIGN KEY (tipo_tela_id) REFERENCES tipos_tela(id),
    CONSTRAINT fk_salrapida_color FOREIGN KEY (color_id) REFERENCES colores(id),
    CONSTRAINT fk_salrapida_transferencia FOREIGN KEY (transferencia_id) REFERENCES transferencias(id)
);
