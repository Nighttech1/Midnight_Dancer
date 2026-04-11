import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

/// Имена файлов моделей Piper VITS по voiceId (как в старом приложении).
const Map<String, String> piperModelFileName = {
  'ruslan': 'ru_RU-ruslan-medium.onnx',
  'irina': 'ru_RU-irina-medium.onnx',
  'kamila': 'en_US-libritts_r-medium.onnx',
};

const int _copyChunkSize = 512 * 1024; // 512 KB — меньше пик памяти при копировании большой модели

/// Пишет [data] в файл по частям, чтобы не дублировать весь объём в памяти.
Future<void> _writeByteDataToFileChunked(ByteData data, File file) async {
  final sink = file.openWrite();
  try {
    for (var offset = 0; offset < data.lengthInBytes; offset += _copyChunkSize) {
      final end = (offset + _copyChunkSize < data.lengthInBytes)
          ? offset + _copyChunkSize
          : data.lengthInBytes;
      final chunk = data.buffer.asUint8List(data.offsetInBytes + offset, end - offset);
      sink.add(chunk);
    }
  } finally {
    await sink.close();
  }
}

String? _espeakNgDataDirCache;

/// Копирует espeak-ng-data из ассетов во временную папку (один раз за сессию).
/// Возвращает путь к папке или null при ошибке. Нужен для Sherpa VITS (dataDir).
Future<String?> copyEspeakNgDataToTemp() async {
  if (_espeakNgDataDirCache != null) return _espeakNgDataDirCache;
  try {
    final dir = await getTemporaryDirectory();
    final espeakDir = Directory('${dir.path}/tts_espeak_ng_data');
    if (await espeakDir.exists()) {
      try {
        await espeakDir.delete(recursive: true);
      } catch (_) {}
    }
    await espeakDir.create(recursive: true);

    final manifest = await rootBundle.loadString('assets/voices/espeak-ng-data-manifest.txt');
    final lines = manifest.split('\n').map((s) => s.trim()).where((s) => s.isNotEmpty);
    for (final relPath in lines) {
      final key = 'assets/voices/espeak-ng-data/$relPath';
      try {
        final data = await rootBundle.load(key);
        final file = File('${espeakDir.path}/$relPath');
        await file.parent.create(recursive: true);
        await file.writeAsBytes(data.buffer.asUint8List());
      } catch (_) {}
    }
    _espeakNgDataDirCache = espeakDir.path;
    return _espeakNgDataDirCache;
  } catch (e, st) {
    debugPrint('TtsAssetLoader: copyEspeakNgDataToTemp failed: $e');
    debugPrint('TtsAssetLoader: $st');
    return null;
  }
}

/// Копирует ассеты голоса assets/voices/[flavor]/[voiceId]/ во временную папку.
/// Возвращает путь к папке или null при ошибке — тогда используется системный TTS.
Future<String?> copyVoiceAssetsToTemp(String flavor, String voiceId) async {
  try {
    final dir = await getTemporaryDirectory();
    final voiceDir = Directory('${dir.path}/tts_voice_${flavor}_$voiceId');
    if (await voiceDir.exists()) {
      try {
        await voiceDir.delete(recursive: true);
      } catch (_) {}
    }
    await voiceDir.create(recursive: true);

    final prefix = 'assets/voices/$flavor/$voiceId/';
    int copied = 0;

    // 1) Модель ONNX — запись по частям, чтобы не держать две копии в памяти
    final onnxName = piperModelFileName[voiceId] ?? 'model.onnx';
    try {
      final data = await rootBundle.load('$prefix$onnxName');
      final file = File('${voiceDir.path}/model.onnx');
      await _writeByteDataToFileChunked(data, file);
      copied++;
    } catch (e) {
      debugPrint('TtsAssetLoader: load onnx $onnxName: $e');
      try {
        final data = await rootBundle.load('${prefix}model.onnx');
        final file = File('${voiceDir.path}/model.onnx');
        await _writeByteDataToFileChunked(data, file);
        copied++;
      } catch (e2) {
        debugPrint('TtsAssetLoader: load model.onnx: $e2');
      }
    }

    // 2) Токены
    try {
      final data = await rootBundle.load('${prefix}tokens.txt');
      final file = File('${voiceDir.path}/tokens.txt');
      await file.writeAsBytes(data.buffer.asUint8List());
      copied++;
    } catch (_) {}

    // 3) Лексикон опционален
    try {
      final data = await rootBundle.load('${prefix}lexicon.txt');
      final file = File('${voiceDir.path}/lexicon.txt');
      await file.writeAsBytes(data.buffer.asUint8List());
      copied++;
    } catch (_) {}

    if (copied < 2) return null;
    return voiceDir.path;
  } catch (e, st) {
    debugPrint('TtsAssetLoader: copyVoiceAssetsToTemp failed: $e');
    debugPrint('TtsAssetLoader: $st');
    return null;
  }
}
