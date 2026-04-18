import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:video_player/video_player.dart';

/// Видео в карточке элемента: воспроизведение, пауза и ползунок позиции снизу.
class MoveCardVideo extends StatefulWidget {
  const MoveCardVideo({
    super.key,
    required this.videoPathOrUri,
    this.thumbnailBytes,
    required this.levelLabel,
    required this.masteryPercent,
    required this.topRight,
    required this.onEdit,
    this.onError,
    /// На лицевой стороне карточки в сетке — без полосы перемотки.
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
  State<MoveCardVideo> createState() => _MoveCardVideoState();
}

class _MoveCardVideoState extends State<MoveCardVideo> {
  VideoPlayerController? _controller;
  bool _initError = false;
  bool _initializing = false;

  void _tick() {
    if (mounted) setState(() {});
  }

  Future<void> _init() async {
    if (_controller != null || _initializing || widget.videoPathOrUri.isEmpty) return;
    _initializing = true;
    if (mounted) setState(() {});
    try {
      final path = widget.videoPathOrUri;
      if (path.startsWith('content:')) {
        _controller = VideoPlayerController.contentUri(Uri.parse(path));
      } else {
        _controller = VideoPlayerController.file(File(path));
      }
      _controller!.addListener(_tick);
      await _controller!.initialize();
      await _controller!.setVolume(0);
      await _controller!.setLooping(false);
      await _controller!.pause();
    } catch (e, _) {
      _initError = true;
      widget.onError?.call();
    } finally {
      _initializing = false;
    }
    if (mounted) setState(() {});
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _init());
  }

  @override
  void didUpdateWidget(covariant MoveCardVideo oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.videoPathOrUri != widget.videoPathOrUri) {
      _controller?.removeListener(_tick);
      _controller?.dispose();
      _controller = null;
      _initError = false;
      _initializing = false;
      _init();
    }
  }

  @override
  void dispose() {
    _controller?.removeListener(_tick);
    _controller?.dispose();
    super.dispose();
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

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        _buildVideoLayer(),
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
                  widget.levelLabel,
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
                  '${widget.masteryPercent.clamp(0, 100)}%',
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
        Positioned(top: 10, right: 10, child: widget.topRight),
        if (widget.showVideoTimeline &&
            _controller != null &&
            _controller!.value.isInitialized)
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.fromLTRB(2, 16, 2, 4),
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
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  IconButton(
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
                    icon: Icon(
                      _controller!.value.isPlaying ? Icons.pause : Icons.play_arrow,
                      color: Colors.white,
                      size: 26,
                    ),
                    onPressed: _togglePlay,
                  ),
                  Expanded(
                    child: SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        trackHeight: 3,
                        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
                        overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
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
          ),
      ],
    );
  }

  Widget _buildVideoLayer() {
    if (_initError) {
      return ColoredBox(
        color: AppColors.background,
        child: Center(
          child: Icon(Icons.videocam_off, color: Colors.white38, size: 40),
        ),
      );
    }
    if (_initializing || _controller == null || !_controller!.value.isInitialized) {
      if (widget.thumbnailBytes != null && widget.thumbnailBytes!.isNotEmpty) {
        return Image.memory(
          widget.thumbnailBytes!,
          fit: BoxFit.cover,
          width: double.infinity,
          height: double.infinity,
          cacheWidth: 512,
          cacheHeight: 512,
        );
      }
      return ColoredBox(
        color: AppColors.background,
        child: const Center(
          child: SizedBox(
            width: 28,
            height: 28,
            child: CircularProgressIndicator(
              color: AppColors.accent,
              strokeWidth: 2,
            ),
          ),
        ),
      );
    }
    return FittedBox(
      fit: BoxFit.cover,
      clipBehavior: Clip.hardEdge,
      child: SizedBox(
        width: _controller!.value.size.width,
        height: _controller!.value.size.height,
        child: GestureDetector(
          onTap: _togglePlay,
          child: VideoPlayer(_controller!),
        ),
      ),
    );
  }
}
