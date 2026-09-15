# Seguimiento del incremento 04

Fecha de inicio: 2026-09-14. Cierre: 2026-09-15. Estado: incremento verificado en bases aisladas; despliegue personal pendiente. Versión: backend 0.4.0-SNAPSHOT y frontend 0.4.0.

## Resultado implementado

- Sala colaborativa persistente por modelo, participantes lectores/editores, presencia, foco y salida explícita.
- WebSocket nativo de Spring, autenticado con la sesión opaca y limitado al mismo origen. Los clientes solo envían pulso y foco; las escrituras UML conservan REST, CSRF, UUID idempotente y revisión base.
- Cambios confirmados difundidos únicamente `AFTER_COMMIT`; el resto de navegadores recupera la revisión nueva. Los formularios pendientes se conservan y se marcan en conflicto.
- Reautorización en conexión, pulso, foco y difusión. Los cambios de rol, excepciones, suspensión o retiro degradan a lector o desconectan después del commit. `DENEGAR` sigue prevaleciendo mediante la función de autorización vigente.
- La edición requiere una sesión real `EDITOR/EN_LINEA` y deja trazabilidad en `cambio_modelo.id_sesion_edicion` y `actividad_proyecto`.
- Interfaz con estado del canal, participantes, rol de sesión y elemento enfocado; reintentos acotados ante pérdida de conexión y bloqueo seguro de escritura cuando el servicio no está disponible.
- Versión, lanzadores y proxy Vite actualizados a 0.4.0. La cookie opaca usa `Path=/` para cubrir REST y WebSocket sin exponer el token a JavaScript.

Los contratos y decisiones están en [contratos del incremento 04](2026-09-14_incremento_04_contratos.md).

## Cobertura por caso de uso

- **Abrir sesión de modelado (catálogo CU09 / detalle CU10): parcial avanzado.** Autoriza, carga el modelo, registra presencia y aplica modo lector/editor; el informe de validación continúa disponible en el editor, pero falta confirmar si el flujo exige mostrar siempre un estado resumido sin abrirlo. La creación del proyecto ya garantiza el modelo inicial vacío.
- **Editar colaborativamente (catálogo CU16 / detalle CU17): parcial.** Cubre participantes, foco, cambios confirmados, salida, reconexión, servicio limitado y pérdida de permisos. La resolución comparada y persistente de incompatibilidades pertenece al detalle CU18 y sigue pendiente. El archivo durante una sala no ocurre porque el incremento 03 bloquea el archivo con sesiones editables.
- **Consultar actividad (detalle CU15): ampliado.** Incorpora apertura, incorporación, salida y cambios UML asociados a la sala.
- **Organización visual (detalle CU16): parcial previo.** El movimiento individual se sincroniza; tamaños, selección múltiple, alineación, configuración de vista y autoorganización siguen pendientes.

## Pruebas y evidencia

- PostgreSQL 18 nativo, base ficticia nueva `uml_collab_test_c98dd7162c2c` en 127.0.0.1:55433: **40 pruebas de servidor aprobadas**, 0 fallos, errores u omitidas. Son 37 de integración (43,28 s) y tres del motor semántico; la ejecución Maven completa tomó 50,710 s.
- PostgreSQL 17.11 portátil, base ficticia nueva `uml_collab_test_2bd49dbf03d2` en 127.0.0.1:55435: las mismas **40 pruebas de servidor aprobadas**, 0 fallos, errores u omitidas. Son 37 de integración (38,04 s) y tres del motor semántico; la ejecución Maven completa tomó 42,413 s.
- Playwright/Chromium contra PostgreSQL 18 aislado: **14 recorridos de navegador aprobados** en 1,2 min, incluidas dos sesiones de usuario simultáneas, presencia, foco, propagación de una versión confirmada, degradación a lector, recuperación de conflictos, permisos, rollback, paginación y regresión de los incrementos anteriores. El resultado estructurado queda en `core/frontend/test-results/resultado.json` y la evidencia visual en `core/frontend/test-results/incremento4-colaboracion-verificada.png`.
- Compilación de producción React/TypeScript aprobada: 197 módulos y paquete Vite generado. Empaquetado Spring Boot aprobado: `core/backend/target/plataforma-0.4.0-SNAPSHOT.jar`.
- Prettier, formato Java, `git diff --check` y sintaxis de los cuatro scripts PowerShell aprobados. `2026-09-14_ejecutar_backend.ps1 -VerificarEntorno` localizó Java 21 y el JAR 0.4 sin solicitar secretos ni iniciar la aplicación.

Los arneses crearon únicamente bases con prefijo `uml_collab_test_` en puertos exclusivos. Nunca usaron la base personal 55432. Durante la regresión se corrigieron defectos observables en foco nulo, personalización del modo por destinatario, reconocimiento del cambio propio y desbloqueo de la recarga tras un conflicto; después se repitieron las suites completas.

## Límites y pendientes visibles

- Implementar el detalle CU18 con registro de conflictos, comparación de propuestas, autorización para resolver y rollback íntegro.
- Implementar consulta/comparación de historial y restauración como nueva versión, respetando los procedimientos PostgreSQL y las sesiones pendientes.
- Añadir un transporte compartido antes de ejecutar varias instancias del backend. La difusión WebSocket actual es por proceso.
- Aclarar con el docente la numeración contradictoria CU09/CU10 y CU16/CU17, la política de archivo durante una sesión y la visibilidad permanente del estado de validación.
- Continúan pendientes auditoría global, notificaciones, expiración de credenciales y política definitiva de roles múltiples. No se resolvieron mediante supuestos.
- Flutter permanece aplazado por decisión actual del usuario; se conserva como requisito histórico offline para una fase posterior.

## Despliegue personal

No se inicializó otro administrador, no se restablecieron contraseñas, no se ejecutó SQL contra la base personal y no se recrearon contenedores o volúmenes. El reinicio personal con el JAR 0.4 queda a cargo del usuario mediante `2026-09-14_ejecutar_backend.ps1 -HttpLocal`, sin `-InicializarAdministrador`.

Los servidores PostgreSQL nativos de prueba de los puertos 55433 y 55435 pueden continuar activos. Una solicitud anterior para detenerlos fue rechazada por el revisor automático al alcanzarse su límite temporal; no se intentó eludir esa decisión. Sus directorios y bases son exclusivos de `.qa` y no corresponden al contenedor personal.

## Siguiente incremento

Continuar con gestión de conflictos del detalle CU18 y preparar historia/comparación/restauración. Antes de restaurar se deberá estudiar el procedimiento vigente de versiones; no se insertarán snapshots ni se actualizarán revisiones directamente.
