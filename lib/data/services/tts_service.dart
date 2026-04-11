import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';

import '../../core/app_flavor.dart';
import 'tts_asset_loader_stub.dart' if (dart.library.io) 'tts_asset_loader_io.dart' as asset_loader;
import 'tts_sherpa_stub.dart';
import 'tts_sherpa_stub.dart' if (dart.library.io) 'tts_sherpa_io.dart' as sherpa_impl;

/// Голос TTS: id для путей к моделям и отображения.
class TtsVoice {
  const TtsVoice({required this.id, required this.displayName});

  final String id;
  final String displayName;

  static const ruslan = TtsVoice(id: 'ruslan', displayName: 'Руслан');
  static const irina = TtsVoice(id: 'irina', displayName: 'Ирина');
  static const kamila = TtsVoice(id: 'kamila', displayName: 'Камила');

  static const all = [ruslan, irina, kamila];
}

/// Сервис TTS: офлайн (Sherpa-ONNX) с fallback на системный (flutter_tts).
class TtsService {
  TtsService._();

  static TtsService? _instance;
  static TtsService get instance => _instance ??= TtsService._();

  final FlutterTts _flutterTts = FlutterTts();
  final ValueNotifier<double?> _loadProgress = ValueNotifier<double?>(null);
  final ValueNotifier<bool> _useFlutterTts = ValueNotifier<bool>(true);

  /// Прогресс загрузки модели (0.0..1.0 или null).
  ValueNotifier<double?> get loadProgress => _loadProgress;

  /// true, если используется системный TTS (fallback).
  ValueNotifier<bool> get useFlutterTts => _useFlutterTts;

  /// Доступные голоса в зависимости от flavor.
  /// Lite: только Руслан. Standard: Руслан + Ирина. Full: Руслан + Ирина + Камила. English: Камила + Руслан.
  List<TtsVoice> get availableVoices {
    if (AppFlavor.isFull) return [TtsVoice.ruslan, TtsVoice.irina, TtsVoice.kamila];
    if (AppFlavor.isStandard) return [TtsVoice.ruslan, TtsVoice.irina];
    if (AppFlavor.isEnglish) return [TtsVoice.kamila, TtsVoice.ruslan];
    return [TtsVoice.ruslan];
  }

  TtsSherpaEngine? _sherpaTts;
  bool _flutterTtsInitialized = false;

  /// Текущий загруженный голос (null, если ещё не инициализировали ни один).
  TtsVoice? _currentVoice;
  TtsVoice? get currentVoice => _currentVoice;

  /// Инициализировать голос. При успехе Sherpa — офлайн, иначе fallback на flutter_tts.
  /// [onProgress] вызывается с 0.0..1.0 во время загрузки.
  Future<void> initVoice(TtsVoice voice, {void Function(double)? onProgress}) async {
    _loadProgress.value = 0.0;
    onProgress?.call(0.0);

    if (kIsWeb) {
      await _initFlutterTts();
      _currentVoice = voice;
      _loadProgress.value = 1.0;
      onProgress?.call(1.0);
      _loadProgress.value = null;
      return;
    }

    try {
      // English-сборка использует голоса из папки full (kamila, ruslan)
      final flavor = AppFlavor.isEnglish ? 'full' : AppFlavor.name;
      _loadProgress.value = 0.2;
      onProgress?.call(0.2);

      final dir = await asset_loader.copyVoiceAssetsToTemp(flavor, voice.id);
      if (dir == null || dir.isEmpty) {
        await _initFlutterTts();
        _useFlutterTts.value = true;
        _currentVoice = voice;
        _loadProgress.value = 1.0;
        onProgress?.call(1.0);
        _loadProgress.value = null;
        return;
      }

      _loadProgress.value = 0.5;
      onProgress?.call(0.5);

      final dataDir = await asset_loader.copyEspeakNgDataToTemp();
      final tts = await sherpa_impl.createSherpaTts(dir, dataDir: dataDir);
      _loadProgress.value = 0.9;
      onProgress?.call(0.9);

      if (tts != null) {
        _sherpaTts = tts;
        _useFlutterTts.value = false;
        _currentVoice = voice;
      } else {
        await _initFlutterTts();
        _useFlutterTts.value = true;
        _currentVoice = voice;
      }
    } catch (e, st) {
      debugPrint('TtsService: initVoice failed ($e), using flutter_tts');
      debugPrint('TtsService: $st');
      await _initFlutterTts();
      _useFlutterTts.value = true;
      _currentVoice = voice;
    } finally {
      _loadProgress.value = 1.0;
      onProgress?.call(1.0);
      _loadProgress.value = null;
    }
  }

  Future<void> _initFlutterTts() async {
    if (_flutterTtsInitialized) return;
    await _flutterTts.setLanguage('ru-RU');
    await _flutterTts.setSpeechRate(0.5);
    await _flutterTts.setPitch(1.0);
    _flutterTtsInitialized = true;
  }

  /// Синтез речи: [text], [speed] 0.0..1.0 (по умолчанию 0.5), [pitch] 0.5..2.0 (по умолчанию 1.0), [volume] 0.0..1.0 (по умолчанию 1.0).
  Future<void> speak(String text, {double speed = 0.5, double pitch = 1.0, double volume = 1.0}) async {
    if (text.trim().isEmpty) return;
    final vol = volume.clamp(0.0, 1.0);
    if (_useFlutterTts.value) {
      await _flutterTts.setSpeechRate(speed.clamp(0.0, 1.0));
      await _flutterTts.setPitch(pitch.clamp(0.5, 2.0));
      await _flutterTts.setVolume(vol);
      await _flutterTts.speak(text);
      return;
    }
    if (_sherpaTts != null) {
      try {
        await _sherpaTts!.speak(text, speed: speed, pitch: pitch, volume: vol);
      } catch (e) {
        debugPrint('TtsService: sherpa speak failed ($e), falling back to flutter_tts');
        await _initFlutterTts();
        _useFlutterTts.value = true;
        await _flutterTts.setSpeechRate(speed.clamp(0.0, 1.0));
        await _flutterTts.setPitch(pitch.clamp(0.5, 2.0));
        await _flutterTts.setVolume(vol);
        await _flutterTts.speak(text);
      }
      return;
    }
    await _initFlutterTts();
    await _flutterTts.setSpeechRate(speed.clamp(0.0, 1.0));
    await _flutterTts.setPitch(pitch.clamp(0.5, 2.0));
    await _flutterTts.setVolume(vol);
    await _flutterTts.speak(text);
  }

  Future<void> stop() async {
    await _flutterTts.stop();
    await _sherpaTts?.stop();
  }
}
