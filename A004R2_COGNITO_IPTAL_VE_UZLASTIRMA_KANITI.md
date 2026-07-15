# A-004R2 Cognito iptal, platform oturumu ve uzlaştırma kanıtı

## Görev başlangıç sözleşmesi

- **Görev:** A-004R2 — Cognito iptal, platform oturumu ve olay kaybı uzlaştırma deneyini yap.
- **Bağımlılık:** A-004R1 `main` üzerinde `DONE`; A-004R2 görev başlangıcında `READY` idi.
- **Değiştirilen alan:** Bu kanıt belgesi; bağımsız
  `experiments/a004r2_cognito_revocation/` deneyi ve secret göstermeyen fail-closed Cognito
  yeniden üretim betiği.
- **Beklenen çıktı:** Cognito refresh rotation/reuse, disable/global sign-out, platform cihaz ve
  kurum kapsamlı iptal, provider kesintisi ve kaçırılmış olay uzlaştırma kanıtı.
- **Kabul ölçütleri:** İptal edilmiş provider tokenı yeni platform ailesi üretemez; mevcut opaque
  aileler kapsamına göre ve idempotent iptal edilir; olay kaybı fail-closed uzlaştırılır; sentetik
  veri ve en az yetkili kısa ömürlü AWS rolü kullanılır; secret/token kanıta girmez.
- **Test yöntemi:** Node.js otomatik testleri, gerçek `eu-central-1` Cognito CLI çağrıları,
  CloudTrail/bütçe sorguları, final kaynak envanteri ve secret taraması.

## Güvenlik kapısı

- Hesap kanıtta maskelenmiştir: `6045****3748`.
- AWS konsolu MFA korumalı `kurs-platform-a004r2-bootstrap` kullanıcısıyla açıldı.
- CloudShell işlemleri 1 saatlik STS oturumundaki
  `assumed-role/kurs-platform-a004r2-experiment/a004r2-verification` rolüyle yürütüldü.
- `sts get-caller-identity` rol adı doğrulaması: **PASS**.
- Korunan A-004R1 user pool üzerinde dar Cognito erişimi: **PASS**.
- `iam:ListUsers` negatif kontrolü: **AccessDenied / PASS**.
- AWS access key, session token, parola, Cognito JWT/refresh tokenı, authorization code veya
  client ID kanıta ya da repoya yazılmadı.
- Yalnız A-004R1'den kalan tek sentetik kullanıcı ve sentetik user pool kullanıldı.

Konsolun bootstrap kimliği ile deney komutlarının STS rolü farklıdır. Bütün sağlayıcı çağrıları
CloudShell sürecindeki kısa ömürlü `AWS_*` STS değişkenleri etkin durumdayken yapıldı. Root
principal, kalıcı access key veya yeni IAM yönetim işlemi kullanılmadı.

## Deney tasarımı

### Platform opaque oturum katmanı

Bağımsız Node.js deneyi Cognito tokenını ürün API'lerinde kullanmaz. Doğrulanmış provider
durumundan sonra platformun kendi opaque access/refresh ailesini üretir. Bellek deposu yalnız
token SHA-256 özetlerini indeksler ve şu sınırları uygular:

1. Her refresh tokenı tek kullanımlıdır ve başarılı yenilemede yeni opaque refresh üretir.
2. Tüketilmiş refresh tokenının tekrar kullanımı bütün platform ailesini iptal eder.
3. Access/refresh kullanımında kullanıcı, üyelik ve cihaz `session_generation` değerleri
   karşılaştırılır.
4. Tek cihaz iptali yalnız hedef cihaz ailelerini; kurum iptali yalnız hedef üyeliğin ailelerini;
   global provider olayı kullanıcının bütün kurum ailelerini etkiler.
5. Cihaz ve kurum iptali tekrarı ikinci generation artışı üretmez. İptal sonrası aynı bağlamda
   yeniden aile üretmek zorunlu yeniden kimlik doğrulama tamamlanana kadar reddedilir.
6. Provider doğrulaması kullanılamıyorsa yeni platform ailesi üretilmez.
7. Kaçırılmış disable olayı uzlaştırmada görülür; aktif aileler fail-closed iptal edilir ve user
   generation yalnız ilk terminal iptalde artar.
8. Provider event completion anahtarı `provider + realm + event ID` bileşimidir. Bilinmeyen
   subject completion yazmaz; eşleme oluştuktan sonra aynı olay yeniden uygulanabilir.
9. Event completion yalnız global aile iptali başarıyla uygulandıktan sonra kaydedilir.

Bu kod üretim IAM uygulaması, kalıcı veritabanı, provider adaptörü veya yeni bağlayıcı API
sözleşmesi değildir. IAM-001 ve sonraki uygulama görevlerine çalıştırılabilir karar girdisidir.

### Gerçek Cognito katmanı

Mevcut sentetik kullanıcı için yalnız deney süresince iki secretsiz helper app client oluşturuldu.
İkisinde de rotation `ENABLED`, grace period `0` ve refresh ömrü `1 gün` idi. Helper clientlar
her betiğin `trap` cleanup adımında silindi. Sentetik parola CloudShell belleğinde rastgele
üretildi, hiçbir çıktıda gösterilmedi ve işlem sonunda değişkenler/geçici hata dosyaları silindi.
Akış `scripts/run-cognito-experiment.sh` ile yeniden üretilebilir hâle getirildi. Betik yanlış
principal, bölge, budget, rotation, beklenen ret, uzlaştırma veya cleanup sonucunda non-zero exit
verir; bütün çıkış yollarında helper clientı silip sentetik kullanıcıyı etkin duruma döndürür.

## Sonuçlar

### Platform otomatik testleri

| Senaryo | Sonuç |
|---|---|
| Refresh tek kullanımlı rotation | PASS |
| Eski refresh reuse ile tam aile iptali | PASS |
| Provider kesintisinde yeni aileyi fail-closed reddetme | PASS |
| İptal/devre dışı provider assertionından yeni aileyi reddetme | PASS |
| Global provider olayının bütün kurum ailelerini idempotent iptali | PASS |
| Bilinmeyen subject olayının pending kalıp eşleme sonrası aynı event ile uygulanması | PASS |
| `provider + realm + event ID` dedupe izolasyonu | PASS |
| Tek cihaz iptalinin diğer cihazı etkilememesi | PASS |
| Kurum iptalinin diğer kurumu etkilememesi | PASS |
| Kaçırılmış disable olayının uzlaştırılması | PASS |
| Uzlaştırma sağlayıcısı kullanılamadığında aktif ailelerin fail-closed iptali | PASS |
| Provider erişilemezliğinde user generation'ın tam bir kez artması | PASS |
| Rotation assertion başarısızlığında deneyin non-zero exit vermesi | PASS |
| Gerçek Cognito betiğinin eksik/yanlış güvenlik girdisinde fail-closed durması | PASS |

`npm test` sonucu: **14/14 PASS**. `npm run experiment` özeti gerçek token karşılaştırmasından
hesaplanan rotation, reuse detection ve tam aile iptalini **PASS**, opaque değer çıktısını
`false` gösterdi. Sonuçlardan biri `FAIL` olursa çalıştırıcı non-zero exit verir.

### Gerçek Cognito sonuçları

| Senaryo | Gözlem | Sonuç |
|---|---|---|
| Rotation yapılandırması | Helper client `RefreshTokenRotation.Feature=ENABLED` | PASS |
| İlk refresh üretimi | Auth yanıtında refresh var/yok kontrolü `true` | PASS |
| Refresh rotation | `GetTokensFromRefreshToken` farklı ardıl refresh üretti | PASS |
| Eski refresh reuse | 2 saniye sonra `RefreshTokenReuseException` | PASS |
| Global sign-out | `AdminUserGlobalSignOut` sonrası refresh reddedildi | PASS |
| Kullanıcı disable | Disable öncesi refresh, disable sonrası reddedildi | PASS |
| Disabled yeni giriş | `AdminInitiateAuth` reddedildi | PASS |
| Olay kaybı uzlaştırması | Event tüketilmeden `AdminGetUser.Enabled=False` bulundu | PASS |
| Deney sonrası kullanıcı | `Enabled=True` | PASS |
| Geçici helper temizliği | `kurs-platform-a004r2-*` client sayısı `0` | PASS |
| Secret çıktısı | Parola/token/JWT çıktısı yok | PASS |

Cognito API sözleşmesi rotation açıkken başarılı yenilemenin yeni refresh üretip eskisini grace
süresi sonunda geçersizleştirdiğini ve tekrar kullanımda `RefreshTokenReuseException` verdiğini
belirtir. Deneyde grace `0` olduğundan eski token hemen geçersizleşti:
[GetTokensFromRefreshToken](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_GetTokensFromRefreshToken.html),
[RefreshTokenRotationType](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_RefreshTokenRotationType.html).

`AdminUserGlobalSignOut` hedef kullanıcının provider refresh/ID/access tokenlarını iptal eder.
Ancak self-contained JWT'yi yalnız imza ve süreyle doğrulayan bağımsız bir kütüphane provider
iptalini kendiliğinden göremez. Bu nedenle ürün API'leri provider JWT'sini oturum tokenı olarak
kabul etmemeli; exchange ve uzlaştırma sonrası yalnız platform opaque tokenını kabul etmelidir:
[Cognito token revocation](https://docs.aws.amazon.com/cognito/latest/developerguide/token-revocation.html),
[AdminUserGlobalSignOut](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminUserGlobalSignOut.html).

## Olay kaybı ve provider kesintisi kararı

Deney, provider olayının teslim edildiğini varsaymaz. Disable çağrısından sonra olay tüketimi
bilinçli olarak atlandı ve kanonik `AdminGetUser` snapshotı `Enabled=False` döndürdü. Platform
uzlaştırıcısı aynı durumda kullanıcı `session_generation` değerini yalnız ilk terminal iptalde
artırıp bütün platform ailelerini idempotent iptal eder. Dedupe anahtarı provider ve realm
kapsamındaki event ID'dir. Subject eşlemesi yoksa olay `PENDING_MAPPING` kalır; completion
yazılmaz. Eşleme oluştuktan sonra aynı event yeniden geldiğinde global iptal uygulanır ve ancak
ondan sonra completion kaydedilir.

Gerçek bir AWS bölgesel kesintisi kasıtlı olarak oluşturulmadı. Bunun yerine provider portunun
`available=false` sonucu otomatik testte deterministik üretildi. Bu durumda:

- provider assertionından yeni platform ailesi oluşturulmaz;
- uzlaştırma sonucu güvenilir değilse aktif aileler fail-closed iptal edilir;
- başarısız provider komutu başarılı veya tamamlanmış sayılmaz;
- kalıcı komut/olay kaydı sonraki IAM görevinde başarı kanıtına kadar korunmalıdır.

Bu deney provider çağrısı ve platform iptalinin dağıtık tek transaction olduğu iddiasında
bulunmaz. Yerel erişimi önce kesme, provider komutunu idempotent tekrar etme ve kanonik snapshot
ile uzlaştırma sınırı korunur.

## CloudTrail kanıtı

İstek/yanıt gövdeleri kanıta alınmadı. Yalnız olay adı, UTC zamanı ve event ID kaydedildi:

| Olay | Zaman (UTC) | Event ID | Sonuç |
|---|---|---|---|
| `AdminUserGlobalSignOut` | 2026-07-15 11:06:10 | `98a989e1-75f8-4df1-8dce-c8389c6d1dce` | SUCCESS |
| `AdminDisableUser` | 2026-07-15 11:06:13 | `31e3d53f-259e-4dda-9f80-66a3ba67f60e` | SUCCESS |
| `AdminEnableUser` | 2026-07-15 11:06:17 | `0f206a89-d41f-4400-9ebc-fb152c721cdc` | SUCCESS |
| Son helper `CreateUserPoolClient` | 2026-07-15 11:06:59 | `57540301-354e-43e7-9c73-150d278de3ca` | SUCCESS |
| Son helper `DeleteUserPoolClient` | 2026-07-15 11:07:05 | `44bcf98b-e84d-4efa-bd57-79d7b1356efb` | SUCCESS |

## Maliyet ve final kaynak durumu

- Bütçe: `kurs-platform-a004r1-monthly-cost`.
- Limit: `5.0 USD`.
- Son gerçekleşen harcama: `0.0 USD`.
- Son tahmini harcama: AWS henüz değer üretmedi (`null`).
- A-004R2 helper client sayısı: `0`.
- Sentetik kullanıcı: `CONFIRMED`, `Enabled=True`.
- A-004R1 user pool, public mobile client, domain, branding ve budget A-004R3 teardown için
  korunmuştur.
- A-004R2'nin deney sırasında oluşturduğu helper clientlar temizlendi; fakat görevin güvenlik
  ön koşulu için koordinasyon adımında oluşturulan `kurs-platform-a004r2-bootstrap` IAM userı,
  ona bağlı sanal MFA cihazı, `kurs-platform-a004r2-experiment` IAM rolü ve bu ikisine bağlı
  deney-özel policy/attachment kaynakları A-004R3 teardown için **halen vardır**. Dolayısıyla
  final envanter “IAM kaynağı kalmadı” değildir. Uzun ömürlü access key kullanılmadı; A-004R3
  silmeden önce access key/credential sayısını ayrıca sıfır olarak doğrulamalıdır.

### A-004R3 eksiksiz teardown listesi

A-004R3 aşağıdaki sırayı envanter kimlikleri ve yokluk sorgularıyla kapatmalıdır:

1. `kurs-platform-a004r1-*` önekli beklenmedik/helper app client sayısını doğrula ve varsa sil;
   ardından bilinen public mobile app clientı sil.
2. Sentetik Cognito kullanıcılarını sil; kullanıcı sayısının sıfır olduğunu doğrula.
3. Managed-login branding kaynağını ve Cognito domainini sil; domain/branding yokluğunu sorgula.
4. Silme koruması `INACTIVE` olan A-004R1 user poolu sil ve `ResourceNotFoundException` ile
   yokluğunu doğrula.
5. `kurs-platform-a004r1-monthly-cost` budget için nihai gerçekleşen/tahmini maliyeti ve dört
   notification/subscriber kaydını raporla; budgetı silip yokluğunu doğrula.
6. `kurs-platform-a004r2-bootstrap` IAM userında varsa access key, login profile, signing
   certificate, SSH/service credential, group membership, attached managed policy ve inline
   policy kaynaklarını envantere al; MFA cihazını deactivate et, sanal MFA cihazını sil ve sonra
   userı sil.
7. `kurs-platform-a004r2-experiment` rolündeki attached managed policy, inline policy ve varsa
   instance-profile ilişkilerini kaldır; rolü sil. Deney için oluşturulmuş customer-managed
   policy varsa bütün non-default versionlarını, ardından default version/policy kaynağını sil.
8. Yeni STS oturumu üretilemediğini doğrula ve mevcut en fazla 1 saatlik oturumun süresinin
   dolmasını bekle; root veya başka IAM kimliğiyle deneyi sürdürme.
9. CloudShell home ve geçici dizinlerinde deney scripti, token/parola dosyası veya credential
   exportu kalmadığını; yerel/CI artifact ve repo secret taramasının temiz olduğunu doğrula.
10. User pool, app client, domain, branding, sentetik kullanıcı, budget, notification/subscriber,
    bootstrap user, MFA device, deney rolü ve deney-özel policy sorgularının tamamını “yok”
    sonucuyla tek teardown tablosunda kaydet.

## Kabul ölçütleri

- [x] Refresh rotation ve eski token reuse gerçek Cognito üzerinde kanıtlandı.
- [x] Provider disable/global sign-out sonrası refresh reddi kanıtlandı.
- [x] Disabled provider kimliği yeni platform oturumu üretemez.
- [x] Platform refresh reuse bütün opaque aileyi iptal eder.
- [x] Tek cihaz, kurum ve global iptal kapsamları birbirinden ayrıldı.
- [x] Mevcut access tokenlar `session_generation` ile sonraki istekte reddedilir.
- [x] Provider kesintisi ve uzlaştırma belirsizliği fail-closed ele alındı.
- [x] Kaçırılmış disable olayı kanonik provider snapshotıyla bulundu.
- [x] Provider olayı ve platform aile iptali idempotenttir.
- [x] UNKNOWN_SUBJECT completion yazmaz; eşleme sonrası aynı olay global iptali uygular.
- [x] Provider/realm + event ID dedupe sınırı uygulanmıştır.
- [x] Provider erişilemezliğinde user generation tam bir kez artar.
- [x] Deney runnerı ölçülen rotation başarısızlığında non-zero exit verir.
- [x] Gerçek Cognito akışı secret göstermeyen fail-closed betikle yeniden üretilebilir.
- [x] En az yetkili, kısa ömürlü STS rolü doğrulandı; IAM yönetimi reddedildi.
- [x] Sentetik veri kullanıldı; secret/token repo, artifact ve kanıta yazılmadı.
- [x] Geçici helper clientlar silindi, kullanıcı etkin duruma getirildi ve bütçe doğrulandı.

## Sınırlamalar ve sonraki görev

- Gerçek AWS bölgesel kesintisi oluşturulmadı; kesinti davranışı provider portunda deterministik
  hata ile test edildi.
- Deney bellekte çalışır; PostgreSQL transaction, RLS, lease/fencing, durable command ve event
  cursor uygulaması IAM-003 ve ilgili IAM test görevlerinin kapsamıdır.
- Deney provider event taşıma kanalını seçmez. Poll/cursor sıklığı, CloudTrail teslim gecikmesi ve
  operasyonel alarm eşikleri A-004R3/IAM-001 girdisi olarak kesinleşmelidir.
- Cognito JWT'sinin offline imza doğrulamasından geçmesi aktif platform oturumu kanıtı değildir.
- A-004R3 nihai maliyet/operasyon kararını kapatmalı ve yukarıdaki on maddelik kaynak listesinin
  tamamını yokluk kanıtıyla kaldırmalıdır.
