import 'dart:io';

const _redirectActivity = 'net.openid.appauth.RedirectUriReceiverActivity';

final class OAuthRedirectIntentData {
  const OAuthRedirectIntentData({
    required this.scheme,
    required this.host,
    required this.path,
  });

  final String scheme;
  final String host;
  final String path;

  bool matches(Uri uri) =>
      uri.scheme == scheme && uri.host == host && uri.path == path;
}

OAuthRedirectIntentData inspectOAuthRedirectManifest(String manifest) {
  final activityPattern = RegExp(
    '<activity\\b(?=[^>]*android:name="$_redirectActivity")[^>]*>'
    r'([\s\S]*?)</activity>',
  );
  final activities = activityPattern.allMatches(manifest).toList();
  if (activities.length != 1) {
    throw StateError(
      'Merged manifest tam olarak bir RedirectUriReceiverActivity içermeli; '
      '${activities.length} bulundu.',
    );
  }

  final filters = RegExp(
    r'<intent-filter\b[^>]*>([\s\S]*?)</intent-filter>',
  ).allMatches(activities.single.group(1)!).toList();
  final redirectFilters = filters.where((match) {
    final body = match.group(1)!;
    return body.contains('android.intent.action.VIEW') &&
        body.contains('android.intent.category.DEFAULT') &&
        body.contains('android.intent.category.BROWSABLE');
  }).toList();
  if (redirectFilters.length != 1) {
    throw StateError(
      'Redirect activity tam olarak bir OAuth VIEW intent-filter içermeli; '
      '${redirectFilters.length} bulundu.',
    );
  }

  final dataTags = RegExp(
    r'<data\b[^>]*/>',
  ).allMatches(redirectFilters.single.group(1)!).toList();
  if (dataTags.length != 1) {
    throw StateError(
      'OAuth intent-filter tam olarak bir data öğesi içermeli; '
      '${dataTags.length} bulundu.',
    );
  }
  final data = dataTags.single.group(0)!;
  String attribute(String name) {
    final match = RegExp('android:$name="([^"]*)"').firstMatch(data);
    if (match == null) throw StateError('OAuth data android:$name eksik.');
    return match.group(1)!;
  }

  final result = OAuthRedirectIntentData(
    scheme: attribute('scheme'),
    host: attribute('host'),
    path: attribute('path'),
  );
  if (result.scheme != 'kursplatform' ||
      result.host != 'oauth2redirect' ||
      result.path.isNotEmpty) {
    throw StateError(
      'OAuth data yalnız kursplatform://oauth2redirect (boş path) olmalı.',
    );
  }
  _verifyUriMatching(result);
  return result;
}

void _verifyUriMatching(OAuthRedirectIntentData data) {
  final cases = <Uri, bool>{
    Uri.parse('kursplatform://oauth2redirect?code=test&state=test'): true,
    Uri.parse('kursplatform://other-host'): false,
    Uri.parse('kursplatform:///'): false,
    Uri.parse('kursplatform://oauth2redirect/other-path'): false,
  };
  for (final entry in cases.entries) {
    if (data.matches(entry.key) != entry.value) {
      throw StateError('Beklenmeyen OAuth URI eşleşmesi: ${entry.key}');
    }
  }
}

File _mergedManifest(String variant) {
  final root = Directory('build/app/intermediates/merged_manifests/$variant');
  if (!root.existsSync()) {
    throw StateError(
      '$variant merged manifest dizini bulunamadı: ${root.path}',
    );
  }
  final manifests = root
      .listSync(recursive: true)
      .whereType<File>()
      .where((file) => file.uri.pathSegments.last == 'AndroidManifest.xml')
      .toList();
  if (manifests.length != 1) {
    throw StateError(
      '$variant için tam olarak bir merged AndroidManifest.xml bekleniyordu; '
      '${manifests.length} bulundu.',
    );
  }
  return manifests.single;
}

void main() {
  for (final variant in <String>['debug', 'release']) {
    final manifest = _mergedManifest(variant);
    final data = inspectOAuthRedirectManifest(manifest.readAsStringSync());
    stdout.writeln(
      '$variant merged manifest: 1 OAuth intent-filter; '
      'scheme=${data.scheme}, host=${data.host}, path="${data.path}"',
    );
  }
}
