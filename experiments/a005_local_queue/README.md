# A-005 — Yerel mobil kuyruk deneyi

Bu deney, mobil istemcide kalıcı, şifreli ve kurum bağlamı ayrılmış yazma kuyruğu için seçilen
yaklaşımı doğrular. Üretim uygulamasına doğrudan bağlı değildir; karar ve sınırlamalar için
`ADR/ADR-005-yerel-mobil-veritabani-ve-kuyruk.md` esas alınır.

## Teknoloji ve bağımlılıklar

- Dart SDK (`>=3.6.0 <4.0.0`)
- Drift ile SQLite sorgu/yürütme katmanı
- `sqlite3mc` ile şifreli SQLite veritabanı
- `crypto` ile özetleme yardımcıları
- `package:test` ile kabul testleri

## Kurulum ve çalıştırma

```bash
cd experiments/a005_local_queue
dart pub get
dart test
```

## Kanıtlanan davranışlar

- Şifreli veritabanı aynı anahtarla yeniden açılır; yanlış anahtar veriyi okuyamaz.
- Kuyruk girdileri aktör ve `GLOBAL`/`ORGANIZATION` bağlamıyla ayrılır; başka kullanıcı veya
  kurum aynı işlem kimliğiyle kayda erişemez.
- İstemci işlem kimliği yeniden denemelerde korunur; yarım `SYNCING` kayıt açılış sonrası
  `PENDING`e döner.
- Kayıt yalnız kesin başarıdan sonra temizlenir. Geçici hata geri deneme zamanını korur;
  iptal edilmiş oturumdaki kayıt `BLOCKED` kalır ve gönderilemez.
- Aynı hedefte sıralama, bağımlılık sırası ve atomik claim korunur; eşzamanlı iki claim
  denemesinden yalnız biri kaydı `SYNCING`e geçirir.

`test/local_queue_test.dart` içindeki **17 test**, yukarıdaki kabul davranışlarını kanıtlar.

## Sınırlar

Bu sonuçlar üretim kapasitesini, gerçek cihaz davranışını veya production güvenilirliğini
kanıtlamaz. Ağ istemcisi, sunucu onayı, cihaz güvenli saklama entegrasyonu ve uygulama yaşam
döngüsü bu izole deneyin kapsamı dışındadır. Deney kodu production uygulamasına doğrudan
bağlanmaz; üretim entegrasyonu ayrı uygulama görevlerinin sorumluluğundadır.
