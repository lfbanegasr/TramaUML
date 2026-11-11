# UML Colaborativo para Android

Cliente Flutter de la plataforma, preparado para un dispositivo Android ARM64 como el Redmi Note 13.

## Acceso y sesiones

Utiliza la misma cuenta que en escritorio. El backend crea un token opaco independiente por inicio de sesión y lo entrega en la cookie `UML_SESION`. Android conserva ese token en almacenamiento seguro; no guarda la contraseña. El cierre de sesión revoca el token de este dispositivo. La duración actual de acceso es de dos horas y no existe renovación automática.

La participación colaborativa está identificada por usuario, no por dispositivo. Por eso Android cierra su WebSocket al salir o ir a segundo plano, sin eliminar la membresía de la sesión compartida con escritorio. El servidor ya evita marcar ausente al usuario mientras tenga otra conexión activa. No se muestran dos personas diferentes cuando la misma cuenta abre ambos clientes.

## Ejecutar en el Redmi por USB

1. Inicia el backend existente en el PC, en el puerto 8080. La configuración HTTP local debe estar habilitada mediante el script existente `core/docs/2026-09-14_ejecutar_backend.ps1 -HttpLocal`.
2. En Android activa las opciones de desarrollador y Depuración USB. Conecta el cable y acepta la autorización del PC. Algunos equipos Xiaomi requieren habilitar también la instalación por USB.
3. Desde esta carpeta ejecuta:

```powershell
.\2026-11-10_ejecutar_android.ps1
```

El script configura `adb reverse tcp:8080 tcp:8080` y ejecuta Flutter con `API_URL=http://127.0.0.1:8080`. Así el teléfono accede al backend del PC aunque este solo escuche en loopback. El túnel debe restablecerse después de desconectar o reiniciar el dispositivo.

Si ya hay un APK compilado:

```powershell
.\2026-11-10_ejecutar_android.ps1 -SoloInstalar
```

En ese caso, abre «Conexión al servidor» en el acceso y escribe `http://127.0.0.1:8080`. La dirección se conserva tras entrar.

## Conexión por Wi-Fi

Ambos dispositivos deben compartir red. El backend debe escuchar en la interfaz de red del PC y el firewall permitir el puerto. En el acceso introduce `http://IP_DEL_PC:8080`, sin `/api`. Al implementar esta app se detectó `192.168.0.200`, pero esa dirección puede cambiar. No se modifica automáticamente el firewall ni la configuración del backend.

Se permite HTTP para la demostración local. Para un servidor remoto utiliza HTTPS. El cliente conserva y envía las cookies y el token CSRF también durante la conexión WebSocket.

## Funciones implementadas

- Acceso, restauración de sesión y cierre de sesión por dispositivo.
- Proyectos con búsqueda, paginación y creación según permisos.
- Visor UML táctil con zoom, desplazamiento, ajuste, búsqueda, clases, atributos, operaciones y relaciones; ficha de detalle completa.
- Agente con texto, dictado nativo editable y captura/selección de imagen PNG/JPG.
- Revisión de propuestas, selección de operaciones con dependencias, rechazo y aplicación mediante la API existente.
- Protección ante propuestas obsoletas y revisión concurrente en el servidor.
- Canal WebSocket autenticado, presencia, actualización de modelo y reconexión al volver a primer plano.
- Equipo y actividad reciente.
- Historial de conversación y borrador de texto locales, separados por servidor, cuenta y proyecto. No constituyen memoria del agente en el backend.

## Alcance del agente

La app utiliza el agente existente. La generación compleja por texto depende del proveedor IA configurado en el servidor. El dictado se convierte en texto editable y usa la misma ruta de texto; no depende del formato de audio que grabe Android.

El backend actual no ofrece conversaciones persistentes compartidas ni respuestas consultivas independientes de propuestas. Tampoco se implementa aquí el agente ampliado con memoria del equipo: ese trabajo sigue siendo una evolución del backend. Los mensajes locales no se envían como historial al proveedor. Las imágenes se envían al confirmar y se conservan en memoria mientras el proyecto sigue abierto; el texto del borrador sí se recupera al reiniciar.

No se incluye editor manual completo, administración global, importación/exportación EA ni configuración de generación Spring Boot. Estas funciones continúan en escritorio.

## Comprobación

```powershell
flutter pub get
flutter analyze
flutter test
flutter build apk --debug --target-platform android-arm64
```

APK de prueba: `build/app/outputs/flutter-apk/app-debug.apk`. Firma de depuración para instalación local, no publicación en Play Store.

Para una prueba completa: entra con tu cuenta, abre un proyecto tanto en web como Android, verifica presencia y cambios remotos, solicita una clase de prueba desde Android, revisa la propuesta y aplícala. Comprueba en web la nueva versión. Después genera una propuesta, cambia el modelo desde escritorio e intenta aplicar la propuesta anterior: debe bloquearse. Cierra Android y comprueba que el escritorio sigue conectado.

## Procedencia

Contratos reutilizados: `core/backend/src/main/java/bo/edu/uml/{acceso,proyectos,modelado,colaboracion,ia}` y `core/frontend/src/features/{ia,colaboracion}/api`. No se modifica el backend ni las fuentes originales del sistema.
