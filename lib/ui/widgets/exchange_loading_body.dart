import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';

/// Текст + заполняющийся по кругу индикатор (как на сплэше). Используется в диалоге и на полноэкранной загрузке.
class ExchangeLoadingBody extends StatefulWidget {
  const ExchangeLoadingBody({
    super.key,
    required this.message,
    this.cycleDuration = const Duration(milliseconds: 2200),
  });

  final String message;
  final Duration cycleDuration;

  @override
  State<ExchangeLoadingBody> createState() => _ExchangeLoadingBodyState();
}

class _ExchangeLoadingBodyState extends State<ExchangeLoadingBody>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _progress;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: widget.cycleDuration)..repeat();
    _progress = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          widget.message,
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 16,
            height: 1.35,
            fontWeight: FontWeight.w500,
          ),
        ),
        const SizedBox(height: 28),
        SizedBox(
          width: 56,
          height: 56,
          child: AnimatedBuilder(
            animation: _progress,
            builder: (context, child) {
              return CircularProgressIndicator(
                value: _progress.value,
                strokeWidth: 4,
                backgroundColor: AppColors.card,
                valueColor: const AlwaysStoppedAnimation<Color>(AppColors.accent),
              );
            },
          ),
        ),
      ],
    );
  }
}
