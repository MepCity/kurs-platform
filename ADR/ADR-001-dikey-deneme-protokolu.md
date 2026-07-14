# ADR-001 ek A — Mobil dikey deneme protokolü

| Alan | Değer |
|---|---|
| Bağlı ADR | `ADR-001-mobil-framework.md` |
| Amaç | Flutter kararını iOS ve Android'de temel mobil akışla doğrulamak |
| Durum | Tamamlandı — Android ve iOS VT-01–VT-03 PASS |
| Görev | A-001 |

## Amaç

Bu deneme, tasarım gösterimi değil; sentetik giriş, sınıf/öğrenci listesi ve basit yoklama
etkileşiminin iOS ile Android'de çalıştığını gösteren küçük bir framework denemesidir. Kalıcı
kuyruk, idempotency, ağ hata sınıflandırması, gerçek zamanlı taşıma ve eşitleme bu kabul
kapısının dışındadır; bunlar A-005/A-006 kararlarına aittir.

## Önkoşullar

- A-001'in izole spike'ı, `experiments/a001_flutter_spike` altında ve A-011'den bağımsızdır.
  Gerekli platform sarmalayıcıları bu dizindeki kesin `flutter create` komutuyla üretilir.
- Deneme, yalnız sabit sentetik ORG-A verisi kullanır: hoca, Gül Sınıfı ve iki öğrenci.
- Bir iOS hedefi (simülatör + mümkünse gerçek cihaz) ve bir Android emülatörü veya gerçek cihaz
  bulunur. Yayın kapısındaki gerçek cihaz gereksiniminin yerine simülatör geçirilmez.

## Kabul senaryoları

| Kimlik | Akış | Beklenen kanıt | Başarısızlık ölçütü |
|---|---|---|---|
| VT-01 | Sentetik giriş → sınıf/öğrenci listesi | Girişten sonra Gül Sınıfı ile Ayşe Demir ve Mehmet Kaya görünür. | Beklenen öğrenci listesi görünmez. |
| VT-02 | Yoklama ekranını açma | “Bugünkü Yoklama” ekranı açılır. | Rota/ekran açılamaz. |
| VT-03 | Basit yoklama etkileşimi | “Geldi olarak işaretle” sonrası “Ayşe Demir: Geldi” görünür. | Etkileşimden sonra durum değişmez. |

Bu deneme yalnız framework/araç zinciri fizibilitesini ölçer. P-010 sözleşmesinin kalıcı kuyruk,
idempotency, çakışma ve eşitleme kuralları bu denemeyle doğrulanmış sayılmaz.

## Uygulama ve test yöntemi

1. İzole ve üretim dışı `experiments/a001_flutter_spike` dizinindeki kaynaklar kullanılır; bu
   dizin nihai mobil modül iskeleti değildir.
2. `flutter analyze` ve widget testi çalıştırılır.
3. Flutter'ın resmi `integration_test` paketiyle VT-01–VT-03 iOS ve Android hedeflerinde
   çalıştırılır; test sonuçları cihaz/işletim sistemi/sürüm ile saklanır.

## Karar kuralı

- iOS **ve** Android'de VT-01, VT-02 ve VT-03'ün tamamı için ayrı ayrı nihai `All tests passed`
  sonucu varsa Flutter önerisi `Kabul edildi` durumuna geçirilebilir.
- Ortam veya cihaz yokluğu FAIL değildir; fakat kanıt da değildir. Durum `NOT_RUN` kalır ve
  ADR `Öneri` durumunda tutulur.

## 14 Temmuz 2026 deney raporu

### İzolasyon ve uygulama

- Kaynak dizini: `experiments/a001_flutter_spike` (üretim dışı; A-011 mobil modülü değildir).
  İlk geçici çalıştırma `/tmp/kurs_platform_a001_spike` altında yapılmıştır; artık aynı kaynak
  ve kesin platform üretim komutu repo içinde incelenebilir durumdadır.
- Uygulama: sentetik giriş, `ORG-A — Gül Sınıfı` öğrenci listesi ve tek “Geldi” yoklama
  düğümü; gerçek API, kullanıcı verisi, kalıcı kuyruk veya gerçek zamanlı kanal içermez.
- Testler: bir widget testi ve `integration_test/spike_flow_test.dart`.

### SDK ve hedefler

| Bileşen | Ölçülen sürüm / hedef |
|---|---|
| Flutter | 3.44.6 stable, framework `ee80f08bbf` |
| Dart | 3.12.2 |
| Java | OpenJDK 21.0.9 |
| Android SDK | Platform 36, Build-Tools 36.0.0, Platform-Tools 37.0.0 |
| Android Emulator | 36.6.11.0 |
| Android AVD | Pixel 7, Android 16 / API 36, Google APIs, ARM64-v8a |
| iOS | Çalıştırılamadı: yalnız Command Line Tools var; tam Xcode ve `simctl` yok |

### Birebir komutlar ve sonuçlar

| Komut | Sonuç | Süre / çıktı |
|---|---|---|
| `flutter create --platforms=android,ios --org org.mepcity.kursplatform /tmp/kurs_platform_a001_spike` | PASS | 4,0 sn; 75 dosya (geçici) |
| `flutter analyze` | PASS | 5,29 sn; sorun yok |
| `flutter test` | PASS | 8,38 sn; 1 widget testi geçti |
| `flutter build apk --debug` | PASS | İlk deneme NDK kurulumu nedeniyle ilerlemesiz kaldı ve sonlandırıldı; emülatör kapatılarak tekrarlanan temiz derleme APK üretti |
| `ls -lh build/app/outputs/flutter-apk/app-debug.apk` | PASS | Son ölçümde `app-debug.apk`: 178 MB (önceki çok ABI'li denemede 141 MB) |
| `emulator -avd A001_API_36 -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect` | PASS | `adb`: `emulator-5554 device`; sistem sürümü 16, model `sdk_gphone64_arm64` |
| `flutter test integration_test/spike_flow_test.dart -d emulator-5554 -r expanded` | KISMİ | APK derlendi ve AVD'ye kuruldu; araç akışı nihai PASS/FAIL satırını vermedi. Çıkış kodu ve `All tests passed` satırı yakalanmadığı için sonuç PASS kabul edilmez. |
| `adb shell am start -n org.mepcity.kursplatform.kurs_platform_a001_spike/.MainActivity` | KISMİ | `MainActivity` başladı, ancak 12 saniye zaman aşımında Flutter splash ekranından uygulama içeriğine geçiş gözlenmedi; VT-01–VT-03 PASS değildir. |
| `flutter logs -d emulator-5554` ve `adb logcat` | KISMİ | Zaman aşımı penceresinde uygulama içeriğine geçişi doğrulayan veya kesin kök neden veren uygulama hatası kayda alınamadı. Teşhis: splash takılması gözlendi, kök neden kanıtlanmadı; yeniden koşuda `-r expanded`, çıkış kodu, iki günlük ve zaman aşımı birlikte saklanmalıdır. |
| `xcrun simctl list devices available` | BLOCKED | `simctl` yok; tam Xcode kurulumu gerekli |

### 14 Temmuz 2026 mevcut ortam doğrulaması

Bu repo içindeki kaynaklarla tekrar çalıştırılan araç doğrulamaları aşağıdadır. Önceki Android
AVD/SDK geçici çalışma alanı temizlendiğinden bu ortamda emülatör bağlı değildir; bu, splash
gözlemini açıklayan bir log değildir ve Android kabul kanıtı değildir.

| Komut | Exit code | Süre | Sonuç |
|---|---:|---:|---|
| `flutter analyze` | 0 | 5,31 sn | PASS — `No issues found!` |
| `flutter test` | 0 | 2,21 sn | PASS — `All tests passed!` (1 widget testi; VT-01'de `ORG-A — Gül Sınıfı`, Ayşe Demir ve Mehmet Kaya doğrulanır) |
| `dart format lib test integration_test` | 0 | 0,01 sn | PASS — 3 dosya biçimlendirildi |
| `dart format --set-exit-if-changed lib test integration_test` | 0 | 0,01 sn | PASS — `0 changed`; biçim denetimi temiz |
| `git diff --check` | 0 | < 0,01 sn | PASS |
| `flutter test integration_test/spike_flow_test.dart -d emulator-5554 -r expanded` | 1 | 0,7 sn | NOT_RUN — `emulator-5554` bağlı değil; nihai test sonucu yok |
| `flutter logs -d emulator-5554` | 1 | < 0,1 sn | NOT_RUN — bağlı cihaz yok |
| `adb logcat -d -v threadtime` | 127 | < 0,1 sn | NOT_RUN — bu ortamda `adb` PATH'te değil |
| `xcodebuild -version` | 1 | < 0,1 sn | BLOCKED — etkin geliştirici dizini yalnız Command Line Tools |
| `xcrun simctl list devices available` | 72 | < 0,1 sn | BLOCKED — `simctl` kurulu değil |

Mevcut macOS hedefi `macOS 26.1 (25B78, darwin-arm64)` olarak görünmektedir; ancak tam Xcode
olmadığı için iOS Simulator cihazı/işletim sistemi sürümü ve iOS entegrasyon testi sonucu yoktur.
Tam Xcode bulunan macOS ortamında README'deki iOS komutu çalıştırılmalı, cihaz adı ve OS sürümü,
Flutter sürümü, exit code, süre ve nihai `All tests passed` satırı bu tabloya eklenmelidir.

Android splash teşhisi sonuçsuzdur: önceki AVD denemesinde 12 saniye zaman aşımında içerik
görünmedi, fakat `flutter logs`/`adb logcat` kök nedeni kanıtlayan uygulama hatası vermedi ve
nihai test süreci exit code'u yakalanmadı. Bu yüzden neden hakkında çıkarım yapılmamış, Android
durumu PASS yazılmamıştır. Yeniden tanı için AVD yeniden kurulup aynı anda `-r expanded` çıktısı,
komut exit code'u, `flutter logs`, `adb logcat` ve 12 saniyelik zaman aşımı kaydedilmelidir.

Yeniden kurulum 14 Temmuz 2026'da başlatıldı: Android Command-line Tools ile `platform-tools`
kuruldu, ancak `emulator` paketi indirmesi “Preparing Install Android Emulator v.36.6.11”
aşamasından sonra bu çalışma ortamında tamamlanmadı; AVD oluşturulamadı. Bu bir splash kök neden
sonucu değildir. Android VT-01–VT-03 yeniden çalıştırılmadı ve ADR durumu değiştirilmedi.

### Düzeltme turu doğrulaması

Bu turda Android Emulator `36.6.11.0` ikilisi doğrudan doğrulanmış arşivden kuruldu; ancak
Android 16 / API 36 Google APIs ARM64 sistem imajı (`1.872.691.175` bayt) bu ortamda kurulu
olmadığından AVD oluşturulamadı. Bu nedenle ne eski splash belirtisi yeniden üretilebildi ne de
kök neden için `flutter logs`/`adb logcat` alınabildi.

| Hedef / komut | Exit code | Süre | Nihai çıktı |
|---|---:|---:|---|
| `flutter pub get` | 0 | 0,87 sn | Bağımlılıklar çözüldü |
| `flutter analyze` | 0 | 5,35 sn | `No issues found!` |
| `flutter test -r expanded` | 0 | 2,45 sn | `All tests passed!` (yalnız widget testi) |
| `dart format --output=none --set-exit-if-changed lib test integration_test` | 0 | 0,01 sn | `0 changed` |
| Android: `flutter test integration_test/spike_flow_test.dart -d emulator-5554 -r expanded` | 1 | < 1 sn | `No supported devices found ... emulator-5554` — NOT_RUN |
| iOS: `xcodebuild -version` | 1 | < 1 sn | Etkin dizin yalnız Command Line Tools — BLOCKED |
| iOS: `xcrun simctl list devices available` | 72 | < 1 sn | `simctl` bulunamadı — BLOCKED |

Bu turda Android cihaz/simülatör adı ile OS/API sürümü, iOS Simulator adı ve iOS sürümü, her iki
entegrasyon testinin süresi ve nihai `All tests passed` satırı **yoktur**. Dolayısıyla bunlar
platform PASS kanıtı değildir ve ADR durumunu değiştirmez.

### Kabul koşusu — GitHub Actions #29358690335

PR #15'in platform kaynaklarını içeren
[A-001 platform spike](https://github.com/MepCity/kurs-platform/actions/runs/29358690335)
iş akışı iki hedefi paralel çalıştırmış ve `success` sonucuyla tamamlanmıştır. İş akışı Flutter
`3.44.6` etiketini kullanır; analiz, widget testi ve hedef üzerindeki entegrasyon testi aynı
kaynaklardan çalıştırılır. Entegrasyon çıktısındaki `All tests passed!` satırı ayrıca iş kapısı
olarak doğrulanır.

| Hedef | Ortam | Entegrasyon sonucu | İş sonucu |
|---|---|---|---|
| Android | Ubuntu 24.04; Android 16 / API 36; `sdk_gphone64_x86_64` emülatörü | `00:04 +1: All tests passed!` | PASS — 7 dk 52 sn |
| iOS | macOS 15; Xcode 16.4 (`16F6`); iPhone 16 Pro Simulator; iOS 18.5 | `00:06 +1: All tests passed!` | PASS — 11 dk 58 sn |

Her entegrasyon testi tek senaryoda VT-01 sentetik giriş ve `ORG-A — Gül Sınıfı` listesini,
VT-02 yoklama ekranına geçişi ve VT-03 Ayşe Demir'in “Geldi” durumuna alınmasını doğrular.
Android ve iOS işleri aynı head committe başarıyla tamamlandığından karar kuralı sağlanmıştır.
Yerel ortam raporları tarihsel teşhis kaydıdır ve bu CI kabul kanıtının yerine kullanılmaz.

### Kanıt durumu

| Hedef | VT-01–VT-03 | Durum |
|---|---|---|
| Android 16 / API 36 emülatörü | VT-01–VT-03; `All tests passed!` | PASS |
| iOS 18.5 / iPhone 16 Pro Simulator | VT-01–VT-03; `All tests passed!` | PASS |

Bu nedenle ADR-001 `Kabul edildi` durumundadır. A-011 sonrasında gerçek modül ve gerçek cihaz
denemesi ayrı bir downstream kalite kapısıdır; A-001 spike'ının önkoşulu değildir.
