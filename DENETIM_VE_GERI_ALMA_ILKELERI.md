# Denetim ve Geri Alma İlkeleri

| Alan | Değer |
|---|---|
| Görev | P-011 — Denetim ve geri alma ilkelerini detaylandır |
| Belge sürümü | 1.2 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlılıklar | P-003 (`YETKI_MATRISI.md`), P-008 (`VERI_MODELI.md`) |
| Son güncelleme | 14 Temmuz 2026 |

## 1. Amaç ve kapsam

Bu belge, ilk sürümdeki denetim kaydının hangi olaylarda nasıl üretileceğini ve yalnızca
açıkça desteklenen işlemlerin nasıl geri alınacağını tanımlar. `VERI_MODELI.md` §13'teki
`audit_action_catalog` ve değiştirilemez `audit_logs` yapısını kullanır; yeni tablo, rol veya
veri yaşam döngüsü tanımlamaz.

Geri alma, veritabanı yedeğine dönmek veya geçmişi silmek değildir. Orijinal denetim kaydına
bağlanan, geçerli durumu doğrulayan ve kendisi de yeni bir denetim kaydı üreten yetkili bir
komuttur. Bu belge normal uygulama akışındaki geri almayı tanımlar; yedekten kurtarma ve
hukuki saklama/imha prosedürü kapsam dışıdır.

## 2. Değişmez ilkeler

1. **Denetim kaydı değişmezdir.** Normal uygulama akışında `audit_logs` satırı güncellenmez,
   silinmez veya geri alma nedeniyle yeniden yazılmaz. Geri alma ilişkisi yalnızca yeni satırın
   `is_undo = true` ve `undo_of_audit_log_id` alanlarıyla kurulur.
2. **Kurum ve kapsam korunur.** Kurum kapsamlı bir kayıt yalnızca aynı kurumdaki,
   `event_scope = ORGANIZATION` olan kaydı geri alabilir. Global bir kayıt yalnızca global
   kaydı gösterebilir. Bu sınır `VERI_MODELI.md` §13.2'deki iki scope-pinned bileşik FK ile
   veritabanı düzeyinde de zorunludur.
3. **Sunucu yetkisi ve tarihsel kapsam esastır.** İstemcide görünen düğme yetki sayılmaz.
   Sunucu geri alma iznini, hedef işlemin yazma yetkisini ve olay anında kaydedilmiş
   `scope_class_id` kapsamını aynı istekte yeniden doğrular. Öğrencinin güncel sınıf ilişkisi,
   geçmiş olayın görünürlüğünü veya geri alma kapsamını değiştirmez.
4. **Açık liste dışında geri alma yoktur.** `audit_action_catalog.is_undoable = true` değeri,
   yalnızca bölüm 6'daki kaynak eylemler için atanır. Katalogda denetleniyor olmak, işlemin
   geri alınabilir olduğu anlamına gelmez.
5. **Geri alma da idempotent ve eşzamanlılığa duyarlıdır.** Her komut benzersiz bir
   `clientMutationId` taşır; aynı istek yalnızca bir kez etkili olur. Kaynak kaydın sürümü veya
   beklenen güncel değer eşleşmiyorsa sunucu sessizce ezmez.
6. **Geri alma fiziksel silme yapmaz.** Arşivlenebilir varlıklarda ters işlem arşivleme veya
   geri yüklemedir. Denetim kaydı, ilişkili geçmiş kayıtları ve başka bağımlılıklar silinmez.
7. **Hassas değerler gereksiz yayılmaz.** Denetim kaydındaki eski/yeni değerler hata mesajına,
   istemci loguna veya genel gözlemlenebilirlik kaydına yazılmaz. Yanıt, yalnızca isteği yapanın
   zaten görmeye yetkili olduğu geri alma sonucu alanlarını içerir.

## 3. Denetim olayı sözleşmesi

### 3.1. Zorunlu kayıt alanları

Her denetim olayı, `VERI_MODELI.md` §13.2'de tanımlanan kurum (global değilse), işlemi yapan
kullanıcı, işlem türü, hedef tür/kimlik, eski değer, yeni değer, zaman ve istek/cihaz bağlamını
taşır. Sistem veya otomatik süreçlerde `actor_user_id` boş olabilir; bu durum işlem türü ve
istek bağlamıyla ayırt edilir.

İstekten doğan audit kaydındaki `request_id`, P-009 `X-Request-Id` değeridir; UUID olmak
zorunda değildir. 1–128 karakterlik ve yalnızca ASCII harf/rakam ile `.`, `_`, `-`, `:` içeren
değerler kabul edilir. İstemci göndermediyse API'nin ürettiği aynı geçerli değer audit kaydına
yazılır; istek dışı sistem işleri `NULL` taşıyabilir.

`old_value` ve `new_value` hedef kaydın tüm satırının gelişigüzel kopyası değil,
`action_type + payload_schema_version` ile belirlenen şemalı özetidir. Aynı sürümlü katalog
şeması `event_metadata` için izinli anahtarları ve `reason_code` için kontrollü kodları da
tanımlar; serbest reason veya serbest metadata kabul edilmez. Parola, erişim/yenileme belirteci,
sır niteliğindeki cihaz verisi ve ham hassas dosya içeriği hiçbir zaman bu payloadlara yazılmaz.

API denetim görünümü, bu şemalı payloadı alan bazlı olarak **sunucuda** maskeler. Veli iletişim
bilgisi, kişi serbest metni, yoklama durumu ve öğretmen notu gibi yüksek riskli alanlar ancak
ilgili alanı görme yetkisi olan istemciye maskesiz döner. Maskeleme, saklanan sunucu içi
`old_value`/`new_value` verisini veya desteklenen ters işlemin kullandığı veriyi değiştirmez.

### 3.2. Zorunlu olaylar ve asgari hedef

Ana plan §8.10'daki bütün kategoriler denetlenir: giriş ve kritik oturum olayları; öğrenci;
anne/baba; sınıf ataması; yoklama; program/içerik; ilerleme/değerlendirme; kullanıcı/yetki;
kurum ayarı; rapor dışa aktarma; platform yöneticisinin kurum verisine erişimi. Bunlara ek
olarak, `YETKI_MATRISI.md` §4.5 uyarınca devredilmiş marka, modül, hoca hesabı, hoca-sınıf
ataması, başka kullanıcının cihaz oturumunu iptal etme ve kuruma özel yoklama durumu değişikliği
de denetlenir. Kuruma özel öğrenci alanı tanımı/seçeneği oluşturma, değiştirme ve arşivleme de
`ORG_SETTING_CHANGED` altında hedef türü ve maskelenmiş alan özetiyle yazılan zorunlu kurum
ayarı denetim olayıdır (`YETKI_MATRISI.md` §2.2 madde 6b).

Kritik oturum veya erişim olayının yalnızca başarılı sonucu değil, güvenlik açısından anlamlı
reddi de denetlenir: örneğin yetkisiz platform yöneticisi destek erişimi denemesi veya başka
kurumun kaydını geri alma denemesi. Bu reddedilmiş olaylarda hedef değerler kopyalanmaz;
istek kimliği, aktör, istenen eylem, hedef tür/kimlik (biliniyorsa), kurum bağlamı ve red
nedeni kaydedilir.

Kurum yaşam döngüsü audit eşlemesi bağlayıcıdır: oluşturma `ORG_CREATED`,
`SUSPEND`/`ACTIVATE`/`ARCHIVE` durum değişimleri `ORG_STATUS_CHANGED`, kimlik/marka/modül gibi
ayar değişiklikleri `ORG_SETTING_CHANGED`, platform yöneticisinin hedef kurum verisine
erişmesi ise `PLATFORM_ADMIN_ORG_ACCESS` üretir. İlk iki kod ile erişim kodu
`is_undoable=false`; yalnız `ORG_SETTING_CHANGED` bölüm 6'daki kontrollü ters işlem
sözleşmesine tabidir.

### 3.3. Görüntüleme kapsamı

| İsteyen | Görebileceği denetim kayıtları |
|---|---|
| Platform yöneticisi | Sistem geneli kayıtlar ve destek için eriştiği kurumun kayıtları. Her kurum verisi görüntüleme/işleme erişimi ayrıca `PLATFORM_ADMIN_ORG_ACCESS` olayı üretir. |
| Kurum yöneticisi | Yalnızca kendi kurumunun kayıtları; kurum içindeki sınıf kısıtı uygulanmaz. |
| Hoca | Ayrı “işlem geçmişi görüntüleme” izni, aktif kurum üyeliği ve `scope_class_id` ile işaretli sınıfa güncel ataması birlikte varsa o sınıfın operasyonel olayları. Öğrencinin daha sonra sınıf değiştirmiş olması bu tarihsel kapsamı değiştirmez. |

Hoca için kurum kapsamlı yönetim olayları (ör. kendisine verilmiş marka yönetimi izniyle yaptığı
değişiklik) yalnızca olayın aktörü kendisiyse görüntülenebilir ve geri alınabilir. Başka
kullanıcıların kurum kapsamlı yönetim olayları, kurum yöneticisi ile platform yöneticisine kendi
yetki kapsamlarında görünür ve geri alınabilir; hoca için işlem geçmişi/geri alma izni bu
olaylara toplu erişime dönüşmez. Buna karşılık, gerekli izinleri olan hoca kendi atanmış olduğu
`scope_class_id`li operasyonel olayda **başka bir hocanın işlemini de** geri alabilir. Bu kural
`YETKI_MATRISI.md` §2.2 ve §4.4'teki operasyonel veri izolasyonunu korur.

Veli iletişim bilgisi görüntülemesi bir erişim olayı olarak denetlenir. Hoca için olay kaydı
yalnızca kendi atanmış sınıfındaki öğrenciye ilişkin erişim için üretilebilir; denetim listesi
ve ayrıntı görünümü telefon/adres değerini değil erişimin yapıldığını gösterir. Kurum yöneticisi
bu olayları kendi kurumu içinde görebilir.

## 4. Geri alma yetkisi

Geri alma için üç koşul birlikte sağlanır:

1. İstekte bulunanın `geri alma` izni vardır. Bu izin, `işlem geçmişi görüntüleme` izninden
   bağımsızdır (`YETKI_MATRISI.md` §3.7 ve §4.2).
2. İstekte bulunan, hedef kaynak üzerinde bugün aynı işlemi yapmaya yetkilidir. Hoca için bu,
   audit satırındaki `scope_class_id`ye güncel sınıf ataması + ilgili işlem iznidir; kurum
   yöneticisi için kendi kurumu; platform yöneticisi için denetlenen destek bağlamıdır.
3. Kaynak eylem bölüm 6'da desteklenmektedir ve bölüm 7'deki tüm ön koşullar sağlanır.

`RESTORE_ARCHIVED`, öğrenci ve sınıf için tek ortak izindir (`VERI_MODELI.md` §4.8). Arşivden
geri yükleme komutu bu izin ile geri alma iznini birlikte gerektirmez: kullanıcının doğrudan
arşivden geri yükleme yetkisi `RESTORE_ARCHIVED` ile belirlenir. Bir **denetim kaydından geri
alma** olarak aynı işlem başlatılırsa, hem `RESTORE_ARCHIVED` hem geri alma izni doğrulanır.

Kurum ve platform yöneticisi kendi yetki kapsamındaki başka aktörlerin desteklenen olaylarını
geri alabilir. Hoca, yalnızca atanmış olduğu `scope_class_id`li operasyonel olaylarda başka
hocanın işlemini geri alabilir; kurum kapsamlı yönetim olayında yalnızca kendi olayını geri
alabilir. Orijinal aktörün kimliği her zaman hem kaynak hem de geri alma kaydında korunur.

## 5. Geri alma komut biçimleri

Kesin HTTP yolları ilgili modül API sözleşmesinde tanımlanır. Tekil ve grup geri alma, farklı
istek gövdeleri ve ön koşullar taşır; grup komutu tekil `undoOfAuditLogId` komutunun diziye
sarılmış biçimi değildir.

### 5.1. Tekil geri alma

| Alan | Kural |
|---|---|
| `undoOfAuditLogId` | Kaynak denetim kaydıdır; aynı kurum ve aynı olay kapsamında olmalıdır. |
| `clientMutationId` | Geri alma isteği için benzersizdir; yeniden gönderimde aynı sonucu döndürür. |
| `If-Match-Row-Version` | Değiştirilebilir hedefte zorunlu başlıktır; kaynak kaydın beklenen mevcut sürümünü belirtir. |
| `reasonCode` | İsteğe bağlı kontrollü nedendir; `action_type + payload_schema_version` kataloğunda izinli kodlardan biri olmalıdır. Serbest metin reason kabul edilmez. |

Başarılı komut, kaynak ve sonuç denetim kayıtlarının kimliklerini, yeni hedef sürümünü ve hedefin
yetkili kullanıcının zaten görebildiği özetini döndürür. `undo_of_audit_log_id` yalnızca doğrudan
kaynak olayı gösterir; bir geri alma kaydını tekrar geri alma veya zinciri otomatik dolaşma
desteklenmez. Geri alma sonucunu değiştirmek gerekirse, normal yetkili düzeltme komutu kullanılır
ve yeni denetim kaydı oluşur.

### 5.2. Grup geri alma

V1 “Hepsi Geldi” grup geri alma komutu aşağıdaki anlamsal gövdeyi taşır:

| Alan | Kural |
|---|---|
| `undoOfOperationGroupId` | Kaynak `ATTENDANCE_BULK_PRESENT_RECORD_CHANGED` satırlarının ortak `operation_group_id` değeridir. |
| `clientMutationId` | Grup komutu için benzersizdir; güvenli yeniden denemede aynen korunur. |
| `targets` | Her kaynak hedef için `{ auditLogId, expectedRowVersion }` nesnesinden oluşan listedir. |
| `reasonCode` | İsteğe bağlı kontrollü nedendir; grup action + payload şemasında izinli olmalıdır. |

Sunucu, `targets` listesinin kaynak `undoOfOperationGroupId` grubunun **tam kümesi** olduğunu
tek transaction içinde doğrular: listede eksik hedef, fazla hedef, tekrarlı `auditLogId` veya
başka bir operation group'a ait hedef varsa `409 GROUP_UNDO_CONFLICT` döner. Ardından her hedef
için kaynak olayın henüz geri alınmamış olması, `expectedRowVersion`, tarihsel
`scope_class_id` kapsamı, geri alma/yazma yetkisi ve aynı kurum koşulu birlikte doğrulanır.
Herhangi bir doğrulama başarısızsa hiçbir attendance kaydı, geri alma audit satırı veya yeni
operation group kalıcılaşmaz.

## 6. Desteklenen kaynak eylemler ve ters işlemler

| Kaynak eylem | `is_undoable` | Ters komut | Ters etkinin tanımı | Ek yetki |
|---|---:|---|---|---|
| Öğrenci arşivleme (`STUDENT_ARCHIVED`) | Evet | Öğrenciyi geri yükle | Aynı öğrenci kaydını aktif duruma döndürür; geçmiş üyelik, yoklama ve ilerleme silinmez. | `RESTORE_ARCHIVED` |
| Sınıf arşivleme (`CLASS_ARCHIVED`) | Evet | Sınıfı geri yükle | Aynı sınıfı aktif duruma döndürür; geçmiş kayıtlar korunur. | `RESTORE_ARCHIVED` |
| Tekil yoklama kaydı değişikliği (`ATTENDANCE_RECORD_CHANGED`) | Evet | Yoklamayı önceki değere döndür | Kaynak olaydaki eski durum alanlarını aynen uygular; yeni kayıt oluşturmaz. Katalogda `requires_class_scope=true`, `requires_operation_group=false`dır. | Yoklama alma/düzeltme; geçmiş tarih için ayrıca geçmiş yoklama düzeltme izni |
| V1 toplu “Hepsi Geldi” (`ATTENDANCE_BULK_PRESENT_RECORD_CHANGED`) | Evet | Grup yoklamasını önceki değerlere döndür | Her öğrenci hedefi için ayrı audit satırı vardır; katalogdaki `requires_class_scope=true` ve `requires_operation_group=true` nedeniyle `scope_class_id` ile `operation_group_id` DB düzeyinde zorunludur. | Yoklama alma/düzeltme; geçmiş tarih için ayrıca geçmiş yoklama düzeltme izni |
| İlerleme kaydı değişikliği (`PROGRESS_CHANGED`) | Evet | İlerlemeyi önceki değere döndür | Tamamlanma, puan, not ve tekrar gerekli alanlarını kaynak olaydaki eski değere döndürür. | İlerleme kaydetme/düzeltme |
| Anne/baba bilgisi güncellemesi (`GUARDIAN_UPDATED`) | Hayır | — | Çok varlıklı aggregate ters işlem ve tüm ön koşulları ayrıca kesinleşmeden geri alınmaz. | — |
| Program değişikliği (`PROGRAM_CHANGED`) | Hayır | — | `program_versions` satırı yerinde değiştirilmez; yeni sürüm/aktif sürüm değişimi için eksiksiz aggregate ters işlem sözleşmesi kesinleşmeden geri alınmaz. | — |
| İçerik değişikliği (`CONTENT_CHANGED`) | Evet | İçeriği önceki değere döndür | Metin ve güvenli dosya referansını kaynak olaydaki sürüme döndürür; fiziksel dosya silmez. | Program/içerik yönetimi |
| Kurum ayarı değişikliği (`ORG_SETTING_CHANGED`) | Evet | Ayarı önceki değere döndür | Ad, renk, logo referansı, etkin modül veya yoklama durumu gibi değiştirilen ayar alanını eski değere döndürür. | İlgili kurum ayarı izni |

Bir işlem “oluşturma” olarak denetlenmişse, bu sürümde genel ters işlem tanımlı değildir.
Örneğin öğrenci, sınıf veya program oluşturma kaydını geri almak için arşivleme/yönetim akışı
kullanılır ve kendi denetim kaydını üretir. Böylece yeni oluşturulmuş ilişkileri yanlışlıkla
veya fiziksel olarak silen geniş kapsamlı bir geri alma oluşmaz.

### 6.1. V1 “Hepsi Geldi” grup geri alma

“Hepsi Geldi” birden fazla hedefi değiştirse de ana plan §3.1'deki geri alınabilir toplu işlem
ilkesi ve §8.5'teki öğrenci kayıtlarının bağımsız tutulması nedeniyle V1'de özel olarak
desteklenir. Komut her öğrenci hedefi için ayrı
`ATTENDANCE_BULK_PRESENT_RECORD_CHANGED` audit olayı üretir; hepsine aynı
`operation_group_id`, `request_id`, `scope_class_id` ve payload şema sürümü yazılır. Böylece her
öğrencinin eski/yeni değeri bağımsız denetlenir ve her kaynak satırın yalnızca bir geri alma
kaydı olabilir.

Yeni yoklama oturumunda her öğrenci için teknik `UNMARKED` başlangıç durumu vardır. İlk “Hepsi
Geldi” bu durumu `PRESENT`e değiştirir; grup geri alma önceki `UNMARKED` değerine ve
`recorded_by_user_id`/`recorded_at` alanlarını `NULL`a döndürür. Satır silinmez, kurumun
seçilebilir özel durumlarına `UNMARKED` eklenmez ve kaynak/geri alma audit geçmişi korunur.

Grup geri alma, kaynak `operation_group_id` ile seçilir. Başarılı ters işlem, hedef başına yeni
`is_undo=true` audit satırı ve **yeni** bir `operation_group_id` üretir; kaynak grup kimliği
kontrollü `event_metadata` içinde taşınır. Böylece kaynak ve ters işlem grupları birbirinden
ayrılır, her hedefin `undo_of_audit_log_id` ilişkisi yine doğrudan kaynak satıra bağlanır.

Grup geri alma, gruptaki **tüm** hedeflerin kaynak olaylarının henüz geri alınmamış olmasını,
güncel `If-Match-Row-Version` ön koşullarını ve çağıranın her hedefin tarihsel
`scope_class_id` kapsamındaki yetkisini tek transactionda doğrular. Bir hedefte sürüm/değer
çatışması, zaten geri alma veya yetki sorunu varsa **hiçbir hedef geri alınmaz** ve
`409 GROUP_UNDO_CONFLICT` döner; kısmi başarı ve kısmi audit izi oluşmaz. Yanıt yalnızca
çağıranın görme yetkisi bulunan hedeflerin çatışma özetini içerir. Farklı `clientMutationId`
ile iki eşzamanlı grup geri alma denemesinde, hedef başına partial unique index ikinci denemeyi
engeller; transaction atomik olduğundan yalnızca bir grup başarıyla tamamlanabilir.

## 7. Başarı ve ret koşulları

Sunucu, kaynak denetim kaydını ve hedefin güncel durumunu tek transaction içinde kilitli/uyumlu
biçimde değerlendirir; hedef değişikliği ve yeni geri alma denetim kaydı aynı transaction içinde
kalıcılaşır. Aşağıdaki koşullardan biri sağlanmazsa hedefte hiçbir değişiklik, başarı cevabı veya
geri alma denetim kaydı oluşmaz; uygun hata cevabı ve gerekli güvenlik denetim olayı üretilir.

| Durum | Sonuç |
|---|---|
| Kayıt bulunamaz ya da istek sahibi onu görmeye yetkili değildir | Varlık gizleme kuralı uygulanır; `404 RESOURCE_NOT_FOUND` döner. Hoca için kontrol audit satırının tarihsel `scope_class_id`siyle yapılır; başka kurum/sınıf verisinin varlığı açığa çıkmaz. |
| Geri alma veya kaynak işlemin yazma yetkisi yoktur | `403 FORBIDDEN`; hedef değişmez. |
| Kaynak olay geri alınabilir değildir | `422 UNDO_NOT_SUPPORTED`; hedef değişmez. |
| Kaynak olay başka kurumda veya farklı `event_scope`dadır | Yetkisiz görünürlükte `404`; aksi halde `422 UNDO_SCOPE_MISMATCH`; hedef değişmez. |
| Kaynak olay zaten geri alınmıştır veya başka `clientMutationId` ile eşzamanlı geri alma partial unique indexe takılmıştır | `409 UNDO_ALREADY_APPLIED`; hedef değişmez. |
| Hedef yok, arşivden geri yüklenemez, veya bağımlı yaşam döngüsü kuralı geri yüklemeyi engeller | `409 UNDO_PRECONDITION_FAILED`; hedef değişmez. |
| `If-Match-Row-Version` ya da beklenen güncel değer eşleşmez | `409 VERSION_CONFLICT`; sunucu güncel yetkili özeti dönebilir, sessiz ezme yapmaz. |
| Aynı `clientMutationId` yeniden gelir | İlk başarılı ya da kesin hata sonucu döner; ikinci ters işlem uygulanmaz. |

`UNDO_ALREADY_APPLIED` kontrolü, uygulama sorgusuna ek olarak `audit_logs_one_undo_per_source_idx`
partial unique indexiyle DB düzeyinde de zorlanır. Geri alma sonrasında yapılan yeni normal
değişiklik, yeni bir kaynak denetim olayıdır ve kendi destek koşullarıyla bağımsız değerlendirilir.

## 8. Geri alınmayan olaylar

Aşağıdaki olaylar denetlenir ancak `is_undoable = false` kalır:

- Giriş, çıkış, başarısız giriş, oturum yenileme, oturum veya cihaz iptali; güvenlik olayını
  geçmişe dönük olarak tersine çevirmek güvenli değildir.
- Kullanıcı, rol, izin, hoca-sınıf ataması ve kurum yöneticisi ataması değişiklikleri; bunlar
  ayrı, güncel yetki değerlendirmesiyle yeni yönetim işlemi olarak yapılır. Özellikle iptal
  edilmiş rolün bağlı izin/atamalarının aynı transactionda kapatılması kuralı korunur.
- Platform yöneticisinin kurum erişimi; bu bir erişim izidir, verisel ters işlemi yoktur.
- Rapor dışa aktarma; üretilen raporun denetim izi silinmez, geri alma dosyayı veya kullanıcıda
  mevcut olabilecek kopyayı geri çağırmaz.
- Kurum, öğrenci, sınıf veya program oluşturma; genel oluşturma geri alması fiziksel silme ya
  da ilişki kaybı riski taşıdığından bölüm 6'daki kontrollü yönetim/arşiv akışı kullanılır.
- Kurumun askıya alınması, yeniden etkinleştirilmesi veya arşivlenmesi; bu durumlar genel audit
  geri alma yoluyla değil, `ORG-001`deki yetkili yaşam döngüsü komutları ve terminal arşiv
  kurallarıyla yönetilir.
- Değerlendirme şeması, plan şablonu dağıtımı, özel alan şeması değişikliği ve **V1 “Hepsi
  Geldi” dışındaki** toplu işlemler; bunların ters etkisi birden çok kayıt veya geçmiş anlamını
  değiştirebilir. Ayrı ürün/API sözleşmesi olmadan geri alınamaz.

## 9. Denetim ve geri alma kabul senaryoları

Uygulama görevlerinde en az aşağıdaki senaryolar otomatik testlere dönüştürülmelidir:

1. Aynı kurumda yetkili kurum yöneticisi bir yoklama değişikliğini doğru eski değerle geri
   alır; hedef ve geri alma denetim kaydı tek transactionda oluşur.
2. Aynı geri alma isteği cevap kaybından sonra aynı `clientMutationId` ile yeniden gönderilir;
   ikinci ters işlem veya ikinci denetim kaydı oluşmaz.
3. Aynı kaynak audit olayını iki farklı `clientMutationId` ile eşzamanlı geri alma girişiminden
   yalnızca biri başarılı olur; `audit_logs_one_undo_per_source_idx` diğerini
   `UNDO_ALREADY_APPLIED` ile reddeder ve ikinci yan etki/audit satırı oluşmaz.
4. Başka bir hoca kaynak hedefi değiştirmişse eski `If-Match-Row-Version` ile geri alma
   `VERSION_CONFLICT` döner ve veri ezilmez.
5. Hoca, audit satırındaki `scope_class_id`ye atanmışsa gerekli izinle başka hocanın
   operasyonel işlemini geri alabilir; öğrenci sonradan başka sınıfa geçmiş olsa da tarihsel
   kapsam değişmez. Atanmadığı sınıfın kimliğiyle denediğinde `404` alır ve veri sızmaz.
6. Hoca kurum kapsamlı yönetim olayında yalnızca kendi olayını görür/geri alır; kurum ve
   platform yöneticileri kendi yetki kapsamlarında başka aktörlerin desteklenen olaylarını
   geri alabilir.
7. A kurumu kullanıcısı B kurumunun denetim kaydını geri alamaz; kurumlar arası ilişki hem
   uygulama denetiminde hem şema kısıtında reddedilir.
8. Arşivlenmiş öğrenci ve sınıf `RESTORE_ARCHIVED` ile geri yüklenir; geçmiş yoklama/ilerleme
   korunur. Kaynaktan geri alma yolunda geri alma izni de aranır.
9. “Hepsi Geldi” her öğrenci için aynı `operation_group_id`li ayrı audit satırları üretir.
   Grup geri almada tek hedefin sürüm çakışması tüm grubu `GROUP_UNDO_CONFLICT` ile reddeder;
   hiçbir hedef ve geri alma audit satırı kalıcılaşmaz.
10. Yetkisiz kullanıcı geri alma, işlem geçmişi veya veli iletişim bilgisi erişimi dener;
   hedef değerler yanıtta ve uygulama loglarında görünmez, güvenlik olayı denetlenir.
11. `PROGRAM_CHANGED` ve çok varlıklı `GUARDIAN_UPDATED` için geri alma denemeleri
   `UNDO_NOT_SUPPORTED` döner; `program_versions` satırı yerinde değişmez.
12. Rol iptali, oturum iptali, rapor dışa aktarma ve platform erişimi için geri alma denemeleri
   `UNDO_NOT_SUPPORTED` döner; kaynak denetim izleri değişmeden kalır.
13. Platform yöneticisinin kurum verisine her görüntüleme veya işlem erişiminde ilgili kurum
   bağlamlı `PLATFORM_ADMIN_ORG_ACCESS` kaydı oluşur.
14. UUID olmayan ancak P-009'un 1–128 karakterlik izinli ASCII `X-Request-Id` desenine uyan
    `request_id` audit kaydına yazılır; geçersiz değer DB `CHECK`i ve API tarafından reddedilir.
15. `requires_class_scope=true` olan operasyonel audit olayı `scope_class_id` olmadan DB
    tarafından reddedilir; `false` olan olayda sınıf kimliği yazılamaz.
16. `ATTENDANCE_BULK_PRESENT_RECORD_CHANGED` satırı `operation_group_id` olmadan DB tarafından
    reddedilir; tekil `ATTENDANCE_RECORD_CHANGED` satırına grup kimliği yazılamaz.
17. Yeni yoklama gününde ilk “Hepsi Geldi” geri alındığında öğrenciler `UNMARKED` durumuna
    döner, `recorded_by_user_id` ile `recorded_at` `NULL` olur; attendance satırları veya audit
    geçmişi fiziksel olarak silinmez.
18. Grup geri alma `targets` listesi kaynak `undoOfOperationGroupId` kümesinden eksik, fazla,
    tekrarlı veya başka gruba ait `auditLogId` içerirse `GROUP_UNDO_CONFLICT` döner; bütün
    sürüm/yetki/kapsam denetimleri aynı transactionda yapıldığından hiçbir hedef geri alınmaz.

## 10. Kapsam dışı ve açık sınırlar

- Geri alma HTTP endpoint yolu, istek/cevap şeması ve hata kodlarının API kaynak sözleşmesine
  eklenmesi; bu belge `API_GENEL_KURALLARI.md` kurallarına uyacak anlamsal sözleşmeyi verir.
- Veritabanı migration'ı, transaction/locking mekanizması, denetim görünümü ve mobil ekran
  uygulaması sonraki Dalga görevlerindedir.
- Yedekten geri dönüş, felaket kurtarma, hukuki saklama süresi ve denetlenen kalıcı silme
  prosedürü normal uygulama geri almasından ayrıdır ve burada tasarlanmamıştır.
- V1 “Hepsi Geldi” dışındaki toplu, şablon kaynaklı veya çok varlıklı işlemler için ters işlem,
  etkiledikleri kayıtların kapsamı ayrıca sözleşmeye bağlanmadan desteklenmeyecektir.

## 11. Kaynaklarla uyum kontrolü

- Zorunlu denetim kategorileri ve “ters işlem, geçmişi silmez” ilkesi `URUN_VE_UYGULAMA_PLANI.md`
  §8.10 ile uyumludur.
- Sunucu yetkisi, varlık gizleme, idempotency, sürüm çatışması, atomiklik ve hata zarfı
  `API_GENEL_KURALLARI.md` §4, §5 ve §7 ile uyumludur.
- Geri alma ile işlem geçmişi izninin bağımsızlığı; hoca için sınıf kapsamı ve kurum yöneticisi
  sınırı `YETKI_MATRISI.md` §2.2, §3.7 ve §4.2–§4.4 ile uyumludur.
- `RESTORE_ARCHIVED` tek ortak izin, scope-pinned undo ilişkisi, denetim kaydının değişmezliği
  ve rol iptalinin atomik kapanış kuralı `VERI_MODELI.md` §4.8, §4.12, §13 ve §15.5 ile
  uyumludur.
- Toplu işlemlerin geri alınabilir olması `URUN_VE_UYGULAMA_PLANI.md` §3.1; her öğrenci
  yoklama kaydının bağımsız saklanması ise §8.5 ile uyumludur. V1 istisnası yalnızca “Hepsi
  Geldi”dir; diğer toplu işlemler sonraki sözleşmeye bırakılmıştır.
- Hassas denetim değerlerinin gereksiz görünmemesi `KISISEL_VERI_ENVANTERI.md` satır 7,
  12–16 ile uyumludur; bu belge hukuki saklama veya KVKK sınıflandırması iddiasında bulunmaz.
