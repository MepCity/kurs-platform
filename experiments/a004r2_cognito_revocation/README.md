# A-004R2 platform oturumu ve iptal deneyi

Bu bağımsız Node.js deneyi Cognito tokenlarını ürün API'lerinde kullanmaz. Sağlayıcı kimliği
doğrulandıktan sonra platformun ürettiği opaque access/refresh ailesinin güvenlik davranışını
kanıtlar; üretim IAM uygulaması değildir.

## Kanıtlanan davranışlar

- Refresh token tek kullanımlıdır; ardılı üretilince tüketilmiş olur.
- Tüketilmiş refresh tokenının yeniden kullanımı tüm aileyi idempotent iptal eder.
- Provider durumu doğrulanamıyorsa veya token/hesap iptalse yeni platform ailesi üretilmez.
- Global provider olayı bütün kurum ailelerini; cihaz iptali yalnız hedef cihazı; kurum iptali
  yalnız hedef üyeliği etkiler.
- Provider olay completion anahtarı `provider + realm + event ID` bileşimidir. Bilinmeyen
  subject completion yazmaz; eşleme oluştuktan sonra aynı olay yeniden uygulanabilir.
- Kaçırılmış disable olayı uzlaştırmada bulunur. Provider uzlaştırması kullanılamıyorsa mevcut
  aileler fail-closed iptal edilir ve kullanıcı generation değeri yalnız bir kez artar.
- Her erişimde kullanıcı, üyelik ve cihaz `session_generation` değerleri yeniden karşılaştırılır.
- Tokenların yalnız SHA-256 özeti bellekte indekslenir; deney çıktısı opaque değer yazmaz.

## Çalıştırma

```bash
npm test
npm run experiment
```

Harici bağımlılık yoktur; Node.js yerleşik test ve kripto modülleri kullanılır.
`npm run experiment`, ölçülen rotation/reuse/aile iptali sonuçlarından biri başarısızsa non-zero
exit code üretir.

## Gerçek Cognito deneyini yeniden üretme

`scripts/run-cognito-experiment.sh` yalnız `eu-central-1` bölgesinde, MFA ile alınmış bir saatlik
`kurs-platform-a004r2-experiment` STS rolüyle çalışır. Root, IAM user principal veya farklı rol
görürse hiçbir provider mutasyonu yapmadan durur. Betik `aws`, `jq` ve `openssl` gerektirir.

Pool kimliği ve sentetik username'i komut satırına literal yazmadan güvenli oturum
değişkenlerine alın:

```bash
read -rsp "A-004R1 user pool ID: " A004R2_USER_POOL_ID; echo
read -rsp "Sentetik username: " A004R2_USERNAME; echo
export A004R2_USER_POOL_ID A004R2_USERNAME
export AWS_REGION=eu-central-1
./scripts/run-cognito-experiment.sh
unset A004R2_USER_POOL_ID A004R2_USERNAME
```

Betik `set -Eeuo pipefail` ile fail-closed çalışır; parola ve tokenları yalnız süreç belleğinde
tutar, `set -x` açmaz ve değerleri yazdırmaz. Her çıkış yolundaki `trap`, sentetik kullanıcıyı
etkin duruma getirir, helper clientı siler ve geçici hata dosyalarını kaldırır. Rotation yeni
refresh üretmezse, beklenen ret kodu alınmazsa, uzlaştırma `Enabled=False` görmezse veya cleanup
tamamlanmazsa exit code non-zero olur. Başarılı koşuda yalnız PASS işaretleri görünür.

CloudTrail teslimi gecikebildiği için betik event ID'lerini başarı koşulu yapmaz. Provider
sonuçları tamamlandıktan sonra kanıt belgesindeki olay adları ayrı salt-okunur sorguyla alınır;
istek/yanıt gövdeleri, username, client ID veya tokenlar kanıta eklenmez.
