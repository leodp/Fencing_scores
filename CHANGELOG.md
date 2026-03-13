# Changelog

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
