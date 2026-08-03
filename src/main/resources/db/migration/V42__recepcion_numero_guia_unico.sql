-- Auditoria INV-02: `numero_guia` sin UNIQUE.
-- El service hacia read-then-write (findFirstByNumeroGuia + insert) para impedir
-- guias duplicadas, pero eso NO cubre la concurrencia: dos POST simultaneos con
-- la misma guia leen ambos "no existe", pasan el chequeo e insertan DOS
-- recepciones con la misma guia -> stock/kardex duplicados y programa con
-- recibido > solicitado. La unica garantia real es un UNIQUE de base.
--
-- 1) Guias en blanco ("" o solo espacios) -> NULL. Una recepcion puede quedar
--    sin guia; en MySQL el UNIQUE permite multiples NULL, asi que varias
--    recepciones sin guia NO chocan entre si (pero dos guias reales iguales si).
UPDATE recepciones SET numero_guia = NULL WHERE TRIM(numero_guia) = '';

-- 2) La columna pasa a aceptar NULL (antes NOT NULL) para poder guardar las
--    recepciones sin guia como NULL en vez de "".
ALTER TABLE recepciones
  MODIFY COLUMN numero_guia VARCHAR(50) NULL;

-- 3) Indice unico. Si una base ya tuviera guias duplicadas REALES (no NULL),
--    este ALTER falla y la app no arranca: es una senal explicita para limpiar
--    los duplicados a mano ANTES de migrar (no se pueden fusionar recepciones
--    automaticamente sin decidir cual conservar). En una copia nueva
--    (recepciones vacia) se aplica sin ruido.
ALTER TABLE recepciones
  ADD CONSTRAINT uq_recepcion_numero_guia UNIQUE (numero_guia);
