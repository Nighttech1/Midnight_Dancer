import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/services/legal_terms_consent.dart';

void main() {
  test('LegalTermsConsent fromSettings false by default', () {
    expect(LegalTermsConsent.fromSettings({}), isFalse);
    expect(LegalTermsConsent.fromSettings({'legalTermsAccepted': false}), isFalse);
  });

  test('LegalTermsConsent fromSettings true when accepted', () {
    expect(
      LegalTermsConsent.fromSettings({
        ...LegalTermsConsent.acceptedSettingsEntry(),
      }),
      isTrue,
    );
  });
}
