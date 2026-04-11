import 'package:json_annotation/json_annotation.dart';
import 'move.dart';

part 'dance_style.g.dart';

@JsonSerializable()
class DanceStyle {
  DanceStyle({
    required this.id,
    required this.name,
    this.moves = const [],
    this.currentMoveId,
  });

  factory DanceStyle.fromJson(Map<String, dynamic> json) =>
      _$DanceStyleFromJson(json);

  final String id;
  final String name;
  final List<Move> moves;

  /// Элемент, закреплённый первым в списке (вне фильтра/сортировки).
  final String? currentMoveId;

  static const Object _unsetCurrentMove = Object();

  Map<String, dynamic> toJson() => _$DanceStyleToJson(this);

  DanceStyle copyWith({
    String? id,
    String? name,
    List<Move>? moves,
    Object? currentMoveId = _unsetCurrentMove,
  }) =>
      DanceStyle(
        id: id ?? this.id,
        name: name ?? this.name,
        moves: moves ?? this.moves,
        currentMoveId: identical(currentMoveId, _unsetCurrentMove)
            ? this.currentMoveId
            : currentMoveId as String?,
      );
}
