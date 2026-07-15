# A-004R1 Cognito mobil PKCE deneyi

Bu uygulama, Cognito Essentials managed login üzerinde gerçek iOS/Android sistem tarayıcısıyla
Authorization Code + PKCE `S256` akışını doğrular. Üretim uygulaması veya IAM entegrasyonu
değildir.

## Güvenlik sınırları

- Public mobile client secretsizdir; client secret oluşturulmaz.
- Domain ve client ID yalnız `--dart-define` ile çalışma anında verilir.
- Parola, authorization code, code verifier ve token değerleri loglanmaz, render edilmez veya
  kalıcı olarak saklanmaz.
- Deney yalnız sentetik kullanıcıyla çalışır.
- Redirect URI sabittir: `kursplatforma004r1://oauth2redirect`.
- Cognito rol/grup claim'leri ürün yetkisi olarak kullanılmaz.

## Bağımlılık gerekçesi

`flutter_appauth 12.0.2`, iOS AppAuth ve Android AppAuth kitaplıkları üzerinden sistem
tarayıcısını, `state`/`nonce` üretimini ve PKCE `S256` kod değişimini uygular. Elle WebView veya
OAuth protokolü yazmak güvenlik sorumluluğunu artıracağı için seçilmemiştir. AWS Amplify ise bu
sınırlı deney için gereksiz sağlayıcı SDK yüzeyi ekleyeceğinden kullanılmamıştır.

## Çalıştırma

Gerçek değerleri komut geçmişine veya repo dosyasına yazmadan güvenli yerel değişkenlerden verin:

```bash
flutter run \
  --dart-define=COGNITO_DOMAIN="$COGNITO_DOMAIN" \
  --dart-define=COGNITO_CLIENT_ID="$COGNITO_CLIENT_ID"
```

Uygulamadaki **Gerçek PKCE girişini başlat** düğmesi sistem tarayıcısını açar. Cognito'nun
`NEW_PASSWORD_REQUIRED` challenge'ı ile ilk parola belirlendikten sonra giriş yapılır; callback
uygulamaya dönünce ekranda authorization code, code verifier, nonce ve üç token türü için yalnız
var/yok kanıtı görünmelidir. İlk parola challenge'ının AWS doğrulaması ve kaynak yaşam döngüsü
repo kökündeki A-004R1 kanıt belgesinde kayıtlıdır.

## Kontroller

```bash
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
flutter build apk --debug
flutter build ios --simulator --debug --no-codesign
```

Cloud kaynak envanteri ve gerçek deney sonucu repo kökündeki A-004R1 kanıt belgesindedir.
