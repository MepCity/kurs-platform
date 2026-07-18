# ADR-004 — Kimlik doğrulama sağlayıcısı seçimi

| Alan | Değer |
|---|---|
| Durum | Kabul edildi — V1 için Amazon Cognito Essentials |
| Tarih | 15 Temmuz 2026 |
| Görev | A-004 — Kimlik doğrulama sağlayıcısı ADR'si |
| Karar sahipliği | Dalga 1 teknik kararları |
| Bağımlılıklar | `YETKI_MATRISI.md` (P-003), `KISISEL_VERI_ENVANTERI.md` (P-004) |

## Bağlam

İlk sürümde platform yöneticisi, kurum yöneticisi ve hocalar yönetici tarafından oluşturulan
kullanıcı adı/parola hesabıyla giriş yapar. Mobil uygulama parolayı saklamaz; güvenli cihazda
kalıcı oturum, kısa ömürlü erişim belirteci, iptal edilebilir yenileme belirteci ve yönetici
tarafından cihaz oturumlarını kapatma gereklidir.

Kimlik doğrulama; kurum üyeliği, rol, sınıf ataması veya iş eylemi yetkisinin kaynağı değildir.
Bir global kullanıcı birden fazla kurum üyeliğine sahip olabilir. Bu nedenle tek bir
sağlayıcı-oturumunun genel olarak iptal edilmesi, bir kurum yöneticisinin başka kurum bağlamını
etkilemesine yol açamaz. `VERI_MODELI.md` §4.11'deki kurum kapsamlı `refresh_tokens` ve
`session_generation` değişmezleri korunmalıdır.

Bu ADR yalnızca son kullanıcı kimlik doğrulama sağlayıcısını, onunla sınırı ve V1 güvenlik
profilini seçer. Veritabanı/hosting (`A-003`), ortam sırları
(`A-013`), oran sınırlamanın sayısal eşikleri ve MFA/biometri ürün kararı kapsam dışıdır.

## Karar sürücüleri

1. iOS ve Android için kullanıcı adı/parola ile standart, güvenli giriş; public mobile client
   için Authorization Code + PKCE (`S256`) desteği sağlamalıdır.
2. Parola platform veritabanına veya mobil uygulamaya açık biçimde hiç girmemeli; parola
   politikası, kaba kuvvet koruması, parola sıfırlama ve oturum yönetimi merkezi olmalıdır.
3. Kısa ömürlü erişim belirteçleri ve dönen, iptal edilebilir yenileme belirteçleri
   sağlanmalıdır. Ham token yalnız cihazın güvenli saklamasında bulunabilir; uygulama
   veritabanında yalnız geri döndürülemez özet saklanır.
4. Sağlayıcı, backend'in standart OIDC doğrulaması yapabilmesi için keşif belgesi, JWKS ve
   imzalı kimlik belirteçleri sunmalıdır; uygulama sağlayıcıya doğrudan veritabanı erişimi
   vermez.
5. Çok kurumlu rol, izin, sınıf kapsamı ve kurum-kapsamlı oturum iptali, sağlayıcıdaki sabit
   grup/rol iddialarına bağlanmadan IAM modülünde canlı olarak doğrulanabilmelidir.
6. Başlangıçta tek modüler monolitin yanında çalışabilmeli; bulut sağlayıcısı, veri barındırma
   veya ticari kimlik hizmetine erken bağlayıcı karar getirmemelidir.
7. Yönetim API'si, kullanıcı oluşturma/devre dışı bırakma, parola sıfırlama ve olağandışı giriş
   denetimi için olgun olmalıdır.

## Değerlendirilen seçenekler

| Seçenek | Güçlü yönler | Sınırlamalar | Sonuç |
|---|---|---|---|
| **Keycloak (self-managed), tek global realm** | Açık standart OIDC/OAuth 2.0, kullanıcı adı/parola, PKCE zorunluluğu, dönen refresh token, oturum/administration API'leri; belirli bulut sağlayıcısına bağlamaz. | İşletme, yükseltme, yedekleme ve yüksek erişilebilirlik sorumluluğu platformdadır. | Fallback |
| Amazon Cognito Essentials User Pool | Yönetilen hizmet, native app için PKCE, username/parola, refresh-token rotasyonu ve iptal desteği; doğrudan kullanıcılar için ücretsiz MAU kotası. | AWS hesabı/bölgesi ve IAM yönetim yüzeyi getirir; provider iptali kurum-kapsamlı platform iptalinin yerine geçemez. | **V1 için kabul edildi** |
| Auth0 | Native uygulama ve refresh-token rotasyonu için olgun SaaS deneyimi sunar. | Ticari fiyatlandırma ve sağlayıcı bağımlılığı getirir; kurum-kapsamlı erişim/iptal değişmezleri yine uygulamada kalır. | Reddedildi |
| Firebase Authentication | Mobil SDK'lar ve yönetilen kullanıcı oturumları sunar. | Token iptali sunucu tarafında ek doğrulama çağrısı gerektirir; IAM sınırlarına göre daha fazla platforma özgü SDK/operasyon bağımlılığı getirir. | Reddedildi |
| Uygulama içinde parola/oturum implementasyonu | Tek deploy birimi olabilir. | Parola saklama, sıfırlama, saldırı koruması, OIDC uyumluluğu ve oturum güvenliği için gereksiz yüksek güvenlik sorumluluğu doğurur. | Reddedildi |

### Maliyet ve işletme karşılaştırması

Rakamlar yalnız 14 Temmuz 2026'da yayımlanmış liste fiyatı/ücretsiz kota yönünü gösterir;
kur, bölge, SMS, destek, egress ve operasyon emeği dahil değildir. Kesin bütçe A-010'da
iş yükü ve seçilen hosting bölgesiyle hesaplanır.

| Seçenek | Sağlayıcı ücreti yönü | Platformun üstlendiği maliyet/sorumluluk |
|---|---|---|
| Keycloak self-managed | Yazılım Apache 2.0; lisans ücreti yoktur. | Ayrı DB, compute, TLS, yedek/geri yükleme tatbikatı, izleme, yükseltme/CVE müdahalesi, HA ve nöbet/operasyon emeği. |
| Amazon Cognito | Doğrudan kullanıcı girişi için MAU temelli; yeni havuzlarda fiyat katmanı ve gelişmiş güvenlik ek MAU maliyeti vardır. | AWS hesap/bölge bağımlılığı; altyapı işletimi düşer. |
| Auth0 | Liste fiyatında ücretsiz katman 25.000 MAU; ücretli katman 500 MAU için aylık 35 USD'den başlar. | SaaS bağımlılığı ve plan/limit yükseltme riski; işletim yükü düşer. |
| Firebase Authentication | Çoğu auth seçeneği ücretsiz; Identity Platform e-posta/sosyal için 50.000 MAU ücretsiz kota, telefon ayrı ücretlidir. | Google Cloud bağımlılığı ve telefon/SMS maliyeti; işletim yükü düşer. |

### 16 Temmuz 2026 tarihli Cognito Essentials maliyet modeli

Bu model AWS'nin 16 Temmuz 2026 tarihinde erişilen resmî
[Amazon Cognito fiyatlandırma](https://aws.amazon.com/cognito/pricing/) sayfasının liste
fiyatlarına dayanır; vergi, kur, destek ve kurum sözleşmesi indirimi içermez.

| Maliyet kalemi | 16 Temmuz 2026 tabanı | V1 bütçe etkisi |
|---|---|---|
| Doğrudan username/parola veya sosyal IdP ile giriş yapan Essentials kullanıcıları | Hesap veya AWS Organization başına ayda ilk `10.000 MAU` ücretsiz; sınırın üzerindeki her MAU `0,015 USD/ay` | V1'in ana kullanıcı maliyeti; aylık aktif kullanıcı tahmini A-010 bütçesinde bu eşikle hesaplanır. |
| SAML/OIDC federasyonlu kullanıcı | Ayda ilk `50 MAU` ücretsiz; sınırın üzerindeki her MAU `0,015 USD/ay` | V1 kapsamı değildir; kurumsal federasyon açılırsa ayrı bütçe ve ADR revizyonu gerekir. |
| SMS ile MFA, kayıt, parola kurtarma veya telefon doğrulama | Cognito taban MAU ücretine dahil değildir; SNS/AWS End User Messaging SMS ülke, operatör, gönderici ve kayıt ücretleri ayrıca uygulanır. | SMS bu ADR'de etkinleştirilmez; etkinleştirilmeden ülke bazlı fiyat ve harcama kotası onaylanır. |
| E-posta ile kayıt, parola kurtarma, doğrulama veya MFA | Cognito taban MAU ücretine dahil değildir; production gönderiminde Amazon SES fiyatı ve kota/sandbox koşulları ayrıca uygulanır. | A-010, beklenen ileti hacmini SES bütçesine ayrı satır olarak ekler. |
| Multi-Region, artırılmış API kotası ve M2M token istekleri | Cognito fiyatlandırmasında ayrı add-on/istek ücretidir. | V1 temel kararına dahil değildir; etkinleştirme yeni maliyet ve operasyon onayı gerektirir. |

Fiyat değişikliği yeniden değerlendirme kapısıdır. Ücretsiz MAU sınırının düşmesi, Essentials
MAU birim fiyatının artması, mesajlaşma/add-on zorunluluğu veya A-010 onaylı aylık kimlik
bütçesinin aşılması halinde production kaynağı büyütülmez; PLAN-005 maliyet karşılaştırması ile
bu ADR yeniden açılır. Production user poolu oluşturulmadan hemen önce resmî fiyat sayfası ve
AWS Pricing Calculator çıktısı tarihli olarak yeniden doğrulanır.

## Karar ve revizyon kapısı

İlk incelemede kimlik doğrulama sağlayıcısı olarak **Keycloak self-managed**, tek bir global
`kurs-platform` realm'iyle seçilmiştir. PLAN-005 maliyet/operasyon incelemesi, bu kararın ayrı
compute, veritabanı, TLS, yedek, izleme, yükseltme/CVE ve kesinti operasyonunu başlangıç bütçesi
dışında bıraktığını tespit etmiştir. Bu nedenle Keycloak uygulama kararı askıya alınmış ve
**Cognito Essentials** yönetilen aday olarak A-004R1–A-004R3 deney zincirine alınmıştır.

A-004R1–A-004R3 deney zinciri sonucunda **Amazon Cognito Essentials**, V1'in global son kullanıcı
kimlik doğrulama sağlayıcısı olarak kabul edilmiştir. Karar kanıtı ve kaynak kapatma ayrıntıları
`A004R3_COGNITO_MALIYET_OPERASYON_VE_TEARDOWN_KANITI.md` içindedir. Deneyde son gerçekleşen
maliyet `0.0 USD`, tahmin `None` idi; bu bir production fiyat garantisi değildir. Cognito,
budget, bootstrap IAM ve deney rolü/policy kaynaklarının yokluğu doğrulanmıştır.

Bağlayıcı sınırlar:

- `issuer + subject` eşlemesi, platform `user_id`, kurum üyeliği, rol/izin/sınıf kontrolü,
  platform opaque token aileleri, `session_generation` ve kurum kapsamlı iptal değişmezdir;
- sağlayıcı rolleri ürün yetkisi sayılmaz ve iptal edilmiş provider JWT'sinin yalnız yerel
  imza/süre doğrulamasıyla reddedileceği varsayılmaz;
- mobil client secretsiz public clienttır; Authorization Code + PKCE `S256`, tam redirect URI
  allow-listi, `state` ve `nonce` doğrulaması zorunludur;
- kurum API'leri yalnız platform opaque access tokenını kabul eder; Cognito tokenları yalnız
  doğrulanmış token değişimi sınırında kullanılır;
- provider global güvenlik olayları platform ailelerine idempotent ve fail-closed yansıtılır;
  kanonik durum en çok 5 dakikada uzlaştırılır, 2 dakikada alarm ve 5 dakikada escalation açılır;
- production user poolundan önce yeni bütçe/bildirim kaynağı ve en az yetkili kısa ömürlü
  çalışma kimliği oluşturulup doğrulanır; deney kaynakları yeniden kullanılmaz.

Cognito Essentials doğrulaması tek iş gününe sığdırılmaz ve şu atomik sırayla yürütülür:

1. `A-004R1` — yalnız sentetik kullanıcılarla yönetici username + geçici parola provisioning'i,
   ilk giriş parola değişimi, kayıp create yanıtı ve gerçek mobil Authorization Code + PKCE
   akışını kanıtlar. Deney AWS `eu-central-1` bölgesinde yürür; ilk kaynak açılmadan `5 USD`
   bütçe alarmı kurulur ve yazılı ürün sahibi onayı olmadan `10 USD` üstüne çıkılmaz. Kimlik
   bilgileri yalnız kısa ömürlü veya yerel güvenli yüzeyden alınır; repo, artifact ve loga yazılmaz.
2. `A-004R2` — refresh rotation/reuse, provider kesintisi, kullanıcı disable/global iptal,
   platform tek-cihaz ve kurum-kapsamlı iptal ile olay kaybı uzlaştırmasını kanıtlar. İptal
   edilmiş veya devre dışı bırakılmış provider tokenı yeni platform oturumu üretemez; önceden
   üretilmiş platform token aileleri idempotent iptal edilir ve kaçırılan olay fail-closed
   uzlaştırılır. Yalnız imza/süre doğrulamasının iptali kanıtlamadığı negatif test edilir.
3. `A-004R3` — kanıtları ve denetim modelini karşılaştırır, nihai sağlayıcı kararını/IAM-001
   girdisini kaydeder, gerçekleşen maliyeti raporlar ve geçici kaynakları kaldırır. User pool,
   app client, domain, test kullanıcıları, IAM politikaları ve diğer geçici kaynakların silindiği
   öncesi/sonrası envanterle kanıtlanır.

Bu deneylerde gerçek kullanıcı/kurum/öğrenci verisi kullanılmamıştır. Cognito kararının revizyon
kapısı; fiyat/ücretsiz kota değişimi, gerekli bölgede servis engeli, kabul edilemeyen operasyonel
SLO veya güvenlik yeteneği kaybıdır. Bu kapı açılırsa Keycloak fallback'i gerçek barındırma ve
operasyon maliyeti A-010'a eklenerek yeniden onaya sunulur.

## Cognito V1 bağlayıcı profili

- Pool tek global kimlik alanıdır; kendi kendine kayıt kapalı, admin provisioning açıktır.
- Username + tek kullanımlı geçici parola ve ilk girişte zorunlu parola değişimi kullanılır.
- Public mobile client secret taşımaz; yalnız Authorization Code + PKCE `S256` kullanır.
- Refresh rotation açıktır; eski refresh reuse aynı provider oturumunu reddeder. Platform kendi
  opaque refresh ailesi, `session_generation` ve kurum-kapsamlı iptalini ayrıca uygular.
- Provider erişilemez veya kullanıcı durumu belirsizse yeni platform ailesi üretilmez. Mevcut
  platform refresh yalnız canlı üyelik, cihaz ve iptal kontrolleriyle çalışabilir.
- Disable, global sign-out ve parola güvenlik olayları kalıcı provider-command/event dedup ve
  kanonik durum taramasıyla ilgili kullanıcının bütün platform ailelerine yansıtılır.
- Pool, client, domain ve budget production altyapı koduyla yeniden oluşturulur; A-004R1 deney
  kimlikleri veya A-004R2/A-004R3 geçici IAM kaynakları tekrar kullanılmaz.
- **Cihaz-bazlı yeniden kimlik doğrulama bariyeri (bağlayıcı, IAM-002):** Aynı `(user_id,
  device_identifier)` çifti için yeni bir `trusted_devices` satırı, yalnız doğrulanmış Cognito
  `auth_time`, o çift için önceden var olan satırların **en son** `revoked_at` değerinden
  (`MAX(revoked_at)`) büyükse üretilebilir; önceki satır yoksa bariyer devreye girmez. Bu
  `MAX(revoked_at)`, `trusted_devices`e erişen üç işlemi (`PROVIDER_TOKEN_EXCHANGE`,
  `DEVICE_SELF_REVOKE`, `PLATFORM_DEVICE_REVOKE`) aynı `(user_id, device_identifier)` mantıksal
  anahtarında serileştiren bir transaction-scoped advisory lock **altında** yeniden okunur; satır
  hiç yokken bile çalışır (bkz. "IAM-002 — ... RLS eklentisi"). Bu kontrol,
  `users.reauthentication_required_after` ve `organization_memberships.
  reauthentication_required_after` eşiklerinden **bağımsız ve ek** bir cihaz-düzeyi bariyerdir —
  `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE` bu iki eşiği bilinçli olarak değiştirmediğinden
  (bkz. `IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md` §2), bu bariyer olmadan iptal edilmiş bir cihaz
  hâlâ geçerli/eski bir sağlayıcı oturumuyla anında yeniden güven kazanabilirdi. İstemcinin
  `prompt=login` (veya Cognito Hosted UI'da eşdeğer zorunlu yeniden kimlik doğrulama parametresi)
  göndermesi yalnız istemci tarafı yardımdır; bağlayıcı sınır sunucunun bu karşılaştırmasıdır ve
  istemci bunu atlayamaz. Ayrıntılı RLS/`WITH CHECK` ifadesi ve negatif kabul testi aşağıdaki
  "IAM-002 — Cihaz ve oturum iptali `iam_runtime`/RLS eklentisi" bölümündedir.

## Keycloak fallback referansı — bağlayıcı değildir

Keycloak yeniden seçilirse aşağıdaki V1 referansı ADR revizyonunda güncellenip yeniden
onaylanmalıdır. Bu bölüm mevcut Cognito kararı altında bağlayıcı değildir:

- Mobil istemci, secret içermeyen public OIDC client'tır. Yalnız Authorization Code Flow +
  PKCE `S256` etkin olur; implicit flow ve Resource Owner Password Credentials/Direct Access
  Grants kapalıdır. Redirect URI'ler iOS ve Android uygulama bağlantıları için tam ve
  allow-listli tanımlanır; joker redirect URI kullanılmaz.
- Keycloak yalnız doğrulanmış global kullanıcı kimliğini (`iss`, `sub`, `aud`, `azp`, `exp`,
  `iat`, `sid` gerektiğinde) verir. Mobil istemci `state`, `nonce` ve PKCE `code_verifier`ını
  üretir/saklar; callback'te eşleştirir. IAM yalnız Keycloak **access tokenını** JWKS, `iss`,
  `aud`, `azp`, süre ve scope ile doğrular; ID tokenı API veya token değişimi için kabul etmez.
  Kurum kimliği, kurum rolü, devredilmiş izin ve sınıf
  ataması access-token claim'i olarak yetki kaynağı değildir.
- IAM, geçerli OIDC sonucu sonrasında §4.10a'daki tek kullanımlı, 5 dakikalık opaque
  `context_selection_tokens` değerini üretir. Kullanıcı bir kurum seçtiğinde IAM; etkin üyelik,
  geri alınmamış rol ve güncel
  `session_generation` denetimini yaparak kurum-kapsamlı platform access/refresh token
  çifti üretir. Kurum API'leri yalnız bu platform access token'ını kabul eder. Böylece
  `API_GENEL_KURALLARI.md` §4'teki token değişimi ve `VERI_MODELI.md` §4.11'deki model
  korunur.
- Platform access token'ı JWT değil, en az 256-bit rastgele **opaque** bearer değerdir ve
  DB'de `HMAC-SHA-256(pepper, token)` özetiyle doğrulanır; ayrı JWT claim/anahtar/iptal listesi
  yoktur. Access token ömrü **10 dakika**dır. Platform refresh tokenı da opaque, dönen ve
  tek kullanımlıktır; DB yalnız pepper'lı HMAC özetlerini tutar. Token
  yenileme, token özetini, ilişkili güvenilir cihazı, üyeliğin canlı durumunu ve
  `session_generation` değerini doğrular. Keycloak'taki global güvenlik olayları IAM'de
  bu tokenların iptal edilmesiyle eşlenir. Eşzamanlı yenileme yarışları tek kullanımlı
  tokenın ikinci kez başarı sayılmasına izin vermez; istemci güvenli şekilde yeniden giriş ister.
- İlk başarılı Keycloak→platform token değişiminden sonra mobil, Keycloak access ve refresh
  tokenlarını OS güvenli saklamasından siler. Kalıcı oturum yalnız platform opaque refresh
  tokenıyla sürer; Keycloak refresh tokenı platform DB'sine veya IAM refresh endpointine hiç
  gönderilmez. Böylece `DEVICE_SESSION_REVOKE` sonrası eski sağlayıcı tokenı sessizce yeni
  platform oturumu oluşturamaz. Sağlayıcı kesintisinde halen geçerli platform access/refresh
  tokenları, canlı üyelik ve iptal kontrolü geçtikçe çalışır; yeni cihaz kaydı veya etkileşimli
  giriş başarısız olur ve istemci kuyruktaki yazmayı silmez.
- Platform refresh tokenının ham değeri yalnız işletim sisteminin güvenli saklamasında tutulur.
  `refresh_tokens.token_hash`, SHA-256 ile hesaplanmış **pepper'lı HMAC** özetidir; pepper
  yalnız secret yöneticisinde bulunur. Ham platform veya Keycloak refresh token'ı uygulama
  veritabanı, audit kaydı, log, hata zarfı ya da telemetry'ye yazılmaz.
- Her platform refresh tokenı `family_id` ve `previous_refresh_token_id` ile dönen aileye
  bağlıdır. Yenileme işleminde eski satır kilitlenir ve `used_at` bir kez yazılır; yeni token
  aynı ailede yeni satır olur. Tüketilmiş eski token yeniden sunulursa çalınma varsayılır,
  ilgili ailenin tüm refresh/access tokenları atomik iptal edilir ve `SESSION_REVOKED` döner.
  Eşzamanlı iki yenilemeden yalnız biri başarılıdır; aile iptali başka kurum/aileyi etkilemez.
- Kullanıcı oluşturma, devre dışı bırakma ve parola sıfırlama yalnız backend'in confidential
  Keycloak yönetim istemcisi üzerinden yapılır. Mobil client'ın yönetim API'si, client secret,
  servis hesabı veya parola yönetim yetkisi yoktur.
- Kurum yöneticisinin `DEVICE_SESSION_REVOKE` işlemi, yalnız hedef kullanıcının kendi
  kurumundaki platform refresh tokenlarını iptal eder ve o üyeliğin `session_generation`
  değerini atomik artırır. Bu işlem Keycloak'ta global kullanıcı logout'u çağırmaz. Kullanıcının
  kendi global cihazını kaldırması veya platform yöneticisinin global güvenlik iptali ayrı,
  denetimli akıştır ve tüm kurum bağlamlarını etkileyebilir.
- İlk kullanıcı oluşturma akışında worker, Keycloak create çağrısı için rastgele tek kullanımlı
  geçici parola üretir ve `UPDATE_PASSWORD` required action'ını ekler. Keycloak kullanıcı
  oluşturması/uzlaştırması doğrulanınca yalnız bir kez görüntülenebilen, oluşturulduğu andan
  itibaren **en fazla 10 dakika** geçerli, A-013 anahtarıyla şifreli teslim sonucu oluşturulur.
  Ham parola log, audit, normal uygulama tablosu veya kalıcı idempotency sonucuna yazılmaz;
  teslim süresi geçince tekrar gösterilmez ve yeni parola sıfırlama komutu gerekir. Teslim
  isteğinde alıcının aynı kurumdaki canlı üyeliği ile `ORG_ADMIN` rolü veya
  `TEACHER_ACCOUNT_MANAGE` izni yeniden doğrulanır; yalnız provisioning'i başlatmış olmak sonraki
  teslim için kalıcı yetki oluşturmaz. Mobil ilk girişte action tamamlanmadan platform tokenı
  alamaz.
- Parola sıfırlama, şüpheli hesap ele geçirme, global kullanıcı devre dışı bırakma ve kullanıcının
  kendi tüm cihazlarından çıkışı hem Keycloak oturum/refresh tokenlarını hem IAM tarafındaki
  ilgili platform tokenlarını iptal eden güvenlik akışlarıdır. Keycloak yönetim çağrısı ve IAM
  iptali dağıtık tek transaction sayılmaz: IAM, erişimi önce yerel olarak keser; sağlayıcı çağrısı
  başarısız olursa denetlenmiş, kalıcı tekrar işiyle tamamlanır. Mevcut platform access tokenları
  her istekte canlı üyelik ve `session_generation` kontrolüyle en geç sonraki istekte reddedilir.

## Keycloak fallback — token yaşam döngüsü ve global uzlaştırma

1. Mobil, sistem tarayıcısında Code + PKCE `S256` akışını yürütür; `state`/`nonce` callback
   eşleşmesini doğrular. İlk başarılı değişimden hemen sonra Keycloak access/refresh tokenlarını
   siler ve yalnız platform tokenlarını OS güvenli saklamasında tutar.
2. Mobil, Keycloak access tokenını IAM token-değişim ucuna sunar. İmza/JWKS, `iss`, `aud`,
   `azp`, `exp` ve scope doğrulaması tamamlanınca sunucu `SET LOCAL
   app.iam_operation_scope='AUTHENTICATION'`, `app.iam_provider_issuer` ve
   `app.iam_provider_subject` değerlerini yalnız bu token claim'lerinden kurar. Bu kapsam
   `user_identities` üzerinde yalnız tam issuer+subject eşleşmesiyle tek satır `SELECT` yapar;
   listeleme, wildcard, başka identity okuma ve yazma yapamaz. Eşleşen `user_id` öğrenilince
   önce provider ayarları temizlenir; global kullanıcı veya token tablosuna erişmeden önce yalnız
   açık `GLOBAL` target-user scope'u kurulabilir. Aynı transaction'da kapsamlar birikemez.
   Eşleme yoksa
   yalnız yetkili yönetici oluşturma akışı yeni eşleme üretir; çakışma güvenli hata ve audit verir.
3. IAM, eşleşen aktif kullanıcı ve cihaz için 5 dakikalık tek kullanımlı context-selection tokenı
   üretir. Üyelik listeleme tokenı tüketmez; kurum seçimi, `consumed_at` yazılması ve platform
   opaque access+refresh ailesinin üretimi aynı transaction'dadır. API'ler yalnız platform access
   tokenını kabul eder. Platform refresh, Keycloak'a çağrı yapmaz; kendi token ailesini döndürür
   ve ilgili üyelik/cihaz/iptal durumunu atomik kontrol eder.
4. Keycloak'ta global iptal/deaktif/parola reset olayı oluştuğunda management akışı önce IAM'de
   tüm ilgili platform token ailelerini iptal eder, sonra sağlayıcı çağrısını tamamlar. Sağlayıcı
   olayı veya çağrısı başarısızsa denetlenmiş kalıcı uzlaştırma işi tekrarlar; tekrar güvenli
   biçimde idempotenttir. Kurum yöneticisinin `DEVICE_SESSION_REVOKE` işlemi bu global akışı
   kullanmaz ve Keycloak global oturumunu kapatmaz.

5. `DEVICE_SESSION_REVOKE` sonrası cihaz kaydı ancak yeni etkileşimli Keycloak girişiyle yapılır.
   Mobil, bu yeniden kayıt isteğinde `prompt=login` (veya Keycloak'ta aynı anlama gelen zorunlu
   yeniden kimlik doğrulama) gönderir; sistem tarayıcısındaki eski SSO çerezi sessiz giriş için
   kullanılamaz. Kurum-kapsamlı iptal yalnız hedef üyeliğin platform token ailelerini etkiler;
   başka kurumlardaki platform tokenları değişmez.

Keycloak clientı güvenilir `auth_time` claim'ini access tokena ekleyecek biçimde yapılandırılır.
IAM, yeni güvenilir cihaz, kurum token ailesi, platform yöneticisi global ailesi veya iptal
sonrası yeniden kayıt üretirken doğrulanmış `auth_time > users.reauthentication_required_after`
ve üyelik ailesinde ayrıca `auth_time > organization_memberships.reauthentication_required_after`
koşullarını zorunlu kılar. `auth_time`
taşımayan token veya eski SSO çereziyle alınmış eski `auth_time`, yeni cihaz/üyelik oturumu
oluşturamaz. `prompt=login` istemci yardımıdır; bağlayıcı güvenlik sınırı sunucu kontrolüdür.

### Güvenilir cihaz oturumu

V1'de 14/30 günlük zorunlu periyodik yeniden giriş yoktur. Güvenilir cihaz oturumu, kullanıcı
çıkış yapana, yetkili yönetici iptal edene, global güvenlik olayı oluşana veya cihazın güvenli
saklaması kaybolup/temizlenene kadar dönen platform refresh tokenıyla sürer.

## Keycloak fallback — IAM veritabanı erişim referansı

ADR-003 §5.3 uyarınca seçilen mekanizma **ayrı `iam_runtime` PostgreSQL rolüdür**; `app_runtime`
bu yüzeyde doğrudan tablo/sequence hakkı taşımaz. `iam_runtime` owner, superuser veya
`BYPASSRLS` olamaz; migration owner ayrıdır.

| Fiziksel tablo | `iam_runtime` SQL hakkı | Zorunlu sınır |
|---|---|---|
| `users` | `SELECT`, `UPDATE` | `GLOBAL` `FORCE RLS`; yalnız doğrulanmış `app.iam_target_user_id` hedefi. |
| `users` | `SELECT`, `INSERT` | İlk `IAM_PROVISIONING` `FORCE RLS`; yalnız önceden üretilmiş target, actor, kurum ve `TEACHER_ACCOUNT_CREATE`; `UPDATE`/`DELETE` yok. |
| `user_identities` | `SELECT`, `INSERT` | Yalnız `app.iam_operation_scope='IAM_PROVISIONING'` + `app.iam_operation_code='TEACHER_ACCOUNT_FINALIZE'`; `Location`tan alınmış subject, aynı command/target ve doğrulanmış `platform_user_id` attribute'u ile; ilk transaction'da erişim yok. |
| `user_identities` | `SELECT` | `AUTHENTICATION` `FORCE RLS`; yalnız server-set `app.iam_provider_issuer` + `app.iam_provider_subject` tam eşleşmesi tek satırı açar. Listeleme/wildcard/başka identity ve yazma yok. |
| `user_identities` | `SELECT` | `GLOBAL` yalnız `USER_DISABLE`, `USER_LOGOUT` ve `PASSWORD_RESET` için; server-set, transaction-scoped `app.iam_target_identity_id` ile `id` ve server-set `app.iam_target_user_id` ile `user_id` birlikte eşleşmelidir. Eksik/geçersiz/başka kullanıcı identity değeri fail-closed reddedilir; genel identity listeleme yoktur. |
| `users` | `SELECT`, dar `UPDATE` | `IAM_AUTH` `FORCE RLS`; normal akışta yalnız `id = app.iam_actor_user_id` satırını açar. `PLATFORM_ADMIN_ACTIVATE` veya `CONTEXT_ACTIVATE` sırasında sunucunun doğruladığı canonical provider sonucu `DISABLED`/`REVOKED` ise `WITH CHECK` yalnız `reauthentication_required_after` alanının transaction zamanına yükseltilmesine izin verir; başka alan ve başka kullanıcı update'i yoktur. |
| `platform_administrators`, `platform_administrator_profiles` | `SELECT` | `FORCE RLS`; `platform_administrators`, `IAM_AUTH` `PLATFORM_ADMIN_ACTIVATE`te aktörün kendi aktif kaydını ve IAM-002 `GLOBAL` `PLATFORM_DEVICE_REVOKE`te yine yalnız `user_id = app.iam_actor_user_id` + `revoked_at IS NULL` kaydını açar. `platform_administrator_profiles` yalnız `IAM_AUTH` `PLATFORM_ADMIN_ACTIVATE` kapsamında açılır. `ORG_CREATE`/`ORG_LIST` ve ORGANIZATION scope platform-administrator policy'leri `iam_runtime`a ait değildir; runtime rolü ve `TO <role>`/`GRANT` ifadeleri ORG-003'te kesinleşir. |
| `trusted_devices` | `SELECT` | `FORCE RLS`; üç ayrı adlandırılmış `PERMISSIVE` policy, **üçü de scope+operation guard'ını doğrudan `USING` predicate'i içinde taşır** (yalnız policy adı/açıklaması değil): `trusted_devices_select_self` → `USING (app.iam_operation_scope = 'IAM_AUTH' AND app.iam_operation_code IN ('DEVICE_LIST','DEVICE_SELF_REVOKE') AND user_id = app.iam_actor_user_id)` — aktörün **kendi bütün cihazları** (aktif + iptal), liste/kendi-iptal ekranı için; `trusted_devices_select_provider_exchange` → `USING (app.iam_operation_scope = 'IAM_AUTH' AND app.iam_operation_code = 'PROVIDER_TOKEN_EXCHANGE' AND user_id = app.iam_actor_user_id AND device_identifier = app.iam_provider_device_identifier)` — yalnız istekte sunulan **tek** `device_identifier`in geçmişi (aktif + iptal), aktörün başka cihazları bile görünmez; `app.iam_provider_device_identifier` yalnız sunucunun doğrulanmış istek gövdesinden kurduğu server-set değerdir. `trusted_devices_select_platform_revoke` → `USING (app.iam_operation_scope = 'GLOBAL' AND app.iam_operation_code = 'PLATFORM_DEVICE_REVOKE' AND user_id = app.iam_target_user_id AND id = app.iam_target_device_id)` — **bu policy artık scope+operation guard'sız değildir**; önceki sürümde yalnız `user_id`/`id` eşleşmesi vardı ve `app.iam_operation_scope`/`app.iam_operation_code` guard'ı eksikti, bu düzeltilmiştir. PostgreSQL bu üç `PERMISSIVE` policy'yi varsayılan olarak `OR` ile birleştirir; güvenlik, ayrı policy olmalarından değil, her birinin scope+operation guard'ının **karşılıklı dışlayıcı** olmasından gelir (bir transaction'da `app.iam_operation_scope` ve `app.iam_operation_code` tam olarak tek bir çift taşır — §12), bu yüzden `OR` birleşiminde aynı anda yalnız kendi guard'ı `true` olan policy katkı yapar. |
| `trusted_devices` | `INSERT` | `FORCE RLS`; `trusted_devices_insert_provider_exchange` → `WITH CHECK (app.iam_operation_scope = 'IAM_AUTH' AND app.iam_operation_code = 'PROVIDER_TOKEN_EXCHANGE' AND user_id = app.iam_actor_user_id AND device_identifier = app.iam_provider_device_identifier AND NOT EXISTS (SELECT 1 FROM trusted_devices old WHERE old.user_id = app.iam_actor_user_id AND old.device_identifier = app.iam_provider_device_identifier AND old.revoked_at IS NOT NULL AND old.revoked_at >= app.iam_verified_auth_time))` — `app.iam_operation_code = 'PROVIDER_TOKEN_EXCHANGE'` guard'ı olmadan bu tek `INSERT` policy'si, `IAM_AUTH` scope'unda `app.iam_actor_user_id`/`app.iam_provider_device_identifier`i tesadüfen dolduran **başka bir** operation code için de geçerli olurdu; bu satır artık her zaman doğrudan predicate içinde kontrol edilir. `old` burada bir korelasyon takma adı (self-join subquery), trigger `OLD` kaydı **değildir**; RLS ifadelerinde trigger `NEW`/`OLD` kayıtları yoktur. Aynı `(user_id, device_identifier)` çiftinin **en son** `revoked_at`ından eski/eşit doğrulanmış `auth_time` ile yeni satır açılamaz (cihaz-bazlı reauth bariyeri, IAM-002). Bu `NOT EXISTS` alt sorgusu yukarıdaki `trusted_devices_select_provider_exchange` policy'sinin **aynı** `device_identifier` eşleşmesiyle görünür kıldığı satırları okur; `app.iam_verified_auth_time` ve `app.iam_provider_device_identifier` yalnız server-set'tir, istemci gövdesinden doğrudan kurulamaz. Bu `INSERT`, §12.0'daki mantıksal cihaz kilidi alınmadan çalışamaz (bkz. IAM-002 eklentisi). |
| `trusted_devices` | `UPDATE (revoked_at)` — **column-level `GRANT`**; `iam_runtime`e tablo geneli `UPDATE` **verilmez** | `FORCE RLS`; iki ayrı adlandırılmış policy, ikisi de scope+operation guard'ını doğrudan predicate içinde taşır: `trusted_devices_update_self_revoke` → `USING (app.iam_operation_scope = 'IAM_AUTH' AND app.iam_operation_code = 'DEVICE_SELF_REVOKE' AND user_id = app.iam_actor_user_id AND id = app.iam_target_device_id AND revoked_at IS NULL)`; `WITH CHECK (revoked_at = transaction_timestamp())`. `trusted_devices_update_platform_revoke` → `USING (app.iam_operation_scope = 'GLOBAL' AND app.iam_operation_code = 'PLATFORM_DEVICE_REVOKE' AND user_id = app.iam_target_user_id AND id = app.iam_target_device_id AND revoked_at IS NULL)`; `WITH CHECK (revoked_at = transaction_timestamp())`. **İki `UPDATE` policy'si de artık scope+operation guard'sız değildir** — önceki sürümde yalnız `user_id`/`id`/`revoked_at IS NULL` vardı; bu, `app.iam_target_device_id`i tesadüfen dolduran **başka bir** `IAM_AUTH`/`GLOBAL` operation code'unun da bu satırı güncelleyebilmesine izin verirdi. RLS burada `NEW`/`OLD` kaydı **karşılaştırmaz** (bu geçersiz sözdizimidir); `USING`teki `revoked_at IS NULL`, yalnız halihazırda **aktif** ve doğru sahiplikteki/scope+operation guard'ına uyan satırı hedef almaya izin verir — zaten iptal edilmiş bir satır bu `USING`den geçemediği için **ikinci kez hiçbir yönde** (ne `NULL`a ne başka bir zaman damgasına) hedef alınamaz; bu davranış `WITH CHECK`ten değil `USING`in kendisinden gelir, terminal durumun değişmezliği için `WITH CHECK`e ayrıca ihtiyaç yoktur. `WITH CHECK (revoked_at = transaction_timestamp())`, yazılan değerin sunucunun **kendi** transaction başlangıç zamanına tam eşit olmasını zorunlu kılar — istemci/uygulama katmanının keyfî bir geçmiş veya gelecek zaman damgası göndermesi (örn. hatalı saat senkronizasyonu veya kasıtlı manipülasyon) reddedilir; `column-level GRANT`, hangi kolonun yazılabileceğini sınırlar, yazılan **değerin** doğruluğunu sınırlamaz — bu, `WITH CHECK`in işidir. `device_identifier`, `user_id`, `platform`, `device_name`, `trusted_at` sütunlarına yazma, RLS'in değil **`iam_runtime`e bu kolonlar için hiç `GRANT UPDATE` verilmemiş olmasının** sonucu olarak SQL privilege seviyesinde reddedilir (bkz. aşağıdaki "IAM-002 — ... RLS eklentisi" `GRANT` bloğu). `last_seen_at`, bu policy'nin/`GRANT`ın kapsamında **değildir**; güncellenmesi gerekiyorsa ayrı bir operation code ve ayrı `GRANT UPDATE (last_seen_at)` ile tanımlanır — revoke işlemlerine bu kolon için hiç yazma izni verilmez (V1'de bu ayrı akış tanımlı değildir, IAM-002 kapsamı dışıdır). `DELETE` yoktur. Bu `UPDATE`, aşağıdaki "IAM-002 — ... RLS eklentisi" bölümündeki **Faz C** (kilit-sonrası yeniden okuma + doğrulama) tamamlanmadan çalışamaz; `app.iam_target_device_id`, path parametresinden hemen `SET LOCAL` yapılabilir (bu sadece bilinen bir yol parametresidir, gizli/keşfedilmiş bir değer değildir) — ama bu `UPDATE`in kendisi yalnız Faz C'den sonra yürütülür. |
| `organization_memberships` | `SELECT`, `UPDATE` | `ORGANIZATION` `FORCE RLS`; `SET LOCAL app.organization_id` ve hedef üyelik birlikte eşleşir. `DEVICE_SESSION_REVOKE`te `WITH CHECK` yalnız `session_generation` (+1) ve `reauthentication_required_after` (işlem zamanı) alanlarına yazar; `trusted_devices`e dokunmaz. Platform yöneticisinin destek amaçlı çağrısında sunucu, aktif `platform_administrators` kaydını doğruladıktan **sonra** dar bir kod yolunda `app.iam_operation_scope='ORGANIZATION'` + `app.organization_id`=hedef kurum kurar (normal `ORG_ADMIN`/`TEACHER` rol/izin `USING` koşulu bu yolda **bypass edilmez**; bunun yerine ayrı bir `app.iam_platform_admin_support_access=true` server-set bayrağı, rol/izin `USING` koşulunu bu bayrakla `OR` bağlar) ve aynı transaction `PLATFORM_ADMIN_ORG_ACCESS` audit satırını da yazar (bkz. `VERI_MODELI.md` §13.2 global örnekler). Bu bayrak istemci parametresiyle kurulamaz. |
| `organization_membership_roles`, `organization_membership_permissions`, `class_teacher_assignments` | `SELECT` | `FORCE RLS`; yalnız `app.organization_id` kapsamı. |
| `people`, `organization_memberships`, `organization_membership_roles` | `SELECT`, `INSERT` | İlk `IAM_PROVISIONING` `FORCE RLS`; yalnız actorun kurumunda, önceden üretilmiş tek targetta ve `TEACHER_ACCOUNT_CREATE`; `UPDATE`/`DELETE` yok. |
| `users`, `organization_memberships`, `iam_secret_deliveries` | `SELECT`, `INSERT`, gerekli `UPDATE` | Yalnız `app.iam_operation_scope='IAM_PROVISIONING'` + `app.iam_operation_code='TEACHER_ACCOUNT_FINALIZE'`; aynı command/targetta identity + `ACTIVE` + süresi geçmemiş teslim `READY` atomik yazılır. Başka hedef/kurum/command, `DELETE` ve alan dışı update reddedilir. |
| `context_selection_tokens` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`; `IAM_AUTH` scope'unda `USING (user_id = app.iam_actor_user_id AND EXISTS (SELECT 1 FROM trusted_devices td WHERE td.id = trusted_device_id AND td.user_id = app.iam_actor_user_id AND td.revoked_at IS NULL))` ile yalnız aynı actor kullanıcıya ait aktif `trusted_devices` zincirindeki token açılır. `WITH CHECK` üretimde yalnız aynı actor/cihaz zincirine, tüketimde yalnız `PLATFORM_ADMIN_ACTIVATE` veya `CONTEXT_ACTIVATE` operation code'una izin verir; `CONTEXT_ACTIVATE`te ayrıca `app.iam_target_membership_id` + `app.iam_target_organization_id` eşleşmesi zorunludur. `UPDATE` yalnız koşullu `consumed_at`/`revoked_at`; aile üretimiyle aynı transaction'dadır. |
| `organization_memberships` | `SELECT` | `IAM_AUTH` `FORCE RLS`; yalnız `CONTEXT_ACTIVATE`te `USING (id = app.iam_target_membership_id AND user_id = app.iam_actor_user_id AND organization_id = app.iam_target_organization_id)` açılır. Yazma yoktur. |
| `organization_membership_roles` | `SELECT` | `IAM_AUTH` `FORCE RLS`; yalnız `CONTEXT_ACTIVATE`te `USING (organization_membership_id = app.iam_target_membership_id AND revoked_at IS NULL)` açılır. Yazma yoktur. |
| `platform_administrators` | `SELECT` | `FORCE RLS`; `IAM_AUTH` `PLATFORM_ADMIN_ACTIVATE` ve IAM-002 `GLOBAL` `PLATFORM_DEVICE_REVOKE` dışında açılmaz. Her iki policy de yalnız `USING (user_id = app.iam_actor_user_id AND revoked_at IS NULL)` ile çağıranın kendi aktif admin kaydını açar; yazma yoktur. |
| `refresh_token_families` | `SELECT`, `INSERT`, `UPDATE` | `IAM_AUTH`te `CONTEXT_ACTIVATE` için `USING` yalnız hedef üyeliğin `user_id + trusted_device_id + organization_membership_id` zincirini; `WITH CHECK` yalnız aynı actor/cihaz/üyelik/kurum zincirinde yeni family yaratmayı açar. `PLATFORM_ADMIN_ACTIVATE` için `USING` aktif admin actor + cihaz zincirini; `WITH CHECK` yalnız `organization_membership_id IS NULL` global family insertini açar. `SESSION_REFRESH`/`SESSION_LOGOUT` için `USING` yalnız aynı actor-device-family zincirini; `UPDATE` yalnız `revoked_at` veya refresh güvenlik alanlarını değiştirir. Canonical provider sonucu `DISABLED`/`REVOKED` ise yalnız allow-listli `PLATFORM_ADMIN_ACTIVATE`/`CONTEXT_ACTIVATE` operation code'u altında `USING (user_id = app.iam_actor_user_id AND current_setting('app.iam_canonical_provider_status', true) IN ('DISABLED','REVOKED'))` ile aktörün bütün ailelerini açar; `WITH CHECK` yalnız `revoked_at` yazımına izin verir. `DEVICE_SELF_REVOKE`te `USING (trusted_device_id = app.iam_target_device_id AND user_id = app.iam_actor_user_id)` ile yalnız aktörün **kendi** hedef cihazına bağlı bütün aileleri (kurum + varsa global) açar; `WITH CHECK` yalnız `revoked_at` yazımına izin verir. `DEVICE_SESSION_REVOKE`, bu tabloya `IAM_AUTH` scope'undan **erişmez** — bu işlem `ORGANIZATION` scope'unda, üstteki `organization_memberships` satırıyla aynı transaction'da, `USING (organization_membership_id = app.iam_target_membership_id AND organization_id = app.organization_id)` ile açılır; `WITH CHECK` yalnız `revoked_at` yazımına izin verir. |
| `refresh_tokens` | `SELECT`, `INSERT`, `UPDATE` | `IAM_AUTH`te `CONTEXT_ACTIVATE` ve `PLATFORM_ADMIN_ACTIVATE` için `WITH CHECK` yalnız yukarıdaki açılmış family içinde ilk token insertini açar. `SESSION_REFRESH`te `USING` yalnız aynı actor-device-family zincirindeki mevcut tokenı açar; `WITH CHECK` yalnız aynı family içinde ardıl token insertine izin verir. `SESSION_LOGOUT`ta `USING` yalnız aynı actor-device-family zincirini açar; `UPDATE` yalnız `revoked_at` alanını değiştirir. Canonical provider sonucu `DISABLED`/`REVOKED` ise yalnız allow-listli `PLATFORM_ADMIN_ACTIVATE`/`CONTEXT_ACTIVATE` operation code'u altında `USING (EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = refresh_tokens.family_id AND rtf.user_id = app.iam_actor_user_id AND current_setting('app.iam_canonical_provider_status', true) IN ('DISABLED','REVOKED')))` ile aynı aktörün ailelerine bağlı tokenları açar; `WITH CHECK` yalnız `revoked_at` yazımına izin verir. `DEVICE_SELF_REVOKE`te `USING (EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = refresh_tokens.family_id AND rtf.trusted_device_id = app.iam_target_device_id AND rtf.user_id = app.iam_actor_user_id))`; `WITH CHECK` yalnız `revoked_at`. `DEVICE_SESSION_REVOKE` bu satıra `IAM_AUTH`tan erişmez; `ORGANIZATION` scope'unda `USING (EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = refresh_tokens.family_id AND rtf.organization_membership_id = app.iam_target_membership_id))` ile açılır; `WITH CHECK` yalnız `revoked_at`. |
| `refresh_token_families` | `SELECT`, `UPDATE` | Global güvenlik komutu (`IAM-004`, tam hesap): `FORCE RLS`, `app.iam_operation_scope='GLOBAL'` ve `SET LOCAL app.iam_target_user_id`; yalnız açık hedef kullanıcının tüm kurum/global aileleri, tablo geneli değil. `PLATFORM_DEVICE_REVOKE` bu geniş policy'yi **kullanmaz**; ayrı ve daha dar bir `GLOBAL` policy ile `USING (user_id = app.iam_target_user_id AND trusted_device_id = app.iam_target_device_id)` — yalnız hedef **cihaza** bağlı aileler, kullanıcının diğer cihazları değil; `WITH CHECK` yalnız `revoked_at`. Global policy `INSERT` içermez. |
| `refresh_tokens` | `SELECT`, `UPDATE` | Global güvenlik komutu (`IAM-004`): aynı geniş `GLOBAL` policy; yalnız hedef kullanıcının ailelerinin refresh/access token satırları. `PLATFORM_DEVICE_REVOKE` için ayrı dar `GLOBAL` policy: `USING (EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = refresh_tokens.family_id AND rtf.user_id = app.iam_target_user_id AND rtf.trusted_device_id = app.iam_target_device_id))`; `WITH CHECK` yalnız `revoked_at`. Global policy `INSERT` içermez. |
| `permission_catalog`, `audit_action_catalog` | `SELECT` | Salt okunur global katalog; tenant RLS listesinde değildir ve yazma hakkı yoktur. |
| `audit_logs` | `SELECT`, `INSERT` | `FORCE RLS`: `ORGANIZATION` komutu yalnız `app.organization_id` (`DEVICE_SESSION_REVOKE` ve destek amaçlı `PLATFORM_ADMIN_ORG_ACCESS` dahil); `GLOBAL` güvenlik komutu yalnız `event_scope='GLOBAL'`, `target_entity_type='USER'`, `target_entity_id=app.iam_target_user_id` (`PLATFORM_DEVICE_REVOKE` bu satırı, `event_metadata`de hedef `deviceId` ile kullanır); `IAM_AUTH` yalnız allow-listli auth güvenlik olaylarını (`PROVIDER_TOKEN_EXCHANGE`, `PLATFORM_ADMIN_ACTIVATE`, `CONTEXT_ACTIVATE`, `SESSION_REFRESH`, `SESSION_LOGOUT`, `DEVICE_SELF_REVOKE`) yazar. `WITH CHECK`, `PROVIDER_TOKEN_EXCHANGE`te actor+cihaz, `PLATFORM_ADMIN_ACTIVATE`te actor+cihaz+global family veya server-set provider güvenlik sonucu, `CONTEXT_ACTIVATE`te actor+cihaz+üyelik+kurum veya server-set provider güvenlik sonucu, `SESSION_REFRESH`/`SESSION_LOGOUT`ta actor+cihaz+family zincirini, `DEVICE_SELF_REVOKE`te actor+çağıran cihaz+hedef cihaz zincirini zorunlu kılar. Provisioning yalnız kendi `organization_id`/actor/target kaydını yazar. Başka kapsam varsayılan reddir. `UPDATE`, `DELETE` yoktur. Aynı `Idempotency-Key`nin güvenli replay'i ikinci `INSERT` üretmez (uygulama katmanı ilk sonucu döner, transaction hiç açılmaz); farklı `Idempotency-Key` ile zaten terminal (iptal edilmiş) bir hedefe gelen istek ise **yeni** bir `INSERT` üretir — durum mutasyonu yapmadan "zaten iptal edilmişti" sonucunu kaydeder (bkz. `IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md` §13). |
| `idempotency_keys` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`: `ORGANIZATION` için `scope_type='ORGANIZATION'`, `organization_id=app.organization_id` ve `user_id=app.iam_actor_user_id` (`DEVICE_SESSION_REVOKE` bu dala girer); `GLOBAL` için `scope_type='GLOBAL'`, `organization_id IS NULL` ve `user_id=app.iam_actor_user_id` (`PLATFORM_DEVICE_REVOKE` bu dala girer, `operation_type='PLATFORM_DEVICE_REVOKE'`); `IAM_AUTH` için `scope_type='IAM_AUTH'`, `organization_id IS NULL`, `user_id=app.iam_actor_user_id` ve `operation_type=app.iam_operation_code` (`DEVICE_SELF_REVOKE` bu dala girer). Tekil `UNIQUE (user_id, client_mutation_id) WHERE scope_type='IAM_AUTH'` DB kısıtı bütün `IAM_AUTH` işlemleri için (`DEVICE_SELF_REVOKE` dahil) aynen kalır; `operationType`, çağıran `deviceIdentifier`, token fingerprint, yöntem/yol ve hedef (`deviceId`/`organizationMembershipId`/`userId`) ise DB kısıtının değil, `request_fingerprint` kolonunun girdisidir. `USING`/`WITH CHECK` aynı actor + operation code satırını zorunlu kılar; aynı kullanıcı için aynı `client_mutation_id` farklı `operation_type` veya farklı `request_fingerprint` ile yeniden kullanılırsa fingerprint çakışması `409` üretir. Provisioning yalnız aynı actor+kurum+operationCode kaydını yazar. `user_id` P-010'daki aktördür, hedef değildir. |
| `iam_auth_response_escrows` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`; yalnız `app.iam_operation_scope='IAM_AUTH'`, `actor_user_id=app.iam_actor_user_id`, `operation_type=app.iam_operation_code` ve aynı actor/operation'a bağlı idempotency kaydı. `WITH CHECK`, `PROVIDER_TOKEN_EXCHANGE`te yalnız context token bağını, `PLATFORM_ADMIN_ACTIVATE`te global family+token bağını, `CONTEXT_ACTIVATE`te üyelik/kurum family+token bağını, `SESSION_REFRESH`te aynı actor-device-family ardıl token bağını açar. `SESSION_LOGOUT` escrow allow-listte değildir. Cleanup `UPDATE` için `USING` yalnız `status='READY' AND (expires_at <= transaction_time OR current_setting('app.iam_security_revoke_required', true) = 'true')` satırlarını açar; `WITH CHECK` yalnız `status IN ('EXPIRED','REVOKED')`, `deleted_at IS NOT NULL`, `ciphertext IS NULL`, `aead_key_reference IS NULL`, `aead_nonce IS NULL`, `aad_context IS NULL` new-row geçişini açar. Aynı `UPDATE` içinde terminal durum + purge zorunludur; zaten terminal satırdaki idempotent tekrar no-op olabilir. Başka aktör/operation varsayılan reddir. |
| `iam_provider_commands` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`: `GLOBAL` bağlamda yalnız target identity→user; `TEACHER_ACCOUNT_CREATE` provisioning'de yalnız `target_user_id`, actor/kurum/username-hash ve şifreli payload ile `INSERT`. Command-type XOR hedef türünü zorunlu kılar; lease/fencing kontrolü yazmada zorunludur. |
| `iam_secret_deliveries` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`; yalnız aynı create command ve target. Secret yalnız finalize sonrası, en fazla 10 dakikalık bir-kezlik okuma için `READY` olur. Okuma anında alıcının aynı kurumda etkin üyeliği ve geri alınmamış `ORG_ADMIN` rolü veya etkin `TEACHER_ACCOUNT_MANAGE` izni yeniden doğrulanır; yalnız `recipient_actor_user_id` eşleşmesi yeterli değildir. `ESCROWED → READY/EXPIRED` ve `READY → CONSUMED/EXPIRED` geçişlerinde terminal new-row görünürlüğü yalnız durum işlemi içindir: trigger/check aynı atomik update'te `encrypted_secret` ve `payload_key_id` alanlarını `NULL` zorunlu kılar. Terminal satır yeniden okunsa bile gizli materyal elde edilemez; terminal durumdan update yoktur. |
| `iam_event_cursors` | `SELECT`, `INSERT`, `UPDATE` | Yalnız `ADMIN_EVENTS` veya `USER_EVENTS` kaynak satırı; cursor ilerlemesi başarılı işlemle koşulludur. |
| `iam_event_deduplications` | `SELECT`, `INSERT` | Yalnız `(source, event_id)` benzersiz anahtarı; runtime silme/güncelleme yapamaz. |

`iam_runtime` owner, superuser veya `BYPASSRLS` olamaz; sahibi olduğu nesne bulunmaz. Migration
owner ayrı kalır ve production runtime'da kullanılmaz. Bu rol, tenant tablolarda `SET LOCAL`
ile kurulan kurum RLS bağlamını baypas eden genel erişim yolu değildir. Her DB transaction'ı
başta `SET LOCAL app.iam_operation_scope` ile tam olarak `ORGANIZATION`, `GLOBAL`, `IAM_AUTH`,
`AUTHENTICATION` veya `IAM_PROVISIONING` seçer; hiçbir kapsam genel GLOBAL+ORGANIZATION birleşimi
değildir. Normal iki scope doğrulanmış `app.iam_actor_user_id` ister. `ORGANIZATION`, geçerli
`app.organization_id` ister ve `app.iam_target_user_id` boş olmalıdır; `GLOBAL`, geçerli
`app.iam_target_user_id` ister ve `app.organization_id` boş olmalıdır. Aktör ve hedef GLOBAL
işlemde ayrı bağlamlardır. `AUTHENTICATION` yalnız doğrulanmış access tokendan server-set
`app.iam_provider_issuer`+`app.iam_provider_subject` ister; actor/target/organization ve bütün
diğer tablo yüzeyleri boş/kapalıdır ve `user_identities` dışında mutation açmaz. `PROVIDER_TOKEN_EXCHANGE`
önce bu read-only transaction ile actor çözer, ardından ayrı `IAM_AUTH` transaction'ı açar;
scope'lar aynı transaction içinde birikmez. `IAM_PROVISIONING`, actor, organization, önceden üretilmiş
target ve allow-listli `app.iam_operation_code` ister. Oluşturma ile finalize aynı scope'u
paylaşır fakat sırasıyla yalnız `TEACHER_ACCOUNT_CREATE` ve `TEACHER_ACCOUNT_FINALIZE` operation
code'larıyla birbirinden ayrılır; `IAM_PROVISIONING_FINALIZE` adlı beşinci bir scope yoktur.
`IAM_AUTH`, doğrulanmış `app.iam_actor_user_id`, allow-listli `app.iam_operation_code`,
`deviceIdentifier` ve güvenli token fingerprint bağlamını ister; `app.iam_target_membership_id`
ve `app.iam_target_organization_id` yalnız `CONTEXT_ACTIVATE`te server-set olabilir, diğer
operationlarda zorunlu olarak boştur. `app.iam_target_device_id` (IAM-002), `DEVICE_SELF_REVOKE`
(`IAM_AUTH`) ve `PLATFORM_DEVICE_REVOKE`de (`GLOBAL`) hedef `trusted_devices.id`yi taşır; sunucu
bunu yalnız yol parametresi + sahiplik doğrulamasından (aktör veya `app.iam_target_user_id`ye ait
olduğu SELECT ile) sonra `SET LOCAL` yapar, diğer operationlarda boştur. `app.iam_provider_device_
identifier` (IAM-002), yalnız `PROVIDER_TOKEN_EXCHANGE`te sunucunun doğrulanmış istek gövdesinden
(`deviceIdentifier` alanı) kurduğu server-set değerdir; `trusted_devices` `SELECT`/`INSERT`
policy'lerinin dar eşleşme anahtarıdır, diğer operationlarda zorunlu olarak boştur; istemci
bunu doğrudan bir RLS parametresi olarak kuramaz, yalnız gövde alanı olarak gönderir ve sunucu
doğrulayıp `SET LOCAL` yapar. `app.iam_verified_auth_time`
(IAM-002), yalnız `PROVIDER_TOKEN_EXCHANGE`in doğruladığı Cognito `auth_time`i taşır ve cihaz-bazlı
reauth bariyerinin (bkz. "Cognito V1 bağlayıcı profili") `WITH CHECK`i tarafından okunur;
istemciden kurulamaz. `app.iam_platform_admin_support_access` (IAM-002), yalnız platform
yöneticisinin aktif `platform_administrators` kaydı sunucu tarafında doğrulandıktan sonra
`DEVICE_SESSION_REVOKE` için server-set edilen boolean'dır; normal `ORG_ADMIN`/`TEACHER` yolunda
her zaman `false`/boştur ve istemci parametresiyle kurulamaz. `app.iam_canonical_provider_status` yalnız server-set
`UNSET`/`DISABLED`/`REVOKED` değerlerinden birini alabilir; `app.iam_security_revoke_required`
yalnız server-set boolean'dır. Bu iki değişken istemci gövdesi/header'ından kurulamaz; yalnız
doğrulanmış provider sonucu veya sunucu güvenlik akışı `SET LOCAL` yapabilir. Normal
operationlarda `app.iam_canonical_provider_status='UNSET'` ve
`app.iam_security_revoke_required=false` zorunludur. `GLOBAL` yerine kullanılır
çünkü normal kullanıcı da kurum öncesi token değişimi yapabilir. Bu scope, auth replay
escrow yüzeyi dışında tenant veya güvenlik iptal tablolarını açmaz; `CONTEXT_ACTIVATE`te yalnız
hedef üyelik/kurum zinciri, `SESSION_REFRESH`/`SESSION_LOGOUT`ta yalnız aynı aktör/cihaz ailesi,
`PLATFORM_ADMIN_ACTIVATE`te yalnız aktif admin kaydı açılır. `app.iam_current_family_id`,
`SESSION_REFRESH` ve `SESSION_LOGOUT` işlemlerinde sunucu tarafından `SET LOCAL` ile kurulan
server-set değerdir; istemci bu değeri doğrudan gönderemez veya değiştiremez. Refresh token
fingerprint'inden çözülen `refresh_token_families.id` değerini taşır ve RLS policy'leri tarafından
aynı transaction içindeki tüm `refresh_token_families` ve `refresh_tokens` erişimlerini bu tek
aileyle sınırlamak için kullanılır. Policy'ler bu değişkeni `::uuid` cast ile karşılaştırır;
geçersiz UUID veya boş değer policy koşulunu sağlamaz ve erişim reddedilir. Bu mekanizma, aynı
kullanıcı ve cihaz üzerinde birden fazla oturum ailesi bulunduğunda bir işlemin yalnız ilgili
aileyi görmesini ve değiştirmesini garanti eder. `iam_target_membership_id`,
`iam_target_organization_id`, üyelik satırındaki `user_id` ve `organization_id` çapraz
eşleşmezse fail-closed reddedilir. Sahte kurum bağlamı ve birleşik
transaction scope reddedilir. Kapsam değişiminde önceki bütün `SET
LOCAL` değerleri temizlenir; iki kapsamın ayarlarının birlikte bulunması, bilinmeyen scope veya
eksik değer tüm `USING`/`WITH CHECK` policy'leri tarafından reddedilir. Tenant tablolarında
politikalar ikiye ayrılır: `ORGANIZATION` scope'unda `current_setting('app.organization_id', true)`
zorunludur; `IAM_AUTH CONTEXT_ACTIVATE`te bunun yerine `app.iam_target_membership_id` +
`app.iam_target_organization_id` + actor/device zinciri zorunludur. Global kullanıcı tablolarında ayrı `FORCE RLS` politikası yalnız doğrulanmış
komutun `SET LOCAL app.iam_target_user_id` UUID'sine eşit kullanıcıyı açar; boş, sahte veya
çoklu hedef bağlam reddedilir. Ayrı rol, SECURITY DEFINER function'a göre seçilmiştir; bu V1'in IAM komutları
transaction/application service sınırında daha okunur ve geniş, yanlış parametreli function
yüzeyini önler.

Zorunlu otomasyon: `app_runtime` ile IAM erişim kapısındaki tüm doğrudan işlemler reddedilir;
`iam_runtime` owner/superuser/BYPASSRLS değildir; kurum A yöneticisinin iptal komutu kurum B
token satırını değiştiremez; IAM'in global güvenlik komutu yalnız gerekli kullanıcıya ait
oturumları etkiler. AUTHENTICATION kullanıcı listesi veya token ailesi okuyamaz; provisioning
başka kurum/hedef/operationCode'a erişemez. Bu SQL yetki testleri IAM-003/IAM-009'da yazılır;
A-013 rol sırlarını yalnız secret manager'a verir.

IAM-009 otomasyonu en az şunları kanıtlar: doğrulanmış issuer+subject yalnız doğru `user_id`yi
bulur; değiştirilmiş issuer/subject hiçbir kullanıcıyı açmaz; AUTHENTICATION kullanıcı listesi
ve token ailesi okuyamaz; context-selection tokenı ikinci aile üretiminde, süresi dolduğunda veya
eski `auth_time` taşıdığında reddedilir; kurum A yöneticisi yalnız kurum A'da hedef hoca
provisioning yapabilir; provisioning kurum B'ye veya ikinci targeta erişemez; başarısız Keycloak
çağrısı aktif ve giriş yapılabilir yarım hesap bırakmaz.
IAM-009 ayrıca `POST /users` `Location` subject'iyle finalize edilen eşlemenin doğru yazıldığını,
cevap kaybında yalnız username + değişmez `platform_user_id` eşleşmesinin sahiplenildiğini,
uyuşmazlıkta fail-closed kaldığını, bir-kezlik teslimin süresinden sonra okunamadığını ve aynı
kullanıcı/kurum provisioning'inin ikinci hesap oluşturmadığını doğrular. Ayrıca create operation
code'unun finalize policy'sini açamadığını, finalize operation code'unun yalnız aynı
command/targetı güncelleyebildiğini ve provisioning'i başlatan aktör teslimden önce üyelik/rol/
`TEACHER_ACCOUNT_MANAGE` yetkisini kaybederse secretı okuyamadığını kanıtlar. Teslim yanıtı
`CONSUMED` commit'inden sonra kaybolduğunda ikinci gösterimin reddedilmesi ve yeni reset
gerekmesi de aynı test grubundadır.

### IAM-002 — Cihaz ve oturum iptali `iam_runtime`/RLS eklentisi

Bu bölüm `IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md`nin dört uç yüzeyini yukarıdaki tabloya
bağlar: `DEVICE_LIST` (salt okunur, `IAM_AUTH`, ayrı policy gerekmez), `DEVICE_SELF_REVOKE`
(`IAM_AUTH`), `DEVICE_SESSION_REVOKE` (`ORGANIZATION`) ve `PLATFORM_DEVICE_REVOKE` (`GLOBAL`).

**Mantıksal cihaz kilidi (§12.0, yeni — satır bulunmasa bile serileştirir).** `trusted_devices`
satır kilidi (`SELECT ... FOR UPDATE`), satır **var olduğunda** çalışır; `PROVIDER_TOKEN_EXCHANGE`
henüz hiçbir satır yokken ilk kez bir `(user_id, device_identifier)` çifti için çalıştığında
kilitlenecek bir satır yoktur ve iki eşzamanlı ilk-kayıt denemesi arasındaki yarış yalnız partial
`UNIQUE (user_id, device_identifier) WHERE revoked_at IS NULL` kısıtına bırakılırsa, kısıt ihlali
sadece **aynı anda iki aktif satır** oluşmasını engeller — bir taraf `revoked_at`ı henüz güncel
olmayan bir okumayla `auth_time` kontrolünü geçip commit olabilir. Bu nedenle `PROVIDER_TOKEN_
EXCHANGE`, `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE`, `trusted_devices`e güvenlik-relevant
her erişimden **önce** aynı mantıksal anahtarda transaction-scoped bir advisory lock alır:
`SELECT pg_advisory_xact_lock(hashtext('trusted_device:' || user_id::text),
hashtext(device_identifier::text))`. Bu kilit satırın var olup olmadığından bağımsız çalışır,
`COMMIT`/`ROLLBACK`ta otomatik serbest kalır ve üç işlemi de aynı mantıksal cihaz için birbirine
göre **tam sıralı** hâle getirir. Hash çakışması riskini azaltmak için anahtar iki bağımsız
`hashtext` girdisinden (sabit önekli `user_id` ve çıplak `device_identifier`) üretilir; işlevsel
olarak eşdeğer başka bir güvenli kilit mekanizması (ör. tekil `pg_advisory_xact_lock(bigint)` +
`hashtextextended`) da kabul edilir, ancak mekanizma ne olursa olsun üç işlem de **aynı** anahtar
üretim fonksiyonunu kullanmak zorundadır. Bu kilit ayrı bir SECURITY DEFINER fonksiyonuna veya
`iam_runtime`in tablo geneline geniş erişimine ihtiyaç duymaz; `ADR-003 §5.3`teki ayrı `iam_runtime`
rolü kararı ve dar `FORCE RLS` policy modeli değişmeden korunur (bkz. "Reddedilen alternatif"
aşağıda). **Düzeltilmiş ifade:** "`trusted_devices`e her erişimden önce kilit" kuralı mutlak
değildir — yalnız aşağıdaki dar Faz A keşif sorgusuna izin verilir; bütün güvenlik-relevant
okumalar ve her `INSERT`/`UPDATE`, kilit **sonrasında** yapılır.

**İki fazlı akış — `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE` neden Faz A'ya ihtiyaç duyar.**
`PROVIDER_TOKEN_EXCHANGE`, mantıksal anahtarın her iki bileşenini de (`user_id` doğrulanmış
aktörden, `device_identifier` istek gövdesinden) kilitten **önce** zaten bilir; doğrudan Faz B'ye
geçer. `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE` ise yalnız `deviceId`
(`trusted_devices.id`) alır — mantıksal anahtarın `device_identifier` bileşeni **bilinmez**;
sunucu bunu öğrenmeden kilidi üretemez. Bu nedenle bu iki işlem dört fazlı çalışır:

- **Faz A — keşif (kararsız, salt okunur, kilitsiz):** `DEVICE_SELF_REVOKE`te
  `SELECT id, user_id, device_identifier FROM trusted_devices WHERE user_id =
  app.iam_actor_user_id AND id = :pathDeviceId` (mevcut geniş `DEVICE_SELF_REVOKE` `SELECT`
  policy'si zaten `user_id = app.iam_actor_user_id` ile sınırlıdır, ek policy gerekmez);
  `PLATFORM_DEVICE_REVOKE`te `SELECT id, user_id, device_identifier FROM trusted_devices WHERE
  user_id = app.iam_target_user_id AND id = :pathDeviceId`. Bu sorgu **yalnız** değişmez
  `user_id` ve `device_identifier` alanlarını okur; `revoked_at` durumuna bakmaz, hiçbir
  mutasyon/terminal-durum/başarı kararı vermez ve `SELECT ... FOR UPDATE` **değildir**. Eşleşme
  yoksa (0 satır) istek burada `404 RESOURCE_NOT_FOUND` ile biter.
- **Faz B — mantıksal kilit:** Faz A'da keşfedilen `(user_id, device_identifier)` ile yukarıdaki
  `pg_advisory_xact_lock` alınır.
- **Faz C — kilit-sonrası yeniden okuma ve doğrulama:** aynı `deviceId` satırı `SELECT ... FOR
  UPDATE` ile yeniden okunur; okunan satırın `user_id`si **hâlâ** beklenen kullanıcıyla (aktör
  veya hedef kullanıcı) ve `device_identifier`i Faz A'da keşfedilen değerle eşleşmiyorsa istek
  fail-closed `404 RESOURCE_NOT_FOUND` ile durur (satır silinmiş/taşınmış gibi imkânsız ama
  savunma amaçlı bir durumu yakalar; `device_identifier` zaten immutable olduğundan bu normal
  akışta hiç tetiklenmez — bkz. `VERI_MODELI.md` §4.10).
- **Faz D — karar ve mutasyon:** `revoked_at` kontrolü, no-op/terminal karar, `UPDATE` ve bağlı
  `refresh_token_families`/`refresh_tokens` iptalleri **yalnız** bu noktadan sonra, Faz C'nin
  yeniden okuduğu satıra dayanarak yapılır. `app.iam_target_device_id`, yol parametresinden
  (bilinen, keşfedilmemiş bir değer olduğu için) Faz A'dan önce `SET LOCAL` yapılabilir; ama
  bu değişkeni kullanan `UPDATE`in kendisi yalnız Faz D'de çalışır.

**Kilit sırası ve eşzamanlılık.** Mantıksal cihaz kilidi (Faz B) yalnız `PROVIDER_TOKEN_EXCHANGE`,
`DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE` için zorunludur — bu üçü `trusted_devices`in
kimlik/güvenilirlik durumunu (`INSERT`/`revoked_at` `UPDATE`) değiştirir. `CONTEXT_ACTIVATE` ve
`PLATFORM_ADMIN_ACTIVATE` (IAM-001), `trusted_devices`e hiç yazmaz ve satırını kilitlemez (yalnız
`context_selection_tokens`in `EXISTS (SELECT ...)` `USING` koşuluyla salt okunur biçimde
referans alır); bu iki işlem mantıksal cihaz kilidini **almaz** ve kilit sırasına adım (1)'den
(`organization_memberships`/`refresh_token_families`/`refresh_tokens`, kendi tablo matrisine göre)
başlar. Kilit sırası, kilidi alan işlem için **bağlayıcıdır ve sabittir**: (0) mantıksal cihaz
kilidi (yalnız üç işlem, yukarıda), (1) `trusted_devices` satırı (varsa; Faz A/C ayrımı yukarıda
tanımlıdır), (2) `organization_memberships` (varsa), (3) `refresh_token_families`, (4)
`refresh_tokens`. Ters sırayla kilitleme deadlock riski taşıdığından uygulama kodunda yasaktır.
Adım (1) uygulanabiliyorsa kilit (`SELECT ... FOR UPDATE`, Faz C) alındıktan **sonra** aktif
aile/token/`session_generation`/`revoked_at`/**`MAX(revoked_at)`** durumu **yeniden okunur**;
karar ilk (kilitsiz veya Faz A) okumaya değil bu ikinci okumaya göre verilir.
`PROVIDER_TOKEN_EXCHANGE`in `WITH CHECK`i, doğrulanmış `auth_time`i yalnız bu kilit-sonrası
`MAX(revoked_at)` ile karşılaştırır; eski/eşit `auth_time` kesinlikle reddedilir. Bir iptal
transaction'ı commit olduktan sonra, ondan önce başlamış ama henüz mantıksal kilidi almamış
yarışan bir `PROVIDER_TOKEN_EXCHANGE` (veya `CONTEXT_ACTIVATE`/`PLATFORM_ADMIN_ACTIVATE`
transaction'ı, kendi tablo sırasında), kilit sırasına girip yeniden okuma yaptığında hedefin
artık iptal edilmiş/eski `session_generation`lı/eski `auth_time`yle geçersiz olduğunu görür ve
`WITH CHECK` bu eski durumla yeni satır üretimini reddeder; yarışan işlem eski
`session_generation`/eski `auth_time` ile **yeni bir aile veya yeni bir aktif cihaz kaydı
üretemez**.

**Reddedilen alternatif (bağlayıcı kısıt, IAM-002):** Cihaz-bazlı reauth bariyerinin eski
`revoked_at` geçmişini görebilmesi, `iam_runtime`e genel `SELECT` yetkisi açarak, `BYPASSRLS`
vererek veya RLS'i atlayan bir `SECURITY DEFINER` fonksiyonuyla **çözülmez**. Tek kabul edilen
çözüm, yukarıdaki dar `PROVIDER_TOKEN_EXCHANGE` `SELECT` policy'sidir (yalnız aynı actor + aynı
`device_identifier`); bu policy dışında hiçbir genel/geniş okuma yüzeyi açılmaz.

**`trusted_devices` değişmez kolonlarının korunması — column-level privilege (bağlayıcı,
IAM-002).** PostgreSQL RLS policy ifadeleri (`USING`/`WITH CHECK`), trigger fonksiyonlarındaki
`NEW`/`OLD` kayıt değişkenlerine sahip **değildir**; bir `UPDATE` policy'sinde "bu kolon eski
değerinden değişemez" ifadesi `NEW.x = OLD.x` biçiminde yazılamaz (geçersiz sözdizimi). Bu nedenle
`device_identifier`, `user_id`, `platform`, `device_name` ve `trusted_at` kolonlarının hiçbir
`UPDATE`de değişmemesi RLS ile değil, migration'ın kurduğu **column-level `GRANT`** ile sağlanır:

```sql
REVOKE UPDATE ON trusted_devices FROM iam_runtime;
GRANT UPDATE (revoked_at) ON trusted_devices TO iam_runtime;
-- last_seen_at güncellemesi V1'de tanımlı değildir (IAM-002 kapsamı dışı); tanımlanırsa
-- ayrı ve dar bir GRANT ile eklenir:
-- GRANT UPDATE (last_seen_at) ON trusted_devices TO iam_runtime;
```

Bu `GRANT`, `iam_runtime`in çalıştırabileceği **her** `UPDATE trusted_devices SET ...` ifadesinde
`SET` listesine `revoked_at` dışında bir kolon eklenmesini SQL sözdizimi/privilege seviyesinde
imkânsız kılar — bu, RLS'in kapsamadığı bir savunma katmanıdır ve RLS `USING`/`WITH CHECK`
koşullarıyla **birlikte** çalışır (RLS hangi satırın hedef alınabileceğini, `GRANT` hangi
kolonun yazılabileceğini sınırlar). `device_identifier` immutability'si bu ikisinin
**birleşimidir**: RLS satırı bulur ve doğrular, `GRANT` yalnız `revoked_at` yazımına izin verir
(bkz. `VERI_MODELI.md` §4.10).

**`revoked_at` değerinin bütünlüğü — `transaction_timestamp()` (bağlayıcı, IAM-002).**
`column-level GRANT`, yalnız **hangi kolonun** yazılabileceğini sınırlar; yazılan **değerin**
doğruluğunu sınırlamaz. Bu nedenle yazılan `revoked_at` değerinin keyfî bir geçmiş veya gelecek
zaman damgası olmadığını (uygulama katmanındaki bir hata, yanlış saat senkronizasyonu veya
kasıtlı manipülasyon ihtimaline karşı) garanti etmek `WITH CHECK (revoked_at =
transaction_timestamp())`in işidir — bu, sunucunun **kendi** transaction başlangıç zamanına tam
eşitliği zorunlu kılar; `iam_runtime`, `revoked_at` için `transaction_timestamp()` dışında bir
ifade (sabit değer, istemciden gelen zaman damgası, geçmiş/gelecek keyfî değer) yazamaz. Terminal
(zaten `revoked_at IS NOT NULL`) bir satırın `NULL`a veya başka herhangi bir zaman damgasına
çevrilmesi ise `WITH CHECK`ten değil, yukarıdaki `USING (... AND revoked_at IS NULL)` koşulundan
gelir: bu koşulu sağlamayan (zaten iptal edilmiş) bir satır, hangi yeni değer yazılmak istenirse
istensin, `UPDATE`in **hedef kümesine hiç girmez** (0 satır etkilenir) — column-level privilege
bu davranışla ilgisizdir, yalnız kolon erişimini sınırlar.

**Replay ve audit tekilliği.** Aynı `Idempotency-Key` + aynı `request_fingerprint` ile gelen
yeniden deneme hiçbir tabloya (dahil `audit_logs`) ikinci `INSERT`/`UPDATE` üretmez; ilk
tamamlanmış sonucun eşdeğeri döner ve bu kontrol uygulama katmanında transaction açılmadan
yapılır. Farklı bir `Idempotency-Key` ile, hedefi **zaten** terminal (iptal edilmiş) durumda
bulan bir istek ise ayrı ve **yeni** bir mantıksal istektir: hedef tabloya durum mutasyonu
yapmaz (zaten iptal), ama `audit_logs`a yine tam bir `INSERT` yazar (bu, aynı hedefe farklı
zamanlarda kimin dokunduğunu kaybetmemek için kasıtlıdır). `DEVICE_SELF_REVOKE` için idempotency
replay'i, standart `Authorization` doğrulamasından **sonra** çalışır ve onu asla atlamaz; sunulan
access tokenı zaten iptal edilmişse istek `401 SESSION_REVOKED` ile reddedilir — replay hiçbir
koşulda iptal edilmiş bir tokenı yeniden yetkili göstermez.

IAM-009, IAM-001'in yukarıdaki test listesine ek olarak en az şunları kanıtlar:

- **FORCE RLS altında exact actor/device geçmişi görülür:** aktörün kendi `user_id`si ve
  `provider-token-exchange` gövdesinde sunduğu tek `device_identifier`in **hem aktif hem iptal
  edilmiş** geçmiş satırları, dar `PROVIDER_TOKEN_EXCHANGE` `SELECT` policy'siyle görülebilir
  (`MAX(revoked_at)` doğru hesaplanır).
- **Başka actor/device geçmişi görülmez:** aynı sorgu, başka bir `user_id`ye ait satırları veya
  aynı kullanıcının **farklı** bir `device_identifier`ine ait satırları döndürmez; RLS bu
  satırları görünmez kılar (uygulama filtrelemesi değil).
- Aynı `(user_id, device_identifier)` çifti için, önceki satırın kilit-sonrası yeniden okunan
  `MAX(revoked_at)`ından eski veya eşit doğrulanmış `auth_time` ile yapılan `PROVIDER_TOKEN_
  EXCHANGE` yeni `trusted_devices` satırı üretemez (`app.iam_verified_auth_time` `WITH CHECK`i)
  ve `403 REAUTHENTICATION_REQUIRED` döner; yalnız daha yeni `auth_time` ile yeni satır açılabilir.
- **Revoke-vs-exchange yarışı (mantıksal cihaz kilidi):** aynı `(user_id, device_identifier)`
  çiftinde eşzamanlı `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE` ile eski `auth_time` taşıyan
  bir `PROVIDER_TOKEN_EXCHANGE` yarıştığında, mantıksal kilidi önce alan işlem tamamlanır; sonra
  kilidi alan `PROVIDER_TOKEN_EXCHANGE` güncel (yeniden okunmuş) `MAX(revoked_at)` ile karşılaştırır
  ve eski `auth_time` ile **hiçbir sırada** aktif cihaz bırakamaz — kazanan iptal işlemi önce commit
  olursa exchange reddedilir; exchange önce kilidi alsa bile revoke aynı anahtar için beklemek
  zorunda kalır ve exchange commit olduktan sonra kendi `revoked_at`ını üretmeye devam eder (iki
  işlem de birbirini geçersiz kılan çelişkili bir "aktif ama iptal edilmiş" durum bırakmaz).
- **Henüz hiç satır yokken çift eşzamanlı ilk kayıt:** aynı `(user_id, device_identifier)` çifti
  için hiç `trusted_devices` satırı yokken gelen iki eşzamanlı `PROVIDER_TOKEN_EXCHANGE`,
  mantıksal cihaz kilidiyle serileştirilir; yalnız biri satırı üretir, diğeri kilidi
  aldığında satırın artık var olduğunu görüp mevcut aktif cihazı bulur/yeniden kullanır — hiçbir
  koşulda aynı çift için **birden fazla aktif** satır oluşmaz (partial `UNIQUE` kısıtı bu
  senaryoda ikinci bir savunma katmanıdır, birincil savunma mantıksal kilittir).
- **Faz A/Faz B arası yarış:** `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE`in Faz A keşif
  sorgusu ile Faz B mantıksal kilidi arasına eşzamanlı bir `PLATFORM_DEVICE_REVOKE`/
  `DEVICE_SELF_REVOKE` girip aynı cihazı iptal edip commit ederse, ilk işlemin Faz C yeniden
  okuması bu güncel `revoked_at`ı görür ve karar/mutasyon buna göre (no-op veya fail-closed)
  verilir; Faz A'nın kilitsiz okuduğu eski durum hiçbir karara girmez.
- **Faz C yeniden okumanın güncelliği:** Faz C'nin `SELECT ... FOR UPDATE`i, Faz A'dan sonra
  başka bir transaction tarafından commit edilmiş bir `revoked_at` değişikliğini **her zaman**
  görür (kilit + `FOR UPDATE` snapshot garantisi); karar hiçbir koşulda Faz A'nın önbelleğe
  alınmış değerine dayanmaz.
- **Çapraz `userId`/`deviceId` keşfi 404 döner:** `DEVICE_SELF_REVOKE`te başka kullanıcıya ait
  `deviceId`, `PLATFORM_DEVICE_REVOKE`te path `userId`sine ait olmayan `deviceId` Faz A'da 0 satır
  döner ve istek `404 RESOURCE_NOT_FOUND` ile biter; mantıksal kilit hiç alınmaz, Faz C/D'ye
  geçilmez.
- Aktivasyon-vs-iptal yarışı: `CONTEXT_ACTIVATE` ile eşzamanlı gelen `DEVICE_SESSION_REVOKE`de
  önce commit olan kazanır; kaybeden taraf eski `session_generation`/eski `revoked_at` durumuyla
  ikinci bir aile veya iptal edilmemiş görünüm üretemez.
- Refresh-vs-iptal yarışı: `SESSION_REFRESH` ile eşzamanlı gelen `DEVICE_SELF_REVOKE`de,
  iptal önce commit olursa yenilenen token da aynı transaction zincirinde iptal edilir; iki
  işlem de başarılı görünüp çelişkili bir "aktif ama iptal edilmiş" durum bırakmaz.
- İki farklı `Idempotency-Key` ile eşzamanlı aynı hedefe gelen iptal isteğinde yalnız biri satır
  kilidini alıp durum değiştirir ve audit yazar; diğeri kilit serbest kalınca yeniden okur, hedefi
  zaten terminal bulur ve yalnız kendi audit satırını yazarak no-op başarı döner.
- Platform yöneticisinin destek amaçlı `DEVICE_SESSION_REVOKE` çağrısı yalnız
  `app.iam_platform_admin_support_access=true` server-set bayrağıyla normal rol/izin `USING`
  koşulunu genişletir; istemci bu bayrağı hiçbir parametreyle kuramaz ve aynı transaction
  `PLATFORM_ADMIN_ORG_ACCESS` audit satırını da üretir.
- `PLATFORM_DEVICE_REVOKE`in dar `GLOBAL` policy'si yalnız hedef `trusted_device_id`ye bağlı
  aileleri açar; hedef kullanıcının **başka** cihazlarının aileleri bu işlemden etkilenmez.
- **`trusted_devices` `CREATE POLICY`/`GRANT` ifadeleri gerçek PostgreSQL'de başarıyla
  oluşturulur:** yukarıdaki `SELECT`/`INSERT`/`UPDATE` policy'leri ve `REVOKE UPDATE ON
  trusted_devices FROM iam_runtime; GRANT UPDATE (revoked_at) ON trusted_devices TO
  iam_runtime;` ifadeleri migration'da hatasız çalışır (özellikle `INSERT` `WITH CHECK`indeki
  `old` korelasyon takma adının geçerli bir self-join subquery olduğu, trigger `NEW`/`OLD`
  sözdizimi kullanılmadığı doğrulanır).
- **`revoked_at` `NULL → transaction_timestamp()` güncellemesi izinlidir:** `iam_runtime`, aktif
  (`revoked_at IS NULL`) ve doğru scope+operation+sahiplikteki bir satırda `UPDATE
  trusted_devices SET revoked_at = transaction_timestamp() WHERE id = ...` çalıştırabilir.
- **`revoked_at` `NULL → keyfî geçmiş/gelecek timestamp` güncellemesi reddedilir:** `UPDATE
  trusted_devices SET revoked_at = '2020-01-01' WHERE ...` veya `SET revoked_at = now() +
  interval '1 day'` gibi `transaction_timestamp()`e eşit olmayan bir değer, `WITH CHECK
  (revoked_at = transaction_timestamp())` tarafından reddedilir — yalnız sunucunun kendi
  transaction başlangıç zamanı kabul edilir.
- **Terminal `revoked_at`ın `NULL`a veya başka bir timestamp'e çevrilmesi reddedilir:** bu,
  `WITH CHECK`ten değil `USING (... AND revoked_at IS NULL)` koşulundan gelir — zaten
  `revoked_at IS NOT NULL` olan bir satır `USING`i hiç geçemediği için `UPDATE`in hedef
  kümesine girmez (0 satır etkilenir), yazılmak istenen yeni değer ne olursa olsun.
- **`device_identifier`, `user_id`, `platform`, `device_name`, `trusted_at` güncellemeleri
  privilege seviyesinde reddedilir:** `iam_runtime` bu kolonlardan herhangi birini `SET` listesine
  koyan bir `UPDATE trusted_devices` ifadesi çalıştırdığında Postgres `ERROR: permission denied
  for column ...` döner (RLS'e hiç ulaşmadan); bu, `GRANT UPDATE (revoked_at)`in dışında hiçbir
  `GRANT UPDATE`in tanımlı olmamasının doğal sonucudur.
- **Başka actor/target cihaz güncellemesi RLS ile reddedilir:** aktörün kendi `user_id`sine
  ait olmayan veya hedef `deviceId`den farklı bir satıra yönelik `UPDATE trusted_devices SET
  revoked_at = ...`, doğru `GRANT`e sahip olsa bile `USING` koşulunu sağlamadığından 0 satır
  etkiler.
- **Başka bir `IAM_AUTH` operation code'u, hedef değişkenlerini kursa bile cihazı
  okuyamaz/güncelleyemez:** örn. `app.iam_operation_code = 'SESSION_REFRESH'` veya
  `'CONTEXT_ACTIVATE'` iken `app.iam_actor_user_id`/`app.iam_target_device_id` tesadüfen
  `DEVICE_SELF_REVOKE`teki gibi dolu olsa bile, `trusted_devices_select_self` ve
  `trusted_devices_update_self_revoke` policy'lerinin `app.iam_operation_code =
  'DEVICE_SELF_REVOKE'` guard'ı sağlanmadığından `SELECT`/`UPDATE` 0 satır döner.
- **Başka bir `GLOBAL` operation code'u, hedef değişkenlerini kursa bile cihazı
  okuyamaz/güncelleyemez:** `app.iam_operation_scope = 'GLOBAL'` iken `app.iam_operation_code
  <> 'PLATFORM_DEVICE_REVOKE'` olduğunda (örn. `IAM-004`'ün geniş hesap-güvenliği global
  komutu), `app.iam_target_user_id`/`app.iam_target_device_id` dolu olsa bile
  `trusted_devices_select_platform_revoke`/`trusted_devices_update_platform_revoke`
  policy'lerinin operation-code guard'ı sağlanmadığından `trusted_devices`e erişilmez; `IAM-004`
  global komutu zaten yalnız `refresh_token_families`/`refresh_tokens`e erişir, `trusted_devices`e
  hiç dokunmaz (bkz. üstteki geniş `GLOBAL` policy satırı).
- **`PROVIDER_TOKEN_EXCHANGE` dışındaki bir işlem `INSERT` yapamaz:** `app.iam_operation_code
  <> 'PROVIDER_TOKEN_EXCHANGE'` iken denenen herhangi bir `INSERT INTO trusted_devices`,
  `trusted_devices_insert_provider_exchange` policy'sinin `WITH CHECK`indeki operation-code
  guard'ını sağlamadığından reddedilir; ayrıca `iam_runtime`e bu tablo için başka hiçbir
  `INSERT` policy'si tanımlı değildir.

### Dar hoca hesabı provisioning akışı

`IAM_PROVISIONING` yalnız `TEACHER_ACCOUNT_CREATE` ve buna bağlı
`TEACHER_ACCOUNT_FINALIZE` allow-listli komutlarında kullanılabilir. Sunucu
önceden benzersiz `targetUserId` üretir; yetkili `actorUserId`, tek `organizationId`, target ve
operation code'u transaction bağlamına koyar. İlk transaction yalnız `users`, kurumun `people`,
`organization_memberships`, başlangıç `organization_membership_roles`, kurum kapsamlı
audit/idempotency ve `target_user_id`li provider-command outbox satırlarını yazar; henüz
`user_identities` yazılmaz. RLS policy tüm bu değerleri ve actorun kurum yetkisini
`USING`/`WITH CHECK` ile zorunlu kılar; başka kurum, hedef, işlem türü ve gereksiz
`UPDATE`/`DELETE` reddedilir.

Yeni `users` ve üyelik satırları `PROVISIONING` durumunda başlar ve normal login/kurum seçimi
tarafından görünmez. Worker kullanıcı adını, `UPDATE_PASSWORD` action'ını ve create alanlarını
şifreli command payload'dan alır; geçici parolayı worker üretir. Keycloak `POST /users` başarıyla
dönerse `Location` başlığındaki gerçek user id/subject alınır. İkinci, aynı command/target'a
daraltılmış `IAM_PROVISIONING` scope'u ve `TEACHER_ACCOUNT_FINALIZE` operation code'u ile çalışan
transaction, `user_identities` eşlemesini, kullanıcı ve üyeliğin `ACTIVE` durumunu ve süresi
geçmemiş bir-kezlik şifreli teslim sonucunun `READY` durumunu birlikte yazar. Escrow bu sırada
süresi dolmuşsa hesap güvenle finalize edilebilir ancak teslim `EXPIRED` kalır; geçici parola
gösterilmez ve yetkili yönetici yeni parola sıfırlama akışı başlatır.

Create cevabı kaybolursa worker aynı kullanıcı adıyla Keycloak'ta kesin arama yapar; bulunan
kullanıcı ancak değiştirilemez `platform_user_id` attribute'u beklenen yerel `targetUserId` ile
eşleşirse sahiplenilir ve finalize edilir. Eşleşme yoksa veya arama belirsizse fail-closed hata,
alarm ve audit üretilir; mevcut hesap bağlanmaz. Aynı kullanıcı/kurum isteğinin tekrarında aynı
idempotency/username-hash command'i döner; `PENDING`/`CLAIMED` komut lease sonrası yeniden
sahiplenilir. Terminal başarısızlıkta kullanıcı ve üyelik `SUSPENDED` kalır; terk edilmiş
`PROVISIONING` kayıtları scheduler tarafından yeniden denenir veya güvenli terminal hataya
çekilir, yeni aktif hesap oluşturmaz. Kesin API sözleşmesi STAFF-001, migration IAM-003,
uygulama STAFF-002 ve IAM-004, izolasyon/yarım-hesap testleri IAM-009 sahibidir.

## Keycloak fallback — IAM güvenlik olayı uzlaştırması

Normal kullanıcı devre dışı bırakma, parola sıfırlama, şüpheli hesap ve global logout işlemleri
IAM-first'tür: IAM, platform token ailelerini ve erişimi yerel transaction'da önce keser, audit
ve idempotent `PENDING` sağlayıcı komutu kaydeder; ardından Keycloak management çağrısını yapar.
Çağrı başarısızsa kalıcı iş üstel geri çekilmeyle tekrar eder ve aynı idempotency anahtarıyla
yinelenir. Keycloak Admin Console üzerinden doğrudan kullanıcı güvenlik değişikliği normal
operasyon olarak yasaktır; yönetim yetkisi yalnız IAM servis hesabındadır.

Global parola değişimi/reset, kullanıcı devre dışı bırakma, global logout, cihazın global
kaldırılması ve hesap ele geçirme olaylarının her birinde aynı transaction `users.
reauthentication_required_after = transaction_time` yazar, kullanıcının tüm aile/access
tokenlarını iptal eder, güvenlik audit'i ve benzersiz `iam_provider_commands` satırını yazar.

Acil/out-of-band değişiklikler için bağlayıcı mekanizma, iki ayrı Keycloak REST kaynağını
**polling** ile tüketmektir: Admin Events `GET /admin/realms/{realm}/admin-events`, User Events
`GET /admin/realms/{realm}/events`. Admin Events'in resmî operation type enumu `CREATE`,
`UPDATE`, `DELETE`, `ACTION`'dır. Güvenlik bakımından ilgili Admin Event, `resourceType=USER`
ve kanonik `resourcePath=users/{user-id}`, `users/{user-id}/logout`,
`users/{user-id}/reset-password`, `users/{user-id}/credentials/{credential-id}` veya
`users/{user-id}/credentials/{credential-id}/moveAfter/{new-previous-credential-id}`,
`users/{user-id}/credentials/{credential-id}/moveToFirst`,
`users/{user-id}/credentials/{credential-id}/userLabel` veya
`users/{user-id}/disable-credential-types` olan `UPDATE`/`ACTION`/`DELETE` kaydıdır. Bu
credential-mutasyon yolu listesi Keycloak fallback'i seçilirse sabitlenecek sürümün Management API'siyle
doğrulanır. Parser, sağlayıcının gönderdiği baştaki tek isteğe bağlı `/` karakterini kaldırarak
bu kanonik biçimlerle eşleştirir. `users/{id}/...` biçiminde olup bu listede olmayan güvenlik
açısından belirsiz `ACTION`/`UPDATE`/`DELETE` sessizce atılmaz: hedef kullanıcının aileleri
fail-closed iptal edilir ve alarm/audit üretilir. IAM hedef kullanıcıyı path'ten alır ve Management API ile
enabled/credential durumunu yeniden okur. User Events'te
resource path yoktur: hedef, eventin `userId` alanıdır. Geçerli eşlemeler `LOGOUT`, modern
`UPDATE_CREDENTIAL`, `RESET_PASSWORD` ve `LOGIN_ERROR`'dır; `UPDATE_PASSWORD` yalnız seçili
eski sağlayıcı sürümü bunu yayımlıyorsa geriye uyumluluk eşlemesidir.

Her kaynak bağımsız yerel checkpoint, dedup ve retention politikası taşır: IAM her **1 dakikada**
Keycloak'un `dateFrom`/`dateTo` + `first`/`max` offset sayfalamasıyla çeker; sorgu boyunca
`dateTo` sabit, sonraki turda `dateFrom` 2 dakikalık overlap ile geridedir. `(eventTime,eventId)`
yalnız yerel checkpoint/dedup kaydıdır; Keycloak'un `eventId` için start-after cursoru veya
ikincil sıralama garantisi olduğu varsayılmaz. Tüm sayfalar başarıyla işlenmeden yerel checkpoint
ilerlemez; `(source,event_id)` tekrar elemesi uygulanır. Retention en az 24 saattir. Retention
boşluğu, yarım sayfalama, checkpoint bütünlük
hatası veya kaynak erişilemezliği, yalnız kullanıcı durum taramasıyla kapatılamaz: kaynak
yeniden sağlanana kadar son güvenilir cursor sonrasındaki hedeflenebilir kullanıcıların aileleri
fail-closed iptal edilir; hedef belirlenemiyorsa realmde son güvenilir cursor sonrasında aktif
tüm platform aileleri iptal edilir. Hedef SLO 5 dakikadır; Keycloak erişilemezken SLO sağlanamaz,
2 dakikada alarm ve 5 dakikada escalation açılır.

Provider polling ve event dedup işlemleri `idempotency_keys` kullanmaz: bunlar istemci
`clientMutationId`si ve aktörü olmayan dış olay tüketimleridir. Tekrar güvenliği sırasıyla
`iam_event_deduplications (source,event_id)` ve cursor transaction'ı ile sağlanır. Polling'in
ürettiği Keycloak yönetim çağrısı gerekiyorsa, ayrı `iam_provider_commands.idempotency_key`
kullanılır; sistem aktörlü bir `idempotency_keys` satırı oluşturulmaz. Kullanıcı/ yönetici
başlatmalı IAM-first komutları ise P-010 `actorUserId`siyle `idempotency_keys` kullanır.

| Kaynak / olay | Eşleme | Eylem |
|---|---|---|
| Admin Events `UPDATE`/`ACTION` | `USER`, `users/{id}`, `users/{id}/logout`, `users/{id}/reset-password`, `users/{id}/credentials/{credential-id}` ve `moveAfter`/`moveToFirst`/`userLabel` alt yolları veya `users/{id}/disable-credential-types`; parser isteğe bağlı baştaki `/`yi kaldırır | Management API yeniden okumasıyla deaktif/credential güvenlik değişiminde global fail-closed iptal; tanınmayan `users/{id}/...` güvenlik olayı fail-closed iptal + alarm; profil değişiminde audit-only |
| Admin Events `DELETE` | `USER`, `users/{id}`; parser isteğe bağlı baştaki `/`yi kaldırır | Global fail-closed iptal |
| User Events `LOGOUT` | `userId` (resource path yok) | Global logout ise global iptal; aksi audit-only |
| User Events `UPDATE_CREDENTIAL`, `RESET_PASSWORD` | `userId` (resource path yok) | Global eşik + aile iptali |
| User Events `UPDATE_PASSWORD` | Yalnız eski sürüm uyumluluk olayı | `UPDATE_CREDENTIAL` ile aynı eylem |
| User Events `LOGIN_ERROR` | `userId` varsa (resource path yok) | Audit/risk sayacı; tek başına otomatik global iptal yok |

Sorumluluk ayrımı: Keycloak fallback'i seçilirse güncellenecek A-010 ortam/hosting, scheduler ve alarm altyapısını; A-013
`iam_runtime`, Keycloak management ve şifreli teslim anahtar/sırlarını uygular. A-010 açılış
kapısı ayrıca seçilen realmde User Events ile Admin Events'in etkin olduğunu, gerekli olay
türlerinin kaydedildiğini, retention'ın 1 dakikalık polling + 2 dakikalık overlap + arıza/
uzlaştırma penceresinden uzun olduğunu kanıtlar. Admin Event representation ayrıntıları veri
minimizasyonuna uygun tutulur; gereksiz representation/payload loglanmaz. Management servis
hesabı `realm-admin` değildir; yalnız kullanıcı create/okuma/arama, credential/reset, enable/
disable, logout ve olay okuma için gereken en dar realm-management izinlerine sahiptir.
IAM-001 giriş/oturum API sözleşmesini; IAM-002 cihaz/iptal sözleşmesini; IAM-003 IAM tabloları,
rolleri ve migration'ı; IAM-004 giriş/token değişimi ve provider command akışını; IAM-005
refresh ailesi/yenileme/çıkış/tekrar kullanım tespitini; IAM-006 cihaz kaydı,
`DEVICE_SESSION_REVOKE` ve yeniden doğrulamayı; IAM-009 entegrasyon, izolasyon, olay kaybı,
iptal gecikmesi ile seçilecek Keycloak sürümünden alınmış kanonik/`/`-önekli logout
ve credential-mutasyon Admin Event path örneklerinin parser testlerini uygular.

## Cognito uygulama sorumlulukları ve güvenlik sınırı

| Katman | Sorumluluk |
|---|---|
| Cognito Essentials | Kimlik bilgisi doğrulama, parola politikası/özeti, reset akışı, global kullanıcı durumu/oturumu, OIDC discovery/JWKS ve yönetim API'leri. |
| IAM (modüler monolit) | `(issuer, subject)`–`users.id` eşlemesi, Cognito token doğrulaması, kurum seçimi, opaque platform token üretimi/HMAC özet saklama/rotasyonu, `session_generation`, üyelik-rol-izin-sınıf doğrulaması, kurum-kapsamlı iptal, provider-command uzlaştırması ve audit. |
| Mobil istemci | Sistem tarayıcısıyla `state`/`nonce`/PKCE akışı, callback eşleştirmesi, ilk değişimden sonra Cognito tokenlarını silme, yalnız platform tokenlarını OS güvenli saklama alanında tutma, iptal sonrası zorunlu yeniden giriş, tokenı loglamama ve kullanıcıya açık oturum/eşitleme durumu. |

Backend, Cognito **access tokenını** kabul etmeden önce en az imza/JWKS, `iss`, `client_id`,
`exp`, `nbf` (varsa) ve gerekli scope doğrulamasını yapar. `state`, `nonce` ve PKCE callback
doğrulaması mobile aittir. ID token API erişimi veya IAM token değişimi için kabul edilmez.
Cognito grupları ve custom claim'leri, uygulama yetkisinin yerine geçmez. Sağlayıcı erişilemezse yeni giriş
ve parola sıfırlama başarısız olur; platform refresh'i Cognito'ya çağrı yapmadan kendi canlı
üyelik/iptal denetimini yapar. Mevcut kısa ömürlü access token yalnız uygulamanın canlı
üyelik/iptal denetiminden geçiyorsa kullanılabilir. İstemci bu durumu başarı
saymaz ve bekleyen yazmayı silmez.

Giriş, başarısız giriş, parola sıfırlama başlangıcı/tamamlanması, oturum yenileme, cihaz kaydı,
kurum token değişimi ve iptal denetim olayı üretir. Audit kaydında token, parola, doğrulama
kodu, IP adresinin gereksiz tam değeri veya kişisel hata içeriği bulunmaz; `KISISEL_VERI_ENVANTERI.md`
satır 10, 11 ve 14'teki veri minimizasyonu uygulanır.

## Kabul ve doğrulama

| Ölçüt | Sonuç |
|---|---|
| Cognito mobil giriş akışı; secretsiz public client, Code + PKCE S256 ile gerçek Android deneyinde doğrulandı. | Karşılandı |
| Parola ve ham token saklama sınırı; token rotasyonu, ömür ve iptal davranışı tanımlıdır. | Karşılandı |
| Kurum üyeliği, rol, izin ve sınıf yetkisi sağlayıcı claim'ine taşınmadan IAM'de kalır. | Karşılandı |
| Kurum-kapsamlı iptal başka kurum oturumunu kapatmaz; `session_generation` kontrolü korunur. | Karşılandı |
| Kaba kuvvet koruması, yönetim API sınırı, audit ve hassas veri minimizasyonu ele alınır. | Karşılandı |
| Veritabanı/hosting, MFA/biometri, oran eşikleri ve uygulama iskeleti için kapsam dışı karar alınmamıştır. | Karşılandı |
| Güvenilir cihaz oturumu, V1 çıkış/iptal/global olay/saklama kaybı sınırlarıyla tanımlıdır; periyodik giriş zorunlu değildir. | Karşılandı |
| Deney maliyeti ve budget alarmı raporlandı; Cognito/budget/bootstrap IAM yokluğu kanıtlandı. | Karşılandı |
| Experiment role ve beş inline policy'nin yokluğu kanıtlandı. | Karşılandı |

Nihai sağlayıcı kararı kapanmıştır. IAM-001 bu ADR'deki Cognito sınırı ile
`A004R3_COGNITO_MALIYET_OPERASYON_VE_TEARDOWN_KANITI.md` girdilerini sözleşmeye dönüştürebilir.
Production SDK, ortam değişkeni, gerçek kullanıcı veya cloud kaynağı; IAM-001 ve A-013'ün en az
yetkili kimlik/secret sözleşmesi ile yeni bütçe/alarm kapısı tamamlanmadan eklenmez.

## Kaynaklar ve uyum

- Ana plan: `URUN_VE_UYGULAMA_PLANI.md` §8.1, §11.5, §15, §18.2/8.
- Yetki ve kurum sınırı: `YETKI_MATRISI.md` §2.2, §3.3, §6.2/7.
- Hassas kimlik verisi: `KISISEL_VERI_ENVANTERI.md` satır 9–11, 14.
- Oturum veri modeli: `VERI_MODELI.md` §4.5, §4.10–4.11, §15.5.
- API token/kurum bağlamı: `API_GENEL_KURALLARI.md` §4, §9/1–3, 14.
- Kabul senaryoları: `KRITIK_TEST_VE_KABUL_PLANI.md` KAP-03, KAP-20, KAP-31.
- [Keycloak Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/) — OIDC Authorization Code Flow, PKCE `S256`, refresh-token rotation, session ve token timeout/iptal seçenekleri.
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html) — Admin Events/User Events kaynakları ve olay temsilleri için resmî REST sözleşmesi.
- [Keycloak `UsersResource` kaynak kodu](https://github.com/keycloak/keycloak/blob/main/services/src/main/java/org/keycloak/services/resources/admin/UsersResource.java) — kullanıcı create işleminin `201 Created` ve `Location` içindeki sağlayıcı user id ile dönmesinin çapraz kontrolü.
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0-18.html) — OIDC Authorization Code Flow ve önceden kaydedilmiş redirect URI kuralları.
- [Amazon Cognito: token revocation](https://docs.aws.amazon.com/cognito/latest/developerguide/token-revocation.html) ve [refresh token rotation](https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-client-apps.html) — yönetilen alternatifin değerlendirilmesinde kullanılan yetenekler.
- [Amazon Cognito feature plans](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-sign-in-feature-plans.html) — Essentials'ın yeni user pool varsayılanı, managed login ve Lite/Essentials özellik farkları.
- [Amazon Cognito endpoints and quotas](https://docs.aws.amazon.com/general/latest/gr/cognito.html) — Europe (Frankfurt) `eu-central-1` user-pool endpoint desteği.
- [Auth0: refresh token rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/configure-refresh-token-rotation) — SaaS alternatifinin değerlendirilmesinde kullanılan yetenekler.
- [Firebase: session management](https://firebase.google.com/docs/auth/admin/manage-sessions) — Firebase alternatifinin iptal doğrulama maliyeti.
- [Amazon Cognito fiyatlandırması](https://aws.amazon.com/cognito/pricing/) — MAU ve advanced-security fiyat yönü.
- [Auth0 fiyatlandırması](https://auth0.com/pricing) — ücretsiz/ücretli MAU katmanları.
- [Firebase fiyatlandırması](https://firebase.google.com/pricing) — Authentication/Identity Platform kotası.
- [Keycloak LICENSE.txt](https://github.com/keycloak/keycloak/blob/main/LICENSE.txt) — Apache 2.0 lisans bilgisi.

## Sonuçlar, riskler ve sonraki adımlar

### Olumlu sonuçlar

- Kimlik bilgisi işleme ve parola yaşam döngüsü iş uygulamasından ayrılır; OIDC tabanlı mobil
  ve gelecekteki web istemcileri aynı kimlik katmanını kullanabilir.
- Kurum izolasyonu, sınıf kapsamı ve izinler stale claim'lere bağlı olmadan backend'de canlı
  değerlendirilir.
- Sağlayıcı değişimi, IAM'in OIDC sınır adaptörüyle sınırlı tutulabilir; iş modülleri
  sağlayıcı SDK'sına bağımlı olmaz.

### Riskler ve azaltım

- Self-managed Keycloak fallback'i seçilirse A-010; ayrı DB topolojisi, TLS/alan adı, şifreli
  yedekleme ve geri yükleme, sürüm/CVE sorumluluğu, health check, kapasite/HA, log erişimi,
  toplam maliyet ve kesinti kurtarma hedeflerini yeniden onaya bağlar.
- İki katmanlı oturum yaşam döngüsü (Cognito + platform tokenları) yanlış entegrasyonda
  ayrışabilir. IAM-004–IAM-006 yenileme/iptal akışlarını tek application service sınırında; A-013 ise
  Cognito yönetim kimliğini secret manager/çalışma kimliği sınırında uygular. KAP-03, KAP-20 ve KAP-31
  otomasyona dönüştürülmeden IAM özelliği kabul edilmez.
- Cognito global sign-out'u kurum-kapsamlı iptal için kullanılmaz. Bu sınır ihlal edilirse
  kurumlar arası erişim kesintisi riski doğar; ilgili use-case'te explicit test zorunludur.

### Kapsam dışı

- E-posta/SMS sağlayıcısı, MFA, biyometrik kilit, passkey veya sosyal giriş etkinleştirilmez.
- A-004R1 öncesinde veya A-004R1–A-004R3 deney kapsamı dışında Keycloak/Cognito kurulumu,
  kalıcı user pool/realm, container image, tema,
  kullanıcı migrasyonu, mobil SDK ve backend endpointleri eklenmez.
- Hukuki saklama süreleri ve KVKK uyum değerlendirmesi yapılmaz.
