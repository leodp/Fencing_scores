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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fencing.scores.R;
import com.fencing.scores.ScoresViewModel;
import com.fencing.scores.MainActivity;

import java.util.List;

public class FinalFragment extends Fragment {

    private ScoresViewModel scoresViewModel;
    private LinearLayout rankingsLayout;
    private LinearLayout rankingsLayoutMiddle;  // Middle column for split view
    private LinearLayout rankingsLayoutRight;   // Right column for split view
    private ActivityResultLauncher<Intent> saveFileLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
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
        return inflater.inflate(R.layout.fragment_final, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        scoresViewModel = new ViewModelProvider(requireActivity()).get(ScoresViewModel.class);
        rankingsLayout = view.findViewById(R.id.final_rankingsLayout);
        rankingsLayoutMiddle = view.findViewById(R.id.final_rankingsLayoutMiddle);
        rankingsLayoutRight = view.findViewById(R.id.final_rankingsLayoutRight);
        Button saveBtn = view.findViewById(R.id.final_saveBtn);

        // Observe final rankings and update UI
        scoresViewModel.getFinalKORankings().observe(getViewLifecycleOwner(), rankings -> {
            renderRankings(rankings);
        });

        // Save button - launch file picker
        saveBtn.setOnClickListener(v -> launchFilePicker());
        
        // Long press on view or rankings area navigates to Round (page 0)
        View.OnLongClickListener goToRound = v -> {
            navigateToPreviousPage();
            return true;
        };
        view.setOnLongClickListener(goToRound);
        rankingsLayout.setOnLongClickListener(goToRound);
        rankingsLayoutMiddle.setOnLongClickListener(goToRound);
        rankingsLayoutRight.setOnLongClickListener(goToRound);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Request KOFragment to calculate and provide rankings
        scoresViewModel.requestKORankingsCalculation(true);
    }
    
    // Navigate to previous page (Final -> Round, wrapping in loop)
    private void navigateToPreviousPage() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).navigateToPage(0); // 0 = RoundFragment (loop)
        }
    }

    private void renderRankings(List<String> rankings) {
        rankingsLayout.removeAllViews();
        rankingsLayoutMiddle.removeAllViews();
        rankingsLayoutRight.removeAllViews();
        
        if (rankings == null || rankings.isEmpty()) {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No final rankings available.\nPress FINAL on the KO page after completing matches.");
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(16, 32, 16, 32);
            rankingsLayout.addView(emptyText);
            return;
        }
        
        // Count valid participants (excluding Empty, empty, null, whitespace-only)
        int validCount = 0;
        for (String name : rankings) {
            if (isValidParticipant(name)) {
                validCount++;
            }
        }
        
        // Split into 3 columns
        int itemsPerColumn = (validCount + 2) / 3;  // Ceiling division by 3

        int rank = 1;
        int displayedCount = 0;
        for (String name : rankings) {
            // Skip invalid participants
            if (!isValidParticipant(name)) {
                continue;
            }

            // Create text with bold position number using SpannableString
            String posText = rank + ". ";
            String fullText = posText + name;
            android.text.SpannableString spannable = new android.text.SpannableString(fullText);
            spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, posText.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            TextView rankView = new TextView(getContext());
            rankView.setText(spannable);
            rankView.setTextSize(20);
            rankView.setPadding(16, 12, 16, 12);
            rankView.setShadowLayer(3, 2, 2, 0xFF444444); // Dark shadow for position
            
            // Highlight top 3
            if (rank == 1) {
                rankView.setBackgroundColor(0xFFFFD700); // Gold
                rankView.setTextColor(0xFF000000);
            } else if (rank == 2) {
                rankView.setBackgroundColor(0xFFC0C0C0); // Silver
                rankView.setTextColor(0xFF000000);
            } else if (rank == 3) {
                rankView.setBackgroundColor(0xFFCD7F32); // Bronze
                rankView.setTextColor(0xFFFFFFFF);
            }
            
            // Decide which column to add to (3-way split)
            if (displayedCount >= itemsPerColumn * 2) {
                rankingsLayoutRight.addView(rankView);
            } else if (displayedCount >= itemsPerColumn) {
                rankingsLayoutMiddle.addView(rankView);
            } else {
                rankingsLayout.addView(rankView);
            }
            
            rank++;
            displayedCount++;
        }
    }
    
    // Check if participant name is valid (not empty, null, "EMPTY", or unresolved refs)
    private boolean isValidParticipant(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.equalsIgnoreCase("empty")) return false;
        if (trimmed.equals("-")) return false;
        // Filter out unresolved bracket references
        if (trimmed.startsWith("W_") || trimmed.startsWith("L_") || 
            trimmed.startsWith("MW") || trimmed.startsWith("LW") ||
            trimmed.startsWith("ML") || trimmed.equals("GFL") || trimmed.equals("LBW")) {
            return false;
        }
        return true;
    }

    private void launchFilePicker() {
        List<String> rankings = scoresViewModel.getFinalKORankings().getValue();
        
        if (rankings == null || rankings.isEmpty()) {
            Toast.makeText(getContext(), "No rankings to save", Toast.LENGTH_SHORT).show();
            android.util.Log.w("FinalFragment", "SAVE: No rankings to save");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "Final_Rankings.csv");
        android.util.Log.d("FinalFragment", "SAVE: Launching file picker");
        saveFileLauncher.launch(intent);
    }

    private void saveCsvToUri(Uri uri) {
        List<String> rankings = scoresViewModel.getFinalKORankings().getValue();
        
        if (rankings == null || rankings.isEmpty()) {
            Toast.makeText(getContext(), "No rankings to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            android.util.Log.i("FinalFragment", "SAVE: Writing to URI " + uri.toString());
            
            java.io.OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                throw new Exception("Could not open output stream");
            }
            
            java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(outputStream));
            writer.write("Rank,Name\n");
            
            int rank = 1;
            for (String name : rankings) {
                if (isValidParticipant(name)) {
                    writer.write(rank + "," + name + "\n");
                    android.util.Log.d("FinalFragment", "SAVE: " + rank + "," + name);
                    rank++;
                }
            }
            
            writer.close();
            android.util.Log.i("FinalFragment", "SAVE: File saved successfully");
            Toast.makeText(getContext(), "Final rankings saved successfully!", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            android.util.Log.e("FinalFragment", "SAVE ERROR: " + e.getMessage(), e);
            Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
