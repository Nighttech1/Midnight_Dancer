import 'dart:typed_data';
import 'dart:ui' show Rect;

import 'package:cross_file/cross_file.dart';
import 'package:share_plus/share_plus.dart';

/// Web: передача из памяти (файловая система недоступна).
Future<void> shareChoreographyZipBytes(
  Uint8List zip,
  String fileName,
  String subject, {
  Rect? sharePositionOrigin,
}) async {
  await Share.shareXFiles(
    [
      XFile.fromData(
        zip,
        name: fileName,
        mimeType: 'application/zip',
      ),
    ],
    subject: subject,
    sharePositionOrigin: sharePositionOrigin,
  );
}
