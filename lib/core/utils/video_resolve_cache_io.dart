import 'dart:io';
import 'dart:typed_data';

import 'package:midnight_dancer/core/utils/video_temp_io.dart';
import 'package:midnight_dancer/data/services/storage_service.dart';

Future<String?> resolvePath(
  String videoUri,
  Future<Uint8List?> Function(String id) loadBytes,
) async {
  if (videoUri.startsWith('content:') ||
      videoUri.startsWith('/') ||
      videoUri.startsWith('file://')) {
    return videoUri;
  }

  final filePath =
      await StorageService.instance.getMediaFilePath(videoUri, 'video');
  if (filePath != null) {
    final file = File(filePath);
    if (await file.exists()) {
      return filePath;
    }
  }

  final bytes = await loadBytes(videoUri);
  if (bytes == null || bytes.isEmpty) return null;
  return writeVideoTemp(bytes, cacheKey: videoUri);
}
