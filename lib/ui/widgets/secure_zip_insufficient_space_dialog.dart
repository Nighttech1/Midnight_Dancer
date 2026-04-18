import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/format_storage_size.dart';

/// STEP 2 — понятное предупреждение о нехватке места перед распаковкой.
Future<void> showSecureZipInsufficientSpaceDialog({
  required BuildContext context,
  required AppStrings str,
  required int requiredBytes,
  required int freeBytes,
}) {
  final reqLabel = formatStorageSizeHumanReadable(requiredBytes);
  final freeLabel = formatStorageSizeHumanReadable(freeBytes);
  return showDialog<void>(
    context: context,
    useRootNavigator: true,
    builder: (ctx) => AlertDialog(
      backgroundColor: AppColors.card,
      title: Text(
        str.secureImportInsufficientSpaceTitle,
        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
      ),
      content: Text(
        str.secureImportInsufficientSpace(reqLabel, freeLabel),
        style: TextStyle(color: Colors.white.withOpacity(0.92), fontSize: 15, height: 1.4),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: Text(
            str.secureImportInsufficientSpaceOk,
            style: const TextStyle(color: AppColors.accent),
          ),
        ),
      ],
    ),
  );
}
