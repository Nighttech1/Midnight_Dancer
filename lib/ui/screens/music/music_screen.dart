import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';

/// Экран «Музыка» — заглушка.
class MusicScreen extends StatelessWidget {
  const MusicScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.music_note, size: 48, color: AppColors.accent),
            const SizedBox(height: 16),
            Text(
              'Музыка',
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
