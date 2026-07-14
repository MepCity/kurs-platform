# ADR-009 — Monorepo ve repo yapısı

| Alan | Değer |
|---|---|
| Durum | Kabul edildi |
| Tarih | 15 Temmuz 2026 |
| Görev | A-009 — Monorepo/repo yapısı ADR'si |
| Karar | Tek Git reposu; üretim uygulamaları, deneyler ve repo araçları açık üst seviye sınırlarla ayrılır |
| Bağımlılıklar | `ADR-001-mobil-framework.md` (A-001), `ADR-002_BACKEND_DILI_VE_FRAMEWORK.md` (A-002) |
| İlgili sonraki görev | A-011 — Repo ve modül klasör iskeletini oluştur |

## 1. Bağlam

İlk sürümde iOS ve Android için Flutter uygulaması ile Java 21/Spring Boot 4.1 tabanlı tek
deploy edilen bir backend geliştirilecektir. Mobil ve backend aynı ürün sözleşmelerine bağlıdır;
ancak farklı dil, araç zinciri ve bağımlılık yaşam döngülerine sahiptir. Repo ayrıca A-001,
A-005 ve A-006'nın tekrar üretilebilir teknik kanıtlarını içerir. Bu kanıtlar üretim
uygulamalarının başlangıç iskeleti değildir.

Repo topolojisi şu ihtiyaçları birlikte karşılamalıdır:

- mobil ve backend değişikliklerinin aynı API/veri/olay sözleşmesine karşı incelenebilmesi;
- modüler monolit sınırlarının klasör ve paket yapısında görünmesi;
- farklı araç zincirlerinin birbirini gereksiz yere kilitlememesi;
- görev ve dosya sahipliğinin PR diff'inde açık olması;
- deney kodunun üretim kodu veya ortak paket sanılmaması;
- CI'ın yalnız etkilenen uygulama ya da deneyi çalıştırabilmesi.

Bu ADR fiziksel iskeleti veya uygulama kodunu oluşturmaz. Topolojiyi A-011 için bağlayıcı hâle
getirir.

## 2. Değerlendirilen seçenekler

| Seçenek | Güçlü yön | Temel maliyet/risk | Sonuç |
|---|---|---|---|
| Tek repo, uygulama sınırları açık | Sözleşme ve atomik çapraz istemci değişiklikleri tek PR'da; ortak inceleme ve izlenebilirlik | Path bazlı CI ve sahiplik disiplini gerekir | **Seçildi** |
| Mobil ve backend için ayrı repolar | Araç zincirleri ve erişim sınırları doğal ayrılır | Sözleşme sürümleme/koordinasyon yükü, küçük ekipte çapraz değişikliklerin bölünmesi | Reddedildi |
| Tek repo, bütün kaynakları tek `src` altında toplama | İlk görünüm basit | Dil ve sahiplik sınırları belirsizleşir; deneyler üretime karışır | Reddedildi |
| Nx/Turborepo benzeri zorunlu kök orkestratör | Etkilenen iş ve önbellek için hazır özellikler | Dart/Gradle üzerinde ek Node araç zinciri ve bağımlılık getirir | Şimdilik seçilmedi |

İlk aşamada ayrı repo veya özel monorepo yöneticisinin sağlayacağı fayda, getireceği sözleşme
senkronizasyonu ya da üçüncü araç zinciri maliyetini karşılamaz. Ölçülmüş CI süresi veya repo
ölçeği sorunu oluşursa orkestrasyon aracı ayrı ADR ile değerlendirilebilir.

## 3. Karar

`MepCity/kurs-platform` tek ürün reposu olarak korunacaktır. Üretim uygulamaları `apps`,
teknik karar kanıtları `experiments`, repo içi otomasyon `tooling` altında ayrılır. Mevcut
bağlayıcı ürün ve çalışma sözleşmeleri repo kökünde, mimari karar kayıtları `ADR` altında
kalır.

A-011'in oluşturacağı hedef üst seviye yerleşim şöyledir. Aşağıdaki ağaç bir dosya oluşturma
talimatı değil, ad ve sahiplik sözleşmesidir:

```text
kurs-platform/
├── apps/
│   ├── mobile/                 # Flutter iOS/Android üretim uygulaması
│   └── backend/                # Java/Spring Boot modüler monolit
├── experiments/                # İzole, üretim dışı ve tekrar üretilebilir karar kanıtları
│   ├── a001_flutter_spike/
│   ├── a005_local_queue/
│   └── a006_realtime_sse/
├── tooling/                    # Repo otomasyonu; ürün çalışma zamanı kodu değil
├── ADR/                        # Mimari karar kayıtları ve karar ekleri
├── .github/workflows/          # Path bazlı CI girişleri
├── AGENTS.md                   # Agent çalışma kuralları
├── README.md                   # Repo giriş belgesi
└── ...                         # Mevcut bağlayıcı ürün, görev ve alan sözleşmeleri
```

Kökte genel amaçlı `src`, `lib`, `shared`, `common` veya `utils` klasörü açılmaz. Bir kodun
tek sahibi belirlenemiyorsa ortak alana taşınmaz. Üretim uygulamaları birbirinin kaynak
dosyasına dosya yolu, Git alt modülü veya yerel paket bağımlılığıyla bağlanmaz.

## 4. Mobil uygulama yerleşimi

`apps/mobile` tek Flutter uygulamasıdır ve kendi `pubspec.yaml` ile kilit dosyasına sahiptir.
Ana ürün planındaki beş katman feature-first yerleşimle korunur:

```text
apps/mobile/
├── lib/
│   ├── core/                   # Kimlik bağlamı, hata, ağ, yönlendirme, tema ve gözlemlenebilirlik
│   └── features/
│       └── <feature>/
│           ├── presentation/
│           ├── application/
│           ├── domain/
│           └── data/
├── test/                       # Kaynak sınırlarını yansıtan birim/widget testleri
├── integration_test/           # Uygulama içi dikey akışlar
├── android/                    # Flutter platform sarmalayıcısı
└── ios/                        # Flutter platform sarmalayıcısı
```

- `presentation`, doğrudan HTTP, yerel veritabanı veya secure storage çağırmaz.
- `domain`, Flutter UI, HTTP, persistence ve platform adaptörü türlerine bağımlı olmaz.
- `data`, domain/application tarafından tanımlanan sözleşmeleri uygular; kurum ve kullanıcı
  bağlamına göre ayrılmış API, önbellek ve kalıcı kuyruk adaptörlerini barındırır.
- `core`, feature'ların sahibi olması gereken iş kurallarının toplandığı bir depo değildir.
  Yalnız birden fazla feature için gerçekten çapraz kesen, kararlı altyapı sözleşmelerini taşır.
- Feature adları ürün dilini izler. Backend iç paketine veya tablo adına göre mobil feature
  oluşturulmaz.

İlk iskelette ayrı Dart paketlerinden oluşan bir workspace kurulmaz. Uygulama içi sınırlar
önce klasör, import ve mimari testlerle korunur. Bağımsız sürümleme ya da ölçülmüş derleme
ihtiyacı doğarsa paket çıkarımı ayrı görev ve gerekirse ADR ister.

## 5. Backend yerleşimi

`apps/backend` tek Spring Boot uygulaması ve başlangıçta tek Gradle projesidir. Tek deploy
edilen modüler monolit, teknik katmanlara göre kökten değil iş alanlarına göre paketlenir:

```text
apps/backend/
├── src/main/java/org/mepcity/kursplatform/
│   ├── configuration/          # Composition root; adaptör-port bağlantıları
│   ├── core/                   # Dar ortak çekirdek ve kararlı ortak sözleşmeler
│   └── <module>/               # iam, org, term, cls, people, att, content, ...
│       ├── api/
│       ├── application/
│       ├── domain/
│       └── infrastructure/
├── src/main/resources/
├── src/test/                   # Birim, slice ve mimari sınır testleri
└── src/integrationTest/        # PostgreSQL/repository/transaction entegrasyon testleri
```

`<module>`, `AGENT_GOREV_PLANI.md` §5'teki CORE, IAM, ORG, TERM, CLS, PEOPLE, ATT, CONTENT,
PROGRAM, PROGRESS, AUDIT, EXPORT, SYNC, REALTIME ve sonraki faz NOTIFY sınırlarının küçük harfli
paket karşılığıdır. NOTIFY için ilk sürüm davranışı erkenden uygulanmaz.

- Bağımlılık yönü `api → application → domain`dir. `infrastructure`, application/domain
  portlarını uygular; application'ın infrastructure import etmesi yasaktır.
- `domain`, Spring, HTTP veya persistence türü içermez.
- Başka modülün `infrastructure` ya da persistence yüzeyine doğrudan erişilmez. Modüller arası
  kullanım yayımlanmış application sözleşmesi üzerinden yapılır.
- `configuration`, composition root dışında iş kuralı taşımaz.
- `core`, bütün modüllerin iç modellerini birleştiren bir alan değildir. Kimlik türleri, ortak
  hata modeli, zaman, sayfalama ve gerçekten kararlı ortak sözleşmelerle sınırlıdır.
- Başlangıçta her iş alanı ayrı Gradle subproject veya ayrı deploy birimi yapılmaz. Paket
  sınırları ve mimari testler yetersiz kalırsa çoklu Gradle modülüne geçiş ayrı ADR ister;
  mikroservise ayrılma anlamına gelmez.

## 6. Bağımlılık ve araç zinciri kuralları

- Mobil bağımlılıkları ve sürüm kilidi `apps/mobile` içinde Dart/Flutter araçlarıyla yönetilir.
- Backend bağımlılıkları, BOM ve Gradle wrapper `apps/backend` içinde yönetilir.
- Kökte bütün dilleri yöneten zorunlu bir paket yöneticisi veya tek birleşik lockfile olmaz.
- `tooling`, geliştirici/CI kolaylığı sağlayan ince orkestrasyon komutlarını içerebilir; uygulama
  iş kuralı, secret, gerçek kullanıcı verisi veya üretim çalışma zamanı bağımlılığı içermez.
- Ortak API/veri/olay anlamı çalıştırılabilir kaynak paylaşımıyla değil, onaylı repo
  sözleşmeleriyle yönetilir. Gelecekte şema üretimi eklenirse kanonik kaynak, üretilen çıktı,
  drift kontrolü ve sahiplik ayrı sözleşmede tanımlanır.
- Yeni bağımlılık yalnız sahibi olan uygulamanın manifestine eklenir. Bir uygulamanın testini
  çalıştırmak diğer uygulamanın SDK'sını gerektirmemelidir; yalnız repo düzeyi tüm-kalite
  komutu iki bağımsız kontrolü ardışık çağırabilir.

## 7. Deneylerin yaşam döngüsü

`experiments/<görev-kimliği>-<kısa-ad>` dizinleri karar kanıtıdır ve üretim bağımlılık grafiğinin
dışındadır.

- Her deney kendi çalıştırma talimatını, manifestini/lockfile'ını ve kabul testini taşır.
- `apps` altındaki kod deney kaynağını import etmez; deney de üretim uygulamasının gizli bir
  başlangıç modülü sayılmaz.
- Kabul edilmiş deney, kanıtı tekrar üretmek veya güvenlik/araç zinciri uyumluluğunu korumak
  dışında genişletilmez. Ürün davranışı ilgili uygulama görevinde üretim sınırlarıyla yeniden
  uygulanır ve test edilir.
- Bir deney artık tekrar üretilemiyorsa sessizce silinmez. Sebep, son doğrulanmış sürüm ve
  yerine geçen kanıt belgelenir; kaldırma ayrı görevde yapılır.
- Mevcut A-001, A-005 ve A-006 dizinleri bu sözleşmeye uygundur ve yerinde kalır. A-011 bunları
  `apps` altına taşımaz veya üretim manifestlerine bağlamaz.

## 8. CI, sahiplik ve değişiklik sınırları

- CI girişleri `.github/workflows` altında kalır ve mümkün olduğunda `apps/mobile`,
  `apps/backend`, `experiments/<id>`, belge ve tooling yollarına göre ayrılır.
- Bir uygulama değişikliği kendi lint, format, type/compile ve test kapısını çalıştırır. Ortak
  sözleşme veya repo otomasyonu değişikliği etkilenen bütün kapıları çalıştırabilir.
- Workflow içindeki ürün mantığı büyütülmez; tekrar kullanılan doğrulama komutları gerektiğinde
  `tooling` altında sürümlenir.
- Aynı ortak çekirdeğin, workflow'un veya sözleşmenin eşzamanlı görev sahipliği verilmez.
- CODEOWNERS veya benzeri inceleme yönlendirmesi eklenirse güvenlik/yetki yerine geçmez; amacı
  doğru modül sahibini incelemeye çağırmaktır.

A-012 kesin CI kalite kapılarını ve önbellek stratejisini; A-013 ortam değişkeni/secret
iskeletini; A-014 loglama ve hata izleme temelini kuracaktır. Bu ADR bu görevlerin araç veya
sağlayıcı kararlarını erkenden almaz.

## 9. A-011 için bağlayıcı iskelet sözleşmesi

A-011:

1. `apps/mobile` altında derlenebilir boş Flutter uygulamasını ve katman/feature sınırlarını;
2. `apps/backend` altında derlenebilir tek Spring Boot/Gradle uygulamasını, composition root'u,
   iş alanı paketlerini ve mimari sınır testlerini;
3. gerekli en küçük `tooling` girişlerini ve repo kullanım belgelerini

oluşturabilir.

A-011; gerçek giriş, yoklama, veri tabanı migration'ı, API endpoint'i, üretim sync/SSE,
secret değeri veya sonraki faz özelliği eklemez. Boş paketleri yalnız görünürlük için üretmek
yerine derlenebilir en küçük sınırlı iskelet ve sınır testleri tercih edilir. Platform tarafından
üretilen dosyalar dışında anlamsız placeholder kaynak bırakılmaz.

## 10. Sonuçlar ve riskler

### Olumlu sonuçlar

- Mobil, backend, sözleşme ve deney değişiklikleri tek PR geçmişinde izlenebilir.
- Flutter ve Gradle bağımlılıkları birbirinden bağımsız kalır.
- Üretim kodu ile teknik kanıtların karışması engellenir.
- Backend modüler monolit ve mobil katman sınırları repo ağacından okunabilir.
- A-011, ürün davranışı tasarlamadan kesin bir iskelet sözleşmesine dayanır.

### Maliyetler ve önlemler

- Repo büyüdükçe gereksiz CI çalışması oluşabilir. Önlem: path bazlı iş seçimi; yetersiz kalırsa
  ölçümle monorepo orkestratörü değerlendirmesi.
- Uygulama içindeki `core` alanları zamanla sahipliği belirsiz kod deposuna dönüşebilir.
  Önlem: dar kapsam, mimari import testleri ve ortak değişikliklerde tek görev sahipliği.
- Tek repo bütün kaynakları teknik olarak görünür kılar. Yetki ayrıştırma gereksinimi doğarsa
  CODEOWNERS yeterli güvenlik sınırı sayılmaz; repo ayrımı ve erişim modeli ayrı karardır.
- Tek Gradle proje paket sınırlarını derleme zamanında tek başına zorlamaz. Önlem: A-002'nin
  zorunlu mimari testleri; ihlal ölçülürse Gradle subproject seçeneğinin yeniden değerlendirilmesi.

## 11. Kabul ve doğrulama

| Ölçüt | Sonuç |
|---|---|
| Monorepo ile ayrı repo seçenekleri ve seçimin gerekçesi kaydedildi | Karşılandı |
| Flutter mobil uygulaması ile Spring Boot backend için sahiplik ve üst seviye yollar tanımlandı | Karşılandı |
| Mobil beş katman ve backend feature/domain-first modüler monolit sınırları korundu | Karşılandı |
| Deneylerin üretim dışı konumu, bağımlılık yasağı ve yaşam döngüsü tanımlandı | Karşılandı |
| Ortak kod, bağımlılık, lockfile, tooling ve CI sınırları açıklandı | Karşılandı |
| Kurum izolasyonu, yetkilendirme, idempotency ve denetim kuralları klasör yapısıyla gevşetilmedi | Karşılandı |
| A-011'in oluşturacağı iskelet ile A-009 kapsamında oluşturulmayacaklar ayrıldı | Karşılandı |

## 12. Kapsam dışı

- `apps/mobile`, `apps/backend` veya `tooling` klasörlerini fiziksel olarak oluşturmak (A-011).
- Flutter, Java, Spring Boot, Gradle veya paket bağımlılıklarını eklemek/pinlemek (A-011).
- CI kalite kapıları ve branch korumalarını uygulamak (A-012).
- Ortam, deployment, secret, loglama veya hata izleme sağlayıcısı seçmek (A-010, A-013, A-014).
- Veritabanı migration'ı, API/olay şeması veya ürün davranışı uygulamak.
- Mevcut kök sözleşme belgelerini taşımak ya da yeniden adlandırmak.

## 13. Kaynaklar ve uyum

- `URUN_VE_UYGULAMA_PLANI.md` §3.2, §11.1–§11.3, §18 ve §21.
- `AGENT_GOREV_PLANI.md` §5, §6 ve §9.
- `ADR/ADR-001-mobil-framework.md` — Flutter, beş mobil katman ve deney sınırı.
- `ADR/ADR-002_BACKEND_DILI_VE_FRAMEWORK.md` — Java/Spring Boot, tek deploy, feature/domain-first
  paketleme ve mimari test kuralları.
- `ADR/ADR-005-yerel-mobil-veritabani-ve-kuyruk.md` — A-005 deneyinin üretim dışı sınırı.
- `ADR/ADR-006-gercek-zamanli-kanal.md` — A-006 deneyinin üretim dışı sınırı.

Bu ADR repo yerleşimini belirler; kurum izolasyonu, bağlama göre yetkilendirme, idempotency,
sunucu onayı, denetim kaydı, geri alma ve arşivleme sözleşmelerinden hiçbirini değiştirmez.
