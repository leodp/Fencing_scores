# Fencing Scores - Current Status Baseline

Date: 2026-07-01
Scope: Baseline snapshot before major extension work

## 1) Build and Runtime Snapshot
- Platform: Android app (Java, AndroidX, ViewModel, ViewPager2)
- App module: app
- package: com.fencing.scores
- compileSdk: 34
- minSdk: 30
- targetSdk: 34
- versionCode: 12
- versionName: 2.22
- Build system: Gradle + Android Gradle Plugin 9.0.1
- Java level: 21
- Key external libs:
  - com.google.zxing:core:3.5.2
  - com.journeyapps:zxing-android-embedded:4.3.0

## 2) Navigation and Page Model
Current app flow is dynamic, not fixed to 4 pages.

Page sequence:
- Rounds1..RoundsN (N = 1..5)
- Merged
- KO
- Final

Main behavior:
- MainActivity hosts ViewPager2 and dynamically rebuilds MainPagerAdapter when nrRounds changes.
- MainPagerAdapter returns RoundFragment.newInstance(roundCode) for each round page, then fixed pages (Merged, KO, Final).
- Circular swipe logic is implemented in MainActivity (first and last page wraparound).

## 3) Core Data Model (ScoresViewModel)
ScoresViewModel is the central state holder for cross-page coordination.

Key state:
- nrPart (participants)
- nrRounds (1..5)
- participantNames[]
- boutResults (active round matrix)
- roundBoutResults map: roundCode -> matrix
- roundColorCycleIndex map: roundCode -> theme index
- colorCycleIndex (active round)
- finalKORankings (for Final page rendering)
- requestKORankings (signal to KO page)

Key behaviors:
- Per-round data persistence when switching active round.
- Dynamic creation/removal of rounds with constraints [1..5].
- Multi-round synchronization tools (e.g., reorderAllRounds, name-aware remap in setParticipantNames).
- Reset to defaults with round map reset.

## 4) Fragment Responsibilities
- RoundFragment:
  - Per-round matrix entry/editing and ranking columns.
  - Round help/workflow actions (load/save/QR/restore/quit and related flows).
  - Participant sorting/reordering workflows.
- MergedFragment:
  - Consolidates and edits merged ranking rows.
  - Restore/replace/add/QR/save flows.
  - Backup/restore integration for merged data.
- KOFragment:
  - KO modes (standard, repechage, quick, mix-round variants).
  - Match data structures (Match, RepechageTree, KOGroup).
  - Match propagation, mode-specific rendering, and ranking generation for Final.
  - KO backup/restore and CSV/QR import-export.
- FinalFragment:
  - Observes finalKORankings from ViewModel.
  - Filters unresolved/empty placeholders and renders 3-column ranking layout.
  - Triggers KO ranking refresh request on resume.

## 5) Storage and Recovery
- Internal filesDir backups used by pages:
  - Fencing_backup.csv (round data)
  - Merged_backup.csv (merged data)
  - KO_backup.csv (KO data)
- Crash detection:
  - MainActivity uses CRASH.txt logic to detect previous abnormal termination.
  - Auto-restore paths are guarded by crashDetected checks.

## 6) Current Documentation State
- CHANGELOG.md is active and reflects v2.22 updates, including grouping-merge routine behavior.
- README.md and Fencing_scores.md are aligned on dynamic rounds and Merged grouping/sorting behavior.
- docs/program-structure-neo4j.cypher remains structurally valid and does not require graph changes for this release.

## 7) Baseline Risks Before Major Extension
- Architectural complexity concentrates in KOFragment and RoundFragment (large classes with many responsibilities).
- Some repo docs are not fully synchronized with current dynamic rounds behavior.
- Existing backup/import flows are feature-rich and coupled; extension work should preserve compatibility of CSV/QR/meta formats.

## 8) Recommended Next Organizing Step
Use this baseline together with docs/program-structure-neo4j.cypher as a change-control anchor:
- Keep model updates in sync with architecture changes.
- Add one changelog entry per functional slice (Rounds, Merged, KO, Final, Storage, Navigation).
- Keep KO ranking logic changes isolated and regression-tested with saved KO CSV fixtures.
