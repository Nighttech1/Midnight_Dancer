import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sherpa_onnx/sherpa_onnx.dart';

import 'tts_sherpa_stub.dart';

bool _bindingsInited = false;

void _initSherpaOnce() {
  if (_bindingsInited) return;
  try {
    initBindings();
    _bindingsInited = true;
  } catch (e) {
    debugPrint('TtsSherpaIO: initBindings failed ($e)');
  }
}

/// Пишет WAV (16-bit PCM) из float32 сэмплов во временный файл. Возвращает путь или null.
Future<String?> _writeWavFile(Float32List samples, int sampleRate) async {
  if (samples.isEmpty) return null;
  const numChannels = 1;
  const bitsPerSample = 16;
  final byteRate = sampleRate * numChannels * (bitsPerSample >> 3);
  final dataSize = samples.length * 2; // 16 bit = 2 bytes per sample
  final fileSize = 36 + dataSize;

  final header = ByteData(44);
  header.setUint8(0, 0x52); // 'R'
  header.setUint8(1, 0x49); // 'I'
  header.setUint8(2, 0x46); // 'F'
  header.setUint8(3, 0x46); // 'F'
  header.setUint32(4, fileSize, Endian.little);
  header.setUint8(8, 0x57); // 'W'
  header.setUint8(9, 0x41); // 'A'
  header.setUint8(10, 0x56); // 'V'
  header.setUint8(11, 0x45); // 'E'
  header.setUint8(12, 0x66); // 'f'
  header.setUint8(13, 0x6d); // 'm'
  header.setUint8(14, 0x74); // 't'
  header.setUint8(15, 0x20); // ' '
  header.setUint32(16, 16, Endian.little); // subchunk1 size
  header.setUint16(20, 1, Endian.little);  // PCM
  header.setUint16(22, numChannels, Endian.little);
  header.setUint32(24, sampleRate, Endian.little);
  header.setUint32(28, byteRate, Endian.little);
  header.setUint16(32, (numChannels * bitsPerSample) >> 3, Endian.little);
  header.setUint16(34, bitsPerSample, Endian.little);
  header.setUint8(36, 0x64); // 'd'
  header.setUint8(37, 0x61); // 'a'
  header.setUint8(38, 0x74); // 't'
  header.setUint8(39, 0x61); // 'a'
  header.setUint32(40, dataSize, Endian.little);

  final pcm = ByteData(samples.length * 2);
  for (var i = 0; i < samples.length; i++) {
    var s = (samples[i] * 32767).round().clamp(-32768, 32767);
    pcm.setInt16(i * 2, s, Endian.little);
  }

  final dir = await getTemporaryDirectory();
  final file = File('${dir.path}/tts_sherpa_play_${DateTime.now().millisecondsSinceEpoch}.wav');
  final out = file.openWrite();
  out.add(header.buffer.asUint8List());
  out.add(pcm.buffer.asUint8List());
  await out.close();
  return file.path;
}

/// Реализация Sherpa-ONNX для мобильных/десктоп платформ.
/// Загружает VITS (Piper) из [modelDir]: model.onnx, tokens.txt; [dataDir] — espeak-ng-data для фонемизации.
Future<TtsSherpaEngine?> createSherpaTts(String modelDir, {String? dataDir}) async {
  try {
    _initSherpaOnce();

    final modelPath = '$modelDir/model.onnx';
    final tokensPath = '$modelDir/tokens.txt';
    final lexiconFile = File('$modelDir/lexicon.txt');
    final lexiconPath = await lexiconFile.exists() ? lexiconFile.path : '';
    final dataDirPath = (dataDir != null && dataDir.isNotEmpty) ? dataDir : '';

    if (!await File(modelPath).exists() || !await File(tokensPath).exists()) {
      debugPrint('TtsSherpaIO: missing model/tokens in $modelDir');
      return null;
    }

    final config = OfflineTtsConfig(
      model: OfflineTtsModelConfig(
        vits: OfflineTtsVitsModelConfig(
          model: modelPath,
          tokens: tokensPath,
          lexicon: lexiconPath,
          dataDir: dataDirPath,
        ),
      ),
    );

    final tts = OfflineTts(config);
    return _SherpaEngine(tts);
  } catch (e, st) {
    debugPrint('TtsSherpaIO: $e');
    debugPrint('TtsSherpaIO stack: $st');
    return null;
  }
}

class _SherpaEngine implements TtsSherpaEngine {
  _SherpaEngine(this._tts);

  final OfflineTts _tts;
  final AudioPlayer _player = AudioPlayer();

  @override
  Future<void> speak(String text, {required double speed, required double pitch, double volume = 1.0}) async {
    if (text.trim().isEmpty) return;
    try {
      // Sherpa VITS поддерживает только speed. Тональность (pitch) имитируем через скорость:
      // выше pitch -> быстрее воспроизведение (звучит выше), ниже pitch -> медленнее.
      final baseSpeed = 0.5 + speed.clamp(0.0, 1.0);
      final pitchFactor = pitch.clamp(0.5, 2.0);
      final sherpaSpeed = baseSpeed * pitchFactor;
      final audio = _tts.generate(text: text, speed: sherpaSpeed);
      if (audio.samples.isEmpty) return;

      const double volumeGain = 1.6;
      final boosted = Float32List(audio.samples.length);
      for (var i = 0; i < audio.samples.length; i++) {
        boosted[i] = (audio.samples[i] * volumeGain).clamp(-1.0, 1.0);
      }

      final path = await _writeWavFile(boosted, audio.sampleRate);
      if (path == null) return;

      final vol = (volume.clamp(0.0, 1.0) * 1.2).clamp(0.0, 2.0);
      _player.setVolume(vol);
      await _player.setAudioSource(AudioSource.file(path));
      await _player.play();
      await _player.processingStateStream.firstWhere(
        (s) => s == ProcessingState.completed || s == ProcessingState.idle,
      );
      try {
        await File(path).delete();
      } catch (_) {}
    } catch (e) {
      debugPrint('TtsSherpaEngine.speak: $e');
      rethrow;
    }
  }

  @override
  Future<void> stop() async {
    await _player.stop();
  }
}
