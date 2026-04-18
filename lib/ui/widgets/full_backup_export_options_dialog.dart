import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/full_backup_export_subset.dart';

/// Диалог выбора содержимого полного архива перед выгрузкой.
Future<AppData?> showFullBackupExportOptionsDialog({
  required BuildContext context,
  required AppStrings str,
  required AppData appData,
}) {
  return showDialog<AppData>(
    context: context,
    useRootNavigator: true,
    builder: (ctx) => _FullBackupExportOptionsDialog(str: str, appData: appData),
  );
}

class _FullBackupExportOptionsDialog extends StatefulWidget {
  const _FullBackupExportOptionsDialog({
    required this.str,
    required this.appData,
  });

  final AppStrings str;
  final AppData appData;

  @override
  State<_FullBackupExportOptionsDialog> createState() =>
      _FullBackupExportOptionsDialogState();
}

class _FullBackupExportOptionsDialogState
    extends State<_FullBackupExportOptionsDialog> {
  String? _filterMoveStyleId;
  String? _filterSongStyleId;
  String? _filterChoreoStyleId;

  late final TextEditingController _moveSearchCtrl;
  late final TextEditingController _songSearchCtrl;
  late final TextEditingController _choreoSearchCtrl;

  final Set<String> _selectedMoveIds = {};
  final Set<String> _selectedSongIds = {};
  final Set<String> _selectedChoreoIds = {};

  @override
  void initState() {
    super.initState();
    _moveSearchCtrl = TextEditingController();
    _songSearchCtrl = TextEditingController();
    _choreoSearchCtrl = TextEditingController();
    _moveSearchCtrl.addListener(() => setState(() {}));
    _songSearchCtrl.addListener(() => setState(() {}));
    _choreoSearchCtrl.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _moveSearchCtrl.dispose();
    _songSearchCtrl.dispose();
    _choreoSearchCtrl.dispose();
    super.dispose();
  }

  String get _searchMoves => _moveSearchCtrl.text;
  String get _searchSongs => _songSearchCtrl.text;
  String get _searchChoreos => _choreoSearchCtrl.text;

  DanceStyle? _styleById(String? id) {
    if (id == null) return null;
    for (final s in widget.appData.danceStyles) {
      if (s.id == id) return s;
    }
    return null;
  }

  List<({DanceStyle style, Move move})> _visibleMoves() {
    final q = _searchMoves.trim().toLowerCase();
    final out = <({DanceStyle style, Move move})>[];
    for (final s in widget.appData.danceStyles) {
      if (_filterMoveStyleId != null && s.id != _filterMoveStyleId) continue;
      for (final m in s.moves) {
        if (q.isNotEmpty && !m.name.toLowerCase().contains(q)) continue;
        out.add((style: s, move: m));
      }
    }
    out.sort((a, b) {
      final c = a.style.name.compareTo(b.style.name);
      if (c != 0) return c;
      return a.move.name.compareTo(b.move.name);
    });
    return out;
  }

  List<Song> _visibleSongs() {
    final q = _searchSongs.trim().toLowerCase();
    final st = _styleById(_filterSongStyleId);
    final list = widget.appData.songs.where((song) {
      if (st != null && song.danceStyle != st.name) return false;
      if (q.isNotEmpty && !song.title.toLowerCase().contains(q)) return false;
      return true;
    }).toList();
    list.sort((a, b) => a.title.compareTo(b.title));
    return list;
  }

  List<Choreography> _visibleChoreos() {
    final q = _searchChoreos.trim().toLowerCase();
    final list = widget.appData.choreographies.where((c) {
      if (_filterChoreoStyleId != null && c.styleId != _filterChoreoStyleId) {
        return false;
      }
      if (q.isNotEmpty && !c.name.toLowerCase().contains(q)) return false;
      return true;
    }).toList();
    list.sort((a, b) => a.name.compareTo(b.name));
    return list;
  }

  void _selectAllMovesForFilter() {
    setState(() {
      for (final e in _visibleMoves()) {
        _selectedMoveIds.add(e.move.id);
      }
    });
  }

  void _selectAllSongsVisible() {
    setState(() {
      for (final s in _visibleSongs()) {
        _selectedSongIds.add(s.id);
      }
    });
  }

  void _selectAllChoreosVisible() {
    setState(() {
      for (final c in _visibleChoreos()) {
        _selectedChoreoIds.add(c.id);
      }
    });
  }

  void _tryExport() {
    final subset = buildFullBackupExportSubset(
      source: widget.appData,
      selectedMoveIds: _selectedMoveIds,
      selectedSongIds: _selectedSongIds,
      selectedChoreographyIds: _selectedChoreoIds,
    );
    if (isFullBackupExportSubsetEmpty(subset)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(widget.str.fullBackupExportNothingSelected)),
      );
      return;
    }
    Navigator.of(context).pop(subset);
  }

  void _exportEverything() {
    final allMoves = <String>{
      for (final s in widget.appData.danceStyles) for (final m in s.moves) m.id,
    };
    final allSongs = widget.appData.songs.map((s) => s.id).toSet();
    final allChoreos = widget.appData.choreographies.map((c) => c.id).toSet();
    final subset = buildFullBackupExportSubset(
      source: widget.appData,
      selectedMoveIds: allMoves,
      selectedSongIds: allSongs,
      selectedChoreographyIds: allChoreos,
    );
    Navigator.of(context).pop(subset);
  }

  Widget _styleDropdown({
    required AppStrings str,
    required String? value,
    required ValueChanged<String?> onChanged,
  }) {
    final styles = widget.appData.danceStyles;
    return InputDecorator(
      decoration: InputDecoration(
        filled: true,
        fillColor: AppColors.background,
        border: OutlineInputBorder(
          borderRadius: AppRadius.radiusSm,
          borderSide: const BorderSide(color: AppColors.cardBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: AppRadius.radiusSm,
          borderSide: const BorderSide(color: AppColors.cardBorder),
        ),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String?>(
          isExpanded: true,
          value: value,
          dropdownColor: AppColors.card,
          items: [
            DropdownMenuItem<String?>(
              value: null,
              child: Text(
                str.fullBackupExportOptionsAllStyles,
                style: const TextStyle(color: Colors.white),
              ),
            ),
            for (final s in styles)
              DropdownMenuItem<String?>(
                value: s.id,
                child: Text(
                  s.name,
                  style: const TextStyle(color: Colors.white),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
          ],
          onChanged: onChanged,
        ),
      ),
    );
  }

  Widget _searchField({
    required TextEditingController controller,
    required String hint,
  }) {
    return TextField(
      controller: controller,
      style: const TextStyle(color: Colors.white),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: TextStyle(color: Colors.white.withValues(alpha: 0.45)),
        filled: true,
        fillColor: AppColors.background,
        isDense: true,
        prefixIcon:
            Icon(Icons.search, color: Colors.white.withValues(alpha: 0.5)),
        border: OutlineInputBorder(
          borderRadius: AppRadius.radiusSm,
          borderSide: const BorderSide(color: AppColors.cardBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: AppRadius.radiusSm,
          borderSide: const BorderSide(color: AppColors.cardBorder),
        ),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final str = widget.str;
    final visibleMoves = _visibleMoves();
    final visibleSongs = _visibleSongs();
    final visibleChoreos = _visibleChoreos();

    return AlertDialog(
      backgroundColor: AppColors.card,
      title: Text(
        str.fullBackupExportOptionsTitle,
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.bold,
        ),
      ),
      content: SizedBox(
        width: double.maxFinite,
        height: MediaQuery.sizeOf(context).height * 0.62,
        child: ListView(
          children: [
            Text(
              str.fullBackupExportOptionsIntro,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.85),
                fontSize: 14,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                onPressed: _exportEverything,
                child: Text(
                  str.fullBackupExportEverythingShortcut,
                  style: const TextStyle(color: AppColors.accent),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              str.fullBackupExportOptionsElementsSection,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              str.fullBackupExportOptionsStyleFilter,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.75),
                fontSize: 12,
              ),
            ),
            const SizedBox(height: 6),
            _styleDropdown(
              str: str,
              value: _filterMoveStyleId,
              onChanged: (v) => setState(() => _filterMoveStyleId = v),
            ),
            const SizedBox(height: 8),
            _searchField(
              controller: _moveSearchCtrl,
              hint: str.searchHint,
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: _selectAllMovesForFilter,
                icon: const Icon(Icons.select_all_rounded, color: AppColors.accent),
                label: Text(
                  str.fullBackupExportOptionsSelectAllInStyle,
                  style: const TextStyle(color: AppColors.accent),
                ),
              ),
            ),
            const SizedBox(height: 6),
            if (visibleMoves.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  '—',
                  style: TextStyle(color: Colors.white.withValues(alpha: 0.5)),
                ),
              )
            else
              ...visibleMoves.map((e) {
                final m = e.move;
                final checked = _selectedMoveIds.contains(m.id);
                return CheckboxListTile(
                  value: checked,
                  onChanged: (v) {
                    setState(() {
                      if (v == true) {
                        _selectedMoveIds.add(m.id);
                      } else {
                        _selectedMoveIds.remove(m.id);
                      }
                    });
                  },
                  fillColor: WidgetStateProperty.resolveWith((states) {
                    if (states.contains(WidgetState.selected)) {
                      return AppColors.accent;
                    }
                    return null;
                  }),
                  checkColor: Colors.white,
                  title: Text(
                    m.name,
                    style: const TextStyle(color: Colors.white, fontSize: 15),
                  ),
                  subtitle: Text(
                    '${e.style.name} · ${m.level}',
                    style: TextStyle(color: Colors.white.withValues(alpha: 0.65)),
                  ),
                  controlAffinity: ListTileControlAffinity.leading,
                  contentPadding: EdgeInsets.zero,
                );
              }),
            const SizedBox(height: 20),
            Text(
              str.fullBackupExportOptionsMusicSection,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              str.fullBackupExportOptionsStyleFilter,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.75),
                fontSize: 12,
              ),
            ),
            const SizedBox(height: 6),
            _styleDropdown(
              str: str,
              value: _filterSongStyleId,
              onChanged: (v) => setState(() => _filterSongStyleId = v),
            ),
            const SizedBox(height: 8),
            _searchField(
              controller: _songSearchCtrl,
              hint: str.searchHint,
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: _selectAllSongsVisible,
                icon: const Icon(Icons.select_all_rounded, color: AppColors.accent),
                label: Text(
                  str.fullBackupExportOptionsSelectAllFiltered,
                  style: const TextStyle(color: AppColors.accent),
                ),
              ),
            ),
            const SizedBox(height: 6),
            if (visibleSongs.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  '—',
                  style: TextStyle(color: Colors.white.withValues(alpha: 0.5)),
                ),
              )
            else
              ...visibleSongs.map((song) {
                final checked = _selectedSongIds.contains(song.id);
                return CheckboxListTile(
                  value: checked,
                  onChanged: (v) {
                    setState(() {
                      if (v == true) {
                        _selectedSongIds.add(song.id);
                      } else {
                        _selectedSongIds.remove(song.id);
                      }
                    });
                  },
                  fillColor: WidgetStateProperty.resolveWith((states) {
                    if (states.contains(WidgetState.selected)) {
                      return AppColors.accent;
                    }
                    return null;
                  }),
                  checkColor: Colors.white,
                  title: Text(
                    song.title,
                    style: const TextStyle(color: Colors.white, fontSize: 15),
                  ),
                  subtitle: Text(
                    song.danceStyle,
                    style: TextStyle(color: Colors.white.withValues(alpha: 0.65)),
                  ),
                  controlAffinity: ListTileControlAffinity.leading,
                  contentPadding: EdgeInsets.zero,
                );
              }),
            const SizedBox(height: 20),
            Text(
              str.fullBackupExportOptionsChoreoSection,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              str.fullBackupExportOptionsStyleFilter,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.75),
                fontSize: 12,
              ),
            ),
            const SizedBox(height: 6),
            _styleDropdown(
              str: str,
              value: _filterChoreoStyleId,
              onChanged: (v) => setState(() => _filterChoreoStyleId = v),
            ),
            const SizedBox(height: 8),
            _searchField(
              controller: _choreoSearchCtrl,
              hint: str.searchHint,
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: _selectAllChoreosVisible,
                icon: const Icon(Icons.select_all_rounded, color: AppColors.accent),
                label: Text(
                  str.fullBackupExportOptionsSelectAllFiltered,
                  style: const TextStyle(color: AppColors.accent),
                ),
              ),
            ),
            const SizedBox(height: 6),
            if (visibleChoreos.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(
                  '—',
                  style: TextStyle(color: Colors.white.withValues(alpha: 0.5)),
                ),
              )
            else
              ...visibleChoreos.map((c) {
                final st = _styleById(c.styleId);
                final checked = _selectedChoreoIds.contains(c.id);
                return CheckboxListTile(
                  value: checked,
                  onChanged: (v) {
                    setState(() {
                      if (v == true) {
                        _selectedChoreoIds.add(c.id);
                      } else {
                        _selectedChoreoIds.remove(c.id);
                      }
                    });
                  },
                  fillColor: WidgetStateProperty.resolveWith((states) {
                    if (states.contains(WidgetState.selected)) {
                      return AppColors.accent;
                    }
                    return null;
                  }),
                  checkColor: Colors.white,
                  title: Text(
                    c.name,
                    style: const TextStyle(color: Colors.white, fontSize: 15),
                  ),
                  subtitle: st != null
                      ? Text(
                          st.name,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.65),
                          ),
                        )
                      : null,
                  controlAffinity: ListTileControlAffinity.leading,
                  contentPadding: EdgeInsets.zero,
                );
              }),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(str.cancel, style: const TextStyle(color: Colors.white70)),
        ),
        TextButton(
          onPressed: _tryExport,
          child: Text(
            str.fullBackupExportOptionsExport,
            style: const TextStyle(color: AppColors.accent),
          ),
        ),
      ],
    );
  }
}
