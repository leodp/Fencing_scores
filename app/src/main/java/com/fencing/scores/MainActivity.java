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

package com.fencing.scores;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.content.res.Configuration;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.util.DisplayMetrics;
import java.util.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.content.ContentResolver;
import java.util.HashMap;
import java.util.Map;
import androidx.viewpager2.widget.ViewPager2;
import java.util.List;
import java.util.ArrayList;
import android.provider.MediaStore;
import android.content.ContentValues;
import java.io.OutputStreamWriter;
import android.provider.Settings;
import android.os.Build;

public class MainActivity extends AppCompatActivity {
        // Flag to indicate if a crash was detected on startup
        public static boolean crashDetected = false;
        private ViewPager2 viewPager;

        public ViewPager2 getViewPager() {
            return viewPager;
        }
    // Helper to create a solid background with a single black border
    private android.graphics.drawable.Drawable makeCellBorder(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        /////drawable.setStroke(dpToPx(2), android.graphics.Color.BLACK); // 2dp black border
        return drawable;
    }
        // Generates the CSV string for export
        private String generateCSV() {
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
                for (int j = 0; j < nrPart; j++) {
                    sb.append(",");
                    if (i == j) {
                        sb.append("");
                    } else if (boutResults[i][j] >= 0) {
                        sb.append(boutResults[i][j]);
                    } else {
                        sb.append("");
                    }
                }
                // Results columns
                sb.append(",").append(calculateVictories(i));
                sb.append(",").append(calculateGiven(i));
                sb.append(",").append(calculateReceived(i));
                sb.append(",").append(calculateIndex(i));
                sb.append(",").append(calculatePercent(i));
                sb.append(",").append(calculatePosition(i));
                sb.append("\n");
            }
            return sb.toString();
        }
    // Cycles the color scheme for result columns
    private void cycleResultColors() {
        colorCycleIndex = (colorCycleIndex + 1) % resultColorPairs.length;
        saveColorConfig();
    }

    // Exports the current matrix to CSV (auto-export)
    private void autoExportToCSV() {
        // Use the same logic as exportToExcel, but with a default file name and silent export
        try {
            File documentsDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "");
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }
            File exportFile = new File(documentsDir, "Fencing_auto_export.csv");
            FileWriter writer = new FileWriter(exportFile, false);
            writeBackupContent(writer);
            writer.close();
            // Optionally, show a toast or log
            // Toast.makeText(this, "Auto-exported to Fencing_auto_export.csv", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // Optionally, show a toast or log
            // Toast.makeText(this, "Auto-export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

        // ...existing code...


        private int getPositionForSort(int idx) {
            try {
                return Integer.parseInt(calculatePosition(idx));
            } catch (Exception e) {
                return 9999;
            }
        }
    // Color cycling for result columns (cyan/green, #87CEFA/#B0C4DE, ...)
    private final int[][] resultColorPairs = {
        {0xFFFFD700, 0xFFFF7F50}, // Gold/Coral (new default)
        {0xFF00FFFF, 0xFF00FF00},
        {0xFF87CEFA, 0xFFB0C4DE},
        {0xFFFFFFE0, 0xFFF0E68C},
        {0xFF98FB98, 0xFF9ACD32},
        {0xFFA9A9A9, 0xFFDCDCDC}
    };
    private int colorCycleIndex = 0; // Ensure default is gold/coral
    private static final int CREATE_FILE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_REQUEST = 100;
    
    private boolean isAppInitialized = false; // Track if app has been initialized
    private int nrPart = 10;
    private final int MBT = 16;
    // private TableLayout tableLayout; // moved to RoundFragment
    private String[] participantNames;
    private int[][] boutResults;
    private Map<String, Integer> participantMap = new HashMap<>();
    private Map<Integer, String> boutOrderMap = new HashMap<>();
    private List<String> partSeq = new ArrayList<>();
    
    // Config file name for color scheme
    private static final String CONFIG_FILE = "fencing_config.json";

    // Save colorCycleIndex to config file
    private void saveColorConfig() {
        try {
            File configFile = new File(getFilesDir(), CONFIG_FILE);
            java.io.FileWriter writer = new java.io.FileWriter(configFile, false);
            writer.write("{\"colorCycleIndex\":" + colorCycleIndex + "}");
            writer.close();
        } catch (Exception e) {
            // Ignore errors
        }
    }

    // Load colorCycleIndex from config file
    private void loadColorConfig() {
        try {
            File configFile = new File(getFilesDir(), CONFIG_FILE);
            if (configFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(configFile));
                String line = reader.readLine();
                reader.close();
                if (line != null && line.contains("colorCycleIndex")) {
                    int idx = line.indexOf(":");
                    int end = line.indexOf("}", idx);
                    if (idx > 0 && end > idx) {
                        String val = line.substring(idx + 1, end).replaceAll("[^0-9]", "");
                        if (!val.isEmpty()) colorCycleIndex = Integer.parseInt(val) % resultColorPairs.length;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }

    // Crash file check and handling (only checks, creation is in onStart)
    private void checkAndHandleCrashFile() {
        try {
            File documentsDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "");
            if (!documentsDir.exists()) documentsDir.mkdirs();
            File crashFile = new File(documentsDir, "CRASH.txt");
            android.util.Log.d("MainActivity", "checkAndHandleCrashFile: file exists=" + crashFile.exists() + ", path=" + crashFile.getAbsolutePath());
            if (crashFile.exists()) {
                // Show warning and delete file - this means app crashed last time
                Toast.makeText(this, "App crashed last time", Toast.LENGTH_LONG).show();
                crashDetected = true;
                crashFile.delete();
                android.util.Log.d("MainActivity", "Crash detected! Set crashDetected=true");
            } else {
                android.util.Log.d("MainActivity", "No crash file found, clean start");
            }
            // Note: CRASH.txt is created in onStart() and deleted in onStop()
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error in checkAndHandleCrashFile: " + e.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force light mode - app uses white backgrounds and has no dark mode resources
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        // Reset crash flag before checking (static variable may persist across activity recreations)
        crashDetected = false;
        // Crash file check
        checkAndHandleCrashFile();
        // Load color scheme from config file if present
        loadColorConfig();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hide action bar for full screen
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new com.fencing.scores.ui.MainPagerAdapter(this));
        viewPager.setOffscreenPageLimit(1);  // Minimum - only adjacent pages kept
        
        // Enable ViewPager2 native swipe (works better with scrollable content)
        viewPager.setUserInputEnabled(true);
        
        // Circular navigation: wrap around at edges (Final -> Round, Round -> Final)
        // Use GestureDetector to detect flings at edge pages
        final int PAGE_COUNT = 4;
        final int LAST_PAGE = PAGE_COUNT - 1; // 3 = Final
        
        android.view.GestureDetector edgeFlingDetector = new android.view.GestureDetector(this, 
            new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, 
                                       float velocityX, float velocityY) {
                    if (e1 == null || e2 == null) return false;
                    
                    int currentPage = viewPager.getCurrentItem();
                    float diffX = e2.getX() - e1.getX();
                    float threshold = 100; // minimum swipe distance
                    float velocityThreshold = 100; // minimum velocity
                    
                    // On Final (page 3), swipe left (forward) -> wrap to Round (page 0)
                    if (currentPage == LAST_PAGE && diffX < -threshold && Math.abs(velocityX) > velocityThreshold) {
                        viewPager.setCurrentItem(0, true);
                        return true;
                    }
                    // On Round (page 0), swipe right (backward) -> wrap to Final (page 3)
                    if (currentPage == 0 && diffX > threshold && Math.abs(velocityX) > velocityThreshold) {
                        viewPager.setCurrentItem(LAST_PAGE, true);
                        return true;
                    }
                    return false;
                }
            });
        
        // Attach gesture detector to ViewPager2's internal RecyclerView
        View recyclerView = viewPager.getChildAt(0);
        if (recyclerView != null) {
            recyclerView.setOnTouchListener((v, event) -> {
                edgeFlingDetector.onTouchEvent(event);
                return false; // Don't consume - let ViewPager2 handle normally
            });
        }
        
        // All pages use landscape orientation - no orientation changes needed
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // Matrix UI is now handled in RoundFragment

        // Automatically restore from backup if a crash was detected
        // if (crashDetected) {
        //     restoreFromDefaultBackup();
        // } // Disabled: app should be idle at startup
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Create CRASH.txt when app becomes visible
        // If app crashes, this file will remain and signal crash on next start
        createCrashFile();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Delete CRASH.txt when app goes to background or is closed
        // If onStop() completes normally, app exited cleanly
        android.util.Log.d("MainActivity", "onStop: deleting crash file");
        deleteCrashFile();
    }

    @Override
    protected void onDestroy() {
        // Also delete CRASH.txt on destroy for safety
        android.util.Log.d("MainActivity", "onDestroy: deleting crash file");
        deleteCrashFile();
        super.onDestroy();
    }
    
    // Switch to page - orientation is handled by onPageSelected callback
    private void switchToPage(int page) {
        // Switch page (smooth scroll = false for instant transition)
        viewPager.setCurrentItem(page, false);
    }
    
    // Public method for fragments to navigate to a specific page
    public void navigateToPage(int page) {
        switchToPage(page);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle orientation change without recreating activity
        // Fragments and ViewPager2 remain intact
        android.util.Log.d("MainActivity", "Configuration changed, orientation: " + 
            (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait"));
    }
    
    private void createCrashFile() {
        try {
            File documentsDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "");
            if (!documentsDir.exists()) documentsDir.mkdirs();
            File crashFile = new File(documentsDir, "CRASH.txt");
            FileWriter writer = new FileWriter(crashFile, false);
            writer.write(new java.util.Date().toString());
            writer.close();
            android.util.Log.d("MainActivity", "Created CRASH.txt");
        } catch (Exception e) {
            // Ignore errors
        }
    }
    
    private void deleteCrashFile() {
        try {
            File documentsDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "");
            File crashFile = new File(documentsDir, "CRASH.txt");
            boolean existed = crashFile.exists();
            boolean deleted = false;
            if (existed) {
                deleted = crashFile.delete();
            }
            android.util.Log.d("MainActivity", "deleteCrashFile: path=" + crashFile.getAbsolutePath() + ", existed=" + existed + ", deleted=" + deleted);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "deleteCrashFile ERROR: " + e.getMessage());
        }
    }
    
    // Matrix UI logic moved to RoundFragment
    
    // Matrix UI logic moved to RoundFragment
    
    private TableRow createHeaderRow() {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT));
        
        // Add header cells
        String[] headers = new String[nrPart + 8]; // Nr, Name, 1-nrPart, V, ->, <-, I, %, P
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
            TextView cell;
            int[] colors = resultColorPairs[colorCycleIndex % resultColorPairs.length];
            if (i == 0) { // Nr column header
                cell = createHeaderCell(headers[i]);
                cell.setBackground(makeCellBorder(0xFFD5D5D5));
                cell.setOnClickListener(v -> exportResultsToUserFolder());
            } else if (i == 1) { // Name header
                cell = createHeaderCell(headers[i]);
                cell.setBackgroundColor(0xFFD5D5D5);
                cell.setOnClickListener(v -> exportResultsToUserFolder());
            } else if (i >= 2 && i < nrPart + 2) { // Participant headers (numbered columns)
                cell = createHeaderCell(headers[i]);
                cell.setBackground(makeCellBorder(0xFFD5D5D5));
                cell.setOnClickListener(v -> exportResultsToUserFolder());
            } else if (i == headers.length - 1) { // P column header
                cell = createHeaderCell(headers[i]);
                // Set P header color to match the second color in the cycle
                cell.setBackgroundColor(colors[1]);
                // Add long press to sort by rank
                /////cell.setOnLongClickListener(v -> {
                  /////  sortByRank();
                    // createMatrix();
                    //////return true;
                /////}
                // //////);
            } else if (i >= nrPart + 2 && i < headers.length - 1) { // Results headers
                cell = createHeaderCell(headers[i]);
                cell.setBackgroundColor(colors[0]);
                cell.setOnClickListener(v -> {
                    cycleResultColors();
                    // createMatrix();
                });
            } else {
                cell = createHeaderCell(headers[i]);
                cell.setOnClickListener(v -> exportResultsToUserFolder());
            }
            row.addView(cell);
        }
        
        return row;
    }

    // Launches a file picker to let the user save Results.csv to a folder of their choice
    private void exportResultsToUserFolder() {
        // Save Results.csv directly to Documents folder, no file picker
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Toast.makeText(this, "Cannot access storage", Toast.LENGTH_SHORT).show();
            return;
        }
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!documentsDir.exists()) {
            documentsDir.mkdirs();
        }
        // Find a unique filename if Results.csv exists, using 3-digit numbering
        File outFile = new File(documentsDir, "Results.csv");
        int fileIndex = 1;
        while (outFile.exists()) {
            outFile = new File(documentsDir, String.format("Results_%03d.csv", fileIndex));
            fileIndex++;
        }
        try (FileWriter writer = new FileWriter(outFile)) {
            writer.write(generateCSV());
            writer.flush();
            Toast.makeText(this, outFile.getName() + " saved to Documents", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save " + outFile.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    // Launches a file picker to let the user save Results.csv to a folder of their choice (from Help menu)
    private void saveResultsToUserFolder() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "Results.csv");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                        android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"));
            } catch (Exception e) { }
        }
        startActivityForResult(intent, 1002); // 1002 = Save Results (Help)
    }
    
    private TextView createHeaderCell(String text) {
        TextView cell = new TextView(this);
        cell.setText(text);
        cell.setTextColor(Color.BLACK); // Black text for header
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, calculateTextSize());
        cell.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        // Add shadow to make text appear thicker/more prominent
        cell.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK);
        cell.setBackgroundResource(R.drawable.cell_border);
        cell.setPadding(8, 8, 8, 8);
        cell.setGravity(android.view.Gravity.CENTER);
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            calculateCellHeight());
        params.weight = 1; // Restore weight for equal column sizing
        cell.setLayoutParams(params);
        return cell;
    }
    
    private TableRow createParticipantRow(final int participantIndex) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT));
        
        // Nr column
        TextView nrCell = createDataCell(String.valueOf(participantIndex + 1), true);
        nrCell.setBackgroundColor(0xFFD5D5D5); // participant number column color
        nrCell.setOnClickListener(v -> handleNrClick(participantIndex));
        row.addView(nrCell);

        // Name column (should be white)
        TextView nameCell = createNameCell(participantIndex);
        /////nameCell.setBackgroundColor(Color.WHITE);
        row.addView(nameCell);

        // Participant columns (bout results)
        for (int j = 0; j < nrPart; j++) {
            TextView boutCell = createBoutCell(participantIndex, j);
            boutCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(calculateTextSize() * 1.3)); // 30% larger
            // Set all bout result cells (except diagonal) to white
            if (participantIndex != j) {
        /////        boutCell.setBackgroundColor(Color.WHITE);
            }
            row.addView(boutCell);
        }

        // Result columns
        int[] colors = resultColorPairs[colorCycleIndex % resultColorPairs.length];
        TextView victoriesCell = createResultCell(calculateVictories(participantIndex), colors[0]);
        victoriesCell.setBackgroundColor(colors[0]);
        victoriesCell.setOnClickListener(v -> {
            cycleResultColors();
            // createMatrix();
        });
        row.addView(victoriesCell);
        TextView givenCell = createResultCell(calculateGiven(participantIndex), colors[0]);
        givenCell.setBackgroundColor(colors[0]);
        givenCell.setOnClickListener(v -> {
            cycleResultColors();
            // createMatrix();
        });
        row.addView(givenCell);
        TextView receivedCell = createResultCell(calculateReceived(participantIndex), colors[0]);
        receivedCell.setBackgroundColor(colors[0]);
        receivedCell.setOnClickListener(v -> {
            cycleResultColors();
            // createMatrix();
        });
        row.addView(receivedCell);
        TextView indexCell = createResultCell(calculateIndex(participantIndex), colors[0]);
        indexCell.setBackgroundColor(colors[0]);
        indexCell.setOnClickListener(v -> {
            cycleResultColors();
            // createMatrix();
        });
        row.addView(indexCell);
        TextView percentCell = createResultCell(calculatePercent(participantIndex), colors[0]);
        percentCell.setBackgroundColor(colors[0]);
        percentCell.setOnClickListener(v -> {
            cycleResultColors();
            // createMatrix();
        });
        row.addView(percentCell);
        TextView positionCell = createPositionCell(calculatePosition(participantIndex), participantIndex);
        positionCell.setBackgroundColor(colors[1]);
        row.addView(positionCell);
        return row;
    }
    
    private TextView createDataCell(String text, boolean bold) {
        TextView cell = new TextView(this);
        cell.setText(text);
        // Use black text for bold elements, blue for regular elements
        cell.setTextColor(bold ? Color.BLACK : Color.BLUE);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, calculateTextSize());
        // Apply bold+italic when explicitly requested (for headers, participant numbers, names, results)
        if (bold) {
            cell.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
            // Add shadow to make text appear thicker/more prominent
            cell.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK);
        } else {
            cell.setTypeface(android.graphics.Typeface.DEFAULT);
        }
        cell.setBackgroundResource(R.drawable.cell_border);
        cell.setPadding(8, 8, 8, 8);
        /////cell.setGravity(android.view.Gravity.CENTER);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            calculateCellHeight());
        params.weight = 1; // Restore weight for equal column sizing
        cell.setLayoutParams(params);
        
        return cell;
    }
    
    private int calculateCellHeight() {
        // Use actual pixel dimensions for accurate screen size evaluation
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenHeightPixels = displayMetrics.heightPixels;
        int screenWidthPixels = displayMetrics.widthPixels;
        
        // Get actual status bar height in pixels
        int statusBarHeightPixels = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeightPixels = getResources().getDimensionPixelSize(resourceId);
        }
        
        // Calculate usable height using 95% of available screen height in pixels
        int usableHeightPixels = (int) ((screenHeightPixels - statusBarHeightPixels) * 0.95);
        
        int totalRows = nrPart + 1; // +1 for header row
        int totalColumns = nrPart + 7; // Nr, Name, participants 1-nrPart, V, ->, <-, I, P
        
        // Calculate height based on pixel-perfect 95% screen usage
        int heightBasedCellHeightPixels = usableHeightPixels / totalRows;
        
        // Calculate width-based cell height in pixels (for square cells)
        int usableWidthPixels = (int) (screenWidthPixels * 0.98);
        int widthBasedCellHeightPixels = usableWidthPixels / totalColumns;
        
        // Choose the smaller value to ensure fit, preferring square cells
        int calculatedHeightPixels = Math.min(heightBasedCellHeightPixels, widthBasedCellHeightPixels);
        
        // Ensure minimum readability in pixels
        int minHeightPixels = (int) (20 * displayMetrics.density); // 20dp in pixels
        calculatedHeightPixels = Math.max(calculatedHeightPixels, minHeightPixels);
        
        // Final verification that total height doesn't exceed 95% of usable screen
        int totalRequiredHeightPixels = calculatedHeightPixels * totalRows;
        if (totalRequiredHeightPixels > usableHeightPixels) {
            calculatedHeightPixels = usableHeightPixels / totalRows;
        }
        
        // Return pixel height with 15% increase, ensuring absolute minimum
        int absoluteMinPixels = (int) (18 * displayMetrics.density); // 18dp in pixels
        int finalHeight = Math.max(calculatedHeightPixels, absoluteMinPixels);
        
        // Increase cell heights by 15% as requested (debugging effective height on actual Redmi Note 13 phone)
        return (int) (finalHeight * 1.15);
    }
    
    private TextView createNameCell(final int participantIndex) {
        TextView cell = new TextView(this);
        cell.setText(participantNames[participantIndex]);
        cell.setTextColor(Color.BLACK); // Black text for bold name column
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, calculateTextSize());
        cell.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC); // Make bold and italic
        // Add shadow to make text appear thicker/more prominent
        cell.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK);
        cell.setBackgroundResource(R.drawable.cell_border);
        cell.setPadding(8, 8, 8, 8);
        //cell.setGravity(android.view.Gravity.CENTER);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            dpToPx(100), // 20 character width as per .md requirements
            calculateCellHeight());
        cell.setLayoutParams(params);
        
        cell.setOnClickListener(v -> showNameEditDialog(participantIndex));
        
        return cell;
    }
    
    private TextView createBoutCell(final int row, final int col) {
        TextView cell = new TextView(this);
        
        if (row == col) {
            // Diagonal cell - just black background, no text
            cell.setText("");
            cell.setBackgroundColor(Color.BLACK);
        } else {
            // Regular bout cell - should have PLAIN text (not bold)
            String text = "";
            int textColor = Color.BLUE;
            
            if (boutResults[row][col] != -1) {
                text = String.valueOf(boutResults[row][col]);
                // Red if this participant lost
                if (boutResults[row][col] < boutResults[col][row]) {
                    textColor = Color.RED;
                }
            }
            
            cell.setText(text);
            cell.setTextColor(textColor);
            // Bout matrix cells should be PLAIN text (not bold)
            cell.setTypeface(android.graphics.Typeface.DEFAULT);
            cell.setBackgroundResource(R.drawable.cell_border);
            
            cell.setOnClickListener(v -> showBoutResultDialog(row, col));
        }
        
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, calculateTextSize());
        cell.setPadding(8, 8, 8, 8);
        cell.setGravity(android.view.Gravity.CENTER);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            calculateCellHeight());
        params.weight = 1; // Restore weight for equal column sizing
        cell.setLayoutParams(params);
        
        return cell;
    }
    
    private void handleNrClick(int participantIndex) {
        if (participantIndex == nrPart - 1) {
            // Increase participants when clicking last row
            nrPart++;
            String[] newNames = new String[nrPart];
            int[][] newResults = new int[nrPart][nrPart];
            
            System.arraycopy(participantNames, 0, newNames, 0, participantNames.length);
            newNames[nrPart - 1] = "";
            
            for (int i = 0; i < participantNames.length; i++) {
                System.arraycopy(boutResults[i], 0, newResults[i], 0, boutResults[i].length);
            }
            for (int i = 0; i < nrPart; i++) {
                for (int j = participantNames.length; j < nrPart; j++) {
                    newResults[i][j] = -1;
                }
            }
            for (int j = 0; j < nrPart; j++) {
                newResults[nrPart - 1][j] = -1;
            }
            
            participantNames = newNames;
            boutResults = newResults;
            // createMatrix();
        } else if (participantIndex == nrPart - 2 && nrPart > 2) {
            // Decrease participants when clicking second-to-last row
            nrPart--;
            String[] newNames = new String[nrPart];
            int[][] newResults = new int[nrPart][nrPart];
            
            System.arraycopy(participantNames, 0, newNames, 0, nrPart);
            for (int i = 0; i < nrPart; i++) {
                System.arraycopy(boutResults[i], 0, newResults[i], 0, nrPart);
            }
            
            participantNames = newNames;
            boutResults = newResults;
            // createMatrix();
        } else if (participantIndex >= 0 && participantIndex <= nrPart - 3) {
            // Show next bout for positions 1 to NrPart-2 (0 to NrPart-3 in 0-based indexing)
            showNextBoutPopup();
        }
    }
    
    private void showNameEditDialog(final int participantIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Participant Name");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(participantNames[participantIndex]);
        input.selectAll(); // Pre-select text for speed
        builder.setView(input);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            participantNames[participantIndex] = newName;
            
            // If name is empty, clear all bout results for this participant
            if (newName.isEmpty()) {
                clearParticipantBouts(participantIndex);
            }
            
            updatePartSeq(); // Update PartSeq when names are edited
            // createMatrix();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        AlertDialog dialog = builder.create();
        
        // Show dialog first
        dialog.show();
        
        // Force keyboard to appear and pre-select text
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        input.post(() -> {
            input.requestFocus();
            input.selectAll();
            // Force show keyboard
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        // Auto-confirm on Enter
        input.setOnEditorActionListener((v, actionId, event) -> {
            String newName = input.getText().toString().trim();
            participantNames[participantIndex] = newName;
            
            // If name is empty, clear all bout results for this participant
            if (newName.isEmpty()) {
                clearParticipantBouts(participantIndex);
            }
            
            updatePartSeq(); // Update PartSeq when names are edited
            // createMatrix();
            dialog.dismiss();
            return true;
        });
    }
    
    private void showBoutResultDialog(final int participant1, final int participant2) {
        // Only show popup if both participants have non-empty names
        if (participantNames[participant1].trim().isEmpty() || participantNames[participant2].trim().isEmpty()) {
            return; // Don't show popup for empty names
        }
        
        // First popup for participant1 (NR)
        showFirstScoreSelectionPopup(participant1, participant2);
    }
    
    private void showFirstScoreSelectionPopup(final int participant1, final int participant2) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Title: "Name1      Vs Name2" (6 spaces as per requirements)
        String title = participantNames[participant1] + "      Vs " + participantNames[participant2];
        builder.setTitle(title);
        
        // Create 6x3 grid for score selection (0-16 + Reset = 18 items)
        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setRowCount(3);
        gridLayout.setColumnCount(6);
        gridLayout.setOrientation(GridLayout.HORIZONTAL); // Ensure left->right, top->down ordering
        gridLayout.setPadding(30, 30, 30, 30); // Increased padding for 20% wider
        
        // Add score buttons (0-16) FIRST as per requirements with explicit positioning
        for (int score = 0; score <= MBT; score++) {
            Button scoreButton = new Button(this);
            scoreButton.setText(String.valueOf(score));
            scoreButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); // Slightly larger
            scoreButton.setBackgroundColor(Color.LTGRAY);
            scoreButton.setPadding(8, 8, 8, 8); // More padding for wider buttons
            
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            // Calculate position: column-first ordering (0,1,2 in col 0; 3,4,5 in col 1; etc.)
            int col = score / 3; // Column position (0-5)
            int row = score % 3; // Row position (0-2)
            params.columnSpec = GridLayout.spec(col);
            params.rowSpec = GridLayout.spec(row);
            // Calculate optimal width: match width of widest score button (16)
            int buttonWidth = calculatePopupButtonWidth("16");
            params.width = buttonWidth;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(1, 1, 1, 1);
            scoreButton.setLayoutParams(params);

            final int finalScore = score;
            scoreButton.setOnClickListener(v -> {
                try {
                    // Set score for participant1 and show second popup
                    showSecondScoreSelectionPopup(participant1, participant2, finalScore);
                    ((AlertDialog) v.getTag()).dismiss();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error processing score: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            gridLayout.addView(scoreButton);
        }
        
        // Add Reset button LAST as per requirements (position 16 = col 4, row 2)
        Button resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); // Smaller text to fit in square
        resetButton.setBackgroundColor(Color.YELLOW);
        resetButton.setPadding(5, 5, 5, 5);
        GridLayout.LayoutParams resetParams = new GridLayout.LayoutParams();
        resetParams.columnSpec = GridLayout.spec(5); // Column 5 (6th column)
        resetParams.rowSpec = GridLayout.spec(1);    // Row 1 (2nd row)
        // Calculate optimal width: match width of widest score button (16)
        int buttonWidth = calculatePopupButtonWidth("16");
        resetParams.width = buttonWidth;
        resetParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
        resetParams.setMargins(1, 1, 1, 1);
        resetButton.setLayoutParams(resetParams);
        
        resetButton.setOnClickListener(v -> {
            // Clear both matrix values
            boutResults[participant1][participant2] = -1;
            boutResults[participant2][participant1] = -1;
            updateMatrix();
            ((AlertDialog) v.getTag()).dismiss();
        });
        gridLayout.addView(resetButton);
        
        builder.setView(gridLayout);
        AlertDialog dialog = builder.create();
        
        // Set dialog tag for all buttons (score buttons + reset button)
        for (int i = 0; i < gridLayout.getChildCount(); i++) {
            gridLayout.getChildAt(i).setTag(dialog);
        }
        
        // Make dialog window 20% wider
        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.6); // 20% wider than default
                dialog.getWindow().setAttributes(lp);
            }
        });
        
        dialog.show();
    }

    private void showSecondScoreSelectionPopup(final int participant1, final int participant2, final int score1) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Title: "Name2      Vs Name1" (6 spaces as per requirements, reversed order)
        String title = participantNames[participant2] + "      Vs " + participantNames[participant1];
        builder.setTitle(title);
        
        // Create 6x3 grid for score selection (0-16 + Reset = 18 items)
        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setRowCount(3);
        gridLayout.setColumnCount(6);
        gridLayout.setOrientation(GridLayout.HORIZONTAL); // Ensure left->right, top->down ordering
        gridLayout.setPadding(30, 30, 30, 30); // Increased padding for 20% wider
        
        // Add score buttons (0-16) FIRST as per requirements with explicit positioning
        for (int score = 0; score <= MBT; score++) {
            Button scoreButton = new Button(this);
            scoreButton.setText(String.valueOf(score));
            scoreButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); // Slightly larger
            scoreButton.setBackgroundColor(Color.LTGRAY);
            scoreButton.setPadding(8, 8, 8, 8); // More padding for wider buttons
            
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            // Calculate position: column-first ordering (0,1,2 in col 0; 3,4,5 in col 1; etc.)
            int col = score / 3; // Column position (0-5)
            int row = score % 3; // Row position (0-2)
            params.columnSpec = GridLayout.spec(col);
            params.rowSpec = GridLayout.spec(row);
            // Calculate optimal width: match width of widest score button (16)
            int buttonWidth = calculatePopupButtonWidth("16");
            params.width = buttonWidth;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(1, 1, 1, 1);
            scoreButton.setLayoutParams(params);

            final int finalScore = score;
            scoreButton.setOnClickListener(v -> {
                try {
                    // Set both scores and update matrix
                    if (score1 == finalScore) {
                        // Equal scores - clear both values
                        boutResults[participant1][participant2] = -1;
                        boutResults[participant2][participant1] = -1;
                    } else {
                        // Set scores
                        boutResults[participant1][participant2] = score1;
                        boutResults[participant2][participant1] = finalScore;
                    }
                    
                    // First dismiss dialog, then update matrix to prevent crashes
                    AlertDialog dialogToClose = (AlertDialog) v.getTag();
                    if (dialogToClose != null) {
                        dialogToClose.dismiss();
                    }
                    
                    updatePartSeq(); // Update PartSeq when bout scores are entered
                    updateMatrix();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error processing bout result: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            gridLayout.addView(scoreButton);
        }
        
        // Add Reset button LAST as per requirements (position 16 = col 4, row 2)
        Button resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); // Smaller text to fit in square
        resetButton.setBackgroundColor(Color.YELLOW);
        resetButton.setPadding(5, 5, 5, 5);
        GridLayout.LayoutParams resetParams = new GridLayout.LayoutParams();
        resetParams.columnSpec = GridLayout.spec(5); // Column 5 (6th column)
        resetParams.rowSpec = GridLayout.spec(1);    // Row 1 (2nd row)
        // Calculate optimal width: match width of widest score button (16)
        int buttonWidth = calculatePopupButtonWidth("16");
        resetParams.width = buttonWidth;
        resetParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
        resetParams.setMargins(1, 1, 1, 1);
        resetButton.setLayoutParams(resetParams);
        
        resetButton.setOnClickListener(v -> {
            try {
                // Clear both matrix values
                boutResults[participant1][participant2] = -1;
                boutResults[participant2][participant1] = -1;
                
                // First dismiss dialog, then update matrix
                AlertDialog dialogToClose = (AlertDialog) v.getTag();
                if (dialogToClose != null) {
                    dialogToClose.dismiss();
                }
                
                updatePartSeq(); // Update PartSeq when bout is reset
                updateMatrix();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Error resetting scores: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        gridLayout.addView(resetButton);
        
        builder.setView(gridLayout);
        AlertDialog dialog = builder.create();
        
        // Set dialog tag for all buttons (score buttons + reset button)
        for (int i = 0; i < gridLayout.getChildCount(); i++) {
            gridLayout.getChildAt(i).setTag(dialog);
        }
        
        // Make dialog window 20% wider
        dialog.setOnShowListener(dialogInterface -> {
            if (dialog.getWindow() != null) {
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.6); // 20% wider than default
                dialog.getWindow().setAttributes(lp);
            }
        });
        
        dialog.show();
    }
    
    private void updateMatrix() {
        // Ensure matrix update runs on UI thread to prevent freezing
        runOnUiThread(() -> {
            try {
                // createMatrix();
                
                // Step 5: Backup after Results Processing as specified in .md
                if (hasAnyResults()) {
                    backupData();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error updating matrix: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private String calculateVictories(int participantIndex) {
        // Return empty for participants with empty names
        if (participantNames[participantIndex].trim().isEmpty()) {
            return "";
        }
        if (!hasAnyResults()) return "";
        
        int victories = 0;
        for (int j = 0; j < nrPart; j++) {
            if (j != participantIndex && boutResults[participantIndex][j] != -1) {
                if (boutResults[participantIndex][j] > boutResults[j][participantIndex]) {
                    victories++;
                }
            }
        }
        return String.valueOf(victories);
    }
    
    private String calculateGiven(int participantIndex) {
        // Return empty for participants with empty names
        if (participantNames[participantIndex].trim().isEmpty()) {
            return "";
        }
        if (!hasAnyResults()) return "";
        
        int given = 0;
        for (int j = 0; j < nrPart; j++) {
            if (j != participantIndex && boutResults[participantIndex][j] != -1) {
                given += boutResults[participantIndex][j];
            }
        }
        return String.valueOf(given);
    }
    
    private String calculateReceived(int participantIndex) {
        // Return empty for participants with empty names
        if (participantNames[participantIndex].trim().isEmpty()) {
            return "";
        }
        if (!hasAnyResults()) return "";
        
        int received = 0;
        for (int i = 0; i < nrPart; i++) {
            if (i != participantIndex && boutResults[i][participantIndex] != -1) {
                received += boutResults[i][participantIndex];
            }
        }
        return String.valueOf(received);
    }
    
    private String calculateIndex(int participantIndex) {
        // Return empty for participants with empty names
        if (participantNames[participantIndex].trim().isEmpty()) {
            return "";
        }
        if (!hasAnyResults()) return "";
        
        try {
            int given = Integer.parseInt(calculateGiven(participantIndex));
            int received = Integer.parseInt(calculateReceived(participantIndex));
            return String.valueOf(given - received);
        } catch (NumberFormatException e) {
            return "";
        }
    }
    
    private String calculatePosition(int participantIndex) {
        if (!hasAnyResults() || participantNames[participantIndex].isEmpty()) return "";

        List<Integer> participants = new ArrayList<>();
        for (int i = 0; i < nrPart; i++) {
            if (!participantNames[i].isEmpty()) {
                participants.add(i);
            }
        }

        // Sort participants by ranking criteria
        participants.sort((p1, p2) -> {
            // Primary: Most victories (descending)
            int victories1 = Integer.parseInt(calculateVictories(p1));
            int victories2 = Integer.parseInt(calculateVictories(p2));
            if (victories1 != victories2) {
                return Integer.compare(victories2, victories1);
            }
            // Secondary: Highest index (descending)
            int index1 = Integer.parseInt(calculateIndex(p1));
            int index2 = Integer.parseInt(calculateIndex(p2));
            if (index1 != index2) {
                return Integer.compare(index2, index1);
            }
            // Tertiary: Most given points (descending)
            int given1 = Integer.parseInt(calculateGiven(p1));
            int given2 = Integer.parseInt(calculateGiven(p2));
            return Integer.compare(given2, given1);
        });

        // Assign positions: first is 1, last is N (no skipped ranks for ties)
        int[] positions = new int[participants.size()];
        int pos = 1;
        for (int i = 0; i < participants.size(); i++) {
            if (i > 0) {
                int prev = participants.get(i - 1);
                int curr = participants.get(i);
                boolean sameVictories = calculateVictories(curr).equals(calculateVictories(prev));
                boolean sameIndex = calculateIndex(curr).equals(calculateIndex(prev));
                boolean sameGiven = calculateGiven(curr).equals(calculateGiven(prev));
                if (!(sameVictories && sameIndex && sameGiven)) {
                    pos = i + 1;
                }
            }
            positions[i] = pos;
        }

        // Find this participant's position
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i) == participantIndex) {
                return String.valueOf(positions[i]);
            }
        }
        return "";
    }
    
    private boolean hasAnyResults() {
        for (int i = 0; i < nrPart; i++) {
            for (int j = 0; j < nrPart; j++) {
                if (boutResults[i][j] != -1) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private float calculateTextSize() {
        // Dynamically calculate text size based on number of participants
        int baseSize = 14;
        int reduction = Math.max(0, (nrPart - 10) * 2);
        return Math.max(8, baseSize - reduction);
    }
    
    private TextView createResultCell(String text, int backgroundColor) {
        TextView cell = new TextView(this);
        cell.setText(text);
        cell.setTextColor(Color.BLACK); // Black text for result columns
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, calculateTextSize());
        // Result data cells should be plain text (not bold)
        cell.setTypeface(android.graphics.Typeface.DEFAULT);
        // Set background color first, then apply border as overlay
        cell.setBackgroundColor(backgroundColor);
        // Create border as compound drawable overlay
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setStroke(4, Color.BLACK); // 2dp double border = 4px
        border.setColor(backgroundColor); // Maintain background color
        cell.setBackground(border);
        cell.setPadding(8, 8, 8, 8);
        cell.setGravity(android.view.Gravity.CENTER);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            calculateCellHeight());
        params.weight = 1; // Restore weight for equal column sizing
        cell.setLayoutParams(params);
        
        return cell;
    }

    private TextView createPositionCell(String text, int participantIndex) {
        TextView cell = new TextView(this);
        
        // Show "Help" in the lowest visible participant's position cell ONLY on app launch
        if (!isAppInitialized && participantIndex == nrPart - 1) {
            cell.setText("Help");
        } else {
            cell.setText(text);
        }
        
        cell.setTextColor(Color.BLACK); // Black text for position column
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, calculateTextSize());
        // Position cells should be bold and italic with shadow for visibility
        cell.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        // Add shadow to make text appear thicker/more prominent
        cell.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK);
        // Set background color first, then apply border as overlay
        cell.setBackgroundColor(Color.GREEN);
        // Create border as compound drawable overlay
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setStroke(4, Color.BLACK); // 2dp double border = 4px
        border.setColor(Color.GREEN); // Maintain background color
        cell.setBackground(border);
        cell.setPadding(8, 8, 8, 8);
        cell.setGravity(android.view.Gravity.CENTER);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            calculateCellHeight(), // Square cells
            calculateCellHeight());
        cell.setLayoutParams(params);
        
        // Add click listener for help functionality
        cell.setOnClickListener(v -> showHelpDialog());

        return cell;
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
    
    private int calculatePopupButtonWidth(String text) {
        // Calculate optimal width for popup buttons: text width + 75% increase
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics()));
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        float textWidth = paint.measureText(text);
        // Add 75% increase + padding
        return (int) (textWidth * 1.75f) + dpToPx(16); // 75% increase + 16dp for padding
    }
    
    private void exportToExcel() {
        // Use Storage Access Framework for user-selected location
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "Results.csv");
        
        // Set initial directory to Documents if possible (locale-aware)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, 
                    android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"));
            } catch (Exception e) {
                // Fallback if documents URI is not available
            }
        }
        
        startActivityForResult(intent, CREATE_FILE_REQUEST);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_FILE_REQUEST && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                writeExcelFile(uri);
            }
        } else if (requestCode == 2 && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                restoreFromCSV(uri);
            }
        } else if (requestCode == 1001 && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                writeExcelFile(uri);
                Toast.makeText(this, "Results.csv exported successfully", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1002 && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                writeExcelFile(uri);
                Toast.makeText(this, "Results.csv saved successfully", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void writeExcelFile(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            OutputStream outputStream = resolver.openOutputStream(uri);
            if (outputStream != null) {
                StringBuilder content = new StringBuilder();
                
                // Write CSV header
                content.append("Nr,Name");
                for (int i = 1; i <= nrPart; i++) {
                    content.append(",").append(i);
                }
                content.append(",V,->,<-,I,%,P\n");
                
                // Write CSV data rows
                for (int i = 0; i < nrPart; i++) {
                    content.append(i + 1).append(",").append(participantNames[i]);
                    for (int j = 0; j < nrPart; j++) {
                        if (i == j) {
                            content.append(",X");
                        } else {
                            content.append(",").append(boutResults[i][j] == -1 ? "" : boutResults[i][j]);
                        }
                    }
                    content.append(",").append(calculateVictories(i));
                    content.append(",").append(calculateGiven(i));
                    content.append(",").append(calculateReceived(i));
                    content.append(",").append(calculateIndex(i));
                    content.append(",").append(calculatePercent(i));
                    content.append(",").append(calculatePosition(i));
                    content.append("\n");
                }
                
                outputStream.write(content.toString().getBytes());
                outputStream.close();
                
                Toast.makeText(this, "Results exported to CSV format successfully", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "CSV export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private TextView createPositionHeaderCell() {
        TextView cell = new TextView(this);
        
        // Show "Help" in the last visible row of P column ONLY on app launch
        if (!isAppInitialized && nrPart == 1) {
            cell.setText("Help");
        } else {
            cell.setText("P");
        }
        
        cell.setTextColor(Color.BLACK);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, calculateTextSize());
        cell.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        cell.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK);
        cell.setBackgroundColor(Color.GREEN);
        
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setStroke(4, Color.BLACK);
        border.setColor(Color.GREEN);
        cell.setBackground(border);
        cell.setPadding(8, 8, 8, 8);
        cell.setGravity(android.view.Gravity.CENTER);
        
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            calculateCellHeight(),
            calculateCellHeight());
        cell.setLayoutParams(params);
        
        // No click listener needed - export functionality is added in createMatrix()
        
        return cell;
    }
    
    private void showHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Help");
        
        // Create main vertical layout
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);

        // Create a vertical layout for buttons and help text (to scroll together)
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);

        // 2x2 grid for buttons
        GridLayout buttonGrid = new GridLayout(this);
        buttonGrid.setRowCount(2);
        buttonGrid.setColumnCount(2);
        buttonGrid.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // Button layout params to fill width and set height to 2x text size
        float btnTextSize = 18f;
        int btnHeightPx = (int) (48 * getResources().getDisplayMetrics().density); // 48dp in pixels
        GridLayout.LayoutParams btnParams = new GridLayout.LayoutParams();
        btnParams.width = 0;
        btnParams.height = btnHeightPx;
        btnParams.setMargins(10, 10, 10, 10);
        btnParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        btnParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);

        Button restoreButton = new Button(this);
        restoreButton.setText("Restore");
        restoreButton.setTextSize(btnTextSize);
        restoreButton.setPadding(0, 0, 0, 0);
        restoreButton.setLayoutParams(new GridLayout.LayoutParams(btnParams));
        buttonGrid.addView(restoreButton);

        Button loadButton = new Button(this);
        loadButton.setText("Load");
        loadButton.setTextSize(btnTextSize);
        loadButton.setPadding(0, 0, 0, 0);
        loadButton.setLayoutParams(new GridLayout.LayoutParams(btnParams));
        buttonGrid.addView(loadButton);

        Button saveButton = new Button(this);
        saveButton.setText("Save");
        saveButton.setTextSize(btnTextSize);
        saveButton.setPadding(0, 0, 0, 0);
        saveButton.setLayoutParams(new GridLayout.LayoutParams(btnParams));
        // Use a final array to allow access to dialog from lambda
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        saveButton.setOnClickListener(v -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            saveResultsToUserFolder();
        });
        buttonGrid.addView(saveButton);

        Button quitButton = new Button(this);
        quitButton.setText("Quit!");
        quitButton.setTextSize(btnTextSize);
        quitButton.setTextColor(Color.WHITE);
        quitButton.setTypeface(null, android.graphics.Typeface.BOLD);
        quitButton.setBackgroundColor(Color.RED);
        quitButton.setPadding(0, 0, 0, 0);
        quitButton.setLayoutParams(new GridLayout.LayoutParams(btnParams));
        buttonGrid.addView(quitButton);

        scrollContent.addView(buttonGrid);

        // Create text area for help content
        TextView helpText = new TextView(this);
        helpText.setPadding(10, 10, 10, 10);
        helpText.setTextSize(14);
        
        // Load help text from assets
        String helpContent = "Help content not available.";
        try {
            InputStream inputStream = getAssets().open("help.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
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
        
        // Enable clickable links - use standard LinkMovementMethod
        android.text.util.Linkify.addLinks(helpText, android.text.util.Linkify.WEB_URLS | android.text.util.Linkify.EMAIL_ADDRESSES);
        helpText.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        helpText.setLinkTextColor(Color.BLUE);
        
        // Add text to scroll view and scroll view to main layout
        scrollContent.addView(helpText);

        // Create ScrollView for both buttons and help text
        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0);
        scrollParams.weight = 1; // Take up available space
        scrollView.setLayoutParams(scrollParams);
        scrollView.addView(scrollContent);
        mainLayout.addView(scrollView);
        
        // Set the main layout as dialog content
        builder.setView(mainLayout);
        builder.setCancelable(true);
        
        // Create and show dialog with explicit 3-button layout
        final AlertDialog dialog = builder.create();
        
        // Ensure dialog is completely fresh - set all properties explicitly
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialogHolder[0] = dialog;
        
        // Set button listeners after dialog is created - ensure 3 separate buttons
        restoreButton.setOnClickListener(v -> {
            dialog.dismiss(); // Close help dialog first
            restoreFromDefaultBackup();
        });
        
        loadButton.setOnClickListener(v -> {
            dialog.dismiss(); // Close help dialog first  
            openFilePickerForLoad();
        });
        
        quitButton.setOnClickListener(v -> {
            dialog.dismiss(); // Close help dialog first
            finishAffinity();
        });
        
        // Show the dialog
        dialog.show();
        
    }
    
    private void restoreFromDefaultBackup() {
        try {
            // Read from the default backup location in app's private files folder
            File filesDir = getFilesDir();
            File backupFile = new File(filesDir, "Fencing_backup.csv");
            if (!backupFile.exists()) {
                Toast.makeText(this, "No default backup file found in app private folder", Toast.LENGTH_LONG).show();
                return;
            }
            Uri fileUri = Uri.fromFile(backupFile);
            restoreFromCSV(fileUri);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to restore from default backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void openFilePickerForLoad() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        
        // Set initial directory to device Documents folder (public)
        try {
            File publicDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (publicDocumentsDir != null && publicDocumentsDir.exists()) {
                Uri initialUri = Uri.fromFile(publicDocumentsDir);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            }
        } catch (Exception e) {
            // Ignore if setting initial directory fails
        }
        
        startActivityForResult(intent, 2);
    }
    
    private boolean checkPermissionsAndRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 13+ (API 33+), use specific media permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return true; // No runtime permissions needed for Documents folder access via MediaStore
            }
            
            // For older versions, check WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_REQUEST);
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied, show settings dialog
                showPermissionSettingsDialog();
            }
        }
    }
    
    private void showPermissionSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Storage Permission Required");
        builder.setMessage("This app needs storage permission to save and load backup files. Please enable it in Settings.");
        builder.setPositiveButton("Open Settings", (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(this, "Cannot save/load files without storage permission", Toast.LENGTH_LONG).show();
        });
        builder.show();
    }
    
    private void backupData() {
        // Save directly to app private files folder
        saveToPrivateFilesFolder();
    }
    
    private void saveToPrivateFilesFolder() {
        try {
            File filesDir = getFilesDir();
            File backupFile = new File(filesDir, "Fencing_backup.csv");
            FileWriter writer = new FileWriter(backupFile, false); // false = overwrite mode
            writeBackupContent(writer);
            writer.close();
            // Backup saved silently - no notification
        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void deletePreviousBackupFiles() {
        try {
            // Delete backup file from app private files folder
            File filesDir = getFilesDir();
            File backupFile = new File(filesDir, "Fencing_backup.csv");
            if (backupFile.exists()) {
                backupFile.delete();
            }
        } catch (Exception e) {
            // Silent fail - not critical if deletion fails
        }
    }
    
    private void clearParticipantBouts(int participantIndex) {
        // Clear all bout results for the specified participant (row and column)
        for (int i = 0; i < nrPart; i++) {
            if (i != participantIndex) {
                boutResults[participantIndex][i] = -1; // Clear row
                boutResults[i][participantIndex] = -1; // Clear column
            }
        }
    }
    
    private void writeBackupContent(java.io.Writer writer) throws IOException {
        // Write CSV header
        writer.write("Nr,Name");
        for (int i = 1; i <= nrPart; i++) {
            writer.write("," + i);
        }
        writer.write(",V,->,<-,I,%,P\n");
        
        // Write CSV data rows
        for (int i = 0; i < nrPart; i++) {
            writer.write((i + 1) + "," + participantNames[i]);
            for (int j = 0; j < nrPart; j++) {
                if (i == j) {
                    writer.write(",X");
                } else {
                    writer.write("," + (boutResults[i][j] == -1 ? "" : boutResults[i][j]));
                }
            }
            writer.write("," + calculateVictories(i));
            writer.write("," + calculateGiven(i));
            writer.write("," + calculateReceived(i));
            writer.write("," + calculateIndex(i));
            writer.write("," + calculatePercent(i));
            writer.write("," + calculatePosition(i));
            writer.write("\n");
        }
    }
    
    private void restoreFromCSV(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            InputStream inputStream = resolver.openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Cannot access the selected file", Toast.LENGTH_LONG).show();
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            inputStream.close();
            
            if (lines.size() < 2) {
                Toast.makeText(this, "Invalid CSV file format - file too short", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Parse header to determine number of participants  
            String[] headers = lines.get(0).split(",");
            if (headers.length < 9) { // Nr, Name, at least 2 participants, V, ->, <-, I, %, P
                Toast.makeText(this, "Invalid CSV file format - insufficient columns", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Check if file has old format (without %) or new format (with %)
            int resultColumns = headers[headers.length - 1].equals("P") ? 
                (headers[headers.length - 2].equals("%") ? 8 : 7) : 7;
            int newNrPart = headers.length - resultColumns; // Nr, Name, participants, results
            
            if (newNrPart < 2 || newNrPart > 18) {
                Toast.makeText(this, "Invalid number of participants in CSV: " + newNrPart + " (must be 2-18)", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Initialize new arrays
            nrPart = newNrPart;
            participantNames = new String[nrPart];
            boutResults = new int[nrPart][nrPart];
            
            // Initialize with empty values
            for (int i = 0; i < nrPart; i++) {
                participantNames[i] = "";
                for (int j = 0; j < nrPart; j++) {
                    boutResults[i][j] = -1;
                }
            }
            
            // Parse data rows
            int successfulRows = 0;
            for (int i = 1; i < lines.size() && (i - 1) < nrPart; i++) {
                String[] values = lines.get(i).split(",", -1); // Keep empty trailing fields
                int participantIndex = i - 1;
                
                if (values.length >= 2) {
                    // Read name
                    participantNames[participantIndex] = values.length > 1 ? values[1] : "";
                    
                    // Determine how many result columns to skip (V, ->, <-, I, %, P or V, ->, <-, I, P)
                    int resultColumnsToSkip = headers[headers.length - 2].equals("%") ? 6 : 5;
                    
                    // Read bout results
                    for (int j = 2; j < values.length - resultColumnsToSkip && (j - 2) < nrPart; j++) {
                        String value = values[j].trim();
                        if (!value.isEmpty() && !value.equals("X")) {
                            try {
                                int score = Integer.parseInt(value);
                                if (score >= 0 && score <= 16) { // Validate score range
                                    boutResults[participantIndex][j - 2] = score;
                                }
                            } catch (NumberFormatException e) {
                                // Skip invalid values, continue processing
                            }
                        }
                    }
                    successfulRows++;
                }
            }
            
            if (successfulRows == 0) {
                Toast.makeText(this, "No valid data found in CSV file", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Rebuild the matrix
            // createMatrix();
            Toast.makeText(this, "Data restored successfully (" + successfulRows + " participants)", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to restore data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private String calculatePercent(int participantIndex) {
        // Return empty for participants with empty names
        if (participantNames[participantIndex].trim().isEmpty()) {
            return "";
        }
        if (!hasAnyResults()) return "";
        
        // Calculate sum of bouts won (opponent's score shown in red)
        int boutsWon = 0;
        int boutsLost = 0;
        
        for (int j = 0; j < nrPart; j++) {
            if (j != participantIndex && boutResults[participantIndex][j] != -1) {
                if (boutResults[participantIndex][j] > boutResults[j][participantIndex]) {
                    boutsWon++;
                } else if (boutResults[participantIndex][j] < boutResults[j][participantIndex]) {
                    boutsLost++;
                }
            }
        }
        
        int totalBouts = boutsWon + boutsLost;
        
        // Handle division by zero
        if (totalBouts == 0) {
            return "0";
        }
        
        // Calculate percentage: (boutsWon / (boutsWon + boutsLost)) * 100
        double percent = ((double) boutsWon / (double) totalBouts) * 100.0;
        return String.format("%.0f", percent);
    }
    
    private void loadBoutOrders() {
        boutOrderMap.clear();
        try {
            InputStream inputStream = getAssets().open("BoutOrder.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            int currentPoolSize = -1;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // Check if this is a pool header line
                if (line.startsWith("pool of ")) {
                    try {
                        // Extract pool size from "pool of X (Y Bouts)"
                        String[] parts = line.split(" ");
                        if (parts.length >= 3) {
                            currentPoolSize = Integer.parseInt(parts[2]);
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid headers
                    }
                } else if (currentPoolSize >= 4 && currentPoolSize <= 12) {
                    // This is the bout order line for current pool size
                    boutOrderMap.put(currentPoolSize, line);
                    currentPoolSize = -1; // Reset after reading order
                }
            }
            
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading bout orders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updatePartSeq() {
        partSeq.clear();
        
        // Count participants with non-empty names
        List<String> activeParticipants = new ArrayList<>();
        for (int i = 0; i < nrPart; i++) {
            if (!participantNames[i].trim().isEmpty()) {
                activeParticipants.add(participantNames[i]);
            }
        }
        
        int activeCount = activeParticipants.size();
        
        // Only create PartSeq if participant count is between 4 and 12
        if (activeCount < 4 || activeCount > 12) {
            return;
        }
        
        // Get the bout order for this pool size
        String boutOrder = boutOrderMap.get(activeCount);
        if (boutOrder == null || boutOrder.trim().isEmpty()) {
            return;
        }
        
        // Parse bout order and create PartSeq with participant names
        String[] bouts = boutOrder.trim().split(" ");
        for (String bout : bouts) {
            bout = bout.trim();
            if (bout.isEmpty()) continue;
            
            String[] participants = bout.split("-");
            if (participants.length == 2) {
                try {
                    int p1 = Integer.parseInt(participants[0]) - 1; // Convert to 0-based
                    int p2 = Integer.parseInt(participants[1]) - 1;
                    
                    // Check if bout is completed
                    if (p1 >= 0 && p1 < activeCount && p2 >= 0 && p2 < activeCount) {
                        // Find actual participant indices in the full array
                        int actualP1 = findParticipantIndex(activeParticipants.get(p1));
                        int actualP2 = findParticipantIndex(activeParticipants.get(p2));
                        
                        // Only add if bout is not completed
                        if (actualP1 >= 0 && actualP2 >= 0 && 
                            boutResults[actualP1][actualP2] == -1) {
                            String boutString = activeParticipants.get(p1) + " - " + activeParticipants.get(p2);
                            partSeq.add(boutString);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid bout entries
                }
            }
        }
    }
    
    private int findParticipantIndex(String name) {
        for (int i = 0; i < nrPart; i++) {
            if (participantNames[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
    
    private void showNextBoutPopup() {
        // Update PartSeq to get current state
        updatePartSeq();
        
        if (partSeq.isEmpty()) {
            Toast.makeText(this, "No upcoming bouts available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Next Bouts");
        
        // Create text content with next 6 bouts
        StringBuilder content = new StringBuilder();
        int boutsToShow = Math.min(6, partSeq.size());
        for (int i = 0; i < boutsToShow; i++) {
            content.append((i + 1)).append(". ").append(partSeq.get(i)).append("\n");
        }
        
        TextView textView = new TextView(this);
        textView.setText(content.toString());
        textView.setTextSize(16);
        textView.setPadding(40, 40, 40, 40);
        textView.setTextColor(Color.BLACK);
        
        builder.setView(textView);
        builder.setCancelable(true);
        
        final AlertDialog dialog = builder.create();
        dialog.show();
        
        // Auto-dismiss after 5 seconds
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
        }, 5000);
    }
}