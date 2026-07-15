# Core

Bu dizin yalnız birden fazla feature tarafından kullanılan kararlı ve çapraz kesen mobil
altyapı sözleşmelerine ayrılmıştır. İş kuralları veya feature'a özel yardımcılar burada
toplanmaz.

## Gözlemlenebilirlik

`observability` dizini sağlayıcı bağımsız güvenli olay, log adaptörü ve hata raporlama portunu
taşır. Flutter framework, platform dispatcher ve zone hataları uygulama başlangıcında bu sınıra
bağlanır. Hata tipi ve sabit operasyon adı korunur; istisna mesajı, stack trace, ürün gövdesi,
parola, token, telefon, adres ve serbest not olay modeline kabul edilmez. Gerçek crash sağlayıcı
adaptörü ve ortam secret'ları sonraki sahip görevlerde eklenir. Logger/reporter arızaları
best-effort izole edilir; önceki Flutter/platform handler sonucu ve zone hatasının üst zona
iletilmesi korunur.
