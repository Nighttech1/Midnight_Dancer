import 'dart:typed_data';

import 'package:audio_session/audio_session.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'package:midnight_dancer/core/utils/audio_source_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/audio_source_platform_io.dart'
    as audio_platform;
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:path/path.dart' as p;

/// Состояние воспроизведения в разделе «Музыка» (плеер живёт дольше, чем экран вкладки).
class MusicPlaybackState {
  const MusicPlaybackState({
    this.playingSongId,
    this.loadedAudioSongId,
  });

  final String? playingSongId;
  final String? loadedAudioSongId;

  MusicPlaybackState copyWith({
    String? playingSongId,
    String? loadedAudioSongId,
    bool clearPlayingSongId = false,
    bool clearLoadedAudioSongId = false,
  }) {
    return MusicPlaybackState(
      playingSongId: clearPlayingSongId ? null : (playingSongId ?? this.playingSongId),
      loadedAudioSongId: clearLoadedAudioSongId ? null : (loadedAudioSongId ?? this.loadedAudioSongId),
    );
  }
}

class MusicPlaybackNotifier extends StateNotifier<MusicPlaybackState> {
  MusicPlaybackNotifier(this._ref) : super(const MusicPlaybackState()) {
    _player.playerStateStream.listen((st) {
      if (st.processingState == ProcessingState.completed) {
        state = state.copyWith(clearPlayingSongId: true, clearLoadedAudioSongId: true);
      }
    });
  }

  final Ref _ref;
  final AudioPlayer _player = AudioPlayer();

  AudioPlayer get player => _player;

  Future<void> activateAudioSession() async {
    try {
      final session = await AudioSession.instance;
      await session.setActive(true);
    } catch (_) {}
  }

  Future<void> loadSongAudioSource(Song song) async {
    final notifier = _ref.read(appDataNotifierProvider.notifier);
    await _player.stop();
    final path = await notifier.getSongFilePath(song);
    if (!kIsWeb && path != null) {
      await _player.setAudioSource(audio_platform.createFileAudioSource(path));
    } else {
      final bytes = await notifier.loadSongBytes(song);
      if (bytes != null && bytes.isNotEmpty) {
        final ext = song.fileName.contains('.') ? p.extension(song.fileName).replaceFirst('.', '') : 'mp3';
        final mime = ext == 'mp3' ? 'audio/mpeg' : (ext == 'm4a' ? 'audio/mp4' : 'audio/wav');
        await _player.setAudioSource(AudioSource.uri(Uri.dataFromBytes(bytes, mimeType: mime)));
      } else {
        throw Exception('Нет данных трека');
      }
    }
    await _player.setSpeed(song.playbackSpeed.clamp(0.2, 1.5));
    state = state.copyWith(loadedAudioSongId: song.id);
  }

  Future<void> playOrPause(Song song) async {
    if (state.playingSongId == song.id) {
      if (_player.playing) {
        await _player.pause();
      } else {
        await activateAudioSession();
        await _player.play();
      }
      return;
    }

    await loadSongAudioSource(song);
    state = state.copyWith(playingSongId: song.id);
    await activateAudioSession();
    await _player.play();
  }

  Future<void> stopPlayback() async {
    await _player.stop();
    state = state.copyWith(clearPlayingSongId: true, clearLoadedAudioSongId: true);
  }

  /// Стоп в диалоге редактирования: сброс воспроизведения и загрузка источника для превью/ползунка.
  Future<void> stopPlaybackAndReloadForEdit(Song song) async {
    await _player.stop();
    state = state.copyWith(clearPlayingSongId: true);
    await loadSongAudioSource(song);
    await _player.pause();
    await _player.seek(Duration.zero);
  }

  /// После удаления трека или если id больше нет в данных.
  Future<void> onSongRemovedFromData(String songId) async {
    if (state.playingSongId == songId) {
      await _player.stop();
      state = state.copyWith(clearPlayingSongId: true, clearLoadedAudioSongId: true);
    } else if (state.loadedAudioSongId == songId) {
      await _player.stop();
      state = state.copyWith(clearLoadedAudioSongId: true);
    }
  }

  Future<void> clearPlaybackIfSongMissing(Iterable<String> existingSongIds) async {
    final id = state.playingSongId;
    if (id != null && !existingSongIds.contains(id)) {
      await _player.stop();
      state = state.copyWith(clearPlayingSongId: true, clearLoadedAudioSongId: true);
    }
  }

  /// Длительность файла при добавлении трека (временно грузит источник в общий плеер).
  Future<double> probeDurationSecFromPath(String path) async {
    try {
      final d = await _player.setAudioSource(audio_platform.createFileAudioSource(path));
      final sec = (d?.inMilliseconds ?? 0) / 1000.0;
      await _player.stop();
      state = state.copyWith(clearLoadedAudioSongId: true);
      return sec;
    } catch (_) {
      return 0;
    }
  }

  Future<double> probeDurationSecFromBytes(Uint8List bytes, String ext) async {
    try {
      final mime = ext == 'mp3' ? 'audio/mpeg' : (ext == 'm4a' ? 'audio/mp4' : 'audio/wav');
      final uri = Uri.dataFromBytes(bytes, mimeType: mime);
      final d = await _player.setAudioSource(AudioSource.uri(uri));
      final sec = (d?.inMilliseconds ?? 0) / 1000.0;
      await _player.stop();
      state = state.copyWith(clearLoadedAudioSongId: true);
      return sec;
    } catch (_) {
      return 0;
    }
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }
}

final musicPlaybackProvider =
    StateNotifierProvider<MusicPlaybackNotifier, MusicPlaybackState>((ref) {
  return MusicPlaybackNotifier(ref);
});
