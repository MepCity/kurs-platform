# ADR-004 — Kimlik doğrulama sağlayıcısı seçimi

| Alan | Değer |
|---|---|
| Durum | Önerildi — inceleme bekliyor |
| Tarih | 14 Temmuz 2026 |
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
| **Keycloak (self-managed), tek global realm** | Açık standart OIDC/OAuth 2.0, kullanıcı adı/parola, PKCE zorunluluğu, dönen refresh token, oturum/administration API'leri; belirli bulut sağlayıcısına bağlamaz. | İşletme, yükseltme, yedekleme ve yüksek erişilebilirlik sorumluluğu platformdadır. | **Seçildi** |
| Amazon Cognito User Pools | Yönetilen hizmet, native app için PKCE, refresh-token rotasyonu ve iptal desteği sunar. | AWS hesabı/bölgesi ve operasyon modelini A-003'ten önce fiilen seçer; genel kullanıcı iptali kurum-kapsamlı iptalin yerine geçemez. | Reddedildi |
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

## Karar

İnceleme sonucunda kabul edilmek üzere kimlik doğrulama sağlayıcısı olarak **Keycloak'un
self-managed kurulumu**, tek bir global `kurs-platform` realm'iyle önerilir. Keycloak, IAM
modülünün parçası veya yetki kararlarının sahibi değildir; OIDC kimlik sağlayıcısı olarak ayrı
bir altyapı bileşenidir. Bu ayrım, modüler monoliti mikroservislere bölmez.

V1 yapılandırması aşağıdaki sınırları zorunlu kılar:

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

## Token yaşam döngüsü ve global uzlaştırma

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

## IAM veritabanı erişim kapısı

ADR-003 §5.3 uyarınca seçilen mekanizma **ayrı `iam_runtime` PostgreSQL rolüdür**; `app_runtime`
bu yüzeyde doğrudan tablo/sequence hakkı taşımaz. `iam_runtime` owner, superuser veya
`BYPASSRLS` olamaz; migration owner ayrıdır.

| Fiziksel tablo | `iam_runtime` SQL hakkı | Zorunlu sınır |
|---|---|---|
| `users` | `SELECT`, `UPDATE` | `GLOBAL` `FORCE RLS`; yalnız doğrulanmış `app.iam_target_user_id` hedefi. |
| `users` | `SELECT`, `INSERT` | İlk `IAM_PROVISIONING` `FORCE RLS`; yalnız önceden üretilmiş target, actor, kurum ve `TEACHER_ACCOUNT_CREATE`; `UPDATE`/`DELETE` yok. |
| `user_identities` | `SELECT`, `INSERT` | Yalnız `app.iam_operation_scope='IAM_PROVISIONING'` + `app.iam_operation_code='TEACHER_ACCOUNT_FINALIZE'`; `Location`tan alınmış subject, aynı command/target ve doğrulanmış `platform_user_id` attribute'u ile; ilk transaction'da erişim yok. |
| `user_identities` | `SELECT` | `AUTHENTICATION` `FORCE RLS`; yalnız server-set `app.iam_provider_issuer` + `app.iam_provider_subject` tam eşleşmesi tek satırı açar. Listeleme/wildcard/başka identity ve yazma yok. |
| `platform_administrators`, `platform_administrator_profiles` | `SELECT` | `FORCE RLS`; yalnız hedef kullanıcı için platform-yönetici aile üretimi yetki denetimi. |
| `trusted_devices` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`; yalnız hedef kullanıcı; `DELETE` yoktur. |
| `organization_memberships` | `SELECT`, `UPDATE` | `FORCE RLS`; `SET LOCAL app.organization_id` ve hedef üyelik birlikte eşleşir. |
| `organization_membership_roles`, `organization_membership_permissions`, `class_teacher_assignments` | `SELECT` | `FORCE RLS`; yalnız `app.organization_id` kapsamı. |
| `people`, `organization_memberships`, `organization_membership_roles` | `SELECT`, `INSERT` | İlk `IAM_PROVISIONING` `FORCE RLS`; yalnız actorun kurumunda, önceden üretilmiş tek targetta ve `TEACHER_ACCOUNT_CREATE`; `UPDATE`/`DELETE` yok. |
| `users`, `organization_memberships`, `iam_secret_deliveries` | `SELECT`, `INSERT`, gerekli `UPDATE` | Yalnız `app.iam_operation_scope='IAM_PROVISIONING'` + `app.iam_operation_code='TEACHER_ACCOUNT_FINALIZE'`; aynı command/targetta identity + `ACTIVE` + süresi geçmemiş teslim `READY` atomik yazılır. Başka hedef/kurum/command, `DELETE` ve alan dışı update reddedilir. |
| `context_selection_tokens` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`; üretim yalnız açık `GLOBAL` target-user scope'unda sahibi kullanıcı/cihaz için, tüketim yalnız hedef üyelikle daraltılmış kurum seçiminde olur. `UPDATE` yalnız koşullu `consumed_at`/`revoked_at`; kurum seçimi aile üretimiyle aynı transaction'dadır. |
| `refresh_token_families` | `SELECT`, `INSERT`, `UPDATE` | Kurum komutu: `FORCE RLS`, `app.iam_operation_scope='ORGANIZATION'` ve `SET LOCAL app.organization_id`; `USING`/`WITH CHECK` yalnız etkin hedef üyeliğin kullanıcı/cihaz ailesini açar. |
| `refresh_tokens` | `SELECT`, `INSERT`, `UPDATE` | Kurum komutu: aynı `ORGANIZATION` policy'si; `USING`/`WITH CHECK` yalnız yukarıdaki aile ve ardıl zincirini açar. |
| `refresh_token_families` | `SELECT`, `UPDATE` | Global güvenlik komutu: `FORCE RLS`, `app.iam_operation_scope='GLOBAL'` ve `SET LOCAL app.iam_target_user_id`; yalnız açık hedef kullanıcının tüm kurum/global aileleri, tablo geneli değil. Global policy `INSERT` içermez. |
| `refresh_tokens` | `SELECT`, `UPDATE` | Global güvenlik komutu: aynı `GLOBAL` policy; yalnız hedef kullanıcının ailelerinin refresh/access token satırları. Global policy `INSERT` içermez. |
| `permission_catalog`, `audit_action_catalog` | `SELECT` | Salt okunur global katalog; tenant RLS listesinde değildir ve yazma hakkı yoktur. |
| `audit_logs` | `SELECT`, `INSERT` | `FORCE RLS`: `ORGANIZATION` komutu yalnız `app.organization_id`; `GLOBAL` güvenlik komutu yalnız `event_scope='GLOBAL'`, `target_entity_type='USER'`, `target_entity_id=app.iam_target_user_id`; provisioning yalnız kendi `organization_id`/actor/target kaydını yazar. Başka kapsam varsayılan reddir. `UPDATE`, `DELETE` yoktur. |
| `idempotency_keys` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`: `ORGANIZATION` için `scope_type='ORGANIZATION'`, `organization_id=app.organization_id` ve `user_id=app.iam_actor_user_id`; `GLOBAL` için `scope_type='GLOBAL'`, `organization_id IS NULL` ve `user_id=app.iam_actor_user_id`. Provisioning yalnız aynı actor+kurum+operationCode kaydını yazar. `user_id` P-010'daki aktördür, hedef değildir. |
| `iam_provider_commands` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`: `GLOBAL` bağlamda yalnız target identity→user; `TEACHER_ACCOUNT_CREATE` provisioning'de yalnız `target_user_id`, actor/kurum/username-hash ve şifreli payload ile `INSERT`. Command-type XOR hedef türünü zorunlu kılar; lease/fencing kontrolü yazmada zorunludur. |
| `iam_secret_deliveries` | `SELECT`, `INSERT`, `UPDATE` | `FORCE RLS`; yalnız aynı create command ve target. Secret yalnız finalize sonrası, en fazla 10 dakikalık bir-kezlik okuma için `READY` olur. Okuma anında alıcının aynı kurumda etkin üyeliği ve geri alınmamış `ORG_ADMIN` rolü veya etkin `TEACHER_ACCOUNT_MANAGE` izni yeniden doğrulanır; yalnız `recipient_actor_user_id` eşleşmesi yeterli değildir. Yalnız `ESCROWED → READY/EXPIRED` ve `READY → CONSUMED/EXPIRED` durum geçişleri açılır; terminal durumdan update yoktur. |
| `iam_event_cursors` | `SELECT`, `INSERT`, `UPDATE` | Yalnız `ADMIN_EVENTS` veya `USER_EVENTS` kaynak satırı; cursor ilerlemesi başarılı işlemle koşulludur. |
| `iam_event_deduplications` | `SELECT`, `INSERT` | Yalnız `(source, event_id)` benzersiz anahtarı; runtime silme/güncelleme yapamaz. |

`iam_runtime` owner, superuser veya `BYPASSRLS` olamaz; sahibi olduğu nesne bulunmaz. Migration
owner ayrı kalır ve production runtime'da kullanılmaz. Bu rol, tenant tablolarda `SET LOCAL`
ile kurulan kurum RLS bağlamını baypas eden genel erişim yolu değildir. Her IAM transaction'ı
başta `SET LOCAL app.iam_operation_scope` ile tam olarak `ORGANIZATION`, `GLOBAL`,
`AUTHENTICATION` veya `IAM_PROVISIONING` seçer; hiçbir kapsam genel GLOBAL+ORGANIZATION birleşimi
değildir. Normal iki scope doğrulanmış `app.iam_actor_user_id` ister. `ORGANIZATION`, geçerli
`app.organization_id` ister ve `app.iam_target_user_id` boş olmalıdır; `GLOBAL`, geçerli
`app.iam_target_user_id` ister ve `app.organization_id` boş olmalıdır. Aktör ve hedef GLOBAL
işlemde ayrı bağlamlardır. `AUTHENTICATION` yalnız doğrulanmış access tokendan server-set
`app.iam_provider_issuer`+`app.iam_provider_subject` ister; actor/target/organization ve bütün
diğer tablo yüzeyleri boş/kapalıdır. `IAM_PROVISIONING`, actor, organization, önceden üretilmiş
target ve allow-listli `app.iam_operation_code` ister. Oluşturma ile finalize aynı scope'u
paylaşır fakat sırasıyla yalnız `TEACHER_ACCOUNT_CREATE` ve `TEACHER_ACCOUNT_FINALIZE` operation
code'larıyla birbirinden ayrılır; `IAM_PROVISIONING_FINALIZE` adlı beşinci bir scope yoktur.
Kapsam değişiminde önceki bütün `SET
LOCAL` değerleri temizlenir; iki kapsamın ayarlarının birlikte bulunması, bilinmeyen scope veya
eksik değer tüm `USING`/`WITH CHECK` policy'leri tarafından reddedilir. Tenant tablolarında
politikalar hem `current_setting('app.organization_id', true)` hem hedef üyelik zincirini
zorunlu kılar. Global kullanıcı tablolarında ayrı `FORCE RLS` politikası yalnız doğrulanmış
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

## Keycloak → IAM güvenlik olayı uzlaştırması

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
credential-mutasyon yolu listesi A-010'un sabitlediği Keycloak sürümünde Management API ile
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

Sorumluluk ayrımı: A-010 Keycloak ortamı/hosting, scheduler ve alarm altyapısını; A-013
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
iptal gecikmesi ile A-010'un sabitlediği Keycloak sürümünden alınmış kanonik/`/`-önekli logout
ve credential-mutasyon Admin Event path örneklerinin parser testlerini uygular.

## Uygulama sorumlulukları ve güvenlik sınırı

| Katman | Sorumluluk |
|---|---|
| Keycloak | Kimlik bilgisi doğrulama, parola politikası/özeti, reset akışı, kaba kuvvet koruması, global kullanıcı oturumu, OIDC discovery/JWKS ve kimlik olayları. |
| IAM (modüler monolit) | `(issuer, subject)`–`users.id` eşlemesi, Keycloak access-token doğrulaması, kurum seçimi, opaque platform token üretimi/HMAC özet saklama/rotasyonu, `session_generation`, üyelik-rol-izin-sınıf doğrulaması, kurum-kapsamlı iptal ve audit. |
| Mobil istemci | Sistem tarayıcısıyla `state`/`nonce`/PKCE akışı, callback eşleştirmesi, ilk değişimden sonra Keycloak tokenlarını silme, yalnız platform tokenlarını OS güvenli saklama alanında tutma, iptal sonrası `prompt=login`, tokenı loglamama ve kullanıcıya açık oturum/eşitleme durumu. |

Backend, Keycloak **access tokenını** kabul etmeden önce en az imza/JWKS, `iss`, `aud`, `azp`,
`exp`, `nbf` (varsa) ve gerekli scope doğrulamasını yapar. `state`, `nonce` ve PKCE callback
doğrulaması mobile aittir. ID token API erişimi veya IAM token değişimi için kabul edilmez.
Keycloak rolleri ve grupları, uygulama yetkisinin yerine geçmez. Sağlayıcı erişilemezse yeni giriş
ve parola sıfırlama başarısız olur; platform refresh'i Keycloak'a çağrı yapmadan kendi canlı
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
| Sağlayıcı ve mobil giriş akışı; public client, Code + PKCE S256 ile nettir. | Karşılandı |
| Parola ve ham token saklama sınırı; token rotasyonu, ömür ve iptal davranışı tanımlıdır. | Karşılandı |
| Kurum üyeliği, rol, izin ve sınıf yetkisi sağlayıcı claim'ine taşınmadan IAM'de kalır. | Karşılandı |
| Kurum-kapsamlı iptal başka kurum oturumunu kapatmaz; `session_generation` kontrolü korunur. | Karşılandı |
| Kaba kuvvet koruması, yönetim API sınırı, audit ve hassas veri minimizasyonu ele alınır. | Karşılandı |
| Veritabanı/hosting, MFA/biometri, oran eşikleri ve uygulama iskeleti için kapsam dışı karar alınmamıştır. | Karşılandı |
| Güvenilir cihaz oturumu, V1 çıkış/iptal/global olay/saklama kaybı sınırlarıyla tanımlıdır; periyodik giriş zorunlu değildir. | Karşılandı |

Bu ADR belge kararıdır; henüz uygulama kodu, Keycloak image/dependency'si, ortam değişkeni
veya gerçek kullanıcı hesabı eklemez. Bu nedenle otomatik test çalıştırılacak bir uygulama
altyapısı yoktur. Onay sonrası IAM-003–IAM-006, IAM-009 ve A-013; bu kabul ölçütlerini çalıştırılabilir
yapılandırma ve testlere dönüştürür.

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

- Self-managed Keycloak kritik bir bağımlılıktır. **A-010**, A-003'ün seçtiği DB/hosting
  üzerinde Keycloak DB topolojisi, TLS/alan adı, şifreli yedekleme ve geri yükleme tatbikatı,
  sürüm/CVE yükseltme sorumluluğu, health check, kapasite/HA, log erişimi, maliyet bütçesi ve
  kesinti kurtarma RTO/RPO'sunu sözleşmeye bağlamalıdır.
- İki katmanlı oturum yaşam döngüsü (Keycloak + platform tokenları) yanlış entegrasyonda
  ayrışabilir. IAM-004–IAM-006 yenileme/iptal akışlarını tek application service sınırında; A-013 ise
  Keycloak yönetim kimlik bilgisini secret manager'da uygular. KAP-03, KAP-20 ve KAP-31
  otomasyona dönüştürülmeden IAM özelliği kabul edilmez.
- Keycloak'un global logout'u kurum-kapsamlı iptal için kullanılmaz. Bu sınır ihlal edilirse
  kurumlar arası erişim kesintisi riski doğar; ilgili use-case'te explicit test zorunludur.

### Kapsam dışı

- E-posta/SMS sağlayıcısı, MFA, biyometrik kilit, passkey veya sosyal giriş etkinleştirilmez.
- Keycloak kurulumu, container image, realm export'u, tema, kullanıcı migrasyonu, mobil SDK
  ve backend endpoint'leri eklenmez.
- Hukuki saklama süreleri ve KVKK uyum değerlendirmesi yapılmaz.
