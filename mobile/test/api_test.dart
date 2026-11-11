import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:uml_mobile/src/api.dart';

void main() {
  test(
    'normaliza servidor y rechaza rutas, credenciales y esquemas inválidos',
    () {
      expect(
        Api.normalizeUrl(' http://192.168.0.200:8080/ '),
        'http://192.168.0.200:8080',
      );
      for (final value in [
        'localhost',
        'ftp://host',
        'https://user:password@host',
        'https://host/api',
        'https://host?token=x',
      ]) {
        expect(() => Api.normalizeUrl(value), throwsA(isA<ApiException>()));
      }
    },
  );
  test(
    'rota CSRF, conserva cookies y no repite escrituras automáticamente',
    () async {
      var csrfRequests = 0, writes = 0;
      final api = Api(
        client: MockClient((request) async {
          if (request.url.path.endsWith('/csrf')) {
            csrfRequests++;
            return http.Response(
              jsonEncode({'token': 'token-$csrfRequests', 'header': 'X-CSRF'}),
              200,
              headers: {
                'set-cookie': 'XSRF-TOKEN=value; Path=/; SameSite=Strict',
              },
            );
          }
          writes++;
          expect(request.headers['X-CSRF'], 'token-$csrfRequests');
          expect(request.headers['Cookie'], contains('XSRF-TOKEN=value'));
          return http.Response(
            jsonEncode({'mensaje': 'Conflicto de revisión'}),
            writes == 1 ? 403 : 409,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );
      await expectLater(
        api.request('POST', '/prueba', body: {'x': 1}),
        throwsA(isA<ApiException>().having((e) => e.status, 'status', 403)),
      );
      await expectLater(
        api.request('POST', '/prueba', body: {'x': 1}),
        throwsA(isA<ApiException>().having((e) => e.status, 'status', 409)),
      );
      expect(csrfRequests, 2);
      expect(writes, 2);
      api.dispose();
    },
  );
  test('rechaza una imagen que no es PNG/JPG antes de enviar', () async {
    final api = Api(
      client: MockClient((_) async => throw StateError('No debe enviar')),
    );
    await expectLater(
      api.image('modelo', [1, 2, 3, 4], 'fake.jpg', ''),
      throwsA(isA<ApiException>()),
    );
    api.dispose();
  });
}
