# PLAN-005 — Başlangıç Maliyeti ve Operasyon Revizyonu

| Alan | Değer |
|---|---|
| Durum | İncelemeye hazır |
| Tarih | 15 Temmuz 2026 |
| Karar sahibi | Ürün sahibi |
| Kapsam | A-001–A-010 sonrası maliyet, sağlayıcı ve görev grafiği düzeltmesi |

## 1. Sonuç

A-001, A-002, A-005, A-006, A-008 ve A-009 kararları değişmeden korunur. A-003 ve A-010
kademeli maliyet profiline geçirilmiştir. A-004 nihai kimlik sağlayıcısı A-004R1–A-004R3 deney
zincirine, A-007 gerçek nesne deposu kararı ise ilk dosya ihtiyacında A-007R doğrulamasına
bağlanmıştır.

A-011 durdurulmaz; yalnız sağlayıcı bağımsız fiziksel iskelet ve mimari testleri üretir. Gerçek
auth/storage entegrasyonu, cloud secret, migration ve provisioning bu görevin kapsamı değildir.

## 2. Onaylı maliyet kademeleri

| Kademe | Kullanım | Hedef dış ödeme | Güvenilirlik sınırı |
|---|---|---:|---|
| Yerel geliştirme | Tek geliştirici, sentetik veri | `0 USD/ay` | Yerel Docker/PostgreSQL |
| Kapalı alfa | 0–10 davetli gerçek test kullanıcısı; bütün ürün verisi sentetik | hedef `0 USD/ay` | Best-effort, SLA yok, ücretsiz/öğrenci kredili kaynak olabilir |
| Gerçek kurum pilotu | Düzenli kullanım, gerçek öğrenci verisi | hedef `25–60 USD/ay` | Ücretli duraklamayan backend/DB, günlük yedek ve restore kanıtı |
| Aktif/kritik ürün | Birden fazla kurum veya kabul edilmiş RPO/RTO | ihtiyaca göre | Sürekli staging, PITR, Pro yönetişim ve HA yalnız tetikle |

`0 USD` bir garanti değildir. Domain, e-posta, kota aşımı veya kredi uygunluğu dış ödeme
oluşturabilir. Kapalı alfada kullanıcı adları tercihen takmadır; kurum, öğrenci, veli, yoklama,
ilerleme, PDF ve rapor verileri gerçek kişiyi/kurumu temsil etmez. Herhangi bir gerçek kurum
veya öğrenci verisi, kullanıcı sayısından bağımsız production pilotu verisidir.

## 3. Değişmeyen güvenlik sınırları

- Mobil istemci veritabanına doğrudan bağlanmaz; Data API kullanılmaz.
- Tenant tablolarında API yetkisi, bileşik bütünlük ve FORCE RLS savunmaları korunur.
- Provider rolleri kurum/sınıf/işlem yetkisinin kaynağı değildir.
- Platform opaque token ailesi, `session_generation`, cihaz ve kurum kapsamlı iptal korunur.
- Gerçek veride şifreli yedek ve restore kanıtı zorunludur; tek CI zamanlayıcısı tek yedek olmaz.
- Dosya anahtarı opak, indirme yetkisi güncel ve kurum kapsamlıdır; bucket public olmaz.
- Standart Docker, PostgreSQL, SQL migration ve sağlayıcı adaptör sınırı taşınabilirliği korur.

## 4. Sağlayıcı kapıları

### A-004R1–A-004R3 — Cognito Essentials deney zinciri

- `A-004R1`; sentetik kullanıcılarla provisioning, username/geçici parola, ilk parola değişimi,
  kayıp create yanıtı ve gerçek mobil PKCE akışını kanıtlar. İlk AWS kaynağından önce `5 USD`
  alarm kurulur; yazılı ürün sahibi onayı olmadan `10 USD` üstüne çıkılmaz.
- `A-004R2`; refresh rotation/reuse, provider disable/global iptal, platform tek-cihaz/kurum
  iptali, provider kesintisi ve olay kaybı uzlaştırmasını kanıtlar. İptal edilmiş provider tokenı
  yeni platform oturumu açamaz; mevcut platform aileleri idempotent ve fail-closed iptal edilir.
- `A-004R3`; nihai sağlayıcı, gerçekleşen maliyet ve operasyon kararını kapatır. Deney
  `eu-central-1` içinde ve yalnız sentetik veriyle yürür. Secret'lar repo/artifact/loga girmez;
  bütün geçici AWS kaynaklarının kaldırıldığı envanterle kanıtlanır.

A-004R3 tamamlanmadan IAM/provider kodu başlamaz.

### A-007R — koşullu depolama doğrulaması

Kapalı alfa dosya kullanmıyorsa uzak depo açılmaz. Dosya gerektiğinde S3 referansını erteleme
ile R2 `eu` jurisdiction + immutable key + bağımsız backup karşılaştırılır. R2 bucket lock,
S3 object versioning değildir; version-ID, exact-version imha ve restore karşılığı yazılmadan
R2 seçilemez.

## 5. Öğrenci kredisi ve sahiplik

- Kredi development/kapalı alfa maliyetini düşürür; mimari karar veya production sahipliği değildir.
- Repo, domain, proje hesapları, yedek ve kurtarma erişimi ürün sahibinin kontrolünde kalır.
- Kredi bitişinden en az 60 gün önce taşınabilir ücretli profile geçiş planı ve restore kanıtı hazırlanır.
- Sağlayıcı değişiminde parola hash'i taşınamıyorsa kontrollü zorunlu parola yenileme uygulanır.

## 6. Görev grafiği

- `A-011`: READY kalır; fiziksel iskeletle sınırlıdır.
- `A-012`: A-011 sonrası sağlayıcı bağımsız CI temeli olarak ilerleyebilir.
- `A-014`: A-011 sonrası sağlayıcı bağımsız log/hata izleme temeli olarak ilerleyebilir.
- `A-004R1`: PLAN-005 merge sonrası READY olabilir; A-004R2 ve A-004R3 sıralı ilerler.
- `A-013` ve `IAM-001`: PLAN-005 + A-004R3 tamamlanmadan başlayamaz.
- `A-007R`: yalnız gerçek dosya ihtiyacı oluştuğunda READY yapılır.
- `OPS-005`: A-007R ve A-013 tamamlanmadan başlayamaz.
- `ORG-005`/`ORG-008`: yalnız kurum adı ve renk ayarlarını uygular; logo dosyası kabul etmez.
- `ORG-010`/`ORG-011`: Dalga 5'te A-007R ve OPS-005 sonrasında logo backend/mobil akışını
  uygular; gerçek kurum pilotu öncesinde tamamlanır.

## 7. Kabul kontrolü

- [x] Dört maliyet kademesi ana plan, A-003 ve A-010'da aynı anlamla tanımlandı.
- [x] `214 USD/ay` başlangıç zorunluluğu olmaktan çıkarıldı.
- [x] Keycloak kararı A-004R3 sonuçlanana kadar uygulama kapısına alındı.
- [x] A-007 S3 sözleşmesi korunurken dört hesaplı provisioning kritik profile ertelendi.
- [x] R2'nin bucket lock özelliği object versioning yerine geçirilmedi.
- [x] A-011 kapsamı provider portu/SDK/cloud kaynağı üretmeyecek biçimde daraltıldı.
- [x] A-013, IAM-001 ve OPS-005 bağımlılıkları yeni kapılarla güncellendi.
- [x] Bayat A-002/A-005/A-006/A-008/A-009 ADR durumları düzeltildi.
- [x] Ortak API ve veri envanterindeki Keycloak'a gereksiz doğrudan bağlar genelleştirildi.
- [x] Cognito deneyi provisioning/PKCE, iptal/uzlaştırma ve nihai karar/teardown olarak üç atomik
      göreve ayrıldı.
- [x] Logo backend/mobil görevleri dosyasız marka akışından ayrılıp A-007R/OPS-005 kapısına bağlandı.
- [x] Gerçek veri öncesi OPS-002 restore tatbikatının başarıyla geçmesi zorunlu kılındı.
- [x] Kapalı alfa ürün verisinin tamamının sentetik olduğu tek anlamlı tanımla sabitlendi.

`GOREV_DURUMU.md`, repo kuralı gereği bu PR merge edilmeden değiştirilmez. Merge sonrasında
PLAN-005 DONE yapılır; A-011 ve A-004R1 READY olarak değerlendirilir.
