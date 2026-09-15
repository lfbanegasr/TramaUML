# Incremento 04 — sesión colaborativa y presencia

Fecha: 2026-09-14. Backend de esta plataforma: 0.4.0-SNAPSHOT; frontend: 0.4.0. Se continúa desde 0.3 sin migrar JDBC, sesiones opacas ni el contrato transaccional UML.

## Fuentes y alcance

Fuentes: [seguimiento 03](2026-09-14_seguimiento_incremento_03.md), [contratos 03](2026-09-14_incremento_03_contratos.md), [convenciones](2026-09-14_convenciones_desarrollo.md), [guía de persistencia](../../docs/2026-09-13_guia_persistencia_corregida.md), [diccionario vigente](../../docs/2026-09-13_diccionario_base_datos_vigente.md) y `core/docs/fuentes/2026-09-13_lectura_Documentacion.txt`, derivado de `../Documentacion/Documentacion.docx`.

Se revisaron los flujos titulados **Abrir sesión de modelado** y **Editar colaborativamente**. Existe una contradicción de numeración dentro de la documentación: el catálogo resumido los denomina CU09 y CU16; las especificaciones detalladas los denominan CU10 y CU17 porque incluyen antes la asignación de roles y la organización visual. Este incremento identifica ambos números y usa los títulos para evitar atribuir una regla al caso equivocado. Los originales se conservan sin cambios.

El alcance comprende apertura y salida de una sala del modelo, presencia, foco, autorización renovada, difusión de versiones confirmadas y recuperación de conexión. La comparación y resolución persistente de conflictos corresponde al caso detallado CU18 y queda para el siguiente incremento junto con historial/restauración. No se incluyen IA, XMI, generación ni Flutter; el requisito histórico Flutter offline permanece aplazado por indicación del usuario.

## Decisiones explícitas

- Un modelo tiene como máximo una sala `ACTIVO`. Su apertura bloquea la fila del modelo y reutiliza la sala vigente. Esta interpretación sigue la frase “sesión colaborativa activa” y la clave participante/sesión existente; la documentación no establece múltiples salas paralelas.
- Abrir por REST incorpora al usuario como `AUSENTE` y mantiene la sala en `SOLO_LECTURA`. Solo el upgrade WebSocket autenticado cambia su presencia a `EN_LINEA` y habilita edición. Si el transporte falla, el modelo puede consultarse, se informa el modo limitado y se bloquean nuevas escrituras simultáneas.
- Una desconexión inesperada deja al participante `AUSENTE`, limpia su foco y permite cinco reintentos con espera exponencial, hasta diez segundos. No se inventa una caducidad de sesiones. Una salida explícita marca `DESCONECTADO`; la sala se cierra cuando no quedan participantes.
- Varias pestañas del mismo usuario se cuentan en memoria. Cerrar una no lo ausenta mientras conserve otra conexión en la misma sala.
- El foco admite clases, relaciones, paquetes, atributos y operaciones existentes en el mismo modelo. La vista actual emite foco para clases, relaciones y paquetes, que son seleccionables en el lienzo/explorador.
- El archivo de proyecto continúa bloqueado mientras exista una sesión editable activa. El flujo detallado de colaboración también contempla que un archivo ocurra durante la sesión; ambos comportamientos no pueden suceder a la vez. Se conserva la decisión segura del incremento 03 y queda pendiente confirmar con el docente si debe existir un archivo forzado que degrade y notifique.
- El registro de conexiones y la difusión son locales a una instancia de Spring Boot. PostgreSQL conserva sala, participantes y actividad, pero un despliegue horizontal necesitará un broker o publicación compartida. No se presenta esta versión como escalable entre nodos.

## Contrato HTTP y WebSocket

Las rutas REST requieren la cookie de sesión opaca. Las escrituras REST requieren CSRF. El actor siempre procede de la sesión y el servidor consulta otra vez cuenta, membresía, estado de proyecto y permisos. Un modelo no autorizado se oculta con 404.

| Método y ruta | Contrato |
|---|---|
| POST `/api/modelos/{modelo}/sesiones` | Cuerpo `idSolicitudCliente` UUID. Crea o reutiliza la sala activa e incorpora al actor con modo `EDITOR` o `LECTOR`. Responde 201 con sala y participantes. |
| GET `/api/modelos/{modelo}/sesiones/{sesion}` | Devuelve la sala únicamente a un participante todavía vigente. |
| DELETE `/api/modelos/{modelo}/sesiones/{sesion}` | Salida explícita, actividad y cierre de sala cuando sale el último participante. |
| WebSocket `/ws/modelos/{modelo}/sesiones/{sesion}` | Upgrade del mismo origen con la misma cookie HttpOnly; vuelve a autorizar y registra `EN_LINEA`. |

Mensajes aceptados del navegador:

- `{"tipo":"PING"}` renueva presencia y permisos.
- `{"tipo":"FOCO","tipoEntidad":"CLASE","idEntidad":"<uuid>"}` actualiza el elemento enfocado. Ambos campos nulos limpian el foco.

Mensajes emitidos por el servidor:

- `CONECTADO` confirma el canal.
- `PRESENCIA` contiene la sala y una lista con nombre, roles vigentes, modo, estado y foco. `modoPropio` se construye para cada destinatario; no se comparte el modo personal de otro usuario.
- `MODELO_CONFIRMADO` contiene modelo, UUID de cambio, actor, revisión y versión solo después del commit. El UUID permite que la pestaña emisora reconozca su confirmación sin confundirla con una edición remota.
- `ERROR` informa una operación de canal rechazada. Mensajes desconocidos, grandes o inválidos cierran el canal.

El canal no acepta mutaciones UML. Estas siguen en POST `/api/modelos/{modelo}/cambios`, con CSRF, revisión base e UUID idempotente. El cambio incorpora `idSesionEdicion`, que debe señalar una participación `EDITOR`, `EN_LINEA` y activa. `cambio_modelo.id_sesion_edicion` registra la sala real.

La cookie opaca cambió de `Path=/api` a `Path=/` para que autentique también el upgrade `/ws`. Conserva `HttpOnly`, `SameSite=Strict`, duración de dos horas y `Secure` configurable por entorno. El token no es accesible desde JavaScript.

## Persistencia y orden transaccional

No se añade una migración. Se reutilizan `sesion_edicion_modelo`, `participante_sesion_modelo`, `cambio_modelo` y `actividad_proyecto`; el esquema vigente permanece en 48 tablas, 507 columnas y 119 FK.

La apertura serializa sobre `modelo_uml FOR UPDATE`, crea o recupera la sala, incorpora al participante y registra `ABRIR_SESION` o `INCORPORARSE` en la misma transacción. La salida registra `SALIR_SESION` atómicamente. Un cambio de membresía actualiza el modo o desconecta al afectado dentro de su transacción; la notificación se refresca en una transacción nueva posterior al commit.

La edición UML conserva una transacción/conexión y el orden obligatorio:

`autorizar → reconocer reenvío → validar sesión editable en línea → iniciar_edicion_modelo → cambios JDBC → cambio_modelo vinculado a sesión → guardar_version_modelo → actividad → commit`.

La publicación `MODELO_CONFIRMADO` y los cambios de presencia se ejecutan con listeners `AFTER_COMMIT`. Una escritura revertida no se difunde. No se escriben punteros de revisión ni snapshots arbitrarios y continúa el tratamiento existente de SQLSTATE 40001.

React conserva el formulario y ambos UUID ante respuestas inciertas. Si llega una revisión remota con un formulario o envío pendiente, marca el conflicto, conserva el borrador y exige recargar/revisar. Sin borrador aplica la versión confirmada mediante una nueva lectura. Perder edición durante la sesión oculta las herramientas de escritura y muestra solo lectura.

## Ejecución personal

El JAR esperado es `core/backend/target/plataforma-0.4.0-SNAPSHOT.jar`. El administrador personal ya existe. Después de detener voluntariamente la terminal que ejecute una versión anterior, iniciar desde `Implementacion` con:

```powershell
./core/docs/2026-09-14_ejecutar_backend.ps1 -HttpLocal
```

No usar `-InicializarAdministrador`. El lanzador solicita de forma oculta la clave vigente de `uml_app` si `UML_DB_PASSWORD` no está disponible. React se inicia desde `core/frontend` con `npm.cmd run dev`. No se necesita aplicar SQL a la base personal para este incremento.
