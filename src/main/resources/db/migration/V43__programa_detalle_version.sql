-- Auditoria INV-03: ProgramaDetalle podia perder actualizaciones (lost update).
-- El @Version de Recepcion (V38) cierra el doble-submit de UNA misma recepcion,
-- pero dos recepciones DISTINTAS del mismo programa confirmadas en paralelo
-- pisaban el contador cantidad_recibida (last-write-wins): ambas leen X y
-- escriben X+propio, quedando el avance del programa por debajo de lo real.
--
-- Con esta columna mapeada como @Version en ProgramaDetalle, Hibernate agrega
-- `AND version = ?` al UPDATE del contador: la segunda transaccion concurrente
-- afecta 0 filas -> OptimisticLockException -> rollback de esa confirmacion.
--
-- Filas existentes arrancan en 0 (DEFAULT); NOT NULL porque @Version no admite
-- null en el flujo normal.
ALTER TABLE programa_detalles
  ADD COLUMN version INT NOT NULL DEFAULT 0;
