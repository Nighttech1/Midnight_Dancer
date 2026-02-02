import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';

Future<String?> writeVideoTemp(Uint8List bytes) async {
  try {
    final dir = await getTemporaryDirectory();
    final path = '${dir.path}/vid_${DateTime.now().millisecondsSinceEpoch}.mp4';
    final file = File(path);
    await file.writeAsBytes(bytes);
    return path;
  } catch (_) {
    return null;
  }
}
