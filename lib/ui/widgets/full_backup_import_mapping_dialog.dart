import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/services/full_backup_import_style_plan.dart';

/// Результат диалога: план импорта или отмена (`null`).
Future<FullBackupStyleImportPlan?> showFullBackupImportMappingDialog({
  required BuildContext context,
  required AppStrings str,
  required AppData localData,
  required List<DanceStyle> importedStyles,
}) {
  return showDialog<FullBackupStyleImportPlan>(
    context: context,
    barrierDismissible: false,
    builder: (ctx) => _FullBackupImportMappingDialog(
      str: str,
      localData: localData,
      importedStyles: importedStyles,
    ),
  );
}

class _FullBackupImportMappingDialog extends StatefulWidget {
  const _FullBackupImportMappingDialog({
    required this.str,
    required this.localData,
    required this.importedStyles,
  });

  final AppStrings str;
  final AppData localData;
  final List<DanceStyle> importedStyles;

  @override
  State<_FullBackupImportMappingDialog> createState() => _FullBackupImportMappingDialogState();
}

class _FullBackupImportMappingDialogState extends State<_FullBackupImportMappingDialog> {
  late Map<String, String?> _mergeInto;
  late bool _separateOnly;

  @override
  void initState() {
    super.initState();
    _separateOnly = false;
    final localIds = widget.localData.danceStyles.map((s) => s.id).toSet();
    _mergeInto = {
      for (final s in widget.importedStyles)
        s.id: localIds.contains(s.id) ? s.id : null,
    };
  }

  @override
  Widget build(BuildContext context) {
    final str = widget.str;
    return AlertDialog(
      backgroundColor: AppColors.card,
      title: Text(
        str.fullBackupImportMappingTitle,
        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
      ),
      content: SizedBox(
        width: double.maxFinite,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              CheckboxListTile(
                value: _separateOnly,
                onChanged: (v) => setState(() => _separateOnly = v ?? false),
                activeColor: AppColors.accent,
                title: Text(
                  str.fullBackupImportSeparateOnlyTitle,
                  style: const TextStyle(color: Colors.white, fontSize: 15),
                ),
                subtitle: Text(
                  str.fullBackupImportSeparateOnlySubtitle,
                  style: TextStyle(color: Colors.white.withOpacity(0.65), fontSize: 12),
                ),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
              ),
              const SizedBox(height: 8),
              if (!_separateOnly && widget.importedStyles.isNotEmpty) ...[
                Text(
                  str.fullBackupImportMappingStylesHeader,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w600,
                    fontSize: 15,
                  ),
                ),
                const SizedBox(height: 8),
                ...widget.importedStyles.map((imp) {
                  final choices = <DropdownMenuItem<String?>>[
                    DropdownMenuItem<String?>(
                      value: null,
                      child: Text(
                        str.fullBackupImportMergeById,
                        style: const TextStyle(color: Colors.white),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    ...widget.localData.danceStyles.map(
                      (loc) => DropdownMenuItem<String?>(
                        value: loc.id,
                        child: Text(
                          str.displayDanceStyleName(loc.name),
                          style: const TextStyle(color: Colors.white),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                  ];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          str.displayDanceStyleName(imp.name),
                          style: TextStyle(
                            color: Colors.white.withOpacity(0.9),
                            fontWeight: FontWeight.w500,
                            fontSize: 13,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 4),
                        DropdownButtonFormField<String?>(
                          value: _mergeInto[imp.id],
                          decoration: InputDecoration(
                            labelText: str.fullBackupImportTargetLabel,
                            isDense: true,
                          ),
                          dropdownColor: AppColors.card,
                          isExpanded: true,
                          items: choices,
                          onChanged: (v) => setState(() => _mergeInto[imp.id] = v),
                        ),
                      ],
                    ),
                  );
                }),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(str.cancel, style: const TextStyle(color: Colors.white70)),
        ),
        TextButton(
          onPressed: () {
            final plan = FullBackupStyleImportPlan(
              importAsSeparateOnly: _separateOnly,
              mergeArchiveStyleIntoLocalId: _separateOnly ? {} : Map<String, String?>.from(_mergeInto),
            );
            Navigator.pop(context, plan);
          },
          child: Text(str.fullBackupImportApply, style: const TextStyle(color: AppColors.accent)),
        ),
      ],
    );
  }
}
