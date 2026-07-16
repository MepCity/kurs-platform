# Core

Bu dizin yalnız birden fazla feature tarafından kullanılan kararlı ve çapraz kesen mobil
altyapı sözleşmelerine ayrılmıştır. İş kuralları veya feature'a özel yardımcılar burada
toplanmaz.

## Ortam yapılandırması

`config` dizini yalnız public mobil yapılandırmayı taşır. Mobil uygulama `--dart-define`
üzerinden `KURS_PLATFORM_ENVIRONMENT`, `KURS_PLATFORM_PUBLIC_API_BASE_URL`,
`KURS_PLATFORM_COGNITO_ISSUER_URI` ve secretsiz `KURS_PLATFORM_COGNITO_CLIENT_ID` değerlerini
okur. Veritabanı bağlantısı, token pepper, secret delivery key, Cognito admin role veya başka
backend secret referansı mobil pakette yer almaz. Runtime kodu default environment üretmez;
sentetik değerler yalnız test veya build komutunda açık verilir.

## Gözlemlenebilirlik

`observability` dizini sağlayıcı bağımsız güvenli olay, log adaptörü ve hata raporlama portunu
taşır. Flutter framework, platform dispatcher ve zone hataları uygulama başlangıcında bu sınıra
bağlanır. Hata tipi ve sabit operasyon adı korunur; istisna mesajı, stack trace, ürün gövdesi,
parola, token, telefon, adres ve serbest not olay modeline kabul edilmez. Gerçek crash sağlayıcı
adaptörü ve ortam secret'ları sonraki sahip görevlerde eklenir. Logger/reporter arızaları
best-effort izole edilir; önceki Flutter/platform handler sonucu ve zone hatasının üst zona
iletilmesi korunur.
