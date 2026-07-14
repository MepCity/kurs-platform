# Agentlara Görev Verme ve Branch Çalışma Rehberi

Bu rehber, `AGENT_GOREV_PLANI.md` içindeki görevlerin farklı yapay zekâ agentlarına güvenli
biçimde dağıtılması için kullanılacaktır.

## 1. Temel kural

Her branch yalnızca bir görev kimliğine aittir.

Örnekler:

```text
codex/p-001-terimler-sozlugu
codex/p-003-yetki-matrisi
codex/att-006-yoklama-okuma-api
codex/att-013-mobil-yoklama-listesi
```

Bir agent aynı branch içinde ikinci göreve başlamaz. Yeni iş için yeni branch ve yeni görev
kartı gerekir.

## 2. Başlamadan önce

Koordinatör:

1. `main` dalının güncel olduğunu doğrular.
2. `GOREV_DURUMU.md` içindeki `READY` görevlerden birini seçer.
3. Görevin bütün bağımlılıklarının `DONE` olduğunu kontrol eder.
4. Paralel agent varsa dosya ve modül sahipliğinin çakışmadığını kontrol eder.
5. Görev branch'ini güncel `main` üzerinden açar.

Örnek:

```bash
git switch main
git pull --ff-only
git switch -c codex/p-001-terimler-sozlugu
```

Agent branch'i kendisi açacaksa komutta branch adını açıkça belirtmek gerekir.

Commit atmadan önce repo-yerel Git kimliği doğrulanır:

```bash
git config user.name "Yasir Arslan"
git config user.email "hamasetyasir@gmail.com"
git config --get user.name
git config --get user.email
```

Bu ayar commit içindeki `Author/Committer` bilgisidir. GitHub'da push veya PR'ı oluşturan
hesap ise makinede kullanılan GitHub kimlik bilgileriyle belirlenir. Agent bu hesabı
değiştiremez. Şu an görünen teknik GitHub hesabı `MepCity`dir; farklı bir PR sahibi
isteniyorsa kullanıcı o hesapla giriş yapmalı ve repoya yetki vermelidir.

## 3. Agent için standart görev komutu

Aşağıdaki şablon kopyalanıp görev bilgileri değiştirilir:

```text
Bu repoda yalnızca P-001 — Terimler sözlüğünü oluştur görevi üzerinde çalış.

Başlamadan önce sırasıyla şunları tamamen oku:
1. AGENTS.md
2. URUN_VE_UYGULAMA_PLANI.md
3. AGENT_GOREV_PLANI.md içindeki P-001 tanımı
4. GOREV_DURUMU.md

Branch: codex/p-001-terimler-sozlugu

Görev kapsamı:
- AGENT_GOREV_PLANI.md içindeki P-001 amaç, çıktı ve kabul ölçütlerini eksiksiz karşıla.
- Çıktıyı TERIMLER_SOZLUGU.md dosyasına yaz.

Kurallar:
- Başka göreve veya uygulama koduna başlama.
- Ürün kapsamını kendiliğinden değiştirme.
- GOREV_DURUMU.md dosyasını değiştirme; onu merge sonrasında koordinatör güncelleyecek.
- Kapsam dışı iyileştirmeleri uygulama; teslim notunda öneri olarak belirt.
- Belirsizlik ana ürün kararını etkiliyorsa varsayım yapma, dur ve sor.
- Görev sonunda kabul ölçütlerini tek tek doğrula.
- Yalnızca bu görevin dosyalarını commit et.
- Commit yazarı olarak `Yasir Arslan <hamasetyasir@gmail.com>` kullan.
- Commit mesajını `<type>(<GÖREV_NO>): <kısa Türkçe açıklama>` biçiminde yaz.
- Commit veya PR mesajına kendini, model adını ya da agent adını ekleme.
- `Co-Authored-By`, `Generated-By` veya benzeri AI atıf satırı ekleme.

Tesliminde şunları yaz:
- Değiştirilen dosyalar
- Kabul ölçütlerinin sonucu
- Yapılan doğrulamalar/testler
- Varsayımlar
- Bilinen sınırlamalar
- Sonraki hazır olabilecek görevler
- Commit yazarı
- Push/PR GitHub hesabı
```

## 4. Commit mesajı ve yazarlık standardı

### Zorunlu commit yazarı

```text
Yasir Arslan <hamasetyasir@gmail.com>
```

Agent commit sonrasında kimliği ve mesajı doğrulamalıdır:

```bash
git show -s --format='author=%an <%ae>%ncommitter=%cn <%ce>%n%n%B' HEAD
```

### Mesaj biçimi

```text
<type>(<GÖREV_NO>): <kısa Türkçe açıklama>
```

İzin verilen temel tipler:

- `docs`: Belge değişikliği
- `feat`: Yeni ürün davranışı
- `fix`: Hata düzeltmesi
- `test`: Test değişikliği
- `refactor`: Davranışı değiştirmeyen yeniden düzenleme
- `perf`: Performans iyileştirmesi
- `ci`: CI/CD değişikliği
- `chore`: Bakım ve araç değişikliği

Örnekler:

```text
docs(P-001): terimler sözlüğünü oluştur
feat(ATT-006): günlük yoklama okuma servisini ekle
fix(SYNC-003): başarısız işlemi kuyrukta koru
test(PERM-003): kurum izolasyonu senaryolarını ekle
```

Başlık kısa, somut ve Türkçe olmalı; noktayla bitmemelidir.

### Yasak atıflar

Commit mesajında veya trailer bölümünde şunlar bulunamaz:

```text
Co-Authored-By: <agent veya model>
Generated-By: <araç veya model>
Written-By: <agent veya model>
```

Agent/model adı commit sahibi, ortak yazar veya üretici olarak eklenmez. Proje commitlerinin
yazarı kullanıcıdır.

### Push/PR hesabı

- Commit yazarıyla GitHub push hesabı farklı kavramlardır.
- Commit yazarı daima `Yasir Arslan <hamasetyasir@gmail.com>` olmalıdır.
- Push/PR hesabı, kullanıcının makinede yetkilendirdiği GitHub hesabıdır.
- Agent yeni hesapla giriş yapamaz, mevcut hesabı değiştiremez veya kendi hesabını kullanamaz.
- Push öncesinde kullanılabiliyorsa `gh auth status` ile gerçek GitHub hesabı doğrulanır.
- Teslim raporunda commit yazarı ve push/PR hesabı ayrı ayrı yazılır.
- Şu an görünen teknik push/PR hesabı `MepCity`dir. Bunun değişmesi isteniyorsa kullanıcı
  önceden farklı GitHub hesabıyla kimlik doğrulaması yapmalıdır.

## 5. Kod görevi için ek komut

Uygulama geliştirme görevlerinde standart komuta şunlar eklenir:

```text
- İlgili onaylı API/veri/olay sözleşmesinden ayrılma.
- Yeni bağımlılık eklemeden önce gerekçesini bildir.
- Kurum izolasyonu ve yetki kontrollerini yalnızca UI'da bırakma; sunucuda doğrula.
- Başarılı sunucu onayı gelmeden bekleyen işlemi silme.
- Normal silmede fiziksel silme yerine arşivleme kullan.
- İlgili birim ve entegrasyon testlerini ekle ve çalıştır.
- Migration gerekiyorsa mevcut migration'ı yeniden yazma; yeni migration oluştur.
- Başka modülün iç tablosuna veya dosyasına doğrudan bağımlılık ekleme.
```

## 6. Agent önce plan sunsun

Orta boy veya riskli görevlerde agenttan uygulamadan önce kısa plan istenir:

```text
Henüz dosya değiştirme. Önce:
1. Görevi kendi cümlelerinle özetle.
2. Dokunacağın dosyaları listele.
3. Bağımlılıkların hazır olup olmadığını doğrula.
4. Uygulama ve test adımlarını yaz.
5. Gördüğün belirsizlikleri belirt.

Plan onaylandıktan sonra uygulamaya geçeceksin.
```

Bu yöntem özellikle şu görevlerde kullanılmalıdır:

- Veritabanı migration'ları
- Kimlik ve yetki
- Senkronizasyon
- Eşzamanlı kullanım
- Ortak API sözleşmesi
- Ortak mobil altyapı
- Birden fazla modülü etkileyen değişiklikler

## 7. Paralel görev verme

Yalnızca `AGENT_GOREV_PLANI.md` içinde bağımlılıkları tamamlanmış ve dosya sahipliği
çakışmayan görevler paralel verilir.

Örnek güvenli akış:

1. `ATT-001` sözleşmesi tek agent tarafından hazırlanır ve merge edilir.
2. Sözleşme onaylandıktan sonra:
   - Agent B: `ATT-006` backend okuma API'si,
   - Agent M: `ATT-013` mock API ile mobil yoklama listesi,
   - Agent T: `ATT-017`–`ATT-020` test senaryosu hazırlığı.
3. Alt görevler ayrı PR'larla incelenir.
4. `INT-401` entegrasyon görevi en son yapılır.

API sözleşmesi merge edilmeden frontend ve backend agentlarına aynı özellik verilmez.

## 8. Agent teslimini inceleme

Agent işi bitirdiğinde hemen merge edilmez. Önce şu kontrol yapılır:

### Kapsam

- Yalnızca atanmış görev yapılmış mı?
- İstenmeyen refactor veya yeni özellik var mı?
- Kapsam dışı dosyalar değiştirilmiş mi?

### Mimari

- Ana ürün planıyla uyumlu mu?
- Modül sınırları korunmuş mu?
- Onaylı sözleşmeden sapılmış mı?
- Yeni teknik karar gerekiyorsa ADR var mı?

### Güvenlik ve veri

- Kurum izolasyonu korunuyor mu?
- Yetki backend'de kontrol ediliyor mu?
- Hassas bilgi loglanıyor mu?
- Silme/arşivleme kuralı korunuyor mu?

### Güvenilirlik

- Hata ve boş durumları ele alınmış mı?
- Tekrar istek çift kayıt üretiyor mu?
- Sunucu hatası başarı olarak gösteriliyor mu?
- Çevrimdışı kuyruk veya gerçek zaman davranışı etkileniyor mu?

### Test

- Kabul ölçütlerinin her biri test edilmiş mi?
- Testler gerçekten çalıştırılmış mı?
- Uçtan uca veya entegrasyon testi gereken yerde yalnızca birim testiyle yetinilmiş mi?

## 9. PR şablonu

Her PR açıklaması şu yapıyı kullanmalıdır:

```text
## Görev
P-001 — Terimler sözlüğünü oluştur

## Kapsam
- ...

## Kapsam dışı
- ...

## Değişiklikler
- ...

## Kabul ölçütleri
- [x] ...
- [x] ...

## Test / doğrulama
- ...

## Riskler ve notlar
- ...
```

PR başlığı görev kimliğiyle başlamalıdır:

```text
[P-001] Terimler sözlüğünü oluştur
```

## 10. Merge sırası

1. Agent PR'ı açar.
2. Otomatik kontroller çalışır.
3. Kod/belge ve kapsam incelemesi yapılır.
4. Gerekirse agent aynı branch üzerinde düzeltme yapar.
5. Kabul ölçütleri doğrulanır.
6. PR `main` dalına merge edilir.
7. Koordinatör `GOREV_DURUMU.md` dosyasını günceller.
8. Bağımlılığı tamamlanan görevler `READY` yapılır.
9. Kullanılmış branch silinir.

Bağımlı ikinci görev, ilk PR yalnızca “hazır görünüyor” diye başlatılmaz; önce `main` dalına
merge edilmelidir.

## 11. GitHub repo ayarları

`main` dalı için önerilen korumalar:

- Pull request olmadan merge edilmesin.
- En az bir inceleme/onay gereksin.
- Durum kontrolleri başarılı olmadan merge edilmesin.
- Force push kapalı olsun.
- Branch silme koruması açık olsun.
- Konuşmalar çözülmeden merge edilmesin.
- Mümkünse doğrusal geçmiş veya squash merge kullanılsın.

Planlama aşamasında otomatik testler henüz yoksa PR zorunluluğu ve inceleme koruması yine
etkinleştirilebilir. CI kurulduğunda zorunlu durum kontrolleri eklenir.

## 12. Tarihsel ilk görev sırası

Dalga 0'ın tamamlanmış tarihsel başlangıç sırası:

1. `P-001` tek başına yapılır ve merge edilir.
2. Ardından `P-002` ve `P-004` farklı agentlara paralel verilebilir.
3. `P-002` tamamlanınca `P-005` ve `P-006` paralel başlayabilir.
4. `P-001`, `P-002` tamamlanınca `P-003` yapılabilir.
5. `P-005` ve `P-006` tamamlanınca `P-007` yapılır.
6. `P-003` ve `P-004` tamamlanınca `P-008` yapılır.
7. Sonraki sıra görev planındaki bağımlılık tablosundan takip edilir.

Bu liste güncel görev durumu değildir. Her yeni oturumda `GOREV_DURUMU.md` okunur; yalnızca
orada `READY` görünen ve bağımlılıkları doğrulanan görev başlatılır.

## 13. Koordinatöre verilecek kısa komut

Kullanıcı her yeni oturumda şu kadar kısa bir istek verebilir:

```text
GOREV_DURUMU.md dosyasını oku. Sıradaki READY görevi başlat. Gerekli branch'i görev
kimliğiyle aç, yalnızca o görevi tamamla, test et ve PR incelemesine hazırla. Görev panosunu
merge gerçekleşmeden DONE yapma.
```

Birden fazla agent isteniyorsa:

```text
GOREV_DURUMU.md ve AGENT_GOREV_PLANI.md dosyalarını oku. Şu anda paralel yapılması güvenli,
bağımlılıkları tamamlanmış ve dosya sahipliği çakışmayan görevleri belirle. Her biri için ayrı
branch ve ayrı agent kullan. Ortak sözleşme eksikse paralel geliştirmeye başlama.
```
