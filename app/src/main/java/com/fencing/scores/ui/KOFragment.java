/*
 * Fencing Scores - Android App for Fencing Pool Management
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fencing.scores.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fencing.scores.R;
import com.fencing.scores.MainActivity;
import com.fencing.scores.ScoresViewModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.journeyapps.barcodescanner.ScanOptions;
import com.journeyapps.barcodescanner.ScanContract;
import android.Manifest;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;

public class KOFragment extends Fragment {
            // Color pairs for KO boxes (same as RoundFragment)
            private static final int[][] RESULT_COLOR_PAIRS = {
                {0xFFFFD700, 0xFFFF7F50}, // Default
                {0xFF87CEFA, 0xFFB0C4DE},
                {0xFFFFFFE0, 0xFFF0E68C},
                {0xFF98FB98, 0xFF9ACD32},
                {0xFFA9A9A9, 0xFFDCDCDC},
                {0xFF00FFFF, 0xFF39E75F}
            };
            // Helper to interpolate between two colors
            private int interpolateColor(int colorStart, int colorEnd, float ratio) {
                int alpha = (int) (Color.alpha(colorStart) + (Color.alpha(colorEnd) - Color.alpha(colorStart)) * ratio);
                int red = (int) (Color.red(colorStart) + (Color.red(colorEnd) - Color.red(colorStart)) * ratio);
                int green = (int) (Color.green(colorStart) + (Color.green(colorEnd) - Color.green(colorStart)) * ratio);
                int blue = (int) (Color.blue(colorStart) + (Color.blue(colorEnd) - Color.blue(colorStart)) * ratio);
                return Color.argb(alpha, red, green, blue);
            }
            
            // Helper to brighten a color by a percentage (0.25 = 25% brighter) without changing hue
            private int brightenColor(int color, float factor) {
                int alpha = Color.alpha(color);
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                // Move each channel toward 255 by the factor percentage
                red = red + (int)((255 - red) * factor);
                green = green + (int)((255 - green) * factor);
                blue = blue + (int)((255 - blue) * factor);
                return Color.argb(alpha, red, green, blue);
            }
    // KO tree data structure
    private static class Match {
        int roundIdx;
        int matchIdx;
        String p1;
        String p2;
        String winner;
        int score1 = -1;
        int score2 = -1;
        boolean isLosers = false; // true if this is a losers bracket match
        String treeId = "R1"; // Which tree this match belongs to
        int displayColumn = 0; // Which display column (for horizontal alignment)
        Match(int roundIdx, int matchIdx, String p1, String p2) {
            this.roundIdx = roundIdx;
            this.matchIdx = matchIdx;
            this.p1 = p1;
            this.p2 = p2;
        }
    }
    
    // Repechage tree node - each tree has matches and can spawn sub-trees for losers
    private static class RepechageTree {
        String treeId; // e.g., "R1", "R1L", "R1W2L", "R1L2L"
        String parentTreeId; // null for main tree
        int parentRound; // which round of parent tree spawned this tree
        int startColumn; // which display column this tree starts at
        int positionStart; // First position this tree determines
        int positionEnd; // Last position this tree determines
        List<List<Match>> rounds = new ArrayList<>();
        List<RepechageTree> subTrees = new ArrayList<>(); // Sub-trees for losers of each round
        
        RepechageTree(String treeId) {
            this.treeId = treeId;
        }
    }
    
    private List<List<Match>> koRounds = new ArrayList<>(); // Main bracket
    private List<List<Match>> losersRounds = new ArrayList<>(); // Legacy losers bracket (kept for compatibility)
    private RepechageTree mainTree = null; // Main tree with all sub-trees for repechage
    private java.util.List<RepechageTree> allRepechageTrees = new ArrayList<>(); // Flat list of all trees
    private Match grandFinalMatch = null; // Grand Final: Main winner vs Losers winner
    private Match thirdPlaceMatch = null; // Third place match (for repechage)
    private boolean koRepechage = false;
    private String[] koParticipantNames = null; // Local copy of participant names for KO (to avoid affecting RoundFragment)
    private int koNrPart = 0; // Local copy of participant count
    private ScoresViewModel scoresViewModel;
    private ActivityResultLauncher<Intent> saveFileLauncher;
    private ActivityResultLauncher<Intent> replaceFileLauncher;
    private LinearLayout koBoxLayoutRef; // Keep reference for onResume
    private CheckBox repechageCheckboxRef; // Keep reference for restore
    
    // QR Scanner launcher for KO data
    private final ActivityResultLauncher<ScanOptions> qrScannerLauncher = 
        registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                handleKOQrScanResult(result.getContents());
            }
        });
    
    // Image picker launcher for QR from gallery
    private final ActivityResultLauncher<String> imagePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                decodeQrFromImage(uri);
            }
        });

    @Override
    public void onResume() {
        super.onResume();
        // Auto-restore from backup if koRounds is empty
        if (koRounds.isEmpty() && koBoxLayoutRef != null) {
            tryAutoRestoreFromBackup();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Auto-backup KO state when leaving fragment
        if (!koRounds.isEmpty()) {
            backupKOTree();
            android.util.Log.d("KOFragment", "Auto-backup on pause");
        }
    }
    
    // Get participant names for KO - uses local copy if available, otherwise falls back to ViewModel
    private String[] getKOParticipantNames() {
        if (koParticipantNames != null && koParticipantNames.length > 0) {
            return koParticipantNames;
        }
        return scoresViewModel.getParticipantNames().getValue();
    }
    
    // Get participant count for KO - uses local copy if set, otherwise falls back to ViewModel
    private int getKONrPart() {
        if (koNrPart > 0) {
            return koNrPart;
        }
        Integer vmValue = scoresViewModel.getNrPart().getValue();
        return vmValue != null ? vmValue : ScoresViewModel.DEFAULT_PARTICIPANTS;
    }
    
    // Load participant names from Merged_backup.csv into local koParticipantNames
    private void loadParticipantNamesFromMerged() {
        try {
            java.io.File filesDir = requireContext().getFilesDir();
            java.io.File mergedBackup = new java.io.File(filesDir, "Merged_backup.csv");
            if (!mergedBackup.exists()) return;
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(mergedBackup));
            String header = reader.readLine();
            if (header == null) { reader.close(); return; }
            
            // Find FinalPos column
            String[] headerParts = header.split(",");
            int idxFinalPos = -1;
            for (int i = 0; i < headerParts.length; i++) {
                if (headerParts[i].trim().equals("FinalPos")) {
                    idxFinalPos = i;
                    break;
                }
            }
            
            // Read all valid participants
            java.util.List<String[]> validParticipants = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",", -1);
                if (tokens.length >= 2 && !tokens[1].trim().isEmpty()) {
                    validParticipants.add(tokens);
                }
            }
            reader.close();
            
            // Sort by FinalPos if available
            if (idxFinalPos >= 0) {
                final int finalPosIdx = idxFinalPos;
                java.util.Collections.sort(validParticipants, (a, b) -> {
                    int posA = 999, posB = 999;
                    try {
                        if (finalPosIdx < a.length && !a[finalPosIdx].trim().isEmpty()) {
                            posA = Integer.parseInt(a[finalPosIdx].trim());
                        }
                    } catch (Exception e) { posA = 999; }
                    try {
                        if (finalPosIdx < b.length && !b[finalPosIdx].trim().isEmpty()) {
                            posB = Integer.parseInt(b[finalPosIdx].trim());
                        }
                    } catch (Exception e) { posB = 999; }
                    return Integer.compare(posA, posB);
                });
            }
            
            // Build sorted names array
            String[] sortedNames = new String[validParticipants.size()];
            for (int i = 0; i < validParticipants.size(); i++) {
                sortedNames[i] = validParticipants.get(i)[1].trim();
            }
            
            koParticipantNames = sortedNames;
            koNrPart = sortedNames.length;
            
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error loading participant names from Merged: " + e.getMessage());
        }
    }
    
    // Try to restore from KO_backup.csv if it exists
    private void tryAutoRestoreFromBackup() {
        try {
            java.io.File filesDir = requireContext().getFilesDir();
            java.io.File backupFile = new java.io.File(filesDir, "KO_backup.csv");
            if (!backupFile.exists()) {
                android.util.Log.d("KOFragment", "No KO_backup.csv found for auto-restore");
                return;
            }
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
            String metaLine = reader.readLine();
            if (metaLine == null) { reader.close(); return; }
            
            // Parse metadata line: #META,koSize,repechage
            int koSize = 8;
            boolean repechage = false;
            if (metaLine.startsWith("#META,")) {
                String[] metaParts = metaLine.split(",");
                if (metaParts.length >= 3) {
                    try { koSize = Integer.parseInt(metaParts[1].trim()); } catch (Exception e) {}
                    repechage = "true".equalsIgnoreCase(metaParts[2].trim());
                }
                // Skip header line
                reader.readLine();
            } else {
                // Old format without metadata - fall back to counting round 1 matches
                // metaLine is actually the header, continue reading
            }
            
            // Read match data
            java.util.List<String> lines = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            
            // If old format (no META line), count round 1 matches to determine koSize
            if (!metaLine.startsWith("#META,")) {
                int round1Matches = 0;
                for (String l : lines) {
                    String[] parts = l.split(",");
                    if (parts.length >= 1 && parts[0].trim().equals("1")) {
                        round1Matches++;
                    }
                }
                koSize = round1Matches * 2;
                if (koSize < 4) koSize = 8;
            }
            
            android.util.Log.d("KOFragment", "Auto-restore: koSize=" + koSize + ", repechage=" + repechage);
            koRepechage = repechage;
            koNrPart = koSize; // Set local nrPart from backup
            
            // Load participant names from Merged_backup.csv if available
            loadParticipantNamesFromMerged();
            
            // Reload KO tree structure
            loadKOTree(koSize, koRepechage);
            
            // Apply saved data
            for (String l : lines) {
                String[] parts = l.split(",");
                if (parts.length < 6) continue;
                try {
                    // New format: Tree,Round,Match,P1,Score1,P2,Score2,Winner
                    String treeId = parts[0].trim();
                    int roundNum = Integer.parseInt(parts[1].trim()) - 1;
                    int matchNum = Integer.parseInt(parts[2].trim()) - 1;
                    String p1 = parts[3].trim();
                    String scoreStr1 = parts[4].trim();
                    String p2 = parts[5].trim();
                    String scoreStr2 = parts.length > 6 ? parts[6].trim() : "";
                    
                    int score1 = scoreStr1.isEmpty() ? -1 : Integer.parseInt(scoreStr1);
                    int score2 = scoreStr2.isEmpty() ? -1 : Integer.parseInt(scoreStr2);
                    String winner = "";
                    if (score1 > score2) winner = p1;
                    else if (score2 > score1) winner = p2;
                    
                    if (treeId.equals("R1")) {
                        // Main bracket
                        if (roundNum >= 0 && roundNum < koRounds.size()) {
                            for (Match m : koRounds.get(roundNum)) {
                                if (m.matchIdx == matchNum) {
                                    m.p1 = p1;
                                    m.p2 = p2;
                                    m.score1 = score1;
                                    m.score2 = score2;
                                    m.winner = winner;
                                    break;
                                }
                            }
                        }
                    } else {
                        // Repechage tree
                        RepechageTree tree = findTreeById(treeId);
                        if (tree != null && roundNum >= 0 && roundNum < tree.rounds.size()) {
                            for (Match m : tree.rounds.get(roundNum)) {
                                if (m.matchIdx == matchNum) {
                                    m.p1 = p1;
                                    m.p2 = p2;
                                    m.score1 = score1;
                                    m.score2 = score2;
                                    m.winner = winner;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Try old format: Round,Match,P1,Score1,P2,Score2,Winner (no Tree column)
                    try {
                        int roundNum = Integer.parseInt(parts[0].trim()) - 1;
                        int matchNum = Integer.parseInt(parts[1].trim()) - 1;
                        String p1 = parts[2].trim();
                        String scoreStr1 = parts[3].trim();
                        String p2 = parts[4].trim();
                        String scoreStr2 = parts.length > 5 ? parts[5].trim() : "";
                        
                        if (roundNum >= 0 && roundNum < koRounds.size()) {
                            for (Match m : koRounds.get(roundNum)) {
                                if (m.matchIdx == matchNum) {
                                    m.p1 = p1;
                                    m.p2 = p2;
                                    m.score1 = scoreStr1.isEmpty() ? -1 : Integer.parseInt(scoreStr1);
                                    m.score2 = scoreStr2.isEmpty() ? -1 : Integer.parseInt(scoreStr2);
                                    if (m.score1 > m.score2) m.winner = p1;
                                    else if (m.score2 > m.score1) m.winner = p2;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e2) {}
                }
            }
            
            // Auto-advance Empty matches and propagawte winners
            String[] participantNames = getKOParticipantNames();
            if (participantNames != null) {
                autoAdvanceEmptyMatches(participantNames);
                if (koRepechage && !losersRounds.isEmpty()) {
                    propagateLosersToLosersBracket(participantNames);
                    autoAdvanceEmptyMatchesInLosersBracket(participantNames);
                }
            }
            propagateKOWinners();
            
            // Update checkbox state to match restored repechage
            if (repechageCheckboxRef != null) {
                repechageCheckboxRef.setOnCheckedChangeListener(null);
                repechageCheckboxRef.setChecked(koRepechage);
                repechageCheckboxRef.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    koRepechage = isChecked;
                    int nrPartVal = getKONrPart();
                    
                    // Save ALL match results before reloading using round/match indices
                    java.util.Map<String, int[]> savedResults = new java.util.HashMap<>();
                    for (int r = 0; r < koRounds.size(); r++) {
                        List<Match> round = koRounds.get(r);
                        for (int mi = 0; mi < round.size(); mi++) {
                            Match m = round.get(mi);
                            if (m.score1 >= 0 && m.score2 >= 0) {
                                String key = "R1|R" + r + "|M" + mi;
                                savedResults.put(key, new int[]{m.score1, m.score2});
                            }
                        }
                    }
                    for (RepechageTree tree : allRepechageTrees) {
                        if (tree.treeId.equals("R1")) continue;
                        for (int r = 0; r < tree.rounds.size(); r++) {
                            List<Match> round = tree.rounds.get(r);
                            for (int mi = 0; mi < round.size(); mi++) {
                                Match m = round.get(mi);
                                if (m.score1 >= 0 && m.score2 >= 0) {
                                    String key = tree.treeId + "|R" + r + "|M" + mi;
                                    savedResults.put(key, new int[]{m.score1, m.score2});
                                }
                            }
                        }
                    }
                    
                    loadKOTree(nrPartVal, koRepechage);
                    
                    // Restore saved match results to main bracket
                    for (int r = 0; r < koRounds.size(); r++) {
                        List<Match> round = koRounds.get(r);
                        for (int mi = 0; mi < round.size(); mi++) {
                            Match m = round.get(mi);
                            String key = "R1|R" + r + "|M" + mi;
                            if (savedResults.containsKey(key)) {
                                int[] scores = savedResults.get(key);
                                m.score1 = scores[0];
                                m.score2 = scores[1];
                                if (m.score1 > m.score2) {
                                    m.winner = getKOName(m.p1, getKOParticipantNames());
                                } else if (m.score2 > m.score1) {
                                    m.winner = getKOName(m.p2, getKOParticipantNames());
                                }
                            }
                        }
                    }
                    // Restore saved match results to all repechage trees
                    for (RepechageTree tree : allRepechageTrees) {
                        if (tree.treeId.equals("R1")) continue;
                        for (int r = 0; r < tree.rounds.size(); r++) {
                            List<Match> round = tree.rounds.get(r);
                            for (int mi = 0; mi < round.size(); mi++) {
                                Match m = round.get(mi);
                                String key = tree.treeId + "|R" + r + "|M" + mi;
                                if (savedResults.containsKey(key)) {
                                    int[] scores = savedResults.get(key);
                                    m.score1 = scores[0];
                                    m.score2 = scores[1];
                                    if (m.score1 > m.score2) {
                                        m.winner = getKOName(m.p1, getKOParticipantNames());
                                    } else if (m.score2 > m.score1) {
                                        m.winner = getKOName(m.p2, getKOParticipantNames());
                                    }
                                }
                            }
                        }
                    }
                    
                    String[] pNames = getKOParticipantNames();
                    if (pNames != null) {
                        autoAdvanceEmptyMatches(pNames);
                        if (koRepechage && !losersRounds.isEmpty()) {
                            propagateLosersToLosersBracket(pNames);
                            autoAdvanceEmptyMatchesInLosersBracket(pNames);
                        }
                    }
                    propagateKOWinners();
                    renderKOTable(koBoxLayoutRef);
                });
            }
            
            if (koBoxLayoutRef != null) {
                renderKOTable(koBoxLayoutRef);
            }
            android.util.Log.i("KOFragment", "Auto-restored " + lines.size() + " matches from KO_backup.csv");
            
        } catch (Exception e) {
            android.util.Log.w("KOFragment", "Auto-restore failed: " + e.getMessage());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Register file picker launcher for SAVE
        saveFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        saveCsvToUri(uri);
                    }
                }
            }
        );
        
        // Register file picker launcher for REPLACE
        replaceFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        loadKOFromCsvUri(uri);
                    }
                }
            }
        );

        View root = inflater.inflate(R.layout.fragment_ko, container, false);
        scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
        LinearLayout koBoxLayout = root.findViewById(R.id.ko_boxLayout);
        koBoxLayoutRef = koBoxLayout; // Save reference for onResume
        Button reloadBtn = root.findViewById(R.id.ko_reloadBtn);
        Button replaceBtn = root.findViewById(R.id.ko_replaceBtn);
        Button restoreCrashBtn = root.findViewById(R.id.ko_restoreCrashBtn);
        Button qrOutBtn = root.findViewById(R.id.ko_qrOutBtn);
        Button qrInBtn = root.findViewById(R.id.ko_qrInBtn);
        Button saveBtn = root.findViewById(R.id.ko_saveBtn);
        CheckBox repechageCheckbox = root.findViewById(R.id.ko_repechageCheckbox);
        repechageCheckboxRef = repechageCheckbox; // Save reference for restore

        // Observe ViewModel and update KO table reactively
        scoresViewModel.getParticipantNames().observe(getViewLifecycleOwner(), names -> renderKOTable(koBoxLayout));
        scoresViewModel.getNrPart().observe(getViewLifecycleOwner(), n -> renderKOTable(koBoxLayout));
        
        // Observe request for rankings calculation (triggered when Final page becomes visible)
        scoresViewModel.getRequestKORankings().observe(getViewLifecycleOwner(), request -> {
            if (request != null && request) {
                // Calculate rankings and set them
                java.util.List<String> rankings = calculateKORankings();
                // Filter out empty/null names
                java.util.List<String> filteredRankings = new java.util.ArrayList<>();
                for (String name : rankings) {
                    if (name != null && !name.trim().isEmpty() && !name.equalsIgnoreCase("Empty")) {
                        filteredRankings.add(name);
                    }
                }
                scoresViewModel.setFinalKORankings(filteredRankings);
                // Reset the request flag
                scoresViewModel.requestKORankingsCalculation(false);
            }
        });

        // Reload from Merged with confirmation
        reloadBtn.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(getContext())
                .setTitle("Reload KO from Merged?")
                .setMessage("This will reset all KO rounds. Continue?")
                .setPositiveButton("Yes", (dialog, which) -> reloadFromMerged(koBoxLayout))
                .setNegativeButton("Cancel", null)
                .show();
        });

        // REPLACE button: load KO state from selected CSV file
        replaceBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            replaceFileLauncher.launch(intent);
        });

        // RESTORE CRASH button: restore KO state from KO_backup.csv
        restoreCrashBtn.setOnClickListener(v -> restoreFromKOBackup(koBoxLayout));

        // QR OUT button: generate and show QR code
        qrOutBtn.setOnClickListener(v -> generateAndShowKOQrCode());

        // QR IN button: scan QR code
        qrInBtn.setOnClickListener(v -> startKOQrScanner());

        // SAVE button: save KO results to CSV in Downloads
        saveBtn.setOnClickListener(v -> saveKOToCSV());

        // Repechage checkbox: reload KO tree with/without repechage, preserving match results
        repechageCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            koRepechage = isChecked;
            int nrPartVal = getKONrPart();
            
            // Save ALL match results before reloading using round/match indices (stable across bracket types)
            java.util.Map<String, int[]> savedResults = new java.util.HashMap<>();
            // Save main bracket by round and match index
            for (int r = 0; r < koRounds.size(); r++) {
                List<Match> round = koRounds.get(r);
                for (int mi = 0; mi < round.size(); mi++) {
                    Match m = round.get(mi);
                    if (m.score1 >= 0 && m.score2 >= 0) {
                        String key = "R1|R" + r + "|M" + mi;
                        savedResults.put(key, new int[]{m.score1, m.score2});
                    }
                }
            }
            // Save all repechage trees by round and match index
            for (RepechageTree tree : allRepechageTrees) {
                if (tree.treeId.equals("R1")) continue; // Skip main tree, already saved above
                for (int r = 0; r < tree.rounds.size(); r++) {
                    List<Match> round = tree.rounds.get(r);
                    for (int mi = 0; mi < round.size(); mi++) {
                        Match m = round.get(mi);
                        if (m.score1 >= 0 && m.score2 >= 0) {
                            String key = tree.treeId + "|R" + r + "|M" + mi;
                            savedResults.put(key, new int[]{m.score1, m.score2});
                        }
                    }
                }
            }
            
            loadKOTree(nrPartVal, koRepechage);
            
            // Restore saved match results to main bracket
            for (int r = 0; r < koRounds.size(); r++) {
                List<Match> round = koRounds.get(r);
                for (int mi = 0; mi < round.size(); mi++) {
                    Match m = round.get(mi);
                    String key = "R1|R" + r + "|M" + mi;
                    if (savedResults.containsKey(key)) {
                        int[] scores = savedResults.get(key);
                        m.score1 = scores[0];
                        m.score2 = scores[1];
                        if (m.score1 > m.score2) {
                            m.winner = getKOName(m.p1, getKOParticipantNames());
                        } else if (m.score2 > m.score1) {
                            m.winner = getKOName(m.p2, getKOParticipantNames());
                        }
                    }
                }
            }
            // Restore saved match results to all repechage trees
            for (RepechageTree tree : allRepechageTrees) {
                if (tree.treeId.equals("R1")) continue;
                for (int r = 0; r < tree.rounds.size(); r++) {
                    List<Match> round = tree.rounds.get(r);
                    for (int mi = 0; mi < round.size(); mi++) {
                        Match m = round.get(mi);
                        String key = tree.treeId + "|R" + r + "|M" + mi;
                        if (savedResults.containsKey(key)) {
                            int[] scores = savedResults.get(key);
                            m.score1 = scores[0];
                            m.score2 = scores[1];
                            if (m.score1 > m.score2) {
                                m.winner = getKOName(m.p1, getKOParticipantNames());
                            } else if (m.score2 > m.score1) {
                                m.winner = getKOName(m.p2, getKOParticipantNames());
                            }
                        }
                    }
                }
            }
            
            // Auto-advance Empty matches and propagate to losers bracket
            String[] participantNames = getKOParticipantNames();
            if (participantNames != null) {
                autoAdvanceEmptyMatches(participantNames);
                if (koRepechage && !losersRounds.isEmpty()) {
                    propagateLosersToLosersBracket(participantNames);
                    autoAdvanceEmptyMatchesInLosersBracket(participantNames);
                }
            }
            propagateKOWinners();
            renderKOTable(koBoxLayout);
        });

        // Initial render
        renderKOTable(koBoxLayout);
        
        return root;
    }

    // Load KO tree from JSON asset (ko_8.json or ko_h8.json)
    private void loadKOTree(int nrPart, boolean repechage) {
        koRounds.clear();
        losersRounds.clear();
        grandFinalMatch = null;
        thirdPlaceMatch = null;
        
        // Calculate next power of two >= nrPart
        int N = 1;
        while (N < nrPart) N *= 2;
        int koSize = N;
        
        // Always load main bracket from regular KO file
        String jsonFile = getKOJsonFile(koSize, false); // Use regular KO file for main bracket
        if (jsonFile == null) {
            Toast.makeText(getContext(), "KO tree not available for " + koSize + " participants", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            InputStream is = getContext().getAssets().open(jsonFile);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer);
            JSONObject obj = new JSONObject(jsonStr);
            JSONArray rounds = obj.has("rounds") ? obj.getJSONArray("rounds") : null;
            if (rounds != null) {
                // For repechage: only load main bracket rounds (exclude Third Place match)
                int mainBracketRounds = repechage ? rounds.length() - 1 : rounds.length();
                for (int i = 0; i < mainBracketRounds; i++) {
                    JSONObject round = rounds.getJSONObject(i);
                    JSONArray matches = round.getJSONArray("matches");
                    List<Match> roundMatches = new ArrayList<>();
                    for (int j = 0; j < matches.length(); j++) {
                        JSONObject m = matches.getJSONObject(j);
                        String p1 = m.get("p1").toString();
                        String p2 = m.get("p2").toString();
                        Match match = new Match(i, j, p1, p2);
                        roundMatches.add(match);
                    }
                    koRounds.add(roundMatches);
                }
            }
            
            // For repechage mode, initialize losers bracket structure
            if (repechage && koRounds.size() > 0) {
                initializeLosersBracket(koSize);
            }
            
            // Debug output
            int firstRoundMatches = (koRounds.size() > 0) ? koRounds.get(0).size() : 0;
            int losersRoundCount = losersRounds.size();
            android.util.Log.d("KOFragment", "DEBUG: Nr=" + nrPart + ", main rounds=" + koRounds.size() + 
                ", first round matches=" + firstRoundMatches + ", losers rounds=" + losersRoundCount);
        } catch (Exception e) {
            Toast.makeText(getContext(), "KO tree asset missing or invalid: " + jsonFile, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    // Initialize consolation trees for repechage mode
    // Creates parallel consolation trees using naming convention:
    // R1 = main tree, R1L = losers from R1 of main, R1W2L = won R1 lost R2, etc.
    private void initializeLosersBracket(int koSize) {
        losersRounds.clear();
        allRepechageTrees.clear();
        mainTree = null;
        
        int numMainRounds = koRounds.size();
        int totalParticipants = koSize;
        
        // Create main tree structure named "R1"
        mainTree = new RepechageTree("R1");
        mainTree.startColumn = 0;
        mainTree.positionStart = 1;
        mainTree.positionEnd = totalParticipants;
        
        // Copy koRounds to mainTree.rounds and set displayColumn
        for (int r = 0; r < koRounds.size(); r++) {
            List<Match> round = koRounds.get(r);
            for (Match m : round) {
                m.displayColumn = r;
                m.treeId = "R1";
            }
            mainTree.rounds.add(round);
        }
        
        allRepechageTrees.add(mainTree);
        
        // Build repechage sub-trees recursively using new naming convention
        buildRepechageSubTreesNew(mainTree, totalParticipants, "");
        
        // Populate legacy losersRounds for backward compatibility
        rebuildLosersRoundsFromRepechageTrees();
        
        grandFinalMatch = null;
        thirdPlaceMatch = null;
        
        android.util.Log.d("KOFragment", "Repechage trees initialized: " + allRepechageTrees.size() + " trees");
    }
    
    // Build repechage sub-trees using naming convention:
    // R1 (main), R1L (lost R1), R1W2L (won R1 lost R2), R1L2L (lost R1, lost in R1L's round 1), etc.
    // The "history" parameter tracks the W/L path: "" for main, "L" for R1L, "W2L" for R1W2L, etc.
    private void buildRepechageSubTreesNew(RepechageTree parentTree, int totalParticipants, String parentHistory) {
        int numRounds = parentTree.rounds.size();
        
        // For each round except the last, losers form a sub-tree
        for (int r = 0; r < numRounds - 1; r++) {
            List<Match> round = parentTree.rounds.get(r);
            int numLosers = round.size();
            
            if (numLosers < 2) continue; // Need at least 2 losers for a tree
            
            // Build the new tree's ID based on history
            // Column number for this loser tree = startColumn of parent + r + 1
            int loserColumn = parentTree.startColumn + r + 1;
            
            // Build the history string for this subtree
            String newHistory;
            if (parentHistory.isEmpty()) {
                // From main tree (R1): losers from round (r+1) get history based on round
                // Round 1 losers: "L" (becomes R1L)
                // Round 2 losers: "W2L" (won R1, lost R2, becomes R1W2L)
                // Round 3 losers: "W2W3L" (won R1, won R2, lost R3, becomes R1W2W3L)
                if (r == 0) {
                    newHistory = "L";
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= r; i++) {
                        sb.append("W").append(i + 1);
                    }
                    sb.append("L");
                    newHistory = sb.toString();
                }
            } else {
                // From a subtree: append the round number and L
                // E.g., R1L's round 1 losers become R1L2L (the 2 indicates column/displayColumn)
                newHistory = parentHistory + (loserColumn) + "L";
            }
            
            String subTreeId = "R1" + newHistory;
            RepechageTree subTree = new RepechageTree(subTreeId);
            subTree.parentTreeId = parentTree.treeId;
            subTree.parentRound = r;
            subTree.startColumn = loserColumn;
            
            // Calculate position range: losers compete for worse positions in parent's range
            // If parent handles positions posStart to posEnd, and this is losers from round r,
            // they compete for the worse half of remaining positions
            int parentPosStart = parentTree.positionStart;
            int parentPosEnd = parentTree.positionEnd;
            int remainingRange = parentPosEnd - parentPosStart + 1;
            int halfRange = remainingRange / 2;
            
            // Losers from earlier rounds get worse positions
            // R1 losers: worst positions, R2 losers: better than R1 losers, etc.
            int roundDivisor = 1 << (numRounds - r - 1); // For R1 of parent with 4 rounds: 8
            subTree.positionStart = parentPosStart + remainingRange - (remainingRange / roundDivisor);
            subTree.positionEnd = parentPosEnd;
            
            // Build rounds for this sub-tree
            int matchCount = numLosers;
            int roundIdx = 0;
            
            while (matchCount >= 2) {
                List<Match> subRound = new ArrayList<>();
                int numMatches = matchCount / 2;
                
                for (int m = 0; m < numMatches; m++) {
                    String p1, p2;
                    if (roundIdx == 0) {
                        // First round: losers from parent tree's round r
                        p1 = "L_" + parentTree.treeId + "_R" + (r + 1) + "_" + (m * 2 + 1);
                        p2 = "L_" + parentTree.treeId + "_R" + (r + 1) + "_" + (m * 2 + 2);
                    } else {
                        // Subsequent rounds: winners from previous round of this tree
                        p1 = "W_" + subTreeId + "_R" + roundIdx + "_" + (m * 2 + 1);
                        p2 = "W_" + subTreeId + "_R" + roundIdx + "_" + (m * 2 + 2);
                    }
                    
                    Match match = new Match(roundIdx, m, p1, p2);
                    match.isLosers = true;
                    match.treeId = subTreeId;
                    match.displayColumn = subTree.startColumn + roundIdx;
                    subRound.add(match);
                }
                
                subTree.rounds.add(subRound);
                matchCount = numMatches;
                roundIdx++;
            }
            
            parentTree.subTrees.add(subTree);
            allRepechageTrees.add(subTree);
            
            // Recursively build sub-trees for this sub-tree's losers
            if (subTree.rounds.size() > 1) {
                buildRepechageSubTreesNew(subTree, totalParticipants, newHistory);
            }
        }
    }
    
    // Rebuild losersRounds from all repechage trees (for compatibility)
    private void rebuildLosersRoundsFromRepechageTrees() {
        losersRounds.clear();
        
        // Collect all non-main tree matches, organized by display column
        java.util.Map<Integer, List<Match>> matchesByColumn = new java.util.TreeMap<>();
        
        for (RepechageTree tree : allRepechageTrees) {
            if (tree.treeId.equals("R1")) continue; // Skip main tree
            
            for (List<Match> round : tree.rounds) {
                for (Match m : round) {
                    matchesByColumn.computeIfAbsent(m.displayColumn, k -> new ArrayList<>()).add(m);
                }
            }
        }
        
        // Convert to losersRounds list
        for (List<Match> columnMatches : matchesByColumn.values()) {
            losersRounds.add(columnMatches);
        }
    }

    // Helper: choose correct KO JSON file
    private String getKOJsonFile(int nrPart, boolean repechage) {
        if (nrPart <= 4 || nrPart > 128) return null;
        if (nrPart <= 8) return repechage ? "KO/ko_h8.json" : "KO/ko_8.json";
        if (nrPart <= 16) return repechage ? "KO/ko_h16.json" : "KO/ko_16.json";
        if (nrPart <= 32) return repechage ? "KO/ko_h32.json" : "KO/ko_32.json";
        if (nrPart <= 64) return repechage ? "KO/ko_h64.json" : "KO/ko_64.json";
        if (nrPart <= 128) return repechage ? "KO/ko_h128.json" : "KO/ko_128.json";
        return null;
    }

    // Reload KO data from Merged backup file (reset KO state)
    private void reloadFromMerged(LinearLayout koBoxLayout) {
        android.util.Log.v("KOFragment", "reloadFromMerged() called - loading from Merged_backup.csv");
        
        // Load from MergedFragment's backup file
        java.io.File filesDir = requireContext().getFilesDir();
        java.io.File mergedBackupFile = new java.io.File(filesDir, "Merged_backup.csv");
        
        if (!mergedBackupFile.exists()) {
            Toast.makeText(getContext(), "Merged backup not found. Please add participants in Merged first.", Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(mergedBackupFile));
            String headerLine = reader.readLine();
            if (headerLine == null) {
                reader.close();
                Toast.makeText(getContext(), "Merged backup is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Parse header to find FinalPos column
            String[] header = headerLine.split(",");
            int idxFinalPos = -1;
            for (int i = 0; i < header.length; i++) {
                if (header[i].trim().equals("FinalPos")) {
                    idxFinalPos = i;
                    break;
                }
            }
            
            // Read all rows and filter out empty names
            java.util.List<String[]> validParticipants = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",", -1);
                if (tokens.length >= 2) {
                    String name = tokens[1].trim();
                    if (name != null && !name.isEmpty()) {
                        validParticipants.add(tokens);
                    }
                }
            }
            reader.close();
            
            int actualParticipants = validParticipants.size();
            android.util.Log.i("KOFragment", "Loaded " + actualParticipants + " valid participants from Merged backup");
            
            if (actualParticipants == 0) {
                Toast.makeText(getContext(), "No valid participants found in Merged", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Sort by FinalPos if available, otherwise by order in file
            if (idxFinalPos >= 0) {
                final int finalPosIdx = idxFinalPos;
                java.util.Collections.sort(validParticipants, (a, b) -> {
                    int posA = 999, posB = 999;
                    try {
                        if (finalPosIdx < a.length && !a[finalPosIdx].trim().isEmpty()) {
                            posA = Integer.parseInt(a[finalPosIdx].trim());
                        }
                    } catch (Exception e) { posA = 999; }
                    try {
                        if (finalPosIdx < b.length && !b[finalPosIdx].trim().isEmpty()) {
                            posB = Integer.parseInt(b[finalPosIdx].trim());
                        }
                    } catch (Exception e) { posB = 999; }
                    return Integer.compare(posA, posB);
                });
            }
            
            // Build names array (sorted by FinalPos)
            String[] sortedNames = new String[actualParticipants];
            for (int i = 0; i < actualParticipants; i++) {
                sortedNames[i] = validParticipants.get(i)[1].trim();
            }
            
            android.util.Log.i("KOFragment", "Participants sorted by FinalPos: " + java.util.Arrays.toString(sortedNames));
            
            // Store participant names locally (don't update shared ViewModel to avoid affecting RoundFragment)
            koParticipantNames = sortedNames;
            koNrPart = actualParticipants;
            
            // Build full participantNames array for KO bracket (including "Empty" slots)
            int koSize = 1;
            while (koSize < actualParticipants) koSize *= 2;
            String[] fullKONames = new String[koSize];
            for (int i = 0; i < koSize; i++) {
                if (i < sortedNames.length) fullKONames[i] = sortedNames[i];
                else fullKONames[i] = "Empty";
            }
            
            // Reset KO rounds and load KO tree with correct bracket size
            koRounds.clear();
            loadKOTree(actualParticipants, koRepechage);
            autoAdvanceEmptyMatches(fullKONames);
            // In repechage mode, propagate losers and auto-advance in losers bracket
            if (koRepechage && !losersRounds.isEmpty()) {
                propagateLosersToLosersBracket(fullKONames);
                autoAdvanceEmptyMatchesInLosersBracket(fullKONames);
            }
            renderKOTable(koBoxLayout);
            
            Toast.makeText(getContext(), "Loaded " + actualParticipants + " participants for KO", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error loading Merged_backup.csv: " + e.getMessage());
            Toast.makeText(getContext(), "Failed to load Merged backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Auto-advance all matches where one participant is 'Empty'
    private void autoAdvanceEmptyMatches(String[] participantNames) {
        // Only auto-advance in the first round, but never overwrite p1/p2 in Round 1
        if (koRounds.isEmpty() || koRounds.size() < 2) return;
        List<Match> firstRound = koRounds.get(0);
        List<Match> secondRound = koRounds.get(1);
        for (int i = 0; i < firstRound.size(); i++) {
            Match match = firstRound.get(i);
            String p1 = match.p1;
            String p2 = match.p2;
            // Resolve numeric indices to actual names
            String p1Name = getKOName(p1, participantNames);
            String p2Name = getKOName(p2, participantNames);
            int nextIdx = i / 2;
            String winner = null;
            
            // Case 1: Both participants are Empty - set winner to "Empty"
            if (p1Name.equals("Empty") && p2Name.equals("Empty")) {
                match.score1 = 0;
                match.score2 = 0;
                match.winner = "Empty";
                winner = "Empty";
            }
            // Case 2: One participant is Empty, the other wins 15:0
            else if (!p1Name.equals("Empty") && p2Name.equals("Empty")) {
                match.score1 = 15;
                match.score2 = 0;
                match.winner = p1Name;
                winner = p1Name;
            } else if (p1Name.equals("Empty") && !p2Name.equals("Empty")) {
                match.score1 = 0;
                match.score2 = 15;
                match.winner = p2Name;
                winner = p2Name;
            }
            // Case 3: Both are real participants - DO NOT propagate, wait for match result
            
            // Only propagate if there's a winner (from Empty match)
            if (winner != null && nextIdx < secondRound.size()) {
                Match nextMatch = secondRound.get(nextIdx);
                if (i % 2 == 0 && nextMatch.p1.startsWith("W")) {
                    nextMatch.p1 = winner;
                } else if (i % 2 == 1 && nextMatch.p2.startsWith("W")) {
                    nextMatch.p2 = winner;
                }
            }
        }
        
        // For later rounds, run multiple passes until no more changes
        // This ensures cascading auto-advances (e.g., Round 2 enables Round 3)
        boolean changed = true;
        int maxPasses = koRounds.size(); // Safety limit
        int pass = 0;
        while (changed && pass < maxPasses) {
            changed = false;
            pass++;
            for (int r = 1; r < koRounds.size(); r++) {
                List<Match> currentRound = koRounds.get(r);
                List<Match> nextRound = (r + 1 < koRounds.size()) ? koRounds.get(r + 1) : null;
                for (int i = 0; i < currentRound.size(); i++) {
                    Match match = currentRound.get(i);
                    // Skip if already has a winner
                    if (match.winner != null) continue;
                    
                    String p1 = match.p1;
                    String p2 = match.p2;
                    
                    // Skip if either side still has unresolved W*/L* reference
                    if ((p1.startsWith("W") || p1.startsWith("L")) || 
                        (p2.startsWith("W") || p2.startsWith("L"))) {
                        continue;
                    }
                    
                    // Both sides are resolved names - check for Empty
                    String p1Name = p1.equals("Empty") ? "Empty" : p1;
                    String p2Name = p2.equals("Empty") ? "Empty" : p2;
                    
                    String winner = null;
                    // Case 1: Both are Empty
                    if (p1Name.equals("Empty") && p2Name.equals("Empty")) {
                        match.score1 = 0;
                        match.score2 = 0;
                        match.winner = "Empty";
                        winner = "Empty";
                        changed = true;
                    }
                    // Case 2: One is Empty
                    else if (p1Name.equals("Empty") && !p2Name.equals("Empty")) {
                        match.score1 = 0;
                        match.score2 = 15;
                        match.winner = p2Name;
                        winner = p2Name;
                        changed = true;
                    } else if (!p1Name.equals("Empty") && p2Name.equals("Empty")) {
                        match.score1 = 15;
                        match.score2 = 0;
                        match.winner = p1Name;
                        winner = p1Name;
                        changed = true;
                    }
                    // Case 3: Both are real names - don't auto-advance
                    
                    // Propagate winner to next round
                    if (winner != null && nextRound != null) {
                        int nextIdx = i / 2;
                        if (nextIdx < nextRound.size()) {
                            Match nextMatch = nextRound.get(nextIdx);
                            if (i % 2 == 0 && nextMatch.p1.startsWith("W")) {
                                nextMatch.p1 = winner;
                            } else if (i % 2 == 1 && nextMatch.p2.startsWith("W")) {
                                nextMatch.p2 = winner;
                            }
                        }
                    }
                }
            }
        }
    }

    // Helper: resolve KO reference (W*, L*, or number) to participant name or 'Empty'
    // currentRound is the round that contains this reference (W/L refs look at previous round)
    private String resolveKORef(String ref, java.util.List<java.util.List<Match>> koRounds, int currentRound) {
        if (ref == null) return "Empty";
        if (ref.equals("Empty")) return "Empty";
        if (ref.startsWith("W") || ref.startsWith("L")) {
            try {
                char wl = ref.charAt(0);
                int matchNum = Integer.parseInt(ref.substring(1)); // 1-based match number
                
                // Determine which round to look in:
                // - For W refs: look in previous round (currentRound - 1)
                // - For L refs: Third Place (last round) uses losers from Semifinals (typically round koRounds.size() - 3)
                int lookInRound;
                if (wl == 'L') {
                    // L refs in Third Place look at Semifinals (2 rounds before Third Place, or 1 round before Final)
                    // Third Place is typically the last round
                    lookInRound = Math.max(0, koRounds.size() - 3); // Semifinals for 4+ round brackets
                } else {
                    // W refs look at immediately previous round
                    lookInRound = currentRound - 1;
                }
                
                if (lookInRound < 0 || lookInRound >= koRounds.size()) return ref;
                
                List<Match> targetRound = koRounds.get(lookInRound);
                for (Match m : targetRound) {
                    if ((m.matchIdx + 1) == matchNum) {
                        if (wl == 'W' && m.winner != null) {
                            return m.winner;
                        } else if (wl == 'L' && m.winner != null) {
                            // Return the LOSER of this match
                            // First, resolve p1 and p2 to actual names (they might be W refs from earlier rounds)
                            String p1Resolved = resolveKORef(m.p1, koRounds, lookInRound);
                            String p2Resolved = resolveKORef(m.p2, koRounds, lookInRound);
                            // Also resolve through getKOName for numeric refs
                            p1Resolved = getKONameForRef(p1Resolved);
                            p2Resolved = getKONameForRef(p2Resolved);
                            
                            // Use scores to determine the loser - more reliable than name comparison
                            // If score1 > score2, p1 won so p2 is the loser
                            // If score2 > score1, p2 won so p1 is the loser
                            if (m.score1 > m.score2) {
                                return p2Resolved; // p1 won, return p2 (loser)
                            } else if (m.score2 > m.score1) {
                                return p1Resolved; // p2 won, return p1 (loser)
                            } else {
                                // Scores are equal or both 0 - fall back to name comparison
                                if (m.winner != null && m.winner.equals(p1Resolved)) {
                                    return p2Resolved;
                                } else if (m.winner != null) {
                                    return p1Resolved;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) { return ref; }
            return ref;
        }
        return ref;
    }
    
    // Overload for backward compatibility (used where round is not known)
    private String resolveKORef(String ref, java.util.List<java.util.List<Match>> koRounds) {
        // Legacy: search all rounds (not ideal, but maintains compatibility)
        if (ref == null) return "Empty";
        if (ref.equals("Empty")) return "Empty";
        if (!ref.startsWith("W") && !ref.startsWith("L")) return ref;
        // Can't determine correct round, return ref unchanged
        return ref;
    }
    
    // Helper to get name from ref without participantNames (used internally)
    private String getKONameForRef(String ref) {
        if (ref == null) return "Empty";
        if (ref.equals("Empty")) return "Empty";
        String[] names = getKOParticipantNames();
        if (names == null) return ref;
        return getKOName(ref, names);
    }

    // Launch file picker for saving KO results to CSV
    private void saveKOToCSV() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "KO_results.csv");
        saveFileLauncher.launch(intent);
    }

    // Write KO CSV content to user-selected URI
    private void saveCsvToUri(Uri uri) {
        try {
            java.io.OutputStream os = requireContext().getContentResolver().openOutputStream(uri);
            if (os == null) {
                Toast.makeText(getContext(), "Failed to open file for writing", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] participantNames = getKOParticipantNames();
            android.util.Log.i("KOFragment", "SAVE CSV: koRounds.size()=" + koRounds.size() + ", participantNames=" + (participantNames != null ? participantNames.length : "null"));
            if (participantNames != null) {
                for (int i = 0; i < participantNames.length; i++) {
                    android.util.Log.d("KOFragment", "SAVE CSV: participantNames[" + i + "]=" + participantNames[i]);
                }
            }
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(os);
            writer.write("Tree,Round,Match,Participant1,Score1,Participant2,Score2,Winner\n");
            
            // Write main bracket (R1)
            for (int r = 0; r < koRounds.size(); r++) {
                android.util.Log.d("KOFragment", "SAVE CSV: Round " + r + " has " + koRounds.get(r).size() + " matches");
                for (Match m : koRounds.get(r)) {
                    // Resolve participant references to "Number Name" format
                    String p1Display = formatParticipantForCsv(m.p1, participantNames, r);
                    String p2Display = formatParticipantForCsv(m.p2, participantNames, r);
                    android.util.Log.d("KOFragment", "SAVE CSV: m.p1=" + m.p1 + " -> " + p1Display + ", m.p2=" + m.p2 + " -> " + p2Display);
                    String winnerDisplay = "";
                    if (m.score1 > m.score2) {
                        winnerDisplay = p1Display;
                    } else if (m.score2 > m.score1) {
                        winnerDisplay = p2Display;
                    }
                    writer.write("R1,"+(r+1)+","+(m.matchIdx+1)+","+p1Display+","+(m.score1>=0?m.score1:"")+","+p2Display+","+(m.score2>=0?m.score2:"")+","+winnerDisplay+"\n");
                }
            }
            
            // Write repechage trees if enabled
            if (koRepechage && !allRepechageTrees.isEmpty()) {
                for (RepechageTree tree : allRepechageTrees) {
                    if (tree.treeId.equals("R1")) continue; // Skip main tree (already written)
                    
                    for (int r = 0; r < tree.rounds.size(); r++) {
                        for (Match m : tree.rounds.get(r)) {
                            String p1Display = formatRepechageParticipantForCsv(m.p1, participantNames);
                            String p2Display = formatRepechageParticipantForCsv(m.p2, participantNames);
                            String winnerDisplay = "";
                            if (m.score1 >= 0 && m.score2 >= 0) {
                                if (m.score1 > m.score2) {
                                    winnerDisplay = p1Display;
                                } else if (m.score2 > m.score1) {
                                    winnerDisplay = p2Display;
                                }
                            }
                            writer.write(tree.treeId+","+(r+1)+","+(m.matchIdx+1)+","+p1Display+","+(m.score1>=0?m.score1:"")+","+p2Display+","+(m.score2>=0?m.score2:"")+","+winnerDisplay+"\n");
                        }
                    }
                }
            }
            
            writer.close();
            os.close();
            android.util.Log.i("KOFragment", "SAVE CSV: File saved to URI: " + uri.toString());
            Toast.makeText(getContext(), "KO results saved successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "SAVE CSV ERROR: " + e.getMessage(), e);
            Toast.makeText(getContext(), "Failed to save KO results: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // Format repechage participant reference for CSV output
    private String formatRepechageParticipantForCsv(String ref, String[] participantNames) {
        if (ref == null) return "Empty";
        String resolved = getLosersName(ref, participantNames);
        if (resolved.equals("Empty") || resolved.equals("-")) return "Empty";
        // Try to find position for the name
        if (participantNames != null) {
            for (int i = 0; i < participantNames.length; i++) {
                if (resolved.equals(participantNames[i])) {
                    return (i + 1) + " " + resolved;
                }
            }
        }
        return resolved;
    }
    
    // Format participant reference as "Number Name" for CSV output
    private String formatParticipantForCsv(String ref, String[] participantNames, int roundIdx) {
        if (ref == null || ref.equals("Empty")) return "Empty";
        // Resolve W/L references first
        String resolved = resolveKORef(ref, koRounds, roundIdx);
        android.util.Log.d("KOFragment", "formatParticipantForCsv: ref=" + ref + " -> resolved=" + resolved);
        // Try to parse as participant number
        try {
            int idx = Integer.parseInt(resolved) - 1;
            if (participantNames != null && idx >= 0 && idx < participantNames.length) {
                String name = participantNames[idx];
                android.util.Log.d("KOFragment", "formatParticipantForCsv: idx=" + idx + " -> name=" + name);
                if (name != null && !name.isEmpty() && !name.equals("Empty")) {
                    return (idx + 1) + " " + name;
                }
            }
        } catch (NumberFormatException e) {
            // Not a number, return as-is (already a name from later rounds)
        }
        // For non-numeric refs (names), try to find their position
        if (participantNames != null) {
            for (int i = 0; i < participantNames.length; i++) {
                if (resolved.equals(participantNames[i])) {
                    return (i + 1) + " " + resolved;
                }
            }
        }
        return resolved;
    }

    // Show final classification page - calculate rankings and navigate to FinalFragment
    private void showFinalPage() {
        java.util.List<String> rankings = calculateKORankings();
        
        if (rankings.isEmpty()) {
            Toast.makeText(getContext(), "No results to show. Complete some matches first.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Set rankings in ViewModel
        scoresViewModel.setFinalKORankings(rankings);
        
        // Navigate to FinalFragment (position 3)
        if (getActivity() instanceof com.fencing.scores.MainActivity) {
            com.fencing.scores.MainActivity activity = (com.fencing.scores.MainActivity) getActivity();
            activity.navigateToPage(3);
        }
    }
    
    // Calculate rankings based on KO match outcomes
    private java.util.List<String> calculateKORankings() {
        java.util.List<String> rankings = new java.util.ArrayList<>();
        
        if (koRounds.isEmpty()) {
            return rankings;
        }
        
        String[] participantNames = getKOParticipantNames();
        if (participantNames == null) return rankings;
        
        // Load FinalPos from Merged_backup.csv for tiebreaking
        final java.util.Map<String, Integer> nameToFinalPos = loadFinalPosFromMerged();
        
        // Handle repechage mode differently
        if (koRepechage && !losersRounds.isEmpty()) {
            return calculateRepechageRankings(participantNames, nameToFinalPos);
        }
        
        // Standard single-elimination rankings
        // For fencing: no 3rd place match - losers are ranked by FinalPos within each elimination round
        // Map: participant name -> round eliminated (0 = first round, higher = later round)
        java.util.Map<String, Integer> nameToRoundEliminated = new java.util.HashMap<>();
        
        String finalWinner = null;
        String finalLoser = null;
        
        // Find the actual final round (last round with exactly 1 match, excluding any third place match)
        // The final is the first single-match round after all multi-match rounds
        int finalRoundIndex = -1;
        for (int r = 0; r < koRounds.size(); r++) {
            List<Match> round = koRounds.get(r);
            if (round.size() == 1) {
                // Check if previous round exists and has 2 matches (semifinal)
                if (r > 0 && koRounds.get(r - 1).size() == 2) {
                    finalRoundIndex = r;
                    break;
                } else if (r == koRounds.size() - 1 || r == koRounds.size() - 2) {
                    // Fallback: if it's near the end and has 1 match, consider it the final
                    finalRoundIndex = r;
                    break;
                }
            }
        }
        
        // Go through all rounds and track when each participant was eliminated
        for (int r = 0; r < koRounds.size(); r++) {
            List<Match> round = koRounds.get(r);
            
            // Skip third place match (any round after the final)
            if (finalRoundIndex >= 0 && r > finalRoundIndex) continue;
            
            boolean isFinal = (r == finalRoundIndex);
            
            for (Match m : round) {
                // Resolve p1 and p2 to actual names
                String p1Ref = resolveKORef(m.p1, koRounds, r);
                String p2Ref = resolveKORef(m.p2, koRounds, r);
                String p1Name = getKOName(p1Ref, participantNames);
                String p2Name = getKOName(p2Ref, participantNames);
                String winner = m.winner;
                
                // Skip Empty participants
                if (p1Name.equals("Empty") && p2Name.equals("Empty")) continue;
                
                if (winner != null && !winner.equals("Empty")) {
                    // Determine actual loser name
                    String loser = winner.equals(p1Name) ? p2Name : p1Name;
                    
                    if (isFinal) {
                        // Track 1st/2nd place specifically
                        finalWinner = winner;
                        finalLoser = loser;
                    } else {
                        // Regular round - loser is eliminated here
                        if (!loser.equals("Empty") && !nameToRoundEliminated.containsKey(loser)) {
                            nameToRoundEliminated.put(loser, r);
                        }
                    }
                }
            }
        }
        
        // Build rankings in order:
        // 1st: Final winner
        // 2nd: Final loser  
        // Then: losers from each round sorted by elimination round (higher = better) and FinalPos
        
        if (finalWinner != null && !finalWinner.equals("Empty")) {
            rankings.add(finalWinner);
        }
        if (finalLoser != null && !finalLoser.equals("Empty")) {
            rankings.add(finalLoser);
        }
        
        // Sort remaining participants: higher round eliminated = better rank
        // Within same round: lower FinalPos (better pool position) = better final rank
        java.util.List<java.util.Map.Entry<String, Integer>> sortedEntries = 
            new java.util.ArrayList<>(nameToRoundEliminated.entrySet());
        
        sortedEntries.sort((a, b) -> {
            int roundA = a.getValue();
            int roundB = b.getValue();
            if (roundA != roundB) {
                return Integer.compare(roundB, roundA); // Higher round = better
            }
            // Same round: use FinalPos from Merged (lower FinalPos = better)
            int posA = nameToFinalPos.getOrDefault(a.getKey(), 999);
            int posB = nameToFinalPos.getOrDefault(b.getKey(), 999);
            return Integer.compare(posA, posB);
        });
        
        for (java.util.Map.Entry<String, Integer> entry : sortedEntries) {
            String name = entry.getKey();
            if (!name.equals("Empty") && !rankings.contains(name)) {
                rankings.add(name);
            }
        }
        
        return rankings;
    }
    
    // Calculate rankings for repechage mode with parallel consolation trees
    private java.util.List<String> calculateRepechageRankings(String[] participantNames, java.util.Map<String, Integer> nameToFinalPos) {
        java.util.List<String> rankings = new java.util.ArrayList<>();
        
        // Map participant name to their final position
        java.util.Map<String, Integer> nameToPosition = new java.util.HashMap<>();
        
        int totalParticipants = participantNames.length;
        for (String name : participantNames) {
            if (name != null && !name.equals("Empty")) {
                nameToPosition.put(name, totalParticipants); // Default to last
            }
        }
        
        // 1. Process main bracket to determine positions 1-2 (and track losers)
        java.util.Map<Integer, java.util.List<String>> mainRoundLosers = new java.util.HashMap<>();
        
        int numMainRounds = koRounds.size();
        for (int r = 0; r < numMainRounds; r++) {
            List<Match> round = koRounds.get(r);
            mainRoundLosers.put(r, new java.util.ArrayList<>());
            
            for (Match m : round) {
                if (m.winner != null && !m.winner.equals("Empty")) {
                    String p1Ref = resolveKORef(m.p1, koRounds, r);
                    String p2Ref = resolveKORef(m.p2, koRounds, r);
                    String p1Name = getKOName(p1Ref, participantNames);
                    String p2Name = getKOName(p2Ref, participantNames);
                    
                    String loser = m.winner.equals(p1Name) ? p2Name : p1Name;
                    if (!loser.equals("Empty")) {
                        mainRoundLosers.get(r).add(loser);
                    }
                    
                    // Final match
                    if (r == numMainRounds - 1) {
                        nameToPosition.put(m.winner, 1);
                        if (!loser.equals("Empty")) {
                            nameToPosition.put(loser, 2);
                        }
                    }
                }
            }
        }
        
        // 2. Process each repechage tree to determine positions
        for (RepechageTree tree : allRepechageTrees) {
            if (tree.treeId.equals("R1")) continue; // Skip main tree
            processRepechageTreeRankings(tree, participantNames, nameToPosition, mainRoundLosers);
        }
        
        // 3. Build final rankings from nameToPosition map
        java.util.List<java.util.Map.Entry<String, Integer>> sortedEntries = 
            new java.util.ArrayList<>(nameToPosition.entrySet());
        
        sortedEntries.sort((a, b) -> {
            int posA = a.getValue();
            int posB = b.getValue();
            if (posA != posB) {
                return Integer.compare(posA, posB); // Lower position = better
            }
            // Tiebreaker: use FinalPos from Merged (lower = better)
            int mergedPosA = nameToFinalPos.getOrDefault(a.getKey(), 999);
            int mergedPosB = nameToFinalPos.getOrDefault(b.getKey(), 999);
            return Integer.compare(mergedPosA, mergedPosB);
        });
        
        for (java.util.Map.Entry<String, Integer> e : sortedEntries) {
            if (!e.getKey().equals("Empty")) {
                rankings.add(e.getKey());
            }
        }
        
        return rankings;
    }
    
    // Process a repechage tree to determine rankings for its position range
    private void processRepechageTreeRankings(RepechageTree tree, String[] participantNames,
                                                 java.util.Map<String, Integer> nameToPosition,
                                                 java.util.Map<Integer, java.util.List<String>> mainRoundLosers) {
        if (tree.rounds.isEmpty()) return;
        
        int posStart = tree.positionStart;
        int posEnd = tree.positionEnd;
        int posRange = posEnd - posStart + 1;
        
        // Track who wins/loses at each round in this tree
        java.util.List<String> currentParticipants = new java.util.ArrayList<>();
        
        // Initialize with losers from parent bracket
        // For direct children of main tree, get losers from main bracket round
        if (tree.parentTreeId != null && tree.parentTreeId.equals("R1") && mainRoundLosers.containsKey(tree.parentRound)) {
            currentParticipants.addAll(mainRoundLosers.get(tree.parentRound));
        }
        
        // Process each round of the repechage tree
        for (int r = 0; r < tree.rounds.size(); r++) {
            List<Match> round = tree.rounds.get(r);
            java.util.List<String> roundWinners = new java.util.ArrayList<>();
            java.util.List<String> roundLosers = new java.util.ArrayList<>();
            
            for (Match m : round) {
                String winner = m.winner;
                if (winner != null && !winner.equals("Empty") && 
                    !winner.startsWith("L_") && !winner.startsWith("W_") && !winner.startsWith("ML")) {
                    // Resolve participants
                    String p1Name = resolveRepechageParticipant(m.p1, participantNames, tree);
                    String p2Name = resolveRepechageParticipant(m.p2, participantNames, tree);
                    
                    String loser = winner.equals(p1Name) ? p2Name : p1Name;
                    
                    if (!winner.equals("Empty")) roundWinners.add(winner);
                    if (!loser.equals("Empty")) roundLosers.add(loser);
                }
            }
            
            // Losers from this round get positions in the worse half of remaining range
            if (!roundLosers.isEmpty()) {
                int losersStartPos = posStart + (posRange / 2);
                int losersPerPosition = posRange / (2 * roundLosers.size());
                if (losersPerPosition < 1) losersPerPosition = 1;
                
                for (int i = 0; i < roundLosers.size(); i++) {
                    String loser = roundLosers.get(i);
                    int pos = losersStartPos + i;
                    if (pos <= posEnd) {
                        nameToPosition.put(loser, pos);
                    }
                }
                
                // Shrink position range for winners
                posEnd = losersStartPos - 1;
                posRange = posEnd - posStart + 1;
            }
            
            currentParticipants = roundWinners;
        }
        
        // Final winner(s) get best remaining position(s)
        for (int i = 0; i < currentParticipants.size() && posStart + i <= posEnd; i++) {
            nameToPosition.put(currentParticipants.get(i), posStart + i);
        }
    }
    
    // Resolve a repechage tree participant reference to actual name
    private String resolveRepechageParticipant(String ref, String[] participantNames, RepechageTree tree) {
        if (ref == null) return "Empty";
        
        // Handle new L_/W_ references
        if (ref.startsWith("L_")) {
            return resolveRepechageLoserRef(ref, participantNames);
        }
        if (ref.startsWith("W_")) {
            return resolveRepechageWinnerRef(ref, participantNames);
        }
        
        // If it's already a name (not a reference), return it
        if (!ref.startsWith("ML")) {
            // Try to find in participantNames
            for (String name : participantNames) {
                if (ref.equals(name)) return name;
            }
            return ref;
        }
        
        // ML references: loser from main bracket
        if (ref.startsWith("ML")) {
            // Format: ML{round}_{index}
            try {
                String[] parts = ref.substring(2).split("_");
                int round = Integer.parseInt(parts[0]) - 1;
                int idx = Integer.parseInt(parts[1]) - 1;
                
                if (round >= 0 && round < koRounds.size()) {
                    List<Match> mainRound = koRounds.get(round);
                    if (idx >= 0 && idx < mainRound.size()) {
                        Match m = mainRound.get(idx);
                        if (m.winner != null) {
                            String p1Ref = resolveKORef(m.p1, koRounds, round);
                            String p2Ref = resolveKORef(m.p2, koRounds, round);
                            String p1Name = getKOName(p1Ref, participantNames);
                            String p2Name = getKOName(p2Ref, participantNames);
                            return m.winner.equals(p1Name) ? p2Name : p1Name; // Return loser
                        }
                    }
                }
            } catch (Exception e) {}
        }
        
        return "Empty";
    }
    
    // Load FinalPos from Merged_backup.csv for tiebreaking in rankings
    private java.util.Map<String, Integer> loadFinalPosFromMerged() {
        java.util.Map<String, Integer> nameToFinalPos = new java.util.HashMap<>();
        try {
            java.io.File filesDir = requireContext().getFilesDir();
            java.io.File backupFile = new java.io.File(filesDir, "Merged_backup.csv");
            if (!backupFile.exists()) {
                return nameToFinalPos;
            }
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
            reader.readLine(); // Skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 9) {
                    String name = parts[1].trim();
                    String finalPosStr = parts[8].trim();
                    if (!name.isEmpty() && !finalPosStr.isEmpty()) {
                        try {
                            int finalPos = Integer.parseInt(finalPosStr);
                            nameToFinalPos.put(name, finalPos);
                        } catch (Exception e) {}
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            android.util.Log.w("KOFragment", "Could not load FinalPos from Merged: " + e.getMessage());
        }
        return nameToFinalPos;
    }

    // Render KO table: header and participant rows (editable)
    private void renderKOTable(LinearLayout koBoxLayout) {
        koBoxLayout.removeAllViews();
        String[] origNames = getKOParticipantNames();
        // Always use the full participant array length, not filtered or sorted names, for KO tree
        int nrPart = (origNames != null) ? origNames.length : ScoresViewModel.DEFAULT_PARTICIPANTS;
        if (origNames == null || origNames.length == 0) return;
        int N = 1;
        while (N < nrPart) N *= 2;
        int koSize = N;
        // Read FinalPos from Merged_backup.csv and sort names accordingly
        java.util.List<String> sortedNames = new java.util.ArrayList<>();
        try {
            java.io.File filesDir = requireContext().getFilesDir();
            java.io.File backupFile = new java.io.File(filesDir, "Merged_backup.csv");
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
            String line;
            reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 9) {
                    rows.add(parts);
                }
            }
            reader.close();
            // Sort rows by FinalPos (column 8, 0-based), randomizing ties
            java.util.Random rand = new java.util.Random();
            rows.sort((a, b) -> {
                try {
                    int posa = Integer.parseInt(a[8]);
                    int posb = Integer.parseInt(b[8]);
                    if (posa != posb) return Integer.compare(posa, posb);
                    // If tied, randomize order
                    return rand.nextInt(3) - 1; // -1, 0, or 1
                } catch (Exception e) { return 0; }
            });
            for (String[] row : rows) {
                String name = row[1];
                if (name != null && !name.trim().isEmpty()) {
                    sortedNames.add(name);
                }
            }
        } catch (Exception e) {
            // fallback: use origNames order
            for (String name : origNames) {
                if (name != null && !name.trim().isEmpty()) sortedNames.add(name);
            }
        }
        String[] participantNames = new String[koSize];
        for (int i = 0; i < koSize; i++) {
            if (i < sortedNames.size()) participantNames[i] = sortedNames.get(i);
            else participantNames[i] = "Empty";
        }
        int[] positions = new int[koSize];
        // Map name -> FinalPos for lookup by name (used in later rounds)
        final java.util.Map<String, Integer> nameToPosition = new java.util.HashMap<>();
        try {
            java.io.File filesDir = requireContext().getFilesDir();
            java.io.File backupFile = new java.io.File(filesDir, "Merged_backup.csv");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
            String line;
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 9) {
                    String name = parts[1];
                    int pos = 0;
                    try { pos = Integer.parseInt(parts[8]); } catch (Exception e) { pos = 0; }
                    nameToPosition.put(name, pos);
                }
            }
            reader.close();
            for (int i = 0; i < koSize; i++) {
                positions[i] = nameToPosition.getOrDefault(participantNames[i], 0);
            }
        } catch (Exception e) {
            for (int i = 0; i < koSize; i++) positions[i] = 0;
        }
        if (koRounds.isEmpty()) {
            loadKOTree(nrPart, koRepechage);
            autoAdvanceEmptyMatches(participantNames);
            if (koRepechage && !losersRounds.isEmpty()) {
                propagateLosersToLosersBracket(participantNames);
                autoAdvanceEmptyMatchesInLosersBracket(participantNames);
            }
        }
        // Pixel-based KO box positioning
        float density = getResources().getDisplayMetrics().density;
        int boxHeightPx = (int) (32 * density); // 32dp
        int verticalSpacingPx = (int) (16 * density); // 16dp
        int colorIdx = 0;
        try {
            colorIdx = scoresViewModel.getColorCycleIndex().getValue() != null ? scoresViewModel.getColorCycleIndex().getValue() : 0;
        } catch (Exception e) {}
        int[] colorPair = RESULT_COLOR_PAIRS[colorIdx % RESULT_COLOR_PAIRS.length];
        int colorTop = colorPair[0];
        int colorBottom = colorPair[1];
        // Track the pixel y-position for each match in each round
        java.util.List<java.util.List<Float>> matchYPixels = new java.util.ArrayList<>();
        java.util.List<java.util.List<Button>> matchButtons = new java.util.ArrayList<>();
        for (int r = 0; r < koRounds.size(); r++) {
            int matches = koRounds.get(r).size();
            java.util.List<Float> roundYPixels = new java.util.ArrayList<>();
            java.util.List<Button> roundButtons = new java.util.ArrayList<>();
            int smallSpacingPx = (int) (2 * density); // 2dp for minimal separation
            for (int m = 0; m < matches; m++) {
                float yPx;
                if (r == 0) {
                    // First round: boxes stacked with just their height plus a few pixels
                    yPx = m * (boxHeightPx + smallSpacingPx);
                } else {
                    // For round >=2: yPx is the average of the absolute y positions of the two source boxes in the previous round
                    java.util.List<Float> prevYPixels = matchYPixels.get(r - 1);
                    int src1 = m * 2;
                    int src2 = m * 2 + 1;
                    float prev1 = (src1 < prevYPixels.size()) ? prevYPixels.get(src1) : 0f;
                    float prev2 = (src2 < prevYPixels.size()) ? prevYPixels.get(src2) : prev1;
                    yPx = (prev1 + prev2) / 2f;
                }
                roundYPixels.add(yPx);
                Match match = koRounds.get(r).get(m);
                Button matchBtn = new Button(getContext());
                // Resolve winner/loser references first, then look up names (pass current round)
                String p1Ref = resolveKORef(match.p1, koRounds, r);
                String p2Ref = resolveKORef(match.p2, koRounds, r);
                // Convert numeric refs to actual names from participantNames
                String p1Name = getKOName(p1Ref, participantNames);
                String p2Name = getKOName(p2Ref, participantNames);
                if (r == 0) {
                    android.util.Log.d("KOFragment", "RENDER R1 Match " + m + ": match.p1=" + match.p1 + " -> p1Ref=" + p1Ref + " -> p1Name=" + p1Name);
                    android.util.Log.d("KOFragment", "RENDER R1 Match " + m + ": match.p2=" + match.p2 + " -> p2Ref=" + p2Ref + " -> p2Name=" + p2Name);
                }
                // Look up FinalPos by name (works for all rounds, not just round 1)
                int p1Pos = nameToPosition.getOrDefault(p1Name, 0);
                int p2Pos = nameToPosition.getOrDefault(p2Name, 0);
                String label;
                String posShadow = "font-weight:bold;text-shadow:2px 2px 4px #222;";
                if (match.score1 >= 0 && match.score2 >= 0) {
                    boolean p1Winner = match.score1 > match.score2;
                    boolean p2Winner = match.score2 > match.score1;
                    String p1Color = p1Winner ? "#1976D2" : "#D32F2F";
                    String p2Color = p2Winner ? "#1976D2" : "#D32F2F";
                    String p1PosSpan = (p1Pos > 0) ? "<span style='color:" + p1Color + ";" + posShadow + "'>" + p1Pos + "</span> " : "";
                    String p2PosSpan = (p2Pos > 0) ? "<span style='color:" + p2Color + ";" + posShadow + "'>" + p2Pos + "</span> " : "";
                    String p1NameSpan = "<span style='color:" + p1Color + "'>" + p1Name.toUpperCase() + "</span>";
                    String p2NameSpan = "<span style='color:" + p2Color + "'>" + p2Name.toUpperCase() + "</span>";
                    String resultSpan = "<span style='color:#222;" + posShadow + "'> " + match.score1 + ":" + match.score2 + "</span>";
                    label = p1PosSpan + p1NameSpan + " Vs " + p2PosSpan + p2NameSpan + resultSpan;
                } else {
                    String p1Label = (p1Pos > 0) ? "<b style='text-shadow:2px 2px 4px #222'>" + p1Pos + "</b> " + p1Name.toUpperCase() : p1Name.toUpperCase();
                    String p2Label = (p2Pos > 0) ? "<b style='text-shadow:2px 2px 4px #222'>" + p2Pos + "</b> " + p2Name.toUpperCase() : p2Name.toUpperCase();
                    label = p1Label + " Vs " + p2Label + " <b>x:x</b>";
                }
                matchBtn.setAllCaps(false);
                matchBtn.setText(android.text.Html.fromHtml(label));
                matchBtn.setSingleLine(true);
                matchBtn.setPadding(8, 0, 8, 0);
                matchBtn.setIncludeFontPadding(false);
                matchBtn.setGravity(android.view.Gravity.CENTER);
                matchBtn.setOnClickListener(v -> showMatchDialog(match, participantNames));
                matchBtn.setOnLongClickListener(v -> {
                    navigateToFinalPage();
                    return true;
                });
                float ratio = (koRounds.size() <= 1) ? 0f : (float)r / (float)(koRounds.size() - 1);
                int bgColor = interpolateColor(colorTop, colorBottom, ratio);
                matchBtn.setBackgroundColor(bgColor);
                TableRow.LayoutParams params = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT, boxHeightPx);
                params.setMargins(r * 32, 0, 0, 0); // Shift right by 32px per round
                matchBtn.setLayoutParams(params);
                roundButtons.add(matchBtn);
            }
            matchYPixels.add(roundYPixels);
            matchButtons.add(roundButtons);
        }
        // Create horizontal container for round columns
        LinearLayout columnsContainer = new LinearLayout(getContext());
        columnsContainer.setOrientation(LinearLayout.HORIZONTAL);
        columnsContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int totalRounds = matchButtons.size();
        int defaultBoxWidthPx = (int) (120 * density);
        int columnSpacingPx = (int) (8 * density);

        // Calculate width for Round 1 only, then use it for ALL rounds
        int[] roundWidth = new int[totalRounds];
        int round1Width = defaultBoxWidthPx;
        
        // Calculate Round 1 width
        if (totalRounds > 0) {
            java.util.List<Button> round1Btns = matchButtons.get(0);
            int widest = 0;
            for (Button btn : round1Btns) {
                btn.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int w = btn.getMeasuredWidth();
                if (w > widest) widest = w;
            }
            // Add space for 2 characters
            int extra = 0;
            if (!round1Btns.isEmpty()) {
                float textSizePx = round1Btns.get(0).getTextSize();
                extra = (int) (2 * 0.6f * textSizePx);
            }
            round1Width = Math.max(widest + extra, defaultBoxWidthPx);
        }
        
        // Use Round 1 width for ALL rounds
        for (int r = 0; r < totalRounds; r++) {
            roundWidth[r] = round1Width;
        }

        // Build each round as a separate vertical column
        for (int r = 0; r < totalRounds; r++) {
            LinearLayout roundColumn = new LinearLayout(getContext());
            roundColumn.setOrientation(LinearLayout.VERTICAL);
            
            // Add header for this round
            TextView header = new TextView(getContext());
            header.setText("Round " + (r + 1));
            header.setGravity(Gravity.CENTER);
            header.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
            header.setPadding(8, 4, 8, 4);
            roundColumn.addView(header);

            java.util.List<Button> roundBtns = matchButtons.get(r);
            java.util.List<Float> roundYPixels = matchYPixels.get(r);
            
            // Track the bottom of the previous button in this column
            float prevBottomY = 0;
            
            for (int m = 0; m < roundBtns.size(); m++) {
                Button btn = roundBtns.get(m);
                float yPx = roundYPixels.get(m);
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    roundWidth[r], boxHeightPx);
                
                // Calculate top margin: distance from previous button's bottom to this button's top
                int topMargin;
                if (m == 0) {
                    topMargin = (int) yPx;
                } else {
                    // yPx is absolute position; prevBottomY is where previous button ended
                    topMargin = (int) (yPx - prevBottomY);
                    if (topMargin < 0) topMargin = 0;
                }
                params.setMargins(0, topMargin, columnSpacingPx, 0);
                btn.setLayoutParams(params);
                roundColumn.addView(btn);
                
                // Update prevBottomY to include this button's height
                prevBottomY = yPx + boxHeightPx;
            }
            
            columnsContainer.addView(roundColumn);
        }
        
        koBoxLayout.addView(columnsContainer);
        
        // If repechage mode, render losers bracket below main bracket  
        if (koRepechage && !losersRounds.isEmpty()) {
            renderLosersBracket(koBoxLayout, participantNames, nameToPosition, boxHeightPx, density, colorTop, colorBottom, roundWidth);
        }
    }
    
    // Render losers bracket for repechage mode with tree groupings
    private void renderLosersBracket(LinearLayout koBoxLayout, String[] participantNames, 
            java.util.Map<String, Integer> nameToPosition, int boxHeightPx, float density,
            int colorTop, int colorBottom, int[] mainBracketRoundWidths) {
        
        // Add separator
        View separator = new View(getContext());
        separator.setBackgroundColor(0xFF888888);
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (int)(2 * density));
        sepParams.setMargins(0, (int)(16 * density), 0, (int)(8 * density));
        separator.setLayoutParams(sepParams);
        koBoxLayout.addView(separator);
        
        // Add losers bracket header
        TextView losersHeader = new TextView(getContext());
        losersHeader.setText("CONSOLATION BRACKET (Repechage)");
        losersHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        losersHeader.setTextSize(16);
        losersHeader.setGravity(Gravity.CENTER);
        losersHeader.setPadding(8, 8, 8, 8);
        losersHeader.setBackgroundColor(0xFF333333);
        losersHeader.setTextColor(0xFFFFFFFF);
        koBoxLayout.addView(losersHeader);
        
        // Organize matches by displayColumn, then by treeId
        java.util.Map<Integer, java.util.Map<String, List<Match>>> columnTreeMatches = new java.util.TreeMap<>();
        int maxColumn = -1;
        
        // Also track matches by tree and round for Y position calculation
        java.util.Map<String, java.util.Map<Integer, List<Match>>> treeRoundMatches = new java.util.LinkedHashMap<>();
        
        for (RepechageTree tree : allRepechageTrees) {
            if (tree.treeId.equals("R1")) continue;
            
            treeRoundMatches.put(tree.treeId, new java.util.TreeMap<>());
            
            for (int r = 0; r < tree.rounds.size(); r++) {
                List<Match> round = tree.rounds.get(r);
                treeRoundMatches.get(tree.treeId).put(r, round);
                
                for (Match m : round) {
                    int col = m.displayColumn;
                    if (col > maxColumn) maxColumn = col;
                    columnTreeMatches
                        .computeIfAbsent(col, k -> new java.util.LinkedHashMap<>())
                        .computeIfAbsent(tree.treeId, k -> new ArrayList<>())
                        .add(m);
                }
            }
        }
        
        if (maxColumn < 0) return; // No losers matches
        
        int minBoxWidthPx = (int) (100 * density);
        int columnSpacingPx = (int) (8 * density);
        int smallSpacingPx = (int) (2 * density);
        int treeSeparationPx = (int) (16 * density);
        float textSizeSp = 14f; // Same text size as winners bracket
        
        // Use Round 1 width from main bracket for ALL losers columns
        // This ensures consistent box sizing across all repechage trees
        int round1Width = (mainBracketRoundWidths != null && mainBracketRoundWidths.length > 0) 
            ? mainBracketRoundWidths[0] : minBoxWidthPx;
        
        java.util.Map<Integer, Integer> columnWidths = new java.util.HashMap<>();
        for (int col = 1; col <= maxColumn; col++) {
            columnWidths.put(col, round1Width);
        }
        
        // Pre-calculate Y positions for each match in each tree (bracket-style alignment)
        // Key: treeId -> roundIdx -> matchIdx -> yPosition
        java.util.Map<String, java.util.Map<Integer, java.util.Map<Integer, Float>>> treeMatchYPositions = new java.util.HashMap<>();
        
        for (java.util.Map.Entry<String, java.util.Map<Integer, List<Match>>> treeEntry : treeRoundMatches.entrySet()) {
            String treeId = treeEntry.getKey();
            java.util.Map<Integer, List<Match>> roundsMap = treeEntry.getValue();
            java.util.Map<Integer, java.util.Map<Integer, Float>> treeYPos = new java.util.HashMap<>();
            
            for (int r = 0; r < roundsMap.size(); r++) {
                List<Match> round = roundsMap.get(r);
                java.util.Map<Integer, Float> roundYPos = new java.util.HashMap<>();
                
                for (int m = 0; m < round.size(); m++) {
                    float yPx;
                    if (r == 0) {
                        // First round: boxes stacked sequentially
                        yPx = m * (boxHeightPx + smallSpacingPx);
                    } else {
                        // Subsequent rounds: center between source matches from previous round
                        java.util.Map<Integer, Float> prevRoundY = treeYPos.get(r - 1);
                        if (prevRoundY != null) {
                            int src1 = m * 2;
                            int src2 = m * 2 + 1;
                            Float y1 = prevRoundY.get(src1);
                            Float y2 = prevRoundY.get(src2);
                            float prev1 = (y1 != null) ? y1 : 0f;
                            float prev2 = (y2 != null) ? y2 : prev1;
                            yPx = (prev1 + prev2) / 2f;
                        } else {
                            yPx = m * (boxHeightPx + smallSpacingPx);
                        }
                    }
                    roundYPos.put(m, yPx);
                }
                treeYPos.put(r, roundYPos);
            }
            treeMatchYPositions.put(treeId, treeYPos);
        }
        
        // Calculate cumulative Y offset for each tree (trees stack below each other)
        java.util.Map<String, Float> treeYOffsets = new java.util.HashMap<>();
        float cumulativeOffset = 0;
        for (String treeId : treeRoundMatches.keySet()) {
            treeYOffsets.put(treeId, cumulativeOffset);
            
            // Calculate max height of this tree
            java.util.Map<Integer, java.util.Map<Integer, Float>> treeYPos = treeMatchYPositions.get(treeId);
            float maxY = 0;
            if (treeYPos != null) {
                for (java.util.Map<Integer, Float> roundY : treeYPos.values()) {
                    for (Float y : roundY.values()) {
                        if (y != null && y > maxY) maxY = y;
                    }
                }
            }
            cumulativeOffset += maxY + boxHeightPx + treeSeparationPx;
        }
        
        // Create horizontal container for columns
        LinearLayout losersContainer = new LinearLayout(getContext());
        losersContainer.setOrientation(LinearLayout.HORIZONTAL);
        losersContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // Add empty column for R1 alignment (no losers in R1)
        LinearLayout emptyR1Column = new LinearLayout(getContext());
        emptyR1Column.setOrientation(LinearLayout.VERTICAL);
        TextView emptyHeader = new TextView(getContext());
        emptyHeader.setText("|| R1");
        emptyHeader.setGravity(Gravity.CENTER);
        emptyHeader.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        emptyHeader.setTextColor(0xFF666666);
        emptyHeader.setPadding(8, 4, 8, 4);
        emptyR1Column.addView(emptyHeader);
        // Empty placeholder with R1 width
        View emptyPlaceholder = new View(getContext());
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(round1Width, boxHeightPx);
        emptyParams.setMargins(0, 0, columnSpacingPx, 0);
        emptyPlaceholder.setLayoutParams(emptyParams);
        emptyR1Column.addView(emptyPlaceholder);
        losersContainer.addView(emptyR1Column);
        
        // Color palette for different trees - start at RGB(200,100,100), increase saturation
        // Each tree gets progressively more saturated red while keeping luminosity
        int[] treeColors = {
            0xFFC86464, // RGB(200,100,100) - base reddish
            0xFFD05050, // More saturated
            0xFFD83C3C, // More saturated
            0xFFE02828, // More saturated
            0xFFE81414, // High saturation
            0xFFF00000  // Full red
        };
        java.util.Map<String, Integer> treeColorMap = new java.util.HashMap<>();
        int colorIdx = 0;
        
        // Build each column using FrameLayout for bracket-style Y positioning
        for (int col = 1; col <= maxColumn; col++) {
            // Use vertical LinearLayout container with FrameLayout for match positioning
            LinearLayout colContainer = new LinearLayout(getContext());
            colContainer.setOrientation(LinearLayout.VERTICAL);
            
            // Column header showing alignment with main bracket
            TextView colHeader = new TextView(getContext());
            colHeader.setText("|| R" + (col + 1));
            colHeader.setGravity(Gravity.CENTER);
            colHeader.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
            colHeader.setTextColor(0xFF666666);
            colHeader.setPadding(8, 4, 8, 4);
            colContainer.addView(colHeader);
            
            java.util.Map<String, List<Match>> treesInCol = columnTreeMatches.get(col);
            int colWidthPx = columnWidths.getOrDefault(col, minBoxWidthPx);
            
            if (treesInCol == null || treesInCol.isEmpty()) {
                // Empty column placeholder
                View placeholder = new View(getContext());
                LinearLayout.LayoutParams phParams = new LinearLayout.LayoutParams(colWidthPx, boxHeightPx);
                phParams.setMargins(0, 0, columnSpacingPx, 0);
                placeholder.setLayoutParams(phParams);
                colContainer.addView(placeholder);
                losersContainer.addView(colContainer);
                continue;
            }
            
            // Calculate total height needed for this column
            float maxTotalY = 0;
            for (String treeId : treesInCol.keySet()) {
                Float treeOffset = treeYOffsets.get(treeId);
                java.util.Map<Integer, java.util.Map<Integer, Float>> treeYPos = treeMatchYPositions.get(treeId);
                if (treeOffset != null && treeYPos != null) {
                    for (java.util.Map<Integer, Float> roundY : treeYPos.values()) {
                        for (Float y : roundY.values()) {
                            if (y != null) {
                                float totalY = treeOffset + y + boxHeightPx;
                                if (totalY > maxTotalY) maxTotalY = totalY;
                            }
                        }
                    }
                }
            }
            
            // Create FrameLayout for absolute positioning of matches
            android.widget.FrameLayout matchFrame = new android.widget.FrameLayout(getContext());
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                colWidthPx, (int) maxTotalY + (int)(16 * density));
            frameParams.setMargins(0, 0, columnSpacingPx, 0);
            matchFrame.setLayoutParams(frameParams);
            
            for (java.util.Map.Entry<String, List<Match>> entry : treesInCol.entrySet()) {
                String treeId = entry.getKey();
                List<Match> matches = entry.getValue();
                
                // Assign color to this tree if not already assigned
                if (!treeColorMap.containsKey(treeId)) {
                    treeColorMap.put(treeId, treeColors[colorIdx % treeColors.length]);
                    colorIdx++;
                }
                int treeColor = treeColorMap.get(treeId);
                
                Float treeYOffset = treeYOffsets.get(treeId);
                if (treeYOffset == null) treeYOffset = 0f;
                
                // Add tree header at tree's Y offset
                TextView treeHeader = new TextView(getContext());
                // Tree IDs like R1L, R1W2L, R1L2L are already readable
                treeHeader.setText(treeId);
                treeHeader.setTextSize(9);
                treeHeader.setGravity(Gravity.START);
                treeHeader.setTextColor(treeColor);
                treeHeader.setTypeface(null, android.graphics.Typeface.ITALIC);
                treeHeader.setPadding(2, 0, 2, 0);
                android.widget.FrameLayout.LayoutParams headerParams = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                headerParams.topMargin = (int) (treeYOffset - (int)(14 * density));
                if (headerParams.topMargin < 0) headerParams.topMargin = 0;
                headerParams.leftMargin = 0;
                treeHeader.setLayoutParams(headerParams);
                // Only add header if there's room (Y offset > 0 or first tree)
                if (treeYOffset > 10 * density) {
                    matchFrame.addView(treeHeader);
                }
                
                // Add match buttons for this tree
                for (int m = 0; m < matches.size(); m++) {
                    Match match = matches.get(m);
                    Button matchBtn = new Button(getContext());
                    
                    String p1Name = getLosersName(match.p1, participantNames);
                    String p2Name = getLosersName(match.p2, participantNames);
                    
                    // Check if names are resolved (actual participant names) or placeholders
                    boolean p1IsPlaceholder = isPlaceholderName(p1Name);
                    boolean p2IsPlaceholder = isPlaceholderName(p2Name);
                    boolean hasPlaceholder = p1IsPlaceholder || p2IsPlaceholder;
                    
                    int p1Pos = nameToPosition.getOrDefault(p1Name, 0);
                    int p2Pos = nameToPosition.getOrDefault(p2Name, 0);
                    
                    String label;
                    String posShadow = "font-weight:bold;text-shadow:2px 2px 4px #222;";
                    if (match.score1 >= 0 && match.score2 >= 0) {
                        boolean p1Winner = match.score1 > match.score2;
                        boolean p2Winner = match.score2 > match.score1;
                        String p1Color = p1Winner ? "#1976D2" : "#D32F2F";
                        String p2Color = p2Winner ? "#1976D2" : "#D32F2F";
                        String p1PosSpan = (p1Pos > 0) ? "<span style='color:" + p1Color + ";" + posShadow + "'>" + p1Pos + "</span> " : "";
                        String p2PosSpan = (p2Pos > 0) ? "<span style='color:" + p2Color + ";" + posShadow + "'>" + p2Pos + "</span> " : "";
                        String p1NameSpan = "<span style='color:" + p1Color + "'>" + p1Name.toUpperCase() + "</span>";
                        String p2NameSpan = "<span style='color:" + p2Color + "'>" + p2Name.toUpperCase() + "</span>";
                        String resultSpan = "<span style='color:#222;" + posShadow + "'> " + match.score1 + ":" + match.score2 + "</span>";
                        label = p1PosSpan + p1NameSpan + " Vs " + p2PosSpan + p2NameSpan + resultSpan;
                    } else {
                        String p1Label = (p1Pos > 0) ? "<b style='text-shadow:2px 2px 4px #222'>" + p1Pos + "</b> " + p1Name.toUpperCase() : p1Name.toUpperCase();
                        String p2Label = (p2Pos > 0) ? "<b style='text-shadow:2px 2px 4px #222'>" + p2Pos + "</b> " + p2Name.toUpperCase() : p2Name.toUpperCase();
                        label = p1Label + " Vs " + p2Label + " <b>x:x</b>";
                    }
                    
                    matchBtn.setText(android.text.Html.fromHtml(label));
                    // Use same text size as winners bracket for all buttons
                    matchBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp);
                    matchBtn.setSingleLine(true);
                    matchBtn.setPadding(8, 0, 8, 0);
                    matchBtn.setIncludeFontPadding(false);
                    matchBtn.setGravity(android.view.Gravity.CENTER);
                    final Match finalMatch = match;
                    final String[] finalNames = participantNames;
                    matchBtn.setOnClickListener(v -> showMatchDialog(finalMatch, finalNames));
                    matchBtn.setOnLongClickListener(v -> {
                        navigateToFinalPage();
                        return true;
                    });
                    matchBtn.setBackgroundColor(brightenColor(treeColor, 0.25f));
                    
                    // Calculate Y position for this match using pre-computed values
                    float matchY = 0;
                    java.util.Map<Integer, java.util.Map<Integer, Float>> treeYPos = treeMatchYPositions.get(treeId);
                    if (treeYPos != null) {
                        // Find which round this match belongs to in the tree
                        RepechageTree tree = findTreeById(treeId);
                        if (tree != null) {
                            for (int r = 0; r < tree.rounds.size(); r++) {
                                List<Match> round = tree.rounds.get(r);
                                for (int mi = 0; mi < round.size(); mi++) {
                                    if (round.get(mi) == match) {
                                        java.util.Map<Integer, Float> roundY = treeYPos.get(r);
                                        if (roundY != null && roundY.containsKey(mi)) {
                                            matchY = roundY.get(mi);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                        colWidthPx, boxHeightPx);
                    params.topMargin = (int) (treeYOffset + matchY);
                    params.leftMargin = 0;
                    matchBtn.setLayoutParams(params);
                    matchFrame.addView(matchBtn);
                }
            }
            
            colContainer.addView(matchFrame);
            losersContainer.addView(colContainer);
        }
        
        // Add Grand Final column if set
        if (grandFinalMatch != null) {
            LinearLayout gfColumn = new LinearLayout(getContext());
            gfColumn.setOrientation(LinearLayout.VERTICAL);
            
            TextView gfHeader = new TextView(getContext());
            gfHeader.setText("Grand Final");
            gfHeader.setGravity(Gravity.CENTER);
            gfHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            gfHeader.setPadding(8, 4, 8, 4);
            gfColumn.addView(gfHeader);
            
            Button gfBtn = new Button(getContext());
            String gfP1 = getLosersName(grandFinalMatch.p1, participantNames).toUpperCase();
            String gfP2 = getLosersName(grandFinalMatch.p2, participantNames).toUpperCase();
            String gfLabel;
            if (grandFinalMatch.score1 >= 0 && grandFinalMatch.score2 >= 0) {
                gfLabel = gfP1 + " Vs " + gfP2 + " " + grandFinalMatch.score1 + ":" + grandFinalMatch.score2;
            } else {
                gfLabel = gfP1 + " Vs " + gfP2 + " x:x";
            }
            gfBtn.setText(gfLabel);
            gfBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            gfBtn.setSingleLine(true);
            gfBtn.setPadding(8, 0, 8, 0);
            gfBtn.setIncludeFontPadding(false);
            gfBtn.setGravity(android.view.Gravity.CENTER);
            gfBtn.setBackgroundColor(0xFFFFD700); // Gold for Grand Final
            gfBtn.setOnClickListener(v -> showMatchDialog(grandFinalMatch, participantNames));
            gfBtn.setOnLongClickListener(v -> {
                navigateToFinalPage();
                return true;
            });
            
            // Measure Grand Final button width
            android.graphics.Paint gfPaint = new android.graphics.Paint();
            gfPaint.setTextSize(textSizeSp * density);
            int gfWidth = Math.max((int) gfPaint.measureText(gfLabel) + (int)(32 * density), minBoxWidthPx);
            
            LinearLayout.LayoutParams gfParams = new LinearLayout.LayoutParams(
                gfWidth, boxHeightPx);
            gfParams.setMargins(0, (int)(16 * density), columnSpacingPx, 0);
            gfBtn.setLayoutParams(gfParams);
            gfColumn.addView(gfBtn);
            
            losersContainer.addView(gfColumn);
        }
        
        koBoxLayout.addView(losersContainer);
    }
    
    // Get name for losers bracket participant (handles L_*, W_*, ML*, LW* references)
    // Returns shortened format for unresolved refs: L_R1_R1_2 -> "L1.2", W_R1L_R1_1 -> "W1.1"
    private String getLosersName(String ref, String[] participantNames) {
        if (ref == null) return "Empty";
        if (ref.equals("Empty")) return "Empty";
        if (ref.equals("-")) return "Empty";
        if (ref.equals("MW")) return "MW";
        if (ref.equals("LBW")) return "LW";
        if (ref.equals("GFL")) return "GFL";
        if (ref.equals("LFL")) return "LFL";
        
        // New format: L_{treeId}_R{round}_{matchIdx} - loser from tree's round
        if (ref.startsWith("L_")) {
            String resolved = resolveRepechageLoserRef(ref, participantNames);
            // If still unresolved (returned ref), shorten it
            if (resolved.equals(ref)) {
                return shortenLoserRef(ref);
            }
            return resolved;
        }
        
        // New format: W_{treeId}_R{round}_{matchIdx} - winner from tree's round
        if (ref.startsWith("W_")) {
            String resolved = resolveRepechageWinnerRef(ref, participantNames);
            if (resolved.equals(ref)) {
                return shortenWinnerRef(ref);
            }
            return resolved;
        }
        
        // Old ML references: ML{round}_{matchIdx}
        if (ref.startsWith("ML")) {
            String resolved = resolveMainLoserRef(ref, participantNames);
            if (resolved.equals(ref)) {
                // Shorten ML1_2 -> "L1.2"
                return "L" + ref.substring(2).replace("_", ".");
            }
            return resolved;
        }
        
        // LW references still unresolved  
        if (ref.startsWith("LW")) return "W" + ref.substring(2).replace("_", ".");
        
        // Try as participant index
        return getKOName(ref, participantNames);
    }
    
    // Shorten L_{treeId}_R{round}_{matchIdx} -> "L{round}.{matchIdx}"
    private String shortenLoserRef(String ref) {
        try {
            // Extract round and match index from the end
            String[] parts = ref.split("_");
            if (parts.length >= 2) {
                String lastPart = parts[parts.length - 1]; // matchIdx
                String secondLast = parts[parts.length - 2]; // R{round}
                if (secondLast.startsWith("R")) {
                    return "L" + secondLast.substring(1) + "." + lastPart;
                }
            }
        } catch (Exception e) {}
        return "L?";
    }
    
    // Shorten W_{treeId}_R{round}_{matchIdx} -> "W{round}.{matchIdx}"
    private String shortenWinnerRef(String ref) {
        try {
            String[] parts = ref.split("_");
            if (parts.length >= 2) {
                String lastPart = parts[parts.length - 1];
                String secondLast = parts[parts.length - 2];
                if (secondLast.startsWith("R")) {
                    return "W" + secondLast.substring(1) + "." + lastPart;
                }
            }
        } catch (Exception e) {}
        return "W?";
    }
    
    // Check if a name is a placeholder (not a real participant name)
    private boolean isPlaceholderName(String name) {
        if (name == null) return true;
        if (name.equals("Empty")) return true;
        if (name.equals("MW") || name.equals("LW") || name.equals("GFL") || name.equals("LFL")) return true;
        // Check for shortened refs like L1.2, W1.1, L?, W?
        if (name.matches("^[LW]\\d+\\.\\d+$")) return true;
        if (name.equals("L?") || name.equals("W?")) return true;
        // Check for unresolved references
        if (name.startsWith("L_") || name.startsWith("W_") || name.startsWith("ML")) return true;
        return false;
    }
    
    // Resolve L_{treeId}_R{round}_{matchIdx} reference to actual loser name
    private String resolveRepechageLoserRef(String ref, String[] participantNames) {
        // Format: L_{treeId}_R{round}_{matchIdx}
        try {
            // Parse: L_R1_R1_1 means loser of R1 tree, round 1, match 1
            String[] parts = ref.split("_");
            if (parts.length < 4) return ref;
            
            String treeId = parts[1];
            // Handle multi-part tree IDs like "C_main_R1" or "C_C_main_R2_R1"
            // Find the LAST R{num} followed by matchIdx (second to last part must be R{num})
            // Format is always: L_{treeId}_R{round}_{matchIdx} where matchIdx is the last part
            int rIdx = parts.length - 2; // Second to last is always R{round}
            if (!parts[rIdx].startsWith("R")) {
                // Fallback: search from end
                rIdx = -1;
                for (int i = parts.length - 2; i >= 2; i--) {
                    if (parts[i].startsWith("R") && parts[i].length() > 1) {
                        try {
                            Integer.parseInt(parts[i].substring(1));
                            rIdx = i;
                            break;
                        } catch (Exception e) {}
                    }
                }
            }
            if (rIdx == -1 || rIdx < 2) return ref;
            
            // Rebuild treeId from parts between index 1 and rIdx
            StringBuilder treeIdBuilder = new StringBuilder(parts[1]);
            for (int i = 2; i < rIdx; i++) {
                treeIdBuilder.append("_").append(parts[i]);
            }
            treeId = treeIdBuilder.toString();
            
            int roundNum = Integer.parseInt(parts[rIdx].substring(1)); // R1 -> 1
            int matchIdx = Integer.parseInt(parts[rIdx + 1]); // 1-based
            
            // Find the tree and match
            RepechageTree tree = findTreeById(treeId);
            if (tree == null) return ref;
            
            int roundIndex = roundNum - 1;
            if (roundIndex < 0 || roundIndex >= tree.rounds.size()) return ref;
            
            List<Match> round = tree.rounds.get(roundIndex);
            int matchOffset = matchIdx - 1; // 0-based
            if (matchOffset < 0 || matchOffset >= round.size()) return ref;
            
            Match match = round.get(matchOffset);
            if (match.winner == null) return ref; // Winner not determined yet
            
            // Return the loser (the one who didn't win)
            String p1Name = treeId.equals("R1") ? 
                getKOName(resolveKORef(match.p1, koRounds, roundIndex), participantNames) :
                getLosersName(match.p1, participantNames);
            String p2Name = treeId.equals("R1") ?
                getKOName(resolveKORef(match.p2, koRounds, roundIndex), participantNames) :
                getLosersName(match.p2, participantNames);
            
            return match.winner.equals(p1Name) ? p2Name : p1Name;
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error resolving loser ref: " + ref, e);
            return ref;
        }
    }
    
    // Resolve W_{treeId}_R{round}_{matchIdx} reference to actual winner name
    private String resolveRepechageWinnerRef(String ref, String[] participantNames) {
        // Format: W_{treeId}_R{round}_{matchIdx}
        try {
            String[] parts = ref.split("_");
            if (parts.length < 4) return ref;
            
            // Find R index - second to last part is always R{round}
            int rIdx = parts.length - 2;
            if (!parts[rIdx].startsWith("R")) {
                // Fallback: search from end for R{num}
                rIdx = -1;
                for (int i = parts.length - 2; i >= 2; i--) {
                    if (parts[i].startsWith("R") && parts[i].length() > 1) {
                        try {
                            Integer.parseInt(parts[i].substring(1));
                            rIdx = i;
                            break;
                        } catch (Exception e) {}
                    }
                }
            }
            if (rIdx == -1 || rIdx < 2) return ref;
            
            // Rebuild treeId
            StringBuilder treeIdBuilder = new StringBuilder(parts[1]);
            for (int i = 2; i < rIdx; i++) {
                treeIdBuilder.append("_").append(parts[i]);
            }
            String treeId = treeIdBuilder.toString();
            
            int roundNum = Integer.parseInt(parts[rIdx].substring(1));
            int matchIdx = Integer.parseInt(parts[rIdx + 1]);
            
            RepechageTree tree = findTreeById(treeId);
            if (tree == null) return ref;
            
            int roundIndex = roundNum - 1;
            if (roundIndex < 0 || roundIndex >= tree.rounds.size()) return ref;
            
            List<Match> round = tree.rounds.get(roundIndex);
            int matchOffset = matchIdx - 1;
            if (matchOffset < 0 || matchOffset >= round.size()) return ref;
            
            Match match = round.get(matchOffset);
            return match.winner != null ? match.winner : ref;
        } catch (Exception e) {
            return ref;
        }
    }
    
    // Resolve old-style ML{round}_{matchIdx} reference
    private String resolveMainLoserRef(String ref, String[] participantNames) {
        // Format: ML1_1 means loser from main round 1, match 1
        try {
            String numPart = ref.substring(2); // Remove "ML"
            String[] parts = numPart.split("_");
            if (parts.length < 2) return ref;
            
            int roundNum = Integer.parseInt(parts[0]);
            int matchIdx = Integer.parseInt(parts[1]);
            
            int roundIndex = roundNum - 1;
            if (roundIndex < 0 || roundIndex >= koRounds.size()) return ref;
            
            List<Match> round = koRounds.get(roundIndex);
            int matchOffset = matchIdx - 1;
            if (matchOffset < 0 || matchOffset >= round.size()) return ref;
            
            Match match = round.get(matchOffset);
            if (match.winner == null) return ref;
            
            String p1Name = getKOName(resolveKORef(match.p1, koRounds, roundIndex), participantNames);
            String p2Name = getKOName(resolveKORef(match.p2, koRounds, roundIndex), participantNames);
            
            return match.winner.equals(p1Name) ? p2Name : p1Name;
        } catch (Exception e) {
            return ref;
        }
    }
    
    // Find a RepechageTree by its ID
    private RepechageTree findTreeById(String treeId) {
        for (RepechageTree tree : allRepechageTrees) {
            if (tree.treeId.equals(treeId)) return tree;
        }
        return null;
    }
    
    // Helper: get participant name or winner/empty
    private String getKOName(String ref, String[] participantNames) {
        if (ref == null) return "Empty";
        if (ref.equals("Empty")) return "Empty";
        if (ref.startsWith("W") || ref.startsWith("L")) {
            return ref;
        }
        try {
            int idx = Integer.parseInt(ref) - 1;
            if (idx >= 0 && idx < participantNames.length) {
                String n = participantNames[idx];
                return (n == null || n.trim().isEmpty() || n.equals("Empty")) ? "Empty" : n;
            }
            return "Empty"; // Index out of bounds = empty slot
        } catch (Exception e) {}
        return ref;
    }

    // Navigate to Final page (page index 3)
    private void navigateToFinalPage() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).navigateToPage(3);
        }
    }
    
    // Navigate to previous page (KO -> Merged)
    private void navigateToPreviousPage() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).navigateToPage(1); // 1 = MergedFragment
        }
    }

    // Show dialog to enter match result
    private void showMatchDialog(Match match, String[] participantNames) {
        // For losers bracket matches, use getLosersName which resolves L_/W_ references
        String p1Name = match.isLosers ? 
            getLosersName(match.p1, participantNames) : 
            getKOName(match.p1, participantNames);
        String p2Name = match.isLosers ? 
            getLosersName(match.p2, participantNames) : 
            getKOName(match.p2, participantNames);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(p1Name.toUpperCase() + "      Vs " + p2Name.toUpperCase());

        // Create grid for score selection (0-15)
        android.widget.GridLayout grid = new android.widget.GridLayout(getContext());
        grid.setColumnCount(6);
        grid.setRowCount(5); // 3 score rows + 1 empty + 1 button row
        int btnHeight = (int) (48 * getResources().getDisplayMetrics().density);
        float baseTextSize = 16f;
        float increasedTextSize = baseTextSize * 1.3f;
        final int[] selectedScores = new int[] { -1, -1 };
        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        
        // Row 0: 0,1,2,3,4,5
        for (int col = 0; col < 6; col++) {
            final int score = col;
            android.widget.Button btn = new android.widget.Button(getContext());
            btn.setText(String.valueOf(score));
            btn.setMinHeight(btnHeight);
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                if (selectedScores[0] == -1) {
                    selectedScores[0] = score;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    showSecondScoreDialog(match, participantNames, selectedScores[0]);
                }
            });
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = android.widget.GridLayout.spec(col, 1f);
            params.rowSpec = android.widget.GridLayout.spec(0);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            grid.addView(btn);
        }
        
        // Row 1: space, 6,7,8,9,10
        android.widget.Space space1 = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams space1Params = new android.widget.GridLayout.LayoutParams();
        space1Params.width = 0;
        space1Params.height = btnHeight;
        space1Params.columnSpec = android.widget.GridLayout.spec(0, 1f);
        space1Params.rowSpec = android.widget.GridLayout.spec(1);
        space1.setLayoutParams(space1Params);
        grid.addView(space1);
        for (int col = 1; col < 6; col++) {
            final int score = col + 5;
            android.widget.Button btn = new android.widget.Button(getContext());
            btn.setText(String.valueOf(score));
            btn.setMinHeight(btnHeight);
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                if (selectedScores[0] == -1) {
                    selectedScores[0] = score;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    showSecondScoreDialog(match, participantNames, selectedScores[0]);
                }
            });
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = android.widget.GridLayout.spec(col, 1f);
            params.rowSpec = android.widget.GridLayout.spec(1);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            grid.addView(btn);
        }
        
        // Row 2: space, 11,12,13,14,15
        android.widget.Space space2 = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams space2Params = new android.widget.GridLayout.LayoutParams();
        space2Params.width = 0;
        space2Params.height = btnHeight;
        space2Params.columnSpec = android.widget.GridLayout.spec(0, 1f);
        space2Params.rowSpec = android.widget.GridLayout.spec(2);
        space2.setLayoutParams(space2Params);
        grid.addView(space2);
        for (int col = 1; col < 6; col++) {
            final int score = col + 10;
            android.widget.Button btn = new android.widget.Button(getContext());
            btn.setText(String.valueOf(score));
            btn.setMinHeight(btnHeight);
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                if (selectedScores[0] == -1) {
                    selectedScores[0] = score;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    showSecondScoreDialog(match, participantNames, selectedScores[0]);
                }
            });
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = android.widget.GridLayout.spec(col, 1f);
            params.rowSpec = android.widget.GridLayout.spec(2);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            grid.addView(btn);
        }
        
        // Row 3: empty row (spacer spanning all columns)
        android.widget.Space emptyRow = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams emptyParams = new android.widget.GridLayout.LayoutParams();
        emptyParams.width = 0;
        emptyParams.height = (int) (16 * getResources().getDisplayMetrics().density);
        emptyParams.columnSpec = android.widget.GridLayout.spec(0, 6, 1f);
        emptyParams.rowSpec = android.widget.GridLayout.spec(3);
        emptyRow.setLayoutParams(emptyParams);
        grid.addView(emptyRow);
        
        // Row 4: space, Cancel, space, space, space, RESET
        android.widget.Space btnSpace1 = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams btnSpace1Params = new android.widget.GridLayout.LayoutParams();
        btnSpace1Params.width = 0;
        btnSpace1Params.height = btnHeight;
        btnSpace1Params.columnSpec = android.widget.GridLayout.spec(0, 1f);
        btnSpace1Params.rowSpec = android.widget.GridLayout.spec(4);
        btnSpace1.setLayoutParams(btnSpace1Params);
        grid.addView(btnSpace1);
        
        android.widget.Button cancelBtn = new android.widget.Button(getContext());
        cancelBtn.setText("Cancel");
        cancelBtn.setMinHeight(btnHeight);
        cancelBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
        cancelBtn.setPadding(4, 4, 4, 4);
        cancelBtn.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].cancel();
        });
        android.widget.GridLayout.LayoutParams cancelParams = new android.widget.GridLayout.LayoutParams();
        cancelParams.width = 0;
        cancelParams.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
        cancelParams.columnSpec = android.widget.GridLayout.spec(1, 1f);
        cancelParams.rowSpec = android.widget.GridLayout.spec(4);
        cancelParams.setMargins(4, 4, 4, 4);
        cancelBtn.setLayoutParams(cancelParams);
        grid.addView(cancelBtn);
        
        for (int col = 2; col < 5; col++) {
            android.widget.Space sp = new android.widget.Space(getContext());
            android.widget.GridLayout.LayoutParams spParams = new android.widget.GridLayout.LayoutParams();
            spParams.width = 0;
            spParams.height = btnHeight;
            spParams.columnSpec = android.widget.GridLayout.spec(col, 1f);
            spParams.rowSpec = android.widget.GridLayout.spec(4);
            sp.setLayoutParams(spParams);
            grid.addView(sp);
        }
        
        android.widget.Button resetBtn = new android.widget.Button(getContext());
        resetBtn.setText("RESET");
        resetBtn.setMinHeight(btnHeight);
        resetBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
        resetBtn.setBackgroundColor(android.graphics.Color.RED);
        resetBtn.setPadding(4, 4, 4, 4);
        resetBtn.setOnClickListener(v -> {
            match.score1 = -1;
            match.score2 = -1;
            backupKOTree();
            renderKOTable((LinearLayout) getView().findViewById(R.id.ko_boxLayout));
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        android.widget.GridLayout.LayoutParams resetParams = new android.widget.GridLayout.LayoutParams();
        resetParams.width = 0;
        resetParams.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
        resetParams.columnSpec = android.widget.GridLayout.spec(5, 1f);
        resetParams.rowSpec = android.widget.GridLayout.spec(4);
        resetParams.setMargins(4, 4, 4, 4);
        resetBtn.setLayoutParams(resetParams);
        grid.addView(resetBtn);

        // Layout for grid
        android.widget.LinearLayout verticalLayout = new android.widget.LinearLayout(getContext());
        verticalLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        verticalLayout.addView(grid);

        builder.setView(verticalLayout);
        android.app.AlertDialog dialog = builder.create();
        dialogRef[0] = dialog;
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
                lp.copyFrom(window.getAttributes());
                lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.75);
                window.setAttributes(lp);
            }
        });
        dialog.show();
    }

    // Show second score selection popup for KO
    private void showSecondScoreDialog(Match match, String[] participantNames, int score1) {
        // For losers bracket matches, use getLosersName which resolves L_/W_ references
        String p1Name = match.isLosers ? 
            getLosersName(match.p1, participantNames) : 
            getKOName(match.p1, participantNames);
        String p2Name = match.isLosers ? 
            getLosersName(match.p2, participantNames) : 
            getKOName(match.p2, participantNames);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(p2Name.toUpperCase() + "      Vs " + p1Name.toUpperCase());

        android.widget.GridLayout grid = new android.widget.GridLayout(getContext());
        grid.setColumnCount(6);
        grid.setRowCount(5); // 3 score rows + 1 empty + 1 button row
        int btnHeight = (int) (48 * getResources().getDisplayMetrics().density);
        float baseTextSize = 16f;
        float increasedTextSize = baseTextSize * 1.3f;
        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        
        // Row 0: 0,1,2,3,4,5
        for (int col = 0; col < 6; col++) {
            final int score2 = col;
            android.widget.Button btn = new android.widget.Button(getContext());
            btn.setText(String.valueOf(score2));
            btn.setMinHeight(btnHeight);
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                match.score1 = score1;
                match.score2 = score2;
                propagateKOWinners();
                backupKOTree();
                renderKOTable((LinearLayout) getView().findViewById(R.id.ko_boxLayout));
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = android.widget.GridLayout.spec(col, 1f);
            params.rowSpec = android.widget.GridLayout.spec(0);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            grid.addView(btn);
        }
        
        // Row 1: space, 6,7,8,9,10
        android.widget.Space space1 = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams space1Params = new android.widget.GridLayout.LayoutParams();
        space1Params.width = 0;
        space1Params.height = btnHeight;
        space1Params.columnSpec = android.widget.GridLayout.spec(0, 1f);
        space1Params.rowSpec = android.widget.GridLayout.spec(1);
        space1.setLayoutParams(space1Params);
        grid.addView(space1);
        for (int col = 1; col < 6; col++) {
            final int score2 = col + 5;
            android.widget.Button btn = new android.widget.Button(getContext());
            btn.setText(String.valueOf(score2));
            btn.setMinHeight(btnHeight);
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                match.score1 = score1;
                match.score2 = score2;
                propagateKOWinners();
                backupKOTree();
                renderKOTable((LinearLayout) getView().findViewById(R.id.ko_boxLayout));
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = android.widget.GridLayout.spec(col, 1f);
            params.rowSpec = android.widget.GridLayout.spec(1);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            grid.addView(btn);
        }
        
        // Row 2: space, 11,12,13,14,15
        android.widget.Space space2 = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams space2Params = new android.widget.GridLayout.LayoutParams();
        space2Params.width = 0;
        space2Params.height = btnHeight;
        space2Params.columnSpec = android.widget.GridLayout.spec(0, 1f);
        space2Params.rowSpec = android.widget.GridLayout.spec(2);
        space2.setLayoutParams(space2Params);
        grid.addView(space2);
        for (int col = 1; col < 6; col++) {
            final int score2 = col + 10;
            android.widget.Button btn = new android.widget.Button(getContext());
            btn.setText(String.valueOf(score2));
            btn.setMinHeight(btnHeight);
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                match.score1 = score1;
                match.score2 = score2;
                propagateKOWinners();
                backupKOTree();
                renderKOTable((LinearLayout) getView().findViewById(R.id.ko_boxLayout));
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = android.widget.GridLayout.spec(col, 1f);
            params.rowSpec = android.widget.GridLayout.spec(2);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);
            grid.addView(btn);
        }
        
        // Row 3: empty row (spacer spanning all columns)
        android.widget.Space emptyRow = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams emptyParams = new android.widget.GridLayout.LayoutParams();
        emptyParams.width = 0;
        emptyParams.height = (int) (16 * getResources().getDisplayMetrics().density);
        emptyParams.columnSpec = android.widget.GridLayout.spec(0, 6, 1f);
        emptyParams.rowSpec = android.widget.GridLayout.spec(3);
        emptyRow.setLayoutParams(emptyParams);
        grid.addView(emptyRow);
        
        // Row 4: space, Cancel, space, space, space, RESET
        android.widget.Space btnSpace1 = new android.widget.Space(getContext());
        android.widget.GridLayout.LayoutParams btnSpace1Params = new android.widget.GridLayout.LayoutParams();
        btnSpace1Params.width = 0;
        btnSpace1Params.height = btnHeight;
        btnSpace1Params.columnSpec = android.widget.GridLayout.spec(0, 1f);
        btnSpace1Params.rowSpec = android.widget.GridLayout.spec(4);
        btnSpace1.setLayoutParams(btnSpace1Params);
        grid.addView(btnSpace1);
        
        android.widget.Button cancelBtn = new android.widget.Button(getContext());
        cancelBtn.setText("Cancel");
        cancelBtn.setMinHeight(btnHeight);
        cancelBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
        cancelBtn.setPadding(4, 4, 4, 4);
        cancelBtn.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].cancel();
        });
        android.widget.GridLayout.LayoutParams cancelParams = new android.widget.GridLayout.LayoutParams();
        cancelParams.width = 0;
        cancelParams.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
        cancelParams.columnSpec = android.widget.GridLayout.spec(1, 1f);
        cancelParams.rowSpec = android.widget.GridLayout.spec(4);
        cancelParams.setMargins(4, 4, 4, 4);
        cancelBtn.setLayoutParams(cancelParams);
        grid.addView(cancelBtn);
        
        for (int col = 2; col < 5; col++) {
            android.widget.Space sp = new android.widget.Space(getContext());
            android.widget.GridLayout.LayoutParams spParams = new android.widget.GridLayout.LayoutParams();
            spParams.width = 0;
            spParams.height = btnHeight;
            spParams.columnSpec = android.widget.GridLayout.spec(col, 1f);
            spParams.rowSpec = android.widget.GridLayout.spec(4);
            sp.setLayoutParams(spParams);
            grid.addView(sp);
        }
        
        android.widget.Button resetBtn = new android.widget.Button(getContext());
        resetBtn.setText("RESET");
        resetBtn.setMinHeight(btnHeight);
        resetBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
        resetBtn.setBackgroundColor(android.graphics.Color.RED);
        resetBtn.setPadding(4, 4, 4, 4);
        resetBtn.setOnClickListener(v -> {
            match.score1 = -1;
            match.score2 = -1;
            propagateKOWinners();
            backupKOTree();
            renderKOTable((LinearLayout) getView().findViewById(R.id.ko_boxLayout));
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        android.widget.GridLayout.LayoutParams resetParams = new android.widget.GridLayout.LayoutParams();
        resetParams.width = 0;
        resetParams.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT;
        resetParams.columnSpec = android.widget.GridLayout.spec(5, 1f);
        resetParams.rowSpec = android.widget.GridLayout.spec(4);
        resetParams.setMargins(4, 4, 4, 4);
        resetBtn.setLayoutParams(resetParams);
        grid.addView(resetBtn);

        android.widget.LinearLayout verticalLayout = new android.widget.LinearLayout(getContext());
        verticalLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        verticalLayout.addView(grid);

        builder.setView(verticalLayout);
        android.app.AlertDialog dialog = builder.create();
        dialogRef[0] = dialog;
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
                lp.copyFrom(window.getAttributes());
                lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.75);
                window.setAttributes(lp);
            }
        });
        dialog.show();

        // Always check and auto-advance winner if one participant is 'Empty', both at dialog open and after any score entry
        if ((p1Name.equals("Empty") && !p2Name.equals("Empty")) || (!p1Name.equals("Empty") && p2Name.equals("Empty"))) {
            match.score1 = p1Name.equals("Empty") ? 0 : 15;
            match.score2 = p2Name.equals("Empty") ? 0 : 15;
            propagateKOWinners();
            backupKOTree();
            renderKOTable((LinearLayout) getView().findViewById(R.id.ko_boxLayout));
        }
    }
    
    // Propagate KO winners to next round after score entry
    private void propagateKOWinners() {
        String[] participantNames = getKOParticipantNames();
        // Propagate up to but NOT including the Final round - Final should not propagate to Third Place
        // Third Place uses L refs from Semifinals, not winners from Final
        int finalRoundIdx = koRounds.size() - 2; // Final is second-to-last (Third Place is last)
        for (int r = 0; r < finalRoundIdx; r++) {
            List<Match> currentRound = koRounds.get(r);
            List<Match> nextRound = koRounds.get(r + 1);
            for (int m = 0; m < currentRound.size(); m++) {
                Match match = currentRound.get(m);
                String winner = getMatchWinner(match, participantNames, r);
                
                // Set match.winner to the actual name for proper loser resolution later
                if (winner != null && !winner.equals("Empty")) {
                    match.winner = winner;
                }
                
                int nextIdx = m / 2;
                if (nextRound.size() > nextIdx) {
                    Match nextMatch = nextRound.get(nextIdx);
                    // Only update the winner's slot in the next round
                    if (m % 2 == 0 && !winner.equals("Empty")) {
                        nextMatch.p1 = winner;
                    } else if (m % 2 == 1 && !winner.equals("Empty")) {
                        nextMatch.p2 = winner;
                    }
                    // Do NOT touch the opponent's slot; it remains W* or the winner of the other match if already defined
                }
            }
        }
        // Also set winner for Final and Third Place matches (last two rounds)
        int totalRounds = koRounds.size();
        for (int r = totalRounds - 2; r < totalRounds; r++) {
            if (r < 0) continue;
            for (Match match : koRounds.get(r)) {
                String winner = getMatchWinner(match, participantNames, r);
                if (winner != null && !winner.equals("Empty")) {
                    match.winner = winner;
                }
            }
        }
        
        // In repechage mode, propagate losers to losers bracket
        if (koRepechage && !losersRounds.isEmpty()) {
            propagateLosersToLosersBracket(participantNames);
            autoAdvanceEmptyMatchesInLosersBracket(participantNames);
        }
    }
    
    // Helper: get winner name (resolved) for a match in main bracket
    private String getMatchWinner(Match match, String[] participantNames, int roundIdx) {
        // Resolve p1/p2 to actual names
        String p1Ref = resolveKORef(match.p1, koRounds, roundIdx);
        String p2Ref = resolveKORef(match.p2, koRounds, roundIdx);
        String p1Name = getKOName(p1Ref, participantNames);
        String p2Name = getKOName(p2Ref, participantNames);
        
        if (p1Name.equals("Empty") && !p2Name.equals("Empty")) return p2Name;
        if (!p1Name.equals("Empty") && p2Name.equals("Empty")) return p1Name;
        if (match.score1 > match.score2) return p1Name;
        if (match.score2 > match.score1) return p2Name;
        return "Empty";
    }
    
    // Propagate losers from main bracket to repechage trees
    private void propagateLosersToLosersBracket(String[] participantNames) {
        // For each main bracket round, propagate losers to repechage trees
        for (int r = 0; r < koRounds.size() - 1; r++) { // Exclude final
            List<Match> mainRound = koRounds.get(r);
            for (int m = 0; m < mainRound.size(); m++) {
                Match match = mainRound.get(m);
                if (match.winner == null) continue;
                
                // Find the loser
                String p1Ref = resolveKORef(match.p1, koRounds, r);
                String p2Ref = resolveKORef(match.p2, koRounds, r);
                String p1Name = getKOName(p1Ref, participantNames);
                String p2Name = getKOName(p2Ref, participantNames);
                String loser;
                if (match.winner.equals("Empty")) {
                    // Both were Empty, loser is also Empty
                    loser = "Empty";
                } else {
                    loser = match.winner.equals(p1Name) ? p2Name : p1Name;
                }
                
                // Propagate loser to appropriate losers bracket slot using new L_ format
                // Format: L_R1_R{round+1}_{matchIdx+1} -> Replace with actual loser name
                String loserRefNew = "L_R1_R" + (r + 1) + "_" + (m + 1);
                String loserRefOld = "ML" + (r + 1) + "_" + (m + 1);
                replaceRepechageRef(loserRefNew, loser);
                replaceRepechageRef(loserRefOld, loser); // Also try old format
            }
        }
        
        // Also propagate winners within repechage trees
        for (RepechageTree tree : allRepechageTrees) {
            if (tree.treeId.equals("R1")) continue;
            
            for (int r = 0; r < tree.rounds.size(); r++) {
                List<Match> round = tree.rounds.get(r);
                for (int m = 0; m < round.size(); m++) {
                    Match match = round.get(m);
                    String winner = getRepechageMatchWinner(match, participantNames);
                    
                    if (winner != null && !winner.equals("Empty") && 
                        !winner.startsWith("L_") && !winner.startsWith("W_") && !winner.startsWith("ML")) {
                        match.winner = winner;
                        
                        // If winner is due to Empty opponent, set scores too (for color display)
                        if (match.score1 < 0 || match.score2 < 0) {
                            String p1 = match.p1;
                            String p2 = match.p2;
                            String p1Name = p1.equals("Empty") || p1.equals("-") ? "Empty" : p1;
                            String p2Name = p2.equals("Empty") || p2.equals("-") ? "Empty" : p2;
                            if (p1Name.equals("Empty") && !p2Name.equals("Empty")) {
                                match.score1 = 0;
                                match.score2 = 15;
                            } else if (!p1Name.equals("Empty") && p2Name.equals("Empty")) {
                                match.score1 = 15;
                                match.score2 = 0;
                            }
                        }
                        
                        // Propagate winner to next round in this tree
                        String winnerRef = "W_" + tree.treeId + "_R" + (r + 1) + "_" + (m + 1);
                        if (r + 1 < tree.rounds.size()) {
                            replaceRepechageRefInTree(tree, r + 1, winnerRef, winner);
                        }
                    }
                }
            }
        }
        
        // Rebuild legacy losersRounds
        rebuildLosersRoundsFromRepechageTrees();
        
        // Update Grand Final participants
        if (grandFinalMatch != null) {
            // Main bracket winner
            if (koRounds.size() >= 1) {
                Match mainFinal = null;
                // Main final is usually the only match in last round (or second-to-last for repechage)
                for (int r = koRounds.size() - 1; r >= 0; r--) {
                    if (koRounds.get(r).size() == 1) {
                        mainFinal = koRounds.get(r).get(0);
                        break;
                    }
                }
                if (mainFinal != null && mainFinal.winner != null && !mainFinal.winner.equals("Empty")) {
                    grandFinalMatch.p1 = mainFinal.winner;
                }
            }
            
            // Losers bracket winner
            if (!losersRounds.isEmpty()) {
                List<Match> lastLosersRound = losersRounds.get(losersRounds.size() - 1);
                if (!lastLosersRound.isEmpty()) {
                    Match losersFinal = lastLosersRound.get(0);
                    if (losersFinal.winner != null && !losersFinal.winner.equals("Empty")) {
                        grandFinalMatch.p2 = losersFinal.winner;
                    }
                }
            }
        }
    }
    
    // Replace reference in all repechage trees with actual name
    private void replaceRepechageRef(String ref, String actualName) {
        for (RepechageTree tree : allRepechageTrees) {
            if (tree.treeId.equals("R1")) continue;
            for (List<Match> round : tree.rounds) {
                for (Match match : round) {
                    if (match.p1.equals(ref)) {
                        match.p1 = actualName;
                    }
                    if (match.p2.equals(ref)) {
                        match.p2 = actualName;
                    }
                }
            }
        }
    }
    
    // Replace reference in specific tree round with actual name
    private void replaceRepechageRefInTree(RepechageTree tree, int roundIdx, String ref, String actualName) {
        if (roundIdx >= tree.rounds.size()) return;
        List<Match> round = tree.rounds.get(roundIdx);
        for (Match match : round) {
            if (match.p1.equals(ref)) {
                match.p1 = actualName;
            }
            if (match.p2.equals(ref)) {
                match.p2 = actualName;
            }
        }
    }
    
    // Get winner for repechage match
    private String getRepechageMatchWinner(Match match, String[] participantNames) {
        String p1 = match.p1;
        String p2 = match.p2;
        
        // If either side is still a reference, can't determine winner
        if (p1.startsWith("L_") || p1.startsWith("W_") || p1.startsWith("ML") ||
            p2.startsWith("L_") || p2.startsWith("W_") || p2.startsWith("ML")) {
            return null;
        }
        
        String p1Name = p1.equals("Empty") || p1.equals("-") ? "Empty" : p1;
        String p2Name = p2.equals("Empty") || p2.equals("-") ? "Empty" : p2;
        
        if (p1Name.equals("Empty") && !p2Name.equals("Empty")) return p2Name;
        if (!p1Name.equals("Empty") && p2Name.equals("Empty")) return p1Name;
        if (match.score1 > match.score2) return p1Name;
        if (match.score2 > match.score1) return p2Name;
        return null;
    }
    
    // Auto-advance all matches in losers bracket where one participant is 'Empty'
    private void autoAdvanceEmptyMatchesInLosersBracket(String[] participantNames) {
        if (allRepechageTrees.isEmpty()) return;
        
        // Run multiple passes until no more changes (handles cascading)
        boolean changed = true;
        int maxPasses = 10; // Safety limit
        int pass = 0;
        while (changed && pass < maxPasses) {
            changed = false;
            pass++;
            
            // Process each repechage tree (skip main)
            for (RepechageTree tree : allRepechageTrees) {
                if (tree.treeId.equals("R1")) continue;
                
                for (int r = 0; r < tree.rounds.size(); r++) {
                    List<Match> currentRound = tree.rounds.get(r);
                    List<Match> nextRound = (r + 1 < tree.rounds.size()) ? tree.rounds.get(r + 1) : null;
                    
                    for (int i = 0; i < currentRound.size(); i++) {
                        Match match = currentRound.get(i);
                        // Skip if already has a valid winner
                        if (match.winner != null && !match.winner.startsWith("L_") && 
                            !match.winner.startsWith("W_") && !match.winner.startsWith("ML")) continue;
                        
                        String p1 = match.p1;
                        String p2 = match.p2;
                        
                        // Skip if either side still has unresolved L_*/W_*/ML reference
                        if (p1.startsWith("L_") || p1.startsWith("W_") || p1.startsWith("ML") ||
                            p2.startsWith("L_") || p2.startsWith("W_") || p2.startsWith("ML")) {
                            // Try to resolve them
                            String p1Resolved = getLosersName(p1, participantNames);
                            String p2Resolved = getLosersName(p2, participantNames);
                            
                            // If still unresolved (returns placeholder like "L1.2"), skip
                            boolean p1StillUnresolved = isPlaceholderName(p1Resolved) && !p1Resolved.equals("Empty");
                            boolean p2StillUnresolved = isPlaceholderName(p2Resolved) && !p2Resolved.equals("Empty");
                            if (p1StillUnresolved || p2StillUnresolved) {
                                continue;
                            }
                            
                            // Both resolved - update p1/p2 and continue
                            match.p1 = p1Resolved;
                            match.p2 = p2Resolved;
                            p1 = p1Resolved;
                            p2 = p2Resolved;
                            changed = true;
                        }
                        
                        // Now check for Empty matches - resolve names properly
                        String p1Name = getLosersName(p1, participantNames);
                        String p2Name = getLosersName(p2, participantNames);
                        
                        // Skip if either name is still a placeholder (not resolved) but not "Empty"
                        boolean p1Unresolved = isPlaceholderName(p1Name) && !p1Name.equals("Empty");
                        boolean p2Unresolved = isPlaceholderName(p2Name) && !p2Name.equals("Empty");
                        if (p1Unresolved || p2Unresolved) {
                            continue;
                        }
                        
                        String winner = null;
                        // Case 1: Both are Empty
                        if (p1Name.equals("Empty") && p2Name.equals("Empty")) {
                            match.score1 = 0;
                            match.score2 = 0;
                            match.winner = "Empty";
                            winner = "Empty";
                            changed = true;
                        }
                        // Case 2: One is Empty
                        else if (p1Name.equals("Empty") && !p2Name.equals("Empty")) {
                            match.score1 = 0;
                            match.score2 = 15;
                            match.winner = p2Name;
                            winner = p2Name;
                            changed = true;
                        } else if (!p1Name.equals("Empty") && p2Name.equals("Empty")) {
                            match.score1 = 15;
                            match.score2 = 0;
                            match.winner = p1Name;
                            winner = p1Name;
                            changed = true;
                        }
                        // Case 3: Both are real names - don't auto-advance
                        
                        // Propagate winner to next round in this tree
                        if (winner != null && nextRound != null) {
                            String treeId = tree.treeId;
                            int roundNum = r + 1; // 1-based
                            String winnerRef = "W_" + treeId + "_R" + roundNum + "_" + (i + 1);
                            
                            for (Match nextMatch : nextRound) {
                                // Check p1 reference
                                if (nextMatch.p1.equals(winnerRef) || 
                                    (nextMatch.p1.contains("_R" + roundNum + "_") && nextMatch.p1.contains(treeId))) {
                                    // Parse to verify match index
                                    String[] parts = nextMatch.p1.split("_");
                                    if (parts.length >= 2) {
                                        String lastPart = parts[parts.length - 1];
                                        try {
                                            int refIdx = Integer.parseInt(lastPart);
                                            // Check if this ref points to source match
                                            int expectedSrc1 = (nextMatch.matchIdx * 2) + 1;
                                            int expectedSrc2 = (nextMatch.matchIdx * 2) + 2;
                                            if (refIdx == expectedSrc1 || refIdx == expectedSrc2) {
                                                if (i + 1 == refIdx) {
                                                    nextMatch.p1 = winner;
                                                }
                                            }
                                        } catch (Exception e) {}
                                    }
                                }
                                // Check p2 reference
                                if (nextMatch.p2.equals(winnerRef) ||
                                    (nextMatch.p2.contains("_R" + roundNum + "_") && nextMatch.p2.contains(treeId))) {
                                    String[] parts = nextMatch.p2.split("_");
                                    if (parts.length >= 2) {
                                        String lastPart = parts[parts.length - 1];
                                        try {
                                            int refIdx = Integer.parseInt(lastPart);
                                            int expectedSrc1 = (nextMatch.matchIdx * 2) + 1;
                                            int expectedSrc2 = (nextMatch.matchIdx * 2) + 2;
                                            if (refIdx == expectedSrc1 || refIdx == expectedSrc2) {
                                                if (i + 1 == refIdx) {
                                                    nextMatch.p2 = winner;
                                                }
                                            }
                                        } catch (Exception e) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Rebuild losersRounds from repechage trees after updates
        rebuildLosersRoundsFromRepechageTrees();
        
        // Also update grand final if losers bracket final has a winner
        if (grandFinalMatch != null && !losersRounds.isEmpty()) {
            List<Match> lastRound = losersRounds.get(losersRounds.size() - 1);
            if (!lastRound.isEmpty()) {
                Match losersFinal = lastRound.get(0);
                if (losersFinal.winner != null && !losersFinal.winner.equals("Empty") && 
                    !losersFinal.winner.startsWith("L_") && !losersFinal.winner.startsWith("W_")) {
                    grandFinalMatch.p2 = losersFinal.winner;
                }
            }
        }
    }
    
    // Restore KO state from KO_backup.csv
    private void restoreFromKOBackup(LinearLayout koBoxLayout) {
        try {
            File filesDir = requireContext().getFilesDir();
            File backupFile = new File(filesDir, "KO_backup.csv");
            
            if (!backupFile.exists()) {
                Toast.makeText(getContext(), "KO_backup.csv not found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
            String firstLine = reader.readLine();
            if (firstLine == null) {
                reader.close();
                Toast.makeText(getContext(), "KO_backup.csv is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Parse metadata line: #META,koSize,repechage
            int koSize = 8;
            boolean repechage = false;
            boolean hasMetaLine = false;
            if (firstLine.startsWith("#META,")) {
                hasMetaLine = true;
                String[] metaParts = firstLine.split(",");
                if (metaParts.length >= 3) {
                    try { koSize = Integer.parseInt(metaParts[1].trim()); } catch (Exception e) {}
                    repechage = "true".equalsIgnoreCase(metaParts[2].trim());
                }
                // Skip header line
                reader.readLine();
            }
            
            // Read all data lines
            java.util.List<String> lines = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            
            // If old format (no META line), count round 1 matches
            if (!hasMetaLine) {
                int round1Matches = 0;
                for (String l : lines) {
                    String[] parts = l.split(",");
                    if (parts.length >= 1 && parts[0].trim().equals("1")) {
                        round1Matches++;
                    }
                }
                koSize = round1Matches * 2;
                if (koSize < 4) koSize = 8;
            }
            
            android.util.Log.d("KOFragment", "RESTORE: koSize=" + koSize + ", repechage=" + repechage);
            koRepechage = repechage;
            koNrPart = koSize; // Set local nrPart from backup
            
            // Load participant names from Merged_backup.csv
            loadParticipantNamesFromMerged();
            
            // Reload KO tree structure
            loadKOTree(koSize, koRepechage);
            
            android.util.Log.d("KOFragment", "RESTORE: koRounds.size()=" + koRounds.size() + ", lines to restore=" + lines.size());
            
            // Apply saved scores and participants
            for (String l : lines) {
                String[] parts = l.split(",");
                if (parts.length < 5) continue;
                try {
                    int roundNum = Integer.parseInt(parts[0].trim()) - 1; // 0-indexed
                    int matchNum = Integer.parseInt(parts[1].trim()) - 1; // 0-indexed
                    String p1 = parts[2].trim();
                    String scoreStr1 = parts[3].trim();
                    String p2 = parts[4].trim();
                    String scoreStr2 = parts.length > 5 ? parts[5].trim() : "";
                    
                    android.util.Log.d("KOFragment", "RESTORE LINE: round=" + roundNum + " match=" + matchNum + " p1=" + p1 + " p2=" + p2 + " s1=" + scoreStr1 + " s2=" + scoreStr2);
                    
                    if (roundNum >= 0 && roundNum < koRounds.size()) {
                        List<Match> round = koRounds.get(roundNum);
                        for (Match m : round) {
                            if (m.matchIdx == matchNum) {
                                m.p1 = p1;
                                m.p2 = p2;
                                m.score1 = scoreStr1.isEmpty() ? -1 : Integer.parseInt(scoreStr1);
                                m.score2 = scoreStr2.isEmpty() ? -1 : Integer.parseInt(scoreStr2);
                                // Set winner based on scores
                                if (m.score1 > m.score2) {
                                    m.winner = p1;
                                } else if (m.score2 > m.score1) {
                                    m.winner = p2;
                                } else {
                                    m.winner = null;
                                }
                                android.util.Log.d("KOFragment", "RESTORE APPLIED: m.p1=" + m.p1 + " m.p2=" + m.p2);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("KOFragment", "RESTORE ERROR: " + e.getMessage());
                }
            }
            
            // Log Round 1 state after restore
            if (!koRounds.isEmpty()) {
                android.util.Log.d("KOFragment", "RESTORE COMPLETE - Round 1 matches:");
                for (Match m : koRounds.get(0)) {
                    android.util.Log.d("KOFragment", "  Match " + m.matchIdx + ": p1=" + m.p1 + " p2=" + m.p2 + " s1=" + m.score1 + " s2=" + m.score2);
                }
            }
            
            // Update checkbox state to match restored repechage
            if (repechageCheckboxRef != null) {
                repechageCheckboxRef.setOnCheckedChangeListener(null); // Avoid triggering reload
                repechageCheckboxRef.setChecked(koRepechage);
                repechageCheckboxRef.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    koRepechage = isChecked;
                    int nrPartVal = getKONrPart();
                    
                    // Save ALL match results before reloading using round/match indices
                    java.util.Map<String, int[]> savedResults = new java.util.HashMap<>();
                    for (int r = 0; r < koRounds.size(); r++) {
                        List<Match> round = koRounds.get(r);
                        for (int mi = 0; mi < round.size(); mi++) {
                            Match m = round.get(mi);
                            if (m.score1 >= 0 && m.score2 >= 0) {
                                String key = "R1|R" + r + "|M" + mi;
                                savedResults.put(key, new int[]{m.score1, m.score2});
                            }
                        }
                    }
                    for (RepechageTree tree : allRepechageTrees) {
                        if (tree.treeId.equals("R1")) continue;
                        for (int r = 0; r < tree.rounds.size(); r++) {
                            List<Match> round = tree.rounds.get(r);
                            for (int mi = 0; mi < round.size(); mi++) {
                                Match m = round.get(mi);
                                if (m.score1 >= 0 && m.score2 >= 0) {
                                    String key = tree.treeId + "|R" + r + "|M" + mi;
                                    savedResults.put(key, new int[]{m.score1, m.score2});
                                }
                            }
                        }
                    }
                    
                    loadKOTree(nrPartVal, koRepechage);
                    
                    // Restore saved match results to main bracket
                    for (int r = 0; r < koRounds.size(); r++) {
                        List<Match> round = koRounds.get(r);
                        for (int mi = 0; mi < round.size(); mi++) {
                            Match m = round.get(mi);
                            String key = "R1|R" + r + "|M" + mi;
                            if (savedResults.containsKey(key)) {
                                int[] scores = savedResults.get(key);
                                m.score1 = scores[0];
                                m.score2 = scores[1];
                                if (m.score1 > m.score2) {
                                    m.winner = getKOName(m.p1, getKOParticipantNames());
                                } else if (m.score2 > m.score1) {
                                    m.winner = getKOName(m.p2, getKOParticipantNames());
                                }
                            }
                        }
                    }
                    // Restore saved match results to all repechage trees
                    for (RepechageTree tree : allRepechageTrees) {
                        if (tree.treeId.equals("R1")) continue;
                        for (int r = 0; r < tree.rounds.size(); r++) {
                            List<Match> round = tree.rounds.get(r);
                            for (int mi = 0; mi < round.size(); mi++) {
                                Match m = round.get(mi);
                                String key = tree.treeId + "|R" + r + "|M" + mi;
                                if (savedResults.containsKey(key)) {
                                    int[] scores = savedResults.get(key);
                                    m.score1 = scores[0];
                                    m.score2 = scores[1];
                                    if (m.score1 > m.score2) {
                                        m.winner = getKOName(m.p1, getKOParticipantNames());
                                    } else if (m.score2 > m.score1) {
                                        m.winner = getKOName(m.p2, getKOParticipantNames());
                                    }
                                }
                            }
                        }
                    }
                    
                    String[] participantNames = getKOParticipantNames();
                    if (participantNames != null) {
                        autoAdvanceEmptyMatches(participantNames);
                        if (koRepechage && !losersRounds.isEmpty()) {
                            propagateLosersToLosersBracket(participantNames);
                            autoAdvanceEmptyMatchesInLosersBracket(participantNames);
                        }
                    }
                    propagateKOWinners();
                    renderKOTable(koBoxLayout);
                });
            }
            
            // Auto-advance Empty matches and propagate
            String[] participantNames = getKOParticipantNames();
            if (participantNames != null) {
                autoAdvanceEmptyMatches(participantNames);
                if (koRepechage && !losersRounds.isEmpty()) {
                    propagateLosersToLosersBracket(participantNames);
                    autoAdvanceEmptyMatchesInLosersBracket(participantNames);
                }
            }
            propagateKOWinners();
            renderKOTable(koBoxLayout);
            Toast.makeText(getContext(), "Restored from KO_backup.csv", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to restore: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // Auto-backup KO tree to KO_backup.csv in app private Documents
    private void backupKOTree() {
        try {
            File filesDir = requireContext().getFilesDir();
            File backupFile = new File(filesDir, "KO_backup.csv");
            FileWriter writer = new FileWriter(backupFile, false);
            // Write metadata line with koSize and repechage state
            int koSize = koRounds.isEmpty() ? 8 : koRounds.get(0).size() * 2;
            writer.write("#META," + koSize + "," + koRepechage + "\n");
            writer.write("Tree,Round,Match,Participant1,Score1,Participant2,Score2,Winner\n");
            android.util.Log.d("KOFragment", "BACKUP: koRounds.size()=" + koRounds.size() + ", koSize=" + koSize + ", repechage=" + koRepechage);
            
            // Write main bracket (R1)
            for (int r = 0; r < koRounds.size(); r++) {
                android.util.Log.d("KOFragment", "BACKUP: Round " + r + " has " + koRounds.get(r).size() + " matches");
                for (Match m : koRounds.get(r)) {
                    String p1 = m.p1;
                    String p2 = m.p2;
                    String winner = (m.score1 > m.score2) ? p1 : (m.score2 > m.score1 ? p2 : "");
                    String line = "R1,"+(r+1)+","+(m.matchIdx+1)+","+p1+","+(m.score1>=0?m.score1:"")+","+p2+","+(m.score2>=0?m.score2:"")+","+winner;
                    android.util.Log.d("KOFragment", "BACKUP LINE: " + line);
                    writer.write(line + "\n");
                }
            }
            
            // Write repechage trees if enabled
            if (koRepechage && !allRepechageTrees.isEmpty()) {
                for (RepechageTree tree : allRepechageTrees) {
                    if (tree.treeId.equals("R1")) continue; // Skip main tree (already written)
                    
                    for (int r = 0; r < tree.rounds.size(); r++) {
                        for (Match m : tree.rounds.get(r)) {
                            String p1 = m.p1;
                            String p2 = m.p2;
                            String winner = "";
                            if (m.score1 >= 0 && m.score2 >= 0) {
                                winner = (m.score1 > m.score2) ? p1 : (m.score2 > m.score1 ? p2 : "");
                            }
                            String line = tree.treeId+","+(r+1)+","+(m.matchIdx+1)+","+p1+","+(m.score1>=0?m.score1:"")+","+p2+","+(m.score2>=0?m.score2:"")+","+winner;
                            writer.write(line + "\n");
                        }
                    }
                }
            }
            
            writer.close();
        } catch (Exception e) { 
            android.util.Log.e("KOFragment", "BACKUP ERROR: " + e.getMessage());
        }
    }
    
    // ========== REPLACE: Load KO state from selected CSV file ==========
    
    private void loadKOFromCsvUri(Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            inputStream.close();
            
            // Process matches from CSV (same format as backup)
            processRestoredMatches(lines);
            
            Toast.makeText(getContext(), "KO state loaded from file", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "REPLACE ERROR: " + e.getMessage());
            Toast.makeText(getContext(), "Error loading KO file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // ========== QR Code Methods for KO Data ==========
    
    // Compress data using GZIP + Base64 for QR code
    private String compressKOData(String data) {
        try {
            java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(byteStream);
            gzip.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            gzip.close();
            return android.util.Base64.encodeToString(byteStream.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error compressing data: " + e.getMessage());
            return null;
        }
    }
    
    // Decompress data from GZIP + Base64
    private String decompressKOData(String compressed) {
        try {
            byte[] compressedBytes = android.util.Base64.decode(compressed, android.util.Base64.NO_WRAP);
            java.io.ByteArrayInputStream byteStream = new java.io.ByteArrayInputStream(compressedBytes);
            java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(byteStream);
            java.io.ByteArrayOutputStream outStream = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                outStream.write(buffer, 0, len);
            }
            gzip.close();
            return outStream.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error decompressing data: " + e.getMessage());
            return null;
        }
    }
    
    // Generate QR code bitmap
    private android.graphics.Bitmap generateQrCode(String data, int size) {
        try {
            com.google.zxing.MultiFormatWriter writer = new com.google.zxing.MultiFormatWriter();
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error generating QR code: " + e.getMessage());
            return null;
        }
    }
    
    // Generate KO data CSV string for QR code (same format as backup)
    private String generateKOCsvData() {
        StringBuilder csv = new StringBuilder();
        // Write metadata line with koSize and repechage state
        int koSize = koRounds.isEmpty() ? 8 : koRounds.get(0).size() * 2;
        csv.append("#META," + koSize + "," + koRepechage + "\n");
        csv.append("Tree,Round,Match,Participant1,Score1,Participant2,Score2,Winner\n");
        
        // Write main bracket (R1)
        for (int r = 0; r < koRounds.size(); r++) {
            for (Match m : koRounds.get(r)) {
                String p1 = m.p1;
                String p2 = m.p2;
                String winner = (m.score1 > m.score2) ? p1 : (m.score2 > m.score1 ? p2 : "");
                csv.append("R1,"+(r+1)+","+(m.matchIdx+1)+","+p1+","+(m.score1>=0?m.score1:"")+","+p2+","+(m.score2>=0?m.score2:"")+","+winner+"\n");
            }
        }
        
        // Write repechage trees if enabled
        if (koRepechage && !allRepechageTrees.isEmpty()) {
            for (RepechageTree tree : allRepechageTrees) {
                if (tree.treeId.equals("R1")) continue; // Skip main tree (already written)
                
                for (int r = 0; r < tree.rounds.size(); r++) {
                    for (Match m : tree.rounds.get(r)) {
                        String p1 = m.p1;
                        String p2 = m.p2;
                        String winner = "";
                        if (m.score1 >= 0 && m.score2 >= 0) {
                            winner = (m.score1 > m.score2) ? p1 : (m.score2 > m.score1 ? p2 : "");
                        }
                        csv.append(tree.treeId+","+(r+1)+","+(m.matchIdx+1)+","+p1+","+(m.score1>=0?m.score1:"")+","+p2+","+(m.score2>=0?m.score2:"")+","+winner+"\n");
                    }
                }
            }
        }
        
        return csv.toString();
    }
    
    // Generate and show QR code for KO data
    private void generateAndShowKOQrCode() {
        String csvData = generateKOCsvData();
        String compressed = compressKOData(csvData);
        
        if (compressed == null) {
            Toast.makeText(getContext(), "Error compressing KO data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get screen dimensions for QR code size
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        int qrSize = Math.min(screenWidth, screenHeight) - 100;
        
        android.graphics.Bitmap qrBitmap = generateQrCode(compressed, qrSize);
        if (qrBitmap == null) {
            Toast.makeText(getContext(), "Error generating QR code", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showQrCodeFullscreen(qrBitmap);
    }
    
    // Show QR code in fullscreen dialog
    private void showQrCodeFullscreen(android.graphics.Bitmap qrBitmap) {
        // Create fullscreen dialog with no title
        android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        
        android.widget.ImageView imageView = new android.widget.ImageView(getContext());
        imageView.setImageBitmap(qrBitmap);
        imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        imageView.setBackgroundColor(Color.WHITE);
        imageView.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(imageView);
        dialog.getWindow().setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        );
        dialog.show();
    }
    
    // Start QR scanner - show dialog to choose camera or gallery
    private void startKOQrScanner() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Import from QR Code")
            .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                if (which == 0) {
                    launchKOCameraScanner();
                } else {
                    imagePickerLauncher.launch("image/*");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // Launch camera scanner
    private void launchKOCameraScanner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), 
                new String[]{Manifest.permission.CAMERA}, 100);
            return;
        }
        
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan KO Data QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
        qrScannerLauncher.launch(options);
    }
    
    // Decode QR code from gallery image
    private void decodeQrFromImage(Uri imageUri) {
        try {
            Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
            
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            
            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(binaryBitmap);
            
            if (result != null && result.getText() != null) {
                handleKOQrScanResult(result.getText());
            } else {
                Toast.makeText(getContext(), "No QR code found in image", Toast.LENGTH_SHORT).show();
            }
        } catch (com.google.zxing.NotFoundException e) {
            Toast.makeText(getContext(), "No QR code found in image", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error decoding QR from image: " + e.getMessage());
            Toast.makeText(getContext(), "Error reading image", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Launch QR scanner for KO data (kept for compatibility)
    private void launchKOQrScanner() {
        launchKOCameraScanner();
    }
    
    // Handle scanned QR code result for KO data
    private void handleKOQrScanResult(String scannedData) {
        String decompressed = decompressKOData(scannedData);
        if (decompressed == null) {
            Toast.makeText(getContext(), "Invalid QR code data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            String[] lines = decompressed.split("\n");
            List<String> lineList = new ArrayList<>();
            for (String line : lines) {
                lineList.add(line);
            }
            
            // Process matches from QR data (same format as backup)
            processRestoredMatches(lineList);
            
            Toast.makeText(getContext(), "KO data imported from QR", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("KOFragment", "Error parsing QR data: " + e.getMessage());
            Toast.makeText(getContext(), "Error parsing QR data", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Process restored matches from CSV lines (used by both REPLACE and QR IN)
    private void processRestoredMatches(List<String> lines) {
        int koSize = 8;
        boolean repechageEnabled = false;
        
        // Parse metadata line
        for (String line : lines) {
            if (line.startsWith("#META,")) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    koSize = Integer.parseInt(parts[1].trim());
                    repechageEnabled = Boolean.parseBoolean(parts[2].trim());
                }
                break;
            }
        }
        
        // Setup number of participants from koSize
        koNrPart = koSize;
        koRepechage = repechageEnabled;
        
        // Load appropriate KO tree structure
        loadKOTree(koNrPart, koRepechage);
        
        // Update checkbox state
        if (repechageCheckboxRef != null) {
            repechageCheckboxRef.setOnCheckedChangeListener(null);
            repechageCheckboxRef.setChecked(koRepechage);
            repechageCheckboxRef.setOnCheckedChangeListener((buttonView, isChecked) -> {
                koRepechage = isChecked;
                int nrPartVal = getKONrPart();
                
                // Save ALL match results before reloading using round/match indices
                java.util.Map<String, int[]> savedResults = new java.util.HashMap<>();
                for (int r = 0; r < koRounds.size(); r++) {
                    List<Match> round = koRounds.get(r);
                    for (int mi = 0; mi < round.size(); mi++) {
                        Match m = round.get(mi);
                        if (m.score1 >= 0 && m.score2 >= 0) {
                            String key = "R1|R" + r + "|M" + mi;
                            savedResults.put(key, new int[]{m.score1, m.score2});
                        }
                    }
                }
                for (RepechageTree tree : allRepechageTrees) {
                    if (tree.treeId.equals("R1")) continue;
                    for (int r = 0; r < tree.rounds.size(); r++) {
                        List<Match> round = tree.rounds.get(r);
                        for (int mi = 0; mi < round.size(); mi++) {
                            Match m = round.get(mi);
                            if (m.score1 >= 0 && m.score2 >= 0) {
                                String key = tree.treeId + "|R" + r + "|M" + mi;
                                savedResults.put(key, new int[]{m.score1, m.score2});
                            }
                        }
                    }
                }
                
                loadKOTree(nrPartVal, koRepechage);
                
                // Restore saved match results to main bracket
                for (int r = 0; r < koRounds.size(); r++) {
                    List<Match> round = koRounds.get(r);
                    for (int mi = 0; mi < round.size(); mi++) {
                        Match m = round.get(mi);
                        String key = "R1|R" + r + "|M" + mi;
                        if (savedResults.containsKey(key)) {
                            int[] scores = savedResults.get(key);
                            m.score1 = scores[0];
                            m.score2 = scores[1];
                            if (m.score1 > m.score2) {
                                m.winner = getKOName(m.p1, getKOParticipantNames());
                            } else if (m.score2 > m.score1) {
                                m.winner = getKOName(m.p2, getKOParticipantNames());
                            }
                        }
                    }
                }
                // Restore saved match results to all repechage trees
                for (RepechageTree tree : allRepechageTrees) {
                    if (tree.treeId.equals("R1")) continue;
                    for (int r = 0; r < tree.rounds.size(); r++) {
                        List<Match> round = tree.rounds.get(r);
                        for (int mi = 0; mi < round.size(); mi++) {
                            Match m = round.get(mi);
                            String key = tree.treeId + "|R" + r + "|M" + mi;
                            if (savedResults.containsKey(key)) {
                                int[] scores = savedResults.get(key);
                                m.score1 = scores[0];
                                m.score2 = scores[1];
                                if (m.score1 > m.score2) {
                                    m.winner = getKOName(m.p1, getKOParticipantNames());
                                } else if (m.score2 > m.score1) {
                                    m.winner = getKOName(m.p2, getKOParticipantNames());
                                }
                            }
                        }
                    }
                }
                
                String[] participantNames = getKOParticipantNames();
                if (participantNames != null) {
                    autoAdvanceEmptyMatches(participantNames);
                    if (koRepechage && !losersRounds.isEmpty()) {
                        propagateLosersToLosersBracket(participantNames);
                        autoAdvanceEmptyMatchesInLosersBracket(participantNames);
                    }
                }
                propagateKOWinners();
                renderKOTable(koBoxLayoutRef);
            });
        }
        
        // Apply match data from lines
        for (String line : lines) {
            if (line.startsWith("#") || line.startsWith("Tree,")) continue;
            String[] parts = line.split(",", -1);
            if (parts.length < 7) continue;
            
            String treeId = parts[0].trim();
            int roundIdx, matchIdx;
            try {
                roundIdx = Integer.parseInt(parts[1].trim());
                matchIdx = Integer.parseInt(parts[2].trim());
            } catch (Exception e) { continue; }
            
            String p1 = parts[3];
            String score1Str = parts[4];
            String p2 = parts[5];
            String score2Str = parts[6];
            
            int s1 = -1, s2 = -1;
            try { if (!score1Str.isEmpty()) s1 = Integer.parseInt(score1Str); } catch (Exception e) {}
            try { if (!score2Str.isEmpty()) s2 = Integer.parseInt(score2Str); } catch (Exception e) {}
            
            if (treeId.equals("R1") && roundIdx > 0 && roundIdx <= koRounds.size()) {
                List<Match> round = koRounds.get(roundIdx - 1);
                if (matchIdx > 0 && matchIdx <= round.size()) {
                    Match m = round.get(matchIdx - 1);
                    m.p1 = p1;
                    m.p2 = p2;
                    m.score1 = s1;
                    m.score2 = s2;
                }
            } else if (!treeId.equals("R1") && koRepechage) {
                for (RepechageTree tree : allRepechageTrees) {
                    if (tree.treeId.equals(treeId) && roundIdx > 0 && roundIdx <= tree.rounds.size()) {
                        List<Match> round = tree.rounds.get(roundIdx - 1);
                        if (matchIdx > 0 && matchIdx <= round.size()) {
                            Match m = round.get(matchIdx - 1);
                            m.p1 = p1;
                            m.p2 = p2;
                            m.score1 = s1;
                            m.score2 = s2;
                        }
                        break;
                    }
                }
            }
        }
        
        // Render the KO table
        if (koBoxLayoutRef != null) {
            renderKOTable(koBoxLayoutRef);
        }
        
        // Auto-advance and propagate matches after restore
        String[] participantNames = getKOParticipantNames();
        if (participantNames != null) {
            autoAdvanceEmptyMatches(participantNames);
            if (koRepechage && !losersRounds.isEmpty()) {
                propagateLosersToLosersBracket(participantNames);
                autoAdvanceEmptyMatchesInLosersBracket(participantNames);
            }
            // Re-render after propagation
            if (koBoxLayoutRef != null) {
                renderKOTable(koBoxLayoutRef);
            }
        }
        
        // Enable repechage checkbox if repechage trees are detected
        if (repechageCheckboxRef != null && koRepechage) {
            repechageCheckboxRef.setEnabled(true);
        }
    }
}
