import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';

/// Главный экран-заглушка. Оптимизирован для уменьшения лагов.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: Scaffold(
        backgroundColor: AppColors.background,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              Image.asset(
                'assets/icon.png',
                width: 120,
                height: 120,
                fit: BoxFit.contain,
                filterQuality: FilterQuality.medium,
                cacheWidth: 240,
                cacheHeight: 240,
              ),
              const SizedBox(height: 24),
              Text(
                'Midnight Dancer',
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  color: AppColors.accent,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Dance Training App',
                style: const TextStyle(
                  fontSize: 16,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'by Nighttech',
                style: const TextStyle(
                  fontSize: 14,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
