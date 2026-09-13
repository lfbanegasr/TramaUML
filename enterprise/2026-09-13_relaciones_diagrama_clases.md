# Relaciones del diagrama de clases — 2026-09-13

Derivado del diccionario de la base corregida. [Script de Enterprise Architect](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/enterprise/2026-09-13_diagrama_clases_corregido.vbs>) · [Guía y validación](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/enterprise/2026-09-13_guia_diagrama_clases.md>).

Cada fila corresponde a UNA asociación del modelo. La multiplicidad escrita junto a A indica cuántas instancias de A pueden asociarse a una instancia de B; la escrita junto a B indica cuántas B pueden asociarse a una A. El verbo se lee A → B; la columna de lectura inversa se lee B → A. El sentido del verbo puede ser contrario al de la FK sin cambiar las cardinalidades ni inventar navegación.

| Nº | Clase A | Junto a A | Conector A → B | Clase B | Junto a B | Lectura inversa B → A |
|---|---|---|---|---|---|---|
| 1 | MODELO_UML | 0..* | pertenece a | PROYECTO | 1 | contiene |
| 2 | USUARIO | 0..1 | crea | MODELO_UML | 0..* | es creado por |
| 3 | USUARIO | 0..1 | crea | PAQUETE_UML | 0..* | es creado por |
| 4 | SOLICITUD_INTELIGENTE | 0..* | trabaja sobre | MODELO_UML | 1 | recibe |
| 5 | USUARIO | 1 | solicita | SOLICITUD_INTELIGENTE | 0..* | es solicitada por |
| 6 | CONFIGURACION_GENERACION_BACKEND | 0..* | se basa en | VERSION_MODELO_UML | 1 | sirve de base a |
| 7 | USUARIO | 1 | crea | CONFIGURACION_GENERACION_BACKEND | 0..* | es creada por |
| 8 | USUARIO | 1 | inicia | SESION_AUTENTICACION | 0..* | pertenece a |
| 9 | ROL | 1 | tiene | ROL_PERMISO | 0..* | pertenece a |
| 10 | ROL_PERMISO | 0..* | asigna | PERMISO | 1 | se asigna mediante |
| 11 | USUARIO | 1 | tiene | USUARIO_ROL | 0..* | pertenece a |
| 12 | USUARIO_ROL | 0..* | asigna | ROL | 1 | se asigna mediante |
| 13 | USUARIO | 0..1 | asigna | USUARIO_ROL | 0..* | es asignada por |
| 14 | USUARIO | 1 | posee | PROYECTO | 0..* | pertenece a |
| 15 | MIEMBRO_PROYECTO | 0..* | pertenece a | PROYECTO | 1 | tiene |
| 16 | USUARIO | 1 | participa mediante | MIEMBRO_PROYECTO | 0..* | representa a |
| 17 | MIEMBRO_PROYECTO | 1 | tiene | ROL_MIEMBRO_PROYECTO | 0..* | pertenece a |
| 18 | ROL_MIEMBRO_PROYECTO | 0..* | asigna | ROL | 1 | se asigna mediante |
| 19 | USUARIO | 0..1 | asigna | ROL_MIEMBRO_PROYECTO | 0..* | es asignada por |
| 20 | MIEMBRO_PROYECTO | 1 | tiene | PERMISO_MIEMBRO_PROYECTO | 0..* | pertenece a |
| 21 | PERMISO_MIEMBRO_PROYECTO | 0..* | concede | PERMISO | 1 | se concede mediante |
| 22 | USUARIO | 0..1 | concede | PERMISO_MIEMBRO_PROYECTO | 0..* | es concedida por |
| 23 | ARCHIVO_ALMACENADO | 0..* | pertenece a | PROYECTO | 0..1 | almacena |
| 24 | USUARIO | 0..1 | carga | ARCHIVO_ALMACENADO | 0..* | es cargado por |
| 25 | USUARIO | 0..1 | crea | SERVICIO_PLATAFORMA | 0..* | es creado por |
| 26 | SECRETO_SERVICIO | 0..* | protege | SERVICIO_PLATAFORMA | 1 | tiene secretos registrados en |
| 27 | COMPROBACION_SERVICIO | 0..* | comprueba | SERVICIO_PLATAFORMA | 1 | se comprueba mediante |
| 28 | USUARIO | 0..1 | realiza | COMPROBACION_SERVICIO | 0..* | es realizada por |
| 29 | VERSION_MODELO_UML | 0..* | pertenece a | MODELO_UML | 1 | tiene versiones registradas en |
| 30 | USUARIO | 0..1 | crea | VERSION_MODELO_UML | 0..* | es creada por |
| 31 | PAQUETE_UML | 0..* | pertenece a | MODELO_UML | 1 | contiene |
| 32 | USUARIO | 0..1 | actualiza | PAQUETE_UML | 0..* | es actualizado por |
| 33 | CLASE_UML | 0..* | pertenece a | MODELO_UML | 1 | contiene |
| 34 | USUARIO | 0..1 | crea | CLASE_UML | 0..* | es creada por |
| 35 | USUARIO | 0..1 | actualiza | CLASE_UML | 0..* | es actualizada por |
| 36 | USUARIO | 1 | abre | SESION_EDICION_MODELO | 0..* | es abierta por |
| 37 | CLASE_UML | 1 | tiene | ATRIBUTO_UML | 0..* | pertenece a |
| 38 | POSICION_RELACION_UML | 0..1 | ubica | RELACION_UML | 1 | tiene posicion registrada en |
| 39 | USUARIO | 0..1 | aplica | PROPUESTA_INTELIGENTE | 0..* | es aplicada por |
| 40 | CLASE_UML | 1 | tiene | OPERACION_UML | 0..* | pertenece a |
| 41 | OPERACION_UML | 1 | recibe | PARAMETRO_UML | 0..* | pertenece a |
| 42 | RELACION_UML | 0..* | pertenece a | MODELO_UML | 1 | contiene |
| 43 | RELACION_UML | 0..* | parte de | CLASE_UML | 1 | origina |
| 44 | RELACION_UML | 0..* | llega a | CLASE_UML | 1 | recibe |
| 45 | USUARIO | 0..1 | crea | RELACION_UML | 0..* | es creada por |
| 46 | USUARIO | 0..1 | actualiza | RELACION_UML | 0..* | es actualizada por |
| 47 | POSICION_CLASE_UML | 0..1 | ubica | CLASE_UML | 1 | tiene posicion registrada en |
| 48 | PREFERENCIA_DIAGRAMA | 0..* | personaliza | MODELO_UML | 1 | se personaliza mediante |
| 49 | USUARIO | 1 | establece | PREFERENCIA_DIAGRAMA | 0..* | pertenece a |
| 50 | SESION_EDICION_MODELO | 0..* | edita | MODELO_UML | 1 | tiene sesiones registradas en |
| 51 | PARTICIPANTE_SESION_MODELO | 0..* | pertenece a | SESION_EDICION_MODELO | 1 | tiene |
| 52 | USUARIO | 1 | participa mediante | PARTICIPANTE_SESION_MODELO | 0..* | representa a |
| 53 | CONFLICTO_EDICION | 0..* | contrasta el guardado | CAMBIO_MODELO | 0..1 | interviene como guardado en |
| 54 | CONFLICTO_EDICION | 0..* | contrasta el entrante | CAMBIO_MODELO | 0..1 | interviene como entrante en |
| 55 | CAMBIO_MODELO | 0..* | modifica | MODELO_UML | 1 | recibe |
| 56 | CAMBIO_MODELO | 0..* | ocurre durante | SESION_EDICION_MODELO | 0..1 | agrupa |
| 57 | USUARIO | 1 | realiza | CAMBIO_MODELO | 0..* | es realizado por |
| 58 | CONFLICTO_EDICION | 0..* | afecta a | MODELO_UML | 1 | tiene conflictos registrados en |
| 59 | USUARIO | 0..1 | resuelve | CONFLICTO_EDICION | 0..* | es resuelto por |
| 60 | ACTIVIDAD_PROYECTO | 0..* | registra actividad de | PROYECTO | 1 | tiene actividad registrada en |
| 61 | ACTIVIDAD_PROYECTO | 0..* | registra actividad de | MODELO_UML | 0..1 | tiene actividad registrada en |
| 62 | ACTIVIDAD_PROYECTO | 0..* | registra actividad de | VERSION_MODELO_UML | 0..1 | tiene actividad registrada en |
| 63 | USUARIO | 0..1 | realiza | ACTIVIDAD_PROYECTO | 0..* | es realizada por |
| 64 | PROPUESTA_INTELIGENTE | 0..1 | responde a | SOLICITUD_INTELIGENTE | 1 | produce |
| 65 | VALIDACION_MODELO | 0..* | evalua | MODELO_UML | 1 | se evalua mediante |
| 66 | VALIDACION_MODELO | 0..* | evalua | VERSION_MODELO_UML | 0..1 | se evalua mediante |
| 67 | USUARIO | 0..1 | solicita | VALIDACION_MODELO | 0..* | es solicitada por |
| 68 | VALIDACION_MODELO | 1 | detecta | HALLAZGO_VALIDACION_MODELO | 0..* | pertenece a |
| 69 | HALLAZGO_VALIDACION_MODELO | 0..* | se fundamenta en | REGLA_VALIDACION | 0..1 | fundamenta |
| 70 | USUARIO | 0..1 | revisa | PROPUESTA_INTELIGENTE | 0..* | es revisada por |
| 71 | PROPUESTA_INTELIGENTE | 0..* | se aplica en | VERSION_MODELO_UML | 0..1 | registra la aplicacion de |
| 72 | USUARIO | 0..1 | rechaza | PROPUESTA_INTELIGENTE | 0..* | es rechazada por |
| 73 | PROPUESTA_INTELIGENTE | 1 | plantea | AMBIGUEDAD_PROPUESTA | 0..* | pertenece a |
| 74 | SOLICITUD_INTELIGENTE | 0..* | utiliza | SERVICIO_PLATAFORMA | 0..1 | atiende |
| 75 | SOLICITUD_INTELIGENTE | 0..* | interpreta | ARCHIVO_ALMACENADO | 0..1 | es interpretado por |
| 76 | USUARIO | 0..1 | resuelve | AMBIGUEDAD_PROPUESTA | 0..* | es resuelta por |
| 77 | INTERCAMBIO_XMI | 0..* | utiliza como origen | VERSION_MODELO_UML | 0..1 | se utiliza como origen en |
| 78 | USUARIO | 1 | solicita | INTERCAMBIO_XMI | 0..* | es solicitado por |
| 79 | INTERCAMBIO_XMI | 0..* | lee | ARCHIVO_ALMACENADO | 0..1 | es leido por |
| 80 | PROPUESTA_INTELIGENTE | 1 | contiene | OPERACION_PROPUESTA | 0..* | pertenece a |
| 81 | DEPENDENCIA_OPERACION_PROPUESTA | 0..* | pertenece a | OPERACION_PROPUESTA | 1 | tiene dependencias registradas en |
| 82 | DEPENDENCIA_OPERACION_PROPUESTA | 0..* | requiere | OPERACION_PROPUESTA | 1 | es requerida mediante |
| 83 | AMBIGUEDAD_PROPUESTA | 0..* | afecta a | OPERACION_PROPUESTA | 0..1 | tiene ambiguedades registradas en |
| 84 | INTERCAMBIO_XMI | 0..* | intercambia | MODELO_UML | 1 | participa en |
| 85 | INTERCAMBIO_XMI | 0..* | produce | ARCHIVO_ALMACENADO | 0..1 | es producido por |
| 86 | INTERCAMBIO_XMI | 0..* | incorpora | VERSION_MODELO_UML | 0..1 | es incorporada por |
| 87 | CONFIGURACION_GENERACION_BACKEND | 1 | contiene | CLASE_CONFIGURACION_BACKEND | 0..* | pertenece a |
| 88 | CLASE_CONFIGURACION_BACKEND | 0..* | selecciona | CLASE_UML | 1 | se selecciona mediante |
| 89 | CONFIGURACION_GENERACION_BACKEND | 1 | contiene | MAPEO_TIPO_BACKEND | 0..* | pertenece a |
| 90 | CONFIGURACION_GENERACION_BACKEND | 1 | declara | DEPENDENCIA_BACKEND | 0..* | pertenece a |
| 91 | GENERACION_BACKEND | 0..* | utiliza | CONFIGURACION_GENERACION_BACKEND | 1 | se utiliza en |
| 92 | USUARIO | 1 | solicita | GENERACION_BACKEND | 0..* | es solicitada por |
| 93 | GENERACION_BACKEND | 0..* | produce | ARCHIVO_ALMACENADO | 0..1 | es producido por |
| 94 | GENERACION_BACKEND | 0..* | registra en | ARCHIVO_ALMACENADO | 0..1 | contiene el registro de |
| 95 | CONFIGURACION_GENERACION_BACKEND | 1 | contiene | MAPEO_PERSISTENCIA_BACKEND | 0..* | pertenece a |
| 96 | MAPEO_PERSISTENCIA_BACKEND | 0..* | mapea | ATRIBUTO_UML | 0..1 | se mapea mediante |
| 97 | MAPEO_PERSISTENCIA_BACKEND | 0..* | mapea | RELACION_UML | 0..1 | se mapea mediante |
| 98 | VALIDACION_BACKEND | 0..* | evalua | GENERACION_BACKEND | 1 | se evalua mediante |
| 99 | USUARIO | 0..1 | solicita | VALIDACION_BACKEND | 0..* | es solicitada por |
| 100 | VALIDACION_BACKEND | 0..* | informa en | ARCHIVO_ALMACENADO | 0..1 | contiene el informe de |
| 101 | VALIDACION_BACKEND | 1 | detecta | HALLAZGO_VALIDACION_BACKEND | 0..* | pertenece a |
| 102 | DESCARGA_ARTEFACTO | 0..* | obtiene el resultado de | GENERACION_BACKEND | 1 | tiene descargas registradas en |
| 103 | DESCARGA_ARTEFACTO | 0..* | obtiene | ARCHIVO_ALMACENADO | 1 | se obtiene mediante |
| 104 | USUARIO | 1 | realiza | DESCARGA_ARTEFACTO | 0..* | es realizada por |
| 105 | MODELO_UML | 0..1 | tiene como version actual | VERSION_MODELO_UML | 0..1 | es version actual de |
| 106 | VERSION_MODELO_UML | 0..* | deriva de | VERSION_MODELO_UML | 0..1 | origina |
| 107 | VERSION_MODELO_UML | 0..* | restaura | VERSION_MODELO_UML | 0..1 | es restaurada por |
| 108 | PAQUETE_UML | 0..* | pertenece a | PAQUETE_UML | 0..1 | contiene |
| 109 | CLASE_UML | 0..* | pertenece a | PAQUETE_UML | 0..1 | contiene |

Las clases intermedias son reales: no se dibuja además una asociación N:M directa. En las autorreferencias de VERSION_MODELO_UML, una relación «deriva de» apunta a la versión padre y «restaura» a la versión restaurada; en PAQUETE_UML, «pertenece a» apunta al paquete padre. Esos papeles se conservan en las notas SQL aunque ambas clases tengan el mismo nombre.
