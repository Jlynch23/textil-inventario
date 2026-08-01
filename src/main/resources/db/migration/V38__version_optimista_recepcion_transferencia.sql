-- Lock optimista para cerrar el TOCTOU de las confirmaciones de stock
-- (auditoria red-team R-C1). Los guards `if (estado != PENDIENTE/BORRADOR)` de
-- confirmarRecepcion / confirmarSalida / confirmarLlegada protegen el
-- doble-click SECUENCIAL, pero NO la concurrencia real: dos POST simultaneos
-- leen ambos el estado inicial, pasan el guard y aplican el movimiento de stock
-- DOS veces (stock inflado, kardex duplicado) en silencio.
--
-- Con una columna `version` mapeada como @Version en las entidades padre
-- (Recepcion, Transferencia), Hibernate agrega `AND version = ?` al UPDATE del
-- estado: la segunda transaccion concurrente afecta 0 filas -> lanza
-- OptimisticLockException -> rollback de TODO su movimiento de stock/kardex.
--
-- Las filas existentes arrancan en 0 (DEFAULT); NOT NULL porque @Version no
-- admite null en el flujo normal.
ALTER TABLE recepciones
  ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE transferencias
  ADD COLUMN version INT NOT NULL DEFAULT 0;
