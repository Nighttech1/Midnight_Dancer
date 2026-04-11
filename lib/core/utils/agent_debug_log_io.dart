import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

// #region agent log
const _kLogPath = r'd:\Applications\Midnight Dancer app\Midnight_Dancer\.cursor\debug.log';
const _kIngest = 'http://127.0.0.1:7252/ingest/1f16f297-0a7a-48ca-9a61-510ab0be5fb9';

Future<void> _agentPostIngest(Map<String, dynamic> payload) async {
  try {
    final client = HttpClient();
    client.connectionTimeout = const Duration(milliseconds: 800);
    final req = await client.postUrl(Uri.parse(_kIngest));
    req.headers.contentType = ContentType.json;
    req.write(jsonEncode(payload));
    await req.close();
    client.close(force: true);
  } catch (_) {}
}

void agentDebugLog({
  required String hypothesisId,
  required String location,
  required String message,
  Map<String, dynamic>? data,
  String runId = 'pre-fix',
}) {
  final payload = <String, dynamic>{
    'timestamp': DateTime.now().millisecondsSinceEpoch,
    'hypothesisId': hypothesisId,
    'location': location,
    'message': message,
    'data': data ?? {},
    'runId': runId,
  };
  final line = '${jsonEncode(payload)}\n';
  try {
    File(_kLogPath).writeAsStringSync(line, mode: FileMode.append, flush: true);
  } catch (_) {}
  unawaited(_agentPostIngest(payload));
  debugPrint('AGENT_NDJSON:${jsonEncode(payload)}');
}
// #endregion
