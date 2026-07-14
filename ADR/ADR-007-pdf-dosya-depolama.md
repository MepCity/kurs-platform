# ADR-007 — PDF ve dosya depolama

| Alan | Değer |
|---|---|
| Durum | Koşullu kabul — S3 referans sözleşmesi korunuyor, provisioning ertelendi |
| Tarih | 15 Temmuz 2026 |
| Görev | A-007 — PDF/dosya depolama ADR'si |
| Karar sahibi | Ürün sahibi |
| Bağımlılıklar | `KISISEL_VERI_ENVANTERI.md` (P-004), `EXCEL_RAPOR_VERI_SOZLESMESI.md` (P-012) |
| İlgili sonraki görevler | OPS-005, OPS-006, ORG-002, CONTENT-001, CONTENT-003, EXPORT-006 |

## 1. Karar özeti

Kapalı alfa dosya yüklemiyorsa hiçbir uzak nesne deposu açılmaz. İlk gerçek dosya ihtiyacında
bu ADR'deki **Amazon S3 Standard `eu-central-1` (Frankfurt)** tasarımı güvenlik ve geri yükleme
referansıdır; ancak dört hesaplı AWS topolojisi otomatik olarak provision edilmez. Önce A-007R,
S3'ü erteleyerek koruma ile Cloudflare R2 `eu` jurisdiction + immutable key + bağımsız backup
modelini aynı yaşam döngüsü ve imha senaryolarıyla ölçer. Sonuç onaylanmadan R2 “ucuz S3” gibi
eşdeğer kabul edilmez.

Seçilecek uzak depoda kurum logosu, kişi profil fotoğrafı, içerik PDF'si ve geçici Excel rapor
artefaktları herkese kapalı tutulur. Mobil istemci kalıcı sağlayıcı kimlik bilgisi almaz ve
hiçbir bucket doğrudan herkese açık yapılmaz.

Kalıcı nesne anahtarları kullanıcı girdisinden, dosya adından veya kurum kimliğinden türetilmez;
kriptografik olarak rastgele, opak değerlerdir. Veritabanındaki kurum kapsamlı `file_assets`
kaydı, nesne anahtarının sahibi ve erişim kaynağıdır. Excel artefaktları ise
`EXCEL_RAPOR_VERI_SOZLESMESI.md` §5 uyarınca `export_jobs` tarafından yönetilir ve
`file_assets` sayılmaz.

İstemciye S3 kalıcı nesne anahtarı veya S3 ön-imzalı indirme URL'si verilmez. Backend, güncel
kimlik/kurum/rol/sınıf yetkisini doğruladıktan sonra kısa ömürlü, iptal edilebilir ve yalnız tek
dosyaya bağlı bir **uygulama indirme tokenı** üretir. Dönen HTTPS URL'si platform API'sine
aittir; istemci bu URL'yi geçerli platform oturumuyla çağırır, backend yetkiyi tekrar doğrular
ve nesneyi S3'ten akışla iletir. Böylece başka kuruma ait nesne varlığı, kalıcı depolama anahtarı
ve sağlayıcı kimliği istemciye açılmaz.

Bu ADR sağlayıcı hesabı veya bucket oluşturmaz. Dört ayrı AWS hesaplı production topolojisi
kritik-production referansıdır; gerçek kurum pilotunda zorunlu başlangıç topolojisi değildir.
OPS-005 ancak dosya gereksinimi ve A-007R sağlayıcı kararı tamamlandığında seçilen profili
provision eder. Secret/workload identity A-013, log/alarmlar A-014, nesne geri yükleme tatbikatı
OPS-006 kapsamındadır.

## 2. Bağlam

V1'de üç farklı yaşam döngüsü olan dosya grubu vardır:

1. İçerik PDF'leri, kurum logoları ve kişi profil fotoğrafları ürün kayıtlarına bağlı kalıcı
   varlıklardır. Normal kullanıcı işlemi fiziksel silme yerine arşivleme yaklaşımını korur.
2. Excel raporları; veli telefonu, yoklama ve ilerleme gibi toplu, yüksek riskli ürün içi veri
   taşıyan geçici artefaktlardır. Yalnız `READY` durumunda indirilebilir, süre sonunda fiziksel
   olarak temizlenir ve kalıcı URL alamaz.
3. Doğrulanmamış yüklemeler güvenilir ürün varlığı değildir. Boyut, tür ve içerik doğrulaması
   tamamlanmadan aktif dosya olarak bağlanamaz.

`KISISEL_VERI_ENVANTERI.md` profil fotoğrafını yüksek riskli ürün içi veri, Excel raporunu ise
toplu dışa aktarım nedeniyle daha yüksek riskli bir artefakt olarak değerlendirir. Ana ürün
planı PDF'lerin kalıcı herkese açık URL ile sunulmasını yasaklar; dosya yedekleme/sürümleme veya
uygun geri yükleme politikası ister.

`VERI_MODELI.md` §10.1'de `file_assets.organization_id` zorunludur; kişi fotoğrafı, logo ve
içerik PDF'si aynı kurum içindeki kayda bileşik FK ile bağlanır. Depolama servisi bu DB
bütünlüğünün yerine geçmez. `EXCEL_RAPOR_VERI_SOZLESMESI.md` ise rapor nesnesinin
`export_jobs.artifact_storage_key` üzerinden ayrı ve süreli bir yaşam döngüsü taşımasını
zorunlu kılar.

## 3. Karar sürücüleri

| Ölçüt | Neden gerekli |
|---|---|
| Özel erişim ve kurum izolasyonu | Bir kurum başka kurumun PDF, fotoğraf, logo veya raporunu nesne kimliğiyle deneyerek görememelidir. |
| Yanlış silmeye karşı kurtarma | Kalıcı varlıklarda uygulama hatası veya operatör yanlışından geri dönüş mümkün olmalıdır. |
| Yaşam döngüsü otomasyonu | Geçici yükleme ve Excel artefaktı süre sonunda kısmi/unutulmuş nesne bırakmadan temizlenmelidir. |
| Yükleme güvenliği | MIME iddiası tek başına kabul edilmemeli; boyut, uzantı, imza ve zararlı içerik denetlenmelidir. |
| Bölge ve şifreleme | Kişisel veri içeren nesneler Frankfurt'ta, TLS ile ve beklemede şifreli tutulmalıdır. |
| Taşınabilirlik | Backend, sağlayıcı SDK'sını alan katmanına sızdırmadan S3 uyumlu bir port üzerinden çalışmalıdır. |
| Operasyon ve maliyet | V1 hacminde düşük sabit maliyet, ölçülebilir istek/egress ve test edilebilir geri yükleme sunmalıdır. |

## 4. İncelenen seçenekler ve maliyet

| Seçenek | Güçlü yanlar | Sınırlamalar |
|---|---|---|
| Amazon S3 (`eu-central-1`) | Kesin Frankfurt bölgesi; Block Public Access; IAM/KMS; sürümleme, lifecycle ve replikasyon; olgun Java SDK | Ayrı AWS hesap/IAM/KMS işletimi; internet egress ve istek maliyeti; Supabase kotasından ayrı fatura |
| Supabase Storage | A-003 ile seçilen projelerde 100 GB Pro kotası; aynı proje/bölge; özel bucket, RLS ve S3 uyumlu erişim; düşük ek işletim | S3 versioning ve bucket lifecycle API'si desteklenmez; silinen nesne kalıcı gider; Supabase DB yedeği Storage nesnelerini içermez; signed URL süre dolmadan doğrudan iptal edilemez |
| Cloudflare R2 (`eu` jurisdiction) | S3 uyumlu API, AB içinde yerleşim kısıtı, düşük depolama ve ücretsiz internet egress; lifecycle ve süreli URL desteği | Ayrı sağlayıcı; konum ipucu tek başına garanti değildir, `eu` jurisdiction açık kurulmalıdır; yerleşik S3 versioning yerine ayrı yedek gerekir |
| PostgreSQL/BLOB veya backend yerel diski | Tek veri/dağıtım yüzeyi | DB yedeğini şişirir; CDN/akış/lifecycle zayıf; geçici instance diski kalıcı değildir; yatay ölçek ve geri yükleme zorlaşır |

Karşılaştırma 15 Temmuz 2026 tarihinde sağlayıcıların resmî belgeleri ve AWS Price List API
değerleriyle yapılmıştır. Fiyatlar vergi, kur dönüşümü, loglama, malware tarama compute'u ve
beklenmeyen operasyonları içermez; OPS-005 provisioning öncesi aynı hesap yeniden çalıştırılır.

### 4.1. Ortak örnek yük ve fiyat varsayımları

Üç aday aynı aylık production yüküyle karşılaştırılır:

- 20 GB güncel kalıcı PDF/logo/fotoğraf,
- 20 GB eski/noncurrent sürüm veya sağlayıcı versioning sunmuyorsa immutable eski nesne,
- 5 GB geçici Excel raporu,
- aylık 10.000 yazma/kopyalama ve 100.000 okuma,
- mobil istemcilere 50 GB internet çıkışı,
- geçici raporlar yeniden üretilebilir olduğundan hariç tutularak 40 GB kalıcı + eski sürümün
  ayrı, versioned ve runtime tarafından silinemeyen S3 yedeği.

Hesapta kullanılan birim fiyatlar:

- S3 Frankfurt Standard ilk 50 TB: `$0,0245/GB-ay`; PUT/COPY/POST/LIST `$0,0054/1.000`, GET
  ve diğer okumalar `$0,0043/10.000`.
- AWS KMS Frankfurt: müşteri yönetimli anahtar başına `$1/ay`, simetrik istek başına `$0,000003`
  (`$0,03/10.000`) ve hesap başına aylık 20.000 uygun istek ücretsizdir. Primary için 110.000
  istek production hesabına, yedek kopyası için yaklaşık 20.000 istek ayrı backup hesabına
  faturalandırılır; her hesap kendi 20.000 kotasını uygular. Böylece primary KMS brüt `$1,33`,
  kota sonrası `$1,27`; backup KMS brüt `$1,06`, kota sonrası `$1,00`dır. S3 Bucket Key indirimi
  hesaba katılmaz. Replikasyon istekleri fiilen production hesabına yazılırsa backup kotası
  kullanılamaz ve toplam KMS'e `$0,06` eklenir; OPS-005 Cost Explorer ile atfı doğrular.
- AWS'nin ilk 100 GB/ay ortak internet çıkış kotasının başka hizmetçe tüketilmediği varsayımıyla
  S3'ün 50 GB egress'i `$0`dır. Kota yoksa yaklaşık `$0,09/GB` ile **ek `$4,50`** oluşur.
- Supabase Pro, mevcut A-003 planında 100 GB dosya ve 250 GB egress içerir; bu örnekte storage,
  istek ve egress marjinali `$0`dır. Storage tek başına alınsaydı Pro tabanı `$25/ay` olurdu.
- R2 Standard: `$0,015/GB-ay`, Class A `$4,50/milyon`, Class B `$0,36/milyon`, internet egress
  `$0`; aylık 10 GB, 1 milyon Class A ve 10 milyon Class B kotası bu örnekte uygulanır.
- Her adayda kurtarılabilirlik için aynı 40 GB cross-account S3 yedeği kullanılır. Ücretsiz KMS
  kotası sonrası yedek `$0,98` depolama + `$0,054` kopya isteği + `$1,00` KMS = **`$2,034`**;
  KMS kotası önceden tüketilmiş brüt senaryoda **`$2,094`** olur. Böylece Supabase/R2'nin düşük
  fiyatı yedeksiz bir kurulumla yapay olarak iyileştirilmez.

### 4.2. Production örnek yükü + production backup aylık maliyet tablosu

Bu tablo yalnız §4.1'deki production örnek yükünü ve production backup kopyasını karşılaştırır.
Development/staging depolama ve istekleri burada adaylara dağıtılmaz; yalnız kritik-production
referansı olan dört hesaplı S3 topolojisinin alt ortam KMS tabanı §4.3'te ayrıca gösterilir.

| Maliyet kalemi (USD/ay) | Amazon S3 | Supabase Storage | Cloudflare R2 |
|---|---:|---:|---:|
| Güncel kalıcı 20 GB | 0,49 | 0,00 — Pro kotasında | 0,30 brüt |
| Eski sürüm 20 GB | 0,49 | 0,00 — immutable nesne, Pro kotasında | 0,30 brüt |
| Geçici rapor 5 GB | 0,12 | 0,00 — Pro kotasında | 0,08 brüt |
| 10.000 yazma | 0,05 | 0,00 | 0,05 brüt |
| 100.000 okuma | 0,04 | 0,00 | 0,04 brüt |
| R2 depolama/istek kotası kredisi | 0,00 | 0,00 | −0,23 — 10 GB + istek kotası |
| 50 GB internet egress | 0,00* | 0,00 — 250 GB kotasında | 0,00 |
| Primary KMS — anahtar + brüt istek | 1,33 | 0,00 — sağlayıcı yönetimli | 0,00 — sağlayıcı yönetimli |
| Primary KMS ücretsiz istek kredisi | −0,06 | 0,00 | 0,00 |
| Yedek depolama 40 GB | 0,98 | 0,98 | 0,98 |
| Yedek kopya isteği | 0,05 | 0,05 | 0,05 |
| Yedek KMS — anahtar + brüt istek | 1,06 | 1,06 | 1,06 |
| Yedek KMS ücretsiz istek kredisi | −0,06 | −0,06 | −0,06 |
| **Ücretsiz kotalar sonrası marjinal toplam** | **4,50*** | **2,03** | **2,56** |
| Bağımsız hizmet tabanı dahil | **4,50*** | **27,03** (`$25` Pro dahil) | **2,56** |

Tablo yalnız belirtilen ücretsiz kotalar uygulandıktan sonraki senaryodur; brüt değerlerle
karıştırılmaz. İki AWS hesabının KMS istek kotası da önceden tüketilmişse egress hariç toplamlar
sırasıyla **S3 `$4,62`**, **Supabase `$2,09`**, **R2 `$2,62`** olur. `*` Buna ek olarak S3
hesabının ortak 100 GB çıkış kotası tüketilmişse S3 yaklaşık **`$9,12`**ye çıkar. R2'nin kendi
ücretsiz depolama/istek kotası ile iki KMS kotası birlikte tüketilmişse R2 yaklaşık **`$2,85`**
olur. Supabase marjinali A-003 nedeniyle Pro planının zaten ödendiği proje gerçeğini gösterir;
bağımsız karşılaştırmada `$25` tabanı saklanmamıştır. Log, tarama compute'u ve vergiler ayrıca
bütçelenir.

### 4.3. Kritik-production dört hesaplı S3 topolojisinin bilinen tabanı

Production örnek yükü + backup için kota sonrası S3 toplamı `$4,5035`tir. Development ve
staging hesaplarının her birinde zorunlu ayrı müşteri yönetimli KMS anahtarı `$1/ay` olduğundan,
henüz sentetik storage/istek yükü eklenmeden kritik-production referansı olan dört hesaplı AWS
depolama tabanı:

| Bilinen bileşen | USD/ay |
|---|---:|
| Production örnek yükü + production backup | 4,50 |
| Development KMS anahtarı | 1,00 |
| Staging KMS anahtarı | 1,00 |
| **Dört hesaplı depolama tabanı** | **yaklaşık 6,50 + development/staging storage/istek** |
| A-010 bilinen platform tabanı | 214,00 |
| **Birleşik bilinen platform tabanı** | **yaklaşık 220,50 + diğer bilinmeyenler** |

“Diğer bilinmeyenler”; development/staging S3 storage/istekleri, egress aşımı, artifact
registry/retention, malware tarama compute'u, log/izleme, veritabanı mantıksal yedeği, restore
tatbikatı geçici kaynakları, vergi ve kur dönüşümüdür; sıfır kabul edilmez.

Taban dört müşteri yönetimli KMS anahtarının ilk key material sürümünü içerir. AWS KMS'te
otomatik veya isteğe bağlı ilk rotation, döndürülen **her anahtara** `$1/ay`; ikinci rotation
bir `$1/ay` daha ekler ve artış ikinci rotation'da durur. Dört anahtarın tamamı döndürülürse ilk
rotation sonrası ek `$4/ay` ile bilinen toplamlar yaklaşık `$10,50` / `$224,50`; ikinci rotation
sonrası bir ek `$4/ay` ile `$14,50` / `$228,50` olur. Rotation güvenlik kararı OPS-005'te
uygulanırsa bu güncel kademe bütçe alarmına ayrıca yazılır.

### 4.4. Simetrik ağırlıklı karar matrisi

Puan `1` (zayıf)–`5` (çok güçlü), ağırlık toplamı 100'dür. Operasyonel karmaşıklıkta yüksek
puan daha az ve daha yönetilebilir iş yükü anlamına gelir. Maliyet puanı yukarıdaki **marjinal**
toplamı, kota aşımı görünürlüğünü ve zorunlu yedeği birlikte değerlendirir.

| Ölçüt | Ağırlık | Amazon S3 | Supabase Storage | Cloudflare R2 |
|---|---:|---:|---:|---:|
| Özel erişim, IAM/KMS ve tenant savunması | 25 | 5 | 4 | 4 |
| Versioning, yedek ve geri yüklenebilirlik | 25 | 5 | 2 | 3 |
| Operasyonel karmaşıklık | 20 | 2 | 5 | 4 |
| Maliyet ve kota öngörülebilirliği | 15 | 3 | 4 | 5 |
| Frankfurt/AB veri yerleşimi | 10 | 5 | 5 | 4 |
| S3 taşınabilirliği ve Java entegrasyonu | 5 | 5 | 3 | 4 |
| **Ağırlıklı toplam / 500** | **100** | **410** | **375** | **390** |

Supabase operasyon ve mevcut plan marjinalinde, R2 maliyet/egress'te öndedir. Buna karşılık S3;
kalıcı öğrenci fotoğrafları ve içeriklerde versioning, ayrı IAM/KMS sınırı ve test edilebilir
cross-account yedekle en yüksek güvenlik/geri yüklenebilirlik puanını alır. **Amazon S3 Standard
`eu-central-1` referansı, production+backup `$4,50` ve dört hesaplı bilinen `$6,50` tabanla
matris yeniden çalıştırıldığında 410/500 sonuç vermiştir.** Bu sonuç başlangıçta dört hesabın
hemen kurulmasını değil, S3'ün kritik-production güvenlik referansı olarak korunmasını sağlar.
Dört hesaplı topoloji S3'ün
operasyon puanını `3`ten `2`ye düşürmüş; `$2` alt ortam KMS artışı mutlak V1 bütçesinde maliyet
puanını `3`te tutmuştur. Maliyet puanı ayrıca bir kademe `2`ye indirilseydi S3 toplamı
`395/500` olur ve R2'nin `390/500` sonucunu yine geçerdi. İlk 100 GB egress kotası
tüketildiğinde yaklaşık `$4,50` eklenebileceğinden OPS-005 bütçe alarmı ve aynı yükle yeniden
hesaplama zorunludur.

## 5. Bağlayıcı teknik sınırlar

### 5.1. Ortam ve bucket ayrımı

Bu bölüm S3 seçildiğinde uygulanacak referans profildir. Kapalı alfa dosya yüklemiyorsa kaynak
oluşturulmaz; R2 seçilirse eşdeğer olmayan versioning, backup ve imha maddeleri A-007R ile
yeniden karara bağlanmadan bu bölümün karşılandığı iddia edilemez.

- Development, staging ve production `eu-central-1` bölgesinde ayrı AWS hesaplarındadır;
  production backup dördüncü, yalnız yedek/restore amaçlı AWS hesabındadır. Production nesnesi
  development/staging'e kopyalanmaz; backup hedefi uygulama ortamı veya genel dosya deposu
  olarak kullanılamaz.
- Her uygulama hesabının ayrı bucket'ları, IAM rolleri ve KMS anahtarı vardır. Hesaplar arasında
  kalıtsal runtime yetkisi, ortak bucket, ortak KMS key policy veya ortak kalıcı access key
  yoktur. Production/non-production ve source/backup erişimi resource policy, role trust ve KMS
  key policy katmanlarının her birinde varsayılan reddir.
- En az üç yaşam döngüsü ayrılır: `assets` (PDF/logo/fotoğraf), `exports` (geçici XLSX) ve
  `quarantine` (doğrulanmamış yükleme). Bucket adları ve hesap kimlikleri API yanıtına yazılmaz.
- Bütün bucket'larda hesap ve bucket düzeyinde **S3 Block Public Access** açık, ACL kullanımı
  kapalı ve yalnız TLS erişimi zorunludur. Public bucket, public object ACL veya wildcard
  principal yasaktır.
- Her hesabın nesneleri o hesaba özel müşteri yönetimli KMS anahtarıyla SSE-KMS kullanır.
  Production key policy yalnız production workload/provisioning rollerini; backup key policy
  yalnız dar replication/restore ve backup provisioning rollerini kabul eder. Runtime,
  finalizer, purger, reconciler ve disposal rolleri backup key/bucket yönetemez veya backup
  nesnesi silemez; backup rolü production key policy/bucket yönetemez. Key deletion ve policy
  değişikliği bütün runtime rollerine kapalıdır. OPS-005 bu topolojiyi IaC ve negatif testlerle
  uygular; A-013 yalnız workload identity/secret referanslarını taşır.

### 5.2. Nesne anahtarı ve metaveri

- Kalıcı nesne anahtarı sunucunun ürettiği en az 128-bit rastgele opak kimliktir. Kurum adı,
  `organization_id`, kullanıcı/öğrenci kimliği, e-posta, telefon, özgün dosya adı veya içerik
  başlığı anahtara yazılmaz.
- Anahtarın terminal parçası tam 32 küçük harfli hexadecimal karakterdir; `assets/`, `exports/`
  veya `quarantine/` sınıfından sonra ek suffix kabul edilmez. Böylece geçerli hiçbir anahtar
  başka bir geçerli tam anahtarın devamı olamaz ve §5.4'teki exact-key doğrulama prefix'i komşu
  nesne ad alanına genişlemez.
- İstemci depolama anahtarı gönderemez ve `storage_key` ile sorgu yapamaz. API yalnız ürün
  kaydı/asset kimliğini kabul eder; backend önce DB'deki kurum ve nesne ilişkisini doğrular,
  sonra depolama portunu çağırır.
- Özgün dosya adı yalnız gösterim metaverisidir; yol oluşturmaz. Kontrol karakterleri ve path
  ayraçları temizlenir, loglara veya URL'ye yazılmaz. İndirmede güvenli `Content-Disposition`
  başlığı backend tarafından üretilir.
- `Content-Type`, boyut ve SHA-256 checksum güvenilir sunucu doğrulamasından sonra kaydedilir.
  S3 `ETag`i multipart yüklemede içerik checksum'ı kabul edilmez.
- Aynı anahtarın üzerine yazılmaz. Yeni dosya yeni anahtar ve yeni varlık üretir; iş kaydı
  ilişkisini atomik olarak yeni varlığa geçirir. Eski nesnenin yaşam döngüsü ayrıca yürür.

### 5.3. Yükleme ve doğrulama

1. Mobil istemci ürün kaydı bağlamında yükleme niyeti oluşturur. Backend kimlik, etkin kurum,
   işlem izni ve hedef kaydın kurum/sınıf kapsamını doğrular.
2. Backend platform API alan adında, yalnız tek yükleme oturumuna bağlı kısa ömürlü upload
   yüzeyi üretir. İstemci akışı bu API'ye gönderir; backend izinli `Content-Type`, checksum ve
   yapılandırılmış boyut sınırıyla opak `quarantine` anahtarına aktarır. İstemci S3'e
   yönlendirilmez; kalıcı AWS anahtarı/JWT/service credential, bucket veya nesne anahtarı mobil
   uygulamaya verilmez.
3. Yükleme tamamlandı bildirimi idempotent bir finalize komutudur. Backend/işçi nesne boyutunu,
   beyan edilen checksum'ı, dosya uzantısını, gerçek magic-byte türünü ve ayrıştırılabilirliğini
   doğrular; zararlı içerik taramasını tamamlar.
4. PDF yalnız izinli PDF imzası ve MIME ile; logo/fotoğraf yalnız CONTENT/ORG sözleşmesinin
   izin verdiği raster türlerle kabul edilir. Parolalı/şifreli veya güvenli taranamayan PDF,
   SVG/HTML/çalıştırılabilir içerik ve türü belirsiz dosya varsayılan olarak reddedilir.
   Görseller decode edilip güvenli biçimde yeniden kodlanır; EXIF/konum gibi gereksiz metaveri
   kaldırılır.
5. Doğrulama geçmeden `file_assets` kaydı veya ürün ilişkisi oluşturulmaz. Başarılı dosya
   yeni, opak `assets` anahtarına kopyalanır; checksum tekrar doğrulanır; DB kaydı ve iş
   ilişkisi tamamlandıktan sonra quarantine nesnesi temizlenir.
6. Başarısız/yetkisiz/yarım yükleme ürün tarafından başarı gösterilmez. Quarantine nesneleri
   kısa lifecycle kuralıyla temizlenir; kesin süre CONTENT-001/ORG-002 sözleşmesindeki yükleme
   oturumu davranışıyla ayrı nesne depolama provisioning kontrolünde belirlenir.

Bucket kısıtları savunma katmanıdır; yalnız istemcinin `Content-Type` iddiasına veya dosya
uzantısına güvenilmez. Kesin PDF/görsel boyutu, sayfa/piksel sınırı ve tarama motoru CONTENT-001,
ORG-002 ve CONTENT-003 kapsamında ölçülebilir kabul ölçütüyle seçilir; bu ADR değer uydurmaz.

### 5.4. Bağlayıcı orphan reconciliation modeli

**Karar:** Orphan tespiti için S3 Inventory veya bucket/prefix taraması değil, her uzak işlemden
önce oluşturulan **durable upload/finalize işlem kaydı** kullanılır. S3 Inventory gecikmeli bir
envanterdir ve V1 doğruluk kaynağı değildir. Yokluk kanıtının tek bağlayıcı AWS yüzeyi, normal
runtime'dan kopuk kısa ömürlü `storage_version_verifier` eşdeğeri roldür. Bu rol durable
operation/cleanup/manifest item'ındaki tek exact key için `ListObjectVersions` çağırır;
`HeadObject` sonucu yokluk kanıtı değildir.

Verifier session'ı yalnız imzalı broker tarafından, kayıtlı hesap + bucket + terminal formata
uyan full key ile üretilir. Role ve session policy birlikte yalnız ilgili bucket üzerinde
`s3:ListBucketVersions` verir; `s3:prefix` **StringEquals exact full key** ve her sayfada
`s3:max-keys <= 100` koşulları zorunludur. `s3:ListBucket`, başka bucket/key, daha kısa veya
wildcard prefix, koşulsuz listeleme ve object-data işlemleri reddedilir. Sonraki
`key-marker/version-id-marker` yalnız önceki başarılı sayfanın durable kaydedilmiş continuation
değerlerinden alınır. Bu dar çağrı bucket keşfi veya prefix taraması değildir: §5.2 terminal
anahtar grameri komşu geçerli key'i engeller, uygulama yalnız exact key satırlarını kabul eder
ve beklenmeyen key dönerse fail-closed `NEEDS_ATTENTION` üretir.

Bağlayıcı doğrulama sonucu şöyledir:

- `VERSION_PRESENT`: tam sayfalanmış başarılı `2xx` yanıtta exact key + hedef version ID vardır.
- `VERSION_ABSENT`: bütün sayfalar başarıyla tüketilmiş, yanıt exact key dışına çıkmamış ve hedef
  version ID hiçbir version/delete-marker satırında yoktur.
- `KEY_ABSENT`: aynı tam tarama exact key için hiçbir version veya delete marker döndürmez.
- Truncated akış tamamlanmamışsa; `403`, `404`, timeout, access/owner/policy uyuşmazlığı veya
  beklenmeyen satır varsa sonuç `UNKNOWN/NEEDS_ATTENTION`dır. Özellikle `403` hiçbir durumda
  “nesne yok” sayılmaz.

`storage_runtime`, writer, reconciler, finalizer, purger ve iki disposal rolü geniş
`s3:ListBucket`/`s3:ListBucketVersions` alamaz; verifier bu yasağın yalnız exact-key ve koşullu
istisnasıdır. Pozitif `HeadObject(versionId)` `2xx` sonucu metadata/checksum doğrulayabilir;
onun `403` veya `404` sonucu yokluk postcondition'ı oluşturamaz.

CONTENT-001 sözleşmesi ve CONTENT-003 uygulaması, adı uygulama görevinde kesinleşecek
`storage_object_operations` eşdeğeri kurum kapsamlı kaydı şu asgari bilgilerle sağlar:

| Alan | Zorunlu anlam |
|---|---|
| `operation_id`, `organization_id`, `actor_user_id` | İşlem ve doğrulanmış tenant/aktör kapsamı |
| `idempotency_key`, `request_hash` | Aynı niyetin tek işlem olması ve farklı gövdeyle tekrarın reddi |
| `object_kind`, `quarantine_key`, `final_key` | Yalnız sunucunun ürettiği opak, kesin nesne anahtarları |
| `expected_size`, `expected_checksum`, `observed_version_id` | Boyut/içerik doğrulaması ve exact-version imha/restore izi |
| `state`, `row_version`, `attempt_count`, `next_attempt_at` | CAS durum geçişi ve kalıcı yeniden deneme |
| `linked_asset_id` veya `export_job_id` | Başarılı DB ilişkisinin tek kaynağı |
| `created_at`, `updated_at`, `completed_at`, `last_failure_code` | Süre aşımı, operasyon gözlemi ve güvenli hata |

Durum akışı en az `RESERVED → QUARANTINE_STORED → VALIDATED → FINAL_STORED → LINKED →
COMPLETED` ilerler; yeniden denenebilir hata aynı durumda kalır, kalıcı red `CLEANUP_PENDING →
FAILED` olur. Her S3 `PUT`/`COPY` çağrısından **önce** işlem satırı ve kullanılacak kesin anahtar
commit edilir. Bu nedenle DB ilişkisi olmayan fakat S3'e yazılmış bir nesne dahi kayıtsız
değildir; gecikmiş nonterminal işlem üzerinden bulunur.

Reconciliation worker yalnız DB'de eşik süresini aşmış nonterminal satırları sorgular ve o
satırdaki kesin anahtar için verifier sonucu ister:

1. `KEY_ABSENT` ise güvenli yeniden deneme veya süresi dolmuş yükleme kapanışı yapılır.
2. Exact quarantine version adayı varsa pozitif boyut/checksum doğrulanır ve aynı `final_key`
   ile finalize sürdürülür.
3. `FINAL_STORED` nesnesi var fakat `file_assets`/`export_jobs` bağı yoksa güncel kurum/yetki,
   checksum ve hedef kaydın hâlâ geçerli olduğu doğrulanarak ilişki tamamlanır; koşul geçersizse
   exact-key cleanup kaydı üretilir. Belirsizlikte nesne silinmez, `NEEDS_ATTENTION` olur.
4. DB bağı mevcutsa işlem `LINKED/COMPLETED` yapılır; aynı nesne tekrar kopyalanmaz.
5. Cleanup öncesi hem işlem satırı hem `file_assets`/`export_jobs` referansları yeniden kontrol
   edilir. Yalnız exact key/version silinir; bucket/prefix taraması veya toplu silme yoktur.

İşlem anahtarı `(organization_id, actor_user_id, idempotency_key)` kapsamında tektir ve
`request_hash` ile korunur. Aynı anahtar/eşdeğer istek aynı `operation_id` ve durumu döndürür;
farklı istek `409 IDEMPOTENCY_KEY_REUSED` olur. `PUT`/`COPY` başarı yanıtı kaybolursa retry yeni
anahtar üretmez; verifier exact key'deki aday version'ları bulur, adayın pozitif
`HeadObject(versionId)` metadata/checksum doğrulamasından sonra gözlenen version ID'yi durable
kayda yazar. `KEY_ABSENT` güvenli yeniden denemeye, birden çok eşleşen/uyuşmayan version ise
`NEEDS_ATTENTION`a gider. Durum güncellemesi `row_version` karşılaştırmasıyla yapılır; iki worker
aynı finalize veya cleanup'ı çift uygulayamaz.

Her destructive cleanup/purge/disposal için ilk çağrıdan önce aynı exact key/version
`VERSION_PRESENT` olmalı ve bu precondition ile `DELETE_REQUESTED`/attempt kimliği ağ çağrısından
önce commit edilmelidir. Doğrulanmış AWS delete `2xx` yanıtından sonra da
`VERSION_ABSENT` alınmadan başarı yazılmaz. Delete yanıtı kaybolursa ancak aynı durable
precondition + attempt için `VERSION_ABSENT` güçlü postcondition'ı başarıdır; hedef hâlâ
`VERSION_PRESENT` ise aynı idempotency kimliğiyle retry edilir. Önceden varlığı kanıtlanmamış
yanlış version ID'nin zaten absent olması silme başarısı sayılamaz. Referans, retention ve hold
kontrolleri her denemede yenilenir.

#### Rol ve silme sınırları

| Rol | İzin verilen dar yüzey | Kesin yasak |
|---|---|---|
| `storage_runtime` | DB'den çözülen exact asset/export için `GetObject`/pozitif metadata `HeadObject` | `PutObject`, listeleme, bucket/KMS yönetimi, durable asset/export silme |
| `storage_writer` | Durable satır commit edildikten sonra broker'ın verdiği kısa ömürlü session policy ile yalnız o işlemdeki exact quarantine key'e `PutObject` | Prefix/genel yazma, geniş/dar listeleme, okuma, kopyalama ve silme |
| `storage_reconciler` | DB durumunu ilerletme, exact-key verifier isteği ve dar action job üretme | Doğrudan geniş/dar listeleme, yazma, kopyalama, silme ve bucket/KMS yönetimi |
| `storage_finalizer` | Durable işlem satırındaki exact quarantine nesnesini okuma; exact final anahtara `CopyObject`/`PutObject`; başarı sonrası precondition'ı kanıtlı exact quarantine version silme | Doğrudan geniş/dar listeleme, bucket yönetimi, linked asset veya export silme |
| `storage_purger` | Durable cleanup/purge kaydındaki precondition'ı kanıtlı exact quarantine/export version'ı silme | Doğrudan geniş/dar listeleme, linked/active asset veya kayıtsız version silme |
| `storage_disposal` | Source hesabında, iki onaylı imha manifestindeki varlığı önceden kanıtlı exact source key/version ID için kısa ömürlü `DeleteObjectVersion` | Backup erişimi, sürekli runtime, geniş/dar listeleme, manifestsiz/prefix/toplu silme, retention/hold bypass |
| `storage_backup` | Backup hesabındaki dar replication/restore rolü olarak kaynak exact sürümü versioned hedefe kopyalama, durable eşlemeyi yazma ve onaylı restore manifestindeki exact sürümü okuma | Source/backup silme, geniş/dar listeleme, disposal/verifier rolünü assume etme, bucket/KMS policy yönetimi |
| `storage_backup_disposal` | Backup hesabında, iki onaylı imha manifestindeki varlığı önceden kanıtlı exact backup key/replica version ID için retention/hold okuma ve kısa ömürlü `DeleteObjectVersion` | Source erişimi, sürekli runtime, geniş/dar listeleme, manifestsiz/prefix/toplu silme, hold değiştirme veya governance retention bypass |
| `storage_version_verifier` | Durable tek operation/cleanup/manifest target için exact bucket+key'e koşullu `ListObjectVersions`; tüm sayfaları tüketip version/key varlık-yokluk sonucu yazma | `ListBucket`, koşulsuz/geniş prefix listeleme, başka key/bucket/tenant, object read/write/delete, KMS/bucket yönetimi ve disposal rolünü assume etme |
| `storage_provisioner` | IaC ile hesap/bucket/KMS policy, role trust, versioning, Object Lock/lifecycle ve alarm yapılandırması | Uygulama nesnesi okuma, `DeleteObject`/`DeleteObjectVersion`, disposal rollerini assume etme veya manifest yürütme |

`storage_disposal` sürekli uygulama credential'ı değildir; süreli operasyon kimliği, iki aşamalı
onay ve denetim olayı gerektirir. Genel runtime ve insan/admin kimliği `PutObject` alamaz.
Yazma broker'ı önce durable satırın commit edildiğini ve aktör/kurum kapsamını doğrular; sonra
yalnız kayıtlı quarantine key'ini kapsayan kısa ömürlü `storage_writer` STS session policy
üretir. Boyut ve checksum §5.3 akışında sunucu tarafından doğrulanır. Finalizer/purger da yalnız
durable job'daki exact key'lere scope edilmiş session kullanır. Böylece kayıt dışı veya
prefix-geneli nesne üretilemez. Verifier broker'ı da yalnız durable kaydın hesap/bucket/key/
version/tenant kapsamı için tek kullanımlık session üretir; bunu reconciler veya disposal rolüne
genel listeleme olarak devretmez. OPS-005 bu assume-role/session-policy negatiflerini,
runtime'ın geniş/dar listeleme reddini ve verifier'ın exact prefix/`max-keys` koşullarını
otomatik doğrular.

#### Onaylı source + backup fiziksel imha durumu

`storage_disposal` ile `storage_backup_disposal` birbirinden ve normal runtime'dan ayrı trust
policy'lere sahiptir; biri diğerini assume edemez. İkisi de sürekli credential değildir. Aynı
imha manifestinin değişmez özeti üzerinde, talep sahibinden ve birbirinden farklı iki yetkili
onay vermeden kısa ömürlü exact-resource session üretilemez. Provisioning, replication/restore,
runtime veya genel insan/admin rolüne object-version silme yetkisi verilmez.

Durable `storage_disposal_manifest_items` eşdeğeri kayıt en az şunları taşır:

- `manifest_id`, `manifest_hash`, `organization_id`, hukukî gerekçe/retention policy referansı,
  iki onaylayıcı ve onay zamanları,
- `source_key`, `source_version_id`, `backup_key`, `replica_version_id`, checksum ve replication
  doğrulama zamanı,
- source ve backup için ayrı `state`, `attempt_count`, `next_attempt_at`, son istek/verify zamanı
  ve hata kodu,
- idempotency key/request hash, audit correlation ID ve tamamlanma zamanı.

Source version ile replica version eşlemesi replication tamamlanırken durable kayda yazılır.
Managed S3 Replication kullanılırsa aynı key/version ve `x-amz-replication-status` exact
`HeadObject` ile doğrulanır; kontrollü copy kullanılırsa dönen destination version ID kaydedilir.
Bu `HeadObject` yalnız pozitif `2xx` metadata kanıtıdır. Eşit version ID varsayımıyla veya geniş
`ListBucket`/`ListBucketVersions` taramasıyla eşleme üretilmez. Kayıt eksik ya da checksum
uyuşmazsa imha başlamaz ve `NEEDS_ATTENTION` olur.

Manifest genel durumu `DRAFT → APPROVAL_PENDING → APPROVED → EXECUTING` ilerler. Her item için
source ve backup alt durumları bağımsız olarak `PENDING → DELETE_REQUESTED → DELETE_VERIFIED`
ilerler; aktif legal hold veya dolmamış retention varsa ilgili taraf `RETENTION_PENDING` olur.
Geçici/yanıtı belirsiz hata `RETRY_PENDING`, yetki/eşleme belirsizliği `NEEDS_ATTENTION` olur.
Manifest yalnız bütün item'larda hem source hem backup `DELETE_VERIFIED` ise `COMPLETED` olur;
aksi durumda genel durum `DISPOSAL_PENDING` kalır. Source silinmiş olsa bile retention nedeniyle
backup kopyası duruyorsa bütüncül fiziksel imha başarılı gösterilmez.

Her denemede source ve backup için `GetObjectRetention`/`GetObjectLegalHold` eşdeğeri kontrol
yenilenir. Rol legal hold kaldıramaz ve `BypassGovernanceRetention` alamaz. Silme yalnız manifestte
kayıtlı exact key + version ID ile yapılır; version ID'siz delete marker üretmek imha sayılmaz.
Her target önce verifier ile `VERSION_PRESENT` alır; sonra aynı
`(manifest_id, item_id, target, key, version_id)` idempotency kimliği ve `DELETE_REQUESTED`
durumu durable commit edilerek exact-version delete çağrılır. AWS'ye giden istek veya yanıt
kaybolsa da yalnız verifier'ın tam sayfalanmış `VERSION_ABSENT` sonucu `DELETE_VERIFIED`
üretir. Hedef `VERSION_PRESENT` ise retry; `403`, `404`, timeout, eksik sayfa veya beklenmeyen
key ise `NEEDS_ATTENTION`dır ve yokluk/başarı değildir. Ön-varlık kanıtı olmayan yanlış version
ID'nin absent sonucu da başarıya çevrilmez.
Source üzerindeki specific-version delete backup'a replike edilmediğinden iki taraf ayrı
silinir ve ayrı doğrulanır. Talep, iki onay, manifest özeti, hold/retention sonucu, role session,
delete denemesi, uzlaştırma ve nihai durum değişmez audit olayları üretir.

#### Zorunlu reconciliation testleri

1. `storage_runtime` ile `PutObject`, `ListBucket`, `ListBucketVersions`, asset `DeleteObject`
   ve bucket/KMS yönetimi `AccessDenied` olur; exact yetkili pozitif `HeadObject/GetObject`
   çalışır fakat `HeadObject` `403/404` sonucu yokluk sayılmaz.
2. `PUT` başarılı olup DB durum güncellemesi öncesi worker öldürülür; reconciler aynı kayıtlı
   anahtar için verifier'dan version adayını alır, pozitif metadata/checksum ile eşleştirir,
   aynı operation/final key ile tamamlar ve ikinci nesne üretmez.
3. Final copy başarılı olup DB link transaction'ı öncesi worker öldürülür; reconciler güncel
   yetki/checksum ile bağı tamamlar veya koşul geçersizse exact cleanup üretir; belirsizlikte
   silmez.
4. Aynı idempotency key/eşdeğer payload paralel gönderildiğinde tek operation/nesne oluşur;
   farklı payload `409 IDEMPOTENCY_KEY_REUSED` alır.
5. Cleanup retry'ında başka operation'ın anahtarı, linked asset, aktif export veya başka kurum
   nesnesi silinemez.
6. Durable işlem kaydı olmadan `storage_writer` session alınamaz ve runtime/admin doğrudan
   `PutObject` yapamaz; geçerli session başka key'e yazamaz, yanlış checksum/boyut finalize'da
   reddedilir ve oluşan key/version ID'si kayıtla eşleşir.
7. Kalıcı asset sürümü yalnız onaylı `storage_disposal` manifestiyle silinebilir; runtime,
   finalizer ve purger aynı version ID için reddedilir.
8. `storage_backup`, runtime ve provisioner backup `DeleteObject`/`DeleteObjectVersion` yapamaz;
   yalnız iki onaylı manifest için exact session alan `storage_backup_disposal` yapabilir.
9. Manifestte kayıtlı source/replica eşlemesi yoksa veya backup key/version farklıysa iki silme
   de başlamaz; roller geniş listeleme ile hedef arayamaz ve verifier başka key için session
   üretemez.
10. Source veya backup delete yanıtı kaybolduğunda, önceki `VERSION_PRESENT` ve durable attempt
    aynı target için varsa tam sayfalanmış `VERSION_ABSENT` ayrı uzlaştırılır; ikinci delete
    audit'i veya yanlış başarı oluşmaz.
11. Backup legal hold veya retention silmeyi engellerse source silinmiş olsa dahi item
    `RETENTION_PENDING`, manifest `DISPOSAL_PENDING` kalır; retention sonrasında aynı idempotency
    kimliğiyle devam eder.
12. Bütün item'ların source ve backup version yokluğu doğrulanmadan manifest `COMPLETED` olamaz.
13. Kayıp delete yanıtından sonra verifier hedef version'ı hâlâ görürse target
    `RETRY_PENDING` kalır; yokmuş gibi `DELETE_VERIFIED` olmaz.
14. Önce varlığı kanıtlanan version gerçekten silinmişse exact-key tüm sayfaların başarılı
    tüketimiyle `VERSION_ABSENT` alınır ve yalnız o target `DELETE_VERIFIED` olur.
15. Manifestte yanlış/hiç var olmamış version ID varsa pre-delete `VERSION_PRESENT` alınamaz;
    sonradan alınan absent sonucu silme başarısı sayılmaz ve item `NEEDS_ATTENTION` olur.
16. Verifier yetkisi/policy/owner kayması `403` üretirse, pozitif metadata kontrolünde KMS
    yetki kayması veya `HeadObject` `403/404` görülürse ya da pagination tamamlanamazsa yokluk
    değil `NEEDS_ATTENTION` üretilir.
17. Başka kurum/key, daha kısa/wildcard prefix, başka bucket, `max-keys > 100` veya prefixesiz
    istek broker/IAM tarafından reddedilir; yanıtta exact key dışında satır görülürse fail-closed
    alarm üretilir.
18. Final copy yanıtı kaybolduğunda exact final key `KEY_ABSENT` ise aynı operation ile copy
    yeniden denenir; eşleşen version bulunursa pozitif checksum doğrulanıp link sürdürülür;
    `403/404` veya birden çok belirsiz aday başarı sayılmaz.
19. Quarantine cleanup veya export purge delete yanıtı kaybolduğunda target version mevcutsa
    retry edilir, ön-varlık+attempt kanıtlı target gerçekten yoksa doğrulanır; bu postcondition
    alınmadan cleanup tamamlanmış veya `purged_at` yazılmış gösterilmez.

### 5.5. İndirme ve yetkilendirme

- Bütün V1 nesneleri özeldir. Kurum logosu da public bucket istisnası yaratmaz; gelecekte
  herkese açık marka yüzeyi gerekirse ayrı ADR ile değerlendirilir.
- İndirme isteği asset/export iş kimliğiyle yapılır. Backend her istekte kimlik doğrulama,
  etkin üyelik, rol/izin, kurum ve gerekiyorsa sınıf atamasını doğrular. Başka kurum/sınıf
  nesnesi `404 RESOURCE_NOT_FOUND` ile gizlenir.
- Backend yalnız tek nesne ve tek kullanıcı/oturum amacına bağlı, yüksek entropili uygulama
  indirme tokenı üretir. DB'de ham token değil HMAC özeti, hedef nesne, kullanıcı/kurum bağlamı,
  oluşturulma, sona erme ve iptal bilgisi tutulur. Sayısal token ömrü ilgili API sözleşmesinde
  belirlenir; hassas raporda kısa olmak zorundadır.
- Dönen URL platform API alan adındadır ve kalıcı S3 anahtarı, bucket, AWS hesap kimliği,
  `organization_id` veya özgün dosya adını içermez. S3 ön-imzalı GET URL'sine yönlendirme
  yapılmaz.
- Token tek başına yetki değildir. İndirme çağrısı TLS yanında geçerli platform oturumu taşır;
  backend token bağlamını oturumla eşleştirir ve güncel yetkiyi tekrar denetler. Yetki iptali,
  token iptali, dosya arşivi/purge veya süre sonu indirmeyi durdurur.
- Backend nesneyi bellek/diskte bütünüyle tamponlamadan sınırlandırılmış akışla S3'ten iletir.
  `Content-Type`, `Content-Length`, güvenli `Content-Disposition`, `X-Content-Type-Options:
  nosniff` ve hassas içerik için `Cache-Control: private, no-store` başlıklarını kontrol eder.
  Range desteği eklenirse yalnız yetkili token ve güvenli boyut sınırıyla uygulanır.
- Oluşturma, finalize, ilişkilendirme, değiştirme, arşivleme/purge, indirme tokenı üretme ve
  reddedilen kurum/yetki denemeleri uygun güvenli denetim/operasyon olaylarını üretir. Token,
  kalıcı anahtar, ön-imza, özgün hassas dosya adı veya dosya içeriği audit/loga yazılmaz.

Excel indirmesi ayrıca `EXCEL_RAPOR_VERI_SOZLESMESI.md` §3–§5'teki iş sahibi, rol, sınıf,
`REPORT_EXPORT`, `GUARDIAN_CONTACT_VIEW`, `READY`, `expiresAt` ve indirme anı yeniden yetki
kurallarını aynen uygular. `QUEUED`, `RUNNING`, `FAILED` veya `EXPIRED` iş token alamaz.

### 5.6. Arşivleme, sürümleme, silme ve yedek

- `assets`, `exports` ve `quarantine` bucket'larında S3 Versioning zorunludur ve her başarılı
  yazımın version ID'si durable kayda alınır. Uygulama aynı anahtara yazmasa da versioning,
  operatör veya entegrasyon hatasıyla overwrite/delete durumunda kurtarma katmanıdır; cleanup,
  purge ve kayıp yanıt uzlaştırmasının exact-version sınırını da sağlar.
- Normal ürün silmesi fiziksel S3 delete değildir: ürün ilişkisi arşivlenir, nesne yeni
  indirme tokenı alamaz ve geçmiş/audit ilişkisi korunur. Paylaşılan asset başka aktif kayda
  bağlıysa temizlenemez.
- Kalıcı fiziksel silme yalnız onaylı kişisel veri imha prosedürü veya doğrulanmış sahipsiz
  nesne temizliğiyle yapılır. Versioned bucket'ta delete marker tek başına imha sayılmaz;
  bütün source sürümleri, replica version eşlemeleri ve ilgili yedekler hukukî politikaya göre
  ele alınır. Specific source version silme replica silmesini tetiklemediğinden §5.4'teki iki
  ayrı disposal rolü ve durum doğrulaması zorunludur.
- `exports` bucket'ındaki rapor artefaktı `export_jobs.expires_at` sonunda erişilemez olur;
  uygulama purge işi fiziksel temizliği yapar ve `purged_at`ı yalnız pre-delete varlık+attempt
  kaydı ile verifier `VERSION_ABSENT` postcondition'ından sonra yazar.
  Bucket lifecycle, unutulmuş artefaktlar için ikinci güvenlik katmanıdır. İş kaydı ile dosya
  artefaktının saklama süresi aynı olmak zorunda değildir.
- `quarantine` ve tamamlanmamış multipart yüklemeler lifecycle ile temizlenir. Lifecycle
  gecikmesi boyunca hiçbir ürün API'si bu nesnelere erişim vermez.
- Noncurrent version ve delete marker yaşam döngüsü; hukukî saklama/imha kararı olmadan sonsuz
  veya sayısal süreyle sabitlenmez. OPS-005 güvenli başlangıç değerini, ürün sahibi/hukuk
  onayını ve maliyet etkisini kaydeder. Backup Object Lock/legal hold/retention, onaylı imha
  talebini sessizce aşmaz: koruma bitene kadar `RETENTION_PENDING` olarak gözlenir; bypass yetkisi
  normal veya disposal rollerine verilmez.
- Tek bölgeli S3 çoklu Availability Zone dayanıklılığı sağlar; bu, uygulama/operatör silmesine
  karşı bağımsız yedek değildir. Production için versioning yanında ayrı erişim rolüne sahip
  ikinci bucket'a Same-Region Replication veya bağımsız doğrulanmış export/backup akışı
  kurulmalıdır. Hedef, kaynak runtime rolünden silinemez olmalıdır. Kesin yöntem ve saklama
  OPS-005'te; her başarılı kopyanın source key/version → backup key/replica version eşlemesi
  durable kayda yazılır. Gerçek geri yükleme ve sentetik onaylı imha kanıtı OPS-006'da maliyet
  ve hukukî politika ile doğrulanır.
- Geri yükleme tatbikatı; nesne sürümü ile DB `file_assets`/`export_jobs` metaverisinin
  eşleşmesini, checksum'ı, kurum izolasyonunu, KMS erişimini ve token üzerinden indirmeyi
  doğrular. Yalnız “bucket var” veya “versioning açık” kanıtı geri yükleme kabulü değildir.
- Bütüncül fiziksel imha; source `DeleteObjectVersion` ve backup `DeleteObjectVersion`
  çağrılarının §5.4 exact-key verifier'ından ayrı `VERSION_ABSENT` kanıtı olmadan tamamlanamaz.
  Backup retention nedeniyle ertelenen kopya maliyet ve operasyon metriğinde bekleyen imha
  olarak kalır; source başarısı, delete marker, `HeadObject` `403/404` veya API'nin yalnız `2xx`
  yanıtı bütüncül başarı değildir.

## 6. Uygulama ve modül sınırları

- Domain/application katmanı AWS SDK türlerine bağımlı olmaz. En az `put/get/head/copy/delete`
  ile exact-key `verifyVersions` varlık/yokluk ve checksum sonuçlarını taşıyan uygulama portunu
  infrastructure S3 adaptörü uygular.
- Uzak nesne aktarımı uzun DB transaction'ı içinde yapılmaz. Yükleme/finalize/purge durumları
  idempotent komut ve güvenli yeniden deneme ile yürür; S3 başarısı doğrulanmadan DB başarı
  durumu veya kuyruk temizliği yazılmaz.
- `file_assets` ve ürün ilişkisi aynı kurum kapsamını DB bileşik FK ile korur. Bucket key
  düzeni, IAM veya uygulama kontrolü bu DB kısıtının yerine geçmez.
- §5.4 rol tablosu yetki sözleşmesidir. Runtime, writer, finalizer, purger, reconciler ve
  disposal rolleri yalnız durable kayıttan çözülen exact key/version üzerinde çalışır; hiçbiri
  geniş/dar `ListBucket`/`ListBucketVersions`, bucket policy/KMS yönetimi veya prefix/toplu silme
  yetkisi almaz. Tek istisna, object-data yetkisiz `storage_version_verifier` rolünün exact full
  key + `max-keys` koşullu kısa ömürlü `ListBucketVersions` session'ıdır.
  Kalıcı source sürümü yalnız kısa ömürlü `storage_disposal`, backup replica sürümü yalnız
  `storage_backup_disposal`, replication/restore ise silme yetkisiz `storage_backup` rolüyle
  yürür. Provisioner object data/sürüm silemez ve disposal rollerini assume edemez.
- A-013 erişim anahtarını repoya koymaz. Mümkünse workload identity/role ve kısa ömürlü kimlik
  bilgisi kullanılır; statik anahtar zorunluysa ayrı secret store, rotasyon ve alarm gerekir.
- A-014; yetkisiz istek, upload/finalize hatası, checksum/tarama reddi, purge gecikmesi,
  replication/versioning durumu, KMS erişim hatası, depolama/egress, süresi aşmış nonterminal
  işlem, `DISPOSAL_PENDING`/`RETENTION_PENDING` yaşı ve `NEEDS_ATTENTION` sayısı için
  metrik/alarm tanımlar.

## 7. Hata, boş ve yükleniyor durumları

| Durum | Zorunlu davranış |
|---|---|
| Yükleme başlatılıyor/devam ediyor | Ürün kaydı dosya eklenmiş gibi gösterilmez; ilerleme ve iptal görünürdür. |
| Doğrulama/tarama bekliyor | Dosya indirilemez ve iş ilişkisine aktif bağlanmaz; kullanıcıya güvenli bekleme durumu verilir. |
| Tür/boyut/checksum/tarama reddi | Quarantine dosyası yaşam döngüsüne bırakılır; güvenli hata kodu döner, dosya adı/içerik loglanmaz. |
| Depolama geçici hatası | İşlem yeniden denenebilir kalır; sunucu onayı olmadan başarı ve kuyruk temizliği yoktur. |
| Durable işlem eşik süresini aştı | Reconciler durable exact key için verifier sonucu alır; `KEY_ABSENT` ise güvenli retry/kapanış, aday version varsa pozitif metadata doğrulaması yapar; belirsizliği `NEEDS_ATTENTION` yapar. |
| Backup imhası retention/hold nedeniyle engelli | Source sonucu ayrı korunur; item `RETENTION_PENDING`, manifest `DISPOSAL_PENDING` kalır ve bütüncül başarı gösterilmez. |
| Source/backup delete yanıtı kayıp | Pre-delete varlık+attempt kanıtlı target, exact-key verifier'ın tam sayfalanmış `VERSION_ABSENT` sonucuyla ayrı uzlaştırılır; `403/404`, timeout, eksik sayfa veya eşleme belirsizliği başarı sayılmaz. |
| Dosya alanı boş | İsteğe bağlı PDF/logo/fotoğraf için geçerli durumdur; sahte URL veya placeholder asset üretilmez. |
| Yetkisiz/başka kurum | Varlık bilgisi sızdırmayan `404`; indirme tokenı ve S3 çağrısı oluşmaz. |
| Süresi dolmuş/iptal token | İndirme başlamaz; istemci yetkisi varsa yeni token ister. |
| Nesne ile DB metaverisi uyuşmuyor | Başarı dönülmez; güvenli bütünlük hatası ve operasyon alarmı üretilir. |

## 8. Zorunlu kabul ve doğrulama senaryoları

1. İki kurumun dosyaları aynı bucket'ta olsa dahi başka kurum asset/export kimliği `404`
   döner; S3 isteği ve indirme tokenı oluşmaz.
2. Mobil istemci AWS kalıcı kimlik bilgisi, bucket adı veya kalıcı nesne anahtarı alamaz.
3. Public access/ACL denemesi reddedilir; yalnız TLS ve SSE-KMS kullanılan private nesneler
   kabul edilir.
4. PDF/görsel MIME iddiası magic byte/ayrıştırma ile uyuşmazsa finalize başarısız olur ve aktif
   `file_assets`/ürün ilişkisi oluşmaz.
5. Boyut, checksum, zararlı içerik veya desteklenmeyen/şifreli PDF reddinde kısmi başarı
   gösterilmez; quarantine nesnesi erişilemez ve lifecycle ile temizlenir.
6. Aynı idempotency key ve eşdeğer gövdeyle paralel finalize/purge tek operation, nesne ve DB
   ilişkisi üretir; aynı anahtar farklı gövdeyle `409 IDEMPOTENCY_KEY_REUSED` döner.
7. Dosya S3'e yazılıp DB durum güncellemesi başarısız olduğunda reconciler durable işlem
   kaydındaki exact key'i koşullu verifier ile bulur; version adayını pozitif metadata/checksum
   ile eşleyip aynı işlemle tamamlar veya referans kontrolünden sonra exact-version cleanup
   üretir. Geniş listeleme gerektirmez ve ürün kaydı erken başarı sayılmaz.
8. Başka sınıfa atanmış hoca veya izni iptal edilmiş kullanıcı, önceden bildiği asset/token ile
   dosya indiremez.
9. Uygulama indirme URL'si yalnız API alan adı ve opak token taşır; bucket, AWS hesap/nesne
   anahtarı, kurum kimliği ve özgün dosya adı içermez.
10. Token süresi/iptali ve platform oturumu ayrı ayrı doğrulanır; süresi dolan, iptal edilen veya
    başka kullanıcı oturumuyla çağrılan token nesne akışı başlatmaz.
11. Excel için yalnız `READY` iş token alır; indirme anında güncel `REPORT_EXPORT`, veli bilgisi
    ve hoca sınıf kapsamı yeniden doğrulanır; expiry sonrası artefakt S3 onayından önce
    `purged_at` alamaz.
12. Normal asset arşivleme nesneyi fiziksel silmez; yeni indirmeyi kapatır ve geçmiş ilişkiyi
    korur. Yetkili geri yükleme aynı checksum'lı nesneyi yeniden erişilebilir yapabilir.
13. Yanlış overwrite/delete sonrası versioning/backup'tan geri yüklenen nesnenin checksum'ı,
    DB ilişkisi, KMS erişimi ve tenant izolasyonu doğrulanır.
14. Dört AWS hesabı ayrıdır: production rolü development/staging'e, hiçbir uygulama runtime'ı
    backup hesabına erişemez. Runtime/finalizer/purger/reconciler/disposal rolleri listeleme
    yapamaz; yalnız izole verifier exact-key koşullu version görünümü alır. Bu roller bucket
    policy, KMS key, linked asset sürümü veya backup hedefini silemez. Kalıcı source version
    silme yalnız onaylı disposal manifestiyle mümkündür; disposal rolünün backup yetkisi yoktur.
15. `storage_backup_disposal` yalnız değişmez özeti iki farklı yetkili tarafından onaylanmış
    manifestteki exact backup key/replica version ID için kısa ömürlü session alır. Runtime,
    provisioner, `storage_backup` ve `storage_disposal` aynı replica version'ı silemez.
16. Durable source→replica eşlemesi eksik/uyuşmaz veya başka manifest/kurum key'i verilmişse
    source ve backup silme başlamaz; disposal rolleri hedef keşfedemez, verifier da başka
    tenant/key için session alamaz.
17. Specific source version silmesi doğrulandığında backup replica hâlâ mevcutsa manifest
    `DISPOSAL_PENDING` kalır. Backup legal hold/retention engelinde `RETENTION_PENDING` olur;
    engel kalkıp exact replica version yokluğu doğrulanana kadar `COMPLETED` olamaz.
18. Source veya backup delete yanıtı kaybolursa aynı idempotency kimliği, ön-varlık/attempt kanıtı
    ve exact-key verifier postcondition'ı ile ayrı uzlaştırılır; target mevcutsa retry edilir,
    gerçekten yoksa `DELETE_VERIFIED` olur; tekrar deneme ikinci manifest, yanlış target veya
    çift başarı audit'i üretmez.
19. Source ve backup alt durumları bütün item'larda ayrı ayrı `DELETE_VERIFIED` olmadıkça
    bütüncül fiziksel imha tamamlanmış gösterilemez.
20. Log/audit çıktılarında token, S3 anahtarı, ön-imza, özgün hassas dosya adı veya dosya
    içeriği bulunmaz.

Bu görev belge kararıdır; sağlayıcı hesabı ve uygulama iskeleti henüz oluşturulmadığından
senaryolar bu PR'da çalıştırılmış sayılmaz. CONTENT-003 durable upload/finalize ve onaylı imha
durum testlerine; EXPORT-006 indirme entegrasyonuna; A-013/A-014 gözlem ve secret kontrollerine;
OPS-005 rol/IaC negatiflerine; OPS-006 restore ve sentetik source+backup imha tatbikatına
dönüştürülmeleri zorunludur.

## 9. Sonuçlar ve riskler

### Olumlu sonuçlar

- Kalıcı PDF/görseller ile geçici Excel artefaktlarının yaşam döngüsü ayrılır.
- Özel bucket, opak anahtar, uygulama tokenı ve indirme anı yetki kontrolü kurum izolasyonunu
  depolama URL'sine bırakmaz.
- S3 versioning/lifecycle ve ayrı yedek hedefi yanlış silme ile sahipsiz/geçici dosya risklerini
  ayrı kontrollerle azaltır.
- S3 portu sağlayıcı ayrıntılarını application/domain katmanından uzak tutar.

### Riskler ve azaltımlar

| Risk | Azaltım |
|---|---|
| Backend üzerinden indirme CPU/ağ yükü oluşturur | Dosya akışla iletilir; V1 boyut sınırları ölçülür. Egress/latency yayın hedefini bozarsa kalıcı anahtarı gizleyen ve eşdeğer iptal/yetki sağlayan ayrı dağıtım ADR'si açılır. |
| Dört AWS hesabı, IAM ve KMS işletimi erken açılır | Kapalı alfada uzak depo açılmaz; bu topoloji yalnız kritik-production tetiklerinde ve yazılı maliyet onayıyla OPS-005'e girer. |
| Versioning depolama maliyetini büyütür | Immutable key, orphan metriği ve hukukî onaylı noncurrent lifecycle; aylık storage/version bütçe alarmı. |
| DB kaydı ile nesne yazımı atomik değildir | Uzak çağrıdan önce commit edilen durable işlem, exact-key verifier, pozitif metadata kontrolü, CAS durum geçişi, idempotency ve referans kontrollü cleanup kullanılır; uzak aktarım DB transaction'ında tutulmaz. |
| Yetki dışı/elle yazılmış nesnenin durable kaydı yoktur | İnsan/genel admin `PutObject` erişimi verilmez; bütün writer workload'ları işlem kaydıyla sınırlandırılır ve OPS-005 negatif IAM testi doğrudan yazmayı reddeder. |
| Zararlı veya yanıltıcı dosya yüklenir | Allow-list, boyut/checksum, magic-byte/parse, tarama, görsel yeniden kodlama ve quarantine. |
| İndirme tokenı sızar | Ham token DB/logda tutulmaz; kısa ömür, oturum bağlama, güncel yetki kontrolü ve iptal. |
| Fiziksel silme version/backup'ta veriyi bırakır | Durable source→replica eşlemesi, ayrı disposal rolleri ve iki ayrı exact-version yokluk kanıtı zorunludur; delete marker veya yalnız source silmesi imha sayılmaz. |
| Backup retention onaylı imhayı geciktirir | Hold/retention bypass edilmez; `RETENTION_PENDING` maliyet/alarmıyla izlenir ve backup doğrulanmadan manifest tamamlanmaz. |
| Delete yanıtı kaybı yanlış başarı veya çift işlem üretir | Target başına pre-delete varlık kanıtı, durable attempt ve tam sayfalanmış exact-key version postcondition'ı kullanılır; `403/404`, eksik sayfa veya timeout başarıya çevrilmez. |

## 10. Kapsam dışı ve açık kararlar

- Sayısal PDF/görsel boyut, sayfa/piksel, upload tokenı, indirme tokenı, export artefaktı,
  noncurrent version ve yedek saklama süreleri ilgili API/ortam/hukuk kararında kesinleşir.
- Kesin malware tarama ürünü, dosya dönüştürme kütüphanesi ve PDF sanitize motoru CONTENT-003
  uygulama görevinin teknoloji değerlendirmesidir; bu ADR güvenlik sonucunu bağlar.
- Sağlayıcı eşdeğerlik deneyi/erteleme kararı A-007R; nesne deposu provisioning, seçilen
  sağlayıcının IAM/şifreleme/lifecycle ve güvenlik negatifleri OPS-005;
  nesne yedek/geri yükleme kanıtı OPS-006 kapsamındadır. OPS-001/OPS-002 yalnız veritabanı
  yedek/geri yüklemesini kapsar.
- `file_assets` API alanları, yükleme oturumu/finalize endpointleri ve hata kodları CONTENT-001;
  logo davranışı ORG-002; Excel indirme ucu EXPORT-006 kapsamındadır.
- Genel ortam ve runtime barındırma sınırları A-010'dadır. S3 seçilirse dört hesap/KMS ancak
  kritik-production profilinde bağlayıcı olur; R2 seçilirse version-ID, backup, exact-version
  imha ve geri yükleme sözleşmesi A-007R'de açıkça yeniden yazılır. Kaynak/IaC OPS-005,
  secret/workload identity A-013, metrik/log sağlayıcısı A-014 kapsamındadır.
- Hukukî KVKK sınıflandırması, veri işleme şartları, DPA ve kesin saklama/imha politikası bu
  teknik ADR'nin kapsamı dışındadır ve ürün sahibi/hukuk onayı gerektirir.
- Herkese açık kurumsal logo/CDN, ses/video/gelişmiş medya, e-posta eki ve kurumlar arası dosya
  paylaşımı V1 kapsamı dışındadır.

## 11. Kabul kontrol listesi

- [x] PDF, logo/fotoğraf ve Excel artefaktı yaşam döngüleri ayrıldı.
- [x] AWS S3, Supabase Storage, Cloudflare R2 ve DB/yerel disk seçenekleri karşılaştırıldı.
- [x] Ortak örnek yükte maliyet kalemleri ayrıldı ve simetrik ağırlıklı matrisle S3 kararı yeniden doğrulandı.
- [x] Dört hesaplı AWS ortam/backup topolojisi kritik-production referansı olarak tanımlandı;
      kapalı alfa provisioning'inden çıkarıldı.
- [x] Private access, kurum/sınıf yetkisi ve depolama anahtarını gizleyen teslim yolu tanımlandı.
- [x] Yükleme doğrulama, quarantine, checksum ve başarısız/yarım durumları tanımlandı.
- [x] Orphan reconciliation durable işlem/exact-key modeli, idempotency ve yalnız izole/veri işlemsiz verifier'a verilen koşullu version görünümüyle bağlandı.
- [x] Arşivleme, fiziksel silme, versioning, lifecycle, backup ve restore ayrıştırıldı.
- [x] Source ve backup fiziksel imhası ayrı kısa ömürlü roller, durable replica eşlemesi, retention bekleme ve çift doğrulamayla bağlandı.
- [x] P-004 yüksek riskli veri ve P-012 `export_jobs`/READY/yetki/purge kuralları korundu.
- [x] A-003'ün Supabase Storage'ı otomatik seçmeme sınırı korundu.
- [x] Uygulama, secret, ortam, gözlemleme ve hukukî karar sahipleri açık bırakıldı.
- [x] Güncel sağlayıcı iddiaları yalnız resmî kaynaklarla doğrulandı.

## 12. Resmî kaynaklar

- AWS, [Amazon S3 genel bakış](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html):
  private varsayılan, Block Public Access, lifecycle ve replikasyon yetenekleri.
- AWS, [S3 güvenlik en iyi uygulamaları](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html):
  public erişimi engelleme, en az yetki, şifreleme ve versioning önerileri.
- AWS, [S3 veri dayanıklılığı](https://docs.aws.amazon.com/AmazonS3/latest/userguide/DataDurability.html)
  ve [versioning davranışı](https://docs.aws.amazon.com/AmazonS3/latest/userguide/versioning-workflows.html):
  çoklu Availability Zone dayanıklılığı ve yanlış silme/overwrite kurtarması.
- AWS, [`DeleteObject` API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html):
  versioned bucket'ta kalıcı silme için exact `versionId` ve `s3:DeleteObjectVersion` gereksinimi.
- AWS, [`HeadObject` API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html):
  nesne yokken `s3:ListBucket` sahibi çağrının `404`, bu izni olmayan çağrının `403` alması ve
  HEAD hata yanıtlarının ayrıntılı hata kodu vermemesi. Bu nedenle `403/404` bu ADR'de yokluk
  postcondition'ı değildir.
- AWS, [`ListObjectVersions` API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectVersions.html),
  [S3 policy condition key'leri](https://docs.aws.amazon.com/AmazonS3/latest/userguide/amazon-s3-policy-keys.html)
  ve [S3 işlem/izin eşlemesi](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-with-s3-policy-actions.html):
  version görünümü için `s3:ListBucketVersions`, `s3:prefix`, `s3:max-keys` ve sayfalama
  sözleşmesi; §5.4 exact-key verifier sınırının resmî dayanağı.
- AWS, [S3 replikasyonu](https://docs.aws.amazon.com/AmazonS3/latest/userguide/replication.html),
  [replikasyonda silme davranışı](https://docs.aws.amazon.com/AmazonS3/latest/userguide/replication-what-is-isnot-replicated.html)
  ve [replikasyon durumu](https://docs.aws.amazon.com/AmazonS3/latest/userguide/replication-status.html):
  version/metadata korunumu, specific source-version silmesinin replica silmesini tetiklememesi
  ve exact nesne için `PENDING`/`COMPLETED`/`FAILED`/`REPLICA` durumları.
- AWS, [S3 Object Lock](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html)
  ve [Object Lock yönetimi](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock-managing.html):
  version bazlı legal hold/retention, korunan version'ın silinememesi ve exact-version hold/
  retention okuma izinleri.
- AWS, [ön-imzalı URL davranışı](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html):
  URL'lerin bearer niteliği, credential ve süre sonu ilişkisi. V1'in kullanıcıya doğrudan S3
  URL'si vermemesi bu risk değerlendirmesine dayanır.
- AWS, [veri aktarımı fiyatlandırması](https://aws.amazon.com/ec2/pricing/on-demand/): hizmet ve
  bölgeler arasında toplu ilk 100 GB/ay internet çıkışı kotası ve kota sonrası ücret modeli.
- AWS Price List API, [S3 Frankfurt güncel fiyat dizini](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonS3/current/eu-central-1/index.json)
  ve [KMS Frankfurt güncel fiyat dizini](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/awskms/current/eu-central-1/index.json):
  depolama, istek, anahtar ve KMS işlem birim fiyatları.
- AWS, [KMS fiyatlandırması ve ücretsiz katman](https://aws.amazon.com/kms/pricing/): hesap
  başına aylık 20.000 uygun ücretsiz istek, anahtar/cross-account istek faturalaması ve ilk iki
  key material rotation'ın anahtar başına aylık ek maliyeti.
- Supabase, [Storage private bucket'ları](https://supabase.com/docs/guides/storage/buckets/fundamentals),
  [S3 uyumluluğu](https://supabase.com/docs/guides/storage/s3/compatibility) ve
  [veritabanı yedekleri](https://supabase.com/docs/guides/platform/backups): private erişim,
  versioning/lifecycle sınırlamaları ve DB yedeğinin nesneleri içermemesi.
- Supabase, [Storage fiyatlandırması](https://supabase.com/pricing): Pro kotası, aşım ve egress
  kalemleri.
- Cloudflare, [R2 veri konumu](https://developers.cloudflare.com/r2/reference/data-location/),
  [bucket lock](https://developers.cloudflare.com/r2/buckets/bucket-locks/),
  [ön-imzalı URL'ler](https://developers.cloudflare.com/r2/api/s3/presigned-urls/) ve
  [nesne yaşam döngüsü](https://developers.cloudflare.com/r2/buckets/object-lifecycles/) ile
  [fiyatlandırma](https://developers.cloudflare.com/r2/pricing/): AB jurisdiction, süreli
  erişim, lifecycle, lock ve depolama/operasyon/egress modeli. Bucket lock silme/overwrite
  korumasıdır; S3 object versioning geçmişinin eşdeğeri kabul edilmez.
