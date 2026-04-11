// Восстановить: скопировать в lib/ui/screens/elements/elements_screen.dart
// Часть checkpoint 03.02.2026 - Elements

import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/core/utils/file_copy_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/file_copy_platform_io.dart'
    as file_copy;
import 'package:midnight_dancer/core/utils/video_temp.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/video_temp_io.dart'
    as video_temp;
import 'package:midnight_dancer/ui/widgets/move_card.dart';
import 'package:midnight_dancer/ui/widgets/video_preview.dart';

const _levelOptions = [
  ('Beginner', 'Начинающий'),
  ('Intermediate', 'Средний'),
  ('Advanced', 'Профи'),
];

const _sortOptions = [
  ('name', 'По А-Я'),
  ('level', 'По уровню'),
];

/// Форма добавления/редактирования движения.
class _MoveFormSheet extends StatefulWidget {
  const _MoveFormSheet({
    required this.scrollController,
    required this.isEdit,
    required this.initialName,
    required this.initialLevel,
    required this.initialDescription,
    required this.initialVideoPath,
    required this.onSave,
    required this.onCancel,
  });

  final ScrollController scrollController;
  final bool isEdit;
  final String initialName;
  final String initialLevel;
  final String initialDescription;
  final String? initialVideoPath;
  final void Function(
    String name,
    String level,
    String description,
    String? videoPath,
    Uint8List? videoBytes,
  ) onSave;
  final VoidCallback onCancel;

  @override
  State<_MoveFormSheet> createState() => _MoveFormSheetState();
}

class _MoveFormSheetState extends State<_MoveFormSheet> {
  late TextEditingController _nameController;
  late TextEditingController _descController;
  late String _level;
  String? _videoPath;
  Uint8List? _videoBytes;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.initialName);
    _descController = TextEditingController(text: widget.initialDescription);
    _level = widget.initialLevel;
    _videoPath = widget.initialVideoPath;
    _nameController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _nameController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _pickVideo() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.video,
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
        });
      }
    }
  }

  void _doSave() {
    final name = _nameController.text.trim();
    if (name.isEmpty) return;
    widget.onSave(name, _level, _descController.text.trim(), _videoPath, _videoBytes);
  }

  @override
  Widget build(BuildContext context) {
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
            widget.isEdit ? 'Изменить элемент' : 'Добавить элемент',
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _nameController,
            decoration: const InputDecoration(
              labelText: 'Название',
              hintText: 'Название элемента',
            ),
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            value: _level,
            decoration: const InputDecoration(labelText: 'Уровень'),
            dropdownColor: AppColors.card,
            items: _levelOptions
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
              _videoPath != null || _videoBytes != null ? 'Видео выбрано' : 'Выбрать видео',
            ),
          ),
          if (_videoPath != null && _videoPath!.isNotEmpty) ...[
            const SizedBox(height: 16),
            VideoPreview(videoPath: _videoPath!, initialSpeed: 1.0),
          ],
          const SizedBox(height: 16),
          TextField(
            controller: _descController,
            decoration: const InputDecoration(
              labelText: 'Описание',
              hintText: 'Опишите детали шага...',
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
                  child: const Text('Сохранить'),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: OutlinedButton(
                  onPressed: widget.onCancel,
                  child: const Text('Отмена'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

const _filterLevelOptions = [
  ('All', 'Все уровни'),
  ('Beginner', 'Начинающий'),
  ('Intermediate', 'Средний'),
  ('Advanced', 'Профи'),
];

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

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: asyncData.when(
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppColors.accent),
        ),
        error: (e, _) => Center(
          child: Text('Ошибка: $e', style: TextStyle(color: AppColors.accent)),
        ),
        data: (data) {
          final styles = data.danceStyles;
          if (styles.isEmpty) {
            return _buildEmptyState(context);
          }
          final selectedId = _selectedStyleId ?? (styles.isEmpty ? null : styles.first.id);
          DanceStyle? style;
          for (final s in styles) {
            if (s.id == selectedId) {
              style = s;
              break;
            }
          }
          style ??= styles.isEmpty ? null : styles.first;
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
                      _buildStyleSelector(context, styles, selectedId),
                    ]),
                  ),
                ),
                if (style != null) ...[
                  SliverPadding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    sliver: SliverToBoxAdapter(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const SizedBox(height: 16),
                          _buildFiltersAndAdd(context, style),
                          const SizedBox(height: 16),
                        ],
                      ),
                    ),
                  ),
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    sliver: _buildMovesGridSliver(context, style),
                  ),
                ],
              ],
            ),
          );
        },
        ),
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.format_list_bulleted, size: 64, color: AppColors.textSecondary),
            const SizedBox(height: 16),
            Text(
              'Добавьте первый стиль',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: AppColors.accent,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Например: Сальса, Бачата, Кизомба',
              style: TextStyle(color: AppColors.textSecondary),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _showNewStyleDialogInner,
              icon: const Icon(Icons.add),
              label: const Text('Новый стиль'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Элементы',
          style: TextStyle(
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
                  'Текущий стиль',
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
                          s.name,
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
          if (styles.length > 1 && selectedId != null)
            IconButton(
              onPressed: () => _confirmDeleteStyle(context, selectedId),
              icon: const Icon(Icons.delete_outline, color: Colors.white54),
              tooltip: 'Удалить стиль',
            ),
          const SizedBox(width: 8),
          Flexible(
            child: ElevatedButton.icon(
              onPressed: _showNewStyleDialogInner,
              icon: const Icon(Icons.add, size: 18),
              label: const FittedBox(
                fit: BoxFit.scaleDown,
                child: Text('Новый'),
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

  Widget _buildFiltersAndAdd(BuildContext context, DanceStyle style) {
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
                    hintText: 'Поиск...',
                    prefixIcon: const Icon(Icons.search, color: AppColors.textSecondary),
                    isDense: true,
                  ),
                  style: const TextStyle(color: Colors.white),
                  onChanged: (v) => setState(() => _searchQuery = v),
                ),
              ),
              const SizedBox(width: 8),
              Flexible(
                child: ElevatedButton.icon(
                  onPressed: () => _openAddMoveDialog(style.id),
                  icon: const Icon(Icons.add, size: 18),
                  label: const FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text('Добавить'),
                  ),
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
                    value: _filterLevel,
                    isExpanded: true,
                    dropdownColor: AppColors.card,
                    items: _filterLevelOptions
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
                    value: _sortBy,
                    isExpanded: true,
                    dropdownColor: AppColors.card,
                    items: _sortOptions
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

  List<Move> _filteredMoves(DanceStyle style) {
    var moves = List<Move>.from(style.moves);
    if (_filterLevel != 'All') {
      moves = moves.where((m) => m.level == _filterLevel).toList();
    }
    if (_searchQuery.isNotEmpty) {
      moves = moves
          .where((m) => m.name.toLowerCase().contains(_searchQuery.toLowerCase()))
          .toList();
    }
    moves.sort((a, b) {
      if (_sortBy == 'name') return a.name.compareTo(b.name);
      const order = {'Beginner': 1, 'Intermediate': 2, 'Advanced': 3};
      return (order[a.level] ?? 0).compareTo(order[b.level] ?? 0);
    });
    return moves;
  }

  Widget _buildMovesGrid(BuildContext context, DanceStyle style) {
    final moves = _filteredMoves(style);
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 0.85,
        crossAxisSpacing: 16,
        mainAxisSpacing: 16,
      ),
      itemCount: moves.length,
      itemBuilder: (_, i) => _buildMoveCard(moves[i], style),
    );
  }

  SliverGrid _buildMovesGridSliver(BuildContext context, DanceStyle style) {
    final moves = _filteredMoves(style);
    return SliverGrid(
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 0.85,
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
    return MoveCard(
      move: move,
      styleId: style.id,
      onEdit: () => _openEditMoveDialog(move, style.id),
      onDelete: () => _showDeleteConfirm((move.id, style.id, move.name)),
      onVideoUnavailable: () async {
        await ref.read(appDataNotifierProvider.notifier).clearVideoForMove(style.id, move.id);
      },
    );
  }

  Future<void> _confirmDeleteStyle(BuildContext context, String styleId) async {
    final data = ref.read(appDataNotifierProvider).valueOrNull;
    if (data != null && data.danceStyles.length <= 1) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Нельзя удалить последний стиль!')),
      );
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Удалить стиль?'),
        content: const Text(
          'Вы уверены, что хотите удалить стиль и все его элементы?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Отмена'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Удалить', style: TextStyle(color: Colors.red)),
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
    final isEdit = _editingMove != null;
    final name = _moveName;
    final level = _moveLevel;
    final desc = _moveDescription;
    final videoPath = _pickedVideoPath ?? (isEdit ? _editingMove?.$1?.videoUri : null);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
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
          onSave: (n, l, d, path, bytes) async {
            await _saveMoveFromSheet(ctx, n, l, d, videoPath: path, videoBytes: bytes);
          },
          onCancel: () => Navigator.pop(ctx),
        ),
      ),
    ).then((_) {
      setState(() {
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
  }) async {
    if (name.trim().isEmpty) return;
    final styleId = _editingMove?.$2 ?? _addingMoveStyleId;
    if (styleId == null || styleId.isEmpty) return;
    final notifier = ref.read(appDataNotifierProvider.notifier);

    final moveId = _editingMove?.$1?.id ?? 'move-${DateTime.now().millisecondsSinceEpoch}';
    final move = Move(
      id: moveId,
      name: name.trim(),
      level: level,
      description: description.trim().isEmpty ? null : description.trim(),
      videoUri: null,
    );

    try {
      if (_editingMove != null) {
        await notifier.updateMove(styleId, move, videoBytes: videoBytes, videoPath: videoPath);
      } else {
        await notifier.addMove(styleId, move, videoBytes: videoBytes, videoPath: videoPath);
      }
    } catch (e) {
      if (sheetContext.mounted) {
        ScaffoldMessenger.of(sheetContext).showSnackBar(
          SnackBar(content: Text('Ошибка сохранения: $e')),
        );
      }
      return;
    }
    if (sheetContext.mounted) Navigator.pop(sheetContext);
  }


  Future<void> _showNewStyleDialogInner() async {
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) {
        var n = '';
        return AlertDialog(
          backgroundColor: AppColors.card,
          title: const Text('Новый стиль'),
          content: TextField(
            autofocus: true,
            decoration: const InputDecoration(
              labelText: 'Название стиля',
              hintText: 'Например: Сальса',
            ),
            onChanged: (v) => n = v,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Отмена'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(ctx, n.trim()),
              child: const Text('Создать'),
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
    final (moveId, styleId, name) = p;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: const Text('Удалить элемент?'),
        content: Text('Вы собираетесь удалить "$name". Это нельзя отменить.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Отмена'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Удалить навсегда', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (ok == true && mounted) {
      await ref.read(appDataNotifierProvider.notifier).deleteMove(styleId, moveId);
    }
  }
}
