import 'package:json_annotation/json_annotation.dart';

part 'choreography.g.dart';

@JsonSerializable()
class Choreography {
  Choreography({
    required this.id,
    required this.name,
    required this.songId,
    required this.styleId,
    this.timeline = const {},
    this.startTime = 0,
    required this.endTime,
  });

  factory Choreography.fromJson(Map<String, dynamic> json) =>
      _$ChoreographyFromJson(json);

  final String id;
  final String name;
  final String songId;
  final String styleId;
  final Map<double, String> timeline;
  final double startTime;
  final double endTime;

  Map<String, dynamic> toJson() => _$ChoreographyToJson(this);

  Choreography copyWith({
    String? id,
    String? name,
    String? songId,
    String? styleId,
    Map<double, String>? timeline,
    double? startTime,
    double? endTime,
  }) =>
      Choreography(
        id: id ?? this.id,
        name: name ?? this.name,
        songId: songId ?? this.songId,
        styleId: styleId ?? this.styleId,
        timeline: timeline ?? this.timeline,
        startTime: startTime ?? this.startTime,
        endTime: endTime ?? this.endTime,
      );
}
