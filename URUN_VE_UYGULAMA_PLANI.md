# Kur'an Kursu Eğitim ve Takip Platformu

## Ürün, Mimari ve Uygulama Planı

| Alan | Değer |
|---|---|
| Belge sürümü | 1.2 |
| Durum | Onaylanmış başlangıç sözleşmesi |
| İlk yayın hedefi | Yaklaşık 2–3 ay; kalite zaman baskısından önceliklidir |
| İlk sürüm kullanıcıları | Platform yöneticisi, kurum yöneticisi ve hocalar |
| İlk istemciler | iOS ve Android mobil uygulama |
| Sonraki istemciler | Web yönetim paneli, veli ve öğrenci uygulama deneyimi |
| Veri başlangıcı | Temiz başlangıç; eski Excel ve Google Sheets verileri yalnızca referanstır |
| Son güncelleme | 15 Temmuz 2026 |

---

## 1. Belgenin amacı ve bağlayıcılığı

Bu belge ürün geliştirme sürecinde izlenecek ana sözleşmedir. Ürün kapsamı, kullanıcı rolleri,
modül sınırları, veri modeli, güvenlik yaklaşımı, çevrimdışı çalışma, eşzamanlı kullanım,
fazlar ve kalite ölçütleri bu belgeye göre yönetilir.

Bu belgede:

- **Zorunludur** ifadesi, uygulanmadan ilgili fazın tamamlanamayacağını belirtir.
- **Önerilir** ifadesi, aksi için açık ve belgelenmiş bir karar gerektiğini belirtir.
- **Sonraki faz** ifadesi, ilk sürümün kapsamına alınmaması gereken işi belirtir.

Geliştirme sırasında yeni bir fikir doğrudan koda eklenmez. Önce bu belgenin “Karar ve
değişiklik yönetimi” bölümüne göre değerlendirilir. Onaylanan değişiklik ilgili kapsama,
fazlara ve kabul ölçütlerine işlenir.

---

## 2. Ürün vizyonu

Farklı Kur'an kurslarının kendi kurumlarını, sınıflarını, hocalarını, öğrencilerini,
yoklama düzenlerini, ezber içeriklerini, eğitim programlarını ve değerlendirme yöntemlerini
yönetebildiği; iOS ve Android üzerinde çalışan; hızlı, sezgisel ve kişiselleştirilebilir bir
eğitim takip platformu oluşturulacaktır.

İlk sürüm hoca ve yöneticilerin günlük işlerini güvenilir biçimde yürütmesine odaklanacaktır.
Mimari, daha sonra öğrenci ve velilerin sisteme katılmasını destekleyecek şekilde en baştan
hazırlanacaktır.

### 2.1. Başarı tanımı

Ürün başarılı sayılırsa:

1. Bir kurum sisteme eklenebilir ve kendi görünümünü oluşturabilir.
2. Yönetici sınıf, hoca, öğrenci ve programları telefondan yönetebilir.
3. Birden fazla hoca aynı sınıfta eşzamanlı çalışabilir.
4. Yoklama ve ilerleme kayıtlarında sessiz veri kaybı yaşanmaz.
5. Kurumlar farklı ezber ve takip düzenlerini kod değişmeden kurabilir.
6. Kullanıcı yaptığı işlemin kaydedilip kaydedilmediğini açıkça anlayabilir.
7. Veriler kurumlar arasında kesin biçimde ayrılır.
8. İstenen tarih aralığı için Excel çıktısı alınabilir.
9. Yeni modüller mevcut modüller bozulmadan eklenebilir.

---

## 3. Değişmez ürün ilkeleri

### 3.1. Sezgisel kullanım

- Günlük ve sık kullanılan işlemler en az dokunuşla tamamlanmalıdır.
- Kullanıcıya teknik hata kodu değil, yapılabilecek bir sonraki hareket gösterilmelidir.
- Kaydetme, eşitleme ve hata durumları görünür olmalıdır.
- Toplu işlemler desteklenmeli, fakat geri alınabilir olmalıdır.
- Tehlikeli işlemler açık onay istemelidir.
- Arayüz yalnızca teknik olarak çalışan değil, gerçek telefonlarda rahat kullanılan bir yapı
  olmalıdır.

### 3.2. Modülerlik

- Her iş alanının açık bir modül sınırı olmalıdır.
- Bir modül başka modülün iç verisine doğrudan bağımlı olmamalıdır.
- Ortak davranışlar tanımlı uygulama servisleri ve sözleşmeler üzerinden kullanılmalıdır.
- İlk backend, sınırları belirgin bir **modüler monolit** olmalıdır.
- Erken aşamada mikroservis kullanılmamalıdır.

### 3.3. Kontrollü kişiselleştirme

- Kurumlar içeriklerini, takip yöntemlerini, menülerini, modüllerini ve görsel kimliğini
  değiştirebilmelidir.
- Öğrenci, yetki, yoklama ve denetim kaydı gibi çekirdek kavramlar tamamen serbest biçimli
  yapılmamalıdır.
- Esneklik, doğrulanmış ayarlar ve tanımlı şemalar üzerinden sağlanmalıdır.
- “Her şeyi tek bir JSON alanında tutma” veya tamamen dinamik EAV veri modeli ana yaklaşım
  olmamalıdır.

### 3.4. Güvenilirlik

- Sunucu onayı gelmeyen işlem başarılı gösterilmemelidir.
- Başarısız işlem kuyruktan silinmemelidir.
- Aynı isteğin tekrar gönderilmesi çift kayıt üretmemelidir.
- Kritik kayıtlar fiziksel olarak hemen silinmemeli, arşivlenmelidir.
- Her kritik değişiklik denetim geçmişine yazılmalıdır.

### 3.5. Güvenlik ve mahremiyet

- Her kullanıcı kimliği doğrulanmış olarak işlem yapmalıdır.
- Yetki kontrolü yalnızca arayüzde değil sunucuda da uygulanmalıdır.
- Bir kurum başka kurumun verisini hiçbir sorguda görememelidir.
- Veli ve öğrenci erişimi geldiğinde yalnızca ilişkili ve izin verilen veriler gösterilmelidir.
- Hassas veriler loglara ve hata mesajlarına gereksiz yere yazılmamalıdır.

### 3.6. Kademeli maliyet ve operasyon

- Güvenlik, kurum izolasyonu, veri bütünlüğü ve geri yüklenebilirlik maliyet azaltmak için
  kaldırılmaz; pahalı kapasite, yüksek erişilebilirlik ve sürekli alt ortamlar ölçülmüş ihtiyaca
  kadar ertelenebilir.
- Yerel geliştirme ve yalnız sentetik veri kullanan kapalı alfa için dış ödeme hedefi
  `0 USD/ay`dır; bu bir fiyat garantisi veya hizmet seviyesi taahhüdü değildir.
- Gerçek öğrenci verisi kullanan tek kurum pilotu, kullanıcı sayısı az olsa bile production
  verisidir. Hedef başlangıç bütçesi `25–60 USD/ay`dır; yedek, geri yükleme ve erişim sınırları
  kapatılmadan yalnız ücretsiz kota gerekçesiyle açılmaz.
- PITR, sürekli staging, ücretli ekip yönetişimi, yüksek erişilebilirlik ve çapraz hesap/bölge
  yedeği ancak kabul edilmiş RPO/RTO, sözleşmeli erişilebilirlik veya ölçülmüş kapasite ihtiyacı
  oluştuğunda etkinleştirilir.
- Öğrenci kredileri development, kapalı alfa veya geçici doğrulamada kullanılabilir; production
  sahipliği, alan adı, repo ve veri kurtarma imkânı kişisel öğrenci hesabına kalıcı bağımlı
  olamaz.
- Sağlayıcı bağımlılığı standart Docker imajı, PostgreSQL/SQL migration, sağlayıcıdan bağımsız
  uygulama sınırları ve düzenli dışa aktarma/geri yükleme kanıtıyla sınırlandırılır.

---

## 4. Kesinleşen ürün kararları

### 4.1. Yayın ve veri başlangıcı

- Mevcut Fındıklı veya diğer sınıf siteleri yeni ürün olarak yayına alınmayacaktır.
- Yeni platform temiz veriyle başlayacaktır.
- Google Sheets yeni sistemin veri kaynağı olmayacaktır.
- Mevcut Excel, HTML ve Apps Script dosyaları yalnızca iş akışı ve kullanıcı deneyimi
  referansı olarak kullanılacaktır.
- Sistem istenildiğinde Excel biçiminde çıktı üretecektir.

### 4.2. Çok kurumlu yapı

- Platform birden fazla bağımsız Kur'an kursunu destekleyecektir.
- Her kurumun kullanıcıları, sınıfları, öğrencileri, içerikleri ve ayarları birbirinden
  ayrılacaktır.
- Mimari üç kurum edinme yöntemini destekleyecektir:
  1. Platform yöneticisinin kurum oluşturması,
  2. Kurs yöneticisinin kayıt olması,
  3. Davet kodu veya davet bağlantısı.
- İlk sürümde yalnızca platform yöneticisi kurum oluşturacaktır.
- Diğer iki yöntem sonraki fazlarda etkinleştirilecektir.

### 4.3. İlk sürüm kullanıcıları

- İlk sürüm platform yöneticisi, kurum yöneticisi ve hocalara yöneliktir.
- Veli ve öğrenci girişleri sonraki fazdadır.
- Veli ve öğrenci hesapları sonraki fazda eklenecek olsa da veri modeli buna en baştan hazır
  olacaktır.

### 4.4. Mobil ve web önceliği

- İlk ürün iOS ve Android uygulaması olarak geliştirilecektir.
- Kurum yöneticileri ilk sürümde yönetim işlemlerini mobil uygulamadan yapacaktır.
- Kapsamlı web yönetim paneli ikinci veya üçüncü ana fazda geliştirilecektir.

---

## 5. Kullanıcı rolleri ve yetki modeli

Yetki modeli rol, kurum, sınıf ve işlem bağlamını birlikte değerlendirecektir. Arayüzde bir
butonun gizlenmesi güvenlik sayılmaz; bütün izinler backend tarafından doğrulanmalıdır.

### 5.1. Platform yöneticisi

İlk platform yöneticisi ürün sahibidir ve:

- Bütün kurumlara erişebilir.
- Bütün sınıfları ve kullanıcıları görebilir.
- Kurum oluşturabilir, etkinleştirebilir, askıya alabilir ve arşivleyebilir.
- Kurum yöneticisi atayabilir.
- Destek amacıyla kurum bağlamına geçebilir.
- Sistem genelindeki denetim kayıtlarına erişebilir.
- Kurumların verilerini birbirine karıştırmadan raporlayabilir.

Platform yöneticisinin kurum verisine erişimi ayrıca denetim kaydına yazılmalıdır.

### 5.2. Kurum yöneticisi

Kurum yöneticisi kendi kurumu içinde:

- Kurum adı, logo ve renklerini yönetebilir.
- Hoca hesabı oluşturabilir ve kapatabilir.
- Hocaların görebileceği sınıfları belirleyebilir.
- Hocalara ayrıntılı yetkiler verebilir veya geri alabilir.
- Sınıf, öğrenci, program, takvim ve değerlendirme düzenini yönetebilir.
- Kurum raporlarını ve işlem geçmişini görebilir.
- Arşivlenmiş kayıtları geri yükleyebilir.

### 5.3. Hoca

- Bir hoca birden fazla sınıfa atanabilir.
- Bir sınıfa birden fazla hoca atanabilir.
- Hoca yalnızca yetkili olduğu sınıfları görebilir.
- Varsayılan olarak başka hoca oluşturamaz veya yetkilendiremez.
- Yönetici isterse öğrenci, program veya sınıf yönetimi gibi belirli izinleri hocaya verebilir.
- Hoca kendi sınıflarındaki diğer hocaların öğrenciye yazdığı normal notları görebilir.
- Gelecekte yalnızca yöneticilerin görebildiği özel not türü eklenebilir; ilk sürümde zorunlu
  değildir.

### 5.4. Gelecekteki roller

- Öğrenci
- Anne
- Baba
- Gerekirse vasi

Bu roller ilk sürümde giriş yapmayacak, ancak kişi ve ilişki modeli ileride hesap açmalarına
uygun olacaktır.

### 5.5. Yetki örnekleri

Yetkiler en az aşağıdaki eylemleri ayrı ayrı kontrol edebilmelidir:

- Sınıf görüntüleme
- Sınıf oluşturma/düzenleme/arşivleme
- Öğrenci görüntüleme
- Öğrenci oluşturma/düzenleme/arşivleme
- Veli iletişim bilgisi görüntüleme
- Yoklama alma/düzeltme
- Program görüntüleme/yönetme
- İlerleme kaydetme/düzeltme
- Rapor dışa aktarma
- İşlem geçmişi görüntüleme
- Kullanıcı ve yetki yönetme
- Kurum ayarlarını değiştirme

Varsayılan politika “izin verilmediyse erişim yok” olmalıdır.

---

## 6. Kurum, sınıf ve üyelik modeli

### 6.1. Kurum

Her kurum en az şu alanlara sahip olacaktır:

- Benzersiz kimlik
- Kurum adı
- Kısa ad
- Logo
- Ana renk ve yardımcı renkler
- Durum: aktif, askıda, arşivlenmiş
- Varsayılan saat dilimi
- Oluşturulma ve güncellenme bilgileri
- Etkin modüller
- Kurum ayarları

İlk sürümde varsayılan saat dilimi `Europe/Istanbul` olacaktır.

### 6.2. Eğitim dönemi

- Kurum birden fazla eğitim dönemi tanımlayabilir.
- Dönem başlangıç ve bitiş tarihine sahip olur.
- Tatiller ve çalışma yapılmayacak günler tanımlanabilir.
- Geçmiş dönemler arşivlenebilir ve raporlanabilir.

### 6.3. Sınıf

- Sınıf bir kuruma ve eğitim dönemine bağlıdır.
- Bir öğrenci ilk sürümde aynı kurum içinde aynı anda yalnızca bir aktif sınıfta bulunabilir.
- Bir hoca birden fazla sınıfta görev alabilir.
- Bir sınıfta birden fazla hoca bulunabilir.
- Gerekirse ana hoca bilgisi tutulabilir; yardımcı hocalar aynı sınıfa ayrıca atanabilir.
- Bir sınıf arşivlendiğinde geçmiş kayıtları korunur.

Bir öğrencinin eşzamanlı birden fazla gruba katılması sonraki faz kararıdır.

---

## 7. Kişi, öğrenci ve veli modeli

### 7.1. Ortak kişi kaydı

Hoca, yönetici, öğrenci ve veli kayıtları ortak bir kişi çekirdeğini paylaşmalıdır. Bu yapı
aynı kişinin gelecekte birden fazla role sahip olmasını kolaylaştırır.

Zorunlu çekirdek alanlar:

- Ad
- Soyad
- Telefon

Desteklenecek isteğe bağlı alanlar:

- Profil fotoğrafı
- Doğum tarihi
- Adres
- Okul
- Açıklama/not
- Kuruma özel tanımlanmış alanlar

Ad ve soyad arayüzde birlikte gösterilebilir; veritabanında ayrı tutulması önerilir.

### 7.2. Öğrenci kaydı

Öğrenci kaydı kişi bilgisine ek olarak:

- Kurum
- Aktif sınıf
- Eğitim dönemi
- Kayıt tarihi
- Durum: aktif, pasif, arşivlenmiş
- Kuruma özel öğrenci alanları

bilgilerini içerir.

Öğrenci silme işlemi ilk aşamada arşivleme olarak uygulanmalıdır. Fiziksel silme yalnızca
özel veri silme prosedürüyle yapılmalıdır.

### 7.3. Anne ve baba

- Anne ve baba ayrı kişi kayıtlarıdır.
- Öğrenciyle `anne` veya `baba` ilişkisi üzerinden bağlanırlar.
- Her biri için ad, soyad ve telefon tutulabilir.
- Anne ve baba bilgileri isteğe bağlıdır.
- İlk sürümde giriş hesapları bulunmaz.
- Veli modülü geldiğinde mevcut kişi kaydı kullanıcı hesabına bağlanır; veri yeniden
  oluşturulmaz.

---

## 8. Ana işlevsel modüller

### 8.1. Kimlik ve oturum modülü

- Kullanıcı bilgileri yönetici tarafından oluşturulur.
- İlk sürüm giriş yöntemi kullanıcı adı ve parola olabilir; kesin yöntem teknik tasarım
  fazında güvenlik ve kullanım kolaylığıyla birlikte kararlaştırılacaktır.
- Başarılı girişten sonra cihaz güvenilir cihaz olarak eşleştirilebilir.
- Kullanıcı çıkış yapana, uygulama kaldırılana, oturum süresi dolana veya yönetici cihaz
  erişimini iptal edene kadar tekrar bilgi sorulmayabilir.
- Güvenli yenileme belirteçleri kullanılmalı; parola cihazda açık biçimde tutulmamalıdır.
- Yönetici bir kullanıcının bütün cihaz oturumlarını kapatabilmelidir.
- İlk sürümde biyometri zorunlu değildir.

### 8.2. Kurum yönetimi modülü

- Platform yöneticisinin kurum oluşturması
- Kurum yöneticisi atama
- Kurum durumunu yönetme
- Logo ve renk yönetimi
- Etkin modülleri belirleme
- Kurum takvimini ve çalışma günlerini belirleme

### 8.3. Kullanıcı ve yetki modülü

- Hoca hesabı oluşturma
- Geçici giriş bilgisi verme
- Parola yenileme
- Sınıf atama
- Ayrıntılı izin yönetimi
- Hesabı askıya alma
- Güvenilir cihazları iptal etme

### 8.4. Öğrenci modülü

- Öğrenci ekleme
- Öğrenci bilgilerini düzenleme
- Anne ve baba bilgilerini yönetme
- Öğrenciyi sınıfa atama
- Öğrenciyi arşivleme ve geri yükleme
- Arama, sıralama ve filtreleme
- Kuruma özel öğrenci alanları

### 8.5. Yoklama modülü

- Her sınıf için günde bir yoklama oturumu bulunur.
- `Geldi` ve `Gelmedi` bütün kurumlarda zorunlu temel durumlardır.
- Yönetici isterse `Geç geldi`, `İzinli`, `Hasta` gibi ek durumlar tanımlayabilir.
- Ek durumlar sistem genelinde zorunlu veya sabit olmayacaktır.
- Toplu “hepsi geldi” işlemi desteklenebilir.
- Her öğrenci kaydı bağımsız olarak saklanır.
- Hoca geçmiş tarihli yoklama düzenleme yetkisine sahipse değişiklik yapabilir.
- Her değişiklikte eski değer, yeni değer ve değiştiren kullanıcı kaydedilir.
- Aynı sınıfı açık tutan hocalar diğer hocanın değişikliklerini kısa sürede görmelidir.

### 8.6. Program ve içerik modülü

Kurumlar aşağıdaki takip türlerini oluşturabilmelidir:

- Günlük ezber
- Haftalık ezber
- Sûre ve dua listeleri
- Kart veya bölüm sistemi
- Kur'an sayfa takibi
- Elif-Ba ilerlemesi
- Namaz takibi
- Serbest görev ve ödev

Sistem bunları tek bir yapılandırılabilir eğitim programı çekirdeği üzerinde sunmalıdır.
Takip türleri kullanıcıya uygun hazır şablonlar olarak gösterilebilir.

İçerik ilk sürümde:

- Metin
- İsteğe bağlı PDF eki

destekleyecektir. Ses, resim ve video sonraki fazlarda değerlendirilecektir.

### 8.7. Planlama modülü

İki yöntem birlikte desteklenecektir:

1. Hocanın zamanı geldiğinde günlük görevi tek tek eklemesi,
2. Önceden 20 günlük veya başka uzunlukta bir program şablonu hazırlayıp takvime dağıtması.

Yönetici:

- Çalışma günlerini,
- Başlangıç ve bitiş tarihini,
- Tatilleri,
- Programın uygulanmayacağı günleri,
- Gün sayısını,
- İçerik sırasını

belirleyebilir.

Bir sınıfta aynı anda birden fazla farklı program aktif olabilir. Örneğin sûre programı,
Nurlu Kart programı ve namaz takibi birlikte çalışabilir.

İlk sürümde bir program sınıfın bütün öğrencilerine ortak atanır. Öğrenciye özel program ve
hedefler sonraki fazdadır.

### 8.8. Değerlendirme ve ilerleme modülü

Her program kendi değerlendirme şemasını seçebilir.

Zorunlu temel yöntem:

- Tamamlandı
- Tamamlanmadı

Yönetici isterse programa şu alanları ekleyebilir:

- 10 üzerinden puan
- Öğretmen notu
- Tekrar gerekli

Bu seçenekler bütün programlarda zorunlu veya sabit değildir. Program oluşturulurken
etkinleştirilir.

İlk sürümde aynı sınıftaki hocalar öğrenci için yazılmış normal öğretmen notlarını görebilir.

### 8.9. Rapor ve dışa aktarma modülü

Excel çıktısı aşağıdaki kapsamları desteklemelidir:

- Kurum bazında
- Sınıf bazında
- Öğrenci bazında
- Günlük
- Haftalık
- Aylık
- Özel tarih aralığı

Çıktıya dahil edilebilecek bölümler:

- Öğrenci listesi
- Anne ve baba iletişim bilgileri
- Tarihlere göre yoklama
- Program, ezber ve ilerleme durumu
- Dönem özeti

Hoca işlem geçmişinin standart Excel raporuna eklenmesi zorunlu değildir. Denetim geçmişi
yetkili kullanıcılar için uygulama içinde ayrı sunulur.

Excel üretimi mobil cihazın kaynaklarına bağlı olmamalı; sunucuda hazırlanıp güvenli,
süreli bir indirme bağlantısıyla sunulması önerilir.

### 8.10. Denetim ve geri alma modülü

En az şu işlemler denetim kaydı oluşturmalıdır:

- Giriş ve kritik oturum olayları
- Öğrenci oluşturma/düzenleme/arşivleme
- Anne ve baba bilgilerinin değiştirilmesi
- Sınıf ataması
- Yoklama değişikliği
- Program ve içerik değişikliği
- İlerleme ve değerlendirme değişikliği
- Kullanıcı ve yetki değişikliği
- Kurum ayarı değişikliği
- Rapor dışa aktarma
- Platform yöneticisinin kurum verisine erişimi

Denetim kaydı:

- Kurum
- Kullanıcı
- İşlem türü
- Hedef kayıt
- Eski değer
- Yeni değer
- Zaman
- İstek/cihaz bağlamı

bilgilerini taşımalıdır.

Kullanıcının geri alabileceği işlemler açıkça tanımlanmalıdır. Geri alma, geçmişi silmek
yerine ters işlem oluşturarak yapılmalıdır.

### 8.11. Bildirim modülü

Bildirimler ürün mimarisinde yer alacak ancak ilk sürüm için yayın engelleyici değildir.

Sonraki fazlarda:

- Günlük yoklama hatırlatması
- Yeni program veya görev bildirimi
- Eşitleme sorunu
- Yönetici duyurusu
- Gelecekte veliye ilerleme veya devamsızlık bildirimi

desteklenebilir.

---

## 9. Kişiselleştirme çerçevesi

### 9.1. Görsel kimlik

İlk sürümden itibaren kurum yöneticisi:

- Kurum adını,
- Logosunu,
- Ana rengini,
- Desteklenen yardımcı renklerini

değiştirebilir.

Erişilebilirlik ve okunabilirlik kuralları kurum renklerinden daha önceliklidir. Sistem,
okunmaz renk kombinasyonlarını reddedebilmeli veya otomatik güvenli ton üretmelidir.

### 9.2. Menü ve modül görünürlüğü

- Menü görünürlüğü role ve kurumda etkin modüllere göre belirlenmelidir.
- Yönetici kullanılmayan modülleri kapatabilmelidir.
- Menü sırası kontrollü bir yapı içinde değiştirilebilir olmalıdır.
- Kullanıcının yetkisi olmayan sayfa yalnızca gizlenmemeli; doğrudan bağlantıyla da
  açılamamalıdır.

### 9.3. Özel öğrenci alanları

Zorunlu ad, soyad ve telefon dışında yönetici ek alanlar tanımlayabilir.

İlk desteklenmesi önerilen alan türleri:

- Kısa metin
- Uzun metin
- Sayı
- Tarih
- Evet/hayır
- Tek seçim
- Çoklu seçim

Her özel alan için etiket, zorunluluk, görünürlük ve sıralama tanımlanmalıdır.

### 9.4. Yapılandırılabilir takipler

Yönetici:

- Takip adını,
- Program türünü,
- İçerik sırasını,
- Gün sayısını,
- Çalışma günlerini,
- Değerlendirme alanlarını,
- Aktif/pasif durumunu

belirleyebilir.

---

## 10. Ana kullanıcı akışları

### 10.1. İlk giriş

1. Yönetici kullanıcı hesabını oluşturur.
2. Kullanıcı verilen bilgilerle giriş yapar.
3. Parola değişimi gerekiyorsa tamamlar.
4. Cihaz güvenilir cihaz olarak kaydedilir.
5. Kullanıcı yetkili olduğu kurum ve sınıfları görür.

### 10.2. Hoca ana akışı

1. Uygulama açılır; geçerli oturum varsa tekrar giriş istenmez.
2. Hoca yetkili olduğu sınıfları görür.
3. Sınıfı seçer.
4. Sınıf ana ekranında şu bölümlere ulaşır:
   - Bugünkü yoklama
   - Öğrenciler
   - Program ve ezberler
   - İlerleme
   - Yetkisi varsa ayarlar/yönetim
5. İşlem sonucu anında görünür ve eşitleme durumu belirtilir.

### 10.3. Yönetici ana akışı

Yönetici mobil uygulamadan:

- Sınıf oluşturur.
- Hoca oluşturur ve sınıfa atar.
- Hoca yetkilerini belirler.
- Öğrenci ve veli bilgilerini yönetir.
- Program şablonu veya günlük plan oluşturur.
- Kurum takvimini düzenler.
- Logo, renk ve kurum adını değiştirir.
- Rapor üretir.
- İşlem geçmişini inceler ve desteklenen işlemleri geri alır.

### 10.4. Yoklama akışı

1. Hoca sınıfı açar.
2. Bugünkü öğrenci listesi görüntülenir.
3. Geldi/gelmedi veya kurumun eklediği durum seçilir.
4. Değişiklik yerelde anında görünür.
5. İşlem benzersiz kimlikle sunucuya gönderilir.
6. Sunucu işlemi doğrular ve kaydeder.
7. Onaydan sonra işlem tamamlandı gösterilir.
8. Diğer hocaların ekranı güncellenir.
9. Çakışma varsa sessizce veri ezmek yerine tanımlı kural uygulanır ve gerekirse kullanıcı
   bilgilendirilir.

---

## 11. Hedef teknik mimari

### 11.1. Genel yaklaşım

Sistem şu ana parçalardan oluşacaktır:

1. iOS ve Android mobil uygulama,
2. Uygulama API'si,
3. İlişkisel veritabanı,
4. Kimlik ve oturum altyapısı,
5. PDF ve logo gibi dosyalar için nesne depolama,
6. Arka plan işleri ve rapor üretimi,
7. Gerçek zamanlı güncelleme kanalı,
8. Loglama, hata izleme ve denetim altyapısı,
9. Sonraki fazda web yönetim paneli.

### 11.2. Mobil uygulama katmanları

Mobil uygulama şu sorumluluklara ayrılmalıdır:

- **Presentation:** Ekranlar, bileşenler ve görsel durumlar
- **Application:** Kullanıcı akışları ve uygulama komutları
- **Domain:** İş kuralları ve alan modelleri
- **Data:** API, yerel veritabanı, önbellek ve senkronizasyon
- **Core:** Kimlik, tema, yönlendirme, hata işleme, loglama ve ortak altyapı

Ön teknoloji adayı Flutter'dır. Kesin karar, Faz 0'da yapılacak küçük dikey teknik deneme
sonrasında verilecektir. Deneme iOS ve Android üzerinde giriş, öğrenci listesi, yoklama,
yerel kayıt ve eşitleme akışını doğrulamalıdır.

### 11.3. Backend yaklaşımı

- Backend modüler monolit olarak başlamalıdır.
- Her modül kendi uygulama servislerine ve veri erişim sınırlarına sahip olmalıdır.
- Dış istemciler yalnızca sürümlenmiş API üzerinden işlem yapmalıdır.
- İş kuralları mobil uygulamada veya veritabanı tetikleyicilerinde dağınık hâlde
  tutulmamalıdır.
- Gerçek ihtiyaç oluşmadan mikroservise ayrılmamalıdır.

### 11.4. Veritabanı

- Ana veri kaynağı PostgreSQL gibi ilişkisel bir veritabanı olmalıdır.
- Her kuruma bağlı tabloda `organization_id` benzeri açık kurum ilişkisi bulunmalıdır.
- Yabancı anahtarlar ve benzersiz kısıtlar veri bütünlüğünü korumalıdır.
- Sık kullanılan kurum, sınıf, öğrenci ve tarih sorguları indekslenmelidir.
- Özel alan değerleri kontrollü bir genişletme modeliyle tutulmalıdır.
- Şema değişiklikleri sürümlü migration dosyalarıyla yönetilmelidir.

### 11.5. API ilkeleri

- API sürümlenmelidir.
- Bütün yazma istekleri kimlik doğrulamalıdır.
- Kurum ve sınıf yetkisi her istekte doğrulanmalıdır.
- İstek ve cevap şemaları doğrulanmalıdır.
- Sayfalama, sıralama ve filtreleme standartlaştırılmalıdır.
- Hatalar sabit hata kodu ve kullanıcıya uygun mesaj içermelidir.
- Toplu işlemler kısmi başarıyı açıkça bildirmelidir.
- Kritik yazma işlemleri idempotent olmalıdır.

---

## 12. Eşzamanlı kullanım ve gerçek zamanlı güncelleme

Bir sınıfta birden fazla hocanın eşzamanlı çalışması ilk sürümün temel gereksinimidir.

### 12.1. Kayıt sürümü

- Değiştirilebilir kayıtlar sürüm veya güncellenme belirteci taşımalıdır.
- İstemci hangi sürüm üzerinde değişiklik yaptığını sunucuya bildirmelidir.
- Sunucu eski sürümden gelen kritik değişikliği sessizce kabul etmemelidir.

### 12.2. İdempotency

- Her mobil yazma işlemi benzersiz `clientMutationId` benzeri bir kimlik taşımalıdır.
- Aynı işlem bağlantı sorunu nedeniyle tekrar gönderilirse yalnızca bir kez uygulanmalıdır.
- Başarılı sunucu onayı alınmadan yerel kuyruktan kaldırılmamalıdır.

### 12.3. Gerçek zamanlı yayılım

- Aynı sınıfa bağlı cihazlar ilgili sınıf kanalına abone olabilir.
- Sunucu değişiklik sonrasında küçük bir olay yayınlar.
- İstemci gerekli kaydı yeniden çeker veya güvenli olay içeriğini uygular.
- Bağlantı koparsa istemci yeniden bağlanıp son bilinen sürümden değişiklikleri toparlamalıdır.

### 12.4. Çakışma kuralları

Tek bir genel “son yazan kazanır” kuralı kullanılmamalıdır.

- **Yoklama:** En güncel geçerli işlem uygulanabilir; önceki değer denetim kaydında korunur.
- **Öğrenci bilgisi:** Sürüm uyuşmazlığında kullanıcı güncel veriyi görmeli ve değişikliğini
  yeniden değerlendirmelidir.
- **Program yapısı:** Aktif kullanımdayken yapılan büyük değişiklikler yeni program sürümü
  oluşturmalıdır.
- **Arşivleme:** Başka güncelleme sürerken sessizce uygulanmamalıdır.

---

## 13. Çevrimdışı ve zayıf bağlantı yaklaşımı

İnternetin düzenli olması beklenmektedir; yine de mobil ağ kesintileri nedeniyle temel
işlemler dayanıklı olmalıdır.

- Uygulama gerekli sınıf ve öğrenci listesini yerel veritabanında saklayabilir.
- Yoklama ve ilerleme işlemleri önce yerelde uygulanabilir.
- Bekleyen işlemler kalıcı kuyrukta tutulmalıdır.
- Uygulama kapanıp açılsa bile kuyruk kaybolmamalıdır.
- Kullanıcı bekleyen, başarılı ve başarısız işlemleri ayırt edebilmelidir.
- Kalıcı başarısızlık kullanıcıya açıklanmalı ve yeniden deneme sunulmalıdır.
- Hassas yerel veriler platformun güvenli depolama imkânlarıyla korunmalıdır.

İlk sürüm tam kapsamlı çevrimdışı yönetim paneli sunmak zorunda değildir. Yoklama ve temel
ilerleme kaydı önceliklidir.

---

## 14. Silme, arşivleme ve veri yaşam döngüsü

- Öğrenci, sınıf, program, kurum ve kullanıcı normal arayüzden fiziksel olarak silinmez.
- Aktif, pasif, askıda ve arşivlenmiş durumları kullanılır.
- Arşivlenen kayıtların geçmiş raporlardaki bağlantıları korunur.
- Geri yükleme yetkiye bağlıdır.
- Kişisel verinin kalıcı silinmesi ayrı ve denetlenen bir prosedürdür.
- Dönem kapatma işlemi geçmiş kayıtları değiştirmeden yeni dönem açılmasına izin verir.

---

## 15. Güvenlik gereksinimleri

İlk sürümde aşırı kullanıcı sürtünmesi istenmemektedir; buna rağmen temel güvenlikten taviz
verilmez.

- Parolalar düz metin olarak saklanamaz.
- Mobil uygulamada parola yerine güvenli oturum belirteci saklanır.
- Erişim belirteçleri kısa ömürlü, yenileme belirteçleri iptal edilebilir olmalıdır.
- Bütün trafik TLS üzerinden taşınmalıdır.
- Veritabanı doğrudan mobil uygulamaya yönetici yetkisiyle açılmamalıdır.
- Kurum izolasyonu otomatik testlerle doğrulanmalıdır.
- Kritik yönetim işlemlerinde yeniden kimlik doğrulama değerlendirilebilir.
- Oran sınırlama ve kaba kuvvet giriş koruması bulunmalıdır.
- PDF dosyaları herkese açık kalıcı URL ile sunulmamalıdır.
- Yedekler şifreli ve erişimi sınırlı olmalıdır.
- Üretim sırları kaynak kod deposuna yazılmamalıdır.

---

## 16. Performans ve ölçeklenebilirlik hedefleri

Kesin sayılar gerçek kullanım ölçümleriyle güncellenecektir. İlk hedefler:

- Uygulama sıcak açılışta ana ekranı yaklaşık 2 saniye içinde göstermelidir.
- Yerel verisi olan sınıf listesi ağ beklemeden görüntülenebilmelidir.
- Yoklama dokunuşu ekranda yaklaşık 100 ms içinde tepki vermelidir.
- Normal API çağrılarının büyük çoğunluğu uygun ağda 500 ms civarında tamamlanmalıdır.
- Diğer hocanın değişikliği normal koşullarda birkaç saniye içinde görünmelidir.
- Öğrenci listeleri sayfalama veya sanallaştırmayla yüksek sayılarda akıcı kalmalıdır.
- Rapor üretimi mobil uygulama ana akışını bloklamamalıdır.
- Kurum ve sınıf filtreleri bütün temel sorgularda indekslenmelidir.

Performans yalnızca son aşamada ele alınmayacak; her fazda ölçülecektir.

---

## 17. Gözlemlenebilirlik, yedekleme ve operasyon

### 17.1. Gözlemlenebilirlik

- Mobil çökme raporları
- Backend hata takibi
- İstek süresi ve hata oranları
- Başarısız eşitleme sayısı
- Rapor üretim hataları
- Gerçek zamanlı bağlantı sorunları
- Güvenlik ve yetki reddi olayları

izlenmelidir.

### 17.2. Yedekleme

- Veritabanı otomatik yedeklenmelidir.
- Yedekten geri dönüş düzenli olarak denenmelidir.
- Dosya depolama için uygun sürümleme veya yedek politikası bulunmalıdır.
- Yalnızca yedek alınması yeterli değildir; geri yükleme süresi ve prosedürü belgelenmelidir.
- Sentetik kapalı alfada best-effort yedek kabul edilebilir; gerçek öğrenci verisinde zamanlanmış
  bir CI işi tek yedek mekanizması olamaz ve başarısızlık görünür alarm üretmelidir.

### 17.3. Ortamlar

Mantıksal olarak en az şu ortam profilleri tanımlanmalıdır:

- Geliştirme
- Test/staging
- Üretim

Bu profillerin üçü ilk günden sürekli çalışan ayrı bulut kaynakları olmak zorunda değildir.
Development yerel olabilir; staging yayın öncesinde geçici açılabilir. Üretim verisi hiçbir
durumda geliştirici cihazına veya sentetik alt ortama kopyalanamaz.

Üretim verisi geliştirici cihazlarına kopyalanmamalıdır. Test ortamında sentetik veya
anonimleştirilmiş veri kullanılmalıdır.

---

## 18. Test stratejisi ve kalite kapıları

### 18.1. Test seviyeleri

- Domain iş kuralları için birim testleri
- Repository ve veritabanı entegrasyon testleri
- API yetki ve kurum izolasyonu testleri
- Mobil ekran ve durum yönetimi testleri
- Kritik kullanıcı akışları için uçtan uca testler
- Eşzamanlı iki hoca senaryosu testleri
- Ağ kesilmesi ve yeniden bağlanma testleri
- Excel çıktısı doğrulama testleri
- iOS ve Android gerçek cihaz testleri

### 18.2. Kritik test senaryoları

1. İki hoca aynı sınıfta aynı anda yoklama değiştirir.
2. İşlem gönderilirken ağ kesilir.
3. Sunucu işlemi kaydeder fakat cevap cihaza ulaşmaz; tekrar gönderim çift kayıt üretmez.
4. Yetkisiz hoca başka sınıfın kimliğini kullanarak veri istemeye çalışır.
5. Bir kurum başka kurumun öğrenci kimliğini sorgular.
6. Öğrenci arşivlenir ve geri yüklenir; geçmiş kayıtlar korunur.
7. Program başladıktan sonra içerik değiştirilir; geçmiş ilerleme bozulmaz.
8. Kullanıcının cihaz oturumu yönetici tarafından iptal edilir.
9. Excel raporu seçilen tarih ve sınıfla tutarlı sonuç üretir.
10. Uygulama bekleyen işlemler varken kapanıp yeniden açılır.

### 18.3. Bir özelliğin tamamlanma tanımı

Bir özellik ancak:

- Kabul ölçütleri karşılandıysa,
- Yetki kontrolleri yazıldıysa,
- Hata ve boş durumları tasarlandıysa,
- İlgili otomatik testler geçtiyse,
- Gerçek cihazda kontrol edildiyse,
- Analitik/loglama ihtiyacı ele alındıysa,
- Dokümantasyonu güncellendiyse,
- Erişilebilirlik ve performans gözden geçirildiyse

tamamlanmış sayılır.

---

## 19. İlk sürüm kapsamı

İlk sürüm aşağıdaki işleri içerir:

- Platform yöneticisinin kurum oluşturması
- Kurum yöneticisi ve hoca girişleri
- Güvenilir cihazda kalıcı oturum
- Kurum adı, logo ve renk ayarı
- Sınıf oluşturma ve arşivleme
- Hoca hesabı ve sınıf yetkileri
- Öğrenci ekleme, düzenleme ve arşivleme
- Anne ve baba bilgileri
- Günlük tek yoklama
- Geldi/gelmedi ve kuruma özel ek yoklama durumları
- Birden fazla hocanın aynı sınıfta eşzamanlı çalışması
- Yapılandırılabilir program ve ezber takibi
- Günlük manuel görev ve çok günlük şablon
- Aynı sınıfta birden fazla aktif program
- Tamamlandı/tamamlanmadı
- İsteğe bağlı 10 üzerinden puan, not ve tekrar gerekli
- Metin içeriği ve isteğe bağlı PDF
- Kurum çalışma günleri, dönem ve tatil ayarları
- Denetim geçmişi
- Desteklenen işlemlerde geri alma
- Kurum, sınıf, öğrenci ve tarih bazında Excel çıktısı
- Temel çevrimdışı kuyruk ve güvenli yeniden deneme
- iOS ve Android yayın hazırlığı

---

## 20. İlk sürüm dışında kalanlar

Kapsam kaymasını engellemek için aşağıdakiler ilk sürümün yayın şartı değildir:

- Veli girişi
- Öğrenci girişi
- Mesajlaşma
- Push bildirimleri
- Kurs yöneticisinin kendi kendine kurum açması
- Davet koduyla kurum oluşturma
- Gelişmiş web yönetim paneli
- Öğrenciye özel program ve hedef
- Öğrencinin aynı anda birden fazla sınıf/gruba üyeliği
- Ses, video ve gelişmiş medya içerikleri
- Ödeme ve abonelik sistemi
- Çoklu dil
- Gelişmiş puan, rozet ve oyunlaştırma
- Yapay zekâ özellikleri

Bu işler mimaride engellenmeyecek, fakat ilk sürüme gizlice eklenmeyecektir.

---

## 21. Uygulama fazları

Takvim tahminidir; fazlar kabul ölçütleri tamamlanmadan kapatılmaz.

### Faz 0 — Ürün sözleşmesi, UX ve teknik doğrulama

Amaç: Kodlamadan önce belirsizlikleri kapatmak ve en riskli teknik varsayımları doğrulamak.

Çıktılar:

- Bu belgenin onaylanması
- Terimler sözlüğü
- Ayrıntılı yetki matrisi
- Ana ekran akışları ve düşük ayrıntılı wireframe'ler
- Veri modeli taslağı
- API sınırları
- Mobil teknoloji dikey denemesi
- İki cihaz eşzamanlı yoklama denemesi
- Yerel kuyruk ve idempotency denemesi
- Teknik karar kayıtları

Kabul ölçütleri:

- Mobil teknoloji kararı kanıtla verilmiş olmalı.
- Hoca ve yönetici ana akışları herkesçe aynı anlaşılmalı.
- Kurum izolasyonu ve yetki yaklaşımı yazılı olmalı.
- Eşzamanlı değişiklik senaryosu teknik olarak doğrulanmalı.

#### Faz 0 ile çalışma dalgalarının eşlemesi

Ürün **fazları** kabul sonucunu, çalışma **dalgaları** ise agent görevlerinin yürütme paketlerini
ifade eder; aynı kavram değildir. Faz 0 iki çalışma parçasından oluşur:

| Faz 0 parçası | Görev karşılığı | Kapı durumu |
|---|---|---|
| Ürün, UX ve veri/API sözleşmeleri | Dalga 0, `P-001`–`P-014` | Tamamlandı; P-014 yalnız bu belge kapısını kapatır |
| Teknik karar ve risk doğrulaması | Dalga 1'in `A-001`–`A-010` bölümü | Açık; ADR ve çalıştırılabilir deney kanıtlarıyla kapanır |

`A-001` mobil framework dikey denemesini, `A-005` kalıcı yerel kuyruk/idempotent yeniden deneme
denemesini, `A-006` ise iki istemcili yoklama olayı yayılımı/eşzamanlılık denemesini kanıtlarıyla üretir.
`A-002`–`A-004` ve `A-007`–`A-010` kalan teknik karar kayıtlarını tamamlar. Faz 0 teknik kabul
kapısı `A-001`–`A-010` onaylanmadan kapanmış sayılmaz.

Dalga 1'in `A-011`–`A-015` bölümü Faz 1 platform temeline aittir. Bu nedenle Dalga 1 iki ürün
fazına yayılan bir yürütme paketidir; `A-011` repo/uygulama iskeleti Faz 0 teknik kapısından önce
başlatılamaz.

### Faz 1 — Platform temeli

Amaç: Güvenli ve test edilebilir uygulama iskeletini kurmak.

Çıktılar:

- Geliştirme, staging ve üretim ortamları
- Veritabanı migration sistemi
- Kimlik ve oturum altyapısı
- Platform yöneticisi
- Kurum oluşturma
- Kurum izolasyonu
- Rol ve izin altyapısı
- Mobil navigasyon, tema ve hata yönetimi
- Loglama ve hata takibi
- CI doğrulamaları

Kabul ölçütleri:

- Bir kurum kullanıcısı başka kurum verisine erişememeli.
- Oturum cihazda güvenli biçimde korunmalı ve iptal edilebilmeli.
- Temel otomatik test ve dağıtım hattı çalışmalı.

### Faz 2 — Kurum, sınıf, kullanıcı ve öğrenci yönetimi

Amaç: Yöneticiye mobil uygulamadan kurumu işletme imkânı vermek.

Çıktılar:

- Kurum markalaşması
- Eğitim dönemi ve takvim
- Sınıf yönetimi
- Hoca hesabı ve sınıf ataması
- Yetki yönetimi
- Öğrenci yönetimi
- Anne ve baba kayıtları
- Arşivleme ve geri yükleme
- Arama ve filtreleme

Kabul ölçütleri:

- Yönetici yeni kurumu mobil uygulamadan kullanılabilir hâle getirebilmeli.
- Hoca yalnızca atandığı sınıfı görebilmeli.
- Öğrenci arşivlendiğinde geçmiş bağları korunmalı.

### Faz 3 — Yoklama ve eşzamanlı çalışma

Amaç: İlk günlük ana işlevi üretim kalitesinde tamamlamak.

Çıktılar:

- Günlük tek yoklama
- Yapılandırılabilir yoklama durumları
- Toplu işlemler
- Yerel hızlı güncelleme
- Kalıcı işlem kuyruğu
- Sunucu onayı ve yeniden deneme
- Gerçek zamanlı sınıf güncellemeleri
- Çakışma yönetimi
- Yoklama denetim geçmişi ve geri alma

Kabul ölçütleri:

- İki veya daha fazla hoca aynı sınıfta veri kaybetmeden çalışabilmeli.
- Ağ kesintisinde işlem kaybolmamalı.
- Sunucu hatası başarı olarak gösterilmemeli.
- Tekrar gönderim çift yoklama kaydı üretmemeli.

### Faz 4 — Program, içerik ve ilerleme

Amaç: Farklı kurumların kendi eğitim düzenini kurabilmesini sağlamak.

Çıktılar:

- Program türleri ve hazır şablonlar
- Metin ve PDF içerikleri
- Günlük manuel planlama
- Çok günlük program şablonu
- Çalışma günü ve tatil hesaplama
- Aynı sınıfta çoklu aktif program
- Tamamlandı/tamamlanmadı
- İsteğe bağlı puan, not ve tekrar gerekli
- İlerleme geçmişi

Kabul ölçütleri:

- Kod değişmeden farklı iki kurum farklı takip düzeni kurabilmeli.
- Başlamış programdaki geçmiş ilerleme, program düzenlemesinden etkilenmemeli.
- Aynı sınıfın birden fazla programı birlikte kullanılabilmeli.

### Faz 5 — Raporlama, sertleştirme ve mobil yayın

Amaç: İlk sürümü gerçek kullanıcıya güvenle açmak.

Çıktılar:

- Excel dışa aktarma
- Denetim ekranları
- Desteklenen geri alma akışları
- Performans iyileştirmeleri
- Erişilebilirlik kontrolü
- Güvenlik incelemesi
- Yedek ve geri yükleme testi
- iOS ve Android gerçek cihaz testleri
- Pilot kurum kullanımı
- Mağaza hazırlıkları
- Kullanıcı ve destek dokümantasyonu

Kabul ölçütleri:

- Kritik ve yüksek öncelikli açık hata bulunmamalı.
- Yedekten geri dönüş denenmiş olmalı.
- Pilot kullanımda sessiz veri kaybı yaşanmamalı.
- Excel raporları kaynak verilerle uyuşmalı.
- iOS ve Android temel akışları aynı işlevsel sonucu vermeli.

### Faz 6 — Web yönetim paneli

- Büyük ekran için kurum yönetimi
- Toplu öğrenci ve kullanıcı işlemleri
- Program tasarlama
- Gelişmiş raporlama
- Excel içe aktarma
- Yetki ve denetim yönetimi

### Faz 7 — Veli ve öğrenci deneyimi

Önce salt okunur başlanması önerilir:

- Kendi/çocuğunun yoklama özeti
- Atanan program ve görevler
- İlerleme durumu
- Öğretmen veya kurum duyuruları

Veli ve öğrencinin veri yazması daha sonraki kontrollü alt fazlarda açılır.

### Faz 8 — Bildirimler ve gelişmiş platform özellikleri

- Push bildirimleri
- Duyurular
- Hatırlatmalar
- Paket ve abonelik seçenekleri
- Kurumun kendi kaydı ve davet akışları
- Çoklu dil
- Gelişmiş analizler

---

## 22. Yayın stratejisi

İlk yayın doğrudan bütün kurumlara açılmamalıdır.

1. İç test verileriyle kullanım
2. Tek pilot kurum
3. Sınırlı sayıda gerçek sınıf
4. Geri bildirim ve hata düzeltme dönemi
5. İkinci pilot kurumla kişiselleştirme doğrulaması
6. Kontrollü genel erişim

Pilot sırasında yeni özellik eklemekten önce veri kaybı, kullanım güçlüğü, performans ve
yetki sorunları çözülmelidir.

---

## 23. Karar ve değişiklik yönetimi

### 23.1. Yeni fikir değerlendirme soruları

Her yeni özellik için:

1. Hangi kullanıcı problemini çözüyor?
2. İlk sürüm için zorunlu mu?
3. Mevcut bir modülle çözülebilir mi?
4. Veri modeline etkisi nedir?
5. Yetki ve mahremiyet etkisi nedir?
6. Çevrimdışı ve eşzamanlı kullanım etkisi nedir?
7. Test ve operasyon maliyeti nedir?
8. Hangi faza aittir?

soruları cevaplanmalıdır.

### 23.2. Teknik karar kayıtları

Aşağıdaki kararlar ayrı ADR belgeleriyle kaydedilmelidir:

- Mobil framework seçimi
- Backend dili ve framework'ü
- Kimlik doğrulama altyapısı
- Veritabanı ve hosting sağlayıcısı
- Gerçek zamanlı güncelleme yöntemi
- Yerel mobil veritabanı
- Dosya depolama
- Bildirim sağlayıcısı
- Rapor üretme yöntemi

Her ADR; bağlam, seçenekler, karar, gerekçe ve sonuçlar bölümlerini içermelidir.

### 23.3. Belge sürümleme

- Küçük açıklama değişiklikleri: `1.0` → `1.1`
- Kapsam veya mimari değişikliği: `1.x` → `2.0`
- Her değişiklik tarih ve gerekçeyle “Karar günlüğü”ne eklenir.
- Onaylanan karar kaldırılmaz; yerine geçen karar belirtilir.

---

## 24. Risk kaydı

| Risk | Etki | Önlem |
|---|---|---|
| Aşırı kişiselleştirme nedeniyle karmaşık ürün | Yüksek | Sabit çekirdek + kontrollü özel alanlar ve program şemaları |
| İlk sürüme veli, mesajlaşma ve web panelinin eklenmesi | Yüksek | İlk sürüm dışı kapsam listesine uyum |
| Eşzamanlı hocalarda veri ezilmesi | Çok yüksek | Sürüm kontrolü, idempotency, gerçek zamanlı olaylar ve çakışma testleri |
| Kurumlar arası veri sızıntısı | Çok yüksek | Sunucu tarafı yetkilendirme, kurum filtreleri ve otomatik izolasyon testleri |
| Sessiz eşitleme/veri kaybı | Çok yüksek | Onaylanmadan kuyruktan silmeme ve görünür eşitleme durumu |
| Her şeyi dinamik yapma nedeniyle raporların bozulması | Yüksek | Çekirdek ilişkisel model ve doğrulanmış genişletme şeması |
| Mobil yönetimin karmaşıklaşması | Orta | İlk sürümde sade akış; web panelini sonraki fazda ekleme |
| Teknoloji seçiminin acele yapılması | Yüksek | Faz 0 dikey teknik deneme ve ADR |
| PDF ve raporların cihazı yavaşlatması | Orta | Sunucu tarafı dosya ve rapor üretimi |
| Yedek var sanılıp geri yüklenememesi | Yüksek | Düzenli geri yükleme tatbikatı |

---

## 25. İlk tasarım çalışmalarında üretilecek belgeler

Bu ana belgeden sonra Dalga 0'da:

1. `TERIMLER_SOZLUGU.md`, `AKTORLER_VE_KULLANIM_SENARYOLARI.md`, `YETKI_MATRISI.md` ve `KISISEL_VERI_ENVANTERI.md`
2. `YONETICI_BILGI_MIMARISI.md`, `HOCA_MOBIL_BILGI_MIMARISI.md` ve `EKRAN_ENVANTERI.md`
3. `VERI_MODELI.md`, `API_GENEL_KURALLARI.md`, `SENKRONIZASYON_VE_CAKISMA.md` ve `DENETIM_VE_GERI_ALMA_ILKELERI.md`
4. `EXCEL_RAPOR_VERI_SOZLESMESI.md`, `KRITIK_TEST_VE_KABUL_PLANI.md` ve `FAZ_0_BUTUNLUK_INCELEMESI.md`
5. `ADR/` altındaki teknik karar belgeleri
6. `AGENT_GOREV_PLANI.md` içindeki atomik görev kartları ve çalışma dalgaları

hazırlanmalıdır.

Dalga 0 belgeleri tamamlanmadan ilgili alanda büyük ölçekli geliştirmeye başlanmamalıdır.

---

## 26. Karar günlüğü

### 13 Temmuz 2026 — Başlangıç kararları

- Yeni platform temiz başlangıç yapacak.
- Google Sheets ana veri kaynağı olmayacak.
- İlk sürüm hoca ve yöneticilere yönelik olacak.
- Platform çok kurumlu olacak.
- İlk kurumları platform yöneticisi oluşturacak.
- Platform yöneticisi bütün kurum ve sınıflara erişebilecek.
- Kurum yöneticisi hoca yetkilerini ve görülebilen sınıfları belirleyecek.
- Bir öğrenci ilk sürümde tek aktif sınıfta olacak.
- Bir hoca birden fazla sınıfta, bir sınıf birden fazla hocayla çalışabilecek.
- Her sınıf için günlük tek yoklama alınacak.
- Geldi ve gelmedi zorunlu; diğer durumlar kuruma göre yapılandırılabilir olacak.
- Kurumlar bütün temel program ve takip türlerini oluşturabilecek.
- Tamamlandı/tamamlanmadı zorunlu temel; puan, not ve tekrar gerekli isteğe bağlı olacak.
- Manuel günlük plan ve çok günlük şablon birlikte desteklenecek.
- İlk sürümde program sınıf seviyesinde ortak olacak.
- Anne ve baba ayrı kişi kayıtları olarak tutulacak.
- Güvenilir cihazda oturum açık kalabilecek.
- Excel raporunda öğrenci, veli, yoklama, ilerleme ve dönem özeti bulunabilecek.
- İlk dil Türkçe olacak.
- Kurum adı, logo ve renkler değiştirilebilecek.
- Hoca notları aynı sınıftaki hocalarca görülebilecek.
- Değişiklik geçmişi ve geri alma desteklenecek.
- Metin ve isteğe bağlı PDF içerikleri desteklenecek.
- Bildirim mimaride yer alacak ancak ilk sürüm için zorunlu olmayacak.
- Web yönetim paneli sonraki faza bırakılacak.

### 13 Temmuz 2026 — P-003 yetki matrisi kararları

- `YETKI_MATRISI.md` (P-003) kapsamında ana planda açık dayanağı olmayan dokuz devredilebilirlik
  sorusu kullanıcı tarafından onaylanmıştır.
- Kurum marka ayarı, etkin modül yönetimi, hoca–sınıf ataması, başka kullanıcının cihaz
  oturumunu iptal etme ve kuruma özel yoklama durumu tanımlama gibi operasyonel kurum/personel
  ayarları, kurum yöneticisi tarafından hocaya ayrı ve geri alınabilir izinlerle devredilebilir.
- Hoca hesabı oluşturma/kapatma ayrı bir personel yönetimi izniyle hocaya devredilebilir; bu izin
  başka kullanıcılara izin verme yetkisi sağlamaz.
- Hoca izinlerini değiştirme/verme/geri alma yetkisi ilk sürümde yalnızca kurum yöneticisindedir;
  bu yetki hiçbir izinle hocaya devredilemez.
- Veli iletişim bilgisi görüntüleme, hoca için varsayılan kapalı ve ayrı izinle açılabilir bir
  yetkidir; öğrenci görüntüleme izninden bağımsızdır.

### 14 Temmuz 2026 — PLAN-004 tutarlılık kararları

- Ürün fazları kabul sonuçlarını, çalışma dalgaları agent yürütme paketlerini ifade eder. Dalga 0
  belge kapısı tamamlanmıştır; Faz 0 teknik doğrulama kapısı `A-001`–`A-010` tamamlanana kadar
  açıktır. Dalga 1'in `A-011`–`A-015` bölümü Faz 1 platform temeline aittir.
- `A-001`, `A-005` ve `A-006` salt ADR metniyle tamamlanamaz; sırasıyla mobil dikey uygulama,
  kalıcı kuyruk/idempotent yeniden deneme ve iki istemcili olay yayılımı deney kanıtı üretir.
- Kuruma özel öğrenci alanı tanımlarını yönetme V1'de yalnız kurum yöneticisine ve açık destek
  bağlamındaki platform yöneticisine aittir; hocaya devredilemez. Öğrencideki özel alan değerine
  erişim, alan tanımı yönetiminden ayrı olarak hedef öğrenci erişim kapsamına tabidir.

### 15 Temmuz 2026 — PLAN-005 maliyet ve operasyon kararları

- Yerel geliştirme ve 0–10 davetli gerçek test kullanıcılı kapalı alfa için dış ödeme hedefi
  `0 USD/ay`dır. Kullanıcı adları tercihen takmadır; kurum, öğrenci, veli, yoklama, ilerleme,
  PDF ve rapor verilerinin tamamı sentetiktir. Herhangi bir gerçek kurum veya öğrenci verisi,
  kullanıcı sayısından bağımsız gerçek kurum pilotudur ve hedef bütçesi `25–60 USD/ay`dır.
- Ücretsiz veya öğrenci kredili hizmetler geçici maliyet desteğidir; üretim sahipliği ve veri
  kurtarma süreci kişisel öğrenci hesabına bağlanmayacaktır.
- A-011 sağlayıcı bağımsız repo/uygulama iskeleti olarak devam edebilir; kimlik, depolama,
  cloud secret veya gerçek provisioning sözleşmesi üretmez.
- Self-managed Keycloak kararı Cognito Essentials A-004R1–A-004R3 deney zinciri sonuçlanana
  kadar uygulamaya alınmayacaktır. Kurum/sınıf yetkisi ile cihaz ve kurum kapsamlı platform
  oturum iptali kimlik sağlayıcısından bağımsız olarak platformda kalacaktır.
- Kapalı alfada dosya gerekmiyorsa uzak nesne deposu kurulmayacaktır. S3 referans güvenlik
  sözleşmesi korunacak; R2 EU ancak versioning, geri yükleme ve imha sözleşmesi yeniden
  doğrulanırsa alternatif olacaktır.
- Sürekli staging, PITR, Render Pro ve yüksek erişilebilirlik başlangıç zorunluluğu olmaktan
  çıkarılmış; ölçülmüş kullanım veya kabul edilmiş RPO/RTO tetiklerine bağlanmıştır.

### 20 Temmuz 2026 — Erken audit çekirdeği ve migration sırası

- Kurum yaşam döngüsü audit kaydı olmadan production çağrı yüzeyi açmayacağı için audit
  çekirdeği Dalga 6'yı beklemeden `AUDIT-001A` ile Dalga 2'ye alınmıştır.
- `AUDIT-001A`, yalnız `audit_action_catalog` ve değişmez `audit_logs` çekirdeğini, FORCE RLS
  ve varsayılan-red erişimle oluşturur; runtime rolleri için geniş yetki veya audit'i atlayan
  yol açmaz.
- `classes` tablosu henüz bulunmadığından sınıf kapsamlı audit satırları geçici DB kısıtlarıyla
  tamamen kapalıdır. `CLS-002` sonrasında `AUDIT-001`, sınıf bileşik FK'sini ve sınıf kapsamlı
  audit kataloglarını ekleyerek ortak şemayı tamamlar.
- Flyway sırası tek sahipli tutulur: önce `AUDIT-001A` merge edilir, ardından açık ORG-003 PR'ı
  güncel main üzerine rebase edilip bir sonraki migration numarasına taşınır. Böylece aynı
  migration sürümü ve ortak tablo üzerinde paralel değişiklik yapılmaz.

---

## 27. Bir sonraki adım

Bu belge onaylandıktan sonra ilk çalışma **Faz 0** olacaktır. İlk alt adımlar:

1. Terimler sözlüğünü hazırlamak,
2. Ayrıntılı yetki matrisini oluşturmak,
3. Yönetici ve hoca kullanıcı akışlarını çizmek,
4. Çekirdek veri modelini kesinleştirmek,
5. Mobil teknoloji ve eşzamanlı yoklama için dikey teknik deneme planı hazırlamak.

Kodlama, bu beş alt adımın kapsamı ve kabul ölçütleri netleşmeden ana geliştirme olarak
başlatılmamalıdır.

Günlük çalışma sırası, paralel agent kullanımı, görev bağımlılıkları ve modül bazlı atomik
iş paketleri `AGENT_GOREV_PLANI.md` belgesinde tanımlanmıştır. Repo üzerinde çalışan bütün
agentlar ayrıca kök dizindeki `AGENTS.md` kurallarına uymalıdır.
