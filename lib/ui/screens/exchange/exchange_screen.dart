import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/picker_file_bytes_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/picker_file_bytes_io.dart'
    as picker_file;
import 'package:midnight_dancer/core/utils/full_backup_save_downloads_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/full_backup_save_downloads_io.dart'
    as backup_dl;
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/providers/music_playback_provider.dart';
import 'package:midnight_dancer/ui/widgets/exchange_fullscreen_loading.dart';
import 'package:midnight_dancer/ui/widgets/full_backup_export_options_dialog.dart';
import 'package:midnight_dancer/ui/widgets/full_backup_import_mapping_dialog.dart';
import 'package:midnight_dancer/ui/widgets/secure_zip_insufficient_space_dialog.dart';

/// Раздел «Обмен»: выгрузка и загрузка полного ZIP с данными приложения.
class ExchangeScreen extends ConsumerStatefulWidget {
  const ExchangeScreen({super.key});

  @override
  ConsumerState<ExchangeScreen> createState() => _ExchangeScreenState();
}

class _ExchangeScreenState extends ConsumerState<ExchangeScreen> {
  /// Полноэкранный слой с кругом поверх всего приложения; пока идёт [task], анимация крутится.
  Future<void> _withFullscreenExchangeLoading(
    String message,
    Future<void> Function() task,
  ) async {
    final nav = Navigator.of(context, rootNavigator: true);
    final done = nav.push<void>(
      PageRouteBuilder<void>(
        opaque: true,
        barrierDismissible: false,
        transitionDuration: Duration.zero,
        reverseTransitionDuration: const Duration(milliseconds: 200),
        pageBuilder: (_, __, ___) => ExchangeFullscreenLoading(message: message),
      ),
    );
    await Future<void>.delayed(Duration.zero);
    try {
      await task();
    } finally {
      if (mounted && nav.canPop()) nav.pop();
    }
    await done;
  }

  Future<void> _showExportDoneDialog(String folderPath, String fileName) async {
    final str = ref.read(appStringsProvider);
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      useRootNavigator: true,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(
          str.exchangeExportDoneTitle,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: SingleChildScrollView(
          child: SelectableText(
            str.exchangeExportDoneBody(folderPath, fileName),
            style: TextStyle(
              color: Colors.white.withOpacity(0.92),
              fontSize: 15,
              height: 1.4,
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(str.exchangeExportDoneOk, style: const TextStyle(color: AppColors.accent)),
          ),
        ],
      ),
    );
  }

  Future<void> _exportFullBackup() async {
    final str = ref.read(appStringsProvider);
    final appData = ref.read(appDataNotifierProvider).valueOrNull;
    if (!mounted || appData == null) return;

    final exportData = await showFullBackupExportOptionsDialog(
      context: context,
      str: str,
      appData: appData,
    );
    if (!mounted || exportData == null) return;

    await _withFullscreenExchangeLoading(str.exchangeExporting, () async {
      try {
        final zip = await ref
            .read(appDataNotifierProvider.notifier)
            .buildFullBackupZip(data: exportData);
        if (!mounted) return;
        if (zip == null || zip.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(str.fullBackupExportNothing)),
          );
          return;
        }
        final name =
            'midnight-dancer-full-${DateTime.now().toIso8601String().replaceAll(':', '-')}.zip';
        final saved = await backup_dl.saveFullBackupZipToDownloads(zip, name);
        if (!mounted) return;
        if (saved == null) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(str.fullBackupExportSaveFailed)),
          );
          return;
        }
        await _showExportDoneDialog(saved.folderPath, saved.fileName);
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(str.fullBackupError(e.toString()))),
          );
        }
      }
    });
  }

  Future<void> _importFullBackup() async {
    final str = ref.read(appStringsProvider);
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.card,
        title: Text(
          str.fullBackupImportConfirmTitle,
          style: const TextStyle(color: Colors.white),
        ),
        content: Text(
          str.fullBackupImportConfirmBody,
          style: TextStyle(color: Colors.white.withOpacity(0.9)),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(str.cancel)),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(
              str.fullBackupImportConfirmAction,
              style: const TextStyle(color: AppColors.accent),
            ),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;

    FilePickerResult? result;
    try {
      result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const ['zip'],
        withData: kIsWeb,
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.fullBackupError(e.toString()))),
      );
      return;
    }
    if (result == null || result.files.isEmpty || !mounted) return;
    final picked = result.files.single;
    final zipPath = picked.path;

    FullBackupParseResult? parsed;
    String? parseErr;

    await _withFullscreenExchangeLoading(str.exchangeParsingBackup, () async {
      try {
        if (!kIsWeb && zipPath != null && zipPath.isNotEmpty) {
          final r = await ref.read(appDataNotifierProvider.notifier).tryParseFullBackupFromSecureFilePath(zipPath);
          parsed = r.$1;
          parseErr = r.$2;
        } else {
          final bytes = await picker_file.completePickerFileBytes(picked);
          if (!mounted) return;
          if (bytes == null || bytes.isEmpty) {
            parseErr = 'no_file';
            return;
          }
          final r = ref.read(appDataNotifierProvider.notifier).tryParseFullBackupBytes(bytes);
          parsed = r.$1;
          parseErr = r.$2;
        }
      } catch (e) {
        parseErr = e.toString();
      }
    });

    if (!mounted) return;

    if (parseErr != null) {
      final pe = parseErr!;
      if (pe == 'no_file') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.importChoreographyNoFile)),
        );
        return;
      }
      if (pe.startsWith('insufficient_space:')) {
        final parts = pe.split(':');
        if (parts.length >= 3) {
          final req = int.tryParse(parts[1]);
          final free = int.tryParse(parts[2]);
          if (req != null && free != null) {
            await showSecureZipInsufficientSpaceDialog(
              context: context,
              str: str,
              requiredBytes: req,
              freeBytes: free,
            );
            return;
          }
        }
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.fullBackupError(pe))),
      );
      return;
    }

    final okParsed = parsed;
    if (okParsed == null || !okParsed.isOk || okParsed.appData == null) return;

    final localData = ref.read(appDataNotifierProvider).valueOrNull;
    final plan = await showFullBackupImportMappingDialog(
      context: context,
      str: str,
      localData: localData ?? AppData(),
      importedStyles: okParsed.appData!.danceStyles,
    );
    if (!mounted || plan == null) return;

    String? mergeErr;
    await _withFullscreenExchangeLoading(str.exchangeImporting, () async {
      try {
        mergeErr = await ref.read(appDataNotifierProvider.notifier).mergeParsedFullBackup(okParsed, plan);
      } catch (e) {
        mergeErr = e.toString();
      }
    });

    if (!mounted) return;
    if (mergeErr != null) {
      final me = mergeErr!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.fullBackupError(me))),
      );
      return;
    }
    await ref.read(musicPlaybackProvider.notifier).stopPlayback();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.fullBackupImportSuccess)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    return SafeArea(
      child: CustomScrollView(
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
            sliver: SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    str.exchangeTitle,
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    str.exchangeIntro,
                    style: TextStyle(
                      color: Colors.white.withOpacity(0.75),
                      fontSize: 14,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            sliver: SliverToBoxAdapter(
              child: _ExchangeActionCard(
                icon: Icons.cloud_upload_outlined,
                title: str.exchangeShareCardTitle,
                subtitle: str.exchangeShareCardSubtitle,
                accent: AppColors.accent,
                onTap: _exportFullBackup,
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
            sliver: SliverToBoxAdapter(
              child: _ExchangeActionCard(
                icon: Icons.cloud_download_outlined,
                title: str.exchangeImportCardTitle,
                subtitle: str.exchangeImportCardSubtitle,
                accent: AppColors.accent.withOpacity(0.85),
                onTap: _importFullBackup,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ExchangeActionCard extends StatelessWidget {
  const _ExchangeActionCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.accent,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final Color accent;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.card,
      borderRadius: AppRadius.radiusMd,
      child: InkWell(
        onTap: onTap,
        borderRadius: AppRadius.radiusMd,
        child: Container(
          decoration: BoxDecoration(
            borderRadius: AppRadius.radiusMd,
            border: Border.all(color: AppColors.cardBorder),
          ),
          padding: const EdgeInsets.all(18),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: accent.withOpacity(0.15),
                  borderRadius: AppRadius.radiusSm,
                ),
                child: Icon(icon, color: accent, size: 28),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 17,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      subtitle,
                      style: TextStyle(
                        color: Colors.white.withOpacity(0.72),
                        fontSize: 14,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
              Icon(Icons.chevron_right, color: Colors.white.withOpacity(0.35)),
            ],
          ),
        ),
      ),
    );
  }
}
