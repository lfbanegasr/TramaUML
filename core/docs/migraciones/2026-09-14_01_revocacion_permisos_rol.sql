-- Incremento de administración: la matriz rol_permiso no tiene borrado lógico.
-- Permite revocar asociaciones exclusivamente en esa tabla, después de que
-- el servicio autentique al actor, proteja al superadministrador y audite.
-- No cambia tablas, columnas, FK ni datos de dominio. Ejecutar como propietario.
-- Reversión del privilegio: REVOKE DELETE ON uml_colaborativo.rol_permiso FROM uml_app;
BEGIN;
GRANT DELETE ON TABLE uml_colaborativo.rol_permiso TO uml_app;
COMMIT;
