import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';

/// Web/stub: без нативного видеоплеера — постер и те же оверлеи (ползунок неактивен).
class MoveCardVideo extends StatelessWidget {
  const MoveCardVideo({
    super.key,
    required this.videoPathOrUri,
    this.thumbnailBytes,
    required this.levelLabel,
    required this.masteryPercent,
    required this.topRight,
    required this.onEdit,
    this.onError,
    this.showVideoTimeline = false,
  });

  final String videoPathOrUri;
  final Uint8List? thumbnailBytes;
  final String levelLabel;
  final int masteryPercent;
  final Widget topRight;
  final VoidCallback onEdit;
  final VoidCallback? onError;
  final bool showVideoTimeline;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        GestureDetector(
          onTap: onEdit,
          child: ColoredBox(
            color: AppColors.background,
            child: thumbnailBytes != null && thumbnailBytes!.isNotEmpty
                ? Image.memory(
                    thumbnailBytes!,
                    fit: BoxFit.cover,
                    width: double.infinity,
                    height: double.infinity,
                  )
                : const Center(
                    child: Icon(Icons.videocam, color: Colors.white24, size: 40),
                  ),
          ),
        ),
        Positioned(
          top: 10,
          left: 10,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.accent.withOpacity(0.35)),
                ),
                child: Text(
                  levelLabel,
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: AppColors.accent,
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.white24),
                ),
                child: Text(
                  '${masteryPercent.clamp(0, 100)}%',
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
              ),
            ],
          ),
        ),
        Positioned(top: 10, right: 10, child: topRight),
        if (showVideoTimeline)
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.fromLTRB(8, 12, 8, 6),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.transparent,
                    Colors.black.withOpacity(0.85),
                  ],
                ),
              ),
              child: Row(
                children: [
                  Icon(Icons.play_arrow, color: Colors.white54, size: 24),
                  Expanded(
                    child: SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        trackHeight: 3,
                        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                      ),
                      child: Slider(
                        value: 0,
                        onChanged: null,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}
