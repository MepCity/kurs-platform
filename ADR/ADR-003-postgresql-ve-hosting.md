# ADR A-003 — PostgreSQL ve veritabanı barındırma sağlayıcısı

| Alan | Değer |
|---|---|
| Görev | A-003 — PostgreSQL/hosting sağlayıcısı ADR'si |
| Durum | Öneri — PR incelemesi ve kullanıcı onayı bekliyor |
| Tarih | 14 Temmuz 2026 |
| Karar sahibi | Ürün sahibi |
| Bağımlılıklar | `KISISEL_VERI_ENVANTERI.md`, `VERI_MODELI.md` |
| İlgili sonraki görevler | A-010, A-011, OPS-001, OPS-002 |

## 1. Karar özeti

İlk sürümün ana işlem veritabanı için **Supabase üzerinde yönetilen PostgreSQL**, kesin bölge olarak **AWS `eu-central-1` (Frankfurt)** ile önerilir.

Bu karar yalnızca ilişkisel veritabanının işletimi ve barındırılması içindir. Supabase'in Auth, Storage, Realtime ve Edge Functions ürünleri bu ADR ile seçilmez; bunlar sırasıyla A-004, A-007, A-006 ve backend/ortam kararlarının kapsamındadır. Uygulama API'si veritabanına sunucu tarafında standart PostgreSQL bağlantısıyla erişir; mobil istemci doğrudan veritabanı erişimi almaz.

**Supabase Data API kesin olarak kapalıdır.** Production ve staging projelerinde Dashboard → Data API → “Enable Data API” anahtarı kapatılır; bu ayar auto-generated REST ve GraphQL uç noktalarını grant/RLS durumundan bağımsız olarak yanıt vermez hâle getirir. Bu ürün Data API, GraphQL, `supabase-js` veya servis anahtarlarını kullanmaz. Projenin API ayarlarındaki “Exposed schemas” listesinde uygulama şeması bulunmaz; yalnızca sağlayıcının zorunlu sistem şemaları kalabilir. Bu dashboard yapılandırması SQL migration ile temsil edilemediğinden A-010 açılış kapısında kanıtlanır ve OPS-002 geri yükleme tatbikatında tekrar doğrulanır.

Karar onaylanana kadar hiçbir sağlayıcı hesabı, üretim ortamı, sır veya migration oluşturulmaz.

## 2. Bağlam ve zorunlu gereksinimler

Ana plan ilişkisel bir ana veri kaynağı, sürümlü migration, kurum bazlı açık ilişki, FK ve benzersizlik kısıtlarını zorunlu kılar (`URUN_VE_UYGULAMA_PLANI.md` §11.4). Ayrıca bütün yazma isteklerinde kimlik doğrulama, kurum/sınıf yetki kontrolü ve idempotency gerektirir (§11.5, §12.2).

`VERI_MODELI.md` taslağı PostgreSQL'e doğrudan uygun yapılar kullanır:

- UUID, `TIMESTAMPTZ`, `JSONB` ve adlandırılmış `ENUM` tipleri;
- kurum kapsamını zorlayan bileşik FK'ler ve partial unique index'ler;
- öğrencinin çakışan sınıf üyeliğini engelleyen `btree_gist` gerektiren `EXCLUDE` kısıtı;
- transaction ile birlikte uygulanması gereken idempotency, audit ve sürüm kuralları.

Bu nedenle aday; PostgreSQL uyumluluğunu bir soyutlama katmanı olmadan sağlamalı, gerekli uzantıyı migration ile etkinleştirebilmeli ve `pg_dump`/`pg_restore` ile taşınabilir olmalıdır. Kişisel veri envanteri telefon, adres, doğum tarihi, fotoğraf, serbest not, yoklama ve ilerleme verilerini yüksek riskli ürün içi veri olarak işaretler. Bu nedenle Avrupa bölgesi, TLS, erişim yetkilerinin sınırlandırılması, yedekleme ve test edilmiş geri yükleme akışı seçimin ayrılmaz parçasıdır. Bu ADR hukukî KVKK uygunluğu veya saklama/imha süresi kararı vermez.

## 3. Değerlendirme ölçütleri

| Ölçüt | Neden gerekli |
|---|---|
| Tam PostgreSQL uyumu | Veri modeli PostgreSQL'e özgü kısıtlar ve tipler içerir. |
| Frankfurt'ta veri yerleşimi | İlk kullanıcıların Türkiye'de olması için gecikmeyi azaltır; Avrupa bölgesi veri işleme değerlendirmesini de kolaylaştırır. |
| Yedekleme ve geri yükleme | Yoklama, ilerleme ve denetim kaydında sessiz veri kaybı kabul edilemez. |
| Erişim izolasyonu | Ağ erişimi, sırlar ve veritabanı rolleri kurum izolasyonuna ek savunma sağlamalıdır. |
| Düşük işletim yükü | İlk sürüm modüler monolittir; ekip, yönetilen hizmet karşılığında veritabanı operasyonunu büyütmemelidir. |
| Taşınabilirlik | Sağlayıcı değişiminde şema ve veri standart PostgreSQL araçlarıyla taşınabilmelidir. |
| Maliyet görünürlüğü | Üretim yedeği/PITR maliyeti ayrı ve görünür olmalıdır; ücretsiz katman üretim sayılmaz. |

## 4. İncelenen seçenekler

| Seçenek | Güçlü yanlar | Sınırlamalar | Sonuç |
|---|---|---|---|
| Supabase PostgreSQL | Tam PostgreSQL, Frankfurt seçeneği, bağlantı havuzu, yönetilen günlük yedek, PITR ve PostgreSQL RLS desteği; ilk sürüm için düşük operasyon yükü | PITR ek maliyettir; platformun diğer ürünlerini seçmiş saymamak için erişim sınırları disiplinle korunmalıdır | **Önerildi** |
| Amazon RDS for PostgreSQL | Olgun VPC/KMS denetimleri, 35 güne kadar PITR, Multi-AZ seçeneği | Ağ, erişim, gözlemleme ve uygulama barındırma yapılandırmasının ilk sürüm maliyeti/operasyon yükü daha yüksektir | Üretim ölçeği veya özel ağ/compliance gereksiniminde yeniden değerlendirilir |
| Render PostgreSQL | Frankfurt bölgesi, uygulama ile özel ağ ve yönetilen PITR; basit işletim | Geri yükleme penceresi planla sınırlıdır ve bu görevdeki veri koruma hedefi için açık RPO taahhüdü kaynakta doğrulanamadı | Yedek aday; seçilmedi |
| Kendi yönetilen PostgreSQL | Tam ağ/işletim kontrolü | Yama, HA, yedek, geri yükleme ve izleme sorumluluğu V1 için gereksiz yük oluşturur | Reddedildi |

Sağlayıcı özellikleri ve fiyatları zamanla değişebileceği için bu karşılaştırma 14 Temmuz 2026'da üreticilerin resmî belgelerine göre yapılmıştır; satın alma öncesi yeniden doğrulanmalıdır.

### 4.1. Aynı varsayımlarla aylık maliyet karşılaştırması

Bu tablo yalnız veritabanı katmanını karşılaştırır: Frankfurt'ta tek production PostgreSQL, 730 saat, yaklaşık 1 GB bellek sınıfı, 20 GB veri, 7 gün veya daha fazla kurtarma penceresi, tek-AZ/tekil düğüm, replica/HA yok, egress/vergiler/uygulama barındırma/staging/haricî mantıksal yedek yoktur. “Toplam”, bu varsayımlarla üretim veritabanının aylık USD tahminidir; sağlayıcı paketleri aynı mimariyi birebir sunmadığından satın alma teklifi veya SLA değildir.

| Sağlayıcı | Hesap bileşenleri | Aylık tahmin (USD) | Aynı koruma varsayımındaki not |
|---|---|---:|---|
| Supabase | Pro plan 25 USD + Small compute 15 USD + 7 gün PITR yaklaşık 100 USD − 10 USD compute kredisi | **yaklaşık 130** | PITR en az Small compute gerektirir; 2 dakikaya kadar RPO belgesi vardır ve seçilen production tabanı budur. |
| Amazon RDS PostgreSQL | Frankfurt'ta Single-AZ `db.t4g.micro` yaklaşık 22 USD + 20 GB gp3 depolama yaklaşık 3 USD | **yaklaşık 25 + egress/ek yedek** | Otomatik yedek/PITR 35 güne kadar yapılandırılabilir; 2 dakikalık RPO için ayrı doğrulama gerekir. |
| Render PostgreSQL | Pro workspace 25 USD + Basic-1gb 19 USD + 20 GB genişletilebilir SSD için 6 USD | **yaklaşık 50** | Pro planda 7 günlük PITR penceresi vardır; 2 dakikalık RPO taahhüdü doğrulanmadığından aynı veri koruma varsayımını karşılamaz. |

AWS tahmini, yalnız karşılaştırılabilir küçük tekil instance içindir; AWS'nin resmî fiyatlandırması instance, depolama, yedek ve veri transferini ayrı ücretlendirir. Render toplamı ise bu belgede tanımlanan 2 dakikalık RPO'yu sağlamaz; maliyet karşılaştırılabilir kapasite/pencere içindir, eşdeğer dayanıklılık iddiası değildir. A-010, satın alma öncesinde üç satırı aynı güncel varsayımlarla yeniden hesaplayıp karar kaydına ekler.

Supabase için planlanan üç ortamın ayrı toplamı: tek Pro organizasyonu 25 USD + production Small 15 USD + production 7 gün PITR 100 USD + staging Micro 10 USD + development Micro 10 USD − tek 10 USD compute kredisi = **yaklaşık 150 USD/ay**. Staging ve development sentetik veri taşır, PITR kullanmaz. Egress, depolama aşımı, IPv4 eklentisi, mantıksal yedek deposu ve restore tatbikatı sırasında oluşan geçici proje maliyeti bu toplama dahil değildir.

## 5. Önerilen teknik sınırlar

### 5.1. Ortamlar ve bölge

- Her ortam ayrı Supabase projesidir: `development`, `staging`, `production`.
- Production ve staging `eu-central-1` (Frankfurt) bölgesinde oluşturulur. Development yalnızca sentetik veri içerir; üretim kişisel verisi development/staging ortamına kopyalanmaz.
- Production, ücretsiz/otomatik askıya alınabilen katmanda çalıştırılmaz.
- A-010, backend çalışma ortamını aynı bölgeye yerleştirir veya bölgeler arası ağ gecikmesi ve egress etkisini ayrıca onaylar.

### 5.2. Şema, Data API ve varsayılan grant sınırı

- Uygulama tabloları `app` adlı **özel ve non-exposed** şemada oluşturulur; `public` şemasında uygulama tablosu, view, function veya RPC oluşturulmaz. `app`, Data API “Exposed schemas” listesine eklenmez. Bu, Data API kapatma ayarına ek ikinci sınırdır; Data API ileride yanlışlıkla etkinleştirilse dahi uygulama nesneleri kendiliğinden HTTP yüzeyi kazanmaz.
- Yeni Supabase projeleri “Automatically expose new tables and functions” kapalı seçeneğiyle oluşturulur. Sağlayıcı varsayılanı değiştiği için migration ayrıca `anon`, `authenticated` ve `service_role` rollerinden `app` şeması ile bütün tablo/sequence/function haklarını açıkça `REVOKE` eder; gelecek nesneler için de aynı roller adına default privilege iptali uygular.
- `PUBLIC` rolüne uygulama şeması üzerinde `USAGE`, uygulama tablolarında `SELECT/INSERT/UPDATE/DELETE`, sequence'lerde `USAGE/SELECT` veya function'larda `EXECUTE` verilmez. PUBLIC için PostgreSQL'in varsayılan function `EXECUTE` hakkı, uygulama fonksiyonları varsa migration ile iptal edilir.
- Runtime uygulama rolüne yalnızca gerekli `app` şeması/nesne hakları verilir. Bu rol **owner, superuser veya `BYPASSRLS` olamaz**; `CREATEDB`, `CREATEROLE` ve şema oluşturma hakkı da taşımaz. Runtime rolünün sahibi olduğu nesne bulunmaz.
- Migration sahibi runtime rolünden ayrıdır. Migration aracı/yürütme modeli **ayrı bir teknik karar** olarak A-011 başlamadan önce kapanmalıdır; bu ADR veya A-002 bu aracı seçmez. A-011, yalnız onaylı aracı repo/CI iskeletine bağlar; her modül görevi yalnız kendi yeni migration dosyasının sahibidir. Migration sahibi, yalnız CI/CD'nin kısa ömürlü sırrıyla kullanılır ve production çalışma zamanında kullanılmaz.
- Varsayılan yetki iptalleri migration sahibine açıkça bağlanır: `ALTER DEFAULT PRIVILEGES FOR ROLE <migration_owner> IN SCHEMA app REVOKE ... FROM anon, authenticated, service_role, PUBLIC`. İlk migration mevcut nesnelerde de aynı `REVOKE` işlemlerini uygular. PostgreSQL default privilege'ları yalnız ilgili owner'ın sonradan oluşturduğu nesneleri etkilediğinden, uygulama nesnesi oluşturan başka owner yasaktır.

### 5.3. RLS: global ve kurum kapsamlı tablolar

- `organization_id` taşıyan bütün tenant tablolarında hem `ENABLE ROW LEVEL SECURITY` hem `FORCE ROW LEVEL SECURITY` zorunludur. Policy, yalnız transaction'a güvenli biçimde yerleştirilmiş etkin kurum bağlamıyla eşleşen satırlara izin verir; bileşik FK'ler ve API yetkilendirmesi bu politikanın yerine geçmez.
- `users`, `platform_administrators` ve `platform_administrator_profiles` global tablolardır (`VERI_MODELI.md` §2.2, §4.1–§4.4). Bunlar “kurum eşitse” politikasıyla görünür kılınamaz. Varsayılanı kapalı erişimdir: runtime rolüne doğrudan tablo hakkı verilmez. Global tablo erişiminin kesin komut/function/ayrı rol mekanizması **A-004 kabul kapısında** seçilip yetki, oturum ve denetim kurallarıyla doğrulanır.
- Kurum tablolarındaki bir platform yöneticisi istisnası dahi RLS'i genel olarak baypas etmez. İstisna, kanıtlanmış platform-yönetici bağlamı + hedef `organization_id` ile sınırlı policy/komut üzerinden işler ve `PLATFORM_ADMIN_ORG_ACCESS` denetim olayı üretir.
- Tenant bağlamı her istek transaction'ının başında `SET LOCAL` ile kurulur ve commit/rollback ile otomatik temizlenir. Connection pooler nedeniyle session-scoped `SET` veya bağlantı üzerinde kalıcı bağlam kullanılamaz. Bağlam kurulamazsa veya doğrulanamazsa sorgu reddedilir; varsayılan kurum ya da “tüm kurumlar” fallback'i yoktur.

### 5.4. Bağlantı kararı için A-010 kapısı

- Migration, `pg_dump`, `pg_restore` ve uzun ömürlü backend için doğrudan PostgreSQL bağlantısı tercih edilir; Supabase'de bu uç nokta varsayılan olarak IPv6'dır.
- Backend çalışma ortamı IPv4-only ise A-010 şu iki yolu ölçerek seçer: uzun ömürlü runtime için IPv4 uyumlu Supavisor **session pooler** veya direct bağlantı zorunluysa ücretli IPv4 eklentisi. Eklenti direct endpointi IPv4'e çevirir; dual-stack değildir ve DNS değişiminde kısa kesinti riski vardır.
- Serverless/kısa ömürlü runtime seçilirse A-010 transaction pooler kullanımını değerlendirir. Transaction-scope tenant bağlamı ve prepared statement/connection özellikleri seçilen sürücüyle staging'de test edilmeden bu mod onaylanmaz.
- A-010 kapısı; seçilen bağlantı türü, IPv6 desteği, IPv4 eklentisi maliyeti/gereksinimi, TLS doğrulaması, pooler davranışı ve transaction-scope tenant RLS izolasyon testini yazılı kanıtla kapatır.

### 5.5. Şema ve migration ön koşulları

- Migration SQL'i PostgreSQL 15+ hedefler ve her ortamda aynı sırayla çalışır.
- İlk migration, `btree_gist` uzantısının kullanılabilirliğini doğrular ve gereken `EXCLUDE` kısıtını üretir. Uzantı sağlanamazsa migration başarısız olur; uygulama katmanı ile sessiz ikame yapılmaz.
- `VERI_MODELI.md` §15.1–§15.6'daki bileşik FK, unique/partial index ve enum kısıtları migration ile oluşturulur. RLS bunların alternatifi değildir.
- SQL migration doğrulaması en az şunları otomatik olarak sınar: `app` şeması ve nesneleri vardır; `anon`/`authenticated`/`service_role`/`PUBLIC` grant'leri yoktur; default privilege iptalleri `migration_owner` için tanımlıdır; runtime rolü owner/superuser/`BYPASSRLS` değildir; tenant tablolarda `ENABLE` + `FORCE RLS` etkin ve policy'ler vardır; global tablolar tenant policy'sine yanlışlıkla bağlanmamıştır; iki farklı kurum bağlamının birbirinin satırlarını okuyamadığı doğrulanır.
- Production şema değişikliği sürümlü migration, yedek uygunluğu ve staging denemesi olmadan çalıştırılmaz.

Data API kapalı olması, “Exposed schemas”ın `app` içermemesi, proje oluşturma ayarındaki otomatik exposure iptali ve bağlantı/IPv6 seçimi **SQL migration doğrulamasının parçası değildir**. Bunlar A-010 ortam/provisioning kontrolleridir; Dashboard/Management API kanıtı ve dışarıdan REST/GraphQL erişiminin reddi ile doğrulanır.

### 5.6. Yedekleme, geri yükleme ve hedefler

- Production için sağlayıcının PITR özelliği etkinleştirilir. Bu, günlük yedekten daha sık geri dönüş gerektiren yoklama ve ilerleme verisi için zorunlu operasyonel kontroldür.
- Tasarım hedefi: sağlayıcının belgelediği sınırlar içinde **en fazla 2 dakika RPO**. Supabase, PITR için en kötü durumda iki dakika RPO belirtir; gerçek son geri yüklenebilir zaman, alarm ve operasyon doğrulamasıyla izlenir.
- **RTO hedefi 4 saattir**; bu sağlayıcı SLA'sı değildir. OPS-002, gerçek veri boyutuyla geri yükleme tatbikatı yapar ve bu hedefi ölçerek kabul/iyileştirme kararı verir.
- PITR'a ek olarak, şifreli mantıksal yedek (`pg_dump`) production dışında, erişimi kısıtlı depoda saklanır. Sıklık, şifreleme anahtarı, saklama süresi ve imha politikası A-010 ile hukukî değerlendirme tamamlandığında kesinleştirilir; bu ADR süre uydurmaz.
- Geri yükleme mevcut production üstüne doğrudan yapılmaz: önce **yeni Supabase proje/örneğine** geri dönülür, bu geçici ikinci ortamın compute/PITR/egress maliyeti restore tatbikatı bütçesine yazılır, sonra onaylı kesme planı uygulanır.
- OPS-001 yedek yapılandırmasını uygular. OPS-002; restore-to-new-project maliyetini, özel rollerin/parolaların ve başka secret'ların yeniden oluşturulmasını, migration sürümünü, default grant iptallerini, Data API/non-exposed şema ayarını, RLS force/policy durumunu ve global/tenant izolasyon sorgularını doğrular. Bu ADR bu operasyon görevlerini tamamlanmış saymaz.

### 5.7. Gözlemleme ve maliyet eşiği

- A-014; bağlantı sayısı, CPU/bellek/disk, başarısız bağlantı, en son geri yüklenebilir zaman, yedek başarısı ve migration hatası için alarm/izleme eşiğini tanımlar.
- Üretim maliyet onayı; Pro planı, seçilen compute boyutu, PITR, depolama/egress, staging ve mantıksal yedek depolamasını tek tahminde gösterir. PITR'nin dokümanda yaklaşık 7 günlük saklama için aylık 100 USD olarak listelenmesi, proje sahibinin açık maliyet onayını gerektirir.
- Kapasite yükseltme kararı ölçüme dayanır; ilk compute boyutu bu ADR ile sabitlenmez.

## 6. Sonuçlar ve riskler

### Olumlu sonuçlar

- Veri modelinin PostgreSQL'e özgü bütünlük kuralları doğrudan uygulanabilir.
- Frankfurt bölgesi ve yönetilen yedek/PITR ile V1 operasyon yükü azalır.
- Standart PostgreSQL bağlantısı ve `pg_dump`/`pg_restore` taşınabilirliği, sağlayıcı bağımlılığını sınırlar.

### Riskler ve azaltımlar

| Risk | Azaltım |
|---|---|
| Supabase hizmetlerinin kapsamı farkında olmadan genişler | Bu ADR yalnız DB katmanını seçer; Auth/Storage/Realtime/Data API için ayrı ADR ve açık onay gerekir. |
| RLS'i baypas eden sunucu rolü tenant sızıntısı oluşturur | API yetki kontrolü korunur; transaction tenant bağlamı, RLS policy'leri ve kurum izolasyon testleri zorunludur. |
| PITR maliyeti beklenenden yüksek olur | Üretim açılışında maliyet onayı, aylık kullanım izleme ve A-010 ortam bütçesi. |
| Geri yükleme beklenen sürede tamamlanmaz | OPS-002 ile düzenli tatbikat; RTO hedefi karşılanmazsa compute/operasyon planı yeniden değerlendirilir. |
| Sağlayıcı taşınması gerekir | Sürüm kontrollü SQL migration'lar, standart PostgreSQL kullanım biçimi ve mantıksal yedekler korunur. |

## 7. Kapsam dışı ve açık kararlar

- Backend dili/frameworkü ve uygulamanın nerede çalışacağı A-002/A-010 kapsamındadır.
- Kimlik doğrulama sağlayıcısı, parola/token saklama biçimi A-004 kapsamındadır.
- Gerçek zaman kanalı A-006, PDF/logo nesne depolama A-007 kapsamındadır.
- Migration aracı/yürütme modeli, A-011 öncesinde ayrı bir teknik karar olarak kesinleşir; A-011 yalnız onaylı aracın repo/CI bağlantısını kurar. Secret mekanizması A-013 tarafından kesinleştirilir. A-009 yalnız monorepo/repo yapısı kararını verir.
- Hukukî veri işleme, saklama, imha süresi ve sağlayıcı DPA'sının ürün sahibi/hukuk tarafından onaylanması bu teknik ADR'nin kapsamı dışındadır.
- Çok bölgeli felaket kurtarma, read replica ve yüksek erişilebilirlik topolojisi V1 başlatma gereksinimi değildir; ölçüm veya sözleşmesel ihtiyaç oluşursa yeni ADR ile ele alınır.

## 8. Kabul ve doğrulama listesi

Bu görev belge kararıdır; henüz uygulama, sağlayıcı hesabı veya otomatik test altyapısı yoktur. PR incelemesinde aşağıdaki maddeler doğrulanmıştır:

- [x] Seçim, `VERI_MODELI.md`nin PostgreSQL kısıtları ve kurum izolasyonu ile uyumludur.
- [x] Kişisel/hassas veri envanterindeki erişim, yedek ve hukukî sınırlar korunmuştur.
- [x] Sağlayıcı seçimi A-004, A-006, A-007 ve A-010 kararlarını önceden vermemektedir.
- [x] Yedek, PITR, geri yükleme tatbikatı ve maliyet onayı için sonraki görev sahipleri açıktır.
- [x] Supabase, AWS RDS ve Render seçenekleri ölçüt bazında karşılaştırılmıştır.
- [x] Data API kapatma, non-exposed şema, varsayılan grant iptali ve runtime/migration rol ayrımı tanımlanmıştır.
- [x] Global ve tenant tablolarının RLS ayrımı ile transaction-scope tenant bağlamı açıklanmıştır.
- [x] IPv6/direct, session pooler ve IPv4 eklentisi A-010 karar kapısına bağlanmıştır.
- [x] SQL migration kontrolleri ile A-010 ortam/Data API kontrolleri ayrılmıştır.
- [x] Production ve üç ortam toplam maliyeti ayrı gösterilmiştir.
- [x] Güncel sağlayıcı iddiaları resmî kaynaklarla doğrulanmıştır.

Onaydan sonra A-010, bu ADR'nin seçtiği veritabanı bölgesine ve erişim sınırlarına uygun ortam sözleşmesini üretmelidir. OPS-001 ve OPS-002 tamamlanmadan yedekleme/geri yükleme kabul edilmiş sayılmaz.

## 9. Resmî kaynaklar

- Supabase, [bölgeler](https://supabase.com/docs/guides/platform/regions): `eu-central-1` Frankfurt bölgesinin kullanılabildiği.
- Supabase, [veritabanı genel bakışı](https://supabase.com/docs/guides/database/overview): tam PostgreSQL, uzantılar ve ücretli planlarda yedek/PITR yetenekleri.
- Supabase, [bağlantı yöntemleri](https://supabase.com/docs/guides/database/connecting-to-postgres): migration/`pg_dump` için doğrudan bağlantı ve uygulama trafiği için havuz seçenekleri.
- Supabase, [RLS kılavuzu](https://supabase.com/docs/guides/database/postgres/row-level-security): RLS'in etkinleştirilmesi, policy'ler ve baypas eden anahtarların istemcide kullanılmaması.
- Supabase, [Data API güvenliği](https://supabase.com/docs/guides/api/securing-your-api): Data API'nin Dashboard'dan kapatılması, exposed schema ve grant/RLS sınırları.
- Supabase, [Data API exposure değişikliği](https://supabase.com/changelog/45329-breaking-change-tables-not-exposed-to-data-and-graphql-api-automatically): otomatik Data API grant'lerinin opt-in hâle gelmesi.
- Supabase, [bağlantı ve IPv4](https://supabase.com/docs/guides/platform/ipv4-address): direct bağlantının IPv6 varsayılanı, IPv4 uyumlu Supavisor ve IPv4 eklentisi.
- Supabase, [yedek ve PITR belgeleri](https://supabase.com/docs/guides/platform/backups) ve [fiyatlandırma](https://supabase.com/pricing): saklama, geri yükleme ve güncel maliyet varsayımları.
- Supabase, [restore to a new project](https://supabase.com/docs/guides/platform/clone-project): yeni proje maliyeti, taşınan veriler ve elle yeniden yapılandırılması gereken ayarlar.
- AWS, [RDS for PostgreSQL](https://aws.amazon.com/rds/postgresql/): alternatifin PITR, VPC/KMS ve Multi-AZ olanakları.
- Render, [PostgreSQL yedekleri](https://render.com/docs/postgresql-backups), [bölgeler](https://render.com/docs/regions) ve [fiyatlandırma](https://render.com/pricing): alternatifin yedek/PITR, Frankfurt ve maliyet bilgileri.
