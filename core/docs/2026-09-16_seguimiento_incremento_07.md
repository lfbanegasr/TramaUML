# Seguimiento del incremento 07

Fecha: 2026-09-16. Estado: implementado, pendiente ejecucion de suite de pruebas en terminal real.
Version: backend 0.7.0-SNAPSHOT, frontend 0.7.0.

## Resultado implementado

### Grupo A - Correccion de guia de ejecucion
- 2026-09-16_guia_ejecucion.md corregida: eliminadas las credenciales ficticias
  admin@sistema.local / admin123, que violaban las convenciones de seguridad.
- La guia ahora distingue correctamente el comando unico de inicializacion
  (-InicializarAdministrador) del arranque normal (-HttpLocal).

### Grupo B - CU15/CU06: Estadisticas de resumen ampliadas

- Backend ProyectoDto.Resumen: nuevo campo totalVersiones (long).
- Backend ProyectoRepositorio: subquery count(*) from version_modelo join modelo_uml añadida.
- Frontend proyectosApi.ts: totalVersiones: number añadido al tipo Proyecto.
- Frontend ProyectoResumen.tsx: nuevo elemento dt Versiones en el historial.

### Grupo C - CU01/CU02: Historial de sesiones

- Backend AccesoRepositorio: historialSesiones() y totalSesiones(). Record SesionResumen.
- Backend AccesoDto.PaginaSesiones: nuevo record con lista paginada.
- Backend AccesoControlador: GET /api/acceso/sesiones (paginado, validado).
- Backend AdministracionServicio/Controlador: GET /api/administracion/usuarios/{id}/sesiones.
- Frontend accesoApi.ts: tipos SesionResumen, PaginaSesiones, consultarSesiones().
- Frontend SesionesPanel.tsx: nuevo componente modal con tabla paginada de sesiones.
- Frontend App.tsx: import SesionesPanel, estado verSesiones, boton #btn-mis-sesiones.

### Sin migracion SQL
La tabla sesion_autenticacion ya tenia todas las columnas necesarias.

## Verificacion completada
- TypeScript build: OK (exit code 0, 204 modulos, 0 errores TypeScript)
- Prettier: 100% conforme en todos los archivos

## Pruebas backend pendientes de ejecucion manual
Ejecutar desde Implementacion\:
  .\core\docs\pruebas\2026-09-14_probar_nucleo.ps1
Confirmar 67/67 (las nuevas funciones no tienen pruebas en esta iteracion).

## Proximos pasos - Incremento 08
- Pruebas NucleoIntegracionTest para historialSesiones y totalSesiones.
- Test Playwright para #btn-mis-sesiones -> SesionesPanel.
- Historial de sesiones de otros usuarios accesible desde UI de administracion.
- CU01 completo: bitacora de intentos fallidos de acceso.
