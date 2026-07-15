# A-004R1 Cognito provisioning ve mobil PKCE kanıtı

## Görev başlangıç sözleşmesi

- **Görev:** A-004R1 — Cognito provisioning, ilk parola ve gerçek mobil PKCE deneyini yap
- **Bağımlılıklar:** PLAN-005, A-004 ve A-010 `main` üzerinde `DONE`; görev `READY` idi.
- **Değiştirilen alan:** Bu kanıt belgesi ile `experiments/a004r1_cognito_pkce/` altındaki bağımsız
  Flutter deney uygulaması.
- **Beklenen çıktı:** eu-central-1 Cognito Essentials provisioning'i, sentetik kullanıcının ilk
  parola akışı, kayıp create yanıtı uzlaştırması ve gerçek mobil Authorization Code + PKCE kanıtı.
- **Kabul ölçütleri:** 5 USD bütçe alarmı kaynaktan önce; secretsiz public client; sentetik veri;
  ilk parola; tekilleştirilmiş kayıp yanıt; gerçek mobil code + PKCE; maskeli envanter ve maliyet.
- **Test yöntemi:** AWS CLI doğrulama sorguları, Android sistem tarayıcılı gerçek giriş,
  Flutter test/analyze/format ve Android debug build.

## AWS kimliği ve sınırlar

- Hesap: `6045****3748`
- Aktif principal: `arn:aws:iam::6045****3748:root`
- Cognito bölgesi: yalnız `eu-central-1` (Frankfurt)
- Yeni IAM user, access key, client secret, IAM policy veya kalıcı yerel AWS kimliği oluşturulmadı.
- Mevcut IAM, billing ayarı, domain veya proje kaynağı değiştirilmedi.
- AWS işlemleri kullanıcının açık Chrome oturumundaki CloudShell üzerinden yapıldı.
- Deneyde yalnız sentetik kullanıcı ve sentetik UUID kullanıldı.

### Güvenlik bulgusu ve sonraki deney kapısı

**Yüksek — A-004R1 root principal ile yürütüldü.** Hesap root principal'ı deney için en az
ayrıcalık ilkesini karşılamaz. Bu turda IAM, permission set veya rol değiştirilmedi; mevcut
güvenlik sınırı genişletilmedi.

A-004R2 ve A-004R3 için zorunlu ön koşul; IAM Identity Center kısa ömürlü oturumu veya STS
`AssumeRole` ile alınmış, yalnız `kurs-platform-a004r1-*` deney kaynaklarının gerekli işlemlerine
izin veren en az yetkili roldür. İş başlangıcındaki `sts get-caller-identity` sonucu root, IAM user
ya da uzun ömürlü access key gösterirse çalışma fail-closed durmalı ve `BLOCKED` raporlanmalıdır.
Kalan deneyler root principal ile yürütülmeyecektir.

## Bütçe ve maliyet kapısı

İlk Cognito kaynağından önce hesap-kapsamlı AWS Budgets kaynağı
`kurs-platform-a004r1-monthly-cost` oluşturuldu. Aylık limit 5 USD'dir. Dört bildirim vardır:

| Tür | Eşik | USD karşılığı |
|---|---:|---:|
| Gerçekleşen | %80 | 4 USD |
| Tahmini | %100 | 5 USD |
| Gerçekleşen | %180 | 9 USD |
| Tahmini | %180 | 9 USD |

Son doğrulamada gerçekleşen harcama `0.0 USD`, tahmini harcama `None` idi. AWS Budgets verisi
gecikmeli olabilir ve harcamayı otomatik durdurmaz. 9 USD eşikleri 10 USD yazılı onay sınırına
yaklaşma uyarısıdır; bu seviyede A-004R1/A-004R2 çalışması durmalıdır. Tek sentetik MAU ve mevcut
kısa deney için ek aylık maliyet tahmini yaklaşık 0 USD'dir; bu bir fiyat garantisi değildir.

Maskeli `describe-budget`/bildirim özeti: aylık `COST`, limit `5.0 USD`, gerçekleşen `0.0 USD`,
tahmin `None`, dört bildirim ve her bildirimde bir EMAIL subscriber (toplam dört) vardır.
Subscriber adresi kanıta alınmamıştır. Dört subscriber kaydı tek hedefe işaret eder; AWS'ten
okunan hedefin SHA-256 özeti
`60566ab1aea787b60020a091733f8b28d1feec215642b222baee4743695999a0` olup ürün sahibinin
verdiği bütçe alarmı hedefinin aynı normalizasyonla alınan özetiyle eşleşmiştir. AWS Budgets
global bir kontrol düzlemi hizmeti olduğu için CloudTrail kaydı `us-east-1` gösterir; Cognito iş
yükü kaynaklarının tamamı `eu-central-1` içindedir.

| Olay | Zaman (UTC) | Event ID | CloudTrail region | Sonuç |
|---|---|---|---|---|
| `CreateBudget` | 2026-07-15 07:05:08 | `1785c926-04e9-4d48-b5cf-9f245ebef285` | us-east-1 | SUCCESS |
| İlk başarılı `CreateUserPool` | 2026-07-15 07:07:55 | `2af64566-8c87-4465-901a-a9b92006ee8d` | eu-central-1 | SUCCESS |

Budget, ilk başarılı Cognito kaynak oluşturmasından **167 saniye önce** oluşturulmuştur.

## Kaynak envanteri

| Tür | Bölge/kapsam | Maskeli kimlik | Son durum | Yaşam döngüsü |
|---|---|---|---|---|
| AWS Budget | hesap-kapsamlı/global | `kurs-platform-a004r1-monthly-cost` | 5 USD, 4 bildirim | A-004R3 temizler |
| Cognito user pool | eu-central-1 | `eu-central-1_KL****1vfJ` | Essentials, ACTIVE | A-004R2 için korunur; A-004R3 temizler |
| Cognito public app client | eu-central-1 | `23sn66****31sm` | secretsiz, ACTIVE | A-004R2 için korunur; A-004R3 temizler |
| Cognito managed-login domain | eu-central-1 | `kurs-platform-a004r1-****ba` | ACTIVE | A-004R2 için korunur; A-004R3 temizler |
| Managed-login branding | eu-central-1 | `ad246db2****681e` | v2, ACTIVE | Domain ile A-004R3 temizler |
| Sentetik kullanıcı | eu-central-1 | `a004r1-****ba` | CONFIRMED | A-004R2 için korunur; A-004R3 temizler |
| İlk parola yardımcı clientı | eu-central-1 | `kurs-platform-a004r1-first-password-****ba` | silindi | Challenge sonrası aynı oturumda silindi |
| Reset negatif testi yardımcı clientı | eu-central-1 | `22bb2i****j2oi` | silindi; describe sayısı 0 | Negatif test sonrası aynı oturumda silindi |

User pool silme koruması `INACTIVE` bırakıldı; böylece A-004R3 teardown sahibi kaynağı
silebilir. A-004R3; kullanıcı, app client, branding/domain, user pool ve budget kaynağını silip
gerçekleşen nihai maliyeti kaydetmelidir. A-004R2 bu kaynakları silmemeli; gerekirse sentetik
kullanıcı parolasını yeniden güvenli yüzeyde sıfırlamalıdır.

## Provisioning ve ilk parola sonucu

- Pool tier: `ESSENTIALS`.
- Username case-insensitive; kendi kendine kayıt kapalı; kullanıcı yaratma yalnız admin.
- Parola politikası: en az 12 karakter; büyük/küçük harf, sayı ve sembol; geçici parola 1 gün.
- Kurtarma yöntemi yalnız admin; gerçek e-posta/telefon niteliği eklenmedi.
- Değişmez `custom:platform_user_id` niteliği sentetik UUID ile yazıldı.
- Public mobile clientta client secret yoktur; callback
  `kursplatforma004r1://oauth2redirect`, grant yalnız authorization code, scope `openid profile`.
- Access/ID token ömrü 10 dakika, refresh token ömrü 1 gündür. Refresh rotation A-004R2
  kapsamındadır ve bu görevde uygulanmadı.

`AdminCreateUser` başarılı yanıtı kasıtlı olarak kaybedildi. Sonuç, tam username ile sorgulanıp
değişmez `platform_user_id` eşleşmesi ve tek kullanıcı sayımıyla uzlaştırıldı. Aynı create komutu
yeniden gönderildiğinde `UsernameExistsException` alındı ve kullanıcı sayısı 1 kaldı.

Geçici parola ile `ADMIN_USER_PASSWORD_AUTH` başlatıldı; Cognito
`NEW_PASSWORD_REQUIRED` döndürdü. Yeni parola challenge'ı yanıtlandı, token sonuçları yalnız
var/yok olarak kontrol edildi ve kullanıcı `CONFIRMED` oldu. Bunun için oluşturulan secretsiz
yardımcı app client challenge tamamlanınca silindi; sonraki sorguda yardımcı client sayısı 0 idi.
Challenge yanıt dosyaları ve parola dosyaları CloudShell ile yerel geçici yüzeyden silindi.

### CloudTrail ve describe kanıtları

CloudTrail olaylarında request/response gövdeleri kanıta alınmadı; yalnız olay adı, UTC zamanı,
event ID, region ve sonuç tutuldu:

| Olay | Zaman (UTC) | Event ID | Region | Sonuç |
|---|---|---|---|---|
| `CreateUserPool` | 2026-07-15 07:07:36 | `981898bb-4e5e-4bd5-a8e8-f7d4b289eb5c` | eu-central-1 | `InvalidParameterException` |
| `CreateUserPool` | 2026-07-15 07:07:55 | `2af64566-8c87-4465-901a-a9b92006ee8d` | eu-central-1 | SUCCESS |
| `CreateUserPoolClient` | 2026-07-15 07:08:20 | `1497d155-3768-4c3b-bbc2-7e5940f5a4fe` | eu-central-1 | SUCCESS |
| `CreateUserPoolDomain` | 2026-07-15 07:08:35 | `f1af05d3-babf-4085-8211-009dc2df06b7` | eu-central-1 | SUCCESS |
| `AdminCreateUser` | 2026-07-15 07:10:58 | `361f7da1-2d74-4f34-b7cf-1ab29c5986c7` | eu-central-1 | SUCCESS |
| `AdminCreateUser` tekrar | 2026-07-15 07:11:25 | `7d77484f-5efd-40fa-9e6b-9d1819f45f85` | eu-central-1 | `UsernameExistsException` |
| Reset helper `CreateUserPoolClient` | 2026-07-15 08:51:44 | `1fbfc7e8-ec49-4e99-861a-60bb0ecadc3f` | eu-central-1 | SUCCESS |
| Eski geçici parola için `AdminSetUserPassword` | 2026-07-15 08:51:45 | `88a063e3-5a7c-459c-b7db-ea0c6c139945` | eu-central-1 | SUCCESS |
| Süre dolumu reseti `AdminSetUserPassword` | 2026-07-15 08:51:46 | `18d90299-be47-4d98-b33e-f209ab683765` | eu-central-1 | SUCCESS |
| Eski parola `AdminInitiateAuth` | 2026-07-15 08:51:47 | `86235e10-f7a9-4eb7-8f1a-6bdf664b1f77` | eu-central-1 | `NotAuthorizedException` |
| Yeni parola `AdminInitiateAuth` | 2026-07-15 08:51:48 | `9778184d-5ef9-4671-99da-66199f03ec90` | eu-central-1 | SUCCESS |
| `RespondToAuthChallenge` | 2026-07-15 08:51:49 | `9739baf2-6665-444a-be93-40c4030533a6` | eu-central-1 | SUCCESS |
| Reset helper `DeleteUserPoolClient` | 2026-07-15 08:51:51 | `1e998533-c03b-4611-8287-5459394fa7c7` | eu-central-1 | SUCCESS |

Create/delete olayları aynı maskeli helper client kimliğiyle korele edildi. Son
`list-user-pool-clients` sorgusu bu isim önekindeki client sayısını `0` gösterdi.

Maskeli describe özeti şöyledir: budget `5.0 USD`/4 notification/4 EMAIL subscriber; pool
`eu-cen****1vfJ`, `ESSENTIALS`, geçici parola `1 gün`, deletion protection `INACTIVE`; public
client `23sn66****31sm`, secret yok, code grant açık, scope `openid profile`, access/ID token
10 dakika, refresh token 1 gün; domain `kurs-p****07ba`, `ACTIVE`; kullanıcı
`a004r1****etic`, sayı `1`, durum `CONFIRMED`; helper client sayısı `0`.

### Bir günlük provider süresi ile 10 dakikalık teslim sınırı — deney sonucu

Cognito'nun `TemporaryPasswordValidityDays=1` ayarı yalnız provider üst sınırıdır; A-004'ün
`iam_secret_deliveries` için tanımladığı en fazla 10 dakikalık, tek-görüntüleme sınırını tek
başına uygulamaz. Bu deneyde değerlendirilen **aday yaklaşım**, teslim kaydı `READY → EXPIRED`
olduğunda ikinci bir geçici `AdminSetUserPassword` çağrısı yaparak önceki geçici parolayı provider
tarafında geçersizleştirmektir. Yeni rastgele reset değerinin kullanıcıya gösterilmeden atılması ve
sonraki teslim talebinin yeni bir provider resetine bağlanması da aday akışın parçasıdır; bunlar bu
aşamada bağlayıcı platform kararı değildir.

Negatif testte 10 dakikalık teslim süresinin dolması deterministik olarak tetiklenip ikinci reset
hemen uygulandı. İlk geçici parola `NotAuthorizedException` ile reddedildi; yalnız replacement
parola `NEW_PASSWORD_REQUIRED` challenge'ına ulaştı. Test sonunda kullanıcı sentetik kalıcı
parolayla `CONFIRMED` durumuna geri alındı, helper client silindi ve geçici secret dosyası sayısı
`0` doğrulandı. Sonuç yalnız **başarılı** `AdminSetUserPassword` resetinin eski parolayı hemen
geçersizleştirdiğini kanıtlar.

Provider kesintisinde çağrı hiç uygulanamazsa veya yanıt kaybında reset sonucunun başarılı olup
olmadığı belirlenemezse bu deney eski parolanın geçersizleştiğini kanıtlayamaz. Böyle bir durumda
eski geçici parola Cognito'nun bir günlük TTL'si dolana kadar kullanılabilir kalabilir; dolayısıyla
10 dakikalık teslim sınırı provider tarafından garanti edilmiş sayılmaz. Provider kesintisi ile
belirsiz reset sonucunun fail-closed davranışı ve uzlaştırılması A-004R2 kabul ölçütüdür. Yaklaşımın
nihai kabulü ile durable/idempotent provider-command tasarımı A-004R3 kararına bırakılmıştır. Bu
belge ADR veya veri modeli için yeni bağlayıcı tasarım kararı oluşturmaz.

## Gerçek mobil Authorization Code + PKCE sonucu

Android API 36 arm64 emülatöründe Flutter uygulaması gerçek Chrome Custom Tab açtı. Onaylanmış
sentetik kullanıcıyla girişten sonra custom URI callback'i AppAuth receiver'a döndü ve token
endpoint değişimi tamamlandı. Uygulama değerleri göstermeden şu kanıtların tamamını `true`
olarak render etti:

- authorization code alındı;
- PKCE `code_verifier` üretildi (AppAuth `S256` kullanır);
- OIDC nonce üretildi;
- access token, ID token ve refresh token alındı;
- access token son kullanma zamanı alındı.

Parola, authorization code, verifier, nonce, cookie veya token değerleri ekrana, loga, repoya ya
da kanıt belgesine yazılmadı. Deney bittikten sonra uygulama/Chrome emülatör verisi temizlendi ve
emülatör kapatıldı.

### AppAuth güvenlik sorumluluğu ve pinler

Deney `flutter_appauth 12.0.2` sürümünü hem `pubspec.yaml` hem lock dosyasında sabitler. Bu plugin
Android'de `net.openid:appauth:0.11.1`, iOS'ta `AppAuth 2.0.0` pinler. Gerçek mobil kanıt Android
AppAuth 0.11.1 üzerinde alınmıştır; iOS akışı ayrıca doğrulanmadan Android sonucu iOS'a
genellenmez.

- **State:** AppAuth rastgele state üretir; callback state'i request state ile eşleşmiyorsa
  `STATE_MISMATCH` ile response'u atar. Deney kodu bu kontrolü devre dışı bırakmaz.
- **Nonce:** AppAuth nonce üretip token isteğine taşır; authorization-code yanıtındaki ID token
  nonce'u beklenen request nonce'uyla eşleşmezse `ID_TOKEN_VALIDATION_ERROR/Nonce mismatch`
  üretir. Deney nonce'u `null` yapmaz.
- **PKCE:** AppAuth verifier/challenge'ı otomatik üretir, desteklenen Android platformunda
  SHA-256 tabanlı `S256` yöntemini kullanır ve aynı verifier'ı token exchange'e taşır. Deney
  verifier'ı `null` yapmaz veya `plain` yöntemi seçmez.

Sorumluluk native AppAuth kütüphanesindedir; Flutter uygulaması güvenli varsayılanları bozmaz,
client secret taşımaz ve yalnız var/yok kanıtını gösterir. Pinned kaynaklar:

- [flutter_appauth 12.0.2 kaynak etiketi](https://github.com/MaikuB/flutter_appauth/tree/flutter_appauth-v12.0.2)
- [AppAuth Android 0.11.1 state eşleştirmesi](https://github.com/openid/AppAuth-Android/blob/0.11.1/library/java/net/openid/appauth/AuthorizationManagementActivity.java#L347-L360)
- [AppAuth Android 0.11.1 nonce doğrulaması](https://github.com/openid/AppAuth-Android/blob/0.11.1/library/java/net/openid/appauth/IdToken.java#L291-L297)
- [AppAuth Android 0.11.1 S256/PKCE üretimi](https://github.com/openid/AppAuth-Android/blob/0.11.1/library/java/net/openid/appauth/AuthorizationRequest.java#L906-L953)

## Otomatik kontroller ve sınırlamalar

- `dart format --output=none --set-exit-if-changed lib test`: geçti.
- `flutter analyze`: geçti, issue yok.
- `flutter test`: geçti, 3 test.
- `flutter build apk --debug`: geçti.
- İzlenen dosyalarda AWS access key, private key ve JWT imzası arayan secret regex taraması:
  geçti, eşleşme yok.
- `git diff --check`: geçti.
- Android gerçek sistem tarayıcılı Authorization Code + PKCE: geçti.
- iOS kaynak yapılandırması ve custom scheme eklendi; yerel makinede tam Xcode/CocoaPods olmadığı
  için iOS simulator build çalıştırılamadı. iOS gerçek akış doğrulaması bu görevin açık
  sınırlamasıdır.

## Kaynaklar

- [Cognito authorization endpoint ve PKCE](https://docs.aws.amazon.com/cognito/latest/developerguide/authorization-endpoint.html)
- [Cognito managed login](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-managed-login.html)
- [AdminCreateUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminCreateUser.html)
- [AWS Budgets bildirimleri](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html)
- [flutter_appauth Android/iOS kurulumu](https://pub.dev/packages/flutter_appauth)

## Kapsam dışı ve sonraki görevler

- A-004R2: refresh rotation/reuse, provider kesintisi, disable/global sign-out, platform token
  ailesi iptali ve olay kaybı uzlaştırması.
- A-004R3: nihai Cognito maliyet/operasyon kararı, gerçekleşen maliyet ve eksiksiz teardown.
- IAM-001: provider kimliğini platform `user_id`, üyelik/rol/izin ve opaque token ailelerine
  bağlayan üretim sözleşmesi. Bu deney Cognito rol/grup claim'lerini ürün yetkisi saymaz.
