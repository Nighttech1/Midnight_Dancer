import 'package:json_annotation/json_annotation.dart';
import 'dance_style.dart';
import 'song.dart';
import 'choreography.dart';

part 'app_data.g.dart';

@JsonSerializable()
class AppData {
  AppData({
    this.danceStyles = const [],
    this.songs = const [],
    this.choreographies = const [],
    this.settings = const {},
  });

  factory AppData.fromJson(Map<String, dynamic> json) =>
      _$AppDataFromJson(json);

  final List<DanceStyle> danceStyles;
  final List<Song> songs;
  final List<Choreography> choreographies;
  final Map<String, dynamic> settings;

  Map<String, dynamic> toJson() => _$AppDataToJson(this);

  AppData copyWith({
    List<DanceStyle>? danceStyles,
    List<Song>? songs,
    List<Choreography>? choreographies,
    Map<String, dynamic>? settings,
  }) =>
      AppData(
        danceStyles: danceStyles ?? this.danceStyles,
        songs: songs ?? this.songs,
        choreographies: choreographies ?? this.choreographies,
        settings: settings ?? this.settings,
      );
}
