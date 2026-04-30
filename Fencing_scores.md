# Fencing Tournament Scores App - Technical Specifications

## Overview
Android app for managing complete fencing tournaments: pool rounds, merged rankings, knockout brackets, and final results.

**License:** GNU General Public License V3.0  
**Version:** 1.6  
**Target:** Android 11+ (API 30+, target API 34)  
**App Name:** "Fence!"  
**APK Filename:** `Fence.apk` (project root — ready to install)  
**Icon:** Fencer with epee on dark blue (#001582) background, adaptive icon support for round/shaped launchers

## App Structure

### Pages (ViewPager2 Navigation)
1. **Round** - Pool scoring matrix (landscape)
2. **Merged** - Combined rankings from pools (portrait)
3. **KO** - Knockout bracket (portrait)
4. **Final** - Final tournament rankings (portrait)

Navigation: Swipe left/right to cycle through pages. Long-press on Final returns to Round.

---

## Page 1: Round (Pool Phase)

### Configuration
- **Participants:** 10 default (adjustable 5-18)
- **Maximum Score:** 16 touches per bout
- **Orientation:** Landscape, full screen
- **Display:** ~98% screen height usage, dynamically sized cells

### Visual Design
- **Layout:** Excel-like matrix, white background, black borders on all cells (including Name column and headers)
- **Text:** Blue for regular, bold/italic for headers
- **Result columns:** Cyan background (cycling color themes available)
- **P column:** Green background
- **Buttons:** Rounded corners (8dp), blue (#1976D2) or red (#D32F2F) for QUIT

### Matrix Structure

#### Header Row
| Column | Description | Background |
|--------|-------------|------------|
| Nr | Participant numbers (1 to N) | #F5F5F5 |
| Name | Participant names (editable) | White |
| 1-N | Bout result columns | #F5F5F5 header |
| V | Victories | Cyan (cycling) |
| → | Given points | Cyan (cycling) |
| ← | Received points | Cyan (cycling) |
| I | Index (given - received) | Cyan (cycling) |
| % | Win percentage | Cyan (cycling) |
| P | Position/Rank | Green (cycling) |

#### Data Rows
- **Nr Column:** Participant numbers (bold/italic)
- **Name Column:** Editable names (bold/italic), auto-saved to backup on edit
- **Bout Cells:** Winner in blue, loser in red
- **Diagonal:** Black background (self vs self)

### User Interactions

#### Participant Management
- **Add:** Click Nr cell at last position
- **Remove:** Click Nr cell at second-to-last position
- **Edit Name:** Click name field → popup with auto-capitalized text
- **Clear Name:** Enter empty string → clears bouts for that participant

#### Bout Score Entry
1. Click any bout cell intersection
2. First popup: "ParticipantA Vs ParticipantB" with score grid (0-16)
3. Second popup: Reversed participant order
4. Results: Higher score = blue (winner), lower = red (loser)
5. Reset button clears both scores

#### Bout Order Display
- Click Nr cells 1 to N-2 → Shows next 6 suggested bouts
- Order loaded from BoutOrder.txt in assets
- Only shows for 4-12 participants with names

#### Help Dialog (Click P column)
- **Buttons (3×2 grid with rounded corners):**
  - LOAD: Open file picker for CSV
  - RESTORE: Load from backup
  - SAVE: Export as `BoutRounds_YYYYMMDD_hh.mm.ss.csv`
  - QR OUT: Display QR code (with screenshot of matrix as background)
  - QR IN: Scan QR code to import
  - QUIT: Exit app (red, bold) — clears all backup files for a clean restart
- **Content:** Help text from assets/help.txt

#### Header Row Click Actions
- **Nr, Name, 1-N, V, →, ←, I, % headers:** Click generates and shows a fullscreen QR code of the round data (with table screenshot as background)
- **V, →, ←, I, % headers:** Click also cycles color theme
- **P header:** Long-press sorts participants by ranking

### Calculations
All calculated only for participants with non-empty names:
- **V:** Count of bouts won
- **→:** Sum of all scores achieved
- **←:** Sum of all scores received
- **I:** Given - Received
- **%:** (Wins / Total bouts) × 100
- **P:** Position (1 = best)

### Ranking Priority
1. Win percentage (descending)
2. Index (descending)
3. Given points (descending)
4. Equal position for identical records

---

## Page 2: Merged (Rankings Combination)

### Purpose
Combine rankings from multiple pool rounds into unified seeding for KO phase.

### Layout
- **Orientation:** Portrait, full screen
- **Structure:** Split view with left and right tables
- **Background:** #D0D0D0

### Buttons (Horizontal row, rounded corners, close spacing with 4dp gaps, widths +20%)
| Button | Color | Function |
|--------|-------|----------|
| RELOAD round | Green #388E3C | Load data from Round page |
| REPLACE | Blue #1565C0 | Replace with CSV file |
| ADD | Blue #1565C0 | Append CSV data |
| RESTORE crash | Blue #1565C0 | Load from Merged_backup.csv |
| QR OUT | Blue #1565C0 | Display QR code (with vertical "MergedRanking" label) |
| QR ADD | Blue #1565C0 | Scan QR to add data |
| SAVE | Red #D32F2F | Export as `MergedRanking_YYYYMMDD_hh.mm.ss.csv` |

### Table Columns
- Nr, Name, V (victories), → (given), ← (received), I (index), % (win percentage), Pos (original position), P (editable), Final (calculated FinalPos)

### Features
- All fields editable (including P column via numeric input popup)
- FinalPos is the authoritative ranking used by KO, not recalculated from P
- P values preserved during CSV import (not overwritten by FinalPos)
- Automatic re-ranking when data changes
- Click last name to add participant, second-to-last to remove
- Empty name removes participant from list

### Auto-backup
Saves to Merged_backup.csv after each change.

---

## Page 3: KO (Knockout Phase)

### Purpose
Knockout bracket with multiple modes: standard, repechage, quick groupings, and mixed-round groupings.

### Layout
- **Orientation:** Portrait, full screen
- **Background:** White
- **Match boxes:** 32dp height, rounded corners, border
- **Scrollable:** Both horizontal and vertical (page swipe only at content edges)
- **Color themes:** Synced with Round page, updated in real time

### Top Controls (close spacing, 4dp gaps)
| Control | Type | Function |
|---------|------|----------|
| Mode selector | Pulldown menu (black background, white bold text) | Select KO mode |
| RELOAD | Button (Green) | Load participants from Merged (seeded per selected mode) |
| REPLACE | Button (Blue) | Replace with CSV file |
| RESTORE CRASH | Button (Blue) | Load from KO_backup.csv |
| QR OUT | Button (Blue) | Display QR code (with mode name as vertical label) |
| QR IN | Button (Blue) | Scan QR to import |
| SAVE | Button (Red) | Export as `KO_results_YYYYMMDD_hh.mm.ss.csv` |

### KO Modes (Pulldown Menu)

#### KO (Standard)
- Single-elimination bracket
- Supports 8, 16, 32, 64 participants
- Bracket definitions in assets: ko_8.json through ko_64.json
- Compact layout: later rounds overlap horizontally when boxes don't vertically collide

#### KO with Repechage
- Double elimination: losers enter repechage bracket
- Winners of repechage compete for 3rd place
- Main bracket winner = 1st, runner-up = 2nd, repechage winner(s) = 3rd+
- Bracket definitions: ko_h8.json through ko_h64.json

#### Quick KO 1:2 3:4 5:6...
- Pairs participants by FinalPos: #1 vs #2, #3 vs #4, etc.
- Each pair is a single match to determine relative position

#### Quick KO 1-4 5-8 9-12...
- Groups of 4 participants by FinalPos, each as a small KO bracket
- Within each group: best vs worst, second vs third (by FinalPos)
- Padded with "Empty" entries to next power-of-2 if needed

#### Quick KO 1-8 9-16 17-24...
- Groups of 8 participants by FinalPos, each as a small KO bracket
- Seeding within group follows standard KO bracket rules
- Padded with "Empty" entries to next power-of-2 if needed

#### Mix-Rounds 1:1 2:2 3:3
- Groups participants by P value from Merged (not FinalPos)
- All P=1 meet together, all P=2 meet together, remaining form one group
- Within each group, matches organized by FinalPos ranking
- Disabled if only one participant has P=1; participants with P=0 excluded

#### Mix-Rounds 1-2:1-2 3-4:3-4
- P=1 and P=2 together in one group, P=3 and P=4 together, remaining together
- Within each group, matches organized by FinalPos ranking

#### Mix-Rounds 1-4:1-4 5-8:5-8
- P=1 through P=4 together, P=5 through P=8 together, remaining together
- Within each group, matches organized by FinalPos ranking

### Multi-Group Layout (Quick KO and Mix-Rounds)
- Groups displayed in a grid: side-by-side and wrapped by screen width
- Each group has a title (e.g., "Quick 1:2", "Mix 1-4:1-4")
- Compact bracket positioning with horizontal overlap when possible
- Empty participants auto-advance (lose 15:0), removed from Final ranking
- Final ranking constrained within each group (no cross-group position mixing)

### Match Display & Score Entry
- Position numbers shown in front of participant names in all modes
- Winner in blue, loser in red, unplayed shows "x:x"
- Click match box → two sequential score input dialogs (one per participant)
- Scores from 0-15, higher score wins and advances
- Results auto-propagate through bracket

### Data Format
- META line includes koModus field
- Group trees use "G1", "G2", etc. identifiers
- Import (CSV/QR) restores KO mode and spinner selection

### Auto-backup
Saves to KO_backup.csv after each change, including mode and all group data.

---

## Page 4: Final (Results)

### Purpose
Display consolidated final tournament rankings.

### Layout
- **Orientation:** Portrait, full screen
- **Background:** #D0D0D0
- **Structure:** 3-column layout for rankings

### Header
- Title: "Final Rankings" (24sp, bold, shadow)
- SAVE button (Red #D32F2F, rounded corners)

### Rankings Display
- Split into 3 columns for better readability
- Format: "Position. Name"
- Filters out:
  - Empty names
  - Placeholder names (W_*, L_*, MW, LW, ML, GFL, LBW)
  - Whitespace-only names

### Navigation
- Long-press anywhere → Return to Round page (page 0)

### SAVE Function
- Opens file picker for CSV location
- Exports final rankings with positions

---

## Technical Details

### Data Flow
```
Round (pool scores)
    ↓ RELOAD
Merged (combined rankings)
    ↓ RELOAD  
KO (knockout bracket)
    ↓ FINAL button / auto-calculate
Final (results display)
```

### File Formats

#### CSV Structure (Round/Merged)
```
Nr,Name,1,2,3,...,V,→,←,I,%,P
1,Alice,,-,5,...,3,45,30,15,75,1
```

#### CSV Structure (KO)
```
#META,koSize,repechage,koModus
Tree,Round,Match,Participant1,Score1,Participant2,Score2,Winner
R1,1,1,Alice,15,Bob,10,Alice
G1,1,1,Alice,15,Bob,10,Alice
```

### Assets Required
- help.txt - Help menu content
- BoutOrder.txt - Suggested bout orders for pools of 4-14
- ko_8.json, ko_16.json, ko_32.json, ko_64.json, ko_128.json - Standard brackets
- ko_h8.json, ko_h16.json, ko_h32.json, ko_h64.json, ko_h128.json - Repechage brackets

### Backup Files (in app filesDir)
- Fencing_backup.csv - Round auto-backup
- Merged_backup.csv - Merged auto-backup
- KO_backup.csv - KO auto-backup

### QR Code
- Uses ZXing library for generation/scanning
- Data compressed for larger datasets
- Fullscreen display for easy scanning
- Context label displayed vertically alongside QR code (page-specific)

### Color Themes (Result Columns)
Cycling colors when clicking result columns (except P). Synced across Round and KO pages.
1. #00FFFF / #39E75F (cyan/green)
2. #87CEFA / #B0C4DE (light blue/gray)
3. #FFFFE0 / #F0E68C (light yellow/khaki)
4. #98FB98 / #9ACD32 (pale green/yellow-green)
5. #FFD700 / #FF7F50 (gold/coral) - Default
6. #A9A9A9 / #DCDCDC (dark gray/light gray)

### Button Styling
- Rounded corners: 8dp radius using GradientDrawable
- Green (#388E3C): RELOAD buttons
- Blue (#1565C0): Standard actions
- Red (#D32F2F): SAVE and QUIT buttons
- White text on all buttons
- Close spacing (4dp gaps) between buttons

### Screen Specifications
- Full screen (no action bar)
- Dynamic cell/font sizing based on content
- Round page: Landscape only
- Forced light mode (no dark mode support)

---

## Implementation Notes

### ViewModel (ScoresViewModel)
Shared data across all fragments:
- Participant names and scores
- Merged rankings
- KO results
- Final rankings
- Color cycle index (synced between pages via LiveData)

### Fragment Communication
- ViewPager2 with FragmentStateAdapter
- ViewModel for data sharing
- LiveData for reactive updates

### Crash Recovery
- Auto-backup after each data change
- RESTORE buttons load from backup
- Crash detection flag for recovery prompt
- QUIT clears all backup files

### Build Configuration
- compileSdk: 34
- minSdk: 30
- Java 21
- Gradle 9.2+
- R8/ProGuard with preserved line numbers for debugging