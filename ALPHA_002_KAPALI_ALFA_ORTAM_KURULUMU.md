# ALPHA-002 — Kapalı alfa backend ve Cognito ortamı

| Alan | Değer |
|---|---|
| Görev | ALPHA-002 — Kapalı alfa backend ve Cognito ortamını kur |
| Ortam | `development` — yalnız sentetik, best-effort kapalı alfa |
| Bölge | `eu-central-1` / Frankfurt |
| Durum | `REVIEW` |
| Branch | `alpha-002/kapali-alfa-backend-cognito` |
| Draft PR | `https://github.com/MepCity/kurs-platform/pull/65` |

## Sağlayıcılar ve çalışan ortam

- Backend: Render Free Docker Web Service, Frankfurt. Standart image root olmayan
  `10001:10001` kullanıcısıyla çalışır; production profilinde stub provider kapalıdır.
- PostgreSQL: Supabase Free, Frankfurt. IPv4 session pooler `5432`, TLS, ayrı migration sahibi ve
  `iam_runtime` kullanılır. Data API kapalıdır ve istemci DB erişimi açılmamıştır.
- Kimlik: Amazon Cognito Essentials, Frankfurt. Native/public app client secretsizdir; yalnız
  Authorization Code + PKCE ve `openid` scope'u açıktır.
- AWS maliyet koruması: aylık USD 5 budget, gerçekleşen maliyet %80 eşiğinde SNS alarmı.

## ALPHA-001 secret olmayan runtime paketi

```text
KURS_PLATFORM_ENVIRONMENT=development
KURS_PLATFORM_API_BASE_URL=https://kurs-platform-alpha-api-development.onrender.com
KURS_PLATFORM_COGNITO_ISSUER_URL=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_1GH5JivoG
KURS_PLATFORM_COGNITO_CLIENT_ID=2c59dh2nf60fmk6chn6qq3eoqu
KURS_PLATFORM_OAUTH_REDIRECT_URL=kursplatform://oauth2redirect
KURS_PLATFORM_OAUTH_LOGOUT_REDIRECT_URL=kursplatform://oauth2redirect
KURS_PLATFORM_AWS_REGION=eu-central-1
```

Parola, access/refresh token, DB credential veya AWS secret bu pakete ve repoya yazılmamıştır.

## Gerçek ortam kabul matrisi — 28 Temmuz 2026

| Kanıt | Sonuç | Kayıt |
|---|---|---|
| Public HTTPS readiness | PASS | `GET /health` → `200`, `{"status":"UP"}` |
| Docker ve dar runtime | PASS | Render Free Docker; UID/GID `10001:10001` |
| Temiz PostgreSQL migration | PASS | Flyway `32/32`, latest version `32` |
| Superuser olmayan runtime | PASS | Health sorgusu `iam_runtime`, `rolsuper=false`, `rolbypassrls=false` doğrular |
| RLS sınırları | PASS | 32 uygulama tablosu; 28 RLS tablosunun 28'inde `FORCE RLS` |
| Cognito public mobile client | PASS | Client secret yok; code flow, PKCE ve tam mobil callback doğrulandı |
| Gerçek Authorization Code + PKCE | PASS | State + S256 verifier doğrulandı; code exchange `200` |
| Cognito token → provider exchange | PASS | `200`; `GLOBAL_PLATFORM_ADMIN` ve `ORGANIZATION_SELECTION` |
| Platform-admin activation | PASS | `200`; platform access/refresh token çifti üretildi |
| `sessions/me` | PASS | `200`; `GLOBAL_PLATFORM_ADMIN` scope |
| Platform refresh | PASS | `200`; access ve refresh tokenları döndürüldü |
| Logout | PASS | `204` |
| Bozuk token fail-closed | PASS | provider exchange `401 UNAUTHENTICATED` |
| Yanlış issuer/audience/pool matrisi | PASS | Backend otomatik negatif testleri |
| Sentetik uçtan uca smoke | PASS | Yalnız `alpha-smoke@invalid.example`; gerçek veri yok |
| Secret taraması | PASS | `./tooling/check_no_secrets.sh` |
| Repo sınırı | PASS | `./tooling/check_repo_boundaries.sh` |
| Backend test/build | PASS | `./gradlew build`; tam suite geçti |
| Git diff kontrolü | PASS | `git diff --check` |
| GitHub kalite kapıları | PASS | PR head `ba52264`; zorunlu işler `SUCCESS` |

## Başlangıç ve ücretsiz katman ölçümü

- Uyuyan Free instance'ın ölçülen public health dönüşü: `26.338 s`.
- Hemen sonraki sıcak health dönüşü: `205 ms`.
- İlk migration'lı deploy'un deploy başlangıcından live durumuna geçişi yaklaşık `102 s` sürdü.
- Render, Free instance için hareketsizlik sonrası `50 s` veya daha uzun gecikme olabileceğini ayrıca
  bildirir. Bu ölçümler SLA değildir ve ağ/sağlayıcı yüküyle değişir.

## Maliyet, risk ve veri dayanıklılığı

Tahmini dış ödeme hedefi `0 USD/ay`dır: Render Free, Supabase Free ve tek sentetik Cognito MAU'su
ücretsiz katmanlardadır. Bu garanti değildir; kota, fiyat, data transferi veya yanlışlıkla açılan
ücretli özellikler ek maliyet doğurabilir. USD 5 aylık budget ve %80 alarmı bu riski sınırlar.

- Render Free uyur; cold start vardır ve production SLA sağlamaz.
- Supabase Free düşük etkinlikte duraklayabilir; otomatik PITR yoktur. Veri tamamen sentetik ve
  migration/bootstrap ile yeniden üretilebilir.
- Cognito'da SMS/e-posta gönderimi, federasyon ve ücretli güvenlik eklentileri açılmamıştır.
- Ortam kapalı alfa, best-effort ve yeniden üretilebilirdir; production dayanıklılığı iddiası yoktur.

## Kaynak kaydı ve teardown

AWS, Render, Supabase ve sentetik kullanıcı kaynaklarının ID/ARN, amaç, tahmini maliyet ve teardown
sırası repo dışında, CloudShell'deki `~/.alpha-002-secure/inventory.json` dosyasında `0600`
izinleriyle saklanır. Dosya bütünlüğü `SECURE_INVENTORY_OK` ile doğrulanmıştır.

Teardown sırası:

1. Render Web Service ve Blueprint'i sil; runtime IAM access key'ini kaldır.
2. Supabase projesini sil.
3. Sentetik Cognito kullanıcısını sil.
4. Cognito CloudFormation stack'ini kaldır; pool, client, domain ve dar IAM kullanıcısının yokluğunu doğrula.
5. AWS budget ve SNS topic'i sil.

## Root oturumu istisnası

AWS kurulumu, kullanıcının açıkça yetkilendirdiği mevcut MFA korumalı root Console/CloudShell
oturumunda yürütüldü. Bu, normal güvenlik sözleşmesindeki root yasağına karşı **yüksek riskli,
yalnız ALPHA-002'ye özgü ve geçici bir istisnadır**. Root access key oluşturulmadı. Cognito runtime
için yalnız dar IAM kullanıcı anahtarı oluşturuldu ve secret doğrudan Render secret alanına yazıldı.
Parola, MFA kodu, session tokenı veya credential istenmedi, repoya/loga/rapora kaydedilmedi.
CloudShell geçici ALPHA/AWS ortam değişkenleri ve shell history temizlendi.

PR draft olarak bırakılmıştır; merge edilmemiştir ve `GOREV_DURUMU.md` değiştirilmemiştir.
