# Güncel Görev Panosu

| Alan | Değer |
|---|---|
| Son güncelleme | 18 Temmuz 2026 |
| Aktif dalga | Dalga 2 — Kimlik, kurum ve mobil kabuk |
| Aktif görev | Yok |
| Sıradaki görev | IAM-008 — Mobil güvenli oturum saklamayı uygula |

Bu dosya projenin kaldığı yeri gösteren kısa operasyon panosudur. Her çalışma oturumunun
başında okunur, görev kabul edildiğinde güncellenir. Ayrıntılı görev tanımları
`AGENT_GOREV_PLANI.md` içindedir.

## READY

| Kimlik | Görev | Boyut | Not |
|---|---|---:|---|
| IAM-008 | Mobil güvenli oturum saklamayı uygula | M | IAM-002 ve A-005 tamamlandı; mobil alanda IAM-003 backend migration çalışmasıyla paralel ilerleyebilir |
| IAM-007 | Mobil giriş ekranını uygula | M | IAM-001 ve UI-001 tamamlandı; IAM-008 ile aynı mobil kimlik alanına dokunduğundan paralel başlatılmaz |
| UI-003 | Ortak düğme, alan, liste ve durum bileşenleri | M | UI-001 tamamlandı; backend sözleşme ve migration işleriyle paralel ilerleyebilir |
| ORG-002 | Marka ayarları sözleşmesini yaz | S | ORG-001 ve A-007 tamamlandı; IAM-003 migration çalışmasıyla dosya sahipliği çakışmıyor |
| ORG-006 | Platform yöneticisi kurum listeleme ekranı | M | ORG-001 ve UI-001 tamamlandı; onaylı sözleşmeye bağlı mock API ile geliştirilebilir |
| ORG-007 | Mobil kurum oluşturma akışı | M | ORG-001 ve UI-001 tamamlandı; ORG-006 ile ortak mobil dosya sahipliği koordine edilmelidir |

## IN_PROGRESS

Aktif görev yok.

## REVIEW

| Kimlik | Görev | PR | Not |
|---|---|---|---|
| IAM-003 | IAM tabloları, roller ve migration'ı uygula | #37 | Agent teslimi incelemede; merge edilmedi |

## BLOCKED

| Kimlik | Görev | Engel | Açılma koşulu |
|---|---|---|---|
| ORG-003 | Kurum migration ve repository'sini oluştur | PR #37 ile `platform_administrators`, runtime rolü ve RLS/migration yüzeyi çakışıyor | IAM-003 merge edilip main güncellendikten sonra rebase edilerek READY yapılır |

## DONE

| Kimlik | Görev | Tamamlanma tarihi | Teslim |
|---|---|---|---|
| PLAN-001 | Ana ürün ve mimari planını oluştur | 13 Temmuz 2026 | `URUN_VE_UYGULAMA_PLANI.md` |
| PLAN-002 | Agent odaklı görev planını oluştur | 13 Temmuz 2026 | `AGENT_GOREV_PLANI.md` |
| PLAN-003 | Repo agent kurallarını oluştur | 13 Temmuz 2026 | `AGENTS.md` |
| PLAN-004 | Faz/Dalga ve çapraz sözleşme tutarlılık düzeltmelerini uygula | 14 Temmuz 2026 | 17 belge — PR #17 |
| PLAN-005 | Başlangıç maliyeti, sağlayıcı kapıları ve kademeli ortam sözleşmesini uygula | 15 Temmuz 2026 | `PLAN_005_MALIYET_VE_OPERASYON_REVIZYONU.md` ve ilgili sözleşmeler — PR #26 |
| P-001 | Terimler sözlüğünü oluştur | 13 Temmuz 2026 | `TERIMLER_SOZLUGU.md` — PR #1 |
| P-002 | Aktörleri ve ana kullanım senaryolarını listele | 13 Temmuz 2026 | `AKTORLER_VE_KULLANIM_SENARYOLARI.md` — PR #2 |
| P-003 | Ayrıntılı yetki matrisini oluştur | 13 Temmuz 2026 | `YETKI_MATRISI.md` — PR #4 |
| P-004 | Kişisel ve hassas veri envanterini çıkar | 13 Temmuz 2026 | `KISISEL_VERI_ENVANTERI.md` — PR #3 |
| P-005 | Yönetici mobil bilgi mimarisini çiz | 14 Temmuz 2026 | `YONETICI_BILGI_MIMARISI.md` — PR #6 |
| P-006 | Hoca mobil bilgi mimarisini çiz | 13 Temmuz 2026 | `HOCA_MOBIL_BILGI_MIMARISI.md` — PR #5 |
| P-007 | İlk sürüm ekran envanterini çıkar | 14 Temmuz 2026 | `EKRAN_ENVANTERI.md` — PR #9 |
| P-008 | Çekirdek veri modeli taslağını yaz | 14 Temmuz 2026 | `VERI_MODELI.md` — PR #7 |
| P-009 | API genel kurallarını yaz | 14 Temmuz 2026 | `API_GENEL_KURALLARI.md` — PR #8 |
| P-010 | Senkronizasyon ve çakışma sözleşmesini yaz | 14 Temmuz 2026 | `SENKRONIZASYON_VE_CAKISMA.md` — PR #11 |
| P-011 | Denetim ve geri alma ilkelerini detaylandır | 14 Temmuz 2026 | `DENETIM_VE_GERI_ALMA_ILKELERI.md` — PR #10 |
| P-012 | Excel rapor veri sözleşmesini tanımla | 14 Temmuz 2026 | `EXCEL_RAPOR_VERI_SOZLESMESI.md` — PR #12 |
| P-013 | Kritik test ve kabul planını yaz | 14 Temmuz 2026 | `KRITIK_TEST_VE_KABUL_PLANI.md` — PR #13 |
| P-014 | Faz 0 bütünlük incelemesi yap | 14 Temmuz 2026 | `FAZ_0_BUTUNLUK_INCELEMESI.md` — PR #14 |
| A-001 | Flutter ve alternatif mobil framework karşılaştırması/dikey deneme | 15 Temmuz 2026 | `ADR/ADR-001-mobil-framework.md`, `experiments/a001_flutter_spike` — Android ve iOS VT-01–VT-03 PASS — PR #15 |
| A-002 | Backend dili ve framework ADR'si | 14 Temmuz 2026 | `ADR/ADR-002_BACKEND_DILI_VE_FRAMEWORK.md` — PR #16 |
| A-003 | PostgreSQL/hosting sağlayıcısı ADR'si | 14 Temmuz 2026 | `ADR/ADR-003-postgresql-ve-hosting.md` — PR #18 |
| A-004 | Kimlik doğrulama sağlayıcısı ADR'si | 14 Temmuz 2026 | `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` — PR #19 |
| A-004R1 | Cognito provisioning, ilk parola ve gerçek mobil PKCE deneyini yap | 15 Temmuz 2026 | `A004R1_COGNITO_PROVISIONING_VE_PKCE_KANITI.md`, `experiments/a004r1_cognito_pkce` — Android PKCE ve provisioning kanıtı — PR #28 |
| A-004R2 | Cognito iptal, platform oturumu ve olay kaybı uzlaştırma deneyini yap | 15 Temmuz 2026 | `A004R2_COGNITO_IPTAL_VE_UZLASTIRMA_KANITI.md`, `experiments/a004r2_cognito_revocation` — gerçek Cognito iptal/rotation kanıtı ve 14 test — PR #31 |
| A-004R3 | Cognito maliyet/operasyon kararını kapat ve geçici kaynakları kaldır | 16 Temmuz 2026 | `A004R3_COGNITO_MALIYET_OPERASYON_VE_TEARDOWN_KANITI.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` — Cognito Essentials kararı, maliyet modeli ve eksiksiz AWS teardown kanıtı — PR #32 |
| A-005 | Yerel mobil veritabanı/kuyruk ADR'si ve kalıcı yeniden deneme deneyi | 14 Temmuz 2026 | `ADR/ADR-005-yerel-mobil-veritabani-ve-kuyruk.md`, `experiments/a005_local_queue` — 17 test — PR #20 |
| A-006 | Gerçek zamanlı kanal ADR'si ve iki istemcili yoklama olayı deneyi | 14 Temmuz 2026 | `ADR/ADR-006-gercek-zamanli-kanal.md`, `experiments/a006_realtime_sse` — 6 test — PR #21 |
| A-007 | PDF/dosya depolama ADR'si | 15 Temmuz 2026 | `ADR/ADR-007-pdf-dosya-depolama.md` — PR #24 |
| A-008 | Excel üretme yaklaşımı ADR'si | 15 Temmuz 2026 | `ADR/ADR-008-excel-uretim-yaklasimi.md` — PR #23 |
| A-009 | Monorepo/repo yapısı ADR'si | 15 Temmuz 2026 | `ADR/ADR-009-monorepo-ve-repo-yapisi.md` — PR #22 |
| A-010 | Geliştirme, staging ve üretim ortam sözleşmesi | 15 Temmuz 2026 | `ORTAM_SOZLESMESI.md` — PR #25 |
| A-011 | Repo ve modül klasör iskeletini oluştur | 15 Temmuz 2026 | `apps/mobile`, `apps/backend`, `tooling` — backend build, 29 mimari + 1 context testi; Flutter analiz, 19 test ve Android APK PASS; iOS binary kanıtı A-012'ye devredildi — PR #27 |
| A-012 | CI kalite kapılarını oluştur | 15 Temmuz 2026 | `.github/workflows/quality-gates.yml`, path/rename regresyonları, dört zorunlu gate; backend, Android, iOS simulator ve SBOM PASS — PR #29 |
| A-013 | Ortam değişkeni ve secret yönetimi iskeleti | 16 Temmuz 2026 | `A013_ORTAM_DEGISKENI_VE_SECRET_ISKELETI.md`, backend/mobil fail-fast yapılandırma ve repo secret tarama kapısı — PR #33 |
| A-014 | Loglama ve hata izleme temelini kur | 15 Temmuz 2026 | `GOZLEMLENEBILIRLIK_VE_HATA_IZLEME_SOZLESMESI.md`, backend ve mobil güvenli gözlemlenebilirlik temeli; backend 49 test, mobil 34 test, Android ve iOS binary kalite kapıları PASS — PR #30 |
| A-015 | Dalga 1 iskelet bütünlük incelemesi | 16 Temmuz 2026 | `A015_DALGA_1_ISKELET_BUTUNLUK_INCELEMESI.md` — Dalga 1 çıkış kapısı, Android/iOS CI ve açık build-time bağımlılık riski sınıflaması — PR #34 |
| IAM-001 | Giriş/oturum API sözleşmesini kesinleştir | 17 Temmuz 2026 | `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`, `API_GENEL_KURALLARI.md`, `VERI_MODELI.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` — giriş, aktivasyon, refresh, logout, IAM_AUTH RLS ve idempotent replay sözleşmesi — PR #35 |
| IAM-002 | Cihaz ve oturum iptali sözleşmesini yaz | 17 Temmuz 2026 | `IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md`, `API_GENEL_KURALLARI.md`, `IAM_GIRIS_OTURUM_API_SOZLESMESI.md`, `VERI_MODELI.md` — cihaz/kurum/platform iptali, dar FORCE RLS, idempotency ve eşzamanlılık sözleşmesi — PR #36 |
| UI-001 | Mobil tasarım tokenlarını tanımla | 17 Temmuz 2026 | `MOBIL_TASARIM_TOKENLARI.md` — deterministik tema üretimi, WCAG kontrast kapıları, erişilebilir metin ölçekleme ve etkileşim alanı kuralları — PR #39 |
| ORG-001 | Kurum yaşam döngüsü API sözleşmesini yaz | 17 Temmuz 2026 | `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md`, `ADR/ADR-004_KIMLIK_DOGRULAMA_SAGLAYICISI.md` — kurum yaşam döngüsü, GLOBAL/ORGANIZATION yetki bağlamı, audit fail-closed, idempotency ve eşzamanlılık sözleşmesi — PR #38 |
| UI-002 | Navigasyon ve rol bazlı menü sözleşmesini yaz | 18 Temmuz 2026 | `UI_002_NAVIGASYON_VE_ROL_BAZLI_MENU_SOZLESMESI.md` — rol bazlı NavigationBar, güvenli kurum/rol bağlamı, bağımsız izin görünürlüğü ve sınıf seçimi sözleşmesi — PR #40 |

## Sonraki görev nasıl READY yapılır?

1. Aktif görev `DONE` olur.
2. Teslim ve test sonucu bu dosyaya yazılır.
3. `AGENT_GOREV_PLANI.md` içinde bağımlılığı tamamlanan en erken görev bulunur.
4. Yalnızca gerçekten bütün bağımlılıkları tamamlanan görev `READY` bölümüne alınır.
5. Paralel görev açılacaksa dosya ve modül sahipliğinin çakışmadığı kontrol edilir.

## Oturum kapanış kontrolü

- Görev durumu güncellendi mi?
- Teslim dosyası veya kod bağlantısı yazıldı mı?
- Test sonucu kaydedildi mi?
- Yeni karar varsa ana planın karar günlüğüne eklendi mi?
- Bağımlılığı çözülen sıradaki görev READY yapıldı mı?
- Yarım veya sahipsiz değişiklik kaldı mı?
