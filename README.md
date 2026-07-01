# Fencing Scores - Android App

A comprehensive Android application for managing fencing tournament scores, from pool rounds through knockout stages to final rankings. Built under the GNU General Public License V3.0.

## Features

- **Dynamic Tournament Flow**: Rounds1..RoundsN (1..5) → Merged → KO → Final
- **Matrix-based pool scoring**: Track bout results between participants
- **Merged rankings**: Combine results from multiple pools
- **Knockout bracket**: Standard or repechage elimination brackets
- **Final rankings**: Consolidated tournament results
- **QR code data transfer**: Share data between devices
- **Auto-backup**: Crash recovery for all pages
- **Touch-friendly interface**: Rounded buttons, easy score entry

## Pages Overview

### Round (Pool Phase)
- Matrix scoring grid for pool bouts
- Automatic calculation of V (victories), → (given), ← (received), I (index), % (win rate), P (position)
- Each Round page is independent and can use the same participants, different participants, or partial overlaps in different order
- Bout order suggestions
- Help dialog with LOAD, RESTORE, SAVE, QR OUT, QR IN, QUIT buttons
- Color-coded result columns (cycling themes)
- Highlighting missing bouts when 25% or less results are missing

### Merged (Rankings Combination)
- Combine results from multiple pools
- Split table view (left/right rankings)
- Buttons: RELOAD round, REPLACE, ADD, RESTORE crash, QR OUT, QR ADD, SAVE
- Editable fields with automatic re-ranking
- Grouping merge routine:
  - exact same participant sets are merged into one group
  - fully disjoint participant sets stay separated
  - if any participant overlaps between non-identical sets, all groups are merged together
- Import ADD with name-overlap handling:
  - Append results to already imported participants (merge by name)
  - Add as distinct mixed group (sets `P=0` to disable ambiguous KO-Mixed grouping)
- Includes `#` column (total matches) for robust aggregation across mixed participant overlaps

### KO (Knockout Phase)
- Visual bracket display with boxes for each match
- **8 KO modes** via pulldown menu:
  - **KO** — Standard single-elimination
  - **KO with Repechage** — Double elimination for losers
  - **Quick KO 1:2** — #1 vs #2, #3 vs #4, etc.
  - **Quick KO 1-4** — Groups of 4 by FinalPos ranking
  - **Quick KO 1-8** — Groups of 8 by FinalPos ranking
  - **Mix-Rounds 1:1 2:2** — Groups by P value (one P per group)
  - **Mix-Rounds 1-2:1-2** — Groups by paired P values
  - **Mix-Rounds 1-4:1-4** — Groups of 4 P values
- Automatic participant seeding from Merged rankings
- Compact bracket layout with horizontal overlap when possible
- Buttons: RELOAD, REPLACE, RESTORE CRASH, QR OUT, QR IN, SAVE
- Support for 8, 16, 32, 64 participant brackets (auto-padded with Empty)
- Color themes synced with Round page
- Repechage reference handling keeps winner/loser names aligned even when some matches are still unplayed (strict winner-ref matching, unresolved refs remain placeholders)

### Final (Results)
- 3-column final rankings display
- Filters empty/placeholder entries
- SAVE button for CSV export
- Long-press to return to Round page

## Project Structure

```
Fencing_scores/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/fencing/scores/
│       │   ├── MainActivity.java
│       │   ├── ScoresViewModel.java
│       │   └── ui/
│       │       ├── RoundFragment.java
│       │       ├── MergedFragment.java
│       │       ├── KOFragment.java
│       │       └── FinalFragment.java
│       ├── res/
│       │   ├── drawable/
│       │   ├── layout/
│       │   ├── mipmap-*/
│       │   └── values/
│       └── assets/
│           ├── help.txt
│           ├── BoutOrder.txt
│           └── ko_*.json
├── KO/
│   └── ko_*.json, ko_h*.json
├── txt/
│   ├── help.txt
│   └── BoutOrder.txt
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle
```

## Requirements

- Android SDK (API 30+, target API 34)
- Java 21+
- Gradle 9.2+

## Building the Project

### Prerequisites

1. **Install Android Studio** or Android SDK
2. **Set SDK location** in `local.properties`:
   ```
   sdk.dir=C:\\path\\to\\your\\Android\\Sdk
   ```

### Build Commands

```bash
# Debug APK (for development)
./gradlew assembleDebug

# Release APK (for distribution)
./gradlew assembleRelease
```

The generated APKs will be located at:
- Debug: `app/build/outputs/apk/debug/Fence-debug.apk`
- Release (signed): `Fence.apk` (project root — ready to install)

## Navigation

- **Swipe left/right**: Navigate between pages in order Round → Merged → KO → Final → Round
- **Long-press** on Final page: Jump directly to Round page

## Button Colors

- **Green (#388E3C)**: RELOAD buttons
- **Blue (#1565C0)**: Standard action buttons
- **Red (#D32F2F)**: SAVE and QUIT buttons

## Data Persistence

- **Auto-backup**: Each page saves to `*_backup.csv` after changes
- **Crash recovery**: RESTORE buttons load from backup files
- **Clean quit**: QUIT removes all backup files so next startup begins empty
- **CSV export**: SAVE buttons allow user-selected file location
- **QR transfer**: Data can be shared between devices via QR codes

## Multi-Round Header

- Round Name header now shows `Round Nr: X`
- Click and long-press both open the `Rounds Nr. in the pool` selector

## Merged Sorting

- Long-press `Name` cells toggles name order `A-Z` / `Z-A`
- Long-press `P` cells toggles P order increasing / decreasing
- Click or long-press `Grp` header toggles Grp order `A-Z` / `Z-A`
- Long-press `Grp` value cells toggles Grp order `A-Z` / `Z-A`
- Long-press `FinalPos` cells toggles FinalPos order increasing / decreasing

## Ranking System

Participants are ranked by:
1. **Win percentage** (descending)
2. **Index** (descending) - given minus received
3. **Given points** (descending)
4. **Equal position** - if all criteria tied

## Technical Details

- **KO Box Height**: 32dp per match box
- **Button Corners**: 8dp rounded corners
- **Full screen**: No action bar

## License

This project is licensed under the GNU General Public License V3.0.

## Icon

The app icon features a fencer with an epee on a dark blue (#001582) background, with adaptive icon support for round and shaped launchers.

## Version

Current release: **V2.22** — See [CHANGELOG.md](CHANGELOG.md) for full history.
