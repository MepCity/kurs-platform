# Tooling

Bu dizin repo içi, ürün çalışma zamanı dışında kalan ince otomasyon girişlerine ayrılmıştır.
`check_repo_boundaries.sh`; uygulama manifest/wrapper sınırlarını, kökte yasak ortak kaynak
dizinlerini ve üretim uygulamalarının deney koduna bağlanmamasını doğrular. Kontrol; Flutter
`pubspec`, bütün `*.gradle`/`*.gradle.kts` build ve settings varyantları, version catalog/lock
dosyaları ile yaygın dependency manifestlerini tarar. `apps` altındaki bir manifestten
`experiments` path dependency verilmesini ve bu dizine doğrudan/dolaylı symlink oluşturulmasını
reddeder. Xcode `pbxproj`, `xcconfig`, workspace bağlantısı, settings ve scheme metin dosyaları
da aynı kurala tabidir; build/cache dizinleri ve binary dosyalar taranmaz.

```bash
./tooling/check_repo_boundaries.sh
./tooling/check_no_secrets.sh
./tooling/test/check_repo_boundaries_test.sh
./tooling/test/check_no_secrets_test.sh
./tooling/test/detect_changed_areas_test.sh
./tooling/test/quality_workflow_test.sh
```

`check_no_secrets.sh`, A-013 kapsamında yerel `.env`, private key dosyası, AWS access key,
JWT benzeri bearer değeri ve yaygın ham `password`/`token`/`secret` atamalarını reddeder.
Ham `DATABASE_URL`, `JDBC_URL`, `SPRING_DATASOURCE_URL` ve kullanıcı bilgisi taşıyan PostgreSQL/
JDBC bağlantı atamaları da reddedilir.
Secret referansları yalnız `KURS_PLATFORM_*_REF=<environment>/<path>` biçiminde kabul edilir;
test dosyaları taramadan dışlanmaz. Dosya listesi NUL-delimited işlendiği için boşluk, tab ve
shell özel karakterleri içeren dosya adları güvenli taranır. Gerçek secret manager entegrasyonu
sonraki operasyon görevlerinin konusudur.

`detect_changed_areas.sh`, rename tespitini kapatarak eski ve yeni yolu ayrı ayrı değerlendirir.
Yalnız backend değişikliğinde backend; yalnız mobil değişikliğinde mobil kapısını seçer.
`experiments` içindeki değişiklikler üretim uygulamalarını tetiklemez. Bir uygulamadan deneylere
taşıma ve uygulama dosyası silme kaynak uygulamanın kapısını; uygulamalar arası taşıma iki kapıyı;
ortak sözleşme, tooling veya workflow değişikliği ise iki uygulama kapısını da çalıştırır.

`.github/workflows/quality-gates.yml`; repo sınırlarını, backend test/build'i, mobil
format/analyze/test'i, Android ve tam Xcode'lu macOS üzerinde iOS simulator binary build'ini
doğrular. Gradle, Flutter SDK ve pub cache'leri sürüm/kilit girdileriyle ayrılır; PR işleri
varsayılan branch cache'ini yalnız okur. Backend ve mobil CycloneDX SBOM'ları 14 günlük CI
artefaktı olarak saklanır. Yeni bağımlılıklar yüksek/kritik güvenlik açığı ve AGPL lisansları
için dependency review kapısından geçer; backend bağımlılık grafiği `main` push'unda GitHub'a
gönderilir.

Branch korumasında zorunlu tutulacak kararlı kontrol adları şunlardır:

- `Repo kalite kapısı`
- `Backend kalite kapısı`
- `Mobil kalite kapısı`
- `Bağımlılık güvenliği`

## Merkez merge politikası

- Projede yalnız `MepCity` GitHub hesabı kullanıldığı sürece zorunlu GitHub approval sayısı
  `0`dır; ikinci gerçek insan reviewer hesabı veya ekibi eklendiğinde yeniden `1` yapılır.
- Merge için kullanıcının merkez agent konuşmasında açık `merge et` talimatı zorunludur.
- Dört zorunlu kalite kapısı başarılı olmadan admin bypass kullanılamaz.
- Açık review tartışmaları çözülmeden ve PR dalı güncel `main` üzerine rebase edilmeden merge
  yapılamaz.
- Doğrusal geçmiş korunur: görev PR'ları rebase merge ile birleştirilir, merge commit yöntemi
  kullanılmaz.
- Admin doğrudan `main` push istisnası yalnız `GOREV_DURUMU.md` koordinatör güncellemesi gibi
  açıkça tanımlanmış idari commitlerle sınırlıdır.
