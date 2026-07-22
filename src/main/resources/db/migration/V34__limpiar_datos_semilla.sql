-- V34__limpiar_datos_semilla.sql
-- Deja la instancia con el CATALOGO, INVENTARIO y MOVIMIENTOS vacios, para que
-- cada cliente nuevo cargue SUS propios datos y no herede los de ejemplo del
-- negocio original (empresas, ubicaciones, tipos de tela, titulos y colores que
-- siembra V2). Conserva usuarios y roles.
--
-- Se aplica UNA sola vez, en el arranque inicial de cada instancia (antes de que
-- el cliente cargue nada), asi que NO destruye datos reales: en una copia nueva
-- corre junto con el resto de migraciones sobre una BD recien creada. Flyway la
-- marca como aplicada y no vuelve a ejecutarse.

SET FOREIGN_KEY_CHECKS = 0;

-- Movimientos e inventario
DELETE FROM log_eventos;
DELETE FROM kardex_movimientos;
DELETE FROM stock_actual;

-- Almacen (entradas/salidas rapidas del supervisor)
DELETE FROM salidas_rapidas;
DELETE FROM entradas_rapidas;

-- Transferencias
DELETE FROM transferencia_distribucion;
DELETE FROM transferencia_detalle;
DELETE FROM transferencias;

-- Recepciones y documentos
DELETE FROM recepcion_detalles;
DELETE FROM recepcion_documentos;
DELETE FROM recepciones;

-- Programas de teñido
DELETE FROM programa_detalles;
DELETE FROM programas;

-- Archivo historico
DELETE FROM documentos_historicos;

-- Catalogo
DELETE FROM articulos;
DELETE FROM colores;
DELETE FROM acabados;
DELETE FROM composiciones;
DELETE FROM titulos;
DELETE FROM tipos_tela;
DELETE FROM ubicaciones;
DELETE FROM empresas;

SET FOREIGN_KEY_CHECKS = 1;
