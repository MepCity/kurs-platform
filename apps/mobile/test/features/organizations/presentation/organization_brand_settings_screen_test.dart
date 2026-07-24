import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme_provider.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';
import 'package:kurs_platform_mobile/features/organizations/presentation/organization_brand_settings_screen.dart';

class _NonLinearTextScaler extends TextScaler {
  const _NonLinearTextScaler();

  @override
  double scale(double fontSize) => fontSize < 16
      ? fontSize * 1.7
      : fontSize < 22
      ? fontSize * 1.35
      : fontSize * 1.1;

  @override
  double get textScaleFactor => 1;
}

class _TestRepository implements OrganizationBrandRepository {
  _TestRepository({
    this.delay = Duration.zero,
    this.readFailure,
    this.writeFailure,
    this.primaryColor = '#2E7D32',
  });

  final Duration delay;
  final OrganizationsFailure? readFailure;
  final OrganizationsFailure? writeFailure;
  final String primaryColor;
  int version = 1;
  int getBrandCalls = 0;
  int getColorsCalls = 0;
  int getModulesCalls = 0;
  int updateBrandCalls = 0;
  int updateColorsCalls = 0;
  int updateModulesCalls = 0;
  late OrganizationBrand currentBrand = OrganizationBrand(
    primaryColor: primaryColor,
    secondaryColor: '#E65100',
    rowVersion: version,
  );
  OrganizationBrandColors currentColors = OrganizationBrandColors(
    rowVersion: 1,
    items: const [],
  );
  OrganizationModules currentModules = OrganizationModules(
    rowVersion: 1,
    items: OrganizationModuleCode.values.indexed
        .map(
          (entry) => OrganizationModule(
            code: entry.$2,
            isEnabled: true,
            sortOrder: entry.$1,
          ),
        )
        .toList(),
  );

  Future<void> _beforeRead() async {
    await Future<void>.delayed(delay);
    if (readFailure case final failure?) throw failure;
  }

  Future<void> _beforeWrite() async {
    await Future<void>.delayed(delay);
    if (writeFailure case final failure?) throw failure;
  }

  void _reconcile(int nextVersion) {
    version = nextVersion;
    currentBrand = currentBrand.copyWith(rowVersion: version);
    currentColors = currentColors.copyWith(rowVersion: version);
    currentModules = currentModules.copyWith(rowVersion: version);
  }

  @override
  Future<OrganizationBrand> getBrand(String organizationId) async {
    getBrandCalls++;
    await _beforeRead();
    return currentBrand;
  }

  @override
  Future<OrganizationBrandColors> getBrandColors(String organizationId) async {
    getColorsCalls++;
    await _beforeRead();
    return currentColors;
  }

  @override
  Future<OrganizationModules> getModules(String organizationId) async {
    getModulesCalls++;
    await _beforeRead();
    return currentModules;
  }

  @override
  Future<OrganizationBrand> updateBrand(
    String organizationId,
    OrganizationBrand brand,
    String clientMutationId,
  ) async {
    updateBrandCalls++;
    await _beforeWrite();
    _reconcile(version + 1);
    currentBrand = OrganizationBrand(
      primaryColor: brand.primaryColor,
      secondaryColor: brand.secondaryColor,
      rowVersion: version,
    );
    return currentBrand;
  }

  @override
  Future<OrganizationBrandColors> replaceBrandColors(
    String organizationId,
    OrganizationBrandColors colors,
    String clientMutationId,
  ) async {
    updateColorsCalls++;
    await _beforeWrite();
    _reconcile(version + 1);
    currentColors = OrganizationBrandColors(
      rowVersion: version,
      items: colors.items,
    );
    return currentColors;
  }

  @override
  Future<OrganizationModules> updateModules(
    String organizationId,
    OrganizationModules modules,
    String clientMutationId,
  ) async {
    updateModulesCalls++;
    await _beforeWrite();
    _reconcile(version + 1);
    currentModules = OrganizationModules(
      rowVersion: version,
      items: modules.items,
    );
    return currentModules;
  }
}

Widget app({
  required OrganizationBrandRepository repository,
  required AppThemeProvider themeProvider,
  bool canManageBrand = true,
  bool canManageModules = true,
  String organizationId = 'org-1',
  TextScaler textScaler = TextScaler.noScaling,
}) => AppThemeScope(
  notifier: themeProvider,
  child: MaterialApp(
    theme: themeProvider.themeData,
    home: Builder(
      builder: (context) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: textScaler),
        child: OrganizationBrandSettingsScreen(
          organizationId: organizationId,
          repository: repository,
          canManageBrand: canManageBrand,
          canManageModules: canManageModules,
        ),
      ),
    ),
  ),
);

void setRealSize(WidgetTester tester, Size size) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void expectNoFlutterExceptions(WidgetTester tester, String reason) {
  final exceptions = <Object>[];
  for (;;) {
    final exception = tester.takeException();
    if (exception == null) break;
    exceptions.add(exception);
  }
  if (exceptions.isNotEmpty) {
    fail('$reason:\n${exceptions.join('\n---\n')}');
  }
}

Future<void> pumpReady(
  WidgetTester tester, {
  // ignore: library_private_types_in_public_api
  required _TestRepository repository,
  required AppThemeProvider provider,
  bool canManageBrand = true,
  bool canManageModules = true,
  TextScaler textScaler = TextScaler.noScaling,
}) async {
  await tester.pumpWidget(
    app(
      repository: repository,
      themeProvider: provider,
      canManageBrand: canManageBrand,
      canManageModules: canManageModules,
      textScaler: textScaler,
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> scrollTo(WidgetTester tester, Finder finder) async {
  await tester.scrollUntilVisible(
    finder,
    300,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.ensureVisible(finder);
  await tester.pump();
}

void main() {
  group('OrganizationBrandSettingsScreen permissions and states', () {
    testWidgets('brand-only loads and renders only brand resources', (
      tester,
    ) async {
      final repository = _TestRepository();
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
        canManageModules: false,
      );
      expect(find.text('Marka renkleri'), findsOneWidget);
      expect(find.text('Etkin modüller'), findsNothing);
      expect(repository.getBrandCalls, 1);
      expect(repository.getColorsCalls, 1);
      expect(repository.getModulesCalls, 0);
    });

    testWidgets('module-only loads and renders only module resources', (
      tester,
    ) async {
      final repository = _TestRepository();
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
        canManageBrand: false,
      );
      expect(find.text('Marka renkleri'), findsNothing);
      expect(find.text('Etkin modüller'), findsOneWidget);
      expect(repository.getBrandCalls, 0);
      expect(repository.getColorsCalls, 0);
      expect(repository.getModulesCalls, 1);
    });

    testWidgets('both permissions render both independent surfaces', (
      tester,
    ) async {
      final repository = _TestRepository();
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
      );
      expect(find.text('Marka renkleri'), findsOneWidget);
      await scrollTo(tester, find.byKey(const Key('modules_save')));
      expect(find.text('Etkin modüller'), findsOneWidget);
      expect(repository.getModulesCalls, 1);
    });

    testWidgets('no permission is fail-closed without repository calls', (
      tester,
    ) async {
      final repository = _TestRepository();
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
        canManageBrand: false,
        canManageModules: false,
      );
      expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);
      expect(repository.getBrandCalls, 0);
      expect(repository.getColorsCalls, 0);
      expect(repository.getModulesCalls, 0);
    });

    testWidgets('loading uses AppLoadingState', (tester) async {
      await tester.pumpWidget(
        app(
          repository: _TestRepository(delay: const Duration(seconds: 1)),
          themeProvider: AppThemeProvider(),
        ),
      );
      expect(find.text('Marka ayarları yükleniyor…'), findsOneWidget);
      await tester.pump(const Duration(seconds: 4));
    });

    testWidgets('load error uses AppErrorState and retry works', (
      tester,
    ) async {
      final repository = _TestRepository(
        readFailure: const OrganizationsFailure(
          OrganizationsFailureCode.internalError,
          'Sunucu geçici olarak yanıt vermiyor.',
        ),
      );
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
      );
      expect(find.text('Sunucu geçici olarak yanıt vermiyor.'), findsOneWidget);
      expect(find.text('Tekrar Dene'), findsOneWidget);
      await tester.tap(find.text('Tekrar Dene'));
      await tester.pumpAndSettle();
      expect(repository.getBrandCalls, 2);
      expect(find.text('Marka renkleri'), findsNothing);
    });

    testWidgets(
      '401/403 uses unauthorized state and never renders stale form',
      (tester) async {
        await pumpReady(
          tester,
          repository: _TestRepository(
            readFailure: const OrganizationsFailure(
              OrganizationsFailureCode.forbidden,
              'Bu kurum ayarına erişemezsiniz.',
            ),
          ),
          provider: AppThemeProvider(),
        );
        expect(find.text('Bu kurum ayarına erişemezsiniz.'), findsOneWidget);
        expect(find.text('Marka renkleri'), findsNothing);
      },
    );

    testWidgets('empty palette has explanatory empty state', (tester) async {
      await pumpReady(
        tester,
        repository: _TestRepository(),
        provider: AppThemeProvider(),
      );
      expect(find.byKey(const Key('palette_empty')), findsOneWidget);
      expect(find.text('Yardımcı renk yok'), findsOneWidget);
    });
  });

  group('OrganizationBrandSettingsScreen interactions', () {
    testWidgets('brand save updates theme only after server success', (
      tester,
    ) async {
      final provider = AppThemeProvider();
      final repository = _TestRepository();
      await pumpReady(tester, repository: repository, provider: provider);
      await tester.enterText(find.byKey(const Key('brand_primary')), '#1565C0');
      await tester.enterText(
        find.byKey(const Key('brand_secondary')),
        '#00796B',
      );
      await tester.tap(find.byKey(const Key('brand_save')));
      await tester.pumpAndSettle();
      expect(repository.updateBrandCalls, 1);
      expect(provider.theme.primary, const Color(0xFF1565C0));
      expect(find.text('Kaydedildi.'), findsOneWidget);
    });

    testWidgets('failed and validation saves never change theme', (
      tester,
    ) async {
      final provider = AppThemeProvider();
      await pumpReady(
        tester,
        repository: _TestRepository(
          writeFailure: const OrganizationsFailure(
            OrganizationsFailureCode.internalError,
            'Çok uzun bir Türkçe hata mesajı: Ayarlar sunucu tarafından '
            'onaylanmadı; lütfen bağlantınızı denetleyip yeniden deneyin.',
          ),
        ),
        provider: provider,
      );
      await tester.enterText(find.byKey(const Key('brand_primary')), '#BAD');
      await tester.tap(find.byKey(const Key('brand_save')));
      await tester.pump();
      expect(find.textContaining('#RRGGBB'), findsOneWidget);
      expect(provider.theme.primary, const Color(0xFF2E7D32));

      await tester.enterText(find.byKey(const Key('brand_primary')), '#1565C0');
      await tester.enterText(
        find.byKey(const Key('brand_secondary')),
        '#00796B',
      );
      await tester.tap(find.byKey(const Key('brand_save')));
      await tester.pumpAndSettle();
      expect(find.textContaining('sunucu tarafından'), findsOneWidget);
      expect(provider.theme.primary, const Color(0xFF2E7D32));
    });

    testWidgets(
      'version conflict preserves form values and exposes retry path',
      (tester) async {
        await pumpReady(
          tester,
          repository: _TestRepository(
            writeFailure: const OrganizationsFailure(
              OrganizationsFailureCode.versionConflict,
              'Ayarlar başka bir kullanıcı tarafından güncellendi.',
            ),
          ),
          provider: AppThemeProvider(),
        );
        await tester.enterText(
          find.byKey(const Key('brand_primary')),
          '#1565C0',
        );
        await tester.enterText(
          find.byKey(const Key('brand_secondary')),
          '#00796B',
        );
        await tester.tap(find.byKey(const Key('brand_save')));
        await tester.pumpAndSettle();

        final primary = tester.widget<TextField>(
          find.descendant(
            of: find.byKey(const Key('brand_primary')),
            matching: find.byType(TextField),
          ),
        );
        expect(primary.controller!.text, '#1565C0');
        expect(
          find.textContaining('yeniden kaydedebilirsiniz'),
          findsOneWidget,
        );
        expect(find.byKey(const Key('brand_save')), findsOneWidget);
      },
    );

    testWidgets('palette add, edit, remove and save lifecycle works', (
      tester,
    ) async {
      final repository = _TestRepository();
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
      );
      await scrollTo(tester, find.byKey(const Key('palette_add')));
      await tester.tap(find.byKey(const Key('palette_add')));
      await tester.pump();
      expect(find.byKey(const Key('palette_hex_0')), findsOneWidget);
      await tester.enterText(find.byKey(const Key('palette_hex_0')), '#ABCDEF');
      tester.testTextInput.hide();
      await tester.pump();
      await scrollTo(tester, find.byKey(const Key('palette_save')));
      await tester.drag(find.byType(ListView), const Offset(0, 160));
      await tester.pump();
      await tester.tap(find.text('Paleti Kaydet'));
      await tester.pumpAndSettle();
      expect(repository.updateColorsCalls, 1);
      expect(repository.currentColors.items.single.colorHex, '#ABCDEF');

      await tester.tap(find.byKey(const Key('palette_remove_0')));
      await tester.pump();
      expect(find.byKey(const Key('palette_empty')), findsOneWidget);
    });

    testWidgets('module toggle and save sends one modules mutation', (
      tester,
    ) async {
      final repository = _TestRepository();
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
      );
      await scrollTo(tester, find.byKey(const Key('module_ATT')));
      await tester.tap(find.byKey(const Key('module_ATT')));
      await scrollTo(tester, find.byKey(const Key('modules_save')));
      await tester.tap(find.byKey(const Key('modules_save')));
      await tester.pumpAndSettle();
      expect(repository.updateModulesCalls, 1);
      expect(repository.currentModules.items.first.isEnabled, isFalse);
    });

    testWidgets('double tap makes one request and disables every save button', (
      tester,
    ) async {
      final repository = _TestRepository(delay: const Duration(seconds: 1));
      await pumpReady(
        tester,
        repository: repository,
        provider: AppThemeProvider(),
      );
      await tester.enterText(find.byKey(const Key('brand_primary')), '#1565C0');
      await tester.enterText(
        find.byKey(const Key('brand_secondary')),
        '#00796B',
      );
      await tester.tap(find.byKey(const Key('brand_save')));
      await tester.pump();
      await tester.tap(
        find.byKey(const Key('brand_save')),
        warnIfMissed: false,
      );
      expect(repository.updateBrandCalls, 1);
      await scrollTo(tester, find.byKey(const Key('palette_save')));
      final modulesSemantics = tester.getSemantics(
        find.byKey(const Key('palette_save')),
      );
      final modulesData = modulesSemantics.getSemanticsData();
      expect(modulesData.flagsCollection.isButton, isTrue);
      expect(modulesData.flagsCollection.isEnabled, Tristate.isFalse);
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
    });

    testWidgets('repository replacement discards stale async response', (
      tester,
    ) async {
      final provider = AppThemeProvider();
      final slow = _TestRepository(
        delay: const Duration(seconds: 1),
        primaryColor: '#C62828',
      );
      final fast = _TestRepository(primaryColor: '#1565C0');
      await tester.pumpWidget(app(repository: slow, themeProvider: provider));
      await tester.pumpWidget(app(repository: fast, themeProvider: provider));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextField>(
              find.descendant(
                of: find.byKey(const Key('brand_primary')),
                matching: find.byType(TextField),
              ),
            )
            .controller!
            .text,
        '#1565C0',
      );
      await tester.pump(const Duration(seconds: 2));
      expect(
        tester
            .widget<TextField>(
              find.descendant(
                of: find.byKey(const Key('brand_primary')),
                matching: find.byType(TextField),
              ),
            )
            .controller!
            .text,
        '#1565C0',
      );
      await tester.pump(const Duration(seconds: 2));
    });
  });

  group('OrganizationBrandSettingsScreen responsive and accessibility', () {
    final sizes = <String, Size>{
      '320dp': const Size(320, 640),
      'landscape': const Size(640, 320),
    };
    final scalers = <String, TextScaler>{
      '1.0': TextScaler.linear(1),
      '1.5': TextScaler.linear(1.5),
      '2.0': TextScaler.linear(2),
      'nonlinear': const _NonLinearTextScaler(),
    };
    for (final size in sizes.entries) {
      for (final scaler in scalers.entries) {
        testWidgets(
          '${size.key} at ${scaler.key} renders long Turkish content without exceptions',
          (tester) async {
            setRealSize(tester, size.value);
            await pumpReady(
              tester,
              repository: _TestRepository(),
              provider: AppThemeProvider(),
              textScaler: scaler.value,
            );
            await tester.enterText(
              find.byKey(const Key('brand_primary')),
              '#123',
            );
            await tester.tap(find.byKey(const Key('brand_save')));
            await tester.pump();
            expect(find.textContaining('#RRGGBB'), findsOneWidget);
            expectNoFlutterExceptions(
              tester,
              '${size.key}/${scaler.key} responsive sweep',
            );
          },
        );
      }
    }

    testWidgets(
      'interactive targets are 48dp and semantics expose button state',
      (tester) async {
        setRealSize(tester, const Size(320, 640));
        await pumpReady(
          tester,
          repository: _TestRepository(),
          provider: AppThemeProvider(),
        );
        for (final key in const [
          Key('brand_save'),
          Key('palette_add'),
          Key('palette_save'),
          Key('modules_save'),
        ]) {
          await scrollTo(tester, find.byKey(key));
          final size = tester.getSize(find.byKey(key));
          expect(size.width, greaterThanOrEqualTo(48), reason: '$key width');
          expect(size.height, greaterThanOrEqualTo(48), reason: '$key height');
        }
        final semantics = tester.getSemantics(
          find.byKey(const Key('modules_save')),
        );
        expect(semantics.label, contains('Modülleri Kaydet'));
        final data = semantics.getSemanticsData();
        expect(data.flagsCollection.isButton, isTrue);
        expect(data.flagsCollection.isEnabled, Tristate.isTrue);
      },
    );

    testWidgets('success and errors are live regions', (tester) async {
      await pumpReady(
        tester,
        repository: _TestRepository(),
        provider: AppThemeProvider(),
      );
      await tester.enterText(find.byKey(const Key('brand_primary')), '#1565C0');
      await tester.enterText(
        find.byKey(const Key('brand_secondary')),
        '#00796B',
      );
      await tester.tap(find.byKey(const Key('brand_save')));
      await tester.pumpAndSettle();
      final semantics = tester.getSemantics(find.text('Kaydedildi.'));
      expect(semantics.getSemanticsData().flagsCollection.isLiveRegion, isTrue);
    });

    testWidgets(
      'keyboard inset and bottom safe area keep the form scrollable',
      (tester) async {
        setRealSize(tester, const Size(320, 640));
        tester.view.viewInsets = const FakeViewPadding(bottom: 240);
        addTearDown(tester.view.reset);
        await pumpReady(
          tester,
          repository: _TestRepository(),
          provider: AppThemeProvider(),
        );
        await tester.showKeyboard(find.byKey(const Key('brand_secondary')));
        await tester.pump();
        await tester.drag(find.byType(ListView), const Offset(0, -300));
        await tester.pump();
        await scrollTo(tester, find.byKey(const Key('modules_save')));
        expect(find.byKey(const Key('modules_save')), findsOneWidget);
        expectNoFlutterExceptions(tester, 'keyboard and safe area');
      },
    );
  });
}
