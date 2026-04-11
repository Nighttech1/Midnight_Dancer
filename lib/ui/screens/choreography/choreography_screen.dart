import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/picker_file_bytes_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/picker_file_bytes_io.dart' as picker_file;
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/services/choreography_package.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/ui/screens/choreography/sequence_editor_screen.dart';
import 'package:midnight_dancer/ui/screens/choreography/choreography_share_zip_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/ui/screens/choreography/choreography_share_zip_io.dart' as share_zip;

class ChoreographyScreen extends ConsumerWidget {
  const ChoreographyScreen({super.key});

  static String _styleName(AppData data, String styleId) {
    final s = data.danceStyles.where((x) => x.id == styleId).toList();
    return s.isEmpty ? styleId : s.first.name;
  }

  static String _songTitle(AppData data, String songId) {
    final s = data.songs.where((x) => x.id == songId).toList();
    return s.isEmpty ? songId : s.first.title;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncData = ref.watch(appDataNotifierProvider);
    final str = ref.watch(appStringsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncData.when(
          loading: () => const Center(child: CircularProgressIndicator(color: AppColors.accent)),
          error: (e, _) => Center(child: Text('${str.errorPrefix}: $e', style: const TextStyle(color: AppColors.accent))),
          data: (data) {
            final list = data.choreographies;
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
                            child: Row(
                              children: [
                                Expanded(
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
                                const SizedBox(width: 8),
                                Expanded(
                                  child: OutlinedButton.icon(
                                    onPressed: () => _importChoreographyPackage(context, ref),
                                    icon: const Icon(Icons.upload_file, size: 18),
                                    label: Text(str.uploadChoreography),
                                    style: OutlinedButton.styleFrom(
                                      foregroundColor: Colors.white,
                                      side: const BorderSide(color: AppColors.cardBorder),
                                    ),
                                  ),
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
                                  str.choreoEmpty,
                                  style: const TextStyle(color: AppColors.textSecondary),
                                  textAlign: TextAlign.center,
                                ),
                              ),
                            )
                          else
                            ...list.map((c) => _ChoreoCard(
                                  choreography: c,
                                  styleName: _styleName(data, c.styleId),
                                  songTitle: _songTitle(data, c.songId),
                                  shareTooltip: str.shareChoreography,
                                  onTap: () => _openEditor(context, ref, c, data),
                                  onRename: () => _renameChoreography(context, ref, c),
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
    final styles = data.danceStyles.where((s) => s.id == choreography.styleId).toList();
    final songs = data.songs.where((s) => s.id == choreography.songId).toList();
    if (styles.isEmpty || songs.isEmpty) return;
    final style = styles.first;
    final song = songs.first;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (ctx) => SequenceEditorScreen(
          choreography: choreography,
          style: style,
          song: song,
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
      await share_zip.shareChoreographyZipBytes(zip, name, c.name);
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

  Future<void> _importChoreographyPackage(BuildContext context, WidgetRef ref) async {
    final str = ref.read(appStringsProvider);
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['zip'],
      withData: kIsWeb,
    );
    if (result == null || result.files.isEmpty) return;
    final file = result.files.single;
    final bytes = await picker_file.completePickerFileBytes(file);
    if (!context.mounted) return;
    if (bytes == null || bytes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.importChoreographyNoFile)),
      );
      return;
    }

    late final ChoreographyPackagePayload payload;
    try {
      payload = ChoreographyPackage.decode(bytes);
    } on ChoreographyPackageException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.choreoPackageImportError(e.message))),
        );
      }
      return;
    }

    if (!context.mounted) return;
    final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();

    final hasStyles = data.danceStyles.isNotEmpty;
    var createNewStyle = !hasStyles;
    String? mergeStyleId = hasStyles ? data.danceStyles.first.id : null;
    final nameController = TextEditingController(text: payload.styleName);

    try {
      final go = await showDialog<bool>(
        context: context,
        builder: (ctx) => StatefulBuilder(
          builder: (ctx, setSt) => AlertDialog(
            backgroundColor: AppColors.card,
            title: Text(str.uploadChoreography),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    str.importChoreographyStyleInfo(payload.styleName),
                    style: const TextStyle(color: Colors.white70, fontSize: 14),
                  ),
                  if (!hasStyles) ...[
                    const SizedBox(height: 12),
                    Text(
                      str.importChoreographyNoStylesYet,
                      style: const TextStyle(color: Colors.white60, fontSize: 13),
                    ),
                  ],
                  if (hasStyles) ...[
                    const SizedBox(height: 12),
                    if (!createNewStyle) ...[
                      Text(
                        str.importChoreographyMergeHint,
                        style: const TextStyle(color: Colors.white60, fontSize: 12),
                      ),
                      const SizedBox(height: 8),
                    ],
                    RadioListTile<bool>(
                      title: Text(
                        str.importChoreographyPickTargetStyle,
                        style: const TextStyle(fontSize: 14, color: Colors.white),
                      ),
                      value: false,
                      groupValue: createNewStyle,
                      activeColor: AppColors.accent,
                      onChanged: (_) => setSt(() => createNewStyle = false),
                    ),
                    if (!createNewStyle) ...[
                      const SizedBox(height: 4),
                      DropdownButtonFormField<String>(
                        value: mergeStyleId,
                        decoration: InputDecoration(
                          labelText: str.style,
                          border: const OutlineInputBorder(),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: AppRadius.radiusMd,
                            borderSide: const BorderSide(color: AppColors.cardBorder),
                          ),
                        ),
                        dropdownColor: AppColors.card,
                        isExpanded: true,
                        items: data.danceStyles
                            .map(
                              (s) => DropdownMenuItem(
                                value: s.id,
                                child: Text(
                                  str.displayDanceStyleName(s.name),
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(color: Colors.white),
                                ),
                              ),
                            )
                            .toList(),
                        onChanged: (v) => setSt(() => mergeStyleId = v),
                      ),
                    ],
                    RadioListTile<bool>(
                      title: Text(
                        str.importChoreographyCreateNewStyle,
                        style: const TextStyle(fontSize: 14, color: Colors.white),
                      ),
                      value: true,
                      groupValue: createNewStyle,
                      activeColor: AppColors.accent,
                      onChanged: (_) => setSt(() => createNewStyle = true),
                    ),
                    if (createNewStyle) ...[
                      const SizedBox(height: 4),
                      Text(
                        str.importChoreographyNewStyleHintBody,
                        style: const TextStyle(color: Colors.white60, fontSize: 12),
                      ),
                    ],
                  ],
                  if (createNewStyle || !hasStyles) ...[
                    const SizedBox(height: 12),
                    TextField(
                      controller: nameController,
                      decoration: InputDecoration(
                        labelText: str.importChoreographyNewStyleNameHint,
                        hintText: payload.styleName,
                      ),
                      style: const TextStyle(color: Colors.white),
                    ),
                  ],
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: Text(str.cancel),
              ),
              ElevatedButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: Text(str.importChoreographyImport),
              ),
            ],
          ),
        ),
      );
      if (go != true || !context.mounted) return;

      final effectiveCreateNew = !hasStyles || createNewStyle;
      final err = await ref.read(appDataNotifierProvider.notifier).importChoreographyFromPackagePayload(
            payload,
            createNewStyle: effectiveCreateNew,
            mergeIntoStyleId: effectiveCreateNew ? null : mergeStyleId,
            newStyleName: effectiveCreateNew ? nameController.text : '',
          );
      if (!context.mounted) return;
      if (err != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.choreoPackageImportErrorExtra(err))),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.importChoreographySuccess)),
        );
      }
    } finally {
      nameController.dispose();
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
    required this.onTap,
    required this.onRename,
    required this.onShare,
    required this.onCopy,
    required this.onDelete,
  });

  final Choreography choreography;
  final String styleName;
  final String songTitle;
  final String shareTooltip;
  final VoidCallback onTap;
  final VoidCallback onRename;
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
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        choreography.name,
                        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 16),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '$styleName · $songTitle',
                        style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.drive_file_rename_outline, color: Colors.white70, size: 22),
                  onPressed: onRename,
                  style: IconButton.styleFrom(minimumSize: const Size(36, 36), padding: EdgeInsets.zero, tapTargetSize: MaterialTapTargetSize.shrinkWrap),
                ),
                IconButton(
                  tooltip: shareTooltip,
                  icon: const Icon(Icons.share, color: Colors.white70, size: 22),
                  onPressed: onShare,
                  style: IconButton.styleFrom(minimumSize: const Size(36, 36), padding: EdgeInsets.zero, tapTargetSize: MaterialTapTargetSize.shrinkWrap),
                ),
                IconButton(
                  icon: const Icon(Icons.copy, color: Colors.white70, size: 22),
                  onPressed: onCopy,
                  style: IconButton.styleFrom(minimumSize: const Size(36, 36), padding: EdgeInsets.zero, tapTargetSize: MaterialTapTargetSize.shrinkWrap),
                ),
                IconButton(
                  icon: const Icon(Icons.delete_outline, color: Colors.white54, size: 22),
                  onPressed: onDelete,
                  style: IconButton.styleFrom(minimumSize: const Size(36, 36), padding: EdgeInsets.zero, tapTargetSize: MaterialTapTargetSize.shrinkWrap),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
