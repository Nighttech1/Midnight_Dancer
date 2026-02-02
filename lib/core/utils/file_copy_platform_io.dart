import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/services.dart';

const _channel = MethodChannel('com.midnightdancer.app/file_copy');

Future<String?> copyPickedFileToCache(String uriOrPath) async {
  try {
    final result = await _channel.invokeMethod<String>('copyToCache', {'uri': uriOrPath});
    return result;
  } catch (_) {
    return null;
  }
}

Future<bool> takeUriPermission(String uri) async {
  try {
    await _channel.invokeMethod('takeUriPermission', {'uri': uri});
    return true;
  } catch (_) {
    return false;
  }
}

Future<Uint8List?> getVideoThumbnail(String uriOrPath) async {
  try {
    final base64 = await _channel.invokeMethod<String>('getVideoThumbnail', {'uri': uriOrPath});
    if (base64 == null || base64.isEmpty) return null;
    return Uint8List.fromList(base64Decode(base64));
  } catch (_) {
    return null;
  }
}
