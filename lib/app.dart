import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:midnight_dancer/core/app_ui_language.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'ui/screens/splash/splash_screen.dart';

class MidnightDancerApp extends StatelessWidget {
  const MidnightDancerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      child: _MaterialAppShell(),
    );
  }
}

class _MaterialAppShell extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = ref.watch(uiLanguageProvider);
    return MaterialApp(
      title: 'Midnight Dancer',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark,
      locale: lang.materialLocale,
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppUiLanguage.materialLocales,
      home: const SplashScreen(),
    );
  }
}
