import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/ui/screens/elements/elements_screen.dart';
import 'package:midnight_dancer/ui/screens/music/music_screen.dart';
import 'package:midnight_dancer/ui/screens/choreography/choreography_screen.dart';
import 'package:midnight_dancer/ui/screens/trainer/trainer_screen.dart';

enum NavTab { elements, music, choreography, trainer }

/// Главный каркас с адаптивной навигацией.
class MainScaffold extends StatefulWidget {
  const MainScaffold({super.key});

  @override
  State<MainScaffold> createState() => _MainScaffoldState();
}

class _MainScaffoldState extends State<MainScaffold> {
  NavTab _current = NavTab.elements;

  static const _tabs = [
    (tab: NavTab.elements, icon: Icons.format_list_bulleted, label: 'Элементы'),
    (tab: NavTab.music, icon: Icons.music_note, label: 'Музыка'),
    (tab: NavTab.choreography, icon: Icons.groups, label: 'Хореография'),
    (tab: NavTab.trainer, icon: Icons.bolt, label: 'Тренировка'),
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
    }
  }

  @override
  Widget build(BuildContext context) {
    final isWide = MediaQuery.of(context).size.width >= 600;

    if (isWide) {
      return Scaffold(
        backgroundColor: AppColors.background,
        body: Row(
          children: [
            NavigationRail(
              backgroundColor: AppColors.card,
              selectedIndex: _tabs.indexWhere((t) => t.tab == _current),
              onDestinationSelected: (i) =>
                  setState(() => _current = _tabs[i].tab),
              labelType: NavigationRailLabelType.all,
              destinations: _tabs
                  .map(
                    (t) => NavigationRailDestination(
                      icon: Icon(
                        t.icon,
                        color: t.tab == _current
                            ? AppColors.accent
                            : AppColors.textSecondary,
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
      body: _buildScreen(),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          color: AppColors.card,
          border: Border(
            top: BorderSide(color: AppColors.cardBorder),
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: _tabs.map((t) {
                final isActive = t.tab == _current;
                return InkWell(
                  onTap: () => setState(() => _current = t.tab),
                  borderRadius: AppRadius.radiusMd,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          t.icon,
                          size: 24,
                          color: isActive
                              ? AppColors.accent
                              : AppColors.textSecondary,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          t.label,
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight:
                                isActive ? FontWeight.bold : FontWeight.normal,
                            color: isActive
                                ? AppColors.accent
                                : AppColors.textSecondary,
                          ),
                        ),
                      ],
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
