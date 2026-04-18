/// Human-readable size for storage messages (МБ / ГБ etc.).
String formatStorageSizeHumanReadable(int bytes) {
  var b = bytes;
  if (b < 0) {
    b = 0;
  }
  const gb = 1024 * 1024 * 1024;
  const mb = 1024 * 1024;
  if (b >= gb) {
    return '${(b / gb).toStringAsFixed(2)} ГБ';
  }
  if (b >= mb) {
    return '${(b / mb).toStringAsFixed(1)} МБ';
  }
  if (b >= 1024) {
    return '${(b / 1024).toStringAsFixed(1)} КБ';
  }
  return '$b Б';
}
