# Source checkpoint — 2026-11-19

## Provenance

Based on remote main `b7410cafc854583a704d1594a31eac2c2c766766` (verify exact parent from git; historical shorthand b7410ca). Overlay: 404 accepted source paths from local W-2026-11-16-styles-owned-media. Original local manifest retained as `accepted-android-source.sha256`. Git normalizes text line endings; this raw Windows manifest is provenance, not a promise of byte-identical checkout files on another platform. A separate `published-source.sha256` records the Git-normalized source bytes in this checkpoint. CI configuration changes are deliberate exceptions and are independently reviewed. Positive manifests do not encode deletions: `removed-paths.txt` explicitly records one obsolete ElementDetailSheet.kt removed to match the accepted local tree. Room schema JSON and all inherited build dependencies are retained.

Accepted Android artifact (retained locally, not committed as a 100 MB Git blob):

- `MidnightDancer-0.5.0-styles-media-startup-debug.apk`
- SHA256 `f8ecd9bf5f78357626a28b901de458734ea4b03377bbaadacfc398dd8abf7915`
- package `com.midnightdancer.app`; versionName `0.5.0-rehearsal-preview`, code 3; debug, not store release signing.

## Historical Android acceptance (not rerun on publication date)

Independent Client accepted the APK and source checkpoint: 289 host tests passed; 26 native product cases passed in split cold runs. A separate one-case maintenance cleanup test is not a 27th product test. GUI checks covered canonical styles across sections, set styles, immutable imported sets, retained source-deleted media and repeat playback, plus cold-start splash geometry. Individual GUI runs covered specific clips/durations, not every possible long composition.

User data restored to original counts: 14 moves, 4 songs, 5 vault entries, 7 sets; 11 canonical styles. Original external user media were never deleted. Private backups, raw databases, personal recordings and device paths are intentionally absent from this public checkpoint.

## Remaining work

- iOS compile, signing, feature parity and Flutter data migration remain unaccepted. See `CONTINUE_IOS.md`.
- Real subscription billing and commercial store release not enabled/accepted.
- General settings persistence outside canonical styles remains a separate follow-up.
- User-facing Element deletion UI not added. No automatic media garbage collection until complete reference tracking exists.
- Cloud-created Android debug builds may use a different signing key: do not uninstall the user's installed app to force an update; preserve/export data first and use deliberate signing strategy.

## Team contributions for this publication

Orchestrator /root prepared isolated snapshot and cloud handoff. Architect /root/rehearsal_architect verified 404 hashes and dependency closure. Platform Ops /root/subscription_platform_ops audited/prepared manual diagnostic CI. Independent Client /root/protocol_reviewer reviews exact source/CI/privacy diff before push and remote evidence afterwards. This is source-publication review, not fresh device or iOS acceptance.
