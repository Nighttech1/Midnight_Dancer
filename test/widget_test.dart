import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/app.dart';

void main() {
  testWidgets('App shows Midnight Dancer title', (WidgetTester tester) async {
    await tester.pumpWidget(const MidnightDancerApp());
    expect(find.text('Midnight Dancer'), findsOneWidget);
  });
}
