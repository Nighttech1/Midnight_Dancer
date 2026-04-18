import 'dart:convert';
import 'dart:io';

import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:path/path.dart' as p;

import 'secure_zip_import_exception.dart';

/// STEP 4 (partial) — validates `metadata.json` in the sandbox root after extraction.
Future<void> validateExtractedMetadataJson(Directory sandbox) async {
  final meta = File(p.join(sandbox.path, 'metadata.json'));
  if (!await meta.exists()) {
    throw SecureZipImportException('missing_metadata');
  }
  final bytes = await meta.readAsBytes();
  if (bytes.isEmpty) {
    throw SecureZipImportException('empty_metadata');
  }
  try {
    final dynamic decoded = jsonDecode(utf8.decode(bytes));
    if (decoded is! Map<String, dynamic>) {
      throw SecureZipImportException('metadata_not_object');
    }
    final map = decoded;
    for (final key in ['danceStyles', 'songs', 'choreographies', 'settings']) {
      if (!map.containsKey(key)) {
        throw SecureZipImportException('metadata_missing_field', key);
      }
    }
    if (map['danceStyles'] is! List || map['songs'] is! List || map['choreographies'] is! List) {
      throw SecureZipImportException('metadata_bad_array_types');
    }
    if (map['settings'] is! Map) {
      throw SecureZipImportException('metadata_bad_settings');
    }
    AppData.fromJson(map);
  } on SecureZipImportException {
    rethrow;
  } catch (e, st) {
    throw SecureZipImportException('metadata_invalid', '$e\n$st');
  }
}
