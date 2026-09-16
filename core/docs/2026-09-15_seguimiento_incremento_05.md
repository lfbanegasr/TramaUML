# Seguimiento del incremento 05

Fecha: 2026-09-15. Estado: incremento verificado en bases aisladas; migración y despliegue personal pendientes. Versión: backend 0.5.0-SNAPSHOT y frontend 0.5.0.

## Resultado implementado

- Las escrituras basadas en una revisión obsoleta ya no pierden la propuesta: después del rollback original se vuelve a autorizar y se registra un `cambio_modelo` en conflicto y un `conflicto_edicion` abierto.
- Comparación por elemento y propiedad con versión base, estado confirmado actual, propuesta, autores, fechas y revisiones. Valores opcionales ausentes y `null` se normalizan para no mostrar diferencias falsas.
- Resoluciones humanas para conservar, aplicar, fusionar propiedades disjuntas o descartar. La fusión ambigua se rechaza; no se incorporó una fusión automática genérica.
- Revisión, permisos, estado del proyecto/modelo y sesión colaborativa se comprueban otra vez bajo bloqueo antes de decidir. Una revisión nueva obliga a actualizar la comparación.
- Aplicar o fusionar usa el protocolo transaccional completo, valida que no aparezcan errores UML nuevos y enlaza la resolución con su cambio/versión. Un fallo revierte UML, revisión, versión, conflicto, actividad y eventos.
- Un elemento eliminado puede reactivarse explícitamente si sus dependencias permanecen activas. No se restauran en cascada elementos no incluidos en la propuesta.
- REST con CSRF e idempotencia por UUID para consulta, resolución y descarte. Un lector autorizado consulta diferencias sin recibir acciones de resolución.
- Actividad `RESOLVER_CONFLICTO`/`DESCARTAR_CONFLICTO` y avisos WebSocket `CONFLICTO_ACTUALIZADO`; toda difusión ocurre `AFTER_COMMIT` y vuelve a autorizar al destinatario.
- React incorpora contador, panel comparativo, propiedades resaltadas, aviso de elemento eliminado, modo de lectura, confirmación y conservación de la decisión local durante recarga o desconexión.
- Migración incremental reejecutable sobre `conflicto_edicion`, sin tablas nuevas. En el esquema 0.5 validado hay 48 tablas, 509 columnas y 120 FK.

Los contratos están en [2026-09-15_incremento_05_contratos.md](2026-09-15_incremento_05_contratos.md). El análisis para el siguiente incremento está en [2026-09-15_base_tecnica_historial_restauracion.md](2026-09-15_base_tecnica_historial_restauracion.md).

## Cobertura por caso de uso

- **CU18 — Gestionar conflictos de edición: completo para los elementos y operaciones del editor vigente.** Cubre detección, conservación de propuestas, comparación, consulta sin resolución, cuatro decisiones, confirmación, consistencia UML, carreras, permisos, proyecto/sesión, desconexión, elemento eliminado, idempotencia, rollback, sincronización y actividad.
- **CU19 — Consultar historial del modelo: no implementado.** Se analizó el formato de snapshot y se propusieron contratos, filtros y comparación.
- **CU20 — Restaurar versión del modelo: no implementado.** Se documentaron validación, orden de reconstrucción y protocolo transaccional; no se insertaron snapshots ni se movieron revisiones.

La fuente conserva una contradicción: el catálogo llama CU17/CU18/CU19 a conflictos/historial/restauración y las fichas detalladas los llaman CU18/CU19/CU20. Este seguimiento usa número y nombre de las fichas detalladas por instrucción del usuario, sin afirmar que el docente resolvió la contradicción.

## Persistencia y decisiones

La migración [2026-09-15_02_resolucion_conflictos.sql](migraciones/2026-09-15_02_resolucion_conflictos.sql) añade `id_solicitud_resolucion`, `id_cambio_resolucion`, una FK compuesta, CHECK e índices únicos parciales. Se probó al instalarla y al reaplicarla sobre una base ficticia con datos/resoluciones existentes. No se ejecutó en la base personal 55432.

Cerrar el panel conserva el conflicto pendiente; “Descartar” es una decisión explícita y trazable. Conservar el confirmado o descartar no crea una versión artificial. Aplicar/fusionar crea una versión solo cuando existe una mutación efectiva. El cambio entrante termina `RECHAZADO` porque la versión vigente, incluso cuando acepta sus valores, se materializa mediante un nuevo cambio de resolución vinculado.

La validación semántica bloquea errores nuevos respecto del estado previo. Esto permite corregir modelos que ya contienen hallazgos sin convertir CU18 en una restauración o saneamiento global. La serialización sigue basada en revisión global del modelo; dos cambios independientes pueden llegar a revisión humana aunque sean fusionables, una elección segura hasta medir y definir granularidad más fina.

## Pruebas y evidencia ejecutadas en este incremento

- PostgreSQL 18 nativo, base nueva `uml_collab_test_960a6925d5e6` en 127.0.0.1:55433: **46 pruebas de backend aprobadas**, 0 fallos, errores u omitidas. Fueron 43 de integración y tres del motor semántico; Maven tomó 1 min 6 s.
- PostgreSQL 17.11 portátil, base nueva `uml_collab_test_3838af64d32c` en 127.0.0.1:55435: las mismas **46 pruebas aprobadas**, 0 fallos, errores u omitidas; Maven tomó 56,959 s.
- Playwright/Chrome contra PostgreSQL 18, base nueva `uml_collab_test_89508a2eddae`: **15 recorridos aprobados** en 1,5 min. El nuevo recorrido usa dos contextos de navegador, produce un conflicto real, comprueba ambos valores y aplica la propuesta mediante la interfaz.
- Compilación de producción React/TypeScript aprobada: 199 módulos transformados y paquete Vite generado.
- Empaquetado Spring Boot aprobado: `core/backend/target/plataforma-0.5.0-SNAPSHOT.jar`.
- La migración se reaplicó correctamente sobre `uml_collab_test_15a417a12a22`, que ya contenía datos de la suite, sin duplicar columnas, restricciones o índices.
- Formato Java, TypeScript, CSS y documentos nuevos aprobado; sintaxis de los tres scripts PowerShell modificados y `git diff --check` aprobados.

Las pruebas nuevas cubren dos usuarios sobre la misma propiedad, propiedades compatibles, conservar, aplicar, fusión válida/inválida, elemento eliminado, lector sin resolución, pérdida de permiso, revisión nueva, desconexión/recuperación, UUID repetido, rollback completo y ausencia de notificación antes del commit. Las suites completas protegen la regresión de acceso, administración, proyectos, modelado, validación, colaboración y actividad.

## Límites y pendientes visibles

- Aplicar la migración 05 y reiniciar el JAR 0.5 en la instalación personal requiere una decisión explícita y credenciales del propietario del esquema. El administrador personal ya existente no se reinicializa.
- WebSocket sigue siendo local a una instancia; un despliegue horizontal necesita transporte compartido.
- CU19/CU20, reorganización visual, IA, XMI y generación permanecen fuera de este incremento.
- Auditoría global consultable, notificaciones, expiración de credenciales y política definitiva de roles múltiples siguen pendientes; no se resolvieron con supuestos.
- Flutter permanece aplazado por indicación actual del usuario; se conserva el requisito histórico offline para una fase posterior.

## Despliegue personal pendiente

Antes de iniciar 0.5 se debe respaldar y aplicar la migración como propietario del esquema, con la aplicación detenida y entrada oculta de contraseña. No usar `uml_app` como propietario ni ejecutar el SQL mientras haya escrituras. Después se puede iniciar desde `Implementacion` con:

```powershell
./core/docs/2026-09-14_ejecutar_backend.ps1 -HttpLocal
```

No usar `-InicializarAdministrador`. El lanzador solicita de forma oculta la clave vigente de `uml_app`. El frontend se inicia desde `core/frontend` con `npm.cmd run dev`.

## Siguiente incremento recomendado

Implementar primero CU19: listado paginado, filtros, lectura segura del snapshot y comparación por UUID/propiedad. Con esa lectura validada, implementar CU20 como reconstrucción transaccional que crea una versión nueva, resuelve cambios pendientes de sesiones según una política confirmada y nunca modifica directamente los punteros de revisión.
