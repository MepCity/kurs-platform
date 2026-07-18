# Navigasyon ve Rol Bazlı Menü Sözleşmesi

| Alan | Değer |
|---|---|
| Görev | UI-002 — Navigasyon ve rol bazlı menü sözleşmesini yaz |
| Belge sürümü | 1.2 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` §3.1, §9.2, §10 |
| Yetki kaynağı | `YETKI_MATRISI.md` (P-003) |
| Ekran envanteri kaynağı | `EKRAN_ENVANTERI.md` (P-007) |
| Bilgi mimarisi kaynakları | `YONETICI_BILGI_MIMARISI.md` (P-005), `HOCA_MOBIL_BILGI_MIMARISI.md` (P-006) |
| Tasarım tokenı kaynağı | `MOBIL_TASARIM_TOKENLARI.md` (UI-001) |
| IAM oturum kaynağı | `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` (IAM-001) |
| Veri modeli kaynağı | `VERI_MODELI.md` (P-008) §4.5–§4.6 |
| Son güncelleme | 17 Temmuz 2026 |

---

## Revizyon notu (v1.0 → v1.1)

1. **CTX-01 IAM oturum sözleşmesiyle hizalandı.** Bağlam seçme/değiştirme davranışı,
   `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` (IAM-001) ile birebir uyumlu hale getirildi.
   `contextSelectionToken` zorunluluğu, context activation akışı, son bağlam güvenilirlik
   sınırı ve "Kurum Değiştir"in yeni provider-token-exchange gerektirmesi eklendi.

2. **Aynı kurumda çoklu rol davranışı kesinleştirildi.** `VERI_MODELI.md` §4.5–§4.6'daki
   `ORG_ADMIN` + `TEACHER` birlikteliği için yeni bölüm 7.2 eklendi. Kurum aktivasyonu
   sonrası rol seçim yüzeyi tanımlandı. Rol seçiminin mevcut kurum oturumunda yalnız
   navigasyon kabuğunu değiştirdiği, token kapsamını genişletmediği bağlandı.

3. **İzin yenileme mekanizmasındaki dayanaksız API iddiası kaldırıldı.** "Her API cevabı
   güncel izin kümesini döner" ve "bir sonraki API isteğinde güncellenir" varsayımları
   kaldırıldı. Mobil menü izinlerinin kesin endpoint/cevap sözleşmesi PERM-001/PERM-002'ye
   referans veren açık bağımlılık olarak kaydedildi.

4. **RESTORE_ARCHIVED bağımsız navigasyon olarak uygulandı.** Hoca "Daha Fazla" menüsüne
   "Arşivlenmiş Sınıflar" (MGT-04 → MGT-05) girişi RESTORE_ARCHIVED izniyle, sınıf
   yönetimi izninden bağımsız olarak eklendi. Öğrenci geri yükleme (STD-06 → STD-07)
   akışının da aynı bağımsızlıkta korunduğu teyit edildi.

5. **Tek/çoklu sınıf çelişkisi giderildi.** "İkinci sınıf gelince seçim zorunlu" ile
   "önceki sınıf aktif kalır" çelişkisi kaldırıldı. Önceki seçim hâlâ geçerliyse korunur;
   ikinci sınıf zorunlu yeniden seçim dayatmaz. Tek sınıflı kullanıcıya tek seçenekli
   seçim yüzeyi gösterilmez.

6. **Navigasyon ve uygulama ayrıntıları temizlendi.** Material 3 için bağlayıcı bileşen
   adı `NavigationBar` olarak sabitlendi. 8 karakter sınırı kaldırıldı. "Sola dayalı
   yeniden sıralanır" ifadesi kaldırıldı; yalnız görünür sekmeler arasındaki göreli sıra
   korunur. Somut Flutter uygulama varsayımları (Scaffold, Navigator, FadeTransition,
   ListView, shared_preferences) temizlendi.

7. **Kabul senaryoları eklendi.** Bölüm 17'de 13 açık kabul testi senaryosu tanımlandı.

## Revizyon notu (v1.1 → v1.2)

1. **CTX-01 görünürlüğü seçilebilir bağlam sayısına bağlandı.** CTX-01'in gösterilip
   gösterilmemesi yalnız kurum üyeliği sayısıyla değil, toplam seçilebilir bağlam sayısıyla
   belirlenir (kurum üyelikleri + GLOBAL_PLATFORM_ADMIN). Tek kurum üyeliği + platform
   admin yetkisi olan kullanıcıda CTX-01 atlanamaz. Profildeki "Kurum/Bağlam Değiştir"
   girişi aynı toplam sayısına bağlandı.

2. **Hoca operasyonel sekmelerinin görünürlük koşulu hizalandı.** Yoklama, Öğrenciler ve
   Program sekmeleri "en az bir sınıfa atanmış olmak"la değil, "geçerli bir seçili sınıf
   bağlamı varsa" görünür. Birden fazla sınıfa atanmış fakat henüz sınıf seçmemiş hocada
   operasyonel sekmeler gizli kalır. Bölüm 5.4 tablosu ve boş durum metinleri bu kuralla
   hizalandı.

3. **Seçili sınıf erişimi kaldırılınca deterministik üç dallı davranış tanımlandı.**
   "CLS-01 otomatik açılmaz" ile "CLS-01 zorunlu açılır" çelişkisi kaldırıldı. Seçim
   derhal temizlenir; 0 sınıf → boş durum, 1 sınıf → otomatik seçim ve tek sınıflı
   optimizasyon, 2+ sınıf → CLS-01 otomatik ve zorunlu açılır. Bölüm 7.3, 8.4 ve kabul
   senaryosu 10 bu kararla hizalandı.

---

## 1. Amaç ve kapsam

Bu belge, mobil uygulamanın **navigasyon kabuğunu** — hangi somut bileşenle sunulacağını,
her rolde hangi sekmelerin ve menü öğelerinin görüneceğini, bağlam (kurum/sınıf/rol)
geçişinin nasıl çalışacağını ve izin değişikliğinde menünün nasıl güncelleneceğini —
bağlayıcı bir sözleşme olarak tanımlar.

Bu belge:

- `EKRAN_ENVANTERI.md` §14'teki navigasyon gruplaması **önerisini** bağlayıcı karara
  dönüştürür.
- `YONETICI_BILGI_MIMARISI.md` §8 ve `HOCA_MOBIL_BILGI_MIMARISI.md` §8'deki **açık noktaları**
  (çoklu rol/bağlam geçişi, tek sınıflı hoca optimizasyonu, menü gruplaması) kesinleştirir.
- Her rolde alt navigasyon çubuğunun sekme sırasını, ikonunu ve görünürlük koşulunu tanımlar.
- "Daha Fazla" menüsünün yapısını, hangi ekranları kapsadığını ve izne göre nasıl
  filtrelendiğini belirler.
- Üst çubuk (AppBar) davranışını, bağlam göstergesini ve sınıf seçiciyi kesinleştirir.
- Ok tuşuyla geri gitme (back navigation) ve sekmeler arası geçiş kurallarını tanımlar.

Bu belge şunları tanımlamaz:

- Ekranların iç yerleşimi, alan listesi veya görsel tasarımı (`EKRAN_ENVANTERI.md` ve
  `UI-001` kapsamı).
- Somut Flutter widget uygulaması, widget ağacı, durum yönetimi seçimi, animasyon detayı
  (`UI-004` kapsamı).
- API, veri modeli veya yetki değerlendirme servisi detayı (`P-008`, `P-009`, `PERM-002`
  kapsamı).
- İzin yenileme endpoint sözleşmesi (`PERM-001`, `PERM-002` kapsamı).
- Logo yükleme/görüntüleme teknik detayı (`ORG-010`, `ORG-011` kapsamı).

---

## 2. Navigasyon ilkeleri

Aşağıdaki ilkeler ana plan §3.1 (sezgisel kullanım), §9.2 (menü ve modül görünürlüğü),
§12.1–§12.4 (eşzamanlı kullanım), §15 (güvenlik gereksinimleri) ve
`YETKI_MATRISI.md` §2.2 ile `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §2'den türetilmiştir:

1. **Günlük işlem önceliklidir (§3.1):** Sık kullanılan işlemler (yoklama, öğrenci listesi,
   program görüntüleme) ana navigasyonun en kolay erişilen yerindedir. Seyrek işlemler (kurum
   ayarları, personel yönetimi, raporlar) "Daha Fazla" altında gruplanır.
2. **Alt navigasyon çubuğu (NavigationBar) ana bileşendir:** Bütün roller için birincil
   navigasyon, ekranın altında sabit konumlanan Material 3 `NavigationBar`'dır. Bu çubuk
   bütün ana ekranlarda görünür; giriş ve parola değiştirme gibi oturum ekranlarında gizlenir.
3. **Her sekme kendi gezinme yığınını korur:** Bir sekmede ilerlenen alt ekranlar, o sekmenin
   kendi yığınında saklanır. Sekmeler arası geçiş yığınları korur; kullanıcı bir sekmede alt
   ekrana inmişse sekme değiştirip geri döndüğünde aynı alt ekranı görür.
4. **"Daha Fazla" ayrı bir sekme değil, bir menü sayfasıdır:** Alt navigasyon çubuğundaki
   "Daha Fazla" sekmesine dokunulduğunda, alt sekmeler gibi içerik değil, listelenmiş menü
   öğeleri gösteren bir sayfa açılır. Bu sayfanın kendi gezinme yığını vardır; menü öğesine
   dokunulduğunda hedef ekran bu yığına itilir.
5. **Yetkisiz düğüm yalnızca gizlenmez — doğrudan erişim de engellenir (§9.2, §15):** Bir
   menü öğesinin normal şartlarda gizli olması güvenlik sayılmaz; navigasyon rotası doğrudan
   bağlantıyla, eski istemciyle veya elle oluşturulmuş istekle erişilmeye çalışıldığında da
   yetkisiz durumu gösterir. **Sunucu tarafı doğrulama her koşulda bağımsız olarak çalışır;**
   menü gizleme güvenlik sınırı değildir (`YETKI_MATRISI.md` §2.2 madde 9).
6. **Güvenilir oturum olmadan doğrudan kabuğa erişim olmaz:** Mobil uygulama, yalnız yerel
   olarak saklanan `organizationId` veya `organizationMembershipId` değerine güvenerek
   bağlam açamaz. Yerel saklanan son kurum/rol tercihi yalnız kullanıcı konforu içindir;
   oturum kanıtı değildir. Kabuğun açılabilmesi için o bağlama ait güncel bir platform
   refresh/access ailesinin güvenli depoda bulunması ve sunucudan doğrulanabilmesi zorunludur
   (`IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §2, §15).
7. **Kurum değiştirme yeni bir oturum akışıdır:** Aktif bir kurum veya global platform
   oturumundan "Kurum Değiştir" seçildiğinde mevcut platform access tokenıyla yeni kurum
   bağlamı seçilemez. Kurum değiştirme, yeni bir `provider-token-exchange` ve yeni
   `contextSelectionToken` gerektirir. Provider oturumu geçerliyse bu işlem kullanıcıya
   yeniden parola sormadan yürütülebilir; provider yeniden doğrulama isterse giriş ekranına
   yönlendirilir (`IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §12.2).
8. **Aktif bağlam her zaman görünürdür:** Hangi kurumda, hangi rolde ve hangi sınıfta
   çalışıldığı, üst çubukta veya bağlam göstergesinde sürekli görünür kalır. Bağlam
   belirsiz bırakılmaz.
9. **Destek modu göstergesi:** Platform yöneticisi destek modunda bir kuruma girdiğinde,
   bütün ekranlarda sürekli bir "destek modu" göstergesi bulunur. Bu gösterge, kullanıcıya
   işlemin denetim kaydı ürettiğini hatırlatır (`URUN_VE_UYGULAMA_PLANI.md` §5.1).
10. **Çoklu rol kabukları birleşik "süper-rol" hâline getirilmez:** Kullanıcı aynı kurumda
    birden fazla role sahipse, aynı anda yalnızca seçili tek bir rolün navigasyon kabuğu
    gösterilir. Menü hiçbir zaman iki rolün izinlerini birleştirmez.

---

## 3. Genel navigasyon mimarisi

### 3.1. Kabuk yapısı

```text
Giriş / Parola Değiştir / Oturum Süresi Doldu
    (alt navigasyon çubuğu gizli)
           │
           ▼
    ┌──────────────────────────────────────────┐
    │  Üst Çubuk (AppBar)                      │
    │  [kurum adı]  [sınıf seçici]  [⚙◉]       │
    ├──────────────────────────────────────────┤
    │                                          │
    │  Sekme İçeriği                           │
    │                                          │
    ├──────────────────────────────────────────┤
    │  Alt Navigasyon Çubuğu (NavigationBar)   │
    │  [Sekme 1]  [Sekme 2]  [Sekme 3]  [...]  │
    └──────────────────────────────────────────┘
```

### 3.2. Gezinme yığını kuralları

| Kural | Açıklama |
|---|---|
| Sekme başına ayrı yığın | Her alt sekme kendi yığınına sahiptir. Bir sekmede alt ekrana inildiğinde, diğer sekmelerin yığını etkilenmez. |
| Yığın korunur | Kullanıcı A sekmesinde alt ekrandayken B sekmesine geçip A'ya dönerse, A'nın yığını korunur; aynı alt ekranı görür. |
| Sekme köküne dönüş | Kullanıcı hâlihazırda aktif sekmedeyse ve o sekmenin ikonuna tekrar dokunursa, o sekmenin yığını kök ekrana (ilk ekrana) sıfırlanır (pop-to-root). |
| "Daha Fazla" yığını | "Daha Fazla" sekmesinin kendi yığını vardır. Menü öğesine dokunulduğunda hedef ekran bu yığına itilir. Geri tuşu (veya ok) yığında bir üste döner; yığın boşaldığında "Daha Fazla" liste sayfasına dönülür. |
| Kurum değişimi | Yeni bir kurum bağlamına geçildiğinde bütün sekme yığınları sıfırlanır, kök ekranlara dönülür. |
| Rol değişimi | Aynı kurumda rol değiştirildiğinde (bölüm 7.2), bütün sekme yığınları sıfırlanır ve yeni rolün kabuğu açılır. |

### 3.3. Üst çubuk (AppBar) kuralları

AppBar, aktif bağlama göre dinamik olarak değişir.

| Bağlam | Sol | Orta (başlık) | Sağ |
|---|---|---|---|
| Platform yöneticisi — genel | Platform logosu veya uygulama adı | "Kurs Platform" | Eşitleme durum göstergesi + Profil simgesi |
| Platform yöneticisi — destek modu | Geri oku (← kurum listesine dön) | Kurum adı + [DESTEK MODU] göstergesi | Eşitleme durum göstergesi + Profil simgesi |
| Kurum yöneticisi | Kurum logosu | Kurum adı | Eşitleme durum göstergesi + Profil simgesi |
| Hoca (sınıf seçili) | Kurum logosu (küçük) | Sınıf seçici + seçili sınıf adı | Eşitleme durum göstergesi + Profil simgesi |
| Hoca (sınıf seçili değil) | Kurum logosu (küçük) | Sınıf seçici ("Sınıf seçin") | Eşitleme durum göstergesi + Profil simgesi |

**Profil simgesi menüsü:** Profil simgesine dokunulduğunda açılan menüde en az şunlar bulunur:
- Kullanıcı adı (displayName)
- "Kurum/Bağlam Değiştir" (toplam seçilebilir bağlam sayısı birden fazlaysa — bölüm 7.1)
- "Rol Değiştir" (aynı kurumda birden fazla rol varsa — bölüm 7.2)
- "Profilim" → PRF-01
- "Çıkış Yap" → PRF-03

**Sınıf seçici bileşeni:** Hoca AppBar'ında seçili sınıf adına dokunulduğunda, yalnızca
en az iki seçilebilir sınıf varsa CLS-01 (Sınıf Seçici) açılır. Tek sınıflı hocada
dokunma, seçim yüzeyi açmaz; seçili sınıf bilgisi gösterilebilir ancak seçilecek alternatif
olmadığı için seçim aksiyonu anlamsızdır (bkz. bölüm 7.3).

---

## 4. Alt navigasyon çubuğu (NavigationBar) bileşen sözleşmesi

### 4.1. Görsel tokenlar

`MOBIL_TASARIM_TOKENLARI.md` §10.6'dan alınan ve bu sözleşmede bağlayıcı hale gelen
değerler:

| Token | Değer |
|---|---|
| Yükseklik | 64 dp |
| Sekme ikon boyutu (seçili değil) | 20 dp (`icon-md`) |
| Sekme ikon boyutu (seçili) | 24 dp (`icon-lg`) |
| Sekme etiket boyutu | 10 dp (`text-xs`) |
| Yükselti | `elevation-4` (4 dp) |
| Arka plan rengi | `neutral-0` (`#FFFFFF`) |
| Seçili sekme rengi | `primary` (kurum ana rengi) |
| Seçili olmayan sekme rengi | `neutral-500` (`#6C757D`) |
| Sekme sayısı üst sınırı | En fazla 5 |

### 4.2. Sekme yapılandırması

Her sekme şu özellikleri taşır:

- **İkon:** Material ikon setinden bir ikon. Seçili/seçili değil durumunda aynı ikonun
  outlined/filled varyantı kullanılır (örn. `Icons.checklist_outlined` / `Icons.checklist`).
- **Etiket:** Kısa Türkçe metin. `TERIMLER_SOZLUGU.md` ile tutarlıdır.
- **Görünürlük koşulu:** Sekmenin gösterilip gösterilmeyeceğini belirleyen rol ve izin
  tabanlı kural. Koşul sağlanmıyorsa sekme alt çubukta **hiç gösterilmez**.
- **Hedef rota:** Sekmeye dokunulduğunda açılacak kök ekranın rotası. Sekmenin kendi
  yığınının ilk ekranıdır.

### 4.3. Sekme sırası değişmezliği

Her rol için sekme sırası sabittir. Sekme görünürlüğü izne bağlı olarak değiştiğinde,
gizlenmeyen sekmeler arasındaki göreli sıra korunur. Örneğin hoca "Daha Fazla" sekmesinin
görünürlük koşulu sağlanmıyorsa, alt çubukta yalnızca Yoklama, Öğrenciler ve Program
sekmeleri — aynı sırayla — görünür.

---

## 5. Rol bazlı sekme tanımları

### 5.1. Platform yöneticisi — genel bağlam

Platform yöneticisi, herhangi bir kuruma destek modunda girmeden önce bu sekmeleri görür.

| Sıra | Sekme etiketi | İkon (Material) | Görünürlük koşulu | Hedef kök ekran |
|---:|---|---|---|---|
| 1 | Kurumlar | `Icons.business` / `Icons.business_outlined` | Her zaman | PLAT-01 (Kurum Listesi) |
| 2 | Denetim | `Icons.history` / `Icons.history_outlined` | Her zaman | PLAT-07 (Sistem Geneli Denetim) |
| 3 | Rapor | `Icons.assessment` / `Icons.assessment_outlined` | Her zaman | PLAT-08 (Kurumlar Arası Rapor) |
| 4 | Profil | `Icons.person` / `Icons.person_outlined` | Her zaman | PRF-01 (Profil/Hesap) |

**Not:** Platform yöneticisinin alt navigasyon çubuğunda "Daha Fazla" sekmesi yoktur;
bütün işlevsel ekranlar doğrudan sekmelerdedir. Profil sekmesi PRF-01, PRF-02, PRF-03 ve
PRF-04 ekranlarını kapsar.

### 5.2. Platform yöneticisi — destek modu

Platform yöneticisi destek modunda bir kuruma girdiğinde (PLAT-06), alt navigasyon çubuğu
**kurum yöneticisi navigasyonuna (bölüm 5.3) geçer**. Farklar:

- AppBar'da kurum adı ve [DESTEK MODU] göstergesi görünür.
- Sol üstte geri oku (←) bulunur; dokunulduğunda platform yöneticisi genel bağlamına dönülür
  ve alt navigasyon çubuğu bölüm 5.1'deki haline döner.
- Kurum yöneticisinin görebildiği bütün sekmeler ve içerikler, platform yöneticisine de
  aynen gösterilir.
- Her ekran erişimi denetim kaydı üretir.
- Destek modu, global platform bağlamından farklı bir oturum kapsamı değildir; platform
  yöneticisi kendi global oturumuyla hedef kuruma erişir (bkz.
  `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §3.3, §11.3). Bu erişim, sentetik kurum üyeliği
  gibi modellenmez.

### 5.3. Kurum yöneticisi

| Sıra | Sekme etiketi | İkon (Material) | Görünürlük koşulu | Hedef kök ekran |
|---:|---|---|---|---|
| 1 | Ana Sayfa | `Icons.home` / `Icons.home_outlined` | Her zaman | HOME-01 (Kurum Ana Sayfası) |
| 2 | Sınıflar | `Icons.school` / `Icons.school_outlined` | Her zaman | CLS-01 (Sınıf Seçici) → CLS-02 (Sınıf Ana Ekranı) |
| 3 | Öğrenciler | `Icons.people` / `Icons.people_outlined` | Her zaman | STD-01 (Öğrenci Listesi — kurum geneli) |
| 4 | Yönetim | `Icons.settings` / `Icons.settings_outlined` | Her zaman | MGT-01 (Sınıf Listesi — yönetim) |
| 5 | Daha Fazla | `Icons.more_horiz` | Her zaman | "Daha Fazla" menü sayfası (bölüm 6.2) |

Kurum yöneticisi için bütün sekmeler her zaman görünür; kurum yöneticisinin varsayılan
erişiminde hiçbir sekme kapanmaz.

**Yönetim sekmesi kök ekranı:** "Yönetim" sekmesinin kök ekranı MGT-01'dir (Sınıf Listesi —
yönetim). MGT-01'den diğer yönetim ekranlarına (Dönem/Takvim MGT-07, Kurum Ayarları
MGT-09, Personel MGT-13) geçiş için MGT-01 içinde bir gezinme menüsü veya liste başlığı
bulunur. Bu ara gezinme yapısı UI-004'te netleşecektir.

### 5.4. Hoca

Hoca için sekme görünürlüğü **izne, sınıf atamasına ve geçerli seçili sınıf bağlamına bağlıdır**. Yoklama, Öğrenciler ve Program sekmeleri yalnızca geçerli bir seçili sınıf bağlamı varsa görünür. Yalnızca "en az bir sınıfa atanmış olmak" sekmeleri göstermek için yeterli değildir.

| Sıra | Sekme etiketi | İkon (Material) | Görünürlük koşulu | Hedef kök ekran |
|---:|---|---|---|---|
| 1 | Yoklama | `Icons.checklist` / `Icons.checklist_outlined` | Geçerli bir seçili sınıf varsa | ATT-01 (Bugünkü Yoklama — seçili sınıf) |
| 2 | Öğrenciler | `Icons.people` / `Icons.people_outlined` | Geçerli bir seçili sınıf varsa | STD-01 (Öğrenci Listesi — seçili sınıf) |
| 3 | Program | `Icons.menu_book` / `Icons.menu_book_outlined` | Geçerli bir seçili sınıf varsa | PRG-01 (Aktif Programlar Listesi — seçili sınıf) |
| 4 | Daha Fazla | `Icons.more_horiz` | Bölüm 6.3 koşulu | "Daha Fazla" menü sayfası (bölüm 6.3) |

**Hiç sınıfa atanmamış hoca:** Yoklama, Öğrenciler ve Program sekmeleri gizlenir. Yalnızca
"Daha Fazla" sekmesi (koşulu sağlanıyorsa) görünür. Hiçbir sekme görünmüyorsa, alt
navigasyon çubuğu tamamen gizlenir ve uygulama tek ekranlı bir görünüme düşer; sınıf ataması olmayan hocaya CLS-01 boş durumu ("Henüz atanmış bir sınıfın yok") veya yönetim alanına erişim varsa "Daha Fazla" içeriği gösterilir.

**Birden fazla sınıfa atanmış ancak henüz sınıf seçmemiş hoca:** Operasyonel sekmeler
(Yoklama, Öğrenciler, Program) gizli kalır. Kullanıcı CLS-01'den geçerli bir sınıf seçene kadar bu sekmeler görünmez. Tek geçerli sınıf varsa bu sınıf otomatik seçildiği için sekmeler doğrudan görünür (bölüm 7.3).

---

## 6. "Daha Fazla" menü yapısı

### 6.1. Genel yapı

"Daha Fazla" sekmesine dokunulduğunda, liste biçiminde gruplanmış menü öğeleri gösteren
bir sayfa açılır. Bu sayfa diğer sekmeler gibi kendi yığınına sahiptir; menü öğesine
dokunulduğunda hedef ekran bu yığına itilir.

Menü öğeleri üç düzeyde gruplanır:

1. **Bölüm başlığı (grup):** Altında menü öğeleri sıralanır. Bölüm, ancak altındaki en az
   bir menü öğesi görünür durumdaysa gösterilir; aksi durumda bölüm başlığı da gizlenir.
2. **Menü öğesi:** Dokunulduğunda hedef ekrana yönlendirir. Her menü öğesinin kendi bağımsız
   görünürlük koşulu vardır. Bir bölüm altındaki menü öğeleri birbirinden bağımsız olarak
   görünür veya gizlenir.
3. **Alt menü öğesi (opsiyonel):** Bazı bölümler altında ikinci düzey menü öğeleri
   bulunabilir. Bunlar ana menü öğesinin açtığı ekran içinde değil, doğrudan "Daha Fazla"
   listesinde ayrı bir satır olarak gösterilir.

**Görünürlük kuralı:** "Daha Fazla" bölüm başlığı, altındaki menü öğelerinden **en az biri**
görünür durumdaysa gösterilir. Hiçbir alt öğe görünmüyorsa bölüm başlığı da gizlenir.
"Daha Fazla" sekmesi, en az bir bölüm başlığı görünür durumdaysa alt navigasyon çubuğunda
gösterilir; aksi durumda "Daha Fazla" sekmesi tamamen gizlenir.

### 6.2. Kurum yöneticisi — "Daha Fazla" menüsü

Kurum yöneticisi için bütün menü öğeleri her zaman görünür; koşullu gizleme yoktur.

```text
Daha Fazla
├── Raporlar ve Denetim
│   ├── Excel Raporu İndir                              → RPT-01
│   ├── İşlem Geçmişi                                    → RPT-02
│   └── İşlemi Geri Al                                   → RPT-03
│
└── Profil ve Oturum
    ├── Profilim                                         → PRF-01
    ├── Cihaz ve Oturum                                  → PRF-02
    ├── Eşitleme Durumu                                  → PRF-04
    └── Çıkış Yap                                        → PRF-03 (onay diyaloğu açar)
```

### 6.3. Hoca — "Daha Fazla" menüsü

Hoca için menü öğeleri **bağımsız izinlere** göre görünür. Her menü öğesinin görünürlüğü,
`YETKI_MATRISI.md` bölüm 3'teki karşılık gelen iznin hocaya verilmiş olmasına bağlıdır.
Aksi belirtilmedikçe bütün menü öğeleri varsayılan olarak **kapalıdır** (gizlidir).

```text
Daha Fazla
├── Yönetim                                 [≥ 1 alt öğe görünürse bu başlık görünür]
│   ├── Sınıf Yönetimi                                 → MGT-01
│   │     [Sınıf yönetimi izni — sınıf oluşturma/düzenleme/arşivleme]
│   ├── Arşivlenmiş Sınıflar                           → MGT-04
│   │     [RESTORE_ARCHIVED izni — sınıf yönetimi izninden BAĞIMSIZ;
│   │      MGT-04'ten MGT-05 (Sınıfı Geri Yükle) akışına erişilir]
│   ├── Dönem ve Takvim                                 → MGT-07
│   │     [Dönem/takvim izni]
│   ├── Kurum Ayarları                                  → (alt öğelere genişler)
│   │     [Marka izni VEYA modül yönetimi izni VEYA yoklama durumu tanımlama izni ≥ 1]
│   │   ├── Marka Ayarları                              → MGT-09
│   │   │     [Marka ayarı izni]
│   │   ├── Etkin Modüller                              → MGT-10
│   │   │     [Modül yönetimi izni]
│   │   └── Yoklama Durumları                           → MGT-11
│   │         [Yoklama durumu tanımlama izni]
│   └── Personel                                        → MGT-13
│         [Dört personel izninden EN AZ BİRİ]
│
├── Raporlar ve Denetim                    [≥ 1 alt öğe görünürse bu başlık görünür]
│   ├── Excel Raporu İndir                              → RPT-01
│   │     [Rapor dışa aktarma izni]
│   ├── İşlem Geçmişi                                    → RPT-02
│   │     [İşlem geçmişi görüntüleme izni]
│   └── İşlemi Geri Al                                   → RPT-03
│         [Geri alma izni — işlem geçmişi izninden bağımsız]
│
└── Profil ve Oturum                        [her zaman görünür]
    ├── Profilim                                         → PRF-01
    ├── Cihaz ve Oturum                                  → PRF-02
    ├── Eşitleme Durumu                                  → PRF-04
    └── Çıkış Yap                                        → PRF-03 (onay diyaloğu açar)
```

**Sınıf Yönetimi ile Arşivlenmiş Sınıflar bağımsızlığı:** "Sınıf Yönetimi" (MGT-01 girişi)
ve "Arşivlenmiş Sınıflar" (MGT-04 girişi) aynı Yönetim bölümü altında bulunur; ancak
görünürlük koşulları tamamen bağımsızdır. Hoca yalnızca `RESTORE_ARCHIVED` iznine sahipken
ve sınıf yönetimi iznine sahip değilken MGT-04 → MGT-05 akışına ulaşabilmelidir.
`RESTORE_ARCHIVED` izni yeni kayıt oluşturma, düzenleme veya arşivleme izni sağlamaz;
bu nedenle MGT-02, MGT-03 gibi sınıf yönetimi ekranlarına erişim vermez.

**Öğrenci geri yükleme bağımsızlığı:** Hoca "Öğrenciler" sekmesindeki STD-06 (Arşivlenmiş
Öğrenciler) ve STD-07 (Öğrenciyi Geri Yükle) ekranlarına yalnızca `RESTORE_ARCHIVED` izniyle
erişir. Bu erişim, öğrenci oluşturma/düzenleme/arşivleme izninden bağımsızdır. Hoca
yalnızca `RESTORE_ARCHIVED` iznine sahipken ve öğrenci yönetimi iznine sahip değilken
STD-06 → STD-07 akışına ulaşabilmelidir. Öğrenci yönetimi izni olmadan STD-03, STD-04,
STD-05 ekranlarına erişemez.

**İç içe menü öğeleri:** Kurum Ayarları ve Personel, ikinci düzey menü öğelerine genişler.
Kurum Ayarları ana öğesi, altındaki üç izinden (marka, modül, yoklama durumu) en az birinin
açık olması durumunda görünür; ana öğeye dokunulduğunda alt liste genişler (accordion/
expansion tile). Alt öğeler yalnızca kendi izinleri açıksa görünür.

Personel ana öğesi (MGT-13), `EKRAN_ENVANTERI.md` §15.2 ve
`YONETICI_BILGI_MIMARISI.md` §7'de tanımlanan dört bağımsız personel izninden en az biri
açıksa görünür. Ana öğeye dokunulduğunda doğrudan MGT-13 (Hoca Listesi) ekranı açılır;
ikinci düzey menü genişletmesi yoktur.

**Kurum Ayarları > Özel Öğrenci Alanları (MGT-12):** Bu menü öğesi hoca için hiçbir koşulda
gösterilmez (`YETKI_MATRISI.md` §2.2 madde 6b). Kurum yöneticisinin "Daha Fazla"sında yer
alması gerekiyorsa da, hocanın menüsünde bulunmaz.

**Profil ve Oturum grubu:** Bu grup, hoca için **her zaman** görünür. Altındaki dört öğe
(Profil, Cihaz/Oturum, Eşitleme Durumu, Çıkış Yap) izin koşuluna bağlı değildir; her hoca
kendi profilini ve oturumunu yönetebilir (`YETKI_MATRISI.md` §3.3).

**Hiç yönetim izni olmayan hoca:** Yönetim ve Raporlar ve Denetim bölüm başlıkları tamamen
gizlenir. "Daha Fazla" sekmesi yalnızca "Profil ve Oturum" grubunu gösterir. Bu durumda
dahi "Daha Fazla" sekmesi alt çubukta görünür — hocanın profil ve oturum yönetimine
her zaman erişimi vardır.

---

## 7. Bağlam seçimi ve yönetimi

### 7.1. CTX-01 — Bağlam Seçimi / Kurum Seçimi

`YONETICI_BILGI_MIMARISI.md` §8 ve `EKRAN_ENVANTERI.md` §3'te aday ekran olarak kaydedilen
CTX-01, bu sözleşmeyle ve `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`'deki oturum akışıyla
bağlayıcı davranışa kavuşur.

CTX-01, `GET /api/v1/iam/auth/context-selections` çağrısının sonucuna dayanarak
kullanıcının etkin kurum üyeliklerini listeler. `context-selections` ucu yalnız geçerli
bir `contextSelectionToken` ile çağrılabilir; platform access tokenı bu uçta geçerli
değildir (`IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §5, §8).

| Özellik | Değer |
|---|---|
| Seçilebilir bağlam nedir? | Etkin kurum üyelikleri + kullanıcı aktif platform yöneticisiyse GLOBAL_PLATFORM_ADMIN seçeneği. Toplam seçilebilir bağlam sayısı bu ikisinin toplamıdır. |
| Ne zaman gösterilir? | `provider-token-exchange` başarılı olduktan sonra, eğer toplam seçilebilir bağlam sayısı birden fazlaysa CTX-01 zorunlu gösterilir. Tek kurum üyeliği + GLOBAL_PLATFORM_ADMIN yetkisi olan platform yöneticisinde CTX-01 atlanamaz (iki seçilebilir bağlam). |
| Ne zaman gösterilmez? | Toplam seçilebilir bağlam sayısı tam olarak bir ise, CTX-01 görsel olarak atlanabilir ve tek seçenek otomatik aktive edilir. Sunucu context activation token tüketimiyle tamamlanmadan başarılı sayılmaz. |
| Sürekli erişilebilir mi? | Evet — CTX-01, AppBar'daki Profil simgesi menüsünden "Kurum/Bağlam Değiştir" öğesiyle açılabilir. Bu öğe yalnızca toplam seçilebilir bağlam sayısı birden fazlaysa görünür. |
| Erişim koşulu | "Kurum/Bağlam Değiştir" seçildiğinde, mevcut platform access tokenıyla `context-selections` çağrılamaz. Yeni bir `provider-token-exchange` → yeni `contextSelectionToken` → `context-selections` akışı başlatılır. Provider oturumu geçerliyse kullanıcıya yeniden parola sorulmaz; provider yeniden doğrulama isterse AUTH-01'e yönlendirilir (`IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §12.2). |
| İçerik | Kullanıcının üyesi olduğu kurumların listesi; her satırda kurum adı, kullanıcının o kurumdaki rol(ler)i ve aktif bağlam göstergesi bulunur. |
| Seçim davranışı | Bir kuruma dokunulduğunda, `POST /api/v1/iam/auth/context-selections/{organizationMembershipId}/activate` çağrısı yapılır. Başarılı aktivasyon sonrası `contextSelectionToken` tüketilir, kurum kapsamlı refresh token ailesi oluşturulur ve platform access/refresh çifti döner. Bu cevapla kabuk (bölüm 5.3 veya 5.4) açılır. |
| Platform yöneticisi global seçeneği | Platform yöneticisi için CTX-01 listesinde "Platform Yönetimi" (GLOBAL_PLATFORM_ADMIN) seçeneği de gösterilir. Bu seçenek sentetik bir kurum üyeliği değildir; yalnızca `platformAdministrator.status = ACTIVE` ise görünür. Seçildiğinde `POST /api/v1/iam/auth/platform-admin/activate` çağrısı yapılır. |
| Son bağlam hatırlama | Uygulama, kullanıcının son aktif kurum/rol tercihini güvenli olmayan bir yerel tercih deposunda saklayabilir; bu yalnızca kullanıcı konforu içindir, yetki kanıtı değildir. Uygulama açıldığında son bağlam ancak o bağlama ait geçerli platform refresh/access ailesi güvenli depoda bulunuyor ve sunucudan (`GET /api/v1/iam/sessions/me`) doğrulanabiliyorsa doğrudan açılabilir. Oturum geçersizse, üyelik iptal edilmişse veya güvenli depoda token bulunmuyorsa yeniden `provider-token-exchange` → context selection/activation akışı çalıştırılır. |
| Eski kurum oturumu | Yeni bir kurum bağlamına geçerken, eski kurum oturumunun korunması veya kapatılması `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`'deki logout/refresh sözleşmesine göre yapılır. İstemci yalnız kurum kimliğini değiştirerek bağlam değiştiremez; her kurumun kendi token ailesi vardır (§2). |

### 7.2. Aynı kurumda çoklu rol seçimi

`VERI_MODELI.md` §4.5–§4.6, aynı `organization_membership` üzerinde hem `ORG_ADMIN` hem
`TEACHER` rolünün birlikte bulunmasına izin verir. UI-002 bu durumu aşağıdaki kurallarla
destekler.

**Bağlam iki seviyelidir:** Kurum bağlamı (bölüm 7.1'de seçilir) + aktif rol görünümü
(bu bölümde seçilir).

**Rol seçim kuralları:**

| Durum | Davranış |
|---|---|
| Tek rol | Kullanıcının seçilen kurumda yalnızca bir aktif rolü varsa, rol seçim yüzeyi gösterilmez ve ilgili kabuk doğrudan açılır. |
| Birden fazla rol (ORG_ADMIN + TEACHER) | Kurum aktivasyonundan sonra rol seçim yüzeyi gösterilir. Kullanıcı "Kurum Yöneticisi" veya "Hoca" görünümünü seçer. |
| Son rol tercihi | Son kullanılan rol görünümü, güvenli olmayan bir yerel tercih deposunda saklanabilir; yetki kanıtı değildir. Son rol hâlâ aktifse sonraki açılışta doğrudan kullanılabilir. Son rol iptal edilmiş veya geçersizse yeniden rol seçimi yapılır. |
| "Rol Değiştir" erişimi | AppBar'daki Profil simgesi menüsünde "Rol Değiştir" girişi bulunur (yalnızca aynı kurumda birden fazla rol varsa görünür). |
| Rol değiştirme davranışı | Rol değiştirmek, kurum veya token kapsamını genişletmez; yalnız mevcut kurum oturumunda sunucunun döndürdüğü (`GET /api/v1/iam/sessions/me` içindeki `roleCodes`) aktif role göre farklı navigasyon kabuğunu seçer. |
| Birleşik menü yasak | Menü hiçbir zaman iki rolün izinlerini birleştiren bir "süper-rol" hâline getirilmez. Aynı anda yalnız seçilen rolün kabuğu gösterilir. |
| Platform yöneticisi ayrımı | Platform yöneticisinin global kabuğu (bölüm 5.1), bu kurum içi rol seçiminden ayrıdır. Global bağlam ve kurum bağlamı farklı oturum kapsamlarıdır. |

**Rol seçim yüzeyi ve ekran envanteri:** Rol seçim yüzeyi, CTX-01'in kurum seçimi sonrası
gösterilen bir alt adımıdır; yeni bir ekran kimliği gerektirmez. CTX-01'in akış içindeki
durumlarından biri olarak modellenir ("kurum seçildi → roller listeleniyor"). Bu, ürün
kapsamını sessizce genişletmez.

### 7.3. Sınıf seçimi ve tek/çoklu sınıf davranışı

Hoca için sınıf bağlamı seçimi şu kurallara bağlıdır:

| Durum | Davranış |
|---|---|
| Hiç sınıf ataması yok | Operasyonel sekmeler gizli. CLS-01 boş durum gösterir. Yalnızca izinlere bağlı yönetim ve profil alanları görünür. |
| Tek sınıf ataması | CLS-01 görsel olarak atlanır; tek sınıf otomatik seçilir. Operasyonel sekmeler doğrudan görünür. |
| Birden fazla sınıf, önceden seçilmiş sınıf hâlâ geçerli | Önceden seçilmiş sınıf korunur; hoca yeniden seçim yapmak zorunda bırakılmaz. |
| Birden fazla sınıf, seçili sınıf yok/iptal edilmiş/erişilemez | CLS-01 zorunlu gösterilir; hoca seçim yapana kadar operasyonel sekmeler gizli kalır. |
| Seçili sınıf erişimi kaldırıldı | Seçim derhal temizlenir. Kalan geçerli sınıflar sayılır:<br>- **0 sınıf:** Boş durum gösterilir; CLS-01 açılmaz.<br>- **1 sınıf:** Tek sınıflı optimizasyon uygulanır ve sınıf otomatik seçilir.<br>- **2+ sınıf:** CLS-01 otomatik ve zorunlu açılır. Yeni geçerli sınıf seçilene kadar operasyonel sekmeler gizli kalır. Aktif rota artık geçersizse güvenli görünür kök ekrana geçilir. |

**Tek sınıflı kullanıcıda seçim yüzeyi:** Tek sınıfa atanmış hocada sınıf adına
dokunulduğunda seçim yüzeyi açılmaz. Seçilebilir en az iki sınıf olmadığı sürece seçim
yüzeyi göstermek gereksizdir; kullanıcıya seçim mekanizmasını "öğretme" gerekçesiyle
tek seçenekli bir liste gösterilmez.

---

## 8. İzin değişikliğinde menü güncellemesi

### 8.1. Güvenlik sınırı

Menü hiçbir zaman yalnız yerel/eski izin önbelleğine güvenerek güvenlik kararı vermez.
Sunucu tarafındaki her iş ucu kendi yetki kontrolünü bağımsız uygular.

### 8.2. İzin yenileme bağımlılığı

Mobil uygulamanın güncel menü izinlerini alacağı kesin endpoint/cevap sözleşmesi,
`PERM-001` (Yetki sabitleri ve politika sözleşmesi) ve `PERM-002` (Rol/izin migration ve
değerlendirme servisi) veya ilgili IAM oturum özeti görevinde tanımlanacaktır.

UI-004, bu bağlayıcı izin kaynağı oluşmadan "her API cevabı izin taşır" varsayımı
yapamaz.

İzin kaynağı hazır olduğunda, mobil uygulama aşağıdaki olaylarda izinleri yeniden
yükler:

1. Uygulama başlangıcında (ilk oturum kurulumu).
2. Foreground dönüşünde (uygulama arka plandan döndüğünde).
3. Bağlam/rol değişiminde (yeni kurum seçimi veya rol değiştirme).
4. `401 SESSION_REVOKED`, `403 FORBIDDEN` veya `sessionGeneration` değişikliği sinyali
   alındığında.

### 8.3. Gerçek zamanlı izin değişikliği

Gerçek zamanlı izin değişikliği olayı ileriki bir görevdir. Mevcut sınıf SSE kanalının
bunu sağladığı varsayılmamalıdır.

### 8.4. İzin veya sınıf erişimi geri alındığında navigasyon davranışı

Hocanın bütün yönetim ve rapor izinleri geri alındığında:

- Yönetim bölüm başlığı gizlenir.
- Raporlar ve Denetim bölüm başlığı gizlenir.
- "Daha Fazla" sekmesi, yalnızca Profil ve Oturum grubunu göstermeye devam ettiği için
  alt çubukta kalmaya devam eder.

Hocanın seçili sınıf erişimi iptal edildiğinde, bölüm 7.3'teki üç dallı davranış uygulanır:

- Seçili sınıf derhal temizlenir. Kalan geçerli sınıf sayısına göre:
  - **0 sınıf:** Boş durum gösterilir; CLS-01 açılmaz.
  - **1 sınıf:** Tek sınıflı optimizasyonla otomatik seçilir.
  - **2+ sınıf:** CLS-01 otomatik ve zorunlu açılır.
- Yeni geçerli sınıf seçilene kadar operasyonel sekmeler gizli kalır.
- Aktif rota artık geçersizse, uygulama güvenli görünür kök ekrana geçer.

---

## 9. Geri gitme (back navigation) kuralları

### 9.1. Android sistem geri tuşu

| Durum | Davranış |
|---|---|
| Sekme yığınında alt ekran var | Bir üst ekrana dön (pop) |
| Sekme yığını kök ekranda | Uygulamayı arka plana al (varsayılan platform davranışı) |
| "Daha Fazla" yığınında alt ekran var | Bir üst ekrana dön (pop); yığın boşaldıysa "Daha Fazla" liste sayfasına dön |
| Modal bottom sheet açık | Bottom sheet'i kapat |
| Diyalog açık | Diyaloğu kapat |

### 9.2. iOS kenardan kaydırma (swipe back)

iOS'ta kenardan kaydırarak geri gitme jesti, platformun varsayılan davranışıyla
desteklenir. Yığında bir üst ekran varsa çalışır; kök ekranda çalışmaz.

### 9.3. AppBar geri oku

| Durum | Davranış |
|---|---|
| Sekme yığınında alt ekran var | AppBar'da geri oku (←) gösterilir; dokunulduğunda bir üst ekrana dönülür (pop). |
| Sekme yığını kök ekranda | Geri oku gösterilmez. |
| "Daha Fazla" yığınında alt ekran var | AppBar'da geri oku gösterilir. |
| "Daha Fazla" liste sayfası | Geri oku gösterilmez. |

---

## 10. Derin bağlantı (deep link) ve rota koruması

### 10.1. Doğrudan rota erişimi

Uygulama içinde bir ekrana doğrudan rota adıyla erişilmeye çalışıldığında (push
notification, derin bağlantı veya programatik navigasyon):

1. Rota, bölüm 2 madde 5 gereği önce mobil yetki kontrolünden geçer.
2. Kullanıcının o rotaya erişim izni yoksa, hedef ekran yerine yetkisiz durumu (Z)
   gösterilir.
3. Bu kontrol, sunucu tarafı yetkilendirmeden bağımsız bir **ilk katman** korumasıdır;
   asıl güvenlik sunucuda sağlanır. Menü gizli olsa bile doğrudan rota veya API erişimi
   sunucu tarafından reddedilir.

### 10.2. Giriş gerektiren rotalar

Kullanıcı giriş yapmamışsa, bütün korumalı rotalar giriş ekranına (AUTH-01) yönlendirir.
Başarılı girişten sonra, kullanıcının son bağlamına ve yetkilerine göre hedef rotaya
veya varsayılan kabuğa yönlendirme yapılır.

---

## 11. Eşitleme durum göstergesi

### 11.1. Konum

Eşitleme durum göstergesi, `MOBIL_TASARIM_TOKENLARI.md` §10.9'a uygun olarak AppBar'ın
sağ üst köşesinde, Profil simgesinin solunda yer alır.

### 11.2. Görünürlük

Gösterge şu durumlarda görünür:

- **Bekleyen işlem varsa:** `sync-pending` renginde (gri) bir saat/döngü ikonu.
- **Aktif gönderim varsa:** `sync-syncing` renginde (mavi) dönen bir ilerleme göstergesi.
- **Başarısız işlem varsa:** `sync-failed` renginde (kırmızı) bir uyarı ikonu; dokunulduğunda
  PRF-04 (Eşitleme Durumu) ekranına yönlendirir.

Tüm işlemler başarılı olduğunda ve bekleyen işlem kalmadığında, gösterge gizlenir veya
`sync-success` renginde (yeşil) kısa süreli bir onay ikonu gösterilip kaybolur.

### 11.3. Bağlam bağımsızlığı

Eşitleme durumu, kullanıcının hangi kurumda veya sınıfta olduğundan bağımsızdır; bütün
kurumlardaki bekleyen işlemleri tek bir göstergede toplar.

---

## 12. Sekme başına ekran eşleşmesi

### 12.1. Platform yöneticisi — genel bağlam

| Sekme | Kök ekran | Yığındaki diğer ekranlar |
|---|---|---|
| Kurumlar | PLAT-01 (Kurum Listesi) | PLAT-02, PLAT-03, PLAT-04, PLAT-05, PLAT-06 |
| Denetim | PLAT-07 (Sistem Geneli Denetim) | — |
| Rapor | PLAT-08 (Kurumlar Arası Rapor) | — |
| Profil | PRF-01 (Profil/Hesap) | PRF-02, PRF-04, PRF-03 (diyalog) |

### 12.2. Kurum yöneticisi

| Sekme | Kök ekran | Yığındaki diğer ekranlar |
|---|---|---|
| Ana Sayfa | HOME-01 (Kurum Ana Sayfası) | — |
| Sınıflar | CLS-01 → CLS-02 (Sınıf Ana Ekranı) | ATT-01, ATT-02, STD-01..STD-09, PRG-01..PRG-04, PRS-01, PRS-02 (seçili sınıf bağlamında) |
| Öğrenciler | STD-01 (Öğrenci Listesi — kurum geneli) | STD-02..STD-09 |
| Yönetim | MGT-01 (Sınıf Listesi — yönetim) | MGT-02..MGT-20, MGT-07..MGT-08, MGT-09..MGT-12 |
| Daha Fazla | "Daha Fazla" menü sayfası | RPT-01..RPT-03, PRF-01..PRF-04 |

### 12.3. Hoca

| Sekme | Kök ekran | Yığındaki diğer ekranlar |
|---|---|---|
| Yoklama | ATT-01 (Bugünkü Yoklama) | ATT-02 (ilgili izin varsa) |
| Öğrenciler | STD-01 (Öğrenci Listesi) | STD-02 (temel bilgi), STD-03..STD-09 (ilgili izin varsa; STD-06/STD-07 RESTORE_ARCHIVED izniyle bağımsız) |
| Program | PRG-01 (Aktif Programlar Listesi) | PRG-02..PRG-04 (ilgili izin varsa), PRS-01, PRS-02 |
| Daha Fazla | "Daha Fazla" menü sayfası | Bölüm 6.3'teki ilgili ekranlar, PRF-01..PRF-04 |

---

## 13. Ana ürün planıyla uyum kontrolü

- Alt navigasyon çubuğu tercihi, §3.1 sezgisel kullanım ilkesini karşılar: günlük işlemler
  (yoklama, öğrenciler, program) doğrudan erişilebilir; seyrek yönetim işlemleri "Daha
  Fazla" altındadır.
- Menü görünürlüğü §9.2 ilkesine uygundur: rol ve kurumda etkin modüllere göre belirlenir;
  yetkisiz düğüm yalnızca gizlenmez, doğrudan bağlantıyla da erişilemez (bölüm 10.1).
- Bağlam seçme/değiştirme davranışı, `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` (IAM-001) bölüm
  2, 8, 9, 10 ve 12 ile uyumludur. `contextSelectionToken` zorunluluğu, kurum
  aktivasyonunun token tüketimiyle tamamlanması ve kurum değiştirmenin yeni provider-token
  exchange gerektirmesi bu belgeye açık referanslarla bağlanmıştır.
- "İzin verilmediyse erişim yok" ilkesi (§5.5), bütün menü öğelerinin varsayılan kapalı
  olması ve bağımsız izinle açılmasıyla karşılanır.
- Menü güvenlik sınırı bağlayıcıdır: menü hiçbir zaman yalnız yerel/eski izin
  önbelleğine güvenerek güvenlik kararı vermez; sunucu tarafı yetki kontrolü her zaman
  zorunludur (bölüm 8.1).
- Destek modu göstergesi (§5.1), platform yöneticisi erişiminin denetimli olduğunu
  görünür kılar. Global platform bağlamı sentetik kurum üyeliği gibi modellenmez.
- Eşitleme durum göstergesi (§13), kullanıcının bekleyen, başarılı ve başarısız işlemleri
  ayırt edebilmesini sağlar.
- "Daha Fazla" altındaki personel yönetimi menü öğeleri, `YETKI_MATRISI.md` §4.1'deki
  güvenlik sınırlarına uyar: hoca kendi sınıf atamasını değiştiremez, hoca izinlerini
  değiştirme menü öğesi hiçbir zaman gösterilmez.
- RESTORE_ARCHIVED izni, sınıf/öğrenci oluşturma-düzenleme-arşivleme izinlerinden
  bağımsız navigasyon girişleriyle uygulanmıştır (bölüm 6.3, bölüm 12.3).
- Çoklu rol (ORG_ADMIN + TEACHER) davranışı, `VERI_MODELI.md` §4.5–§4.6 ve
  `IAM_GIRIS_OTURUM_API_SOZLESMESI.md` §6.1 `roleCodes` ile uyumludur. Birleşik "süper-rol"
  menüsü üretilmez.
- Bütün menü öğeleri, `EKRAN_ENVANTERI.md`'deki 59 ekranın rol bazlı görünürlük
  tablolarıyla eşleşir; hiçbir ekran navigasyondan dışlanmamıştır. Rol seçim yüzeyi,
  CTX-01'in bir alt durumu olarak modellendiğinden yeni ekran kimliği gerektirmez.
- Tek sınıflı hoca optimizasyonu, seçilebilir en az iki sınıf olmadan seçim yüzeyi
  açılmaması kuralıyla kesinleştirilmiştir.
- Sekme sayısı (en fazla 5) ve "Daha Fazla" kullanımı, mobil ekranda sezgisel kullanımı
  korur.
- Terim kullanımı `TERIMLER_SOZLUGU.md` ile tutarlıdır.

---

## 14. UI-004 ile ilişki

`UI-004 — Rol bazlı mobil kabuk ve navigasyon` görevi, bu sözleşmeyi birebir uygulayacaktır.
UI-004:

- Material 3 `NavigationBar` ile alt navigasyon çubuğunu kuracaktır.
- Bölüm 5'teki sekme tanımlarını her rol için ayrı bir yapılandırma olarak kodlayacaktır.
- Bölüm 6'daki "Daha Fazla" menüsünü izin tabanlı görünürlükle oluşturacaktır.
- Bölüm 7'deki bağlam seçimi, çoklu rol ve sınıf seçimi davranışını IAM oturum
  sözleşmesine uygun biçimde uygulayacaktır.
- Bölüm 8'deki izin yenileme bağımlılığını dikkate alacak; "her API cevabı izin taşır"
  varsayımı yapmayacaktır.
- Bölüm 9 ve 10'daki geri gitme ve derin bağlantı kurallarını rota yapılandırmasına
  entegre edecektir.
- Token ve refresh ailesini yalnızca OS güvenli deposunda tutacaktır.

UI-004; somut widget ağacı, durum yönetimi seçimi, animasyon detayı ve kod
organizasyonundan sorumludur. Bu sözleşmede belirtilmeyen somut Flutter sınıfları
(Scaffold, Navigator, FadeTransition, ListView vb.) UI-004'ün uygulama kararıdır.

---

## 15. Varsayımlar

- Alt navigasyon çubuğu, Material 3 `NavigationBar` bileşeniyle gerçeklenir. `NavigationBar`
  bu sözleşmenin bağlayıcı bileşen adıdır.
- Sekme ikonları, Material ikon setindendir. İlk sürümde özel ikon seti kullanılmaz.
- "Daha Fazla" menüsü, basit bir liste sayfası olarak gerçeklenir; karmaşık bir drawer
  veya side sheet değildir.
- Son kurum/rol tercihi, güvenli olmayan bir yerel tercih deposunda saklanır (kurum
  ID'si/rol kodu gibi kimlik bilgileri). Token ve refresh ailesi yalnızca OS güvenli
  deposunda tutulur; tercih deposu ile güvenli depo aynı mekanizma değildir.
- Kullanıcının birden fazla kurumda üye olması senaryosu, ilk sürümde platform
  yöneticileri için olasıdır; diğer roller için nadirdir. Yine de CTX-01 bütün roller
  için aynı şekilde çalışır.
- Aynı kurumda hem ORG_ADMIN hem TEACHER rolüne sahip kullanıcı için, kurum
  aktivasyonundan sonra rol seçim adımı gösterilir. Tek rol varsa bu adım atlanır.
- `YONETICI_BILGI_MIMARISI.md`'deki Kurum Ayarları > Özel Öğrenci Alanları menü öğesi,
  kurum yöneticisinin MGT-01'den veya "Daha Fazla"daki Yönetim bölümünden erişebileceği
  bir ekrandır. Tam yeri UI-004'ün MGT-01 içi gezinme tasarımıyla netleşir.
- MGT-01'in "yönetim giriş noktası" olarak içerdiği ara gezinme listesi (Dönem/Takvim,
  Kurum Ayarları, Personel bağlantıları), bir sekme içi navigasyon menüsü veya liste
  başlığıdır; ayrı bir alt sekme yapısı değildir.

---

## 16. Bilinen sınırlamalar

- İzin yenileme endpoint sözleşmesi (`PERM-001`, `PERM-002`) henüz tanımlanmamıştır.
  Bu, UI-004'ün önünde açık bir bağımlılıktır. UI-004, bu sözleşmeler olmadan izin
  bazlı menü güncellemesini eksiksiz uygulayamaz.
- Gerçek zamanlı izin değişikliği olayı ileriki bir görevdir; mevcut sınıf SSE kanalının
  bunu sağladığı varsayılmamalıdır.
- "Daha Fazla" menüsündeki Kurum Ayarları alt öğelerinin genişletme/daraltma animasyonu
  ve görsel tasarımı bu belgede tanımlanmamıştır; UI-004'te kararlaştırılacaktır.
- "Daha Fazla" menüsünün izne göre dinamik olarak yeniden oluşturulması, izin yenileme
  kaynağı hazır olana kadar statik bir snapshot ile sınırlı kalabilir.
- Eski sistem (Excel/HTML/Apps Script) bu repoda bulunmadığından, navigasyon yapısının
  eski sistemdeki menü ile karşılaştırması yapılmamıştır; bu belge yalnızca onaylı ana
  plana ve Dalga 0/Dalga 1 belgelerine dayanır.
- Tablet cihazlar için optimize edilmiş navigasyon (bölmeli görünüm, yan panel, çok sütunlu
  layout) ilk sürüm kapsamında değildir. Tablet uyumu eklendiğinde alt navigasyon çubuğu
  yerine `NavigationRail` veya kalıcı drawer gibi alternatif bileşenler değerlendirilebilir.

---

## 17. Kabul senaryoları

Aşağıdaki senaryolar, UI-004 ve sonraki entegrasyon testlerinde doğrulanmalıdır:

1. **Tek kurumlu kullanıcıda görsel CTX-01 atlanır, context activation sunucuda tamamlanır:**
   Kullanıcı tek kurum üyesidir; `provider-token-exchange` → `context-selections` → tek
   üyelik görülür → otomatik `activate` yapılır → CTX-01 gösterilmeden doğrudan kabuk açılır.

2. **Aktif kurum oturumuyla context-selections çağrısı reddedilir:**
   Kullanıcı bir kurum kabuğundayken "Kurum Değiştir" seçer. Mevcut platform access tokenıyla
   `context-selections` çağrılamaz; `403 ORGANIZATION_CONTEXT_REQUIRED` döner. Yeni
   `provider-token-exchange` akışı başlatılır.

3. **Son kurum tercihi var ama güvenli oturum yoksa doğrudan kabuk açılmaz:**
   Yerel tercih deposunda son kurum ID'si var, fakat güvenli depoda o kuruma ait refresh
   token yok veya `GET /api/v1/iam/sessions/me` `401` dönüyor. Uygulama doğrudan kabuğa
   gitmez; yeniden `provider-token-exchange` başlatır.

4. **Platform yöneticisi global bağlam ile kurum bağlamını ayırt ederek seçebilir:**
   Platform yöneticisi CTX-01'de "Platform Yönetimi" (GLOBAL_PLATFORM_ADMIN) seçeneğini
   görür. Bu seçenek sentetik kurum üyeliği değildir; seçildiğinde `platform-admin/activate`
   çağrılır ve global kabuk (bölüm 5.1) açılır.

5. **Aynı kurumda ORG_ADMIN + TEACHER rolü olan kullanıcı rol seçebilir ve rol
   değiştirebilir:** Kurum aktivasyonundan sonra rol seçim yüzeyi gösterilir. Kullanıcı
   "Kurum Yöneticisi"ni seçer → bölüm 5.3 kabuğu açılır. Profil menüsünden "Rol
   Değiştir" ile "Hoca" seçilir → bölüm 5.4 kabuğu açılır. İki kabuk birleşmez.

6. **Rol iptal edildiğinde son rol tercihi güvenilir kabul edilmez:**
   Kullanıcının son rol tercihi "Kurum Yöneticisi" olarak kayıtlı, ancak ORG_ADMIN rolü
   geri alınmış. Uygulama açıldığında son rol otomatik seçilmez; kalan tek rol TEACHER
   ile doğrudan hoca kabuğu açılır (rol seçim adımı atlanır — tek rol kaldığı için).

7. **Yalnız RESTORE_ARCHIVED izni olan hoca MGT-04/MGT-05'e erişebilir:**
   Hocanın RESTORE_ARCHIVED izni var, sınıf yönetimi izni yok. "Daha Fazla" → Yönetim →
   "Arşivlenmiş Sınıflar" (MGT-04) görünür ve tıklanabilir. MGT-04'ten MGT-05'e
   (Sınıfı Geri Yükle) geçiş yapılabilir. "Sınıf Yönetimi" (MGT-01) alt öğesi
   RESTORE_ARCHIVED sahibi hocada görünmez.

8. **RESTORE_ARCHIVED izni olmayan fakat sınıf yönetimi izni olan hoca geri yükleme
   eylemini göremez:** Hocanın sınıf yönetimi izni var, RESTORE_ARCHIVED izni yok.
   "Daha Fazla" → Yönetim → "Sınıf Yönetimi" (MGT-01) görünür. "Arşivlenmiş Sınıflar"
   (MGT-04) görünmez. Öğrenciler sekmesinde STD-06/STD-07 görünmez.

9. **Geçerli seçili sınıf varken ikinci sınıf atanması zorunlu yeniden seçim üretmez:**
   Hoca A sınıfında çalışıyor, seçili bağlam A. Kurum yöneticisi hocayı B sınıfına da
   atar. Hoca otomatik olarak sınıf seçim ekranına yönlendirilmez; A sınıfı seçili
   kalmaya devam eder. AppBar'daki sınıf seçici artık iki seçeneklidir; dokunulduğunda
   CLS-01 iki sınıflı liste gösterir.

10. **Seçili sınıf erişimi kaldırıldığında kalan sınıf sayısına göre davranılır:**
    Hoca A sınıfında çalışıyor; kurum yöneticisi A sınıfı atamasını kaldırır. Seçim derhal
    temizlenir. Kalan sınıflar sayılır:
    - 0 sınıf → boş durum, CLS-01 açılmaz.
    - 1 sınıf (B) → tek sınıflı optimizasyonla B otomatik seçilir, sekmeler görünür.
    - 2+ sınıf → CLS-01 otomatik ve zorunlu açılır; seçim yapılana kadar operasyonel
      sekmeler gizli kalır.

11. **Bir kurum üyeliği ve GLOBAL_PLATFORM_ADMIN yetkisi olan kullanıcı CTX-01'de iki ayrı seçenek görür:**
    Platform yöneticisi bir kuruma üyedir ve GLOBAL_PLATFORM_ADMIN yetkisi vardır.
    `provider-token-exchange` sonrası toplam seçilebilir bağlam = 2 (kurum + platform admin).
    CTX-01 atlanamaz; hem kurum seçeneği hem "Platform Yönetimi" seçeneği gösterilir.
    Sistem kurumu otomatik seçerek global seçeneği atlayamaz.

12. **İzin yenilemesi için "her API cevabı izin taşır" varsayımı bulunmaz:**
    UI-004'ün izin bazlı menü güncellemesi, bu varsayıma dayanmaz. Menü, PERM-001/
    PERM-002 veya IAM oturum özetindeki bağlayıcı izin kaynağı oluşana kadar statik
    bir snapshot ile sınırlıdır.

13. **Menü gizli olsa bile doğrudan rota/API erişimi sunucu tarafından reddedilir:**
    Hocanın sınıf yönetimi izni yok; "Sınıf Yönetimi" menü öğesi gizli. Hoca doğrudan
    MGT-01 rotasına navigasyon yapmaya çalışır → mobil rota koruması yetkisiz durumu
    gösterir. Aynı işlem API ile denenirse → sunucu `403 FORBIDDEN` döner.

14. **Çoklu rol kabukları birleşik süper-role dönüşmez:**
    Kullanıcının aynı kurumda ORG_ADMIN ve TEACHER rolü var. "Kurum Yöneticisi"
    seçiliyken hoca izni gerektiren bir menü öğesi (örn. Yoklama sekmesi) görünmez.
    "Hoca" seçiliyken kurum yöneticisi özel menü öğeleri (örn. MGT-19 İzinleri
    Değiştir) görünmez. Hiçbir durumda iki kabuk birleşmez.

---

## 18. Kapsam dışı bırakılanlar

- Somut Flutter navigasyon uygulaması, widget ağacı ve yığın yönetimi (`UI-004` kapsamı).
- Tasarım tokenları, ikon seti, renk ve tipografi kararları (`UI-001`, `UI-003` kapsamı).
- Mobil giriş ekranı ve oturum akışı (`IAM-007`, `IAM-008` kapsamı).
- Logo yükleme ve görüntüleme teknik detayı (`ORG-010`, `ORG-011` kapsamı).
- Yetki değerlendirme servisi ve izin modeli uygulaması (`PERM-002`, `IAM-003` kapsamı).
- İzin yenileme endpoint sözleşmesi (`PERM-001`, `PERM-002` kapsamı).
- Eşitleme kuyruğu ve durum yönetimi (`SYNC-001`, `SYNC-003` kapsamı).
- Tablet için optimize edilmiş navigasyon bileşenleri (sonraki faz).
- Öğrenci ve veli navigasyonu (sonraki faz, Dalga 8 `PORTAL-*`).
- Web yönetim paneli navigasyonu (sonraki faz, Dalga 8 `WEB-*`).
