/// Согласие с пользовательским соглашением и политикой (флаг в [AppData.settings]).
class LegalTermsConsent {
  LegalTermsConsent._();

  static const String keyAccepted = 'legalTermsAccepted';

  static bool fromSettings(Map<String, dynamic> settings) =>
      settings[keyAccepted] == true;

  static Map<String, dynamic> acceptedSettingsEntry() => {keyAccepted: true};
}
