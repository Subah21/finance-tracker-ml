package edu.msu.cse476.haidaris.finance_tracker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public class BudgetFragment extends Fragment {

    private String firebaseUid;

    private TextView monthlyBudgetValue, spentValue, remainingValue, forecastText;
    private ProgressBar foodProgress, transportProgress, entertainmentProgress;

    // Store spending + budget data once loaded
    private double totalBudget = 0;
    private double totalSpent  = 0;
    private double foodSpent = 0, transportSpent = 0, entertainmentSpent = 0;
    private double foodLimit = 0, transportLimit = 0, entertainmentLimit = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, container, false);

        // Bind views
        monthlyBudgetValue    = view.findViewById(R.id.monthlyBudgetValue);
        spentValue            = view.findViewById(R.id.spentValue);
        remainingValue        = view.findViewById(R.id.remainingValue);
        forecastText          = view.findViewById(R.id.forecastText);
        foodProgress          = view.findViewById(R.id.foodProgress);
        transportProgress     = view.findViewById(R.id.transportProgress);
        entertainmentProgress = view.findViewById(R.id.entertainmentProgress);

        // Get UID from parent
        firebaseUid = ((DashboardActivity) requireActivity()).getFirebaseUid();

        // Load data
        loadBudgetLimits();
        loadSpendingSummary();

        Button updateBudgetButton = view.findViewById(R.id.updateBudgetButton);
        updateBudgetButton.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Budget update feature coming next", Toast.LENGTH_SHORT).show()
        );

        return view;
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
