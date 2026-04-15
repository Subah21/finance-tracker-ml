package edu.msu.cse476.haidaris.finance_tracker;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

public class BudgetFragment extends Fragment {

    private String firebaseUid;

    private TextView monthlyBudgetValue, spentValue, remainingValue, forecastText;
    private ProgressBar foodProgress, transportProgress, entertainmentProgress;

    private double totalBudget = 0;
    private double totalSpent  = 0;
    private double foodSpent = 0, transportSpent = 0, entertainmentSpent = 0;
    private double foodLimit = 0, transportLimit = 0, entertainmentLimit = 0;
    private double totalCategoryBudget = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, container, false);

        monthlyBudgetValue    = view.findViewById(R.id.monthlyBudgetValue);
        spentValue            = view.findViewById(R.id.spentValue);
        remainingValue        = view.findViewById(R.id.remainingValue);
        forecastText          = view.findViewById(R.id.forecastText);
        foodProgress          = view.findViewById(R.id.foodProgress);
        transportProgress     = view.findViewById(R.id.transportProgress);
        entertainmentProgress = view.findViewById(R.id.entertainmentProgress);

        firebaseUid = ((DashboardActivity) requireActivity()).getFirebaseUid();

        loadBudgetLimits();
        loadSpendingSummary();

        Button updateBudgetButton = view.findViewById(R.id.updateBudgetButton);
        updateBudgetButton.setOnClickListener(v -> showSetBudgetDialog());

        return view;
    }

    private static final String[] BUDGET_CATEGORIES = {
            "food", "housing", "transportation", "entertainment",
            "education", "technology", "health", "personal", "other"
    };

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void showSetBudgetDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_set_budget, null);

        Spinner categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        EditText limitInput     = dialogView.findViewById(R.id.limitAmountInput);

        String[] displayNames = new String[BUDGET_CATEGORIES.length];
        for (int i = 0; i < BUDGET_CATEGORIES.length; i++) {
            displayNames[i] = capitalize(BUDGET_CATEGORIES[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                displayNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        new AlertDialog.Builder(requireContext())
                .setTitle("Set Budget Limit")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    int index = categorySpinner.getSelectedItemPosition();
                    String category = BUDGET_CATEGORIES[index];
                    String amountStr = limitInput.getText().toString().trim();

                    if (amountStr.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter an amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double amount;
                    try {
                        amount = Double.parseDouble(amountStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveBudgetLimit(category, amount);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveBudgetLimit(String category, double amount) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("category", category);
            body.put("limit_amount", amount);

            ApiClient.post("/budget", body, new ApiClient.ResponseCallback() {
                @Override
                public void onSuccess(String responseBody) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Budget updated for " + category, Toast.LENGTH_SHORT).show();
                        // Refresh displayed data
                        loadBudgetLimits();
                        loadSpendingSummary();
                    });
                }

                @Override
                public void onFailure(String error) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                    "Failed to save budget: " + error, Toast.LENGTH_SHORT).show()
                    );
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error building request", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadBudgetLimits() {
        ApiClient.get("/budget/" + firebaseUid,
                new ApiClient.ResponseCallback() {
                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONArray arr = new JSONArray(body);
                            totalBudget = 0;

                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject b = arr.getJSONObject(i);
                                String cat = b.getString("category");
                                double limit = b.getDouble("limit_amount");
                                totalBudget += limit;

                                switch (cat) {
                                    case "food":           foodLimit = limit; break;
                                    case "transportation": transportLimit = limit; break;
                                    case "entertainment":  entertainmentLimit = limit; break;
                                    default: break;
                                }
                            }

                            requireActivity().runOnUiThread(() -> {
                                monthlyBudgetValue.setText("$" + String.format("%.2f", totalBudget));
                                updateProgressBars();
                                updateRemaining();
                            });

                        } catch (Exception e) {
                            setFallback(monthlyBudgetValue, "—");
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        setFallback(monthlyBudgetValue, "—");
                    }
                });
    }

    private void loadSpendingSummary() {
        ApiClient.get("/transactions/" + firebaseUid + "/summary",
                new ApiClient.ResponseCallback() {
                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONObject json = new JSONObject(body);
                            totalSpent = json.getDouble("total_spent");
                            JSONObject byCategory = json.getJSONObject("by_category");

                            foodSpent          = byCategory.optDouble("food", 0);
                            transportSpent     = byCategory.optDouble("transportation", 0);
                            entertainmentSpent = byCategory.optDouble("entertainment", 0);

                            requireActivity().runOnUiThread(() -> {
                                spentValue.setText("$" + String.format("%.0f", totalSpent));
                                forecastText.setText("You've spent $" + String.format("%.0f", totalSpent)
                                        + " this month across "
                                        + byCategory.length() + " categories.");
                                updateProgressBars();
                                updateRemaining();
                            });

                        } catch (Exception e) {
                            setFallback(spentValue, "—");
                            setFallback(forecastText, "Could not load spending data.");
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        setFallback(spentValue, "—");
                        setFallback(forecastText, "Could not reach server.");
                    }
                });
    }

    private void updateProgressBars() {
        foodProgress.setProgress(calcProgress(foodSpent, foodLimit));
        transportProgress.setProgress(calcProgress(transportSpent, transportLimit));
        entertainmentProgress.setProgress(calcProgress(entertainmentSpent, entertainmentLimit));
    }

    private void updateRemaining() {
        if (totalBudget > 0) {
            double remaining = Math.max(0, totalBudget - totalSpent);
            remainingValue.setText("$" + String.format("%.0f", remaining));
        }
    }

    private int calcProgress(double spent, double limit) {
        if (limit <= 0) return 0;
        return (int) Math.min(100, (spent / limit) * 100);
    }

    private void setFallback(TextView tv, String text) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> tv.setText(text));
        }
    }
}