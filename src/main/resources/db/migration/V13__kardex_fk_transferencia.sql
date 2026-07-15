-- Agrega el FK e índice reales sobre columnas que ya existían sin usar desde V1
CREATE INDEX idx_kardex_transferencia ON kardex_movimientos(transferencia_id);

ALTER TABLE kardex_movimientos
    ADD CONSTRAINT fk_kardex_transferencia FOREIGN KEY (transferencia_id) REFERENCES transferencias(id);

ALTER TABLE kardex_movimientos
    ADD CONSTRAINT fk_kardex_recepcion_detalle FOREIGN KEY (recepcion_detalle_id) REFERENCES recepcion_detalles(id);
