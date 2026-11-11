// ignore_for_file: file_names
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:uml_mobile/src/api.dart';
import 'package:uml_mobile/src/agent.dart';

void main() {
  testWidgets(
    'recupera mensajes compartidos con autor y acuerdos desde servidor',
    (tester) async {
      FlutterSecureStorage.setMockInitialValues({});
      var writes = 0;
      final api = Api(
        client: MockClient((request) async {
          if (request.method != 'GET') writes++;
          if (request.url.path.endsWith('/vigente')) {
            return http.Response('', 204);
          }
          if (request.url.path.endsWith('/conversacion')) {
            final initial = request.url.queryParameters['cursor'] == '0';
            return http.Response(
              jsonEncode({
                'idConversacion': 'h',
                'cursor': 2,
                'hayMas': false,
                'ejecuciones': [],
                'acuerdos': [
                  {
                    'id': 'a',
                    'idMensaje': '1',
                    'contenido': 'Anticipo opcional',
                    'nombreAutor': 'Ana',
                    'vigente': true,
                  },
                ],
                'mensajes': initial
                    ? [
                        {
                          'id': '1',
                          'secuencia': 1,
                          'autor': 'ana',
                          'nombreAutor': 'Ana',
                          'emisor': 'USUARIO',
                          'texto': 'Reservas sin anticipo obligatorio',
                          'modalidad': 'TEXTO',
                          'creadoEn': '2026-11-11T00:00:00Z',
                        },
                        {
                          'id': '2',
                          'secuencia': 2,
                          'nombreAutor': 'Asistente UML',
                          'emisor': 'ASISTENTE',
                          'texto': 'El acuerdo permite pago opcional.',
                          'modalidad': 'TEXTO',
                          'tipoResultado': 'RESPUESTA',
                          'fuentes': ['a'],
                          'supuestos': [],
                          'creadoEn': '2026-11-11T00:00:01Z',
                        },
                      ]
                    : [],
              }),
              200,
              headers: {'content-type': 'application/json; charset=utf-8'},
            );
          }
          return http.Response('{}', 200);
        }),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AgentView(
              api: api,
              projectId: 'p',
              modelId: 'm',
              revision: 1,
              canEdit: false,
              onApplied: () async {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('CONSULTA DE Ana'), findsOneWidget);
      expect(find.text('El acuerdo permite pago opcional.'), findsOneWidget);
      await tester.pump(const Duration(seconds: 4));
      await tester.pumpAndSettle();
      expect(find.text('El acuerdo permite pago opcional.'), findsOneWidget);
      expect(writes, 0);
      await tester.pumpWidget(const SizedBox());
      api.dispose();
    },
  );

  testWidgets('mantiene la lectura anterior cuando llega un mensaje nuevo', (
    tester,
  ) async {
    FlutterSecureStorage.setMockInitialValues({});
    var historyCalls = 0;
    final api = Api(
      client: MockClient((request) async {
        if (!request.url.path.endsWith('/conversacion')) {
          return http.Response('{}', 200);
        }
        historyCalls++;
        final initial = request.url.queryParameters['cursor'] == '0';
        final messages = initial
            ? List.generate(
                35,
                (index) => {
                  'id': '${index + 1}',
                  'secuencia': index + 1,
                  'autor': 'ana',
                  'nombreAutor': 'Ana',
                  'emisor': 'USUARIO',
                  'texto': 'Consulta histórica número ${index + 1}',
                  'modalidad': 'TEXTO',
                  'creadoEn': '2026-11-11T00:00:00Z',
                },
              )
            : historyCalls == 2
            ? [
                {
                  'id': '36',
                  'secuencia': 36,
                  'autor': 'luis',
                  'nombreAutor': 'Luis',
                  'emisor': 'USUARIO',
                  'texto': 'Mensaje recibido mientras Ana lee arriba',
                  'modalidad': 'TEXTO',
                  'creadoEn': '2026-11-11T00:00:01Z',
                },
              ]
            : <Map<String, Object?>>[];
        return http.Response(
          jsonEncode({
            'idConversacion': 'h',
            'cursor': initial ? 35 : 36,
            'hayMas': false,
            'ejecuciones': [],
            'acuerdos': [],
            'mensajes': messages,
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AgentView(
            api: api,
            projectId: 'p',
            modelId: 'm',
            revision: 1,
            canEdit: false,
            onApplied: () async {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final listFinder = find.byWidgetPredicate(
      (widget) => widget is ListView && widget.controller != null,
    );
    final controller = tester.widget<ListView>(listFinder).controller!;
    controller.jumpTo(controller.position.maxScrollExtent);
    await tester.pumpAndSettle();
    await tester.drag(listFinder, const Offset(0, 500));
    await tester.pumpAndSettle();
    final readingOffset = controller.offset;
    expect(readingOffset, lessThan(controller.position.maxScrollExtent));

    await tester.pump(const Duration(seconds: 4));
    await tester.pumpAndSettle();

    expect(controller.offset, closeTo(readingOffset, 1));
    await tester.tap(find.text('Ver últimos mensajes'));
    await tester.pumpAndSettle();
    expect(find.text('CONSULTA DE Luis'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    api.dispose();
  });

  testWidgets(
    'no mueve el historial ni siquiera al llegar mensajes cerca del final',
    (tester) async {
      FlutterSecureStorage.setMockInitialValues({});
      var calls = 0;
      final api = Api(
        client: MockClient((r) async {
          if (!r.url.path.endsWith('/conversacion')) {
            return http.Response('{}', 200);
          }
          calls++;
          return http.Response(
            jsonEncode({
              'cursor': calls == 1 ? 30 : 31,
              'hayMas': false,
              'mensajes': calls == 1
                  ? List.generate(
                      30,
                      (i) => {
                        'id': '$i',
                        'autor': 'ana',
                        'nombreAutor': 'Ana',
                        'emisor': 'USUARIO',
                        'texto': 'Mensaje largo del historial $i. ' * 5,
                      },
                    )
                  : calls == 2
                  ? [
                      {
                        'id': 'nuevo',
                        'autor': 'luis',
                        'nombreAutor': 'Luis',
                        'emisor': 'USUARIO',
                        'texto': 'Nueva consulta del compañero mientras leo.',
                      },
                    ]
                  : [],
              'ejecuciones': [],
              'acuerdos': [],
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: AgentView(
              api: api,
              projectId: 'p',
              modelId: 'm',
              revision: 1,
              canEdit: false,
              onApplied: () async {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final list = tester.widget<ListView>(
        find.byWidgetPredicate((w) => w is ListView && w.controller != null),
      );
      final controller = list.controller!;
      controller.jumpTo(controller.position.maxScrollExtent - 35);
      await tester.pumpAndSettle();
      final before = controller.offset;
      await tester.pump(const Duration(seconds: 4));
      await tester.pumpAndSettle();
      expect(controller.offset, closeTo(before, 1));
      await tester.pump(const Duration(seconds: 8));
      await tester.pumpAndSettle();
      expect(controller.offset, closeTo(before, 1));
      await tester.pumpWidget(const SizedBox());
      api.dispose();
    },
  );

  test(
    'adjunto conversacional conserva clave y no utiliza el contrato anterior',
    () async {
      var sent = false;
      final api = Api(
        client: MockClient((r) async {
          if (r.url.path.endsWith('/csrf')) {
            return http.Response(
              jsonEncode({'token': 't', 'header': 'X-CSRF'}),
              200,
            );
          }
          expect(r.url.path, '/api/modelos/m/ia/conversacion/imagen');
          final body = latin1.decode(r.bodyBytes);
          expect(body, contains('name="clave"'));
          expect(body, contains('clave-estable'));
          expect(body, contains('CONSULTAR'));
          sent = true;
          return http.Response(
            jsonEncode({
              'ejecucion': {'id': 'e', 'estado': 'PENDIENTE'},
            }),
            202,
          );
        }),
      );
      final result = await api.image(
        'm',
        [137, 80, 78, 71, 0],
        'foto.png',
        'Explica la imagen',
        messageKey: 'clave-estable',
        intention: 'CONSULTAR',
      );
      expect(result['ejecucion']['estado'], 'PENDIENTE');
      expect(sent, true);
      api.dispose();
    },
  );

  test(
    'comando de voz envia transcripcion a /ia/conversacion/voz con clave e intencion',
    () async {
      var sentVoice = false;
      final api = Api(
        client: MockClient((r) async {
          if (r.url.path.endsWith('/csrf')) {
            return http.Response(
              jsonEncode({'token': 't', 'header': 'X-CSRF'}),
              200,
            );
          }
          expect(r.url.path, '/api/modelos/m/ia/conversacion/voz');
          final body = utf8.decode(r.bodyBytes, allowMalformed: true);
          expect(body, contains('name="clave"'));
          expect(body, contains('clave-voz'));
          expect(body, contains('Crea clase Factura'));
          sentVoice = true;
          return http.Response(
            jsonEncode({
              'ejecucion': {'id': 'ev', 'estado': 'PENDIENTE'},
            }),
            202,
          );
        }),
      );
      final result = await api.voice(
        'm',
        'Crea clase Factura',
        messageKey: 'clave-voz',
        intention: 'MODIFICAR',
      );
      expect(result['ejecucion']['estado'], 'PENDIENTE');
      expect(sentVoice, true);
      api.dispose();
    },
  );
}
