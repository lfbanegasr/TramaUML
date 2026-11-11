import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:uml_mobile/src/api.dart';
import 'package:uml_mobile/src/app.dart';
import 'package:uml_mobile/src/diagram.dart';

void main() {
  testWidgets('acceso móvil muestra cuenta compartida y valida campos', (
    tester,
  ) async {
    final api = Api();
    await tester.pumpWidget(MaterialApp(home: LoginPage(api: api)));
    expect(find.byType(TextFormField), findsNWidgets(2));
    expect(find.text('Servidor de producción'), findsNothing);
    expect(
      find.text('Utiliza la misma cuenta que en escritorio.'),
      findsOneWidget,
    );
    await tester.ensureVisible(find.text('Entrar al espacio de trabajo'));
    await tester.tap(find.text('Entrar al espacio de trabajo'));
    await tester.pump();
    expect(find.text('Campo obligatorio'), findsNWidgets(2));
    api.dispose();
  });
  testWidgets('visor vacío orienta a captura con el agente', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DiagramView(
            model: {'clases': [], 'relaciones': []},
            onClass: (_) {},
          ),
        ),
      ),
    );
    expect(find.text('Aquí empieza tu modelo'), findsOneWidget);
  });
  testWidgets('visor renderiza clases y permite abrir su detalle', (
    tester,
  ) async {
    String? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DiagramView(
            model: {
              'clases': [
                {'id': 'a', 'nombre': 'Producto', 'x': 0, 'y': 0},
              ],
              'atributos': [
                {
                  'id_clase': 'a',
                  'nombre': 'precio',
                  'tipo_uml': 'Double',
                  'visibilidad': 'PRIVADO',
                },
              ],
              'operaciones': [],
              'relaciones': [],
            },
            onClass: (c) => selected = c['id'] as String,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Producto'));
    expect(selected, 'a');
    expect(tester.takeException(), isNull);
  });
}
