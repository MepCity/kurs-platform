import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _layers = {'presentation', 'application', 'domain', 'data'};
const _directAdapterPrefixes = <String>[
  'dart:ffi',
  'dart:io',
  'package:dio/',
  'package:drift/',
  'package:flutter_secure_storage/',
  'package:http/',
  'package:sqflite/',
  'package:sqlite3/',
];

void main() {
  final libDirectory = Directory('lib');

  test('required feature-first boundaries are visible', () {
    final requiredDirectories = <String>[
      'lib/core',
      'lib/features',
      'lib/features/bootstrap/presentation',
      'lib/features/bootstrap/application',
      'lib/features/bootstrap/domain',
      'lib/features/bootstrap/data',
    ];

    for (final path in requiredDirectories) {
      expect(Directory(path).existsSync(), isTrue, reason: '$path bulunamadı');
    }
  });

  test('production Dart files respect path and directive whitelists', () {
    final violations = <String>[];

    for (final file
        in libDirectory
            .listSync(recursive: true)
            .whereType<File>()
            .where((file) => file.path.endsWith('.dart'))) {
      violations.addAll(_violationsFor(file.path, file.readAsStringSync()));
    }

    expect(violations, isEmpty, reason: violations.join('\n'));
  });

  test('rejects undefined top-level production folder', () {
    _expectForbidden('lib/services/client.dart', 'class Client {}');
    _expectForbidden('lib/shared/helper.dart', 'class Helper {}');
    _expectForbidden('lib/utils/format.dart', 'class Format {}');
  });

  test('rejects undefined feature layer', () {
    _expectForbidden(
      'lib/features/sample/service/use_case.dart',
      'class UseCase {}',
    );
    _expectForbidden(
      'lib/features/sample/repository/store.dart',
      'class Store {}',
    );
    _expectForbidden('lib/features/sample/model/item.dart', 'class Item {}');
  });

  test('domain rejects Flutter UI imports', () {
    _expectForbidden(
      'lib/features/sample/domain/rule.dart',
      "import 'package:flutter/widgets.dart';",
    );
  });

  test('presentation and domain reject direct HTTP clients', () {
    _expectForbiddenInPresentationAndDomain("import 'package:http/http.dart';");
    _expectForbiddenInPresentationAndDomain("import 'package:dio/dio.dart';");
  });

  test('presentation and domain reject Drift and SQLite clients', () {
    _expectForbiddenInPresentationAndDomain(
      "import 'package:drift/drift.dart';",
    );
    _expectForbiddenInPresentationAndDomain(
      "import 'package:sqflite/sqflite.dart';",
    );
    _expectForbiddenInPresentationAndDomain(
      "import 'package:sqlite3/sqlite3.dart';",
    );
  });

  test('presentation and domain reject secure storage clients', () {
    _expectForbiddenInPresentationAndDomain(
      "import 'package:flutter_secure_storage/flutter_secure_storage.dart';",
    );
  });

  test('presentation and domain reject platform adapters', () {
    _expectForbiddenInPresentationAndDomain("import 'dart:io';");
    _expectForbiddenInPresentationAndDomain(
      "import 'package:flutter/services.dart';",
    );
  });

  test('application rejects presentation and data imports', () {
    _expectForbidden(
      'lib/features/sample/application/use_case.dart',
      "import '../data/repository.dart';",
    );
  });

  test('domain cannot export data layer', () {
    _expectForbidden(
      'lib/features/sample/domain/rule.dart',
      "export '../data/repository.dart';",
    );
  });

  test('presentation cannot export direct adapter package', () {
    _expectForbidden(
      'lib/features/sample/presentation/screen.dart',
      "export 'package:drift/drift.dart';",
    );
  });

  test('domain cannot include another layer through part', () {
    _expectForbidden(
      'lib/features/sample/domain/rule.dart',
      "part '../application/use_case.dart';",
    );
  });

  test('presentation rejects conditional Dio and HTTP imports', () {
    _expectForbidden('lib/features/sample/presentation/screen.dart', """
import 'stub.dart'
  if (dart.library.io) 'package:dio/dio.dart';
""");
    _expectForbidden('lib/features/sample/presentation/screen.dart', """
import 'stub.dart'
  if (dart.library.io) 'package:http/http.dart';
""");
  });

  test('presentation rejects conditional Drift and SQLite exports', () {
    _expectForbidden('lib/features/sample/presentation/screen.dart', """
export 'stub.dart'
  if (dart.library.io) 'package:drift/drift.dart';
""");
    _expectForbidden('lib/features/sample/presentation/screen.dart', """
export 'stub.dart'
  if (dart.library.io) 'package:sqflite/sqflite.dart';
""");
  });

  test('domain rejects conditional Flutter platform and data dependencies', () {
    _expectForbidden('lib/features/sample/domain/rule.dart', """
import 'stub.dart'
  if (dart.library.io) 'package:flutter/services.dart';
""");
    _expectForbidden('lib/features/sample/domain/rule.dart', """
import 'stub.dart'
  if (dart.library.io) 'dart:io';
""");
    _expectForbidden('lib/features/sample/domain/rule.dart', """
export 'stub.dart'
  if (dart.library.io) '../data/repository.dart';
""");
  });

  test('allowed conditional import does not create a false positive', () {
    final source = """
import 'stub.dart'
  if (dart.library.html) 'browser_stub.dart';
""";
    expect(
      _violationsFor('lib/features/sample/presentation/screen.dart', source),
      isEmpty,
    );
  });

  test('comments and strings ignore fake conditional directives', () {
    final source = """
// import 'stub.dart'
//   if (dart.library.io) 'package:dio/dio.dart';
/*
export 'stub.dart'
  if (dart.library.io) 'package:drift/drift.dart';
*/
const example = "import 'stub.dart' if (dart.library.io) 'dart:io';";
const multiline = '''
import 'stub.dart'
  if (dart.library.io) 'package:flutter/services.dart';
''';
""";
    expect(
      _violationsFor('lib/features/sample/domain/rule.dart', source),
      isEmpty,
    );
  });

  test('comments and strings do not create fake directives', () {
    final source = """
// import 'package:drift/drift.dart';
/*
export '../data/repository.dart';
part '../application/use_case.dart';
*/
const example = "import 'package:http/http.dart';";
const multiline = '''
export '../data/repository.dart';
''';
""";
    expect(
      _violationsFor('lib/features/sample/domain/rule.dart', source),
      isEmpty,
    );
  });
}

void _expectForbidden(String path, String source) {
  expect(
    _violationsFor(path, source),
    isNotEmpty,
    reason: 'İhlal reddedilmedi: $path\n$source',
  );
}

void _expectForbiddenInPresentationAndDomain(String source) {
  for (final layer in ['presentation', 'domain']) {
    _expectForbidden('lib/features/sample/$layer/example.dart', source);
  }
}

List<String> _violationsFor(String path, String source) {
  final normalizedPath = path.replaceAll('\\', '/');
  final location = _locate(normalizedPath);
  final violations = <String>[];
  if (!location.allowed) {
    violations.add('$path: üretim Dart yolu whitelist dışında');
  }

  final directives = _directives(source);
  for (final directive in directives) {
    final uri = directive.uri;
    final usesDirectAdapter = _directAdapterPrefixes.any(uri.startsWith);
    final usesFlutterPlatformChannel = uri == 'package:flutter/services.dart';

    if (location.layer == 'domain' &&
        (uri.startsWith('package:flutter/') ||
            usesDirectAdapter ||
            uri.contains('/presentation/') ||
            uri.contains('/application/') ||
            uri.contains('/data/'))) {
      violations.add(
        '$path: domain katmanı yasak ${directive.kind} içeriyor: $uri',
      );
    }
    if (location.layer == 'application' &&
        (uri.contains('/presentation/') || uri.contains('/data/'))) {
      violations.add(
        '$path: application katmanı yasak ${directive.kind} içeriyor: $uri',
      );
    }
    if (location.layer == 'presentation' &&
        (usesDirectAdapter ||
            usesFlutterPlatformChannel ||
            uri.contains('/data/'))) {
      violations.add(
        '$path: presentation katmanı yasak ${directive.kind} içeriyor: $uri',
      );
    }
  }

  return violations;
}

_Location _locate(String path) {
  if (path == 'lib/main.dart') {
    return const _Location(true, null);
  }
  final parts = path.split('/');
  if (parts.length >= 3 && parts[0] == 'lib' && parts[1] == 'core') {
    return const _Location(true, 'core');
  }
  if (parts.length >= 5 &&
      parts[0] == 'lib' &&
      parts[1] == 'features' &&
      parts[2].isNotEmpty &&
      _layers.contains(parts[3])) {
    return _Location(true, parts[3]);
  }
  return const _Location(false, null);
}

Iterable<_Directive> _directives(String source) {
  final directivePattern = RegExp(
    r'''^\s*(import|export|part)\b''',
    multiLine: true,
  );
  final uriPattern = RegExp(r'''(['"])([^'"]+)\1''');
  final codePositions = _codePositions(source);
  final directives = <_Directive>[];

  for (final match in directivePattern.allMatches(source)) {
    final kind = match.group(1)!;
    final keywordIndex = source.indexOf(kind, match.start);
    if (keywordIndex < 0 || !codePositions[keywordIndex]) {
      continue;
    }

    var statementEnd = match.end;
    while (statementEnd < source.length &&
        !(source[statementEnd] == ';' && codePositions[statementEnd])) {
      statementEnd++;
    }
    if (statementEnd == source.length) {
      continue;
    }

    final statement = source.substring(match.end, statementEnd);
    for (final uriMatch in uriPattern.allMatches(statement)) {
      final quoteIndex = match.end + uriMatch.start;
      if (codePositions[quoteIndex]) {
        directives.add(_Directive(kind, uriMatch.group(2)!));
      }
    }
  }

  return directives;
}

List<bool> _codePositions(String source) {
  final positions = List<bool>.filled(source.length, false);
  var index = 0;
  var blockCommentDepth = 0;
  var inLineComment = false;
  var quote = '';
  var tripleQuoted = false;

  while (index < source.length) {
    final current = source[index];
    final next = index + 1 < source.length ? source[index + 1] : '';
    final following = index + 2 < source.length ? source[index + 2] : '';

    if (inLineComment) {
      if (current == '\n') {
        inLineComment = false;
        positions[index] = true;
      }
      index++;
      continue;
    }
    if (blockCommentDepth > 0) {
      if (current == '/' && next == '*') {
        blockCommentDepth++;
        index += 2;
        continue;
      }
      if (current == '*' && next == '/') {
        blockCommentDepth--;
        index += 2;
      } else {
        index++;
      }
      continue;
    }
    if (quote.isNotEmpty) {
      if (tripleQuoted &&
          current == quote &&
          next == quote &&
          following == quote) {
        quote = '';
        tripleQuoted = false;
        index += 3;
      } else if (!tripleQuoted && current == '\\' && next.isNotEmpty) {
        index += 2;
      } else {
        if (!tripleQuoted && current == quote) {
          quote = '';
        }
        index++;
      }
      continue;
    }
    if (current == '/' && next == '/') {
      inLineComment = true;
      index += 2;
      continue;
    }
    if (current == '/' && next == '*') {
      blockCommentDepth = 1;
      index += 2;
      continue;
    }
    if (current == "'" || current == '"') {
      positions[index] = true;
      quote = current;
      tripleQuoted = next == current && following == current;
      index += tripleQuoted ? 3 : 1;
      continue;
    }
    positions[index] = true;
    index++;
  }

  return positions;
}

class _Location {
  const _Location(this.allowed, this.layer);

  final bool allowed;
  final String? layer;
}

class _Directive {
  const _Directive(this.kind, this.uri);

  final String kind;
  final String uri;
}
