import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show debugPrint, kIsWeb;
import 'package:hive_flutter/hive_flutter.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;

import 'file_storage_stub.dart' if (dart.library.io) 'file_storage_io.dart' as fs;
import 'storage_platform_stub.dart'
    if (dart.library.io) 'storage_platform_io.dart' as platform;

import '../models/app_data.dart';

/// Тип медиафайла для subfolder (music/ или videos/).
typedef MediaType = String; // 'music' | 'video'

const String _metadataKey = 'metadata';
const String _metadataFile = 'metadata.json';
const String _folderName = 'MidnightDancer';
const String _musicFolder = 'music';
const String _videosFolder = 'videos';

/// Сервис хранения: filesystem (mobile) или Hive/IndexedDB (web).
class StorageService {
  StorageService._();

  static StorageService? _instance;
  static StorageService get instance => _instance ??= StorageService._();

  bool? _useFilesystem;
  String? _rootPath;
  Box<String>? _hiveBox;

  bool get useFilesystem => _useFilesystem ??= _detectMode();

  bool _detectMode() {
    if (kIsWeb) return false;
    return platform.useFilesystem;
  }

  /// Инициализация (вызвать до первого использования).
  Future<void> init() async {
    if (_useFilesystem != null) return;
    _useFilesystem = _detectMode();

    if (useFilesystem) {
      debugPrint('StorageService: using filesystem mode');
      try {
        final dir = await getApplicationDocumentsDirectory();
        _rootPath = p.join(dir.path, _folderName);
      } catch (e) {
        debugPrint('StorageService: using Hive fallback ($e)');
        _useFilesystem = false;
      }
    } else {
      debugPrint('StorageService: using Hive fallback');
    }

    if (!useFilesystem) {
      await _initHive();
    }
  }

  Future<void> _initHive() async {
    await Hive.initFlutter();
    _hiveBox = await Hive.openBox<String>('midnight_dancer');
  }

  Future<String> _ensureRoot() async {
    if (_rootPath != null) return _rootPath!;
    final dir = await getApplicationDocumentsDirectory();
    _rootPath = p.join(dir.path, _folderName);
    return _rootPath!;
  }

  Future<String> _mediaPath(MediaType type) async {
    final root = await _ensureRoot();
    return type == 'music'
        ? p.join(root, _musicFolder)
        : p.join(root, _videosFolder);
  }

  String _mediaKey(String id, MediaType type) =>
      '${type}_${id.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_')}';

  /// Загрузить AppData.
  Future<AppData> loadAppData() async {
    await init();

    if (useFilesystem) {
      try {
        final root = await _ensureRoot();
        final metaFile = p.join(root, _metadataFile);
        final bytes = await fs.readFile(metaFile);
        if (bytes == null || bytes.isEmpty) return AppData();
        final json = utf8.decode(bytes);
        final map = jsonDecode(json) as Map<String, dynamic>;
        return AppData.fromJson(map);
      } catch (_) {
        return AppData();
      }
    }

    final raw = _hiveBox?.get(_metadataKey);
    if (raw == null || raw.isEmpty) return AppData();
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      return AppData.fromJson(map);
    } catch (_) {
      return AppData();
    }
  }

  /// Сохранить AppData.
  Future<void> saveAppData(AppData data) async {
    await init();
    final json = jsonEncode(data.toJson());

    if (useFilesystem) {
      try {
        final root = await _ensureRoot();
        await fs.ensureDir(root);
        await fs.ensureDir(p.join(root, _musicFolder));
        await fs.ensureDir(p.join(root, _videosFolder));
        final metaFile = p.join(root, _metadataFile);
        await fs.writeFile(metaFile, Uint8List.fromList(utf8.encode(json)));
      } catch (e) {
        debugPrint('StorageService saveAppData error: $e');
      }
      return;
    }

    await _hiveBox?.put(_metadataKey, json);
  }

  /// Скопировать медиафайл из локального пути (без загрузки в память).
  Future<void> saveMediaFileFromPath(String id, String sourcePath, MediaType type) async {
    await init();
    if (!useFilesystem) return;
    try {
      final base = await _mediaPath(type);
      await fs.ensureDir(base);
      final ext = type == 'music' ? 'mp3' : 'mp4';
      final name = '$id.$ext';
      final path = p.join(base, name);
      await fs.copyFile(sourcePath, path);
    } catch (e) {
      debugPrint('StorageService saveMediaFileFromPath error: $e');
    }
  }

  /// Сохранить медиафайл.
  Future<void> saveMediaFile(String id, Uint8List bytes, MediaType type) async {
    await init();

    if (useFilesystem) {
      try {
        final base = await _mediaPath(type);
        await fs.ensureDir(base);
        final ext = type == 'music' ? 'mp3' : 'mp4';
        final name = '$id.$ext';
        final path = p.join(base, name);
        await fs.writeFile(path, bytes);
      } catch (e) {
        debugPrint('StorageService saveMediaFile error: $e');
      }
      return;
    }

    await _hiveBox?.put(_mediaKey(id, type), base64Encode(bytes));
  }

  /// Загрузить медиафайл.
  Future<Uint8List?> loadMediaFile(String id, MediaType type) async {
    await init();

    if (useFilesystem) {
      try {
        final base = await _mediaPath(type);
        final ext = type == 'music' ? 'mp3' : 'mp4';
        final name = '$id.$ext';
        final path = p.join(base, name);
        return await fs.readFile(path);
      } catch (_) {
        return null;
      }
    }

    final raw = _hiveBox?.get(_mediaKey(id, type));
    if (raw == null) return null;
    try {
      return Uint8List.fromList(base64Decode(raw));
    } catch (_) {
      return null;
    }
  }

  /// Удалить медиафайл.
  Future<void> deleteMediaFile(String id, MediaType type) async {
    await init();

    if (useFilesystem) {
      try {
        final base = await _mediaPath(type);
        final ext = type == 'music' ? 'mp3' : 'mp4';
        final name = '$id.$ext';
        final path = p.join(base, name);
        await fs.deleteFile(path);
      } catch (e) {
        debugPrint('StorageService deleteMediaFile error: $e');
      }
      return;
    }

    await _hiveBox?.delete(_mediaKey(id, type));
  }
}
