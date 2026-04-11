import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/ui/widgets/exchange_loading_body.dart';

/// Окно «данные выгружаются / загружаются» с кругом в стиле сплэша (плавное заполнение по кругу).
class ExchangeDataProgressDialog extends StatelessWidget {
  const ExchangeDataProgressDialog({
    super.key,
    required this.message,
    this.cycleDuration = const Duration(milliseconds: 2200),
  });

  final String message;
  final Duration cycleDuration;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.card,
      content: ExchangeLoadingBody(message: message, cycleDuration: cycleDuration),
    );
  }
}
