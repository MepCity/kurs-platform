# A-004R3 Cognito maliyet, operasyon ve teardown kanıtı

## Görev başlangıç sözleşmesi

- **Görev:** A-004R3 — Cognito maliyet/operasyon kararını kapat ve geçici kaynakları kaldır.
- **Bağımlılıklar:** PLAN-005, A-004, A-010, A-004R1 ve A-004R2 `main` üzerinde tamamlanmış;
  görev `READY` durumundaydı.
- **Değiştirilen alan:** ADR-004 sağlayıcı kararı ile bu karar/teardown kanıt belgesi.
- **Beklenen çıktı:** Deney kanıtlarının karşılaştırılması, nihai sağlayıcı ve IAM-001
  girdileri, gerçekleşen maliyet ve geçici kaynakların yokluk kanıtı.
- **Kabul ölçütleri:** Cognito/budget/bootstrap IAM temizliği; provider ve platform güvenlik
  sınırlarının korunması; secret bırakılmaması; kalan IAM rolü/policy sınırının doğru raporlanması.
- **Test yöntemi:** MFA korumalı kısa ömürlü STS oturumunda salt-okunur ön/son envanter,
  AWS CLI silme çağrıları, CloudTrail olay korelasyonu, repo sınır ve secret taramaları.

`GOREV_DURUMU.md` bu görevde değiştirilmemiştir.

## Nihai karar

V1 için global son kullanıcı kimlik doğrulama sağlayıcısı **Amazon Cognito Essentials User
Pool** olarak kabul edilmiştir. Keycloak self-managed, bu kararın revizyon kapısındaki fallback
seçeneğidir; production kaynağı bu deney havuzundan türetilmez.

Kararın dayanakları şunlardır:

- A-004R1; secretsiz public mobile client, Authorization Code + PKCE `S256`, username + geçici
  parola, ilk giriş parola değişimi ve kayıp create yanıtı uzlaştırmasını gerçek Cognito üzerinde
  kanıtladı.
- A-004R2; refresh rotation/reuse reddi, disable/global sign-out, platform cihaz/kurum iptali,
  provider kesintisi ve olay kaybında fail-closed uzlaştırmayı gerçek sağlayıcı çağrıları ve
  bağımsız platform modeliyle kanıtladı.
- Deneyde gerçekleşen Cognito maliyeti `0.0 USD` oldu. Bu sonuç bir fiyat garantisi değildir;
  production havuzu açılmadan yeni bütçe ve bildirim kaynağı zorunludur.
- Yönetilen hizmet, ilk sürümde ayrı Keycloak compute/DB/TLS/yedek/yükseltme/CVE/HA işletimini
  kaldırır. AWS hesap, bölge, IAM ve servis bağımlılığı kabul edilmiş bir risktir.

## Bağlayıcı provider/platform sınırı ve IAM-001 girdileri

1. Cognito yalnız global kimlik doğrular. Platform kimliği `(issuer, subject)` ile iç
   `user_id`ye eşlenir; kurum üyeliği, rol, izin, sınıf ve yetki Cognito claim/gruplarından
   üretilmez.
2. Mobil, secretsiz public client ile sistem tarayıcısında Authorization Code + PKCE `S256`
   kullanır. Redirect URI tam allow-listlidir; `state` ve `nonce` doğrulanır. Client secret,
   implicit flow ve password grant production mobil clientında yoktur.
3. Cognito access/ID/refresh tokenları yalnız token değişimi sınırına kadardır. Kurum API'leri
   yalnız platformun en az 256-bit rastgele opaque access tokenını kabul eder; DB ham token
   yerine pepper'lı HMAC özetini tutar.
4. Platform refresh tokenı opaque, dönen, tek kullanımlı ve `family_id` ile kapsamlıdır. Eski
   token reuse aynı aileyi atomik iptal eder. Kurum-kapsamlı iptal başka kurum ailesini kapatmaz.
5. Her üyelik/cihaz ailesinde güncel `session_generation` canlı denetlenir. Provider global
   disable, parola güvenlik değişimi veya global sign-out olayı ilgili kullanıcının bütün
   platform ailelerine idempotent fail-closed iptal olarak yansıtılır.
6. Yeni platform token ailesi üretilirken provider durumu kanonik olarak doğrulanır. Provider
   sonucu erişilemez, belirsiz veya iptal edilmişse yeni aile üretilmez; mevcut kuyruk yazısı
   başarı sayılmaz ya da kalıcı kuyruktan silinmez.
7. Provider command, olay tüketimi ve durum taraması ayrı kalıcı checkpoint/dedup kayıtları
   taşır. Aynı `(provider, realm/pool, event_id)` tekrar güvenlidir; bilinmeyen subject veya
   belirsiz güvenlik olayı sessizce atlanmaz, fail-closed iptal ve alarm üretir.
8. Pilot başlangıç SLO'su: provider güvenlik durumu en çok **5 dakikada** uzlaştırılır; olay/
   command gecikmesi **2 dakikada** alarm, **5 dakikada** escalation üretir. Sayısal eşikler
   IAM-001/IAM-009 yük ve kesinti testleriyle doğrulanmadan production açılmaz; değişiklik ADR
   gerektirir.
9. Admin provisioning, username + tek kullanımlı geçici parola ve ilk girişte zorunlu parola
   değişimi kullanır. Ham parola/token; repo, audit, log, telemetry veya kalıcı idempotency
   sonucuna yazılmaz.
10. Cognito yönetim kimliği en az yetkili, kısa ömürlü çalışma kimliğidir. Root principal,
    kişisel hesap veya uzun ömürlü access key normal işletim mekanizması değildir.

## Maliyet ve alarm kanıtı

Teardown öncesi son salt-okunur sorgu:

| Alan | Sonuç |
|---|---|
| Budget | `kurs-platform-a004r1-monthly-cost` |
| Aylık limit | `5.0 USD` |
| Gerçekleşen | `0.0 USD` |
| Tahmini | `None` — AWS veri üretmedi |
| Bildirim | 4 |
| Subscriber | Her bildirimde bir EMAIL subscriber; adres kanıta alınmadı |
| Son durum | Budget ve bildirim/subscriber sorguları `NotFound` ile yok |

Dört eşik A-004R1'deki `%80 gerçekleşen`, `%100 tahmini`, `%180 gerçekleşen` ve `%180
tahmini` bildirimleridir. AWS Budgets verisi gecikmeli olabilir ve harcamayı otomatik durdurmaz.
Deney sonucu yalnız gözlenen tüketimi gösterir; production fiyatı, SMS, advanced security,
destek, egress veya kur maliyeti için taahhüt değildir.

## Teardown kimliği ve fail-closed yürütme

Silme öncesi aktif kimlik root olmayan, MFA korumalı ve en fazla bir saatlik STS oturumuydu:

`arn:aws:sts::604561273748:assumed-role/kurs-platform-a004r2-experiment/kurs-platform-a004r2-bootstrap`

Ön envanterde pool `eu-central-1_KLIwq1vfJ`, domain
`kurs-platform-a004r1-5407ba`, bir sentetik kullanıcı, sıfır app client, aktif domain,
`ESSENTIALS` tier ve `INACTIVE` deletion protection görüldü. Managed-login branding, public
clientın daha önce silinmesiyle zaten `ResourceNotFoundException` dönüyordu.

Bootstrap IAM user ön envanteri: access key `0`, MFA `1`, login profile `1`, attached managed
policy `IAMUserChangePassword`, inline policy `AssumeA004R2ExperimentRole`; group, signing
certificate, SSH key ve service-specific credential sayılarının her biri `0`.

İlk dar rol denemesinde eksik yetkiler `AccessDenied` ürettiğinde işlem fail-closed durdu. Yetki
koordinasyonu tamamlandıktan sonra her silme doğrudan başarılı AWS yanıtıyla yeniden yürütüldü;
önceki bileşik shell çıktısı başarılı silme kanıtı sayılmadı.

## CloudTrail silme kanıtları

| Kaynak/işlem | UTC | Event ID | Sonuç |
|---|---|---|---|
| Public app client `DeleteUserPoolClient` | 2026-07-15 12:50:10 | `bfa0dfd1-bb71-4d52-9a36-864601e8d05a` | SUCCESS |
| Sentetik kullanıcı `AdminDeleteUser` | 2026-07-15 17:16:59 | `40892ed1-b05b-4187-a170-ff1baa41a74b` | SUCCESS |
| Domain `DeleteUserPoolDomain` | 2026-07-15 17:17:01 | `66cb4fd9-76f1-4a06-9f0d-96b554e01d05` | SUCCESS |
| User pool `DeleteUserPool` | 2026-07-15 17:17:02 | `d5029596-2967-42fc-8ff9-553bf108b3f7` | SUCCESS |
| Login profile `DeleteLoginProfile` | 2026-07-15 17:29:00 | `7f1153e1-b52a-463c-bad0-fd87a3645e04` | SUCCESS |
| Managed policy detach `DetachUserPolicy` | 2026-07-15 17:29:01 | `26d5e490-6262-4e7e-a053-e0db13cce2f3` | SUCCESS |
| Inline policy `DeleteUserPolicy` | 2026-07-15 17:29:02 | `56e7aca6-ac76-4fe8-92db-2db2f1e2a91d` | SUCCESS |
| MFA deactivate `DeactivateMFADevice` | 2026-07-15 17:29:03 | `b705d4b8-4aeb-4d0a-9559-d171f7ff9bd4` | SUCCESS |
| Bootstrap user `DeleteUser` | 2026-07-15 17:29:05 | `9beacb7c-c223-4125-82c3-e063c5ed1a31` | SUCCESS |

Sanal MFA silme ve budget silme doğrudan API çağrıları sırasıyla
`DELETE_VIRTUAL_MFA=PASS` ve `DELETE_BUDGET_CONFIRMED=PASS` verdi. CloudTrail lookup sonucu
kanıt alınırken görünmedi; bu iki kaynak için bağlayıcı kanıt başarılı doğrudan çağrı ve aşağıdaki
yokluk sorgusudur.

Experiment rolünün final temizliğinde root kimliğiyle yapılan dar ön kontrol; rol üzerinde yalnız
beklenen beş inline policy bulunduğunu, attached managed policy ve instance profile sayılarının
`0` olduğunu gösterdi. Beş `DeleteRolePolicy` çağrısı ve ardından `DeleteRole` çağrısı ayrı ayrı
`PASS` verdi. Hemen sonraki ve tekrarlanan `iam:GetRole` sorguları `NoSuchEntity` döndürdü.
CloudTrail `DeleteRole` lookup'u olay yayılımı sırasında `NOT_FOUND` döndü; bu satır başarı kanıtı
olarak kullanılmadı. Bağlayıcı kanıt başarılı doğrudan silme çağrıları ile tekrarlanan
`GetRole -> NoSuchEntity` sonucudur.

## Son yokluk envanteri

| A-004R2 sıra | Kaynak | Son sorgu | Durum |
|---:|---|---|---|
| 1 | Public/helper app client | Pool yok; client listesi üretilemiyor | **YOK** |
| 2 | Sentetik kullanıcı | Pool yok; kullanıcı listesi üretilemiyor | **YOK** |
| 3a | Managed-login branding | `ResourceNotFoundException` | **YOK** |
| 3b | Cognito domain | `DescribeUserPoolDomain` kaynak döndürmedi | **YOK** |
| 4 | User pool `eu-central-1_KLIwq1vfJ` | `ResourceNotFoundException` | **YOK** |
| 5a | Budget | `NotFoundException` | **YOK** |
| 5b | Notification/subscriber | Budget yok; liste sorgusu `NotFoundException` | **YOK** |
| 6a | Bootstrap IAM user | `NoSuchEntity` | **YOK** |
| 6b | Bootstrap sanal MFA | İsimle eşleşen sanal MFA sayısı `0` | **YOK** |
| 7a | Experiment role | Tekrarlanan `iam:GetRole` sorgusu `NoSuchEntity` | **YOK** |
| 7b | Role inline policy'leri | Beklenen beş `DeleteRolePolicy` çağrısının her biri `PASS`; rol yok olduğundan policy namespace'i de yok | **YOK** |
| 8 | Yeni bootstrap STS üretimi | Bootstrap user yok; yeni oturum üretilemez | **PASS** |
| 9 | CloudShell dosya/credential izi | Ön tarama `0`; `/tmp` ve shell history temizlendi, AWS env unset edildi | **YOK** |

Tek provider yokluk kontrolü `POOL=ABSENT`, `CLIENTS=ABSENT`, `USERS=ABSENT`,
`DOMAIN=ABSENT`, `BRANDING=ABSENT`, `BUDGET=ABSENT`, `NOTIFICATIONS=ABSENT` ve
`PROVIDER_ABSENCE_PROOF=PASS` üretti. Bootstrap son kontrolü `BOOTSTRAP_USER=ABSENT` ve
`VIRTUAL_MFA_MATCH_COUNT=0` üretti.

### Final IAM rolü teardown kanıtı

Kullanıcının dar onayıyla root oturumunda yalnız `kurs-platform-a004r2-experiment` rolü ve ona
bağlı aşağıdaki beş inline policy silindi:

- `A004R3CloudShellTemporary`
- `A004R3DomainDescribeTemporary`
- `A004R3FinalCompletionTemporary`
- `A004R3TeardownTemporary`
- `kurs-platform-a004r2-experimentPolicy`

Ön kontrol `DELETE_PREFLIGHT=PASS`, attached managed policy sayısı `0` ve instance profile
sayısı `0` verdi. Her policy için `DELETE_ROLE_POLICY=<policy>:PASS`, rol için
`DELETE_ROLE=PASS` alındı. Sonrasında `ROLE_ABSENCE=PASS_NO_SUCH_ENTITY`, tekrarlanan kontrolde
`ROLE=ABSENT_NO_SUCH_ENTITY` ve toplam sonuçta `IAM_ROLE_TEARDOWN=PASS` üretildi. Rol yokken
inline policy'ler ayrı bir IAM kaynağı olarak var olamayacağından A-004R2 teardown listesinin
rol ve policy yokluğu maddeleri karşılanmıştır. Bu final işlemde başka AWS kaynağına dokunulmadı.

## Kabul sonucu ve sonraki kapılar

| Ölçüt | Sonuç |
|---|---|
| A-004R1/A-004R2 kanıtları karşılaştırıldı ve Cognito Essentials kararı kapatıldı | **PASS** |
| IAM-001 için provider/platform, token, iptal ve uzlaştırma girdileri yazıldı | **PASS** |
| Gerçekleşen maliyet, bütçe ve dört alarm kaydı raporlandı | **PASS** |
| Cognito pool/client/domain/branding/kullanıcı ve budget kaynakları yok | **PASS** |
| Bootstrap user/MFA/login profile/policy kaynakları yok | **PASS** |
| Repo/CloudShell secret ve geçici dosya izi yok | **PASS** |
| Experiment role ve deney policy'lerinin tamamı yok | **PASS** |

IAM-001 sağlayıcı sözleşmesi artık hazırlanabilir; A-013 ve sağlayıcıya bağlı uygulama işleri,
IAM-001 onayı ile production bütçe/alarm ve en az yetkili çalışma kimliği tanımlanmadan gerçek
Cognito kaynağı açamaz. A-004R3 PR'ının merge edilmesi görev panosunu otomatik güncellemez.
