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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ScoresViewModel extends ViewModel {
            /**
             * Reset participantNames and boutResults to default empty state, and reset nrPart.
             */
            public void resetToDefault() {
                synchronized (resizeLock) {
                    // Reset participant names
                    String[] emptyNames = new String[DEFAULT_PARTICIPANTS];
                    for (int i = 0; i < DEFAULT_PARTICIPANTS; i++) {
                        emptyNames[i] = "";
                    }
                    participantNames.setValue(emptyNames);
                    // Reset bout results
                    int[][] emptyResults = new int[DEFAULT_PARTICIPANTS][DEFAULT_PARTICIPANTS];
                    for (int i = 0; i < DEFAULT_PARTICIPANTS; i++) {
                        for (int j = 0; j < DEFAULT_PARTICIPANTS; j++) {
                            emptyResults[i][j] = -1;
                        }
                    }
                    boutResults.setValue(emptyResults);
                    // Reset participant count
                    nrPart.setValue(DEFAULT_PARTICIPANTS);
                }
            }
        private final Object resizeLock = new Object();
    public static final int MAX_PARTICIPANTS = 128;
    public static final int MIN_PARTICIPANTS = 5;
    public static final int DEFAULT_PARTICIPANTS = 10;
    public static final int MAX_SCORE = 16;

    private final MutableLiveData<Integer> nrPart = new MutableLiveData<>(DEFAULT_PARTICIPANTS);
    private final MutableLiveData<String[]> participantNames = new MutableLiveData<>(new String[DEFAULT_PARTICIPANTS]);
    private final MutableLiveData<int[][]> boutResults;
        {
            // Initialize boutResults with -1 (empty) instead of 0
            int[][] initialResults = new int[DEFAULT_PARTICIPANTS][DEFAULT_PARTICIPANTS];
            for (int i = 0; i < DEFAULT_PARTICIPANTS; i++) {
                for (int j = 0; j < DEFAULT_PARTICIPANTS; j++) {
                    initialResults[i][j] = -1;
                }
            }
            boutResults = new MutableLiveData<>(initialResults);
        }
    private final MutableLiveData<Integer> colorCycleIndex = new MutableLiveData<>(0);
    
    // Final KO rankings: list of participant names in ranking order (1st, 2nd, 3rd, etc.)
    private final MutableLiveData<java.util.List<String>> finalKORankings = new MutableLiveData<>(new java.util.ArrayList<>());
    
    // Request flag for KOFragment to calculate rankings when Final page becomes visible
    private final MutableLiveData<Boolean> requestKORankings = new MutableLiveData<>(false);

    public LiveData<Integer> getNrPart() { return nrPart; }
    public LiveData<String[]> getParticipantNames() { return participantNames; }
    public LiveData<int[][]> getBoutResults() { return boutResults; }
    public LiveData<Integer> getColorCycleIndex() { return colorCycleIndex; }
    public LiveData<java.util.List<String>> getFinalKORankings() { return finalKORankings; }
    public LiveData<Boolean> getRequestKORankings() { return requestKORankings; }

    public void setFinalKORankings(java.util.List<String> rankings) {
        finalKORankings.setValue(rankings);
    }
    
    public void requestKORankingsCalculation(boolean request) {
        requestKORankings.setValue(request);
    }

    public void setNrPart(int n) {
        synchronized (resizeLock) {
            if (n < MIN_PARTICIPANTS) n = MIN_PARTICIPANTS;
            if (n > MAX_PARTICIPANTS) n = MAX_PARTICIPANTS;
            // Always create and set new arrays for both add and remove
            String[] names = participantNames.getValue();
            int[][] results = boutResults.getValue();
            String[] newNames = new String[n];
            int[][] newResults = new int[n][n];
            int oldLen = (names != null) ? names.length : 0;
            int oldResLen = (results != null) ? results.length : 0;
            for (int i = 0; i < n; i++) {
                if (names != null && i < oldLen && names[i] != null) {
                    newNames[i] = names[i];
                } else {
                    newNames[i] = "";
                }
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (results != null && i < oldResLen && j < results[i].length) {
                        newResults[i][j] = results[i][j];
                    } else {
                        newResults[i][j] = -1;
                    }
                }
            }
            participantNames.setValue(newNames);
            boutResults.setValue(newResults);
            // Now update nrPart last, so observers see fully resized arrays
            nrPart.setValue(n);
        }
    }

    public void setParticipantNames(String[] names) {
        participantNames.setValue(names);
    }
    public void setBoutResults(int[][] results) {
        boutResults.setValue(results);
    }
    public void setColorCycleIndex(int idx) {
        colorCycleIndex.setValue(idx);
    }
}
