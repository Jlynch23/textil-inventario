-- #6 (auditoria): correlativo transaccional para los números de transferencia.
--
-- generarNumero derivaba el siguiente número del MAX(numero) existente: mejor que
-- count(), pero SIGUE teniendo carrera -> dos altas simultáneas leen el mismo MAX
-- y generan el mismo "TRF-000101"; el UNIQUE salva la BD pero una request falla.
-- Con esta tabla + bloqueo pesimista de la fila, el número se reserva de forma
-- serializada y no hay colisión posible.
CREATE TABLE correlativo (
    nombre       VARCHAR(50)  NOT NULL PRIMARY KEY,
    ultimo_valor BIGINT       NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0
);

-- Semilla del correlativo 'transferencia' = máximo número ya emitido (la parte
-- numérica de "TRF-NNNNNN"), o 0 si no hay ninguna. Así el primer número nuevo
-- continúa la secuencia sin pisar los existentes.
INSERT INTO correlativo (nombre, ultimo_valor, version)
SELECT 'transferencia',
       COALESCE(MAX(CAST(SUBSTRING_INDEX(numero, '-', -1) AS UNSIGNED)), 0),
       0
FROM transferencias;
