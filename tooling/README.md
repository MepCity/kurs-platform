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
./tooling/test/check_repo_boundaries_test.sh
```

A-011 kök paket yöneticisi veya birleşik lockfile eklemez. Kesin lint/test/build orkestrasyonu
ve CI kalite kapıları A-012 kapsamında oluşturulacaktır.
