# ORG Marka Ayarları API Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | ORG-002 — Marka ayarları sözleşmesini yaz |
| Belge sürümü | 1.0 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlı sözleşmeler | `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md`, `VERI_MODELI.md`, `API_GENEL_KURALLARI.md`, `YETKI_MATRISI.md`, `ADR/ADR-007-pdf-dosya-depolama.md`, `MOBIL_TASARIM_TOKENLARI.md` |
| Son güncelleme | 20 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, kurum yöneticisinin ve platform yöneticisinin kurum marka ayarlarını yönetmesi
için gerekli API sözleşmesini tanımlar. Amaç; kurum ana rengi, yardımcı renk paleti, logo
yükleme/indirme/silme ve etkin modül yönetimi uçlarını sonraki ORG/backend/mobil görevleri
için çelişkisiz hâle getirmektir.

Bu sözleşme:

- `VERI_MODELI.md` §5.1 `organizations.primary_color`, `organizations.secondary_color`
  ve `logo_asset_id`, §5.2 `organization_brand_colors`, §5.3 `organization_modules` ve
  §10.1 `file_assets` tablolarını esas alır.
- Logo yükleme API şeklini tanımlar; gerçek nesne deposu provisioning'i ve dosya
  transfer güvenliği Dalga 5'teki `ORG-010`/`ORG-011` uygulama görevlerinde,
  `OPS-005` nesne deposu provisioning'i tamamlandıktan sonra gerçekleştirilir.
- `MOBIL_TASARIM_TOKENLARI.md` §3.2 (kurum paleti) ve §3.2.3'teki WCAG kontrast
  kurallarına uymak zorundadır; sunucu `primary` ve `secondary` bağlayıcı ham hex
  alanları için bu kurallara göre doğrulama yapar. `organization_brand_colors`
  yardımcı palet ayrıdır; `secondary` alanını örtük sıra/ilk eleman yorumuyla
  belirlemez.
- `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` ile aynı transaction scope modelini,
  idempotency kurallarını ve replay sıralamasını kullanır.
- Her DB transaction tam olarak **tek** scope taşır.

Bu sözleşme **değildir**:

- Kurum oluşturma, listeleme, detay, kimlik alanı (`name`/`shortName`/`defaultTimezone`)
  güncelleme ve durum değişikliği — bunlar `ORG-001` kapsamındadır.
- Nesne deposu provisioning'i, bucket/key policy yönetimi — `OPS-005` kapsamındadır.
- Dosya güvenliği uygulama katmanı iki aşamalı onay, source/backup alt durumları ve
  exact-version pre-delete — `CONTENT-003` kapsamındadır.
- Gerçek dosya transferi, MIME/zararlı içerik doğrulaması ve sağlayıcı SDK entegrasyonu
  — `ORG-010`/`ORG-011` kapsamındadır.
- Mobil logo seçme, önizleme ve yükleme bileşenleri — `ORG-008` (dosyasız),
  `ORG-011` (dosyalı) kapsamındadır.
- Kurum yöneticisi atama veya üyelik yönetimi — `STAFF-*` kapsamındadır.

## 2. Bağlayıcı karar özeti

### 2.1. Genel kurallar

- Kurum marka ayarları kurum yöneticisi (`ORG_ADMIN`) ve `BRAND_MANAGE` izni verilmiş
  hoca tarafından yönetilebilir. Platform yöneticisi destek amaçlı hedef kurumlu
  erişimle yönetebilir.
- Logo **isteğe bağlıdır**. Logo yüklenmemiş kurumda varsayılan sistem teması
  kullanılır. Logo alanı boş/null geçerli durumdur; sahte URL veya placeholder asset
  üretilmez.
- Logo MIME türü ve byte üst sınırı **bu sözleşmede bağlayıcıdır**: V1'de yalnızca
  `image/png` veya `image/jpeg`, en fazla 5 MB. Zararlı içerik taraması, bozuk dosya
  doğrulaması ve nesne deposu sağlayıcı SDK entegrasyonu `ORG-010` kapsamında
  kesinleşir; bu sözleşme bu kararları tekrar bağlamaz.
- `primary_color` ve `secondary_color` ayrı, bağımsız ve bağlayıcı kurum renkleridir;
  her biri geçerli 6 haneli hex (`#RRGGBB`) ve WCAG AA kontrast eşiklerini sağlamalıdır.
  Üretilen/gönderilmeyen renklerde açıkça tanımlı varsayılan tema değerleri kullanılır:
  `primary_color` eksikse Zeytin teması `#2E7D32`, `secondary_color` eksikse Zeytin
  temasının yardımcı rengi `#E65100` (bkz. §6.5). `organization_brand_colors`
  yardımcı paletidir; `secondary_color`'u belirlemez.
- Bütün yazma uçları `Idempotency-Key` başlığı taşır.
- Logo yükleme ve silme dışındaki yazma uçları `If-Match-Row-Version` başlığı taşır.
  Logo yükleme multipart/form-data kodlaması nedeniyle row version'ı istek gövdesinde
  `rowVersion` alanı olarak taşır.
- Platform yöneticisinin kurum marka verisine erişimi zorunlu denetim kaydı üretir.
  Denetim kaydı yazılamazsa cevap fail-closed reddedilir.
- Logo dosyası fiziksel silinmez; `logo_asset_id` temizlendiğinde `file_assets` kaydı
  korunur. Kalıcı fiziksel silme `VERI_MODELI.md` §14'teki arşivleme yaklaşımına tabidir.
- `organization_brand_colors` listesi boş olabilir. Liste, sıfır veya daha fazla
  yardımcı renkten oluşur.
- Bütün modül kodları `AGENT_GOREV_PLANI.md` §5'teki sabit listeden gelir. Modül
  kapatılsa bile yetki denetimi çalışmaya devam eder; modül durumu yalnızca menü
  görünürlüğünü etkiler.

### 2.2. Transaction scope modeli (bağlayıcı)

Tüm marka ayarları işlemleri `ORGANIZATION` scope'unda çalışır. Platform yöneticisinin
hedef kurumlu işlemleri aşağıdaki sırayı izler:

1. Sunucu, doğrulanmış token ve eşleşmiş route üzerinden `actorUserId`, hedef
   `organizationId` ve `operationCode`'u çözer.
2. `operationCode`, bu sözleşmenin dokuz kodluk allow-list'inde
   (`ORG_VIEW_BRAND`, `ORG_UPDATE_BRAND`, `ORG_VIEW_BRAND_COLORS`,
   `ORG_UPDATE_BRAND_COLORS`, `ORG_VIEW_MODULES`, `ORG_UPDATE_MODULES`,
   `ORG_UPLOAD_LOGO`, `ORG_REMOVE_LOGO`, `ORG_VIEW_LOGO`) değilse transaction
   açılmadan fail-closed `403 FORBIDDEN` reddedilir.
3. Transaction içinde `SET LOCAL app.iam_operation_scope = 'ORGANIZATION'`,
   `SET LOCAL app.iam_actor_user_id = :actorUserId`,
   `SET LOCAL app.organization_id = :targetOrgId` ve
   `SET LOCAL app.iam_operation_code = :operationCode` server-set kurulur.
4. Bu bağlamla dar `platform_administrators` actor-only `SELECT` yapılır
   (bkz. `ORG-001` §4.1a — tek kanonik policy; allow-list yukarıdaki dokuz kodu
   içerir).
5. Aktif admin satırı bulunursa `app.iam_platform_admin_support_access = true`
   server-set bayrağı kurulur; bulunamazsa fail-closed `403 FORBIDDEN` (bkz.
   §2.2.2).

§2.2.1'deki aktör bazlı yetkilendirme/scope matrisi **tek bağlayıcı kaynak**dır; bu
belgedeki uç bazlı erişim matrisi blokları (§8.2, §10.2, §12.2, §13.2, §14.2), scope/tablo
matrisi (§4.1) ve replay akışı (§5.4) bu matrisle çelişemez. Eski scope özet tablosu ve
eski bayrak paragrafları kaldırılmıştır.

#### 2.2.1. Aktör bazlı yetkilendirme/scope matrisi (9 uç — bağlayıcı)

Aşağıdaki matris 9 ucun her biri için iki aktörün yetki koşullarını tek kaynak olarak
belirler; uç bazındaki erişim/erişim matrisi bloklarındaki (§8.2, §10.2, §12.2, §13.2,
§14.2, §15.x) ve scope/tablo matrisindeki (§4.1) satırlar bu tabloyla çelişemez.

| Uç | Kurum aktörü — scope ve koşullar | Platform yöneticisi — scope ve koşullar |
|---|---|---|
| `GET /brand` | `ORGANIZATION` scope; `organization_memberships.status = ACTIVE`, ilgili `organization_membership_roles` `revoked_at IS NULL`; `BRAND_MANAGE` izni gerektirmez (salt okunur); `app.iam_platform_admin_support_access` kurulmaz; hedef `organization_id` aktör üyeliğine eşit değilse fail-closed `403 FORBIDDEN`. | `ORGANIZATION` scope; transaction için `app.iam_operation_code = 'ORG_VIEW_BRAND'` allow-list ve dar `platform_administrators` actor-only `SELECT` (§4.1a); satır dönerse `app.iam_platform_admin_support_access = true` server-set kurulur; audit zorunlu → yazılamazsa fail-closed `500 INTERNAL_ERROR`. |
| `PATCH /brand` | `ORGANIZATION` scope; aktif üyelik + (aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni, `organization_membership_permissions.revoked_at IS NULL`) zorunlu (§4.1 yazma yetkisi koşuluyla birebir); destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_UPDATE_BRAND'` allow-list + platform-admin doğrulaması; doğrulama sonrası `app.iam_platform_admin_support_access = true` server-set; audit zorunlu, fail-closed. |
| `GET /brand-colors` | `ORGANIZATION` scope; salt okunur, `BRAND_MANAGE` izni gerektirmez; aktif üyelik zorunlu; destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_VIEW_BRAND_COLORS'`; platform-admin doğrulaması sonrası `app.iam_platform_admin_support_access = true` server-set; audit zorunlu, fail-closed. |
| `PUT /brand-colors` | `ORGANIZATION` scope; aktif üyelik + (aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni) zorunlu (§4.1 yazma yetkisi koşuluyla birebir); destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_UPDATE_BRAND_COLORS'`; platform-admin doğrulaması + destek bayrağı; audit zorunlu, fail-closed. |
| `GET /modules` | `ORGANIZATION` scope; salt okunur, `MODULE_MANAGE` izni gerektirmez; aktif üyelik zorunlu; destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_VIEW_MODULES'`; platform-admin doğrulaması + destek bayrağı; audit zorunlu, fail-closed. |
| `PATCH /modules` | `ORGANIZATION` scope; aktif üyelik + (aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `MODULE_MANAGE` izni) zorunlu (§4.1 yazma yetkisi koşuluyla birebir); destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_UPDATE_MODULES'`; platform-admin doğrulaması + destek bayrağı; audit zorunlu, fail-closed. |
| `PUT /logo` | `ORGANIZATION` scope; aktif üyelik + (aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni) zorunlu (§4.1 yazma yetkisi koşuluyla birebir); destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_UPLOAD_LOGO'`; platform-admin doğrulaması + destek bayrağı; audit zorunlu, fail-closed. |
| `DELETE /logo` | `ORGANIZATION` scope; aktif üyelik + (aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni) zorunlu (§4.1 yazma yetkisi koşuluyla birebir); destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_REMOVE_LOGO'`; platform-admin doğrulaması + destek bayrağı; audit zorunlu, fail-closed. |
| `GET /logo` | `ORGANIZATION` scope; salt okunur, `BRAND_MANAGE` gerektirmez; aktif üyelik zorunlu; destek bayrağı kurulmaz. | `ORGANIZATION` scope; `operationCode = 'ORG_VIEW_LOGO'`; platform-admin doğrulaması + destek bayrağı; audit zorunlu, fail-closed. |

`operationCode`'lar bu sözleşmeye özeldir; sunucu bunları `ORG_VIEW_*` /
`ORG_UPDATE_*` / `ORG_UPLOAD_LOGO` / `ORG_REMOVE_LOGO` allow-list'i dışında kabul
etmez. `PATCH /brand` ve `PUT /brand-colors` tek uç; birden aktör seçeneği yoktur.
Platform yöneticisi hedef kurumlu işlemlerinde kullanılan `platform_administrators`
ORGANIZATION scope `SELECT` RLS policy'si `ORG-001` §4.1a'da tanımlı **tek kanonik
policy**'dir; bu sözleşme ayrı bir policy tanımlamaz. Policy'nin allow-list'i
ORG-001'in beş operation code'u ile birlikte yukarıdaki dokuz kodu da içerir.

#### 2.2.2. Fail-closed reddi kuralları (bağlayıcı)

Aşağıdaki durumlar matristen bağımsız olarak her uçta fail-closed `403 FORBIDDEN`
(veya kapsam gerekliyse `404 RESOURCE_NOT_FOUND`) ile reddedilir; eski başarılı
sonuçlar idempotency replay ile tekrar oynatılmaz:

- Hedef `organization_id` aktörün aktif üyeliğine eşit değilse (kurum izolasyonu).
- `organization_memberships.status ≠ ACTIVE` veya ilgili `organization_membership_roles
  .revoked_at IS NOT NULL` → üyelik/rol geri alınmış aktör.
- Devredilmiş izin gerektiren uçta `organization_membership_permissions.revoked_at
  IS NOT NULL` → izin geri alınmış aktör.
- Platform yöneticisi iddiasında `platform_administrators` actor-only `SELECT` satır
  döndürmemiş veya `revoked_at IS NOT NULL` iken sahte `app.iam_platform_admin_support
  _access = true` bayrağı iddia edilmişse. Bayrağın `SELECT` öncesi kurulmuş gibi
  davranması, başka admin satırı, revoked admin ve yanlış operation code
  `ORG-001` §4.1a negatif test kapılarıdır.
- Çözülen `operationCode` bu sözleşmenin dokuz kodluk allow-list'inin dışındaysa
  transaction açılmadan fail-closed `403 FORBIDDEN`.

Üyelik/izin doğrulaması her transaction içi işlemde yeniden yapılır; replay sırasında
eski `BRAND_MANAGE`/`MODULE_MANAGE` sonucu kullanılmaz.

### 2.3. Logo yaşam döngüsü

- Logo `PUT /organizations/{id}/logo` ile yüklenir. Yükleme, yeni `file_assets` kaydı
  oluşturur ve `organizations.logo_asset_id`'yi günceller.
- Mevcut logo varken yeni logo yüklenirse eski `file_assets` kaydı fiziksel silinmez;
  `logo_asset_id` yeni kayda işaret edecek şekilde güncellenir.
- Logo `DELETE /organizations/{id}/logo` ile kaldırılır; `logo_asset_id` `NULL` yapılır.
  `file_assets` kaydı korunur.

#### 2.3.1. Logo idempotency kuralı (bağlayıcı, çelişkisiz)

Logo binary içeriği `clientMutationId` + binary SHA-256 özetiyle birlikte fingerprint'e
dâhildir. Tek bağlayıcı kural:

- **Aynı `Idempotency-Key` + aynı binary SHA-256 → replay:** İlk başarılı sonuç
  döner; ikinci `file_assets` kaydı oluşmaz, `logo_asset_id` değişmez.
- **Aynı `Idempotency-Key` + farklı binary SHA-256 → `409 IDEMPOTENCY_KEY_REUSED`:**
  Fingerprint çakışması; ikinci yükleme reddedilir, side-effect oluşmaz.
- **Farklı `Idempotency-Key` + aynı binary SHA-256 → yeni yükleme yapılabilir:**
  Farklı `clientMutationId` ile aynı binary içeriği tekrar yüklemek geçerlidir; yeni
  bir `file_assets` kaydı oluşur ve `logo_asset_id` yeni kayda işaret eder. Bu, aynı
  görselin birden fazla kurumda ya da farklı zamanlarda yeniden yüklenmesine izin
  verir; idempotency kapsamı `clientMutationId`'ye göredir, binary içeriğe göre global
  deduplication yapılmaz.

Eski "aynı logo tekrar yüklenirse yeni `file_assets` kaydı oluşür; idempotent yeniden
kullanım yoktur (logo binary içeriği idempotency kapsamına alınmaz)" ifadesi
kaldırılmıştır; binary SHA-256 fingerprint'in bir parçasıdır ve tek kaynak yukarıdaki
 üç kuraldır.

### 2.4. Renk doğrulaması (bağlayıcı)

Sunucu ve mobil istemci **aynı** WCAG 2.1 göreli luminans formülünü uygular
(bkz. `MOBIL_TASARIM_TOKENLARI.md` §3.2.2 ve §3.2.4). Doğrulama kapsamı `primary_color`,
`secondary_color` ve `organization_brand_colors` yardımcı paletinde ayrı ayrı tanımlanır.

#### 2.4.1. WCAG göreli luminans ve on-color seçim algoritması

Her renk `c` için (`#RRGGBB`, `#000000` ve `#FFFFFF` dâhil):

```
L(c) = 0.2126·R_lin + 0.7152·G_lin + 0.0722·B_lin
  R_lin = R/255 ≤ 0.03928 ? R/255/12.92 : ((R/255 + 0.055)/1.055)^2.4  (G, B için aynı)

whiteContrast(c) = 1.05 / (L(c) + 0.05)     # c üzerinde beyaz metin/simge
blackContrast(c) = (L(c) + 0.05) / 0.05     # c üzerinde siyah metin/simge
onColor(c)       = whiteContrast(c) >= blackContrast(c) ? #FFFFFF : #000000
onContrast(c)    = max(whiteContrast(c), blackContrast(c))
```

`onColor` deterministik seçilir: iki adayın yüksek kontrastlı olanı alınır; eşitlikte
`whiteContrast == blackContrast` (L ≈ 0,1791) `#FFFFFF` (beyaz) seçilir. Geçerli sRGB
girdilerinde `onContrast` en az ≈ 4,58:1'dir; bu nedenle `onContrast ≥ 4,5:1` kuralı
geçerli sRGB akışında neredeyse her zaman sağlanır ve bozuk/standart dışı girdiye karşı
**savunmacı doğrulama** olarak korunur.

#### 2.4.2. `primary_color` ve `secondary_color` doğrulaması (bağımsız bağlayıcı)

Her ikisi için ayrı ayrı ve bağımsız olarak:

1. Hex biçimi ASCII regex `^#[0-9A-Fa-f]{6}$` ile zorunlu → uymazsa
   `422 VALIDATION_FAILED` + `<alan>.INVALID_HEX`. Regex harf boyutu duyarsızdır;
   tam 7 karakter (`#` + 6 hex) kabul edilir.
2. `#000000` ve `#FFFFFF` reddedilir → `<alan>.BLACK_OR_WHITE`.
3. `whiteContrast` ve `blackContrast` hesaplanır; `onColor` deterministik seçilir;
   `onContrast ≥ 4,5:1` aranır → sağlanmazsa `<alan>.CONTRAST_NOT_PASSED`.
4. Renk ile `neutral-0` (`#FFFFFF`) arasındaki grafiksel kontrast
   `whiteContrast(c) = 1.05 / (L(c) + 0.05)` değerine eşittir; `≥ 3:1` aranır →
   sağlanmazsa `<alan>.GRAPHICAL_CONTRAST_NOT_PASSED`.

`primary` ve `secondary` farklı bağımsız alanlardır; birinin doğrulaması diğerine
geçmez. `secondary_color` `organization_brand_colors`'tan türetilmez; yardımcı paletin
ilk elemanı/örtük sırası `secondary_color`'u belirlemez.

#### 2.4.3. `organization_brand_colors` yardımcı palet doğrulaması

- Her `colorHex` geçerli `#RRGGBB` olmalı; değilse `422 VALIDATION_FAILED` +
  `colorHex.INVALID_HEX`.
- Aynı `colorHex` birden fazla kez yer alamaz → `colorHex.DUPLICATE`.
- Yardımcı palet için on-color seçimi, `4,5:1` metin kontrastı ve `3:1` grafiksel
  kontrast kontrolü **zorunlu değildir**. Palet wacagı renklerin UI tarafından otomatik
  beyaz/siyah metinle kullanılması varsayımında değildir; UI-003 programatik kontrast
  testleri bu çiftleri doğrular. Sunucu yalnızca hex geçerliliğini ve benzersizliğini
  denetler.

#### 2.4.4. Doğrulama örnekleri (makineyle hesaplanmış)

Aşağıdaki değerler bu sözleşmenin kabul senaryolarında (§16) ve CI doğrulama
script'inde teyit edilmiştir (kaynak: WCAG 2.1 göreli luminans formülü):

| Renk | L | whiteContrast | blackContrast | `onColor` | `onContrast` | `primary`/`secondary` ↔ `neutral-0` |
|---|---|---|---|---|---|---|
| `#2E7D32` (Zeytin primary) | 0,1548 | 5,13:1 | 4,10:1 | `#FFFFFF` | 5,13:1 ✓ | 5,13:1 ✓ (≥ 3:1) |
| `#E65100` (Zeytin secondary) | 0,2271 | 3,79:1 | 5,54:1 | `#000000` | 5,54:1 ✓ | 3,79:1 ✓ (≥ 3:1) |
| `#1565C0` (Safir primary) | 0,1327 | 5,75:1 | 3,66:1 | `#FFFFFF` | 5,75:1 ✓ | 5,75:1 ✓ |
| `#00796B` (Safir secondary) | 0,1474 | 5,32:1 | 3,95:1 | `#FFFFFF` | 5,32:1 ✓ | 5,32:1 ✓ |
| `#C62828` (Nar primary) | 0,1368 | 5,62:1 | 3,73:1 | `#FFFFFF` | 5,62:1 ✓ | 5,62:1 ✓ |
| `#455A64` (Nar secondary) | 0,0950 | 7,24:1 | 2,90:1 | `#FFFFFF` | 7,24:1 ✓ | 7,24:1 ✓ |
| `#6A1B9A` (Lavanta primary) | 0,0618 | 9,39:1 | 2,24:1 | `#FFFFFF` | 9,39:1 ✓ | 9,39:1 ✓ |
| `#006064` (Lavanta secondary) | 0,0929 | 7,35:1 | 2,86:1 | `#FFFFFF` | 7,35:1 ✓ | 7,35:1 ✓ |
| `#6D4C41` (Toprak primary) | 0,0880 | 7,61:1 | 2,76:1 | `#FFFFFF` | 7,61:1 ✓ | 7,61:1 ✓ |
| `#2E7D32` (Toprak secondary) | 0,1548 | 5,13:1 | 4,10:1 | `#FFFFFF` | 5,13:1 ✓ | 5,13:1 ✓ |

Gerçekten başarısız olan örnekler (sınır değer testleri):

| Renk | L | `onColor` | `onContrast` | ↔ `neutral-0` | Sonuç |
|---|---|---|---|---|---|
| `#E0E0E0` | 0,7454 | `#000000` | 15,91:1 ✓ | 1,32:1 — **< 3:1** | `GRAPHICAL_CONTRAST_NOT_PASSED` |
| `#80CBC4` | 0,5129 | `#000000` | 11,26:1 ✓ | 1,86:1 — **< 3:1** | `GRAPHICAL_CONTRAST_NOT_PASSED` |
| `#FF8A00` | 0,3944 | `#000000` | 8,89:1 ✓ | 2,36:1 — **< 3:1** | `GRAPHICAL_CONTRAST_NOT_PASSED` |
| `#949494` | 0,2961 | `#000000` | 6,92:1 ✓ | 3,03:1 ✓ (≥ 3:1 sınır) | Geçer |
| `#959595` | 0,3005 | `#000000` | 7,01:1 ✓ | 2,995:1 — **< 3:1 sınırın hemen altı** | `GRAPHICAL_CONTRAST_NOT_PASSED` |
| `#777777` | 0,1845 | `#000000` | 4,69:1 ✓ | 4,48:1 ✓ | Geçer (`onContrast` ≥ 4,5:1 kuralına yakın-başarılı) |
| `#FFFF00` (saf sarı) | 0,9278 | `#000000` | 19,56:1 ✓ | 1,07:1 — < 3:1 | Sadece `GRAPHICAL_CONTRAST_NOT_PASSED` (saf sarı `onColor` üzerinde `#000000` ile **geçer**; metin reddi **yanlış negatif** olarak kullanılmaz) |

Notlar:

- `#FFFF00` örneği eski sözleşme taslağında "metin kontrastı < 4,5:1" gerekçesiyle
  reddediliyordu; bu yanlıştır çünkü `blackContrast(#FFFF00) ≈ 19,56:1` ≥ 4,5:1 ve
  algoritma `#000000`'ı seçer. Saf sarı yalnızca grafiksel kontrast (`1,07:1` < 3:1)
  nedeniyle reddedilebilir; metin gerekçesi kaldırılmıştır.
- Eşitlik noktası `L ≈ 0,1791`'de `whiteContrast == blackContrast ≈ 4,587:1` ve
  algoritma `#FFFFFF`'i seçer; bu değer `onContrast ≥ 4,5:1` kuralının geçerli sRGB
  için sağlanabilen minimumudur.

### 2.5. Etkin modül kuralları

- Modül listesi sistem başlangıcında bütün modüller `is_enabled = true` ve
  `sort_order = 0` ile oluşturulur.
- Yönetici modülü kapatabilir (`is_enabled = false`). Kapatılan modüle ait menü
  ögeleri kullanıcıya gösterilmez. Modülün API uçları yetki denetimine tabi olmaya
  devam eder; `is_enabled` yalnızca menü görünürlüğünü etkiler, yetkisiz erişim
  sağlamaz veya engellemez.
- `sort_order` menüdeki görünüm sırasını belirler; aynı değerde modül kodu
  alfabetik sıralanır.
- En az `CORE`, `IAM` ve `ORG` modülleri her zaman etkindir; kapatılamaz.
  Kapatma girişimi `422 VALIDATION_FAILED` döndürür.
- Modül kodları sistem tarafından sabitlenir; yeni modül kodu eklenemez veya
  silinemez. Yalnızca `is_enabled` ve `sort_order` değiştirilebilir.

### 2.6. İzin modeli

| İşlem | Kurum yöneticisi | `BRAND_MANAGE` izinli hoca | Platform admin |
|---|---|---|---|
| Marka ayarı (`primary_color`) | İzinli | İzinli | İzinli (denetlenir) |
| Renk paleti yönetimi | İzinli | İzinli | İzinli (denetlenir) |
| Logo yükleme/silme | İzinli | İzinli | İzinli (denetlenir) |
| Logo görüntüleme | İzinli | İzinli | İzinli (denetlenir) |
| Modül yönetimi | İzinli | İzinli (`MODULE_MANAGE` izni varsa) | İzinli (denetlenir) |
| Modül listeleme | İzinli | İzinli | İzinli (denetlenir) |

`BRAND_MANAGE` ve `MODULE_MANAGE` izin kodları mevcut `permission_catalog` seed
sabitleridir (`VERI_MODELI.md` §4.8 ORG_SETTINGS kategorisi, `P-008`); bu sözleşme yeni
bir izin kodu tanımlamaz. Kurum yöneticisi bu izinleri `YETKI_MATRISI.md` kararı
uyarınca hocaya ayrı ve geri alınabilir şekilde devredebilir; atama/geri alma uygulaması
`STAFF-*` ve değerlendirme algoritması `PERM-002` kapsamında yürütülür.

### 2.7. Organizasyon durumuna göre erişim

| Organizasyon durumu | Org aktörü | Platform admin |
|---|---|---|
| `ACTIVE` | Tam erişim (yetkiye bağlı) | Tam erişim (denetlenir) |
| `SUSPENDED` | `403 FORBIDDEN` | Tam erişim |
| `ARCHIVED` | `403 FORBIDDEN` | Salt okunur (`GET /brand`, `GET /brand-colors`, `GET /modules`, `GET /logo`) |

`ARCHIVED` kurumda platform admin logo yükleyemez/silemez, marka ayarı değiştiremez,
renk paleti veya modül güncelleyemez → `409 STATE_CONFLICT`.

### 2.8. Audit (denetim) şeması ve v2 katalog sahipliği (bağlayıcı)

Marka yazma uçları (`PATCH /brand`, `PUT /brand-colors`, `PATCH /modules`,
`PUT /logo`, `DELETE /logo`) her başarılı mutasyon ile aynı transaction'da
`ORG_SETTING_CHANGED` audit eylemi yazar (bkz. `VERI_MODELI.md` §13,
`DENETIM_VE_GERI_ALMA_ILKELERI.md`). Audit başarısız olursa mutasyonlar
rollback yapılır, `500 INTERNAL_ERROR` döner (§5.4 adım 6).

#### 2.8.1. v1 audit şemasının sınırları (değişmez)

`AUDIT-001A`'nın `V2__audit_core.sql` migration'ında fiziksel olarak tanımlı
`ORG_SETTING_CHANGED` **v1** (`payload_schema_version = 1`) katalog satırı
aşağıdaki kapalı `payload_schema`'yı taşır:

```
oldValue.allowed: ["name","shortName","defaultTimezone","primaryColor",
                    "logoAssetId","enabledModules","attendanceStatuses","rowVersion"]
newValue.allowed: ["name","shortName","defaultTimezone","primaryColor",
                    "logoAssetId","enabledModules","attendanceStatuses","rowVersion"]
eventMetadata.allowed: ["operationCode"]
rejectUnknown: true
```

v1 şema **`secondaryColor` ve `brandColors` alanlarını kabul etmez**. `V2__audit_core
.sql` migration'ı değişmez (immutable): mevcut katalog satırı veya şema sürümü bu
sözleşmeyle dokunulmaz, değiştirilmez.

ORG-002 uygulamaları (`secondaryColor` ve `brandColors` içeren marka yazmaları)
v1 şemaya uymadığı için v1 katalog satırıyla audit yazamazlar. Bu sözleşme v1'i
kullanmayı gerektirmez; v1 yalnızca geçmiş/beklenen ORG-001 yaşam döngüsü yazmaları
için referans niteliğindedir.

#### 2.8.2. v2 audit şeması — payload_schema_version=2 (bağlayıcı)

`payload_schema_version = 2` için `ORG_SETTING_CHANGED` katalog satırı
aşağıdaki kapalı `payload_schema`'yı taşır:

```
oldValue.allowed: ["name","shortName","defaultTimezone","primaryColor",
                    "secondaryColor","logoAssetId","enabledModules","brandColors",
                    "attendanceStatuses","rowVersion"]
newValue.allowed: ["name","shortName","defaultTimezone","primaryColor",
                    "secondaryColor","logoAssetId","enabledModules","brandColors",
                    "attendanceStatuses","rowVersion"]
eventMetadata.allowed: ["operationCode"]
rejectUnknown: true
```

- v2 izinli alanlar = v1 alanları + `secondaryColor` + `brandColors` (rowVersion zaten
  v1'de mevcuttur). `allowed` listesi, bir yazmada **görünebilecek üst kümedir**;
  §2.8.2a'daki "yalnız değişen alan" kuralı gereği tek bir yazmada bu alanların
  hepsi aynı anda bulunmaz.
- `brandColors` array biçimindedir; her öğesi `{colorHex, sortOrder}` çiftini taşır
  (`organization_brand_colors` tablosunun anlık görüntüsü); değişiklik olduğunda
  tüm liste (silinen/eklenen/sıra değişen dâhil) yeni durumun tam anlık görüntüsü
  olarak yazılır — kısmi/delta liste yazılmaz.
- `enabledModules` v2'de `organization_modules` tablosunun **tam anlık görüntüsüdür**:
  array biçiminde, her öğesi `{moduleCode, isEnabled, sortOrder}` üçlüsünü taşır
  (bkz. §6.4 modül nesnesi ile birebir alan adları). Örnek:
  `[{"moduleCode":"ATT","isEnabled":true,"sortOrder":0},
  {"moduleCode":"PROGRAM","isEnabled":false,"sortOrder":1}]`. Liste, sabit modül
  setinin **tamamını** (etkin + devre dışı) `sortOrder` artan sırada içerir;
  yalnızca etkin modül kodlarının listesi değildir. `PATCH /modules` herhangi bir
  `isEnabled` veya `sortOrder` değiştirdiğinde `enabledModules` alanı tam yeni
  anlık görüntüyle yazılır — kısmi/delta liste yazılmaz.
- Yeni/unknown alanlar v2'de de **reddedilir** (`rejectUnknown: true`); serbest
  JSON alanı açılmaz.

#### 2.8.2a. Audit payload'ında yalnız değişen alanlar (bağlayıcı, PR #43 `SettingChange` modeliyle hizalı)

Bir `ORG_SETTING_CHANGED` yazmasında `oldValue`/`newValue`, o mutasyonda **fiilen
değişen alanları** taşır; değişmeyen alan **oldValue==newValue olarak da dâhil
edilmez, tamamen çıkarılır (omit)**. Bu, `PR #43`'ün `AuditEvent.SettingChange`
sınıfındaki `changedFields` kümesi + serileştirme kuralıyla birebir aynıdır:
değişen alan JSON `null`/`[]` olsa bile daima yazılır, dokunulmayan alan JSON
gövdesinde hiç görünmez.

- Örnek: `PATCH /brand` yalnız `primaryColor`'u değiştirdiyse `oldValue` ve
  `newValue` **yalnız** `primaryColor` (+ `rowVersion`, her mutasyonda değiştiği
  için daima dâhildir) alanını taşır; `secondaryColor`, `shortName` vb.
  değişmeyen alanlar oldValue/newValue'da **hiç yer almaz**.
- `rowVersion` her başarılı yazmada değiştiği için pratikte her zaman "değişen
  alan" kümesindedir ve daima dâhildir.
- `rejectUnknown: true` kuralı hâlâ geçerlidir: mevcut olan her alan adı
  §2.8.2'deki `allowed` listesinde olmalıdır; sadece hangi alanların *o mutasyonda*
  mevcut olacağı değişti (üst küme → değişen alt küme).
- ORG-003/ORG-005 audit yazıcısı, `SettingChange`'e yalnız fiilen değişen alanları
  ekler (`requireChanged` eşitlik kontrolüyle); eşit değer için setter çağrılırsa
  hata fırlatılır — sunucu tarafı kazara "değişmeyen alanı da yaz" davranışına
  fail-fast kapalıdır.

#### 2.8.3. Fiziksel v2 katalog seed'inin sahipliği (ORG-003)

`ORG_SETTING_CHANGED` v2 katalog satırını ekleyen fiziksel migration **ORG-003'e
aittir**. `V2__audit_core.sql` (AUDIT-001A) değişmez; ORG-003 kendi Flyway
migration'ında (örn. `V3__...`) aşağıdaki kabul ölçütünü sağlamalıdır (bkz.
`ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §4.1c):

- Yeni `INSERT INTO audit_action_catalog` satırı `(code='ORG_SETTING_CHANGED',
  payload_schema_version=2, ...)` ile eklenir; mevcut v1 katalog satırı
  **değiştirilmez** ve **silinmez**.
- v2 `payload_schema` yukarıdaki §2.8.2 şablonuyla **birebir aynı** olmalıdır;
  farklı izinli alan seti yanlış pozitif/negatif audit doğrulamasına yol açar.
- ORG-003 runtime rolü `audit_logs` `INSERT` policy/grant'i ile bu satırı
  yazabilmeli; audit başarısız olursa ORG lifecycle transaction'ı fail-closed
  rollback yapmalı (testlerle kanıtlanmalı).
- ORG-005 (marka API backend) v1 değil yalnız v2 katalog satırıyla audit
  yazabilmeli; v1 yoluna düşerse fail-closed reddedilmeli (testle kanıtlanmalı).

`ORG-003` PR'ı henüz merge edilmediğinden, bu sözleşme v2 migration'ın kendisini
içermez; yalnızca şema sahipliğini ve kabul ölçütünü bağlar.

#### 2.8.3a. ORG-003 → ORG-002 açık bağımlılığı (bağlayıcı)

`ORG-003` (PR #43), aynı sözleşme/migration sınırına bağlı olduğu için bu belgenin
(`ORG-002`, PR #41) merge edilmesini bekler; PR #41 merge olana kadar PR #43'e yeni
audit katalog/RLS değişikliği pushlanmaz. PR #41 merge sonrasında PR #43:

1. Güncel `main`'e **rebase edilir**.
2. Kendi `V3__...` migration'ında bu sözleşmenin §2.8.2 payload_schema'sını
   (izinli alan üst kümesi, `brandColors`/`enabledModules` anlık görüntü
   biçimleri) ve §2.8.2a "yalnız değişen alan" (`changedFields`) audit modelini
   **birebir** uygulamalıdır — `AuditEvent.SettingChange` sınıfı bu modele göre
   genişletilir (`secondaryColor`, `brandColors`, tam `{moduleCode, isEnabled,
   sortOrder}` modül anlık görüntüsü desteği eklenir).
3. §2.8.3'teki kabul ölçütü (v1 satırı korunur, v2 şeması birebir, fail-closed
   rollback, ORG-005 yalnız v2 yazar) PR #43'ün kendi kabul kriterlerine dâhil
   edilir.

Bu bağımlılık PR #43 tarafında ayrıca doğrulanana kadar tek yönlüdür: PR #41 bu
sözleşmeyi PR #43'ün mevcut `SettingChange` implementasyonuna (v1, `changedFields`
modeli) göre hizalar; PR #43 ise v2 desteğini bu sözleşmeye göre sonradan ekler.

## 3. Aktörler ve ön koşullar

### 3.1. Mobil istemci ön koşulları

- İstemci `IAM-001` sözleşmesine göre geçerli bir platform oturumu kurmuş olmalıdır.
- Kurum yöneticisi ve hoca işlemleri `ORGANIZATION` kapsamlı token gerektirir.
- Platform yöneticisi hedef kurumlu işlemler için `GLOBAL_PLATFORM_ADMIN` kapsamlı
  token ile gelir; transaction içinde platform-admin bayrağı kurulur.
- Logo yükleme ve silme dışındaki yazma isteklerinde `If-Match-Row-Version` başlığı
  zorunludur.
- Logo yükleme isteği `multipart/form-data` kodlaması kullanır; `rowVersion` gövde
  alanı olarak taşınır.

### 3.2. Backend ön koşulları

- `organizations`, `organization_brand_colors`, `organization_modules`, `file_assets`,
  `platform_administrators`, `idempotency_keys`, `audit_logs` tabloları
  `VERI_MODELI.md` ile uyumlu mevcuttur.
- Logo yükleme/silme uçları, `A-007R` ve `OPS-005` nesne deposu provisioning'i
  tamamlanana kadar `501 NOT_IMPLEMENTED` döner. Diğer bütün uçlar (renk, modül)
  dosyasız çalışır.
- Renk kontrastı hesaplaması için sunucu WCAG 2.1 göreli parlaklık ve kontrast oranı
  formülünü uygular.

## 4. Kaynaklar ve uçlar

Bu sözleşme dokuz ORG marka uç yüzeyini bağlar:

1. `GET /api/v1/organizations/{organizationId}/brand`
2. `PATCH /api/v1/organizations/{organizationId}/brand`
3. `GET /api/v1/organizations/{organizationId}/brand-colors`
4. `PUT /api/v1/organizations/{organizationId}/brand-colors`
5. `GET /api/v1/organizations/{organizationId}/modules`
6. `PATCH /api/v1/organizations/{organizationId}/modules`
7. `PUT /api/v1/organizations/{organizationId}/logo`
8. `DELETE /api/v1/organizations/{organizationId}/logo`
9. `GET /api/v1/organizations/{organizationId}/logo`

### 4.1. İşlem × scope × tablo matrisi (bağlayıcı)

`AUTHZ` sütunu kurum aktörü için transaction içi yetkilendirmenin salt-okunur
tablolarını listeler; aşağıdaki üç tablo her kurum aktörü satırı için zorunludur:

- `organization_memberships` (salt okunur; `status = ACTIVE` ve hedef
  `organization_id` eşleşmesi),
- `organization_membership_roles` (salt okunur; ilgili roller için `revoked_at IS NULL`),
- `organization_membership_permissions` (salt okunur; yazma uçlarında **yalnız
  `TEACHER` dalında** sorgulanır — aktör aktif `ORG_ADMIN` rolüne sahipse bu
  tablo hiç `SELECT` edilmez, izin kontrolü tamamen atlanır).
- `permission_catalog` (salt okunur; yalnız `TEACHER` dalında `BRAND_MANAGE`/
  `MODULE_MANAGE` kodu sorgulanırken; `ORG_ADMIN` dalında sorgulanmaz).

Kurum aktörü yazma yetkisi koşulu: `organization_memberships.status = ACTIVE` AND
(
  `organization_membership_roles`'de `revoked_at IS NULL` bir `ORG_ADMIN` rolü
  OR
  `revoked_at IS NULL` bir `TEACHER` rolü + ilgili `organization_membership_permissions`
  satırında `revoked_at IS NULL` `BRAND_MANAGE`/`MODULE_MANAGE` izni
). Salt-okunur uçlarda (`GET /brand`, `GET /brand-colors`, `GET /modules`,
`GET /logo`) aktif üyelik ve aktif rol zorunludur; devredilmiş yazma izni zorunlu
değildir. Başka kurum, revoked üyelik/rol/izin fail-closed `403 FORBIDDEN`'dir
(bkz. §2.2.2).

| İşlem | `operationCode` | Scope | Tablolar | SQL işlemleri |
|---|---|---|---|---|
| `GET /brand` (platform admin hedefli) | `ORG_VIEW_BRAND` | `ORGANIZATION` + platform-admin bayrağı | `organizations` (salt okunur), `organization_brand_colors` (salt okunur), `platform_administrators` (salt okunur), `audit_logs` | `SELECT` platform_administrators; `SELECT` organization; `SELECT` brand_colors; `INSERT` audit |
| `GET /brand` (org aktörü) | `ORG_VIEW_BRAND` | `ORGANIZATION` | `AUTHZ` (üyelik + rol, izin zorunlu değil), `organizations` (salt okunur), `organization_brand_colors` (salt okunur) | `SELECT` membership/rol; `SELECT` organization; `SELECT` brand_colors |
| `PATCH /brand` (platform admin hedefli) | `ORG_UPDATE_BRAND` | `ORGANIZATION` + platform-admin bayrağı | `organizations`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `SELECT ... FOR UPDATE` organization; `UPDATE` primary_color ve/veya secondary_color (yalnız istektekiler); `UPDATE` rowVersion +1; `INSERT` audit (yalnız değişen alan); `INSERT`/koşullu `UPDATE` idempotency |
| `PATCH /brand` (org aktörü) | `ORG_UPDATE_BRAND` | `ORGANIZATION` | `AUTHZ` (üyelik + aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni), `organizations`, `audit_logs`, `idempotency_keys` | `SELECT` membership/rol; `ORG_ADMIN` ise izin `SELECT` atlanır, `TEACHER` ise `BRAND_MANAGE` izin `SELECT` zorunlu; `SELECT ... FOR UPDATE` organization; `UPDATE` primary_color ve/veya secondary_color (yalnız istektekiler); `UPDATE` rowVersion +1; `INSERT` audit (yalnız değişen alan); `INSERT`/koşullu `UPDATE` idempotency |
| `GET /brand-colors` (platform admin hedefli) | `ORG_VIEW_BRAND_COLORS` | `ORGANIZATION` + platform-admin bayrağı | `organization_brand_colors` (salt okunur), `platform_administrators` (salt okunur), `audit_logs` | `SELECT` platform_administrators; `SELECT` brand_colors; `INSERT` audit |
| `GET /brand-colors` (org aktörü) | `ORG_VIEW_BRAND_COLORS` | `ORGANIZATION` | `AUTHZ` (üyelik + rol, izin zorunlu değil), `organization_brand_colors` (salt okunur) | `SELECT` membership/rol; `SELECT` brand_colors |
| `PUT /brand-colors` (platform admin hedefli) | `ORG_UPDATE_BRAND_COLORS` | `ORGANIZATION` + platform-admin bayrağı | `organization_brand_colors`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `SELECT ... FOR UPDATE` organization (rowVersion); `DELETE` tüm eski; `INSERT` yeni liste; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `PUT /brand-colors` (org aktörü) | `ORG_UPDATE_BRAND_COLORS` | `ORGANIZATION` | `AUTHZ` (üyelik + aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni), `organization_brand_colors`, `audit_logs`, `idempotency_keys` | `SELECT` membership/rol; `ORG_ADMIN` ise izin `SELECT` atlanır, `TEACHER` ise `BRAND_MANAGE` izin `SELECT` zorunlu; `SELECT ... FOR UPDATE` organization (rowVersion); `DELETE` tüm eski; `INSERT` yeni liste; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `GET /modules` (platform admin hedefli) | `ORG_VIEW_MODULES` | `ORGANIZATION` + platform-admin bayrağı | `organization_modules` (salt okunur), `platform_administrators` (salt okunur), `audit_logs` | `SELECT` platform_administrators; `SELECT` modules; `INSERT` audit |
| `GET /modules` (org aktörü) | `ORG_VIEW_MODULES` | `ORGANIZATION` | `AUTHZ` (üyelik + rol, izin zorunlu değil), `organization_modules` (salt okunur) | `SELECT` membership/rol; `SELECT` modules |
| `PATCH /modules` (platform admin hedefli) | `ORG_UPDATE_MODULES` | `ORGANIZATION` + platform-admin bayrağı | `organization_modules`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `SELECT ... FOR UPDATE` organization (rowVersion); `UPDATE` is_enabled/sort_order; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `PATCH /modules` (org aktörü) | `ORG_UPDATE_MODULES` | `ORGANIZATION` | `AUTHZ` (üyelik + aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `MODULE_MANAGE` izni), `organization_modules`, `audit_logs`, `idempotency_keys` | `SELECT` membership/rol; `ORG_ADMIN` ise izin `SELECT` atlanır, `TEACHER` ise `MODULE_MANAGE` izin `SELECT` zorunlu; `SELECT ... FOR UPDATE` organization (rowVersion); `UPDATE` is_enabled/sort_order; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `PUT /logo` (platform admin hedefli) | `ORG_UPLOAD_LOGO` | `ORGANIZATION` + platform-admin bayrağı | `file_assets`, `organizations`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `INSERT` file_assets; `SELECT ... FOR UPDATE` organization; `UPDATE` logo_asset_id; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `PUT /logo` (org aktörü) | `ORG_UPLOAD_LOGO` | `ORGANIZATION` | `AUTHZ` (üyelik + aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni), `file_assets`, `organizations`, `audit_logs`, `idempotency_keys` | `SELECT` membership/rol; `ORG_ADMIN` ise izin `SELECT` atlanır, `TEACHER` ise `BRAND_MANAGE` izin `SELECT` zorunlu; `INSERT` file_assets; `SELECT ... FOR UPDATE` organization; `UPDATE` logo_asset_id; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `DELETE /logo` (platform admin hedefli) | `ORG_REMOVE_LOGO` | `ORGANIZATION` + platform-admin bayrağı | `organizations`, `platform_administrators` (salt okunur), `audit_logs`, `idempotency_keys` | `SELECT` platform_administrators; `SELECT ... FOR UPDATE` organization; `UPDATE` logo_asset_id = NULL; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `DELETE /logo` (org aktörü) | `ORG_REMOVE_LOGO` | `ORGANIZATION` | `AUTHZ` (üyelik + aktif `ORG_ADMIN` rolü OR aktif `TEACHER` rolü + devredilmiş `BRAND_MANAGE` izni), `organizations`, `audit_logs`, `idempotency_keys` | `SELECT` membership/rol; `ORG_ADMIN` ise izin `SELECT` atlanır, `TEACHER` ise `BRAND_MANAGE` izin `SELECT` zorunlu; `SELECT ... FOR UPDATE` organization; `UPDATE` logo_asset_id = NULL; `UPDATE` rowVersion +1; `INSERT` audit; `INSERT`/koşullu `UPDATE` idempotency |
| `GET /logo` (platform admin hedefli) | `ORG_VIEW_LOGO` | `ORGANIZATION` + platform-admin bayrağı | `organizations` (salt okunur), `file_assets` (salt okunur), `platform_administrators` (salt okunur), `audit_logs` | `SELECT` platform_administrators; `SELECT` organization; logo varsa `SELECT` file_assets ve nesne deposundan akış; `INSERT` audit |
| `GET /logo` (org aktörü) | `ORG_VIEW_LOGO` | `ORGANIZATION` | `AUTHZ` (üyelik + rol, izin zorunlu değil), `organizations` (salt okunur), `file_assets` (salt okunur) | `SELECT` membership/rol; `SELECT` organization; logo varsa `SELECT` file_assets ve nesne deposundan akış |

Bu matrisin dışındaki tablo erişimi sözleşme ihlalidir. Platform admin hedefli
satırlarda `platform_administrators` actor-only `SELECT`'i `ORG-001` §4.1a'daki tek
kanonik RLS policy'si ile yapılır; bu sözleşme ayrı bir policy tanımlamaz.

## 5. Ortak istek/cevap kuralları

### 5.1. Genel kurallar

- `API_GENEL_KURALLARI.md` bölüm 3, 4, 5 ve 7 bu belge için aynen geçerlidir.
- Bütün yazma uçları `Idempotency-Key` başlığı taşır.
- Logo yükleme (`PUT /logo`) dışındaki yazma uçları `If-Match-Row-Version` başlığı taşır.
- Logo yükleme, `rowVersion` değerini `multipart/form-data` gövde alanı olarak taşır.
- Logo silme `If-Match-Row-Version` başlığı taşır.
- `PATCH /brand`, `PUT /brand-colors`, `PATCH /modules` için
  `If-Match-Row-Version` güncel `organizations.rowVersion` ile eşleşmezse
  `409 VERSION_CONFLICT` döner.
- Okuma uçları `Idempotency-Key` ve `If-Match-Row-Version` taşımaz.

### 5.2. İdempotency kapsamı

Tüm marka yazma işlemleri `ORGANIZATION` scope'unda çalışır. İdempotency kapsamı:

- `organizationId + actorUserId + clientMutationId`

Logo yükleme için ek olarak binary SHA-256 özeti fingerprint'e dâhil edilir (bkz.
§2.3.1). `clientMutationId` (yani `Idempotency-Key`) başına tek bir sonuç saklanır;
farklı `clientMutationId` ile aynı binary içeriğin tekrar yüklenmesi engellenmez.

### 5.3. Request fingerprint

Yazma işlemleri için fingerprint'ler `operationCode` ve gövde özetini bağlar;
`PATCH /brand` gövdesi `primaryColor` ve `secondaryColor` alanlarının **her ikisini
birden** canonical sırayla (alan adına göre alfabetik) içerir; yalnızca bir alan
gönderilse dahi diğer alan yokluğuyla sabitlenmiş biçimde fingerprint'e dâhildir
(bkz. §8.3).

- **PATCH /brand:** `SHA-256(PATCH + /api/v1/organizations/{orgId}/brand + ORG_UPDATE_BRAND + <orgId> + SHA-256(body) + <rowVersion>)`
- **PUT /brand-colors:** `SHA-256(PUT + /api/v1/organizations/{orgId}/brand-colors + ORG_UPDATE_BRAND_COLORS + <orgId> + SHA-256(body) + <rowVersion>)`
- **PATCH /modules:** `SHA-256(PATCH + /api/v1/organizations/{orgId}/modules + ORG_UPDATE_MODULES + <orgId> + SHA-256(body) + <rowVersion>)`
- **PUT /logo:** `SHA-256(PUT + /api/v1/organizations/{orgId}/logo + ORG_UPLOAD_LOGO + <orgId> + SHA-256(logo-binary) + <rowVersion>)`
- **DELETE /logo:** `SHA-256(DELETE + /api/v1/organizations/{orgId}/logo + ORG_REMOVE_LOGO + <orgId> + SHA-256("") + <rowVersion>)`

### 5.4. Replay akış sıralaması (bağlayıcı)

Bütün yazma işlemlerinde replay değerlendirmesi `ORG-001` §5.5 ile aynı sıradadır:

1. **Authentication:** token doğrulaması.
2. **Operation code allow-list (transaction öncesi):** route'tan çözülen `operationCode`
   bu sözleşmenin dokuz kodluk allow-list'inde değilse transaction açılmadan
   fail-closed `403 FORBIDDEN` (§2.2 adım 2).
3. **Authorization (transaction içi):** güncel aktör yetkisi kontrolü.
   - **Kurum aktörü:** `organization_memberships`/`organization_membership_roles`/
     `organization_membership_permissions`/`permission_catalog` üzerinden §4.1
     `AUTHZ` koşulu doğrulanır. Yetkisi geri alınmış aktör bu adımda reddedilir;
     eski idempotency sonucu replay edilmez.
   - **Platform yöneticisi:** `platform_administrators` actor-only `SELECT`
     (`ORG-001` §4.1a RLS policy'si) doğrulanır; `revoked_at IS NULL` satır
     dönerse `app.iam_platform_admin_support_access = true` kurulur.
4. **Fingerprint / idempotency replay:** aynı `Idempotency-Key` + aynı fingerprint
   ile daha önce tamamlanmış işlem varsa ilk sonucun eşdeğeri döner. Aynı key +
   farklı fingerprint → `409 IDEMPOTENCY_KEY_REUSED`.
5. **State / rowVersion:** `If-Match-Row-Version` ve durum ön koşulu doğrulanır.
6. **Mutation:** değişiklik uygulanır; `rowVersion +1`; audit ve idempotency sonucu
   aynı transaction'da yazılır. Audit `INSERT` başarısız olursa tüm mutasyonlar
   rollback yapılır; `500 INTERNAL_ERROR` döner.

## 6. Veri şekilleri

### 6.1. Marka nesnesi

```json
{
  "primaryColor": "#2E7D32",
  "secondaryColor": "#E65100",
  "rowVersion": 7,
  "logo": {
    "assetId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
    "downloadUrl": "/api/v1/organizations/4a9cc266-53f0-4dd6-a388-9b13fcaaf3db/logo",
    "originalFilename": "logo.png",
    "mimeType": "image/png",
    "sizeBytes": 245760
  }
}
```

- `logo` nesnesi `null` olabilir (logo yüklenmemişse).
- `primaryColor` ve `secondaryColor` ayrı, bağımsız ve bağlayıcı alanlardır. Her ikisi
  de eksikse varsayılan tema (Zeytin) değerleri `#2E7D32` / `#E65100` kabul edilir.
- `rowVersion` güncel `organizations.rowVersion` tamsayı değeridir; `GET /brand` ve
  `PATCH /brand` cevaplarında aynı alan adını kullanır. İstemci sonraki yazma
  işleminde `If-Match-Row-Version: <rowVersion>` (veya `PUT /logo` gövdesinde
  `rowVersion`) başlığı olarak bu değeri geri gönderir; eşleşmezse `409
  VERSION_CONFLICT` döner (bkz. §5.1).
- `downloadUrl` platform API yoludur; sağlayıcı ön-imzalı URL'si veya kalıcı herkese
  açık URL değildir.

### 6.2. Logo yanıtı (logo varken)

```json
{
  "assetId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
  "downloadUrl": "/api/v1/organizations/4a9cc266-53f0-4dd6-a388-9b13fcaaf3db/logo",
  "originalFilename": "logo.png",
  "mimeType": "image/png",
  "sizeBytes": 245760,
  "uploadedAt": "2026-07-17T10:00:00Z"
}
```

### 6.3. Renk paleti nesnesi

```json
{
  "rowVersion": 7,
  "items": [
    { "colorHex": "#FFC107", "sortOrder": 1 },
    { "colorHex": "#FF5722", "sortOrder": 2 }
  ]
}
```

- `items` boş dizi olabilir.
- `sortOrder` 0'dan başlar; artan sırada sıralanır. Eşit `sortOrder` değerinde
  `colorHex` alfabetik bağlayıcıdır.
- `rowVersion` güncel `organizations.rowVersion` tamsayı değeridir; `GET /brand-colors`
  ve `PUT /brand-colors` cevaplarında aynı alan adını kullanır. `organization_brand
  _colors` tablosunun kendi `rowVersion`'ı yoktur; optimistic concurrency kaynağı
  kurum satırıdır. İstemci sonraki `PUT /brand-colors` yazma işleminde
  `If-Match-Row-Version: <rowVersion>` başlığı olarak bu değeri geri gönderir;
  eşleşmezse `409 VERSION_CONFLICT` döner (bkz. §5.1).

### 6.4. Modül nesnesi

```json
{
  "moduleCode": "ATT",
  "isEnabled": true,
  "sortOrder": 3
}
```

### 6.5. Modül listesi

```json
{
  "rowVersion": 7,
  "items": [
    { "moduleCode": "ATT", "isEnabled": true, "sortOrder": 0 },
    { "moduleCode": "PROGRAM", "isEnabled": true, "sortOrder": 1 },
    { "moduleCode": "CONTENT", "isEnabled": false, "sortOrder": 2 },
    { "moduleCode": "PROGRESS", "isEnabled": true, "sortOrder": 3 },
    { "moduleCode": "EXPORT", "isEnabled": true, "sortOrder": 4 },
    { "moduleCode": "AUDIT", "isEnabled": true, "sortOrder": 5 }
  ]
}
```

- Listeye dâhil edilecek modül kodları: `ATT`, `PROGRAM`, `CONTENT`, `PROGRESS`,
  `EXPORT`, `AUDIT`.
- `CORE`, `IAM`, `ORG`, `TERM`, `CLS`, `PEOPLE` altyapı modülleri listeye dâhil
  edilmez; her zaman etkindir ve yönetim arayüzünde gösterilmez.
- `SYNC`, `REALTIME` altyapı modülleri listeye dâhil edilmez.
- `NOTIFY` Dalga 8'de listeye eklenecektir; V1'de modül listesinde yer almaz.
- `rowVersion` güncel `organizations.rowVersion` tamsayı değeridir; `GET /modules` ve
  `PATCH /modules` cevaplarında aynı alan adını kullanır. `organization_modules`
  tablosunun kendi `rowVersion`'ı yoktur; optimistic concurrency kaynağı kurum
  satırıdır. İstemci sonraki `PATCH /modules` yazma işleminde
  `If-Match-Row-Version: <rowVersion>` başlığı olarak bu değeri geri gönderir;
  eşleşmezse `409 VERSION_CONFLICT` döner (bkz. §5.1).

### 6.6. Sunucu alan kuralları

- `primaryColor`: `^#[0-9A-Fa-f]{6}$` regex'ine uyan 7 karakterlik `#RRGGBB`,
  §2.4.2 kuralları geçerli. `#000000` ve `#FFFFFF` reddedilir. Eksikse varsayılan
  `#2E7D32`.
- `secondaryColor`: `^#[0-9A-Fa-f]{6}$` regex'ine uyan 7 karakterlik `#RRGGBB`,
  §2.4.2 kuralları geçerli. `#000000` ve `#FFFFFF` reddedilir. Eksikse varsayılan
  `#E65100`. `primary`'den bağımsız bir kurum rengidir; `organization_brand_colors`
  'tan türetilmez.
- `colorHex` (palet): `^#[0-9A-Fa-f]{6}$` regex'ine uyan 7 karakterlik `#RRGGBB`.
  Palette benzersiz. Yardımcı palet `secondaryColor`'u belirlemez; ilk eleman/örtük
  sıra yorumu yoktur.
- `moduleCode`: `AGENT_GOREV_PLANI.md` §5'teki sabit listeden.
- `isEnabled`: `BOOLEAN`, `false` ise menüde gösterilmez.
- `sortOrder`: `INTEGER`, 0–999 arası.
- Logo binary: `image/png` veya `image/jpeg` MIME, en fazla 5 MB. Bu sınırlar **bu
  sözleşmede bağlayıcıdır**; zararlı içerik taraması ve nesne deposu entegrasyonu
  `ORG-010` kapsamında kesinleşmektedir.

#### 6.6.1. Onaylı varsayılan tema değerleri (bağlayıcı)

Aşağıdaki değerler `MOBIL_TASARIM_TOKENLARI.md` §3.2.1 ön tanımlı temalarıyla birebir
aynıdır. Kurum renkleri eksik/null olduğunda, kimlik alanı güncellemesi bu değerleri
kullanır; kontrast doğrulaması bu değerler de geçer (§2.4.4 hesaplanmış tablosu).

| Anahtar | Varsayılan (`primaryColor`) | Varsayılan (`secondaryColor`) |
|---|---|---|
| Varsayılan (Zeytin) | `#2E7D32` | `#E65100` |
| Safir | `#1565C0` | `#00796B` |
| Nar | `#C62828` | `#455A64` |
| Lavanta | `#6A1B9A` | `#006064` |
| Toprak | `#6D4C41` | `#2E7D32` |

Sistem, kurum oluşturulurken her iki renk de eksikse **bu beş temadan** varsayılan
olarak Zeytin temasını uygular; bu tablo yalnızca ilk oluşturma/eksik alan
varsayılanı içindir.

`PATCH /brand` gönderiminde bu tablo **örtük tamamlama için kullanılmaz**:
`primaryColor` ve `secondaryColor` birbirinden bağımsız, ayrı gönderilen alanlardır.
Yalnızca `primaryColor` (veya yalnızca `secondaryColor`) gönderilirse sunucu
gönderilmeyen alanı **hiçbir temadan türetmez, değiştirmez**; kurumun mevcut değeri
aynen kalır (bkz. §5.1, §6.1). Örn. kullanıcı yalnızca `primaryColor: "#1565C0"`
(Safir teması) gönderirse, kurumun önceki `secondaryColor` değeri (örn. `#E65100`)
değişmeden kalır; Safir temasının `#00796B` karşılığı yazılmaz. Ön tanımlı bir temanın
her iki rengini birlikte uygulamak isteyen istemci her iki alanı da açıkça istek
gövdesinde göndermelidir.

## 7. `GET /api/v1/organizations/{organizationId}/brand` — Marka ayarlarını görüntüleme

### 7.1. Amaç

Kurumun marka ayarlarını (ana renk, logo bilgisi, renk paleti) tek seferde döndürür.

### 7.2. İstek

Başlık: `Authorization: Bearer <platform-access-token>`

Yol: `organizationId` — hedef kurum UUID.

### 7.3. Başarılı cevap

`200 OK`

```json
{
  "primaryColor": "#2E7D32",
  "secondaryColor": "#E65100",
  "rowVersion": 7,
  "logo": {
    "assetId": "4a9cc266-53f0-4dd6-a388-9b13fcaaf3db",
    "downloadUrl": "/api/v1/organizations/4a9cc266-53f0-4dd6-a388-9b13fcaaf3db/logo",
    "originalFilename": "logo.png",
    "mimeType": "image/png",
    "sizeBytes": 245760
  },
  "colors": [
    { "colorHex": "#FFC107", "sortOrder": 1 },
    { "colorHex": "#FF5722", "sortOrder": 2 }
  ]
}
```

Davranış:

- `logo` alanı logo yoksa `null` döner.
- `primaryColor` ve `secondaryColor` ayrı bağlayıcı alanlardır; eksikse varsayılan
  Zeytin tema değerleri döner (§6.6.1).
- `rowVersion` güncel `organizations.rowVersion` değeridir; `PATCH /brand` cevap
  şekliyle aynı alan adını kullanır (§6.1).
- `colors` alanı `organization_brand_colors` yardımcı paletidir; boş dizi olabilir ve
  `secondaryColor`'u belirlemez.
- Platform admin hedef kurumlu erişimde audit zorunlu; başarısız → `500 INTERNAL_ERROR`.
- Org aktörü yalnızca kendi `ACTIVE` kurumunu görebilir; başka kuruma `403 FORBIDDEN`.

### 7.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `500 INTERNAL_ERROR`, `429 RATE_LIMITED`.

## 8. `PATCH /api/v1/organizations/{organizationId}/brand` — Marka ayarını güncelleme

### 8.1. Amaç

Kurum ana rengini (`primaryColor`) ve yardımcı rengini (`secondaryColor`) değiştirir.
Her ikisi ayrı, bağımsız ve bağlayıcı alanlardır. Logo bu uçtan değiştirilmez; logo
için `PUT /logo` ve `DELETE /logo` kullanılır.

### 8.2. Erişim matrisi

| Durum | Platform admin | Org aktörü |
|---|---|---|
| `ACTIVE` | İzinli | İzinli (`ORG_ADMIN` / `BRAND_MANAGE`) |
| `SUSPENDED` | İzinli | `403 FORBIDDEN` |
| `ARCHIVED` | `409 STATE_CONFLICT` | `403 FORBIDDEN` |

### 8.3. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`
- `If-Match-Row-Version: <rowVersion>`

Gövde:

```json
{
  "primaryColor": "#1565C0",
  "secondaryColor": "#00796B"
}
```

Alan kuralları:

- En az `primaryColor` veya `secondaryColor`'dan biri gönderilmelidir; ikisi birden
  de gönderilebilir. İkisi de yoksa `422 VALIDATION_FAILED`.
- Her alan `^#[0-9A-Fa-f]{6}$` regex'ine uymalı; `#000000` ve `#FFFFFF` reddedilir.
- WCAG AA kontrast doğrulaması her alan için ayrı yapılır (bkz. §2.4.2).
- Gönderilmeyen alan değişmez.
- Boş gövde `422`.
- `logo` bu uçtan değiştirilemez.

Fingerprint: `SHA-256(PATCH + /api/v1/organizations/{orgId}/brand + ORG_UPDATE_BRAND + <orgId> + SHA-256(body) + <rowVersion>)`

Gövde, kısmi güncelleme için canonical sıraya (alan adlarına göre alfabetik:
`primaryColor`, `secondaryColor`) getirilir; eksik alan yokluğuyla sabitlenir ve
fingerprint'e dâhil edilir. Sunucu yalnızca istekte bulunan alanları `UPDATE` eder;
gönderilmeyen alan dokunulmaz. `organizations.rowVersion` mutasyonla `+1` artar.
Audit kaydı yalnız fiilen değişen alan(lar)ı + `rowVersion`'ı taşır; gönderilmeyen
ya da değeri değişmeyen alan `oldValue`/`newValue`'da hiç yer almaz (§2.8.2a).

### 8.4. Başarılı cevap

`200 OK` — güncel marka nesnesi (bkz. §6.1), güncel `rowVersion` ile döner.

Davranış:

- Sunucu `organizations` satırını `SELECT ... FOR UPDATE` ile kilitler; istekte
  bulunan alanları `UPDATE primary_color` ve/veya `UPDATE secondary_color` ile
  yazar; gönderilmeyen alan korunur.
- `organizations.rowVersion` `+1` artırılır ve cevap gövdesinde/ETag'te güncel
  sürüm döner.
- Audit kaydı yalnız fiilen değişen alan(lar)ı + `rowVersion`'ı içerir;
  gönderilmeyen/değişmeyen alan `oldValue`/`newValue`'da yer almaz (§2.8.2a).
- Tüm işlem tek transaction'da; audit `INSERT` başarısız olursa mutasyonlar
  rollback yapılır, `500 INTERNAL_ERROR` döner (§5.4 adım 6).

### 8.5. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`,
  `409 IDEMPOTENCY_KEY_REUSED`, `422 VALIDATION_FAILED`, `500 INTERNAL_ERROR`,
  `429 RATE_LIMITED`.

`422` alan hataları:

- `body.REQUIRED` — `primaryColor` ve `secondaryColor`'un ikisi de yok.
- `primaryColor.INVALID_HEX` — geçerli `#RRGGBB` değil.
- `primaryColor.BLACK_OR_WHITE` — `#000000` veya `#FFFFFF` kabul edilmez.
- `primaryColor.CONTRAST_NOT_PASSED` — `onContrast < 4,5:1` (bkz. §2.4.2).
- `primaryColor.GRAPHICAL_CONTRAST_NOT_PASSED` — `primary` ↔ `neutral-0` < 3:1.
- `secondaryColor.INVALID_HEX` — geçerli `#RRGGBB` değil.
- `secondaryColor.BLACK_OR_WHITE` — `#000000` veya `#FFFFFF` kabul edilmez.
- `secondaryColor.CONTRAST_NOT_PASSED` — `onContrast < 4,5:1`.
- `secondaryColor.GRAPHICAL_CONTRAST_NOT_PASSED` — `secondary` ↔ `neutral-0` < 3:1.

## 9. `GET /api/v1/organizations/{organizationId}/brand-colors` — Renk paletini görüntüleme

### 9.1. Amaç

Kurumun yardımcı renk paletini döndürür.

### 9.2. İstek

Başlık: `Authorization: Bearer <platform-access-token>`

### 9.3. Başarılı cevap

`200 OK`

```json
{
  "rowVersion": 7,
  "items": [
    { "colorHex": "#FFC107", "sortOrder": 1 },
    { "colorHex": "#FF5722", "sortOrder": 2 }
  ]
}
```

- Liste boş olabilir (`items: []`).
- `sortOrder` artan sırada döner.
- `rowVersion` güncel `organizations.rowVersion` değeridir; `PUT /brand-colors` cevap
  şekliyle aynı alan adını kullanır (§6.3). İstemci sonraki yazma için bu değeri
  `If-Match-Row-Version` başlığında geri gönderir.

### 9.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `429 RATE_LIMITED`.

## 10. `PUT /api/v1/organizations/{organizationId}/brand-colors` — Renk paletini güncelleme

### 10.1. Amaç

Kurumun yardımcı renk paletini tamamen değiştirir. Gönderilen liste mevcut paletin
yerini alır. Boş liste göndermek tüm yardımcı renkleri kaldırır.

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
  "items": [
    { "colorHex": "#FFC107", "sortOrder": 0 },
    { "colorHex": "#FF5722", "sortOrder": 1 },
    { "colorHex": "#4CAF50", "sortOrder": 2 }
  ]
}
```

Alan kuralları:

- `items` zorunlu; boş dizi geçerli (tüm renkler kaldırılır).
- En fazla 20 renk; aşım `422 VALIDATION_FAILED`.
- Her `colorHex` geçerli `#RRGGBB` olmalı.
- Aynı `colorHex` listede tekrar edemez.
- `sortOrder` 0–999 arası tamsayı; tekrar edebilir (eşitlikte `colorHex` alfabetik).

Fingerprint: `SHA-256(PUT + /api/v1/organizations/{orgId}/brand-colors + ORG_UPDATE_BRAND_COLORS + <orgId> + SHA-256(body) + <rowVersion>)`

### 10.4. Başarılı cevap

`200 OK` — güncel renk paleti listesi.

Davranış:

- Mevcut bütün `organization_brand_colors` satırları silinir; yeni liste `INSERT`
  edilir.
- Tüm işlem tek transaction'da yürütülür.
- Audit kaydı eski ve yeni paleti içerir.

### 10.5. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`,
  `409 IDEMPOTENCY_KEY_REUSED`, `422 VALIDATION_FAILED`, `500 INTERNAL_ERROR`,
  `429 RATE_LIMITED`.

## 11. `GET /api/v1/organizations/{organizationId}/modules` — Modül listesini görüntüleme

### 11.1. Amaç

Kurumun etkin modül listesini ve sıralamasını döndürür.

### 11.2. İstek

Başlık: `Authorization: Bearer <platform-access-token>`

### 11.3. Başarılı cevap

`200 OK`

```json
{
  "rowVersion": 7,
  "items": [
    { "moduleCode": "ATT", "isEnabled": true, "sortOrder": 0 },
    { "moduleCode": "PROGRAM", "isEnabled": true, "sortOrder": 1 },
    { "moduleCode": "PROGRESS", "isEnabled": true, "sortOrder": 2 },
    { "moduleCode": "CONTENT", "isEnabled": false, "sortOrder": 3 },
    { "moduleCode": "EXPORT", "isEnabled": false, "sortOrder": 4 },
    { "moduleCode": "AUDIT", "isEnabled": true, "sortOrder": 5 }
  ]
}
```

- Liste sabit uzunluktadır; modül eklenip çıkarılamaz.
- `sortOrder` artan sırada döner.
- `isEnabled = false` olan modüller menüde gösterilmez.
- `rowVersion` güncel `organizations.rowVersion` değeridir; `PATCH /modules` cevap
  şekliyle aynı alan adını kullanır (§6.5). İstemci sonraki yazma için bu değeri
  `If-Match-Row-Version` başlığında geri gönderir.

### 11.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `429 RATE_LIMITED`.

## 12. `PATCH /api/v1/organizations/{organizationId}/modules` — Modül ayarlarını güncelleme

### 12.1. Amaç

Bir veya daha fazla modülün `isEnabled` ve/veya `sortOrder` değerini kısmi günceller.

### 12.2. Erişim matrisi

| Durum | Platform admin | Org aktörü |
|---|---|---|
| `ACTIVE` | İzinli | İzinli (`ORG_ADMIN` / `MODULE_MANAGE`) |
| `SUSPENDED` | İzinli | `403 FORBIDDEN` |
| `ARCHIVED` | `409 STATE_CONFLICT` | `403 FORBIDDEN` |

### 12.3. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`
- `If-Match-Row-Version: <rowVersion>`

Gövde:

```json
{
  "items": [
    { "moduleCode": "CONTENT", "isEnabled": false, "sortOrder": 3 },
    { "moduleCode": "EXPORT", "isEnabled": true, "sortOrder": 4 }
  ]
}
```

Alan kuralları:

- `items` zorunlu; en az 1, en fazla 6 öge.
- Her ögede `moduleCode` zorunlu; geçerli modül kodlarından biri olmalı.
- `CORE`, `IAM`, `ORG` modül kodları gönderilemez; gönderilirse `422 VALIDATION_FAILED`.
- `isEnabled` isteğe bağlı; gönderilmezse değişmez.
- `sortOrder` isteğe bağlı; gönderilmezse değişmez. 0–999 arası.
- Gövdede `isEnabled` ve `sortOrder` dışında alan bulunamaz.
- `CORE`, `IAM`, `ORG` için `isEnabled = false` gönderilemez → `422 VALIDATION_FAILED`.
- Boş gövde `422`.
- Aynı `moduleCode` listede tekrar edemez.

Fingerprint: `SHA-256(PATCH + /api/v1/organizations/{orgId}/modules + ORG_UPDATE_MODULES + <orgId> + SHA-256(body) + <rowVersion>)`

### 12.4. Başarılı cevap

`200 OK` — güncel modül listesi.

Davranış:

- Gövdede bulunmayan modüller değişmez.
- `isEnabled` veya `sortOrder` alanı gönderilmeyen modülün mevcut değeri korunur.
- Tüm işlem tek transaction'da yürütülür.
- Herhangi bir modülün `isEnabled` veya `sortOrder`'ı fiilen değiştiyse audit
  kaydı `enabledModules` alanının **tam eski/yeni anlık görüntüsünü** (tüm
  modüller, yalnız değişenler değil) `{moduleCode, isEnabled, sortOrder}`
  biçiminde içerir (§2.8.2). İstek geçerli olup hiçbir modülün değeri fiilen
  değişmediyse (mevcut değerle aynı `isEnabled`/`sortOrder` gönderilmişse)
  `enabledModules` "değişen alan" sayılmaz; audit `INSERT` edilmez, `200 OK`
  no-op olarak döner (§2.8.2a "yalnız değişen alan" kuralının doğal sonucu).
- `organization_modules.updated_at` ve `updated_by_user_id` güncellenir.

### 12.5. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`,
  `409 IDEMPOTENCY_KEY_REUSED`, `422 VALIDATION_FAILED`, `500 INTERNAL_ERROR`,
  `429 RATE_LIMITED`.

`422` alan hataları:

- `moduleCode.INVALID` — geçerli modül kodu değil.
- `moduleCode.REQUIRED` — moduleCode zorunlu.
- `moduleCode.IMMUTABLE` — CORE, IAM veya ORG değiştirilemez.
- `moduleCode.DUPLICATE` — aynı modül kodu tekrar ediyor.
- `isEnabled.REQUIRED_FOR_CORE` — CORE/IAM/ORG kapatılamaz.
- `sortOrder.OUT_OF_RANGE` — 0–999 dışında.

## 13. `PUT /api/v1/organizations/{organizationId}/logo` — Logo yükleme/değiştirme

### 13.1. Amaç

Kurum logosunu yükler veya mevcut logoyu yenisiyle değiştirir.

> **Uygulama notu (iki dönembağlayıcı):**
>
> - **Dönem 1 — `A-007R`/`OPS-005` provisioning ve `ORG-010` tamamlanana kadar:**
>   yetkilendirme geçilse dahi uç `501 NOT_IMPLEMENTED` döner; nesne deposu yazma ve
>   `file_assets` kaydı oluşmaz. API şekli yine de bağlayıcıdır.
> - **Dönem 2 — provisioning ve `ORG-010` sonrasında:** yetkili kurum aktörü
>   (`ORG_ADMIN` veya `BRAND_MANAGE` izni) destek bayrağı olmadan `200 OK` ile logo
>   bilgisini döner; platform yöneticisi `ORG-001 §4.1a` policy'siyle doğrulandıktan
>   sonra `app.iam_platform_admin_support_access = true` bayrağı kurulmuş şekilde
>   `200 OK` döner. Yetkisiz, başka kurum, revoked üyelik/rol/izin, sahte bayrak ve
>   yanlış `operationCode` fail-closed `403 FORBIDDEN` reddedilir (§2.2.2).

### 13.2. Erişim matrisi

| Durum | Platform admin | Org aktörü |
|---|---|---|
| `ACTIVE` | İzinli | İzinli (`ORG_ADMIN` / `BRAND_MANAGE`) |
| `SUSPENDED` | İzinli | `403 FORBIDDEN` |
| `ARCHIVED` | `409 STATE_CONFLICT` | `403 FORBIDDEN` |

### 13.3. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`
- `Content-Type: multipart/form-data`

Form alanları:

| Alan | Tür | Açıklama |
|---|---|---|
| `file` | binary | Logo dosyası. PNG veya JPEG, en fazla 5 MB. |
| `rowVersion` | tamsayı | Güncel `organizations.rowVersion`. Logo yükleme `If-Match-Row-Version` başlığı yerine bu form alanını kullanır. |

Fingerprint: `SHA-256(PUT + /api/v1/organizations/{orgId}/logo + ORG_UPLOAD_LOGO + <orgId> + SHA-256(logo-binary) + <rowVersion>)`

### 13.4. Başarılı cevap

`200 OK` — logo yanıtı (bkz. §6.2).

Davranış:

- Dosya doğrulaması: MIME türü `image/png` veya `image/jpeg`; aksi `422`.
- Dosya boyutu ≤ 5 MB; aksi `422`.
- Yeni `file_assets` kaydı oluşturulur; `organizations.logo_asset_id` güncellenir.
- Mevcut logo varsa `logo_asset_id` değişir; eski `file_assets` kaydı korunur.
- Idempotent yeniden gönderim: aynı key + aynı binary SHA-256 → ilk başarılı sonuç.

### 13.5. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`,
  `409 IDEMPOTENCY_KEY_REUSED`, `422 VALIDATION_FAILED`, `500 INTERNAL_ERROR`,
  `501 NOT_IMPLEMENTED`, `429 RATE_LIMITED`.

`422` alan hataları:

- `file.REQUIRED` — dosya alanı zorunlu.
- `file.INVALID_MIME` — yalnız `image/png`, `image/jpeg` kabul edilir.
- `file.SIZE_EXCEEDED` — 5 MB üst sınır aşıldı.
- `file.CONTENT_VALIDATION_FAILED` — dosya içerik doğrulaması başarısız (zararlı içerik,
  bozuk dosya). Kesin kriterler `ORG-010`'da belirlenecektir.
- `rowVersion.REQUIRED` — `rowVersion` form alanı zorunlu.

## 14. `DELETE /api/v1/organizations/{organizationId}/logo` — Logo silme

### 14.1. Amaç

Kurum logosunu kaldırır. `logo_asset_id` `NULL` yapılır; `file_assets` kaydı korunur.

> **Uygulama notu (iki dönembağlayıcı):**
>
> - **Dönem 1 — `A-007R`/`OPS-005` provisioning ve `ORG-010` tamamlanana kadar:**
>   yetkilendirme geçilse dahi uç `501 NOT_IMPLEMENTED` döner; `logo_asset_id`
>   değişmez. Logo olmayan kurumda dönem 2'de `404 RESOURCE_NOT_FOUND` döner.
> - **Dönem 2 — provisioning ve `ORG-010` sonrasında:** yetkili kurum aktörü
>   (`ORG_ADMIN` veya `BRAND_MANAGE` izni) destek bayrağı olmadan `204 No Content`
>   döner; platform yöneticisi `ORG-001 §4.1a` policy'siyle doğrulandıktan sonra
>   `app.iam_platform_admin_support_access = true` bayrağı kurulmuş şekilde `204 No
>   Content` döner. Fail-closed kuralları §2.2.2'de tanımlıdır.

### 14.2. Erişim matrisi

| Durum | Platform admin | Org aktörü |
|---|---|---|
| `ACTIVE` | İzinli | İzinli (`ORG_ADMIN` / `BRAND_MANAGE`) |
| `SUSPENDED` | İzinli | `403 FORBIDDEN` |
| `ARCHIVED` | `409 STATE_CONFLICT` | `403 FORBIDDEN` |

### 14.3. İstek

Başlıklar:

- `Authorization: Bearer <platform-access-token>`
- `Idempotency-Key: <clientMutationId>`
- `If-Match-Row-Version: <rowVersion>`

Gövde yok.

Fingerprint: `SHA-256(DELETE + /api/v1/organizations/{orgId}/logo + ORG_REMOVE_LOGO + <orgId> + SHA-256("") + <rowVersion>)`

### 14.4. Başarılı cevap

`204 No Content`

Davranış:

- Logo yoksa `404 RESOURCE_NOT_FOUND`.
- İşlem başarılı olduğunda `organizations.logo_asset_id` `NULL` yapılır.
- `file_assets` kaydı fiziksel silinmez; denetim bütünlüğü için korunur.
- Idempotent yeniden gönderim: logo zaten yoksa ilk başarılı sonuç (`204`) döner.

### 14.5. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `409 VERSION_CONFLICT`, `409 STATE_CONFLICT`,
  `409 IDEMPOTENCY_KEY_REUSED`, `500 INTERNAL_ERROR`, `501 NOT_IMPLEMENTED`,
  `429 RATE_LIMITED`.

## 15. `GET /api/v1/organizations/{organizationId}/logo` — Logo görüntüleme/indirme

### 15.1. Amaç

Kurum logosunu görüntülemek veya indirmek için güvenli akış sağlar.

> **Uygulama notu (iki dönembağlayıcı):**
>
> - **Dönem 1 — `A-007R`/`OPS-005` provisioning ve `ORG-010` tamamlanana kadar:**
>   yetkilendirme geçilse dahi uç `501 NOT_IMPLEMENTED` döner; nesne deposundan akış
>   başlatılmaz.
> - **Dönem 2 — provisioning ve `ORG-010` sonrasında:** yetkili kurum aktörü (aktif
>   üyelik + rol; `BRAND_MANAGE` izni zorunlu değil) destek bayrağı olmadan `200 OK`
>   ile binary akışı döner; platform yöneticisi `ORG-001 §4.1a` policy'siyle
>   doğrulandıktan sonra `app.iam_platform_admin_support_access = true` bayrağı
>   kurulmuş şekilde `200 OK` döner. Yetkisiz erişim fail-closed `403 FORBIDDEN`'dir
>   (§2.2.2).

### 15.2. İstek

Başlık: `Authorization: Bearer <platform-access-token>`

### 15.3. Başarılı cevap

`200 OK` — `Content-Type` logo dosyasının `mime_type` değerine göre (`image/png` veya
`image/jpeg`), `Content-Length` dosya boyutu kadar. Binary akış.

Davranış:

- Logo yoksa `404 RESOURCE_NOT_FOUND`.
- İstemciye S3 ön-imzalı URL veya kalıcı herkese açık URL verilmez.
- Backend yetkiyi doğruladıktan sonra nesne deposundan akışla iletir.
- `Cache-Control` başlığı mobil istemcinin makul süre önbelleklemesine izin verir
  (örn. `private, max-age=3600`). Kesin süre `ORG-010`'da belirlenir.
- Platform admin hedef kurumlu erişimde audit zorunlu.

### 15.4. Hata kuralları

- `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `403 ORGANIZATION_CONTEXT_REQUIRED`,
  `404 RESOURCE_NOT_FOUND`, `500 INTERNAL_ERROR`, `501 NOT_IMPLEMENTED`,
  `429 RATE_LIMITED`.

## 16. Kabul senaryoları

### 16.1. Marka ayarı (1–6)

1. **Ana renk güncelleme:** Org admin `PATCH /brand` → `200 OK`, `primaryColor`
   güncellenir.
2. **WCAG metin kontrast reddi (gerçek başarısız):** Gerçek başarısız örnek olarak
   `primaryColor: #E0E0E0` gönderilir; algoritma `#000000`'ı seçer ama `primary`
   ↔ `neutral-0` grafiksel kontrast 1,32:1 < 3:1 → `422 VALIDATION_FAILED` +
   `primaryColor.GRAPHICAL_CONTRAST_NOT_PASSED`. (Eski `#FFFF00` örneği yanlış bir
   negatifti; saf sarı üzerinde siyah metin 19,56:1 ile **geçer**; yalnızca grafiksel
   kontrast gerekçesi değerlendirilir.)
3. **Siyah/beyaz reddi:** `#000000` veya `#FFFFFF` → `422 VALIDATION_FAILED` +
   `BLACK_OR_WHITE`.
4. **Geçersiz hex reddi:** `#GGGGGG` veya `red` → `422 VALIDATION_FAILED` +
   `INVALID_HEX`.
5. **Yetkisiz erişim:** `BRAND_MANAGE` izni olmayan hoca → `403 FORBIDDEN`.
6. **SUSPENDED kurum reddi:** Kurum `SUSPENDED`; org aktörü `PATCH /brand` →
   `403 FORBIDDEN`.

### 16.2. Renk paleti (7–11)

7. **Palet güncelleme:** Org admin `PUT /brand-colors` ile 3 renk gönderir →
   `200 OK`, yeni liste döner.
8. **Palet temizleme:** `PUT /brand-colors` ile boş `items: []` → `200 OK`,
   palet temizlenir.
9. **Tekrarlı hex reddi:** Aynı `colorHex` iki kez gönderilir → `422 VALIDATION_FAILED`.
10. **Üst sınır aşımı:** 21 renk gönderilir → `422 VALIDATION_FAILED`.
11. **Sürüm çakışması:** Eski `rowVersion` ile `PUT /brand-colors` →
    `409 VERSION_CONFLICT`.

### 16.3. Modül yönetimi (12–18)

12. **Modül kapatma:** Org admin `PATCH /modules` ile `CONTENT` modülünü kapatır →
    `200 OK`.
13. **CORE kapatma reddi:** `CORE` modülü için `isEnabled: false` → `422 VALIDATION_FAILED`
    + `IMMUTABLE` veya `REQUIRED_FOR_CORE`.
14. **Modül sıralaması:** `PATCH /modules` ile `sortOrder` değerleri güncellenir →
    `200 OK`.
15. **Yetkisiz modül yönetimi:** `MODULE_MANAGE` izni olmayan hoca → `403 FORBIDDEN`.
16. **Geçersiz modül kodu:** `HAYALI_MODUL` → `422 VALIDATION_FAILED` + `INVALID`.
17. **ARCHIVED kurum reddi:** Platform admin `ARCHIVED` kuruma `PATCH /modules` →
    `409 STATE_CONFLICT`.
18. **Kısmi güncelleme:** Yalnızca `sortOrder` gönderilir, `isEnabled` değişmez;
    diğer modüller etkilenmez → `200 OK`.

### 16.4. Logo yönetimi (19–23)

> Senaryolar iki döneme ayrılır. Dönem 1'de `A-007R`/`OPS-005` provisioning ve
> `ORG-010` tamamlanana kadar yetkilendirme geçilse dahi `501 NOT_IMPLEMENTED`
> dönülür. Dönem 2'de (provisioning + `ORG-010` sonrası) aşağıdaki davranışlar
> geçerlidir.

19. **Dönem-1 yetkili kurum aktörü `501`:** Org admin `PUT /logo` isteği yapar;
    yetkilendirme geçer, ama provisioning eksik olduğu için `501 NOT_IMPLEMENTED`
    döner; `file_assets` kaydı oluşmaz, `logo_asset_id` değişmez. Aynı durum
    `DELETE /logo` ve `GET /logo` için de geçerlidir.
20. **Dönem-2 yetkili kurum aktörü bayraksız `200`:** Org admin (`ORG_ADMIN` veya
    `BRAND_MANAGE` izinli hoca) `PUT /logo` ile geçerli PNG yükler → `200 OK`,
    `logo_asset_id` güncellenir, `rowVersion +1`; `app.iam_platform_admin_support
    _access` kurulmaz; `platform_administrators` `SELECT` yapılmaz.
21. **Dönem-2 yetkili kurum aktörü bayraksız logo değiştirme/silme:** Mevcut logo
    varken yeni logo yüklenir → eski `file_assets` korunur, `logo_asset_id`
    güncellenir. Logo mevcutken `DELETE /logo` → `204 No Content`,
    `logo_asset_id` `NULL`; `file_assets` kaydı korunur.
22. **Dönem-2 platform admin doğrulanmış destek bayrağıyla `200`/`204`:** Platform
    yöneticisi `PUT /logo`/`DELETE /logo`'ya `ORG-001 §4.1a` RLS policy'sinden
    geçer; `app.iam_platform_admin_support_access = true` kurulur; `200 OK`/`204`
    döner, audit yazılır, fail-closed reddinde `500 INTERNAL_ERROR`. Logo yokken
    `DELETE /logo` → `404 RESOURCE_NOT_FOUND`.
23. **Dönem-2 geçersiz dosya reddi:** `image/gif` veya > 5 MB dosya →
    `422 VALIDATION_FAILED` (`file.INVALID_MIME` / `file.SIZE_EXCEEDED`).

### 16.5. İdempotency ve replay (24–27)

24. **İdempotent marka güncelleme:** Aynı key + fingerprint ile `PATCH /brand` →
    `200 OK`, ikinci audit kaydı oluşmaz.
25. **Replay VERSION_CONFLICT üretmez:** Kayıp cevap, aynı key/fingerprint ile
    yeniden gönderim → ilk başarılı sonuç.
26. **Farklı fingerprint reddi:** Aynı key, farklı `primaryColor` →
    `409 IDEMPOTENCY_KEY_REUSED`.
27. **İdempotent logo yükleme:** Aynı key + aynı binary SHA-256 → ilk başarılı sonuç;
    ikinci `file_assets` kaydı oluşmaz. Aynı key + farklı binary →
    `409 IDEMPOTENCY_KEY_REUSED`.

### 16.6. Yetki ve durum (28–32)

28. **Yetkisi geri alınmış aktör replay reddi:** Kullanıcının `BRAND_MANAGE` izni geri
    alınır. Eski bir `PATCH /brand` işleminin `Idempotency-Key`'i ile yeniden istek →
    authorization adımında `403 FORBIDDEN`; eski başarılı sonuç replay edilmez.
29. **Platform admin audit fail-closed:** `PATCH /brand`'te audit başarısız →
    transaction rollback; `500 INTERNAL_ERROR`.
30. **ARCHIVED kurumda salt okunur erişim:** Platform admin `GET /brand`, `GET /logo`
    → `200 OK`. `PATCH /brand` → `409 STATE_CONFLICT`.
31. **Logo olmayan kurumda GET /brand:** `logo: null` döner; `404` değil.
32. **ORG-001 PATCH ile primaryColor değiştirilemez:** `ORG-001` `PATCH /organizations/{id}`
    gövdesinde `primaryColor` (veya `secondaryColor`) gönderilir → `422 VALIDATION_FAILED`.

### 16.7. Aktör bazlı yetki, bayrak ve kontrast kabul senaryoları (33–40)

33. **Kurum aktörü bayraksız başarılı marka işlemi:** Aktif üyelik + `BRAND_MANAGE` iznine
    sahip org aktörü `PATCH /brand` `{"primaryColor":"#1565C0"}` → `200 OK`. Transaction
    sonunda `app.iam_platform_admin_support_access` kurulmamıştır; `platform_administrators`
    tablosunda `SELECT` yapılmamıştır. Aynı aktör `PUT /logo` ve `DELETE /logo` işlemlerinde
    de destek bayrağı olmadan başarılıdır.
34. **Platform admin doğrulama ve destek bayrağı olmadan ret:** Platform admin token'ı ile
    `PATCH /brand` isteği gelir; `platform_administrators` actor-only `SELECT` satır
    döndürmez (kayıt `revoked_at IS NOT NULL` veya yok). `app.iam_platform_admin_support
    _access = true` kurulmaz; istek fail-closed `403 FORBIDDEN`; mutasyon/audit yazılmaz.
35. **Başka kurum ve geri alınmış izinle ret:** Org A üyeliği olan aktör Org B hedef
    `PATCH /brand` → `403 FORBIDDEN` (kurum izolasyonu). Aynı aktör Org A'da `MODULE_MANAGE`
    izni geri alınmışken (`revoked_at IS NOT NULL`) `PATCH /modules` → `403 FORBIDDEN`;
    eski başarılı sonuç idempotency replay ile oynatılmaz.
36. **`primary`/`secondary` için doğru on-color seçimi:** `PATCH /brand` ile
    `{"primaryColor":"#E65100","secondaryColor":"#2E7D32"}` → `200 OK`. Algoritma
    `primary` için `onColor = #000000` (`onContrast 5,54:1`), `secondary` için
    `onColor = #FFFFFF` (`onContrast 5,13:1`) seçer. Sonraki `GET /brand` cevabı bu
    `onColor` seçimini (read-only türev alan olarak) göstermez; yalnızca ham hex değerleri
    döner, on-color istemcide `MOBIL_TASARIM_TOKENLARI.md` §3.2.2 algoritmasıyla üretilir.
37. **3:1 grafiksel kontrast reddi:** `PATCH /brand` `{"secondaryColor":"#959595"}` →
    `secondary` ↔ `neutral-0` 2,995:1 < 3:1 → `422 VALIDATION_FAILED` +
    `secondaryColor.GRAPHICAL_CONTRAST_NOT_PASSED`. (`#949494` 3,03:1 ile **geçer**; sınır
    denetimi 3:1 eşiğinde bağlayıcıdır.)
38. **Paletin `secondary` alanını örtük biçimde değiştirmediği:** `PUT /brand-colors`
    `{"items":[{"colorHex":"#FFC107","sortOrder":0}]}` → `200 OK`. Sonraki `GET /brand`
    cevabında `secondaryColor` önceki değerinde kalır; `organization_brand_colors` ilk
    elemanı `secondary`'ye yansıtılmaz.
39. **Siyah/beyaz sınır ve gerçek kontrast hesapları (makineyle teyit):** Hesaplanan sınır
    değerleri makineyle teyit edilmiştir: `#777777` `onContrast 4,69:1` ✓ geçer;
    `#949494` vs `neutral-0` `3,03:1` ✓ geçer; `#959595` vs `neutral-0` `2,995:1` ✗
    reddedilir; `#E0E0E0` `onContrast 15,91:1` ✓ ama vs `neutral-0` `1,32:1` ✗ →
    `GRAPHICAL_CONTRAST_NOT_PASSED`; `#FFFF00` üzerinde `onColor = #000000` ile 19,56:1
    ✓ geçer (eski yanlış negatif kaldırıldı), yalnızca vs `neutral-0` `1,07:1` reddi
    gecerlidir; `#80CBC4` vs `neutral-0` `1,86:1` ✗ → reddedilir.
40. **Eksik renklerde onaylı varsayılan tema:** Yeni kurum oluşturulduğunda
    `primary_color` ve `secondary_color` boştur; ilk `GET /brand` cevabı Zeytin teması
    değerlerini (`primaryColor: #2E7D32`, `secondaryColor: #E65100`) döner; her iki değer
    de §2.4.4 hesaplanan tablosuna göre WCAG AA doğrular.
41. **Tek renk gönderiminde örtük tema tamamlaması yapılmadığı:** Kurumun
    `primaryColor: #2E7D32` / `secondaryColor: #E65100` (Zeytin) olduğu durumda
    `PATCH /brand` `{"primaryColor":"#1565C0"}` (Safir'in ana rengi) gönderilir →
    `200 OK`, `primaryColor: #1565C0`. Sonraki `GET /brand` cevabında
    `secondaryColor` **`#E65100` olarak kalır**; Safir temasının `secondaryColor`
    karşılığı (`#00796B`) yazılmaz (§6.6.1).
42. **`ORG_ADMIN` ayrı izin satırı olmadan başarılı yazma:** Aktörün
    `organization_membership_roles`'de aktif (`revoked_at IS NULL`) bir
    `ORG_ADMIN` rolü vardır; `organization_membership_permissions` tablosunda bu
    aktör için `BRAND_MANAGE`/`MODULE_MANAGE` satırı **hiç yoktur** (devredilmiş
    izin verilmemiş). `PATCH /brand` (veya `PUT /brand-colors`/`PATCH /modules`/
    `PUT /logo`/`DELETE /logo`) → `200 OK`; transaction `organization_membership
    _permissions`/`permission_catalog` tablolarına hiç `SELECT` atmaz (§4.1).
    `ORG_ADMIN` rolü tek başına yeterlidir; izin tablosunda satır bulunmaması
    `403 FORBIDDEN` üretmez.

## 17. Kaynaklarla uyum kontrolü

- `URUN_VE_UYGULAMA_PLANI.md` §9.1: kurum adı, logo, ana renk ve yardımcı renkler →
  uyumlu. Logo yükleme ve renk paleti ayrı uçlarla karşılanır.
- `URUN_VE_UYGULAMA_PLANI.md` §9.2: menü görünürlüğü ve modül sırası → uyumlu.
  `organization_modules` API'si modül durumu ve sıralamasını yönetir.
- `YETKI_MATRISI.md` KURUM-01: marka ayarları devredilebilir → uyumlu. `BRAND_MANAGE`
  ve `MODULE_MANAGE` izinleri `VERI_MODELI.md` §4.8 ORG_SETTINGS kategorisinde mevcut
  seed sabitleridir.
- `VERI_MODELI.md` §5.1–5.3, §10.1: tablo yapıları → uyumlu. `organizations.secondary_color`
  bu sözleşmeyle §5.1'e eklenmiştir.
- `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §4.1a: `platform_administrators`
  ORGANIZATION scope `SELECT` RLS policy'si → uyumlu. Policy bu sözleşmenin dokuz
  operation code'unu (`ORG_VIEW_BRAND`, `ORG_UPDATE_BRAND`, `ORG_VIEW_BRAND_COLORS`,
  `ORG_UPDATE_BRAND_COLORS`, `ORG_VIEW_MODULES`, `ORG_UPDATE_MODULES`,
  `ORG_UPLOAD_LOGO`, `ORG_REMOVE_LOGO`, `ORG_VIEW_LOGO`) kapsayacak şekilde
  genişletilmiş; ORG-002 ayrı bir policy tanımlamaz (tek kanonik karar).
- `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md`: platform-admin RLS policy
  referansları ORG-002 operation code'larını içerecek şekilde güncellenmiştir; `iam_runtime`
  ORG transaction'larını işletmez (ADR-003).
- `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §4.1c: scope, idempotency ve replay
  modeli → uyumlu. `primaryColor`/`secondaryColor` ORG-002 kapsamına alınmış,
  ORG-001 PATCH'ten çıkarılmıştır. `ORG-003` bu sözleşmenin 9 operation code'unu
  tek kanonik allow-list olarak uygular.
- `MOBIL_TASARIM_TOKENLARI.md` §3.2 (kurum paleti), §3.2.2 (on-color algoritması),
  §3.2.3, §3.2.4 (WCAG sınırları): uyumlu. Sunucu `primary`/`secondary` ham hex
  değerleri için bağlayıcı on-color seçimi (§2.4.1), `4,5:1` metin ve `3:1` grafiksel
  kontrast doğrulaması yapar; yardımcı palet için bu doğrulamayı yapmaz. Sunucu ve mobil
  aynı WCAG göreli luminans formülünü kullanır.
- `ADR/ADR-007-pdf-dosya-depolama.md`: logo özel erişim ve güvenli indirme → uyumlu.
  Logo kalıcı herkese açık URL ile sunulmaz; yetki doğrulanarak akışla iletilir.
- `API_GENEL_KURALLARI.md`: sayfalama, hata zarfı, idempotency, PATCH/PUT → uyumlu.

## 18. Kapsam dışı kararlar

- Kurum adı ve kısa ad güncelleme → `ORG-001` `PATCH /organizations/{id}`.
- Logo dosyası gerçek yükleme ve nesne deposu entegrasyonu → `ORG-010`, `OPS-005`.
- Mobil logo seçme, önizleme ve yükleme → `ORG-008` (dosyasız marka ekranı),
  `ORG-011` (dosyalı logo akışı).
- Logo dosyasının fiziksel silinmesi → `VERI_MODELI.md` §14 arşivleme yaklaşımı.
- Logo zararlı içerik taraması ve bozuk dosya doğrulamasının kesin kriterleri → `ORG-010`.
- Logo `Cache-Control` süresi → `ORG-010`.
- `BRAND_MANAGE`/`MODULE_MANAGE` izin ataması ve geri alınması uygulaması → `STAFF-*`;
  izin değerlendirme algoritması → `PERM-002`. (İzin kodları zaten `VERI_MODELI.md`
  §4.8 seed sabitidir.)
- Kuruma özel font → yeni ürün kararı gerektirir; V1 kapsamı dışıdır.
- Koyu tema (dark mode) → sonraki faz.
- `NOTIFY` modülünün modül listesine eklenmesi → Dalga 8.
- `CORE`, `IAM`, `ORG`, `TERM`, `CLS`, `PEOPLE` modüllerinin menü görünürlüğü → altyapı
  modülleridir; yönetim arayüzünde gösterilmez.
- Kurum varsayılan teması atama → `ORG-003`/`ORG-004` kapsamında değerlendirilecektir.
- Material `container`/`surface` çiftlerinin WCAG doğrulaması (`primaryContainer` ↔
  `onPrimaryContainer` vb.) → UI-003 programatik kontrast testleri; bu sözleşme ve
  sunucu doğrulaması kapsamı dışıdır.
