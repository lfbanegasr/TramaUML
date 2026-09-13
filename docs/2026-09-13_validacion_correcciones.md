# Validación de las correcciones PostgreSQL

Fecha: 2026-09-13. Resultados ejecutados, no estimaciones. PostgreSQL 17.10, imagen local `postgres:17-alpine`.

## Instalación real

Se aplicó `database/2026-09-13_18_correcciones_desarrollo.sql` mediante el despliegue 20, después de respaldar y restaurar en una copia temporal aislada. El contenedor original `uml-collab-postgresql-1` continúa activo y saludable, con su volumen y contraseñas existentes. No se dejaron registros ficticios en esa base.

Todas las cantidades exactas de filas de las 48 tablas coincidieron antes/después. Catálogos: 4 roles, 27 permisos, 69 enlaces rol-permiso y 9 reglas (109 registros). Usuarios, proyectos, modelos y versiones: cero antes/después. Las pruebas de datos se ejecutaron en copias aisladas, no sobre la instalación real.

| Metadato real posterior | Cantidad |
|---|---|
| Tablas de dominio | 48 |
| Columnas | 507 |
| FK | 119 |
| PK / UQ / CHECK | 48 / 29 / 142 |
| Vistas | 7 |
| Índices incluidos PK/UQ | 127 |
| Funciones | 37 |
| Triggers personalizados | 88 |
| Funciones trigger sin disparadores | 0 |

Permisos confirmados: `uml_app` puede actualizar nombres/metadatos permitidos, pero no `revision_actual` ni `id_version_actual`, no inserta versiones arbitrarias y no ejecuta `reservar_revision_modelo`. Usa las funciones autorizadas de inicio/guardado. Ambas funciones privilegiadas y sus auxiliares tienen esquemas explícitos con `pg_temp` al final.

## Pruebas ejecutadas

| Prueba | Resultado |
|---|---|
| Respaldo custom restaurado en otra base | Correcto; mismos conteos |
| Migración sobre copia de la base existente | Correcta |
| Reejecución de migración 18 | Correcta; idempotente |
| Integridad inicial adaptada de suite 15 | Correcta |
| Suite de regresión 19 | 51 / 51 |
| Transacción completa de cuenta runtime incluida en 19 | Correcta |
| Suite de correcciones 21 | 50 / 50 |
| Instalación nueva vacía con instalador del 13 de septiembre | Correcta; ambas suites aprobadas |
| Inicialización automática con los mismos montajes que Docker vigente | Correcta; cuenta runtime habilitada con clave ficticia, 51/51 y 50/50 |
| Configuración Docker nueva `config --no-interpolate --quiet` | Correcta, sin iniciar/recrear servicios |
| Migración real atómica | Correcta; conteos conservados |
| Metadatos/permisos/salud real después | Correctos |

Las 50 pruebas nuevas cubren IDs con máximos NULL/múltiples, validación terminal/errores/advertencias/fechas, propietario bloqueado/inactivo sin pérdida de titularidad y sin permisos efectivos, transferencia obligatoria antes de retirar propietario, ámbitos compatibles/incompatibles, DTO opcional, configuración congelada con estado modificable, borrado lógico y padres activos, snapshots/posiciones/historial, reserva/contexto/actor/guardado transaccional, escritura directa/pósterior rechazadas, versiones fabricadas, conflictos, nombre de modelo versionado, proyectos archivados y sombreado con tablas temporales.

Las suites utilizan datos ficticios y ROLLBACK. Los estados `VALIDO`, archivos y claves de prueba son fixtures; no representan compilación, IA o almacenamiento de archivos reales.

## Confirmaciones reales entre conexiones

El script `database/2026-09-13_22_prueba_concurrencia.ps1` solo admite contenedores propios etiquetados `codex.prueba`, sin red/puertos y con almacenamiento temporal `tmpfs`. Todos sus fixtures se descartaron al retirar esas instancias.

1. Veinte procesos intentaron editar la revisión cero del mismo modelo mediante `SET LOCAL ROLE uml_app`, inicio, creación de clase, snapshot y COMMIT. Una conexión confirmó; diecinueve recibieron `40001`. El resultado final fue exactamente una revisión, una clase y una versión. Tiempo del lote: **5588 ms**.
2. Cien cambios completos se repartieron entre veinte modelos, en cinco lotes de veinte conexiones simultáneas. Confirmaron los cien: veinte modelos con revisión cinco, cien clases, cien versiones y cien registros `APLICADO`. Tiempo agregado de los lotes: **17421 ms**.

Los tiempos incluyen el arranque de procesos Windows/Docker/psql y son una prueba local pequeña de integridad/concurrencia SQL. NO son latencia de un endpoint, SLA, comparación antes/después, carga de cien conexiones simultáneas ni certificación de cien ingenieros. Los modelos de esta prueba tienen cinco clases; no se probó aquí el costo de snapshots de modelos enormes. No inferir capacidad productiva a partir de estos números.

Los logs regenerables están en `.qa/2026-09-13_concurrencia_c5e41dcdb3924bc0902d32bdb4e6438b`, excluidos de Git. Respaldo del refuerzo final: `database/.privado/2026-09-13_011835_antes_correcciones.dump`. Respaldos privados anteriores: `database/.privado/2026-09-13_010838_antes_correcciones.dump` y `database/.privado/2026-09-13_011125_antes_correcciones.dump`; se conservan además los antecedentes existentes. Los dumps no incluyen contraseñas de roles globales ni los binarios del almacenamiento de objetos.

## Qué queda fuera de esta evidencia

No existe un backend Spring/React/Flutter implementado en esta carpeta. No se verificaron endpoints/JWT, comunicación WebSocket, restauración por API, interpretación de voz/fotos, XMI real en EA, compilación del código generado, IA local móvil, operación móvil offline, pruebas de seguridad productiva, recuperación de todo el sistema ni rendimiento de modelos grandes.

La persistencia corregida soporta el desarrollo y tiene pruebas reproducibles; no se certifica como sistema completo ni completamente optimizado. Conservar la [guía del contrato de integración](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/docs/2026-09-13_guia_persistencia_corregida.md>) como referencia para el siguiente incremento.

## Repetición segura

En PowerShell, desde Implementacion:

```powershell
.\database\2026-09-13_20_aplicar_correcciones.ps1
```

Esto respalda, restaura y prueba una copia sin modificar el esquema original. `-Aplicar` repite la validación y aplica la migración sobre el contenedor/base identificados, únicamente cuando las pruebas pasan. La aplicación real del 13 de septiembre ya se hizo: no es necesario repetirla para empezar a desarrollar.

Para una instalación NUEVA VACÍA usar `database/2026-09-13_instalar_espanol.psql`; nunca instalar la línea base encima de tablas existentes ni borrar el volumen para corregir el sistema. Las copias temporales retiradas pueden regenerarse mediante los scripts; no se eliminaron datos/volúmenes originales.

La prueba `database/2026-09-13_23_prueba_instalacion_nueva.ps1` reproduce además la inicialización automática en una instancia propia sin red ni puertos, con `tmpfs`, montajes SQL de solo lectura y clave ficticia. Solo retira el contenedor que creó y cuya etiqueta verifica.
