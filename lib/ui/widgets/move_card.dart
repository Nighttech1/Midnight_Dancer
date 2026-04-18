import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/thumbnail_cache.dart';
import 'package:midnight_dancer/core/utils/video_temp.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/video_temp_io.dart'
    as video_temp;
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/ui/widgets/move_card_video.dart';

class MoveCard extends ConsumerStatefulWidget {
  const MoveCard({
    super.key,
    required this.move,
    required this.isCurrent,
    required this.onEdit,
    required this.onDelete,
    required this.onToggleCurrent,
    this.onVideoUnavailable,
  });

  final Move move;
  final bool isCurrent;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final VoidCallback onToggleCurrent;
  final VoidCallback? onVideoUnavailable;

  @override
  ConsumerState<MoveCard> createState() => _MoveCardState();
}

class _MoveCardState extends ConsumerState<MoveCard> {
  String? _videoPathOrUri;
  Uint8List? _thumbnailBytes;
  bool _loading = true;
  bool _error = false;

  static const Color _neonOrange = Color(0xFFFF6B35);

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
      if (mounted) {
        setState(() {
          _loading = false;
          _videoPathOrUri = null;
          _thumbnailBytes = null;
          _error = false;
        });
      }
      return;
    }
    if (uri.startsWith('content:') || uri.startsWith('/')) {
      _videoPathOrUri = uri;
      final bytes = await ThumbnailCache.instance.get(uri);
      if (!mounted) return;
      if (mounted) {
        setState(() {
          _thumbnailBytes = bytes;
          _loading = false;
          _error = false;
        });
      }
      return;
    }
    final notifier = ref.read(appDataNotifierProvider.notifier);
    final bytes = await notifier.loadVideo(uri);
    if (!mounted) return;
    if (bytes == null || bytes.isEmpty) {
      if (mounted) {
        setState(() {
          _loading = false;
          _videoPathOrUri = null;
          _thumbnailBytes = null;
          _error = true;
        });
      }
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

  Widget _topRightActions() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Material(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(8),
          child: InkWell(
            onTap: widget.onToggleCurrent,
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.all(6),
              child: Icon(
                widget.isCurrent ? Icons.flag : Icons.flag_outlined,
                size: 16,
                color: widget.isCurrent ? _neonOrange : Colors.white70,
              ),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Material(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(8),
          child: InkWell(
            onTap: widget.onEdit,
            borderRadius: BorderRadius.circular(8),
            child: const Padding(
              padding: EdgeInsets.all(6),
              child: Icon(Icons.edit, size: 16, color: Colors.white70),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Material(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(8),
          child: InkWell(
            onTap: widget.onDelete,
            borderRadius: BorderRadius.circular(8),
            child: const Padding(
              padding: EdgeInsets.all(6),
              child: Icon(Icons.close, size: 16, color: Colors.white70),
            ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    final hasVideo = _videoPathOrUri != null &&
        _videoPathOrUri!.isNotEmpty &&
        !_error;

    final inner = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: hasVideo
              ? MoveCardVideo(
                  videoPathOrUri: _videoPathOrUri!,
                  thumbnailBytes: _thumbnailBytes,
                  levelLabel: str.levelLabelFor(widget.move.level),
                  masteryPercent: widget.move.masteryPercent,
                  topRight: _topRightActions(),
                  onEdit: widget.onEdit,
                  onError: widget.onVideoUnavailable,
                )
              : _buildStaticPreview(),
        ),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
          color: Colors.black,
          child: Text(
            widget.move.name,
            textAlign: TextAlign.center,
            maxLines: 4,
            softWrap: true,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: Colors.white,
              height: 1.25,
            ),
          ),
        ),
      ],
    );

    return Stack(
      clipBehavior: Clip.none,
      children: [
        ClipRRect(
          borderRadius: AppRadius.radiusXl,
          child: ColoredBox(
            color: AppColors.card,
            child: inner,
          ),
        ),
        Positioned.fill(
          child: IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: AppRadius.radiusXl,
                border: Border.all(
                  color: widget.isCurrent ? _neonOrange : AppColors.cardBorder,
                  width: widget.isCurrent ? 2.5 : 1,
                ),
                boxShadow: widget.isCurrent
                    ? [
                        BoxShadow(
                          color: _neonOrange.withOpacity(0.5),
                          blurRadius: 14,
                          spreadRadius: 0,
                          offset: Offset.zero,
                        ),
                      ]
                    : null,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildStaticPreview() {
    final str = ref.watch(appStringsProvider);
    return Stack(
      fit: StackFit.expand,
      children: [
        if (_loading)
          const ColoredBox(
            color: AppColors.background,
            child: Center(
              child: CircularProgressIndicator(color: AppColors.accent),
            ),
          )
        else if (_thumbnailBytes != null && _thumbnailBytes!.isNotEmpty)
          Image.memory(
            _thumbnailBytes!,
            fit: BoxFit.cover,
            cacheWidth: 512,
            cacheHeight: 512,
          )
        else
          ColoredBox(
            color: AppColors.background,
            child: Center(
              child: Icon(Icons.videocam, color: Colors.white24, size: 40),
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
                  str.levelLabelFor(widget.move.level),
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
                  '${widget.move.masteryPercent.clamp(0, 100)}%',
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
        Positioned(top: 10, right: 10, child: _topRightActions()),
      ],
    );
  }
}
