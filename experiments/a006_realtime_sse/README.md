# A-006 — İki istemcili SSE deneyi

Bu deney, üretim uygulaması iskeleti değildir. A-006 kapsamında seçilen tek yönlü SSE
kanalının şu davranışlarını bağımlılık eklemeden ve sentetik verilerle doğrular:

- aynı kurum ve sınıftaki iki ayrı istemci aynı küçük yoklama değişiklik olayını alır;
- ikinci istemci bağlantısı kesikken oluşan olayı kaçırır;
- yeniden bağlantıda `stream.ready.headSequence` ile geride olduğunu görür;
- `headSequence` yalnız doğrulanmış kurum+sınıf kapsamından üretilir;
- kullanıcı/kurum/sınıfa bağlı opak tokenla kalıcı değişiklik akışını tüketip kanonik kaydı
  yeniden çekerek sunucu durumuna döner;
- bağlantı sonrasında sınıf ataması veya oturumu iptal edilirse sonraki olay verilmeden akış
  kapatılır;
- tekrarlar `eventId` ile ayıklanır; farklı olaylardaki sıra boşluğu/sıra dışı teslim güvenli
  uzlaşmayı tetikler;
- başka kurum kimliği veya aynı kurumda yetkisiz sınıf kimliği hedef sınıf akışına abone olamaz;
- başka kullanıcıya bağlı sync token yeniden kullanılamaz;
- kimlik doğrulaması olmadan akış açılamaz.

## Çalıştırma

Node.js 20 veya üzeri yeterlidir; üçüncü taraf paket kurulmaz.

```bash
cd experiments/a006_realtime_sse
npm test
npm run experiment
```

`npm test` altı kabul/regresyon senaryosunu otomatik doğrular. `npm run experiment` ana iki
istemcili senaryoyu çalıştırır
ve olay yayılımı ile yeniden bağlanma/uzlaşma sürelerini JSON olarak yazar. Süreler yerel süreç
içi ölçümdür; internet, mobil radyo, reverse proxy veya üretim yükü ölçümü değildir.

## Deney sınırları

- Kimlikler yalnız sentetik sabit test kimlikleridir; gerçek token veya kullanıcı verisi yoktur.
- Bellek içi değişiklik dizisi üretimdeki `sync_changes` tablosunu temsil eder.
- Test HTTP sunucusu, Spring Boot uygulaması veya production güvenlik uygulaması değildir.
- Mobil yaşam döngüsü, proxy timeout'u, çok instance ve yük davranışı sonraki uygulama/QA
  görevlerinde seçilen gerçek ortamda ayrıca doğrulanacaktır.
