# Changelog

## [V2.22 - 2026-06-29]

### Update - 2026-07-01 (Grouping and Round Rendering)
- Merged grouping routine now follows strict set rules for `RELOAD round`:
  - rounds with identical participant sets are merged into one group
  - fully disjoint participant sets remain separated
  - any overlap across non-identical sets collapses all groups into one merged group
- Added explicit Merged toast when distinct groups are force-merged:
  - `Same participant in distinct groups: MERGED`
- Aligned add/import merge behavior with group-composition checks and validated mixed scenarios (same-set reuse and new disjoint groups)
- Fixed round-page rendering race during round-count changes (increase/decrease):
  - prevents temporary wrong participant matrix on the current page
  - prevents removed round data from contaminating the last remaining round
- Merged sorting extension:
  - `Grp` header click and long-press toggle group sorting A-Z / Z-A
  - long-press on `Grp` value cells also toggles group sorting

### Round Pages - Independent Participant Sets
- Enabled the same participant-name actions on every Round page (`Rounds1..RoundsN`): click to edit name, long-press to toggle name sorting
- Round pages now keep participant names and ordering round-local; each round can hold the same participants, different participants, or mixed participant subsets in independent order
- Round-local sorting no longer forces symmetric resorting of bout matrices in other rounds

### Merged Page - ExtendParallel Name-Aware Merge
- Added name-overlap-aware ADD flow for CSV/QR imports:
  - `Append results`: merges rows by participant name and recomputes aggregated stats
  - `Add as distinct mixed group`: appends as separate mixed group and sets `P=0` to disable KO-Mixed grouping ambiguity
- Added `#` column (total matches) to Merged data model, table, backup/export CSV, and QR payload
- Merged `RELOAD round` now aggregates using each round's own participant names, correctly handling:
  - distinct groups (no overlap)
  - repeated same group across rounds (full overlap)
  - partially overlapping mixed groups

### Release
- Bumped app version to `2.22` (`versionCode 12`)

## [V2.1 - 2026-06-11]

### KO Page - 3:4 Popup Name Resolution
- Fixed score popup titles for main KO matches to resolve bracket references (`W*` / `L*`) using the same round-aware logic as KO box labels
- Fixed Third Place (`3:4`) popup showing placeholder refs like `L1 Vs L2`; popup now shows the same participant names already visible in the `3:4` box
- Verified popup name resolution remains correct in other KO modi (Quick KO and Mix-Rounds use dedicated group dialogs with resolved names)

### KO with Repechage - EMPTY Propagation Stability
- Fixed repechage reference resolution for `EMPTY`-heavy brackets so `EMPTY vs EMPTY` matches resolve deterministically instead of staying as unresolved placeholders (e.g. `L1.1 Vs L1.2`)
- Fixed propagation chain for `EMPTY` scenarios by treating unresolved `EMPTY` winners/losers as concrete `Empty` values, allowing downstream boxes to update correctly
- Preserved displayed `EMPTY Vs EMPTY` result as `0:0` while still enabling forward propagation

### Round Page - Late Missing Bout Threshold
- Updated late-missing-bout highlight threshold from `<= 25%` to `<= 35%` (highlight color remains `#B0B0B0`)

## [V2.0 - 2026-05-22]

### KO Ranking - Correct Positions for Unplayed Matches
- Simplified repechage ranking: rank by main bracket advancement level, actual loss status, repechage advancement level, and FinalPos — no position locking or bracket-path simulation
- Same advancement logic applied consistently in both main bracket and repechage trees: winners of round N who haven't played round N+1 rank above losers of round N
- Participants who won actual matches rank above those who lost, regardless of bracket path; within the same level, FinalPos determines order
- In partially-played rounds, actual losers rank below assumed losers (participants whose match wasn't played)
- Repechage tree actual results differentiate losers at the same main bracket level (more repechage wins = better rank)
- Fixed standard KO bug where Third Place match winner could rank above finalists when the Final was not yet played
- Fixed standard KO to assume better FinalPos wins for unplayed matches (ranking only, no tree modification)
- Ranking changes are calculation-only: bracket trees and match names are never modified by ranking assumptions

### KO CSV Export - Complete Data for All Modes
- Fixed CSV SAVE export to include `#META` header line (koSize, repechage, modus) for reliable mode detection on import
- Fixed CSV SAVE export for Quick KO and Mix-Rounds modes (koModus >= 2) which previously exported empty files because group data was not written
- All KO pulldown choices now produce complete CSV files with all executed matches

### KO CSV Import - Auto-Detection of KO Mode
- Added automatic detection of Quick KO / Mix-Rounds modes from CSV tree IDs (G-prefix) when no `#META` line is present
- Group size is inferred from round-1 match count to determine the correct modus (Quick KO 1:2, 1-4, or 1-8)
- Added CSV-based group reconstruction fallback: groups can be rebuilt from CSV data alone when Merged backup is unavailable

### KO with Repechage - Name Placement and Propagation Fix
- Fixed semifinal winner propagation in repechage mode: winners now correctly flow from semifinals to the final match (previously the propagation loop stopped one round too early)
- Fixed loser determination to use score-based detection (score1 vs score2) instead of relying solely on winner name string comparison, preventing wrong names in repechage trees when name formats differ
- Eliminated redundant double-propagation of losers to repechage trees during CSV import, reducing the risk of stale data overwrites
- Fixed repechage ranking: a participant who won in a primary repechage tree (e.g. R1L) now correctly ranks above one who won only in a deeper sub-bracket (e.g. R1L2L), even if both have the same number of repechage wins — the depth of the shallowest tree where they won is used as a tiebreaker (fewer 'L' letters in tree ID = shallower = better rank)

## [V2.1 - 2026-05-22]

### KO with Repechage - Ranking Consistency Fix
- Fixed repechage ranking so positions 1 and 2 are now locked to the main final outcome: winner stays 1st, final loser stays 2nd
- Fixed repechage ranking to derive main-bracket winner/loser flow from actual match results during ranking calculation, preventing participant-name drift when cached winner fields are stale
- Fixed repechage position assignment so finalists cannot be overwritten by later semifinal-loser/repechage assignments

### KO CSV REPLACE - Legacy Repechage Detection
- Fixed import of KO CSV files without `#META` by auto-detecting repechage mode when repechage tree rows (`R1...` trees other than `R1`) are present
- Repechage tree rows in legacy files are now loaded and ranked correctly instead of being silently ignored

## [V2.0 - 2026-05-21]

### MRDebug5 - KO and Final Ranking Fixes
- Final ranking is now refreshed immediately after KO reload/import/restore and after KO table re-renders, so loading KO data updates Final page rankings without extra manual steps
- Fixed standard KO ranking to correctly apply the `Final 3rd:4th pos.` match result, including cases where the lower-seeded participant wins the 3rd-place match
- Added consistent 8dp rounded-corner styling for KO score popup buttons and the Round "Rounds Nr. in the pool" popup buttons
- Renamed project icon asset folder from `îcon_img` to `icon_img`

### MRDebug6 - KO CSV Reload and Pulldown Cleanup
- Fixed KO `REPLACE` imports for saved CSV files without prior Merged data by rebuilding KO participant seeds directly from the imported KO file, so entering or resetting KO results updates Final rankings correctly afterward
- Updated the KO modus pulldown to match the other buttons with 48dp height, 8dp rounded corners, and a visible white down-arrow indicator

### Stability and UX Cleanup
- Reduced noisy debug messaging in Round and Merged flows by commenting non-essential diagnostic logs and sort-notification toasts
- Kept important messages for save confirmations and error conditions

### Battery and Efficiency
- Removed redundant Merged backup writes triggered by passive table renders; backups are now tied to data-changing actions
- Removed duplicate Merged render pass in `onStart` (render/restore work remains in `onResume`)
- Removed immediate extra table redraws after opening CSV picker actions in Merged

### Round and Merged Consistency
- Verified Round missing-bout highlight threshold is `25%`
- Verified Round matrix height scaling uses `100%` of available screen height
- Ensured backup still runs for Round bout entry and Round name edits
- Ensured backup still runs for Merged data edits, name changes, sorting actions, and import flows

## [V1.8 - 2026-05-20]

### Multi-Rounds (1..5)
- Added dynamic multi-round architecture: pages are now `Rounds1..RoundsN`, then `Merged`, `KO`, `Final`
- Added long-press action on the Round name header to configure round count with a `1..5` popup (`1` highlighted, `CANCEL` action)
- Round pages now carry their own round code label (`Round Nr: X`)
- Round backups are now round-scoped (`Fencing_backup.csv` for R1, `Fencing_backup_Rn.csv` for R2..R5)

### Data Synchronization and Navigation
- Added ViewModel support for per-round bout matrices and per-round color-cycle state
- Added dynamic page-index navigation helpers in MainActivity and updated fragments to use them
- Sorting by P and sorting by Name now reorder participants consistently across all configured rounds

### Merged Page
- `RELOAD round` now aggregates ranking stats across all enabled rounds (by participant name), then recalculates P and FinalPos
- Long-press on Name cells now toggles sorting between A-Z and Z-A
- Long-press on P cells now toggles sorting between increasing and decreasing P values
- Long-press on FinalPos cells now toggles sorting between increasing and decreasing FinalPos values

### Round Page
- Updated late-missing-bout highlight color to `#B0B0B0` when missing valid bouts are <= `25%`
- Matrix height scaling updated to use `100%` of available screen height
- Round CSV default filename now includes round code before date (e.g. `BoutRounds_1R_YYYYMMDD_HH.mm.ss.csv`)
- Fixed clean QUIT flow: backup files are deleted and the next startup is empty
- Clearing a participant name now permanently clears that participant's bout row/column (results are not restored when re-entering a name)
- Name header text updated to `Round Nr: X`; click and long-press now both open round count selector

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
- Improved vertical sizing to use 100% of screen height
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
