# IAM Giriş ve Oturum API Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | IAM-001 — Giriş/oturum API sözleşmesini kesinleştir |
| Belge sürümü | 1.1 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlı sözleşmeler | `API_GENEL_KURALLARI.md`, `VERI_MODELI.md`, `YETKI_MATRISI.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` |
| Son güncelleme | 16 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, mobil istemcinin Cognito ile yaptığı etkileşimli kimlik doğrulama sonrasında platform
IAM katmanıyla kurduğu giriş ve oturum API sözleşmesini tanımlar. Amaç; giriş, kurum seçimi,
kurum üyeliği gerektirmeyen global platform yöneticisi oturumu, aktif oturumun kurulması,
oturumun yenilenmesi ve kullanıcının kendi mevcut oturumundan çıkış davranışını sonraki
IAM/backend/mobil görevleri için çelişkisiz hâle getirmektir.

Bu sözleşme:

- Mobilin sistem tarayıcısında yürüttüğü OIDC Authorization Code + PKCE `S256` akışını
  başlatmaz; mobil istemcinin sağlayıcıyla etkileşimi `ADR-004` kapsamındaki Cognito profiline
  göre istemci tarafında yürür.
- Başka kullanıcının veya başka cihazın kurum kapsamlı iptali, global cihaz kaldırma, zorunlu
  yeniden kimlik doğrulama ayrıntıları ve cihaz yönetimi listesini tanımlamaz; bunlar `IAM-002`
  sahibidir.
- Kullanıcı provisioning'i, öğretmen hesabı oluşturma, parola teslimi veya yönetim komutlarını
  tanımlamaz; bunlar `STAFF-*`, `IAM-004` ve ilişkili provider-command görevlerindedir.

## 2. Bağlayıcı karar özeti

- İş API'leri Cognito ID tokenı veya Cognito access tokenı kabul etmez; yalnız platformun opaque
  access tokenı kabul edilir.
- İlk etkileşimli girişten sonra IAM, doğrulanmış Cognito access tokenına karşı bir kez
  `contextSelectionToken` üretir; kullanıcı bu token ile üyeliklerini listeler, tek kurum
  bağlamı seçer veya aktif platform yöneticisiyse global admin oturumu başlatır.
- Kurum seçimi başarılı olduğunda aynı transaction içinde tek kullanımlı context-selection tokenı
  tüketilir, kurum kapsamlı refresh token ailesi oluşturulur ve platform access/refresh çifti
  döner.
- Aktif `platform_administrators` kaydı olan kullanıcı, kurum üyeliği gerektirmeden ayrı bir
  global refresh token ailesi kurabilir; bu aile hiçbir sahte `organizationMembership` nesnesi
  taşımaz.
- Platform access tokenı opaque bearer değerdir; JWT claim'i, ayrı keyset'i veya client-side
  çözümlenebilir alanları yoktur.
- Platform access token ömrü bağlayıcı olarak tam **10 dakika**dır.
- Refresh token rotasyonu tek kullanımlıdır; reuse tespitinde ilgili aile fail-closed iptal edilir
  ve `401 SESSION_REVOKED` döner.
- `provider-token-exchange`, aktivasyon ve refresh için aynı `Idempotency-Key` + aynı fingerprint ilk tamamlanmış cevabın
  eşdeğerini döndürür; replay kontrolü `consumed_at`/`used_at` reuse değerlendirmesinden önce
  çalışır.
- `provider-token-exchange` kurumsuz akış olduğu için `GLOBAL_PLATFORM_ADMIN` idempotency kapsamını
  kullanmaz; ayrı `IAM_AUTH` kapsamı kullanır.
- Yeni bir platform ailesi kurulmadan hemen önce Cognito kullanıcı durumunun kanonik kontrolü
  zorunludur; provider erişilemez, belirsiz, disabled veya revoked ise yeni aile üretilmez.
- Kurum kapsamı tokenın özelliğidir; istemci kurum kimliğini değiştirerek mevcut tokenı başka
  kuruma genişletemez.
- V1 rolling refresh, zorunlu 14/30 günlük periyodik yeniden giriş oluşturmaz; oturum ancak açık
  çıkış, iptal, global güvenlik olayı, reauth eşiği veya güvenli saklama kaybıyla sonlanır.

## 3. Aktörler ve ön koşullar

### 3.1. Mobil istemci ön koşulları

- Mobil istemci Cognito public client'ı ile sistem tarayıcısında Authorization Code + PKCE
  `S256` akışını tamamlamış olmalıdır.
- `state`, `nonce` ve PKCE callback doğrulaması mobil tarafta tamamlanmadan IAM token değişimi
  çağrısı yapılamaz.
- İstemci, güvenilir cihaz tanımı için uygulama kurulumuna ait kriptografik rastgele
  `deviceIdentifier` üretir. Bu değer IMEI, reklam kimliği, vendor ID veya donanım parmak izi
  değildir.
- İstemci, platform tokenlarını OS güvenli saklamasında tutar; token, parola ve sağlayıcı kodunu
  uygulama loguna veya analitik olaylarına yazmaz.

### 3.2. IAM ön koşulları

- IAM, Cognito access tokenını en az JWKS imzası, `iss`, `client_id`, süre, gerekliyse `nbf` ve
  scope bakımından doğrular.
- IAM, hesap eşlemesini yalnız doğrulanmış `(issuer, subject)` çifti üzerinden
  `user_identities` tablosunda yapar.
- `users`, `organization_memberships`, `organization_membership_roles`, `trusted_devices`,
  `context_selection_tokens`, `refresh_token_families`, `refresh_tokens` ve
  `platform_administrators` tabloları `VERI_MODELI.md` ile uyumlu olarak mevcuttur.

### 3.3. Oturum kapsamları

- `ORGANIZATION` kapsamlı oturum, tek bir `organizationMembershipId` bağlamında açılır ve
  `refresh_token_families.organization_membership_id` doludur.
- `GLOBAL_PLATFORM_ADMIN` kapsamlı oturum, yalnız aktif `platform_administrators.user_id`
  eşleşmesi varsa açılır ve `refresh_token_families.organization_membership_id = NULL` olur.
- Global platform yöneticisi oturumu, bir kuruma sahte üyelik bağlamaz; `organizationMembership`
  alanı yoktur ve istemci bunu `null` doldurup kurum yetkisi varmış gibi yorumlayamaz.
- Global platform yöneticisi, kurum verisine erişirken ayrıca açık hedef kurum bağlamı taşıyan
  modül uçlarını kullanır; her kurum verisi görüntüleme/değiştirme işlemi audit üretir.

## 4. Kaynaklar ve uçlar

Bu sözleşme yedi IAM uç yüzeyini bağlar:

1. `POST /api/v1/iam/auth/provider-token-exchange`
2. `GET /api/v1/iam/auth/context-selections`
3. `POST /api/v1/iam/auth/platform-admin/activate`
4. `POST /api/v1/iam/auth/context-selections/{organizationMembershipId}/activate`
5. `GET /api/v1/iam/sessions/me`
6. `POST /api/v1/iam/sessions/refresh`
7. `POST /api/v1/iam/sessions/logout`

`/api/v1/iam/sessions/revoke`, cihaz listesi, diğer kullanıcının oturumlarını iptal etme ve
yeniden doğrulama komutları bu belgeye ait değildir; `IAM-002` ile tanımlanacaktır.

### 4.1. İşlem, Scope ve Transaction Matrisi

Her DB transaction tam olarak tek transaction scope kullanır; iki scope aynı transaction içinde
birleştirilemez. Bir API işlemi birden fazla transaction gerektiriyorsa her aşama kendi scope'u
ile açıkça tanımlanır.

| İşlem | Transaction scope | Tablolar | SQL işlemleri |
|---|---|---|---|
| `PROVIDER_TOKEN_EXCHANGE / Aşama 1` | `AUTHENTICATION` | `user_identities` | İmza/issuer/audience doğrulamasından sonra `SELECT` ile `issuer + subject` eşleşmesinden tek `actorUserId` çözülür; yazma yoktur |
| `PROVIDER_TOKEN_EXCHANGE / Aşama 2` | `IAM_AUTH` | `users`, `trusted_devices`, `context_selection_tokens`, `idempotency_keys`, `iam_auth_response_escrows`, `audit_logs` | Aşama 1'den çözülen `actorUserId` ile `SELECT` kullanıcı; `INSERT` veya koşullu `UPDATE` cihaz; `INSERT` context token; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` replay escrow; `INSERT` audit |
| `PLATFORM_ADMIN_ACTIVATE` | `IAM_AUTH` | `context_selection_tokens`, `trusted_devices`, `platform_administrators`, `refresh_token_families`, `refresh_tokens`, `idempotency_keys`, `iam_auth_response_escrows`, `audit_logs` | `SELECT`/koşullu `UPDATE` context token; `SELECT` cihaz/admin; `INSERT` family + token; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` replay escrow; `INSERT` audit |
| `CONTEXT_ACTIVATE` | `IAM_AUTH` | `context_selection_tokens`, `trusted_devices`, `organization_memberships`, `organization_membership_roles`, `refresh_token_families`, `refresh_tokens`, `idempotency_keys`, `iam_auth_response_escrows`, `audit_logs` | `SELECT`/koşullu `UPDATE` context token; `SELECT` cihaz/üyelik/rol; `INSERT` family + token; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` replay escrow; `INSERT` audit |
| `SESSION_REFRESH` | `IAM_AUTH` | `refresh_token_families`, `refresh_tokens`, `idempotency_keys`, `iam_auth_response_escrows`, `audit_logs` | `SELECT` family/token; yerel kullanıcı/üyelik/cihaz/generation/revoke durumunu doğrula; `UPDATE` eski token `used_at`; `INSERT` yeni token; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` replay escrow; `INSERT` audit |
| `SESSION_LOGOUT` | `IAM_AUTH` | `refresh_token_families`, `refresh_tokens`, `idempotency_keys`, `audit_logs` | `SELECT` family/token; `UPDATE` family/token `revoked_at`; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` audit |

Bu matrisin dışındaki tablo erişimi sözleşme ihlalidir. `PROVIDER_TOKEN_EXCHANGE`in
`AUTHENTICATION` aşaması hiçbir cihaz, idempotency, escrow, context token veya audit mutasyonu
yapamaz; `IAM_AUTH` aşaması da `issuer + subject` ile identity taraması yapamaz, yalnız çözülmüş
`actorUserId` üzerinde çalışır. `PLATFORM_ADMIN_ACTIVATE`,
`CONTEXT_ACTIVATE` ve `SESSION_REFRESH`; context/token ailesi, refresh token, idempotency,
response escrow ve audit değişikliklerini gerçekten aynı DB transaction'ında tamamlamak zorundadır.

## 5. Ortak istek/cevap kuralları

- `API_GENEL_KURALLARI.md` bölüm 3, 4, 5 ve 7 bu belge için aynen geçerlidir.
- `provider-token-exchange`, `platform-admin/activate`, `context-selections/*/activate`,
  `sessions/refresh` ve `sessions/logout` yazma komutları `Idempotency-Key` ister.
- Token değerleri JSON gövdesinde döner; response header veya query string içinde taşınmaz.
- Ham access/refresh/context-selection tokenı, parola, Cognito `authorization_code` veya ham
  Cognito access tokenı denetim kaydına, normal idempotency payload'una veya loglara yazılmaz.
- Her başarılı oturum cevabında istemcinin eşitleme/uyarı mantığını kurabilmesi için
  `session.scope`, `session.expiresAt` ve uygun olduğunda
  `organizationMembership.sessionGeneration` döner.
- `GET /api/v1/iam/sessions/me` ve diğer korunan uçlar `Authorization: Bearer
  <platform-access-token>` ister.
- `GET /api/v1/iam/auth/context-selections` yalnız `Authorization: Bearer
  <contextSelectionToken>` kabul eder; platform access tokenı veya Cognito tokenı burada geçerli
  değildir.
- IAM auth uçlarında rate limit zorunludur. Sayısal eşikler operasyon ayarıdır; aşıldığında
  `429 RATE_LIMITED` ve uygun olduğunda `Retry-After` döner.

### 5.1. Güvenli replay ve sonuç geri oynatma sınırı

- `provider-token-exchange`, `platform-admin/activate`, kurum `activate` ve
  `sessions/refresh` için
  sunucu, `Idempotency-Key` + doğrulanmış aktör + uç türü + güvenli istek fingerprint'i
  üzerinden önce tamamlanmış sonuç kaydı arar.
- Aynı `Idempotency-Key` ve aynı fingerprint ile daha önce tamamlanmış istek varsa sunucu, ilk
  tamamlanmış cevabın eşdeğerini döndürür. Bu replay kontrolü `context_selection_tokens.consumed_at`
  veya `refresh_tokens.used_at` reuse değerlendirmesinden önce çalışır.
- Aynı key ile eşzamanlı gelen iki istekte tek kazanan kalıcı sonucu yazar; diğer istekler aynı
  `resultReference` üzerinden güvenli replay cevabı alır. İkinci yan etki, ikinci aile veya ikinci
  refresh üretimi olmaz.
- Güvenli replay için sunucu, ham tokenları idempotency satırında saklamaz. Bunun yerine
  `VERI_MODELI.md` §14'teki şifreli ve süreli `iam_auth_response_escrows` yüzeyini veya aynı
  güvenlik özelliklerini sağlayan eşdeğer AEAD escrow mekanizmasını kullanır. Bu yüzeyde
  `aead_key_reference`, actor/operation/device bağlamı, token fingerprint, TTL, tek kapsamlı
  erişim, silme/expiry ve expiry sonrası fail-closed davranışı zorunludur.
- Escrow süresi dolduktan sonra sunucu ikinci kez iş üretmez ve yeni token uydurmaz; istemci
  güvenli yeniden auth veya yeni deneme akışına yönlendirilir.
- Aynı `Idempotency-Key` farklı fingerprint ile tekrar kullanılırsa `409 IDEMPOTENCY_KEY_REUSED`
  döner.

### 5.2. Operation Bazlı Fingerprint ve Replay TTL

| İşlem | Fingerprint girdisi | Replay TTL | Replay sonucu bağ |
|---|---|---:|---|
| `PROVIDER_TOKEN_EXCHANGE` | Cognito access token fingerprint + `deviceIdentifier` + `actorUserId` | 5 dakika | Üretilen `contextSelectionToken` |
| `PLATFORM_ADMIN_ACTIVATE` | `contextSelectionToken` fingerprint + `deviceIdentifier` + `actorUserId` | 5 dakika | Oluşturulan global `refresh_token_family` + ilk `refresh_token` |
| `CONTEXT_ACTIVATE` | `contextSelectionToken` fingerprint + hedef `organizationMembershipId` + `deviceIdentifier` + `actorUserId` | 5 dakika | Oluşturulan kurum `refresh_token_family` + ilk `refresh_token` |
| `SESSION_REFRESH` | Platform refresh token fingerprint + `deviceIdentifier` + `actorUserId` | 10 dakika | Oluşturulan ardıl `refresh_token` |
Replay escrow, ilgili context token veya token family sonucuna FK/kanonik referansla bağlanır.
TTL dolduğunda veya escrow çözme/decrypt başarısız olduğunda sunucu:

1. Yeni iş üretmez.
2. Replay edilen sonuç bir context token ise tokenı `revoked_at` ile iptal eder.
3. Replay edilen sonuç bir family/token ise ilgili family/token sonucunu idempotent biçimde iptal eder.
4. Güvenlik audit'i yazar.

`IAM-004` auth replay escrow üretimi/çözümü, `IAM-005` refresh replay/expiry iptali,
`IAM-006` global güvenlik bariyeri ve cleanup/reconciliation sahipliğini uygular. `IAM-009`
bu TTL/expiry/reconciliation davranışlarını entegrasyon ve RLS testleriyle kanıtlar.

## 6. Veri şekilleri

### 6.1. Ortak nesneler

```json
{
  "user": {
    "id": "8c728d4e-4e84-4b22-81f9-ec92eb03fa6b",
    "displayName": "Yasir Arslan",
    "status": "ACTIVE"
  }
}
```

```json
{
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z"
  }
}
```

```json
{
  "organizationMembership": {
    "id": "5276942f-ef2f-44ca-9ef6-2af1dd58a0fc",
    "organizationId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
    "organizationName": "Fındıklı Kur'an Kursu",
    "organizationStatus": "ACTIVE",
    "membershipStatus": "ACTIVE",
    "roleCodes": ["ORG_ADMIN"],
    "sessionGeneration": 3
  }
}
```

```json
{
  "platformAdministrator": {
    "status": "ACTIVE"
  }
}
```

```json
{
  "session": {
    "scope": "ORGANIZATION",
    "accessToken": "<opaque-access-token>",
    "refreshToken": "<opaque-refresh-token>",
    "tokenType": "Bearer",
    "expiresAt": "2026-07-16T09:32:31Z",
    "refreshExpiresAt": "2026-08-15T09:22:31Z",
    "authenticatedAt": "2026-07-16T09:22:31Z"
  }
}
```

### 6.2. Sunucu alan kuralları

- `displayName`, `organizationName` ve `deviceName` kullanıcıya gösterilebilir alanlardır;
  yetki/kimlik kaynağı değildir.
- `roleCodes`, yalnız istemcinin görünüm kararı için özet bilgidir; son yetki kararı her zaman
  sunucudadır.
- `sessionGeneration`, istemcinin eski önbelleği bloklaması için bilgi amaçlı döner; istemci bu
  değeri kendisi yükseltemez veya varsayım kaynağı yapamaz.
- `refreshExpiresAt`, kesin sayı değil sözleşme alanıdır; süre değeri operasyon/ürün kararıyla
  değişebilir, ancak `accessToken` ömründen uzundur.
- `session.scope`, yalnız `ORGANIZATION` veya `GLOBAL_PLATFORM_ADMIN` değerini alır.
- `platformAdministrator` nesnesi yalnız global admin ailelerinde döner; kurum üyeliğiyle
  aynılaştırılamaz.

## 7. `POST /api/v1/iam/auth/provider-token-exchange`

### 7.1. Amaç

Doğrulanmış Cognito access tokenına karşı kullanıcı hesabını ve güvenilir cihazı eşler; tek
kullanımlı `contextSelectionToken` üretir ve kullanıcının global platform yöneticisi aktivasyonuna
uygun olup olmadığını bildirir.

### 7.2. İstek

Başlıklar:

- `Authorization: Bearer <cognito-access-token>`
- `Idempotency-Key: <clientMutationId>`

Gövde:

```json
{
  "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
  "platform": "ANDROID",
  "deviceName": "Pixel 8"
}
```

Alan kuralları:

- `deviceIdentifier` zorunlu UUID'dir.
- `platform` zorunlu enumdur: `IOS` veya `ANDROID`.
- `deviceName` opsiyoneldir; boş olmayan, kesilmiş/güvenli gösterim metni olarak saklanabilir.
- Aynı kullanıcı ve aynı aktif `deviceIdentifier` için tekrar çağrı ikinci aktif cihaz
  oluşturmaz; mevcut cihaz bağlamında yeni context-selection token üretir veya ilk tamamlanmış
  sonucu döner.
- Bu uç için replay kapsamı `IAM_AUTH`tir; aynı `actorUserId`, `operationType`,
  `deviceIdentifier` ve güvenli Cognito token fingerprint'i eşleşmeden önceki sonuç okunamaz.
- Bu uç aile üretmez; dolayısıyla Cognito kullanıcı durumunun kanonik management kontrolü burada
  zorunlu değildir. Kanonik provider durumu, her yeni aile kurulmadan hemen önce aktivasyon
  uçlarında doğrulanır.

### 7.3. Başarılı cevap

`200 OK`

```json
{
  "contextSelectionToken": "<opaque-context-selection-token>",
  "contextSelectionTokenExpiresAt": "2026-07-16T09:27:31Z",
  "availableScopes": ["ORGANIZATION_SELECTION", "GLOBAL_PLATFORM_ADMIN"],
  "user": {
    "id": "8c728d4e-4e84-4b22-81f9-ec92eb03fa6b",
    "displayName": "Yasir Arslan",
    "status": "ACTIVE"
  },
  "platformAdministrator": {
    "status": "ACTIVE"
  },
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z"
  }
}
```

Davranış:

- Sunucu, Cognito access tokenının imza/issuer/audience doğrulamasını yaptıktan sonra önce salt
  okunur `AUTHENTICATION` transaction'ında yalnız `issuer + subject` eşleşmesinden tek
  `actorUserId` çözer; bu aşama hiçbir cihaz, idempotency, escrow, context token veya audit
  mutasyonu yapmaz.
- Ardından ayrı `IAM_AUTH` mutation transaction'ı, çözülmüş `actorUserId` üzerinde `users`
  durumunu doğrular ve cihaz/context/idempotency/escrow/audit yazılarını atomik yürütür.
- IAM, `trusted_devices` kaydını oluşturur veya mevcut aktif kaydı bulur.
- IAM, kısa ömürlü tek kullanımlı `context_selection_tokens` satırı yazar.
- IAM, doğrulanmış `auth_time` değerini bu context tokenına `authenticatedAt` olarak bağlar;
  aktivasyon uçları bu doğrulanmış zamanı kullanır, istemciden yeniden almaz.
- Başarılı sonuç, ham context tokenı normal `result_payload` içine yazmadan yalnız şifreli auth
  replay escrow yüzeyinde tutulur; aynı key ve fingerprint ile kayıp cevap güvenli replay edilir.
- Bu uç kurum listesi veya platform access/refresh tokenı döndürmez.
- Kullanıcı `PROVISIONING` veya `SUSPENDED` ise platform oturumu kurulmaz.
- Kullanıcı aktif `platform_administrators` kaydına sahipse `availableScopes` içinde
  `GLOBAL_PLATFORM_ADMIN` döner; aksi hâlde dönmez.

### 7.4. Hata kuralları

- `401 UNAUTHENTICATED`: Cognito access tokenı geçersiz, süresi dolmuş veya beklenen client'a ait
  değil; `AUTHENTICATION` aşamasında identity çözülememesi de buna dahildir.
- `403 REAUTHENTICATION_REQUIRED`: doğrulanmış `auth_time`,
  `users.reauthentication_required_after` eşiğinin gerisinde.
- `403 ACCOUNT_NOT_READY`: kullanıcı veya identity eşleşmesi var ama `PROVISIONING`/`SUSPENDED`
  durumda.
- `404 RESOURCE_NOT_FOUND`: doğrulanmış token için platform identity eşleşmesi yok; istemci bunu
  kayıt oluşturma çağrısı gibi yorumlayamaz.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı cihaz veya istek özetiyle tekrar kullanılmış.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 8. `GET /api/v1/iam/auth/context-selections`

### 8.1. Amaç

`contextSelectionToken` ile kullanıcının etkin kurum üyeliklerini listeler.

### 8.2. İstek

Başlık:

- `Authorization: Bearer <contextSelectionToken>`

Sorgu parametresi yoktur. Liste her zaman çağıran kullanıcının etkin üyelikleriyle sınırlıdır.

### 8.3. Başarılı cevap

`200 OK`

```json
{
  "items": [
    {
      "id": "5276942f-ef2f-44ca-9ef6-2af1dd58a0fc",
      "organizationId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
      "organizationName": "Fındıklı Kur'an Kursu",
      "organizationStatus": "ACTIVE",
      "membershipStatus": "ACTIVE",
      "roleCodes": ["ORG_ADMIN"],
      "sessionGeneration": 3
    }
  ],
  "page": {
    "nextCursor": null,
    "hasNextPage": false
  }
}
```

Davranış:

- Listeleme tokenı tüketmez.
- Yalnız `membershipStatus=ACTIVE` ve kurum durumu oturum kurulmasına uygun üyelikler döner.
- Sıralama deterministiktir: `organizationName ASC`, eşitlikte `id ASC`.

### 8.4. Hata kuralları

- `401 UNAUTHENTICATED`: token süresi dolmuş, iptal edilmiş veya hash eşleşmiyor.
- `403 ACCOUNT_NOT_READY`: kullanıcı aktif değil.
- `403 ORGANIZATION_CONTEXT_REQUIRED`: platform access tokenı ile bu uca gelinmişse.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 9. `POST /api/v1/iam/auth/platform-admin/activate`

### 9.1. Amaç

Tek kullanımlı `contextSelectionToken` ile kurum üyeliği gerektirmeyen global platform yöneticisi
oturumu kurar.

### 9.2. İstek

Başlıklar:

- `Authorization: Bearer <contextSelectionToken>`
- `Idempotency-Key: <clientMutationId>`

Gövde yoktur.

### 9.3. Başarılı cevap

`200 OK`

```json
{
  "user": {
    "id": "8c728d4e-4e84-4b22-81f9-ec92eb03fa6b",
    "displayName": "Yasir Arslan",
    "status": "ACTIVE"
  },
  "platformAdministrator": {
    "status": "ACTIVE"
  },
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z"
  },
  "session": {
    "scope": "GLOBAL_PLATFORM_ADMIN",
    "accessToken": "<opaque-access-token>",
    "refreshToken": "<opaque-refresh-token>",
    "tokenType": "Bearer",
    "expiresAt": "2026-07-16T09:32:31Z",
    "refreshExpiresAt": "2026-08-15T09:22:31Z",
    "authenticatedAt": "2026-07-16T09:22:31Z"
  }
}
```

Davranış:

- Sunucu önce güvenli replay kontrolünü yapar; aynı key ve fingerprint için ilk tamamlanmış cevap
  eşdeğeri döner.
- Yeni global aile kurulmadan hemen önce IAM, Cognito kullanıcı durumunu management API ile
  kanonik olarak doğrular.
- Aynı transaction içinde şu kontroller zorunludur: aktif `trusted_device`, aktif `users`,
  aktif `platform_administrators`, doğrulanmış `authenticatedAt >
  users.reauthentication_required_after`, tüketilmemiş `contextSelectionToken` ve uygun
  idempotency kaydı.
- Kanonik Cognito kontrolü `disabled` veya `revoked` sonucu verirse yeni aile üretilmez; kullanıcının
  mevcut yalnız kendi `actorUserId`'sine ait bütün `refresh_token_families` ve `refresh_tokens`
  satırları `revoked_at` ile idempotent kapatılır, yalnız `users.reauthentication_required_after`
  transaction zamanına yükseltilir ve tek güvenlik audit'i yazılır. Bu dar fail-closed dal,
  yalnız sunucunun doğruladığı canonical provider sonucu ve `PLATFORM_ADMIN_ACTIVATE`
  operation code'u ile açılır; istemci parametresi bu yetkiyi açamaz.
- Kanonik Cognito durumu erişilemez veya belirsizse mevcut aileler hakkında tahmin yürütülmez;
  yalnız yeni aile fail-closed engellenir.
- Başarıda `context_selection_tokens.consumed_at` yazılır, `organization_membership_id = NULL`
  global refresh token ailesi oluşturulur ve replay için güvenli `resultReference` kalıcılaştırılır.
- Bu cevapta `organizationMembership` alanı bulunmaz; istemci bunu sentetik üyelik gibi
  dolduramaz.

### 9.4. Hata kuralları

- `401 UNAUTHENTICATED`: token süresi dolmuş, iptal edilmiş veya hash eşleşmiyor.
- `401 SESSION_REVOKED`: Cognito kanonik durumu disabled/revoked veya global aile artık geçersiz.
- `403 FORBIDDEN`: kullanıcı normal kullanıcıdır; aktif platform yöneticisi değildir.
- `403 ACCOUNT_NOT_READY`: kullanıcı aktif değildir.
- `503 PROVIDER_UNAVAILABLE`: Cognito durumu erişilemez veya belirsiz; yeni aile fail-closed
  engellenir. İstemci varsa bekleyen güvenlik/bloke işini başarı saymaz.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı fingerprint ile tekrar kullanılmış.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 10. `POST /api/v1/iam/auth/context-selections/{organizationMembershipId}/activate`

### 10.1. Amaç

Tek kullanımlı `contextSelectionToken` ile belirli bir etkin üyelik için kurum kapsamlı platform
oturumu kurar.

### 10.2. İstek

Başlıklar:

- `Authorization: Bearer <contextSelectionToken>`
- `Idempotency-Key: <clientMutationId>`

Yol parametresi:

- `organizationMembershipId` — çağıran kullanıcıya ait etkin üyelik UUID'si

Gövde yoktur.

### 10.3. Başarılı cevap

`200 OK`

```json
{
  "user": {
    "id": "8c728d4e-4e84-4b22-81f9-ec92eb03fa6b",
    "displayName": "Yasir Arslan",
    "status": "ACTIVE"
  },
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z"
  },
  "organizationMembership": {
    "id": "5276942f-ef2f-44ca-9ef6-2af1dd58a0fc",
    "organizationId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
    "organizationName": "Fındıklı Kur'an Kursu",
    "organizationStatus": "ACTIVE",
    "membershipStatus": "ACTIVE",
    "roleCodes": ["ORG_ADMIN"],
    "sessionGeneration": 3
  },
  "session": {
    "scope": "ORGANIZATION",
    "accessToken": "<opaque-access-token>",
    "refreshToken": "<opaque-refresh-token>",
    "tokenType": "Bearer",
    "expiresAt": "2026-07-16T09:32:31Z",
    "refreshExpiresAt": "2026-08-15T09:22:31Z",
    "authenticatedAt": "2026-07-16T09:22:31Z"
  }
}
```

Davranış:

- Sunucu önce güvenli replay kontrolünü yapar; aynı key ve fingerprint için ilk tamamlanmış cevap
  eşdeğeri döner.
- `consumed_at` güncellemesi, Cognito kullanıcı durumunun kanonik management kontrolü, aktif cihaz,
  aktif üyelik, geri alınmamış rol, güncel `sessionGeneration`, `authenticatedAt >
  users.reauthentication_required_after` ve `authenticatedAt >
  organization_memberships.reauthentication_required_after` doğrulaması, aile üretimi ve replay
  referansı yazımı aynı DB transaction'ındadır.
- Kanonik Cognito kontrolü `disabled` veya `revoked` sonucu verirse yeni aile üretilmez; kullanıcının
  mevcut yalnız kendi `actorUserId`'sine ait bütün `refresh_token_families` ve `refresh_tokens`
  satırları `revoked_at` ile idempotent kapatılır, yalnız `users.reauthentication_required_after`
  transaction zamanına yükseltilir ve tek güvenlik audit'i yazılır. Bu dar fail-closed dal,
  yalnız sunucunun doğruladığı canonical provider sonucu ve `CONTEXT_ACTIVATE` operation code'u
  ile açılır; istemci parametresi bu yetkiyi açamaz.
- İkinci başarılı kurum aktivasyonu aynı context-selection token ile yapılamaz; replay kontrolü
  bu değerlendirmeden önce çalışır.
- Platform access tokenı yalnız seçilen `organizationMembershipId` bağlamında geçerlidir.
- Mobil, bu cevaptan sonra Cognito access/refresh tokenlarını güvenli saklamasından siler.
- Cognito durumu erişilemez, belirsiz, disabled veya revoked ise yeni aile hiç kurulmaz.

### 10.4. Hata kuralları

- `401 UNAUTHENTICATED`: token süresi dolmuş, iptal edilmiş veya daha önce tüketilmiş.
- `403 ACCOUNT_NOT_READY`: kullanıcı aktif değil.
- `403 ORGANIZATION_CONTEXT_REQUIRED`: yanlış token türü kullanılmış.
- `401 SESSION_REVOKED`: `authenticatedAt`, güvenilir cihaz, Cognito kanonik durum veya oturum
  eşiği artık geçerli değil.
- `404 RESOURCE_NOT_FOUND`: üyelik bu kullanıcıya ait değil veya görünür kapsamda değil.
- `409 STATE_CONFLICT`: üyelik aktif değil, kurum askıda/arşivli veya rol yok.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı üyelik veya farklı istek özetiyle tekrar
  kullanılmış.
- `503 PROVIDER_UNAVAILABLE`: Cognito durumu erişilemez veya belirsiz; yeni aile fail-closed
  engellenir. İstemci varsa kuyruktaki bloke yazmayı başarı saymaz.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 11. `GET /api/v1/iam/sessions/me`

### 11.1. Amaç

Geçerli platform access tokenının temsil ettiği kullanıcı, kapsam ve cihaz oturum özetini döner.
Mobil başlangıç akışı ve oturum doğrulama ekranı bu ucu kullanır.

### 11.2. İstek

Başlık:

- `Authorization: Bearer <platform-access-token>`

### 11.3. Başarılı cevap

`200 OK`

Kurum kapsamlı oturum için:

```json
{
  "user": {
    "id": "8c728d4e-4e84-4b22-81f9-ec92eb03fa6b",
    "displayName": "Yasir Arslan",
    "status": "ACTIVE"
  },
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z"
  },
  "organizationMembership": {
    "id": "5276942f-ef2f-44ca-9ef6-2af1dd58a0fc",
    "organizationId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
    "organizationName": "Fındıklı Kur'an Kursu",
    "organizationStatus": "ACTIVE",
    "membershipStatus": "ACTIVE",
    "roleCodes": ["ORG_ADMIN"],
    "sessionGeneration": 3
  },
  "session": {
    "scope": "ORGANIZATION",
    "expiresAt": "2026-07-16T09:32:31Z",
    "authenticatedAt": "2026-07-16T09:22:31Z"
  }
}
```

Global platform yöneticisi için:

```json
{
  "user": {
    "id": "8c728d4e-4e84-4b22-81f9-ec92eb03fa6b",
    "displayName": "Yasir Arslan",
    "status": "ACTIVE"
  },
  "platformAdministrator": {
    "status": "ACTIVE"
  },
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z"
  },
  "session": {
    "scope": "GLOBAL_PLATFORM_ADMIN",
    "expiresAt": "2026-07-16T09:32:31Z",
    "authenticatedAt": "2026-07-16T09:22:31Z"
  }
}
```

Davranış:

- Bu uç ham refresh token döndürmez.
- İzin özeti sade tutulur; tüm devredilmiş izin katalogları başka uçların konusudur.
- `organizationMembership` alanı yalnız `ORGANIZATION` kapsamında döner.
- `GLOBAL_PLATFORM_ADMIN` oturumu kurum üyeliği taşımaz; kurum verisine erişim ayrı açık hedef
  kurum bağlamı ve audit zorunluluğu taşıyan modül uçlarıyla yapılır.

## 12. `POST /api/v1/iam/sessions/refresh`

### 12.1. Amaç

Geçerli refresh tokenı tek kullanımlı olarak döndürür; kurum kapsamlı veya global platform
yöneticisi ailesi için yeni platform access/refresh çifti üretir.

### 12.2. İstek

Başlık:

- `Idempotency-Key: <clientMutationId>`

Gövde:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Alan kuralları:

- Refresh token zorunlu metindir.
- İstemci aynı ağ denemesi için aynı `Idempotency-Key` ve aynı refresh tokenı yeniden kullanır.
- İstemci eski refresh tokenı başarılı dönen yeni tokenla değiştirir; iki tokenı birlikte aktif
  saklamaz.
- Rolling refresh, V1'de zorunlu 14/30 günlük periyodik yeniden giriş üretmez; aile logout, iptal,
  güvenlik olayı, reauth eşiği veya güvenli saklama kaybına kadar sürebilir.
- Kurum değiştirme, mevcut aile üstünde sessiz kapsam değiştirme değildir. İstemci yeni kurum için
  yeni `provider-token-exchange` akışı başlatır; bu akış Cognito tarafında interaktif auth round
  trip gerektirir, ancak tarayıcı SSO oturumu hâlâ geçerliyse kullanıcıdan yeniden parola girmesi
  zorunlu olmayabilir.

### 12.3. Başarılı cevap

`200 OK`

Kurum kapsamlı aile için:

```json
{
  "organizationMembership": {
    "id": "5276942f-ef2f-44ca-9ef6-2af1dd58a0fc",
    "organizationId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
    "organizationName": "Fındıklı Kur'an Kursu",
    "organizationStatus": "ACTIVE",
    "membershipStatus": "ACTIVE",
    "roleCodes": ["ORG_ADMIN"],
    "sessionGeneration": 3
  },
  "session": {
    "scope": "ORGANIZATION",
    "accessToken": "<new-opaque-access-token>",
    "refreshToken": "<new-opaque-refresh-token>",
    "tokenType": "Bearer",
    "expiresAt": "2026-07-16T09:42:31Z",
    "refreshExpiresAt": "2026-08-15T09:32:31Z",
    "authenticatedAt": "2026-07-16T09:22:31Z"
  }
}
```

Global platform yöneticisi ailesi için `organizationMembership` alanı dönmez; `session.scope`
`GLOBAL_PLATFORM_ADMIN` olur.

Davranış:

- Sunucu önce güvenli replay kontrolünü yapar; aynı key ve fingerprint için ilk tamamlanmış cevap
  eşdeğeri döner.
- Sunucu, eski refresh token satırını kilitler, `used_at` yazar ve aynı ailede yeni satır üretir.
- Eşzamanlı iki yenilemeden yalnız biri başarılı olur; diğer aynı-key istek güvenli replay alır.
- Farklı `Idempotency-Key` ile daha önce `used_at` yazılmış aynı refresh token yeniden gelirse bu,
  gerçek reuse sayılır; ilgili aile atomik iptal edilir ve `401 SESSION_REVOKED` döner.
- İstemci `organizationMembershipId` göndermez; oturum kapsamı ve varsa üyelik refresh token
  ailesinden türetilir.

### 12.4. Hata kuralları

- `401 UNAUTHENTICATED`: refresh token hash eşleşmiyor veya süresi dolmuş.
- `401 SESSION_REVOKED`: token reuse tespit edildi, aile iptal edildi veya aile/canlılık
  kontrolleri artık geçerli değil.
- `403 ACCOUNT_NOT_READY`: kullanıcı aktif değil.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı refresh token ile kullanılmış.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 13. `POST /api/v1/iam/sessions/logout`

### 13.1. Amaç

Çağıranın mevcut refresh token ailesini sonlandırır. Bu uç yalnız mevcut oturumdan çıkışı
tanımlar; başka cihaz veya başka kurum iptali yapmaz.

### 13.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`

Gövde:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Alan kuralları:

- `refreshToken` zorunludur; logout komutu access tokenla kimliği, refresh tokenla aileyi bağlar.
- `refreshToken`, access tokenın temsil ettiği aynı aile/kullanıcı/üyelik ile eşleşmelidir.
- Global platform yöneticisi ailesinde bu eşleşme `organizationMembershipId = NULL` olan aynı aile
  üstünden yapılır; sentetik üyelik kabul edilmez.
- `sessions/logout` replay escrow kullanmaz; aynı `Idempotency-Key`, aynı access/refresh aile
  bağı ve aynı aktör altında yalnız mevcut iptal sonucunu güvenli biçimde tekrarlar.

### 13.3. Başarılı cevap

`204 No Content`

Davranış:

- Sunucu mevcut aileyi `revoked_at` ile iptal eder.
- Aynı komut güvenli yeniden denemede ikinci yan etki üretmez.
- İstemci başarılı yanıttan sonra access/refresh tokenı ve bu oturuma ait hassas önbelleği yerel
  güvenli saklamasından siler.

### 13.4. Hata kuralları

- `401 UNAUTHENTICATED`: access token geçersiz veya refresh token eşleşmiyor.
- `401 SESSION_REVOKED`: aile zaten iptal edilmiş; istemci bunu çıkışın terminal sonucu gibi ele
  alabilir.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı aile/refresh token ile kullanılmış.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 14. Audit, güvenlik ve idempotency kuralları

- `provider-token-exchange`, `platform-admin-activate`, `context-activation`, `refresh` ve
  `logout` audit olayı üretir.
- Audit olayında en az `actorUserId`, `organizationMembershipId` varsa onun kimliği, cihaz kimliği,
  olay tipi, sonuç kodu ve `requestId` yer alır.
- Audit olayında tokenın ham değeri, Cognito access tokenı, `authorization_code`, parola veya tam
  kişisel hata içeriği yer almaz.
- `contextSelectionToken` ve refresh tokenlar en az 256-bit rastgele opaque değerdir; DB'de yalnız
  pepper'lı HMAC özeti tutulur.
- Kurum kapsamlı bütün oturum denetimleri `session_generation`, üyelik durumu, rol durumu,
  güvenilir cihaz ve `revoked_at` kontrolleriyle birlikte yapılır.
- Global platform yöneticisi ailesi de aynı güvenlik kurallarına uyar; ek olarak aktif
  `platform_administrators` kontrolü ve `organization_membership_id = NULL` değişmezi zorunludur.
- Güvenlik açısından anlamlı başarısız olaylar da audit edilir: başarısız Cognito token değişimi,
  platform admin aktivasyon reddi, kurum aktivasyon reddi, rate limit, refresh reuse, provider
  belirsizliği, disabled/revoked kullanıcı ve reauth eşiği ihlali.

## 15. Mobil istemci yükümlülükleri

- Mobil, `provider-token-exchange` cevabındaki `contextSelectionToken` ile yalnız üyelik listesi,
  global admin aktivasyonu ve kurum aktivasyonu çağrıları yapar; başka API çağrısı yapmaz.
- Başarılı `activate` sonrası Cognito access/refresh tokenlarını siler; yalnız platform
  access/refresh tokenını saklar.
- Başarılı `platform-admin/activate` sonrası da Cognito access/refresh tokenlarını siler; yalnız
  global platform token ailesini saklar.
- `401 SESSION_REVOKED` kurum kapsamlı oturumda alınırsa mevcut kuruma ait kuyruğu `BLOCKED`
  durumuna alır; başarı sayıp kuyruğu silmez.
- `401 SESSION_REVOKED` global platform yöneticisi oturumunda alınırsa istemci kurum kuyruğu
  varsaymaz; global admin oturumunu kapatır, hassas önbelleği temizler ve kullanıcıyı güvenli
  yeniden giriş ekranına yönlendirir.
- `403 ACCOUNT_NOT_READY`, `409 STATE_CONFLICT` veya `503 PROVIDER_UNAVAILABLE` durumlarını sessiz
  yeniden deneme ile çözmeye çalışmaz; kullanıcıya güvenli yönlendirme gösterir.
- `logout` sonrası başka kullanıcı/kurum girişinde eski önbellek ve eski token namespace'i
  görünmez olmalıdır.
- Global platform yöneticisi kurum değiştirirken mevcut aile üzerinde kapsam değiştirme yapmaz;
  yeni aile için yeniden auth round trip başlatır.

## 16. Kabul ölçütleri

- Cognito access tokenı yalnız ilk platform token değişiminde kullanılır; iş API'leri bunu kabul
  etmez.
- `contextSelectionToken` kısa ömürlü, tek kullanımlı ve refresh edilemez olarak tanımlanmıştır.
- Kurum üyeliği olmayan aktif platform yöneticisi için global oturum akışı tanımlanmıştır.
- Kurum listesi ve kurum aktivasyonu ayrı uçlardadır; aktivasyon ikinci aile üretmez.
- Platform access/refresh oturumu kurum veya global admin kapsamlıdır; üyelik, aktif admin ve
  `sessionGeneration` kontrolleri açıkça sözleşmededir.
- Refresh rotasyonu tek kullanımlıdır; reuse tespiti `SESSION_REVOKED` ile fail-closed çalışır.
- `provider-token-exchange`, aktivasyon ve refresh için kayıp başarı cevabı aynı `Idempotency-Key`
  ile güvenli replay alır.
- Access token ömrü tam 10 dakika olarak bağlanmıştır.
- Kendi oturumundan çıkış akışı tanımlıdır; başka cihaz/başka kullanıcı iptali bu belgeye
  alınmamıştır.
- Audit, hassas veri minimizasyonu, idempotency, rate limit ve mobil kuyruk etkisi belirtilmiştir.

## 17. Test ve doğrulama notları

Bu görev belge/sözleşme görevidir. Uygulama testleri `IAM-004`, `IAM-005`, `IAM-006` ve
`IAM-009` sahipliğindedir. Bu sözleşme en az aşağıdaki senaryoların sonraki görevlerde
kanıtlanmasını zorunlu kılar:

1. Geçerli Cognito tokenıyla identity eşleşmesi olmayan kullanıcı platform oturumu alamaz.
2. Kurum üyeliği olmayan aktif platform yöneticisi global oturum alır.
3. Normal kullanıcı `GLOBAL_PLATFORM_ADMIN` oturumu alamaz.
4. `provider-token-exchange` `AUTHENTICATION` read-only transaction'ı ile `IAM_AUTH`
   mutation transaction'ı aynı transaction içinde birleşmez; scope karışımı reddedilir.
5. Normal kullanıcının kurum öncesi `IAM_AUTH` idempotency kapsamı başka kullanıcıyla çakışmaz.
6. `provider-token-exchange` kayıp cevabı aynı context tokenı güvenli replay eder.
7. Replay escrow TTL ve actor/operation/device erişim izolasyonu korunur.
8. Replay sonucu süresi dolunca fail-closed davranır; ikinci iş üretilmez.
9. Süresi dolmamış `READY` escrow satırı purge edilemez; expiry purge ancak terminal durum +
   gizli alan temizliğiyle aynı update'te yapılır.
10. Kanonik `disabled`/`revoked` sonucu mevcut bütün actor ailelerini kapatır, başka
    kullanıcının ailesine dokunmaz ve global reauth bariyerini yükseltir.
11. Sahte `organizationMembershipId` veya sahte hedef kurum zinciri aile üretmez.
12. Tek `contextSelectionToken` ve aynı key ile `activate` başarı cevabı kaybı ikinci aile
   üretmeden kurtarılır.
13. `refresh` başarı cevabı kaybı aynı key ile aileyi yanlışlıkla iptal etmez.
14. Farklı key ile daha önce kullanılmış refresh token gerçek reuse sayılır ve aileyi iptal eder.
15. Provider belirsiz veya erişilemez iken yeni aile üretilemez.
16. `authenticatedAt`, hem `users.reauthentication_required_after` hem de kurum ailesinde ayrıca
   `organization_memberships.reauthentication_required_after` için eskiyse reddedilir.
17. Askıya alınmış üyelik, geri alınmış rol veya eski `sessionGeneration` ile `refresh`
   başarısız olur.
18. `logout` sonrası aynı aile access/refresh tokenları yeniden kullanılamaz.
19. Güvenlik açısından anlamlı başarısız giriş, aktivasyon, refresh ve reuse olayları audit edilir.
20. Access token ömrü tam 10 dakikadır.
21. `SESSION_REVOKED` alan mobil istemci bekleyen yazmayı başarılı kabul etmez.

### 17.1. Zorunlu SQL/RLS kabul kapıları

`IAM-009` en az aşağıdaki SQL/RLS senaryolarını otomasyonla kanıtlamadan bu sözleşme uygulanmış
sayılmaz:

1. `CONTEXT_ACTIVATE` yalnız kendi kullanıcı/cihaz/üyelik zincirini açar; başka kurum üyeliği
   veya sahte `organization_id` ile aile üretimi reddedilir.
2. `PLATFORM_ADMIN_ACTIVATE` yalnız aktif `platform_administrators` satırı olan kullanıcıyı açar;
   normal kullanıcı aynı `IAM_AUTH` scope'unda global aile üretemez.
3. `PROVIDER_TOKEN_EXCHANGE` `AUTHENTICATION` ve `IAM_AUTH` scope'larını tek transaction'da
   birleştirmeye çalışan veya `IAM_AUTH`tan identity tarayan erişim yolu RLS/policy ile reddedilir.
4. `AUTHENTICATION` aşaması yalnız aynı `issuer + subject` identity'sini açar; başka aktörün
   identity sonucunu veya mutation tablosunu okuyamaz.
5. `SESSION_REFRESH` replay'i aynı aktör/cihaz/family zincirinde güvenli çoklu replay yapar;
   farklı cihaz veya farklı kurum zinciri okuyamaz.
6. Aynı kullanıcı için aynı `clientMutationId` farklı `operationType` ile kullanıldığında
   `idempotency_keys_iam_auth_scope_uq` + fingerprint doğrulaması `409 IDEMPOTENCY_KEY_REUSED`
   üretir.
7. Canonical `disabled` sonucu yalnız aktörün bütün ailelerini kapatır; başka kullanıcının
   family/token satırlarına dokunamaz.
8. Escrow expiry veya decrypt başarısızlığında bağlı context token ya da family/token sonucu
   idempotent biçimde iptal edilir; purge aynı update'te terminal durum + `deleted_at` +
   `ciphertext`/`aead_key_reference`/`aead_nonce`/`aad_context` NULL geçişiyle olur.
9. Süresi dolmamış `READY` escrow satırını purge etmeye çalışan update reddedilir.
10. `IAM_AUTH` audit insert politikası yalnız allow-listli auth olaylarını ve doğru aktör/cihaz/
   üyelik/kurum/family bağlarını kabul eder; çapraz kurum/çapraz cihaz satırı reddeder.

## 18. Kapsam dışında bırakılanlar

- Cognito Hosted UI ekran metinleri, redirect URI listesi ve mobil SDK entegrasyon ayrıntıları
- Oran sınırlama eşik sayıları ve brute-force alarm operasyonu
- Başka kullanıcının oturumlarını iptal etme, global logout ve cihaz yönetimi listesi
- Parola sıfırlama, öğretmen hesabı provisioning'i ve secret teslim akışı
- Veli/öğrenci giriş yüzeyleri
