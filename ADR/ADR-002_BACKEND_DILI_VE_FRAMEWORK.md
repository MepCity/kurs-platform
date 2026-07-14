# ADR-002 — Backend dili ve framework seçimi

| Alan | Değer |
|---|---|
| Durum | Önerildi — inceleme bekliyor |
| Tarih | 14 Temmuz 2026 |
| Görev | A-002 — Backend dili ve framework ADR'si |
| Karar sahipliği | Dalga 1 teknik kararları |
| Bağımlılıklar | `VERI_MODELI.md` (P-008), `API_GENEL_KURALLARI.md` (P-009) |

## Bağlam

İlk backend; sürümlenmiş HTTP API sunan, sınırları açık bir modüler monolit olacaktır.
Kurum kapsamı, rol/sınıf yetkisi, idempotency, `rowVersion`, denetim kaydı ve eşitleme
değişiklikleri aynı iş işlemi içinde güvenilir biçimde yürütülmelidir. İş kuralları mobil
istemciye, veritabanı tetikleyicilerine veya modüller arası doğrudan veri erişimine dağılmaz.

Bu ADR yalnızca backend programlama dili, çalışma zamanı ve ana web uygulama frameworkünü
önerir. Veritabanı/hosting (`A-003`), kimlik sağlayıcısı (`A-004`), gerçek zamanlı kanal
(`A-006`), loglama/izleme (`A-014`), ORM ve migration aracı burada seçilmez.

## Karar sürücüleri

1. Kurum izolasyonu ve yetki, her istekte sunucuda doğrulanabilmelidir.
2. Çok kayıtlı yazma, idempotency sonucu, denetim kaydı ve eşitleme değişikliği tek
   transaction içinde atomik olabilmelidir.
3. PostgreSQL yönünde tasarlanan ilişkisel kısıtlar ve sürümlü migration yaklaşımıyla
   uyumlu olmalıdır; `A-003` tamamlanana kadar belirli bir sürücü veya sağlayıcıya
   bağlanmamalıdır.
4. Birim, repository/transaction, API yetki-izolasyon ve uçtan uca test seviyelerini
   desteklemelidir.
5. İlk sürümde tek deploy edilen uygulama sağlamalı; erken mikroservis veya zorunlu reaktif
   programlama getirmemelidir.
6. TLS, dış yapılandırma, health/metrics, güvenli hata işleme ve dağıtılabilir paket için
   olgun üretim desteği sağlamalıdır.

## Ağırlıklı karar matrisi

Ekip deneyimi hakkında doğrulanmış veri yoktur; bu nedenle **ekip deneyimi** ölçütü bütün
seçeneklerde nötr `3/5` kabul edilmiştir. Puanlar `1` (zayıf) ile `5` (çok güçlü)
arasındadır; ağırlıklı puan, `puan × ağırlık` toplamıdır. Ağırlıklar toplamı 100'dür.

| Ölçüt | Ağırlık | Değerlendirme sorusu |
|---|---:|---|
| Sözleşme ve modüler monolit uyumu | 30 | P-009 HTTP sözleşmesini ve modül sınırlarını açık biçimde taşıyabilir mi? |
| İlişkisel transaction/bütünlük | 25 | Atomik yazma, idempotency, audit ve migration yönü için olgun destek sunuyor mu? |
| Güvenlik ve test edilebilirlik | 20 | Sunucu tarafı yetki/izolasyon ve gerekli test seviyeleri için olgun araçlar var mı? |
| Operasyonel olgunluk | 15 | Yapılandırma, health/metrics ve tek uygulama dağıtımı olgun mu? |
| Ekip deneyimi | 10 | Belgelenmiş ekip deneyimi var mı? Bilinmiyor; tüm seçeneklerde nötr `3` kullanılır. |

| Seçenek | Sözleşme 30 | Transaction 25 | Güvenlik/test 20 | Operasyon 15 | Ekip 10 | Toplam / 500 |
|---|---:|---:|---:|---:|---:|---:|
| Java 21 + Spring Boot 4.1 + Spring MVC | 5 | 5 | 5 | 5 | 3 | **480** |
| Kotlin + Spring Boot + Spring MVC | 5 | 5 | 5 | 4 | 3 | **465** |
| .NET + ASP.NET Core | 5 | 5 | 5 | 4 | 3 | **465** |
| TypeScript + NestJS | 4 | 4 | 4 | 4 | 3 | **390** |
| Go + HTTP framework | 3 | 3 | 3 | 5 | 3 | **330** |

Java/Spring seçeneği, ekosistem kararlarını artırmadan transaction, güvenlik, test ve
operasyon ihtiyaçlarını birlikte karşılayan en yüksek puanı alır. Kotlin/Spring ve .NET
yakın alternatiflerdir; ekip deneyimi doğrulandığında veya A-003/A-004 sonuçlarıyla çelişki
ortaya çıktığında bu ADR yeniden değerlendirilir.

## Önerilen karar

İnceleme sonucunda kabul edilmek üzere backend için **Java 21 LTS** üzerinde **Spring Boot
4.1.x** önerilir. İlk HTTP taşıması **Spring MVC** olacaktır; uygulama tek deploy edilen bir
modüler monolit olarak paketlenir. Spring Boot sürümü, scaffold sırasında 4.1 sürüm hattının
güncel GA yamasıyla pinlenir; önizleme/snapshot sürümü kullanılmaz.

Varsayılan istek modeli bloklayıcı Spring MVC'dir. Bu, reaktif taşımanın yasaklandığı
anlamına gelmez; WebFlux/R2DBC ancak ölçülmüş bir ihtiyaç, ayrı ADR ve mevcut transaction,
izolasyon, idempotency ve gözlemlenebilirlik kurallarına eşdeğer kanıtla eklenebilir.

## Bağlayıcı uygulama ilkeleri

- API kökü, hata zarfı, cursor, `X-Request-Id`, `Idempotency-Key`, `If-Match-Row-Version`
  ve HTTP davranışı `API_GENEL_KURALLARI.md` ile bire bir uyumlu uygulanır.
- Kurum kimliği istemci gövdesi/yol/sorgu iddiasından alınmaz; doğrulanmış token ve yetki
  bağlamından türetilir. Uygulama katmanı DB kısıtlarının yerine geçmez, onları tamamlar.
- Kritik yazma use-case'i iş verisi, idempotency sonucu, denetim kaydı ve varsa sync/outbox
  değişikliğini aynı transaction'da sonuçlandırır. Başarısız işlemde kısmi kalıcılık
  bırakılamaz.
- `@Transactional` sınırı controller'da değil use-case/application service'te tanımlanır.
  Uzak ağ çağrısı veya dosya aktarımı uzun veritabanı transaction'ı içinde yapılmaz.
- Paketleme teknik katmana göre kökten bölünmez; `iam`, `org`, `term`, `cls` gibi feature/domain
  modülleri kök sınırdır. Her modül kendi `api`, `application`, `domain` ve `infrastructure`
  alt yüzeylerini korur. İçe doğru bağımlılık yönü `api → application → domain`dir.
  `infrastructure`, application/domain tarafından tanımlanan portları uygular ve yalnız içe
  doğru bağımlıdır. Application'ın infrastructure import etmesi; domain'in Spring, HTTP,
  persistence veya başka framework türlerine bağımlı olması yasaktır. Composition/configuration
  katmanı adaptörleri portlara bağlar. Modüller arası erişim yalnız yayımlanmış application
  sözleşmesiyle yapılır; başka modülün persistence yüzeyine erişim yasaktır.
- Dependency injection yalnız constructor üzerinden yapılır. Field injection ve controller'ın
  doğrudan repository/persistence nesnesine bağımlılığı yasaktır.
- HTTP request/response DTO'ları ile persistence model/nesneleri ayrı tutulur; persistence
  nesnesi API sözleşmesi olarak döndürülemez veya istemci gövdesinden doğrudan bağlanamaz.
- Girdi doğrulaması DTO sınırında Jakarta Validation ile yapılır. Use-case, DTO doğrulamasına
  güvenmeden kurum kapsamı, yetki, sürüm ve alanlar arası iş kurallarını ayrıca doğrular.
- P-009 hata zarfı merkezi bir hata eşleme katmanından üretilir. Controller veya service
  rastgele HTTP hata gövdesi üretmez; validation, yetki, bulunamama, çakışma ve beklenmeyen
  hata P-009 kodlarına eşlenir ve güvenli `requestId` korunur.
- Spring Security'nin sınırı kimlik doğrulama, token/oturum doğrulama ve doğrulanmış istek
  bağlamını kurmaktır. Kaynak/işlem yetkisi ile kurum ve sınıf kapsamı use-case düzeyinde
  zorunlu olarak değerlendirilir; yalnız endpoint kuralı veya istemci görünürlüğü yeterli
  kabul edilmez.
- API hata yanıtına istisna yığını, SQL ayrıntısı, token veya kişisel veri yazılmaz. İstek
  günlüklerinde P-009'un veri minimizasyonu kuralı uygulanır.
- Mimari modül testleri feature/domain sınırlarını, `api → application → domain` bağımlılık
  yönünü, infrastructure'ın yalnız application/domain portlarını uyguladığını,
  application → infrastructure import yasağını, domain'in Spring/persistence türlerinden
  bağımsızlığını ve DTO–persistence ayrımını doğrular. Composition/configuration katmanının
  adaptör–port bağlantısı dışındaki katmanlar arası bağ kurması da reddedilir. Bu kurallar
  CI'da zorunlu kalite kapısıdır; ihlal test atlanarak kabul edilemez.

## Bağlayıcı test yaklaşımı

- Domain ve application use-case testleri JUnit 5 ile yazılır.
- HTTP controller davranışı, validation ve P-009 merkezi hata eşlemesi MVC slice testleriyle
  doğrulanır; bu testler gerçek persistence'e bağımlı olmaz.
- A-003 veritabanı kararını tamamladıktan sonra repository/transaction, tenant izolasyonu,
  idempotency ve migration entegrasyon testleri PostgreSQL Testcontainers üzerinde çalışır.
  ORM seçimi bu ADR'nin dışında kalır; test yaklaşımı belirli bir ORM'i varsaymaz.
- `KRITIK_TEST_VE_KABUL_PLANI.md`deki KAP kartları uygulanmadıkça PASS sayılmaz.

## Sonuçlar

### Olumlu

- Sürümlü REST API, transaction, validation, test, health/metrics ve dış yapılandırma için
  aynı olgun ekosistemde kalınır.
- Java 21 LTS, üretim çalışma zamanı için kararlı bir temel sunar; Spring Boot 4.1 Java 17+
  gerektirdiğinden seçilen baseline uyumludur.
- Modüler monolit tek süreçte kalırken modül sınırları sonradan korunabilir ve test edilebilir.

### Maliyetler ve risk azaltımı

- Spring bağımlılık grafiği ve sürüm yükseltmeleri bakım ister. Sürümler BOM üzerinden
  yönetilir; güvenlik/bağımlılık güncellemeleri CI kalite kapısına eklenir (`A-012`).
- JVM başlatma ve bellek maliyeti vardır. İlk sürümde ölçüm yapılmadan native image veya
  reaktif mimari eklenmez; performans hedefleri `QA-007`de ölçülür.
- Java null-safety'yi dil düzeyinde Kotlin kadar sağlamaz. Sınır DTO'ları, Jakarta Validation,
  açık nullable kuralları, derleyici/lint kontrolleri ve testlerle korunur.

## Kabul ve doğrulama

| Ölçüt | Sonuç |
|---|---|
| Önerilen dil ve framework, çalışma zamanı ve HTTP modeli nettir. | Karşılandı |
| Alternatifler simetrik, ağırlıklı ve ekip deneyimi nötr varsayımıyla değerlendirilmiştir. | Karşılandı |
| Modüler monolit, sunucu tarafı yetki ve kurum izolasyonu korunur. | Karşılandı |
| P-008 ilişkisel bütünlük/migration yönü ve P-009 HTTP yazma kurallarıyla çelişki yoktur. | Karşılandı |
| Başka ADR'lere ait DB, kimlik, gerçek-zaman, ORM/migration ve gözlemlenebilirlik kararları alınmamıştır. | Karşılandı |
| Test ve atomiklik gereksinimlerinin framework uygulamasına etkisi kaydedilmiştir. | Karşılandı |

## Kaynaklar ve uyum

- Ana ürün planı: `URUN_VE_UYGULAMA_PLANI.md` §3.2, §11.3–11.5, §15, §18, §21.
- Veri modeli: `VERI_MODELI.md` §2, §15 ve §20; PostgreSQL/ORM seçimini bu ADR'ye değil
  A-003 ve sonraki uygulama görevlerine bırakır.
- API kuralları: `API_GENEL_KURALLARI.md` §2–§9.
- Kabul planı: `KRITIK_TEST_VE_KABUL_PLANI.md` §1–§4.
- [Spring Boot 4.1 sistem gereksinimleri](https://docs.spring.io/spring-boot/system-requirements.html)
  — Java 17+ ve desteklenen build araçları.
- [Spring Boot genel bakış](https://docs.spring.io/spring-boot/) — bağımsız uygulama,
  health/metrics ve dış yapılandırma özellikleri.
- [OpenJDK 21](https://openjdk.org/projects/jdk/21/) — Java 21'in LTS sürümü olduğu bilgisi.

## Kapsam dışı ve sonraki adımlar

- A-003 veritabanı/hosting seçimini; A-004 kimlik sağlayıcısını; A-006 gerçek zamanlı
  taşımasını karara bağlar.
- A-009, A-001 ve bu ADR'ye dayanarak yalnız repo/monorepo topolojisini, uygulama sahipliğini
  ve üst seviye sınırları karara bağlar; uygulama veya modül iskeleti oluşturmaz.
- A-011, A-009'un onaylı repo topolojisi içinde backend modül iskeletini ve bu ADR'nin
  bağlayıcı ilkelerini uygular. Bu ADR tek başına uygulama kodu, dependency dosyası veya
  migration eklemez.
