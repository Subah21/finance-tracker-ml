package edu.msu.cse476.haidaris.finance_tracker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

public class TransactionsFragment extends Fragment {

    private String firebaseUid;

    private TextView totalSpentValue;
    private TextView transactionName1, transactionMeta1, transactionAmount1;
    private TextView transactionName2, transactionMeta2, transactionAmount2;
    private TextView transactionName3, transactionMeta3, transactionAmount3;
    private TextView emptyTransactionsText;

    /** Categories that match the backend's expected values. */
    private static final String[] CATEGORIES = {
            "food", "housing", "transportation", "entertainment",
            "education", "technology", "health", "personal", "other"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transactions, container, false);

        // Bind views
        totalSpentValue     = view.findViewById(R.id.totalSpentValue);
        transactionName1    = view.findViewById(R.id.transactionName1);
        transactionMeta1    = view.findViewById(R.id.transactionMeta1);
        transactionAmount1  = view.findViewById(R.id.transactionAmount1);
        transactionName2    = view.findViewById(R.id.transactionName2);
        transactionMeta2    = view.findViewById(R.id.transactionMeta2);
        transactionAmount2  = view.findViewById(R.id.transactionAmount2);
        transactionName3    = view.findViewById(R.id.transactionName3);
        transactionMeta3    = view.findViewById(R.id.transactionMeta3);
        transactionAmount3  = view.findViewById(R.id.transactionAmount3);
        emptyTransactionsText = view.findViewById(R.id.emptyTransactionsText);

        // Get UID from parent
        firebaseUid = ((DashboardActivity) requireActivity()).getFirebaseUid();

        // Load data
        loadSpentThisMonth();
        loadRecentTransactions();

        Button addTransactionButton = view.findViewById(R.id.addTransactionButton);
        addTransactionButton.setOnClickListener(v -> showAddTransactionDialog());

        return view;
    }

    /**
     * Shows a dialog with amount, category dropdown, and optional description.
     * On submit, POSTs to the backend and refreshes the transaction list.
     */
    private void showAddTransactionDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_transaction, null);

        EditText inputAmount      = dialogView.findViewById(R.id.inputAmount);
        Spinner spinnerCategory   = dialogView.findViewById(R.id.spinnerCategory);
        EditText inputDescription = dialogView.findViewById(R.id.inputDescription);

        // Populate spinner with capitalized category names
        String[] displayNames = new String[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) {
            displayNames[i] = capitalize(CATEGORIES[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                displayNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.new_transaction)
                .setView(dialogView)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String amountStr   = inputAmount.getText().toString().trim();
                    String description = inputDescription.getText().toString().trim();
                    int categoryIndex  = spinnerCategory.getSelectedItemPosition();
                    String category    = CATEGORIES[categoryIndex];

                    // Validate amount
                    if (amountStr.isEmpty()) {
                        Toast.makeText(requireContext(),
                                "Please enter an amount.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double amount;
                    try {
                        amount = Double.parseDouble(amountStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(),
                                "Invalid amount.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (amount <= 0) {
                        Toast.makeText(requireContext(),
                                "Amount must be greater than 0.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // POST the transaction to the backend
                    submitTransaction(amount, category, description);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Sends POST /transactions to the backend, then refreshes the UI.
     */
    private void submitTransaction(double amount, String category, String description) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("amount", amount);
            body.put("category", category);
            if (!description.isEmpty()) {
                body.put("description", description);
            }
            body.put("is_recurring", false);

            ApiClient.post("/transactions", body, new ApiClient.ResponseCallback() {
                @Override
                public void onSuccess(String responseBody) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Transaction added!", Toast.LENGTH_SHORT).show();

                        // Refresh both the total and the recent list
                        loadSpentThisMonth();
                        loadRecentTransactions();
                    });
                }

                @Override
                public void onFailure(String error) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                    "Failed to add transaction.", Toast.LENGTH_SHORT).show());
                }
            });

        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Error building request.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSpentThisMonth() {
        ApiClient.get("/transactions/" + firebaseUid + "/summary",
                new ApiClient.ResponseCallback() {
                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONObject json = new JSONObject(body);
                            double totalSpent = json.getDouble("total_spent");
                            requireActivity().runOnUiThread(() ->
                                    totalSpentValue.setText("$" + String.format("%.2f", totalSpent)));
                        } catch (Exception e) {
                            setFallback(totalSpentValue, "—");
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        setFallback(totalSpentValue, "—");
                    }
                });
    }

    private void loadRecentTransactions() {
        ApiClient.get("/transactions/" + firebaseUid,
                new ApiClient.ResponseCallback() {
                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONArray arr = new JSONArray(body);

                            // Show up to 3 most recent (API returns all, newest last)
                            requireActivity().runOnUiThread(() -> {
                                if (arr.length() == 0) {
                                    clearSlot(transactionName1, transactionMeta1, transactionAmount1);
                                    clearSlot(transactionName2, transactionMeta2, transactionAmount2);
                                    clearSlot(transactionName3, transactionMeta3, transactionAmount3);
                                    emptyTransactionsText.setText("No transactions yet.");
                                    return;
                                }

                                emptyTransactionsText.setText("");

                                // Fill slots from most recent
                                fillSlot(arr, arr.length() - 1, transactionName1, transactionMeta1, transactionAmount1);
                                fillSlot(arr, arr.length() - 2, transactionName2, transactionMeta2, transactionAmount2);
                                fillSlot(arr, arr.length() - 3, transactionName3, transactionMeta3, transactionAmount3);
                            });

                        } catch (Exception e) {
                            clearAllSlots();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        clearAllSlots();
                    }
                });
    }

    private void fillSlot(JSONArray arr, int index,
                           TextView name, TextView meta, TextView amount) {
        if (index < 0 || index >= arr.length()) {
            clearSlot(name, meta, amount);
            return;
        }
        try {
            JSONObject tx = arr.getJSONObject(index);
            String desc     = tx.optString("description", "");
            String category = tx.optString("category", "");
            double amt      = tx.getDouble("amount");

            name.setText(desc.isEmpty() ? capitalize(category) : desc);
            meta.setText(capitalize(category));
            amount.setText("-$" + String.format("%.2f", amt));
        } catch (Exception e) {
            clearSlot(name, meta, amount);
        }
    }

    private void clearSlot(TextView name, TextView meta, TextView amount) {
        name.setText("");
        meta.setText("");
        amount.setText("");
    }

    private void clearAllSlots() {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                clearSlot(transactionName1, transactionMeta1, transactionAmount1);
                clearSlot(transactionName2, transactionMeta2, transactionAmount2);
                clearSlot(transactionName3, transactionMeta3, transactionAmount3);
            });
        }
    }

    private void setFallback(TextView tv, String text) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> tv.setText(text));
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
