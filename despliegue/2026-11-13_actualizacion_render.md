# Actualización de generación y chat en Render

Servicio identificado: https://trama-uml.onrender.com/. El historial público de despliegues de GitHub identifica la rama `render-deploy` del repositorio `lfbanegasr/TramaUML`.

## Cambios incluidos

- Generación sin validación UML obligatoria y configuraciones guardadas independientes con el mismo nombre.
- Conservación de la aprobación de un ZIP durante nuevas comprobaciones, con verificación de su contenido por SHA-256.
- Generar directamente o después de guardar, reutilizando ajustes sin cambios. Preparación automática de clases como entidades, sin editar el diagrama; identificadores técnicos sin colisiones.
- Chat con entrada compacta, errores agrupados y vista ampliada; conservación del borrador y de la posición de lectura.
- Ajustes de importación y de manejo de fallos de IA de texto e imagen.

## Base de datos existente

El código admite la base existente sin exigir una migración para guardar repetidamente y generar. Si existe la restricción antigua de nombre, conserva las configuraciones con identificadores internos distintos y muestra el nombre elegido por el usuario. Si el esquema exige validación UML previa, la ejecuta automáticamente sobre la versión elegida, sin simular su aprobación y sin cambiar el diagrama.

Para eliminar esa exigencia del esquema y conservar descargas aprobadas durante otra validación pendiente o fallida, ejecutar **únicamente** `core/docs/migraciones/2026-11-13_12_generacion_independiente.sql` con la cuenta propietaria. Se puede pegar su contenido completo en el editor SQL de Neon o ejecutarlo con psql.

La migración usa una transacción, conserva configuraciones, versiones, archivos y validaciones, y admite repetirse. El rol de la aplicación `uml_app` tiene permisos limitados y no debe convertirse en administrador para ejecutar esta actualización.

El instalador `2026-11-11_instalar_neon.sql` es para bases nuevas y no se utiliza para actualizar una base existente.

## Despliegue y comprobación

El Blueprint declara despliegue por commit. Después del push, comprobar en Render que el servicio sigue la rama `render-deploy`, que construye la nueva revisión con `despliegue/2026-11-11_render.Dockerfile` y que termina correctamente.

Comprobar `/salud`, recargar la web y verificar generación sin validar UML, descarga de un ZIP aprobado, guardados repetidos y ampliación del chat. El push por sí solo no ejecuta la migración en Neon.

## Verificación local previa

59 pruebas de backend con el esquema actualizado, 22 con el esquema anterior y 10 recorridos de navegador aprobados. La prueba del esquema anterior omite explícitamente la migración 12 y comprueba guardar con el mismo nombre, generar, regenerar, compilar y descargar. Web de producción y JAR construidos. Antes del push se comparan las fuentes de ejecución y dependencias de la copia de despliegue con las fuentes probadas.

El despliegue real y la aplicación de SQL a la base real se verifican por separado; esta nota no afirma que esas operaciones ya hayan terminado.
