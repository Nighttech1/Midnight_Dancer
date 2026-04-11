import 'dart:typed_data';

import 'package:flutter/foundation.dart' show debugPrint, debugPrintStack;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/app_ui_language.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/choreography_import_merge.dart';
import 'package:midnight_dancer/data/services/choreography_package.dart';
import 'package:midnight_dancer/data/services/full_backup_export_collect_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/data/services/full_backup_export_collect_io.dart'
    as full_backup_collect;
import 'package:midnight_dancer/data/services/full_backup_import_merge.dart';
import 'package:midnight_dancer/data/services/dance_reminder_config.dart';
import 'package:midnight_dancer/data/services/dance_reminder_notification_copy.dart';
import 'package:midnight_dancer/data/services/dance_reminder_scheduler.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';
import 'package:midnight_dancer/data/services/storage_service.dart';
import 'package:midnight_dancer/data/services/tts_service.dart';

final storageServiceProvider = Provider<StorageService>((ref) {
  return StorageService.instance;
});

/// Состояние и операции с AppData.
class AppDataNotifier extends StateNotifier<AsyncValue<AppData>> {
  AppDataNotifier(this._storage) : super(const AsyncValue.loading()) {
    _load();
  }

  final StorageService _storage;

  /// Напоминания не должны ломать загрузку каталога: сбой плагина уведомлений ≠ потеря данных на диске.
  Future<void> _syncDanceReminders(Map<String, dynamic> settings) async {
    try {
      await DanceReminderScheduler.init();
      final reminderCfg = DanceReminderConfig.fromSettings(settings);
      final lang = AppUiLanguage.fromSettings(settings);
      final s = AppStrings(lang);
      final body = await DanceReminderNotificationCopy.randomBody(fallback: s.danceReminderNotifBody);
      await DanceReminderScheduler.apply(
        reminderCfg,
        notificationTitle: s.danceReminderNotifTitle,
        notificationBody: body,
      );
    } catch (e, st) {
      debugPrint('DanceReminderScheduler: sync failed, app data unchanged ($e)');
      debugPrintStack(stackTrace: st);
    }
  }

  Future<void> _load() async {
    state = const AsyncValue.loading();
    try {
      final data = await _storage.loadAppData();
      state = AsyncValue.data(data);
      await _syncDanceReminders(data.settings);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<void> save(AppData data) async {
    await _storage.saveAppData(data);
    state = AsyncValue.data(data);
  }

  /// Сохранить настройки напоминаний и обновить расписание уведомлений.
  Future<void> saveDanceReminderConfig(DanceReminderConfig config) async {
    final current = state.valueOrNull ?? AppData();
    final merged = Map<String, dynamic>.from(current.settings)..addAll(config.toSettingsEntries());
    await save(current.copyWith(settings: merged));
    await _syncDanceReminders(merged);
  }

  Future<void> saveUiLanguage(AppUiLanguage lang) async {
    final current = state.valueOrNull ?? AppData();
    final merged = Map<String, dynamic>.from(current.settings)..addAll(lang.toSettingsEntry());
    await save(current.copyWith(settings: merged));
    await _syncDanceReminders(merged);
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
      currentMoveId: style.currentMoveId == moveId ? null : style.currentMoveId,
    );
    await updateStyle(updatedStyle);
  }

  Future<void> updateMoveMastery(String styleId, String moveId, int percent) async {
    final current = state.valueOrNull ?? AppData();
    final style = current.danceStyles.firstWhere((s) => s.id == styleId);
    final p = percent.clamp(0, 100);
    final moves = style.moves
        .map((m) => m.id == moveId ? m.copyWith(masteryPercent: p) : m)
        .toList();
    await updateStyle(style.copyWith(moves: moves));
  }

  Future<void> setDanceStyleCurrentMove(String styleId, String? moveId) async {
    final current = state.valueOrNull ?? AppData();
    final style = current.danceStyles.firstWhere((s) => s.id == styleId);
    await updateStyle(style.copyWith(currentMoveId: moveId));
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

  Future<void> addSong(Song song, Uint8List audioBytes) async {
    final current = state.valueOrNull ?? AppData();
    final ext = _extensionFromFileName(song.fileName);
    await _storage.saveMediaFile(song.id, audioBytes, 'music', musicExtension: ext);
    final updated = current.copyWith(songs: [...current.songs, song]);
    await save(updated);
  }

  Future<void> updateSong(Song song, {Uint8List? audioBytes}) async {
    final current = state.valueOrNull ?? AppData();
    final idx = current.songs.indexWhere((s) => s.id == song.id);
    if (idx < 0) return;
    if (audioBytes != null && audioBytes.isNotEmpty) {
      final ext = _extensionFromFileName(song.fileName);
      await _storage.saveMediaFile(song.id, audioBytes, 'music', musicExtension: ext);
    }
    final updated = current.copyWith(
      songs: [
        ...current.songs.take(idx),
        song,
        ...current.songs.skip(idx + 1),
      ],
    );
    await save(updated);
  }

  Future<void> deleteSong(String songId) async {
    final current = state.valueOrNull ?? AppData();
    final song = current.songs.where((s) => s.id == songId).toList();
    if (song.isNotEmpty) {
      final ext = _extensionFromFileName(song.first.fileName);
      await _storage.deleteMediaFile(songId, 'music', musicExtension: ext);
    }
    final updated = current.copyWith(
      songs: current.songs.where((s) => s.id != songId).toList(),
    );
    await save(updated);
  }

  String _extensionFromFileName(String fileName) {
    final i = fileName.lastIndexOf('.');
    if (i >= 0 && i < fileName.length - 1) return fileName.substring(i + 1).toLowerCase();
    return 'mp3';
  }

  Future<String?> getSongFilePath(Song song) async {
    final ext = _extensionFromFileName(song.fileName);
    return _storage.getMediaFilePath(song.id, 'music', ext);
  }

  Future<Uint8List?> loadSongBytes(Song song) async {
    final ext = _extensionFromFileName(song.fileName);
    return _storage.loadMediaFile(song.id, 'music', musicExtension: ext);
  }

  Future<void> addChoreography(Choreography choreography) async {
    final current = state.valueOrNull ?? AppData();
    final updated = current.copyWith(
      choreographies: [...current.choreographies, choreography],
    );
    await save(updated);
  }

  Future<void> updateChoreography(Choreography choreography) async {
    final current = state.valueOrNull ?? AppData();
    final idx = current.choreographies.indexWhere((c) => c.id == choreography.id);
    if (idx < 0) return;
    final updated = current.copyWith(
      choreographies: [
        ...current.choreographies.take(idx),
        choreography,
        ...current.choreographies.skip(idx + 1),
      ],
    );
    await save(updated);
  }

  Future<void> deleteChoreography(String choreographyId) async {
    final current = state.valueOrNull ?? AppData();
    final updated = current.copyWith(
      choreographies: current.choreographies.where((c) => c.id != choreographyId).toList(),
    );
    await save(updated);
  }

  /// ZIP для «Поделиться»: музыка, хореография, стиль с карточками без видео.
  Future<Uint8List?> buildChoreographyShareZip(String choreographyId) async {
    final current = state.valueOrNull ?? AppData();
    Choreography? choreo;
    for (final c in current.choreographies) {
      if (c.id == choreographyId) {
        choreo = c;
        break;
      }
    }
    if (choreo == null) return null;
    Song? song;
    for (final s in current.songs) {
      if (s.id == choreo.songId) {
        song = s;
        break;
      }
    }
    DanceStyle? style;
    for (final st in current.danceStyles) {
      if (st.id == choreo.styleId) {
        style = st;
        break;
      }
    }
    if (song == null || style == null) return null;
    final musicBytes = await loadSongBytes(song);
    if (musicBytes == null || musicBytes.isEmpty) return null;
    final moves = ChoreographyPackage.movesForExport(choreo, style.moves);
    return ChoreographyPackage.encode(
      choreography: choreo,
      song: song,
      styleName: style.name,
      moves: moves,
      timelineResolverMoves: style.moves,
      musicBytes: musicBytes,
    );
  }

  /// Импорт уже разобранного пакета: либо новый стиль, либо слияние элементов в существующий.
  /// Возвращает null при успехе или код/текст ошибки.
  /// ZIP полного бэкапа (метаданные + видео + музыка). На web возвращает null.
  Future<Uint8List?> buildFullBackupZip() async {
    final current = state.valueOrNull;
    if (current == null) return null;
    return full_backup_collect.collectAndBuildFullBackupZip(_storage, current);
  }

  /// Сливает архив с локальными данными (без удаления существующих стилей, треков и т.д.).
  /// Медиа из архива пишется только для новых треков и для видео новых элементов или
  /// элементов без локального видео. После успеха состояние перезагружается.
  Future<String?> importFullBackup(Uint8List zipBytes) async {
    final parsed = FullBackupService.parseZip(zipBytes);
    if (!parsed.isOk || parsed.appData == null) {
      return parsed.error ?? 'parse_failed';
    }
    try {
      final local = state.valueOrNull ?? await _storage.loadAppData();
      final plan = FullBackupImportMerge.mergeInto(
        local: local,
        imported: parsed.appData!,
      );
      // Если в метаданных есть ссылка на видео в хранилище приложения, а файла нет
      // (обновление, сбой, очистка кэша) — подтянуть байты из архива, если они там есть.
      var videoIdsToWrite = {...plan.videoMoveIdsToWrite};
      for (final style in plan.merged.danceStyles) {
        for (final m in style.moves) {
          final u = m.videoUri;
          if (u == null || u.isEmpty) continue;
          if (u.startsWith('content:') || u.startsWith('/')) continue;
          if (!parsed.videoByMoveId.containsKey(m.id)) continue;
          final onDisk = await _storage.loadMediaFile(u, 'video');
          if (onDisk == null || onDisk.isEmpty) {
            videoIdsToWrite.add(m.id);
          }
        }
      }
      for (final e in parsed.videoByMoveId.entries) {
        if (!videoIdsToWrite.contains(e.key)) continue;
        await _storage.saveMediaFile(e.key, e.value, 'video');
      }
      for (final m in parsed.musicEntries) {
        if (!plan.musicSongIdsToWrite.contains(m.songId)) continue;
        await _storage.saveMediaFile(
          m.songId,
          m.bytes,
          'music',
          musicExtension: m.ext,
        );
      }
      await _storage.saveAppData(plan.merged);
      await _load();
      return null;
    } catch (e) {
      return e.toString();
    }
  }

  Future<String?> importChoreographyFromPackagePayload(
    ChoreographyPackagePayload payload, {
    required bool createNewStyle,
    String? mergeIntoStyleId,
    String newStyleName = '',
  }) async {
    if (createNewStyle && mergeIntoStyleId != null) {
      return 'bad_import_args';
    }
    if (!createNewStyle && mergeIntoStyleId == null) {
      return 'bad_import_args';
    }
    try {
      final base = DateTime.now().millisecondsSinceEpoch;
      final songId = 'song-import-$base';
      final choreoId = 'choreo-import-$base';

      if (createNewStyle) {
        final styleId = 'style-import-$base';
        final styleName = newStyleName.trim().isEmpty
            ? payload.styleName
            : newStyleName.trim();

        var moveIndex = 0;
        final moves = payload.moves.map((m) {
          moveIndex++;
          return Move(
            id: 'move-import-$base-$moveIndex',
            name: m.name,
            level: m.level,
            description: m.description,
            videoUri: null,
            masteryPercent: 0,
          );
        }).toList();

        final style = DanceStyle(
          id: styleId,
          name: styleName,
          moves: moves,
          currentMoveId: null,
        );

        final song = payload.song.copyWith(
          id: songId,
          danceStyle: styleName,
          sizeBytes: payload.musicBytes.length,
        );

        final choreo = payload.choreography.copyWith(
          id: choreoId,
          songId: songId,
          styleId: styleId,
        );

        await addSong(song, payload.musicBytes);
        await addStyle(style);
        await addChoreography(choreo);
        return null;
      }

      final current = state.valueOrNull ?? AppData();
      final sid = mergeIntoStyleId!;
      DanceStyle? targetStyle;
      for (final s in current.danceStyles) {
        if (s.id == sid) {
          targetStyle = s;
          break;
        }
      }
      if (targetStyle == null) return 'style_not_found';

      final (newImportedMoves, renameFromOriginal) = ChoreographyImportMerge.mergeMoves(
        targetStyle.moves,
        payload.moves,
        (i) => 'move-import-$base-$i',
      );

      final mergedStyle = targetStyle.copyWith(
        moves: [...targetStyle.moves, ...newImportedMoves],
      );

      final song = payload.song.copyWith(
        id: songId,
        danceStyle: targetStyle.name,
        sizeBytes: payload.musicBytes.length,
      );

      final choreo = payload.choreography.copyWith(
        id: choreoId,
        songId: songId,
        styleId: sid,
        timeline: ChoreographyImportMerge.remapTimeline(
          payload.choreography.timeline,
          renameFromOriginal,
        ),
      );

      await addSong(song, payload.musicBytes);
      await updateStyle(mergedStyle);
      await addChoreography(choreo);
      return null;
    } catch (e) {
      return e.toString();
    }
  }
}

final appDataNotifierProvider =
    StateNotifierProvider<AppDataNotifier, AsyncValue<AppData>>((ref) {
  return AppDataNotifier(ref.watch(storageServiceProvider));
});

final ttsServiceProvider = Provider<TtsService>((ref) => TtsService.instance);
