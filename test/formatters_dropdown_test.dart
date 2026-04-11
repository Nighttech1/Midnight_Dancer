import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/core/utils/formatters.dart';

void main() {
  group('dropdownValueOrFallback', () {
    test('returns current when valid', () {
      const valid = {'All', 'Beginner'};
      expect(dropdownValueOrFallback('Beginner', valid, 'All'), 'Beginner');
    });

    test('returns fallback when current not in set', () {
      const valid = {'All', 'Beginner', 'Intermediate', 'Advanced'};
      expect(dropdownValueOrFallback('Broken', valid, 'All'), 'All');
    });
  });
}
