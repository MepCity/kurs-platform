# Kurs Platform Mobile

Kurs Platform'un iOS ve Android için Flutter uygulama iskeletidir. Bu dizin kendi bağımlılık
manifestini ve kilit dosyasını taşır; `experiments` altındaki kaynakları import etmez.

## Sınırlar

- `lib/core`: kararlı, çapraz kesen mobil altyapı sözleşmeleri
- `lib/features/<feature>/presentation`: ekran ve widget'lar
- `lib/features/<feature>/application`: use-case orkestrasyonu
- `lib/features/<feature>/domain`: framework bağımsız iş kavramları
- `lib/features/<feature>/data`: port uygulamaları ve adaptörler
- `test`: birim, widget ve mimari sınır testleri
- `integration_test`: uygulama içi dikey akış testleri

Presentation doğrudan HTTP, kalıcı depolama veya secure storage kullanmaz. Domain Flutter ve
platform türlerinden bağımsızdır. Mimari test presentation/domain katmanlarında doğrudan HTTP
istemcisi, Drift/SQLite, secure storage, `dart:io`, `dart:ffi` ve Flutter platform channel
importlarını reddeder. Bu adaptörler gerektiğinde data/core sınırında ve ilgili görev
sözleşmesine göre eklenir. A-011 gerçek kimlik, veri, eşitleme veya ürün ekranı eklemez.

## Yerel doğrulama

```bash
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
flutter build apk --debug
flutter build ios --debug --simulator --no-codesign
```

iOS build komutu tam Xcode kurulumu ve çalışan `xcodebuild` gerektirir. Bu araçların bulunmadığı
bir ortamda iOS kaynak iskeleti statik olarak doğrulanabilir ancak binary build kabul kanıtı
oluşmaz. A-012 kalite kapısı bu komutu `macos-15` ve tam Xcode üzerinde zorunlu olarak çalıştırır;
Android debug APK build'i ayrı Linux işinde doğrulanır. Flutter SDK cache anahtarı sabit SDK
revision'ını, pub cache anahtarı `pubspec.lock` içeriğini kullanır. Mobil bağımlılık envanteri
CycloneDX SBOM olarak CI artefaktında saklanır.

İskelet yalnız Flutter SDK, Material ve resmî `flutter_lints`/`flutter_test` yüzeylerini
kullanır. Durum yönetimi, ağ, kalıcılık, secure storage veya sağlayıcı paketi eklenmemiştir;
bunlar ilgili sözleşme ve uygulama görevlerinin karar alanıdır.
