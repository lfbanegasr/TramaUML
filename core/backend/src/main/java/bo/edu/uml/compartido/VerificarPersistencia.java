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
            "select to_regprocedure('uml_colaborativo.iniciar_edicion_modelo(uuid,bigint,uuid)') is"
                + " not null and"
                + " to_regprocedure('uml_colaborativo.guardar_version_modelo(uuid,bigint,uuid,text)')"
                + " is not null",
            Boolean.class);
    Integer propiedadesUml =
        jdbc.queryForObject(
            "select count(*) from information_schema.columns where"
                + " table_schema='uml_colaborativo' and table_name='atributo_uml' and"
                + " column_name in ('es_ordenado','coleccion_unica') and"
                + " data_type='boolean' and is_nullable='NO'",
            Integer.class);
    Integer tablasAgente =
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema='uml_colaborativo'"
                + " and table_name in"
                + " ('conversacion_agente','mensaje_agente','ejecucion_agente','acuerdo_agente')",
            Integer.class);
    if (tablas == null
        || tablas < 52
        || columnas == null
        || columnas < 554
        || fks == null
        || fks < 132
        || !Integer.valueOf(4).equals(tablasAgente)
        || !Integer.valueOf(2).equals(propiedadesUml)
        || !Boolean.TRUE.equals(funciones))
      throw new IllegalStateException(
          "La persistencia no coincide con el contrato UML 2.5.1 y agente colaborativo; revisar las"
              + " migraciones 2026-11-10_10 y 2026-11-10_11. No se modificó el"
              + " esquema.");
  }
}
