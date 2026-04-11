import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';

/// Web и прочие платформы без dart:io: только bytes из пикера.
Future<Uint8List?> completePickerFileBytes(PlatformFile f) async => f.bytes;
