import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'api.dart';

String visibility(dynamic value) =>
    const {
      'PUBLICO': '+',
      'PRIVADO': '−',
      'PROTEGIDO': '#',
      'PAQUETE': '~',
    }[value] ??
    '';
String multiplicity(dynamic min, dynamic max) => min == null
    ? ''
    : min == max
    ? '$min'
    : '$min..${max ?? '*'}';

// Campos ausentes en proyectos anteriores conservan los valores predeterminados UML.
String collectionModifiers(Json a) =>
    '${a['es_ordenado'] == true ? ' {ordered}' : ''}${a['coleccion_unica'] == false ? ' {nonunique}' : ''}';
String parameterDirection(dynamic d) =>
    const {
      'SALIDA': 'out',
      'ENTRADA_SALIDA': 'inout',
      'RETORNO': 'return',
    }[d] ??
    'in';

String returnType(Json model, Json operation) =>
    (operation['tipo_retorno'] ??
            rows(model['parametros'])
                .where(
                  (p) =>
                      p['id_operacion'] == operation['id'] &&
                      p['direccion'] == 'RETORNO',
                )
                .map((p) => p['tipo_uml'])
                .firstOrNull ??
            'void')
        .toString();

class DiagramView extends StatefulWidget {
  const DiagramView({super.key, required this.model, required this.onClass});
  final Json model;
  final ValueChanged<Json> onClass;
  @override
  State<DiagramView> createState() => _DiagramViewState();
}

class _DiagramViewState extends State<DiagramView> {
  final transform = TransformationController();
  final search = TextEditingController();
  Size viewport = Size.zero;
  bool fitted = false, searching = false;
  Map<String, Rect> positions = {};
  @override
  void dispose() {
    transform.dispose();
    search.dispose();
    super.dispose();
  }

  Map<String, Rect> layout(List<Json> classes) {
    final result = <String, Rect>{};
    double minX = 0, minY = 0;
    for (final c in classes) {
      minX = math.min(minX, (c['x'] as num? ?? 0).toDouble());
      minY = math.min(minY, (c['y'] as num? ?? 0).toDouble());
    }
    for (var i = 0; i < classes.length; i++) {
      final c = classes[i];
      final x = (c['x'] as num? ?? (i % 3) * 330).toDouble() - minX + 70;
      final y = (c['y'] as num? ?? (i ~/ 3) * 290).toDouble() - minY + 70;
      final fields = rows(
        widget.model['atributos'],
      ).where((a) => a['id_clase'] == c['id']).length;
      final methods = rows(
        widget.model['operaciones'],
      ).where((a) => a['id_clase'] == c['id']).length;
      result[c['id'].toString()] = Rect.fromLTWH(
        x,
        y,
        math.max(250, (c['ancho'] as num? ?? 250).toDouble()),
        88 + math.min(fields, 5) * 22 + math.min(methods, 3) * 22,
      );
    }
    return result;
  }

  Size get bounds => Size(
    positions.values.fold<double>(360, (v, r) => math.max(v, r.right + 80)),
    positions.values.fold<double>(500, (v, r) => math.max(v, r.bottom + 80)),
  );
  void fit() {
    if (viewport.isEmpty) return;
    final scale = math
        .min(viewport.width / bounds.width, viewport.height / bounds.height)
        .clamp(0.025, 1.0);
    transform.value = Matrix4.identity()
      ..translateByDouble(
        (viewport.width - bounds.width * scale) / 2,
        (viewport.height - bounds.height * scale) / 2,
        0,
        1,
      )
      ..scaleByDouble(scale, scale, 1, 1);
  }

  void focus(Json c) {
    final rect = positions[c['id']];
    if (rect == null) return;
    const scale = 1.0;
    transform.value = Matrix4.identity()
      ..translateByDouble(
        viewport.width / 2 - rect.center.dx * scale,
        viewport.height / 2 - rect.center.dy * scale,
        0,
        1,
      );
  }

  @override
  Widget build(BuildContext context) {
    final classes = rows(widget.model['clases']);
    positions = layout(classes);
    if (classes.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(30),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.account_tree_outlined,
                size: 64,
                color: Color(0xFF176B52),
              ),
              SizedBox(height: 20),
              Text(
                'Aquí empieza tu modelo',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
              ),
              SizedBox(height: 10),
              Text(
                'Abre el agente y describe tu sistema o toma una foto de un diagrama.',
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      );
    }
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: TextField(
            controller: search,
            onChanged: (v) => setState(() => searching = v.isNotEmpty),
            decoration: InputDecoration(
              isDense: true,
              hintText: 'Buscar una clase',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: search.text.isEmpty
                  ? null
                  : IconButton(
                      onPressed: () {
                        search.clear();
                        setState(() => searching = false);
                      },
                      icon: const Icon(Icons.close),
                    ),
            ),
          ),
        ),
        if (searching)
          ConstrainedBox(
            constraints: const BoxConstraints(maxHeight: 170),
            child: ListView(
              shrinkWrap: true,
              children: classes
                  .where(
                    (c) => c['nombre'].toString().toLowerCase().contains(
                      search.text.toLowerCase(),
                    ),
                  )
                  .map(
                    (c) => ListTile(
                      dense: true,
                      title: Text('${c['nombre']}'),
                      trailing: const Icon(Icons.center_focus_strong),
                      onTap: () {
                        focus(c);
                        search.clear();
                        FocusScope.of(context).unfocus();
                        setState(() => searching = false);
                      },
                    ),
                  )
                  .toList(),
            ),
          ),
        Expanded(
          child: LayoutBuilder(
            builder: (context, constraints) {
              viewport = Size(constraints.maxWidth, constraints.maxHeight);
              if (!fitted) {
                fitted = true;
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (mounted) fit();
                });
              }
              return Stack(
                children: [
                  Positioned.fill(
                    child: InteractiveViewer(
                      transformationController: transform,
                      constrained: false,
                      minScale: 0.025,
                      maxScale: 3,
                      boundaryMargin: const EdgeInsets.all(1000),
                      child: SizedBox(
                        width: bounds.width,
                        height: bounds.height,
                        child: Stack(
                          children: [
                            Positioned.fill(
                              child: CustomPaint(
                                painter: RelationsPainter(
                                  rows(widget.model['relaciones']),
                                  positions,
                                ),
                              ),
                            ),
                            ...classes.map((c) {
                              final r = positions[c['id']]!;
                              return Positioned(
                                left: r.left,
                                top: r.top,
                                width: r.width,
                                height: r.height,
                                child: GestureDetector(
                                  onTap: () => widget.onClass(c),
                                  child: ClassCard(
                                    item: c,
                                    model: widget.model,
                                  ),
                                ),
                              );
                            }),
                          ],
                        ),
                      ),
                    ),
                  ),
                  Positioned(
                    right: 16,
                    bottom: 16,
                    child: FloatingActionButton.small(
                      heroTag: 'fit',
                      tooltip: 'Ajustar diagrama',
                      onPressed: fit,
                      child: const Icon(Icons.fit_screen),
                    ),
                  ),
                  Positioned(
                    left: 16,
                    bottom: 18,
                    child: IgnorePointer(
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.92),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          '${classes.length} clases · ${rows(widget.model['relaciones']).length} relaciones',
                        ),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }
}

class ClassCard extends StatelessWidget {
  const ClassCard({super.key, required this.item, required this.model});
  final Json item, model;
  @override
  Widget build(BuildContext context) {
    final fields = rows(
      model['atributos'],
    ).where((a) => a['id_clase'] == item['id']).toList();
    final methods = rows(
      model['operaciones'],
    ).where((a) => a['id_clase'] == item['id']).toList();
    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF4F8D79)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x15000000),
            blurRadius: 10,
            offset: Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            color: const Color(0xFFE3F2EC),
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                if (item['estereotipo'] != null)
                  Text(
                    '«${item['estereotipo']}»',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11),
                  ),
                Text(
                  '${item['nombre']}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    fontStyle: item['es_abstracto'] == true
                        ? FontStyle.italic
                        : FontStyle.normal,
                  ),
                ),
              ],
            ),
          ),
          ...fields
              .take(5)
              .map(
                (a) => Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 2,
                  ),
                  child: Text(
                    '${visibility(a['visibilidad'])} ${a['nombre']}: ${a['tipo_uml']} [${multiplicity(a['multiplicidad_minima'], a['multiplicidad_maxima'])}]${collectionModifiers(a)}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      decoration: a['es_estatico'] == true
                          ? TextDecoration.underline
                          : TextDecoration.none,
                      fontStyle: a['es_abstracto'] == true
                          ? FontStyle.italic
                          : FontStyle.normal,
                    ),
                  ),
                ),
              ),
          if (methods.isNotEmpty) const Divider(height: 8),
          ...methods
              .take(3)
              .map(
                (a) => Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 2,
                  ),
                  child: Text(
                    '${visibility(a['visibilidad'])} ${a['nombre']}(): ${returnType(model, a)}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      decoration: a['es_estatico'] == true
                          ? TextDecoration.underline
                          : TextDecoration.none,
                      fontStyle: a['es_abstracto'] == true
                          ? FontStyle.italic
                          : FontStyle.normal,
                    ),
                  ),
                ),
              ),
          const Spacer(),
          const Padding(
            padding: EdgeInsets.only(bottom: 5),
            child: Text(
              'Toca para ver detalles',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 10, color: Colors.black45),
            ),
          ),
        ],
      ),
    );
  }
}

class RelationsPainter extends CustomPainter {
  RelationsPainter(this.relations, this.positions);
  final List<Json> relations;
  final Map<String, Rect> positions;
  Offset edge(Rect r, Offset toward) {
    final d = toward - r.center;
    if (d.distance < 0.1) return r.topCenter;
    final factor = math.min(
      d.dx == 0 ? double.infinity : r.width / 2 / d.dx.abs(),
      d.dy == 0 ? double.infinity : r.height / 2 / d.dy.abs(),
    );
    return r.center + d * factor;
  }

  void label(Canvas canvas, String text, Offset p) {
    if (text.isEmpty) return;
    final tp = TextPainter(
      text: TextSpan(
        text: text,
        style: const TextStyle(
          color: Color(0xFF24463B),
          fontSize: 12,
          backgroundColor: Color(0xFFF3F7F5),
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: 220);
    tp.paint(canvas, p);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = const Color(0xFF60746C)
      ..strokeWidth = 1.7
      ..style = PaintingStyle.stroke;
    for (final r in relations) {
      final source = positions[r['id_clase_origen']],
          target = positions[r['id_clase_destino']];
      if (source == null || target == null) continue;
      final self = source == target;
      final a = self ? source.topCenter : edge(source, target.center);
      final b = self ? source.centerRight : edge(target, source.center);
      final path = Path()..moveTo(a.dx, a.dy);
      if (self) {
        path.cubicTo(a.dx, a.dy - 70, b.dx + 70, b.dy - 70, b.dx, b.dy);
      } else {
        path.lineTo(b.dx, b.dy);
      }
      if (r['tipo_relacion'] == 'DEPENDENCIA') {
        for (final metric in path.computeMetrics()) {
          for (double d = 0; d < metric.length; d += 13) {
            canvas.drawPath(
              metric.extractPath(d, math.min(d + 7, metric.length)),
              paint,
            );
          }
        }
      } else {
        canvas.drawPath(path, paint);
      }
      final unit = (b - a) / math.max((b - a).distance, 1);
      final normal = Offset(-unit.dy, unit.dx);
      void arrow(Offset tip, Offset u, {bool triangle = false}) {
        final p = Path()
          ..moveTo(tip.dx, tip.dy)
          ..lineTo(
            (tip - u * 15 + Offset(-u.dy, u.dx) * 7).dx,
            (tip - u * 15 + Offset(-u.dy, u.dx) * 7).dy,
          );
        if (triangle) {
          final q = tip - u * 15 - Offset(-u.dy, u.dx) * 7;
          p.lineTo(q.dx, q.dy);
          p.close();
          canvas.drawPath(p, Paint()..color = const Color(0xFFF3F7F5));
        } else {
          p.moveTo(tip.dx, tip.dy);
          final q = tip - u * 15 - Offset(-u.dy, u.dx) * 7;
          p.lineTo(q.dx, q.dy);
        }
        canvas.drawPath(p, paint);
      }

      if (r['tipo_relacion'] == 'GENERALIZACION') {
        arrow(b, unit, triangle: true);
      } else if (r['tipo_relacion'] == 'DEPENDENCIA' ||
          r['direccion'] == 'ORIGEN_A_DESTINO' ||
          r['direccion'] == 'BIDIRECCIONAL') {
        arrow(b, unit);
      }
      if (!['GENERALIZACION', 'DEPENDENCIA'].contains(r['tipo_relacion']) &&
          (r['direccion'] == 'DESTINO_A_ORIGEN' ||
              r['direccion'] == 'BIDIRECCIONAL')) {
        arrow(a, -unit);
      }
      if (['COMPOSICION', 'AGREGACION'].contains(r['tipo_relacion'])) {
        final p = Path()..moveTo(a.dx, a.dy);
        for (final q in [
          a + unit * 10 + normal * 6,
          a + unit * 20,
          a + unit * 10 - normal * 6,
        ]) {
          p.lineTo(q.dx, q.dy);
        }
        p.close();
        canvas.drawPath(
          p,
          Paint()
            ..color = r['tipo_relacion'] == 'COMPOSICION'
                ? paint.color
                : const Color(0xFFF3F7F5),
        );
        canvas.drawPath(p, paint);
      }
      label(canvas, '${r['nombre'] ?? ''}', (a + b) / 2 + normal * 10);
      if (!['GENERALIZACION', 'DEPENDENCIA'].contains(r['tipo_relacion'])) {
        label(
          canvas,
          '${r['rol_origen'] ?? ''} ${multiplicity(r['minimo_origen'], r['maximo_origen'])}'
              .trim(),
          a + unit * 24 + normal * 8,
        );
        label(
          canvas,
          '${r['rol_destino'] ?? ''} ${multiplicity(r['minimo_destino'], r['maximo_destino'])}'
              .trim(),
          b - unit * 45 + normal * 8,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant RelationsPainter oldDelegate) => true;
}

class ClassDetails extends StatelessWidget {
  const ClassDetails({
    super.key,
    required this.model,
    required this.item,
    required this.ask,
  });
  final Json model, item;
  final VoidCallback ask;
  @override
  Widget build(BuildContext context) => DraggableScrollableSheet(
    expand: false,
    initialChildSize: .7,
    maxChildSize: .95,
    minChildSize: .3,
    builder: (context, controller) => ListView(
      controller: controller,
      padding: const EdgeInsets.all(24),
      children: [
        Text(
          '${item['nombre']}',
          style: Theme.of(context).textTheme.headlineMedium,
        ),
        if (item['descripcion'] != null) Text('${item['descripcion']}'),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: ask,
          icon: const Icon(Icons.auto_awesome),
          label: const Text('Consultar al agente sobre esta clase'),
        ),
        const SizedBox(height: 20),
        Text('Atributos', style: Theme.of(context).textTheme.titleLarge),
        ...rows(model['atributos'])
            .where((a) => a['id_clase'] == item['id'])
            .map(
              (a) => ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(
                  '${visibility(a['visibilidad'])} ${a['nombre']}: ${a['tipo_uml']} [${multiplicity(a['multiplicidad_minima'], a['multiplicidad_maxima'])}]${collectionModifiers(a)}',
                ),
                subtitle: Text(
                  '${a['es_identificador'] == true ? 'Identificador · ' : ''}${multiplicity(a['multiplicidad_minima'], a['multiplicidad_maxima'])}${collectionModifiers(a)}',
                ),
              ),
            ),
        const Divider(),
        Text('Operaciones', style: Theme.of(context).textTheme.titleLarge),
        ...rows(
          model['operaciones'],
        ).where((o) => o['id_clase'] == item['id']).map((o) {
          final parameters = rows(model['parametros'])
              .where(
                (p) =>
                    p['id_operacion'] == o['id'] && p['direccion'] != 'RETORNO',
              )
              .map(
                (p) =>
                    '${parameterDirection(p['direccion'])} ${p['nombre']}: ${p['tipo_uml']}${p['es_obligatorio'] == false ? ' [0..1]' : ''}',
              )
              .join(', ');
          return ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(
              '${visibility(o['visibilidad'])} ${o['nombre']}($parameters): ${returnType(model, o)}',
            ),
          );
        }),
        const Divider(),
        Text('Relaciones', style: Theme.of(context).textTheme.titleLarge),
        ...rows(model['relaciones'])
            .where(
              (r) =>
                  r['id_clase_origen'] == item['id'] ||
                  r['id_clase_destino'] == item['id'],
            )
            .map((r) {
              String name(dynamic id) =>
                  rows(model['clases'])
                      .where((c) => c['id'] == id)
                      .map((c) => c['nombre'].toString())
                      .firstOrNull ??
                  '?';
              return ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(
                  '${name(r['id_clase_origen'])} → ${name(r['id_clase_destino'])}',
                ),
                subtitle: Text(
                  '${r['tipo_relacion']} · ${multiplicity(r['minimo_origen'], r['maximo_origen'])} / ${multiplicity(r['minimo_destino'], r['maximo_destino'])}',
                ),
              );
            }),
      ],
    ),
  );
}
