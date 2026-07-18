import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/presentation/widgets/widgets.dart';
import 'package:kurs_platform_mobile/core/theme/app_semantic_colors.dart';
import 'package:kurs_platform_mobile/core/theme/app_spacing.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';

Widget _wrap(Widget child) {
  return MaterialApp(
    theme: const AppTheme(
      primary: Color(0xFF2E7D32),
      secondary: Color(0xFFE65100),
    ).themeData,
    home: Scaffold(body: child),
  );
}

void _noOp() {}

void main() {
  group('AppButton', () {
    const ValueKey<String> visualMaterialKey = ValueKey<String>(
      'app_button_visual_material',
    );

    testWidgets('filled button displays label and responds to tap', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      await tester.pumpWidget(
        _wrap(
          AppButton.filled(label: 'Kaydet', onPressed: () => tapped = true),
        ),
      );
      expect(find.text('Kaydet'), findsOneWidget);
      await tester.tap(find.text('Kaydet'));
      await tester.pump();
      expect(tapped, isTrue);
    });

    testWidgets('disabled button does not fire onPressed', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      await tester.pumpWidget(
        _wrap(AppButton.filled(label: 'Kaydet', onPressed: null)),
      );
      await tester.tap(find.text('Kaydet'));
      await tester.pump();
      expect(tapped, isFalse);
    });

    testWidgets('danger button uses ColorScheme.error and onError', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppButton.danger(label: 'Sil', onPressed: _noOp)),
      );
      final Material material = tester.widget<Material>(
        find.byKey(visualMaterialKey),
      );
      final Text text = tester.widget<Text>(find.text('Sil'));

      final theme = Theme.of(tester.element(find.byType(AppButton)));
      expect(material.color, theme.colorScheme.error);
      expect(text.style?.color, theme.colorScheme.onError);
    });

    testWidgets('all sizes have a visual Material height of 32/40/48 dp', (
      WidgetTester tester,
    ) async {
      final Map<AppButtonSize, double> expectedHeights = {
        AppButtonSize.small: AppSpacing.buttonHeightSm,
        AppButtonSize.medium: AppSpacing.buttonHeightMd,
        AppButtonSize.large: AppSpacing.buttonHeightLg,
      };

      for (final size in AppButtonSize.values) {
        await tester.pumpWidget(
          _wrap(AppButton(label: 'Kaydet', size: size, onPressed: () {})),
        );
        final RenderBox materialBox = tester.renderObject(
          find.byKey(visualMaterialKey),
        );
        expect(
          materialBox.size.height,
          expectedHeights[size],
          reason: 'visual height for $size',
        );
      }
    });

    testWidgets('all sizes have at least 48x48 hit/semantics area', (
      WidgetTester tester,
    ) async {
      for (final size in AppButtonSize.values) {
        await tester.pumpWidget(
          _wrap(AppButton(label: 'Kaydet', size: size, onPressed: () {})),
        );
        final RenderBox box = tester.renderObject(find.byType(AppButton));
        expect(
          box.size.width,
          greaterThanOrEqualTo(AppSpacing.touchTarget),
          reason: 'width for $size',
        );
        expect(
          box.size.height,
          greaterThanOrEqualTo(AppSpacing.touchTarget),
          reason: 'height for $size',
        );
      }
    });

    testWidgets('width smaller than 48 is clamped to hit area', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(AppButton.filled(label: 'A', width: 30, onPressed: () {})),
      );
      final RenderBox box = tester.renderObject(find.byType(AppButton));
      expect(box.size.width, greaterThanOrEqualTo(AppSpacing.touchTarget));
    });

    testWidgets('outlined button uses primary border', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppButton.outlined(label: 'İptal', onPressed: _noOp)),
      );
      final Material material = tester.widget<Material>(
        find.byKey(visualMaterialKey),
      );
      final shape = material.shape as RoundedRectangleBorder;
      final theme = Theme.of(tester.element(find.byType(AppButton)));
      expect(shape.side.color, theme.colorScheme.primary);
    });

    testWidgets('long label wraps and grows visual Material height', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(
          SizedBox(
            width: 140,
            child: AppButton.filled(
              label: 'Çok uzun Türkçe düğme etiketi',
              onPressed: () {},
            ),
          ),
        ),
      );
      final RenderBox visualBox = tester.renderObject(
        find.byKey(visualMaterialKey),
      );
      final RenderBox hitBox = tester.renderObject(find.byType(AppButton));
      expect(
        visualBox.size.height,
        greaterThan(AppSpacing.buttonHeightMd),
        reason: 'visual surface should grow when text wraps',
      );
      expect(
        hitBox.size.height,
        greaterThanOrEqualTo(AppSpacing.touchTarget),
        reason: 'hit area must remain at least 48 dp',
      );
      expect(
        hitBox.size.width,
        greaterThanOrEqualTo(AppSpacing.touchTarget),
        reason: 'hit area must remain at least 48 dp',
      );

      final Text text = tester.widget<Text>(
        find.text('Çok uzun Türkçe düğme etiketi'),
      );
      expect(text.maxLines, isNull);
      expect(text.overflow, isNot(TextOverflow.ellipsis));
    });

    testWidgets('focused button activates on Enter', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      final FocusNode focusNode = FocusNode(debugLabel: 'enter');
      addTearDown(focusNode.dispose);
      await tester.pumpWidget(
        _wrap(
          AppButton.filled(
            label: 'Kaydet',
            focusNode: focusNode,
            onPressed: () => tapped = true,
          ),
        ),
      );
      focusNode.requestFocus();
      await tester.pump();
      expect(focusNode.hasFocus, isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(tapped, isTrue);
    });

    testWidgets('focused button activates on Space', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      final FocusNode focusNode = FocusNode(debugLabel: 'space');
      addTearDown(focusNode.dispose);
      await tester.pumpWidget(
        _wrap(
          AppButton.filled(
            label: 'Kaydet',
            focusNode: focusNode,
            onPressed: () => tapped = true,
          ),
        ),
      );
      focusNode.requestFocus();
      await tester.pump();
      expect(focusNode.hasFocus, isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(tapped, isTrue);
    });

    testWidgets('disabled button is not focusable and ignores keyboard', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      final FocusNode focusNode = FocusNode(debugLabel: 'disabled');
      addTearDown(focusNode.dispose);
      await tester.pumpWidget(
        _wrap(
          AppButton.filled(
            label: 'Kaydet',
            focusNode: focusNode,
            onPressed: null,
          ),
        ),
      );
      focusNode.requestFocus();
      await tester.pump();
      expect(focusNode.hasFocus, isFalse);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(tapped, isFalse);
    });

    testWidgets('semantics exposes button, enabled and label', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(AppButton.filled(label: 'Kaydet', onPressed: () {})),
      );
      final SemanticsNode semantics = tester.getSemantics(
        find.byType(AppButton),
      );
      final SemanticsData data = semantics.getSemanticsData();
      expect(data.flagsCollection.isButton, isTrue);
      expect(data.flagsCollection.isEnabled, Tristate.isTrue);
      expect(semantics.label, 'Kaydet');
    });
  });

  group('AppTextField', () {
    testWidgets('shows label and helper', (WidgetTester tester) async {
      await tester.pumpWidget(
        _wrap(const AppTextField(label: 'Ad', helper: 'Öğrenci adı')),
      );
      expect(find.text('Ad'), findsOneWidget);
      expect(find.text('Öğrenci adı'), findsOneWidget);
    });

    testWidgets('shows error text in semantics and InputDecoration', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppTextField(label: 'Ad', error: 'Ad zorunludur')),
      );
      expect(find.text('Ad zorunludur'), findsOneWidget);
      final textField = tester.widget<TextField>(find.byType(TextField));
      expect(textField.decoration?.errorText, 'Ad zorunludur');
      expect(textField.decoration?.helperText, isNull);
    });

    testWidgets('error border is active when error is set', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(_wrap(const AppTextField(error: 'Hata')));
      final theme = Theme.of(tester.element(find.byType(AppTextField)));
      final inputTheme = theme.inputDecorationTheme;
      expect(inputTheme.errorBorder?.borderSide.color, theme.colorScheme.error);
      expect(
        inputTheme.focusedErrorBorder?.borderSide.color,
        theme.colorScheme.error,
      );
    });

    testWidgets('disabled border is used when enabled is false', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(_wrap(const AppTextField(enabled: false)));
      final theme = Theme.of(tester.element(find.byType(AppTextField)));
      final inputTheme = theme.inputDecorationTheme;
      expect(inputTheme.disabledBorder, isNotNull);
    });

    testWidgets('single-line field is at least 48 dp', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(_wrap(const AppTextField()));
      final RenderBox box = tester.renderObject(find.byType(AppTextField));
      expect(box.size.height, greaterThanOrEqualTo(AppSpacing.inputHeight));
    });

    testWidgets('multiline field is at least 80 dp', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppTextField(minLines: 3, maxLines: 5)),
      );
      final RenderBox box = tester.renderObject(find.byType(AppTextField));
      expect(
        box.size.height,
        greaterThanOrEqualTo(AppSpacing.inputHeightMultiline),
      );
    });
  });

  group('AppCard', () {
    testWidgets('renders child and responds to tap', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      await tester.pumpWidget(
        _wrap(
          AppCard(
            onTap: () => tapped = true,
            child: const Text('Kart içeriği'),
          ),
        ),
      );
      expect(find.text('Kart içeriği'), findsOneWidget);
      await tester.tap(find.text('Kart içeriği'));
      await tester.pump();
      expect(tapped, isTrue);
    });

    testWidgets('tappable card hit area is at least 48x48', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(
          AppCard(onTap: () {}, child: const SizedBox(width: 20, height: 20)),
        ),
      );
      final RenderBox box = tester.renderObject(find.byType(AppCard));
      expect(box.size.width, greaterThanOrEqualTo(AppSpacing.touchTarget));
      expect(box.size.height, greaterThanOrEqualTo(AppSpacing.touchTarget));
    });
  });

  group('AppBottomActionArea', () {
    Widget wrapBottomActionArea(Widget child) {
      return MaterialApp(
        theme: const AppTheme(
          primary: Color(0xFF2E7D32),
          secondary: Color(0xFFE65100),
        ).themeData,
        home: Scaffold(body: child),
      );
    }

    void setViewInsets(
      WidgetTester tester, {
      required FakeViewPadding viewPadding,
      required FakeViewPadding viewInsets,
    }) {
      tester.view.devicePixelRatio = 1.0;
      tester.view.viewPadding = viewPadding;
      tester.view.viewInsets = viewInsets;
      addTearDown(tester.view.reset);
    }

    testWidgets('uses safe-area bottom minimum when insets are small', (
      WidgetTester tester,
    ) async {
      setViewInsets(
        tester,
        viewPadding: FakeViewPadding.zero,
        viewInsets: FakeViewPadding.zero,
      );
      await tester.pumpWidget(
        wrapBottomActionArea(const AppBottomActionArea(child: Text('Action'))),
      );
      final Padding padding = tester.widget<Padding>(
        find.descendant(
          of: find.byType(AppBottomActionArea),
          matching: find.byType(Padding),
        ),
      );
      expect(padding.padding, const EdgeInsets.fromLTRB(16, 16, 16, 48));
    });

    testWidgets('uses viewPadding.bottom when it exceeds the minimum', (
      WidgetTester tester,
    ) async {
      setViewInsets(
        tester,
        viewPadding: const FakeViewPadding(bottom: 60),
        viewInsets: FakeViewPadding.zero,
      );
      await tester.pumpWidget(
        wrapBottomActionArea(const AppBottomActionArea(child: Text('Action'))),
      );
      final Padding padding = tester.widget<Padding>(
        find.descendant(
          of: find.byType(AppBottomActionArea),
          matching: find.byType(Padding),
        ),
      );
      expect(padding.padding, const EdgeInsets.fromLTRB(16, 16, 16, 60));
    });

    testWidgets('adds keyboard clearance above the keyboard inset', (
      WidgetTester tester,
    ) async {
      setViewInsets(
        tester,
        viewPadding: FakeViewPadding.zero,
        viewInsets: const FakeViewPadding(bottom: 200),
      );
      await tester.pumpWidget(
        wrapBottomActionArea(const AppBottomActionArea(child: Text('Action'))),
      );
      final Padding padding = tester.widget<Padding>(
        find.descendant(
          of: find.byType(AppBottomActionArea),
          matching: find.byType(Padding),
        ),
      );
      expect(padding.padding, const EdgeInsets.fromLTRB(16, 16, 16, 216));
    });
  });

  group('AppListItem', () {
    testWidgets('single-line item is at least 48 dp', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppListItem(title: 'Ahmet Yılmaz', divider: false)),
      );
      final RenderBox box = tester.renderObject(find.byType(AppListItem));
      expect(
        box.size.height,
        greaterThanOrEqualTo(AppSpacing.listItemMinHeight),
      );
    });

    testWidgets('two-line item is at least 64 dp', (WidgetTester tester) async {
      await tester.pumpWidget(
        _wrap(
          const AppListItem(
            title: 'Ahmet Yılmaz',
            subtitle: 'A sınıfı',
            divider: false,
          ),
        ),
      );
      final RenderBox box = tester.renderObject(find.byType(AppListItem));
      expect(
        box.size.height,
        greaterThanOrEqualTo(AppSpacing.listItemMinHeightDouble),
      );
    });

    testWidgets('tappable item hit area is at least 48 dp high', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(AppListItem(title: 'A', onTap: () {}, divider: false)),
      );
      final RenderBox box = tester.renderObject(find.byType(AppListItem));
      expect(box.size.height, greaterThanOrEqualTo(AppSpacing.touchTarget));
    });

    testWidgets('fires onTap', (WidgetTester tester) async {
      bool tapped = false;
      await tester.pumpWidget(
        _wrap(AppListItem(title: 'Ahmet', onTap: () => tapped = true)),
      );
      await tester.tap(find.text('Ahmet'));
      await tester.pump();
      expect(tapped, isTrue);
    });

    testWidgets('long title and subtitle wrap instead of clipping', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(
          SizedBox(
            width: 200,
            child: AppListItem(
              title:
                  'Çok uzun öğrenci adı ve soyadı buraya yazıldığında taşmalıdır',
              subtitle: 'A sınıfı, hafta sonu grubu, öğleden sonra programı',
              onTap: () {},
              divider: false,
            ),
          ),
        ),
      );
      final RenderBox box = tester.renderObject(find.byType(AppListItem));
      expect(box.size.height, greaterThanOrEqualTo(AppSpacing.touchTarget));
      expect(
        find.text(
          'Çok uzun öğrenci adı ve soyadı buraya yazıldığında taşmalıdır',
        ),
        findsOneWidget,
      );
      expect(
        find.text('A sınıfı, hafta sonu grubu, öğleden sonra programı'),
        findsOneWidget,
      );
    });
  });

  group('AppStatusChip', () {
    testWidgets('success uses successContainer/onSuccessContainer', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppStatusChip(label: 'Geldi', type: AppStatusType.success)),
      );
      final semantic = AppSemanticColors.of(
        tester.element(find.byType(AppStatusChip)),
      );
      final DecoratedBox box = tester.widget<DecoratedBox>(
        find.descendant(
          of: find.byType(AppStatusChip),
          matching: find.byType(DecoratedBox),
        ),
      );
      final ShapeDecoration decoration = box.decoration as ShapeDecoration;
      final Text text = tester.widget<Text>(find.text('Geldi'));
      expect(decoration.color, semantic.successContainer);
      expect(text.style?.color, semantic.onSuccessContainer);
    });

    testWidgets('error uses errorContainer/onErrorContainer', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppStatusChip(label: 'Gelmedi', type: AppStatusType.error)),
      );
      final scheme = Theme.of(
        tester.element(find.byType(AppStatusChip)),
      ).colorScheme;
      final DecoratedBox box = tester.widget<DecoratedBox>(
        find.descendant(
          of: find.byType(AppStatusChip),
          matching: find.byType(DecoratedBox),
        ),
      );
      final ShapeDecoration decoration = box.decoration as ShapeDecoration;
      final Text text = tester.widget<Text>(find.text('Gelmedi'));
      expect(decoration.color, scheme.errorContainer);
      expect(text.style?.color, scheme.onErrorContainer);
    });

    testWidgets('warning uses warningContainer/onWarningContainer', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(const AppStatusChip(label: 'Geç', type: AppStatusType.warning)),
      );
      final semantic = AppSemanticColors.of(
        tester.element(find.byType(AppStatusChip)),
      );
      final DecoratedBox box = tester.widget<DecoratedBox>(
        find.descendant(
          of: find.byType(AppStatusChip),
          matching: find.byType(DecoratedBox),
        ),
      );
      final ShapeDecoration decoration = box.decoration as ShapeDecoration;
      final Text text = tester.widget<Text>(find.text('Geç'));
      expect(decoration.color, semantic.warningContainer);
      expect(text.style?.color, semantic.onWarningContainer);
    });

    testWidgets('long label wraps to multiple lines without clipping', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(
          SizedBox(
            width: 80,
            child: const AppStatusChip(
              label: 'Çok uzun durum etiketi devam eden metin',
              type: AppStatusType.info,
            ),
          ),
        ),
      );
      final Text text = tester.widget<Text>(
        find.text('Çok uzun durum etiketi devam eden metin'),
      );
      expect(text.maxLines, isNull);
      expect(text.overflow, isNot(TextOverflow.ellipsis));
      expect(text.softWrap, isTrue);

      final RenderBox box = tester.renderObject(find.byType(AppStatusChip));
      expect(
        box.size.height,
        greaterThan(AppSpacing.chipHeight),
        reason: 'chip should grow beyond single-line height when text wraps',
      );
    });
  });

  group('AppSyncIndicator', () {
    testWidgets('pending shows access_time icon', (WidgetTester tester) async {
      await tester.pumpWidget(
        _wrap(const AppSyncIndicator(status: AppSyncStatus.pending)),
      );
      expect(find.byIcon(Icons.access_time), findsOneWidget);
    });

    testWidgets('failed uses error color and is tappable', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      await tester.pumpWidget(
        _wrap(
          AppSyncIndicator(
            status: AppSyncStatus.failed,
            onTap: () => tapped = true,
          ),
        ),
      );
      expect(find.byIcon(Icons.error), findsOneWidget);

      final icon = tester.widget<Icon>(find.byIcon(Icons.error));
      final scheme = Theme.of(
        tester.element(find.byType(AppSyncIndicator)),
      ).colorScheme;
      expect(icon.color, scheme.error);

      await tester.tap(find.byType(AppSyncIndicator));
      await tester.pump();
      expect(tapped, isTrue);
    });

    testWidgets('hit area is at least 48x48 when failed', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(AppSyncIndicator(status: AppSyncStatus.failed, onTap: () {})),
      );
      final RenderBox box = tester.renderObject(find.byType(AppSyncIndicator));
      expect(box.size.width, greaterThanOrEqualTo(AppSpacing.touchTarget));
      expect(box.size.height, greaterThanOrEqualTo(AppSpacing.touchTarget));
    });

    testWidgets('failed state with onTap is a button', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(AppSyncIndicator(status: AppSyncStatus.failed, onTap: () {})),
      );
      final SemanticsNode semantics = tester.getSemantics(
        find.byType(AppSyncIndicator),
      );
      expect(semantics.getSemanticsData().flagsCollection.isButton, isTrue);
    });

    testWidgets('non-failed states are not buttons', (
      WidgetTester tester,
    ) async {
      for (final status in <AppSyncStatus>[
        AppSyncStatus.pending,
        AppSyncStatus.syncing,
        AppSyncStatus.success,
      ]) {
        await tester.pumpWidget(
          _wrap(AppSyncIndicator(status: status, onTap: () {})),
        );
        final SemanticsNode semantics = tester.getSemantics(
          find.byType(AppSyncIndicator),
        );
        expect(
          semantics.getSemanticsData().flagsCollection.isButton,
          isFalse,
          reason: '$status should not be a button',
        );
      }
    });
  });
}
