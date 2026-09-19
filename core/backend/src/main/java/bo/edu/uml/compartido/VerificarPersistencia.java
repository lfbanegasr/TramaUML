package bo.edu.uml.compartido;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class VerificarPersistencia implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public VerificarPersistencia(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"uml_app".equals(jdbc.queryForObject("select current_user", String.class)))
            throw new IllegalStateException("El datasource debe utilizar uml_app.");
        Integer tablas =
                jdbc.queryForObject(
                        "select count(*) from information_schema.tables where"
                                + " table_schema='uml_colaborativo' and table_type='BASE TABLE'",
                        Integer.class);
        Integer columnas =
                jdbc.queryForObject(
                        "select count(*) from information_schema.columns c join"
                            + " information_schema.tables t"
                            + " using(table_catalog,table_schema,table_name) where"
                            + " c.table_schema='uml_colaborativo' and t.table_type='BASE TABLE'",
                        Integer.class);
        Integer fks =
                jdbc.queryForObject(
                        "select count(*) from pg_constraint where"
                                + " connamespace='uml_colaborativo'::regnamespace and contype='f'",
                        Integer.class);
        Boolean funciones =
                jdbc.queryForObject(
                        "select"
                            + " to_regprocedure('uml_colaborativo.iniciar_edicion_modelo(uuid,bigint,uuid)')"
                            + " is not null and"
                            + " to_regprocedure('uml_colaborativo.guardar_version_modelo(uuid,bigint,uuid,text)')"
                            + " is not null",
                        Boolean.class);
        if (!Integer.valueOf(48).equals(tablas)
                || !Integer.valueOf(510).equals(columnas)
                || !Integer.valueOf(120).equals(fks)
                || !Boolean.TRUE.equals(funciones))
            throw new IllegalStateException(
                    "La persistencia no coincide con el contrato vigente; no se modificó el"
                            + " esquema.");
    }
}
