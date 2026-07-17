# IAM Cihaz ve Oturum İptali API Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | IAM-002 — Cihaz ve oturum iptali sözleşmesini yaz |
| Belge sürümü | 1.5 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlı sözleşmeler | `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`, `API_GENEL_KURALLARI.md`, `VERI_MODELI.md`, `YETKI_MATRISI.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md`, `A004R2_COGNITO_IPTAL_VE_UZLASTIRMA_KANITI.md` |
| Son güncelleme | 17 Temmuz 2026 (v1.5 — `trusted_devices` `SELECT`/`INSERT`/`UPDATE` predicate'lerine açık scope+operation guard'ı eklendi (özellikle `GLOBAL SELECT` ve iki revoke `UPDATE` policy'si), `revoked_at` yazımı `transaction_timestamp()`e pinlendi, hatalı "column-level privilege timestamp bütünlüğünü sağlar" iddiası düzeltildi; v1.4 — geçersiz RLS `NEW`/`OLD` sözdizimi kaldırıldı, `trusted_devices` değişmez kolonları column-level `GRANT`le korunuyor, `SELECT` policy `OR` semantiği düzeltildi; v1.3 — dört fazlı keşif→kilit→yeniden-okuma→karar akışı; v1.2 — dar `PROVIDER_TOKEN_EXCHANGE` `SELECT` policy'si ve mantıksal cihaz kilidi; v1.1 — cihaz-bazlı reauth bariyeri, ADR-004 RLS hizalaması, eşzamanlılık, replay ve audit tekilliği düzeltmeleri) |

---

## 1. Amaç ve kapsam

Bu belge, `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`nin (IAM-001) kasıtlı olarak dışarıda bıraktığı üç
yüzeyi tanımlar: kullanıcının **kendi** güvenilir cihazlarını görüntülemesi/kaldırması, bir kurum
yöneticisinin (veya yetkilendirilmiş bir hocanın) **aynı kurumdaki başka bir kullanıcının** kurum
kapsamlı oturumunu iptal etmesi ve platform yöneticisinin destek amaçlı, kuruma bağlı olmayan
**tek cihaz** iptali. IAM-001 §1, §4 ve §18'de "bunlar IAM-002 sahibidir" diye işaretlenen üç
kalem — kurum kapsamlı cihaz/oturum iptali, global cihaz kaldırma ve cihaz yönetimi listesi — bu
belgeyle kapatılır.

Bu sözleşme:

- Kullanıcının kendi oturumundan çıkışını (`POST /api/v1/iam/sessions/logout`) yeniden tanımlamaz;
  bu uç `IAM-001` sahibidir ve değişmeden kalır.
- Cognito/sağlayıcı tarafında tetiklenen tam hesap devre dışı bırakma, global sign-out ve olay
  kaybı uzlaştırmasını (`iam_provider_commands.command_type = USER_DISABLE` ve ilişkili
  reconciliation akışı) tanımlamaz; bu, `A004R2_COGNITO_IPTAL_VE_UZLASTIRMA_KANITI.md`'de deneyle
  kanıtlanmış ve `IAM-004`/ilgili provider-command görevlerine ait bir akıştır.
- Parola sıfırlama, hoca hesabı oluşturma/kapatma ve personel yönetimi izinlerinin verilmesini
  tanımlamaz; bunlar `STAFF-*` ve `PERM-*` görevlerindedir. Bu belge yalnızca `DEVICE_SESSION_
  REVOKE` iznini **tüketir** (var olan iznin API üzerindeki etkisini tanımlar), izni veren/geri
  alan akışı tanımlamaz.
- Web/masaüstü oturum yönetimini tanımlamaz; V1 yalnızca mobil istemcidir.
- IAM-001'in `PROVIDER_TOKEN_EXCHANGE` davranışını yeniden yazmaz; yalnız §11'de tanımlanan
  cihaz-bazlı reauth bariyerini o uca **ek bir kısıt** olarak bağlar (bkz.
  `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §7.3/§7.4, bu belgeyle küçük ve gerekçeli bir ekleme
  görmüştür — bölüm 18).

## 2. Bağlayıcı karar özeti

- Üç ayrı ve birbirini etkilemeyen iptal kapsamı vardır: **cihaz** (bir `trusted_devices`
  satırının bütün kurum bağlamlarını kapatır), **kurum oturumu** (`DEVICE_SESSION_REVOKE` — yalnız
  hedef `organization_membership_id`'nin ailelerini kapatır, cihaz güvenilirliğine dokunmaz) ve
  **platform geneli tek cihaz** (yalnız platform yöneticisinin destek erişiminde, delegasyona
  kapalı). Bu ayrım `A004R2_COGNITO_IPTAL_VE_UZLASTIRMA_KANITI.md`'de "Tek cihaz, kurum ve global
  iptal kapsamları birbirinden ayrıldı" kabul ölçütüyle deneysel olarak doğrulanmıştır.
- Kendi cihazını yönetme/kaldırma her aktörün (platform yöneticisi, kurum yöneticisi, hoca)
  varsayılan ve devredilemez hakkıdır (`YETKI_MATRISI.md` §3.3, `ORTAK-01`); ayrı izin gerekmez.
- Kurum kapsamlı `DEVICE_SESSION_REVOKE`, kurum yöneticisi için varsayılan **açık**, hoca için
  varsayılan **kapalı**dır; hoca yalnız kendi kurumu için verilmiş ayrı ve geri alınabilir
  `DEVICE_SESSION_REVOKE` izniyle kullanabilir (`YETKI_MATRISI.md` §3.3, 13 Temmuz 2026 onaylı
  karar 7). Platform yöneticisi aynı işlemi yalnız destek amaçlı erişimde yapabilir; bu ayrı ve
  kalıcı bir rol tanımlamaz, dar ve denetlenen bir sunucu-taraflı geçişle açılır (bkz. §4.2).
- `DEVICE_SESSION_REVOKE`nin kapsamındaki "bütün cihaz oturumları" ifadesi yalnız **işlemi yapan
  kurumun bağlamında açılmış** oturumları kapsar; hedef kullanıcının başka kurumdaki üyelik
  bağlamındaki oturumları etkilenmez (`VERI_MODELI.md` §4.11, `YETKI_MATRISI.md` §3.3 netleştirme
  notu). Bir kullanıcının gerçekten **bütün** kurum bağlamlarındaki oturumlarının aynı anda
  kapatılması yalnız kendi cihaz iptaliyle veya platform yöneticisinin tek-cihaz global
  yeteneğiyle mümkündür.
- Cihaz iptali (kendi veya platform yöneticisi) `trusted_devices.revoked_at`i doldurur ve o
  cihaza bağlı **her** `refresh_token_family`i (kurum aileleri + varsa global platform yöneticisi
  ailesi) iptal eder; `session_generation` veya `reauthentication_required_after` **değiştirmez**
  — bu alanlar yalnız kurum/hesap düzeyinde anlamlıdır, cihaz düzeyinde değil.
- Kurum kapsamlı `DEVICE_SESSION_REVOKE`, hedef `organization_memberships.session_generation`ı
  artırır ve `reauthentication_required_after`ı işlem zamanına yükseltir; yalnız o üyeliğin
  ailelerini/tokenlarını iptal eder, `trusted_devices`e dokunmaz.
- Hiçbir uç `users.reauthentication_required_after`ı değiştirmez; global hesap düzeyinde
  yeniden kimlik doğrulama eşiği yalnız `IAM-004` kapsamındaki hesap güvenliği olaylarının
  (devre dışı bırakma, sağlayıcı olay kaybı uzlaştırması) sahipliğindedir.
- **Cihaz-bazlı reauth bariyeri (yeni, bağlayıcı):** Yukarıdaki üç eşiğin (`users`,
  `organization_memberships`, cihaz düzeyi) hiçbiri diğerinin yerine geçmez. `DEVICE_SELF_REVOKE`
  ve `PLATFORM_DEVICE_REVOKE` hesap/üyelik eşiklerini bilinçli olarak değiştirmediği için, aynı
  `(user_id, device_identifier)` çiftinin **yeniden** güven kazanması ayrı ve ek bir kontrole
  bağlanmıştır: yeni `trusted_devices` satırı yalnız doğrulanmış Cognito `auth_time`, o çiftin en
  son `revoked_at`ından **büyükse** açılabilir (bkz. §11, `VERI_MODELI.md` §4.10,
  `ADR-004`). Bu, `IAM-001`in `PROVIDER_TOKEN_EXCHANGE` ucuna bu belgenin eklediği tek davranış
  değişikliğidir.
- Bütün mutasyonlar `API_GENEL_KURALLARI.md` §7.2'deki idempotency kapsam kurallarını izler;
  hiçbiri yeni bir HTTP hata kodu icat etmez, yalnız `API_GENEL_KURALLARI.md` §5.2 kataloğundaki
  mevcut kodları kullanır.
- Aynı fiziksel bağlamda tekrarlanan iptal ikinci `session_generation` artışı üretmez
  (`A004R2_COGNITO_IPTAL_VE_UZLASTIRMA_KANITI.md` madde 5).
- **Replay ve audit tekilliği ayrı kararlardır (bkz. §12):** Aynı `Idempotency-Key` ile yapılan
  yeniden deneme her zaman ikinci audit/yan etki üretmeden ilk sonucun eşdeğerini döner. Farklı
  bir `Idempotency-Key` ile zaten terminal (iptal edilmiş) bir hedefe gelen istek ise ayrı bir
  mantıksal komuttur: durum mutasyonu yapmaz (idempotent no-op başarı) ama denetim bütünlüğü için
  yine **tam bir yeni audit satırı** yazar.

## 3. Aktörler ve yetki ön koşulları

### 3.1. Kendi cihazını yönetme (bölüm 7–8)

- Aktör: geçerli bir platform access tokenına sahip her kullanıcı — `ORGANIZATION` veya
  `GLOBAL_PLATFORM_ADMIN` kapsamlı oturum fark etmez.
- Ek izin gerekmez (`YETKI_MATRISI.md` §3.3 `ORTAK-01`).
- Hedef her zaman çağıranın **kendi** `user_id`'sine ait `trusted_devices` satırıdır; başka
  kullanıcının cihazı bu uçlarla asla görülemez veya değiştirilemez.

### 3.2. Kurum kapsamlı `DEVICE_SESSION_REVOKE` (bölüm 9)

- Aktör kurum yöneticisiyse: hedef `organizationId` çağıranın kendi aktif üyeliğinin kurumuyla
  aynı olmalıdır; ek izin gerekmez.
- Aktör hocaysa: hedef `organizationId` çağıranın kendi aktif üyeliğinin kurumuyla aynı olmalı
  **ve** çağıranın bu kurumdaki `TEACHER` rolüne bağlı, geri alınmamış `DEVICE_SESSION_REVOKE`
  iznine (`organization_membership_permissions`, bkz. `VERI_MODELI.md` §4.9) sahip olması
  gerekir.
- Aktör platform yöneticisiyse: `GLOBAL_PLATFORM_ADMIN` kapsamlı oturumla, **herhangi bir**
  `organizationId` için çağrılabilir (destek amaçlı erişim). Bu durumda normal `ORG_ADMIN`/
  `TEACHER` rol/izin kontrolü **bypass edilmez**; onun yerine sunucu, aktif
  `platform_administrators` kaydını doğruladıktan sonra dar bir kod yolunda server-set
  `app.iam_platform_admin_support_access=true` bayrağını kurar ve yetki `USING` koşulu bu bayrakla
  `OR` bağlanarak genişler (bkz. §4.2). İşlem her zaman denetlenir; aynı transaction ayrıca
  `PLATFORM_ADMIN_ORG_ACCESS` audit satırını üretir (`YETKI_MATRISI.md` §4.5).
- Hedef `organizationMembershipId`, yol parametresindeki `organizationId`ye ait olmalıdır;
  değilse istek reddedilir.

### 3.3. Platform geneli tek cihaz iptali (bölüm 10)

- Aktör yalnız aktif `platform_administrators` kaydına sahip kullanıcıdır ve isteği
  `GLOBAL_PLATFORM_ADMIN` kapsamlı oturumla yapmalıdır; `ORGANIZATION` kapsamlı oturumla (aktör
  aynı zamanda bir kurumda üye olsa bile) bu uca erişilemez.
- Bu yetenek delegasyona açık değildir; kurum yöneticisi veya hoca hiçbir izinle bu ucu
  kullanamaz.
- İşlem her zaman denetlenir.

## 4. Kaynaklar ve uçlar

Bu sözleşme dört IAM uç yüzeyini tanımlar:

1. `GET /api/v1/iam/devices`
2. `POST /api/v1/iam/devices/{deviceId}/revoke`
3. `POST /api/v1/iam/organizations/{organizationId}/memberships/{organizationMembershipId}/session-revoke`
4. `POST /api/v1/iam/platform-admin/users/{userId}/devices/{deviceId}/revoke`

`POST /api/v1/iam/sessions/logout` (kendi oturumundan çıkış) bu belgeye ait değildir;
`IAM-001` sahibidir ve değişmeden kalır. `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §4'ün taslak
olarak andığı tekil `/api/v1/iam/sessions/revoke` yolu bağlayıcı değildir; üç farklı aktör ve
kapsamı (kendi cihazı, kurum üyeliği, platform geneli cihaz) tek ve aşırı yüklenmiş bir yolda
karıştırmamak için burada üç ayrı, açık kaynak yoluna somutlaştırılmıştır. Bu bir davranış
değişikliği değil, IAM-001'in "IAM-002 ile tanımlanacaktır" dediği yüzeyin bu belgede
netleştirilmesidir.

### 4.1. İşlem, scope ve transaction matrisi

| İşlem | Transaction scope | Tablolar | SQL işlemleri |
|---|---|---|---|
| `DEVICE_LIST` | `IAM_AUTH` (salt okunur) | `trusted_devices` | Çağıranın `user_id`sine ait, `revoked_at IS NULL` satırlarda `SELECT` |
| `DEVICE_SELF_REVOKE` | `IAM_AUTH` | `trusted_devices`, `refresh_token_families`, `refresh_tokens`, `idempotency_keys`, `audit_logs` | Mantıksal cihaz kilidi (`user_id`+`device_identifier`, §12.0); `SELECT`+kilit cihaz (`user_id`=aktör, `id`=`app.iam_target_device_id`); koşullu `UPDATE` cihaz `revoked_at`; kilit+yeniden oku+`UPDATE` bağlı aile/token `revoked_at`; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` audit |
| `DEVICE_SESSION_REVOKE` | `ORGANIZATION` | `organization_memberships`, `organization_membership_roles`, `organization_membership_permissions`, `refresh_token_families`, `refresh_tokens`, `idempotency_keys`, `audit_logs` | `SELECT` yetki/üyelik; kilit+yeniden oku+`UPDATE` hedef aile/token `revoked_at`; koşullu `UPDATE` üyelik `session_generation`+`reauthentication_required_after`; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` audit (+ destek erişiminde `PLATFORM_ADMIN_ORG_ACCESS` audit) |
| `PLATFORM_DEVICE_REVOKE` | `GLOBAL` | `platform_administrators`, `trusted_devices`, `refresh_token_families`, `refresh_tokens`, `idempotency_keys`, `audit_logs` | `SELECT` admin doğrulama; mantıksal cihaz kilidi (`user_id`+`device_identifier`, §12.0); `SELECT`+kilit cihaz; koşullu `UPDATE` cihaz `revoked_at`; kilit+yeniden oku+`UPDATE` bağlı aile/token `revoked_at`; `INSERT`/koşullu `UPDATE` idempotency; `INSERT` audit |

Bu matrisin dışındaki tablo erişimi sözleşme ihlalidir. `DEVICE_SESSION_REVOKE`, `trusted_devices`e
hiçbir koşulda yazmaz (bu nedenle mantıksal cihaz kilidini almaz); `DEVICE_SELF_REVOKE` ve
`PLATFORM_DEVICE_REVOKE`, hedef üyeliğin `session_generation`/`reauthentication_required_after`
alanlarına hiçbir koşulda yazmaz. `IAM-001`in `PROVIDER_TOKEN_EXCHANGE`i de aynı mantıksal cihaz
kilidini alır (bkz. §11, §12.0) — bu tabloya dahil değildir çünkü kendi transaction'ı IAM-001
sahipliğindedir, ancak kilit mekanizması bu üç işlemle paylaşılır. "Kilit+yeniden oku" ifadesi
§12'deki eşzamanlılık kuralına atıfta bulunur: mantıksal cihaz kilidinden sonra satır kilidi
alınır, ardından aktif durum/`MAX(revoked_at)` yeniden okunur, karar buna göre verilir.

### 4.2. SET LOCAL değişkenleri ve ADR-004 hizalaması

`ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` "IAM-002 — Cihaz ve oturum iptali `iam_runtime`/RLS
eklentisi" bölümü bu belgenin dört işlem koduna karşılık gelen gerçek `iam_runtime` tablo × scope ×
SQL işlemi × `USING`/`WITH CHECK` satırlarını tanımlar. Bu bölüm o hizalamanın özetidir; çelişki
olursa ADR-004 bağlayıcıdır.

- `app.iam_target_device_id` — `DEVICE_SELF_REVOKE` (`IAM_AUTH`) ve `PLATFORM_DEVICE_REVOKE`de
  (`GLOBAL`) hedef `trusted_devices.id`; bu **bilinen bir yol parametresidir** (keşfedilmiş bir
  değer değildir), bu yüzden sunucu Faz A keşif sorgusundan önce `SET LOCAL` yapabilir — ama bu
  değişkeni kullanan `UPDATE`in kendisi yalnız Faz D'de (mantıksal kilit + Faz C yeniden okuma
  sonrası) çalışır (bkz. §12.0a). Diğer işlemlerde zorunlu olarak boştur.
- `app.iam_provider_device_identifier` — yalnız `PROVIDER_TOKEN_EXCHANGE`te sunucunun doğrulanmış
  istek gövdesindeki `deviceIdentifier` alanından kurduğu server-set değer; `trusted_devices`
  `SELECT`/`INSERT` policy'lerinin **dar** eşleşme anahtarıdır (yalnız aynı `user_id` + aynı bu
  değer — başka kullanıcı veya başka `device_identifier` hiçbir koşulda görünmez). Diğer
  işlemlerde zorunlu olarak boştur.
- `app.iam_verified_auth_time` — yalnız `PROVIDER_TOKEN_EXCHANGE`in doğruladığı Cognito
  `auth_time`i taşır; cihaz-bazlı reauth bariyerinin `WITH CHECK`i tarafından, mantıksal cihaz
  kilidi altında yeniden okunan `MAX(revoked_at)`e karşı değerlendirilir (bkz. §11, §12.0).
  İstemciden kurulamaz.
- `app.iam_platform_admin_support_access` — yalnız platform yöneticisinin aktif
  `platform_administrators` kaydı sunucu tarafında doğrulandıktan sonra `DEVICE_SESSION_REVOKE`
  için server-set edilen boolean; normal `ORG_ADMIN`/`TEACHER` yolunda her zaman `false`/boştur.
  İstemci parametresiyle kurulamaz.
- `app.iam_target_membership_id`/`app.iam_target_organization_id` — `DEVICE_SESSION_REVOKE`de
  `CONTEXT_ACTIVATE` ile aynı isimlendirmeyi paylaşır (IAM-001'de zaten tanımlı); yeni bir
  değişken eklenmez, yeniden kullanılır.
- **Mantıksal cihaz kilidi** — `PROVIDER_TOKEN_EXCHANGE`, `DEVICE_SELF_REVOKE` ve
  `PLATFORM_DEVICE_REVOKE`, `trusted_devices`e **güvenlik-relevant** her erişimden **önce**
  aynı `(user_id, device_identifier)` mantıksal anahtarında bir transaction-scoped
  `pg_advisory_xact_lock` alır (§12.0); satır hiç yokken de çalışır ve üç işlemi birbirine göre
  tam sıralı hâle getirir. `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE`, mantıksal anahtarı yol
  parametresinden değil dar bir Faz A keşif sorgusundan öğrenir (§12.0a). Tam ifade `ADR-004`tedir.
- Bütün diğer `iam_runtime`/`FORCE RLS`/kapsam izolasyonu kuralları (`ORGANIZATION`, `GLOBAL`,
  `IAM_AUTH` birbirine karışamaz; eksik değer reddedilir; owner/superuser/BYPASSRLS yasağı)
  `ADR-004`'te değişmeden geçerlidir. **Reddedilen alternatif:** cihaz-bazlı bariyerin eski
  `revoked_at` geçmişini görmesi ihtiyacı, `iam_runtime`e genel `SELECT` yetkisi açarak, `BYPASSRLS`
  vererek veya RLS'i atlayan bir `SECURITY DEFINER` fonksiyonuyla çözülmez; tek kabul edilen yol
  yukarıdaki dar `app.iam_provider_device_identifier` eşleşmeli policy'dir.

## 5. Ortak istek/cevap kuralları

- `API_GENEL_KURALLARI.md` bölüm 3, 4, 5 ve 7 bu belge için aynen geçerlidir.
- `POST` komutlarının tamamı `Idempotency-Key` ister; `GET /api/v1/iam/devices` istemez.
- `DEVICE_SESSION_REVOKE`, standart kurum kapsamlı idempotency kullanır: DB `UNIQUE` kapsamı
  `organizationId + actorUserId + clientMutationId` (`idempotency_keys_organization_scope_uq`,
  `API_GENEL_KURALLARI.md` §7.2); `request_fingerprint` ayrıca hedef `organizationMembershipId`yi
  kapsar.
- `PLATFORM_DEVICE_REVOKE`, standart global idempotency kullanır: DB `UNIQUE` kapsamı
  `actorUserId + clientMutationId` (`idempotency_keys_global_scope_uq`); `request_fingerprint`
  ayrıca hedef `userId` ve hedef `deviceId`yi kapsar — aynı anahtarın farklı hedef kullanıcı/
  cihazla yeniden kullanılması `409 IDEMPOTENCY_KEY_REUSED` döner.
- `DEVICE_SELF_REVOKE`, `IAM_AUTH` kapsamını kullanır (`SESSION_REFRESH`/`SESSION_LOGOUT` ile
  aynı aile). DB `UNIQUE` kapsamı `idempotency_keys_iam_auth_scope_uq (user_id,
  client_mutation_id) WHERE scope_type='IAM_AUTH'`tir; bu, diğer bütün `IAM_AUTH` işlemleriyle
  **aynı** ve değişmeyen kısıttır (`VERI_MODELI.md` §14, `ADR-004`). Bunun **üzerine**,
  `request_fingerprint` kolonu şu bileşenleri tek bir opak özet olarak kapsar: `operationType`
  (`DEVICE_SELF_REVOKE`), çağıranın isteği doğrulayan **kendi** `trusted_devices.id`si (çağıran
  cihaz), hedef `trusted_devices.id` (`deviceId`, iptal edilecek cihaz — çağıran cihazla aynı
  olabilir), çağıranın geçerli platform access tokenının fingerprint'i ve yöntem/yol. Bu altı
  bileşenden biri uyuşmazsa `409 IDEMPOTENCY_KEY_REUSED` döner (bkz. §12 replay sırası).
- `DEVICE_SELF_REVOKE`, `SESSION_LOGOUT` gibi replay escrow **kullanmaz**; cevap ham sır
  taşımadığından güvenli sonuç doğrudan `idempotency_keys.result_payload` eşdeğeri olarak
  güvenli biçimde tekrarlanabilir. Bu replay her koşulda standart `Authorization` doğrulamasından
  **sonra** çalışır; asla onu atlayıp iptal edilmiş bir tokenı yeniden yetkili göstermez (§12).
- Üç mutasyonun hiçbiri ham access/refresh token değeri döndürmez; bu nedenle hiçbiri escrow
  mekanizması gerektirmez.
- Bütün korunan uçlar `Authorization: Bearer <platform-access-token>` ister.
- IAM auth uçlarında rate limit zorunludur; aşıldığında `429 RATE_LIMITED` ve uygun olduğunda
  `Retry-After` döner.

### 5.1. Operasyon bazlı fingerprint özeti

| İşlem | Idempotency DB `UNIQUE` kapsamı | `request_fingerprint` girdisi | Escrow |
|---|---|---|---|
| `DEVICE_SELF_REVOKE` | `IAM_AUTH`: `(user_id, client_mutation_id)` | `operationType` + çağıranın platform access tokenı + çağıran `trusted_devices.id` + hedef `trusted_devices.id` (`deviceId`) + yöntem/yol | Kullanılmaz |
| `DEVICE_SESSION_REVOKE` | `ORGANIZATION`: `(organization_id, user_id, client_mutation_id)` | `organizationId` + `actorUserId` + hedef `organizationMembershipId` + yöntem/yol | Kullanılmaz |
| `PLATFORM_DEVICE_REVOKE` | `GLOBAL`: `(user_id, client_mutation_id)` | `actorUserId` + hedef `userId` + hedef `deviceId` + yöntem/yol | Kullanılmaz |

Bu tablo, `API_GENEL_KURALLARI.md` §7.2 ve `VERI_MODELI.md` §14'teki işlem bazlı
`requestTokenFingerprint`/`request_fingerprint` listeleriyle **aynı** ifadeyi kullanır; üçü
arasında ifade farkı kalmamıştır (bkz. §18).

## 6. Veri şekilleri

### 6.1. Cihaz nesnesi

```json
{
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z",
    "lastSeenAt": "2026-07-17T08:10:05Z",
    "revokedAt": null
  }
}
```

### 6.2. Sunucu alan kuralları

- `deviceName` kullanıcıya gösterilebilir alandır; yetki/kimlik kaynağı değildir.
- `lastSeenAt`, cihazın en son başarılı `SESSION_REFRESH` veya iş isteğinde bulunduğu zamandır;
  kesin güncelleme sıklığı operasyon ayarıdır ve bu belgede sayısal olarak sabitlenmez.
- `isCurrentDevice` yalnız `GET /api/v1/iam/devices` ve `POST
  /api/v1/iam/devices/{deviceId}/revoke` cevaplarında döner; sunucu bu değeri çağıranın **o
  anki** isteğini kimlik doğrulayan `refresh_token_families.trusted_device_id` ile karşılaştırarak
  hesaplar. İstemci bu alanı kendisi göndermez veya hesaplamaz.
- `revokedAt` dolu bir cihaz artık aktif değildir. Aynı `deviceIdentifier` ile yeni bir
  interaktif girişte **yeniden aktif** bir cihaz kaydı üretilmesi artık koşulsuz değildir: §11'deki
  cihaz-bazlı reauth bariyeri, yalnız doğrulanmış Cognito `auth_time` bu satırın (veya aynı çift
  için önceki satırların en sonuncusunun) `revoked_at`ından **büyükse** yeni satıra izin verir.
  Eski/eşit bir `auth_time` ile gelen deneme `403 REAUTHENTICATION_REQUIRED` ile reddedilir
  (`IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §7.4).

## 7. `GET /api/v1/iam/devices`

### 7.1. Amaç

Çağıranın kendi hesabına bağlı, hâlâ aktif (`revoked_at IS NULL`) güvenilir cihazlarını listeler.

### 7.2. İstek

Başlık:

- `Authorization: Bearer <platform-access-token>`

Sorgu parametreleri `API_GENEL_KURALLARI.md` §6'daki standart sayfalama parametreleridir
(`cursor`, `limit`). Ek filtre/sıralama alanı yoktur.

### 7.3. Başarılı cevap

`200 OK`

```json
{
  "items": [
    {
      "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
      "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
      "platform": "ANDROID",
      "deviceName": "Pixel 8",
      "trustedAt": "2026-07-16T09:22:31Z",
      "lastSeenAt": "2026-07-17T08:10:05Z",
      "isCurrentDevice": true
    }
  ],
  "page": { "nextCursor": null, "hasNextPage": false }
}
```

Davranış:

- Yalnız çağıranın kendi `user_id`sine ait ve `revoked_at IS NULL` satırlar döner.
- Sıralama deterministiktir: `trustedAt DESC`, eşitlikte `id ASC`.
- Bu uç herhangi bir tabloya yazmaz; idempotency gerektirmez.

### 7.4. Hata kuralları

- `401 UNAUTHENTICATED`: access token geçersiz, süresi dolmuş veya iptal edilmiş.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 8. `POST /api/v1/iam/devices/{deviceId}/revoke`

### 8.1. Amaç

Çağıranın kendi güvenilir cihazını tamamen kaldırır; bu cihaza bağlı bütün kurum ve (varsa)
global platform yöneticisi oturum ailelerini, hangi kurumda açılmış olurlarsa olsunlar, iptal
eder.

### 8.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`

Yol parametresi:

- `deviceId` — çağıranın kendi `trusted_devices.id` değeri.

Gövde yoktur.

### 8.3. Başarılı cevap

`200 OK`

```json
{
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "trustedAt": "2026-07-16T09:22:31Z",
    "revokedAt": "2026-07-17T10:02:11Z"
  },
  "isCurrentDevice": true,
  "revokedRefreshTokenFamilyCount": 2
}
```

Davranış:

- Sunucu önce standart `Authorization` doğrulamasını yapar (bu adım idempotency/replaydan
  **öncedir** ve onunla atlanamaz — bkz. §12); ardından aynı `Idempotency-Key` + aynı
  `request_fingerprint` için idempotent replay kontrolü yapar ve varsa ilk tamamlanmış cevabın
  eşdeğerini döner.
- **Faz A (keşif):** dar, kilitsiz, salt okunur bir sorgu, `deviceId`nin çağıranın kendi
  `user_id`sine ait olup olmadığını ve `device_identifier`ini öğrenir; bu sorgu `revoked_at`a
  bakmaz ve hiçbir karar vermez. Eşleşme yoksa `404 RESOURCE_NOT_FOUND` döner (bkz. §12.0a).
- **Faz B/C (kilit ve yeniden okuma):** Faz A'da öğrenilen `(user_id, device_identifier)` ile
  mantıksal cihaz kilidi alınır; aynı `deviceId` satırı `SELECT ... FOR UPDATE` ile yeniden
  okunur. Okunan satırın `user_id`/`device_identifier`i Faz A ile uyuşmuyorsa fail-closed `404
  RESOURCE_NOT_FOUND` döner (bkz. §12.0a).
- **Faz D (karar ve mutasyon):** Farklı bir `Idempotency-Key` ile gelen istek, Faz C'nin yeniden
  okuduğu satırı **zaten** `revoked_at` dolu bulursa: işlem no-op başarıyla sonuçlanır
  (`trusted_devices`/`refresh_token_families`e ikinci yazım yapılmaz, mevcut `revokedAt` aynen
  döner) **ama** bu farklı anahtar için yine tam bir yeni `audit_logs` satırı yazılır (bkz.
  §12.3/§13 — aynı-anahtar replayından kasıtlı olarak farklı bu davranış). Aksi hâlde aynı
  transaction içinde: `trusted_devices.revoked_at` işlem zamanına ayarlanır; bu
  `trusted_device_id`ye bağlı, henüz iptal edilmemiş bütün `refresh_token_families` (kurum
  aileleri + varsa global platform yöneticisi ailesi) ve onların `refresh_tokens` satırları
  `revoked_at` ile iptal edilir.
- Bu işlem `organization_memberships.session_generation` veya
  `reauthentication_required_after`ı **değiştirmez**; yalnız cihaz düzeyinde etki eder.
  `users.reauthentication_required_after`ı da değiştirmez (bkz. §2). Aynı `(user_id,
  device_identifier)` çiftinin yeniden güven kazanması §11'deki ayrı cihaz-bazlı bariyerle
  korunur.
- `isCurrentDevice=true` dönerse, çağıranın bu isteği kimlik doğrulayan aile de iptal edilenler
  arasındadır; bkz. bölüm 14 mobil yükümlülükleri ve §12 replay sırası (bu tokenla yapılacak bir
  sonraki istek artık `401 SESSION_REVOKED` alır).

### 8.4. Hata kuralları

- `401 UNAUTHENTICATED`: access token geçersiz, süresi dolmuş veya iptal edilmiş.
- `401 SESSION_REVOKED`: sunulan access tokenı bu isteğe ulaşmadan önce zaten iptal edilmiş
  (örn. önceki bir `isCurrentDevice=true` çağrısı tarafından); replay bu adımı atlayıp tokenı
  yeniden yetkili göstermez (§12).
- `404 RESOURCE_NOT_FOUND`: `deviceId` yok veya çağıranın kendi hesabına ait değil (varlığı
  açığa çıkarmamak için farklılaştırılmaz).
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı çağıran/hedef cihaz, token fingerprint veya
  istek özetiyle kullanılmış.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 9. `POST /api/v1/iam/organizations/{organizationId}/memberships/{organizationMembershipId}/session-revoke`

### 9.1. Amaç

`DEVICE_SESSION_REVOKE`i uygular: hedef kurum üyeliğinin, **yalnız o kurumun bağlamında**
açılmış bütün oturum ailelerini iptal eder. Cihaz güvenilirliğine veya kullanıcının başka
kurumdaki üyeliğine dokunmaz.

### 9.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`

Yol parametreleri:

- `organizationId` — hedef üyeliğin ait olduğu kurum.
- `organizationMembershipId` — iptal edilecek hedef üyelik; `organizationId`ye ait olmalıdır.

Gövde yoktur.

### 9.3. Başarılı cevap

`200 OK`

```json
{
  "organizationMembership": {
    "id": "5276942f-ef2f-44ca-9ef6-2af1dd58a0fc",
    "organizationId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
    "sessionGeneration": 4
  },
  "revokedRefreshTokenFamilyCount": 1,
  "reauthenticationRequiredAfter": "2026-07-17T10:05:00Z"
}
```

Davranış:

- Sunucu önce çağıranın yetkisini doğrular (bölüm 3.2 — kurum yöneticisi/yetkili hoca yolu veya
  platform yöneticisinin dar destek geçişi); sonra idempotent replay kontrolü yapar (aynı-anahtar
  ise ikinci yan etki/audit üretmeden ilk sonucu döner).
- `organizationMembershipId`, path'teki `organizationId`ye ait değilse `404
  RESOURCE_NOT_FOUND` döner.
- §12'deki kilit sırasıyla hedef üyelik satırı kilitlenir ve aktif aile durumu **yeniden okunur**.
  Kilit sonrası hâlâ iptal edilmemiş en az bir `refresh_token_family`i varsa: bu ailelerin ve
  bağlı `refresh_tokens`ın tamamı `revoked_at` ile iptal edilir, `organization_memberships.
  session_generation` bir artırılır ve `reauthentication_required_after` işlem zamanına
  yükseltilir — hepsi aynı transaction'da.
- Farklı bir `Idempotency-Key` ile gelen istek, kilit sonrası hedefi zaten iptal edilmiş/aktif
  ailesi yok bulursa: `session_generation` **ikinci kez artırılmaz** (`A004R2` madde 5), durum
  mutasyonu yapılmaz, ama yine tam bir yeni `audit_logs` satırı yazılır (bkz. §12/§13).
- Bu işlem `trusted_devices`e dokunmaz; hedef kullanıcının **başka bir kurumdaki** üyelik
  bağlamında açılmış oturumları etkilenmez.
- Platform yöneticisi destek amaçlı çağırdıysa, normal `ORG_ADMIN`/`TEACHER` yetki kontrolü
  server-set `app.iam_platform_admin_support_access=true` bayrağıyla genişler (bkz. §4.2); bu
  durumda `SESSION_REVOKED`-tipi audit'e ek olarak aynı transaction bir de
  `PLATFORM_ADMIN_ORG_ACCESS` audit satırı yazar.
- Denetim kaydı her zaman üretilir (`YETKI_MATRISI.md` §4.5).

### 9.4. Hata kuralları

- `401 UNAUTHENTICATED`: access token geçersiz, süresi dolmuş veya iptal edilmiş.
- `403 FORBIDDEN`: çağıran doğru kurumdadır ama `ORG_ADMIN` değildir ve geri alınmamış
  `DEVICE_SESSION_REVOKE` iznine sahip bir `TEACHER` de değildir (platform yöneticisi hariç).
- `404 RESOURCE_NOT_FOUND`: `organizationId` çağıranın kendi kurumu değildir (platform yöneticisi
  hariç) veya `organizationMembershipId` bu kuruma ait değildir/yoktur; varlığı açığa
  çıkarmamak için 403 yerine 404 döner.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı hedef üyelik veya istek özetiyle kullanılmış.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 10. `POST /api/v1/iam/platform-admin/users/{userId}/devices/{deviceId}/revoke`

### 10.1. Amaç

Platform yöneticisinin destek amaçlı, delegasyona kapalı global tek cihaz iptalini uygular:
hedef kullanıcının belirli bir cihazına bağlı bütün oturum ailelerini, hangi kurumda açılmış
olurlarsa olsunlar, iptal eder ve cihazı güvenilirlikten çıkarır.

### 10.2. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>` (yalnız `GLOBAL_PLATFORM_ADMIN` kapsamlı)
- `Idempotency-Key: <clientMutationId>`

Yol parametreleri:

- `userId` — hedef kullanıcı.
- `deviceId` — hedef cihaz; `userId`ye ait olmalıdır.

Gövde yoktur.

### 10.3. Başarılı cevap

`200 OK`

```json
{
  "device": {
    "id": "6ef04c06-351a-4ac9-b4a2-29652b213ad2",
    "deviceIdentifier": "c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "revokedAt": "2026-07-17T10:12:44Z"
  },
  "revokedRefreshTokenFamilyCount": 3
}
```

Davranış:

- Sunucu önce çağıranın aktif `platform_administrators` kaydını ve `GLOBAL_PLATFORM_ADMIN`
  oturum kapsamını doğrular; sonra idempotent replay kontrolü yapar.
- **Faz A (keşif):** dar, kilitsiz, salt okunur bir sorgu, `deviceId`nin path `userId`sine ait
  olup olmadığını ve `device_identifier`ini öğrenir; bu sorgu `revoked_at`a bakmaz ve hiçbir
  karar vermez. Eşleşme yoksa `404 RESOURCE_NOT_FOUND` döner (bkz. §12.0a).
- **Faz B/C (kilit ve yeniden okuma):** Faz A'da öğrenilen `(userId, device_identifier)` ile
  mantıksal cihaz kilidi alınır; aynı `deviceId` satırı `SELECT ... FOR UPDATE` ile yeniden
  okunur. Okunan satırın `user_id`/`device_identifier`i Faz A ile uyuşmuyorsa fail-closed `404
  RESOURCE_NOT_FOUND` döner.
- **Faz D (karar ve mutasyon):** Farklı bir `Idempotency-Key` ile gelen istek, Faz C'nin yeniden
  okuduğu satırı zaten iptal edilmiş bulursa: işlem no-op başarıyla sonuçlanır, ama yine tam bir
  yeni `audit_logs` satırı yazılır (bkz. §12.3/§13). Aksi hâlde aynı transaction içinde:
  `trusted_devices.revoked_at` işlem zamanına ayarlanır; bu cihaza **dar** kapsamlı `GLOBAL`
  policy'yle (yalnız bu `trusted_device_id`, kullanıcının diğer cihazları değil) bağlı, henüz
  iptal edilmemiş bütün `refresh_token_families` (herhangi bir kurum bağlamında veya global
  platform yöneticisi ailesi) ve `refresh_tokens` iptal edilir.
- Bu işlem `users.reauthentication_required_after`ı **değiştirmez** — bu, yalnız `IAM-004`
  kapsamındaki tam hesap güvenliği olaylarının (devre dışı bırakma, sağlayıcı olay uzlaştırması)
  etkilediği daha geniş bir eşiktir; bilinçli olarak burada genişletilmez. Aynı `(user_id,
  device_identifier)` çiftinin yeniden güven kazanması §11'deki ayrı cihaz-bazlı bariyerle
  korunur.
- Denetim kaydı `event_scope=GLOBAL`, `target_entity_type=USER`, `target_entity_id=userId` ile
  ve iptal edilen cihazın kimliği `event_metadata` içinde olacak şekilde her zaman üretilir.

### 10.4. Hata kuralları

- `401 UNAUTHENTICATED`: access token geçersiz, süresi dolmuş veya iptal edilmiş.
- `403 FORBIDDEN`: çağıran aktif platform yöneticisi değildir veya isteği
  `GLOBAL_PLATFORM_ADMIN` kapsamlı oturumla yapmamıştır.
- `404 RESOURCE_NOT_FOUND`: `userId` veya `deviceId` yok, ya da cihaz bu kullanıcıya ait değil.
- `409 IDEMPOTENCY_KEY_REUSED`: aynı anahtar farklı hedef kullanıcı/cihazla kullanılmış.
- `429 RATE_LIMITED`: hız sınırı aşıldı.

## 11. Zorunlu yeniden kimlik doğrulama etkisi

Üç bağımsız reauth eşiği vardır; biri diğerinin yerine geçmez:

- **Hesap düzeyi** (`users.reauthentication_required_after`) — bu belgedeki hiçbir uç
  değiştirmez; yalnız `IAM-004` kapsamındaki tam hesap güvenliği olayları (devre dışı bırakma,
  sağlayıcı olay uzlaştırması) değiştirir.
- **Üyelik düzeyi** (`organization_memberships.reauthentication_required_after`) —
  `DEVICE_SESSION_REVOKE` bunu işlem zamanına yükseltir. Bundan sonra o üyelik için yeni bir
  `CONTEXT_ACTIVATE` yalnız, doğrulanmış Cognito `auth_time`i bu eşiğin **sonrasında** olan
  **yeni** bir interaktif girişle mümkündür (`IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §9–§10,
  `VERI_MODELI.md` §4.11). Eski bir `contextSelectionToken` veya eski `auth_time` bu barajı
  aşamaz.
- **Cihaz düzeyi (yeni, bağlayıcı):** `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE`, yukarıdaki
  iki eşiği bilinçli olarak değiştirmez; bunun yerine `trusted_devices.revoked_at` doğrudan bir
  bariyer oluşturur. `PROVIDER_TOKEN_EXCHANGE`, `trusted_devices`e erişmeden **önce** aynı
  `(user_id, device_identifier)` mantıksal anahtarında bir transaction-scoped advisory lock alır
  (§12.0 — satır hiç yokken de serileştirir) ve bu kilit altında, yalnız aynı `user_id` + aynı
  `device_identifier`i kapsayan **dar** bir `FORCE RLS` `SELECT` policy'siyle (başka kullanıcı
  veya başka cihazın geçmişi hiçbir koşulda görünmez) aynı çiftin en son `revoked_at`ını yeniden
  okur. Yeni bir `trusted_devices` satırı yalnız doğrulanmış Cognito `auth_time` bu **kilit-sonrası**
  değerden **büyükse** açılabilir (`VERI_MODELI.md` §4.10, `ADR-004`). Bu kilit/dar-görünürlük
  kombinasyonu olmadan, iptal edilmiş bir cihaz hâlâ geçerli/eski bir tarayıcı SSO oturumuyla
  anında yeniden güven kazanabilir veya eşzamanlı bir iptalle yarışarak tutarsız bir sonuç
  üretebilirdi. İstemcinin `prompt=login` (veya Cognito Hosted UI'da eşdeğer zorunlu yeniden
  kimlik doğrulama parametresi) göndermesi **yalnız istemci tarafı yardımdır**; bağlayıcı sınır
  sunucunun bu karşılaştırmasıdır ve istemci tarafından atlanamaz. Eski/eşit `auth_time` ile
  yapılan deneme `403 REAUTHENTICATION_REQUIRED` ile reddedilir.
- Hâlâ geçerli bir access/refresh token ile yapılan bir istek, bu belgedeki iptallerden biri
  gerçekleştikten **sonra** sunulursa `401 SESSION_REVOKED` döner (mevcut IAM-001 §12–§13
  kontrolleri değişmeden uygulanır); bu belge yeni bir hata kodu tanımlamaz.

## 12. Eşzamanlılık, replay ve audit tekilliği kuralları

### 12.0. Mantıksal cihaz kilidi (satır bulunmasa bile serileştirir)

- `trusted_devices` satır kilidi (`SELECT ... FOR UPDATE`), yalnız satır **var olduğunda**
  çalışır. `PROVIDER_TOKEN_EXCHANGE` aynı `(user_id, device_identifier)` çifti için **ilk kez**
  çalıştığında kilitlenecek satır yoktur; bu durumda partial `UNIQUE (user_id, device_identifier)
  WHERE revoked_at IS NULL` kısıtı tek başına yeterli değildir — yalnız "aynı anda iki aktif
  satır" oluşmasını engeller, ama bir tarafın güncel olmayan bir `MAX(revoked_at)` okumasıyla
  `auth_time` kontrolünü geçip commit olmasını engellemez.
- Bu nedenle `PROVIDER_TOKEN_EXCHANGE`, `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE`,
  `trusted_devices`e **güvenlik-relevant** her erişimden **önce** aynı mantıksal anahtarda
  transaction-scoped bir advisory lock alır (örn. `pg_advisory_xact_lock(hashtext('trusted_device:'
  || user_id::text), hashtext(device_identifier::text))`); işlevsel olarak eşdeğer başka bir
  güvenli kilit mekanizması da kabul edilir, ancak üç işlem **aynı** anahtar üretim fonksiyonunu
  kullanmak zorundadır. Bu kilit satırın var olup olmadığından bağımsız çalışır, transaction
  sonunda otomatik serbest kalır ve üç işlemi aynı mantıksal cihaz için birbirine göre **tam
  sıralı** hâle getirir. **Düzeltilmiş kural:** "her erişimden önce kilit" mutlak değildir; yalnız
  aşağıdaki dar Faz A keşif sorgusuna kilitsiz izin verilir — bütün güvenlik-relevant okumalar ve
  her `INSERT`/`UPDATE`, kilit **sonrasında** yapılır.
- Bu mekanizma `ADR-003` §5.3'teki ayrı `iam_runtime` rolü kararını veya dar `FORCE RLS`
  policy modelini gevşetmez; genel `SELECT` yetkisi, `BYPASSRLS` veya RLS'i atlayan bir
  `SECURITY DEFINER` fonksiyonu **kullanılmaz** — tam ifade `ADR-004`tedir.

### 12.0a. İki fazlı akış — `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE` mantıksal anahtarı nasıl öğrenir

`PROVIDER_TOKEN_EXCHANGE`, mantıksal anahtarın iki bileşenini de (`user_id` doğrulanmış
aktörden, `device_identifier` istek gövdesinden) kilitten **önce** zaten bilir; doğrudan §12.0'daki
kilide geçer. `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE` ise yol parametresinde yalnız
`deviceId` (`trusted_devices.id`) alır — `device_identifier` **bilinmez**. Sunucu bunu öğrenmeden
kilidi üretemeyeceğinden bu iki işlem dört fazlı çalışır:

- **Faz A — keşif (kararsız, salt okunur, kilitsiz):** `DEVICE_SELF_REVOKE`te `SELECT id, user_id,
  device_identifier FROM trusted_devices WHERE user_id = app.iam_actor_user_id AND id =
  :pathDeviceId`; `PLATFORM_DEVICE_REVOKE`te `SELECT id, user_id, device_identifier FROM
  trusted_devices WHERE user_id = app.iam_target_user_id AND id = :pathDeviceId`. Bu sorgu
  **yalnız** değişmez `user_id`/`device_identifier` alanlarını okur; `revoked_at` durumuna
  bakmaz, hiçbir mutasyon/terminal-durum/başarı kararı vermez ve `SELECT ... FOR UPDATE` **değildir**.
  Eşleşme yoksa (0 satır) istek burada `404 RESOURCE_NOT_FOUND` ile biter.
- **Faz B — mantıksal kilit:** Faz A'da keşfedilen `(user_id, device_identifier)` ile §12.0'daki
  `pg_advisory_xact_lock` alınır.
- **Faz C — kilit-sonrası yeniden okuma ve doğrulama:** aynı `deviceId` satırı `SELECT ... FOR
  UPDATE` ile yeniden okunur; okunan satırın `user_id`si **hâlâ** beklenen kullanıcıyla ve
  `device_identifier`i Faz A'da keşfedilen değerle eşleşmiyorsa istek fail-closed `404
  RESOURCE_NOT_FOUND` ile durur (`device_identifier` immutable olduğundan — `VERI_MODELI.md`
  §4.10 — bu normal akışta hiç tetiklenmez; savunma amaçlı bir doğrulamadır).
- **Faz D — karar ve mutasyon:** `revoked_at` kontrolü, no-op/terminal karar, `UPDATE` ve bağlı
  `refresh_token_families`/`refresh_tokens` iptalleri **yalnız** bu noktadan sonra, Faz C'nin
  yeniden okuduğu satıra dayanarak yapılır.

`app.iam_target_device_id`, yol parametresinden (bilinen, keşfedilmemiş bir değer olduğu için)
Faz A'dan önce `SET LOCAL` yapılabilir; ama bu değişkeni kullanan `UPDATE`in kendisi yalnız Faz
D'de çalışır (bkz. §4.2).

### 12.1. Kilit sırası ve yeniden okuma

- §12.0'daki mantıksal cihaz kilidi (Faz B) **yalnız** `PROVIDER_TOKEN_EXCHANGE`,
  `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE` için zorunludur — bu üçü `trusted_devices`in
  kimlik/güvenilirlik durumunu değiştirir. `CONTEXT_ACTIVATE` ve `PLATFORM_ADMIN_ACTIVATE`
  (IAM-001), `trusted_devices`e hiç yazmaz ve satırını kilitlemez (yalnız
  `context_selection_tokens`in `EXISTS (SELECT ...)` `USING` koşuluyla salt okunur biçimde
  referans alır); bu iki işlem mantıksal cihaz kilidini **almaz**, kilit sırasına adım (1)'den
  (kendi tablo matrisine göre `organization_memberships`/`refresh_token_families`/
  `refresh_tokens`) başlar. Aşağıdaki numaralı sıra, kilidi **alan** işlemler için geçerlidir;
  bütün aktivasyonların adım (0)'ı aldığı anlamına gelmez.
- Kilidi alan işlemler için sıra **bağlayıcı ve sabittir**: (0) mantıksal cihaz kilidi (Faz B),
  (1) `trusted_devices` (varsa; `PROVIDER_TOKEN_EXCHANGE`in ilk kaydında ve Faz A'da bu adım
  atlanır/farklıdır — bkz. §12.0a), (2) `organization_memberships` (varsa), (3)
  `refresh_token_families`, (4) `refresh_tokens`. Ters sırayla kilitleme deadlock riski
  taşıdığından yasaktır.
- Adım (1) uygulanabiliyorsa kilit (`SELECT ... FOR UPDATE`, Faz C) alındıktan **sonra** aktif
  aile/token/`session_generation`/`revoked_at`/**`MAX(revoked_at)`** durumu **yeniden okunur**;
  karar ilk (kilitsiz veya Faz A) okumaya değil bu ikinci okumaya göre verilir.
  `PROVIDER_TOKEN_EXCHANGE`in `auth_time > MAX(revoked_at)` karşılaştırması her zaman bu
  kilit-sonrası değere karşı yapılır (§11).
- Bir iptal transaction'ı commit olduktan sonra, ondan önce başlamış ama henüz mantıksal kilidi
  almamış yarışan bir `PROVIDER_TOKEN_EXCHANGE` (veya kendi tablo sırasında bir aktivasyon
  transaction'ı), kilit sırasına girip yeniden okuma yaptığında hedefin artık iptal edilmiş/eski
  `session_generation`lı/eski `auth_time`li olduğunu görür; yarışan işlem eski durumla **yeni bir
  aile veya yeni bir aktif cihaz kaydı üretemez** — hangisi önce kilidi alırsa alsın, sonuç
  tutarlıdır ve çelişkili bir "aktif ama iptal edilmiş" durum bırakmaz.

### 12.2. Replay sırası

- Her mutasyonda standart `Authorization` doğrulaması, idempotency/replay kontrolünden **önce**
  çalışır ve onunla asla atlanamaz. Sunulan access tokenı zaten iptal edilmişse istek `401
  SESSION_REVOKED` ile reddedilir; replay bu adımı bypass edip tokenı yeniden yetkili gösteremez.
- `DEVICE_SELF_REVOKE` için tamamlanmış bir sonuç, yalnız §5.1'deki bütün bileşenler (aynı
  `actorUserId`, aynı çağıran `trusted_devices.id`, aynı hedef `deviceId`, aynı `operationType`,
  aynı access-token fingerprint, aynı `Idempotency-Key`) birlikte eşleşirse güvenli replay ile
  okunabilir. Bir cihazın kendi kendini iptal ettiği (`isCurrentDevice=true`) çağrıda, istemci bu
  isteği aynı (artık iptal edilmiş) tokenla yeniden deneyemez; sonucu bilmiyorsa §14'teki mobil
  yükümlülüğe göre davranır.

### 12.3. Audit tekilliği — bilinçli seçim

İki farklı durum vardır ve davranışları **kasıtlı olarak farklıdır**:

1. **Aynı `Idempotency-Key` + aynı `request_fingerprint` replayı** — hiçbir tabloya (dahil
   `audit_logs`) ikinci `INSERT`/`UPDATE` üretilmez; ilk tamamlanmış sonucun eşdeğeri döner.
2. **Farklı bir `Idempotency-Key` ile, hedefi zaten terminal (iptal edilmiş) bulan istek** — bu
   ayrı bir mantıksal komuttur: hedef tabloya durum mutasyonu yapılmaz (idempotent no-op başarı,
   `session_generation` ikinci kez artmaz) **ama** `audit_logs`a yine tam bir yeni `INSERT`
   yazılır. Bu, aynı hedefe farklı zamanlarda kimin dokunduğunu (örn. iki farklı yöneticinin aynı
   kullanıcıyı ayrı ayrı iptal etmeye çalışması) denetim geçmişinden kaybetmemek için kasıtlıdır
   ve `YETKI_MATRISI.md` §4.5'teki zorunlu denetim gereksinimiyle uyumludur.

Bu iki davranış §8.3, §9.3 ve §10.3'te aynı biçimde uygulanır; belge içinde başka bir yerde farklı
bir audit-tekilliği kararı ima edilmez.

## 13. Audit, güvenlik ve idempotency kuralları

- `DEVICE_SELF_REVOKE`, `DEVICE_SESSION_REVOKE` ve `PLATFORM_DEVICE_REVOKE` her zaman audit
  olayı üretir (bkz. §12.3 için tam kural); başarısız yetki reddi (`403 FORBIDDEN`) de güvenlik
  açısından anlamlı olduğundan denetlenir (`YETKI_MATRISI.md` §4.5, §8.10).
- Audit olayında en az `actorUserId`, etkilenen `deviceId` ve/veya
  `organizationMembershipId`, olay tipi, sonuç kodu ve `requestId` yer alır.
- `DEVICE_SESSION_REVOKE` audit satırı `event_scope=ORGANIZATION`, `organization_id`=hedef
  kurum ile yazılır; platform yöneticisi destek amaçlı çağırdıysa ek bir `PLATFORM_ADMIN_ORG_
  ACCESS` (`event_scope=ORGANIZATION`) satırı da yazılır. `DEVICE_SELF_REVOKE` ve
  `PLATFORM_DEVICE_REVOKE` audit satırları `event_scope=GLOBAL`, `organization_id=NULL` ile
  yazılır; `PLATFORM_DEVICE_REVOKE` ayrıca `target_entity_type=USER`, `target_entity_id`=hedef
  kullanıcı taşır.
- `IAM_AUTH` kapsamındaki transaction'lar için `audit_logs` RLS'i, yalnız izin verilen işlem
  türlerinin yazılmasına izin verir (`VERI_MODELI.md` §13.2, `ADR-004`). Bu belge, o allow-list'e
  `DEVICE_SELF_REVOKE`i ekler (bkz. §18).
- Ham access/refresh token değeri, Cognito tokenı veya parola audit kaydına, idempotency
  payload'una veya loglara yazılmaz.
- Aynı `Idempotency-Key` ve eşdeğer istekle yapılan yeniden deneme ikinci audit veya ikinci yan
  etki üretmez (§12.3 madde 1); farklı anahtarla zaten terminal hedefe gelen istek yalnız yeni bir
  audit üretir, durum mutasyonu üretmez (§12.3 madde 2).

## 14. Mobil istemci yükümlülükleri

- İstemci, kullanıcının kendi cihaz listesini görüntülerken sunucunun döndürdüğü
  `isCurrentDevice` alanına göre "bu cihaz" etiketini gösterir; bunu kendisi hesaplamaz.
- İstemci, `isCurrentDevice=true` olan bir cihazı iptal ettiğinde (bölüm 8), bu başarılı cevabı
  aynı zamanda **kendi** oturumunun sonu gibi ele alır: access/refresh tokenı ve hassas
  önbelleği güvenli saklamadan siler, kullanıcıyı giriş ekranına yönlendirir — sunucudan ayrıca
  bir `401 SESSION_REVOKED` beklemez. İstemci bu isteği aynı tokenla **yeniden denemez**; ağ
  belirsizliğinde önceki isteğin sonucu bilinmiyorsa (örn. yanıt hiç gelmediyse) durumu farklı bir
  aktif oturumdan veya yeni bir girişten sonra `GET /api/v1/iam/devices` ile doğrular — eski
  tokenla tekrar denemek `401 SESSION_REVOKED` alır ve bu, önceki isteğin muhtemelen başarılı
  olduğunun terminal sinyali olarak yorumlanır (`sessions/logout` ile aynı desen,
  `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §13.4).
- İstemci, bir kurum yöneticisi/hoca ekranında `DEVICE_SESSION_REVOKE` çağırdıktan sonra hedef
  kullanıcının **yalnız o kurumdaki** oturumunun kapandığını varsayar; kullanıcının cihazının
  veya başka kurumdaki oturumunun kapandığını varsaymaz.
- İstemci, kendi arka planda çalışırken aldığı `401 SESSION_REVOKED` sonucunu daha önce olduğu
  gibi (`IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §15) ele alır: kurum kapsamlı oturumda ilgili kurum
  kuyruğunu `BLOCKED` yapar, global platform yöneticisi oturumunda kuyruk varsaymadan oturumu
  kapatır.
- İstemci, cihaz iptalinden sonra aynı `deviceIdentifier`i yeniden güvenilir kılmaya çalışırken
  `403 REAUTHENTICATION_REQUIRED` alırsa bunu geçici bir ağ hatası gibi sessizce yeniden denemez;
  kullanıcıyı güvenli, etkileşimli (`prompt=login` içeren) yeniden girişe yönlendirir.
- Cihaz listesi ve iptal çağrıları başarısız/yavaş ağ durumunda sessizce yeniden denenmez;
  kullanıcıya açık bekliyor/başarısız durumu gösterilir (`ORTAK-02`).

## 15. Kabul ölçütleri

- Kullanıcı yalnız kendi güvenilir cihazlarını listeleyebilir; başka kullanıcının cihazı bu
  uçlarla görülemez.
- Kendi cihaz iptali, o cihaza bağlı bütün kurum bağlamlarındaki oturumları (ve varsa global
  platform yöneticisi ailesini) tek işlemde kapatır; `session_generation`/`reauthentication_
  required_after`ı değiştirmez.
- `DEVICE_SESSION_REVOKE`, kurum yöneticisi için varsayılan açık, hoca için varsayılan kapalı ve
  yalnız ayrı izinle açılabilir olarak tanımlanmıştır; yalnız aynı kurumun üyeliğinde
  kullanılabilir.
- `DEVICE_SESSION_REVOKE`, hedef üyeliğin yalnız o kurumdaki ailelerini kapatır; kullanıcının
  başka kurumdaki üyeliği ve cihaz güvenilirliği etkilenmez.
- `DEVICE_SESSION_REVOKE` başarılı çağrısı hedef üyeliğin `session_generation`ını artırır ve
  `reauthentication_required_after`ı yükseltir; zaten aktif ailesi olmayan hedefte tekrar
  artış üretmez.
- Platform yöneticisinin destek amaçlı `DEVICE_SESSION_REVOKE` çağrısı normal rol/izin
  kontrolünü bypass etmez; dar, sunucu tarafında doğrulanmış bir bayrakla genişler ve ek
  `PLATFORM_ADMIN_ORG_ACCESS` audit'i üretir.
- Platform geneli tek cihaz iptali yalnız `GLOBAL_PLATFORM_ADMIN` kapsamlı oturumla ve yalnız
  aktif platform yöneticisi tarafından çağrılabilir; delegasyona kapalıdır, dar kapsamlı `GLOBAL`
  RLS policy'siyle yalnız hedef cihazı etkiler ve her zaman denetlenir.
- Platform geneli tek cihaz iptali yalnız hedef cihazı ve ona bağlı bütün kurum ailelerini
  kapatır; hesap düzeyinde `reauthentication_required_after`ı değiştirmez.
- **Cihaz-bazlı reauth bariyeri:** aynı `(user_id, device_identifier)` çifti için, mantıksal cihaz
  kilidi altında yeniden okunan `MAX(revoked_at)`tan eski/eşit doğrulanmış `auth_time` ile yeni bir
  aktif `trusted_devices` satırı üretilemez; yalnız daha yeni `auth_time` ile üretilebilir. Bu
  kontrol satır hiç yokken bile (mantıksal kilit) ve satır varken (`FOR UPDATE`) aynı biçimde
  korunur.
- **Dar görünürlük:** cihaz-bazlı bariyerin okuduğu geçmiş, dar `PROVIDER_TOKEN_EXCHANGE` `SELECT`
  policy'siyle yalnız aynı `user_id` + aynı `device_identifier`e sınırlıdır; başka kullanıcı veya
  başka cihazın geçmişi hiçbir koşulda görünmez ve bu görünürlük genel `SELECT` yetkisi/`BYPASSRLS`/
  `SECURITY DEFINER` ile değil yalnız bu dar policy ile sağlanır.
- **Eşzamanlılık:** aktivasyon-vs-iptal, refresh-vs-iptal ve `PROVIDER_TOKEN_EXCHANGE`-vs-iptal
  yarışlarında (mantıksal cihaz kilidiyle serileştirilerek) önce commit olan kazanır; kaybeden
  taraf eski durumla ikinci bir aile/iptal edilmemiş görünüm/eski `auth_time`yle aktif cihaz
  üretemez. Satır hiç yokken gelen iki eşzamanlı ilk-kayıt denemesi de aynı kilitle serileştirilir;
  birden fazla aktif satır oluşmaz.
- **Replay:** `DEVICE_SELF_REVOKE`'un tamamlanmış sonucu yalnız aktör+çağıran cihaz+hedef cihaz+
  işlem+token fingerprint+anahtar altılısı eşleştiğinde okunur; replay hiçbir koşulda iptal
  edilmiş bir tokenı yeniden yetkili göstermez.
- **Audit tekilliği:** aynı anahtar replayı ikinci audit üretmez; farklı anahtarla zaten terminal
  hedefe gelen istek durum mutasyonu yapmadan yine tam bir yeni audit satırı üretir — bu iki
  davranış üç mutasyonda da aynı biçimde uygulanır.
- Üç mutasyonun tamamı `Idempotency-Key` ister ve `API_GENEL_KURALLARI.md`/`VERI_MODELI.md`/bu
  belge aynı fingerprint ifadesini kullanır.
- Hiçbir uç yeni bir HTTP hata kodu tanımlamaz; yalnız `API_GENEL_KURALLARI.md` §5.2
  kataloğundaki kodlar kullanılır.
- Erişim kapsamı dışındaki kurum/üyelik/cihaz kaydına doğrudan kimlikle erişim `404
  RESOURCE_NOT_FOUND` döner; varlığı açığa çıkarmaz.

## 16. Test ve doğrulama notları

Bu görev belge/sözleşme görevidir. Uygulama testleri `IAM-006` ve `IAM-009` sahipliğindedir. Bu
sözleşme en az aşağıdaki senaryoların sonraki görevlerde kanıtlanmasını zorunlu kılar:

1. Kullanıcı yalnız kendi cihazlarını listeler; başka kullanıcının cihaz kaydı asla dönmez.
2. Kendi cihaz iptali, aynı cihazın iki farklı kurum ailesini tek işlemde kapatır; diğer
   cihazları etkilemez.
3. Kendi cihaz iptali, global platform yöneticisi ailesi varsa onu da kapatır.
4. `ORG_ADMIN`, kendi kurumundaki bir hocanın `DEVICE_SESSION_REVOKE`ini varsayılan olarak
   çağırabilir; `TEACHER` izinsizken `403 FORBIDDEN` alır.
5. `DEVICE_SESSION_REVOKE` izni verilmiş `TEACHER`, yalnız kendi kurumundaki hedefte başarılı
   olur; başka kurumdaki `organizationMembershipId` `404` döner.
6. `DEVICE_SESSION_REVOKE`, hedefin başka kurumdaki üyeliğini ve cihaz güvenilirliğini
   etkilemez.
7. `DEVICE_SESSION_REVOKE` tekrarı (aynı anahtar) `session_generation`ı ikinci kez artırmaz.
8. `PLATFORM_DEVICE_REVOKE`, yalnız `GLOBAL_PLATFORM_ADMIN` kapsamlı platform yöneticisi
   oturumuyla çağrılabilir; `ORGANIZATION` kapsamlı oturumla (aktör platform yöneticisi olsa
   bile) `403 FORBIDDEN` alır.
9. `PLATFORM_DEVICE_REVOKE`, hedef cihazın bütün kurum ailelerini kapatır ama
   `users.reauthentication_required_after`ı ve kullanıcının **başka** cihazlarının ailelerini
   değiştirmez.
10. **Negatif kabul testi — eski `auth_time` ile yeniden güven kazanma:** İptal edilmiş bir
    `trusted_devices` satırının, mantıksal cihaz kilidi altında yeniden okunan `MAX(revoked_at)`
    değerinden **eski veya eşit** doğrulanmış `auth_time` taşıyan bir Cognito oturumuyla (örn.
    eski tarayıcı SSO çerezi) yapılan `provider-token-exchange`, aynı `deviceIdentifier` için
    yeni aktif cihaz kaydı **üretemez** ve `403 REAUTHENTICATION_REQUIRED` döner. Yalnız
    `revoked_at`ından **yeni** bir `auth_time` (gerçek, etkileşimli yeniden giriş) ile yeni ve
    ayrı bir aktif cihaz kaydı üretilebilir.
11. **FORCE RLS altında dar görünürlük:** aynı `user_id` + aynı `device_identifier`in aktif ve
    iptal edilmiş geçmiş satırları dar `PROVIDER_TOKEN_EXCHANGE` `SELECT` policy'siyle görülebilir
    ve `MAX(revoked_at)` doğru hesaplanır; aynı sorgu başka bir `user_id`ye veya aynı kullanıcının
    **farklı** bir `device_identifier`ine ait satırları döndürmez.
12. **Revoke-vs-exchange yarışı (mantıksal cihaz kilidi):** aynı `(user_id, device_identifier)`
    çiftinde eşzamanlı `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE` ile eski `auth_time` taşıyan
    bir `provider-token-exchange` yarıştığında, mantıksal kilidi önce alan işlem tamamlanır;
    hangisi önce commit olursa olsun eski `auth_time` ile **hiçbir sırada** aktif cihaz kalmaz.
13. **Eşzamanlı ilk kayıt:** aynı `(user_id, device_identifier)` çifti için hiç `trusted_devices`
    satırı yokken gelen iki eşzamanlı `provider-token-exchange`, mantıksal cihaz kilidiyle
    serileştirilir; yalnız biri satırı üretir, diğeri mevcut aktif cihazı bulur/yeniden kullanır —
    hiçbir koşulda aynı çift için birden fazla aktif satır oluşmaz.
14. **Faz A/Faz B arası yarış:** `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE`in Faz A keşif
    sorgusu ile Faz B mantıksal kilidi arasına eşzamanlı bir başka iptal (`PLATFORM_DEVICE_
    REVOKE`/`DEVICE_SELF_REVOKE`) girip aynı cihazı iptal edip commit ederse, ilk işlemin Faz C
    yeniden okuması bu güncel `revoked_at`ı görür ve karar/mutasyon buna göre (no-op) verilir;
    Faz A'nın kilitsiz okuduğu eski/yok durum hiçbir karara girmez.
15. **Faz C yeniden okumanın güncelliği:** Faz C'nin `SELECT ... FOR UPDATE`i, Faz A'dan sonra
    başka bir transaction tarafından commit edilmiş bir `revoked_at` değişikliğini **her zaman**
    görür; karar hiçbir koşulda Faz A'nın önbelleğe alınmış değerine dayanmaz.
16. **Çapraz `userId`/`deviceId` keşfi 404 döner:** `DEVICE_SELF_REVOKE`te başka kullanıcıya ait
    `deviceId`, `PLATFORM_DEVICE_REVOKE`te path `userId`sine ait olmayan `deviceId` Faz A'da 0
    satır döner ve istek `404 RESOURCE_NOT_FOUND` ile biter; mantıksal kilit hiç alınmaz, Faz
    C/D'ye geçilmez.
17. Aktivasyon-vs-iptal yarışı: `CONTEXT_ACTIVATE` ile eşzamanlı `DEVICE_SESSION_REVOKE`de önce
    commit olan kazanır; kaybeden eski `session_generation`/eski durumla devam edemez.
18. Refresh-vs-iptal yarışı: `SESSION_REFRESH` ile eşzamanlı `DEVICE_SELF_REVOKE`de iptal önce
    commit olursa yenilenen token da aynı zincirde iptal edilmiş sayılır; çelişkili "aktif ama
    iptal edilmiş" durum kalmaz.
19. İki farklı `Idempotency-Key` ile eşzamanlı aynı hedefe gelen iptalde yalnız biri durum
    değiştirip audit yazar; diğeri no-op döner ve **kendi** audit satırını yazar (§12.3).
20. Aynı `Idempotency-Key` ile kayıp cevap güvenli replay edilir; farklı hedef/çağıran cihaz/token
    fingerprint ile aynı anahtar `409 IDEMPOTENCY_KEY_REUSED` döner.
21. `DEVICE_SELF_REVOKE` replay'i, isteği doğrulayan access tokenı zaten iptal edilmişse
    `401 SESSION_REVOKED` ile durur; replay bu tokenı yeniden yetkili göstermez.
22. Bütün üç mutasyon audit olayı üretir; audit kaydında ham token/parola bulunmaz.

### 16.1. Zorunlu SQL/RLS kabul kapıları

`IAM-009`, en az aşağıdaki SQL/RLS senaryolarını otomasyonla kanıtlamadan bu sözleşme uygulanmış
sayılmaz:

1. `DEVICE_SESSION_REVOKE` transaction'ı yalnız `app.organization_id` ile eşleşen
   `organization_memberships` satırını değiştirebilir; başka kurumun üyeliği RLS ile reddedilir.
2. `PLATFORM_DEVICE_REVOKE` transaction'ı yalnız aktif `platform_administrators` satırına
   sahip aktörle çalışır; normal kullanıcı aynı `GLOBAL` scope'ta bu işlemi yürütemez. Dar
   `GLOBAL` policy'si yalnız hedef `trusted_device_id`ye bağlı aileleri açar; kullanıcının başka
   cihazlarının aileleri RLS ile reddedilir.
3. `DEVICE_SELF_REVOKE` transaction'ı yalnız `trusted_devices.user_id = app.iam_actor_user_id AND
   revoked_at IS NULL` koşuluna uyan satırı değiştirebilir; başka kullanıcının cihazı veya zaten
   iptal edilmiş bir satır RLS ile reddedilir (0 satır etkilenir).
4. `trusted_devices` `INSERT` `WITH CHECK`i, aynı `(user_id, device_identifier)` çifti için
   `app.iam_verified_auth_time`, mantıksal kilit altında yeniden okunan `MAX(revoked_at)`tan
   büyük olmadan yeni satır açılmasını reddeder (cihaz-bazlı reauth bariyeri).
5. `IAM_AUTH` scope'unda `DEVICE_SELF_REVOKE` audit insert'i yalnız güncellenmiş allow-list'teki
   işlem türleriyle ve doğru aktör/çağıran cihaz/hedef cihaz bağıyla kabul edilir; çapraz
   kullanıcı satırı reddedilir.
6. Platform yöneticisinin destek amaçlı `DEVICE_SESSION_REVOKE` çağrısı yalnız server-set
   `app.iam_platform_admin_support_access=true` ile normal rol/izin `USING`unu genişletir;
   istemci bu bayrağı hiçbir parametreyle kuramaz.
7. Aynı kullanıcı için aynı `clientMutationId` farklı `operationType`/hedefle kullanıldığında
   ilgili idempotency kapsamının `UNIQUE` kısıtı + `request_fingerprint` doğrulaması `409
   IDEMPOTENCY_KEY_REUSED` üretir.
8. İki eşzamanlı transaction aynı hedefi kilitlemeye çalıştığında kilit sırası (mantıksal cihaz
   kilidi → `trusted_devices` → `organization_memberships` → `refresh_token_families` →
   `refresh_tokens`) her iki tarafta aynıdır; ters sıralı kilitleme uygulama kodunda yoktur
   (statik/inceleme kontrolü).
9. `trusted_devices` dar `PROVIDER_TOKEN_EXCHANGE` `SELECT` policy'si (`app.iam_operation_code =
   'PROVIDER_TOKEN_EXCHANGE' AND user_id = app.iam_actor_user_id AND device_identifier =
   app.iam_provider_device_identifier`) aynı actor + aynı `device_identifier`in **hem aktif hem
   iptal edilmiş** geçmişini görünür kılar; başka `user_id` veya başka `device_identifier`e ait
   hiçbir satır — aktif veya iptal — RLS ile görünmez.
10. Aynı `(user_id, device_identifier)` mantıksal anahtarında `pg_advisory_xact_lock` (veya
    eşdeğer kilit) alınmadan `trusted_devices` `INSERT`/`UPDATE` yapılamaz; kilit satır hiç
    yokken de çalışır — hiç satır olmayan bir çift için gelen iki eşzamanlı
    `PROVIDER_TOKEN_EXCHANGE` denemesinden yalnız biri satır üretir, ikincisi kilit
    serbest kaldığında mevcut satırı bulur/yeniden kullanır.
11. `trusted_devices`e erişen hiçbir `iam_runtime` işlemi `BYPASSRLS`, tablo geneli `SELECT`/
    `UPDATE` yetkisi veya RLS'i atlayan bir `SECURITY DEFINER` fonksiyonu kullanmaz (statik/
    inceleme kontrolü — bkz. "Reddedilen alternatif" ve column-level `GRANT` bölümü, `ADR-004`).
12. `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE`in Faz A keşif sorgusu ile Faz B mantıksal kilidi
    arasına giren eşzamanlı bir iptal, hedefi Faz C'nin `SELECT ... FOR UPDATE`i **her zaman**
    güncel `revoked_at` ile görecek şekilde commit olur; karar Faz A'nın kilitsiz okuduğu değere
    değil Faz C'ye dayanır.
13. Faz A'da 0 satır dönen (çapraz `user_id`/`deviceId`) bir keşif, mantıksal kilidi hiç almadan
    ve Faz C/D'ye geçmeden `404 RESOURCE_NOT_FOUND` ile biter; bu senaryoda hiçbir
    `pg_advisory_xact_lock` çağrısı yapılmaz (statik/inceleme kontrolü).
14. Faz C'nin yeniden okuduğu satırın `user_id`/`device_identifier`i Faz A'da keşfedilenle
    uyuşmuyorsa (savunma amaçlı, `device_identifier` immutable olduğundan normalde tetiklenmez)
    istek fail-closed `404 RESOURCE_NOT_FOUND` ile durur; hiçbir mutasyon yapılmaz.
15. `trusted_devices` `CREATE POLICY`/`GRANT` ifadeleri (`SELECT`/`INSERT`/`UPDATE` policy'leri
    ve `REVOKE UPDATE ON trusted_devices FROM iam_runtime; GRANT UPDATE (revoked_at) ON
    trusted_devices TO iam_runtime;`) gerçek PostgreSQL üzerinde hatasız oluşturulur; hiçbir
    ifade geçersiz trigger `NEW`/`OLD` sözdizimi kullanmaz.
16. `revoked_at`ın `NULL → transaction_timestamp()` güncellemesi izinlidir (aktif ve doğru
    scope+operation+sahiplikteki satırda); `NULL → keyfî geçmiş/gelecek timestamp` `WITH CHECK
    (revoked_at = transaction_timestamp())` ile, terminal `timestamp → NULL` veya `timestamp →
    başka bir timestamp` ise `USING (... AND revoked_at IS NULL)`in satırı hedef kümesine hiç
    almamasıyla reddedilir (column-level `GRANT`in bu değer bütünlüğüyle ilgisi yoktur — o yalnız
    hangi kolonun yazılabileceğini sınırlar).
17. `device_identifier`, `user_id`, `platform`, `device_name` ve `trusted_at` kolonlarına
    yönelik bir `UPDATE`, `iam_runtime`e bu kolonlar için hiç `GRANT UPDATE` verilmediğinden
    RLS'e ulaşmadan **privilege seviyesinde** (`permission denied for column`) reddedilir.
18. Başka aktöre/hedefe ait bir cihazın güncellenmesi — doğru `GRANT`e sahip olunsa bile —
    `USING` koşulunu sağlamadığından RLS ile reddedilir (0 satır etkilenir).
19. Başka bir `IAM_AUTH`/`GLOBAL` operation code'u, `app.iam_target_device_id`/
    `app.iam_actor_user_id`/`app.iam_target_user_id` tesadüfen dolu olsa bile
    `trusted_devices`e ait `SELECT`/`UPDATE` policy'lerinin kendi operation-code guard'ını
    sağlamadığından bu tabloyu okuyamaz/güncelleyemez.
20. `PROVIDER_TOKEN_EXCHANGE` dışındaki bir işlem `trusted_devices`e `INSERT` yapamaz;
    `trusted_devices_insert_provider_exchange` policy'sinin operation-code guard'ı sağlanmaz ve
    başka hiçbir `INSERT` policy'si tanımlı değildir.

## 17. Kapsam dışında bırakılanlar

- Kendi oturumundan çıkış (`sessions/logout`) — `IAM-001` sahibidir, değişmemiştir.
- Cognito/sağlayıcı tarafında tetiklenen tam hesap devre dışı bırakma, global sign-out ve olay
  kaybı uzlaştırması — `IAM-004` ve ilgili provider-command görevlerindedir.
- `DEVICE_SESSION_REVOKE` iznini verme/geri alma akışı — `STAFF-*`/`PERM-002` kapsamındadır; bu
  belge yalnız var olan izni API üzerinde tüketir.
- Parola sıfırlama, hoca hesabı oluşturma/kapatma ve personel yönetimi — `STAFF-*`.
- Cihaz adını değiştirme (rename) ve cihaz başına bildirim tercihleri — V1'de gerekli değildir,
  ileride ayrı bir görev gerektirir.
- Toplu "bütün cihazlarımı kaldır" tek komutu — V1'de kullanıcı cihazlarını tek tek kaldırır
  (`ORTAK-01`); ayrı bir toplu uç bu belgede tanımlanmamıştır.
- Web/masaüstü oturum yönetimi — V1 yalnızca mobil istemcidir.

## 18. Bu görevle güncellenen diğer belgeler

- `VERI_MODELI.md` §13.2 — `iam_runtime` `IAM_AUTH` audit RLS allow-list'i `DEVICE_SELF_REVOKE`
  ile genişletilmiştir (önceden yalnız `PROVIDER_TOKEN_EXCHANGE`, `PLATFORM_ADMIN_ACTIVATE`,
  `CONTEXT_ACTIVATE`, `SESSION_REFRESH`, `SESSION_LOGOUT` sayılıyordu).
- `VERI_MODELI.md` §14 (`idempotency_keys.request_fingerprint` açıklaması) — aynı işlem bazlı
  fingerprint listesine `DEVICE_SELF_REVOKE` eklenmiştir; bu, `API_GENEL_KURALLARI.md` §7.2'deki
  eklemenin veri modelindeki birebir eşleniğidir.
- `VERI_MODELI.md` §4.10 (`trusted_devices`) — cihaz-bazlı reauth bariyeri (aynı `(user_id,
  device_identifier)` çifti için `auth_time > MAX(revoked_at)` kuralı) bağlayıcı bir invariant
  olarak eklenmiştir.
- `API_GENEL_KURALLARI.md` §7.2 — işlem bazlı `requestTokenFingerprint` kaynağı listesine
  `DEVICE_SELF_REVOKE` eklenmiştir; ifade `VERI_MODELI.md` §14 ve bu belgenin §5/§5.1'iyle
  birebir aynıdır (önceki sürümde üç belge arasında ifade farkı vardı; bu düzeltilmiştir).
- `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` — "Cognito V1 bağlayıcı profili" bölümüne
  cihaz-bazlı reauth bariyeri bağlayıcı madde olarak eklenmiş; `iam_runtime`/RLS matrisine
  `trusted_devices`/`refresh_token_families`/`refresh_tokens`/`organization_memberships`/
  `idempotency_keys`/`audit_logs` için `DEVICE_LIST`, `DEVICE_SELF_REVOKE`,
  `DEVICE_SESSION_REVOKE`, `PLATFORM_DEVICE_REVOKE` satırları ve `app.iam_target_device_id`/
  `app.iam_verified_auth_time`/`app.iam_platform_admin_support_access` SET LOCAL değişkenleri
  eklenmiş; yeni "IAM-002 — Cihaz ve oturum iptali `iam_runtime`/RLS eklentisi" alt bölümü kilit
  sırası, replay ve IAM-009 test gereksinimlerini tanımlamıştır.
- `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §7.3/§7.4 — `PROVIDER_TOKEN_EXCHANGE`in cihaz oluşturma
  davranışına cihaz-bazlı reauth bariyeri referansı ve `403 REAUTHENTICATION_REQUIRED`in ikinci
  kaynağı (mevcut hata kodu, yeni kod icat edilmemiştir) eklenmiştir.
- **(v1.2)** `ADR-004` `trusted_devices` `SELECT`/`INSERT` policy satırları, `PROVIDER_TOKEN_
  EXCHANGE` için ayrı ve dar bir `SELECT` policy'siyle (yalnız aynı `user_id` + yeni
  `app.iam_provider_device_identifier` eşleşmesi) genişletilmiş; yeni `(user_id,
  device_identifier)` mantıksal cihaz kilidi (`pg_advisory_xact_lock`) `PROVIDER_TOKEN_EXCHANGE`,
  `DEVICE_SELF_REVOKE` ve `PLATFORM_DEVICE_REVOKE`i serileştirecek şekilde eklenmiş; "Reddedilen
  alternatif" notu (BYPASSRLS/`SECURITY DEFINER`/genel `SELECT` yasağı) bağlayıcı kısıt olarak
  yazılmıştır.
- **(v1.2)** `VERI_MODELI.md` §4.10, `API_GENEL_KURALLARI.md` §7.2'ye eşdeğer bir değişiklik
  gerektirmemiştir (mekanizma zaten `ADR-004`e bağlanmıştı); yalnız §4.10'daki cihaz-bazlı bariyer
  metni mantıksal kilit ve dar görünürlükle güncellenmiş, ayrıca önceki sürümdeki bir yazım hatası
  ("`DEVICE_SESSION_REVOKE`" yerine doğrusu "`DEVICE_SELF_REVOKE`") düzeltilmiştir.
- **(v1.2)** `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §7.3, `PROVIDER_TOKEN_EXCHANGE`in artık
  `trusted_devices`e erişmeden önce mantıksal cihaz kilidi aldığını ve dar `SELECT` policy'sinin
  başka kullanıcı/cihazın geçmişini göstermediğini belirtecek şekilde genişletilmiştir.
- **(v1.3)** `DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE`, yalnız `deviceId` alıp
  `device_identifier`i bilmediğinden, mantıksal cihaz kilidinden önce dar/kararsız/salt-okunur
  bir **Faz A keşif** sorgusu eklenmiş (§12.0a); kilit sonrası **Faz C** yeniden okuma +
  `user_id`/`device_identifier` doğrulaması (uyuşmazsa fail-closed `404`) ve ancak ardından
  **Faz D** karar/mutasyon zorunlu kılınmıştır. "`trusted_devices`e her erişimden önce kilit"
  ifadesi "yalnız Faz A keşfine kilitsiz izin verilir, bütün güvenlik-relevant okuma ve
  `INSERT`/`UPDATE` kilit sonrasıdır" şeklinde netleştirilmiştir. §12.1, mantıksal kilidin
  **yalnız** `PROVIDER_TOKEN_EXCHANGE`/`DEVICE_SELF_REVOKE`/`PLATFORM_DEVICE_REVOKE` için zorunlu
  olduğunu, `CONTEXT_ACTIVATE`/`PLATFORM_ADMIN_ACTIVATE`in bu kilidi almadığını netleştirmiştir.
- **(v1.4)** `ADR-004` `trusted_devices` `UPDATE` satırındaki geçersiz `WITH CHECK (NEW.x =
  OLD.x ...)` sözdizimi (RLS ifadelerinde trigger `NEW`/`OLD` kaydı yoktur) kaldırılmış; yerine
  **column-level `GRANT UPDATE (revoked_at)`** ile değişmez kolon koruması, `USING (... AND
  revoked_at IS NULL)` + `WITH CHECK (revoked_at IS NOT NULL)` ile geçerli PostgreSQL RLS
  ifadeleri tanımlanmıştır (`REVOKE UPDATE ON trusted_devices FROM iam_runtime; GRANT UPDATE
  (revoked_at) ON trusted_devices TO iam_runtime;`). `trusted_devices` `SELECT` policy'sindeki
  "OR'lanmayan" yanlış ifadesi düzeltilmiş; üç `PERMISSIVE` policy'nin gerçekten `OR`landığı ama
  karşılıklı dışlayıcı `app.iam_operation_code` guard'ları sayesinde güvenli olduğu açıklanmıştır.
  `VERI_MODELI.md` §4.10, `device_identifier` immutability'sinin RLS `WITH CHECK`i değil
  column-level privilege ile sağlandığını yansıtacak şekilde düzeltilmiştir. IAM-009 kabul
  testlerine `CREATE POLICY`/`GRANT`in gerçek PostgreSQL'de çalıştığı, `revoked_at NULL→timestamp`
  izinli/`timestamp→NULL` veya `→başka timestamp` reddedilir, diğer beş kolonun privilege
  seviyesinde reddedildiği ve çapraz actor/target güncellemenin RLS ile reddedildiği senaryoları
  eklenmiştir.
- **(v1.5)** `ADR-004` `trusted_devices` `SELECT`/`INSERT`/`UPDATE` policy'lerinin **tamamına**
  açık `app.iam_operation_scope`/`app.iam_operation_code` guard'ı predicate içine eklenmiştir;
  özellikle `GLOBAL` `SELECT` (`trusted_devices_select_platform_revoke`) ve iki revoke `UPDATE`
  policy'si (`trusted_devices_update_self_revoke`, `trusted_devices_update_platform_revoke`)
  önceki sürümde guard'sızdı — yalnız `user_id`/`id` eşleşmesi vardı, policy açıklama metni
  guard varmış izlenimi verse de gerçek predicate'te yoktu; bu düzeltilmiştir. Hatalı "`revoked_at`ı
  başka bir zaman damgasına çevirme column-level privilege'le zaten mümkün değildir" iddiası
  kaldırılmış (column-level `GRANT` yalnız hangi kolonun yazılabileceğini sınırlar, yazılan
  değerin doğruluğunu değil); yerine `WITH CHECK (revoked_at = transaction_timestamp())` —
  yazılan değerin sunucunun kendi transaction zamanına tam eşit olmasını zorunlu kılan, geçmiş/
  gelecek keyfî zaman damgalarını reddeden — geçerli bir ifade tanımlanmıştır. Terminal satırın
  değişmezliği hâlâ `USING (... AND revoked_at IS NULL)`den gelir. IAM-009 kabul testlerine
  şunlar eklenmiştir: başka bir `IAM_AUTH` operation code'unun hedef değişkenleri dolu olsa bile
  cihazı okuyamadığı/güncelleyemediği; başka bir `GLOBAL` operation code'unun aynı şekilde
  okuyamadığı/güncelleyemediği; `PROVIDER_TOKEN_EXCHANGE` dışındaki işlemin `INSERT` yapamadığı;
  `NULL→transaction_timestamp()`in geçerli, `NULL→keyfî geçmiş/gelecek timestamp`in `WITH CHECK`
  ile, terminal→`NULL`/başka timestamp'in `USING` ile reddedildiği senaryolar. `IAM_CIHAZ`,
  `VERI_MODELI.md` ve `ADR-004` aynı mekanizma ve terminolojiyle hizalanmıştır.

Bu değişikliklerin tamamı bu sözleşmenin ihtiyaç duyduğu dar ve doğrudan ek niteliğindedir;
ilgili belgelerin başka bölümlerine dokunulmamıştır.
