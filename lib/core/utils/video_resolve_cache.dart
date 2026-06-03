import 'dart:typed_data';

import 'video_resolve_cache_stub.dart'
    if (dart.library.io) 'video_resolve_cache_io.dart'
    as impl;

/// Кэш путей к видео: один раз резолвим uri/id → локальный путь, дальше из памяти.
class VideoResolveCache {
  VideoResolveCache._();

  static final VideoResolveCache instance = VideoResolveCache._();

  final Map<String, String> _paths = {};
  final Map<String, Future<String?>> _inFlight = {};

  bool hasCached(String videoUri) => _paths.containsKey(videoUri);

  Future<String?> resolve(
    String videoUri,
    Future<String?> Function() loader,
  ) async {
    if (videoUri.isEmpty) return null;

    final hit = _paths[videoUri];
    if (hit != null && hit.isNotEmpty) return hit;

    final pending = _inFlight[videoUri];
    if (pending != null) return pending;

    final future = loader().then((path) {
      _inFlight.remove(videoUri);
      if (path != null && path.isNotEmpty) {
        _paths[videoUri] = path;
      }
      return path;
    });
    _inFlight[videoUri] = future;
    return future;
  }

  /// Для id в хранилище: сначала путь на диске (без загрузки в RAM), иначе temp.
  Future<String?> resolveForMoveVideo(
    String videoUri,
    Future<Uint8List?> Function(String id) loadBytes,
  ) {
    return resolve(videoUri, () => impl.resolvePath(videoUri, loadBytes));
  }

  void clear() {
    _paths.clear();
    _inFlight.clear();
  }
}
