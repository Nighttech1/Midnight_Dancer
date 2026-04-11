import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:video_player/video_player.dart';

/// Превью видео (mobile) — принимает путь к файлу.
class VideoPreview extends ConsumerStatefulWidget {
  const VideoPreview({
    super.key,
    this.videoPath,
    this.initialSpeed = 1.0,
  });

  final String? videoPath;
  final double initialSpeed;

  @override
  ConsumerState<VideoPreview> createState() => _VideoPreviewState();
}

class _VideoPreviewState extends ConsumerState<VideoPreview> {
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

  double get _progress {
    final c = _controller;
    if (c == null || !c.value.isInitialized) return 0;
    final d = c.value.duration;
    if (d.inMilliseconds <= 0) return 0;
    return (c.value.position.inMilliseconds / d.inMilliseconds).clamp(0.0, 1.0);
  }

  Future<void> _seekTo(double fraction) async {
    final c = _controller;
    if (c == null || !c.value.isInitialized) return;
    final d = c.value.duration;
    if (d.inMilliseconds <= 0) return;
    await c.seekTo(Duration(milliseconds: (fraction * d.inMilliseconds).round()));
  }

  Future<void> _togglePlay() async {
    final c = _controller;
    if (c == null || !c.value.isInitialized) return;
    if (c.value.isPlaying) {
      await c.pause();
    } else {
      await c.play();
    }
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
    final str = ref.watch(appStringsProvider);
    if (_error) {
      return Container(
        height: 180,
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: AppRadius.radiusMd,
        ),
        alignment: Alignment.center,
        child: Text(
          str.videoLoadError,
          style: const TextStyle(color: AppColors.textSecondary),
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
              onTap: _togglePlay,
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
        const SizedBox(height: 10),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              IconButton(
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                icon: Icon(
                  _controller!.value.isPlaying ? Icons.pause : Icons.play_arrow,
                  color: Colors.white,
                  size: 28,
                ),
                onPressed: _togglePlay,
              ),
              Expanded(
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    trackHeight: 4,
                    thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
                    overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
                    activeTrackColor: AppColors.accent,
                    inactiveTrackColor: Colors.white24,
                    thumbColor: Colors.white,
                    overlayColor: AppColors.accent.withOpacity(0.25),
                  ),
                  child: Slider(
                    value: _progress,
                    onChanged: (v) {
                      setState(() {});
                      _seekTo(v);
                    },
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                str.speedLabel,
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
