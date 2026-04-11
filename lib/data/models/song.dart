import 'package:json_annotation/json_annotation.dart';

part 'song.g.dart';

@JsonSerializable()
class Song {
  Song({
    required this.id,
    required this.title,
    required this.danceStyle,
    required this.level,
    required this.fileName,
    required this.duration,
    required this.sizeBytes,
    this.playbackSpeed = 1.0,
  });

  factory Song.fromJson(Map<String, dynamic> json) => _$SongFromJson(json);

  final String id;
  final String title;
  final String danceStyle;
  final String level;
  final String fileName;
  final double duration;
  final int sizeBytes;

  /// Скорость воспроизведения (0.2–1.5), сохраняется в данных приложения.
  @JsonKey(defaultValue: 1.0)
  final double playbackSpeed;

  Map<String, dynamic> toJson() => _$SongToJson(this);

  Song copyWith({
    String? id,
    String? title,
    String? danceStyle,
    String? level,
    String? fileName,
    double? duration,
    int? sizeBytes,
    double? playbackSpeed,
  }) =>
      Song(
        id: id ?? this.id,
        title: title ?? this.title,
        danceStyle: danceStyle ?? this.danceStyle,
        level: level ?? this.level,
        fileName: fileName ?? this.fileName,
        duration: duration ?? this.duration,
        sizeBytes: sizeBytes ?? this.sizeBytes,
        playbackSpeed: playbackSpeed ?? this.playbackSpeed,
      );
}
