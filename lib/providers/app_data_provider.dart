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
