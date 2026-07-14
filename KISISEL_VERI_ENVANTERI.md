# Kişisel ve Hassas Veri Envanteri

| Alan | Değer |
|---|---|
| Görev | P-004 — Kişisel ve hassas veri envanterini çıkar |
| Belge sürümü | 1.0 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Terim kaynağı | `TERIMLER_SOZLUGU.md` |
| Son güncelleme | 13 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, `URUN_VE_UYGULAMA_PLANI.md` içinde tanımlanan ilk sürüm kapsamında hangi kişisel veri
öğelerinin toplandığını, hangi varlığa ait olduğunu, ürün içi risk sınıflamasında hangi seviyeye
girdiğini ve ana planda bu öğeler için zaten tanımlanmış erişim/görünürlük ve saklama kısıtlarını
tek bir yerde listeler. Bu sınıflama hukuki bir KVKK değerlendirmesi değildir (bkz. bölüm 2).

Bu belge:

- Yeni bir ürün veya güvenlik kararı almaz; yalnızca ana planda zaten onaylanmış ilkeleri
  (bölüm 3.5 Güvenlik ve mahremiyet, bölüm 7 Kişi/öğrenci/veli modeli, bölüm 14 Silme ve
  arşivleme, bölüm 15 Güvenlik gereksinimleri, bölüm 8.10 Denetim ve geri alma) veri öğesi
  seviyesinde somutlaştırır.
- Terimler sözlüğündeki (`TERIMLER_SOZLUGU.md`) tercih edilen ad ve tanımlarla tutarlıdır.
- Rol bazlı ayrıntılı erişim matrisini **tanımlamaz**; bu iş `P-003 — Ayrıntılı yetki matrisi`
  görevinin kapsamındadır (bkz. bölüm 5 Kapsam dışı).
- Veritabanı şeması, alan tipi veya kolon adı kesinleştirmez; bağlayıcı şema kararı
  `P-008 — Çekirdek veri modeli taslağı` görevine aittir.

---

## 2. Hassasiyet kategorileri — ürün içi risk sınıflaması

**Önemli uyarı:** Bu bölümdeki kategoriler bir **hukuki/KVKK sınıflandırması değildir**. Bunlar
yalnızca ürün içi güvenlik ve erişim kontrolü tasarımına girdi sağlamak için kullanılan, teknik
risk seviyesine dayalı **ürün içi çalışma sınıflarıdır**. Bu belge KVKK anlamında "özel nitelikli
kişisel veri" tespiti yapmaz ve resmi bir KVKK uyum analizi teşkil etmez; böyle bir analiz ayrı,
hukuki uzmanlık gerektiren bir çalışmadır (bkz. bölüm 5 Kapsam dışı).

Envanterde her veri öğesi aşağıdaki üç ürün içi risk sınıfından birine atanır:

- **Temel kişisel veri:** Kimliği doğrudan veya kolayca belirleyen, düşük ek risk taşıyan veri
  (ör. ad, soyad).
- **Yüksek riskli kişisel veri (ürün içi sınıf):** Kötüye kullanımı, ayrımcılığı veya mahremiyet
  ihlalini kolaylaştırabilecek veri; çocuğa ait veri, sağlıkla dolaylı ilişki kurulabilecek veri,
  serbest metin not, kimlik doğrulama sırrı ve adres bu sınıftadır. Bu etiket, ilgili verinin
  KVKK'da tanımlı "özel nitelikli kişisel veri" kategorisine girdiği anlamına **gelmez**; yalnızca
  ürün içinde daha sıkı erişim/loglama dikkati gerektirdiğini işaret eder.
- **Teknik/işlem verisi:** Doğrudan kimliği tanımlamasa da kullanıcı davranışını ve erişimini
  izlemeye yarayan veri (ör. cihaz/istek bağlamı, denetim kaydı alanları).

Telefon, adres, doğum tarihi ve sıradan profil fotoğrafı bu envanterde "yüksek riskli kişisel
veri (ürün içi sınıf)" olarak işaretlenmiştir; ancak bu, bunların KVKK anlamında otomatik olarak
"özel nitelikli kişisel veri" (ırk, etnik köken, sağlık, din, biyometrik veri vb.) sayıldığı
anlamına gelmez. Özel nitelikli veri tespiti profesyonel bir KVKK değerlendirmesinin konusudur.

Ayrıca iki özel durum bu envanterde ayrıca not edilmiştir:

- **"Hasta" yoklama durumu**, seçildiğinde dolaylı olarak sağlık verisi çıkarımına yol açabileceği
  için ayrı ve dikkatli bir hukuki/ürün değerlendirmesi gerektirir (bkz. tablo satır 12).
- **Kur'an kursuna katılımın kendisi ve dini eğitim ilerlemesi** (ör. ezber/sûre takibi), doğası
  gereği bir kişinin din/inanç bilgisiyle ilişkilendirilebilecek bir çıkarıma yol açabilir. Bu
  çıkarımın KVKK anlamında nasıl sınıflandırılacağı bu belgenin kapsamında değildir; profesyonel
  KVKK değerlendirmesine bırakılmıştır (bkz. tablo satır 13 ve bölüm 5 Kapsam dışı).

Bir öğrencinin **reşit olmayan birey** olabileceği ve doğum tarihi, adres gibi alanların onun için
ayrıca yüksek riskli kabul edilmesi, ana planın 7.2 ve 15. bölümlerindeki mahremiyet ilkesinden
yapılan bir **çıkarımdır**; ana planda "reşit olmama" ifadesi doğrudan geçmez.

---

## 3. Veri envanteri tablosu

Etiket kuralı: her hücrede parantez içi etiket, ifadenin kaynağını gösterir — **[Karar]** ana
planda doğrudan yazılı bir hüküm, **[Çıkarım]** ana plandan mantıksal olarak türetilmiş bir
yorum, **[Öneri/bekleyen karar]** ana planda henüz kesinleşmemiş, ileride başka bir görev/ADR'de
karara bağlanacak konudur.

| # | Veri öğesi | Ait olduğu terim/varlık (bkz. `TERIMLER_SOZLUGU.md`) | Kategori (ürün içi risk sınıfı) | Hassasiyet gerekçesi | Ana planda tanımlı erişim/görünürlük kısıtı | Saklama / silme yaklaşımı |
|---|---|---|---|---|---|---|
| 1 | Ad, soyad | Kişi (`person`) | Temel kişisel veri | Doğrudan kimlik bilgisi. [Karar] | Kurum izolasyonu zorunlu (bölüm 3.5, 15); yalnızca kimliği doğrulanmış ve yetkili kullanıcı görebilir. [Karar] | Kişi kaydının kendisi için ana planın bölüm 14'ünde ayrı bir fiziksel silinmeme garantisi yazılı değildir (bölüm 14 yalnızca öğrenci, sınıf, program, kurum ve kullanıcıyı sayar). İlişkili kayıtlar (ör. öğrenci) üzerinden dolaylı olarak korunabilir; kişi kaydının kesin yaşam döngüsü ve kalıcı silme prosedürü ayrıca kesinleşecektir. [Öneri/bekleyen karar] |
| 2 | Telefon | Kişi / Anne / Baba (`person`) | Yüksek riskli kişisel veri (ürün içi sınıf; iletişim bilgisi) | Üçüncü kişilere (veli) ait iletişim verisi; istenmeyen erişimde doğrudan temasa yol açabilir. Bu etiket KVKK anlamında özel nitelikli veri sayıldığı anlamına gelmez. [Çıkarım] | Ana planın 5.5 bölümü "Veli iletişim bilgisi görüntüleme" işlemini ayrı, açıkça yetkilendirilebilir bir izin olarak tanımlar. [Karar] | Kişi kaydının kesin saklama/silme politikası ana planda ayrıca yazılı değildir; ilişkili kayıt yaşam döngüsü ve kalıcı silme prosedüründe kesinleşecektir. [Öneri/bekleyen karar] |
| 3 | Profil fotoğrafı | Kişi (`person`) | Yüksek riskli kişisel veri (ürün içi sınıf; görsel kimlik) | Kişiyi doğrudan görsel olarak teşhis eder. Bu etiket KVKK anlamında özel nitelikli veri sayıldığı anlamına gelmez. [Çıkarım] | Ana planın bölüm 15'teki PDF kuralından ("PDF dosyaları herkese açık kalıcı URL ile sunulmamalıdır") türetilmiş bir güvenlik **önerisidir**: profil fotoğrafına da süreli/güvenli bağlantıyla erişim uygulanması önerilir. Bu, onaylanmış genel bir dosya politikası değildir; kesin karar `A-007 — PDF/dosya depolama ADR'si` görevine bırakılmıştır. [Öneri/bekleyen karar] | Kişi kaydının kesin saklama/silme politikası ana planda ayrıca yazılı değildir; ilişkili kayıt yaşam döngüsü ve kalıcı silme prosedüründe kesinleşecektir. [Öneri/bekleyen karar] |
| 4 | Doğum tarihi | Kişi / Öğrenci (`person`, `student`) | Yüksek riskli kişisel veri (ürün içi sınıf; olası çocuk verisi) | Öğrencinin reşit olmayan birey olabileceği ana planın 7.2 bölümündeki öğrenci/veli ele alışından çıkarılmıştır; bu bir ana plan hükmü değil, yorumdur. Bu etiket KVKK anlamında özel nitelikli veri sayıldığı anlamına gelmez. [Çıkarım] | Genel kurum izolasyonu ve rol bazlı erişim kısıtları uygulanır; ayrıntılı izin `P-003` kapsamındadır. [Karar] | Kişi kaydının kesin saklama/silme politikası ana planda ayrıca yazılı değildir; ilişkili kayıt yaşam döngüsü ve kalıcı silme prosedüründe kesinleşecektir. [Öneri/bekleyen karar] |
| 5 | Adres | Kişi (`person`) | Yüksek riskli kişisel veri (ürün içi sınıf) | Konum bilgisi; kötüye kullanımda fiziksel güvenlik riski oluşturabilir. Bu etiket KVKK anlamında özel nitelikli veri sayıldığı anlamına gelmez. [Çıkarım] | Genel kurum izolasyonu ve rol bazlı erişim kısıtları uygulanır. [Karar] | Kişi kaydının kesin saklama/silme politikası ana planda ayrıca yazılı değildir; ilişkili kayıt yaşam döngüsü ve kalıcı silme prosedüründe kesinleşecektir. [Öneri/bekleyen karar] |
| 6 | Okul | Kişi (`person`) | Temel kişisel veri | Ek bağlam bilgisi, doğrudan kimlik değil ama diğer alanlarla birleşince teşhis riskini artırır. [Çıkarım] | Genel kurum izolasyonu uygulanır. [Karar] | Kişi kaydının kesin saklama/silme politikası ana planda ayrıca yazılı değildir; ilişkili kayıt yaşam döngüsü ve kalıcı silme prosedüründe kesinleşecektir. [Öneri/bekleyen karar] |
| 7 | Açıklama/not (kişi serbest metin alanı) | Kişi (`person`) | Yüksek riskli kişisel veri (ürün içi sınıf; serbest metin) | Serbest metin olduğu için içeriği önceden sınırlanamaz; sağlık, aile durumu gibi ek hassas bilgi içerebilir. [Çıkarım] | Genel kurum izolasyonu ve rol bazlı erişim kısıtları uygulanır; ayrıca bölüm 3.5 "hassas veriler loglara ve hata mesajlarına gereksiz yere yazılmamalı" ilkesi bu alan için özellikle geçerlidir. [Karar] | Kişi kaydının kesin saklama/silme politikası ana planda ayrıca yazılı değildir; ilişkili kayıt yaşam döngüsü ve kalıcı silme prosedüründe kesinleşecektir. [Öneri/bekleyen karar] |
| 8 | Kuruma özel tanımlanmış kişi/öğrenci alanları | Kişi / Öğrenci (kurum özel alan) | Belirlenemez (kurum tanımına bağlı) | Ana plan bu alanların türünü (kısa metin, sayı, tarih, evet/hayır, seçim vb.) tanımlar ancak içeriği kuruma bırakır (bölüm 9.3); hassasiyet kurumun seçtiği alana göre değişir. [Çıkarım] | Ana plan, bu alanların da çekirdek kurum izolasyonu ve yetki kontrolüne tabi olduğunu belirtir; alan bazlı hassasiyet etiketleme mekanizması ana planda tanımlı değildir. [Karar + Öneri/bekleyen karar] | Öğrenciye bağlıysa öğrenci kaydının bölüm 14'teki fiziksel silinmeme garantisinden dolaylı yararlanabilir [Çıkarım]; kişiye bağlıysa kesin politika ayrıca yazılı değildir. [Öneri/bekleyen karar] |
| 9 | Kullanıcı adı | Kullanıcı (`user`) | Temel kişisel veri | Kimlik doğrulama girdisidir, doğrudan hesaba işaret eder. [Karar] | Kimlik doğrulama zorunludur (bölüm 3.5, 11.5). [Karar] | Kullanıcı, ana planın bölüm 14'ünde normal arayüzden fiziksel silinmeyeceği açıkça belirtilen beş varlıktan biridir. [Karar] |
| 10 | Parola | Kullanıcı (`user`) | Yüksek riskli kişisel veri (ürün içi sınıf; kimlik doğrulama sırrı) | Ele geçirilirse hesabın tamamına erişim sağlar. [Karar] | Ana plan açıkça yasaklar: "Parolalar düz metin olarak saklanamaz" (bölüm 15); mobil cihazda da açık biçimde tutulamaz (bölüm 8.1, 3.4). Buna göre saklanan değer parolanın kendisi değil, geri döndürülemez güvenli bir özet/hash'idir; kesin özetleme algoritması ve parametreleri ilgili kimlik doğrulama ADR'sinde (`A-004`) kesinleştirilecektir. [Karar + Öneri/bekleyen karar] | Uygulanamaz — parolanın kendisi hiçbir biçimde saklanmaz, yalnızca güvenli özeti tutulur; özetin saklama/rotasyon detayı `A-004` ADR'sine bırakılmıştır. [Öneri/bekleyen karar] |
| 11 | Erişim/yenileme belirteci (token) ve güvenilir cihaz bilgisi | Kullanıcı oturumu (IAM) | Yüksek riskli kişisel veri (ürün içi sınıf) / teknik veri | Ele geçirilirse hesap oturumunun ele geçirilmesine yol açar. [Karar] | Erişim belirteçleri kısa ömürlü, yenileme belirteçleri iptal edilebilir olmalıdır (bölüm 15); yönetici kullanıcının bütün cihaz oturumlarını kapatabilmelidir (bölüm 8.1). Oturumların iptal edilebilir olması ana planda açık bir hükümdür. [Karar] | Token sırrının veritabanında hangi biçimde tutulacağı (ör. güvenli hash/özet veya sağlayıcıya özgü saklama yöntemi) ana planda kesinleşmemiştir; "sırrın kendisi saklanmaz" ifadesi bir karar değil, önerilen bir yaklaşımdır. Kesin karar `A-004 — Kimlik doğrulama sağlayıcısı ADR'si` görevine bırakılmıştır. [Öneri/bekleyen karar] |
| 12 | Yoklama kaydı durumu (özellikle kuruma özel "Hasta" gibi durumlar) | Yoklama kaydı (`attendance_record`) | Yüksek riskli kişisel veri (ürün içi sınıf; olası sağlık verisi çıkarımı) | Kurum "Hasta" gibi isteğe bağlı bir durum tanımlarsa (bölüm 8.5), bu durum dolaylı olarak sağlık verisi çıkarımına yol açabilir ve bu nedenle ayrı, dikkatli bir hukuki/ürün değerlendirmesi gerektirir (bkz. bölüm 2). Bu değerlendirme bu belgenin kapsamında yapılmamıştır. [Çıkarım + Öneri/bekleyen karar] | Genel kurum izolasyonu ve yoklama alma/düzeltme yetkisi (bölüm 5.5) uygulanır; her değişiklikte eski değer, yeni değer ve değiştiren kullanıcı kaydedilir (bölüm 8.5, 8.10). [Karar] | Değişiklik geçmişinin denetim kaydında tutulması ve geri almanın desteklenmesi ana planda açık bir hükümdür (bölüm 8.10). [Karar] Yoklama kaydının kendisinin fiziksel olarak silinip silinmeyeceğine dair ayrı bir garanti ana planda yazılı değildir; kesin saklama politikası henüz belirlenmemiştir. [Öneri/bekleyen karar] |
| 13 | İlerleme/değerlendirme kaydı (puan, öğretmen notu, tekrar gerekli) | İlerleme (`progress_record`) | Yüksek riskli kişisel veri (ürün içi sınıf; eğitim performans verisi) | Öğrencinin eğitim performansına dair değerlendirme içerir; öğretmen notu serbest metin olduğundan ek bilgi taşıyabilir. Ayrıca Kur'an kursu bağlamında dini eğitim ilerlemesi (ör. ezber/sûre takibi), kişinin din/inanç bilgisiyle ilişkilendirilebilecek bir çıkarıma yol açabilir; bu çıkarımın KVKK anlamındaki hukuki sınıflandırması profesyonel bir KVKK değerlendirmesine bırakılmıştır (bkz. bölüm 2). [Çıkarım + Öneri/bekleyen karar] | "İlerleme kaydetme/düzeltme" ayrı bir yetki olarak tanımlıdır (bölüm 5.5); aynı sınıftaki hocalar normal öğretmen notlarını görebilir, özel not türü sonraki fazdadır (bölüm 5.3, 8.8). [Karar] | Program/ilerleme değişikliğinin denetim kaydına yazılması ana planda açık bir hükümdür (bölüm 8.10). [Karar] İlerleme kaydının kendisinin fiziksel olarak silinip silinmeyeceğine dair ayrı bir garanti ana planda yazılı değildir; kesin saklama politikası henüz belirlenmemiştir. [Öneri/bekleyen karar] |
| 14 | Denetim kaydı alanları (kullanıcı, hedef kayıt, eski/yeni değer, istek/cihaz bağlamı) | Denetim kaydı (`audit_log`) | Teknik/işlem verisi (dolaylı kişisel veri) | Geçmişte hangi kullanıcının hangi kişisel veriyi değiştirdiğini gösterir; kendisi de kişisel veri içerir. [Çıkarım] | Yalnızca yetkili roller görebilir ve normal uygulama akışında kayıt değiştirilemez/silinemez niteliktedir (`TERIMLER_SOZLUGU.md` bölüm 2); "işlem geçmişi görüntüleme" ayrı bir yetkidir (bölüm 5.5). [Karar] | Normal uygulama akışında denetim kaydı değiştirilemez ve silinmez; bu, uygulama davranışına ilişkin bir tasarım kararıdır. [Karar] Bu ifade, hukuki saklama/imha süresinin sonsuz olduğu anlamına **gelmez**; hukuki saklama süresi ayrı bir değerlendirme konusudur ve yalnızca ayrı, denetlenen kişisel veri silme prosedürü kapsamında ele alınabilir (bölüm 14). [Öneri/bekleyen karar] |
| 15 | Platform yöneticisinin kurum verisine erişim izi | Denetim kaydı (`audit_log`) | Teknik/işlem verisi | Platform yöneticisinin istisnai geniş erişiminin kötüye kullanılmadığını denetlemek için gereklidir. [Karar] | Bu erişimin denetim kaydı üretmesi ana planın açık hükmüdür (bölüm 5.1: "Platform yöneticisinin kurum verisine erişimi denetim kaydı üretir"; bölüm 8.10). [Karar] Bu akışın ayrıca test edilmesi gerektiği yönündeki öneri henüz ayrı bir test görevine bağlanmamıştır. [Öneri/bekleyen karar] | Normal uygulama akışında denetim kaydı olarak saklanır ve değiştirilemez. [Karar] Hukuki saklama süresinin sonsuz olduğu iddia edilmez; bu ayrı bir değerlendirme konusudur (bölüm 14). [Öneri/bekleyen karar] |
| 16 | Excel/rapor dışa aktarımı (öğrenci listesi, veli iletişim bilgisi, yoklama, ilerleme, dönem özeti) | Rapor/dışa aktarma (EXPORT modülü) | Yüksek riskli kişisel veri (ürün içi sınıf; toplu dışa aktarım) | Birden fazla kişisel veri öğesini tek dosyada birleştirdiği için tekil kayda göre daha yüksek risk taşır. [Çıkarım] | "Rapor dışa aktarma" ayrı bir yetkidir (bölüm 5.5) ve işlem denetim kaydı üretir (bölüm 8.10); dosya sunucuda üretilip güvenli, süreli indirme bağlantısıyla sunulmalıdır (bölüm 8.9). [Karar] | Üretilen dosyanın saklanma/erişim süresi ana planda kesinleştirilmemiştir (bkz. bölüm 6 Bilinen sınırlamalar). [Öneri/bekleyen karar] |
| 17 | Kurum adı, logo, renkler | Kurum (`organization`) | Temel kişisel olmayan (kurumsal) veri | Bireyi değil kurumu tanımlar; düşük mahremiyet riski taşır. [Çıkarım] | Yalnızca kurum yöneticisi/platform yöneticisi değiştirebilir (bölüm 5.1, 5.2, 9.1). [Karar] | Kurum, ana planın bölüm 14'ünde normal arayüzden fiziksel silinmeyeceği açıkça belirtilen beş varlıktan biridir. [Karar] |

---

## 4. Ana plandaki genel ilkelerle çapraz kontrol

- Bütün satırlar bölüm 3'teki "Kurum izolasyonu otomatik testlerle doğrulanmalıdır" ve "Bir kurum
  başka kurumun verisini hiçbir sorguda görememelidir" ilkesine (`URUN_VE_UYGULAMA_PLANI.md`
  bölüm 3.5, 15) tabidir; bu envanterde ayrıca tekrarlanmamıştır, her satırda örtük kabul edilir.
- Ana planın bölüm 14'ü (Silme, arşivleme ve veri yaşam döngüsü) yalnızca **öğrenci, sınıf,
  program, kurum ve kullanıcı** için normal arayüzden fiziksel silinmeyeceğini açıkça
  kesinleştirir. Bu envanterde yalnızca bu beş varlığa doğrudan bağlı satırlar (9, 17 ve dolaylı
  olarak 8'in öğrenciye bağlı hâli) `[Karar]` olarak işaretlenmiştir. Kişi, anne/baba, yoklama
  kaydı ve ilerleme kaydı gibi ana planda ayrıca adı geçmeyen varlıklar için kesin saklama/silme
  garantisi verilmemiş, `[Öneri/bekleyen karar]` olarak bırakılmıştır.
- Denetim kaydının normal uygulama akışında değiştirilemez/silinemez olması ile bu kaydın hukuki
  saklama/imha süresinin ne olacağı bu belgede bilinçli olarak ayrıştırılmıştır (satır 14, 15);
  ilki bir tasarım kararıdır, ikincisi ayrı bir hukuki değerlendirme konusudur.
- Denetim kaydı gerektiren işlemler (satır 12, 13, 14, 15, 16) bölüm 8.10'daki zorunlu denetim
  listesiyle uyumludur.
- Bu belgede ana plana veya `TERIMLER_SOZLUGU.md`'ye aykırı bir tanım bulunmamaktadır.

---

## 5. Kapsam dışı

- Rol/işlem bazlı ayrıntılı erişim matrisi ve izin kombinasyonları `YETKI_MATRISI.md`de
  tanımlıdır.
- Veritabanı şeması, kolon adı, alan tipi ve kısıtlar `VERI_MODELI.md`de tanımlıdır.
- Kuruma özel tanımlanmış alanların (satır 8) içerik bazında hassasiyet etiketleme mekanizması;
  ana planda bu mekanizma tanımlı değildir, ayrı bir ürün kararı gerektirir.
- Kişisel verinin kalıcı silinmesi prosedürünün adım adım tasarımı (ana plan bunun "ayrı ve
  denetlenen bir prosedür" olacağını belirtir ama prosedürün kendisini tanımlamaz; bu envanterin
  kapsamında değildir).
- KVKK/GDPR gibi belirli bir mevzuata resmi uyum değerlendirmesi; bu belge yalnızca ana planda
  zaten onaylanmış ilkeleri veri öğesi seviyesinde listeler, hukuki uyum analizi yapmaz veya
  yaptığını iddia etmez.
- KVKK anlamında "özel nitelikli kişisel veri" tespiti ve sınıflandırması (ör. "Hasta" yoklama
  durumunun sağlık verisi sayılıp sayılmayacağı, dini eğitim ilerlemesinin din/inanç verisi
  çıkarımı doğurup doğurmadığı); bu envanterdeki "yüksek riskli kişisel veri" etiketi yalnızca
  ürün içi bir risk sınıfıdır ve bu hukuki tespiti yapmaz (bkz. bölüm 2).

---

## 6. Bilinen sınırlamalar

- Excel/rapor dosyalarının (satır 16) sunucuda ne kadar süre saklanacağı veya indirme
  bağlantısının geçerlilik süresi ana planda sayısal olarak belirtilmemiştir; yalnızca "güvenli,
  süreli" ifadesi kullanılır (bölüm 8.9). Kesin süre `EXPORT-006` görevinde netleştirilmelidir.
- Kuruma özel tanımlanmış alanların (satır 8) hangi somut örneklerinin hangi kurumlarca
  kullanılacağı bu aşamada bilinmemektedir; hassasiyet ataması bu nedenle örnek bazında değil,
  alan türü bazında genel tutulmuştur.
- Eski Excel/HTML/Apps Script dosyaları bu repoda bulunmadığından, bu dosyalarda hangi ek kişisel
  veri alanlarının tutulduğu tespit edilememiştir; envanter yalnızca `URUN_VE_UYGULAMA_PLANI.md`
  içinde onaylanmış ilk sürüm kapsamına dayanır.
- Kişi, anne/baba, profil fotoğrafı, adres, kuruma özel alan, yoklama kaydı ve ilerleme kaydı
  için kesin saklama süresi veya fiziksel silinmeme garantisi ana planda ayrıca yazılı değildir
  (bölüm 14 yalnızca öğrenci, sınıf, program, kurum ve kullanıcıyı kapsar). Bu envanter bu
  boşluğu doldurmaz; kesin politika ilgili kayıt yaşam döngüsü tasarımında veya kalıcı silme
  prosedüründe kesinleşecektir.
- Bu envanterdeki "yüksek riskli kişisel veri" etiketi resmi bir KVKK özel nitelikli veri tespiti
  değildir; hukuki sınıflandırma profesyonel bir değerlendirme gerektirir (bkz. bölüm 2 ve 5).

---

## 7. Varsayımlar

- Öğrencinin reşit olmayan birey olması ve bu nedenle doğum tarihi/adres gibi alanların onun
  için ayrıca yüksek riskli (ürün içi sınıf) kabul edilmesi, ana planın öğrenciyi "veli" ile
  temsil edilen bir kişi olarak ele almasından (bölüm 7.3, 5.4) çıkarılan bir yorumdur; bu bir
  KVKK sınıflandırması değildir. Ana planda "reşit olmama" ifadesi doğrudan geçmez.
- "Hasta" yoklama durumunun sağlıkla dolaylı ilişkili kabul edilmesi, ana planın bu durumu yalnızca
  isteğe bağlı bir örnek olarak sayması nedeniyle (bölüm 8.5, "Geç geldi, izinli ve hasta gibi
  durumları kurum yöneticisi isteğe bağlı tanımlar") temkinli bir yorumdur; kurum bu durumu hiç
  kullanmayabilir.

---

## 8. Sonraki hazır olabilecek görevler

- `P-002 — Aktörleri ve ana kullanım senaryolarını listele`: `DONE` (bu görevden bağımsız olarak
  zaten tamamlandı ve merge edildi).
- `P-003 — Ayrıntılı yetki matrisi`: PR #4 üzerinde `REVIEW` durumundadır, `main`'e merge
  beklemektedir.
- `P-008 — Çekirdek veri modeli taslağı`: hem `P-003` hem de bu görev (`P-004`) `main` dalına
  merge edilmeden `READY` olmaz. Güncel durum `GOREV_DURUMU.md`'den doğrulanmalıdır; bu belge
  bir görev panosu değildir ve panonun yerini almaz.
