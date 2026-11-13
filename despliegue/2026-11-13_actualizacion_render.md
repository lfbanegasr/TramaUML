# Actualización de generación y chat en Render

Servicio identificado: https://trama-uml.onrender.com/. El historial público de despliegues de GitHub identifica la rama `render-deploy` del repositorio `lfbanegasr/TramaUML`.

## Cambios incluidos

- Generación sin validación UML obligatoria y configuraciones guardadas independientes con el mismo nombre.
- Conservación de la aprobación de un ZIP durante nuevas comprobaciones, con verificación de su contenido por SHA-256.
- Preparación de clases como entidades desde el diálogo, sin editar el diagrama; identificadores técnicos sin colisiones.
- Chat con entrada compacta, errores agrupados y vista ampliada; conservación del borrador y de la posición de lectura.
- Ajustes de importación y de manejo de fallos de IA de texto e imagen.

## Base de datos existente

Es necesario ejecutar **únicamente** `core/docs/migraciones/2026-11-13_12_generacion_independiente.sql` en la base Neon que utiliza este servicio, con la cuenta propietaria del esquema. Se puede pegar su contenido completo en el editor SQL de Neon o ejecutarlo con psql.

La migración usa una transacción, conserva configuraciones, versiones, archivos y validaciones, y admite repetirse. El rol de la aplicación `uml_app` tiene permisos limitados y no debe convertirse en administrador para ejecutar esta actualización.

El instalador `2026-11-11_instalar_neon.sql` es para bases nuevas y no se utiliza para actualizar una base existente.

## Despliegue y comprobación

El Blueprint declara despliegue por commit. Después del push, comprobar en Render que el servicio sigue la rama `render-deploy`, que construye la nueva revisión con `despliegue/2026-11-11_render.Dockerfile` y que termina correctamente.

Comprobar `/salud`, recargar la web y verificar generación sin validar UML, descarga de un ZIP aprobado, guardados repetidos y ampliación del chat. El push por sí solo no ejecuta la migración en Neon.

## Verificación local previa

58 pruebas de backend y nueve recorridos de navegador aprobados. Web de producción y JAR construidos. La migración se ejecutó y se repitió en PostgreSQL ficticio, comprobando que conserva las cantidades de configuraciones, versiones, archivos y validaciones. Antes del push se compararon todas las fuentes de ejecución y dependencias de la copia de despliegue con las fuentes probadas.

El despliegue real y la aplicación de SQL a la base real se verifican por separado; esta nota no afirma que esas operaciones ya hayan terminado.
