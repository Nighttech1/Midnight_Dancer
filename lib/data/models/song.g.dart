// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'song.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Song _$SongFromJson(Map<String, dynamic> json) => Song(
      id: json['id'] as String,
      title: json['title'] as String,
      danceStyle: json['danceStyle'] as String,
      level: json['level'] as String,
      fileName: json['fileName'] as String,
      duration: (json['duration'] as num).toDouble(),
      sizeBytes: (json['sizeBytes'] as num).toInt(),
    );

Map<String, dynamic> _$SongToJson(Song instance) => <String, dynamic>{
      'id': instance.id,
      'title': instance.title,
      'danceStyle': instance.danceStyle,
      'level': instance.level,
      'fileName': instance.fileName,
      'duration': instance.duration,
      'sizeBytes': instance.sizeBytes,
    };
