# Debug and extend

## Debug
- For debugging purposes I provide in this directory the file MergedKOdebug.csv, which needs to be used to solve these issues:
  - Load it for the Merged page
  - Go to KO and "RELOAD", give the ok. Keep the modus to the default of "KO". I found 3 bugs which are possibly related, in the way ranking are calculated and propagated, and in the effect of reset buttons:
    - Bug 1: Without entering data, go to the Final ranking page: it's empty. Correct behavior: show the ranking, even in absence of completed KO rounds, following the FinalPos in Merged. Consider if "RELOAD" button is correctly propagating the ranking to Final Ranking page. In [KO modus, KO with repechage, Quick modi] the ranking follows the MErged FinalPos;in Mix-Rounds modi the final ranking is first organized (as already for the KO page trees) in subset of 1:2, 3:4, ... or 1_4, 5-8, ... and so on  participants, each subset in the list better ranked than the following one; then within each subset the relative ranking is considered. The ranking is recalculated every time the "RELOAD" button is pressed, the KO pulldown menu choice is changed or a result in KO is entered
    - Bug 2: if I enter the result Ronny Vs Fred 2:15, that means Fred won against Ronny. Going to Final ranking page shows only Ronny at position 1. This is not correct, because all other participants are still missing, and, even if his b´rounds are not complete, Fred should be in a better placement than Ronny.
    - Bug 3; Gong back to KO page, after entering the results as given in the "Bug 2", Now I press Ronny Vs Fred in Round 1 and choose RESET. In the first entry in Round 1 I still see Leo Vs Fred: Fred should not be there, because the reset tells that Fred has not won, its name (and possible ranking changes) should be restored by considering the canceling of the entered result in Ronny Vs Fred. Also the Final Ranking page should be updated, by considering the resetting of the Ronny Vs Fred match
    - Bug mitigation: if I select the pulldown "KO with repechage" or the Quick entries  the Final Rankings are ok. Also when entering just a few rounds. Check how the ranking is calculated, and see what is missing in the "KO" pulldown choice. Probably Bug 1 and Bug 2 have a simple solution


## Extend
- In Rounds page, long pressing the Pos cells reorders the participants and data acvcording to the Pos. Extend the reordering method like this:
  - Long pressing the Pos cells now toggles the reordering between increasing and decreasing order
  - Long pressing the participants' names defines now the following action: togglesd the reordering of the participants and data in Alphabetical and reversed alphabetical order
- In the Rounds page, when entering a bout result, check that the remaining bouts not done yet is above 15% (the calculation should be done for the matches with defined participants only. IF a participants has a non defined/empty name its bouts are excluded from the calculation). Once it falls below do the following:
  - Change the background of the still missing bouts to #B0B0B0 from the previously defined white. Remember to set it back to white once the results for a specific bout is entered
  
### MultiRounds
- This extension is quite complex and may break the code. Consider it carefully before doing any change. Backup files you need to edit before making changes, with the extension "bak"
  - Long pressing the Name heder in Rounds opens up a popup menu similar to the one for the bouts, but with the following content:
    - Title: "Rounds Nr. in the pool"
    - Boxes for numbers: 1 to 5 (as in bout results). 1 has a green background
    - Below them a red box with the text "CANCEL" (wide enough to allow the full text with some extra space around it)
    - Pressing the cancel button closes the menu without further actions
    - Pressing a number box sets the ##NrRounds##. Default is 1
    - in V1.7 the program is built to have only ##NrRounds##=1. This is represented by the data in Page Rounds. We'll call this from now on Page ##Rounds1##
    - In case ##NrRounds## is changed, this means a different number of Pages Rounds is present: ##Rounds1##, ##Rounds2## and so on. If ##NrRounds## is increased, new Page Rounds are appended to the present one(s), before the Merged page. If it is decreased the required rounds Pages are removed, starting from the last one. 
    - Actions in each Rounds page are as defined now. Take care of adding an indicator to the action, when it should act only on the specific Rounds page and not in all of them.  Example: bout results are applied only in the active Rounds page. Example 2: colors may be changed independently in each rounds page. The colors in the following pages is as that on ##Rounds1##
    - This possibility allows for repeating matches between the same participants
    - The ranking in each Rounds page is based on the bouts results in that same page
    - The ranking in the Merged page, calculated when "RELOAD ROUND" is pressed, accounts for all the results entered in all the Rounds pages enabled: combined matches, winning matches, given touches and so on. The participants with the same name are considered as the same participant. This means that for each participant there may be multiple entries, but that they appear in one single line in the Merged page. Update accordingly the Merged page Ranking (P) and values
    - The Exported/saved files in each Rounds page now has in the name another ##Nrcode##, before the date, which is the number of the Rounds Page: 1 for ##Rounds1##, 2 for ##Rounds2## and so on
    - This ##Nrcode## is also used in the Names header: instead of "Name" use now ""NrCode"" followed by"R. Name:"
    - Some actions are synced among all Rounds pages: Reordering, adding and removing participants, changing their name is kept in sinc with all other non visible Rounds Pages
    - Swiping and moving by long press between pages is extended now in the new updated pages list: ##Rounds1##, ##Rounds2##, ... Merged, KO, Final, and back to ##Rounds1##
    - Add the description of the new feature in the help file and help menu which may be opened in the Rounds pages, and also where needed in the readme and changelog
    - Update the list of actions already defined in such files with the newly defined actions.
#### MRDebug
- The participants list in the multi rounds is not correctly sorted: the single ID for the participants is their Name. All the Rounds pages have to be sorted with the same Name sequence as the previously existing ones. If ##NrRounds## is increased, then the new created Rounds pages will have the names already defined, and the same participants Nr, as the previously existing Rounds Pages. If a sorting is performed, the same sorting has to be done in all the other Rounds pages. If results are loaded (File or QR code) and in other Results pages the Names are already defined, the same names sequence is used in the Results page where the data has been loaded. If some names are not matching, they are added also in the other pages, in the same sequence, after taking care of any necessary increase in Nr of participants. If one name is edited or deleted or added in one page, the same name is edited, deleted or added in all other Results Pages
- The colors in Merged and following pages are inherited from the first Rounds page, not the last one
- Update (also in the documentation) one style couple of colors from  ( #87CEFA #B0C4DE)  to ( #87DEFA #9084DE )

- When the rounds number is increased, the new Rounds pages will be created in position which FOLLOW those already existing
- The newly created rounds pages will have EMPTY bouts results, only the names will be defined, in the same ORDER as those already existing in the previous Rounds pages. The Name is the Unique ID of the participants, not their Nr. Do a test by performing these operations:
  - In ##Rounds1## open the file MergedKOdebug.csv
  - Reorder following the Pos Low to high ranking value
  - Long click Name and select 2 rounds
  - Go to ##Rounds2##. Check that the Name, at each Nr, is the same as in ##Rounds1##
  - Sort by Name Z-A
  - Go back to ##Rounds1##: the names sequence should be as in ##Rounds2##
  - Return to ##Rounds2##, and load the file MergedKOdebug.csv
  - If the loading changes the Names sequence, once finished the same ordering should be done in ALL other Rounds pages
  - At this point all data  in the matrix of ##Rounds1## and ##Rounds2## should match. Test it and verify that the QR code the two Pages export is identical in content.
#### MRDebug2
- When ##Rounds2## is created, its contents are not empty. Are the values taken from a previously written crash debug? When the Number of Rounds is increased, the crash data should be ignored, 'till the new pages have been populated
- When loading data in ##Rounds1##, and changing the order, if I then add ##Rounds2## by setting ##NrRounds##=2 there may be cases where the Names order is not in symc among all Rounds pages. Check again the implementation I suggested previously, in lines above.
- The saved Bout Rounds csv are missing (in the name, before the date) the number code of the rounds page, to differentiate between 1. and following Rounds. This ID number is used also in the backup files, which are updated in case any data is updated in any Rounds Page. Upon a crash, all the Rounds Pages backup files are reloaded from their backup file. To know what was the ##NrRounds##, and how many Rounds pages to create, the max ##NrRounds## number code in the backup files (before the date) has to be found. - Every time ##NrRounds## is changed, the backup files for the Round Pages 
- As usual, upon a clean exit, all backup files are removed

#### MRDebug3
- It seems ok, but when I go from ##NrRounds##=1 to 2, the ##Rounds2## page is created with data already in the bouts. This should be empty! Why does it happen? Check and solve. Check that no backup for the 2nd and following Rounds is present when creating ##Rounds2##. Check other possibilities.

#### MRDebug4
- One bug found: after correct Quit, when I restart the names and bouts results are entered automatically. Probably from a backup? This behavbior is not correct: when correctly closing ALL backup data is reset and files deleted
- Also: if I enter empty Names, the bouts results are reset. But if I enter again a valid Name, the previous bout results are reloaded. This should NOT happen
- Improvement: In the Name header, there is now th Number of the Round, then "R. Name". To suggest the behavior, change the header to: "Round Nr: " followed by the Round page number. Everything in bold, with black shadow. And change the simple press/touch action of this cell to the one now defined only for long press (define Rounds Nr.)

#### MRDebug5

How to reproduce symptoms: in the KO Page, load test files saved in the no/ directory. Use them for verifying and correcting the errors. Some examples:

KO_results_20260522_00.30.26.csv
Felipe wins, Feliipe 2nd, then without fighting, based on previous ranking Greta and Inti follow

KO_results_20260522_00.32.35.csv
Final: Inti Vs Cai is not fought, so, following from previous ranking Inti is 1., Cai 2.
Final 3:4 place Feliipe wins Vs Enzo, which was better placed before the KO
But Final Rankings: 1. Aiko 2. Greta 3. Biko 4. Devi --> all wrong

Bugs description:
1) When loading data in KO Page, the Final rankings are not updated
2) If I start from Merged, go to KO and press RELOAD, and enter all Matches as usual, and in the 3./4. pos. final the Participant with worst placement wins, the final ranking shows the wrong placement: the loser is 3., the winner is 4., as their relative placement was BEFORE doing the KO match
3) There may be similar bugs in other setups, check also "KO with repechage", where it's expected that KO matches swap positions from their previous situation

Remember, in general: 
- In a round the winners proceed, and get to better placements; the losers (starting from the first KO round) occupy the lowest part of the ranking, according to their previous relative ranking. Exceptions:
  - In case of Repechage also the losers relative position is reassessed, by considering the repechage matches results (iteratively in case of more rounds of repechage)
  In case of Quick and Mix modi the ranbking is first split into the defined groups (Quick 1:2, then Quick 3:4 vand so on), then within each group the relative positions are assigned based on direct matches results, or, in case of missing results based on the relative FinalPos previous data
  
IMPORTANT: the KO Page correctly sets the results of winners and losers (just for consistency: check the KO with Repechage algorithm): lets winners proceed in the trees and stops losers (or puts them in repechage trees). So the problem in the calculation of final ranking on the Final Ranking Page is restricted to this last step. If you ghave some idea that the KO page needs a change in the ranking algorithm write me what you're doing and ask for my permission to proceed: I see a high risk of breaking good code.

Another change: in Pages you are using buttons with rounded corners: 8dp radius using GradientDrawable
Change the buttons rounding (not size) also in all other popup menus in all Pages for consistency 

Errata: the icon of the app is now saved in a directory named "îcon_img". The accent on the first character is a typo. Change thedirectory name to "icon_img", change also all references to the previous name in the code

### MRDebug6
- In some files there's still the mention of 98% screen usage for Rounds. This was changed to 100%. Fix not code-affecting references 
- In KO Page the KO pulldown menu has still a geometry which is not as the other buttons, probasbly because it's a pulldown menu. Make its HEIGHT as the other buttons. Make it rounded as the other buttons. Make visible, in white a downward pointing arrow or triangle, after the text, to suggest that this is a pulldown menu and not a passive text

IMPORTANT: check and fix
Final Ranking is still not working in this case: No Merged data is present->I go to KO Page-> I load any KO CSV file with REPLACE, as for example those saved in the no/ directory (check them)-> entering ANY result in the KO tree matches does NOT update the rankings. Check how is REPLACE data entering the results flo


## Tasks
- Implement the above points, all the changes since last execution, test them, update the release version to Version 2, updating and preparing the files and changelog for Github upload, compile the release apk and move it to the root directoy as usual. Then push to GitHub


## ExtendParallel

I would like to implement a more complex merging of bouts results procedure.
The MergedRanking windowis at the moment able to combine: 
- Rounds with the same participants, summing up the results
- Rounds with non-matching participants, importing ranking data and preserving the initial Pos, for being able to make the so-named KO with mixed rounds.

There are some unresolved situations. Let'S describe the possible situations.
AB, CDE, BD, ACE, AC and so on are groups with distinct participants. The capital letter hints at the first letter of the participants' names. I use groups of 2 or 3 for simplicity. Whatto extract: there may be participants mixing in successive rounds, not all groups have the same Nr of participants.
In the following lines, there are sections separated by multiple "-", preceded by an ordering number. Each of this section represents a competition Rounds. Each line in the section represents data acquired by a different device. Entries in each line, separated by "," indicate distinct rounds in a tournament. At the moment we define a Round Nr (defailt:1, max:5) in the app. The participants are assumed to be the same, and are reordered consistently in each Rouunds page.
This will change now: reordering of Participants is applied only to the active Rounds window and does not propagate to the other windows. Participants names is not propagated automatically to all Rounds windows.
Now the matches sections:
1-----------------------------------------------------
AB
(simple match, nothing new)
2-----------------------------------------------------
AB,CDE
(two distinct groups in one device, Pos and results are imported as they are in the Merged page, KO Mixed possible)
3-----------------------------------------------------
AB 
CDE
(two distinct groups in two devices. The results of the 2nd are imported as usual. Pos is kept for KO Mixed)
4-----------------------------------------------------
AB,AB
(Double round, same participants. Correct impolementation now. No Ko Mixed possible)
5-----------------------------------------------------
AB
AB
(Double round, same participants, in 2 devices. Results of 2nd devices imported via CSV or QR. Correct implementation now. No Ko Mixed possible)
6-----------------------------------------------------
AB,CDE
AB,CDE
(Double round, with 2 groups having distinct participants, in 2 devices. Results of 2nd device imported via CSV or QR. Not clear how importing of the results for the 2 groups works now. KO Mixed should be possible)
7-----------------------------------------------------
AB,AB
CDE,CDE
(Double round, with 2 groups having distinct participants, in 2 devices. Results of 2nd device imported via CSV or QR. Not clear how importing of the results for the 2nd group's 2 distinct entries works now. KO Mixed should be possible)
8-----------------------------------------------------
AB,CDE
AC,BDE
(Mixing groups participants multiple rounds. Care has to be taken when merging data: as participants' Nr is probably different in each group, the combined "%" cannot be simply averaged from the various "%" values, but has to be calculated as 100*[total won]/[total disputed]. KO Mixed not possible, as there are no clear separated groups.)
9d-----------------------------------------------------
AB,ACE
CDE,BD
(Similar to previous situation, but now names in the second round are partially the same as those in the first round, in each device. This may be a flag when merging. Similar attention for the "%" calculation and no KO Mixed possible)
-----------------------------------------------------

So clearly:
- We may mix groups with or without the same or a subset of the same participants
- We may have to change the way we calculated the Merged Ranking and the entries in the Merged page. You may wish to add a column for Total matches performed, maybe before the column "V", with the key indicator "#". Exported SVN, QR code, imported data and matrices may have to be updated accordingly.
- In situation like at section 7, when we import data from the second device, we may have to decide how to merge it to already present data. A popup may prompt the user for this decision, in case at least some of the participants names' are already present in the active Merged page. Possible choices are:
	"Append results to an already imported group"
	"Add results of a distinct (mixed?) group"
Every time the second entry is chosen, the KO-Mixed is automatically disabled, as there's no clear way of defining Pos when groups participants mix
- In all cases when groups mix (detected by identifying the Participants with their Names) no KO-Mixed is possible, and Pos does not make sense. We may wish to edit the Pos value after updating the calculations and setting it to a flag value of 0 for all participants in Merged Page

This scheme works, of course, if more than 2 rounds are performed, or if results from more than 2 devices are merged in the Merged Page.

Check the above instruction, plan the changes, write down the operating plan before implementing it
