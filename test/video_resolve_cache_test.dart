import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/core/utils/video_resolve_cache.dart';

void main() {
  test('VideoResolveCache returns same path on second resolve', () async {
    VideoResolveCache.instance.clear();
    var loadCount = 0;

    Future<String?> loader() async {
      loadCount++;
      return '/tmp/test_video.mp4';
    }

    final first = await VideoResolveCache.instance.resolve('move-1', loader);
    final second = await VideoResolveCache.instance.resolve('move-1', loader);

    expect(first, '/tmp/test_video.mp4');
    expect(second, '/tmp/test_video.mp4');
    expect(loadCount, 1);
  });

  test('VideoResolveCache deduplicates concurrent resolves', () async {
    VideoResolveCache.instance.clear();
    var loadCount = 0;

    Future<String?> slowLoader() async {
      loadCount++;
      await Future<void>.delayed(const Duration(milliseconds: 30));
      return '/tmp/concurrent.mp4';
    }

    final results = await Future.wait([
      VideoResolveCache.instance.resolve('move-2', slowLoader),
      VideoResolveCache.instance.resolve('move-2', slowLoader),
      VideoResolveCache.instance.resolve('move-2', slowLoader),
    ]);

    expect(results.every((p) => p == '/tmp/concurrent.mp4'), isTrue);
    expect(loadCount, 1);
  });

  test('VideoResolveCache hasCached reflects stored paths', () async {
    VideoResolveCache.instance.clear();
    expect(VideoResolveCache.instance.hasCached('x'), isFalse);
    await VideoResolveCache.instance.resolve('x', () async => '/a.mp4');
    expect(VideoResolveCache.instance.hasCached('x'), isTrue);
  });
}
