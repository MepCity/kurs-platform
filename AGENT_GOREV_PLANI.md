# Agent Odaklı Görev ve Uygulama Planı

| Alan | Değer |
|---|---|
| Belge sürümü | 1.2 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Amaç | Ürünü küçük, bağımsız, doğrulanabilir ve agentlara atanabilir işlere bölmek |
| Görev üst sınırı | Bir görev tercihen 2–6 saat, en fazla bir iş günü |
| Mimari yaklaşım | Modüler monolit; mikroservis değil |
| Son güncelleme | 15 Temmuz 2026 |

---

## 1. Bu çalışma modeli neden kullanılacak?

Yapay zekâ agentları paralel ve hızlı çalışabilir; ancak geniş, belirsiz görevlerde farklı
varsayımlar yaparak mimariyi parçalayabilir. Bu nedenle ürün “bir agent backend'i yapsın,
diğeri mobil uygulamayı yapsın” gibi büyük parçalara ayrılmayacaktır.

Bunun yerine:

- Önce ortak sözleşmeler hazırlanır.
- Her görev tek bir ölçülebilir çıktı üretir.
- Görev bağımlılıkları açıkça yazılır.
- Aynı dosya veya veri alanında paralel sahiplik engellenir.
- Frontend ve backend ancak API sözleşmesinden sonra paralelleştirilir.
- Her paralel çalışma dalgasının sonunda entegrasyon kapısı bulunur.

Bu yaklaşım mini servislerin bağımsızlığını görev seviyesinde sağlar; üretim sistemini erken
mikroservis karmaşıklığına sokmaz.

---

## 2. Görev boyutlandırma

| Boyut | Tahmini süre | Kullanım |
|---|---:|---|
| XS | 1–2 saat | Tek belge bölümü, küçük test, sınırlı UI düzeltmesi |
| S | 2–4 saat | Tek API, tek ekran durumu, tek migration |
| M | 4–8 saat | Küçük dikey özellik veya entegrasyon |
| L | 1 günden fazla | Doğrudan yapılamaz; önce XS/S/M görevlere bölünür |

Bir agenta aynı anda yalnızca bir görev atanır. “Kullanıcı yönetimini yap” geçersiz görevdir;
“Hoca oluşturma API sözleşmesini yaz” geçerli görevdir.

---

## 3. Görev durumları

- `BACKLOG`: Henüz ayrıntılandırılmadı.
- `PLANNED`: Kapsamı var, bağımlılıkları bekliyor.
- `READY`: Bağımlılıkları tamam, agent alabilir.
- `IN_PROGRESS`: Tek bir agent çalışıyor.
- `REVIEW`: Çıktı ve testler inceleniyor.
- `BLOCKED`: Açık engeli var.
- `DONE`: Kabul ölçütleri karşılandı ve entegre edildi.

Bir görevin yalnızca kodunun yazılması `DONE` olması için yeterli değildir.

Güncel durumlar ve projenin kaldığı yer `GOREV_DURUMU.md` dosyasında tutulur. Bu ayrım
sayesinde bu belge sabit yol haritası olarak kalırken görev panosu her çalışma gününde kısa
ve güvenli biçimde güncellenebilir.

Paralel branch çakışmalarını önlemek için çalışan agentlar görev panosunu değiştirmez.
`GOREV_DURUMU.md`, PR merge edildikten ve kabul ölçütleri koordinatör tarafından
doğrulandıktan sonra ayrı bir koordinasyon değişikliğiyle güncellenir.

---

## 4. Standart görev kartı

Her yeni görev aşağıdaki biçimde oluşturulmalıdır:

```text
Kimlik:
Başlık:
Durum:
Boyut:
Amaç:
Kapsam içi:
Kapsam dışı:
Bağımlılıklar:
Sahip olunan modül/dosyalar:
Beklenen çıktılar:
Kabul ölçütleri:
Test/doğrulama:
Teslim notu:
```

Görevde “uygun şekilde”, “gerekli yerler” veya “tamamını yap” gibi ölçülemeyen ifadeler
kullanılmamalıdır.

---

## 5. Modül sınırları

İlk modüler monolit aşağıdaki iş alanlarına ayrılacaktır:

| Kod | Modül | Sorumluluk |
|---|---|---|
| CORE | Ortak çekirdek | Kimlik türleri, hata modeli, zaman, sayfalama, ortak sözleşmeler |
| IAM | Kimlik ve erişim | Giriş, oturum, cihaz, roller ve izinler |
| ORG | Kurum | Kurum yaşam döngüsü, marka ve kurum ayarları |
| TERM | Dönem ve takvim | Eğitim dönemi, çalışma günleri ve tatiller |
| CLS | Sınıf | Sınıf ve hoca/öğrenci üyelikleri |
| PEOPLE | Kişiler | Ortak kişi, öğrenci, anne ve baba kayıtları |
| ATT | Yoklama | Günlük yoklama, durumlar ve düzeltmeler |
| CONTENT | İçerik | Metin, PDF ve içerik kataloğu |
| PROGRAM | Program | Program şablonu, plan ve takvim dağıtımı |
| PROGRESS | İlerleme | Tamamlama, puan, not ve tekrar gerekli |
| AUDIT | Denetim | Değişiklik geçmişi ve geri alma |
| EXPORT | Dışa aktarma | Excel raporları ve güvenli dosya teslimi |
| SYNC | Senkronizasyon | Yerel kuyruk, idempotency, sürüm ve çakışma |
| REALTIME | Gerçek zaman | Sınıf olayları ve istemci güncellemesi |
| NOTIFY | Bildirim | Sonraki faz push ve duyuru altyapısı |

Modüller ayrı deploy edilen servisler değildir. Tek backend içinde sınırları korunmuş
paketlerdir. Veritabanı ortak olabilir; bir modül başka modülün tablolarını gelişigüzel
değiştiremez.

Görev kimliği önekleri her zaman birebir modül kodu değildir. `PROG-*` görevleri `PROGRESS`
modülüne; `PERM-*` görevleri IAM içindeki rol/izin politikasına; `STAFF-*` görevleri IAM ve CLS
arasındaki hoca yönetimi akışına; `UI-*` görevleri ortak mobil kabuk ve bileşenlere aittir.
`PLAN-*` kimlikleri ürün/agent çalışma sözleşmelerini oluşturan repo hazırlık görevleridir ve
uygulama modülü değildir. Bu eşleme sahipliği açıklar, yeni deploy sınırı oluşturmaz.

---

## 6. Paralel çalışma kuralları

### 6.1. Paralel yapılabilecek işler

- Onaylı API sözleşmesinden sonra mobil ekran ve backend uygulaması
- Backend geliştirmesiyle bağımsız UI bileşenleri ve tasarım sistemi
- Birbirinden bağımsız modüllerin belge ve test senaryoları
- Aynı şemaya dokunmayan modüller
- Uygulama koduyla bağımsız QA senaryo hazırlığı

### 6.2. Paralel yapılmayacak işler

- Veri modeli kesinleşmeden migration ve repository geliştirme
- Yetki matrisi kesinleşmeden erişim politikaları
- Aynı API'nin iki agent tarafından ayrı tasarlanması
- Aynı ortak paketin veya aynı migration zincirinin eşzamanlı değiştirilmesi
- Mobil ve backend tarafının ayrı varsayımlarla veri modeli üretmesi
- Entegrasyon tamamlanmadan sonraki bağımlı dalgaya geçme

### 6.3. Agent sahipliği

Her paralel dalgada şu roller atanabilir:

- **Sözleşme sahibi:** API/veri/olay sözleşmesini korur.
- **Backend sahibi:** Onaylı sözleşmeyi uygular.
- **Mobil sahibi:** Onaylı sözleşmeye karşı ekran ve yerel davranışı uygular.
- **Test sahibi:** Kabul ve entegrasyon senaryolarını bağımsız doğrular.
- **Entegrasyon sahibi:** Alt çıktıları birleştirir; yeni özellik eklemez.

Bu roller görev bazındadır; kalıcı ekip unvanı olmak zorunda değildir.

---

## 7. Çalışma dalgaları

```text
Dalga 0: Ürün ve sözleşmeler
    ↓
Dalga 1: Teknoloji kararları ve iskelet
    ↓
Dalga 2: Kimlik + kurum + mobil kabuk
    ↓
Dalga 3: Sınıf + hoca yetkileri + öğrenci
    ↓
Dalga 4: Yoklama dikey dilimi
    ↓
Dalga 5: Program + içerik + takvim
    ↓
Dalga 6: İlerleme + denetim + geri alma
    ↓
Dalga 7: Excel + sertleştirme + pilot
    ↓
Dalga 8: Web + veli/öğrenci + bildirimler
```

Her dalganın sonunda entegrasyon ve kabul görevi vardır. Kabul tamamlanmadan sonraki dalga
`READY` durumuna geçirilmez.

---

## 8. Repo hazırlık görevleri ve Dalga 0 — Ürün/sözleşme görevleri

Dalga öncesi planlama ve sonradan yapılan çapraz sözleşme düzeltmeleri uygulama modülü
değildir; `PLAN-*` kimliğiyle izlenir:

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| PLAN-001 | M | Ana ürün ve mimari planını oluştur | Kullanıcı ürün kararları |
| PLAN-002 | M | Agent odaklı atomik görev planını oluştur | PLAN-001 |
| PLAN-003 | S | Repo agent çalışma kurallarını oluştur | PLAN-001, PLAN-002 |
| PLAN-004 | M | Faz/Dalga ve çapraz sözleşme tutarlılık düzeltmelerini uygula | P-014, harici dokümantasyon incelemesi |
| PLAN-005 | M | Başlangıç maliyeti, sağlayıcı kapıları ve kademeli ortam sözleşmesini uygula | A-001–A-010, ürün sahibi maliyet kararı |

### Dalga 0 — Ürün ve sözleşme görevleri

Bu dalga kodlama içermez. İlk başlanacak kesin sıra buradadır.

| Kimlik | Boyut | Görev | Bağımlılık | Paralel grup |
|---|---:|---|---|---|
| P-001 | S | Terimler sözlüğünü oluştur | Ana plan | A |
| P-002 | S | Aktörleri ve ana kullanım senaryolarını listele | P-001 | B |
| P-003 | M | Ayrıntılı yetki matrisini oluştur | P-001, P-002 | C |
| P-004 | S | Kişisel ve hassas veri envanterini çıkar | P-001 | B |
| P-005 | M | Yönetici mobil bilgi mimarisini çiz | P-002 | D |
| P-006 | M | Hoca mobil bilgi mimarisini çiz | P-002 | D |
| P-007 | S | İlk sürüm ekran envanterini çıkar | P-005, P-006 | E |
| P-008 | M | Çekirdek veri modeli taslağını yaz | P-001, P-003, P-004 | F |
| P-009 | S | API genel kurallarını yaz | P-003, P-008 | G |
| P-010 | M | Senkronizasyon ve çakışma sözleşmesini yaz | P-008, P-009 | G |
| P-011 | S | Denetim ve geri alma ilkelerini detaylandır | P-003, P-008 | G |
| P-012 | S | Excel rapor veri sözleşmesini tanımla | P-008 | H |
| P-013 | M | Kritik test ve kabul planını yaz | P-003, P-009, P-010 | I |
| P-014 | S | Faz 0 bütünlük incelemesi yap | P-001–P-013 | Entegrasyon |

### P-001 ile başlanmasının nedeni

“Kurum”, “sınıf”, “program”, “plan”, “görev”, “içerik”, “ilerleme” ve “değerlendirme” gibi
kelimeler farklı agentlar tarafından farklı yorumlanırsa veri modeli ve API daha baştan
ayrışır. Bu nedenle ilk gerçek görev terimler sözlüğüdür.

### Dalga 0 çıkış kapısı

- Terimler çelişkisizdir.
- Yetkiler rol ve işlem bazında tanımlıdır.
- Ana mobil akışların ekran envanteri vardır.
- Veri modeli taslağı bütün ilk sürüm gereksinimlerini taşır.
- API, sync, audit ve export sözleşmeleri birbirine uyumludur.
- Kritik test senaryoları yazılıdır.

---

## 9. Dalga 1 — Teknoloji kararları ve iskelet

Bu görevler Dalga 0 tamamlanmadan başlatılmaz.

| Kimlik | Boyut | Görev | Bağımlılık | Paralel grup |
|---|---:|---|---|---|
| A-001 | M | Flutter ve alternatif mobil framework karşılaştırması/dikey deneme | P-007, P-010 | A |
| A-002 | M | Backend dili ve framework ADR'si | P-008, P-009 | A |
| A-003 | M | PostgreSQL/hosting sağlayıcısı ADR'si | P-004, P-008 | A |
| A-004 | M | Kimlik doğrulama sağlayıcısı ADR'si | P-003, P-004 | A |
| A-005 | M | Yerel mobil veritabanı/kuyruk ADR'si ve kalıcı yeniden deneme deneyi | P-010 | A |
| A-006 | M | Gerçek zamanlı kanal ADR'si ve iki istemcili yoklama olayı deneyi | P-010 | A |
| A-007 | S | PDF/dosya depolama ADR'si | P-004, P-012 | A |
| A-008 | S | Excel üretme yaklaşımı ADR'si | P-012 | A |
| A-009 | S | Monorepo/repo yapısı ADR'si | A-001, A-002 | B |
| A-010 | S | Geliştirme, staging ve üretim ortam sözleşmesi | A-002, A-003 | B |
| A-004R1 | M | Cognito provisioning, ilk parola ve gerçek mobil PKCE deneyini yap | PLAN-005, A-004, A-010 | C |
| A-004R2 | M | Cognito iptal, platform oturumu ve olay kaybı uzlaştırma deneyini yap | A-004R1 | C |
| A-004R3 | S | Cognito maliyet/operasyon kararını kapat ve geçici kaynakları kaldır | A-004R2 | C |
| A-007R | M | Dosya ihtiyacında S3 erteleme/R2 EU eşdeğerlik kararını doğrula | PLAN-005, A-007, A-010 | Koşullu C |
| A-011 | M | Repo ve modül klasör iskeletini oluştur | A-009 | C |
| A-012 | M | CI kalite kapılarını oluştur | A-011 | D |
| A-013 | S | Ortam değişkeni ve secret yönetimi iskeleti | A-010, A-011, PLAN-005, A-004R3 | D |
| A-014 | S | Loglama ve hata izleme temelini kur | A-011 | D |
| A-015 | S | Dalga 1 iskelet bütünlük incelemesi | A-001–A-014, PLAN-005, A-004R3 | Entegrasyon |

### Dalga 1 paralelliği

`A-011`, PLAN-005/A-004R1–A-004R3 ile paralel yürüyebilir; yalnız ADR-009'daki fiziksel iskelet ve
mimari testleri uygular. Auth/storage portu, provider SDK'sı, gerçek migration, cloud secret,
environment değişkeni veya deployment kaynağı oluşturmaz. `A-012` ve A-014'ün sağlayıcı
bağımsız temeli A-011 sonrasında ilerleyebilir. `A-013`, IAM-001 ve gerçek provider
entegrasyonu PLAN-005 ile A-004R3 tamamlanmadan başlayamaz. `A-007R` yalnız ilk gerçek dosya
ihtiyacı doğduğunda READY yapılır; kapalı alfada uzak depo yoksa kritik yol değildir.

### PLAN-005 sonrası sağlayıcı doğrulama kapıları

- `A-004R1`; AWS `eu-central-1` bölgesinde yalnız sentetik kullanıcılarla Cognito Essentials
  provisioning, username + geçici parola, ilk giriş parola değişimi, kayıp create yanıtı ve
  gerçek mobil Authorization Code + PKCE akışını kanıtlar. Deney kimlik bilgileri yalnız kısa
  ömürlü/local güvenli yüzeyden alınır; repo, artifact ve loga yazılmaz. İlk kaynak açılmadan
  `5 USD` bütçe alarmı kurulur ve yazılı ürün sahibi onayı olmadan `10 USD` üstüne çıkılmaz.
- `A-004R2`; refresh rotation/reuse, provider kesintisi, kullanıcı disable/global token iptali,
  platform tek-cihaz ve kurum-kapsamlı iptal ile olay kaybı uzlaştırmasını kanıtlar. İptal edilmiş
  veya devre dışı bırakılmış provider tokenı yeni platform oturumu üretemez; önceden üretilmiş
  platform token aileleri idempotent iptal edilir ve kaçırılan olay fail-closed uzlaştırılır.
- `A-004R3`; deney kanıtlarını karşılaştırır, nihai sağlayıcı kararını ve IAM-001 girdisini
  kaydeder. Gerçekleşen maliyet ile bütçe alarmı kanıtını raporlar. User pool, app client,
  domain, test kullanıcıları, IAM politikaları ve diğer geçici kaynakların silindiği
  envanter/teardown kanıtıyla doğrulanır. Platform `user_id`,
  üyelik/rol/izin, opaque token ailesi ve `session_generation` bütün görevlerde korunur.
- `A-007R`; dosya gerçekten gerektiğinde mevcut S3 reference profilini erteleyerek koruma ile
  R2 `eu` jurisdiction + immutable key + bağımsız backup modelini karşılaştırır. R2 bucket lock
  S3 object versioning sayılmaz; version-ID, exact-version imha, source/backup yokluk kanıtı ve
  restore sözleşmesi yeniden yazılmadan sağlayıcı değişmez.
- Öğrenci kredisi, ücretsiz kota veya kişisel hesap production sahipliği değildir. Repo, domain,
  yedek ve kurtarma erişimi proje sahibinde kalır; kredi bitiminden en az 60 gün önce ücretli
  veya devredilebilir profile geçiş kanıtlanır.

### Faz 0 teknik doğrulama kanıtları

Dalga ve ürün fazı aynı kavram değildir. `P-001`–`P-014` Dalga 0 belge kapısını kapatmıştır;
ana plandaki Faz 0 teknik kapısı `A-001`–`A-010` ile kapanır. En az şu çalıştırılabilir kanıtlar
zorunludur:

- `A-001`: aday mobil frameworkte iOS ve Android hedeflerini doğrulayan küçük dikey uygulama,
  çalıştırma komutları ve ölçüm/karşılaştırma raporu.
- `A-005`: işlem kuyruğunun uygulama kapanıp açıldıktan sonra korunduğunu, aynı
  `clientMutationId` ile yeniden denendiğini ve kesin sunucu sonucu olmadan silinmediğini
  gösteren deney ve tekrar üretilebilir test adımları.
- `A-006`: iki ayrı istemcinin aynı sınıftaki örnek yoklama değişikliğini almasını,
  kopma-yeniden bağlanmayı ve kanonik sunucu durumuna dönmeyi gösteren deney ve ölçüm kaydı.

ADR metni tek başına bu üç görevin deney kabulünü karşılamaz. Deney kodu üretim iskeleti
değildir; karar sonrasında korunacaksa yeri `A-009`, ortamı `A-010` ile kesinleştirilir.
`A-009` ve `A-010` onaylandığında Faz 0 teknik karar seti tamamlanır; `A-011` ile Faz 1 platform
temeli başlar.

### Dalga 1 çıkış kapısı

- Bütün temel teknoloji kararlarının ADR'si vardır.
- Boş mobil ve backend uygulamaları derlenir.
- Test ve lint kontrolleri CI'da çalışır.
- Ortam sırları repodan ayrıdır.
- Modül sınırları klasör yapısında görünürdür.

---

## 10. Dalga 2 — Kimlik, kurum ve mobil kabuk

### Sözleşme görevleri

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| IAM-001 | S | Giriş/oturum API sözleşmesini kesinleştir | P-003, A-004R3 |
| IAM-002 | S | Cihaz ve oturum iptali sözleşmesini yaz | IAM-001 |
| ORG-001 | S | Kurum yaşam döngüsü API sözleşmesini yaz | P-008, P-009 |
| ORG-002 | S | Marka ayarları sözleşmesini yaz | ORG-001, A-007 |
| UI-001 | S | Mobil tasarım tokenlarını tanımla | P-005, P-006 |
| UI-002 | S | Navigasyon ve rol bazlı menü sözleşmesini yaz | P-003, P-007 |

### Uygulama görevleri

| Kimlik | Boyut | Görev | Bağımlılık | Paralel hat |
|---|---:|---|---|---|
| IAM-003 | M | IAM tabloları, roller ve migration'ı uygula | IAM-001 | Backend |
| IAM-004 | M | Giriş/token değişimi ve provider command akışını uygula | IAM-003 | Backend |
| IAM-005 | M | Refresh ailesi, yenileme, çıkış ve tekrar kullanım tespitini uygula | IAM-004 | Backend |
| IAM-006 | M | Cihaz kaydı, DEVICE_SESSION_REVOKE ve yeniden doğrulamayı uygula | IAM-002, IAM-005 | Backend |
| IAM-007 | M | Mobil giriş ekranını uygula | IAM-001, UI-001 | Mobil |
| IAM-008 | M | Mobil güvenli oturum saklamayı uygula | IAM-002, A-005 | Mobil |
| AUDIT-001A | M | Erken ortak audit çekirdeğini ve temel RLS kapısını oluştur | P-011, A-002, IAM-003, ORG-001 | Backend migration |
| ORG-003 | M | Kurum migration ve repository'sini oluştur | ORG-001, AUDIT-001A | Backend |
| ORG-004 | M | Platform yöneticisi kurum oluşturma API'si | ORG-003 | Backend |
| ORG-005 | M | Kurum adı ve renk ayarları API'si (dosyasız) | ORG-002, ORG-003 | Backend |
| ORG-006 | M | Platform yöneticisi kurum listeleme ekranı | ORG-001, UI-001 | Mobil |
| ORG-007 | M | Mobil kurum oluşturma akışı | ORG-001, UI-001 | Mobil |
| ORG-008 | M | Kurum adı ve renk ayarı mobil akışı (dosyasız) | ORG-002, UI-001 | Mobil |
| UI-003 | M | Ortak düğme, alan, liste ve durum bileşenleri | UI-001 | Mobil |
| UI-004 | M | Rol bazlı mobil kabuk ve navigasyon | UI-002, UI-003 | Mobil |
| IAM-009 | M | Entegrasyon, izolasyon, olay kaybı ve iptal gecikmesi testleri | IAM-004–IAM-008, ORG-004 | Test |
| ORG-009 | M | Dalga 2 dosyasız çekirdek uçtan uca entegrasyon | IAM-003–IAM-008, ORG-003–ORG-008, UI-003–UI-004 | Entegrasyon |

### AUDIT-001A — Erken ortak audit çekirdeği ve migration kapısı

`ORG-001`, kurum yaşam döngüsündeki kritik işlemlerin iş değişikliği, idempotency sonucu ve
audit kaydıyla aynı transaction'da tamamlanmasını zorunlu kılar. Bu nedenle `ORG-003`, Dalga
6'daki tam audit uygulamasını bekleyemez ve audit tablosu yokken audit'i atlayan production
yolu açamaz. `AUDIT-001A` bu bağımlılığı aşağıdaki dar kapsamla çözer:

- Güncel `origin/main` tabanında sıradaki Flyway migration olarak yalnız
  `audit_action_catalog` ve `audit_logs` çekirdeğini oluşturur.
- `VERI_MODELI.md` §13'teki sütun, katalog FK'si, scope/target/operation-group kontrolleri,
  immutable/append-only kuralı, geri alma öz-referansları ve indeksleri uygular.
- `audit_logs` için `ENABLE RLS` ve `FORCE RLS` zorunludur. Yeni runtime rolü,
  `BYPASSRLS`, `SECURITY DEFINER`, tablo-geneli yazma yetkisi veya varsayılan açık policy
  oluşturulmaz; runtime erişimi görev sahibinin rolü tanımlandığında ayrı migration'da verilir.
- Yalnız sınıf kapsamı gerektirmeyen, mevcut sözleşmelerde tanımlı
  `ORG_SETTING_CHANGED` ve `PLATFORM_ADMIN_ORG_ACCESS` katalog satırları eklenir. Yeni audit
  eylem kodu icat edilmez.
- `classes` tablosu henüz mevcut olmadığından `audit_logs.scope_class_id` sütunu korunur fakat
  `AUDIT-001A` boyunca sınıf kapsamlı katalog/audit satırı DB kısıtıyla reddedilir. `classes`
  bileşik FK'si ve sınıf kapsamlı katalog satırları `CLS-002` sonrasındaki `AUDIT-001`e aittir.
- Gerçek PostgreSQL/Testcontainers testleri migration'ın güncel main üzerinde çalıştığını;
  scope ve katalog bütünlüğünü; başka kurum/global-scope karışımının reddini; sınıf kapsamlı
  satırın reddini; geri alma FK/tekillik kurallarını; `UPDATE`/`DELETE` ve yetkisiz runtime
  erişiminin varsayılan olarak reddedildiğini kanıtlar. Docker yoksa bu testler sessizce
  atlanmaz.
- `ORG-003` branch'ine veya `GOREV_DURUMU.md` dosyasına dokunulmaz. Görev merge edildikten
  sonra PR #43 güncel main üzerine rebase edilir; ORG migration'ı bir sonraki sürüm numarasını
  alır ve `org_runtime` için dar audit `INSERT` policy/grant'i ile audit başarısızlığında tüm
  lifecycle transaction'ının geri alındığını kanıtlayan testler aynı PR'da tamamlanır.

Kabul için migration/test/build/kalite kapılarının tamamı geçmeli; değişen dosya kapsamı audit
migration'ı, audit testleri ve zorunlu dar sözleşme hizalamalarıyla sınırlı kalmalıdır.

### Güvenli paralellik

Backend sözleşmeleri onaylandıktan sonra `IAM-004` ile `IAM-007`, `ORG-004` ile `ORG-006`
paralel yapılabilir. Mobil görevler mock API kullanabilir. `ORG-009` gerçek backend ile bütün
dosyasız Dalga 2 akışlarını birleştirir. `ORG-005`/`ORG-008` logo dosyası kabul etmez; logo
backend/mobil akışı Dalga 5'teki `ORG-010`/`ORG-011` görevlerine aittir.
`AUDIT-001A` ile `ORG-003` aynı Flyway zincirine dokunduğundan paralel yürütülmez; PR #43,
`AUDIT-001A` merge edilene kadar incelemede ve merge dışı tutulur.

### Dalga 2 çıkış kapısı

- Platform yöneticisi giriş yapabilir.
- Kurum oluşturabilir ve dosyasız kurum adı/renk ayarlarını değiştirebilir.
- Oturum uygulama yeniden açıldığında güvenli biçimde devam eder.
- Yönetici cihaz oturumunu iptal edebilir.
- Başka kurum bağlamına yetkisiz erişim reddedilir.

---

## 11. Dalga 3 — Sınıf, hoca yetkileri ve öğrenci

### Dönem ve sınıf

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| TERM-001 | S | Dönem/takvim veri ve API sözleşmesi | P-008, P-009 |
| TERM-002 | M | Dönem ve çalışma günü backend'i | TERM-001, ORG-003 |
| TERM-003 | M | Dönem/takvim mobil yönetim ekranı | TERM-001, UI-003 |
| CLS-001 | S | Sınıf ve üyelik API sözleşmesi | P-003, P-008 |
| CLS-002 | M | Sınıf migration ve repository | CLS-001, TERM-002 |
| CLS-003 | M | Sınıf CRUD/arşiv backend'i | CLS-002 |
| CLS-004 | M | Mobil sınıf listeleme ve oluşturma | CLS-001, UI-003 |
| CLS-005 | M | Mobil sınıf düzenleme/arşivleme | CLS-001, CLS-004 |

### Hoca ve yetki

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| PERM-001 | S | Yetki sabitleri ve politika sözleşmesi | P-003 |
| PERM-002 | M | Rol/izin migration ve değerlendirme servisi | PERM-001, IAM-003 |
| STAFF-001 | S | Hoca hesabı ve sınıf atama API sözleşmesi | CLS-001, PERM-001 |
| STAFF-002 | M | Hoca oluşturma backend'i | STAFF-001, PERM-002 |
| STAFF-003 | M | Hoca-sınıf atama backend'i | STAFF-001, CLS-002 |
| STAFF-004 | M | Mobil hoca oluşturma ekranı | STAFF-001, UI-003 |
| STAFF-005 | M | Mobil sınıf ve yetki atama ekranı | STAFF-001, STAFF-004 |
| PERM-003 | M | Kurum/sınıf izolasyonu güvenlik testleri | PERM-002, STAFF-002, STAFF-003 |

### Öğrenci, anne ve baba

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| PEOPLE-001 | S | Kişi/öğrenci/veli API sözleşmesi | P-008, P-009 |
| PEOPLE-002 | M | Kişi ve öğrenci migration'ı | PEOPLE-001, CLS-002 |
| PEOPLE-003 | S | Anne/baba ilişki migration'ı | PEOPLE-001, PEOPLE-002 |
| PEOPLE-004 | M | Öğrenci oluşturma/düzenleme backend'i | PEOPLE-002 |
| PEOPLE-005 | M | Anne/baba yönetimi backend'i | PEOPLE-003 |
| PEOPLE-006 | S | Öğrenci arşivleme/geri yükleme backend'i | PEOPLE-004 |
| PEOPLE-007 | M | Mobil öğrenci listesi ve arama | PEOPLE-001, UI-003 |
| PEOPLE-008 | M | Mobil öğrenci ekleme/düzenleme formu | PEOPLE-001, UI-003 |
| PEOPLE-009 | M | Mobil anne/baba alanları | PEOPLE-001, PEOPLE-008 |
| PEOPLE-010 | S | Mobil arşivleme ve geri yükleme | PEOPLE-006, PEOPLE-007 |
| PEOPLE-011 | M | Öğrenci/veli doğrulama ve izolasyon testleri | PEOPLE-004–PEOPLE-010 |

### Dalga 3 entegrasyonu

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| INT-301 | M | Kurum yöneticisi kuruluş akışı uçtan uca testi | TERM-003, CLS-004, STAFF-005, PEOPLE-010, PERM-003 |
| INT-302 | M | Hoca yalnızca atanmış sınıfları görür uçtan uca testi | STAFF-003, PERM-003, CLS-004, UI-004 |
| INT-303 | M | Öğrenci arşivleme ve geçmiş bağları entegrasyon testi | PEOPLE-006, PEOPLE-010, CLS-002 |

### Dalga 3 çıkış kapısı

Yönetici telefondan dönem, sınıf, hoca, yetki, öğrenci, anne ve baba bilgilerini yönetebilir.
Hoca yalnızca atanmış sınıfları görür. Bir öğrenci aynı anda ikinci aktif sınıfa atanamaz.

---

## 12. Dalga 4 — Yoklama dikey dilimi

Bu dalga projenin en kritik ilk gerçek kullanım dilimidir. Paralel görevler sözleşmelerden
sonra başlar.

### Sözleşme ve veri

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| ATT-001 | M | Yoklama durum ve kayıt sözleşmesini kesinleştir | P-010, CLS-001, PEOPLE-001 |
| ATT-002 | S | Yoklama çakışma kurallarını senaryolaştır | ATT-001, P-010 |
| ATT-003 | S | Yoklama gerçek zaman olay sözleşmesini yaz | ATT-001, A-006 |
| ATT-004 | M | Yoklama migration ve kısıtları | ATT-001, PEOPLE-002 |

### Backend

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| ATT-005 | M | Kuruma özel yoklama durumları backend'i | ATT-004 |
| ATT-006 | M | Günlük yoklama okuma API'si | ATT-004 |
| ATT-007 | M | Tek öğrenci yoklama yazma API'si | ATT-002, ATT-004 |
| ATT-008 | M | Toplu yoklama API'si ve kısmi hata modeli | ATT-007 |
| ATT-009 | M | Idempotency kaydı ve tekrar istek kontrolü | ATT-007, P-010 |
| ATT-010 | M | Yoklama sürüm/çakışma kontrolü | ATT-002, ATT-007 |
| ATT-011 | M | Yoklama denetim kayıtları | ATT-007, P-011 |
| ATT-012 | M | Sınıf gerçek zaman olay yayını | ATT-003, ATT-007 |

### Mobil

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| ATT-013 | M | Mobil günlük yoklama listesi | ATT-001, UI-003 |
| ATT-014 | M | Yoklama durum seçme etkileşimi | ATT-001, ATT-013 |
| ATT-015 | S | Toplu hepsi geldi etkileşimi | ATT-008, ATT-013 |
| SYNC-001 | M | Kalıcı mobil işlem kuyruğu temeli | P-010, A-005 |
| SYNC-002 | M | Yoklama optimistic update ve geri dönüş | ATT-002, SYNC-001 |
| SYNC-003 | M | Onay, hata ve yeniden deneme durumları | SYNC-001, ATT-007 |
| REALTIME-001 | M | Mobil sınıf kanalı aboneliği | ATT-003, A-006 |
| REALTIME-002 | M | Gelen yoklama olayını güvenli yenileme | REALTIME-001, ATT-006 |
| ATT-016 | S | Mobil eşitleme durumu göstergesi | SYNC-003, UI-003 |

### Test ve entegrasyon

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| ATT-017 | M | İki hoca aynı öğrenciyi değiştirir testi | ATT-010, REALTIME-002 |
| ATT-018 | M | Cevap kaybolur ve istek tekrar gider testi | ATT-009, SYNC-003 |
| ATT-019 | M | Uygulama kapanıp açılınca kuyruk korunur testi | SYNC-001, SYNC-003 |
| ATT-020 | M | Yetkisiz sınıf yoklama erişimi testi | ATT-006, ATT-007, PERM-003 |
| INT-401 | M | Yoklama dikey dilim pilot kabul testi | ATT-005–ATT-020, SYNC-001–SYNC-003, REALTIME-001–REALTIME-002 |

### Dalga 4 çıkış kapısı

- Aynı sınıfta iki hoca güvenle çalışır.
- Ağ ve cevap kaybında veri kaybolmaz.
- Tekrar istek çift kayıt oluşturmaz.
- Kullanıcı bekleyen ve başarısız işlemi görür.
- Yetkisiz sınıf erişimi reddedilir.

---

## 13. Dalga 5 — İçerik, program ve takvim

### Nesne deposu altyapısı

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| OPS-005 | M | Nesne deposu provisioning ve güvenlik kontrolleri | A-007R, A-010, A-011, A-013, A-014 |

### Kurum logosu

Logo V1 kapsamındadır ancak dosyasız Dalga 2 marka görevlerine gizlice eklenmez. Nesne deposu
kararı ve güvenlik provisioning'i tamamlandıktan sonra aşağıdaki Dalga 5 görevleri yürür; bütün
V1 modüllerini gerektiren QA ve gerçek kurum pilotu öncesinde tamamlanır.

| Kimlik | Boyut | Görev | Bağımlılık | Paralel hat |
|---|---:|---|---|---|
| ORG-010 | M | Güvenli kurum logosu yükleme/indirme backend akışını uygula | ORG-002, ORG-003, A-007R, OPS-005 | Backend |
| ORG-011 | M | Mobil logo seçme, yükleme ve görüntüleme akışını uygula | ORG-010, UI-001 | Mobil |

### İçerik

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| CONTENT-001 | S | Metin/PDF içerik API sözleşmesi | P-008, A-007 |
| CONTENT-002 | M | İçerik migration ve backend CRUD | CONTENT-001 |
| CONTENT-003 | M | Güvenli PDF yükleme/indirme | CONTENT-001, A-007, OPS-005 |
| CONTENT-004 | M | Mobil içerik oluşturma/düzenleme | CONTENT-001, UI-003 |
| CONTENT-005 | S | Mobil PDF seçme ve görüntüleme | CONTENT-003, CONTENT-004 |

### Program ve plan

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| PROGRAM-001 | M | Program/şablon/plan kavram sözleşmesi | P-008, TERM-001, CONTENT-001 |
| PROGRAM-002 | M | Program ve sürüm migration'ları | PROGRAM-001 |
| PROGRAM-003 | M | Program CRUD backend'i | PROGRAM-002 |
| PROGRAM-004 | M | Değerlendirme şeması ayar backend'i | PROGRAM-002 |
| PROGRAM-005 | M | Manuel günlük görev backend'i | PROGRAM-003 |
| PROGRAM-006 | M | Çok günlük şablon dağıtım servisi | PROGRAM-003, TERM-002 |
| PROGRAM-007 | M | Tatil/çalışma günü hesaplama testleri | PROGRAM-006, TERM-002 |
| PROGRAM-008 | M | Mobil program listesi ve aktif programlar | PROGRAM-001, UI-003 |
| PROGRAM-009 | M | Mobil program oluşturma sihirbazı | PROGRAM-001, CONTENT-004 |
| PROGRAM-010 | M | Mobil değerlendirme alanı seçimi | PROGRAM-004, PROGRAM-009 |
| PROGRAM-011 | M | Mobil günlük manuel görev ekleme | PROGRAM-005, PROGRAM-008 |
| PROGRAM-012 | M | Mobil çok günlük şablon oluşturma | PROGRAM-006, PROGRAM-008 |
| INT-501 | M | İki farklı kurum yapılandırması kabul testi | CONTENT-002–PROGRAM-012 |

### Dalga 5 çıkış kapısı

İki pilot kurum kod değişmeden farklı program yapıları kurabilir. Aynı sınıfta birden fazla
aktif program çalışır. Manuel görev ve çok günlük şablon aynı sistemde kullanılabilir.

---

## 14. Dalga 6 — İlerleme, denetim ve geri alma

### İlerleme

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| PROG-001 | M | İlerleme kayıt ve API sözleşmesi | PROGRAM-004, P-010 |
| PROG-002 | M | İlerleme migration ve repository | PROG-001 |
| PROG-003 | M | Tamamlandı/tamamlanmadı backend'i | PROG-002 |
| PROG-004 | M | İsteğe bağlı puan/not/tekrar backend'i | PROG-002, PROGRAM-004 |
| PROG-005 | M | Mobil sınıf ilerleme ekranı | PROG-001, UI-003 |
| PROG-006 | M | Mobil değerlendirme giriş bileşenleri | PROG-001, PROG-005 |
| PROG-007 | M | İlerleme sync ve idempotency | PROG-003, PROG-004, SYNC-001 |
| PROG-008 | M | İlerleme gerçek zaman yenileme | PROG-003, REALTIME-001 |

### Denetim ve geri alma

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| AUDIT-001 | M | Ortak audit event şemasını sınıf kapsamıyla tamamla | AUDIT-001A, CLS-002 |
| AUDIT-002 | M | Öğrenci ve yetki audit entegrasyonu | AUDIT-001, PEOPLE-004, PERM-002 |
| AUDIT-003 | M | Program ve ilerleme audit entegrasyonu | AUDIT-001, PROGRAM-003, PROG-003 |
| AUDIT-004 | M | Denetim listeleme/filtreleme API'si | AUDIT-001 |
| AUDIT-005 | M | Mobil işlem geçmişi ekranı | AUDIT-004, UI-003 |
| AUDIT-006 | M | Yoklama geri alma komutu | AUDIT-001, ATT-011 |
| AUDIT-007 | M | Öğrenci arşivleme geri alma komutu | AUDIT-001, PEOPLE-006 |
| AUDIT-008 | S | Geri alma yeni audit kaydı üretir testi | AUDIT-006, AUDIT-007 |
| INT-601 | M | İlerleme ve audit uçtan uca kabul testi | PROG-001–AUDIT-008 |

### Dalga 6 çıkış kapısı

Hocalar program ilerlemesini kaydedebilir; aynı sınıftaki diğer hocalar güncel durumu görür.
Kritik işlemler denetim geçmişine düşer ve tanımlı işlemler geçmiş silinmeden geri alınabilir.

---

## 15. Dalga 7 — Excel, kalite ve pilot yayın

### Excel

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| EXPORT-001 | S | Rapor filtre API sözleşmesini kesinleştir | P-012 |
| EXPORT-002 | M | Öğrenci/veli Excel veri sorgusu | EXPORT-001, PEOPLE-005 |
| EXPORT-003 | M | Yoklama Excel veri sorgusu | EXPORT-001, ATT-006 |
| EXPORT-004 | M | İlerleme Excel veri sorgusu | EXPORT-001, PROG-003 |
| EXPORT-005 | M | Excel dosya üretim servisi | EXPORT-002–EXPORT-004, A-008 |
| EXPORT-006 | S | Süreli güvenli indirme bağlantısı | EXPORT-005, A-007, OPS-005 |
| EXPORT-007 | M | Mobil rapor filtre ve indirme ekranı | EXPORT-001, UI-003 |
| EXPORT-008 | M | Excel içerik/doğruluk testleri | EXPORT-005, P-012 |

### Sertleştirme

| Kimlik | Boyut | Görev | Bağımlılık |
|---|---:|---|---|
| QA-001 | M | iOS gerçek cihaz ana akış testi | Tüm V1 modülleri |
| QA-002 | M | Android gerçek cihaz ana akış testi | Tüm V1 modülleri |
| QA-003 | M | Eşzamanlı 2–5 cihaz yük testi | ATT, PROGRESS, REALTIME |
| QA-004 | M | Kurum izolasyonu güvenlik testi | Tüm API modülleri |
| QA-005 | M | Yetki matrisi regresyon testi | P-003, tüm modüller |
| QA-006 | M | Erişilebilirlik ve sezgisel kullanım incelemesi | Tüm mobil ekranlar |
| QA-007 | M | Performans ölçümü ve kritik iyileştirmeler | Tüm V1 modülleri |
| OPS-001 | S | Veritabanı yedekleme ayarı | A-003 |
| OPS-002 | M | Yedekten geri yükleme tatbikatı | OPS-001 |
| OPS-003 | S | Üretim izleme ve alarm eşikleri | A-014 |
| OPS-004 | M | Pilot kurum kurulum kontrol listesi | Tüm V1 modülleri |
| OPS-006 | M | Nesne deposu yedek/geri yükleme tatbikatı | OPS-002, OPS-005, CONTENT-003, EXPORT-006 |
| PILOT-001 | M | Sentetik veriyle iç pilot | QA-001–QA-007, OPS-001–OPS-006, ORG-010–ORG-011 |
| PILOT-002 | M | İlk gerçek kurum pilotu | PILOT-001 |
| PILOT-003 | M | İkinci kurum kişiselleştirme pilotu | PILOT-002 |
| RELEASE-001 | M | Yayın hazırlığı ve son kabul | PILOT-003 |

### Nesne deposu operasyon görevlerinin kabul sınırı

- `OPS-005`; yalnız gerçek dosya ihtiyacı ve A-007R sağlayıcı kararı sonrasında başlar. S3
  seçilmişse ADR-007'de bağlanan gerekli ortam/backup AWS hesaplarında private bucket'ları,
  ayrı SSE-KMS key policy'lerini, Block Public Access, versioning,
  Object Lock/retention, lifecycle, cross-account yedek hedefini ve §5.4 rollerini IaC ile
  kurar. Source `storage_disposal` ile backup hesabındaki ayrı `storage_backup_disposal` trust
  policy/session sınırını ve object-data yetkisiz ayrı `storage_version_verifier` trust/broker
  yüzeyini oluşturur; runtime, provisioner ve replication/restore rolüne object version silme
  vermez. Verifier allow testi yalnız durable tek target'ın exact full key'i, `s3:prefix`
  `StringEquals` ve sayfa başına `s3:max-keys <= 100` ile `ListBucketVersions` çalıştırır.
  Runtime/disposal dâhil diğer rollerde geniş/dar listeleme; verifier'da `ListBucket`, başka
  bucket/tenant/key, kısa/wildcard/koşulsuz prefix, prefixesiz veya `max-keys > 100`, eksik
  durable kayıt, object read/write/delete; ayrıca manifestsiz/prefix silme, hold değiştirme,
  governance bypass, roller arası assume ve genel delete negatiflerini otomatik doğrular.
  Production+backup ile
  development/staging KMS/rotation basamaklarını güncel fiyatlarla yeniden hesaplar. Hukukî
  retention süresini seçmez ve application durable manifest/durum akışını geliştirmez. R2
  seçilmişse bu S3 kontrolleri kopyalanmış sayılmaz; A-007R'de onaylanan EU jurisdiction,
  immutable-key, backup, restore ve imha karşılıklarını uygular.
- `CONTENT-003`; ADR-007 §5.4'teki durable source→replica eşlemesini, iki aşamalı onay,
  target-bazlı idempotency, ayrı source/backup alt durumları, retention bekleme, exact-version
  pre-delete varlık kaydı, ağ çağrısından önce durable attempt, exact-key tam sayfalanmış version
  postcondition'ı ile upload/finalize/orphan/purge/disposal kayıp-yanıt uzlaştırmasını ve
  bütüncül tamamlanma kapısını uygulama katmanında gerçekleştirir. `HeadObject` `403/404`
  hiçbir durumda yokluk sayılmaz; wrong version, yetki kayması, başka tenant/key ve eksik
  pagination fail-closed kalır.
- `OPS-006`; OPS-005 yedeğinden izole hedefe gerçek nesne geri yükler; DB
  `file_assets`/`export_jobs` metaverisi, version ID, checksum, KMS erişimi, tenant izolasyonu
  ve tokenlı indirmeyi doğrular. Ayrıca sentetik version'da source silmesini, retention nedeniyle
  bekleyen backup imhasını ve retention kalktıktan sonra exact replica silmesini ayrı ayrı
  aynı verifier mekanizmasıyla doğrular. Kayıp delete yanıtında target mevcut, gerçekten silinmiş,
  yanlış version ID, verifier `403` yetki kayması ve başka kurum/key senaryolarını ayrı tatbik
  eder; bütün item'lar iki tarafta yok olmadan tamamlanma kabul etmez. Ölçülen RPO/RTO ile
  restore/imha kanıtını kaydeder. Veritabanı restore'unu OPS-002'den alır ve yalnız
  “yedek/bucket var” ya da yalnız source silindi kontrolünü kabul etmez.
- `PILOT-001`, OPS-005 ve OPS-006 tamamlanmadan başlayamaz. Bu nedenle `RELEASE-001` de pilot
  zinciri üzerinden aynı provisioning/güvenlik ve nesne geri yükleme kalite kapılarına bağlıdır.

### Dalga 7 çıkış kapısı

- Kritik/yüksek açık hata yoktur.
- İki kurum farklı yapılandırmalarla başarıyla kullanmıştır.
- Sessiz veri kaybı yaşanmamıştır.
- Veritabanı ile nesne yedeklerinden izole geri dönüş denenmiş; checksum, metaveri ve kurum
  izolasyonu eşleşmiştir.
- Runtime/lifecycle/backup rol sınırları, exact-key verifier allow/deny matrisi ve geniş
  listeleme/silme negatifleri geçmiştir.
- Sentetik onaylı imhada source ve backup version yokluğu ayrı kanıtlanmış; retention bekleme
  durumu yanlış başarı üretmemiştir.
- Excel raporları kaynak verilerle uyuşur.
- iOS ve Android mağaza/yayın gereksinimleri karşılanır.

---

## 16. Dalga 8 — İlk sürüm sonrası modüller

Bu dalganın görevleri ilk mobil yayın tamamlanmadan `READY` yapılmaz.

### Web paneli

- WEB-001: Web paneli teknoloji ADR'si
- WEB-002: Web kimlik ve rol kabuğu
- WEB-003: Kurum yönetimi
- WEB-004: Toplu kullanıcı ve öğrenci yönetimi
- WEB-005: Program tasarım ekranları
- WEB-006: Rapor ve denetim ekranları
- WEB-007: Excel içe aktarma

### Veli ve öğrenci

- PORTAL-001: Veli/öğrenci yetki matrisi
- PORTAL-002: Mevcut anne/baba kişisini hesaba bağlama
- PORTAL-003: Veli–öğrenci ilişki doğrulaması
- PORTAL-004: Salt okunur yoklama özeti
- PORTAL-005: Salt okunur program ve ilerleme
- PORTAL-006: Öğrenci kendi görünümü

### Bildirimler

- NOTIFY-001: Bildirim tercihleri veri modeli
- NOTIFY-002: Push sağlayıcısı ADR'si
- NOTIFY-003: Cihaz token yönetimi
- NOTIFY-004: Yoklama hatırlatması
- NOTIFY-005: Yeni program/görev bildirimi
- NOTIFY-006: Yönetici duyurusu
- NOTIFY-007: Veli bildirimleri

---

## 17. İlk 15 çalışma günü için önerilen sıra

Bu sıra tek kişi ve gerektiğinde agent desteğiyle ilerlemek için hazırlanmıştır. Bir gün içinde
bir görev tamamlanamazsa görev küçültülür; sonraki güne yarım ve belirsiz iş taşınmaz.

| Gün | Ana görev | Beklenen sonuç |
|---:|---|---|
| 1 | P-001 Terimler sözlüğü | Her agentın aynı kavramları kullanması |
| 2 | P-002 Aktörler ve kullanım senaryoları | Yönetici/hoca işlerinin sınırı |
| 3 | P-003 Yetki matrisi, bölüm 1 | Platform ve kurum rolleri |
| 4 | P-003 Yetki matrisi, bölüm 2 | Sınıf ve işlem yetkileri |
| 5 | P-004 Veri envanteri | Hassas veri ve erişim seviyesi |
| 6 | P-005 Yönetici bilgi mimarisi | Yönetici ekran haritası |
| 7 | P-006 Hoca bilgi mimarisi | Hoca ekran haritası |
| 8 | P-007 Ekran envanteri | İlk sürüm ekranlarının kesin listesi |
| 9 | P-008 Veri modeli, kurum/kimlik | İlk çekirdek varlıklar |
| 10 | P-008 Veri modeli, sınıf/kişi | Sınıf, öğrenci, anne ve baba |
| 11 | P-008 Veri modeli, takipler | Yoklama, program ve ilerleme |
| 12 | P-009 API kuralları | Bütün modüllerin ortak API standardı |
| 13 | P-010 Sync sözleşmesi | Kuyruk, idempotency ve çakışma |
| 14 | P-011/P-012 | Audit, geri alma ve Excel sözleşmesi |
| 15 | P-013/P-014 | Test planı ve Faz 0 bütünlük incelemesi |

Bu 15 gün kod yazmadan geçirilmiş sayılmaz; sonraki bütün agent çalışmalarının tekrarını ve
çatışmasını azaltan üretim çalışmasıdır.

---

## 18. Birden fazla agent kullanma örneği

### Yanlış dağıtım

- Agent 1: Backend'i yap.
- Agent 2: Mobil uygulamayı yap.
- Agent 3: Veritabanını yap.

Bu dağıtım üç farklı veri modeli ve API üretme riski taşır.

### Doğru dağıtım örneği

Önce tek görev:

- Agent S: `ATT-001` yoklama sözleşmesini hazırlar.
- İnsan/ana agent sözleşmeyi onaylar.

Sonra paralel:

- Agent B: `ATT-006` ve ardından `ATT-007` backend görevlerini yapar.
- Agent M: `ATT-013` mobil listeyi mock API ile yapar.
- Agent T: `ATT-017`–`ATT-020` test senaryolarını hazırlar.

Son olarak:

- Entegrasyon sahibi `INT-401` görevinde gerçek backend, mobil, sync ve testleri birleştirir.

---

## 19. Agent teslim raporu biçimi

Her agent görevi bitirdiğinde şu özeti vermelidir:

```text
Görev:
Durum: DONE / BLOCKED
Değiştirilen dosyalar:
Üretilen davranış:
Kabul ölçütleri sonucu:
Çalıştırılan testler:
Bilinen sınırlamalar:
Sonraki hazır görevler:
Kapsam dışı bırakılanlar:
```

“Tamamlandı” tek başına geçerli teslim raporu değildir.

---

## 20. Entegrasyon disiplini

- Her modül değişikliği küçük ve geri alınabilir olmalıdır.
- Ortak sözleşme değişirse bağımlı mock, test ve dokümanlar aynı görevde güncellenmelidir.
- Migration dosyaları değiştirildikten sonra geriye dönük yeniden yazılmamalı; yeni migration
  eklenmelidir.
- Entegrasyon görevi yalnızca uyum ve kabul sorunlarını çözer; yeni ürün özelliği eklemez.
- Dalga sonunda ana kullanıcı akışı staging ortamında uçtan uca gösterilmelidir.
- Sonraki dalga başlamadan açık entegrasyon borcu kapatılmalıdır.

---

## 21. Günlük çalışma rutini

Her çalışma gününde:

1. En erken `READY` durumundaki ve bağımlılığı olmayan görev seçilir.
2. Görev kartı okunur; kapsam dışı maddeler teyit edilir.
3. Agenta yalnızca bu görev ve gerekli belgeler verilir.
4. Agent planını ve dokunacağı dosyaları önceden bildirir.
5. Görev uygulanır ve test edilir.
6. Teslim raporu alınır.
7. Ana agent/insan kabul ölçütlerini doğrular.
8. Görev `DONE` yapılır.
9. Bağımlılığı çözülen bir sonraki görev `READY` yapılır.
10. Gerekirse karar günlüğü ve ana plan güncellenir.

Bir günlük hedef “çok kod yazmak” değil, bir görevi eksiksiz kapatmaktır.

---

## 22. Tarihsel ilk görev tanımı

Dalga 0 başlatılırken ilk görev `P-001 — Terimler sözlüğü` idi. Bu bölüm tarihsel görev
tanımını korur; güncel sıradaki iş burada belirlenmez. Güncel durum için yalnız
`GOREV_DURUMU.md` esas alınır.

### Amaç

Kurum, kullanıcı, kişi, hoca, öğrenci, anne, baba, sınıf, dönem, program, program şablonu,
plan, günlük görev, içerik, değerlendirme, ilerleme, yoklama oturumu, yoklama kaydı, denetim
kaydı ve geri alma kavramlarını tek anlamla tanımlamak.

### Çıktı

`TERIMLER_SOZLUGU.md`

### Kabul ölçütleri

- Her terimin tek ve açık tanımı vardır.
- Birbirine benzeyen terimlerin farkı açıklanmıştır.
- Veri modeli ve arayüzde kullanılacak tercih edilen ad belirtilmiştir.
- Eski sistemdeki terimlerle yeni platformdaki karşılığı gösterilmiştir.
- Ana ürün planıyla çelişki yoktur.

`P-001` tamamlanmadan `P-003`, `P-008` veya herhangi bir uygulama geliştirme görevi
başlatılmaz.
