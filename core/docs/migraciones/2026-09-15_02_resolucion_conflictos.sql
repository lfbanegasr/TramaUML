-- Incremento 05: trazabilidad e idempotencia de la decisión humana sobre conflictos.
-- La tabla ya conserva ambos valores y los cambios causales. Estas columnas enlazan
-- la solicitud REST y, cuando existe, el cambio confirmado por la resolución.
-- Ejecutar como propietario del esquema. No contiene datos ni modifica revisiones.
BEGIN;

ALTER TABLE uml_colaborativo.conflicto_edicion
    ADD COLUMN IF NOT EXISTS id_solicitud_resolucion uuid,
    ADD COLUMN IF NOT EXISTS id_cambio_resolucion uuid;

ALTER TABLE uml_colaborativo.conflicto_edicion
    DROP CONSTRAINT IF EXISTS ck_conflicto_edicion_04,
    DROP CONSTRAINT IF EXISTS ck_conflicto_edicion_05,
    DROP CONSTRAINT IF EXISTS ck_conflicto_edicion_06;

ALTER TABLE uml_colaborativo.conflicto_edicion
    ADD CONSTRAINT ck_conflicto_edicion_04 CHECK (
        (estado = 'ABIERTO' AND resuelto_en IS NULL)
        OR (estado IN ('RESUELTO', 'DESCARTADO') AND resuelto_en IS NOT NULL)
    ),
    ADD CONSTRAINT ck_conflicto_edicion_05 CHECK (
        id_solicitud_resolucion IS NULL
        OR (estado IN ('RESUELTO', 'DESCARTADO') AND resuelto_por IS NOT NULL)
    ),
    ADD CONSTRAINT ck_conflicto_edicion_06 CHECK (
        id_cambio_resolucion IS NULL
        OR (estado = 'RESUELTO' AND tipo_resolucion IN ('APLICAR_ENTRANTE', 'FUSIONAR'))
    );

DO $migracion$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_conflicto_edicion_07'
          AND conrelid = 'uml_colaborativo.conflicto_edicion'::regclass
    ) THEN
        ALTER TABLE uml_colaborativo.conflicto_edicion
            ADD CONSTRAINT fk_conflicto_edicion_07
            FOREIGN KEY (id_cambio_resolucion, id_modelo)
            REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo);
    END IF;
END
$migracion$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_conflicto_edicion_01
    ON uml_colaborativo.conflicto_edicion(id_solicitud_resolucion)
    WHERE id_solicitud_resolucion IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_conflicto_edicion_02
    ON uml_colaborativo.conflicto_edicion(id_cambio_entrante)
    WHERE id_cambio_entrante IS NOT NULL AND estado = 'ABIERTO';

COMMIT;
