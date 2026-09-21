# Seguimiento del Incremento 14 — Configuración avanzada de generación

Fecha de cierre técnico: 2026-09-21
Rama: `codex/incremento-14-generacion-avanzada`
Versión: backend `0.14.0-SNAPSHOT`, frontend `0.14.0`

## Resultado

La configuración de generación dejó de ser un formulario global ligado al modelo vivo. Ahora se guarda sobre una versión UML congelada y permite elegir clases, operaciones, capas, exposición REST, mapeos, dependencias y estrategia de migración. El servidor devuelve diagnósticos antes de generar y no suplanta la validación semántica.

## Backend

- DTOs inmutables para versiones, clases, operaciones, mapeos, dependencias, mensajes y resultado.
- Lectura histórica desde `version_modelo_uml.instantanea`.
- Persistencia JDBC en las cinco estructuras de configuración existentes.
- Configuraciones nuevas e inmutabilidad de las ya utilizadas.
- Verificación de pertenencia por modelo para configuración, versión y descarga.
- Generación por `idConfiguracion`, con bloqueo de versiones sin validación aprobada.
- Motor configurable por capas, Java/Spring Boot, Maven, mapeos y Flyway/Liquibase/SQL.
- Servicios generados con firmas de operaciones UML seleccionadas.
- Migración `2026-09-21_08_generacion_backend_avanzada.sql`, que conserva las invariantes duras sin convertir capas opcionales en obligatorias.

## Frontend

- Selector de versión vigente o histórica.
- Formulario Maven/Java/Spring/migración.
- Selección de capas, clases y operaciones.
- Edición de nombres de entidad/tabla y mapeos UML/Java/PostgreSQL.
- Inventario visible de dependencias obligatorias.
- Mensajes del servidor y bloqueo explícito cuando la versión aún no fue validada.
- Guardado independiente y generación/descarga con la configuración exacta.

## Pruebas incorporadas

- Motor básico y avanzado: capas opcionales, dependencias, mapeos, tabla personalizada, Flyway y operaciones UML.
- Integración: configuración persistida, advertencia previa a validación, bloqueo de generación, validación real, generación y descarga.
- Integración avanzada: selección de clases y capas, mapeo decimal y tabla personalizada, dependencia Maven opcional, reproducción de una versión histórica después de editar el modelo, aislamiento de configuración y artefacto por modelo, y protección CSRF.
- Autorización con roles reales y aislamiento del artefacto por modelo.
- Recorrido Playwright actualizado para crear un identificador, validar el modelo y usar la pantalla avanzada.

Verificaciones del núcleo realizadas el 2026-09-21: formato Google Java Format AOSP correcto; suite aislada en `55433` con **119 pruebas aprobadas, cero fallas**; paquete `0.14.0-SNAPSHOT` construido. En frontend, `npm run format:check` y `npm run build` aprobaron (el build informa una advertencia de tamaño del bundle, no un error).
El recorrido completo de navegador aprobó **24 de 24 pruebas** sobre otra base aislada en `55433`, incluida la configuración avanzada y la importación XMI. El primer intento dentro del entorno restringido agotó el tiempo al abrir páginas nuevas; la repetición completa con ejecución permitida finalizó sin fallas.

Todas las pruebas con persistencia se ejecutan únicamente en bases nuevas del clúster aislado `127.0.0.1:55433`. El PostgreSQL Docker de `55432` no forma parte del proceso automatizado.

## Límite deliberado

Este incremento no afirma que el ZIP compile. La compilación en un entorno aislado, el informe persistente de validación, la comparación contra el modelo y la descarga condicionada se implementarán en el **Incremento 15: validación y descarga controlada del backend**.
