-- V47__kardex_documento_generalizado.sql
--
-- Generaliza el vinculo del kardex con el documento que origina cada movimiento,
-- y le ensena a expresar una TRANSFORMACION. Es el paso que el ROADMAP marca como
-- obligatorio ANTES de la primera tabla de hilo de V2 (correccion 2).
--
-- POR QUE AHORA: kardex_movimientos es la tabla mas grande del sistema y crece sin
-- fin. Migrarla se paga una sola vez, y hoy no hay ningun cliente de pago dado de
-- alta -- o sea, cero historial real encima. Hacerlo despues significa el mismo
-- ALTER con los datos de produccion de varios clientes adentro.
--
-- 1) UNA COLUMNA POR TIPO DE DOCUMENTO -> el par (tipo_documento, documento_id).
--    Hoy son dos columnas (recepcion_detalle_id, transferencia_id). V2 suma compra
--    de hilo, orden de tejido y envio a tintoreria: serian CINCO columnas con
--    cuatro siempre NULL en cada fila, mas cinco FK y cinco indices. Con el par,
--    sumar un tipo de documento deja de tocar el esquema.
--    Se pierden las FK reales (fk_kardex_recepcion_detalle, fk_kardex_transferencia):
--    ese es el precio consciente. La integridad pasa a sostenerla la aplicacion, que
--    solo vincula con KardexMovimiento.vincularDocumento(tipo, id).
--
-- 2) TRANSFORMACION_IN / TRANSFORMACION_OUT + operacion_id.
--    Los cuatro tipos actuales (INGRESO, TRANSFERENCIA_IN/OUT, AJUSTE) mueven el
--    MISMO material o lo ajustan. Una transformacion es otra cosa: entran 100 kg de
--    hilo y salen 80 kg de tela cruda -- un material se consume y aparece otro
--    distinto, y los dos movimientos son las dos caras de un mismo hecho.
--    operacion_id une esas dos caras: no alcanza con el documento, porque una misma
--    orden de tejido puede tener varias transformaciones y sin el no se sabe que
--    hilo salio para que rollo. Queda NULL hasta V2.
--
-- 3) tipo_movimiento pasa de ENUM de MySQL a VARCHAR.
--    Agregar los dos tipos de arriba ya obligo a un ALTER sobre la tabla mas grande;
--    el proximo tipo no deberia obligar a otro. Mismo criterio que tipo_documento.
--
-- NO se toca color_id (sigue NOT NULL). El hilo crudo no tiene color, asi que V2 va
-- a necesitar resolverlo, pero eso depende de una decision de diseno abierta
-- (generalizar Articulo a "material" vs. una entidad por etapa) y no se prejuzga
-- aca. Ver ROADMAP.md, "Tres cosas del modelo actual que NO aguantan V2", punto 3.

-- ---------------------------------------------------------------------------
-- 1. Columnas nuevas
-- ---------------------------------------------------------------------------
ALTER TABLE kardex_movimientos
    ADD COLUMN tipo_documento VARCHAR(30) NULL AFTER peso_kg,
    ADD COLUMN documento_id   BIGINT      NULL AFTER tipo_documento,
    ADD COLUMN operacion_id   BIGINT      NULL AFTER documento_id;

-- ---------------------------------------------------------------------------
-- 2. Backfill de las dos columnas viejas
-- ---------------------------------------------------------------------------
UPDATE kardex_movimientos
   SET tipo_documento = 'RECEPCION_DETALLE',
       documento_id   = recepcion_detalle_id
 WHERE recepcion_detalle_id IS NOT NULL;

-- El AND recepcion_detalle_id IS NULL es defensivo: ninguna fila deberia tener las
-- dos columnas puestas, pero si alguna las tuviera, el vinculo de la recepcion ya
-- quedo escrito arriba y no se pisa.
UPDATE kardex_movimientos
   SET tipo_documento = 'TRANSFERENCIA',
       documento_id   = transferencia_id
 WHERE transferencia_id IS NOT NULL
   AND recepcion_detalle_id IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Baja de las columnas viejas (primero las FK, despues el indice, despues
--    las columnas: MySQL no deja soltar un indice que sostiene una FK)
-- ---------------------------------------------------------------------------
ALTER TABLE kardex_movimientos DROP FOREIGN KEY fk_kardex_transferencia;
ALTER TABLE kardex_movimientos DROP FOREIGN KEY fk_kardex_recepcion_detalle;
ALTER TABLE kardex_movimientos DROP INDEX idx_kardex_transferencia;
ALTER TABLE kardex_movimientos
    DROP COLUMN recepcion_detalle_id,
    DROP COLUMN transferencia_id;

-- ---------------------------------------------------------------------------
-- 4. Indices nuevos
-- ---------------------------------------------------------------------------
-- (tipo_documento, documento_id) en ese orden: toda busqueda parte de saber que
-- clase de documento se busca. Reemplaza a idx_kardex_transferencia y al indice
-- que MySQL habia creado solo para fk_kardex_recepcion_detalle.
CREATE INDEX idx_kardex_documento ON kardex_movimientos(tipo_documento, documento_id);
CREATE INDEX idx_kardex_operacion ON kardex_movimientos(operacion_id);

-- ---------------------------------------------------------------------------
-- 5. tipo_movimiento: ENUM -> VARCHAR, con los dos tipos de transformacion
-- ---------------------------------------------------------------------------
ALTER TABLE kardex_movimientos
    MODIFY COLUMN tipo_movimiento VARCHAR(30) NOT NULL;
