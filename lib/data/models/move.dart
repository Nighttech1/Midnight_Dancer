import 'package:json_annotation/json_annotation.dart';

part 'move.g.dart';

@JsonSerializable()
class Move {
  Move({
    required this.id,
    required this.name,
    required this.level,
    this.description,
    this.videoUri,
    this.masteryPercent = 0,
  });

  factory Move.fromJson(Map<String, dynamic> json) {
    final m = _$MoveFromJson(json);
    final uri = m.videoUri ??
        json['videoFileName'] as String? ??
        json['videoRef'] as String?;
    if (uri != null && uri.isNotEmpty && uri != m.videoUri) {
      return m.copyWith(videoUri: uri);
    }
    return m;
  }

  final String id;
  final String name;
  final String level;
  final String? description;
  /// content:// URI или путь к файлу. Без копирования в папку приложения.
  final String? videoUri;

  /// Процент освоения элемента, 0–100.
  @JsonKey(defaultValue: 0)
  final int masteryPercent;

  Map<String, dynamic> toJson() => _$MoveToJson(this);

  Move copyWith({
    String? id,
    String? name,
    String? level,
    String? description,
    String? videoUri,
    int? masteryPercent,
  }) =>
      Move(
        id: id ?? this.id,
        name: name ?? this.name,
        level: level ?? this.level,
        description: description ?? this.description,
        videoUri: videoUri ?? this.videoUri,
        masteryPercent: masteryPercent ?? this.masteryPercent,
      );
}
