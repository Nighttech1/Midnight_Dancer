import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/app_ui_language.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';

/// Текущий язык интерфейса из загруженных настроек (до загрузки — [AppUiLanguage.fallbackForFlavor]).
final uiLanguageProvider = Provider<AppUiLanguage>((ref) {
  final async = ref.watch(appDataNotifierProvider);
  return async.maybeWhen(
    data: (d) => AppUiLanguage.fromSettings(d.settings),
    orElse: AppUiLanguage.fallbackForFlavor,
  );
});

/// Строки интерфейса для текущего языка (перестраивается при смене языка).
final appStringsProvider = Provider<AppStrings>((ref) {
  return AppStrings(ref.watch(uiLanguageProvider));
});
