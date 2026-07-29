import 'dart:io';

const _runnerBundleId = 'com.mepcity.kursplatform';
const _runnerTestsBundleId = 'com.mepcity.kursplatform.RunnerTests';
const _legacyBundleId =
    'org.mepcity.kurs'
    'PlatformMobile';

Never _fail(String message) {
  stderr.writeln('iOS release yapılandırması geçersiz: $message');
  exit(1);
}

void main() {
  final project = File(
    'ios/Runner.xcodeproj/project.pbxproj',
  ).readAsStringSync();
  final info = File('ios/Runner/Info.plist').readAsStringSync();
  final entitlements = File(
    'ios/Runner/Runner.entitlements',
  ).readAsStringSync();
  final debugConfig = File('ios/Flutter/Debug.xcconfig').readAsStringSync();
  final releaseConfig = File('ios/Flutter/Release.xcconfig').readAsStringSync();
  final gitignore = File('ios/.gitignore').readAsStringSync();

  if (project.contains(_legacyBundleId)) {
    _fail('eski bundle ID hâlâ Xcode projesinde bulunuyor');
  }
  if (_occurrences(project, 'PRODUCT_BUNDLE_IDENTIFIER = $_runnerBundleId;') !=
      3) {
    _fail('Runner Debug/Release/Profile bundle ID değerleri eksik');
  }
  if (_occurrences(
        project,
        'PRODUCT_BUNDLE_IDENTIFIER = $_runnerTestsBundleId;',
      ) !=
      3) {
    _fail('RunnerTests Debug/Release/Profile bundle ID değerleri eksik');
  }
  if (_occurrences(project, 'CODE_SIGN_STYLE = Automatic;') != 6) {
    _fail(
      'Runner ve RunnerTests yapılandırmalarının tamamı Automatic Signing kullanmalı',
    );
  }
  if (_occurrences(
            project,
            'baseConfigurationReference = 9740EEB21CF90195004384FC /* Debug.xcconfig */;',
          ) <
          2 ||
      _occurrences(
            project,
            'baseConfigurationReference = 7AFA3C8E1D35360C0083082E /* Release.xcconfig */;',
          ) <
          4) {
    _fail('RunnerTests yerel signing xcconfig zincirini kullanmalı');
  }
  if (_occurrences(
        project,
        'DEVELOPMENT_TEAM = "\$(KURS_PLATFORM_IOS_DEVELOPMENT_TEAM)";',
      ) !=
      6) {
    _fail(
      'Apple Team ID bütün hedeflerde yalnız yerel build değişkeninden alınmalı',
    );
  }
  if (project.contains('PROVISIONING_PROFILE') ||
      project.contains('CODE_SIGN_IDENTITY')) {
    _fail('sertifika veya provisioning profile Xcode projesine sabitlenemez');
  }
  if (!info.contains('<string>\$(PRODUCT_BUNDLE_IDENTIFIER)</string>') ||
      !info.contains('<string>kursplatform</string>')) {
    _fail('Info.plist bundle veya OAuth şema başvurusu eksik');
  }
  if (!info.contains('<key>ITSAppUsesNonExemptEncryption</key>') ||
      !info.contains('<false/>')) {
    _fail('export compliance beyanı Info.plist içinde kayıtlı olmalı');
  }
  if (!entitlements.contains(
    '<string>\$(AppIdentifierPrefix)\$(CFBundleIdentifier)</string>',
  )) {
    _fail('Keychain entitlement dinamik App ID ile bağlı olmalı');
  }
  for (final config in <String>[debugConfig, releaseConfig]) {
    if (!config.contains('#include? "Signing.xcconfig"')) {
      _fail('yerel signing xcconfig include değeri eksik');
    }
  }
  if (!gitignore.contains('Flutter/Signing.xcconfig')) {
    _fail('yerel signing dosyası Git tarafından yok sayılmalı');
  }

  stdout.writeln('iOS release yapılandırması geçerli.');
}

int _occurrences(String value, String pattern) {
  return pattern.allMatches(value).length;
}
