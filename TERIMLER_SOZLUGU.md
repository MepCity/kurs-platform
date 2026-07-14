# Terimler Sözlüğü

| Alan | Değer |
|---|---|
| Görev | P-001 — Terimler sözlüğünü oluştur |
| Belge sürümü | 1.1 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Son güncelleme | 14 Temmuz 2026 (Kullanıcı/Hoca veri modeli satırları `P-008`/`VERI_MODELI.md` ile uyumlu hâle getirildi) |

---

## 1. Amaç ve kapsam

Bu belge, ürün ve mimari planında (`URUN_VE_UYGULAMA_PLANI.md`) ve görev planında
(`AGENT_GOREV_PLANI.md`) geçen çekirdek kavramları tek ve çelişkisiz biçimde tanımlar. Buradan
sonraki bütün görevler (yetki matrisi, veri modeli, API sözleşmesi, mobil ekranlar) bu terimleri
aynı anlamda kullanmalıdır.

Bu belge yeni bir ürün kararı almaz; yalnızca onaylı ana plandaki kavramları netleştirir. Ana
plana aykırı bir tanım bulunmamaktadır.

Belge, bölüm 2'deki terim tablosunda 23 çekirdek terim tanımlar: kurum, kullanıcı, kişi, hoca,
öğrenci, anne, baba, sınıf, dönem, program, program şablonu, plan, günlük görev, içerik,
değerlendirme, ilerleme, yoklama oturumu, yoklama kaydı, denetim kaydı, geri alma, operasyonel
veri, metaveri ve destek modu.

### 1.1. Kaynak notu — eski sistem karşılığı

Eski Excel, Google Sheets, Apps Script ve HTML dosyaları (örn. "Fındıklı" sınıf sitesi) bu repo
içinde bulunmamaktadır; `AGENTS.md` ve `URUN_VE_UYGULAMA_PLANI.md` bunları yalnızca dış referans
olarak tanımlar. Bu nedenle "eski sistem karşılığı" sütunu iki kaynaktan doldurulmuştur:

1. Ana ürün planının kendisinde açıkça geçen eski isimler (örn. "Nurlu Kart", "kart/bölüm
   sistemi", "Fındıklı" sınıf sitesi).
2. Kavramın Kur'an kursu takip pratiğinde genel bilinen adı (örn. "hoca", "öğrenci", "yoklama").
   Bu genel adlar, eski dosyalardaki tam alan yapısını veya kullanımını doğrulamaz; yalnızca
   bilinen kelimenin kendisidir.

Eski dosyaların gerçek içeriğine dayanan, doğrulanamayan hiçbir yapısal varsayım (örn. bir alanın
tek mi ayrı mı tutulduğu, bir kaydın nasıl saklandığı) bu belgede yapılmamıştır. Ana plana veya bu
repoya hiç yansımamış ve genel bilinen bir karşılığı da olmayan terimler aşağıda "tespit edilemedi
— eski kaynaklar bu repoda yok" olarak işaretlenmiştir. Kesin eski ad eşleşmesi gerekirse eski
Excel/HTML dosyalarının incelenmesi ayrı bir görev olarak açılmalıdır (kapsam dışı, aşağıda tekrar
belirtilmiştir).

---

## 2. Terim tablosu

Her satır: **Terim**, **Tanım**, **Veri modeli / arayüz tercih edilen ad**, **Eski sistem
karşılığı**. Benzer terimlerin farkı tablo altında ayrıca açıklanmıştır.

| Terim | Tanım | Tercih edilen ad (veri modeli / arayüz) | Eski sistem karşılığı |
|---|---|---|---|
| Kurum | Platformu kullanan bağımsız bir Kur'an kursu; kendi kullanıcıları, sınıfları, öğrencileri, içerikleri ve ayarlarıyla diğer kurumlardan tamamen ayrılmış üst kapsam birimi. | Veri modeli: `organization` (tablo: `organizations`). Arayüz: "Kurum". | Tespit edilemedi — eski kaynaklar bu repoda yok. Ana plan ve `AGENTS.md`, eski "Fındıklı" sınıf sitesine referans verir; ancak bu tek sınıf sitesinin "kurum" kavramına tam karşılığı doğrulanamadı. |
| Kullanıcı | Sisteme kimlik doğrulamasıyla giriş yapabilen, global (kurum bağımsız) hesap. Kullanıcının kendisi tek bir role sabitlenmez — platform yöneticiliği global bir rol atamasıdır; kurum yöneticisi/hoca rolleri ise kullanıcının üye olduğu her kurumda ayrı ayrı tanımlanır. Bir kullanıcı birden fazla rol atamasına sahip olabilir (örn. aynı kişi bir kurumda yönetici, başka bir kurumda hoca olabilir); her kurum kendi PII (ad/telefon vb.) kopyasını ayrıca tutar, kişisel veriler kurumlar arasında paylaşılmaz. | Veri modeli (`P-008` bağlayıcı şeması, `VERI_MODELI.md`): `users` (global kimlik doğrulama, `person_id` taşımaz) + `organization_memberships` (kullanıcının bir kurumdaki üyeliği ve o kurumdaki `people` profili) + `organization_membership_roles` (o üyelikteki bir veya birden fazla aktif rol: `ORG_ADMIN`/`TEACHER`) + `platform_administrators` (global `PLATFORM_ADMIN` rolü). Arayüz: "Kullanıcı". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Kişi | Hoca, yönetici, öğrenci ve velinin paylaştığı ortak çekirdek kayıt; ad, soyad ve telefon gibi temel bilgileri taşır. Bir kişinin kullanıcı hesabı olmayabilir (örn. ilk sürümde öğrenci ve veli). | Veri modeli: `person` (tablo: `people`). Arayüz: ayrı bir ekran adı yoktur; rol bazlı ekranlarda (öğrenci, hoca vb.) üzerinden gösterilir. | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Hoca | Bir kullanıcının, ilgili kurum içindeki `TEACHER` rolüdür; bu rol bir veya birden fazla sınıfa ayrıca atanabilir (rolün kendisi ile sınıf ataması ayrı kavramlardır). Hoca, yalnızca kendisine sınıf ataması yapılmış sınıflarda yoklama, program ve ilerleme işlemlerini yapabilir; sınıfı olmasa bile kendisine verilmiş kurum kapsamlı izinleri kullanabilir. Aynı kullanıcı başka bir kurumda veya aynı kurumda ek olarak farklı bir role (`ORG_ADMIN`) sahip olabilir. | Veri modeli (`P-008` bağlayıcı şeması, `VERI_MODELI.md`): kurum üyeliğinde (`organization_memberships`) `organization_membership_roles` kaydında `role = TEACHER`; sınıf ataması ayrıca `class_teacher_assignments`'ta tutulur. Arayüz: "Hoca". | "Öğretmen" / "hoca" — genel bilinen terim. Eski dosyalardaki tam kullanım tespit edilemedi — eski kaynaklar bu repoda yok. |
| Öğrenci | Bir kuruma ve (varsa) bir aktif sınıfa kayıtlı, kişi çekirdeğine ek olarak kayıt tarihi ve durum (aktif/pasif/arşivlenmiş) bilgisi taşıyan kayıt. | Veri modeli: `student` (tablo: `students`, `person_id` FK). Arayüz: "Öğrenci". | "Öğrenci" — genel bilinen terim; ana plan eski veri kaynağının Excel/Google Sheets olduğunu belirtir, ancak tam alan yapısı tespit edilemedi — eski kaynaklar bu repoda yok. |
| Anne | Öğrenciyle `anne` ilişki türü üzerinden bağlı, ayrı bir kişi kaydı; ilk sürümde giriş hesabı yoktur. | Veri modeli: `person` kaydı + `student_guardian` ilişkisinde `relation_type = MOTHER`. Arayüz: "Anne". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Baba | Öğrenciyle `baba` ilişki türü üzerinden bağlı, ayrı bir kişi kaydı; ilk sürümde giriş hesabı yoktur. | Veri modeli: `person` kaydı + `student_guardian` ilişkisinde `relation_type = FATHER`. Arayüz: "Baba". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Sınıf | Bir kuruma ve bir eğitim dönemine bağlı; öğrenci ve hoca üyeliklerini barındıran çalışma birimi. Bir öğrenci ilk sürümde aynı kurumda aynı anda yalnızca bir aktif sınıfta bulunabilir. | Veri modeli: `class` (tablo: `classes`; sınıf-hoca ve sınıf-öğrenci ilişkileri ayrı ilişki tablolarında). Arayüz: "Sınıf". | "Sınıf" — örn. eski "Fındıklı" sınıf sitesindeki tek sınıf kavramının karşılığı; yeni sistemde kurum başına birden fazla sınıf olabilir. |
| Dönem (Eğitim dönemi) | Kurumun tanımladığı, başlangıç ve bitiş tarihi olan; tatiller ve çalışılmayan günlerin de tanımlandığı zaman aralığı. Sınıflar bir döneme bağlıdır. | Veri modeli: `term` (tablo: `terms`). Arayüz: "Eğitim dönemi" / "Dönem". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Program | Bir sınıfta kullanılan, yapılandırılabilir eğitim takip çekirdeğinin tek örneği (örn. bir "günlük ezber" programı, bir "namaz takibi" programı). Aynı sınıfta birden fazla program aynı anda aktif olabilir. Ezber, sûre/dua listesi, kart/bölüm, Kur'an sayfa takibi, Elif-Ba ve namaz takibi, sistemin sunduğu başlangıç şablonlarıdır; kurumlar bunların dışında da kendi programlarını oluşturabilir. | Veri modeli: `program` (tablo: `programs`); sistem başlangıç şablonları `starter_program_templates` kataloğundadır, program bunlardan birine isteğe bağlı bağlanır veya serbest tanımlanır (`VERI_MODELI.md` §11.1–§11.2). Arayüz: "Program". | "Nurlu Kart programı", "kart/bölüm sistemi", "ezber takibi" gibi eski takip yöntemleri; yeni sistemde bunların hepsi birer "program" örneği veya başlangıç şablonu olarak sunulabilir. |
| Program şablonu | Önceden hazırlanmış, birden fazla günlük içerik sırasını (örn. 20 günlük plan) tanımlayan; takvime toplu olarak dağıtılabilen kalıp. | Veri modeli: `program_template` (tablo: `program_templates`, içerik sırası ve gün sayısı taşır). Arayüz: "Program şablonu". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Plan | Bir programın, belirli bir tarihe/güne atanmış tekil iş/içerik birimi. İki üretim yolu vardır: hocanın elle tek tek eklemesi (bkz. "Günlük görev") veya bir program şablonunun takvime dağıtılması. | Veri modeli: `plan_item` (tablo: `plan_items`; `source = MANUAL` veya `source = TEMPLATE` ile üretim yolu ayırt edilir). Arayüz: "Plan" / "Günün planı". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Günlük görev | Hocanın, program şablonu kullanmadan, zamanı geldiğinde elle tek tek eklediği ve bir "plan" kaydına dönüşen iş birimi. | Veri modeli: `plan_item` kaydının `source = MANUAL` özel durumu; ayrı bir tablo değildir. Arayüz: "Günlük görev". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| İçerik | Bir plan veya programa bağlı, metin ve isteğe bağlı PDF ekinden oluşan öğretim materyali (örn. bir sûrenin metni, bir ödev açıklaması). | Veri modeli: `content` (tablo: `contents`); yalnız metinde `content_type = TEXT`, PDF eki varsa `content_type = TEXT_WITH_PDF` ve `pdf_asset_id` zorunludur (`VERI_MODELI.md` §10.2). Arayüz: "İçerik". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Değerlendirme | Bir programın seçtiği; hangi alanların (Tamamlandı/Tamamlanmadı zorunlu, isteğe bağlı 10 üzerinden puan, not, tekrar gerekli) kullanılacağını tanımlayan şema. | Veri modeli: ayrı `evaluation_schemas` tablosu yoktur; etkin alanlar `program_versions` üzerindeki `evaluation_*_enabled` sütunlarıyla sürümlenir (`VERI_MODELI.md` §11.3). Arayüz: "Değerlendirme ayarları". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| İlerleme | Bir öğrencinin, bir plan/program kalemi için değerlendirme şemasına göre girilmiş gerçek sonuç kaydı (örn. "Tamamlandı", puan: 8). | Veri modeli: `progress_record` (tablo: `progress_records`; `plan_item_id` + `student_id` + şema alanlarının değerleri). Arayüz: "İlerleme". | Yoklama işaretleri, Nurlu Kart/sûre işaretleri, Elif-Ba notu, namaz ve Kur'an sayfa takibi gibi eski genel takip pratikleri; yeni sistemde bunların karşılığı yapılandırılmış "ilerleme" kaydıdır. |
| Yoklama oturumu | Bir sınıf için bir güne ait, tek bir yoklama sürecini temsil eden konteyner kayıt (sınıf + tarih ile benzersizdir; sınıf başına günde bir tane). | Veri modeli: `attendance_session` (tablo: `attendance_sessions`; `class_id` + `date` üzerinde benzersizlik kısıtı). Arayüz: "Bugünkü yoklama". | "Yoklama" — genel bilinen terim. Eski dosyalardaki tam kullanım tespit edilemedi — eski kaynaklar bu repoda yok. |
| Yoklama kaydı | Bir yoklama oturumu içinde, her öğrenci için tutulan tekil durum satırı (Geldi/Gelmedi veya kuruma özel ek durum; değişiklik geçmişiyle birlikte). | Veri modeli: `attendance_record` (tablo: `attendance_records`; `attendance_session_id` + `student_id`). Arayüz: yoklama oturumu ekranındaki öğrenci satırı. | Tespit edilemedi — eski kaynaklar bu repoda yok. Genel karşılığı, "Yoklama" sürecinin öğrenci bazlı görünümüdür. |
| Denetim kaydı | Kritik bir değişikliğin kim, ne, ne zaman ve hangi eski/yeni değerle yaptığını pasif olarak saklayan geçmiş kaydı. | Veri modeli: `audit_log` (tablo: `audit_logs`; kurum, kullanıcı, işlem türü, hedef kayıt, eski değer, yeni değer, zaman, istek/cihaz bağlamı alanları). Arayüz: "İşlem geçmişi". | Eski sistemde "İşlem Geçmişi" bulunuyordu. Yeni denetim kaydı bundan daha kapsamlıdır (kurum, kullanıcı, hedef kayıt, eski/yeni değer, istek/cihaz bağlamı gibi zorunlu alanlar taşır), yetki kontrollüdür (yalnızca yetkili roller görebilir) ve değiştirilemez niteliktedir (kayıt sonradan güncellenip silinemez). |
| Geri alma | Bir denetim kaydına dayanarak, orijinal işlemi silmeden ters bir işlem uygulayıp önceki duruma dönmeyi sağlayan, yalnızca desteklenen işlem türleri için tanımlı komut. | Veri modeli: mevcut `audit_log` kaydını referans alan yeni bir işlem; kendisi de yeni bir `audit_log` kaydı üretir. Arayüz: "Geri al". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Operasyonel veri | Bir sınıfın eğitim faaliyetini ve öğrencilerini doğrudan anlatan öğrenci/veli, yoklama, ilerleme, değerlendirme ve normal öğretmen notu verisi. Hoca yalnız atandığı sınıfta ve ilgili işlem yetkisiyle erişebilir; kurum kapsamlı yönetim izni bu sınırı genişletmez. | Veri modeli: tek tablo değildir; `students`, `student_guardians`, `attendance_*`, `progress_records` ve ilişkili sınıf kapsamlı kayıtlardır. Arayüz: öğrenci, yoklama ve ilerleme ekranlarındaki asıl kayıtlar. | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Metaveri | Yetkili bir yönetim işlemini gerçekleştirmek için gereken sınırlı kurum/sınıf/hoca tanımlayıcı ve liste bilgisidir; öğrenci/veli/yoklama/ilerleme ayrıntısı içermez. | Veri modeli: ayrı tablo değildir; API'nin role ve işleme göre daralttığı sınırlı alan görünümüdür (`VERI_MODELI.md` §16, `API_GENEL_KURALLARI.md` §4). Arayüz: yönetim seçim listelerindeki sınırlı özet. | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Destek modu | Platform yöneticisinin belirli bir hedef kurum bağlamını açıkça seçerek destek amacıyla kuruma eriştiği, her erişimin denetim kaydı ürettiği istisnai çalışma bağlamı. Kurumlar arası veriyi tek görünümde karıştırmaz. | Veri modeli: ayrı rol değildir; global platform yöneticisi + açık hedef kurum bağlamı + zorunlu denetim olayıdır. Arayüz: "Destek modu" / seçili kurum göstergesi. | Tespit edilemedi — eski kaynaklar bu repoda yok. |

---

## 3. Birbirine benzeyen terimlerin farkı

### 3.1. Kişi / Kullanıcı / Öğrenci

- **Kişi**, ad-soyad-telefon taşıyan en temel kayıttır; hoca, yönetici, öğrenci, anne ve baba
  hepsi birer kişi kaydı paylaşır.
- **Kullanıcı**, bir kişinin sisteme giriş yapabilen halidir (kimlik doğrulama hesabı). Kullanıcının
  kendisi tek bir role sabitlenmez; platform yöneticisi rolü global, kurum yöneticisi ve hoca
  rolleri kurum üyeliği bağlamında atanır ve bir kullanıcı birden fazla rol atamasına sahip
  olabilir. Sınıfa erişim ayrı bir rol ataması değil, hoca–sınıf atamasıdır. İlk sürümde yalnızca
  platform yöneticisi, kurum yöneticisi ve hoca rolü atanmış kişiler kullanıcı olabilir; öğrenci
  ve veli kişi kaydına sahiptir ama kullanıcı değildir.
- **Öğrenci**, kişi çekirdeğine ek kurum/sınıf/dönem/durum bilgisi eklenmiş özel bir kişi türüdür.

### 3.2. Kurum / Sınıf

- **Kurum**, veri izolasyonunun üst sınırıdır; bütün diğer kavramlar (sınıf, öğrenci, program vb.)
  bir kuruma bağlıdır.
- **Sınıf**, bir kurumun içinde, belirli bir dönemde çalışan alt birimdir; bir kurumun birden
  fazla sınıfı olabilir.

### 3.3. Program / Program şablonu / Plan / Günlük görev

- **Program**, bir takip türünün sınıfta çalışan tek örneğidir; en genel kavramdır. Ezber, sûre,
  kart, Kur'an sayfa, Elif-Ba ve namaz takibi, sistemin sunduğu hazır şablon/başlangıç
  seçenekleridir — kurumlar bunların dışında kendi takip düzenini de tanımlayabilir.
- **Program şablonu**, bir programın içeriğini birden fazla güne önceden sıralayan, tekrar
  kullanılabilir kalıptır.
- **Plan**, bir programın belirli bir tarihe atanmış somut iş birimidir; hem şablon dağıtımından
  hem elle eklemeden üretilebilir.
- **Günlük görev**, "plan" kaydının elle (şablonsuz) üretildiği özel durumun adıdır. Yani her
  günlük görev bir plan kaydıdır, ama her plan kaydı günlük görev değildir (şablondan gelenler
  değildir).

### 3.4. Değerlendirme / İlerleme

- **Değerlendirme**, hangi alanların kullanılacağını tanımlayan şemadır (tanım/ayar seviyesi).
- **İlerleme**, o şemaya göre bir öğrenci için gerçekten girilmiş sonuç kaydıdır (veri seviyesi).

### 3.5. Yoklama oturumu / Yoklama kaydı

- **Yoklama oturumu**, sınıf + gün seviyesinde tek bir konteynerdir (günde bir tane).
- **Yoklama kaydı**, o oturum içinde her öğrenci için tutulan satırdır (oturum başına öğrenci
  sayısı kadar).

### 3.6. Denetim kaydı / Geri alma

- **Denetim kaydı**, geçmişte ne olduğunu pasif olarak saklayan bilgidir; kendisi bir işlem
  değildir.
- **Geri alma**, bu bilgiye dayanarak yeni bir düzeltici işlem başlatan aktif komuttur; çalıştığında
  kendisi de yeni bir denetim kaydı oluşturur. Geri alma, var olan denetim kaydını silmez veya
  değiştirmez.

### 3.7. Anne / Baba / Veli

- **Anne** ve **Baba**, ilk sürümde giriş hesabı olmayan, öğrenciyle ilişki üzerinden bağlı ayrı
  kişi kayıtlarıdır.
- **Veli**, ana planda anne/babayı (ve ileride gerekirse vasiyi) kapsayan üst kavram olarak
  kullanılır; ilk sürümde kendi başına ayrı bir kayıt türü değildir, anne/baba ilişki türleri
  üzerinden ifade edilir.

---

## 4. Ana ürün planıyla uyum kontrolü

- Rol ve erişim tanımları `URUN_VE_UYGULAMA_PLANI.md` bölüm 5 ile uyumludur.
- Kurum, dönem, sınıf tanımları bölüm 6 ile uyumludur.
- Kişi, öğrenci, anne/baba tanımları bölüm 7 ile uyumludur.
- Program, program şablonu, plan, günlük görev, içerik, değerlendirme, ilerleme tanımları bölüm
  8.6–8.8 ile uyumludur.
- Denetim kaydı ve geri alma tanımları bölüm 8.10 ile uyumludur.
- Yoklama oturumu ve yoklama kaydı tanımları bölüm 8.5 ile uyumludur.
- Belgede ana plana veya görev planına aykırı bir tanım bulunmamaktadır.

---

## 5. Güncel kararlarla uyum notları

- "Plan" ve "günlük görev" arasındaki ilişki (günlük görev = plan kaydının manuel üretilme
  biçimi) ana planın 8.7 bölümündeki iki üretim yönteminden (elle ekleme / şablon dağıtımı)
  çıkarılmıştır; `VERI_MODELI.md` §11.6 bunu bağlayıcı olarak `plan_items.source = MANUAL`
  kuralıyla karşılar.
- Veri modeli tercih edilen İngilizce adlar artık `VERI_MODELI.md`deki bağlayıcı tablo/alan
  adlarıyla güncellenmiştir; bu sözlükteki adlar o şemaya yapılan açıklayıcı başvurulardır.
- Program teriminin eski takip yöntemlerinden (ezber, sûre/dua, kart/bölüm, Kur'an sayfa,
  Elif-Ba, namaz) sistemin sunduğu başlangıç seçenekleri olduğu kararı, `VERI_MODELI.md`
  §11.1–§11.2'deki açık katalog ve isteğe bağlı program bağlantısıyla kesinleşmiştir.

## 6. Bilinen sınırlamalar

- Eski Excel/HTML/Apps Script dosyaları bu repoda bulunmadığından "eski sistem karşılığı" sütunu
  yalnızca ana plandaki açık referanslara ve genel bilinen terim kullanımına dayanmaktadır;
  eski dosyaların gerçek iç yapısına dair hiçbir doğrulanmamış varsayımda bulunulmamıştır. Birden
  fazla terimde bu nedenle "tespit edilemedi — eski kaynaklar bu repoda yok" notu bulunmaktadır.

## 7. Kapsam dışı bırakılanlar

- Eski dosyaların doğrudan incelenip terim eşleşmesinin kesinleştirilmesi (eski dosyalara erişim
  gerektirir; bu görevin kapsamında değildir).
- Yetki matrisi, veri modeli, API sözleşmesi gibi sonraki Dalga 0 görevlerinin içeriği
  (`P-002`–`P-014`).
- Terimlerin İngilizce arayüz çevirisi (ilk sürüm dili Türkçedir; ana plan karar günlüğü).
