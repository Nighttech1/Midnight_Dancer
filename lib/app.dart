import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'ui/screens/splash/splash_screen.dart';

class MidnightDancerApp extends StatelessWidget {
  const MidnightDancerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      child: MaterialApp(
        title: 'Midnight Dancer',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark,
        home: const SplashScreen(),
      ),
    );
  }
}
