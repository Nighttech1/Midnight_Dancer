import 'dart:io';
import 'dart:typed_data';

Future<Uint8List?> readFile(String path) async {
  final f = File(path);
  if (!await f.exists()) return null;
  return f.readAsBytes();
}

Future<void> writeFile(String path, Uint8List bytes) async {
  final f = File(path);
  await f.create(recursive: true);
  await f.writeAsBytes(bytes);
}

Future<void> deleteFile(String path) async {
  final f = File(path);
  if (await f.exists()) await f.delete();
}

Future<void> ensureDir(String path) async {
  final d = Directory(path);
  if (!await d.exists()) await d.create(recursive: true);
}

Future<bool> fileExists(String path) async {
  return File(path).exists();
}

Future<void> copyFile(String sourcePath, String destPath) async {
  final src = File(sourcePath);
  if (!await src.exists()) throw Exception('Source file not found');
  await src.copy(destPath);
}

Future<void> deleteDirRecursive(String path) async {
  final d = Directory(path);
  if (await d.exists()) await d.delete(recursive: true);
}
