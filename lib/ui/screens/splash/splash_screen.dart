import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/data/services/legal_terms_consent.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/ui/navigation/main_scaffold.dart';
import 'package:midnight_dancer/ui/widgets/legal_ru_en_links_rich_text.dart';

/// Стартовый экран: при первом запуске — согласие с документами; иначе короткий заставочный экран с переходом в приложение.
class SplashScreen extends ConsumerWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(appDataNotifierProvider);
    return async.when(
      loading: () => const _SplashBrandedShell(centerBody: _SplashLoadingBody()),
      error: (_, __) => const _SplashBrandedShell(centerBody: _SplashLoadingBody()),
      data: (data) {
        if (!LegalTermsConsent.fromSettings(data.settings)) {
          return const _LegalTermsFirstRunGate();
        }
        return const _SplashAutoAdvanceGate();
      },
    );
  }
}

class _SplashBrandedShell extends StatelessWidget {
  const _SplashBrandedShell({required this.centerBody});

  final Widget centerBody;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            const _SplashIconBlock(),
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
            centerBody,
          ],
        ),
      ),
    );
  }
}

class _SplashIconBlock extends StatefulWidget {
  const _SplashIconBlock();

  @override
  State<_SplashIconBlock> createState() => _SplashIconBlockState();
}

class _SplashIconBlockState extends State<_SplashIconBlock> {
  bool _iconError = false;

  @override
  void initState() {
    super.initState();
    _preloadIcon();
  }

  Future<void> _preloadIcon() async {
    try {
      await rootBundle.load('assets/icon.png');
    } catch (_) {
      if (mounted) setState(() => _iconError = true);
    }
  }

  @override
  Widget build(BuildContext context) {
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

class _SplashLoadingBody extends StatelessWidget {
  const _SplashLoadingBody();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: 56,
      height: 56,
      child: CircularProgressIndicator(
        strokeWidth: 4,
        backgroundColor: AppColors.card,
        valueColor: AlwaysStoppedAnimation<Color>(AppColors.accent),
      ),
    );
  }
}

/// Первый запуск: чекбокс и кнопка «Начать».
class _LegalTermsFirstRunGate extends ConsumerStatefulWidget {
  const _LegalTermsFirstRunGate();

  @override
  ConsumerState<_LegalTermsFirstRunGate> createState() => _LegalTermsFirstRunGateState();
}

class _LegalTermsFirstRunGateState extends ConsumerState<_LegalTermsFirstRunGate> {
  bool _accepted = false;

  Future<void> _onContinue() async {
    await ref.read(appDataNotifierProvider.notifier).saveLegalTermsAccepted();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (_, __, ___) => const MainScaffold(),
        transitionsBuilder: (_, a, __, c) => FadeTransition(opacity: a, child: c),
        transitionDuration: const Duration(milliseconds: 200),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 8),
              const _SplashIconBlock(),
              const SizedBox(height: 20),
              const Text(
                'Midnight Dancer',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                  color: AppColors.accent,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Dance training app',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 15,
                  color: AppColors.textSecondary.withValues(alpha: 0.95),
                ),
              ),
              const SizedBox(height: 24),
              Expanded(
                child: SingleChildScrollView(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Checkbox(
                          value: _accepted,
                          activeColor: AppColors.accent,
                          onChanged: (v) => setState(() => _accepted = v ?? false),
                        ),
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.only(top: 12, right: 8),
                            child: LegalRuEnLinksRichText(
                              leading: str.legalTermsCheckboxLeading,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              FilledButton(
                onPressed: _accepted ? _onContinue : null,
                style: FilledButton.styleFrom(
                  backgroundColor: AppColors.accent,
                  foregroundColor: Colors.white,
                  disabledBackgroundColor: AppColors.card,
                  disabledForegroundColor: Colors.white54,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
                child: Text(str.legalTermsContinue),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Повторный запуск: прежний таймер ~4 с и переход в приложение.
class _SplashAutoAdvanceGate extends StatefulWidget {
  const _SplashAutoAdvanceGate();

  @override
  State<_SplashAutoAdvanceGate> createState() => _SplashAutoAdvanceGateState();
}

class _SplashAutoAdvanceGateState extends State<_SplashAutoAdvanceGate>
    with SingleTickerProviderStateMixin {
  bool _navigated = false;
  static const _splashDuration = Duration(seconds: 4);

  late AnimationController _progressController;
  late Animation<double> _progressAnimation;

  @override
  void initState() {
    super.initState();
    _progressController = AnimationController(
      vsync: this,
      duration: _splashDuration,
    );
    _progressAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _progressController, curve: Curves.easeInOut),
    );
    _progressController.addStatusListener(_onProgressStatus);
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

  @override
  void dispose() {
    _progressController.removeStatusListener(_onProgressStatus);
    _progressController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _SplashBrandedShell(
      centerBody: SizedBox(
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
    );
  }
}
