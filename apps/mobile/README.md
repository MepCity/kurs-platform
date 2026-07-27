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

## Ortam yapılandırması

Mobil uygulama public yapılandırmayı `--dart-define` değerlerinden okur:
`KURS_PLATFORM_ENVIRONMENT`, `KURS_PLATFORM_PUBLIC_API_BASE_URL`,
`KURS_PLATFORM_COGNITO_ISSUER_URI`, secretsiz `KURS_PLATFORM_COGNITO_CLIENT_ID` ve sabit
allow-listli `KURS_PLATFORM_COGNITO_REDIRECT_URI=kursplatform://oauth2redirect`.
Development/staging/production adları açık yazılır; `prod` veya `test` gibi kısa adlar kabul
edilmez. Mobil pakete veritabanı bağlantısı, token pepper, Cognito admin role veya başka backend
secret referansı konmaz. Runtime kodu sessiz development fallback içermez; eksik `--dart-define`
değeri konfigürasyon hatası üretir.

## Yerel doğrulama

```bash
flutter pub get
dart format --output=none --set-exit-if-changed lib test tool
flutter analyze
flutter test
flutter build apk --debug
./android/gradlew -p android app:processReleaseManifest --no-daemon
dart run tool/android_oauth_manifest_verifier.dart
flutter build ios --debug --simulator --no-codesign
```

iOS build komutu tam Xcode kurulumu ve çalışan `xcodebuild` gerektirir. Bu araçların bulunmadığı
bir ortamda iOS kaynak iskeleti statik olarak doğrulanabilir ancak binary build kabul kanıtı
oluşmaz. A-012 kalite kapısı bu komutu `macos-15` ve tam Xcode üzerinde zorunlu olarak çalıştırır;
Android debug APK build'i ayrı Linux işinde doğrulanır. Flutter SDK cache anahtarı sabit SDK
revision'ını, pub cache anahtarı `pubspec.lock` içeriğini kullanır. Mobil bağımlılık envanteri
CycloneDX SBOM olarak CI artefaktında saklanır.

IAM-008, platform access/refresh tokenları için `flutter_secure_storage` adaptörünü ekler.
Bu adaptör Android Keystore ve iOS Keychain kullanır; parola veya Cognito tokenı saklamaz,
Android yedeğini kapatır ve iOS anahtarlarını başka cihaza taşınmayacak şekilde yapılandırır.
Android manifestindeki `allowBackup=false`, uygulama verisinin bulut/cihaz yedeğine alınmasını
engeller. `migrateWithBackup=true` ise bunun tersi değildir: yalnızca secure-storage'ın şifreleme
algoritması geçişinde kullandığı yerel geri alma mekanizmasıdır. Oturum ayrıca secretsiz uygulama
kum havuzu kurulum işaretçisiyle bağlanır; Keychain/Keystore girdisi yeniden kurulumda kalsa bile
işaretçi olmadan okunamaz. iOS eşzamanlaması kapalıdır (`synchronizable=false`) ve this-device-only
erişilebilirlik kullanılır.
Şifreli yerel iş veritabanı ve kalıcı çevrimdışı kuyruk hâlâ ilgili sonraki görevlerin karar
alanıdır.

ORG-009B, A-004R1'de doğrulanan `flutter_appauth 12.0.2` sürümünü production sistem-tarayıcısı
Authorization Code + PKCE `S256` akışı için kullanır. WebView veya elle OAuth uygulamak yerine
platform AppAuth kitaplıklarının state, nonce ve verifier doğrulaması yeniden kullanılır. IAM
HTTP yüzeyi ek bir framework olmadan dar bir `dart:io` adaptörüdür.

ORG-009C, doğrulanmış platform oturumunu sekiz dosyasız ORG ucuna bağlayan production
adapterını ekler. Ortak taşıma katmanı redirect izlemez, yanıt boyutunu sınırlar ve her fiziksel
istekte yeni `X-Request-Id` üretir. Yazma denemeleri kendi stabil `Idempotency-Key` değerini,
marka/palet/modül güncellemeleri ise `If-Match-Row-Version` ve doğrulanmış `ETag` eşleşmesini
korur. Yanıt gövdesi mutlak toplam deadline ile okunur; başarı ve hata cevaplarında tek, geçerli
JSON `Content-Type` zorunludur ve `429 RATE_LIMITED` yalnız pozitif, sınırlandırılmış
`Retry-After` ile kabul edilir. API refresh cevabı güvenli depoya yazılmadan önce yeni access
tokenla `/sessions/me` üzerinden kanonikleştirilir; rol veya görünür kurum bilgisi değişirse eski
workspace ve bekleyen ORG sonucu kapatılır. Production composition mock repository kullanmaz;
PLAT-01, PLAT-02 ve ORG-008 ekranları
UI-004 rota kataloğu üzerinden açılır. IAM sözleşmesi öğretmenin bağımsız izinlerini henüz
`sessions/me` içinde yayımlamadığından öğretmen yönetim menüsü production'da fail-closed kalır;
rota kataloğu doğrulanmış izin kaynağı enjekte edildiğinde bağımsız marka ve modül yetkilerini
birleştirmeden değerlendirmeye hazırdır.
