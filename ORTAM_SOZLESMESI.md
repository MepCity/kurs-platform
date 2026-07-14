# Geliştirme, Staging ve Üretim Ortam Sözleşmesi

| Alan | Değer |
|---|---|
| Durum | Kabul edildi — kademeli maliyet profiliyle revize edildi |
| Tarih | 15 Temmuz 2026 |
| Görev | A-010 — Geliştirme, staging ve üretim ortam sözleşmesi |
| Karar sahibi | Ürün sahibi |
| Bağımlılıklar | `ADR/ADR-002_BACKEND_DILI_VE_FRAMEWORK.md` (A-002), `ADR/ADR-003-postgresql-ve-hosting.md` (A-003) |
| İlgili sonraki görevler | A-004R1–A-004R3, A-007R, A-011, A-012, A-013, A-014, OPS-001, OPS-002 |

## 1. Amaç ve kapsam

Bu sözleşme Kurs Platform'un geliştirme, staging ve üretim ortamlarının birbirinden nasıl
ayrılacağını; Java/Spring Boot backend'in nerede ve hangi dağıtım modeliyle çalışacağını;
ortamlar arasında hangi yapıtın nasıl terfi edeceğini; Supabase PostgreSQL bağlantısının ve
açılış kontrollerinin nasıl uygulanacağını tanımlar.

Bu görev bir ortam kurulum görevi değildir. Sağlayıcı hesabı, Render servisi, Supabase
projesi, DNS kaydı, secret, migration veya CI hattı oluşturmaz. Fiziksel iskelet ve uygulama
sonraki görevlerde yalnız bu sözleşmenin sınırları içinde kurulur.

Bağlayıcı kaynak önceliği şöyledir:

1. `URUN_VE_UYGULAMA_PLANI.md`,
2. onaylı ADR'ler ve alan sözleşmeleri,
3. bu ortam sözleşmesi,
4. sağlayıcı panelindeki varsayılanlar.

Sağlayıcı varsayılanı bu belgelerden daha geniş erişim veriyorsa varsayılan daraltılır; sessizce
kabul edilmez.

## 2. Karar özeti

- `development`, `staging` ve `production` ayrı güven ve veri profilleridir; ilk günden üç
  sürekli bulut servisi olmak zorunda değildir. Development varsayılan olarak yerel,
  staging yayın öncesi geçici, production ise gerçek kullanıcı başladığında kalıcıdır.
- Java 21/Spring Boot 4.1 modüler monolit standart Docker imajı olarak kalır. Render tercih
  edilen yönetilen runtime adayıdır; kapalı alfa Render Free veya öğrenci kredili Heroku'da
  best-effort çalışabilir. Sağlayıcı seçimi uygulama koduna sızmaz.
- Kapalı alfa dış ödeme hedefi `0 USD/ay`, gerçek kurum pilotu hedefi `25–60 USD/ay`dır. Bu
  hedefler garanti/SLA değildir; kota, domain, e-posta ve gözlemleme ayrıca ölçülür.
- Render Pro workspace, üç sürekli backend, üç ücretli Supabase projesi ve PITR başlangıç
  zorunluluğu değildir. Bu kontroller aktif/kritik ürün tetiklerinde açılır.
- Mobil istemci hiçbir profilde veritabanına doğrudan bağlanmaz; tek giriş yüzeyi sürümlü
  uygulama API'sidir. Supabase Data API/Auth/Realtime bu ortam kararıyla seçilmez.
- Backend kalıcı bir istemcidir. Seçilen çalışma ortamı için doğrulanmış IPv6 direct bağlantı
  kanıtı bulunmadığından başlangıç bağlantısı IPv4 uyumlu **Supavisor session pooler**, port
  `5432`dir. Transaction pooler kullanılmaz.
- Aynı committen bir kez üretilen, değişmez imaj özeti önce development, sonra staging, sonra
  production'a terfi eder. Ortama göre yeniden derleme yapılmaz; fark yalnız dış yapılandırma
  ve secret'lardadır.
- Kabul edilmiş production digest'i ile en az bir önceki doğrulanmış rollback digest'i,
  release kaydındaki rollback penceresi boyunca registry garbage collection'dan korunur.
  Digest registry'de erişilebilir ve manifest/checksum doğrulanmış değilse terfi veya rollback
  başlamaz.
- Gerçek production dağıtımı otomatik olarak `main` dalını izlemez. Geçici staging kabulü,
  migration/yedek uygunluğu ve yetkili insan onayı olmadan terfi yapılamaz.
- Production verisi hiçbir yolla development veya staging'e kopyalanmaz. Alt ortamlarda yalnız
  sentetik veri kullanılır. Bu kural destek vakası, hata ayıklama ve performans testi için de
  geçerlidir.

## 3. Backend çalışma ortamı seçimi

### 3.1. Değerlendirilen seçenekler

| Seçenek | Güçlü yön | Temel maliyet/risk | Sonuç |
|---|---|---|---|
| Render Frankfurt, Docker Web Service | Düşük işletim yükü; ücretsiz alfa ve ölçüme göre ücretli compute; health check/TLS/rollback | Free uyku/soğuk başlangıç ve 0.1 CPU yalnız best-effort alfaya uygundur | **Tercih edilen aday** |
| Öğrenci kredili Heroku `eu` | Standart container/runtime ve geçici dış ödeme avantajı | Kredi kişiye/süreye bağlıdır; `eu` kesin Frankfurt değildir; kapasite ve veri yerleşimi deney ister | Kapalı alfa deneyi adayı |
| AWS App Runner Frankfurt | Yönetilen container ve otomatik ölçekleme; Supabase'in AWS Frankfurt altyapısıyla aynı bulut bölgesi | Kullanıma bağlı maliyet, AWS/ECR/IAM/CloudWatch işletim yüzeyi ve ilk sürüm için daha fazla yapılandırma | Ölçülmüş ölçek veya ağ ihtiyacında yeniden değerlendirilir |
| Fly.io Frankfurt | Düşük başlangıç compute maliyeti ve esnek VM/container modeli | Makine, ağ, ölçek ve yayın operasyonu Render'a göre daha çok sahiplik ister | Seçilmedi |
| Supabase Edge Functions | Veritabanıyla aynı platform | A-002'nin Java 21/Spring Boot kalıcı modüler monolit kararıyla uyumlu değildir | Reddedildi |
| Kendi yönetilen VM/Kubernetes | En geniş kontrol | Yama, orkestrasyon, TLS, ölçekleme ve olay müdahalesi V1 için gereksiz işletim yüküdür | Reddedildi |

### 3.2. Render çalışma sınırları

- Başlangıç workspace planı tek ürün sahibi için Hobby olabilir. Pro; ikinci operasyon
  yetkilisi, workspace audit logu, sürekli isolated environment veya protected production
  gereksinimi ortaya çıktığında yazılı maliyet onayıyla açılır.
- Hobby kişisel prototip sınırıdır: tek workspace üyesine ve proje başına iki ortama izin verir;
  workspace audit logu ve environment isolation sağlamaz. Bu nedenle adlandırılmış çoklu insan
  erişimini, üç ayrı kalıcı ortamı ve denetlenebilir production yönetimini karşılamaz.
- Runtime türü `Docker`dır. Sağlayıcının dil runtime'ı yerine repodaki sürümlü Dockerfile
  kullanılır; yerel, CI ve açılan uzak ortamlar aynı Java çalışma zamanı ailesini kullanır.
- Servis, Render'ın verdiği `PORT` değerinde `0.0.0.0` adresine bağlanır.
- Kalıcı disk bağlanmaz. Uygulama süreci stateless'tir; yerel dosya sistemi geçici kabul edilir.
  PDF, logo, rapor veya yedek runtime diskinde saklanmaz.
- HTTP readiness kontrolü yalnız süreç ayakta mı sorusunu değil, yeni örneğin trafik almaya
  hazır olup olmadığını gösterir. Tam Actuator yüzeyi veya hassas bağımlılık ayrıntısı genel
  internete açılmaz.
- Uygulama `SIGTERM` aldığında yeni iş kabulünü durdurur, uçuş hâlindeki istekleri belirlenen
  süre içinde tamamlar ve veritabanı bağlantı havuzunu kapatır. Kesin süre A-011 uygulama
  iskeletinde ölçülür ve Render'ın kapanış sınırını aşamaz.
- Sentetik kapalı alfa ücretsiz/uykuya geçen compute kullanabilir ve SLA sunmaz. Spring Boot'un
  512 MB/0.1 CPU sınırında açılış, bellek, health check, soğuk başlangıç ve SSE davranışı ölçülür.
- Gerçek kurum pilotu ücretsiz/uykuya geçen compute kullanmaz. İlk ücretli boyut ölçümle seçilir;
  Starter yetersizse Standard'a yükseltilir. Kaynak eksikliği özellik/test kapatarak gizlenmez.

### 3.3. Sağlayıcı değişikliği koşulu

Aşağıdakilerden biri oluşursa yeni ADR gerekir:

- Frankfurt servisinin ihtiyacı karşılamaması,
- sağlayıcılar arası gecikmenin normal API çağrılarındaki hedefi sürekli bozması,
- sabit/dedike çıkış IP'si veya özel ağ için maliyetin alternatif sağlayıcıyı anlamlı kılması,
- Render'da Java runtime kaynak sınırının güvenilirlik veya maliyet hedefini karşılamaması,
- sözleşmesel erişilebilirlik, veri işleme ya da güvenlik gereksiniminin değişmesi.

## 4. Ortam matrisi

| Sınır | Development | Staging | Production |
|---|---|---|---|
| Amaç | Günlük geliştirme ve otomatik test | Release adayı, migration provası, kabul/güvenlik doğrulaması | Gerçek kurumların canlı kullanımı |
| Yaşam döngüsü | Yerel ve gerektiğinde yeniden kurulabilir | Varsayılan geçici; yayın penceresinde açılır | Gerçek pilot başlayınca kalıcı |
| Backend | Yerel Docker/Java; gerekirse ücretsiz alfa servisi | Onaylı image digest ile geçici ücretli servis | Ölçülmüş ücretli Docker servisi |
| Veritabanı | Yerel PostgreSQL/Testcontainers | Ayrı sentetik PostgreSQL; geçici olabilir | Duraklamayan ücretli PostgreSQL; pilotta günlük yedek, kritik profilde gerekirse PITR |
| Veri | Yalnız sentetik; kişisel gerçek veri yasak | Yalnız sentetik; üretimden kopya/maskeleme ile aktarım yasak | Yetkili gerçek veri |
| Dağıtım kaynağı | `main` üzerindeki başarılı CI sonrası onaylı deploy | Development'ta doğrulanan değişmez imaj özeti | Staging'de kabul edilen aynı imaj özeti |
| Dağıtım onayı | Geliştirici/CI sahibi | Release sorumlusu | Ürün sahibi veya açıkça yetkilendirdiği kişi |
| Migration | Geriye uyumluluk ve sentetik veriyle ilk uygulama | Production öncesi aynı sırayla prova | Yedek uygunluğu ve staging kanıtından sonra kontrollü uygulama |
| Kimlik/issuer | Development'a özel | Staging'e özel | Production'a özel |
| API tabanı | Development'a özel HTTPS URL | Staging'e özel HTTPS URL | Production'a özel HTTPS URL ve onaylı alan adı |
| Log/telemetri | Sentetik veri; hassas içerik yok | Sentetik veri; production ile ayrı proje/akış | Sınırlı erişim ve üretime özel proje/akış |
| Erişim | Adlandırılmış geliştiriciler, en az yetki | Release/test sorumluları, en az yetki | En dar operasyon grubu; acil erişim izli ve süreli |
| Sağlayıcı yönetişimi | Kişisel yerel erişim | Production'dan ayrı kimlik ve secret | Kritik profilde isolated/protected; pilotta en dar erişim ve manuel onay |

Ortam adları kaynak, servis, alarm, dashboard, secret ve loglarda tam yazılır. `prod`/`test`
gibi bağlama göre değişen tek başına kısaltmalar erişim kararı için kullanılmaz.

Önerilen kaynak adları:

```text
kurs-platform-api-development
kurs-platform-api-staging
kurs-platform-api-production

kurs-platform-db-development
kurs-platform-db-staging
kurs-platform-db-production
```

Gerçek sağlayıcı proje kimlikleri ve URL'leri secret değildir; yine de uygulama kodunda sabit
yazılmaz. Ortam eşlemesi A-013'ün dış yapılandırma iskeletiyle yapılır.

## 5. Yerel geliştirme ve otomatik test sınırı

- Geliştirici, `development` profilindeki backend'i yerel Docker container veya Java süreci
  olarak çalıştırabilir. Yerel süreç yalnız development kaynaklarına bağlanabilir.
- Paylaşılan development veritabanı için migration sahibi veya production rolü kullanılmaz.
  Yerel geliştirici runtime yetkisi en az yetkilidir.
- Birim testleri dış servise bağlanmaz. Repository, transaction, migration ve kurum izolasyonu
  testleri A-002 uyarınca geçici PostgreSQL Testcontainers kullanır. Container kapandığında
  test verisi silinebilir; bu, development/staging/production ortamlarından biri değildir.
- Test fixture'ları sentetiktir. Gerçek ad, telefon, adres, token, PDF, log veya veritabanı
  dump'ı fixture ya da hata eki yapılmaz.
- Bir geliştiricinin yerel `.env`, IDE ayarı, sertifika dosyası veya CLI oturumu repoya
  eklenmez. Örnek yapılandırma yalnız sahte değer ve değişken adını gösterebilir.

## 6. Veritabanı bağlantı sözleşmesi

### 6.1. Runtime bağlantısı

Üç backend runtime'ı için başlangıç bağlantısı:

- Supavisor shared pooler **session mode**,
- port `5432`,
- ortamın kendi `app_runtime`/ilgili en az yetkili rolü,
- SSL enforcement açık,
- `sslmode=verify-full` eşdeğeri CA ve hostname doğrulaması,
- ilgili Supabase projesinden indirilen CA sertifikası,
- uygulama tarafında sınırlandırılmış bağlantı havuzu.

Session pooler kalıcı Spring backend ile uyumludur ve IPv4 yolu sağlar. Transaction pooler
portu `6543` kullanılmaz; prepared statement ve transaction-scope tenant bağlamı davranışı bu
topolojide gereksiz risk oluşturur. IPv4 eklentisi başlangıç bütçesine eklenmez.

Direct bağlantıya geçiş ancak seçilen Render servisi için IPv6 erişimi, TLS `verify-full`,
bağlantı kopması/yeniden bağlanma ve kurum izolasyon testleri staging'de kanıtlanırsa yapılabilir.
Bu değişiklik maliyet veya güvenilirlik etkisi taşıyorsa karar kaydı güncellenir.

### 6.2. Migration, dump ve restore bağlantısı

- Migration, `pg_dump` ve `pg_restore` runtime bağlantı havuzundan ve runtime rolünden ayrıdır.
- Bu işlemlerde direct endpoint tercih edilir. Çalıştırıcı IPv6 sunmuyorsa production'a
  sessizce transaction pooler ile bağlanılmaz; kısa ömürlü IPv6 uyumlu çalıştırıcı veya onaylı
  IPv4 eklentisi kullanılır.
- Migration sahibi yalnız CI/CD'nin kısa ömürlü bağlamında bulunur; production uygulama
  sürecine verilmez.
- Migration aracı ve secret mekanizması sırasıyla ilgili sonraki teknik görevlerde seçilir;
  bu belge araç adı uydurmaz.

### 6.3. Her açılışta bağlantı kanıtı

Her ortam için aşağıdaki kanıtlar ayrı saklanır:

1. hedef host/port ve seçilen bağlantı modunun adı,
2. CA ve hostname doğrulayan TLS bağlantısının başarılı olması,
3. TLS doğrulaması kapalı veya yanlış CA ile bağlantının reddedilmesi,
4. runtime rolünün owner, superuser veya `BYPASSRLS` olmaması,
5. aynı bağlantı havuzunda ardışık iki transaction'ın farklı kurum bağlamlarını sızdırmaması,
6. iki kurumun birbirinin satırını okuyamaması/yazamaması,
7. platform yöneticisi istisnasının hedef kurumla sınırlanması ve audit üretmesi,
8. pool yeniden bağlanma sonrasında health/readiness davranışı.

İlk gerçek şema ve roller henüz oluşturulmadığı için bu kanıtlar A-010'da çalıştırılmış sayılmaz;
ilgili migration ve uygulama görevlerinin açılış kapısıdır.

## 7. Supabase proje açılış kapısı

Her Supabase projesi diğerinden bağımsız açılır. Production ve staging için aşağıdakilerin
tamamı zorunludur; development'ta da aynı güvenlik sınırı korunur:

- Bölge `eu-central-1` (Frankfurt).
- Uygulama şeması `app`; `public` içinde uygulama tablosu, view, function veya RPC yok.
- Dashboard'da Data API → **Enable Data API kapalı**.
- `app`, “Exposed schemas” listesinde yok.
- Proje oluştururken “Automatically expose new tables and functions” kapalı.
- Dışarıdan auto-generated REST ve GraphQL isteği yanıt vermiyor.
- `anon`, `authenticated`, `service_role` ve `PUBLIC` için A-003'teki mevcut/default grant
  iptalleri migration testiyle doğrulanmış.
- Runtime ile migration rolleri ayrı; runtime owner/superuser/`BYPASSRLS` değil.
- Tenant tablolarda `ENABLE ROW LEVEL SECURITY` ve `FORCE ROW LEVEL SECURITY` etkin.
- Production'da ücretsiz veya otomatik askıya alınan plan kullanılmaz. Gerçek kurum pilotunda
  duraklamayan ücretli plan, sağlayıcı günlük yedeği ve bağımsız şifreli mantıksal dump
  zorunludur; Small compute ve PITR zorunlu değildir. Small+ ve PITR yalnız §11'deki kritik
  production tetikleri oluştuğunda açılır.

Data API anahtarı ve exposed schema listesi SQL migration tarafından kanıtlanamaz. Açılış kanıtı
Dashboard ekran kaydı veya Management API çıktısı ile; dış REST/GraphQL negatif testi ayrıca
saklanır. Ekran kaydında token, parola, bağlantı dizesi veya kişisel veri görünemez.

## 8. Yapıt, dağıtım ve terfi akışı

```text
pull request
  → zorunlu CI kontrolleri
  → main merge
  → tek, değişmez Docker imajı + digest + commit SHA
  → registry digest erişilebilirlik + manifest/checksum doğrulaması
  → development deploy ve smoke test
  → registry digest doğrulamasını tekrarla
  → aynı digest ile staging deploy
  → migration/izolasyon/kabul kanıtı
  → production ve rollback digest'lerini GC'den koru + registry doğrulamasını tekrarla
  → yetkili production onayı
  → aynı digest ile production deploy
  → smoke test ve gözlem penceresi
```

- Git branch'i veya mutable image tag release ya da rollback kimliği değildir. Mutable tag
  geçmişteki başka bir manifesti gösterebilir. Commit SHA ve değişmez image digest birlikte
  kaydedilir; deploy ve rollback digest ile adreslenir.
- Staging ve production kaynak koddan ayrı ayrı build edilmez. Production'da çalışan digest,
  staging'de kabul edilene eşit olmalıdır.
- Development, staging ve production terfilerinin her birinden hemen önce registry kimlik
  bilgileriyle digest'in çekilebilir olduğu ve çözümlenen manifest/checksum değerinin release
  kaydıyla bire bir eşleştiği doğrulanır. Eşleşme veya erişim yoksa adım **fail-closed** durur.
- Kabul edilmiş production digest'i ve en az bir önceki başarıyla deploy edilmiş/doğrulanmış
  rollback digest'i, sayısal süresi release öncesinde kaydedilen rollback penceresi bitene kadar
  silme ve registry garbage collection'dan korunur. A-012 registry'yi ve pencerenin süresini
  seçer; süre tanımlanmadan veya iki digest için retention kanıtlanmadan production deploy
  edilemez.
- Registry'nin kendi retention kuralı, lifecycle policy'si veya manuel silme yetkisi bu korumayı
  aşamaz. Koruma yalnız tag tutmakla kanıtlanamaz; digest/manifest varlığı ayrıca sorgulanır.
- Render otomatik deploy'u production için kapalıdır. Development otomasyonu dahi A-012'nin
  zorunlu CI kontrolleri olmadan etkinleştirilmez.
- Health check başarısızsa yeni deploy trafik almaz. Eski sağlıklı sürüm çalışmaya devam eder.
- Uygulama deploy'u sırasında schema migration controller başlangıcında otomatik çalışmaz.
  Migration ayrı, gözlemlenebilir ve başarısızlıkta deploy'u durduran adımdır.
- Production migration'ı önce staging'de aynı sırayla denenir. Geriye uyumlu genişlet/daralt
  yaklaşımı gerektiren şema değişiklikleri tek kesintili adımda birleştirilmez.
- Production deploy sonrası smoke test; public API TLS'i, sürüm/health, kimlik doğrulama reddi,
  yetkili düşük riskli okuma ve veritabanı erişimini kapsar. Gerçek kişisel veriyi test
  çıktısına yazmaz.

## 9. Geri alma ve başarısızlık davranışı

- Uygulama geri alma, release kaydındaki bir önceki doğrulanmış digest'e dönerek yapılır. Kaynak
  dalı yeniden build etmek veya eski mutable tag'i yeniden kullanmak geri alma sayılmaz.
- Rollback başlamadan önce hedef digest'in registry'de erişilebilirliği, manifest/checksum
  eşleşmesi ve Render pull kimlik bilgilerinin geçerliliği yeniden doğrulanır. Render prebuilt
  image rollback sırasında imajı registry'den tekrar çeker; yerel cache veya önceki başarılı
  deploy, imajın hâlâ erişilebilir olduğunu kanıtlamaz.
- Hedef digest bulunamaz, registry erişilemez veya manifest/checksum eşleşmezse rollback başarılı
  ya da başlatılmış sayılmaz. Deploy bloke edilir, mevcut sağlıklı örnek korunur ve olay müdahale
  prosedürüne geçilir; mutable tag'e, yeniden build'e veya başka digest'e sessiz fallback yoktur.
- Uygulama rollback'i migration'ı otomatik geri almaz. Veri kaybettirebilecek down migration
  production olay müdahalesinde çalıştırılmaz; ileri yönlü düzeltme veya onaylı restore planı
  uygulanır.
- Migration başarısızsa yeni uygulama trafiğe alınmaz ve başarısız migration başarılı gibi
  işaretlenmez.
- Production veritabanı geri yükleme mevcut proje üstüne doğrudan yapılmaz. A-003 ve OPS-002
  uyarınca yeni Supabase projesine restore edilir; roller, parolalar, Data API ayarı, grant'ler,
  RLS ve ortam secret'ları yeniden doğrulandıktan sonra onaylı kesme planı uygulanır.
- Olay sırasında production verisini staging'e kopyalayarak hata ayıklamak yasaktır.

## 10. Veri, erişim ve secret ayrımı

### 10.1. Veri sınırı

- Ortamlar arasında veritabanı replikasyonu, dump aktarımı veya ortak bucket yoktur.
- Production kaynaklı dump, snapshot, PITR clone, log veya export development/staging girdisi
  olamaz. “Anonimleştirme” bu görevde onaylı ve kanıtlanmış bir süreç değildir; bu nedenle
  üretimden türetilmiş veri alt ortamlarda kullanılmaz.
- Sentetik veri üreticileri gerçek telefon/e-posta aralıklarını, gerçek kişi adlarını veya
  production kimliklerini kullanmaz.
- Ortam sıfırlama arşivleme ürün kuralının istisnası değildir: yalnız sentetik alt ortam veri
  seti, ortam sahibi onayıyla yeniden kurulabilir. Production'da normal ürün kayıtları fiziksel
  silinmez.

### 10.2. İnsan ve makine erişimi

- Tek bir paylaşılan insan hesabı veya veritabanı parolası kullanılmaz.
- Sağlayıcı dashboard erişimi adlandırılmış kimlik, MFA ve en az yetkiyle verilir.
- Development yetkisi staging veya production yetkisi doğurmaz.
- Production erişimi görev gerektirdiği süre ve yüzeyle sınırlıdır; erişim verme/geri alma
  kayıt altına alınır.
- Uygulama runtime, migration, yedekleme ve insan destek kimlikleri ayrıdır.
- Platform yöneticisinin ürün içi kurum verisine erişimi uygulama audit'i üretir; sağlayıcı
  dashboard erişim kaydı bunun yerine geçmez.

### 10.3. Secret sınırı

- Her ortamın veritabanı parolası, OIDC istemcisi, imzalama/doğrulama malzemesi, dış servis
  anahtarı, registry pull kimliği ve sertifika referansı farklıdır.
- Secret değeri repoya, Docker image katmanına, build argümanına, mobil pakete, loga veya PR
  açıklamasına yazılmaz.
- Production secret'ı development/staging'e kopyalanmaz.
- Secret deposu, değişken adları, rotasyon ve kırılma prosedürü A-013 kapsamındadır. Bu belge
  belirli bir secret ürünü seçmez.

## 11. Yedekleme ve veri koruma kapısı

- Sentetik kapalı alfa best-effort çalışabilir; yedek kaybı gerçek kişi verisi kaybı
  oluşturmamalıdır.
- Gerçek kurum pilotunda duraklamayan ücretli PostgreSQL, sağlayıcı otomatik günlük yedeği ve
  bağımsız şifreli mantıksal dump zorunludur. GitHub Actions gibi tek bir zamanlanmış CI işi
  tek production yedeği sayılamaz; başarısızlık alarm üretir.
- PITR ve en az Small compute, günlük veri kaybının elle yeniden girilemez olduğu veya kabul
  edilmiş RPO/RTO bulunduğu kritik production profilinde açılır. Bu profilde tasarım hedefi en
  kötü durumda 2 dakika RPO, 4 saat RTO'dur ve OPS-002 tatbikatında ölçülür.
- PITR'a ek şifreli mantıksal `pg_dump`, production dışında erişimi kısıtlı bir depoya yazılır.
  Şifreleme anahtarı veritabanı ve yedekle aynı erişim düzleminde tutulmaz.
- Hukukî veri saklama/imha değerlendirmesi tamamlanmadığından mantıksal yedeğin sıklığı ve
  saklama süresi bu görevde uydurulmamıştır. Ürün sahibi/hukuk onayı ve OPS-001 uygulaması
  olmadan production gerçek veriye açılamaz.
- Yedek başarısı restore başarısı sayılmaz. OPS-002, yeni projeye restore'u, özel rollerin ve
  parolaların yeniden oluşturulmasını, migration sürümünü, Data API ayarını, grant/RLS
  sınırlarını ve iki kurum izolasyonunu kanıtlar. Bu tatbikat başarıyla geçmeden ilk gerçek
  kurum/öğrenci verisi alınamaz; yalnız tatbikat tarihi planlamak yeterli değildir.

## 12. Maliyet tabanı ve onay eşiği

Fiyatlar 15 Temmuz 2026 tarihinde sağlayıcıların resmî fiyat/ürün belgelerinden yeniden
kontrol edilmiştir. Vergi, egress aşımı, domain, log drain, artifact registry, dedike IP,
mantıksal yedek deposu, restore sırasında geçici proje ve destek planı dahil değildir.

### 12.1. A-003 veritabanı karşılaştırmasının güncel tekrarı

Varsayım: Frankfurt'ta tek production PostgreSQL, 730 saat, yaklaşık 1 GB RAM sınıfı, 20 GB
veri, tek-AZ/tekil düğüm; uygulama barındırma ve staging hariç.

| Sağlayıcı | 15 Temmuz 2026 hesap bileşenleri | Aylık tahmin | Not |
|---|---|---:|---|
| Supabase | Pro 25 USD + Small 15 USD + 7 gün PITR 100 USD − 10 USD compute kredisi | **yaklaşık 130 USD** | Kritik-production karşılaştırma tabanı; 2 dakika RPO belgesi vardır |
| AWS RDS PostgreSQL | `db.t4g.micro` 0,019 USD/saat × 730 + 20 GB gp3 × 0,137 USD | **yaklaşık 16,61 USD + egress/ek yedek** | AWS fiyat kataloğu anlık hesabı; 2 dakika RPO eşdeğerliği iddia edilmez |
| Render PostgreSQL | Pro workspace 25 USD + Basic-1gb 19 USD + 20 GB genişletilebilir SSD 6 USD | **yaklaşık 50 USD** | 7 gün PITR vardır; 2 dakika RPO taahhüdü kaynakta doğrulanmadı |

Fiyat tek başına karar ölçütü değildir. Supabase'in yönetilen pilot adayı olarak korunması;
tam PostgreSQL, Frankfurt seçeneği, RLS, gerektiğinde PITR ve düşük ilk işletim yükünün birlikte
değerlendirilmesine dayanır. Kesin provisioning kararı ilgili kademe açılırken tekrar doğrulanır.

### 12.2. Kademeli çalışma bütçesi

| Kademe | Kullanım | Hedef dış ödeme | Zorunlu sınır |
|---|---|---:|---|
| Yerel geliştirme | Tek geliştirici, sentetik veri | `0 USD/ay` | Yerel Docker/PostgreSQL, gerçek veri yok |
| Kapalı alfa | 0–10 davetli gerçek test kullanıcısı; bütün ürün verisi sentetik, SLA yok | hedef `0 USD/ay` | Ücretsiz/öğrenci kredili kaynak; soğuk başlangıç ve kota görünür |
| Gerçek kurum pilotu | Düzenli kullanım ve gerçek öğrenci verisi | hedef `25–60 USD/ay` | Ücretli duraklamayan DB/backend, günlük yedek, restore kanıtı |
| Aktif/kritik ürün | Birden fazla kurum veya kabul edilmiş RPO/RTO | ihtiyaca göre | Sürekli staging, PITR, Pro yönetişim ve HA yalnız tetikle |

`214 USD/ay` önceki üç sürekli ortam + PITR örnek toplamıdır; başlangıç zorunluluğu değildir,
kritik-production karşılaştırma referansı olarak korunur. Keycloak fallback'i seçilirse ayrı
compute/DB/yedek/izleme maliyeti bu toplama eklenmeden onay verilemez.

Ücretli profile geçiş yalnız kullanıcı sayısına bağlanmaz. İlk kurumun gerçek veriyi günlük
kullanması, veri kaybının elle yeniden girilemez olması, ücretsiz kotanın `%60–70`ine ulaşma,
soğuk başlangıcın sync'i bozması, ikinci operasyon yetkilisi, sürekli staging/audit ihtiyacı
veya öğrenci kredisinin bitimine 60 gün kalması tetiklerden herhangi biridir.

## 13. Ortam terfi kontrol listeleri

### 13.1. Development açılışı

- [ ] Yerel kaynak veya seçilmiş geçici alfa sağlayıcısı doğru profil ve bölgede.
- [ ] Ücretsiz/öğrenci kredili kaynağın kota, bitiş ve sahiplik sınırı kaydedilmiş.
- [ ] Yalnız development secret'ları ve sentetik veri var.
- [ ] Supabase kullanılıyorsa Data API kapalı; `app` exposed değil; dış REST/GraphQL negatif
      testi geçiyor. Yerel standart PostgreSQL profilinde bu kontrol uygulanamaz olarak kaydediliyor.
- [ ] Uzak Supabase kullanılıyorsa session pooler + `verify-full` bağlantısı geçiyor ve yanlış
      CA testi reddediliyor; yerel profilde standart PostgreSQL TLS/bağlantı kontrolü kullanılıyor.
- [ ] Runtime rolü en az yetkili ve migration rolünden ayrı.
- [ ] Health/readiness, graceful shutdown ve yeniden deploy smoke testi geçiyor.

### 13.2. Staging terfisi

- [ ] Development'ta doğrulanan commit SHA ve image digest değişmeden kullanılıyor.
- [ ] Digest registry'de erişilebilir; manifest/checksum release kaydıyla eşleşiyor.
- [ ] Staging secret, issuer, API URL ve veritabanı development/production'dan farklı.
- [ ] Bütün migration'lar temiz staging veritabanında aynı sırayla geçiyor.
- [ ] Kurum izolasyonu, yetkisiz erişim, idempotency ve transaction bağlam testleri geçiyor.
- [ ] Data API/dashboard açılış kanıtı ve dış negatif test saklandı.
- [ ] Production migration ve rollback/ileri düzeltme planı gözden geçirildi.

### 13.3. Production terfisi

- [ ] Staging'de kabul edilen image digest bire bir aynı.
- [ ] Production digest'i ve en az bir önceki doğrulanmış rollback digest'i registry'de
      erişilebilir; manifest/checksum eşleşiyor ve rollback penceresi boyunca GC korumasında.
- [ ] Yetkili insan onayı kaydedildi; doğrudan `main` auto-deploy kapalı.
- [ ] Pilot profilde günlük yedek; kritik profilde gerekiyorsa Small+/PITR uygunluğu doğrulandı.
- [ ] Hukukî saklama/imha kararı ve OPS-001 production açılış ön koşulu tamamlandı.
- [ ] OPS-002 restore tatbikatı ilk gerçek kurum/öğrenci verisi alınmadan önce başarıyla geçti;
      kullanılan yedek türü, ölçülen RPO/RTO ve güvenlik kontrolleri kanıt olarak saklandı.
- [ ] Data API kapalı, `app` non-exposed ve dış REST/GraphQL negatif testi geçiyor.
- [ ] TLS, runtime rolü, FORCE RLS ve iki kurum izolasyon testleri geçiyor.
- [ ] Migration staging'de denenmiş ve production yedeği/ileri düzeltme planı hazır.
- [ ] Deploy sonrası smoke test, gözlem penceresi ve rollback sorumlusu belli.
- [ ] Aylık toplam maliyet ürün sahibi tarafından yazılı onaylandı.

Kontrol maddelerinden biri başarısız veya kanıtsızsa ortam bir üst aşamaya terfi ettirilmez.
Sunucu/provider panelinin “başarılı” etiketi ürün kabulü yerine geçmez.

## 14. Sorumluluklar ve sonraki görev sınırları

| Alan | Sahip görev | A-010'un kararı |
|---|---|---|
| Fiziksel repo/app iskeleti, Dockerfile | A-011 | Bu sözleşmedeki Docker/stateless/runtime sınırını uygular |
| CI kalite, registry ve deploy kapıları | A-012 | Registry/pencere seçimini; tek build, digest/manifest kaydı, retention ve fail-closed terfi/rollback kontrollerini uygular |
| Ortam değişkeni ve secret iskeleti | A-013 | PLAN-005 ve A-004R3 sonrası ortam ayrımı, secret sızıntısı yasağı ve rotasyon yüzeyini uygular |
| Loglama, izleme ve maliyet alarmı | A-014 | Ortam ayrımı ve hassas veri minimizasyonuyla eşikleri kurar |
| Migration aracı/yürütme modeli | Ayrı onaylı teknik karar/uygulama görevi | Runtime ve migration kimliğini ayırmak zorundadır |
| Production yedek ayarı | OPS-001 | Pilotta günlük yedek + şifreli dump; kritik profilde tetiklenirse PITR uygular |
| Restore tatbikatı | OPS-002 | Yeni projeye restore ve bütün güvenlik kontrollerini ölçer |

## 15. Kapsam dışı ve açık kararlar

- Bu görev sağlayıcı hesabı veya gerçek ortam oluşturmaz; para harcamaz.
- Özel alan adı, DNS sağlayıcısı, WAF/CDN, oran sınırlama ürünü ve DDoS ek planı seçmez.
- Migration aracını, ORM'i, container registry sağlayıcısını veya secret ürününü seçmez.
  Registry seçimi A-012'ye bırakılmıştır; digest retention, erişilebilirlik, manifest/checksum
  doğrulaması ve fail-closed rollback bu görevde bağlayıcıdır.
- Log/hata izleme sağlayıcısı ve alarm eşikleri A-014 kapsamındadır.
- Dosya/PDF depolama A-007; Excel üretme yaklaşımı A-008 kapsamındadır.
- Kimlik sağlayıcısı A-004, gerçek zaman kanalı A-006 sözleşmelerine tabidir; bu belge onları
  yeniden tasarlamaz.
- Çok bölgeli çalışma, aktif/pasif felaket kurtarma, replica, Kubernetes ve mikroservis V1
  ortam topolojisine eklenmez.
- Mantıksal yedek saklama süresi hukukî değerlendirme olmadan belirlenmemiştir. Bu açık nokta
  production gerçek veri açılış kapısında bloke edicidir; geliştirme/staging sözleşmesini
  belirsiz bırakmaz.

## 16. Kabul ve doğrulama matrisi

| Kabul ölçütü | Sonuç |
|---|---|
| Development, staging ve production kaynak/veri/erişim sınırları ayrıdır. | Karşılandı |
| Production verisinin geliştirici cihazına veya alt ortama kopyalanması yasaktır. | Karşılandı |
| A-002'nin Java 21/Spring Boot tek deploy edilen modüler monolit kararı korunur. | Karşılandı |
| Docker/stateless runtime korunmuş; ücretsiz alfa ve ücretli pilot sağlayıcıları deney/ölçüm kapısına bağlanmıştır. | Karşılandı |
| Açılan Supabase projelerinde Data API/non-exposed schema ve runtime/migration rol sınırları ortam kapısına bağlanmıştır. | Karşılandı |
| IPv4/IPv6, session pooler, TLS `verify-full`, prepared statement ve transaction tenant bağlamı kararı nettir. | Karşılandı |
| Aynı değişmez yapıtın ortamlar arasında onayla terfisi ve production rollback davranışı tanımlıdır. | Karşılandı |
| Production ve önceki rollback digest'inin retention, erişilebilirlik, manifest/checksum ve fail-closed kapıları tanımlıdır. | Karşılandı |
| Migration, yedek, restore, kurum izolasyonu ve negatif erişim kontrollerinin sahipleri bellidir. | Karşılandı |
| A-003'ün üç veritabanı maliyet satırı güncel aynı varsayımlarla tekrar hesaplanmıştır. | Karşılandı |
| Yerel, kapalı alfa, gerçek pilot ve kritik production maliyet profilleri görünürdür. | Karşılandı |
| Render Pro, sürekli staging ve PITR başlangıç zorunluluğu olmaktan çıkarılıp ölçülmüş tetiklere bağlanmıştır. | Karşılandı |
| Gerçek ortam/secret/migration kurulumu veya başka görev kapsamı erkenden uygulanmamıştır. | Karşılandı |
| Bilinmeyen hukukî saklama süresi uydurulmamış ve production kapısında açıkça bloklanmıştır. | Karşılandı |

Bu görev belge kararıdır. Sağlayıcı hesapları ve uygulama iskeleti henüz kurulmadığından
çalıştırılabilir ortam testleri A-010 kapsamında yapılmış gibi gösterilmez. Kontrol listeleri,
ilgili uygulama görevlerinin ölçülebilir kabul kapılarıdır.

## 17. Resmî kaynaklar

Kaynak özellikleri ve fiyatları zamanla değişebilir; satın alma ve production açılışı öncesinde
aynı varsayımlarla yeniden doğrulanır.

- Render, [Web Services](https://render.com/docs/web-services) — Docker runtime, port, health
  check ve web service davranışı.
- Render, [Regions](https://render.com/docs/regions) — Frankfurt bölgesi ve sağlayıcılar arası
  bağlantıda özel ağ varsayılmaması.
- Render, [Deploying on Render](https://render.com/docs/deploys) ve
  [Rollbacks](https://render.com/docs/rollbacks) — health-gated deploy, graceful shutdown ve
  registry-hosted digest/tag geri alma davranışı.
- Render, [Deploy a Prebuilt Docker Image](https://render.com/docs/deploying-an-image) — her
  deploy/rollback için registry erişilebilirliği ve digest gereksinimi.
- Render, [Platform Features by Plan](https://render.com/docs/platform-features-by-plan),
  [Workspaces, Members, and Roles](https://render.com/docs/team-members),
  [Audit Logs](https://render.com/docs/audit-logs) ve [Projects and Environments](https://render.com/docs/projects)
  — Pro/Hobby ekip, audit, isolated/protected environment sınırları.
- Render, [Pricing](https://render.com/pricing) — 25 USD Pro workspace, backend ve alternatif
  PostgreSQL başlangıç fiyatları ile Free/Starter compute sınırları.
- Heroku, [öğrenci kredisi](https://www.heroku.com/students/) ve
  [bölgeler](https://devcenter.heroku.com/articles/regions) — 24 aylık kredi sınırı, Common
  Runtime `eu` bölgesinin kesin Frankfurt olmaması.
- Supabase, [Connect to your database](https://supabase.com/docs/guides/database/connecting-to-postgres)
  — direct/session/transaction bağlantı seçimi, IPv4/IPv6 ve portlar.
- Supabase, [Postgres SSL Enforcement](https://supabase.com/docs/guides/platform/ssl-enforcement)
  — CA sertifikası ve `verify-full` doğrulaması.
- Supabase, [Securing your Data API](https://supabase.com/docs/guides/api/securing-your-api)
  — Data API kapatma ve grant/exposed schema sınırları.
- Supabase, [Database Backups](https://supabase.com/docs/guides/platform/backups) ve
  [Pricing](https://supabase.com/pricing) — Small/PITR koşulu, 7 günlük fiyat ve compute
  tabanı.
- Supabase, [Database backup features](https://supabase.com/features/database-backups) — en
  kötü durumda iki dakikalık RPO beyanı.
- GitHub, [zamanlanmış workflow davranışı](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/disable-and-enable-workflows) — public repoda 60 gün hareketsizlikte zamanlanmış işin kapanabilmesi; tek production yedeği sayılmamasının gerekçesi.
- AWS, [RDS for PostgreSQL pricing](https://aws.amazon.com/rds/postgresql/pricing/) ve
  [eu-central-1 fiyat kataloğu](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/current/eu-central-1/index.json)
  — 15 Temmuz 2026 `db.t4g.micro` ve gp3 hesap girdileri.
- AWS, [App Runner bölgeleri](https://docs.aws.amazon.com/general/latest/gr/apprunner.html) —
  Frankfurt desteği.
- Fly.io, [Regions](https://fly.io/docs/reference/regions/) ve
  [Resource Pricing](https://fly.io/docs/about/pricing/) — Frankfurt alternatifi ve runtime
  maliyet modeli.
