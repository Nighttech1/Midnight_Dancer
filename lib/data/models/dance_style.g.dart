// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'dance_style.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

DanceStyle _$DanceStyleFromJson(Map<String, dynamic> json) => DanceStyle(
      id: json['id'] as String,
      name: json['name'] as String,
      moves: (json['moves'] as List<dynamic>?)
              ?.map((e) => Move.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      currentMoveId: json['currentMoveId'] as String?,
    );

Map<String, dynamic> _$DanceStyleToJson(DanceStyle instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'moves': instance.moves.map((e) => e.toJson()).toList(),
      'currentMoveId': instance.currentMoveId,
    };
