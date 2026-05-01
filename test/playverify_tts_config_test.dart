import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/core/app_flavor.dart';
import 'package:midnight_dancer/data/services/tts_service.dart';

void main() {
  final flavor = const String.fromEnvironment('FLAVOR', defaultValue: '');
  final isPlayverifyRun = flavor == 'playverify';

  test(
    'playverify: один голос Руслан в TTS и не три как у full',
    () {
      TestWidgetsFlutterBinding.ensureInitialized();
      expect(AppFlavor.isPlayverify, isTrue);
      expect(AppFlavor.hasFullVoiceSet, isFalse);
      final voices = TtsService.instance.availableVoices;
      expect(voices, hasLength(1));
      expect(voices.single.id, 'ruslan');
    },
    skip: isPlayverifyRun
        ? false
        : 'Запустите: flutter test test/playverify_tts_config_test.dart --dart-define=FLAVOR=playverify',
  );
}
