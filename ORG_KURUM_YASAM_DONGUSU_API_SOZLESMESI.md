# ORG Kurum Yaşam Döngüsü API Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | ORG-001 — Kurum yaşam döngüsü API sözleşmesini yaz |
| Belge sürümü | 1.7 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlı sözleşmeler | `VERI_MODELI.md`, `API_GENEL_KURALLARI.md`, `YETKI_MATRISI.md`, `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` |
| Son güncelleme | 17 Temmuz 2026 |

---

## Revizyon notu (v1.1 → v1.2)

1. Transaction scope modeli düzeltildi: her DB transaction tam olarak tek scope taşır; §4.2'deki
   "iki scope kuralı geçerli değildir" istisnası kaldırıldı (§2, §4).
2. Tam işlem×scope×tablo matrisi eklendi: platform admin hedef kurumlu işlemleri
   `ORGANIZATION` scope + doğrulanmış platform-admin destek bayrağı + allow-listli
   operation code ile çalışır (§4.1).
3. `SUSPEND`/`ARCHIVE` için sabit kilit sırası tanımlandı: organizations →
   organization_memberships → refresh_token_families → refresh_tokens. Organization
   satırı kilitlendikten sonra status/rowVersion yeniden okunur (§4.2).
4. `CONTEXT_ACTIVATE` ve `SESSION_REFRESH` aynı organization yaşam döngüsü bariyeriyle
   serileşir; kilit sonrası kurum `ACTIVE` değilse yeni family/token üretilmez (§4.3).
5. `If-Match-Row-Version` kuralı düzeltildi: `CREATE` hariç güncelleme ve durum komutları
   taşır; oluşturma taşımaz (§5.1, §7.2).
6. Bütün replay akışlarında sıralama açık yazıldı: authentication → yetki → fingerprint/
   idempotency → state/rowVersion → mutation. Yetkisi geri alınmış aktör eski sonucu
   replay edemez (§5.5).
7. `ORGANIZATION` scope `LIST` tek ve çelişkisiz karara bağlandı: status/search/sort/order/
   limit/cursor parametrelerinin tamamı `422` ile reddedilir; bu scope hiçbir cursor
   üretmez (§8.3, §8.4).
8. Kabul senaryolarına 9 yeni senaryo eklendi: scope karışımı reddi, 4 yarış senaryosu,
   yetkisi geri alınmış replay ve ORGANIZATION limit/cursor reddi (§14).

## Revizyon notu (v1.2 → v1.3)

1. SET LOCAL değişken adları IAM sözleşmesiyle hizalandı: `app.iam_platform_admin_support_access`,
   `app.iam_operation_code`, `app.iam_operation_scope` (§2.2, §4.1, §5.5).
2. Platform admin hedef kurumlu işlemlerde aynı transaction içinde aktif
   `platform_administrators` kaydı doğrulaması zorunlu kılındı; token claim'iyle
   yetkilendirme kaldırıldı. Doğrulama `SELECT`'i matrise eklendi (§4.1).
3. İdempotency kuralı tek karara bağlandı: `CREATE` dâhil bütün yazma uçları
   `Idempotency-Key` taşır; yalnızca `CREATE` `If-Match-Row-Version` taşımaz (§2.1, §5.1,
   §5.2).
4. Scope/idempotency senaryosu düzeltildi: aynı `clientMutationId` farklı ve geçerli
   scope'larda partial unique index kuralları kapsamında coexist edebilir; yanlış
   operation/scope birleşimi idempotency kontrolünden önce fail-closed reddedilir (§14.5).
5. SUSPEND–ACTIVATE yarışı düzeltildi: `ACTIVATE` `rowVersion` artırır; eski `rowVersion`
   taşıyan gecikmiş `SUSPEND` `VERSION_CONFLICT` alır; güncel `rowVersion` ile `ACTIVE`
   kuruma gönderilen yeni `SUSPEND` geçerlidir (§2.4, §12.3, §14.6).
6. Üç yeni kabul testi eklendi: yetkisi geri alınmış platform admin stale token reddi,
   yanlış support flag/operation code reddi, ACTIVATE sonrası eski rowVersion SUSPEND
   reddi (§14.7).

## Revizyon notu (v1.3 → v1.4)

1. `§4.3`'teki SUSPEND–ACTIVATE yarışı `VERSION_CONFLICT` ile hizalandı; kalan
   `STATE_CONFLICT` ifadesi kaldırıldı (§4.3, §2.4, §12.3, §14.6).
2. Dar `platform_administrators` `ORGANIZATION` scope `SELECT` RLS policy'si tanımlandı:
   `app.iam_operation_scope='ORGANIZATION'` + `app.iam_actor_user_id = user_id` +
   `revoked_at IS NULL` + allow-listli operation code; bayrağa bağımlı değil; başka admin
   satırı/BYPASSRLS/yazma yok (§4.1a, ADR-004).
3. Üç RLS doğrulama kabul testi eklendi (§14.8).

## Revizyon notu (v1.4 → v1.5)

1. İşlem sıralaması hizalandı: operation code route'tan transaction öncesinde çözülür;
   yalnızca `app.iam_platform_admin_support_access` bayrağı admin `SELECT` doğrulamasından
   sonra kurulur (§2.2, §4.1, §5.5).
2. Dört sıralama kabul testi eklendi (§14.9).

## Revizyon notu (v1.5 → v1.6)

1. `CREATE` ve platform-admin `LIST` için `GLOBAL` scope `platform_administrators` `SELECT`
   RLS policy'si tanımlandı; allow-list: `ORG_CREATE`, `ORG_LIST`. `GLOBAL` yolunda
   `app.iam_platform_admin_support_access` kullanılmaz (§2.2, §4.1, §4.1b, ADR-004).
2. `§5.5` replay akışı GLOBAL CREATE, hedef-kurumlu platform-admin yazmaları ve kurum
   aktörü `PATCH` için ayrı ayrı tanımlandı; salt-okunur `ORG_DETAIL` yazma replay
   allow-list'inden çıkarıldı (§5.5).
3. `CREATE`, `PATCH`, `SUSPEND`, `ACTIVATE` ve `ARCHIVE` hata sözleşmelerine
   `500 INTERNAL_ERROR` eklendi; audit başarısızlığında rollback + fail-closed davranışı
   yazıldı (§7.4, §10.5, §11.4, §12.4, §13.4).
4. Audit fail-closed kabul testleri eklendi: platform admin ve kurum aktörü PATCH yolları
   dâhil bütün yazma uçları kapsandı (§14.10).
5. GLOBAL admin doğrulama kabul testleri eklendi (§14.11).

## Revizyon notu (v1.6 → v1.7)

1. Runtime rolü sahipliği netleştirildi: ORG işlemi tek DB transaction ve tek runtime
   rolüyle çalışır; somut rol adı `ORG-003` kapsamındadır. `iam_runtime`'ın ORG
   tablolarını işlettiği iddiası kaldırıldı (§4.1a, §4.1b, §4.1c).
2. `ORG-003` kabul ölçütüne tüm ORG operation code'ları için gerçek PostgreSQL
   `GRANT`/RLS testleri eklenecektir (§4.1c).
3. ACTIVATE audit başarısızlığı kabul testi eklendi (§14.10, senaryo 45).
4. ORG_LIST GLOBAL doğrulama kabul testi eklendi (§14.11, senaryo 49).

---

## 1. Amaç ve kapsam

Bu belge, platform yöneticisinin kurum oluşturması, kurum listesini görüntülemesi ve kurumun
yaşam döngüsü durumunu (aktif, askıda, arşivlenmiş) yönetmesi için gerekli API sözleşmesini
tanımlar. Amaç; kurum oluşturma, listeleme, detay görüntüleme, kimlik alanı güncelleme ve
durum değişikliği uçlarını sonraki ORG/backend/mobil görevleri için çelişkisiz hâle getirmektir.

Bu sözleşme:

- `VERI_MODELI.md` §5.1 `organizations` tablosundaki çekirdek kurum kaydını esas alır.
- Kurum oluşturma yalnızca platform yöneticisine aittir (`URUN_VE_UYGULAMA_PLANI.md` §4.2).
- Durum değişiklikleri yalnızca platform yöneticisi tarafından yapılır; her durum geçişi
  atomik oturum iptali yan etkisi taşır.
- `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` ve `VERI_MODELI.md` §4.5/§4.11'deki
  `session_generation`, `refresh_token_families` ve `refresh_tokens` modeliyle uyumludur.
- Her DB transaction tam olarak **tek** scope taşır; aynı transaction içinde iki scope
  birleştirilemez (bkz. §4.1).

Bu sözleşme **değildir**:

- Logo yükleme/indirme, marka renk paleti (`organization_brand_colors`) ve etkin modül
  (`organization_modules`) yönetimi — bunlar `ORG-002` kapsamındadır.
- Kurum yöneticisi atama — `STAFF-*` veya üyelik görevleri kapsamındadır.
- Kurum yöneticisinin kendi kendine kaydolması veya davet koduyla kurum oluşturma — bunlar
  sonraki faz işleridir (Dalga 8).
- Eğitim dönemi, sınıf, öğrenci veya yoklama uçları — bunlar kendi modül sözleşmelerinin
  konusudur.

## 2. Bağlayıcı karar özeti

### 2.1. Genel kurallar

- V1'de yalnızca platform yöneticisi kurum oluşturabilir (`URUN_VE_UYGULAMA_PLANI.md` §4.2).
- Kurum oluşturma `GLOBAL` scope + `GLOBAL_PLATFORM_ADMIN` kapsamlı token ile yapılır.
- Oluşturma anında `status` her zaman `ACTIVE` başlar; istemci durumu seçemez.
- `defaultTimezone` gönderilmezse `Europe/Istanbul` kullanılır.
- Kurum normal arayüzden fiziksel silinmez; arayüzde silme yolu (`DELETE`) yoktur.
- Bütün yazma uçları `Idempotency-Key` başlığı taşır.
- Yalnızca `CREATE` `If-Match-Row-Version` başlığı taşımaz; diğer bütün güncelleme ve durum
  komutları `If-Match-Row-Version` taşır.
- Bütün yazma uçları için request fingerprint zorunludur (bkz. §5.4).
- Platform yöneticisinin kurum verisi erişimi (görüntüleme ve değiştirme) zorunlu denetim
  kaydı üretir. Denetim kaydı yazılamazsa cevap fail-closed reddedilir.

### 2.2. Transaction scope modeli (bağlayıcı)

Her DB transaction tam olarak **tek** scope taşır. Platform yöneticisinin hedef kurumlu
işlemleri (DETAIL, PATCH, SUSPEND, ACTIVATE, ARCHIVE) `ORGANIZATION` scope'unda çalışır.
Bağlayıcı işlem sırası şöyledir:

1. Sunucu, doğrulanmış token ve eşleşmiş route üzerinden `actorUserId`, hedef
   `organizationId` ve `operationCode`'u çözer.
2. `operationCode`, `ORG_DETAIL`, `ORG_UPDATE_IDENTITY`, `ORG_SUSPEND`, `ORG_ACTIVATE`,
   `ORG_ARCHIVE` allow-list'inde değilse transaction açılmadan veya tablo erişiminden
   önce fail-closed `403 FORBIDDEN` reddedilir.
3. Transaction içinde `app.iam_operation_scope = 'ORGANIZATION'`,
   `app.iam_actor_user_id`, `app.organization_id` ve `app.iam_operation_code` server-set
   kurulur.
4. Bu bağlamla dar `platform_administrators` actor-only `SELECT` yapılır (bkz. §4.1a).
5. Aktif admin satırı bulunursa ancak bundan sonra `app.iam_platform_admin_support_access
   = true` server-set bayrağı kurulur. Bayrak istemci parametresiyle kurulamaz.
6. Hedef kurum erişimi ve zorunlu `PLATFORM_ADMIN_ORG_ACCESS` denetim kaydı yürütülür.

`CREATE` ve platform-admin `LIST` işlemleri `GLOBAL` scope'unda çalışır. Bağlayıcı sıra:

1. Sunucu, doğrulanmış token ve eşleşmiş route üzerinden `actorUserId` ve `operationCode`'u
   çözer.
2. `operationCode`, `ORG_CREATE` veya `ORG_LIST` allow-list'inde değilse transaction
   açılmadan fail-closed `403 FORBIDDEN` reddedilir.
3. Transaction içinde `app.iam_operation_scope = 'GLOBAL'`, `app.iam_actor_user_id` ve
   `app.iam_operation_code` server-set kurulur.
4. Bu bağlamla dar `platform_administrators` actor-only `SELECT` yapılır (bkz. §4.1b).
5. Aktif admin satırı bulunursa `CREATE` veya `LIST` işlemi yürütülür.
   `app.iam_platform_admin_support_access` bayrağı `GLOBAL` scope'ta kullanılmaz; bu
   bayrak yalnız hedef-kurumlu `ORGANIZATION` destek erişimine aittir.
6. `LIST` için her görüntülenen kurumda audit yazılır; `CREATE` audit'i aynı
   transaction'dadır.

| İşlem | Scope |
|---|---|
| `CREATE` | `GLOBAL` |
| `LIST` (platform admin) | `GLOBAL` |
| `LIST` (org aktörü) | `ORGANIZATION` |
| `DETAIL` (platform admin hedefli) | `ORGANIZATION` + platform-admin bayrağı |
| `DETAIL` (org aktörü) | `ORGANIZATION` |
| `PATCH` (platform admin hedefli) | `ORGANIZATION` + platform-admin bayrağı |
| `PATCH` (org aktörü) | `ORGANIZATION` |
| `SUSPEND` | `ORGANIZATION` + platform-admin bayrağı |
| `ACTIVATE` | `ORGANIZATION` + platform-admin bayrağı |
| `ARCHIVE` | `ORGANIZATION` + platform-admin bayrağı |

Platform admin'in hedef kurumlu işlemlerinde idempotency kaydı da aynı `ORGANIZATION`
transaction'ındadır; farklı scope'lara bölünmez.

### 2.3. SUSPEND/ARCHIVE kilit sırası

`SUSPEND` ve `ARCHIVE` transaction'ları aşağıdaki sabit sırada kilit alır:

1. `organizations` — `SELECT ... FOR UPDATE`
2. Kilit sonrası `status` ve `rowVersion` yeniden okunur; durum ön koşulu tekrar doğrulanır.
3. `organization_memberships` — `SELECT ... FOR UPDATE` (hedef kurumun üyelikleri)
4. `refresh_token_families` — `SELECT ... FOR UPDATE` (adım 3'teki üyeliklere bağlı aileler)
5. `refresh_tokens` — `SELECT ... FOR UPDATE` (adım 4'teki ailelere bağlı token'lar)
6. Mutasyonlar (UPDATE status, session_generation, revoked_at)
7. Audit + idempotency yazımı
8. COMMIT

Bu kilit sırası deadlock'u önler ve `CONTEXT_ACTIVATE`/`SESSION_REFRESH` ile
serileştirmeyi sağlar (bkz. §4.3).

### 2.4. Oturum yaşam döngüsü serileştirmesi

`CONTEXT_ACTIVATE` (IAM-001) ve `SESSION_REFRESH` (IAM-001), kurum yaşam döngüsüyle aynı
kilit bariyerini kullanır: her ikisi de ilgili `organizations` satırını `FOR UPDATE` ile
kilitler ve kilit sonrası kurum `status`'unun `ACTIVE` olduğunu doğrular. `ACTIVE` değilse
yeni family/token üretilmez.

- `SUSPEND`/`ARCHIVE` taramasından sonra yarışarak yeni family oluşturulması, bu kilit
  sayesinde engellenir: `SUSPEND` organizasyonu kilitledikten sonra `CONTEXT_ACTIVATE`
  bekler → `SUSPEND` commit → `CONTEXT_ACTIVATE` kilidi alır → status'u `SUSPENDED` görür
  → reddeder.
- `ACTIVATE` sonrası geç kalmış bir `CONTEXT_ACTIVATE` transaction'ı (hâlâ `SUSPENDED`
  varsayımıyla çalışan) kilit sonrası `ACTIVE` görür ve başarılı olur; bu istenen
  davranıştır.
- `ACTIVATE` `rowVersion` artırır. `ACTIVATE` commit'inden sonra eski `rowVersion`
  taşıyan gecikmiş bir `SUSPEND` transaction'ı `VERSION_CONFLICT` alır. Güncel
  `rowVersion` ile `ACTIVE` kuruma gönderilen yeni `SUSPEND` geçerlidir.

### 2.5. Durum değişikliği oturum yan etkileri

- `SUSPEND` ve `ARCHIVE` işlemleri **tek transaction'da** atomik tamamlar:
  1. `organizations.status` güncellemesi,
  2. Hedef kurumun bütün `organization_memberships` satırlarında `session_generation`
     artışı,
  3. Bu üyeliklere bağlı `refresh_token_families.revoked_at` toplu iptali,
  4. Aynı ailelere bağlı `refresh_tokens.revoked_at` toplu iptali,
  5. Denetim kaydı,
  6. İdempotency sonucu.
- `ACTIVATE` eski oturumları geri canlandırmaz. `session_generation` artırılmaz; askı
  sırasında artırılmış değerler korunur. Kullanıcı yeni oturum kurmalıdır.
- `session_generation` bariyeri: kurum kapsamlı her API isteğinde token'ın
  `issued_at_session_generation` değeri üyeliğin güncel `session_generation` değeriyle
  eşleşmelidir.

### 2.6. SUSPENDED/ARCHIVED erişim modeli

- `SUSPENDED` ve `ARCHIVED` kurumlara kurum kapsamlı oturumlarla erişim reddedilir.
- `GLOBAL_PLATFORM_ADMIN` kapsamlı token: `DETAIL` ve `LIST` her durumda çalışır.
- `PATCH` için durum matrisi: ACTIVE → izinli, SUSPENDED → org aktörü red/platform admin
  izinli, ARCHIVED → terminal (herkes red).
- Kurum üyelerine salt-okunur erişim açık ürün kararı gerektirir; bu sözleşme tanımlamaz.

### 2.7. PATCH erişim matrisi

| Organizasyon durumu | Platform admin | Kurum yöneticisi / yetkili hoca |
|---|---|---|
| `ACTIVE` | İzinli | İzinli (`ORG_ADMIN` veya `BRAND_MANAGE`) |
| `SUSPENDED` | İzinli | **Red** — `403 FORBIDDEN` |
| `ARCHIVED` | **Red** — `409 STATE_CONFLICT` | **Red** — `403 FORBIDDEN` |

### 2.8. Diğer kararlar

- `status` alanı yalnızca komut uçlarıyla değişir; `PATCH` gövdesinde reddedilir.
- `primaryColor` güncellemesi `ORG-002` kapsamındadır.

## 3. Aktörler ve ön koşullar

### 3.1. Mobil istemci ön koşulları

- İstemci `IAM-001` sözleşmesine göre geçerli bir platform oturumu kurmuş olmalıdır.
- Platform yöneticisi işlemleri `GLOBAL_PLATFORM_ADMIN` kapsamlı token gerektirir.
- Kurum yöneticisi ve hoca işlemleri `ORGANIZATION` kapsamlı token gerektirir.
- `CREATE` hariç yazma isteklerinde `If-Match-Row-Version` başlığı zorunludur.

### 3.2. Backend ön koşulları

- `organizations`, `organization_memberships`, `organization_membership_roles`,
  `organization_membership_permissions`, `refresh_token_families`, `refresh_tokens`,
  `idempotency_keys`, `audit_logs` tabloları `VERI_MODELI.md` ile uyumlu mevcuttur.

### 3.3. Oturum kapsamları ve organizasyon erişimi

| Organizasyon durumu | ORGANIZATION token erişimi | GLOBAL_PLATFORM_ADMIN erişimi |
|---|---|---|
| `ACTIVE` | LIST, DETAIL, PATCH (yetkiye bağlı) | LIST, DETAIL, PATCH, SUSPEND, ARCHIVE |
| `SUSPENDED` | **Red** — `403 FORBIDDEN` | LIST, DETAIL, PATCH, ACTIVATE, ARCHIVE |
| `ARCHIVED` | **Red** — `403 FORBIDDEN` | LIST, DETAIL |

## 4. Kaynaklar ve uçlar

Bu sözleşme yedi ORG uç yüzeyini bağlar:

1. `POST /api/v1/organizations`
2. `GET /api/v1/organizations`
3. `GET /api/v1/organizations/{organizationId}`
4. `PATCH /api/v1/organizations/{organizationId}`
5. `POST /api/v1/organizations/{organizationId}/suspend`
6. `POST /api/v1/organizations/{organizationId}/activate`
7. `POST /api/v1/organizations/{organizationId}/archive`

### 4.1. İşlem × scope × tablo matrisi (bağlayıcı)

Her DB transaction tam olarak tek scope kullanır.

| İşlem | Scope | `app.*` bağlamı | Tablolar | SQL işlemleri |
|---|---|---|---|---|
| `CREATE` | `GLOBAL` | `app.iam_operation_code = 'ORG_CREATE'` | `organizations`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `INSERT` organization; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `LIST` (platform admin) | `GLOBAL` | `app.iam_operation_code = 'ORG_LIST'` | `organizations` (salt okunur), `platform_administrators` (salt okunur), `audit_logs` | `SELECT` platform_administrators; `SELECT` organizations; `INSERT` audit (her görüntülenen kurum için) |
| `LIST` (org aktörü) | `ORGANIZATION` | Token'dan çözülen `app.organization_id` | `organizations` (salt okunur) | `SELECT` yalnızca kendi kurumu |
| `DETAIL` (platform admin hedefli) | `ORGANIZATION` | `app.iam_platform_admin_support_access = true`, `app.iam_operation_code = 'ORG_DETAIL'`, `app.organization_id = :targetOrgId` | `organizations` (salt okunur), `platform_administrators` (salt okunur), `audit_logs` | `SELECT` platform_administrators; `SELECT` organization; `INSERT` audit |
| `DETAIL` (org aktörü) | `ORGANIZATION` | Token'dan çözülen `app.organization_id` | `organizations` (salt okunur) | `SELECT` yalnızca kendi kurumu |
| `PATCH` (platform admin hedefli) | `ORGANIZATION` | `app.iam_platform_admin_support_access = true`, `app.iam_operation_code = 'ORG_UPDATE_IDENTITY'`, `app.organization_id = :targetOrgId` | `organizations`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `SELECT ... FOR UPDATE` organization; `UPDATE`; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `PATCH` (org aktörü) | `ORGANIZATION` | Token'dan çözülen `app.organization_id` | `organizations`, `audit_logs`, `idempotency_keys` | `SELECT ... FOR UPDATE` organization; `UPDATE`; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `SUSPEND` | `ORGANIZATION` | `app.iam_platform_admin_support_access = true`, `app.iam_operation_code = 'ORG_SUSPEND'`, `app.organization_id = :targetOrgId` | `organizations`, `platform_administrators` (salt okunur), `organization_memberships`, `refresh_token_families`, `refresh_tokens`, `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; sabit kilit sırası (§4.2); `UPDATE` status + session_generation + revoked_at; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `ACTIVATE` | `ORGANIZATION` | `app.iam_platform_admin_support_access = true`, `app.iam_operation_code = 'ORG_ACTIVATE'`, `app.organization_id = :targetOrgId` | `organizations`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `SELECT ... FOR UPDATE` organization; `UPDATE` status + rowVersion; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `ARCHIVE` | `ORGANIZATION` | `app.iam_platform_admin_support_access = true`, `app.iam_operation_code = 'ORG_ARCHIVE'`, `app.organization_id = :targetOrgId` | `organizations`, `platform_administrators` (salt okunur), `organization_memberships`, `refresh_token_families`, `refresh_tokens`, `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; sabit kilit sırası (§4.2); `UPDATE` status + session_generation + revoked_at; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |

Platform admin'in hedef kurumlu işlemleri için bağlayıcı sıra şöyledir:

1. Sunucu doğrulanmış token ve eşleşmiş route'tan `actorUserId`, hedef `organizationId`
   ve `operationCode`'u çözer.
2. `operationCode` allow-list'te (`ORG_DETAIL`, `ORG_UPDATE_IDENTITY`, `ORG_SUSPEND`,
   `ORG_ACTIVATE`, `ORG_ARCHIVE`) değilse transaction açılmadan fail-closed reddedilir.
3. Transaction içinde `app.iam_operation_scope = 'ORGANIZATION'`,
   `app.iam_actor_user_id`, `app.organization_id` ve `app.iam_operation_code`
   server-set kurulur.
4. Bu bağlamla dar `platform_administrators` `SELECT`'i yapılır:
   `SELECT ... WHERE user_id = :actorUserId AND revoked_at IS NULL` (§4.1a RLS
   policy'si altında).
5. `SELECT` başarıyla satır döndürürse `app.iam_platform_admin_support_access = true`
   bayrağı kurulur. Doğrulama başarısız olursa (kayıt yok veya geri alınmış) bayrak
   kurulmaz; transaction fail-closed `403 FORBIDDEN` ile reddedilir.
6. Hedef kurum erişimi ve zorunlu `PLATFORM_ADMIN_ORG_ACCESS` audit'i yürütülür.

`app.iam_operation_code` adım 1'de route'tan çözülür ve adım 2'de allow-list'e karşı
doğrulanır; yalnızca `app.iam_platform_admin_support_access` bayrağı adım 5'te
admin `SELECT` doğrulamasından sonra kurulur.

Bu matrisin dışındaki tablo erişimi sözleşme ihlalidir.

Audit action eşlemesi de kapalıdır:

| İşlem | Üretilen audit action |
|---|---|
| `CREATE` | `ORG_CREATED` |
| Platform admin `LIST` / `DETAIL` | Görüntülenen her hedef kurum için `PLATFORM_ADMIN_ORG_ACCESS` |
| `PATCH` | `ORG_SETTING_CHANGED`; platform admin hedefli işlemde ayrıca `PLATFORM_ADMIN_ORG_ACCESS` |
| `SUSPEND` / `ACTIVATE` / `ARCHIVE` | `ORG_STATUS_CHANGED`; ayrıca hedef kurum erişimi için `PLATFORM_ADMIN_ORG_ACCESS` |

`ORG_CREATED` ve `ORG_STATUS_CHANGED` geri alınabilir değildir. `ORG_SETTING_CHANGED` yalnız
`DENETIM_VE_GERI_ALMA_ILKELERI.md` §6'daki kontrollü ayar geri alma akışına tabidir. Action
kodları birbirinin yerine kullanılamaz; özellikle oluşturma veya yaşam döngüsü durum değişimi
`ORG_SETTING_CHANGED` olarak yazılamaz.

### 4.1a. `platform_administrators` ORGANIZATION scope SELECT RLS (bağlayıcı)

Platform yöneticisinin hedef kurumlu işlemlerinde aktif `platform_administrators` kaydının
doğrulanması, aşağıdaki dar `FORCE RLS` policy'si ile yapılır. Bu policy **ORG-001 ve
ORG-002 arasında tek kanonik karar**dır; `ORG-002` marka uçları aynı policy'yi kullanır ve
ayrı bir RLS policy tanımlamaz. Bu policy'nin ait olduğu runtime rolü ve migration
grant'leri `ORG-003` kapsamında kesinleştirilecektir; ORG işlemi tek DB transaction ve tek
runtime rolüyle çalışır. `iam_runtime` rolü ADR-003 uyarınca yalnız IAM ve dar güvenlik
yüzeyiyle sınırlıdır; ORG yaşam döngüsü transaction'ını işletmez.

```
USING (
  app.iam_operation_scope = 'ORGANIZATION'
  AND app.iam_actor_user_id = platform_administrators.user_id
  AND platform_administrators.revoked_at IS NULL
  AND app.iam_operation_code IN (
    'ORG_DETAIL',
    'ORG_UPDATE_IDENTITY',
    'ORG_SUSPEND',
    'ORG_ACTIVATE',
    'ORG_ARCHIVE',
    'ORG_VIEW_BRAND',
    'ORG_UPDATE_BRAND',
    'ORG_VIEW_BRAND_COLORS',
    'ORG_UPDATE_BRAND_COLORS',
    'ORG_VIEW_MODULES',
    'ORG_UPDATE_MODULES',
    'ORG_UPLOAD_LOGO',
    'ORG_REMOVE_LOGO',
    'ORG_VIEW_LOGO'
  )
)
```

Kısıtlar:

- Bu policy `app.iam_platform_admin_support_access` bayrağına **bağımlı değildir**; bayrak
  ancak bu `SELECT` başarıyla bir satır döndürdükten **sonra** server-set olarak `true`
  yapılır. Bayrağın `SELECT` öncesi kurulmuş gibi davranması negatif test kapısıdır ve
  hiçbir satır döndürmemelidir.
- Policy yalnız `actor user_id + revoked_at IS NULL` satırını açar; başka admin satırı
  (`user_id != app.iam_actor_user_id`) görünmez. Başka admin ile sahte destek erişimi
  negatif test kapısıdır.
- Genel `SELECT` (boş `USING`), `BYPASSRLS` veya yazma yetkisi (`INSERT`/`UPDATE`/`DELETE`)
  verilmez.
- Allow-list dışı operation code ile `SELECT` hiçbir satır döndürmez → doğrulama başarısız
  → `403 FORBIDDEN`. Yanlış operation code negatif test kapısıdır.
- Revoked admin satırı (`revoked_at IS NOT NULL`) görünmez ve fail-closed
  `403 FORBIDDEN` döner; revoked admin negatif test kapısıdır.
- Bu policy, `platform_administrators` için mevcut `IAM_AUTH` `PLATFORM_ADMIN_ACTIVATE`
  policy'sinden bağımsız ve ona ek bir `PERMISSIVE` policy'dir; `OR` ile birleşir.
  `ORGANIZATION` scope'unda `IAM_AUTH` policy'sinin `app.iam_operation_scope = 'IAM_AUTH'`
  guard'ı zaten `false` olduğu için çakışma oluşmaz.

Bu policy'nin ADR-004'teki tam karşılığı `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md`
işlem/scope matrisinde tanımlıdır ve bu sözleşmeyle birebir aynıdır (ORG-002 operation
code'ları dâhil).

### 4.1b. `platform_administrators` GLOBAL scope SELECT RLS (bağlayıcı)

`CREATE` ve platform-admin `LIST` işlemlerinde aktif `platform_administrators` kaydının
doğrulanması, aşağıdaki dar `FORCE RLS` policy'si ile yapılır. Bu policy'nin ait olduğu
runtime rolü `ORG-003` kapsamında kesinleştirilecektir; `iam_runtime` bu policy'yi
işletmez.

```
USING (
  app.iam_operation_scope = 'GLOBAL'
  AND app.iam_actor_user_id = platform_administrators.user_id
  AND platform_administrators.revoked_at IS NULL
  AND app.iam_operation_code IN (
    'ORG_CREATE',
    'ORG_LIST'
  )
)
```

Kısıtlar:

- `app.iam_platform_admin_support_access` bayrağı `GLOBAL` scope'ta **kullanılmaz** ve
  bu policy'nin `USING` predicate'inde yer almaz. Bayrak yalnız hedef-kurumlu
  `ORGANIZATION` destek erişimine aittir.
- `platform_administrator_profiles` `GLOBAL` scope'ta görünmez; tablo bu policy'nin
  kapsamı dışındadır.
- Başka platform yöneticisi satırı görünmez.
- Genel `SELECT`, `BYPASSRLS` veya yazma yetkisi verilmez.
- Allow-list dışı operation code hiçbir satır döndürmez → `403 FORBIDDEN`.
- Bu policy, mevcut `IAM_AUTH` ve `ORGANIZATION` `platform_administrators` policy'lerine
  ek bir `PERMISSIVE` policy'dir; `OR` ile birleşir. `GLOBAL` scope'unda diğer
  policy'lerin guard'ları `false` olduğu için çakışma oluşmaz.

Bu policy'nin ADR-004'teki tam karşılığı aşağıdaki tabloda tanımlıdır ve bu sözleşmeyle
birebir aynıdır.

### 4.1c. Runtime rolü sahipliği (bağlayıcı)

- ORG işlemi **tek DB transaction ve tek runtime rolüyle** çalışır. Bu rol:
  - `organizations` üzerinde ilgili operation code'lara ait dar `GRANT`/`FORCE RLS`
    policy'lerine,
  - `platform_administrators` üzerinde yalnızca actor-only `SELECT` RLS policy'lerine
    (ORGANIZATION: `§4.1a`, GLOBAL: `§4.1b`),
  - `audit_logs` üzerinde `ORGANIZATION` scope `INSERT` ve `GLOBAL` scope `INSERT`
    yetkisine,
  - `idempotency_keys` üzerinde `ORGANIZATION` ve `GLOBAL` scope `INSERT`/`UPDATE`
    yetkisine
  sahip olmalıdır.
- Somut rol adı ve migration grant'leri `ORG-003` kapsamında kesinleştirilecektir.
- `ORG-003` kabul ölçütüne `ORG_CREATE`, `ORG_LIST`, `ORG_DETAIL`,
  `ORG_UPDATE_IDENTITY`, `ORG_SUSPEND`, `ORG_ACTIVATE`, `ORG_ARCHIVE` ve
  `ORG-002`'den gelen `ORG_VIEW_BRAND`, `ORG_UPDATE_BRAND`, `ORG_VIEW_BRAND_COLORS`,
  `ORG_UPDATE_BRAND_COLORS`, `ORG_VIEW_MODULES`, `ORG_UPDATE_MODULES`,
  `ORG_UPLOAD_LOGO`, `ORG_REMOVE_LOGO`, `ORG_VIEW_LOGO` operation code'ları için
  gerçek PostgreSQL `GRANT`/RLS testleri eklenmelidir. `ORG-003` bu sözleşmenin
  §4.1a allow-list'inin tamamını (ORG-001 + ORG-002) tek kanonik liste olarak uygular;
  ayrı bir ORG-002 allow-list tanımlamaz.
- `ORG-003` kabul ölçütüne `AUDIT-001A`'nın `V2__audit_core.sql` migration'ını
  **değiştirmeden** kendi Flyway V3 migration'ında (`V3__...`) `ORG_SETTING_CHANGED`
  için `payload_schema_version = 2` katalog satırını eklemesi dâhildir. v2 şeması
  `ORG_MARKA_AYARLARI_API_SOZLESMESI.md §2.8.2`'de tanımlanan izinli alanları
  (`name`, `shortName`, `defaultTimezone`, `primaryColor`, `secondaryColor`,
  `logoAssetId`, `enabledModules`, `brandColors`, `attendanceStatuses`,
  `rowVersion`) `rejectUnknown: true` ile birebir taşımalıdır; mevcut v1 katalog
  satırı **değiştirilmemeli** ve **silinmemelidir**. ORG-005 (marka API backend)
  yalnız v2 katalog satırıyla audit yazabilmeli; v1 yolu fail-closed reddedilmeli
  (testle kanıtlanmalı). ORG-003 audit `INSERT` policy/grant'i başarısız audit
  kaydında tüm lifecycle transaction'ı rollback yapmalı (testle kanıtlanmalı).
- `iam_runtime` rolü ADR-003 uyarınca yalnız IAM ve dar güvenlik yüzeyiyle sınırlıdır;
  ORG yaşam döngüsü transaction'ını işletmez ve ORG tablolarına (`organizations` dâhil)
  erişmez.
- ADR-004 IAM sahipliğini korur; ORG policy davranışına çapraz referans verebilir fakat
  `iam_runtime`'ın ORG tablolarını işlettiğini iddia etmez. Yeni runtime rolü veya yeni
  mimari karar üretilmez.

### 4.2. SUSPEND/ARCHIVE sabit kilit sırası (bağlayıcı)

`SUSPEND` ve `ARCHIVE` transaction'ları aşağıdaki sırada kilit alır. Bu sıra deadlock'u
önler ve `CONTEXT_ACTIVATE`/`SESSION_REFRESH` ile serileştirmeyi sağlar:

1. **`organizations`** — `SELECT id, status, row_version FROM organizations WHERE id = :orgId FOR UPDATE`
2. Kilit sonrası `status` ve `rowVersion` yeniden okunur; durum ön koşulu doğrulanır:
   - `SUSPEND` için: `status = ACTIVE` ve `rowVersion = If-Match-Row-Version`
   - `ARCHIVE` için: `status IN (ACTIVE, SUSPENDED)` ve `rowVersion = If-Match-Row-Version`
   - Koşul sağlanmazsa → `409 STATE_CONFLICT` veya `409 VERSION_CONFLICT`, transaction rollback.
3. **`organization_memberships`** — `SELECT id, session_generation FROM organization_memberships WHERE organization_id = :orgId FOR UPDATE`
   (hedef kurumun bütün üyelikleri)
4. **`refresh_token_families`** — `SELECT id FROM refresh_token_families WHERE organization_membership_id IN (:membershipIds) AND revoked_at IS NULL FOR UPDATE`
5. **`refresh_tokens`** — `SELECT id FROM refresh_tokens WHERE family_id IN (:familyIds) AND revoked_at IS NULL FOR UPDATE`
6. Mutasyonlar:
   - `UPDATE organizations SET status = :newStatus, updated_at = NOW(), row_version = row_version + 1`
   - `UPDATE organization_memberships SET session_generation = session_generation + 1, reauthentication_required_after = NOW() WHERE organization_id = :orgId`
   - `UPDATE refresh_token_families SET revoked_at = NOW() WHERE id IN (:familyIds)`
   - `UPDATE refresh_tokens SET revoked_at = NOW() WHERE id IN (:tokenIds)`
7. `INSERT INTO audit_logs` — kurum durum değişikliği + üye oturum toplu iptali
8. `INSERT` veya koşullu `UPDATE idempotency_keys`
9. COMMIT

`ACTIVATE` işlemi yalnızca adım 1–2 (organizations kilidi), adım 6 (status UPDATE,
`row_version = row_version + 1`), adım 7 (audit) ve adım 8'i (idempotency)
çalıştırır. Adım 3–5 çalıştırılmaz. `rowVersion` artışı, `ACTIVATE` sonrası eski
`rowVersion` taşıyan gecikmiş `SUSPEND` işlemlerinin `VERSION_CONFLICT` almasını
sağlar.

### 4.3. CONTEXT_ACTIVATE ve SESSION_REFRESH serileştirmesi

IAM-001 sözleşmesindeki `CONTEXT_ACTIVATE` ve `SESSION_REFRESH` işlemleri, kurum yaşam
döngüsüyle aynı kilit bariyerini kullanır:

- `CONTEXT_ACTIVATE`: yeni `refresh_token_families`/`refresh_tokens` üretmeden önce,
  hedef `organization_membership`'in bağlı olduğu `organizations` satırını
  `SELECT ... FOR UPDATE` ile kilitler. Kilit sonrası `status = ACTIVE` değilse
  yeni aile üretilmez; `403 FORBIDDEN` veya uygun hata döner.
- `SESSION_REFRESH`: mevcut aileyi yenilemeden önce aynı `organizations` satırını kilitler.
  Kilit sonrası `status != ACTIVE` ise yenileme yapılmaz; `401 SESSION_REVOKED` döner.

Bu serileştirme şu yarış koşullarını engeller:

- **SUSPEND ↔ CONTEXT_ACTIVATE:** SUSPEND organization'ı kilitler → CONTEXT_ACTIVATE bekler
  → SUSPEND commit → CONTEXT_ACTIVATE kilidi alır → status SUSPENDED → reddeder.
- **SUSPEND ↔ SESSION_REFRESH:** Aynı mekanizma; SUSPEND sonrası refresh reddedilir.
- **ARCHIVE ↔ CONTEXT_ACTIVATE:** Aynı mekanizma; ARCHIVE sonrası yeni oturum kurulamaz.
- **ACTIVATE sonrası geç family/token:** ACTIVATE commit'inden sonra gelen CONTEXT_ACTIVATE
  kilidi alır → status ACTIVE → başarılı olur (istenen davranış). ACTIVATE `rowVersion`
  artırdığı için, `ACTIVATE` commit'ini bekleyen ve eski `If-Match-Row-Version` taşıyan
  bir `SUSPEND` transaction'ı kilidi aldıktan sonra `rowVersion` eşleşmez →
  `409 VERSION_CONFLICT` döner. Güncel `rowVersion` ile `ACTIVE` kuruma gönderilen yeni
  `SUSPEND` geçerlidir. Eski token hiçbir senaryoda yeniden geçerli olmaz.

## 5. Ortak istek/cevap kuralları

### 5.1. Genel kurallar

- `API_GENEL_KURALLARI.md` bölüm 3, 4, 5 ve 7 bu belge için aynen geçerlidir.
- Bütün yazma uçları `Idempotency-Key` başlığı taşır.
- Yalnızca `CREATE` `If-Match-Row-Version` başlığı taşımaz; diğer bütün güncelleme ve durum
  komutları `If-Match-Row-Version` taşır.
- `PATCH` ve durum komutlarında `If-Match-Row-Version` güncel `rowVersion` ile eşleşmezse
  `409 VERSION_CONFLICT` döner.
- `LIST` ve `DETAIL` okuma uçları `Idempotency-Key` taşımaz.
- Başarılı durum değişikliği güncel kaydı `200 OK` ile döndürür.
- `status` alanı yalnızca komut uçları üzerinden değişir; `PATCH` gövdesinde `status`
  göndermek `422 VALIDATION_FAILED` döndürür.

### 5.2. İdempotency kapsamı

İdempotency kapsamı, işlemin transaction scope'u ile aynıdır:

- `GLOBAL` scope işlemler (`CREATE`): `actorUserId + clientMutationId`
- `ORGANIZATION` scope işlemler (platform admin hedefli ve org aktörü, `CREATE` hariç tüm
  yazmalar): `organizationId + actorUserId + clientMutationId`

`idempotency_keys` tablosundaki `UNIQUE (scope_type, organization_id, user_id,
client_mutation_id)` partial unique index sayesinde, aynı `clientMutationId` farklı ve
geçerli scope'larda (`GLOBAL` ve `ORGANIZATION` gibi) coexist edebilir. Yanlış
operation/scope birleşimi (örn. `ORG_SUSPEND` operation code'u `GLOBAL` scope'ta
çalıştırılmaya çalışılırsa) idempotency/fingerprint kontrolünden önce fail-closed
reddedilir.

### 5.3. Cursor sözleşmesi

- `LIST` ucu cursor tabanlı sayfalama kullanır.
- Cursor **opaktır**; bütünlük korumalıdır (HMAC).
- Cursor taşıdığı bağlam: `actor`, `scope` (`GLOBAL`), `filter` (`status`, `search`),
  `sort` + `order`, `offset`/`pointer`.
- Başka aktör, scope, filtre veya sıralama bağlamında kullanılamaz; `400 INVALID_CURSOR`.
- `ORGANIZATION` scope `LIST` hiçbir cursor üretmez (`nextCursor` her zaman `null`).

### 5.4. Request fingerprint

Her yazma işlemi için:

```
fingerprint = SHA-256(
  HTTP method +
  canonical path +
  operation type (CREATE | UPDATE_IDENTITY | SUSPEND | ACTIVATE | ARCHIVE) +
  target organizationId (veya boş dize) +
  SHA-256(request body veya boş dize) +
  If-Match-Row-Version (:create için boş dize)
)
```

### 5.5. Replay akış sıralaması (bağlayıcı)

Bütün yazma işlemlerinde replay değerlendirmesi şu sırada yapılır:

1. **Authentication:** token doğrulaması (geçerlilik, süre, `session_generation` eşleşmesi).

2. **Operation code allow-list (transaction öncesi):** route'tan çözülen `operationCode`
   allow-list'te değilse transaction açılmadan fail-closed `403 FORBIDDEN` reddedilir.
   Allow-list yazma işlemine göre:
   - `CREATE`: `ORG_CREATE`
   - Platform admin hedefli `PATCH`, `SUSPEND`, `ACTIVATE`, `ARCHIVE`:
     `ORG_UPDATE_IDENTITY`, `ORG_SUSPEND`, `ORG_ACTIVATE`, `ORG_ARCHIVE`
   - Kurum aktörü `PATCH`: operation code allow-list kontrolü yoktur (scope doğrudan
     token'dan çözülür).
   Salt-okunur `ORG_DETAIL` yazma replay allow-list'inde yer almaz.

3. **Authorization (transaction içi):** güncel aktör yetkisi kontrolü.

   **GLOBAL CREATE:**
   - `app.iam_operation_scope = 'GLOBAL'`, `app.iam_actor_user_id` ve
     `app.iam_operation_code` server-set kurulur.
   - Dar `platform_administrators` `SELECT`'i (§4.1b RLS policy'si ile) yapılır;
     `revoked_at IS NULL` satır bulunmazsa `403 FORBIDDEN`.
   - `app.iam_platform_admin_support_access` kullanılmaz.

   **Platform admin hedefli yazmalar (PATCH/SUSPEND/ACTIVATE/ARCHIVE):**
   - `app.iam_operation_scope = 'ORGANIZATION'`, `app.iam_actor_user_id`,
     `app.organization_id` ve `app.iam_operation_code` server-set kurulur.
   - Dar `platform_administrators` `SELECT`'i (§4.1a RLS policy'si ile) yapılır;
     `revoked_at IS NULL` satır bulunursa `app.iam_platform_admin_support_access = true`
     kurulur. Bulunmazsa `403 FORBIDDEN`.

   **Kurum aktörü PATCH:**
   - Token'dan çözülen `app.organization_id` kullanılır.
   - `organization_memberships.status = ACTIVE`, rol geri alınmamış, `BRAND_MANAGE`
     izni aktif mi? Değilse `403 FORBIDDEN`.

   Yetki geri alınmış aktör bu adımda reddedilir; eski idempotency sonucu replay edilmez.

4. **Fingerprint / idempotency replay:** aynı `Idempotency-Key` + aynı fingerprint
   ile daha önce tamamlanmış işlem varsa, ilk sonucun eşdeğeri döner. Bu adım
   `VERSION_CONFLICT` veya `STATE_CONFLICT` üretmez. Aynı key + farklı fingerprint
   → `409 IDEMPOTENCY_KEY_REUSED` döner.

5. **State / rowVersion:** `If-Match-Row-Version` ve durum ön koşulu doğrulanır
   (kilit sonrası, bkz. §4.2). `CREATE` bu adımı atlar.

6. **Mutation:** değişiklik uygulanır; audit ve idempotency sonucu aynı transaction'da
   yazılır. Audit `INSERT` başarısız olursa tüm mutasyonlar rollback yapılır;
   idempotency sonucu terminal başarıya dönüşmez; istemciye `500 INTERNAL_ERROR` döner.

## 6. Veri şekilleri

### 6.1. Kurum nesnesi

```json
{
  "id": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
  "name": "Fındıklı Kur'an Kursu",
  "shortName": "Fındıklı",
  "defaultTimezone": "Europe/Istanbul",
  "status": "ACTIVE",
  "createdAt": "2026-07-17T10:00:00Z",
  "updatedAt": "2026-07-17T11:30:00Z",
  "rowVersion": 3
}
```

### 6.2. Sayfalı liste zarfı

```json
{
  "items": [],
  "page": {
    "nextCursor": null,
    "hasNextPage": false
  }
}
```

### 6.3. Sunucu alan kuralları

- `id`: UUID, sunucu üretir. Oluşturmada istemci UUID'si kabul edilmez.
- `name`: Zorunlu, 1–200 karakter, boş olamaz.
- `shortName`: İsteğe bağlı, 1–50 karakter, `null` ile temizlenebilir.
- `defaultTimezone`: Zorunlu, geçerli IANA tanımlayıcısı; gönderilmezse `Europe/Istanbul`.
- `status`: `ACTIVE`, `SUSPENDED` veya `ARCHIVED`. Oluşturmada `ACTIVE`.
- `createdAt`, `updatedAt`: ISO 8601 UTC, salt okunur.
- `rowVersion`: Artan tamsayı, salt okunur.

## 7. `POST /api/v1/organizations` — Kurum oluşturma

### 7.1. Amaç

Yeni kurum oluşturur. V1'de yalnızca platform yöneticisi. Scope: `GLOBAL`.

### 7.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>` (kapsam: `GLOBAL_PLATFORM_ADMIN`)
- `Idempotency-Key: <clientMutationId>`

`If-Match-Row-Version` **taşımaz** (oluşturma işlemi).

Gövde:

```json
{
  "name": "Fındıklı Kur'an Kursu",
  "shortName": "Fındıklı",
  "defaultTimezone": "Europe/Istanbul"
}
```

Alan kuralları:

- `name` zorunlu; 1–200 karakter, boş veya yalnızca boşluk olamaz.
- `shortName` isteğe bağlı; 1–50 karakter.
- `defaultTimezone` isteğe bağlı; geçerli IANA tanımlayıcısı, yoksa `Europe/Istanbul`.
- `status`, `id`, `createdAt`, `updatedAt`, `rowVersion` gönderilemez.

Fingerprint: `SHA-256(POST + /api/v1/organizations + CREATE + "" + SHA-256(body) + "")`

### 7.3. Başarılı cevap

`201 Created` — kurum nesnesi, `status: ACTIVE`, `rowVersion: 1`.

### 7.4. Hata kuralları

- `401 UNAUTHENTICATED`: token geçersiz.
- `403 FORBIDDEN`: token `GLOBAL_PLATFORM_ADMIN` kapsamında değil.
- `403 ORGANIZATION_CONTEXT_REQUIRED`: `contextSelectionToken` ile gelinmişse.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı key, farklı fingerprint.
- `422 VALIDATION_FAILED`: `name` boş/geçersiz; `shortName` geçersiz;
  `defaultTimezone` geçersiz; salt okunur alan gönderilmiş.
- `500 INTERNAL_ERROR`: audit kaydı yazılamadı; transaction rollback, kurum oluşturulmaz.
- `429 RATE_LIMITED`.

## 8. `GET /api/v1/organizations` — Kurum listeleme

### 8.1. Amaç

Kurumları listeler. Platform admin: `GLOBAL` scope, bütün kurumlar sayfalı. Org aktörü:
`ORGANIZATION` scope, yalnızca kendi `ACTIVE` kurumu.

### 8.2. İstek

Başlık: `Authorization: Bearer <platform-access-token>`

Sorgu parametreleri (yalnızca `GLOBAL` scope'ta geçerli):

| Parametre | Tür | Açıklama |
|---|---|---|
| `status` | `ACTIVE`, `SUSPENDED`, `ARCHIVED` | Durum filtresi |
| `search` | metin | `name`/`shortName` içinde kısmi arama |
| `sort` | `name`, `createdAt` | Varsayılan `name` |
| `order` | `ASC`, `DESC` | Varsayılan `ASC` |
| `limit` | pozitif tamsayı | Sayfa boyutu |
| `cursor` | opak metin | Sonraki sayfa |

### 8.3. Başarılı cevap

`200 OK`

**`GLOBAL` scope (platform admin):**

```json
{
  "items": [ { "...kurum nesnesi..." } ],
  "page": {
    "nextCursor": "<opak-cursor>",
    "hasNextPage": true
  }
}
```

- Bütün durumlardaki kurumlar, filtre ve sayfalama uygulanır.
- Sıralama: `sort` + `order`, eşitlikte `id ASC`.
- Her görüntülenen kurum için audit yazılır; audit başarısız → `500 INTERNAL_ERROR`.
- Cursor: aktör, scope, filtre, sıralama bağlamına bağlı.

**`ORGANIZATION` scope:**

```json
{
  "items": [ { "...kendi kurumu..." } ],
  "page": {
    "nextCursor": null,
    "hasNextPage": false
  }
}
```

- Ön koşul: kendi kurumu `ACTIVE`. `SUSPENDED`/`ARCHIVED` → `403 FORBIDDEN`.
- Yalnızca tek ögeli liste.
- `status`, `search`, `sort`, `order`, `limit`, `cursor` parametrelerinden **herhangi
  biri** gönderilirse `422 VALIDATION_FAILED` döner (hiçbiri sessizce yok sayılmaz).
- `nextCursor` her zaman `null`; bu scope **hiçbir cursor üretmez**.

### 8.4. Hata kuralları

- `401 UNAUTHENTICATED`: token geçersiz.
- `403 FORBIDDEN`: `ORGANIZATION` scope'ta kurum `SUSPENDED`/`ARCHIVED`.
- `403 ORGANIZATION_CONTEXT_REQUIRED`: `contextSelectionToken` ile gelinmişse.
- `400 INVALID_CURSOR`: geçersiz, bütünlük başarısız veya bağlam uyuşmazlığı.
- `422 VALIDATION_FAILED`: geçersiz `status`; `limit` üst sınır aşımı; `ORGANIZATION`
  scope'ta `status`/`search`/`sort`/`order`/`limit`/`cursor` parametrelerinden
  herhangi biri gönderilmiş.
- `500 INTERNAL_ERROR`: platform admin audit yazılamadı (fail-closed).
- `429 RATE_LIMITED`.

## 9. `GET /api/v1/organizations/{organizationId}` — Kurum detayı

### 9.1. Amaç

Tek kurum detayı.

### 9.2. İstek

Başlık: `Authorization: Bearer <platform-access-token>`

Yol: `organizationId` — hedef kurum UUID.

### 9.3. Başarılı cevap

`200 OK` — kurum nesnesi.

Davranış:

- **Platform admin hedefli** (`ORGANIZATION` scope + platform-admin bayrağı):
  herhangi bir durumdaki kurum. Audit zorunlu; başarısız → `500 INTERNAL_ERROR`.
- **Org aktörü** (`ORGANIZATION` scope): yalnızca kendi `ACTIVE` kurumu.
  `organizationId` eşleşmezse `404`; `SUSPENDED`/`ARCHIVED` → `403`.

### 9.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `500 INTERNAL_ERROR`, `429 RATE_LIMITED`.

## 10. `PATCH /api/v1/organizations/{organizationId}` — Kurum kimlik alanı güncelleme

### 10.1. Amaç

`name`, `shortName`, `defaultTimezone` kısmi güncelleme. Logo/renk/modül: `ORG-002`.

### 10.2. Erişim matrisi

| Durum | Platform admin | Org aktörü |
|---|---|---|
| `ACTIVE` | İzinli | İzinli (`ORG_ADMIN` / `BRAND_MANAGE`) |
| `SUSPENDED` | İzinli | `403 FORBIDDEN` |
| `ARCHIVED` | `409 STATE_CONFLICT` | `403 FORBIDDEN` |

### 10.3. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`
- `If-Match-Row-Version: <rowVersion>`

Gövde:

```json
{
  "name": "Fındıklı Yeni Ad",
  "shortName": "FYeni",
  "defaultTimezone": "Europe/Istanbul"
}
```

Alan kuralları:

- `name`: 1–200 karakter, boş olamaz.
- `shortName`: 1–50 karakter veya açık `null` (temizlenir).
- `defaultTimezone`: geçerli IANA, `null` gönderilemez.
- Gövdede bulunmayan alan değişmez; boş gövde `422`.
- `status` gönderilemez.

Fingerprint: `SHA-256(PATCH + /api/v1/organizations/{orgId} + UPDATE_IDENTITY + <orgId> + SHA-256(body) + <rowVersion>)`

### 10.4. Başarılı cevap

`200 OK` — güncel kurum nesnesi.

### 10.5. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`,
  `409 IDEMPOTENCY_KEY_REUSED`, `422 VALIDATION_FAILED`, `500 INTERNAL_ERROR`,
  `429 RATE_LIMITED`.

## 11. `POST /api/v1/organizations/{organizationId}/suspend` — Askıya alma

### 11.1. Amaç

`ACTIVE` → `SUSPENDED`. Kuruma bağlı bütün aktif oturumları aynı transaction'da iptal
eder. Scope: `ORGANIZATION` + platform-admin bayrağı. Mutlak sınır.

### 11.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>` (kapsam: `GLOBAL_PLATFORM_ADMIN`)
- `Idempotency-Key: <clientMutationId>`
- `If-Match-Row-Version: <rowVersion>`

Gövde yok.

Fingerprint: `SHA-256(POST + /api/v1/organizations/{orgId}/suspend + SUSPEND + <orgId> + SHA-256("") + <rowVersion>)`

### 11.3. Başarılı cevap

`200 OK` — kurum nesnesi, `status: SUSPENDED`.

Davranış:

- Ön koşul: `status = ACTIVE`.
- Sabit kilit sırası (§4.2) ile transaction yürütülür.
- Kilit sonrası status ve rowVersion yeniden doğrulanır.
- Atomik: status değişikliği + session_generation artışı + family/token iptali + audit +
  idempotency.
- Replay sıralaması (§5.5): authentication → yetki → fingerprint/idempotency →
  state/rowVersion (kilit sonrası) → mutation.

### 11.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`,
  `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`, `409 IDEMPOTENCY_KEY_REUSED`,
  `500 INTERNAL_ERROR`, `429 RATE_LIMITED`.

## 12. `POST /api/v1/organizations/{organizationId}/activate` — Etkinleştirme

### 12.1. Amaç

`SUSPENDED` → `ACTIVE`. Eski oturumları canlandırmaz; kullanıcı yeni oturum kurmalıdır.
Scope: `ORGANIZATION` + platform-admin bayrağı. Mutlak sınır.

### 12.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>` (kapsam: `GLOBAL_PLATFORM_ADMIN`)
- `Idempotency-Key: <clientMutationId>`
- `If-Match-Row-Version: <rowVersion>`

Gövde yok.

Fingerprint: `SHA-256(POST + /api/v1/organizations/{orgId}/activate + ACTIVATE + <orgId> + SHA-256("") + <rowVersion>)`

### 12.3. Başarılı cevap

`200 OK` — kurum nesnesi, `status: ACTIVE`.

Davranış:

- Ön koşul: `status = SUSPENDED`.
- `organizations` satırı `FOR UPDATE` ile kilitlenir; kilit sonrası status/rowVersion
  yeniden doğrulanır.
- `rowVersion` artırılır (`row_version = row_version + 1`). Bu artış, `ACTIVATE` sonrası
  eski `rowVersion` taşıyan gecikmiş `SUSPEND` işlemlerinin `VERSION_CONFLICT` almasını
  sağlar.
- `session_generation` değerleri geri alınmaz. Askı sırasında artırılmış kuşak korunur.
- Eski token'lar geçersiz kalır. Kullanıcı yeni `CONTEXT_ACTIVATE` ile oturum kurmalıdır.

### 12.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`,
  `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`, `409 IDEMPOTENCY_KEY_REUSED`,
  `500 INTERNAL_ERROR`, `429 RATE_LIMITED`.

## 13. `POST /api/v1/organizations/{organizationId}/archive` — Arşivleme

### 13.1. Amaç

`ACTIVE`/`SUSPENDED` → `ARCHIVED`. Terminal durum. Oturumları aynı transaction'da iptal
eder. Scope: `ORGANIZATION` + platform-admin bayrağı. Mutlak sınır.

### 13.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>` (kapsam: `GLOBAL_PLATFORM_ADMIN`)
- `Idempotency-Key: <clientMutationId>`
- `If-Match-Row-Version: <rowVersion>`

Gövde yok.

Fingerprint: `SHA-256(POST + /api/v1/organizations/{orgId}/archive + ARCHIVE + <orgId> + SHA-256("") + <rowVersion>)`

### 13.3. Başarılı cevap

`200 OK` — kurum nesnesi, `status: ARCHIVED`.

Davranış:

- Ön koşul: `status IN (ACTIVE, SUSPENDED)`.
- Sabit kilit sırası (§4.2) ile transaction yürütülür; `SUSPEND` ile aynı adımlar +
  hedef durum `ARCHIVED`.
- `ARCHIVED` terminal: platform admin dâhil PATCH ve durum komutları reddedilir.
- Geçmiş veriler korunur; platform admin LIST/DETAIL ile erişmeye devam eder.

### 13.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`,
  `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`, `409 IDEMPOTENCY_KEY_REUSED`,
  `500 INTERNAL_ERROR`, `429 RATE_LIMITED`.

## 14. Kabul senaryoları

### 14.1. Temel akışlar (1–13)

1. **Oluşturma:** Platform admin `POST /organizations` → `201 Created`, `status: ACTIVE`.
2. **Yetkisiz oluşturma:** Org token ile `POST /organizations` → `403 FORBIDDEN`.
3. **Platform admin listeleme:** `GET /organizations` → sayfalı, filtreli.
4. **Org aktörü listeleme:** `GET /organizations` → tek ögeli, `hasNextPage: false`.
5. **Org scope parametre reddi:** `GET /organizations?status=SUSPENDED` org token ile →
   `422 VALIDATION_FAILED` (tüm parametreler reddedilir).
6. **Org scope limit reddi:** `GET /organizations?limit=10` org token ile →
   `422 VALIDATION_FAILED`.
7. **Org scope cursor reddi:** Org token ile `GET /organizations?cursor=<herhangi>` →
   `422 VALIDATION_FAILED` (bu scope cursor üretmez, kabul de etmez).
8. **Cursor bağlam reddi:** Platform admin cursor'ı org token ile → `400 INVALID_CURSOR`.
9. **Cursor filtre reddi:** `status=ACTIVE` cursor'ı `status=SUSPENDED` filtresiyle →
   `400 INVALID_CURSOR`.
10. **Varlık gizleme:** Org aktörü başka kurum ID'si ile `GET /organizations/{id}` →
    `404`.
11. **Kimlik güncelleme:** Org admin `PATCH` → `200 OK`. Yetkisiz hoca → `403`.
12. **Sürüm çakışması:** Eski `rowVersion` → `409 VERSION_CONFLICT`.
13. **Durum geçiş ön koşulu:** `SUSPENDED` kuruma `suspend` → `409 STATE_CONFLICT`.

### 14.2. İdempotency ve replay (14–17)

14. **İdempotent oluşturma:** Aynı key + fingerprint → `201 Created`, ikinci kayıt yok.
15. **Replay VERSION_CONFLICT üretmez:** Kayıp `PATCH` cevabı, aynı key/fingerprint ile
    yeniden gönderim → ilk başarılı sonuç, `409` üretmez.
16. **Replay STATE_CONFLICT üretmez:** Kayıp `suspend` cevabı, aynı key/fingerprint ile
    yeniden gönderim → ilk başarılı sonuç, `409` üretmez.
17. **Farklı fingerprint reddi:** Aynı key, farklı `If-Match-Row-Version` veya gövde →
    `409 IDEMPOTENCY_KEY_REUSED`.

### 14.3. Oturum ve durum yan etkileri (18–20)

18. **Suspend oturumları iptal eder:** `ACTIVE` → `suspend`; mevcut access/refresh
    token'lar geçersiz; `401 UNAUTHENTICATED`.
19. **Activate eski token'ları canlandırmaz:** `SUSPENDED` → `activate`; askı öncesi
    token'lar hâlâ geçersiz. Kullanıcı yeni `CONTEXT_ACTIVATE` ile oturum kurar.
20. **Archive oturumları iptal eder:** `ACTIVE` → `archive`; tüm oturumlar geçersiz;
    terminal durum.

### 14.4. Erişim kontrolleri (21–23)

21. **SUSPENDED org erişim reddi:** Kurum `SUSPENDED`; org admin `LIST`/`DETAIL`/`PATCH`
    → `403 FORBIDDEN`.
22. **ARCHIVED kurum PATCH reddi:** Platform admin `ARCHIVED` kuruma `PATCH` →
    `409 STATE_CONFLICT`.
23. **Platform admin audit fail-closed:** `LIST` veya `DETAIL`'te audit yazılamaz →
    `500 INTERNAL_ERROR`, kurum verisi gösterilmez.

### 14.5. Scope ve idempotency (24)

24. **Aynı clientMutationId farklı scope'larda coexist:** Aynı `clientMutationId` değeri
    `GLOBAL` scope'ta `CREATE` için ve `ORGANIZATION` scope'ta `PATCH` için kullanılabilir;
    `idempotency_keys` partial unique index'i `(scope_type, organization_id, user_id,
    client_mutation_id)` üzerinde tanımlandığından farklı scope'larda çakışma oluşmaz.
    Yanlış operation/scope birleşimi (örn. `ORG_SUSPEND` operation code'u `GLOBAL`
    scope'ta çalıştırılmaya çalışılırsa) idempotency/fingerprint kontrolünden önce
    fail-closed `403 FORBIDDEN` ile reddedilir.

### 14.6. Eşzamanlılık ve yarış senaryoları (25–29)

25. **SUSPEND ↔ CONTEXT_ACTIVATE yarışı:** `SUSPEND` organizasyonu kilitler →
    `CONTEXT_ACTIVATE` bekler → `SUSPEND` commit → `CONTEXT_ACTIVATE` kilidi alır →
    `status = SUSPENDED` → yeni aile üretilmez; `403 FORBIDDEN`.
26. **SUSPEND ↔ SESSION_REFRESH yarışı:** `SUSPEND` organizasyonu kilitler →
    `SESSION_REFRESH` bekler → `SUSPEND` commit → `SESSION_REFRESH` kilidi alır →
    `status = SUSPENDED` → yenileme red; `401 SESSION_REVOKED`.
27. **ARCHIVE ↔ CONTEXT_ACTIVATE yarışı:** `ARCHIVE` organizasyonu kilitler →
    `CONTEXT_ACTIVATE` bekler → `ARCHIVE` commit → `CONTEXT_ACTIVATE` kilidi alır →
    `status = ARCHIVED` → yeni aile üretilmez; `403 FORBIDDEN`.
28. **ACTIVATE sonrası eski rowVersion SUSPEND reddi:** `ACTIVATE` commit eder,
    `rowVersion` artar (örn. 5 → 6). `ACTIVATE` öncesinde `rowVersion=5` ile başlatılmış
    gecikmiş bir `SUSPEND` transaction'ı `ACTIVATE` commit'ini bekler → kilidi aldığında
    `rowVersion` güncel değerle (6) eşleşmez → `409 VERSION_CONFLICT` döner. Güncel
    `rowVersion=6` ile `ACTIVE` kuruma gönderilen yeni `SUSPEND` ise ön koşul sağlandığı
    için başarılı olur. Hiçbir senaryoda eski token yeniden geçerli olmaz.
29. **Yetkisi geri alınmış aktör replay reddi:** Kullanıcının `BRAND_MANAGE` izni geri
    alınır. Eski bir `PATCH` işleminin `Idempotency-Key`'i ile yeniden istek yapılır.
    Authentication başarılı olsa da authorization adımında (`§5.5` adım 2) güncel
    yetki kontrolü başarısız olur → `403 FORBIDDEN`. Eski başarılı sonuç replay
    edilmez.

### 14.7. Platform admin yetki ve operation code kontrolleri (30–32)

30. **Yetkisi geri alınmış platform admin stale token reddi:** Platform yöneticisinin
    `platform_administrators` kaydı geri alınır (`revoked_at` doldurulur). Kullanıcının
    mevcut `GLOBAL_PLATFORM_ADMIN` token'ı hâlâ geçerli olsa da, hedef kurumlu bir
    işlemde (örn. `PATCH`) aynı transaction içinde `platform_administrators` kontrolü
    `SELECT ... WHERE revoked_at IS NULL` başarısız olur → `403 FORBIDDEN`. Server-set
    `app.iam_platform_admin_support_access` bayrağı kurulmaz.

31. **Yanlış support flag veya operation code tablo erişimi sağlayamaz:** İstemci
    `app.iam_platform_admin_support_access` veya `app.iam_operation_code` değerini
    başlık/gövde üzerinden göndermeye çalışır. Bu değişkenler yalnızca server-set'tir;
    istemci girdisi reddedilir. Ayrıca allow-list dışı bir operation code (örn.
    hayali `ORG_DELETE`) `ORGANIZATION` scope'ta `app.iam_platform_admin_support_access`
    ile birleştirilemez; `403 FORBIDDEN`.

32. **ACTIVATE sonrası eski rowVersion SUSPEND VERSION_CONFLICT alır:** `ACTIVATE`
    başarıyla tamamlanır, `rowVersion` 5 → 6 olur. `ACTIVATE` öncesinde `rowVersion=5`
    ve `If-Match-Row-Version: 5` ile başlatılmış gecikmiş bir `SUSPEND` isteği kilit
    sonrası `rowVersion`'ı 6 olarak okur → `If-Match-Row-Version` eşleşmez →
    `409 VERSION_CONFLICT`. Güncel `rowVersion=6` ve `If-Match-Row-Version: 6` ile
    gönderilen yeni `SUSPEND` ise başarılı olur.

### 14.8. Platform admin dar RLS doğrulaması (33–35)

33. **Destek bayrağı kurulmadan actor-only SELECT çalışır:** Platform admin hedef kurumlu
    bir işlem başlatır. `app.iam_platform_admin_support_access` henüz kurulmamışken
    `platform_administrators` üzerinde `ORGANIZATION` scope `SELECT` RLS policy'si
    yalnızca `app.iam_operation_scope`, `app.iam_actor_user_id`, `revoked_at IS NULL`
    ve allow-listli `app.iam_operation_code` koşullarıyla çalışır; destek bayrağına
    bağımlı değildir. `SELECT` başarıyla bir satır döndürür → bayrak `true` yapılır →
    işlem devam eder.

34. **Başka platform admin satırı görünmez:** Aktif platform yöneticisi A, hedef kurumlu
    bir `PATCH` işleminde `platform_administrators` `ORGANIZATION` scope policy'si
    üzerinden `SELECT` yapar. Policy `app.iam_actor_user_id = platform_administrators.
    user_id` koşuluyla yalnızca kendi satırını açar; başka bir platform yöneticisi B'nin
    `user_id` farklı olduğu için satırı görünmez. Genel `SELECT` veya `BYPASSRLS` yoktur.

35. **Allow-list dışı operation code SELECT reddedilir:** `app.iam_operation_code =
    'HAYALI_ORG_DELETE'` gibi allow-list dışı bir değerle `ORGANIZATION` scope'unda
    `platform_administrators` SELECT'i yapılır. Policy'nin `app.iam_operation_code IN
    (...)` guard'ı `false` olur → hiçbir satır dönmez → doğrulama başarısız →
    `403 FORBIDDEN`. `app.iam_platform_admin_support_access` bayrağı kurulmaz.

### 14.9. İşlem sıralaması (36–39)

36. **Operation code kurulmadan SELECT satır döndürmez:** Transaction içinde
    `app.iam_operation_code` `SET LOCAL` yapılmadan (varsayılan boş dize)
    `platform_administrators` SELECT'i yapılır. RLS policy'sinin
    `app.iam_operation_code IN (...)` guard'ı `false` olur → hiçbir satır dönmez.
    `app.iam_operation_code` doğru allow-list değerine kurulmadan admin doğrulaması
    başarılı olamaz.

37. **Allow-listli server-set operation code ile yalnız actor admin satırı görünür:**
    `app.iam_operation_code = 'ORG_SUSPEND'` (allow-list'te) ile `ORGANIZATION`
    scope'unda `platform_administrators` SELECT'i yapılır. RLS `USING` predicate'i
    `app.iam_actor_user_id = user_id AND revoked_at IS NULL` ile yalnızca çağıran
    aktörün kendi aktif admin satırını döndürür. Başka admin satırı görünmez.

38. **Başarılı SELECT sonrasında support flag kurulur:** Adım 37'deki `SELECT`
    başarıyla bir satır döndürür → sunucu `app.iam_platform_admin_support_access =
    true` kurar → hedef kurum tablolarına erişim (örn. `organizations` üzerinde
    `ORGANIZATION` RLS policy'si) bu bayrak sayesinde açılır → işlem tamamlanır.

39. **Support flag SELECT'ten önce kurulamaz:** Sunucu kod yolu,
    `platform_administrators` `SELECT`'inden önce `app.iam_platform_admin_support_access`
    bayrağını kurmaya çalışır. Bu bir sözleşme ihlalidir: bayrak §2.2 adım 5'te
    tanımlandığı gibi yalnızca başarılı admin `SELECT` doğrulamasından sonra
    kurulabilir. Test, bayrağın SELECT'ten önce kurulduğu kod yolunun olmadığını
    statik analiz veya assertion ile doğrular.

### 14.10. Audit fail-closed (40–44)

40. **CREATE audit başarısızlığı rollback:** Platform admin `POST /organizations` yapar.
    `organizations` `INSERT`'i başarılı olur ancak `audit_logs` `INSERT`'i başarısız
    olur (ör. DB hatası). Transaction rollback yapılır; kurum oluşturulmaz;
    idempotency sonucu terminal başarıya dönüşmez. İstemciye `500 INTERNAL_ERROR`
    döner. Aynı `Idempotency-Key` ile yeniden deneme yeni transaction başlatır.

41. **Platform admin PATCH audit başarısızlığı rollback:** Platform admin hedef kurumlu
    `PATCH` yapar. `organizations` `UPDATE`'i başarılı olur ancak audit başarısız olur.
    Transaction rollback; kurum güncellenmez; `500 INTERNAL_ERROR`.

42. **Kurum aktörü PATCH audit başarısızlığı rollback:** Kurum yöneticisi kendi kurumuna
    `PATCH` yapar. Audit başarısız olur → rollback → `500 INTERNAL_ERROR`.
    Platform admin ve kurum aktörü PATCH yollarının ikisi de kapsanmıştır.

43. **SUSPEND audit başarısızlığı rollback:** `SUSPEND` işleminde audit başarısız olur.
    Transaction rollback; `status` değişmez; `session_generation` artmaz;
    `refresh_token_families`/`refresh_tokens` iptal edilmez; idempotency sonucu
    terminal başarıya dönüşmez. `500 INTERNAL_ERROR`.

44. **ARCHIVE audit başarısızlığı rollback:** `ARCHIVE` işleminde audit başarısız olur.
    Transaction rollback; `status` değişmez; oturum iptalleri gerçekleşmez.
    `500 INTERNAL_ERROR`.

45. **ACTIVATE audit başarısızlığı rollback:** `ACTIVATE` işleminde audit başarısız olur.
    Transaction rollback; `status` `SUSPENDED`'da kalır; `rowVersion` artmaz; eski
    oturum durumu değişmez; idempotency sonucu terminal başarıya dönüşmez.
    `500 INTERNAL_ERROR`.

### 14.11. GLOBAL admin doğrulaması (46–49)

46. **GLOBAL CREATE operation code allow-list:** Route'tan `ORG_CREATE` çözülür.
    Allow-list'te olduğu için transaction açılır. İstemci `ORG_CREATE` allow-list'te
    olduğu için reddedilmez; mevcut metin CREATE'ı engellemez.

47. **GLOBAL scope platform_administrators SELECT:** `app.iam_operation_scope = 'GLOBAL'`,
    `app.iam_operation_code = 'ORG_CREATE'` ile `platform_administrators` SELECT'i
    yapılır. RLS `USING` predicate'i yalnızca `app.iam_actor_user_id = user_id AND
    revoked_at IS NULL` koşuluyla çağıran aktörün kendi aktif admin satırını döndürür.
    `app.iam_platform_admin_support_access` predicate'te yer almaz; `GLOBAL` scope'ta
    bu bayrak kullanılmaz.

48. **GLOBAL scope platform_administrator_profiles görünmez:** `GLOBAL` scope policy'si
    yalnızca `platform_administrators` tablosunu kapsar; `platform_administrator_profiles`
    `GLOBAL` scope'ta hiçbir policy tarafından açılmaz. `SELECT * FROM
    platform_administrator_profiles` `GLOBAL` scope'ta boş küme döner.

49. **ORG_LIST GLOBAL doğrulaması:** Platform admin `GET /organizations` yapar.
    `app.iam_operation_scope = 'GLOBAL'`, `app.iam_operation_code = 'ORG_LIST'` ile
    `platform_administrators` SELECT'i başarılı olur (actor-only, `revoked_at IS NULL`).
    `app.iam_platform_admin_support_access` kullanılmaz. Başka platform admin satırı
    görünmez. Allow-list dışı operation code (örn. `ORG_CREATE` ile `LIST` route'u)
    `403 FORBIDDEN` döner.

## 15. Kaynaklarla uyum kontrolü

- `URUN_VE_UYGULAMA_PLANI.md` §4.2: kurum oluşturma platform yöneticisine ait → uyumlu.
- `URUN_VE_UYGULAMA_PLANI.md` §5.1: durum yönetimi platform yöneticisinde → uyumlu.
- `YETKI_MATRISI.md` KURUM-01: marka ayarları devredilebilir → uyumlu.
- `VERI_MODELI.md` §5.1: `organizations` tablo yapısı → uyumlu.
- `VERI_MODELI.md` §4.5/§4.11: `session_generation`, `refresh_token_families`,
  `refresh_tokens` → uyumlu.
- `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`: `CONTEXT_ACTIVATE`, `SESSION_REFRESH`,
  session scope modeli → uyumlu.
- `API_GENEL_KURALLARI.md`: sayfalama, hata zarfı, idempotency, PATCH/POST → uyumlu.
- `DENETIM_VE_GERI_ALMA_ILKELERI.md`: arşivleme, denetim → uyumlu.

## 16. Kapsam dışı kararlar

- Logo, marka renkleri (`organization_brand_colors`), modül yönetimi → `ORG-002`.
- `primaryColor` güncellemesi → `ORG-002`.
- Kurum yöneticisi atama → `STAFF-*`.
- Arşivlenmiş kurum geri yükleme → V1'de desteklenmez.
- `SUSPENDED`/`ARCHIVED` kurum üyelerine salt-okunur erişim → açık ürün kararı bekler.
- Sayfa boyutu üst sınırı, oran sınırlama eşikleri → `ORG-003`/`ORG-004`.
