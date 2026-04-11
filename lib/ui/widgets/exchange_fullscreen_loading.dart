import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/ui/widgets/exchange_loading_body.dart';

/// Полноэкранная загрузка поверх приложения (фон как у приложения, круг по центру).
class ExchangeFullscreenLoading extends StatelessWidget {
  const ExchangeFullscreenLoading({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: AppColors.background,
        body: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Material(
              color: AppColors.card,
              borderRadius: AppRadius.radiusMd,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 32),
                child: ExchangeLoadingBody(message: message),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
