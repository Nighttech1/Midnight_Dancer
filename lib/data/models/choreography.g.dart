// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'choreography.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Map<double, String> _parseTimeline(Map<String, dynamic> json) {
  return json.map((k, v) => MapEntry(double.parse(k), v as String));
}

Map<String, dynamic> _timelineToJson(Map<double, String> timeline) {
  return timeline.map((k, v) => MapEntry(k.toString(), v));
}

Choreography _$ChoreographyFromJson(Map<String, dynamic> json) =>
    Choreography(
      id: json['id'] as String,
      name: json['name'] as String,
      songId: json['songId'] as String,
      styleId: json['styleId'] as String,
      timeline: json['timeline'] != null
          ? _parseTimeline(json['timeline'] as Map<String, dynamic>)
          : {},
      startTime: (json['startTime'] as num?)?.toDouble() ?? 0,
      endTime: (json['endTime'] as num).toDouble(),
    );

Map<String, dynamic> _$ChoreographyToJson(Choreography instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'songId': instance.songId,
      'styleId': instance.styleId,
      'timeline': _timelineToJson(instance.timeline),
      'startTime': instance.startTime,
      'endTime': instance.endTime,
    };
