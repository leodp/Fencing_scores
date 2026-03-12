# Fencing Tournament Scores App - Technical Specifications

## Overview
Android app for managing complete fencing tournaments: pool rounds, merged rankings, knockout brackets, and final results.

**License:** GNU General Public License V3.0  
**Target:** Android 11+ (API 30+, target API 34)  
**App Name:** "Fence!"  
**APK Filename:** `Fence.apk` (project root — ready to install)

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
- **Display:** 95% height usage

### Visual Design
- **Layout:** Excel-like matrix, white background, black borders
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
- **Name Column:** Editable names (bold/italic)
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
  - SAVE: Export to user-selected location
  - QR OUT: Display QR code with data
  - QR IN: Scan QR code to import
  - QUIT: Exit app (red, bold)
- **Content:** Help text from assets/help.txt

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

### Buttons (Horizontal row, rounded corners)
| Button | Color | Function |
|--------|-------|----------|
| RELOAD round | Green #388E3C | Load data from Round page |
| REPLACE | Blue #1565C0 | Replace with CSV file |
| ADD | Blue #1565C0 | Append CSV data |
| RESTORE crash | Blue #1565C0 | Load from Merged_backup.csv |
| QR OUT | Blue #1565C0 | Display QR code |
| QR ADD | Blue #1565C0 | Scan QR to add data |
| SAVE | Red #D32F2F | Export to CSV |

### Table Columns
- Nr, Name, V (victories), → (given), ← (received), I (index), % (win percentage), Pos (original position), Final (calculated position)

### Features
- All fields editable
- Automatic re-ranking when data changes
- Click last name to add participant
- Click second-to-last to remove last participant
- Empty name removes participant from list

### Auto-backup
Saves to Merged_backup.csv after each change.

---

## Page 3: KO (Knockout Phase)

### Purpose
Visual knockout bracket with optional repechage (double elimination for losers).

### Layout
- **Orientation:** Portrait, full screen
- **Background:** White
- **Match boxes:** 32dp height, rounded corners
- **Scrollable:** Both horizontal and vertical

### Top Controls
| Control | Type | Function |
|---------|------|----------|
| Repechage | Checkbox | Toggle repechage mode |
| RELOAD | Button (Green) | Load participants from Merged |
| REPLACE | Button (Blue) | Replace with CSV file |
| RESTORE CRASH | Button (Blue) | Load from KO_backup.csv |
| QR OUT | Button (Blue) | Display QR code |
| QR IN | Button (Blue) | Scan QR to import |
| SAVE | Button (Red) | Export to CSV |

### Bracket Structure
- Supports 8, 16, 32, 64 participants
- Bracket definitions in assets: ko_8.json, ko_16.json, ko_32.json, ko_64.json
- Repechage brackets: ko_h8.json, ko_h16.json, ko_h32.json, ko_h64.json

### Match Box Display
```
[P1 Name]  [Score1]
[P2 Name]  [Score2]
```
- Winner highlighted in blue/green
- Loser in red/gray
- Click box to enter scores

### Repechage Mode
- Losers from main bracket enter repechage trees
- Winners of repechage compete for 3rd place
- Main bracket winner = 1st place
- Main bracket runner-up = 2nd place
- Repechage winner(s) determine 3rd+ positions

### Score Entry
- Click match box → score input dialog
- Scores from 0-15
- Higher score wins, advances to next round
- Results auto-propagate through bracket

### Auto-backup
Saves to KO_backup.csv after each change, including repechage state.

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
#META,koSize,repechage
Tree,Round,Match,Participant1,Score1,Participant2,Score2,Winner
R1,1,1,Alice,15,Bob,10,Alice
```

### Assets Required
- help.txt - Help menu content
- BoutOrder.txt - Suggested bout orders for pools of 4-14
- ko_8.json, ko_16.json, ko_32.json, ko_64.json - Standard brackets
- ko_h8.json, ko_h16.json, ko_h32.json, ko_h64.json - Repechage brackets

### Backup Files (in app filesDir)
- Fencing_backup.csv - Round auto-backup
- Merged_backup.csv - Merged auto-backup
- KO_backup.csv - KO auto-backup

### QR Code
- Uses ZXing library for generation/scanning
- Data compressed for larger datasets
- Fullscreen display for easy scanning

### Color Themes (Result Columns)
Cycling colors when clicking result columns (except P):
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

### Screen Specifications
- Full screen (no action bar)
- Dynamic cell/font sizing based on content
- Round page: Landscape only
- Other pages: Portrait only

---

## Implementation Notes

### ViewModel (ScoresViewModel)
Shared data across all fragments:
- Participant names and scores
- Merged rankings
- KO results
- Final rankings

### Fragment Communication
- ViewPager2 with FragmentStateAdapter
- ViewModel for data sharing
- LiveData for reactive updates

### Crash Recovery
- Auto-backup after each data change
- RESTORE buttons load from backup
- Crash detection flag for recovery prompt

### Build Configuration
- compileSdk: 34
- minSdk: 30
- Java 21
- Gradle 9.2+
