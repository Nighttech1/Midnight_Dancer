import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/audio_source_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/audio_source_platform_io.dart'
    as audio_platform;
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/tts_service.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/ui/screens/trainer/trainer_screen.dart';

/// Параметры сессии тренировки для перехода на экран сессии.
class TrainerSessionParams {
  const TrainerSessionParams({
    required this.mode,
    this.styleId,
    this.songId,
    this.choreography,
    required this.voice,
    required this.speed,
    required this.ducking,
    required this.intervalSec,
    required this.level,
    required this.trackStartSec,
    required this.trackEndSec,
  });

  final TrainerMode mode;
  final String? styleId;
  final String? songId;
  final Choreography? choreography;
  final TtsVoice voice;
  final double speed;
  final bool ducking;
  final double intervalSec;
  final String level;
  final int trackStartSec;
  final int trackEndSec;
}

/// Экран активной тренировки: обратный отсчёт, большие карточки с элементами, громкости, «Закончить».
class TrainerSessionScreen extends ConsumerStatefulWidget {
  const TrainerSessionScreen({super.key, required this.params});

  final TrainerSessionParams params;

  @override
  ConsumerState<TrainerSessionScreen> createState() => _TrainerSessionScreenState();
}

class _TrainerSessionScreenState extends ConsumerState<TrainerSessionScreen> {
  int _countdown = 3;
  String _currentMoveName = '';
  double _musicVolume = 1.0;
  double _voiceVolume = 1.0;
  bool _active = true;

  Timer? _randomTimer;
  StreamSubscription<Duration>? _positionSub;
  StreamSubscription<PlayerState>? _playerStateSub;
  final AudioPlayer _musicPlayer = AudioPlayer();
  final Set<double> _spokenTimelinePoints = {};
  Future<void>? _musicPreloadFuture;
  final Random _random = Random();
  int? _lastRandomMoveIndex;

  @override
  void initState() {
    super.initState();
    if (widget.params.mode == TrainerMode.random && widget.params.songId != null) {
      _musicPreloadFuture = _preloadMusicForRandom();
    } else if (widget.params.mode == TrainerMode.choreography && widget.params.choreography != null) {
      _musicPreloadFuture = _preloadMusicForChoreography();
    }
    WidgetsBinding.instance.addPostFrameCallback((_) => _runCountdown());
  }

  Future<void> _runCountdown() async {
    if (_musicPreloadFuture != null) {
      await _musicPreloadFuture!.timeout(
        const Duration(seconds: 6),
        onTimeout: () {},
      );
    }
    if (!mounted) return;
    for (int i = 3; i >= 1 && mounted; i--) {
      setState(() => _countdown = i);
      await Future.delayed(const Duration(seconds: 1));
    }
    if (!mounted) return;
    setState(() => _countdown = 0);
    _startSession();
  }

  Future<void> _preloadMusicForRandom() async {
    final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
    final params = widget.params;
    final song = params.songId != null
        ? data.songs.cast<Song?>().firstWhere(
              (s) => s?.id == params.songId,
              orElse: () => null,
            )
        : null;
    if (song == null) return;
    final path = await ref.read(appDataNotifierProvider.notifier).getSongFilePath(song);
    if (path == null || path.isEmpty || !mounted) return;
    await _musicPlayer.setAudioSource(audio_platform.createFileAudioSource(path));
    await _musicPlayer.setSpeed(song.playbackSpeed.clamp(0.2, 1.5));
    if (!mounted) return;
    final duration = _musicPlayer.duration ?? Duration.zero;
    final totalSec = duration.inSeconds;
    if (totalSec > 0) {
      final endSec = params.trackEndSec <= 0 ? totalSec : params.trackEndSec.clamp(1, totalSec);
      final startSec = params.trackStartSec.clamp(0, endSec - 1);
      if (startSec > 0 || (params.trackEndSec > 0 && endSec < totalSec)) {
        await _musicPlayer.setClip(
          start: Duration(seconds: startSec),
          end: Duration(seconds: endSec),
        );
      }
    }
    if (!mounted) return;
    try {
      await _musicPlayer.processingStateStream
          .firstWhere((s) => s == ProcessingState.ready)
          .timeout(const Duration(seconds: 5));
    } catch (_) {}
  }

  Future<void> _preloadMusicForChoreography() async {
    final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
    final choreo = widget.params.choreography!;
    final song = data.songs.cast<Song?>().firstWhere(
          (s) => s?.id == choreo.songId,
          orElse: () => null,
        );
    if (song == null) return;
    final path = await ref.read(appDataNotifierProvider.notifier).getSongFilePath(song);
    if (path == null || path.isEmpty || !mounted) return;
    await _musicPlayer.setAudioSource(audio_platform.createFileAudioSource(path));
    await _musicPlayer.setSpeed(song.playbackSpeed.clamp(0.2, 1.5));
    if (!mounted) return;
    await _musicPlayer.setClip(
      start: Duration(milliseconds: (choreo.startTime * 1000).round()),
      end: Duration(milliseconds: (choreo.endTime * 1000).round()),
    );
    if (!mounted) return;
    try {
      await _musicPlayer.processingStateStream
          .firstWhere((s) => s == ProcessingState.ready)
          .timeout(const Duration(seconds: 5));
    } catch (_) {}
  }

  Future<void> _startSession() async {
    final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
    final tts = ref.read(ttsServiceProvider);
    final params = widget.params;

    if (params.mode == TrainerMode.random) {
      final style = params.styleId != null
          ? data.danceStyles.cast<DanceStyle?>().firstWhere(
                (s) => s?.id == params.styleId,
                orElse: () => null,
              )
          : null;
      final song = params.songId != null
          ? data.songs.cast<Song?>().firstWhere(
                (s) => s?.id == params.songId,
                orElse: () => null,
              )
          : null;
      if (style == null || style.moves.isEmpty) {
        if (mounted) _exit();
        return;
      }
      var moves = style.moves;
      if (params.level != 'All') moves = moves.where((m) => m.level == params.level).toList();
      if (moves.isEmpty) moves = style.moves;

      final random = moves;
      final intervalMs = (params.intervalSec * 1000).round();

      int pickRandomIndex() {
        if (random.length == 1) return 0;
        int index = _random.nextInt(random.length);
        if (random.length > 1 && index == _lastRandomMoveIndex) {
          index = (index + 1) % random.length;
        }
        _lastRandomMoveIndex = index;
        return index;
      }

      void tick() async {
        if (!_active || !mounted) return;
        final move = random[pickRandomIndex()];
        if (!mounted) return;
        setState(() => _currentMoveName = move.name);
        if (!_active || !mounted) return;
        if (params.ducking) _musicPlayer.setVolume(_musicVolume * 0.3);
        await tts.speak(move.name, speed: params.speed, volume: _voiceVolume);
        if (!_active) return;
        if (params.ducking && mounted) _musicPlayer.setVolume(_musicVolume);
      }

      if (song != null) {
        if (_musicPreloadFuture != null) {
          await _musicPlayer.setSpeed(song.playbackSpeed.clamp(0.2, 1.5));
          _musicPlayer.setVolume(_musicVolume);
          _musicPlayer.setLoopMode(LoopMode.one);
          _musicPlayer.play();
        } else {
          final path = await ref.read(appDataNotifierProvider.notifier).getSongFilePath(song);
          if (path != null && path.isNotEmpty && _active && mounted) {
            await _musicPlayer.setAudioSource(audio_platform.createFileAudioSource(path));
            if (!_active) return;
            final duration = _musicPlayer.duration ?? Duration.zero;
            final totalSec = duration.inSeconds;
            if (totalSec > 0) {
              final endSec = params.trackEndSec <= 0 ? totalSec : params.trackEndSec.clamp(1, totalSec);
              final startSec = params.trackStartSec.clamp(0, endSec - 1);
              if (startSec > 0 || (params.trackEndSec > 0 && endSec < totalSec)) {
                await _musicPlayer.setClip(
                  start: Duration(seconds: startSec),
                  end: Duration(seconds: endSec),
                );
              }
            }
            if (!_active) return;
            await _musicPlayer.setSpeed(song.playbackSpeed.clamp(0.2, 1.5));
            _musicPlayer.setVolume(_musicVolume);
            _musicPlayer.setLoopMode(LoopMode.one);
            _musicPlayer.play();
          }
        }
      }
      tick();
      _randomTimer = Timer.periodic(Duration(milliseconds: intervalMs), (_) => tick());
      return;
    }

    if (params.mode == TrainerMode.choreography && params.choreography != null) {
      final choreo = params.choreography!;
      final song = data.songs.cast<Song?>().firstWhere(
            (s) => s?.id == choreo.songId,
            orElse: () => null,
          );
      if (song == null) {
        if (mounted) _exit();
        return;
      }

      final style = data.danceStyles.cast<DanceStyle?>().firstWhere(
            (s) => s?.id == choreo.styleId,
            orElse: () => null,
          );
      final moveNames = <String, String>{};
      if (style != null) for (final m in style.moves) moveNames[m.id] = m.name;

      if (_musicPreloadFuture != null) {
        await _musicPreloadFuture;
        if (!_active) return;
        await _musicPlayer.setSpeed(song.playbackSpeed.clamp(0.2, 1.5));
      } else {
        final path = await ref.read(appDataNotifierProvider.notifier).getSongFilePath(song);
        if (path == null || path.isEmpty) {
          if (mounted) _exit();
          return;
        }
        await _musicPlayer.setAudioSource(audio_platform.createFileAudioSource(path));
        await _musicPlayer.setSpeed(song.playbackSpeed.clamp(0.2, 1.5));
        await _musicPlayer.setClip(
          start: Duration(milliseconds: (choreo.startTime * 1000).round()),
          end: Duration(milliseconds: (choreo.endTime * 1000).round()),
        );
      }
      _musicPlayer.setVolume(_musicVolume);
      _musicPlayer.play();

      final sortedTimes = choreo.timeline.keys.toList()..sort();
      _positionSub = _musicPlayer.positionStream.listen((pos) async {
        if (!_active || !mounted) return;
        final sec = choreo.startTime + pos.inMilliseconds / 1000.0;
        for (final t in sortedTimes) {
          if (t <= sec && !_spokenTimelinePoints.contains(t)) {
            _spokenTimelinePoints.add(t);
            final moveId = choreo.timeline[t];
            final name = moveNames[moveId] ?? moveId ?? '';
            if (name.isEmpty) continue;
            if (!_active || !mounted) return;
            setState(() => _currentMoveName = name);
            if (params.ducking) _musicPlayer.setVolume(_musicVolume * 0.3);
            await tts.speak(name, speed: params.speed, volume: _voiceVolume);
            if (!_active) return;
            if (params.ducking && mounted) _musicPlayer.setVolume(_musicVolume);
          }
        }
      });
      _playerStateSub = _musicPlayer.playerStateStream.listen((state) {
        if (state.processingState == ProcessingState.completed && mounted) _exit();
      });
    }
  }

  void _stopAndExit() {
    _active = false;
    ref.read(ttsServiceProvider).stop();
    _randomTimer?.cancel();
    _randomTimer = null;
    _positionSub?.cancel();
    _positionSub = null;
    _playerStateSub?.cancel();
    _playerStateSub = null;
    _musicPlayer.stop();
    if (mounted) Navigator.of(context).pop();
  }

  void _exit() {
    _active = false;
    ref.read(ttsServiceProvider).stop();
    _randomTimer?.cancel();
    _randomTimer = null;
    _positionSub?.cancel();
    _positionSub = null;
    _playerStateSub?.cancel();
    _playerStateSub = null;
    _musicPlayer.stop();
    if (mounted) Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _active = false;
    ref.read(ttsServiceProvider).stop();
    _randomTimer?.cancel();
    _positionSub?.cancel();
    _playerStateSub?.cancel();
    _musicPlayer.stop();
    _musicPlayer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        _stopAndExit();
      },
      child: Scaffold(
        backgroundColor: AppColors.background,
        body: SafeArea(
          child: _countdown > 0
              ? _buildCountdown()
              : _buildSession(),
        ),
      ),
    );
  }

  Widget _buildCountdown() {
    return Center(
      child: Text(
        '$_countdown',
        style: const TextStyle(fontSize: 120, fontWeight: FontWeight.bold, color: Colors.white),
      ),
    );
  }

  Widget _buildSession() {
    final str = ref.watch(appStringsProvider);
    return Center(
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Card(
                color: AppColors.card,
                elevation: 8,
                shape: RoundedRectangleBorder(borderRadius: AppRadius.radiusXl),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 48),
                  child: Text(
                    _currentMoveName.isEmpty ? '—' : _currentMoveName,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: 280,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(str.musicVolume, style: TextStyle(color: AppColors.textSecondary, fontSize: 14)),
                    Slider(
                      value: _musicVolume,
                      min: 0.0,
                      max: 1.0,
                      activeColor: AppColors.accent,
                      onChanged: (v) {
                        setState(() => _musicVolume = v);
                        _musicPlayer.setVolume(v);
                      },
                    ),
                    Text(str.voiceVolume, style: const TextStyle(color: AppColors.textSecondary, fontSize: 14)),
                    Slider(
                      value: _voiceVolume,
                      min: 0.0,
                      max: 1.0,
                      activeColor: AppColors.accent,
                      onChanged: (v) => setState(() => _voiceVolume = v),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: _stopAndExit,
                      icon: const Icon(Icons.stop, size: 24),
                      label: Text(str.finish),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.red,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
