# Seguimiento del incremento 08 — Interoperabilidad XMI y Generación de Backend

**Fecha:** 2026-09-18 · **Estado:** Implementado, verificado con compilación y pruebas unitarias, ejecutando suite de integración.  
**Versión:** backend 0.6.0-SNAPSHOT (compatible con scripts de ejecución existentes), frontend 0.6.0.

## Objetivos del incremento

Implementación de la **Etapa 1: Interoperabilidad y Generación** cubriendo:
- **CU28:** Exportación XMI 2.1 compatible con Enterprise Architect.
- **CU29:** Configuración de generación de backend Spring Boot.
- **CU30:** Generación completa de proyecto Spring Boot en 4 capas (Java 21 / Maven / PostgreSQL / DTOs / OpenAPI).
- **CU32:** Descarga del artefacto ZIP generado.

---

## Componentes implementados

### 1. Backend — Módulo XMI (`bo.edu.uml.xmi`)
- `XmiDto.java`: Records inmutables `SolicitudExportacion`, `ResultadoExportacion`, `DescargaInfo`.
- `XmiRepositorio.java`: Consultas JDBC a elementos del modelo (`clase_uml`, `atributo_uml`, `operacion_uml`, `parametro_uml`, `relacion_uml`, `paquete_uml`), inserción de binario y metadatos en `archivo_almacenado` con cálculo de SHA-256, y registro en `intercambio_xmi`.
- `GeneradorXmi21Service.java`: Generador de XML XMI 2.1 / UML 2.1 con namespaces OMG, mapeo a tipos primitivos UML (`Integer`, `String`, `Boolean`, etc.), cardinalidades (`lowerValue`/`upperValue`) y asociaciones con extremos navegables.
- `XmiServicio.java`: Servicio transaccional que valida permisos de proyecto (`EXPORTAR_XMI`), maneja proyectos activos y delega en el generador y repositorio.
- `XmiControlador.java`: Endpoints REST:
  - `POST /api/modelos/{modelo}/xmi/exportar`
  - `GET /api/modelos/{modelo}/xmi/{id}/descargar`

### 2. Backend — Módulo Generación (`bo.edu.uml.generacion`)
- `GeneracionDto.java`: Records `ConfiguracionGeneracion`, `ActualizarConfiguracion`, `ResultadoGeneracion`.
- `GeneracionRepositorio.java`: JDBC puro contra `configuracion_generacion_backend` (estados `VALIDO`/`DESACTUALIZADO`, herramienta `FLYWAY`), `generacion_backend` (estado `GENERADO`) y `archivo_almacenado` (categoría `BACKEND_GENERADO`).
- `MotorGeneracionSpringBoot.java`: Generador ZIP en memoria con `ZipOutputStream`:
  - `pom.xml` con Java 21, Spring Boot 3.5.16, Spring Data JPA, Web, PostgreSQL, Validation, SpringDoc OpenAPI UI.
  - `src/main/resources/application.properties` y `schema.sql`.
  - Capa Entidad JPA por clase persistente con `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, y `@ManyToOne`/`@OneToMany` para relaciones.
  - Capa Repositorio (`JpaRepository<T, Long>`).
  - Capa Controlador REST con CRUD completo (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`).
  - Capa DTOs (`record {Nombre}Dto`, `record {Nombre}Form`).
- `GeneracionServicio.java`: Servicio transaccional que autoriza `CONFIGURAR_BACKEND`, `GENERAR_BACKEND` y `DESCARGAR_ARTEFACTO`.
- `GeneracionControlador.java`: Endpoints REST:
  - `GET /api/modelos/{modelo}/generacion/configuracion`
  - `PUT /api/modelos/{modelo}/generacion/configuracion`
  - `POST /api/modelos/{modelo}/generacion/generar`
  - `GET /api/modelos/{modelo}/generacion/{id}/descargar`

### 3. Migración SQL
- `core/docs/migraciones/2026-09-18_04_xmi_generacion_permisos.sql`:
  - 5 permisos registrados: `EXPORTAR_XMI`, `IMPORTAR_XMI`, `CONFIGURAR_BACKEND`, `GENERAR_BACKEND`, `DESCARGAR_ARTEFACTO`.
  - Asignación idempotente (`ON CONFLICT DO NOTHING`) a roles `LIDER` y `COLABORADOR`.
  - Columna `contenido bytea` en `uml_colaborativo.archivo_almacenado`.

### 4. Frontend — React 19 / TypeScript
- `xmiApi.ts`: Endpoints tipados de exportación y descarga de archivo XMI.
- `generacionApi.ts`: Endpoints tipados de lectura/escritura de configuración, generación y descarga de ZIP.
- `ExportarXmiDialog.tsx`: Diálogo modal accesible (WAI-ARIA `dialog`, `aria-modal`) con ciclo `idle` -> `exportando` -> `descargando` -> `completado`.
- `GenerarBackendDialog.tsx`: Diálogo modal de configuración avanzada del proyecto Spring Boot y progreso de generación/descarga.
- `EditorPage.tsx`: Integración de botones "Exportar XMI" y "Generar Backend" en la barra de herramientas del editor.
- `styles.css`: Estilos visuales para los modales, formularios en grid, estados de carga y mensajes de éxito.

---

## Pruebas y verificación

| Tipo | Suite / Archivo | Estado | Resultado |
|---|---|---|---|
| Unitario Backend | `XmiTest.java` | Completado | 6/6 tests pasando |
| Unitario Backend | `GeneracionTest.java` | Completado | 8/8 tests pasando |
| Unitario Backend | `ComparadorVersionesTest.java` | Completado | 5/5 tests pasando |
| Unitario Backend | `ValidadorSnapshotTest.java` | Completado | 5/5 tests pasando |
| Unitario Backend | `MotorSemanticoTest.java` | Completado | 3/3 tests pasando |
| Integración Backend | `NucleoIntegracionTest.java` | Ampliado | Tests de XMI, Generación y Seguridad añadidos |
| Frontend Typecheck | `tsc -b` | Completado | 0 errores TypeScript |
| Frontend Build | `vite build` | Completado | Build exitoso en 2.96s |
| Frontend Estilos | `prettier --check` | Completado | 100% formateado conforme |
