import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:shared_preferences/shared_preferences.dart';

typedef Json = Map<String, dynamic>;

class ApiException implements Exception {
  final int status;
  final String message;
  ApiException(this.status, this.message);
  @override
  String toString() => message;
}

// El peso del original no bloquea la selección. Se envía una copia adaptada
// al transporte para conservar legibilidad sin saturar Render ni la conexión.
Future<({List<int> bytes, String name})> preparePhoto(
  List<int> bytes,
  String name,
) async {
  const transportBytes = 4 * 1024 * 1024;
  final standard =
      bytes.length > 4 &&
      ((bytes[0] == 137 && bytes[1] == 80) ||
          (bytes[0] == 255 && bytes[1] == 216));
  if (standard && bytes.length <= transportBytes) {
    return (bytes: bytes, name: name);
  }
  try {
    final buffer = await ui.ImmutableBuffer.fromUint8List(
      Uint8List.fromList(bytes),
    );
    try {
      final descriptor = await ui.ImageDescriptor.encoded(buffer);
      try {
        var longest = 2560;
        while (true) {
          final factor =
              longest /
              (descriptor.width > descriptor.height
                  ? descriptor.width
                  : descriptor.height);
          final codec = await descriptor.instantiateCodec(
            targetWidth: factor < 1
                ? (descriptor.width * factor).round().clamp(1, longest)
                : descriptor.width,
            targetHeight: factor < 1
                ? (descriptor.height * factor).round().clamp(1, longest)
                : descriptor.height,
          );
          try {
            final frame = await codec.getNextFrame();
            try {
              final data = await frame.image.toByteData(
                format: ui.ImageByteFormat.png,
              );
              if (data != null && data.lengthInBytes <= transportBytes) {
                return (
                  bytes: data.buffer.asUint8List(),
                  name: 'diagrama_optimizado.png',
                );
              }
            } finally {
              frame.image.dispose();
            }
          } finally {
            codec.dispose();
          }
          if (longest <= 320) {
            throw const FormatException('Imagen no procesable');
          }
          longest = (longest * 0.75).round();
        }
      } finally {
        descriptor.dispose();
      }
    } finally {
      buffer.dispose();
    }
  } catch (_) {
    throw ApiException(
      0,
      'No se pudo preparar la foto. Intenta tomarla de nuevo o elegir otra imagen.',
    );
  }
}

class Api extends ChangeNotifier {
  Api({http.Client? client}) : _client = client ?? http.Client();
  final http.Client _client;
  final _storage = const FlutterSecureStorage();
  static const productionUrl = 'https://trama-uml.onrender.com';
  String baseUrl = productionUrl;
  final Map<String, String> _cookies = {};
  String? _csrf, _csrfHeader;
  Json? session;
  String get userId => session?['usuario']?['id']?.toString() ?? '';
  String get cookieHeader =>
      _cookies.entries.map((e) => '${e.key}=${e.value}').join('; ');
  bool get canCreate => session?['puedeCrearProyectos'] == true;

  static String normalizeUrl(String input) {
    final uri = Uri.tryParse(input.trim());
    if (uri == null ||
        !['http', 'https'].contains(uri.scheme) ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        (uri.path.isNotEmpty && uri.path != '/')) {
      throw ApiException(
        0,
        'Usa una dirección válida, por ejemplo https://trama-uml.onrender.com',
      );
    }
    return uri.replace(path: '').toString().replaceFirst(RegExp(r'/$'), '');
  }

  Future<void> restore() async {
    // Las actualizaciones migran también las instalaciones que guardaron una IP local.
    baseUrl = productionUrl;
    final saved = await _storage.read(key: 'session:$baseUrl');
    if (saved != null) _cookies['UML_SESION'] = saved;
    if (saved != null) {
      try {
        session = await request('GET', '/acceso/sesion') as Json;
      } on ApiException catch (e) {
        if (e.status != 401) rethrow;
      }
    }
  }

  Future<void> login(String server, String user, String password) async {
    final normalized = normalizeUrl(server);
    _cookies.clear();
    _csrf = null;
    _csrfHeader = null;
    session = null;
    baseUrl = normalized;
    session =
        await request(
              'POST',
              '/acceso/entrar',
              body: {'identificador': user.trim(), 'contrasena': password},
            )
            as Json;
    _csrf = null; // El servidor rota CSRF después del acceso.
    final token = _cookies['UML_SESION'];
    if (token == null) {
      session = null;
      throw ApiException(0, 'El servidor no entregó la cookie de sesión.');
    }
    await _storage.write(key: 'session:$baseUrl', value: token);
    await (await SharedPreferences.getInstance()).setString('server', baseUrl);
    notifyListeners();
  }

  Future<void> logout() async {
    await request('POST', '/acceso/salir');
    await _forget();
  }

  Future<void> _forget() async {
    _cookies.clear();
    _csrf = null;
    session = null;
    await _storage.delete(key: 'session:$baseUrl');
    notifyListeners();
  }

  void _capture(http.StreamedResponse response) {
    final raw = response.headers['set-cookie'];
    if (raw == null) return;
    for (final part in raw.split(RegExp(r',(?=\s*[^;,=\s]+=)'))) {
      try {
        final cookie = Cookie.fromSetCookieValue(part.trim());
        if (cookie.value.isEmpty || cookie.maxAge == 0) {
          _cookies.remove(cookie.name);
        } else {
          _cookies[cookie.name] = cookie.value;
        }
      } on FormatException {
        /* Una cookie inválida no autentica al cliente. */
      }
    }
  }

  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    http.MultipartRequest? multipart,
  }) async {
    if (method != 'GET' && _csrf == null) {
      final token = await request('GET', '/acceso/csrf') as Json;
      _csrf = token['token'] as String;
      _csrfHeader = token['header'] as String;
    }
    final uri = Uri.parse('$baseUrl/api$path');
    final http.BaseRequest req = multipart ?? http.Request(method, uri);
    req.headers['Accept'] = 'application/json';
    if (_cookies.isNotEmpty) req.headers['Cookie'] = cookieHeader;
    if (method != 'GET') req.headers[_csrfHeader!] = _csrf!;
    if (req is http.Request && body != null) {
      req.headers['Content-Type'] = 'application/json';
      req.body = jsonEncode(body);
    }
    try {
      final response = await _client
          .send(req)
          .timeout(const Duration(seconds: 100));
      _capture(response);
      final text = await response.stream.bytesToString().timeout(
        const Duration(seconds: 100),
      );
      dynamic data;
      try {
        data = text.isEmpty ? null : jsonDecode(text);
      } on FormatException {
        data = null;
      }
      if (response.statusCode == 401) await _forget();
      if (response.statusCode == 403) _csrf = null;
      if (response.statusCode >= 400) {
        throw ApiException(
          response.statusCode,
          data is Map
              ? (data['mensaje'] ??
                        data['message'] ??
                        'No se pudo completar la solicitud.')
                    .toString()
              : 'El servidor respondió ${response.statusCode}.',
        );
      }
      if (response.statusCode != 204 && data == null) {
        throw ApiException(
          0,
          'Respuesta incompleta. Comprueba el resultado antes de reenviar.',
        );
      }
      return data;
    } on SocketException {
      throw ApiException(
        0,
        'No se pudo conectar. Comprueba la dirección, el Wi-Fi o el enlace USB.',
      );
    } on http.ClientException {
      throw ApiException(
        0,
        'Conexión interrumpida. Comprueba el resultado antes de reenviar.',
      );
    } on TimeoutException {
      throw ApiException(
        0,
        'El servidor tardó demasiado. Comprueba el resultado antes de reenviar.',
      );
    }
  }

  Future<Json> image(
    String model,
    List<int> bytes,
    String name,
    String instruction, {
    String? messageKey,
    String intention = 'MODIFICAR',
  }) async {
    final prepared = await preparePhoto(bytes, name);
    bytes = prepared.bytes;
    name = prepared.name;
    final png = bytes.length > 4 && bytes[0] == 137 && bytes[1] == 80;
    final jpg = bytes.length > 3 && bytes[0] == 255 && bytes[1] == 216;
    if (!png && !jpg) throw ApiException(0, 'Selecciona una imagen PNG o JPG.');
    final path = messageKey == null
        ? '/modelos/$model/ia/solicitud/imagen'
        : '/modelos/$model/ia/conversacion/imagen';
    final req = http.MultipartRequest('POST', Uri.parse('$baseUrl/api$path'));
    if (messageKey != null) {
      req.fields['clave'] = messageKey;
      req.fields['intencion'] = intention;
    }
    req.files.add(
      http.MultipartFile.fromBytes(
        'archivo',
        bytes,
        filename: name,
        contentType: MediaType('image', png ? 'png' : 'jpeg'),
      ),
    );
    if (instruction.trim().isNotEmpty) {
      req.files.add(
        http.MultipartFile.fromString(
          'textoDetectado',
          instruction.trim(),
          contentType: MediaType('text', 'plain', {'charset': 'utf-8'}),
        ),
      );
    }
    return await request('POST', path, multipart: req) as Json;
  }

  Future<Json> voice(
    String model,
    String text, {
    String? messageKey,
    String intention = 'AUTO',
    List<int>? audioBytes,
  }) async {
    const path = 'ia/conversacion/voz';
    final fullPath = '/modelos/$model/$path';
    final req = http.MultipartRequest(
      'POST',
      Uri.parse('$baseUrl/api$fullPath'),
    );
    if (messageKey != null) {
      req.fields['clave'] = messageKey;
    }
    req.fields['intencion'] = intention;
    if (text.trim().isNotEmpty) {
      req.files.add(
        http.MultipartFile.fromString(
          'texto',
          text.trim(),
          contentType: MediaType('text', 'plain', {'charset': 'utf-8'}),
        ),
      );
    }
    if (audioBytes != null && audioBytes.isNotEmpty) {
      req.files.add(
        http.MultipartFile.fromBytes(
          'archivo',
          audioBytes,
          filename: 'voz.wav',
          contentType: MediaType('audio', 'wav'),
        ),
      );
    }
    return await request('POST', fullPath, multipart: req) as Json;
  }

  Future<WebSocket> connect(
    String model,
    String editingSession,
  ) => WebSocket.connect(
    '${baseUrl.replaceFirst(RegExp(r'^http'), 'ws')}/ws/modelos/$model/sesiones/$editingSession',
    headers: {'Cookie': cookieHeader},
  ).timeout(const Duration(seconds: 15));

  @override
  void dispose() {
    _client.close();
    super.dispose();
  }
}

List<Json> rows(dynamic value) => (value is List ? value : <dynamic>[])
    .whereType<Map>()
    .map((e) => Map<String, dynamic>.from(e))
    .toList();
