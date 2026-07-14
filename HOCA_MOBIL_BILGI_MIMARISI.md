# Hoca Mobil Bilgi Mimarisi

| Alan | Değer |
|---|---|
| Görev | P-006 — Hoca mobil bilgi mimarisini çiz |
| Belge sürümü | 1.3 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Terim kaynağı | `TERIMLER_SOZLUGU.md` |
| Aktör/senaryo kaynağı | `AKTORLER_VE_KULLANIM_SENARYOLARI.md` |
| Yetki kaynağı | `YETKI_MATRISI.md` |
| Son güncelleme | 14 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, hoca rolünün mobil uygulamada dolaştığı **bilgi mimarisini** — gezinme hiyerarşisini,
üst/alt ekran gruplarını, her düğümün hangi yetkiye bağlı olarak göründüğünü ve düğümün **kurum
bağlamında mı yoksa sınıf bağlamında mı** değerlendirildiğini — tanımlar. Kaynağı
`URUN_VE_UYGULAMA_PLANI.md` §10.2 (hoca ana akışı), §8 (ana işlevsel modüller), §9.2 (menü ve
modül görünürlüğü), `AKTORLER_VE_KULLANIM_SENARYOLARI.md` bölüm 3.3 (`HOCA-01`–`HOCA-09`) ve
`YETKI_MATRISI.md` bölüm 3 ve 4'tür (eylem × rol matrisi ve devredilebilirlik notları).

Bu belge:

- Ekran envanteri değildir; tek tek ekranların ayrıntılı listesi ve durumları `P-007` kapsamıdır.
- Wireframe veya görsel tasarım değildir; düşük ayrıntılı wireframe ana plan §21 Faz 0 çıktısı
  olarak ayrıca üretilecektir.
- Mobil navigasyon kabuğunun kesin sekme/menü tasarımı değildir; bkz. bölüm 11.
- Veri modeli veya API sözleşmesi değildir (`P-008`, `P-009` kapsamı).
- Yalnızca **hoca** rolünün bilgi mimarisini kapsar. Yönetici (platform/kurum) bilgi mimarisi
  `P-005` kapsamındadır; bölüm 12'de yalnızca bağlayıcı olmayan bir çapraz referans olarak
  anılır. Öğrenci/veli bilgi mimarisi sonraki faz (Dalga 8) kapsamındadır.
- Bir kullanıcının aynı anda hem hoca hem yönetici rolüne sahip olabileceği durumdaki birleşik
  gezinme deneyimini tanımlamaz; bu belge yalnızca hoca rolünün kendi bağlamındaki bilgi
  mimarisini ele alır.

**1.2 revizyon notu:** `P-008 — Çekirdek veri modeli taslağı` (`VERI_MODELI.md`) tamamlanmış ve
bu belgenin bölüm 14'üne bırakılan açık soruyu kesinleştirmiştir: **arşivlenmiş öğrenci/sınıf
kaydını geri yükleme izni, varlık başına ayrı değil, tek ve ortak bir `RESTORE_ARCHIVED` izin
kodudur** (bkz. `VERI_MODELI.md` §4.8). Bu sürüm, bölüm 4, 5 ve 14'teki ilgili "açık soru"
işaretlerini bu bağlayıcı kararla uyumlu biçimde günceller; ekran hiyerarşisi veya izin
bağımsızlığı ilkelerinde başka bir değişiklik yapılmamıştır.

**1.3 revizyon notu:** `PLAN-004`, rol ile sınıf atamasını terminolojik olarak ayırmış ve
özel öğrenci alanı tanımı yönetiminin V1'de hoca navigasyonuna açılmayan yönetici işlemi
olduğunu bağlayıcı yetki kaynağıyla netleştirmiştir.

---

## 2. Bilgi mimarisi ilkeleri

Aşağıdaki ilkeler doğrudan ana plandan ve yetki matrisinden alınmıştır; bu belge yeni bir ilke
icat etmez:

1. **Sezgisel kullanım (§3.1):** Günlük ve sık kullanılan işlemler (yoklama, ilerleme) gezinme
   hiyerarşisinin en üst seviyesine yakın yerleştirilir.
2. **Rol ve modül bazlı görünürlük (§9.2):** Menü görünürlüğü hocanın rolüne ve kurumda etkin
   modüllere göre belirlenir; yönetici kullanılmayan modülleri kapatabilir.
3. **Yetkisiz düğüm yalnızca gizlenmez, doğrudan erişilemez de (§9.2):** Bilgi mimarisindeki her
   koşullu düğüm, backend yetki kontrolüyle bire bir eşleşmelidir; arayüzde gizleme tek başına
   yetkilendirme sayılmaz.
4. **Varsayılan politika "izin verilmediyse erişim yok"tur (`YETKI_MATRISI.md` §2.2 madde 1):**
   Bu belgedeki her koşullu düğüm bu politikaya göre varsayılan **kapalı** başlar.
5. **Sınıf ataması ile işlem izni ayrı kavramlardır (`YETKI_MATRISI.md` §2.2 madde 10):**
   Operasyonel bir düğümün görünmesi için hem ilgili sınıfa atanmış olmak hem de ilgili işlem
   iznine sahip olmak birlikte gerekir.
6. **Kurum kapsamlı yönetim izni, operasyonel sınıf verisine toplu erişim sağlamaz
   (`YETKI_MATRISI.md` §2.2 madde 11, §4.4):** Personel yönetimi veya kurum ayarı izinleri
   hocaya yalnızca sınırlı metaveriye erişim sağlayan ayrı düğümler açar; bu düğümler hocanın
   atanmadığı sınıfların öğrenci/yoklama/ilerleme verisini göstermez.
7. **Kurum kapsamlı yönetim işlemleri seçili sınıf gerektirmez:** Bir işlem sınıfın operasyonel
   verisine değil kurumun kendisine (marka, modül, dönem, personel) aitse, o işlemin görünürlüğü
   hiçbir sınıfın seçili olmasına bağlı değildir (bkz. bölüm 3, bölüm 8).
8. **Aktif kurum bağlamı her zaman korunur:** Uygulama kabuğu tek bir aktif kurum bağlamında
   çalışır; bütün düğümler (sınıf bağlamlı olsun ya da olmasın) bu aktif kurum kapsamı içinde
   değerlendirilir (bkz. bölüm 9, `YETKI_MATRISI.md` §2.2 madde 2).
9. **İzin kategorileri paket değildir (bkz. bölüm 6):** Bu belgede geçen "kategori 1"–"kategori
   5" ibareleri yalnızca dokümantasyon gruplamasıdır; bir kategorideki işlemler birlikte
   açılmaz. Her işlem izni ayrı ayrı verilir ve geri alınır.

---

## 3. Üst düzey gezinme yapısı

Önceki sürümde "Sınıf ana ekranı → Sınıf ve Kurum Yönetimi" yapısı kullanılmıştı. Bu yapı,
hiçbir sınıfa atanmamış fakat kurum kapsamlı yönetim/personel izni bulunan bir hocanın yönetim
ekranlarına ulaşmasını engelliyordu (yönetim düğümü sınıf seçimine bağımlıydı). Bu sürümde
**kurum bağlamı** ile **sınıf bağlamı** ayrıştırılmıştır:

```text
Giriş / Oturum                                          (HOCA-01, ORTAK-01)
└─ Hoca uygulama kabuğu (aktif kurum bağlamı)
   ├─ Sınıf İşlemleri
   │  └─ Sınıf seçici                                (HOCA-02: yalnızca atanmış sınıflar)
   │     └─ Seçilen sınıf
   │        ├─ Bugünkü Yoklama
   │        ├─ Öğrenciler
   │        ├─ Program ve Ezberler
   │        └─ İlerleme
   ├─ Yönetim                    [yalnızca ilgili bağımsız izinlerden en az biri varsa görünür]
   │  ├─ Sınıf yönetimi
   │  ├─ Dönem/takvim
   │  ├─ Kurum ayarları
   │  └─ Personel işlemleri
   ├─ Rapor ve Denetim                                [ilgili bağımsız izinlerden biri varsa]
   └─ Profil / Oturum / Eşitleme
```

Kurallar (bkz. bölüm 8 ayrıntılı değerlendirme kapsamı için):

- **Sınıf İşlemleri** altındaki düğümler seçili sınıf bağlamı gerektirir; bir sınıf seçilmeden
  operasyonel veriye (yoklama, öğrenci, program, ilerleme) erişilmez.
- **Yönetim** ve **Rapor ve Denetim** altındaki düğümler kurum kapsamlıdır; seçili bir sınıf
  gerektirmez ve aktif kurum bağlamında değerlendirilir.
- Hiç sınıfa atanmamış fakat kurum kapsamlı yönetim izni (personel işlemleri, kurum ayarları,
  dönem/takvim, sınıf yönetimi işlemlerinden herhangi biri) bulunan bir hoca, **Yönetim**
  alanına erişebilir; bu erişim Sınıf İşlemleri'nin durumundan bağımsızdır (bkz. bölüm 10).
- Kurum kapsamlı yönetim izni, operasyonel sınıf verisine (öğrenci/yoklama/ilerleme) erişim
  sağlamaz (`YETKI_MATRISI.md` §2.2 madde 3/11, §4.4).
- **Profil / Oturum / Eşitleme** kullanıcı bağlamındadır; ne sınıf ne de kurum kapsamlı yönetim
  iznine bağlıdır (ORTAK-01, ORTAK-02).

---

## 4. Ekran ağacı (bilgi mimarisi hiyerarşisi)

```text
Hoca uygulama kabuğu
│
├── Sınıf İşlemleri
│   └── Sınıf seçici                                       [HOCA-02] yalnızca atanmış sınıflar
│       └── Seçilen sınıf
│           ├── Bugünkü Yoklama                             [HOCA-03] varsayılan açık
│           │   ├── Öğrenci yoklama listesi (Geldi/Gelmedi/kuruma özel durum)
│           │   ├── Toplu "Hepsi Geldi"                     [HOCA-04] varsayılan açık
│           │   └── Geçmiş tarihli yoklama düzeltme          [HOCA-05] bağımsız izinle açılır
│           │
│           ├── Öğrenciler                                  [HOCA-07] varsayılan açık (temel bilgi)
│           │   ├── Öğrenci listesi (arama/filtre)
│           │   ├── Öğrenci detay → temel bilgiler (ad, soyad, telefon vb.)
│           │   ├── Öğrenci oluştur                         bağımsız izinle açılır (öğrenci yönetimi)
│           │   ├── Öğrenci düzenle                         bağımsız izinle açılır (öğrenci yönetimi — Öğrenci oluştur ile aynı izin)
│           │   ├── Öğrenci arşivle                         bağımsız izinle açılır (öğrenci yönetimi — Öğrenci oluştur ile aynı izin)
│           │   ├── Arşivlenmiş öğrenciler (liste)          bağımsız izinle açılır (`RESTORE_ARCHIVED` izni — bkz. bölüm 14)
│           │   ├── Öğrenciyi geri yükle                    bağımsız izinle açılır (`RESTORE_ARCHIVED` izni — liste ile aynı izin)
│           │   ├── Anne/baba bilgisi oluştur/düzenle       bağımsız izinle açılır (anne/baba bilgisi yönetme)
│           │   └── Veli iletişim bilgisi görüntüleme       bağımsız izinle açılır (öğrenci yönetiminden bağımsız ayrı izin, karar 9)
│           │
│           ├── Program ve Ezberler                         [HOCA-06] varsayılan açık (görüntüleme)
│           │   ├── Aktif programlar listesi (görüntüleme) [HOCA-06] varsayılan açık
│           │   ├── Program oluşturma/yönetme               bağımsız izinle açılır (program yönetimi)
│           │   └── Değerlendirme şeması ayarlama            bağımsız izinle açılır (değerlendirme şeması izni — program yönetiminden ayrı)
│           │
│           └── İlerleme                                    [HOCA-06] varsayılan açık
│               ├── Program/plan bazlı ilerleme girişi (tamamlandı/puan/not/tekrar) [HOCA-06]
│               └── Diğer hocanın normal notunu görüntüleme [HOCA-08] varsayılan açık
│
├── Yönetim                            [aşağıdaki bağımsız izinlerden en az biri varsa görünür]
│   ├── Sınıf yönetimi
│   │   ├── Sınıf oluştur                                   bağımsız izinle açılır (sınıf yönetimi izni)
│   │   ├── Sınıf düzenle                                   bağımsız izinle açılır (sınıf yönetimi izni — oluştur ile aynı izin)
│   │   ├── Sınıf arşivle                                   bağımsız izinle açılır (sınıf yönetimi izni — oluştur ile aynı izin)
│   │   ├── Arşivlenmiş sınıflar (liste)                    bağımsız izinle açılır (`RESTORE_ARCHIVED` izni — bkz. bölüm 14)
│   │   └── Sınıfı geri yükle                               bağımsız izinle açılır (`RESTORE_ARCHIVED` izni — liste ile aynı izin)
│   │
│   ├── Dönem/takvim
│   │   └── Eğitim dönemi ve çalışma günü/tatil tanımlama    bağımsız izinle açılır (dönem/takvim izni)
│   │
│   ├── Kurum ayarları
│   │   ├── Kurum adı/logo/renk (marka) ayarı               bağımsız izinle açılır (marka izni)
│   │   ├── Kurumda etkin modülleri belirleme                bağımsız izinle açılır (modül yönetimi izni)
│   │   └── Kuruma özel yoklama durumu tanımlama             bağımsız izinle açılır (yoklama durumu tanımlama izni)
│   │
│   └── Personel işlemleri
│       ├── Hoca hesabı oluşturma/kapatma                    bağımsız izin
│       ├── Hoca–sınıf ataması                               bağımsız izin (yalnızca aynı kurum içi, kendi ataması hariç)
│       ├── Hoca izinlerini görüntüleme (salt okunur)         bağımsız izin (izin değiştirme bağlantısı yok)
│       └── Kullanıcının cihaz oturumlarını iptal etme        bağımsız izin
│
├── Rapor ve Denetim                    [aşağıdaki bağımsız izinlerden en az biri varsa görünür]
│   ├── Rapor dışa aktarma (Excel)                           bağımsız izin
│   ├── İşlem geçmişi (denetim kaydı) görüntüleme            bağımsız izin
│   └── Desteklenen işlemi geri alma                         bağımsız izin (işlem geçmişi izninden bağımsız; bkz. bölüm 7)
│
└── Profil / Oturum / Eşitleme
    ├── Oturum/cihaz bilgisi ve çıkış yapma                  [ORTAK-01] her hoca için açık
    └── Eşitleme durumu göstergesi                           [ORTAK-02] her hoca için açık
```

"Hoca izinlerini değiştirme (verme/geri alma)" bu ağaçta **hiçbir düğüm olarak yer almaz**;
`YETKI_MATRISI.md` §2.2 madde 6 ve §6.2 karar 6 gereği V1'de hocaya hiçbir izinle
devredilemeyen mutlak bir sınırdır. "Hoca izinlerini görüntüleme" düğümü salt okunurdur ve bu
düğümden izin değiştirme aksiyonuna geçiş yoktur. Aynı şekilde, **kendi sınıf atamasını
değiştirme veya kendisini yeni bir sınıfa atama** hiçbir ekranda sunulmaz (`YETKI_MATRISI.md`
§4.1).

---

## 5. Düğüm bazında görünürlük ve yetki eşlemesi

| Düğüm | Hoca için varsayılan | Bağlı izin | Yetki matrisi kaynağı | Senaryo |
|---|---|---|---|---|
| Bugünkü Yoklama → yoklama listesi | Açık (yalnızca atanmış sınıf) | — | `YETKI_MATRISI.md` §3.5 | HOCA-03 |
| Bugünkü Yoklama → Hepsi Geldi | Açık (yalnızca atanmış sınıf) | — | `YETKI_MATRISI.md` §3.5 | HOCA-04 |
| Bugünkü Yoklama → geçmiş tarih düzeltme | Kapalı | Geçmiş tarihli yoklama düzeltme izni | `YETKI_MATRISI.md` §3.5 | HOCA-05 |
| Öğrenciler → liste/detay temel bilgi | Açık (yalnızca atanmış sınıf) | — | `YETKI_MATRISI.md` §3.4 | HOCA-07 |
| Öğrenciler → oluştur/düzenle/arşivle (3 düğüm) | Kapalı | Öğrenci yönetimi izni (üçü de aynı izin) | `YETKI_MATRISI.md` §3.4 | KURUM-06 (devredilmişse) |
| Öğrenciler → arşivlenmiş liste / geri yükle (2 düğüm) | Kapalı | `RESTORE_ARCHIVED` izni — öğrenci/sınıf için ortak tek izin, `P-008`'de kesinleşti (bkz. bölüm 14) | `YETKI_MATRISI.md` §3.4 | KURUM-11 (devredilmişse) |
| Öğrenciler → anne/baba bilgisi oluştur/düzenle | Kapalı | Anne/baba bilgisi yönetme izni | `YETKI_MATRISI.md` §3.4 | KURUM-06 (devredilmişse) |
| Öğrenciler → veli iletişim bilgisi görüntüleme | Kapalı | Veli iletişim bilgisi görüntüleme izni (öğrenci yönetiminden bağımsız, karar 9) | `YETKI_MATRISI.md` §3.4, §6.2 karar 9 | HOCA-07 |
| Program ve Ezberler → görüntüleme | Açık (yalnızca atanmış sınıf) | — | `YETKI_MATRISI.md` §3.6 | HOCA-06 |
| Program ve Ezberler → program oluşturma/yönetme | Kapalı | Program yönetimi izni | `YETKI_MATRISI.md` §3.6 | KURUM-07 (devredilmişse) |
| Program ve Ezberler → değerlendirme şeması | Kapalı | Değerlendirme şeması ayarlama izni (program yönetiminden bağımsız) | `YETKI_MATRISI.md` §3.6 | KURUM-08 (devredilmişse) |
| İlerleme → ilerleme girişi | Açık (yalnızca atanmış sınıf) | — | `YETKI_MATRISI.md` §3.6 | HOCA-06 |
| İlerleme → diğer hoca notu | Açık (yalnızca atanmış sınıf) | — | `YETKI_MATRISI.md` §3.6 | HOCA-08 |
| Yönetim (üst düğüm) | Kapalı | Aşağıdaki dört alt bölümden en az bir bağımsız izin açıksa görünür | `YETKI_MATRISI.md` §4.3 | HOCA-09 |
| — Sınıf yönetimi → oluştur/düzenle/arşivle (3 düğüm) | Kapalı | Sınıf yönetimi izni (üçü de aynı izin) | `YETKI_MATRISI.md` §3.2 | KURUM-03 (devredilmişse) |
| — Sınıf yönetimi → arşivlenmiş liste / geri yükle (2 düğüm) | Kapalı | `RESTORE_ARCHIVED` izni — öğrenci/sınıf için ortak tek izin, `P-008`'de kesinleşti (bkz. bölüm 14) | `YETKI_MATRISI.md` §3.4 | KURUM-11 (devredilmişse) |
| — Dönem/takvim tanımlama | Kapalı | Dönem/takvim izni | `YETKI_MATRISI.md` §3.1 | KURUM-02 (devredilmişse) |
| — Kurum ayarları → marka | Kapalı | Marka ayarı izni | `YETKI_MATRISI.md` §3.1, §6.2 karar 1 | KURUM-01 (devredilmişse) |
| — Kurum ayarları → etkin modül | Kapalı | Modül yönetimi izni | `YETKI_MATRISI.md` §3.1, §6.2 karar 2 | — |
| — Kurum ayarları → yoklama durumu tanımlama | Kapalı | Yoklama durumu tanımlama izni | `YETKI_MATRISI.md` §3.5, §6.2 karar 8 | KURUM-07 (devredilmişse) |
| — Personel işlemleri → hoca hesabı oluşturma/kapatma | Kapalı | Hoca hesabı izni (bağımsız) | `YETKI_MATRISI.md` §3.3, §6.2 karar 3 | KURUM-04 (devredilmişse) |
| — Personel işlemleri → hoca–sınıf ataması | Kapalı | Hoca–sınıf ataması izni (bağımsız) | `YETKI_MATRISI.md` §3.2/§3.3, §6.2 karar 4 | KURUM-05 (devredilmişse) |
| — Personel işlemleri → hoca izinlerini görüntüleme | Kapalı | İzin görüntüleme izni (bağımsız, salt okunur) | `YETKI_MATRISI.md` §3.3, §6.2 karar 5 | — |
| — Personel işlemleri → cihaz oturumu iptali | Kapalı | Cihaz oturumu iptali izni (bağımsız) | `YETKI_MATRISI.md` §3.3, §6.2 karar 7 | KURUM-12 (devredilmişse) |
| Rapor ve Denetim (üst düğüm) | Kapalı | Aşağıdaki üç bağımsız izinden en az biri açıksa görünür | `YETKI_MATRISI.md` §4.3 | HOCA-09 |
| — Rapor dışa aktarma | Kapalı | Rapor dışa aktarma izni (bağımsız) | `YETKI_MATRISI.md` §3.7 | KURUM-09 (devredilmişse) |
| — İşlem geçmişi görüntüleme | Kapalı | İşlem geçmişi görüntüleme izni (bağımsız) | `YETKI_MATRISI.md` §3.7 | KURUM-10 (devredilmişse) |
| — Desteklenen işlemi geri alma | Kapalı | Geri alma izni (işlem geçmişi izninden bağımsız; bkz. bölüm 7) | `YETKI_MATRISI.md` §3.7, §4.2 | KURUM-10 (devredilmişse) |
| Profil/Oturum → oturum/çıkış | Açık | — (kendi hesabı) | `YETKI_MATRISI.md` §3.3 | ORTAK-01 |
| Profil/Oturum → eşitleme durumu | Açık | — | `URUN_VE_UYGULAMA_PLANI.md` §13 | ORTAK-02 |

---

## 6. İzin kategorilerinin niteliği

Bu belgede ve `YETKI_MATRISI.md` bölüm 4.3'te geçen "kategori 1"–"kategori 5" ibareleri
**yalnızca dokümantasyon gruplamasıdır**; gerçek bir izin paketi değildir. Aşağıdaki kural bütün
kategoriler için aynı şekilde geçerlidir:

- Kategori 1 (sınıf/öğrenci/veli yönetimi), kategori 2 (program yönetimi), kategori 3 (yoklama
  düzeltme ve raporlama), kategori 4 (kurum kapsamlı ayar yönetimi) ve kategori 5 (personel
  yönetimi) — bu beşi de yönetim kolaylığı için yapılmış gruplamalardır.
- Bir kategorideki işlemler **topluca açılmaz**. Örneğin kategori 5'e (personel yönetimi) ait
  dört işlem (hoca hesabı oluşturma/kapatma, hoca–sınıf ataması, hoca izinlerini görüntüleme,
  cihaz oturumu iptali) birbirinden tamamen bağımsız izinlerdir; birine sahip olmak diğerini
  otomatik açmaz.
- Her işlem izni **ayrı ayrı** verilir ve **ayrı ayrı** geri alınır.
- Bir üst düğüm (Yönetim, Rapor ve Denetim, Sınıf yönetimi, Kurum ayarları, Personel işlemleri),
  altındaki bağımsız izinlerden **en az biri** açıksa görünür.
- Üst düğümün görünmesi, altındaki bütün alt eylemlerin açıldığı anlamına **gelmez**. Örneğin
  "Personel işlemleri" üst düğümü görünüyor olsa bile, hoca yalnızca sahip olduğu spesifik alt
  izne karşılık gelen eylemi (örn. yalnızca "hoca izinlerini görüntüleme") görür; diğer üç alt
  eylem (hesap oluşturma, sınıf ataması, cihaz iptali) ayrı ayrı izin verilmediği sürece
  görünmez.

---

## 7. İzinle genişleyen düğümlerin özel kuralları

Bölüm 4 ve 5'teki koşullu düğümler, `YETKI_MATRISI.md` bölüm 4.1'deki güvenlik sınırlarına
tabidir:

- **Personel işlemleri düğümleri kendine dönük aksiyon üretmez:** "Hoca–sınıf ataması" düğümü,
  izin sahibi hocanın **kendi** sınıf atamasını değiştirme aksiyonunu içermez; yalnızca başka
  kullanıcılar üzerinde ve aynı kurum içinde çalışır. Kendisini yeni bir sınıfa atama işlemi
  hiçbir ekranda sunulmaz (`YETKI_MATRISI.md` §4.1).
- **"Hoca izinlerini görüntüleme" düğümünden izin değiştirmeye giden bir bağlantı yoktur:**
  Görüntüleme salt okunurdur (`YETKI_MATRISI.md` §2.2 madde 6, §6.2 karar 5/6).
- **Kurum kapsamlı yönetim düğümleri operasyonel veriye toplu erişim açmaz:** Yönetim
  altındaki düğümler yalnızca işlemin gerektirdiği sınırlı kurum/sınıf metaverisini gösterir;
  kurumun bütün sınıflarındaki öğrenci/yoklama/ilerleme verisi bu düğümler üzerinden
  **görüntülenemez** (`YETKI_MATRISI.md` §2.2 madde 3/11, §4.4). Operasyonel veriye erişim her
  zaman Sınıf İşlemleri altındaki "Bugünkü Yoklama", "Öğrenciler", "Program ve Ezberler" ve
  "İlerleme" düğümleri üzerinden, yalnızca atanmış sınıf(lar) kapsamında sağlanır.
- **Öğrenci yönetimi ile veli iletişim bilgisi görüntüleme bağımsız izinlerdir:** Öğrenci
  görüntüleme/yönetme iznine sahip olmak, veli iletişim bilgisini otomatik göstermez
  (`YETKI_MATRISI.md` §3.4, §6.2 karar 9).
- **Anne/baba bilgisi yönetimi ile veli iletişim bilgisi görüntüleme ayrı düğümlerdir:** Biri
  yazma/düzenleme (anne/baba bilgisi yönetme), diğeri yalnızca görüntüleme iznidir; ikisi
  bağımsız olarak verilir.
- **Program görüntüleme, program yönetme ve değerlendirme şeması ayarlama üç ayrı izindir:**
  Program görüntüleme varsayılan açıktır; program oluşturma/yönetme ve değerlendirme şeması
  ayarlama ayrı ayrı devredilebilir izinlerdir. Aynı dokümantasyon kategorisinde (kategori 2)
  sayılmaları, aynı izinle birlikte açıldıkları anlamına gelmez.
- **Rapor ve Denetim altındaki üç izin tamamen bağımsızdır:** Rapor dışa aktarma, işlem
  geçmişi görüntüleme ve geri alma ayrı ayrı verilir. Özellikle **geri alma** için: işlem
  geçmişi görüntüleme izni otomatik olarak geri alma izni sağlamaz; geri alma hem hedef işlemin
  kapsamına erişimi hem de ayrı bir geri alma iznini gerektirir. Geri alma davranışı ve
  desteklenen işlem türleri `DENETIM_VE_GERI_ALMA_ILKELERI.md` §4–§7'de tanımlıdır
  (`YETKI_MATRISI.md` §3.7, §4.2).

---

## 8. Sınıf bağlamı ile kurum bağlamının değerlendirme kapsamı

Bölüm 3'teki "Sınıf İşlemleri" / "Yönetim" ayrımının hangi düğümde nasıl değerlendirildiği:

- **Yoklama, öğrenci, program ve ilerleme düğümleri** (Sınıf İşlemleri altındakiler) seçili
  sınıf bağlamında değerlendirilir; görünürlükleri hem "bu sınıfa atanmış olma" hem de "ilgili
  işlem izni" koşuluna birlikte bağlıdır.
- **Kurum kapsamlı marka, modül, personel, dönem/takvim ve yoklama durumu ayarları** (Yönetim
  altındakiler) aktif kurum bağlamında değerlendirilir; hiçbir sınıfın seçili olmasını
  gerektirmez ve hocanın atanmış olduğu sınıf sayısından bağımsız olarak, ilgili bağımsız izne
  sahip olduğu sürece görünür.
- **Profil/oturum işlemleri** kullanıcı bağlamındadır; ne sınıf ne kurum kapsamlı yönetim
  iznine bağlıdır.
- **Rapor ve denetim kapsamı** bağlayıcıdır: `REPORT_EXPORT` izni verilen hoca yalnız etkin
  sınıf atamalarının operasyonel verisini dışa aktarır (`EXCEL_RAPOR_VERI_SOZLESMESI.md` §3);
  `AUDIT_LOG_VIEW` izni ise yalnız güncel ataması bulunan sınıf kapsamlı operasyonel olayları
  gösterir (`DENETIM_VE_GERI_ALMA_ILKELERI.md` §3.3). Bu iki izin birbirini içermez.

---

## 9. Sınıf seçici, çoklu sınıf ve tek sınıflı hoca durumu

- Bir hoca birden fazla sınıfa atanabildiğinden (`URUN_VE_UYGULAMA_PLANI.md` §5.3, §6.3),
  Sınıf İşlemleri altındaki bütün düğümlerin operasyonel görünürlüğü seçili sınıf bağlamında
  değerlendirilir.
- İzin atamaları doğrudan kurum üyeliğinin `TEACHER` rolüne bağlıdır (`VERI_MODELI.md` §4.9);
  operasyonel veri erişimi ise her durumda ilgili sınıf atamasını ve işlem iznini gerektirir.
  Kurum kapsamlı yönetim izni operasyonel veri sınırını kaldırmaz (`VERI_MODELI.md` §16).
- Hoca tek bir sınıfa atanmışsa sınıf seçicinin atlanması, `EKRAN_ENVANTERI.md` §7'de tanımlı
  kullanılabilirlik optimizasyonudur; bu durumda dahi:
  - Aktif sınıf bağlamı arayüzde görünür kalmalıdır (hangi sınıfın seçili olduğu belirsiz
    bırakılmaz).
  - Kullanıcı sınıf değiştirme/sınıf seçici ekranına her zaman ulaşabilmelidir (örn. gelecekte
    ikinci bir sınıfa atandığında).
  - Tek sınıf optimizasyonu, Yönetim alanına erişimi hiçbir şekilde etkilemez veya engellemez;
    Yönetim bölüm 3 ve 8'de tanımlandığı gibi sınıf seçiminden bağımsızdır.

---

## 10. Hiç sınıfı veya hiç yönetim izni olmayan hoca durumları

- **Hiç sınıfa atanmamış hoca:** Sınıf İşlemleri boş durum gösterir ("Henüz atanmış bir sınıfın
  yok"). Bu durum hocanın kurum kapsamlı yönetim erişimini etkilemez: ilgili bağımsız izin(ler)
  varsa Yönetim ve/veya Rapor ve Denetim alanı normal şekilde kullanılmaya devam eder (bkz.
  bölüm 3).
- **Hiç sınıfı ve hiç kurum kapsamlı yönetim izni de olmayan hoca:** Sınıf İşlemleri boş durum
  gösterir; Yönetim ve Rapor ve Denetim üst düğümleri hiç görünmez (bölüm 6 kuralı gereği).
  Kullanıcıya, kurum yöneticisiyle iletişime geçmesini öneren açıklayıcı bir boş durum
  gösterilir; bu bir teknik hata olarak sunulmaz, beklenen ve tanımlı bir durumdur.
- `P-007`, bu durumların ekran varlığını ve yükleniyor/boş/hata/yetkisiz durumlarını envantere
  bağlar. Kesin ekran metni, görseli ve etkileşimi `UI-002` veya uygun sonraki mobil tasarım
  görevinde belirlenir; bu belge yalnızca bilgi mimarisi düzeyinde bu iki durumun var olduğunu
  ve teknik hatayla karıştırılmaması gerektiğini not eder.

---

## 11. Mobil navigasyon terminolojisi hakkında not

Bölüm 3 ve 4'teki "Sınıf İşlemleri", "Yönetim", "Rapor ve Denetim" ve "Profil/Oturum/Eşitleme"
gruplamaları **işlevsel bilgi mimarisi bölümleridir**; bunlar:

- Aynı anda mobil alt navigasyonda gösterilecek sekme sayısını veya sırasını **tanımlamaz**.
- Günlük ve sık kullanılan işlemler (Bugünkü Yoklama, Öğrenciler, Program ve Ezberler,
  İlerleme) ana navigasyonda önceliklendirilebilir.
- Seyrek kullanılan Yönetim, Rapor ve Denetim alanları kontrollü bir "Daha Fazla" veya
  "Yönetim" girişi altında toplanabilir.
- Ekran envanteri ve önerilen navigasyon gruplaması `EKRAN_ENVANTERI.md` §14'te tanımlıdır;
  kesin sekme/menü bileşeni `UI-002` sözleşmesinin açık kararıdır.

---

## 12. P-005 ile ortak ekran ilişkisi

- Bu belgedeki Yönetim altındaki düğümler (sınıf yönetimi, dönem/takvim, kurum ayarları,
  personel işlemleri) kavramsal olarak kurum yöneticisinin de kullandığı yönetim işlemleridir.
  Bu ekranlar hoca için yeniden tanımlanmak yerine, `P-005 — Yönetici mobil bilgi mimarisi`
  görevinde tanımlanan ortak ekranlara bağlanır; her iki belge de aynı işlemi iki farklı
  arayüz olarak icat etmez.
- Hoca bu ortak ekranlarda **yalnızca kendisine ayrı ayrı verilmiş bağımsız izinlere karşılık
  gelen eylemleri** görür; kurum yöneticisinin sahip olduğu bütün kontroller hocaya topluca
  açılmaz (bölüm 6, `YETKI_MATRISI.md` §2.2 madde 6/7).
- Tarihsel not: Bu belgenin önceki sürümünde `P-005` henüz merge edilmemişti. Ortak ekran
  ilişkisi artık `YONETICI_BILGI_MIMARISI.md` §7 ve `EKRAN_ENVANTERI.md` §15 ile doğrulanmış
  girdidir; somut mobil bileşen uygulaması ilgili `UI-*` görevlerine aittir.

---

## 13. Ana ürün planıyla uyum kontrolü

- Sınıf İşlemleri altındaki gezinme `URUN_VE_UYGULAMA_PLANI.md` §10.2 (hoca ana akışı) ile
  uyumludur: bugünkü yoklama, öğrenciler, program ve ezberler ve ilerleme öğeleri aynı sırayla
  ve aynı adla yer alır; §10.2'deki "yetkisi varsa ayarlar/yönetim" tek maddesi,
  `YETKI_MATRISI.md` bölüm 3–4'teki daha ayrıntılı bağımsız izinlere göre "Yönetim" ve "Rapor ve
  Denetim" olarak iki koşullu üst düğüme ayrıştırılmıştır; bu bir kapsam genişletmesi değil,
  aynı tek maddenin ayrıntılandırılmasıdır.
- Her koşullu düğüm `YETKI_MATRISI.md` bölüm 3 ve 4'teki onaylı V1 kararlarına (13 Temmuz 2026)
  dayanır; bu belge hiçbir yeni devredilebilirlik kararı almaz.
- Kullanım senaryosu kimlikleri (`HOCA-*`, `ORTAK-*`, ilgili `KURUM-*`)
  `AKTORLER_VE_KULLANIM_SENARYOLARI.md` ile birebir aynıdır; yeni bir kimlik şeması icat
  edilmemiştir.
- "Hoca izinlerini değiştirme" hiçbir düğüm olarak yer almaz; kendi sınıf atamasını değiştirme
  veya kendini yeni sınıfa atama hiçbir ekranda sunulmaz; bu, `YETKI_MATRISI.md` §2.2 madde 6 ve
  §4.1'deki mutlak sınırlarla tutarlıdır.
- Personel işlemlerindeki dört izin (hoca hesabı, hoca–sınıf ataması, izin görüntüleme, cihaz
  oturumu iptali) ayrı ayrı ve bağımsız düğümler olarak işlenmiştir; `YETKI_MATRISI.md` §3.3'te
  bu dördü de ayrı satırlar olarak tanımlanmıştır.
- Terim kullanımı `TERIMLER_SOZLUGU.md` ile tutarlıdır (kurum, sınıf, program, plan, ilerleme,
  denetim kaydı, geri alma).
- Belgede ana plana veya önceki Dalga 0 belgelerine aykırı bir tanım bulunmamaktadır.

---

## 14. Varsayımlar

- Yönetim altındaki dört alt bölüm adı (Sınıf yönetimi, Dönem/takvim, Kurum ayarları, Personel
  işlemleri) ve Sınıf İşlemleri/Rapor ve Denetim/Profil grupları bu belgeye özel çalışma
  adlarıdır; ekran envanteri `EKRAN_ENVANTERI.md` §14'te bunları gruplar, kesin arayüz metni
  ve bileşen seçimi `UI-002` sözleşmesinde belirlenecektir.
- **Karar — arşivleme/geri yükleme izninin öğrenci ve sınıf için ortak olması (kapatılan açık
  soru):** `YETKI_MATRISI.md` §3.4 tek bir "Arşivlenmiş öğrenci/sınıf kaydını geri yükleme"
  iznini hem öğrenci hem sınıf geri yükleme için ortak tanımlar. Bu belgede öğrenci ve sınıf
  geri yükleme ekranları (kullanıcı deneyimi netliği için) ayrı düğümler olarak gösterilir;
  bunların tek bir ortak izne mi yoksa varlık başına ayrı iki izne mi bağlı olacağı önceki
  sürümde `P-008`/`P-009`'a bırakılan açık bir soruydu. `VERI_MODELI.md` (P-008) bu soruyu artık
  bağlayıcı biçimde kapatmıştır: `RESTORE_ARCHIVED` öğrenci ve sınıf için **tek, ortak** bir
  izin kodudur; varlık başına ayrı izin yoktur (bkz. `VERI_MODELI.md` §4.8).
- İzin saklama modeli `VERI_MODELI.md` §4.8–§4.9'da kesinleşmiştir; operasyonel veri erişimi
  her durumda sınıf ataması gerektirmeye devam eder.
- Bu belge, kullanıcının aktif olarak bağlı olduğu **tek bir kurum bağlamını** varsayar; birden
  fazla kurumda rolü olan bir kullanıcının kurumlar arası geçiş akışı bu belgenin kapsamı
  dışındadır (bkz. bölüm 16).

## 15. Bilinen sınırlamalar

- Bu belge gezinme hiyerarşisini ve düğüm-yetki eşlemesini gösterir; ekran envanteri,
  yükleniyor/boş/hata/yetkisiz durumlarını `EKRAN_ENVANTERI.md` §1.2 ve ilgili ekranlarda
  tanımlar. Görsel tasarım sonraki mobil tasarım görevlerinin kapsamındadır.
- Sınırlı kurum/sınıf metaverisi ile operasyonel veri ayrımı `VERI_MODELI.md` §16 ve API
  yetkilendirme kuralı `API_GENEL_KURALLARI.md` §4'te tanımlıdır.
- Rapor ve denetim düğümlerinin veri kapsamı bölüm 8'deki güncel sözleşmelerle bağlayıcıdır.
- Eski sistem (Excel/HTML/Apps Script) bu repoda bulunmadığından, bilgi mimarisinin eski
  sistemle karşılaştırması yapılmamıştır; bu belge yalnızca onaylı ana plana ve önceki Dalga 0
  belgelerine dayanır.

## 16. Kapsam dışı bırakılanlar

- Yönetici (platform/kurum) mobil bilgi mimarisi (`P-005` kapsamı); bölüm 12'deki referans
  bağlayıcı değildir.
- İlk sürüm ekran envanteri ve ekran durumları (boş/hata/yükleniyor) (`P-007` kapsamı).
- Düşük ayrıntılı wireframe ve görsel tasarım.
- Kesin mobil navigasyon kabuğu (sekme sayısı, gruplama, simgeler) ve rol bazlı menü API/veri
  sözleşmesi (`P-007`, `UI-002` kapsamı).
- Veri modeli, API ve senkronizasyon sözleşmesi ayrıntıları, izinlerin kurum/sınıf kapsamlı
  saklanma şeması (`P-008`–`P-010` kapsamı).
- Öğrenci ve veli (anne/baba/vasi) bilgi mimarisi (sonraki faz, Dalga 8).
- Bir kullanıcının birden fazla kurumda rolü olması durumunda kurumlar arası geçiş akışı.
