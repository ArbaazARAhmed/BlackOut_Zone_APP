import 'package:flutter_test/flutter_test.dart';

import 'package:blackout_zone_triage/main.dart';

void main() {
  testWidgets('Triage screen renders primary controls', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.text('Blackout Zone Triage (Offline)'), findsOneWidget);
    expect(find.text('Symptoms'), findsOneWidget);
    expect(find.text('Analyze (Offline)'), findsOneWidget);
    expect(find.text('View Model Schema'), findsOneWidget);

    expect(
      find.textContaining('This is not a doctor'),
      findsOneWidget,
    );
  });
}
