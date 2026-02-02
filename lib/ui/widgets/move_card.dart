import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/file_copy_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/file_copy_platform_io.dart'
    as file_copy;
import 'package:midnight_dancer/core/utils/thumbnail_cache.dart';
import 'package:midnight_dancer/core/utils/video_temp.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/video_temp_io.dart'
    as video_temp;
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';

const _levelLabels = {
  'Beginner': 'Начинающий',
  'Intermediate': 'Средний',
  'Advanced': 'Профи',
};

String _levelLabel(String level) =>
    _levelLabels[level] ?? level;

class MoveCard extends ConsumerStatefulWidget {
  const MoveCard({
    super.key,
    required this.move,
    required this.styleId,
    required this.onEdit,
    required this.onDelete,
    this.onVideoUnavailable,
  });

  final Move move;
  final String styleId;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final VoidCallback? onVideoUnavailable;

  @override
  ConsumerState<MoveCard> createState() => _MoveCardState();
}

class _MoveCardState extends ConsumerState<MoveCard> {
  String? _videoPathOrUri;
  Uint8List? _thumbnailBytes;
  bool _loading = true;
  bool _error = false;

  @override
  void initState() {
    super.initState();
    _resolveVideo();
  }

  @override
  void didUpdateWidget(covariant MoveCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.move.videoUri != widget.move.videoUri) {
      _resolveVideo();
    }
  }

  Future<void> _resolveVideo() async {
    final uri = widget.move.videoUri;
    if (uri == null || uri.isEmpty) {
      if (mounted) setState(() {
        _loading = false;
        _videoPathOrUri = null;
        _thumbnailBytes = null;
        _error = false;
      });
      return;
    }
    if (uri.startsWith('content:') || uri.startsWith('/')) {
      _videoPathOrUri = uri;
      final bytes = await ThumbnailCache.instance.get(uri);
      if (!mounted) return;
      if (mounted) setState(() {
        _thumbnailBytes = bytes;
        _loading = false;
        _error = false;
      });
      return;
    }
    final notifier = ref.read(appDataNotifierProvider.notifier);
    final bytes = await notifier.loadVideo(uri) as Uint8List?;
    if (!mounted) return;
    if (bytes == null || bytes.isEmpty) {
      if (mounted) setState(() {
        _loading = false;
        _videoPathOrUri = null;
        _thumbnailBytes = null;
        _error = true;
      });
      return;
    }
    final path = await video_temp.writeVideoTemp(bytes);
    if (!mounted) return;
    final thumb = await ThumbnailCache.instance.get(path ?? uri);
    if (!mounted) return;
    if (mounted) {
      setState(() {
        _videoPathOrUri = path ?? uri;
        _thumbnailBytes = thumb;
        _loading = false;
        _error = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: widget.onEdit,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: AppRadius.radiusXl,
          border: Border.all(color: AppColors.cardBorder),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          fit: StackFit.expand,
          children: [
            _buildPreview(),
            Positioned(
              top: 8,
              left: 8,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.accent.withOpacity(0.3)),
                ),
                child: Text(
                  _levelLabel(widget.move.level),
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: AppColors.accent,
                  ),
                ),
              ),
            ),
            Positioned(
              top: 8,
              right: 8,
              child: GestureDetector(
                onTap: widget.onDelete,
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.close,
                    size: 16,
                    color: Colors.white70,
                  ),
                ),
              ),
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.transparent, Colors.black87],
                  ),
                ),
                child: Text(
                  widget.move.name,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPreview() {
    if (_loading) {
      return Container(
        color: AppColors.background,
        child: const Center(
          child: CircularProgressIndicator(color: AppColors.accent),
        ),
      );
    }
    if (_thumbnailBytes != null && _thumbnailBytes!.isNotEmpty) {
      return Image.memory(
        _thumbnailBytes!,
        fit: BoxFit.cover,
        cacheWidth: 512,
        cacheHeight: 512,
      );
    }
    return Container(
      color: AppColors.background,
      child: Center(
        child: Icon(Icons.videocam, color: Colors.white24, size: 48),
      ),
    );
  }
}
