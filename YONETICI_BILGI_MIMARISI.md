# Yönetici Mobil Bilgi Mimarisi

| Alan | Değer |
|---|---|
| Görev | P-005 — Yönetici mobil bilgi mimarisini çiz |
| Belge sürümü | 1.3 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Terim kaynağı | `TERIMLER_SOZLUGU.md` |
| Aktör/senaryo kaynağı | `AKTORLER_VE_KULLANIM_SENARYOLARI.md` |
| Yetki kaynağı | `YETKI_MATRISI.md` |
| Son güncelleme | 13 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, `AKTORLER_VE_KULLANIM_SENARYOLARI.md` (P-002) içinde listelenen platform yöneticisi
(`PLAT-*`) ve kurum yöneticisi (`KURUM-*`) senaryolarını, mobil uygulamanın **bilgi
mimarisine** — bölüm/ekran hiyerarşisi, giriş noktaları ve ekranlar arası ilişki — dönüştürür.

"Yönetici" bu belgede iki ayrı rolü kapsar: **Platform yöneticisi** ve **Kurum yöneticisi**.
Hoca rolünün bilgi mimarisi ayrı bir görevde (`P-006`) ele alınır. İki belge birlikte `P-007 —
İlk sürüm ekran envanterini çıkar` görevine girdi sağlar.

Bu belge yeni bir ürün kararı almaz; `URUN_VE_UYGULAMA_PLANI.md` bölüm 5 (roller), 6 (kurum/
sınıf), 8.2–8.10 (işlevsel modüller), 9 (kişiselleştirme) ve 10.3 (yönetici ana akışı) ile
`AKTORLER_VE_KULLANIM_SENARYOLARI.md` bölüm 3.1–3.2'de tanımlanmış senaryoları ekran/bölüm
düzeyinde somutlaştırır. Tutarlılık için `YETKI_MATRISI.md` (P-003) ve `KISISEL_VERI_ENVANTERI.md`
(P-004) referans alınmıştır; ancak bu belgenin bağımlılığı yalnızca `P-002`dir — P-003/P-004
yalnızca ek doğrulama ve çapraz kontrol amacıyla kullanılmıştır.

Bu belge:

- Ekranların görsel tasarımını (renk, tipografi, bileşen görünümü), wireframe'ini veya tasarım
  tokenlarını tanımlamaz (Faz 0'ın "düşük ayrıntılı wireframe" çıktısı ve `UI-001` kapsamı).
- Ekranların kesin alan/aksiyon envanterini üretmez; bu, bu belge ile `P-006`'nın birlikte girdi
  sağladığı `P-007 — İlk sürüm ekran envanterini çıkar` görevinin kapsamıdır.
- Mobil uygulamanın somut navigasyon bileşenini (sekme sayısı, alt/üst menü, "Daha Fazla" gibi
  gruplama) kesinleştirmez; yalnızca bölüm/ekran hiyerarşisini tanımlar (bkz. bölüm 4.1).
- API, veri modeli veya veritabanı şeması tanımlamaz (`P-008`, `P-009` kapsamı).
- Hoca bilgi mimarisini tanımlamaz (`P-006` kapsamı); yalnızca hocaya devredilebilir yönetim
  ekranlarının bu belgede tanımlanan ekranlarla aynı olduğunu ve her iznin bağımsız
  değerlendirildiğini not eder (bkz. bölüm 7).
- Rol/işlem bazlı ayrıntılı yetki kurallarını yeniden tanımlamaz; yalnızca `YETKI_MATRISI.md`
  sonucunu ekran görünürlüğüne yansıtır.

**1.1 revizyon notu:** Bu sürüm, ilk incelemede tespit edilen şu noktaları düzeltti: (1) ekran
ağacındaki yetki etiketlerinin kurum yöneticisinin erişimiyle karıştırılabilecek biçimde
yazılmış olması, (2) personel yönetimi izinlerinin tek paket gibi sunulması, (3) izin
görüntüleme ile izin değiştirmenin tek satırda birleştirilmiş olması, (4) rol bazlı görünürlük
tablosunda "mutlak sınır" ile "varsayılan kapalı, ayrı izinle açılabilir"in karıştırılması,
(5) sınıf geri yükleme ekranının eksik olması, (6) sekiz bölümün sekiz sabit sekme gibi
sunulması.

**1.2 revizyon notu:** Bu sürüm, ikinci incelemede tespit edilen şu kalan yetki-akış
boşluklarını düzeltti:

1. Arşivlenmiş sınıf ve öğrenci kaydını geri yükleme, sınıf/öğrenci yönetimi (oluşturma/
   düzenleme/arşivleme) izninden bağımsız, tek ve ayrı bir "arşivlenmiş kayıt geri yükleme"
   iznine bağlandı (bkz. bölüm 4.2, 6).
2. Anne/baba bilgisi görüntüleme, anne/baba bilgisi yönetme (oluşturma/düzenleme) ve veli
   iletişim bilgisi görüntüleme üç ayrı satır olarak ayrıştırıldı (bkz. bölüm 4.2, 6).
3. Program oluşturma/yönetme ile değerlendirme şeması ayarlama bağımsız iki izin olarak
   ayrıştırıldı (bkz. bölüm 4.2, 6).
4. Hoca Listesi, dört bağımsız personel izninden en az biriyle açılan ortak bir giriş/konteyner
   ekranı olarak yeniden tanımlandı (bkz. bölüm 4.2, 7).
5. Platform ve kurum yöneticisi için ortak profil/oturum/eşitleme ekranları eklendi ve kurum
   yönetimi ekranlarından ayrıştırıldı (bkz. yeni bölüm 5).

**1.3 revizyon notu:** Branch güncel `main` üzerine rebase edildi (`P-006 — Hoca mobil bilgi
mimarisini çiz` artık merge edilmiştir, bkz. `HOCA_MOBIL_BILGI_MIMARISI.md`). Bu sürüm, P-005 ile
P-006 arasındaki iki sözleşme çelişkisini giderir:

1. **Geri yükleme izni artık bağlayıcı "tek ortak izin" iddiası taşımıyor.** "Sınıfı Geri Yükle"
   ve "Öğrenciyi Geri Yükle" ayrı ekranlar olarak kalır; geri yükleme yetkisinin sınıf/öğrenci
   yönetimi izninden bağımsız olduğu korunur; ancak öğrenci ve sınıf geri yükleme için ortak tek
   izin mi yoksa varlık başına ayrı iki izin mi olacağı, `HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 14
   ile aynı dille, **açık soru** olarak işaretlenmiş ve `P-008`/`P-009`'a bırakılmıştır (bkz.
   bölüm 4.2, 6, 10).
2. **Anne/baba bilgisi görünürlüğü varsayılan-deny ilkesine hizalandı.** "Anne/baba bilgilerini
   görüntüleme, öğrenci görüntülemenin parçasıdır" çıkarımı kaldırıldı. Öğrenci görüntüleme
   izni artık anne/baba kaydını veya telefonunu otomatik göstermez; anne/baba bölümü yalnızca
   "anne/baba bilgisi yönetme" veya "veli iletişim bilgisi görüntüleme" izinlerinden en az biri
   varsa gösterilir; ikisi de yoksa bölüm hiç gösterilmez (bkz. bölüm 4.2, 4.3, 6).
3. Bölüm 7'deki P-006 ilişkisi, artık merge edilmiş bir belgeye bağlayıcı çapraz doğrulama olarak
   güncellendi; bağlayıcı olmayan gelecek çalışma referansı kaldırıldı.

**1.4 revizyon notu:** `P-008 — Çekirdek veri modeli taslağı` (`VERI_MODELI.md`) tamamlanmış ve
1.3'te bu belgeye bırakılan açık soruyu kesinleştirmiştir: **arşivlenmiş öğrenci/sınıf kaydını
geri yükleme izni, varlık başına ayrı değil, tek ve ortak bir `RESTORE_ARCHIVED` izin kodudur**
(bkz. `VERI_MODELI.md` §4.6). Bu sürüm, bölüm 4.2, 6, 7, 9, 10 ve 11'deki ilgili "açık soru"
işaretlerini bu bağlayıcı kararla uyumlu biçimde günceller; ekran hiyerarşisi veya izin
bağımsızlığı ilkelerinde başka bir değişiklik yapılmamıştır.

---

## 2. Bilgi mimarisi ilkeleri

Aşağıdaki ilkeler bölüm 3, 4 ve 5'teki bütün ekran hiyerarşilerine eşit biçimde uygulanır ve
her ekranda tekrar yazılmamıştır.

1. **Rol bazlı kabuk:** Kullanıcının aktif bağlamı (platform geneli veya tek bir kurum) hangi
   bölümleri göreceğini belirler. Platform yöneticisi ve kurum yöneticisi farklı bölüm kümeleri
   görür (bkz. bölüm 3.1, 4.1).
2. **Yetkiye göre filtrelenmiş menü:** Ekran/bölüm görünürlüğü hem role hem kurumda etkin
   modüllere göre belirlenir (`URUN_VE_UYGULAMA_PLANI.md` §9.2). Kapalı bir modülün veya sahip
   olunmayan bir iznin ekranı menüde görünmez **ve** doğrudan bağlantıyla da açılamaz (§9.2,
   §15 — sunucu tarafı doğrulama).
3. **Dört durum kuralı:** Veri gösteren her ekran/liste en az şu dört durumu ele alır: yükleniyor,
   boş, hata, yetkisiz (`URUN_VE_UYGULAMA_PLANI.md` §18.3; `AGENTS.md` görev tamamlanma
   koşulları). Bu belge her ekran için bu durumları tek tek yazmaz; kural olarak baştan kabul
   eder.
4. **Onaylı tehlikeli işlem:** Geri dönüşü zor veya kapsamı geniş işlemler (kurum askıya
   alma/arşivleme, hoca hesabını askıya alma, öğrenci/sınıf arşivleme, hoca izni geri alma,
   cihaz oturumu iptali, geri alma komutu) açık onay adımı gerektirir (§3.1 "Tehlikeli işlemler
   açık onay istemelidir").
5. **Mutlak sınırların ekrana yansıması:** `YETKI_MATRISI.md` §2.2'de "Mutlak sınır" olarak
   işaretlenmiş işlemlerin ekranı, hiçbir izin atamasıyla ilgisiz role açılmaz. Bu belgede bu
   tür ekranlar **[HOCA İÇİN MUTLAK SINIR]** etiketiyle işaretlenmiştir.
6. **Etiketler yalnızca hocaya devredilebilirliği gösterir:** Bölüm 4.2'deki ekran ağacında
   görünen **[Hoca delegasyonu: ...]** ve **[HOCA İÇİN MUTLAK SINIR]** etiketleri, o ekranın
   **kurum yöneticisi için** açık olup olmadığını göstermez. Kurum yöneticisi, `YETKI_MATRISI.md`
   bölüm 3'te kendi sütununda "Evet" görünen bütün yönetim ekranlarına kendi kurumunda **her
   zaman ve varsayılan olarak** erişir. Etiketler yalnızca aynı ekranın **hocaya** hangi koşulda
   (varsayılan kapalı → ayrı izinle açılabilir, ya da hiçbir zaman) açılabileceğini gösterir; bu
   nedenle her dal/ekran için ayrıca "kurum yöneticisi için her zaman açık" notu eklenmiştir.
7. **Bağımsız izinler ayrı ayrı değerlendirilir:** Aynı ekran grubuna (ör. Personel, Öğrenciler,
   Program, Kurum Ayarları) ait birden fazla işlem izni birbirinden bağımsızdır
   (`YETKI_MATRISI.md` §3.3, §3.4, §3.6, §4.3). Bir iznin (ör. hoca hesabı oluşturma) hocaya
   açılmış olması, aynı gruptaki başka bir iznin (ör. hoca izinlerini görüntüleme, anne/baba
   bilgisi yönetme veya değerlendirme şeması ayarlama) otomatik olarak açıldığı anlamına
   **gelmez**. `YETKI_MATRISI.md` §4.3'teki kategorilendirme ("aynı kategoride sayılmıştır" gibi
   ifadeler dahil) yalnızca kurum yöneticisinin izin yönetimini kolaylaştıran bir gruplamadır;
   toplu bir yetki paketi veya otomatik izin bağlanması değildir. Her ekran, yalnızca kendi
   ilgili izni ayrıca verilmişse görünür.
8. **Ortak konteyner ekranları OR mantığıyla açılır:** Birden fazla bağımsız iznin herhangi biri
   tarafından açılabilen paylaşımlı bir giriş/liste ekranı (ör. Hoca Listesi, bkz. bölüm 4.2,
   7) varsa, bu ekran "iznlerden en az biri" mantığıyla görünür olur; ancak ekranın kendisinin
   görünür olması, o ekrana bağlı **alt eylemlerin** her birini otomatik açmaz — her alt eylem
   yine kendi ilgili izniyle ayrıca değerlendirilir.

---

## 3. Platform yöneticisi bilgi mimarisi

### 3.1. Giriş noktası ve ana işlevsel bölümler

Platform yöneticisinin tek bir kurumu yoktur; bu nedenle girişten sonra doğrudan kurum listesine
düşer. Ana işlevsel bölümler üçtür: **Kurumlar**, **Sistem Geneli Denetim** ve **Kurumlar Arası
Rapor**. Bu üçü, `AKTORLER_VE_KULLANIM_SENARYOLARI.md` §3.1'deki `PLAT-01`–`PLAT-06`
senaryolarının tamamını karşılar. Bu üç bölümün somut navigasyon bileşeni (sekme, menü vb.)
`P-007`/`UI-002`'de kesinleşecektir; bu belge yalnızca bölüm hiyerarşisini tanımlar. Platform
yöneticisinin kendi profil/oturum ekranları için bkz. bölüm 5.

### 3.2. Ekran hiyerarşisi

```
Giriş / Oturum (ORTAK-01)
└─ Platform Ana Sayfası → Kurumlar Listesi (varsayılan giriş ekranı)
   ├─ Kurumlar
   │  ├─ Kurum Listesi (PLAT-01, PLAT-03) — arama/filtre: durum (aktif/askıda/arşiv)
   │  ├─ Kurum Oluştur (PLAT-01)
   │  └─ Kurum Detayı
   │     ├─ Kurum Bilgisi ve Durum Yönetimi (PLAT-03: aktif/askıda/arşivle — onay adımlı)
   │     ├─ Kurum Yöneticisi Ata (PLAT-02)
   │     └─ Kurum Bağlamına Geç — destek modu (PLAT-04)
   │        → Kurum yöneticisi kabuğuna geçiş (bkz. bölüm 4); ekranda sürekli "destek modu"
   │          göstergesi bulunur ve geçiş otomatik olarak denetim kaydı üretir
   ├─ Sistem Geneli Denetim Kaydı (PLAT-05) — kurum bazında filtrelenebilir, kurumlar
   │  birbirine karıştırılmadan gösterilir
   └─ Kurumlar Arası Rapor (PLAT-06) — kurum bazında ayrıştırılmış rapor/özet
```

### 3.3. Ekrana özel notlar

- **Kurum Listesi boş durumu:** Platform henüz hiç kurum oluşturmadıysa liste boştur; boş
  durumda doğrudan "Kurum Oluştur" eylemi önerilir.
- **Kurum Durumu Yönetimi:** Askıya alma ve arşivleme, kurumun bütün kullanıcılarını etkileyen
  geniş kapsamlı işlemlerdir; bölüm 2 madde 4 gereği onay adımı zorunludur.
- **Kurum Bağlamına Geç (destek modu):** Bu ekran, platform yöneticisinin bölüm 4'teki kurum
  yöneticisi ekranlarına salt kendi rolüyle değil, "destek amaçlı erişim" bağlamıyla girdiğini
  görünür kılar (`YETKI_MATRISI.md` §3.1, "destek amaçlı erişimde" notu). Bu, platform
  yöneticisinin izlenmeyen bir üst rol olmadığını kullanıcıya da açık eden bir bilgi mimarisi
  kararıdır (`URUN_VE_UYGULAMA_PLANI.md` §5.1).
- **Sistem Geneli Denetim Kaydı:** Kurum yöneticisinin gördüğü "İşlem Geçmişi" ekranından
  (bölüm 4.1) ayrı bir ekrandır; kurum yöneticisi yalnızca kendi kurumunu görür, platform
  yöneticisi hepsini kurum bazında ayrıştırılmış biçimde görür (`YETKI_MATRISI.md` §3.1).

---

## 4. Kurum yöneticisi bilgi mimarisi

### 4.1. Giriş noktası ve işlevsel bölümler

Kurum yöneticisi girişten sonra kendi kurumunun ana sayfasına düşer. Bilgi mimarisi,
`URUN_VE_UYGULAMA_PLANI.md` §8.2–§8.10 ve §9'daki modüllerle, `AKTORLER_VE_KULLANIM_SENARYOLARI.md`
§3.2'deki `KURUM-01`–`KURUM-12` senaryolarının tamamını karşılayacak **sekiz işlevsel bölüme**
ayrılmıştır: Sınıflar, Personel (Hocalar), Öğrenciler, Program ve Ezber Takibi, Eğitim Dönemi ve
Takvim, Raporlar, İşlem Geçmişi (Denetim), Kurum Ayarları. Kurum yöneticisinin kendi profil/
oturum ekranları için bkz. bölüm 5.

Bu sekiz bölüm bir **bilgi mimarisi hiyerarşisidir**, sabit bir sekme/menü kararı değildir:

- Sekiz bölümün tamamının aynı anda alt/üst sekme olarak gösterilmesi zorunlu değildir; mobil
  uygulamada aynı anda sekiz ana sekme sunmak sezgisel kullanım ilkesiyle (§3.1) çelişebilir.
- Sık kullanılan bölümler (ör. Sınıflar, Öğrenciler) ana navigasyonda; daha seyrek kullanılan
  yönetim bölümleri (ör. Kurum Ayarları, İşlem Geçmişi) "Daha Fazla" / "Yönetim" gibi kontrollü
  bir giriş altında gruplanabilir.
- Kesin mobil navigasyon kabuğu, sekme sayısı ve gruplaması `P-007` (ekran envanteri) ve
  `UI-002` (navigasyon ve rol bazlı menü sözleşmesi) görevlerinde kararlaştırılacaktır.
- Bu belge yalnızca bölüm hiyerarşisini ve her bölümün içerdiği ekranları tanımlar; hangi somut
  navigasyon bileşenine (sekme çubuğu, çekmece menü, alt menü) yerleşeceğini kesinleştirmez.

### 4.2. Ekran hiyerarşisi

Aşağıdaki ağaçta her üst dal için önce **kurum yöneticisinin erişim durumu** belirtilir (bu her
zaman `YETKI_MATRISI.md` bölüm 3'teki "Kurum yöneticisi: Evet" satırlarına dayanır ve kurum
yöneticisi için hiçbir koşulda kapalı değildir). Alt ekranlardaki **[Hoca delegasyonu: ...]** ve
**[HOCA İÇİN MUTLAK SINIR]** etiketleri yalnızca aynı ekranın **hocaya** hangi koşulda
açılabileceğini gösterir (bkz. bölüm 2 madde 6–8, bölüm 7). Aynı gruptaki birden fazla ekranın
etiketi birbirine benzer görünse bile **her biri ayrı, bağımsız bir izne karşılık gelir**;
"aynı kategoride" notu asla "aynı izinle otomatik açılır" anlamına gelmez (bölüm 2 madde 7).

```
Giriş / Oturum (ORTAK-01)
└─ Kurum Ana Sayfası (varsayılan giriş ekranı; kurum adı/logosu üstte gösterilir — §9.1)
   │
   ├─ Sınıflar — kurum yöneticisi için her zaman açık
   │  ├─ Sınıf Listesi (KURUM-03)
   │  ├─ Sınıf Oluştur / Düzenle
   │  │     [Hoca delegasyonu: sınıf yönetimi izniyle açılabilir — bağımsız izin]
   │  ├─ Sınıf Arşivle (onay adımlı; geçmiş kayıtlar korunur — §6.3, §14)
   │  │     [Hoca delegasyonu: sınıf yönetimi izniyle açılabilir — bağımsız izin]
   │  ├─ Sınıf Detayı
   │  │  ├─ Atanmış Hocalar
   │  │  └─ Atanmış Öğrenciler
   │  └─ Arşivlenmiş Sınıflar (KURUM-11)
   │     ├─ Arşivlenmiş Sınıf Detayı
   │     └─ Sınıfı Geri Yükle (onay adımlı)
   │           [Hoca delegasyonu: `RESTORE_ARCHIVED` izniyle açılabilir — sınıf yönetimi
   │            (oluşturma/düzenleme/arşivleme) izninden BAĞIMSIZ, ayrı bir izin. Öğrenci
   │            geri yüklemeyle ORTAK, TEK izindir — bu, `VERI_MODELI.md` (P-008) §4.6'da
   │            bağlayıcı olarak kesinleşmiştir (önceki sürümde bölüm 10'da açık soruydu),
   │            bkz. YETKI_MATRISI §3.4]
   │
   ├─ Personel (Hocalar) — kurum yöneticisi için her zaman açık
   │  ├─ Hoca Listesi (KURUM-04) — ortak giriş/konteyner ekranı
   │  │     [Hoca delegasyonu: dört bağımsız personel izninden (hoca hesabı oluşturma/
   │  │      kapatma, hoca–sınıf ataması, izin görüntüleme, cihaz oturumu iptali) EN AZ
   │  │      BİRİ hocaya açıldığında görünür; listede yalnızca sahip olunan iznin
   │  │      gerektirdiği sınırlı hoca metaverisi ve eylemi gösterilir. Listenin görünmesi
   │  │      diğer üç personel iznini/eylemini otomatik açmaz, bkz. bölüm 7]
   │  ├─ Hoca Oluştur / Geçici Giriş Bilgisi Ver (KURUM-04)
   │  │     [Hoca delegasyonu: yalnızca hoca hesabı oluşturma/kapatma izniyle açılabilir]
   │  ├─ Hoca Hesabını Askıya Al (KURUM-04, onay adımlı)
   │  │     [Hoca delegasyonu: yalnızca hoca hesabı oluşturma/kapatma izniyle açılabilir]
   │  └─ Hoca Detayı
   │     ├─ Sınıf Ataması (KURUM-05)
   │     │     [Hoca delegasyonu: yalnızca hoca–sınıf ataması izniyle açılabilir]
   │     ├─ İzin Listesi / Yetkileri Görüntüle (KURUM-05)
   │     │     [Hoca delegasyonu: yalnızca izin görüntüleme izniyle açılabilir]
   │     ├─ İzinleri Değiştir / Ver / Geri Al (KURUM-05)
   │     │     [HOCA İÇİN MUTLAK SINIR — hiçbir koşulda hocaya gösterilmez, doğrudan
   │     │      bağlantıyla da açılamaz; bkz. YETKI_MATRISI §2.2 madde 6]
   │     └─ Cihaz Oturumları / Oturum İptali (KURUM-12)
   │           [Hoca delegasyonu: yalnızca cihaz oturumu iptali izniyle açılabilir]
   │
   ├─ Öğrenciler — kurum yöneticisi için her zaman açık
   │  ├─ Öğrenci Listesi — arama/filtre/sıralama (KURUM-06)
   │  ├─ Öğrenci Oluştur / Düzenle
   │  │     [Hoca delegasyonu: öğrenci yönetimi izniyle açılabilir — bağımsız izin]
   │  ├─ Öğrenci Detayı
   │  │  ├─ Anne / Baba Bölümü — yalnızca aşağıdaki iki bağımsız izinden EN AZ BİRİ
   │  │  │  varsa gösterilir; ikisi de yoksa bölüm öğrenci detayında HİÇ gösterilmez.
   │  │  │  Öğrenci görüntüleme izni bu bölümü otomatik açmaz (varsayılan-deny, §2.2
   │  │  │  madde 1)
   │  │  │  ├─ Anne / Baba Bilgilerini Oluştur / Düzenle (KURUM-06)
   │  │  │  │     [Hoca delegasyonu: anne/baba bilgisi yönetme izniyle açılabilir —
   │  │  │  │      öğrenci görüntüleme izninden BAĞIMSIZ ayrı izin; bu izin ilgili
   │  │  │  │      anne/baba kaydını işlem amacıyla görüntüleme VE düzenleme hakkı sağlar]
   │  │  │  └─ Veli İletişim Bilgisini Görüntüle (salt okunur)
   │  │  │        [Hoca delegasyonu: veli iletişim bilgisi görüntüleme izniyle açılabilir
   │  │  │         — öğrenci görüntüleme VE anne/baba bilgisi yönetme izninden BAĞIMSIZ
   │  │  │         ayrı izin, bkz. YETKI_MATRISI §3.4]
   │  ├─ Öğrenci Arşivle (onay adımlı)
   │  │     [Hoca delegasyonu: öğrenci yönetimi izniyle açılabilir — bağımsız izin]
   │  └─ Arşivlenmiş Öğrenciler (KURUM-11)
   │     └─ Öğrenciyi Geri Yükle (onay adımlı)
   │           [Hoca delegasyonu: `RESTORE_ARCHIVED` izniyle açılabilir — öğrenci yönetimi
   │            izninden BAĞIMSIZ ayrı izin. Sınıf geri yüklemeyle ORTAK, TEK izindir —
   │            `VERI_MODELI.md` (P-008) §4.6'da bağlayıcı olarak kesinleşmiştir (bkz.
   │            bölüm 10), bkz. YETKI_MATRISI §3.4]
   │
   ├─ Program ve Ezber Takibi — kurum yöneticisi için her zaman açık
   │  ├─ Program Listesi — aynı sınıfta birden fazla aktif program (KURUM-07)
   │  │     (görüntüleme: hoca atanmış sınıfta varsayılan olarak görebilir — YETKI_MATRISI §3.6)
   │  ├─ Program Oluştur — hazır şablon veya serbest tanım; manuel günlük görev ya da çok
   │  │     günlük şablon dağıtımı (§8.6, §8.7)
   │  │     [Hoca delegasyonu: program oluşturma/yönetme izniyle açılabilir — bağımsız izin]
   │  ├─ Program Düzenle
   │  │     [Hoca delegasyonu: program oluşturma/yönetme izniyle açılabilir — bağımsız izin]
   │  └─ Değerlendirme Şeması Ayarı — puan/not/tekrar gerekli alanları (KURUM-08)
   │        [Hoca delegasyonu: değerlendirme şeması ayarlama izniyle açılabilir — program
   │         oluşturma/yönetme izninden BAĞIMSIZ ayrı izin]
   │
   ├─ Eğitim Dönemi ve Takvim — kurum yöneticisi için her zaman açık
   │  ├─ Dönem Listesi (aktif/geçmiş, arşivlenebilir — §6.2)
   │  └─ Dönem Oluştur / Düzenle — çalışma günleri, tatiller (KURUM-02)
   │        [Hoca delegasyonu: sınıf/dönem yönetimiyle aynı kategoride sayılan — ancak yine
   │         de kendi başına ayrı, varsayılan kapalı ve ayrı izinle açılabilir bir izin,
   │         bkz. YETKI_MATRISI §3.1]
   │
   ├─ Raporlar — kurum yöneticisi için her zaman açık
   │  └─ Rapor Filtrele ve Excel İndir — kurum/sınıf/öğrenci ve tarih bazlı (KURUM-09)
   │        [Hoca delegasyonu: rapor dışa aktarma izniyle açılabilir — bağımsız izin]
   │
   ├─ İşlem Geçmişi (Denetim) — kurum yöneticisi için her zaman açık
   │  ├─ Denetim Kaydı Listesi / Filtre (KURUM-10)
   │  │     [Hoca delegasyonu: işlem geçmişi görüntüleme izniyle açılabilir — bağımsız izin]
   │  └─ Desteklenen İşlemi Geri Al (KURUM-10, onay adımlı)
   │        [Hoca delegasyonu: geri alma izniyle açılabilir — görüntüleme izninden bağımsız]
   │
   └─ Kurum Ayarları — kurum yöneticisi için her zaman açık
      ├─ Marka Ayarları — ad/logo/renk (KURUM-01)
      │     [Hoca delegasyonu: marka ayarı izniyle açılabilir — bağımsız izin]
      ├─ Etkin Modüller
      │     [Hoca delegasyonu: modül yönetimi izniyle açılabilir — bağımsız izin]
      ├─ Kuruma Özel Yoklama Durumları — Geç geldi, İzinli, Hasta vb. (§8.5)
      │     [Hoca delegasyonu: yoklama durumu tanımlama izniyle açılabilir — bağımsız izin]
      └─ Özel Öğrenci Alanları (§9.3)
```

### 4.3. Ekrana özel notlar

- **Sınıf Oluştur sıralaması:** Sınıf bir eğitim dönemine bağlıdır (§6.3); bu nedenle sınıf
  oluşturma akışı en az bir aktif dönemin var olmasını gerektirir. Dönem tanımlı değilse "Sınıf
  Oluştur" ekranı önce dönem oluşturmaya yönlendirir.
- **Program Oluştur dallanması:** Tek bir "Program Oluştur" giriş noktası, ana planın 8.7
  bölümündeki iki üretim yöntemine (elle günlük görev ekleme / şablon hazırlayıp takvime dağıtma)
  göre dallanır; bu bilgi mimarisi seviyesinde tek ekran, akış seviyesinde iki yoldur.
- **Marka Ayarları erişilebilirlik notu:** Renk seçimi ekranı, ana planın §9.1 ilkesi gereği
  ("Sistem, okunmaz renk kombinasyonlarını reddedebilmeli") bir uyarı/kısıtlama davranışı
  içerecektir; bu belge yalnızca ekranın var olduğunu ve bu davranışa ihtiyaç duyduğunu not eder,
  görsel tasarımını tanımlamaz.
- **Arşivlenmiş kayıtlar ayrı liste, geri yükleme izni tek ve ortaktır (kapatılan açık soru):**
  Arşivlenmiş öğrenciler ve arşivlenmiş sınıflar, normal listelerden ayrı ekranlarda tutulur;
  bu, arşivlemenin fiziksel silme olmadığını ve geri yüklenebilir olduğunu kullanıcıya açıkça
  göstermek içindir (§14). `KURUM-11` senaryosu hem öğrenci hem sınıf geri yüklemeyi kapsar.
  `YETKI_MATRISI.md` §3.4 bunu tek bir "arşivlenmiş öğrenci/sınıf kaydını geri yükleme" satırı
  olarak yazar; bu satırın öğrenci ve sınıf için **ortak tek izin mi yoksa varlık başına ayrı
  iki izin mi** olacağı bu belgenin önceki sürümünde bağlayıcı olarak kesinleştirilmemiş,
  `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) bölüm 14 ile hizalı bir açık soru olarak `P-008`'e
  bırakılmıştı. `VERI_MODELI.md` (P-008) §4.6 bu soruyu artık bağlayıcı biçimde kapatmıştır:
  **`RESTORE_ARCHIVED` öğrenci ve sınıf için tek, ortak bir izin kodudur**; varlık başına ayrı
  izin yoktur. Bu geri yükleme yetkisi, sınıf/öğrenci yönetimi (oluşturma/düzenleme/arşivleme)
  izninden **bağımsız** kalmaya devam eder; bir hocaya sınıf veya öğrenci yönetimi izni
  verilmiş olması, geri yükleme ekranını otomatik açmaz.
- **Anne/baba bölümü varsayılan-deny'e tabidir:** Öğrenci görüntüleme izni anne/baba kayıtlarını
  veya telefonlarını **otomatik göstermez** (`YETKI_MATRISI.md` §2.2 madde 1). Öğrenci
  detayındaki anne/baba bölümü yalnızca şu iki bağımsız izinden **en az biri** varsa gösterilir:
  anne/baba bilgisi yönetme (oluşturma/düzenleme — bu izin ilgili anne/baba kaydını işlem
  amacıyla görüntüleme ve düzenleme hakkı sağlar) veya veli iletişim bilgisi görüntüleme
  (anne/baba kimlik ve iletişim bilgisini salt okunur gösterir). İkisinden hiçbiri yoksa öğrenci
  detayında anne/baba bölümü hiç gösterilmez. Bu iki izin birbirinden bağımsızdır; biri diğerini
  içermez. Kurum yöneticisi kendi kurumunda her iki işlemi de her zaman yapabilir; hoca için
  ikisi de varsayılan kapalıdır. Bu sözleşme `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) ile
  hizalıdır.
- **Personel izinlerinin bağımsızlığı ve Hoca Listesi'nin ortak girişi:** "Personel (Hocalar)"
  bölümündeki dört alt eylem (hoca hesabı yönetimi, sınıf ataması, izin görüntüleme, cihaz
  oturumu iptali) dört ayrı izne bağlıdır ve her biri kurum yöneticisi tarafından tek başına
  açılıp kapatılabilir. "Hoca Listesi" ekranı bu dördünün **ortak giriş noktasıdır**: dört
  izinden herhangi biri açık olduğunda liste görünür hale gelir, ancak listenin görünür olması
  diğer üç eylemi açmaz (bkz. bölüm 7).

---

## 5. Ortak yönetici ekranları (Platform ve Kurum yöneticisi)

Aşağıdaki ekranlar hem platform yöneticisi hem kurum yöneticisi için ortaktır ve kurum/platform
yönetimi ekranlarıyla (bölüm 3, 4) karıştırılmamalıdır; bunlar her kullanıcının **kendi
hesabına** ait, rol bağımsız ekranlardır.

```
(Platform Ana Sayfası / Kurum Ana Sayfası — her ikisinden de erişilebilir)
└─ Profil / Hesap
   ├─ Kendi Cihaz / Oturum Bilgisi (ORTAK-01)
   ├─ Çıkış Yap (ORTAK-01)
   └─ Eşitleme Durumu Görüntüle (ORTAK-02)
```

- **Kendi oturumundan çıkış / kendi cihazını yönetme**, `YETKI_MATRISI.md` §3.3'te "Kapsam
  dışı — her aktörün kendi hesabı için varsayılan hakkıdır, devredilebilirlik sorusu bu işlem
  için geçerli değildir" olarak işaretlenmiştir; bu nedenle platform yöneticisi, kurum
  yöneticisi ve hoca dahil her kullanıcı için varsayılan olarak açıktır (`ORTAK-01`).
- **Eşitleme durumu görüntüleme**, kullanıcının kendi yaptığı işlemin bekliyor/başarılı/
  başarısız olduğunu görmesidir (`ORTAK-02`, `URUN_VE_UYGULAMA_PLANI.md` §13); rol bağımsızdır.
- Bu ekranlar, **"Başka kullanıcının cihaz oturumlarını iptal etme"** (KURUM-12, bölüm 4.2
  Personel dalı) işleminden **tamamen ayrıdır**: kendi oturumunu kapatmak her zaman serbestken,
  başka bir kullanıcının oturumunu iptal etmek ayrı ve devredilebilir bir yönetim iznidir
  (`YETKI_MATRISI.md` §3.3).
- Platform yöneticisinin "Profil / Hesap" ekranı Platform Ana Sayfası'ndan, kurum
  yöneticisinin "Profil / Hesap" ekranı Kurum Ana Sayfası'ndan erişilebilir; ikisi aynı ekran
  şablonunu paylaşabilir ama ayrı birer giriş noktasına sahiptir ve birbirinin verisini
  göstermez (kurum izolasyonu, §15).

---

## 6. Rol bazlı görünürlük özeti

Aşağıdaki tablo, her ekranın hangi rolde göründüğünü `YETKI_MATRISI.md` bölüm 3 ile satır satır
çapraz bağlar. Tablo şu şekilde düzenlenmiştir: (a) arşivlenmiş kayıt geri yükleme, sınıf/öğrenci
yönetiminden ayrı satırlara taşındı; geri yükleme izninin öğrenci/sınıf için ortak mı ayrı mı
olacağı `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) ile hizalı biçimde **açık soru** olarak
işaretlendi (bkz. bölüm 10); (b) anne/baba bilgisi yönetme (oluşturma/düzenleme) ve veli
iletişim bilgisi görüntüleme iki bağımsız satıra ayrıldı; önceki sürümdeki "anne/baba
görüntüleme öğrenci görüntülemenin parçasıdır" çıkarımı kaldırıldı — öğrenci görüntüleme izni
artık anne/baba verisini kapsamadığı açıkça belirtiliyor; (c) program oluşturma/yönetme ile
değerlendirme şeması ayarlama ayrı satırlara bölündü; (d) Hoca Listesi ortak giriş ekranı için
ayrı bir satır eklendi; (e) ortak profil/oturum/eşitleme satırları eklendi.

"Hoca (ilgili devredilmiş izinle)" sütunu, o satırın **kendi bağımsız izniyle** hocaya
açılabileceğini gösterir; aynı bölümdeki başka bir satırın izni açık olsa da bu satır otomatik
açılmaz (bkz. bölüm 2 madde 7).

| Bölüm / ekran | Platform yöneticisi | Kurum yöneticisi | Hoca (ilgili devredilmiş izinle) | Yetki kaynağı |
|---|---|---|---|---|
| Kurumlar (liste/oluştur/durum/atama) | Evet | Kapsam dışı | Kapsam dışı | `YETKI_MATRISI.md` §3.1 |
| Kurum Bağlamına Geç (destek modu) | Evet (denetimli) | Kapsam dışı | Kapsam dışı | §3.1 |
| Sistem Geneli Denetim / Kurumlar Arası Rapor | Evet | Hayır — mutlak sınır (yalnızca kendi kurumu) | Hayır — mutlak sınır | §3.1 |
| Sınıflar — görüntüleme | Evet (destek modu) | Evet | Evet (yalnızca atanmış sınıf) | §3.2 |
| Sınıflar — oluşturma/düzenleme/arşivleme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir | §3.2 |
| Sınıflar — arşivlenmiş sınıf geri yükleme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (`RESTORE_ARCHIVED` izni — sınıf yönetiminden BAĞIMSIZ; öğrenci geri yüklemeyle ORTAK tek izin, `VERI_MODELI.md` §4.6'da kesinleşti, bkz. bölüm 10) | §3.4 |
| Eğitim Dönemi ve Takvim | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir | §3.1 |
| Personel — Hoca Listesi (ortak giriş) | Evet (destek modu) | Evet | Dört bağımsız personel izninden EN AZ BİRİYLE açılabilir (bkz. bölüm 7) | §3.3 |
| Personel — Hoca hesabı oluşturma/kapatma | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir | §3.3 |
| Personel — Hoca–sınıf ataması | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (bağımsız izin) | §3.2, §3.3 |
| Personel — İzin listesi görüntüleme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (bağımsız izin) | §3.3 |
| Personel — İzinleri değiştir/ver/geri al | Evet (destek modu) | Evet | Hayır — mutlak sınır (hiçbir zaman) | §3.3 |
| Personel — Cihaz oturumu iptali | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (bağımsız izin) | §3.3 |
| Öğrenciler — görüntüleme | Evet (destek modu) | Evet | Evet (yalnızca atanmış sınıf) | §3.4 |
| Öğrenciler — oluşturma/düzenleme/arşivleme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir | §3.4 |
| Öğrenciler — arşivlenmiş öğrenci geri yükleme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (`RESTORE_ARCHIVED` izni — öğrenci yönetiminden BAĞIMSIZ; sınıf geri yüklemeyle ORTAK tek izin, `VERI_MODELI.md` §4.6'da kesinleşti, bkz. bölüm 10) | §3.4 |
| Öğrenciler — Anne/Baba bilgisi oluşturma/düzenleme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (öğrenci görüntülemeden BAĞIMSIZ; ilgili kaydı görüntüleme+düzenleme hakkı sağlar) | §3.4 |
| Öğrenciler — Veli iletişim bilgisi görüntüleme (salt okunur) | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (öğrenci görüntüleme VE anne/baba yönetiminden BAĞIMSIZ) | §3.4 |
| Program ve Ezber Takibi — görüntüleme | Evet (destek modu) | Evet | Evet (yalnızca atanmış sınıf) | §3.6 |
| Program ve Ezber Takibi — oluşturma/yönetme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir | §3.6 |
| Program ve Ezber Takibi — değerlendirme şeması ayarlama | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (program yönetiminden BAĞIMSIZ) | §3.6 |
| Raporlar | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir | §3.7 |
| İşlem Geçmişi — görüntüleme | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (bağımsız izin) | §3.7 |
| İşlem Geçmişi — desteklenen işlemi geri alma | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (görüntüleme izninden bağımsız) | §3.7 |
| Kurum Ayarları — Marka | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (bağımsız izin) | §3.1 |
| Kurum Ayarları — Etkin Modüller | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (bağımsız izin) | §3.1 |
| Kurum Ayarları — Kuruma Özel Yoklama Durumları | Evet (destek modu) | Evet | Varsayılan kapalı → ayrı izinle açılabilir (bağımsız izin) | §3.5 |
| Profil/Hesap — kendi oturumdan çıkış / kendi cihaz yönetimi | Evet | Evet | Evet (her aktör kendi hesabı için) | §3.3, `ORTAK-01` |
| Profil/Hesap — eşitleme durumu görüntüleme | Evet | Evet | Evet (rol bağımsız) | `ORTAK-02` |

Öğrenci görüntüleme izni anne/baba kaydını veya telefonunu kapsamaz; anne/baba bölümü yukarıdaki
iki bağımsız izinden (anne/baba bilgisi yönetme, veli iletişim bilgisi görüntüleme) en az biri
yoksa öğrenci detayında hiç gösterilmez (bkz. bölüm 4.3).

---

## 7. Hocaya devredilebilir yönetim ekranları ve `P-006` ile ilişki

`P-006 — Hoca mobil bilgi mimarisini çiz` görevi tamamlanmış ve `main` dalına merge edilmiştir
(`HOCA_MOBIL_BILGI_MIMARISI.md`). Bu bölüm artık bağlayıcı olmayan bir gelecek çalışma
referansı değil; iki belge arasındaki ortak yönetim ekranı sözleşmesini bağlayıcı biçimde
tutarlı hale getirir. P-005 ve P-006, birlikte `P-007`'ye çelişkisiz ve tutarlı bir girdi
sağlar.

`YETKI_MATRISI.md` bölüm 4.3 kategori 5 ("Personel yönetimi"), kurum yöneticisinin hocaya
devredebileceği dört işlemi bir arada listeler; ancak bu kategorilendirme yalnızca kurum
yöneticisinin izin yönetimi ekranında **gruplama kolaylığı** sağlar — dört izin birbirinden
**bağımsızdır** (`YETKI_MATRISI.md` §3.3, §4.3; `HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 6).
Kurum yöneticisi bunlardan istediğini tek başına açabilir veya kapatabilir; birinin açık olması
diğerlerini otomatik açmaz.

Bağımsız izinler ve karşılık geldiği ekranlar:

| İzin (bağımsız) | Açtığı ekran(lar) |
|---|---|
| Hoca hesabı oluşturma/kapatma | Hoca Listesi (paylaşılan giriş), Hoca Oluştur, Hoca Hesabını Askıya Al |
| Hoca–sınıf ataması | Hoca Listesi (paylaşılan giriş), Hoca Detayı → Sınıf Ataması |
| Hoca izinlerini görüntüleme | Hoca Listesi (paylaşılan giriş), Hoca Detayı → İzin Listesi / Yetkileri Görüntüle |
| Cihaz oturumlarını iptal etme | Hoca Listesi (paylaşılan giriş), Hoca Detayı → Cihaz Oturumları / Oturum İptali |

**Hoca Listesi, dört iznin ortak giriş/konteyner ekranıdır:** Dört izinden herhangi biri hocaya
açıldığında liste görünür hale gelir (OR mantığı — bkz. bölüm 2 madde 8); ancak liste yalnızca
**sahip olunan iznin gerektirdiği sınırlı hoca metaverisini** (ör. yalnızca sınıf ataması izni
varsa yalnızca atama yapmaya yetecek asgari bilgi) gösterir. Listenin görünür olması, kalan üç
izni veya onların eylemlerini otomatik açmaz. Örneğin yalnızca "cihaz oturumu iptali" izni
verilmiş bir hoca, Hoca Listesi'ni görür ve bir hocanın oturumunu iptal edebilir; ancak aynı
listeden hoca oluşturamaz, sınıf ataması yapamaz veya izin göremez.

**İzinleri Değiştir / Ver / Geri Al** ekranı bu dört iznin hiçbirine dahil değildir ve hiçbir
koşulda hocaya açılmaz — bu, `YETKI_MATRISI.md` §2.2 madde 6'daki mutlak sınırdır. Hocaya izin
görüntüleme yetkisi verilmiş olsa dahi, bu ekrandaki düzenleme kontrolleri (izin ver/geri al)
hocaya hiç gösterilmez ve doğrudan bağlantıyla da açılamaz (bölüm 2 madde 2 ve 5).

`HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 12'de belirtildiği gibi, `P-006`'daki "Personel işlemleri"
düğümleri (hoca hesabı oluşturma/kapatma, hoca–sınıf ataması, hoca izinlerini görüntüleme, cihaz
oturumu iptali) bu ekranları **yeniden tanımlamamış**; hoca navigasyonundan, hocaya açık **her
bağımsız izin için ayrı ayrı** bir giriş noktası eklemiş ve bu belgedeki ortak ekranlara
(Hoca Listesi dahil) bağlanacağını referans almıştır. Aynı ekranın iki belgede farklı
tanımlanması veya izinlerin toplu paket gibi sunulması, ekran envanteri (`P-007`) aşamasında
çelişkiye yol açardı; iki belge arasında böyle bir çelişki bulunmamaktadır.

**İki belge arasındaki çapraz doğrulama:**

- **Arşivlenmiş kayıt geri yükleme:** Her iki belge de bu izni sınıf/öğrenci yönetimi
  (oluşturma/düzenleme/arşivleme) izninden bağımsız sayar. Öğrenci/sınıf için ortak mı ayrı mı
  izin olacağı önceki sürümde her iki belgede de **açık soru** olarak `P-008`'e bırakılmıştı
  (bkz. bölüm 10; `HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 14); `VERI_MODELI.md` (P-008) §4.6 bunu
  artık bağlayıcı biçimde **ortak, tek `RESTORE_ARCHIVED` izni** olarak kesinleştirmiştir.
- **Anne/baba ve veli görünürlüğü:** Her iki belge de anne/baba bilgisi yönetme (oluşturma/
  düzenleme) ile veli iletişim bilgisi görüntülemeyi bağımsız, varsayılan kapalı iki izin olarak
  tanımlar; öğrenci görüntüleme izninin bu ikisini kapsamadığını ve ikisi de yoksa anne/baba
  bölümünün hiç gösterilmediğini belirtir (`HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 4, 7).
- **Program/değerlendirme ayrımı:** Her iki belge de program oluşturma/yönetme ile değerlendirme
  şeması ayarlamayı bağımsız izinler olarak ele alır (`HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 7).
- **Hoca izinlerini değiştirme:** Her iki belge de bu işlemi mutlak sınır olarak işaretler ve
  hiçbir ekranda hocaya sunmaz (`HOCA_MOBIL_BILGI_MIMARISI.md` bölüm 4 sonu).

Ekran paylaşımı yalnızca **görsel ekranın aynı olduğu** anlamına gelir:

- Ekran içinde görünen eylemler her bağımsız izne göre ayrı ayrı filtrelenir.
- Kurum yöneticisinin sahip olduğu bütün kontroller hocaya topluca açılmaz.
- Ekran görünürlüğü yalnızca bir kullanım kolaylığıdır; gerçek yetkilendirme her koşulda sunucu
  tarafında, ekran görünürlüğünden bağımsız olarak doğrulanır (`URUN_VE_UYGULAMA_PLANI.md` §15,
  `YETKI_MATRISI.md` §2.2 madde 9).

Personel yönetimi izni verilen hoca ayrıca; kendi yetkisini değiştiremez, kendini yeni bir
sınıfa atayarak erişimini genişletemez, kurum/sınıf dışına atama yapamaz ve platform/kurum
yöneticisi yetkisi veremez (`YETKI_MATRISI.md` §4.1).

---

## 8. Giriş noktası ve çoklu rol bağlamı — açık nokta

`TERIMLER_SOZLUGU.md` §3.1'e göre bir kullanıcı birden fazla rol atamasına sahip olabilir (örn.
aynı kişi bir kurumda yönetici, başka bir kurumda hoca olabilir). Ancak ana planda, birden fazla
rolü olan bir kullanıcının girişten sonra **hangi bağlamda** (hangi kurum/rol) açılacağı veya
bağlamlar arasında nasıl geçiş yapacağı açıkça tanımlanmamıştır.

Bu belge bir çözüm dayatmaz; bu, ürün davranışını etkileyebilecek bir belirsizlik olduğundan
varsayım yapılmamıştır. Olası yaklaşımlar (son kullanılan bağlamı hatırlama, girişte bağlam seçim
ekranı gösterme, vb.) `UI-002 — Navigasyon ve rol bazlı menü sözleşmesini yaz` görevinde veya
ayrı bir ürün kararında netleştirilmelidir. Bu belge yalnızca ihtiyacı kaydeder.

`P-007`'nin ekran envanterini eksiksiz çıkarabilmesi için, bu belirsizliğe rağmen en azından bir
**"Bağlam Seçimi / Değiştir"** ekranının **aday ekran** olarak kaydedilmesi gerekir; bu ekranın
gösterileceği kesin koşul (yalnızca girişte mi, sürekli erişilebilir bir menü öğesi olarak mı)
ve tam davranışı `UI-002`'ye bırakılmıştır. Bu belge yalnızca adayı kaydeder, bağlayıcı hale
getirmez.

---

## 9. Ana ürün planıyla uyum kontrolü

- Platform yöneticisi ve kurum yöneticisi ekranları `URUN_VE_UYGULAMA_PLANI.md` §5.1, §5.2 ve
  §10.3'teki yönetici ana akışıyla birebir uyumludur; akışta sayılan her eylem (sınıf oluşturma,
  hoca oluşturma/atama, hoca yetkileri, öğrenci/veli yönetimi, program/plan, takvim, marka,
  rapor, işlem geçmişi/geri alma) bir ekrana karşılık gelir.
- Bölüm hiyerarşisi `AKTORLER_VE_KULLANIM_SENARYOLARI.md` §3.1–§3.2'deki `PLAT-01`–`PLAT-06` ve
  `KURUM-01`–`KURUM-12` senaryolarının tamamını kapsar; hiçbir senaryo dışarıda bırakılmamıştır.
- Bölüm 6'daki görünürlük tablosu `YETKI_MATRISI.md` §3.1, §3.2, §3.3, §3.4, §3.6, §3.7 ve §4.3
  ile satır satır yeniden çapraz kontrol edilmiştir; hiçbir satırda hocaya "Hayır — mutlak sınır"
  ile "Varsayılan kapalı — ayrı izinle açılabilir" karıştırılmamıştır. Yalnızca gerçekten mutlak
  sınır olan işlemler (sistem geneli denetim/kurumlar arası rapor, hoca izinlerini değiştirme)
  "Hayır — mutlak sınır" olarak işaretlenmiştir.
- `KURUM-11` (arşivlenmiş kayıt geri yükleme) hem öğrenci hem sınıf için ayrı ekranlarla
  karşılanmış; her ikisi de sınıf/öğrenci yönetimi izninden bağımsızdır. Öğrenci/sınıf için
  ortak mı ayrı mı izin olacağı, `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) ile hizalı biçimde
  önceki sürümde **açık soru** olarak işaretlenmiş ve `P-008`'e bırakılmıştı; `VERI_MODELI.md`
  (P-008) §4.6 bunu **ortak, tek `RESTORE_ARCHIVED` izni** olarak bağlayıcı biçimde
  kesinleştirmiştir (bölüm 4.2, 6, 7, 10).
- Anne/baba bilgisi yönetme (oluşturma/düzenleme) ve veli iletişim bilgisi görüntüleme bağımsız
  iki izin olarak ayrıştırılmıştır; öğrenci görüntüleme izninin anne/baba verisini **kapsamadığı**
  ve ikisinden hiçbiri yoksa anne/baba bölümünün öğrenci detayında hiç gösterilmediği
  netleştirilmiştir (bölüm 4.2, 4.3, 6). Bu sözleşme `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) ile
  hizalıdır.
- P-006 artık merge edilmiş olup bölüm 7'deki ortak ekran ilişkisi bağlayıcı hale getirilmiştir;
  iki belge arasında geri yükleme, anne/baba/veli görünürlüğü, program/değerlendirme ayrımı ve
  hoca izinlerini değiştirme mutlak sınırı çapraz doğrulanmıştır (bölüm 7).
- Program oluşturma/yönetme ile değerlendirme şeması ayarlama bağımsız iki izin olarak
  ayrıştırılmıştır; "aynı kategoride sayılmıştır" ifadesi otomatik izin bağlanması olarak
  yorumlanmamıştır (bölüm 4.2, 6).
- Personel yönetimi kategorisindeki dört izin (hoca hesabı, hoca–sınıf ataması, izin
  görüntüleme, cihaz oturumu iptali) birbirinden bağımsız satırlar olarak ele alınmış; Hoca
  Listesi bu dördünün OR mantığıyla açılan ortak giriş ekranı olarak ayrıca tanımlanmış ve
  listenin görünmesinin diğer eylemleri açmadığı açıkça yazılmıştır (bölüm 4.2, 7).
- İzin görüntüleme ve izin değiştirme ayrı ekran/satır olarak ele alınmış; değiştirme mutlak
  sınır olarak işaretlenmiştir (bölüm 4.2, 6, 7).
- Kendi oturumdan çıkış/kendi cihaz yönetimi ve eşitleme durumu, kurum yönetimi ekranlarından
  ayrı "Ortak yönetici ekranları" bölümünde (bölüm 5) ele alınmış; `ORTAK-01`/`ORTAK-02`
  referans verilmiş ve "başka kullanıcının cihaz oturumu iptali" izninden ayrıştırılmıştır.
- Ekranların varsayılan açık/kapalı durumu ve mutlak sınırlar `YETKI_MATRISI.md` bölüm 2.2 ve 3
  ile birebir tutarlıdır; bu belge yetki matrisiyle çelişen hiçbir varsayılan görünürlük
  tanımlamaz.
- Menü görünürlüğü ilkesi (§9.2) ve tehlikeli işlem onayı ilkesi (§3.1) bölüm 2'de doğrudan
  uygulanmıştır.
- Sekiz bölümün sabit bir sekme sayısı dayatmadığı, somut navigasyon bileşeninin `P-007`/`UI-002`
  görevlerine bırakıldığı bölüm 4.1'de açıkça yazılmıştır; bu, ana planın §3.1 sezgisel kullanım
  ilkesiyle çelişkiyi önler.
- Arşivleme/geri yükleme ekranlarının ayrı tutulması §14 (silme, arşivleme ve veri yaşam
  döngüsü) ile uyumludur.
- Terim kullanımı `TERIMLER_SOZLUGU.md` ile tutarlıdır.
- Belgede ana plana veya önceki Dalga 0 belgelerine aykırı bir tanım bulunmamaktadır.

---

## 10. Varsayımlar

- Sekiz işlevsel bölümün (Sınıflar, Personel, Öğrenciler, Program, Takvim, Raporlar, Denetim,
  Kurum Ayarları) belirlenmesi ve adlandırılması ("Kurum Ana Sayfası" dahil), ana planın
  §8.2–§8.10 modül gruplamasından yapılan bir bilgi mimarisi çıkarımıdır; ana planda birebir bu
  ad ve gruplama yazılı değildir. Bağlayıcı ekran adları ve somut navigasyon bileşeni `P-007` ve
  `UI-002`'de kesinleşecektir.
- "Kurum Ana Sayfası" ve "Platform Ana Sayfası" adları bu belgeye özeldir; ana planda bir
  yönetici dashboard ekranı adı geçmez, yalnızca yönetici eylemleri sayılır (§10.3). Bu belge bu
  eylemleri barındıracak bir giriş ekranı olduğunu varsaymıştır.
- Hocaya devredilebilir yönetim ekranlarının, kurum yöneticisininkiyle **aynı görsel ekran**
  olduğu (ayrı bir sadeleştirilmiş hoca versiyonu değil) varsayımı, `YETKI_MATRISI.md` §4.1'in
  "aynı dar eylemleri kapsar" ifadesinden çıkarılmıştır; ekran içindeki eylemler her bağımsız
  izne göre ayrı filtrelenir (bölüm 7). Kesin ekran/alan farkı `P-007`'de netleşebilir.
- **Karar — arşivleme/geri yükleme izninin öğrenci ve sınıf için ortak olması (kapatılan açık
  soru):** `YETKI_MATRISI.md` §3.4 tek bir "Arşivlenmiş öğrenci/sınıf kaydını geri yükleme"
  iznini hem öğrenci hem sınıf geri yükleme için ortak tanımlar. Bu belgede öğrenci ve sınıf
  geri yükleme ekranları (kullanıcı deneyimi netliği için) ayrı düğümler olarak gösterilir;
  bunların tek bir ortak izne mi yoksa varlık başına ayrı iki izne mi bağlı olacağı önceki
  sürümde `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) bölüm 14 ile hizalı, `P-008`'e bırakılan açık
  bir soruydu. `VERI_MODELI.md` (P-008) §4.6 bu soruyu artık bağlayıcı biçimde kapatmıştır:
  `RESTORE_ARCHIVED` öğrenci ve sınıf için **tek, ortak** bir izin kodudur; varlık başına ayrı
  izin yoktur. Bu geri yükleme yetkisi sınıf/öğrenci yönetimi (oluşturma/düzenleme/arşivleme)
  izninden bağımsız kalmaya devam eder.

---

## 11. Bilinen sınırlamalar

- Bu belge ekranların görsel yerleşimini, bileşen seçimini, tam alan listesini veya somut
  navigasyon bileşenini (sekme/menü) içermez; yalnızca hangi ekranın var olduğunu, nereye bağlı
  olduğunu ve kim tarafından görülebileceğini gösterir.
- Bölüm 8'de not edilen çoklu rol bağlamı geçişi, ana planda açık dayanağı olmayan bir noktadır
  ve bu belgede çözülmemiştir; yalnızca aday bir "Bağlam Seçimi/Değiştir" ekranı kaydedilmiştir.
- Eski sistem (Excel/HTML/Apps Script) bu repoda bulunmadığından, bu bilgi mimarisinin eski
  sistemdeki ekran/menü yapısıyla karşılaştırması yapılmamıştır; bu belge yalnızca onaylı ana
  plana ve önceki Dalga 0 belgelerine dayanır.
- Kuruma özel tanımlanmış alanların (§9.3) "Özel Öğrenci Alanları" ekranında nasıl
  yapılandırılacağı (alan ekleme sihirbazı vb.) ayrıntısı bu belgenin kapsamında değildir;
  yalnızca ekranın bölümdeki yeri belirtilmiştir.
- Geri yükleme izninin öğrenci/sınıf için ortak mı ayrı mı olacağı (bölüm 10), `VERI_MODELI.md`
  (P-008) §4.6'da ortak tek `RESTORE_ARCHIVED` izni olarak kesinleşmiştir; bu artık bu belgede
  bilinen bir sınırlama değildir.

---

## 12. Kapsam dışı bırakılanlar

- Hoca mobil bilgi mimarisi (`P-006` kapsamı).
- İlk sürüm ekranlarının kesin envanteri ve alan listesi (`P-007` kapsamı).
- Mobil tasarım tokenleri, görsel tasarım ve wireframe (`UI-001` ve Faz 0 wireframe çalışması
  kapsamı).
- Somut navigasyon bileşeni (sekme sayısı, menü gruplaması) ve rol bazlı menü sözleşmesinin
  teknik/API detayı (`UI-002` kapsamı).
- Çekirdek veri modeli, API sözleşmesi ve senkronizasyon detayları (`P-008`–`P-010` kapsamı).
- Öğrenci ve veli (anne/baba/vasi) bilgi mimarisi (sonraki faz, Dalga 8 `PORTAL-*`).
- Çoklu rol/bağlam geçişinin kesin ürün davranışı (bölüm 8'de açık nokta olarak bırakılmıştır;
  yalnızca aday ekran kaydedilmiştir).
