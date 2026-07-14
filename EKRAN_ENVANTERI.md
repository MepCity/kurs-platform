# İlk Sürüm Ekran Envanteri

| Alan | Değer |
|---|---|
| Görev | P-007 — İlk sürüm ekran envanterini çıkar |
| Belge sürümü | 1.2 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Terim kaynağı | `TERIMLER_SOZLUGU.md` |
| Bilgi mimarisi kaynakları | `YONETICI_BILGI_MIMARISI.md` (P-005), `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) |
| Yetki kaynağı | `YETKI_MATRISI.md` |
| Aktör/senaryo kaynağı | `AKTORLER_VE_KULLANIM_SENARYOLARI.md` |
| Veri modeli kaynağı | `VERI_MODELI.md` |
| Son güncelleme | 14 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, `YONETICI_BILGI_MIMARISI.md` (P-005) ve `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006)
bilgi mimarilerini birleştirerek ilk sürüm mobil uygulamadaki **bütün ekranların kesin
envanterini** çıkarır. Her ekrana benzersiz bir kimlik verilir, rol bazlı görünürlüğü
belirlenir, dört standart durum (yükleniyor, boş, hata, yetkisiz) ile onay durumları not
edilir ve navigasyon gruplaması önerilir.

Bu belge:

- İlk sürüm kapsamındaki her ekranı tek bir yerde toplar; P-005 ve P-006'da tanımlanan
  ekranları normalize eder ve çakışan ortak ekranları tekil hâle getirir.
- Her ekranın hangi role görünür olduğunu `YETKI_MATRISI.md` ile satır satır bağlar.
- Navigasyon gruplaması önerisi sunar; ancak kesin sekme/menü bileşen tasarımını
  (`UI-002` kapsamı) bağlayıcı olarak tanımlamaz.
- Ekranların alan listesini, wireframe'ini veya görsel tasarımını üretmez.
- API, veri modeli veya veritabanı şeması tanımlamaz (`P-008`, `P-009` kapsamı).

### 1.1. Ekran kimliği şeması

Her ekran şu biçimde benzersiz bir kimlik alır:

```text
<BÖLÜM>-<NN>
```

Bölüm kodları:

| Kod | Bölüm |
|---|---|
| AUTH | Giriş ve oturum |
| PLAT | Platform yöneticisi özel ekranları |
| HOME | Ana sayfa / giriş ekranları |
| CLS | Sınıf işlemleri |
| ATT | Yoklama |
| STD | Öğrenci |
| PRG | Program ve ezber takibi |
| PRS | İlerleme |
| MGT | Yönetim (sınıf yönetimi, dönem/takvim, kurum ayarları, personel) |
| RPT | Raporlar ve denetim |
| PRF | Profil, oturum ve eşitleme |
| CTX | Bağlam seçimi/değiştirme |

### 1.2. Durum kısaltmaları

Her ekran için uygulanabilir durumlar aşağıdaki kısaltmalarla gösterilir:

| Kısaltma | Açıklama |
|---|---|
| Y | Yükleniyor — veri alınırken gösterilecek durum |
| B | Boş — veri olmadığında gösterilecek durum |
| H | Hata — ağ/sunucu hatası durumu |
| Z | Yetkisiz — kullanıcının erişim hakkı olmadığında gösterilecek durum |
| O | Onay — tehlikeli işlem öncesi onay adımı |
| E | Eşitleme — bekleyen/başarılı/başarısız göstergesi |

`URUN_VE_UYGULAMA_PLANI.md` §18.3 ve `AGENTS.md` görev tamamlanma koşulları gereği, veri
gösteren her ekran Y, B, H ve Z durumlarını ele almalıdır. Ek olarak tehlikeli işlemler O,
eşitleme gerektiren ekranlar E durumunu gerektirir.

**Bağlayıcı yetkisiz erişim kuralı:** Kimlik doğrulama ekranları dışındaki bütün korumalı
ekranlarda `Z` durumu uygulanır. Yetkisiz düğüm normal navigasyonda gizlenir; ancak yalnızca
gizlemek güvenlik önlemi değildir. Doğrudan bağlantı, eski istemci veya elle oluşturulmuş API
isteğiyle erişim denendiğinde mobil rota koruması erişimi durdurur, sunucu da aynı isteği
`403 FORBIDDEN` ile reddeder. Aşağıdaki tablolarda korumalı ekranların `Z` durumu açıkça
gösterilir.

---

## 2. Ekran listesi — Giriş ve oturum

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| AUTH-01 | Giriş | Kullanıcı adı ve parola ile giriş ekranı | Y, H | ORTAK-01, HOCA-01 |
| AUTH-02 | Parola Değiştir | İlk giriş veya zorunlu parola değişim ekranı | Y, H | ORTAK-01, HOCA-01 |
| AUTH-03 | Oturum Süresi Doldu | Oturum süresi dolduğunda veya iptal edildiğinde gösterilen bilgilendirme | — | ORTAK-01, HOCA-01 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| AUTH-01 | Evet | Evet | Evet |
| AUTH-02 | Evet | Evet | Evet |
| AUTH-03 | Evet | Evet | Evet |

---

## 3. Ekran listesi — Bağlam seçimi

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| CTX-01 | Bağlam Seçimi / Değiştir | Birden fazla rol/kurum ataması olan kullanıcının aktif bağlamını seçmesi veya değiştirmesi. Bu ekran adaydır; gösterilme koşulu ve kesin davranışı `UI-002` sözleşmesinin açık kararıdır. | Y, B, H, Z | — |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| CTX-01 | Koşullu (birden fazla bağlam varsa) | Koşullu (birden fazla bağlam varsa) | Koşullu (birden fazla bağlam varsa) |

**Not:** `YONETICI_BILGI_MIMARISI.md` bölüm 8'de bu ekran açık nokta olarak kaydedilmiştir.
Kesin koşul (yalnızca girişte mi, sürekli erişilebilir mi) `UI-002`'ye bırakılmıştır. Bu belge
ekranın varlığını kaydeder.

---

## 4. Ekran listesi — Platform yöneticisi özel ekranları

Bu ekranlar yalnızca platform yöneticisi rolüne özeldir; kurum yöneticisi ve hoca bu
ekranları hiçbir koşulda göremez.

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| PLAT-01 | Kurum Listesi | Platform genelindeki kurumların listesi; arama ve durum filtresi (aktif/askıda/arşiv). Platform yöneticisinin varsayılan giriş ekranı. | Y, B, H, Z | PLAT-01, PLAT-03 |
| PLAT-02 | Kurum Oluştur | Yeni kurum kaydı açma formu | Y, H, Z, E | PLAT-01 |
| PLAT-03 | Kurum Detayı | Kurum bilgisi görüntüleme; kurum yöneticisi atama ve kurum bağlamına geçiş giriş noktaları | Y, B, H, Z | PLAT-01, PLAT-02, PLAT-03, PLAT-04 |
| PLAT-04 | Kurum Durumu Değiştir | Kurumu aktif/askıda/arşiv durumuna alma (onay adımlı) | Y, H, Z, O | PLAT-03 |
| PLAT-05 | Kurum Yöneticisi Ata | Kuruma kurum yöneticisi atama ekranı | Y, H, Z, E | PLAT-02 |
| PLAT-06 | Destek Modu Girişi | Kurum bağlamına destek amaçlı geçiş; denetim kaydı otomatik üretilir ve ekranda sürekli "destek modu" göstergesi bulunur | Y, H, Z, O | PLAT-04 |
| PLAT-07 | Sistem Geneli Denetim Kaydı | Platform genelindeki denetim kayıtları; kurum bazında filtrelenebilir | Y, B, H, Z | PLAT-05 |
| PLAT-08 | Kurumlar Arası Rapor | Kurumların verileri birbirine karıştırılmadan sunulan özet rapor | Y, B, H, Z | PLAT-06 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| PLAT-01 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |
| PLAT-02 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |
| PLAT-03 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |
| PLAT-04 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |
| PLAT-05 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |
| PLAT-06 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |
| PLAT-07 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |
| PLAT-08 | Evet | Hayır — mutlak sınır | Hayır — mutlak sınır |

---

## 5. Ekran listesi — Ana sayfa

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| HOME-01 | Kurum Ana Sayfası | Kurum yöneticisi ve hocanın kuruma giriş ekranı; kurum adı ve logosu üstte gösterilir (§9.1). Kurum yöneticisi için sekiz işlevsel bölüme giriş noktası sağlar. Hoca için sınıf seçici ve yönetim alanlarına erişim sunar. | Y, B, H, Z | — |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| HOME-01 | Evet (destek modunda) | Evet | Evet |

---

## 6. Ekran listesi — Sınıf işlemleri

Bu ekranlar seçili sınıf bağlamında çalışır; bir sınıf seçilmeden operasyonel veriye erişilmez.

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| CLS-01 | Sınıf Seçici | Kullanıcının yetkili olduğu sınıfların listesi | Y, B, H, Z | HOCA-02 |
| CLS-02 | Sınıf Ana Ekranı | Seçilen sınıf için yoklama, öğrenciler, program ve ilerleme bölümlerine giriş noktası | Y, B, H, Z | HOCA-02 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| CLS-01 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıflar) |
| CLS-02 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıf) |

**Not — Tek sınıflı hoca optimizasyonu:** Hoca yalnızca bir sınıfa atanmışsa CLS-01 atlanarak
doğrudan CLS-02 gösterilebilir; ancak aktif sınıf bağlamı arayüzde görünür kalmalı ve
kullanıcı sınıf değiştirme ekranına her zaman ulaşabilmelidir. Bu kesin davranış `UI-002`'de
netleşecektir.

---

## 7. Ekran listesi — Yoklama

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| ATT-01 | Bugünkü Yoklama | Seçili sınıftaki öğrenci yoklama listesi; Geldi/Gelmedi ve kuruma özel ek durumlar | Y, B, H, Z, E | HOCA-03, HOCA-04 |
| ATT-02 | Geçmiş Tarihli Yoklama Düzeltme | Geçmiş tarihli yoklama kaydını düzeltme ekranı; eski ve yeni değer korunur | Y, H, Z, O, E | HOCA-05 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| ATT-01 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıf, varsayılan açık) |
| ATT-02 | Evet (destek modunda) | Evet | Varsayılan kapalı → geçmiş tarihli yoklama düzeltme izniyle açılır |

**Not — Toplu işlem:** ATT-01 ekranı içinde "Hepsi Geldi" toplu işlem aksiyonu bulunur
(HOCA-04); bu ayrı bir ekran değil, ATT-01'in içindeki bir eylemdir.

---

## 8. Ekran listesi — Öğrenciler

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| STD-01 | Öğrenci Listesi | Seçili sınıftaki öğrencilerin listesi; arama, filtre ve sıralama | Y, B, H, Z | HOCA-07, KURUM-06 |
| STD-02 | Öğrenci Detayı | Öğrencinin temel bilgileri (ad, soyad, telefon vb.) | Y, B, H, Z | HOCA-07, KURUM-06 |
| STD-03 | Öğrenci Oluştur | Yeni öğrenci kaydı açma formu | Y, H, Z, E | KURUM-06 |
| STD-04 | Öğrenci Düzenle | Mevcut öğrenci bilgilerini düzenleme formu | Y, H, Z, E | KURUM-06 |
| STD-05 | Öğrenci Arşivle | Öğrenciyi arşivleme onay ekranı; geçmiş kayıtlar korunur | Y, H, Z, O, E | KURUM-06 |
| STD-06 | Arşivlenmiş Öğrenciler | Arşivlenmiş öğrenci listesi | Y, B, H, Z | KURUM-11 |
| STD-07 | Öğrenciyi Geri Yükle | Arşivlenmiş öğrenciyi geri yükleme onay ekranı | Y, H, Z, O, E | KURUM-11 |
| STD-08 | Anne/Baba Bilgisi Oluştur/Düzenle | Öğrencinin anne ve/veya baba kaydını oluşturma veya düzenleme formu | Y, H, Z, E | KURUM-06 |
| STD-09 | Veli İletişim Bilgisi Görüntüle | Anne/baba kimlik ve iletişim bilgisini salt okunur görüntüleme | Y, B, H, Z | HOCA-07 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| STD-01 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıf, varsayılan açık — temel bilgi) |
| STD-02 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıf, varsayılan açık — temel bilgi) |
| STD-03 | Evet (destek modunda) | Evet | Varsayılan kapalı → öğrenci yönetimi izniyle açılır |
| STD-04 | Evet (destek modunda) | Evet | Varsayılan kapalı → öğrenci yönetimi izniyle açılır |
| STD-05 | Evet (destek modunda) | Evet | Varsayılan kapalı → öğrenci yönetimi izniyle açılır |
| STD-06 | Evet (destek modunda) | Evet | Varsayılan kapalı → `RESTORE_ARCHIVED` izniyle açılır |
| STD-07 | Evet (destek modunda) | Evet | Varsayılan kapalı → `RESTORE_ARCHIVED` izniyle açılır |
| STD-08 | Evet (destek modunda) | Evet | Varsayılan kapalı → anne/baba bilgisi yönetme izniyle açılır |
| STD-09 | Evet (destek modunda) | Evet | Varsayılan kapalı → veli iletişim bilgisi görüntüleme izniyle açılır |

**Not — Anne/baba bölümü varsayılan-deny:** Öğrenci detayında (STD-02) anne/baba bölümü
yalnızca STD-08 veya STD-09 izinlerinden en az biri varsa gösterilir. Öğrenci görüntüleme
izni anne/baba verisini otomatik kapsamaz; ikisi de yoksa bölüm hiç gösterilmez.

**Not — `RESTORE_ARCHIVED` ortak izin:** STD-06/STD-07 ve MGT-04/MGT-05 (arşivlenmiş sınıf)
ekranları tek, ortak `RESTORE_ARCHIVED` izniyle açılır; varlık başına ayrı izin yoktur
(`VERI_MODELI.md` §4.8).

---

## 9. Ekran listesi — Program ve ezber takibi

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| PRG-01 | Aktif Programlar Listesi | Seçili sınıftaki aktif programların listesi; aynı sınıfta birden fazla program görünür | Y, B, H, Z | HOCA-06, KURUM-07 |
| PRG-02 | Program Oluştur | Yeni program oluşturma; hazır şablon veya serbest tanım seçimi, manuel günlük görev ya da çok günlük şablon dağıtımı yöntemi | Y, H, Z, E | KURUM-07 |
| PRG-03 | Program Düzenle | Mevcut program yapısını düzenleme | Y, H, Z, E | KURUM-07 |
| PRG-04 | Değerlendirme Şeması Ayarı | Programa puan, not ve tekrar gerekli alanlarının etkinleştirilmesi | Y, H, Z, E | KURUM-08 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| PRG-01 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıf, varsayılan açık — görüntüleme) |
| PRG-02 | Evet (destek modunda) | Evet | Varsayılan kapalı → program oluşturma/yönetme izniyle açılır |
| PRG-03 | Evet (destek modunda) | Evet | Varsayılan kapalı → program oluşturma/yönetme izniyle açılır |
| PRG-04 | Evet (destek modunda) | Evet | Varsayılan kapalı → değerlendirme şeması ayarlama izniyle açılır (program yönetiminden bağımsız) |

**Not — Program Oluştur dallanması:** PRG-02 tek bir giriş noktasıdır; akış içinde ana planın
§8.7'deki iki üretim yöntemine (elle günlük görev ekleme / şablon hazırlayıp takvime dağıtma)
göre dallanır. Bu, ekran bazında değil akış bazında bir ayrımdır.

---

## 10. Ekran listesi — İlerleme

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| PRS-01 | Sınıf İlerleme Ekranı | Seçili sınıf için program/plan bazlı ilerleme girişi: tamamlandı/tamamlanmadı, isteğe bağlı puan, not ve tekrar gerekli | Y, B, H, Z, E | HOCA-06 |
| PRS-02 | Diğer Hoca Notu Görüntüleme | Aynı sınıftaki diğer hocanın öğrenciye yazdığı normal notu görüntüleme | Y, B, H, Z | HOCA-08 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| PRS-01 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıf, varsayılan açık) |
| PRS-02 | Evet (destek modunda) | Evet | Evet (yalnızca atanmış sınıf, varsayılan açık) |

---

## 11. Ekran listesi — Yönetim

### 11.1. Sınıf yönetimi

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| MGT-01 | Sınıf Listesi (yönetim) | Kurumdaki sınıfların yönetim amaçlı listesi; oluşturma/düzenleme/arşivleme giriş noktası | Y, B, H, Z | KURUM-03 |
| MGT-02 | Sınıf Oluştur/Düzenle | Yeni sınıf oluşturma veya mevcut sınıfı düzenleme formu; sınıf bir eğitim dönemine bağlıdır | Y, H, Z, E | KURUM-03 |
| MGT-03 | Sınıf Arşivle | Sınıfı arşivleme onay ekranı; geçmiş kayıtlar korunur | Y, H, Z, O, E | KURUM-03 |
| MGT-04 | Arşivlenmiş Sınıflar | Arşivlenmiş sınıfların listesi | Y, B, H, Z | KURUM-11 |
| MGT-05 | Sınıfı Geri Yükle | Arşivlenmiş sınıfı geri yükleme onay ekranı | Y, H, Z, O, E | KURUM-11 |
| MGT-06 | Sınıf Detayı (yönetim) | Sınıf ve hoca atama metaverisi; öğrenci özeti yalnızca kullanıcının ilgili sınıfın operasyonel verisine erişimi varsa gösterilir | Y, B, H, Z | KURUM-03 |

### 11.2. Eğitim dönemi ve takvim

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| MGT-07 | Dönem Listesi | Eğitim dönemlerinin listesi (aktif/geçmiş/arşiv) | Y, B, H, Z | KURUM-02 |
| MGT-08 | Dönem Oluştur/Düzenle | Yeni dönem oluşturma veya mevcut dönemi düzenleme; çalışma günleri, tatiller ve başlangıç/bitiş tarihi | Y, H, Z, E | KURUM-02 |

### 11.3. Kurum ayarları

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| MGT-09 | Marka Ayarları | Kurum adı, logo, ana renk ve yardımcı renklerin düzenlenmesi; okunmaz renk kombinasyonu uyarısı/kısıtlaması | Y, H, Z, E | KURUM-01 |
| MGT-10 | Etkin Modüller | Kurumda kullanılmayan modüllerin kapatılması veya açılması | Y, B, H, Z, E | — |
| MGT-11 | Kuruma Özel Yoklama Durumları | Geç geldi, İzinli, Hasta vb. ek yoklama durumlarının tanımlanması | Y, B, H, Z, E | KURUM-07 |
| MGT-12 | Özel Öğrenci Alanları | Zorunlu ad/soyad/telefon dışında kuruma özel ek alan tanımlama (kısa metin, uzun metin, sayı, tarih, evet/hayır, tek seçim, çoklu seçim) | Y, B, H, Z, E | KURUM-06 |

### 11.4. Personel (Hoca) yönetimi

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| MGT-13 | Hoca Listesi | Kurumdaki hocaların listesi; dört bağımsız personel izninden en az biriyle görünür olur (OR mantığı); sahip olunan izne göre sınırlı metaveri ve eylem gösterilir | Y, B, H, Z | KURUM-04 |
| MGT-14 | Hoca Oluştur | Yeni hoca hesabı açma ve geçici giriş bilgisi verme formu | Y, H, Z, E | KURUM-04 |
| MGT-15 | Hoca Hesabını Askıya Al | Hoca hesabını askıya alma onay ekranı | Y, H, Z, O, E | KURUM-04 |
| MGT-16 | Hoca Detayı | Dört bağımsız personel izninin ortak detay konteyneri; yalnızca kullanıcının sahip olduğu izne ait bilgi bölümü ve eylem gösterilir | Y, B, H, Z | KURUM-04, KURUM-05 |
| MGT-17 | Hoca Sınıf Ataması | Hocayı sınıflara atama veya atama kaldırma ekranı | Y, B, H, Z, E | KURUM-05 |
| MGT-18 | Hoca İzinleri Görüntüle | Hocanın mevcut izinlerini salt okunur listeleme; izin değiştirme aksiyonu bu ekrandan erişilmez | Y, B, H, Z | KURUM-05 |
| MGT-19 | Hoca İzinleri Değiştir | Hocanın izinlerini verme, geri alma veya değiştirme ekranı | Y, B, H, Z, E | KURUM-05 |
| MGT-20 | Hoca Cihaz Oturumları | Hocanın cihaz oturumlarını listeleme ve iptal etme ekranı | Y, B, H, Z, O, E | KURUM-12 |

### Rol bazlı görünürlük — Yönetim ekranları

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| MGT-01 | Evet (destek modunda) | Evet | Varsayılan kapalı → sınıf yönetimi izniyle açılır |
| MGT-02 | Evet (destek modunda) | Evet | Varsayılan kapalı → sınıf yönetimi izniyle açılır |
| MGT-03 | Evet (destek modunda) | Evet | Varsayılan kapalı → sınıf yönetimi izniyle açılır |
| MGT-04 | Evet (destek modunda) | Evet | Varsayılan kapalı → `RESTORE_ARCHIVED` izniyle açılır |
| MGT-05 | Evet (destek modunda) | Evet | Varsayılan kapalı → `RESTORE_ARCHIVED` izniyle açılır |
| MGT-06 | Evet (destek modunda) | Evet | Varsayılan kapalı → sınıf yönetimi izniyle açılır |
| MGT-07 | Evet (destek modunda) | Evet | Varsayılan kapalı → dönem/takvim izniyle açılır |
| MGT-08 | Evet (destek modunda) | Evet | Varsayılan kapalı → dönem/takvim izniyle açılır |
| MGT-09 | Evet (destek modunda) | Evet | Varsayılan kapalı → marka ayarı izniyle açılır |
| MGT-10 | Evet (destek modunda) | Evet | Varsayılan kapalı → modül yönetimi izniyle açılır |
| MGT-11 | Evet (destek modunda) | Evet | Varsayılan kapalı → yoklama durumu tanımlama izniyle açılır |
| MGT-12 | Evet (destek modunda) | Evet | Hayır — `YETKI_MATRISI.md` §2.2 madde 6b gereği V1'de hocaya devredilemez |
| MGT-13 | Evet (destek modunda) | Evet | Varsayılan kapalı → dört personel izninden EN AZ BİRİYLE açılır (bkz. bölüm 15) |
| MGT-14 | Evet (destek modunda) | Evet | Varsayılan kapalı → hoca hesabı oluşturma/kapatma izniyle açılır |
| MGT-15 | Evet (destek modunda) | Evet | Varsayılan kapalı → hoca hesabı oluşturma/kapatma izniyle açılır |
| MGT-16 | Evet (destek modunda) | Evet | Varsayılan kapalı → dört personel izninden EN AZ BİRİYLE açılır |
| MGT-17 | Evet (destek modunda) | Evet | Varsayılan kapalı → hoca–sınıf ataması izniyle açılır |
| MGT-18 | Evet (destek modunda) | Evet | Varsayılan kapalı → izin görüntüleme izniyle açılır |
| MGT-19 | Evet (destek modunda) | Evet | Hayır — mutlak sınır (hiçbir koşulda hocaya gösterilmez) |
| MGT-20 | Evet (destek modunda) | Evet | Varsayılan kapalı → cihaz oturumu iptali izniyle açılır |

**Not — Dönem gereksinimi:** MGT-02 (sınıf oluşturma), en az bir aktif dönemin var olmasını
gerektirir. Dönem tanımlı değilse önce MGT-08 (dönem oluşturma) ekranına yönlendirilir
(`YONETICI_BILGI_MIMARISI.md` §4.3).

**Not — MGT-06 operasyonel veri sınırı:** `CLASS_MANAGE` kurum kapsamlı bir yönetim iznidir;
hocaya atanmadığı sınıfların öğrenci/veli/yoklama/ilerleme/değerlendirme verisini açmaz.
Bu izinle MGT-06 içinde yalnızca işlemin gerektirdiği sınıf ve hoca atama metaverisi gösterilir.
Öğrenci özeti ancak hoca ilgili sınıfa atanmışsa ve öğrenci verisine normal erişim kapsamı
varsa görünür; aksi durumda öğrenci bölümü hiç üretilmez (`YETKI_MATRISI.md` §2.2 madde 3 ve
§4.4).

**Not — MGT-12 hoca kapsamı:** `YETKI_MATRISI.md` §2.2 madde 6b kararı gereği özel öğrenci
alanı tanımı yönetimi V1'de hocaya devredilemez. İleride açılması yeni bir ürün kararı ve
`permission_catalog` girdisi gerektirir; mevcut marka/modül/yoklama durumu izinlerinden hiçbiri
bu ekranı açmaz.

**Not — MGT-16 bağımsız alt bölümler:** Ekran dört personel izninden en az biriyle ortak
konteyner olarak açılabilir. Hoca hesap bilgisi/eylemleri, sınıf atamaları, izin listesi ve
cihaz oturumları ayrı ayrı kendi izinleriyle filtrelenir. Tek bir izne sahip olmak diğer üç
bölümün verisini veya eylemini göstermez.

**Not — MGT-19 mutlak sınır:** Hoca izinlerini değiştir/ver/geri al ekranı, `YETKI_MATRISI.md`
§2.2 madde 6 gereği hiçbir koşulda hocaya gösterilmez ve doğrudan bağlantıyla da açılamaz.
Hocaya izin görüntüleme (MGT-18) verilmiş olsa bile, bu ekrandaki düzenleme kontrollerine
hoca ulaşamaz.

---

## 12. Ekran listesi — Raporlar ve denetim

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| RPT-01 | Rapor Filtrele ve Excel İndir | Kurum/sınıf/öğrenci ve tarih bazlı rapor filtresi ve Excel dosyası indirme | Y, B, H, Z, E | KURUM-09 |
| RPT-02 | Denetim Kaydı Listesi | Kurum denetim kayıtlarının listesi; filtreleme ve arama | Y, B, H, Z | KURUM-10 |
| RPT-03 | İşlemi Geri Al | Desteklenen bir işlemi geri alma onay ekranı; geri alma geçmişi silmez, ters işlem üretir | Y, H, Z, O, E | KURUM-10 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| RPT-01 | Evet (destek modunda) | Evet | Varsayılan kapalı → rapor dışa aktarma izniyle açılır |
| RPT-02 | Evet (destek modunda) | Evet | Varsayılan kapalı → işlem geçmişi görüntüleme izniyle açılır |
| RPT-03 | Evet (destek modunda) | Evet | Varsayılan kapalı → geri alma izniyle açılır (işlem geçmişi izninden bağımsız) |

**Not — Geri alma izni bağımsızlığı:** İşlem geçmişi görüntüleme izni, geri alma iznini
otomatik açmaz. RPT-03 ayrı bir izin gerektirir.

---

## 13. Ekran listesi — Profil, oturum ve eşitleme

Bu ekranlar her kullanıcının kendi hesabına aittir; rol ve kurum kapsamlı yönetim izninden
bağımsızdır.

| Kimlik | Ekran adı | Açıklama | Durumlar | Senaryo |
|---|---|---|---|---|
| PRF-01 | Profil / Hesap | Kullanıcının kendi profil bilgileri | Y, B, H, Z | ORTAK-01 |
| PRF-02 | Kendi Cihaz / Oturum Bilgisi | Kullanıcının kendi aktif cihaz oturumlarının listesi | Y, B, H, Z | ORTAK-01 |
| PRF-03 | Çıkış Yap | Oturumdan çıkış onayı | Z, O | ORTAK-01 |
| PRF-04 | Eşitleme Durumu | Kullanıcının bekleyen, başarılı ve başarısız işlemlerinin göstergesi | Y, B, H, Z | ORTAK-02 |

### Rol bazlı görünürlük

| Ekran | Platform yöneticisi | Kurum yöneticisi | Hoca |
|---|---|---|---|
| PRF-01 | Evet | Evet | Evet |
| PRF-02 | Evet | Evet | Evet |
| PRF-03 | Evet | Evet | Evet |
| PRF-04 | Evet | Evet | Evet |

---

## 14. Navigasyon gruplaması önerisi

`YONETICI_BILGI_MIMARISI.md` §4.1 ve `HOCA_MOBIL_BILGI_MIMARISI.md` §11, sekiz bölümün sabit
sekme kararı olmadığını ve kesin navigasyon bileşeninin `UI-002`'de belirleneceğini belirtir.
Bu bölüm, ekran envanterine dayanan bir navigasyon gruplaması **önerisi** sunar; bağlayıcı
değildir.

### 14.1. Hoca navigasyonu

Günlük ve sık kullanılan işlemler ana navigasyona önceliklendirilir; seyrek kullanılan yönetim
işlemleri kontrollü bir giriş altında gruplanır.

```text
Alt navigasyon çubuğu (4 + 1 düzeni):

[Yoklama]        → ATT-01, ATT-02
[Öğrenciler]     → STD-01, STD-02, STD-03, STD-04, STD-05, STD-06, STD-07, STD-08, STD-09
[Program]        → PRG-01, PRG-02, PRG-03, PRG-04, PRS-01, PRS-02
[Daha Fazla]     → Yönetim, Rapor ve Denetim, Profil/Oturum
```

- **Yoklama** hoca için en sık ve öncelikli işlemdir; doğrudan erişim sağlanır.
- **Öğrenciler** ikinci sıklıkta kullanılır.
- **Program** hem program görüntüleme hem ilerleme girişini kapsar.
- **Daha Fazla** seyrek yönetim işlemlerini barındırır.

Alt navigasyondan önce bir sınıf seçici (CLS-01) ile sınıf bağlamı belirlenir; seçili sınıf
üst çubukta sürekli görünür.

"Daha Fazla" altında:

```text
Daha Fazla
├── Yönetim (koşullu)
│   ├── Sınıf Yönetimi → MGT-01, MGT-02, MGT-03, MGT-04, MGT-05, MGT-06
│   ├── Dönem / Takvim → MGT-07, MGT-08
│   ├── Kurum Ayarları → MGT-09, MGT-10, MGT-11
│   └── Personel → MGT-13, MGT-14, MGT-15, MGT-16, MGT-17, MGT-18, MGT-20
├── Rapor ve Denetim (koşullu)
│   ├── Excel Rapor → RPT-01
│   ├── İşlem Geçmişi → RPT-02
│   └── Geri Al → RPT-03
└── Profil / Oturum
    ├── Profil → PRF-01
    ├── Cihaz / Oturum → PRF-02
    ├── Eşitleme Durumu → PRF-04
    └── Çıkış Yap → PRF-03
```

Yönetim ve Rapor ve Denetim grupları yalnızca ilgili bağımsız izinlerden en az biri varsa
görünür.

### 14.2. Kurum yöneticisi navigasyonu

Kurum yöneticisi hocayla aynı sınıf işlemlerine ek olarak bütün yönetim ekranlarına varsayılan
olarak erişir.

```text
Alt navigasyon çubuğu (5 düzen):

[Ana Sayfa]      → HOME-01
[Sınıflar]       → CLS-01, CLS-02, ATT-01, ATT-02, STD-*, PRG-*, PRS-*
[Öğrenciler]     → STD-01 (kurum geneli), STD-02, STD-03, STD-04, STD-05, STD-06, STD-07, STD-08, STD-09
[Yönetim]        → MGT-01 ... MGT-20
[Daha Fazla]     → Raporlar, İşlem Geçmişi, Profil/Oturum
```

"Daha Fazla" altında:

```text
Daha Fazla
├── Raporlar → RPT-01
├── İşlem Geçmişi → RPT-02, RPT-03
└── Profil / Oturum
    ├── Profil → PRF-01
    ├── Cihaz / Oturum → PRF-02
    ├── Eşitleme Durumu → PRF-04
    └── Çıkış Yap → PRF-03
```

### 14.3. Platform yöneticisi navigasyonu

Platform yöneticisi kurumlar arası çalışır.

```text
Alt navigasyon çubuğu (3 + 1 düzen):

[Kurumlar]       → PLAT-01, PLAT-02, PLAT-03, PLAT-04, PLAT-05, PLAT-06
[Denetim]        → PLAT-07
[Rapor]          → PLAT-08
[Profil]         → PRF-01, PRF-02, PRF-03, PRF-04
```

Platform yöneticisi, destek modu üzerinden kurum bağlamına geçtiğinde kurum yöneticisi
navigasyonuna benzer bir kabuk görür; ancak ekranda sürekli "destek modu" göstergesi bulunur.

---

## 15. Ortak ekranlar ve izin paylaşım kuralları

### 15.1. Kurum yöneticisi ve hoca arasında paylaşılan ekranlar

Aşağıdaki ekranlar hem kurum yöneticisi hem hoca tarafından kullanılabilir; aynı görsel ekran
şablonu paylaşılır, ancak ekran içinde görünen eylemler her bağımsız izne göre ayrı ayrı
filtrelenir:

- **Sınıf işlemleri:** CLS-01, CLS-02, ATT-01, ATT-02, STD-01..STD-09, PRG-01..PRG-04,
  PRS-01, PRS-02
- **Yönetim:** MGT-01..MGT-11, MGT-13..MGT-18, MGT-20 (MGT-12 ve MGT-19 hoca için V1
  mutlak sınırlarıdır)
- **Rapor ve denetim:** RPT-01, RPT-02, RPT-03
- **Profil/oturum:** PRF-01..PRF-04

### 15.2. Personel izinleri ve MGT-13 (Hoca Listesi) paylaşımı

MGT-13 (Hoca Listesi) dört bağımsız personel izninin ortak giriş/konteyner ekranıdır:

| İzin (bağımsız) | Açtığı ekranlar |
|---|---|
| Hoca hesabı oluşturma/kapatma | MGT-13, MGT-14, MGT-15, MGT-16 |
| Hoca–sınıf ataması | MGT-13, MGT-16, MGT-17 |
| İzin görüntüleme | MGT-13, MGT-16, MGT-18 |
| Cihaz oturumu iptali | MGT-13, MGT-16, MGT-20 |

Dört izinden herhangi biri açık olduğunda MGT-13 ve seçilen hoca için MGT-16 ortak konteyneri
görünür (OR mantığı); ancak bu ekranlarda yalnızca sahip olunan iznin gerektirdiği alt bölüm,
eylem ve sınırlı hoca metaverisi gösterilir. Listenin veya detay konteynerinin görünmesi diğer
üç izne ait veriyi ya da eylemi otomatik açmaz.

### 15.3. Tehlikeli işlem ve onay gerektiren ekranlar

Aşağıdaki ekranlar tehlikeli veya geniş kapsamlı işlem içerdiğinden onay adımı (O durumu)
zorunludur (`URUN_VE_UYGULAMA_PLANI.md` §3.1):

| Ekran | İşlem |
|---|---|
| PLAT-04 | Kurum durumu değiştirme (askıya alma/arşivleme) |
| PLAT-06 | Destek modu geçişi |
| MGT-03 | Sınıf arşivleme |
| MGT-05 | Sınıf geri yükleme |
| MGT-15 | Hoca hesabı askıya alma |
| MGT-20 | Cihaz oturumu iptali |
| STD-05 | Öğrenci arşivleme |
| STD-07 | Öğrenci geri yükleme |
| ATT-02 | Geçmiş tarihli yoklama düzeltme |
| RPT-03 | İşlem geri alma |
| PRF-03 | Oturumdan çıkış |

---

## 16. Hiç sınıfı veya hiç yönetim izni olmayan hoca durumları

Bu durumlar `HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 10'da tanımlanmıştır ve teknik hata olarak
gösterilmemeli, tanımlı ve beklenen durumlar olarak ele alınmalıdır.

| Durum | Sınıf İşlemleri | Yönetim | Rapor ve Denetim |
|---|---|---|---|
| Hiç sınıfa atanmamış, kurum kapsamlı yönetim izni var | CLS-01: boş durum ("Henüz atanmış bir sınıfın yok") | Normal çalışır | Normal çalışır (iznine göre) |
| Hiç sınıfa atanmamış, hiç kurum kapsamlı yönetim izni yok | CLS-01: boş durum | Gizli (hiç gösterilmez) | Gizli (hiç gösterilmez) |
| Sınıfa atanmış, hiç yönetim izni yok | Normal çalışır | Gizli (hiç gösterilmez) | Gizli (hiç gösterilmez) |

İkinci durumda kullanıcıya kurum yöneticisiyle iletişime geçmesini öneren açıklayıcı bir
boş durum gösterilmelidir.

---

## 17. Özet ekran sayıları

| Bölüm | Ekran sayısı |
|---|---|
| Giriş ve oturum (AUTH) | 3 |
| Bağlam seçimi (CTX) | 1 |
| Platform yöneticisi (PLAT) | 8 |
| Ana sayfa (HOME) | 1 |
| Sınıf işlemleri (CLS) | 2 |
| Yoklama (ATT) | 2 |
| Öğrenciler (STD) | 9 |
| Program ve ezber (PRG) | 4 |
| İlerleme (PRS) | 2 |
| Yönetim (MGT) | 20 |
| Raporlar ve denetim (RPT) | 3 |
| Profil/oturum/eşitleme (PRF) | 4 |
| **Toplam** | **59** |

---

## 18. Ana ürün planıyla uyum kontrolü

- Her senaryo kimlikleri (PLAT-01..06, KURUM-01..12, HOCA-01..09, ORTAK-01..02)
  `AKTORLER_VE_KULLANIM_SENARYOLARI.md` §3 ile satır satır eşleştirilmiştir; hiçbir senaryo
  dışarıda bırakılmamıştır. HOCA-09 ("Genişletilmiş yönetim işleri") bir meta senaryodur ve
  hocaya devredilebilir yönetim ekranlarını (MGT-01..MGT-11, MGT-13..MGT-18, MGT-20,
  RPT-01..RPT-03) kapsar; MGT-12 bağlayıcı V1 yönetici sınırı nedeniyle bu kümede değildir.
  Ekranlar ilgili bölümlerinde bağımsız izin koşullarıyla tek tek listelenmiştir.
- Rol bazlı görünürlük tabloları `YETKI_MATRISI.md` bölüm 3 ile birebir çapraz kontrol
  edilmiştir; mutlak sınırlar ve varsayılan kapalı/açık durumlar uyumludur.
- P-005'teki platform yöneticisi ekranları (§3.2) ile P-006'daki hoca ekranları (§4) tek bir
  envanterde birleştirilmiştir; ortak ekranlar tekil hâle getirilmiş, yalnızca izne göre
  eylem filtrelemesi kuralı korunmuştur.
- `RESTORE_ARCHIVED` izninin öğrenci/sınıf için ortak tek izin olduğu `VERI_MODELI.md` §4.8
  kararıyla uyumludur.
- Anne/baba bölümü varsayılan-deny ilkesi her iki kaynak belge ile tutarlıdır; öğrenci
  görüntüleme izni anne/baba verisini kapsamaz.
- MGT-19 (hoca izinleri değiştir) `YETKI_MATRISI.md` §2.2 madde 6 gereği mutlak sınır olarak
  işaretlenmiştir.
- MGT-06 içinde kurum kapsamlı sınıf yönetimi izni ile operasyonel öğrenci verisi birbirinden
  ayrılmış; MGT-16 alt bölümleri dört bağımsız personel iznine göre filtrelenmiştir.
- MGT-12, `YETKI_MATRISI.md` §2.2 madde 6b kararı gereği V1'de yalnızca kurum yöneticisi ve
  destek modundaki platform yöneticisine açıktır; KURUM-06 senaryosuna bağlıdır.
- Tehlikeli işlem onay adımı `URUN_VE_UYGULAMA_PLANI.md` §3.1 ile uyumludur.
- Dört durum kuralı (yükleniyor, boş, hata, yetkisiz) `URUN_VE_UYGULAMA_PLANI.md` §18.3 ve
  `AGENTS.md` görev tamamlanma koşullarıyla uyumludur.
- Navigasyon gruplaması önerisi §3.1 (sezgisel kullanım) ilkesini korur: günlük işlemler
  önceliklendirilmiş, yönetim işlemleri "Daha Fazla" altına alınmıştır.
- Terim kullanımı `TERIMLER_SOZLUGU.md` ile tutarlıdır.
- Belgede ana plana veya önceki Dalga 0 belgelerine aykırı bir tanım bulunmamaktadır.

---

## 19. Varsayımlar

- Ekran kimlikleri (AUTH-01, PLAT-01, MGT-01 vb.) bu belgeye özeldir; önceki belgelerde
  kullanılan bilgi mimarisi ağaç düğüm adları, bu kimliklerle tek tek eşleştirilmiştir. Bu
  kimlik şeması sonraki görevlerde (UI-002, mobil uygulama görevleri) referans alınabilir;
  ancak bağlayıcı bir teknik API kararı değildir.
- Navigasyon gruplaması (bölüm 14) öneri niteliğindedir; kesin sekme/menü bileşen kararı
  `UI-002`'de verilecektir.
- Bazı ekranlar (STD-03/STD-04, MGT-02) oluşturma ve düzenleme işlemlerini tek bir ekranda
  birleştirebilir; bu kararın kesinleşmesi mobil uygulama tasarımı sırasında (`UI-*` görevleri)
  yapılacaktır. Bu envanter ayrı kimliklendirme yapmıştır; birleştirilmesi halinde kimlikler
  arası eşleme korunmalıdır.
- CTX-01 (bağlam seçimi) aday ekrandır; gösterilme koşulu ve kesin davranışı `UI-002`'ye
  bırakılmıştır (`YONETICI_BILGI_MIMARISI.md` bölüm 8).
- Tek sınıflı hoca optimizasyonu (CLS-01 atlama) kullanılabilirlik varsayımıdır; kesinleşmesi
  `UI-002`'ye bırakılmıştır (`HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 9).

### 19.1. Revizyon notu — v1.1

- Korumalı ekranların yetkisiz (`Z`) durumu tablo ve merkezi rota/sunucu kuralıyla
  tutarlılaştırıldı.
- MGT-06 operasyonel sınıf verisi sınırı ve MGT-16 bağımsız personel alt bölümleri
  netleştirildi.
- MGT-12'nin V1'de hocaya devredilemeyeceği, gelecekte açılmasının yeni ürün/izin kararı
  gerektirdiği kaydedildi.

### 19.2. Revizyon notu — v1.2

- MGT-12'nin örtük katalog çıkarımı bağlayıcı `YETKI_MATRISI.md` §2.2 madde 6b kararına
  dönüştürüldü ve ekran KURUM-06 senaryosuna bağlandı.
- HOCA-01 giriş/oturum senaryosu AUTH-01–AUTH-03 ekranlarında doğrudan izlenebilir yapıldı.

---

## 20. Bilinen sınırlamalar

- Bu belge ekranların alan listesini, wireframe'ini, görsel tasarımını veya tasarım tokenlarını
  içermez.
- Kesin navigasyon bileşeni (sekme sayısı, simge tasarımı, geçiş animasyonları) `UI-002`
  kapsamıdır.
- Çoklu rol/bağlam geçişinin kesin ürün davranışı `UI-002`'ye bırakılmış bir açık noktadır;
  bu belge yalnızca CTX-01 aday ekranını kaydeder.
- Özel öğrenci alanı yönetimi V1'de bağlayıcı olarak hocaya kapalıdır; gelecekte devredilmesi
  yeni ürün kararı ve izin kataloğu girdisi gerektirir.
- Eski sistem (Excel/HTML/Apps Script) bu repoda bulunmadığından, ekran envanterinin eski
  sistemdeki ekran/menü yapısıyla karşılaştırması yapılmamıştır; bu belge yalnızca onaylı ana
  plana ve Dalga 0 belgelerine dayanır.

---

## 21. Kapsam dışı bırakılanlar

- Ekran içi bileşen detayı, alan listeleri ve wireframe'ler (`UI-001`, `UI-002` ve mobil
  uygulama görevleri kapsamı).
- Kesin navigasyon kabuğu ve sekme kararı (`UI-002` kapsamı).
- API, veri modeli ve senkronizasyon sözleşmesi ayrıntıları (`P-008`, `P-009`, `P-010` kapsamı).
- Denetim ve geri alma işlem türlerinin kesin listesi (`P-011` kapsamı).
- Excel rapor veri sözleşmesi (`P-012` kapsamı).
- Öğrenci ve veli (anne/baba/vasi) kendi kullanım deneyimi ekranları (sonraki faz, Dalga 8
  `PORTAL-*`).
- Web yönetim paneli ekranları (sonraki faz, Dalga 8 `WEB-*`).
