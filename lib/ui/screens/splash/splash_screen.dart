import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/ui/navigation/main_scaffold.dart';

/// Splash screen с иконкой приложения. Показывается при загрузке.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  bool _iconError = false;

  @override
  void initState() {
    super.initState();
    _precacheIcon();
    _navigateAfterDelay();
  }

  Future<void> _precacheIcon() async {
    try {
      await rootBundle.load('assets/icon.png');
    } catch (_) {
      if (mounted) setState(() => _iconError = true);
    }
  }

  Future<void> _navigateAfterDelay() async {
    await Future<void>.delayed(const Duration(milliseconds: 1200));
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (_, __, ___) => const MainScaffold(),
        transitionsBuilder: (_, a, __, c) =>
            FadeTransition(opacity: a, child: c),
        transitionDuration: const Duration(milliseconds: 200),
      ),
    );
  }

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
              _buildIcon(),
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
              const Text(
                'Dance Training App',
                style: TextStyle(
                  fontSize: 16,
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 4),
              const Text(
                'by Nighttech',
                style: TextStyle(
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

  Widget _buildIcon() {
    if (_iconError) {
      return Icon(Icons.music_note, size: 80, color: AppColors.accent);
    }
    return Image.asset(
      'assets/icon.png',
      width: 120,
      height: 120,
      fit: BoxFit.contain,
      filterQuality: FilterQuality.medium,
      cacheWidth: 240,
      cacheHeight: 240,
      gaplessPlayback: true,
      errorBuilder: (_, __, ___) => Icon(
        Icons.music_note,
        size: 80,
        color: AppColors.accent,
      ),
    );
  }
}
