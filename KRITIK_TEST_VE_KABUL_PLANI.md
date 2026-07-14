# Kritik Test ve Kabul Planı

| Alan | Değer |
|---|---|
| Görev | P-013 — Kritik test ve kabul planını yaz |
| Belge sürümü | 1.2 |
| Ana sözleşme | URUN_VE_UYGULAMA_PLANI.md §18 |
| Temel sözleşmeler | YETKI_MATRISI.md (P-003), API_GENEL_KURALLARI.md (P-009), SENKRONIZASYON_VE_CAKISMA.md (P-010) |
| İlişkili kritik sözleşmeler | DENETIM_VE_GERI_ALMA_ILKELERI.md (P-011), EXCEL_RAPOR_VERI_SOZLESMESI.md (P-012) |
| Son güncelleme | 14 Temmuz 2026 |

## Revizyon notu (v1.0 → v1.1)

- KAP kayıtları uygulanabilir test kartlarına dönüştürüldü; her kart amaç, fixture, bağlam, adım, HTTP/uygulama, kalıcılık, yasak yan etki, mobil sonuç, otomasyon, kanıt ve kaynak taşır.
- Excel, cihaz oturumu iptali, P-003 kritik yetki sınırları, yerel kullanıcı/kurum izolasyonu, katalog atomikliği ve geri alma için yeni kritik kartlar eklendi.
- P-009, P-010, P-003, P-011 ve P-012 izlenebilirliği açık tablolara bağlandı.
- BLOCKED ile NEEDS_ATTENTION ayrımı P-010 sözleşmesindeki netleştirmeyle hizalandı.

## Revizyon notu (v1.1 → v1.2)

- Oturum geçersizliği ile sunucunun kesin yetki/atama reddi bütün ilgili KAP ve P-010 bölümlerinde ayrıştırıldı.
- Katalog hata sınıflandırması geçici sunucu hatası/rollback olarak; audit, restore, dokuz izin kararı ve tombstone fazları ise test edilebilir alt sonuçlarla netleştirildi.

## 1. Amaç, fixture ve kanıt standardı

Bu plan V1'in veri kaybı, yetki yükseltmesi, tenant sızıntısı ve yanlış başarı bildirme risklerini uygulanabilir kabul testlerine dönüştürür. Test frameworkü veya taşıma sağlayıcısı seçmez; seçildiklerinde her KAP kartı en az belirtilen otomasyon seviyesinde uygulanır.

Ortak sentetik fixture: ORG-A ve ORG-B etkin kurumlardır; ADM-A ORG-A yöneticisi; T-A1/T-A2 aynı sınıfa atanmış hocalar; T-A3 ORG-A içindeki başka sınıf hocası; T-B1 ORG-B hocası; PA platform yöneticisi; S-A/S-B sırasıyla ORG-A/ORG-B öğrencisidir. Gereken kartlar ayrıca askıda üyelik, geri alınmış rol, eski sessionGeneration, eski rowVersion, arşivlenmiş kayıt ve iptal edilmiş atama üretir. Üretim verisi, ham token, parola ve gerçek kişi verisi kullanılmaz.

Her kanıt; KAP kimliği, commit/build, platform ve sürümü, fixture sürümü, UTC zaman damgası, istek/olay sırası, güvenli HTTP sonucu, DB/audit/sync sorgusu ve mobil durum ekranını taşır. SQL, ham token, telefon, adres, not veya kapsam dışı kimlik kanıta yazılmaz.

## 2. KAP kartları

Kartlardaki DB ifadesi, uygulama katmanının test ortamında doğrulanabilen transaction/audit/idempotency/sync sonucudur. Yok ifadesi kesinlikle oluşmaması gereken yan etkiyi belirtir.

### KAP-01 — Atanmamış sınıf kapsamı

Amaç/risk: Hoca operasyonel sınıf verisine ataması olmadan erişemez. Önkoşul ve fixture: T-A3, S-A'nın sınıfına atanmamıştır. Bağlam: T-A3 tokenı ve ORG-A bağlamı. Adımlar: S-A için doğrudan okuma, liste filtresi ve yazma denenir; dar kurum metaveri izni ayrıca verilir. HTTP/uygulama: kapsam dışı kaynak 404, kapsam içi fakat işlem izni olmayan eylem 403; güvenli hata zarfı döner. DB/transaction/audit/sync: hedef iş/veri değişikliği audit'i ve sync olayı kesinlikle yazılmaz; güvenlik açısından anlamlı çapraz sınıf denemesi için minimize güvenlik-red audit'i yazılır. Yok: güvenlik-red audit'inde hedef veri, telefon, not veya hedefin gerçekten var olup olmadığı bulunmaz; öğrenci/veli/yoklama/ilerleme özeti sızmaz. Mobil/kuyruk: başarı görünmez, yazma NEEDS_ATTENTION olur. Otomasyon: API + repository. Kanıt: üç istek sonucu, hedef yan-etki sıfır sayımı ve minimize red audit'i. Kaynak: P-003 §2.2/3,11; P-009 §4.1, §9/4; P-011 §3.2.

### KAP-02 — Kurum izolasyonu

Amaç/risk: Başka kurumun varlığı veya verisi sızmaz. Önkoşul ve fixture: S-B ve T-B1 ORG-B'dedir. Bağlam: ADM-A ve T-B1 ayrı çağrıcı olarak kullanılır. Adımlar: diğer kurum öğrenci/sınıf/iş kimliği okunur ve yazılır. HTTP/uygulama: 404 RESOURCE_NOT_FOUND veya uygun 403; hata ayırt edici veri içermez. DB/transaction/audit/sync: hedef iş/veri değişikliği audit'i, idempotency sonucu ve olay oluşmaz; güvenlik açısından anlamlı çapraz tenant denemesinde minimize güvenlik-red audit'i oluşur. Yok: red audit'i hedef veri, telefon, not veya hedefin varlığı bilgisini taşımaz; kurum adı, kimliği ve ilişki özeti sızmaz. Mobil/kuyruk: başarı yok; terminal red NEEDS_ATTENTION. Otomasyon: API + repository. Kanıt: iki çağrıcı sonucu, hedef yan-etki sıfır sayımı ve minimize red audit'i. Kaynak: Ana plan §18.2/5; P-009 §2, §4.1, §9/1; P-011 §3.2.

### KAP-03 — Oturum ve rol geçersizliği

Amaç/risk: Askıya alınmış üyelik, eski sessionGeneration veya geri alınmış TEACHER rolü erişemez. Önkoşul ve fixture: T-A1 için bu üç durum ayrı ayrı kurulur. Bağlam: ORG-A tokenı. Adımlar: korunan okuma/yazma çağrılır; rol iptalinde atama ve devredilmiş izinler de kontrol edilir. HTTP/uygulama: 401 UNAUTHENTICATED/SESSION_REVOKED veya 403; geçerli oturum sanılmaz. DB/transaction/audit/sync: rol iptali atomik kapanır; yeni iş/audit/sync oluşmaz. Yok: eski yetkiyle işlem, otomatik kuyruk gönderimi. Mobil/kuyruk: oturum geçersizse BLOCKED; yeniden giriş istenir. Otomasyon: API + transaction. Kanıt: token sonucu, üyelik/atama/izin sorgusu. Kaynak: P-009 §4, §7.3, §9/2-3,14.

### KAP-04 — Platform yöneticisi denetimi

Amaç/risk: Global istisna izlenmeyen erişime dönüşmez. Önkoşul ve fixture: PA ve açık ORG-A destek bağlamı. Bağlam: PA, hedef kurumla; bağlamsız çağrı ayrıca. Adımlar: ORG-A kaydı görüntülenir/değiştirilir, sonra açık bağlam olmadan denenir. HTTP/uygulama: açık bağlamda başarı, diğerinde red. DB/transaction/audit/sync: her izinli kurum erişiminde PLATFORM_ADMIN_ORG_ACCESS; yazmada ilgili audit oluşur. Yok: ORG-B verisi veya bağlamsız destek erişimi. Mobil/kuyruk: sadece kanonik başarı gösterilir. Otomasyon: API + audit. Kanıt: audit özeti ve hedef kapsamı. Kaynak: P-003 §2.2/4; P-009 §2, §4, §9/15.

### KAP-05 — API yazma biçimi

Amaç/risk: PATCH gizli alan değişimi veya fiziksel silme yapmaz. Önkoşul ve fixture: ORG-A güncellenebilir kaynak. Bağlam: yetkili ADM-A. Adımlar: kısmi PATCH, temizlenebilir null, zorunlu null, iş komutu ve DELETE denenir. HTTP/uygulama: yalnız gönderilen alan değişir; geçersiz null 422; komut POST; DELETE desteklenmez. DB/transaction/audit/sync: geçersiz istekte değişiklik/audit/sync yoktur. Yok: gönderilmemiş alan veya arşiv geçmişi kaybı. Mobil/kuyruk: 422 NEEDS_ATTENTION. Otomasyon: API. Kanıt: önce/sonra kaynak ve audit. Kaynak: P-009 §7.1, §9/7.

### KAP-06 — Başlık, cursor ve filtre güvenliği

Amaç/risk: Başlık/filtre manipülasyonu yetki veya veri sızıntısı sağlamaz. Önkoşul ve fixture: ORG-A sayfalı liste. Bağlam: T-A1 ve geçersiz değerler. Adımlar: geçersiz X-Request-Id/Idempotency-Key, bozuk veya başka bağlam cursor, izin dışı filtre denenir. HTTP/uygulama: 400/422 ve güvenli hata zarfı. DB/transaction/audit/sync: yazma yan etkisi yoktur. Yok: idempotency/kimlik doğrama atlatma, liste verisi. Mobil/kuyruk: yazma varsa NEEDS_ATTENTION. Otomasyon: API. Kanıt: hata zarfı ve boş yan-etki sorgusu. Kaynak: P-009 §3.2, §6, §9/10,13.

### KAP-07 — Yetki sonrası deterministik sayfalama

Amaç/risk: Cursor başka tenant veya filtrede kullanılamaz. Önkoşul ve fixture: eşit sıralama değerli ORG-A kayıtları. Bağlam: T-A1 ve ADM-A. Adımlar: ilk sayfa alınır; cursor farklı filtre, sıra ve çağırıcıyla tekrar kullanılır. HTTP/uygulama: yetki sonrası id son bağlayıcılı sıralama; uygunsuz cursor 400 INVALID_CURSOR. DB/transaction/audit/sync: salt okuma, yazma yoktur. Yok: ORG-B veya atanmadığı sınıf kaydı. Mobil/kuyruk: liste güvenli hata gösterir. Otomasyon: API. Kanıt: sayfa sırası ve hata sonuçları. Kaynak: P-009 §6, §9/10-11.

### KAP-08 — Kurum iş günü

Amaç/risk: Cihaz saat dilimi kurum tarihini değiştiremez. Önkoşul ve fixture: ORG-A defaultTimezone ve sınır saatleri. Bağlam: farklı cihaz saat dilimleri. Adımlar: aynı sessionDate/plannedDate iki cihazdan değerlendirilir. HTTP/uygulama: kurum gününe göre aynı sonuç. DB/transaction/audit/sync: tek doğru DATE kaydı; çift oturum yoktur. Yok: cihaz saatinden kaynaklı tarih kayması. Mobil/kuyruk: kullanıcıya kurum tarihi gösterilir. Otomasyon: birim + API. Kanıt: iki cihaz/istek ve tarih sorgusu. Kaynak: P-009 §3.3, §9/12.

### KAP-09 — Eşdeğer idempotent tekrar

Amaç/risk: Yanıt kaybı ikinci etki üretmez. Önkoşul ve fixture: yetkili yazma ve sabit clientMutationId. Bağlam: T-A1, ORG-A. Adımlar: yazma sunucuda tamamlanır, cevap düşürülür, aynı anahtarla tekrar gönderilir. HTTP/uygulama: ilk kanonik sonuç yeniden döner. DB/transaction/audit/sync: tek iş, tek audit, tek mantıksal olay ve tek idempotency terminal sonucu. Yok: ikinci kaynak/sürüm/audit. Mobil/kuyruk: ancak sonuç doğrulanınca SUCCEEDED. Otomasyon: API + transaction + E2E. Kanıt: aynı anahtarlı iki istek ve sayımlar. Kaynak: Ana plan §18.2/3; P-009 §7.2, §9/5; P-010 §9/2.

### KAP-10 — Anahtar tekrar kullanım çelişkisi

Amaç/risk: Aynı anahtar başka isteğe bağlanamaz. Önkoşul ve fixture: tamamlanmış idempotent yazma. Bağlam: aynı kullanıcı/kapsam. Adımlar: yöntem, yol, hedef, gövde veya If-Match sürümü değiştirilerek tekrar gönderilir. HTTP/uygulama: 409 IDEMPOTENCY_KEY_REUSED. DB/transaction/audit/sync: yalnız ilk kayıt korunur. Yok: ikinci iş/audit/olay. Mobil/kuyruk: NEEDS_ATTENTION, yeni işlem yeni anahtar ister. Otomasyon: API + transaction. Kanıt: fingerprint ve sayım sorgusu. Kaynak: P-009 §7.2; P-010 §3.1, §9/3.

### KAP-11 — Eşzamanlı eşdeğer istek

Amaç/risk: Aynı anahtar iki kez yürütülmez. Önkoşul ve fixture: eşdeğer iki paralel istek. Bağlam: T-A1/aynı kullanıcı. Adımlar: iki istek eşzamanlı başlatılır; lease tutulur. HTTP/uygulama: biri yürür, diğeri 202/PENDING takibi veya ilk sonucu alır. DB/transaction/audit/sync: tek lease sahibi ve tek terminal yan etki. Yok: çift audit/olay. Mobil/kuyruk: ikinci deneme anahtarı korur. Otomasyon: transaction + API. Kanıt: lease_generation ve iki yanıt. Kaynak: P-010 §3.2, §9/4.

### KAP-12 — Ağ kesintisi ve yeniden başlatma

Amaç/risk: Bekleyen çevrimdışı yazma kaybolmaz. Önkoşul ve fixture: beyaz listeli yoklama/ilerleme oluşturma. Bağlam: T-A1, ORG-A. Adımlar: gönderimde ağ kesilir, uygulama kapatılıp açılır, bağlantı geri gelir. HTTP/uygulama: aynı anahtarla güvenli yeniden deneme. DB/transaction/audit/sync: kesin sonuç yokken terminal yan etki yoktur. Yok: kuyruk silme veya KAYDEDILDI gösterimi. Mobil/kuyruk: PENDING/RETRY_WAIT kalıcı, sonra kanonik sonuç. Otomasyon: mobil durum + E2E. Kanıt: yerel depo öncesi/sonrası ve istek anahtarı. Kaynak: Ana plan §18.2/2,10; P-010 §4, §9/1.

### KAP-13 — Belirsiz cevap sonrası tekrar

Amaç/risk: Sunucu başarılıyken cevap kaybı veri çoğaltmaz. Önkoşul ve fixture: yazma/audit transaction'ı hata enjeksiyonu ile cevap öncesi tamamlanır. Bağlam: T-A1. Adımlar: cevap düşürülür, aynı anahtar tekrar edilir. HTTP/uygulama: ilk başarılı sonuç döner. DB/transaction/audit/sync: tek kaynak, audit ve eventId. Yok: yeni idempotency sonucu veya ikinci eventId. Mobil/kuyruk: belirsizlikte silinmez, sonuçta SUCCEEDED. Otomasyon: API + E2E. Kanıt: transaction ve event sayımı. Kaynak: P-010 §3.2, §9/2.

### KAP-14 — Kesin hata sınıflandırması

Amaç/risk: Terminal hata otomatik tekrarlanmaz. Önkoşul ve fixture: doğrulama, çakışma, 403/404 ve geçici hata cevapları. Bağlam: T-A1. Adımlar: 400/422/409 ve sunucunun kesin yetki/atama kaybı için 403/404; ayrıca 429/5xx/ağ hatası enjekte edilir. HTTP/uygulama: 403/404 dahil kesin sonuç NEEDS_ATTENTION; 429/5xx/ağ RETRY_WAIT. DB/transaction/audit/sync: kesin hata FAILED olarak tek kez saklanır, geçici hata terminal değildir. Yok: kuyruk silme veya otomatik yeniden yazma. Mobil/kuyruk: kullanıcı çözüm seçer; eski işlem yeni anahtarsız değişmez. Otomasyon: mobil + API. Kanıt: durum geçişi ve idempotency kaydı. Kaynak: P-010 §4.3, §8, §9/9,20,24.

### KAP-15 — İstemci UUID beyaz listesi

Amaç/risk: İstemci kimliği yetki veya kaynak oluşturmayı genişletmez. Önkoşul ve fixture: beyaz listeli ve dışı iki oluşturma ucu. Bağlam: T-A1. Adımlar: aynı UUID/anahtar tekrar edilir; dışı uçta id gönderilir. HTTP/uygulama: beyaz listede id kabul/tekrar güvenli; dışında 422. DB/transaction/audit/sync: tek kaynak ve tek audit. Yok: ikinci kaynak veya dışı uçta kayıt. Mobil/kuyruk: dışı istek NEEDS_ATTENTION. Otomasyon: API + repository. Kanıt: kaynak ve anahtar sorguları. Kaynak: P-009 §5.1, §9/6; P-010 §5, §9/12.

### KAP-16 — Sürüm çakışması

Amaç/risk: Genel son yazan kazanır davranışı yoktur. Önkoşul ve fixture: öğrenci, ilerleme ve arşiv kaydının eski rowVersionı. Bağlam: yetkili ADM-A/T-A1. Adımlar: eski sürümle yazma denenir. HTTP/uygulama: 409 VERSION_CONFLICT; güncel kaynak normal okuma yetkisiyle alınır. DB/transaction/audit/sync: değişiklik/audit/event yoktur. Yok: eski işlemin yeni sürümle otomatik gönderimi. Mobil/kuyruk: NEEDS_ATTENTION ve kullanıcı kararı. Otomasyon: API + mobil. Kanıt: sürüm ve sıfır yan-etki sorgusu. Kaynak: P-009 §7.1, §9/8; P-010 §6, §9/5.

### KAP-17 — İki hoca yoklama çakışması

Amaç/risk: Yalnız normal yoklama istisnası güvenli çalışır. Önkoşul ve fixture: T-A1/T-A2, aynı öğrenci/oturum ve başlangıç rowVersionı. Bağlam: aynı atanmış sınıf. Adımlar: tekil ve toplu normal işaretleme paralel gönderilir. HTTP/uygulama: son geçerli sunucu yazması uygulanır, ikinci yanıtta concurrentChange true. DB/transaction/audit/sync: sürüm artar, önceki değer audit'te kalır. Yok: yetkisiz yazma veya geri alma istisnası. Mobil/kuyruk: kanonik sonuç ve eşzamanlılık uyarısı. Otomasyon: E2E + audit. Kanıt: işlem sırası, iki audit kaydı, sürüm. Kaynak: Ana plan §18.2/1; P-010 §6.2, §9/6.

### KAP-18 — Yoklama geri alma çakışması

Amaç/risk: Geri alma normal yoklama istisnasına kaçmaz. Önkoşul ve fixture: geri alınabilir tekil ve Hepsi Geldi grubu; bir hedefin sürümü değiştirilir. Bağlam: geri alma yetkili çağırıcı. Adımlar: tekil eski sürümle, grup tek çakışmalı durumda geri alınır. HTTP/uygulama: tekil VERSION_CONFLICT, grup GROUP_UNDO_CONFLICT. DB/transaction/audit/sync: grupta atomik sıfır ters kayıt. Yok: kısmi grup geri alma. Mobil/kuyruk: NEEDS_ATTENTION. Otomasyon: transaction + API. Kanıt: tüm hedeflerin önce/sonra durumu. Kaynak: P-010 §6.2, §9/7; P-011 §5.

### KAP-19 — Tek günlük oturum tekilliği

Amaç/risk: Aynı iş günü ikinci yoklama oturumu başarılı sayılmaz. Önkoşul ve fixture: aynı ORG-A/sınıf/gün için iki çevrimdışı oluşturma. Bağlam: T-A1/T-A2. Adımlar: iki oturum oluşturma paralel gönderilir. HTTP/uygulama: ikincisi STATE_CONFLICT, kanonik ilk oturum alınır. DB/transaction/audit/sync: tek oturum/audit/event. Yok: ikinci başarı veya çifte satır. Mobil/kuyruk: ikinci işlem NEEDS_ATTENTION. Otomasyon: API + E2E. Kanıt: tekillik sorgusu ve iki yanıt. Kaynak: P-010 §6.2, §9/8.

### KAP-20 — Oturum engeli ve yetki kaybı

Amaç/risk: BLOCKED ve NEEDS_ATTENTION ayrımı görünür ve güvenlidir. Önkoşul ve fixture: T-A1 bekleyen yazması; önce oturumu iptal, sonra sınıf ataması/yetkisi kaldırılmış ayrı deneme. Bağlam: ORG-A. Adımlar: token geçersizliğinde gönderim; yeniden girişten sonra kaynak/sürüm doğrulaması; kesin 403/404 atama kaybı denenir. HTTP/uygulama: 401/SESSION_REVOKED BLOCKED; kesin 403/404 NEEDS_ATTENTION. DB/transaction/audit/sync: hiçbir durumda eski iş otomatik uygulanmaz; kapsam kaybında yerel hassas veri purge edilir. Yok: kuyruk silme, abonelik sürmesi, yetki geri gelince otomatik gönderim. Mobil/kuyruk: BLOCKED yeniden giriş ister; NEEDS_ATTENTION kullanıcı kararı ister. Otomasyon: API + mobil + E2E. Kanıt: durum geçişi, purge ve abonelik kaydı. Kaynak: P-010 §4.3, §7.3, §8, §9/10,16,18.

### KAP-21 — Olay tekrar/sıra dışılığı

Amaç/risk: Olay taşıması kanonik veriyi bozmaz. Önkoşul ve fixture: aynı eventId tekrarı ve eski changeSequence/rowVersion. Bağlam: yetkili T-A1. Adımlar: olaylar yinelenmiş ve ters sırada teslim edilir. HTTP/uygulama: normal güvenli okuma/akışla uzlaştırma. DB/transaction/audit/sync: yeni iş yan etkisi oluşmaz; eventId ayıklanır. Yok: yerel verinin geriye alınması. Mobil/kuyruk: kanonik görünüm korunur. Otomasyon: mobil + E2E. Kanıt: olay günlüğü ve kanonik kaynak. Kaynak: P-010 §7.1, §9/11.

### KAP-22 — Snapshot toparlaması

Amaç/risk: Geçersiz token kısmi/veri sızdıran akış vermez. Önkoşul ve fixture: süresi dolmuş veya kapsamı değişmiş syncToken. Bağlam: T-A1. Adımlar: akış sürdürülür, tam snapshot sayfalanır, yeni token alınmadan tamamlanma denenir. HTTP/uygulama: kısmi akış reddi; yüksek su işaretli snapshot ve yeni token zorunlu. DB/transaction/audit/sync: arşiv tombstone olarak korunur. Yok: eski kapsam verisi veya tamamlanmış işareti. Mobil/kuyruk: kuyruk korunur, güvenli snapshot sonrası uzlaşır. Otomasyon: API + mobil. Kanıt: token/snapshot sırası. Kaynak: P-010 §7.2, §9/13.

### KAP-23 — Kapsam daralması ve transfer

Amaç/risk: Eski sınıf yeni sınıf bilgisini taşımaz. Önkoşul ve fixture: S-A sınıf transferi ve T-A1'in eski sınıf ataması. Bağlam: eski/yeni sınıf istemcileri. Adımlar: transfer, yetki iptali ve REMOVED_FROM_SCOPE alınır. HTTP/uygulama: eski kapsam okumaları reddedilir. DB/transaction/audit/sync: eski akış removal, yeni akış yetkili UPSERT; kapsamlı olay sınıf kimliği taşır. Yok: eski sınıfta kişisel/operasyonel veri veya yeni sınıf kimliği. Mobil/kuyruk: ilgili önbellek/purge ve NEEDS_ATTENTION. Otomasyon: repository + E2E. Kanıt: iki akış ve yerel purge. Kaynak: P-010 §7.1, §7.3, §9/18,23.

### KAP-24 — Lease kurtarma

Amaç/risk: Çöken sahip yarım veya çift etki bırakamaz. Önkoşul ve fixture: süresi dolacak PENDING lease. Bağlam: iki işçi/sahip. Adımlar: ilk sahip çökertilerek ikinci sahip yeniden alır; eski sahip terminal yazmayı dener. HTTP/uygulama: takip 202 veya güvenli terminal. DB/transaction/audit/sync: büyük fencing sayacı; yalnız güncel sahip terminal yazar. Yok: yarım iş/audit/olay veya eski sahibin COMPLETEDi. Mobil/kuyruk: anahtar korunur. Otomasyon: transaction. Kanıt: lease_generation ve atomiklik sorgusu. Kaynak: P-010 §3.2, §9/14.

### KAP-25 — Tombstone saklama

Amaç/risk: Sonuç minimizasyonu tekrar işleme dönüşmez; süresi dolmuş anahtar için yanlış sunucu garantisi kurulmaz. Önkoşul ve fixture: Faz A'da resultExpiresAt geçmiş/keyRetentionExpiresAt geçmemiş terminal kayıt; Faz B'de keyRetentionExpiresAt ve desteklenen yerel kuyruk ömrü geçmiş kayıt. Bağlam: aynı çağırıcı/anahtar. Adımlar: Faz A'da sonuç gövdesi temizlendikten sonra aynı anahtar tekrar edilir ve zamanlar doğrulanır; Faz B'de istemci eski anahtarı göndermeden yerel kaydı güvenli uzlaştırma/temizleme politikasına alır. HTTP/uygulama: Faz A yeni iş uygulanmaz; Faz B için eski anahtarı bilerek gönderip sunucunun kesin reddi beklenmez. DB/transaction/audit/sync: Faz A tombstone/fingerprint korunur ve createdAt ≤ completedAt < retention; Faz B'de tombstone fiziksel silinmişse sunucunun bilinmeyen anahtarı reddetme garantisi yoktur. Yok: Faz A'da ikinci iş/audit/olay; Faz B'de otomatik eski anahtar gönderimi. Mobil/kuyruk: Faz A kanonik uzlaştırma, Faz B güvenli temizlik/uzlaştırma; ikisi de başarı sayılmaz. Otomasyon: repository + API. Kanıt: iki fazın saklama alanları, yerel kuyruk yaşı ve sayımlar. Kaynak: P-010 §3.1, §9/15 (Faz A), §9/21 (Faz A), §9/22 (Faz B).

### KAP-26 — Program sürümü geçmişi

Amaç/risk: Aktif program değişikliği geçmişi bozmaz. Önkoşul ve fixture: aktif program, plan ve ilerleme. Bağlam: yetkili ADM-A. Adımlar: büyük yapısal değişiklik yapılır. HTTP/uygulama: yeni programVersion döner. DB/transaction/audit/sync: eski plan/ilerleme değişmeden kalır, yeni değişiklik ayrı event/audit olur. Yok: geçmiş satır güncelleme/silme. Mobil/kuyruk: yeni sürüm görünür. Otomasyon: repository + API. Kanıt: iki sürüm ve geçmiş sorgusu. Kaynak: Ana plan §18.2/7; P-010 §6.2, §9/19.

### KAP-27 — Güvenli hata ve log

Amaç/risk: Hata/zaman tanılama hassas veri sızdırmaz. Önkoşul ve fixture: kişisel alan, hatalı yazma ve kapsam dışı kimlik. Bağlam: T-A1/T-B1. Adımlar: doğrulama, yetki, bulunamama, çakışma ve beklenmeyen hata üretilir. HTTP/uygulama: ortak güvenli zarf/code/requestId. DB/transaction/audit/sync: terminal sonuç doğru, hassas payload yok. Yok: SQL, token, telefon, not, başka kurum kimliği. Mobil/kuyruk: kullanıcıya güvenli hareket gösterilir. Otomasyon: API + log incelemesi. Kanıt: sansürlenmiş hata/log/audit örneği. Kaynak: P-009 §3.2, §5.2, §9/9; P-010 §8.

### KAP-28 — Global ve kurum idempotency kapsamı

Amaç/risk: NULL-güvenli kapsam tekilliği kurumları karıştırmaz. Önkoşul ve fixture: PA global kurum oluşturma, ORG-A kurum yazması. Bağlam: PA ve ORG-A çağırıcısı. Adımlar: aynı anahtar GLOBAL ve ORGANIZATION kapsamlarında, sonra farklı gövdelerle denenir. HTTP/uygulama: aynı kapsamda eşdeğer tekrar oynatılır; farklı gövdede 409. DB/transaction/audit/sync: iki kapsam ayrı, her birinde tek etki. Yok: NULL nedeniyle çapraz tekrar veya çift kurum. Mobil/kuyruk: kanonik sonuç. Otomasyon: API + transaction. Kanıt: kapsam alanları ve tekillik sorgusu. Kaynak: P-009 §7.2; P-010 §3.1, §9/17.

### KAP-29 — Öğrenci arşiv geri yükleme

Amaç/risk: Geçmiş korunurken doğrudan restore ile audit kaydından restore/undo farklı yetki ve audit zincirleriyle yürür. Önkoşul ve fixture: arşivli S-A, ilgili geçmiş ve geri alınabilir kaynak audit kaydı. Bağlam: yalnız RESTORE_ARCHIVED yetkili ADM-A; RESTORE_ARCHIVED + AUDIT_UNDO yetkili ayrı çağırıcı; yetkisiz hoca. Adımlar: doğru sürümle doğrudan restore; audit kaydından restore/undo; eski sürüm ve yetkisiz denemeler yapılır. HTTP/uygulama: doğrudan yol RESTORE_ARCHIVED ile başarı; audit yolu iki izni birlikte ister; eski sürüm 409, yetkisiz 403/404. DB/transaction/audit/sync: doğrudan restore normal restore audit'i üretir, is_undo=false ve undo_of ilişkisi yoktur; audit yolu yeni is_undo=true audit satırını kaynak audit'e bağlar, eski audit değişmez. Yok: her iki yolda geçmiş yoklama/ilerleme silinmesi, yan etkili red veya izinlerden birinin diğerinin yerine geçmesi. Mobil/kuyruk: çakışma/ret NEEDS_ATTENTION. Otomasyon: transaction + API. Kanıt: iki yolun audit alanları, arşiv/audit zinciri ve geçmiş sorgusu. Kaynak: Ana plan §18.2/6; P-011 §2, §5-6.

### KAP-30 — Excel kritik kapsam ve teslim

Amaç/risk: Excel raporu tutarlı, yetkili ve güvenli artefakt üretir. Önkoşul ve fixture: ORG-A'da iki sınıf, tarih aralığı, sınıf transferli S-A, UNMARKED ve iki aynı adlı öğrenci; formül önekli metin; veli izinli/izinsiz hoca; limit aşan veri. Bağlam: ADM-A, REPORT_EXPORT hoca ve GUARDIAN_CONTACT_VIEW'süz hoca. Adımlar: sınıf/tarih kapsamlı rapor istenir; snapshot sonrası yazma yapılır; guardianContacts, RUNNING öncesi ve indirme anı yetki iptali, süre sonu, limit aşımı denenir. HTTP/uygulama: yalnız yetkili kapsam; 202/READY ayrımı; iptalde EXPORT_AUTHORIZATION_REVOKED; limitte EXPORT_TOO_LARGE; süre sonunda indirilemez. DB/transaction/audit/sync: tek tutarlı snapshot ve sourceCutoffAt; snapshot sonrası kayıt dışarıda; artefakt yalnız READY; süre sonunda purge; REPORT_EXPORTED güvenli özet taşır. Yok: UNMARKED satırı/sayımı, sınıflar arası dönem toplamı, formül çalışması, yetkisiz veli telefonu, kısmi dosya veya kalıcı URL. Mobil/kuyruk: QUEUED/RUNNING başarı sayılmaz; yetki/limit hatası görünür. Otomasyon: API + dosya doğrulama + E2E. Kanıt: çalışma kitabı hücreleri; ortak Öğrenci Rapor No; transferde ayrı dönem satırı; sourceCutoffAt/completedAt; yetki/purge/audit sorguları. Kaynak: Ana plan §18.2/9; P-012 §2-8 özellikle §5, §6.1-2, §6.5, §6.7, §8/1,4,6-7,9,11-14,18-19.

### KAP-31 — Kurum bağlamlı cihaz oturumu iptali

Amaç/risk: Oturum iptali başka kurum oturumunu kapatmaz ve bekleyen işi kaybetmez. Önkoşul ve fixture: aynı global kullanıcının ORG-A ve ORG-B oturumları; ORG-A'da bekleyen T-A1 işlemi. Bağlam: ADM-A, ORG-A kullanıcı hedefi; yetkisiz hoca ayrıca. Adımlar: ADM-A yalnız ORG-A üyelik oturumlarını iptal eder; eski access/refresh tokenla istek; ORG-B tokenla istek; hoca komutu dener. HTTP/uygulama: ORG-A eski token 401 SESSION_REVOKED; ORG-B oturumu geçerli; hoca 403/404. DB/transaction/audit/sync: yalnız ORG-A sessionGeneration etkilenir; SESSION_REVOKED ve iptal audit'i yazılır, ham token yazılmaz. Yok: ORG-B iptali, bekleyen iş silme, yeni yazma. Mobil/kuyruk: ORG-A işlem BLOCKED ve giriş ister; ORG-B etkilenmez. Otomasyon: API + transaction + mobil. Kanıt: iki kurum token sonucu, session/audit ve kuyruk durumu. Kaynak: Ana plan §18.2/8; P-003 §3.3 ve §6.2/7; P-009 §4; P-010 §4.3.

### KAP-32 — P-003 yetki tavanı

Amaç/risk: Dokuz devredilebilir izin yalnız kendi işlemini açar; izin yönetimi veya operasyonel veri tırmanışı yaratmaz. Önkoşul ve fixture: Veri güdümlü test, T-A1'e sırayla yalnız BRAND_MANAGE, MODULE_MANAGE, TEACHER_ACCOUNT_MANAGE, TEACHER_CLASS_ASSIGN, TEACHER_PERMISSION_VIEW, DEVICE_SESSION_REVOKE, CUSTOM_ATTENDANCE_STATUS_MANAGE ve GUARDIAN_CONTACT_VIEW verir; izin değiştirme ayrıca mutlak sınır olarak denenir; ADM-A kurum tavanı da sınanır. Bağlam: T-A1 ve ADM-A. Adımlar: (1) BRAND_MANAGE yalnız marka, (2) MODULE_MANAGE yalnız modül ayarı, (3) TEACHER_ACCOUNT_MANAGE yalnız hesap yönetimi, (4) TEACHER_CLASS_ASSIGN yalnız aynı kurumda başka hoca ataması, (5) TEACHER_PERMISSION_VIEW yalnız görüntüleme, (6) hoca izin değiştirme ret, (7) DEVICE_SESSION_REVOKE yalnız KAP-31deki kurum oturumu iptali, (8) CUSTOM_ATTENDANCE_STATUS_MANAGE yalnız özel durum ayarı, (9) GUARDIAN_CONTACT_VIEW yalnız veli iletişimi için tek tek çağrılır; her fazda diğer sekiz işlem de denenir. T-A1 kendi üzerinde atama yapmayı, kendi/başka hoca iznini değiştirmeyi ve kurum izniyle atanmadığı sınıf verisini ayrıca dener. HTTP/uygulama: yalnız açık tekil işlem başarı; tüm çapraz işlemler 403 veya kapsam gizliliğinde 404. DB/transaction/audit/sync: redlerde yetki/atama/değişiklik yok; izinli yönetim işlemi denetlenir. Yok: izin türetme, kendi kapsamını genişletme, GUARDIAN_CONTACT_VIEWun öğrenci görüntüleme/GUARDIAN_MANAGE sağlaması, operasyonel veri sızıntısı veya ADM-Anın kurum/rol tavanını aşması. Mobil/kuyruk: terminal red NEEDS_ATTENTION. Otomasyon: API + parametrik transaction testi. Kanıt: dokuz izin × dokuz işlem sonuç matrisi, atama ve sıfır yan-etki sorguları. Kaynak: P-003 §2.2/5-12, §4.1, §4.4, §6.2/1-9; karar 7 için KAP-31.

### KAP-33 — Yerel kullanıcı/kurum izolasyonu

Amaç/risk: Eski kullanıcı/kurum kuyruğu yeni bağlamda görünmez veya gönderilmez. Önkoşul ve fixture: T-A1'in ORG-A bekleyen kuyruğu ve hassas önbelleği. Bağlam: çıkış sonrası T-B1/ORG-B girişi; sonra T-A1 dönüşü. Adımlar: kuyruk varken çıkış, farklı kullanıcı/kurum girişi, eşitleme tetikleme ve eski kullanıcı geri girişi yapılır. HTTP/uygulama: T-B1 adına eski istek gönderilmez. DB/transaction/audit/sync: sunucuda T-A1 işlemi T-B1 kapsamına geçmez. Yok: ORG-A önbelleği, kuyruğu veya hassas özetinin yeni bağlamda okunması. Mobil/kuyruk: eski bağlam görünmez/purge edilir; T-A1 dönüşünde güvenli yerel politika ile kendi işi yeniden değerlendirilir, otomatik başarı olmaz. Otomasyon: mobil durum + E2E. Kanıt: iki yerel namespace, ağ günlüğü ve dönüş durumu. Kaynak: P-010 §4.1, §7.2, §9/16.

### KAP-34 — sync_entity_catalog atomikliği

Amaç/risk: Katalogsuz V1 aggregate sessiz eşitleme dışı kalamaz; eksik migration dağıtım bütünlüğü hatasıdır. Önkoşul ve fixture: catalogda olmayan aggregate; ayrıca sınıf kimliği eksik sınıf-kapsamlı aggregate. Bağlam: yetkili yazma transaction'ı ve deployment/migration bütünlük kontrolü. Adımlar: iş değişikliği başlatılır, catalog FK/kapsam kısıtı hata enjeksiyonu ile reddedilir; geçerli migrationla aggregate/kapsam eşlemesi eklendikten sonra tekrar edilir. HTTP/uygulama: API katmanına ulaşırsa güvenli INTERNAL_ERROR/5xx; bu istemci kaynaklı 4xx değildir. DB/transaction/audit/sync: FK/kapsam reddinde iş değişikliği, audit, idempotency terminal sonucu ve outbox/sync_changes birlikte rollback olur; 5xx sonucunda P-010 gereği idempotency terminal FAILED olmaz. Yok: kısmi iş/audit/olay, sessiz eşitleme dışı değişiklik veya mobil NEEDS_ATTENTION. Mobil/kuyruk: RETRY_WAIT/PENDING kalır; operasyonel alarm üretilir. Otomasyon: repository + transaction + migration/deployment bütünlük testi. Kanıt: rollback, idempotency PENDING durumu, catalog/scope sorguları ve alarm kaydı. Kaynak: P-010 §3.2, §4.3, §7.1, §9/20,23,25.

### KAP-35 — Audit ve geri alma bütünlüğü

Amaç/risk: Hepsi Geldi geri alma, yetki ve maskeleme geçmişi bozmadan çalışır. Önkoşul ve fixture: ilk Hepsi Geldi audit grubu, öğrencilerde başlangıç UNMARKED/recorded alanları NULL; ikinci kez geri alma ve doğrudan RESTORE_ARCHIVED örnekleri; maskelenecek telefon/not/token alanları. Bağlam: AUDIT_UNDO yetkili çağırıcı, yalnız RESTORE_ARCHIVED yetkili çağırıcı ve yetkisiz hoca. Adımlar: grup geri alma; bir hedef çakışması; aynı audit tekrar geri alma; doğrudan restore ve audit üzerinden restore; audit API okuma yapılır. HTTP/uygulama: ilk geri alma öğrencileri UNMARKED ve recorded alanları NULL yapar; grup çakışması GROUP_UNDO_CONFLICT; ikinci undo UNDO_ALREADY_APPLIED; RESTORE_ARCHIVED ve AUDIT_UNDO birbirinin yerine geçmez. DB/transaction/audit/sync: geri alma yeni audit üretir, eski audit değişmez; maskeleme sunucuda uygulanır. Yok: kısmi grup etkisi, eski audit güncellemesi, telefon/not/token/serbest hassas veri sızıntısı. Mobil/kuyruk: çakışma ve tekrar NEEDS_ATTENTION. Otomasyon: transaction + API + audit görünümü. Kanıt: öğrenci durumları, audit zinciri, maskeli/maskesiz yetki yanıtı. Kaynak: P-011 §2, §3.1, §5-6, §8.

## 3. İzlenebilirlik

### 3.1. Ana plan §18.2 — 10/10

| Madde | KAP |
|---|---|
| 1. İki hoca aynı yoklamayı değiştirir | KAP-17 |
| 2. Gönderimde ağ kesilir | KAP-12 |
| 3. Cevap kaybolur, çift kayıt oluşmaz | KAP-09, KAP-13 |
| 4. Yetkisiz hoca başka sınıf kimliği kullanır | KAP-01 |
| 5. Kurum başka kurum öğrencisini sorgular | KAP-02 |
| 6. Arşiv/geri yükleme geçmişi korur | KAP-29 |
| 7. Program değişikliği geçmişi bozmaz | KAP-26 |
| 8. Yönetici cihaz oturumunu iptal eder | KAP-31 |
| 9. Excel kapsamla tutarlıdır | KAP-30 |
| 10. Kuyruk uygulama yeniden açılınca korunur | KAP-12 |

### 3.2. P-009 §9 — 15/15

| Madde | KAP | Madde | KAP | Madde | KAP |
|---:|---|---:|---|---:|---|
| 1 | KAP-02 | 2 | KAP-03 | 3 | KAP-03 |
| 4 | KAP-01 | 5 | KAP-09 | 6 | KAP-15 |
| 7 | KAP-05 | 8 | KAP-16 | 9 | KAP-27 |
| 10 | KAP-07 | 11 | KAP-07 | 12 | KAP-08 |
| 13 | KAP-06 | 14 | KAP-03 | 15 | KAP-04 |

### 3.3. P-010 §9 — 25/25

| Madde | KAP | Madde | KAP | Madde | KAP | Madde | KAP | Madde | KAP |
|---:|---|---:|---|---:|---|---:|---|---:|---|
| 1 | KAP-12 | 2 | KAP-09 | 3 | KAP-10 | 4 | KAP-11 | 5 | KAP-16 |
| 6 | KAP-17 | 7 | KAP-18 | 8 | KAP-19 | 9 | KAP-14 | 10 | KAP-20 |
| 11 | KAP-21 | 12 | KAP-15 | 13 | KAP-22 | 14 | KAP-24 | 15 | KAP-25 Faz A |
| 16 | KAP-33 | 17 | KAP-28 | 18 | KAP-23 | 19 | KAP-26 | 20 | KAP-14 |
| 21 | KAP-25 Faz A | 22 | KAP-25 Faz B | 23 | KAP-34 | 24 | KAP-14 | 25 | KAP-34 |

### 3.4. P-003 kritik yetki sınırları ve dokuz karar

| Kural/karar | KAP |
|---|---|
| Mutlak sınırlar: varsayılan ret, kurum/sınıf izolasyonu, sunucu doğrulaması | KAP-01, KAP-02, KAP-32 |
| Platform yöneticisi istisnası ve denetimi | KAP-04 |
| Hoca izinini değiştirememe, izin görünümünün değiştirme vermemesi | KAP-32 |
| Kendi atamasını/kapsamını genişletememe | KAP-32 |
| Kurum kapsamlı iznin operasyonel veri vermemesi | KAP-01, KAP-32 |
| Karar 1 — BRAND_MANAGE yalnız marka ayarı | KAP-32 |
| Karar 2 — MODULE_MANAGE yalnız modül ayarı | KAP-32 |
| Karar 3 — TEACHER_ACCOUNT_MANAGE izin değiştirme vermez | KAP-32 |
| Karar 4 — TEACHER_CLASS_ASSIGN kendi üzerinde kullanılamaz, aynı kurumla sınırlıdır | KAP-32 |
| Karar 5 — TEACHER_PERMISSION_VIEW değiştirme vermez | KAP-32 |
| Karar 6 — Hoca izin değiştirme mutlak sınırdır | KAP-32 |
| Karar 7 — DEVICE_SESSION_REVOKE bağımsızdır | KAP-32, KAP-31 |
| Karar 8 — CUSTOM_ATTENDANCE_STATUS_MANAGE yalnız özel durum ayarıdır | KAP-32 |
| Karar 9 — GUARDIAN_CONTACT_VIEW öğrenci görüntüleme/GUARDIAN_MANAGEden bağımsızdır | KAP-32 |

### 3.5. P-011 ve P-012 kullanılan kritik kurallar

| Sözleşme | Kural | KAP |
|---|---|---|
| P-011 | Undo atomikliği, UNMARKED, UNDO_ALREADY_APPLIED, ayrı RESTORE_ARCHIVED/AUDIT_UNDO, audit maskelemesi | KAP-18, KAP-29, KAP-35 |
| P-012 | Kapsam/snapshot/sourceCutoffAt, UNMARKED dışlama, rapor numarası, transfer, veli yetkisi, formül güvenliği, limit, iptal, süre/purge | KAP-30 |

## 4. Kalite kapıları

Bir KAP kimliğinin varlığı başarı değildir. Her kart uygulama görevi tarafından NOT_IMPLEMENTED, PASS, FAIL veya ACCEPTANCE_DEBT durumuyla izlenir. Otomasyon henüz yoksa durum NOT_IMPLEMENTED/ACCEPTANCE_DEBT olur; PASS olarak raporlanamaz.

SKIPPED, çalıştırılmamış, kanıtsız veya flaky/karantinadaki kritik test yayın kapısını geçiremez. Kritik test karantinada kaldığı sürece yayın engelidir. PASS için ilgili commit/build, platform, fixture sürümü, UTC zaman, otomasyon çıktısı ve gerektiğinde güvenli DB/audit/sync kanıtı saklanır.

| Kapı | Zorunlu durum |
|---|---|
| Özellik incelemesi | İlgili KAP'ların PASS kanıtı veya açık ACCEPTANCE_DEBT kaydı; yetkisiz/hata/boş/yükleniyor durumları uygulanmış olmalı |
| Dalga 2-3 | KAP-01–KAP-08, KAP-31–KAP-33 ilgili uygulama karşılıkları PASS |
| Dalga 4 pilot | KAP-09–KAP-25, KAP-34–KAP-35 ilgili karşılıkları PASS; KAP-12, KAP-13, KAP-17, KAP-20 iki oturumlu kanıtla PASS |
| Dalga 7/yayın | Geçerli tüm KAP'lar PASS; KAP-30 dosya doğrulaması ve iOS/Android gerçek cihaz kanıtı PASS; kritik/yüksek açık hata yok |

## 5. Kapsam dışı

Test frameworkü, CI komutları, cihaz çiftliği, hata enjeksiyon aracı, performans eşikleri, endpoint şemaları ve saklama sürelerinin sayısal değerleri ilgili teknik karar/uygulama görevlerinde seçilecektir. Bu seçimler KAP sonuçlarını veya sözleşme kurallarını zayıflatamaz. V1 sonrası veli/öğrenci girişi, bildirim ve diğer yüzeyler bu planın kapsamı dışındadır.
