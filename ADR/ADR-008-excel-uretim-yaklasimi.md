# ADR-008 — Excel üretme yaklaşımı

| Alan | Değer |
|---|---|
| Görev | A-008 — Excel üretme yaklaşımı ADR'si |
| Durum | Kabul edildi |
| Tarih | 15 Temmuz 2026 |
| Karar sahipliği | Dalga 1 teknik kararları |
| Bağımlılık | `EXCEL_RAPOR_VERI_SOZLESMESI.md` (P-012) |
| Etkilenen modül | EXPORT |

## Bağlam

V1; öğrenci, veli, yoklama, ilerleme ve dönem özeti verilerini tek kurum bağlamında, sunucuda
üretilen `.xlsx` çalışma kitaplarıyla dışa aktaracaktır. Mobil istemci yalnız işi başlatır,
durumu izler ve `READY` iş için verilen güvenli süreli bağlantıyı kullanır. `202 Accepted`,
`QUEUED` veya `RUNNING` dosyanın hazır olduğu anlamına gelmez.

P-012 çalışma kitabının sayfalarını, satır anlamlarını, deterministik sıralarını, hücre
tiplerini, yetki/kurum sınırını ve iş yaşam döngüsünü bağlayıcı biçimde tanımlar. Bu ADR'nin
karar alanı şunlardır:

- Java 21/Spring Boot backend içinde XLSX yazma kütüphanesi ve yazma modeli,
- rapor işinin kalıcı biçimde sıraya alınması ve bir worker tarafından sahiplenilmesi,
- bütün sayfaların aynı kaynak anını okumasını sağlayan snapshot materyalizasyonu,
- bellek, disk, hücre ve dosya kaynak sınırlarının uygulanma noktası,
- formül enjeksiyonu, geçici dosya ve başarısız/yarım artefakt güvenliği,
- üretim çıktısının doğrulanması ve EXPORT görevlerine aktarılacak test kapıları.

Nesne depolama sağlayıcısı ve süreli bağlantı yöntemi A-007'nin; kesin API filtreleri
EXPORT-001'in; veri sorguları EXPORT-002–EXPORT-004'ün; uygulama kodu EXPORT-005'in; indirme
bağlantısı ve sayısal yaşam süreleri EXPORT-006'nın; içerik doğrulama otomasyonu EXPORT-008'in
sorumluluğundadır.

## Karar sürücüleri

1. P-012'nin `.xlsx`, Türkçe başlık, çoklu sayfa ve kararlı hücre tipi sözleşmesini eksiksiz
   üretmek.
2. Büyük raporlarda bütün çalışma kitabını JVM heap'inde tutmamak; heap, geçici disk, satır,
   hücre ve dosya kullanımını sınırlamak.
3. Java 21 ve A-002'de seçilen Spring Boot modüler monolitiyle uyumlu, olgun ve bakımı süren bir
   kütüphane kullanmak.
4. Kullanıcı kontrollü metni formül, bağlantı veya çalışma kitabı yapısı olarak yorumlamamak.
5. Worker veya süreç kapanmasında kabul edilmiş işi kaybetmemek; birden fazla instance'ın aynı
   işi eşzamanlı üretmesini önlemek.
6. Yetki iptalini, kurum izolasyonunu, tutarlı kaynak snapshot'ını ve denetim kaydını P-012'deki
   işlem sınırlarında korumak.
7. Yeni mesaj aracısı veya ayrı raporlama servisi işletmeden V1 ihtiyacını karşılamak.
8. Üretilecek dosyayı Microsoft Excel ve en az bir bağımsız OOXML okuyucusuyla otomatik
   doğrulanabilir tutmak.

## Değerlendirilen seçenekler

### 1. Apache POI XSSF ile çalışma kitabını bellekte üretmek

`XSSFWorkbook`, XLSX özellikleri ve hücre tipleri için kapsamlı bir API sağlar. Ancak bütün
satırlar bellekte tutulduğu için rapor boyutu büyüdükçe heap tüketimi de büyür. P-012 zaten
toplam satır/hücre/dosya sınırı ister; buna rağmen normal ve beklenen bir raporun heap taşmasına
yaklaşması kabul edilemez. Bu nedenle yalnız küçük raporlara özel ikinci bir üretim yolu olarak
da seçilmemiştir; tek üretim yolunun test edilmesi daha güvenlidir.

### 2. Apache POI SXSSF ile ileri yönlü akış yazımı

`SXSSFWorkbook`, yalnız yapılandırılmış bir satır penceresini bellekte tutup eski satırları
geçici diske yazar. XSSF'in ortak hücre, stil ve biçim API'sini kullanır. P-012'nin ileri yönlü,
deterministik sayfa/satır üretimine uygundur; sözleşme geriye dönüp eski satırı düzenlemeyi,
grafik, yorum, formül değerlendirme veya sayfa klonlamayı gerektirmez.

Bedeli, worker diskinde sayfa başına geçici XML oluşmasıdır. POI belgeleri bu XML'in kaynak
veriden çok daha büyük olabileceğini ve sıkıştırmanın CPU maliyeti getirdiğini belirtir. Ayrıca
satır penceresinden düşen satırlara tekrar erişilemez; çalışma kitabı üreticisi önce gerekli
sıralama ve türetmeleri tamamlamalıdır.

### 3. fastexcel ile ileri yönlü doğrudan yazım

fastexcel daha dar özellik kümesi ve düşük kaynak tüketimine odaklanır; temel stil, tip, birden
fazla sayfa, shared/inline string ve akış çıktısı ihtiyaçlarını karşılayabilir. Kendi yayımladığı
karşılaştırmada POI'nin streaming yolu performans bakımından fastexcel'e yakındır. P-012'nin
özellikleri fastexcel ile de uygulanabilir görünse de Apache POI'nin daha geniş OOXML doğrulama
ekosistemi, güvenlik duyuruları, bilinen format sınırları ve Java ekiplerinde yaygınlığı bu
görevde daha düşük entegrasyon riski sağlar. fastexcel, EXPORT-005 ölçümünde SXSSF kabul
bütçesini karşılayamazsa yeniden değerlendirilecek bir alternatiftir; aynı anda iki üretici
desteklenmez.

### 4. İstek thread'inde senkron üretim

HTTP bağlantısının kopması işi belirsiz bırakır, uzun sorgu ve dosya üretimi request timeout'una
bağlanır ve `202 → QUEUED → RUNNING → READY|FAILED` sözleşmesini karşılamaz. Reddedilmiştir.

### 5. Harici mesaj aracısı veya ayrı rapor servisi

Kafka, RabbitMQ/SQS benzeri bir aracı kalıcı teslim ve ölçekleme sağlayabilir. V1'de rapor
hacmi buna dair kanıt sunmuyor; yeni servis, secret, izleme, retry ve hata ayıklama yüzeyi
oluşturur. Modüler monolit ve PostgreSQL zaten zorunlu bağımlılıklardır. Bu nedenle ilk seçim
değildir. Ölçülen kuyruk gecikmesi veya DB yükü kabul bütçesini aşarsa yeni ADR ile ele alınır.

## Karar

### XLSX üreticisi

V1 Excel çalışma kitapları, A-002'deki Java 21 backend içinde **Apache POI `poi-ooxml` ve
`SXSSFWorkbook`** ile üretilecektir. Uygulama kurulurken desteklenen güncel 5.5.x yaması BOM/
dependency lock üzerinden sabitlenecek; bilinen güvenlik düzeltmeleri nedeniyle 5.4.0 öncesi
bir sürüm kullanılamaz. Bu ADR'nin yazıldığı tarihte kararlı sürüm 5.5.1'dir; sürüm numarası
uygulama kaynak kodu yerine bu ADR ile sonsuza kadar dondurulmuş sayılmaz.
Apache POI Apache License 2.0 ile dağıtılır; `poi-ooxml` ve geçişli bağımlılıkları A-011/A-012
sonrasında dependency lock, SBOM ve güvenlik taramasına dahil edilir.

Üretim tek yönlüdür. Her sayfanın satırları P-012'deki tam bağlayıcılarla aynı transaction'da
materyalize edilen staging snapshot'ında sıralanır; flushed satıra geri dönülmez. `Rapor
Bilgisi` dahil sayfa adları sabit uygulama sabitleridir. Grafik, makro, yorum, pivot, dış
bağlantı, formül, kullanıcı tanımlı sayfa adı ve şablon çalışma kitabı kullanılmaz.

`SXSSFWorkbook` aşağıdaki bağlayıcı yapılandırma sözleşmesiyle açılır:

- satır penceresi, shared-string kullanımı ve geçici dosya sıkıştırması ortamdan değiştirilebilir
  ama doğrulanmış, üst sınırları olan EXPORT ayarlarıdır; sınırsız değer kabul edilmez,
- **shared strings varsayılan olarak açıktır**; daha geniş okuyucu uyumluluğu önceliklidir,
- materyalizasyon, toplam hücre ve benzersiz metin bütçesini üretimden önce/üretim sırasında
  denetler; shared-string tablosunun heap'i sınırsız büyümesine izin verilmez,
- geçici XML sıkıştırması varsayılan olarak açıktır; EXPORT-005 ölçümü CPU ve disk bütçesine
  göre bunu doğrular,
- workbook `try/finally` sınırında kapatılır ve `dispose()` sonucu denetlenir; temp temizleme
  hatası logda yalnız iş/instance teknik kimliğiyle alarm üretir, rapor verisi loglanmaz.

`writeAvoidingTempFiles` deneysel API'si seçilmez. POI'nin normal, belgelenmiş SXSSF yazma yolu
kullanılır.

### Hücre yazma ve formül güvenliği

EXPORT modülünde uygulamaya özel tek bir `SafeExcelCellWriter` sınırı bulunacaktır. Sayfa
üreticileri doğrudan POI `Cell` API'sine veya formül/hyperlink API'lerine erişmez. Bu sınır:

| P-012 türü | Yazım kararı |
|---|---|
| Metin ve telefon | `setCellValue(String)` ile gerçek `STRING`; telefon için metin biçimi |
| Salt tarih | `LocalDate` sayısal Excel tarihi + sabit `yyyy-mm-dd` stili |
| Tarih-saat | `Instant` önce UTC `LocalDateTime` değerine çevrilir; sayısal hücre + sabit `yyyy-mm-dd hh:mm:ss "UTC"` stili |
| Sayı | `setCellValue(double)`; tamsayıların Excel kesin sayı sınırını aşmadığı ayrıca doğrulanır |
| Kullanıcı görünür boolean | Yalnız kontrollü literal `Evet` veya `Hayır` string'i |
| Boş değer | Gerçek boş hücre; `0`, `Hayır` veya uydurma metin değildir |

Kullanıcı kontrollü metin `=`, `+`, `-` veya `@` ile başlasa dahi **yalnız string hücre** olarak
yazılır; görünür değer değiştirilmez. `setCellFormula`, `setHyperlink`, rich text, external link,
named range ve kullanıcı girdisinden üretilen stil/format çağrıları uygulama sınırında yasaktır.
Üretim sonrası doğrulama hiçbir hücrenin `FORMULA` türünde olmadığını ve hiçbir external-link
ilişkisi bulunmadığını kanıtlar. Böylece görünür metne apostrof ekleyip kaynak veriyi değiştirmek
yerine OOXML hücre tipiyle formül yorumu engellenir.

Bir metin XLSX'in 32.767 karakter hücre sınırını aşarsa sessizce kırpılmaz; iş güvenli
`EXPORT_TOO_LARGE` sonucuyla dosya oluşturmadan biter. Aynı kural fiziksel 1.048.576 sayfa satırı
sınırı ve yapılandırılmış toplam satır/hücre/dosya bütçeleri için geçerlidir. Hücre stilleri her
hücrede yeniden üretilmez; küçük, sabit ve önceden oluşturulmuş bir stil kataloğu paylaşılır.

### Bağlayıcı snapshot ve materyalizasyon modeli

V1, transaction kapandıktan sonra canlı iş tablolarını `sourceCutoffAt` filtresiyle yeniden
sorgulamaz. Zaman damgası tek başına MVCC snapshot'ı temsil etmediğinden bu yaklaşım bütün
sayfaların aynı veri hâlini gördüğünü kanıtlayamaz. Bağlayıcı seçim, **aynı PostgreSQL
`REPEATABLE READ` transaction'ında sıralı rapor satırlarını şifreli staging chunk'larına
materyalize etmektir**.

Worker, güncel sahipliğini doğruladıktan sonra kurum bağlamlı, okuma-yazma ve kesin timeout'lu
bir `REPEATABLE READ` transaction açar:

1. İlk yetki/kaynak sorgusu transaction snapshot'ını kurar ve aynı SQL ifadesinde DB
   `transaction_timestamp()` değerini `sourceCutoffAt` olarak alır. Güncel üyelik, rol,
   `REPORT_EXPORT`, gerekli veli izni ve hoca sınıf ataması bu snapshot içinde yeniden
   doğrulanır.
2. P-012'deki tam bağlayıcılarla sıralanan bütün seçili bölüm satırları, çalışma kitabı satırı
   biçimine dönüştürülür ve sabit boyutlu şifreli chunk'lar olarak staging'e yazılır. Rapor
   numaraları ve özetler de bu transaction içindeki aynı snapshot'tan hesaplanır.
3. `export_jobs.source_cutoff_at`, staging manifesti, satır/hücre/byte sayaçları ve staging
   TTL'si aynı transaction'da yazılır. Transaction ancak bütün bölümler eksiksiz
   materyalize edildiğinde commit edilir; hata, timeout, kota veya yetki kaybı tüm staging'i
   rollback eder.
4. Commit sonrasında XLSX worker canlı iş tablolarını okumaz; manifestteki bölüm/chunk sırasına
   göre yalnız kendi şifreli staging verisini çözüp SXSSF'e ileri yönlü yazar.

Bu transaction dosya üretimi veya upload boyunca açık tutulmaz. Snapshot materyalizasyonu
ayrı, süre ve statement timeout'larıyla sınırlı bir adımdır; izin verilen bütçede tamamlanamazsa
iş güvenli biçimde `FAILED` olur. Böylece aynı MVCC görünümü korunurken uzun ömürlü DB
transaction bırakılmaz.

Staging fiziksel modeli EXPORT modülünün sahip olduğu `export_snapshot_manifests` ve
`export_snapshot_chunks` tablolarıdır. Her iki tablo en az şu kapsam anahtarlarını taşır:
`organization_id`, `export_job_id`, monotonik `attempt_no` (fencing token), opak `attempt_id`;
chunk tablosu ayrıca sabit `section_code`, `chunk_no`, `row_count`, `cell_count`, `byte_count`,
`ciphertext`, `nonce`, `key_reference`, `created_at` ve `expires_at` taşır. Tekillik
`(organization_id, export_job_id, attempt_no, section_code, chunk_no)` ile, iş bağı ise
`(organization_id, export_job_id)` bileşik FK'siyle korunur. Yeni claim eski attempt
staging'ini kullanmaz.

Staging tablolarında **FORCE RLS** uygulanır. Normal EXPORT worker policy'si ancak transaction
bağlamındaki `organization_id + export_job_id + attempt_no` üçlüsüyle eşleşen satırı okur/yazar;
boş bağlam, başka kurum, başka iş veya stale attempt varsayılan olarak reddedilir. RLS'yi aşan
owner/superuser uygulama rolü kullanılmaz. Crash temizleyicisinin ayrı, en dar bakım rolü yalnız
TTL'si geçmiş veya canlı lease'i bulunmayan attempt'ları silebilir; payload okuyamaz ve bu
işlemler teknik audit/metric üretir.

Chunk payload'ı uygulama katmanında attempt başına veri şifreleme anahtarıyla **AES-256-GCM
envelope encryption** kullanılarak şifrelenir. Anahtar yöneticisindeki ana anahtar repoda veya
tabloda tutulmaz; staging yalnız key reference/wrapped key bilgisini taşır. AAD en az
`organization_id`, `export_job_id`, `attempt_no`, `section_code`, `chunk_no`, şema sürümü ve
`sourceCutoffAt` değerlerini bağlar; chunk başka iş/kurum/attempt altında çözülemez. DB/volume
şifrelemesi buna ek savunmadır, uygulama katmanı şifrelemesinin yerine geçmez.

Her attempt, yapılandırılmış iş başı ve kurum başı staging satır/hücre/byte kotasından atomik
rezervasyon yapar. Sayaçlar manifest transaction'ında kilitlenir; sınır aşımı tüm transaction'ı
rollback edip `EXPORT_TOO_LARGE` üretir. `expires_at`, `min(export_job.expires_at,
snapshot_created_at + export.snapshotTtl)` olarak zorunlu ve sonlu yazılır;
`export.snapshotTtl`, izin verilen azami attempt çalışma bütçesinden uzun olmak zorundadır.
Sayısal kota ve TTL değerleri A-010 kapasite profili/EXPORT-005 ölçümüyle sabitlenir; `NULL`,
sınırsız veya job süresini aşan TTL kabul edilmez.

`request_snapshot` yalnız doğrulanmış kimlik/filtreler, bölüm bayrakları, kurum saat dilimi ve
güvenli sayaç/şema bilgisini taşır. Ad, telefon, not, yoklama/ilerleme değeri veya çalışma kitabı
satırı gibi ham kişisel veri `request_snapshot`, log, metric ya da audit payload'ına yazılmaz.

Staging cleanup davranışı bağlayıcıdır:

- `READY` commit'inden sonra ilgili attempt staging'i derhal silinmeye çalışılır; silme hatası
  READY sonucunu geri çevirmez, TTL temizleyicisine güvenli teknik alarm bırakır.
- Üretim/validation/upload hatası veya lease kaybında worker yalnız kendi
  `(organization_id, export_job_id, attempt_no)` staging'ini koşullu siler.
- Crash sonrası başlangıç uzlaştırıcısı yalnız TTL'si geçmiş veya job'ın güncel attempt'ından
  küçük olup canlı lease'i bulunmayan staging'i temizler. Güncel canlı attempt'a ve başka kuruma
  dokunamaz.
- Yeni attempt her zaman yeni snapshot üretir; önceki attempt'ın ciphertext'ini veya geçici
  dosyasını sahiplenmez.

### Kalıcı iş kuyruğu ve worker

`export_jobs`, V1'de hem iş yaşam döngüsü kaydı hem de kalıcı kuyruk kaynağıdır. Ayrı bir mesaj
aracısı eklenmez. API, idempotency kontrolüyle aynı işlemde `QUEUED` kaydı oluşturduktan sonra
`202` döner. İş yalnız veritabanına commit edildikten sonra görünür; bellek içi event veya
`@Async` çağrısı teslim garantisi değildir.

Aynı modüler monolit artefaktındaki EXPORT worker, Spring `TaskScheduler` ile yapılandırılmış
aralıkta uygun işleri sorgular. Birden çok instance aşağıdaki fencing/heartbeat protokolüyle
güvenle çalışır:

1. Kısa claim transaction'ı, deterministik `created_at, id` sırasıyla bir işi `FOR UPDATE SKIP
   LOCKED` ile seçer; `attempt_no = attempt_no + 1` yapar. Bu monotonik sayı aynı zamanda
   fencing token'dır. Transaction ayrıca rastgele `attempt_id`, `lease_owner`, `heartbeat_at`,
   `lease_expires_at` ve o attempt'a özel opak `artifact_attempt_key` üretip `RUNNING` yazar.
2. Heartbeat, lease süresinin en fazla üçte biri aralıkla çalışır ve yalnız
   `organization_id + export_job_id + status=RUNNING + attempt_no + attempt_id + lease_owner`
   eşleşiyorsa lease'i ileri taşır. Koşullu update sıfır satır etkilerse worker sahipliği
   kaybetmiştir; yeni yan etki başlatamaz.
3. Worker snapshot transaction'ından hemen önce, upload'dan hemen önce ve finalize
   transaction'ından hemen önce aynı fencing koşulunu doğrular. Uzun workbook yazımı sırasında
   heartbeat sürer; heartbeat başarısızlığında yazım kesilir ve çıktı yayınlanmaz.
4. Geçici dosya tamamlanıp içerik doğrulaması, boyut ve SHA-256 checksum hesabı geçtikten sonra
   A-007 storage portuna yalnız güncel attempt kimliğiyle teslim edilir. Storage anahtarı
   kullanıcı/kurum/iş adı içermeyen, server üretimli ve **attempt-kapsamlı opak** anahtardır;
   farklı attempt aynı anahtara yazamaz veya var olan nesnenin üstüne yazamaz.
5. Upload dönüşünde sahiplik tekrar doğrulanır. Lease kaybedilmişse worker finalize/audit
   yapamaz; yalnız kendi `artifact_attempt_key` nesnesini silmeye çalışır ve kendi staging/temp
   alanını temizler. Silme başarısızsa attempt anahtarı orphan uzlaştırıcısına kalır.
6. Finalize tek DB transaction'ıdır: geçerli fencing koşuluyla `RUNNING → READY`, dosya
   metaverisi/checksum ve **bir** `REPORT_EXPORTED` audit satırı birlikte yazılır. Audit için
   `audit_logs` üzerinde `action_type='REPORT_EXPORTED'` ve hedef `EXPORT_JOB` iken
   `(organization_id, action_type, target_entity_type, target_entity_id)` partial unique
   constraint'i bulunur. Update veya audit insert'ten biri başarısızsa ikisi de rollback olur.
7. Upload başarılı, DB finalize başarısızsa iş `READY` değildir. Aynı attempt lease hâlâ
   geçerliyse koşullu finalize yeniden denenebilir; lease kaybolmuşsa nesne orphan sayılır,
   attempt-kapsamlı anahtarla silinir. Sonraki claim daha yüksek `attempt_no` ve yeni storage
   anahtarı kullanır.
8. Stale worker daha sonra dönse bile düşük `attempt_no` bütün heartbeat, staging, upload öncesi
   doğrulama ve finalize koşullarında reddedilir. En fazla orphan attempt nesnesi bırakabilir;
   kanonik `READY` metaverisini veya audit kaydını değiştiremez.

PostgreSQL belgeleri `SKIP LOCKED` görünümünün genel sorgular için tutarsız olduğunu, fakat
queue-benzeri tablolarda çoklu consumer kilit çekişmesini azaltmak için uygun olduğunu belirtir.
Bu kullanım yalnız iş **sahiplenme** sorgusudur; rapor kaynak verisinin tutarlı snapshot'ı için
`SKIP LOCKED` kullanılmaz.

İşçi/kuyruk adapterı EXPORT modülünün infrastructure katmanında kalır. Domain ve application
katmanları Spring scheduler, POI veya PostgreSQL locking API'sine bağımlı olmaz. Scheduler ile
üretim executor'ı ayrıdır; yapılandırılmış küçük bir concurrency sınırı ve bounded executor
uygulanır. Kuyruk doluluğu yeni isteği kaybetme gerekçesi değildir; DB'deki `QUEUED` iş daha sonra
işlenir. Worker'ın zarif kapanışı yeni iş sahiplenmeyi durdurur; aktif attempt heartbeat'ini
yalnız kontrollü tamamlanma süresince sürdürür, aksi halde işi daha yüksek attempt tarafından
yeniden sahiplenilebilir bırakır.

### Geçici artefakt ve mahremiyet

SXSSF geçici XML'i ve tamamlanan yerel `.xlsx` dosyası yüksek riskli toplu kişisel veri taşır.
POI `TempFile` varsayılan stratejisi process-global/statik durumdur; SXSSF `SheetDataWriter`
sayfa dosyalarını bu utility üzerinden açar. Bu nedenle `TempFile.setTempFileCreationStrategy`
**iş başına çağrılamaz** ve paralel worker'lar arasında global strateji değiştirilemez.

Seçilen uygulanabilir mekanizma, POI 5.5.1'de bulunan
`TempFile.withStrategy(perAttemptStrategy, task)` thread-local kapsamıdır. Her workbook'ın
oluşturulması, bütün sayfalarının yazılması, çıktı dosyasının tamamlanması, `dispose()` ve
`close()` işlemleri aynı dedicated executor thread'inde ve aynı `withStrategy` çağrısı içinde
gerçekleşir. Bu kapsam child thread'e taşınmaz; tek workbook üretimi paralel alt görevlere
bölünmez. Kapsam dışından SXSSF oluşturma fail-fast reddedilir. Process-global varsayılan
strateji uygulama açılışında güvenli fallback'e bir kez sabitlenebilir, fakat aktif iş sırasında
değiştirilemez.

Her claim için güvenilir server değerlerinden
`<sabit-export-temp-root>/<opaque-attempt-id>/` dizini oluşturulur. `organization_id`, kullanıcı
girdisi, kurum/öğrenci adı veya dosya adı path bileşeni olmaz. Per-attempt
`TempFileCreationStrategy.createTempFile/createTempDirectory` yalnız bu önceden doğrulanmış
dizin içinde `Files.createTempFile/createTempDirectory` ile rastgele, `CREATE_NEW` semantiğinde
nesne üretir ve oluşturduğu yolları attempt envanterinde tutar.

- Sabit root uygulama başında `toRealPath(NOFOLLOW_LINKS)` ile pinlenir, worker dışındaki
  kullanıcılarca yazılamaz ve POSIX sistemlerde `0700` dizin / `0600` dosya izinleri zorlanır.
- Attempt dizini oluşturulurken ve her dosya dönüşünde normalize/real parent'ın pinli root
  altında kaldığı doğrulanır; mevcut symlink, symlink parent, `..` ve mutlak kullanıcı yolu
  reddedilir. Destekleyen dosya sistemlerinde link sayısı `1` olmalıdır; desteklemeyenlerde
  root'un yalnız worker tarafından yazılabilmesi ve `CREATE_NEW` envanteri hard-link ekleme
  yüzeyini kapatır. Cleanup link takip etmez.
- `close()` yalnız underlying workbook/package'i kapattığından temp temizliği sayılmaz.
  Başarı ve bütün hata yollarında nested `finally` blokları çıktı stream'ini kapatır ve hem
  `dispose()` hem `close()` çağrısını garanti eder; iki sonuç ayrı denetlenir. Son olarak yalnız
  current attempt envanterindeki dosyalar ve boş attempt dizini silinir.
- Cleanup hiçbir zaman ortak root'u recursive silmez, glob/prefix ile başka attempt seçmez ve
  symlink takip etmez. İki aktif workbook'ın envanterleri/dizinleri ayrıdır; biri diğerinin
  dosyasını silemez.
- Temp volume kalıcı paylaşım veya HTTP dizini değildir; şifreli volume ve attempt/job/kurum
  kotası A-010 kapasite profilinde zorunludur. Disk dolması `FAILED` sonucudur.
- Crash sonrası başlangıç uzlaştırıcısı dizin adındaki opak attempt kimliğini DB attempt
  kaydıyla eşler. Yalnız canlı lease'i olmayan ve grace süresi/TTL'si geçmiş dizini,
  `NOFOLLOW_LINKS` ile dosya dosya siler; bilinmeyen, symlink içeren veya aktif attempt dizinini
  karantinaya alıp alarm üretir, kendiliğinden takip edip silmez.
- Log, metric ve trace'e hücre değeri, gerçek temp yolu, depolama anahtarı, indirme URL'si veya
  kullanıcı kontrollü dosya adı yazılmaz.
- Kısmi dosya indirilebilir anahtara yayımlanmaz. Storage upload başarılı olup DB `READY` +
  audit transaction'ı commit edilmeden istemciye artefakt görünmez; orphan attempt nesneleri
  A-007/EXPORT-006 uzlaştırıcısıyla yalnız tam attempt anahtarı üzerinden silinir.

POI bu senaryoda güvenilir olmayan dosya **okumaz**; yalnız sunucunun doğruladığı değerlerden
yeni dosya yazar. Yine de dependency güvenlik güncellemeleri izlenir ve en güncel desteklenen
patch hattında kalınır.

## Gerekçe

SXSSF; P-012'nin istediği hücre tipleri, sabit biçimler ve çoklu sayfalar için yeterli API'yi,
tam XSSF çalışma kitabını heap'te tutmadan sağlar. Alternatif fastexcel kaynak tüketiminde güçlü
olsa da seçilen gereksinimlerde SXSSF'e karşı kanıtlanmış belirleyici bir üstünlük sunmaz;
Apache POI'nin daha geniş kullanım, güvenlik duyurusu ve OOXML doğrulama yüzeyi ilk uygulama
riskini azaltır. Kalıcı PostgreSQL işi ise zaten seçilmiş altyapıyla süreç çökmesi ve çoklu
instance güvenliğini sağlar. Bu bileşim V1 için yeni broker veya mikroservis maliyeti doğurmadan
ölçülebilir bir yükseltme yolu bırakır.

## Kabul ve doğrulama kapıları

A-008 bir karar belgesidir; henüz uygulama iskeleti bulunmadığından bu görevde kütüphane veya
çalıştırılabilir kod eklenmez. EXPORT-005 ve EXPORT-008 en az aşağıdaki otomasyonu üretmeden bu
karar uygulanmış sayılmaz:

1. P-012 §8'deki 20 kabul senaryosunun dosya üretimiyle ilgili olanları ve KAP-30 geçer.
2. Üretilen dosya Apache POI ile yeniden açılır; OOXML ZIP/XML yapısı ikinci bağımsız okuyucu
   veya doğrulayıcıyla da kontrol edilir. Gerçek Microsoft Excel açılış smoke testi pilot/yayın
   kapısında kayıt altına alınır.
3. Her sayfanın adı, sütun sırası, satır sayısı, deterministik sırası, hücre türü ve görünür
   biçimi altın dosya görüntüsü yerine semantik assertionlarla doğrulanır.
4. `=`, `+`, `-`, `@` önekli değerler `STRING` kalır; çalışma kitabında formül, hyperlink veya
   external-link ilişkisi bulunmaz.
5. Aynı adlı iki öğrencinin rapor numarası, sınıf transferi, `UNMARKED` dışlama ve çoklu CUSTOM
   sayımı P-012 ile birebir doğrulanır.
6. Satır, hücre, benzersiz metin, 32.767 karakter ve çıktı boyutu limitlerinde kısmi dosya
   oluşmadan `EXPORT_TOO_LARGE` üretilir.
7. Yetki iptali snapshot'tan önce işi durdurur; başka kurum kimliği hiçbir hücre veya
   metaveriye sızmaz.
8. Lease workbook üretimi sırasında sona ererken iki worker aynı iş için yarıştırılır. Yeni
   worker daha yüksek `attempt_no` alır; eski worker'ın heartbeat, snapshot okuma, upload öncesi
   doğrulama ve finalize girişimleri fencing ile reddedilir.
9. Küçük, orta ve izin verilen en büyük sentetik fixture için wall time, tepe heap, temp disk,
   çıktı boyutu ve cleanup süresi ölçülür. Sayısal kabul bütçeleri A-010 kapasite profili ve
   EXPORT-005 uygulama notunda sabitlenir; bütçeyi aşan yapılandırma üretime alınmaz.
10. Upload başarılı olduktan sonra DB finalize hata enjeksiyonuyla kaybedilir. İş `READY`
    görünmez; geçerli attempt güvenle finalize retry yapar veya lease kaybından sonra yalnız
    attempt-kapsamlı orphan nesne temizlenir.
11. Aynı işin eşzamanlı/stale finalize denemelerinden DB'de yalnız bir `READY` sonucu ve partial
    unique constraint nedeniyle yalnız bir `REPORT_EXPORTED` audit satırı oluşur; ikisi aynı
    transaction'dadır.
12. İki eşzamanlı workbook ayrı `TempFile.withStrategy` kapsamlarında üretilir. Her SXSSF/temp
    çıktı yalnız kendi attempt dizininde oluşur; birinin `dispose`, `close` veya cleanup'ı diğer
    aktif workbook'ın dosyalarını etkilemez.
13. Snapshot staging için başka kurum, başka iş ve stale attempt erişimi FORCE RLS ile
    reddedilir. Başarı, hata, lease kaybı, TTL ve crash senaryolarında yalnız hedef attempt
    staging'i temizlenir; başka kurumun/canlı attempt'ın satırı korunur.
14. Hata enjeksiyonuyla snapshot, workbook yazımı, storage upload, DB finalize ve temp cleanup
    kesilir; hiçbir aşamada `READY` olmayan artefakt indirilemez ve `request_snapshot`/log/audit
    içinde ham kişisel veri bulunmaz.

## Sonuçlar

### Olumlu sonuçlar

- XLSX üretimi backend diliyle aynı süreçte, ayrı bir servis veya mesaj aracısı olmadan yapılır.
- SXSSF satır penceresi büyük raporlarda heap'i sınırlar; P-012'nin tip ve stil gereksinimleri
  POI'nin ortak API'siyle uygulanır.
- Kalıcı DB işi, süreç kapanması ve birden fazla instance durumunda kabul edilmiş rapor
  taleplerini korur.
- Tek güvenli hücre yazıcısı formül/hyperlink yüzeyini merkezileştirir ve test edilebilir kılar.

### Maliyetler ve riskler

- Shared strings benzersiz metin sayısıyla heap tüketir. Materyalizasyon bütçesi, heap ölçümü
  ve bounded concurrency zorunludur; bütçe sessizce yükseltilmez.
- SXSSF geçici XML'i çok büyüyebilir. Sıkıştırma, temp disk kotası, şifreli volume ve kesin
  cleanup gerekir; disk dolması güvenli `FAILED` sonucudur, `READY` değildir.
- DB tabanlı polling çok yüksek iş hacminde gecikme veya DB yükü yaratabilir. Kuyruk yaşı,
  sahiplenme gecikmesi, deneme ve başarısızlık metrikleri izlenir; kanıt oluşursa harici broker
  ayrı ADR ile değerlendirilir.
- DB ile nesne depolama arasında dağıtık transaction yoktur. Koşullu finalize ve orphan
  uzlaştırması zorunludur.
- Şifreli snapshot staging DB alanı ve kısa bir bounded transaction gerektirir. Kota/TTL,
  FORCE RLS, attempt fencing ve cleanup testleri olmadan EXPORT üretime alınamaz.
- POI temp strategy'nin process-global fallback'i yanlış değiştirilirse paralel workbook'lar
  birbirinin dizinine yazabilir. İş başına global setter yasağı ve 5.5.1 thread-local
  `withStrategy` testi bu riske karşı zorunludur.
- Excel tarih hücresi saat dilimi taşımaz. Bütün `Instant` değerleri yazımdan önce UTC duvar
  zamanına dönüştürülür ve görünür biçimde `UTC` etiketi kullanılır; kurum salt tarihleri
  dönüştürülmez.

## Kapsam dışı

- Nesne depolama ürünü, bucket politikası, presigned URL yöntemi ve sayısal URL/artefakt süresi.
- Kesin worker sayısı, polling aralığı, lease süresi, retry sayısı, heap/temp disk/satır/hücre/
  dosya/staging kota ve TTL değerleri; bunlar ortam kapasitesi ölçülmeden uydurulmaz. Buna
  rağmen sonlu TTL, atomik kota ve timeout zorunluluğu bu ADR'de bağlayıcıdır.
- EXPORT tablo migration'ı, API endpoint'i, veri sorguları ve üretim kodu.
- CSV/PDF, Excel içe aktarma, parola korumalı XLSX, e-posta eki, zamanlanmış rapor, grafik,
  pivot, makro, kurumlar arası rapor ve öğretmen notu dışa aktarımı.
- Kütüphanenin kaynak veriye erişmesi; POI yalnız doğrulanmış EXPORT snapshot DTO'larını alır.

## Kaynaklar ve uyum

- `URUN_VE_UYGULAMA_PLANI.md` §8.9, §11.1, §18.1–2, §19 ve §24.
- `EXCEL_RAPOR_VERI_SOZLESMESI.md` §1–10.
- `KRITIK_TEST_VE_KABUL_PLANI.md` KAP-30.
- `ADR/ADR-002_BACKEND_DILI_VE_FRAMEWORK.md` — Java 21, Spring Boot ve modüler monolit sınırı.
- `ADR/ADR-003-postgresql-ve-hosting.md` — PostgreSQL ve ortam sınırı.
- [Apache POI Spreadsheet overview](https://poi.apache.org/components/spreadsheet/index.html) —
  XSSF/SXSSF ayrımı ve SXSSF sliding-window davranışı.
- [Apache POI `SXSSFWorkbook` API](https://poi.apache.org/apidocs/dev/org/apache/poi/xssf/streaming/SXSSFWorkbook.html) —
  shared/inline string, geçici dosya, sıkıştırma, `close()` ve `dispose()` davranışları.
- [Apache POI `TempFile` API](https://poi.apache.org/apidocs/dev/org/apache/poi/util/TempFile.html) —
  statik varsayılan strategy ve POI 5.5.x thread-local strategy kapsamı.
- [Apache POI `TempFile` resmî kaynak kodu](https://github.com/apache/poi/blob/trunk/poi/src/main/java/org/apache/poi/util/TempFile.java) —
  process-global strategy, `ThreadLocal` önceliği ve `withStrategy` restore davranışı.
- [Apache POI `TempFileCreationStrategy` API](https://poi.apache.org/apidocs/dev/org/apache/poi/util/TempFileCreationStrategy.html) —
  temp dosya/dizin oluşturma ve uygulamaya özel cleanup stratejisi sözleşmesi.
- [Apache POI `SheetDataWriter` resmî kaynak kodu](https://github.com/apache/poi/blob/trunk/poi-ooxml/src/main/java/org/apache/poi/xssf/streaming/SheetDataWriter.java) —
  SXSSF sheet temp dosyalarının `TempFile.createTempFile` üzerinden oluşturulması.
- [Apache POI `Cell` API](https://poi.apache.org/apidocs/dev/org/apache/poi/ss/usermodel/Cell.html) —
  string, sayı, `LocalDate`, `LocalDateTime` ve formül yazım API'lerinin ayrılığı.
- [Apache POI `SpreadsheetVersion`](https://poi.apache.org/apidocs/dev/org/apache/poi/ss/SpreadsheetVersion.html) —
  XLSX satır, sütun, hücre metni ve stil sınırları.
- [Apache POI sürüm ve güvenlik duyuruları](https://poi.apache.org/download.html) — güncel kararlı
  sürüm; [POI güvenlik rehberi](https://poi.apache.org/security.html) — dependency güvenliği.
- [fastexcel resmî deposu](https://github.com/dhatim/fastexcel) — alternatifin özellik,
  streaming ve shared/inline string yaklaşımı.
- [PostgreSQL `SELECT` locking clause](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE) —
  `SKIP LOCKED`ın queue-benzeri çoklu consumer kullanım sınırı.
- [Spring task execution and scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) —
  `TaskScheduler`/`TaskExecutor` ayrımı ve bounded worker yapılandırma yüzeyi.
