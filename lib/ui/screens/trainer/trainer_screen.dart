import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';

/// Экран «Тренировка» — заглушка.
class TrainerScreen extends StatelessWidget {
  const TrainerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.bolt, size: 48, color: AppColors.accent),
            const SizedBox(height: 16),
            Text(
              'Тренировка',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: AppColors.accent,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
