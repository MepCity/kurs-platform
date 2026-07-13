# Terimler Sözlüğü

| Alan | Değer |
|---|---|
| Görev | P-001 — Terimler sözlüğünü oluştur |
| Belge sürümü | 1.0 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Son güncelleme | 13 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, ürün ve mimari planında (`URUN_VE_UYGULAMA_PLANI.md`) ve görev planında
(`AGENT_GOREV_PLANI.md`) geçen çekirdek kavramları tek ve çelişkisiz biçimde tanımlar. Buradan
sonraki bütün görevler (yetki matrisi, veri modeli, API sözleşmesi, mobil ekranlar) bu terimleri
aynı anlamda kullanmalıdır.

Bu belge yeni bir ürün kararı almaz; yalnızca onaylı ana plandaki kavramları netleştirir. Ana
plana aykırı bir tanım bulunmamaktadır.

Belge, bölüm 2'deki terim tablosunda 20 çekirdek terim tanımlar: kurum, kullanıcı, kişi, hoca,
öğrenci, anne, baba, sınıf, dönem, program, program şablonu, plan, günlük görev, içerik,
değerlendirme, ilerleme, yoklama oturumu, yoklama kaydı, denetim kaydı, geri alma.

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
| Kullanıcı | Sisteme kimlik doğrulamasıyla giriş yapabilen hesap; bir kişi kaydına bağlıdır. Kullanıcının kendisi tek bir role sabitlenmez — kullanıcıya global (platform yöneticisi), kurum (kurum yöneticisi) veya gerektiğinde sınıf bağlamında (hoca) ayrı rol atamaları tanımlanır. Bir kullanıcı birden fazla rol atamasına sahip olabilir (örn. aynı kişi bir kurumda yönetici, başka bir kurumda hoca olabilir). | Veri modeli: `user` (tablo: `users`, `person_id` FK ile kişiye bağlanır); rol ataması ayrı bir `role_assignment` kaydında (`scope = GLOBAL/ORGANIZATION/CLASS`, `role`, `scope_id`) tutulur. Arayüz: "Kullanıcı". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Kişi | Hoca, yönetici, öğrenci ve velinin paylaştığı ortak çekirdek kayıt; ad, soyad ve telefon gibi temel bilgileri taşır. Bir kişinin kullanıcı hesabı olmayabilir (örn. ilk sürümde öğrenci ve veli). | Veri modeli: `person` (tablo: `people`). Arayüz: ayrı bir ekran adı yoktur; rol bazlı ekranlarda (öğrenci, hoca vb.) üzerinden gösterilir. | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Hoca | Bir kullanıcının, ilgili kurum içinde bir veya birden fazla sınıfa atanmış olan rolü/üyeliğidir; global bir kimlik değildir. Hoca, yalnızca kendisine bu rol atanmış sınıflarda yoklama, program ve ilerleme işlemlerini yapabilir. Aynı kullanıcı başka bir kurumda veya bağlamda farklı bir role sahip olabilir. | Veri modeli: kurum/sınıf bağlamında `role_assignment` kaydında `role = TEACHER`; kişi + kullanıcı + sınıf ataması ile ifade edilir. Arayüz: "Hoca". | "Öğretmen" / "hoca" — genel bilinen terim. Eski dosyalardaki tam kullanım tespit edilemedi — eski kaynaklar bu repoda yok. |
| Öğrenci | Bir kuruma ve (varsa) bir aktif sınıfa kayıtlı, kişi çekirdeğine ek olarak kayıt tarihi ve durum (aktif/pasif/arşivlenmiş) bilgisi taşıyan kayıt. | Veri modeli: `student` (tablo: `students`, `person_id` FK). Arayüz: "Öğrenci". | "Öğrenci" — genel bilinen terim; ana plan eski veri kaynağının Excel/Google Sheets olduğunu belirtir, ancak tam alan yapısı tespit edilemedi — eski kaynaklar bu repoda yok. |
| Anne | Öğrenciyle `anne` ilişki türü üzerinden bağlı, ayrı bir kişi kaydı; ilk sürümde giriş hesabı yoktur. | Veri modeli: `person` kaydı + `student_guardian` ilişkisinde `relation_type = MOTHER`. Arayüz: "Anne". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Baba | Öğrenciyle `baba` ilişki türü üzerinden bağlı, ayrı bir kişi kaydı; ilk sürümde giriş hesabı yoktur. | Veri modeli: `person` kaydı + `student_guardian` ilişkisinde `relation_type = FATHER`. Arayüz: "Baba". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Sınıf | Bir kuruma ve bir eğitim dönemine bağlı; öğrenci ve hoca üyeliklerini barındıran çalışma birimi. Bir öğrenci ilk sürümde aynı kurumda aynı anda yalnızca bir aktif sınıfta bulunabilir. | Veri modeli: `class` (tablo: `classes`; sınıf-hoca ve sınıf-öğrenci ilişkileri ayrı ilişki tablolarında). Arayüz: "Sınıf". | "Sınıf" — örn. eski "Fındıklı" sınıf sitesindeki tek sınıf kavramının karşılığı; yeni sistemde kurum başına birden fazla sınıf olabilir. |
| Dönem (Eğitim dönemi) | Kurumun tanımladığı, başlangıç ve bitiş tarihi olan; tatiller ve çalışılmayan günlerin de tanımlandığı zaman aralığı. Sınıflar bir döneme bağlıdır. | Veri modeli: `term` (tablo: `terms`). Arayüz: "Eğitim dönemi" / "Dönem". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Program | Bir sınıfta kullanılan, yapılandırılabilir eğitim takip çekirdeğinin tek örneği (örn. bir "günlük ezber" programı, bir "namaz takibi" programı). Aynı sınıfta birden fazla program aynı anda aktif olabilir. Ezber, sûre/dua listesi, kart/bölüm, Kur'an sayfa takibi, Elif-Ba ve namaz takibi, sistemin sunduğu hazır şablon/başlangıç seçenekleridir; kurumlar bunların dışında da kendi yapılandırılabilir takip düzenlerini oluşturabilir. | Veri modeli: `program` (tablo: `programs`); hazır şablon/başlangıç seçenekleri ile serbest tanımlı programların nasıl ayrıştırılacağı (kapalı enum mu, açık yapılandırma şeması mı) bağlayıcı olarak `P-008 Çekirdek veri modeli taslağı` görevinde kararlaştırılacaktır. Arayüz: "Program". | "Nurlu Kart programı", "kart/bölüm sistemi", "ezber takibi" gibi eski takip yöntemleri; yeni sistemde bunların hepsi birer "program" örneği veya hazır şablon seçeneği olarak sunulabilir. |
| Program şablonu | Önceden hazırlanmış, birden fazla günlük içerik sırasını (örn. 20 günlük plan) tanımlayan; takvime toplu olarak dağıtılabilen kalıp. | Veri modeli: `program_template` (tablo: `program_templates`, içerik sırası ve gün sayısı taşır). Arayüz: "Program şablonu". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Plan | Bir programın, belirli bir tarihe/güne atanmış tekil iş/içerik birimi. İki üretim yolu vardır: hocanın elle tek tek eklemesi (bkz. "Günlük görev") veya bir program şablonunun takvime dağıtılması. | Veri modeli: `plan_item` (tablo: `plan_items`; `source = MANUAL` veya `source = TEMPLATE` ile üretim yolu ayırt edilir). Arayüz: "Plan" / "Günün planı". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Günlük görev | Hocanın, program şablonu kullanmadan, zamanı geldiğinde elle tek tek eklediği ve bir "plan" kaydına dönüşen iş birimi. | Veri modeli: `plan_item` kaydının `source = MANUAL` özel durumu; ayrı bir tablo değildir. Arayüz: "Günlük görev". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| İçerik | Bir plan veya programa bağlı, metin ve isteğe bağlı PDF ekinden oluşan öğretim materyali (örn. bir sûrenin metni, bir ödev açıklaması). | Veri modeli: `content` (tablo: `contents`; `content_type = TEXT`, isteğe bağlı `pdf_asset_id`). Arayüz: "İçerik". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| Değerlendirme | Bir programın seçtiği; hangi alanların (Tamamlandı/Tamamlanmadı zorunlu, isteğe bağlı 10 üzerinden puan, not, tekrar gerekli) kullanılacağını tanımlayan şema. | Veri modeli: `evaluation_schema` (program üzerinde alan olarak veya `evaluation_schemas` tablosu; etkin alan bayraklarını taşır). Arayüz: "Değerlendirme ayarları". | Tespit edilemedi — eski kaynaklar bu repoda yok. |
| İlerleme | Bir öğrencinin, bir plan/program kalemi için değerlendirme şemasına göre girilmiş gerçek sonuç kaydı (örn. "Tamamlandı", puan: 8). | Veri modeli: `progress_record` (tablo: `progress_records`; `plan_item_id` + `student_id` + şema alanlarının değerleri). Arayüz: "İlerleme". | Yoklama işaretleri, Nurlu Kart/sûre işaretleri, Elif-Ba notu, namaz ve Kur'an sayfa takibi gibi eski genel takip pratikleri; yeni sistemde bunların karşılığı yapılandırılmış "ilerleme" kaydıdır. |
| Yoklama oturumu | Bir sınıf için bir güne ait, tek bir yoklama sürecini temsil eden konteyner kayıt (sınıf + tarih ile benzersizdir; sınıf başına günde bir tane). | Veri modeli: `attendance_session` (tablo: `attendance_sessions`; `class_id` + `date` üzerinde benzersizlik kısıtı). Arayüz: "Bugünkü yoklama". | "Yoklama" — genel bilinen terim. Eski dosyalardaki tam kullanım tespit edilemedi — eski kaynaklar bu repoda yok. |
| Yoklama kaydı | Bir yoklama oturumu içinde, her öğrenci için tutulan tekil durum satırı (Geldi/Gelmedi veya kuruma özel ek durum; değişiklik geçmişiyle birlikte). | Veri modeli: `attendance_record` (tablo: `attendance_records`; `attendance_session_id` + `student_id`). Arayüz: yoklama oturumu ekranındaki öğrenci satırı. | Tespit edilemedi — eski kaynaklar bu repoda yok. Genel karşılığı, "Yoklama" sürecinin öğrenci bazlı görünümüdür. |
| Denetim kaydı | Kritik bir değişikliğin kim, ne, ne zaman ve hangi eski/yeni değerle yaptığını pasif olarak saklayan geçmiş kaydı. | Veri modeli: `audit_log` (tablo: `audit_logs`; kurum, kullanıcı, işlem türü, hedef kayıt, eski değer, yeni değer, zaman, istek/cihaz bağlamı alanları). Arayüz: "İşlem geçmişi". | Eski sistemde "İşlem Geçmişi" bulunuyordu. Yeni denetim kaydı bundan daha kapsamlıdır (kurum, kullanıcı, hedef kayıt, eski/yeni değer, istek/cihaz bağlamı gibi zorunlu alanlar taşır), yetki kontrollüdür (yalnızca yetkili roller görebilir) ve değiştirilemez niteliktedir (kayıt sonradan güncellenip silinemez). |
| Geri alma | Bir denetim kaydına dayanarak, orijinal işlemi silmeden ters bir işlem uygulayıp önceki duruma dönmeyi sağlayan, yalnızca desteklenen işlem türleri için tanımlı komut. | Veri modeli: mevcut `audit_log` kaydını referans alan yeni bir işlem; kendisi de yeni bir `audit_log` kaydı üretir. Arayüz: "Geri al". | Tespit edilemedi — eski kaynaklar bu repoda yok. |

---

## 3. Birbirine benzeyen terimlerin farkı

### 3.1. Kişi / Kullanıcı / Öğrenci

- **Kişi**, ad-soyad-telefon taşıyan en temel kayıttır; hoca, yönetici, öğrenci, anne ve baba
  hepsi birer kişi kaydı paylaşır.
- **Kullanıcı**, bir kişinin sisteme giriş yapabilen halidir (kimlik doğrulama hesabı). Kullanıcının
  kendisi tek bir role sabitlenmez; global, kurum veya sınıf bağlamında ayrı ayrı rol ataması
  tanımlanır ve bir kullanıcı birden fazla rol atamasına sahip olabilir. İlk sürümde yalnızca
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

## 5. Varsayımlar

- "Plan" ve "günlük görev" arasındaki ilişki (günlük görev = plan kaydının manuel üretilme
  biçimi) ana planın 8.7 bölümündeki iki üretim yönteminden (elle ekleme / şablon dağıtımı)
  çıkarılmıştır; ana planda bu iki kavram arasındaki veri modeli ilişkisi ayrıca yazılı
  değildir. Bu, bir veri modeli kararı değil, terim netleştirmesidir; `P-008 Çekirdek veri
  modeli taslağı` görevinde teyit edilmelidir.
- Veri modeli tercih edilen adlar (İngilizce tablo/alan adları) ileri seviye bir öneridir;
  bağlayıcı şema kararı `P-008` görevinde verilecektir. Bu ad önerileri hiçbir şekilde `P-008`
  için bağlayıcı değildir.
- Program teriminin eski takip yöntemlerinden (ezber, sûre/dua, kart/bölüm, Kur'an sayfa,
  Elif-Ba, namaz) yalnızca sistemin sunduğu hazır şablon/başlangıç seçenekleri olduğu; kesin
  veri modeli/enum kararının `P-008` görevine bırakıldığı varsayılmıştır.

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
