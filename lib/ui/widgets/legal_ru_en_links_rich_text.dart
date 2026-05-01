import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/legal_documents.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';

/// Кликабельные «RU» и «EN» для открытия юридических документов.
/// [leading] — текст перед «RU»; если [showClosingParen], после «EN» добавляется «)».
class LegalRuEnLinksRichText extends StatefulWidget {
  const LegalRuEnLinksRichText({
    super.key,
    this.leading = '',
    this.baseStyle,
    this.textAlign = TextAlign.start,
    this.showClosingParen = true,
  });

  final String leading;
  final TextStyle? baseStyle;
  final TextAlign textAlign;
  final bool showClosingParen;

  @override
  State<LegalRuEnLinksRichText> createState() => _LegalRuEnLinksRichTextState();
}

class _LegalRuEnLinksRichTextState extends State<LegalRuEnLinksRichText> {
  late final TapGestureRecognizer _ru;
  late final TapGestureRecognizer _en;

  @override
  void initState() {
    super.initState();
    _ru = TapGestureRecognizer()..onTap = () => LegalDocuments.openRu();
    _en = TapGestureRecognizer()..onTap = () => LegalDocuments.openEn();
  }

  @override
  void dispose() {
    _ru.dispose();
    _en.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final base = widget.baseStyle ??
        TextStyle(
          color: Colors.white.withValues(alpha: 0.88),
          fontSize: 14,
          height: 1.4,
        );
    final linkStyle = base.copyWith(
      color: AppColors.accent,
      decoration: TextDecoration.underline,
      decorationColor: AppColors.accent,
    );
    final children = <InlineSpan>[
      TextSpan(text: widget.leading),
      TextSpan(text: 'RU', style: linkStyle, recognizer: _ru),
      TextSpan(text: ' | ', style: base),
      TextSpan(text: 'EN', style: linkStyle, recognizer: _en),
    ];
    if (widget.showClosingParen) {
      children.add(const TextSpan(text: ')'));
    }
    return RichText(
      textAlign: widget.textAlign,
      text: TextSpan(style: base, children: children),
    );
  }
}
