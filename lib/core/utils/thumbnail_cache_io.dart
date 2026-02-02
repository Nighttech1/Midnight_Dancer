import 'dart:io';
import 'dart:typed_data';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

Future<Uint8List?> readThumbnail(String key) async {
  try {
    final dir = await getTemporaryDirectory();
    final path = p.join(dir.path, 'thumbnails', '$key.jpg');
    final file = File(path);
    if (await file.exists()) return await file.readAsBytes();
  } catch (_) {}
  return null;
}

Future<void> writeThumbnail(String key, Uint8List bytes) async {
  try {
    final dir = await getTemporaryDirectory();
    final thumbDir = Directory(p.join(dir.path, 'thumbnails'));
    if (!await thumbDir.exists()) await thumbDir.create(recursive: true);
    final file = File(p.join(thumbDir.path, '$key.jpg'));
    await file.writeAsBytes(bytes);
  } catch (_) {}
}
