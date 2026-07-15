# Gözlemlenebilirlik ve Hata İzleme Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | A-014 — Loglama ve hata izleme temelini kur |
| Durum | Sağlayıcı bağımsız başlangıç sözleşmesi |
| Bağımlılık | A-011 |
| Son güncelleme | 15 Temmuz 2026 |

## 1. Amaç ve karar

Backend ve mobil uygulama, bir log/crash sağlayıcısının SDK türlerini ürün koduna taşımayan
güvenli olay sınırı kullanır. Backend olayları SLF4J adaptörüne, mobil olayları `SafeEventLogger`
ve `ErrorReporter` portlarına verir. Yerel geliştirmede mobil yalnız tipli olay zarfını konsola
yazar; uzak hata sağlayıcısı yoktur.

PLAN-005'in A-010'dan daha yeni daraltması uyarınca bu görev provider hesabı, cloud secret,
deployment kaynağı veya ücretli servis oluşturmaz. Production alarm uygulaması ve nöbet/uyarı
yönlendirmesi OPS-003'e aittir. Buradaki eşikler OPS-003'ün doğrulayacağı başlangıç değerleridir.

## 2. Güvenli olay zarfı

Zorunlu olay alanları:

- sabit olay adı (`event`),
- UTC zaman (`occurredAt`),
- tipli önem seviyesi,
- yalnız olay factory'sinin ürettiği teknik bağlam.

Genel veya dışarıdan erişilebilir bir `Map` constructor yoktur. Desteklenen olaylar yalnız tipli
`httpRequestCompleted` ve `unexpectedApplicationError` factory'leriyle kurulur. Yeni olay türü,
yeni tipli factory ve negatif sızıntı testi olmadan eklenemez. İzin verilen teknik alanlar
`requestId`, sabit enum `operation`, doğrulanmış `method`/route kalıbı, `status`, `durationMs`,
tipli `environment` ve sınıftan türetilen `errorType` ile sınırlıdır. Backend HTTP kaydı sorgu
dizesi veya ham URL yerine yalnız eşleşen route kalıbını kaydeder. Mobil yakalanmamış hata kaydı
istisna mesajı ve stack trace yerine yalnız doğrulanmış hata tipini kaydeder.

Önem seviyeleri `INFO`, `WARNING`, `ERROR` ve `FATAL`dır. HTTP durumundan `INFO`/`WARNING`/`ERROR`
türetilir; beklenmeyen uygulama hatası `ERROR`, süreci sürdürmenin güvenli olmadığı hata `FATAL`
olur. SLF4J ayrı fatal metodu sunmadığı için backend `FATAL` olayını `atError()` üzerinden,
ayrıca `severity=FATAL` alanını koruyarak taşır. Adaptör seviyeyi olay adından tahmin etmez.

Şunlar hiçbir log, metric etiketi veya crash olayı alanına yazılmaz:

- parola, geçici parola, authorization başlığı, access/refresh/ID tokenı ve cookie,
- telefon, adres, doğum tarihi, profil fotoğrafı ve özgün dosya adı,
- öğretmen/kişi serbest notu, kuruma özel alan değeri ve istek/yanıt gövdesi,
- sorgu parametreleri, SQL ayrıntısı ve istisna mesajı,
- başka kurumun kimliğini açığa çıkaran bağlam.

Alan adını `password` yerine başka adla koymak tipli factory sınırını aşmaz. Operation ve
environment yalnız kapalı enum değerlerinden gelir; requestId, method, route kalıbı, errorType,
status ve duration alan bazında fail-closed doğrulanır. Kontrol karakteri, geçersiz aralık ve
serbest ürün metni reddedilir.

## 3. İstek korelasyonu

- İstemcinin geçerli `X-Request-Id` değeri backend yanıtına aynen taşınır.
- Başlık yoksa backend 1–128 karakter sınırına uyan UUID üretir.
- ASCII harf/rakam ile `.`, `_`, `-`, `:` dışındaki karakterler güvenli `400 INVALID_REQUEST`
  zarfıyla reddedilir; geçersiz ham değer loglanmaz veya yanıtta yankılanmaz.
- Kimlik yalnız korelasyon içindir; kimlik doğrulama, yetki veya idempotency sağlamaz.
- Her tamamlanan HTTP isteği yöntem, eşleşen route kalıbı (ham URL değil), durum ve süreyle tek
  tamamlanma olayı üretir. Eşleşmeyen yol `unmatched` olarak kaydedilir.
- Route kalıbı query (`?`), fragment (`#`), CR/LF veya başka kontrol karakteri içeremez.
  `TRACE` ve `CONNECT` güvenli HTTP method kataloğundadır; bilinmeyen veya geçersiz method
  telemetri üretimini best-effort bırakır ve ürün isteğinin sonucunu değiştirmez.

## 4. Ortam ve erişim ayrımı

Development, staging ve production log/crash akışları ayrı hedefler olarak ele alınır. Ortam adı
tam yazılır; `prod`/`test` gibi bağlama göre değişen kısaltmalar erişim kararı oluşturmaz.
Production telemetrisi yalnız en dar operasyon grubuna açıktır. Production verisi development
veya staging hata kaydına kopyalanmaz.

Log/crash saklama süresi bu görevde uydurulmaz. Hukukî saklama/imha değerlendirmesi ve seçilen
sağlayıcının bölge, erişim, silme, dışa aktarma ve maliyet özellikleri doğrulanmadan production
telemetrisi açılmaz.

## 5. Başlangıç sinyalleri ve eşikleri

Bu eşikler sentetik yükle kalibre edilir; düşük örnek sayısında yanlış alarm üretmemek için
asgari olay koşullarıyla uygulanır.

| Sinyal | Uyarı | Kritik | Uygulayan görev |
|---|---|---|---|
| Backend 5xx oranı | 10 dk içinde ≥50 istekte `%2` | 5 dk içinde ≥20 istekte `%5` | OPS-003 |
| Backend p95 süre | 10 dk boyunca `>500 ms` | 5 dk boyunca `>1 s` | OPS-003 |
| Yakalanmamış backend hatası | 15 dk içinde 1 olay | 5 dk içinde 5 olay | OPS-003 |
| Mobil fatal hata oranı | 30 dk içinde ≥20 oturumda `%1` | 15 dk içinde ≥20 oturumda `%3` | OPS-003 |
| Başarısız sync işlemi | 15 dk boyunca artış | Aynı kurum/sınıfta 5 dk sürekli artış | SYNC görevleri + OPS-003 |
| DB bağlantı/CPU/bellek kullanımı | 10 dk `%80` | 5 dk `%90` | OPS-003 |
| DB kullanılabilir disk | `%20` altı | `%10` altı | OPS-003 |
| Migration | uygulanamaz | tek başarısızlık | A-012/A-013 sonrası migration sahibi |
| Production günlük yedeği | son başarı `>24 saat` | son başarı `>26 saat` | OPS-001/OPS-003 |
| Aylık toplam maliyet | onaylı bütçenin `%80`i | `%100`ü | OPS-003 |

P95 hedefi ana plandaki normal API çağrılarının yaklaşık 500 ms hedefinden gelir. Trafik veya
pilot profili bu örnek alt sınırlarına ulaşmıyorsa oran alarmı yerine tekil yakalanmamış hata,
health ve kullanıcı bildirimi incelenir; “trafik az” hatayı başarıya dönüştürmez.

## 6. Hata davranışı

- Kullanıcıya istisna, SQL veya sağlayıcı ayrıntısı gösterilmez; güvenli hata kodu ve `requestId`
  döner.
- Log veya crash gönderiminin başarısız olması iş işlemini başarılı ya da başarısız yapmaz.
- Backend event factory veya logger `RuntimeException` üretirse olay best-effort bırakılır;
  başarılı/400/asıl exception akışı korunur. `Error` türleri genel olarak yutulmaz.
- Mobil logger ve reporter hataları birbirinden bağımsız yakalanır, yeni telemetri olayı üretmez
  ve asıl framework/platform/zone hatasını değiştirmez.
- Mobil Flutter handler daha önceki handler'ı çağırır; önceki handler yoksa
  `FlutterError.presentError` kullanır. Platform handler varsa dönüş değeri korunur, yoksa hata
  işlenmemiş (`false`) kalır. Zone hatası telemetri sonrasında üst zona aynen iletilir.
- Hata raporlama asla bekleyen mobil işlemi kuyruktan silmez ve sunucu onayı yerine geçmez.
- Yetkisiz, boş ve yükleniyor ekran davranışları ilgili ürün ekranı görevlerine aittir; A-014
  ürün ekranı eklemez.
- Denetim kaydı log değildir. Kritik iş değişikliği audit modülünde değiştirilemez kayıt üretir;
  tanılama logu bu zorunluluğun yerine geçmez.

## 7. Kabul kanıtı

- Backend geçerli/eksik/geçersiz `X-Request-Id` davranışı otomatik test edilir.
- Backend istek olayı sorgu, gövde, token ve telefon içermediğini negatif testle kanıtlar.
- Backend beklenmeyen hata olayı yalnız hata tipini taşır.
- Backend logger arızasında başarılı, 400 ve asıl uygulama exception akışları korunur; fatal JVM
  `Error` yutulmaz.
- Serbest constructor/alan bulunmadığı; operation enjeksiyonu, bilinmeyen alan, log forging,
  negatif süre, geçersiz status/requestId ve boş errorType negatif testlerle doğrulanır.
- Bütün severity değerlerinin açık SLF4J eşlemesi test edilir.
- Mobil framework/platform/zone hataları sağlayıcı bağımsız porta bağlanır.
- Mobil hata olayı istisna mesajı ve stack trace taşımadığını negatif testle kanıtlar.
- Mobil logger/reporter arızası ile önceki Flutter/platform handler ve üst zone davranışı test
  edilir.
- Backend build, Flutter format/analyze/test ve repo sınır kontrolleri geçer.

## 8. Kapsam dışı

- Sağlayıcı hesabı, SDK'sı, DSN/secret, production dashboard ve uyarı kanalı,
- production alarm kurulumu ve nöbet süreci (OPS-003),
- iş modüllerine özel metric/trace enstrümantasyonu,
- audit şeması ve ortak API hata eşleme katmanının tamamı,
- analitik, kullanıcı davranışı takibi ve ürün ekranları.
