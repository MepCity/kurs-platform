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

  test(
    'Dart IO transport enforces an absolute response body deadline',
    () async {
      server.listen((request) async {
        try {
          for (var index = 0; index < 20; index++) {
            request.response.write('x');
            await request.response.flush();
            await Future<void>.delayed(const Duration(milliseconds: 15));
          }
          await request.response.close();
        } on Object {
          // The client is expected to close the socket at the absolute deadline.
        }
      });
      final transport = DartIoSafeHttpTransport(
        responseTimeout: const Duration(milliseconds: 70),
      );

      await expectLater(
        transport.send(
          SafeHttpRequest(
            method: 'GET',
            uri: Uri.parse('http://127.0.0.1:${server.port}/slow-drip'),
            headers: const {},
          ),
        ),
        throwsA(isA<SafeHttpTransportException>()),
      );
    },
  );

  test(
    'Dart IO transport accepts normal chunked JSON within deadline',
    () async {
      server.listen((request) async {
        request.response.write('{"ok":');
        await request.response.flush();
        await Future<void>.delayed(const Duration(milliseconds: 10));
        request.response.write('true}');
        await request.response.close();
      });
      final transport = DartIoSafeHttpTransport(
        responseTimeout: const Duration(milliseconds: 200),
      );

      final response = await transport.send(
        SafeHttpRequest(
          method: 'GET',
          uri: Uri.parse('http://127.0.0.1:${server.port}/chunked'),
          headers: const {},
        ),
      );

      expect(response.body, '{"ok":true}');
    },
  );
}
