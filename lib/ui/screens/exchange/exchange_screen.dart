import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/core/utils/picker_file_bytes_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/picker_file_bytes_io.dart'
    as picker_file;
import 'package:midnight_dancer/core/utils/full_backup_save_downloads_stub.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/full_backup_save_downloads_io.dart'
    as backup_dl;
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/providers/music_playback_provider.dart';
import 'package:midnight_dancer/ui/widgets/exchange_fullscreen_loading.dart';

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
    await _withFullscreenExchangeLoading(str.exchangeExporting, () async {
      try {
        final zip = await ref.read(appDataNotifierProvider.notifier).buildFullBackupZip();
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

    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['zip'],
      withData: kIsWeb,
    );
    if (result == null || result.files.isEmpty || !mounted) return;
    final bytes = await picker_file.completePickerFileBytes(result.files.single);
    if (!mounted) return;
    if (bytes == null || bytes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(str.importChoreographyNoFile)),
      );
      return;
    }

    await _withFullscreenExchangeLoading(str.exchangeImporting, () async {
      try {
        final err = await ref.read(appDataNotifierProvider.notifier).importFullBackup(bytes);
        if (!mounted) return;
        if (err != null) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(str.fullBackupError(err))),
          );
          return;
        }
        await ref.read(musicPlaybackProvider.notifier).stopPlayback();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(str.fullBackupImportSuccess)),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(str.fullBackupError(e.toString()))),
          );
        }
      }
    });
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
