# Ayrıntılı Yetki Matrisi

| Alan | Değer |
|---|---|
| Görev | P-003 — Ayrıntılı yetki matrisini oluştur |
| Belge sürümü | 1.2 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Terim kaynağı | `TERIMLER_SOZLUGU.md` |
| Aktör/senaryo kaynağı | `AKTORLER_VE_KULLANIM_SENARYOLARI.md` |
| Son güncelleme | 13 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, `URUN_VE_UYGULAMA_PLANI.md` bölüm 5 (kullanıcı rolleri ve yetki modeli) ve bölüm 5.5
(yetki örnekleri) ile `AKTORLER_VE_KULLANIM_SENARYOLARI.md` bölüm 4'te (yönetici ve hoca
işlerinin sınırı) çizilen ilke düzeyindeki çerçeveyi, her işlem için ayrı ayrı doğrulanabilir
bir eylem × rol matrisine dönüştürür.

Ana planda önceden kesinleşmiş kararlar bölüm 2.2'deki değişmez ilkelere ve bölüm 3'teki
"Mutlak sınır" işaretli hücrelere aktarılmıştır. Bu belgenin ilk sürümünde ana planda açık
dayanağı olmayan dokuz devredilebilirlik sorusu "bekleyen ürün kararı" olarak işaretlenmişti;
kullanıcı bu dokuz kararı **13 Temmuz 2026** tarihinde onaylamıştır. Bu kararlar artık varsayım
veya öneri değil, bağlayıcı V1 ürün kararlarıdır ve bölüm 3'teki matrise, bölüm 4'teki kurallara
ve bölüm 6.2'ye işlenmiştir. Aynı karar özeti `URUN_VE_UYGULAMA_PLANI.md` bölüm 26 (Karar
günlüğü) içine de eklenmiştir.

Bu belge:

- Veri modelindeki izin/rol şemasının teknik uygulamasını tanımlamaz (`P-008` kapsamı).
- API düzeyinde yetki denetiminin nasıl kodlanacağını tanımlamaz (uygulama görevleri kapsamı).
- Öğrenci ve veli (anne/baba/vasi) için gelecekteki yetkileri tanımlamaz (sonraki faz, Dalga 8).
- Onaylanan devredilebilirlik kararlarının kesin teknik veri filtrelerini tanımlamaz; bunlar
  `P-008` ve `P-009`'a girdi olarak bırakılmıştır (bkz. bölüm 4.4).

---

## 2. Roller ve temel ilkeler

`TERIMLER_SOZLUGU.md` §3.1'e göre rol, kullanıcıyı sabitleyen bir kimlik değil; kullanıcıya
bağlam (global/kurum/sınıf) bazında atanan bir yetki kümesidir. Bu matriste kullanılan üç rol:

| Rol | Bağlam | Kaynak |
|---|---|---|
| Platform yöneticisi | Global — bütün kurum ve sınıflar | `URUN_VE_UYGULAMA_PLANI.md` §5.1 |
| Kurum yöneticisi | Tek kurum — kendi kurumunun bütün sınıf ve verileri | `URUN_VE_UYGULAMA_PLANI.md` §5.2 |
| Hoca | Yalnızca kendisine atanmış sınıf(lar) | `URUN_VE_UYGULAMA_PLANI.md` §5.3 |

### 2.1. Yetki durumu kategorileri

Bölüm 3'teki her hücre aşağıdaki dört kategoriden biriyle işaretlenmiştir. "Mutlak sınır"
kategorisi yalnızca ana planda gerçekten onaylanmış, hiçbir izin atamasıyla değişmeyen sınırlar
için kullanılır; varsayılan olarak kapalı ama devredilebilirliği ana planda açık olmayan
işlemler bu kategoriye konmaz.

| Kategori | Anlamı |
|---|---|
| **Varsayılan açık — yönetici geri alabilir** | Rol bu işlemi ek izin atanmadan yapabilir; kurum yöneticisi bu izni tek başına geri alabilir. |
| **Varsayılan kapalı — ayrı izinle açılabilir** | Rol bu işlemi ek izin atanmadan yapamaz; kurum yöneticisi bunu ayrı ve geri alınabilir bir izin olarak açabilir. |
| **Mutlak sınır** | Ana planda açıkça onaylanmış, hiçbir izin atamasıyla açılamayan sınır. |
| **Bekleyen ürün kararı** | Ana planda bu işlemin devredilip devredilemeyeceği açık biçimde kararlaştırılmamıştır. Bu belge bir yön dayatmaz; `P-008`/`P-009` için güvenlik gereksinimi girdisi olarak işaretlenmiştir. |

### 2.2. Değişmez ilkeler (mutlak sınırlar)

Aşağıdaki ilkeler ana planda açıkça onaylanmış, tartışmasız değişmez sınırlardır. Bölüm 3'teki
"Mutlak sınır" işaretlemeleri bu ilkelere dayanır.

1. **Varsayılan politika:** İzin açıkça verilmediyse erişim yoktur (§5.5).
2. **Kurum kapsamlı roller kurum dışına çıkamaz:** Kurum yöneticisi, kendi kurumu dışındaki
   hiçbir kurumun verisine erişemez (§4.2, §15).
3. **Hoca, atanmadığı sınıfın operasyonel verisine erişemez:** Hoca, kendisine atanmamış hiçbir
   sınıfın öğrenci, veli, yoklama, ilerleme, değerlendirme ve normal öğretmen notu gibi
   **operasyonel** verisine erişemez (§5.3, §15). Bu operasyonel veri sınırı, kurum kapsamlı bir
   yönetim izniyle (örn. hoca–sınıf ataması, marka yönetimi) **genişletilemez** — böyle bir izin
   yalnızca §2.2 madde 11 ve bölüm 4.4'te tanımlanan, işlemin yapılması için zorunlu sınırlı
   kurum/sınıf **metaverisine** erişim sağlar; operasyonel sınıf verisine erişim sağlamaz. Kesin
   metaveri alanları `P-008`/`P-009`'da tanımlanacaktır.
4. **Platform yöneticisi global kapsamlı istisnadır:** Platform yöneticisi bütün kurumlara
   erişebilir; ancak bu erişim kurum kapsamlı rollerin tabi olduğu izolasyon kuralının bir
   istisnasıdır ve her kurum verisi erişimi (görüntüleme dahil) ayrıca denetim kaydı üretir
   (§5.1). Bu istisna, platform yöneticisini izlenmeyen bir üst rol yapmaz.
5. **Hoca kendi yetkisini kendiliğinden genişletemez:** Hoca, kurum yöneticisi tarafından ayrı
   izinle kendisine verilmiş personel yönetimi yetkilerini (hoca hesabı oluşturma, hoca–sınıf
   ataması, izin görüntüleme — bkz. madde 6a) kullanabilse de, bu yetkiler ona veya
   oluşturduğu/atadığı başka bir hocaya **izin değiştirme hakkı vermez**. Hoca kendi izin
   kümesini kendisi büyütemez; izin kümesindeki genişleme yalnızca kurum yöneticisinden gelir
   (§5.3).
6. **Hoca izinlerini değiştirme/verme/geri alma V1'de mutlak sınırdır:** Bu işlem hiçbir ayrı
   izinle hocaya devredilemez; yalnızca kurum yöneticisi kendi kurumu içinde hoca izinlerini
   yönetebilir. Hiçbir hoca — personel yönetimi yetkisi verilmiş olsa dahi — kendi veya başka
   bir hocanın izinlerini değiştiremez. Bu, kullanıcı tarafından 13 Temmuz 2026'da onaylanmış,
   V1 için bağlayıcı mutlak sınırdır (bkz. bölüm 6.2, karar 6).
6a. **Personel yönetimi izni ile izin yönetimi yetkisi ayrı kavramlardır:** Hoca hesabı
   oluşturma, hoca–sınıf ataması ve hoca izinlerini görüntüleme; kurum yöneticisi tarafından
   hocaya ayrı ve geri alınabilir izinlerle açılabilir (bkz. bölüm 3.3, bölüm 4.1). Bu izinlerin
   hiçbiri, sahibine başka bir kullanıcıya izin verme/değiştirme yetkisi sağlamaz — bu yetki
   madde 6'daki mutlak sınıra tabidir.
7. **Sahip olunmayan veya devretme hakkı olmayan izin başkasına verilemez:** Bir kullanıcı,
   kendisinde bulunmayan veya kendisine devretme yetkisi tanınmamış bir izni başka bir
   kullanıcıya atayamaz.
8. **Belirli işlemler her koşulda platform yöneticisi kapsamındadır:** Kurum oluşturma, kurum
   yöneticisi atama, kurum durumu yönetimi (aktif/askıda/arşiv), sistem geneli denetim kaydına
   erişim ve kurumlar arası raporlama; bu işlemler kurum yöneticisine veya hocaya hiçbir izin
   atamasıyla devredilmez (§5.1). Kurum yöneticisi rolü ataması özellikle bu maddenin
   kapsamındadır; personel yönetimi yetkisi verilen bir hoca da dahil, hiçbir kurum içi rol bu
   atamayı yapamaz.
9. **Sunucu tarafı doğrulama:** Arayüzde bir eylemin gizlenmesi yetkilendirme sayılmaz; her
   işlem sunucuda kurum, sınıf ve işlem bazında doğrulanmalıdır (§5, §15).
10. **Sınıf ataması ile işlem izni ayrı kavramlardır:** Bir sınıfa atanmış olmak, o sınıfın veri
    kapsamına erişimi belirler; işlem izni ise bu kapsam içinde hangi eylemin yapılabileceğini
    belirler. Bir işlem yapılabilmesi için hem kapsam (sınıf ataması) hem de işlem izni birlikte
    sağlanmalıdır — biri olmadan diğeri işlem yapmaya yetmez.
11. **Kurum kapsamlı yönetim izni, operasyonel sınıf verisine toplu erişim sağlamaz:** Marka
    yönetimi, etkin modül yönetimi, hoca–sınıf ataması veya kuruma özel yoklama durumu tanımlama
    gibi kurum kapsamlı yönetim izinlerine sahip olmak, hocaya kurumun bütün sınıflarındaki
    operasyonel veriyi (öğrenci, veli, yoklama, ilerleme, değerlendirme, normal öğretmen notu)
    görme hakkı vermez; bu madde 3'teki operasyonel veri sınırının bir tekrarıdır (bkz. bölüm
    4.4).
12. **Bütün hoca izinleri ayrı ayrı ve geri alınabilirdir:** Hocanın varsayılan olarak sahip
    olduğu veya kendisine ayrı izinle açılmış her işlem izni, kurum yöneticisi tarafından o
    hocanın sınıf ataması korunurken bile tek başına geri alınabilir. "Varsayılan açık" işaretli
    hiçbir satır, yöneticinin o izni kapatamayacağı biçiminde okunmamalıdır.

---

## 3. Ayrıntılı eylem × rol matrisi

Sütunlar:

- **Platform yöneticisi**, **Kurum yöneticisi**, **Hoca (varsayılan)** — o rolün ek izin
  atanmadan bu işlemi yapıp yapamayacağını gösterir.
- **Hoca için durum kategorisi** — bölüm 2.1'deki dört kategoriden hangisinin geçerli olduğunu
  gösterir. Bu sütun, hocaya devredilebilirliğin kesinliğini açıkça ayırt eder.

Bütün satırlarda bölüm 2.2'deki mutlak sınırlar (özellikle kurum/sınıf izolasyonu ve sunucu
tarafı doğrulama) geçerlidir; bu nedenle her hücrede tekrar yazılmamıştır.

### 3.1. Platform ve kurum yönetimi

| İşlem | Platform yöneticisi | Kurum yöneticisi | Hoca (varsayılan) | Hoca için durum kategorisi | Kaynak |
|---|---|---|---|---|---|
| Kurum oluşturma | Evet | Hayır | Hayır | Mutlak sınır — platform yöneticisi kapsamı (§2.2 madde 8) | §4.2, §5.1, PLAT-01 |
| Kurum yöneticisi atama | Evet | Hayır | Hayır | Mutlak sınır — platform yöneticisi kapsamı (§2.2 madde 8) | §5.1, PLAT-02 |
| Kurum durumu yönetimi (aktif/askıda/arşiv) | Evet | Hayır | Hayır | Mutlak sınır — platform yöneticisi kapsamı (§2.2 madde 8) | §5.1, §6.1, PLAT-03 |
| Destek amaçlı kurum bağlamına geçiş | Evet (her erişim denetim kaydı üretir) | Kapsam dışı | Kapsam dışı | Kapsam dışı — platform yöneticisine özel istisna mekanizması | §5.1, PLAT-04 |
| Sistem geneli denetim kaydına erişim | Evet | Hayır (yalnızca kendi kurumunun denetim kaydı — bkz. 3.7) | Hayır | Mutlak sınır — platform yöneticisi kapsamı (§2.2 madde 8) | §5.1, PLAT-05 |
| Kurumlar arası raporlama | Evet | Hayır (yalnızca kendi kurumu) | Hayır | Mutlak sınır — platform yöneticisi kapsamı (§2.2 madde 8) | §5.1, PLAT-06 |
| Kurum adı/logo/renk (marka) ayarı | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 1); işlem denetim kaydı üretmelidir (bkz. bölüm 4.5) | §5.2, §8.2, §9.1, KURUM-01 |
| Kurumda etkin modülleri belirleme | Evet | Evet (kendi kurumu) | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 2); işlem denetim kaydı üretmelidir (bkz. bölüm 4.5) | §8.2 |
| Eğitim dönemi ve takvim (çalışma günü/tatil) tanımlama | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir (§5.5 "sınıf oluşturma/düzenleme" ile aynı yönetim kategorisinde sayılmıştır — bkz. bölüm 4) | §6.2, §8.2, KURUM-02 |

### 3.2. Sınıf yönetimi

| İşlem | Platform yöneticisi | Kurum yöneticisi | Hoca (varsayılan) | Hoca için durum kategorisi | Kaynak |
|---|---|---|---|---|---|
| Sınıf görüntüleme (kendi/atanmış sınıf) | Evet | Evet (kendi kurumu) | Evet (yalnızca atanmış sınıf) | Varsayılan açık — yönetici geri alabilir (bkz. §2.2 madde 12) | §5.5, HOCA-02 |
| Sınıf oluşturma/düzenleme/arşivleme | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir | §5.5, §6.3, KURUM-03 |
| Hoca–sınıf ataması | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 4); yalnızca aynı kurum içi atamalarda kullanılabilir — bkz. bölüm 3.3, bölüm 4.1 güvenlik sınırları | §5.2, §5.3, KURUM-05 |

### 3.3. Kullanıcı ve yetki yönetimi

Bu bölüm, önceki sürümde tek bir "kullanıcı/yetki yönetimi" kararı altında toplanan işlemi beş
ayrı işleme böler. Beşi de kullanıcı tarafından 13 Temmuz 2026'da onaylanmış bağlayıcı V1
kararlarına göre işaretlenmiştir; hiçbiri artık varsayım veya bekleyen karar değildir.

| İşlem | Platform yöneticisi | Kurum yöneticisi | Hoca (varsayılan) | Hoca için durum kategorisi | Kaynak |
|---|---|---|---|---|---|
| Hoca hesabı oluşturma/kapatma | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı **personel yönetimi izniyle** açılabilir (13 Temmuz 2026 onaylı karar 3); bu izin başka kullanıcılara izin verme yetkisi sağlamaz — bkz. bölüm 4.1. İşlem denetim kaydı üretmelidir (bkz. bölüm 4.5) | §5.2, §5.3, §5.5, §8.3, KURUM-04 |
| Hoca–sınıf ataması | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 4); yalnızca aynı kurum içindeki atamalarda kullanılabilir — bkz. bölüm 4.1. İşlem denetim kaydı üretmelidir (bkz. bölüm 4.5) | §5.2, §5.3, KURUM-05 |
| Hoca izinlerini görüntüleme | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 5); görüntüleme izni, izin değiştirme yetkisi sağlamaz | §5.2, §5.3, §5.5 |
| Hoca izinlerini değiştirme (verme/geri alma) | Evet (destek amaçlı erişimde) | Evet | Hayır | **Mutlak sınır** — V1'de hocaya hiçbir izinle devredilemez; yalnızca kurum yöneticisi kendi kurumu içinde hoca izinlerini yönetebilir (13 Temmuz 2026 onaylı karar 6, §2.2 madde 6) | §5.2, §5.3, §5.5, KURUM-05 |
| Kullanıcının bütün cihaz oturumlarını iptal etme | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 7); yalnızca aynı kurumun kullanıcılarında kullanılabilir. İşlem denetim kaydı üretmelidir (bkz. bölüm 4.5) | §8.1, §8.3, KURUM-12 |
| Kendi oturumundan çıkış yapma / kendi cihazını yönetme | Evet | Evet | Evet | Kapsam dışı — her aktörün kendi hesabı için varsayılan hakkıdır, devredilebilirlik sorusu bu işlem için geçerli değildir | §8.1, ORTAK-01 |

### 3.4. Öğrenci ve veli (anne/baba) yönetimi

| İşlem | Platform yöneticisi | Kurum yöneticisi | Hoca (varsayılan) | Hoca için durum kategorisi | Kaynak |
|---|---|---|---|---|---|
| Öğrenci görüntüleme (atanmış sınıftaki) | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Evet (yalnızca atanmış sınıf) | Varsayılan açık — yönetici geri alabilir (bkz. §2.2 madde 12) | §5.5, HOCA-07 |
| Öğrenci oluşturma/düzenleme/arşivleme | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir | §5.5, §7.2, §8.4, KURUM-06 |
| Anne/baba bilgisi yönetme | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir (öğrenci yönetimiyle aynı kategoride — bkz. bölüm 4) | §5.2, §7.3, §8.4, KURUM-06 |
| Veli iletişim bilgisi görüntüleme | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 9). Hoca bu izinle **yalnızca kendisine atanmış sınıflardaki** öğrencilerin anne/baba iletişim bilgilerini görebilir. **Öğrenci görüntüleme izni bu izni otomatik olarak sağlamaz** — ikisi bağımsız izinlerdir | §5.5, §8.4, HOCA-07 |
| Arşivlenmiş öğrenci/sınıf kaydını geri yükleme | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir (öğrenci/sınıf yönetimiyle aynı kategoride — bkz. bölüm 4) | §5.2, §14, KURUM-11 |

### 3.5. Yoklama

| İşlem | Platform yöneticisi | Kurum yöneticisi | Hoca (varsayılan) | Hoca için durum kategorisi | Kaynak |
|---|---|---|---|---|---|
| Günlük yoklama alma (Geldi/Gelmedi/kuruma özel durum) | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Evet (yalnızca atanmış sınıf) | Varsayılan açık — yönetici geri alabilir (bkz. §2.2 madde 12) | §5.5, §8.5, HOCA-03 |
| Toplu "hepsi geldi" işlemi | Evet (destek amaçlı erişimde) | Evet | Evet (yalnızca atanmış sınıf) | Varsayılan açık — yönetici geri alabilir (yoklama almanın bir biçimidir) | §8.5, HOCA-04 |
| Geçmiş tarihli yoklama düzeltme | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir (§5.5 "yoklama alma/düzeltme"yi ayrı işlem olarak sayar) | §5.5, §8.5, HOCA-05 |
| Kuruma özel yoklama durumu tanımlama (Geç geldi, İzinli, Hasta vb.) | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı ve geri alınabilir izinle açılabilir (13 Temmuz 2026 onaylı karar 8); kurum kapsamlı bir ayar iznidir (bkz. bölüm 4.4). İşlem denetim kaydı üretmelidir (bkz. bölüm 4.5) | §8.5, KURUM-07 |

### 3.6. Program, içerik ve ilerleme

| İşlem | Platform yöneticisi | Kurum yöneticisi | Hoca (varsayılan) | Hoca için durum kategorisi | Kaynak |
|---|---|---|---|---|---|
| Program görüntüleme | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Evet (yalnızca atanmış sınıf) | Varsayılan açık — yönetici geri alabilir (bkz. §2.2 madde 12) | §5.5, HOCA-06 |
| Program oluşturma/yönetme (yapılandırma) | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir | §5.5, §8.6–§8.7, KURUM-07 |
| Değerlendirme şeması ayarlama (puan/not/tekrar alanları) | Evet (destek amaçlı erişimde) | Evet | Hayır | Varsayılan kapalı — ayrı izinle açılabilir (program yönetimiyle aynı kategoride — bkz. bölüm 4) | §8.8, KURUM-08 |
| İlerleme kaydetme/düzeltme (tamamlandı/puan/not/tekrar) | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Evet (yalnızca atanmış sınıf) | Varsayılan açık — yönetici geri alabilir (bkz. §2.2 madde 12) | §5.5, §8.8, HOCA-06 |
| Aynı sınıftaki diğer hocanın normal notunu görüntüleme | Evet (destek amaçlı erişimde) | Evet | Evet (yalnızca atanmış sınıf) | Varsayılan açık — yönetici geri alabilir (bkz. §2.2 madde 12) | §5.3, §8.8, HOCA-08 |

### 3.7. Rapor ve denetim

Aşağıdaki üç işlem birbirinden bağımsız izin paketleridir: birinin varsayılan/devredilebilirlik
durumu diğerini otomatik olarak belirlemez.

| İşlem | Platform yöneticisi | Kurum yöneticisi | Hoca (varsayılan) | Hoca için durum kategorisi | Kaynak |
|---|---|---|---|---|---|
| Rapor dışa aktarma (Excel) | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Hayır | Varsayılan kapalı — ayrı izinle açılabilir | §5.5, §8.9, KURUM-09 |
| Kurum içi işlem geçmişi (denetim kaydı) görüntüleme | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Hayır | Varsayılan kapalı — ayrı izinle açılabilir | §5.5, §8.10, KURUM-10 |
| Desteklenen işlemi geri alma | Evet (destek amaçlı erişimde) | Evet (kendi kurumu) | Hayır | Varsayılan kapalı — ayrı izinle açılabilir. **Bu, hedef işlemi yapma izninden ayrı bir izindir** (bkz. bölüm 4.2); bir hocanın yoklama düzeltme iznine sahip olması, ona otomatik olarak yoklama geri alma izni vermez. Kesin geri alma kuralları `P-011`'de tanımlanacaktır | §5.2, §8.10, KURUM-10 |

---

## 4. Devredilebilirlik notları

### 4.1. Personel yönetimi izninin güvenlik sınırları (onaylı V1 kararı)

Kullanıcı 13 Temmuz 2026 tarihinde, "Hoca hesabı oluşturma/kapatma" ve "Hoca–sınıf ataması"
işlemlerinin kurum yöneticisi tarafından hocaya ayrı personel yönetimi izniyle açılabileceğini,
ancak "Hoca izinlerini görüntüleme/değiştirme"den yalnızca **görüntülemenin** aynı şekilde ayrı
izinle açılabileceğini, **izin değiştirmenin ise V1'de hiçbir izinle hocaya devredilemeyeceğini**
onaylamıştır (bölüm 6.2, kararlar 3, 4, 5, 6). Bu artık bağlayıcı V1 kararıdır; aşağıdaki
sınırlar personel yönetimi izni verilen her hoca için zorunludur:

- Personel yönetimi yetkisi verilen hoca **kendi yetkisini değiştiremez**.
- **Kendisini yeni bir sınıfa atayarak veya başka bir yolla kendi erişim kapsamını
  (atandığı sınıf/kurum sınırını) genişletemez.**
- **Hoca, V1'de hiçbir izni verme veya değiştirme yetkisine sahip değildir** — ne kendisine ne
  başka bir kullanıcıya izin atayabilir, ne mevcut bir izni değiştirebilir (§2.2 madde 6, madde
  7). Personel yönetimi izinleri (hesap oluşturma, sınıf ataması, izin görüntüleme) bu genel
  yasağın istisnası değildir; bu izinler yalnızca aşağıdaki dar eylemleri kapsar.
- Hoca–sınıf ataması izni **yalnızca aynı kurum içinde ve başka kullanıcılar üzerinde**
  kullanılabilir; **kendi üzerinde** (kendi sınıf atamasını değiştirme) işlem yapamaz ve **kendi
  kurum/sınıf sınırının dışına** atama yapamaz.
- **Platform yöneticisi veya kurum yöneticisi rolü/yetkisi veremez.**
- **Kurum yöneticisi rolü ataması**, ilk sürümde yalnızca platform yöneticisine aittir (§2.2
  madde 8); personel yönetimi yetkisi verilen bir hoca da dahil, hiçbir kurum içi rol bu atamayı
  yapamaz.
- **Hoca izinlerini görüntüleme yetkisi, izin değiştirme hakkı sağlamaz.** İzin değiştirme
  (verme/geri alma) V1'de yalnızca kurum yöneticisinde kalır (§2.2 madde 6); personel yönetimi
  izni bu mutlak sınırı delmez.
- **Kurum yöneticisi de sınırsız değildir:** Kurum yöneticisinin izin verme/atama yetkisi
  yalnızca kendi kurumuyla ve kendisinde bulunan yetki tavanıyla sınırlıdır; kurum yöneticisi
  kendisinde olmayan bir izni (örn. platform yöneticisi yetkisi) hiçbir kullanıcıya veremez ve
  başka bir kurumda atama yapamaz (§2.2 madde 2, madde 7).

### 4.2. Sınıf ataması, işlem izni ve geri alma izninin bağımsızlığı

Bölüm 2.2 madde 10 ve 12'de belirtildiği gibi, üç kavram birbirinden bağımsız olarak
değerlendirilmelidir:

1. **Sınıf ataması** — hangi sınıfın veri kapsamına erişilebileceğini belirler.
2. **İşlem izni** — o kapsam içinde hangi eylemin (örn. yoklama düzeltme) yapılabileceğini
   belirler.
3. **Geri alma izni** — bir işlemin geri alınıp alınamayacağını belirler; işlem iznine sahip
   olmak otomatik olarak geri alma izni vermez.

Bir işlem yapılabilmesi için ilgili sınıf ataması ve ilgili işlem izni birlikte sağlanmalıdır.
Bir işlemin geri alınabilmesi için kullanıcının hem hedef işlemin kapsamına erişebilmesi hem de
ayrı geri alma iznine sahip olması gerekir; ayrıca yalnızca desteklenen işlem türleri geri
alınabilir. Kesin geri alma kuralları `P-011 — Denetim ve geri alma ilkelerini detaylandır`
görevinde tanımlanacaktır; bu belge bunu bağlayıcı olarak kesinleştirmez.

### 4.3. Devredilebilir izin kategorileri (varsayılan kapalı, ayrı izinle açılabilir olanlar)

`AKTORLER_VE_KULLANIM_SENARYOLARI.md` §4.2'ye göre kurum yöneticisi hocaya izinleri **ayrı ayrı**
verir; rol adına göre toptan yetki devri yoktur. Bölüm 3'teki "Varsayılan kapalı — ayrı izinle
açılabilir" işaretli işlemler beş kategoriye ayrılır; kurum yöneticisi bu kategorilerden
istediğini bağımsız olarak açabilir:

1. **Sınıf/öğrenci/veli yönetimi:** Sınıf oluşturma/düzenleme/arşivleme, öğrenci
   oluşturma/düzenleme/arşivleme, anne/baba bilgisi yönetme, arşivlenmiş kayıt geri yükleme,
   eğitim dönemi/takvim tanımlama, veli iletişim bilgisi görüntüleme.
2. **Program yönetimi:** Program oluşturma/yönetme, değerlendirme şeması ayarlama.
3. **Yoklama düzeltme ve raporlama:** Geçmiş tarihli yoklama düzeltme, rapor dışa aktarma,
   kurum içi işlem geçmişi görüntüleme, desteklenen işlemi geri alma (geri alma izni bölüm
   4.2'de açıklandığı gibi ayrı bir izindir; burada aynı kategoride sayılması yönetim kolaylığı
   içindir, otomatik bağlanma anlamına gelmez).
4. **Kurum kapsamlı ayar yönetimi (13 Temmuz 2026 onaylı):** Kurum adı/logo/renk (marka)
   ayarı, kurumda etkin modülleri belirleme, kuruma özel yoklama durumu tanımlama. Bu kategori
   bölüm 4.4'teki kısıtlamaya tabidir.
5. **Personel yönetimi (13 Temmuz 2026 onaylı, bölüm 4.1'deki sınırlarla):** Hoca hesabı
   oluşturma/kapatma, hoca–sınıf ataması, hoca izinlerini görüntüleme, kullanıcının cihaz
   oturumlarını iptal etme. "Hoca izinlerini değiştirme" bu kategoride **değildir**; o mutlak
   sınırdır (§2.2 madde 6).

Bu kategorilendirme, `P-008` çekirdek veri modeli görevinde izin şemasının (örn. sabit enum mu,
genişletilebilir izin listesi mi) tasarımına başlangıç girdisidir; bağlayıcı şema kararı
`P-008`'e aittir.

### 4.4. Kurum kapsamlı yönetim izni ile operasyonel sınıf verisi erişiminin ayrımı

**Operasyonel veri** (yoklama kaydı, ilerleme kaydı, öğrenci/veli bilgisi, değerlendirme, normal
öğretmen notu) yalnızca hocanın **atanmış olduğu sınıf(lar)da** erişilebilir; bu, §2.2 madde
3'teki mutlak sınırdır ve kurum kapsamlı bir yönetim izniyle genişletilemez.

Buna karşılık, 4.3 kategori 4 ve 5'teki onaylı yönetim izinleri (hoca–sınıf ataması, marka
yönetimi, modül yönetimi, kuruma özel yoklama durumu tanımlama, hoca hesabı oluşturma, cihaz
oturumu iptali) **kurum kapsamlı ayrı izinlerdir** ve operasyonel veriden ayrı bir veri sınıfına
erişir. Bu izinlerden birine sahip olmak, hocaya kurumun **bütün sınıflarındaki** operasyonel
veriyi (öğrenci/veli/yoklama/ilerleme) görme hakkı **vermez** (§2.2 madde 3, madde 11). Hoca bu
izinlerle yalnızca işlemin yapılması için zorunlu, sınırlı kurum/sınıf **metaverisine** (örn.
sınıf listesi, kurum ayarları, hoca listesi) erişebilir; öğrenci detay verisi, yoklama kaydı veya
ilerleme kaydı bu metaveri kapsamında **değildir** ve bunlara erişim her koşulda ayrıca sınıf
ataması + ilgili operasyonel işlem izni gerektirir (§2.2 madde 10).

Kesin metaveri alanları (örn. bir yönetim API'sinin hangi alanları döndüreceği) `P-008` ve
`P-009`'da tanımlanacaktır; bu belge yalnızca ilkeyi kaydeder.

### 4.5. Devredilmiş yönetim işlemlerinde denetim zorunluluğu

Aşağıdaki devredilmiş/devredilebilir yönetim işlemleri, kim tarafından yapılırsa yapılsın
denetim kaydı üretmelidir (§8.10 genel denetim gereksinimiyle uyumlu, burada 13 Temmuz 2026
onayıyla bu işlemler için ayrıca teyit edilmiştir):

- Marka/kurum görünümü değişikliği.
- Etkin modül değişikliği.
- Hoca hesabı oluşturma/kapatma.
- Hoca–sınıf ataması.
- Başka kullanıcının cihaz oturumunu iptal etme.
- Kuruma özel yoklama durumu değişikliği.

Veli iletişim bilgisine erişim için en azından erişim politikasının/log seviyesinin ne olacağı
bağlayıcı olarak belirlenmemiştir; bu, `P-011 — Denetim ve geri alma ilkelerini detaylandır`
görevinde değerlendirilecektir.

---

## 5. Ana ürün planıyla uyum kontrolü

- Bölüm 2 ve 3'teki rol tanımları ve varsayılan yetkiler `URUN_VE_UYGULAMA_PLANI.md` §5.1–§5.3
  ile birebir uyumludur.
- Bölüm 3'teki her satır §5.5'teki yetki örnekleri listesinin en az bir kalemine karşılık gelir;
  §5.5'te sayılan on iki eylemin tamamı matriste bulunur.
- "İzin verilmediyse erişim yok" ilkesi (§5.5) bölüm 2.2 madde 1'de değişmez ilke olarak yeniden
  ifade edilmiştir ve hiçbir satırda çelişmez.
- Platform yöneticisinin kurum verisine erişiminin denetim kaydı üretmesi (§5.1) bölüm 2.2
  madde 4 ve bütün "destek amaçlı erişimde" notlu hücrelerde tutarlı biçimde yansıtılmıştır.
- Kurum yöneticisinin kurum dışına erişemediği (§4.2, §15) ve hocanın sınıf bazlı sabit sınırı
  (§5.3) bölüm 2.2 madde 2 ve 3'te platform yöneticisi istisnasıyla (madde 4) çelişmeyecek
  biçimde ayrı ayrı ifade edilmiştir.
- Ana planda ilk sürümde açık dayanağı olmayan dokuz devredilebilirlik sorusu (kurum ayarları
  alt işlemleri, personel yönetimi, kuruma özel yoklama durumu tanımlama, veli iletişim bilgisi
  görüntüleme), kullanıcı tarafından 13 Temmuz 2026'da onaylanmış ve bölüm 6.2'ye bağlayıcı V1
  kararları olarak işlenmiştir; hiçbiri dayanaksız biçimde "Mutlak sınır" ilan edilmemiştir —
  yalnızca kullanıcı tarafından gerçekten "V1'de hocaya hiçbir izinle devredilemez" olarak
  onaylanan tek işlem (hoca izinlerini değiştirme) "Mutlak sınır" kategorisindedir.
- Kullanım senaryosu kimlikleri (`PLAT-*`, `KURUM-*`, `HOCA-*`, `ORTAK-*`)
  `AKTORLER_VE_KULLANIM_SENARYOLARI.md` ile birebir aynıdır; bu belge yeni bir kimlik şeması
  icat etmez.
- Terim kullanımı `TERIMLER_SOZLUGU.md` ile tutarlıdır.
- Belgede ana plana veya önceki Dalga 0 belgelerine aykırı bir tanım bulunmamaktadır.

---

## 6. Varsayımlar ve onaylanan V1 kararları

### 6.1. Varsayımlar (ana planla aynı yönde netleştirme, kesin dayanağı olan)

- **Kategorilendirme (bölüm 4.3):** §5.5'te ayrı satır olarak sayılmayan bazı "varsayılan
  kapalı, ayrı izinle açılabilir" işlemler (değerlendirme şeması ayarlama, arşivlenmiş kayıt geri
  yükleme, anne/baba yönetimi, eğitim dönemi/takvim tanımlama) en yakın kapsadıkları ana
  kategoriyle aynı devredilebilirlik durumuna atanmıştır. Bağlayıcı izin şeması `P-008`'de
  kesinleşecektir.
- "Evet (destek amaçlı erişimde)" notu, platform yöneticisinin teknik olarak bu işlemi de
  yapabildiğini, ancak bunun normal iş akışı değil istisnai destek erişimi olduğunu ve her
  durumda denetim kaydı ürettiğini belirtir; ayrı bir kalıcı rol tanımlamaz.
- Kesin teknik veri filtreleri (bölüm 4.4) ve kesin denetim log seviyesi/politikaları (bölüm
  4.5, özellikle veli iletişim bilgisi erişimi için) `P-008`, `P-009` ve `P-011`'de
  kesinleşecektir; bu belge yalnızca ilkeyi ve gereksinimi kaydeder.

### 6.2. Onaylanan V1 kararları (13 Temmuz 2026)

İlk sürümde (bölüm sürüm 1.1) aşağıdaki dokuz işlemin hocaya devredilip devredilemeyeceği ana
planda açık dayanaksız "bekleyen ürün kararı" olarak işaretlenmişti. Kullanıcı bu dokuz kararı
**13 Temmuz 2026** tarihinde onaylamıştır. Bunlar artık varsayım veya öneri değil, **bağlayıcı
V1 ürün kararlarıdır** ve bölüm 3'teki matrise işlenmiştir. Aynı özet
`URUN_VE_UYGULAMA_PLANI.md` bölüm 26 (Karar günlüğü) içine de eklenmiştir.

1. **Kurum adı, logo ve renk yönetimi:** Hoca için varsayılan kapalı; kurum yöneticisi
   tarafından ayrı ve geri alınabilir izinle açılabilir (bkz. bölüm 3.1).
2. **Kurumda etkin modülleri yönetme:** Hoca için varsayılan kapalı; ayrı ve geri alınabilir
   izinle açılabilir (bkz. bölüm 3.1).
3. **Hoca hesabı oluşturma/kapatma:** Hoca için varsayılan kapalı; ayrı **personel yönetimi
   izniyle** açılabilir. Bu izin başka kullanıcılara izin verme yetkisi sağlamaz (bkz. bölüm
   3.3, bölüm 4.1).
4. **Hoca–sınıf ataması:** Hoca için varsayılan kapalı; ayrı ve geri alınabilir izinle
   açılabilir. Bu izin yalnızca aynı kurum içindeki atamalarda kullanılabilir (bkz. bölüm 3.2,
   3.3, bölüm 4.1).
5. **Hoca izinlerini görüntüleme:** Hoca için varsayılan kapalı; ayrı ve geri alınabilir izinle
   açılabilir. Görüntüleme izni, izin değiştirme yetkisi sağlamaz (bkz. bölüm 3.3).
6. **Hoca izinlerini değiştirme/verme/geri alma:** V1'de hocaya hiçbir izinle devredilemez.
   Yalnızca kurum yöneticisi kendi kurumu içinde hoca izinlerini yönetebilir. Kurum yöneticisi
   rolünü atama yetkisi yalnızca platform yöneticisinde kalır. Hiçbir hoca kendi veya başka bir
   hocanın izinlerini değiştiremez. Bu, V1 için bağlayıcı **mutlak sınırdır** (bkz. bölüm 2.2
   madde 6, bölüm 3.3).
7. **Başka kullanıcının cihaz oturumlarını iptal etme:** Hoca için varsayılan kapalı; ayrı ve
   geri alınabilir izinle açılabilir. Yalnızca aynı kurumun kullanıcılarında kullanılabilir.
   İşlem denetim kaydı üretmelidir (bkz. bölüm 3.3, bölüm 4.5).
8. **Kuruma özel yoklama durumu tanımlama:** Hoca için varsayılan kapalı; ayrı ve geri alınabilir
   izinle açılabilir. Kurum kapsamlı bir ayar iznidir. İşlem denetim kaydı üretmelidir (bkz.
   bölüm 3.5, bölüm 4.4, bölüm 4.5).
9. **Veli iletişim bilgisi görüntüleme:** Hoca için varsayılan kapalı; ayrı ve geri alınabilir
   izinle açılabilir. Hoca yalnızca kendisine atanmış sınıflardaki öğrencilerin anne/baba
   iletişim bilgilerini görebilir. Öğrenci görüntüleme izni, veli iletişim bilgilerini otomatik
   olarak göstermez (bkz. bölüm 3.4).

Kararlar 3, 4 ve 5 hocaya devredilebilir personel yönetimi izinleridir; bunlar kullanılırken
bölüm 4.1'deki güvenlik sınırları (kendi yetkisini değiştirememe, kapsam genişletememe, sahip
olmadığı izni verememe, kurum/sınıf dışına atama yapamama, platform/kurum yöneticisi yetkisi
verememe) zorunlu olarak uygulanır. Karar 6 bu üç iznin dışındadır ve hiçbir koşulda hocaya
devredilmez.

## 7. Bilinen sınırlamalar

- Bu matris işlem düzeyinde durum kategorisini gösterir; izinlerin veritabanı şemasında nasıl
  saklanacağı (enum, izin tablosu, bit alanı vb.) `P-008` kapsamındadır.
- Eski sistem (Excel/HTML/Apps Script) bu repoda bulunmadığından, yetki matrisinin eski sistemle
  karşılaştırması yapılmamıştır; bu belge yalnızca onaylı ana plana ve önceki Dalga 0
  belgelerine dayanır.
- Matris platform yöneticisinin "destek amaçlı erişim" dışındaki günlük operasyonel yetkilerini
  (örn. sistem sağlığı izleme) kapsamaz; bu ana planın ürün yetki modeli kapsamında değildir.
- Bölüm 6.2'deki dokuz kararın onaylanmasıyla, matriste artık "Bekleyen ürün kararı" işaretli
  satır bulunmamaktadır; bu kategori ileride ana planda dayanağı olmayan yeni bir işlem
  eklenirse kullanılmak üzere bölüm 2.1'de tanımlı kalmıştır.
- Bu matris, bölüm 4.4'te tanımlanan "kurum kapsamlı yönetim izni sınıf verisine toplu erişim
  sağlamaz" ilkesinin veri erişim katmanında nasıl uygulanacağını (örn. hangi API'nin hangi
  alanları filtreleyeceği) belirtmez; bu `P-008`/`P-009` kapsamındadır.

## 8. Kapsam dışı bırakılanlar

- Yetki değerlendirme servisinin teknik tasarımı ve veri modeli (`P-008`, `PERM-002` kapsamı).
- API düzeyinde yetki kontrolünün uygulanması (`PERM-*`, `STAFF-*` uygulama görevleri kapsamı).
- Kurum/sınıf izolasyonu güvenlik testlerinin yazılması (`PERM-003` kapsamı).
- Geri alma işleminin kesin kuralları ve desteklenen işlem türleri listesi (`P-011` kapsamı).
- Öğrenci ve veli (anne/baba/vasi) için gelecekteki yetki matrisi (sonraki faz, Dalga 8
  `PORTAL-001`).
- Ekran bazında hangi butonun görüneceği (bilgi mimarisi ve ekran envanteri — `P-005`, `P-006`,
  `P-007` kapsamı).
- Bölüm 4.4 ve 4.5'te belirtilen kesin teknik veri filtresi ve denetim log seviyesi tasarımı
  (`P-008`, `P-009`, `P-011` kapsamı).
