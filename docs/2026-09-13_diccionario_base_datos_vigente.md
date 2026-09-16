# Diccionario vigente de PostgreSQL — 2026-09-13

Derivado automáticamente de los metadatos del contenedor `uml-collab-postgresql-1`, después de aplicar la migración 18. No contiene registros, contraseñas ni tokens. No se modificaron los documentos originales.

Esquema `uml_colaborativo`: 48 tablas, 507 columnas y 127 índices (incluidos PK/UQ). Las tablas, columnas y relaciones conservan sus nombres españoles. Para instalación e integración consultar [la guía vigente](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/docs/2026-09-13_guia_persistencia_corregida.md>).

La extracción de este documento sigue representando la base personal antes del incremento 05. La migración incremental `core/docs/migraciones/2026-09-15_02_resolucion_conflictos.sql`, todavía no aplicada al puerto 55432, fue verificada en bases aisladas y lleva el esquema a 48 tablas, 509 columnas y 120 FK. Su delta se documenta junto a `CONFLICTO_EDICION` para no presentar como desplegado un cambio pendiente.

Los nombres de tipos SQL y operadores son vocabulario técnico PostgreSQL. `NULL` como máximo de multiplicidad UML representa ausencia de límite, no un máximo admisible para un identificador. Los triggers y las funciones se explican en la guía; esta lista documenta columnas, restricciones e índices físicos.

## ACTIVIDAD_PROYECTO

Tabla física: `uml_colaborativo.actividad_proyecto`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | bigint | No | — |
| id_proyecto | uuid | No | — |
| id_modelo | uuid | Sí | — |
| id_version_modelo | uuid | Sí | — |
| id_usuario_actor | uuid | Sí | — |
| categoria | character varying(64) | No | — |
| accion | character varying(80) | No | — |
| tipo_entidad | character varying(64) | Sí | — |
| id_entidad | uuid | Sí | — |
| valor_anterior | jsonb | Sí | — |
| valor_posterior | jsonb | Sí | — |
| detalle | jsonb | No | '{}'::jsonb |
| ocurrido_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_actividad_proyecto_01` (CHECK): `CHECK ((jsonb_typeof(detalle) = 'object'::text))`.
- `ck_actividad_proyecto_02` (CHECK): `CHECK (((id_version_modelo IS NULL) OR (id_modelo IS NOT NULL)))`.
- `fk_actividad_proyecto_01` (FK): `FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE`.
- `fk_actividad_proyecto_02` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE SET NULL`.
- `fk_actividad_proyecto_03` (FK): `FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL`.
- `fk_actividad_proyecto_04` (FK): `FOREIGN KEY (id_usuario_actor) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_actividad_proyecto_05` (FK): `FOREIGN KEY (id_modelo, id_proyecto) REFERENCES uml_colaborativo.modelo_uml(id, id_proyecto)`.
- `fk_actividad_proyecto_06` (FK): `FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo)`.
- `pk_actividad_proyecto_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_actividad_proyecto_01`: `CREATE INDEX ix_actividad_proyecto_01 ON uml_colaborativo.actividad_proyecto USING btree (id_proyecto, ocurrido_en DESC)`.
- `ix_actividad_proyecto_02`: `CREATE INDEX ix_actividad_proyecto_02 ON uml_colaborativo.actividad_proyecto USING btree (id_modelo, ocurrido_en DESC)`.
- `pk_actividad_proyecto_01`: `CREATE UNIQUE INDEX pk_actividad_proyecto_01 ON uml_colaborativo.actividad_proyecto USING btree (id)`.

## AMBIGUEDAD_PROPUESTA

Tabla física: `uml_colaborativo.ambiguedad_propuesta`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_propuesta | uuid | No | — |
| id_operacion | uuid | Sí | — |
| descripcion | text | No | — |
| resolucion | text | Sí | — |
| resuelto_por | uuid | Sí | — |
| resuelto_en | timestamp with time zone | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `fk_ambiguedad_propuesta_01` (FK): `FOREIGN KEY (id_propuesta) REFERENCES uml_colaborativo.propuesta_inteligente(id) ON DELETE CASCADE`.
- `fk_ambiguedad_propuesta_02` (FK): `FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE`.
- `fk_ambiguedad_propuesta_03` (FK): `FOREIGN KEY (resuelto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_ambiguedad_propuesta_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_ambiguedad_operacion`: `CREATE INDEX ix_ambiguedad_operacion ON uml_colaborativo.ambiguedad_propuesta USING btree (id_operacion) WHERE (id_operacion IS NOT NULL)`.
- `ix_ambiguedad_propuesta_revision`: `CREATE INDEX ix_ambiguedad_propuesta_revision ON uml_colaborativo.ambiguedad_propuesta USING btree (id_propuesta)`.
- `pk_ambiguedad_propuesta_01`: `CREATE UNIQUE INDEX pk_ambiguedad_propuesta_01 ON uml_colaborativo.ambiguedad_propuesta USING btree (id)`.

## ARCHIVO_ALMACENADO

Tabla física: `uml_colaborativo.archivo_almacenado`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_proyecto | uuid | Sí | — |
| cargado_por | uuid | Sí | — |
| categoria | character varying(64) | No | — |
| proveedor_almacenamiento | character varying(40) | No | — |
| clave_almacenamiento | text | No | — |
| nombre_original | character varying(255) | No | — |
| tipo_contenido | character varying(150) | No | — |
| tamano_bytes | bigint | No | — |
| sha256 | character(64) | No | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| eliminado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_archivo_almacenado_01` (CHECK): `CHECK (((categoria)::text = ANY (ARRAY[('IMAGEN_DIAGRAMA'::character varying)::text, ('AUDIO_VOZ'::character varying)::text, ('ENTRADA_XMI'::character varying)::text, ('SALIDA_XMI'::character varying)::text, ('BACKEND_GENERADO'::character varying)::text, ('INFORME_VALIDACION'::character varying)::text, ('REGISTRO_COMPILACION'::character varying)::text, ('OTRO'::character varying)::text])))`.
- `ck_archivo_almacenado_02` (CHECK): `CHECK ((tamano_bytes >= 0))`.
- `ck_archivo_almacenado_03` (CHECK): `CHECK ((sha256 ~ '^[0-9a-f]{64}$'::text))`.
- `fk_archivo_almacenado_01` (FK): `FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE`.
- `fk_archivo_almacenado_02` (FK): `FOREIGN KEY (cargado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_archivo_almacenado_01` (PK): `PRIMARY KEY (id)`.
- `uq_archivo_almacenado_01` (UQ): `UNIQUE (clave_almacenamiento)`.

Índices:

- `ix_archivo_almacenado_01`: `CREATE INDEX ix_archivo_almacenado_01 ON uml_colaborativo.archivo_almacenado USING btree (id_proyecto, categoria, creado_en DESC)`.
- `pk_archivo_almacenado_01`: `CREATE UNIQUE INDEX pk_archivo_almacenado_01 ON uml_colaborativo.archivo_almacenado USING btree (id)`.
- `uq_archivo_almacenado_01`: `CREATE UNIQUE INDEX uq_archivo_almacenado_01 ON uml_colaborativo.archivo_almacenado USING btree (clave_almacenamiento)`.

## ATRIBUTO_UML

Tabla física: `uml_colaborativo.atributo_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_clase | uuid | No | — |
| nombre | character varying(180) | No | — |
| tipo_uml | character varying(180) | No | — |
| visibilidad | character varying(64) | No | 'PRIVADO'::character varying |
| valor_predeterminado | text | Sí | — |
| multiplicidad_minima | integer | No | 1 |
| multiplicidad_maxima | integer | Sí | 1 |
| es_identificador | boolean | No | false |
| es_unico | boolean | No | false |
| es_estatico | boolean | No | false |
| restricciones_json | jsonb | No | '{}'::jsonb |
| orden | integer | No | 0 |
| id_externo_xmi | character varying(255) | Sí | — |
| version_fila | bigint | No | 1 |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| eliminado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_atributo_uml_01` (CHECK): `CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text])))`.
- `ck_atributo_uml_02` (CHECK): `CHECK ((multiplicidad_minima >= 0))`.
- `ck_atributo_uml_03` (CHECK): `CHECK (((multiplicidad_maxima IS NULL) OR (multiplicidad_maxima >= 0)))`.
- `ck_atributo_uml_04` (CHECK): `CHECK ((jsonb_typeof(restricciones_json) = 'object'::text))`.
- `ck_atributo_uml_05` (CHECK): `CHECK ((orden >= 0))`.
- `ck_atributo_uml_06` (CHECK): `CHECK (((multiplicidad_maxima IS NULL) OR (multiplicidad_maxima >= multiplicidad_minima)))`.
- `ck_atributo_uml_07` (CHECK): `CHECK (((nombre)::text <> ''::text))`.
- `ck_atributo_uml_08` (CHECK): `CHECK (((tipo_uml)::text <> ''::text))`.
- `ck_atributo_uml_09` (CHECK): `CHECK (((NOT es_identificador) OR ((multiplicidad_minima = 1) AND (NOT (multiplicidad_maxima IS DISTINCT FROM 1)) AND (NOT es_estatico))))`.
- `fk_atributo_uml_01` (FK): `FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE`.
- `pk_atributo_uml_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_atributo_uml_01`: `CREATE UNIQUE INDEX ix_atributo_uml_01 ON uml_colaborativo.atributo_uml USING btree (id_clase, lower((nombre)::text)) WHERE (eliminado_en IS NULL)`.
- `ix_atributo_uml_02`: `CREATE INDEX ix_atributo_uml_02 ON uml_colaborativo.atributo_uml USING btree (id_clase, orden) WHERE (eliminado_en IS NULL)`.
- `pk_atributo_uml_01`: `CREATE UNIQUE INDEX pk_atributo_uml_01 ON uml_colaborativo.atributo_uml USING btree (id)`.

## CAMBIO_MODELO

Tabla física: `uml_colaborativo.cambio_modelo`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| id_sesion_edicion | uuid | Sí | — |
| id_usuario_actor | uuid | No | — |
| id_cambio_cliente | uuid | No | — |
| revision_base | bigint | No | — |
| revision_aplicada | bigint | Sí | — |
| tipo_operacion | character varying(64) | No | — |
| tipo_entidad | character varying(64) | No | — |
| id_entidad | uuid | Sí | — |
| parche | jsonb | No | — |
| estado | character varying(64) | No | 'PENDIENTE'::character varying |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| aplicado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_cambio_modelo_01` (CHECK): `CHECK ((revision_base >= 0))`.
- `ck_cambio_modelo_02` (CHECK): `CHECK ((revision_aplicada >= 0))`.
- `ck_cambio_modelo_03` (CHECK): `CHECK (((tipo_operacion)::text = ANY (ARRAY[('CREAR'::character varying)::text, ('ACTUALIZAR'::character varying)::text, ('ELIMINAR'::character varying)::text, ('MOVER'::character varying)::text, ('RESTAURAR'::character varying)::text, ('IMPORTAR'::character varying)::text])))`.
- `ck_cambio_modelo_04` (CHECK): `CHECK ((jsonb_typeof(parche) = ANY (ARRAY['object'::text, 'array'::text])))`.
- `ck_cambio_modelo_05` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text, ('CONFLICTO'::character varying)::text])))`.
- `ck_cambio_modelo_06` (CHECK): `CHECK (((((estado)::text = 'APLICADO'::text) = (revision_aplicada IS NOT NULL)) AND (((estado)::text <> 'APLICADO'::text) OR ((aplicado_en IS NOT NULL) AND (revision_aplicada = (revision_base + 1))))))`.
- `fk_cambio_modelo_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_cambio_modelo_02` (FK): `FOREIGN KEY (id_sesion_edicion) REFERENCES uml_colaborativo.sesion_edicion_modelo(id) ON DELETE SET NULL`.
- `fk_cambio_modelo_03` (FK): `FOREIGN KEY (id_usuario_actor) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `fk_cambio_modelo_04` (FK): `FOREIGN KEY (id_sesion_edicion, id_modelo) REFERENCES uml_colaborativo.sesion_edicion_modelo(id, id_modelo)`.
- `pk_cambio_modelo_01` (PK): `PRIMARY KEY (id)`.
- `uq_cambio_modelo_01` (UQ): `UNIQUE (id_modelo, id_cambio_cliente)`.
- `uq_cambio_modelo_02` (UQ): `UNIQUE (id_modelo, revision_aplicada)`.
- `uq_cambio_modelo_03` (UQ): `UNIQUE (id, id_modelo)`.

Índices:

- `ix_cambio_modelo_01`: `CREATE INDEX ix_cambio_modelo_01 ON uml_colaborativo.cambio_modelo USING btree (id_modelo, revision_base, creado_en)`.
- `pk_cambio_modelo_01`: `CREATE UNIQUE INDEX pk_cambio_modelo_01 ON uml_colaborativo.cambio_modelo USING btree (id)`.
- `uq_cambio_modelo_01`: `CREATE UNIQUE INDEX uq_cambio_modelo_01 ON uml_colaborativo.cambio_modelo USING btree (id_modelo, id_cambio_cliente)`.
- `uq_cambio_modelo_02`: `CREATE UNIQUE INDEX uq_cambio_modelo_02 ON uml_colaborativo.cambio_modelo USING btree (id_modelo, revision_aplicada)`.
- `uq_cambio_modelo_03`: `CREATE UNIQUE INDEX uq_cambio_modelo_03 ON uml_colaborativo.cambio_modelo USING btree (id, id_modelo)`.

## CLASE_CONFIGURACION_BACKEND

Tabla física: `uml_colaborativo.clase_configuracion_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id_configuracion | uuid | No | — |
| id_clase | uuid | No | — |
| es_persistente | boolean | No | true |
| exponer_rest | boolean | No | true |
| nombre_tabla | character varying(63) | Sí | — |
| nombre_entidad | character varying(180) | Sí | — |

Restricciones y relaciones:

- `fk_clase_configuracion_backend_01` (FK): `FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE`.
- `fk_clase_configuracion_backend_02` (FK): `FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT`.
- `pk_clase_configuracion_backend_01` (PK): `PRIMARY KEY (id_configuracion, id_clase)`.

Índices:

- `ix_clase_configuracion_backend_01`: `CREATE INDEX ix_clase_configuracion_backend_01 ON uml_colaborativo.clase_configuracion_backend USING btree (id_clase)`.
- `pk_clase_configuracion_backend_01`: `CREATE UNIQUE INDEX pk_clase_configuracion_backend_01 ON uml_colaborativo.clase_configuracion_backend USING btree (id_configuracion, id_clase)`.

## CLASE_UML

Tabla física: `uml_colaborativo.clase_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| id_paquete | uuid | Sí | — |
| nombre | character varying(180) | No | — |
| descripcion | text | Sí | — |
| estereotipo | character varying(100) | Sí | — |
| visibilidad | character varying(64) | No | 'PUBLICO'::character varying |
| es_abstracto | boolean | No | false |
| es_persistente | boolean | No | true |
| id_externo_xmi | character varying(255) | Sí | — |
| version_fila | bigint | No | 1 |
| creado_por | uuid | Sí | — |
| actualizado_por | uuid | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| eliminado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_clase_uml_01` (CHECK): `CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text])))`.
- `ck_clase_uml_02` (CHECK): `CHECK (((nombre)::text <> ''::text))`.
- `fk_clase_uml_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_clase_uml_02` (FK): `FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_clase_uml_03` (FK): `FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_clase_uml_04` (FK): `FOREIGN KEY (id_paquete, id_modelo) REFERENCES uml_colaborativo.paquete_uml(id, id_modelo) ON DELETE SET NULL (id_paquete) DEFERRABLE INITIALLY DEFERRED`.
- `pk_clase_uml_01` (PK): `PRIMARY KEY (id)`.
- `uq_clase_uml_01` (UQ): `UNIQUE (id, id_modelo)`.

Índices:

- `ix_clase_uml_01`: `CREATE UNIQUE INDEX ix_clase_uml_01 ON uml_colaborativo.clase_uml USING btree (id_modelo, COALESCE(id_paquete, '00000000-0000-0000-0000-000000000000'::uuid), lower((nombre)::text)) WHERE (eliminado_en IS NULL)`.
- `ix_clase_uml_02`: `CREATE INDEX ix_clase_uml_02 ON uml_colaborativo.clase_uml USING btree (id_modelo) WHERE (eliminado_en IS NULL)`.
- `ix_clase_uml_03`: `CREATE INDEX ix_clase_uml_03 ON uml_colaborativo.clase_uml USING btree (id_paquete)`.
- `pk_clase_uml_01`: `CREATE UNIQUE INDEX pk_clase_uml_01 ON uml_colaborativo.clase_uml USING btree (id)`.
- `uq_clase_uml_01`: `CREATE UNIQUE INDEX uq_clase_uml_01 ON uml_colaborativo.clase_uml USING btree (id, id_modelo)`.

## COMPROBACION_SERVICIO

Tabla física: `uml_colaborativo.comprobacion_servicio`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_servicio | uuid | No | — |
| estado | character varying(64) | No | — |
| latencia_ms | integer | Sí | — |
| detalle | text | Sí | — |
| comprobado_por | uuid | Sí | — |
| comprobado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_comprobacion_servicio_01` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('DISPONIBLE'::character varying)::text, ('NO_DISPONIBLE'::character varying)::text, ('DEGRADADO'::character varying)::text])))`.
- `ck_comprobacion_servicio_02` (CHECK): `CHECK ((latencia_ms >= 0))`.
- `fk_comprobacion_servicio_01` (FK): `FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE CASCADE`.
- `fk_comprobacion_servicio_02` (FK): `FOREIGN KEY (comprobado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_comprobacion_servicio_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_comprobacion_servicio_01`: `CREATE INDEX ix_comprobacion_servicio_01 ON uml_colaborativo.comprobacion_servicio USING btree (id_servicio, comprobado_en DESC)`.
- `pk_comprobacion_servicio_01`: `CREATE UNIQUE INDEX pk_comprobacion_servicio_01 ON uml_colaborativo.comprobacion_servicio USING btree (id)`.

## CONFIGURACION_GENERACION_BACKEND

Tabla física: `uml_colaborativo.configuracion_generacion_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_version_modelo | uuid | No | — |
| nombre | character varying(180) | No | — |
| nombre_proyecto | character varying(180) | No | — |
| id_grupo | character varying(255) | No | — |
| id_artefacto | character varying(255) | No | — |
| paquete_base | character varying(255) | No | — |
| version_inicial | character varying(60) | No | '0.0.1-SNAPSHOT'::character varying |
| version_java | character varying(20) | No | — |
| version_spring_boot | character varying(30) | No | — |
| motor_base_datos | character varying(20) | No | 'POSTGRESQL'::character varying |
| herramienta_migracion | character varying(16) | No | 'FLYWAY'::character varying |
| generar_repositorios | boolean | No | true |
| generar_servicios | boolean | No | true |
| generar_controladores | boolean | No | true |
| generar_dtos | boolean | No | true |
| exponer_rest | boolean | No | true |
| plantilla_conexion | jsonb | No | '{}'::jsonb |
| estado | character varying(64) | No | 'BORRADOR'::character varying |
| creado_por | uuid | No | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_configuracion_generacion_backend_01` (CHECK): `CHECK (((id_grupo)::text ~ '^[A-Za-z_][A-Za-z0-9_.]*$'::text))`.
- `ck_configuracion_generacion_backend_02` (CHECK): `CHECK (((motor_base_datos)::text = 'POSTGRESQL'::text))`.
- `ck_configuracion_generacion_backend_03` (CHECK): `CHECK (((herramienta_migracion)::text = ANY (ARRAY[('FLYWAY'::character varying)::text, ('LIQUIBASE'::character varying)::text, ('NINGUNO'::character varying)::text])))`.
- `ck_configuracion_generacion_backend_04` (CHECK): `CHECK ((jsonb_typeof(plantilla_conexion) = 'object'::text))`.
- `ck_configuracion_generacion_backend_05` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('VALIDO'::character varying)::text, ('INVALIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text, ('ARCHIVADO'::character varying)::text])))`.
- `ck_configuracion_generacion_backend_06` (CHECK): `CHECK (((id_artefacto)::text ~ '^[A-Za-z0-9_.-]+$'::text))`.
- `ck_configuracion_generacion_backend_07` (CHECK): `CHECK (((paquete_base)::text ~ '^[a-z_][a-z0-9_.]*$'::text))`.
- `fk_configuracion_generacion_backend_01` (FK): `FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE RESTRICT`.
- `fk_configuracion_generacion_backend_02` (FK): `FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `pk_configuracion_generacion_backend_01` (PK): `PRIMARY KEY (id)`.
- `uq_configuracion_generacion_backend_01` (UQ): `UNIQUE (id_version_modelo, nombre)`.

Índices:

- `pk_configuracion_generacion_backend_01`: `CREATE UNIQUE INDEX pk_configuracion_generacion_backend_01 ON uml_colaborativo.configuracion_generacion_backend USING btree (id)`.
- `uq_configuracion_generacion_backend_01`: `CREATE UNIQUE INDEX uq_configuracion_generacion_backend_01 ON uml_colaborativo.configuracion_generacion_backend USING btree (id_version_modelo, nombre)`.

## CONFLICTO_EDICION

Tabla física: `uml_colaborativo.conflicto_edicion`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| tipo_entidad | character varying(64) | No | — |
| id_entidad | uuid | Sí | — |
| revision_base | bigint | No | — |
| id_cambio_almacenado | uuid | Sí | — |
| id_cambio_entrante | uuid | Sí | — |
| valor_almacenado | jsonb | No | — |
| valor_entrante | jsonb | No | — |
| estado | character varying(64) | No | 'ABIERTO'::character varying |
| tipo_resolucion | character varying(64) | Sí | — |
| valor_resolucion | jsonb | Sí | — |
| detectado_en | timestamp with time zone | No | clock_timestamp() |
| resuelto_por | uuid | Sí | — |
| resuelto_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_conflicto_edicion_01` (CHECK): `CHECK ((revision_base >= 0))`.
- `ck_conflicto_edicion_02` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('ABIERTO'::character varying)::text, ('RESUELTO'::character varying)::text, ('DESCARTADO'::character varying)::text])))`.
- `ck_conflicto_edicion_03` (CHECK): `CHECK (((tipo_resolucion IS NULL) OR ((tipo_resolucion)::text = ANY (ARRAY[('CONSERVAR_ALMACENADO'::character varying)::text, ('APLICAR_ENTRANTE'::character varying)::text, ('FUSIONAR'::character varying)::text]))))`.
- `ck_conflicto_edicion_04` (CHECK): `CHECK ((((estado)::text = 'RESUELTO'::text) = (resuelto_en IS NOT NULL)))`.
- `fk_conflicto_edicion_01` (FK): `FOREIGN KEY (id_cambio_almacenado) REFERENCES uml_colaborativo.cambio_modelo(id) ON DELETE SET NULL`.
- `fk_conflicto_edicion_02` (FK): `FOREIGN KEY (id_cambio_entrante) REFERENCES uml_colaborativo.cambio_modelo(id) ON DELETE SET NULL`.
- `fk_conflicto_edicion_03` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_conflicto_edicion_04` (FK): `FOREIGN KEY (resuelto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_conflicto_edicion_05` (FK): `FOREIGN KEY (id_cambio_entrante, id_modelo) REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo)`.
- `fk_conflicto_edicion_06` (FK): `FOREIGN KEY (id_cambio_almacenado, id_modelo) REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo)`.
- `pk_conflicto_edicion_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_conflicto_edicion_01`: `CREATE INDEX ix_conflicto_edicion_01 ON uml_colaborativo.conflicto_edicion USING btree (id_modelo, detectado_en DESC) WHERE ((estado)::text = 'ABIERTO'::text)`.
- `pk_conflicto_edicion_01`: `CREATE UNIQUE INDEX pk_conflicto_edicion_01 ON uml_colaborativo.conflicto_edicion USING btree (id)`.

Delta del incremento 05, pendiente en la base personal:

- `id_solicitud_resolucion uuid NULL`: UUID idempotente de la decisión humana.
- `id_cambio_resolucion uuid NULL`: cambio confirmado creado al aplicar o fusionar.
- `ck_conflicto_edicion_04`: exige fecha de resolución en `RESUELTO` y `DESCARTADO`, y ausencia en `ABIERTO`.
- `ck_conflicto_edicion_05`: una solicitud de resolución solo existe en un conflicto finalizado con resolutor.
- `ck_conflicto_edicion_06`: un cambio resolutor solo existe para `APLICAR_ENTRANTE` o `FUSIONAR` resueltos.
- `fk_conflicto_edicion_07`: `(id_cambio_resolucion, id_modelo)` referencia `cambio_modelo(id, id_modelo)`.
- `uq_conflicto_edicion_01`: UUID de resolución único cuando no es nulo.
- `uq_conflicto_edicion_02`: un único conflicto abierto por cambio entrante.

## DEPENDENCIA_BACKEND

Tabla física: `uml_colaborativo.dependencia_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_configuracion | uuid | No | — |
| id_grupo | character varying(255) | No | — |
| id_artefacto | character varying(255) | No | — |
| version | character varying(80) | Sí | — |
| ambito_dependencia | character varying(64) | No | 'COMPILACION'::character varying |
| es_obligatorio | boolean | No | false |

Restricciones y relaciones:

- `ck_dependencia_backend_01` (CHECK): `CHECK (((ambito_dependencia)::text = ANY (ARRAY[('COMPILACION'::character varying)::text, ('EJECUCION'::character varying)::text, ('PRUEBA'::character varying)::text, ('PROVISTO'::character varying)::text])))`.
- `fk_dependencia_backend_01` (FK): `FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE`.
- `pk_dependencia_backend_01` (PK): `PRIMARY KEY (id)`.
- `uq_dependencia_backend_01` (UQ): `UNIQUE (id_configuracion, id_grupo, id_artefacto)`.

Índices:

- `pk_dependencia_backend_01`: `CREATE UNIQUE INDEX pk_dependencia_backend_01 ON uml_colaborativo.dependencia_backend USING btree (id)`.
- `uq_dependencia_backend_01`: `CREATE UNIQUE INDEX uq_dependencia_backend_01 ON uml_colaborativo.dependencia_backend USING btree (id_configuracion, id_grupo, id_artefacto)`.

## DEPENDENCIA_OPERACION_PROPUESTA

Tabla física: `uml_colaborativo.dependencia_operacion_propuesta`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id_operacion | uuid | No | — |
| id_operacion_requerida | uuid | No | — |

Restricciones y relaciones:

- `ck_dependencia_operacion_propuesta_01` (CHECK): `CHECK ((id_operacion <> id_operacion_requerida))`.
- `fk_dependencia_operacion_propuesta_01` (FK): `FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE`.
- `fk_dependencia_operacion_propuesta_02` (FK): `FOREIGN KEY (id_operacion_requerida) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE`.
- `pk_dependencia_operacion_propuesta_01` (PK): `PRIMARY KEY (id_operacion, id_operacion_requerida)`.

Índices:

- `ix_dependencia_operacion_propuesta_01`: `CREATE INDEX ix_dependencia_operacion_propuesta_01 ON uml_colaborativo.dependencia_operacion_propuesta USING btree (id_operacion_requerida)`.
- `pk_dependencia_operacion_propuesta_01`: `CREATE UNIQUE INDEX pk_dependencia_operacion_propuesta_01 ON uml_colaborativo.dependencia_operacion_propuesta USING btree (id_operacion, id_operacion_requerida)`.

## DESCARGA_ARTEFACTO

Tabla física: `uml_colaborativo.descarga_artefacto`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_generacion | uuid | No | — |
| id_archivo | uuid | No | — |
| descargado_por | uuid | No | — |
| descargado_en | timestamp with time zone | No | clock_timestamp() |
| direccion_ip | inet | Sí | — |
| agente_cliente | text | Sí | — |

Restricciones y relaciones:

- `fk_descarga_artefacto_01` (FK): `FOREIGN KEY (id_generacion) REFERENCES uml_colaborativo.generacion_backend(id) ON DELETE RESTRICT`.
- `fk_descarga_artefacto_02` (FK): `FOREIGN KEY (id_archivo) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE RESTRICT`.
- `fk_descarga_artefacto_03` (FK): `FOREIGN KEY (descargado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `pk_descarga_artefacto_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_descarga_artefacto_01`: `CREATE INDEX ix_descarga_artefacto_01 ON uml_colaborativo.descarga_artefacto USING btree (id_generacion, descargado_en DESC)`.
- `pk_descarga_artefacto_01`: `CREATE UNIQUE INDEX pk_descarga_artefacto_01 ON uml_colaborativo.descarga_artefacto USING btree (id)`.

## GENERACION_BACKEND

Tabla física: `uml_colaborativo.generacion_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_configuracion | uuid | No | — |
| solicitado_por | uuid | No | — |
| numero_compilacion | integer | No | — |
| estado | character varying(64) | No | 'EN_COLA'::character varying |
| id_archivo_salida | uuid | Sí | — |
| id_archivo_registro | uuid | Sí | — |
| informe_transformacion | jsonb | No | '{}'::jsonb |
| detalle_error | text | Sí | — |
| solicitado_en | timestamp with time zone | No | clock_timestamp() |
| iniciado_en | timestamp with time zone | Sí | — |
| completado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_generacion_backend_01` (CHECK): `CHECK ((numero_compilacion > 0))`.
- `ck_generacion_backend_02` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('EN_COLA'::character varying)::text, ('EN_EJECUCION'::character varying)::text, ('GENERADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text])))`.
- `ck_generacion_backend_03` (CHECK): `CHECK ((jsonb_typeof(informe_transformacion) = 'object'::text))`.
- `ck_generacion_backend_04` (CHECK): `CHECK ((((estado)::text <> 'GENERADO'::text) OR ((id_archivo_salida IS NOT NULL) AND (completado_en IS NOT NULL))))`.
- `fk_generacion_backend_01` (FK): `FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE RESTRICT`.
- `fk_generacion_backend_02` (FK): `FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `fk_generacion_backend_03` (FK): `FOREIGN KEY (id_archivo_salida) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL`.
- `fk_generacion_backend_04` (FK): `FOREIGN KEY (id_archivo_registro) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL`.
- `pk_generacion_backend_01` (PK): `PRIMARY KEY (id)`.
- `uq_generacion_backend_01` (UQ): `UNIQUE (id_configuracion, numero_compilacion)`.

Índices:

- `ix_generacion_backend_01`: `CREATE INDEX ix_generacion_backend_01 ON uml_colaborativo.generacion_backend USING btree (estado, solicitado_en)`.
- `pk_generacion_backend_01`: `CREATE UNIQUE INDEX pk_generacion_backend_01 ON uml_colaborativo.generacion_backend USING btree (id)`.
- `uq_generacion_backend_01`: `CREATE UNIQUE INDEX uq_generacion_backend_01 ON uml_colaborativo.generacion_backend USING btree (id_configuracion, numero_compilacion)`.

## HALLAZGO_VALIDACION_BACKEND

Tabla física: `uml_colaborativo.hallazgo_validacion_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_validacion | uuid | No | — |
| severidad | character varying(64) | No | — |
| tipo_comprobacion | character varying(64) | No | — |
| componente | character varying(255) | Sí | — |
| mensaje | text | No | — |
| detalle | jsonb | No | '{}'::jsonb |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_hallazgo_validacion_backend_01` (CHECK): `CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text])))`.
- `ck_hallazgo_validacion_backend_02` (CHECK): `CHECK (((tipo_comprobacion)::text = ANY (ARRAY[('ESTRUCTURA'::character varying)::text, ('MAPEO_MODELO'::character varying)::text, ('COMPILACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text, ('CONFIGURACION_POSTGRESQL'::character varying)::text, ('SEGURIDAD'::character varying)::text])))`.
- `ck_hallazgo_validacion_backend_03` (CHECK): `CHECK ((jsonb_typeof(detalle) = 'object'::text))`.
- `fk_hallazgo_validacion_backend_01` (FK): `FOREIGN KEY (id_validacion) REFERENCES uml_colaborativo.validacion_backend(id) ON DELETE CASCADE`.
- `pk_hallazgo_validacion_backend_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_hallazgo_validacion_backend_01`: `CREATE INDEX ix_hallazgo_validacion_backend_01 ON uml_colaborativo.hallazgo_validacion_backend USING btree (id_validacion)`.
- `pk_hallazgo_validacion_backend_01`: `CREATE UNIQUE INDEX pk_hallazgo_validacion_backend_01 ON uml_colaborativo.hallazgo_validacion_backend USING btree (id)`.

## HALLAZGO_VALIDACION_MODELO

Tabla física: `uml_colaborativo.hallazgo_validacion_modelo`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_validacion | uuid | No | — |
| id_regla | uuid | Sí | — |
| severidad | character varying(64) | No | — |
| tipo_entidad | character varying(64) | Sí | — |
| id_entidad | uuid | Sí | — |
| mensaje | text | No | — |
| recomendacion | text | Sí | — |
| ubicacion | jsonb | No | '{}'::jsonb |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_hallazgo_validacion_modelo_01` (CHECK): `CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text])))`.
- `ck_hallazgo_validacion_modelo_02` (CHECK): `CHECK ((jsonb_typeof(ubicacion) = 'object'::text))`.
- `fk_hallazgo_validacion_modelo_01` (FK): `FOREIGN KEY (id_validacion) REFERENCES uml_colaborativo.validacion_modelo(id) ON DELETE CASCADE`.
- `fk_hallazgo_validacion_modelo_02` (FK): `FOREIGN KEY (id_regla) REFERENCES uml_colaborativo.regla_validacion(id) ON DELETE SET NULL`.
- `pk_hallazgo_validacion_modelo_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_hallazgo_validacion_modelo_01`: `CREATE INDEX ix_hallazgo_validacion_modelo_01 ON uml_colaborativo.hallazgo_validacion_modelo USING btree (id_validacion)`.
- `pk_hallazgo_validacion_modelo_01`: `CREATE UNIQUE INDEX pk_hallazgo_validacion_modelo_01 ON uml_colaborativo.hallazgo_validacion_modelo USING btree (id)`.

## INTERCAMBIO_XMI

Tabla física: `uml_colaborativo.intercambio_xmi`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| id_version_modelo | uuid | Sí | — |
| solicitado_por | uuid | No | — |
| direccion | character varying(64) | No | — |
| variante_xmi | character varying(60) | No | — |
| version_enterprise_architect | character varying(60) | Sí | — |
| estrategia_fusion | character varying(64) | Sí | — |
| ambito | jsonb | No | '{}'::jsonb |
| id_archivo_entrada | uuid | Sí | — |
| id_archivo_salida | uuid | Sí | — |
| estado | character varying(64) | No | 'PENDIENTE'::character varying |
| id_version_importada | uuid | Sí | — |
| informe | jsonb | No | '{}'::jsonb |
| detalle_error | text | Sí | — |
| iniciado_en | timestamp with time zone | Sí | — |
| completado_en | timestamp with time zone | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_intercambio_xmi_01` (CHECK): `CHECK (((direccion)::text = ANY (ARRAY[('IMPORTAR'::character varying)::text, ('EXPORTAR'::character varying)::text])))`.
- `ck_intercambio_xmi_02` (CHECK): `CHECK (((estrategia_fusion IS NULL) OR ((estrategia_fusion)::text = ANY (ARRAY[('FUSIONAR'::character varying)::text, ('REEMPLAZAR'::character varying)::text]))))`.
- `ck_intercambio_xmi_03` (CHECK): `CHECK ((jsonb_typeof(ambito) = 'object'::text))`.
- `ck_intercambio_xmi_04` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ANALIZANDO'::character varying)::text, ('LISTO_PARA_REVISION'::character varying)::text, ('COMPLETADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text])))`.
- `ck_intercambio_xmi_05` (CHECK): `CHECK ((jsonb_typeof(informe) = 'object'::text))`.
- `ck_intercambio_xmi_06` (CHECK): `CHECK (((((direccion)::text = 'IMPORTAR'::text) AND (id_archivo_entrada IS NOT NULL)) OR ((direccion)::text = 'EXPORTAR'::text)))`.
- `fk_intercambio_xmi_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_intercambio_xmi_02` (FK): `FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL`.
- `fk_intercambio_xmi_03` (FK): `FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `fk_intercambio_xmi_04` (FK): `FOREIGN KEY (id_archivo_entrada) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL`.
- `fk_intercambio_xmi_05` (FK): `FOREIGN KEY (id_archivo_salida) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL`.
- `fk_intercambio_xmi_06` (FK): `FOREIGN KEY (id_version_importada) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL`.
- `fk_intercambio_xmi_07` (FK): `FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo)`.
- `fk_intercambio_xmi_08` (FK): `FOREIGN KEY (id_version_importada, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo)`.
- `pk_intercambio_xmi_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_intercambio_xmi_01`: `CREATE INDEX ix_intercambio_xmi_01 ON uml_colaborativo.intercambio_xmi USING btree (id_modelo, creado_en DESC)`.
- `pk_intercambio_xmi_01`: `CREATE UNIQUE INDEX pk_intercambio_xmi_01 ON uml_colaborativo.intercambio_xmi USING btree (id)`.

## MAPEO_PERSISTENCIA_BACKEND

Tabla física: `uml_colaborativo.mapeo_persistencia_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_configuracion | uuid | No | — |
| id_atributo_uml | uuid | Sí | — |
| id_relacion_uml | uuid | Sí | — |
| nombre_tabla | character varying(63) | No | — |
| nombre_columna | character varying(63) | Sí | — |
| tipo_mapeo | character varying(64) | No | — |
| opciones_mapeo | jsonb | No | '{}'::jsonb |

Restricciones y relaciones:

- `ck_mapeo_persistencia_backend_01` (CHECK): `CHECK (((tipo_mapeo)::text = ANY (ARRAY[('COLUMNA'::character varying)::text, ('UNO_A_UNO'::character varying)::text, ('UNO_A_MUCHOS'::character varying)::text, ('MUCHOS_A_UNO'::character varying)::text, ('MUCHOS_A_MUCHOS'::character varying)::text, ('HERENCIA'::character varying)::text])))`.
- `ck_mapeo_persistencia_backend_02` (CHECK): `CHECK ((jsonb_typeof(opciones_mapeo) = 'object'::text))`.
- `ck_mapeo_persistencia_backend_03` (CHECK): `CHECK (((((id_atributo_uml IS NOT NULL))::integer + ((id_relacion_uml IS NOT NULL))::integer) = 1))`.
- `fk_mapeo_persistencia_backend_01` (FK): `FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE`.
- `fk_mapeo_persistencia_backend_02` (FK): `FOREIGN KEY (id_atributo_uml) REFERENCES uml_colaborativo.atributo_uml(id) ON DELETE RESTRICT`.
- `fk_mapeo_persistencia_backend_03` (FK): `FOREIGN KEY (id_relacion_uml) REFERENCES uml_colaborativo.relacion_uml(id) ON DELETE RESTRICT`.
- `pk_mapeo_persistencia_backend_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_mapeo_persistencia_backend_01`: `CREATE UNIQUE INDEX ix_mapeo_persistencia_backend_01 ON uml_colaborativo.mapeo_persistencia_backend USING btree (id_configuracion, id_atributo_uml) WHERE (id_atributo_uml IS NOT NULL)`.
- `ix_mapeo_persistencia_backend_02`: `CREATE UNIQUE INDEX ix_mapeo_persistencia_backend_02 ON uml_colaborativo.mapeo_persistencia_backend USING btree (id_configuracion, id_relacion_uml) WHERE (id_relacion_uml IS NOT NULL)`.
- `pk_mapeo_persistencia_backend_01`: `CREATE UNIQUE INDEX pk_mapeo_persistencia_backend_01 ON uml_colaborativo.mapeo_persistencia_backend USING btree (id)`.

## MAPEO_TIPO_BACKEND

Tabla física: `uml_colaborativo.mapeo_tipo_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_configuracion | uuid | No | — |
| tipo_uml | character varying(180) | No | — |
| tipo_java | character varying(255) | No | — |
| tipo_postgresql | character varying(120) | No | — |
| clase_conversor | character varying(255) | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `fk_mapeo_tipo_backend_01` (FK): `FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE`.
- `pk_mapeo_tipo_backend_01` (PK): `PRIMARY KEY (id)`.
- `uq_mapeo_tipo_backend_01` (UQ): `UNIQUE (id_configuracion, tipo_uml)`.

Índices:

- `pk_mapeo_tipo_backend_01`: `CREATE UNIQUE INDEX pk_mapeo_tipo_backend_01 ON uml_colaborativo.mapeo_tipo_backend USING btree (id)`.
- `uq_mapeo_tipo_backend_01`: `CREATE UNIQUE INDEX uq_mapeo_tipo_backend_01 ON uml_colaborativo.mapeo_tipo_backend USING btree (id_configuracion, tipo_uml)`.

## MIEMBRO_PROYECTO

Tabla física: `uml_colaborativo.miembro_proyecto`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_proyecto | uuid | No | — |
| id_usuario | uuid | No | — |
| estado | character varying(64) | No | 'ACTIVO'::character varying |
| incorporado_en | timestamp with time zone | Sí | — |
| suspendido_en | timestamp with time zone | Sí | — |
| retirado_en | timestamp with time zone | Sí | — |
| motivo_estado | text | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_miembro_proyecto_01` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('INVITADO'::character varying)::text, ('ACTIVO'::character varying)::text, ('SUSPENDIDO'::character varying)::text, ('RETIRADO'::character varying)::text])))`.
- `fk_miembro_proyecto_01` (FK): `FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE`.
- `fk_miembro_proyecto_02` (FK): `FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `pk_miembro_proyecto_01` (PK): `PRIMARY KEY (id)`.
- `trg_miembro_proyecto_02` (trigger diferido): `TRIGGER DEFERRABLE INITIALLY DEFERRED`.
- `uq_miembro_proyecto_01` (UQ): `UNIQUE (id_proyecto, id_usuario)`.

Índices:

- `ix_miembro_proyecto_01`: `CREATE INDEX ix_miembro_proyecto_01 ON uml_colaborativo.miembro_proyecto USING btree (id_usuario, estado)`.
- `ix_miembro_proyecto_02`: `CREATE INDEX ix_miembro_proyecto_02 ON uml_colaborativo.miembro_proyecto USING btree (id_proyecto, estado)`.
- `pk_miembro_proyecto_01`: `CREATE UNIQUE INDEX pk_miembro_proyecto_01 ON uml_colaborativo.miembro_proyecto USING btree (id)`.
- `uq_miembro_proyecto_01`: `CREATE UNIQUE INDEX uq_miembro_proyecto_01 ON uml_colaborativo.miembro_proyecto USING btree (id_proyecto, id_usuario)`.

## MODELO_UML

Tabla física: `uml_colaborativo.modelo_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_proyecto | uuid | No | — |
| nombre | character varying(180) | No | — |
| descripcion | text | Sí | — |
| estado | character varying(64) | No | 'BORRADOR'::character varying |
| revision_actual | bigint | No | 0 |
| id_version_actual | uuid | Sí | — |
| creado_por | uuid | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| archivado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_modelo_uml_01` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('ARCHIVADO'::character varying)::text])))`.
- `ck_modelo_uml_02` (CHECK): `CHECK ((revision_actual >= 0))`.
- `fk_modelo_uml_01` (FK): `FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE`.
- `fk_modelo_uml_02` (FK): `FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_modelo_uml_03` (FK): `FOREIGN KEY (id_version_actual, id) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED`.
- `pk_modelo_uml_01` (PK): `PRIMARY KEY (id)`.
- `trg_modelo_edicion_confirmada` (trigger diferido): `TRIGGER DEFERRABLE INITIALLY DEFERRED`.
- `uq_modelo_uml_01` (UQ): `UNIQUE (id_proyecto, nombre)`.
- `uq_modelo_uml_02` (UQ): `UNIQUE (id, id_proyecto)`.

Índices:

- `ix_modelo_uml_01`: `CREATE INDEX ix_modelo_uml_01 ON uml_colaborativo.modelo_uml USING btree (id_proyecto, estado)`.
- `pk_modelo_uml_01`: `CREATE UNIQUE INDEX pk_modelo_uml_01 ON uml_colaborativo.modelo_uml USING btree (id)`.
- `uq_modelo_uml_01`: `CREATE UNIQUE INDEX uq_modelo_uml_01 ON uml_colaborativo.modelo_uml USING btree (id_proyecto, nombre)`.
- `uq_modelo_uml_02`: `CREATE UNIQUE INDEX uq_modelo_uml_02 ON uml_colaborativo.modelo_uml USING btree (id, id_proyecto)`.

## OPERACION_PROPUESTA

Tabla física: `uml_colaborativo.operacion_propuesta`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_propuesta | uuid | No | — |
| numero_secuencia | integer | No | — |
| tipo_operacion | character varying(64) | No | — |
| tipo_entidad | character varying(64) | No | — |
| id_entidad_destino | uuid | Sí | — |
| valor_actual | jsonb | Sí | — |
| valor_propuesto | jsonb | Sí | — |
| confianza | numeric(5,4) | Sí | — |
| esta_seleccionado | boolean | No | true |
| estado | character varying(64) | No | 'PROPUESTO'::character varying |
| mensajes_validacion | jsonb | No | '[]'::jsonb |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_operacion_propuesta_01` (CHECK): `CHECK ((numero_secuencia > 0))`.
- `ck_operacion_propuesta_02` (CHECK): `CHECK (((tipo_operacion)::text = ANY (ARRAY[('CREAR'::character varying)::text, ('ACTUALIZAR'::character varying)::text, ('ELIMINAR'::character varying)::text])))`.
- `ck_operacion_propuesta_03` (CHECK): `CHECK (((confianza IS NULL) OR ((confianza >= (0)::numeric) AND (confianza <= (1)::numeric))))`.
- `ck_operacion_propuesta_04` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('PROPUESTO'::character varying)::text, ('VALIDO'::character varying)::text, ('INVALIDO'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text])))`.
- `ck_operacion_propuesta_05` (CHECK): `CHECK ((jsonb_typeof(mensajes_validacion) = 'array'::text))`.
- `fk_operacion_propuesta_01` (FK): `FOREIGN KEY (id_propuesta) REFERENCES uml_colaborativo.propuesta_inteligente(id) ON DELETE CASCADE`.
- `pk_operacion_propuesta_01` (PK): `PRIMARY KEY (id)`.
- `uq_operacion_propuesta_01` (UQ): `UNIQUE (id_propuesta, numero_secuencia)`.

Índices:

- `pk_operacion_propuesta_01`: `CREATE UNIQUE INDEX pk_operacion_propuesta_01 ON uml_colaborativo.operacion_propuesta USING btree (id)`.
- `uq_operacion_propuesta_01`: `CREATE UNIQUE INDEX uq_operacion_propuesta_01 ON uml_colaborativo.operacion_propuesta USING btree (id_propuesta, numero_secuencia)`.

## OPERACION_UML

Tabla física: `uml_colaborativo.operacion_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_clase | uuid | No | — |
| nombre | character varying(180) | No | — |
| tipo_retorno | character varying(180) | Sí | — |
| visibilidad | character varying(64) | No | 'PUBLICO'::character varying |
| es_estatico | boolean | No | false |
| es_abstracto | boolean | No | false |
| orden | integer | No | 0 |
| id_externo_xmi | character varying(255) | Sí | — |
| version_fila | bigint | No | 1 |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| eliminado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_operacion_uml_01` (CHECK): `CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text])))`.
- `ck_operacion_uml_02` (CHECK): `CHECK ((orden >= 0))`.
- `ck_operacion_uml_03` (CHECK): `CHECK (((nombre)::text <> ''::text))`.
- `fk_operacion_uml_01` (FK): `FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE`.
- `pk_operacion_uml_01` (PK): `PRIMARY KEY (id)`.
- `trg_operacion_uml_03` (trigger diferido): `TRIGGER DEFERRABLE INITIALLY DEFERRED`.

Índices:

- `ix_operacion_uml_01`: `CREATE INDEX ix_operacion_uml_01 ON uml_colaborativo.operacion_uml USING btree (id_clase, orden) WHERE (eliminado_en IS NULL)`.
- `pk_operacion_uml_01`: `CREATE UNIQUE INDEX pk_operacion_uml_01 ON uml_colaborativo.operacion_uml USING btree (id)`.

## PAQUETE_UML

Tabla física: `uml_colaborativo.paquete_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| id_paquete_padre | uuid | Sí | — |
| nombre | character varying(180) | No | — |
| descripcion | text | Sí | — |
| id_externo_xmi | character varying(255) | Sí | — |
| version_fila | bigint | No | 1 |
| creado_por | uuid | Sí | — |
| actualizado_por | uuid | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| eliminado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_paquete_uml_01` (CHECK): `CHECK (((nombre)::text <> ''::text))`.
- `ck_paquete_uml_02` (CHECK): `CHECK ((id_paquete_padre IS DISTINCT FROM id))`.
- `fk_paquete_uml_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_paquete_uml_02` (FK): `FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_paquete_uml_03` (FK): `FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_paquete_uml_04` (FK): `FOREIGN KEY (id_paquete_padre, id_modelo) REFERENCES uml_colaborativo.paquete_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED`.
- `pk_paquete_uml_01` (PK): `PRIMARY KEY (id)`.
- `uq_paquete_uml_01` (UQ): `UNIQUE (id, id_modelo)`.

Índices:

- `ix_paquete_uml_01`: `CREATE UNIQUE INDEX ix_paquete_uml_01 ON uml_colaborativo.paquete_uml USING btree (id_modelo, COALESCE(id_paquete_padre, '00000000-0000-0000-0000-000000000000'::uuid), lower((nombre)::text)) WHERE (eliminado_en IS NULL)`.
- `ix_paquete_uml_02`: `CREATE INDEX ix_paquete_uml_02 ON uml_colaborativo.paquete_uml USING btree (id_paquete_padre)`.
- `pk_paquete_uml_01`: `CREATE UNIQUE INDEX pk_paquete_uml_01 ON uml_colaborativo.paquete_uml USING btree (id)`.
- `uq_paquete_uml_01`: `CREATE UNIQUE INDEX uq_paquete_uml_01 ON uml_colaborativo.paquete_uml USING btree (id, id_modelo)`.

## PARAMETRO_UML

Tabla física: `uml_colaborativo.parametro_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_operacion | uuid | No | — |
| nombre | character varying(180) | No | — |
| tipo_uml | character varying(180) | No | — |
| direccion | character varying(64) | No | 'ENTRADA'::character varying |
| es_obligatorio | boolean | No | true |
| valor_predeterminado | text | Sí | — |
| orden | integer | No | — |
| id_externo_xmi | character varying(255) | Sí | — |
| version_fila | bigint | No | 1 |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| eliminado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_parametro_uml_01` (CHECK): `CHECK (((direccion)::text = ANY (ARRAY[('ENTRADA'::character varying)::text, ('SALIDA'::character varying)::text, ('ENTRADA_SALIDA'::character varying)::text, ('RETORNO'::character varying)::text])))`.
- `ck_parametro_uml_02` (CHECK): `CHECK ((orden >= 0))`.
- `ck_parametro_uml_03` (CHECK): `CHECK (((nombre)::text <> ''::text))`.
- `ck_parametro_uml_04` (CHECK): `CHECK (((tipo_uml)::text <> ''::text))`.
- `fk_parametro_uml_01` (FK): `FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_uml(id) ON DELETE CASCADE`.
- `pk_parametro_uml_01` (PK): `PRIMARY KEY (id)`.
- `trg_parametro_uml_03` (trigger diferido): `TRIGGER DEFERRABLE INITIALLY DEFERRED`.

Índices:

- `ix_parametro_uml_01`: `CREATE UNIQUE INDEX ix_parametro_uml_01 ON uml_colaborativo.parametro_uml USING btree (id_operacion, orden) WHERE (eliminado_en IS NULL)`.
- `ix_parametro_uml_02`: `CREATE UNIQUE INDEX ix_parametro_uml_02 ON uml_colaborativo.parametro_uml USING btree (id_operacion, lower((nombre)::text)) WHERE (eliminado_en IS NULL)`.
- `pk_parametro_uml_01`: `CREATE UNIQUE INDEX pk_parametro_uml_01 ON uml_colaborativo.parametro_uml USING btree (id)`.

## PARTICIPANTE_SESION_MODELO

Tabla física: `uml_colaborativo.participante_sesion_modelo`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id_sesion_edicion | uuid | No | — |
| id_usuario | uuid | No | — |
| modo_participante | character varying(64) | No | — |
| estado_presencia | character varying(64) | No | 'EN_LINEA'::character varying |
| tipo_entidad_enfocada | character varying(40) | Sí | — |
| id_entidad_enfocada | uuid | Sí | — |
| incorporado_en | timestamp with time zone | No | clock_timestamp() |
| ultima_presencia_en | timestamp with time zone | No | clock_timestamp() |
| salida_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_participante_sesion_modelo_01` (CHECK): `CHECK (((modo_participante)::text = ANY (ARRAY[('EDITOR'::character varying)::text, ('LECTOR'::character varying)::text])))`.
- `ck_participante_sesion_modelo_02` (CHECK): `CHECK (((estado_presencia)::text = ANY (ARRAY[('EN_LINEA'::character varying)::text, ('AUSENTE'::character varying)::text, ('DESCONECTADO'::character varying)::text])))`.
- `fk_participante_sesion_modelo_01` (FK): `FOREIGN KEY (id_sesion_edicion) REFERENCES uml_colaborativo.sesion_edicion_modelo(id) ON DELETE CASCADE`.
- `fk_participante_sesion_modelo_02` (FK): `FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE`.
- `pk_participante_sesion_modelo_01` (PK): `PRIMARY KEY (id_sesion_edicion, id_usuario)`.

Índices:

- `pk_participante_sesion_modelo_01`: `CREATE UNIQUE INDEX pk_participante_sesion_modelo_01 ON uml_colaborativo.participante_sesion_modelo USING btree (id_sesion_edicion, id_usuario)`.

## PERMISO

Tabla física: `uml_colaborativo.permiso`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| codigo | citext | No | — |
| modulo | character varying(64) | No | — |
| accion | character varying(64) | No | — |
| descripcion | text | Sí | — |
| ambito | character varying(64) | No | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_permiso_01` (CHECK): `CHECK (((ambito)::text = ANY (ARRAY[('GLOBAL'::character varying)::text, ('PROYECTO'::character varying)::text, ('AMBOS'::character varying)::text])))`.
- `pk_permiso_01` (PK): `PRIMARY KEY (id)`.
- `uq_permiso_01` (UQ): `UNIQUE (codigo)`.
- `uq_permiso_02` (UQ): `UNIQUE (modulo, accion, ambito)`.

Índices:

- `pk_permiso_01`: `CREATE UNIQUE INDEX pk_permiso_01 ON uml_colaborativo.permiso USING btree (id)`.
- `uq_permiso_01`: `CREATE UNIQUE INDEX uq_permiso_01 ON uml_colaborativo.permiso USING btree (codigo)`.
- `uq_permiso_02`: `CREATE UNIQUE INDEX uq_permiso_02 ON uml_colaborativo.permiso USING btree (modulo, accion, ambito)`.

## PERMISO_MIEMBRO_PROYECTO

Tabla física: `uml_colaborativo.permiso_miembro_proyecto`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_miembro_proyecto | uuid | No | — |
| id_permiso | uuid | No | — |
| decision | character varying(64) | No | — |
| asignado_por | uuid | Sí | — |
| asignado_en | timestamp with time zone | No | clock_timestamp() |
| revocado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_permiso_miembro_proyecto_01` (CHECK): `CHECK (((decision)::text = ANY (ARRAY[('PERMITIR'::character varying)::text, ('DENEGAR'::character varying)::text])))`.
- `ck_permiso_miembro_proyecto_02` (CHECK): `CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))`.
- `fk_permiso_miembro_proyecto_01` (FK): `FOREIGN KEY (id_miembro_proyecto) REFERENCES uml_colaborativo.miembro_proyecto(id) ON DELETE CASCADE`.
- `fk_permiso_miembro_proyecto_02` (FK): `FOREIGN KEY (id_permiso) REFERENCES uml_colaborativo.permiso(id) ON DELETE CASCADE`.
- `fk_permiso_miembro_proyecto_03` (FK): `FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_permiso_miembro_proyecto_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_permiso_miembro_proyecto_01`: `CREATE UNIQUE INDEX ix_permiso_miembro_proyecto_01 ON uml_colaborativo.permiso_miembro_proyecto USING btree (id_miembro_proyecto, id_permiso) WHERE (revocado_en IS NULL)`.
- `ix_permiso_miembro_proyecto_02`: `CREATE INDEX ix_permiso_miembro_proyecto_02 ON uml_colaborativo.permiso_miembro_proyecto USING btree (id_miembro_proyecto)`.
- `pk_permiso_miembro_proyecto_01`: `CREATE UNIQUE INDEX pk_permiso_miembro_proyecto_01 ON uml_colaborativo.permiso_miembro_proyecto USING btree (id)`.

## POSICION_CLASE_UML

Tabla física: `uml_colaborativo.posicion_clase_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id_clase | uuid | No | — |
| x | numeric(12,3) | No | — |
| y | numeric(12,3) | No | — |
| ancho | numeric(12,3) | No | — |
| alto | numeric(12,3) | No | — |
| orden_capa | integer | No | 0 |
| estilo | jsonb | No | '{}'::jsonb |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_posicion_clase_uml_01` (CHECK): `CHECK ((ancho > (0)::numeric))`.
- `ck_posicion_clase_uml_02` (CHECK): `CHECK ((alto > (0)::numeric))`.
- `ck_posicion_clase_uml_03` (CHECK): `CHECK ((jsonb_typeof(estilo) = 'object'::text))`.
- `fk_posicion_clase_uml_01` (FK): `FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE`.
- `pk_posicion_clase_uml_01` (PK): `PRIMARY KEY (id_clase)`.

Índices:

- `pk_posicion_clase_uml_01`: `CREATE UNIQUE INDEX pk_posicion_clase_uml_01 ON uml_colaborativo.posicion_clase_uml USING btree (id_clase)`.

## POSICION_RELACION_UML

Tabla física: `uml_colaborativo.posicion_relacion_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id_relacion | uuid | No | — |
| puntos_intermedios | jsonb | No | '[]'::jsonb |
| etiqueta_x | numeric(12,3) | Sí | — |
| etiqueta_y | numeric(12,3) | Sí | — |
| estilo | jsonb | No | '{}'::jsonb |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_posicion_relacion_uml_01` (CHECK): `CHECK ((jsonb_typeof(puntos_intermedios) = 'array'::text))`.
- `ck_posicion_relacion_uml_02` (CHECK): `CHECK ((jsonb_typeof(estilo) = 'object'::text))`.
- `fk_posicion_relacion_uml_01` (FK): `FOREIGN KEY (id_relacion) REFERENCES uml_colaborativo.relacion_uml(id) ON DELETE CASCADE`.
- `pk_posicion_relacion_uml_01` (PK): `PRIMARY KEY (id_relacion)`.

Índices:

- `pk_posicion_relacion_uml_01`: `CREATE UNIQUE INDEX pk_posicion_relacion_uml_01 ON uml_colaborativo.posicion_relacion_uml USING btree (id_relacion)`.

## PREFERENCIA_DIAGRAMA

Tabla física: `uml_colaborativo.preferencia_diagrama`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id_modelo | uuid | No | — |
| id_usuario | uuid | No | — |
| escala | numeric(8,4) | No | 1 |
| desplazamiento_x | numeric(12,3) | No | 0 |
| desplazamiento_y | numeric(12,3) | No | 0 |
| mostrar_cuadricula | boolean | No | true |
| ajustar_cuadricula | boolean | No | true |
| preferencias | jsonb | No | '{}'::jsonb |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_preferencia_diagrama_01` (CHECK): `CHECK ((escala > (0)::numeric))`.
- `ck_preferencia_diagrama_02` (CHECK): `CHECK ((jsonb_typeof(preferencias) = 'object'::text))`.
- `fk_preferencia_diagrama_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_preferencia_diagrama_02` (FK): `FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE`.
- `pk_preferencia_diagrama_01` (PK): `PRIMARY KEY (id_modelo, id_usuario)`.

Índices:

- `pk_preferencia_diagrama_01`: `CREATE UNIQUE INDEX pk_preferencia_diagrama_01 ON uml_colaborativo.preferencia_diagrama USING btree (id_modelo, id_usuario)`.

## PROPUESTA_INTELIGENTE

Tabla física: `uml_colaborativo.propuesta_inteligente`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_solicitud | uuid | No | — |
| estado | character varying(64) | No | 'BORRADOR'::character varying |
| resumen | text | Sí | — |
| confianza_general | numeric(5,4) | Sí | — |
| revisado_por | uuid | Sí | — |
| revisado_en | timestamp with time zone | Sí | — |
| aplicado_por | uuid | Sí | — |
| id_version_aplicada | uuid | Sí | — |
| aplicado_en | timestamp with time zone | Sí | — |
| rechazado_por | uuid | Sí | — |
| rechazado_en | timestamp with time zone | Sí | — |
| motivo_rechazo | text | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_propuesta_inteligente_01` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('LISTO'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text, ('FALLIDO'::character varying)::text])))`.
- `ck_propuesta_inteligente_02` (CHECK): `CHECK (((confianza_general IS NULL) OR ((confianza_general >= (0)::numeric) AND (confianza_general <= (1)::numeric))))`.
- `ck_propuesta_inteligente_03` (CHECK): `CHECK (((((estado)::text <> 'APLICADO'::text) OR ((aplicado_en IS NOT NULL) AND (aplicado_por IS NOT NULL) AND (id_version_aplicada IS NOT NULL))) AND (((estado)::text <> 'RECHAZADO'::text) OR ((rechazado_en IS NOT NULL) AND (rechazado_por IS NOT NULL))) AND (NOT ((aplicado_en IS NOT NULL) AND (rechazado_en IS NOT NULL)))))`.
- `fk_propuesta_inteligente_01` (FK): `FOREIGN KEY (id_solicitud) REFERENCES uml_colaborativo.solicitud_inteligente(id) ON DELETE CASCADE`.
- `fk_propuesta_inteligente_02` (FK): `FOREIGN KEY (revisado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_propuesta_inteligente_03` (FK): `FOREIGN KEY (aplicado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_propuesta_inteligente_04` (FK): `FOREIGN KEY (id_version_aplicada) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL`.
- `fk_propuesta_inteligente_05` (FK): `FOREIGN KEY (rechazado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_propuesta_inteligente_01` (PK): `PRIMARY KEY (id)`.
- `uq_propuesta_inteligente_01` (UQ): `UNIQUE (id_solicitud)`.

Índices:

- `ix_propuesta_inteligente_01`: `CREATE INDEX ix_propuesta_inteligente_01 ON uml_colaborativo.propuesta_inteligente USING btree (estado, creado_en)`.
- `pk_propuesta_inteligente_01`: `CREATE UNIQUE INDEX pk_propuesta_inteligente_01 ON uml_colaborativo.propuesta_inteligente USING btree (id)`.
- `uq_propuesta_inteligente_01`: `CREATE UNIQUE INDEX uq_propuesta_inteligente_01 ON uml_colaborativo.propuesta_inteligente USING btree (id_solicitud)`.

## PROYECTO

Tabla física: `uml_colaborativo.proyecto`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| codigo | citext | No | — |
| nombre | character varying(180) | No | — |
| descripcion | text | Sí | — |
| id_usuario_propietario | uuid | No | — |
| estado | character varying(64) | No | 'ACTIVO'::character varying |
| visibilidad | character varying(64) | No | 'PRIVADO'::character varying |
| opciones | jsonb | No | '{}'::jsonb |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| archivado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_proyecto_01` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('ARCHIVADO'::character varying)::text, ('SUSPENDIDO'::character varying)::text])))`.
- `ck_proyecto_02` (CHECK): `CHECK (((visibilidad)::text = ANY (ARRAY[('PRIVADO'::character varying)::text, ('INTERNO'::character varying)::text])))`.
- `ck_proyecto_03` (CHECK): `CHECK ((jsonb_typeof(opciones) = 'object'::text))`.
- `ck_proyecto_04` (CHECK): `CHECK (((nombre)::text <> ''::text))`.
- `ck_proyecto_05` (CHECK): `CHECK (((btrim((codigo)::text) <> ''::text) AND (btrim((nombre)::text) <> ''::text)))`.
- `fk_proyecto_01` (FK): `FOREIGN KEY (id_usuario_propietario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `pk_proyecto_01` (PK): `PRIMARY KEY (id)`.
- `trg_proyecto_02` (trigger diferido): `TRIGGER DEFERRABLE INITIALLY DEFERRED`.
- `uq_proyecto_01` (UQ): `UNIQUE (codigo)`.

Índices:

- `ix_proyecto_01`: `CREATE INDEX ix_proyecto_01 ON uml_colaborativo.proyecto USING btree (id_usuario_propietario, estado)`.
- `pk_proyecto_01`: `CREATE UNIQUE INDEX pk_proyecto_01 ON uml_colaborativo.proyecto USING btree (id)`.
- `uq_proyecto_01`: `CREATE UNIQUE INDEX uq_proyecto_01 ON uml_colaborativo.proyecto USING btree (codigo)`.

## REGLA_VALIDACION

Tabla física: `uml_colaborativo.regla_validacion`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| codigo | citext | No | — |
| nombre | character varying(180) | No | — |
| descripcion | text | No | — |
| severidad | character varying(64) | No | — |
| aplica_a | character varying(64) | No | — |
| esta_activo | boolean | No | true |
| clave_implementacion | character varying(200) | No | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_regla_validacion_01` (CHECK): `CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text])))`.
- `pk_regla_validacion_01` (PK): `PRIMARY KEY (id)`.
- `uq_regla_validacion_01` (UQ): `UNIQUE (codigo)`.

Índices:

- `pk_regla_validacion_01`: `CREATE UNIQUE INDEX pk_regla_validacion_01 ON uml_colaborativo.regla_validacion USING btree (id)`.
- `uq_regla_validacion_01`: `CREATE UNIQUE INDEX uq_regla_validacion_01 ON uml_colaborativo.regla_validacion USING btree (codigo)`.

## RELACION_UML

Tabla física: `uml_colaborativo.relacion_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| id_clase_origen | uuid | No | — |
| id_clase_destino | uuid | No | — |
| nombre | character varying(180) | Sí | — |
| tipo_relacion | character varying(64) | No | — |
| direccion | character varying(64) | No | 'SIN_DIRECCION'::character varying |
| rol_origen | character varying(180) | Sí | — |
| rol_destino | character varying(180) | Sí | — |
| minimo_origen | integer | Sí | — |
| maximo_origen | integer | Sí | — |
| minimo_destino | integer | Sí | — |
| maximo_destino | integer | Sí | — |
| origen_navegable | boolean | Sí | — |
| destino_navegable | boolean | Sí | — |
| id_externo_xmi | character varying(255) | Sí | — |
| version_fila | bigint | No | 1 |
| creado_por | uuid | Sí | — |
| actualizado_por | uuid | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| eliminado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_relacion_uml_01` (CHECK): `CHECK (((tipo_relacion)::text = ANY (ARRAY[('ASOCIACION'::character varying)::text, ('AGREGACION'::character varying)::text, ('COMPOSICION'::character varying)::text, ('GENERALIZACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text])))`.
- `ck_relacion_uml_02` (CHECK): `CHECK (((direccion)::text = ANY (ARRAY[('SIN_DIRECCION'::character varying)::text, ('ORIGEN_A_DESTINO'::character varying)::text, ('DESTINO_A_ORIGEN'::character varying)::text, ('BIDIRECCIONAL'::character varying)::text])))`.
- `ck_relacion_uml_03` (CHECK): `CHECK (((minimo_origen IS NULL) OR (minimo_origen >= 0)))`.
- `ck_relacion_uml_04` (CHECK): `CHECK (((maximo_origen IS NULL) OR (maximo_origen >= 0)))`.
- `ck_relacion_uml_05` (CHECK): `CHECK (((minimo_destino IS NULL) OR (minimo_destino >= 0)))`.
- `ck_relacion_uml_06` (CHECK): `CHECK (((maximo_destino IS NULL) OR (maximo_destino >= 0)))`.
- `ck_relacion_uml_07` (CHECK): `CHECK (((maximo_origen IS NULL) OR (minimo_origen IS NULL) OR (maximo_origen >= minimo_origen)))`.
- `ck_relacion_uml_08` (CHECK): `CHECK (((maximo_destino IS NULL) OR (minimo_destino IS NULL) OR (maximo_destino >= minimo_destino)))`.
- `ck_relacion_uml_09` (CHECK): `CHECK ((((tipo_relacion)::text <> 'GENERALIZACION'::text) OR (id_clase_origen <> id_clase_destino)))`.
- `ck_relacion_uml_10` (CHECK): `CHECK (((((tipo_relacion)::text = ANY (ARRAY[('ASOCIACION'::character varying)::text, ('AGREGACION'::character varying)::text, ('COMPOSICION'::character varying)::text])) AND (minimo_origen IS NOT NULL) AND (minimo_destino IS NOT NULL)) OR (((tipo_relacion)::text = ANY (ARRAY[('GENERALIZACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text])) AND (minimo_origen IS NULL) AND (maximo_origen IS NULL) AND (minimo_destino IS NULL) AND (maximo_destino IS NULL))))`.
- `fk_relacion_uml_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_relacion_uml_02` (FK): `FOREIGN KEY (id_clase_origen) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT`.
- `fk_relacion_uml_03` (FK): `FOREIGN KEY (id_clase_destino) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT`.
- `fk_relacion_uml_04` (FK): `FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_relacion_uml_05` (FK): `FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_relacion_uml_06` (FK): `FOREIGN KEY (id_clase_origen, id_modelo) REFERENCES uml_colaborativo.clase_uml(id, id_modelo)`.
- `fk_relacion_uml_07` (FK): `FOREIGN KEY (id_clase_destino, id_modelo) REFERENCES uml_colaborativo.clase_uml(id, id_modelo)`.
- `pk_relacion_uml_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_relacion_uml_01`: `CREATE UNIQUE INDEX ix_relacion_uml_01 ON uml_colaborativo.relacion_uml USING btree (id_modelo, id_externo_xmi) WHERE ((eliminado_en IS NULL) AND (id_externo_xmi IS NOT NULL))`.
- `ix_relacion_uml_02`: `CREATE INDEX ix_relacion_uml_02 ON uml_colaborativo.relacion_uml USING btree (id_modelo) WHERE (eliminado_en IS NULL)`.
- `ix_relacion_uml_03`: `CREATE INDEX ix_relacion_uml_03 ON uml_colaborativo.relacion_uml USING btree (id_clase_origen)`.
- `ix_relacion_uml_04`: `CREATE INDEX ix_relacion_uml_04 ON uml_colaborativo.relacion_uml USING btree (id_clase_destino)`.
- `pk_relacion_uml_01`: `CREATE UNIQUE INDEX pk_relacion_uml_01 ON uml_colaborativo.relacion_uml USING btree (id)`.

## ROL

Tabla física: `uml_colaborativo.rol`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| codigo | citext | No | — |
| nombre | character varying(120) | No | — |
| descripcion | text | Sí | — |
| ambito | character varying(64) | No | — |
| es_del_sistema | boolean | No | false |
| estado | character varying(64) | No | 'ACTIVO'::character varying |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_rol_01` (CHECK): `CHECK (((ambito)::text = ANY (ARRAY[('GLOBAL'::character varying)::text, ('PROYECTO'::character varying)::text, ('AMBOS'::character varying)::text])))`.
- `ck_rol_02` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('INACTIVO'::character varying)::text])))`.
- `pk_rol_01` (PK): `PRIMARY KEY (id)`.
- `uq_rol_01` (UQ): `UNIQUE (codigo)`.

Índices:

- `pk_rol_01`: `CREATE UNIQUE INDEX pk_rol_01 ON uml_colaborativo.rol USING btree (id)`.
- `uq_rol_01`: `CREATE UNIQUE INDEX uq_rol_01 ON uml_colaborativo.rol USING btree (codigo)`.

## ROL_MIEMBRO_PROYECTO

Tabla física: `uml_colaborativo.rol_miembro_proyecto`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_miembro_proyecto | uuid | No | — |
| id_rol | uuid | No | — |
| asignado_por | uuid | Sí | — |
| asignado_en | timestamp with time zone | No | clock_timestamp() |
| revocado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_rol_miembro_proyecto_01` (CHECK): `CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))`.
- `fk_rol_miembro_proyecto_01` (FK): `FOREIGN KEY (id_miembro_proyecto) REFERENCES uml_colaborativo.miembro_proyecto(id) ON DELETE CASCADE`.
- `fk_rol_miembro_proyecto_02` (FK): `FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE RESTRICT`.
- `fk_rol_miembro_proyecto_03` (FK): `FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_rol_miembro_proyecto_01` (PK): `PRIMARY KEY (id)`.
- `trg_rol_miembro_proyecto_01` (trigger diferido): `TRIGGER DEFERRABLE INITIALLY DEFERRED`.

Índices:

- `ix_rol_miembro_por_rol`: `CREATE INDEX ix_rol_miembro_por_rol ON uml_colaborativo.rol_miembro_proyecto USING btree (id_rol) WHERE (revocado_en IS NULL)`.
- `ix_rol_miembro_proyecto_01`: `CREATE UNIQUE INDEX ix_rol_miembro_proyecto_01 ON uml_colaborativo.rol_miembro_proyecto USING btree (id_miembro_proyecto, id_rol) WHERE (revocado_en IS NULL)`.
- `ix_rol_miembro_proyecto_02`: `CREATE INDEX ix_rol_miembro_proyecto_02 ON uml_colaborativo.rol_miembro_proyecto USING btree (id_miembro_proyecto)`.
- `pk_rol_miembro_proyecto_01`: `CREATE UNIQUE INDEX pk_rol_miembro_proyecto_01 ON uml_colaborativo.rol_miembro_proyecto USING btree (id)`.

## ROL_PERMISO

Tabla física: `uml_colaborativo.rol_permiso`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id_rol | uuid | No | — |
| id_permiso | uuid | No | — |
| concedido_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `fk_rol_permiso_01` (FK): `FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE CASCADE`.
- `fk_rol_permiso_02` (FK): `FOREIGN KEY (id_permiso) REFERENCES uml_colaborativo.permiso(id) ON DELETE CASCADE`.
- `pk_rol_permiso_01` (PK): `PRIMARY KEY (id_rol, id_permiso)`.

Índices:

- `ix_rol_permiso_01`: `CREATE INDEX ix_rol_permiso_01 ON uml_colaborativo.rol_permiso USING btree (id_permiso)`.
- `pk_rol_permiso_01`: `CREATE UNIQUE INDEX pk_rol_permiso_01 ON uml_colaborativo.rol_permiso USING btree (id_rol, id_permiso)`.

## SECRETO_SERVICIO

Tabla física: `uml_colaborativo.secreto_servicio`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_servicio | uuid | No | — |
| contenido_cifrado | bytea | No | — |
| algoritmo_cifrado | character varying(40) | No | — |
| version_clave | character varying(40) | No | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| rotado_en | timestamp with time zone | Sí | — |
| retirado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `fk_secreto_servicio_01` (FK): `FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE CASCADE`.
- `pk_secreto_servicio_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_secreto_servicio_01`: `CREATE UNIQUE INDEX ix_secreto_servicio_01 ON uml_colaborativo.secreto_servicio USING btree (id_servicio) WHERE (retirado_en IS NULL)`.
- `pk_secreto_servicio_01`: `CREATE UNIQUE INDEX pk_secreto_servicio_01 ON uml_colaborativo.secreto_servicio USING btree (id)`.

## SERVICIO_PLATAFORMA

Tabla física: `uml_colaborativo.servicio_plataforma`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| nombre | character varying(140) | No | — |
| proveedor | character varying(100) | No | — |
| tipo_servicio | character varying(64) | No | — |
| url_servicio | text | Sí | — |
| configuracion | jsonb | No | '{}'::jsonb |
| estado | character varying(64) | No | 'SIN_VERIFICAR'::character varying |
| creado_por | uuid | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_servicio_plataforma_01` (CHECK): `CHECK (((tipo_servicio)::text = ANY (ARRAY[('IA_TEXTO'::character varying)::text, ('VOZ_A_TEXTO'::character varying)::text, ('IA_IMAGEN'::character varying)::text])))`.
- `ck_servicio_plataforma_02` (CHECK): `CHECK ((jsonb_typeof(configuracion) = 'object'::text))`.
- `ck_servicio_plataforma_03` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('INACTIVO'::character varying)::text, ('SIN_VERIFICAR'::character varying)::text, ('NO_DISPONIBLE'::character varying)::text])))`.
- `fk_servicio_plataforma_01` (FK): `FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_servicio_plataforma_01` (PK): `PRIMARY KEY (id)`.
- `uq_servicio_plataforma_01` (UQ): `UNIQUE (nombre)`.

Índices:

- `pk_servicio_plataforma_01`: `CREATE UNIQUE INDEX pk_servicio_plataforma_01 ON uml_colaborativo.servicio_plataforma USING btree (id)`.
- `uq_servicio_plataforma_01`: `CREATE UNIQUE INDEX uq_servicio_plataforma_01 ON uml_colaborativo.servicio_plataforma USING btree (nombre)`.

## SESION_AUTENTICACION

Tabla física: `uml_colaborativo.sesion_autenticacion`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_usuario | uuid | No | — |
| hash_token | bytea | No | — |
| hash_token_renovacion | bytea | Sí | — |
| direccion_ip | inet | Sí | — |
| agente_cliente | text | Sí | — |
| emitido_en | timestamp with time zone | No | clock_timestamp() |
| expira_en | timestamp with time zone | No | — |
| revocado_en | timestamp with time zone | Sí | — |
| motivo_revocacion | character varying(200) | Sí | — |

Restricciones y relaciones:

- `ck_sesion_autenticacion_01` (CHECK): `CHECK ((expira_en > emitido_en))`.
- `ck_sesion_autenticacion_02` (CHECK): `CHECK (((octet_length(hash_token) = 32) AND ((hash_token_renovacion IS NULL) OR (octet_length(hash_token_renovacion) = 32))))`.
- `ck_sesion_autenticacion_03` (CHECK): `CHECK (((revocado_en IS NULL) OR (revocado_en >= emitido_en)))`.
- `fk_sesion_autenticacion_01` (FK): `FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE`.
- `pk_sesion_autenticacion_01` (PK): `PRIMARY KEY (id)`.
- `uq_sesion_autenticacion_01` (UQ): `UNIQUE (hash_token)`.
- `uq_sesion_autenticacion_02` (UQ): `UNIQUE (hash_token_renovacion)`.

Índices:

- `ix_sesion_autenticacion_01`: `CREATE INDEX ix_sesion_autenticacion_01 ON uml_colaborativo.sesion_autenticacion USING btree (id_usuario, expira_en DESC) WHERE (revocado_en IS NULL)`.
- `pk_sesion_autenticacion_01`: `CREATE UNIQUE INDEX pk_sesion_autenticacion_01 ON uml_colaborativo.sesion_autenticacion USING btree (id)`.
- `uq_sesion_autenticacion_01`: `CREATE UNIQUE INDEX uq_sesion_autenticacion_01 ON uml_colaborativo.sesion_autenticacion USING btree (hash_token)`.
- `uq_sesion_autenticacion_02`: `CREATE UNIQUE INDEX uq_sesion_autenticacion_02 ON uml_colaborativo.sesion_autenticacion USING btree (hash_token_renovacion)`.

## SESION_EDICION_MODELO

Tabla física: `uml_colaborativo.sesion_edicion_modelo`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| abierto_por | uuid | No | — |
| modo | character varying(64) | No | — |
| estado | character varying(64) | No | 'ACTIVO'::character varying |
| revision_base | bigint | No | — |
| abierto_en | timestamp with time zone | No | clock_timestamp() |
| ultima_actividad_en | timestamp with time zone | No | clock_timestamp() |
| cerrado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_sesion_edicion_modelo_01` (CHECK): `CHECK ((revision_base >= 0))`.
- `ck_sesion_edicion_modelo_02` (CHECK): `CHECK (((modo)::text = ANY (ARRAY[('EDICION'::character varying)::text, ('SOLO_LECTURA'::character varying)::text])))`.
- `ck_sesion_edicion_modelo_03` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('CERRADO'::character varying)::text, ('INTERRUMPIDO'::character varying)::text])))`.
- `fk_sesion_edicion_modelo_01` (FK): `FOREIGN KEY (abierto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `fk_sesion_edicion_modelo_02` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `pk_sesion_edicion_modelo_01` (PK): `PRIMARY KEY (id)`.
- `uq_sesion_edicion_modelo_01` (UQ): `UNIQUE (id, id_modelo)`.

Índices:

- `ix_sesion_edicion_modelo_01`: `CREATE INDEX ix_sesion_edicion_modelo_01 ON uml_colaborativo.sesion_edicion_modelo USING btree (id_modelo, ultima_actividad_en DESC) WHERE ((estado)::text = 'ACTIVO'::text)`.
- `pk_sesion_edicion_modelo_01`: `CREATE UNIQUE INDEX pk_sesion_edicion_modelo_01 ON uml_colaborativo.sesion_edicion_modelo USING btree (id)`.
- `uq_sesion_edicion_modelo_01`: `CREATE UNIQUE INDEX uq_sesion_edicion_modelo_01 ON uml_colaborativo.sesion_edicion_modelo USING btree (id, id_modelo)`.

## SOLICITUD_INTELIGENTE

Tabla física: `uml_colaborativo.solicitud_inteligente`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| solicitado_por | uuid | No | — |
| id_servicio | uuid | Sí | — |
| tipo_entrada | character varying(64) | No | — |
| id_archivo_origen | uuid | Sí | — |
| texto_original | text | Sí | — |
| texto_transcrito | text | Sí | — |
| texto_corregido | text | Sí | — |
| codigo_idioma | character varying(16) | Sí | — |
| revision_base | bigint | No | — |
| estado | character varying(64) | No | 'RECIBIDO'::character varying |
| respuesta_servicio | jsonb | Sí | — |
| detalle_error | text | Sí | — |
| recibido_en | timestamp with time zone | No | clock_timestamp() |
| completado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_solicitud_inteligente_01` (CHECK): `CHECK (((tipo_entrada)::text = ANY (ARRAY[('TEXTO'::character varying)::text, ('VOZ'::character varying)::text, ('IMAGEN'::character varying)::text])))`.
- `ck_solicitud_inteligente_02` (CHECK): `CHECK ((revision_base >= 0))`.
- `ck_solicitud_inteligente_03` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('RECIBIDO'::character varying)::text, ('PROCESANDO'::character varying)::text, ('COMPLETADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text])))`.
- `ck_solicitud_inteligente_04` (CHECK): `CHECK (((((tipo_entrada)::text = 'TEXTO'::text) AND (texto_original IS NOT NULL)) OR (((tipo_entrada)::text = ANY (ARRAY[('VOZ'::character varying)::text, ('IMAGEN'::character varying)::text])) AND (id_archivo_origen IS NOT NULL))))`.
- `ck_solicitud_inteligente_05` (CHECK): `CHECK ((((tipo_entrada)::text <> 'TEXTO'::text) OR (btrim(texto_original) <> ''::text)))`.
- `fk_solicitud_inteligente_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_solicitud_inteligente_02` (FK): `FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `fk_solicitud_inteligente_03` (FK): `FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE SET NULL`.
- `fk_solicitud_inteligente_04` (FK): `FOREIGN KEY (id_archivo_origen) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL`.
- `pk_solicitud_inteligente_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_solicitud_inteligente_01`: `CREATE INDEX ix_solicitud_inteligente_01 ON uml_colaborativo.solicitud_inteligente USING btree (id_modelo, recibido_en DESC)`.
- `pk_solicitud_inteligente_01`: `CREATE UNIQUE INDEX pk_solicitud_inteligente_01 ON uml_colaborativo.solicitud_inteligente USING btree (id)`.

## USUARIO

Tabla física: `uml_colaborativo.usuario`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| nombre_usuario | citext | No | — |
| correo_electronico | citext | No | — |
| nombre_completo | character varying(160) | No | — |
| hash_contrasena | text | No | — |
| estado | character varying(64) | No | 'ACTIVO'::character varying |
| intentos_fallidos_acceso | smallint | No | 0 |
| bloqueado_hasta | timestamp with time zone | Sí | — |
| ultimo_acceso_en | timestamp with time zone | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |
| actualizado_en | timestamp with time zone | No | clock_timestamp() |
| desactivado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_usuario_01` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ACTIVO'::character varying)::text, ('BLOQUEADO'::character varying)::text, ('INACTIVO'::character varying)::text])))`.
- `ck_usuario_02` (CHECK): `CHECK ((intentos_fallidos_acceso >= 0))`.
- `ck_usuario_03` (CHECK): `CHECK ((nombre_usuario <> ''::citext))`.
- `ck_usuario_04` (CHECK): `CHECK ((correo_electronico <> ''::citext))`.
- `ck_usuario_05` (CHECK): `CHECK (((btrim((nombre_completo)::text) <> ''::text) AND (btrim((nombre_usuario)::text) <> ''::text) AND (btrim((correo_electronico)::text) <> ''::text)))`.
- `pk_usuario_01` (PK): `PRIMARY KEY (id)`.
- `uq_usuario_01` (UQ): `UNIQUE (nombre_usuario)`.
- `uq_usuario_02` (UQ): `UNIQUE (correo_electronico)`.

Índices:

- `pk_usuario_01`: `CREATE UNIQUE INDEX pk_usuario_01 ON uml_colaborativo.usuario USING btree (id)`.
- `uq_usuario_01`: `CREATE UNIQUE INDEX uq_usuario_01 ON uml_colaborativo.usuario USING btree (nombre_usuario)`.
- `uq_usuario_02`: `CREATE UNIQUE INDEX uq_usuario_02 ON uml_colaborativo.usuario USING btree (correo_electronico)`.

## USUARIO_ROL

Tabla física: `uml_colaborativo.usuario_rol`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_usuario | uuid | No | — |
| id_rol | uuid | No | — |
| asignado_por | uuid | Sí | — |
| asignado_en | timestamp with time zone | No | clock_timestamp() |
| revocado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_usuario_rol_01` (CHECK): `CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))`.
- `fk_usuario_rol_01` (FK): `FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT`.
- `fk_usuario_rol_02` (FK): `FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE RESTRICT`.
- `fk_usuario_rol_03` (FK): `FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `pk_usuario_rol_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_usuario_rol_01`: `CREATE UNIQUE INDEX ix_usuario_rol_01 ON uml_colaborativo.usuario_rol USING btree (id_usuario, id_rol) WHERE (revocado_en IS NULL)`.
- `ix_usuario_rol_por_rol`: `CREATE INDEX ix_usuario_rol_por_rol ON uml_colaborativo.usuario_rol USING btree (id_rol) WHERE (revocado_en IS NULL)`.
- `pk_usuario_rol_01`: `CREATE UNIQUE INDEX pk_usuario_rol_01 ON uml_colaborativo.usuario_rol USING btree (id)`.

## VALIDACION_BACKEND

Tabla física: `uml_colaborativo.validacion_backend`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_generacion | uuid | No | — |
| solicitado_por | uuid | Sí | — |
| estado | character varying(64) | No | 'EN_EJECUCION'::character varying |
| comprobaciones_aprobadas | integer | No | 0 |
| cantidad_advertencias | integer | No | 0 |
| cantidad_errores | integer | No | 0 |
| id_archivo_informe | uuid | Sí | — |
| iniciado_en | timestamp with time zone | No | clock_timestamp() |
| completado_en | timestamp with time zone | Sí | — |

Restricciones y relaciones:

- `ck_resultado_validacion_coherente` (CHECK): `CHECK (((((estado)::text = 'EN_EJECUCION'::text) OR (completado_en IS NOT NULL)) AND ((completado_en IS NULL) OR (completado_en >= iniciado_en)) AND (((estado)::text <> 'VALIDO'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias = 0))) AND (((estado)::text <> 'VALIDO_CON_ADVERTENCIAS'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias > 0))) AND (((estado)::text <> 'INVALIDO'::text) OR (cantidad_errores > 0))))`.
- `ck_validacion_backend_01` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('EN_EJECUCION'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('FALLIDO'::character varying)::text, ('INCOMPLETO'::character varying)::text])))`.
- `ck_validacion_backend_02` (CHECK): `CHECK ((comprobaciones_aprobadas >= 0))`.
- `ck_validacion_backend_03` (CHECK): `CHECK ((cantidad_advertencias >= 0))`.
- `ck_validacion_backend_04` (CHECK): `CHECK ((cantidad_errores >= 0))`.
- `fk_validacion_backend_01` (FK): `FOREIGN KEY (id_generacion) REFERENCES uml_colaborativo.generacion_backend(id) ON DELETE CASCADE`.
- `fk_validacion_backend_02` (FK): `FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_validacion_backend_03` (FK): `FOREIGN KEY (id_archivo_informe) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL`.
- `pk_validacion_backend_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_validacion_backend_01`: `CREATE INDEX ix_validacion_backend_01 ON uml_colaborativo.validacion_backend USING btree (id_generacion, iniciado_en DESC, id DESC)`.
- `pk_validacion_backend_01`: `CREATE UNIQUE INDEX pk_validacion_backend_01 ON uml_colaborativo.validacion_backend USING btree (id)`.

## VALIDACION_MODELO

Tabla física: `uml_colaborativo.validacion_modelo`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| id_version_modelo | uuid | Sí | — |
| revision_validada | bigint | No | — |
| solicitado_por | uuid | Sí | — |
| estado | character varying(64) | No | — |
| cantidad_errores | integer | No | 0 |
| cantidad_advertencias | integer | No | 0 |
| iniciado_en | timestamp with time zone | No | clock_timestamp() |
| completado_en | timestamp with time zone | Sí | — |
| resumen | jsonb | No | '{}'::jsonb |

Restricciones y relaciones:

- `ck_resultado_validacion_coherente` (CHECK): `CHECK (((((estado)::text = 'EN_EJECUCION'::text) OR (completado_en IS NOT NULL)) AND ((completado_en IS NULL) OR (completado_en >= iniciado_en)) AND (((estado)::text <> 'VALIDO'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias = 0))) AND (((estado)::text <> 'VALIDO_CON_ADVERTENCIAS'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias > 0))) AND (((estado)::text <> 'INVALIDO'::text) OR (cantidad_errores > 0))))`.
- `ck_validacion_modelo_01` (CHECK): `CHECK ((revision_validada >= 0))`.
- `ck_validacion_modelo_02` (CHECK): `CHECK (((estado)::text = ANY (ARRAY[('EN_EJECUCION'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('FALLIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text])))`.
- `ck_validacion_modelo_03` (CHECK): `CHECK ((cantidad_errores >= 0))`.
- `ck_validacion_modelo_04` (CHECK): `CHECK ((cantidad_advertencias >= 0))`.
- `ck_validacion_modelo_05` (CHECK): `CHECK ((jsonb_typeof(resumen) = 'object'::text))`.
- `fk_validacion_modelo_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_validacion_modelo_02` (FK): `FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL`.
- `fk_validacion_modelo_03` (FK): `FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_validacion_modelo_04` (FK): `FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo)`.
- `pk_validacion_modelo_01` (PK): `PRIMARY KEY (id)`.

Índices:

- `ix_validacion_modelo_01`: `CREATE INDEX ix_validacion_modelo_01 ON uml_colaborativo.validacion_modelo USING btree (id_modelo, iniciado_en DESC)`.
- `pk_validacion_modelo_01`: `CREATE UNIQUE INDEX pk_validacion_modelo_01 ON uml_colaborativo.validacion_modelo USING btree (id)`.

## VERSION_MODELO_UML

Tabla física: `uml_colaborativo.version_modelo_uml`.

| Atributo | Tipo PostgreSQL | Admite NULL | Predeterminado |
|---|---|---|---|
| id | uuid | No | gen_random_uuid() |
| id_modelo | uuid | No | — |
| numero_version | integer | No | — |
| revision | bigint | No | — |
| id_version_padre | uuid | Sí | — |
| id_version_restaurada | uuid | Sí | — |
| instantanea | jsonb | No | — |
| hash_instantanea_sha256 | character(64) | No | — |
| estado_validacion | character varying(64) | No | 'SIN_VALIDAR'::character varying |
| resumen_cambio | text | Sí | — |
| creado_por | uuid | Sí | — |
| creado_en | timestamp with time zone | No | clock_timestamp() |

Restricciones y relaciones:

- `ck_version_modelo_uml_01` (CHECK): `CHECK ((numero_version > 0))`.
- `ck_version_modelo_uml_02` (CHECK): `CHECK ((revision >= 0))`.
- `ck_version_modelo_uml_03` (CHECK): `CHECK ((jsonb_typeof(instantanea) = 'object'::text))`.
- `ck_version_modelo_uml_04` (CHECK): `CHECK ((hash_instantanea_sha256 ~ '^[0-9a-f]{64}$'::text))`.
- `ck_version_modelo_uml_05` (CHECK): `CHECK (((estado_validacion)::text = ANY (ARRAY[('SIN_VALIDAR'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text])))`.
- `fk_version_modelo_uml_01` (FK): `FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE`.
- `fk_version_modelo_uml_02` (FK): `FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL`.
- `fk_version_modelo_uml_03` (FK): `FOREIGN KEY (id_version_padre, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED`.
- `fk_version_modelo_uml_04` (FK): `FOREIGN KEY (id_version_restaurada, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED`.
- `pk_version_modelo_uml_01` (PK): `PRIMARY KEY (id)`.
- `uq_version_modelo_uml_01` (UQ): `UNIQUE (id_modelo, numero_version)`.
- `uq_version_modelo_uml_02` (UQ): `UNIQUE (id_modelo, revision)`.
- `uq_version_modelo_uml_03` (UQ): `UNIQUE (id, id_modelo)`.

Índices:

- `pk_version_modelo_uml_01`: `CREATE UNIQUE INDEX pk_version_modelo_uml_01 ON uml_colaborativo.version_modelo_uml USING btree (id)`.
- `uq_version_modelo_uml_01`: `CREATE UNIQUE INDEX uq_version_modelo_uml_01 ON uml_colaborativo.version_modelo_uml USING btree (id_modelo, numero_version)`.
- `uq_version_modelo_uml_02`: `CREATE UNIQUE INDEX uq_version_modelo_uml_02 ON uml_colaborativo.version_modelo_uml USING btree (id_modelo, revision)`.
- `uq_version_modelo_uml_03`: `CREATE UNIQUE INDEX uq_version_modelo_uml_03 ON uml_colaborativo.version_modelo_uml USING btree (id, id_modelo)`.
