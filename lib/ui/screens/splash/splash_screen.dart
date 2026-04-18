import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/ui/navigation/main_scaffold.dart';

/// Стартовый экран при первом запуске. Показывается 4 секунды, круг плавно заполняется. При переключении вкладок (возврат в приложение) не показывается — пользователь остаётся на MainScaffold.
class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> with SingleTickerProviderStateMixin {
  bool _iconError = false;
  bool _navigated = false;

  static const _splashDuration = Duration(seconds: 4);

  late AnimationController _progressController;
  late Animation<double> _progressAnimation;

  @override
  void initState() {
    super.initState();
    ref.read(appDataNotifierProvider);
    _progressController = AnimationController(
      vsync: this,
      duration: _splashDuration,
    );
    _progressAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _progressController, curve: Curves.easeInOut),
    );
    _progressController.addStatusListener(_onProgressStatus);
    _preloadIcon();
    _progressController.forward();
  }

  void _onProgressStatus(AnimationStatus status) {
    if (status == AnimationStatus.completed && mounted && !_navigated) {
      _navigated = true;
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          pageBuilder: (_, __, ___) => const MainScaffold(),
          transitionsBuilder: (_, a, __, c) => FadeTransition(opacity: a, child: c),
          transitionDuration: const Duration(milliseconds: 200),
        ),
      );
    }
  }

  Future<void> _preloadIcon() async {
    try {
      await rootBundle.load('assets/icon.png');
    } catch (_) {
      if (mounted) setState(() => _iconError = true);
    }
  }

  @override
  void dispose() {
    _progressController.removeStatusListener(_onProgressStatus);
    _progressController.dispose();
    super.dispose();
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
              const Text(
                'Midnight Dancer',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  color: AppColors.accent,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Dance training app',
                style: TextStyle(
                  fontSize: 16,
                  color: AppColors.textSecondary.withValues(alpha: 0.95),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'by Nighttech',
                style: TextStyle(
                  fontSize: 14,
                  color: AppColors.textSecondary.withValues(alpha: 0.9),
                ),
              ),
              const SizedBox(height: 32),
              SizedBox(
                width: 56,
                height: 56,
                child: AnimatedBuilder(
                  animation: _progressAnimation,
                  builder: (context, child) {
                    return CircularProgressIndicator(
                      value: _progressAnimation.value,
                      strokeWidth: 4,
                      backgroundColor: AppColors.card,
                      valueColor: const AlwaysStoppedAnimation<Color>(AppColors.accent),
                    );
                  },
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
