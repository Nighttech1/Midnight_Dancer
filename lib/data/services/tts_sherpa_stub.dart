/// Заглушка Sherpa-ONNX (web или при недоступности пакета).
Future<TtsSherpaEngine?> createSherpaTts(String modelDir, {String? dataDir}) async {
  return null;
}

abstract class TtsSherpaEngine {
  Future<void> speak(String text, {required double speed, required double pitch, double volume = 1.0});
  Future<void> stop();
}
