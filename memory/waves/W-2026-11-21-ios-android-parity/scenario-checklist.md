# Acceptance matrix

Use the accepted Android 0.5.0 APK and the corresponding iOS commit. Create the same disposable media, styles, and record counts. Screenshot pairs document appearance. The pass for each row also requires observing the listed actions.

| ID | Scenario | Evidence required |
|---|---|---|
| A01 | Cold start ×3 | Original five-second video visible, centered and undistorted on black, no skip, automatic Elements transition. Review screen video. |
| A02 | Navigation and settings | Enter all five tabs, verify selected dock card, open/close Settings, inspect keyboard and small display. |
| A03 | Styles | Add/rename/duplicate/delete/restart. Fallback style is locked and retains affected records. Check all catalog pickers. |
| A04 | Elements | Import test videos from Photos and Files, cancel and rejected save, successful save, relaunch/playback, remove only disposable source. |
| A05 | Music | Import, edit, speed/seek, relaunch/play, delete success/failure. The original remains on the provider. |
| A06 | Choreography | Compose with three distinct videos plus one repeat and a music track, save/relaunch, check Free quota and legacy rights and explicit Pro test state. |
| A07 | Timeline | Play across two seams, seek to clip two and back to zero, split/trim/reorder/delete/duplicate where Android supports it; review video capture and save. |
| A08 | Trainer | Run Free ordinary session, select choreography, inspect disabled Free controls, exercise Pro intervals, pause/background/manual resumption/stop on disposable data. |
| A09 | Sets | Own create/edit with style-scoped sections, view imported archives read-only, share actual package, import and restart round-trip, preserve originals. |
| A10 | iPhone upgrade | Install signed same-bundle update over Flutter with fixture records. Check names, sequence order, old and newly owned media after restart and second upgrade. |

A01 through A09 start as pending. A10 needs a physical iPhone with the previous app/data. Simulator navigation evidence alone cannot close behavioral or appearance parity.
