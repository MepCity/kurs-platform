# Güncel Görev Panosu

| Alan | Değer |
|---|---|
| Son güncelleme | 15 Temmuz 2026 |
| Aktif dalga | Dalga 1 — Teknoloji kararları ve iskelet |
| Aktif görev | Yok |
| Sıradaki görev | A-007–A-010 — Paralel teknoloji/ortam kararları |

Bu dosya projenin kaldığı yeri gösteren kısa operasyon panosudur. Her çalışma oturumunun
başında okunur, görev kabul edildiğinde güncellenir. Ayrıntılı görev tanımları
`AGENT_GOREV_PLANI.md` içindedir.

## READY

| Kimlik | Görev | Boyut | Not |
|---|---|---:|---|
| A-007 | PDF/dosya depolama ADR'si | S | P-004 ve P-012 tamamlandı; paralel başlayabilir |
| A-008 | Excel üretme yaklaşımı ADR'si | S | P-012 tamamlandı; paralel başlayabilir |
| A-009 | Monorepo/repo yapısı ADR'si | S | A-001 ve A-002 tamamlandı; paralel grup B başlayabilir |
| A-010 | Geliştirme, staging ve üretim ortam sözleşmesi | S | A-002 ve A-003 tamamlandı; paralel başlayabilir |

## IN_PROGRESS

Aktif görev yok.

## REVIEW

İncelemede görev yok.

## BLOCKED

Engellenmiş görev yok.

## DONE

| Kimlik | Görev | Tamamlanma tarihi | Teslim |
|---|---|---|---|
| PLAN-001 | Ana ürün ve mimari planını oluştur | 13 Temmuz 2026 | `URUN_VE_UYGULAMA_PLANI.md` |
| PLAN-002 | Agent odaklı görev planını oluştur | 13 Temmuz 2026 | `AGENT_GOREV_PLANI.md` |
| PLAN-003 | Repo agent kurallarını oluştur | 13 Temmuz 2026 | `AGENTS.md` |
| PLAN-004 | Faz/Dalga ve çapraz sözleşme tutarlılık düzeltmelerini uygula | 14 Temmuz 2026 | 17 belge — PR #17 |
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
| A-005 | Yerel mobil veritabanı/kuyruk ADR'si ve kalıcı yeniden deneme deneyi | 14 Temmuz 2026 | `ADR/ADR-005-yerel-mobil-veritabani-ve-kuyruk.md`, `experiments/a005_local_queue` — 17 test — PR #20 |
| A-006 | Gerçek zamanlı kanal ADR'si ve iki istemcili yoklama olayı deneyi | 14 Temmuz 2026 | `ADR/ADR-006-gercek-zamanli-kanal.md`, `experiments/a006_realtime_sse` — 6 test — PR #21 |

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
