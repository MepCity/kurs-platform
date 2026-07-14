import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:a001_flutter_spike/main.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('VT-01–VT-03 sentetik girişten yoklamaya', (tester) async {
    await tester.pumpWidget(const SpikeApp());

    await tester.tap(find.byKey(const Key('login')));
    await tester.pumpAndSettle();
    expect(find.text('ORG-A — Gül Sınıfı'), findsOneWidget);
    expect(find.text('Ayşe Demir'), findsOneWidget);
    expect(find.text('Mehmet Kaya'), findsOneWidget);

    await tester.tap(find.byKey(const Key('attendance')));
    await tester.pumpAndSettle();
    expect(find.text('Bugünkü Yoklama'), findsOneWidget);

    await tester.tap(find.byKey(const Key('mark-present')));
    await tester.pumpAndSettle();
    expect(find.text('Ayşe Demir: Geldi'), findsOneWidget);
  });
}
