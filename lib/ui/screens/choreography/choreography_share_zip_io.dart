import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' show Rect;

import 'package:cross_file/cross_file.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

/// Android/iOS/Desktop: ZIP на диск, затем share — не блокирует UI и стабильнее для Telegram и др.
Future<void> shareChoreographyZipBytes(
  Uint8List zip,
  String fileName,
  String subject, {
  Rect? sharePositionOrigin,
}) async {
  final dir = await getTemporaryDirectory();
  final path =
      '${dir.path}/md_choreo_${DateTime.now().millisecondsSinceEpoch}.zip';
  final f = File(path);
  await f.writeAsBytes(zip, flush: true);
  await Share.shareXFiles(
    [
      XFile(
        f.path,
        mimeType: 'application/zip',
        name: fileName,
      ),
    ],
    subject: subject,
    sharePositionOrigin: sharePositionOrigin,
  );
}
