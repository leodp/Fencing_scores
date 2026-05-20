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
            public static final int MIN_ROUNDS = 1;
            public static final int MAX_ROUNDS = 5;
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
                    nrRounds.setValue(1);
                    activeRoundCode = 1;
                    roundBoutResults.clear();
                    roundBoutResults.put(1, cloneMatrix(emptyResults));
                    roundColorCycleIndex.clear();
                    roundColorCycleIndex.put(1, 0);
                    colorCycleIndex.setValue(0);
                }
            }
        private final Object resizeLock = new Object();
    public static final int MAX_PARTICIPANTS = 128;
    public static final int MIN_PARTICIPANTS = 5;
    public static final int DEFAULT_PARTICIPANTS = 10;
    public static final int MAX_SCORE = 16;

    private final MutableLiveData<Integer> nrPart = new MutableLiveData<>(DEFAULT_PARTICIPANTS);
    private final MutableLiveData<Integer> nrRounds = new MutableLiveData<>(1);
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
    private int activeRoundCode = 1;
    private final java.util.Map<Integer, int[][]> roundBoutResults = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> roundColorCycleIndex = new java.util.HashMap<>();
    
    // Final KO rankings: list of participant names in ranking order (1st, 2nd, 3rd, etc.)
    private final MutableLiveData<java.util.List<String>> finalKORankings = new MutableLiveData<>(new java.util.ArrayList<>());
    
    // Request flag for KOFragment to calculate rankings when Final page becomes visible
    private final MutableLiveData<Boolean> requestKORankings = new MutableLiveData<>(false);

    public ScoresViewModel() {
        int[][] initial = boutResults.getValue();
        roundBoutResults.put(1, cloneMatrix(initial));
        roundColorCycleIndex.put(1, 0);
    }

    public LiveData<Integer> getNrPart() { return nrPart; }
    public LiveData<Integer> getNrRounds() { return nrRounds; }
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

    private int[][] cloneMatrix(int[][] src) {
        if (src == null) return null;
        int[][] out = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            out[i] = (src[i] != null) ? src[i].clone() : null;
        }
        return out;
    }

    private int[][] createEmptyMatrix(int n) {
        int[][] m = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) m[i][j] = -1;
        }
        return m;
    }

    private int[][] resizeMatrix(int[][] src, int newSize) {
        int[][] out = new int[newSize][newSize];
        for (int i = 0; i < newSize; i++) {
            for (int j = 0; j < newSize; j++) {
                if (src != null && i < src.length && src[i] != null && j < src[i].length) out[i][j] = src[i][j];
                else out[i][j] = -1;
            }
        }
        return out;
    }

    private int getCurrentRoundCount() {
        Integer rounds = nrRounds.getValue();
        return rounds != null ? rounds : 1;
    }

    public int getActiveRoundCode() {
        return activeRoundCode;
    }

    public void persistActiveRoundData() {
        synchronized (resizeLock) {
            int[][] current = boutResults.getValue();
            if (current != null) {
                roundBoutResults.put(activeRoundCode, cloneMatrix(current));
            }
            Integer color = colorCycleIndex.getValue();
            roundColorCycleIndex.put(activeRoundCode, color != null ? color : 0);
        }
    }

    public void switchToRound(int roundCode) {
        synchronized (resizeLock) {
            int rounds = getCurrentRoundCount();
            if (roundCode < MIN_ROUNDS) roundCode = MIN_ROUNDS;
            if (roundCode > rounds) roundCode = rounds;

            persistActiveRoundData();

            int n = nrPart.getValue() != null ? nrPart.getValue() : DEFAULT_PARTICIPANTS;
            if (!roundBoutResults.containsKey(roundCode)) {
                roundBoutResults.put(roundCode, createEmptyMatrix(n));
            }
            if (!roundColorCycleIndex.containsKey(roundCode)) {
                int colorRound1 = roundColorCycleIndex.getOrDefault(1, 0);
                roundColorCycleIndex.put(roundCode, colorRound1);
            }
            activeRoundCode = roundCode;
            boutResults.setValue(cloneMatrix(roundBoutResults.get(roundCode)));
            colorCycleIndex.setValue(roundColorCycleIndex.getOrDefault(roundCode, 0));
        }
    }

    public void setNrRounds(int rounds) {
        synchronized (resizeLock) {
            if (rounds < MIN_ROUNDS) rounds = MIN_ROUNDS;
            if (rounds > MAX_ROUNDS) rounds = MAX_ROUNDS;

            persistActiveRoundData();

            int oldRounds = getCurrentRoundCount();
            int n = nrPart.getValue() != null ? nrPart.getValue() : DEFAULT_PARTICIPANTS;
            if (rounds > oldRounds) {
                int colorRound1 = roundColorCycleIndex.getOrDefault(1, colorCycleIndex.getValue() != null ? colorCycleIndex.getValue() : 0);
                for (int r = oldRounds + 1; r <= rounds; r++) {
                    roundBoutResults.put(r, createEmptyMatrix(n));
                    roundColorCycleIndex.put(r, colorRound1);
                }
            } else if (rounds < oldRounds) {
                for (int r = oldRounds; r > rounds; r--) {
                    roundBoutResults.remove(r);
                    roundColorCycleIndex.remove(r);
                }
                if (activeRoundCode > rounds) {
                    activeRoundCode = rounds;
                }
            }

            nrRounds.setValue(rounds);
            switchToRound(activeRoundCode);
        }
    }

    public int[][] getRoundBoutResultsSnapshot(int roundCode) {
        synchronized (resizeLock) {
            int[][] m = roundBoutResults.get(roundCode);
            return cloneMatrix(m);
        }
    }

    public int getRoundColorCycleIndex(int roundCode) {
        synchronized (resizeLock) {
            int rounds = getCurrentRoundCount();
            if (roundCode < MIN_ROUNDS) roundCode = MIN_ROUNDS;
            if (roundCode > rounds) roundCode = rounds;
            return roundColorCycleIndex.getOrDefault(roundCode, 0);
        }
    }

    public void reorderAllRounds(int[] newToOld, String[] newNames) {
        synchronized (resizeLock) {
            persistActiveRoundData();
            int n = newToOld != null ? newToOld.length : 0;
            if (n <= 0) return;

            int rounds = getCurrentRoundCount();
            for (int r = 1; r <= rounds; r++) {
                int[][] src = roundBoutResults.get(r);
                int[][] dst = new int[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        int oldI = newToOld[i];
                        int oldJ = newToOld[j];
                        if (src != null && oldI >= 0 && oldJ >= 0 && oldI < src.length && src[oldI] != null && oldJ < src[oldI].length) {
                            dst[i][j] = src[oldI][oldJ];
                        } else {
                            dst[i][j] = -1;
                        }
                    }
                }
                roundBoutResults.put(r, dst);
            }

            participantNames.setValue(newNames);
            boutResults.setValue(cloneMatrix(roundBoutResults.get(activeRoundCode)));
        }
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
            // Resize matrices for all rounds to keep participant structure synchronized.
            persistActiveRoundData();
            int rounds = getCurrentRoundCount();
            for (int r = 1; r <= rounds; r++) {
                int[][] src = roundBoutResults.get(r);
                roundBoutResults.put(r, resizeMatrix(src, n));
            }
            boutResults.setValue(cloneMatrix(roundBoutResults.getOrDefault(activeRoundCode, newResults)));
            // Now update nrPart last, so observers see fully resized arrays
            nrPart.setValue(n);
        }
    }

    public void setParticipantNames(String[] names) {
        synchronized (resizeLock) {
            String[] oldNames = participantNames.getValue();
            participantNames.setValue(names);

            if (names == null || names.length == 0) {
                return;
            }

            persistActiveRoundData();
            int rounds = getCurrentRoundCount();
            int targetSize = names.length;

            java.util.Map<String, Integer> oldNameToIndex = new java.util.HashMap<>();
            if (oldNames != null) {
                for (int i = 0; i < oldNames.length; i++) {
                    String n = oldNames[i] != null ? oldNames[i].trim() : "";
                    if (!n.isEmpty() && !oldNameToIndex.containsKey(n)) {
                        oldNameToIndex.put(n, i);
                    }
                }
            }

            for (int r = 1; r <= rounds; r++) {
                int[][] src = roundBoutResults.get(r);
                int[][] dst = createEmptyMatrix(targetSize);
                for (int i = 0; i < targetSize; i++) {
                    String ni = names[i] != null ? names[i].trim() : "";
                    Integer siObj = (!ni.isEmpty()) ? oldNameToIndex.get(ni) : null;
                    int si = (siObj != null) ? siObj : i;
                    if (src == null || si < 0 || si >= src.length || src[si] == null) continue;

                    for (int j = 0; j < targetSize; j++) {
                        String nj = names[j] != null ? names[j].trim() : "";
                        Integer sjObj = (!nj.isEmpty()) ? oldNameToIndex.get(nj) : null;
                        int sj = (sjObj != null) ? sjObj : j;
                        if (sj < 0 || sj >= src[si].length) continue;
                        dst[i][j] = src[si][sj];
                    }
                }
                roundBoutResults.put(r, dst);
            }

            boutResults.setValue(cloneMatrix(roundBoutResults.getOrDefault(activeRoundCode, createEmptyMatrix(targetSize))));
        }
    }
    public void setBoutResults(int[][] results) {
        boutResults.setValue(results);
        synchronized (resizeLock) {
            roundBoutResults.put(activeRoundCode, cloneMatrix(results));
        }
    }
    public void setColorCycleIndex(int idx) {
        colorCycleIndex.setValue(idx);
        synchronized (resizeLock) {
            roundColorCycleIndex.put(activeRoundCode, idx);
        }
    }
}
