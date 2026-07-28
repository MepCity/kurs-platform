# ALPHA-002 tekrar üretilebilir ortam araçları

Bu dizin yalnız sentetik `development` kapalı alfa ortamını kurar. Gerçek kişi/kurum/öğrenci
verisi için kullanılamaz. Secret, parola ve tokenlar komut satırı geçmişine, Git'e, loga veya PR
metnine yazılmaz.

## 1. Cognito

Yetkili, root olmayan AWS kimliği ve AWS CLI ile:

```bash
read -r -p 'Sentetik Cognito e-posta kullanici adi (or. alpha@invalid.example): ' ALPHA_TEST_USERNAME
read -r -s -p 'Sentetik Cognito parola: ' ALPHA_TEST_PASSWORD
export ALPHA_TEST_USERNAME ALPHA_TEST_PASSWORD
export ALPHA_COGNITO_DOMAIN_PREFIX='<hesapta-benzersiz-sentetik-on-ek>'
AWS_REGION=eu-central-1 ./deploy/alpha/provision_cognito.sh
unset ALPHA_TEST_PASSWORD
```

CloudFormation yalnız tek Essentials user pool, secretsiz native/mobile app client, managed-login
domaini ve yalnız o poola dar IAM runtime kullanıcısı oluşturur. App client yalnız Authorization
Code akışını, PKCE istemcisini, `openid` scope'unu ve tam
`kursplatform://oauth2redirect` callback/logout değerini kabul eder. Implicit, client secret,
self-signup ve federasyon açılmaz.

Scriptin `.operation-record/runtime-values.env` çıktısı Git tarafından yok sayılır. Parola/token
içermez; gerçek resource kimlikleri yalnız yerel operasyon kaydıdır. Runtime IAM kullanıcısının
access key'i AWS Console'da tek sefer oluşturulur, doğrudan Render secret alanına yazılır ve yerel
dosyaya kaydedilmez. İkinci anahtar oluşturulmaz.

## 2. PostgreSQL ve sentetik platform eşlemesi

Supabase Free üzerinde Frankfurt projesi oluşturulur. Data API kapatılır, `app` exposed schema
listesine eklenmez ve doğrudan istemci DB erişimi açılmaz. Session pooler port `5432` bağlantısı
TLS ile kullanılır. Migration sahibi ile runtime parolası ayrıdır.

Backend ilk açılışta Flyway'i migration sahibiyle çalıştırır ve `iam_runtime` parolasını yalnız
Flyway callback'i üzerinden eşitler. Migration sonrasında sentetik Cognito kullanıcısı platform
yöneticisine şu şekilde bağlanır:

```bash
set -a
. ./deploy/alpha/.operation-record/runtime-values.env
set +a
psql "$DATABASE_MIGRATION_URL" \
  --set=provider_issuer="$IAM_COGNITO_ISSUER" \
  --set=provider_subject="$ALPHA_PROVIDER_SUBJECT" \
  --file=deploy/alpha/bootstrap_platform_admin.sql
```

SQL yalnız açıkça sentetik isim ve sabit UUID'ler üretir; gerçek kişi veya kurum verisi içermez.

## 3. Backend

Kökteki `render.yaml` blueprint'i Frankfurt'ta Free Docker Web Service tanımlar. `sync: false`
alanlar Dashboard'da doldurulur; gerçek değerler `render.yaml`a yazılmaz. `DATABASE_URL` runtime
`iam_runtime` JDBC URL'si, `DATABASE_MIGRATION_URL` migration sahibinin JDBC URL'sidir. Aynı
ayrım kullanıcı adı/parola alanlarında korunur.

`KURS_PLATFORM_*_SECRET_REF` değerleri secretın kendisi değil, yalnız sağlayıcıdaki mantıksal
referans adlarıdır. Supabase shared session pooler kullanıcı adını `<rol>.<project-ref>` biçiminde
beklediğinden `DATABASE_USERNAME`, `iam_runtime.<project-ref>` olarak Dashboard'da doldurulur;
migration kullanıcısı da `postgres.<project-ref>` biçimindedir. Stub, security-event ve
reconciliation worker'ları bu kapalı alfa görevinde kapalıdır; gerçek Cognito token doğrulayıcısı
production Spring profilinde zorunludur.

## 4. Smoke ve negatif doğrulama

Mobil AppAuth ile gerçek Authorization Code + PKCE tamamlandıktan sonra Cognito access tokenı
yalnız yerel güvenli ortam değişkenine alınır:

```bash
export ALPHA_API_BASE_URL='https://<render-service>.onrender.com'
read -r -s -p 'Cognito access token: ' ALPHA_COGNITO_ACCESS_TOKEN
export ALPHA_COGNITO_ACCESS_TOKEN
./deploy/alpha/smoke_test.sh
unset ALPHA_COGNITO_ACCESS_TOKEN
```

Smoke; public health, provider exchange, platform-admin activation, `sessions/me`, refresh,
logout ve bozuk token fail-closed reddini doğrular. Yanlış issuer/audience/pool/JWKS matrisi
backend otomatik testlerinde ayrıca zorunludur. Başarı yanıtları secret içermeyen tarih/süre/HTTP
durum matrisi olarak teslim belgesine elle kaydedilir; token veya gövde kopyalanmaz.

## 5. Teardown

Önce Render servisi ve Supabase projesi Dashboard'dan silinir; sonra:

```bash
AWS_REGION=eu-central-1 ./deploy/alpha/teardown_cognito.sh
```

Silme sonrasında CloudFormation stack, Cognito pool/client/domain ve IAM runtime kullanıcısının
yokluğu doğrulanır. `.operation-record` yerel kayıt dizini çöpe taşınabilir. Free Supabase
otomatik yedek/PITR sağlamaz; kapalı alfa verisi tamamen sentetik ve yeniden üretilebilir kabul
edilir.
