import 'dart:io';

import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class MoveCardVideo extends StatefulWidget {
  const MoveCardVideo({
    super.key,
    required this.videoPathOrUri,
    required this.onEdit,
    this.onError,
  });

  final String videoPathOrUri;
  final VoidCallback onEdit;
  final VoidCallback? onError;

  @override
  State<MoveCardVideo> createState() => _MoveCardVideoState();
}

class _MoveCardVideoState extends State<MoveCardVideo> {
  VideoPlayerController? _controller;
  bool _hover = false;
  bool _initError = false;
  bool _initializing = false;

  @override
  void didUpdateWidget(covariant MoveCardVideo oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.videoPathOrUri != widget.videoPathOrUri) {
      _controller?.dispose();
      _controller = null;
      _initError = false;
      _initializing = false;
    }
  }

  Future<void> _init() async {
    if (_controller != null || _initializing || _initError) return;
    _initializing = true;
    if (mounted) setState(() {});
    try {
      if (widget.videoPathOrUri.startsWith('content:')) {
        _controller = VideoPlayerController.contentUri(Uri.parse(widget.videoPathOrUri));
      } else {
        _controller = VideoPlayerController.file(File(widget.videoPathOrUri));
      }
      await _controller!.initialize();
      await _controller!.setLooping(true);
      await _controller!.setVolume(0);
      if (mounted) setState(() {});
    } catch (_) {
      if (mounted) {
        setState(() {
          _initError = true;
          _initializing = false;
        });
        widget.onError?.call();
      }
      return;
    }
    if (mounted) setState(() => _initializing = false);
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  void _onHover(bool enter) {
    setState(() => _hover = enter);
    if (enter) {
      _init().then((_) {
        if (_controller != null && mounted) _controller?.play();
      });
    } else {
      _controller?.pause();
      _controller?.seekTo(Duration.zero);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ready = _controller != null && _controller!.value.isInitialized;
    return MouseRegion(
      onEnter: (_) => _onHover(true),
      onExit: (_) => _onHover(false),
      child: GestureDetector(
        onTap: widget.onEdit,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (_initError)
              const Center(child: Icon(Icons.videocam_off, color: Colors.white38, size: 48))
            else if (_initializing || !ready)
              Center(
                child: Icon(Icons.play_circle_outline, color: Colors.white54, size: 48),
              )
            else
              LayoutBuilder(
                builder: (_, constraints) {
                  final size = _controller!.value.size;
                  final w = size.width > 0 ? size.width : 16.0;
                  final h = size.height > 0 ? size.height : 9.0;
                  return FittedBox(
                    fit: BoxFit.cover,
                    alignment: Alignment.center,
                    child: SizedBox(
                      width: w,
                      height: h,
                      child: VideoPlayer(_controller!),
                    ),
                  );
                },
              ),
            if (_hover && ready)
              Container(
                color: Colors.black26,
                child: const Center(
                  child: Icon(Icons.edit, color: Colors.white70, size: 48),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
