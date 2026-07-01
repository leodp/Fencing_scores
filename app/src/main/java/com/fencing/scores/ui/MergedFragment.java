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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.text.InputType;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.fencing.scores.ScoresViewModel;
import com.fencing.scores.R;
import androidx.viewpager2.widget.ViewPager2;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import android.util.Base64;

public class MergedFragment extends Fragment {

                        private ScoresViewModel scoresViewModel;
                        
                        // QR Scanner launcher
                        private final ActivityResultLauncher<ScanOptions> qrScannerLauncher = 
                            registerForActivityResult(new ScanContract(), result -> {
                                if (result.getContents() != null) {
                                    handleQrScanResult(result.getContents());
                                }
                            });
                        
                        // Image picker launcher for QR from gallery
                        private final ActivityResultLauncher<String> imagePickerLauncher =
                            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                                if (uri != null) {
                                    decodeQrFromImage(uri);
                                }
                            });
                        
                        // File picker launcher for saving CSV
                        private androidx.activity.result.ActivityResultLauncher<android.content.Intent> saveFileLauncher;
                        
                        @Override
                        public void onCreate(@Nullable Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                            // Register file picker before view is created
                            saveFileLauncher = registerForActivityResult(
                                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                                result -> {
                                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                                        android.net.Uri uri = result.getData().getData();
                                        if (uri != null) {
                                            saveRankingCsvToUri(uri);
                                        }
                                    }
                                }
                            );
                        }

                        @Override
                        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
                            super.onViewCreated(view, savedInstanceState);
                            scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
                            // Observe colorCycleIndex and update table colors when it changes (only when resumed)
                            scoresViewModel.getColorCycleIndex().observe(getViewLifecycleOwner(), idx -> {
                                if (isResumed()) renderRows();
                            });
                        }

                        @Override
                        public void onResume() {
                            super.onResume();
                            // If rows is empty, try to restore from backup first
                            if (rows == null || rows.isEmpty()) {
                                tryAutoRestoreFromBackup();
                            }
                            // Always redraw table and colors when fragment resumes
                            renderRows();
                        }
                        
                        // Try to restore from Merged_backup.csv if it exists and has valid data
                        // If not, fall back to loading from Round's Fencing_backup.csv
                        // Only restores after a crash, not on normal app restart
                        private void tryAutoRestoreFromBackup() {
                            // Only restore if app crashed - normal restart should start fresh
                            if (!com.fencing.scores.MainActivity.crashDetected) {
                                return;
                            }
                            try {
                                java.io.File filesDir = requireContext().getFilesDir();
                                java.io.File backupFile = new java.io.File(filesDir, "Merged_backup.csv");
                                
                                // First try Merged_backup.csv
                                if (backupFile.exists()) {
                                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
                                    String header = reader.readLine();
                                    if (header != null) {
                                        java.util.List<Row> loadedRows = new java.util.ArrayList<>();
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            String[] tokens = line.split(",", -1);
                                            if (tokens.length >= 2) {
                                                String name = tokens[1].trim();
                                                if (name != null && !name.isEmpty()) {
                                                    int nr = tokens.length > 0 ? parseIntSafe(tokens[0]) : loadedRows.size() + 1;
                                                    boolean hasMatchesColumn = header.contains(",#,") || header.contains(",Matches,");
                                                    int base = hasMatchesColumn ? 3 : 2;
                                                    int matches = hasMatchesColumn && tokens.length > 2 ? parseIntSafe(tokens[2]) : 0;
                                                    int victories = tokens.length > base ? parseIntSafe(tokens[base]) : 0;
                                                    int given = tokens.length > (base + 1) ? parseIntSafe(tokens[base + 1]) : 0;
                                                    int received = tokens.length > (base + 2) ? parseIntSafe(tokens[base + 2]) : 0;
                                                    int index = tokens.length > (base + 3) ? parseIntSafe(tokens[base + 3]) : 0;
                                                    int percent = tokens.length > (base + 4) ? parseIntSafe(tokens[base + 4]) : 0;
                                                    int p = tokens.length > (base + 5) ? parseIntSafe(tokens[base + 5]) : 0;
                                                    Integer finalPos = tokens.length > (base + 6) && !tokens[base + 6].trim().isEmpty() ? parseIntSafe(tokens[base + 6]) : null;
                                                    loadedRows.add(new Row(nr, name, matches, victories, given, received, index, percent, p, "A", finalPos));
                                                }
                                            }
                                        }
                                        reader.close();
                                        
                                        if (!loadedRows.isEmpty()) {
                                            rows.clear();
                                            rows.addAll(loadedRows);
                                            return; // Success, no need to fall back
                                        }
                                    }
                                    reader.close();
                                }
                                
                                // Fall back to Fencing_backup.csv (Round data) if Merged backup doesn't exist or is empty
                                java.io.File roundBackupFile = new java.io.File(filesDir, "Fencing_backup.csv");
                                if (roundBackupFile.exists()) {
                                    loadRoundData();
                                }
                            } catch (Exception e) {
                                android.util.Log.e("MergedFragment", "Auto-restore failed: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onStart() {
                            super.onStart();
                            // Keep onStart light; full restore/render work is handled in onResume.
                        }
                    // Helper to move to the previous page (Merged -> Round, KO -> Merged, etc.)
                    private void navigateToPreviousPage() {
        if (getActivity() instanceof com.fencing.scores.MainActivity) {
            com.fencing.scores.MainActivity mainActivity = (com.fencing.scores.MainActivity) getActivity();
            int rounds = mainActivity.getRoundPagesCount();
            mainActivity.navigateToRoundPage(rounds);
        } else if (getActivity() instanceof com.fencing.scores.MergedActivity) {
            android.widget.Toast.makeText(getContext(), "Navigation to Round page is only available in MainActivity.", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.util.Log.e("MergedFragment", "Activity is not MainActivity or MergedActivity, cannot navigate to previous page. Actual activity: " + (getActivity() != null ? getActivity().getClass().getName() : "null"));
        }
                    }
                // Helper to move to the next page (KO -> Round, Merged -> KO, etc.)
                private void navigateToNextPage() {
                    if (getActivity() instanceof com.fencing.scores.MainActivity) {
                        com.fencing.scores.MainActivity mainActivity = (com.fencing.scores.MainActivity) getActivity();
                        mainActivity.navigateToKOPage();
                    } else {
                        android.util.Log.e("MergedFragment", "navigateToNextPage: Activity is not MainActivity, cannot navigate to KO page. Actual activity: " + (getActivity() != null ? getActivity().getClass().getName() : "null"));
                    }
                }
            // Color pairs for result columns (cycled, copied from RoundFragment)
            private static final int[][] RESULT_COLOR_PAIRS = {
                {0xFFFFD700, 0xFFFF7F50}, // Default
                {0xFF87DEFA, 0xFF9084DE},
                {0xFFFFFFE0, 0xFFF0E68C},
                {0xFF98FB98, 0xFF9ACD32},
                {0xFFA9A9A9, 0xFFDCDCDC},
                {0xFF00FFFF, 0xFF39E75F}
            };
            // No local colorIdx; use ViewModel's colorCycleIndex for dynamic color sync
            private android.graphics.drawable.GradientDrawable makeBorderedCell(int fillColor) {
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setColor(fillColor);
                d.setStroke(2, android.graphics.Color.BLACK);
                d.setCornerRadius(0f);
                return d;
            }
        // Backup the merged matrix to Merged_backup.csv in Documents
        private void backupMergedMatrix() {
            try {
                if (com.fencing.scores.MainActivity.cleanExitInProgress) return;
                java.io.File filesDir = requireContext().getFilesDir();
                java.io.File backupFile = new java.io.File(filesDir, "Merged_backup.csv");
                java.io.FileWriter writer = new java.io.FileWriter(backupFile, false);
                // Write header
                writer.write("Nr,Name,#,V,→,←,I,%,P,FinalPos\n");
                for (Row r : rows) {
                    writer.write(
                        r.nr + "," +
                        (r.name != null ? r.name : "") + "," +
                        r.matches + "," +   // #
                        r.victories + "," + // V
                        r.given + "," +     // →
                        r.received + "," +  // ←
                        r.index + "," +     // I
                        r.percent + "," +   // %
                        r.p + "," +         // P
                        (r.finalPos != null ? r.finalPos : "") + "\n"   // FinalPos
                    );
                }
                writer.close();
                android.util.Log.i("MergedFragment", "Backup saved to: " + backupFile.getAbsolutePath() + " (" + rows.size() + " rows)");
            } catch (Exception e) {
                android.util.Log.e("MergedFragment", "Backup failed: " + e.getMessage());
            }
        }
        
        // Open file picker to save ranking CSV
        private void openSaveFilePicker() {
            android.content.Context ctx = getContext();
            if (ctx == null) return;
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, generateTimestampedFilename("MergedRanking"));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                        android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"));
                } catch (Exception e) { /* ignore */ }
            }
            saveFileLauncher.launch(intent);
        }
        
        // Save ranking CSV to the selected URI
        private void saveRankingCsvToUri(android.net.Uri uri) {
            try {
                java.io.OutputStream os = requireContext().getContentResolver().openOutputStream(uri);
                if (os == null) {
                    android.widget.Toast.makeText(getContext(), "Failed to open file", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(os);
                // Write header - same format as backup
                writer.write("Nr,Name,#,V,→,←,I,%,P,FinalPos\n");
                for (Row r : rows) {
                    writer.write(
                        r.nr + "," +
                        (r.name != null ? r.name : "") + "," +
                        r.matches + "," +
                        r.victories + "," +
                        r.given + "," +
                        r.received + "," +
                        r.index + "," +
                        r.percent + "," +
                        r.p + "," +
                        (r.finalPos != null ? r.finalPos : "") + "\n"
                    );
                }
                writer.close();
                os.close();
                android.widget.Toast.makeText(getContext(), "Ranking saved (" + rows.size() + " rows)", android.widget.Toast.LENGTH_SHORT).show();
                android.util.Log.i("MergedFragment", "Ranking CSV saved to: " + uri.toString());
            } catch (Exception e) {
                android.widget.Toast.makeText(getContext(), "Save failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                android.util.Log.e("MergedFragment", "Save CSV failed: " + e.getMessage());
            }
        }
    private static final String[] HEADERS = {"Nr", "Name", "#", "V", "→", "←", "I", "%", "P", "Grp", "FinalPos"};
    private TableLayout tableLayout;
    private TableLayout tableLayoutRight;  // Right side table for split view
    private Button replaceCsvBtn, addCsvBtn, reloadBtn;
    private java.util.List<Row> rows = new java.util.ArrayList<>();
    private boolean useCsvOnly = false;
    private boolean didRestore = false;
    private boolean nameSortAscending = true;
    private boolean pSortAscending = true;
    private boolean grpSortAscending = true;
    private boolean finalPosSortAscending = true;
    private boolean mixedGroupsDetected = false;
    private boolean reloadedDistinctGroupsMerged = false;

    static class Row {
        int nr;
        String name;
        int matches;
        int victories, given, received, index, percent, p;
        String grp;
        Integer finalPos;
        Row(int nr, String name, int matches, int victories, int given, int received, int index, int percent, int p, String grp, Integer finalPos) {
            this.nr = nr; this.name = name; this.victories = victories; this.given = given; this.received = received;
            this.matches = matches;
            this.index = index; this.percent = percent; this.p = p; this.grp = grp != null ? grp : "A"; this.finalPos = finalPos;
        }
    }

    static class Agg {
        int given;
        int received;
        int victories;
        int boutsWon;
        int boutsLost;
        int matches;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_merged, container, false);
        tableLayout = root.findViewById(R.id.tableLayout);
        tableLayoutRight = root.findViewById(R.id.tableLayoutRight);
        LinearLayout btnLayout = root.findViewById(R.id.btnLayout);
        btnLayout.removeAllViews();
        
        // Dark blue color matching KO page
        int darkBlue = 0xFF1565C0;
        int btnMargin = 4; // Close but not touching
        float btnMinWidthDp = 96 * 1.2f; // 20% wider than base
        int btnMinWidth = (int)(btnMinWidthDp * getResources().getDisplayMetrics().density);
        
        reloadBtn = new Button(getContext());
        reloadBtn.setText("RELOAD round");
        setRoundedBackground(reloadBtn, 0xFF388E3C); // Green
        reloadBtn.setTextColor(0xFFFFFFFF);
        reloadBtn.setMinWidth(btnMinWidth);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, btnMargin, 0);
        reloadBtn.setLayoutParams(lp1);
        btnLayout.addView(reloadBtn);
        
        replaceCsvBtn = new Button(getContext());
        replaceCsvBtn.setText("REPLACE");
        setRoundedBackground(replaceCsvBtn, darkBlue);
        replaceCsvBtn.setTextColor(0xFFFFFFFF);
        replaceCsvBtn.setMinWidth(btnMinWidth);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 0, btnMargin, 0);
        replaceCsvBtn.setLayoutParams(lp2);
        btnLayout.addView(replaceCsvBtn);
        
        addCsvBtn = new Button(getContext());
        addCsvBtn.setText("ADD");
        setRoundedBackground(addCsvBtn, darkBlue);
        addCsvBtn.setTextColor(0xFFFFFFFF);
        addCsvBtn.setMinWidth(btnMinWidth);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp3.setMargins(0, 0, btnMargin, 0);
        addCsvBtn.setLayoutParams(lp3);
        btnLayout.addView(addCsvBtn);
        
        Button restoreBtn = new Button(getContext());
        restoreBtn.setText("RESTORE crash");
        setRoundedBackground(restoreBtn, darkBlue);
        restoreBtn.setTextColor(0xFFFFFFFF);
        restoreBtn.setMinWidth(btnMinWidth);
        restoreBtn.setOnClickListener(v -> {
            // android.util.Log.v("MergedFragment", "RESTORE button pressed - loading from Merged_backup.csv");
            restoreData();
        });
        LinearLayout.LayoutParams lpRestore = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRestore.setMargins(0, 0, btnMargin, 0);
        restoreBtn.setLayoutParams(lpRestore);
        btnLayout.addView(restoreBtn);
        
        // QR OUT button - generates QR code from Merged data
        Button qrOutBtn = new Button(getContext());
        qrOutBtn.setText("QR OUT");
        setRoundedBackground(qrOutBtn, darkBlue);
        qrOutBtn.setTextColor(0xFFFFFFFF);
        qrOutBtn.setMinWidth(btnMinWidth);
        qrOutBtn.setOnClickListener(v -> showQrCodeFullscreen());
        LinearLayout.LayoutParams lpQrOut = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpQrOut.setMargins(0, 0, btnMargin, 0);
        qrOutBtn.setLayoutParams(lpQrOut);
        btnLayout.addView(qrOutBtn);
        
        // QR ADD button - scans QR code to add data
        Button qrInBtn = new Button(getContext());
        qrInBtn.setText("QR ADD");
        setRoundedBackground(qrInBtn, darkBlue);
        qrInBtn.setTextColor(0xFFFFFFFF);
        qrInBtn.setMinWidth(btnMinWidth);
        qrInBtn.setOnClickListener(v -> startQrScanner());
        LinearLayout.LayoutParams lpQrIn = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpQrIn.setMargins(0, 0, btnMargin, 0);
        qrInBtn.setLayoutParams(lpQrIn);
        btnLayout.addView(qrInBtn);
        
        // SAVE button - exports ranking to CSV file
        Button saveBtn = new Button(getContext());
        saveBtn.setText("SAVE");
        setRoundedBackground(saveBtn, 0xFFD32F2F); // Red color
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setOnClickListener(v -> openSaveFilePicker());
        btnLayout.addView(saveBtn);

        // RELOAD always fetches from RoundFragment
        reloadBtn.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Reload from Rounds")
                .setMessage("Reload Merged data from all Round pages? Current Merged edits will be overwritten.")
                .setPositiveButton("Reload", (dialog, which) -> {
                    // android.util.Log.v("MergedFragment", "RELOAD confirmed - loading from RoundFragment");
                    useCsvOnly = false;
                    loadRoundData();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        replaceCsvBtn.setOnClickListener(v -> selectCsvFile(1001));
        addCsvBtn.setOnClickListener(v -> selectCsvFile(1002));
        
        return root;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove crash file on normal exit
        try {
            java.io.File filesDir = requireContext().getFilesDir();
            java.io.File crashFile = new java.io.File(filesDir, "crash_detected.flag");
            if (crashFile.exists()) crashFile.delete();
        } catch (Exception e) { /* ignore */ }
    }

    // Placeholder for RESTORE button action
    private void restoreData() {
        // android.util.Log.v("MergedFragment", "restoreData() called");
        StringBuilder sb = new StringBuilder();
        sb.append("MergedFragment RESTORE: Loading backup CSV\n");
        java.io.File filesDir = requireContext().getFilesDir();
        java.io.File backupFile = new java.io.File(filesDir, "Merged_backup.csv");
        sb.append("Backup file path: ").append(backupFile.getAbsolutePath()).append("\n");
        try {
        // Already declared above
            if (!backupFile.exists()) {
                android.widget.Toast.makeText(getContext(), "Restore failed: Merged_backup.csv not found", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(backupFile));
            String headerLine = reader.readLine();
            sb.append("Header line: ").append(headerLine).append("\n");
            if (headerLine == null) return;
            String[] header = headerLine.split(",");
            sb.append("Header columns: ").append(header.length).append("\n");
            boolean isMergedBackup = false;
            int nrPart = 0;
            // Detect format: if header contains bout columns (numbers), it's Round_backup.csv
            for (int i = 2; i < header.length; i++) {
                if (header[i].matches("\\d+")) nrPart++;
                else break;
            }
            if (nrPart > 0) {
                isMergedBackup = false;
            } else {
                isMergedBackup = true;
            }
            int idxMatches = -1, idxV = -1, idxGiven = -1, idxReceived = -1, idxIndex = -1, idxPercent = -1, idxP = -1, idxGrp = -1, idxFinalPos = -1;
            for (int i = 0; i < header.length; i++) {
                sb.append("Header[").append(i).append("] = '").append(header[i]).append("'\n");
                String h = header[i].trim();
                if (h.equals("#") || h.equalsIgnoreCase("Matches")) idxMatches = i;
                else if (h.equals("V")) idxV = i;
                else if (h.equals("→") || h.equals("->")) idxGiven = i;
                else if (h.equals("←") || h.equals("<-")) idxReceived = i;
                else if (h.equals("I")) idxIndex = i;
                else if (h.equals("%")) idxPercent = i;
                else if (h.equals("P")) idxP = i;
                else if (h.equals("Grp")) idxGrp = i;
                else if (h.equals("FinalPos")) idxFinalPos = i;
            }
            String line;
            // android.util.Log.v("MergedFragment", sb.toString());
            java.util.List<Row> loadedRows = new java.util.ArrayList<>();
            int[][] restoredBoutResults = new int[nrPart][nrPart];
            for (int i = 0; i < nrPart; i++) for (int j = 0; j < nrPart; j++) restoredBoutResults[i][j] = -1;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                            // android.util.Log.v("MergedFragment", "Parsing CSV line " + lineNum + ": " + line);
                lineNum++;
                String[] tokens = line.split(",");
                if (tokens.length < 2 + nrPart) {
                    android.util.Log.e("MergedFragment", "CSV line " + lineNum + " skipped: not enough columns (" + tokens.length + ")");
                    continue;
                }
                String name = tokens[1];
                int participantIdx = lineNum - 2;
                if (!isMergedBackup) {
                    // Parse bout results for this participant, ignore diagonal (X)
                    for (int j = 0; j < nrPart; j++) {
                        int boutIdx = 2 + j;
                        if (boutIdx < tokens.length) {
                            String boutVal = tokens[boutIdx].trim();
                            if (boutVal.equalsIgnoreCase("X")) {
                                restoredBoutResults[participantIdx][j] = -1;
                            } else {
                                restoredBoutResults[participantIdx][j] = boutVal.isEmpty() ? -1 : parseIntSafe(boutVal);
                            }
                        }
                    }
                }
                // Relax validation: allow empty participant names
                try {
                    int nr = parseIntSafe(tokens[0]);
                    int matches = (idxMatches >= 0 && idxMatches < tokens.length) ? parseIntSafe(tokens[idxMatches]) : 0;
                    int victories = (idxV >= 0 && idxV < tokens.length) ? parseIntSafe(tokens[idxV]) : 0;
                    int given = (idxGiven >= 0 && idxGiven < tokens.length) ? parseIntSafe(tokens[idxGiven]) : 0;
                    int received = (idxReceived >= 0 && idxReceived < tokens.length) ? parseIntSafe(tokens[idxReceived]) : 0;
                    int index = (idxIndex >= 0 && idxIndex < tokens.length) ? parseIntSafe(tokens[idxIndex]) : 0;
                    int percent = (idxPercent >= 0 && idxPercent < tokens.length) ? parseIntSafe(tokens[idxPercent]) : 0;
                    int p = (idxP >= 0 && idxP < tokens.length) ? parseIntSafe(tokens[idxP]) : 0;
                    String grp = (idxGrp >= 0 && idxGrp < tokens.length && !tokens[idxGrp].trim().isEmpty()) ? tokens[idxGrp].trim() : "A";
                    Integer finalPos = null;
                    if (idxFinalPos >= 0 && idxFinalPos < tokens.length) {
                        try {
                            int fp = Integer.parseInt(tokens[idxFinalPos].trim());
                            if (fp != 0) finalPos = fp;
                        } catch (Exception e) { finalPos = null; }
                    }
                    loadedRows.add(new Row(nr, name, matches, victories, given, received, index, percent, p, grp, finalPos));
                                        // android.util.Log.v("MergedFragment", "Row added: Nr=" + nr + ", Name='" + name + "', V=" + victories + ", →=" + given + ", ←=" + received + ", I=" + index + ", %=" + percent + ", P=" + p + ", Grp=" + grp + ", FinalPos=" + finalPos);
                    // android.util.Log.i("MergedFragment", "CSV line " + lineNum + " parsed: nr=" + nr + ", name=" + name + ", V=" + victories + ", ->=" + given + ", <-=" + received + ", I=" + index + ", %=" + percent + ", P=" + p);
                } catch (Exception parseEx) {
                    android.util.Log.e("MergedFragment", "CSV line " + lineNum + " parse error: " + parseEx.getMessage());
                }
            }
            reader.close();
            // Only sync bout results for Round backup format (not Merged)
            if (!isMergedBackup) {
                ScoresViewModel scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
                scoresViewModel.setBoutResults(restoredBoutResults);
                // Recalculate → and ← for each participant from bout results
                for (int i = 0; i < loadedRows.size(); i++) {
                    int given = 0, received = 0;
                    int matches = 0;
                    for (int j = 0; j < restoredBoutResults.length; j++) {
                        if (i != j && restoredBoutResults[i][j] >= 0 && restoredBoutResults[j][i] >= 0) {
                            given += restoredBoutResults[i][j];
                            received += restoredBoutResults[j][i];
                            matches++;
                        }
                    }
                    loadedRows.get(i).given = given;
                    loadedRows.get(i).received = received;
                    loadedRows.get(i).matches = matches;
                }
            }
            // For Merged backup, keep the values as loaded from CSV (do not recalculate)
            rows.clear();
            // android.util.Log.v("MergedFragment", "Clearing rows and loading backup...");
            rows.addAll(loadedRows);
            // android.util.Log.v("MergedFragment", "Loaded rows: " + rows.size());
            useCsvOnly = true;
            // Only recalculate FinalPos for Round backup; Merged backup already has FinalPos
            if (!isMergedBackup) {
                calculateFinalPositions();
                // android.util.Log.v("MergedFragment", "Calculating final positions after Round RESTORE...");
            } else {
                // android.util.Log.v("MergedFragment", "Skipping FinalPos recalculation for Merged backup (using saved values)");
            }
            renderRows();
            // android.util.Log.v("MergedFragment", "Rendering rows after RESTORE...");
            android.widget.Toast.makeText(getContext(), "Restored from: " + backupFile.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "Restore failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    // Helper to safely parse integers
    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private int inferMatches(int victories, int percent) {
        if (victories <= 0) return 0;
        if (percent <= 0) return victories;
        int estimated = (int) Math.round((victories * 100.0) / percent);
        return Math.max(victories, estimated);
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private java.util.Set<String> currentNameSet() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (Row r : rows) {
            String n = normalizeName(r.name);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    private boolean hasNameOverlap(java.util.List<Row> imported) {
        java.util.Set<String> existing = currentNameSet();
        for (Row r : imported) {
            String n = normalizeName(r.name);
            if (!n.isEmpty() && existing.contains(n)) return true;
        }
        return false;
    }

    private void renumberRows() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).nr = i + 1;
        }
    }

    private void refreshRowDerivedMetrics() {
        for (Row r : rows) {
            r.index = r.given - r.received;
            r.percent = r.matches > 0 ? (int) Math.round((r.victories * 100.0) / r.matches) : 0;
        }
    }

    private void recalculatePFromPerformance() {
        java.util.List<int[]> statsForRanking = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.name != null && !r.name.trim().isEmpty() && r.matches > 0) {
                statsForRanking.add(new int[]{i, r.percent, r.index, r.given});
            } else {
                r.p = 0;
            }
        }

        statsForRanking.sort((a, b) -> {
            int cmp = Integer.compare(b[1], a[1]);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b[2], a[2]);
            if (cmp != 0) return cmp;
            return Integer.compare(b[3], a[3]);
        });

        int pos = 1;
        for (int i = 0; i < statsForRanking.size(); i++) {
            if (i > 0) {
                int[] prev = statsForRanking.get(i - 1);
                int[] curr = statsForRanking.get(i);
                boolean same = curr[1] == prev[1] && curr[2] == prev[2] && curr[3] == prev[3];
                if (!same) pos = i + 1;
            }
            rows.get(statsForRanking.get(i)[0]).p = pos;
        }
    }

    private java.util.Map<String, Integer> captureGroupsFromCurrentPOrder() {
        java.util.Map<String, Integer> nameToGroup = new java.util.LinkedHashMap<>();
        int groupId = 1;
        int prevP = -1;

        for (Row r : rows) {
            String key = normalizeName(r.name);
            if (key.isEmpty()) continue;
            // A new disjoint group starts when P restarts from 1 after a completed group.
            if (prevP > 1 && r.p == 1) {
                groupId++;
            }
            if (!nameToGroup.containsKey(key)) {
                nameToGroup.put(key, groupId);
            }
            if (r.p > 0) prevP = r.p;
        }
        return nameToGroup;
    }

    // Convert group ID (1, 2, 3, ...) to letter label (A, B, C, ...)
    private String groupIdToLabel(int groupId) {
        if (groupId < 1) return "A";
        return String.valueOf((char) ('A' + groupId - 1));
    }

    // Assign group labels to all rows based on nameToGroup mapping
    private void assignGroupLabels(java.util.Map<String, Integer> nameToGroup) {
        for (Row r : rows) {
            String key = normalizeName(r.name);
            if (key.isEmpty()) {
                r.grp = "A";
            } else {
                Integer groupId = nameToGroup.get(key);
                r.grp = groupIdToLabel(groupId != null ? groupId : 1);
            }
        }
    }

    // Extract participant names for each group from current rows
    private java.util.Map<Integer, java.util.Set<String>> getGroupCompositions(java.util.Map<String, Integer> nameToGroup) {
        java.util.Map<Integer, java.util.Set<String>> groupCompositions = new java.util.LinkedHashMap<>();
        for (Row r : rows) {
            String key = normalizeName(r.name);
            if (key.isEmpty()) continue;
            Integer groupId = nameToGroup.get(key);
            if (groupId != null) {
                groupCompositions.computeIfAbsent(groupId, k -> new java.util.HashSet<>()).add(key);
            }
        }
        return groupCompositions;
    }

    private String normalizeGroupLabel(String grp) {
        String g = grp == null ? "" : grp.trim();
        return g.isEmpty() ? "A" : g.toUpperCase(java.util.Locale.ROOT);
    }

    private int groupLabelToId(String grp) {
        String g = normalizeGroupLabel(grp);
        char c = g.charAt(0);
        if (c >= 'A' && c <= 'Z') return c - 'A' + 1;
        try {
            int parsed = Integer.parseInt(g);
            return parsed > 0 ? parsed : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    // Prefer explicit Grp labels if present; fallback to P-order inference for old data.
    private java.util.Map<String, Integer> captureGroupsFromRows() {
        java.util.Map<String, Integer> nameToGroup = new java.util.LinkedHashMap<>();
        boolean anyGrp = false;
        for (Row r : rows) {
            String key = normalizeName(r.name);
            if (key.isEmpty()) continue;
            String grp = r.grp == null ? "" : r.grp.trim();
            if (!grp.isEmpty()) {
                anyGrp = true;
                nameToGroup.put(key, groupLabelToId(grp));
            }
        }
        if (!anyGrp) {
            return captureGroupsFromCurrentPOrder();
        }

        int maxId = 0;
        for (Integer g : nameToGroup.values()) {
            if (g != null && g > maxId) maxId = g;
        }
        for (Row r : rows) {
            String key = normalizeName(r.name);
            if (key.isEmpty()) continue;
            if (!nameToGroup.containsKey(key)) {
                nameToGroup.put(key, ++maxId);
            }
        }
        return nameToGroup;
    }

    // Extract participant names for each group from imported rows
    private java.util.Map<String, java.util.Set<String>> getImportedGroupCompositions(java.util.List<Row> importedRows) {
        java.util.Map<String, java.util.Set<String>> importedCompositions = new java.util.LinkedHashMap<>();
        for (Row r : importedRows) {
            String key = normalizeName(r.name);
            if (key.isEmpty()) continue;
            String grp = normalizeGroupLabel(r.grp);
            importedCompositions.computeIfAbsent(grp, k -> new java.util.HashSet<>()).add(key);
        }
        return importedCompositions;
    }

    // Collapse is required when an imported group overlaps with an existing group but has a different composition,
    // or when it mixes participants from more than one existing group.
    private boolean requiresGroupCollapse(
        java.util.Map<String, Integer> existingNameToGroup,
        java.util.Map<Integer, java.util.Set<String>> existingCompositions,
        java.util.Map<String, java.util.Set<String>> importedCompositions
    ) {
        for (java.util.Set<String> importedSet : importedCompositions.values()) {
            java.util.Set<Integer> overlapped = new java.util.HashSet<>();
            for (String participant : importedSet) {
                Integer gid = existingNameToGroup.get(participant);
                if (gid != null) overlapped.add(gid);
            }

            if (overlapped.isEmpty()) {
                // Entirely new disjoint group is valid.
                continue;
            }
            if (overlapped.size() > 1) {
                // Imported group mixes participants belonging to different existing groups.
                return true;
            }

            Integer gid = overlapped.iterator().next();
            java.util.Set<String> existingSet = existingCompositions.get(gid);
            if (existingSet == null || !existingSet.equals(importedSet)) {
                // Same participant space but composition changed.
                return true;
            }
        }
        return false;
    }

    private void recalculatePByGroups(java.util.Map<String, Integer> nameToGroup) {
        int maxGroup = 0;
        for (Integer g : nameToGroup.values()) {
            if (g != null && g > maxGroup) maxGroup = g;
        }

        java.util.Map<Integer, java.util.List<int[]>> statsByGroup = new java.util.LinkedHashMap<>();
        java.util.Random random = new java.util.Random(System.nanoTime());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String key = normalizeName(r.name);
            if (key.isEmpty()) {
                r.p = 0;
                continue;
            }

            Integer group = nameToGroup.get(key);
            if (group == null) {
                group = ++maxGroup;
                nameToGroup.put(key, group);
            }

            if (r.matches > 0) {
                java.util.List<int[]> stats = statsByGroup.get(group);
                if (stats == null) {
                    stats = new java.util.ArrayList<>();
                    statsByGroup.put(group, stats);
                }
                stats.add(new int[]{i, r.percent, r.index, r.given, r.received});
            } else {
                r.p = 0;
            }
        }

        for (java.util.List<int[]> statsForRanking : statsByGroup.values()) {
            statsForRanking.sort((a, b) -> {
                int cmp = Integer.compare(b[1], a[1]);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b[2], a[2]);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(b[3], a[3]);
                if (cmp != 0) return cmp;
                return Integer.compare(a[4], b[4]);
            });

            // Randomize ties inside each disjoint group, then assign unique sequential P.
            java.util.List<int[]> randomized = new java.util.ArrayList<>();
            for (int i = 0; i < statsForRanking.size(); ) {
                int j = i + 1;
                while (j < statsForRanking.size()) {
                    int[] left = statsForRanking.get(i);
                    int[] right = statsForRanking.get(j);
                    boolean same = right[1] == left[1]
                        && right[2] == left[2]
                        && right[3] == left[3]
                        && right[4] == left[4];
                    if (!same) break;
                    j++;
                }
                java.util.List<int[]> tieBlock = new java.util.ArrayList<>(statsForRanking.subList(i, j));
                if (tieBlock.size() > 1) {
                    java.util.Collections.shuffle(tieBlock, random);
                }
                randomized.addAll(tieBlock);
                i = j;
            }

            int pos = 1;
            for (int[] stat : randomized) {
                rows.get(stat[0]).p = pos++;
            }
        }
    }

    private void applyImportedRows(java.util.List<Row> importedRows, boolean replace, String sourceLabel) {
        if (replace) {
            rows.clear();
            rows.addAll(importedRows);
            mixedGroupsDetected = false;
            renumberRows();
            refreshRowDerivedMetrics();
            calculateFinalPositions();
            backupMergedMatrix();
            renderRows();
            android.widget.Toast.makeText(getContext(), "Replaced " + importedRows.size() + " participants from " + sourceLabel, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.Map<String, Integer> existingNameToGroup = captureGroupsFromRows();
        java.util.Map<Integer, java.util.Set<String>> existingCompositions = getGroupCompositions(existingNameToGroup);
        java.util.Map<String, java.util.Set<String>> importedCompositions = getImportedGroupCompositions(importedRows);

        boolean collapseGroups = requiresGroupCollapse(existingNameToGroup, existingCompositions, importedCompositions);

        java.util.Map<String, Row> byName = new java.util.LinkedHashMap<>();
        for (Row r : rows) {
            String key = normalizeName(r.name);
            if (!key.isEmpty() && !byName.containsKey(key)) byName.put(key, r);
        }

        if (collapseGroups) {
            // Merge by participant name, then flatten into one group with P=0.
            for (Row in : importedRows) {
                String key = normalizeName(in.name);
                if (key.isEmpty()) continue;
                Row cur = byName.get(key);
                if (cur == null) {
                    rows.add(in);
                    byName.put(key, in);
                } else {
                    cur.matches += in.matches;
                    cur.victories += in.victories;
                    cur.given += in.given;
                    cur.received += in.received;
                }
            }

            for (Row r : rows) {
                r.p = 0;
                r.grp = "A";
            }

            mixedGroupsDetected = false;
            renumberRows();
            refreshRowDerivedMetrics();
            calculateFinalPositions();
            backupMergedMatrix();
            renderRows();
            android.widget.Toast.makeText(getContext(), "Same participant in distinct groups: MERGED", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // Composition-compatible: map each imported group by participant set (name of group is irrelevant).
        java.util.Map<String, Integer> importedGrpToTargetId = new java.util.LinkedHashMap<>();
        int maxGroupId = 0;
        for (Integer g : existingNameToGroup.values()) {
            if (g != null && g > maxGroupId) maxGroupId = g;
        }

        for (java.util.Map.Entry<String, java.util.Set<String>> entry : importedCompositions.entrySet()) {
            String importedGrp = entry.getKey();
            java.util.Set<String> importedSet = entry.getValue();

            Integer matchedExisting = null;
            for (java.util.Map.Entry<Integer, java.util.Set<String>> existingEntry : existingCompositions.entrySet()) {
                if (existingEntry.getValue().equals(importedSet)) {
                    matchedExisting = existingEntry.getKey();
                    break;
                }
            }

            if (matchedExisting != null) {
                importedGrpToTargetId.put(importedGrp, matchedExisting);
            } else {
                importedGrpToTargetId.put(importedGrp, ++maxGroupId);
            }
        }

        // Merge imported rows and place each participant in its mapped group.
        for (Row in : importedRows) {
            String key = normalizeName(in.name);
            if (key.isEmpty()) continue;
            String importedGrp = normalizeGroupLabel(in.grp);
            Integer targetGroupId = importedGrpToTargetId.get(importedGrp);
            if (targetGroupId == null) targetGroupId = 1;

            Row cur = byName.get(key);
            if (cur == null) {
                rows.add(in);
                byName.put(key, in);
            } else {
                cur.matches += in.matches;
                cur.victories += in.victories;
                cur.given += in.given;
                cur.received += in.received;
            }
            existingNameToGroup.put(key, targetGroupId);
        }

        mixedGroupsDetected = false;
        renumberRows();
        refreshRowDerivedMetrics();
        recalculatePByGroups(existingNameToGroup);
        assignGroupLabels(existingNameToGroup);
        calculateFinalPositions();
        backupMergedMatrix();
        renderRows();
        android.widget.Toast.makeText(getContext(), "Imported " + sourceLabel + ": merged by group composition", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void selectCsvFile(int requestCode) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_TITLE, "Select CSV file");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"));
            } catch (Exception e) { /* ignore */ }
        }
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == 1001 || requestCode == 1002) && resultCode == android.app.Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                try {
                    java.io.InputStream is = getContext().getContentResolver().openInputStream(uri);
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    String headerLine = reader.readLine();
                    if (headerLine == null) return;
                    String[] header = headerLine.split(",");
                    
                    // Detect RoundFragment format: header has numeric bout columns (1, 2, 3, ...)
                    int nrPart = 0;
                    for (int i = 2; i < header.length; i++) {
                        if (header[i].trim().matches("\\d+")) nrPart++;
                        else break;
                    }
                    boolean isRoundFormat = (nrPart > 0);
                    // android.util.Log.i("MergedFragment", "CSV format detected: " + (isRoundFormat ? "RoundFragment (nrPart=" + nrPart + ")" : "Merged"));
                    
                    // Read all data lines first
                    java.util.List<String[]> allTokens = new java.util.ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        allTokens.add(line.split(",", -1)); // -1 preserves trailing empty strings
                    }
                    reader.close();
                    
                    java.util.List<Row> csvRows = new java.util.ArrayList<>();
                    
                    if (isRoundFormat) {
                        // RoundFragment format: parse bout results and calculate stats
                        int[][] boutResults = new int[allTokens.size()][allTokens.size()];
                        for (int i = 0; i < allTokens.size(); i++) {
                            for (int j = 0; j < allTokens.size(); j++) {
                                boutResults[i][j] = -1;
                            }
                        }
                        
                        // Parse bout results for each participant
                        for (int i = 0; i < allTokens.size(); i++) {
                            String[] tokens = allTokens.get(i);
                            if (tokens.length < 2) continue;
                            
                            // Parse bout columns (columns 2 to 2+nrPart-1)
                            for (int j = 0; j < nrPart && (2 + j) < tokens.length; j++) {
                                String boutVal = tokens[2 + j].trim();
                                if (!boutVal.isEmpty() && !boutVal.equalsIgnoreCase("X")) {
                                    try {
                                        boutResults[i][j] = Integer.parseInt(boutVal);
                                    } catch (Exception e) {
                                        boutResults[i][j] = -1;
                                    }
                                }
                            }
                        }
                        
                        // Now calculate stats from bout results
                        java.util.List<int[]> csvStatsForRanking = new java.util.ArrayList<>();
                        for (int i = 0; i < allTokens.size(); i++) {
                            String[] tokens = allTokens.get(i);
                            if (tokens.length < 2) continue;
                            
                            int nr = i + 1;
                            String name = tokens[1];
                            
                            int victories = 0, given = 0, received = 0, boutsWon = 0, boutsLost = 0;
                            boolean hasBout = false;
                            for (int j = 0; j < allTokens.size(); j++) {
                                if (i != j && boutResults[i][j] >= 0 && boutResults[j][i] >= 0) {
                                    int myScore = boutResults[i][j];
                                    int oppScore = boutResults[j][i];
                                    if (myScore > oppScore) {
                                        victories++;
                                        boutsWon++;
                                    } else if (myScore < oppScore) {
                                        boutsLost++;
                                    }
                                    given += myScore;
                                    received += oppScore;
                                    hasBout = true;
                                }
                            }
                            int index = given - received;
                            int percent = (boutsWon + boutsLost) > 0 ? (int) Math.round((double) boutsWon / (boutsWon + boutsLost) * 100) : 0;
                            
                            int matches = boutsWon + boutsLost;
                            // P is 0 initially, will be ranked below
                            csvRows.add(new Row(nr, name, matches, victories, given, received, index, percent, 0, "A", null));
                            if (name != null && !name.trim().isEmpty() && hasBout) {
                                csvStatsForRanking.add(new int[]{csvRows.size() - 1, percent, index, given});
                            }
                            // android.util.Log.i("MergedFragment", "CSV Round format row: nr=" + nr + ", name=" + name + ", V=" + victories + ", →=" + given + ", ←=" + received + ", I=" + index + ", %=" + percent);
                        }
                        // Calculate P ranking for CSV Round format rows
                        csvStatsForRanking.sort((a, b) -> {
                            int cmp = Integer.compare(b[1], a[1]); // percent DESC
                            if (cmp != 0) return cmp;
                            cmp = Integer.compare(b[2], a[2]); // index DESC
                            if (cmp != 0) return cmp;
                            return Integer.compare(b[3], a[3]); // given DESC
                        });
                        int csvPos = 1;
                        for (int i = 0; i < csvStatsForRanking.size(); i++) {
                            if (i > 0) {
                                int[] prev = csvStatsForRanking.get(i - 1);
                                int[] curr = csvStatsForRanking.get(i);
                                boolean same = curr[1] == prev[1] && curr[2] == prev[2] && curr[3] == prev[3];
                                if (!same) csvPos = i + 1;
                            }
                            csvRows.get(csvStatsForRanking.get(i)[0]).p = csvPos;
                        }
                    } else {
                        // Merged format (no bout columns): use values directly from CSV
                        int idxMatches = -1, idxV = -1, idxGiven = -1, idxReceived = -1, idxIndex = -1, idxPercent = -1, idxP = -1, idxGrp = -1, idxFinalPos = -1;
                        for (int i = 0; i < header.length; i++) {
                            String h = header[i].trim();
                            if (h.equals("#") || h.equalsIgnoreCase("Matches")) idxMatches = i;
                            else if (h.equals("V")) idxV = i;
                            else if (h.equals("→") || h.equals("->")) idxGiven = i;
                            else if (h.equals("←") || h.equals("<-")) idxReceived = i;
                            else if (h.equals("I")) idxIndex = i;
                            else if (h.equals("%")) idxPercent = i;
                            else if (h.equals("P")) idxP = i;
                            else if (h.equals("Grp")) idxGrp = i;
                            else if (h.equals("FinalPos") || h.equals("Pos")) idxFinalPos = i;
                        }
                        
                        for (int i = 0; i < allTokens.size(); i++) {
                            String[] tokens = allTokens.get(i);
                            if (tokens.length < 2) continue;
                            
                            int nr = parseIntSafe(tokens[0]);
                            String name = tokens[1];
                            int matches = (idxMatches >= 0 && idxMatches < tokens.length) ? parseIntSafe(tokens[idxMatches]) : 0;
                            int victories = (idxV >= 0 && idxV < tokens.length) ? parseIntSafe(tokens[idxV]) : 0;
                            int given = (idxGiven >= 0 && idxGiven < tokens.length) ? parseIntSafe(tokens[idxGiven]) : 0;
                            int received = (idxReceived >= 0 && idxReceived < tokens.length) ? parseIntSafe(tokens[idxReceived]) : 0;
                            int index = (idxIndex >= 0 && idxIndex < tokens.length) ? parseIntSafe(tokens[idxIndex]) : 0;
                            int percent = (idxPercent >= 0 && idxPercent < tokens.length) ? parseIntSafe(tokens[idxPercent]) : 0;
                            int p = (idxP >= 0 && idxP < tokens.length) ? parseIntSafe(tokens[idxP]) : 0;
                            String grp = (idxGrp >= 0 && idxGrp < tokens.length && !tokens[idxGrp].trim().isEmpty()) ? tokens[idxGrp].trim() : "A";
                            if (matches <= 0) matches = inferMatches(victories, percent);
                            Integer finalPos = null;
                            if (idxFinalPos >= 0 && idxFinalPos < tokens.length && !tokens[idxFinalPos].trim().isEmpty()) {
                                finalPos = parseIntSafe(tokens[idxFinalPos]);
                            }
                            // P is kept as-is from CSV — never overwritten by FinalPos
                            
                            csvRows.add(new Row(nr, name, matches, victories, given, received, index, percent, p, grp, finalPos));
                            // android.util.Log.i("MergedFragment", "CSV Merged format row: nr=" + nr + ", name=" + name + ", V=" + victories + ", →=" + given + ", ←=" + received + ", I=" + index + ", %=" + percent + ", P=" + p + ", Grp=" + grp + ", FinalPos=" + finalPos);
                        }
                    }
                    
                    // android.util.Log.i("MergedFragment", "CSV loaded: " + csvRows.size() + " rows");
                    
                    applyImportedRows(csvRows, requestCode == 1001, "CSV");
                    
                } catch (Exception e) {
                    android.util.Log.e("MergedFragment", "CSV load error: " + e.getMessage());
                    e.printStackTrace();
                    android.widget.Toast.makeText(getContext(), "CSV load error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void loadRoundData() {
        // android.util.Log.v("MergedFragment", "loadRoundData() called - aggregating all rounds from ViewModel");

        try {
            ScoresViewModel vm = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
            vm.persistActiveRoundData();
            reloadedDistinctGroupsMerged = false;

            int rounds = vm.getNrRounds().getValue() != null ? vm.getNrRounds().getValue() : 1;
            int nrPart = vm.getNrPart().getValue() != null ? vm.getNrPart().getValue() : 0;
            if (nrPart <= 0) {
                android.widget.Toast.makeText(getContext(), "No Round data available", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            java.util.List<java.util.List<Integer>> roundGroups = buildRoundGroups(vm, rounds, nrPart);
            java.util.List<Row> loadedRows = new java.util.ArrayList<>();

            // Track which round group each participant belongs to
            int groupIndex = 0;
            for (java.util.List<Integer> groupRounds : roundGroups) {
                String grpLabel = groupIdToLabel(groupIndex + 1); // Convert 0,1,2... to A,B,C...
                java.util.Map<String, Agg> byName = new java.util.LinkedHashMap<>();

                for (int roundCode : groupRounds) {
                    int[][] roundResults = vm.getRoundBoutResultsSnapshot(roundCode);
                    String[] participantNames = vm.getRoundParticipantNamesSnapshot(roundCode);
                    if (roundResults == null || participantNames == null) continue;

                    for (int i = 0; i < nrPart; i++) {
                        String name = i < participantNames.length && participantNames[i] != null ? participantNames[i].trim() : "";
                        if (!name.isEmpty() && !byName.containsKey(name)) {
                            byName.put(name, new Agg());
                        }
                    }

                    for (int i = 0; i < nrPart; i++) {
                        String nameI = i < participantNames.length && participantNames[i] != null ? participantNames[i].trim() : "";
                        if (nameI.isEmpty()) continue;
                        Agg ai = byName.computeIfAbsent(nameI, k -> new Agg());

                        for (int j = 0; j < nrPart; j++) {
                            if (i == j) continue;
                            String nameJ = j < participantNames.length && participantNames[j] != null ? participantNames[j].trim() : "";
                            if (nameJ.isEmpty()) continue;

                            boolean validI = i < roundResults.length && roundResults[i] != null && j < roundResults[i].length;
                            boolean validJ = j < roundResults.length && roundResults[j] != null && i < roundResults[j].length;
                            if (!validI || !validJ) continue;
                            int s = roundResults[i][j];
                            int o = roundResults[j][i];
                            if (s < 0 || o < 0) continue;

                            ai.given += s;
                            ai.received += o;
                            if (s > o) {
                                ai.victories++;
                                ai.boutsWon++;
                            } else if (s < o) {
                                ai.boutsLost++;
                            }
                            ai.matches++;
                        }
                    }
                }

                java.util.List<int[]> statsForRanking = new java.util.ArrayList<>();
                java.util.List<String> names = new java.util.ArrayList<>(byName.keySet());
                for (int i = 0; i < names.size(); i++) {
                    String name = names.get(i);
                    Agg a = byName.get(name);
                    int index = a.given - a.received;
                    int totalBouts = a.boutsWon + a.boutsLost;
                    int percent = totalBouts > 0 ? (int) Math.round((double) a.boutsWon / totalBouts * 100.0) : 0;
                    loadedRows.add(new Row(loadedRows.size() + 1, name, a.matches, a.victories, a.given, a.received, index, percent, 0, grpLabel, null));
                    if (totalBouts > 0) {
                        statsForRanking.add(new int[]{loadedRows.size() - 1, percent, index, a.given});
                    }
                }

                statsForRanking.sort((a, b) -> {
                    int cmp = Integer.compare(b[1], a[1]);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(b[2], a[2]);
                    if (cmp != 0) return cmp;
                    return Integer.compare(b[3], a[3]);
                });

                int pos = 1;
                for (int i = 0; i < statsForRanking.size(); i++) {
                    if (i > 0) {
                        int[] prev = statsForRanking.get(i - 1);
                        int[] curr = statsForRanking.get(i);
                        boolean same = curr[1] == prev[1] && curr[2] == prev[2] && curr[3] == prev[3];
                        if (!same) pos = i + 1;
                    }
                    loadedRows.get(statsForRanking.get(i)[0]).p = pos;
                }

                groupIndex++;
            }

            rows.clear();
            rows.addAll(loadedRows);

            // Recalculate P within groups for proper within-group ranking
            java.util.Map<String, Integer> nameToGroup = new java.util.LinkedHashMap<>();
            for (Row r : rows) {
                String key = normalizeName(r.name);
                if (!key.isEmpty() && r.grp != null) {
                    // Map group label (A, B, C) back to group ID (1, 2, 3)
                    int groupId = (r.grp.length() > 0) ? (r.grp.charAt(0) - 'A' + 1) : 1;
                    nameToGroup.put(key, groupId);
                }
            }
            recalculatePByGroups(nameToGroup);

            calculateFinalPositions();
            mixedGroupsDetected = false;
            useCsvOnly = false;
            backupMergedMatrix();
            renderRows();
            android.widget.Toast.makeText(getContext(), "Reloaded merged data from " + rounds + " round(s)", android.widget.Toast.LENGTH_SHORT).show();
            if (reloadedDistinctGroupsMerged) {
                android.widget.Toast.makeText(getContext(), "Same participant in distinct groups: MERGED", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("MergedFragment", "Error aggregating rounds: " + e.getMessage());
            android.widget.Toast.makeText(getContext(), "Reload failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private java.util.List<java.util.List<Integer>> buildRoundGroups(ScoresViewModel vm, int rounds, int nrPart) {
        java.util.List<java.util.Set<String>> roundNameSets = new java.util.ArrayList<>();
        for (int r = 1; r <= rounds; r++) {
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            String[] participantNames = vm.getRoundParticipantNamesSnapshot(r);
            if (participantNames != null) {
                for (int i = 0; i < nrPart && i < participantNames.length; i++) {
                    String name = normalizeName(participantNames[i]);
                    if (!name.isEmpty()) names.add(name);
                }
            }
            roundNameSets.add(names);
        }

        // Rule:
        // 1) exact same participant sets -> same group
        // 2) disjoint sets -> separate groups
        // 3) any overlap between non-equal sets -> collapse all rounds into one group
        for (int i = 0; i < roundNameSets.size(); i++) {
            for (int j = i + 1; j < roundNameSets.size(); j++) {
                java.util.Set<String> left = roundNameSets.get(i);
                java.util.Set<String> right = roundNameSets.get(j);
                if (left.equals(right)) continue;
                if (setsIntersect(left, right)) {
                    reloadedDistinctGroupsMerged = true;
                    java.util.List<java.util.List<Integer>> collapsed = new java.util.ArrayList<>();
                    java.util.List<Integer> allRounds = new java.util.ArrayList<>();
                    for (int r = 1; r <= rounds; r++) allRounds.add(r);
                    collapsed.add(allRounds);
                    return collapsed;
                }
            }
        }

        java.util.List<java.util.List<Integer>> groups = new java.util.ArrayList<>();
        boolean[] assigned = new boolean[rounds];
        for (int i = 0; i < rounds; i++) {
            if (assigned[i]) continue;
            java.util.List<Integer> group = new java.util.ArrayList<>();
            group.add(i + 1);
            assigned[i] = true;
            for (int j = i + 1; j < rounds; j++) {
                if (!assigned[j] && roundNameSets.get(i).equals(roundNameSets.get(j))) {
                    group.add(j + 1);
                    assigned[j] = true;
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private boolean setsIntersect(java.util.Set<String> left, java.util.Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return false;
        if (left.size() > right.size()) {
            java.util.Set<String> swap = left;
            left = right;
            right = swap;
        }
        for (String name : left) {
            if (right.contains(name)) return true;
        }
        return false;
    }

    // Helper: set button background with rounded corners
    private void setRoundedBackground(Button button, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(8 * getResources().getDisplayMetrics().density); // 8dp corners
        button.setBackground(drawable);
    }

    // Generate timestamped filename: prefix_YYYYMMDD_hh.mm.ss.csv
    private String generateTimestampedFilename(String prefix) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HH.mm.ss", java.util.Locale.US);
        String timestamp = sdf.format(new java.util.Date());
        return prefix + "_" + timestamp + ".csv";
    }

    private void renderRows() {
        tableLayout.removeAllViews();
        tableLayoutRight.removeAllViews();
        // Ensure at least 1 row always exists (empty participant with zero fields)
        boolean hadRealData = rows != null && rows.size() > 0;
        if (rows == null || rows.size() == 0) {
            rows = new java.util.ArrayList<>();
            rows.add(new Row(1, "", 0, 0, 0, 0, 0, 0, 0, "A", null));
        }
        // Use dynamic color index from ViewModel (shared with RoundFragment)
        ScoresViewModel scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
        int colorIdx = scoresViewModel.getRoundColorCycleIndex(1);
        int[] pair = RESULT_COLOR_PAIRS[colorIdx % RESULT_COLOR_PAIRS.length];
        TableRow headerRow = new TableRow(getContext());
        for (int col = 0; col < HEADERS.length; col++) {
            final int colIdx = col;
            final String h = HEADERS[col];
            TextView tv = new TextView(getContext());
            tv.setText(h);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
            tv.setTextColor(Color.BLACK);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12); // Smaller text to fit
            tv.setPadding(4, 2, 4, 2); // Reduced padding
            // Set background and border for headers as in RoundFragment
            if (col == 0 || col == 1) {
                tv.setBackground(makeBorderedCell(0xFFA0A0A0));
            } else if (col >= 2 && col <= 7) {
                tv.setBackground(makeBorderedCell(pair[0]));
            } else if (col == 8) {
                tv.setBackground(makeBorderedCell(pair[1]));
            } else if (col == 9) { // Grp header styled as stats (pair[0])
                tv.setBackground(makeBorderedCell(pair[0]));
            } else if (col == 10) { // FinalPos header styled as P
                tv.setBackground(makeBorderedCell(pair[1]));
            } else {
                tv.setBackground(makeBorderedCell(Color.WHITE));
            }
            // Add long-press to all headers except P and FinalPos to navigate to KO page
            if (!h.equals("P") && !h.equals("Grp") && !h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    navigateToNextPage();
                    return true;
                });
            }
            if (h.equals("Grp")) {
                tv.setLongClickable(true);
                tv.setOnClickListener(v -> {
                    toggleSortRowsByGrp();
                    backupMergedMatrix();
                    renderRows();
                });
                tv.setOnLongClickListener(v -> {
                    toggleSortRowsByGrp();
                    backupMergedMatrix();
                    renderRows();
                    return true;
                });
            }
            // Add long-press to P header to sort by P (position)
            if (h.equals("P")) {
                tv.setOnLongClickListener(v -> {
                    boolean asc = toggleSortRowsByP();
                    backupMergedMatrix();
                    renderRows();
                    return true;
                });
            }
            // Add long-press to FinalPos header to sort and log verbose debug
            if (h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    boolean asc = toggleSortRowsByFinalPos();
                    backupMergedMatrix();
                    renderRows();
                    return true;
                });
            }
            headerRow.addView(tv);
        }
        tableLayout.addView(headerRow);
        
        // Calculate available screen height for dynamic split
        float density = getResources().getDisplayMetrics().density;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int buttonBarHeight = (int)(48 * density); // Approximate button bar height
        int headerRowHeight = (int)(36 * density); // Header row height
        int rowHeight = (int)(40 * density); // Estimated row height (smaller text)
        int padding = (int)(24 * density); // Top/bottom padding
        int availableHeight = screenHeight - buttonBarHeight - headerRowHeight - padding;
        int maxRowsPerTable = Math.max(1, availableHeight / rowHeight);
        
        // Determine if we need to split the view based on screen height
        int rowCount = rows.size();
        boolean useSplit = (rowCount > maxRowsPerTable);
        int rowsPerTable = useSplit ? (rowCount + 1) / 2 : rowCount;
        
        // Show or hide right table based on split
        View rightScrollView = getView() != null ? getView().findViewById(R.id.rightScrollView) : null;
        if (rightScrollView != null) {
            rightScrollView.setVisibility(useSplit ? View.VISIBLE : View.GONE);
        }
        
        // Add header to right table if splitting
        if (useSplit) {
            TableRow headerRowRight = createHeaderRow(pair);
            tableLayoutRight.addView(headerRowRight);
        }
        
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            TableRow tr = new TableRow(getContext());
            
            // Determine which table this row goes into
            TableLayout targetTable;
            if (useSplit && i >= rowsPerTable) {
                targetTable = tableLayoutRight;
            } else {
                targetTable = tableLayout;
            }
            
            for (int col = 0; col < HEADERS.length; col++) {
                final int colIdx = col;
                final String header = HEADERS[col];
                final String value;
                switch (col) {
                    case 0: value = String.valueOf(row.nr); break;
                    case 1: value = row.name; break;
                    case 2: value = String.valueOf(row.matches); break;
                    case 3: value = String.valueOf(row.victories); break;
                    case 4: value = String.valueOf(row.given); break;
                    case 5: value = String.valueOf(row.received); break;
                    case 6: value = String.valueOf(row.index); break;
                    case 7: value = String.valueOf(row.percent); break;
                    case 8: value = String.valueOf(row.p); break;
                    case 9: value = row.grp != null ? row.grp : "A"; break;
                    case 10: value = row.finalPos != null ? String.valueOf(row.finalPos) : ""; break;
                    default: value = "";
                }
                EditText cell = new EditText(getContext());
                cell.setText(value);
                cell.setGravity(Gravity.CENTER);
                cell.setTextColor(Color.BLACK);
                cell.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12); // Smaller text to fit
                cell.setPadding(4, 2, 4, 2); // Reduced padding
                cell.setLongClickable(true);
                cell.setTextIsSelectable(false);
                cell.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                    public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                    public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                    public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                    public void onDestroyActionMode(android.view.ActionMode mode) {}
                });
                final int rowIdx = i;
                // FinalPos cells: long press sorts by FinalPos
                if (colIdx == 10) {
                    cell.setBackground(makeBorderedCell(pair[1])); // Same color as FinalPos header
                    cell.setOnLongClickListener(v -> {
                        boolean asc = toggleSortRowsByFinalPos();
                        backupMergedMatrix();
                        renderRows();
                        return true;
                    });
                } else if (colIdx == 8) {
                    cell.setOnLongClickListener(v -> {
                        boolean asc = toggleSortRowsByP();
                        backupMergedMatrix();
                        renderRows();
                        return true;
                    });
                } else if (colIdx == 9) {
                    cell.setOnLongClickListener(v -> {
                        toggleSortRowsByGrp();
                        backupMergedMatrix();
                        renderRows();
                        return true;
                    });
                } else if (colIdx == 1) {
                    cell.setOnLongClickListener(v -> {
                        boolean asc = toggleSortRowsByName();
                        backupMergedMatrix();
                        renderRows();
                        return true;
                    });
                } else {
                    cell.setOnLongClickListener(v -> {
                        navigateToNextPage();
                        return true;
                    });
                }
                cell.setFocusable(false);
                final int colFinal = col;
                final String valueFinal = value;
                // Special handling for Nr column (col == 0): add/remove participant
                // Long press: Cells 1 to floor(N/2): Remove, cells floor(N/2)+1 to N: Add
                if (colIdx == 0) {
                    final int currentRowIdx = rowIdx;
                    cell.setOnLongClickListener(v -> {
                        int totalRows = rows.size();
                        int halfPoint = totalRows / 2;
                        
                        if (currentRowIdx < halfPoint) {
                            // Cells 1 to floor(N/2): REMOVE the last participant
                            if (totalRows > 1) {
                                rows.remove(rows.size() - 1);
                                // Renumber rows
                                for (int idx = 0; idx < rows.size(); idx++) {
                                    rows.get(idx).nr = idx + 1;
                                }
                                // android.util.Log.i("MergedFragment", "Removed last participant, now " + rows.size() + " rows");
                                calculateFinalPositions();
                                backupMergedMatrix();
                                renderRows();
                            }
                        } else {
                            // Cells floor(N/2)+1 to N: ADD a new participant
                            int newNr = rows.size() + 1;
                            rows.add(new Row(newNr, "", 0, 0, 0, 0, 0, 0, 0, "A", null));
                            // android.util.Log.i("MergedFragment", "Added participant, now " + rows.size() + " rows");
                            backupMergedMatrix();
                            renderRows();
                        }
                        return true;
                    });
                } else {
                // FinalPos cells: no click editing
                if (colIdx == 10 || colIdx == 9) {
                    // No click action for Grp or FinalPos cells (calculated, not editable)
                } else {
                cell.setOnClickListener(v -> {
                    EditText input = new EditText(getContext());
                    input.setText(valueFinal);
                    input.setSelectAllOnFocus(true);
                    input.setSingleLine(true);
                    input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                    if (colFinal == 1) {
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    } else {
                        boolean isNumeric = false;
                        try { Double.parseDouble(valueFinal); isNumeric = true; } catch (Exception e) { isNumeric = false; }
                        if (isNumeric) {
                            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                        } else {
                            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                        }
                    }
                    input.requestFocus();
                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
                    builder.setTitle("Edit " + header);
                    builder.setView(input);
                    builder.setPositiveButton("DONE", (dialog, which) -> {
                        String newVal = input.getText().toString();
                        switch (colIdx) {
                            case 1:
                                row.name = newVal;
                                if (row.name == null || row.name.trim().isEmpty()) {
                                    rows.remove(rowIdx);
                                    calculateFinalPositions();
                                    backupMergedMatrix();
                                    renderRows();
                                    return;
                                }
                                break;
                            case 2:
                                try { row.matches = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 3:
                                try { row.victories = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 4:
                                try { row.given = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 5:
                                try { row.received = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 6:
                                try { row.index = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 7:
                                try { row.percent = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 8:
                                try { row.p = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 9:
                                try { row.finalPos = Integer.parseInt(newVal); } catch (Exception ex) { row.finalPos = null; }
                                break;
                        }
                        calculateFinalPositions();
                        backupMergedMatrix();
                        renderRows();
                    });
                    builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.cancel());
                    androidx.appcompat.app.AlertDialog dialog = builder.create();
                    dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    input.setOnEditorActionListener((v2, actionId, event) -> {
                        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
                            dialog.dismiss();
                            return true;
                        }
                        return false;
                    });
                    dialog.show();
                });
                } // end else for P/FinalPos check
                } // end else for non-Nr columns
                tr.addView(cell);
            }
            targetTable.addView(tr);
        }
    }
    
    // Helper to create a header row for the right table (duplicate headers)
    private TableRow createHeaderRow(int[] pair) {
        TableRow headerRow = new TableRow(getContext());
        for (int col = 0; col < HEADERS.length; col++) {
            final int colIdx = col;
            final String h = HEADERS[col];
            TextView tv = new TextView(getContext());
            tv.setText(h);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
            tv.setTextColor(Color.BLACK);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12); // Smaller text to fit
            tv.setPadding(4, 2, 4, 2); // Reduced padding
            // Set background and border for headers as in main table
            if (col == 0 || col == 1) {
                tv.setBackground(makeBorderedCell(0xFFA0A0A0));
            } else if (col >= 2 && col <= 7) {
                tv.setBackground(makeBorderedCell(pair[0]));
            } else if (col == 8) {
                tv.setBackground(makeBorderedCell(pair[1]));
            } else if (col == 9) {
                tv.setBackground(makeBorderedCell(pair[1]));
            } else {
                tv.setBackground(makeBorderedCell(Color.WHITE));
            }
            // Add navigation long press for right table headers too
            if (!h.equals("P") && !h.equals("Grp") && !h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    navigateToNextPage();
                    return true;
                });
            }
            if (h.equals("Grp")) {
                tv.setLongClickable(true);
                tv.setOnClickListener(v -> {
                    toggleSortRowsByGrp();
                    backupMergedMatrix();
                    renderRows();
                });
                tv.setOnLongClickListener(v -> {
                    toggleSortRowsByGrp();
                    backupMergedMatrix();
                    renderRows();
                    return true;
                });
            }
            if (h.equals("P")) {
                tv.setOnLongClickListener(v -> {
                    boolean asc = toggleSortRowsByP();
                    backupMergedMatrix();
                    renderRows();
                    return true;
                });
            }
            if (h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    boolean asc = toggleSortRowsByFinalPos();
                    backupMergedMatrix();
                    renderRows();
                    return true;
                });
            }
            headerRow.addView(tv);
        }
        return headerRow;
    }

    private boolean toggleSortRowsByName() {
        final boolean asc = nameSortAscending;
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                String an = a != null && a.name != null ? a.name.trim() : "";
                String bn = b != null && b.name != null ? b.name.trim() : "";
                if (an.isEmpty() && bn.isEmpty()) return 0;
                if (an.isEmpty()) return 1;
                if (bn.isEmpty()) return -1;
                return asc ? an.compareToIgnoreCase(bn) : bn.compareToIgnoreCase(an);
            }
        });
        nameSortAscending = !nameSortAscending;
        return asc;
    }

    private boolean toggleSortRowsByP() {
        final boolean asc = pSortAscending;
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                int cmp = asc ? Integer.compare(a.p, b.p) : Integer.compare(b.p, a.p);
                if (cmp != 0) return cmp;
                String an = a != null && a.name != null ? a.name.trim() : "";
                String bn = b != null && b.name != null ? b.name.trim() : "";
                return an.compareToIgnoreCase(bn);
            }
        });
        pSortAscending = !pSortAscending;
        return asc;
    }

    private boolean toggleSortRowsByGrp() {
        final boolean asc = grpSortAscending;
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                String ag = normalizeGroupLabel(a != null ? a.grp : null);
                String bg = normalizeGroupLabel(b != null ? b.grp : null);
                int cmp = asc ? ag.compareToIgnoreCase(bg) : bg.compareToIgnoreCase(ag);
                if (cmp != 0) return cmp;
                String an = a != null && a.name != null ? a.name.trim() : "";
                String bn = b != null && b.name != null ? b.name.trim() : "";
                return an.compareToIgnoreCase(bn);
            }
        });
        grpSortAscending = !grpSortAscending;
        return asc;
    }

    private boolean toggleSortRowsByFinalPos() {
        final boolean asc = finalPosSortAscending;
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                Integer af = a != null ? a.finalPos : null;
                Integer bf = b != null ? b.finalPos : null;
                if (af == null && bf == null) return 0;
                if (af == null) return 1;
                if (bf == null) return -1;
                return asc ? Integer.compare(af, bf) : Integer.compare(bf, af);
            }
        });
        finalPosSortAscending = !finalPosSortAscending;
        return asc;
    }

    private void addCell(TableRow tr, String value, boolean editable) {
        EditText et = new EditText(getContext());
        et.setText(value);
        et.setGravity(Gravity.CENTER);
        et.setSingleLine(true);
        if (!editable) {
            et.setInputType(InputType.TYPE_NULL);
            et.setFocusable(false);
        } else {
            int col = tr.getChildCount();
            boolean isNumeric = false;
            try {
                Double.parseDouble(value);
                isNumeric = true;
            } catch (Exception e) {
                isNumeric = false;
            }
            // Always use single-line and IME_ACTION_DONE for all editable cells
            if (col == 1) { // Name column
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            } else if (isNumeric) {
                et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            } else {
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            }
            et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            et.setSelectAllOnFocus(true);
            // Prevent Enter from adding a newline
            et.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    // If Enter is pressed, treat as Done
                    et.clearFocus();
                    return true;
                }
                return false;
            });
            et.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    et.clearFocus();
                    return true;
                }
                return false;
            });
            et.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    et.post(() -> et.selectAll());
                } else {
                    int colIdx = tr.indexOfChild(et);
                    int rowIdx = tableLayout.indexOfChild(tr) - 1; // -1 for header
                    if (rowIdx < 0 || rowIdx >= rows.size()) {
                        // Defensive: do not crash, just return
                        return;
                    }
                    Row row = rows.get(rowIdx);
                    String newVal = et.getText().toString();
                    switch (colIdx) {
                        case 1: // Name
                            row.name = newVal;
                            if (row.name == null || row.name.trim().isEmpty()) {
                                rows.remove(rowIdx);
                                calculateFinalPositions();
                                renderRows();
                                return;
                            }
                            break;
                        case 2: // Matches
                            try { row.matches = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 3: // Victories
                            try { row.victories = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 4: // Given
                            try { row.given = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 5: // Received
                            try { row.received = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 6: // Index
                            try { row.index = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 7: // Percent
                            try { row.percent = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 8: // P
                            try { row.p = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                    }
                    calculateFinalPositions();
                    backupMergedMatrix();
                    renderRows();
                }
            });
        }
        et.setBackgroundColor(Color.WHITE);
        et.setTextColor(Color.BLACK);
        et.setPadding(4, 2, 4, 2);
        et.setEnabled(editable);
        tr.addView(et);
    }

    // Synchronize ScoresViewModel participant names and count with current rows
    private void syncViewModelWithRows() {
        ScoresViewModel scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
        java.util.List<String> namesList = new java.util.ArrayList<>();
        for (Row r : rows) {
            if (r.name != null && !r.name.trim().isEmpty()) {
                namesList.add(r.name);
            }
        }
        String[] names = namesList.toArray(new String[0]);
        // Set participantNames BEFORE nrPart to avoid observer race condition
        scoresViewModel.setParticipantNames(names);
        scoresViewModel.setNrPart(names.length);
    }

    // Calculates and assigns FinalPos for all rows using %, I, →
    private void calculateFinalPositions() {
        // Clear all FinalPos values first to ensure fresh recalculation
        for (Row r : rows) {
            r.finalPos = null;
        }
        
        // Remove all but one row with empty Name and null FinalPos
        int emptyRowIdx = -1;
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row r = rows.get(i);
            if ((r.finalPos == null || r.finalPos == 0) && (r.name == null || r.name.trim().isEmpty())) {
                if (emptyRowIdx == -1) {
                    emptyRowIdx = i; // keep the first found (from end)
                } else {
                    rows.remove(i);
                }
            }
        }
        // Only rank rows with non-empty names
        java.util.List<Row> toRank = new java.util.ArrayList<>();
        for (Row r : rows) {
            if (r.name != null && !r.name.trim().isEmpty()) {
                // Treat empty or invalid %, I, or → as lowest possible rank
                if (String.valueOf(r.percent).trim().isEmpty()) r.percent = Integer.MIN_VALUE;
                if (String.valueOf(r.index).trim().isEmpty()) r.index = Integer.MIN_VALUE;
                if (String.valueOf(r.given).trim().isEmpty()) r.given = Integer.MIN_VALUE;
                toRank.add(r);
            } else {
                r.finalPos = null; // Not ranked
            }
        }
        // Sort by percent DESC, then index DESC, then given DESC, then received ASC
        java.util.Collections.sort(toRank, new java.util.Comparator<Row>() {
            public int compare(Row a, Row b) {
                int cmp = Integer.compare(b.percent, a.percent);
                if (cmp != 0) return cmp; // DESC: higher percent first
                cmp = Integer.compare(b.index, a.index);
                if (cmp != 0) return cmp; // DESC: higher index first
                cmp = Integer.compare(b.given, a.given);
                if (cmp != 0) return cmp; // DESC: higher given first
                return Integer.compare(a.received, b.received); // ASC: lower received first
            }
        });

        // Randomize complete ties and assign unique sequential FinalPos values.
        java.util.Random random = new java.util.Random(System.nanoTime());
        java.util.List<Row> randomized = new java.util.ArrayList<>();
        for (int i = 0; i < toRank.size(); ) {
            int j = i + 1;
            while (j < toRank.size() &&
                   toRank.get(j).percent == toRank.get(i).percent &&
                   toRank.get(j).index == toRank.get(i).index &&
                   toRank.get(j).given == toRank.get(i).given &&
                   toRank.get(j).received == toRank.get(i).received) j++;
            java.util.List<Row> tieBlock = new java.util.ArrayList<>(toRank.subList(i, j));
            if (tieBlock.size() > 1) {
                java.util.Collections.shuffle(tieBlock, random);
            }
            randomized.addAll(tieBlock);
            i = j;
        }
        int pos = 1;
        for (Row r : randomized) {
            r.finalPos = pos++;
        }
        // Set all 0 FinalPos to null for consistency
        for (Row r : rows) {
            if (r.finalPos != null && r.finalPos == 0) r.finalPos = null;
        }
    }
    
    // ===================== QR CODE METHODS =====================
    
    // Generate CSV data from current rows
    private String generateCsvData() {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("Nr,Name,#,V,→,←,I,%,P,Grp,FinalPos\n");
        // Data rows
        for (Row r : rows) {
            sb.append(r.nr).append(",");
            sb.append(r.name != null ? r.name.replace(",", ";") : "").append(",");
            sb.append(r.matches).append(",");
            sb.append(r.victories).append(",");
            sb.append(r.given).append(",");
            sb.append(r.received).append(",");
            sb.append(r.index).append(",");
            sb.append(r.percent).append(",");
            sb.append(r.p).append(",");
            sb.append(r.grp != null ? r.grp : "A").append(",");
            sb.append(r.finalPos != null ? r.finalPos : "").append("\n");
        }
        return sb.toString();
    }
    
    // Compress string data using GZIP and encode as Base64
    private String compressData(String data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzos = new GZIPOutputStream(baos);
            gzos.write(data.getBytes("UTF-8"));
            gzos.close();
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.e("MergedFragment", "Compression failed", e);
            return null;
        }
    }
    
    // Decompress Base64+GZIP data back to string
    private String decompressData(String compressed) {
        try {
            byte[] data = Base64.decode(compressed, Base64.NO_WRAP);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            GZIPInputStream gzis = new GZIPInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            gzis.close();
            return baos.toString("UTF-8");
        } catch (Exception e) {
            android.util.Log.e("MergedFragment", "Decompression failed", e);
            return null;
        }
    }
    
    // Get QR code module count for given data (for anti-aliasing calculation)
    private int getQrModuleCount(String data) {
        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, 1, 1);
            return matrix.getWidth(); // Module count (21 for v1, up to 177 for v40)
        } catch (Exception e) {
            return 177; // Assume worst case (version 40)
        }
    }
    
    // Generate QR code bitmap from data at exact size
    private Bitmap generateQrCode(String data, int size) {
        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("MergedFragment", "QR generation failed", e);
            return null;
        }
    }
    
    // Show QR code fullscreen - tap to close
    private void showQrCodeFullscreen() {
        if (rows == null || rows.isEmpty()) {
            android.widget.Toast.makeText(getContext(), "No data to export", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        String csvData = generateCsvData();
        String compressed = compressData(csvData);
        
        if (compressed == null) {
            android.widget.Toast.makeText(getContext(), "Failed to compress data", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check data size - QR code max is ~2953 bytes for version 40
        if (compressed.length() > 2900) {
            android.widget.Toast.makeText(getContext(), "Data too large for QR code (" + compressed.length() + " bytes)", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        
        // Get screen dimensions
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        // Get QR module count for anti-aliasing calculation
        int moduleCount = getQrModuleCount(compressed);
        
        // Calculate optimal size: 97% of screen height, minimum 80% of screen width
        int maxHeight = (int)(screenHeight * 0.97);
        int minWidth = (int)(screenWidth * 0.80);
        
        // Start with max height, round down to multiple of modules for clean pixels
        int qrSize = (maxHeight / moduleCount) * moduleCount;
        
        // Ensure minimum width constraint
        if (qrSize < minWidth) {
            // Need to increase to meet minimum width, round up to next module multiple
            qrSize = ((minWidth / moduleCount) + 1) * moduleCount;
        }
        
        // Safety check: don't exceed screen dimensions
        qrSize = Math.min(qrSize, Math.min(screenWidth, screenHeight));
        
        Bitmap qrBitmap = generateQrCode(compressed, qrSize);
        
        if (qrBitmap == null) {
            android.widget.Toast.makeText(getContext(), "Failed to generate QR code", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create fullscreen dialog
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Create horizontal layout with vertical label + QR code
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setBackgroundColor(0xFFFFFFFF);
        container.setGravity(Gravity.CENTER);
        
        // Vertical label "Merged ranking" on the left
        TextView label = new TextView(requireContext());
        label.setText("MergedRanking");
        label.setTextColor(0xFF333333);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setRotation(-90);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.CENTER;
        label.setLayoutParams(labelParams);
        container.addView(label);
        
        ImageView imageView = new ImageView(requireContext());
        imageView.setImageBitmap(qrBitmap);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        container.addView(imageView);
        
        container.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(container);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.show();
        
        // android.util.Log.i("MergedFragment", "QR OUT: " + qrSize + "px (" + moduleCount + " modules, " +
        //     (qrSize/moduleCount) + "px/module), " + compressed.length() + " bytes");
    }
    
    // Start QR scanner - show dialog to choose camera or gallery
    private void startQrScanner() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add from QR Code")
            .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                if (which == 0) {
                    launchCameraScanner();
                } else {
                    imagePickerLauncher.launch("image/*");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // Launch camera scanner
    private void launchCameraScanner() {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), 
                new String[]{Manifest.permission.CAMERA}, 100);
            return;
        }
        
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR code to add participants");
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
                handleQrScanResult(result.getText());
            } else {
                android.widget.Toast.makeText(getContext(), "No QR code found in image", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (com.google.zxing.NotFoundException e) {
            android.widget.Toast.makeText(getContext(), "No QR code found in image", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("MergedFragment", "Failed to decode QR from image", e);
            android.widget.Toast.makeText(getContext(), "Failed to read image: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    // Handle scanned QR code result - adds to existing data
    private void handleQrScanResult(String scannedData) {
        // android.util.Log.i("MergedFragment", "QR ADD: Received " + scannedData.length() + " bytes");
        
        // Decompress the data
        String csvData = decompressData(scannedData);
        
        if (csvData == null) {
            android.widget.Toast.makeText(getContext(), "Failed to decode QR data", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Parse CSV and add to rows (not replace)
        try {
            String[] lines = csvData.split("\n");
            if (lines.length < 2) {
                android.widget.Toast.makeText(getContext(), "Invalid data format", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            java.util.List<Row> incomingRows = new java.util.ArrayList<>();
            String headerLine = lines[0].trim();
            boolean hasMatches = headerLine.contains(",#,") || headerLine.contains(",Matches,");
            int base = hasMatches ? 3 : 2;
            
            // Detect Grp column index by finding "Grp" in header
            int idxGrpInQr = -1;
            String[] headerCols = headerLine.split(",", -1);
            for (int h = 0; h < headerCols.length; h++) {
                if (headerCols[h].trim().equals("Grp")) {
                    idxGrpInQr = h;
                    break;
                }
            }
            
            // Skip header, parse data rows and add to existing
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                
                String[] cols = line.split(",", -1);
                if (cols.length < (base + 6)) continue;
                
                int nr = incomingRows.size() + 1;
                String name = cols[1].replace(";", ",");
                int matches = hasMatches ? parseInt(cols[2], 0) : 0;
                int victories = parseInt(cols[base], 0);
                int given = parseInt(cols[base + 1], 0);
                int received = parseInt(cols[base + 2], 0);
                int index = parseInt(cols[base + 3], 0);
                int percent = parseInt(cols[base + 4], 0);
                int p = parseInt(cols[base + 5], 0);
                String grp = (idxGrpInQr >= 0 && idxGrpInQr < cols.length && !cols[idxGrpInQr].isEmpty()) ? cols[idxGrpInQr].trim() : "A";
                if (matches <= 0) matches = inferMatches(victories, percent);
                Integer finalPos = cols.length > (base + 6) && !cols[base + 6].isEmpty() ? parseInt(cols[base + 6], 0) : null;

                incomingRows.add(new Row(nr, name, matches, victories, given, received, index, percent, p, grp, finalPos));
            }

            applyImportedRows(incomingRows, false, "QR");
            
        } catch (Exception e) {
            android.util.Log.e("MergedFragment", "Failed to parse QR data", e);
            android.widget.Toast.makeText(getContext(), "Failed to parse data: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    // Helper to parse int with default
    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}

