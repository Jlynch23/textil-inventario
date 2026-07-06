-- =========================================
-- V5: Fusión de Tienda 138/139 en una sola "Tienda"
-- y reparto de llegada por línea+ubicación (en vez de un único destino por transferencia)
-- =========================================

-- 1) Fusionar 138 y 139 en una sola ubicación "Tienda"
UPDATE ubicaciones SET codigo = 'TIENDA', nombre = 'Tienda' WHERE codigo = '138';
DELETE FROM ubicaciones WHERE codigo = '139';

-- 2) transferencias: ya no tiene un único destino a nivel de cabecera
ALTER TABLE transferencias DROP FOREIGN KEY fk_transf_destino;
ALTER TABLE transferencias DROP COLUMN ubicacion_destino_id;

-- 3) Nueva tabla: reparto de cada línea entre una o varias ubicaciones destino
CREATE TABLE transferencia_distribucion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transferencia_detalle_id BIGINT NOT NULL,
    ubicacion_id BIGINT NOT NULL,
    rollos INT NOT NULL,

    CONSTRAINT fk_dist_detalle FOREIGN KEY (transferencia_detalle_id) REFERENCES transferencia_detalle(id) ON DELETE CASCADE,
    CONSTRAINT fk_dist_ubicacion FOREIGN KEY (ubicacion_id) REFERENCES ubicaciones(id),
    CONSTRAINT uq_dist_detalle_ubicacion UNIQUE (transferencia_detalle_id, ubicacion_id)
);

CREATE INDEX idx_dist_detalle ON transferencia_distribucion(transferencia_detalle_id);
CREATE INDEX idx_dist_ubicacion ON transferencia_distribucion(ubicacion_id);
