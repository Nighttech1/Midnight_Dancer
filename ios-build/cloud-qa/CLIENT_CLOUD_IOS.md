# iPhone в облаке — Mobile Next Cloud

## Куда вставить API-ключ (быстрый способ)

Откройте файл:

**`.cursor/mcp.json`**

Найдите блок `mobilenext-cloud` и замените только плейсхолдер:

```json
"mobilenext-cloud": {
  "url": "https://app.mobilenext.ai/mcp",
  "headers": {
    "Authorization": "Bearer mob_PASTE_YOUR_KEY_HERE"
  }
}
```

1. Сотрите `mob_PASTE_YOUR_KEY_HERE`
2. Вставьте свой ключ целиком (он обычно начинается с `mob_`)
3. Должно получиться: `"Authorization": "Bearer mob_xxxx..."`
4. Сохраните файл
5. Cursor → Settings → MCP → перезапустите сервер **mobilenext-cloud** (или перезапустите Cursor)

После этого Клиент берёт iPhone в облаке **без браузерного логина** (ключ уже в MCP).

⚠️ Не коммитьте файл с настоящим ключом в git / не кидайте ключ в чат.

---

## Зачем это облако

На Windows нет iOS Simulator. **Mobile Next Cloud** — основное iPhone-облако команды (агент: tap / swipe / screenshot).

Сайт: https://mobilenext.ai/cloud  

## Лимит минут (важно)

Hobby Mobile Next даёт порядка **20 минут в сутки**. Минуты тикают, пока телефон **выделен** (allocate), даже если агент просто ждёт сборку.

### Как тратить экономно

1. Сначала довести всё до готовности **без** облака: IPA лежит на диске, upload URL получен/файл уже залит, сценарий приёмки понятен.  
2. **Только потом** `allocate` → `install` → 1–2 минуты жестов/скринов → сразу `release`.  
3. **Не** выделять iPhone «пока крутится GitHub Actions / Gradle».  
4. **Не** держать сессию между длинными ролями (Архитектор, KMP Shared, CI).  
5. Цель: уложиться в **2–5 минут** облака на одну приёмку, а не 15+.

### Ошибка сегодняшнего C-09

Телефон взяли **до** готовности IPA и держали во время CI → суточный лимит сгорел до установки приложения.

## Что делает Клиент

1. Убедиться: IPA готов + upload готов (или файл уже в Mobile Next)  
2. Allocate iPhone через MCP `mobilenext-cloud`  
3. Install IPA, короткий сценарий, скрины  
4. **Сразу** Release устройства  
5. Шлёт скрины вам в чат + `memory/tickets/.../visual/`

## Android

Локально, Клиент сам:

```powershell
.\scripts\ensure-client-devices.ps1
```
