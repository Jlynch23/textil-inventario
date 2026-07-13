ALTER TABLE empresas ADD COLUMN carpeta VARCHAR(50) NULL AFTER nombre;
UPDATE empresas SET carpeta = 'Laura' WHERE nombre LIKE '%Laura%';
UPDATE empresas SET carpeta = 'Clemente' WHERE nombre LIKE '%Clemente%';

ALTER TABLE recepciones ADD COLUMN fecha_factura DATE NULL AFTER numero_factura;
