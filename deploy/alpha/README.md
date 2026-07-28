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

## 4. ALPHA-001 mobil release yapılandırması

Mobil uygulama yalnız aşağıdaki beş public `--dart-define` değerini okur. AWS bölgesi
`eu-central-1` operasyonel metaveridir; mobil define değildir.

```bash
cd apps/mobile
flutter build ios --release --no-codesign \
  --dart-define=KURS_PLATFORM_ENVIRONMENT=development \
  --dart-define=KURS_PLATFORM_PUBLIC_API_BASE_URL=https://kurs-platform-alpha-api-development.onrender.com \
  --dart-define=KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_1GH5JivoG \
  --dart-define=KURS_PLATFORM_COGNITO_CLIENT_ID=2c59dh2nf60fmk6chn6qq3eoqu \
  --dart-define=KURS_PLATFORM_COGNITO_REDIRECT_URI=kursplatform://oauth2redirect
```

CI, `alpha_runtime_config_test.dart` dosyasını aynı değerlerle çalıştırır ve ardından imzasız
iOS release binary üretir. Bu public değerler secret değildir; mobil pakete backend credentialı
konmaz.

## 5. Smoke ve negatif doğrulama

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

## 6. Teardown

Script varsayılan olarak yalnız fail-closed preflight yapar ve hiçbir kaynağı silmez:

```bash
ALPHA_ALLOW_ROOT_TEARDOWN=true ./deploy/alpha/teardown_cognito.sh
```

Root opt-in yalnız kullanıcı onaylı ALPHA-002 istisnasıdır; root varsayılan değildir. Preflight;
AWS hesap ID'si, sabit `eu-central-1`, tam stack/pool/client/domain/IAM/budget/topic adları,
`application=kurs-platform` ve `environment=development` tagları, tek sentetik kullanıcı,
access key sayısı, USD 5 budget ve %80 SNS alarm bağını doğrular. Aynı alpha önekinde başka kaynak,
kimlik veya tag uyuşmazlığı varsa hiçbir silme çağrısı yapmadan durur.

Uygulanabilir teardown sırası:

1. Render Dashboard'da `kurs-platform-alpha-api-development` servisini durdurup silin; servis
   kartının ve public URL'nin artık bulunmadığını doğrulayın.
2. Yalnız başarılı preflight sonrasında AWS silmesini açıkça çalıştırın:

   ```bash
   ALPHA_ALLOW_ROOT_TEARDOWN=true \
   ALPHA_TEARDOWN_EXECUTE=true \
   ALPHA_RENDER_SERVICE_DELETED=true \
     ./deploy/alpha/teardown_cognito.sh
   ```

   Script runtime IAM access key'ini, sentetik kullanıcıyı, CloudFormation stack'ini, budget'ı,
   SNS subscription/topic'i bu sırayla siler; stack, IAM user, pool, budget ve topic yokluğunu
   servis-özgü not-found kodlarıyla doğrular.
3. Supabase Dashboard'da `kurs-platform-alpha-database-development`
   (`bughxtwdwblbxzadituk`) projesini silin; proje kartının ve connect uçlarının artık
   bulunmadığını doğrulayın.
4. Render Dashboard'da `kurs-platform-alpha-blueprint-development`
   (`exs-d9k8aeht0dsc7395sf2g`) Blueprint'ini silin; Blueprint ve servis yokluğunu tekrar
   doğrulayın.

Canlı ortamda bu komut çalıştırılmaz; `teardown_cognito_test.sh` mock AWS CLI ile root/account/tag/
envanter fail-closed kapılarını, dry-run'da silme olmamasını, silme sırasını ve yokluk kontrollerini
kanıtlar. `.operation-record` yerel kayıt dizini son doğrulamadan sonra güvenli biçimde
temizlenebilir. Free Supabase otomatik yedek/PITR sağlamaz; veri tamamen sentetiktir.
