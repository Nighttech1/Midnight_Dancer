import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/core/utils/formatters.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_ref.dart';

const _zoomOptions = [25, 50, 100, 200, 400];
const _basePixelsPerSecond = 18.0;

class SequenceEditorScreen extends ConsumerStatefulWidget {
  const SequenceEditorScreen({
    super.key,
    required this.choreography,
    required this.appData,
    required this.song,
    required this.onSave,
  });

  final Choreography choreography;
  final AppData appData;
  final Song song;
  final void Function(Choreography updated) onSave;

  @override
  ConsumerState<SequenceEditorScreen> createState() => _SequenceEditorScreenState();
}

class _SequenceEditorScreenState extends ConsumerState<SequenceEditorScreen> {
  late Choreography _choreo;
  int _zoomIndex = 2; // 100%
  bool _saving = false;

  double get _duration => _choreo.endTime - _choreo.startTime;
  double get _zoomPercent => _zoomOptions[_zoomIndex].toDouble();
  double get _pixelsPerSecond => _basePixelsPerSecond * (_zoomPercent / 100);
  double get _trackWidth => _duration * _pixelsPerSecond;

  @override
  void initState() {
    super.initState();
    _choreo = widget.choreography;
  }

  List<MapEntry<double, String>> get _sortedPoints {
    final list = _choreo.timeline.entries.toList();
    list.sort((a, b) => a.key.compareTo(b.key));
    return list;
  }

  List<DanceStyle> get _styles => widget.appData.danceStyles;

  String _timelineLabel(String raw) {
    final str = ref.watch(appStringsProvider);
    return ChoreographyTimelineRef.displayLabel(
      _styles,
      _choreo.styleId,
      raw,
      str.displayDanceStyleName,
    );
  }

  DanceStyle? _firstStyleWithMoves() {
    for (final s in _styles) {
      if (s.moves.isNotEmpty) return s;
    }
    return null;
  }

  String _defaultNewTimelineRef() {
    final st = _firstStyleWithMoves();
    if (st == null || st.moves.isEmpty) return '';
    return ChoreographyTimelineRef.encode(st.id, st.moves.first.id);
  }

  void _addPoint() {
    final time = (_choreo.startTime + _duration * 0.25).clamp(_choreo.startTime, _choreo.endTime);

    showDialog<void>(
      context: context,
      builder: (ctx) => _PointDialog(
        initialTimes: [time],
        initialTimelineValue: _defaultNewTimelineRef(),
        allStyles: _styles,
        choreographyStyleId: _choreo.styleId,
        startTime: _choreo.startTime,
        endTime: _choreo.endTime,
        removeTime: null,
        timeLabelStart: 1,
        onSave: (times, timelineValue) {
          final newMap = Map<double, String>.from(_choreo.timeline);
          for (final t in times) {
            newMap[t] = timelineValue;
          }
          setState(() => _choreo = _choreo.copyWith(timeline: newMap));
        },
        onDelete: null,
      ),
    );
  }

  void _editPoint(double time, String timelineRaw, {int pointIndex1Based = 1}) {
    showDialog<void>(
      context: context,
      builder: (ctx) => _PointDialog(
        initialTimes: [time],
        initialTimelineValue: timelineRaw,
        allStyles: _styles,
        choreographyStyleId: _choreo.styleId,
        startTime: _choreo.startTime,
        endTime: _choreo.endTime,
        removeTime: time,
        timeLabelStart: pointIndex1Based,
        onSave: (times, timelineValue) {
          final newMap = Map<double, String>.from(_choreo.timeline);
          newMap.remove(time);
          for (final t in times) {
            newMap[t] = timelineValue;
          }
          setState(() => _choreo = _choreo.copyWith(timeline: newMap));
        },
        onDelete: () {
          _deletePoint(time);
          Navigator.pop(ctx);
        },
      ),
    );
  }

  void _deletePoint(double time) {
    final newMap = Map<double, String>.from(_choreo.timeline);
    newMap.remove(time);
    setState(() => _choreo = _choreo.copyWith(timeline: newMap));
  }

  void _updateTrim(double start, double end) {
    final songDuration = widget.song.duration > 0 ? widget.song.duration : 300.0;
    start = start.clamp(0.0, songDuration);
    end = end.clamp(start, songDuration);
    final newMap = <double, String>{};
    for (final e in _choreo.timeline.entries) {
      if (e.key >= start && e.key <= end) newMap[e.key] = e.value;
    }
    setState(() => _choreo = _choreo.copyWith(startTime: start, endTime: end, timeline: newMap));
  }

  double _offsetFromTime(double time) {
    return (time - _choreo.startTime) * _pixelsPerSecond;
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      widget.onSave(_choreo);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _saveAndPop() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      widget.onSave(_choreo);
      // Родительский onSave после сохранения сам закрывает экран (pop)
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        await _saveAndPop();
      },
      child: Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(
        backgroundColor: AppColors.card,
        foregroundColor: Colors.white,
        title: Text(_choreo.name, overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(
            icon: _saving ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : const Icon(Icons.save),
            onPressed: _saving ? null : _save,
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _trimSection(),
            _zoomSection(),
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: _timelineSection(),
            ),
            _pointsListSection(),
          ],
        ),
      ),
    ),
    );
  }

  Widget _trimSection() {
    final l10n = ref.watch(appStringsProvider);
    final songMax = widget.song.duration > 0 ? widget.song.duration : 300.0;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: AppColors.card.withOpacity(0.5),
      child: Row(
        children: [
          SizedBox(
            width: 72,
            child: _TrimField(
              label: l10n.trimStart,
              value: _choreo.startTime,
              max: _choreo.endTime,
              onChanged: (v) => _updateTrim(v, _choreo.endTime),
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 72,
            child: _TrimField(
              label: l10n.trimEnd,
              value: _choreo.endTime,
              min: _choreo.startTime,
              max: songMax,
              onChanged: (v) => _updateTrim(_choreo.startTime, v),
            ),
          ),
          const Spacer(),
          ElevatedButton.icon(
            onPressed: _addPoint,
            icon: const Icon(Icons.add, size: 18),
            label: Text(l10n.addElement),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.accent,
              foregroundColor: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  Widget _zoomSection() {
    final l10n = ref.watch(appStringsProvider);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            Text('${l10n.scale}: ', style: const TextStyle(color: AppColors.textSecondary)),
            ...List.generate(_zoomOptions.length, (i) {
              final pct = _zoomOptions[i];
              return Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  label: Text('$pct%'),
                  selected: _zoomIndex == i,
                  onSelected: (_) => setState(() => _zoomIndex = i),
                  selectedColor: AppColors.accent,
                  labelStyle: TextStyle(color: _zoomIndex == i ? Colors.white : Colors.white70, fontSize: 12),
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  int get _timeScaleStepSeconds {
    if (_zoomPercent <= 25) return 10;
    if (_zoomPercent <= 50) return 5;
    if (_zoomPercent <= 100) return 2;
    return 1;
  }

  Widget _timelineSection() {
    final screenWidth = MediaQuery.of(context).size.width - 32;
    final width = math.max(_trackWidth, screenWidth);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: SizedBox(
        width: width,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: width,
              height: 88,
              child: Stack(
                key: ValueKey('timeline_${_choreo.startTime}_${_choreo.endTime}'),
                clipBehavior: Clip.none,
                children: [
                  Container(
                    width: width,
                    height: 88,
                    decoration: BoxDecoration(
                      color: AppColors.card,
                      borderRadius: AppRadius.radiusSm,
                      border: Border.all(color: AppColors.cardBorder),
                    ),
                  ),
                  _buildStartEndMarkers(width),
                  _buildTimelineSegments(width),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: _buildTimeScale(width),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTimeScale(double totalWidth) {
    const scaleHeight = 22.0;
    const tickHeight = 6.0;
    const labelStyle = TextStyle(color: Colors.white, fontSize: 10);
    final step = _timeScaleStepSeconds.toDouble();
    final start = (_choreo.startTime / step).floor() * step;
    final end = _choreo.endTime;
    final ticks = <Widget>[];
    for (double t = start; t <= end; t += step) {
      final x = _offsetFromTime(t);
      if (x < -10 || x > totalWidth + 10) continue;
      ticks.add(
        Positioned(
          left: x - 12,
          top: 0,
          width: 24,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(width: 1, height: tickHeight, color: Colors.white),
              const SizedBox(height: 2),
              Text('${t.toInt()}', style: labelStyle, textAlign: TextAlign.center),
            ],
          ),
        ),
      );
    }
    return SizedBox(
      width: totalWidth,
      height: scaleHeight,
      child: Stack(
        clipBehavior: Clip.none,
        children: ticks,
      ),
    );
  }

  Widget _buildStartEndMarkers(double totalWidth) {
    const stripWidth = 4.0;
    const glowBlur = 10.0;
    final startX = 0.0;
    final endX = _offsetFromTime(_choreo.endTime);
    final strip = Container(
      width: stripWidth,
      height: 88,
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.7),
        boxShadow: [
          BoxShadow(
            color: Colors.white.withOpacity(0.9),
            blurRadius: glowBlur,
            spreadRadius: 1,
          ),
        ],
      ),
    );
    return Stack(
      key: ValueKey('markers_${_choreo.startTime}_${_choreo.endTime}'),
      clipBehavior: Clip.none,
      children: [
        Positioned(left: startX, top: 0, child: strip),
        if (endX > stripWidth)
          Positioned(
            key: ValueKey('end_$endX'),
            left: endX - stripWidth,
            top: 0,
            child: strip,
          ),
      ],
    );
  }

  Widget _buildTimelineSegments(double totalWidth) {
    final l10n = ref.watch(appStringsProvider);
    final points = _sortedPoints;
    if (points.isEmpty) {
      return Positioned(
        left: 0,
        top: 0,
        right: 0,
        bottom: 0,
        child: Center(
          child: Text(
            l10n.addElementHint,
            style: TextStyle(color: AppColors.textSecondary.withOpacity(0.8), fontSize: 13),
          ),
        ),
      );
    }
    return Stack(
      clipBehavior: Clip.none,
      children: [
        for (int i = 0; i < points.length; i++) ...[
          Positioned(
            left: _offsetFromTime(points[i].key) - 8,
            top: (88 - 24) / 2,
            child: GestureDetector(
              onTap: () => _editPoint(points[i].key, points[i].value, pointIndex1Based: i + 1),
              behavior: HitTestBehavior.opaque,
              child: Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.accent,
                  border: Border.all(color: Colors.white, width: 2),
                  boxShadow: [BoxShadow(color: Colors.black26, blurRadius: 4)],
                ),
              ),
            ),
          ),
          _TimelineSegment(
            left: _offsetFromTime(points[i].key),
            width: (i + 1 < points.length ? _offsetFromTime(points[i + 1].key) : _offsetFromTime(_choreo.endTime)) - _offsetFromTime(points[i].key),
            time: points[i].key,
            moveName: points[i].value,
            onTap: () => _editPoint(points[i].key, points[i].value, pointIndex1Based: i + 1),
          ),
        ],
      ],
    );
  }

  Widget _pointsListSection() {
    final l10n = ref.watch(appStringsProvider);
    final points = _sortedPoints;
    if (points.isEmpty) {
      return Expanded(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          decoration: BoxDecoration(color: AppColors.card.withOpacity(0.3), border: Border(top: BorderSide(color: AppColors.cardBorder))),
          child: Center(
            child: Text(
              l10n.addElementHintLong,
              style: const TextStyle(color: AppColors.textSecondary, fontSize: 12),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ),
      );
    }
    return Expanded(
      child: Container(
        padding: const EdgeInsets.only(left: 16, right: 16, top: 6, bottom: 16),
        decoration: BoxDecoration(color: AppColors.card.withOpacity(0.3), border: Border(top: BorderSide(color: AppColors.cardBorder))),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('${l10n.pointsCount} (${points.length})', style: const TextStyle(color: AppColors.textSecondary, fontSize: 12), overflow: TextOverflow.ellipsis),
            const SizedBox(height: 8),
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  const cardWidth = 140.0;
                  const cardHeight = 64.0;
                  const gap = 8.0;
                  final crossCount = math.max(1, ((constraints.maxWidth + gap) / (cardWidth + gap)).floor());
                  return GridView.builder(
                    gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: crossCount,
                      childAspectRatio: cardWidth / cardHeight,
                      crossAxisSpacing: gap,
                      mainAxisSpacing: gap,
                    ),
                    itemCount: points.length,
                    itemBuilder: (_, i) {
                      final e = points[i];
                      return Material(
                        color: AppColors.card,
                        borderRadius: AppRadius.radiusSm,
                        child: InkWell(
                          onTap: () => _editPoint(e.key, e.value, pointIndex1Based: i + 1),
                          borderRadius: AppRadius.radiusSm,
                          child: Container(
                            padding: const EdgeInsets.all(10),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(formatDuration(e.key), style: const TextStyle(color: AppColors.accent, fontWeight: FontWeight.w600, fontSize: 13), overflow: TextOverflow.ellipsis),
                                const SizedBox(height: 4),
                                Text(_timelineLabel(e.value), style: const TextStyle(color: Colors.white, fontSize: 12), maxLines: 2, overflow: TextOverflow.ellipsis),
                              ],
                            ),
                          ),
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TrimField extends StatefulWidget {
  const _TrimField({
    required this.label,
    required this.value,
    required this.onChanged,
    this.min = 0,
    this.max = 600,
  });

  final String label;
  final double value;
  final double min;
  final double max;
  final void Function(double) onChanged;

  @override
  State<_TrimField> createState() => _TrimFieldState();
}

class _TrimFieldState extends State<_TrimField> {
  late TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.value.toStringAsFixed(1));
  }

  @override
  void didUpdateWidget(_TrimField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value && _controller.text != widget.value.toStringAsFixed(1)) {
      _controller.text = widget.value.toStringAsFixed(1);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final v = double.tryParse(_controller.text.replaceAll(',', '.'));
    if (v != null) widget.onChanged(v.clamp(widget.min, widget.max));
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      decoration: InputDecoration(
        labelText: widget.label,
        isDense: true,
        border: const OutlineInputBorder(),
      ),
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      controller: _controller,
      onSubmitted: (_) => _submit(),
      style: const TextStyle(color: Colors.white),
    );
  }
}

class _FadeInContent extends StatefulWidget {
  const _FadeInContent({required this.child});

  final Widget child;

  @override
  State<_FadeInContent> createState() => _FadeInContentState();
}

class _FadeInContentState extends State<_FadeInContent> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(duration: const Duration(milliseconds: 180), vsync: this);
    _animation = CurvedAnimation(parent: _controller, curve: Curves.easeOut);
    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _animation,
      child: widget.child,
    );
  }
}

class _PointDialog extends ConsumerStatefulWidget {
  const _PointDialog({
    required this.initialTimes,
    required this.initialTimelineValue,
    required this.allStyles,
    required this.choreographyStyleId,
    required this.startTime,
    required this.endTime,
    required this.removeTime,
    this.timeLabelStart = 1,
    required this.onSave,
    required this.onDelete,
  });

  final List<double> initialTimes;
  final String initialTimelineValue;
  final List<DanceStyle> allStyles;
  final String choreographyStyleId;
  final double startTime;
  final double endTime;
  final double? removeTime;
  final int timeLabelStart;
  final void Function(List<double> times, String timelineValue) onSave;
  final VoidCallback? onDelete;

  @override
  ConsumerState<_PointDialog> createState() => _PointDialogState();
}

class _PointDialogState extends ConsumerState<_PointDialog> {
  late List<TextEditingController> _timeControllers;
  late String? _styleId;
  late String? _moveId;

  @override
  void initState() {
    super.initState();
    _timeControllers = widget.initialTimes.map((t) => TextEditingController(text: t.toStringAsFixed(1))).toList();
    _initStyleAndMove();
  }

  void _initStyleAndMove() {
    final v = widget.initialTimelineValue;
    final m = ChoreographyTimelineRef.resolveMove(widget.allStyles, widget.choreographyStyleId, v);
    if (m != null) {
      final dec = ChoreographyTimelineRef.decode(v);
      if (dec != null) {
        _styleId = dec.styleId;
        _moveId = dec.moveId;
      } else {
        for (final s in widget.allStyles) {
          if (s.moves.any((x) => x.id == m.id)) {
            _styleId = s.id;
            _moveId = m.id;
            break;
          }
        }
      }
    }
    _styleId ??= _firstStyleWithMoves()?.id;
    final st = _styleForId(_styleId);
    if (st != null && st.moves.isNotEmpty) {
      _moveId ??= st.moves.first.id;
      if (!st.moves.any((x) => x.id == _moveId)) {
        _moveId = st.moves.first.id;
      }
    } else {
      _moveId = null;
    }
    if (_styleId != null && !widget.allStyles.any((s) => s.id == _styleId)) {
      _styleId = _firstStyleWithMoves()?.id ?? (widget.allStyles.isNotEmpty ? widget.allStyles.first.id : null);
    }
    final stN = _styleForId(_styleId);
    if (stN == null || stN.moves.isEmpty) {
      _moveId = null;
    } else if (_moveId == null || !stN.moves.any((m) => m.id == _moveId)) {
      _moveId = stN.moves.first.id;
    }
  }

  DanceStyle? _firstStyleWithMoves() {
    for (final s in widget.allStyles) {
      if (s.moves.isNotEmpty) return s;
    }
    for (final s in widget.allStyles) {
      return s;
    }
    return null;
  }

  DanceStyle? _styleForId(String? id) {
    if (id == null) return null;
    for (final s in widget.allStyles) {
      if (s.id == id) return s;
    }
    return null;
  }

  @override
  void dispose() {
    for (final c in _timeControllers) {
      c.dispose();
    }
    super.dispose();
  }

  void _addTimeField() {
    setState(() {
      final last = _timeControllers.isNotEmpty ? _timeControllers.last.text : widget.startTime.toStringAsFixed(1);
      final v = double.tryParse(last.replaceAll(',', '.'));
      final t = (v != null ? v.clamp(widget.startTime, widget.endTime) : widget.startTime);
      _timeControllers.add(TextEditingController(text: t.toStringAsFixed(1)));
    });
  }

  void _submit() {
    final sid = _styleId;
    final mid = _moveId;
    if (sid == null || mid == null || sid.isEmpty || mid.isEmpty) return;
    final times = <double>[];
    for (final c in _timeControllers) {
      final v = double.tryParse(c.text.replaceAll(',', '.'));
      if (v != null) {
        times.add(v.clamp(widget.startTime, widget.endTime));
      }
    }
    if (times.isEmpty) return;
    widget.onSave(times, ChoreographyTimelineRef.encode(sid, mid));
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = ref.watch(appStringsProvider);
    final styles = widget.allStyles;
    final st = _styleForId(_styleId);
    final moves = st?.moves ?? const <Move>[];

    return AlertDialog(
      backgroundColor: AppColors.card,
      title: Text(l10n.pointOnTimeline),
      content: SingleChildScrollView(
        child: _FadeInContent(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AnimatedSize(
                duration: const Duration(milliseconds: 220),
                curve: Curves.easeOutCubic,
                alignment: Alignment.topCenter,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: List.generate(_timeControllers.length, (i) {
                    return Padding(
                      padding: EdgeInsets.only(bottom: i < _timeControllers.length - 1 ? 8 : 0),
                      child: Row(
                        children: [
                          Expanded(
                            child: TextField(
                              decoration: InputDecoration(labelText: l10n.timeSecLabel(widget.timeLabelStart + i), isDense: true),
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              controller: _timeControllers[i],
                              style: const TextStyle(color: Colors.white),
                            ),
                          ),
                          if (i == _timeControllers.length - 1)
                            IconButton(
                              icon: const Icon(Icons.add_circle_outline, color: AppColors.accent),
                              onPressed: _addTimeField,
                              tooltip: l10n.addTime,
                            ),
                        ],
                      ),
                    );
                  }),
                ),
              ),
              const SizedBox(height: 16),
              if (styles.isEmpty)
                Text(l10n.addStyleFirst, style: const TextStyle(color: Colors.white70))
              else ...[
                DropdownButtonFormField<String>(
                  value: _styleId,
                  decoration: InputDecoration(labelText: l10n.style, isDense: true),
                  dropdownColor: AppColors.card,
                  isExpanded: true,
                  items: styles
                      .map(
                        (s) => DropdownMenuItem(
                          value: s.id,
                          child: Text(l10n.displayDanceStyleName(s.name), overflow: TextOverflow.ellipsis),
                        ),
                      )
                      .toList(),
                  onChanged: (v) {
                    if (v == null) return;
                    final ns = _styleForId(v);
                    setState(() {
                      _styleId = v;
                      _moveId = ns != null && ns.moves.isNotEmpty ? ns.moves.first.id : null;
                    });
                  },
                  style: const TextStyle(color: Colors.white),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String?>(
                  value: moves.isEmpty ? null : _moveId,
                  decoration: InputDecoration(labelText: l10n.movement, isDense: true),
                  dropdownColor: AppColors.card,
                  isExpanded: true,
                  items: moves
                      .map(
                        (m) => DropdownMenuItem<String?>(
                          value: m.id,
                          child: Text(m.name, overflow: TextOverflow.ellipsis),
                        ),
                      )
                      .toList(),
                  onChanged: moves.isEmpty
                      ? null
                      : (v) => setState(() => _moveId = v),
                  style: const TextStyle(color: Colors.white),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        if (widget.onDelete != null)
          TextButton(
            onPressed: () {
              widget.onDelete!();
            },
            child: Text(l10n.delete, style: const TextStyle(color: Colors.red)),
          ),
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        FilledButton(
          onPressed: (styles.isEmpty || moves.isEmpty || _moveId == null) ? null : _submit,
          child: Text(l10n.save),
        ),
      ],
    );
  }
}

class _TimelineSegment extends StatelessWidget {
  const _TimelineSegment({
    required this.left,
    required this.width,
    required this.time,
    required this.moveName,
    required this.onTap,
  });

  final double left;
  final double width;
  final double time;
  final String moveName;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final segmentWidth = math.max(width, 4.0);
    return Positioned(
      left: left,
      top: (88 - 12) / 2,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(6),
          child: Container(
            width: segmentWidth,
            height: 12,
            decoration: BoxDecoration(
              color: AppColors.accent,
              borderRadius: BorderRadius.circular(6),
            ),
          ),
        ),
      ),
    );
  }
}
