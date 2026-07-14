# A-001 Flutter dikey deneme spike'ı

Bu dizin yalnız A-001 karar kanıtı için üretilmiş, üretim dışı ve izole bir Flutter
uygulamasıdır. Nihai mobil modül, gerçek API, kalıcı kuyruk, gerçek zamanlı kanal, kimlik
bilgisi veya kullanıcı verisi içermez. Sadece sentetik giriş, sınıf/öğrenci listesi ve tek
yoklama etkileşimini doğrular.

## Yeniden üretim

Kullanılan SDK: Flutter `3.44.6 stable` (framework `ee80f08bbf`), Dart `3.12.2`.
Platform sarmalayıcıları kasıtlı olarak commit edilmemiştir; spike kaynağını değiştirmeden,
Flutter'ın aynı sürümüyle bu dizinde aşağıdaki komut üretilir:

```sh
flutter create --platforms=android,ios --org org.mepcity.kursplatform --project-name a001_flutter_spike .
flutter pub get
flutter analyze
flutter test
```

Android API 36 AVD örneği için kesin entegrasyon komutu:

```sh
flutter test integration_test/spike_flow_test.dart -d emulator-5554 -r expanded
```

Sıçrama ekranı veya süreç takılırsa, aynı test komutu için çıkış kodu ile birlikte bu iki günlük
zaman aşımı penceresinde saklanır; nihai `All tests passed` satırı olmadan sonuç PASS değildir:

```sh
flutter logs -d emulator-5554
adb logcat -v threadtime
```

Tam Xcode ve Simulator içeren macOS ortamında iOS için (önce `xcrun simctl list devices available`
ile cihaz kimliğini seçerek):

```sh
flutter test integration_test/spike_flow_test.dart -d <ios-simulator-id> -r expanded
```

Her hedef için cihaz/simülatör adı, işletim sistemi sürümü, Flutter sürümü, tam komut, çıkış
kodu, süre ve nihai test satırı ADR eki deney raporuna kaydedilir. Android ve iOS'ta VT-01,
VT-02 ve VT-03'ün tamamı `All tests passed` olmadan ADR kabul edilmez.

### iOS CI log okuyucu düzeltmesi

Flutter `3.44.6`, iOS Simulator entegrasyon testinde log okuyucuyu uygulama başlatıldıktan
sonra dinlemeye başlayabildiği için Dart VM service satırını aralıklı olarak kaçırabilir. Bu
durum Flutter'ın açık [#181771](https://github.com/flutter/flutter/issues/181771) kaydında
izlenmektedir. A-001 CI akışı, yalnız sabitlenen `3.44.6` SDK kopyasına
`ci/flutter-3.44.6-ios-simulator-log-reader.patch` dosyasını uygular; uygulama kaynağına veya
üretim bağımlılıklarına müdahale etmez. Patch log sürecini Simulator uygulamasından önce
başlatır ve SDK sürümü değiştiğinde `git apply` uyumsuzluğuyla fail-closed davranır. Resmî
Flutter sürümü düzeltmeyi içerdiğinde patch kaldırılmalı ve platform koşusu yeniden
kanıtlanmalıdır.

## Akış

- VT-01: Sentetik hoca girişi ile Gül Sınıfı ve iki öğrenciyi gösterir.
- VT-02: Yoklama ekranını açar.
- VT-03: Ayşe Demir'i “Geldi” olarak işaretler.
