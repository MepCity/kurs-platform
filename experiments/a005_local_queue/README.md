# A-005 — Yerel mobil kuyruk deneyi

Bu deney, mobil istemcide kalıcı, şifreli ve kurum bağlamı ayrılmış yazma kuyruğu için seçilen
yaklaşımı doğrular. Üretim uygulamasına doğrudan bağlı değildir; karar ve sınırlamalar için
`ADR/ADR-005-yerel-mobil-veritabani-ve-kuyruk.md` esas alınır.

## Teknoloji ve bağımlılıklar

- Dart SDK: `^3.12.0`
- `drift: ^2.34.2` ile SQLite sorgu/yürütme katmanı
- `sqlite3: ^3.1.0`; SQLite hook kaynağı: `sqlite3mc`
- Geliştirme bağımlılığı: `test: ^1.26.0`

## Kurulum ve çalıştırma

```bash
cd experiments/a005_local_queue
dart pub get
dart test
```

## Kanıtlanan davranışlar

- Şifreli veritabanı aynı anahtarla yeniden açılır; yanlış anahtar veritabanını açıp okuyamaz.
- Kuyruk girdileri aktör ve `GLOBAL`/`ORGANIZATION` bağlamıyla ayrılır; başka kullanıcı veya
  kurum aynı işlem kimliğiyle kayda erişemez.
- İstemci işlem kimliği yeniden denemelerde korunur; yarım `SYNCING` kayıt açılış sonrası
  `PENDING`e döner.
- Kayıt yalnız kesin başarıdan sonra temizlenir. Geçici hata geri deneme zamanını korur;
  iptal edilmiş oturumdaki kayıt `BLOCKED` kalır ve gönderilemez.
- Aynı hedefte yazma sırası ve bağımlılıklar korunur; eşzamanlı iki claim denemesinden yalnız
  biri kaydı `SYNCING`e geçirir.

`test/local_queue_test.dart` içindeki **17 test**, yukarıdaki kabul davranışlarını kanıtlar.

## Sınırlar

Bu sonuçlar üretim kapasitesini, gerçek cihaz davranışını veya production güvenilirliğini
kanıtlamaz. Ağ istemcisi, sunucu onayı, cihaz güvenli saklama entegrasyonu ve uygulama yaşam
döngüsü bu izole deneyin kapsamı dışındadır. Deney kodu production uygulamasına doğrudan
bağlanmaz; üretim entegrasyonu ayrı uygulama görevlerinin sorumluluğundadır.
