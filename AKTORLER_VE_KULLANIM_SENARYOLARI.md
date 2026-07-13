# Aktörler ve Ana Kullanım Senaryoları

| Alan | Değer |
|---|---|
| Görev | P-002 — Aktörleri ve ana kullanım senaryolarını listele |
| Belge sürümü | 1.0 |
| Ana sözleşme | `URUN_VE_UYGULAMA_PLANI.md` |
| Terim kaynağı | `TERIMLER_SOZLUGU.md` |
| Son güncelleme | 13 Temmuz 2026 |

---

## 1. Amaç ve kapsam

Bu belge, ürün ve mimari planında (`URUN_VE_UYGULAMA_PLANI.md`) tanımlanan ilk sürüm
aktörlerini ve bu aktörlerin gerçekleştirdiği ana kullanım senaryolarını tek bir yerde
listeler. Amacı, yönetici (platform/kurum) ve hoca işlerinin sınırını — hangi işin varsayılan
olarak kime ait olduğunu ve hangi işin yetkiyle devredilebildiğini — açıkça göstermektir.

Bu belge yeni bir ürün kararı almaz; ana plandaki rol tanımları (bölüm 5), kurum/sınıf/kişi
modeli (bölüm 6–7), işlevsel modüller (bölüm 8), kişiselleştirme çerçevesi (bölüm 9) ve ana
kullanıcı akışlarından (bölüm 10) derlenmiştir.

`AGENTS.md` uyarısına göre roller sabit ve kapalı bir kalıba mahkûm edilmemiştir: bir kullanıcı
birden fazla rol atamasına sahip olabilir ve hoca yetkileri kurum yöneticisi tarafından
bağlama göre genişletilip kısıtlanabilir (`TERIMLER_SOZLUGU.md` — "Kullanıcı" ve "Hoca"
terimleri). Bu belgedeki sınırlar bu nedenle **varsayılan** sınırlardır, sabit duvar değildir;
bkz. bölüm 4.

Bu belge ayrıntılı eylem × rol yetki matrisini üretmez (bu, `P-003` kapsamıdır); yalnızca
aktörleri ve ana senaryoları listeler.

---

## 2. İlk sürüm aktörleri

| Aktör | Tanım | Erişim kapsamı | Kaynak |
|---|---|---|---|
| Platform yöneticisi | Ürün sahibi; sistemi kuran ve bütün kurumları denetleyen global rol. | Platform geneli — bütün kurum ve sınıflar. | `URUN_VE_UYGULAMA_PLANI.md` §5.1 |
| Kurum yöneticisi | Bir kurumun kendi işleyişini yöneten rol. | Tek kurum — kendi kurumunun bütün sınıf ve verileri. | `URUN_VE_UYGULAMA_PLANI.md` §5.2 |
| Hoca | Bir veya birden fazla sınıfa atanmış, günlük eğitim/takip işlerini yürüten rol. | Yalnızca kendisine atanmış sınıf(lar). | `URUN_VE_UYGULAMA_PLANI.md` §5.3 |

İlk sürümde giriş yapan ve işlem gerçekleştiren aktörler yukarıdaki üçüdür. "Kullanıcı" başlı
başına ayrı bir aktör değildir; bir kişinin sisteme giriş yapabilen hesap hâlidir ve bu üç
rolden bir veya birden fazlasını taşıyabilir (`TERIMLER_SOZLUGU.md` §3.1).

### 2.1. İlk sürümde giriş yapmayan, gelecekteki aktörler

| Aktör | Durum | Kaynak |
|---|---|---|
| Öğrenci | Kişi kaydı vardır, giriş hesabı yoktur; kendi görünümü sonraki fazdadır. | `URUN_VE_UYGULAMA_PLANI.md` §5.4, §20; Dalga 8 `PORTAL-006` |
| Anne / Baba (Veli) | Ayrı kişi kayıtlarıdır, giriş hesabı yoktur; veli girişi sonraki fazdadır. | `URUN_VE_UYGULAMA_PLANI.md` §5.4, §7.3, §20; Dalga 8 `PORTAL-*` |
| Vasi | Ana planda "gerekirse" ihtimali olarak anılır; ilk sürümde tanımlı bir kayıt türü değildir. | `URUN_VE_UYGULAMA_PLANI.md` §5.4 |

Bu aktörlerin veri modeli (kişi ve ilişki kaydı) ilk sürümden itibaren hazırdır; yalnızca giriş
ve kendi kullanım senaryoları ilk sürüm kapsamına alınmamıştır. Ayrıntılı senaryoları bu
belgenin kapsamı dışıdır (bkz. bölüm 7).

---

## 3. Ana kullanım senaryoları

Her senaryo kimliği, kısa özeti ve ana plandaki kaynağı ile listelenmiştir. Sıralama; bir
işin sıklığı veya önemi hakkında sıralama iması taşımaz.

### 3.1. Platform yöneticisi

| Kimlik | Senaryo | Özet | Kaynak |
|---|---|---|---|
| PLAT-01 | Kurum oluşturma | Yeni bir kurum kaydı açar (ilk sürümde tek edinme yöntemi). | §4.2, §5.1, §8.2 |
| PLAT-02 | Kurum yöneticisi atama | Oluşturulan kuruma kurum yöneticisi atar. | §5.1, §8.2 |
| PLAT-03 | Kurum durumu yönetimi | Kurumu etkinleştirir, askıya alır veya arşivler. | §5.1, §6.1 |
| PLAT-04 | Destek amaçlı kurum bağlamına geçiş | Destek amacıyla bir kurumun verisine erişir; bu erişim ayrıca denetim kaydına yazılır. | §5.1 |
| PLAT-05 | Sistem geneli denetim kaydı inceleme | Kurumlar arası karıştırmadan platform genelindeki denetim kayıtlarına erişir. | §5.1, §8.10 |
| PLAT-06 | Kurumlar arası raporlama | Kurumların verilerini birbirine karıştırmadan raporlar. | §5.1 |

### 3.2. Kurum yöneticisi

| Kimlik | Senaryo | Özet | Kaynak |
|---|---|---|---|
| KURUM-01 | Kurum kimliği ve marka ayarı | Kurum adı, logo, ana ve yardımcı renkleri değiştirir. | §5.2, §8.2, §9.1 |
| KURUM-02 | Eğitim dönemi ve takvim tanımlama | Dönem, çalışma günleri ve tatilleri tanımlar. | §6.2, §8.2 |
| KURUM-03 | Sınıf oluşturma ve arşivleme | Sınıf açar; gerektiğinde arşivler (geçmiş kayıtlar korunur). | §6.3, §8.2 |
| KURUM-04 | Hoca hesabı oluşturma/kapatma | Hoca hesabı açar, geçici giriş bilgisi verir, hesabı askıya alır. | §5.2, §8.3 |
| KURUM-05 | Hoca sınıf ataması ve yetki tanımlama | Hocanın görebileceği sınıfları ve ayrıntılı izinlerini belirler/geri alır. | §5.2, §5.3, §8.3 |
| KURUM-06 | Öğrenci ve veli bilgisi yönetimi | Öğrenci ekler/düzenler/arşivler; anne ve baba bilgilerini yönetir. | §5.2, §7.2, §7.3, §8.4 |
| KURUM-07 | Program ve takip düzeni kurma | Kuruma özgü program/ezber takip türlerini yapılandırır. | §5.2, §8.6–§8.7 |
| KURUM-08 | Değerlendirme şeması ayarlama | Bir programa puan, not, tekrar gerekli gibi isteğe bağlı alanları ekler. | §8.8 |
| KURUM-09 | Rapor üretme ve Excel dışa aktarma | Kurum/sınıf/öğrenci ve tarih bazlı Excel raporu talep eder. | §5.2, §8.9 |
| KURUM-10 | İşlem geçmişi inceleme ve geri alma | Kurum denetim kayıtlarını görür; desteklenen işlemleri geri alır. | §5.2, §8.10 |
| KURUM-11 | Arşivlenmiş kayıt geri yükleme | Arşivlenmiş öğrenci/sınıf gibi kayıtları geri yükler. | §5.2, §14 |
| KURUM-12 | Cihaz oturumu iptali | Bir kullanıcının bütün cihaz oturumlarını kapatır. | §8.1, §8.3 |

### 3.3. Hoca

| Kimlik | Senaryo | Özet | Kaynak |
|---|---|---|---|
| HOCA-01 | Oturum açma ve sürdürme | Giriş yapar; güvenilir cihazda tekrar bilgi istenmeden oturumu sürdürür. | §8.1, §10.1 |
| HOCA-02 | Yetkili sınıfları görüntüleme | Yalnızca kendisine atanmış sınıfları görür. | §5.3, §10.2 |
| HOCA-03 | Günlük yoklama alma | Geldi/Gelmedi veya kuruma özel ek durumu öğrenci bazında işaretler. | §8.5, §10.4 |
| HOCA-04 | Toplu "hepsi geldi" işlemi | Bütün sınıfı tek işlemle "geldi" olarak işaretler. | §8.5 |
| HOCA-05 | Geçmiş tarihli yoklama düzeltme | Yetkiliyse geçmiş tarihli yoklamayı değiştirir; eski/yeni değer korunur. | §8.5 |
| HOCA-06 | Program/ilerleme kaydetme | Programa göre tamamlandı/tamamlanmadı ve isteğe bağlı puan/not/tekrar girer. | §8.8, §10.2 |
| HOCA-07 | Öğrenci bilgisi görüntüleme | Yetkili olduğu sınıftaki öğrenci ve veli iletişim bilgilerini görür (varsayılan izin gerektirir). | §5.5, §8.4 |
| HOCA-08 | Diğer hocaların notunu görüntüleme | Aynı sınıftaki diğer hocanın öğrenciye yazdığı normal notu görür. | §5.3, §8.8 |
| HOCA-09 | Genişletilmiş yönetim işleri (yetkiliyse) | Yönetici tarafından verilmişse öğrenci, sınıf veya program yönetimi işlemlerini yapar. | §5.3, §5.5 |

### 3.4. Bütün aktörler için ortak senaryo

| Kimlik | Senaryo | Özet | Kaynak |
|---|---|---|---|
| ORTAK-01 | İlk giriş ve cihaz eşleştirme | Kullanıcı verilen bilgilerle giriş yapar, gerekiyorsa parola değiştirir, cihaz güvenilir cihaz olur. | §10.1 |
| ORTAK-02 | Eşitleme durumunu görme | Kullanıcı yaptığı işlemin bekliyor/başarılı/başarısız olduğunu görür. | §3.1, §3.4, §13 |

---

## 4. Yönetici ve hoca işlerinin sınırı

Bu sınır **kapalı bir rol duvarı değil, yetkiyle genişleyebilen varsayılan bir ayrımdır**
(`AGENTS.md` — rol ve yetkileri bağlama göre ele alma ilkesi).

### 4.1. Varsayılan sınır

- Kurum yöneticisi kurum çapındaki yapılandırma işlerinin (marka, dönem, sınıf açma, hoca
  hesabı, yetki dağıtımı, rapor) varsayılan sahibidir.
- Hoca varsayılan olarak yalnızca kendisine atanmış sınıf(lar)ın günlük işlerini (yoklama,
  program ilerlemesi) yürütür.
- Hoca varsayılan olarak başka hoca oluşturamaz veya yetkilendiremez (`URUN_VE_UYGULAMA_PLANI.md`
  §5.3).
- Platform yöneticisi bütün kurumlara erişebilir; ancak kurum verisine her erişimi denetim
  kaydı üretir (§5.1) — bu, platform yöneticisinin sınırsız ve izlenmeyen bir üst rol olmadığını
  gösterir.

### 4.2. Genişleyebilen kısım

- Kurum yöneticisi, hocaya öğrenci yönetimi, program yönetimi veya sınıf yönetimi gibi belirli
  izinleri **ayrı ayrı** verebilir veya geri alabilir (§5.2, §5.3).
- Yetki kontrolü rol adına değil, işlem (örn. "sınıf oluşturma", "öğrenci arşivleme", "rapor
  dışa aktarma") bazına dayanır (§5.5).
- Varsayılan politika "izin verilmediyse erişim yok"tur (§5.5); bir hocaya ek izin verilmediği
  sürece 4.1'deki varsayılan sınır geçerlidir.

### 4.3. Sabit kalan sınır

- Bir hocanın atanmadığı sınıfa erişimi, ek izinle de genişletilmez; bu, sınıf bazlı erişim
  sınırıdır ve kurum yöneticisinin yetki tanımlamasının kapsamı dışındadır (§5.3, §5.5).
- Bir kurum yöneticisinin yetkisi kendi kurumuyla sınırlıdır; başka kuruma erişim hiçbir yetki
  atamasıyla açılmaz (§4.2, §15 — kurum izolasyonu).

Bu üç alt bölüm, `P-003` görevinde hazırlanacak ayrıntılı eylem × rol yetki matrisinin
başlangıç çerçevesidir; bağlayıcı ayrıntı `P-003`'te kesinleşecektir.

---

## 5. Ana ürün planıyla uyum kontrolü

- Aktör tanımları `URUN_VE_UYGULAMA_PLANI.md` bölüm 5 ile birebir uyumludur.
- Kullanım senaryoları bölüm 6–10 ve 5.5'teki yetki örnekleriyle çelişmez.
- Hoca/yönetici sınırı, "varsayılan politika izin verilmediyse erişim yoktur" ilkesiyle (§5.5)
  ve `AGENTS.md`'deki rolü bağlama göre ele alma ilkesiyle uyumludur.
- Gelecekteki aktörler (öğrenci, veli) ilk sürüm dışında bırakılmış ve bu belgede yalnızca
  varlıkları not edilmiştir; ilk sürüme gizlice eklenmemiştir (§20).
- Terim kullanımı `TERIMLER_SOZLUGU.md` ile tutarlıdır.

---

## 6. Varsayımlar

- Senaryo kimlikleri (`PLAT-*`, `KURUM-*`, `HOCA-*`, `ORTAK-*`) bu belgeye özeldir; ana planda
  veya görev planında önceden tanımlı bir kodlama yoktur. Sonraki görevler (örn. `P-003` yetki
  matrisi) bu kimlikleri referans alabilir veya kendi numaralandırmasını kullanabilir; bu
  belgedeki kimlikler bağlayıcı bir şema kararı değildir.
- "Vasi" ayrı bir kullanım senaryosu üretecek kadar ayrıntılandırılmamıştır; ana planda yalnızca
  "gerekirse" ihtimaliyle anıldığından bölüm 2.1'de not edilmekle sınırlı tutulmuştur.

## 7. Bilinen sınırlamalar

- Bu belge, hangi izinin hangi role varsayılan geldiğini ve hangisinin devredilebildiğini
  ilke düzeyinde gösterir; her eylem için ayrıntılı, test edilebilir izin matrisi üretmez.
  Bu ayrıntı `P-003`'te ele alınacaktır.
- Eski sistem (Excel/HTML/Apps Script) bu repoda bulunmadığından, senaryoların eski sistemdeki
  karşılığı doğrulanmamıştır; bu belge yalnızca onaylı ana plana dayanır.

## 8. Kapsam dışı bırakılanlar

- Ayrıntılı eylem × rol yetki matrisi (`P-003` kapsamı).
- Yönetici/hoca bilgi mimarisi ve ekran envanteri (`P-005`, `P-006`, `P-007` kapsamı).
- Kişisel ve hassas veri envanteri (`P-004` kapsamı — bu görevle paralel yürütülür).
- Öğrenci ve veli (anne/baba/vasi) için ayrıntılı kullanım senaryoları (sonraki faz, Dalga 8).
- Veri modeli, API ve senkronizasyon sözleşmesi ayrıntıları (`P-008`–`P-010` kapsamı).
