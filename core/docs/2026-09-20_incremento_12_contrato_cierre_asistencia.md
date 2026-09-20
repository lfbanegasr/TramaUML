# Contrato técnico — Incremento 12: cierre funcional de asistencia inteligente

Fecha: 2026-09-20

Alcance: **CU21 a CU25 del catálogo (fichas detalladas CU22 a CU26)**. Se conserva la diferencia de numeración identificada en la documentación fuente.

## Principio de seguridad

La asistencia inteligente solo produce una `propuesta_inteligente`. Ningún motor local o remoto escribe elementos UML. La aplicación continúa pasando por `EdicionTransaccional`, crea una sola revisión y una sola versión, y publica el cambio por WebSocket después del commit.

Las pruebas persistentes se ejecutan exclusivamente sobre bases ficticias del clúster `127.0.0.1:55433`. El puerto personal `55432` está prohibido por los arneses.

## Texto y contexto del modelo

El motor híbrido consulta una instantánea de clases, atributos, operaciones y relaciones activas. El respaldo local determinista reconoce:

- creación de clases con atributos y relaciones;
- renombrado y eliminación de clases;
- creación, corrección y eliminación de atributos;
- creación y eliminación de operaciones sin parámetros;
- creación y eliminación de relaciones entre clases existentes;
- cardinalidades `n a m`, `n a *` y expresiones equivalentes con “muchos”.

Cada operación conserva UUID preasignado, número de secuencia, `valor_actual`, `valor_propuesto`, confianza, estado, mensajes y dependencias. Una referencia inexistente o un nombre duplicado produce una operación `INVALIDO` no seleccionada, en vez de modificar el modelo o inventar una referencia.

La gramática local es deliberadamente acotada. Un proveedor de texto configurado puede normalizar una instrucción libre hacia ella; una salida remota inutilizable vuelve al motor local.

## Revisión editable y revalidación

Se añade el endpoint:

`PUT /api/modelos/{modelo}/ia/propuestas/{propuesta}`

El cuerpo contiene la revisión optimista de la propuesta y exactamente todas sus operaciones:

```json
{
  "revision": "2026-09-20T12:00:00Z",
  "operaciones": [
    {
      "id": "uuid-operacion",
      "seleccionada": true,
      "valorPropuesto": { "nombre": "Cliente" }
    }
  ]
}
```

El servidor bloquea la propuesta, comprueba que siga `LISTO`, que la revisión base coincida con el modelo y que el actor posea `APLICAR_PROPUESTA_IA`. Después valida forma, campos permitidos, tipos, multiplicidades, UUID, extremos de relaciones, parámetros y dependencias. Devuelve la propuesta completa con una nueva `revisionEdicion` y `puedeAplicar`.

Una actualización concurrente devuelve conflicto. Una operación seleccionada inválida o una dependencia desmarcada bloquea la aplicación. `EdicionTransaccional` vuelve a ejecutar las reglas definitivas al aplicar, por lo que un defecto de prevalidación no puede producir una escritura parcial.

El diálogo muestra el valor actual, el propuesto, errores, confianza y dependencias. Permite seleccionar, editar JSON, guardar y revalidar. Mientras existan cambios locales sin revalidar, **Aplicar propuesta** permanece deshabilitado.

## Voz

- Web Speech conserva la transcripción editable antes de generar la propuesta.
- MediaRecorder muestra tiempo, se detiene a los 120 segundos y ofrece reproducción local antes del envío.
- El backend limita tamaño, MIME, firma WebM/WAV/OGG y duración declarada entre 1 y 120 segundos.
- El audio original se almacena como `AUDIO_VOZ`.
- La transcripción manual tiene precedencia. Si no existe, se usa el servicio activo `VOZ_A_TEXTO` resuelto por el Incremento 11. Si ningún proveedor responde, la solicitud queda `FALLIDO` con causa trazable; nunca se inventa texto.

## Imagen

- El navegador valida formato y tamaño y muestra vista previa.
- El backend valida MIME, firma decodificable, dimensiones y límite antes de almacenar como `IMAGEN_DIAGRAMA`.
- Un texto detectado y corregido por el usuario puede acompañar la imagen.
- Sin texto se utiliza el servicio activo `IA_IMAGEN`, compatible con los adaptadores multimodales configurados. La ausencia o fallo del proveedor queda explícito.
- Las clases detectadas reciben coordenadas deterministas sin superponer su posición inicial y todas las operaciones pasan por el mismo diálogo editable.

## Permisos y persistencia

No se modifica el esquema base. Se reutilizan `solicitud_inteligente`, `propuesta_inteligente`, `operacion_propuesta`, `dependencia_operacion_propuesta` y `archivo_almacenado` mediante JDBC puro.

La migración idempotente `2026-09-20_07_revision_asistencia_lectura.sql` asigna `REVISAR_PROPUESTA_IA` a `LECTOR_PROYECTO` —y al alias histórico `LECTOR` si existe—. Un lector puede abrir la propuesta en modo de consulta, pero el frontend no le ofrece corrección, aplicación ni rechazo y el backend vuelve a comprobar el permiso material.

## Compatibilidad

Los endpoints previos de texto, voz, imagen, consulta, aplicación y rechazo se conservan. `duracionSegundos` es opcional para clientes anteriores. Las nuevas respuestas agregan `revisionEdicion` y `puedeAplicar` sin cambiar los campos existentes.
