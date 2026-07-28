# ALPHA-002 — Kapalı alfa backend ve Cognito ortamı

| Alan | Değer |
|---|---|
| Görev | ALPHA-002 — Kapalı alfa backend ve Cognito ortamını kur |
| Ortam | `development` — yalnız sentetik, best-effort kapalı alfa |
| Bölge | `eu-central-1` / Frankfurt |
| Durum | `BLOCKED` — gerçek cloud oturumu ve kullanıcı provisioning'i bekleniyor |
| Branch | `alpha-002/kapali-alfa-backend-cognito` |

## Sağlayıcı seçimi

- Backend: Render Free Docker Web Service. A-010'un tercih edilen Frankfurt adayıdır; standart
  Docker image taşınabilirliği korunur.
- PostgreSQL: Supabase Free, Frankfurt, session pooler `5432`. ADR-003/A-010 ile uyumludur;
  Data API ve istemci DB erişimi kullanılmaz.
- Kimlik: Amazon Cognito Essentials, Frankfurt. ADR-004'ün V1 kararıdır; native public client,
  Authorization Code + PKCE ve secretsiz app client kullanılır.

## Secret olmayan ALPHA-001 runtime paketi

Gerçek cloud kurulumu tamamlandığında aşağıdaki değerler doldurulacaktır. Placeholderlar çalışan
değer değildir ve mobil build'e verilmez.

```text
KURS_PLATFORM_ENVIRONMENT=development
KURS_PLATFORM_API_BASE_URL=https://<render-service>.onrender.com
KURS_PLATFORM_COGNITO_ISSUER_URL=https://cognito-idp.eu-central-1.amazonaws.com/<user-pool-id>
KURS_PLATFORM_COGNITO_CLIENT_ID=<native-public-app-client-id>
KURS_PLATFORM_OAUTH_REDIRECT_URL=kursplatform://oauth2redirect
KURS_PLATFORM_OAUTH_LOGOUT_REDIRECT_URL=kursplatform://oauth2redirect
KURS_PLATFORM_AWS_REGION=eu-central-1
```

## Kabul matrisi

| Kanıt | Durum | Kayıt |
|---|---|---|
| Standart Docker image ve root olmayan runtime | Yerel PASS; cloud bekliyor | image user `10001:10001` |
| Public detail-free readiness ve dar DB rolü | Otomatik PASS; public çağrı bekliyor | `GET /health` testleri |
| Temiz PostgreSQL Flyway + FORCE RLS | Yerel Testcontainers PASS; gerçek Supabase bekliyor | Tam backend suite |
| Cognito pool/client/domain ve en dar IAM | Tekrar üretilebilir şablon hazır | `deploy/alpha/cognito.yaml` |
| Gerçek Authorization Code + PKCE | Bloke | Yetkili AWS oturumu yok |
| Cognito token → platform exchange | Bloke | Cloud kaynakları yok |
| Platform activation / `sessions/me` / refresh / logout | Bloke | Cloud kaynakları yok |
| Yanlış issuer/audience/pool/bozuk token | Kısmi | Otomatik negatifler + cloud smoke bekliyor |
| Sentetik uçtan uca smoke | Bloke | Cloud kaynakları yok |
| Public soğuk başlangıç ölçümü | Bloke | Public URL yok |
| CI kalite kapıları | Bekliyor | Branch push/PR sonrası |

Gerçek ortam satırları `PASS` olmadan görev `REVIEW` olarak raporlanmaz.

## Maliyet ve sınırlamalar

Tahmini dış ödeme hedefi `0 USD/ay`dır: Render Free, Supabase Free ve Cognito'nun ADR-004'teki
ücretsiz MAU eşiği içinde kalınır. Bu garanti değildir; kota/fiyat değişiklikleri deployment
öncesi yeniden doğrulanır. AWS data transferi, destek veya yanlışlıkla açılan ücretli özellikler
ek maliyet doğurabilir. Bütçe alarmı ve sağlayıcı dashboard'u kurulumdan önce kontrol edilir.

- Render Free uyur; soğuk başlangıç ve 0.1 CPU/512 MB sınırı ölçülmeden performans iddiası yoktur.
- Supabase Free düşük etkinlikte duraklayabilir; otomatik yedek/PITR yoktur ve sentetik veri kaybı
  yeniden bootstrap ile giderilir.
- Cognito yalnız kapalı alfa sentetik kullanıcısını barındırır; SMS/e-posta, MFA add-on,
  federasyon ve production dayanıklılığı açılmaz.
- SLA, RPO/RTO veya production erişilebilirliği iddiası yoktur.

## Tek kullanıcı eylemiyle açılacak engel

Yetkili, root olmayan AWS hesabıyla açık bırakılan AWS Console sekmesinde oturum açılması;
ayrıca Render ve Supabase hesaplarında Free kaynak oluşturma yetkisinin hazır olduğunun
bildirilmesi gerekir. Parola, token veya access key bu konuşmaya yazılmaz. Oturumlar hazır
olduğunda bu branch'teki provisioning, gerçek smoke, maliyet/soğuk başlangıç ölçümü, push ve PR
tamamlanabilir.

## Teardown özeti

Render servisi ve Supabase projesi Dashboard'dan silinir. Cognito ve dar IAM kullanıcısı
`deploy/alpha/teardown_cognito.sh` ile CloudFormation stack olarak birlikte kaldırılır. Ayrıntılı
kurulum ve güvenli operasyon sırası `deploy/alpha/README.md` içindedir.
