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
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.net.Uri;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import androidx.activity.result.contract.ActivityResultContracts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.fencing.scores.ScoresViewModel;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.viewpager2.widget.ViewPager2;
import android.util.TypedValue;
import android.util.DisplayMetrics;
import com.fencing.scores.R;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanOptions;
import com.journeyapps.barcodescanner.ScanContract;

public class RoundFragment extends Fragment {
                            // Flag to suspend UI/observers during atomic participant count changes
                            private boolean suspendObservers = false;
                        // Update Help text in the last P data cell
                        private void updateHelpTextInLastPCell(TableLayout tableLayout, int nrPart, int lastEmptyP) {
                            if (lastEmptyP != -1) {
                                TableRow helpRow = (TableRow) tableLayout.getChildAt(lastEmptyP + 1); // +1 for header
                                if (helpRow != null && helpRow.getChildCount() > nrPart + 7) {
                                    TextView pCell = (TextView) helpRow.getChildAt(nrPart + 7);
                                    pCell.setText("Help");
                                }
                            }
                        }

                        // Helper to find the last empty P cell index
                        private int findLastEmptyP() {
                            int nrPart = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
                            String[] participantNames = scoresViewModel.getParticipantNames().getValue();
                            if (participantNames == null) return -1;
                            int lastEmptyP = -1;
                            // Bounds check: iterate only up to actual array length
                            int maxIdx = Math.min(nrPart, participantNames.length);
                            for (int i = 0; i < maxIdx; i++) {
                                if (participantNames[i] == null || participantNames[i].isEmpty()) {
                                    lastEmptyP = i;
                                }
                            }
                            return lastEmptyP;
                        }
                        // ...existing code...
                    // Show upcoming bouts popup if 4-14 participants
                    private void showUpcomingBoutsPopup() {
                        int nrPart = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
                        String[] participantNames = scoresViewModel.getParticipantNames().getValue();
                        int[][] boutResults = scoresViewModel.getBoutResults().getValue();
                        if (participantNames == null || boutResults == null) return;
                        // Count active participants
                        java.util.List<String> activeParticipants = new java.util.ArrayList<>();
                        for (int i = 0; i < nrPart; i++) {
                            if (participantNames[i] != null && !participantNames[i].trim().isEmpty()) {
                                activeParticipants.add(participantNames[i]);
                            }
                        }
                        int activeCount = activeParticipants.size();
                        if (activeCount < 4 || activeCount > 12) {
                            android.widget.Toast.makeText(getContext(), "Upcoming bouts only available for 4-12 participants", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Get bout order from assets/BoutOrder.txt
                        String boutOrder = null;
                        try {
                            java.io.InputStream is = getContext().getAssets().open("BoutOrder.txt");
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                            String line;
                            String poolPrefix = "pool of " + activeCount;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().toLowerCase().startsWith(poolPrefix)) {
                                    boutOrder = reader.readLine();
                                    break;
                                }
                            }
                            reader.close();
                            is.close();
                        } catch (Exception e) {}
                        if (boutOrder == null || boutOrder.trim().isEmpty()) {
                            android.widget.Toast.makeText(getContext(), "No bout order found for this pool size", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        java.util.List<String> partSeq = new java.util.ArrayList<>();
                        String[] bouts = boutOrder.trim().split(" ");
                        for (String bout : bouts) {
                            bout = bout.trim();
                            if (bout.isEmpty()) continue;
                            String[] ps = bout.split("-");
                            if (ps.length == 2) {
                                try {
                                    int p1 = Integer.parseInt(ps[0]) - 1;
                                    int p2 = Integer.parseInt(ps[1]) - 1;
                                    if (p1 >= 0 && p1 < activeCount && p2 >= 0 && p2 < activeCount) {
                                        int actualP1 = p1, actualP2 = p2;
                                        // Find actual indices in full array
                                        for (int i = 0, found = 0; i < nrPart && (found < 2); i++) {
                                            if (participantNames[i] != null && !participantNames[i].trim().isEmpty()) {
                                                if (found == p1) actualP1 = i;
                                                if (found == p2) actualP2 = i;
                                                found++;
                                            }
                                        }
                                        if (boutResults[actualP1][actualP2] == -1) {
                                            partSeq.add(activeParticipants.get(p1) + " - " + activeParticipants.get(p2));
                                        }
                                    }
                                } catch (Exception e) {}
                            }
                        }
                        if (partSeq.isEmpty()) {
                            android.widget.Toast.makeText(getContext(), "No upcoming bouts available", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Show dialog with up to 6 upcoming bouts
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
                        builder.setTitle("Next Bouts");
                        StringBuilder content = new StringBuilder();
                        int boutsToShow = Math.min(6, partSeq.size());
                        for (int i = 0; i < boutsToShow; i++) {
                            content.append((i + 1)).append(". ").append(partSeq.get(i)).append("\n");
                        }
                        android.widget.TextView textView = new android.widget.TextView(getContext());
                        textView.setText(content.toString());
                        textView.setTextSize(16);
                        textView.setPadding(40, 40, 40, 40);
                        textView.setTextColor(android.graphics.Color.BLACK);
                        builder.setView(textView);
                        builder.setCancelable(true);
                        final android.app.AlertDialog dialog = builder.create();
                        dialog.show();
                        new android.os.Handler().postDelayed(() -> { if (dialog.isShowing()) dialog.dismiss(); }, 5000);
                    }
                // Sort all participants and results by P ranking and reload matrix
                private void sortByPRankingAndReload() {
                    int nrPart = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
                    String[] participantNames = scoresViewModel.getParticipantNames().getValue();
                    int[][] boutResults = scoresViewModel.getBoutResults().getValue();
                    if (participantNames == null || boutResults == null || participantNames.length != nrPart || boutResults.length != nrPart) return;

                    // Build sortable list: index, name, percent, index, given, victories, hasBout, row bout results
                    class Row {
                        int idx;
                        String name;
                        int percent;
                        int index;
                        int given;
                        int victories;
                        boolean hasBout;
                        int[] bouts;
                        Row(int idx, String name, int percent, int index, int given, int victories, boolean hasBout, int[] bouts) {
                            this.idx = idx;
                            this.name = name;
                            this.percent = percent;
                            this.index = index;
                            this.given = given;
                            this.victories = victories;
                            this.hasBout = hasBout;
                            this.bouts = bouts;
                        }
                    }
                    java.util.List<Row> withBouts = new java.util.ArrayList<>();
                    java.util.List<Row> namedNoBouts = new java.util.ArrayList<>();
                    java.util.List<Row> emptyNames = new java.util.ArrayList<>();
                    for (int i = 0; i < nrPart; i++) {
                        // Defensive: check bounds before accessing arrays
                        String name = (participantNames != null && i < participantNames.length && participantNames[i] != null) ? participantNames[i] : "";
                        int victories = 0, given = 0, received = 0, boutsWon = 0, boutsLost = 0;
                        boolean hasBout = false;
                        for (int j = 0; j < nrPart; j++) {
                            if (i == j) continue;
                            boolean validI = boutResults != null && i < boutResults.length && boutResults[i] != null && j < boutResults[i].length;
                            boolean validJ = boutResults != null && j < boutResults.length && boutResults[j] != null && i < boutResults[j].length;
                            boolean validNames = participantNames != null && j < participantNames.length && participantNames[j] != null && !participantNames[j].isEmpty();
                            if (validI && validJ && boutResults[i][j] >= 0 && boutResults[j][i] >= 0 && !name.isEmpty() && validNames) {
                                int s = boutResults[i][j];
                                int o = boutResults[j][i];
                                hasBout = true;
                                if (s > o) { victories++; boutsWon++; }
                                else if (s < o) { boutsLost++; }
                                given += s;
                                received += o;
                            }
                        }
                        int idxVal = given - received;
                        int percent = (boutsWon + boutsLost) > 0 ? (int)Math.round((double)boutsWon / (boutsWon + boutsLost) * 100) : 0;
                        int[] safeClone = (boutResults != null && i < boutResults.length && boutResults[i] != null) ? boutResults[i].clone() : new int[0];
                        if (name.isEmpty()) {
                            emptyNames.add(new Row(i, name, percent, idxVal, given, victories, hasBout, safeClone));
                        } else if (hasBout) {
                            withBouts.add(new Row(i, name, percent, idxVal, given, victories, true, safeClone));
                        } else {
                            namedNoBouts.add(new Row(i, name, percent, idxVal, given, victories, false, safeClone));
                        }
                    }
                    // Sort withBouts by percent (desc), then index (desc), then given (desc)
                    withBouts.sort((a, b) -> {
                        int cmp = Integer.compare(b.percent, a.percent);
                        if (cmp != 0) return cmp;
                        cmp = Integer.compare(b.index, a.index);
                        if (cmp != 0) return cmp;
                        cmp = Integer.compare(b.given, a.given);
                        if (cmp != 0) return cmp;
                        return 0;
                    });
                    // Assign positions (standard competition ranking) for withBouts
                    int[] positions = new int[nrPart];
                    int pos = 1;
                    for (int i = 0; i < withBouts.size(); i++) {
                        if (i > 0) {
                            Row prev = withBouts.get(i - 1);
                            Row curr = withBouts.get(i);
                            boolean same = curr.percent == prev.percent && curr.index == prev.index && curr.given == prev.given;
                            if (!same) pos = i + 1;
                        }
                        positions[withBouts.get(i).idx] = pos;
                    }
                    // Now build the final sorted list: withBouts (by P), then namedNoBouts (by name), then emptyNames
                    java.util.List<Row> sortedRows = new java.util.ArrayList<>();
                    // Sort namedNoBouts by name (optional: or keep original order)
                    namedNoBouts.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                    sortedRows.addAll(withBouts);
                    sortedRows.addAll(namedNoBouts);
                    sortedRows.addAll(emptyNames);
                    // Rebuild participantNames and boutResults in sorted order
                    String[] sortedNames = new String[nrPart];
                    int[][] sortedBouts = new int[nrPart][nrPart];
                    for (int i = 0; i < nrPart; i++) {
                        if (i < sortedRows.size()) {
                            Row row = sortedRows.get(i);
                            sortedNames[i] = row.name;
                        } else {
                            sortedNames[i] = "";
                        }
                    }
                    // Reorder boutResults matrix: for each i, j in sorted order, set sortedBouts[i][j] = boutResults[sortedRows.get(i).idx][sortedRows.get(j).idx];
                    for (int i = 0; i < nrPart; i++) {
                        for (int j = 0; j < nrPart; j++) {
                            int safeI = (i < sortedRows.size()) ? sortedRows.get(i).idx : -1;
                            int safeJ = (j < sortedRows.size()) ? sortedRows.get(j).idx : -1;
                            if (safeI >= 0 && safeJ >= 0 && boutResults != null && safeI < boutResults.length && boutResults[safeI] != null && safeJ < boutResults[safeI].length) {
                                sortedBouts[i][j] = boutResults[safeI][safeJ];
                            } else {
                                sortedBouts[i][j] = -1;
                            }
                        }
                    }
                    // Update ViewModel
                    scoresViewModel.setParticipantNames(sortedNames);
                    scoresViewModel.setBoutResults(sortedBouts);
                }
            // Reference to help dialog for file picker result
            private android.app.AlertDialog helpDialog = null;
        // ActivityResultLaunchers for file picker
        private androidx.activity.result.ActivityResultLauncher<android.content.Intent> saveFileLauncher;
        private androidx.activity.result.ActivityResultLauncher<android.content.Intent> loadFileLauncher;

    // Color pairs for result columns (cycled)
    private static final int[][] RESULT_COLOR_PAIRS = {
        {0xFFFFD700, 0xFFFF7F50}, // Default
        {0xFF87CEFA, 0xFFB0C4DE},
        {0xFFFFFFE0, 0xFFF0E68C},
        {0xFF98FB98, 0xFF9ACD32},
        {0xFFA9A9A9, 0xFFDCDCDC},
        {0xFF00FFFF, 0xFF39E75F}
    };
    private static final int NR_BG_COLOR = 0xFFF5F5F5;
    private ScoresViewModel scoresViewModel;
    
    // QR Scanner launcher for Round data
    private final ActivityResultLauncher<ScanOptions> qrScannerLauncher = 
        registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                handleRoundQrScanResult(result.getContents());
            }
        });
    
    // Image picker launcher for QR from gallery
    private final ActivityResultLauncher<String> imagePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                decodeQrFromImage(uri);
            }
        });
    
    // Helper: mix a color with white (50% blend)
    private int mixWithWhite(int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        // Blend with white (255, 255, 255)
        r = (r + 255) / 2;
        g = (g + 255) / 2;
        b = (b + 255) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.matrix_table, container, false);
    }

    // Helper: navigate to next page in a loop (example: to MergedActivity)
    private void navigateToNextPage() {
        // Navigate to MergedFragment using MainActivity.navigateToPage
        if (getActivity() instanceof com.fencing.scores.MainActivity) {
            com.fencing.scores.MainActivity mainActivity = (com.fencing.scores.MainActivity) getActivity();
            mainActivity.navigateToPage(1); // 1 = MergedFragment
        }
    }
    
    // Helper: navigate to previous page (Round -> Final, wrapping)
    private void navigateToPreviousPage() {
        if (getActivity() instanceof com.fencing.scores.MainActivity) {
            com.fencing.scores.MainActivity mainActivity = (com.fencing.scores.MainActivity) getActivity();
            mainActivity.navigateToPage(3); // 3 = FinalFragment
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
                        // Add a button to show upcoming bouts
        // android.widget.Button showBoutsBtn = new android.widget.Button(getContext());
        // showBoutsBtn.setText("Show Upcoming Bouts");
        // showBoutsBtn.setOnClickListener(v -> showUpcomingBoutsPopup());
        // android.widget.LinearLayout container = (android.widget.LinearLayout) ((android.app.Activity)getContext()).findViewById(R.id.fragment_container);
        // if (container != null) {
        //     container.addView(showBoutsBtn, 0);
        // }
                // Register file pickers
                saveFileLauncher = registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (helpDialog != null && helpDialog.isShowing()) helpDialog.dismiss();
                        if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                            android.net.Uri uri = result.getData().getData();
                            if (uri != null) {
                                saveCsvToUri(uri);
                            }
                        }
                    }
                );
                loadFileLauncher = registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (helpDialog != null && helpDialog.isShowing()) helpDialog.dismiss();
                        if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                            android.net.Uri uri = result.getData().getData();
                            if (uri != null) {
                                loadCsvFromUri(uri);
                            }
                        }
                    }
                );
        super.onViewCreated(view, savedInstanceState);
        scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
        // Only restore automatically if crash detected
        // (No restore on normal start, only on crash or RESTORE button)
        android.util.Log.d("RoundFragment", "onViewCreated: crashDetected=" + com.fencing.scores.MainActivity.crashDetected);
        if (com.fencing.scores.MainActivity.crashDetected) {
            android.util.Log.d("RoundFragment", "Restoring from backup due to crash");
            restoreFromDefaultBackupCompat();
        } else {
            // On normal start, clear table to default empty state
            android.util.Log.d("RoundFragment", "Normal start - resetting to default");
            scoresViewModel.resetToDefault();
        }
        // Observe changes and update matrix (only when this fragment is visible/resumed)
        scoresViewModel.getParticipantNames().observe(getViewLifecycleOwner(), names -> {
            if (!suspendObservers && isResumed()) {
                createMatrix(view);
                updateHelpTextInLastPCell((TableLayout) view.findViewById(R.id.tableLayout),
                    scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
                    findLastEmptyP());
            }
        });
        scoresViewModel.getBoutResults().observe(getViewLifecycleOwner(), results -> {
            if (!suspendObservers && isResumed()) {
                createMatrix(view);
                updateHelpTextInLastPCell((TableLayout) view.findViewById(R.id.tableLayout),
                    scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
                    findLastEmptyP());
            }
        });
        scoresViewModel.getNrPart().observe(getViewLifecycleOwner(), n -> {
            if (!suspendObservers && isResumed()) {
                createMatrix(view);
                updateHelpTextInLastPCell((TableLayout) view.findViewById(R.id.tableLayout),
                    scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
                    findLastEmptyP());
            }
        });
        scoresViewModel.getColorCycleIndex().observe(getViewLifecycleOwner(), idx -> {
            if (!suspendObservers && isResumed()) {
                createMatrix(view);
                updateHelpTextInLastPCell((TableLayout) view.findViewById(R.id.tableLayout),
                    scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
                    findLastEmptyP());
            }
        });
        createMatrix(view);
        updateHelpTextInLastPCell((TableLayout) view.findViewById(R.id.tableLayout),
            scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
            findLastEmptyP());
    }

    private void cycleResultColors() {
        int idx = scoresViewModel.getColorCycleIndex().getValue() != null ? scoresViewModel.getColorCycleIndex().getValue() : 0;
        int nextIdx = (idx + 1) % RESULT_COLOR_PAIRS.length;
        scoresViewModel.setColorCycleIndex(nextIdx);
    }

    private void createMatrix(View root) {
        TableLayout tableLayout = root.findViewById(R.id.tableLayout);
        tableLayout.removeAllViews();
        int nrPart = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
        String[] participantNames = scoresViewModel.getParticipantNames().getValue();
        int[][] boutResults = scoresViewModel.getBoutResults().getValue();
        int colorIdx = scoresViewModel.getColorCycleIndex().getValue() != null ? scoresViewModel.getColorCycleIndex().getValue() : 0;
        // Defensive: if arrays are not the expected size, wait for LiveData update
        if (participantNames == null || participantNames.length != nrPart || boutResults == null || boutResults.length != nrPart) {
            return;
        }
        // Calculate dynamic cell height to fill screen
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenHeight = metrics.heightPixels;
        int headerRows = 1;
        int totalRows = nrPart + headerRows;
        int cellHeight = (int) ((screenHeight * 0.95f) / totalRows); // Use 95% of screen height
        TableRow headerRow = createHeaderRow(nrPart, colorIdx, cellHeight);
        tableLayout.addView(headerRow);
        int lastEmptyP = -1;
        boolean hasAnyResult = false;
        for (int i = 0; i < nrPart; i++) {
            participantNames = scoresViewModel.getParticipantNames().getValue();
            boutResults = scoresViewModel.getBoutResults().getValue();
            TableRow row = createParticipantRow(i, tableLayout, participantNames, boutResults, nrPart, colorIdx, cellHeight);
            tableLayout.addView(row);
            if (participantNames == null || participantNames[i] == null || participantNames[i].isEmpty()) {
                lastEmptyP = i;
            }
            // Check if any result is entered for this participant
            if (boutResults != null && i < boutResults.length) {
                for (int j = 0; j < nrPart; j++) {
                    if (i != j && boutResults[i][j] >= 0 && participantNames != null && participantNames[j] != null && !participantNames[j].isEmpty()) {
                        hasAnyResult = true;
                        break;
                    }
                }
            }
        }
        // Help text logic will be refactored into a reusable method below
    }

    private TableRow createHeaderRow(int nrPart) {
        return createHeaderRow(nrPart, 0);
    }

    private TableRow createHeaderRow(int nrPart, int colorIdx) {
        return createHeaderRow(nrPart, colorIdx, -1);
    }

    // Overload with cellHeight
    private TableRow createHeaderRow(int nrPart, int colorIdx, int cellHeight) {
        TableRow row = new TableRow(getContext());
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            cellHeight > 0 ? cellHeight : TableLayout.LayoutParams.WRAP_CONTENT));
        String[] headers = new String[nrPart + 8];
        headers[0] = "Nr";
        headers[1] = "Name";
        for (int i = 0; i < nrPart; i++) {
            headers[i + 2] = String.valueOf(i + 1);
        }
        headers[nrPart + 2] = "V";
        headers[nrPart + 3] = "->";
        headers[nrPart + 4] = "<-";
        headers[nrPart + 5] = "I";
        headers[nrPart + 6] = "%";
        headers[nrPart + 7] = "P";
        for (int i = 0; i < headers.length; i++) {
            TextView cell = createHeaderCell(headers[i], cellHeight, nrPart);
            int[] pair = RESULT_COLOR_PAIRS[colorIdx % RESULT_COLOR_PAIRS.length];
            // Set #A0A0A0 for Nr header, Names header, and bout results headers
            if (i == 0 || i == 1 || (i >= 2 && i < nrPart + 2)) {
                cell.setBackgroundColor(0xFFA0A0A0);
            } else if (i >= nrPart + 2 && i <= nrPart + 6) {
                cell.setBackgroundColor(pair[0]); // C, →, ←, I, %
                cell.setOnClickListener(v -> {
                    int nextIdx = (colorIdx + 1) % RESULT_COLOR_PAIRS.length;
                    scoresViewModel.setColorCycleIndex(nextIdx);
                });
            } else if (i == nrPart + 7) {
                cell.setBackgroundColor(pair[1]);
                // Add long click to sort by P ranking and update matrix
                cell.setOnLongClickListener(v -> {
                    sortByPRankingAndReload();
                    // Update the matrix after sorting
                    createMatrix(getView());
                    return true;
                });
            }
            // Add export to CSV on header click (was long-press)
                if (i != nrPart + 7) { // Do not set short click for P header
                    cell.setOnClickListener(v -> saveCsvDirectToDocumentsAuto());
                }
            row.addView(cell);
        }
        return row;
    }
    // Save CSV with file picker, default Documents folder, default name Results.csv
    private void saveCsvDirectToDocumentsAuto() {
        saveCsvDirectToDocuments();
    }


    private TableRow createParticipantRow(final int participantIndex, TableLayout tableLayout, String[] participantNames, int[][] boutResults, int nrPart) {
        return createParticipantRow(participantIndex, tableLayout, participantNames, boutResults, nrPart, 0);
    }

    private TableRow createParticipantRow(final int participantIndex, TableLayout tableLayout, String[] participantNames, int[][] boutResults, int nrPart, int colorIdx) {
        return createParticipantRow(participantIndex, tableLayout, participantNames, boutResults, nrPart, colorIdx, -1);
    }

    // Overload with cellHeight
    private TableRow createParticipantRow(final int participantIndex, TableLayout tableLayout, String[] participantNames, int[][] boutResults, int nrPart, int colorIdx, int cellHeight) {
                // Defensive: if arrays are not yet resized, return a placeholder row to avoid crash
                if ((participantNames == null || participantNames.length <= participantIndex) || (boutResults == null || boutResults.length <= participantIndex)) {
                    TableRow row = new TableRow(getContext());
                    TextView nrCell = createDataCell(String.valueOf(participantIndex + 1), true, cellHeight, nrPart);
                    nrCell.setBackgroundColor(0xFFA0A0A0);
                    nrCell.setBackgroundResource(R.drawable.cell_border);
                    row.addView(nrCell);
                    TextView nameCell = createDataCell("", true, cellHeight, nrPart);
                    nameCell.setBackgroundColor(0xFFA0A0A0);
                    row.addView(nameCell);
                    // Add empty cells for the rest of the columns
                    for (int j = 0; j < nrPart + 6; j++) {
                        TextView emptyCell = createDataCell("", false, cellHeight, nrPart);
                        row.addView(emptyCell);
                    }
                    return row;
                }
        TableRow row = new TableRow(getContext());
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            cellHeight > 0 ? cellHeight : TableLayout.LayoutParams.WRAP_CONTENT));
        // Defensive: ensure arrays are valid and sized
        String safeName = "";
        if (participantNames != null && participantIndex < participantNames.length) {
            String n = participantNames[participantIndex];
            if (n != null) safeName = n;
        }
        TextView nrCell = createDataCell(String.valueOf(participantIndex + 1), true, cellHeight, nrPart);
        nrCell.setBackgroundColor(0xFFA0A0A0);
        nrCell.setBackgroundResource(R.drawable.cell_border);
        // Click on any Nr cell shows upcoming bouts popup
        nrCell.setOnClickListener(v -> {
            showUpcomingBoutsPopup();
        });
        // Long press actions: cells 1 to floor(N/2) remove, cells floor(N/2)+1 to N add
        int halfPoint = nrPart / 2;
        if (participantIndex < halfPoint) {
            // Cells 1 to floor(N/2): Remove last participant
            nrCell.setOnLongClickListener(v -> {
                int current = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
                if (current > ScoresViewModel.MIN_PARTICIPANTS) {
                    suspendObservers = true;
                    scoresViewModel.setNrPart(current - 1);
                    suspendObservers = false;
                    View root = getView();
                    if (root != null) {
                        createMatrix(root);
                        updateHelpTextInLastPCell((TableLayout) root.findViewById(R.id.tableLayout),
                            scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
                            findLastEmptyP());
                    }
                }
                return true;
            });
        } else {
            // Cells floor(N/2)+1 to N: Add new participant
            nrCell.setOnLongClickListener(v -> {
                int current = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
                if (current < ScoresViewModel.MAX_PARTICIPANTS) {
                    suspendObservers = true;
                    scoresViewModel.setNrPart(current + 1);
                    suspendObservers = false;
                    View root = getView();
                    if (root != null) {
                        createMatrix(root);
                        updateHelpTextInLastPCell((TableLayout) root.findViewById(R.id.tableLayout),
                            scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
                            findLastEmptyP());
                    }
                }
                return true;
            });
        }
        row.addView(nrCell);
        TextView nameCell = createDataCell(safeName, true, cellHeight, nrPart);
        nameCell.setBackgroundColor(0xFFA0A0A0);
        nameCell.setOnClickListener(v -> showNameEditDialog(participantIndex, nameCell));
        row.addView(nameCell);

        // Calculate results for this participant
        int victories = 0;
        int given = 0;
        int received = 0;
        int boutsWon = 0;
        int boutsLost = 0;
        boolean hasAnyBoutResult = false;
        for (int j = 0; j < nrPart; j++) {
            TextView boutCell = createDataCell("", false, cellHeight, nrPart);
            final int opponentIndex = j;
            if (participantIndex != opponentIndex) {
                int score = -1;
                int oppScore = -1;
                // Defensive: check bounds before accessing boutResults
                if (boutResults != null && participantIndex < boutResults.length && opponentIndex < boutResults[participantIndex].length) {
                    score = boutResults[participantIndex][opponentIndex];
                }
                if (boutResults != null && opponentIndex < boutResults.length && participantIndex < boutResults[opponentIndex].length) {
                    oppScore = boutResults[opponentIndex][participantIndex];
                }
                boolean nameValid = participantNames != null
                        && participantNames.length > participantIndex
                        && participantNames.length > opponentIndex
                        && participantNames[participantIndex] != null
                        && participantNames[opponentIndex] != null
                        && !participantNames[participantIndex].isEmpty()
                        && !participantNames[opponentIndex].isEmpty();
                if (score >= 0 && oppScore >= 0 && nameValid) {
                    hasAnyBoutResult = true;
                    boutCell.setText(String.valueOf(score));
                    if (score > oppScore) {
                        boutCell.setTextColor(Color.BLUE);
                        victories++;
                        boutsWon++;
                    } else if (score < oppScore) {
                        boutCell.setTextColor(Color.RED);
                        boutsLost++;
                    } else {
                        boutCell.setTextColor(Color.BLACK);
                    }
                    given += score;
                    received += oppScore;
                } else {
                    boutCell.setText("");
                }
                // Determine background color: for N>=7, highlight rows/columns 5,10,15,20,25...
                int boutBgColor = Color.WHITE;
                if (nrPart >= 7) {
                    int[] currentPair = RESULT_COLOR_PAIRS[colorIdx % RESULT_COLOR_PAIRS.length];
                    int rowNum = participantIndex + 1; // 1-based
                    int colNum = opponentIndex + 1;    // 1-based
                    if (rowNum % 5 == 0 || colNum % 5 == 0) {
                        boutBgColor = mixWithWhite(currentPair[0]);
                    }
                }
                boutCell.setBackground(makeBorderedCell(boutBgColor));
                // Defensive: prevent crash if either participant has empty or null name or out of bounds
                boutCell.setOnClickListener(v -> {
                    if (participantNames == null || participantNames.length <= Math.max(participantIndex, opponentIndex)
                        || participantNames[participantIndex] == null || participantNames[opponentIndex] == null
                        || participantNames[participantIndex].trim().isEmpty() || participantNames[opponentIndex].trim().isEmpty()) {
                        return;
                    }
                    if (boutResults == null || participantIndex >= boutResults.length || opponentIndex >= boutResults.length) {
                        return;
                    }
                    showBoutResultDialog(participantIndex, opponentIndex, boutCell, tableLayout);
                });
            } else {
                boutCell.setBackground(makeBorderedCell(Color.BLACK));
            }
            row.addView(boutCell);
        }
        int[] pair = RESULT_COLOR_PAIRS[colorIdx % RESULT_COLOR_PAIRS.length];
        // Only show results if at least one bout result is set for this participant
        String vStr = "";
        String givenStr = "";
        String receivedStr = "";
        String indexStr = "";
        String percent = "";
        String positionStr = "";
        boolean hasName = participantNames[participantIndex] != null && !participantNames[participantIndex].isEmpty();
        if (hasName && hasAnyBoutResult) {
            vStr = String.valueOf(victories);
            givenStr = String.valueOf(given);
            receivedStr = String.valueOf(received);
            indexStr = String.valueOf(given - received);
            if ((boutsWon + boutsLost) > 0) {
                percent = String.valueOf((int) Math.round((double) boutsWon / (boutsWon + boutsLost) * 100));
            }
            // Calculate P (position) for all participants with names and at least one bout
            java.util.List<Integer> validIndices = new java.util.ArrayList<>();
            for (int i = 0; i < nrPart; i++) {
                boolean hasBout = false;
                if (participantNames[i] != null && !participantNames[i].isEmpty()) {
                    for (int j = 0; j < nrPart; j++) {
                        if (i != j && boutResults[i][j] >= 0 && boutResults[j][i] >= 0 &&
                            participantNames[j] != null && !participantNames[j].isEmpty()) {
                            hasBout = true;
                            break;
                        }
                    }
                }
                if (hasBout) validIndices.add(i);
            }
            java.util.List<int[]> stats = new java.util.ArrayList<>(); // [index, percent, indexValue, given]
            for (int idx : validIndices) {
                int v = 0, g = 0, r = 0, bw = 0, bl = 0;
                String n = participantNames[idx] != null ? participantNames[idx] : "";
                for (int j = 0; j < nrPart; j++) {
                    if (idx != j && boutResults[idx][j] >= 0 && boutResults[j][idx] >= 0 &&
                        !n.isEmpty() && participantNames[j] != null && !participantNames[j].isEmpty()) {
                        int s = boutResults[idx][j];
                        int o = boutResults[j][idx];
                        if (s > o) { v++; bw++; }
                        else if (s < o) { bl++; }
                        g += s;
                        r += o;
                    }
                }
                int idxVal = g - r;
                int pct = (bw + bl) > 0 ? (int)Math.round((double)bw / (bw + bl) * 100) : 0;
                stats.add(new int[]{idx, pct, idxVal, g});
            }
            // Sort for ranking: % DESC, I DESC, → DESC
            stats.sort((a, b) -> {
                int cmp = Integer.compare(b[1], a[1]); // percent DESC
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b[2], a[2]); // indexValue DESC
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b[3], a[3]); // given DESC
                if (cmp != 0) return cmp;
                return 0;
            });
            int[] positions = new int[nrPart];
            int pos = 1;
            for (int i = 0; i < stats.size(); i++) {
                if (i > 0) {
                    int[] prev = stats.get(i - 1);
                    int[] curr = stats.get(i);
                    // Same %, I, → = same position (tie)
                    boolean same = curr[1] == prev[1] && curr[2] == prev[2] && curr[3] == prev[3];
                    if (!same) pos = i + 1;
                }
                positions[stats.get(i)[0]] = pos;
            }
            // If this participant is not in the valid list, leave P empty
            if (validIndices.contains(participantIndex)) {
                positionStr = String.valueOf(positions[participantIndex]);
            } else {
                positionStr = "";
            }
        }
        TextView vCell = createDataCell(vStr, true, cellHeight, nrPart);
        vCell.setBackground(makeBorderedCell(pair[0]));
        vCell.setOnClickListener(v -> {
            int nextIdx = (colorIdx + 1) % RESULT_COLOR_PAIRS.length;
            scoresViewModel.setColorCycleIndex(nextIdx);
        });
        vCell.setOnLongClickListener(v -> {
            navigateToNextPage();
            return true;
        });
        row.addView(vCell);
        TextView givenCell = createDataCell(givenStr, true, cellHeight, nrPart);
        givenCell.setBackground(makeBorderedCell(pair[0]));
        givenCell.setOnClickListener(v -> {
            int nextIdx = (colorIdx + 1) % RESULT_COLOR_PAIRS.length;
            scoresViewModel.setColorCycleIndex(nextIdx);
        });
        givenCell.setOnLongClickListener(v -> {
            navigateToNextPage();
            return true;
        });
        row.addView(givenCell);
        TextView receivedCell = createDataCell(receivedStr, true, cellHeight, nrPart);
        receivedCell.setBackground(makeBorderedCell(pair[0]));
        receivedCell.setOnClickListener(v -> {
            int nextIdx = (colorIdx + 1) % RESULT_COLOR_PAIRS.length;
            scoresViewModel.setColorCycleIndex(nextIdx);
        });
        receivedCell.setOnLongClickListener(v -> {
            navigateToNextPage();
            return true;
        });
        row.addView(receivedCell);
        TextView indexCell = createDataCell(indexStr, true, cellHeight, nrPart);
        indexCell.setBackground(makeBorderedCell(pair[0]));
        indexCell.setOnClickListener(v -> {
            int nextIdx = (colorIdx + 1) % RESULT_COLOR_PAIRS.length;
            scoresViewModel.setColorCycleIndex(nextIdx);
        });
        indexCell.setOnLongClickListener(v -> {
            navigateToNextPage();
            return true;
        });
        row.addView(indexCell);
        TextView percentCell = createDataCell(percent, true, cellHeight, nrPart);
        percentCell.setBackground(makeBorderedCell(pair[0]));
        percentCell.setOnClickListener(v -> {
            int nextIdx = (colorIdx + 1) % RESULT_COLOR_PAIRS.length;
            scoresViewModel.setColorCycleIndex(nextIdx);
        });
        percentCell.setOnLongClickListener(v -> {
            navigateToNextPage();
            return true;
        });
        row.addView(percentCell);
        TextView pCell = createDataCell(positionStr, true, cellHeight, nrPart);
        pCell.setBackground(makeBorderedCell(pair[1]));
        pCell.setOnClickListener(v -> {
            showHelpDialog();
            // Also update Help text in last P cell when Help menu is opened
            TableLayout tableLayout2 = (TableLayout) ((View) v.getParent().getParent()).findViewById(R.id.tableLayout);
            int nrPart2 = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
            updateHelpTextInLastPCell(tableLayout2, nrPart2, findLastEmptyP());
        });
        // Add long-press to sort by P ranking (same as P header)
        pCell.setOnLongClickListener(v -> {
            sortByPRankingAndReload();
            createMatrix(getView());
            return true;
        });
        row.addView(pCell);
        return row;
    }
    // Save CSV directly to Documents with unique filename (Results.csv, Results_001.csv, ...)
    private void saveCsvDirectToDocuments() {
        // Use Storage Access Framework file picker for saving CSV
        android.content.Context ctx = getContext();
        if (ctx == null) return;
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(android.content.Intent.EXTRA_TITLE, "Results.csv");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"));
            } catch (Exception e) { /* ignore */ }
        }
        saveFileLauncher.launch(intent);
        // All CSV saving logic is handled in the ActivityResultLauncher callback (see onViewCreated)
    }

    // Helper to create a colored cell with border
    private android.graphics.drawable.GradientDrawable makeBorderedCell(int fillColor) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(fillColor);
        d.setStroke(2, android.graphics.Color.BLACK);
        d.setCornerRadius(0f);
        return d;
    }

    // Helper: set button background with rounded corners
    private void setRoundedBackground(android.widget.Button button, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(8 * getResources().getDisplayMetrics().density); // 8dp corners
        button.setBackground(drawable);
    }

    // Show the help dialog window
    private void showHelpDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Help");
        android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.GridLayout buttonGrid = new android.widget.GridLayout(getContext());
        buttonGrid.setColumnCount(3);
        buttonGrid.setRowCount(2);
        android.widget.Button loadBtn = new android.widget.Button(getContext());
        android.widget.Button restoreBtn = new android.widget.Button(getContext());
        android.widget.Button saveBtn = new android.widget.Button(getContext());
        android.widget.Button qrOutBtn = new android.widget.Button(getContext());
        android.widget.Button qrInBtn = new android.widget.Button(getContext());
        android.widget.Button quitBtn = new android.widget.Button(getContext());

        // Enable RESTORE if crash detected
        if (com.fencing.scores.MainActivity.crashDetected) {
            restoreBtn.setEnabled(true);
        }
        
        // Set button texts
        loadBtn.setText("LOAD");
        restoreBtn.setText("RESTORE");
        saveBtn.setText("SAVE");
        qrOutBtn.setText("QR OUT");
        qrInBtn.setText("QR IN");
        quitBtn.setText("QUIT");
        
        // Set colors - QUIT is red, all others blue
        int blueColor = 0xFF1976D2;
        int redColor = 0xFFD32F2F;
        
        setRoundedBackground(loadBtn, blueColor);
        loadBtn.setTextColor(Color.WHITE);
        setRoundedBackground(restoreBtn, blueColor);
        restoreBtn.setTextColor(Color.WHITE);
        setRoundedBackground(saveBtn, blueColor);
        saveBtn.setTextColor(Color.WHITE);
        setRoundedBackground(qrOutBtn, blueColor);
        qrOutBtn.setTextColor(Color.WHITE);
        setRoundedBackground(qrInBtn, blueColor);
        qrInBtn.setTextColor(Color.WHITE);
        setRoundedBackground(quitBtn, redColor);
        quitBtn.setTextColor(Color.WHITE);
        quitBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        
        // Layout params for grid items
        android.widget.GridLayout.LayoutParams paramLoad = new android.widget.GridLayout.LayoutParams();
        paramLoad.columnSpec = android.widget.GridLayout.spec(0);
        paramLoad.rowSpec = android.widget.GridLayout.spec(0);
        paramLoad.setMargins(4, 4, 4, 4);
        loadBtn.setLayoutParams(paramLoad);
        
        android.widget.GridLayout.LayoutParams paramRestore = new android.widget.GridLayout.LayoutParams();
        paramRestore.columnSpec = android.widget.GridLayout.spec(1);
        paramRestore.rowSpec = android.widget.GridLayout.spec(0);
        paramRestore.setMargins(4, 4, 4, 4);
        restoreBtn.setLayoutParams(paramRestore);
        
        android.widget.GridLayout.LayoutParams paramSave = new android.widget.GridLayout.LayoutParams();
        paramSave.columnSpec = android.widget.GridLayout.spec(2);
        paramSave.rowSpec = android.widget.GridLayout.spec(0);
        paramSave.setMargins(4, 4, 24, 4); // More margin on right for visibility
        saveBtn.setLayoutParams(paramSave);
        
        android.widget.GridLayout.LayoutParams paramQrOut = new android.widget.GridLayout.LayoutParams();
        paramQrOut.columnSpec = android.widget.GridLayout.spec(0);
        paramQrOut.rowSpec = android.widget.GridLayout.spec(1);
        paramQrOut.setMargins(4, 4, 4, 4);
        qrOutBtn.setLayoutParams(paramQrOut);
        
        android.widget.GridLayout.LayoutParams paramQrIn = new android.widget.GridLayout.LayoutParams();
        paramQrIn.columnSpec = android.widget.GridLayout.spec(1);
        paramQrIn.rowSpec = android.widget.GridLayout.spec(1);
        paramQrIn.setMargins(4, 4, 4, 4);
        qrInBtn.setLayoutParams(paramQrIn);
        
        android.widget.GridLayout.LayoutParams paramQuit = new android.widget.GridLayout.LayoutParams();
        paramQuit.columnSpec = android.widget.GridLayout.spec(2);
        paramQuit.rowSpec = android.widget.GridLayout.spec(1);
        paramQuit.setMargins(4, 4, 24, 4); // More margin on right for visibility
        quitBtn.setLayoutParams(paramQuit);
        
        buttonGrid.addView(loadBtn);
        buttonGrid.addView(restoreBtn);
        buttonGrid.addView(saveBtn);
        buttonGrid.addView(qrOutBtn);
        buttonGrid.addView(qrInBtn);
        buttonGrid.addView(quitBtn);
        layout.addView(buttonGrid);
        android.widget.TextView helpText = new android.widget.TextView(getContext());
        helpText.setPadding(10, 10, 10, 10);
        helpText.setTextSize(14);
        String helpContent = "Help content not available.";
        try {
            java.io.InputStream inputStream = getContext().getAssets().open("help.txt");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            inputStream.close();
            helpContent = content.toString();
        } catch (Exception e) {
            helpContent = "Error loading help content.";
        }
        helpText.setText(helpContent);
        helpText.setAutoLinkMask(android.text.util.Linkify.WEB_URLS | android.text.util.Linkify.EMAIL_ADDRESSES);
        helpText.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        helpText.setLinkTextColor(Color.BLUE);
        layout.addView(helpText);
        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setCancelable(true);
        final android.app.AlertDialog dialog = builder.create();
        this.helpDialog = dialog;
        // Set listeners after dialog is created so we can dismiss it
        saveBtn.setOnClickListener(v -> {
            dialog.dismiss();
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, "Results.csv");
            saveFileLauncher.launch(intent);
        });
        restoreBtn.setOnClickListener(v -> {
            dialog.dismiss();
            restoreFromDefaultBackupCompat();
        });
        loadBtn.setOnClickListener(v -> {
            dialog.dismiss();
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            loadFileLauncher.launch(intent);
        });
        qrOutBtn.setOnClickListener(v -> {
            dialog.dismiss();
            generateAndShowRoundQrCode();
        });
        qrInBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startRoundQrScanner();
        });
        quitBtn.setOnClickListener(v -> {
            dialog.dismiss();
            // Delete crash file before exiting to ensure clean start next time
            try {
                java.io.File documentsDir = new java.io.File(
                    requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "");
                java.io.File crashFile = new java.io.File(documentsDir, "CRASH.txt");
                boolean existed = crashFile.exists();
                boolean deleted = false;
                if (existed) deleted = crashFile.delete();
                android.util.Log.d("RoundFragment", "QUIT: deleteCrashFile path=" + crashFile.getAbsolutePath() + ", existed=" + existed + ", deleted=" + deleted);
            } catch (Exception e) {
                android.util.Log.e("RoundFragment", "QUIT: Error deleting crash file: " + e.getMessage());
            }
            requireActivity().finishAffinity();
        });
        dialog.setOnDismissListener(d -> this.helpDialog = null);
        dialog.show();
    }

    // Show dialog to edit participant name
    private void showNameEditDialog(int participantIndex, TextView nameCell) {
        String[] participantNames = scoresViewModel.getParticipantNames().getValue();
        if (participantNames == null) return;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Edit Name");
        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setText(participantNames[participantIndex]);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.requestFocus();
        // Show alphanumerical keyboard with capitalization
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        builder.setView(input);
        builder.setPositiveButton("Done", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            // Capitalize first letter if not empty
            if (!newName.isEmpty()) {
                newName = newName.substring(0, 1).toUpperCase() + newName.substring(1);
            }
            String oldName = participantNames[participantIndex];
            participantNames[participantIndex] = newName;
            scoresViewModel.setParticipantNames(participantNames);
            // If a new name is entered (not empty), do NOT reset or change bout results—they remain as is.
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        android.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        // Handle Enter key as Done
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        // Handle Enter key as OK
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
    }

    private void showBoutResultDialog(int participantA, int participantB, TextView boutCell, TableLayout tableLayout) {
        int nrPart = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
        String[] participantNames = scoresViewModel.getParticipantNames().getValue();
        int[][] boutResults = scoresViewModel.getBoutResults().getValue();
        String nameA = participantNames[participantA].isEmpty() ? "A" : participantNames[participantA];
        String nameB = participantNames[participantB].isEmpty() ? "B" : participantNames[participantB];
        // Use a one-element array to store the first score between popups
        final int[] firstScore = new int[1];
        firstScore[0] = Integer.MIN_VALUE;
        // Always prompt first for (row, col), then (col, row)
        showBoutScoreDialogFixed(participantA, participantB, nameA, nameB, tableLayout, boutResults, firstScore, participantA, participantB);
    }
    // (Removed duplicate showBoutScoreDialogFixed method declaration)
    private void showBoutScoreDialogFixed(int participantA, int participantB, String nameA, String nameB, TableLayout tableLayout, int[][] boutResults, int[] firstScore, int origA, int origB) {
        String title = nameA + "      Vs " + nameB;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(title);

        android.widget.GridLayout grid = new android.widget.GridLayout(getContext());
        grid.setColumnCount(6);
        grid.setRowCount(5); // 3 score rows + 1 empty + 1 button row
        int btnHeight = (int) (48 * getResources().getDisplayMetrics().density);
        float baseTextSize = 16f;
        float increasedTextSize = baseTextSize * 1.3f;
        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        
        // Row 0: 0,1,2,3,4,5
        for (int col = 0; col < 6; col++) {
            final int score = col;
            android.widget.Button btn = new android.widget.Button(getContext());
            btn.setText(String.valueOf(score));
            btn.setMinHeight(btnHeight);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                if (firstScore[0] == Integer.MIN_VALUE) {
                    firstScore[0] = score;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    showBoutScoreDialogFixed(origB, origA, nameB, nameA, tableLayout, boutResults, firstScore, origA, origB);
                } else {
                    processBoutResult(origA, origB, firstScore[0], score, tableLayout);
                    firstScore[0] = Integer.MIN_VALUE;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
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
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                if (firstScore[0] == Integer.MIN_VALUE) {
                    firstScore[0] = score;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    showBoutScoreDialogFixed(origB, origA, nameB, nameA, tableLayout, boutResults, firstScore, origA, origB);
                } else {
                    processBoutResult(origA, origB, firstScore[0], score, tableLayout);
                    firstScore[0] = Integer.MIN_VALUE;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
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
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
            btn.setPadding(4, 4, 4, 4);
            btn.setOnClickListener(v -> {
                if (firstScore[0] == Integer.MIN_VALUE) {
                    firstScore[0] = score;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    showBoutScoreDialogFixed(origB, origA, nameB, nameA, tableLayout, boutResults, firstScore, origA, origB);
                } else {
                    processBoutResult(origA, origB, firstScore[0], score, tableLayout);
                    firstScore[0] = Integer.MIN_VALUE;
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
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
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
        cancelBtn.setPadding(4, 4, 4, 4);
        cancelBtn.setOnClickListener(v -> {
            firstScore[0] = Integer.MIN_VALUE;
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
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, increasedTextSize);
        resetBtn.setBackgroundColor(Color.RED);
        resetBtn.setPadding(4, 4, 4, 4);
        resetBtn.setOnClickListener(v -> {
            processBoutResult(origA, origB, -1, -1, tableLayout);
            firstScore[0] = Integer.MIN_VALUE;
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

        // Create a vertical LinearLayout to hold the grid
        android.widget.LinearLayout verticalLayout = new android.widget.LinearLayout(getContext());
        verticalLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        verticalLayout.addView(grid);

        builder.setView(verticalLayout);
        // Remove setNegativeButton, handled by custom Cancel button

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

    private void processBoutResult(int participantA, int participantB, int scoreA, int scoreB, TableLayout tableLayout) {
        int[][] boutResults = scoresViewModel.getBoutResults().getValue();
        // If both scores are negative or equal, treat as empty (reset)
        if (scoreA < 0 || scoreB < 0 || scoreA == scoreB) {
            boutResults[participantA][participantB] = -1;
            boutResults[participantB][participantA] = -1;
        } else {
            boutResults[participantA][participantB] = scoreA;
            boutResults[participantB][participantA] = scoreB;
        }
        scoresViewModel.setBoutResults(boutResults);
        // Force matrix/UI refresh to recalculate percent column
        View root = getView();
        if (root != null) {
            createMatrix(root);
        }
        // Automatically save backup after every bout result update
        saveBackupToDocuments();
    }

    // Save backup to Fencing_backup.csv in Documents
    private void saveBackupToDocuments() {
        try {
            android.content.Context ctx = getContext();
            if (ctx == null) return;
            String csv = generateCSVCompat();
            java.io.File filesDir = ctx.getFilesDir();
            java.io.File outFile = new java.io.File(filesDir, "Fencing_backup.csv");
            java.io.FileOutputStream out = new java.io.FileOutputStream(outFile);
            out.write(csv.getBytes());
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TextView createHeaderCell(String text) {
        return createHeaderCell(text, -1, -1);
    }

    // Overload with cellHeight
    private TextView createHeaderCell(String text, int cellHeight) {
        return createHeaderCell(text, cellHeight, -1);
    }

    // Overload with cellHeight and nrPart for scaling
    private TextView createHeaderCell(String text, int cellHeight, int nrPart) {
        TextView cell = new TextView(getContext());
        cell.setText(text);
        cell.setTextColor(Color.BLACK);
        // Scale text size for many participants (>12)
        float textSize = 16f;
        int padding = 2;
        if (nrPart > 12) {
            textSize = 16f * 12f / nrPart;
            padding = Math.max(1, (int)(2 * 12f / nrPart));
        }
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        cell.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        cell.setBackgroundResource(R.drawable.cell_border);
        cell.setPadding(padding, 0, padding, 0);
        cell.setGravity(android.view.Gravity.CENTER);
        if (cellHeight > 0) cell.setHeight(cellHeight);
        return cell;
    }

    private TextView createDataCell(String text, boolean bold) {
        return createDataCell(text, bold, -1, -1);
    }

    // Overload with cellHeight
    private TextView createDataCell(String text, boolean bold, int cellHeight) {
        return createDataCell(text, bold, cellHeight, -1);
    }

    // Overload with cellHeight and nrPart for scaling
    private TextView createDataCell(String text, boolean bold, int cellHeight, int nrPart) {
        TextView cell = new TextView(getContext());
        cell.setText(text);
        cell.setTextColor(bold ? Color.BLACK : Color.BLUE);
        // Scale text size for many participants (>12)
        float textSize = 16f;
        int padding = 2;
        if (nrPart > 12) {
            textSize = 16f * 12f / nrPart;
            padding = Math.max(1, (int)(2 * 12f / nrPart));
        }
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        if (bold) {
            cell.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        } else {
            cell.setTypeface(android.graphics.Typeface.DEFAULT);
        }
        cell.setBackgroundResource(R.drawable.cell_border);
        cell.setPadding(padding, 0, padding, 0);
        cell.setGravity(android.view.Gravity.CENTER);
        if (cellHeight > 0) cell.setHeight(cellHeight);
        return cell;
    }


    // Save CSV to selected URI
    private void saveCsvToUri(android.net.Uri uri) {
        try {
            String csv = generateCSVCompat();
            android.content.Context ctx = getContext();
            if (ctx != null) {
                java.io.OutputStream out = ctx.getContentResolver().openOutputStream(uri);
                if (out != null) {
                    out.write(csv.getBytes());
                    out.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // Generate CSV string for export
    private String generateCSVCompat() {
        int nrPart = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
        String[] participantNames = scoresViewModel.getParticipantNames().getValue();
        int[][] boutResults = scoresViewModel.getBoutResults().getValue();
        if (participantNames == null || boutResults == null) return "";
        StringBuilder sb = new StringBuilder();
        // Header row
        sb.append("Nr,Name");
        for (int i = 0; i < nrPart; i++) {
            sb.append(",").append(i + 1);
        }
        sb.append(",V,→,←,I,%,P\n");
        // Data rows
        for (int i = 0; i < nrPart; i++) {
            sb.append(i + 1).append(",");
            sb.append(participantNames[i] != null ? participantNames[i] : "");
            // Bout results
            int victories = 0;
            int given = 0;
            int received = 0;
            int boutsWon = 0;
            int boutsLost = 0;
            for (int j = 0; j < nrPart; j++) {
                sb.append(",");
                if (i == j) {
                    sb.append("");
                } else if (boutResults[i][j] >= 0) {
                    sb.append(boutResults[i][j]);
                } else {
                    sb.append("");
                }
                // For calculations
                if (i != j && boutResults[i][j] >= 0 && boutResults[j][i] >= 0 &&
                    participantNames[i] != null && !participantNames[i].isEmpty() &&
                    participantNames[j] != null && !participantNames[j].isEmpty()) {
                    int score = boutResults[i][j];
                    int oppScore = boutResults[j][i];
                    if (score > oppScore) {
                        victories++;
                        boutsWon++;
                    } else if (score < oppScore) {
                        boutsLost++;
                    }
                    given += score;
                    received += oppScore;
                }
            }
            // V (Victories)
            sb.append(",").append((participantNames[i] != null && !participantNames[i].isEmpty()) ? victories : "");
            // → (Given)
            sb.append(",").append((participantNames[i] != null && !participantNames[i].isEmpty()) ? given : "");
            // ← (Received)
            sb.append(",").append((participantNames[i] != null && !participantNames[i].isEmpty()) ? received : "");
            // I (Index)
            sb.append(",").append((participantNames[i] != null && !participantNames[i].isEmpty()) ? (given - received) : "");
            // % (Percentage)
            String percent = "";
            if (participantNames[i] != null && !participantNames[i].isEmpty() && (boutsWon + boutsLost) > 0) {
                percent = String.valueOf((int) Math.round((double) boutsWon / (boutsWon + boutsLost) * 100));
            }
            sb.append(",").append(percent);
            // P (Position) - leave empty (ranking logic can be added if needed)
            sb.append(",");
            sb.append("\n");
        }
        return sb.toString();
    }

    // Load CSV from selected URI (stub: implement actual import logic)
    private void loadCsvFromUri(android.net.Uri uri) {
        try {
            android.content.Context ctx = getContext();
            if (ctx != null) {
                java.io.InputStream in = ctx.getContentResolver().openInputStream(uri);
                if (in != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    in.close();
                    importCsvData(sb.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Restore from default backup (stub)
    private void restoreFromDefaultBackupCompat() {
        // Restore from Fencing_backup.csv in app private files folder
        try {
            android.content.Context ctx = getContext();
            if (ctx != null) {
                java.io.File backupFile = new java.io.File(ctx.getFilesDir(), "Fencing_backup.csv");
                if (backupFile.exists()) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    importCsvData(sb.toString());
                } else {
                    android.widget.Toast.makeText(ctx, "No backup file found in app private folder", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Parse CSV and update ViewModel
    private void importCsvData(String csv) {
        if (csv == null || csv.trim().isEmpty()) return;
        String[] lines = csv.split("\n");
        if (lines.length < 2) return; // header + at least one row
        // Find header columns
        String[] header = lines[0].split(",");
        int nrPart = 0;
        for (int i = 2; i < header.length; i++) {
            if (header[i].matches("\\d+")) nrPart++;
            else break;
        }
        if (nrPart < ScoresViewModel.MIN_PARTICIPANTS || nrPart > ScoresViewModel.MAX_PARTICIPANTS) return;
        String[] participantNames = new String[nrPart];
        int[][] boutResults = new int[nrPart][nrPart];
        for (int i = 0; i < nrPart; i++) {
            for (int j = 0; j < nrPart; j++) boutResults[i][j] = -1;
        }
        int rowIdx = 1;
        for (int i = 0; i < nrPart && rowIdx < lines.length; i++, rowIdx++) {
            String[] cols = lines[rowIdx].split(",");
            if (cols.length < 2 + nrPart) continue;
            participantNames[i] = cols[1].trim();
            for (int j = 0; j < nrPart; j++) {
                String val = cols[2 + j].trim();
                if (val.isEmpty()) boutResults[i][j] = -1;
                else {
                    try { boutResults[i][j] = Integer.parseInt(val); }
                    catch (Exception e) { boutResults[i][j] = -1; }
                }
            }
        }
        // Update ViewModel atomically
        suspendObservers = true;
        scoresViewModel.setNrPart(nrPart);
        scoresViewModel.setParticipantNames(participantNames);
        scoresViewModel.setBoutResults(boutResults);
        suspendObservers = false;
        // Refresh matrix UI
        View root = getView();
        if (root != null) {
            createMatrix(root);
            updateHelpTextInLastPCell((TableLayout) root.findViewById(R.id.tableLayout),
                scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS,
                findLastEmptyP());
        }
    }

    // ========== QR Code Methods for Round Data ==========
    
    // Compress data using GZIP + Base64 for QR code
    private String compressRoundData(String data) {
        try {
            java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
            java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(byteStream);
            gzip.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            gzip.close();
            return android.util.Base64.encodeToString(byteStream.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.e("RoundFragment", "Error compressing data: " + e.getMessage());
            return null;
        }
    }
    
    // Decompress data from GZIP + Base64
    private String decompressRoundData(String compressed) {
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
            android.util.Log.e("RoundFragment", "Error decompressing data: " + e.getMessage());
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
            android.util.Log.e("RoundFragment", "Error generating QR code: " + e.getMessage());
            return null;
        }
    }
    
    // Generate Round data CSV string for QR code
    private String generateRoundCsvData() {
        int nrPart = scoresViewModel.getNrPart().getValue() != null ? scoresViewModel.getNrPart().getValue() : ScoresViewModel.DEFAULT_PARTICIPANTS;
        String[] participantNames = scoresViewModel.getParticipantNames().getValue();
        int[][] boutResults = scoresViewModel.getBoutResults().getValue();
        
        StringBuilder csv = new StringBuilder();
        csv.append("ROUND_DATA\n"); // Header to identify data type
        csv.append(nrPart).append("\n");
        
        // Add participant names
        for (int i = 0; i < nrPart; i++) {
            String name = (participantNames != null && i < participantNames.length) ? participantNames[i] : "";
            csv.append(name != null ? name : "").append(i < nrPart - 1 ? "," : "\n");
        }
        
        // Add bout results matrix
        for (int i = 0; i < nrPart; i++) {
            for (int j = 0; j < nrPart; j++) {
                int val = (boutResults != null && i < boutResults.length && j < boutResults[i].length) ? boutResults[i][j] : -1;
                csv.append(val).append(j < nrPart - 1 ? "," : "\n");
            }
        }
        
        return csv.toString();
    }
    
    // Generate and show QR code for Round data
    private void generateAndShowRoundQrCode() {
        String csvData = generateRoundCsvData();
        String compressed = compressRoundData(csvData);
        
        if (compressed == null) {
            android.widget.Toast.makeText(getContext(), "Error compressing data", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get screen dimensions for QR code size
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        int qrSize = Math.min(screenWidth, screenHeight) - 100;
        
        android.graphics.Bitmap qrBitmap = generateQrCode(compressed, qrSize);
        if (qrBitmap == null) {
            android.widget.Toast.makeText(getContext(), "Error generating QR code", android.widget.Toast.LENGTH_SHORT).show();
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
    private void startRoundQrScanner() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Import from QR Code")
            .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                if (which == 0) {
                    launchRoundCameraScanner();
                } else {
                    imagePickerLauncher.launch("image/*");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // Launch camera scanner
    private void launchRoundCameraScanner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), 
                new String[]{Manifest.permission.CAMERA}, 100);
            return;
        }
        
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan Round Data QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
        qrScannerLauncher.launch(options);
    }
    
    // Decode QR code from gallery image
    private void decodeQrFromImage(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
            
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            
            MultiFormatReader reader = new MultiFormatReader();
            Result result = reader.decode(binaryBitmap);
            
            if (result != null && result.getText() != null) {
                handleRoundQrScanResult(result.getText());
            } else {
                android.widget.Toast.makeText(getContext(), "No QR code found in image", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (com.google.zxing.NotFoundException e) {
            android.widget.Toast.makeText(getContext(), "No QR code found in image", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("RoundFragment", "Error decoding QR from image: " + e.getMessage());
            android.widget.Toast.makeText(getContext(), "Error reading image", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    // Launch QR scanner for Round data (kept for compatibility)
    private void launchRoundQrScanner() {
        launchRoundCameraScanner();
    }
    
    // Handle scanned QR code result for Round data
    private void handleRoundQrScanResult(String scannedData) {
        String decompressed = decompressRoundData(scannedData);
        if (decompressed == null) {
            android.widget.Toast.makeText(getContext(), "Invalid QR code data", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            String[] lines = decompressed.split("\n");
            if (lines.length < 3 || !lines[0].equals("ROUND_DATA")) {
                android.widget.Toast.makeText(getContext(), "Invalid Round data format", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            int nrPart = Integer.parseInt(lines[1].trim());
            String[] participantNames = new String[nrPart];
            int[][] boutResults = new int[nrPart][nrPart];
            
            // Parse participant names
            String[] names = lines[2].split(",", -1);
            for (int i = 0; i < nrPart && i < names.length; i++) {
                participantNames[i] = names[i].trim();
            }
            
            // Parse bout results matrix
            for (int i = 0; i < nrPart && (i + 3) < lines.length; i++) {
                String[] values = lines[i + 3].split(",", -1);
                for (int j = 0; j < nrPart && j < values.length; j++) {
                    try {
                        boutResults[i][j] = Integer.parseInt(values[j].trim());
                    } catch (NumberFormatException e) {
                        boutResults[i][j] = -1;
                    }
                }
            }
            
            // Update ViewModel atomically
            suspendObservers = true;
            scoresViewModel.setNrPart(nrPart);
            scoresViewModel.setParticipantNames(participantNames);
            scoresViewModel.setBoutResults(boutResults);
            suspendObservers = false;
            
            // Refresh matrix UI
            View root = getView();
            if (root != null) {
                createMatrix(root);
                updateHelpTextInLastPCell((TableLayout) root.findViewById(R.id.tableLayout),
                    nrPart, findLastEmptyP());
            }
            
            android.widget.Toast.makeText(getContext(), "Round data imported from QR", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("RoundFragment", "Error parsing QR data: " + e.getMessage());
            android.widget.Toast.makeText(getContext(), "Error parsing QR data", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}