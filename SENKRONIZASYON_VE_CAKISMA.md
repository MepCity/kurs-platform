# Senkronizasyon ve Çakışma Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | P-010 — Senkronizasyon ve çakışma sözleşmesini yaz |
| Belge sürümü | 1.0 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlılıklar | `VERI_MODELI.md` (P-008), `API_GENEL_KURALLARI.md` (P-009) |
| Kapsam | Mobil kalıcı yazma kuyruğu, idempotent yeniden deneme, çakışma, gerçek zamanlı güncelleme ve yeniden eşitleme |
| Kapsam dışı | Taşıma sağlayıcısı, yerel veritabanı seçimi, migration/kod ve geri alma komutları |
| Son güncelleme | 14 Temmuz 2026 |

## 1. Amaç ve bağlayıcılık

Bu belge, bağlantının kesilmesi veya iki hocanın aynı kayıtla çalışması hâlinde verinin sessizce kaybolmamasını sağlayan V1 sözleşmesidir. Mobil istemci ile sunucu, burada tanımlanan kuyruk, idempotency, sürüm, olay ve yeniden eşitleme kurallarına birlikte uyar.

Bu sözleşme yalnızca API genel kurallarını tamamlar. Kimlik doğrulama, kurum/sınıf yetkisi ve güvenli hata zarfı `API_GENEL_KURALLARI.md` tarafından tanımlanır; bu kontroller çevrimdışı olma veya yeniden deneme nedeniyle atlanamaz. Geri alınabilir kayıtlar ve ters komutlar `DENETIM_VE_GERI_ALMA_ILKELERI.md` tarafından tanımlanır.

## 2. Değişmez kurallar

- Sunucu kesin başarı cevabı vermeden bir yerel yazma `SUCCEEDED` sayılmaz ve kalıcı kuyruktan kaldırılmaz.
- Her mobil yazma denemesi bir `clientMutationId` üretir ve bunu `Idempotency-Key` başlığında taşır. Aynı mantıksal işlemin bütün yeniden denemeleri aynı anahtarı kullanır; yeni işlem yeni anahtar kullanır.
- `X-Request-Id` tek ağ isteğini izler; `clientMutationId` yerine geçmez. Bir yeniden denemede yeni `X-Request-Id` üretilebilir.
- Sunucu; token, kurum bağlamı, rol, sınıf kapsamı, iş kuralı ve hedef kaydın sürümünü her denemede yeniden doğrular. İstemcinin önceden aldığı yetki veya yerel verisi yetki kanıtı değildir.
- Sunucu, eşdeğer bir idempotent işlemi en fazla bir kez uygular; buna bağlı denetim kaydı ve
  mantıksal değişiklik olayı da en fazla bir kez üretilir. Olay taşıması yinelenebilir.
- Başka kurum, sınıf veya kullanıcıya ait bir işlem sonucu, idempotency kaydı, olay veya hata ayrıntısı çağırana gösterilemez.
- İstemci cihazındaki yerel görünüm iyimser olarak güncellenebilir; ancak kullanıcıya görünür biçimde `BEKLIYOR`, `EŞITLENIYOR`, `KAYDEDILDI` veya `İŞLEM_GEREKIYOR` durumu taşır. Yerel görünüm sunucu gerçeğinin yerine geçmez.

## 3. Yazma işlemi kimliği ve sunucu idempotency kaydı

### 3.1. İstek eşdeğerliği

İdempotency kapsamı ya `ORGANIZATION + organizationId + actorUserId + clientMutationId` ya da
`GLOBAL + actorUserId + clientMutationId` birleşimidir. `ORGANIZATION`, tüm kurum kapsamlı
yazmalarda kullanılır ve mevcut davranışı korur. `GLOBAL` yalnızca platform yöneticisinin,
henüz `organizationId` oluşmadan başlayan kurum oluşturma gibi açık global işlemlerinde
kullanılabilir; kurum yöneticisi veya hoca bu kapsamı seçemez. Platform yöneticisinin hedef
kurumdaki destek erişimi yine `ORGANIZATION` kapsamındadır. Bu ayrım ve NULL-güvenli
tekillik, `VERI_MODELI.md` §14'te tanımlıdır.

Sunucu ilk kabulde aşağıdaki değişmez istek özetini saklar: HTTP yöntem/yol, işlem türü, hedef kimlik (varsa), kanonikleştirilmiş gövde özeti ve gerekiyorsa `If-Match-Row-Version`. Aynı anahtarın bunlardan herhangi biri farklı olan kullanımı `409 IDEMPOTENCY_KEY_REUSED` döndürür ve hiçbir iş yan etkisi oluşturmaz. Özet ham sır, token veya gereksiz kişisel veri içermeyecek biçimde hesaplanır.

`VERI_MODELI.md` §14; `request_fingerprint`, terminal HTTP/hata alanları, güvenli sonuç
gövdesi veya referansı, lease alanları ve iki aşamalı saklama ömrüyle eşdeğerlik denetimini ve
sonucu yeniden oynatmayı kalıcılaştırır. `result_expires_at` geçince sonuç gövdesi/referansı
minimizasyon için temizlenebilir; `key_retention_expires_at` ise anahtar + kapsam +
`request_fingerprint` + terminal durum tombstone'unu saklar ve desteklenen azami
çevrimdışı/kuyruk ömründen **uzun** olmalıdır.

Sunucu, aynı anahtarın yeni işlem gibi uygulanmayacağını garanti ettiği sürece tombstone'u
fiziksel olarak silmez. Tombstone süresi bittikten sonra bu garanti sona erer: istemci bu kadar
eski anahtarı yeniden göndermemelidir; sunucu artık bilmediği anahtarı kesin biçimde reddettiğini
iddia etmez. İstemci önce kanonik kaynak/değişiklik akışıyla uzlaştırır ve yeni mantıksal işlem
için yeni anahtar üretir. Bu görev migration yazmaz.

### 3.2. Sunucu durum makinesi

```text
İlk eşdeğer istek
  → PENDING (lease sahibi)
  → COMPLETED  (iş değişikliği + audit + terminal sonuç + mantıksal olay aynı transaction'da)
  └→ FAILED    (iş yan etkisi yok; yalnız kesin terminal 4xx sonucu kalıcı)

Eşdeğer tekrar
  PENDING   → 202 Accepted; işlem durumu sorgulanabilir
  COMPLETED → ilk başarılı HTTP sonucu/nesnesi yeniden döner
  FAILED    → ilk terminal hata sonucu yeniden döner
```

- `PENDING`, iş değişikliğinin tamamlandığı anlamına gelmez. Her `PENDING` satırın yalnızca
  bir `lease_owner`ı, artan bir `lease_generation` fencing sayacı ve sonlu bir
  `lease_expires_at` değeri olur. Lease geçerliyse eşdeğer tekrar ikinci yürütme başlatmaz;
  `202 Accepted` ve yalnızca çağıranın okuyabildiği takip durumu döner.
- Lease süresi geçerse sunucu, iş değişikliği/audit/terminal sonuç bulunmadığını transaction
  içinde doğruladıktan sonra satırı atomik biçimde yeni sahip ve daha büyük
  `lease_generation` ile yeniden sahiplenebilir. Terminal geçiş, iş değişikliğiyle aynı
  transaction'da güncel sahip + fencing sayacını koşul olarak doğrular; eski sahip lease'i
  kaybettikten sonra `COMPLETED` yazamaz. Çöken sahip nedeniyle `PENDING` sonsuza dek
  sahipsiz kalamaz; kurtarma işi süre geçmiş kayıtları aynı kuralla yeniden sahiplenir veya
  güvenli terminal `FAILED` sonucuna taşır.
- `COMPLETED`, iş değişikliği, zorunlu denetim kaydı, idempotency terminal sonucu ve sabit
  `eventId`li mantıksal değişiklik olayının tek transaction sınırında başarılı olduğunu ifade
  eder. İlk çağrının cevabı cihaza ulaşmasa bile aynı anahtar `COMPLETED` sonucunu döndürür.
- Mantıksal olay bir kez oluşturulur; taşıma katmanı aynı `eventId`yi birden çok kez
  yayımlayabilir. İstemci `eventId` ile yinelenen teslimleri ayıklar.
- `FAILED`, yalnız kesin ve güvenli terminal sonuç olan doğrulama (`400`/`422`), yetki veya
  bulunamama (`403`/`404`), iş durumu/sürüm/idempotency çakışması (`409`) nedeniyle iş
  yan etkisi oluşturmamış sonucu ifade eder. İstemci işlemi değiştirmeden tekrar deneyemez;
  değiştirirse yeni bir `clientMutationId` üretir.
- Ağ kesintisi, süre aşımı, `429`, tüm `5xx` ve sunucunun işlemin sonucunu kesin
  bildiremediği durumlar terminal değildir: idempotency kaydı `PENDING` kalır veya güvenli
  lease kurtarma yoluna girer; kuyruk aynı anahtarla yeniden dener. Terminal `5xx`
  desteklenmez. `PENDING` kaydı yarım iş yan etkisi taşıyamaz; başarılı yan etkiler ancak
  aynı transaction'daki `COMPLETED` ile birlikte kalıcılaşır.

## 4. Mobil kalıcı kuyruk

### 4.1. Kuyruk girdisi ve bağımlılık

Her kuyruk girdisi en az şunları kalıcı olarak taşır: yerel işlem kimliği, `clientMutationId`,
kapsam türü, kurum bağlamı (varsa), işlemi başlatan kullanıcı, HTTP yöntem/yol, istek gövdesi,
beklenen `rowVersion` (varsa), oluşturulma zamanı, durum, son deneme bilgisi, son güvenli
hata ve bağımlı olduğu yerel işlem kimlikleri. Gizli erişim belirteci kuyruk girdisine yazılmaz.
Yerel kuyruk ve önbellek kullanıcı + kurum bağlamına göre fiziksel/mantıksal olarak ayrılır.
Başka kullanıcı giriş yaptığında önceki kullanıcının bekleyen işlemleri gösterilmez, onun adına
gönderilmez ve yeni kullanıcının önbelleğine karışmaz.

Bir çevrimdışı oluşturmanın ürettiği istemci UUID'sine bağımlı yazma, oluşturma onaylanmadan gönderilmez. Aynı hedef kayda yönelik yazmalar oluşturuldukları sırada gönderilir; istemci bu sırayı değiştirerek sonraki değişikliği eski sunucu sürümü üzerinde çalıştıramaz. Bağımsız kayıtların paralel gönderimi mümkündür, fakat aynı kullanıcı/kurum anahtar alanındaki idempotency kurallarını değiştirmez.

### 4.2. İstemci kuyruk durumları

| Durum | Anlamı | İstemci davranışı |
|---|---|---|
| `PENDING` | Yerelde kalıcı, gönderilmeyi bekliyor. | Uygun bağlantı ve geçerli oturumda gönderilir. |
| `SYNCING` | Bir ağ denemesi sürüyor veya sunucu `202` ile beklemede bildirdi. | Uygulama kapanınca güvenli biçimde `PENDING` olarak geri alınır; anahtar korunur. |
| `RETRY_WAIT` | Geçici hata sonrası yeniden deneme zamanı bekleniyor. | Üstel bekleme ve varsa `Retry-After` sonrasında aynı anahtarla dener. |
| `SUCCEEDED` | Sunucu kesin başarılı sonucu doğrulandı. | Yerel görünüm kanonik sunucu sonucu ile güncellenir; kayıt, güvenli yerel geçmiş politikasına göre temizlenebilir. |
| `NEEDS_ATTENTION` | Kullanıcı değerlendirmesi veya yeni işlem gerekiyor. | Otomatik yeniden gönderilmez; hata ve güvenli çözüm seçeneği gösterilir. |
| `BLOCKED` | Bağımlı işlem, geçersiz/iptal edilmiş oturum veya yeniden kimlik doğrulama ihtiyacı nedeniyle şu an gönderilemez. | Oturum yenilenir ya da yeniden giriş yapılır; kayıt sessizce silinmez. Yeniden girişten sonra eski yazma otomatik gönderilmez: kaynak ve sürüm yeniden doğrulanır, kullanıcı yeni bir karar verir. |

`SUCCEEDED` dışındaki hiçbir durum kalıcı kuyruktan otomatik silinmez. Kullanıcının bir işlemi vazgeçmesi yalnızca henüz sunucuya başarıyla uygulanmamış yerel girdiyi iptal eder; sunucuya ulaşmış veya belirsiz sonuçlu işlem için önce sonucu aynı anahtarla sorgulamak zorundadır.

### 4.3. Sonuç sınıflandırması

| Sunucu/ağ sonucu | Kuyruk sonucu | Sonraki hareket |
|---|---|---|
| `2xx` kesin başarı | `SUCCEEDED` | Kanonik kaynak/sonuç yerel depoya yazılır. |
| `202` veya bağlantı sonucu belirsiz | `SYNCING` / `RETRY_WAIT` | Aynı anahtarla durum sorgulanır ya da yeniden denenir. |
| `401`, `SESSION_REVOKED` | `BLOCKED` | Geçersiz/iptal edilmiş oturum veya yeniden kimlik doğrulama ihtiyacı vardır; oturum yenilenir, yenilenemezse giriş istenir. İşlem silinmez. Yeniden giriş tek başına eski yazmayı göndermez. |
| `403`, `404` | `NEEDS_ATTENTION` | Sunucu sınıf ataması/yetki kaybını veya kapsam dışı hedefi kesin olarak doğrulamıştır ve terminal `FAILED` sonucu saklar; hedef varlığı ifşa etmeyen hata gösterilir. Otomatik tekrar yoktur. |
| `409 VERSION_CONFLICT`, `STATE_CONFLICT`, `IDEMPOTENCY_KEY_REUSED` | `NEEDS_ATTENTION` | Sunucu terminal `FAILED` sonucunu saklar; otomatik tekrar yoktur. |
| `400`, `422` | `NEEDS_ATTENTION` | Sunucu terminal `FAILED` sonucunu saklar; düzeltilmiş yeni işlem oluşturulur. |
| `429`, `5xx`, ağ hatası | `RETRY_WAIT` | İdempotency kaydı terminal `FAILED` olmaz; aynı anahtar ve güvenli geri çekilme ile yeniden dene. |

## 5. Çevrimdışı oluşturma ve sunucu doğrulaması

V1'de istemci UUID'si yalnızca öncelikli çevrimdışı yazma alanları olan yoklama oturumu, yoklama kaydı ve ilerleme kaydının **oluşturma** işlemlerinde kabul edilebilir. İlgili ATT ve PROGRESS kaynak sözleşmeleri bu beyaz listeyi endpoint düzeyinde tekrar açıkça belirtir; burada sayılmayan bir kaynakta istemci `id` göndermek `422 VALIDATION_FAILED` döndürür.

İstemci UUID'si sunucudaki yetki veya ilişki doğrulamasını atlatmaz. Örneğin sunucu; öğrencinin oturum/plan tarihinde ilgili sınıfa kayıtlı olduğunu, kurum zincirini, tek günlük yoklama oturumu kuralını, geçerli yoklama durumunu ve yazma iznini doğrular. Aynı istemci UUID ile farklı bir işlem yapılamaz; aynı UUID ile aynı idempotent yeniden deneme ise ikinci kayıt üretemez.

## 6. Sürüm ve varlık bazlı çakışma çözümü

### 6.1. Ortak protokol

Değiştirilebilir çekirdek kaynakların güncelleme, arşivleme ve durum değiştiren komutları `If-Match-Row-Version` taşır. Sunucu, sürüm eşleşmiyorsa sıradan kaynaklarda hiçbir değişiklik yapmadan `409 VERSION_CONFLICT` döndürür. İstemci yalnızca okumaya yetkili olduğu güncel kaynağı tekrar çeker; yerel taslağı kullanıcıya yeniden değerlendirmesi için koruyabilir, ancak eski isteği otomatik olarak yeni sürümle gönderemez.

Sunucu, başarılı her değiştirilebilir kaynak yazısında `rowVersion` değerini artırır ve kanonik sonucu döndürür. Arşivleme de bu kuraldadır; eşzamanlı başka güncellemeyi sessizce ezemez. `rowVersion` yoksa veya geçersizse istek `400 INVALID_REQUEST` ile reddedilir.

### 6.2. Varlık sınıfları

| Varlık / işlem | Çakışma kuralı | İstemci çözümü |
|---|---|---|
| Öğrenci, kişi, kurum, sınıf ve benzeri profil/metaveri güncellemesi | Sürüm uyuşmazlığında reddedilir; genel son yazan kazanır yoktur. | Güncel kaydı görür, yerel değişikliğini karşılaştırır ve yeni işlem oluşturur. |
| Yoklama kaydı — normal işaretleme/düzeltme | İstisna yalnızca normal **tekil** yoklama işaretleme/düzeltme ile normal **toplu** yoklama işaretleme/düzeltme komutlarında geçerlidir: aynı öğrenci-oturum kaydında sunucunun aldığı son **geçerli** yazma, başlangıç `rowVersion`ı eski olsa da uygulanır. Sunucu önce yetki, ilişki ve iş kuralını doğrular; önceki değeri audit kaydında tutar, `rowVersion`ı artırır ve yanıtta `concurrentChange: true` göstergesini döndürür. | İstemci kanonik sonucu çeker, eşzamanlı değişiklik göstergesini ve "başka hoca güncelledi" bilgisini görünür kılar. |
| Yoklama oturumu oluşturma | Aynı sınıf ve kurum iş gününde ikinci oturum oluşturulamaz. | `STATE_CONFLICT` sonrası kanonik mevcut oturum çekilir; istemci ikinci oturumu başarılı göstermez. |
| İlerleme kaydı | Aynı öğrenci-plan kalemi için kaynak sözleşmesinin tekil kaydı üzerinde sürüm kontrolü uygulanır; eski sürüm reddedilir. | Güncel ilerleme kaydı okunur, kullanıcı yeniden karar verir. |
| Program yapısı | Aktif kullanımda büyük değişiklik mevcut sürümü değiştirmez; yeni `program_version` oluşturur. | İstemci yeni sürümü çeker ve mevcut plan/ilerleme geçmişini değiştirmez. |
| Arşivleme / geri yükleme | Güncel `rowVersion` olmadan uygulanmaz; geri yükleme kuralları `DENETIM_VE_GERI_ALMA_ILKELERI.md`'de tanımlıdır. | Çakışmada güncel durumu gösterir; otomatik yeniden deneme yapmaz. |

Yoklama için “en güncel geçerli işlem” yalnızca yukarıdaki normal tekil/toplu işaretleme ve
düzeltme komutlarının alan-bazlı istisnasıdır; öğrenci, program, arşivleme veya geri alma
komutlarına genişletilemez. `DENETIM_VE_GERI_ALMA_ILKELERI.md` §5'teki tekil geri alma,
eski sürümde `409 VERSION_CONFLICT` üretmeye devam eder; §5.2'deki grup geri alma, bir
hedefte sürüm/değer çakışmasında atomik olarak `409 GROUP_UNDO_CONFLICT` üretir. Zaman
karşılaştırması istemci saatine göre değil, sunucunun aldığı ve doğruladığı işlem sırasına göre
yapılır.

## 7. Gerçek zamanlı olaylar ve güvenilir yeniden eşitleme

### 7.1. Olay güvenlik sınırı

Sunucu, başarılı transaction sonrasında ilgili kurum ve sınıf kapsamına küçük bir değişiklik
olayı yayımlar. Olay kanalı kurumu, etkin üyeliği, rolü ve sınıf atamasını doğrular; bir istemci
abone olduğu kanalın dışında veri veya varlık kimliği alamaz. Yetki/rol iptali sonrası abonelik
yeniden değerlendirilir ve erişim derhal kesilir. Gerçek zamanlı olay yalnızca hızlandırıcıdır;
doğruluğun kaynağı değişiklik akışı veya güvenli tam snapshot eşitlemesidir.

Olay, kaynak verinin yetkili okuma cevabı değildir. En az sabit `eventId`, monoton
`changeSequence`, `organizationId`, `entityType`, `entityId`, `changeType`,
`rowVersion`, `occurredAt` ve sınıf kapsamında ise `classId` taşır; serbest not, telefon,
adres, token veya gereksiz kişisel veri taşımaz. İstemci olayı aldığında normal okuma yetkisiyle
kanonik kaynağı yeniden çeker veya güvenli değişiklik akışını uygular. Aynı `eventId` iki kez
gelirse ayıklanır; daha eski `changeSequence` veya `rowVersion` kanonik veriyi geriye alamaz.

Bu olay, `VERI_MODELI.md` §14.1'deki `sync_changes` satırının taşıma görünümüdür. Başarılı
yazmada iş değişikliği, zorunlu audit, idempotency terminal sonucu ve `sync_changes` kaydı aynı
transaction'da oluşur; taşıma katmanı sabit `eventId` ile bu kaydı daha sonra yinelenebilir
biçimde yayımlar.

`sync_entity_catalog`, `CLASS`, `PROGRAM`, `PLAN_ITEM` ile öğrenci/veli/yoklama/ilerleme
aggregate'larını `requires_class_scope=true` ile kapalı olarak tanımlar; `sync_changes` bu
değere pinlenir. Bu nedenle bu türlerin `UPSERT`/`ARCHIVED` olayı sınıf kapsamı olmadan
yazılamaz. `TERM_CALENDAR`, `ORGANIZATION` ve `ORGANIZATION_SETTINGS` kurum kapsamlıdır.
İçerik değişikliği `PROGRAM` aggregate'ı üzerinden, özel yoklama durumu
`ORGANIZATION_SETTINGS` üzerinden eşitlenir; öğrenci/veli kişi değişikliği sırasıyla
`STUDENT`/`GUARDIAN` aggregate'ı üzerinden yayımlanır.

`REMOVED_FROM_SCOPE` daima kurum kapsamı ve dolu **eski** sınıf kapsamı taşır; yeni sınıf
kimliği veya yetkisiz başka veri taşımaz. Katalogta karşılığı olmayan V1 çekirdek değişikliğinin
üretimi reddedilir; yeni aggregate eşlemesi migration ile eklenmeden değişiklik eşitleme dışı
kalmaz.

### 7.2. Bağlantı kopması ve toparlama

Gerçek zamanlı kanal teslim garantisi sağlamaz; olay kaybı veya yinelenmesi normaldir. Sunucu
üretimli opak `syncToken`, belirli kullanıcı yetki bağlamı, kapsam/filtre, kurum ve gerekiyorsa
sınıf ile bağlanır; istemci token içeriğini okuyamaz veya başka bağlamda kullanamaz. Token,
monoton `changeSequence` üzerinden yalnızca o kapsamda oluşmuş değişiklik akışını verir.
Arşivleme, değişiklik akışında varlığı silmek yerine `ARCHIVED` durumunu ve yeterli kimliği
taşıyan tombstone olarak görünür; istemci yerel kaydı bu tombstone ile arşivler.

İstemci yeniden bağlandığında önce kendi bekleyen kuyruk işlemlerini korur, sonra son geçerli
`syncToken` ile değişiklik akışını tüketir. Token geçersiz, kapsamla uyumsuz veya süresi
dolmuşsa sunucu akışı kısmi vermek yerine güvenli tam snapshot zorunluluğunu bildirir. Tam
snapshot birden fazla sayfa gerektirirse sunucu, ilk sayfada kapsamı ve yüksek su işaretini
pinleyen opak bir snapshot işareti üretir; sonraki sayfalar aynı işaret olmadan alınamaz. Son
sayfa, o yüksek su işaretini izleyen yeni bir `syncToken`ı verir; istemci bu tokenı almadan
snapshotı tamam saymaz. Böylece snapshot sırasında oluşan sonraki değişiklikler yeni tokenla
alınır. Normal cursor sayfalama bu doğruluk garantisini vermez ve eşitleme doğruluk kaynağı
olarak kullanılmaz.

Taşıma, değişiklik akışı depolaması ve yerel kuyruk/önbellek teknolojisi sırasıyla A-006 ve
A-005'te seçilecektir. Oturum veya kurum bağlamı değiştiğinde önceki kuruma ait hassas yerel
verinin erişimi kaldırılır; başka kurum veya kullanıcı verisi gösterilemez.

### 7.3. Sınıf transferi, kapsam daralması ve yetki kaybı

Öğrenci sınıf transferinde, eski sınıfın yetkili değişiklik akışına aynı öğrenci/operasyonel
kayıtlar için `REMOVED_FROM_SCOPE` yazılır; yeni sınıfın yetkili akışına kanonik sürümü taşıyan
`UPSERT` yazılır. Bunlar kapsamı farklı iki `sync_changes` satırıdır; eski sınıfın istemcisi
öğrencinin yeni sınıfı veya yetkisiz verisi hakkında ek bilgi alamaz.

İstemci `REMOVED_FROM_SCOPE` aldığında ilgili öğrencinin yerel kişisel ve operasyonel verisini,
ilişkili liste önbelleklerini ve gösterilebilir özetlerini kaldırır. Token/oturum geçersiz veya
iptal edilmişse yazma `BLOCKED` durumda korunur ve yeniden kimlik doğrulama istenir. Sunucu
sınıf ataması ya da işlem yetkisi kaybını kesin `403`/`404` ile doğrulamışsa yazma
`NEEDS_ATTENTION` durumundadır; otomatik tekrar yoktur. Her iki durumda da kuyruk sessizce
silinmez, kapsam kaybında ilgili hassas yerel veri purge edilir ve eski kullanıcının veya eski
sınıfın verisi başka bağlamda gösterilemez ya da gönderilemez. Yeniden giriş veya yetkinin
sonradan geri gelmesi eski işi otomatik göndermez; mevcut kaynak/sürüm doğrulanır ve kullanıcı
yeniden karar verir.

## 8. Kullanıcı görünürlüğü ve hata davranışı

- Bekleyen yazma, ilgili kayıt üzerinde anlaşılır eşitleme durumu ve son güvenli hata ile görünür olur; kullanıcı "kaydedildi" ile "cihazda bekliyor" durumlarını ayırt eder.
- `NEEDS_ATTENTION` durumunda uygulama, güvenli bir sonraki hareket sunar: güncel veriyi gör, değişikliği gözden geçir, yeniden dene veya henüz sunucuya uygulanmamış yerel işlemi iptal et. Ham HTTP ayrıntısı veya başka kullanıcının hassas verisi gösterilmez.
- Oturum geçersiz/iptal edilmişse kuyruk girdisi `BLOCKED` kalır; oturum yenileme veya yeniden giriş istenir. Sunucu sınıf ataması ya da işlem yetkisi kaybını kesin `403`/`404` ile doğrulamışsa girdi `NEEDS_ATTENTION` kalır. Her iki durumda da kayıt sessizce silinmez; kapsam kaybında hassas yerel veri purge edilir. Kullanıcı sonradan yetki kazansa veya yeniden giriş yapsa bile eski işlem otomatik gönderilmez: mevcut kaynak ve sürüm yeniden doğrulanır, kullanıcı yeniden karar verir.
- Kuyruk ve yerel önbellek uygulama kapanması/yeniden açılmasında kaybolmaz. Cihazın güvenli saklama imkânları kullanılır; parola veya ham token kuyrukta, olayda ya da tanılama kaydında tutulmaz.

## 9. Zorunlu kabul senaryoları

1. Ağ kesintisi sırasında oluşturulan yoklama/ilerleme işlemi uygulama kapanıp açıldıktan sonra kalıcı kuyrukta durur ve aynı `clientMutationId` ile güvenle yeniden denenir.
2. Sunucu yazmayı ve denetim kaydını tamamlar, fakat cevap cihaza ulaşmaz; tekrar deneme ikinci kayıt, denetim kaydı veya olay üretmez ve ilk başarılı sonucu döndürür.
3. Aynı anahtar farklı yöntem, yol, hedef, gövde özeti veya `If-Match-Row-Version` ile kullanıldığında işlem uygulanmaz ve `IDEMPOTENCY_KEY_REUSED` döner.
4. Aynı anahtarla eşdeğer eşzamanlı iki istekten yalnızca biri yürütülür; diğeri `PENDING` takibi veya kalıcı ilk sonucu alır.
5. Eski `rowVersion` ile öğrenci bilgisi, ilerleme veya arşivleme sessizce değişmez; `VERSION_CONFLICT` sonrası istemci otomatik yeni sürümle yazmaz.
6. İki hoca aynı başlangıç `rowVersion`ıyla normal tekil veya toplu yoklama işaretleme/düzeltme yazar; sunucunun aldığı son geçerli yazma istisnayla uygulanır, önceki değer audit kaydında kalır, sürüm artar ve ikinci yanıt `concurrentChange: true` taşır.
7. Tekil yoklama geri alma eski `If-Match-Row-Version` ile `VERSION_CONFLICT` döner; grup geri almada tek hedefin sürüm/değer çakışması tüm grubu `GROUP_UNDO_CONFLICT` ile reddeder. Hiçbiri normal yoklama istisnasını kullanmaz.
8. Aynı sınıf/kurum iş günü için çevrimdışı iki yoklama oturumu oluşturma denemesinden ikincisi başarı sayılmaz; kanonik oturum kullanıcıya gösterilir.
9. Geçici ağ hatası, `429` ve `5xx` kuyruk girdisini silmez; `403`, `404`, `409` ve `422` ise görünür `NEEDS_ATTENTION` durumuna geçer ve otomatik yazma tekrarı yapmaz.
10. Yetkisi geri alınan hoca, bekleyen işlemini gönderemez veya sınıf olayına abone kalamaz; başka kurum/sınıfa ait idempotency sonucu ya da olay bilgisi alamaz.
11. Olaylar yinelenmiş veya sırasız geldiğinde istemci sabit `eventId` ile tekrarları ayıklar; doğruluk, olaydan değil monoton değişiklik akışı veya güvenli snapshot eşitlemesinden gelir.
12. İstemci UUID'si beyaz liste dışındaki oluşturma isteğinde reddedilir; beyaz listedeki aynı UUID ve anahtarın yeniden denemesi ikinci kayıt oluşturmaz.
13. Token süresi dolunca veya kapsamı değişince sunucu kısmi akış vermez; istemci yüksek su işaretli tam snapshot ve yeni `syncToken` ile uzlaştırır. Arşivlenen kaynak tombstone ile yerelde arşivlenir.
14. Lease sahibi çöktüğünde süresi geçen `PENDING` işlem daha büyük fencing sayacıyla güvenle yeniden sahiplenilir veya terminal hata olur; eski sahip terminal sonuç yazamaz ve yarım iş değişikliği, audit veya mantıksal olay kalmaz.
15. Sonuç saklama süresi geçip gövde/referans temizlenmiş olsa bile `keyRetentionExpiresAt`
    henüz geçmemişse anahtar tombstone'u korunur ve aynı anahtar yeni işlem gibi uygulanmaz;
    istemci kanonik uzlaştırma yapar.
16. Farklı kullanıcıyla girişte önceki kullanıcının kuyruk girdileri/önbelleği gösterilmez veya onun adına gönderilmez.
17. Platform yöneticisinin kurum oluşturma işlemi `GLOBAL` kapsamda idempotent olur; kurum-kapsamlı işlem `ORGANIZATION` kapsamını korur ve iki kapsam NULL-güvenli tekillikle birbirine karışmaz.
18. Öğrenci sınıf transferinde eski sınıf akışı `REMOVED_FROM_SCOPE`, yeni sınıf akışı yetkili `UPSERT` verir; eski sınıf istemcisi kişisel/operasyonel veriyi siler. Yetki iptalinde token doğrulaması sonrası snapshot/purge uygulanır.
19. Aktif programdaki büyük yapısal değişiklik yeni program sürümü oluşturur; geçmiş plan ve ilerleme kayıtları değişmeden kalır.
20. Ağ hatası, `429` veya geçici `5xx` sonrası aynı anahtarlı kayıt `PENDING` kalır ve yeniden denemede ikinci yan etki üretmez; bu sonuçlar terminal `FAILED` olarak saklanmaz.
21. `PENDING` kaydın `keyRetentionExpiresAt` değeri lease bitiminden önce olamaz; terminal kayıtta `createdAt ≤ completedAt < keyRetentionExpiresAt` sağlanır. Sonuç gövdesi/referansı `resultExpiresAt` sonrasında temizlenirken anahtar tombstone'u korunur.
22. Anahtar tombstone'u desteklenen azami çevrimdışı süreden uzun tutulur; tombstone silindikten sonra istemci eski anahtarı yeniden göndermez ve sunucunun bilinmeyen anahtarı reddetme garantisi olmadığını kabul eder.
23. `REMOVED_FROM_SCOPE` yalnız kurum kapsamı ve dolu eski `scopeClassId` ile yazılır; sınıf-kapsamlı sınıf/program/plan ve öğrenci/veli/yoklama/ilerleme `UPSERT` veya `ARCHIVED` olayı sınıf kimliği olmadan DB tarafından reddedilir. Kurum ayarı veya dönem/takvim olayı sınıf kimliği olmadan kabul edilir.
24. `400`/`403`/`404`/`409`/`422` ile kesinleşen sonuç terminal `FAILED` olarak saklanır; aynı anahtar aynı terminal sonucu döndürür, düzeltilmiş işlem yeni `clientMutationId` gerektirir.
25. Bir V1 çekirdek değişikliğinin üreticisi `sync_entity_catalog`ta karşılığı olmayan türü
    yazamaz; migration ilgili aggregate türünü ve sınıf kapsamı kararını eklemeden değişiklik
    sessizce eşitleme dışı kalmaz.

## 10. Uyum, varsayımlar ve kapsam dışı

- Bu belge, ana plan §12–§13'teki sürüm, idempotency, gerçek zamanlı yayılım, varlık bazlı çakışma ve kalıcı kuyruk ilkelerini; §18.2'deki ağ kesintisi/eşzamanlı hoca kabul senaryolarını somutlaştırır.
- `rowVersion`, UUID, kurum izolasyonu ve kapsam türü/NULL-güvenli idempotency tekilliği
  `VERI_MODELI.md` §2.1–§2.3 ve §14 ile uyumludur. Buradaki istek özeti, lease, terminal
  sonuç ve saklama ömrü kuralları o temel tablonun P-010 için açık bıraktığı durum makinesi
  ayrıntısını kesinleştirir.
- Başlıklar, hata kodları, `202`, idempotency kapsamı, cursor ve API yetki kuralları `API_GENEL_KURALLARI.md` §3–§8 ile uyumludur.
- WebSocket/SSE benzeri gerçek zamanlı taşıma, değişiklik akışı/olay saklama altyapısı, yerel veritabanı/kuyruk ürünü, geri çekilme parametreleri, somut azami kuyruk ömrü ve gerçek migration'lar bu görevin kapsamı dışındadır. Bunlar sırasıyla A-006, A-005 ve ilgili uygulama/operasyon görevlerinde bu bağlayıcı davranışı bozmayacak şekilde seçilecektir.
- Toplu yazmaların kaynak-bazlı atomiklik kararı, kaynak endpoint sözleşmesinde açıkça belirtilecektir; bu belge `API_GENEL_KURALLARI.md` §8'in kısmi başarı kuralını değiştirmez.
- Normal fiziksel silme, istemci UUID beyaz listesi dışındaki yeni kaynaklar ve `DENETIM_VE_GERI_ALMA_ILKELERI.md`'nin geri alma komutları bu belgeyle tanımlanmaz.
