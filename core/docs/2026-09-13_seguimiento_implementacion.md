# Seguimiento de implementación — 2026-09-13

Estado histórico al cierre del 13 de septiembre: desarrollo pausado por solicitud del usuario para recuperar Docker y su almacenamiento en D:. El desarrollo se retomó el 14 de septiembre; consultar el seguimiento de esa fecha para el estado vigente. Este registro conserva los resultados y pendientes de la pausa.

## Fuentes y decisiones

- Documentacion/Documentacion.docx y Documentacion/Contexto del primer parcial.docx, relativos a Primer parcial; lectura sin modificar los originales.
- docs/2026-09-13_guia_persistencia_corregida.md y docs/2026-09-13_diccionario_base_datos_vigente.md, relativos a Implementacion; gobiernan la persistencia actual.
- Los flujos detallados contienen 32 CU, pero la lista inicial y la tabla de ciclos tienen diferencias de numeración. Se adopta la organización actual solicitada por el usuario: CU01–14, CU15–20, CU21–26, CU27–32; no se atribuye esta corrección al docente.
- Flutter con voz, IA y persistencia locales sin Internet permanece como fase adicional obligatoria. No se limita a un negocio específico.
- La interpretación de la capa Vista del backend generado queda pendiente de aclaración; DTO no se considera automáticamente equivalente.
- Spring Boot y React en core/backend y core/frontend. Este backend pertenece a la plataforma; la generación de otros backends aún no está implementada.
- Acceso JDBC con transacciones explícitas y cuenta uml_app; no hay creación automática de esquema ni actualizaciones de Hibernate. Credenciales exclusivamente mediante variables de entorno.
- Sesiones opacas, hash del token en PostgreSQL, cookies HttpOnly y protección CSRF. Autorización comprobada en el servidor en cada petición.
- Edición: autorizar, iniciar_edicion_modelo, aplicar y registrar cambio_modelo, guardar_version_modelo y commit en una conexión. JDBC no requiere flush JPA. El listener se ejecuta después del commit; todavía no publica por WebSocket.

## Alcance implementado y comprobado

- CU01 y CU02: servicios de autenticación, bloqueo por intentos fallidos, consulta de sesión y cierre; pruebas del servidor aprobadas. Interfaz escrita, pendiente de comprobación en navegador.
- CU06: consulta paginada de proyectos autorizados, filtros y comprobación de membresía vigente; pruebas del servidor aprobadas. Interfaz pendiente de comprobación.
- Inicialización explícita del primer administrador, sin contraseña fija, rechazada cuando ya existe cualquier usuario; comprobada únicamente en la base aislada.
- CU07 parcial: creación de proyecto, propietario y modelo inicial con versión registrada por el contrato de persistencia.
- CU10–14 parciales: lectura del modelo, clases, atributos, relaciones, multiplicidades, posición, borrado lógico y validaciones estructurales. Operaciones, parámetros, paquetes y todos los flujos académicos todavía no están completos.
- Editor React escrito con lienzo UML y llamadas reales a la API. No contiene una base simulada; su ejecución completa aún no ha sido verificada.

## Pruebas ejecutadas

PostgreSQL nativo aislado en .qa/postgres/data, puerto 55433, base uml_collab_test_core. Los scripts de instalación revisados se aplicaron exclusivamente a esa base ficticia. No se recreó ni alteró la instalación real de Docker.

- NucleoIntegracionTest: 10 pruebas, 0 fallos, 0 errores y 0 omitidas, con Spring Boot, seguridad y PostgreSQL real aislado. Informe: core/backend/target/surefire-reports/bo.edu.uml.NucleoIntegracionTest.txt. Registro local: .qa/2026-09-13_pruebas_backend.log.
- Cobertura: administrador inicial, hashes, autenticación/CSRF, revocación, bloqueo, permisos y membresías, idempotencia, revisiones obsoletas, rollback del lote, multiplicidades/borrado, concurrencia y restricciones del contrato de versiones.
- La compilación inicial del backend y las pruebas de integración finalizaron correctamente. El empaquetado definitivo tras todos los cambios queda pendiente.
- Instalación de dependencias de React completada con caché en D:. La compilación del frontend quedó bloqueada por EPERM al escribir tsconfig.tsbuildinfo en el entorno restringido; no se afirma que haya pasado.

## Recuperación de Docker antes de continuar

El usuario confirmó que D:/DockerData/DockerDesktopWSL era la ubicación que funcionaba. La configuración actual había vuelto a C:/Users/fernan/AppData/Local/Docker/wsl. No se confirmó por qué se restableció ni que el inicio de sesión lo haya causado.

- Se respaldó la configuración en D:/DockerData/2026-09-13_respaldo_configuracion.
- Se conservó intacto el disco original D:/DockerData/DockerDesktopWSL/disk/docker_data.vhdx, de 19,58 GiB.
- Con Docker y WSL detenidos, se trasladó el disco nuevo de C: al respaldo en D:; su hash antes y después coincidió. No se sobrescribió el original.
- La distribución docker-desktop se trasladó mediante wsl --manage --move a D:/DockerData/DockerDesktopWSL/main. Se restauró CustomWslDistroDir a D:/DockerData/DockerDesktopWSL.
- Comprobado: WSL inicia correctamente desde D:. C: pasó de casi cero a aproximadamente 0,87 GiB libres; D: tiene aproximadamente 15,52 GiB libres.
- Docker todavía no inicia: Windows mantiene inaccesible el socket temporal de docker-secrets-engine/engine.sock. Las carpetas temporales Docker/run se conservaron con otros nombres; el cambio de nombre del directorio restante fue rechazado por Windows incluso con Docker detenido.
- Fallo coincidente reportado: https://github.com/docker/desktop-feedback/issues/460 y https://github.com/docker/desktop-feedback/issues/531. Son reportes de incidencias; la relación con este equipo se infiere del error coincidente.
- Docker y WSL quedaron detenidos. No se reinició Windows, no se eliminaron volúmenes y no se restablecieron datos de fábrica.
- Pendiente inmediato: reiniciar Windows después de guardar el trabajo, abrir Docker y comprobar los contenedores originales y PostgreSQL en 55432 con uml_app. La base real todavía no se pudo verificar.

## Pendientes del incremento y siguiente paso

Después de recuperar Docker, completar instrucciones reproducibles de ejecución e inicialización sin secretos, compilar React, comprobar acceso/proyectos/editor en navegador contra PostgreSQL y corregir los resultados. Proteger el incremento con Git local incluyendo únicamente los archivos del núcleo, sin dependencias, datos, fuentes extraídas ni credenciales.

Resolver la gestión de borradores y solicitudes pendientes al navegar o recibir errores de validación. Completar operaciones/parámetros/paquetes y el resto del ciclo inicial conforme a los flujos relevantes. Los ciclos de colaboración, IA, XMI y generación permanecen pendientes. CU03–05, CU08–09 y los CU posteriores no se declaran completos.
