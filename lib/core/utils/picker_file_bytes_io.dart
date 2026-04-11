import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';

/// Дополняет bytes, читая файл по пути (Android/Desktop и т.д.).
Future<Uint8List?> completePickerFileBytes(PlatformFile f) async {
  if (f.bytes != null && f.bytes!.isNotEmpty) return f.bytes;
  final p = f.path;
  if (p == null) return null;
  try {
    return await File(p).readAsBytes();
  } catch (_) {
    return null;
  }
}
