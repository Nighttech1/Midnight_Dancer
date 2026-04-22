import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/formatters.dart' show dropdownValueOrFallback;
import 'package:midnight_dancer/core/utils/share_position_origin.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/ui/screens/choreography/sequence_editor_screen.dart';
import 'package:midnight_dancer/ui/screens/choreography/choreography_share_zip_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/ui/screens/choreography/choreography_share_zip_io.dart' as share_zip;

String _choreoStyleName(AppData data, String styleId) {
  final s = data.danceStyles.where((x) => x.id == styleId).toList();
  return s.isEmpty ? styleId : s.first.name;
}

String _choreoSongTitle(AppData data, String songId, AppStrings str) {
  if (songId.isEmpty) return str.choreoMissingTrack;
  final s = data.songs.where((x) => x.id == songId).toList();
  return s.isEmpty ? songId : s.first.title;
}

class ChoreographyScreen extends ConsumerStatefulWidget {
  const ChoreographyScreen({super.key});

  @override
  ConsumerState<ChoreographyScreen> createState() => _ChoreographyScreenState();
}

class _ChoreographyScreenState extends ConsumerState<ChoreographyScreen> {
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

  List<String> _uniqueStyleNames(List<DanceStyle> styles) {
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

  List<Choreography> _filteredChoreographies(AppData data) {
    var list = List<Choreography>.from(data.choreographies);
    if (_filterStyle != null && _filterStyle!.isNotEmpty) {
      final f = _filterStyle!.toLowerCase();
      list = list.where((c) => _choreoStyleName(data, c.styleId).toLowerCase() == f).toList();
    }
    if (_filterLevel != 'All') {
      list = list.where((c) {
        Song? song;
        for (final s in data.songs) {
          if (s.id == c.songId) {
            song = s;
            break;
          }
        }
        return song != null && song.level == _filterLevel;
      }).toList();
    }
    list.sort((a, b) => a.name.compareTo(b.name));
    return list;
  }

  @override
  Widget build(BuildContext context) {
    final asyncData = ref.watch(appDataNotifierProvider);
    final str = ref.watch(appStringsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncData.when(
          loading: () => const Center(child: CircularProgressIndicator(color: AppColors.accent)),
          error: (e, _) => Center(child: Text('${str.errorPrefix}: $e', style: const TextStyle(color: AppColors.accent))),
          data: (data) {
            final styleNames = _uniqueStyleNames(data.danceStyles);
            final levelKeys = str.filterLevelOptions.map((e) => e.$1).toSet();
            final safeFilterLevel = dropdownValueOrFallback(_filterLevel, levelKeys, 'All');
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
            final list = _filteredChoreographies(data);
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
                            str.choreoTitle,
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
                                SizedBox(
                                  width: double.infinity,
                                  child: ElevatedButton.icon(
                                    onPressed: () => _showCreateDialog(context, ref, data),
                                    icon: const Icon(Icons.add, size: 18),
                                    label: Text(str.create),
                                    style: ElevatedButton.styleFrom(
                                      backgroundColor: AppColors.accent,
                                      foregroundColor: Colors.white,
                                    ),
                                  ),
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
                                          ...styleNames.map(
                                            (s) => DropdownMenuItem<String?>(
                                              value: s,
                                              child: Text(str.displayDanceStyleName(s), overflow: TextOverflow.ellipsis),
                                            ),
                                          ),
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
                                        items: str.filterLevelOptions
                                            .map((e) => DropdownMenuItem(value: e.$1, child: Text(e.$2, overflow: TextOverflow.ellipsis)))
                                            .toList(),
                                        onChanged: (v) => setState(() => _filterLevel = v ?? 'All'),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 16),
                          if (list.isEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 24),
                              child: Center(
                                child: Text(
                                  data.choreographies.isEmpty ? str.choreoEmpty : str.choreoFilterEmpty,
                                  style: const TextStyle(color: AppColors.textSecondary),
                                  textAlign: TextAlign.center,
                                ),
                              ),
                            )
                          else
                            ...list.map((c) => _ChoreoCard(
                                  choreography: c,
                                  styleName: _choreoStyleName(data, c.styleId),
                                  songTitle: _choreoSongTitle(data, c.songId, str),
                                  shareTooltip: str.shareChoreography,
                                  changeStyleTooltip: str.choreoChangeStyleTooltip,
                                  changeTrackTooltip: str.choreoChangeTrackTooltip,
                                  onTap: () => _openEditor(context, ref, c, data),
                                  onRename: () => _renameChoreography(context, ref, c),
                                  onChangeTrack: () => _changeChoreographySong(context, ref, c, data),
                                  onChangeStyle: () => _changeChoreographyStyle(context, ref, c, data),
                                  onShare: () => _shareChoreography(context, ref, c),
                                  onCopy: () => _copyChoreography(context, ref, c, data),
                                  onDelete: () => _deleteChoreography(context, ref, c),
                                )),
                        ],
                      ),
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

  void _openEditor(BuildContext context, WidgetRef ref, Choreography choreography, AppData data) {
    if (data.danceStyles.isEmpty) return;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (ctx) => SequenceEditorScreen(
          choreography: choreography,
          onSave: (updated) async {
            await ref.read(appDataNotifierProvider.notifier).updateChoreography(updated);
            if (context.mounted) Navigator.of(context).pop();
          },
        ),
      ),
    );
  }

  void _showCreateDialog(BuildContext context, WidgetRef ref, AppData data) {
    final str = ref.read(appStringsProvider);
    if (data.danceStyles.isEmpty || data.songs.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.addStylesAndTracks)),
      );
      return;
    }
    String? styleId = data.danceStyles.first.id;
    String? songId = data.songs.first.id;
    final nameController = TextEditingController(text: str.newChoreography);

    showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setState) => AlertDialog(
          backgroundColor: AppColors.card,
          title: Text(str.newChoreography),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameController,
                  decoration: InputDecoration(labelText: str.nameLabel),
                ),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: styleId,
                  decoration: InputDecoration(labelText: str.style),
                  dropdownColor: AppColors.card,
                  isExpanded: true,
                  items: data.danceStyles
                      .map((s) => DropdownMenuItem(
                            value: s.id,
                            child: Text(str.displayDanceStyleName(s.name), overflow: TextOverflow.ellipsis),
                          ))
                      .toList(),
                  onChanged: (v) => setState(() => styleId = v),
                ),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: songId,
                  decoration: InputDecoration(labelText: str.music),
                  dropdownColor: AppColors.card,
                  isExpanded: true,
                  items: data.songs.map((s) => DropdownMenuItem(value: s.id, child: Text(s.title, overflow: TextOverflow.ellipsis))).toList(),
                  onChanged: (v) => setState(() => songId = v),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: Text(str.cancel)),
            ElevatedButton(
              onPressed: () {
                final sid = styleId;
                final sngId = songId;
                if (sid == null || sngId == null) return;
                final name = nameController.text.trim().isEmpty ? str.newChoreography : nameController.text.trim();
                final song = data.songs.firstWhere((s) => s.id == sngId);
                final choreo = Choreography(
                  id: 'choreo-${DateTime.now().millisecondsSinceEpoch}',
                  name: name,
                  songId: sngId,
                  styleId: sid,
                  timeline: {},
                  startTime: 0,
                  endTime: song.duration > 0 ? song.duration : 180,
                );
                ref.read(appDataNotifierProvider.notifier).addChoreography(choreo);
                Navigator.pop(ctx);
                WidgetsBinding.instance.addPostFrameCallback((_) => nameController.dispose());
                _openEditor(context, ref, choreo, data);
              },
              child: Text(str.create),
            ),
          ],
        ),
      ),
    );
  }

  void _copyChoreography(BuildContext context, WidgetRef ref, Choreography c, AppData data) async {
    final str = ref.read(appStringsProvider);
    final copy = Choreography(
      id: 'choreo-${DateTime.now().millisecondsSinceEpoch}',
      name: '${str.copyOf} ${c.name}',
      songId: c.songId,
      styleId: c.styleId,
      timeline: Map.from(c.timeline),
      startTime: c.startTime,
      endTime: c.endTime,
    );
    await ref.read(appDataNotifierProvider.notifier).addChoreography(copy);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(str.copiedSnackbar(copy.name))));
    }
  }

  void _changeChoreographySong(BuildContext context, WidgetRef ref, Choreography c, AppData data) async {
    final str = ref.read(appStringsProvider);
    if (data.songs.isEmpty) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.addMusicOrOpenExchange)),
        );
      }
      return;
    }
    var songId = c.songId.isNotEmpty && data.songs.any((s) => s.id == c.songId) ? c.songId : data.songs.first.id;
    final picked = await showDialog<String>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) => AlertDialog(
          backgroundColor: AppColors.card,
          title: Text(str.selectTrackForChoreographyTitle),
          content: DropdownButtonFormField<String>(
            value: songId,
            decoration: InputDecoration(labelText: str.music),
            dropdownColor: AppColors.card,
            isExpanded: true,
            items: data.songs
                .map((s) => DropdownMenuItem(value: s.id, child: Text(s.title, overflow: TextOverflow.ellipsis)))
                .toList(),
            onChanged: (v) {
              if (v != null) setSt(() => songId = v);
            },
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: Text(str.cancel)),
            ElevatedButton(
              onPressed: () => Navigator.pop(ctx, songId),
              child: Text(str.save),
            ),
          ],
        ),
      ),
    );
    if (!context.mounted || picked == null || picked == c.songId) return;
    final song = data.songs.firstWhere((s) => s.id == picked);
    final maxDur = song.duration > 0 ? song.duration : 300.0;
    var end = c.endTime.clamp(0.0, maxDur);
    var start = c.startTime.clamp(0.0, end);
    final newMap = <double, String>{};
    for (final e in c.timeline.entries) {
      if (e.key >= start && e.key <= end) {
        newMap[e.key] = e.value;
      }
    }
    await ref.read(appDataNotifierProvider.notifier).updateChoreography(
          c.copyWith(songId: picked, startTime: start, endTime: end, timeline: newMap),
        );
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.choreoChangeTrackDone)),
      );
    }
  }

  void _changeChoreographyStyle(BuildContext context, WidgetRef ref, Choreography c, AppData data) async {
    final str = ref.read(appStringsProvider);
    if (data.danceStyles.length < 2) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.choreoChangeStyleNeedTwoStyles)),
        );
      }
      return;
    }
    var styleId = c.styleId;
    final picked = await showDialog<String>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) => AlertDialog(
          backgroundColor: AppColors.card,
          title: Text(str.choreoChangeStyleTitle(c.name)),
          content: DropdownButtonFormField<String>(
            value: styleId,
            decoration: InputDecoration(labelText: str.style),
            dropdownColor: AppColors.card,
            isExpanded: true,
            items: data.danceStyles
                .map(
                  (s) => DropdownMenuItem(
                    value: s.id,
                    child: Text(str.displayDanceStyleName(s.name), overflow: TextOverflow.ellipsis),
                  ),
                )
                .toList(),
            onChanged: (v) {
              if (v != null) setSt(() => styleId = v);
            },
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: Text(str.cancel)),
            ElevatedButton(
              onPressed: () => Navigator.pop(ctx, styleId),
              child: Text(str.save),
            ),
          ],
        ),
      ),
    );
    if (!context.mounted || picked == null || picked == c.styleId) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(
          str.choreoChangeLabelOnlyTitle,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: Text(
          str.choreoChangeLabelOnlyBody,
          style: TextStyle(color: Colors.white.withOpacity(0.9), fontSize: 15, height: 1.45),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(str.cancel)),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(str.choreoChangeStyleCascadeContinue, style: const TextStyle(color: AppColors.accent)),
          ),
        ],
      ),
    );
    if (!context.mounted || confirmed != true) return;

    await ref.read(appDataNotifierProvider.notifier).updateChoreography(c.copyWith(styleId: picked));
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.choreoChangeStyleDone)),
      );
    }
  }

  void _renameChoreography(BuildContext context, WidgetRef ref, Choreography c) async {
    final str = ref.read(appStringsProvider);
    final nameController = TextEditingController(text: c.name);
    nameController.selection = TextSelection.collapsed(offset: c.name.length);
    final newName = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(str.rename),
        content: TextField(
          controller: nameController,
          decoration: InputDecoration(labelText: str.choreoNameLabel),
          autofocus: true,
          onSubmitted: (s) => Navigator.pop(ctx, s.trim().isEmpty ? c.name : s.trim()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: Text(str.cancel)),
          ElevatedButton(
            onPressed: () {
              final s = nameController.text.trim();
              Navigator.pop(ctx, s.isEmpty ? c.name : s);
            },
            child: Text(str.save),
          ),
        ],
      ),
    );
    WidgetsBinding.instance.addPostFrameCallback((_) => nameController.dispose());
    if (newName != null && newName.isNotEmpty && context.mounted) {
      await ref.read(appDataNotifierProvider.notifier).updateChoreography(c.copyWith(name: newName));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(str.renamedSnackbar(newName))));
      }
    }
  }

  static String _safeShareFileName(String name) {
    final s = name.replaceAll(RegExp(r'[<>:"/\\|?*]'), '_').trim();
    if (s.isEmpty) return 'choreography';
    return s.length > 80 ? s.substring(0, 80) : s;
  }

  Future<void> _shareChoreography(BuildContext context, WidgetRef ref, Choreography c) async {
    final str = ref.read(appStringsProvider);
    var loaderShown = false;
    void closeLoader() {
      if (loaderShown && context.mounted) {
        Navigator.of(context, rootNavigator: true).pop();
        loaderShown = false;
      }
    }

    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => const Center(
        child: Card(
          color: AppColors.card,
          child: Padding(
            padding: EdgeInsets.all(28),
            child: CircularProgressIndicator(color: AppColors.accent),
          ),
        ),
      ),
    );
    loaderShown = true;

    try {
      final notifier = ref.read(appDataNotifierProvider.notifier);
      final zip = await notifier.buildChoreographyShareZip(c.id);
      if (!context.mounted) {
        closeLoader();
        return;
      }
      if (zip == null) {
        closeLoader();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.shareChoreographyFailed)),
        );
        return;
      }
      final name = '${_safeShareFileName(c.name)}.midnight-dancer.zip';
      closeLoader();
      await Future<void>.delayed(const Duration(milliseconds: 50));
      if (!context.mounted) return;
      await share_zip.shareChoreographyZipBytes(
        zip,
        name,
        c.name,
        sharePositionOrigin: sharePositionOriginForContext(context),
      );
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.shareChoreographyFailedOpen)),
        );
      }
    } finally {
      closeLoader();
    }
  }

  void _deleteChoreography(BuildContext context, WidgetRef ref, Choreography c) async {
    final str = ref.read(appStringsProvider);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(str.deleteChoreoConfirm),
        content: Text(str.deleteChoreoMessage(c.name)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(str.cancel)),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(str.delete, style: const TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (ok == true && context.mounted) {
      await ref.read(appDataNotifierProvider.notifier).deleteChoreography(c.id);
    }
  }
}

class _ChoreoCard extends StatelessWidget {
  const _ChoreoCard({
    required this.choreography,
    required this.styleName,
    required this.songTitle,
    required this.shareTooltip,
    required this.changeStyleTooltip,
    required this.changeTrackTooltip,
    required this.onTap,
    required this.onRename,
    required this.onChangeTrack,
    required this.onChangeStyle,
    required this.onShare,
    required this.onCopy,
    required this.onDelete,
  });

  final Choreography choreography;
  final String styleName;
  final String songTitle;
  final String shareTooltip;
  final String changeStyleTooltip;
  final String changeTrackTooltip;
  final VoidCallback onTap;
  final VoidCallback onRename;
  final VoidCallback onChangeTrack;
  final VoidCallback onChangeStyle;
  final VoidCallback onShare;
  final VoidCallback onCopy;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: AppRadius.radiusMd,
        border: Border.all(color: AppColors.cardBorder),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: AppRadius.radiusMd,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 12, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  choreography.name,
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 16),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  '$styleName · $songTitle',
                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 10),
                Align(
                  alignment: Alignment.centerRight,
                  child: Wrap(
                    alignment: WrapAlignment.end,
                    spacing: 0,
                    runSpacing: 4,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.drive_file_rename_outline, color: Colors.white70, size: 22),
                        onPressed: onRename,
                        style: IconButton.styleFrom(
                          minimumSize: const Size(40, 40),
                          padding: EdgeInsets.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                      IconButton(
                        tooltip: changeTrackTooltip,
                        icon: const Icon(Icons.library_music_outlined, color: Colors.white70, size: 22),
                        onPressed: onChangeTrack,
                        style: IconButton.styleFrom(
                          minimumSize: const Size(40, 40),
                          padding: EdgeInsets.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                      IconButton(
                        tooltip: changeStyleTooltip,
                        icon: const Icon(Icons.swap_horiz, color: Colors.white70, size: 22),
                        onPressed: onChangeStyle,
                        style: IconButton.styleFrom(
                          minimumSize: const Size(40, 40),
                          padding: EdgeInsets.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                      IconButton(
                        tooltip: shareTooltip,
                        icon: const Icon(Icons.share, color: Colors.white70, size: 22),
                        onPressed: onShare,
                        style: IconButton.styleFrom(
                          minimumSize: const Size(40, 40),
                          padding: EdgeInsets.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.copy, color: Colors.white70, size: 22),
                        onPressed: onCopy,
                        style: IconButton.styleFrom(
                          minimumSize: const Size(40, 40),
                          padding: EdgeInsets.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.delete_outline, color: Colors.white54, size: 22),
                        onPressed: onDelete,
                        style: IconButton.styleFrom(
                          minimumSize: const Size(40, 40),
                          padding: EdgeInsets.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
