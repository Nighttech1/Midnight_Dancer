import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/audio_source_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/audio_source_platform_io.dart'
    as audio_platform;
import 'package:midnight_dancer/core/utils/formatters.dart'
    show
        formatBytes,
        formatDuration,
        formatDurationFromDuration,
        dropdownValueOrFallback;
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/providers/music_playback_provider.dart';
import 'package:path/path.dart' as p;

class MusicScreen extends ConsumerStatefulWidget {
  const MusicScreen({super.key});

  @override
  ConsumerState<MusicScreen> createState() => _MusicScreenState();
}

class _MusicScreenState extends ConsumerState<MusicScreen> {
  String? _filterStyle;
  String _filterLevel = 'All';

  InputDecoration _dropdownDecoration() {
    return InputDecoration(
      border: OutlineInputBorder(borderRadius: AppRadius.radiusMd),
      enabledBorder: OutlineInputBorder(
        borderRadius: AppRadius.radiusMd,
        borderSide: const BorderSide(color: AppColors.cardBorder),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: AppRadius.radiusMd,
        borderSide: const BorderSide(color: AppColors.accent, width: 2),
      ),
      filled: true,
      fillColor: AppColors.card,
    );
  }

  /// Имена стилей без дубликатов значений (регистронезависимо) — иначе Dropdown падает.
  static List<String> _uniqueStyleNames(List<DanceStyle> styles) {
    final seen = <String>{};
    final out = <String>[];
    for (final s in styles) {
      final key = s.name.toLowerCase();
      if (seen.add(key)) {
        out.add(s.name);
      }
    }
    return out;
  }

  /// Стиль трека → значение из списка items (регистр / переименование стиля в данных).
  static String _resolveStyleDropdownValue(String stored, List<String> styleNames) {
    if (styleNames.isEmpty) return stored;
    for (final n in styleNames) {
      if (n.toLowerCase() == stored.toLowerCase()) {
        return n;
      }
    }
    return styleNames.first;
  }

  List<Song> _filteredSongs(List<Song> songs, {String? filterLevel}) {
    final level = filterLevel ?? _filterLevel;
    var list = List<Song>.from(songs);
    if (_filterStyle != null && _filterStyle!.isNotEmpty) {
      final f = _filterStyle!.toLowerCase();
      list = list.where((s) => s.danceStyle.toLowerCase() == f).toList();
    }
    if (level != 'All') {
      list = list.where((s) => s.level == level).toList();
    }
    list.sort((a, b) => a.title.compareTo(b.title));
    return list;
  }

  Future<void> _pickAndAddSong() async {
    final str = ref.read(appStringsProvider);
    try {
      // FileType.audio на iOS трогает медиатеку и требует NSAppleMusicUsageDescription (иначе TCC SIGABRT).
      // Ограничение по расширениям открывает обычный документ-пикер (файлы / iCloud Drive).
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const ['mp3', 'm4a', 'wav'],
        allowMultiple: false,
        withData: kIsWeb,
      );
      if (result == null || result.files.single.path == null && result.files.single.bytes == null) return;

      final file = result.files.single;
      String? path = file.path;
      Uint8List? bytes = file.bytes;
      final name = file.name;
      final ext = name.contains('.') ? p.extension(name).toLowerCase().replaceFirst('.', '') : 'mp3';
      if (!['mp3', 'm4a', 'wav'].contains(ext)) return;

      final appNotifier = ref.read(appDataNotifierProvider.notifier);
      final mp = ref.read(musicPlaybackProvider.notifier);
      var data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
      var styles = data.danceStyles.map((s) => s.name).toList();
      if (styles.isEmpty) {
        final defaultStyle = DanceStyle(
          id: 'style-default',
          name: kCanonicalDefaultDanceStyleName,
          moves: [],
        );
        await appNotifier.addStyle(defaultStyle);
        styles = [kCanonicalDefaultDanceStyleName];
      }

      double durationSec = 0;
      if (!kIsWeb && path != null && path.isNotEmpty) {
        durationSec = await mp.probeDurationSecFromPath(path);
      } else if (kIsWeb && bytes != null && bytes.isNotEmpty) {
        durationSec = await mp.probeDurationSecFromBytes(bytes, ext);
      }

      final title = p.basenameWithoutExtension(name);
      final songId = 'song-${DateTime.now().millisecondsSinceEpoch}';
      final fileName = '$songId.$ext';
      final sizeBytes = bytes?.length ?? (path != null ? await audio_platform.getFileSizeFromPath(path) : 0);
      final song = Song(
        id: songId,
        title: title,
        danceStyle: styles.first,
        level: 'Beginner',
        fileName: fileName,
        duration: durationSec,
        sizeBytes: sizeBytes,
      );

      if (bytes != null && bytes.isNotEmpty) {
        await appNotifier.addSong(song, bytes);
      } else if (!kIsWeb && path != null) {
        final b = await audio_platform.readFileAsBytes(path);
        if (b != null && b.isNotEmpty) await appNotifier.addSong(song, b);
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(str.addedSnackbar(title))));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(str.loadTrackErrorSnackbar(e.toString()))));
      }
    }
  }

  Future<void> _playOrPause(Song song) async {
    final str = ref.read(appStringsProvider);
    try {
      await ref.read(musicPlaybackProvider.notifier).playOrPause(song);
    } catch (e) {
      if (mounted) {
        await ref.read(musicPlaybackProvider.notifier).stopPlayback();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(str.playErrorSnackbar(e.toString()))));
      }
    }
  }

  Future<void> _editSong(Song song) async {
    final str = ref.read(appStringsProvider);
    final mp = ref.read(musicPlaybackProvider.notifier);
    final playbackBefore = ref.read(musicPlaybackProvider);
    try {
      final sameLoaded = playbackBefore.loadedAudioSongId == song.id;
      if (!sameLoaded) {
        await mp.loadSongAudioSource(song);
        await mp.player.pause();
        await mp.player.seek(Duration.zero);
      } else {
        await mp.player.pause();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.playErrorSnackbar(e.toString()))),
        );
      }
      return;
    }

    final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
    final styleNames = _uniqueStyleNames(data.danceStyles);
    if (styleNames.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.addStyleFirst)),
        );
      }
      return;
    }
    final titleController = TextEditingController(text: song.title);
    titleController.selection = TextSelection.collapsed(offset: song.title.length);
    String danceStyle = _resolveStyleDropdownValue(song.danceStyle, styleNames);
    String level = song.level;
    var playbackSpeed = song.playbackSpeed.clamp(0.2, 1.5);

    if (!mounted) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setDialogState) => AlertDialog(
            backgroundColor: AppColors.card,
            title: Text(str.editTrack),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    decoration: InputDecoration(labelText: str.nameLabel),
                    controller: titleController,
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<String>(
                    value: danceStyle,
                    decoration: _dropdownDecoration().copyWith(labelText: str.style),
                    dropdownColor: AppColors.card,
                    items: styleNames
                        .map((s) => DropdownMenuItem(value: s, child: Text(str.displayDanceStyleName(s))))
                        .toList(),
                    onChanged: (v) => setDialogState(() => danceStyle = v ?? danceStyle),
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<String>(
                    value: level,
                    decoration: _dropdownDecoration().copyWith(labelText: str.levelLabel),
                    dropdownColor: AppColors.card,
                    items: str.filterLevelOptions.where((e) => e.$1 != 'All').map((e) => DropdownMenuItem(value: e.$1, child: Text(e.$2))).toList(),
                    onChanged: (v) => setDialogState(() => level = v ?? level),
                  ),
                  const SizedBox(height: 20),
                  Text(
                    str.trackPositionHint,
                    style: const TextStyle(color: Colors.white54, fontSize: 12),
                  ),
                  const SizedBox(height: 8),
                  StreamBuilder<Duration>(
                    stream: mp.player.positionStream,
                    builder: (context, posSnap) {
                      return StreamBuilder<Duration?>(
                        stream: mp.player.durationStream,
                        builder: (context, durSnap) {
                          final pos = posSnap.data ?? Duration.zero;
                          final metaMs = (song.duration * 1000).round().clamp(1, 864000000);
                          final dur = durSnap.data ??
                              (mp.player.duration ??
                                  Duration(milliseconds: metaMs));
                          final totalMs = dur.inMilliseconds <= 0 ? metaMs : dur.inMilliseconds;
                          final v = totalMs <= 0
                              ? 0.0
                              : (pos.inMilliseconds / totalMs).clamp(0.0, 1.0);
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Row(
                                children: [
                                  IconButton(
                                    onPressed: () async {
                                      if (mp.player.playing) {
                                        await mp.player.pause();
                                      } else {
                                        await mp.activateAudioSession();
                                        await mp.player.play();
                                      }
                                      setDialogState(() {});
                                    },
                                    icon: Icon(
                                      mp.player.playing ? Icons.pause_circle_filled : Icons.play_circle_filled,
                                      color: AppColors.accent,
                                      size: 40,
                                    ),
                                  ),
                                  Expanded(
                                    child: Text(
                                      '${formatDurationFromDuration(pos)} / ${formatDurationFromDuration(Duration(milliseconds: totalMs))}',
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontWeight: FontWeight.w600,
                                        fontSize: 14,
                                      ),
                                      textAlign: TextAlign.center,
                                    ),
                                  ),
                                  IconButton(
                                    tooltip: str.stopTrack,
                                    onPressed: () async {
                                      try {
                                        await mp.stopPlaybackAndReloadForEdit(song);
                                      } catch (_) {}
                                      setDialogState(() {});
                                    },
                                    icon: const Icon(Icons.stop_circle_outlined, color: Colors.white54, size: 32),
                                  ),
                                ],
                              ),
                              SliderTheme(
                                data: SliderTheme.of(context).copyWith(
                                  trackHeight: 5,
                                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 14),
                                  overlayShape: const RoundSliderOverlayShape(overlayRadius: 24),
                                  activeTrackColor: AppColors.accent,
                                  inactiveTrackColor: Colors.white24,
                                  thumbColor: Colors.white,
                                  overlayColor: AppColors.accent.withOpacity(0.2),
                                ),
                                child: Slider(
                                  value: v,
                                  onChanged: (nv) async {
                                    final ms = (nv * totalMs).round();
                                    await mp.player.seek(Duration(milliseconds: ms));
                                    setDialogState(() {});
                                  },
                                ),
                              ),
                            ],
                          );
                        },
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      '${str.trackPlaybackSpeed}: ${str.trackSpeedValue(playbackSpeed)}',
                      style: const TextStyle(color: Colors.white70, fontSize: 13),
                    ),
                  ),
                  Slider(
                    value: playbackSpeed,
                    min: 0.2,
                    max: 1.5,
                    divisions: 26,
                    label: str.trackSpeedValue(playbackSpeed),
                    onChanged: (v) async {
                      setDialogState(() => playbackSpeed = v);
                      if (ref.read(musicPlaybackProvider).loadedAudioSongId == song.id) {
                        try {
                          await mp.player.setSpeed(v.clamp(0.2, 1.5));
                        } catch (_) {}
                      }
                    },
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(str.cancel)),
              TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(str.save)),
            ],
          ),
        );
      },
    );

    if (ok == true && mounted) {
      final title = titleController.text.trim();
      final updated = song.copyWith(
        title: title.isEmpty ? song.title : title,
        danceStyle: danceStyle,
        level: level,
        playbackSpeed: playbackSpeed.clamp(0.2, 1.5),
      );
      titleController.dispose();
      await ref.read(appDataNotifierProvider.notifier).updateSong(updated);
      if (ref.read(musicPlaybackProvider).loadedAudioSongId == song.id) {
        try {
          await mp.player.setSpeed(updated.playbackSpeed.clamp(0.2, 1.5));
        } catch (_) {}
      }
    } else {
      titleController.dispose();
    }
  }

  Future<void> _deleteSong(Song song) async {
    final str = ref.read(appStringsProvider);
    final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
    final usedBy = data.choreographies.where((c) => c.songId == song.id).toList();

    Future<bool> confirmSimple() async {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: AppColors.card,
          title: Text(str.deleteTrackConfirm),
          content: Text(str.deleteTrackMessage(song.title)),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(str.cancel)),
            TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(str.delete, style: const TextStyle(color: Colors.red))),
          ],
        ),
      );
      return ok == true;
    }

    Future<bool> confirmWithChoreographies() async {
      final lines = usedBy.map((c) => '• ${c.name}').join('\n');
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: AppColors.card,
          title: Text(str.deleteTrackUsedInChoreographiesTitle),
          content: SingleChildScrollView(
            child: Text(str.deleteTrackUsedInChoreographiesBody(song.title, lines)),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(str.cancel)),
            TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(str.delete, style: const TextStyle(color: Colors.red)),
            ),
          ],
        ),
      );
      return ok == true;
    }

    final bool go = usedBy.isEmpty ? await confirmSimple() : await confirmWithChoreographies();
    if (go && mounted) {
      final mp = ref.read(musicPlaybackProvider.notifier);
      await mp.onSongRemovedFromData(song.id);
      await ref.read(appDataNotifierProvider.notifier).deleteSong(song.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    final asyncData = ref.watch(appDataNotifierProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncData.when(
          loading: () => const Center(child: CircularProgressIndicator(color: AppColors.accent)),
          error: (e, _) => Center(child: Text('${str.errorPrefix}: $e', style: const TextStyle(color: AppColors.accent))),
          data: (data) {
            final playback = ref.watch(musicPlaybackProvider);
            final mp = ref.read(musicPlaybackProvider.notifier);
            final songs = data.songs;
            final styles = data.danceStyles;
            final styleNames = _uniqueStyleNames(styles);
            final levelKeys =
                str.filterLevelOptions.map((e) => e.$1).toSet();
            final safeFilterLevel =
                dropdownValueOrFallback(_filterLevel, levelKeys, 'All');
            if (safeFilterLevel != _filterLevel) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (!mounted) return;
                setState(() => _filterLevel = 'All');
              });
            }
            final filterDropdownValue = _filterStyle == null || _filterStyle!.isEmpty
                ? null
                : () {
                    final f = _filterStyle!.toLowerCase();
                    for (final n in styleNames) {
                      if (n.toLowerCase() == f) return n;
                    }
                    return null;
                  }();
            final filtered = _filteredSongs(songs, filterLevel: safeFilterLevel);
            if (playback.playingSongId != null && !songs.any((s) => s.id == playback.playingSongId)) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                ref.read(musicPlaybackProvider.notifier).clearPlaybackIfSongMissing(songs.map((s) => s.id));
              });
            }

            return RefreshIndicator(
              onRefresh: () async => ref.invalidate(appDataNotifierProvider),
              color: AppColors.accent,
              child: CustomScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                slivers: [
                  SliverPadding(
                    padding: const EdgeInsets.all(16),
                    sliver: SliverToBoxAdapter(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            str.musicTitle,
                            style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: Colors.white),
                          ),
                          const SizedBox(height: 16),
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: AppColors.card.withOpacity(0.5),
                              borderRadius: AppRadius.radiusMd,
                              border: Border.all(color: AppColors.cardBorder),
                            ),
                            child: Column(
                              children: [
                                Row(
                                  children: [
                                    Expanded(
                                      child: ElevatedButton.icon(
                                        onPressed: _pickAndAddSong,
                                        icon: const Icon(Icons.add, size: 18),
                                        label: Text(str.loadTrack),
                                        style: ElevatedButton.styleFrom(
                                          backgroundColor: AppColors.accent,
                                          foregroundColor: Colors.white,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 12),
                                Row(
                                  children: [
                                    Expanded(
                                      child: DropdownButtonFormField<String?>(
                                        value: filterDropdownValue,
                                        decoration: _dropdownDecoration().copyWith(labelText: str.style, isDense: true),
                                        dropdownColor: AppColors.card,
                                        isExpanded: true,
                                        items: [
                                          DropdownMenuItem(value: null, child: Text(str.allStyles, overflow: TextOverflow.ellipsis)),
                                          ...styleNames.map((s) => DropdownMenuItem<String?>(
                                                value: s,
                                                child: Text(str.displayDanceStyleName(s), overflow: TextOverflow.ellipsis),
                                              )),
                                        ],
                                        onChanged: (v) => setState(() => _filterStyle = v),
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: DropdownButtonFormField<String>(
                                        value: safeFilterLevel,
                                        decoration: _dropdownDecoration().copyWith(labelText: str.levelLabel, isDense: true),
                                        dropdownColor: AppColors.card,
                                        isExpanded: true,
                                        items: str.filterLevelOptions.map((e) => DropdownMenuItem(value: e.$1, child: Text(e.$2, overflow: TextOverflow.ellipsis))).toList(),
                                        onChanged: (v) => setState(() => _filterLevel = v ?? 'All'),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  if (filtered.isEmpty)
                    SliverFillRemaining(
                      child: Center(
                        child: Text(str.noTracksHint, style: const TextStyle(color: AppColors.textSecondary)),
                      ),
                    )
                  else
                    SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (_, i) {
                          final song = filtered[i];
                          return Container(
                            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                            decoration: BoxDecoration(
                              color: AppColors.card,
                              borderRadius: AppRadius.radiusMd,
                              border: Border.all(color: AppColors.cardBorder),
                            ),
                            child: Row(
                              children: [
                                StreamBuilder<PlayerState>(
                                  stream: mp.player.playerStateStream,
                                  builder: (context, snap) {
                                    final isThis = playback.playingSongId == song.id;
                                    final playing = isThis && (snap.data?.playing ?? false);
                                    return IconButton(
                                      style: IconButton.styleFrom(
                                        minimumSize: const Size(44, 44),
                                        padding: EdgeInsets.zero,
                                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                      ),
                                      icon: Icon(
                                        playing ? Icons.pause_circle_filled : Icons.play_circle_filled,
                                        color: AppColors.accent,
                                        size: 32,
                                      ),
                                      onPressed: () => _playOrPause(song),
                                    );
                                  },
                                ),
                                Expanded(
                                  child: Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      Text(
                                        song.title,
                                        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                      Text(
                                        '${str.displayDanceStyleName(song.danceStyle)} · ${str.levelLabelFor(song.level)} · ${formatDuration(song.duration)} · ${formatBytes(song.sizeBytes)}',
                                        style: TextStyle(color: AppColors.textSecondary, fontSize: 12),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  style: IconButton.styleFrom(
                                    minimumSize: const Size(36, 36),
                                    padding: EdgeInsets.zero,
                                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                  ),
                                  icon: const Icon(Icons.edit, color: Colors.white70, size: 20),
                                  onPressed: () => _editSong(song),
                                ),
                                IconButton(
                                  style: IconButton.styleFrom(
                                    minimumSize: const Size(36, 36),
                                    padding: EdgeInsets.zero,
                                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                  ),
                                  icon: const Icon(Icons.delete_outline, color: Colors.white54, size: 20),
                                  onPressed: () => _deleteSong(song),
                                ),
                              ],
                            ),
                          );
                        },
                        childCount: filtered.length,
                      ),
                    ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
