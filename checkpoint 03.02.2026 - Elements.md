# Checkpoint 03.02.2026 — Elements

**Назначение:** восстановить текущую версию приложения Midnight Dancer, если в дальнейшем что-то пойдёт не так.

**Как использовать:** попроси AI «восстанови проект из checkpoint 03.02.2026 - Elements.md» — он создаст/перезапишет файлы по разделам ниже.

**Бинарные assets (не включены — нужны из папки проекта):**
- `assets/icon.png`
- `assets/fonts/Inter-Regular.ttf`, `Inter-Medium.ttf`, `Inter-SemiBold.ttf`, `Inter-Bold.ttf`
- `assets/voices/.gitkeep`

---

## pubspec.yaml

```yaml
name: midnight_dancer
description: Dance Training App with voice assistant
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter
  flutter_riverpod: ^2.4.9
  riverpod_annotation: ^2.3.3
  path_provider: ^2.1.3
  hive_flutter: ^1.1.0
  just_audio: ^0.9.36
  video_player: ^2.9.0
  file_picker: ^8.1.2
  go_router: ^13.2.0
  freezed_annotation: ^2.4.1
  json_annotation: ^4.8.1
  path: ^1.9.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^3.0.1
  build_runner: ^2.4.7
  freezed: ^2.4.6
  json_serializable: ^6.7.1
  riverpod_generator: ^2.3.9

flutter:
  uses-material-design: true
  assets:
    - assets/
    - assets/fonts/
  fonts:
    - family: Inter
      fonts:
        - asset: assets/fonts/Inter-Regular.ttf
        - asset: assets/fonts/Inter-Medium.ttf
          weight: 500
        - asset: assets/fonts/Inter-SemiBold.ttf
          weight: 600
        - asset: assets/fonts/Inter-Bold.ttf
          weight: 700
```

---

## lib/main.dart

```dart
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:midnight_dancer/data/services/storage_service.dart';
import 'app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Color(0xFF0f172a),
    ),
  );
  try {
    await StorageService.instance.init();
  } catch (e, st) {
    debugPrint('StorageService init error: $e');
    debugPrintStack(stackTrace: st);
  }
  runApp(const MidnightDancerApp());
}
```

---

## lib/app.dart

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'ui/screens/splash/splash_screen.dart';

class MidnightDancerApp extends StatelessWidget {
  const MidnightDancerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      child: MaterialApp(
        title: 'Midnight Dancer',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark,
        home: const SplashScreen(),
      ),
    );
  }
}
```

---

## lib/core/theme/app_theme.dart

```dart
import 'package:flutter/material.dart';

/// Цвета приложения. Константы вместо Colors.orange.shade400 — меньше лагов.
class AppColors {
  AppColors._();

  static const Color background = Color(0xFF0f172a);
  static const Color accent = Color(0xFFf97316);
  static const Color textSecondary = Color(0xFF94a3b8);
  static const Color card = Color(0xFF1e293b);
  static const Color cardBorder = Color(0xFF334155);
}

/// Радиусы скругления (24–40px)
class AppRadius {
  AppRadius._();

  static const double sm = 16;
  static const double md = 24;
  static const double lg = 32;
  static const double xl = 40;

  static BorderRadius get radiusSm => BorderRadius.circular(sm);
  static BorderRadius get radiusMd => BorderRadius.circular(md);
  static BorderRadius get radiusLg => BorderRadius.circular(lg);
  static BorderRadius get radiusXl => BorderRadius.circular(xl);
}

/// Тема приложения Midnight Dancer
class AppTheme {
  AppTheme._();

  static ThemeData get dark => ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppColors.background,
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppColors.accent,
          brightness: Brightness.dark,
          primary: AppColors.accent,
          surface: AppColors.background,
        ),
        textTheme: ThemeData.dark().textTheme.apply(fontFamily: 'Inter'),
        cardTheme: CardThemeData(
          color: AppColors.card,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: AppRadius.radiusMd,
            side: const BorderSide(color: AppColors.cardBorder, width: 1),
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.accent,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: AppRadius.radiusMd,
            ),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: AppColors.card,
          border: OutlineInputBorder(borderRadius: AppRadius.radiusMd),
          enabledBorder: OutlineInputBorder(
            borderRadius: AppRadius.radiusMd,
            borderSide: const BorderSide(color: AppColors.cardBorder),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: AppRadius.radiusMd,
            borderSide: const BorderSide(color: AppColors.accent, width: 2),
          ),
        ),
      );
}
```

---

## lib/core/app_flavor.dart

```dart
/// Определяет текущий flavor приложения.
/// Задаётся через --dart-define=FLAVOR=lite|standard|full при запуске.
class AppFlavor {
  AppFlavor._();

  static const String _flavor = String.fromEnvironment(
    'FLAVOR',
    defaultValue: 'lite',
  );

  static bool get isLite => _flavor == 'lite';
  static bool get isStandard => _flavor == 'standard';
  static bool get isFull => _flavor == 'full';

  static String get name => _flavor;
}
```

---

## lib/core/constants/app_constants.dart

```dart
// App constants - colors, strings
class AppConstants {
  static const String appName = 'Midnight Dancer';
}
```

---

## lib/core/utils/formatters.dart

```dart
// Formatting utilities - duration, file size
```

---

## lib/core/utils/read_bytes.dart

```dart
import 'dart:typed_data';

Future<Uint8List?> readBytesFromPath(String path) async => null;
```

---

## lib/core/utils/read_bytes_io.dart

```dart
import 'dart:io';
import 'dart:typed_data';

Future<Uint8List?> readBytesFromPath(String path) async {
  try {
    final f = File(path);
    if (await f.exists()) return f.readAsBytes();
  } catch (_) {}
  return null;
}
```

---

## lib/core/utils/file_copy_platform.dart

```dart
import 'dart:typed_data';

Future<String?> copyPickedFileToCache(String uriOrPath) async => null;
Future<bool> takeUriPermission(String uri) async => false;
Future<Uint8List?> getVideoThumbnail(String uriOrPath) async => null;
```

---

## lib/core/utils/file_copy_platform_io.dart

```dart
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/services.dart';

const _channel = MethodChannel('com.midnightdancer.app/file_copy');

Future<String?> copyPickedFileToCache(String uriOrPath) async {
  try {
    final result = await _channel.invokeMethod<String>('copyToCache', {'uri': uriOrPath});
    return result;
  } catch (_) {
    return null;
  }
}

Future<bool> takeUriPermission(String uri) async {
  try {
    await _channel.invokeMethod('takeUriPermission', {'uri': uri});
    return true;
  } catch (_) {
    return false;
  }
}

Future<Uint8List?> getVideoThumbnail(String uriOrPath) async {
  try {
    final base64 = await _channel.invokeMethod<String>('getVideoThumbnail', {'uri': uriOrPath});
    if (base64 == null || base64.isEmpty) return null;
    return Uint8List.fromList(base64Decode(base64));
  } catch (_) {
    return null;
  }
}
```

---

## lib/core/utils/thumbnail_cache.dart

```dart
import 'dart:typed_data';

import 'file_copy_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/file_copy_platform_io.dart'
    as file_copy;
import 'thumbnail_cache_stub.dart'
    if (dart.library.io) 'thumbnail_cache_io.dart'
    as disk;

/// Кэш миниатюр: память + диск (mobile). Для 200–500 элементов.
/// Лимит одновременных загрузок — 4, чтобы не перегружать UI.
class ThumbnailCache {
  ThumbnailCache._();

  static final ThumbnailCache instance = ThumbnailCache._();

  final Map<String, Uint8List> _memory = {};
  static const _maxMemoryEntries = 150;
  int _activeLoads = 0;
  static const _maxConcurrent = 4;
  Future<Uint8List?> get(String uriOrPath) async {
    if (uriOrPath.isEmpty) return null;
    final key = _key(uriOrPath);

    final cached = _memory[key];
    if (cached != null) return cached;

    while (_activeLoads >= _maxConcurrent) {
      await Future<void>.delayed(const Duration(milliseconds: 50));
    }
    _activeLoads++;
    try {
      final fromDisk = await disk.readThumbnail(key);
      if (fromDisk != null && fromDisk.isNotEmpty) {
        _putMemory(key, fromDisk);
        return fromDisk;
      }

      final generated = await file_copy.getVideoThumbnail(uriOrPath);
      if (generated != null && generated.isNotEmpty) {
        _putMemory(key, generated);
        disk.writeThumbnail(key, generated);
      }
      return generated;
    } finally {
      _activeLoads--;
    }
  }

  void _putMemory(String key, Uint8List bytes) {
    while (_memory.length >= _maxMemoryEntries && _memory.isNotEmpty) {
      _memory.remove(_memory.keys.first);
    }
    _memory[key] = bytes;
  }

  String _key(String uriOrPath) {
    return uriOrPath.hashCode.abs().toString();
  }

  void clear() => _memory.clear();
}
```

---

## lib/core/utils/thumbnail_cache_io.dart

```dart
import 'dart:io';
import 'dart:typed_data';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

Future<Uint8List?> readThumbnail(String key) async {
  try {
    final dir = await getTemporaryDirectory();
    final path = p.join(dir.path, 'thumbnails', '$key.jpg');
    final file = File(path);
    if (await file.exists()) return await file.readAsBytes();
  } catch (_) {}
  return null;
}

Future<void> writeThumbnail(String key, Uint8List bytes) async {
  try {
    final dir = await getTemporaryDirectory();
    final thumbDir = Directory(p.join(dir.path, 'thumbnails'));
    if (!await thumbDir.exists()) await thumbDir.create(recursive: true);
    final file = File(p.join(thumbDir.path, '$key.jpg'));
    await file.writeAsBytes(bytes);
  } catch (_) {}
}
```

---

## lib/core/utils/thumbnail_cache_stub.dart

```dart
import 'dart:typed_data';

Future<Uint8List?> readThumbnail(String key) async => null;
Future<void> writeThumbnail(String key, Uint8List bytes) async {}
```

---

## lib/core/utils/video_temp.dart

```dart
import 'dart:typed_data';

/// Возвращает путь к временному видео-файлу из bytes, или null на web.
Future<String?> writeVideoTemp(Uint8List bytes) async => null;
```

---

## lib/core/utils/video_temp_io.dart

```dart
import 'dart:io';
import 'dart:typed_data';

import 'package:path_provider/path_provider.dart';

Future<String?> writeVideoTemp(Uint8List bytes) async {
  try {
    final dir = await getTemporaryDirectory();
    final path = '${dir.path}/vid_${DateTime.now().millisecondsSinceEpoch}.mp4';
    final file = File(path);
    await file.writeAsBytes(bytes);
    return path;
  } catch (_) {
    return null;
  }
}
```

---

## lib/data/models/move.dart

```dart
import 'package:json_annotation/json_annotation.dart';

part 'move.g.dart';

@JsonSerializable()
class Move {
  Move({
    required this.id,
    required this.name,
    required this.level,
    this.description,
    this.videoUri,
  });

  factory Move.fromJson(Map<String, dynamic> json) {
    final m = _$MoveFromJson(json);
    final uri = m.videoUri ??
        json['videoFileName'] as String? ??
        json['videoRef'] as String?;
    if (uri != null && uri.isNotEmpty && uri != m.videoUri) {
      return m.copyWith(videoUri: uri);
    }
    return m;
  }

  final String id;
  final String name;
  final String level;
  final String? description;
  /// content:// URI или путь к файлу. Без копирования в папку приложения.
  final String? videoUri;

  Map<String, dynamic> toJson() => _$MoveToJson(this);

  Move copyWith({
    String? id,
    String? name,
    String? level,
    String? description,
    String? videoUri,
  }) =>
      Move(
        id: id ?? this.id,
        name: name ?? this.name,
        level: level ?? this.level,
        description: description ?? this.description,
        videoUri: videoUri ?? this.videoUri,
      );
}
```

---

## lib/data/models/move.g.dart

```dart
// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'move.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Move _$MoveFromJson(Map<String, dynamic> json) => Move(
      id: json['id'] as String,
      name: json['name'] as String,
      level: json['level'] as String,
      description: json['description'] as String?,
      videoUri: json['videoUri'] as String?,
    );

Map<String, dynamic> _$MoveToJson(Move instance) => <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'level': instance.level,
      'description': instance.description,
      'videoUri': instance.videoUri,
    };
```

---

## lib/data/models/dance_style.dart

```dart
import 'package:json_annotation/json_annotation.dart';
import 'move.dart';

part 'dance_style.g.dart';

@JsonSerializable()
class DanceStyle {
  DanceStyle({
    required this.id,
    required this.name,
    this.moves = const [],
  });

  factory DanceStyle.fromJson(Map<String, dynamic> json) =>
      _$DanceStyleFromJson(json);

  final String id;
  final String name;
  final List<Move> moves;

  Map<String, dynamic> toJson() => _$DanceStyleToJson(this);

  DanceStyle copyWith({
    String? id,
    String? name,
    List<Move>? moves,
  }) =>
      DanceStyle(
        id: id ?? this.id,
        name: name ?? this.name,
        moves: moves ?? this.moves,
      );
}
```

---

## lib/data/models/dance_style.g.dart

```dart
// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'dance_style.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

DanceStyle _$DanceStyleFromJson(Map<String, dynamic> json) => DanceStyle(
      id: json['id'] as String,
      name: json['name'] as String,
      moves: (json['moves'] as List<dynamic>?)
              ?.map((e) => Move.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );

Map<String, dynamic> _$DanceStyleToJson(DanceStyle instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'moves': instance.moves.map((e) => e.toJson()).toList(),
    };
```

---

## lib/data/models/song.dart

```dart
import 'package:json_annotation/json_annotation.dart';

part 'song.g.dart';

@JsonSerializable()
class Song {
  Song({
    required this.id,
    required this.title,
    required this.danceStyle,
    required this.level,
    required this.fileName,
    required this.duration,
    required this.sizeBytes,
  });

  factory Song.fromJson(Map<String, dynamic> json) => _$SongFromJson(json);

  final String id;
  final String title;
  final String danceStyle;
  final String level;
  final String fileName;
  final double duration;
  final int sizeBytes;

  Map<String, dynamic> toJson() => _$SongToJson(this);

  Song copyWith({
    String? id,
    String? title,
    String? danceStyle,
    String? level,
    String? fileName,
    double? duration,
    int? sizeBytes,
  }) =>
      Song(
        id: id ?? this.id,
        title: title ?? this.title,
        danceStyle: danceStyle ?? this.danceStyle,
        level: level ?? this.level,
        fileName: fileName ?? this.fileName,
        duration: duration ?? this.duration,
        sizeBytes: sizeBytes ?? this.sizeBytes,
      );
}
```

---

## lib/data/models/song.g.dart

```dart
// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'song.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Song _$SongFromJson(Map<String, dynamic> json) => Song(
      id: json['id'] as String,
      title: json['title'] as String,
      danceStyle: json['danceStyle'] as String,
      level: json['level'] as String,
      fileName: json['fileName'] as String,
      duration: (json['duration'] as num).toDouble(),
      sizeBytes: (json['sizeBytes'] as num).toInt(),
    );

Map<String, dynamic> _$SongToJson(Song instance) => <String, dynamic>{
      'id': instance.id,
      'title': instance.title,
      'danceStyle': instance.danceStyle,
      'level': instance.level,
      'fileName': instance.fileName,
      'duration': instance.duration,
      'sizeBytes': instance.sizeBytes,
    };
```

---

## lib/data/models/choreography.dart

```dart
import 'package:json_annotation/json_annotation.dart';

part 'choreography.g.dart';

@JsonSerializable()
class Choreography {
  Choreography({
    required this.id,
    required this.name,
    required this.songId,
    required this.styleId,
    this.timeline = const {},
    this.startTime = 0,
    required this.endTime,
  });

  factory Choreography.fromJson(Map<String, dynamic> json) =>
      _$ChoreographyFromJson(json);

  final String id;
  final String name;
  final String songId;
  final String styleId;
  final Map<double, String> timeline;
  final double startTime;
  final double endTime;

  Map<String, dynamic> toJson() => _$ChoreographyToJson(this);

  Choreography copyWith({
    String? id,
    String? name,
    String? songId,
    String? styleId,
    Map<double, String>? timeline,
    double? startTime,
    double? endTime,
  }) =>
      Choreography(
        id: id ?? this.id,
        name: name ?? this.name,
        songId: songId ?? this.songId,
        styleId: styleId ?? this.styleId,
        timeline: timeline ?? this.timeline,
        startTime: startTime ?? this.startTime,
        endTime: endTime ?? this.endTime,
      );
}
```

---

## lib/data/models/choreography.g.dart

```dart
// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'choreography.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Map<double, String> _parseTimeline(Map<String, dynamic> json) {
  return json.map((k, v) => MapEntry(double.parse(k), v as String));
}

Map<String, dynamic> _timelineToJson(Map<double, String> timeline) {
  return timeline.map((k, v) => MapEntry(k.toString(), v));
}

Choreography _$ChoreographyFromJson(Map<String, dynamic> json) =>
    Choreography(
      id: json['id'] as String,
      name: json['name'] as String,
      songId: json['songId'] as String,
      styleId: json['styleId'] as String,
      timeline: json['timeline'] != null
          ? _parseTimeline(json['timeline'] as Map<String, dynamic>)
          : {},
      startTime: (json['startTime'] as num?)?.toDouble() ?? 0,
      endTime: (json['endTime'] as num).toDouble(),
    );

Map<String, dynamic> _$ChoreographyToJson(Choreography instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'songId': instance.songId,
      'styleId': instance.styleId,
      'timeline': _timelineToJson(instance.timeline),
      'startTime': instance.startTime,
      'endTime': instance.endTime,
    };
```

---

## lib/data/models/app_data.dart

```dart
import 'package:json_annotation/json_annotation.dart';
import 'dance_style.dart';
import 'song.dart';
import 'choreography.dart';

part 'app_data.g.dart';

@JsonSerializable()
class AppData {
  AppData({
    this.danceStyles = const [],
    this.songs = const [],
    this.choreographies = const [],
    this.settings = const {},
  });

  factory AppData.fromJson(Map<String, dynamic> json) =>
      _$AppDataFromJson(json);

  final List<DanceStyle> danceStyles;
  final List<Song> songs;
  final List<Choreography> choreographies;
  final Map<String, dynamic> settings;

  Map<String, dynamic> toJson() => _$AppDataToJson(this);

  AppData copyWith({
    List<DanceStyle>? danceStyles,
    List<Song>? songs,
    List<Choreography>? choreographies,
    Map<String, dynamic>? settings,
  }) =>
      AppData(
        danceStyles: danceStyles ?? this.danceStyles,
        songs: songs ?? this.songs,
        choreographies: choreographies ?? this.choreographies,
        settings: settings ?? this.settings,
      );
}
```

---

## lib/data/models/app_data.g.dart

```dart
// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AppData _$AppDataFromJson(Map<String, dynamic> json) => AppData(
      danceStyles: (json['danceStyles'] as List<dynamic>?)
              ?.map((e) => DanceStyle.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      songs: (json['songs'] as List<dynamic>?)
              ?.map((e) => Song.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      choreographies: (json['choreographies'] as List<dynamic>?)
              ?.map((e) => Choreography.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      settings: (json['settings'] as Map<String, dynamic>?) ?? {},
    );

Map<String, dynamic> _$AppDataToJson(AppData instance) => <String, dynamic>{
      'danceStyles': instance.danceStyles.map((e) => e.toJson()).toList(),
      'songs': instance.songs.map((e) => e.toJson()).toList(),
      'choreographies':
          instance.choreographies.map((e) => e.toJson()).toList(),
      'settings': instance.settings,
    };
```

---

## lib/data/models/models.dart

```dart
export 'move.dart';
export 'dance_style.dart';
export 'song.dart';
export 'choreography.dart';
export 'app_data.dart';
```

---

## lib/data/services/file_storage_io.dart

```dart
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
```

---

## lib/data/services/file_storage_stub.dart

```dart
import 'dart:typed_data';

/// Заглушка для web: file I/O недоступен.
Future<Uint8List?> readFile(String path) async => null;
Future<void> writeFile(String path, Uint8List bytes) async {}
Future<void> deleteFile(String path) async {}
Future<void> ensureDir(String path) async {}
Future<bool> fileExists(String path) async => false;
Future<void> copyFile(String sourcePath, String destPath) async {}
```

---

## lib/data/services/storage_platform_io.dart

```dart
import 'dart:io' show Platform;

/// На mobile (Android/iOS) используем filesystem.
bool get useFilesystem => Platform.isAndroid || Platform.isIOS;
```

---

## lib/data/services/storage_platform_stub.dart

```dart
/// Заглушка для web: filesystem недоступен.
bool get useFilesystem => false;
```

---

## lib/data/services/storage_service.dart

```dart
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
```

---

## lib/providers/app_data_provider.dart

```dart
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/services/storage_service.dart';

final storageServiceProvider = Provider<StorageService>((ref) {
  return StorageService.instance;
});

/// Состояние и операции с AppData.
class AppDataNotifier extends StateNotifier<AsyncValue<AppData>> {
  AppDataNotifier(this._storage) : super(const AsyncValue.loading()) {
    _load();
  }

  final StorageService _storage;

  Future<void> _load() async {
    state = const AsyncValue.loading();
    try {
      final data = await _storage.loadAppData();
      state = AsyncValue.data(data);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> save(AppData data) async {
    await _storage.saveAppData(data);
    state = AsyncValue.data(data);
  }

  Future<void> addStyle(DanceStyle style) async {
    final current = state.valueOrNull ?? AppData();
    final updated = current.copyWith(
      danceStyles: [...current.danceStyles, style],
    );
    await save(updated);
  }

  Future<void> updateStyle(DanceStyle style) async {
    final current = state.valueOrNull ?? AppData();
    final idx = current.danceStyles.indexWhere((s) => s.id == style.id);
    if (idx < 0) return;
    final updated = current.copyWith(
      danceStyles: [
        ...current.danceStyles.take(idx),
        style,
        ...current.danceStyles.skip(idx + 1),
      ],
    );
    await save(updated);
  }

  Future<void> deleteStyle(String styleId) async {
    final current = state.valueOrNull ?? AppData();
    final updated = current.copyWith(
      danceStyles: current.danceStyles.where((s) => s.id != styleId).toList(),
    );
    await save(updated);
  }

  Future<void> addMove(String styleId, Move move, {Uint8List? videoBytes, String? videoPath}) async {
    final current = state.valueOrNull ?? AppData();
    final style = current.danceStyles.firstWhere((s) => s.id == styleId);
    String? videoUri;
    if (videoPath != null && videoPath.isNotEmpty) {
      videoUri = videoPath;
    } else if (videoBytes != null && videoBytes.isNotEmpty) {
      await _storage.saveMediaFile(move.id, videoBytes, 'video');
      videoUri = move.id;
    }
    final newMove = move.copyWith(videoUri: videoUri);
    final updatedStyle = style.copyWith(moves: [...style.moves, newMove]);
    await updateStyle(updatedStyle);
  }

  Future<void> updateMove(String styleId, Move move, {Uint8List? videoBytes, String? videoPath}) async {
    final current = state.valueOrNull ?? AppData();
    final style = current.danceStyles.firstWhere((s) => s.id == styleId);
    final oldMoves = style.moves.where((m) => m.id == move.id);
    final oldMove = oldMoves.isEmpty ? null : oldMoves.first;
    String? videoUri;
    if (videoPath != null && videoPath.isNotEmpty) {
      videoUri = videoPath;
    } else if (videoBytes != null && videoBytes.isNotEmpty) {
      await _storage.saveMediaFile(move.id, videoBytes, 'video');
      videoUri = move.id;
    } else {
      videoUri = move.videoUri ?? oldMove?.videoUri;
    }
    final newMove = move.copyWith(videoUri: videoUri);
    final updatedMoves = style.moves.map((m) => m.id == move.id ? newMove : m).toList();
    await updateStyle(style.copyWith(moves: updatedMoves));
  }

  Future<void> deleteMove(String styleId, String moveId) async {
    final current = state.valueOrNull ?? AppData();
    final style = current.danceStyles.firstWhere((s) => s.id == styleId);
    final updatedStyle = style.copyWith(
      moves: style.moves.where((m) => m.id != moveId).toList(),
    );
    await updateStyle(updatedStyle);
  }

  Future<void> clearVideoForMove(String styleId, String moveId) async {
    final current = state.valueOrNull ?? AppData();
    final style = current.danceStyles.firstWhere((s) => s.id == styleId);
    final updatedMoves = style.moves.map((m) {
      if (m.id == moveId) return m.copyWith(videoUri: null);
      return m;
    }).toList();
    await updateStyle(style.copyWith(moves: updatedMoves));
  }

  Future<Uint8List?> loadVideo(String? videoId) async {
    if (videoId == null || videoId.isEmpty) return null;
    return _storage.loadMediaFile(videoId, 'video');
  }
}

final appDataNotifierProvider =
    StateNotifierProvider<AppDataNotifier, AsyncValue<AppData>>((ref) {
  return AppDataNotifier(ref.watch(storageServiceProvider));
});
```

---

## lib/ui/widgets/video_preview.dart

```dart
// Export platform-specific implementation.
export 'video_preview_stub.dart' if (dart.library.io) 'video_preview_io.dart';
```

---

## lib/ui/widgets/video_preview_io.dart

```dart
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:video_player/video_player.dart';

/// Превью видео (mobile) — принимает путь к файлу.
class VideoPreview extends StatefulWidget {
  const VideoPreview({
    super.key,
    this.videoPath,
    this.initialSpeed = 1.0,
  });

  final String? videoPath;
  final double initialSpeed;

  @override
  State<VideoPreview> createState() => _VideoPreviewState();
}

class _VideoPreviewState extends State<VideoPreview> {
  VideoPlayerController? _controller;
  double _speed = 1.0;
  bool _error = false;

  static const _speeds = [0.5, 0.75, 1.0, 1.5];

  @override
  void initState() {
    super.initState();
    _speed = widget.initialSpeed;
    _initController();
  }

  @override
  void didUpdateWidget(covariant VideoPreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.videoPath != widget.videoPath) {
      _disposeController();
      _error = false;
      _initController();
    }
  }

  void _listener() {
    if (mounted) setState(() {});
  }

  Future<void> _initController() async {
    final path = widget.videoPath;
    if (path == null || path.isEmpty) return;
    try {
      if (path.startsWith('content:')) {
        _controller = VideoPlayerController.contentUri(Uri.parse(path));
      } else {
        _controller = VideoPlayerController.file(File(path));
      }
      _controller!.addListener(_listener);
      await _controller!.initialize();
      await _controller!.setPlaybackSpeed(_speed);
      await _controller!.play();
      if (mounted) setState(() {});
    } catch (e) {
      _error = true;
      if (mounted) setState(() {});
    }
  }

  void _disposeController() {
    _controller?.removeListener(_listener);
    _controller?.dispose();
    _controller = null;
  }

  @override
  void dispose() {
    _disposeController();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_error) {
      return Container(
        height: 180,
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: AppRadius.radiusMd,
        ),
        alignment: Alignment.center,
        child: Text(
          'Ошибка загрузки видео',
          style: TextStyle(color: AppColors.textSecondary),
        ),
      );
    }
    if (_controller == null || !_controller!.value.isInitialized) {
      if (widget.videoPath == null || widget.videoPath!.isEmpty) {
        return const SizedBox.shrink();
      }
      return Container(
        height: 180,
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: AppRadius.radiusMd,
        ),
        alignment: Alignment.center,
        child: const CircularProgressIndicator(color: AppColors.accent),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: AppRadius.radiusMd,
          child: AspectRatio(
            aspectRatio: _controller!.value.aspectRatio,
            child: GestureDetector(
              onTap: () async {
                if (_controller!.value.isPlaying) {
                  await _controller!.pause();
                } else {
                  await _controller!.play();
                }
                if (mounted) setState(() {});
              },
              child: Stack(
                alignment: Alignment.center,
                children: [
                  VideoPlayer(_controller!),
                  if (!_controller!.value.isPlaying)
                    Container(
                      color: Colors.black26,
                      child: const Icon(Icons.play_arrow, size: 64, color: Colors.white),
                    ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Скорость',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textSecondary,
                ),
              ),
              Row(
                children: _speeds.map((s) {
                  final active = (_speed - s).abs() < 0.01;
                  return Padding(
                    padding: const EdgeInsets.only(left: 6),
                    child: GestureDetector(
                      onTap: () async {
                        _speed = s;
                        await _controller?.setPlaybackSpeed(s);
                        if (mounted) setState(() {});
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: active ? AppColors.accent : AppColors.card,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          '${s}x',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: active ? Colors.white : AppColors.textSecondary,
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
```

---

## lib/ui/widgets/video_preview_stub.dart

```dart
import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';

/// Заглушка VideoPreview для web — показывает плейсхолдер.
class VideoPreview extends StatelessWidget {
  const VideoPreview({
    super.key,
    this.videoPath,
    this.initialSpeed = 1.0,
  });

  final String? videoPath;
  final double initialSpeed;

  @override
  Widget build(BuildContext context) {
    if (videoPath == null || videoPath!.isEmpty) return const SizedBox.shrink();
    return Container(
      height: 180,
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(24),
      ),
      alignment: Alignment.center,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.videocam, size: 48, color: AppColors.textSecondary),
          const SizedBox(height: 8),
          Text(
            'Предпросмотр на web',
            style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
          ),
        ],
      ),
    );
  }
}
```

---

## lib/ui/widgets/move_card_video.dart

```dart
export 'move_card_video_stub.dart'
    if (dart.library.io) 'move_card_video_io.dart';
```

---

## lib/ui/widgets/move_card_video_io.dart

```dart
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

class MoveCardVideo extends StatefulWidget {
  const MoveCardVideo({
    super.key,
    required this.videoPathOrUri,
    required this.onEdit,
    this.onError,
  });

  final String videoPathOrUri;
  final VoidCallback onEdit;
  final VoidCallback? onError;

  @override
  State<MoveCardVideo> createState() => _MoveCardVideoState();
}

class _MoveCardVideoState extends State<MoveCardVideo> {
  VideoPlayerController? _controller;
  bool _hover = false;
  bool _initError = false;
  bool _initializing = false;

  @override
  void didUpdateWidget(covariant MoveCardVideo oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.videoPathOrUri != widget.videoPathOrUri) {
      _controller?.dispose();
      _controller = null;
      _initError = false;
      _initializing = false;
    }
  }

  Future<void> _init() async {
    if (_controller != null || _initializing || _initError) return;
    _initializing = true;
    if (mounted) setState(() {});
    try {
      if (widget.videoPathOrUri.startsWith('content:')) {
        _controller = VideoPlayerController.contentUri(Uri.parse(widget.videoPathOrUri));
      } else {
        _controller = VideoPlayerController.file(File(widget.videoPathOrUri));
      }
      await _controller!.initialize();
      await _controller!.setLooping(true);
      await _controller!.setVolume(0);
      if (mounted) setState(() {});
    } catch (_) {
      if (mounted) {
        setState(() {
          _initError = true;
          _initializing = false;
        });
        widget.onError?.call();
      }
      return;
    }
    if (mounted) setState(() => _initializing = false);
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  void _onHover(bool enter) {
    setState(() => _hover = enter);
    if (enter) {
      _init().then((_) {
        if (_controller != null && mounted) _controller?.play();
      });
    } else {
      _controller?.pause();
      _controller?.seekTo(Duration.zero);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ready = _controller != null && _controller!.value.isInitialized;
    return MouseRegion(
      onEnter: (_) => _onHover(true),
      onExit: (_) => _onHover(false),
      child: GestureDetector(
        onTap: widget.onEdit,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (_initError)
              const Center(child: Icon(Icons.videocam_off, color: Colors.white38, size: 48))
            else if (_initializing || !ready)
              Center(
                child: Icon(Icons.play_circle_outline, color: Colors.white54, size: 48),
              )
            else
              LayoutBuilder(
                builder: (_, constraints) {
                  final size = _controller!.value.size;
                  final w = size.width > 0 ? size.width : 16.0;
                  final h = size.height > 0 ? size.height : 9.0;
                  return FittedBox(
                    fit: BoxFit.cover,
                    alignment: Alignment.center,
                    child: SizedBox(
                      width: w,
                      height: h,
                      child: VideoPlayer(_controller!),
                    ),
                  );
                },
              ),
            if (_hover && ready)
              Container(
                color: Colors.black26,
                child: const Center(
                  child: Icon(Icons.edit, color: Colors.white70, size: 48),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
```

---

## lib/ui/widgets/move_card_video_stub.dart

```dart
import 'package:flutter/material.dart';

class MoveCardVideo extends StatelessWidget {
  const MoveCardVideo({
    super.key,
    required this.videoPathOrUri,
    required this.onEdit,
    this.onError,
  });

  final String videoPathOrUri;
  final VoidCallback onEdit;
  final VoidCallback? onError;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onEdit,
      child: Container(
        color: Colors.black26,
        child: const Center(
          child: Icon(Icons.videocam, color: Colors.white38, size: 48),
        ),
      ),
    );
  }
}
```

---

## lib/ui/widgets/move_card.dart

```dart
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/file_copy_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/file_copy_platform_io.dart'
    as file_copy;
import 'package:midnight_dancer/core/utils/thumbnail_cache.dart';
import 'package:midnight_dancer/core/utils/video_temp.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/video_temp_io.dart'
    as video_temp;
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';

const _levelLabels = {
  'Beginner': 'Начинающий',
  'Intermediate': 'Средний',
  'Advanced': 'Профи',
};

String _levelLabel(String level) =>
    _levelLabels[level] ?? level;

class MoveCard extends ConsumerStatefulWidget {
  const MoveCard({
    super.key,
    required this.move,
    required this.styleId,
    required this.onEdit,
    required this.onDelete,
    this.onVideoUnavailable,
  });

  final Move move;
  final String styleId;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final VoidCallback? onVideoUnavailable;

  @override
  ConsumerState<MoveCard> createState() => _MoveCardState();
}

class _MoveCardState extends ConsumerState<MoveCard> {
  String? _videoPathOrUri;
  Uint8List? _thumbnailBytes;
  bool _loading = true;
  bool _error = false;

  @override
  void initState() {
    super.initState();
    _resolveVideo();
  }

  @override
  void didUpdateWidget(covariant MoveCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.move.videoUri != widget.move.videoUri) {
      _resolveVideo();
    }
  }

  Future<void> _resolveVideo() async {
    final uri = widget.move.videoUri;
    if (uri == null || uri.isEmpty) {
      if (mounted) setState(() {
        _loading = false;
        _videoPathOrUri = null;
        _thumbnailBytes = null;
        _error = false;
      });
      return;
    }
    if (uri.startsWith('content:') || uri.startsWith('/')) {
      _videoPathOrUri = uri;
      final bytes = await ThumbnailCache.instance.get(uri);
      if (!mounted) return;
      if (mounted) setState(() {
        _thumbnailBytes = bytes;
        _loading = false;
        _error = false;
      });
      return;
    }
    final notifier = ref.read(appDataNotifierProvider.notifier);
    final bytes = await notifier.loadVideo(uri) as Uint8List?;
    if (!mounted) return;
    if (bytes == null || bytes.isEmpty) {
      if (mounted) setState(() {
        _loading = false;
        _videoPathOrUri = null;
        _thumbnailBytes = null;
        _error = true;
      });
      return;
    }
    final path = await video_temp.writeVideoTemp(bytes);
    if (!mounted) return;
    final thumb = await ThumbnailCache.instance.get(path ?? uri);
    if (!mounted) return;
    if (mounted) {
      setState(() {
        _videoPathOrUri = path ?? uri;
        _thumbnailBytes = thumb;
        _loading = false;
        _error = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: widget.onEdit,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.card,
          borderRadius: AppRadius.radiusXl,
          border: Border.all(color: AppColors.cardBorder),
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          fit: StackFit.expand,
          children: [
            _buildPreview(),
            Positioned(
              top: 8,
              left: 8,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.accent.withOpacity(0.3)),
                ),
                child: Text(
                  _levelLabel(widget.move.level),
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: AppColors.accent,
                  ),
                ),
              ),
            ),
            Positioned(
              top: 8,
              right: 8,
              child: GestureDetector(
                onTap: widget.onDelete,
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.close,
                    size: 16,
                    color: Colors.white70,
                  ),
                ),
              ),
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.transparent, Colors.black87],
                  ),
                ),
                child: Text(
                  widget.move.name,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPreview() {
    if (_loading) {
      return Container(
        color: AppColors.background,
        child: const Center(
          child: CircularProgressIndicator(color: AppColors.accent),
        ),
      );
    }
    if (_thumbnailBytes != null && _thumbnailBytes!.isNotEmpty) {
      return Image.memory(
        _thumbnailBytes!,
        fit: BoxFit.cover,
        cacheWidth: 512,
        cacheHeight: 512,
      );
    }
    return Container(
      color: AppColors.background,
      child: Center(
        child: Icon(Icons.videocam, color: Colors.white24, size: 48),
      ),
    );
  }
}
```

---

## lib/ui/screens/elements/elements_screen.dart

**При восстановлении:** скопировать `checkpoint_03.02.2026_elements_screen_src.dart` → `lib/ui/screens/elements/elements_screen.dart`

---

## lib/ui/navigation/main_scaffold.dart

Полный код в проекте — MainScaffold с BottomNav/NavigationRail, ElementsScreen, MusicScreen, ChoreographyScreen, TrainerScreen.

---

## lib/ui/screens/splash/splash_screen.dart

Полный код в проекте — SplashScreen с иконкой, «by Nighttech», переход в MainScaffold.

---

## lib/ui/screens/home_screen.dart, music_screen.dart, choreography_screen.dart, trainer_screen.dart

Заглушки с иконками и заголовками.

---

## analysis_options.yaml

```yaml
include: package:flutter_lints/flutter.yaml
linter:
  rules:
    avoid_print: false
```

---

## .gitignore (ключевые строки)

```
# Flutter
.dart_tool/
.packages
build/
pubspec.lock
old_app_midnightdancer/
# IDE
.idea/
.vscode/
*.iml
# Android
android/.gradle/
android/local.properties
android/**/build/
```

---

## android/app/src/main/kotlin/com/midnightdancer/app/MainActivity.kt

Полный код в проекте: takeUriPermission, getVideoThumbnailBase64 (512px, JPEG 85), copyToAppCache. MethodChannel `com.midnightdancer.app/file_copy`.

---

## android/app/build.gradle

Flavors: lite, standard, full. compileSdk 36, Java 17.

---

## android/build.gradle, settings.gradle, gradle.properties

Стандартная конфигурация Flutter. gradle-wrapper: 8.4.

---

## android/app/src/main/AndroidManifest.xml

Разрешения для video picker, queries.

---

## android/app/src/main/res/values/colors.xml, styles.xml

LaunchTheme #0f172a, NormalTheme.

---

**После восстановления:** `flutter pub get`, при необходимости `dart run build_runner build --delete-conflicting-outputs` (если .g.dart отсутствуют).
