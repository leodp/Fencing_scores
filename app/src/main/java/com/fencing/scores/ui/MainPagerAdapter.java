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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainPagerAdapter extends FragmentStateAdapter {
    private int roundCount;

    public MainPagerAdapter(@NonNull FragmentActivity fa) {
        this(fa, 1);
    }

    public MainPagerAdapter(@NonNull FragmentActivity fa, int roundCount) {
        super(fa);
        this.roundCount = Math.max(1, Math.min(5, roundCount));
    }

    public void setRoundCount(int roundCount) {
        this.roundCount = Math.max(1, Math.min(5, roundCount));
    }

    public int getRoundCount() {
        return roundCount;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position < roundCount) {
            return RoundFragment.newInstance(position + 1);
        }

        int rel = position - roundCount;
        switch (rel) {
            case 0:
                return new MergedFragment();
            case 1:
                return new KOFragment();
            case 2:
            default:
                return new FinalFragment();
        }
    }

    @Override
    public int getItemCount() {
        return roundCount + 3;
    }

    @Override
    public long getItemId(int position) {
        // Generate stable IDs based on fragment type, not position
        // This ensures fragments aren't unnecessarily destroyed when roundCount changes
        if (position < roundCount) {
            // Round pages: IDs 1000 + position (stable based on round index)
            return 1000L + position;
        }
        int rel = position - roundCount;
        switch (rel) {
            case 0:
                return 2000L;  // Merged
            case 1:
                return 2001L;  // KO
            case 2:
            default:
                return 2002L;  // Final
        }
    }

    @Override
    public boolean containsItem(long itemId) {
        // Check if this item ID belongs to a valid fragment
        if (itemId >= 1000L && itemId < 1000L + roundCount) {
            return true;  // Round page
        }
        // Check for Merged, KO, Final fixed pages
        return itemId == 2000L || itemId == 2001L || itemId == 2002L;
    }
}
