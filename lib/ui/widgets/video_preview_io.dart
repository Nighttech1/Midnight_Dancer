import 'dart:io';

import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:video_player/video_player.dart';

/// Превью видео (mobile) — принимает путь к файлу.
class VideoPreview extends StatefulWidget {
  const VideoPreview({
    super.key,
    this.videoPath,
    this.initialSpeed = 1.0,
  });

  final String? videoPath;
  final double initialSpeed;

  @override
  State<VideoPreview> createState() => _VideoPreviewState();
}

class _VideoPreviewState extends State<VideoPreview> {
  VideoPlayerController? _controller;
  double _speed = 1.0;
  bool _error = false;

  static const _speeds = [0.5, 0.75, 1.0, 1.5];

  @override
  void initState() {
    super.initState();
    _speed = widget.initialSpeed;
    _initController();
  }

  @override
  void didUpdateWidget(covariant VideoPreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.videoPath != widget.videoPath) {
      _disposeController();
      _error = false;
      _initController();
    }
  }

  void _listener() {
    if (mounted) setState(() {});
  }

  Future<void> _initController() async {
    final path = widget.videoPath;
    if (path == null || path.isEmpty) return;
    try {
      if (path.startsWith('content:')) {
        _controller = VideoPlayerController.contentUri(Uri.parse(path));
      } else {
        _controller = VideoPlayerController.file(File(path));
      }
      _controller!.addListener(_listener);
      await _controller!.initialize();
      await _controller!.setPlaybackSpeed(_speed);
      await _controller!.play();
      if (mounted) setState(() {});
    } catch (e) {
      _error = true;
      if (mounted) setState(() {});
    }
  }

  void _disposeController() {
    _controller?.removeListener(_listener);
    _controller?.dispose();
    _controller = null;
  }

  @override
  void dispose() {
    _disposeController();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_error) {
      return Container(
        height: 180,
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: AppRadius.radiusMd,
        ),
        alignment: Alignment.center,
        child: Text(
          'Ошибка загрузки видео',
          style: TextStyle(color: AppColors.textSecondary),
        ),
      );
    }
    if (_controller == null || !_controller!.value.isInitialized) {
      if (widget.videoPath == null || widget.videoPath!.isEmpty) {
        return const SizedBox.shrink();
      }
      return Container(
        height: 180,
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: AppRadius.radiusMd,
        ),
        alignment: Alignment.center,
        child: const CircularProgressIndicator(color: AppColors.accent),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: AppRadius.radiusMd,
          child: AspectRatio(
            aspectRatio: _controller!.value.aspectRatio,
            child: GestureDetector(
              onTap: () async {
                if (_controller!.value.isPlaying) {
                  await _controller!.pause();
                } else {
                  await _controller!.play();
                }
                if (mounted) setState(() {});
              },
              child: Stack(
                alignment: Alignment.center,
                children: [
                  VideoPlayer(_controller!),
                  if (!_controller!.value.isPlaying)
                    Container(
                      color: Colors.black26,
                      child: const Icon(Icons.play_arrow, size: 64, color: Colors.white),
                    ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Скорость',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textSecondary,
                ),
              ),
              Row(
                children: _speeds.map((s) {
                  final active = (_speed - s).abs() < 0.01;
                  return Padding(
                    padding: const EdgeInsets.only(left: 6),
                    child: GestureDetector(
                      onTap: () async {
                        _speed = s;
                        await _controller?.setPlaybackSpeed(s);
                        if (mounted) setState(() {});
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: active ? AppColors.accent : AppColors.card,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          '${s}x',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: active ? Colors.white : AppColors.textSecondary,
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
