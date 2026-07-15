# Kurs Platform Backend

Java 21 ve Spring Boot 4.1 üzerinde çalışan tek deploy birimli modüler monolit iskeletidir.
İş alanları `org.mepcity.kursplatform.<module>` altında, teknik katmana göre kökten ayrılmadan
paketlenir.

## Paket sınırları

- `configuration`: yalnız composition root
- `core`: dar ve kararlı ortak sözleşmeler
- `iam`, `org`, `term`, `cls`, `people`, `att`, `content`, `program`, `progress`, `audit`,
  `export`, `sync`, `realtime`: ilk sürüm iş alanları
- `notify`: yalnız sonraki faz paket sınırı; ilk sürüm davranışı içermez

Her iş alanındaki kaynaklar `api`, `application`, `domain` ve `infrastructure` alt paketlerine
yerleşir. Bağımlılık yönü `api → application → domain` şeklindedir. Infrastructure,
application/domain portlarını uygular; başka modülün infrastructure yüzeyi import edilmez.

Modüller arası yayımlanmış tek Java yüzeyi
`org.mepcity.kursplatform.<module>.application.contract[.<alt-paket>]` paketidir. Bir tipin
yalnız `.application` altında bulunması onu yayımlanmış yapmaz. Başka modüller `.api`, `.domain`,
`.infrastructure`, `.application` iç paketleri veya persistence yüzeylerine doğrudan erişemez.
Mimari test; normal/static importları ve kaynak içindeki tam nitelikli sınıf referanslarını aynı
kuralla denetler.

Üretim Java kaynaklarında yalnız kök `KursPlatformBackendApplication.java`, `configuration/**`,
`core/**` ve tanımlı iş modüllerinin `api/application/domain/infrastructure` katmanları kabul
edilir. `service`, `repository`, `model`, `shared` gibi tanımsız üst veya modül-alt paketler
fail-closed reddedilir. `core` hiçbir iş modülüne, iş modülleri de composition root olan
`configuration` paketine bağımlı olamaz.

A-011 endpoint, migration, persistence, auth/storage portu, sağlayıcı SDK'sı, secret, ortam
değişkeni veya deployment kaynağı içermez.

## Gözlemlenebilirlik sınırı

`core.observability` yalnız tipli factory'lerle kurulabilen sağlayıcı bağımsız olay sözleşmesini,
`configuration.observability` ise HTTP korelasyonu ve SLF4J adaptörünü taşır. İstek gövdesi,
sorgu parametreleri, parola, token, telefon, adres, serbest not ve istisna mesajı loglanmaz.
Geçerli `X-Request-Id` yanıta taşınır; eksikse sunucu üretir, geçersizse güvenli `400` zarfı
döner. Logger `RuntimeException` hataları HTTP/asıl exception davranışını değiştirmeyen
best-effort sınırında kalır; JVM `Error` türleri genel olarak yutulmaz. `FATAL`, SLF4J
`atError()` üzerinden özgün severity alanıyla taşınır. Gerçek izleme sağlayıcısı, secret ve
production alarm kurulumu bu iskeletin parçası değildir.

## Yerel doğrulama

```bash
./gradlew test
./gradlew build
```

Gradle wrapper bu uygulamanın içindedir; kök repo veya mobil SDK backend testleri için gerekli
değildir.

CI aynı komutları Java 21 ile çalıştırır, Gradle wrapper doğrulaması ve açık kaynak Gradle
cache'i kullanır. Üretilen çalıştırılabilir JAR için CycloneDX SBOM saklanır; `main` dalında
çözümlenen bağımlılıklar GitHub bağımlılık grafiğine gönderilir.

## Başlangıç bağımlılıkları

- `spring-boot-starter-webmvc`: ADR-002'de seçilen Spring MVC çalışma zamanını derlenebilir
  kılar; A-011 endpoint tanımlamaz.
- `spring-boot-starter-webmvc-test`: JUnit 5, Spring context ve AssertJ test yüzeyini sağlar.

Ayrı bir mimari test kütüphanesi eklenmemiştir; mevcut JUnit/AssertJ yüzeyi bu iskeletin paket
ve import sınırlarını doğrulamak için yeterlidir. Persistence, migration, security/provider,
storage ve gözlemlenebilirlik bağımlılıkları kendi görevlerine bırakılmıştır.
