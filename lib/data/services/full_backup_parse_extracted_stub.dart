import 'package:midnight_dancer/data/services/full_backup_service.dart';

/// Web / non-IO: directory parsing is not available.
FullBackupParseResult fullBackupParseExtractedDirectory(dynamic _) {
  return FullBackupParseResult.error('extracted_dir_not_supported');
}
