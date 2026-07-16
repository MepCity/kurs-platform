# A-013 Ortam değişkeni ve secret yönetimi iskeleti

## Görev başlangıç sözleşmesi

- **Görev:** A-013 — Ortam değişkeni ve secret yönetimi iskeleti.
- **Bağımlılıklar:** A-010, A-011, PLAN-005 ve A-004R3 `main` üzerinde tamamlandı; görev
  `READY` durumundaydı.
- **Değiştirilen alan:** Backend ve mobil dış yapılandırma iskeleti, örnek ortam dosyası, repo
  secret sızıntısı kontrolü ve ilgili testler.
- **Beklenen çıktı:** Gerçek secret taşımayan, environment ayrımını ve secret referanslarını
  doğrulayan başlangıç iskeleti.
- **Test yöntemi:** Backend birim/context testleri, mobil birim/mimari testleri, tooling
  regresyon testi ve repo secret taraması.

`GOREV_DURUMU.md` bu görevde değiştirilmez.

## Ortam adları

Bağlayıcı ortam adları şunlardır:

| Değer | Anlam |
|---|---|
| `development` | Yerel geliştirme ve sentetik test verisi |
| `staging` | Release adayı ve sentetik kabul ortamı |
| `production` | Gerçek kurum pilotu veya canlı kullanım |

`prod`, `test`, `dev` gibi kısa veya bağlama göre değişen adlar uygulama yapılandırmasında
kabul edilmez.

## Değişken adı sözleşmesi

| Değişken | Backend | Mobil | Secret değeri mi? | Açıklama |
|---|---|---|---|---|
| `KURS_PLATFORM_ENVIRONMENT` | Zorunlu | Zorunlu | Hayır | `development`, `staging` veya `production` |
| `KURS_PLATFORM_PUBLIC_API_BASE_URL` | Zorunlu | Zorunlu | Hayır | Mobilin ve dış istemcilerin gördüğü HTTPS API tabanı |
| `KURS_PLATFORM_COGNITO_ISSUER_URI` | Zorunlu | Zorunlu | Hayır | Ortama özel Cognito issuer URL'si |
| `KURS_PLATFORM_COGNITO_CLIENT_ID` | Zorunlu | Zorunlu | Hayır | Secretsiz public mobile client kimliği |
| `KURS_PLATFORM_DATABASE_URL_SECRET_REF` | Zorunlu | Yok | Referans | Veritabanı bağlantı secret'ının adı/referansı |
| `KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF` | Zorunlu | Yok | Referans | Platform token HMAC pepper secret referansı |
| `KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF` | Zorunlu | Yok | Referans | İlk parola/teslim escrow şifreleme anahtarı referansı |
| `KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF` | Zorunlu | Yok | Referans | Cognito yönetim komutları için en az yetkili çalışma kimliği referansı |

Secret referansları gerçek değer taşımaz. Bu görev belirli bir secret manager ürünü seçmez; ancak
referanslar environment ile başlamalıdır:

```text
development/platform/database-url
staging/platform/database-url
production/platform/database-url
```

Production secret referansı development veya staging prefix'iyle başlayamaz. Secret değerleri;
repo, Docker image layer, build arg, mobil paket, log, PR açıklaması ve idempotency sonucuna
yazılmaz.

## Backend davranışı

- Backend `KURS_PLATFORM_*` environment değişkenlerini Spring dış yapılandırması üzerinden okur.
- Eksik veya geçersiz ortam adı uygulama açılışında fail-fast durur.
- `application.properties` zorunlu değerler için sessiz development fallback içermez; sentetik
  değerler yalnız test veya deploy/build girdisiyle açık verilir.
- API tabanı ve Cognito issuer production/staging için HTTPS olmak zorundadır; development'ta
  yalnız `localhost`/loopback HTTP istisnası kabul edilir.
- Secret alanları yalnız referanstır. Referans içinde `=`, boşluk veya `://` gibi ham değer
  izlenimi veren biçimler reddedilir.
- `KURS_PLATFORM_COGNITO_CLIENT_ID` public client kimliğidir; client secret değildir.

## Mobil davranışı

- Mobil yapılandırma `--dart-define` ile alınır.
- Mobilde yalnız public environment bilgileri bulunur; veritabanı, token pepper, secret delivery
  key veya Cognito admin role referansı yoktur.
- Mobil runtime kodu default development değeri üretmez; eksik `--dart-define` konfigürasyon
  hatasıdır.
- Geçersiz environment, HTTPS olmayan production/staging API tabanı veya eksik Cognito issuer
  fail-fast konfigürasyon hatası üretir.
- Mobil uygulama client secret, provider yönetim kimliği veya veritabanı bağlantı secret'ı
  taşımaz.

## Repo güvenlik kontrolü

`./tooling/check_no_secrets.sh` şu sınırlı kontrolleri yapar:

- `.env`, `.env.local`, `.pem`, `.key`, `.p12`, `.pfx` gibi yerel secret dosyalarının repoya
  eklenmesini reddeder.
- AWS access key biçimi, private key başlangıcı, JWT benzeri uzun bearer değerleri ve yaygın
  `password/token/secret=...` kalıplarını reddeder.
- `DATABASE_URL`, `JDBC_URL`, `SPRING_DATASOURCE_URL` ve kullanıcı bilgisi taşıyan PostgreSQL/
  JDBC bağlantı atamalarını reddeder.
- Secret referanslarını yalnız `KURS_PLATFORM_*_REF=<environment>/<path>` biçiminde kabul eder.
  Satır geneli `example`, `placeholder`, `fake` veya koşulsuz `_REF=` muafiyeti yoktur.
- Test dosyaları taramadan dışlanmaz; regresyon testleri secret kalıplarını runtime'da parçaları
  birleştirerek üretir.
- Dosya listesi NUL-delimited işlenir; boşluk, tab ve shell özel karakterleri içeren dosya
  adları split edilmeden taranır.

Bu tarama gerçek secret yönetimi veya DLP ürünü değildir; erken sızıntıların repoya girmesini
engelleyen fail-closed başlangıç kapısıdır.

## Secret rotasyon ve kırılma prosedürü

Bu prosedür provider bağımsızdır; gerçek secret manager ürünü seçmez.

1. **Sahiplik:** Her secret referansının bir ortam sahibi, teknik uygulama sahibi ve acil onay
   sahibi olur. Production erişimi süreli, adlandırılmış kimlikle ve MFA ile verilir; paylaşılan
   insan hesabı kullanılmaz.
2. **Yeni sürüm oluşturma:** Rotation yeni bir secret sürümü üretir; mevcut referans hemen
   overwrite edilmez. Yeni sürüm aynı environment prefix'i altında ayrı sürüm/etiketle tutulur.
   Development, staging ve production sürümleri birbirinden türetilmez.
3. **Kontrollü geçiş ve overlap:** Uygulama önce yeni sürümü okuyabilecek hale getirilir.
   Token pepper veya şifreleme anahtarı gibi stateful secret'larda kısa overlap penceresi
   açıkça kaydedilir; yeni yazılar yeni sürüme, doğrulama gerekiyorsa eski sürüme yalnız okuma
   amaçlı bakar.
4. **Doğrulama:** Staging'de aynı image digest ve sentetik veriyle health, login/token,
   idempotency, audit ve iki kurum izolasyonu testleri geçmeden production rotasyonu başlamaz.
   Production'da smoke test ve gözlem penceresi tamamlanmadan eski sürüm iptal edilmez.
5. **Eski sürümü iptal:** Overlap penceresi bitince eski sürüm disabled/revoked yapılır.
   Silme yerine önce geri dönüşü mümkün pasifleme tercih edilir; kalıcı imha yedek/restore ve
   hukukî saklama kararıyla uyumlu olmak zorundadır.
6. **Rollback:** Yeni sürüm başarısız olursa uygulama eski doğrulanmış sürüme geri döner.
   Rollback, mutable environment adını sessizce değiştirmek yerine release kaydına secret sürümünü
   ve nedeni yazar. Eski sürüm iptal edildiyse fail-closed durulur ve acil erişim akışı açılır.
7. **Acil erişim:** Break-glass erişim yalnız production sahibi onayıyla, süreli, en dar kapsamlı
   ve denetim kaydıyla kullanılır. İşlem tamamlanınca erişim geri alınır, kullanılan secret
   rotate edilir ve olay raporu yazılır.
8. **Audit:** Secret oluşturma, okuma yetkisi verme, sürüm değiştirme, iptal, rollback ve acil
   erişim olayları ortam, aktör, referans, sürüm ve gerekçeyle kaydedilir. Secret değeri audit,
   log veya PR açıklamasına yazılmaz.
9. **Ortam izolasyonu:** Production secret'ı development/staging'e kopyalanmaz. Bir ortamda
   sızıntı şüphesi diğer ortam secret'larını otomatik güvenli saymaz; etki analizi yapılır.
10. **Fail-closed:** Secret referansı eksik, yanlış environment prefix'li, provider sonucu
    belirsiz veya doğrulama başarısızsa uygulama yeni işlem başlatmaz; bekleyen kuyruk başarı
    sayılmaz ve kalıcı kuyruktan silinmez.

## Kapsam dışı

- Gerçek Cognito, Supabase, Render, registry veya secret manager kaynağı oluşturulmadı.
- AWS SDK, provider adapter, deployment dosyası veya migration secret akışı eklenmedi.
- Bu prosedür gerçek provider üzerinde çalıştırılmadı; fiziksel rotasyon tatbikatı ve secret
  manager entegrasyonu sonraki operasyon görevlerine bırakıldı.
