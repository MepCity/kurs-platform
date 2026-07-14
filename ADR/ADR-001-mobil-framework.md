# ADR-001 — Mobil framework seçimi

| Alan | Değer |
|---|---|
| Durum | Kabul edildi |
| Tarih | 14 Temmuz 2026 |
| Görev | A-001 — Flutter ve alternatif mobil framework karşılaştırması/dikey deneme |
| Karar | iOS ve Android için Flutter |
| Bağımlılıklar | `EKRAN_ENVANTERI.md` (P-007), `SENKRONIZASYON_VE_CAKISMA.md` (P-010) |

## Bağlam

İlk sürüm, iOS ve Android için tek bir mobil uygulama ister. Uygulamanın giriş, sınıf/öğrenci
listesi, günlük yoklama, yerel kalıcı kayıt ve güvenli eşitleme akışlarını iki platformda aynı
iş kurallarıyla sunması gerekir. Mobil katmanlar Presentation, Application, Domain, Data ve
Core olarak ayrılmalıdır (`URUN_VE_UYGULAMA_PLANI.md` §11.2).

Bu seçim özellikle aşağıdaki bağlayıcı kuralları taşıyabilmelidir:

- Yerel yazma, sunucunun kesin başarısı olmadan başarılı sayılmaz veya kalıcı kuyruktan
  silinmez.
- Her yazma aynı mantıksal yeniden denemede aynı `clientMutationId` ve `Idempotency-Key`
  ile gönderilir.
- Yerel önbellek ve kuyruk kullanıcı + kurum bağlamına göre ayrılır; erişim belirteci kuyrukta
  tutulmaz.
- Yoklama yazımı, sunucunun kanonik sonucunu ve varsa `concurrentChange` bilgisini görünür
  biçimde uzlaştırır.
- Korumalı ekranda yükleniyor, boş, hata ve yetkisiz durumları ele alınır.

Bu kurallar framework seçimiyle gevşetilemez; kaynakları sırasıyla
`SENKRONIZASYON_VE_CAKISMA.md` §2, §4–§6 ve `EKRAN_ENVANTERI.md` §1.2'dir.

## Seçenekler ve ağırlıklı karar matrisi

Puanlar 1 (zayıf)–5 (güçlü) arasındadır. Ağırlıklar bu kararın kapsamını, yani ilk sürümdeki
giriş–öğrenci listesi–yoklama akışını yansıtır; kalıcı kuyruk, gerçek zamanlı taşıma ve bunların
sağlayıcıları A-005/A-006'nın ayrı kararı olduğundan matrise dahil edilmemiştir.

| Ölçüt | Ağırlık | Flutter | React Native | Kotlin Multiplatform + Compose | Ayrı native (SwiftUI + Kotlin) |
|---|---:|---:|---:|---:|---:|
| iOS + Android tek UI kod tabanı | 25 | 5 | 5 | 4 | 1 |
| Küçük dikey akışın derlenmesi/cihazda test edilmesi | 20 | 5 | 4 | 3 | 4 |
| Katmanlı Presentation/Application/Domain/Data/Core yapısına uyum | 15 | 5 | 4 | 4 | 5 |
| Platform API'sine kaçış ve platform görünümü | 15 | 4 | 4 | 5 | 5 |
| Başlangıç araç zinciri ve bakım yüzeyi | 15 | 4 | 3 | 2 | 2 |
| Ekip öğrenme/iki platformda teslim hızı | 10 | 4 | 4 | 3 | 2 |
| **Ağırlıklı toplam / 5** | **100** | **4,60** | **4,10** | **3,55** | **3,05** |

### Puanlama sonucu

Yalnız “küçük dikey akışın derlenmesi/cihazda test edilmesi” satırı A-001 deney sonucuyla
değiştirilebilir. Flutter, Android ve iOS'ta VT-01–VT-03 için ayrı `All tests passed` kanıtını
aldığından bu satırın puanı 5'e, ağırlıklı toplamı 4,60'a yükseltilmiştir. React Native, KMP ve
ayrı native'in bu satırdaki puanları deney sonucu değil, karşılaştırma için resmi araç zinciri
kapasitesi tahminidir. Nihai seçim iki hedefteki PASS kapısına dayanır; toplam puan tek başına
bağlayıcı değildir.

Karşılaştırma, platformların pazarlama iddialarına değil bu projenin dört kritik akışına ve
işletim maliyetine göre yapılmıştır. React Native ve Kotlin Multiplatform üretimde kullanılabilir
alternatiflerdir; reddedilmeleri genel bir kalite hükmü değildir.

## Karar

Flutter, iOS ve Android mobil uygulamalarının framework'ü olarak seçilmiştir. Android ve iOS
hedeflerinde VT-01, VT-02 ve VT-03 için ayrı `All tests passed` kanıtı GitHub Actions koşusu
[#29358690335](https://github.com/MepCity/kurs-platform/actions/runs/29358690335) üzerinde
saklanmıştır. Tek platformdaki derleme veya kısmi test kabul için yeterli değildir; bu koşuda
iki platform kapısı da aynı commit üzerinde geçmiştir.

Flutter uygulaması, ana planın beş katmanını koruyacaktır. Presentation katmanı doğrudan HTTP,
veritabanı veya güvenli depolama çağırmaz. Eşitleme orkestrasyonu Application/Data sınırında
kalır; Domain iş kurallarını ve Data platform adaptörlerini doğrudan görmez. Yerel veritabanı,
kuyruk, güvenli depolama, gerçek zamanlı taşıma ve durum yönetimi için somut paket/sağlayıcı
seçimi bu ADR'nin kararı değildir; ilgili A-005 ve A-006 görevlerine bırakılmıştır.

## Gerekçe

- Tek kod tabanı, P-007'de tanımlı ortak ekranların ve P-010'da tanımlı kuyruk durumlarının iki
  platformda aynı uygulama akışıyla test edilmesini kolaylaştırır.
- Flutter'ın resmi araçları, uygulama içi senaryoları hedef cihazda çalışan entegrasyon testleri
  ile doğrulayabilir. Bu, girişten yoklama eşitlemesine kadar olan dikey denemenin hedefiyle
  doğrudan örtüşür.
- Resmi destek matrisi, V1'in iOS ve Android hedefini karşılar.
- Alternatiflerin ikisi de mümkün olsa da, bu aşamada Flutter'ın tek SDK/test modeli; React
  Native'in ek E2E aracı ve Kotlin Multiplatform'un iOS framework/Xcode bağlama yüzeyine göre
  daha az başlangıç entegrasyonu gerektirir.

## Dikey deneme kanıtı ve sınır

Bu kararın doğrulanabilir akışları ve PASS/FAIL ölçütleri
`ADR/ADR-001-dikey-deneme-protokolu.md` belgesindedir. İzole ve üretim dışı kaynaklar
`experiments/a001_flutter_spike` altındadır; A-001 bunları A-011'den bağımsız çalıştırır.
Android 16 / API 36 emülatörü ve iOS 18.5 iPhone 16 Pro Simulator üzerinde VT-01–VT-03
tamamlanmış, iki hedefte de nihai `All tests passed` sonucu alınmıştır. Yerel makinedeki önceki
eksik koşular PASS sayılmamış; kabul yalnız tekrarlanabilir CI kanıtına dayandırılmıştır.

Karar, teknik seçimi ve tekrar üretilebilir kabul protokolünü kayda alır. A-001'in Android ve
iOS kanıt kapısı tamamlanmıştır.

## Sonuçlar

### Olumlu

- A-009, kabul edilen bu karara dayanarak repo topolojisini belirleyebilir; Flutter mobil
  iskeletini bu topoloji içinde A-011 kurabilir.
- iOS ve Android için aynı ekran, kuyruk durumu ve erişim kontrolü senaryosu yazılabilir.
- Yerel/uzak veri ayrımı ile P-010 kuralları paket ayrıntısından bağımsız tutulur.

### Olumsuz ve önlemler

- Paket ekosistemi seçimi yanlış yapılırsa güvenlik veya kuyruk güvenilirliği riske girebilir.
  Önlem: A-005, kalıcı kuyruk ve yerel veritabanını ayrı ADR ile karara bağlar; güvenli
  depolama ve namespace izolasyonunu test eder.
- Platforma özgü izin, güvenli depolama veya bağlantı davranışları soyutlamanın arkasında
  farklılaşabilir. Önlem: adaptörler Data/Core sınırında tutulur ve protokol her iki hedefte
  çalıştırılır.
- Gerçek modül ve gerçek cihaz denemesi henüz yapılmamıştır. Önlem: A-011 sonrasında bu,
  üretim modülü için downstream mobil kalite kapısıdır; A-001'in izole spike önkoşulu değildir.

## Kapsam dışı

- Mobil repo/modül klasör iskeleti ve bağımlılıkların eklenmesi (A-011).
- Yerel veritabanı, kuyruk, güvenli depolama veya gerçek zamanlı kanal sağlayıcısı seçimi
  (A-005/A-006).
- Giriş veya yoklama API'sinin uygulanması.
- Web, veli/öğrenci uygulaması, bildirim ve diğer V1 sonrası yüzeyler.

## Kaynaklar

- [Flutter desteklenen dağıtım platformları](https://docs.flutter.dev/reference/supported-platforms) — iOS/Android destek matrisi.
- [Flutter entegrasyon testleri](https://docs.flutter.dev/testing/integration-tests) — `integration_test` ile hedef cihaz/emülatör testi.
- [Flutter test genel bakışı](https://docs.flutter.dev/testing/overview) — birim, widget ve entegrasyon testlerinin sınırları.
- [React Native test rehberi](https://reactnative.dev/docs/0.73/testing-overview) — cihaz üzeri E2E testinin rolü.
- [Kotlin Multiplatform yayınlama rehberi](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-apps.html) — iOS framework/Xcode entegrasyonu.
- [Kotlin Multiplatform genel bakışı](https://kotlinlang.org/multiplatform/) — paylaşımlı mantık/UI ve platform API'leri.

Bu dış kaynaklar 14 Temmuz 2026'da kontrol edilmiştir; proje davranışı için bağlayıcı kaynaklar
repo içindeki ürün ve sözleşme belgeleridir.
