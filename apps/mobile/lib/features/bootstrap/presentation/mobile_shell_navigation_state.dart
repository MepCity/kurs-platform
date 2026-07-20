import 'package:flutter/widgets.dart';

import 'mobile_navigation_shell.dart';

/// [MobileShellContext.navigationIdentity] kaydının tipli takma adı. Deferred
/// navigasyon callback'leri (bkz. [MobileShellRouteRequest] işleme) gerçek
/// push anında bu değeri yakalandığı andaki değerle karşılaştırarak identity
/// değişimini tespit eder.
typedef MobileShellNavigationIdentity = ({
  String session,
  String? organization,
  String? support,
  MobileShellRole role,
  bool supportMode,
  String? classId,
});

/// Bir Navigator stack'inde izlenen tipli rota işareti.
///
/// Root rotalar [isRoot] true ile işaretlenir; iç rotalar false ile.
/// Politika uzlaştırması başlık veya widget tipine değil yalnızca
/// [routeId]'ye bakar.
///
/// [trusted] `false` olduğunda bu girdi, gerçek `MobileShellRouteMarker`
/// argümanı taşımayan bilinmeyen/işaretsiz bir rotayı temsil eder (bkz.
/// `_StackObserver._markerFor`). Böyle bir girdi listedeki konumunu ve
/// sayısını korur (altındaki gerçek girdilerin izlenebilirliği bozulmaz)
/// ama [MobileShellStackTracker.firstUnauthorizedIndex] tarafından politika
/// kontrolüne bakılmaksızın her zaman yetkisiz sayılır; böylece ne kök
/// kabul edilir ne de yetki uzlaştırmasını atlatabilir.
@immutable
class MobileShellRouteMarker {
  const MobileShellRouteMarker(
    this.routeId, {
    required this.isRoot,
    this.trusted = true,
  });
  final MobileShellRouteId routeId;
  final bool isRoot;
  final bool trusted;

  @override
  bool operator ==(Object other) =>
      other is MobileShellRouteMarker &&
      other.routeId == routeId &&
      other.isRoot == isRoot &&
      other.trusted == trusted;

  @override
  int get hashCode => Object.hash(routeId, isRoot, trusted);

  @override
  String toString() =>
      'MobileShellRouteMarker(${routeId.name}, isRoot: $isRoot, trusted: $trusted)';
}

/// Navigation request için monoton artan sıra numarası.
///
/// Shell yaşam döngüsü boyunca "exactly once" garanti etmek için
/// [MobileShellRouteRequest.requestId]'nin yanı sıra [sequence] kullanılır.
///
/// Sözleşme:
/// - Çağıran, her yeni niyet (kullanıcı girişi veya yeniden deneme dahil)
///   için çağrılar arasında monoton artan ve benzersiz bir `sequence` üretir.
/// - `requestId` izlenebilirlik/günlük kimliği olarak korunur.
/// - Shell yalnız `sequence > lastConsumedSequence` olan isteği değerlendirir.
/// - Kabul edilen veya reddedilen (yetkisiz dahil) tüm istekler tüketilmiş
///   sayılır ve high-watermark'ı ilerletir.
/// - Aynı veya daha küçük sequence tekrar işlenmez.
/// - Yeni (daha büyük) sequence aynı rotayı yeniden açabilir.
/// - Sequence shell yaşamı boyunca ve navigation identity değişimlerinde
///   sıfırlanmaz.
/// - Aynı sequence farklı `requestId`/`route` ile gelse dahi reddedilir.
/// - Out-of-order (daha küçük) eski istekler reddedilir.
///
/// Bu model sabit bellekle kesin tekrar engeli sağlar; bounded LRU
/// kullanılmaz.
class MobileShellRequestSequence {
  /// Hiçbir istek işlenmemiş başlangıç değeri.
  static const int initial = 0;
}

/// İki context karşılaştırmasından üretilen saf uzlaştırma kararı.
///
/// Widget/render katmanı bu etkiyi uygular; kendisi bağlam karşılaştırması
/// yapmaz. Üç olası etki vardır:
/// - [MobileShellReconciliationResetStacks]: navigation identity değişti,
///   tüm nested Navigator stack'leri güvenli köke sıfırlanmalı.
/// - [MobileShellReconciliationReconcileRoutes]: identity sabit, yalnızca
///   permission/politika girdileri değişti; açık route'lar politika
///   kataloğuyla yeniden doğrulanmalı.
/// - [MobileShellReconciliationNone]: anlamsal değişim yok; stack sabit
///   kalmalı.
sealed class MobileShellReconciliationEffect {
  const MobileShellReconciliationEffect();
}

class MobileShellReconciliationResetStacks
    extends MobileShellReconciliationEffect {
  const MobileShellReconciliationResetStacks();
}

class MobileShellReconciliationReconcileRoutes
    extends MobileShellReconciliationEffect {
  const MobileShellReconciliationReconcileRoutes();
}

class MobileShellReconciliationNone extends MobileShellReconciliationEffect {
  const MobileShellReconciliationNone();
}

/// Saf, widget-bağımsız reconciliation fonksiyonları.
///
/// Bu sınıfın hiçbir durumu yoktur; tüm metodlar girdiye göre deterministik
/// etki üretir. Bu sayede `didUpdateWidget` içinde devasa bir blok oluşmaz.
class MobileShellReconciler {
  const MobileShellReconciler._();

  /// Navigation identity değişimini `MobileShellContext.navigationIdentity`
  /// kaydının structural eşitliği üzerinden tespit eder. Bu kayıt
  /// session/organization/support/role/supportMode/classId alanlarını kapsar.
  static bool identityChanged(
    MobileShellContext previous,
    MobileShellContext current,
  ) => previous.navigationIdentity != current.navigationIdentity;

  /// Permission kümesinin anlamsal içeriği değişmiş mi? Referans eşitliği
  /// yerine set içerik karşılaştırması yapar.
  static bool permissionsChanged(
    MobileShellContext previous,
    MobileShellContext current,
  ) => !setEquals(previous.permissions, current.permissions);

  /// `assignedClasses` (sınıf listesi) anlamsal içeriği değişmiş mi?
  /// Sınıf adı değişimi identity'yi etkilemediği için reconciliation bunu
  /// kendi başına bir etki üretmek için kullanmaz; yalnızca takip için.
  static bool assignedClassesChanged(
    MobileShellContext previous,
    MobileShellContext current,
  ) {
    final a = previous.classState.classes;
    final b = current.classState.classes;
    if (a.length != b.length) return true;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return true;
    }
    return false;
  }

  /// İki context'i karşılaştırıp uygun etkiyi döndürür.
  ///
  /// Öncelik sırası: identity > permission > none. Identity değişimi
  /// permission değişimini yutar (reset zaten her şeyi temizler).
  static MobileShellReconciliationEffect evaluate({
    required MobileShellContext previous,
    required MobileShellContext current,
  }) {
    if (identityChanged(previous, current)) {
      return const MobileShellReconciliationResetStacks();
    }
    if (permissionsChanged(previous, current)) {
      return const MobileShellReconciliationReconcileRoutes();
    }
    return const MobileShellReconciliationNone();
  }
}

/// Tek bir destination Navigator'ındaki route kimliklerinin doğruluk kaynağı.
///
/// Observer callback'leri sırasına veya gecikmesine bakılmaksızın gerçek
/// Navigator stack ile uyumlu kalır. Her destination için bağımsız bir
/// izleyici tutulur.
class MobileShellStackTracker {
  MobileShellStackTracker();

  final Map<MobileShellRouteId, List<MobileShellRouteMarker>> _stacks = {};

  /// Bu nesnenin hangi observer generation'a ait olduğu. Eski observer
  /// callback'leri [isStale] true döndürdüğünde atılır.
  int _generation = 0;

  int get generation => _generation;

  /// Tüm takibi sıfırlar ve yeni generation'a geçer. Eski observer'ların
  /// callback'leri artık bu tracker'ı etkilemez.
  void resetAll() {
    _stacks.clear();
    _generation++;
  }

  bool isStale(int observedGeneration) => observedGeneration != _generation;

  /// Bir destination'ın güncel route kimlik listesini döndürür. Hiç rota
  /// bilinmiyorsa yalnızca [root] içeren liste döner.
  List<MobileShellRouteMarker> stackFor(MobileShellRouteId destination) {
    final existing = _stacks[destination];
    if (existing != null && existing.isNotEmpty) {
      return List.unmodifiable(existing);
    }
    return List.unmodifiable([
      MobileShellRouteMarker(destination, isRoot: true),
    ]);
  }

  /// Root rotanın varlığını garanti eder; observer henüz tetiklenmediğinde
  /// ilk doğruluk kaynağı olur.
  void ensureRoot(MobileShellRouteId destination) {
    final list = _stacks.putIfAbsent(destination, () => []);
    if (list.isEmpty) {
      list.add(MobileShellRouteMarker(destination, isRoot: true));
    }
  }

  void _push(MobileShellRouteId destination, MobileShellRouteMarker marker) {
    final list = _stacks.putIfAbsent(destination, () => []);
    if (list.isEmpty) {
      list.add(MobileShellRouteMarker(destination, isRoot: true));
    }
    list.add(marker);
  }

  void _removeTop(MobileShellRouteId destination) {
    final list = _stacks[destination];
    if (list == null || list.length <= 1) return;
    list.removeLast();
  }

  void _popToRoot(MobileShellRouteId destination) {
    final list = _stacks[destination];
    if (list == null || list.isEmpty) return;
    list.removeRange(1, list.length);
  }

  void _replaceTopWith(
    MobileShellRouteId destination,
    MobileShellRouteMarker marker,
  ) {
    final list = _stacks.putIfAbsent(destination, () => []);
    if (list.isEmpty) {
      list.add(MobileShellRouteMarker(destination, isRoot: true));
    }
    if (list.length == 1) {
      list.add(marker);
    } else {
      list[list.length - 1] = marker;
    }
  }

  /// Verilen generation'a ait bir push callback'i. Eski generation atılır.
  void onPush(
    MobileShellRouteId destination,
    MobileShellRouteMarker marker, {
    required int generation,
  }) {
    if (isStale(generation)) return;
    _push(destination, marker);
  }

  void onPop(MobileShellRouteId destination, {required int generation}) {
    if (isStale(generation)) return;
    _removeTop(destination);
  }

  void onRemove(MobileShellRouteId destination, {required int generation}) {
    if (isStale(generation)) return;
    _removeTop(destination);
  }

  void onReplace(
    MobileShellRouteId destination,
    MobileShellRouteMarker marker, {
    required int generation,
  }) {
    if (isStale(generation)) return;
    _replaceTopWith(destination, marker);
  }

  void popToRoot(MobileShellRouteId destination, {required int generation}) {
    if (isStale(generation)) return;
    _popToRoot(destination);
  }

  /// Bu destination'ın stack'inde en az bir untrusted (marker'sız/bilinmeyen)
  /// girdi var mı? Planlı untrusted-route reconciliation'ın, temizlik
  /// zamanında hâlâ geçerli bir hedefi olup olmadığını doğrulamak için
  /// kullanılır.
  bool hasUntrusted(MobileShellRouteId destination) {
    final list = _stacks[destination];
    if (list == null) return false;
    return list.any((marker) => !marker.trusted);
  }

  /// Uzlaştırma için en alttaki yetkisiz route'un pozisyonunu döndürür.
  /// Root route güvenli taraf olarak kabul edilir; iç route politika dışıysa
  /// root'a kadar temizlenir. Güvenilmeyen (bilinmeyen/işaretsiz) bir girdi
  /// ([MobileShellRouteMarker.trusted] `false`) politika kontrolüne
  /// bakılmaksızın her zaman yetkisiz sayılır: fail-closed.
  ///
  /// Dönüş: yetkisiz ilk iç route'un dizini (root dahil değil), yoksa null.
  int? firstUnauthorizedIndex(
    MobileShellRouteId destination,
    MobileShellContext context,
  ) {
    final list = _stacks[destination];
    if (list == null) return null;
    for (var i = 1; i < list.length; i++) {
      final marker = list[i];
      if (!marker.trusted ||
          !MobileShellRoutePolicyCatalog.allows(context, marker.routeId)) {
        return i;
      }
    }
    return null;
  }

  /// Bir destination'da tanımlı marker ile izlenen en üst route döndürür.
  MobileShellRouteMarker? topMarker(MobileShellRouteId destination) {
    final list = _stacks[destination];
    if (list == null || list.isEmpty) return null;
    return list.last;
  }
}

bool setEquals<T>(Set<T> a, Set<T> b) {
  if (identical(a, b)) return true;
  if (a.length != b.length) return false;
  return a.containsAll(b);
}
