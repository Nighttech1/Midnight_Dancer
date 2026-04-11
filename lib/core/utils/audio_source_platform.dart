import 'dart:typed_data';

import 'package:just_audio/just_audio.dart';

/// Stub: file source not supported on web.
AudioSource createFileAudioSource(String path) {
  throw UnsupportedError('File audio source not available on web');
}

/// Stub: file size not available on web.
Future<int> getFileSizeFromPath(String path) async => 0;

/// Stub: read file as bytes not available on web.
Future<Uint8List?> readFileAsBytes(String path) async => null;
