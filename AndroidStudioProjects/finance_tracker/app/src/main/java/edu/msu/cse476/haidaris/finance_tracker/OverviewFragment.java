package edu.msu.cse476.haidaris.finance_tracker;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tab 1 — shows the 3 key numbers:
 *   1. Safe to spend (income and the total spent this month)
 *   2. Income vs total spent (two small cards)
 *   3. Overspending risk % from ML model + progress bar
 *   4. ML user type (Saver or Balanced or Spender)
 *
 * API CALLS MADE:
 *   GET  /transactions/{uid}/summary  --> income, total_spent, safe_to_spend
 *   POST /predict                     --> risk_percent, user_type, tip
 *
 * CALL ORDER:
 *   loadSummary() runs first. When it succeeds, it calls loadPredictions()
 *   using the spending data from the summary. This ensures predictions
 *   always use fresh, current data.
 */
public class OverviewFragment extends Fragment {

    private TextView txtSafeToSpend, txtIncomeSubtitle;
    private TextView txtIncome, txtTotalSpent;
    private TextView txtRiskPercent, txtRiskBadge, txtRiskMessage;
    private ProgressBar riskProgressBar;
    private TextView txtUserType, txtUserTypeTip;

    private String firebaseUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

        // Bind all views
        txtSafeToSpend    = view.findViewById(R.id.txtSafeToSpend);
        txtIncomeSubtitle = view.findViewById(R.id.txtIncomeSubtitle);
        txtIncome         = view.findViewById(R.id.txtIncome);
        txtTotalSpent     = view.findViewById(R.id.txtTotalSpent);
        txtRiskPercent    = view.findViewById(R.id.txtRiskPercent);
        txtRiskBadge      = view.findViewById(R.id.txtRiskBadge);
        txtRiskMessage    = view.findViewById(R.id.txtRiskMessage);
        riskProgressBar   = view.findViewById(R.id.riskProgressBar);
        txtUserType       = view.findViewById(R.id.txtUserType);
        txtUserTypeTip    = view.findViewById(R.id.txtUserTypeTip);

        // Get Firebase UID from parent DashboardActivity
        firebaseUid = ((DashboardActivity) requireActivity()).getFirebaseUid();

        // Load data
        loadSummary();

        return view;
    }

    // Step 1: Load spending summary
    private void loadSummary() {
        ApiClient.get("/transactions/" + firebaseUid + "/summary",
                new ApiClient.ResponseCallback() {

                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONObject json       = new JSONObject(body);
                            double income         = json.getDouble("monthly_income");
                            double totalSpent     = json.getDouble("total_spent");
                            double safeToSpend    = json.getDouble("safe_to_spend");
                            String month          = json.getString("month");
                            JSONObject byCategory = json.getJSONObject("by_category");

                            // Calculate spending breakdown for ML call
                            double food          = byCategory.optDouble("food", 0);
                            double entertainment = byCategory.optDouble("entertainment", 0);
                            double discretionary = food + entertainment
                                    + byCategory.optDouble("misc", 0)
                                    + byCategory.optDouble("personal_care", 0);

                            // Update UI on main thread
                            requireActivity().runOnUiThread(() -> {
                                txtSafeToSpend.setText("$" + String.format("%.2f", safeToSpend));
                                txtIncomeSubtitle.setText("of $" + String.format("%.0f", income)
                                        + " income · " + month);
                                txtIncome.setText("$" + String.format("%.0f", income));
                                txtTotalSpent.setText("$" + String.format("%.0f", totalSpent));
                            });

                            // Step 2: now call ML predictions using this data
                            loadPredictions(income, totalSpent, food, entertainment, discretionary);

                        } catch (Exception e) {
                            showError("Could not load summary.");
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        showError("Could not reach server.");
                    }
                });
    }

    // Step 2: Load ML predictions
    private void loadPredictions(double income, double totalSpent,
                                 double food, double entertainment,
                                 double discretionary) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid",        firebaseUid);
            body.put("total_spent",         totalSpent);
            body.put("food_spend",          food);
            body.put("entertainment_spend", entertainment);
            body.put("discretionary_spend", discretionary);
            // monthly_totals left empty
            body.put("monthly_totals",      new JSONArray());

            ApiClient.post("/predict", body, new ApiClient.ResponseCallback() {

                @Override
                public void onSuccess(String responseBody) {
                    try {
                        JSONObject json     = new JSONObject(responseBody);
                        JSONObject riskJson = json.getJSONObject("risk");
                        JSONObject typeJson = json.getJSONObject("user_type");

                        double riskPercent = riskJson.getDouble("risk_percent");
                        boolean atRisk     = riskJson.getBoolean("at_risk");
                        String message     = riskJson.getString("message");
                        String userType    = typeJson.getString("label");
                        String tip         = typeJson.getString("tip");

                        requireActivity().runOnUiThread(() ->
                                updateRiskAndType(riskPercent, atRisk, message, userType, tip));

                    } catch (Exception e) {
                        showError("Could not parse ML results.");
                    }
                }

                @Override
                public void onFailure(String error) {
                    // ML server not reachable for now, so it would show a placeholder
                    requireActivity().runOnUiThread(() -> {
                        txtRiskPercent.setText("N/A");
                        txtUserType.setText("N/A");
                        txtRiskMessage.setText("ML server not reachable.");
                    });
                }
            });

        } catch (Exception e) {
            showError("Could not build prediction request.");
        }
    }

    // Update risk card and user type card
    private void updateRiskAndType(double riskPercent, boolean atRisk,
                                   String message, String userType, String tip) {
        // Risk percent
        txtRiskPercent.setText((int) riskPercent + "%");
        txtRiskMessage.setText(message);
        riskProgressBar.setProgress((int) riskPercent);

        // Color the progress bar and badge based on risk level
        if (riskPercent < 35) {
            riskProgressBar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1D9E75")));
            txtRiskBadge.setText("Low risk");
            txtRiskBadge.setBackgroundColor(Color.parseColor("#E1F5EE"));
            txtRiskBadge.setTextColor(Color.parseColor("#0F6E56"));
        } else if (riskPercent < 70) {
            riskProgressBar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#BA7517")));
            txtRiskBadge.setText("Medium risk");
            txtRiskBadge.setBackgroundColor(Color.parseColor("#FAEEDA"));
            txtRiskBadge.setTextColor(Color.parseColor("#854F0B"));
        } else {
            riskProgressBar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#E24B4A")));
            txtRiskBadge.setText("High risk");
            txtRiskBadge.setBackgroundColor(Color.parseColor("#FCEBEB"));
            txtRiskBadge.setTextColor(Color.parseColor("#A32D2D"));
        }

        // User type
        txtUserType.setText(userType);
        txtUserTypeTip.setText(tip);
    }

    // Helper which shows the toast error on main thread
    private void showError(String message) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
        }
    }
}