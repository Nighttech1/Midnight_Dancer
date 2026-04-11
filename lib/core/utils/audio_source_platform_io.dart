import 'dart:io';
import 'dart:typed_data';

import 'package:just_audio/just_audio.dart';

AudioSource createFileAudioSource(String path) {
  return AudioSource.file(path);
}

Future<int> getFileSizeFromPath(String path) async {
  try {
    final f = File(path);
    if (await f.exists()) return await f.length();
  } catch (_) {}
  return 0;
}

Future<Uint8List?> readFileAsBytes(String path) async {
  try {
    final f = File(path);
    if (await f.exists()) return await f.readAsBytes();
  } catch (_) {}
  return null;
}
