package bo.edu.uml.ia.servicio;

import bo.edu.uml.ia.servicio.AnalizadorModelo.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/**
 * Mentor de respaldo para cuando no hay proveedor generativo (o este falla). Responde con
 * conocimiento de ingeniería de software estructurado (guías de dominio, conceptos UML) cruzado con
 * el modelo real, los acuerdos confirmados y lo que dijeron otros integrantes. Es orientación
 * determinista: nunca produce operaciones aplicables ni se presenta como razonamiento generativo.
 */
public final class MentorLocal {
  private MentorLocal() {}

  record Entidad(String nombre, String proposito, String atributos, List<String> alias) {}

  record Enlace(String origen, String destino, String tipo, String multOrigen, String multDestino) {}

  record Guia(
      String titulo,
      List<String> palabras,
      String resumen,
      List<Entidad> entidades,
      List<String> relaciones,
      List<String> decisiones,
      List<String> reglas,
      List<Enlace> enlaces) {
    Guia(
        String titulo,
        List<String> palabras,
        String resumen,
        List<Entidad> entidades,
        List<String> relaciones,
        List<String> decisiones,
        List<String> reglas) {
      this(titulo, palabras, resumen, entidades, relaciones, decisiones, reglas, List.of());
    }
  }

  record Concepto(List<String> palabras, String titulo, String texto) {}

  private static Entidad e(String nombre, String proposito, String atributos, String... alias) {
    return new Entidad(nombre, proposito, atributos, List.of(alias));
  }

  private static Enlace rel(
      String origen, String destino, String tipo, String multOrigen, String multDestino) {
    return new Enlace(origen, destino, tipo, multOrigen, multDestino);
  }

  static final List<Guia> GUIAS =
      List.of(
          new Guia(
              "Inventarios",
              List.of("inventario", "stock", "existencia", "almacen", "bodega", "kardex"),
              "Un inventario responde «cuánto hay de qué y dónde», y sobre todo «por qué cambió».",
              List.of(
                  e("Producto", "Qué se almacena", "codigo, nombre, unidadMedida, stockMinimo, activo",
                      "producto", "articulo", "item", "insumo", "material"),
                  e("Categoria", "Agrupa productos", "nombre, descripcion", "categoria", "familia", "rubro"),
                  e("Almacen", "Lugar físico de resguardo", "nombre, ubicacion, activo",
                      "almacen", "bodega", "deposito"),
                  e("Existencia", "Cantidad de un producto en un almacén", "cantidad",
                      "existencia", "stock", "saldo"),
                  e("MovimientoStock", "Entradas, salidas, ajustes y transferencias",
                      "fecha, tipo, cantidad, motivo, responsable", "movimiento", "kardex"),
                  e("Lote", "Trazabilidad y vencimiento (opcional)", "numero, fechaVencimiento", "lote"),
                  e("Proveedor", "Origen de las entradas", "nombre, documento, contacto", "proveedor")),
              List.of(
                  "Categoria 1 — 0..* Producto (asociación)",
                  "Producto 1 — 0..* Existencia y Almacen 1 — 0..* Existencia: Existencia resuelve el"
                      + " muchos-a-muchos entre Producto y Almacen",
                  "Producto 1 — 0..* MovimientoStock; MovimientoStock *— 1 Almacen (origen/destino"
                      + " en transferencias)",
                  "Producto 1 — 0..* Lote, solo si hay vencimiento o trazabilidad"),
              List.of(
                  "¿Un solo almacén o varios? Cambia la necesidad de Existencia y de transferencias.",
                  "¿Se controla por lote o fecha de vencimiento?",
                  "¿Cómo se valora el costo: promedio ponderado, PEPS u otro?",
                  "¿Se permite stock negativo? ¿Quién autoriza los ajustes?",
                  "¿Hay unidades de medida y conversiones (caja/unidad)?"),
              List.of(
                  "El stock no se edita directamente: cambia solo mediante MovimientoStock (invariante"
                      + " de negocio).",
                  "Una salida mayor que la existencia debe rechazarse o requerir autorización.",
                  "Una transferencia es salida + entrada y debe ser atómica.",
                  "El aviso de stock mínimo es una regla; el diagrama solo aporta el atributo."),
              List.of(
                  rel("Categoria", "Producto", "ASOCIACION", "1", "0..*"),
                  rel("Producto", "Existencia", "ASOCIACION", "1", "0..*"),
                  rel("Almacen", "Existencia", "ASOCIACION", "1", "0..*"),
                  rel("Producto", "MovimientoStock", "ASOCIACION", "1", "0..*"),
                  rel("Almacen", "MovimientoStock", "ASOCIACION", "1", "0..*"),
                  rel("Producto", "Lote", "ASOCIACION", "1", "0..*"),
                  rel("Proveedor", "MovimientoStock", "ASOCIACION", "0..1", "0..*"))),
          new Guia(
              "Ventas y pedidos",
              List.of("venta", "pedido", "carrito", "cotizacion", "orden de venta", "facturacion"),
              "Una venta separa quién compra, qué compra y cuánto se pactó en ese momento.",
              List.of(
                  e("Cliente", "Quien compra", "nombre, documento, correo, telefono", "cliente"),
                  e("Pedido", "Cabecera de la venta", "fecha, estado, total", "pedido", "venta", "orden"),
                  e("DetallePedido", "Una línea del pedido", "cantidad, precioUnitario, subtotal",
                      "detalle", "linea"),
                  e("Producto", "Lo que se vende", "codigo, nombre, precio", "producto", "articulo", "plato"),
                  e("Pago", "Cobro del pedido", "monto, fecha, estado", "pago", "cobro"),
                  e("Factura", "Comprobante fiscal (opcional)", "numero, fecha, total", "factura", "comprobante")),
              List.of(
                  "Cliente 1 — 0..* Pedido (asociación)",
                  "Pedido 1 ◆ 1..* DetallePedido (composición: la línea no existe sin el pedido)",
                  "DetallePedido *— 1 Producto (asociación)",
                  "Pedido 1 — 0..* Pago y Pedido 1 — 0..1 Factura, según la política"),
              List.of(
                  "¿Se guarda el precio en el detalle (histórico) o se consulta el vigente?",
                  "¿Hay impuestos, descuentos o propinas?",
                  "¿Qué estados tiene un pedido y qué transiciones son válidas?",
                  "¿Se descuenta el inventario al confirmar o al despachar?"),
              List.of(
                  "total = suma de subtotales; subtotal = cantidad × precioUnitario.",
                  "Un pedido cerrado o pagado no se modifica.",
                  "precioUnitario se copia al detalle para conservar el histórico."),
              List.of(
                  rel("Cliente", "Pedido", "ASOCIACION", "1", "0..*"),
                  rel("Pedido", "DetallePedido", "COMPOSICION", "1", "1..*"),
                  rel("Producto", "DetallePedido", "ASOCIACION", "1", "0..*"),
                  rel("Pedido", "Pago", "ASOCIACION", "1", "0..*"),
                  rel("Pedido", "Factura", "ASOCIACION", "1", "0..1"))),
          new Guia(
              "Reservas",
              List.of("reserva", "turno", "cita", "agenda", "disponibilidad", "mesa", "habitacion"),
              "Una reserva liga a una persona con un recurso en un intervalo de tiempo.",
              List.of(
                  e("Cliente", "Quien reserva", "nombre, telefono, correo", "cliente"),
                  e("Reserva", "Compromiso de uso", "fechaInicio, fechaFin, estado, cantidadPersonas",
                      "reserva", "cita", "turno"),
                  e("Mesa", "Recurso reservable (o Sala, Habitación)", "codigo, capacidad, activa",
                      "mesa", "sala", "habitacion", "recurso"),
                  e("Anticipo", "Pago previo opcional", "monto, estado", "anticipo", "deposito")),
              List.of(
                  "Cliente 1 — 0..* Reserva (asociación)",
                  "Reserva *— 1 Mesa si cada reserva usa un solo recurso; si admite varios,"
                      + " introduce ReservaMesa (muchos-a-muchos con clase intermedia)",
                  "Reserva 1 — 0..1 Anticipo, solo si se exigen anticipos"),
              List.of(
                  "¿Una reserva puede incluir varios recursos?",
                  "¿Se exigen anticipos y cuál es la política de cancelación?",
                  "¿Hay tolerancia de llegada y estado «no se presentó»?"),
              List.of(
                  "Que no existan reservas superpuestas para un mismo recurso es una restricción de"
                      + " negocio: una asociación no la garantiza; requiere implementación y pruebas.",
                  "Los estados (PENDIENTE, CONFIRMADA, CANCELADA…) deben tener transiciones definidas."),
              List.of(
                  rel("Cliente", "Reserva", "ASOCIACION", "1", "0..*"),
                  rel("Mesa", "Reserva", "ASOCIACION", "1", "0..*"),
                  rel("Reserva", "Anticipo", "ASOCIACION", "1", "0..1"))),
          new Guia(
              "Pagos y cobros",
              List.of("pago", "cobro", "caja", "transaccion", "anticipo", "metodo de pago", "reembolso"),
              "Un pago debe poder explicarse: qué cubre, con qué medio y en qué estado quedó.",
              List.of(
                  e("Pago", "Cobro individual", "monto (BigDecimal), fecha, estado, referencia", "pago", "cobro", "transaccion"),
                  e("MetodoPago", "Efectivo, tarjeta, transferencia", "nombre, activo", "metodo"),
                  e("Comprobante", "Respaldo del cobro", "numero, fecha", "comprobante", "factura", "recibo"),
                  e("Reembolso", "Devolución parcial o total", "monto, motivo, fecha", "reembolso", "devolucion")),
              List.of(
                  "Pedido 1 — 0..* Pago (permite pagos parciales)",
                  "Pago *— 1 MetodoPago",
                  "Pago 1 — 0..* Reembolso"),
              List.of(
                  "¿Se admiten pagos parciales o anticipos? Evita que un pago quede asociado a dos"
                      + " conceptos distintos.",
                  "¿Hay varias monedas o propinas?",
                  "¿Cómo se concilia y quién autoriza un reembolso?"),
              List.of(
                  "Usa BigDecimal para dinero, no Double.",
                  "La suma de pagos no debe superar el total, salvo propina o vuelto definidos.",
                  "Reintentar un cobro no debe duplicarlo (idempotencia)."),
              List.of(
                  rel("MetodoPago", "Pago", "ASOCIACION", "1", "0..*"),
                  rel("Pago", "Comprobante", "ASOCIACION", "1", "0..1"),
                  rel("Pago", "Reembolso", "ASOCIACION", "1", "0..*"))),
          new Guia(
              "Usuarios, roles y permisos",
              List.of("usuario", "rol", "permiso", "login", "autenticacion", "acceso", "seguridad"),
              "Separa quién eres (autenticación) de qué puedes hacer (autorización).",
              List.of(
                  e("Usuario", "Persona con acceso", "nombreUsuario, correo, hashContrasena, estado", "usuario"),
                  e("Rol", "Conjunto de permisos", "nombre, descripcion", "rol", "perfil"),
                  e("Permiso", "Acción autorizable", "codigo, descripcion", "permiso", "privilegio")),
              List.of(
                  "Usuario *— * Rol (con clase intermedia si necesitas fecha o alcance por proyecto)",
                  "Rol *— * Permiso"),
              List.of(
                  "¿Un usuario puede tener varios roles?",
                  "¿Los permisos son globales o por proyecto/módulo?",
                  "¿Hay bloqueo de cuenta, recuperación de contraseña y auditoría?"),
              List.of(
                  "Nunca almacenes la contraseña en claro: solo su hash con sal.",
                  "Valida los permisos en el servidor, no solo en la interfaz."),
              List.of(
                  rel("Usuario", "Rol", "ASOCIACION", "0..*", "1..*"),
                  rel("Rol", "Permiso", "ASOCIACION", "0..*", "1..*"))),
          new Guia(
              "Compras y proveedores",
              List.of("compra", "proveedor", "abastecimiento", "orden de compra", "recepcion"),
              "Una compra termina en una recepción que alimenta el inventario.",
              List.of(
                  e("Proveedor", "Quien suministra", "nombre, documento, contacto", "proveedor"),
                  e("OrdenCompra", "Pedido al proveedor", "fecha, estado, total", "ordencompra", "compra"),
                  e("DetalleOrdenCompra", "Línea de la orden", "cantidad, costoUnitario", "detalleorden", "detallecompra"),
                  e("Recepcion", "Ingreso real de mercadería", "fecha, observaciones", "recepcion", "ingreso")),
              List.of(
                  "Proveedor 1 — 0..* OrdenCompra",
                  "OrdenCompra 1 ◆ 1..* DetalleOrdenCompra (composición)",
                  "OrdenCompra 1 — 0..* Recepcion (recepciones parciales)"),
              List.of(
                  "¿Se admiten recepciones parciales?",
                  "¿Quién aprueba una orden y con qué límites?"),
              List.of("La recepción debe generar movimientos de entrada en el inventario."),
              List.of(
                  rel("Proveedor", "OrdenCompra", "ASOCIACION", "1", "0..*"),
                  rel("OrdenCompra", "DetalleOrdenCompra", "COMPOSICION", "1", "1..*"),
                  rel("OrdenCompra", "Recepcion", "ASOCIACION", "1", "0..*"))),
          new Guia(
              "Fidelización y promociones",
              List.of("fidelizacion", "puntos", "cupon", "descuento", "promocion", "membresia"),
              "Fidelizar es registrar beneficios ganados y consumidos, con trazabilidad.",
              List.of(
                  e("Cliente", "Quien acumula beneficios", "nombre, correo", "cliente"),
                  e("CuentaPuntos", "Saldo de puntos", "saldo, nivel", "cuentapuntos", "membresia", "puntos"),
                  e("MovimientoPuntos", "Acumulación o canje", "fecha, puntos, motivo", "movimientopuntos"),
                  e("Promocion", "Regla de beneficio", "nombre, vigenciaDesde, vigenciaHasta", "promocion", "cupon")),
              List.of(
                  "Cliente 1 — 0..1 CuentaPuntos",
                  "CuentaPuntos 1 — 0..* MovimientoPuntos",
                  "Promocion *— * Producto o Categoria, según el alcance"),
              List.of(
                  "¿Los puntos se ganan por monto o por visita? ¿Vencen?",
                  "¿Las promociones se acumulan entre sí?"),
              List.of("El saldo de puntos debe derivarse de movimientos, no editarse a mano."),
              List.of(
                  rel("Cliente", "CuentaPuntos", "ASOCIACION", "1", "0..1"),
                  rel("CuentaPuntos", "MovimientoPuntos", "ASOCIACION", "1", "0..*"))),
          new Guia(
              "Administración de condominios y áreas comunes",
              List.of(
                  "condominio",
                  "edificio",
                  "copropiedad",
                  "residente",
                  "propietario",
                  "inquilino",
                  "area comun",
                  "areas comunes",
                  "expensa",
                  "invitado",
                  "visitante",
                  "porteria"),
              "Integra unidades habitacionales, residentes, reservas de áreas comunes, pagos/expensas y control de invitados.",
              List.of(
                  e("Condominio", "Edificio o conjunto residencial", "nombre, direccion, telefono", "condominio", "edificio"),
                  e("UnidadHabitacional", "Departamento, casa o lote", "codigo, piso, alicuota, estado", "unidad", "departamento", "apartamento", "casa"),
                  e("Residente", "Propietario o inquilino habitante", "nombre, documento, telefono, correo, tipo", "residente", "propietario", "inquilino", "vecino"),
                  e("AreaComun", "Salón, parrilla, piscina o cancha reservable", "nombre, capacidad, costoReserva, requierePago, activa", "areacomun", "salon", "parrilla", "piscina"),
                  e("ReservaAreaComun", "Solicitud de uso de área común en fecha y horario", "fecha, horaInicio, horaFin, estado, cantidadAsistentes", "reservaarea", "reserva"),
                  e("PagoServicio", "Cobro de expensa, mantenimiento o reserva", "concepto, monto, fechaPago, metodoPago, estado", "pagoservicio", "expensa", "cuota", "pago"),
                  e("Invitado", "Visitante autorizado para unidad o evento", "nombreCompleto, documentoIdentidad, fechaIngreso, autorizado", "invitado", "visitante", "visita")),
              List.of(
                  "Condominio 1 ◆ 1..* UnidadHabitacional (composición)",
                  "Condominio 1 ◆ 0..* AreaComun (composición)",
                  "UnidadHabitacional 1 — 1..* Residente (asociación)",
                  "Residente 1 — 0..* ReservaAreaComun y AreaComun 1 — 0..* ReservaAreaComun",
                  "UnidadHabitacional 1 — 0..* PagoServicio y ReservaAreaComun 0..1 — 0..* PagoServicio",
                  "Residente 1 — 0..* Invitado y ReservaAreaComun 0..1 — 0..* Invitado"),
              List.of(
                  "¿Las reservas de áreas comunes exigen estar al día en expensas (PagoServicio)?",
                  "¿Los invitados se asocian a una ReservaAreaComun (evento) o directamente al Residente?",
                  "¿El cobro de expensas se calcula por alícuota o monto fijo por unidad?"),
              List.of(
                  "No deben solaparse dos ReservaAreaComun confirmadas para la misma AreaComun en el mismo horario.",
                  "La suma de invitados de un evento no debe superar la capacidad del AreaComun."),
              List.of(
                  rel("Condominio", "UnidadHabitacional", "COMPOSICION", "1", "1..*"),
                  rel("Condominio", "AreaComun", "COMPOSICION", "1", "0..*"),
                  rel("UnidadHabitacional", "Residente", "ASOCIACION", "1", "1..*"),
                  rel("Residente", "ReservaAreaComun", "ASOCIACION", "1", "0..*"),
                  rel("AreaComun", "ReservaAreaComun", "ASOCIACION", "1", "0..*"),
                  rel("UnidadHabitacional", "PagoServicio", "ASOCIACION", "1", "0..*"),
                  rel("ReservaAreaComun", "PagoServicio", "ASOCIACION", "0..1", "0..*"),
                  rel("Residente", "Invitado", "ASOCIACION", "1", "0..*"),
                  rel("ReservaAreaComun", "Invitado", "ASOCIACION", "0..1", "0..*"))));

  static final List<Concepto> CONCEPTOS =
      List.of(
          new Concepto(
              List.of("composicion"),
              "Composición",
              "Todo-parte fuerte: la parte no existe sin el todo y se elimina con él. En composición el"
                  + " origen es el conjunto y el destino la parte; la multiplicidad máxima del"
                  + " conjunto es 1. Ejemplo: Pedido 1 ◆ 1..* DetallePedido. La exclusividad es por"
                  + " instancia, no por tipo de clase."),
          new Concepto(
              List.of("agregacion"),
              "Agregación",
              "Todo-parte débil: la parte puede existir sin el todo (Equipo ◇ Jugador). UML 2.5.1 no"
                  + " le da semántica adicional; úsala solo si comunica una intención útil al equipo."),
          new Concepto(
              List.of("generaliz", "herencia", "subclase", "superclase"),
              "Generalización",
              "Relación «es un»: el origen es la subclase y el destino la superclase; no se permiten"
                  + " ciclos. Úsala solo si las subclases comparten atributos o comportamiento reales;"
                  + " si solo difieren en un valor, prefiere un atributo tipo/estado."),
          new Concepto(
              List.of("dependencia"),
              "Dependencia",
              "Uso temporal: el cliente (origen) necesita al proveedor (destino) para operar, por"
                  + " ejemplo como parámetro o servicio. No implica atributo ni persistencia."),
          new Concepto(
              List.of("multiplicidad", "cardinalidad"),
              "Multiplicidad",
              "Responde a «¿cuántos?» en cada extremo: 1, 0..1, 1..*, 0..*. Léela en ambos sentidos"
                  + " («un Cliente tiene 0..* Pedidos; un Pedido tiene exactamente 1 Cliente»). El"
                  + " mínimo expresa obligatoriedad; el máximo, el límite."),
          new Concepto(
              List.of("intermedia", "asociativa", "muchos a muchos"),
              "Clase intermedia",
              "Una relación muchos-a-muchos con datos propios (fecha, cantidad, estado) se modela con"
                  + " una clase intermedia: Estudiante 1—0..* Inscripcion *—1 Curso."),
          new Concepto(
              List.of("abstract"),
              "Clase abstracta",
              "No se instancia directamente: existe para compartir estructura entre subclases."
                  + " Márcala abstracta solo si hay al menos dos subclases concretas."));

  public static boolean reconoceTema(String peticion) {
    String p = AnalizadorModelo.normalizar(peticion);
    return !guiasMencionadas(p).isEmpty()
        || CONCEPTOS.stream().anyMatch(c -> menciona(p, c.palabras()))
        || intencionDe(p) != Intencion.GENERAL;
  }

  enum Intencion {
    FALTA,
    RELACION,
    DESARROLLAR,
    GENERAL
  }

  static Intencion intencionDe(String p) {
    if (contieneAlguna(p, "relacion", "asociacion", "cardinalidad", "multiplicidad", "relacionar", "vincul", "conectar"))
      return Intencion.RELACION;
    if (contieneAlguna(p, "desarroll", "modulo", "implement", "disen", "por donde", "como empiezo", "como armo", "como construyo"))
      return Intencion.DESARROLLAR;
    if (contieneAlguna(p, "falta", "mejor", "adecuad", "complet", "revisa", "analiza", "evalua", "correcto", "esta bien", "que mas", "que anad", "que agreg", "sugier", "recomiend"))
      return Intencion.FALTA;
    return Intencion.GENERAL;
  }

  /** Coincidencia por inicio de palabra: «venta» no coincide con «inventario». */
  private static boolean menciona(String p, String palabra) {
    return java.util.regex.Pattern.compile("(^|[^a-z0-9])" + java.util.regex.Pattern.quote(palabra))
        .matcher(p)
        .find();
  }

  private static boolean menciona(String p, List<String> palabras) {
    for (String w : palabras) if (menciona(p, w)) return true;
    return false;
  }

  private static boolean contieneAlguna(String p, String... partes) {
    for (String x : partes) if (menciona(p, x)) return true;
    return false;
  }

  private static List<Guia> guiasMencionadas(String p) {
    List<Guia> r = new ArrayList<>();
    for (Guia g : GUIAS)
      if (menciona(p, g.palabras())) r.add(g);
    return r.size() > 3 ? r.subList(0, 3) : r;
  }

  /**
   * Sintetiza un JSON de diagrama UML cuando el usuario solicita generar o desarrollar un módulo o
   * dominio reconocido (por ejemplo condominio, reservas, pagos, inventarios, ventas) y el
   * proveedor generativo remoto no está disponible.
   */
  public static Optional<String> sintetizarDiagramaJson(
      ObjectMapper json, String peticion, Analisis a) {
    String p = AnalizadorModelo.normalizar(peticion);
    List<Guia> guias = guiasMencionadas(p);
    if (guias.isEmpty()) return Optional.empty();
    // Si ya menciona explícitamente Administración de condominios y también palabras genéricas de
    // reserva/pago que están cubiertas en el condominio, priorizar el dominio de condominios para no
    // duplicar ReservaAreaComun con Reserva/Mesa de restaurante.
    boolean tieneCondominio =
        guias.stream().anyMatch(g -> g.titulo().startsWith("Administración de condominios"));
    if (tieneCondominio) {
      guias =
          guias.stream()
              .filter(
                  g ->
                      g.titulo().startsWith("Administración de condominios")
                          || (!"Reservas".equals(g.titulo()) && !"Pagos y cobros".equals(g.titulo())))
              .toList();
    }

    ObjectNode raiz = json.createObjectNode();
    String titulos = String.join(" + ", guias.stream().map(Guia::titulo).toList());
    raiz.put("resumen", "Diseño de dominio generado para: " + titulos);
    ArrayNode clasesNode = raiz.putArray("clases");
    ArrayNode relacionesNode = raiz.putArray("relaciones");

    Map<String, ObjectNode> clasesPorClave = new LinkedHashMap<>();
    Map<String, Set<String>> atributosPorClase = new LinkedHashMap<>();
    Map<String, String> nombreRealPorEntidad = new LinkedHashMap<>();

    for (Guia g : guias) {
      for (Entidad en : g.entidades()) {
        Optional<ClaseInfo> ex = existente(a, en);
        String nombreFinal = ex.map(ClaseInfo::nombre).orElse(en.nombre());
        nombreRealPorEntidad.put(en.nombre(), nombreFinal);
        String clave = AnalizadorModelo.normalizar(nombreFinal);

        ObjectNode nodoClase = clasesPorClave.get(clave);
        if (nodoClase == null) {
          nodoClase = json.createObjectNode();
          nodoClase.put("nombre", nombreFinal);
          nodoClase.put("visibilidad", "PUBLICO");
          nodoClase.putArray("atributos");
          nodoClase.putArray("operaciones");
          clasesPorClave.put(clave, nodoClase);
          atributosPorClase.put(clave, new LinkedHashSet<>());
          clasesNode.add(nodoClase);
        }
        ArrayNode attrs = (ArrayNode) nodoClase.get("atributos");
        Set<String> yaAgregados = atributosPorClase.get(clave);
        for (String rawAttr : en.atributos().split(",")) {
          String limpio = rawAttr.replaceAll("\\(.*?\\)", "").trim();
          if (limpio.isEmpty()) continue;
          String claveAttr = AnalizadorModelo.normalizar(limpio);
          if (!yaAgregados.add(claveAttr)) continue;
          ObjectNode attrNode = attrs.addObject();
          attrNode.put("nombre", limpio);
          attrNode.put("tipo", tipoUmlAtributo(limpio, rawAttr));
          attrNode.put("visibilidad", "PRIVADO");
          attrNode.put("esIdentificador", "id".equalsIgnoreCase(limpio));
        }
      }
    }

    Set<String> paresRelacion = new HashSet<>();
    for (RelacionInfo r : a.relaciones()) {
      paresRelacion.add(
          AnalizadorModelo.normalizar(r.origen()) + "->" + AnalizadorModelo.normalizar(r.destino()));
      paresRelacion.add(
          AnalizadorModelo.normalizar(r.destino()) + "->" + AnalizadorModelo.normalizar(r.origen()));
    }

    for (Guia g : guias) {
      for (Enlace rel : g.enlaces()) {
        String orig = nombreRealPorEntidad.getOrDefault(rel.origen(), rel.origen());
        String dest = nombreRealPorEntidad.getOrDefault(rel.destino(), rel.destino());
        String k1 = AnalizadorModelo.normalizar(orig) + "->" + AnalizadorModelo.normalizar(dest);
        String k2 = AnalizadorModelo.normalizar(dest) + "->" + AnalizadorModelo.normalizar(orig);
        if (!clasesPorClave.containsKey(AnalizadorModelo.normalizar(orig))
            || !clasesPorClave.containsKey(AnalizadorModelo.normalizar(dest))) continue;
        if (!paresRelacion.add(k1)) continue;
        paresRelacion.add(k2);
        ObjectNode relNode = relacionesNode.addObject();
        relNode.put("origen", orig);
        relNode.put("destino", dest);
        relNode.put("tipo", rel.tipo());
        relNode.put("multiplicidadOrigen", rel.multOrigen());
        relNode.put("multiplicidadDestino", rel.multDestino());
      }
    }
    return clasesNode.isEmpty() ? Optional.empty() : Optional.of(raiz.toString());
  }

  private static String tipoUmlAtributo(String nombre, String raw) {
    if (raw.contains("BigDecimal")) return "BigDecimal";
    String n = AnalizadorModelo.normalizar(nombre);
    if (n.equals("id")) return "Long";
    if (n.contains("monto")
        || n.contains("precio")
        || n.contains("costo")
        || n.contains("total")
        || n.contains("subtotal")
        || n.contains("saldo")
        || n.contains("alicuota")) return "BigDecimal";
    if (n.contains("cantidad")
        || n.contains("capacidad")
        || n.contains("piso")
        || n.contains("stock")
        || n.contains("puntos")
        || n.equals("numero")) return "Integer";
    if (n.startsWith("activ") || n.startsWith("requiere") || n.startsWith("autoriz"))
      return "Boolean";
    if (n.contains("fechahora") || n.contains("fechaingreso") || n.contains("fechainicio") || n.contains("fechafin"))
      return "LocalDateTime";
    if (n.contains("fecha") || n.contains("vigencia")) return "LocalDate";
    return "String";
  }

  /** Guía del dominio que mejor coincide con las clases existentes (al menos dos coincidencias). */
  private static Optional<Guia> inferirGuia(Analisis a) {
    Guia mejor = null;
    int max = 1;
    for (Guia g : GUIAS) {
      int n = (int) g.entidades().stream().filter(en -> existente(a, en).isPresent()).count();
      if (n > max) {
        max = n;
        mejor = g;
      }
    }
    return Optional.ofNullable(mejor);
  }

  private static Optional<ClaseInfo> existente(Analisis a, Entidad en) {
    for (ClaseInfo c : a.clases()) {
      String n = AnalizadorModelo.normalizar(c.nombre());
      for (String alias : en.alias())
        if (n.equals(alias) || n.equals(alias + "s") || (alias.length() >= 5 && n.contains(alias)))
          return Optional.of(c);
    }
    return Optional.empty();
  }

  public static String responder(String peticion, Analisis a, long revision, JsonNode datos) {
    String p = AnalizadorModelo.normalizar(peticion);
    Intencion intencion = intencionDe(p);
    List<ClaseInfo> enfoque = AnalizadorModelo.enfoque(a, peticion);
    List<Guia> guias = new ArrayList<>(guiasMencionadas(p));
    boolean inferida = false;
    if (guias.isEmpty() && !a.vacio() && enfoque.isEmpty()
        && (intencion == Intencion.FALTA || intencion == Intencion.GENERAL)) {
      var g = inferirGuia(a);
      if (g.isPresent()) {
        guias.add(g.get());
        inferida = true;
      }
    }
    StringBuilder t = new StringBuilder();
    t.append("**Modo local** (sin proveedor generativo): respondo con criterios de ingeniería de")
        .append(" software y con lo que ya existe en el modelo (revisión ")
        .append(revision)
        .append("). Es orientación, no una propuesta aplicable.\n\n");

    estadoModelo(t, a);
    if (!enfoque.isEmpty()) elementoEnFoco(t, a, enfoque, intencion);

    for (Concepto c : CONCEPTOS)
      if (menciona(p, c.palabras()))
        t.append("### ").append(c.titulo()).append("\n").append(c.texto()).append("\n\n");
    if (intencion == Intencion.RELACION && enfoque.isEmpty()
        && CONCEPTOS.stream().noneMatch(c -> menciona(p, c.palabras())))
      guiaRelaciones(t);

    for (Guia g : guias) guiaDominio(t, g, a, inferida);

    hallazgos(t, a, enfoque);
    contextoEquipo(t, datos);
    siguientesPasos(t, a, enfoque, guias, intencion);
    return t.toString().stripTrailing();
  }

  private static void estadoModelo(StringBuilder t, Analisis a) {
    t.append("### Estado actual del modelo\n");
    if (a.vacio()) {
      t.append("El modelo está vacío: aún no hay clases ni relaciones que analizar.\n\n");
      return;
    }
    t.append("- ")
        .append(a.clases().size())
        .append(a.clases().size() == 1 ? " clase y " : " clases y ")
        .append(a.relaciones().size())
        .append(a.relaciones().size() == 1 ? " relación.\n" : " relaciones.\n");
    StringBuilder lista = new StringBuilder();
    for (ClaseInfo c : a.clases().stream().limit(25).toList()) {
      if (!lista.isEmpty()) lista.append(", ");
      lista.append(c.nombre()).append(" (").append(c.atributos().size()).append(" atrib.");
      if (!c.operaciones().isEmpty()) lista.append(", ").append(c.operaciones().size()).append(" oper.");
      lista.append(")");
    }
    if (a.clases().size() > 25) lista.append(", …");
    t.append("- Clases: ").append(lista).append(".\n\n");
  }

  private static void elementoEnFoco(
      StringBuilder t, Analisis a, List<ClaseInfo> enfoque, Intencion intencion) {
    for (ClaseInfo c : enfoque) {
      t.append("### Sobre «").append(c.nombre()).append("»\n");
      if (!c.atributos().isEmpty())
        t.append("- Atributos: ").append(String.join("; ", c.atributos())).append(".\n");
      if (!c.operaciones().isEmpty())
        t.append("- Operaciones: ").append(String.join("; ", c.operaciones())).append(".\n");
      var rels = a.relacionesDe(c);
      if (rels.isEmpty()) t.append("- No participa en ninguna relación.\n");
      for (RelacionInfo r : rels) {
        t.append("- ").append(r.descripcion()).append(". ").append(lecturaRelacion(r)).append("\n");
      }
      t.append("\n");
    }
    if (enfoque.size() >= 2) {
      for (int i = 0; i < enfoque.size(); i++)
        for (int j = i + 1; j < enfoque.size(); j++) {
          ClaseInfo x = enfoque.get(i), y = enfoque.get(j);
          boolean directa =
              a.relaciones().stream()
                  .anyMatch(
                      r ->
                          (r.idOrigen().equals(x.id()) && r.idDestino().equals(y.id()))
                              || (r.idOrigen().equals(y.id()) && r.idDestino().equals(x.id())));
          if (!directa) {
            t.append("### ¿Cómo relacionar «")
                .append(x.nombre())
                .append("» y «")
                .append(y.nombre())
                .append("»?\n")
                .append("No hay relación directa entre ambas. Antes de crearla, responde:\n")
                .append("1. ¿Una ").append(x.nombre()).append(" necesita conocer a ").append(y.nombre())
                .append(", o es al revés (dirección)?\n")
                .append("2. ¿Cuántas de cada una participan (1, 0..1, 1..*, 0..*)?\n")
                .append("3. ¿Si desaparece una, desaparece la otra? Si sí, es composición; si no,")
                .append(" asociación.\n")
                .append("4. ¿La relación tiene datos propios (fecha, cantidad, estado)? Entonces")
                .append(" necesita una clase intermedia.\n")
                .append("Comando de ejemplo una vez decidido: «Crea relación entre ")
                .append(x.nombre()).append(" y ").append(y.nombre())
                .append(" de tipo ASOCIACION con cardinalidad 1 a *».\n\n");
          }
        }
    }
    if (intencion == Intencion.RELACION) guiaRelaciones(t);
  }

  private static String lecturaRelacion(RelacionInfo r) {
    return switch (r.tipo()) {
      case "COMPOSICION" ->
          "Composición: «" + r.destino() + "» no existe sin «" + r.origen() + "» y se elimina con ella.";
      case "AGREGACION" ->
          "Agregación: «" + r.destino() + "» puede existir sin «" + r.origen() + "».";
      case "GENERALIZACION" ->
          "«" + r.origen() + "» es una especialización de «" + r.destino() + "».";
      case "DEPENDENCIA" ->
          "«" + r.origen() + "» usa «" + r.destino() + "» sin conservarla como atributo.";
      default ->
          r.maximoOrigenIlimitado() && r.maximoDestinoIlimitado() && !r.sinMultiplicidad()
              ? "Es muchos-a-muchos: ¿tiene datos propios? Si sí, usa una clase intermedia."
              : "Asociación: revisa que la multiplicidad se lea bien en ambos sentidos.";
    };
  }

  private static void guiaRelaciones(StringBuilder t) {
    t.append("### Cómo elegir el tipo de relación\n")
        .append("- **Asociación**: A usa o conoce a B y sus ciclos de vida son independientes")
        .append(" (Cliente — Pedido).\n")
        .append("- **Agregación**: todo-parte débil; úsala solo si comunica intención.\n")
        .append("- **Composición**: la parte depende del todo (Pedido ◆ DetallePedido); el máximo del")
        .append(" origen es 1.\n")
        .append("- **Generalización**: «es un»; origen subclase, destino superclase, solo con")
        .append(" estructura o comportamiento compartido.\n")
        .append("- **Dependencia**: uso temporal, sin atributo.\n\n");
  }

  private static void guiaDominio(StringBuilder t, Guia g, Analisis a, boolean inferida) {
    t.append("### Guía de referencia: ").append(g.titulo()).append("\n");
    if (inferida)
      t.append("_Las clases que ya tienes se parecen a este dominio; la uso como referencia._\n");
    t.append(g.resumen()).append("\n\n");
    t.append("| Clase típica | Para qué sirve | Atributos clave | En tu modelo |\n|---|---|---|---|\n");
    List<String> faltantes = new ArrayList<>();
    for (Entidad en : g.entidades()) {
      var ex = existente(a, en);
      t.append("| ").append(en.nombre()).append(" | ").append(en.proposito()).append(" | ")
          .append(en.atributos()).append(" | ")
          .append(ex.map(c -> "✅ «" + c.nombre() + "»").orElse("⬜ pendiente")).append(" |\n");
      if (ex.isEmpty()) faltantes.add(en.nombre());
    }
    t.append("\n**Relaciones habituales**\n");
    g.relaciones().forEach(r -> t.append("- ").append(r).append("\n"));
    t.append("\n**Decisiones que el equipo debe tomar antes de cerrar el diseño**\n");
    g.decisiones().forEach(d -> t.append("- ").append(d).append("\n"));
    t.append("\n**Reglas de negocio (no las garantiza el diagrama por sí solo)**\n");
    g.reglas().forEach(r -> t.append("- ").append(r).append("\n"));
    if (!faltantes.isEmpty() && faltantes.size() < g.entidades().size())
      t.append("\nTe faltaría considerar: ").append(String.join(", ", faltantes)).append(".\n");
    t.append("\n");
  }

  private static void hallazgos(StringBuilder t, Analisis a, List<ClaseInfo> enfoque) {
    if (a.vacio() || a.hallazgos().isEmpty()) return;
    Set<String> nombres = new HashSet<>();
    enfoque.forEach(c -> nombres.add(c.nombre()));
    List<Hallazgo> lista =
        a.hallazgos().stream()
            .filter(h -> nombres.isEmpty() || h.clase() == null || nombres.contains(h.clase()))
            .sorted(Comparator.comparingInt(h -> peso(h.severidad())))
            .limit(8)
            .toList();
    if (lista.isEmpty()) return;
    t.append("### Observaciones de diseño sobre el modelo actual\n");
    for (Hallazgo h : lista)
      t.append("- [").append(h.severidad()).append("] ").append(h.mensaje()).append("\n");
    t.append("\n");
  }

  private static int peso(String s) {
    return switch (s) {
      case "ERROR" -> 0;
      case "ADVERTENCIA" -> 1;
      case "SUGERENCIA" -> 2;
      default -> 3;
    };
  }

  private static void contextoEquipo(StringBuilder t, JsonNode datos) {
    if (datos == null) return;
    String solicitante = datos.path("solicitante").asText("");
    StringBuilder b = new StringBuilder();
    if (!solicitante.isBlank()) {
      b.append("- Consulta realizada por **").append(solicitante).append("**.\n");
    }
    for (JsonNode ac : datos.path("acuerdos"))
      b.append("- **Acuerdo confirmado por ")
          .append(ac.path("nombreAutor").asText("el equipo"))
          .append("**: ")
          .append(ac.path("contenido").asText())
          .append("\n");
    for (JsonNode act : datos.path("actividadSimultanea")) {
      String autorAct = act.path("nombreAutor").asText("");
      String detalleAct = act.path("detalle").asText("");
      if (!detalleAct.isBlank()) {
        b.append("- Actividad simultánea")
            .append(autorAct.isBlank() ? "" : " de **" + autorAct + "**")
            .append(": ")
            .append(detalleAct)
            .append("\n");
      }
    }
    Set<String> vistos = new HashSet<>();
    int n = 0;
    List<JsonNode> candidatos = new ArrayList<>();
    datos.path("antecedentes").forEach(candidatos::add);
    List<JsonNode> recientes = new ArrayList<>();
    datos.path("mensajes").forEach(recientes::add);
    Collections.reverse(recientes);
    candidatos.addAll(recientes);
    for (JsonNode m : candidatos) {
      String autor = m.has("nombreAutor") ? m.path("nombreAutor").asText() : m.path("nombre_autor").asText("");
      String emisor = m.path("emisor").asText("USUARIO");
      String texto = m.path("texto").asText("").strip();
      if (!"USUARIO".equals(emisor) || autor.isBlank() || autor.equals(solicitante) || texto.isEmpty())
        continue;
      if (!vistos.add(texto) || n >= 3) continue;
      b.append("- ").append(autor).append(" comentó: «")
          .append(texto.length() > 180 ? texto.substring(0, 180) + "…" : texto)
          .append("» (opinión del equipo, no un acuerdo confirmado).\n");
      n++;
    }
    if (!b.isEmpty()) t.append("### Lo que el equipo ya aportó\n").append(b).append("\n");
  }

  private static void siguientesPasos(
      StringBuilder t, Analisis a, List<ClaseInfo> enfoque, List<Guia> guias, Intencion intencion) {
    t.append("### Siguientes pasos sugeridos\n");
    int n = 1;
    if (a.vacio()) {
      t.append(n++).append(". Define el núcleo del dominio: 3 a 5 clases principales y qué las relaciona.\n");
      t.append(n++).append(". Pide una por una con un comando concreto, por ejemplo «Crea clase Producto con")
          .append(" nombre: String, precio: BigDecimal».\n");
    } else {
      boolean error = a.hallazgos().stream().anyMatch(h -> "ERROR".equals(h.severidad()));
      if (error) t.append(n++).append(". Corrige primero los hallazgos marcados [ERROR].\n");
      if (a.hallazgos().stream().anyMatch(h -> "CLASE_AISLADA".equals(h.codigo())))
        t.append(n++).append(". Conecta las clases aisladas o justifica por qué están solas.\n");
      if (a.hallazgos().stream().anyMatch(h -> "CLASE_SIN_ATRIBUTOS".equals(h.codigo())))
        t.append(n++).append(". Completa los atributos de las clases vacías.\n");
      if (!guias.isEmpty())
        t.append(n++).append(". Revisa con el equipo las decisiones pendientes de la guía y confírmalas")
            .append(" como acuerdos para que el agente las recuerde.\n");
    }
    t.append(n).append(". Para aplicar cambios usa órdenes concretas («Agrega…», «Crea relación…»); para")
        .append(" razonamiento abierto con contexto completo configura el proveedor generativo.\n");
  }
}

