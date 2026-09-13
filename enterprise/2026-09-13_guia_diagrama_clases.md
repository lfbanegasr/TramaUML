# Diagrama de clases de la base corregida — 2026-09-13

Derivado técnico de desarrollo. No sustituye los documentos del estudiante ni el enunciado del docente. La base de la plataforma colaborativa es distinta de las bases de negocios que la herramienta generará.

## Archivo que debes ejecutar

[VBScript completo](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/enterprise/2026-09-13_diagrama_clases_corregido.vbs>). Es autocontenido: no necesita PostgreSQL, Docker, Graphviz ni un archivo JSON para ejecutarse dentro de Enterprise Architect. El catálogo y las previsualizaciones acompañantes permiten comprobar su procedencia.

1. Abre tu proyecto de Enterprise Architect y selecciona un paquete padre en el navegador.
2. Abre la ventana **Scripting**. Dentro de un grupo de scripts normal, crea un **nuevo VBScript**; no un Workflow Script.
3. Abre el archivo entregado, copia TODO su contenido y reemplaza TODO el contenido de la plantilla del nuevo script. No lo agregues debajo de otro script ni de otro `Option Explicit`.
4. Guarda el script y ejecuta **Run Script**. Confirma la creación cuando aparezca el mensaje.
5. Se abrirá **00 Diagrama de clases COMPLETO - atributos y multiplicidades**. En el navegador también encontrarás las nueve vistas por módulos.

Este flujo utiliza la [ventana Scripting documentada por Sparx](https://imap.sparxsystems.com/enterprise_architect_user_guide/17.2/add-ins___scripting/scripts_tab.html). La ubicación del botón que abre Scripting depende de la versión y de la edición de EA; en tu versión usa la misma ventana en la que ejecutaste el script anterior. El archivo **no se importa como XMI** y ejecutarlo con doble clic en Windows solo comprueba los datos: no dibuja en EA.

## Contenido y presentación

El modelo crea 48 elementos UML **Class**, 507 atributos y 109 conectores UML **Association**, con ambas multiplicidades. No emplea estereotipos Table, notación de patas de gallo, herencias ni composiciones deducidas de una FK.

Las clases muestran únicamente el nombre de la tabla y los nombres de sus atributos. No muestran tipos, valores predeterminados, PK/FK, `{id}`, atributos `[0..1]`, notas ni restricciones. Los tipos SQL, NULL y claves se conservan en notas y valores etiquetados; no se borran del diccionario físico. En este modelo documental el tipo y los límites de multiplicidad del atributo se dejan vacíos para no depender de la configuración visual que tengas en EA.

Los nombres de conectores son verbos naturales: «pertenece a», «tiene», «solicita», «crea», «realiza», «detecta», «produce», etc. No se muestran los nombres técnicos de roles `+clase_uml_por_modelo`. El sentido de lectura y la lectura inversa están documentados en las notas de cada asociación y en [el listado de relaciones](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/enterprise/2026-09-13_relaciones_diagrama_clases.md>). **Un verbo no es un método ejecutable ni convierte una relación en composición.** No se añade una flecha de navegabilidad que las FK no demuestren.

Los paquetes solo organizan el navegador: no hay figuras de paquetes en el dibujo ni prefijos como `01 Acceso::ROL`. El script desactiva `HighlightImports` (Show Namespace), según el [API de Diagram](https://sparxsystems.com/enterprise_architect_user_guide/17.2/add-ins___scripting/diagram2.html), y oculta los nombres totalmente cualificados mediante `NoFullScope`.

Las posiciones están precalculadas según las asociaciones, con cajas compactas, Arial 9 y ancho según el atributo/nombre más largo. Los conectores de EA usan líneas ortogonales redondeadas: `LineStyle=9`, documentado en [DiagramLink](https://www.sparxsystems.com/enterprise_architect_user_guide/17.2/add-ins___scripting/diagramlinks.html). EA calcula sus rutas y etiquetas; no se promete que los 109 conectores del completo queden sin cruces ni ajustes de etiquetas. Usa las vistas modulares para trabajar y mueve puntos/etiquetas cuando EA los superponga. Las previsualizaciones SVG son un render independiente, **no capturas de ejecución real de EA**.

## Cómo leer ROL — ROL_PERMISO — PERMISO

`ROL (1) —tiene— (0..*) ROL_PERMISO (0..*) —asigna— (1) PERMISO`

Un rol tiene cero o varias asignaciones de permisos; cada asignación pertenece a exactamente un rol y asigna exactamente un permiso. Un permiso puede aparecer en cero o varias asignaciones para distintos roles. A través de ROL_PERMISO se lee «un rol tiene muchos permisos» y, en sentido inverso, «un permiso pertenece a muchos roles».

Se conserva ROL_PERMISO con sus atributos reales: id_rol, id_permiso y concedido_en. No se agrega una segunda asociación directa ROL—PERMISO: duplicaría la relación que ya representa la tabla intermedia. Lo mismo se aplica a USUARIO_ROL y las demás clases de asociación.

La multiplicidad se escribe en el extremo de la clase contada: `1` junto a ROL significa que cada ROL_PERMISO apunta a un rol. `0..*` junto a ROL_PERMISO significa que un rol puede tener ninguna o muchas asignaciones. No se cambia a `1..*` por intuición: la FK no exige que todos los roles tengan permisos.

## Fidelidad a la base corregida

Fuente principal: [diccionario vigente](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/docs/2026-09-13_diccionario_base_datos_vigente.md>), extraído del PostgreSQL después de aplicar [la migración 18](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/database/2026-09-13_18_correcciones_desarrollo.sql>). Contrato del backend: [guía de persistencia corregida](<D:/1 Universidad/1SEMESTRE-2-2026/04_INGENIERIA_SOFTWARE_I/Primer parcial/Implementacion/docs/2026-09-13_guia_persistencia_corregida.md>). La línea base es el esquema 16 más la migración 18.

Docker no estaba activo al generar esta entrega; **no se volvió a consultar ni a modificar la instalación**. Se contrastaron todos los atributos y las 119 FK del diccionario vigente con el catálogo estructural anterior: coinciden, tal como documenta la corrección. Sí se actualizaron las notas de las restricciones: 219 PK/UQ/CHECK más seis restricciones de triggers diferidos; el CHECK del identificador UML y los resultados de validación corresponden a la migración 18.

Las 119 FK físicas se representan mediante 109 asociaciones: diez referencias simples/compuestas redundantes verifican además que los objetos pertenecen al mismo modelo/proyecto. Sus dos definiciones SQL quedan juntas en las notas del mismo conector. No se pierden relaciones ni se dibujan dos veces por controles de ámbito.

La multiplicidad del extremo padre se calcula con la nulabilidad de la referencia; el máximo del extremo hijo se calcula con PK/UQ que cubren la FK completa. Las restricciones condicionales y las FK compuestas se mantienen en notas. Se muestran todos los registros posibles, incluidos históricos: por ejemplo, un servicio puede tener varios secretos retirados aunque solo uno esté vigente. No se aplica la unicidad de un índice parcial a toda la clase.

No se crean clases extra para vistas SQL, funciones, triggers o índices. Las 48 clases documentan las tablas existentes; este diagrama no evalúa de nuevo si una tabla debe eliminarse ni cambia el esquema.

## Validación y seguridad

Se completaron 3.883 comprobaciones sobre el VBScript/catálogo y diez SVG: nombres, tipos/NULL de origen, PK/FK, restricciones actuales, cobertura exacta de las 119 FK, nombres naturales y multiplicidades. Windows compiló/ejecutó la comprobación de datos del VBScript. La prueba de creación con un repositorio simulado comprobó 48 clases, 507 atributos, 109 asociaciones, diez diagramas y cajas sin solapamiento, así como ausencia de PK/FK/tipos/límites de atributos visibles y preservación de originales.

**No se ejecutó ni se revisó visualmente en tu proyecto real de Enterprise Architect.** El modelo generado se verifica dentro del propio script antes de anunciar éxito; todavía debes revisar las rutas y etiquetas que dibuje tu versión de EA.

Se crea el paquete nuevo **Diagrama de clases completo - 2026-09-13 relaciones naturales**. No se elimina ni se sobrescribe el anterior. Si ejecutas de nuevo bajo el mismo padre, se detiene para evitar duplicados: selecciona otro padre o cambia NOMBRE_PAQUETE. Si EA falla a mitad de una ejecución puede quedar un paquete nuevo parcial; los originales se conservan.

Comprueba con un ejemplo propio al abrirlo: ¿por qué CLASE_UML puede no tener PAQUETE_UML?, ¿por qué una SOLICITUD_INTELIGENTE tiene como máximo una PROPUESTA_INTELIGENTE?, ¿por qué POSICION_CLASE_UML no es una relación uno a muchos? Contrasta las respuestas con las FK, NULL y unicidad del diccionario, no solo con la etiqueta del conector.
