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


## Enhancements Version 1.2

### Rounds page
- The cells in Names column and in the headers should have a black border as the bout entry cells, in order to improve visibility
- The vertical size is not using the whole screen size, its calculation is probably unnecessarily complex. Please cesize the header and cells vertical sizes based on the actual screen size, so that the UI is using as close as possible to 100% of it, if necessary slightly less than 100% to avoid activating/needing to vertical-scroll
- In the bouts results popup the text "CANCEL" and "RESET" is a bit too wide. Decrease its text font size 25%
- The QR out is nice, but it lack context. Please use a screenshot of the rounds page (the matrix with names, bouts, calculations, ...) as background to the QR code, and center the QR code image on the bouts results matrix
- Behavior: when the app is launched for the first time, and Names are added, and then the app closed, the names are lost. Please add to the Names cells the same behavior as in the bouts results: when a name is added the Rounds values should be saved in the backup file.
- When Pressing the Save button in the help menu, or clicking the headers in the Rounds page, the default filename is Results.csv; please default the name to "BoutRounds_" followed by the date in the form YYYYMMDD_hh.mm.ss and then the extension ".csv"

### Merged page
- Disable the recalculation of the P value in the Merged page. The FinalPos is the ranking order which has value in the merged table, and it is already calculated correctly. Check that this value is used as ranking in the KO rounds. Disable the editing of P cells values in the cells: short clicking them will have no action associated
- Disable the editing of FinalPos cells values in the cells: shortclicking them will have no action associated
- The text in the buttons on the top is too close to the button size. Increase the buttons widths 20%
- Make the button on the top of the screen close to each other, without touching
- The QR out is nice, but it lack context. Please add a vertical text on its left: "Merged ranking". Do not rescale the QR code: the text is vertical so that it does not overlap with the code
- When Pressing the Save button, the default filename is Ranking.csv; please default the name to "RankingRounds_" followed by the date in the form YYYYMMDD_hh.mm.ss and then the extension ".csv"

### KO page
- Make the button on the top of the screen close to each other, without touching
- The QR out is nice, but it lack context. Please add a vertical text on its left: "KO". Do not rescale the QR code: the text is vertical so that it does not overlap with the code
- When Pressing the Save button, the default filename is KO_results.csv; please default the name to "KO_results_" followed by the date in the form YYYYMMDD_hh.mm.ss and then the extension ".csv"
- Scrolling within the page works, but sometimes the scrolling to other pages (Merged or Final). Change the scrolling behavior, if possible, so that the previous/next pages are scrolled to only after we get to the edges of the KO page

### Conclusion
- compile the release, ask for my feedback on it, if it's ok upload the changes to github, updating the release to V1.2 and adding the changes to the Changelog (in a synthetic, not too detailed way)

## Enhancements version 1.5

### Icon
- The background of the icon can be undefined in some Android or homepage implementations (example: A16, with round icons: it shows a white background). If possible change the icon background to  (#001582)

### KO modus and interface
- There is now a checkbox to define the KO modus: simple or with Repechage
- Substitute the button with a pulldown menu, defaulting to KO with the following entries:
  - KO
  - KO with Repechage
  - Quick KO 1:2 3:4  5:6...  
  - Quick KO 1-4 5-8  9-12...
  - Quick KO 1-8 9-16 17-24...
  - Mix-Rounds 1:1 2:2 3:3     
  - Mix-Rounds 1-2:1-2  3-4:3-4
  - Mix-Rounds 1-4:1-4  5-8:5-8
- The first two entries in the pulldown are the already implemented KO rounds
- The Quick KO create smaller KO rounds from the general ranking (FinalPos) for a quicker KO execution, with the following rules:
  - #1 meets #2, #3 meets #4 (and so on) to decide their respective position. 
  - alternatively (pulldown entries 4 & 5, Quick KO) small KO rounds without repechage are done, composed of classified #1 to #4, #5 to #8 and so on, or #1 to #8, #9 to #16 and so on. Each small KO round organizes the matches as a 2 or 4 simple KO round, each according to the FinalPos of the participants (example: #1 Vs #4, #2 Vs #3 and so on)
- The Mix-Rounds entries combine participants coming from distinct Rounds, according to the P (not FinalPos) ranking in Merged page
  - All the P=1 meet to decide the first positions, then the P=2, and all the rest to define all other positions. For more than 2 people present in each small KO round, in each small Mix round the matches are organized according to the FinalPos ranking (for example if 4 people are present the best ranking one is meeting the worst one, the second best meets the third best. For the Mix rounds with 8 participants the individual Matchign rules are similar, with the same method defined for the KO rounds, eventually filled with EMPTY entries that are then removed when calculating the Final ranking) 
  - For the second Mix entry P=1 and P=2 all meet in a small KO round without Repechage, and the same happens to (P3 and P=4), and to the remaining participants.  For more than 2 people present in each small KO round, in each small Mix round the matches are organized according to the FinalPos ranking (for example if 4 people are present the best ranking one is meeting the worst one, the second best meets the third best. For the Mix rounds with 8 participants the individual Matchign rules are similar, with the same method defined for the KO rounds, eventually filled with EMPTY entries that are then removed when calculating the Final ranking)
  - When clicking on the pulldown menu, the P values in Merged are evaluated. Mix-Rounds entries are disabled if there is only one participant with value P=1, otherwise they are enabled. Participants with P=0 are not considered
- It's clear that for the Final ranking each Quick and Mix group will mix only the positions of the participants matching within themselves, without allowing their rank to go below or above the ranking of participants in other rounds of KO. When generating the Mix-Rounds care has to be taken to properly consider the position of participants, based on the P saved in Merged, and not on FinalPos as usual.
- EMPTY entry participants will be added as in KO and KO with Repechage to fill the groups, propagating the results as usual (Empty loses 15:0), but removing all Empty from the Final rank.
- Prepare a format to export the KO page (including the pulldown menu) to a QR code and to a csv file, including the backup file. You can use a format incompatible to previous version, if necessary. Importing the QR or the csv also sets the pulldown menu and the KO Modus correctly
- Give a title to each Quick and Mix Tree, like for example "Quick 1:2" or "Quick 1-4", or "Mix 1-4:1-4". Choose appropriately 
- The RELOAD button fills in the Participants according to the KO Modus chosen
- For the Quick and Mix pulldown selections, based also on the size of the rounds evaluated at runtime, please come up with meaningful KO Round trees layout that is clear to understand and also makes efficient use of the display. You can place distinct trees one-beside the other and/or one below the other. Do not give a title to them (as previously was "Round n")
- When generating the buttons at the top of the screen please take care that there's enough space in the KO page. At the moment the QR add appears small and empty, limited by the size of the device display, and the SAVE button is missing. This happens probably because the width of the pulldown mmenu button is at the default/start very wide, but only the "KO" text is printed. This is solved when another entry is selected, and KO is then selected again: in this case the KO button menu is resized and the other buttons are properly placed. Debug this
- At the moment the KO Round trees for the Quick entries are not displayed. Verify the reason before deleting and re-implementing the code
- The popup menu for entering the Quick and Mix results is not correct: there is the name of the first participant, then fields for the results, followed by the name of the second participant in the direct KO. Copy the schema of implementation from the simple KO round, with two popup menus appearing for the touches of the two participants.
- In the QR code, instead of the text "KO" print beside the QR code the same text as in the pulldown menu
- The icon of the installed apk has an image that is too big in case the icon is round (Some Homepage or Android 16 implementations). Make the image smaller, so that it is not cropped by a round icon
 
### Merged page
- P columns cells values are enabled again for editing, with the same text entry setup as, for example the % column