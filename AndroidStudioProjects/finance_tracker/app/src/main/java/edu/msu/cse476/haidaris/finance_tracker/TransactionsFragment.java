package edu.msu.cse476.haidaris.finance_tracker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Tab 2 — will show a list of transactions + a button to add new ones.
 * Currently a placeholder. Next step is to add:
 *   - RecyclerView showing GET /transactions/{uid}
 *   - FloatingActionButton --> bottom sheet dialog to POST /transactions
 */
public class TransactionsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transactions, container, false);
        return view;
    }
}