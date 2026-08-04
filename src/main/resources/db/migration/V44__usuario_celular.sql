-- Celular del usuario, para las alertas de stock bajo por SMS: se manda a los
-- ADMIN y GERENTE que tengan numero cargado (SUPERADMIN nunca; es el proveedor).
-- Opcional: un usuario sin celular simplemente no recibe SMS. Formato esperado
-- E.164 (+51987654321); el controlador lo normaliza al guardar.
ALTER TABLE usuarios
  ADD COLUMN celular VARCHAR(20) NULL;
