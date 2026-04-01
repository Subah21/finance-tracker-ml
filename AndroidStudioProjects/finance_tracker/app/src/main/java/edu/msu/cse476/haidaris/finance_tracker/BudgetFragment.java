package edu.msu.cse476.haidaris.finance_tracker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * BudgetFragment.java
 * Tab 3 — will show per-category budget progress bars and Prophet forecast.
 *
 * Currently a placeholder. Next step is to add:
 *   - Progress bars from GET /budget/{uid} vs GET /transactions/{uid}/summary
 *   - Prophet forecast card from POST /predict with monthly_totals
 */
public class BudgetFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, container, false);
        return view;
    }
}