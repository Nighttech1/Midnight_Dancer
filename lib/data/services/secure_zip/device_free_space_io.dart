import 'dart:io';

import 'package:disk_space_2/disk_space_2.dart';

/// Free space on the volume that holds [path] (or whole device fallback).
///
/// [DiskSpace] reports mebibytes (2^20). Returns **bytes**, or `null` if unknown.
Future<int?> getFreeDiskSpaceBytesForPath(String path) async {
  try {
    final dir = Directory(path);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    final mib = await DiskSpace.getFreeDiskSpaceForPath(path);
    if (mib == null) return null;
    return (mib * (1 << 20)).round();
  } catch (_) {
    final mib = await DiskSpace.getFreeDiskSpace;
    if (mib == null) return null;
    return (mib * (1 << 20)).round();
  }
}
