# Güncel Görev Panosu

| Alan | Değer |
|---|---|
| Son güncelleme | 14 Temmuz 2026 |
| Aktif dalga | Dalga 0 — Ürün ve sözleşmeler |
| Aktif görev | Yok |
| Sıradaki görev | P-010, P-011 ve P-012 paralel başlatılabilir |

Bu dosya projenin kaldığı yeri gösteren kısa operasyon panosudur. Her çalışma oturumunun
başında okunur, görev kabul edildiğinde güncellenir. Ayrıntılı görev tanımları
`AGENT_GOREV_PLANI.md` içindedir.

## READY

| Kimlik | Görev | Boyut | Not |
|---|---|---:|---|
| P-010 | Senkronizasyon ve çakışma sözleşmesini yaz | M | P-008 ve P-009 tamamlandı; başlamaya hazır |
| P-011 | Denetim ve geri alma ilkelerini detaylandır | S | P-003 ve P-008 tamamlandı; başlamaya hazır |
| P-012 | Excel rapor veri sözleşmesini tanımla | S | P-008 tamamlandı; başlamaya hazır |

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
| P-001 | Terimler sözlüğünü oluştur | 13 Temmuz 2026 | `TERIMLER_SOZLUGU.md` — PR #1 |
| P-002 | Aktörleri ve ana kullanım senaryolarını listele | 13 Temmuz 2026 | `AKTORLER_VE_KULLANIM_SENARYOLARI.md` — PR #2 |
| P-003 | Ayrıntılı yetki matrisini oluştur | 13 Temmuz 2026 | `YETKI_MATRISI.md` — PR #4 |
| P-004 | Kişisel ve hassas veri envanterini çıkar | 13 Temmuz 2026 | `KISISEL_VERI_ENVANTERI.md` — PR #3 |
| P-005 | Yönetici mobil bilgi mimarisini çiz | 14 Temmuz 2026 | `YONETICI_BILGI_MIMARISI.md` — PR #6 |
| P-006 | Hoca mobil bilgi mimarisini çiz | 13 Temmuz 2026 | `HOCA_MOBIL_BILGI_MIMARISI.md` — PR #5 |
| P-007 | İlk sürüm ekran envanterini çıkar | 14 Temmuz 2026 | `EKRAN_ENVANTERI.md` — PR #9 |
| P-008 | Çekirdek veri modeli taslağını yaz | 14 Temmuz 2026 | `VERI_MODELI.md` — PR #7 |
| P-009 | API genel kurallarını yaz | 14 Temmuz 2026 | `API_GENEL_KURALLARI.md` — PR #8 |

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
