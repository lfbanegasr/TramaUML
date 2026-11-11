// El nombre fechado sigue la convención documental del proyecto.
// ignore_for_file: file_names
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:uml_mobile/src/diagram.dart';

void main() {
  test('máximo ilimitado y defaults históricos no se confunden con uno', () {
    expect(multiplicity(0, null), '0..*');
    expect(multiplicity(2, 8), '2..8');
    expect(collectionModifiers({'nombre': 'anterior'}), '');
    expect(
      collectionModifiers({'es_ordenado': true, 'coleccion_unica': false}),
      ' {ordered} {nonunique}',
    );
    expect(parameterDirection('ENTRADA_SALIDA'), 'inout');
  });
  test('retorno explícito conserva lectura del contrato previo', () {
    final model = <String, dynamic>{
      'parametros': [
        {'id_operacion': 'op', 'direccion': 'RETORNO', 'tipo_uml': 'Integer'},
      ],
    };
    expect(returnType(model, {'id': 'op'}), 'Integer');
    expect(returnType(model, {'id': 'op', 'tipo_retorno': 'String'}), 'String');
  });
  testWidgets('el visor presenta propiedades nuevas y clases antiguas', (
    tester,
  ) async {
    final model = <String, dynamic>{
      'atributos': [
        {
          'id_clase': 'c',
          'nombre': 'valores',
          'tipo_uml': 'String',
          'multiplicidad_minima': 0,
          'multiplicidad_maxima': null,
          'es_ordenado': true,
          'coleccion_unica': false,
        },
      ],
      'operaciones': [],
      'parametros': [],
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            height: 240,
            child: ClassCard(
              item: const {'id': 'c', 'nombre': 'Concepto'},
              model: model,
            ),
          ),
        ),
      ),
    );
    expect(find.textContaining('0..*'), findsOneWidget);
    expect(find.textContaining('{ordered} {nonunique}'), findsOneWidget);
  });
}
