# Kurs Platform

Çok kurumlu, modüler ve kişiselleştirilebilir Kur'an kursu eğitim, yoklama ve öğrenci takip
platformu.

Bu repo şu anda planlama ve mimari hazırlık aşamasındadır. Eski HTML/Google Sheets uygulaması
yeni ürünün kod tabanı değildir; yalnızca ürün davranışları için referans olarak kullanılabilir.

## Başlangıç sırası

1. [`URUN_VE_UYGULAMA_PLANI.md`](URUN_VE_UYGULAMA_PLANI.md) — Bağlayıcı ürün ve mimari sözleşmesi
2. [`AGENT_GOREV_PLANI.md`](AGENT_GOREV_PLANI.md) — Atomik görevler, bağımlılıklar ve çalışma dalgaları
3. [`GOREV_DURUMU.md`](GOREV_DURUMU.md) — Projenin güncel durumu ve sıradaki görev
4. [`AGENTS.md`](AGENTS.md) — Repoda çalışan agentların uyması gereken kurallar
5. [`AGENT_KOMUT_REHBERI.md`](AGENT_KOMUT_REHBERI.md) — Branch, görev verme, PR ve merge akışı
6. [`ADMIN_AGENT.md`](ADMIN_AGENT.md) — Merkez koordinatör agentın kalıcı rolü ve başlangıç promptu

## Güncel durum

Aktif dalga ve sıradaki görev bu dosyada sabitlenmez; tek operasyonel kaynak
[`GOREV_DURUMU.md`](GOREV_DURUMU.md) dosyasıdır.

Güncel görev durumu ve sıradaki iş için [`GOREV_DURUMU.md`](GOREV_DURUMU.md) esas alınır.

## Repo yerleşimi

- `apps/mobile` — bağımsız Flutter iOS/Android uygulaması
- `apps/backend` — bağımsız Java 21/Spring Boot modüler monoliti
- `experiments` — üretim bağımlılık grafiği dışında kalan teknik karar kanıtları
- `tooling` — repo içi otomasyon; ürün çalışma zamanı kodu değildir
- `ADR` — mimari karar kayıtları

Mobil ve backend kendi manifest, kilit/wrapper ve test komutlarına sahiptir. Bir uygulamanın
kontrolü diğer uygulamanın SDK'sını gerektirmez; üretim uygulamaları `experiments` kaynaklarını
import etmez. Ayrıntılı sınırlar için uygulama dizinlerindeki README dosyalarına ve
[`ADR-009`](ADR/ADR-009-monorepo-ve-repo-yapisi.md) belgesine bakın.

Repo düzeyi fiziksel sınır kontrolü `./tooling/check_repo_boundaries.sh` ile çalıştırılır.
Secret sızıntısı başlangıç kontrolü `./tooling/check_no_secrets.sh` ile çalıştırılır. Pull
request kalite kapıları, cache/SBOM yaklaşımı ve zorunlu kontrol adları
[`tooling/README.md`](tooling/README.md) belgesinde açıklanır.

Sağlayıcı bağımsız loglama, güvenli hata yakalama, hassas veri minimizasyonu ve başlangıç alarm
eşikleri [`GOZLEMLENEBILIRLIK_VE_HATA_IZLEME_SOZLESMESI.md`](GOZLEMLENEBILIRLIK_VE_HATA_IZLEME_SOZLESMESI.md)
belgesinde tanımlıdır.

Dalga 0 belge kapısı tamamlanmıştır. Faz 0'ın sınırlı teknik doğrulama/ADR işleri
(`A-001`–`A-010`) üretim geliştirmesi değildir ve bu kapıyı kanıtla kapatmak için yürütülür;
uygulama iskeleti `A-011` ile, Faz 0 teknik kabul kapısı tamamlandıktan sonra başlar.
