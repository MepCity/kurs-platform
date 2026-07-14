# ADR-005 — Yerel mobil veritabanı ve kalıcı yazma kuyruğu

| Alan | Değer |
|---|---|
| Görev | A-005 — Yerel mobil veritabanı/kuyruk ADR'si ve kalıcı yeniden deneme deneyi |
| Durum | Kabul edildi |
| Tarih | 14 Temmuz 2026 |
| Karar sahibi | Ürün sahibi |
| Bağımlılıklar | `SENKRONIZASYON_VE_CAKISMA.md` (P-010) |
| İlgili sonraki görevler | IAM-008, SYNC-001, SYNC-002, SYNC-003, A-011, ATT-019 |

## 1. Karar özeti

Flutter istemcisi için yerel kanonik önbellek ve kalıcı yazma kuyruğu, **Drift
`NativeDatabase` + `sqlite3` 3.x build hook `source: sqlite3mc`** üzerinde tutulmalıdır.
`sqlite3mc`, SQLite3MultipleCiphers'tır. `PRAGMA key`, veritabanına herhangi bir sorgu veya
migration gönderilmeden önce uygulanır; açılış `PRAGMA cipher` ve `sqlite_version()` ile
şifreli motoru doğrular. Eski `sqlcipher_flutter_libs` yolu seçilmez. Paket sürümleri A-011'de
pinlenebilir, ancak teknoloji yolu bu ADR'de bağlayıcıdır. SQLCipher uyumluluk modu yalnız
eski bir DB'nin taşınması gerekirse ayrı migration kararıyla kullanılabilir.

Bu karar, `ADR-001`in Flutter önerisi onaylandığında uygulanır. Flutter kararı kabul
edilmezse SQLite/sqlite3mc veri modeli ve bu ADR'deki davranış kuralları korunur; yalnız
platform erişim katmanı yeni bir ADR ekiyle seçilir.

## 2. Bağlam ve değişmezler

`SENKRONIZASYON_VE_CAKISMA.md` §2 ve §4, aşağıdakileri bağlayıcı kılar:

- kesin `2xx` sunucu onayı olmadan kayıt `SUCCEEDED` olmaz ve kalıcı kuyruktan silinmez;
- belirsiz ağ sonucu, `202`, `429` ve `5xx` aynı `clientMutationId` ile yeniden denenir;
- kesin `400`/`403`/`404`/`409`/`422` sonucu görünür `NEEDS_ATTENTION` olur;
- kullanıcı + kurum bağlamları arasında kuyruk, önbellek ve hassas özetler karışmaz;
- aynı hedefte sıralı yazmalar, onaylanmamış bağımlı oluşturma ve oturum değişimi güvenli
  biçimde ele alınır.

Yerel veri depolaması bu kuralları gevşetmez. Sunucu; kimlik, yetki, kurum/sınıf kapsamı,
`rowVersion` ve idempotency kararının tek kaynağıdır. Yerel kayıt ancak görünür durum ve
yeniden deneme bilgisidir.

## 3. Değerlendirilen seçenekler

| Seçenek | Güçlü yön | Bu görev için sınırlama | Sonuç |
|---|---|---|---|
| Drift NativeDatabase + sqlite3mc | Transaction, migration, tek executor ve güncel sqlite3 3.x build hook | Native sürüm/lisans güncellemesi izlenir | **Önerildi** |
| Drift + düz SQLite | Daha sade kurulum | Şifreli yerel veri gereksinimini karşılamaz | Reddedildi |
| `sqflite` / anahtar-değer deposu | Basit erişim | İlişkisel dependency, atomik claim ve sıra için yetersiz | Seçilmedi |
| `sqlcipher_flutter_libs` | Eski yaklaşım | sqlite3 3.x build hook yoluyla çakışır | Reddedildi |
| Sadece secure storage veya anahtar-değer deposu | Küçük sırlar için basit | Sıralı, bağımlı, sorgulanabilir ve atomik kalıcı kuyruk için uygun değildir | Reddedildi |

Bu değerlendirme, ürünün SQL kullanmasını zorunlu kılan bir backend kararı değildir. Seçim;
yerel kuyrukta atomik durum geçişi, ilişkisel bağımlılık sorgusu, migration ve denetlenebilir
tekrar üretilebilirlik ihtiyacınadır.

## 4. Bağlayıcı yerel model

Her **global kullanıcı** için ayrı şifreli DB dosyası ve ayrı rastgele DB anahtarı vardır.
Dosya adı kullanıcı adı, telefon veya e-posta içermez; güvenli depodaki rastgele yerel profil
kimliğinden türetilir. Aynı kullanıcının kurumları DB içinde `scopeType` + `organizationId`
ile ayrılır. Kullanıcı değişiminde eski bağlantı tamamen kapatılır, anahtar etkin bellekten
temizlenir ve yeni kullanıcı eski DB'yi açamaz. Anahtar kaybı/DB açma hatasında uygulama
sessizce boş DB oluşturmaz; kullanıcıya kurtarılamayan bekleyen yazılar için güvenli hata
gösterilir.

`pending_mutations` tablosu en az şunları taşır:

- `localOperationId`, `clientMutationId`, `scopeType`, nullable `organizationId`, `actorUserId`;
- HTTP yöntem/yol, güvenli biçimde saklanan istek gövdesi, `expectedRowVersion`;
- `PENDING`, `SYNCING`, `RETRY_WAIT`, `SUCCEEDED`, `NEEDS_ATTENTION`, `BLOCKED` durumu;
- `createdSequence` (pozitif monoton sıra), oluşturulma/son deneme/sonraki deneme zamanı,
  `attemptCount >= 0` ve güvenli hata kodu;
- `targetOrderingKey` ve ilişkisel `mutation_dependencies` bağımlılık modeli.

`GLOBAL` kapsamda `organizationId` NULL, `ORGANIZATION` kapsamda zorunludur ve CHECK ile
zorlanır. `clientMutationId`, `actorUserId + scopeType + organizationId?` kapsamında iki
partial unique index ile NULL-güvenlidir. İlk yerel transaction'da üretilir ve aynı satırın her ağ denemesinde
değişmeden kullanılır. İstek kaydı, iyimser yerel görünüm ve durum değişimi aynı yerel
transaction'da yazılır. `SYNCING` olarak kalmış kayıt uygulama açılışında `PENDING`e geri
alınır; anahtar korunur. `SUCCEEDED` olmayan hiçbir satır otomatik silinmez. `SUCCEEDED`
satırının temizliği yalnız kanonik sunucu sonucu ile yerel uzlaştırma tamamlandıktan sonra,
ayrı saklama politikasına göre yapılabilir.

Bağlantı setup'ı her açılışta `PRAGMA foreign_keys = ON` uygular ve sonucu `1` olarak
doğrular. Ana kuyruk satırı ile dependency çözümleme/eklemeleri tek Drift transaction'ında
yürür; eksik, farklı scope'lu veya eklenemeyen dependency tüm eklemeleri rollback eder.

Anahtar ve veri yaşam döngüsü ile platform özetleri:

- sqlite3mc veritabanı anahtarı Keychain/Keystore destekli güvenli depoda bulunur; kuyrukta
  token, parola veya ham güvenli-depo değeri bulunmaz.
- İstek gövdesi yalnız sözleşmenin gerektirdiği alanları içerir; ham hata gövdesi, token,
  telefon, adres veya serbest not log/audit/diagnostic alanına kopyalanmaz.
- Oturum iptalinde kuyruk başarı/silinmiş sayılmaz, `BLOCKED` kalır. V1'de şifreli DB ve anahtarı
  cihazlar arası/cloud backup ile taşınmaz; uygulama kaldırılınca bekleyen yazılar kaybolabilir.
- Yerel migration, uygulama sürüm geçişinde transaction ile yürütülür; başarısız migration
  kuyruğu temizlemez. Secure-storage IAM-008, gerçek kuyruk/DB SYNC-001, gönderici ve yeniden
  deneme SYNC-002/SYNC-003 kapsamındadır.

## 5. Yeniden deneme ve hata sınıflandırması

Gönderici yalnız etkin yerel bağlamdaki, bağımlılığı çözülmüş kaydı seçer. Aynı hedefin
mutasyonları oluşturulma sırasını korur; farklı bağımsız hedefler paralel olabilir. Geri
çekilme üstel ve jitter'lıdır; kesin parametreler A-011/SYNC-003 uygulama ölçümüyle
belirlenecektir.

| Sonuç | Kalıcı durum | Silme / sonraki hareket |
|---|---|---|
| Kesin `2xx` | `SUCCEEDED` | Kanonik sonucu transaction ile uygula; ancak sonra saklama politikasıyla temizle |
| `202`, cevap kaybı/ağ hatası, `429`, `5xx` | `SYNCING` veya `RETRY_WAIT` | Aynı anahtarı koru; durum sorgula veya yeniden dene |
| `401` / `SESSION_REVOKED` | `BLOCKED` | Oturum çözülene kadar tut; eski yazıyı otomatik gönderme |
| `400`, `403`, `404`, `409`, `422` | `NEEDS_ATTENTION` | Otomatik tekrar etme; düzeltilmiş işlem yeni anahtar alır |

Bu tablo P-010 §4.3'ün uygulama biçimidir; sunucunun idempotency, audit veya gerçek zamanlı
olay kararını istemci üstlenmez.

### 5.1. Bağlam-sınırlı erişim, sıra ve claim

Bütün find, transition, `SYNCING`/`SUCCEEDED`/terminal hata, temizlik, claim ve gönderilebilir
seçimleri `actorUserId + scopeType + organizationId` ile filtrelenir. Tek başına
`localOperationId` alan API yoktur; aynı kimliği bilen başka kullanıcı veya kurum kaydı
okuyamaz, değiştiremez ya da silemez.

Gönderim UUID/alfabetik kimliğe göre değil `createdSequence`e göredir. Aynı
`targetOrderingKey` için sonraki yazı önceki başarıya kadar seçilemez. Tamamlanmamış bağımlılık
bağlı işlemi engeller; öncül `SUCCEEDED` olduğunda işlem seçilebilir olur.

`RETRY_WAIT`, kalıcı `nextAttemptAt` taşır. `sendableFor` ve `claim`, yalnız zamanı gelmiş
retry kaydını kabul eder; saat testlerde enjekte edilir. Claim ağ denemesini başlatırken
`attemptCount`ı bir kez artırır; geçici hata geçişi ikinci kez artırmaz.

Drift için tek DB executor/dispatcher kullanılır. Foreground ve OS background worker aynı
claim yüzeyinden geçer. Koşullu `UPDATE … RETURNING`, kaydı tek SQL ifadesinde `SYNCING`e
geçirir; iki eşzamanlı denemeden yalnız biri satır döner.

## 6. Deney ve doğrulama

`experiments/a005_local_queue` içindeki bağımsız Dart deneyi, üretim uygulaması değildir.
macOS üzerinde Drift NativeDatabase + sqlite3mc ile aşağıdaki kanıtları otomatikleştirir:

1. cipher/SQLite sürüm kontrolü, aynı anahtarla kapat-aç ve yanlış anahtarla açma/okuma hatası;
2. kuyruk kalıcılığı, aynı `clientMutationId`, erken temizlememe ve yarım `SYNCING` toparlama;
3. başka kullanıcı/kurum için negatif okuma, transition ve temizleme;
4. FK etkinliği ve eksik dependency enqueue'unda transaction rollback;
5. GLOBAL/ORGANIZATION tekilliği, createdSequence, hedef sırası ve dependency;
6. aynı hedefte doğrudan claim engeli, tek kazanan atomik claim ve `BLOCKED` oturum davranışı;
7. gelecekteki `nextAttemptAt` engeli ve bir ağ denemesinde tek `attemptCount` artışı.

Çalıştırma: `dart pub get`, `dart test -r expanded`, `dart analyze`,
`dart format --output=none --set-exit-if-changed lib test`.

Bu A-005 davranış deneyi **PASS** olduğunda kabul edilir. iOS/Android production entegrasyonu,
Keychain/Keystore, uygulama kaldırma ve gerçek background worker kanıtı downstream kalite
kapısıdır; bunların bu deneyde olmaması A-005 PASS sonucunu geçersiz kılmaz.

## 7. Sonuçlar, kapsam dışı ve açık işler

Olumlu sonuç; kuyruk için transaction, sorgu, bağımlılık ve migration yüzeyi tek, test
edilebilir veri katmanında kalır. Maliyet; Drift/sqlite3mc native paketlerinin sürüm ve
lisans/güvenlik güncellemelerinin A-011/A-012'de izlenmesidir.

Bu ADR; Flutter iskeleti, paket sürümü pinleme, sqlite3mc anahtar üretimi, secure storage
adaptörü, background scheduler, API istemcisi, gerçek migration, cihaz testi veya sunucu
idempotency uygulamasını eklemez. Bunlar sırasıyla A-011, IAM-008 ve SYNC görevlerinin
kapsamındadır. Cihaz/cloud backup kararı bu ADR'de V1 için "taşınmaz" olarak verilmiştir.

## 8. Kabul kontrolü

| Ölçüt | Sonuç |
|---|---|
| Kalıcı yerel veritabanı ve kuyruk yaklaşımı, alternatifleriyle birlikte açıkça seçildi | Karşılandı |
| P-010'un anahtar, durum, tekrar, terminal hata ve izolasyon kuralları korundu | Karşılandı |
| Uygulama kapat-aç, aynı anahtar, yanlış anahtar ve erken silmeme için otomatik deney eklendi | Karşılandı |
| Kullanıcı/kurum ayrımı ve kesin hata görünürlüğü deneyle doğrulandı | Karşılandı |
| Bağlam izolasyonu, dependency/sıra ve atomik claim otomatik deneyle doğrulandı | Karşılandı |

## 9. Kaynaklar

- `URUN_VE_UYGULAMA_PLANI.md` §11.2, §12.2, §13, §18.2 ve §21.
- `SENKRONIZASYON_VE_CAKISMA.md` §2–§7, §9–§10.
- `KRITIK_TEST_VE_KABUL_PLANI.md` KAP-05, KAP-09, KAP-33 ve KAP-34.
- [sqlite3 build hook seçenekleri](https://pub.dev/documentation/sqlite3/latest/topics/hook-topic.html) — `source: sqlite3mc`.
- [Drift NativeDatabase API](https://pub.dev/documentation/drift/latest/native/) — setup aşaması ve native executor.
- [SQLite3MultipleCiphers PRAGMA belgeleri](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_sql_pragmas/) — `PRAGMA key` ve cipher ayarları.
