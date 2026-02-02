import 'dart:typed_data';

import 'file_copy_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/file_copy_platform_io.dart'
    as file_copy;
import 'thumbnail_cache_stub.dart'
    if (dart.library.io) 'thumbnail_cache_io.dart'
    as disk;

/// Кэш миниатюр: память + диск (mobile). Для 200–500 элементов.
/// Лимит одновременных загрузок — 4, чтобы не перегружать UI.
class ThumbnailCache {
  ThumbnailCache._();

  static final ThumbnailCache instance = ThumbnailCache._();

  final Map<String, Uint8List> _memory = {};
  static const _maxMemoryEntries = 150;
  int _activeLoads = 0;
  static const _maxConcurrent = 4;
  Future<Uint8List?> get(String uriOrPath) async {
    if (uriOrPath.isEmpty) return null;
    final key = _key(uriOrPath);

    final cached = _memory[key];
    if (cached != null) return cached;

    while (_activeLoads >= _maxConcurrent) {
      await Future<void>.delayed(const Duration(milliseconds: 50));
    }
    _activeLoads++;
    try {
      final fromDisk = await disk.readThumbnail(key);
      if (fromDisk != null && fromDisk.isNotEmpty) {
        _putMemory(key, fromDisk);
        return fromDisk;
      }

      final generated = await file_copy.getVideoThumbnail(uriOrPath);
      if (generated != null && generated.isNotEmpty) {
        _putMemory(key, generated);
        disk.writeThumbnail(key, generated);
      }
      return generated;
    } finally {
      _activeLoads--;
    }
  }

  void _putMemory(String key, Uint8List bytes) {
    while (_memory.length >= _maxMemoryEntries && _memory.isNotEmpty) {
      _memory.remove(_memory.keys.first);
    }
    _memory[key] = bytes;
  }

  String _key(String uriOrPath) {
    return uriOrPath.hashCode.abs().toString();
  }

  void clear() => _memory.clear();
}
