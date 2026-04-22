import 'package:flutter/material.dart';

/// Якорь для share sheet на iPad: параметр `sharePositionOrigin` у `share_plus` / `Share.shareXFiles`.
Rect sharePositionOriginForContext(BuildContext context) {
  final box = context.findRenderObject() as RenderBox?;
  if (box != null && box.attached && box.hasSize) {
    final o = box.localToGlobal(Offset.zero);
    return Rect.fromLTWH(o.dx, o.dy, box.size.width, box.size.height);
  }
  final sz = MediaQuery.sizeOf(context);
  const edge = 44.0;
  return Rect.fromCenter(
    center: Offset(sz.width / 2, sz.height / 2),
    width: edge,
    height: edge,
  );
}
