import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:uml_mobile/src/agent.dart';
import 'package:uml_mobile/src/api.dart';

void main() {
  testWidgets('una propuesta obsoleta se puede consultar pero no aplicar', (
    tester,
  ) async {
    var writes = 0;
    final api = Api(
      client: MockClient((request) async {
        if (request.method != 'GET') writes++;
        return http.Response(
          jsonEncode({
            'id': 'p',
            'resumen': 'Agregar Producto',
            'tipoEntrada': 'TEXTO',
            'revisionBase': 1,
            'revisionActual': 2,
            'revisionEdicion': 'r',
            'estado': 'LISTO',
            'puedeAplicar': false,
            'operaciones': [
              {
                'id': 'a',
                'tipoOperacion': 'CREAR',
                'tipoEntidad': 'CLASE',
                'seleccionada': true,
                'estado': 'VALIDO',
                'valorPropuesto': {'nombre': 'Producto'},
                'dependencias': [],
              },
            ],
          }),
          200,
        );
      }),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ProposalSheet(
            api: api,
            modelId: 'm',
            proposalId: 'p',
            currentRevision: 2,
            canEdit: true,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Esta propuesta ya no es aplicable'),
      findsOneWidget,
    );
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Aplicar cambios'),
    );
    expect(button.onPressed, isNull);
    expect(writes, 0);
    api.dispose();
  });
}
