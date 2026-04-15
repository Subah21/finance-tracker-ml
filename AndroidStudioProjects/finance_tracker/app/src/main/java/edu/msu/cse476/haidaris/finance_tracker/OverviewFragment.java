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

public class OverviewFragment extends Fragment {

    private TextView txtSafeToSpend, txtIncomeSubtitle;
    private TextView txtIncome, txtTotalSpent;
    private TextView txtRiskPercent, txtRiskBadge, txtRiskMessage;
    private ProgressBar riskProgressBar;
    private TextView txtUserType, txtUserTypeTip;

    private String firebaseUid;

    // Hold summary values so budget call can use them
    private double cachedTotalSpent     = 0;
    private double cachedFood           = 0;
    private double cachedEntertainment  = 0;
    private double cachedDiscretionary  = 0;
    private String cachedMonth          = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

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

        firebaseUid = ((DashboardActivity) requireActivity()).getFirebaseUid();

        loadSummary();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSummary();
    }

    // Step 1: Load spending summary
    private void loadSummary() {
        ApiClient.get("/transactions/" + firebaseUid + "/summary",
                new ApiClient.ResponseCallback() {

                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONObject json       = new JSONObject(body);
                            double totalSpent     = json.getDouble("total_spent");
                            String month          = json.getString("month");
                            JSONObject byCategory = json.getJSONObject("by_category");

                            double food          = byCategory.optDouble("food", 0);
                            double entertainment = byCategory.optDouble("entertainment", 0);
                            double discretionary = food + entertainment
                                    + byCategory.optDouble("misc", 0)
                                    + byCategory.optDouble("personal_care", 0);

                            // Cache for use in budget callback
                            cachedTotalSpent    = totalSpent;
                            cachedFood          = food;
                            cachedEntertainment = entertainment;
                            cachedDiscretionary = discretionary;
                            cachedMonth         = month;

                            requireActivity().runOnUiThread(() ->
                                    txtTotalSpent.setText("$" + String.format(java.util.Locale.getDefault(), "%.0f", totalSpent)));

                            // Step 2: load total budget to use as "income"
                            loadBudgetTotal(totalSpent, food, entertainment, discretionary, month);

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

    // Step 2: Load budget total and use it as the income figure
    private void loadBudgetTotal(double totalSpent, double food,
                                 double entertainment, double discretionary,
                                 String month) {
        ApiClient.get("/budget/" + firebaseUid,
                new ApiClient.ResponseCallback() {
                    @Override
                    public void onSuccess(String body) {
                        try {
                            JSONArray arr = new JSONArray(body);
                            double totalBudget = 0;
                            for (int i = 0; i < arr.length(); i++) {
                                totalBudget += arr.getJSONObject(i).getDouble("limit_amount");
                            }

                            double safeToSpend = Math.max(0, totalBudget - totalSpent);
                            double finalBudget = totalBudget;

                            requireActivity().runOnUiThread(() -> {
                                txtSafeToSpend.setText("$" + String.format(java.util.Locale.getDefault(), "%.2f", safeToSpend));
                                txtIncomeSubtitle.setText("of $" + String.format(java.util.Locale.getDefault(), "%.0f", finalBudget)
                                        + " budget · " + month);
                                txtIncome.setText("$" + String.format(java.util.Locale.getDefault(), "%.0f", finalBudget));
                            });

                            // Step 3: ML predictions
                            loadPredictions(finalBudget, totalSpent, food, entertainment, discretionary);

                        } catch (Exception e) {
                            // Budget load failed — fall back to $0
                            requireActivity().runOnUiThread(() -> {
                                txtSafeToSpend.setText("$0.00");
                                txtIncomeSubtitle.setText("of $0 budget · " + month);
                                txtIncome.setText("$0");
                            });
                            loadPredictions(0, totalSpent, food, entertainment, discretionary);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        loadPredictions(0, totalSpent, food, entertainment, discretionary);
                    }
                });
    }

    // Step 3: Load ML predictions
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
            body.put("monthly_totals",      new JSONArray());

            ApiClient.post("/predict", body, new ApiClient.ResponseCallback() {

                @Override
                public void onSuccess(String responseBody) {
                    try {
                        JSONObject json     = new JSONObject(responseBody);
                        JSONObject riskJson = json.getJSONObject("risk");
                        JSONObject typeJson = json.getJSONObject("user_type");

                        if (riskJson.has("error") || typeJson.has("error")) {
                            requireActivity().runOnUiThread(() -> {
                                txtRiskPercent.setText("N/A");
                                txtUserType.setText("N/A");
                                txtRiskMessage.setText("ML server offline — start it on port 8001.");
                                txtUserTypeTip.setText("");
                            });
                            return;
                        }

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

    private void updateRiskAndType(double riskPercent, boolean atRisk,
                                   String message, String userType, String tip) {
        txtRiskPercent.setText((int) riskPercent + "%");
        txtRiskMessage.setText(message);
        riskProgressBar.setProgress((int) riskPercent);

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

        txtUserType.setText(userType);
        txtUserTypeTip.setText(tip);
    }

    private void showError(String message) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
        }
    }
}