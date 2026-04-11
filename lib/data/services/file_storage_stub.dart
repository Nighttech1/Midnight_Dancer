import 'dart:typed_data';

/// Заглушка для web: file I/O недоступен.
Future<Uint8List?> readFile(String path) async => null;
Future<void> writeFile(String path, Uint8List bytes) async {}
Future<void> deleteFile(String path) async {}
Future<void> ensureDir(String path) async {}
Future<bool> fileExists(String path) async => false;
Future<void> copyFile(String sourcePath, String destPath) async {}

Future<void> deleteDirRecursive(String path) async {}
