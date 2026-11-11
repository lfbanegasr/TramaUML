--
-- PostgreSQL database dump
--

-- Catalogos en espanol: sin usuarios humanos ni credenciales.

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: permiso; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('33f4a471-f304-4815-bd0c-c37a6d688023', 'CREAR_PROYECTOS', 'PROYECTO', 'CREAR', 'Crear proyectos.', 'GLOBAL', '2026-09-13 02:57:33.226147+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('8eabf7ab-ecfb-4a74-8481-f281d0c0a2c5', 'GESTIONAR_USUARIOS', 'ACCESO', 'GESTIONAR_USUARIOS', 'Gestionar cuentas y estados de usuario.', 'GLOBAL', '2026-09-13 02:57:33.225693+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('6390272c-3f19-4574-b2e4-714b4c5584e3', 'GESTIONAR_ROLES', 'ACCESO', 'GESTIONAR_ROLES', 'Gestionar roles y permisos.', 'GLOBAL', '2026-09-13 02:57:33.226015+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('f36f27be-9fe8-49c3-9173-7e1883f3d86a', 'CONFIGURAR_SERVICIOS', 'ACCESO', 'CONFIGURAR_SERVICIOS', 'Configurar proveedores de IA, voz e imagen.', 'GLOBAL', '2026-09-13 02:57:33.226094+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('f4bd3641-0a95-40d8-b843-356bebf92f87', 'DESCARGAR_ARTEFACTO', 'GENERACION', 'DESCARGAR_ARTEFACTO', 'Descargar artefactos generados y validados.', 'PROYECTO', '2026-09-13 02:57:33.227019+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('1c1dde76-9f39-4ecf-b58c-aa731d344ba7', 'VALIDAR_BACKEND', 'GENERACION', 'VALIDAR_BACKEND', 'Compilar y validar un backend generado.', 'PROYECTO', '2026-09-13 02:57:33.226982+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('bda3882b-5898-4764-8cc3-09dd6eac8c35', 'GENERAR_BACKEND', 'GENERACION', 'GENERAR_BACKEND', 'Generar un backend Spring Boot.', 'PROYECTO', '2026-09-13 02:57:33.226945+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('6e92e7cf-c52a-4062-a9df-0deca27b5c10', 'CONFIGURAR_BACKEND', 'GENERACION', 'CONFIGURAR_BACKEND', 'Configurar la transformacion a Spring Boot.', 'PROYECTO', '2026-09-13 02:57:33.226906+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('86892fc9-0a22-49e5-bbf9-77162be1d727', 'EXPORTAR_XMI', 'INTEROPERABILIDAD', 'EXPORTAR_XMI', 'Exportar modelos XMI.', 'PROYECTO', '2026-09-13 02:57:33.226867+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('1440d453-1d12-4c5f-b4c3-9c55647b2c98', 'IMPORTAR_XMI', 'INTEROPERABILIDAD', 'IMPORTAR_XMI', 'Importar modelos XMI.', 'PROYECTO', '2026-09-13 02:57:33.226831+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', 'REVISAR_PROPUESTA', 'INTELIGENCIA', 'REVISAR_PROPUESTA', 'Revisar, corregir, aceptar o rechazar propuestas.', 'PROYECTO', '2026-09-13 02:57:33.226792+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('719827db-36f1-4ce4-bfae-60d8d53b642a', 'ASISTIR_IMAGEN', 'INTELIGENCIA', 'USAR_IMAGEN', 'Crear propuestas desde imagen.', 'PROYECTO', '2026-09-13 02:57:33.226749+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('2c693e04-04b3-45f0-8c7c-132c43b28b6d', 'ASISTIR_VOZ', 'INTELIGENCIA', 'USAR_VOZ', 'Crear propuestas desde voz.', 'PROYECTO', '2026-09-13 02:57:33.226712+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('93210a03-db2d-48f8-ac6b-132a6a47ba57', 'ASISTIR_TEXTO', 'INTELIGENCIA', 'USAR_TEXTO', 'Crear propuestas desde texto.', 'PROYECTO', '2026-09-13 02:57:33.226675+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', 'RESOLVER_CONFLICTO', 'COLABORACION', 'RESOLVER_CONFLICTO', 'Resolver conflictos de edicion.', 'PROYECTO', '2026-09-13 02:57:33.226638+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('56f5ee6a-5bc5-4077-af6a-3432406c62cd', 'COLABORAR', 'COLABORACION', 'EDITAR_EN_VIVO', 'Participar en edicion simultanea.', 'PROYECTO', '2026-09-13 02:57:33.2266+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('fa4009bb-f5df-44b2-ac92-28d99041592e', 'RESTAURAR_MODELO', 'CALIDAD', 'RESTAURAR_VERSION', 'Restaurar una version como una version nueva.', 'PROYECTO', '2026-09-13 02:57:33.226563+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('09f257c4-d7eb-457c-a48a-70432dc88d2b', 'VER_HISTORIAL_MODELO', 'CALIDAD', 'VER_HISTORIAL', 'Consultar y comparar versiones.', 'PROYECTO', '2026-09-13 02:57:33.226525+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('76c10f64-773c-476e-8dc7-fa2440e57de9', 'VALIDAR_MODELO', 'CALIDAD', 'VALIDAR_MODELO', 'Ejecutar reglas de validacion UML.', 'PROYECTO', '2026-09-13 02:57:33.226486+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('524d9497-9375-4b49-b37b-af8493d0a41a', 'ORGANIZAR_MODELO', 'MODELO', 'ORGANIZAR', 'Organizar visualmente el diagrama.', 'PROYECTO', '2026-09-13 02:57:33.226449+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('f08ba837-62dd-4ccd-b867-9d15b8f2f37b', 'VER_ACTIVIDAD', 'COLABORACION', 'VER_ACTIVIDAD', 'Consultar la actividad del proyecto.', 'PROYECTO', '2026-09-13 02:57:33.226337+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('fa394fae-0b5b-4ce6-90cd-9b622d8ef52d', 'ASIGNAR_ROLES_PROYECTO', 'PROYECTO', 'ASIGNAR_ROLES', 'Asignar roles y permisos en un proyecto.', 'PROYECTO', '2026-09-13 02:57:33.226299+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('3c716b10-92c8-4e20-9351-713963e06fa8', 'GESTIONAR_MIEMBROS', 'PROYECTO', 'GESTIONAR_MIEMBROS', 'Incorporar, suspender y retirar miembros.', 'PROYECTO', '2026-09-13 02:57:33.226261+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('e201365e-0455-444a-ad76-7b58f663781a', 'GESTIONAR_PROYECTOS', 'PROYECTO', 'GESTIONAR', 'Modificar, archivar y reactivar un proyecto.', 'PROYECTO', '2026-09-13 02:57:33.226224+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', 'VER_MODELO', 'MODELO', 'VER', 'Abrir y consultar el modelo UML.', 'PROYECTO', '2026-09-13 02:57:33.226374+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', 'EDITAR_MODELO', 'MODELO', 'EDICION', 'Crear y modificar elementos UML.', 'PROYECTO', '2026-09-13 02:57:33.226414+00');
INSERT INTO uml_colaborativo.permiso (id, codigo, modulo, accion, descripcion, ambito, creado_en) VALUES ('0e1c5568-4f6a-428d-9825-1fa764dc9c0e', 'VER_PROYECTOS', 'PROYECTO', 'VER', 'Consultar proyectos autorizados.', 'AMBOS', '2026-09-13 02:57:33.226187+00');


--
-- Data for Name: regla_validacion; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('0611caf1-668b-461f-bffc-6571926478fa', 'UML_FIRMA_OPERACION_UNICA', 'Firma de operacion unica', 'No se admiten firmas duplicadas dentro de una clase.', 'ERROR', 'UML_OPERATION', true, 'operacion.firma.unica', '2026-09-13 02:57:33.241395+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('7e861453-d8a1-4781-810e-c6b8360ae9c6', 'UML_IDENTIFICADOR_PERSISTENTE_OBLIGATORIO', 'Identificador persistente obligatorio', 'Toda clase persistente debe declarar al menos un atributo identificador.', 'ERROR', 'CLASE_UML', true, 'persistente.id.obligatorio', '2026-09-13 02:57:33.24127+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('b8a2875a-ac3b-4052-b734-2d8202d315fa', 'UML_NOMBRE_CLASE_UNICO', 'Nombre de clase unico', 'No puede haber dos clases activas con el mismo nombre dentro del paquete.', 'ERROR', 'CLASE_UML', true, 'clase.nombre.unico', '2026-09-13 02:57:33.2412+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('b01619c3-e252-4681-92f2-806ea0af6c96', 'UML_NOMBRE_CLASE_OBLIGATORIO', 'Nombre de clase obligatorio', 'Toda clase activa debe tener un nombre valido.', 'ERROR', 'CLASE_UML', true, 'clase.nombre.obligatorio', '2026-09-13 02:57:33.24098+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('b17ce393-9e48-4645-ac90-31df06059f7d', 'UML_TIPO_ATRIBUTO_OBLIGATORIO', 'Tipo de atributo obligatorio', 'Todo atributo debe declarar un tipo UML.', 'ERROR', 'ATRIBUTO_UML', true, 'atributo.tipo.obligatorio', '2026-09-13 02:57:33.241238+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('824f137d-0bbd-4996-a103-fb7788949a1d', 'UML_CARACTERISTICA_XMI_NO_COMPATIBLE', 'Caracteristica XMI no compatible', 'Una propiedad importada o exportada no se representa completamente.', 'ADVERTENCIA', 'MODELO_UML', true, 'xmi.caracteristica.no_compatible', '2026-09-13 02:57:33.241426+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('50e08559-1760-4e87-8815-f93c3411f346', 'UML_GENERALIZACION_SIN_CICLOS', 'Generalizacion sin ciclos', 'La jerarquia de generalizacion no puede contener ciclos.', 'ERROR', 'RELACION_UML', true, 'generalizacion.sin_ciclos', '2026-09-13 02:57:33.241364+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('c9dd0a6c-c7f1-4e46-b065-72ac35275b35', 'UML_CARDINALIDAD_VALIDA', 'Cardinalidad valida', 'Los minimos y maximos de una relacion deben ser coherentes.', 'ERROR', 'RELACION_UML', true, 'relacion.cardinalidad.valida', '2026-09-13 02:57:33.241333+00');
INSERT INTO uml_colaborativo.regla_validacion (id, codigo, nombre, descripcion, severidad, aplica_a, esta_activo, clave_implementacion, creado_en) VALUES ('06900322-d006-4bb7-a101-64945f432564', 'UML_EXTREMOS_RELACION_VALIDOS', 'Extremos de relacion validos', 'Los extremos deben existir y pertenecer al mismo modelo.', 'ERROR', 'RELACION_UML', true, 'relacion.extremos.validos', '2026-09-13 02:57:33.2413+00');


--
-- Data for Name: rol; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'PROPIETARIO_PROYECTO', 'Propietario de proyecto', 'Administra el proyecto, sus miembros y sus artefactos.', 'PROYECTO', true, 'ACTIVO', '2026-09-13 02:57:33.223019+00', '2026-09-13 02:57:33.223019+00');
INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', 'EDITOR_PROYECTO', 'Ingeniero editor', 'Edita, valida e intercambia modelos autorizados.', 'PROYECTO', true, 'ACTIVO', '2026-09-13 02:57:33.223076+00', '2026-09-13 02:57:33.223076+00');
INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', 'LECTOR_PROYECTO', 'Ingeniero visualizador', 'Consulta el proyecto y su historial sin modificarlo.', 'PROYECTO', true, 'ACTIVO', '2026-09-13 02:57:33.223125+00', '2026-09-13 02:57:33.223125+00');
INSERT INTO uml_colaborativo.rol (id, codigo, nombre, descripcion, ambito, es_del_sistema, estado, creado_en, actualizado_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'SUPERADMINISTRADOR', 'Superadministrador', 'Administracion global de la plataforma.', 'GLOBAL', true, 'ACTIVO', '2026-09-13 02:57:33.222131+00', '2026-09-13 02:57:33.222131+00');


--
-- Data for Name: rol_permiso; Type: TABLE DATA; Schema: uml_colaborativo; Owner: -
--

INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '8eabf7ab-ecfb-4a74-8481-f281d0c0a2c5', '2026-09-13 02:57:33.228935+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '6390272c-3f19-4574-b2e4-714b4c5584e3', '2026-09-13 02:57:33.229209+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'f36f27be-9fe8-49c3-9173-7e1883f3d86a', '2026-09-13 02:57:33.229244+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '33f4a471-f304-4815-bd0c-c37a6d688023', '2026-09-13 02:57:33.229269+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.229292+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'e201365e-0455-444a-ad76-7b58f663781a', '2026-09-13 02:57:33.229315+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '3c716b10-92c8-4e20-9351-713963e06fa8', '2026-09-13 02:57:33.229337+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'fa394fae-0b5b-4ce6-90cd-9b622d8ef52d', '2026-09-13 02:57:33.229362+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.229383+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.229404+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', '2026-09-13 02:57:33.229426+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '524d9497-9375-4b49-b37b-af8493d0a41a', '2026-09-13 02:57:33.229448+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '76c10f64-773c-476e-8dc7-fa2440e57de9', '2026-09-13 02:57:33.229469+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.229551+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'fa4009bb-f5df-44b2-ac92-28d99041592e', '2026-09-13 02:57:33.229582+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '56f5ee6a-5bc5-4077-af6a-3432406c62cd', '2026-09-13 02:57:33.229606+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', '2026-09-13 02:57:33.229629+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '93210a03-db2d-48f8-ac6b-132a6a47ba57', '2026-09-13 02:57:33.229652+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '2c693e04-04b3-45f0-8c7c-132c43b28b6d', '2026-09-13 02:57:33.229673+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '719827db-36f1-4ce4-bfae-60d8d53b642a', '2026-09-13 02:57:33.229696+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', '2026-09-13 02:57:33.229721+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '1440d453-1d12-4c5f-b4c3-9c55647b2c98', '2026-09-13 02:57:33.229743+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '86892fc9-0a22-49e5-bbf9-77162be1d727', '2026-09-13 02:57:33.229765+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '6e92e7cf-c52a-4062-a9df-0deca27b5c10', '2026-09-13 02:57:33.229789+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'bda3882b-5898-4764-8cc3-09dd6eac8c35', '2026-09-13 02:57:33.22981+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', '1c1dde76-9f39-4ecf-b58c-aa731d344ba7', '2026-09-13 02:57:33.229832+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('d9b7d3c1-15e1-48f2-8464-935775a648c6', 'f4bd3641-0a95-40d8-b843-356bebf92f87', '2026-09-13 02:57:33.229856+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.233869+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'e201365e-0455-444a-ad76-7b58f663781a', '2026-09-13 02:57:33.233917+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '3c716b10-92c8-4e20-9351-713963e06fa8', '2026-09-13 02:57:33.233945+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'fa394fae-0b5b-4ce6-90cd-9b622d8ef52d', '2026-09-13 02:57:33.233977+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.234004+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.234028+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', '2026-09-13 02:57:33.234053+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '524d9497-9375-4b49-b37b-af8493d0a41a', '2026-09-13 02:57:33.234078+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '76c10f64-773c-476e-8dc7-fa2440e57de9', '2026-09-13 02:57:33.234105+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.234131+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'fa4009bb-f5df-44b2-ac92-28d99041592e', '2026-09-13 02:57:33.234155+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '56f5ee6a-5bc5-4077-af6a-3432406c62cd', '2026-09-13 02:57:33.234179+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', '2026-09-13 02:57:33.234203+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '93210a03-db2d-48f8-ac6b-132a6a47ba57', '2026-09-13 02:57:33.234227+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '2c693e04-04b3-45f0-8c7c-132c43b28b6d', '2026-09-13 02:57:33.234251+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '719827db-36f1-4ce4-bfae-60d8d53b642a', '2026-09-13 02:57:33.234276+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', '2026-09-13 02:57:33.2343+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '1440d453-1d12-4c5f-b4c3-9c55647b2c98', '2026-09-13 02:57:33.234325+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '86892fc9-0a22-49e5-bbf9-77162be1d727', '2026-09-13 02:57:33.234351+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '6e92e7cf-c52a-4062-a9df-0deca27b5c10', '2026-09-13 02:57:33.234376+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'bda3882b-5898-4764-8cc3-09dd6eac8c35', '2026-09-13 02:57:33.2344+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', '1c1dde76-9f39-4ecf-b58c-aa731d344ba7', '2026-09-13 02:57:33.234428+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('a0203f64-6c30-4813-8f6d-96b89f4d9b5f', 'f4bd3641-0a95-40d8-b843-356bebf92f87', '2026-09-13 02:57:33.234454+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.236634+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.236682+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.236708+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '14e2cc3d-4a6e-4634-86fc-a8ac88d43f54', '2026-09-13 02:57:33.236734+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '524d9497-9375-4b49-b37b-af8493d0a41a', '2026-09-13 02:57:33.236756+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '76c10f64-773c-476e-8dc7-fa2440e57de9', '2026-09-13 02:57:33.236779+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.236802+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '56f5ee6a-5bc5-4077-af6a-3432406c62cd', '2026-09-13 02:57:33.236826+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '2ec2c8e0-82df-4dcb-a8dd-93f016d438ce', '2026-09-13 02:57:33.236852+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '93210a03-db2d-48f8-ac6b-132a6a47ba57', '2026-09-13 02:57:33.236874+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '2c693e04-04b3-45f0-8c7c-132c43b28b6d', '2026-09-13 02:57:33.236896+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '719827db-36f1-4ce4-bfae-60d8d53b642a', '2026-09-13 02:57:33.236919+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '0839e42c-e9c1-42d5-a57a-5c3e4ad229b3', '2026-09-13 02:57:33.236941+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '1440d453-1d12-4c5f-b4c3-9c55647b2c98', '2026-09-13 02:57:33.236963+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('4fcb507a-e0a6-4efd-aabd-4e332e1dfe37', '86892fc9-0a22-49e5-bbf9-77162be1d727', '2026-09-13 02:57:33.236985+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', '0e1c5568-4f6a-428d-9825-1fa764dc9c0e', '2026-09-13 02:57:33.238932+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', 'f08ba837-62dd-4ccd-b867-9d15b8f2f37b', '2026-09-13 02:57:33.238982+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', 'a0fdd410-a05c-4a0e-9d50-0a71c83dd47e', '2026-09-13 02:57:33.239007+00');
INSERT INTO uml_colaborativo.rol_permiso (id_rol, id_permiso, concedido_en) VALUES ('3258f5e8-096d-4f17-9829-610e4784ee57', '09f257c4-d7eb-457c-a48a-70432dc88d2b', '2026-09-13 02:57:33.239037+00');


--
-- PostgreSQL database dump complete
--

SET search_path TO uml_colaborativo, public;
