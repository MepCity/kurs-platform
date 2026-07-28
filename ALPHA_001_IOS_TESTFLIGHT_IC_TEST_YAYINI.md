# ALPHA-001 — Dalga 2 iOS TestFlight İç Test Yayını

## Görev başlangıç sözleşmesi

- **Görev:** ALPHA-001 — Dalga 2 iOS TestFlight iç test yayınını hazırla.
- **Bağımlılıklar:** Dalga 2'nin IAM/ORG/mobil entegrasyonu `main` üzerinde tamamlandı.
- **Değiştirilen alan:** iOS Runner/RunnerTests kimliği, signing girdisi, sürüm bilgisi,
  yapılandırma regresyon testi ve bu yayın kanıtı.
- **Beklenen çıktı:** Yalnız sentetik verili kapalı alfada internal TestFlight grubuna
  dağıtılan ve gerçek iPhone'da doğrulanan bir `0.2.0` build'i.
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
| Build | `2` (`1`, Cognito scope uyuşmazlığı nedeniyle reddedildi ve expire edildi) |
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
| Commit SHA | `0cb326b67d8dbd1d9f20ef59f8e2d710228d1e96` |
| Kaynak Xcode sürümü | PASS — Xcode 26.6 (`17F113`) |
| Apple Team / Automatic Signing | PASS — üyelik, Team, Apple Distribution sertifikası/private key ve App Store profili doğrulandı; signing sırrı repoya alınmadı |
| App ID | PASS — `com.mepcity.kursplatform` kaydedildi |
| App Store Connect uygulama kaydı | PASS — `Kurs Platform`, Apple ID `6795416553`, iOS, Türkçe, `KURSPLATFORM-IOS-001` |
| Archive (`0.2.0 (2)`) | PASS — exact release define'larıyla imzalı Release archive |
| Export edilen IPA SHA-256 | `bf3555c865560b21653d9f0bf27ac34ae9335667fa5423c320fa265be92f5c62` |
| Archive executable SHA-256 | `e61de137c5958b7073092a119ff91126aa6349e10d83ec9907916f9e2422a9d9` |
| App Store validation | PASS — Xcode Organizer validation başarılı |
| App Store Connect upload | PASS — internal testing only upload başarılı |
| Build processing/export compliance | PASS — build `Ready to Test`; non-exempt encryption `No` |
| Internal grup erişimi | PASS — `Dalga 2 İç Test`, 1 internal tester, build 2 gruba eklendi; otomatik dağıtım/public link/external testing kapalı |

Build 1 aynı TestFlight grubuna ulaştı ancak uygulamanın istediği `openid profile` kapsamı Cognito
client'taki yalnız `openid` izniyle uyuşmadığı için gerçek cihaz girişinde `invalid_scope` verdi.
Mobil kapsam `openid` olarak düzeltildi, build 2 üretildi ve hatalı build 1 App Store Connect'te
expire edilerek test erişiminden kaldırıldı.

Archive üretildiğinde `.xcarchive` dizininin kendisi repoya eklenmez. Kanıt için archive içindeki
ürün uygulamasının ve export edilen IPA'nın SHA-256 özeti, Xcode sürümü, commit SHA, sürüm/build
ve App Store Connect durumu bu tabloya yazılır; Apple hesabı veya signing sırrı yazılmaz.

## Yerel kalite kapıları

| Kontrol | Sonuç |
|---|---|
| `dart format --output=none --set-exit-if-changed lib test tool` | PASS |
| `flutter analyze` | PASS — sorun yok |
| `flutter test` | PASS — 613 test |
| `flutter build apk --debug` | PASS |
| `dart run tool/ios_release_config_verifier.dart` | PASS |
| `flutter build ios --debug --simulator --no-codesign` | PASS |
| `tooling/check_repo_boundaries.sh` ve regresyon testi | PASS |
| `tooling/check_no_secrets.sh` ve regresyon testi | PASS |
| Eski bundle ID mekanik taraması | PASS — kalıntı yok |
| `git diff --check` | PASS |

Main kalite çalışması `30358454563` SUCCESS olduktan sonra `origin/main`, rebase/squash/amend
yapılmadan normal merge commit'i `fbe90bf` ile branch'e alındı. Scope düzeltmesi
`0cb326b` commit'indedir. Draft PR [#64](https://github.com/MepCity/kurs-platform/pull/64) için
Actions çalışması `30362880306` içindeki backend, Android, iOS release ve repo/güvenlik işleri
PASS oldu. PR merge edilmedi.

## Gerçek iPhone smoke testi

| Senaryo | Sonuç | Kanıt/not |
|---|---|---|
| TestFlight'tan kurulum | PASS | Build `0.2.0 (2)` gerçek iPhone'a TestFlight ile kuruldu; model/iOS sürümü kullanıcıdan bekleniyor |
| İlk açılış; eksik config yok | PASS | Release config ile uygulama açıldı |
| Cognito sistem tarayıcısı + PKCE giriş | PASS | Cognito Hosted UI açıldı ve callback uygulamaya döndü; token/parola kaydedilmedi |
| Bağlam seçimi ve aktivasyon | PASS | Sentetik kullanıcı için `Platform yöneticisi` bağlamı seçildi; Kurumlar kabuğu açıldı |
| Kurum listeleme | **FAIL** | 16:31:13'te backend `500 INTERNAL_ERROR`; `Tekrar Dene` iki kez basıldığında ekran değişmedi |
| Kurum oluşturma | BLOKE | Listeleme 500 nedeniyle çalıştırılmadı; gerçek veri girilmedi |
| Marka, renk paleti ve modül ayarları | BLOKE | Kurum oluşturulamadığı için çalıştırılmadı |
| Oturum refresh (`sessions/me`) | BLOKE | Kurum listeleme kabul kapısında duruldu; token değeri kaydedilmedi |
| Logout | BLOKE | Kurum listeleme kabul kapısında duruldu |
| Tekrar giriş | BLOKE | Logout çalıştırılamadığı için çalıştırılmadı |
| Kapat/aç ve arka plan/ön plan sonrası güvenli oturum | BLOKE | Kurum listeleme kabul kapısında duruldu |
| Yetkisiz durumun güvenli görünmesi | ÇALIŞTIRILMADI | Bu build kabul edilmediği için ek negatif test yapılmadı |
| Bağlantı hatasının güvenli görünmesi | PASS | Backend ayrıntısı/token göstermeyen genel hata ve tekrar eylemi sunuldu |
| Crash, hassas log ve gerçek veri yok | KISMİ PASS | Blokere kadar crash, hassas veri/log sızıntısı veya gerçek veri gözlenmedi; tam matris tamamlanmadı |

Smoke testi TestFlight'tan kurulan işlenmiş build üzerinde yapılır; Xcode ile doğrudan cihaza
kurulan debug/release build bu kabulün yerine geçmez.

Kurum listesi hatası cold start olarak sınıflandırılmadı: aynı anda public `/health` `200` ve
`238 ms` döndü, kurum isteği Render request logunda `ERROR`/500 olarak tamamlandı ve iki kullanıcı
tekrarı da aynı sonucu verdi. ALPHA-002 sırasında uyuyan Render Free instance için ölçülen cold
start `26.338 s`, hemen sonraki sıcak health `205 ms` idi; bunlar bu telefon denemesinin kesin
süresi değil, ortamın daha önce kaydedilmiş referans ölçümleridir.

## Kaynak ve maliyet envanteri

| Kaynak | Ortam/bölge | Veri | Aylık maliyet | Durum |
|---|---|---|---:|---|
| Public HTTPS backend | Render Free, Frankfurt | Sentetik | `0 USD/ay` hedefi | Public health çalışıyor; kurum listeleme çağrısı tekrarlanabilir 500 üretiyor |
| PostgreSQL | Supabase Free | Sentetik | `0 USD/ay` hedefi | ALPHA-002'de migration/RLS/runtime rolü doğrulandı |
| Cognito User Pool/app client | `eu-central-1` | Sentetik hesap | `0 USD/ay` hedefi | Secretsiz native client, authorization code + PKCE doğrulandı |
| Apple Developer Program | Aktif | Uygulama metadata/build | Mevcut üyelik | App ID, uygulama kaydı, signing ve internal grup doğrulandı |

Kapalı alfa hedefi `0 USD/ay` dış ödemedir; fiyat garantisi değildir. Yeni ücretli backend,
PostgreSQL, Cognito mesajlaşma/add-on, alan adı veya başka dış kaynak yazılı kullanıcı onayı
olmadan açılmaz.

## Bilinen sınırlamalar ve açık kapılar

- AppIcon Flutter şablon ikonudur; Xcode build sırasında uyarı görülmesine rağmen Organizer
  validation ve internal-only upload başarılı oldu. Public App Store yayını kapsam dışıdır.
- Build 2'de kurum listeleme gerçek cihazda tekrarlanabilir `500 INTERNAL_ERROR` ile başarısızdır.
  Güvenli genel hata görünse de kurum oluşturma ve sonraki smoke adımları blokelendi. Backend
  kök nedeninin ayrı sahiplikte giderilmesi ve artırılmış build numarasıyla yeni upload gerekir.
- Gerçek iPhone modeli ve iOS sürümü henüz kullanıcıdan alınmadı.
- Build 1 Cognito scope hatası nedeniyle reddedildi ve expire edildi. Build 2 de kurum listeleme
  500'ü nedeniyle kabul edilmedi; bu nedenle ALPHA-001 **IN_PROGRESS** kalır ve DONE raporlanmaz.

## Resmî Apple başvuruları

- [Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)
- [Add internal testers](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers/)
- [TestFlight overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)
- [Export compliance overview](https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance/)
- [`ITSAppUsesNonExemptEncryption`](https://developer.apple.com/documentation/BundleResources/Information-Property-List/ITSAppUsesNonExemptEncryption)
