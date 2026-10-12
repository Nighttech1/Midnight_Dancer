# Tags ≠ levels · dual playback speed (2026-10-08)

## Tags

- Product **no longer has «уровни»** as a first-class field for UI.
- Settings «теги» (`ElementCategoryConfig`) feed filter chips and card pills.
- One element / track / vault item: **max 1 tag** (`MAX_TAGS_PER_ELEMENT = 1`, product 2026-10-08).
- Legacy JSON `level` may still exist for Flutter import — map into `tags` for display; do not show a separate «уровень» chrome.

## Dual playback speed

| Scope | Field | Range | When used |
|-------|-------|-------|-----------|
| Library track | `Song.playbackSpeed` (base) | **0.4 … 2.0** | Always when playing the track in Music / anywhere without choreo override |
| Choreography | `songPlaybackSpeed` (override) | **0.5 … 2.0** | Only inside that choreography; does **not** mutate Song.base |

Validators: update `DomainValidators.PLAYBACK_SPEED_*` for songs; add `CHOREO_PLAYBACK_SPEED_*` for override.
