import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/network/safe_http_transport.dart';

void main() {
  late HttpServer server;

  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  });

  tearDown(() async {
    await server.close(force: true);
  });

  test(
    'Dart IO transport rejects redirects without following Location',
    () async {
      var requests = 0;
      server.listen((request) async {
        requests++;
        request.response
          ..statusCode = HttpStatus.found
          ..headers.set('Location', '/token-bearing-destination');
        await request.response.close();
      });
      final transport = DartIoSafeHttpTransport();

      await expectLater(
        transport.send(
          SafeHttpRequest(
            method: 'GET',
            uri: Uri.parse('http://127.0.0.1:${server.port}/start'),
            headers: const {},
          ),
        ),
        throwsA(isA<SafeHttpTransportException>()),
      );
      expect(requests, 1);
    },
  );

  test(
    'Dart IO transport rejects oversized response before decoding',
    () async {
      server.listen((request) async {
        request.response.write(List<String>.filled(65, 'x').join());
        await request.response.close();
      });
      final transport = DartIoSafeHttpTransport(maxResponseBytes: 64);

      await expectLater(
        transport.send(
          SafeHttpRequest(
            method: 'GET',
            uri: Uri.parse('http://127.0.0.1:${server.port}/large'),
            headers: const {},
          ),
        ),
        throwsA(isA<SafeHttpTransportException>()),
      );
    },
  );
}
