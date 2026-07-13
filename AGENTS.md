# Agent Çalışma Kuralları

Bu repo yeni, çok kurumlu Kur'an kursu eğitim ve takip platformunun planlama ve geliştirme
alanıdır. Eski HTML, Apps Script ve Excel dosyaları yalnızca referanstır; yeni sistemin
üzerine inşa edileceği temel değildir.

## Her görevden önce okunacaklar

1. `URUN_VE_UYGULAMA_PLANI.md`
2. `AGENT_GOREV_PLANI.md`
3. `GOREV_DURUMU.md`
4. Görevin bağımlı olduğu tasarım/ADR belgeleri
5. Varsa görevin dokunduğu modülün README ve testleri

## Çalışma biçimi

- Yalnızca açıkça atanmış tek görev kimliği üzerinde çalış.
- `GOREV_DURUMU.md` dosyasını çalışan agent değiştirmez; görev panosunu merge sonrasında
  yalnızca koordinatör günceller.
- Görev kapsamı dışındaki özellikleri ekleme veya yeniden tasarlama.
- Bir görev bir iş gününden büyükse uygulamaya başlamadan önce daha küçük görevlere böl.
- Başka agentın sahip olduğu dosyayı veya modülü eşzamanlı değiştirme.
- API, veritabanı şeması, yetki modeli veya ortak paket değişikliği gerekiyorsa önce ilgili
  sözleşme/ADR görevini tamamla.
- Eski sistemi yeni mimariye kopyalama; yalnızca doğrulanmış kullanıcı davranışlarını referans al.
- Kurum izolasyonu, yetkilendirme, idempotency ve denetim kaydı kurallarını atlama.
- Sunucu onayı alınmayan işlemi başarılı kabul etme veya kalıcı kuyruktan silme.
- Normal silme işlemlerinde fiziksel silme yerine arşivleme yaklaşımını koru.
- Yeni bağımlılık eklerken gerekçeyi ve alternatifleri görev notuna yaz.
- Gizli anahtarları veya gerçek kullanıcı verisini repoya ekleme.

## Görev başlangıcı

Bir göreve başlamadan önce şu bilgileri yazılı olarak doğrula:

- Görev kimliği ve başlığı
- Bağımlılıkların tamamlandığı
- Değiştirilecek modül/dosyalar
- Beklenen çıktı
- Kabul ölçütleri
- Test yöntemi

Belirsiz kabul ölçütü varsa kodlama yapma; görev sözleşmesini netleştir.

## Görev tamamlanması

Bir görev ancak şu koşullarda tamamlanır:

- Kapsamdaki çıktı üretildi.
- Kabul ölçütlerinin her biri doğrulandı.
- İlgili otomatik testler eklendi ve geçti.
- Hata, boş, yükleniyor ve yetkisiz durumları ele alındı.
- Yetki ve kurum izolasyonu etkisi kontrol edildi.
- Gerekiyorsa dokümantasyon güncellendi.
- Kapsam dışı kalan noktalar açıkça raporlandı.
- Başka bir görevi gizlice başlatan yarım altyapı bırakılmadı.

## Commit kimliği ve mesaj standardı

- Commit yazarı `Yasir Arslan <hamasetyasir@gmail.com>` olmalıdır.
- Repo içindeki yerel Git ayarı görev başında doğrulanmalıdır:

  ```bash
  git config user.name "Yasir Arslan"
  git config user.email "hamasetyasir@gmail.com"
  ```

- Agent, model veya araç kendisini commit mesajına yazar ya da ortak yazar olarak ekleyemez.
- `Co-Authored-By`, `Generated-By`, model adı, agent adı veya benzeri AI atıf satırları
  yasaktır.
- Commit mesajı `<type>(<GÖREV_NO>): <kısa Türkçe açıklama>` biçiminde olmalıdır.
- İzin verilen temel tipler: `docs`, `feat`, `fix`, `test`, `refactor`, `perf`, `ci`, `chore`.
- Örnek: `docs(P-001): terimler sözlüğünü oluştur`.
- Push/PR işlemi yalnızca kullanıcının yetkilendirdiği GitHub hesabıyla yapılmalıdır. Agent
  kendi hesabıyla giriş yapamaz veya GitHub kimliğini değiştiremez.
- Teslim raporunda commit yazarı ile push/PR yapan GitHub hesabı ayrı ayrı belirtilmelidir.

## Paralel çalışma

- Aynı veritabanı tablolarını değiştiren iki görev paralel yürütülmez.
- Aynı API sözleşmesinin frontend ve backend işleri, sözleşme onaylandıktan sonra paralel
  yürütülebilir.
- Mobil ekran mock API ile geliştirilebilir; mock davranışı onaylı API sözleşmesiyle aynı olmalıdır.
- Ortak tema, yönlendirme, kimlik, ağ istemcisi ve hata modeli tek bir görev sahibine aittir.
- Entegrasyon görevi tamamlanmadan paralel alt görevler “faz tamamlandı” sayılmaz.

## Karar yetkisi

Agentlar ürün kapsamını kendiliğinden değiştiremez. Yeni ürün veya mimari kararı gerekiyorsa:

1. Seçenekleri ve etkileri yaz.
2. İlgili ADR/değişiklik önerisini hazırla.
3. Kullanıcı onayı olmadan bağlayıcı kararı uygulama.
