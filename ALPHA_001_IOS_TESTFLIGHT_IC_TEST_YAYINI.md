# ALPHA-001 — Dalga 2 iOS TestFlight İç Test Yayını

## Görev başlangıç sözleşmesi

- **Görev:** ALPHA-001 — Dalga 2 iOS TestFlight iç test yayınını hazırla.
- **Bağımlılıklar:** Dalga 2'nin IAM/ORG/mobil entegrasyonu `main` üzerinde tamamlandı.
- **Değiştirilen alan:** iOS Runner/RunnerTests kimliği, signing girdisi, sürüm bilgisi,
  yapılandırma regresyon testi ve bu yayın kanıtı.
- **Beklenen çıktı:** Yalnız sentetik verili kapalı alfada internal TestFlight grubuna
  dağıtılan ve gerçek iPhone'da doğrulanan `0.2.0 (1)` build'i.
- **Kabul:** Görev promptundaki bundle, archive, validation, upload, internal grup, gerçek cihaz,
  secret/PII ve Android regresyon ölçütlerinin tamamı kanıtlanır.
- **Test yöntemi:** Mobil format/analyze/test/build kontrolleri, repo sınır/secret kontrolleri,
  imzalı Xcode archive/validation/upload ve aşağıdaki gerçek cihaz matrisi.

`GOREV_DURUMU.md` bu görevde değiştirilmez. Public App Store yayını, external testing ve public
TestFlight linki açılmaz.

## Sabit yayın kimliği

| Alan | Değer |
|---|---|
| Uygulama adı | Kurs Platform |
| Platform | iOS |
| Bundle ID | `com.mepcity.kursplatform` |
| RunnerTests bundle ID | `com.mepcity.kursplatform.RunnerTests` |
| OAuth callback | `kursplatform://oauth2redirect` |
| Sürüm | `0.2.0` |
| Build | `1` |
| App Store Connect SKU | `KURSPLATFORM-IOS-001` |
| TestFlight grubu | `Dalga 2 İç Test` |
| Dağıtım | Internal testing only |

Bundle ID ve sürüm App Store Connect upload eşleşmesinin parçasıdır; build numarası aynı sürümde
her yeni yüklemede artırılır. Bu build App Store review'a gönderilmez.

## Signing ve yerel hazırlık

Runner ve RunnerTests Debug/Release/Profile yapılandırmaları Automatic Signing kullanır.
`DEVELOPMENT_TEAM`, Git'ten hariç tutulan `apps/mobile/ios/Flutter/Signing.xcconfig` içindeki
`KURS_PLATFORM_IOS_DEVELOPMENT_TEAM` değerinden alınır. Örnek dosya:
`apps/mobile/ios/Flutter/Signing.xcconfig.example`.

Automatic Signing sınırı:

- Debug gerçek cihaz build'i Apple Development kimliğiyle oluşturulur.
- App Store archive/export Apple Distribution kimliği ve App Store provisioning ile oluşturulur.
- Sertifika private key'i, provisioning profile veya Apple giriş bilgisi repoya kopyalanmaz.
- Simulator build signing gerektirmez ve gerçek cihaz/archive kabulünün yerine geçmez.

## Sentetik kapalı alfa ortamı

Bu görev yalnız `development` adlı, public HTTPS API kullanan geçici kapalı alfa profilini kabul
eder. Gerçek kurum, öğrenci, veli, yoklama, ilerleme, PDF veya rapor verisi yasaktır. Davetli
insan tester hesabında tercihen takma kullanıcı adı kullanılır.

Release build'e aşağıdaki public değerler `--dart-define` ile verilir; belgeye gerçek değerleri
değil yalnız kullanılan kaynağın adı ve doğrulama sonucu yazılır:

- `KURS_PLATFORM_ENVIRONMENT=development`
- `KURS_PLATFORM_PUBLIC_API_BASE_URL`
- `KURS_PLATFORM_COGNITO_ISSUER_URI`
- `KURS_PLATFORM_COGNITO_CLIENT_ID`
- `KURS_PLATFORM_COGNITO_REDIRECT_URI=kursplatform://oauth2redirect`

Backend adresi public HTTPS, PostgreSQL ortamı yalnız sentetik veri, Cognito app client secretsiz
public native client ve callback tam eşleşmeli olmalıdır. Ortam kaynağı henüz yoksa ücretli yeni
kaynak kullanıcı onayı olmadan açılmaz.

## App Store Connect iç test bilgileri

- **Beta açıklaması:** Kurum yöneticileri için Dalga 2 kimlik, güvenli oturum ve dosyasız kurum
  yönetimi kapalı alfa build'i. Yalnız sentetik veriyle kullanılır.
- **What to Test:** Sistem tarayıcısı + PKCE girişini, bağlam aktivasyonunu, kurum listeleme ve
  oluşturmayı, ad/renk/modül ayarlarını, oturum refresh/logout akışını, cihaz listeleme ve oturum
  iptalini, uygulama yeniden açıldığında güvenli oturum geri yüklemeyi, yetkisiz ve bağlantı
  hatalarının güvenli görünmesini doğrulayın. Gerçek kurum veya öğrenci verisi girmeyin. Crash,
  token ya da hassas log görürseniz build numarası ve adımlarla feedback gönderin.
- **Feedback e-postası:** PASS — ürün sahibinin onayladığı ve erişebildiği adres App Store
  Connect'e kaydedildi; kişisel adres repo belgesinde tutulmaz.
- **Export compliance:** Mobil kod özel/non-standard kriptografi uygulamaz; TLS ve iOS Keychain
  gibi platform güvenlik yüzeylerini kullanır. Apple soru akışı bu kullanımın muaf olduğunu
  doğruladığında `ITSAppUsesNonExemptEncryption=NO` beyanı kullanılır. Sonuç belirsiz veya Apple
  belge isterse tahmin edilmez; upload bloke edilip uzman/Apple desteğine yöneltilir.

Internal grup otomatik dağıtım kapalı oluşturulur ve build manuel eklenir. Böylece hangi build'in
test edildiği kanıtlanır. External testing, public link ve App Store submission açılmaz.

## Archive, validation ve upload kaydı

| Kanıt | Sonuç |
|---|---|
| Commit SHA | Bekliyor |
| Kaynak Xcode sürümü | PASS — Xcode 26.6 (`17F113`); iOS 26.5 simulator runtime indirmesi sürüyor |
| Apple Team / Automatic Signing | KISMİ PASS — üyelik aktif, App ID ve yerel Team ID girdisi hazır; signing kimliği/provisioning bekliyor |
| App ID | PASS — `com.mepcity.kursplatform` kaydedildi |
| App Store Connect uygulama kaydı | PASS — `Kurs Platform`, iOS, Türkçe, `KURSPLATFORM-IOS-001` |
| Archive (`0.2.0 (1)`) | Bekliyor |
| Archive SHA-256 | Bekliyor |
| App Store validation | Bekliyor |
| App Store Connect upload | Bekliyor |
| Build processing/export compliance | Bekliyor |
| Internal grup erişimi | KISMİ PASS — `Dalga 2 İç Test` oluşturuldu, otomatik dağıtım kapalı; 0 tester/0 build |

Archive üretildiğinde `.xcarchive` dizininin kendisi repoya eklenmez. Kanıt için archive içindeki
ürün uygulamasının ve export edilen IPA'nın SHA-256 özeti, Xcode sürümü, commit SHA, sürüm/build
ve App Store Connect durumu bu tabloya yazılır; Apple hesabı veya signing sırrı yazılmaz.

## Yerel kalite kapıları

| Kontrol | Sonuç |
|---|---|
| `dart format --output=none --set-exit-if-changed lib test tool` | PASS |
| `flutter analyze` | PASS — sorun yok |
| `flutter test` | PASS — 612 test |
| `flutter build apk --debug` | PASS |
| `dart run tool/ios_release_config_verifier.dart` | PASS |
| `flutter build ios --debug --simulator --no-codesign` | Bekliyor — Xcode 26.6 ve CocoaPods 1.17.0 hazır; iOS 26.5 simulator runtime kuruluyor |
| `tooling/check_repo_boundaries.sh` ve regresyon testi | PASS |
| `tooling/check_no_secrets.sh` ve regresyon testi | PASS |
| Eski bundle ID mekanik taraması | PASS — kalıntı yok |
| `git diff --check` | PASS |

## Gerçek iPhone smoke testi

| Senaryo | Sonuç | Kanıt/not |
|---|---|---|
| TestFlight'tan kurulum | Bekliyor | Cihaz/OS bilgisi secretsiz kaydedilecek |
| İlk açılış; eksik config yok | Bekliyor | |
| Cognito sistem tarayıcısı + PKCE giriş | Bekliyor | |
| Bağlam seçimi ve aktivasyon | Bekliyor | |
| Kurum listeleme | Bekliyor | |
| Kurum oluşturma | Bekliyor | Yalnız sentetik kurum |
| Marka, renk paleti ve modül ayarları | Bekliyor | |
| Oturum refresh | Bekliyor | Token değeri kaydedilmez |
| Logout | Bekliyor | |
| Cihaz listeleme ve cihaz oturumu iptali | Bekliyor | |
| Kapat/aç sonrası güvenli oturum geri yükleme | Bekliyor | |
| Yetkisiz durumun güvenli görünmesi | Bekliyor | |
| Bağlantı hatasının güvenli görünmesi | Bekliyor | |
| Crash, hassas log ve gerçek veri yok | Bekliyor | |

Smoke testi TestFlight'tan kurulan işlenmiş build üzerinde yapılır; Xcode ile doğrudan cihaza
kurulan debug/release build bu kabulün yerine geçmez.

## Kaynak ve maliyet envanteri

| Kaynak | Ortam/bölge | Veri | Aylık maliyet | Durum |
|---|---|---|---:|---|
| Public HTTPS backend | Belirlenecek | Sentetik | Belirlenecek | Render oturumu yok; mevcut kaynak doğrulanamadı |
| PostgreSQL | Belirlenecek | Sentetik | Belirlenecek | Supabase oturumu yok; mevcut kaynak doğrulanamadı |
| Cognito User Pool/app client | `eu-central-1` hedefi | Sentetik hesap | Belirlenecek | AWS oturumu yok; mevcut kaynak doğrulanamadı |
| Apple Developer Program | Aktif | Uygulama metadata/build | Mevcut üyelik | App ID, uygulama kaydı ve internal grup hazır |

Kapalı alfa hedefi `0 USD/ay` dış ödemedir; fiyat garantisi değildir. Yeni ücretli backend,
PostgreSQL, Cognito mesajlaşma/add-on, alan adı veya başka dış kaynak yazılı kullanıcı onayı
olmadan açılmaz.

## Bilinen sınırlamalar ve açık kapılar

- Xcode 26.6 ve CocoaPods kurulmuştur; iOS 26.5 simulator runtime kurulumu sürmektedir. Sistem
  `xcode-select` değeri hâlâ Command Line Tools'u gösterdiğinden build komutları geçici olarak
  açık `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` ile çalıştırılır.
- Apple Developer üyeliği 28 Temmuz 2026 tarihinde aktif doğrulanmıştır. App ID, App Store
  Connect uygulama kaydı ve internal grup hazırdır; Xcode hesabı, Development/Distribution
  signing kimlikleri ve provisioning profilleri henüz hazırlanmadı.
- Public HTTPS backend/PostgreSQL/Cognito kapalı alfa zincirinin çalışan kaynakları henüz
  doğrulanmamıştır; AWS, Render ve Supabase oturumları açık değildir.
- AppIcon halen Flutter şablon ikonudur. TestFlight validation bunu uyarı veya hata olarak
  raporlarsa ürün sahibi onaylı geçici kapalı-alfa ikonu olmadan upload başarılı sayılmaz.
- Feedback e-posta adresi ürün sahibi tarafından doğrulanmalıdır.
- Archive, validation, upload, TestFlight processing/internal grup ve gerçek iPhone smoke testi
  tamamlanmadan ALPHA-001 bitmiş sayılmaz.

## Resmî Apple başvuruları

- [Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)
- [Add internal testers](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers/)
- [TestFlight overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)
- [Export compliance overview](https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance/)
- [`ITSAppUsesNonExemptEncryption`](https://developer.apple.com/documentation/BundleResources/Information-Property-List/ITSAppUsesNonExemptEncryption)
