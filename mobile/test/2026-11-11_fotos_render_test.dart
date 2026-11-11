// ignore_for_file: file_names
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uml_mobile/src/api.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('una instalación que guardó una IP local vuelve a Render', () async {
    FlutterSecureStorage.setMockInitialValues({});
    SharedPreferences.setMockInitialValues({
      'server': 'http://192.168.0.200:8080',
    });
    final api = Api();
    await api.restore();
    expect(api.baseUrl, 'https://trama-uml.onrender.com');
    api.dispose();
  });

  test(
    'acepta original PNG mayor a 10 MB y conserva una copia decodificable',
    () async {
      final recorder = ui.PictureRecorder();
      ui.Canvas(recorder).drawRect(
        const ui.Rect.fromLTWH(0, 0, 100, 100),
        ui.Paint()..color = const ui.Color(0xFF176B52),
      );
      final picture = recorder.endRecording();
      final image = await picture.toImage(100, 100);
      final png = (await image.toByteData(
        format: ui.ImageByteFormat.png,
      ))!.buffer.asUint8List();
      image.dispose();
      picture.dispose();
      final large = Uint8List(12 * 1024 * 1024)..setAll(0, png);
      final photo = await preparePhoto(large, 'foto_camara.png');
      expect(photo.bytes.length, lessThanOrEqualTo(4 * 1024 * 1024));
      final codec = await ui.instantiateImageCodec(
        Uint8List.fromList(photo.bytes),
      );
      final frame = await codec.getNextFrame();
      expect(frame.image.width, 100);
      expect(frame.image.height, 100);
      frame.image.dispose();
      codec.dispose();
    },
  );
}
