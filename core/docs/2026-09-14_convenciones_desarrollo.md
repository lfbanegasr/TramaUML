# Convenciones de desarrollo — 2026-09-14

Estas decisiones se aplican al código de la plataforma. Los Word originales y scripts de antecedentes se conservan.

## Organización y responsabilidades
Java usa módulos de negocio con controlador, DTO, servicio, repositorio y modelo cuando corresponde. El controlador recibe/valida entradas y delega. El servicio autoriza y coordina reglas/transacciones. El repositorio contiene SQL parametrizado, sin concatenar entradas del usuario. El motor semántico no accede a PostgreSQL ni toma decisiones de diseño.

React se organiza por funcionalidades con API tipada, páginas, componentes y estado; los elementos comunes permanecen en shared. Las credenciales temporales se mantienen solo mientras se muestra la respuesta, sin localStorage/sessionStorage. Un error de guardado conserva el formulario para corregirlo; 409 exige revisión explícita.

No se añade una capa o interfaz sin una responsabilidad concreta. El módulo modelado no se mezcla con el backend que posteriormente se generará para otros negocios.

## Formato comprobable
[core/.editorconfig](../.editorconfig) fija UTF-8, LF y sangría. PowerShell se conserva con BOM por compatibilidad con Windows PowerShell 5.1.

Java: google-java-format 1.36.1, modalidad AOSP de cuatro espacios. Es una elección de formato del proyecto, no una afirmación de cumplimiento íntegro de todas las reglas de Google Java Style. El JAR se descarga de la [publicación oficial](https://github.com/google/google-java-format) y el script verifica un SHA-256 fijado antes de ejecutarlo. Permanece en .qa/tools de D:, fuera de Git.

Desde Implementacion:
```powershell
./core/docs/2026-09-14_formato_java.ps1 -InstalarFormateador
./core/docs/2026-09-14_formato_java.ps1
./core/docs/2026-09-14_formato_java.ps1 -Aplicar
```

-InstalarFormateador solo es necesario si falta el archivo. Sin -Aplicar, el script comprueba y falla si hay diferencias. JavaHome permite elegir un JDK 21 existente sin modificar el sistema.

React/TypeScript/CSS: Prettier 3.9.6 como dependencia exacta y archivo de bloqueo. [Instalación oficial de Prettier](https://prettier.io/docs/install).

```powershell
Set-Location core/frontend
npm run format:check
npm run format
npm run build
```

El formato no demuestra seguridad ni corrección funcional; se complementa con compilación y pruebas de comportamiento.

## Seguridad y persistencia
- La sesión se resuelve en el servidor, mediante token opaco cuyo hash se almacena en PostgreSQL. Cookies HttpOnly/SameSite y CSRF para escrituras; Secure por defecto.
- Entradas con DTO y límites; campos UML con listas permitidas; SQL parametrizado; respuestas de error sin consultas ni secretos.
- Sin cuentas ni contraseñas fijas. El primer administrador se crea localmente solo si no existen usuarios.
- La aplicación siempre usa uml_app. La cuenta propietaria solo interviene en migraciones separadas y justificadas.
- Borrado lógico para entidades/asignaciones que disponen de él; las instantáneas/revisiones siguen las funciones existentes.
- Idempotencia mediante UUID y comparación del envío confirmado; transacciones completas y eventos posteriores al commit.
- Revisiones administrativas obligatorias en modificaciones; protecciones contra autoexclusión, último administrador y escalamiento de autorizaciones.
- Workers con capacidad limitada; informes asociados a versiones inmutables, recuperación de pendientes y finalización idempotente.
- No se escriben secretos en Git, archivos de configuración, capturas ni bitácoras. No se publican remotos ni despliegues.

## Verificación y límites
Las pruebas del servidor ejercitan autorización, rollback, idempotencia y persistencia real en bases nuevas uml_collab_test_*. Las pruebas de Chrome recorren la aplicación con HTTP y PostgreSQL reales, usando credenciales ficticias aleatorias. La respuesta perdida se inyecta únicamente como fallo controlado en el test.

La suite protege el nombre de la base y rechaza usuarios previos. La preparación nativa comprueba el directorio real del clúster antes de crear datos. No usa 55432 ni elimina bases. Los procesos de prueba usan 8181/5174; la instalación personal permanece en 8080/5173.

No se afirma haber realizado una auditoría independiente, pruebas de carga para 100 ingenieros ni un despliegue de producción. El registro de acceso administrativo es técnico y posterior al commit; una auditoría global durable/consultable requiere una decisión de persistencia futura.
