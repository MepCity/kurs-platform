# Faz 0 Bütünlük İncelemesi

| Alan | Değer |
|---|---|
| Görev | P-014 — Faz 0 bütünlük incelemesi yap |
| Sonuç | MERGE EDİLEBİLİR — Dalga 0 çıkış kapısı karşılandı |
| İncelenen bağımlılıklar | P-001–P-013 |
| İnceleme tarihi | 14 Temmuz 2026 |

## 1. Amaç ve kapsam

Bu inceleme, Dalga 0 belgelerinin ana ürün sözleşmesine uyduğunu ve birbirleriyle
uygulanabilir, çelişkisiz bir başlangıç sözleşmesi oluşturduğunu doğrular. Uygulama,
migration veya teknoloji seçimi yapmaz; bunlar Dalga 1 ve sonraki görevlerin kapsamındadır.

İncelenen çıktılar: terimler, aktörler, yetki ve kişisel veri belgeleri; iki mobil bilgi
mimarisi ve ekran envanteri; veri modeli; API, senkronizasyon, denetim/geri alma, Excel
ve kritik kabul planıdır.

## 2. Bağımlılık ve kaynak doğrulaması

`GOREV_DURUMU.md`, P-001–P-013'ü `DONE`, P-014'ü `READY` olarak gösterir. Güncel `main`
dalı, her bağımlı belgenin teslim commitini içerir. İnceleme bu güncel `origin/main` tabanı
üzerinde yapılmıştır.

Ana kaynak önceliği `URUN_VE_UYGULAMA_PLANI.md`dir. İnceleme, açık ürün kararını daha eski
bir görev notuna göre değiştirmez; sonraki belgelerdeki bağlayıcı netleştirmeleri bu ana
sözleşmeyle birlikte değerlendirir.

## 3. Dalga 0 çıkış kapısı

| Çıkış ölçütü | Kanıt | Sonuç |
|---|---|---|
| Terimler çelişkisizdir | P-001; P-008'in kurum-kapsamlı `people`, global `users` ve çoklu rol modeliyle güncellenmiş terimleri | Geçti |
| Yetkiler rol ve işlem bazındadır | P-003 eylem × rol matrisi; P-008 izin kataloğu; P-005/P-006/P-007 görünürlük eşlemeleri | Geçti |
| Ana mobil akışların ekran envanteri vardır | P-005, P-006 ve P-007; giriş, bağlam, günlük sınıf işlemleri, yönetim, rapor/denetim ve oturum durumları | Geçti |
| Veri modeli V1 gereksinimlerini taşır | P-008: tenant zincirleri, üyelik/rol, sınıf ve öğrenci, yoklama, program/ilerleme, audit, idempotency ve outbox | Geçti |
| API, sync, audit ve Excel uyumludur | P-009–P-012 ile ortak kimlik, hata, sürüm, idempotency, kapsam ve denetim kuralları | Geçti |
| Kritik kabul senaryoları yazılıdır | P-013: KAP-01–KAP-35 ve ana plan/P-009/P-010 izlenebilirlik tabloları | Geçti |

## 4. Çapraz sözleşme sonuçları

| Konu | Doğrulanan ortak kural | Kaynaklar |
|---|---|---|
| Kurum izolasyonu | Kurum verisi `organization_id` ve bileşik FK zincirleriyle ayrılır; erişim ayrıca sunucuda rol/sınıf bağlamıyla doğrulanır. | P-003, P-008, P-009, P-010, P-013 |
| Kimlik ve rol | `users` global kimliktir; kişi profili kurum kapsamlıdır; bir üyelik birden çok aktif rol taşıyabilir. | P-001, P-003, P-008 |
| Hoca sınırı | Hoca yalnız atanmış sınıfın operasyonel verisine erişir; devredilen kurum izinleri bu veriye erişim vermez; hoca izin değiştiremez. | P-002, P-003, P-005–P-007, P-013 KAP-01/KAP-32 |
| Arşiv ve geri yükleme | Normal silme yoktur. Öğrenci ve sınıf için tek ortak `RESTORE_ARCHIVED` izni; denetimden geri alma ayrıca geri alma izni gerektirir. | P-003, P-008 §4.8, P-011, P-013 KAP-29/KAP-35 |
| Yoklama | Sınıf/iş günü için tek oturum; `UNMARKED` teknik başlangıç durumudur, seçilemez ve Excel sonucu/sayımına girmez. | P-008, P-010, P-011, P-012, P-013 KAP-17/KAP-19/KAP-35 |
| Güvenilir yazma | İstemci işlem kimliği, idempotency, satır sürümü ve atomik iş/audit/outbox yazımı korunur; kesin sunucu onayı olmadan kuyruk silinmez. | P-008–P-011, P-013 KAP-09–KAP-25/KAP-34 |
| Denetim ve rapor | Kritik olaylar denetlenir; hassas audit alanları sunucuda maskelenir; Excel işi yetkiyi üretim ve indirmede yeniden doğrular. | P-004, P-011, P-012, P-013 KAP-30/KAP-35 |

## 5. İnceleme sırasında düzeltilen bulgu

P-005 ve P-006, ortak `RESTORE_ARCHIVED` kararına doğru biçimde atıf yapıyor; ancak bazı
atıflar `VERI_MODELI.md` §4.6'yı gösteriyordu. Yetki kataloğu ve kararın gerçek konumu
§4.8'dir. Bu görev kapsamında tüm bu çapraz başvurular §4.8 olarak düzeltildi. Davranış veya
ürün kararı değişmemiştir.

README'deki eski “ilk görev P-001” ifadesi de güncel görev panosunu işaret edecek şekilde
düzeltildi; görev durumunun tek operasyonel kaynağı `GOREV_DURUMU.md` olmaya devam eder.

## 6. Bilinen, bloklamayan sınırlar

- Çoklu rol/kurum bağlamı ekranının kesin giriş ve geçiş davranışı `UI-002` sözleşmesinin
  açık kararıdır. P-007 bunu `CTX-01` aday ekranı olarak kaydeder; mevcut rol/izolasyon
  kurallarını belirsiz bırakmaz.
- Teknoloji, taşıma kanalı, yerel veritabanı, migration ve sayısal saklama süreleri Dalga 1
  ADR ve uygulama görevlerinin konusudur.
- Hukuki KVKK saklama/imha kararları P-004'te açıkça hukukî değerlendirmeye bırakılmıştır;
  bu inceleme ürün içi hassasiyet ve erişim kurallarını doğrular, hukukî uygunluk beyanı vermez.

Bu sınırlar Dalga 0 çıkış ölçütlerine aykırı değildir ve A-001–A-008'in karar alanlarını
önceden kapatmaz.

## 7. Açık/bekleyen karar envanteri

| Karar | Sahibi olan sonraki görev | Bloklayıcı mı? | Durum |
|---|---|---|---|
| Çoklu rol/kurum bağlamı ekranının gösterilme koşulu ve geçiş davranışı | UI-002 | Hayır — Dalga 1 ADR'lerini engellemez | CTX-01 aday ekranı kayıtlıdır |
| Mobil navigasyon bileşeni, kesin metin ve simgeler | UI-002 | Hayır | Ekran envanteri ve önerilen gruplama P-007'de vardır |
| Mobil framework, backend, veritabanı/hosting, kimlik, yerel depo, gerçek zaman, dosya ve Excel teknolojileri | A-001–A-008 | Evet — A-009 ve sonraki iskelet görevleri için | Teknoloji ADR kararları beklenir |
| Programdaki büyük/küçük yapısal değişiklik eşiği | PROGRAM-003 | Hayır — mevcut sürümleme değişmezi tanımlıdır | Açık ürün/API eşiği |
| Kurumlar arası mevcut kişi dedüplikasyonu | Ayrı PEOPLE görevi | Hayır | V1'de kurum kapsamlı kişi profili korunur |
| Hukuki KVKK saklama/imha süreleri | Hukukî değerlendirme + ilgili operasyon görevi | Hayır — ürün içi erişim/hassasiyet kuralları tanımlıdır | Hukukî uygunluk beyanı değildir |

## 8. Doğrulama

1. P-001–P-013 teslim dosyaları, `GOREV_DURUMU.md` ve güncel `main` commit geçmişi
   karşılaştırıldı.
2. Dalga 0 çıkış kapısının altı ölçütü, yukarıdaki izlenebilirlik tablosunda kaynaklarıyla
   değerlendirildi.
3. Yetki, tenant, arşiv/geri alma, yoklama, sync, audit ve Excel kuralları belgeler arasında
   arandı ve eşleştirildi.
4. Markdown dosya referansları, hedef dosyaların varlığı ve `§` bölüm başvuruları sistematik
   tarandı; kırık başvuru bulunmadı. Tamamlanmış P-005, P-007, P-008, P-009, P-010 ve P-011'e
   ait normatif gelecek-zamanlı ifadeler güncel sözleşme/bölüm başvurularına dönüştürüldü.
5. Kontrol komutları ve sonuçları:
   - `rg -o --no-filename '[[:alnum:]_ÇĞİÖŞÜçğıöşü.-]+\.md' --glob '*.md' . | sort -u` ile
     çıkarılan her dosya
     başvurusu çalışma alanında bulundu (0 eksik dosya).
   - ``perl -ne 'while (/`([^`]+\.md)`\s*§([0-9]+(?:\.[0-9]+)?)/g) { print "$1\t$2\n" }'``
     ile çıkarılan doğrudan dosya/bölüm başvuruları, hedef Markdown başlıklarıyla karşılaştırıldı
     (0 eksik bölüm).
   - `rg -n -i '(P-005|P-007|P-008|P-009|P-010|P-011).*?(ileride|kesinleş|belirlen|bırak|sonra|netleş|kapsamındadır)' --glob '*.md' .`
     taraması 30 eşleşme döndürdü. Sonuç; güncel karar/izlenebilirlik başvuruları, açıkça
     “önceki sürümde” diye işaretlenmiş tarihsel revizyon notları, görev planı satırı ve
     kontrol komutu metniyle sınıflandırıldı.
   - `rg -n -i 'P-007.{0,160}(netleş|kapsamındadır)' HOCA_MOBIL_BILGI_MIMARISI.md YONETICI_BILGI_MIMARISI.md`
     hedefli taraması boş sonuç verdi; P-007 yalnız ekran envanteri ve durumları için
     kullanılır, kesin metin/görsel/etkileşim/alan-bileşen farkları UI-002 veya uygun sonraki
     mobil tasarım görevine bağlıdır.
   - `rg -n -i 'P-011.{0,120}(tanımlanacak|kesinleş)|P-008.?/?.?P-009.{0,120}(tanımlanacak|kesinleş)' HOCA_MOBIL_BILGI_MIMARISI.md YETKI_MATRISI.md`
     hedefli taraması boş sonuç verdi.
   - `git diff --check origin/main...HEAD` boş sonuç verdi.
6. Bu repo planlama aşamasında olduğundan çalıştırılabilir uygulama, lint veya test altyapısı
   yoktur. P-013 KAP kartları otomasyon planıdır; uygulanmamış kartlar PASS olarak
   raporlanmamıştır.

## 9. Sonraki güvenli hareket

P-014 merge ve görev panosu koordinatör tarafından güncellendikten sonra yalnız
`A-001`–`A-008` paralel `READY` olabilir. `A-009`, `A-010` ve `A-011` kendi bağımlılıkları
tamamlanana kadar; sonraki iskelet görevleri ise ilgili ADR kararları tamamlanana kadar
başlatılmamalıdır.
