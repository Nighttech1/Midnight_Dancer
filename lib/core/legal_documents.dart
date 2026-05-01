import 'package:url_launcher/url_launcher.dart';

/// Публичные юридические документы (Google Docs).
abstract final class LegalDocuments {
  static const String ruPubUrl =
      'https://docs.google.com/document/d/e/2PACX-1vRgd45h-3bag7IwbjSrkdf891vp3P2IjrSL5nvYbEdEfwh4hN5etQI7IfFTzNL94DMifEAEuOWHmOnq/pub';
  static const String enPubUrl =
      'https://docs.google.com/document/d/e/2PACX-1vQH1d5PPxPo4xZsgdbsfkR7l5Yj-W0KmhlZKe4spoP2guMbKsIbDwgSA1ujr-drC9KMZs4cM3fmWd3w/pub';

  static Future<void> openRu() => _open(Uri.parse(ruPubUrl));

  static Future<void> openEn() => _open(Uri.parse(enPubUrl));

  static Future<void> _open(Uri uri) async {
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }
}
