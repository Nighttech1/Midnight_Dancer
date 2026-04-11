# Модели TTS (Piper VITS / Sherpa-ONNX)

Чтобы голоса **Руслан, Ирина, Камила** звучали по-разному (офлайн), нужны пайпер-пакеты из старого приложения.

## Быстрая настройка: скопировать голоса из old_app

Из корня проекта выполните (PowerShell):

```powershell
.\scripts\copy_voices_from_old_app.ps1
```

Скрипт создаёт папки и копирует модели:

- `lite/ruslan/` — Руслан (flavor lite)
- `standard/ruslan/`, `standard/irina/` — Руслан, Ирина
- `full/ruslan/`, `full/irina/`, `full/kamila/` — Руслан, Ирина, Камила (en-US акцент)

В каждой папке ожидаются файлы **Piper VITS** (как в старом приложении):

- `ru_RU-ruslan-medium.onnx` / `ru_RU-irina-medium.onnx` / `en_US-libritts_r-medium.onnx` (или общее имя `model.onnx`)
- `tokens.txt`
- `lexicon.txt` — опционально (у Piper его нет)

После копирования пересоберите приложение. Если папки пусты или модели не загружаются — используется системный TTS (один голос для всех).
