# Incremento 02 — administración, miembros, paquetes y validación
Fecha: 2026-09-14. Versión: 0.2.0-SNAPSHOT del backend de la plataforma.

## Fuentes y alcance
Se consultaron los flujos detallados CU03–05, CU08–09, CU14 y CU16 de ../Documentacion/Documentacion.docx (ruta relativa a Implementacion), junto con sus diagramas de secuencia y realización. Las imágenes revisadas son derivados de lectura; no se modificaron los Word originales.

Las fuentes de persistencia siguen siendo docs/2026-09-13_guia_persistencia_corregida.md y docs/2026-09-13_diccionario_base_datos_vigente.md. Los scripts de instalación antiguos se utilizan solamente para reconstruir bases ficticias aisladas, aplicando las correcciones vigentes.

La lista inicial del Word y la tabla de ciclos difieren de los 32 casos detallados. Se mantiene la organización solicitada por el usuario CU01–14 / CU15–20 / CU21–26 / CU27–32; no se atribuye esa corrección al docente. La interpretación de “Vista” del backend generado continúa pendiente: DTO no equivale automáticamente a esa capa.

Por indicación actual del usuario, Flutter queda aplazado. Se conserva como requisito histórico su funcionamiento con voz, IA y persistencia locales sin Internet y adaptación a negocios diversos. No se implementa en este incremento.

## Funciones incorporadas
- Administración: búsqueda y filtros de usuarios; alta con credencial temporal aleatoria; activación con contraseña personal; actualización, estado, roles globales y restablecimiento.
- Roles: alta, modificación de nombre/descripción/estado, búsqueda y filtros de ámbito/estado; matriz de permisos. Código y ámbito permanecen estables para conservar las asignaciones.
- Miembros: búsqueda de cuentas activas, incorporación, rol de proyecto, excepciones PERMITIR/DENEGAR, suspensión, retiro, reactivación y transferencia explícita de propiedad.
- Modelado: paquetes jerárquicos, edición, asignación de clases y borrado lógico de paquetes vacíos mediante el protocolo transaccional existente.
- Validación: solicitud asíncrona, informe persistido, errores/advertencias, localización de elementos y detección de informes correspondientes a otra revisión.

No se declara completado todo el ciclo inicial. El ciclo de vida de proyectos, recuperación personal, auditoría consultable y otras limitaciones se enumeran en el seguimiento.

## Decisiones de acceso
El servidor consulta la identidad y los permisos vigentes. Ocultar un botón no constituye autorización. Una cuenta nueva queda PENDIENTE y debe activar la credencial para elegir una contraseña de 12 caracteres como mínimo y hasta 72 bytes UTF-8.

La credencial temporal es aleatoria, se devuelve una vez y solo se conserva un hash bcrypt con marcador privado `{temporal}`. Este marcador evita que INACTIVO → ACTIVO eluda la activación. Nunca forma parte del listado ni del almacenamiento del navegador. Restablecer reemplaza la credencial anterior y revoca sesiones. Su entrega es manual; no hay envío automático de correo ni expiración temporal implementada todavía.

La cuenta propia administrativa, el último superadministrador activo y el rol SUPERADMINISTRADOR están protegidos. Un administrador delegado no puede modificar un superadministrador ni conceder autorizaciones superiores a las suyas. Los roles globales y de proyecto se comprueban por ámbito.

Los cambios administrativos usan un bloqueo transaccional común y las membresías toman su modalidad compartida antes de bloquear el proyecto. La revisión `actualizado_en` se exige al modificar recursos: una revisión obsoleta produce 409 y exige recargar/revisar. No se sobrescribe una edición concurrente silenciosamente.

DENEGAR prevalece sobre PERMITIR y sobre permisos heredados. Las asignaciones revocadas y membresías retiradas se conservan. Para retirar al propietario se requiere un sucesor activo; transferir añade el rol de propietario y revoca sus denegaciones. El propietario anterior conserva sus roles hasta una modificación explícita posterior. La pantalla configura un rol principal por miembro; guardar reemplaza sus asignaciones vigentes y conserva las anteriores como revocadas.

La suspensión/revocación cierra los registros existentes de participación y las sesiones abiertas por el miembro. El transporte de presencia y la desconexión WebSocket se desarrollarán en colaboración; no se simulan aquí.

## Persistencia UML
Los paquetes y las reubicaciones requieren ORGANIZAR_MODELO además de EDITAR_MODELO. Omitir `idPaquete` al actualizar una clase conserva la ubicación; `null` la lleva a la raíz y requiere autorización para organizar.

Todo cambio sigue una sola transacción/conexión:
autorizar → reconocer UUID confirmado → iniciar_edicion_modelo → aplicar JDBC → registrar cambio_modelo → guardar_version_modelo → commit. JDBC no necesita flush JPA. Los eventos de cambio confirmado se procesan AFTER_COMMIT; aún no existe transporte WebSocket.

No se actualizan revisiones/punteros de versión desde Java, no se crean instantáneas arbitrarias y Hibernate no administra el esquema. Las restricciones existentes rechazan ciclos, referencias ajenas y paquetes con contenido. Los errores SQLSTATE 40001 revierten el lote y reciben el tratamiento de idempotencia/409 del núcleo.

## Validación semántica
POST solicita el trabajo y responde 202. El worker consulta trabajos confirmados y usa dos hilos con cola limitada. Los trabajos EN_EJECUCION se recuperan tras un reinicio; la finalización bloquea el registro e impide duplicar hallazgos.

El motor analiza la instantánea inmutable creada por PostgreSQL, nunca una edición parcial del cliente. Persiste el informe, los hallazgos, las reglas evaluadas y la revisión. Actualiza únicamente `estado_validacion` de la versión, metadato permitido por el contrato. Una revisión diferente produce DESACTUALIZADO. Una regla requerida ausente o una regla activa sin implementación produce FALLIDO y no permite declarar válido el modelo.

Se implementan las nueve claves del catálogo vigente: firmas de operaciones, identificador de entidades persistentes, unicidad/nombre de clases, tipos, extremos/cardinalidades, ciclos de generalización y advertencia de compatibilidad XMI pendiente. Se añaden comprobaciones de referencias, parámetros, identificadores, propiedad de composición y valores predeterminados.

Los tipos simples se resuelven contra primitivas conocidas y clases activas. Dos clases homónimas en paquetes diferentes exigen tipo cualificado, como `ventas.Cliente`. Los valores numéricos se comprueban contra el rango de su tipo.

Las restricciones JSON min/max/minLength/maxLength/pattern tienen una interpretación técnica provisional de estructura y coherencia. Un patrón se compila para comprobar su sintaxis; no se ejecuta como SQL. Otras claves se conservan y generan advertencia. Este vocabulario debe acordarse antes de convertirlo en validaciones del backend generado. No se interpreta texto libre como código.

VALIDO significa que pasó el motor semántico disponible; no acredita importación/exportación Enterprise Architect ni compilación de un backend generado. Esas fases tendrán sus propias comprobaciones.

## API incorporada
Todas las escrituras requieren sesión y CSRF, excepto activar, que es pública pero también requiere CSRF. Ninguna API expone credenciales PostgreSQL ni permite SQL libre.

| Ruta bajo /api | Acción |
|---|---|
| POST /acceso/activar | Activar credencial temporal y elegir contraseña |
| GET/POST /administracion/usuarios | Consultar o crear cuentas |
| PUT /administracion/usuarios/{id} | Actualizar con revisión |
| POST /administracion/usuarios/{id}/restablecer | Reemplazar credencial y revocar sesiones |
| GET /administracion/roles-usuario | Catálogo para asignar roles globales |
| GET/POST /administracion/roles | Consultar o crear roles |
| PUT /administracion/roles/{id} | Modificar rol/permisos con revisión |
| GET /administracion/permisos | Consultar catálogo vigente |
| GET/POST /proyectos/{id}/miembros | Consultar o incorporar miembros |
| GET /proyectos/{id}/cuentas?filtro=... | Buscar cuentas activas para incorporar |
| PUT /proyectos/{id}/miembros/{miembro} | Modificar membresía con revisión |
| POST /proyectos/{id}/propietario | Transferir a miembro activo |
| POST /modelos/{id}/validaciones | Encolar validación |
| GET /modelos/{id}/validaciones | Consultar último informe |
| GET /modelos/{id}/validaciones/{trabajo} | Consultar trabajo autorizado |

`PAQUETE` se incorpora a las entidades de POST /modelos/{id}/cambios con `nombre`, `descripcion` e `idPaquetePadre`. Las clases aceptan `idPaquete`. Se conserva el formato UUID/revisionBase/acciones del primer incremento.

## Migración separada
Archivo: [2026-09-14_01_revocacion_permisos_rol.sql](migraciones/2026-09-14_01_revocacion_permisos_rol.sql).

La instalación original concede SELECT/INSERT/UPDATE sobre rol_permiso, pero no DELETE. Esa tabla puente no tiene borrado lógico; revocar una asignación requiere DELETE. Se añade exclusivamente ese privilegio a uml_app; no se amplían privilegios en otras tablas ni se alteran columnas/FK. La cuenta propietaria uml_admin se utiliza solamente para esta migración, nunca como datasource.

El backend no ejecuta la migración automáticamente. Para revertir el privilegio: `REVOKE DELETE ON TABLE uml_colaborativo.rol_permiso FROM uml_app;`. Revertir impedirá revocar permisos a través de la plataforma.

Aplicada el 14 de septiembre en la base real como propietario después de las pruebas aisladas. Se verificó el privilegio con uml_app, manteniendo 48 tablas, 507 columnas, 119 FK y el usuario existente. Las pruebas y límites se registran en el [seguimiento](2026-09-14_seguimiento_incremento_02.md).

## Ejecutarlo
Desde Implementacion, tras compilar la versión 0.2:
```powershell
./core/docs/2026-09-14_ejecutar_backend.ps1 -VerificarEntorno
./core/docs/2026-09-14_ejecutar_backend.ps1 -HttpLocal
```

Si la versión anterior sigue abierta, detén su terminal con Ctrl+C antes del segundo comando. El administrador personal ya existe: no repitas -InicializarAdministrador.

En otra terminal:
```powershell
Set-Location core/frontend
npm run dev
```

Abrir http://127.0.0.1:5173. Administración aparece para las cuentas autorizadas. Miembros se abre desde la tarjeta del proyecto. Nuevo paquete e Informe de validación están en la barra del editor.

El lanzador solicita la clave PostgreSQL mediante entrada oculta y escribe el registro técnico en .qa, excluido de Git y ubicado en D:. Acceso y administración registran UUID/acción después del commit, sin contraseñas, tokens ni correo. Esta bitácora técnica no sustituye una futura auditoría global transaccional y consultable. La actividad de miembros sí se registra atómicamente en actividad_proyecto.
