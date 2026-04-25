import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('iOS Info.plist contains required privacy usage descriptions', () {
    final plist = File('ios/Runner/Info.plist');
    expect(plist.existsSync(), isTrue, reason: 'Info.plist must exist for iOS builds');

    final content = plist.readAsStringSync();
    const requiredKeys = <String>[
      'NSPhotoLibraryUsageDescription',
      'NSAppleMusicUsageDescription',
      'NSMicrophoneUsageDescription',
    ];

    for (final key in requiredKeys) {
      expect(
        content.contains('<key>$key</key>'),
        isTrue,
        reason: 'Missing required iOS privacy key: $key',
      );
    }
  });
}
