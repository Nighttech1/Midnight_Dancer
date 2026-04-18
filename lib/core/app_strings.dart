import 'package:midnight_dancer/core/app_ui_language.dart';

/// Всегда так называем автосозданный стиль в данных (независимо от языка интерфейса).
const String kCanonicalDefaultDanceStyleName = 'General';

/// Строки интерфейса для выбранного [AppUiLanguage].
class AppStrings {
  AppStrings(this.lang);

  final AppUiLanguage lang;

  String _t({required String ru, required String en, required String es}) =>
      switch (lang) {
        AppUiLanguage.ru => ru,
        AppUiLanguage.en => en,
        AppUiLanguage.es => es,
      };

  /// Подпись в UI для встроенного стиля по умолчанию (в БД — [kCanonicalDefaultDanceStyleName] или старое «Общий»).
  String displayDanceStyleName(String stored) {
    if (stored == kCanonicalDefaultDanceStyleName || stored == 'Общий') {
      return defaultStyleDisplayName;
    }
    return stored;
  }

  // Навигация
  String get navElements => _t(ru: 'Элементы', en: 'Elements', es: 'Elementos');
  String get navMusic => _t(ru: 'Музыка', en: 'Music', es: 'Música');
  String get navChoreography => _t(ru: 'Хореография', en: 'Choreography', es: 'Coreografía');
  String get navTrainer => _t(ru: 'Тренировка', en: 'Training', es: 'Entrenamiento');
  String get navExchange => _t(ru: 'Обмен', en: 'Exchange', es: 'Intercambio');

  // Настройки
  String get settingsTitle => _t(ru: 'Настройки', en: 'Settings', es: 'Ajustes');
  String get settingsNotificationsSection =>
      _t(ru: 'Уведомления', en: 'Notifications', es: 'Notificaciones');
  String get danceReminderTitle =>
      _t(ru: 'Напоминания о тренировке', en: 'Practice reminders', es: 'Recordatorios de práctica');
  String get danceReminderSubtitle => _t(
        ru: 'Уведомление о том, что пора открыть приложение и потанцевать.',
        en: 'Get a notification when it is time to open the app and dance.',
        es: 'Recibe un aviso cuando toque abrir la app y bailar.',
      );
  String get danceReminderEnabled =>
      _t(ru: 'Включить напоминания', en: 'Enable reminders', es: 'Activar recordatorios');
  String get danceReminderTime => _t(ru: 'Время', en: 'Time', es: 'Hora');
  String get danceReminderFrequency => _t(ru: 'Как часто', en: 'How often', es: 'Con qué frecuencia');
  String get danceReminderWeekday => _t(ru: 'День недели', en: 'Day of week', es: 'Día de la semana');
  String get danceReminderModeDaily =>
      _t(ru: 'Каждый день', en: 'Every day', es: 'Cada día');
  String get danceReminderModeWeekdays =>
      _t(ru: 'По будням (пн–пт)', en: 'Weekdays (Mon–Fri)', es: 'Entre semana (lun–vie)');
  String get danceReminderModeWeekly =>
      _t(ru: 'Раз в неделю', en: 'Once a week', es: 'Una vez por semana');
  String get danceReminderPermissionDenied => _t(
        ru: 'Разрешение на уведомления не выдано. Его можно включить в настройках телефона.',
        en: 'Notification permission was not granted. You can allow it in system settings.',
        es: 'No se concedió el permiso de notificaciones. Puedes activarlo en los ajustes del sistema.',
      );
  String get danceReminderNotifTitle =>
      _t(ru: 'Пора танцевать!', en: 'Time to dance!', es: '¡Hora de bailar!');
  String get danceReminderNotifBody => _t(
        ru: 'Откройте Midnight Dancer и потренируйтесь.',
        en: 'Open Midnight Dancer and practice.',
        es: 'Abre Midnight Dancer y practica.',
      );

  String weekdayLong(int weekday) {
    assert(weekday >= 1 && weekday <= 7);
    const ru = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'];
    const en = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    const es = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
    return switch (lang) {
      AppUiLanguage.ru => ru[weekday - 1],
      AppUiLanguage.en => en[weekday - 1],
      AppUiLanguage.es => es[weekday - 1],
    };
  }

  /// Раздел «Обмен»: полный архив приложения
  String get exchangeTitle => _t(ru: 'Обмен данными', en: 'Data sharing', es: 'Compartir datos');
  String get exchangeIntro => _t(
        ru:
            'Выгрузите ZIP со стилями, элементами, музыкой, хореографиями и медиа или добавьте данные из файла. Уже имеющиеся данные не удаляются — архив подмешивается к текущим.',
        en:
            'Export a ZIP with your styles, elements, music, choreographies and media, or add data from a file. Existing data is kept; the archive is merged in.',
        es:
            'Exporta un ZIP con tus estilos, elementos, música, coreografías y medios, o añade datos desde un archivo. Los datos existentes se conservan; el archivo se fusiona.',
      );

  String get exchangeShareCardTitle =>
      _t(ru: 'Сохранить архив в Загрузки', en: 'Save archive to Downloads', es: 'Guardar archivo en Descargas');
  String get exchangeShareCardSubtitle => _t(
        ru:
            'Собрать ZIP с выбранными данными и сохранить в папку «Загрузки». Перед выгрузкой можно отметить элементы, музыку и хореографии.',
        en:
            'Create a ZIP with the data you choose and save it to Downloads. Before exporting you can pick elements, music and choreographies.',
        es:
            'Crea un ZIP con los datos que elijas y guárdalo en Descargas. Antes puedes marcar elementos, música y coreografías.',
      );

  String get exchangeImportCardTitle =>
      _t(ru: 'Загрузить архив', en: 'Load archive', es: 'Cargar archivo');
  String get exchangeImportCardSubtitle => _t(
        ru:
            'Выберите ZIP резервной копии Midnight Dancer. Новые стили, треки и хореографии добавятся к уже имеющимся.',
        en:
            'Pick a Midnight Dancer backup ZIP. New styles, tracks and choreographies will be added; existing ones stay.',
        es:
            'Elige un ZIP de copia de Midnight Dancer. Se añadirán estilos, pistas y coreografías nuevas; las actuales se mantienen.',
      );

  String get exchangeExporting =>
      _t(ru: 'Данные выгружаются…', en: 'Data is being exported…', es: 'Exportando datos…');
  String get exchangeImporting =>
      _t(ru: 'Данные загружаются и устанавливаются…', en: 'Data is being loaded and applied…', es: 'Cargando e importando datos…');
  String get exchangeParsingBackup => _t(
        ru: 'Архив проверяется…',
        en: 'Verifying archive…',
        es: 'Comprobando archivo…',
      );

  String get exchangeExportDoneTitle =>
      _t(ru: 'Выгрузка завершена', en: 'Export complete', es: 'Exportación completada');

  String exchangeExportDoneBody(String folderPath, String fileName) => _t(
        ru: 'Архив сохранён.\n\nПапка:\n$folderPath\n\nФайл:\n$fileName',
        en: 'The archive was saved.\n\nFolder:\n$folderPath\n\nFile:\n$fileName',
        es: 'El archivo se guardó.\n\nCarpeta:\n$folderPath\n\nArchivo:\n$fileName',
      );

  String get exchangeExportDoneOk => _t(ru: 'Понятно', en: 'OK', es: 'Entendido');

  String get fullBackupExportOptionsTitle => _t(
        ru: 'Что положить в архив',
        en: 'What to include in the archive',
        es: 'Qué incluir en el archivo',
      );
  String get fullBackupExportOptionsIntro => _t(
        ru:
            'Отметь элементы, музыку и хореографию, которыми хочешь поделиться. Для выбранных хореографий в архив автоматически попадут их элементы и треки.',
        en:
            'Select elements, music and choreographies you want to share. For selected choreographies, their elements and tracks are included in the archive automatically.',
        es:
            'Marca elementos, música y coreografías que quieras compartir. Para las coreografías elegidas, sus elementos y pistas entrarán en el archivo automáticamente.',
      );
  String get fullBackupExportOptionsStyleFilter => _t(
        ru: 'Стиль',
        en: 'Style',
        es: 'Estilo',
      );
  String get fullBackupExportOptionsAllStyles => _t(
        ru: 'Все стили',
        en: 'All styles',
        es: 'Todos los estilos',
      );
  String get fullBackupExportOptionsSelectAllInStyle => _t(
        ru: 'Выбрать все элементы стиля',
        en: 'Select all elements of this style',
        es: 'Seleccionar todos los elementos de este estilo',
      );
  String get fullBackupExportOptionsElementsSection => _t(
        ru: 'Элементы (видео)',
        en: 'Elements (video)',
        es: 'Elementos (vídeo)',
      );
  String get fullBackupExportOptionsMusicSection => _t(
        ru: 'Музыка',
        en: 'Music',
        es: 'Música',
      );
  String get fullBackupExportOptionsSelectAllFiltered => _t(
        ru: 'Выбрать всё в списке',
        en: 'Select all in list',
        es: 'Seleccionar todo en la lista',
      );
  String get fullBackupExportOptionsChoreoSection => _t(
        ru: 'Хореографии',
        en: 'Choreographies',
        es: 'Coreografías',
      );
  String get fullBackupExportOptionsExport => _t(
        ru: 'Собрать архив',
        en: 'Build archive',
        es: 'Crear archivo',
      );
  String get fullBackupExportNothingSelected => _t(
        ru: 'Ничего не выбрано для выгрузки.',
        en: 'Nothing was selected to export.',
        es: 'No hay nada seleccionado para exportar.',
      );
  String get fullBackupExportEverythingShortcut => _t(
        ru: 'Выгрузить все данные',
        en: 'Export all data',
        es: 'Exportar todos los datos',
      );

  String get fullBackupExportSaveFailed => _t(
        ru: 'Не удалось сохранить архив в папку «Загрузки» на этом устройстве.',
        en: 'Could not save the archive to Downloads on this device.',
        es: 'No se pudo guardar el archivo en Descargas en este dispositivo.',
      );

  /// Полный бэкап (экспорт / импорт ZIP)
  String get fullBackupExportTooltip =>
      _t(ru: 'Экспорт всех данных', en: 'Export all data', es: 'Exportar todos los datos');
  String get fullBackupImportTooltip =>
      _t(ru: 'Импорт из резервной копии', en: 'Import backup', es: 'Importar copia de seguridad');
  String get fullBackupExportPreparing =>
      _t(ru: 'Готовим архив…', en: 'Preparing backup…', es: 'Preparando copia…');
  String get fullBackupExportNothing => _t(
        ru: 'Не удалось собрать архив (на web не поддерживается).',
        en: 'Nothing to export (or not supported on this platform).',
        es: 'No hay nada que exportar (o no está disponible en esta plataforma).',
      );
  String get fullBackupImportConfirmTitle =>
      _t(ru: 'Добавить данные из архива?', en: 'Add data from archive?', es: '¿Añadir datos del archivo?');
  String get fullBackupImportConfirmBody => _t(
        ru:
            'Данные из файла будут объединены с тем, что уже есть. Текущие стили, треки и хореографии не удаляются; из архива добавятся только новые записи и недостающие медиа.',
        en:
            'The file will be merged with what you already have. Existing styles, tracks and choreographies will not be removed; new items from the file will be added.',
        es:
            'El archivo se fusionará con lo que ya tienes. No se eliminan estilos, pistas ni coreografías; se añadirán elementos nuevos y medios que falten.',
      );
  String get fullBackupImportConfirmAction =>
      _t(ru: 'Загрузить', en: 'Load', es: 'Cargar');
  String get fullBackupImportMappingTitle => _t(
        ru: 'Импорт данных',
        en: 'Data import',
        es: 'Importación de datos',
      );
  String get fullBackupImportSeparateOnlyTitle => _t(
        ru: 'Импортировать стили без слияния',
        en: 'Import styles without merging',
        es: 'Importar estilos sin fusionar',
      );
  String get fullBackupImportSeparateOnlySubtitle => _t(
        ru:
            'Всё содержимое архива будет добавлено отдельно, без слияния с уже существующими стилями. Музыка и хореографии останутся привязаны к новым стилям.',
        en:
            'Everything from the archive is added separately without merging into your existing styles. Music and choreographies stay linked to the new styles.',
        es:
            'Todo el archivo se añade por separado sin fusionar con los estilos que ya tienes. La música y las coreografías quedan enlazadas a los estilos nuevos.',
      );
  String get fullBackupImportMappingStylesHeader =>
      _t(ru: 'Стили из архива', en: 'Styles in the archive', es: 'Estilos en el archivo');
  String get fullBackupImportMergeById => _t(
        ru: 'Создать новый стиль',
        en: 'Create new style',
        es: 'Crear nuevo estilo',
      );
  String get fullBackupImportTargetLabel =>
      _t(ru: 'Импортировать в стиль', en: 'Import into style', es: 'Importar al estilo');
  String get fullBackupImportApply =>
      _t(ru: 'Импортировать', en: 'Import', es: 'Importar');
  String get fullBackupImportSuccess =>
      _t(ru: 'Данные из архива добавлены.', en: 'Archive merged.', es: 'Datos del archivo fusionados.');
  String get elementStyleLabel =>
      _t(ru: 'Стиль элемента', en: 'Element style', es: 'Estilo del elemento');
  String get choreoChangeStyleTooltip => _t(
        ru: 'Сменить стиль',
        en: 'Change style',
        es: 'Cambiar estilo',
      );
  String get choreoChangeStyleNeedTwoStyles => _t(
        ru: 'Нужно как минимум два стиля в приложении. Добавьте стиль в разделе «Элементы».',
        en: 'You need at least two styles. Add another style in Elements.',
        es: 'Se necesitan al menos dos estilos. Añade otro en Elementos.',
      );
  String get choreoChangeStyleNext =>
      _t(ru: 'Далее', en: 'Next', es: 'Siguiente');
  String get choreoChangeStyleCascadeTitle => _t(
        ru: 'Перенести элементы и музыку?',
        en: 'Move steps and music too?',
        es: '¿Mover pasos y música?',
      );
  String get choreoChangeStyleCascadeBody => _t(
        ru:
            'Все элементы, которые отмечены на таймлайне этой хореографии, будут перенесены в выбранный стиль. У связанного трека в карточке музыки поле «стиль» тоже сменится на этот стиль. Другие хореографии в старом стиле могут потерять метки с этими элементами.',
        en:
            'Every step used on this choreography timeline will move to the selected style. The linked track’s style field in Music will match that style too. Other choreographies in the old style may lose markers for those steps.',
        es:
            'Todos los pasos usados en la línea de tiempo pasarán al estilo elegido. El campo de estilo del tema enlazado en Música también coincidirá. Otras coreografías del estilo antiguo pueden perder marcas de esos pasos.',
      );
  String get choreoChangeStyleCascadeContinue =>
      _t(ru: 'Продолжить', en: 'Continue', es: 'Continuar');
  String choreoTimelineOrphan(String ref) => _t(
        ru: 'В таймлайне есть ссылка «$ref», но такого элемента в исходном стиле нет.',
        en: 'The timeline references «$ref», but that step is missing in the source style.',
        es: 'La línea de tiempo usa «$ref», pero ese paso no está en el estilo de origen.',
      );
  String choreoChangeStyleTitle(String choreoName) => _t(
        ru: 'Стиль хореографии «$choreoName»',
        en: 'Choreography style: $choreoName',
        es: 'Estilo de la coreografía: $choreoName',
      );
  String choreoChangeStyleMissingMove(String moveName) => _t(
        ru:
            'В выбранном стиле нет элемента «$moveName» с тем же именем, что в разметке. Сначала добавьте элемент или переименуйте.',
        en:
            'The selected style has no step named «$moveName» used on the timeline. Add it or rename first.',
        es:
            'El estilo elegido no tiene el paso «$moveName» usado en la línea de tiempo. Añádelo o renómbralo.',
      );
  String get choreoChangeStyleDone => _t(
        ru: 'Стиль хореографии и связанные данные обновлены.',
        en: 'Choreography style and linked data were updated.',
        es: 'Estilo de la coreografía y datos enlazados actualizados.',
      );
  String get moveTransferIdConflict => _t(
        ru: 'В другом стиле уже есть карточка с тем же служебным номером. Выберите другой стиль или удалите конфликтующую карточку.',
        en: 'The other style already has a card with the same internal reference. Pick another style or remove the duplicate card.',
        es: 'El otro estilo ya tiene una tarjeta con la misma referencia interna. Elige otro estilo o elimina el duplicado.',
      );
  String fullBackupError(String detail) => _t(
        ru: 'Ошибка резервной копии: $detail',
        en: 'Backup error: $detail',
        es: 'Error de copia de seguridad: $detail',
      );

  String get secureImportInsufficientSpaceTitle =>
      _t(ru: 'Нехватка места', en: 'Not enough space', es: 'Sin espacio suficiente');

  /// Безопасный импорт ZIP: не хватает места (после preflight + 10% запас).
  String secureImportInsufficientSpace(String requiredLabel, String freeLabel) => _t(
        ru:
            'Недостаточно места для импорта. Требуется: $requiredLabel, свободно: $freeLabel. Пожалуйста, освободите память.',
        en:
            'Not enough space to import. Required: $requiredLabel, available: $freeLabel. Please free some storage.',
        es:
            'No hay espacio suficiente para importar. Necesario: $requiredLabel, disponible: $freeLabel. Libera espacio.',
      );

  String get secureImportInsufficientSpaceOk =>
      _t(ru: 'Понятно', en: 'OK', es: 'Entendido');

  // Хореография
  String get choreoTitle => _t(ru: 'Хореография', en: 'Choreography', es: 'Coreografía');
  String get choreoEmpty => _t(
        ru: 'Нет хореографий. Нажмите «Создать» и выберите стиль и музыку.',
        en: 'No choreographies. Tap «Create» and choose style and music.',
        es: 'No hay coreografías. Pulsa «Crear» y elige estilo y música.',
      );
  String get choreoFilterEmpty => _t(
        ru: 'Нет хореографий по выбранным фильтрам. Сбросьте фильтры или измените критерии.',
        en: 'No choreographies match the filters. Clear filters or change criteria.',
        es: 'Ninguna coreografía coincide con los filtros. Limpia los filtros o cambia los criterios.',
      );
  String get choreoChangeLabelOnlyTitle => _t(
        ru: 'Только метка стиля',
        en: 'Style label only',
        es: 'Solo la etiqueta de estilo',
      );
  String get choreoChangeLabelOnlyBody => _t(
        ru:
            'Меняется только поле «стиль» у этой хореографии. Элементы на линии времени и карточка музыки остаются как есть — они могут относиться к другим стилям.',
        en:
            'Only this choreography’s style field changes. Timeline steps and the linked music card stay as they are — they may belong to other styles.',
        es:
            'Solo cambia el campo de estilo de esta coreografía. Los pasos de la línea de tiempo y la pista enlazada se mantienen — pueden pertenecer a otros estilos.',
      );
  String get create => _t(ru: 'Создать', en: 'Create', es: 'Crear');
  String get addStylesAndTracks => _t(
        ru: 'Добавьте стили и треки в разделах Элементы и Музыка',
        en: 'Add styles and tracks in Elements and Music sections',
        es: 'Añade estilos y pistas en Elementos y Música',
      );

  // Редактор последовательности
  String get trimStart => _t(ru: 'Начало', en: 'Start', es: 'Inicio');
  String get trimEnd => _t(ru: 'Конец', en: 'End', es: 'Fin');
  String get addElement => _t(ru: 'Добавить элемент', en: 'Add element', es: 'Añadir elemento');
  String get scale => _t(ru: 'Масштаб', en: 'Scale', es: 'Escala');
  String get addElementHint =>
      _t(ru: 'Нажмите «Добавить элемент»', en: 'Tap «Add element»', es: 'Pulsa «Añadir elemento»');
  String get addElementHintLong => _t(
        ru: 'Нажмите «Добавить элемент» для новой точки',
        en: 'Tap «Add element» for a new point',
        es: 'Pulsa «Añadir elemento» para un punto nuevo',
      );
  String get pointsCount => _t(ru: 'Точки', en: 'Points', es: 'Puntos');
  String get pointOnTimeline =>
      _t(ru: 'Точка на таймлайне', en: 'Point on timeline', es: 'Punto en la línea de tiempo');
  String get timeSec => _t(ru: 'Время', en: 'Time', es: 'Tiempo');
  String timeSecLabel(int n) =>
      _t(ru: 'Время $n (сек)', en: 'Time $n (sec)', es: 'Tiempo $n (s)');
  String get movement => _t(ru: 'Движение', en: 'Movement', es: 'Movimiento');
  String get delete => _t(ru: 'Удалить', en: 'Delete', es: 'Eliminar');
  String get cancel => _t(ru: 'Отмена', en: 'Cancel', es: 'Cancelar');
  String get save => _t(ru: 'Сохранить', en: 'Save', es: 'Guardar');
  String get addTime => _t(ru: 'Добавить время', en: 'Add time', es: 'Añadir tiempo');

  // Тренировка
  String get trainerTitle => _t(ru: 'Тренировка', en: 'Training', es: 'Entrenamiento');
  String get freestyle => _t(ru: 'Фристайл', en: 'Freestyle', es: 'Freestyle');
  String get choreography => _t(ru: 'Хореография', en: 'Choreography', es: 'Coreografía');
  String get allLevels => _t(ru: 'Все', en: 'All', es: 'Todos');
  String get levelBeginner => _t(ru: 'Начинающий', en: 'Beginner', es: 'Principiante');
  String get levelIntermediate => _t(ru: 'Средний', en: 'Intermediate', es: 'Intermedio');
  String get levelAdvanced => _t(ru: 'Профи', en: 'Advanced', es: 'Avanzado');
  String get start => _t(ru: 'Старт', en: 'Start', es: 'Inicio');
  String get startDance => _t(ru: 'Танцуем!', en: "Let's dance!", es: '¡A bailar!');
  String get musicVolume => _t(ru: 'Громкость музыки', en: 'Music volume', es: 'Volumen de la música');
  String get voiceVolume => _t(ru: 'Громкость голоса', en: 'Voice volume', es: 'Volumen de la voz');
  String get finish => _t(ru: 'Закончить', en: 'Finish', es: 'Terminar');
  String get intervalSec => _t(ru: 'Интервал (сек)', en: 'Interval (sec)', es: 'Intervalo (s)');
  String get trackRange => _t(ru: 'Участок трека (сек)', en: 'Track range (sec)', es: 'Tramo del tema (s)');
  String get style => _t(ru: 'Стиль', en: 'Style', es: 'Estilo');
  String get music => _t(ru: 'Музыка', en: 'Music', es: 'Música');
  String get levelElements => _t(ru: 'Уровень элементов', en: 'Element level', es: 'Nivel de elementos');
  String get intervalElements =>
      _t(ru: 'Интервал элементов', en: 'Element interval', es: 'Intervalo de elementos');
  String get voice => _t(ru: 'Голос', en: 'Voice', es: 'Voz');
  String get noStyles => _t(ru: 'Нет стилей', en: 'No styles', es: 'No hay estilos');
  String get noTracks => _t(ru: 'Нет треков', en: 'No tracks', es: 'No hay pistas');
  String get trackStart => _t(ru: 'Начало', en: 'Start', es: 'Inicio');
  String get trackEndLabel => _t(ru: 'Конец', en: 'End', es: 'Fin');
  String get trackEndHint => _t(ru: 'Конец (0=всё)', en: 'End (0=all)', es: 'Fin (0=todo)');
  /// (value, displayLabel) для выбора уровня — значение хранится на английском.
  List<(String, String)> get levelOptions => [
        ('All', _t(ru: 'Все', en: 'All', es: 'Todos')),
        ('Beginner', _t(ru: 'Начинающий', en: 'Beginner', es: 'Principiante')),
        ('Intermediate', _t(ru: 'Средний', en: 'Intermediate', es: 'Intermedio')),
        ('Advanced', _t(ru: 'Профи', en: 'Advanced', es: 'Avanzado')),
      ];

  // Тренировка (доп.)
  String get trackRangeSec =>
      _t(ru: 'Диапазон трека (сек)', en: 'Track range (sec)', es: 'Rango del tema (s)');
  String get secSuffix => _t(ru: ' с', en: ' sec', es: ' s');
  String get testVoice => _t(ru: 'Тест голоса', en: 'Test voice', es: 'Probar voz');
  String get testVoicePhrase =>
      _t(ru: 'Раз, два, три', en: 'One, two, three', es: 'Uno, dos, tres');
  String get speechSpeed => _t(ru: 'Скорость речи', en: 'Speech speed', es: 'Velocidad de la voz');
  String get duckMusic => _t(
        ru: 'Приглушение музыки',
        en: 'Lower music when speaking',
        es: 'Bajar la música al hablar',
      );
  String get noChoreographies =>
      _t(ru: 'Нет хореографий', en: 'No choreographies', es: 'No hay coreografías');
  String voiceDisplayName(String id) {
    switch (id) {
      case 'ruslan':
        return _t(ru: 'Руслан', en: 'Ruslan', es: 'Ruslan');
      case 'irina':
        return _t(ru: 'Ирина', en: 'Irina', es: 'Irina');
      case 'kamila':
        return _t(ru: 'Камила', en: 'Kamila', es: 'Kamila');
      default:
        return id;
    }
  }

  // Хореография (диалоги)
  String get newChoreography =>
      _t(ru: 'Новая хореография', en: 'New choreography', es: 'Nueva coreografía');
  String get nameLabel => _t(ru: 'Название', en: 'Name', es: 'Nombre');
  String get choreoNameLabel =>
      _t(ru: 'Название хореографии', en: 'Choreography name', es: 'Nombre de la coreografía');
  String get copyOf => _t(ru: 'Копия', en: 'Copy of', es: 'Copia de');
  String copiedSnackbar(String name) =>
      _t(ru: 'Скопировано: $name', en: 'Copied: $name', es: 'Copiado: $name');
  String get rename => _t(ru: 'Переименовать', en: 'Rename', es: 'Renombrar');
  String renamedSnackbar(String name) =>
      _t(ru: 'Переименовано: $name', en: 'Renamed: $name', es: 'Renombrado: $name');
  String get deleteChoreoConfirm =>
      _t(ru: 'Удалить хореографию?', en: 'Delete choreography?', es: '¿Eliminar coreografía?');
  String deleteChoreoMessage(String name) => _t(
        ru: '«$name» будет удалена без возможности восстановления.',
        en: '«$name» will be deleted and cannot be restored.',
        es: '«$name» se eliminará y no se podrá recuperar.',
      );
  String get errorPrefix => _t(ru: 'Ошибка', en: 'Error', es: 'Error');

  /// Обмен хореографиями (ZIP-пакет)
  String get shareChoreography => _t(ru: 'Поделиться', en: 'Share', es: 'Compartir');
  String get shareChoreographyFailed => _t(
        ru: 'Не удалось собрать пакет (проверьте, что файл трека доступен).',
        en: 'Could not build the package (check that the track file is available).',
        es: 'No se pudo crear el paquete (comprueba que el archivo de audio esté disponible).',
      );
  String get shareChoreographyFailedOpen => _t(
        ru: 'Не удалось открыть окно отправки.',
        en: 'Could not open the share dialog.',
        es: 'No se pudo abrir el diálogo para compartir.',
      );
  String get importChoreographySuccess =>
      _t(ru: 'Хореография загружена.', en: 'Choreography imported.', es: 'Coreografía importada.');
  String get importChoreographyNoFile =>
      _t(ru: 'Не удалось прочитать файл.', en: 'Could not read the file.', es: 'No se pudo leer el archivo.');
  String choreoPackageImportError(String code) {
    switch (code) {
      case 'empty_archive':
        return _t(ru: 'Архив пустой.', en: 'The archive is empty.', es: 'El archivo está vacío.');
      case 'invalid_zip':
        return _t(
          ru: 'Это не ZIP-архив или он повреждён.',
          en: 'Not a valid ZIP file.',
          es: 'No es un ZIP válido o está dañado.',
        );
      case 'missing_manifest':
      case 'bad_manifest_json':
        return _t(
          ru: 'В архиве нет нужного описания (manifest).',
          en: 'The archive is missing a valid manifest.',
          es: 'Falta un manifiesto válido en el archivo.',
        );
      case 'wrong_format':
        return _t(
          ru: 'Это не пакет хореографии Midnight Dancer.',
          en: 'This is not a Midnight Dancer choreography package.',
          es: 'No es un paquete de coreografía de Midnight Dancer.',
        );
      case 'unsupported_version':
        return _t(
          ru: 'Версия пакета не поддерживается. Обновите приложение.',
          en: 'This package version is not supported. Update the app.',
          es: 'Esta versión del paquete no es compatible. Actualiza la app.',
        );
      case 'missing_json':
      case 'bad_json':
        return _t(
          ru: 'Данные в пакете повреждены.',
          en: 'The package data is damaged.',
          es: 'Los datos del paquete están dañados.',
        );
      case 'empty_style_name':
        return _t(
          ru: 'В пакете пустое название стиля.',
          en: 'The style name in the package is empty.',
          es: 'El nombre del estilo en el paquete está vacío.',
        );
      case 'missing_music':
        return _t(
          ru: 'В пакете нет файла музыки.',
          en: 'The package has no music file.',
          es: 'El paquete no incluye archivo de música.',
        );
      case 'bad_music_extension':
        return _t(
          ru: 'В пакете допустимы только MP3, M4A или WAV.',
          en: 'Only MP3, M4A or WAV are allowed in the package.',
          es: 'Solo se permiten MP3, M4A o WAV en el paquete.',
        );
      default:
        return _t(
          ru: 'Не удалось импортировать: $code',
          en: 'Import failed: $code',
          es: 'Error al importar: $code',
        );
    }
  }

  String importChoreographyStyleInfo(String archiveStyleName) => _t(
        ru: 'В архиве эти элементы относятся к стилю «$archiveStyleName».',
        en: 'In the archive, these elements belong to the style «$archiveStyleName».',
        es: 'En el archivo, estos elementos pertenecen al estilo «$archiveStyleName».',
      );
  String get importChoreographyPickTargetStyle => _t(
        ru: 'Добавить элементы в один из ваших стилей:',
        en: 'Add elements to one of your styles:',
        es: 'Añadir elementos a uno de tus estilos:',
      );
  String get importChoreographyCreateNewStyle => _t(
        ru: 'Создать новый стиль для этих элементов',
        en: 'Create a new style for these elements',
        es: 'Crear un estilo nuevo para estos elementos',
      );
  String get importChoreographyNewStyleNameHint =>
      _t(ru: 'Название нового стиля', en: 'Name for the new style', es: 'Nombre del estilo nuevo');
  String get importChoreographyImport => _t(ru: 'Импорт', en: 'Import', es: 'Importar');
  String get importChoreographyNoStylesYet => _t(
        ru:
            'У вас пока нет стилей. Будет создан новый стиль с элементами из архива. Ниже можно изменить название.',
        en:
            'You have no styles yet. A new style will be created with the steps from the archive. You can change the name below.',
        es:
            'Aún no tienes estilos. Se creará uno nuevo con los pasos del archivo. Puedes cambiar el nombre abajo.',
      );
  String get importChoreographyMergeHint => _t(
        ru:
            'Элементы из архива добавляются к выбранному вами стилю. Если элемент с таким именем уже есть, к имени добавится номер — хореография из архива останется привязана к нужным карточкам.',
        en:
            'Steps from the archive are added to the style you select. If a step with the same name already exists, a number is added to the name so the imported choreography stays linked to the right cards.',
        es:
            'Los pasos del archivo se añaden al estilo que elijas. Si ya existe un paso con el mismo nombre, se añade un número para mantener la coreografía enlazada a las tarjetas correctas.',
      );
  String get importChoreographyNewStyleHintBody => _t(
        ru: 'Создаётся отдельный новый стиль только с элементами из архива; ваши текущие стили не затрагиваются.',
        en: 'A separate new style is created only from the archive; your existing styles are unchanged.',
        es: 'Se crea un estilo nuevo solo con los elementos del archivo; tus estilos actuales no cambian.',
      );
  String choreoPackageImportErrorExtra(String code) {
    if (code == 'bad_import_args') {
      return _t(
        ru: 'Неверные параметры импорта.',
        en: 'Invalid import options.',
        es: 'Opciones de importación no válidas.',
      );
    }
    if (code == 'style_not_found') {
      return _t(
        ru: 'Выбранный стиль не найден.',
        en: 'Selected style was not found.',
        es: 'No se encontró el estilo seleccionado.',
      );
    }
    return choreoPackageImportError(code);
  }

  // Элементы
  String get elementsTitle => _t(ru: 'Элементы', en: 'Elements', es: 'Elementos');
  String get addOrEditElement =>
      _t(ru: 'Добавить элемент', en: 'Add element', es: 'Añadir elemento');
  String get editElement => _t(ru: 'Изменить элемент', en: 'Edit element', es: 'Editar elemento');
  String get elementNameHint =>
      _t(ru: 'Название элемента', en: 'Element name', es: 'Nombre del elemento');
  String get levelLabel => _t(ru: 'Уровень', en: 'Level', es: 'Nivel');
  String get videoSelected => _t(ru: 'Видео выбрано', en: 'Video selected', es: 'Vídeo seleccionado');
  String get pickVideo => _t(ru: 'Выбрать видео', en: 'Pick video', es: 'Elegir vídeo');
  String get descriptionLabel => _t(ru: 'Описание', en: 'Description', es: 'Descripción');
  String get descriptionHint =>
      _t(ru: 'Опишите детали шага...', en: 'Describe the step...', es: 'Describe el paso...');
  String get addFirstStyle =>
      _t(ru: 'Добавьте первый стиль', en: 'Add first style', es: 'Añade el primer estilo');
  String get styleNameHint => _t(
        ru: 'Например: Сальса, Бачата, Кизомба',
        en: 'e.g. Salsa, Bachata, Kizomba',
        es: 'p. ej. Salsa, Bachata, Kizomba',
      );
  String get newStyle => _t(ru: 'Новый стиль', en: 'New style', es: 'Estilo nuevo');
  String get currentStyle => _t(ru: 'Текущий стиль', en: 'Current style', es: 'Estilo actual');
  String get deleteStyleTooltip =>
      _t(ru: 'Удалить стиль', en: 'Delete style', es: 'Eliminar estilo');
  String get newLabel => _t(ru: 'Новый', en: 'New', es: 'Nuevo');
  String get searchHint => _t(ru: 'Поиск...', en: 'Search...', es: 'Buscar...');
  String get addLabel => _t(ru: 'Добавить', en: 'Add', es: 'Añadir');
  String get cannotDeleteLastStyle =>
      _t(ru: 'Нельзя удалить последний стиль!', en: 'Cannot delete the last style!', es: '¡No puedes eliminar el último estilo!');
  String get deleteStyleConfirm =>
      _t(ru: 'Удалить стиль?', en: 'Delete style?', es: '¿Eliminar estilo?');
  String get deleteStyleMessage => _t(
        ru: 'Вы уверены, что хотите удалить стиль и все его элементы?',
        en: 'Are you sure you want to delete this style and all its elements?',
        es: '¿Seguro que quieres eliminar este estilo y todos sus elementos?',
      );
  String get styleNameLabel => _t(ru: 'Название стиля', en: 'Style name', es: 'Nombre del estilo');
  String get styleNameExample =>
      _t(ru: 'Например: Сальса', en: 'e.g. Salsa', es: 'p. ej. Salsa');
  String get deleteElementConfirm =>
      _t(ru: 'Удалить элемент?', en: 'Delete element?', es: '¿Eliminar elemento?');
  String deleteElementMessage(String name) => _t(
        ru: 'Вы собираетесь удалить "$name". Это нельзя отменить.',
        en: 'You are about to delete "$name". This cannot be undone.',
        es: 'Vas a eliminar "$name". Esto no se puede deshacer.',
      );
  String get deletePermanently =>
      _t(ru: 'Удалить навсегда', en: 'Delete permanently', es: 'Eliminar para siempre');
  String saveErrorSnackbar(String e) =>
      _t(ru: 'Ошибка сохранения: $e', en: 'Save error: $e', es: 'Error al guardar: $e');
  String get sortByName => _t(ru: 'По А-Я', en: 'By A–Z', es: 'De la A a la Z');
  String get sortByLevel => _t(ru: 'По уровню', en: 'By level', es: 'Por nivel');
  List<(String, String)> get sortOptions => [
        ('name', sortByName),
        ('level', sortByLevel),
      ];
  List<(String, String)> get filterLevelOptions => [
        ('All', _t(ru: 'Все уровни', en: 'All levels', es: 'Todos los niveles')),
        ('Beginner', _t(ru: 'Начинающий', en: 'Beginner', es: 'Principiante')),
        ('Intermediate', _t(ru: 'Средний', en: 'Intermediate', es: 'Intermedio')),
        ('Advanced', _t(ru: 'Профи', en: 'Advanced', es: 'Avanzado')),
      ];
  String levelLabelFor(String value) {
    for (final e in filterLevelOptions) {
      if (e.$1 == value) return e.$2;
    }
    return value;
  }

  String masteryOnCard(int percent) =>
      _t(ru: 'Освоение: $percent%', en: 'Mastery: $percent%', es: 'Dominio: $percent%');
  String get elementMasteryProgress =>
      _t(ru: 'Ваш прогресс (%)', en: 'Your progress (%)', es: 'Tu progreso (%)');
  String get assignCurrentMove =>
      _t(ru: 'Назначить текущим', en: 'Set as current', es: 'Marcar como actual');
  String get clearCurrentMove =>
      _t(ru: 'Снять как текущий', en: 'Clear current', es: 'Quitar como actual');

  // Музыка
  String get musicTitle => _t(ru: 'Музыка', en: 'Music', es: 'Música');
  /// Только для подписей в UI (данные — [kCanonicalDefaultDanceStyleName]).
  String get defaultStyleDisplayName =>
      _t(ru: 'Общий', en: 'General', es: 'General');
  String get addStyleFirst => _t(
        ru: 'Сначала добавьте стиль в разделе Элементы',
        en: 'Add a style in Elements first',
        es: 'Primero añade un estilo en Elementos',
      );
  String addedSnackbar(String title) =>
      _t(ru: 'Добавлен: $title', en: 'Added: $title', es: 'Añadido: $title');
  String playErrorSnackbar(String e) =>
      _t(ru: 'Ошибка воспроизведения: $e', en: 'Playback error: $e', es: 'Error de reproducción: $e');
  String get editTrack => _t(ru: 'Редактировать трек', en: 'Edit track', es: 'Editar pista');
  String get deleteTrackConfirm =>
      _t(ru: 'Удалить трек?', en: 'Delete track?', es: '¿Eliminar pista?');
  String deleteTrackMessage(String name) => _t(
        ru: '«$name» будет удалён без возможности восстановления.',
        en: '«$name» will be deleted and cannot be restored.',
        es: '«$name» se eliminará y no se podrá recuperar.',
      );
  String get loadTrack => _t(ru: 'Загрузить трек', en: 'Load track', es: 'Cargar pista');
  String get trackPlaybackSpeed =>
      _t(ru: 'Скорость трека', en: 'Track speed', es: 'Velocidad de la pista');
  String trackSpeedValue(double v) => '${v.toStringAsFixed(2)}×';
  String get allStyles => _t(ru: 'Все стили', en: 'All styles', es: 'Todos los estilos');
  String get noTracksHint => _t(
        ru: 'Нет треков. Загрузите MP3, M4A или WAV.',
        en: 'No tracks. Load MP3, M4A or WAV.',
        es: 'No hay pistas. Carga MP3, M4A o WAV.',
      );
  String get stopTrack => _t(ru: 'Стоп', en: 'Stop', es: 'Parar');
  String get trackPositionHint => _t(
        ru: 'Позиция воспроизведения (перетащите круг)',
        en: 'Playback position (drag the knob)',
        es: 'Posición de reproducción (arrastra el control)',
      );

  // Видео / виджеты
  String get videoLoadError =>
      _t(ru: 'Ошибка загрузки видео', en: 'Video load error', es: 'Error al cargar el vídeo');
  String get speedLabel => _t(ru: 'Скорость', en: 'Speed', es: 'Velocidad');
  String get previewOnWeb =>
      _t(ru: 'Предпросмотр на web', en: 'Preview on web', es: 'Vista previa en web');

  // Сплэш
  String get appSubtitle =>
      _t(ru: 'Приложение для танцев', en: 'Dance Training App', es: 'App de baile');
  String get byNighttech =>
      _t(ru: 'от Nighttech', en: 'by Nighttech', es: 'por Nighttech');
}
