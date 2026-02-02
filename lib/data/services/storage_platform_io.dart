import 'dart:io' show Platform;

/// На mobile (Android/iOS) используем filesystem.
bool get useFilesystem => Platform.isAndroid || Platform.isIOS;
