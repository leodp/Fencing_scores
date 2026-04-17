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
                            // Observe colorCycleIndex and update table colors when it changes
                            scoresViewModel.getColorCycleIndex().observe(getViewLifecycleOwner(), idx -> {
                                renderRows();
                            });
                            // Debug: log fragment created
                            android.util.Log.d("MergedFragment", "onViewCreated: MergedFragment created and observers set");
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
                                android.util.Log.d("MergedFragment", "No crash detected, skipping auto-restore");
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
                                                    int victories = tokens.length > 2 ? parseIntSafe(tokens[2]) : 0;
                                                    int given = tokens.length > 3 ? parseIntSafe(tokens[3]) : 0;
                                                    int received = tokens.length > 4 ? parseIntSafe(tokens[4]) : 0;
                                                    int index = tokens.length > 5 ? parseIntSafe(tokens[5]) : 0;
                                                    int percent = tokens.length > 6 ? parseIntSafe(tokens[6]) : 0;
                                                    int p = tokens.length > 7 ? parseIntSafe(tokens[7]) : 0;
                                                    Integer finalPos = tokens.length > 8 && !tokens[8].trim().isEmpty() ? parseIntSafe(tokens[8]) : null;
                                                    loadedRows.add(new Row(nr, name, victories, given, received, index, percent, p, finalPos));
                                                }
                                            }
                                        }
                                        reader.close();
                                        
                                        if (!loadedRows.isEmpty()) {
                                            rows.clear();
                                            rows.addAll(loadedRows);
                                            android.util.Log.i("MergedFragment", "Auto-restored " + rows.size() + " rows from Merged_backup.csv");
                                            return; // Success, no need to fall back
                                        }
                                    }
                                    reader.close();
                                }
                                
                                // Fall back to Fencing_backup.csv (Round data) if Merged backup doesn't exist or is empty
                                java.io.File roundBackupFile = new java.io.File(filesDir, "Fencing_backup.csv");
                                if (roundBackupFile.exists()) {
                                    android.util.Log.i("MergedFragment", "No valid Merged_backup.csv, loading from Fencing_backup.csv");
                                    loadRoundData();
                                }
                            } catch (Exception e) {
                                android.util.Log.w("MergedFragment", "Auto-restore failed: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onStart() {
                            super.onStart();
                            // If rows is empty, try to restore from backup first
                            if (rows == null || rows.isEmpty()) {
                                tryAutoRestoreFromBackup();
                            }
                            // Also redraw table and colors when fragment starts (covers navigation)
                            renderRows();
                        }
                    // Helper to move to the previous page (Merged -> Round, KO -> Merged, etc.)
                    private void navigateToPreviousPage() {
        if (getActivity() instanceof com.fencing.scores.MainActivity) {
            com.fencing.scores.MainActivity mainActivity = (com.fencing.scores.MainActivity) getActivity();
            android.util.Log.i("MergedFragment", "Navigating to Round page (index 0)");
            mainActivity.navigateToPage(0);
        } else if (getActivity() instanceof com.fencing.scores.MergedActivity) {
            android.util.Log.i("MergedFragment", "MergedFragment is attached to MergedActivity. Navigation to Round page is not supported in this context.");
            android.widget.Toast.makeText(getContext(), "Navigation to Round page is only available in MainActivity.", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.util.Log.e("MergedFragment", "Activity is not MainActivity or MergedActivity, cannot navigate to previous page. Actual activity: " + (getActivity() != null ? getActivity().getClass().getName() : "null"));
        }
                    }
                // Helper to move to the next page (KO -> Round, Merged -> KO, etc.)
                private void navigateToNextPage() {
                    android.util.Log.d("MergedFragment", "navigateToNextPage: Attempting navigation from Merged to KO");
                    if (getActivity() instanceof com.fencing.scores.MainActivity) {
                        com.fencing.scores.MainActivity mainActivity = (com.fencing.scores.MainActivity) getActivity();
                        android.util.Log.i("MergedFragment", "navigateToNextPage: Navigating to KO page (index 2)");
                        mainActivity.navigateToPage(2);
                    } else {
                        android.util.Log.e("MergedFragment", "navigateToNextPage: Activity is not MainActivity, cannot navigate to KO page. Actual activity: " + (getActivity() != null ? getActivity().getClass().getName() : "null"));
                    }
                }
            // Color pairs for result columns (cycled, copied from RoundFragment)
            private static final int[][] RESULT_COLOR_PAIRS = {
                {0xFFFFD700, 0xFFFF7F50}, // Default
                {0xFF87CEFA, 0xFFB0C4DE},
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
                java.io.File filesDir = requireContext().getFilesDir();
                java.io.File backupFile = new java.io.File(filesDir, "Merged_backup.csv");
                java.io.FileWriter writer = new java.io.FileWriter(backupFile, false);
                // Write header
                writer.write("Nr,Name,V,→,←,I,%,P,FinalPos\n");
                for (Row r : rows) {
                    writer.write(
                        r.nr + "," +
                        (r.name != null ? r.name : "") + "," +
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
            intent.putExtra(android.content.Intent.EXTRA_TITLE, "Ranking.csv");
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
                writer.write("Nr,Name,V,→,←,I,%,P,FinalPos\n");
                for (Row r : rows) {
                    writer.write(
                        r.nr + "," +
                        (r.name != null ? r.name : "") + "," +
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
    private static final String[] HEADERS = {"Nr", "Name", "V", "→", "←", "I", "%", "P", "FinalPos"};
    private TableLayout tableLayout;
    private TableLayout tableLayoutRight;  // Right side table for split view
    private Button replaceCsvBtn, addCsvBtn, reloadBtn;
    private java.util.List<Row> rows = new java.util.ArrayList<>();
    private boolean useCsvOnly = false;
    private boolean didRestore = false;

    static class Row {
        int nr;
        String name;
        int victories, given, received, index, percent, p;
        Integer finalPos;
        Row(int nr, String name, int victories, int given, int received, int index, int percent, int p, Integer finalPos) {
            this.nr = nr; this.name = name; this.victories = victories; this.given = given; this.received = received;
            this.index = index; this.percent = percent; this.p = p; this.finalPos = finalPos;
        }
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
        int btnMargin = 12;
        
        reloadBtn = new Button(getContext());
        reloadBtn.setText("RELOAD round");
        setRoundedBackground(reloadBtn, 0xFF388E3C); // Green
        reloadBtn.setTextColor(0xFFFFFFFF);
        reloadBtn.setMinWidth((int)(130 * getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, btnMargin, 0);
        reloadBtn.setLayoutParams(lp1);
        btnLayout.addView(reloadBtn);
        
        replaceCsvBtn = new Button(getContext());
        replaceCsvBtn.setText("REPLACE");
        setRoundedBackground(replaceCsvBtn, darkBlue);
        replaceCsvBtn.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 0, btnMargin, 0);
        replaceCsvBtn.setLayoutParams(lp2);
        btnLayout.addView(replaceCsvBtn);
        
        addCsvBtn = new Button(getContext());
        addCsvBtn.setText("ADD");
        setRoundedBackground(addCsvBtn, darkBlue);
        addCsvBtn.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp3.setMargins(0, 0, btnMargin, 0);
        addCsvBtn.setLayoutParams(lp3);
        btnLayout.addView(addCsvBtn);
        
        Button restoreBtn = new Button(getContext());
        restoreBtn.setText("RESTORE crash");
        setRoundedBackground(restoreBtn, darkBlue);
        restoreBtn.setTextColor(0xFFFFFFFF);
        restoreBtn.setMinWidth((int)(130 * getResources().getDisplayMetrics().density));
        restoreBtn.setOnClickListener(v -> {
            android.util.Log.v("MergedFragment", "RESTORE button pressed - loading from Merged_backup.csv");
            restoreData();
        });
        LinearLayout.LayoutParams lpRestore = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRestore.setMargins(0, 0, btnMargin * 4, 0);
        restoreBtn.setLayoutParams(lpRestore);
        btnLayout.addView(restoreBtn);
        
        // QR OUT button - generates QR code from Merged data
        Button qrOutBtn = new Button(getContext());
        qrOutBtn.setText("QR OUT");
        setRoundedBackground(qrOutBtn, darkBlue);
        qrOutBtn.setTextColor(0xFFFFFFFF);
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
        qrInBtn.setOnClickListener(v -> startQrScanner());
        LinearLayout.LayoutParams lpQrIn = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpQrIn.setMargins(0, 0, btnMargin * 4, 0);
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
            android.util.Log.v("MergedFragment", "RELOAD button pressed - loading from RoundFragment");
            useCsvOnly = false;
            loadRoundData();
        });
        replaceCsvBtn.setOnClickListener(v -> { selectCsvFile(1001); renderRows(); });
        addCsvBtn.setOnClickListener(v -> { selectCsvFile(1002); renderRows(); });
        
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
        android.util.Log.v("MergedFragment", "restoreData() called");
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
            int idxV = -1, idxGiven = -1, idxReceived = -1, idxIndex = -1, idxPercent = -1, idxP = -1, idxFinalPos = -1;
            for (int i = 0; i < header.length; i++) {
                sb.append("Header[").append(i).append("] = '").append(header[i]).append("'\n");
                String h = header[i].trim();
                if (h.equals("V")) idxV = i;
                else if (h.equals("→") || h.equals("->")) idxGiven = i;
                else if (h.equals("←") || h.equals("<-")) idxReceived = i;
                else if (h.equals("I")) idxIndex = i;
                else if (h.equals("%")) idxPercent = i;
                else if (h.equals("P")) idxP = i;
                else if (h.equals("FinalPos")) idxFinalPos = i;
            }
            String line;
            android.util.Log.v("MergedFragment", sb.toString());
            java.util.List<Row> loadedRows = new java.util.ArrayList<>();
            int[][] restoredBoutResults = new int[nrPart][nrPart];
            for (int i = 0; i < nrPart; i++) for (int j = 0; j < nrPart; j++) restoredBoutResults[i][j] = -1;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                            android.util.Log.v("MergedFragment", "Parsing CSV line " + lineNum + ": " + line);
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
                    int victories = (idxV >= 0 && idxV < tokens.length) ? parseIntSafe(tokens[idxV]) : 0;
                    int given = (idxGiven >= 0 && idxGiven < tokens.length) ? parseIntSafe(tokens[idxGiven]) : 0;
                    int received = (idxReceived >= 0 && idxReceived < tokens.length) ? parseIntSafe(tokens[idxReceived]) : 0;
                    int index = (idxIndex >= 0 && idxIndex < tokens.length) ? parseIntSafe(tokens[idxIndex]) : 0;
                    int percent = (idxPercent >= 0 && idxPercent < tokens.length) ? parseIntSafe(tokens[idxPercent]) : 0;
                    int p = (idxP >= 0 && idxP < tokens.length) ? parseIntSafe(tokens[idxP]) : 0;
                    Integer finalPos = null;
                    if (idxFinalPos >= 0 && idxFinalPos < tokens.length) {
                        try {
                            int fp = Integer.parseInt(tokens[idxFinalPos].trim());
                            if (fp != 0) finalPos = fp;
                        } catch (Exception e) { finalPos = null; }
                    }
                    loadedRows.add(new Row(nr, name, victories, given, received, index, percent, p, finalPos));
                                        android.util.Log.v("MergedFragment", "Row added: Nr=" + nr + ", Name='" + name + "', V=" + victories + ", →=" + given + ", ←=" + received + ", I=" + index + ", %=" + percent + ", P=" + p + ", FinalPos=" + finalPos);
                    android.util.Log.i("MergedFragment", "CSV line " + lineNum + " parsed: nr=" + nr + ", name=" + name + ", V=" + victories + ", ->=" + given + ", <-=" + received + ", I=" + index + ", %=" + percent + ", P=" + p);
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
                    for (int j = 0; j < restoredBoutResults.length; j++) {
                        if (i != j && restoredBoutResults[i][j] >= 0 && restoredBoutResults[j][i] >= 0) {
                            given += restoredBoutResults[i][j];
                            received += restoredBoutResults[j][i];
                        }
                    }
                    loadedRows.get(i).given = given;
                    loadedRows.get(i).received = received;
                }
            }
            // For Merged backup, keep the values as loaded from CSV (do not recalculate)
            rows.clear();
            android.util.Log.v("MergedFragment", "Clearing rows and loading backup...");
            rows.addAll(loadedRows);
            android.util.Log.v("MergedFragment", "Loaded rows: " + rows.size());
            useCsvOnly = true;
            // Only recalculate FinalPos for Round backup; Merged backup already has FinalPos
            if (!isMergedBackup) {
                calculateFinalPositions();
                android.util.Log.v("MergedFragment", "Calculating final positions after Round RESTORE...");
            } else {
                android.util.Log.v("MergedFragment", "Skipping FinalPos recalculation for Merged backup (using saved values)");
            }
            renderRows();
            android.util.Log.v("MergedFragment", "Rendering rows after RESTORE...");
            android.widget.Toast.makeText(getContext(), "Restored from: " + backupFile.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "Restore failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    // Helper to safely parse integers
    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
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
                    android.util.Log.i("MergedFragment", "CSV format detected: " + (isRoundFormat ? "RoundFragment (nrPart=" + nrPart + ")" : "Merged"));
                    
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
                        for (int i = 0; i < allTokens.size(); i++) {
                            String[] tokens = allTokens.get(i);
                            if (tokens.length < 2) continue;
                            
                            int nr = i + 1;
                            String name = tokens[1];
                            
                            int victories = 0, given = 0, received = 0, boutsWon = 0, boutsLost = 0;
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
                                }
                            }
                            int index = given - received;
                            int percent = (boutsWon + boutsLost) > 0 ? (int) Math.round((double) boutsWon / (boutsWon + boutsLost) * 100) : 0;
                            
                            // P is 0 initially, FinalPos will be calculated after
                            csvRows.add(new Row(nr, name, victories, given, received, index, percent, 0, null));
                            android.util.Log.i("MergedFragment", "CSV Round format row: nr=" + nr + ", name=" + name + ", V=" + victories + ", →=" + given + ", ←=" + received + ", I=" + index + ", %=" + percent);
                        }
                    } else {
                        // Merged format (no bout columns): use values directly from CSV
                        int idxV = -1, idxGiven = -1, idxReceived = -1, idxIndex = -1, idxPercent = -1, idxP = -1, idxFinalPos = -1;
                        for (int i = 0; i < header.length; i++) {
                            String h = header[i].trim();
                            if (h.equals("V")) idxV = i;
                            else if (h.equals("→") || h.equals("->")) idxGiven = i;
                            else if (h.equals("←") || h.equals("<-")) idxReceived = i;
                            else if (h.equals("I")) idxIndex = i;
                            else if (h.equals("%")) idxPercent = i;
                            else if (h.equals("P")) idxP = i;
                            else if (h.equals("FinalPos") || h.equals("Pos")) idxFinalPos = i;
                        }
                        
                        for (int i = 0; i < allTokens.size(); i++) {
                            String[] tokens = allTokens.get(i);
                            if (tokens.length < 2) continue;
                            
                            int nr = parseIntSafe(tokens[0]);
                            String name = tokens[1];
                            int victories = (idxV >= 0 && idxV < tokens.length) ? parseIntSafe(tokens[idxV]) : 0;
                            int given = (idxGiven >= 0 && idxGiven < tokens.length) ? parseIntSafe(tokens[idxGiven]) : 0;
                            int received = (idxReceived >= 0 && idxReceived < tokens.length) ? parseIntSafe(tokens[idxReceived]) : 0;
                            int index = (idxIndex >= 0 && idxIndex < tokens.length) ? parseIntSafe(tokens[idxIndex]) : 0;
                            int percent = (idxPercent >= 0 && idxPercent < tokens.length) ? parseIntSafe(tokens[idxPercent]) : 0;
                            int p = (idxP >= 0 && idxP < tokens.length) ? parseIntSafe(tokens[idxP]) : 0;
                            Integer finalPos = null;
                            if (idxFinalPos >= 0 && idxFinalPos < tokens.length && !tokens[idxFinalPos].trim().isEmpty()) {
                                finalPos = parseIntSafe(tokens[idxFinalPos]);
                            }
                            // If FinalPos was in CSV, use it; otherwise set P as initial position
                            if (finalPos != null) {
                                p = finalPos; // Keep P in sync with FinalPos
                            }
                            
                            csvRows.add(new Row(nr, name, victories, given, received, index, percent, p, finalPos));
                            android.util.Log.i("MergedFragment", "CSV Merged format row: nr=" + nr + ", name=" + name + ", V=" + victories + ", →=" + given + ", ←=" + received + ", I=" + index + ", %=" + percent + ", P=" + p + ", FinalPos=" + finalPos);
                        }
                    }
                    
                    android.util.Log.i("MergedFragment", "CSV loaded: " + csvRows.size() + " rows");
                    
                    if (requestCode == 1001) {
                        // REPLACE: clear existing rows and use loaded rows
                        rows.clear();
                        rows.addAll(csvRows);
                    } else if (requestCode == 1002) {
                        // ADD: append loaded rows to existing, renumber
                        int startNr = rows.size() + 1;
                        for (int i = 0; i < csvRows.size(); i++) {
                            Row r = csvRows.get(i);
                            r.nr = startNr + i;
                            rows.add(r);
                        }
                    }
                    
                    // Always recalculate FinalPos after loading
                    calculateFinalPositions();
                    backupMergedMatrix();
                    renderRows();
                    
                    android.widget.Toast.makeText(getContext(), (requestCode == 1001 ? "Replaced" : "Added") + " " + csvRows.size() + " participants", android.widget.Toast.LENGTH_SHORT).show();
                    
                } catch (Exception e) {
                    android.util.Log.e("MergedFragment", "CSV load error: " + e.getMessage());
                    e.printStackTrace();
                    android.widget.Toast.makeText(getContext(), "CSV load error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void loadRoundData() {
        android.util.Log.v("MergedFragment", "loadRoundData() called - loading from Fencing_backup.csv");
        
        // Load from RoundFragment's backup file to get original round data
        java.io.File filesDir = requireContext().getFilesDir();
        java.io.File roundBackupFile = new java.io.File(filesDir, "Fencing_backup.csv");
        
        if (!roundBackupFile.exists()) {
            android.util.Log.w("MergedFragment", "Fencing_backup.csv not found, cannot reload round data");
            android.widget.Toast.makeText(getContext(), "No Round data backup found", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(roundBackupFile));
            String headerLine = reader.readLine();
            if (headerLine == null) {
                reader.close();
                return;
            }
            
            String[] header = headerLine.split(",");
            // Detect nrPart from header (columns 2, 3, 4... are bout number columns)
            int nrPart = 0;
            for (int i = 2; i < header.length; i++) {
                if (header[i].trim().matches("\\d+")) nrPart++;
                else break;
            }
            android.util.Log.v("MergedFragment", "Detected nrPart=" + nrPart + " from Fencing_backup.csv");
            
            // Read all participant lines
            java.util.List<String[]> allTokens = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                allTokens.add(line.split(",", -1));
            }
            reader.close();
            
            // Parse bout results matrix
            int actualPart = Math.min(nrPart, allTokens.size());
            int[][] boutResults = new int[actualPart][actualPart];
            String[] participantNames = new String[actualPart];
            for (int i = 0; i < actualPart; i++) {
                for (int j = 0; j < actualPart; j++) {
                    boutResults[i][j] = -1;
                }
            }
            
            for (int i = 0; i < actualPart; i++) {
                String[] tokens = allTokens.get(i);
                if (tokens.length < 2) continue;
                participantNames[i] = tokens[1];
                
                // Parse bout columns
                for (int j = 0; j < actualPart && (2 + j) < tokens.length; j++) {
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
            
            // Build rows from parsed data
            rows.clear();
            for (int i = 0; i < actualPart; i++) {
                String name = participantNames[i] != null ? participantNames[i] : "";
                int victories = 0, given = 0, received = 0, boutsWon = 0, boutsLost = 0;
                for (int j = 0; j < actualPart; j++) {
                    if (i != j && boutResults[i][j] >= 0 && boutResults[j][i] >= 0 &&
                        participantNames[j] != null && !participantNames[j].isEmpty()) {
                        int s = boutResults[i][j];
                        int o = boutResults[j][i];
                        if (s > o) { victories++; boutsWon++; }
                        else if (s < o) { boutsLost++; }
                        given += s;
                        received += o;
                    }
                }
                int index = given - received;
                int percent = (boutsWon + boutsLost) > 0 ? (int) Math.round((double) boutsWon / (boutsWon + boutsLost) * 100) : 0;
                rows.add(new Row(i + 1, name, victories, given, received, index, percent, 0, null));
                android.util.Log.v("MergedFragment", "Row loaded from Round backup: Nr=" + (i + 1) + ", Name='" + name + "', V=" + victories);
            }
            
            calculateFinalPositions();
            // Assign P = FinalPos for all rows after ranking
            for (Row r : rows) {
                r.p = (r.finalPos != null) ? r.finalPos : 0;
            }
            
            useCsvOnly = false;
            backupMergedMatrix();
            renderRows();
            android.widget.Toast.makeText(getContext(), "Reloaded " + rows.size() + " participants from Round", android.widget.Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            android.util.Log.e("MergedFragment", "Error loading Fencing_backup.csv: " + e.getMessage());
            android.widget.Toast.makeText(getContext(), "Reload failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    // Helper: set button background with rounded corners
    private void setRoundedBackground(Button button, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(8 * getResources().getDisplayMetrics().density); // 8dp corners
        button.setBackground(drawable);
    }

    private void renderRows() {
        android.util.Log.v("MergedFragment", "renderRows() called");
        StringBuilder sb = new StringBuilder();
        sb.append("MergedFragment renderRows: Table state\n");
        sb.append("Rows count: ").append(rows.size()).append("\n");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            sb.append("Row ").append(i+1).append(": Nr=").append(r.nr)
                .append(", Name=").append(r.name)
                .append(", V=").append(r.victories)
                .append(", →=").append(r.given)
                .append(", ←=").append(r.received)
                .append(", I=").append(r.index)
                .append(", %=").append(r.percent)
                .append(", P=").append(r.p)
                .append(", FinalPos=").append(r.finalPos).append("\n");
        }
        android.util.Log.v("MergedFragment", sb.toString());
        tableLayout.removeAllViews();
        tableLayoutRight.removeAllViews();
        // Ensure at least 1 row always exists (empty participant with zero fields)
        boolean hadRealData = rows != null && rows.size() > 0;
        if (rows == null || rows.size() == 0) {
            rows = new java.util.ArrayList<>();
            rows.add(new Row(1, "", 0, 0, 0, 0, 0, 0, null));
            android.util.Log.i("MergedFragment", "renderRows: No data, added empty participant row.");
        }
        // Only backup if we have real data (not just placeholder empty row)
        if (hadRealData) {
            backupMergedMatrix();
        }
        // Use dynamic color index from ViewModel (shared with RoundFragment)
        ScoresViewModel scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
        int colorIdx = scoresViewModel.getColorCycleIndex().getValue() != null ? scoresViewModel.getColorCycleIndex().getValue() : 0;
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
            } else if (col >= 2 && col <= 6) {
                tv.setBackground(makeBorderedCell(pair[0]));
            } else if (col == 7) {
                tv.setBackground(makeBorderedCell(pair[1]));
            } else if (col == 8) { // FinalPos header styled as P
                tv.setBackground(makeBorderedCell(pair[1]));
            } else {
                tv.setBackground(makeBorderedCell(Color.WHITE));
            }
            // Add long-press to all headers except P and FinalPos to navigate to KO page
            if (!h.equals("P") && !h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    navigateToNextPage();
                    return true;
                });
            }
            // Add long-press to P header to sort by P (position)
            if (h.equals("P")) {
                tv.setOnLongClickListener(v -> {
                    android.util.Log.d("MergedFragment", "Header long-pressed: P (column " + colIdx + ") - sorting by P");
                    android.widget.Toast.makeText(getContext(), "Sorting by P", android.widget.Toast.LENGTH_SHORT).show();
                    sortRowsByP();
                    renderRows();
                    return true;
                });
            }
            // Add long-press to FinalPos header to sort and log verbose debug
            if (h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    android.util.Log.d("MergedFragment", "Header long-pressed: FinalPos (column " + colIdx + ")");
                    StringBuilder sb2 = new StringBuilder();
                    sb2.append("MergedFragment header long-press: FinalPos\n");
                    sb2.append("Rows count: ").append(rows.size()).append("\n");
                    for (int i = 0; i < rows.size(); i++) {
                        Row r = rows.get(i);
                        sb2.append("Row ").append(i+1).append(": Nr=").append(r.nr)
                          .append(", Name=").append(r.name)
                          .append(", V=").append(r.victories)
                          .append(", →=").append(r.given)
                          .append(", ←=").append(r.received)
                          .append(", I=").append(r.index)
                          .append(", %=").append(r.percent)
                          .append(", P=").append(r.p)
                          .append(", FinalPos=").append(r.finalPos).append("\n");
                    }
                    android.util.Log.i("MergedFragment", sb2.toString());
                    sortRowsByFinalPos();
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
                    case 2: value = String.valueOf(row.victories); break;
                    case 3: value = String.valueOf(row.given); break;
                    case 4: value = String.valueOf(row.received); break;
                    case 5: value = String.valueOf(row.index); break;
                    case 6: value = String.valueOf(row.percent); break;
                    case 7: value = String.valueOf(row.p); break;
                    case 8: value = row.finalPos != null ? String.valueOf(row.finalPos) : ""; break;
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
                // FinalPos cells (col == 8): long press sorts by FinalPos
                if (colIdx == 8) {
                    cell.setBackground(makeBorderedCell(pair[1])); // Same color as FinalPos header
                    cell.setOnLongClickListener(v -> {
                        android.util.Log.d("MergedFragment", "FinalPos cell long-pressed: row=" + (rowIdx+1) + " - sorting by FinalPos");
                        android.widget.Toast.makeText(getContext(), "Sorting by FinalPos", android.widget.Toast.LENGTH_SHORT).show();
                        sortRowsByFinalPos();
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
                                android.util.Log.i("MergedFragment", "Removed last participant, now " + rows.size() + " rows");
                                calculateFinalPositions();
                                backupMergedMatrix();
                                renderRows();
                            }
                        } else {
                            // Cells floor(N/2)+1 to N: ADD a new participant
                            int newNr = rows.size() + 1;
                            rows.add(new Row(newNr, "", 0, 0, 0, 0, 0, 0, null));
                            android.util.Log.i("MergedFragment", "Added participant, now " + rows.size() + " rows");
                            backupMergedMatrix();
                            renderRows();
                        }
                        return true;
                    });
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
                                    renderRows();
                                    return;
                                }
                                break;
                            case 2:
                                try { row.victories = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 3:
                                try { row.given = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 4:
                                try { row.received = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 5:
                                try { row.index = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 6:
                                try { row.percent = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 7:
                                try { row.p = Integer.parseInt(newVal); } catch (Exception ex) {}
                                break;
                            case 8:
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
            } else if (col >= 2 && col <= 6) {
                tv.setBackground(makeBorderedCell(pair[0]));
            } else if (col == 7) {
                tv.setBackground(makeBorderedCell(pair[1]));
            } else if (col == 8) {
                tv.setBackground(makeBorderedCell(pair[1]));
            } else {
                tv.setBackground(makeBorderedCell(Color.WHITE));
            }
            // Add navigation long press for right table headers too
            if (!h.equals("P") && !h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    navigateToNextPage();
                    return true;
                });
            }
            if (h.equals("P")) {
                tv.setOnLongClickListener(v -> {
                    sortRowsByP();
                    renderRows();
                    return true;
                });
            }
            if (h.equals("FinalPos")) {
                tv.setOnLongClickListener(v -> {
                    sortRowsByFinalPos();
                    renderRows();
                    return true;
                });
            }
            headerRow.addView(tv);
        }
        return headerRow;
    }

    // Sorts the rows by P (position) in ascending order
    private void sortRowsByP() {
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return Integer.compare(a.p, b.p);
            }
        });
    }

    // Sorts the rows by FinalPos in ascending order
    private void sortRowsByFinalPos() {
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                if (a.finalPos == null && b.finalPos == null) return 0;
                if (a.finalPos == null) return 1;
                if (b.finalPos == null) return -1;
                return Integer.compare(a.finalPos, b.finalPos);
            }
        });
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
                        case 2: // Victories
                            try { row.victories = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 3: // Given
                            try { row.given = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 4: // Received
                            try { row.received = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 5: // Index
                            try { row.index = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 6: // Percent
                            try { row.percent = Integer.parseInt(newVal); } catch (Exception ex) {}
                            break;
                        case 7: // P
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
        // Sort by percent DESC, then index DESC, then given DESC
        java.util.Collections.sort(toRank, new java.util.Comparator<Row>() {
            public int compare(Row a, Row b) {
                int cmp = Integer.compare(b.percent, a.percent);
                if (cmp != 0) return cmp; // DESC: higher percent first
                cmp = Integer.compare(b.index, a.index);
                if (cmp != 0) return cmp; // DESC: higher index first
                return Integer.compare(b.given, a.given); // DESC: higher given first
            }
        });
        int pos = 1;
        for (int i = 0; i < toRank.size(); ) {
            int j = i + 1;
            while (j < toRank.size() &&
                   toRank.get(j).percent == toRank.get(i).percent &&
                   toRank.get(j).index == toRank.get(i).index &&
                   toRank.get(j).given == toRank.get(i).given) j++;
            for (int k = i; k < j; k++) {
                toRank.get(k).finalPos = pos;
            }
            pos += (j - i);
            i = j;
        }
        // Set all 0 FinalPos to null for consistency, and sync P with FinalPos
        for (Row r : rows) {
            if (r.finalPos != null && r.finalPos == 0) r.finalPos = null;
            // Keep P in sync with FinalPos
            r.p = (r.finalPos != null) ? r.finalPos : 0;
        }
    }
    
    // ===================== QR CODE METHODS =====================
    
    // Generate CSV data from current rows
    private String generateCsvData() {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("Nr,Name,V,→,←,I,%,P,FinalPos\n");
        // Data rows
        for (Row r : rows) {
            sb.append(r.nr).append(",");
            sb.append(r.name != null ? r.name.replace(",", ";") : "").append(",");
            sb.append(r.victories).append(",");
            sb.append(r.given).append(",");
            sb.append(r.received).append(",");
            sb.append(r.index).append(",");
            sb.append(r.percent).append(",");
            sb.append(r.p).append(",");
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
        
        ImageView imageView = new ImageView(requireContext());
        imageView.setImageBitmap(qrBitmap);
        imageView.setBackgroundColor(0xFFFFFFFF);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(imageView);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.show();
        
        android.util.Log.i("MergedFragment", "QR OUT: " + qrSize + "px (" + moduleCount + " modules, " + 
            (qrSize/moduleCount) + "px/module), " + compressed.length() + " bytes");
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
        android.util.Log.i("MergedFragment", "QR ADD: Received " + scannedData.length() + " bytes");
        
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
            
            // Count how many new rows we add
            int addedCount = 0;
            
            // Skip header, parse data rows and add to existing
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                
                String[] cols = line.split(",", -1);
                if (cols.length < 8) continue;
                
                int nr = rows.size() + 1; // Assign new Nr based on current table size
                String name = cols[1].replace(";", ",");
                int victories = parseInt(cols[2], 0);
                int given = parseInt(cols[3], 0);
                int received = parseInt(cols[4], 0);
                int index = parseInt(cols[5], 0);
                int percent = parseInt(cols[6], 0);
                int p = parseInt(cols[7], 0);
                Integer finalPos = cols.length > 8 && !cols[8].isEmpty() ? parseInt(cols[8], 0) : null;
                
                rows.add(new Row(nr, name, victories, given, received, index, percent, p, finalPos));
                addedCount++;
            }
            
            // Recalculate FinalPos for combined data
            calculateFinalPositions();
            
            // Re-render table
            renderRows();
            backupMergedMatrix();
            
            android.widget.Toast.makeText(getContext(), "Added " + addedCount + " participants (total: " + rows.size() + ")", android.widget.Toast.LENGTH_SHORT).show();
            android.util.Log.i("MergedFragment", "QR ADD: Successfully added " + addedCount + " rows, total now " + rows.size());
            
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
