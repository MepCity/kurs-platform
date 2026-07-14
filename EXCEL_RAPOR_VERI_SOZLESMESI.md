# Excel Rapor Veri Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | P-012 — Excel rapor veri sözleşmesini tanımla |
| Belge sürümü | 1.3 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Bağımlı sözleşmeler | `VERI_MODELI.md`, `YETKI_MATRISI.md`, `KISISEL_VERI_ENVANTERI.md`, `API_GENEL_KURALLARI.md` |
| Modül | EXPORT |
| Son güncelleme | 14 Temmuz 2026 |

---

## Revizyon notu (v1.0 → v1.1)

- P-011 ile kesinleşen teknik `UNMARKED` yoklama durumu rapor sonuçlarından ve sayımlarından
  açıkça çıkarıldı.
- Veli iletişim yetkisi rol kapsamına göre ayrıştırıldı; üretim öncesi yeniden yetki denetimi,
  tutarlı snapshot, Excel formül enjeksiyonu ve boyut sınırları bağlayıcı hâle getirildi.
- Hücre tipleri, telefon biçimi, öğrenci/sınıf satır semantiği ve birden çok özel yoklama
  durumunun deterministik özeti netleştirildi.

---

## Revizyon notu (v1.1 → v1.2)

- `sourceCutoffAt`ın snapshot edinimiyle aynı işlem sınırında atomik alınması ve `completedAt`tan
  ayrılması kesinleştirildi.
- Öğrenci listesi için tarih/dönem/snapshot etkin kapsamı, sınıf-öğrenci filtre doğrulaması ve
  dönem içi sınıf transferi kuralları bağlayıcı hâle getirildi.
- Çalışma kitabı içi takma `Öğrenci Rapor No` eklendi; boolean görünümü kontrollü `Evet`/`Hayır`
  metnine çevrildi.

---

## 1. Amaç ve kapsam

Bu belge, V1'de sunucuda üretilen Excel (`.xlsx`) raporlarının istek, veri kapsamı, çalışma kitabı
ve güvenli teslim sözleşmesini tanımlar. Sözleşme, seçilen kurum, sınıf ve tarih aralığı için
öğrenci listesi, veli iletişim bilgileri, yoklama, program/ilerleme ve dönem özeti üretimini
kapsar.

Bu sözleşme Excel üretim kütüphanesini, nesne depolama sağlayıcısını, kuyruk teknolojisini veya
sayısal indirme bağlantısı süresini seçmez. Bunlar sırasıyla `A-008`, `A-007` ve ilgili ortam
kararlarında belirlenecektir. Mobil istemci çalışma kitabı üretmez; yalnızca API üzerinden istek
oluşturur, durumunu gösterir ve sunucunun verdiği güvenli indirme bağlantısını kullanır.

Standart Excel çıktısına hoca işlem geçmişi/denetim geçmişi eklenmez. Denetim geçmişi uygulama
içinde ayrı bir yetkili görünüm olarak kalır.

## 2. Bağlayıcı ilkeler

- Her rapor tam olarak bir `organizationId` bağlamında üretilir. İstemci bu kimliği gövdede
  göndermez; sunucu onu kurum kapsamlı token'dan çıkarır.
- İstekteki sınıf, öğrenci, dönem, yoklama, plan ve ilerleme kayıtları aynı kurumda değilse
  hiçbir veri döndürülmez; kapsam dışındaki doğrudan kimlikler `404 RESOURCE_NOT_FOUND` ile
  gizlenir.
- Raporun bütün sayfaları tek bir tutarlı veritabanı snapshot'ından veya tek işlem içinde bu
  snapshot'tan materyalize edilmiş aynı mantıksal snapshot'tan üretilir. `sourceCutoffAt`,
  snapshot edinilirken **aynı işlem sınırında** sunucu/veritabanı zamanından atomik alınır ve
  `export_jobs.source_cutoff_at`a yazılır; tek başına bir zaman damgası filtresi farklı
  sorguların tutarlı olduğu garantisi değildir. Uzun materyalizasyon veya dosya üretiminin
  sonundaki zaman `sourceCutoffAt` olamaz; bu ayrı `completedAt` değeridir. Snapshot sonrasında
  gerçekleşen yazmalar aynı rapora karışmaz.
- `sessionDate`, `plannedDate`, dönem başlangıç/bitiş tarihleri ve istek tarih aralığı kurumun
  `defaultTimezone` bağlamındaki salt tarihlerdir; cihaz saat dilimi bunları dönüştüremez.
- Tarih aralığı kapalıdır: `dateFrom` ve `dateTo` dahil edilir. `dateFrom > dateTo` geçersizdir.
- Normal arşivleme geçmiş rapor bağlantılarını korur. Rapor, aksi özellikle belirtilmedikçe
  kapsam tarihindeki ilgili kayıtları içerir; güncel `ARCHIVED` durumu geçmiş yoklama veya
  ilerleme satırını sessizce düşürmez.
- Dosya, hata yanıtı, URL veya çalışma kitabı adı başka bir kurumun adı, kimliği ya da verisini
  açığa çıkaramaz. Hassas alanlar uygulama loglarına ve denetim kaydına ham değer olarak
  yazılmaz.
- Rapor oluşturma ve indirme başarılı sayılmadan önce istemci dosyayı hazır kabul edemez.
- Kullanıcı kontrollü hiçbir metin Excel formülü olarak değerlendirilmez; hücre ve sınır kuralları
  bölüm 6.1'de tanımlıdır.

## 3. Yetki ve kapsam

Rapor isteği için sunucu; kimlik doğrulama, etkin kurum üyeliği, `REPORT_EXPORT` işlemi ve aşağıdaki
veri kapsamını birlikte doğrular. Arayüzdeki görünürlük bu denetimlerin yerine geçmez.

| Aktör | Yetki ve kapsam |
|---|---|
| Platform yöneticisi | Açık hedef kurum bağlamında rapor üretebilir; kurum erişimi ve rapor dışa aktarımı denetlenir. Kurumlar arası tek bir çalışma kitabı V1 kapsamı dışındadır. |
| Kurum yöneticisi | Yalnızca kendi etkin kurumunun raporlarını üretebilir. |
| Hoca | Varsayılan olarak rapor üretemez. Etkin `REPORT_EXPORT` izni verilmişse yalnızca kendisine etkin atanmış sınıfların operasyonel verisini içeren rapor üretebilir. |

`guardianContacts=true` için rol kapsamı aşağıdaki gibidir: kurum yöneticisi kendi etkin
kurumunda, platform yöneticisi açık destek bağlamındaki hedef kurumda veli iletişim bilgisini
dışa aktarabilir; bu iki rol için ayrıca `GUARDIAN_CONTACT_VIEW` aranmaz. Hoca için ise etkin
`REPORT_EXPORT` yanında ayrı ve etkin `GUARDIAN_CONTACT_VIEW` zorunludur. Hoca bu ek izinle
yalnızca etkin sınıf atamasındaki öğrencilerin velilerini alabilir; sınıf ataması sınırı
genişlemez. Şart sağlanmıyorsa istek `403 FORBIDDEN` ile reddedilir; sütunlar sessizce
boşaltılarak başarı dönülmez.

`REPORT_EXPORT`, denetim kaydını görüntüleme veya geri alma izinlerini içermez; bu üçü bağımsız
izinlerdir.

## 4. Rapor istek sözleşmesi

### 4.1. Oluşturma ucu

`POST /api/v1/export-jobs/excel`

Bu bir yazma komutudur; `API_GENEL_KURALLARI.md` uyarınca `Idempotency-Key` ve isteğe bağlı
`X-Request-Id` taşır. Aynı kullanıcı, kurum ve anahtarla eşdeğer istek yeniden gönderilirse aynı
işin eşdeğer sonucu döner; farklı gövdeyle tekrar kullanım `409 IDEMPOTENCY_KEY_REUSED` döndürür.

Başarılı kabul yanıtı `202 Accepted` olur:

```json
{
  "id": "7e7b5775-4e1b-49ee-ba5e-249c97e6df76",
  "status": "QUEUED",
  "createdAt": "2026-07-14T10:15:00Z"
}
```

`202`, dosyanın hazır olduğu anlamına gelmez. İstemci durum ucunu sorgular; bildirim/gerçek zaman
kanalı varsa bu yalnızca yardımcı güncellemedir, kesin durum kaynağı değildir.

### 4.2. İstek gövdesi

```json
{
  "scope": {
    "termId": "7a5eaf32-7fe3-45e9-b6e5-3b61e40c54ba",
    "classIds": ["5b1c7f73-3415-4c21-8c7d-2e14f3f1ef77"],
    "studentIds": ["c09cbd89-47a3-4b9d-8b7e-3c6e07a56d3e"],
    "dateFrom": "2026-07-01",
    "dateTo": "2026-07-14"
  },
  "sections": {
    "studentList": true,
    "guardianContacts": false,
    "attendance": true,
    "progress": true,
    "termSummary": true
  }
}
```

| Alan | Zorunluluk | Kural |
|---|---|---|
| `scope.termId` | Hayır | UUID. Verilirse bütün sınıf/öğrenci satırları bu döneme ait olmalıdır. |
| `scope.classIds` | Hayır | Tekrarsız UUID dizisi. Boş dizi filtre yoktur; tanımsız sınıf veya erişim dışı sınıf `404` döndürür. |
| `scope.studentIds` | Hayır | Tekrarsız UUID dizisi. Her öğrenci, aşağıdaki etkin üyelik kapsamında ve verilmişse seçili sınıflarda olmalıdır; aksi `422 BUSINESS_RULE_VIOLATION` döndürür. |
| `scope.dateFrom`, `scope.dateTo` | Birlikte | İkisi de `YYYY-MM-DD` olmalıdır. Biri eksikse `422 VALIDATION_FAILED`. İkisi birlikte yoksa tarih tabanlı bölümler için dönem sınırı kullanılır, dönem de seçilmemişse istek reddedilir. |
| `sections.studentList` | Evet | Öğrenci listesi çalışma sayfasını üretir. |
| `sections.guardianContacts` | Evet | Veli iletişim sütunlarını öğrenci listesine ekler; ek yetki gerektirir. Tek başına `true` olamaz; `studentList=true` zorunludur. |
| `sections.attendance` | Evet | Yoklama çalışma sayfasını üretir. |
| `sections.progress` | Evet | Program ve ilerleme çalışma sayfasını üretir. |
| `sections.termSummary` | Evet | Dönem özeti çalışma sayfasını üretir; `termId` zorunludur. |

En az bir bölüm `true` olmalıdır. `termId` olmadan `termSummary=true` veya tarih aralığı olmadan
`attendance=true` / `progress=true` istenemez. Sunucu, istemcinin gönderdiği sınıf/öğrenci
kimliklerini yetki kapsamını genişleten filtre olarak kabul etmez.

Birden fazla sınıf seçilebilir; seçilen sınıflar aynı kurumda olmalıdır. `classIds` verilmezse
kurum yöneticisi kendi kurumundaki, yetkili hoca ise yalnızca etkin atamasındaki sınıflar içinde
rapor alır.

`studentList` için etkin üyelik kapsamı, aynı biçimde `classIds`/`studentIds` doğrulamasına da
uygulanır:

1. `dateFrom`/`dateTo` varsa kapalı tarih aralığı kullanılır; bir üyelik bu aralıkla kesişiyorsa
   kapsamdadır.
2. Tarih aralığı yok, `termId` varsa seçili dönemin kapalı başlangıç/bitiş tarih aralığı kullanılır.
3. İkisi de yoksa yalnız `studentList=true` olan raporda `sourceCutoffAt` anında etkin olan
   üyelikler kullanılır: `started_at <= sourceCutoffAt` ve `ended_at` boş veya
   `ended_at > sourceCutoffAt` olmalıdır. Bütün üyelik geçmişi üretilmez.

Seçili bir `studentId`, bu etkin kapsamda en az bir üyeliğe sahip değilse reddedilir. `classIds`
verilmişse her seçili öğrenci, aynı etkin kapsamda bu sınıflardan en az birine bağlı olmalıdır;
aksi hâlde istek reddedilir. Sınıf filtresi öğrenci bulunmayan geçerli bir sınıfı tek başına
geçersiz kılmaz; sonuç ilgili bölümde boş olabilir. `studentList` dışındaki bölümler kendi
tarih/dönem ön koşullarını korur; bu snapshot-as-of varsayılanı onları genişletmez.

### 4.3. Durum ve indirme uçları

`GET /api/v1/export-jobs/{exportJobId}` iş sahibine veya aynı kurumda `REPORT_EXPORT` yetkisi
olan kurumsal yetkiliye iş metaverisini döndürür. Hoca yalnızca kendi oluşturduğu işi görebilir;
başka hocanın rapor metaverisine erişemez. Kapsam dışı iş kimliği `404 RESOURCE_NOT_FOUND` olur.

```json
{
  "id": "7e7b5775-4e1b-49ee-ba5e-249c97e6df76",
  "status": "READY",
  "sourceCutoffAt": "2026-07-14T10:15:00Z",
  "expiresAt": "2026-07-14T10:30:00Z",
  "file": {
    "filename": "kurs-raporu-2026-07-01_2026-07-14.xlsx",
    "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "sizeBytes": 18432
  },
  "createdAt": "2026-07-14T10:15:00Z",
  "completedAt": "2026-07-14T10:15:04Z"
}
```

`POST /api/v1/export-jobs/{exportJobId}/download-link` yalnızca `READY` iş için, isteği yapanın
yetkisini yeniden kontrol ederek tek amaçlı, HTTPS süreli indirme bağlantısı üretir. Yanıt `url`
ve `expiresAt` içerir. Bağlantı veya iş süresi sona ermişse `410 EXPORT_EXPIRED`; iş hazır değilse
`409 STATE_CONFLICT`; iş başarısızsa güvenli `failureCode` ile `409 STATE_CONFLICT` döner.
Bağlantı, dosyanın kalıcı depolama anahtarını veya başka tenant bilgisini açığa çıkarmaz.

## 5. İş yaşam döngüsü, saklama ve denetim

`export_jobs` EXPORT modülünün kurum kapsamlı işlem kaydıdır; `file_assets` değildir. İçerik,
logo ve kişi fotoğrafı için tasarlanan `file_assets` tablosu üretilen hassas rapor dosyalarının
yaşam döngüsünü temsil etmek için kullanılmaz.

| Alan | Açıklama |
|---|---|
| `id`, `organization_id` | UUID kimlik ve zorunlu kurum kapsamı; kurum bütünlüğü `VERI_MODELI.md` §15 desenine uyar. |
| `requested_by_user_id`, `requested_at` | İşi isteyen kullanıcı ve sunucu zamanı. |
| `request_snapshot` | Doğrulanmış filtreler, bölümler ve kurum saat diliminin JSON anlık görüntüsü; ham hassas veri içermez. |
| `source_cutoff_at` | Tutarlı DB snapshot'ı edinilirken aynı işlemde atomik alınan sunucu/DB zamanı; kaynak okuma üst sınırı ve raporun veri anı. |
| `status` | `QUEUED`, `RUNNING`, `READY`, `FAILED`, `EXPIRED`. |
| `artifact_storage_key`, `filename`, `content_type`, `size_bytes`, `checksum` | Yalnızca `READY` durumda dolu olan sunucu içi dosya metaverisi. Depolama anahtarı API ile dönmez. |
| `expires_at`, `purged_at` | Erişimin bittiği ve artefaktın silindiği zaman; sayısal süre ayrı kararda belirlenir. |
| `failure_code` | Başarısızlıkta güvenli makine kodu; hata yığını veya kaynak veri içermez. |

Durum geçişleri yalnızca `QUEUED → RUNNING → READY|FAILED` ve `READY|FAILED → EXPIRED` olur.
Süresi dolmuş veya iptal edilmiş artefakt yeniden indirilemez; aynı rapor gerekirse yeni bir iş
oluşturulur. İş kaydı denetim/operasyon amacıyla saklanabilir, ancak dosya artefaktı erişim süresi
sonunda silinir. Kesin saklama ve bağlantı süreleri bu belgeyle belirlenmez.

İş `RUNNING` durumuna alınmadan ve kaynak snapshot'ı oluşturulmadan **hemen önce** sunucu;
isteyenin güncel etkin üyeliğini, geri alınmamış rolünü, `REPORT_EXPORT` iznini,
`guardianContacts=true` ise bu belgenin bölüm 3'teki güncel rol/`GUARDIAN_CONTACT_VIEW`
şartını ve hoca için güncel etkin sınıf atamalarını tekrar doğrular. İlk kabulden sonra bu
yetkilerden biri iptal edilmişse iş `FAILED` olur, güvenli `EXPORT_AUTHORIZATION_REVOKED`
`failureCode`u yazılır, snapshot ve artefakt oluşturulmaz. Bu denetim snapshot kapsamını
belirleyen aynı işlem sınırında yapılır. İndirme bağlantısı üretilirken yapılan yeniden yetki
denetimi ayrıca zorunludur; önceki `READY` sonucu erişim hakkını dondurmaz.

Sunucu, snapshot/materyalizasyon sırasında başlık satırı dahil çalışma kitabı sayfa başına en fazla
`1_048_576` satır (XLSX fiziksel sınırı) olmasını ve yapılandırılmış uygulama kapsam, toplam satır, hücre
ve dosya boyutu limitlerini hesaplar. Kesin operasyonel değerler ayrı kararda belirlenir; ancak
her limit aşımında iş dosya veya kısmi artefakt oluşturmadan `FAILED` ve güvenli
`EXPORT_TOO_LARGE` `failureCode`u ile biter. Satır/sütun/hücreler sessizce kırpılamaz veya
başka sayfaya bölünemez; çok sayfalı/bölünmüş rapor ayrı sözleşme gerektirir.

Dosya üretimi başarıyla tamamlandığında `REPORT_EXPORTED` denetim olayı üretilir. Olay; kurum,
aktör, iş kimliği, bölümler, filtre özeti, `sourceCutoffAt`, sonuç durumu ve istek
kimliğini taşır; öğrenci/veli telefonları, yoklama durumları, öğretmen notları, indirme URL'si,
depolama anahtarı ve çalışma kitabı satırları denetim değerine yazılmaz. Platform yöneticisinin
hedef kurum bağlamındaki erişimi ayrıca `PLATFORM_ADMIN_ORG_ACCESS` olayı üretir.

## 6. Çalışma kitabı sözleşmesi

Dosya `.xlsx`, UTF-8 metin değerleri ve Türkçe görünür başlıklarla üretilir. Çalışma kitabında
bir `Rapor Bilgisi` sayfası ve her seçili bölüm için aşağıdaki sayfalar bulunur. Sayfa adları
Excel sınırlarına uygun, sabittir; kullanıcı girdisinden üretilmez.

### 6.1. Hücre tipi ve formül güvenliği

| Değer türü | XLSX hücre temsili |
|---|---|
| Metin | Literal `string`; kullanıcı kontrollü metin asla formül hücresi değildir. |
| Telefon | Literal `string` ve metin hücre biçimi; baştaki sıfırlar korunur, bilimsel/sayısal gösterime dönüşmez. |
| Salt tarih | `date` hücresi, görünür biçim `YYYY-MM-DD`; kurum saat dilimi bağlamındaki tarih dönüştürülmez. |
| Tarih-saat | UTC anını temsil eden `datetime` hücresi, görünür biçim `YYYY-MM-DD HH:mm:ss 'UTC'`. |
| Sayı | `number`; sayım ve puan değerleri metin/formül değildir. |
| Boolean | Kullanıcı odaklı raporda kontrollü literal `string`: kesin görünür değer `Evet` veya `Hayır`; XLSX `boolean` hücresi ve stil bağımlılığı kullanılmaz. |

Ad/soyad, telefon, sınıf/program adı, içerik başlığı, veli adı, kurum veya özel yoklama durumu
etiketi gibi kullanıcı kontrollü her metin literal `string` hücre olarak yazılır. Değer `=`,
`+`, `-` veya `@` ile başlasa bile üretici bunu formül olarak yazmaz; bu önekle başlayan değer,
Excel'in formül yorumunu engelleyecek biçimde metin olarak kaçışlanır ve görünen metin değeri
korunur. Kullanıcı girdisinden URL, hücre formülü, hücre başvurusu, sayfa adı veya dosya adı
üretilmez.

### 6.2. `Öğrenci Rapor No`

`Öğrenci Rapor No`, çalışma kitabındaki her benzersiz öğrenciye yalnız bu rapor için verilen
deterministik pozitif tamsayıdır. Numaralar snapshot içindeki öğrenciler `Soyad`, `Ad`, ardından
teknik kaynak `student.id` son bağlayıcısıyla sıralanarak `1`den başlar. Aynı ad/soyadda iki
öğrenci varsa son bağlayıcı sayesinde ayrı numara alırlar; teknik UUID hücrede gösterilmez.

Bu değer; `Öğrenciler`, `Yoklama`, `İlerleme`, `Dönem Özeti` ve `Özel Yoklama Durumları`
sayfalarında aynı öğrenci için aynıdır. Sınıf değiştiren öğrencinin farklı üyelik satırlarında
da değişmez. Değer kurumlar veya raporlar arasında kalıcı değildir, fakat bu çalışma kitabı
içinde kişisel veriyi ilişkilendiren rapor-yerel takma kimliktir; log/denetim kaydına öğrenci
eşleme listesi olarak yazılmaz.

### 6.3. `Rapor Bilgisi`

| Alan | Değer |
|---|---|
| Kurum | Yetkili kurum adı |
| Rapor kapsamı | Seçili dönem, sınıf(lar), öğrenci filtresi ve kapalı tarih aralığı |
| Kurum saat dilimi | `organizations.default_timezone` |
| Kaynak kesim anı | `sourceCutoffAt` (RFC 3339 UTC) |
| Üretim tamamlanma anı | `completedAt` — dosya üretiminin bittiği ayrı sunucu zamanı (RFC 3339 UTC) |
| Dahil edilen bölümler | İstek içindeki etkin bölümler |
| Veri notu | Arşivli güncel kayıtların tarih kapsamındaki geçmiş satırları korunur. |

Bu sayfa telefon, adres, doğum tarihi, not veya indirme bağlantısı içermez.

### 6.4. `Öğrenciler`

Satır birimi **öğrenci-sınıf üyeliği kesişimidir**. `Öğrenci Rapor No` bölüm 6.2'deki ortak
rapor-yerel kimliktir. `Üyelik Satır No`, yalnız bu sayfada `Öğrenci Rapor No`,
`student_class_enrollments.started_at`, sonra kaynak `id` sırasıyla `1`den başlayan ayrı satır
sıra numarasıdır; teknik UUID veya kurum içi kalıcı tanımlayıcı dışa aktarılmaz.

Varsayılan sütunlar: `Öğrenci Rapor No`, `Üyelik Satır No`, `Ad`, `Soyad`, `Kayıt Tarihi`,
`Öğrenci Durumu`, `Dönem`, `Sınıf`, `Sınıfa Başlama`, `Sınıftan Ayrılma`, `Üyelik Durumu`. Bir üyelik bölüm 4.2'deki etkin kapsamla
kesişiyorsa bir satır üretilir; öğrenci aralıkta sınıf değiştirmişse her kesişen
`student_class_enrollments` satırı için ayrı satır üretilir. `Aktif Sınıf` ifadesi kullanılmaz:
aralık sonundaki durum değil, üyeliğin gerçek tarih aralığı gösterilir. Tarih/dönem yoksa yalnız
`sourceCutoffAt` anındaki etkin üyelik satırı görünür; bütün üyelik geçmişi görünmez. Arşivli
öğrenci/sınıf üyeliği de tarih veya dönem kesişimi varsa aynı kuralla görünür.

`Üyelik Durumu` depolanan bir sütun değildir; `sourceCutoffAt` anına göre deterministik türetilir.
`started_at <= sourceCutoffAt` ve (`ended_at IS NULL` veya `ended_at > sourceCutoffAt`) ise
`Aktif`, aksi halde `Sona Erdi` yazılır. Bu görünür değer, rapor tarih aralığına geçmiş üyelik
satırının dahil edilip edilmemesi kararını değiştirmez.

`guardianContacts=true` ise buna `Anne Ad Soyad`, `Anne Telefon`, `Baba Ad Soyad`, `Baba Telefon`
sütunları eklenir. Veli ilişkisi olmayan öğrenci için ilgili hücreler boş kalır; telefon
uydurulmaz. Kalıcı kurum içi tanımlayıcı kullanılacak olursa bunun takma adlı kişisel veri olduğu
kabul edilir ve ayrı bir sözleşme olmadan bu sayfaya eklenmez.

### 6.5. `Yoklama`

Satır birimi öğrenci-yoklama kaydıdır. Sütunlar: `Öğrenci Rapor No`, `Tarih`, `Dönem`, `Sınıf`, `Öğrenci`,
`Durum`, `Kuruma Özel Durum`, `Kaydedilme Zamanı`. `PRESENT` ve `ABSENT` Türkçe görünür
değerlerle; `CUSTOM` ise kurumun etkin/önceki durum etiketinden üretilir. Boş yoklama kaydı
“Gelmedi” olarak yorumlanmaz ve satır üretilmez.

`attendance_records.status_type=UNMARKED` teknik başlangıç satırı hiçbir zaman sonuç satırı
üretmez; `Geldi`, `Gelmedi` veya kuruma özel durum olarak gösterilmez. Yalnızca seçili tarih
aralığındaki `attendance_sessions.session_date`, yetkili öğrenciler ve `PRESENT`, `ABSENT` ya da
`CUSTOM` durumları dahil edilir. Sıra `Tarih`, `Sınıf`, öğrenci soyadı/adı ve
`attendance_records.id`dir.

### 6.6. `İlerleme`

Satır birimi öğrenci-plan-ilerleme kaydıdır. Sütunlar: `Öğrenci Rapor No`, `Plan Tarihi`, `Dönem`, `Sınıf`,
`Program`, `Program Sürümü`, `Plan Sırası`, `İçerik/Başlık`, `Öğrenci`, `Tamamlandı`, `Puan`,
`Tekrar Gerekli`, `Kaydedilme Zamanı`. Puan ve tekrar gerekli yalnızca ilgili program sürümünde
etkinse doldurulur. `Öğretmen Notu` standart Excel raporuna dahil edilmez;
normal notu görme yetkisi, toplu dışa aktarma için ayrıca veri minimizasyonu istisnası yaratmaz.

`İçerik/Başlık` şu sırayla türetilir: şablondan gelen plan kaleminde boş olmayan
`program_template_days.title`; aksi halde bağlı `contents.body_text`. İkisi de yoksa hücre boş
kalır. Metin bölüm 5.2'deki formül enjeksiyonu temizliğinden ve XLSX hücre uzunluğu sınırından
geçer; PDF dosya adı veya indirme bağlantısı bu sütuna yazılmaz.

Bu sayfa yalnızca seçili tarih aralığındaki `plan_items.planned_date` ve seçili kapsamda geçerli
öğrencilerin `progress_records` satırlarını içerir. Sıra `Plan Tarihi`, `Sınıf`, `Program`,
`Plan Sırası`, öğrenci soyadı/adı ve `progress_records.id`dir.

### 6.7. `Dönem Özeti`

Satır birimi **öğrenci-dönem-sınıf üyeliği kesişimidir**. Dönem içinde sınıf değiştiren öğrenci
her sınıf için ayrı satır alır; öğrencinin farklı sınıflardaki toplamları birbirine eklenmez.
Ana sayfanın sütunları: `Öğrenci Rapor No`, `Dönem`, `Sınıf`, `Üyelik Başlangıcı`, `Üyelik
Bitişi`, `Öğrenci`, `Toplam Yoklama Kaydı`, `Geldi`, `Gelmedi`, `Planlanan Kalem`, `İlerleme
Kaydı`, `Tamamlanan`, `Tekrar Gerekli`.

Her satırda yoklama sayıları yalnız o üyeliğin sınıfında ve seçili dönem ile varsa istek tarih
aralığının kesişiminde kalan `attendance_sessions.session_date` için hesaplanır. Plan ve ilerleme
sayıları da yalnız aynı sınıfa program zinciriyle bağlı, aynı tarih kesişimindeki
`plan_items.planned_date` ve ilgili öğrencinin kayıtları üzerinden hesaplanır. Bu kesişim dışında
veya öğrencinin bu sınıf üyeliği dönemi dışında kalan satırlar o özete dahil edilmez.

Birden çok kuruma özel durum tek, belirsiz bir hücrede toplanmaz. `Özel Yoklama Durumları` adlı
zorunlu normalize sayfada her satır `Öğrenci Rapor No`, `Dönem`, `Sınıf`, `Üyelik Başlangıcı`,
`Üyelik Bitişi`, `Öğrenci`, `Durum Kodu`, `Durum Etiketi`, `Sayı` sütunlarını taşır; yalnızca
`CUSTOM` kaydı olan satırlar bulunur ve sıra `Durum Kodu`, ardından `Öğrenci Rapor No` ve üyelik
başlangıcıdır. `Durum Kodu`, `organization_attendance_statuses.code` değeridir; etiket değişse
dahi makinece okunabilirlik korunur.

`UNMARKED` satırları ne ana özetin `Toplam Yoklama Kaydı`/`Geldi`/`Gelmedi` sayılarına ne de
normalize özel durum sayfasına katılır. Yüzde, ortalama puan veya “başarı” yorumu V1
sözleşmesinde yer almaz; bu tür hesapların anlamı kurum ve program düzenine göre ayrı bir karar
gerektirir.

Özet, seçilen döneme bağlıdır. Yoklama sayıları dönem ve varsa tarih aralığı kesişiminden;
plan/ilerleme sayıları `planned_date` üzerinden aynı kesişimden hesaplanır. Sıfır değerleri
gerçek `0` olarak yazılır; veri yokluğu boş satır veya “başarısız” çıkarımı değildir.

## 7. Boş, yükleniyor ve hata durumları

- Kapsam geçerli fakat seçilen bölüm için kaynak satır yoksa iş `READY` olur; ilgili sayfa
  başlıklarıyla birlikte boş veri satırı içerir ve `Rapor Bilgisi` sayfasında “Kayıt bulunamadı”
  notu yer alır. Bu bir hata değildir.
- İş `QUEUED` veya `RUNNING` iken dosya ve indirme URL'si dönmez; istemci açık yükleniyor/durum
  göstergesi sunar.
- Yetki, kurum kapsamı, istek biçimi veya iş kuralı hataları ortak API hata zarfını kullanır.
  İşçi üretim hatası `FAILED` durumuna güvenli `failureCode` ile yazılır; dosya artefaktı
  oluşturulmaz ve istemci başarısız işi başarılı göstermez.
- İşçi öncesi yeniden yetki denetiminde iptal saptanırsa `EXPORT_AUTHORIZATION_REVOKED`; kapsam,
  satır, hücre veya dosya boyutu sınırı aşılırsa `EXPORT_TOO_LARGE` kullanılır. Bu iki durumda
  kısmi çalışma kitabı veya indirilebilir artefakt oluşmaz.
- Süresi dolan dosyada istemci eski bağlantıyı yeniden kullanamaz; yeni iş oluşturmalıdır.

## 8. Zorunlu kabul senaryoları

1. Aynı kurumda seçili sınıf ve kapalı tarih aralığı için üretilen `Yoklama`, `İlerleme` ve
   `Dönem Özeti` satırları kaynak tablolardaki kapsamla uyuşur.
2. Başka kuruma ait sınıf, öğrenci veya iş kimliği ne veri ne de varlık bilgisi sızdırır;
   `404 RESOURCE_NOT_FOUND` döner.
3. Varsayılan hoca rapor oluşturamaz; `REPORT_EXPORT` verilmiş hoca yalnızca etkin atandığı
   sınıfların verisini alabilir.
4. Kurum yöneticisi kendi kurumunda ve platform yöneticisi açık destek bağlamında veli iletişim
   sütunlarını alır. Hoca, `REPORT_EXPORT` yanında etkin `GUARDIAN_CONTACT_VIEW` olmadan
   `guardianContacts=true` isteğiyle `403 FORBIDDEN` alır; iki izni de varsa yalnızca etkin
   atamasındaki sınıfın velileri yer alır.
5. Aynı `Idempotency-Key` ve eşdeğer istek ikinci iş ya da ikinci `REPORT_EXPORTED` olayı
   üretmez; farklı istek özetiyle tekrar `409 IDEMPOTENCY_KEY_REUSED` döner.
6. `202` veya `QUEUED`/`RUNNING` durumundaki iş için indirme bağlantısı verilmez; yalnızca
   `READY` işte süreli HTTPS bağlantısı üretilir.
7. Süresi dolan iş indirilemez, dosya artefaktı silinir ve durum `EXPIRED` olur; yeni rapor
   isteği eski işi yeniden canlandırmaz.
8. Arşivlenmiş öğrenci/sınıfın tarih kapsamındaki yoklama ve ilerleme geçmişi korunur; güncel
   arşiv durumu geçmiş satırı sessizce dışlamaz.
9. `sourceCutoffAt`, tutarlı snapshot edinilirken aynı işlemde atomik alınır; snapshot sonrası
   yazılan yoklama veya ilerleme kaydı dosyaya girmez. Uzun materyalizasyon/üretim sonunda
   yazılan ayrı `completedAt`, `sourceCutoffAt`ın yerini alamaz; bütün sayfalar aynı snapshot'a
   dayanır.
10. Rapor denetim kaydı filtre özeti ve sonuç durumunu taşır; telefon, öğretmen notu, yoklama
    satırı, indirme URL'si veya depolama anahtarı taşımaz. Platform yöneticisi erişimi ayrıca
    denetlenir.
11. `UNMARKED` yoklama satırı `Yoklama` sayfasında sonuç satırı üretmez; `Dönem Özeti`ndeki
    toplam, geldi, gelmedi veya özel durum sayılarına katılmaz.
12. İş `RUNNING`e alınmadan önce üyelik, rol, `REPORT_EXPORT`, gerekli veli izni ve hoca sınıf
    ataması tekrar doğrulanır. Bu haklardan biri iptal edilmişse iş `FAILED` /
    `EXPORT_AUTHORIZATION_REVOKED` olur, snapshot ve artefakt oluşmaz; indirme anındaki tekrar
    denetim de yetkisiz erişimi engeller.
13. Kullanıcı kontrollü `=`, `+`, `-` veya `@` ile başlayan ad, sınıf/program adı, içerik başlığı
    veya özel durum etiketi literal metin hücresi olarak kalır; formül, URL veya hücre başvurusu
    çalıştırmaz.
14. Bir sayfanın `1_048_576` XLSX satır sınırını ya da yapılandırılmış kapsam/satır/hücre/dosya
    limitini aşan istek `FAILED` / `EXPORT_TOO_LARGE` olur; dosya üretilmez ve satır kırpılmaz.
15. Telefon hücreleri baştaki sıfırları koruyan metindir; tarih, tarih-saat ve sayı hücreleri
    bölüm 6.1'deki kararlı tür/biçimle, kullanıcıya gösterilen boolean değerler ise kontrollü
    literal `Evet`/`Hayır` metniyle yazılır.
16. `studentList`te tarih aralığı varsa kapalı aralık, yalnız `termId` varsa dönem aralığı,
    ikisi de yoksa yalnız `sourceCutoffAt` anındaki etkin üyelikler kullanılır; aynı etkin
    kapsam `classIds`/`studentIds` doğrulamasında uygulanır.
17. Tarih aralığında sınıf değiştiren öğrenci, `Öğrenciler` sayfasında her kesişen üyelik için
    ayrı, tarihli satıra sahiptir; teknik UUID veya kalıcı takma adlı tanımlayıcı dışa çıkmaz.
18. Dönem içinde sınıf değiştiren öğrencinin `Dönem Özeti`nde her sınıf/üyelik kesişimi için
    ayrı satırı vardır; yoklama, plan ve ilerleme sayıları yalnız o satırın sınıf ve tarih
    kesişiminden gelir, başka sınıftaki toplamlarla karışmaz.
19. Aynı ad/soyadda iki farklı öğrenci farklı `Öğrenci Rapor No` alır. Aynı öğrenci, sınıf
    değiştirerek birden çok üyelik satırı alsa dahi bütün çalışma sayfalarında aynı rapor-yerel
    numarayla eşleşir.
20. Birden fazla `CUSTOM` yoklama durumu `Özel Yoklama Durumları` normalize sayfasında durum
    kodu/etiketi başına ayrı, deterministik sayım olarak görünür; ana özette belirsiz birleşik
    hücre bulunmaz.

## 9. Kapsam dışı ve açık kararlar

- Excel üretim kütüphanesi, işçi/kuyruk ve nesne depolama sağlayıcısı; `A-007`/`A-008` ADR'leri.
- İndirme bağlantısı, artefakt ve iş kaydının kesin sayısal saklama süreleri; güvenli ve süreli
  olması bağlayıcıdır, süre değeri ayrı karardır.
- XLSX dışı biçimler, e-posta ile gönderim, zamanlanmış raporlar, kurumlar arası birleşik
  çalışma kitabı, grafikler ve gelişmiş karşılaştırmalı metrikler.
- Kuruma özel alanların Excel'e eklenmesi; alan hassasiyeti ve sütun seçimi ayrı sözleşme
  gerektirir.
- Öğretmen notunun standart Excel'e eklenmesi; V1 veri minimizasyonu gereği dahil edilmez.

## 10. Kaynaklarla uyum kontrolü

- Rapor kapsamları ve sunucu üretimi `URUN_VE_UYGULAMA_PLANI.md` §8.9 ile uyumludur.
- `REPORT_EXPORT` yetkisi, kurum yöneticisi/hoca varsayılanları ve platform yöneticisi sınırı
  `YETKI_MATRISI.md` §3.7 ile uyumludur.
- Toplu dışa aktarımın yüksek riskli kişisel veri olduğu, denetlenmesi ve süreli bağlantıyla
  teslim edilmesi `KISISEL_VERI_ENVANTERI.md` satır 16 ile karşılanır.
- Kaynak varlıklar, kurum izolasyonu, arşiv geçmişinin korunması ve `REPORT_EXPORTED` kataloğu
  `VERI_MODELI.md` §3, §13 ve §15 ile uyumludur.
- API sürümleme, hata zarfı, idempotency, `202`nin tamamlanma sayılmaması ve kurum bağlamı
  `API_GENEL_KURALLARI.md` ile uyumludur.
