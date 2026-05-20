# Changelog

## [V1.7 - 2026-05-20]

### KO Page - Ranking and Reset Fixes
- Fixed standard KO rankings to always include all participants, even when no KO matches are completed yet
- Final page now correctly reflects initial ranking order from Merged FinalPos after KO RELOAD in standard KO mode
- Fixed partial-match ranking issue where only eliminated participants could appear; active and unresolved participants now remain ranked using progress plus FinalPos tiebreak
- Fixed RESET behavior in standard KO: resetting a match now clears downstream propagated winners and dependent match results correctly

### Round Page - Sorting Extensions
- Long-press on P (Pos) cells now toggles ordering between increasing and decreasing position order
- Long-press on participant name cells now toggles full-table reordering between alphabetical (A-Z) and reverse alphabetical (Z-A)
- Name/P reordering continues to keep bout data aligned with the reordered participants

## [V1.6 - 2026-04-30]

### Round Page
- Header row click action changed from CSV export to QR code generation
- QR code displayed fullscreen with table screenshot as background (same as Help → QR OUT)

### KO Page - Quick & Mix Ranking Fix
- Fixed Quick KO and Mix-Rounds ranking to include all participants from the start
- Ranking now calculated immediately when KO starts, using FinalPos as initial ordering
- After each match, ranking is updated: match results take priority, FinalPos used as tiebreaker
- Participants in earlier groups (e.g., Quick 1:2) always rank above those in later groups (e.g., Quick 3:4)
- Unresolved participants (no match played yet) ranked by FinalPos within their group
- Aligned behavior with standard KO mode which uses FinalPos for tiebreaking
- Progress-score system: active participants (won last match, waiting for next round) rank above eliminated participants at the same round level

### Navigation
- Improved page swipe responsiveness by reducing ViewPager2 internal touch slop
- Nested scroll views in all fragments no longer compete as heavily with page swipes

### Battery Optimization
- Reduced unnecessary UI redraws: KOFragment and MergedFragment LiveData observers now skip rendering when fragment is not visible
- Added proper onPause/onResume lifecycle handling in MainActivity to allow screen to dim when app is backgrounded
- No impact on data persistence or app resume speed — all data preserved via ViewModel and backup files

## [V1.5 - 2026-04-30]

### Icon
- Changed icon background to #001582 for consistent display across Android launchers
- Added adaptive icon support (mipmap-anydpi-v26) with proper foreground/background layers
- Reduced foreground image size to prevent cropping on round icon shapes

### KO Page - New Modes
- Replaced Repechage checkbox with a pulldown menu (8 KO modes)
- New modes: Quick KO (1:2, 1-4, 1-8 groupings) and Mix-Rounds (by P value)
- Quick KO creates smaller brackets from FinalPos-sorted participants
- Mix-Rounds groups participants by their P ranking from Merged page
- Mix-Rounds entries disabled when only one participant has P=1
- Groups padded with "Empty" to next power-of-2, auto-advanced (15:0)
- Multi-group grid layout: groups arranged side-by-side and wrapped by screen width

### KO Page - Visual Improvements
- Position numbers shown in front of participant names in all modes
- Match boxes have rounded corners and border consistently across all modes
- Compact bracket positioning: later rounds overlap horizontally when no vertical collision
- Pulldown menu styled with black background and white bold text
- Spinner properly sized on initial load to fit all mode labels
- Color theme changes from Rounds page now reflected immediately in KO

### KO Page - Bug Fixes
- Fixed crash (NPE) in Quick KO 1-4, 1-8 and all Mix-Rounds modes caused by text measurement on detached Button
- Fixed score entry popup to use same two-dialog pattern as standard KO

### KO Page - Data Format
- Backup/CSV/QR format updated: META line now includes koModus field
- Group tree data uses "G1", "G2", etc. identifiers for Quick/Mix modes
- Import correctly restores KO mode and spinner selection
- QR code label matches selected pulldown menu text

### Merged Page
- P column cells re-enabled for editing with numeric input
- Fixed P value being overwritten by FinalPos during CSV import
- Save filename defaults to `MergedRanking_YYYYMMDD_hh.mm.ss.csv`
- QR code label changed to "MergedRanking"

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
