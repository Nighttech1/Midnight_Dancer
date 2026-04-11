import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/core/app_ui_language.dart';

void main() {
  test('AppUiLanguage fromSettings / toSettingsEntry roundtrip', () {
    for (final lang in AppUiLanguage.values) {
      final settings = <String, dynamic>{}..addAll(lang.toSettingsEntry());
      expect(AppUiLanguage.fromSettings(settings), lang);
    }
  });

  test('AppUiLanguage fromSettings missing key uses flavor fallback', () {
    final fromEmpty = AppUiLanguage.fromSettings({});
    expect(fromEmpty, isIn(AppUiLanguage.values));
  });
}
