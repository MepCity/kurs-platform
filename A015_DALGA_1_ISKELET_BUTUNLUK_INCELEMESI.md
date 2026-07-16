# A-015 Dalga 1 iskelet bütünlük incelemesi

| Alan | Değer |
|---|---|
| Görev | A-015 — Dalga 1 iskelet bütünlük incelemesi |
| Sonuç | REVIEW — Dalga 1 çıkış kapısı karşılandı; Dalga 2 sözleşme görevleri başlayabilir |
| İncelenen bağımlılıklar | A-001–A-014, PLAN-005, A-004R3 |
| İnceleme tarihi | 16 Temmuz 2026 |

## 1. Görev başlangıç sözleşmesi

- **Görev kimliği ve başlığı:** A-015 — Dalga 1 iskelet bütünlük incelemesi.
- **Bağımlılıklar:** `GOREV_DURUMU.md` üzerinde A-001–A-014, PLAN-005 ve A-004R3 `DONE`;
  A-015 `READY` durumundadır.
- **Değiştirilecek dosyalar:** Bu inceleme belgesi. Görev panosu `GOREV_DURUMU.md`
  çalışan agent tarafından değiştirilmez.
- **Beklenen çıktı:** Dalga 1 teknoloji kararları, uygulama iskeleti, CI, ortam/secret ve
  gözlemlenebilirlik temelinin Dalga 2 öncesi çelişkisiz olduğunu gösteren entegrasyon raporu.
- **Kabul ölçütleri:** `AGENT_GOREV_PLANI.md` Dalga 1 çıkış kapısındaki beş ölçütün tamamı
  kanıtlarıyla doğrulanır; açık riskler bloklayan/bloklamayan olarak ayrılır.
- **Test yöntemi:** Repo sınırları ve secret taraması, tooling regresyonları, backend test/build,
  mobil format/analyze/test, Android debug build ve iOS binary build denemesi; yerel iOS ortam
  sınırlaması ayrıca raporlanır.

## 2. Amaç ve kapsam

Bu inceleme, Dalga 1'in teknoloji kararları ve iskelet çıktılarının birbiriyle uyumlu olduğunu
doğrular. Yeni teknoloji seçmez, provider kaynağı oluşturmaz, uygulama özelliği eklemez,
migration yazmaz ve görev panosunu güncellemez.

Dalga 1 iki ürün fazına yayılır: A-001–A-010 teknik karar ve risk doğrulama kapısını; A-011–A-015
ise Faz 1 platform temelini oluşturur. Bu belge, Dalga 2'deki IAM ve mobil kabuk sözleşmelerine
geçmeden önce bütünlüğü kapatır.

## 3. Bağımlılık ve kaynak doğrulaması

| Bağımlılık | Kanıt | Sonuç |
|---|---|---|
| PLAN-005 | Maliyet kademeleri, provider kapıları ve A-011/A-013/IAM-001 sıralaması yazıldı | Geçti |
| A-001 | Flutter kararı ve iOS/Android dikey deneme kanıtı var | Geçti |
| A-002 | Java 21/Spring Boot 4.1 modüler monolit kararı var | Geçti |
| A-003 | Supabase PostgreSQL, FORCE RLS, yedek/restore ve kademeli maliyet sınırı var | Geçti |
| A-004 + A-004R1–R3 | Cognito Essentials nihai karar, PKCE/provisioning, iptal/uzlaştırma ve teardown kanıtı var | Geçti |
| A-005 | Drift/SQLite tabanlı kalıcı kuyruk ve yeniden deneme modeli deneyle kanıtlandı | Geçti |
| A-006 | SSE tabanlı gerçek zaman kanalı ve kopma sonrası kanonik uzlaşma modeli deneyle kanıtlandı | Geçti |
| A-007 | Dosya depolama için koşullu S3 referans sözleşmesi korundu; provisioning A-007R/OPS-005'e ertelendi | Geçti |
| A-008 | Excel üretimi için backend worker, snapshot ve güvenli geçici artefakt modeli var | Geçti |
| A-009 | Monorepo sınırları ve deney/üretim ayrımı tanımlandı | Geçti |
| A-010 | Development/staging/production ortam sözleşmesi ve terfi/rollback sınırları var | Geçti |
| A-011 | Backend ve mobil fiziksel iskeleti, modül sınırları ve mimari testler var | Geçti |
| A-012 | PR/main kalite kapıları, Android/iOS binary build ve SBOM işleri var | Geçti |
| A-013 | Dış yapılandırma, secret referansı ve repo secret taraması iskeleti var | Geçti |
| A-014 | Sağlayıcı bağımsız güvenli loglama, korelasyon ve hata davranışı temeli var | Geçti |

## 4. Dalga 1 çıkış kapısı

| Çıkış ölçütü | Kanıt | Sonuç |
|---|---|---|
| Bütün temel teknoloji kararlarının ADR'si vardır | `ADR/ADR-001-dikey-deneme-protokolu.md`, `ADR/ADR-001-mobil-framework.md`, `ADR/ADR-002_BACKEND_DILI_VE_FRAMEWORK.md`, `ADR/ADR-003-postgresql-ve-hosting.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md`, `ADR/ADR-005-yerel-mobil-veritabani-ve-kuyruk.md`, `ADR/ADR-006-gercek-zamanli-kanal.md`, `ADR/ADR-007-pdf-dosya-depolama.md`, `ADR/ADR-008-excel-uretim-yaklasimi.md`, `ADR/ADR-009-monorepo-ve-repo-yapisi.md`, `ORTAM_SOZLESMESI.md`, `PLAN_005_MALIYET_VE_OPERASYON_REVIZYONU.md` ve `A004R3_COGNITO_MALIYET_OPERASYON_VE_TEARDOWN_KANITI.md` temel mobil, backend, DB, kimlik, yerel kuyruk, gerçek zaman, dosya, Excel, monorepo ve ortam kararlarını kapsar | Geçti |
| Boş mobil ve backend uygulamaları derlenir | Yerel doğrulamada backend `test build` ve Android debug build geçti; yerel iOS denemesi toolchain nedeniyle FAIL kaldı. PR #34 GitHub Actions run `29514907607` içindeki `iOS simulator binary build`, `Mobil statik test ve Android build` ve `Mobil kalite kapısı` işleri PASS olduğu için derleme çıkış kapısı CI macOS kanıtıyla karşılandı | Geçti |
| Test ve lint kontrolleri CI'da çalışır | `.github/workflows/quality-gates.yml` repo, backend, mobil Android, mobil iOS ve dependency review kapılarını içerir; kontrol adları `tooling/README.md` içinde sabittir | Geçti |
| Ortam sırları repodan ayrıdır | A-013 değişken sözleşmesi gerçek secret yerine `*_SECRET_REF`/`*_ROLE_REF` kullanır; `check_no_secrets.sh` yerel secret dosyalarını ve ham bağlantı/token kalıplarını reddeder | Geçti |
| Modül sınırları klasör yapısında görünürdür | `apps/backend` modül paketleri ve `apps/mobile` feature/layer dizinleri README ve mimari testlerle tanımlıdır; üretim uygulamaları `experiments` kaynaklarına bağlanmaz | Geçti |

## 5. Çapraz sözleşme sonuçları

| Konu | Doğrulanan ortak kural | Kaynaklar |
|---|---|---|
| Faz ve dalga ayrımı | Dalga 1'in A-001–A-010 bölümü teknik kapı, A-011–A-015 bölümü platform temelidir; Dalga 2 bu kapıdan sonra başlar | `URUN_VE_UYGULAMA_PLANI.md`, `AGENT_GOREV_PLANI.md`, bu belge |
| Provider bağımsız iskelet | A-011/A-013/A-014 gerçek cloud kaynağı, provider SDK'sı, migration, storage portu veya deployment kaynağı oluşturmaz | `ADR/ADR-009-monorepo-ve-repo-yapisi.md`, `A013_ORTAM_DEGISKENI_VE_SECRET_ISKELETI.md`, `GOZLEMLENEBILIRLIK_VE_HATA_IZLEME_SOZLESMESI.md`, `apps/backend/README.md`, `apps/mobile/README.md` |
| Kimlik sınırı | Cognito kullanıcı doğrulama provider'ıdır; platform `user_id`, üyelik/rol/izin, opaque token ailesi ve `session_generation` platformda kalır | `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md`, `A004R1_COGNITO_PROVISIONING_VE_PKCE_KANITI.md`, `A004R2_COGNITO_IPTAL_VE_UZLASTIRMA_KANITI.md`, `A004R3_COGNITO_MALIYET_OPERASYON_VE_TEARDOWN_KANITI.md` |
| Ortam ve secret ayrımı | Ortam adları açık yazılır; production/staging HTTPS ister; mobil yalnız public yapılandırma taşır; backend secret değerini değil secret referansını okur | `ORTAM_SOZLESMESI.md`, `A013_ORTAM_DEGISKENI_VE_SECRET_ISKELETI.md` |
| Kurum izolasyonu hazırlığı | DB ve API kararları FORCE RLS, bileşik tenant zinciri, provider-rol ayrımı ve sunucu yetki kontrolünü korur; henüz migration yoktur | ADR-003, ADR-004, ORTAM_SOZLESMESI |
| Güvenilir yazma ve sync | Kalıcı mobil kuyruk, idempotency ve kesin sunucu sonucu olmadan kuyruktan silmeme kararı teknoloji iskeletiyle çelişmez | ADR-005, `A013_ORTAM_DEGISKENI_VE_SECRET_ISKELETI.md` secret rotasyon prosedürü |
| Gerçek zaman | SSE taşıması tenant/sınıf scope, yetki iptali ve kopma sonrası kanonik REST uzlaşmasıyla tanımlıdır; websocket veya Supabase Realtime erken seçilmedi | ADR-006 |
| Dosya ve rapor | Dosya deposu gerçek ihtiyaç/A-007R/OPS-005 kapısına bırakıldı; Excel üretimi backend worker ve güvenli geçici artefakt modeliyle ilerleyecek | ADR-007, ADR-008 |
| Gözlemlenebilirlik | Request body, token, parola, telefon, adres, serbest not ve exception message loglanmaz; request id ve güvenli olay zarfı provider bağımsızdır | GOZLEMLENEBILIRLIK_VE_HATA_IZLEME_SOZLESMESI, backend/mobile kodu |

## 6. Açık güvenlik riskleri

| Risk | Kanıt ve sınıflama | Sonraki hareket |
|---|---|---|
| GitHub Dependabot uyarısı #1: `org.apache.commons:commons-lang3` `3.16.0`, CVE-2025-48924 / GHSA-j288-q9x7-2f5v, severity `medium`, fixed version `3.18.0` | `cd apps/backend && ./gradlew buildEnvironment --no-daemon` çıktısı paketin Spring Boot Gradle plugin build classpath'i üzerinden transitif geldiğini gösterdi: `org.springframework.boot:spring-boot-gradle-plugin:4.1.0` → `spring-boot-buildpack-platform:4.1.0` → `commons-compress:1.27.1` → `commons-lang3:3.16.0`. `cd apps/backend && ./gradlew dependencyInsight --dependency org.apache.commons:commons-lang3 --configuration runtimeClasspath --no-daemon` çıktısı backend uygulama `runtimeClasspath` içinde eşleşen bağımlılık bulunmadığını doğruladı. Bu nedenle Dalga 1'i engellemeyen build-time risk olarak sınıflandırıldı. | Risk kapatılmadı. Ayrı dependency bakım çalışmasında Spring Boot Gradle plugin yükseltme veya güvenli override uyumluluğu doğrulanmalı ve fixed version `3.18.0` veya üstüne geçiş kanıtlanmalıdır. |

`Bağımlılık güvenliği` CI sonucunun PASS olması yalnız mevcut workflow politikasındaki high/critical
eşiğinin geçildiğini ifade eder; açık medium uyarının yok olduğu veya kapatıldığı anlamına gelmez.

## 7. Bilinen, bloklamayan sınırlar

- A-015 panoyu `DONE` yapmaz; merge ve kabul sonrasında koordinatör `GOREV_DURUMU.md`
  güncellemesi gerekir.
- IAM-001 hâlâ ayrı sözleşme görevidir. A-004R3 gerekli provider/platform girdilerini üretmiştir;
  giriş/oturum API sözleşmesi bu incelemede yazılmaz.
- Gerçek Cognito, Supabase, Render, registry, secret manager, izleme sağlayıcısı veya nesne
  deposu kaynağı bu dalgada açılmaz. Provider entegrasyonları ilgili IAM/OPS görevlerinin
  kapsamındadır.
- Veritabanı migration, RLS policy, application service, gerçek API endpoint'i ve mobil ürün
  ekranı yoktur. Dalga 1 yalnız kararlar ve iskelet sınırını kapatır.
- iOS binary build yerelde tam Flutter/Xcode ortamı gerektirir. Yerel deneme toolchain nedeniyle
  FAIL kaldı; PR #34 GitHub Actions run `29514907607` içindeki `iOS simulator binary build`
  PASS olduğu için kabul kanıtı CI macOS kapısından alınmıştır.
- A-007R koşullu görevdir; ilk gerçek dosya ihtiyacı doğmadan OPS-005, logo dosyası ve PDF
  yükleme işleri başlatılmaz.

Bu sınırlar Dalga 1 çıkış kapısını engellemez; sonraki görevlerin kapsamını gizlice başlatmaz.

## 8. Dalga 2 öncesi açık kapılar

| Kapı | Sonraki sahip görev | Bloklama durumu |
|---|---|---|
| Giriş/oturum API sözleşmesi | IAM-001 | A-015 merge sonrasında başlayabilir |
| Cihaz ve oturum iptali sözleşmesi | IAM-002 | IAM-001'e bağlı |
| IAM tabloları ve migration | IAM-003 | IAM-001 onaylanmadan başlayamaz |
| Mobil giriş ekranı | IAM-007 + UI-001 | IAM-001 ve UI-001'e bağlı |
| Gerçek provider secret/rol erişimi | IAM/OPS uygulama görevleri | A-013 sözleşmesine uymalı; bu görevde yok |
| Production gerçek veri | OPS-001/OPS-002 ve ilgili pilot kapıları | Restore kanıtı ve ücretli production sınırı olmadan açılamaz |

## 9. Doğrulama

1. `main` dalı `origin/main` ile fast-forward güncellendi ve A-015 branch'i güncel `main`
   üzerinden açıldı.
2. `GOREV_DURUMU.md` A-015'in `READY`, A-001–A-014/PLAN-005/A-004R3 bağımlılıklarının `DONE`
   olduğunu gösterdi.
3. `ADR/ADR-001-dikey-deneme-protokolu.md`, `ADR/ADR-001-mobil-framework.md`,
   `ADR/ADR-002_BACKEND_DILI_VE_FRAMEWORK.md`, `ADR/ADR-003-postgresql-ve-hosting.md`,
   `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md`,
   `ADR/ADR-005-yerel-mobil-veritabani-ve-kuyruk.md`,
   `ADR/ADR-006-gercek-zamanli-kanal.md`, `ADR/ADR-007-pdf-dosya-depolama.md`,
   `ADR/ADR-008-excel-uretim-yaklasimi.md`, `ADR/ADR-009-monorepo-ve-repo-yapisi.md`,
   `PLAN_005_MALIYET_VE_OPERASYON_REVIZYONU.md`, `ORTAM_SOZLESMESI.md`,
   `A004R1_COGNITO_PROVISIONING_VE_PKCE_KANITI.md`,
   `A004R2_COGNITO_IPTAL_VE_UZLASTIRMA_KANITI.md`,
   `A004R3_COGNITO_MALIYET_OPERASYON_VE_TEARDOWN_KANITI.md`,
   `A013_ORTAM_DEGISKENI_VE_SECRET_ISKELETI.md`,
   `GOZLEMLENEBILIRLIK_VE_HATA_IZLEME_SOZLESMESI.md`, backend/mobile README ve A-012
   workflow dosyaları tarandı.
4. Dalga 1 çıkış kapısının beş ölçütü yukarıdaki izlenebilirlik tablosunda kaynaklarıyla
   değerlendirildi.
5. Çalıştırılan kontroller ve sonuçları:
   - `./tooling/check_repo_boundaries.sh` — PASS
   - `./tooling/check_no_secrets.sh` — PASS
   - `./tooling/test/check_repo_boundaries_test.sh` — PASS
   - `./tooling/test/check_no_secrets_test.sh` — PASS
   - `./tooling/test/detect_changed_areas_test.sh` — PASS
   - `./tooling/test/quality_workflow_test.sh` — PASS
   - `cd apps/backend && ./gradlew test build --no-daemon` — PASS
   - `cd apps/mobile && flutter pub get` — PASS
   - `cd apps/mobile && dart format --output=none --set-exit-if-changed lib test` — PASS
   - `cd apps/mobile && flutter analyze` — PASS
   - `cd apps/mobile && flutter test` — PASS
   - `cd apps/mobile && flutter build apk --debug ...` — PASS
   - `cd apps/mobile && flutter build ios --debug --simulator --no-codesign ...` — yerelde FAIL;
     Flutter çıktısı `Application not configured for iOS`, `flutter doctor -v` çıktısı eksik
     Xcode kurulumu ve eksik CocoaPods bildirdi. Yerel PASS sayılmadı.
   - PR #34 GitHub Actions run `29514907607` / `iOS simulator binary build` — PASS
   - PR #34 GitHub Actions run `29514907607` / `Mobil statik test ve Android build` — PASS
   - PR #34 GitHub Actions run `29514907607` / `Mobil kalite kapısı` — PASS
   - `cd apps/backend && ./gradlew dependencyInsight --dependency org.apache.commons:commons-lang3 --configuration runtimeClasspath --no-daemon` — PASS; runtime eşleşmesi yok
   - `cd apps/backend && ./gradlew buildEnvironment --no-daemon` — PASS; `commons-lang3:3.16.0` build classpath üzerinde transitif görünüyor
   - `git diff --check` — PASS

## 10. Sonraki güvenli hareket

A-015 PR'ı merge edildikten ve koordinatör görev panosunu güncelledikten sonra IAM-001
`READY` yapılabilir. IAM-001, ADR-004/A004R3 provider-platform sınırını API sözleşmesine
dönüştürmeli; gerçek kullanıcı, migration, provider SDK'sı veya production secret erişimi
başlatmamalıdır.
