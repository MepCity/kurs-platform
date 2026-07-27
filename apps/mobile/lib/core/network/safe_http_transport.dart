import 'dart:async';
import 'dart:convert';
import 'dart:io';

const int safeHttpMaxResponseBytes = 1024 * 1024;

class SafeHttpRequest {
  const SafeHttpRequest({
    required this.method,
    required this.uri,
    required this.headers,
    this.body,
  });

  final String method;
  final Uri uri;
  final Map<String, String> headers;
  final String? body;
}

class SafeHttpResponse {
  const SafeHttpResponse({
    required this.statusCode,
    required this.headers,
    required this.body,
  });

  final int statusCode;
  final Map<String, String> headers;
  final String body;
}

class SafeHttpTransportException implements Exception {
  const SafeHttpTransportException();
}

abstract interface class SafeHttpTransport {
  Future<SafeHttpResponse> send(SafeHttpRequest request);
}

class DartIoSafeHttpTransport implements SafeHttpTransport {
  DartIoSafeHttpTransport({
    this.connectionTimeout = const Duration(seconds: 10),
    this.responseTimeout = const Duration(seconds: 20),
    this.maxResponseBytes = safeHttpMaxResponseBytes,
  });

  final Duration connectionTimeout;
  final Duration responseTimeout;
  final int maxResponseBytes;

  @override
  Future<SafeHttpResponse> send(SafeHttpRequest request) async {
    final client = HttpClient()..connectionTimeout = connectionTimeout;
    try {
      final outgoing = await client
          .openUrl(request.method, request.uri)
          .timeout(connectionTimeout);
      outgoing.followRedirects = false;
      request.headers.forEach(outgoing.headers.set);
      if (request.body != null) outgoing.write(request.body);
      final incoming = await outgoing.close().timeout(responseTimeout);
      if (incoming.isRedirect) throw const SafeHttpTransportException();
      final bytes = <int>[];
      await for (final chunk in incoming.timeout(responseTimeout)) {
        bytes.addAll(chunk);
        if (bytes.length > maxResponseBytes) {
          throw const SafeHttpTransportException();
        }
      }
      final body = utf8.decode(bytes, allowMalformed: false);
      final headers = <String, String>{};
      incoming.headers.forEach((name, values) {
        final canonical = name.toLowerCase();
        if (canonical.length <= 128 &&
            values.length <= 8 &&
            values.every((value) => value.length <= 1024)) {
          headers[canonical] = values.join(',');
        }
      });
      return SafeHttpResponse(
        statusCode: incoming.statusCode,
        headers: Map<String, String>.unmodifiable(headers),
        body: body,
      );
    } on SafeHttpTransportException {
      rethrow;
    } on Object {
      throw const SafeHttpTransportException();
    } finally {
      client.close(force: true);
    }
  }
}
