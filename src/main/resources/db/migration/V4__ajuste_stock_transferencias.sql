-- =========================================
-- V4: Consolidación de stock (sin partición por empresa)
-- y ajuste de Transferencias (destino se decide en la confirmación de llegada)
-- =========================================

-- 1) stock_actual: pool único por artículo+ubicación (ya no por empresa)
ALTER TABLE stock_actual ADD CONSTRAINT uq_stock_nuevo UNIQUE (articulo_id, ubicacion_id);
ALTER TABLE stock_actual DROP FOREIGN KEY fk_stock_empresa;
ALTER TABLE stock_actual DROP INDEX uq_stock;
ALTER TABLE stock_actual DROP INDEX fk_stock_empresa;
ALTER TABLE stock_actual DROP COLUMN empresa_id;
ALTER TABLE stock_actual RENAME INDEX uq_stock_nuevo TO uq_stock;

-- 2) kardex_movimientos: empresa pasa a ser opcional
--    (se sigue llenando en INGRESO, queda vacía en TRANSFERENCIA_OUT/IN)
ALTER TABLE kardex_movimientos MODIFY COLUMN empresa_id BIGINT NULL;

-- 3) transferencias: ya no registra empresa; destino se decide al confirmar llegada
ALTER TABLE transferencias DROP FOREIGN KEY fk_transf_empresa;
ALTER TABLE transferencias DROP INDEX idx_transferencias_empresa;
ALTER TABLE transferencias DROP COLUMN empresa_id;
ALTER TABLE transferencias MODIFY COLUMN ubicacion_destino_id BIGINT NULL;
