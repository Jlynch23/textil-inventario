-- El total de rollos esperado del programa debe coincidir con la suma de
-- las cantidades de cada linea (validado en ProgramaService al crear y al
-- editar). Los programas ya existentes quedan en 0 por defecto y se pueden
-- corregir editandolos.
ALTER TABLE programas
    ADD COLUMN total_rollos INT NOT NULL DEFAULT 0 AFTER fecha;
