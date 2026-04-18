import 'dart:io';

import 'package:path/path.dart' as p;

/// Recursively deletes [dir] if it exists. Ignores errors (best-effort cleanup).
Future<void> wipeDirectorySilently(Directory? dir) async {
  if (dir == null) return;
  try {
    if (await dir.exists()) {
      await dir.delete(recursive: true);
    }
  } catch (_) {}
}

/// Copies every file under [src] into [dst] preserving relative paths.
Future<void> copyDirectoryContentsTo(Directory src, Directory dst) async {
  await for (final entity in src.list(recursive: true)) {
    if (entity is! File) continue;
    final rel = p.relative(entity.path, from: src.path);
    final out = File(p.join(dst.path, rel));
    await out.parent.create(recursive: true);
    await entity.copy(out.path);
  }
}
