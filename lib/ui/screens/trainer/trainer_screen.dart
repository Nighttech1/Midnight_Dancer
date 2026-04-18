import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/audio_source_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/audio_source_platform_io.dart'
    as audio_platform;
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/tts_service.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/ui/screens/trainer/trainer_session_screen.dart';

enum TrainerMode { random, choreography }

/// Экран «Тренировка»: режимы Случайный / Хореография, настройки, обратный отсчёт, голос по таймлайну.
class TrainerScreen extends ConsumerStatefulWidget {
  const TrainerScreen({super.key});

  @override
  ConsumerState<TrainerScreen> createState() => _TrainerScreenState();
}

class _TrainerScreenState extends ConsumerState<TrainerScreen> {
  TrainerMode _mode = TrainerMode.random;
  String? _styleId;
  String? _songId;
  Choreography? _choreography;
  TtsVoice? _selectedVoice;
  bool _loading = false;
  bool _initialVoiceScheduled = false;
  double _speed = 0.5;
  double _intervalSec = 5.0;
  String _level = 'All';
  bool _ducking = true;
  /// Диапазон трека для случайного режима: 0 = от начала / до конца.
  int _trackStartSec = 0;
  int _trackEndSec = 0;
  final TextEditingController _trackStartController = TextEditingController(text: '0');
  final TextEditingController _trackEndController = TextEditingController(text: '0');

  @override
  void dispose() {
    _trackStartController.dispose();
    _trackEndController.dispose();
    super.dispose();
  }

  Future<void> _loadVoice(TtsService tts, TtsVoice voice) async {
    if (!mounted) return;
    setState(() => _loading = true);
    try {
      await tts.initVoice(voice, onProgress: (_) {});
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _openSession() {
    if (_selectedVoice == null) return;
    _trackStartSec = int.tryParse(_trackStartController.text) ?? 0;
    _trackEndSec = int.tryParse(_trackEndController.text) ?? 0;
    final params = TrainerSessionParams(
      mode: _mode,
      styleId: _styleId,
      songId: _songId,
      choreography: _choreography,
      voice: _selectedVoice!,
      speed: _speed,
      ducking: _ducking,
      intervalSec: _intervalSec,
      level: _level,
      trackStartSec: _trackStartSec,
      trackEndSec: _trackEndSec,
    );
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => TrainerSessionScreen(params: params),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    final tts = ref.watch(ttsServiceProvider);
    final voices = tts.availableVoices;
    final data = ref.watch(appDataNotifierProvider).valueOrNull ?? AppData();

    if (_selectedVoice == null && voices.isNotEmpty && !_initialVoiceScheduled) {
      _initialVoiceScheduled = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        final current = tts.currentVoice;
        if (current != null && voices.any((v) => v.id == current.id)) {
          setState(() => _selectedVoice = current);
        } else {
          setState(() => _selectedVoice = voices.first);
          _loadVoice(tts, voices.first);
        }
      });
    }

    final styles = data.danceStyles;
    final songs = data.songs;
    final choreographies = data.choreographies;

    if (styles.isNotEmpty && _styleId == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _styleId != null) return;
        setState(() => _styleId = styles.first.id);
      });
    }
    if (songs.isNotEmpty && _songId == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _songId != null) return;
        setState(() => _songId = songs.first.id);
        _updateTrackEndFromSong(songs.first.id);
      });
    }
    if (_mode == TrainerMode.choreography && choreographies.isNotEmpty && _choreography == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _choreography != null) return;
        setState(() => _choreography = choreographies.first);
      });
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                str.trainerTitle,
                style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: Colors.white),
              ),
              const SizedBox(height: 20),
              Center(
                child: SegmentedButton<TrainerMode>(
                segments: [
                  ButtonSegment(value: TrainerMode.random, label: Text(str.freestyle), icon: const Icon(Icons.shuffle)),
                  ButtonSegment(value: TrainerMode.choreography, label: Text(str.choreography), icon: const Icon(Icons.music_note)),
                ],
                selected: {_mode},
                onSelectionChanged: (s) => setState(() => _mode = s.first),
                style: ButtonStyle(
                  backgroundColor: WidgetStateProperty.resolveWith((states) {
                    if (states.contains(WidgetState.selected)) return AppColors.accent;
                    return AppColors.card;
                  }),
                  foregroundColor: const WidgetStatePropertyAll(Colors.white),
                ),
                ),
              ),
              const SizedBox(height: 20),

              if (_mode == TrainerMode.random) ...[
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.style),
                          DropdownButtonFormField<String>(
                            value: styles.isEmpty ? null : (_styleId ?? styles.first.id),
                            decoration: _dropdownDecoration(),
                            dropdownColor: AppColors.card,
                            isExpanded: true,
                            items: styles.isEmpty
                                ? [DropdownMenuItem(value: null, child: Text(str.noStyles))]
                                : styles
                                    .map((s) => DropdownMenuItem(
                                          value: s.id,
                                          child: Text(str.displayDanceStyleName(s.name)),
                                        ))
                                    .toList(),
                            onChanged: styles.isEmpty ? null : (v) => setState(() => _styleId = v),
                            style: const TextStyle(color: Colors.white),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.music),
                          DropdownButtonFormField<String>(
                            value: songs.isEmpty ? null : (_songId ?? songs.first.id),
                            decoration: _dropdownDecoration(),
                            dropdownColor: AppColors.card,
                            isExpanded: true,
                            items: songs.isEmpty
                                ? [DropdownMenuItem(value: null, child: Text(str.noTracks))]
                                : songs.map((s) => DropdownMenuItem(
                                      value: s.id,
                                      child: Text(s.title, overflow: TextOverflow.ellipsis, maxLines: 1),
                                    )).toList(),
                            onChanged: songs.isEmpty
                                ? null
                                : (v) {
                                    setState(() => _songId = v);
                                    if (v != null) _updateTrackEndFromSong(v);
                                  },
                            style: const TextStyle(color: Colors.white),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.levelElements),
                          DropdownButtonFormField<String>(
                            value: _level,
                            decoration: _dropdownDecoration(),
                            dropdownColor: AppColors.card,
                            isExpanded: true,
                            items: str.levelOptions.map((e) => DropdownMenuItem(value: e.$1, child: Text(e.$2))).toList(),
                            onChanged: (v) => setState(() => _level = v ?? 'All'),
                            style: const TextStyle(color: Colors.white),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.intervalElements),
                          SizedBox(
                            height: 56,
                            child: Stack(
                              clipBehavior: Clip.none,
                              children: [
                                Positioned(
                                  left: 0,
                                  right: 0,
                                  bottom: 0,
                                  child: SliderTheme(
                                    data: SliderTheme.of(context).copyWith(
                                      overlayShape: const RoundSliderOverlayShape(),
                                      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10),
                                      trackHeight: 4,
                                    ),
                                    child: Slider(
                                      value: _intervalSec.clamp(2.0, 30.0),
                                      min: 2,
                                      max: 30,
                                      divisions: 56,
                                      activeColor: AppColors.accent,
                                      onChanged: (v) => setState(() => _intervalSec = v),
                                    ),
                                  ),
                                ),
                                Positioned(
                                  left: 0,
                                  right: 0,
                                  top: 0,
                                  child: Align(
                                    alignment: Alignment(
                                      (_intervalSec.clamp(2.0, 30.0) - 2) / 28 * 2 - 1,
                                      -1,
                                    ),
                                    child: Text(
                                      '${_intervalSec.toStringAsFixed(1)} с',
                                      style: const TextStyle(
                                        color: AppColors.accent,
                                        fontWeight: FontWeight.w600,
                                        fontSize: 15,
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.trackRangeSec),
                          Row(
                            children: [
                              Expanded(
                                child: TextField(
                                  keyboardType: TextInputType.number,
                                  decoration: _dropdownDecoration().copyWith(
                                    labelText: str.trackStart,
                                    hintText: str.trimStart,
                                    hintStyle: const TextStyle(color: AppColors.textSecondary),
                                  ),
                                  style: const TextStyle(color: Colors.white),
                                  controller: _trackStartController,
                                  onChanged: (v) => setState(() => _trackStartSec = int.tryParse(v) ?? 0),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: TextField(
                                  keyboardType: TextInputType.number,
                                  decoration: _dropdownDecoration().copyWith(
                                    labelText: str.trackEndLabel,
                                    hintText: '',
                                    hintStyle: const TextStyle(color: AppColors.textSecondary),
                                  ),
                                  style: const TextStyle(color: Colors.white),
                                  controller: _trackEndController,
                                  onChanged: (v) => setState(() => _trackEndSec = int.tryParse(v) ?? 0),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.voice),
                          Row(
                            children: [
                              Expanded(
                                child: DropdownButtonFormField<TtsVoice>(
                                  value: _selectedVoice,
                                  decoration: _dropdownDecoration(),
                                  dropdownColor: AppColors.card,
                                  isExpanded: true,
                                  items: voices.map((v) => DropdownMenuItem(value: v, child: Text(str.voiceDisplayName(v.id)))).toList(),
                                  onChanged: _loading
                                      ? null
                                      : (v) {
                                          if (v == null) return;
                                          setState(() => _selectedVoice = v);
                                          _loadVoice(tts, v);
                                        },
                                  style: const TextStyle(color: Colors.white),
                                ),
                              ),
                              ValueListenableBuilder<double?>(
                                valueListenable: tts.loadProgress,
                                builder: (_, progress, __) {
                                  if (progress == null) return const SizedBox(width: 28, height: 28);
                                  return SizedBox(
                                    width: 28,
                                    height: 28,
                                    child: CircularProgressIndicator(
                                      value: progress,
                                      strokeWidth: 2,
                                      backgroundColor: AppColors.card,
                                      valueColor: const AlwaysStoppedAnimation<Color>(AppColors.accent),
                                    ),
                                  );
                                },
                              ),
                            ],
                          ),
                          Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: TextButton.icon(
                              onPressed: _loading ? null : () => tts.speak(str.testVoicePhrase, speed: _speed, volume: 1.0),
                              icon: const Icon(Icons.record_voice_over, size: 18),
                              label: Text(str.testVoice),
                              style: TextButton.styleFrom(
                                foregroundColor: AppColors.accent,
                                splashFactory: NoSplash.splashFactory,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.speechSpeed),
                          Slider(
                            value: _speed,
                            min: 0.0,
                            max: 1.0,
                            activeColor: AppColors.accent,
                            onChanged: (v) => setState(() => _speed = v),
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          _sectionLabel(str.duckMusic),
                          Switch(
                            value: _ducking,
                            activeColor: AppColors.accent,
                            onChanged: (v) => setState(() => _ducking = v),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],

              if (_mode == TrainerMode.choreography) ...[
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.choreography),
                          DropdownButtonFormField<String>(
                            value: choreographies.isEmpty ? null : (_choreography?.id ?? choreographies.first.id),
                            decoration: _dropdownDecoration(),
                            dropdownColor: AppColors.card,
                            isExpanded: true,
                            items: choreographies.isEmpty
                                ? [DropdownMenuItem(value: null, child: Text(str.noChoreographies))]
                                : choreographies
                                    .map((c) => DropdownMenuItem(
                                          value: c.id,
                                          child: Text(c.name, overflow: TextOverflow.ellipsis, maxLines: 1),
                                        ))
                                    .toList(),
                            onChanged: choreographies.isEmpty
                                ? null
                                : (v) {
                                    setState(() => _choreography = v == null ? null : choreographies.firstWhere((c) => c.id == v));
                                  },
                            style: const TextStyle(color: Colors.white),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.voice),
                          Row(
                            children: [
                              Expanded(
                                child: DropdownButtonFormField<TtsVoice>(
                                  value: _selectedVoice,
                                  decoration: _dropdownDecoration(),
                                  dropdownColor: AppColors.card,
                                  isExpanded: true,
                                  items: voices.map((v) => DropdownMenuItem(value: v, child: Text(str.voiceDisplayName(v.id)))).toList(),
                                  onChanged: _loading
                                      ? null
                                      : (v) {
                                          if (v == null) return;
                                          setState(() => _selectedVoice = v);
                                          _loadVoice(tts, v);
                                        },
                                  style: const TextStyle(color: Colors.white),
                                ),
                              ),
                              ValueListenableBuilder<double?>(
                                valueListenable: tts.loadProgress,
                                builder: (_, progress, __) {
                                  if (progress == null) return const SizedBox(width: 28, height: 28);
                                  return SizedBox(
                                    width: 28,
                                    height: 28,
                                    child: CircularProgressIndicator(
                                      value: progress,
                                      strokeWidth: 2,
                                      backgroundColor: AppColors.card,
                                      valueColor: const AlwaysStoppedAnimation<Color>(AppColors.accent),
                                    ),
                                  );
                                },
                              ),
                            ],
                          ),
                          if (_selectedVoice != null)
                            Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: TextButton.icon(
                                onPressed: _loading ? null : () => tts.speak(str.testVoicePhrase, speed: _speed, volume: 1.0),
                                icon: const Icon(Icons.record_voice_over, size: 18),
                                label: Text(str.testVoice),
                                style: TextButton.styleFrom(
                                  foregroundColor: AppColors.accent,
                                  splashFactory: NoSplash.splashFactory,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
                Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _sectionLabel(str.speechSpeed),
                          Slider(
                            value: _speed,
                            min: 0.0,
                            max: 1.0,
                            activeColor: AppColors.accent,
                            onChanged: (v) => setState(() => _speed = v),
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          _sectionLabel(str.duckMusic),
                          Switch(
                            value: _ducking,
                            activeColor: AppColors.accent,
                            onChanged: (v) => setState(() => _ducking = v),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: _canStart() ? _openSession : null,
                  icon: const Icon(Icons.play_arrow, size: 24),
                  label: Text(str.startDance),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.accent,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                ),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  bool _canStart() {
    if (_selectedVoice == null || _loading) return false;
    if (_mode == TrainerMode.random) {
      if (_styleId == null) return false;
      final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
      final style = data.danceStyles.cast<DanceStyle?>().firstWhere(
        (s) => s?.id == _styleId,
        orElse: () => null,
      );
      if (style == null || style.moves.isEmpty) return false;
      return true;
    }
    return _choreography != null;
  }

  Widget _sectionLabel(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Text(text, style: const TextStyle(color: AppColors.textSecondary, fontSize: 14)),
    );
  }

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

  Future<void> _updateTrackEndFromSong(String? songId) async {
    if (songId == null || !mounted) return;
    final data = ref.read(appDataNotifierProvider).valueOrNull ?? AppData();
    final song = data.songs.cast<Song?>().firstWhere(
          (s) => s?.id == songId,
          orElse: () => null,
        );
    if (song == null) return;
    final path = await ref.read(appDataNotifierProvider.notifier).getSongFilePath(song);
    if (path == null || path.isEmpty || !mounted) return;
    final player = AudioPlayer();
    try {
      await player.setAudioSource(audio_platform.createFileAudioSource(path));
      final duration = player.duration ?? Duration.zero;
      if (mounted && duration.inSeconds > 0) {
        _trackEndSec = duration.inSeconds;
        _trackEndController.text = '${duration.inSeconds}';
      }
    } catch (_) {}
    await player.dispose();
  }
}
