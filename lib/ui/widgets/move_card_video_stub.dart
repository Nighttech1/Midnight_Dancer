import 'package:flutter/material.dart';

class MoveCardVideo extends StatelessWidget {
  const MoveCardVideo({
    super.key,
    required this.videoPathOrUri,
    required this.onEdit,
    this.onError,
  });

  final String videoPathOrUri;
  final VoidCallback onEdit;
  final VoidCallback? onError;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onEdit,
      child: Container(
        color: Colors.black26,
        child: const Center(
          child: Icon(Icons.videocam, color: Colors.white38, size: 48),
        ),
      ),
    );
  }
}
