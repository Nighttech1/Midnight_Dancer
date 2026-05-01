import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';

/// Снимок сущностей, добавленных **последним** импортом ZIP (полный бэкап или пакет хореографии).
/// Хранится в [AppData.settings] под [settingsKey]; при новом импорте заменяется целиком.
class LastImportManifest {
  LastImportManifest({
    required Set<String> wholeStyleIds,
    required Set<String> moveIds,
    required Set<String> songIds,
    required Set<String> choreographyIds,
  })  : wholeStyleIds = Set.unmodifiable(wholeStyleIds),
        moveIds = Set.unmodifiable(moveIds),
        songIds = Set.unmodifiable(songIds),
        choreographyIds = Set.unmodifiable(choreographyIds);

  static const String settingsKey = 'last_import_manifest';

  final Set<String> wholeStyleIds;
  final Set<String> moveIds;
  final Set<String> songIds;
  final Set<String> choreographyIds;

  bool get isEmpty =>
      wholeStyleIds.isEmpty &&
      moveIds.isEmpty &&
      songIds.isEmpty &&
      choreographyIds.isEmpty;

  Map<String, dynamic> toJson() => {
        'wholeStyleIds': wholeStyleIds.toList(),
        'moveIds': moveIds.toList(),
        'songIds': songIds.toList(),
        'choreographyIds': choreographyIds.toList(),
      };

  factory LastImportManifest.fromJson(Map<String, dynamic>? json) {
    if (json == null || json.isEmpty) {
      return LastImportManifest.empty();
    }
    Set<String> ss(String key) {
      final v = json[key];
      if (v is! List) return {};
      return v.map((e) => e.toString()).toSet();
    }

    return LastImportManifest(
      wholeStyleIds: ss('wholeStyleIds'),
      moveIds: ss('moveIds'),
      songIds: ss('songIds'),
      choreographyIds: ss('choreographyIds'),
    );
  }

  factory LastImportManifest.empty() => LastImportManifest(
        wholeStyleIds: {},
        moveIds: {},
        songIds: {},
        choreographyIds: {},
      );

  static LastImportManifest fromSettings(Map<String, dynamic> settings) {
    final raw = settings[settingsKey];
    if (raw is Map<String, dynamic>) return LastImportManifest.fromJson(raw);
    if (raw is Map) return LastImportManifest.fromJson(Map<String, dynamic>.from(raw));
    return LastImportManifest.empty();
  }

  /// Вписать манифест в карту настроек (пустой — удалить ключ).
  static Map<String, dynamic> embedInSettings(
    Map<String, dynamic> settings,
    LastImportManifest manifest,
  ) {
    final out = Map<String, dynamic>.from(settings);
    if (manifest.isEmpty) {
      out.remove(settingsKey);
    } else {
      out[settingsKey] = manifest.toJson();
    }
    return out;
  }

  /// Разница между локальными данными до слияния и результатом после [FullBackupImportMerge.mergeInto].
  static LastImportManifest computeFullBackupDelta(AppData local, AppData merged) {
    final localStyleIds = local.danceStyles.map((s) => s.id).toSet();
    final localMoveIds = <String>{};
    for (final s in local.danceStyles) {
      for (final m in s.moves) {
        localMoveIds.add(m.id);
      }
    }
    final localSongIds = local.songs.map((s) => s.id).toSet();
    final localChoreoIds = local.choreographies.map((c) => c.id).toSet();

    final wholeStyleIds = <String>{};
    final moveIds = <String>{};

    for (final s in merged.danceStyles) {
      if (!localStyleIds.contains(s.id)) {
        wholeStyleIds.add(s.id);
        for (final m in s.moves) {
          moveIds.add(m.id);
        }
      } else {
        DanceStyle? old;
        for (final o in local.danceStyles) {
          if (o.id == s.id) {
            old = o;
            break;
          }
        }
        final oldM = old?.moves.map((m) => m.id).toSet() ?? {};
        for (final m in s.moves) {
          if (!oldM.contains(m.id)) moveIds.add(m.id);
        }
      }
    }

    final songIds = merged.songs.map((s) => s.id).where((id) => !localSongIds.contains(id)).toSet();

    final choreographyIds =
        merged.choreographies.map((c) => c.id).where((id) => !localChoreoIds.contains(id)).toSet();

    return LastImportManifest(
      wholeStyleIds: wholeStyleIds,
      moveIds: moveIds,
      songIds: songIds,
      choreographyIds: choreographyIds,
    );
  }

  /// Импорт пакета хореографии ([importChoreographyFromPackagePayload]).
  static LastImportManifest forChoreographyPackageImport({
    required bool createdNewStyle,
    required String styleId,
    required List<String> importedMoveIds,
    required String songId,
    required String choreographyId,
  }) {
    return LastImportManifest(
      wholeStyleIds: createdNewStyle ? {styleId} : {},
      moveIds: importedMoveIds.toSet(),
      songIds: {songId},
      choreographyIds: {choreographyId},
    );
  }
}
