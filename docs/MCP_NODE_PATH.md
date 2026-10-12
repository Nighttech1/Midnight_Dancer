# MCP — пути Node (Windows)

**Проблема 2026-09-28:** в логах Cursor (`MCP project-0-*.log`) массово:
`Не удается найти указанный файл` / `Connection closed` —
в `.cursor/mcp.json` был мёртвый путь `E:\Applications\Tools\node\npx.cmd`.

**Исправление:** все `npx`-серверы →  
`C:\Users\alexk\AppData\Local\Programs\nodejs\npx.cmd`  
(Node v22; альтернатива на машине: `D:\Languages\Node.js`).

| Сервер | Статус после фикса пути |
|--------|-------------------------|
| `mobilenext-cloud` | уже работал (HTTP) |
| `maestro` | уже работал (bat) |
| `mobile-mcp`, playwright*, firebase, storepilot, a11y, image-compare, appium, mcp-mobile-interaction | путь Node исправлен |
| `semgrep` | пакет переименован: `semgrep-mcp` (404) → `mcp-server-semgrep` |
| Figma / Notion plugins | нужна OAuth (`mcp_auth`) — не path |

После правки `mcp.json`: **Cursor → Settings → MCP → Restart** (или Reload Window), иначе статус «errored» останется старым.
