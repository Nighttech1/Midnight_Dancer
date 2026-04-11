import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';

/// Заглушка VideoPreview для web — показывает плейсхолдер.
class VideoPreview extends ConsumerWidget {
  const VideoPreview({
    super.key,
    this.videoPath,
    this.initialSpeed = 1.0,
  });

  final String? videoPath;
  final double initialSpeed;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final str = ref.watch(appStringsProvider);
    if (videoPath == null || videoPath!.isEmpty) return const SizedBox.shrink();
    return Container(
      height: 180,
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(24),
      ),
      alignment: Alignment.center,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.videocam, size: 48, color: AppColors.textSecondary),
          const SizedBox(height: 8),
          Text(
            str.previewOnWeb,
            style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
          ),
        ],
      ),
    );
  }
}
