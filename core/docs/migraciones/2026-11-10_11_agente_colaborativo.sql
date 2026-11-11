-- Incremental, después de 01..10. No modifica conversaciones ni versiones anteriores.
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(20261110, 11);
CREATE TABLE IF NOT EXISTS uml_colaborativo.conversacion_agente (
 id uuid PRIMARY KEY, id_modelo uuid NOT NULL UNIQUE REFERENCES uml_colaborativo.modelo_uml(id),
 creado_por uuid NOT NULL REFERENCES uml_colaborativo.usuario(id),
 creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE IF NOT EXISTS uml_colaborativo.mensaje_agente (
 id uuid PRIMARY KEY, id_conversacion uuid NOT NULL REFERENCES uml_colaborativo.conversacion_agente(id),
 secuencia bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
 autor uuid REFERENCES uml_colaborativo.usuario(id),
 emisor varchar(12) NOT NULL CHECK(emisor IN ('USUARIO','ASISTENTE')),
 texto text NOT NULL CHECK(length(texto)<=32000),
 modalidad varchar(10) NOT NULL CHECK(modalidad IN ('TEXTO','VOZ','IMAGEN')),
 clave uuid, respuesta_a uuid REFERENCES uml_colaborativo.mensaje_agente(id),
 id_archivo uuid REFERENCES uml_colaborativo.archivo_almacenado(id),
 id_propuesta uuid REFERENCES uml_colaborativo.propuesta_inteligente(id),
 tipo_resultado varchar(12) CHECK(tipo_resultado IN ('RESPUESTA','ACLARACION','PROPUESTA','ERROR')),
 fuentes jsonb NOT NULL DEFAULT '[]', supuestos jsonb NOT NULL DEFAULT '[]',
 creado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(id_conversacion,autor,clave), UNIQUE(respuesta_a)
);
CREATE INDEX IF NOT EXISTS ix_mensaje_agente_hilo ON uml_colaborativo.mensaje_agente(id_conversacion,secuencia);
ALTER TABLE uml_colaborativo.mensaje_agente ADD COLUMN IF NOT EXISTS extraccion text CHECK(extraccion IS NULL OR length(extraccion)<=60000);
CREATE TABLE IF NOT EXISTS uml_colaborativo.ejecucion_agente (
 id uuid PRIMARY KEY, id_mensaje uuid NOT NULL UNIQUE REFERENCES uml_colaborativo.mensaje_agente(id),
 estado varchar(12) NOT NULL CHECK(estado IN ('PENDIENTE','PROCESANDO','COMPLETADO','FALLIDO','CANCELADO')),
 intencion varchar(10) NOT NULL CHECK(intencion IN ('AUTO','CONSULTAR','MODIFICAR')),
 revision_base bigint, corte_contexto bigint, contexto jsonb, resultado jsonb,
 token_trabajo uuid, intentos int NOT NULL DEFAULT 0, iniciado_en timestamptz,
 completado_en timestamptz, creado_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 duracion_ms bigint, modo varchar(20), error varchar(1000)
);
CREATE INDEX IF NOT EXISTS ix_ejecucion_agente_pendiente ON uml_colaborativo.ejecucion_agente(estado,creado_en);
CREATE TABLE IF NOT EXISTS uml_colaborativo.acuerdo_agente (
 id uuid PRIMARY KEY, id_conversacion uuid NOT NULL REFERENCES uml_colaborativo.conversacion_agente(id),
 id_mensaje uuid NOT NULL REFERENCES uml_colaborativo.mensaje_agente(id),
 contenido varchar(2000) NOT NULL CHECK(length(btrim(contenido))>0),
 confirmado_por uuid NOT NULL REFERENCES uml_colaborativo.usuario(id),
 sustituye uuid UNIQUE REFERENCES uml_colaborativo.acuerdo_agente(id),
 confirmado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);
GRANT SELECT,INSERT,UPDATE ON uml_colaborativo.conversacion_agente,uml_colaborativo.mensaje_agente,
 uml_colaborativo.ejecucion_agente,uml_colaborativo.acuerdo_agente TO uml_app;
GRANT USAGE,SELECT ON SEQUENCE uml_colaborativo.mensaje_agente_secuencia_seq TO uml_app;
COMMIT;
