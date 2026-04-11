import 'dart:typed_data';

/// Результат сохранения архива в папку загрузок (или аналог).
class FullBackupSaveResult {
  const FullBackupSaveResult({
    required this.folderPath,
    required this.fileName,
  });

  /// Полный путь к каталогу (например …/Download).
  final String folderPath;

  /// Имя сохранённого файла.
  final String fileName;
}

/// Web и прочие платформы без реализации — null.
Future<FullBackupSaveResult?> saveFullBackupZipToDownloads(
  Uint8List zipBytes,
  String fileName,
) async =>
    null;
