' Enterprise Architect: UML de clases persistentes, base corregida 2026-09-13.
' AUTOCONTENIDO: pegar TODO en Scripting de EA como VBScript.
' 48 tablas / 507 columnas / 119 FK agrupadas en 109 asociaciones UML.
' Origen: diccionario vigente posterior a migracion 18; no registros ni secretos.
' Muestra nombres, atributos y relaciones; tipos/NULL/PK/FK quedan en notas y tags.
' No modifica la base ni los modelos anteriores. Crea un paquete NUEVO.
' Disposicion precalculada segun relaciones, cajas compactas; no requiere Graphviz.
' Los paquetes organizan el navegador: NO se dibujan ni califican los nombres.
' Direccion de lectura en notas/tags, NO se infiere navegabilidad o composicion.
Option Explicit

Const NOMBRE_PAQUETE = "Diagrama de clases completo - 2026-09-13 relaciones naturales"
Const TOTAL_TABLAS = 48
Const TOTAL_ATRIBUTOS = 507
Const TOTAL_RELACIONES = 109
Const TOTAL_RESTRICCIONES = 225

Dim gTablas, gAtributos, gRelaciones, gRestricciones, gClases, gPaquetes
Dim gRepositorio, gRaiz, gDiagramaGeneral, gAbrirDiagrama
Dim gPosiciones, gOrigenFK
Dim anfitrion
Set anfitrion = Nothing
gAbrirDiagrama = True

Sub InicializarDatos()
    Set gTablas = CreateObject("Scripting.Dictionary")
    Set gAtributos = CreateObject("Scripting.Dictionary")
    Set gRelaciones = CreateObject("Scripting.Dictionary")
    Set gRestricciones = CreateObject("Scripting.Dictionary")
    Set gClases = CreateObject("Scripting.Dictionary")
    Set gPaquetes = CreateObject("Scripting.Dictionary")
    Set gPosiciones = CreateObject("Scripting.Dictionary")
    Set gOrigenFK = CreateObject("Scripting.Dictionary")
    CargarPosiciones
    CargarTablas
    CargarAtributos_Acceso
    CargarAtributos_Proyectos
    CargarAtributos_Servicios_archivos
    CargarAtributos_Modelo_UML
    CargarAtributos_Colaboracion
    CargarAtributos_Validacion_UML
    CargarAtributos_Asistente_IA
    CargarAtributos_Intercambio_XMI
    CargarAtributos_Generacion_backend
    CargarRestricciones
    CargarRelaciones
    ValidarDatos
End Sub

Sub AgregarTabla(nombre, modulo, notas)
    gTablas.Add nombre, Array(modulo, notas)
End Sub

Sub AgregarAtributo(tabla, nombre, tipoUML, tipoSQL, admiteNulo, esPK, esFK, identificaSolo, predeterminado, notas)
    gAtributos.Add tabla & "." & nombre, Array(tabla, nombre, tipoUML, tipoSQL, admiteNulo, esPK, esFK, identificaSolo, predeterminado, notas)
End Sub

Sub AgregarRestriccion(tabla, nombre, definicion)
    gRestricciones.Add tabla & "." & nombre, Array(tabla, nombre, definicion)
End Sub

Sub AgregarRelacion(origen, destino, nombre, cardinalidadOrigen, cardinalidadDestino, notas, tablaFK)
    gOrigenFK.Add gRelaciones.Count, tablaFK
    gRelaciones.Add gRelaciones.Count, Array(origen, destino, nombre, cardinalidadOrigen, cardinalidadDestino, notas)
End Sub

Sub Posicionar(modulo, tabla, izquierda, arriba, ancho, alto)
    gPosiciones.Add modulo & "." & tabla, Array(izquierda, arriba, ancho, alto)
End Sub

Sub ValidarDatos()
    Dim clave, dato
    If gTablas.Count <> TOTAL_TABLAS Or gAtributos.Count <> TOTAL_ATRIBUTOS Or gRelaciones.Count <> TOTAL_RELACIONES Then
        Err.Raise vbObjectError + 501, "ValidarDatos", "Cantidad de tablas, atributos o asociaciones incorrecta."
    End If
    If gRestricciones.Count <> TOTAL_RESTRICCIONES Then Err.Raise vbObjectError + 520, "ValidarDatos", "Cantidad de restricciones incorrecta."
    For Each clave In gAtributos.Keys
        dato = gAtributos(clave)
        If Not gTablas.Exists(dato(0)) Then Err.Raise vbObjectError + 502, "ValidarDatos", "Atributo sin tabla: " & clave
    Next
    For Each clave In gRelaciones.Keys
        dato = gRelaciones(clave)
        If Not gTablas.Exists(dato(0)) Or Not gTablas.Exists(dato(1)) Then
            Err.Raise vbObjectError + 503, "ValidarDatos", "Asociacion sin tabla."
        End If
    Next
End Sub

Sub Guardar(objeto)
    If Not objeto.Update() Then
        Err.Raise vbObjectError + 504, "Guardar", objeto.GetLastError()
    End If
End Sub

Sub Etiqueta(coleccion, nombre, valor)
    Dim etiquetaNueva
    Set etiquetaNueva = coleccion.AddNew(nombre, valor)
    Guardar etiquetaNueva
End Sub

Function BuscarPaquete(padre, nombre)
    Dim indice, paquete
    Set BuscarPaquete = Nothing
    padre.Packages.Refresh
    For indice = 0 To padre.Packages.Count - 1
        Set paquete = padre.Packages.GetAt(indice)
        If paquete.Name = nombre Then
            Set BuscarPaquete = paquete
            Exit Function
        End If
    Next
End Function

Sub GenerarEn(repositorio, seleccionado)
    Dim existente, clave, dato, paquete, elemento, atributo, restriccion, conector
    Dim origen, destino, modulo, etiquetaEstereotipo
    InicializarDatos
    Set gRepositorio = repositorio
    Set existente = BuscarPaquete(seleccionado, NOMBRE_PAQUETE)
    If Not existente Is Nothing Then
        Err.Raise vbObjectError + 505, "GenerarEn", "Ya existe este paquete. No se sobrescribira; seleccione otro padre o cambie NOMBRE_PAQUETE."
    End If
    Set gRaiz = seleccionado.Packages.AddNew(NOMBRE_PAQUETE, "")
    gRaiz.Notes = "Base corregida por migracion 18. Documentacion: 48 clases persistentes, no servicios ni pantallas. Relaciones con verbos naturales y ambas multiplicidades; referencias redundantes de ambito agrupadas sin perder FK. Los nombres de paquetes no se muestran. Notas y tags conservan los tipos, PK/FK, NULL y SQL. Fuente: docs/2026-09-13_diccionario_base_datos_vigente.md. No modifica PostgreSQL. Las tablas historicas se muestran sin filtros de filas activas."
    Guardar gRaiz
    Etiqueta gRaiz.Element.TaggedValues, "esquema_postgresql", "uml_colaborativo"
    For Each clave In gTablas.Keys
        dato = gTablas(clave)
        modulo = dato(0)
        If Not gPaquetes.Exists(modulo) Then
            Set paquete = gRaiz.Packages.AddNew(modulo, "")
            Guardar paquete
            gPaquetes.Add modulo, paquete
        End If
        Set paquete = gPaquetes(modulo)
        Set elemento = paquete.Elements.AddNew(UCase(clave), "Class")
        elemento.Alias = clave
        elemento.Stereotype = ""
        elemento.Gentype = "Java"
        elemento.Notes = dato(1)
        Guardar elemento
        Etiqueta elemento.TaggedValues, "tabla_postgresql", clave
        gClases.Add clave, elemento
    Next
    For Each clave In gAtributos.Keys
        dato = gAtributos(clave)
        Set elemento = gClases(dato(0))
        Set atributo = elemento.Attributes.AddNew(dato(1), "")
        atributo.Visibility = "Private"
        ' La multiplicidad SQL se conserva como metadato, no como sufijo del atributo.
        atributo.LowerBound = ""
        atributo.UpperBound = ""
        atributo.IsID = False
        ' El valor SQL se documenta, no se presenta como una expresion Java.
        atributo.Default = ""
        atributo.Notes = "Tipo PostgreSQL: " & dato(3) & ". Valor predeterminado SQL: " & dato(8) & ". " & dato(9)
        atributo.StereotypeEx = ""
        Guardar atributo
        Etiqueta atributo.TaggedValues, "tipo_postgresql", dato(3)
        Etiqueta atributo.TaggedValues, "admite_nulo", CStr(dato(4))
        Etiqueta atributo.TaggedValues, "clave_primaria", CStr(dato(5))
        Etiqueta atributo.TaggedValues, "clave_foranea", CStr(dato(6))
        Etiqueta atributo.TaggedValues, "tipo_documentacion_uml", dato(2)
    Next
    For Each clave In gRestricciones.Keys
        dato = gRestricciones(clave)
        Set elemento = gClases(dato(0))
        Set restriccion = elemento.Constraints.AddNew(dato(1), "Invariant")
        restriccion.Notes = dato(2)
        Guardar restriccion
    Next
    For Each clave In gRelaciones.Keys
        dato = gRelaciones(clave)
        Set origen = gClases(dato(0))
        Set destino = gClases(dato(1))
        Set conector = origen.Connectors.AddNew(dato(2), "Association")
        conector.ClientID = origen.ElementID
        conector.SupplierID = destino.ElementID
        conector.Notes = dato(5)
        conector.ClientEnd.Cardinality = dato(3)
        conector.SupplierEnd.Cardinality = dato(4)
        ' Sin roles tecnicos "+clase_por_modelo": solo verbo y multiplicidades.
        conector.SupplierEnd.Role = ""
        conector.ClientEnd.Role = ""
        conector.Direction = "Unspecified"
        conector.ClientEnd.Aggregation = 0
        conector.SupplierEnd.Aggregation = 0
        conector.ClientEnd.Navigable = "Unspecified"
        conector.SupplierEnd.Navigable = "Unspecified"
        Guardar conector
        Guardar conector.ClientEnd
        Guardar conector.SupplierEnd
        Etiqueta conector.TaggedValues, "lectura", UCase(dato(0)) & " " & dato(2) & " " & UCase(dato(1))
        Etiqueta conector.TaggedValues, "tabla_origen_fk", gOrigenFK(clave)
        gRelaciones(clave) = Array(dato(0), dato(1), dato(2), dato(3), dato(4), dato(5), conector.ConnectorID)
    Next
    gRaiz.Packages.Refresh
    CrearDiagramas
    VerificarModeloCreado
    gRepositorio.RefreshModelView gRaiz.PackageID
    If gAbrirDiagrama Then gRepositorio.OpenDiagram gDiagramaGeneral.DiagramID
End Sub

Sub CrearDiagramas()
    Dim visibles, clave, dato, modulo, paquete, diagrama
    Set visibles = CreateObject("Scripting.Dictionary")
    For Each clave In gTablas.Keys
        visibles.Add clave, True
    Next
    Set gDiagramaGeneral = NuevoDiagrama(gRaiz, "00 Diagrama de clases COMPLETO - atributos y multiplicidades", visibles, True, "")
    For Each modulo In gPaquetes.Keys
        Set visibles = CreateObject("Scripting.Dictionary")
        For Each clave In gTablas.Keys
            dato = gTablas(clave)
            If dato(0) = modulo Then visibles.Add clave, True
        Next
        ' Incluir las clases referenciadas por este modulo, sin duplicarlas.
        ' Las asociaciones entrantes desde otros modulos estan en el completo.
        For Each clave In gRelaciones.Keys
            dato = gRelaciones(clave)
            If gTablas(gOrigenFK(clave))(0) = modulo Then
                If Not visibles.Exists(dato(0)) Then visibles.Add dato(0), True
                If Not visibles.Exists(dato(1)) Then visibles.Add dato(1), True
            End If
        Next
        Set paquete = gPaquetes(modulo)
        Set diagrama = NuevoDiagrama(paquete, modulo, visibles, True, modulo)
    Next
End Sub

Sub ConfigurarEstiloDiagrama(diagrama, parametro, valor)
    Dim partes, parte, nuevoEstilo
    partes = Split(diagrama.StyleEx, ";")
    nuevoEstilo = ""
    For Each parte In partes
        If parte <> "" Then
            If StrComp(Split(parte, "=")(0), parametro, vbTextCompare) <> 0 Then
                nuevoEstilo = nuevoEstilo & parte & ";"
            End If
        End If
    Next
    diagrama.StyleEx = nuevoEstilo & parametro & "=" & valor & ";"
End Sub

Function ObtenerEnlace(diagrama, idConector)
    Dim i, enlaceExistente
    diagrama.DiagramLinks.Refresh
    For i = 0 To diagrama.DiagramLinks.Count - 1
        Set enlaceExistente = diagrama.DiagramLinks.GetAt(i)
        If enlaceExistente.ConnectorID = idConector Then
            Set ObtenerEnlace = enlaceExistente
            Exit Function
        End If
    Next
    Set ObtenerEnlace = diagrama.DiagramLinks.AddNew("", "")
End Function

Function NuevoDiagrama(paquete, nombre, visibles, mostrarAtributos, modulo)
    Dim diagrama, indice, columna, izquierda, arriba, alto, altoFila
    Dim clave, dato, elemento, objetoDiagrama, enlace, columnas, orden, local, posicion, j
    Set diagrama = paquete.Diagrams.AddNew(nombre, "Logical")
    diagrama.Notes = "Diagrama UML de clases persistentes. Cada clase muestra todos sus atributos. Asociaciones UML con multiplicidad en ambos extremos. Sin inferir operaciones, herencia ni composicion. Clases grises: contexto de otro modulo."
    diagrama.HighlightImports = False
    diagrama.ShowPackageContents = False
    diagrama.ShowDetails = 0
    diagrama.ShowPrivate = True
    diagrama.ShowPublic = True
    diagrama.ShowProtected = True
    ConfigurarEstiloDiagrama diagrama, "TConnectorNotation", "UML 2.1"
    ConfigurarEstiloDiagrama diagrama, "VisibleAttributeDetail", "0"
    ConfigurarEstiloDiagrama diagrama, "NoFullScope", "1"
    ConfigurarEstiloDiagrama diagrama, "HideQuals", "1"
    ConfigurarEstiloDiagrama diagrama, "AdvancedFeatureProps", "0"
    ConfigurarEstiloDiagrama diagrama, "AdvancedConnectorProps", "0"
    ConfigurarEstiloDiagrama diagrama, "HideConnStereotype", "1"
    ConfigurarEstiloDiagrama diagrama, "SuppConnectorLabels", "0"
    ConfigurarEstiloDiagrama diagrama, "AttPkg", "1"
    diagrama.Orientation = "L"
    Guardar diagrama
    columnas = 4
    If modulo <> "" Then columnas = 3
    Set orden = CreateObject("Scripting.Dictionary")
    ' Las clases del modulo preceden a las vecinas.
    For Each clave In gTablas.Keys
        If visibles.Exists(clave) Then
            local = True
            If modulo <> "" Then local = (gTablas(clave)(0) = modulo)
            If local Then orden.Add clave, True
        End If
    Next
    For Each clave In visibles.Keys
        If Not orden.Exists(clave) Then orden.Add clave, True
    Next
    indice = 0
    arriba = 60
    altoFila = 0
    For Each clave In orden.Keys
        Set elemento = gClases(clave)
        elemento.Attributes.Refresh
        posicion = gPosiciones(modulo & "." & clave)
        izquierda = posicion(0)
        arriba = posicion(1)
        alto = posicion(3)
        Set objetoDiagrama = diagrama.DiagramObjects.AddNew("", "")
        objetoDiagrama.ElementID = elemento.ElementID
        objetoDiagrama.Left = izquierda
        objetoDiagrama.Right = izquierda + posicion(2)
        ' EA utiliza coordenadas cartesianas: Top y Bottom NEGATIVOS.
        objetoDiagrama.Top = -arriba
        objetoDiagrama.Bottom = -(arriba + alto)
        objetoDiagrama.FontName = "Arial"
        objetoDiagrama.FontSize = "9"
        objetoDiagrama.ShowPrivateAttributes = True
        objetoDiagrama.ShowPublicAttributes = True
        objetoDiagrama.ShowProtectedAttributes = True
        objetoDiagrama.ShowPackageAttributes = True
        objetoDiagrama.ShowPrivateOperations = False
        objetoDiagrama.ShowPublicOperations = False
        objetoDiagrama.ShowProtectedOperations = False
        objetoDiagrama.ShowPackageOperations = False
        objetoDiagrama.ShowConstraints = False
        objetoDiagrama.ShowTags = False
        objetoDiagrama.ShowNotes = False
        objetoDiagrama.ElementDisplayMode = 1
        If modulo <> "" Then
            If gTablas(clave)(0) <> modulo Then objetoDiagrama.BackgroundColor = RGB(238, 238, 238)
        End If
        Guardar objetoDiagrama
        indice = indice + 1
    Next
    For Each clave In gRelaciones.Keys
        dato = gRelaciones(clave)
        If visibles.Exists(dato(0)) And visibles.Exists(dato(1)) Then
            Set enlace = ObtenerEnlace(diagrama, dato(6))
            enlace.ConnectorID = dato(6)
            enlace.IsHidden = False
            enlace.HiddenLabels = False
            enlace.LineStyle = 9
            Guardar enlace
        End If
    Next
    diagrama.DiagramObjects.Refresh
    diagrama.DiagramLinks.Refresh
    Guardar diagrama
    gRepositorio.ReloadDiagram diagrama.DiagramID
    Set NuevoDiagrama = diagrama
End Function

Sub VerificarModeloCreado()
    Dim clave, dato, elemento, esperado, otra, conector, objeto, i
    For Each clave In gTablas.Keys
        Set elemento = gClases(clave)
        elemento.Attributes.Refresh
        esperado = 0
        For Each otra In gAtributos.Keys
            If gAtributos(otra)(0) = clave Then esperado = esperado + 1
        Next
        If elemento.Type <> "Class" Or elemento.Attributes.Count <> esperado Then
            Err.Raise vbObjectError + 510, "VerificarModeloCreado", "Clase o atributos incorrectos: " & clave
        End If
    Next
    For Each clave In gRelaciones.Keys
        dato = gRelaciones(clave)
        Set conector = gRepositorio.GetConnectorByID(dato(6))
        If conector.Name <> dato(2) Or conector.ClientEnd.Role <> "" Or conector.SupplierEnd.Role <> "" Then
            Err.Raise vbObjectError + 521, "VerificarModeloCreado", "Nombre/roles de la relacion incorrectos."
        End If
        If conector.Type <> "Association" Then Err.Raise vbObjectError + 511, "VerificarModeloCreado", "Relacion distinta de asociacion UML."
        If conector.ClientEnd.Cardinality <> dato(3) Or conector.SupplierEnd.Cardinality <> dato(4) Then
            Err.Raise vbObjectError + 512, "VerificarModeloCreado", "Multiplicidad incorrecta: " & dato(0) & " / " & dato(1)
        End If
    Next
    gDiagramaGeneral.DiagramObjects.Refresh
    gDiagramaGeneral.DiagramLinks.Refresh
    If gDiagramaGeneral.DiagramObjects.Count <> TOTAL_TABLAS Or gDiagramaGeneral.DiagramLinks.Count <> TOTAL_RELACIONES Then
        Err.Raise vbObjectError + 513, "VerificarModeloCreado", "El diagrama completo no contiene todas las clases/asociaciones."
    End If
    For i = 0 To gDiagramaGeneral.DiagramObjects.Count - 1
        Set objeto = gDiagramaGeneral.DiagramObjects.GetAt(i)
        If Not objeto.ShowPrivateAttributes Or objeto.Top >= 0 Or objeto.Bottom >= objeto.Top Then
            Err.Raise vbObjectError + 514, "VerificarModeloCreado", "Presentacion de clase incorrecta."
        End If
    Next
End Sub

Sub Main()
    Dim seleccionado, numeroError, mensajeError
    Set seleccionado = Nothing
    Err.Clear
    On Error Resume Next
    Set gRepositorio = Eval("Repository")
    Set seleccionado = gRepositorio.GetTreeSelectedPackage()
    numeroError = Err.Number
    mensajeError = Err.Description
    On Error GoTo 0
    If numeroError <> 0 Or seleccionado Is Nothing Then
        MsgBox "Abra un proyecto de Enterprise Architect y seleccione un paquete en el navegador.", vbExclamation
        Exit Sub
    End If
    If MsgBox("Crear 48 clases y 10 diagramas en un paquete nuevo? No se eliminara ni sobrescribira contenido existente.", vbYesNo + vbQuestion) <> vbYes Then Exit Sub
    On Error Resume Next
    GenerarEn gRepositorio, seleccionado
    numeroError = Err.Number
    mensajeError = Err.Description
    On Error GoTo 0
    If numeroError <> 0 Then
        MsgBox "No se completo la generacion: " & mensajeError & vbCrLf & "Puede quedar un paquete parcial nuevo; los paquetes originales no se modifican.", vbCritical
    Else
        MsgBox "Listo: 48 clases, 507 atributos, 109 asociaciones con nombres naturales y 10 diagramas.", vbInformation
    End If
End Sub

' En cscript solo comprobar datos/sintaxis; nunca conectarse a un proyecto ajeno.
On Error Resume Next
Set anfitrion = Eval("WScript")
On Error GoTo 0
Err.Clear
If anfitrion Is Nothing Then
    Main
Else
    InicializarDatos
    anfitrion.Echo "VERIFICADO: " & gTablas.Count & " clases, " & gAtributos.Count & " atributos, " & gRelaciones.Count & " asociaciones."
    anfitrion.Echo "Para crear el modelo, pegue este contenido en Scripting de Enterprise Architect y ejecutelo con un paquete seleccionado."
End If

Sub CargarTablas()
    AgregarTabla "usuario", "01 Acceso", "Tabla PostgreSQL uml_colaborativo.usuario. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "sesion_autenticacion", "01 Acceso", "Tabla PostgreSQL uml_colaborativo.sesion_autenticacion. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "rol", "01 Acceso", "Tabla PostgreSQL uml_colaborativo.rol. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "permiso", "01 Acceso", "Tabla PostgreSQL uml_colaborativo.permiso. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "usuario_rol", "01 Acceso", "Tabla PostgreSQL uml_colaborativo.usuario_rol. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "rol_permiso", "01 Acceso", "Tabla PostgreSQL uml_colaborativo.rol_permiso. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "proyecto", "02 Proyectos", "Tabla PostgreSQL uml_colaborativo.proyecto. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "miembro_proyecto", "02 Proyectos", "Tabla PostgreSQL uml_colaborativo.miembro_proyecto. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "rol_miembro_proyecto", "02 Proyectos", "Tabla PostgreSQL uml_colaborativo.rol_miembro_proyecto. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "permiso_miembro_proyecto", "02 Proyectos", "Tabla PostgreSQL uml_colaborativo.permiso_miembro_proyecto. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "actividad_proyecto", "02 Proyectos", "Tabla PostgreSQL uml_colaborativo.actividad_proyecto. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "archivo_almacenado", "03 Servicios y archivos", "Tabla PostgreSQL uml_colaborativo.archivo_almacenado. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "servicio_plataforma", "03 Servicios y archivos", "Tabla PostgreSQL uml_colaborativo.servicio_plataforma. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "secreto_servicio", "03 Servicios y archivos", "Tabla PostgreSQL uml_colaborativo.secreto_servicio. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "comprobacion_servicio", "03 Servicios y archivos", "Tabla PostgreSQL uml_colaborativo.comprobacion_servicio. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "modelo_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.modelo_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "version_modelo_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.version_modelo_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "paquete_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.paquete_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "clase_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.clase_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "atributo_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.atributo_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "operacion_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.operacion_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "parametro_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.parametro_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "relacion_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.relacion_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "posicion_clase_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.posicion_clase_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "posicion_relacion_uml", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.posicion_relacion_uml. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "preferencia_diagrama", "04 Modelo UML", "Tabla PostgreSQL uml_colaborativo.preferencia_diagrama. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "sesion_edicion_modelo", "05 Colaboracion", "Tabla PostgreSQL uml_colaborativo.sesion_edicion_modelo. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "participante_sesion_modelo", "05 Colaboracion", "Tabla PostgreSQL uml_colaborativo.participante_sesion_modelo. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "cambio_modelo", "05 Colaboracion", "Tabla PostgreSQL uml_colaborativo.cambio_modelo. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "conflicto_edicion", "05 Colaboracion", "Tabla PostgreSQL uml_colaborativo.conflicto_edicion. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "regla_validacion", "06 Validacion UML", "Tabla PostgreSQL uml_colaborativo.regla_validacion. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "validacion_modelo", "06 Validacion UML", "Tabla PostgreSQL uml_colaborativo.validacion_modelo. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "hallazgo_validacion_modelo", "06 Validacion UML", "Tabla PostgreSQL uml_colaborativo.hallazgo_validacion_modelo. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "solicitud_inteligente", "07 Asistente IA", "Tabla PostgreSQL uml_colaborativo.solicitud_inteligente. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "propuesta_inteligente", "07 Asistente IA", "Tabla PostgreSQL uml_colaborativo.propuesta_inteligente. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "operacion_propuesta", "07 Asistente IA", "Tabla PostgreSQL uml_colaborativo.operacion_propuesta. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "dependencia_operacion_propuesta", "07 Asistente IA", "Tabla PostgreSQL uml_colaborativo.dependencia_operacion_propuesta. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "ambiguedad_propuesta", "07 Asistente IA", "Tabla PostgreSQL uml_colaborativo.ambiguedad_propuesta. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "intercambio_xmi", "08 Intercambio XMI", "Tabla PostgreSQL uml_colaborativo.intercambio_xmi. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "configuracion_generacion_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.configuracion_generacion_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "clase_configuracion_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.clase_configuracion_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "mapeo_tipo_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.mapeo_tipo_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "dependencia_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.dependencia_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "mapeo_persistencia_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.mapeo_persistencia_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "generacion_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.generacion_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "validacion_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.validacion_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "hallazgo_validacion_backend", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.hallazgo_validacion_backend. Modelo de documentacion derivado, no una clase de control o interfaz."
    AgregarTabla "descarga_artefacto", "09 Generacion backend", "Tabla PostgreSQL uml_colaborativo.descarga_artefacto. Modelo de documentacion derivado, no una clase de control o interfaz."
End Sub

Sub CargarAtributos_Acceso()
    AgregarAtributo "usuario", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "usuario", "nombre_usuario", "Texto", "citext", False, False, False, False, "", ""
    AgregarAtributo "usuario", "correo_electronico", "Texto", "citext", False, False, False, False, "", ""
    AgregarAtributo "usuario", "nombre_completo", "Texto", "character varying(160)", False, False, False, False, "", ""
    AgregarAtributo "usuario", "hash_contrasena", "Texto", "text", False, False, False, False, "", ""
    AgregarAtributo "usuario", "estado", "Texto", "character varying(64)", False, False, False, False, "'ACTIVO'::character varying", ""
    AgregarAtributo "usuario", "intentos_fallidos_acceso", "Entero", "smallint", False, False, False, False, "0", ""
    AgregarAtributo "usuario", "bloqueado_hasta", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "usuario", "ultimo_acceso_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "usuario", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "usuario", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "usuario", "desactivado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "sesion_autenticacion", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "sesion_autenticacion", "id_usuario", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "sesion_autenticacion", "hash_token", "Binario", "bytea", False, False, False, False, "", ""
    AgregarAtributo "sesion_autenticacion", "hash_token_renovacion", "Binario", "bytea", True, False, False, False, "", ""
    AgregarAtributo "sesion_autenticacion", "direccion_ip", "Texto", "inet", True, False, False, False, "", ""
    AgregarAtributo "sesion_autenticacion", "agente_cliente", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "sesion_autenticacion", "emitido_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "sesion_autenticacion", "expira_en", "FechaHora", "timestamp with time zone", False, False, False, False, "", ""
    AgregarAtributo "sesion_autenticacion", "revocado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "sesion_autenticacion", "motivo_revocacion", "Texto", "character varying(200)", True, False, False, False, "", ""
    AgregarAtributo "rol", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "rol", "codigo", "Texto", "citext", False, False, False, False, "", ""
    AgregarAtributo "rol", "nombre", "Texto", "character varying(120)", False, False, False, False, "", ""
    AgregarAtributo "rol", "descripcion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "rol", "ambito", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "rol", "es_del_sistema", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "rol", "estado", "Texto", "character varying(64)", False, False, False, False, "'ACTIVO'::character varying", ""
    AgregarAtributo "rol", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "rol", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "permiso", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "permiso", "codigo", "Texto", "citext", False, False, False, False, "", ""
    AgregarAtributo "permiso", "modulo", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "permiso", "accion", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "permiso", "descripcion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "permiso", "ambito", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "permiso", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "usuario_rol", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "usuario_rol", "id_usuario", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "usuario_rol", "id_rol", "UUID", "uuid", False, False, True, False, "", " Clave foranea: rol."
    AgregarAtributo "usuario_rol", "asignado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "usuario_rol", "asignado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "usuario_rol", "revocado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "rol_permiso", "id_rol", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: rol."
    AgregarAtributo "rol_permiso", "id_permiso", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: permiso."
    AgregarAtributo "rol_permiso", "concedido_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
End Sub

Sub CargarAtributos_Proyectos()
    AgregarAtributo "proyecto", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "proyecto", "codigo", "Texto", "citext", False, False, False, False, "", ""
    AgregarAtributo "proyecto", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "proyecto", "descripcion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "proyecto", "id_usuario_propietario", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "proyecto", "estado", "Texto", "character varying(64)", False, False, False, False, "'ACTIVO'::character varying", ""
    AgregarAtributo "proyecto", "visibilidad", "Texto", "character varying(64)", False, False, False, False, "'PRIVADO'::character varying", ""
    AgregarAtributo "proyecto", "opciones", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "proyecto", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "proyecto", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "proyecto", "archivado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "miembro_proyecto", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "miembro_proyecto", "id_proyecto", "UUID", "uuid", False, False, True, False, "", " Clave foranea: proyecto."
    AgregarAtributo "miembro_proyecto", "id_usuario", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "miembro_proyecto", "estado", "Texto", "character varying(64)", False, False, False, False, "'ACTIVO'::character varying", ""
    AgregarAtributo "miembro_proyecto", "incorporado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "miembro_proyecto", "suspendido_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "miembro_proyecto", "retirado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "miembro_proyecto", "motivo_estado", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "miembro_proyecto", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "miembro_proyecto", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "rol_miembro_proyecto", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "rol_miembro_proyecto", "id_miembro_proyecto", "UUID", "uuid", False, False, True, False, "", " Clave foranea: miembro_proyecto."
    AgregarAtributo "rol_miembro_proyecto", "id_rol", "UUID", "uuid", False, False, True, False, "", " Clave foranea: rol."
    AgregarAtributo "rol_miembro_proyecto", "asignado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "rol_miembro_proyecto", "asignado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "rol_miembro_proyecto", "revocado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "permiso_miembro_proyecto", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "permiso_miembro_proyecto", "id_miembro_proyecto", "UUID", "uuid", False, False, True, False, "", " Clave foranea: miembro_proyecto."
    AgregarAtributo "permiso_miembro_proyecto", "id_permiso", "UUID", "uuid", False, False, True, False, "", " Clave foranea: permiso."
    AgregarAtributo "permiso_miembro_proyecto", "decision", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "permiso_miembro_proyecto", "asignado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "permiso_miembro_proyecto", "asignado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "permiso_miembro_proyecto", "revocado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "actividad_proyecto", "id", "EnteroLargo", "bigint", False, True, False, True, "", "Clave primaria."
    AgregarAtributo "actividad_proyecto", "id_proyecto", "UUID", "uuid", False, False, True, False, "", " Clave foranea: proyecto,modelo_uml."
    AgregarAtributo "actividad_proyecto", "id_modelo", "UUID", "uuid", True, False, True, False, "", " Clave foranea: modelo_uml,version_modelo_uml."
    AgregarAtributo "actividad_proyecto", "id_version_modelo", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "actividad_proyecto", "id_usuario_actor", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "actividad_proyecto", "categoria", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "actividad_proyecto", "accion", "Texto", "character varying(80)", False, False, False, False, "", ""
    AgregarAtributo "actividad_proyecto", "tipo_entidad", "Texto", "character varying(64)", True, False, False, False, "", ""
    AgregarAtributo "actividad_proyecto", "id_entidad", "UUID", "uuid", True, False, False, False, "", ""
    AgregarAtributo "actividad_proyecto", "valor_anterior", "JSON", "jsonb", True, False, False, False, "", ""
    AgregarAtributo "actividad_proyecto", "valor_posterior", "JSON", "jsonb", True, False, False, False, "", ""
    AgregarAtributo "actividad_proyecto", "detalle", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "actividad_proyecto", "ocurrido_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
End Sub

Sub CargarAtributos_Servicios_archivos()
    AgregarAtributo "archivo_almacenado", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "archivo_almacenado", "id_proyecto", "UUID", "uuid", True, False, True, False, "", " Clave foranea: proyecto."
    AgregarAtributo "archivo_almacenado", "cargado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "archivo_almacenado", "categoria", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "archivo_almacenado", "proveedor_almacenamiento", "Texto", "character varying(40)", False, False, False, False, "", ""
    AgregarAtributo "archivo_almacenado", "clave_almacenamiento", "Texto", "text", False, False, False, False, "", ""
    AgregarAtributo "archivo_almacenado", "nombre_original", "Texto", "character varying(255)", False, False, False, False, "", ""
    AgregarAtributo "archivo_almacenado", "tipo_contenido", "Texto", "character varying(150)", False, False, False, False, "", ""
    AgregarAtributo "archivo_almacenado", "tamano_bytes", "EnteroLargo", "bigint", False, False, False, False, "", ""
    AgregarAtributo "archivo_almacenado", "sha256", "Texto", "character(64)", False, False, False, False, "", ""
    AgregarAtributo "archivo_almacenado", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "archivo_almacenado", "eliminado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "servicio_plataforma", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "servicio_plataforma", "nombre", "Texto", "character varying(140)", False, False, False, False, "", ""
    AgregarAtributo "servicio_plataforma", "proveedor", "Texto", "character varying(100)", False, False, False, False, "", ""
    AgregarAtributo "servicio_plataforma", "tipo_servicio", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "servicio_plataforma", "url_servicio", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "servicio_plataforma", "configuracion", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "servicio_plataforma", "estado", "Texto", "character varying(64)", False, False, False, False, "'SIN_VERIFICAR'::character varying", ""
    AgregarAtributo "servicio_plataforma", "creado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "servicio_plataforma", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "servicio_plataforma", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "secreto_servicio", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "secreto_servicio", "id_servicio", "UUID", "uuid", False, False, True, False, "", " Clave foranea: servicio_plataforma."
    AgregarAtributo "secreto_servicio", "contenido_cifrado", "Binario", "bytea", False, False, False, False, "", ""
    AgregarAtributo "secreto_servicio", "algoritmo_cifrado", "Texto", "character varying(40)", False, False, False, False, "", ""
    AgregarAtributo "secreto_servicio", "version_clave", "Texto", "character varying(40)", False, False, False, False, "", ""
    AgregarAtributo "secreto_servicio", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "secreto_servicio", "rotado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "secreto_servicio", "retirado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "comprobacion_servicio", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "comprobacion_servicio", "id_servicio", "UUID", "uuid", False, False, True, False, "", " Clave foranea: servicio_plataforma."
    AgregarAtributo "comprobacion_servicio", "estado", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "comprobacion_servicio", "latencia_ms", "Entero", "integer", True, False, False, False, "", ""
    AgregarAtributo "comprobacion_servicio", "detalle", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "comprobacion_servicio", "comprobado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "comprobacion_servicio", "comprobado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
End Sub

Sub CargarAtributos_Modelo_UML()
    AgregarAtributo "modelo_uml", "id", "UUID", "uuid", False, True, True, True, "gen_random_uuid()", "Clave primaria. Clave foranea: version_modelo_uml."
    AgregarAtributo "modelo_uml", "id_proyecto", "UUID", "uuid", False, False, True, False, "", " Clave foranea: proyecto."
    AgregarAtributo "modelo_uml", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "modelo_uml", "descripcion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "modelo_uml", "estado", "Texto", "character varying(64)", False, False, False, False, "'BORRADOR'::character varying", ""
    AgregarAtributo "modelo_uml", "revision_actual", "EnteroLargo", "bigint", False, False, False, False, "0", ""
    AgregarAtributo "modelo_uml", "id_version_actual", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "modelo_uml", "creado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "modelo_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "modelo_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "modelo_uml", "archivado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "version_modelo_uml", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "version_modelo_uml", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,version_modelo_uml."
    AgregarAtributo "version_modelo_uml", "numero_version", "Entero", "integer", False, False, False, False, "", ""
    AgregarAtributo "version_modelo_uml", "revision", "EnteroLargo", "bigint", False, False, False, False, "", ""
    AgregarAtributo "version_modelo_uml", "id_version_padre", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "version_modelo_uml", "id_version_restaurada", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "version_modelo_uml", "instantanea", "JSON", "jsonb", False, False, False, False, "", ""
    AgregarAtributo "version_modelo_uml", "hash_instantanea_sha256", "Texto", "character(64)", False, False, False, False, "", ""
    AgregarAtributo "version_modelo_uml", "estado_validacion", "Texto", "character varying(64)", False, False, False, False, "'SIN_VALIDAR'::character varying", ""
    AgregarAtributo "version_modelo_uml", "resumen_cambio", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "version_modelo_uml", "creado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "version_modelo_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "paquete_uml", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "paquete_uml", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,paquete_uml."
    AgregarAtributo "paquete_uml", "id_paquete_padre", "UUID", "uuid", True, False, True, False, "", " Clave foranea: paquete_uml."
    AgregarAtributo "paquete_uml", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "paquete_uml", "descripcion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "paquete_uml", "id_externo_xmi", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "paquete_uml", "version_fila", "EnteroLargo", "bigint", False, False, False, False, "1", ""
    AgregarAtributo "paquete_uml", "creado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "paquete_uml", "actualizado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "paquete_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "paquete_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "paquete_uml", "eliminado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "clase_uml", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "clase_uml", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,paquete_uml."
    AgregarAtributo "clase_uml", "id_paquete", "UUID", "uuid", True, False, True, False, "", " Clave foranea: paquete_uml."
    AgregarAtributo "clase_uml", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "clase_uml", "descripcion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "clase_uml", "estereotipo", "Texto", "character varying(100)", True, False, False, False, "", ""
    AgregarAtributo "clase_uml", "visibilidad", "Texto", "character varying(64)", False, False, False, False, "'PUBLICO'::character varying", ""
    AgregarAtributo "clase_uml", "es_abstracto", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "clase_uml", "es_persistente", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "clase_uml", "id_externo_xmi", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "clase_uml", "version_fila", "EnteroLargo", "bigint", False, False, False, False, "1", ""
    AgregarAtributo "clase_uml", "creado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "clase_uml", "actualizado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "clase_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "clase_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "clase_uml", "eliminado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "atributo_uml", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "atributo_uml", "id_clase", "UUID", "uuid", False, False, True, False, "", " Clave foranea: clase_uml."
    AgregarAtributo "atributo_uml", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "atributo_uml", "tipo_uml", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "atributo_uml", "visibilidad", "Texto", "character varying(64)", False, False, False, False, "'PRIVADO'::character varying", ""
    AgregarAtributo "atributo_uml", "valor_predeterminado", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "atributo_uml", "multiplicidad_minima", "Entero", "integer", False, False, False, False, "1", ""
    AgregarAtributo "atributo_uml", "multiplicidad_maxima", "Entero", "integer", True, False, False, False, "1", ""
    AgregarAtributo "atributo_uml", "es_identificador", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "atributo_uml", "es_unico", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "atributo_uml", "es_estatico", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "atributo_uml", "restricciones_json", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "atributo_uml", "orden", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "atributo_uml", "id_externo_xmi", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "atributo_uml", "version_fila", "EnteroLargo", "bigint", False, False, False, False, "1", ""
    AgregarAtributo "atributo_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "atributo_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "atributo_uml", "eliminado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "operacion_uml", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "operacion_uml", "id_clase", "UUID", "uuid", False, False, True, False, "", " Clave foranea: clase_uml."
    AgregarAtributo "operacion_uml", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "operacion_uml", "tipo_retorno", "Texto", "character varying(180)", True, False, False, False, "", ""
    AgregarAtributo "operacion_uml", "visibilidad", "Texto", "character varying(64)", False, False, False, False, "'PUBLICO'::character varying", ""
    AgregarAtributo "operacion_uml", "es_estatico", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "operacion_uml", "es_abstracto", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "operacion_uml", "orden", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "operacion_uml", "id_externo_xmi", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "operacion_uml", "version_fila", "EnteroLargo", "bigint", False, False, False, False, "1", ""
    AgregarAtributo "operacion_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "operacion_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "operacion_uml", "eliminado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "parametro_uml", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "parametro_uml", "id_operacion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: operacion_uml."
    AgregarAtributo "parametro_uml", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "parametro_uml", "tipo_uml", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "parametro_uml", "direccion", "Texto", "character varying(64)", False, False, False, False, "'ENTRADA'::character varying", ""
    AgregarAtributo "parametro_uml", "es_obligatorio", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "parametro_uml", "valor_predeterminado", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "parametro_uml", "orden", "Entero", "integer", False, False, False, False, "", ""
    AgregarAtributo "parametro_uml", "id_externo_xmi", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "parametro_uml", "version_fila", "EnteroLargo", "bigint", False, False, False, False, "1", ""
    AgregarAtributo "parametro_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "parametro_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "parametro_uml", "eliminado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "relacion_uml", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,clase_uml."
    AgregarAtributo "relacion_uml", "id_clase_origen", "UUID", "uuid", False, False, True, False, "", " Clave foranea: clase_uml."
    AgregarAtributo "relacion_uml", "id_clase_destino", "UUID", "uuid", False, False, True, False, "", " Clave foranea: clase_uml."
    AgregarAtributo "relacion_uml", "nombre", "Texto", "character varying(180)", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "tipo_relacion", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "direccion", "Texto", "character varying(64)", False, False, False, False, "'SIN_DIRECCION'::character varying", ""
    AgregarAtributo "relacion_uml", "rol_origen", "Texto", "character varying(180)", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "rol_destino", "Texto", "character varying(180)", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "minimo_origen", "Entero", "integer", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "maximo_origen", "Entero", "integer", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "minimo_destino", "Entero", "integer", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "maximo_destino", "Entero", "integer", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "origen_navegable", "Booleano", "boolean", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "destino_navegable", "Booleano", "boolean", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "id_externo_xmi", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "relacion_uml", "version_fila", "EnteroLargo", "bigint", False, False, False, False, "1", ""
    AgregarAtributo "relacion_uml", "creado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "relacion_uml", "actualizado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "relacion_uml", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "relacion_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "relacion_uml", "eliminado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "posicion_clase_uml", "id_clase", "UUID", "uuid", False, True, True, True, "", "Clave primaria. Clave foranea: clase_uml."
    AgregarAtributo "posicion_clase_uml", "x", "Decimal", "numeric(12,3)", False, False, False, False, "", ""
    AgregarAtributo "posicion_clase_uml", "y", "Decimal", "numeric(12,3)", False, False, False, False, "", ""
    AgregarAtributo "posicion_clase_uml", "ancho", "Decimal", "numeric(12,3)", False, False, False, False, "", ""
    AgregarAtributo "posicion_clase_uml", "alto", "Decimal", "numeric(12,3)", False, False, False, False, "", ""
    AgregarAtributo "posicion_clase_uml", "orden_capa", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "posicion_clase_uml", "estilo", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "posicion_clase_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "posicion_relacion_uml", "id_relacion", "UUID", "uuid", False, True, True, True, "", "Clave primaria. Clave foranea: relacion_uml."
    AgregarAtributo "posicion_relacion_uml", "puntos_intermedios", "JSON", "jsonb", False, False, False, False, "'[]'::jsonb", ""
    AgregarAtributo "posicion_relacion_uml", "etiqueta_x", "Decimal", "numeric(12,3)", True, False, False, False, "", ""
    AgregarAtributo "posicion_relacion_uml", "etiqueta_y", "Decimal", "numeric(12,3)", True, False, False, False, "", ""
    AgregarAtributo "posicion_relacion_uml", "estilo", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "posicion_relacion_uml", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "preferencia_diagrama", "id_modelo", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: modelo_uml."
    AgregarAtributo "preferencia_diagrama", "id_usuario", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: usuario."
    AgregarAtributo "preferencia_diagrama", "escala", "Decimal", "numeric(8,4)", False, False, False, False, "1", ""
    AgregarAtributo "preferencia_diagrama", "desplazamiento_x", "Decimal", "numeric(12,3)", False, False, False, False, "0", ""
    AgregarAtributo "preferencia_diagrama", "desplazamiento_y", "Decimal", "numeric(12,3)", False, False, False, False, "0", ""
    AgregarAtributo "preferencia_diagrama", "mostrar_cuadricula", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "preferencia_diagrama", "ajustar_cuadricula", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "preferencia_diagrama", "preferencias", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "preferencia_diagrama", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
End Sub

Sub CargarAtributos_Colaboracion()
    AgregarAtributo "sesion_edicion_modelo", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "sesion_edicion_modelo", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml."
    AgregarAtributo "sesion_edicion_modelo", "abierto_por", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "sesion_edicion_modelo", "modo", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "sesion_edicion_modelo", "estado", "Texto", "character varying(64)", False, False, False, False, "'ACTIVO'::character varying", ""
    AgregarAtributo "sesion_edicion_modelo", "revision_base", "EnteroLargo", "bigint", False, False, False, False, "", ""
    AgregarAtributo "sesion_edicion_modelo", "abierto_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "sesion_edicion_modelo", "ultima_actividad_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "sesion_edicion_modelo", "cerrado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "participante_sesion_modelo", "id_sesion_edicion", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: sesion_edicion_modelo."
    AgregarAtributo "participante_sesion_modelo", "id_usuario", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: usuario."
    AgregarAtributo "participante_sesion_modelo", "modo_participante", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "participante_sesion_modelo", "estado_presencia", "Texto", "character varying(64)", False, False, False, False, "'EN_LINEA'::character varying", ""
    AgregarAtributo "participante_sesion_modelo", "tipo_entidad_enfocada", "Texto", "character varying(40)", True, False, False, False, "", ""
    AgregarAtributo "participante_sesion_modelo", "id_entidad_enfocada", "UUID", "uuid", True, False, False, False, "", ""
    AgregarAtributo "participante_sesion_modelo", "incorporado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "participante_sesion_modelo", "ultima_presencia_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "participante_sesion_modelo", "salida_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "cambio_modelo", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,sesion_edicion_modelo."
    AgregarAtributo "cambio_modelo", "id_sesion_edicion", "UUID", "uuid", True, False, True, False, "", " Clave foranea: sesion_edicion_modelo."
    AgregarAtributo "cambio_modelo", "id_usuario_actor", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "cambio_modelo", "id_cambio_cliente", "UUID", "uuid", False, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "revision_base", "EnteroLargo", "bigint", False, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "revision_aplicada", "EnteroLargo", "bigint", True, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "tipo_operacion", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "tipo_entidad", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "id_entidad", "UUID", "uuid", True, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "parche", "JSON", "jsonb", False, False, False, False, "", ""
    AgregarAtributo "cambio_modelo", "estado", "Texto", "character varying(64)", False, False, False, False, "'PENDIENTE'::character varying", ""
    AgregarAtributo "cambio_modelo", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "cambio_modelo", "aplicado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "conflicto_edicion", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,cambio_modelo."
    AgregarAtributo "conflicto_edicion", "tipo_entidad", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "id_entidad", "UUID", "uuid", True, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "revision_base", "EnteroLargo", "bigint", False, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "id_cambio_almacenado", "UUID", "uuid", True, False, True, False, "", " Clave foranea: cambio_modelo."
    AgregarAtributo "conflicto_edicion", "id_cambio_entrante", "UUID", "uuid", True, False, True, False, "", " Clave foranea: cambio_modelo."
    AgregarAtributo "conflicto_edicion", "valor_almacenado", "JSON", "jsonb", False, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "valor_entrante", "JSON", "jsonb", False, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "estado", "Texto", "character varying(64)", False, False, False, False, "'ABIERTO'::character varying", ""
    AgregarAtributo "conflicto_edicion", "tipo_resolucion", "Texto", "character varying(64)", True, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "valor_resolucion", "JSON", "jsonb", True, False, False, False, "", ""
    AgregarAtributo "conflicto_edicion", "detectado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "conflicto_edicion", "resuelto_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "conflicto_edicion", "resuelto_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
End Sub

Sub CargarAtributos_Validacion_UML()
    AgregarAtributo "regla_validacion", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "regla_validacion", "codigo", "Texto", "citext", False, False, False, False, "", ""
    AgregarAtributo "regla_validacion", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "regla_validacion", "descripcion", "Texto", "text", False, False, False, False, "", ""
    AgregarAtributo "regla_validacion", "severidad", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "regla_validacion", "aplica_a", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "regla_validacion", "esta_activo", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "regla_validacion", "clave_implementacion", "Texto", "character varying(200)", False, False, False, False, "", ""
    AgregarAtributo "regla_validacion", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "validacion_modelo", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "validacion_modelo", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,version_modelo_uml."
    AgregarAtributo "validacion_modelo", "id_version_modelo", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "validacion_modelo", "revision_validada", "EnteroLargo", "bigint", False, False, False, False, "", ""
    AgregarAtributo "validacion_modelo", "solicitado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "validacion_modelo", "estado", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "validacion_modelo", "cantidad_errores", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "validacion_modelo", "cantidad_advertencias", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "validacion_modelo", "iniciado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "validacion_modelo", "completado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "validacion_modelo", "resumen", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "hallazgo_validacion_modelo", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "hallazgo_validacion_modelo", "id_validacion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: validacion_modelo."
    AgregarAtributo "hallazgo_validacion_modelo", "id_regla", "UUID", "uuid", True, False, True, False, "", " Clave foranea: regla_validacion."
    AgregarAtributo "hallazgo_validacion_modelo", "severidad", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_modelo", "tipo_entidad", "Texto", "character varying(64)", True, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_modelo", "id_entidad", "UUID", "uuid", True, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_modelo", "mensaje", "Texto", "text", False, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_modelo", "recomendacion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_modelo", "ubicacion", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "hallazgo_validacion_modelo", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
End Sub

Sub CargarAtributos_Asistente_IA()
    AgregarAtributo "solicitud_inteligente", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "solicitud_inteligente", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml."
    AgregarAtributo "solicitud_inteligente", "solicitado_por", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "solicitud_inteligente", "id_servicio", "UUID", "uuid", True, False, True, False, "", " Clave foranea: servicio_plataforma."
    AgregarAtributo "solicitud_inteligente", "tipo_entrada", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "id_archivo_origen", "UUID", "uuid", True, False, True, False, "", " Clave foranea: archivo_almacenado."
    AgregarAtributo "solicitud_inteligente", "texto_original", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "texto_transcrito", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "texto_corregido", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "codigo_idioma", "Texto", "character varying(16)", True, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "revision_base", "EnteroLargo", "bigint", False, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "estado", "Texto", "character varying(64)", False, False, False, False, "'RECIBIDO'::character varying", ""
    AgregarAtributo "solicitud_inteligente", "respuesta_servicio", "JSON", "jsonb", True, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "detalle_error", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "solicitud_inteligente", "recibido_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "solicitud_inteligente", "completado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "propuesta_inteligente", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "propuesta_inteligente", "id_solicitud", "UUID", "uuid", False, False, True, False, "", " Clave foranea: solicitud_inteligente."
    AgregarAtributo "propuesta_inteligente", "estado", "Texto", "character varying(64)", False, False, False, False, "'BORRADOR'::character varying", ""
    AgregarAtributo "propuesta_inteligente", "resumen", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "propuesta_inteligente", "confianza_general", "Decimal", "numeric(5,4)", True, False, False, False, "", ""
    AgregarAtributo "propuesta_inteligente", "revisado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "propuesta_inteligente", "revisado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "propuesta_inteligente", "aplicado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "propuesta_inteligente", "id_version_aplicada", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "propuesta_inteligente", "aplicado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "propuesta_inteligente", "rechazado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "propuesta_inteligente", "rechazado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "propuesta_inteligente", "motivo_rechazo", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "propuesta_inteligente", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "propuesta_inteligente", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "operacion_propuesta", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "operacion_propuesta", "id_propuesta", "UUID", "uuid", False, False, True, False, "", " Clave foranea: propuesta_inteligente."
    AgregarAtributo "operacion_propuesta", "numero_secuencia", "Entero", "integer", False, False, False, False, "", ""
    AgregarAtributo "operacion_propuesta", "tipo_operacion", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "operacion_propuesta", "tipo_entidad", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "operacion_propuesta", "id_entidad_destino", "UUID", "uuid", True, False, False, False, "", ""
    AgregarAtributo "operacion_propuesta", "valor_actual", "JSON", "jsonb", True, False, False, False, "", ""
    AgregarAtributo "operacion_propuesta", "valor_propuesto", "JSON", "jsonb", True, False, False, False, "", ""
    AgregarAtributo "operacion_propuesta", "confianza", "Decimal", "numeric(5,4)", True, False, False, False, "", ""
    AgregarAtributo "operacion_propuesta", "esta_seleccionado", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "operacion_propuesta", "estado", "Texto", "character varying(64)", False, False, False, False, "'PROPUESTO'::character varying", ""
    AgregarAtributo "operacion_propuesta", "mensajes_validacion", "JSON", "jsonb", False, False, False, False, "'[]'::jsonb", ""
    AgregarAtributo "operacion_propuesta", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "dependencia_operacion_propuesta", "id_operacion", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: operacion_propuesta."
    AgregarAtributo "dependencia_operacion_propuesta", "id_operacion_requerida", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: operacion_propuesta."
    AgregarAtributo "ambiguedad_propuesta", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "ambiguedad_propuesta", "id_propuesta", "UUID", "uuid", False, False, True, False, "", " Clave foranea: propuesta_inteligente."
    AgregarAtributo "ambiguedad_propuesta", "id_operacion", "UUID", "uuid", True, False, True, False, "", " Clave foranea: operacion_propuesta."
    AgregarAtributo "ambiguedad_propuesta", "descripcion", "Texto", "text", False, False, False, False, "", ""
    AgregarAtributo "ambiguedad_propuesta", "resolucion", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "ambiguedad_propuesta", "resuelto_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "ambiguedad_propuesta", "resuelto_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "ambiguedad_propuesta", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
End Sub

Sub CargarAtributos_Intercambio_XMI()
    AgregarAtributo "intercambio_xmi", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "intercambio_xmi", "id_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: modelo_uml,version_modelo_uml."
    AgregarAtributo "intercambio_xmi", "id_version_modelo", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "intercambio_xmi", "solicitado_por", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "intercambio_xmi", "direccion", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "intercambio_xmi", "variante_xmi", "Texto", "character varying(60)", False, False, False, False, "", ""
    AgregarAtributo "intercambio_xmi", "version_enterprise_architect", "Texto", "character varying(60)", True, False, False, False, "", ""
    AgregarAtributo "intercambio_xmi", "estrategia_fusion", "Texto", "character varying(64)", True, False, False, False, "", ""
    AgregarAtributo "intercambio_xmi", "ambito", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "intercambio_xmi", "id_archivo_entrada", "UUID", "uuid", True, False, True, False, "", " Clave foranea: archivo_almacenado."
    AgregarAtributo "intercambio_xmi", "id_archivo_salida", "UUID", "uuid", True, False, True, False, "", " Clave foranea: archivo_almacenado."
    AgregarAtributo "intercambio_xmi", "estado", "Texto", "character varying(64)", False, False, False, False, "'PENDIENTE'::character varying", ""
    AgregarAtributo "intercambio_xmi", "id_version_importada", "UUID", "uuid", True, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "intercambio_xmi", "informe", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "intercambio_xmi", "detalle_error", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "intercambio_xmi", "iniciado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "intercambio_xmi", "completado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "intercambio_xmi", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
End Sub

Sub CargarAtributos_Generacion_backend()
    AgregarAtributo "configuracion_generacion_backend", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "configuracion_generacion_backend", "id_version_modelo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: version_modelo_uml."
    AgregarAtributo "configuracion_generacion_backend", "nombre", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "configuracion_generacion_backend", "nombre_proyecto", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "configuracion_generacion_backend", "id_grupo", "Texto", "character varying(255)", False, False, False, False, "", ""
    AgregarAtributo "configuracion_generacion_backend", "id_artefacto", "Texto", "character varying(255)", False, False, False, False, "", ""
    AgregarAtributo "configuracion_generacion_backend", "paquete_base", "Texto", "character varying(255)", False, False, False, False, "", ""
    AgregarAtributo "configuracion_generacion_backend", "version_inicial", "Texto", "character varying(60)", False, False, False, False, "'0.0.1-SNAPSHOT'::character varying", ""
    AgregarAtributo "configuracion_generacion_backend", "version_java", "Texto", "character varying(20)", False, False, False, False, "", ""
    AgregarAtributo "configuracion_generacion_backend", "version_spring_boot", "Texto", "character varying(30)", False, False, False, False, "", ""
    AgregarAtributo "configuracion_generacion_backend", "motor_base_datos", "Texto", "character varying(20)", False, False, False, False, "'POSTGRESQL'::character varying", ""
    AgregarAtributo "configuracion_generacion_backend", "herramienta_migracion", "Texto", "character varying(16)", False, False, False, False, "'FLYWAY'::character varying", ""
    AgregarAtributo "configuracion_generacion_backend", "generar_repositorios", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "configuracion_generacion_backend", "generar_servicios", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "configuracion_generacion_backend", "generar_controladores", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "configuracion_generacion_backend", "generar_dtos", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "configuracion_generacion_backend", "exponer_rest", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "configuracion_generacion_backend", "plantilla_conexion", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "configuracion_generacion_backend", "estado", "Texto", "character varying(64)", False, False, False, False, "'BORRADOR'::character varying", ""
    AgregarAtributo "configuracion_generacion_backend", "creado_por", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "configuracion_generacion_backend", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "configuracion_generacion_backend", "actualizado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "clase_configuracion_backend", "id_configuracion", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: configuracion_generacion_backend."
    AgregarAtributo "clase_configuracion_backend", "id_clase", "UUID", "uuid", False, True, True, False, "", "Clave primaria compuesta. Clave foranea: clase_uml."
    AgregarAtributo "clase_configuracion_backend", "es_persistente", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "clase_configuracion_backend", "exponer_rest", "Booleano", "boolean", False, False, False, False, "true", ""
    AgregarAtributo "clase_configuracion_backend", "nombre_tabla", "Texto", "character varying(63)", True, False, False, False, "", ""
    AgregarAtributo "clase_configuracion_backend", "nombre_entidad", "Texto", "character varying(180)", True, False, False, False, "", ""
    AgregarAtributo "mapeo_tipo_backend", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "mapeo_tipo_backend", "id_configuracion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: configuracion_generacion_backend."
    AgregarAtributo "mapeo_tipo_backend", "tipo_uml", "Texto", "character varying(180)", False, False, False, False, "", ""
    AgregarAtributo "mapeo_tipo_backend", "tipo_java", "Texto", "character varying(255)", False, False, False, False, "", ""
    AgregarAtributo "mapeo_tipo_backend", "tipo_postgresql", "Texto", "character varying(120)", False, False, False, False, "", ""
    AgregarAtributo "mapeo_tipo_backend", "clase_conversor", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "mapeo_tipo_backend", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "dependencia_backend", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "dependencia_backend", "id_configuracion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: configuracion_generacion_backend."
    AgregarAtributo "dependencia_backend", "id_grupo", "Texto", "character varying(255)", False, False, False, False, "", ""
    AgregarAtributo "dependencia_backend", "id_artefacto", "Texto", "character varying(255)", False, False, False, False, "", ""
    AgregarAtributo "dependencia_backend", "version", "Texto", "character varying(80)", True, False, False, False, "", ""
    AgregarAtributo "dependencia_backend", "ambito_dependencia", "Texto", "character varying(64)", False, False, False, False, "'COMPILACION'::character varying", ""
    AgregarAtributo "dependencia_backend", "es_obligatorio", "Booleano", "boolean", False, False, False, False, "false", ""
    AgregarAtributo "mapeo_persistencia_backend", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "mapeo_persistencia_backend", "id_configuracion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: configuracion_generacion_backend."
    AgregarAtributo "mapeo_persistencia_backend", "id_atributo_uml", "UUID", "uuid", True, False, True, False, "", " Clave foranea: atributo_uml."
    AgregarAtributo "mapeo_persistencia_backend", "id_relacion_uml", "UUID", "uuid", True, False, True, False, "", " Clave foranea: relacion_uml."
    AgregarAtributo "mapeo_persistencia_backend", "nombre_tabla", "Texto", "character varying(63)", False, False, False, False, "", ""
    AgregarAtributo "mapeo_persistencia_backend", "nombre_columna", "Texto", "character varying(63)", True, False, False, False, "", ""
    AgregarAtributo "mapeo_persistencia_backend", "tipo_mapeo", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "mapeo_persistencia_backend", "opciones_mapeo", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "generacion_backend", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "generacion_backend", "id_configuracion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: configuracion_generacion_backend."
    AgregarAtributo "generacion_backend", "solicitado_por", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "generacion_backend", "numero_compilacion", "Entero", "integer", False, False, False, False, "", ""
    AgregarAtributo "generacion_backend", "estado", "Texto", "character varying(64)", False, False, False, False, "'EN_COLA'::character varying", ""
    AgregarAtributo "generacion_backend", "id_archivo_salida", "UUID", "uuid", True, False, True, False, "", " Clave foranea: archivo_almacenado."
    AgregarAtributo "generacion_backend", "id_archivo_registro", "UUID", "uuid", True, False, True, False, "", " Clave foranea: archivo_almacenado."
    AgregarAtributo "generacion_backend", "informe_transformacion", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "generacion_backend", "detalle_error", "Texto", "text", True, False, False, False, "", ""
    AgregarAtributo "generacion_backend", "solicitado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "generacion_backend", "iniciado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "generacion_backend", "completado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "validacion_backend", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "validacion_backend", "id_generacion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: generacion_backend."
    AgregarAtributo "validacion_backend", "solicitado_por", "UUID", "uuid", True, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "validacion_backend", "estado", "Texto", "character varying(64)", False, False, False, False, "'EN_EJECUCION'::character varying", ""
    AgregarAtributo "validacion_backend", "comprobaciones_aprobadas", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "validacion_backend", "cantidad_advertencias", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "validacion_backend", "cantidad_errores", "Entero", "integer", False, False, False, False, "0", ""
    AgregarAtributo "validacion_backend", "id_archivo_informe", "UUID", "uuid", True, False, True, False, "", " Clave foranea: archivo_almacenado."
    AgregarAtributo "validacion_backend", "iniciado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "validacion_backend", "completado_en", "FechaHora", "timestamp with time zone", True, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_backend", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "hallazgo_validacion_backend", "id_validacion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: validacion_backend."
    AgregarAtributo "hallazgo_validacion_backend", "severidad", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_backend", "tipo_comprobacion", "Texto", "character varying(64)", False, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_backend", "componente", "Texto", "character varying(255)", True, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_backend", "mensaje", "Texto", "text", False, False, False, False, "", ""
    AgregarAtributo "hallazgo_validacion_backend", "detalle", "JSON", "jsonb", False, False, False, False, "'{}'::jsonb", ""
    AgregarAtributo "hallazgo_validacion_backend", "creado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "descarga_artefacto", "id", "UUID", "uuid", False, True, False, True, "gen_random_uuid()", "Clave primaria."
    AgregarAtributo "descarga_artefacto", "id_generacion", "UUID", "uuid", False, False, True, False, "", " Clave foranea: generacion_backend."
    AgregarAtributo "descarga_artefacto", "id_archivo", "UUID", "uuid", False, False, True, False, "", " Clave foranea: archivo_almacenado."
    AgregarAtributo "descarga_artefacto", "descargado_por", "UUID", "uuid", False, False, True, False, "", " Clave foranea: usuario."
    AgregarAtributo "descarga_artefacto", "descargado_en", "FechaHora", "timestamp with time zone", False, False, False, False, "clock_timestamp()", ""
    AgregarAtributo "descarga_artefacto", "direccion_ip", "Texto", "inet", True, False, False, False, "", ""
    AgregarAtributo "descarga_artefacto", "agente_cliente", "Texto", "text", True, False, False, False, "", ""
End Sub


Sub CargarRestricciones()
    AgregarRestriccion "actividad_proyecto", "ck_actividad_proyecto_01", "CHECK ((jsonb_typeof(detalle) = 'object'::text))"
    AgregarRestriccion "actividad_proyecto", "ck_actividad_proyecto_02", "CHECK (((id_version_modelo IS NULL) OR (id_modelo IS NOT NULL)))"
    AgregarRestriccion "actividad_proyecto", "pk_actividad_proyecto_01", "PRIMARY KEY (id)"
    AgregarRestriccion "ambiguedad_propuesta", "pk_ambiguedad_propuesta_01", "PRIMARY KEY (id)"
    AgregarRestriccion "archivo_almacenado", "ck_archivo_almacenado_01", "CHECK (((categoria)::text = ANY (ARRAY[('IMAGEN_DIAGRAMA'::character varying)::text, ('AUDIO_VOZ'::character varying)::text, ('ENTRADA_XMI'::character varying)::text, ('SALIDA_XMI'::character varying)::text, ('BACKEND_GENERADO'::character varying)::text, ('INFORME_VALIDACION'::character varying)::text, ('REGISTRO_COMPILACION'::character varying)::text, ('OTRO'::character varying)::text])))"
    AgregarRestriccion "archivo_almacenado", "ck_archivo_almacenado_02", "CHECK ((tamano_bytes >= 0))"
    AgregarRestriccion "archivo_almacenado", "ck_archivo_almacenado_03", "CHECK ((sha256 ~ '^[0-9a-f]{64}$'::text))"
    AgregarRestriccion "archivo_almacenado", "pk_archivo_almacenado_01", "PRIMARY KEY (id)"
    AgregarRestriccion "archivo_almacenado", "uq_archivo_almacenado_01", "UNIQUE (clave_almacenamiento)"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_01", "CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text])))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_02", "CHECK ((multiplicidad_minima >= 0))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_03", "CHECK (((multiplicidad_maxima IS NULL) OR (multiplicidad_maxima >= 0)))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_04", "CHECK ((jsonb_typeof(restricciones_json) = 'object'::text))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_05", "CHECK ((orden >= 0))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_06", "CHECK (((multiplicidad_maxima IS NULL) OR (multiplicidad_maxima >= multiplicidad_minima)))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_07", "CHECK (((nombre)::text <> ''::text))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_08", "CHECK (((tipo_uml)::text <> ''::text))"
    AgregarRestriccion "atributo_uml", "ck_atributo_uml_09", "CHECK (((NOT es_identificador) OR ((multiplicidad_minima = 1) AND (NOT (multiplicidad_maxima IS DISTINCT FROM 1)) AND (NOT es_estatico))))"
    AgregarRestriccion "atributo_uml", "pk_atributo_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "cambio_modelo", "ck_cambio_modelo_01", "CHECK ((revision_base >= 0))"
    AgregarRestriccion "cambio_modelo", "ck_cambio_modelo_02", "CHECK ((revision_aplicada >= 0))"
    AgregarRestriccion "cambio_modelo", "ck_cambio_modelo_03", "CHECK (((tipo_operacion)::text = ANY (ARRAY[('CREAR'::character varying)::text, ('ACTUALIZAR'::character varying)::text, ('ELIMINAR'::character varying)::text, ('MOVER'::character varying)::text, ('RESTAURAR'::character varying)::text, ('IMPORTAR'::character varying)::text])))"
    AgregarRestriccion "cambio_modelo", "ck_cambio_modelo_04", "CHECK ((jsonb_typeof(parche) = ANY (ARRAY['object'::text, 'array'::text])))"
    AgregarRestriccion "cambio_modelo", "ck_cambio_modelo_05", "CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text, ('CONFLICTO'::character varying)::text])))"
    AgregarRestriccion "cambio_modelo", "ck_cambio_modelo_06", "CHECK (((((estado)::text = 'APLICADO'::text) = (revision_aplicada IS NOT NULL)) AND (((estado)::text <> 'APLICADO'::text) OR ((aplicado_en IS NOT NULL) AND (revision_aplicada = (revision_base + 1))))))"
    AgregarRestriccion "cambio_modelo", "pk_cambio_modelo_01", "PRIMARY KEY (id)"
    AgregarRestriccion "cambio_modelo", "uq_cambio_modelo_01", "UNIQUE (id_modelo, id_cambio_cliente)"
    AgregarRestriccion "cambio_modelo", "uq_cambio_modelo_02", "UNIQUE (id_modelo, revision_aplicada)"
    AgregarRestriccion "cambio_modelo", "uq_cambio_modelo_03", "UNIQUE (id, id_modelo)"
    AgregarRestriccion "clase_configuracion_backend", "pk_clase_configuracion_backend_01", "PRIMARY KEY (id_configuracion, id_clase)"
    AgregarRestriccion "clase_uml", "ck_clase_uml_01", "CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text])))"
    AgregarRestriccion "clase_uml", "ck_clase_uml_02", "CHECK (((nombre)::text <> ''::text))"
    AgregarRestriccion "clase_uml", "pk_clase_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "clase_uml", "uq_clase_uml_01", "UNIQUE (id, id_modelo)"
    AgregarRestriccion "comprobacion_servicio", "ck_comprobacion_servicio_01", "CHECK (((estado)::text = ANY (ARRAY[('DISPONIBLE'::character varying)::text, ('NO_DISPONIBLE'::character varying)::text, ('DEGRADADO'::character varying)::text])))"
    AgregarRestriccion "comprobacion_servicio", "ck_comprobacion_servicio_02", "CHECK ((latencia_ms >= 0))"
    AgregarRestriccion "comprobacion_servicio", "pk_comprobacion_servicio_01", "PRIMARY KEY (id)"
    AgregarRestriccion "configuracion_generacion_backend", "ck_configuracion_generacion_backend_01", "CHECK (((id_grupo)::text ~ '^[A-Za-z_][A-Za-z0-9_.]*$'::text))"
    AgregarRestriccion "configuracion_generacion_backend", "ck_configuracion_generacion_backend_02", "CHECK (((motor_base_datos)::text = 'POSTGRESQL'::text))"
    AgregarRestriccion "configuracion_generacion_backend", "ck_configuracion_generacion_backend_03", "CHECK (((herramienta_migracion)::text = ANY (ARRAY[('FLYWAY'::character varying)::text, ('LIQUIBASE'::character varying)::text, ('NINGUNO'::character varying)::text])))"
    AgregarRestriccion "configuracion_generacion_backend", "ck_configuracion_generacion_backend_04", "CHECK ((jsonb_typeof(plantilla_conexion) = 'object'::text))"
    AgregarRestriccion "configuracion_generacion_backend", "ck_configuracion_generacion_backend_05", "CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('VALIDO'::character varying)::text, ('INVALIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text, ('ARCHIVADO'::character varying)::text])))"
    AgregarRestriccion "configuracion_generacion_backend", "ck_configuracion_generacion_backend_06", "CHECK (((id_artefacto)::text ~ '^[A-Za-z0-9_.-]+$'::text))"
    AgregarRestriccion "configuracion_generacion_backend", "ck_configuracion_generacion_backend_07", "CHECK (((paquete_base)::text ~ '^[a-z_][a-z0-9_.]*$'::text))"
    AgregarRestriccion "configuracion_generacion_backend", "pk_configuracion_generacion_backend_01", "PRIMARY KEY (id)"
    AgregarRestriccion "configuracion_generacion_backend", "uq_configuracion_generacion_backend_01", "UNIQUE (id_version_modelo, nombre)"
    AgregarRestriccion "conflicto_edicion", "ck_conflicto_edicion_01", "CHECK ((revision_base >= 0))"
    AgregarRestriccion "conflicto_edicion", "ck_conflicto_edicion_02", "CHECK (((estado)::text = ANY (ARRAY[('ABIERTO'::character varying)::text, ('RESUELTO'::character varying)::text, ('DESCARTADO'::character varying)::text])))"
    AgregarRestriccion "conflicto_edicion", "ck_conflicto_edicion_03", "CHECK (((tipo_resolucion IS NULL) OR ((tipo_resolucion)::text = ANY (ARRAY[('CONSERVAR_ALMACENADO'::character varying)::text, ('APLICAR_ENTRANTE'::character varying)::text, ('FUSIONAR'::character varying)::text]))))"
    AgregarRestriccion "conflicto_edicion", "ck_conflicto_edicion_04", "CHECK ((((estado)::text = 'RESUELTO'::text) = (resuelto_en IS NOT NULL)))"
    AgregarRestriccion "conflicto_edicion", "pk_conflicto_edicion_01", "PRIMARY KEY (id)"
    AgregarRestriccion "dependencia_backend", "ck_dependencia_backend_01", "CHECK (((ambito_dependencia)::text = ANY (ARRAY[('COMPILACION'::character varying)::text, ('EJECUCION'::character varying)::text, ('PRUEBA'::character varying)::text, ('PROVISTO'::character varying)::text])))"
    AgregarRestriccion "dependencia_backend", "pk_dependencia_backend_01", "PRIMARY KEY (id)"
    AgregarRestriccion "dependencia_backend", "uq_dependencia_backend_01", "UNIQUE (id_configuracion, id_grupo, id_artefacto)"
    AgregarRestriccion "dependencia_operacion_propuesta", "ck_dependencia_operacion_propuesta_01", "CHECK ((id_operacion <> id_operacion_requerida))"
    AgregarRestriccion "dependencia_operacion_propuesta", "pk_dependencia_operacion_propuesta_01", "PRIMARY KEY (id_operacion, id_operacion_requerida)"
    AgregarRestriccion "descarga_artefacto", "pk_descarga_artefacto_01", "PRIMARY KEY (id)"
    AgregarRestriccion "generacion_backend", "ck_generacion_backend_01", "CHECK ((numero_compilacion > 0))"
    AgregarRestriccion "generacion_backend", "ck_generacion_backend_02", "CHECK (((estado)::text = ANY (ARRAY[('EN_COLA'::character varying)::text, ('EN_EJECUCION'::character varying)::text, ('GENERADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text])))"
    AgregarRestriccion "generacion_backend", "ck_generacion_backend_03", "CHECK ((jsonb_typeof(informe_transformacion) = 'object'::text))"
    AgregarRestriccion "generacion_backend", "ck_generacion_backend_04", "CHECK ((((estado)::text <> 'GENERADO'::text) OR ((id_archivo_salida IS NOT NULL) AND (completado_en IS NOT NULL))))"
    AgregarRestriccion "generacion_backend", "pk_generacion_backend_01", "PRIMARY KEY (id)"
    AgregarRestriccion "generacion_backend", "uq_generacion_backend_01", "UNIQUE (id_configuracion, numero_compilacion)"
    AgregarRestriccion "hallazgo_validacion_backend", "ck_hallazgo_validacion_backend_01", "CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text])))"
    AgregarRestriccion "hallazgo_validacion_backend", "ck_hallazgo_validacion_backend_02", "CHECK (((tipo_comprobacion)::text = ANY (ARRAY[('ESTRUCTURA'::character varying)::text, ('MAPEO_MODELO'::character varying)::text, ('COMPILACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text, ('CONFIGURACION_POSTGRESQL'::character varying)::text, ('SEGURIDAD'::character varying)::text])))"
    AgregarRestriccion "hallazgo_validacion_backend", "ck_hallazgo_validacion_backend_03", "CHECK ((jsonb_typeof(detalle) = 'object'::text))"
    AgregarRestriccion "hallazgo_validacion_backend", "pk_hallazgo_validacion_backend_01", "PRIMARY KEY (id)"
    AgregarRestriccion "hallazgo_validacion_modelo", "ck_hallazgo_validacion_modelo_01", "CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text])))"
    AgregarRestriccion "hallazgo_validacion_modelo", "ck_hallazgo_validacion_modelo_02", "CHECK ((jsonb_typeof(ubicacion) = 'object'::text))"
    AgregarRestriccion "hallazgo_validacion_modelo", "pk_hallazgo_validacion_modelo_01", "PRIMARY KEY (id)"
    AgregarRestriccion "intercambio_xmi", "ck_intercambio_xmi_01", "CHECK (((direccion)::text = ANY (ARRAY[('IMPORTAR'::character varying)::text, ('EXPORTAR'::character varying)::text])))"
    AgregarRestriccion "intercambio_xmi", "ck_intercambio_xmi_02", "CHECK (((estrategia_fusion IS NULL) OR ((estrategia_fusion)::text = ANY (ARRAY[('FUSIONAR'::character varying)::text, ('REEMPLAZAR'::character varying)::text]))))"
    AgregarRestriccion "intercambio_xmi", "ck_intercambio_xmi_03", "CHECK ((jsonb_typeof(ambito) = 'object'::text))"
    AgregarRestriccion "intercambio_xmi", "ck_intercambio_xmi_04", "CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ANALIZANDO'::character varying)::text, ('LISTO_PARA_REVISION'::character varying)::text, ('COMPLETADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text])))"
    AgregarRestriccion "intercambio_xmi", "ck_intercambio_xmi_05", "CHECK ((jsonb_typeof(informe) = 'object'::text))"
    AgregarRestriccion "intercambio_xmi", "ck_intercambio_xmi_06", "CHECK (((((direccion)::text = 'IMPORTAR'::text) AND (id_archivo_entrada IS NOT NULL)) OR ((direccion)::text = 'EXPORTAR'::text)))"
    AgregarRestriccion "intercambio_xmi", "pk_intercambio_xmi_01", "PRIMARY KEY (id)"
    AgregarRestriccion "mapeo_persistencia_backend", "ck_mapeo_persistencia_backend_01", "CHECK (((tipo_mapeo)::text = ANY (ARRAY[('COLUMNA'::character varying)::text, ('UNO_A_UNO'::character varying)::text, ('UNO_A_MUCHOS'::character varying)::text, ('MUCHOS_A_UNO'::character varying)::text, ('MUCHOS_A_MUCHOS'::character varying)::text, ('HERENCIA'::character varying)::text])))"
    AgregarRestriccion "mapeo_persistencia_backend", "ck_mapeo_persistencia_backend_02", "CHECK ((jsonb_typeof(opciones_mapeo) = 'object'::text))"
    AgregarRestriccion "mapeo_persistencia_backend", "ck_mapeo_persistencia_backend_03", "CHECK (((((id_atributo_uml IS NOT NULL))::integer + ((id_relacion_uml IS NOT NULL))::integer) = 1))"
    AgregarRestriccion "mapeo_persistencia_backend", "pk_mapeo_persistencia_backend_01", "PRIMARY KEY (id)"
    AgregarRestriccion "mapeo_tipo_backend", "pk_mapeo_tipo_backend_01", "PRIMARY KEY (id)"
    AgregarRestriccion "mapeo_tipo_backend", "uq_mapeo_tipo_backend_01", "UNIQUE (id_configuracion, tipo_uml)"
    AgregarRestriccion "miembro_proyecto", "ck_miembro_proyecto_01", "CHECK (((estado)::text = ANY (ARRAY[('INVITADO'::character varying)::text, ('ACTIVO'::character varying)::text, ('SUSPENDIDO'::character varying)::text, ('RETIRADO'::character varying)::text])))"
    AgregarRestriccion "miembro_proyecto", "pk_miembro_proyecto_01", "PRIMARY KEY (id)"
    AgregarRestriccion "miembro_proyecto", "trg_miembro_proyecto_02", "TRIGGER DEFERRABLE INITIALLY DEFERRED"
    AgregarRestriccion "miembro_proyecto", "uq_miembro_proyecto_01", "UNIQUE (id_proyecto, id_usuario)"
    AgregarRestriccion "modelo_uml", "ck_modelo_uml_01", "CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('ARCHIVADO'::character varying)::text])))"
    AgregarRestriccion "modelo_uml", "ck_modelo_uml_02", "CHECK ((revision_actual >= 0))"
    AgregarRestriccion "modelo_uml", "pk_modelo_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "modelo_uml", "trg_modelo_edicion_confirmada", "TRIGGER DEFERRABLE INITIALLY DEFERRED"
    AgregarRestriccion "modelo_uml", "uq_modelo_uml_01", "UNIQUE (id_proyecto, nombre)"
    AgregarRestriccion "modelo_uml", "uq_modelo_uml_02", "UNIQUE (id, id_proyecto)"
    AgregarRestriccion "operacion_propuesta", "ck_operacion_propuesta_01", "CHECK ((numero_secuencia > 0))"
    AgregarRestriccion "operacion_propuesta", "ck_operacion_propuesta_02", "CHECK (((tipo_operacion)::text = ANY (ARRAY[('CREAR'::character varying)::text, ('ACTUALIZAR'::character varying)::text, ('ELIMINAR'::character varying)::text])))"
    AgregarRestriccion "operacion_propuesta", "ck_operacion_propuesta_03", "CHECK (((confianza IS NULL) OR ((confianza >= (0)::numeric) AND (confianza <= (1)::numeric))))"
    AgregarRestriccion "operacion_propuesta", "ck_operacion_propuesta_04", "CHECK (((estado)::text = ANY (ARRAY[('PROPUESTO'::character varying)::text, ('VALIDO'::character varying)::text, ('INVALIDO'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text])))"
    AgregarRestriccion "operacion_propuesta", "ck_operacion_propuesta_05", "CHECK ((jsonb_typeof(mensajes_validacion) = 'array'::text))"
    AgregarRestriccion "operacion_propuesta", "pk_operacion_propuesta_01", "PRIMARY KEY (id)"
    AgregarRestriccion "operacion_propuesta", "uq_operacion_propuesta_01", "UNIQUE (id_propuesta, numero_secuencia)"
    AgregarRestriccion "operacion_uml", "ck_operacion_uml_01", "CHECK (((visibilidad)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('PROTEGIDO'::character varying)::text, ('PRIVADO'::character varying)::text, ('PAQUETE'::character varying)::text])))"
    AgregarRestriccion "operacion_uml", "ck_operacion_uml_02", "CHECK ((orden >= 0))"
    AgregarRestriccion "operacion_uml", "ck_operacion_uml_03", "CHECK (((nombre)::text <> ''::text))"
    AgregarRestriccion "operacion_uml", "pk_operacion_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "operacion_uml", "trg_operacion_uml_03", "TRIGGER DEFERRABLE INITIALLY DEFERRED"
    AgregarRestriccion "paquete_uml", "ck_paquete_uml_01", "CHECK (((nombre)::text <> ''::text))"
    AgregarRestriccion "paquete_uml", "ck_paquete_uml_02", "CHECK ((id_paquete_padre IS DISTINCT FROM id))"
    AgregarRestriccion "paquete_uml", "pk_paquete_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "paquete_uml", "uq_paquete_uml_01", "UNIQUE (id, id_modelo)"
    AgregarRestriccion "parametro_uml", "ck_parametro_uml_01", "CHECK (((direccion)::text = ANY (ARRAY[('ENTRADA'::character varying)::text, ('SALIDA'::character varying)::text, ('ENTRADA_SALIDA'::character varying)::text, ('RETORNO'::character varying)::text])))"
    AgregarRestriccion "parametro_uml", "ck_parametro_uml_02", "CHECK ((orden >= 0))"
    AgregarRestriccion "parametro_uml", "ck_parametro_uml_03", "CHECK (((nombre)::text <> ''::text))"
    AgregarRestriccion "parametro_uml", "ck_parametro_uml_04", "CHECK (((tipo_uml)::text <> ''::text))"
    AgregarRestriccion "parametro_uml", "pk_parametro_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "parametro_uml", "trg_parametro_uml_03", "TRIGGER DEFERRABLE INITIALLY DEFERRED"
    AgregarRestriccion "participante_sesion_modelo", "ck_participante_sesion_modelo_01", "CHECK (((modo_participante)::text = ANY (ARRAY[('EDITOR'::character varying)::text, ('LECTOR'::character varying)::text])))"
    AgregarRestriccion "participante_sesion_modelo", "ck_participante_sesion_modelo_02", "CHECK (((estado_presencia)::text = ANY (ARRAY[('EN_LINEA'::character varying)::text, ('AUSENTE'::character varying)::text, ('DESCONECTADO'::character varying)::text])))"
    AgregarRestriccion "participante_sesion_modelo", "pk_participante_sesion_modelo_01", "PRIMARY KEY (id_sesion_edicion, id_usuario)"
    AgregarRestriccion "permiso", "ck_permiso_01", "CHECK (((ambito)::text = ANY (ARRAY[('GLOBAL'::character varying)::text, ('PROYECTO'::character varying)::text, ('AMBOS'::character varying)::text])))"
    AgregarRestriccion "permiso", "pk_permiso_01", "PRIMARY KEY (id)"
    AgregarRestriccion "permiso", "uq_permiso_01", "UNIQUE (codigo)"
    AgregarRestriccion "permiso", "uq_permiso_02", "UNIQUE (modulo, accion, ambito)"
    AgregarRestriccion "permiso_miembro_proyecto", "ck_permiso_miembro_proyecto_01", "CHECK (((decision)::text = ANY (ARRAY[('PERMITIR'::character varying)::text, ('DENEGAR'::character varying)::text])))"
    AgregarRestriccion "permiso_miembro_proyecto", "ck_permiso_miembro_proyecto_02", "CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))"
    AgregarRestriccion "permiso_miembro_proyecto", "pk_permiso_miembro_proyecto_01", "PRIMARY KEY (id)"
    AgregarRestriccion "posicion_clase_uml", "ck_posicion_clase_uml_01", "CHECK ((ancho > (0)::numeric))"
    AgregarRestriccion "posicion_clase_uml", "ck_posicion_clase_uml_02", "CHECK ((alto > (0)::numeric))"
    AgregarRestriccion "posicion_clase_uml", "ck_posicion_clase_uml_03", "CHECK ((jsonb_typeof(estilo) = 'object'::text))"
    AgregarRestriccion "posicion_clase_uml", "pk_posicion_clase_uml_01", "PRIMARY KEY (id_clase)"
    AgregarRestriccion "posicion_relacion_uml", "ck_posicion_relacion_uml_01", "CHECK ((jsonb_typeof(puntos_intermedios) = 'array'::text))"
    AgregarRestriccion "posicion_relacion_uml", "ck_posicion_relacion_uml_02", "CHECK ((jsonb_typeof(estilo) = 'object'::text))"
    AgregarRestriccion "posicion_relacion_uml", "pk_posicion_relacion_uml_01", "PRIMARY KEY (id_relacion)"
    AgregarRestriccion "preferencia_diagrama", "ck_preferencia_diagrama_01", "CHECK ((escala > (0)::numeric))"
    AgregarRestriccion "preferencia_diagrama", "ck_preferencia_diagrama_02", "CHECK ((jsonb_typeof(preferencias) = 'object'::text))"
    AgregarRestriccion "preferencia_diagrama", "pk_preferencia_diagrama_01", "PRIMARY KEY (id_modelo, id_usuario)"
    AgregarRestriccion "propuesta_inteligente", "ck_propuesta_inteligente_01", "CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('LISTO'::character varying)::text, ('APLICADO'::character varying)::text, ('RECHAZADO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text, ('FALLIDO'::character varying)::text])))"
    AgregarRestriccion "propuesta_inteligente", "ck_propuesta_inteligente_02", "CHECK (((confianza_general IS NULL) OR ((confianza_general >= (0)::numeric) AND (confianza_general <= (1)::numeric))))"
    AgregarRestriccion "propuesta_inteligente", "ck_propuesta_inteligente_03", "CHECK (((((estado)::text <> 'APLICADO'::text) OR ((aplicado_en IS NOT NULL) AND (aplicado_por IS NOT NULL) AND (id_version_aplicada IS NOT NULL))) AND (((estado)::text <> 'RECHAZADO'::text) OR ((rechazado_en IS NOT NULL) AND (rechazado_por IS NOT NULL))) AND (NOT ((aplicado_en IS NOT NULL) AND (rechazado_en IS NOT NULL)))))"
    AgregarRestriccion "propuesta_inteligente", "pk_propuesta_inteligente_01", "PRIMARY KEY (id)"
    AgregarRestriccion "propuesta_inteligente", "uq_propuesta_inteligente_01", "UNIQUE (id_solicitud)"
    AgregarRestriccion "proyecto", "ck_proyecto_01", "CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('ARCHIVADO'::character varying)::text, ('SUSPENDIDO'::character varying)::text])))"
    AgregarRestriccion "proyecto", "ck_proyecto_02", "CHECK (((visibilidad)::text = ANY (ARRAY[('PRIVADO'::character varying)::text, ('INTERNO'::character varying)::text])))"
    AgregarRestriccion "proyecto", "ck_proyecto_03", "CHECK ((jsonb_typeof(opciones) = 'object'::text))"
    AgregarRestriccion "proyecto", "ck_proyecto_04", "CHECK (((nombre)::text <> ''::text))"
    AgregarRestriccion "proyecto", "ck_proyecto_05", "CHECK (((btrim((codigo)::text) <> ''::text) AND (btrim((nombre)::text) <> ''::text)))"
    AgregarRestriccion "proyecto", "pk_proyecto_01", "PRIMARY KEY (id)"
    AgregarRestriccion "proyecto", "trg_proyecto_02", "TRIGGER DEFERRABLE INITIALLY DEFERRED"
    AgregarRestriccion "proyecto", "uq_proyecto_01", "UNIQUE (codigo)"
    AgregarRestriccion "regla_validacion", "ck_regla_validacion_01", "CHECK (((severidad)::text = ANY (ARRAY[('ERROR'::character varying)::text, ('ADVERTENCIA'::character varying)::text, ('INFORMACION'::character varying)::text])))"
    AgregarRestriccion "regla_validacion", "pk_regla_validacion_01", "PRIMARY KEY (id)"
    AgregarRestriccion "regla_validacion", "uq_regla_validacion_01", "UNIQUE (codigo)"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_01", "CHECK (((tipo_relacion)::text = ANY (ARRAY[('ASOCIACION'::character varying)::text, ('AGREGACION'::character varying)::text, ('COMPOSICION'::character varying)::text, ('GENERALIZACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text])))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_02", "CHECK (((direccion)::text = ANY (ARRAY[('SIN_DIRECCION'::character varying)::text, ('ORIGEN_A_DESTINO'::character varying)::text, ('DESTINO_A_ORIGEN'::character varying)::text, ('BIDIRECCIONAL'::character varying)::text])))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_03", "CHECK (((minimo_origen IS NULL) OR (minimo_origen >= 0)))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_04", "CHECK (((maximo_origen IS NULL) OR (maximo_origen >= 0)))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_05", "CHECK (((minimo_destino IS NULL) OR (minimo_destino >= 0)))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_06", "CHECK (((maximo_destino IS NULL) OR (maximo_destino >= 0)))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_07", "CHECK (((maximo_origen IS NULL) OR (minimo_origen IS NULL) OR (maximo_origen >= minimo_origen)))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_08", "CHECK (((maximo_destino IS NULL) OR (minimo_destino IS NULL) OR (maximo_destino >= minimo_destino)))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_09", "CHECK ((((tipo_relacion)::text <> 'GENERALIZACION'::text) OR (id_clase_origen <> id_clase_destino)))"
    AgregarRestriccion "relacion_uml", "ck_relacion_uml_10", "CHECK (((((tipo_relacion)::text = ANY (ARRAY[('ASOCIACION'::character varying)::text, ('AGREGACION'::character varying)::text, ('COMPOSICION'::character varying)::text])) AND (minimo_origen IS NOT NULL) AND (minimo_destino IS NOT NULL)) OR (((tipo_relacion)::text = ANY (ARRAY[('GENERALIZACION'::character varying)::text, ('DEPENDENCIA'::character varying)::text])) AND (minimo_origen IS NULL) AND (maximo_origen IS NULL) AND (minimo_destino IS NULL) AND (maximo_destino IS NULL))))"
    AgregarRestriccion "relacion_uml", "pk_relacion_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "rol", "ck_rol_01", "CHECK (((ambito)::text = ANY (ARRAY[('GLOBAL'::character varying)::text, ('PROYECTO'::character varying)::text, ('AMBOS'::character varying)::text])))"
    AgregarRestriccion "rol", "ck_rol_02", "CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('INACTIVO'::character varying)::text])))"
    AgregarRestriccion "rol", "pk_rol_01", "PRIMARY KEY (id)"
    AgregarRestriccion "rol", "uq_rol_01", "UNIQUE (codigo)"
    AgregarRestriccion "rol_miembro_proyecto", "ck_rol_miembro_proyecto_01", "CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))"
    AgregarRestriccion "rol_miembro_proyecto", "pk_rol_miembro_proyecto_01", "PRIMARY KEY (id)"
    AgregarRestriccion "rol_miembro_proyecto", "trg_rol_miembro_proyecto_01", "TRIGGER DEFERRABLE INITIALLY DEFERRED"
    AgregarRestriccion "rol_permiso", "pk_rol_permiso_01", "PRIMARY KEY (id_rol, id_permiso)"
    AgregarRestriccion "secreto_servicio", "pk_secreto_servicio_01", "PRIMARY KEY (id)"
    AgregarRestriccion "servicio_plataforma", "ck_servicio_plataforma_01", "CHECK (((tipo_servicio)::text = ANY (ARRAY[('IA_TEXTO'::character varying)::text, ('VOZ_A_TEXTO'::character varying)::text, ('IA_IMAGEN'::character varying)::text])))"
    AgregarRestriccion "servicio_plataforma", "ck_servicio_plataforma_02", "CHECK ((jsonb_typeof(configuracion) = 'object'::text))"
    AgregarRestriccion "servicio_plataforma", "ck_servicio_plataforma_03", "CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('INACTIVO'::character varying)::text, ('SIN_VERIFICAR'::character varying)::text, ('NO_DISPONIBLE'::character varying)::text])))"
    AgregarRestriccion "servicio_plataforma", "pk_servicio_plataforma_01", "PRIMARY KEY (id)"
    AgregarRestriccion "servicio_plataforma", "uq_servicio_plataforma_01", "UNIQUE (nombre)"
    AgregarRestriccion "sesion_autenticacion", "ck_sesion_autenticacion_01", "CHECK ((expira_en > emitido_en))"
    AgregarRestriccion "sesion_autenticacion", "ck_sesion_autenticacion_02", "CHECK (((octet_length(hash_token) = 32) AND ((hash_token_renovacion IS NULL) OR (octet_length(hash_token_renovacion) = 32))))"
    AgregarRestriccion "sesion_autenticacion", "ck_sesion_autenticacion_03", "CHECK (((revocado_en IS NULL) OR (revocado_en >= emitido_en)))"
    AgregarRestriccion "sesion_autenticacion", "pk_sesion_autenticacion_01", "PRIMARY KEY (id)"
    AgregarRestriccion "sesion_autenticacion", "uq_sesion_autenticacion_01", "UNIQUE (hash_token)"
    AgregarRestriccion "sesion_autenticacion", "uq_sesion_autenticacion_02", "UNIQUE (hash_token_renovacion)"
    AgregarRestriccion "sesion_edicion_modelo", "ck_sesion_edicion_modelo_01", "CHECK ((revision_base >= 0))"
    AgregarRestriccion "sesion_edicion_modelo", "ck_sesion_edicion_modelo_02", "CHECK (((modo)::text = ANY (ARRAY[('EDICION'::character varying)::text, ('SOLO_LECTURA'::character varying)::text])))"
    AgregarRestriccion "sesion_edicion_modelo", "ck_sesion_edicion_modelo_03", "CHECK (((estado)::text = ANY (ARRAY[('ACTIVO'::character varying)::text, ('CERRADO'::character varying)::text, ('INTERRUMPIDO'::character varying)::text])))"
    AgregarRestriccion "sesion_edicion_modelo", "pk_sesion_edicion_modelo_01", "PRIMARY KEY (id)"
    AgregarRestriccion "sesion_edicion_modelo", "uq_sesion_edicion_modelo_01", "UNIQUE (id, id_modelo)"
    AgregarRestriccion "solicitud_inteligente", "ck_solicitud_inteligente_01", "CHECK (((tipo_entrada)::text = ANY (ARRAY[('TEXTO'::character varying)::text, ('VOZ'::character varying)::text, ('IMAGEN'::character varying)::text])))"
    AgregarRestriccion "solicitud_inteligente", "ck_solicitud_inteligente_02", "CHECK ((revision_base >= 0))"
    AgregarRestriccion "solicitud_inteligente", "ck_solicitud_inteligente_03", "CHECK (((estado)::text = ANY (ARRAY[('RECIBIDO'::character varying)::text, ('PROCESANDO'::character varying)::text, ('COMPLETADO'::character varying)::text, ('FALLIDO'::character varying)::text, ('CANCELADO'::character varying)::text])))"
    AgregarRestriccion "solicitud_inteligente", "ck_solicitud_inteligente_04", "CHECK (((((tipo_entrada)::text = 'TEXTO'::text) AND (texto_original IS NOT NULL)) OR (((tipo_entrada)::text = ANY (ARRAY[('VOZ'::character varying)::text, ('IMAGEN'::character varying)::text])) AND (id_archivo_origen IS NOT NULL))))"
    AgregarRestriccion "solicitud_inteligente", "ck_solicitud_inteligente_05", "CHECK ((((tipo_entrada)::text <> 'TEXTO'::text) OR (btrim(texto_original) <> ''::text)))"
    AgregarRestriccion "solicitud_inteligente", "pk_solicitud_inteligente_01", "PRIMARY KEY (id)"
    AgregarRestriccion "usuario", "ck_usuario_01", "CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ACTIVO'::character varying)::text, ('BLOQUEADO'::character varying)::text, ('INACTIVO'::character varying)::text])))"
    AgregarRestriccion "usuario", "ck_usuario_02", "CHECK ((intentos_fallidos_acceso >= 0))"
    AgregarRestriccion "usuario", "ck_usuario_03", "CHECK ((nombre_usuario <> ''::citext))"
    AgregarRestriccion "usuario", "ck_usuario_04", "CHECK ((correo_electronico <> ''::citext))"
    AgregarRestriccion "usuario", "ck_usuario_05", "CHECK (((btrim((nombre_completo)::text) <> ''::text) AND (btrim((nombre_usuario)::text) <> ''::text) AND (btrim((correo_electronico)::text) <> ''::text)))"
    AgregarRestriccion "usuario", "pk_usuario_01", "PRIMARY KEY (id)"
    AgregarRestriccion "usuario", "uq_usuario_01", "UNIQUE (nombre_usuario)"
    AgregarRestriccion "usuario", "uq_usuario_02", "UNIQUE (correo_electronico)"
    AgregarRestriccion "usuario_rol", "ck_usuario_rol_01", "CHECK (((revocado_en IS NULL) OR (revocado_en >= asignado_en)))"
    AgregarRestriccion "usuario_rol", "pk_usuario_rol_01", "PRIMARY KEY (id)"
    AgregarRestriccion "validacion_backend", "ck_resultado_validacion_coherente", "CHECK (((((estado)::text = 'EN_EJECUCION'::text) OR (completado_en IS NOT NULL)) AND ((completado_en IS NULL) OR (completado_en >= iniciado_en)) AND (((estado)::text <> 'VALIDO'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias = 0))) AND (((estado)::text <> 'VALIDO_CON_ADVERTENCIAS'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias > 0))) AND (((estado)::text <> 'INVALIDO'::text) OR (cantidad_errores > 0))))"
    AgregarRestriccion "validacion_backend", "ck_validacion_backend_01", "CHECK (((estado)::text = ANY (ARRAY[('EN_EJECUCION'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('FALLIDO'::character varying)::text, ('INCOMPLETO'::character varying)::text])))"
    AgregarRestriccion "validacion_backend", "ck_validacion_backend_02", "CHECK ((comprobaciones_aprobadas >= 0))"
    AgregarRestriccion "validacion_backend", "ck_validacion_backend_03", "CHECK ((cantidad_advertencias >= 0))"
    AgregarRestriccion "validacion_backend", "ck_validacion_backend_04", "CHECK ((cantidad_errores >= 0))"
    AgregarRestriccion "validacion_backend", "pk_validacion_backend_01", "PRIMARY KEY (id)"
    AgregarRestriccion "validacion_modelo", "ck_resultado_validacion_coherente", "CHECK (((((estado)::text = 'EN_EJECUCION'::text) OR (completado_en IS NOT NULL)) AND ((completado_en IS NULL) OR (completado_en >= iniciado_en)) AND (((estado)::text <> 'VALIDO'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias = 0))) AND (((estado)::text <> 'VALIDO_CON_ADVERTENCIAS'::text) OR ((cantidad_errores = 0) AND (cantidad_advertencias > 0))) AND (((estado)::text <> 'INVALIDO'::text) OR (cantidad_errores > 0))))"
    AgregarRestriccion "validacion_modelo", "ck_validacion_modelo_01", "CHECK ((revision_validada >= 0))"
    AgregarRestriccion "validacion_modelo", "ck_validacion_modelo_02", "CHECK (((estado)::text = ANY (ARRAY[('EN_EJECUCION'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('FALLIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text])))"
    AgregarRestriccion "validacion_modelo", "ck_validacion_modelo_03", "CHECK ((cantidad_errores >= 0))"
    AgregarRestriccion "validacion_modelo", "ck_validacion_modelo_04", "CHECK ((cantidad_advertencias >= 0))"
    AgregarRestriccion "validacion_modelo", "ck_validacion_modelo_05", "CHECK ((jsonb_typeof(resumen) = 'object'::text))"
    AgregarRestriccion "validacion_modelo", "pk_validacion_modelo_01", "PRIMARY KEY (id)"
    AgregarRestriccion "version_modelo_uml", "ck_version_modelo_uml_01", "CHECK ((numero_version > 0))"
    AgregarRestriccion "version_modelo_uml", "ck_version_modelo_uml_02", "CHECK ((revision >= 0))"
    AgregarRestriccion "version_modelo_uml", "ck_version_modelo_uml_03", "CHECK ((jsonb_typeof(instantanea) = 'object'::text))"
    AgregarRestriccion "version_modelo_uml", "ck_version_modelo_uml_04", "CHECK ((hash_instantanea_sha256 ~ '^[0-9a-f]{64}$'::text))"
    AgregarRestriccion "version_modelo_uml", "ck_version_modelo_uml_05", "CHECK (((estado_validacion)::text = ANY (ARRAY[('SIN_VALIDAR'::character varying)::text, ('VALIDO'::character varying)::text, ('VALIDO_CON_ADVERTENCIAS'::character varying)::text, ('INVALIDO'::character varying)::text, ('DESACTUALIZADO'::character varying)::text])))"
    AgregarRestriccion "version_modelo_uml", "pk_version_modelo_uml_01", "PRIMARY KEY (id)"
    AgregarRestriccion "version_modelo_uml", "uq_version_modelo_uml_01", "UNIQUE (id_modelo, numero_version)"
    AgregarRestriccion "version_modelo_uml", "uq_version_modelo_uml_02", "UNIQUE (id_modelo, revision)"
    AgregarRestriccion "version_modelo_uml", "uq_version_modelo_uml_03", "UNIQUE (id, id_modelo)"
End Sub

Sub CargarRelaciones()
    AgregarRelacion "modelo_uml", "proyecto", "pertenece a", "0..*", "1", "MODELO_UML pertenece a PROYECTO. Lectura inversa: PROYECTO contiene MODELO_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura MODELO_UML: 0..*; destino PROYECTO: 1. FK real modelo_uml.id_proyecto -> proyecto. fk_modelo_uml_01: FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE", "modelo_uml"
    AgregarRelacion "usuario", "modelo_uml", "crea", "0..1", "0..*", "USUARIO crea MODELO_UML. Lectura inversa: MODELO_UML es creado por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino MODELO_UML: 0..*. FK real modelo_uml.creado_por -> usuario. fk_modelo_uml_02: FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "modelo_uml"
    AgregarRelacion "usuario", "paquete_uml", "crea", "0..1", "0..*", "USUARIO crea PAQUETE_UML. Lectura inversa: PAQUETE_UML es creado por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino PAQUETE_UML: 0..*. FK real paquete_uml.creado_por -> usuario. fk_paquete_uml_02: FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "paquete_uml"
    AgregarRelacion "solicitud_inteligente", "modelo_uml", "trabaja sobre", "0..*", "1", "SOLICITUD_INTELIGENTE trabaja sobre MODELO_UML. Lectura inversa: MODELO_UML recibe SOLICITUD_INTELIGENTE.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura SOLICITUD_INTELIGENTE: 0..*; destino MODELO_UML: 1. FK real solicitud_inteligente.id_modelo -> modelo_uml. fk_solicitud_inteligente_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "solicitud_inteligente"
    AgregarRelacion "usuario", "solicitud_inteligente", "solicita", "1", "0..*", "USUARIO solicita SOLICITUD_INTELIGENTE. Lectura inversa: SOLICITUD_INTELIGENTE es solicitada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino SOLICITUD_INTELIGENTE: 0..*. FK real solicitud_inteligente.solicitado_por -> usuario. fk_solicitud_inteligente_02: FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "solicitud_inteligente"
    AgregarRelacion "configuracion_generacion_backend", "version_modelo_uml", "se basa en", "0..*", "1", "CONFIGURACION_GENERACION_BACKEND se basa en VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML sirve de base a CONFIGURACION_GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFIGURACION_GENERACION_BACKEND: 0..*; destino VERSION_MODELO_UML: 1. FK real configuracion_generacion_backend.id_version_modelo -> version_modelo_uml. fk_configuracion_generacion_backend_01: FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE RESTRICT", "configuracion_generacion_backend"
    AgregarRelacion "usuario", "configuracion_generacion_backend", "crea", "1", "0..*", "USUARIO crea CONFIGURACION_GENERACION_BACKEND. Lectura inversa: CONFIGURACION_GENERACION_BACKEND es creada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino CONFIGURACION_GENERACION_BACKEND: 0..*. FK real configuracion_generacion_backend.creado_por -> usuario. fk_configuracion_generacion_backend_02: FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "configuracion_generacion_backend"
    AgregarRelacion "usuario", "sesion_autenticacion", "inicia", "1", "0..*", "USUARIO inicia SESION_AUTENTICACION. Lectura inversa: SESION_AUTENTICACION pertenece a USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino SESION_AUTENTICACION: 0..*. FK real sesion_autenticacion.id_usuario -> usuario. fk_sesion_autenticacion_01: FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE", "sesion_autenticacion"
    AgregarRelacion "rol", "rol_permiso", "tiene", "1", "0..*", "ROL tiene ROL_PERMISO. Lectura inversa: ROL_PERMISO pertenece a ROL.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura ROL: 1; destino ROL_PERMISO: 0..*. FK real rol_permiso.id_rol -> rol. fk_rol_permiso_01: FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE CASCADE", "rol_permiso"
    AgregarRelacion "rol_permiso", "permiso", "asigna", "0..*", "1", "ROL_PERMISO asigna PERMISO. Lectura inversa: PERMISO se asigna mediante ROL_PERMISO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura ROL_PERMISO: 0..*; destino PERMISO: 1. FK real rol_permiso.id_permiso -> permiso. fk_rol_permiso_02: FOREIGN KEY (id_permiso) REFERENCES uml_colaborativo.permiso(id) ON DELETE CASCADE", "rol_permiso"
    AgregarRelacion "usuario", "usuario_rol", "tiene", "1", "0..*", "USUARIO tiene USUARIO_ROL. Lectura inversa: USUARIO_ROL pertenece a USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino USUARIO_ROL: 0..*. FK real usuario_rol.id_usuario -> usuario. fk_usuario_rol_01: FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "usuario_rol"
    AgregarRelacion "usuario_rol", "rol", "asigna", "0..*", "1", "USUARIO_ROL asigna ROL. Lectura inversa: ROL se asigna mediante USUARIO_ROL.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO_ROL: 0..*; destino ROL: 1. FK real usuario_rol.id_rol -> rol. fk_usuario_rol_02: FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE RESTRICT", "usuario_rol"
    AgregarRelacion "usuario", "usuario_rol", "asigna", "0..1", "0..*", "USUARIO asigna USUARIO_ROL. Lectura inversa: USUARIO_ROL es asignada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino USUARIO_ROL: 0..*. FK real usuario_rol.asignado_por -> usuario. fk_usuario_rol_03: FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "usuario_rol"
    AgregarRelacion "usuario", "proyecto", "posee", "1", "0..*", "USUARIO posee PROYECTO. Lectura inversa: PROYECTO pertenece a USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino PROYECTO: 0..*. FK real proyecto.id_usuario_propietario -> usuario. fk_proyecto_01: FOREIGN KEY (id_usuario_propietario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "proyecto"
    AgregarRelacion "miembro_proyecto", "proyecto", "pertenece a", "0..*", "1", "MIEMBRO_PROYECTO pertenece a PROYECTO. Lectura inversa: PROYECTO tiene MIEMBRO_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura MIEMBRO_PROYECTO: 0..*; destino PROYECTO: 1. FK real miembro_proyecto.id_proyecto -> proyecto. fk_miembro_proyecto_01: FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE", "miembro_proyecto"
    AgregarRelacion "usuario", "miembro_proyecto", "participa mediante", "1", "0..*", "USUARIO participa mediante MIEMBRO_PROYECTO. Lectura inversa: MIEMBRO_PROYECTO representa a USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino MIEMBRO_PROYECTO: 0..*. FK real miembro_proyecto.id_usuario -> usuario. fk_miembro_proyecto_02: FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "miembro_proyecto"
    AgregarRelacion "miembro_proyecto", "rol_miembro_proyecto", "tiene", "1", "0..*", "MIEMBRO_PROYECTO tiene ROL_MIEMBRO_PROYECTO. Lectura inversa: ROL_MIEMBRO_PROYECTO pertenece a MIEMBRO_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura MIEMBRO_PROYECTO: 1; destino ROL_MIEMBRO_PROYECTO: 0..*. FK real rol_miembro_proyecto.id_miembro_proyecto -> miembro_proyecto. fk_rol_miembro_proyecto_01: FOREIGN KEY (id_miembro_proyecto) REFERENCES uml_colaborativo.miembro_proyecto(id) ON DELETE CASCADE", "rol_miembro_proyecto"
    AgregarRelacion "rol_miembro_proyecto", "rol", "asigna", "0..*", "1", "ROL_MIEMBRO_PROYECTO asigna ROL. Lectura inversa: ROL se asigna mediante ROL_MIEMBRO_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura ROL_MIEMBRO_PROYECTO: 0..*; destino ROL: 1. FK real rol_miembro_proyecto.id_rol -> rol. fk_rol_miembro_proyecto_02: FOREIGN KEY (id_rol) REFERENCES uml_colaborativo.rol(id) ON DELETE RESTRICT", "rol_miembro_proyecto"
    AgregarRelacion "usuario", "rol_miembro_proyecto", "asigna", "0..1", "0..*", "USUARIO asigna ROL_MIEMBRO_PROYECTO. Lectura inversa: ROL_MIEMBRO_PROYECTO es asignada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino ROL_MIEMBRO_PROYECTO: 0..*. FK real rol_miembro_proyecto.asignado_por -> usuario. fk_rol_miembro_proyecto_03: FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "rol_miembro_proyecto"
    AgregarRelacion "miembro_proyecto", "permiso_miembro_proyecto", "tiene", "1", "0..*", "MIEMBRO_PROYECTO tiene PERMISO_MIEMBRO_PROYECTO. Lectura inversa: PERMISO_MIEMBRO_PROYECTO pertenece a MIEMBRO_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura MIEMBRO_PROYECTO: 1; destino PERMISO_MIEMBRO_PROYECTO: 0..*. FK real permiso_miembro_proyecto.id_miembro_proyecto -> miembro_proyecto. fk_permiso_miembro_proyecto_01: FOREIGN KEY (id_miembro_proyecto) REFERENCES uml_colaborativo.miembro_proyecto(id) ON DELETE CASCADE", "permiso_miembro_proyecto"
    AgregarRelacion "permiso_miembro_proyecto", "permiso", "concede", "0..*", "1", "PERMISO_MIEMBRO_PROYECTO concede PERMISO. Lectura inversa: PERMISO se concede mediante PERMISO_MIEMBRO_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PERMISO_MIEMBRO_PROYECTO: 0..*; destino PERMISO: 1. FK real permiso_miembro_proyecto.id_permiso -> permiso. fk_permiso_miembro_proyecto_02: FOREIGN KEY (id_permiso) REFERENCES uml_colaborativo.permiso(id) ON DELETE CASCADE", "permiso_miembro_proyecto"
    AgregarRelacion "usuario", "permiso_miembro_proyecto", "concede", "0..1", "0..*", "USUARIO concede PERMISO_MIEMBRO_PROYECTO. Lectura inversa: PERMISO_MIEMBRO_PROYECTO es concedida por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino PERMISO_MIEMBRO_PROYECTO: 0..*. FK real permiso_miembro_proyecto.asignado_por -> usuario. fk_permiso_miembro_proyecto_03: FOREIGN KEY (asignado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "permiso_miembro_proyecto"
    AgregarRelacion "archivo_almacenado", "proyecto", "pertenece a", "0..*", "0..1", "ARCHIVO_ALMACENADO pertenece a PROYECTO. Lectura inversa: PROYECTO almacena ARCHIVO_ALMACENADO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura ARCHIVO_ALMACENADO: 0..*; destino PROYECTO: 0..1. FK real archivo_almacenado.id_proyecto -> proyecto. fk_archivo_almacenado_01: FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE", "archivo_almacenado"
    AgregarRelacion "usuario", "archivo_almacenado", "carga", "0..1", "0..*", "USUARIO carga ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO es cargado por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino ARCHIVO_ALMACENADO: 0..*. FK real archivo_almacenado.cargado_por -> usuario. fk_archivo_almacenado_02: FOREIGN KEY (cargado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "archivo_almacenado"
    AgregarRelacion "usuario", "servicio_plataforma", "crea", "0..1", "0..*", "USUARIO crea SERVICIO_PLATAFORMA. Lectura inversa: SERVICIO_PLATAFORMA es creado por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino SERVICIO_PLATAFORMA: 0..*. FK real servicio_plataforma.creado_por -> usuario. fk_servicio_plataforma_01: FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "servicio_plataforma"
    AgregarRelacion "secreto_servicio", "servicio_plataforma", "protege", "0..*", "1", "SECRETO_SERVICIO protege SERVICIO_PLATAFORMA. Lectura inversa: SERVICIO_PLATAFORMA tiene secretos registrados en SECRETO_SERVICIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura SECRETO_SERVICIO: 0..*; destino SERVICIO_PLATAFORMA: 1. FK real secreto_servicio.id_servicio -> servicio_plataforma. fk_secreto_servicio_01: FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE CASCADE", "secreto_servicio"
    AgregarRelacion "comprobacion_servicio", "servicio_plataforma", "comprueba", "0..*", "1", "COMPROBACION_SERVICIO comprueba SERVICIO_PLATAFORMA. Lectura inversa: SERVICIO_PLATAFORMA se comprueba mediante COMPROBACION_SERVICIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura COMPROBACION_SERVICIO: 0..*; destino SERVICIO_PLATAFORMA: 1. FK real comprobacion_servicio.id_servicio -> servicio_plataforma. fk_comprobacion_servicio_01: FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE CASCADE", "comprobacion_servicio"
    AgregarRelacion "usuario", "comprobacion_servicio", "realiza", "0..1", "0..*", "USUARIO realiza COMPROBACION_SERVICIO. Lectura inversa: COMPROBACION_SERVICIO es realizada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino COMPROBACION_SERVICIO: 0..*. FK real comprobacion_servicio.comprobado_por -> usuario. fk_comprobacion_servicio_02: FOREIGN KEY (comprobado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "comprobacion_servicio"
    AgregarRelacion "version_modelo_uml", "modelo_uml", "pertenece a", "0..*", "1", "VERSION_MODELO_UML pertenece a MODELO_UML. Lectura inversa: MODELO_UML tiene versiones registradas en VERSION_MODELO_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VERSION_MODELO_UML: 0..*; destino MODELO_UML: 1. FK real version_modelo_uml.id_modelo -> modelo_uml. fk_version_modelo_uml_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "version_modelo_uml"
    AgregarRelacion "usuario", "version_modelo_uml", "crea", "0..1", "0..*", "USUARIO crea VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML es creada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino VERSION_MODELO_UML: 0..*. FK real version_modelo_uml.creado_por -> usuario. fk_version_modelo_uml_02: FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "version_modelo_uml"
    AgregarRelacion "paquete_uml", "modelo_uml", "pertenece a", "0..*", "1", "PAQUETE_UML pertenece a MODELO_UML. Lectura inversa: MODELO_UML contiene PAQUETE_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PAQUETE_UML: 0..*; destino MODELO_UML: 1. FK real paquete_uml.id_modelo -> modelo_uml. fk_paquete_uml_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "paquete_uml"
    AgregarRelacion "usuario", "paquete_uml", "actualiza", "0..1", "0..*", "USUARIO actualiza PAQUETE_UML. Lectura inversa: PAQUETE_UML es actualizado por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino PAQUETE_UML: 0..*. FK real paquete_uml.actualizado_por -> usuario. fk_paquete_uml_03: FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "paquete_uml"
    AgregarRelacion "clase_uml", "modelo_uml", "pertenece a", "0..*", "1", "CLASE_UML pertenece a MODELO_UML. Lectura inversa: MODELO_UML contiene CLASE_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CLASE_UML: 0..*; destino MODELO_UML: 1. FK real clase_uml.id_modelo -> modelo_uml. fk_clase_uml_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "clase_uml"
    AgregarRelacion "usuario", "clase_uml", "crea", "0..1", "0..*", "USUARIO crea CLASE_UML. Lectura inversa: CLASE_UML es creada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino CLASE_UML: 0..*. FK real clase_uml.creado_por -> usuario. fk_clase_uml_02: FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "clase_uml"
    AgregarRelacion "usuario", "clase_uml", "actualiza", "0..1", "0..*", "USUARIO actualiza CLASE_UML. Lectura inversa: CLASE_UML es actualizada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino CLASE_UML: 0..*. FK real clase_uml.actualizado_por -> usuario. fk_clase_uml_03: FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "clase_uml"
    AgregarRelacion "usuario", "sesion_edicion_modelo", "abre", "1", "0..*", "USUARIO abre SESION_EDICION_MODELO. Lectura inversa: SESION_EDICION_MODELO es abierta por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino SESION_EDICION_MODELO: 0..*. FK real sesion_edicion_modelo.abierto_por -> usuario. fk_sesion_edicion_modelo_01: FOREIGN KEY (abierto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "sesion_edicion_modelo"
    AgregarRelacion "clase_uml", "atributo_uml", "tiene", "1", "0..*", "CLASE_UML tiene ATRIBUTO_UML. Lectura inversa: ATRIBUTO_UML pertenece a CLASE_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CLASE_UML: 1; destino ATRIBUTO_UML: 0..*. FK real atributo_uml.id_clase -> clase_uml. fk_atributo_uml_01: FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE", "atributo_uml"
    AgregarRelacion "posicion_relacion_uml", "relacion_uml", "ubica", "0..1", "1", "POSICION_RELACION_UML ubica RELACION_UML. Lectura inversa: RELACION_UML tiene posicion registrada en POSICION_RELACION_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura POSICION_RELACION_UML: 0..1; destino RELACION_UML: 1. FK real posicion_relacion_uml.id_relacion -> relacion_uml. fk_posicion_relacion_uml_01: FOREIGN KEY (id_relacion) REFERENCES uml_colaborativo.relacion_uml(id) ON DELETE CASCADE", "posicion_relacion_uml"
    AgregarRelacion "usuario", "propuesta_inteligente", "aplica", "0..1", "0..*", "USUARIO aplica PROPUESTA_INTELIGENTE. Lectura inversa: PROPUESTA_INTELIGENTE es aplicada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino PROPUESTA_INTELIGENTE: 0..*. FK real propuesta_inteligente.aplicado_por -> usuario. fk_propuesta_inteligente_03: FOREIGN KEY (aplicado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "propuesta_inteligente"
    AgregarRelacion "clase_uml", "operacion_uml", "tiene", "1", "0..*", "CLASE_UML tiene OPERACION_UML. Lectura inversa: OPERACION_UML pertenece a CLASE_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CLASE_UML: 1; destino OPERACION_UML: 0..*. FK real operacion_uml.id_clase -> clase_uml. fk_operacion_uml_01: FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE", "operacion_uml"
    AgregarRelacion "operacion_uml", "parametro_uml", "recibe", "1", "0..*", "OPERACION_UML recibe PARAMETRO_UML. Lectura inversa: PARAMETRO_UML pertenece a OPERACION_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura OPERACION_UML: 1; destino PARAMETRO_UML: 0..*. FK real parametro_uml.id_operacion -> operacion_uml. fk_parametro_uml_01: FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_uml(id) ON DELETE CASCADE", "parametro_uml"
    AgregarRelacion "relacion_uml", "modelo_uml", "pertenece a", "0..*", "1", "RELACION_UML pertenece a MODELO_UML. Lectura inversa: MODELO_UML contiene RELACION_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura RELACION_UML: 0..*; destino MODELO_UML: 1. FK real relacion_uml.id_modelo -> modelo_uml. fk_relacion_uml_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "relacion_uml"
    AgregarRelacion "relacion_uml", "clase_uml", "parte de", "0..*", "1", "RELACION_UML parte de CLASE_UML. Lectura inversa: CLASE_UML origina RELACION_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura RELACION_UML: 0..*; destino CLASE_UML: 1. FK real relacion_uml.id_clase_origen -> clase_uml. fk_relacion_uml_02: FOREIGN KEY (id_clase_origen) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT | fk_relacion_uml_06: FOREIGN KEY (id_clase_origen, id_modelo) REFERENCES uml_colaborativo.clase_uml(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "relacion_uml"
    AgregarRelacion "relacion_uml", "clase_uml", "llega a", "0..*", "1", "RELACION_UML llega a CLASE_UML. Lectura inversa: CLASE_UML recibe RELACION_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura RELACION_UML: 0..*; destino CLASE_UML: 1. FK real relacion_uml.id_clase_destino -> clase_uml. fk_relacion_uml_03: FOREIGN KEY (id_clase_destino) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT | fk_relacion_uml_07: FOREIGN KEY (id_clase_destino, id_modelo) REFERENCES uml_colaborativo.clase_uml(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "relacion_uml"
    AgregarRelacion "usuario", "relacion_uml", "crea", "0..1", "0..*", "USUARIO crea RELACION_UML. Lectura inversa: RELACION_UML es creada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino RELACION_UML: 0..*. FK real relacion_uml.creado_por -> usuario. fk_relacion_uml_04: FOREIGN KEY (creado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "relacion_uml"
    AgregarRelacion "usuario", "relacion_uml", "actualiza", "0..1", "0..*", "USUARIO actualiza RELACION_UML. Lectura inversa: RELACION_UML es actualizada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino RELACION_UML: 0..*. FK real relacion_uml.actualizado_por -> usuario. fk_relacion_uml_05: FOREIGN KEY (actualizado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "relacion_uml"
    AgregarRelacion "posicion_clase_uml", "clase_uml", "ubica", "0..1", "1", "POSICION_CLASE_UML ubica CLASE_UML. Lectura inversa: CLASE_UML tiene posicion registrada en POSICION_CLASE_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura POSICION_CLASE_UML: 0..1; destino CLASE_UML: 1. FK real posicion_clase_uml.id_clase -> clase_uml. fk_posicion_clase_uml_01: FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE CASCADE", "posicion_clase_uml"
    AgregarRelacion "preferencia_diagrama", "modelo_uml", "personaliza", "0..*", "1", "PREFERENCIA_DIAGRAMA personaliza MODELO_UML. Lectura inversa: MODELO_UML se personaliza mediante PREFERENCIA_DIAGRAMA.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PREFERENCIA_DIAGRAMA: 0..*; destino MODELO_UML: 1. FK real preferencia_diagrama.id_modelo -> modelo_uml. fk_preferencia_diagrama_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "preferencia_diagrama"
    AgregarRelacion "usuario", "preferencia_diagrama", "establece", "1", "0..*", "USUARIO establece PREFERENCIA_DIAGRAMA. Lectura inversa: PREFERENCIA_DIAGRAMA pertenece a USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino PREFERENCIA_DIAGRAMA: 0..*. FK real preferencia_diagrama.id_usuario -> usuario. fk_preferencia_diagrama_02: FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE", "preferencia_diagrama"
    AgregarRelacion "sesion_edicion_modelo", "modelo_uml", "edita", "0..*", "1", "SESION_EDICION_MODELO edita MODELO_UML. Lectura inversa: MODELO_UML tiene sesiones registradas en SESION_EDICION_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura SESION_EDICION_MODELO: 0..*; destino MODELO_UML: 1. FK real sesion_edicion_modelo.id_modelo -> modelo_uml. fk_sesion_edicion_modelo_02: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "sesion_edicion_modelo"
    AgregarRelacion "participante_sesion_modelo", "sesion_edicion_modelo", "pertenece a", "0..*", "1", "PARTICIPANTE_SESION_MODELO pertenece a SESION_EDICION_MODELO. Lectura inversa: SESION_EDICION_MODELO tiene PARTICIPANTE_SESION_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PARTICIPANTE_SESION_MODELO: 0..*; destino SESION_EDICION_MODELO: 1. FK real participante_sesion_modelo.id_sesion_edicion -> sesion_edicion_modelo. fk_participante_sesion_modelo_01: FOREIGN KEY (id_sesion_edicion) REFERENCES uml_colaborativo.sesion_edicion_modelo(id) ON DELETE CASCADE", "participante_sesion_modelo"
    AgregarRelacion "usuario", "participante_sesion_modelo", "participa mediante", "1", "0..*", "USUARIO participa mediante PARTICIPANTE_SESION_MODELO. Lectura inversa: PARTICIPANTE_SESION_MODELO representa a USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino PARTICIPANTE_SESION_MODELO: 0..*. FK real participante_sesion_modelo.id_usuario -> usuario. fk_participante_sesion_modelo_02: FOREIGN KEY (id_usuario) REFERENCES uml_colaborativo.usuario(id) ON DELETE CASCADE", "participante_sesion_modelo"
    AgregarRelacion "conflicto_edicion", "cambio_modelo", "contrasta el guardado", "0..*", "0..1", "CONFLICTO_EDICION contrasta el guardado CAMBIO_MODELO. Lectura inversa: CAMBIO_MODELO interviene como guardado en CONFLICTO_EDICION.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFLICTO_EDICION: 0..*; destino CAMBIO_MODELO: 0..1. FK real conflicto_edicion.id_cambio_almacenado -> cambio_modelo. fk_conflicto_edicion_01: FOREIGN KEY (id_cambio_almacenado) REFERENCES uml_colaborativo.cambio_modelo(id) ON DELETE SET NULL | fk_conflicto_edicion_06: FOREIGN KEY (id_cambio_almacenado, id_modelo) REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "conflicto_edicion"
    AgregarRelacion "conflicto_edicion", "cambio_modelo", "contrasta el entrante", "0..*", "0..1", "CONFLICTO_EDICION contrasta el entrante CAMBIO_MODELO. Lectura inversa: CAMBIO_MODELO interviene como entrante en CONFLICTO_EDICION.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFLICTO_EDICION: 0..*; destino CAMBIO_MODELO: 0..1. FK real conflicto_edicion.id_cambio_entrante -> cambio_modelo. fk_conflicto_edicion_02: FOREIGN KEY (id_cambio_entrante) REFERENCES uml_colaborativo.cambio_modelo(id) ON DELETE SET NULL | fk_conflicto_edicion_05: FOREIGN KEY (id_cambio_entrante, id_modelo) REFERENCES uml_colaborativo.cambio_modelo(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "conflicto_edicion"
    AgregarRelacion "cambio_modelo", "modelo_uml", "modifica", "0..*", "1", "CAMBIO_MODELO modifica MODELO_UML. Lectura inversa: MODELO_UML recibe CAMBIO_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CAMBIO_MODELO: 0..*; destino MODELO_UML: 1. FK real cambio_modelo.id_modelo -> modelo_uml. fk_cambio_modelo_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "cambio_modelo"
    AgregarRelacion "cambio_modelo", "sesion_edicion_modelo", "ocurre durante", "0..*", "0..1", "CAMBIO_MODELO ocurre durante SESION_EDICION_MODELO. Lectura inversa: SESION_EDICION_MODELO agrupa CAMBIO_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CAMBIO_MODELO: 0..*; destino SESION_EDICION_MODELO: 0..1. FK real cambio_modelo.id_sesion_edicion -> sesion_edicion_modelo. fk_cambio_modelo_02: FOREIGN KEY (id_sesion_edicion) REFERENCES uml_colaborativo.sesion_edicion_modelo(id) ON DELETE SET NULL | fk_cambio_modelo_04: FOREIGN KEY (id_sesion_edicion, id_modelo) REFERENCES uml_colaborativo.sesion_edicion_modelo(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "cambio_modelo"
    AgregarRelacion "usuario", "cambio_modelo", "realiza", "1", "0..*", "USUARIO realiza CAMBIO_MODELO. Lectura inversa: CAMBIO_MODELO es realizado por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino CAMBIO_MODELO: 0..*. FK real cambio_modelo.id_usuario_actor -> usuario. fk_cambio_modelo_03: FOREIGN KEY (id_usuario_actor) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "cambio_modelo"
    AgregarRelacion "conflicto_edicion", "modelo_uml", "afecta a", "0..*", "1", "CONFLICTO_EDICION afecta a MODELO_UML. Lectura inversa: MODELO_UML tiene conflictos registrados en CONFLICTO_EDICION.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFLICTO_EDICION: 0..*; destino MODELO_UML: 1. FK real conflicto_edicion.id_modelo -> modelo_uml. fk_conflicto_edicion_03: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "conflicto_edicion"
    AgregarRelacion "usuario", "conflicto_edicion", "resuelve", "0..1", "0..*", "USUARIO resuelve CONFLICTO_EDICION. Lectura inversa: CONFLICTO_EDICION es resuelto por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino CONFLICTO_EDICION: 0..*. FK real conflicto_edicion.resuelto_por -> usuario. fk_conflicto_edicion_04: FOREIGN KEY (resuelto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "conflicto_edicion"
    AgregarRelacion "actividad_proyecto", "proyecto", "registra actividad de", "0..*", "1", "ACTIVIDAD_PROYECTO registra actividad de PROYECTO. Lectura inversa: PROYECTO tiene actividad registrada en ACTIVIDAD_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura ACTIVIDAD_PROYECTO: 0..*; destino PROYECTO: 1. FK real actividad_proyecto.id_proyecto -> proyecto. fk_actividad_proyecto_01: FOREIGN KEY (id_proyecto) REFERENCES uml_colaborativo.proyecto(id) ON DELETE CASCADE", "actividad_proyecto"
    AgregarRelacion "actividad_proyecto", "modelo_uml", "registra actividad de", "0..*", "0..1", "ACTIVIDAD_PROYECTO registra actividad de MODELO_UML. Lectura inversa: MODELO_UML tiene actividad registrada en ACTIVIDAD_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura ACTIVIDAD_PROYECTO: 0..*; destino MODELO_UML: 0..1. FK real actividad_proyecto.id_modelo -> modelo_uml. fk_actividad_proyecto_02: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE SET NULL | fk_actividad_proyecto_05: FOREIGN KEY (id_modelo, id_proyecto) REFERENCES uml_colaborativo.modelo_uml(id, id_proyecto) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "actividad_proyecto"
    AgregarRelacion "actividad_proyecto", "version_modelo_uml", "registra actividad de", "0..*", "0..1", "ACTIVIDAD_PROYECTO registra actividad de VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML tiene actividad registrada en ACTIVIDAD_PROYECTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura ACTIVIDAD_PROYECTO: 0..*; destino VERSION_MODELO_UML: 0..1. FK real actividad_proyecto.id_version_modelo -> version_modelo_uml. fk_actividad_proyecto_03: FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL | fk_actividad_proyecto_06: FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "actividad_proyecto"
    AgregarRelacion "usuario", "actividad_proyecto", "realiza", "0..1", "0..*", "USUARIO realiza ACTIVIDAD_PROYECTO. Lectura inversa: ACTIVIDAD_PROYECTO es realizada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino ACTIVIDAD_PROYECTO: 0..*. FK real actividad_proyecto.id_usuario_actor -> usuario. fk_actividad_proyecto_04: FOREIGN KEY (id_usuario_actor) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "actividad_proyecto"
    AgregarRelacion "propuesta_inteligente", "solicitud_inteligente", "responde a", "0..1", "1", "PROPUESTA_INTELIGENTE responde a SOLICITUD_INTELIGENTE. Lectura inversa: SOLICITUD_INTELIGENTE produce PROPUESTA_INTELIGENTE.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PROPUESTA_INTELIGENTE: 0..1; destino SOLICITUD_INTELIGENTE: 1. FK real propuesta_inteligente.id_solicitud -> solicitud_inteligente. fk_propuesta_inteligente_01: FOREIGN KEY (id_solicitud) REFERENCES uml_colaborativo.solicitud_inteligente(id) ON DELETE CASCADE", "propuesta_inteligente"
    AgregarRelacion "validacion_modelo", "modelo_uml", "evalua", "0..*", "1", "VALIDACION_MODELO evalua MODELO_UML. Lectura inversa: MODELO_UML se evalua mediante VALIDACION_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VALIDACION_MODELO: 0..*; destino MODELO_UML: 1. FK real validacion_modelo.id_modelo -> modelo_uml. fk_validacion_modelo_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "validacion_modelo"
    AgregarRelacion "validacion_modelo", "version_modelo_uml", "evalua", "0..*", "0..1", "VALIDACION_MODELO evalua VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML se evalua mediante VALIDACION_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VALIDACION_MODELO: 0..*; destino VERSION_MODELO_UML: 0..1. FK real validacion_modelo.id_version_modelo -> version_modelo_uml. fk_validacion_modelo_02: FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL | fk_validacion_modelo_04: FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "validacion_modelo"
    AgregarRelacion "usuario", "validacion_modelo", "solicita", "0..1", "0..*", "USUARIO solicita VALIDACION_MODELO. Lectura inversa: VALIDACION_MODELO es solicitada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino VALIDACION_MODELO: 0..*. FK real validacion_modelo.solicitado_por -> usuario. fk_validacion_modelo_03: FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "validacion_modelo"
    AgregarRelacion "validacion_modelo", "hallazgo_validacion_modelo", "detecta", "1", "0..*", "VALIDACION_MODELO detecta HALLAZGO_VALIDACION_MODELO. Lectura inversa: HALLAZGO_VALIDACION_MODELO pertenece a VALIDACION_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VALIDACION_MODELO: 1; destino HALLAZGO_VALIDACION_MODELO: 0..*. FK real hallazgo_validacion_modelo.id_validacion -> validacion_modelo. fk_hallazgo_validacion_modelo_01: FOREIGN KEY (id_validacion) REFERENCES uml_colaborativo.validacion_modelo(id) ON DELETE CASCADE", "hallazgo_validacion_modelo"
    AgregarRelacion "hallazgo_validacion_modelo", "regla_validacion", "se fundamenta en", "0..*", "0..1", "HALLAZGO_VALIDACION_MODELO se fundamenta en REGLA_VALIDACION. Lectura inversa: REGLA_VALIDACION fundamenta HALLAZGO_VALIDACION_MODELO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura HALLAZGO_VALIDACION_MODELO: 0..*; destino REGLA_VALIDACION: 0..1. FK real hallazgo_validacion_modelo.id_regla -> regla_validacion. fk_hallazgo_validacion_modelo_02: FOREIGN KEY (id_regla) REFERENCES uml_colaborativo.regla_validacion(id) ON DELETE SET NULL", "hallazgo_validacion_modelo"
    AgregarRelacion "usuario", "propuesta_inteligente", "revisa", "0..1", "0..*", "USUARIO revisa PROPUESTA_INTELIGENTE. Lectura inversa: PROPUESTA_INTELIGENTE es revisada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino PROPUESTA_INTELIGENTE: 0..*. FK real propuesta_inteligente.revisado_por -> usuario. fk_propuesta_inteligente_02: FOREIGN KEY (revisado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "propuesta_inteligente"
    AgregarRelacion "propuesta_inteligente", "version_modelo_uml", "se aplica en", "0..*", "0..1", "PROPUESTA_INTELIGENTE se aplica en VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML registra la aplicacion de PROPUESTA_INTELIGENTE.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PROPUESTA_INTELIGENTE: 0..*; destino VERSION_MODELO_UML: 0..1. FK real propuesta_inteligente.id_version_aplicada -> version_modelo_uml. fk_propuesta_inteligente_04: FOREIGN KEY (id_version_aplicada) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL", "propuesta_inteligente"
    AgregarRelacion "usuario", "propuesta_inteligente", "rechaza", "0..1", "0..*", "USUARIO rechaza PROPUESTA_INTELIGENTE. Lectura inversa: PROPUESTA_INTELIGENTE es rechazada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino PROPUESTA_INTELIGENTE: 0..*. FK real propuesta_inteligente.rechazado_por -> usuario. fk_propuesta_inteligente_05: FOREIGN KEY (rechazado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "propuesta_inteligente"
    AgregarRelacion "propuesta_inteligente", "ambiguedad_propuesta", "plantea", "1", "0..*", "PROPUESTA_INTELIGENTE plantea AMBIGUEDAD_PROPUESTA. Lectura inversa: AMBIGUEDAD_PROPUESTA pertenece a PROPUESTA_INTELIGENTE.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PROPUESTA_INTELIGENTE: 1; destino AMBIGUEDAD_PROPUESTA: 0..*. FK real ambiguedad_propuesta.id_propuesta -> propuesta_inteligente. fk_ambiguedad_propuesta_01: FOREIGN KEY (id_propuesta) REFERENCES uml_colaborativo.propuesta_inteligente(id) ON DELETE CASCADE", "ambiguedad_propuesta"
    AgregarRelacion "solicitud_inteligente", "servicio_plataforma", "utiliza", "0..*", "0..1", "SOLICITUD_INTELIGENTE utiliza SERVICIO_PLATAFORMA. Lectura inversa: SERVICIO_PLATAFORMA atiende SOLICITUD_INTELIGENTE.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura SOLICITUD_INTELIGENTE: 0..*; destino SERVICIO_PLATAFORMA: 0..1. FK real solicitud_inteligente.id_servicio -> servicio_plataforma. fk_solicitud_inteligente_03: FOREIGN KEY (id_servicio) REFERENCES uml_colaborativo.servicio_plataforma(id) ON DELETE SET NULL", "solicitud_inteligente"
    AgregarRelacion "solicitud_inteligente", "archivo_almacenado", "interpreta", "0..*", "0..1", "SOLICITUD_INTELIGENTE interpreta ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO es interpretado por SOLICITUD_INTELIGENTE.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura SOLICITUD_INTELIGENTE: 0..*; destino ARCHIVO_ALMACENADO: 0..1. FK real solicitud_inteligente.id_archivo_origen -> archivo_almacenado. fk_solicitud_inteligente_04: FOREIGN KEY (id_archivo_origen) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL", "solicitud_inteligente"
    AgregarRelacion "usuario", "ambiguedad_propuesta", "resuelve", "0..1", "0..*", "USUARIO resuelve AMBIGUEDAD_PROPUESTA. Lectura inversa: AMBIGUEDAD_PROPUESTA es resuelta por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino AMBIGUEDAD_PROPUESTA: 0..*. FK real ambiguedad_propuesta.resuelto_por -> usuario. fk_ambiguedad_propuesta_03: FOREIGN KEY (resuelto_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "ambiguedad_propuesta"
    AgregarRelacion "intercambio_xmi", "version_modelo_uml", "utiliza como origen", "0..*", "0..1", "INTERCAMBIO_XMI utiliza como origen VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML se utiliza como origen en INTERCAMBIO_XMI.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura INTERCAMBIO_XMI: 0..*; destino VERSION_MODELO_UML: 0..1. FK real intercambio_xmi.id_version_modelo -> version_modelo_uml. fk_intercambio_xmi_02: FOREIGN KEY (id_version_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL | fk_intercambio_xmi_07: FOREIGN KEY (id_version_modelo, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "intercambio_xmi"
    AgregarRelacion "usuario", "intercambio_xmi", "solicita", "1", "0..*", "USUARIO solicita INTERCAMBIO_XMI. Lectura inversa: INTERCAMBIO_XMI es solicitado por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino INTERCAMBIO_XMI: 0..*. FK real intercambio_xmi.solicitado_por -> usuario. fk_intercambio_xmi_03: FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "intercambio_xmi"
    AgregarRelacion "intercambio_xmi", "archivo_almacenado", "lee", "0..*", "0..1", "INTERCAMBIO_XMI lee ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO es leido por INTERCAMBIO_XMI.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura INTERCAMBIO_XMI: 0..*; destino ARCHIVO_ALMACENADO: 0..1. FK real intercambio_xmi.id_archivo_entrada -> archivo_almacenado. fk_intercambio_xmi_04: FOREIGN KEY (id_archivo_entrada) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL", "intercambio_xmi"
    AgregarRelacion "propuesta_inteligente", "operacion_propuesta", "contiene", "1", "0..*", "PROPUESTA_INTELIGENTE contiene OPERACION_PROPUESTA. Lectura inversa: OPERACION_PROPUESTA pertenece a PROPUESTA_INTELIGENTE.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PROPUESTA_INTELIGENTE: 1; destino OPERACION_PROPUESTA: 0..*. FK real operacion_propuesta.id_propuesta -> propuesta_inteligente. fk_operacion_propuesta_01: FOREIGN KEY (id_propuesta) REFERENCES uml_colaborativo.propuesta_inteligente(id) ON DELETE CASCADE", "operacion_propuesta"
    AgregarRelacion "dependencia_operacion_propuesta", "operacion_propuesta", "pertenece a", "0..*", "1", "DEPENDENCIA_OPERACION_PROPUESTA pertenece a OPERACION_PROPUESTA. Lectura inversa: OPERACION_PROPUESTA tiene dependencias registradas en DEPENDENCIA_OPERACION_PROPUESTA.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura DEPENDENCIA_OPERACION_PROPUESTA: 0..*; destino OPERACION_PROPUESTA: 1. FK real dependencia_operacion_propuesta.id_operacion -> operacion_propuesta. fk_dependencia_operacion_propuesta_01: FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE", "dependencia_operacion_propuesta"
    AgregarRelacion "dependencia_operacion_propuesta", "operacion_propuesta", "requiere", "0..*", "1", "DEPENDENCIA_OPERACION_PROPUESTA requiere OPERACION_PROPUESTA. Lectura inversa: OPERACION_PROPUESTA es requerida mediante DEPENDENCIA_OPERACION_PROPUESTA.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura DEPENDENCIA_OPERACION_PROPUESTA: 0..*; destino OPERACION_PROPUESTA: 1. FK real dependencia_operacion_propuesta.id_operacion_requerida -> operacion_propuesta. fk_dependencia_operacion_propuesta_02: FOREIGN KEY (id_operacion_requerida) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE", "dependencia_operacion_propuesta"
    AgregarRelacion "ambiguedad_propuesta", "operacion_propuesta", "afecta a", "0..*", "0..1", "AMBIGUEDAD_PROPUESTA afecta a OPERACION_PROPUESTA. Lectura inversa: OPERACION_PROPUESTA tiene ambiguedades registradas en AMBIGUEDAD_PROPUESTA.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura AMBIGUEDAD_PROPUESTA: 0..*; destino OPERACION_PROPUESTA: 0..1. FK real ambiguedad_propuesta.id_operacion -> operacion_propuesta. fk_ambiguedad_propuesta_02: FOREIGN KEY (id_operacion) REFERENCES uml_colaborativo.operacion_propuesta(id) ON DELETE CASCADE", "ambiguedad_propuesta"
    AgregarRelacion "intercambio_xmi", "modelo_uml", "intercambia", "0..*", "1", "INTERCAMBIO_XMI intercambia MODELO_UML. Lectura inversa: MODELO_UML participa en INTERCAMBIO_XMI.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura INTERCAMBIO_XMI: 0..*; destino MODELO_UML: 1. FK real intercambio_xmi.id_modelo -> modelo_uml. fk_intercambio_xmi_01: FOREIGN KEY (id_modelo) REFERENCES uml_colaborativo.modelo_uml(id) ON DELETE CASCADE", "intercambio_xmi"
    AgregarRelacion "intercambio_xmi", "archivo_almacenado", "produce", "0..*", "0..1", "INTERCAMBIO_XMI produce ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO es producido por INTERCAMBIO_XMI.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura INTERCAMBIO_XMI: 0..*; destino ARCHIVO_ALMACENADO: 0..1. FK real intercambio_xmi.id_archivo_salida -> archivo_almacenado. fk_intercambio_xmi_05: FOREIGN KEY (id_archivo_salida) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL", "intercambio_xmi"
    AgregarRelacion "intercambio_xmi", "version_modelo_uml", "incorpora", "0..*", "0..1", "INTERCAMBIO_XMI incorpora VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML es incorporada por INTERCAMBIO_XMI.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura INTERCAMBIO_XMI: 0..*; destino VERSION_MODELO_UML: 0..1. FK real intercambio_xmi.id_version_importada -> version_modelo_uml. fk_intercambio_xmi_06: FOREIGN KEY (id_version_importada) REFERENCES uml_colaborativo.version_modelo_uml(id) ON DELETE SET NULL | fk_intercambio_xmi_08: FOREIGN KEY (id_version_importada, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) Las referencias simples/compuestas verifican tambien el mismo modelo/proyecto; no son asociaciones adicionales.", "intercambio_xmi"
    AgregarRelacion "configuracion_generacion_backend", "clase_configuracion_backend", "contiene", "1", "0..*", "CONFIGURACION_GENERACION_BACKEND contiene CLASE_CONFIGURACION_BACKEND. Lectura inversa: CLASE_CONFIGURACION_BACKEND pertenece a CONFIGURACION_GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFIGURACION_GENERACION_BACKEND: 1; destino CLASE_CONFIGURACION_BACKEND: 0..*. FK real clase_configuracion_backend.id_configuracion -> configuracion_generacion_backend. fk_clase_configuracion_backend_01: FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE", "clase_configuracion_backend"
    AgregarRelacion "clase_configuracion_backend", "clase_uml", "selecciona", "0..*", "1", "CLASE_CONFIGURACION_BACKEND selecciona CLASE_UML. Lectura inversa: CLASE_UML se selecciona mediante CLASE_CONFIGURACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CLASE_CONFIGURACION_BACKEND: 0..*; destino CLASE_UML: 1. FK real clase_configuracion_backend.id_clase -> clase_uml. fk_clase_configuracion_backend_02: FOREIGN KEY (id_clase) REFERENCES uml_colaborativo.clase_uml(id) ON DELETE RESTRICT", "clase_configuracion_backend"
    AgregarRelacion "configuracion_generacion_backend", "mapeo_tipo_backend", "contiene", "1", "0..*", "CONFIGURACION_GENERACION_BACKEND contiene MAPEO_TIPO_BACKEND. Lectura inversa: MAPEO_TIPO_BACKEND pertenece a CONFIGURACION_GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFIGURACION_GENERACION_BACKEND: 1; destino MAPEO_TIPO_BACKEND: 0..*. FK real mapeo_tipo_backend.id_configuracion -> configuracion_generacion_backend. fk_mapeo_tipo_backend_01: FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE", "mapeo_tipo_backend"
    AgregarRelacion "configuracion_generacion_backend", "dependencia_backend", "declara", "1", "0..*", "CONFIGURACION_GENERACION_BACKEND declara DEPENDENCIA_BACKEND. Lectura inversa: DEPENDENCIA_BACKEND pertenece a CONFIGURACION_GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFIGURACION_GENERACION_BACKEND: 1; destino DEPENDENCIA_BACKEND: 0..*. FK real dependencia_backend.id_configuracion -> configuracion_generacion_backend. fk_dependencia_backend_01: FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE", "dependencia_backend"
    AgregarRelacion "generacion_backend", "configuracion_generacion_backend", "utiliza", "0..*", "1", "GENERACION_BACKEND utiliza CONFIGURACION_GENERACION_BACKEND. Lectura inversa: CONFIGURACION_GENERACION_BACKEND se utiliza en GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura GENERACION_BACKEND: 0..*; destino CONFIGURACION_GENERACION_BACKEND: 1. FK real generacion_backend.id_configuracion -> configuracion_generacion_backend. fk_generacion_backend_01: FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE RESTRICT", "generacion_backend"
    AgregarRelacion "usuario", "generacion_backend", "solicita", "1", "0..*", "USUARIO solicita GENERACION_BACKEND. Lectura inversa: GENERACION_BACKEND es solicitada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino GENERACION_BACKEND: 0..*. FK real generacion_backend.solicitado_por -> usuario. fk_generacion_backend_02: FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "generacion_backend"
    AgregarRelacion "generacion_backend", "archivo_almacenado", "produce", "0..*", "0..1", "GENERACION_BACKEND produce ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO es producido por GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura GENERACION_BACKEND: 0..*; destino ARCHIVO_ALMACENADO: 0..1. FK real generacion_backend.id_archivo_salida -> archivo_almacenado. fk_generacion_backend_03: FOREIGN KEY (id_archivo_salida) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL", "generacion_backend"
    AgregarRelacion "generacion_backend", "archivo_almacenado", "registra en", "0..*", "0..1", "GENERACION_BACKEND registra en ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO contiene el registro de GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura GENERACION_BACKEND: 0..*; destino ARCHIVO_ALMACENADO: 0..1. FK real generacion_backend.id_archivo_registro -> archivo_almacenado. fk_generacion_backend_04: FOREIGN KEY (id_archivo_registro) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL", "generacion_backend"
    AgregarRelacion "configuracion_generacion_backend", "mapeo_persistencia_backend", "contiene", "1", "0..*", "CONFIGURACION_GENERACION_BACKEND contiene MAPEO_PERSISTENCIA_BACKEND. Lectura inversa: MAPEO_PERSISTENCIA_BACKEND pertenece a CONFIGURACION_GENERACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CONFIGURACION_GENERACION_BACKEND: 1; destino MAPEO_PERSISTENCIA_BACKEND: 0..*. FK real mapeo_persistencia_backend.id_configuracion -> configuracion_generacion_backend. fk_mapeo_persistencia_backend_01: FOREIGN KEY (id_configuracion) REFERENCES uml_colaborativo.configuracion_generacion_backend(id) ON DELETE CASCADE", "mapeo_persistencia_backend"
    AgregarRelacion "mapeo_persistencia_backend", "atributo_uml", "mapea", "0..*", "0..1", "MAPEO_PERSISTENCIA_BACKEND mapea ATRIBUTO_UML. Lectura inversa: ATRIBUTO_UML se mapea mediante MAPEO_PERSISTENCIA_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura MAPEO_PERSISTENCIA_BACKEND: 0..*; destino ATRIBUTO_UML: 0..1. FK real mapeo_persistencia_backend.id_atributo_uml -> atributo_uml. fk_mapeo_persistencia_backend_02: FOREIGN KEY (id_atributo_uml) REFERENCES uml_colaborativo.atributo_uml(id) ON DELETE RESTRICT", "mapeo_persistencia_backend"
    AgregarRelacion "mapeo_persistencia_backend", "relacion_uml", "mapea", "0..*", "0..1", "MAPEO_PERSISTENCIA_BACKEND mapea RELACION_UML. Lectura inversa: RELACION_UML se mapea mediante MAPEO_PERSISTENCIA_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura MAPEO_PERSISTENCIA_BACKEND: 0..*; destino RELACION_UML: 0..1. FK real mapeo_persistencia_backend.id_relacion_uml -> relacion_uml. fk_mapeo_persistencia_backend_03: FOREIGN KEY (id_relacion_uml) REFERENCES uml_colaborativo.relacion_uml(id) ON DELETE RESTRICT", "mapeo_persistencia_backend"
    AgregarRelacion "validacion_backend", "generacion_backend", "evalua", "0..*", "1", "VALIDACION_BACKEND evalua GENERACION_BACKEND. Lectura inversa: GENERACION_BACKEND se evalua mediante VALIDACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VALIDACION_BACKEND: 0..*; destino GENERACION_BACKEND: 1. FK real validacion_backend.id_generacion -> generacion_backend. fk_validacion_backend_01: FOREIGN KEY (id_generacion) REFERENCES uml_colaborativo.generacion_backend(id) ON DELETE CASCADE", "validacion_backend"
    AgregarRelacion "usuario", "validacion_backend", "solicita", "0..1", "0..*", "USUARIO solicita VALIDACION_BACKEND. Lectura inversa: VALIDACION_BACKEND es solicitada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 0..1; destino VALIDACION_BACKEND: 0..*. FK real validacion_backend.solicitado_por -> usuario. fk_validacion_backend_02: FOREIGN KEY (solicitado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE SET NULL", "validacion_backend"
    AgregarRelacion "validacion_backend", "archivo_almacenado", "informa en", "0..*", "0..1", "VALIDACION_BACKEND informa en ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO contiene el informe de VALIDACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VALIDACION_BACKEND: 0..*; destino ARCHIVO_ALMACENADO: 0..1. FK real validacion_backend.id_archivo_informe -> archivo_almacenado. fk_validacion_backend_03: FOREIGN KEY (id_archivo_informe) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE SET NULL", "validacion_backend"
    AgregarRelacion "validacion_backend", "hallazgo_validacion_backend", "detecta", "1", "0..*", "VALIDACION_BACKEND detecta HALLAZGO_VALIDACION_BACKEND. Lectura inversa: HALLAZGO_VALIDACION_BACKEND pertenece a VALIDACION_BACKEND.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VALIDACION_BACKEND: 1; destino HALLAZGO_VALIDACION_BACKEND: 0..*. FK real hallazgo_validacion_backend.id_validacion -> validacion_backend. fk_hallazgo_validacion_backend_01: FOREIGN KEY (id_validacion) REFERENCES uml_colaborativo.validacion_backend(id) ON DELETE CASCADE", "hallazgo_validacion_backend"
    AgregarRelacion "descarga_artefacto", "generacion_backend", "obtiene el resultado de", "0..*", "1", "DESCARGA_ARTEFACTO obtiene el resultado de GENERACION_BACKEND. Lectura inversa: GENERACION_BACKEND tiene descargas registradas en DESCARGA_ARTEFACTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura DESCARGA_ARTEFACTO: 0..*; destino GENERACION_BACKEND: 1. FK real descarga_artefacto.id_generacion -> generacion_backend. fk_descarga_artefacto_01: FOREIGN KEY (id_generacion) REFERENCES uml_colaborativo.generacion_backend(id) ON DELETE RESTRICT", "descarga_artefacto"
    AgregarRelacion "descarga_artefacto", "archivo_almacenado", "obtiene", "0..*", "1", "DESCARGA_ARTEFACTO obtiene ARCHIVO_ALMACENADO. Lectura inversa: ARCHIVO_ALMACENADO se obtiene mediante DESCARGA_ARTEFACTO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura DESCARGA_ARTEFACTO: 0..*; destino ARCHIVO_ALMACENADO: 1. FK real descarga_artefacto.id_archivo -> archivo_almacenado. fk_descarga_artefacto_02: FOREIGN KEY (id_archivo) REFERENCES uml_colaborativo.archivo_almacenado(id) ON DELETE RESTRICT", "descarga_artefacto"
    AgregarRelacion "usuario", "descarga_artefacto", "realiza", "1", "0..*", "USUARIO realiza DESCARGA_ARTEFACTO. Lectura inversa: DESCARGA_ARTEFACTO es realizada por USUARIO.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura USUARIO: 1; destino DESCARGA_ARTEFACTO: 0..*. FK real descarga_artefacto.descargado_por -> usuario. fk_descarga_artefacto_03: FOREIGN KEY (descargado_por) REFERENCES uml_colaborativo.usuario(id) ON DELETE RESTRICT", "descarga_artefacto"
    AgregarRelacion "modelo_uml", "version_modelo_uml", "tiene como version actual", "0..1", "0..1", "MODELO_UML tiene como version actual VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML es version actual de MODELO_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura MODELO_UML: 0..1; destino VERSION_MODELO_UML: 0..1. FK real modelo_uml.id_version_actual -> version_modelo_uml. fk_modelo_uml_03: FOREIGN KEY (id_version_actual, id) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED", "modelo_uml"
    AgregarRelacion "version_modelo_uml", "version_modelo_uml", "deriva de", "0..*", "0..1", "VERSION_MODELO_UML deriva de VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML origina VERSION_MODELO_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VERSION_MODELO_UML: 0..*; destino VERSION_MODELO_UML: 0..1. FK real version_modelo_uml.id_version_padre -> version_modelo_uml. fk_version_modelo_uml_03: FOREIGN KEY (id_version_padre, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED", "version_modelo_uml"
    AgregarRelacion "version_modelo_uml", "version_modelo_uml", "restaura", "0..*", "0..1", "VERSION_MODELO_UML restaura VERSION_MODELO_UML. Lectura inversa: VERSION_MODELO_UML es restaurada por VERSION_MODELO_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura VERSION_MODELO_UML: 0..*; destino VERSION_MODELO_UML: 0..1. FK real version_modelo_uml.id_version_restaurada -> version_modelo_uml. fk_version_modelo_uml_04: FOREIGN KEY (id_version_restaurada, id_modelo) REFERENCES uml_colaborativo.version_modelo_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED", "version_modelo_uml"
    AgregarRelacion "paquete_uml", "paquete_uml", "pertenece a", "0..*", "0..1", "PAQUETE_UML pertenece a PAQUETE_UML. Lectura inversa: PAQUETE_UML contiene PAQUETE_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura PAQUETE_UML: 0..*; destino PAQUETE_UML: 0..1. FK real paquete_uml.id_paquete_padre -> paquete_uml. fk_paquete_uml_04: FOREIGN KEY (id_paquete_padre, id_modelo) REFERENCES uml_colaborativo.paquete_uml(id, id_modelo) DEFERRABLE INITIALLY DEFERRED", "paquete_uml"
    AgregarRelacion "clase_uml", "paquete_uml", "pertenece a", "0..*", "0..1", "CLASE_UML pertenece a PAQUETE_UML. Lectura inversa: PAQUETE_UML contiene CLASE_UML.\nMultiplicidades de instancias, sin filtrar historicos. Origen de lectura CLASE_UML: 0..*; destino PAQUETE_UML: 0..1. FK real clase_uml.id_paquete -> paquete_uml. fk_clase_uml_04: FOREIGN KEY (id_paquete, id_modelo) REFERENCES uml_colaborativo.paquete_uml(id, id_modelo) ON DELETE SET NULL (id_paquete) DEFERRABLE INITIALLY DEFERRED", "clase_uml"
End Sub

Sub CargarPosiciones()
    Posicionar "", "actividad_proyecto", 3663, 6173, 185, 266
    Posicionar "", "ambiguedad_propuesta", 3663, 7422, 185, 181
    Posicionar "", "archivo_almacenado", 4505, 9001, 214, 249
    Posicionar "", "atributo_uml", 4519, 2044, 185, 351
    Posicionar "", "cambio_modelo", 3663, 4512, 185, 283
    Posicionar "", "clase_configuracion_backend", 2492, 3353, 237, 147
    Posicionar "", "clase_uml", 3663, 1458, 185, 317
    Posicionar "", "comprobacion_servicio", 3660, 12891, 192, 164
    Posicionar "", "configuracion_generacion_backend", 1393, 5748, 274, 419
    Posicionar "", "conflicto_edicion", 2960, 3178, 185, 300
    Posicionar "", "dependencia_backend", 2064, 6518, 185, 164
    Posicionar "", "dependencia_operacion_propuesta", 3622, 8625, 267, 79
    Posicionar "", "descarga_artefacto", 139, 8829, 185, 164
    Posicionar "", "generacion_backend", 818, 10354, 199, 249
    Posicionar "", "hallazgo_validacion_backend", 799, 10907, 237, 181
    Posicionar "", "hallazgo_validacion_modelo", 4497, 2779, 229, 215
    Posicionar "", "intercambio_xmi", 3634, 5219, 244, 351
    Posicionar "", "mapeo_persistencia_backend", 2042, 3712, 229, 181
    Posicionar "", "mapeo_tipo_backend", 2064, 6112, 185, 164
    Posicionar "", "miembro_proyecto", 3663, 11579, 185, 215
    Posicionar "", "modelo_uml", 5356, 4744, 185, 232
    Posicionar "", "operacion_propuesta", 4519, 7962, 185, 266
    Posicionar "", "operacion_uml", 4519, 335, 185, 266
    Posicionar "", "paquete_uml", 4519, 1391, 185, 249
    Posicionar "", "parametro_uml", 5356, 195, 185, 266
    Posicionar "", "participante_sesion_modelo", 3641, 6762, 229, 198
    Posicionar "", "permiso", 6428, 11739, 185, 164
    Posicionar "", "permiso_miembro_proyecto", 4941, 11611, 214, 164
    Posicionar "", "posicion_clase_uml", 2960, 2204, 185, 181
    Posicionar "", "posicion_relacion_uml", 2060, 2535, 192, 147
    Posicionar "", "preferencia_diagrama", 4106, 7463, 185, 198
    Posicionar "", "propuesta_inteligente", 2957, 7695, 192, 300
    Posicionar "", "proyecto", 5904, 7990, 199, 232
    Posicionar "", "regla_validacion", 5356, 785, 185, 198
    Posicionar "", "relacion_uml", 2960, 1381, 185, 419
    Posicionar "", "rol", 5356, 11977, 185, 198
    Posicionar "", "rol_miembro_proyecto", 4519, 12002, 185, 147
    Posicionar "", "rol_permiso", 5911, 11912, 185, 96
    Posicionar "", "secreto_servicio", 3663, 13497, 185, 181
    Posicionar "", "servicio_plataforma", 4519, 12866, 185, 215
    Posicionar "", "sesion_autenticacion", 3660, 10440, 192, 215
    Posicionar "", "sesion_edicion_modelo", 4516, 6076, 192, 198
    Posicionar "", "solicitud_inteligente", 3660, 9176, 192, 317
    Posicionar "", "usuario", 2946, 10316, 214, 249
    Posicionar "", "usuario_rol", 4106, 12194, 185, 147
    Posicionar "", "validacion_backend", 124, 10890, 214, 215
    Posicionar "", "validacion_modelo", 3660, 3516, 192, 232
    Posicionar "", "version_modelo_uml", 4508, 5266, 207, 249
    Posicionar "01 Acceso", "permiso", 2186, 636, 185, 164
    Posicionar "01 Acceso", "rol", 1186, 619, 185, 198
    Posicionar "01 Acceso", "rol_permiso", 1680, 670, 185, 96
    Posicionar "01 Acceso", "sesion_autenticacion", 670, 165, 192, 215
    Posicionar "01 Acceso", "usuario", 124, 260, 214, 249
    Posicionar "01 Acceso", "usuario_rol", 674, 644, 185, 147
    Posicionar "02 Proyectos", "actividad_proyecto", 116, 743, 185, 266
    Posicionar "02 Proyectos", "miembro_proyecto", 1405, 1438, 185, 215
    Posicionar "02 Proyectos", "modelo_uml", 1405, 621, 185, 232
    Posicionar "02 Proyectos", "permiso", 2515, 1449, 185, 164
    Posicionar "02 Proyectos", "permiso_miembro_proyecto", 1957, 1463, 214, 164
    Posicionar "02 Proyectos", "proyecto", 1965, 948, 199, 232
    Posicionar "02 Proyectos", "rol", 2515, 1877, 185, 198
    Posicionar "02 Proyectos", "rol_miembro_proyecto", 1972, 1888, 185, 147
    Posicionar "02 Proyectos", "usuario", 727, 1421, 214, 249
    Posicionar "02 Proyectos", "version_modelo_uml", 730, 390, 207, 249
    Posicionar "03 Servicios y archivos", "archivo_almacenado", 124, 185, 214, 249
    Posicionar "03 Servicios y archivos", "comprobacion_servicio", 135, 1505, 192, 164
    Posicionar "03 Servicios y archivos", "proyecto", 710, 194, 199, 232
    Posicionar "03 Servicios y archivos", "secreto_servicio", 139, 1922, 185, 181
    Posicionar "03 Servicios y archivos", "servicio_plataforma", 717, 1767, 185, 215
    Posicionar "03 Servicios y archivos", "usuario", 124, 870, 214, 249
    Posicionar "04 Modelo UML", "atributo_uml", 1754, 2301, 185, 351
    Posicionar "04 Modelo UML", "clase_uml", 1170, 2318, 185, 317
    Posicionar "04 Modelo UML", "modelo_uml", 2412, 1218, 185, 232
    Posicionar "04 Modelo UML", "operacion_uml", 1754, 3665, 185, 266
    Posicionar "04 Modelo UML", "paquete_uml", 1754, 1648, 185, 249
    Posicionar "04 Modelo UML", "parametro_uml", 2412, 3665, 185, 266
    Posicionar "04 Modelo UML", "posicion_clase_uml", 624, 2677, 185, 181
    Posicionar "04 Modelo UML", "posicion_relacion_uml", 118, 1990, 192, 147
    Posicionar "04 Modelo UML", "preferencia_diagrama", 1754, 211, 185, 198
    Posicionar "04 Modelo UML", "proyecto", 2960, 1218, 199, 232
    Posicionar "04 Modelo UML", "relacion_uml", 624, 1854, 185, 419
    Posicionar "04 Modelo UML", "usuario", 1155, 185, 214, 249
    Posicionar "04 Modelo UML", "version_modelo_uml", 1743, 819, 207, 249
    Posicionar "05 Colaboracion", "cambio_modelo", 811, 344, 185, 283
    Posicionar "05 Colaboracion", "conflicto_edicion", 139, 216, 185, 300
    Posicionar "05 Colaboracion", "modelo_uml", 1915, 299, 185, 232
    Posicionar "05 Colaboracion", "participante_sesion_modelo", 789, 1148, 229, 198
    Posicionar "05 Colaboracion", "sesion_edicion_modelo", 1412, 695, 192, 198
    Posicionar "05 Colaboracion", "usuario", 124, 1123, 214, 249
    Posicionar "06 Validacion UML", "hallazgo_validacion_modelo", 687, 165, 229, 215
    Posicionar "06 Validacion UML", "modelo_uml", 1385, 843, 185, 232
    Posicionar "06 Validacion UML", "regla_validacion", 1385, 174, 185, 198
    Posicionar "06 Validacion UML", "usuario", 124, 1252, 214, 249
    Posicionar "06 Validacion UML", "validacion_modelo", 135, 482, 192, 232
    Posicionar "06 Validacion UML", "version_modelo_uml", 698, 1004, 207, 249
    Posicionar "07 Asistente IA", "ambiguedad_propuesta", 772, 2643, 185, 181
    Posicionar "07 Asistente IA", "archivo_almacenado", 1487, 185, 214, 249
    Posicionar "07 Asistente IA", "dependencia_operacion_propuesta", 731, 2157, 267, 79
    Posicionar "07 Asistente IA", "modelo_uml", 1502, 1340, 185, 232
    Posicionar "07 Asistente IA", "operacion_propuesta", 1502, 2166, 185, 266
    Posicionar "07 Asistente IA", "propuesta_inteligente", 135, 1723, 192, 300
    Posicionar "07 Asistente IA", "servicio_plataforma", 1502, 758, 185, 215
    Posicionar "07 Asistente IA", "solicitud_inteligente", 769, 663, 192, 317
    Posicionar "07 Asistente IA", "usuario", 124, 2573, 214, 249
    Posicionar "07 Asistente IA", "version_modelo_uml", 761, 1461, 207, 249
    Posicionar "08 Intercambio XMI", "archivo_almacenado", 811, 185, 214, 249
    Posicionar "08 Intercambio XMI", "intercambio_xmi", 133, 496, 244, 351
    Posicionar "08 Intercambio XMI", "modelo_uml", 1490, 751, 185, 232
    Posicionar "08 Intercambio XMI", "usuario", 148, 1344, 214, 249
    Posicionar "08 Intercambio XMI", "version_modelo_uml", 815, 1078, 207, 249
    Posicionar "09 Generacion backend", "archivo_almacenado", 1458, 1724, 214, 249
    Posicionar "09 Generacion backend", "atributo_uml", 4001, 246, 185, 351
    Posicionar "09 Generacion backend", "clase_configuracion_backend", 2545, 1591, 237, 147
    Posicionar "09 Generacion backend", "clase_uml", 3508, 549, 185, 317
    Posicionar "09 Generacion backend", "configuracion_generacion_backend", 1428, 2417, 274, 419
    Posicionar "09 Generacion backend", "dependencia_backend", 2116, 3043, 185, 164
    Posicionar "09 Generacion backend", "descarga_artefacto", 139, 1451, 185, 164
    Posicionar "09 Generacion backend", "generacion_backend", 818, 1844, 199, 249
    Posicionar "09 Generacion backend", "hallazgo_validacion_backend", 799, 2601, 237, 181
    Posicionar "09 Generacion backend", "mapeo_persistencia_backend", 2094, 673, 229, 181
    Posicionar "09 Generacion backend", "mapeo_tipo_backend", 2116, 3450, 185, 164
    Posicionar "09 Generacion backend", "relacion_uml", 2990, 554, 185, 419
    Posicionar "09 Generacion backend", "usuario", 124, 2001, 214, 249
    Posicionar "09 Generacion backend", "validacion_backend", 124, 2666, 214, 215
    Posicionar "09 Generacion backend", "version_modelo_uml", 2105, 2502, 207, 249
End Sub
