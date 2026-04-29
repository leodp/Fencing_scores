# Changelog

## [V1.2 - 2026-04-29]

### Rounds Page
- Added black borders to Name column and header cells for improved visibility
- Improved vertical sizing to use ~98% of screen height
- Reduced CANCEL/RESET button font size by 25% to prevent text clipping
- QR OUT now uses a screenshot of the rounds matrix as background
- QR code size increased to 95% of screen height for better readability
- Names are now saved to backup on edit, persisting across app restarts
- Default save filename changed to `BoutRounds_YYYYMMDD_hh.mm.ss.csv`
- QUIT now clears all backup files (Fencing, Merged, KO) for a clean restart
- Loading a CSV file or QR code now triggers a backup save, so Merged "Reload Round" works immediately after import

### Merged Page
- Disabled P value recalculation; FinalPos is the authoritative ranking
- P values are now preserved when importing from Round data (calculated once at import)
- P and FinalPos cells are no longer editable via short click
- Increased button widths by 20% for better readability
- Uniform close spacing between buttons (4dp gaps)
- QR OUT shows vertical "Merged ranking" label alongside the code
- Default save filename changed to `RankingRounds_YYYYMMDD_hh.mm.ss.csv`

### KO Page
- Compact bracket layout: rounds 2+ positioned closer horizontally using FrameLayout
- Repechage bracket also uses compact layout with expanded vertical spacing to prevent overlaps
- Repechage column headers renamed from "|| R1" to "Round 1" format
- Uniform close spacing between buttons (4dp gaps)
- QR OUT shows vertical "KO" label alongside the code
- Default save filename changed to `KO_results_YYYYMMDD_hh.mm.ss.csv`
- Improved scroll behavior: page swipe only triggers at content edges

## [2026-04-17]

### Bug Fixes

#### Text Color Visibility on Android 16 (MergedFragment, KOFragment, MainActivity, MergedActivity)
- **Issue**: On Android 16 phones with dark mode enabled, text in Merged data cells and KO match buttons was invisible (white on white). The app uses `Theme.MaterialComponents.DayNight.DarkActionBar` but has no dark mode resources, so DayNight switched default text colors to white while backgrounds remained white.
- **Fix**:
  - Forced light mode via `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)` in both `MainActivity` and `MergedActivity`
  - Added explicit `setTextColor(Color.BLACK)` to all Merged data cells (EditText), KO match buttons (main bracket, consolation bracket, Grand Final), round headers, Grand Final header, and Repechage checkbox
  - Replaced unsupported CSS `<span style='color:...'>` with Android-compatible `<font color='...'>` tags in KO match button HTML labels (Android's `Html.fromHtml()` does not support CSS style attributes)

## [2026-03-13]

### Bug Fixes

#### FinalPos Recalculation on Merge (MergedFragment)
- **Issue**: When adding CSV data to existing data (via ADD button or QR ADD), the FinalPos values were not being recalculated for the combined dataset. Each pool retained its original independent FinalPos values (e.g., both pools had positions 1,2,3...), resulting in duplicate positions.
- **Fix**: Modified `calculateFinalPositions()` to clear all `finalPos` values to `null` before recalculating, ensuring fresh position assignment across the merged dataset.

#### QR ADD Missing FinalPos Recalculation (MergedFragment)
- **Issue**: The QR ADD flow was missing the call to `calculateFinalPositions()` after adding scanned data.
- **Fix**: Added `calculateFinalPositions()` call in `handleQrScanResult()` before rendering rows.

#### Crash Restore Behavior (MergedFragment, KOFragment)
- **Issue**: On normal app restart, Merged and KO pages were auto-restoring data from backup files, even though the app had exited cleanly. Data should only be restored after a crash.
- **Fix**: Added check for `MainActivity.crashDetected` flag in `tryAutoRestoreFromBackup()` methods. Auto-restore now only triggers when the crash detection mechanism (CRASH.txt file) indicates a previous crash.

#### Round Sorting Not Persisted (RoundFragment)
- **Issue**: When reordering participants by long-pressing the P column header in Round, the new sorted order was not saved to backup. This caused Merged's "RELOAD ROUND" to load the old unsorted data with incorrect Nr values.
- **Fix**: Added `saveBackupToDocuments()` call after `sortByPRankingAndReload()` in both the P header and P cell long-press handlers.

### UX Improvements

#### Edit Dialog Keyboard Behavior (MergedFragment)
- **Issue**: When clicking on editable cells in Merged to edit values, the on-screen keyboard did not appear automatically and the previous value was not pre-selected, unlike the behavior in Round.
- **Fix**: Added `setSelectAllOnFocus(true)`, `requestFocus()`, and `setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE)` to edit dialogs in MergedFragment to match Round's behavior.

### Notes
- Ranking formula remains: % (percent) → I (index) → → (given touches), in descending order
- V (victories) is displayed but not used in ranking calculations
