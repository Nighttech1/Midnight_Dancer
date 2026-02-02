// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AppData _$AppDataFromJson(Map<String, dynamic> json) => AppData(
      danceStyles: (json['danceStyles'] as List<dynamic>?)
              ?.map((e) => DanceStyle.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      songs: (json['songs'] as List<dynamic>?)
              ?.map((e) => Song.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      choreographies: (json['choreographies'] as List<dynamic>?)
              ?.map((e) => Choreography.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      settings: (json['settings'] as Map<String, dynamic>?) ?? {},
    );

Map<String, dynamic> _$AppDataToJson(AppData instance) => <String, dynamic>{
      'danceStyles': instance.danceStyles.map((e) => e.toJson()).toList(),
      'songs': instance.songs.map((e) => e.toJson()).toList(),
      'choreographies':
          instance.choreographies.map((e) => e.toJson()).toList(),
      'settings': instance.settings,
    };
