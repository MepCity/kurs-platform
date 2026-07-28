# ALPHA-002 — Kapalı alfa backend ve Cognito ortamı

| Alan | Değer |
|---|---|
| Görev | ALPHA-002 — Kapalı alfa backend ve Cognito ortamını kur |
| Ortam | `development` — yalnız sentetik, best-effort kapalı alfa |
| Bölge | `eu-central-1` / Frankfurt |
| Durum | `REVIEW` |
| Branch | `alpha-002/kapali-alfa-backend-cognito` |
| PR | `https://github.com/MepCity/kurs-platform/pull/65` |

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
KURS_PLATFORM_PUBLIC_API_BASE_URL=https://kurs-platform-alpha-api-development.onrender.com
KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_1GH5JivoG
KURS_PLATFORM_COGNITO_CLIENT_ID=2c59dh2nf60fmk6chn6qq3eoqu
KURS_PLATFORM_COGNITO_REDIRECT_URI=kursplatform://oauth2redirect
```

AWS bölgesi operasyonel metaveri olarak `eu-central-1` değeridir; mobil `--dart-define`
paketinin parçası değildir.

Doğrudan kopyalanabilir, imzasız iOS release build komutu:

```bash
cd apps/mobile
flutter build ios --release --no-codesign \
  --dart-define=KURS_PLATFORM_ENVIRONMENT=development \
  --dart-define=KURS_PLATFORM_PUBLIC_API_BASE_URL=https://kurs-platform-alpha-api-development.onrender.com \
  --dart-define=KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_1GH5JivoG \
  --dart-define=KURS_PLATFORM_COGNITO_CLIENT_ID=2c59dh2nf60fmk6chn6qq3eoqu \
  --dart-define=KURS_PLATFORM_COGNITO_REDIRECT_URI=kursplatform://oauth2redirect
```

`alpha_runtime_config_test.dart` bu beş değerin gerçek `AppRuntimeConfig.fromEnvironment()`
yolundan kabul edildiğini kanıtlar; GitHub macOS kapısı aynı değerlerle release binary üretir.
Parola, access/refresh token, DB credential veya AWS secret bu pakete ve repoya yazılmamıştır.

## Gerçek ortam kabul matrisi — 28 Temmuz 2026

| Kanıt | Sonuç | Kayıt |
|---|---|---|
| Public HTTPS readiness | PASS | `GET /health` → `200`, `{"status":"UP"}` |
| Docker ve dar runtime | PASS | Render Free Docker; UID/GID `10001:10001` |
| Temiz PostgreSQL migration | PASS | Flyway `32/32`, latest version `32` |
| Superuser olmayan runtime | PASS | Health sorgusu `iam_runtime`, `rolsuper=false`, `rolbypassrls=false` doğrular |
| RLS sınırları | PASS | 32 uygulama tablosu; RLS kapsamındaki 28 tablonun 28'inde `FORCE RLS` |
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
| ALPHA-001 AppRuntimeConfig | PASS | Gerçek beş define ile hedefli 1/1 test |
| Mobil tam suite/analyze | PASS | 612 test; analyze 0 bulgu |
| iOS release build | PASS | Gerçek beş define; yerel `--release --no-codesign` binary |
| Teardown emniyetleri | PASS | 8 mock senaryo: root/account/region/tag/envanter, stack içeriği, dry-run ve sıralı execute/yokluk |
| Secret taraması | PASS | `./tooling/check_no_secrets.sh` |
| Repo sınırı | PASS | `./tooling/check_repo_boundaries.sh` |
| Backend test/build | PASS | `./gradlew clean test build`; 529 test, 0 hata/atlama |
| Git diff kontrolü | PASS | `git diff --check` |
| GitHub kalite kapıları | PASS | Düzeltme commit'i `c86b21c`; [Actions run 30356987924](https://github.com/MepCity/kurs-platform/actions/runs/30356987924); zorunlu işler `SUCCESS` |

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

`deploy/alpha/teardown_cognito.sh` varsayılan olarak yalnız preflight/dry-run çalıştırır. Sabit AWS
hesabı ve `eu-central-1` bölgesi, tam kaynak adları, `application=kurs-platform` ile
`environment=development` tagları, tek sentetik kullanıcı, access key sayısı, USD 5 budget ve
%80 alarm/SNS bağı eşleşmeden silme yoluna geçmez. Aynı `kurs-platform-alpha-*` önekinde başka
kaynak bulunursa fail-closed durur. Root varsayılan değildir; bu istisna ancak
`ALPHA_ALLOW_ROOT_TEARDOWN=true` ile açılabilir. Gerçek silme ayrıca
`ALPHA_TEARDOWN_EXECUTE=true` ve önceden silinmiş Render servisi için
`ALPHA_RENDER_SERVICE_DELETED=true` teyitlerini ister.

| Sıra | Kaynak/adım | Sahip | Doğrulama |
|---:|---|---|---|
| 1 | Render Web Service'i durdur/sil | Kullanıcı / Render Dashboard | Servis URL'si ve `srv-d9k8c1rm8hqs73btfr50` artık bulunmaz; teyit env'i ancak sonra verilir |
| 2 | Runtime IAM access key | AWS teardown scripti | Tam kullanıcıda 0/1 key preflight; ID raporlanmadan key silinir |
| 3 | `alpha-smoke@invalid.example` | AWS teardown scripti | Tam poolda beklenen tek sentetik kullanıcı doğrulanıp silinir |
| 4 | `kurs-platform-alpha-cognito-development` stack | AWS teardown scripti | Stack wait; pool, client, domain ve IAM user stack ile kalkar |
| 5 | `kurs-platform-alpha-monthly-development` budget | AWS teardown scripti | Tam ad, USD 5/month ve %80 alarm doğrulanıp silinir |
| 6 | `kurs-platform-alpha-budget-alerts-development` SNS | AWS teardown scripti | Subscription'lar, sonra tam topic silinir |
| 7 | AWS yokluk matrisi | AWS teardown scripti | Stack, IAM user, pool, budget ve topic için servis-özgü not-found kodları |
| 8 | Supabase projesi | Kullanıcı / Supabase Dashboard | `bughxtwdwblbxzadituk` proje kartı ve connect uçları artık bulunmaz |
| 9 | Render Blueprint ve nihai yokluk | Kullanıcı / Render Dashboard | `exs-d9k8aeht0dsc7395sf2g` ve servis listede/URL'de bulunmaz |

Canlı alfa kaynaklarında teardown çalıştırılmamıştır. Sözleşme, mock AWS CLI ile hem dry-run
silmesizliği hem de emniyetli silme sırası/yokluk matrisi üzerinden test edilir.

## Root oturumu istisnası

AWS kurulumu, kullanıcının açıkça yetkilendirdiği mevcut MFA korumalı root Console/CloudShell
oturumunda yürütüldü. Bu, normal güvenlik sözleşmesindeki root yasağına karşı **yüksek riskli,
yalnız ALPHA-002'ye özgü ve geçici bir istisnadır**. Root access key oluşturulmadı. Cognito runtime
için yalnız dar IAM kullanıcı anahtarı oluşturuldu ve secret doğrudan Render secret alanına yazıldı.
Parola, MFA kodu, session tokenı veya credential istenmedi, repoya/loga/rapora kaydedilmedi.
CloudShell geçici ALPHA/AWS ortam değişkenleri ve shell history temizlendi.

Bu runtime access key uzun ömürlü credential olduğundan kalıcı risktir. Rotation ve teardown
sahibi ürün sahibidir; key yalnız bu development Render servisindeki Cognito yönetim çağrıları
için ve kapalı alfa incelemesinin sonuna kadar kullanılabilir. En geç ALPHA-002 ortamı
kapatılırken, şüpheli kullanımda ise derhal, yeni anahtar oluşturulmadan önce mevcut key silinir;
son kullanım Render servisinin silinmesiyle kesilir ve AWS teardown yokluk kontrolüyle doğrulanır.

PR merge edilmemiştir ve `GOREV_DURUMU.md` değiştirilmemiştir.
