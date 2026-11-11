-- Cuenta tecnica del backend; los usuarios humanos se guardan en usuario.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='uml_app') THEN
        CREATE ROLE uml_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    ELSIF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='uml_app'
        AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) THEN
        RAISE EXCEPTION 'uml_app existe con privilegios administrativos; use un servidor dedicado o revise el rol';
    END IF;
END;
$$;
ALTER ROLE uml_app SET search_path = uml_colaborativo, public;
-- La clave se asigna mediante el inicializador Docker o psql interactivo.
