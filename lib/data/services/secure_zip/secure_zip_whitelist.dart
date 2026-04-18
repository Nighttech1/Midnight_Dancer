/// Rules for which ZIP member paths may be counted (preflight) and extracted.
///
/// Default matches the product requirement: `.mp4`, `.mov`, `.jpg`, and `metadata.json`.
class SecureZipWhitelist {
  const SecureZipWhitelist({
    required this.allowedExtensionsLowercase,
    required this.exactFileNamesLowercase,
  });

  /// Midnight Dancer full backup also needs `manifest.json` and audio extensions.
  static const SecureZipWhitelist defaultUserSpec = SecureZipWhitelist(
    allowedExtensionsLowercase: {'mp4', 'mov', 'jpg'},
    exactFileNamesLowercase: {'metadata.json'},
  );

  /// Whitelist for [FullBackupService] archives produced by this app.
  static const SecureZipWhitelist midnightDancerFullBackup = SecureZipWhitelist(
    allowedExtensionsLowercase: {'mp4', 'mov', 'jpg', 'mp3', 'm4a', 'wav'},
    exactFileNamesLowercase: {'metadata.json', 'manifest.json'},
  );

  final Set<String> allowedExtensionsLowercase;
  final Set<String> exactFileNamesLowercase;

  /// Normalized archive member path uses forward slashes, no leading `/`.
  bool matchesEntryPath(String entryPath) {
    final norm = entryPath.replaceAll('\\', '/').trim();
    if (norm.isEmpty || norm.endsWith('/')) {
      return false;
    }
    final name = (norm.split('/')..removeWhere((s) => s.isEmpty)).last.toLowerCase();
    if (exactFileNamesLowercase.contains(name)) {
      return true;
    }
    final dot = name.lastIndexOf('.');
    if (dot < 0 || dot >= name.length - 1) {
      return false;
    }
    final ext = name.substring(dot + 1).toLowerCase();
    return allowedExtensionsLowercase.contains(ext);
  }
}
