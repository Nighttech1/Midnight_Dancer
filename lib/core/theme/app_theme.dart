import 'package:flutter/material.dart';

/// Цвета приложения. Константы вместо Colors.orange.shade400 — меньше лагов.
class AppColors {
  AppColors._();

  static const Color background = Color(0xFF0f172a);
  static const Color accent = Color(0xFFf97316);
  static const Color textSecondary = Color(0xFF94a3b8);
  static const Color card = Color(0xFF1e293b);
  static const Color cardBorder = Color(0xFF334155);
}

/// Радиусы скругления (24–40px)
class AppRadius {
  AppRadius._();

  static const double sm = 16;
  static const double md = 24;
  static const double lg = 32;
  static const double xl = 40;

  static BorderRadius get radiusSm => BorderRadius.circular(sm);
  static BorderRadius get radiusMd => BorderRadius.circular(md);
  static BorderRadius get radiusLg => BorderRadius.circular(lg);
  static BorderRadius get radiusXl => BorderRadius.circular(xl);
}

/// Тема приложения Midnight Dancer
class AppTheme {
  AppTheme._();

  static ThemeData get dark => ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppColors.background,
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppColors.accent,
          brightness: Brightness.dark,
          primary: AppColors.accent,
          surface: AppColors.background,
        ),
        textTheme: ThemeData.dark().textTheme.apply(fontFamily: 'Inter'),
        cardTheme: CardThemeData(
          color: AppColors.card,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: AppRadius.radiusMd,
            side: const BorderSide(color: AppColors.cardBorder, width: 1),
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.accent,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: AppRadius.radiusMd,
            ),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: AppColors.card,
          border: OutlineInputBorder(borderRadius: AppRadius.radiusMd),
          enabledBorder: OutlineInputBorder(
            borderRadius: AppRadius.radiusMd,
            borderSide: const BorderSide(color: AppColors.cardBorder),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: AppRadius.radiusMd,
            borderSide: const BorderSide(color: AppColors.accent, width: 2),
          ),
        ),
      );
}
