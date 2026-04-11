// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'move.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Move _$MoveFromJson(Map<String, dynamic> json) => Move(
      id: json['id'] as String,
      name: json['name'] as String,
      level: json['level'] as String,
      description: json['description'] as String?,
      videoUri: json['videoUri'] as String?,
      masteryPercent: (json['masteryPercent'] as num?)?.toInt() ?? 0,
    );

Map<String, dynamic> _$MoveToJson(Move instance) => <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'level': instance.level,
      'description': instance.description,
      'videoUri': instance.videoUri,
      'masteryPercent': instance.masteryPercent,
    };
