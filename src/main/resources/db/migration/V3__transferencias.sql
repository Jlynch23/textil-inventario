-- =========================================
-- V3: Módulo de Transferencias entre ubicaciones
-- Reemplaza las tablas vacías creadas en V1 (diseño antiguo de una sola confirmación)
-- por el nuevo diseño con doble confirmación (salida / llegada) y empresa asociada
-- =========================================

DROP TABLE IF EXISTS transferencia_detalle;
DROP TABLE IF EXISTS transferencias;

CREATE TABLE transferencias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero VARCHAR(20) NOT NULL UNIQUE,
    empresa_id BIGINT NOT NULL,
    ubicacion_origen_id BIGINT NOT NULL,
    ubicacion_destino_id BIGINT NOT NULL,
    usuario_solicita_id BIGINT NOT NULL,
    usuario_confirma_salida_id BIGINT NULL,
    usuario_confirma_llegada_id BIGINT NULL,
    fecha_solicitud DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_confirmacion_salida DATETIME NULL,
    fecha_confirmacion_llegada DATETIME NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    observaciones VARCHAR(500) NULL,

    CONSTRAINT fk_transf_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT fk_transf_origen FOREIGN KEY (ubicacion_origen_id) REFERENCES ubicaciones(id),
    CONSTRAINT fk_transf_destino FOREIGN KEY (ubicacion_destino_id) REFERENCES ubicaciones(id),
    CONSTRAINT fk_transf_usr_solicita FOREIGN KEY (usuario_solicita_id) REFERENCES usuarios(id),
    CONSTRAINT fk_transf_usr_salida FOREIGN KEY (usuario_confirma_salida_id) REFERENCES usuarios(id),
    CONSTRAINT fk_transf_usr_llegada FOREIGN KEY (usuario_confirma_llegada_id) REFERENCES usuarios(id),

    CONSTRAINT chk_transf_estado CHECK (estado IN ('BORRADOR', 'CONFIRMADA_SALIDA', 'CONFIRMADA_LLEGADA', 'CON_DIFERENCIA', 'ANULADA'))
);

CREATE TABLE transferencia_detalle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transferencia_id BIGINT NOT NULL,
    articulo_id BIGINT NOT NULL,
    cantidad_solicitada INT NOT NULL,
    cantidad_confirmada_salida INT NULL,
    cantidad_confirmada_llegada INT NULL,
    observaciones VARCHAR(300) NULL,

    CONSTRAINT fk_transfdet_transferencia FOREIGN KEY (transferencia_id) REFERENCES transferencias(id) ON DELETE CASCADE,
    CONSTRAINT fk_transfdet_articulo FOREIGN KEY (articulo_id) REFERENCES articulos(id)
);

CREATE INDEX idx_transferencias_estado ON transferencias(estado);
CREATE INDEX idx_transferencias_destino ON transferencias(ubicacion_destino_id);
CREATE INDEX idx_transferencias_empresa ON transferencias(empresa_id);
CREATE INDEX idx_transfdet_transferencia ON transferencia_detalle(transferencia_id);
CREATE INDEX idx_transfdet_articulo ON transferencia_detalle(articulo_id);

ALTER TABLE transferencias ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
