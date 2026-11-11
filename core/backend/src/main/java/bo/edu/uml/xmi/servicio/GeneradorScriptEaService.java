package bo.edu.uml.xmi.servicio;

import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generador de scripts VBScript para Enterprise Architect (EA 12-16+).
 *
 * <p>Produce un script autocontenido que se puede ejecutar en la ventana de Scripting de Enterprise
 * Architect o directamente en Windows con WScript/CScript. Crea programáticamente los paquetes, el
 * diagrama de clases, los elementos, atributos, operaciones con parámetros, y los conectores con
 * cardinalidades y roles visibles, ubicando los objetos en el canvas y abriendo el diagrama
 * automáticamente.
 */
@Service
public class GeneradorScriptEaService {

    /** Genera el script VBScript para Enterprise Architect. */
    public byte[] generar(
            String nombreModelo,
            List<Map<String, Object>> paquetes,
            List<Map<String, Object>> clases,
            List<Map<String, Object>> atributos,
            List<Map<String, Object>> operaciones,
            List<Map<String, Object>> parametros,
            List<Map<String, Object>> relaciones,
            List<Map<String, Object>> posicionesClases) {

        var w = new StringWriter(8192);

        w.write(
                "' ==============================================================================\n");
        w.write("' Script de Creación de Diagrama de Clases para Enterprise Architect\n");
        w.write("' Plataforma UML Colaborativa - Modelo: " + escComentario(nombreModelo) + "\n");
        w.write(
                "' ==============================================================================\n");
        w.write("Option Explicit\n\n");

        w.write("Sub GenerarDiagramaClases()\n");
        w.write("    Dim repo, pkgPadre, pkgNuevo, diagrama\n");
        w.write("    \n");
        w.write("    On Error Resume Next\n");
        w.write("    Dim eaApp\n");
        w.write("    Set repo = Nothing\n");
        w.write("    Set eaApp = Nothing\n");
        w.write("    Set eaApp = GetObject(, \"EA.App\")\n");
        w.write("    If Not eaApp Is Nothing Then Set repo = eaApp.Repository\n");
        w.write("    On Error GoTo 0\n");
        w.write("    \n");
        w.write("    If repo Is Nothing Then\n");
        w.write("        MsgBox \"No se pudo conectar a Enterprise Architect.\" & vbCrLf & _\n");
        w.write(
                "               \"Por favor abre Enterprise Architect con tu proyecto antes de"
                        + " ejecutar este script.\", _\n");
        w.write("               vbCritical, \"Plataforma UML\"\n");
        w.write("        Exit Sub\n");
        w.write("    End If\n");
        w.write("    \n");
        w.write("    Set pkgPadre = repo.GetTreeSelectedPackage()\n");
        w.write("    If pkgPadre Is Nothing Then\n");
        w.write("        If repo.Models.Count > 0 Then\n");
        w.write("            Set pkgPadre = repo.Models.GetAt(0)\n");
        w.write("        Else\n");
        w.write(
                "            MsgBox \"Por favor selecciona un paquete o modelo en el Explorador de"
                        + " Proyectos de Enterprise Architect.\", _\n");
        w.write("                   vbExclamation, \"Plataforma UML\"\n");
        w.write("            Exit Sub\n");
        w.write("        End If\n");
        w.write("    End If\n");
        w.write("    \n");

        // Crear subpaquete para el modelo
        w.write("    ' Crear paquete contenedor\n");
        w.write(
                "    Set pkgNuevo = pkgPadre.Packages.AddNew(\""
                        + escVbs(nombreModelo)
                        + "\", \"Nothing\")\n");
        w.write("    pkgNuevo.Update()\n");
        w.write("    \n");

        // Crear diagrama de clases
        w.write("    ' Crear diagrama de clases UML\n");
        w.write(
                "    Set diagrama = pkgNuevo.Diagrams.AddNew(\""
                        + escVbs(nombreModelo)
                        + " - Diagrama de Clases\", \"Logical\")\n");
        w.write(
                "    diagrama.Notes = \"Diagrama UML de clases generado automáticamente por la"
                        + " Plataforma UML Colaborativa.\"\n");
        w.write("    diagrama.Update()\n");
        w.write("    \n");

        w.write("    Dim dictClases, dictPaquetes, pkgDestino, pkgActual\n");
        w.write("    Set dictClases = CreateObject(\"Scripting.Dictionary\")\n");
        w.write("    Set dictPaquetes = CreateObject(\"Scripting.Dictionary\")\n");
        w.write("    Dim elem, attr, op, param, con, diagObj\n\n");

        w.write("    ' --- Jerarquía de paquetes UML ---\n");
        for (var paquete : ordenarPaquetes(paquetes)) {
            String id = str(paquete.get("id"));
            String padre = str(paquete.get("id_paquete_padre"));
            w.write("    Set pkgDestino = pkgNuevo\n");
            if (padre != null)
                w.write(
                        "    If dictPaquetes.Exists(\""
                                + escVbs(padre)
                                + "\") Then Set pkgDestino = dictPaquetes(\""
                                + escVbs(padre)
                                + "\")\n");
            w.write(
                    "    Set pkgActual = pkgDestino.Packages.AddNew(\""
                            + escVbs(str(paquete.get("nombre")))
                            + "\", \"Nothing\")\n");
            w.write("    pkgActual.Update()\n");
            w.write("    dictPaquetes.Add \"" + escVbs(id) + "\", pkgActual\n\n");
        }

        // Agrupaciones para acceso O(1)
        Map<String, List<Map<String, Object>>> atribPorClase = agrupar(atributos, "id_clase");
        Map<String, List<Map<String, Object>>> opPorClase = agrupar(operaciones, "id_clase");
        Map<String, List<Map<String, Object>>> paramPorOp = agrupar(parametros, "id_operacion");

        // Mapeo de posiciones
        Map<String, Map<String, Object>> posMap = new HashMap<>();
        if (posicionesClases != null) {
            for (var p : posicionesClases) {
                String idC = str(p.get("id_clase"));
                if (idC != null) posMap.put(idC, p);
            }
        }

        // Crear Clases
        w.write("    ' --- Clases UML ---\n");
        for (int i = 0; i < clases.size(); i++) {
            var c = clases.get(i);
            String id = str(c.get("id"));
            String nombre = str(c.get("nombre"));
            boolean esAbstracto = bool(c.get("es_abstracto"));
            String visibilidad = visibilidadVbs(str(c.get("visibilidad")));

            w.write("    Set pkgDestino = pkgNuevo\n");
            String idPaquete = str(c.get("id_paquete"));
            if (idPaquete != null)
                w.write(
                        "    If dictPaquetes.Exists(\""
                                + escVbs(idPaquete)
                                + "\") Then Set pkgDestino = dictPaquetes(\""
                                + escVbs(idPaquete)
                                + "\")\n");
            w.write(
                    "    Set elem = pkgDestino.Elements.AddNew(\""
                            + escVbs(nombre)
                            + "\", \"Class\")\n");
            w.write("    elem.Visibility = \"" + visibilidad + "\"\n");
            if (esAbstracto) w.write("    elem.Abstract = \"1\"\n");
            w.write("    elem.Update()\n");
            w.write("    dictClases.Add \"" + escVbs(id) + "\", elem\n");

            // Atributos de la clase
            for (var a : atribPorClase.getOrDefault(id, List.of())) {
                String aNombre = str(a.get("nombre"));
                String aTipo = tipoVbs(str(a.get("tipo_uml")));
                String aVis = visibilidadVbs(str(a.get("visibilidad")));
                boolean aEstatico = bool(a.get("es_estatico"));
                w.write(
                        "    Set attr = elem.Attributes.AddNew(\""
                                + escVbs(aNombre)
                                + "\", \""
                                + escVbs(aTipo)
                                + "\")\n");
                w.write("    attr.Visibility = \"" + aVis + "\"\n");
                if (aEstatico) w.write("    attr.IsStatic = True\n");
                w.write(
                        "    attr.LowerBound = \""
                                + escVbs(
                                        str(
                                                a.get("multiplicidad_minima") != null
                                                        ? a.get("multiplicidad_minima")
                                                        : "1"))
                                + "\"\n");
                w.write(
                        "    attr.UpperBound = \""
                                + escVbs(
                                        str(
                                                a.get("multiplicidad_maxima") != null
                                                        ? a.get("multiplicidad_maxima")
                                                        : "1"))
                                + "\"\n");
                w.write("    attr.Update()\n");
            }

            // Operaciones de la clase
            for (var o : opPorClase.getOrDefault(id, List.of())) {
                String oNombre = str(o.get("nombre"));
                String oRetorno = tipoVbs(str(o.get("tipo_retorno")));
                String oVis = visibilidadVbs(str(o.get("visibilidad")));
                boolean oAbstracto = bool(o.get("es_abstracto"));
                boolean oEstatico = bool(o.get("es_estatico"));

                w.write(
                        "    Set op = elem.Methods.AddNew(\""
                                + escVbs(oNombre)
                                + "\", \""
                                + escVbs(oRetorno)
                                + "\")\n");
                w.write("    op.Visibility = \"" + oVis + "\"\n");
                if (oAbstracto) w.write("    op.Abstract = True\n");
                if (oEstatico) w.write("    op.IsStatic = True\n");
                w.write("    op.Update()\n");

                // Parámetros de la operación
                for (var p : paramPorOp.getOrDefault(str(o.get("id")), List.of())) {
                    String pNombre = str(p.get("nombre"));
                    String pTipo = tipoVbs(str(p.get("tipo_uml")));
                    String pDir = direccionVbs(str(p.get("direccion")));

                    w.write(
                            "    Set param = op.Parameters.AddNew(\""
                                    + escVbs(pNombre)
                                    + "\", \""
                                    + escVbs(pTipo)
                                    + "\")\n");
                    w.write("    param.Kind = \"" + pDir + "\"\n");
                    w.write("    param.Update()\n");
                }
            }

            // Diagram Object (colocación visual en el diagrama)
            int left, top, right, bottom;
            if (posMap.containsKey(id)) {
                var pos = posMap.get(id);
                double x = pos.get("x") instanceof Number n ? n.doubleValue() : 80;
                double y = pos.get("y") instanceof Number n ? n.doubleValue() : 80;
                double wVal = pos.get("ancho") instanceof Number n ? n.doubleValue() : 180;
                double hVal = pos.get("alto") instanceof Number n ? n.doubleValue() : 120;
                left = (int) Math.round(x);
                top = (int) Math.round(y);
                right = left + (int) Math.round(wVal > 0 ? wVal : 180);
                bottom = top + (int) Math.round(hVal > 0 ? hVal : 120);
            } else {
                int col = i % 4;
                int row = i / 4;
                left = 80 + col * 260;
                top = 80 + row * 180;
                right = left + 180;
                bottom = top + 110;
            }

            w.write("    Set diagObj = diagrama.DiagramObjects.AddNew(\"\", \"\")\n");
            w.write("    diagObj.ElementID = elem.ElementID\n");
            w.write("    diagObj.Left = " + left + "\n");
            w.write("    diagObj.Right = " + right + "\n");
            w.write("    diagObj.Top = -" + top + "\n");
            w.write("    diagObj.Bottom = -" + bottom + "\n");
            w.write("    diagObj.Update()\n\n");
        }

        // Crear Conectores / Relaciones
        w.write("    ' --- Relaciones y Multiplicidades ---\n");
        for (var r : relaciones) {
            String idOrigen = str(r.get("id_clase_origen"));
            String idDestino = str(r.get("id_clase_destino"));
            String tipoRelacion = Objects.toString(r.get("tipo_relacion"), "ASOCIACION");
            String nombreRel = escVbs(str(r.get("nombre")));
            String rolOrigen = escVbs(str(r.get("rol_origen")));
            String rolDestino = escVbs(str(r.get("rol_destino")));
            String multOrigen =
                    escVbs(multiplicidadVbs(r.get("minimo_origen"), r.get("maximo_origen")));
            String multDestino =
                    escVbs(multiplicidadVbs(r.get("minimo_destino"), r.get("maximo_destino")));

            String tipoConectorEa =
                    switch (tipoRelacion) {
                        case "GENERALIZACION", "HERENCIA" -> "Generalization";
                        case "DEPENDENCIA" -> "Dependency";
                        case "REALIZACION" -> "Realisation";
                        default -> "Association";
                    };

            w.write(
                    "    If dictClases.Exists(\""
                            + escVbs(idOrigen)
                            + "\") And dictClases.Exists(\""
                            + escVbs(idDestino)
                            + "\") Then\n");
            w.write(
                    "        Set con = dictClases(\""
                            + escVbs(idOrigen)
                            + "\").Connectors.AddNew(\""
                            + nombreRel
                            + "\", \""
                            + tipoConectorEa
                            + "\")\n");
            w.write(
                    "        con.SupplierID = dictClases(\""
                            + escVbs(idDestino)
                            + "\").ElementID\n");
            if (!multOrigen.isBlank())
                w.write("        con.ClientEnd.Cardinality = \"" + multOrigen + "\"\n");
            if (!rolOrigen.isBlank())
                w.write("        con.ClientEnd.Role = \"" + rolOrigen + "\"\n");
            if (!multDestino.isBlank())
                w.write("        con.SupplierEnd.Cardinality = \"" + multDestino + "\"\n");
            if (!rolDestino.isBlank())
                w.write("        con.SupplierEnd.Role = \"" + rolDestino + "\"\n");

            if ("COMPOSICION".equals(tipoRelacion)) {
                w.write("        con.SupplierEnd.Aggregation = 2 ' Composite\n");
            } else if ("AGREGACION".equals(tipoRelacion)) {
                w.write("        con.SupplierEnd.Aggregation = 1 ' Shared\n");
            }
            w.write("        con.Update()\n");
            w.write("    End If\n");
        }

        w.write("\n    ' Refrescar y abrir diagrama\n");
        w.write("    diagrama.DiagramObjects.Refresh\n");
        w.write("    diagrama.DiagramLinks.Refresh\n");
        w.write("    diagrama.Update()\n");
        w.write("    repo.OpenDiagram(diagrama.DiagramID)\n");
        w.write("    repo.RefreshModelView(pkgNuevo.PackageID)\n");
        w.write("    \n");
        w.write(
                "    MsgBox \"Diagrama de clases '"
                        + escVbs(nombreModelo)
                        + "' creado exitosamente en Enterprise Architect.\" & vbCrLf & _\n");
        w.write(
                "           \"Total clases: \" & dictClases.Count, vbInformation, \"Plataforma UML"
                        + " - Exportación Exitosa\"\n");
        w.write("End Sub\n\n");
        w.write("GenerarDiagramaClases\n");

        return w.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String multiplicidadVbs(Object minObj, Object maxObj) {
        int min = minObj instanceof Number n ? n.intValue() : 1;
        if (maxObj == null) return min == 0 ? "0..*" : min == 1 ? "1..*" : min + "..*";
        int max = ((Number) maxObj).intValue();
        if (max < 0) return min == 0 ? "0..*" : min == 1 ? "1..*" : min + "..*";
        if (min == max) return String.valueOf(min);
        return min + ".." + max;
    }

    private String visibilidadVbs(String v) {
        if (v == null) return "Public";
        return switch (v) {
            case "PUBLICO" -> "Public";
            case "PRIVADO" -> "Private";
            case "PROTEGIDO" -> "Protected";
            case "PAQUETE" -> "Package";
            default -> "Public";
        };
    }

    private String direccionVbs(String d) {
        if (d == null) return "in";
        return switch (d) {
            case "ENTRADA" -> "in";
            case "SALIDA" -> "out";
            case "ENTRADA_SALIDA" -> "inout";
            default -> "in";
        };
    }

    private String tipoVbs(String t) {
        if (t == null || t.isBlank()) return "String";
        return t;
    }

    private String escVbs(String s) {
        if (s == null) return "";
        return s.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"");
    }

    private String escComentario(String s) {
        if (s == null) return "";
        return s.replace("\r", "").replace("\n", " ");
    }

    private Map<String, List<Map<String, Object>>> agrupar(
            List<Map<String, Object>> lista, String campo) {
        Map<String, List<Map<String, Object>>> mapa = new LinkedHashMap<>();
        for (var item : lista) {
            String clave = str(item.get(campo));
            if (clave != null) mapa.computeIfAbsent(clave, k -> new ArrayList<>()).add(item);
        }
        return mapa;
    }

    private List<Map<String, Object>> ordenarPaquetes(List<Map<String, Object>> paquetes) {
        Map<String, Map<String, Object>> pendientes = new LinkedHashMap<>();
        for (var paquete : paquetes) {
            String id = str(paquete.get("id"));
            if (id == null || pendientes.putIfAbsent(id, paquete) != null)
                throw new IllegalArgumentException(
                        "La jerarquía de paquetes contiene un ID inválido o repetido.");
        }
        List<Map<String, Object>> ordenados = new ArrayList<>(pendientes.size());
        while (!pendientes.isEmpty()) {
            boolean avance = false;
            var iterador = pendientes.entrySet().iterator();
            while (iterador.hasNext()) {
                var entrada = iterador.next();
                String padre = str(entrada.getValue().get("id_paquete_padre"));
                if (padre == null || !pendientes.containsKey(padre)) {
                    ordenados.add(entrada.getValue());
                    iterador.remove();
                    avance = true;
                }
            }
            if (!avance)
                throw new IllegalArgumentException("La jerarquía de paquetes contiene un ciclo.");
        }
        return ordenados;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }
}
