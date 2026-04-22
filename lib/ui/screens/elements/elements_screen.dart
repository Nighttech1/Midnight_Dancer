import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/core/utils/file_copy_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/file_copy_platform_io.dart'
    as file_copy;
import 'package:midnight_dancer/core/utils/video_temp.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/video_temp_io.dart'
    as video_temp;
import 'package:midnight_dancer/core/utils/formatters.dart';
import 'package:midnight_dancer/ui/widgets/move_card.dart';
import 'package:midnight_dancer/ui/widgets/video_preview.dart';

/// Форма добавления/редактирования движения.
class _MoveFormSheet extends ConsumerStatefulWidget {
  const _MoveFormSheet({
    required this.scrollController,
    required this.isEdit,
    required this.initialName,
    required this.initialLevel,
    required this.initialDescription,
    required this.initialVideoPath,
    required this.initialMasteryPercent,
    this.onMasteryPercentLive,
    required this.initialPersistStyleId,
    this.editStyleChoices,
    required this.onSave,
    required this.onCancel,
  });

  final ScrollController scrollController;
  final bool isEdit;
  final String initialName;
  final String initialLevel;
  final String initialDescription;
  final String? initialVideoPath;
  final int initialMasteryPercent;
  final ValueChanged<int>? onMasteryPercentLive;
  /// Стиль, в котором сохраняется элемент (редактирование: можно сменить).
  final String initialPersistStyleId;
  final List<DanceStyle>? editStyleChoices;
  final void Function(
    String name,
    String level,
    String description,
    String? videoPath,
    Uint8List? videoBytes,
    int masteryPercent,
    String persistStyleId,
  ) onSave;
  final VoidCallback onCancel;

  @override
  ConsumerState<_MoveFormSheet> createState() => _MoveFormSheetState();
}

class _MoveFormSheetState extends ConsumerState<_MoveFormSheet> {
  late TextEditingController _nameController;
  late TextEditingController _descController;
  late String _level;
  late double _masterySlider;
  late String _persistStyleId;
  String? _videoPath;
  Uint8List? _videoBytes;
  String? _previewVideoPath;
  bool _resolvingPreview = false;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.initialName);
    _descController = TextEditingController(text: widget.initialDescription);
    _level = widget.initialLevel;
    _persistStyleId = widget.initialPersistStyleId;
    _videoPath = widget.initialVideoPath;
    _masterySlider = widget.initialMasteryPercent.clamp(0, 100).toDouble();
    _previewVideoPath = _pathUsableByVideoPlayer(widget.initialVideoPath);
    _nameController.addListener(() => setState(() {}));
    _resolveStorageVideoPreviewIfNeeded();
  }

  static String? _pathUsableByVideoPlayer(String? p) {
    if (p == null || p.isEmpty) return null;
    if (p.startsWith('content:') || p.startsWith('/')) return p;
    return null;
  }

  Future<void> _resolveStorageVideoPreviewIfNeeded() async {
    final path = widget.initialVideoPath;
    if (path == null || path.isEmpty) return;
    if (path.startsWith('content:') || path.startsWith('/')) return;
    if (!mounted) return;
    setState(() => _resolvingPreview = true);
    final notifier = ref.read(appDataNotifierProvider.notifier);
    final bytes = await notifier.loadVideo(path);
    if (!mounted) return;
    if (bytes == null || bytes.isEmpty) {
      setState(() {
        _resolvingPreview = false;
        _previewVideoPath = null;
      });
      return;
    }
    final tmp = await video_temp.writeVideoTemp(bytes);
    if (!mounted) return;
    setState(() {
      _resolvingPreview = false;
      _previewVideoPath = tmp ?? path;
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _pickVideo() async {
    final str = ref.read(appStringsProvider);
    try {
      // Как и для аудио: FileType.video на iOS может уводить в приватные API каталогов; custom → документ-пикер.
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const ['mp4', 'mov', 'm4v'],
        allowMultiple: false,
        withData: kIsWeb,
      );
      if (result != null) {
        final file = result.files.single;
        String? path = file.path;
        Uint8List? bytes = file.bytes;
        if (!kIsWeb && path != null && path.isNotEmpty) {
          if (path.startsWith('content:')) {
            await file_copy.takeUriPermission(path);
          }
        } else if (kIsWeb && bytes != null && bytes.isNotEmpty) {
          path = await video_temp.writeVideoTemp(bytes);
        }
        if (mounted) {
          setState(() {
            _videoPath = path;
            _videoBytes = bytes;
            _previewVideoPath = path;
            _resolvingPreview = false;
          });
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.videoPickErrorSnackbar(e.toString()))),
        );
      }
    }
  }

  void _doSave() {
    final name = _nameController.text.trim();
    if (name.isEmpty) return;
    final m = _masterySlider.round().clamp(0, 100);
    widget.onSave(
      name,
      _level,
      _descController.text.trim(),
      _videoPath,
      _videoBytes,
      m,
      _persistStyleId,
    );
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    return Container(
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: ListView(
        controller: widget.scrollController,
        padding: const EdgeInsets.all(24),
        children: [
          Text(
            widget.isEdit ? str.editElement : str.addOrEditElement,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 20),
          if (widget.isEdit &&
              widget.editStyleChoices != null &&
              widget.editStyleChoices!.length > 1) ...[
            DropdownButtonFormField<String>(
              value: _persistStyleId,
              decoration: InputDecoration(labelText: str.elementStyleLabel),
              dropdownColor: AppColors.card,
              isExpanded: true,
              items: widget.editStyleChoices!
                  .map(
                    (s) => DropdownMenuItem(
                      value: s.id,
                      child: Text(
                        str.displayDanceStyleName(s.name),
                        style: const TextStyle(color: Colors.white),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  )
                  .toList(),
              onChanged: (v) {
                if (v != null) setState(() => _persistStyleId = v);
              },
            ),
            const SizedBox(height: 16),
          ],
          TextField(
            controller: _nameController,
            decoration: InputDecoration(
              labelText: str.nameLabel,
              hintText: str.elementNameHint,
            ),
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            value: _level,
            decoration: InputDecoration(labelText: str.levelLabel),
            dropdownColor: AppColors.card,
            items: str.filterLevelOptions
                .where((e) => e.$1 != 'All')
                .map((e) => DropdownMenuItem(
                      value: e.$1,
                      child: Text(e.$2, style: const TextStyle(color: Colors.white)),
                    ))
                .toList(),
            onChanged: (v) => setState(() => _level = v ?? 'Beginner'),
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _pickVideo,
            icon: const Icon(Icons.videocam),
            label: Text(
              _videoPath != null || _videoBytes != null ? str.videoSelected : str.pickVideo,
            ),
          ),
          if (_resolvingPreview) ...[
            const SizedBox(height: 16),
            const SizedBox(
              height: 180,
              child: Center(child: CircularProgressIndicator(color: AppColors.accent)),
            ),
          ] else if (_previewVideoPath != null && _previewVideoPath!.isNotEmpty) ...[
            const SizedBox(height: 16),
            VideoPreview(videoPath: _previewVideoPath!, initialSpeed: 1.0),
          ],
          const SizedBox(height: 20),
          Text(
            str.elementMasteryProgress,
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    trackHeight: 10,
                    thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 14),
                    overlayShape: const RoundSliderOverlayShape(overlayRadius: 22),
                    activeTrackColor: AppColors.accent,
                    inactiveTrackColor: Colors.white24,
                    thumbColor: Colors.white,
                    overlayColor: AppColors.accent.withOpacity(0.22),
                  ),
                  child: Slider(
                    value: _masterySlider.clamp(0, 100) / 100,
                    onChanged: (v) => setState(() => _masterySlider = v * 100),
                    onChangeEnd: (v) {
                      final p = (v * 100).round().clamp(0, 100);
                      widget.onMasteryPercentLive?.call(p);
                    },
                  ),
                ),
              ),
              SizedBox(
                width: 52,
                child: Text(
                  '${_masterySlider.round().clamp(0, 100)}%',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 17,
                    color: AppColors.accent,
                  ),
                  textAlign: TextAlign.end,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _descController,
            decoration: InputDecoration(
              labelText: str.descriptionLabel,
              hintText: str.descriptionHint,
              alignLabelWithHint: true,
            ),
            maxLines: 3,
          ),
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: ElevatedButton(
                  onPressed: _nameController.text.trim().isEmpty ? null : _doSave,
                  child: Text(str.save),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: OutlinedButton(
                  onPressed: widget.onCancel,
                  child: Text(str.cancel),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class ElementsScreen extends ConsumerStatefulWidget {
  const ElementsScreen({super.key});

  @override
  ConsumerState<ElementsScreen> createState() => _ElementsScreenState();
}

class _ElementsScreenState extends ConsumerState<ElementsScreen> {
  String? _selectedStyleId;
  String _filterLevel = 'All';
  String _sortBy = 'name';
  String _searchQuery = '';
  (Move? move, String? styleId)? _editingMove;
  String? _addingMoveStyleId;
  String _moveName = '';
  String _moveLevel = 'Beginner';
  String _moveDescription = '';
  String? _pickedVideoPath;

  @override
  Widget build(BuildContext context) {
    final asyncData = ref.watch(appDataNotifierProvider);
    final str = ref.watch(appStringsProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncData.when(
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppColors.accent),
        ),
        error: (e, _) => Center(
          child: Text('${str.errorPrefix}: $e', style: TextStyle(color: AppColors.accent)),
        ),
        data: (data) {
          final styles = data.danceStyles;
          if (styles.isEmpty) {
            return _buildEmptyState(context);
          }
          final selectedId = _selectedStyleId ?? styles.first.id;
          var dropdownStyleId = selectedId;
          if (!styles.any((s) => s.id == dropdownStyleId)) {
            dropdownStyleId = styles.first.id;
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (mounted) setState(() => _selectedStyleId = dropdownStyleId);
            });
          }
          DanceStyle? style;
          for (final s in styles) {
            if (s.id == dropdownStyleId) {
              style = s;
              break;
            }
          }
          style ??= styles.first;
          final selectedStyle = style;
          final levelKeys =
              str.filterLevelOptions.map((e) => e.$1).toSet();
          final sortKeys = str.sortOptions.map((e) => e.$1).toSet();
          final safeFilterLevel =
              dropdownValueOrFallback(_filterLevel, levelKeys, 'All');
          final safeSortBy =
              dropdownValueOrFallback(_sortBy, sortKeys, 'name');
          if (safeFilterLevel != _filterLevel || safeSortBy != _sortBy) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (!mounted) return;
              setState(() {
                if (!levelKeys.contains(_filterLevel)) _filterLevel = 'All';
                if (!sortKeys.contains(_sortBy)) _sortBy = 'name';
              });
            });
          }
          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(appDataNotifierProvider);
            },
            color: AppColors.accent,
            child: CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverPadding(
                  padding: const EdgeInsets.all(16),
                  sliver: SliverList(
                    delegate: SliverChildListDelegate([
                      _buildHeader(context),
                      const SizedBox(height: 16),
                      _buildStyleSelector(context, styles, dropdownStyleId),
                    ]),
                  ),
                ),
                SliverPadding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  sliver: SliverToBoxAdapter(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const SizedBox(height: 16),
                        _buildFiltersAndAdd(
                          context,
                          selectedStyle,
                          safeFilterLevel,
                          safeSortBy,
                        ),
                        const SizedBox(height: 16),
                      ],
                    ),
                  ),
                ),
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  sliver: _buildMovesGridSliver(
                    context,
                    selectedStyle,
                    safeFilterLevel,
                    safeSortBy,
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

  Widget _buildEmptyState(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.format_list_bulleted, size: 64, color: AppColors.textSecondary),
            const SizedBox(height: 16),
            Text(
              str.addFirstStyle,
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: AppColors.accent,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              str.styleNameHint,
              style: TextStyle(color: AppColors.textSecondary),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _showNewStyleDialogInner,
              icon: const Icon(Icons.add),
              label: Text(str.newStyle),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          str.elementsTitle,
          style: const TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
      ],
    );
  }

  Widget _buildStyleSelector(
    BuildContext context,
    List<DanceStyle> styles,
    String? selectedId,
  ) {
    final str = ref.watch(appStringsProvider);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.card.withOpacity(0.5),
        borderRadius: AppRadius.radiusMd,
        border: Border.all(color: AppColors.cardBorder),
      ),
      child: Row(
        children: [
          Expanded(
            flex: 1,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  str.currentStyle,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 8),
                DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    value: selectedId,
                    isExpanded: true,
                    isDense: true,
                    dropdownColor: AppColors.card,
                    items: styles.map((s) {
                      return DropdownMenuItem(
                        value: s.id,
                        child: Text(
                          str.displayDanceStyleName(s.name),
                          style: const TextStyle(color: Colors.white),
                          overflow: TextOverflow.ellipsis,
                        ),
                      );
                    }).toList(),
                    onChanged: (id) => setState(() => _selectedStyleId = id),
                  ),
                ),
              ],
            ),
          ),
          if (selectedId != null)
            IconButton(
              onPressed: () => _confirmDeleteStyle(context, selectedId),
              icon: const Icon(Icons.delete_outline, color: Colors.white54),
              tooltip: str.deleteStyleTooltip,
            ),
          const SizedBox(width: 8),
          Flexible(
            child: ElevatedButton.icon(
              onPressed: _showNewStyleDialogInner,
              icon: const Icon(Icons.add, size: 18),
              label: FittedBox(
                fit: BoxFit.scaleDown,
                child: Text(str.newLabel),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.accent,
                foregroundColor: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFiltersAndAdd(
    BuildContext context,
    DanceStyle style,
    String filterLevelForDropdown,
    String sortByForDropdown,
  ) {
    final str = ref.watch(appStringsProvider);
    return Container(
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
                child: TextField(
                  decoration: InputDecoration(
                    hintText: str.searchHint,
                    prefixIcon: const Icon(Icons.search, color: AppColors.textSecondary),
                    isDense: true,
                  ),
                  style: const TextStyle(color: Colors.white),
                  onChanged: (v) => setState(() => _searchQuery = v),
                ),
              ),
              const SizedBox(width: 8),
              ConstrainedBox(
                constraints: const BoxConstraints(minWidth: 88, minHeight: 48),
                child: ElevatedButton.icon(
                  onPressed: () => _openAddMoveDialog(style.id),
                  icon: const Icon(Icons.add, size: 18),
                  label: Text(str.addLabel),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              SizedBox(
                width: 140,
                child: DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    value: filterLevelForDropdown,
                    isExpanded: true,
                    dropdownColor: AppColors.card,
                    items: str.filterLevelOptions
                        .map((e) => DropdownMenuItem(
                              value: e.$1,
                              child: Text(e.$2, style: const TextStyle(color: Colors.white)),
                            ))
                        .toList(),
                    onChanged: (v) => setState(() => _filterLevel = v ?? 'All'),
                  ),
                ),
              ),
              SizedBox(
                width: 140,
                child: DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    value: sortByForDropdown,
                    isExpanded: true,
                    dropdownColor: AppColors.card,
                    items: str.sortOptions
                        .map((e) => DropdownMenuItem(
                              value: e.$1,
                              child: Text(e.$2, style: const TextStyle(color: Colors.white)),
                            ))
                        .toList(),
                    onChanged: (v) => setState(() => _sortBy = v ?? 'name'),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  List<Move> _filteredMoves(
    DanceStyle style,
    String filterLevel,
    String sortBy,
  ) {
    Move? pinned;
    final curId = style.currentMoveId;
    if (curId != null && curId.isNotEmpty) {
      for (final m in style.moves) {
        if (m.id == curId) {
          pinned = m;
          break;
        }
      }
    }
    var moves = List<Move>.from(style.moves);
    if (pinned != null) {
      moves.removeWhere((m) => m.id == pinned!.id);
    }
    if (filterLevel != 'All') {
      moves = moves.where((m) => m.level == filterLevel).toList();
    }
    if (_searchQuery.isNotEmpty) {
      final q = _searchQuery.toLowerCase();
      moves = moves.where((m) => m.name.toLowerCase().contains(q)).toList();
    }
    moves.sort((a, b) {
      if (sortBy == 'name') return a.name.compareTo(b.name);
      const order = {'Beginner': 1, 'Intermediate': 2, 'Advanced': 3};
      return (order[a.level] ?? 0).compareTo(order[b.level] ?? 0);
    });
    if (pinned != null) {
      return [pinned, ...moves];
    }
    return moves;
  }

  SliverGrid _buildMovesGridSliver(
    BuildContext context,
    DanceStyle style,
    String filterLevel,
    String sortBy,
  ) {
    final moves = _filteredMoves(style, filterLevel, sortBy);
    return SliverGrid(
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 0.66,
        crossAxisSpacing: 16,
        mainAxisSpacing: 16,
      ),
      delegate: SliverChildBuilderDelegate(
        (_, i) => _buildMoveCard(moves[i], style),
        childCount: moves.length,
      ),
    );
  }

  Widget _buildMoveCard(Move move, DanceStyle style) {
    final isCurrent = style.currentMoveId == move.id;
    return MoveCard(
      move: move,
      isCurrent: isCurrent,
      onEdit: () => _openEditMoveDialog(move, style.id),
      onDelete: () => _showDeleteConfirm((move.id, style.id, move.name)),
      onToggleCurrent: () async {
        final n = ref.read(appDataNotifierProvider.notifier);
        if (isCurrent) {
          await n.setDanceStyleCurrentMove(style.id, null);
        } else {
          await n.setDanceStyleCurrentMove(style.id, move.id);
        }
      },
    );
  }

  Future<void> _confirmDeleteStyle(BuildContext context, String styleId) async {
    final str = ref.read(appStringsProvider);
    final data = ref.read(appDataNotifierProvider).valueOrNull;
    if (data != null && data.danceStyles.length <= 1) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.cannotDeleteLastStyle)),
      );
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(str.deleteStyleConfirm),
        content: Text(
          str.deleteStyleMessage,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(str.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(str.delete, style: const TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (ok == true && mounted) {
      await ref.read(appDataNotifierProvider.notifier).deleteStyle(styleId);
      final data = ref.read(appDataNotifierProvider).valueOrNull;
      if (data != null && data.danceStyles.isNotEmpty) {
        setState(() => _selectedStyleId = data.danceStyles.first.id);
      }
    }
  }

  void _openAddMoveDialog(String styleId) {
    setState(() {
      _addingMoveStyleId = styleId;
      _editingMove = null;
      _moveName = '';
      _moveLevel = 'Beginner';
      _moveDescription = '';
      _pickedVideoPath = null;
    });
    _showMoveFormSheet();
  }

  void _openEditMoveDialog(Move move, String styleId) {
    setState(() {
      _addingMoveStyleId = null;
      _editingMove = (move, styleId);
      _moveName = move.name;
      _moveLevel = move.level;
      _moveDescription = move.description ?? '';
      _pickedVideoPath = null;
    });
    _showMoveFormSheet();
  }

  void _showMoveFormSheet() {
    if (!mounted) return;
    final isEdit = _editingMove != null;
    final name = _moveName;
    final level = _moveLevel;
    final desc = _moveDescription;
    final videoPath = _pickedVideoPath ?? (isEdit ? _editingMove?.$1?.videoUri : null);
    final editPair = _editingMove;
    final editMove = editPair?.$1;
    final editStyleId = editPair?.$2;
    final data = ref.read(appDataNotifierProvider).valueOrNull;
    final persistStyleId = isEdit
        ? (editStyleId ?? '')
        : (_addingMoveStyleId ??
            (data != null && data.danceStyles.isNotEmpty ? data.danceStyles.first.id : ''));
    final editChoices = isEdit ? data?.danceStyles : null;

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      barrierColor: Colors.black54,
      isDismissible: true,
      enableDrag: true,
      builder: (ctx) => DraggableScrollableSheet(
        initialChildSize: 0.9,
        minChildSize: 0.5,
        maxChildSize: 1,
        builder: (_, scrollController) => _MoveFormSheet(
          scrollController: scrollController,
          isEdit: isEdit,
          initialName: name,
          initialLevel: level,
          initialDescription: desc,
          initialVideoPath: videoPath,
          initialMasteryPercent: editMove?.masteryPercent ?? 0,
          initialPersistStyleId: persistStyleId,
          editStyleChoices: editChoices,
          onMasteryPercentLive: editMove != null && editStyleId != null
              ? (p) {
                  ref.read(appDataNotifierProvider.notifier).updateMoveMastery(
                        editStyleId,
                        editMove.id,
                        p,
                      );
                }
              : null,
          onSave: (n, l, d, path, bytes, mastery, sid) async {
            await _saveMoveFromSheet(ctx, n, l, d,
                videoPath: path, videoBytes: bytes, masteryPercent: mastery, persistStyleId: sid);
          },
          onCancel: () => Navigator.pop(ctx),
        ),
      ),
    ).then((_) {
      if (mounted) setState(() {
        _editingMove = null;
        _addingMoveStyleId = null;
        _pickedVideoPath = null;
      });
    });
  }


  Future<void> _saveMoveFromSheet(
    BuildContext sheetContext,
    String name,
    String level,
    String description, {
    String? videoPath,
    Uint8List? videoBytes,
    int masteryPercent = 0,
    required String persistStyleId,
  }) async {
    if (name.trim().isEmpty) return;
    if (persistStyleId.isEmpty) return;
    final notifier = ref.read(appDataNotifierProvider.notifier);

    final moveId = _editingMove?.$1?.id ?? 'move-${DateTime.now().millisecondsSinceEpoch}';
    final move = Move(
      id: moveId,
      name: name.trim(),
      level: level,
      description: description.trim().isEmpty ? null : description.trim(),
      videoUri: null,
      masteryPercent: masteryPercent.clamp(0, 100),
    );

    try {
      if (_editingMove != null) {
        final fromStyle = _editingMove!.$2;
        if (fromStyle == null || fromStyle.isEmpty) return;
        if (fromStyle != persistStyleId) {
          final mErr = await notifier.moveMoveBetweenStyles(
            fromStyleId: fromStyle,
            toStyleId: persistStyleId,
            moveId: moveId,
          );
          if (mErr != null) {
            if (sheetContext.mounted) {
              final str = ref.read(appStringsProvider);
              final msg = mErr == 'move_id_conflict'
                  ? str.moveTransferIdConflict
                  : str.saveErrorSnackbar(mErr);
              ScaffoldMessenger.of(sheetContext).showSnackBar(SnackBar(content: Text(msg)));
            }
            return;
          }
        }
        await notifier.updateMove(persistStyleId, move, videoBytes: videoBytes, videoPath: videoPath);
      } else {
        await notifier.addMove(persistStyleId, move, videoBytes: videoBytes, videoPath: videoPath);
      }
    } catch (e) {
      if (sheetContext.mounted) {
        final str = ref.read(appStringsProvider);
        ScaffoldMessenger.of(sheetContext).showSnackBar(
          SnackBar(content: Text(str.saveErrorSnackbar(e.toString()))),
        );
      }
      return;
    }
    if (sheetContext.mounted) Navigator.pop(sheetContext);
  }


  Future<void> _showNewStyleDialogInner() async {
    final str = ref.read(appStringsProvider);
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) {
        var n = '';
        return AlertDialog(
          backgroundColor: AppColors.card,
          title: Text(str.newStyle),
          content: TextField(
            autofocus: true,
            decoration: InputDecoration(
              labelText: str.styleNameLabel,
              hintText: str.styleNameExample,
            ),
            onChanged: (v) => n = v,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: Text(str.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.pop(ctx, n.trim()),
              child: Text(str.create),
            ),
          ],
        );
      },
    );
    if (name != null && name.isNotEmpty && mounted) {
      final style = DanceStyle(
        id: 'style-${DateTime.now().millisecondsSinceEpoch}',
        name: name,
        moves: [],
      );
      await ref.read(appDataNotifierProvider.notifier).addStyle(style);
      setState(() => _selectedStyleId = style.id);
    }
  }

  Future<void> _showDeleteConfirm((String, String, String) p) async {
    final str = ref.read(appStringsProvider);
    final (moveId, styleId, name) = p;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(str.deleteElementConfirm),
        content: Text(str.deleteElementMessage(name)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(str.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(str.deletePermanently, style: const TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (ok == true && mounted) {
      await ref.read(appDataNotifierProvider.notifier).deleteMove(styleId, moveId);
    }
  }
}
