# Seguimiento consolidado — Incremento 10

Fecha de cierre: 2026-09-20

Rama de trabajo: `codex/incremento-10-estabilizacion`

Versión consolidada: backend `0.10.0-SNAPSHOT`, frontend `0.10.0`

Este documento representa el estado técnico vigente al cierre del Incremento 10. Los seguimientos anteriores se conservan como evidencia histórica; cuando exista una diferencia de estado, prevalecen esta consolidación y la [matriz de trazabilidad](2026-09-20_matriz_trazabilidad_casos_uso.md).

## Objetivo cumplido

Se estabilizó la línea construida hasta P04 antes de incorporar nuevos casos de uso. El incremento corrigió la trazabilidad funcional, normalizó permisos contra los roles reales, amplió la cobertura de sesiones, XMI y generación, alineó las versiones y cerró todas las verificaciones automatizadas en el entorno aislado.

## Cambios consolidados

### Trazabilidad

- Se creó una matriz única para los 31 casos del catálogo y las 32 fichas detalladas.
- Se documentó el desfase provocado por la ficha detallada CU05, **Gestionar permisos**, sin asumir una corrección no confirmada por el docente.
- Cada caso quedó clasificado como completo comprobado, avanzado parcial o pendiente, con su evidencia y brecha principal.
- Se mantuvo el orden aprobado de continuidad: el próximo trabajo funcional es el Incremento 11, **Configurar servicios de la plataforma**.

### Roles y permisos

- Las migraciones de historial e interoperabilidad ahora usan `PROPIETARIO_PROYECTO`, `EDITOR_PROYECTO` y `LECTOR_PROYECTO`.
- Se conservaron `LIDER` y `COLABORADOR` como alias compatibles por código o nombre, sin inventar roles nuevos.
- El propietario puede consultar/restaurar historial y configurar/generar/exportar/importar/descargar.
- El editor puede consultar historial y exportar/importar/descargar, pero no restaurar, configurar ni generar.
- El lector conserva únicamente la consulta del historial.
- Las asignaciones son idempotentes mediante `ON CONFLICT DO NOTHING`.

### Estabilidad del backend

- Las violaciones de validación de parámetros se traducen a respuesta HTTP 400.
- El paquete Java predeterminado de un backend generado se normaliza a un identificador válido, incluso cuando el nombre del modelo contiene guiones, espacios u otros caracteres.
- Se añadieron pruebas de integración para historial propio y administrativo, paginación, permisos de roles reales y autorización de interoperabilidad sin depender del superadministrador.

### Cobertura del navegador

- Se añadió el recorrido de consulta del historial de sesiones desde la interfaz.
- Se verificó la descarga XMI desde el editor.
- Se verificó la configuración, generación y descarga del backend ZIP.
- Se corrigió el plural visible de “sesiones registradas”.

### Versionado

- Maven y el artefacto ejecutable quedaron en `0.10.0-SNAPSHOT`.
- El paquete frontend y su archivo de bloqueo quedaron en `0.10.0`.
- Los metadatos del exportador XMI y del generador quedaron en `0.10.0`.
- Los lanzadores de backend y navegador apuntan al artefacto vigente.

## Verificación final

Todas las pruebas automatizadas utilizaron bases ficticias creadas en PostgreSQL `127.0.0.1:55433`. No se estableció ninguna conexión con el PostgreSQL de Docker del puerto `55432`.

| Control | Resultado |
|---|---:|
| Google Java Format AOSP | Aprobado |
| Pruebas Maven del núcleo | 98 aprobadas, 0 fallos, 0 errores, 0 omitidas |
| Prettier `format:check` | Aprobado |
| Compilación TypeScript/Vite | Aprobada |
| Pruebas Playwright | 22 aprobadas |
| Empaquetado Spring Boot | Aprobado |

Vite mantiene una advertencia no bloqueante: el paquete principal minificado supera 500 kB. La división de código queda como mejora de rendimiento, no como defecto funcional de este incremento.

## Límites conservados

- No se modificó el esquema base de datos original.
- No se tocaron archivos de otras materias ni materiales académicos originales.
- No se publicaron cambios ni se configuraron remotos.
- No se implementaron anticipadamente los incrementos 11 a 18.
- Las bases ficticias de verificación se conservan para inspección según el comportamiento de los lanzadores de prueba.

## Próximo avance autorizado por el plan

El Incremento 11 debe implementar la configuración de servicios de plataforma: contrato sobre `servicio_plataforma` y `secreto_servicio`, cifrado o referencia segura de secretos, permisos administrativos, API, interfaz, auditoría y pruebas. Su desarrollo comenzará únicamente con autorización expresa.
