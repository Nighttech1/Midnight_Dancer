import 'dart:typed_data';

import 'package:cross_file/cross_file.dart';
import 'package:share_plus/share_plus.dart';

/// Web: передача из памяти (файловая система недоступна).
Future<void> shareChoreographyZipBytes(
  Uint8List zip,
  String fileName,
  String subject,
) async {
  await Share.shareXFiles(
    [
      XFile.fromData(
        zip,
        name: fileName,
        mimeType: 'application/zip',
      ),
    ],
    subject: subject,
  );
}
