# Mobil Tasarım Tokenları

| Alan | Değer |
|---|---|
| Görev | UI-001 — Mobil tasarım tokenlarını tanımla |
| Belge sürümü | 1.0 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` §9.1, §9.2, §19 |
| Bilgi mimarisi kaynakları | `YONETICI_BILGI_MIMARISI.md` (P-005), `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) |
| Ekran envanteri kaynağı | `EKRAN_ENVANTERI.md` (P-007) |
| Framework kaynağı | `ADR/ADR-001-mobil-framework.md` |
| Son güncelleme | 17 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, mobil uygulamadaki bütün ekran ve bileşenlerin ortak görsel yapı taşlarını — renk,
tipografi, boşluk, yuvarlaklık, yükselti/gölge, ikon boyutu ve hareket süreleri — tek bir
**tasarım tokenı sözleşmesi** olarak tanımlar. Tokenlar, `EKRAN_ENVANTERI.md`deki 59 ekranın
tamamında tutarlı ve erişilebilir bir görsel deneyim üretmek için kullanılacaktır.

Bu belge:

- Kurum kişiselleştirmesine açık ve kapalı tokenları net biçimde ayırır.
- Renk erişilebilirliği (WCAG AA kontrast) ve okunabilirlik kurallarını bağlayıcı olarak tanımlar.
- Uygulanabildiği yerde Flutter/Material karşılığını referans verir; ancak bu belge kod değil sözleşmedir.
- Somut Flutter `ThemeData` uygulamasını veya bileşen kodunu içermez (`UI-003` kapsamı).

Bu belge şunları tanımlamaz:

- Ekranların yerleşimi, alan listesi veya akışı (`EKRAN_ENVANTERI.md`, `P-007` kapsamı).
- Navigasyon kabuğu, sekme sayısı veya menü gruplaması (`UI-002` kapsamı).
- Somut widget/bileşen API'si veya uygulaması (`UI-003` kapsamı).
- Logo yükleme/görüntüleme teknik detayı (`ORG-010`, `ORG-011` kapsamı).

---

## 2. Tasarım ilkeleri

Aşağıdaki ilkeler ana plan §3.1 (sezgisel kullanım), §9.1 (görsel kimlik), §16 (performans) ve
ADR-001'den (Flutter) türetilmiştir:

1. **Önce okunabilirlik:** Kurum renkleri dâhil hiçbir yapılandırma, metin okunabilirliğini ve
   WCAG AA kontrast oranını (normal metin ≥ 4,5:1, büyük metin ≥ 3:1) bozamaz.
2. **Kurum kişiselleştirmesi kontrollüdür:** Yalnızca açıkça işaretlenmiş tokenlar kurum
   yöneticisi tarafından değiştirilebilir. Çekirdek tokenlar sabittir.
3. **Tutarlı ritim:** Bütün boşluk ve boyutlandırma 4 dp'lik temel ızgara birimine dayanır.
4. **Dokunma hedefi:** Etkileşimli öğeler en az 48×48 dp dokunma alanına sahip olmalıdır
   (platform erişilebilirlik standardı).
5. **Hareket amaçlıdır:** Animasyonlar kullanıcıya geri bildirim vermek veya geçişi
   yumuşatmak içindir; 200 ms üzerindeki animasyonlar yalnızca anlamlı bir geçiş için
   kullanılır.
6. **Platform duyarlılığı:** iOS ve Android'de aynı token değerleri kullanılır; platforma özgü
   his (kaydırma fiziği, navigasyon geçişi) Flutter'ın platform adaptif davranışına bırakılır.

---

## 3. Renk sistemi

Renk sistemi üç katmana ayrılır: **çekirdek palet** (sabit), **kurum paleti** (kurum
yöneticisi tarafından değiştirilebilir) ve **işlevsel palet** (sabit, anlamsal).

### 3.1. Çekirdek renkler (sabit — kurumdan bağımsız)

Bütün kurumlarda aynı kalan renklerdir. Nötr arka plan, yüzey, kenarlık, gölge ve metin
hâkimiyetini sağlar.

| Token | Hex değeri | Flutter karşılığı | Kullanım |
|---|---|---|---|
| `neutral-0` | `#FFFFFF` | `Colors.white` | Sayfa arka planı, kart yüzeyi |
| `neutral-50` | `#F8F9FA` | — | Hafif alternatif arka plan |
| `neutral-100` | `#E9ECEF` | — | Grup arka planı, bölme çizgileri |
| `neutral-200` | `#DEE2E6` | — | Kenarlık, devre dışı durum arka planı |
| `neutral-300` | `#CED4DA` | — | Devre dışı kenarlık |
| `neutral-400` | `#ADB5BD` | — | Devre dışı metin, yer tutucu ikon |
| `neutral-500` | `#6C757D` | — | İkincil metin, alt başlık |
| `neutral-600` | `#495057` | — | Gövde metni (açık zemin) |
| `neutral-700` | `#343A40` | — | Vurgulu gövde metni |
| `neutral-800` | `#212529` | — | Başlık metni |
| `neutral-900` | `#121416` | — | Yüksek vurgulu başlık |
| `neutral-950` | `#0A0B0D` | — | En koyu metin, simge |

### 3.2. Kurum paleti (kurum yöneticisi tarafından değiştirilebilir)

Kurum yöneticisi §9.1 gereği ana renk ve yardımcı rengi değiştirebilir. Aşağıdaki ön
tanımlı temalar dağıtımla birlikte gelir; kurum yöneticisi hazır bir temayı seçebilir veya
özel hex değeri girebilir.

#### 3.2.1. Ön tanımlı temalar

| Tema | Ana renk (`primary`) | Yardımcı renk (`secondary`) | Ton adı |
|---|---|---|---|
| Varsayılan (Zeytin) | `#2E7D32` | `#E65100` | Koyu yeşil + koyu turuncu |
| Safir | `#1565C0` | `#00796B` | Mavi + koyu teal |
| Nar | `#C62828` | `#455A64` | Koyu kırmızı + gri-mavi |
| Lavanta | `#6A1B9A` | `#006064` | Mor + koyu petrol |
| Toprak | `#6D4C41` | `#2E7D32` | Kahverengi + yeşil |

Varsayılan tema, kullanıcı tanımlı bir kurum teması atanana kadar bütün yeni kurumlarda
kullanılır. Her temada ana renk ve yardımcı renk, erişilebilirlik kontrast kontrollerinden ön
onaylıdır.

#### 3.2.2. Deterministik `ColorScheme` üretim kuralı

Kurumun `primary` ve `secondary` hex değerleri `ColorScheme`'e **aynen** yansıtılır.
Tonal palet `primary`'den seed olarak üretilir; `secondary` seed'e katılmaz — bu,
Material 3 tasarımının `secondary` tonunun `primary` seed'inden otomatik türetilmesi
kuralıdır. Kurumun özgün `secondary` değeri `ColorScheme.secondary` alanına override ile
yazılır.

**Algoritma:**

1. `ColorScheme.fromSeed(seedColor: primary, dynamicSchemeVariant: DynamicSchemeVariant.content)` ile
   tonal palet (primary, secondary, tertiary, neutral, neutralVariant, error paletleri)
   üretilir.
2. `fromSeed` sonucundaki `primary` ve `secondary` alanları, kurumun girdiği orijinal hex
   değerleriyle override edilir. Bu override `onPrimary`/`onSecondary`'yi **otomatik
   yeniden hesaplamaz** (`ColorScheme.copyWith` bağımsız alan değişimi yapar — Flutter'ın
   resmî dokümantasyonu bunu doğrular).
3. `onPrimary` ve `onSecondary` değerleri aşağıdaki bağlayıcı algoritma ile seçilir:

   ```
   L = relative_luminance(hex)
   whiteContrast = 1.05 / (L + 0.05)
   blackContrast = (L + 0.05) / 0.05
   onColor = whiteContrast >= blackContrast ? #FFFFFF : #000000
   ```

   Algoritma, iki aday arasından **yüksek kontrastlı olanı** seçer. Eşitlik durumunda
   (`whiteContrast == blackContrast`) `#FFFFFF` (beyaz) seçilir. Siyah ve beyaz
   adaylardan yüksek kontrastlı olan seçildiğinde, geçerli sRGB renkleri için elde edilen
   minimum maksimum kontrast yaklaşık **4,58:1**'dir (eşitlik noktası luminansı ≈ 0,179).
   Bu nedenle geçerli sRGB girdilerinde seçilen `on-color` normal metin için 4,5:1
   eşiğini karşılar. `< 4,5:1` kontrolü bozuk/standart dışı girdi veya uygulama
   hatasına karşı **savunmacı doğrulama** olarak korunur; normal geçerli sRGB akışında
   erişilmesi beklenmez. Sunucu ve Flutter istemcisi **aynı formülü** uygular.

   `onPrimaryContainer` ve `onSecondaryContainer` tonal paletten otomatik üretildiği
   gibi bırakılır; bunların kontrastı `fromSeed` algoritması tarafından garanti edilir.
4. Container tonları (`primaryContainer`, `secondaryContainer` vb.) ve yüzey katmanları
   (`surface`, `surfaceContainerLowest`–`surfaceContainerHighest`, `onSurface` vb.) 1.
   adımdaki tonal paletten olduğu gibi alınır; ek override uygulanmaz.

**Not — `secondaryContainer` bağımsız değildir:** `secondaryContainer`, `fromSeed`'in
yalnızca `primary` seed'ine dayalı olarak ürettiği bir tondur; kurumun girdiği bağımsız
`secondary` rengiyle doğrudan ilişkili değildir. Bu, Material 3'ün "tüm palet tek bir
seed'ten türer" tasarımıdır. Kurum `secondary`'si yalnızca `ColorScheme.secondary` ve
türevi olan `onSecondary`'yi etkiler.

**Sunucu/istemci sorumluluğu:** Kurum renkleri yalnızca sunucuda saklanır. Kanonik tema
çıktısı Flutter istemcisinde yukarıdaki algoritma ile belirlenimci olarak üretilir.
Sunucu, renk kaydı sırasında **yalnızca** `primary` ve `secondary` ham hex değerlerinin
yukarıdaki `on-*` seçim algoritmasına göre kontrastını doğrular; §3.2.3'teki eşik
kontrolü buna göre yapılır. Türetilen Material container/surface çiftlerinin kontrastı
sunucu tarafından doğrulanmaz; bu çiftlerin WCAG uygunluğu UI-003 tarafından eklenecek
**programatik kontrast testleriyle** doğrulanır. Snapshot/golden testleri yalnızca
Flutter SDK veya `material_color_utilities` sürümü değiştiğinde palet çıktısındaki
değişimi yakalar; tek başına WCAG uygunluğunu kanıtlamaz (§15.2 kontrast test kabul
kapıları). Aynı `(primary, secondary)` girdisi, aynı Flutter SDK ve aynı
`material_color_utilities` sürümünde her zaman aynı `ColorScheme`'i üretir.

**Üretilen tonlar ve override özeti:**

| Ton | Kaynak | Override |
|---|---|---|
| `primary` | **Kurum girdisi** — doğrudan yazılır | Evet (girdi aynen) |
| `onPrimary` | §3.2.2 adım 3 algoritması (manuel seçim) | Evet (algoritma) |
| `primaryContainer` | Tonal palet (seed = primary) | Yok |
| `onPrimaryContainer` | Tonal palet | Yok |
| `secondary` | **Kurum girdisi** — doğrudan yazılır | Evet (girdi aynen) |
| `onSecondary` | §3.2.2 adım 3 algoritması (manuel seçim) | Evet (algoritma) |
| `secondaryContainer` | Tonal palet (seed = primary) | Yok |
| `onSecondaryContainer` | Tonal palet | Yok |
| `tertiary` | Tonal palet (seed = primary) | Yok |
| `surface`–`surfaceContainerHighest` | Tonal palet (seed = primary) | Yok |

#### 3.2.3. Her ön plan/arka plan çiftinin kontrast kontrolü

Aşağıdaki çiftlerin her biri için §3.2.4'teki WCAG AA eşikleri bağımsız olarak kontrol
edilir; bir çiftin geçmesi diğerini geçerli kılmaz:

1. **`primary` ↔ `onPrimary`:** §3.2.2 adım 3 algoritması ile seçilen `onPrimary` metin/
   simge renginin `primary` dolgusu üzerindeki kontrastı (normal metin ≥ 4,5:1).
2. **`primary` ↔ `neutral-0` (sayfa zemini):** `primary` dolgulu buton, FAB veya AppBar'ın
   sayfanın `neutral-0` arka planına karşı grafiksel kontrastı (≥ 3:1).
3. **`secondary` ↔ `onSecondary`:** `secondary` üzerinde `onSecondary` metin/simge
   (≥ 4,5:1).
4. **`secondary` ↔ `neutral-0`:** `secondary` dolgulu veya çerçeveli bileşenlerin sayfa
   arka planına karşı grafiksel kontrastı (≥ 3:1).
5. **`primaryContainer` ↔ `onPrimaryContainer`** ve **`secondaryContainer` ↔
   `onSecondaryContainer`:** Bu çiftler tonal paletten otomatik üretilir; kontrast
   garantisi `fromSeed` algoritmasına aittir. WCAG uygunluğu UI-003 programatik
   kontrast testleriyle doğrulanır; snapshot/golden testleri tek başına yeterli
    değildir (§15.2).

6–10 arası çiftler §3.3'teki (kurumdan bağımsız) işlevsel renklere aittir:

6. **`success` ↔ `onSuccess`** ve **`successContainer` ↔ `onSuccessContainer`**
7. **`warning` ↔ `onWarning`** ve **`warningContainer` ↔ `onWarningContainer`**
8. **`error` ↔ `onError`** ve **`errorContainer` ↔ `onErrorContainer`**
9. **`info` ↔ `onInfo`** ve **`infoContainer` ↔ `onInfoContainer`**
10. **Bütün `surface*` ↔ `onSurface`** çiftleri (tonal paletten otomatik karşılanır).

Siyah/beyaz dinamik on-color algoritması (§3.2.2 adım 3) **yalnızca** kurum tarafından
girilen `primary` ve `secondary` renklerine uygulanır. §3.3'teki `success`, `warning`,
`error`, `info` ve bunların container/on-container çiftleri **sabit tokenlardır**; her
birinin on-color değeri (§3.3 tablosunda) WCAG formülüyle bağımsız olarak önceden
doğrulanmıştır. Container on-color değerleri siyah veya beyaz olmak zorunda değildir
(örneğin `on-warning-container = #BF360C` koyu turuncudur).

Her çift için normal metin ≥ 4,5:1, büyük metin (≥ 24 px veya ≥ 18 px bold) ≥ 3:1 eşiği
aranır. `primary`/`secondary` ↔ arka plan grafiksel kontrastı için ≥ 3:1 yeterlidir.

#### 3.2.4. Erişilebilirlik sınırları (bağlayıcı)

Kurum yöneticisi özel renk girdiğinde sistem §3.2.3'teki bütün kontrast çiftlerini
otomatik doğrular:

1. Kontrast hesaplaması WCAG 2.1 göreli luminans formülü ile yapılır. Bütün kontrast
   değerleri 1:1–21:1 aralığındadır.
2. `primary` veya `secondary` için §3.2.2 adım 3 algoritması sonucu seçilen `on-*`
   renginin kontrastı 4,5:1'in altındaysa veya grafiksel kontrast ≥ 3:1 sağlanamazsa:
   sistem rengi reddeder ve hatayı kullanıcıya açıklar. Otomatik güvenli ton önerisi
   sunar (ana plan §9.1).
3. Kontrast denetimi sunucu tarafında, renk veritabanına kaydedilirken yapılır. Sunucu
   yalnızca ham `primary`/`secondary` hex değerlerini ve yukarıdaki adım 3
   algoritmasıyla seçilen `onPrimary`/`onSecondary`'yi doğrular; türetilmiş Material
   container/surface çiftlerini doğrulamaz. İstemci sunucudan gelen ve kontrastı zaten
   doğrulanmış rengi uygular.

**Ön tanımlı temaların doğrulanmış kontrastları:**

| Tema | `primary` | `onPrimary` | Kontrast | `secondary` | `onSecondary` | Kontrast |
|---|---|---|---|---|---|---|
| Varsayılan (Zeytin) | `#2E7D32` | `#FFFFFF` | **5,13:1** ✓ | `#E65100` | `#000000` | **5,54:1** ✓ |
| Safir | `#1565C0` | `#FFFFFF` | **5,75:1** ✓ | `#00796B` | `#FFFFFF` | **5,32:1** ✓ |
| Nar | `#C62828` | `#FFFFFF` | **5,62:1** ✓ | `#455A64` | `#FFFFFF` | **7,24:1** ✓ |
| Lavanta | `#6A1B9A` | `#FFFFFF` | **9,39:1** ✓ | `#006064` | `#FFFFFF` | **7,35:1** ✓ |
| Toprak | `#6D4C41` | `#FFFFFF` | **7,61:1** ✓ | `#2E7D32` | `#FFFFFF` | **5,13:1** ✓ |

`onPrimary`/`onSecondary` sütunları §3.2.2 adım 3 algoritmasının sonucudur. Örneğin
Zeytin temasında `secondary = #E65100` için `whiteContrast ≈ 3,79:1`, `blackContrast ≈
5,54:1`; algoritma yüksek kontrastlı aday olarak `#000000` (siyah) seçer.

### 3.3. İşlevsel/anlamsal renkler (sabit — kurumdan bağımsız)

Durum, hata, başarı ve uyarı iletileri için kullanılır. Kurum kişiselleştirmesine kapalıdır.

Flutter `ColorScheme` içinde yalnızca `error`/`onError`/`errorContainer`/
`onErrorContainer` alanları tanımlıdır; `success`, `warning` ve `info` alanları yoktur.
Bu nedenle:

- `error` ve `onError` çifti → `ColorScheme.error` / `ColorScheme.onError`
- `errorContainer` ve `onErrorContainer` → `ColorScheme.errorContainer` / `ColorScheme.onErrorContainer`
- `success`, `warning`, `info` ve bunların container/on-container çiftleri →
  `ThemeExtension<AppSemanticColors>` ile modellenir (UI-003 kapsamı).

| Token | Hex değeri | Model | Kullanım |
|---|---|---|---|
| `success` | `#2E7D32` | `ThemeExtension` | Başarılı işlem, tamamlandı, geldi |
| `on-success` | `#FFFFFF` | `ThemeExtension` | Başarı zemini üzerindeki metin |
| `success-container` | `#C8E6C9` | `ThemeExtension` | Başarı arka plan kartı |
| `on-success-container` | `#1B5E20` | `ThemeExtension` | Başarı kartı üzerindeki metin |
| `warning` | `#BF360C` | `ThemeExtension` | Uyarı, bekleyen işlem, geç geldi |
| `on-warning` | `#FFFFFF` | `ThemeExtension` | Uyarı zemini üzerindeki metin |
| `warning-container` | `#FFF3E0` | `ThemeExtension` | Uyarı arka plan kartı |
| `on-warning-container` | `#BF360C` | `ThemeExtension` | Uyarı kartı üzerindeki metin |
| `error` | `#D32F2F` | `ColorScheme.error` | Hata, gelmedi, başarısız işlem |
| `on-error` | `#FFFFFF` | `ColorScheme.onError` | Hata zemini üzerindeki metin |
| `error-container` | `#FFCDD2` | `ColorScheme.errorContainer` | Hata arka plan kartı |
| `on-error-container` | `#B71C1C` | `ColorScheme.onErrorContainer` | Hata kartı üzerindeki metin |
| `info` | `#1565C0` | `ThemeExtension` | Bilgi, izinli, nötr durum iletisi |
| `on-info` | `#FFFFFF` | `ThemeExtension` | Bilgi zemini üzerindeki metin |
| `info-container` | `#BBDEFB` | `ThemeExtension` | Bilgi arka plan kartı |
| `on-info-container` | `#0D47A1` | `ThemeExtension` | Bilgi kartı üzerindeki metin |

`ThemeExtension<AppSemanticColors>` sınıfı UI-003 görevinde tanımlanacaktır. Bu sınıf
yukarıdaki `success*`, `warning*` ve `info*` alanlarını taşır; `error*` alanlarını
taşımaz (onlar doğrudan `ColorScheme`'tendir).

**Hesaplanmış kontrastlar:**

| Çift | Zemin | Metin | Normal metin (≥ 4,5:1) |
|---|---|---|---|
| `success` ↔ `on-success` | `#2E7D32` | `#FFFFFF` | **5,13:1** ✓ |
| `success-container` ↔ `on-success-container` | `#C8E6C9` | `#1B5E20` | **5,85:1** ✓ |
| `warning` ↔ `on-warning` | `#BF360C` | `#FFFFFF` | **5,60:1** ✓ |
| `warning-container` ↔ `on-warning-container` | `#FFF3E0` | `#BF360C` | **5,11:1** ✓ |
| `error` ↔ `on-error` | `#D32F2F` | `#FFFFFF` | **4,98:1** ✓ |
| `error-container` ↔ `on-error-container` | `#FFCDD2` | `#B71C1C` | **4,67:1** ✓ |
| `info` ↔ `on-info` | `#1565C0` | `#FFFFFF` | **5,75:1** ✓ |
| `info-container` ↔ `on-info-container` | `#BBDEFB` | `#0D47A1` | **6,15:1** ✓ |

**Durum tek başına renkle anlatılamaz:** Her durum göstergesi, rengin yanı sıra en az bir
ek kanalla (ikon, metin etiketi veya şekil farkı) ifade edilir. Bu kural bütün işlevsel
renklerin kullanıldığı yerlerde — yoklama listesi, eşitleme durum göstergesi, snackbar,
form hatası — zorunludur. Örneğin yalnızca yeşil/kırmızı nokta ile yetinilmez; "Geldi"/
"Gelmedi" metni veya ✓/✗ ikonu daima eşlik eder. Bu, WCAG 2.1 1.4.1 (Rengin Kullanımı)
başarı ölçütünün doğrudan uygulamasıdır.

### 3.4. Yoklama durum renkleri (sabit — kurumdan bağımsız)

Bütün kurumlarda zorunlu olan `Geldi` ve `Gelmedi` ile kuruma özel ek durumların varsayılan
renk eşlemesi:

| Yoklama durumu | Token | Renk | Açıklama |
|---|---|---|---|
| Geldi | `att-present` | `#2E7D32` (yeşil) | Başarı rengine eş |
| Gelmedi | `att-absent` | `#D32F2F` (kırmızı) | Hata rengine eş |
| Geç geldi | `att-late` | `#BF360C` (koyu turuncu) | Uyarı rengine eş |
| İzinli | `att-excused` | `#1565C0` (mavi) | Bilgi rengine eş |
| Hasta | `att-sick` | `#6A1B9A` (mor) | Nötr, bilgi renginden ayrışsın |
| Kuruma özel (varsayılan) | `att-custom` | `#6C757D` (gri) | Tanımsız ek durumlar için yedek |

Kuruma özel ek yoklama durumlarının renkleri, kurum yöneticisi tarafından yoklama durumu
tanımlama ekranında (MGT-11) değiştirilebilir. Bu değişiklik için de §3.2.3'teki aynı
kontrast kontrolleri uygulanır.

### 3.5. Eşitleme durum renkleri (sabit — kurumdan bağımsız)

| Durum | Token | Renk | Kullanım |
|---|---|---|---|
| Bekliyor | `sync-pending` | `neutral-500` / `#6C757D` | Kuyrukta bekleyen işlem |
| Eşitleniyor | `sync-syncing` | `info` / `#1565C0` | Aktif gönderim |
| Başarılı | `sync-success` | `success` / `#2E7D32` | Sunucu onaylı |
| Başarısız | `sync-failed` | `error` / `#D32F2F` | Yeniden denenebilir hata |

---

## 4. Tipografi sistemi

### 4.1. Yazı tipi ailesi

Platform varsayılanı kullanılır. Flutter, Material 3 ile Roboto (Android) ve San Francisco
(iOS) yazı tiplerini otomatik eşler. İlk sürümde özel bir font dosyası eklenmez.

| Platform | Yazı tipi |
|---|---|
| Android | Roboto |
| iOS | San Francisco (SF Pro) |

### 4.2. Boyut skalası

| Token | Boyut (dp) | Flutter karşılığı | Kullanım |
|---|---|---|---|
| `text-xs` | 10 | `headlineSmall`-türevi değil, özel | Yan etiket, tarih damgası, sayaç |
| `text-sm` | 12 | — | Yardımcı metin, alt başlık, sekme |
| `text-md` | 14 | `bodyMedium` | Liste öğesi gövdesi, form alanı değeri, buton metni |
| `text-lg` | 16 | `bodyLarge` | Ana gövde metni, kart başlığı, diyalog metni |
| `text-xl` | 18 | `titleMedium` | Bölüm başlığı, kart başlığı |
| `text-2xl` | 20 | `titleLarge` | Sayfa başlığı, ana başlık |
| `text-3xl` | 24 | `headlineSmall` | Ekran başlığı, oturum başlığı |
| `text-4xl` | 28 | `headlineMedium` | Giriş ekranı başlığı |
| `text-5xl` | 32 | — | Karşılama başlığı |
| `text-6xl` | 40 | `headlineLarge` | Uygulama adı, büyük boş durum başlığı |

### 4.3. Ağırlık skalası

| Token | Ağırlık | Flutter karşılığı | Kullanım |
|---|---|---|---|
| `font-normal` | 400 (normal) | `FontWeight.w400` | Gövde metni, liste öğesi, yardımcı metin |
| `font-medium` | 500 (medium) | `FontWeight.w500` | Buton metni, sekme, kart alt başlığı |
| `font-semibold` | 600 (semi-bold) | `FontWeight.w600` | Bölüm başlığı, diyalog başlığı |
| `font-bold` | 700 (bold) | `FontWeight.w700` | Sayfa başlığı, ana başlık, boş durum başlığı |

### 4.4. Satır yüksekliği

| Token | Değer (çarpan) | Kullanım |
|---|---|---|
| `leading-tight` | 1,25 | Başlıklar, kısa metinler |
| `leading-normal` | 1,5 | Gövde metni, liste öğeleri |
| `leading-relaxed` | 1,75 | Uzun açıklama metinleri, yardım metinleri |

### 4.5. Tipografi tokenlarının rollere eşlenmesi

Aşağıdaki eşleme, tipografik rollerin hangi token kombinasyonlarıyla karşılandığını gösterir:

| Rol | Boyut | Ağırlık | Satır yüksekliği |
|---|---|---|---|
| Ekran başlığı (AppBar) | `text-3xl` (24) | `font-bold` (700) | `leading-tight` (1,25) |
| Sayfa başlığı | `text-2xl` (20) | `font-bold` (700) | `leading-tight` (1,25) |
| Bölüm başlığı | `text-xl` (18) | `font-semibold` (600) | `leading-tight` (1,25) |
| Kart başlığı | `text-lg` (16) | `font-semibold` (600) | `leading-normal` (1,5) |
| Liste öğesi başlığı | `text-md` (14) | `font-medium` (500) | `leading-normal` (1,5) |
| Liste öğesi alt metni | `text-sm` (12) | `font-normal` (400) | `leading-normal` (1,5) |
| Gövde metni | `text-lg` (16) | `font-normal` (400) | `leading-normal` (1,5) |
| Buton metni | `text-md` (14) | `font-medium` (500) | `leading-tight` (1,25) |
| Form alanı etiketi | `text-sm` (12) | `font-normal` (400) | `leading-tight` (1,25) |
| Form alanı değeri | `text-md` (14) | `font-normal` (400) | `leading-normal` (1,5) |
| Yardımcı/açıklama metni | `text-sm` (12) | `font-normal` (400) | `leading-relaxed` (1,75) |
| Küçük etiket | `text-xs` (10) | `font-medium` (500) | `leading-tight` (1,25) |
| Boş durum başlığı | `text-4xl` (28) | `font-bold` (700) | `leading-tight` (1,25) |
| Boş durum açıklaması | `text-lg` (16) | `font-normal` (400) | `leading-relaxed` (1,75) |

---

## 5. Boşluk sistemi

4 dp'lik temel ızgara birimine dayanır. Bütün padding, margin ve boşluk değerleri bu skala
ile adlandırılır.

| Token | Değer (dp) | Kullanım |
|---|---|---|
| `space-0` | 0 | Sıfır boşluk, liste sonu |
| `space-1` | 4 | Simge-metin arası, sıkı boşluk |
| `space-2` | 8 | Liste öğesi iç boşluğu, chip arası, buton-metin arası |
| `space-3` | 12 | Kart iç boşluğu, form alanları arası |
| `space-4` | 16 | Standart kenar boşluğu, sayfa padding'i, liste öğeleri arası |
| `space-5` | 20 | Bölümler arası, diyalog iç boşluğu |
| `space-6` | 24 | Büyük bölüm ayracı, kart grupları arası |
| `space-8` | 32 | Sayfa-altı boşluk, üst düzey bölüm ayracı |
| `space-10` | 40 | Boş durum bileşenleri arası |
| `space-12` | 48 | Ekran-altı güvenli alan, FAB alt boşluğu |

**Kenar boşluğu standardı:** Bütün ekranların sağ ve sol kenarından `space-4` (16 dp)
standart padding alır. Bu kural, `EKRAN_ENVANTERI.md`deki bütün liste ve form ekranlarında
geçerlidir. Tam genişlikteki bileşenler (tam genişlik kart, AppBar) istisnadır.

---

## 6. Yuvarlaklık (border radius)

| Token | Değer (dp) | Kullanım |
|---|---|---|
| `radius-none` | 0 | Tam genişlik kart, tablo hücresi |
| `radius-sm` | 4 | Girdi alanı, küçük buton, chip |
| `radius-md` | 8 | Kart, liste öğesi grubu, diyalog |
| `radius-lg` | 12 | Büyük kart, bottom sheet, resim |
| `radius-xl` | 16 | Modal bottom sheet üst köşeleri |
| `radius-pill` | 999 (tam yuvarlak) | FAB, badge, durum etiketi, buton |

---

## 7. Yükselti ve gölge (elevation)

Flutter'ın Material `elevation` sistemini kullanır. Bütün değerler dp cinsindendir ve
gölge + yüzey tonu değişimini birlikte ifade eder.

| Token | Değer (dp) | Kullanım |
|---|---|---|
| `elevation-0` | 0 | Düz liste öğesi, düz kart, tam genişlik bölme |
| `elevation-1` | 1 | Standart kart, liste öğesi kartı |
| `elevation-2` | 2 | Yükseltilmiş kart, FAB (durağan) |
| `elevation-3` | 3 | AppBar (kaydırmalı), üst seviye kart |
| `elevation-4` | 4 | Alt navigasyon çubuğu |
| `elevation-6` | 6 | FAB (basılı), snackbar |
| `elevation-8` | 8 | Diyalog, açılır menü, bottom sheet (açık) |
| `elevation-16` | 16 | Modal bottom sheet (arka plan karartması üstünde) |

---

## 8. İkon boyutları

| Token | Boyut (dp) | Kullanım |
|---|---|---|
| `icon-xs` | 12 | Durum noktası, küçük gösterge |
| `icon-sm` | 16 | Liste öğesi yan ikonu, yardımcı ikon |
| `icon-md` | 20 | Standart ikon, buton içi, sekme ikonu |
| `icon-lg` | 24 | AppBar ikonu, ana eylem ikonu, FAB ikonu |
| `icon-xl` | 32 | Boş durum ikonu |
| `icon-2xl` | 48 | Büyük boş durum ikonu, karşılama ikonu |

---

## 9. Hareket süreleri

| Token | Süre (ms) | Kullanım |
|---|---|---|
| `duration-instant` | 100 | Buton basma geri bildirimi, switch, checkbox |
| `duration-fast` | 150 | Sayfa geçişi, sekme değişimi, FAB görünme/kaybolma |
| `duration-normal` | 300 | Diyalog açılma/kapanma, bottom sheet, genişleme animasyonu |
| `duration-slow` | 500 | Boş durum animasyonu, uzun liste geçişi |
| `duration-snackbar` | 4000 | Snackbar görünme süresi (otomatik kapanma) |

Hareket eğrisi (easing) olarak Flutter'ın Material varsayılanları kullanılır:
`Curves.easeInOut` giriş-çıkış, `Curves.easeOut` giriş, `Curves.fastOutSlowIn` standart
geçişler için.

---

## 10. Bileşen düzeyinde tokenlar

Aşağıdaki tokenlar, ortak bileşenlerin (`UI-003` kapsamı) tutarlı biçimde üretilmesi için
bağlayıcıdır.

### 10.1. Butonlar

| Token | Değer |
|---|---|
| `button-height-sm` | 32 dp (görsel yükseklik; küçük buton) |
| `button-height-md` | 40 dp (görsel yükseklik; standart metin butonu) |
| `button-height-lg` | 48 dp (görsel yükseklik; birincil eylem butonu, FAB) |
| `button-min-hit` | 48×48 dp (bütün butonlar için hit-test ve semantics alanı; görsel yükseklikten bağımsız) |
| `button-radius` | `radius-pill` (999 dp) |
| `button-padding-h` | `space-4` (16 dp yatay iç boşluk) |
| `button-gap` | `space-2` (8 dp — buton ile içerik/metin arası) |

Görsel yükseklik (`button-height-*`) butonun boyanmış sınırlarını belirler. Hit-test ve
semantics alanı (`button-min-hit`) bütün buton çeşitlerinde en az 48×48 dp'dir; bu alan
görsel sınırdan bağımsız olarak, gerekirse `visualDensity` veya padding ile genişletilir.
İkon butonlar bu kuralın istisnası değildir; yalnızca ikon içeren bir butonun da hit alanı
en az 48×48 dp olmalıdır.

Buton çeşitleri ve öncelik sırası:

| Çeşit | Dolgu | Kenarlık | Kullanım |
|---|---|---|---|
| Birincil (filled) | `primary` | Yok | Ana ekrandaki birincil eylem, form gönder |
| İkincil (tonal) | `primary-container` | Yok | Kart içi eylem, alternatif eylem |
| Düz (text) | Saydam | Yok | Liste öğesi eylemi, "İptal" |
| Çerçeveli (outlined) | Saydam | `primary` (1,5 dp) | Orta önemli eylem, formda ikincil buton |
| Tehlike (danger) | `error` | Yok | Silme, arşivleme, çıkış gibi tehlikeli işlem |
| Devre dışı | `neutral-200` | Yok/`neutral-300` | Yetkisiz veya form geçersizken |

### 10.2. Girdi alanları

| Token | Değer |
|---|---|
| `input-height` | 48 dp (standart metin alanı) |
| `input-height-multiline` | En az 80 dp (çok satırlı alan) |
| `input-radius` | `radius-sm` (4 dp — üst köşeler, Material 3 outlined) |
| `input-border-color-normal` | `#7B8591` (normal durum kenarlığı) |
| `input-border-color-focus` | `primary` (odak durumu kenarlığı) |
| `input-border-color-error` | `error` / `#D32F2F` (hata durumu kenarlığı) |
| `input-border-color-disabled` | `neutral-200` / `#DEE2E6` (devre dışı kenarlık) |
| `input-border-width` | 1 dp (normal) / 2 dp (odak ve hata) |
| `input-padding-h` | `space-3` (12 dp) |
| `input-label-size` | `text-sm` (12 dp) |
| `input-value-size` | `text-md` (14 dp) |
| `input-error-size` | `text-xs` (10 dp) |
| `input-helper-size` | `text-xs` (10 dp) |

Girdi alanı kenarlığı, WCAG 2.1 1.4.11 (Grafiksel Olmayan Kontrast) başarı ölçütü
kapsamında bir **görsel kontrol göstergesidir**. Beyaz sayfa zemini (`#FFFFFF`) karşısında
en az 3:1 kontrast sağlamalıdır.

**Input kenarlık kontrastları (beyaz zemin `#FFFFFF` karşısında):**

| Kenarlık durumu | Renk | Kontrast (≥ 3:1) |
|---|---|---|
| Normal | `#7B8591` | **3,75:1** ✓ |
| Odak (`primary`, Zeytin varsayılan) | `#2E7D32` | **5,13:1** ✓ |
| Hata | `#D32F2F` | **4,98:1** ✓ |
| Devre dışı | `#DEE2E6` | **1,30:1** — devre dışı bileşenler için kontrast zorunlu değildir (WCAG 1.4.11 istisnası) |

### 10.3. Kartlar

| Token | Değer |
|---|---|
| `card-radius` | `radius-md` (8 dp) |
| `card-elevation` | `elevation-1` (1 dp) |
| `card-padding` | `space-4` (16 dp) |
| `card-gap` | `space-3` (12 dp — kart içi bölümler arası) |

### 10.4. Liste öğeleri

| Token | Değer |
|---|---|
| `list-item-height` | En az 48 dp (tek satır) |
| `list-item-height-double` | En az 64 dp (iki satır) |
| `list-item-padding-h` | `space-4` (16 dp) |
| `list-item-padding-v` | `space-2` (8 dp) |
| `list-item-gap` | `space-3` (12 dp — ikon/bilgi ile metin arası) |
| `list-divider-height` | 1 dp |
| `list-divider-color` | `neutral-100` |
| `list-divider-indent` | `space-4` (16 dp — soldan başlangıç) |

### 10.5. AppBar / Üst çubuk

| Token | Değer |
|---|---|
| `appbar-height` | 56 dp (standart) |
| `appbar-elevation` | `elevation-0` (düz) / `elevation-2` (kaydırmalı, içerik varken) |
| `appbar-title-size` | `text-xl` (18 dp) |
| `appbar-icon-size` | `icon-lg` (24 dp) |

### 10.6. Sekme çubuğu / Alt navigasyon

| Token | Değer |
|---|---|
| `navbar-height` | 64 dp (alt navigasyon çubuğu) |
| `navbar-icon-size` | `icon-md` (20 dp — seçili değil) / `icon-lg` (24 dp — seçili) |
| `navbar-label-size` | `text-xs` (10 dp) |
| `navbar-elevation` | `elevation-4` (4 dp) |

### 10.7. Diyalog ve bottom sheet

| Token | Değer |
|---|---|
| `dialog-radius` | `radius-lg` (12 dp) |
| `dialog-elevation` | `elevation-8` (8 dp) |
| `dialog-padding` | `space-5` (20 dp) |
| `dialog-max-width` | 320 dp (telefon diyalog maksimum genişliği) |
| `sheet-radius-top` | `radius-xl` (16 dp — üst köşeler) |

### 10.8. Durum göstergeleri (chip, badge, etiket)

| Token | Değer |
|---|---|
| `chip-height` | 32 dp (görsel yükseklik) |
| `chip-min-hit` | 48×48 dp (hit-test ve semantics alanı; görsel yükseklikten bağımsız) |
| `chip-radius` | `radius-pill` (999 dp) |
| `chip-padding-h` | `space-3` (12 dp) |
| `chip-gap` | `space-1` (4 dp — ikon-metin arası) |
| `badge-size` | 16 dp (küçük) / 20 dp (büyük) |
| `badge-radius` | `radius-pill` (999 dp) |
| `badge-font-size` | 10 dp |

### 10.9. Eşitleme durum göstergesi

| Token | Değer |
|---|---|
| `sync-indicator-size` | 20 dp (simge boyutu) |
| `sync-indicator-margin` | `space-2` (8 dp — AppBar sağı) |

### 10.10. Boş durum bileşeni

| Token | Değer |
|---|---|
| `empty-icon-size` | `icon-2xl` (48 dp) |
| `empty-title-size` | `text-4xl` (28 dp) |
| `empty-description-size` | `text-lg` (16 dp) |
| `empty-spacing` | `space-10` (40 dp — ikon, başlık, açıklama ve eylem arası) |
| `empty-padding` | `space-8` (32 dp — ekran kenarından) |

---

## 11. Kurum teması kişiselleştirme sözleşmesi

### 11.1. Değiştirilebilir tokenlar

Kurum yöneticisi marka ayarları ekranından (MGT-09) yalnızca aşağıdaki tokenları
değiştirebilir:

| Token grubu | Değiştirme yöntemi |
|---|---|
| Ana renk (`primary`) | Hazır tema seçimi veya özel hex girme |
| Yardımcı renk (`secondary`) | Hazır tema seçimi veya özel hex girme |
| Kurum adı | Serbest metin |
| Logo | Dosya yükleme (Dalga 5, `ORG-011` kapsamı) |

Bu dört değer dışında **hiçbir renk, tipografi, boşluk veya bileşen tokenı** kurum
yöneticisi tarafından değiştirilemez. Bu sınır, §9.1'deki "Kontrollü kişiselleştirme"
ilkesinin doğrudan sonucudur.

### 11.2. Platform yöneticisi istisnası

Platform yöneticisi, destek modunda (§3.2, PLAT-06) kurum temasını MGT-09 üzerinden
değiştirebilir. Platform yöneticisi de §11.1'deki aynı sınırlara tabidir; çekirdek tokenları
değiştiremez. Her değişiklik denetim kaydı üretir.

### 11.3. Tema uygulama sırası

Mobil uygulama tema çözümleme sırası:

1. Kullanıcı giriş yaptığında, aktif kurumun `primary` ve `secondary` hex değerleri
   sunucudan alınır.
2. Flutter'da `ColorScheme.fromSeed(seedColor: primary, dynamicSchemeVariant: DynamicSchemeVariant.content)`
   ile tonal palet üretilir.
3. `primary` ve `secondary` kurum girdileriyle override edilir. `onPrimary`/`onSecondary`
    §3.2.2 adım 3'teki maksimum kontrast algoritmasıyla belirlenir ve `ColorScheme`'e yazılır.
4. `ColorScheme.error*` alanlarına §3.3'teki `error`/`onError`/`errorContainer`/
   `onErrorContainer` sabit değerleri yazılır.
5. `success*`, `warning*`, `info*` değerleri `AppSemanticColors` adlı bir
   `ThemeExtension` alt sınıfına yazılır ve `ThemeData.extensions` üzerinden
   `copyWith(extensions: [...])` ile temaya eklenir.
6. Çekirdek sabit tokenlarla (nötr palet, tipografi, boşluk) birleştirilerek `ThemeData`
   üretilir.
7. Kurum değiştiğinde veya kurum ayarları güncellendiğinde tema 1–6 arası adımlarla
   yeniden oluşturulur.

Çekirdek tokenlar (§3.1, §3.3–§3.5, §4–§10) hiçbir koşulda sunucu veya kurum ayarından
etkilenmez; mobil uygulama içinde sabit olarak derlenir.

---

## 12. Güvenli alan ve cihaz uyumu

| Token | Değer |
|---|---|
| `safe-area-bottom-min` | En az `space-12` (48 dp — FAB/buton için) veya sistem güvenli alanı (hangisi büyükse) |
| `safe-area-top` | Sistem durum çubuğu + AppBar (dinamik) |
| `keyboard-avoid-min-padding` | `space-4` (16 dp — klavye ile odaklı alan arası) |

Güvenli alan hesaplaması Flutter'ın `MediaQuery.padding` veya `SafeArea` widget'ına
bırakılır; bu belge yalnızca asgari tasarım niyetini belirtir.

---

## 13. Yönelim uyumu

Uygulama portrait ve landscape yönelimlerinde temel akışları (yoklama, öğrenci listesi,
program görüntüleme, form doldurma) çalıştırabilir. Bileşenler her iki yönelimde de
işlevsel olmalı; yatay yönelimde ekran genişliğinden bağımsız olarak kaydırma ve temel
etkileşim korunmalıdır.

Tablet cihazlar için optimize edilmiş özel çok sütunlu layout, bölmeli görünüm (split view)
ve büyük ekran gezinme kalıpları ilk sürüm kapsamında değildir. Telefon/tablet ayrımı
`MediaQueryData.size.shortestSide < 600 dp` ile tanımlanır; bu eşiğin altındaki cihazlarda
(telefon boyutu) her iki yönelim de desteklenir.

| Token | Değer |
|---|---|
| `content-max-width` | Sınırsız (viewport genişliği) |
| `orientation` | Portrait ve landscape (telefon boyutu); tablet optimize layout kapsam dışı |

---

## 14. Ana ürün planıyla uyum kontrolü

- Renk kişiselleştirme sınırı (yalnızca ana renk ve yardımcı renk) §9.1 "Kontrollü
  kişiselleştirme" ilkesiyle uyumludur; keyfi tam tema değişikliğine izin verilmez.
- WCAG AA kontrast kontrolleri (§3.2.3) §9.1 "Sistem, okunmaz renk kombinasyonlarını
  reddedebilmeli" ilkesini bağlayıcı biçimde uygular.
- 48 dp minimum dokunma hedefi §3.1 sezgisel kullanım ilkesini destekler.
- Eşitleme durum göstergesi renkleri (§3.5), §13 "Kullanıcı bekleyen, başarılı ve başarısız
  işlemleri ayırt edebilmelidir" gereksinimiyle uyumludur.
- Yoklama durum renkleri (§3.4), §8.5 yoklama modülünün zorunlu temel durumları (Geldi,
  Gelmedi) ve kuruma özel ek durumlarıyla uyumludur.
- Hareket süreleri (§9), §16 "Yoklama dokunuşu ekranda yaklaşık 100 ms içinde tepki vermelidir"
  performans hedefiyle uyumludur.
- Tipografi skalası (§4) ve boşluk sistemi (§5), §16 performans hedeflerini (öğrenci listeleri
  sayfalama/sanallaştırma) destekleyecek biçimde basit ve öngörülebilir tutulmuştur.
- Token isimlendirmesi `TERIMLER_SOZLUGU.md` ile tutarlıdır; "kurum", "sınıf", "yoklama",
  "program" terimleri tek anlamla kullanılmıştır.
- Belgede ana plana veya Dalga 0 belgelerine aykırı bir tanım bulunmamaktadır.

---

## 15. UI-003 ile ilişki

`UI-003 — Ortak düğme, alan, liste ve durum bileşenleri` görevi, bu belgedeki §10 (bileşen
düzeyinde tokenlar) ve §3–§9 (temel tokenlar) bölümlerini girdi olarak alacaktır. UI-003:

- Her tokenın somut Flutter `ThemeData` / `ThemeExtension` kurulumunu yapar.
- §10'daki her bileşen için yeniden kullanılabilir widget üretir.
- Kurum teması değiştiğinde otomatik güncellenen `Theme` sağlayıcısını kurar.
- §12'deki güvenli alan kurallarını ve §13'teki yönelim uyumunu kök widget'lara uygular.

UI-001 ve UI-003 birlikte, Dalga 2 çıkış kapısındaki "mobil navigasyon, tema ve hata
yönetimi" altyapısının tema bacağını oluşturur.

### 15.1. UI-003 metin ölçekleme kabul kapıları (bağlayıcı)

UI-003'ün kabul ölçütlerine aşağıdaki testler eklenmelidir:

1. **%100 sistem metin ölçeği:** Bütün bileşenler varsayılan metin ölçeğinde doğru
   boyutlandırılır ve hizalanır.
2. **Doğrusal metin ölçeği stres testleri:** Widget testinde
   `TextScaler.linear(1.0)`, `TextScaler.linear(1.5)` ve `TextScaler.linear(2.0)` ile:
   - Metin kesilmesi (clipping) veya taşması (overflow) olmamalıdır.
   - Sabit yüksekliğe sahip bileşenler (buton, chip, liste öğesi) metin büyüdüğünde
     `IntrinsicHeight` veya eşdeğer bir mekanizma ile dikey olarak genişlemelidir.
   - Satır taşması (wrapping) desteklenmeli ve okunabilirliği korumalıdır.
3. **Doğrusal olmayan sistem ölçeklendirme testi:**
   - Farklı font boyutlarına farklı ölçek oranı uygulayan özel bir test `TextScaler`
     uygulamasıyla (örneğin küçük boyutlarda daha agresif, büyük boyutlarda daha muhafazakâr
     ölçekleme yapan bir `TextScaler`) widget testi çalıştırılmalıdır.
   - `TextScaler.linear()` değerleri platformun (iOS/Android) doğrusal olmayan ölçekleme
     eğrisini temsil etmez; deterministik widget testinde doğrudan platform eğrisini
     taklit ettiği iddia edilmemelidir.
    - Android ve iOS platform ölçeklemesinin doğrulanması gerçek cihaz/simülatör
      entegrasyon testinde veya erişilebilirlik testinde yapılır.
    - Sistem `TextScaler` davranışını doğrulayan widget testi gerekiyorsa
      `tester.platformDispatcher.textScaleFactorTestValue` kullanılabilir,
      `MediaQueryData.fromView(tester.view)` üzerinden sistem oluşturulur,
      test sonunda `clearTextScaleFactorTestValue()` çağrılır. Bu test, gerçek
      iOS/Android ölçekleme eğrilerinin tam taklidi değildir.
4. **Dokunma ve semantics alanlarının korunması:** Metin ölçeğinden bağımsız olarak bütün
   etkileşimli bileşenler en az 48×48 dp hit-test alanını korumalıdır. Metin büyüdüğünde
   hit alanı büyümeyebilir ancak asla 48×48 dp'nin altına düşmemelidir.
5. **Test ortamı:** Doğrusal testler `testWidgets` ile `MediaQueryData.textScaler`
   değeri `TextScaler.linear(1.0)`, `TextScaler.linear(1.5)` ve `TextScaler.linear(2.0)`
   olarak değiştirilerek çalıştırılır. Doğrusal olmayan test özel `TextScaler` uygulaması
   ile aynı ortamda çalıştırılır. Görsel düzen doğrulaması (pixel-perfect) beklenmez;
    kesilme/taşma olmaması ve hit alanının korunması yeterlidir.

### 15.2. UI-003 programatik kontrast kabul kapıları (bağlayıcı)

UI-003'ün kabul ölçütlerine aşağıdaki kontrast testleri eklenmelidir:

1. **Container/surface çiftleri:** Aşağıdaki `ColorScheme` çiftlerinin her biri için
   WCAG göreli luminans formülü ile kontrast oranı programatik olarak hesaplanmalıdır:
   - `primaryContainer` / `onPrimaryContainer`
   - `secondaryContainer` / `onSecondaryContainer`
   - `surface` / `onSurface`
   - `surfaceContainerLowest` / `onSurface`
   - `surfaceContainerLow` / `onSurface`
   - `surfaceContainer` / `onSurface`
   - `surfaceContainerHigh` / `onSurface`
   - `surfaceContainerHighest` / `onSurface`
2. Metin çiftleri için en az **4,5:1** kontrast şartı aranmalıdır.
3. Testler **bütün ön tanımlı tema seed'leri** için çalıştırılmalıdır:
   `#2E7D32` (Zeytin), `#1565C0` (Safir), `#C62828` (Nar),
   `#6A1B9A` (Lavanta), `#6D4C41` (Toprak).
4. Testler hedef Flutter revision'ında çalıştırılmalı ve **CI mobil kalite kapısına**
   bağlanmalıdır.
5. **Snapshot/golden testleri:** Bu testler yalnızca Flutter SDK veya
   `material_color_utilities` sürümü değiştiğinde palet çıktısındaki değişimi yakalar.
   Snapshot testi **tek başına WCAG uygunluğunu kanıtlamaz.** Programatik kontrast
   testleri (1–4. maddeler) WCAG uygunluğunun asıl kanıtıdır.

---

## 16. Varsayımlar

- Varsayılan tema (Zeytin), herhangi bir kurum teması atanana kadar bütün yeni kurumlarda
  kullanılır. Bu karar ana planda açık değildir; §9.1'deki "kurum adını, logosunu, ana
  rengini değiştirebilir" ifadesi makul bir varsayılanın varlığını gerektirir.
- Koyu tema (dark mode) ilk sürüm kapsamında değildir. §3'teki bütün renkler açık tema
  varsayılarak tanımlanmıştır. Koyu tema sonraki fazda eklenebilir; çekirdek token yapısı
  buna uygundur.
- §4.1'de belirtildiği gibi ilk sürümde özel bir font dosyası kullanılmaz. İleride kuruma
  özel font istenirse bu yeni bir ürün kararı ve token revizyonu gerektirir.
- §3.2.3'teki kontrast kontrolleri, istemci tarafında (Flutter'da) anlık olarak değil,
  sunucuda renk kaydedilirken doğrulanır. Sunucu yalnızca ham `primary`/`secondary` ve
  §3.2.2 adım 3 algoritmasıyla seçilen `onPrimary`/`onSecondary` değerlerini doğrular;
  türetilmiş Material container/surface çiftlerini doğrulamaz. Mobil uygulama sunucudan
   gelen ve kontrastı zaten doğrulanmış renkleri uygular. Türetilmiş çiftlerin
   WCAG uygunluğu UI-003 programatik kontrast testleriyle doğrulanır; snapshot/
   golden testleri yalnızca SDK/paket sürüm değişikliğinde palet farkını yakalar.
    Detaylı kabul kapıları §15.2'dedir.
- Bileşen düzeyinde tokenların (§10) tam listesi, UI-003 uygulaması sırasında
  genişleyebilir. Bu belge, 59 ekran envanterinde ortak olan bileşenleri kapsar.
- `ColorScheme.fromSeed` ile `DynamicSchemeVariant.content` kullanan ton üretimi, Flutter
  3.22+ ile gelen Material 3 ton algoritmasına dayanır. Hedef Flutter SDK sürümü A-001
  (Flutter seçimi) ve A-011 (uygulama iskeleti) kararlarıyla uyumludur.

---

## 17. Bilinen sınırlamalar

- Bu belge, bileşenlerin tam görsel spesifikasyonunu (padding, margin, alignment piksel
  piksel) vermez; yalnızca token düzeyinde bağlayıcı değerleri tanımlar. Piksel hassasiyeti
  `UI-003` uygulama görevine aittir.
- §3.2.1'deki ön tanımlı temalar 5 adetle sınırlıdır. Gerçek kullanıcı geri bildirimiyle tema
  sayısı artırılabilir; bu bir ürün kararı değil uygulama güncellemesidir.
- Koyu tema desteği ilk sürüm kapsamında değildir; açık tema varsayılmıştır. Koyu tema
  eklendiğinde §3'teki nötr palet (neutral-*) tersine çevrilmeli, işlevsel renkler (success,
  error, vb.) koyu zeminde yeniden değerlendirilmelidir.
- Eski sistem (Excel/HTML/Apps Script) bu repoda bulunmadığından, token değerlerinin eski
  sistemdeki görsel kararlarla karşılaştırması yapılmamıştır; bu belge yalnızca onaylı ana
  plana ve Dalga 0 belgelerine dayanır.
- Tablet cihazlar için optimize edilmiş özel layout (bölmeli görünüm, çok sütunlu yapı) ilk
  sürüm kapsamında değildir (§13). Tablet uyumu eklendiğinde `content-max-width`, breakpoint
  ve büyük ekran gezinme tokenları eklenmelidir.

---

## 18. Kapsam dışı bırakılanlar

- Somut Flutter `ThemeData`, `ColorScheme` veya `ThemeExtension` uygulaması (`UI-003` kapsamı).
- Navigasyon kabuğu, sekme bileşeni ve rol bazlı menü sözleşmesi (`UI-002` kapsamı).
- Logo yükleme, saklama ve görüntüleme teknik detayı (`ORG-010`, `ORG-011` kapsamı).
- Koyu tema (dark mode) desteği (sonraki faz).
- Tablet için optimize edilmiş özel layout, bölmeli görünüm ve büyük ekran gezinme
  kalıpları (sonraki faz).
- Özel font yükleme ve kuruma özel font desteği (sonraki faz).
- İkon seti seçimi — Flutter Material ikonları varsayılan olarak kullanılır; özel ikon seti
  sonraki tasarım görevlerindedir.
- Web yönetim paneli tasarım tokenları (Dalga 8, `WEB-*` kapsamı).
- Bildirim, push ve duyuru bileşenlerinin tokenları (Dalga 8, `NOTIFY-*` kapsamı).
