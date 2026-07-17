# API Genel Kuralları

| Alan | Değer |
|---|---|
| Görev | P-009 — API genel kurallarını yaz |
| Belge sürümü | 1.2 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlı sözleşmeler | `YETKI_MATRISI.md`, `VERI_MODELI.md` |
| Son güncelleme | 15 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, mobil istemciler ile modüler monolit uygulama API'si arasındaki ortak HTTP sözleşme kurallarını tanımlar. Sonraki modül sözleşmeleri burada belirlenen sürümleme, kimlik doğrulama, kurum bağlamı, hata, listeleme ve yazma kurallarına uyar.

Kaynak/endpoint envanteri bu belgenin kapsamı değildir. Senkronizasyon kuyruğunun durum makinesi ve varlık bazlı çakışma çözümü `P-010`; geri alma komutları `DENETIM_VE_GERI_ALMA_ILKELERI.md`; rapor uçları `P-012` kapsamındadır.

## 2. Bağlayıcı ilkeler

- Dış istemciler yalnızca sürümlenmiş API üzerinden işlem yapar; veritabanına doğrudan erişmez.
- İstek ve cevaplar şemaya göre doğrulanır. Sunucu, istemcinin `organizationId`, rol, sahiplik veya yetki iddiasını güvenilir kabul etmez.
- Varsayılan politika erişimi reddetmektir. Kimlik doğrulama, etkin oturum, kurum/sınıf kapsamı ve işlem izni her istekte sunucuda değerlendirilir.
- Bir kurumun verisi başka kuruma sorgu, hata, liste özeti veya ilişki yoluyla sızdırılamaz. Platform yöneticisi istisnası dahil kurum verisi erişimi denetim kaydı üretir.
- Normal silme uçları fiziksel silme yapmaz; desteklenen varlıklar arşivlenir. Arşivleme de yetki, sürüm ve denetim kurallarına tabidir.
- Sunucu kesin başarı cevabı vermeden istemci işlemi kalıcı başarılı kabul etmez.

## 3. Protokol, sürümleme ve ortak başlıklar

### 3.1. Taşıma ve kök yol

- API yalnızca TLS üzerinden sunulur. Üretimde düz HTTP desteklenmez.
- Birinci genel sürümün kök yolu `/api/v1` olur. Geriye uyumsuz istek/cevap değişikliği yeni ana API sürümü gerektirir; alan eklemek gibi uyumlu değişiklikler aynı sürümde yapılabilir.
- Kaynak adları çoğul, küçük harfli ve `kebab-case`; kimlikler UUID metni olarak yol parametresinde kullanılır. İstemci kimliklerin sıralı veya tahmin edilebilir olduğunu varsaymaz.
- Gövdeli istek ve JSON yanıtlarda `application/json; charset=utf-8` kullanılır. Dosya istisnaları ilgili modül sözleşmesinde açıkça belirtilir.

### 3.2. İstek kimliği ve başlık güvenliği

- İstemci her istek için `X-Request-Id` gönderebilir. Değer 1–128 karakter uzunluğunda olmalı ve yalnızca ASCII harf/rakam ile `.`, `_`, `-`, `:` karakterlerini içermelidir; geçersiz değer `400 INVALID_REQUEST` ile reddedilir. Gönderilmezse sunucu bu sınırlara uyan bir değer üretir ve yanıtta döndürür.
- `X-Request-Id` yalnızca izleme/denetim bağlamıdır; güvenlik, kimlik doğrulama, yetkilendirme veya idempotency amacıyla kullanılmaz.
- `Idempotency-Key`, `X-Request-Id` ile aynı kavram değildir: ilki bir mantıksal yazma denemesini ve güvenli yeniden denemeyi tanımlar, ikincisi tek HTTP isteğini izler. `Idempotency-Key` yalnızca yazma komutlarında kullanılır; sunucu uzunluk ve izinli karakter/biçim sınırını doğrular, geçersiz anahtarı `400 INVALID_REQUEST` ile reddeder. Kesin biçim sınırı her modül sözleşmesinde en fazla 128 ASCII görünür karakter olacak şekilde beyaz listeyle belirtilir.
- Loglar en az istek kimliği, yol, yöntem, sonuç ve süreyi taşıyabilir; parola, ham token, telefon, adres, serbest metin notu ve tam gövde kaydedilmez.

### 3.3. Zaman ve adlandırma

- JSON alanları `camelCase`, enum/değer kodları `UPPER_SNAKE_CASE` kullanır. Tarih-saatler ISO 8601/RFC 3339 UTC ofsetiyle, salt tarihler `YYYY-MM-DD` biçimindedir.
- `TIMESTAMPTZ` alanları API'de UTC/RFC 3339 anları olarak taşınır. `sessionDate`, `plannedDate` ve benzeri `DATE` alanları UTC'ye dönüştürülen anlar değil, kurumun `defaultTimezone` bağlamındaki iş gününü temsil eder.
- İstemci cihazının saat dilimini kullanarak kurum iş gününü sessizce değiştiremez. Gün başlangıcı/bitişi ve yaz-kış saati geçişleri kurumun `defaultTimezone` değerine göre değerlendirilir.

## 4. Kimlik doğrulama, oturum ve kurum bağlamı

- Kimlik doğrulama gereken uçlarda `Authorization: Bearer <platform-access-token>` zorunludur. Bu, IAM'in verdiği 256-bit rastgele **opaque** token'dır; haricî kimlik sağlayıcısının ID/access tokenı iş API'lerinde kabul edilmez. Eksik, geçersiz, süresi dolmuş veya iptal edilmiş token `401 UNAUTHENTICATED` döndürür.
- Native OIDC Authorization Code + PKCE akışında `state`, `nonce`, `code_verifier` ve `code_challenge` mobil istemcinin sorumluluğundadır; `state`/`nonce` callback'te eşleşmeden, `S256` PKCE doğrulanmadan kod kabul edilmez. IAM, sağlayıcının tokenını issuer JWKS ile imza, `iss`, `aud`, `azp`, süre ve gerekli scope bakımından doğrular; API erişimi için provider ID/access tokenı kabul etmez. Kesin token değişim akışı A-004R3'te seçilen sağlayıcıyla IAM-001'de bağlanır.
- IAM'in platform token üretmesi, doğrulanmış `issuer` + `subject` çiftinin `user_identities` eşlemesine dayanır. Kullanıcı adı, e-posta, ad/soyad veya istemciden gelen `userId` ile hesap eşleme/oluşturma yapılamaz.
- Kurum kapsamlı token tek etkin kurum üyeliğine bağlıdır. İstemci kurum kimliğini yol, sorgu veya gövdeye koyarak token kapsamını genişletemez.
- `contextSelectionToken`, en az 256-bit opaque, 5 dakikalık, refresh edilemeyen ve tek
  kullanımlı kurum-bağlamsız tokendir. Yalnız kullanıcının kurumlarını listeler ve bir etkin
  üyelik için tek kurum-token değişimi yapar; listeleme tokenı tüketmez, başarılı seçim tüketir.
  Kurum kapsamlı API'ye erişirse `403 ORGANIZATION_CONTEXT_REQUIRED` döner.
- Her kurum kapsamlı istekte sunucu üyeliğin etkinliğini, tokenın `sessionGeneration` değerini, rolün geri alınmadığını ve işlem iznini doğrular. Bu kontroller veritabanı FK'lerinin yerine geçmez.
- Kurum yöneticisi yalnızca kendi kurumunda işlem yapabilir. Hoca operasyonel sınıf verisine yalnızca etkin atandığı sınıfta erişebilir; dar metaveri izni bu sınırı genişletmez.
- Platform yöneticisinin kurum erişimi açık hedef kurum bağlamıyla yürütülür ve her görüntüleme/değişiklik denetlenir.

### 4.1. Yetki reddi ve varlık gizleme

- Kimliği doğrulanmış fakat işlemi yapmaya yetkisi olmayan istek `403 FORBIDDEN` döndürür.
- Erişim kapsamı dışındaki kurum veya sınıf kaydına doğrudan kimlikle erişim, varlığı açığa çıkarmamak için `404 RESOURCE_NOT_FOUND` döndürür.
- Hata yanıtları hesap, üyelik veya başka kurum kaydının varlığı hakkında ek bilgi vermez.

## 5. İstek, cevap ve hata biçimi

### 5.1. Başarılı cevaplar

Tekil kaynak cevabı kaynak nesnesidir; koleksiyonlar ortak zarfla döner:

```json
{
  "items": [],
  "page": { "nextCursor": null, "hasNextPage": false }
}
```

- Oluşturma `201 Created` ve oluşturulan nesneyi döndürür; `Location` yeni kaynağın kanonik yoludur.
- Gövdesi olmayan başarı `204 No Content` döndürür.
- Asenkron işlem kabulü `202 Accepted` ile takip yolu/durum içerir; istemci bunu tamamlanmış saymaz.
- Sunucu `createdAt`, `updatedAt`, `rowVersion` ve denetim alanlarını üretir; istemci bunları yazma gövdesinde gönderemez.
- Çevrimdışı oluşturulması desteklenen bir kaynakta istemci oluşturma gövdesinde `id` olarak UUID gönderebilir. Sunucu UUID biçimini, tokenın kurum kapsamıyla uyumunu ve benzersizliği doğrular. İstemci UUID'si yalnızca oluşturma işleminde kullanılabilir; mevcut kaydın kimliğini değiştiremez.
- İstemci UUID'sini kabul eden kaynaklar ilgili modül sözleşmesinde açık beyaz liste olarak belirtilir. Bu listede olmayan kaynaklarda `id` sunucu tarafından üretilir; istemcinin `id` göndermesi `422 VALIDATION_FAILED` döndürür.
- Aynı istemci UUID'si ve `Idempotency-Key` ile yapılan yeniden deneme ikinci kayıt üretmez; ilk tamamlanmış sonucun eşdeğeri döner.

### 5.2. Hata zarfı

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Gönderilen bilgiler doğrulanamadı.",
    "requestId": "c07e1b3c-2dd3-45e5-a9a1-8b9705c95e4a",
    "fieldErrors": [
      { "field": "name", "code": "REQUIRED", "message": "Ad zorunludur." }
    ]
  }
}
```

- `code` sabit, makinece işlenebilir `UPPER_SNAKE_CASE` değerdir; istemci davranışını `message` metnine bağlamaz.
- `message` kullanıcıya gösterilebilecek Türkçe ve güvenli açıklamadır. `fieldErrors` yalnızca izinli alanlardaki doğrulama hatalarında döner.
- İstisna yığını, SQL ayrıntısı, token, kişisel veri veya başka kurum kimliği yanıtta bulunmaz.

| HTTP | Hata kodu örnekleri | Kullanım |
|---:|---|---|
| 400 | `INVALID_REQUEST`, `INVALID_CURSOR` | JSON, yol, sorgu, başlık veya cursor biçimi geçersiz. |
| 401 | `UNAUTHENTICATED`, `SESSION_REVOKED` | Kimlik doğrulama/oturum geçersiz. |
| 403 | `FORBIDDEN`, `ORGANIZATION_CONTEXT_REQUIRED` | Kimlik var, bağlam veya izin yetersiz. |
| 404 | `RESOURCE_NOT_FOUND` | Kaynak yok veya erişim kapsamı dışında. |
| 409 | `VERSION_CONFLICT`, `STATE_CONFLICT`, `IDEMPOTENCY_KEY_REUSED`, `GROUP_UNDO_CONFLICT`, `UNDO_ALREADY_APPLIED`, `UNDO_PRECONDITION_FAILED` | Durum, sürüm, geri alma ya da yazma anahtarı çelişkisi. |
| 410 | `EXPORT_EXPIRED` | Süreli dışa aktarma artefaktı artık indirilemez. |
| 422 | `VALIDATION_FAILED`, `BUSINESS_RULE_VIOLATION`, `UNDO_NOT_SUPPORTED`, `UNDO_SCOPE_MISMATCH` | Alan, iş kuralı veya geri alma kapsamı geçersiz. |
| 429 | `RATE_LIMITED` | İstek hızı sınırı aşıldı; uygun olduğunda `Retry-After` eklenir. |
| 500 | `INTERNAL_ERROR` | Beklenmeyen hata; istemci ayrıntı görmez. |

Tablo ortak kodları ve mevcut V1 sözleşmelerinde kullanılan modül kodlarını gösterir. Yeni bir
modül hata kodu eklediğinde HTTP anlamını, güvenli mesajını ve kuyruk davranışını kendi API
sözleşmesinde tanımlar; bu tabloyla çelişen ikinci bir anlam veremez.

## 6. Okuma, listeleme, filtreleme ve sıralama

- Koleksiyon uçları varsayılan olarak sayfalıdır. İlk sayfa cursor göndermez; sonraki sayfa yalnızca önceki yanıttaki opak `nextCursor` ile alınır.
- Sıralama deterministiktir: eşit birincil sıralama değerlerinde `id`, zorunlu son bağlayıcı (`tie-breaker`) olarak kullanılır.
- Cursor opaktır; istemci içeriğini düzenleyemez veya anlam çıkaramaz. Cursor tenant, yetkiyle daraltılmış filtre ve sıralama bağlamına bağlıdır; farklı sorguda yeniden kullanılamaz. Geçersiz, süresi dolmuş veya bağlamla uyuşmayan cursor `400 INVALID_CURSOR` döndürür.
- `limit` pozitif tamsayıdır; sunucu üst sınır uygular, sınırı aşanı `422 VALIDATION_FAILED` ile reddeder. Kesin varsayılan/üst değer uygulama performans ölçümüyle belirlenir.
- Sıralama `sort` ve isteğe bağlı `order=ASC|DESC` ile yapılır. Her kaynak izinli alanları beyaz liste olarak tanımlar.
- Filtreler açık alanlarla tanımlanır (ör. `status=ACTIVE`, `termId=<uuid>`). Serbest sorgu dili, SQL parçası veya istemcinin oluşturduğu filtre ifadesi kabul edilmez.
- Yetki filtresi sayfalamadan önce uygulanır. İlişkili nesne özeti, çağıranın o ilişkiyi görme hakkıyla sınırlıdır.
- `totalCount` varsayılan cevap alanı değildir; pahalı sayım sorgusu gerektirdiğinden yalnızca ilgili kaynak sözleşmesinde açıkça istenebilen ve desteklenen bir seçenek olarak tanımlanır.
- Sayfalama, istekler arasındaki eşzamanlı ekleme/güncelleme/arşivleme karşısında kesin snapshot garantisi vermez. İstemci cursor üzerinden güvenli ilerler; tutarlılık ve yeniden eşitleme ayrıntıları `P-010` kapsamındadır.

## 7. Yazma, sürüm ve idempotency

### 7.1. Değiştirilebilir kayıtların sürümü

- Kısmi güncelleme `PATCH` ile yapılır. Gövdede bulunmayan alan değiştirilmez; mevcut değer korunur. Açık `null` yalnızca kaynak sözleşmesinde nullable ve temizlenebilir olarak tanımlanmış alanı temizler. Zorunlu veya temizlenemez bir alana `null` göndermek `422 VALIDATION_FAILED` döndürür.
- `PUT`, yalnızca ilgili kaynak sözleşmesinin tam kaynak değiştirme olarak açıkça tanımladığı durumda kullanılabilir; aksi hâlde V1'de desteklenmez.
- Arşivleme, geri yükleme, rol iptali ve benzeri iş komutları sıradan `PATCH` değildir; ilgili komut uçlarında `POST` ile modellenir ve komutun ön koşul/yan etkileri ayrı sözleşmede belirtilir.
- Fiziksel `DELETE`, ayrı ve denetlenen kişisel veri silme prosedürü dışında kullanılmaz; V1 normal API uçlarında desteklenmez.
- Güncellenebilir çekirdek kaynaklar `rowVersion` içerir. İstemci güncelleme, arşivleme ve durum değiştiren komutlarda beklediği sürümü `If-Match-Row-Version` başlığıyla gönderir.
- Sunucu sürüm eşleşmezse değişikliği uygulamaz ve `409 VERSION_CONFLICT` döndürür. Güncel kaydın yeniden okunması normal okuma yetkisine tabidir.
- Yalnızca `SENKRONIZASYON_VE_CAKISMA.md`'de açıkça tanımlanan normal tekil/toplu yoklama
  işaretleme ve düzeltme komutları bu red kuralının istisnasıdır. Tekil veya grup geri alma
  komutları istisnaya dahil değildir; `DENETIM_VE_GERI_ALMA_ILKELERI.md`'deki sürüm ve grup
  çakışması kurallarını uygular.
- Yeni kaynak oluşturma ve değişmez denetim kayıtları bu başlığı gerektirmez. Bu belge genel "son yazan kazanır" kuralı tanımlamaz; varlık bazlı çakışma davranışı `P-010` kapsamındadır.

### 7.2. İdempotent yazma

- Her mobil oluşturma, güncelleme, arşivleme, geri alma ve durum değiştiren komut benzersiz `Idempotency-Key` başlığı taşır. Bu anahtar istemcinin `clientMutationId` değeridir; yeniden denemede aynen korunur.
- Sunucu idempotency kapsamını doğrulanmış işlem bağlamıyla kurar: kurum işleminde
  `organizationId + actorUserId + clientMutationId`, global güvenlik işleminde `actorUserId +
  clientMutationId`, kurum öncesi IAM kimlik doğrulama işleminde ise `actorUserId +
  operationType + deviceIdentifier + requestTokenFingerprint + clientMutationId`.
- Kurum kapsamlı yazmada kapsam kurum, kullanıcı ve anahtardır. Yalnızca platform yöneticisinin
  henüz kurum oluşmadan yaptığı açık global işlemlerde kapsam global, kullanıcı ve anahtardır.
  `provider-token-exchange` gibi kurum öncesi auth akışları bu `GLOBAL` kapsamı kullanmaz;
  bunlar ayrı `IAM_AUTH` kapsamında değerlendirilir. Aynı anahtar farklı işlem türü, hedef yol
  veya istek özetiyle kullanılırsa sunucu işlemi uygulamaz ve `409 IDEMPOTENCY_KEY_REUSED`
  döndürür.
- `IAM_AUTH` kapsamı, başka aktörün veya başka token tabanlı auth işleminin sonucunun
  okunmasını engellemek zorundadır; en az `actorUserId`, `operationType`, `deviceIdentifier` ve
  işlem tipine göre güvenli token fingerprint'i birlikte doğrulanmadan replay sonucu döndürülemez.
- İşlem bazlı `requestTokenFingerprint` kaynağı bağlayıcıdır:
  `PROVIDER_TOKEN_EXCHANGE` için Cognito access tokenı,
  `PLATFORM_ADMIN_ACTIVATE` ve `CONTEXT_ACTIVATE` için `contextSelectionToken`,
  `SESSION_REFRESH` ve `SESSION_LOGOUT` için platform refresh tokenı,
  `DEVICE_SELF_REVOKE` için çağıranın platform access tokenı + çağıranın isteği doğrulayan
  kendi `trusted_devices.id`si (çağıran cihaz) + hedef `trusted_devices.id` (`deviceId`, iptal
  edilecek cihaz — çağıran cihazla aynı olabilir) kullanılır
  (`IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md` §5/§5.1).
- `IAM_AUTH` kapsamında aynı kullanıcı için aynı `clientMutationId` farklı `operationType` ile
  yeniden kullanılırsa parmak izi çakışması oluşur ve `409 IDEMPOTENCY_KEY_REUSED` döner.
- Aynı anahtarla eşdeğer istek tekrarında ikinci yan etki veya denetim kaydı oluşmaz; ilk
  tamamlanmış sonucun eşdeğeri döner. Ham sır içeren cevaplarda bu eşdeğer sonuç, güvenli replay
  escrow/reference yüzeyi üzerinden döner; ham token normal `result_payload`, log veya audit
  içine yazılmaz.
- İşlem sonuçlanmamışsa `SENKRONIZASYON_VE_CAKISMA.md` §3–§4'teki durum makinesi uygulanır. Ağ hatası, `429` ve `5xx`
  idempotency sonucunu terminal yapmaz; istemci kuyruğu silmeden aynı anahtarla güvenli yeniden
  dener. Kalıcı terminal sonuçlar yalnız ilgili sözleşmenin güvenli olarak tanımladığı hata
  kodlarıyla saklanır.

### 7.3. Atomiklik ve denetim

- Bir komutun birlikte değişmesi gereken kayıtları tek transaction içinde güncellenir; başarısızlıkta kısmi kalıcı yazma veya yarım denetim kaydı bırakılamaz.
- Kritik yazmalarda denetim kaydı iş değişikliğiyle aynı başarılı işlemde üretilir. Denetim kaydı değiştirilemez; hassas veri minimizasyonu uygulanır.
- `TEACHER` rolü geri alınırken bağlı etkin sınıf atamaları ve devredilmiş izinler aynı işlemde kapatılır. Yeni yetki değerlendirmesi etkin üyelik, geri alınmamış rol ve gerekiyorsa etkin devredilmiş izni birlikte kontrol eder.

## 8. Toplu işlemler

- Toplu uçlar açık bir komut kaynağı/eylem adı kullanır; tekil uçlara gizli dizi gövdesi eklenmez.
- Her hedef ayrı yetki ve iş kuralı açısından değerlendirilir; gerekiyorsa kendi `rowVersion` değerini taşır.
- Kısmi başarı mümkünse `200 OK` ile açıkça bildirilir; hiçbir hedef sessizce atlanmaz:

```json
{
  "results": [
    { "id": "<uuid>", "status": "SUCCEEDED" },
    { "id": "<uuid>", "status": "FAILED", "error": { "code": "VERSION_CONFLICT" } }
  ]
}
```

- İş kuralı tüm grubun atomikliğini gerektirirse ilgili uç bunu açıkça belirtir; ilk geçersiz hedefte hiçbir değişiklik uygulanmaz.
- İstemci kısmi başarılı sonuçları bütün grubun başarılı olduğu varsayımıyla işleyemez.

## 9. Zorunlu kabul senaryoları

1. Geçerli kurum tokenı başka kurum kaydını okuyamaz, değiştiremez veya varlığını öğrenemez.
2. Tek kullanımlı `contextSelectionToken` kurum kapsamlı uca erişemez; etkin olmayan üyelik için
   token değişimi üretemez veya ikinci kez kurum ailesi oluşturamaz.
3. Askıya alınmış üyelik, geri alınmış rol veya eski `sessionGeneration` taşıyan token reddedilir.
4. Hoca, atanmamış sınıfın operasyonel verisini doğrudan kimlikle veya liste filtresiyle göremez; metaveri izni bu sınırı genişletmez.
5. Aynı `Idempotency-Key` ve eşdeğer istek iki kayıt/denetim kaydı üretmez; farklı istekle tekrar kullanım çelişki döndürür.
6. Çevrimdışı oluşturmayı destekleyen beyaz listeli kaynakta istemci UUID'si kabul edilir; aynı UUID ve `Idempotency-Key` yeniden denemede ikinci kayıt üretmez. Beyaz liste dışındaki kaynakta istemci UUID'si reddedilir.
7. `PATCH`te bulunmayan alan değişmeden kalır; nullable/temizlenebilir alanın açık `null` değeri temizlenir, zorunlu/temizlenemez alanda `VALIDATION_FAILED` döner. İş komutları `POST`tur ve normal API'de fiziksel `DELETE` yoktur.
8. Eski `rowVersion` ile güncelleme/arşivleme sessizce uygulanmaz ve `VERSION_CONFLICT` döner.
9. Doğrulama, yetki, bulunamama, çakışma ve beklenmeyen hata ortak hata zarfı, güvenli hata kodu ve `requestId` taşır; sır/kişisel veri içermez.
10. Cursor deterministik sıralama ve `id` son bağlayıcısı kullanır; başka tenant/filtre/sıralama bağlamında veya geçersiz/süresi dolmuş hâlde `INVALID_CURSOR` döner.
11. Sayfalama, filtreleme ve sıralama yalnızca izinli alanlarla çalışır; sonuç sayfası yetki filtresinden sonra hesaplanır ve kesin snapshot garantisi vermez.
12. Kurum iş günü `defaultTimezone` ile değerlendirilir; istemci cihaz saat dilimi `sessionDate` veya `plannedDate` değerini sessizce değiştiremez.
13. `X-Request-Id` ve `Idempotency-Key` biçim sınırları doğrulanır, birbirinin yerine kullanılamaz ve güvenlik/kimlik doğrulama amacı taşımaz.
14. `TEACHER` rolü geri alma işlemi bağlı etkin sınıf ataması ve devredilmiş izinleri kapatır; sonraki istek reddedilir.
15. Platform yöneticisinin kurum verisi görüntülemesi ve değiştirmesi denetim kaydı üretir.

## 10. Kapsam dışı kararlar

- Kimlik sağlayıcısı, platform token biçimi/ömürleri ve parola sahipliği `A-004` ile kesinleştirilmiştir; kesin endpoint listesi ve oran sınırlama eşikleri ilgili uygulama/operasyon görevindedir.
- Kesin endpoint listesi, kaynak alan şemaları, filtre beyaz listeleri ve sayfa boyutu sınırı ilgili modül sözleşmelerinde tanımlanacaktır.
- Gerçek zamanlı taşıma, çevrimdışı kuyruk durum makinesi, yeniden bağlanma ve çakışma çözümü `SENKRONIZASYON_VE_CAKISMA.md`de tanımlıdır.
- Geri alınabilir işlem listesi ve ters işlem sözleşmesi `DENETIM_VE_GERI_ALMA_ILKELERI.md`; Excel rapor sözleşmesi ve indirme yaşam döngüsü `P-012` kapsamındadır.
- Backend framework, veritabanı ve gözlemlenebilirlik sağlayıcısı bu belgeyle seçilmez.

## 11. Kaynaklarla uyum kontrolü

- Sürümleme, sunucu tarafı yetki doğrulaması, şema doğrulaması, standart listeleme, hata kodları, kısmi başarı ve idempotency ana plan §11.5 ile uyumludur.
- `rowVersion`, `clientMutationId` ve başarı alınmadan kuyruğu silmeme kuralları ana plan §12 ile; `idempotency_keys` temeli `VERI_MODELI.md` §14 ile uyumludur.
- Kurum bağlamı, `sessionGeneration`, rol iptali ve uygulama katmanı yetki kontrolü `VERI_MODELI.md` §2.2, §4.11–4.12 ve §15.5 ile uyumludur.
- Rol/kurum/sınıf kapsamı ve platform yöneticisi erişiminin denetimi `YETKI_MATRISI.md` §2 ve eylem matrisiyle uyumludur.
- Bu belge yeni kaynak şeması, yeni rol veya veri yaşam döngüsü istisnası tanımlamaz.
