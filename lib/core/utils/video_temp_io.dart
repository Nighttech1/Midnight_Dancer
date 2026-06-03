import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';

final Map<String, String> _tempPathByKey = {};

Future<String?> writeVideoTemp(Uint8List bytes, {String? cacheKey}) async {
  try {
    if (cacheKey != null && cacheKey.isNotEmpty) {
      final existing = _tempPathByKey[cacheKey];
      if (existing != null) {
        final f = File(existing);
        if (await f.exists()) return existing;
      }
    }
    final dir = await getTemporaryDirectory();
    final safeKey = cacheKey != null && cacheKey.isNotEmpty
        ? cacheKey.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_')
        : '${DateTime.now().millisecondsSinceEpoch}';
    final path = '${dir.path}/vid_$safeKey.mp4';
    final file = File(path);
    await file.writeAsBytes(bytes);
    if (cacheKey != null && cacheKey.isNotEmpty) {
      _tempPathByKey[cacheKey] = path;
    }
    return path;
  } catch (_) {
    return null;
  }
}
