# ADR-006 — Gerçek zamanlı kanal ve değişiklik yayılımı

| Alan | Değer |
|---|---|
| Durum | Kabul edildi |
| Tarih | 14 Temmuz 2026 |
| Görev | A-006 — Gerçek zamanlı kanal ADR'si ve iki istemcili yoklama olayı deneyi |
| Bağımlılık | `SENKRONIZASYON_VE_CAKISMA.md` (P-010) |
| İlgili kararlar | ADR-002 Java/Spring MVC, ADR-003 Supabase PostgreSQL |

## 1. Karar özeti

Mobil istemcilere sınıf kapsamlı küçük değişiklik olaylarını taşımak için **kimlik doğrulamalı
Server-Sent Events (SSE)** önerilir. İstemci yazmaları mevcut sürümlenmiş REST API'den yapılır;
SSE yalnız sunucudan istemciye hızlandırıcı bildirim kanalıdır.

`sync_changes` kalıcı değişiklik akışı ve doğruluk kaynağı olmaya devam eder. PostgreSQL
`LISTEN/NOTIFY`, backend instance'larını yeni değişiklik için uyandıran, veri taşımayan ve
kaçırılabilir bir sinyaldir. Her instance bildirim sonrası `sync_changes` tablosunu kendi yerel
abonelerinin yetkili kapsamları için okur; bildirim kaybı yapılandırılabilir periyodik taramayla
toparlanır. Redis, ayrı mesaj broker'ı veya Supabase Realtime ilk sürüm bağımlılığı yapılmaz.

Bu karar Supabase PostgreSQL kullanımını genişletip mobil istemciye doğrudan veritabanı,
Supabase Data API veya servis anahtarı erişimi vermez. Bütün abonelik ve kanonik okuma/yazma
yetkileri Spring backend tarafından doğrulanır.

## 2. Bağlam ve karar sürücüleri

P-010 gerçek zamanlı olayı teslim garantisi olmayan bir hızlandırıcı olarak tanımlar. Aynı
belge; sabit `eventId`, monoton `changeSequence`, kurum ve gerektiğinde sınıf kapsamı, küçük
olay gövdesi, yinelenen/sırasız olay toleransı, opak `syncToken` ve gerektiğinde tam snapshot
ile kanonik uzlaşmayı zorunlu kılar. Kanal seçimi bu doğruluk modelini değiştiremez.

Kararı belirleyen ihtiyaçlar şunlardır:

1. Bir sınıfta birden fazla hoca birkaç saniye içinde değişiklik sinyali almalıdır.
2. Kurum ve sınıf izolasyonu bağlantı kurulurken ve bağlantı yaşarken backend'de korunmalıdır.
3. Kopma, olay kaybı, yinelenme veya sıra bozulması sessiz veri kaybına yol açmamalıdır.
4. ADR-002'nin bloklayıcı Spring MVC ve tek deploy edilen modüler monolit kararıyla uyumlu
   olmalıdır; sırf gerçek zaman için WebFlux veya mikroservis zorunluluğu doğurmamalıdır.
5. İlk sürümde çift yönlü sohbet, presence veya istemciden soket üzerinden komut gerekmemektedir.
6. Çok instance çalışmada süreç içi abonelik listesi doğruluk veya teslim kaynağı olmamalıdır.
7. Yeni operasyonel servis ancak ölçülmüş ihtiyaç varsa eklenmelidir.

## 3. Değerlendirilen seçenekler

Puanlar `1` (zayıf) ile `5` (çok güçlü) arasındadır. Ağırlıklı toplamın üst sınırı 500'dür.

| Ölçüt | Ağırlık | SSE + `sync_changes` | WebSocket/STOMP | Supabase Realtime Broadcast | Kısa aralıklı polling |
|---|---:|---:|---:|---:|---:|
| P-010 kopma/kanonik uzlaşma uyumu | 30 | 5 | 4 | 4 | 5 |
| Kurum/sınıf yetkisini backend'de tutma | 25 | 5 | 4 | 3 | 5 |
| Spring MVC ve mevcut REST modeliyle uyum | 20 | 5 | 3 | 2 | 5 |
| Operasyonel sadelik ve sağlayıcı bağımsızlığı | 15 | 4 | 3 | 3 | 5 |
| Birkaç saniyelik yayılım ve mobil maliyet | 10 | 4 | 4 | 5 | 2 |
| **Ağırlıklı toplam** | **100** | **475** | **365** | **330** | **470** |

### 3.1. SSE

Spring MVC `SseEmitter` ile standart `text/event-stream` yanıtı üretir. Kanal tek yönlü olduğu
için yoklama yazmalarını sokete taşıyan ikinci bir komut protokolü gerekmez. HTTP kimlik ve
izleme altyapısı yeniden kullanılabilir. Uzun bağlantı, heartbeat, proxy buffering/timeout ve
instance başına bağlantı kapasitesi ayrıca işletilmelidir.

### 3.2. WebSocket/STOMP

Çift yönlü komut, presence veya yoğun etkileşim gerektiğinde güçlüdür. Spring'in basit STOMP
broker'ı süreç içidir; çok instance için harici broker relay veya ayrıca dağıtık yayın katmanı
gerekir. V1 ihtiyacı tek yönlü küçük bildirim olduğundan protokol, güvenlik ve operasyon
maliyeti karşılığında gerekli bir ürün davranışı sağlamaz.

### 3.3. Supabase Realtime Broadcast

Yönetilen WebSocket ve private channel sunar. Ancak Realtime Authorization, Supabase JWT/RLS
ve istemci kütüphanesiyle ikinci bir yetki yüzeyi oluşturur. ADR-003 mobilin veritabanına
doğrudan açılmamasını ve Data API'nin kapalı kalmasını zorunlu tutar; A-004 kimlik kararıyla
ek token köprüsü ve sağlayıcı bağımlılığı oluşur. Bu nedenle PostgreSQL sağlayıcısının seçilmiş
olması Realtime ürününü otomatik olarak seçmek için yeterli değildir.

### 3.4. Kısa aralıklı polling

Kanonik HTTP okumalarıyla güvenlidir ve geri dönüş mekanizması olarak korunur. Sürekli birkaç
saniyelik polling, değişiklik olmayan zamanda da mobil radyo, istek ve veritabanı yükü üretir.
Ana hızlandırıcı kanal yerine yalnız SSE kullanılamadığında kontrollü fallback'tir.

## 4. Taşıma sözleşmesi

Kesin endpoint ve alan adları `ATT-003` olay sözleşmesinde son hâlini alacaktır. A-006'nın
bağlayıcı taşıma yüzeyi şöyledir:

- İstemci `GET /api/v1/realtime/classes/{classId}/events` isteğini `Authorization: Bearer ...`
  ve `Accept: text/event-stream` ile açar.
- Sunucu token/oturumu, etkin kurum üyeliğini, sınıf atamasını ve sınıf görüntüleme iznini
  doğrulamadan `200` akış başlatmaz. İstemciden kurum kimliği kabul edilmez; kurum doğrulanmış
  bağlamdan türetilir.
- Yetkisiz ya da kapsam dışı hedef P-009 güvenli hata modeliyle `401`, `403` veya varlık
  ifşasını engellemek gereken yerde `404` döner. Başka kurum/sınıfa ait olay kimliği veya
  sıra bilgisi hata gövdesine konmaz.
- Akış açıldığında `stream.ready` kontrol olayı, yalnız doğrulanmış `organizationId + classId`
  kapsamındaki `sync_changes` satırlarının en büyük `changeSequence` değerini yüksek su işareti
  olarak verir; global sayaç doğrudan kullanılamaz. Başka kurum veya sınıfta araya giren
  değişiklik bu değeri ilerletemez. Değer tek başına sync token değildir ve istemci tarafından
  başka kapsamda kullanılamaz.
- Değişiklik bildirimi `entity.changed` türündedir ve P-010'un en az `eventId`,
  `changeSequence`, `organizationId`, sınıf kapsamındaysa `classId`, `entityType`, `entityId`,
  `changeType`, `rowVersion` ve `occurredAt` alanlarını taşır.
- Olay; yoklama durumu, öğretmen notu, telefon, adres, token veya gereksiz kişisel veri taşımaz.
  İstemci kanonik kaydı normal yetkili API veya değişiklik akışı üzerinden alır.
- SSE `id` alanı sabit `eventId` değeridir. Yalnız aynı `eventId` tekrar teslimi ayıklanır.
  Farklı `eventId` taşıyan bir olayın `changeSequence` değeri son kabul edilen sıradan küçük/eşit
  veya beklenen sıradan büyükse olay sessizce atılmaz; istemci güvenli sync/kanonik uzlaşma
  başlatır. Daha küçük `changeSequence` veya `rowVersion` kanonik veriyi geriye götüremez.
- Sunucu, proxy ve istemci bağlantıyı canlı tutmak ve kopmayı fark etmek için başlangıçta 15
  saniyelik yorum satırı heartbeat hedefi kullanır. Değer A-010 ortam ölçümüyle ayarlanabilir.
- Akış süresi access token son kullanma zamanını aşamaz. İstemci token yeniledikten sonra yeni
  HTTP bağlantısı kurar; token URL sorgusuna veya loglanabilir kanal adına yazılmaz.

## 5. Yetki iptali ve kurum izolasyonu

Abonelik bir kez izin verilince süresiz yetkili sayılmaz:

- IAM oturum iptali, kurum üyeliği değişimi ve sınıf atama/izin değişimi aynı kalıcı değişiklik
  akışında güvenli bir kapsam-geçersizleştirme sinyali üretir. Her backend instance ilgili yerel
  akışları kapatır.
- Her olay gönderiminde aboneliğin kurum/sınıf kapsamı tekrar eşleştirilir. Yetki önbelleği
  kullanılırsa iptal transaction'ıyla geçersizleştirilmesi zorunludur; yalnız bağlantı anındaki
  karara güvenilemez. Canlı oturumu veya sınıf ataması artık geçerli olmayan aboneye olay
  yazılmaz ve SSE akışı kapatılır.
- Heartbeat sırasında oturum ve sınıf yetkisinin yeniden doğrulanması, kaçırılmış iç
  geçersizleştirme sinyaline karşı ikinci savunmadır. Doğrulama başarısızsa akış kapatılır ve
  istemci kanonik snapshot/purge sürecine geçer.
- `LISTEN/NOTIFY` yükünde kurum, sınıf, kullanıcı veya varlık kimliği taşınmaz; yalnız yeni
  değişiklik bulunduğunu belirten genel uyanma/sıra sinyali bulunur. Yetkili kapsam sorgusu
  backend instance'ında yapılır.

Bu kurallar `REALTIME-001`, IAM/CLS entegrasyonu ve kurum izolasyonu testleri uygulanmadan
yalnız ADR ile tamamlanmış sayılmaz.

## 6. Kopma ve kanonik uzlaşma algoritması

Gerçek zamanlı kanal geçmişi güvenilir biçimde teslim etmek zorunda değildir. Yeniden bağlanan
istemci şu sırayı izler:

1. Yerel bekleyen yazma kuyruğunu korur; SSE kopması yazmayı başarılı veya başarısız yapmaz.
2. Yetkili SSE akışını açar ve `stream.ready.headSequence` değerini alır. Bu sırada gelen canlı
   olayları geçici tamponda tutar.
3. Son opak `syncToken` ile P-010 değişiklik akışını tüketir. Token geçersiz/süresi dolmuşsa
   yüksek su işaretli tam snapshot uygular.
4. `stream.ready` anındaki yüksek su işaretine kadar uzlaşınca, daha büyük sıralı tamponlanmış
   olayları `eventId`/`changeSequence` ile ayıklayarak işler.
5. Olayın işaret ettiği kaydı normal okuma yetkisiyle yeniden çeker. Kanonik `rowVersion`,
   yerel görünümden eski olamaz.
6. Uzlaşma başarısızsa ekranda güncel olduğu iddia edilmez; kontrollü polling/snapshot denenir
   ve eşitleme sorunu görünür kalır.

Bu sıra, değişiklik akışı ile SSE aboneliği arasındaki yarış penceresini kapatır. SSE'nin
`Last-Event-ID` mekanizması bağlantı tanılamasında kullanılabilir; P-010 opak `syncToken`ının
ve yüksek su işaretli snapshot'ın yerine geçmez.

## 7. Sunucu uygulama ve çok instance topolojisi

İlk uygulama ADR-002'deki Spring MVC içinde `SseEmitter` ile yapılır. Ayrı gerçek zaman
mikroservisi kurulmaz.

1. İş değişikliği, audit, idempotency terminal sonucu ve `sync_changes` aynı PostgreSQL
   transaction'ında yazılır.
2. Commit sonrasında PostgreSQL genel uyanma bildirimi üretir. Bildirim üretilemez veya
   dinleyici koparsa kalıcı `sync_changes` satırı kaybolmaz.
3. Her backend instance dedicated PostgreSQL dinleyici bağlantısıyla uyanır ve son işlediği
   sıra sonrasını sorgular. Ayrıca başlangıçta 1 saniyelik yapılandırılabilir güvenlik taraması
   kullanılır; gerçek yük ölçümüyle geri çekilme ayarlanır. Instance toparlama sorgusu
   `delivery_status` değerine güvenmez; başka yayıncının `PUBLISHED` yaptığı kalıcı satırı da
   kendi yerel bağlantıları için okuyabilir.
4. Instance yalnız kendi belleğindeki, hâlen yetkili SSE bağlantılarına küçük olay yollar.
   Süreç belleği teslim kaydı veya kanonik veri değildir.
5. Yayın/sse yazma hatası `sync_changes` satırını silmez. İstemci yeniden bağlanıp sync ile
   toparlar.

Üretim reverse proxy'sinde response buffering kapatılır, idle timeout heartbeat'ten büyük
tutulur ve `Cache-Control: no-cache, no-transform` kullanılır. Spring async executor ve bağlantı
üst sınırları A-011/A-010 sonrasında ölçülür. Ölçüm kanıtı olmadan WebFlux, Redis veya broker
eklenmez; QA-003 çok cihaz testinde kapasite yetersizse bu ADR gözden geçirilir.

## 8. Gözlemlenebilirlik ve hata davranışı

Kişisel veri veya token loglamadan en az şu ölçümler tutulur:

- aktif SSE bağlantısı ve kurum/sınıf bilgisi içermeyen toplam bağlantı sayısı;
- bağlantı açma reddi (`401/403/404`) sayıları;
- olay commit zamanı ile istemciye yazma zamanı arasındaki dağıtım gecikmesi;
- instance değişiklik akışı watermark gecikmesi;
- heartbeat/yazma hatası, yeniden bağlantı ve sync fallback sayıları;
- yetki iptali nedeniyle kapatılan bağlantı sayısı.

SSE yayın hatası iş yazmasını geri almaz ve kullanıcıya yazma başarısı olarak da sunulmaz.
Yazmanın kesin sonucu REST/idempotency sözleşmesinden; ekranın güncelliği sync/kanonik okumadan
gelir.

## 9. Çalıştırılabilir deney ve ölçüm kaydı

Deney `experiments/a006_realtime_sse` altında, Node.js standart kütüphanesiyle ve üçüncü taraf
bağımlılık olmadan tutulur. Bellek içi değişiklik dizisi yalnız `sync_changes` davranışını
temsil eder; üretim kodu değildir.

14 Temmuz 2026 tarihinde Node.js `v25.2.1` ile:

```text
npm test
6 test, 6 passed, 0 failed

npm run experiment
ilk olay → istemci A: 4,89 ms
ilk olay → istemci B: 4,96 ms
yeniden bağlanma + değişiklik akışı + kanonik okuma: 1,82 ms
toparlanan sıra: [2]
kanonik sonuç: ABSENT, rowVersion=2
başka kurumun hedef sınıf aboneliği: HTTP 403
aynı kurumda yetkisiz sınıf aboneliği: HTTP 403
başka kullanıcının opak sync tokenını kullanma: HTTP 409
kimliksiz akış açma: HTTP 401
```

Ölçümler tek makinede süreç içidir; üretim gecikme veya kapasite hedefi değildir. Deney şu
kabul davranışlarını kanıtlar:

- iki bağımsız istemci aynı sınıf değişikliğini aynı `eventId` ve sıra ile alır;
- istemci B kopukken oluşan ikinci olayı canlı kanaldan alamaz;
- yeniden bağlantıda yüksek su işaretini görür, eksik sıra 2'yi değişiklik akışından alır ve
  kanonik `ABSENT`/`rowVersion=2` durumuna döner;
- farklı kurum kimliği `class-a` akışına abone olamaz;
- aynı kurumda yalnız `class-b` yetkisi olan kimlik `class-a` akışına abone olamaz;
- kullanıcı B'ye bağlı opak sync token kullanıcı A tarafından kullanıldığında reddedilir;
- kimlik doğrulaması olmayan akış isteği reddedilir.

Regresyon testleri ayrıca şunları doğrular:

- başka kurum/sınıf değişikliği scoped `stream.ready.headSequence` değerini ilerletmez ve boş
  scoped uzlaşmayı kilitlemez;
- bağlantı sonrasında sınıf ataması veya oturumu iptal edilen abone sonraki olayı almaz ve
  akışı kapanır;
- iki öğrenci olayı ters sırada geldiğinde sıra boşluğu ve sıra dışı teslim sync/kanonik
  uzlaşmayı tetikler; aynı `eventId` tekrarı ayıklanır ve iki kanonik kayıt da korunur;
- `waitFor` timeout'u bekleyici kaydını listeden çıkarır;
- bütün deney yüzeyleri `/api/v1` kökünü kullanır, eski `/v1` yolu sunulmaz.

## 10. Sonuçlar ve sınırlar

### Olumlu sonuçlar

- Spring MVC ve REST güvenlik bağlamı korunur; ikinci yazma protokolü oluşmaz.
- Doğruluk SSE veya süreç belleğine bağlanmaz; P-010 sync/snapshot modeli aynen kalır.
- Supabase Realtime, Redis veya harici broker maliyeti ilk sürüme eklenmez.
- Standart HTTP akışı, mobil istemcinin bearer header ve bağlantı yaşam döngüsünü kontrol
  etmesine izin verir.

### Maliyet ve riskler

- Uzun HTTP bağlantıları proxy, executor, dosya tanıtıcısı ve mobil yaşam döngüsü ayarı ister.
- PostgreSQL genel uyanma sinyali ve periyodik tarama, yüksek olay hacminde verimsizleşebilir.
  `QA-003` ve `QA-007` ölçümleri broker ihtiyacını yeniden değerlendirme kapısıdır.
- SSE çift yönlü değildir. Gelecekte presence, sohbet veya çok yüksek frekanslı istemci mesajı
  gerekirse WebSocket/STOMP ayrı ADR ile değerlendirilebilir.
- Native mobil HTTP stream davranışı iOS/Android gerçek cihazda `REALTIME-001` ve QA görevlerinde
  doğrulanmalıdır; bu Node deneyi mobil radyo/uyku davranışını kanıtlamaz.

## 11. Kapsam dışı ve sonraki görevler

- `ATT-003` kesin yoklama olay adını, aggregate eşlemesini ve alan şemasını tanımlar.
- `ATT-012` transaction sonrası sınıf olay üretimini uygular.
- `REALTIME-001` mobil bağlantı, token yenileme, heartbeat ve yaşam döngüsünü uygular.
- `REALTIME-002` gelen olay sonrası güvenli kanonik yenilemeyi uygular.
- `A-010` ortam/proxy timeout ve bağlantı ayarlarını; `A-011` üretim klasör iskeletini belirler.
- PostgreSQL migration, Spring uygulama kodu, gerçek auth/izin entegrasyonu, Redis/broker,
  production yük testi ve mobil gerçek cihaz testi A-006 kapsamında değildir.

## 12. Kaynaklar ve uyum

- Ana ürün planı: `URUN_VE_UYGULAMA_PLANI.md` §12, §16, §18 ve §21.
- Sync sözleşmesi: `SENKRONIZASYON_VE_CAKISMA.md` §7 ve §9.
- [Spring MVC asenkron istek ve `SseEmitter` belgeleri](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
  — MVC'de SSE üretimi, kopmayı algılamak için heartbeat ve async executor gereksinimi.
- [WHATWG Server-Sent Events standardı](https://html.spec.whatwg.org/multipage/server-sent-events.html)
  — `text/event-stream`, yeniden bağlantı, `id`, `retry` ve `Last-Event-ID` davranışı.
- [Spring STOMP/WebSocket genel bakışı](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/overview.html)
  — süreç içi basit broker ve harici broker relay seçenekleri.
- [Supabase Realtime Broadcast belgeleri](https://supabase.com/docs/guides/realtime/broadcast)
  — private channel yetkilendirmesi, WebSocket yayını ve veritabanı broadcast davranışı.

Bu ADR, P-010'un kurum/sınıf izolasyonu, küçük olay, teslim garantisi olmaması, sabit olay
kimliği, monoton değişiklik akışı, opak sync token ve kanonik snapshot kurallarını değiştirmez.
