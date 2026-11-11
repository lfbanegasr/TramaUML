import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:uml_mobile/src/api.dart';
import 'package:uml_mobile/src/app.dart';

void main() {
  testWidgets(
    'acceso usable en teléfono vertical y horizontal con texto ampliado',
    (tester) async {
      final fontPath = Platform.environment['UML_PREVIEW_FONT'];
      if (fontPath != null) {
        await (FontLoader('PreviewRoboto')..addFont(
              Future.value(
                ByteData.sublistView(File(fontPath).readAsBytesSync()),
              ),
            ))
            .load();
        await (FontLoader('MaterialIcons')..addFont(
              Future.value(
                ByteData.sublistView(
                  File(
                    '${File(fontPath).parent.path}/materialicons-regular.otf',
                  ).readAsBytesSync(),
                ),
              ),
            ))
            .load();
      }
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final api = Api();
      for (final size in [const Size(393, 873), const Size(873, 393)]) {
        tester.view.physicalSize = size;
        final boundary = GlobalKey();
        await tester.pumpWidget(
          MaterialApp(
            theme: ThemeData(
              useMaterial3: true,
              fontFamily: fontPath == null ? null : 'PreviewRoboto',
              colorScheme: ColorScheme.fromSeed(
                seedColor: const Color(0xFF5350D5),
              ),
              scaffoldBackgroundColor: const Color(0xFFF5F6FC),
              inputDecorationTheme: const InputDecorationTheme(
                border: OutlineInputBorder(),
                filled: true,
                fillColor: Colors.white,
              ),
            ),
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: TextScaler.linear(size.width < 500 ? 1 : 1.3),
              ),
              child: child!,
            ),
            home: RepaintBoundary(
              key: boundary,
              child: LoginPage(api: api),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        if (fontPath != null && size.width < 500) {
          await tester.runAsync(() async {
            final image =
                await (boundary.currentContext!.findRenderObject()
                        as RenderRepaintBoundary)
                    .toImage(pixelRatio: 2);
            final bytes = await image.toByteData(
              format: ui.ImageByteFormat.png,
            );
            final output = File('build/previews/2026-11-10_acceso_redmi.png');
            await output.parent.create(recursive: true);
            await output.writeAsBytes(bytes!.buffer.asUint8List());
            image.dispose();
          });
        }
        await tester.ensureVisible(find.text('Entrar al espacio de trabajo'));
        await tester.tap(find.text('Entrar al espacio de trabajo'));
        await tester.pump();
        expect(find.text('Campo obligatorio'), findsNWidgets(2));
      }
      api.dispose();
    },
  );
}
