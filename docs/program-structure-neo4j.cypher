// Fencing Scores architecture graph (baseline)
// Usage in Neo4j Browser:
//   :play cypher
//   :source docs/program-structure-neo4j.cypher
// or paste into Browser and run.

// Optional cleanup for reruns
MATCH (n:FencingScores) DETACH DELETE n;

// Root
CREATE (app:FencingScores:Project {
  name: 'Fencing Scores',
  package: 'com.fencing.scores',
  versionName: '2.1',
  versionCode: 11,
  minSdk: 30,
  targetSdk: 34,
  compileSdk: 34,
  language: 'Java',
  build: 'Gradle/AGP'
});

// Modules
CREATE (modApp:FencingScores:Module {name:'app'}),
       (modRootBuild:FencingScores:Module {name:'root-build'}),
       (modSettings:FencingScores:Module {name:'settings'}),
       (modAssets:FencingScores:Module {name:'assets'}),
       (modKOJson:FencingScores:Module {name:'ko-json'});

CREATE (app)-[:HAS_MODULE]->(modApp),
       (app)-[:HAS_MODULE]->(modRootBuild),
       (app)-[:HAS_MODULE]->(modSettings),
       (app)-[:HAS_MODULE]->(modAssets),
       (app)-[:HAS_MODULE]->(modKOJson);

// Build/dependency nodes
CREATE (depAppCompat:FencingScores:Dependency {name:'androidx.appcompat:appcompat', version:'1.6.1'}),
       (depMaterial:FencingScores:Dependency {name:'com.google.android.material:material', version:'1.10.0'}),
       (depConstraint:FencingScores:Dependency {name:'androidx.constraintlayout:constraintlayout', version:'2.1.4'}),
       (depZXingCore:FencingScores:Dependency {name:'com.google.zxing:core', version:'3.5.2'}),
       (depZXingAndroid:FencingScores:Dependency {name:'com.journeyapps:zxing-android-embedded', version:'4.3.0'}),
       (depAgp:FencingScores:Dependency {name:'com.android.tools.build:gradle', version:'9.0.1'});

CREATE (modApp)-[:DEPENDS_ON]->(depAppCompat),
       (modApp)-[:DEPENDS_ON]->(depMaterial),
       (modApp)-[:DEPENDS_ON]->(depConstraint),
       (modApp)-[:DEPENDS_ON]->(depZXingCore),
       (modApp)-[:DEPENDS_ON]->(depZXingAndroid),
       (modRootBuild)-[:DEPENDS_ON]->(depAgp);

// Runtime components
CREATE (mainActivity:FencingScores:Class {name:'MainActivity', path:'app/src/main/java/com/fencing/scores/MainActivity.java', type:'Activity'}),
       (mergedActivity:FencingScores:Class {name:'MergedActivity', path:'app/src/main/java/com/fencing/scores/MergedActivity.java', type:'Activity'}),
       (viewModel:FencingScores:Class {name:'ScoresViewModel', path:'app/src/main/java/com/fencing/scores/ScoresViewModel.java', type:'ViewModel'}),
       (pager:FencingScores:Class {name:'MainPagerAdapter', path:'app/src/main/java/com/fencing/scores/ui/MainPagerAdapter.java', type:'Adapter'}),
       (roundFrag:FencingScores:Class {name:'RoundFragment', path:'app/src/main/java/com/fencing/scores/ui/RoundFragment.java', type:'Fragment'}),
       (mergedFrag:FencingScores:Class {name:'MergedFragment', path:'app/src/main/java/com/fencing/scores/ui/MergedFragment.java', type:'Fragment'}),
       (koFrag:FencingScores:Class {name:'KOFragment', path:'app/src/main/java/com/fencing/scores/ui/KOFragment.java', type:'Fragment'}),
       (finalFrag:FencingScores:Class {name:'FinalFragment', path:'app/src/main/java/com/fencing/scores/ui/FinalFragment.java', type:'Fragment'});

CREATE (modApp)-[:CONTAINS]->(mainActivity),
       (modApp)-[:CONTAINS]->(mergedActivity),
       (modApp)-[:CONTAINS]->(viewModel),
       (modApp)-[:CONTAINS]->(pager),
       (modApp)-[:CONTAINS]->(roundFrag),
       (modApp)-[:CONTAINS]->(mergedFrag),
       (modApp)-[:CONTAINS]->(koFrag),
       (modApp)-[:CONTAINS]->(finalFrag);

// Wiring
CREATE (mainActivity)-[:USES]->(pager),
       (mainActivity)-[:USES]->(viewModel),
       (pager)-[:CREATES {order:'1..N'}]->(roundFrag),
       (pager)-[:CREATES {order:'N+1'}]->(mergedFrag),
       (pager)-[:CREATES {order:'N+2'}]->(koFrag),
       (pager)-[:CREATES {order:'N+3'}]->(finalFrag);

CREATE (roundFrag)-[:USES]->(viewModel),
       (mergedFrag)-[:USES]->(viewModel),
       (koFrag)-[:USES]->(viewModel),
       (finalFrag)-[:USES]->(viewModel);

// Page-level data flow
CREATE (roundFrag)-[:PRODUCES {data:'round matrices, participant names'}]->(mergedFrag),
       (mergedFrag)-[:PRODUCES {data:'final seeding/ranking rows'}]->(koFrag),
       (koFrag)-[:PRODUCES {data:'final KO rankings'}]->(finalFrag),
       (finalFrag)-[:REQUESTS_UPDATE {signal:'requestKORankings=true'}]->(koFrag);

// Dynamic rounds behavior
CREATE (nrRounds:FencingScores:State {name:'nrRounds', range:'1..5'}),
       (activeRound:FencingScores:State {name:'activeRoundCode'}),
       (roundData:FencingScores:State {name:'roundBoutResults', shape:'Map<Integer,int[][]>'}),
       (roundNames:FencingScores:State {name:'roundParticipantNames', shape:'Map<Integer,String[]>'}),
       (roundColors:FencingScores:State {name:'roundColorCycleIndex', shape:'Map<Integer,Integer>'}),
       (finalRanks:FencingScores:State {name:'finalKORankings', shape:'LiveData<List<String>>'});

CREATE (viewModel)-[:OWNS_STATE]->(nrRounds),
       (viewModel)-[:OWNS_STATE]->(activeRound),
       (viewModel)-[:OWNS_STATE]->(roundData),
       (viewModel)-[:OWNS_STATE]->(roundNames),
       (viewModel)-[:OWNS_STATE]->(roundColors),
       (viewModel)-[:OWNS_STATE]->(finalRanks),
       (mainActivity)-[:OBSERVES]->(nrRounds),
       (mainActivity)-[:REBINDS_ADAPTER_ON_CHANGE]->(nrRounds);

// KO internals as sub-structures
CREATE (koMode:FencingScores:State {name:'koModus', values:'0..7'}),
       (koMatch:FencingScores:Structure {name:'Match'}),
       (koTree:FencingScores:Structure {name:'RepechageTree'}),
       (koGroup:FencingScores:Structure {name:'KOGroup'});

CREATE (koFrag)-[:OWNS_STATE]->(koMode),
       (koFrag)-[:USES_STRUCTURE]->(koMatch),
       (koFrag)-[:USES_STRUCTURE]->(koTree),
       (koFrag)-[:USES_STRUCTURE]->(koGroup);

// Storage and backup
CREATE (storeInternal:FencingScores:Storage {name:'Context filesDir'}),
       (backupRound:FencingScores:File {name:'Fencing_backup_R{N}.csv', note:'one per round, N=1..nrRounds; replaceMode restore bypasses LiveData merge'}),
       (backupMerged:FencingScores:File {name:'Merged_backup.csv'}),
       (backupKO:FencingScores:File {name:'KO_backup.csv'}),
       (crashFile:FencingScores:File {name:'CRASH.txt'}),
       (configFile:FencingScores:File {name:'fencing_config.json'});

CREATE (app)-[:USES_STORAGE]->(storeInternal),
       (storeInternal)-[:CONTAINS]->(backupRound),
       (storeInternal)-[:CONTAINS]->(backupMerged),
       (storeInternal)-[:CONTAINS]->(backupKO),
       (storeInternal)-[:CONTAINS]->(configFile),
       (mainActivity)-[:CHECKS_FOR]->(crashFile),
       (roundFrag)-[:READS_WRITES]->(backupRound),
       (mergedFrag)-[:READS_WRITES]->(backupMerged),
       (koFrag)-[:READS_WRITES]->(backupKO);

// Layout and resources
CREATE (layoutMain:FencingScores:Resource {name:'activity_main.xml'}),
       (layoutRound:FencingScores:Resource {name:'fragment_round.xml'}),
       (layoutMerged:FencingScores:Resource {name:'fragment_merged.xml'}),
       (layoutKO:FencingScores:Resource {name:'fragment_ko.xml'}),
       (layoutFinal:FencingScores:Resource {name:'fragment_final.xml'}),
       (layoutMatrix:FencingScores:Resource {name:'matrix_table.xml'}),
       (manifest:FencingScores:Resource {name:'AndroidManifest.xml'}),
       (txtHelp:FencingScores:Resource {name:'txt/help.txt'}),
       (txtBouts:FencingScores:Resource {name:'txt/BoutOrder.txt'}),
       (koJson:FencingScores:Resource {name:'KO/ko_*.json + KO/ko_h*.json'});

CREATE (modApp)-[:HAS_RESOURCE]->(layoutMain),
       (modApp)-[:HAS_RESOURCE]->(layoutRound),
       (modApp)-[:HAS_RESOURCE]->(layoutMerged),
       (modApp)-[:HAS_RESOURCE]->(layoutKO),
       (modApp)-[:HAS_RESOURCE]->(layoutFinal),
       (modApp)-[:HAS_RESOURCE]->(layoutMatrix),
       (modApp)-[:HAS_RESOURCE]->(manifest),
       (modAssets)-[:HAS_RESOURCE]->(txtHelp),
       (modAssets)-[:HAS_RESOURCE]->(txtBouts),
       (modKOJson)-[:HAS_RESOURCE]->(koJson),
       (mainActivity)-[:INFLATES]->(layoutMain),
       (roundFrag)-[:INFLATES]->(layoutRound),
       (mergedFrag)-[:INFLATES]->(layoutMerged),
       (koFrag)-[:INFLATES]->(layoutKO),
       (finalFrag)-[:INFLATES]->(layoutFinal),
       (roundFrag)-[:USES]->(layoutMatrix),
       (roundFrag)-[:READS]->(txtHelp),
       (roundFrag)-[:READS]->(txtBouts),
       (koFrag)-[:READS]->(koJson);

// Useful index hints
CREATE INDEX project_name IF NOT EXISTS FOR (p:FencingScores:Project) ON (p.name);
CREATE INDEX class_name IF NOT EXISTS FOR (c:FencingScores:Class) ON (c.name);
CREATE INDEX state_name IF NOT EXISTS FOR (s:FencingScores:State) ON (s.name);
CREATE INDEX file_name IF NOT EXISTS FOR (f:FencingScores:File) ON (f.name);
