import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/data/services/dance_reminder_config.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';
import 'package:midnight_dancer/ui/screens/elements/elements_screen.dart';
import 'package:midnight_dancer/ui/screens/music/music_screen.dart';
import 'package:midnight_dancer/ui/screens/choreography/choreography_screen.dart';
import 'package:midnight_dancer/ui/screens/trainer/trainer_screen.dart';
import 'package:midnight_dancer/ui/screens/exchange/exchange_screen.dart';
import 'package:midnight_dancer/ui/screens/settings/settings_screen.dart';

enum NavTab { elements, music, choreography, trainer, exchange }

/// Главный каркас с адаптивной навигацией (включая раздел «Обмен»).
class MainScaffold extends ConsumerStatefulWidget {
  const MainScaffold({super.key});

  @override
  ConsumerState<MainScaffold> createState() => _MainScaffoldState();
}

class _MainScaffoldState extends ConsumerState<MainScaffold> {
  NavTab _current = NavTab.elements;

  List<({NavTab tab, IconData icon, String label})> _tabs(AppStrings strings) => [
        (tab: NavTab.elements, icon: Icons.format_list_bulleted, label: strings.navElements),
        (tab: NavTab.music, icon: Icons.music_note, label: strings.navMusic),
        (tab: NavTab.choreography, icon: Icons.groups, label: strings.navChoreography),
        (tab: NavTab.trainer, icon: Icons.bolt, label: strings.navTrainer),
        (tab: NavTab.exchange, icon: Icons.swap_horiz_rounded, label: strings.navExchange),
      ];

  Widget _buildScreen() {
    switch (_current) {
      case NavTab.elements:
        return const ElementsScreen();
      case NavTab.music:
        return const MusicScreen();
      case NavTab.choreography:
        return const ChoreographyScreen();
      case NavTab.trainer:
        return const TrainerScreen();
      case NavTab.exchange:
        return const ExchangeScreen();
    }
  }

  PreferredSizeWidget _settingsAppBar(AppStrings strings) {
    return AppBar(
      backgroundColor: AppColors.background,
      foregroundColor: Colors.white,
      elevation: 0,
      actions: [
        IconButton(
          icon: const Icon(Icons.settings_outlined),
          tooltip: strings.settingsTitle,
          onPressed: () {
            final data = ref.read(appDataNotifierProvider).valueOrNull;
            final cfg = DanceReminderConfig.fromSettings(data?.settings ?? {});
            Navigator.of(context).push<void>(
              MaterialPageRoute<void>(
                builder: (ctx) => SettingsScreen(initialReminder: cfg),
              ),
            );
          },
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    final tabs = _tabs(str);
    final isWide = MediaQuery.of(context).size.width >= 600;

    if (isWide) {
      return Scaffold(
        backgroundColor: AppColors.background,
        appBar: _settingsAppBar(str),
        body: Row(
          children: [
            NavigationRail(
              backgroundColor: AppColors.card,
              selectedIndex: tabs.indexWhere((t) => t.tab == _current),
              onDestinationSelected: (i) => setState(() => _current = tabs[i].tab),
              labelType: NavigationRailLabelType.all,
              destinations: tabs
                  .map(
                    (t) => NavigationRailDestination(
                      icon: Icon(
                        t.icon,
                        color: t.tab == _current ? AppColors.accent : AppColors.textSecondary,
                      ),
                      selectedIcon: Icon(
                        t.icon,
                        color: AppColors.accent,
                      ),
                      label: Text(t.label),
                    ),
                  )
                  .toList(),
            ),
            Expanded(child: _buildScreen()),
          ],
        ),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: _settingsAppBar(str),
      body: _buildScreen(),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          color: AppColors.card,
          border: Border(
            top: BorderSide(color: AppColors.cardBorder),
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: tabs.map((t) {
                final isActive = t.tab == _current;
                return Expanded(
                  child: InkWell(
                    onTap: () => setState(() => _current = t.tab),
                    borderRadius: AppRadius.radiusMd,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            t.icon,
                            size: 24,
                            color: isActive ? AppColors.accent : AppColors.textSecondary,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            t.label,
                            textAlign: TextAlign.center,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
                              color: isActive ? AppColors.accent : AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
        ),
      ),
    );
  }
}
