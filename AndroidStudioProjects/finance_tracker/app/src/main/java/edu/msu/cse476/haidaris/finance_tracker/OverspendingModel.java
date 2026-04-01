package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Logistic Regression model for predicting overspending risk.
 *
 * This class loads pre-trained model weights (exported from Python/scikit-learn)
 * and uses them to predict whether a user is at risk of overspending.
 *
 * How it works:
 * 1. Takes user's financial data (income, spending categories)
 * 2. Scales the data the same way the training data was scaled
 * 3. Multiplies by learned weights and applies sigmoid function
 * 4. Returns a probability (0.0 to 1.0) of overspending risk
 */
public class OverspendingModel {

    // Model parameters loaded from model_weights.json
    private double[] coefficients;  // Learned weights for each feature
    private double intercept;       // Bias term
    private double[] scalerMean;    // Mean of each feature (for scaling)
    private double[] scalerScale;   // Std dev of each feature (for scaling)
    private String[] featureNames;  // Names of the features (for reference)

    // Risk threshold — above this probability = "at risk"
    private static final double RISK_THRESHOLD = 0.5;

    /**
     * Constructor: loads the model weights from assets/model_weights.json
     *
     * @param context Android context (usually 'this' from an Activity or getContext() from a Fragment)
     */
    public OverspendingModel(Context context) {
        loadModel(context);
    }

    /**
     * Reads model_weights.json from the assets folder and parses the weights.
     */
    private void loadModel(Context context) {
        try {
            // Read the JSON file from assets
            InputStream is = context.getAssets().open("model_weights.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            // Parse the JSON
            JSONObject obj = new JSONObject(json);

            // Load feature names
            JSONArray featuresArr = obj.getJSONArray("features");
            featureNames = new String[featuresArr.length()];
            for (int i = 0; i < featuresArr.length(); i++) {
                featureNames[i] = featuresArr.getString(i);
            }

            // Load coefficients (weights the model learned)
            JSONArray coefArr = obj.getJSONArray("coefficients");
            coefficients = new double[coefArr.length()];
            for (int i = 0; i < coefArr.length(); i++) {
                coefficients[i] = coefArr.getDouble(i);
            }

            // Load intercept (bias)
            intercept = obj.getDouble("intercept");

            // Load scaler parameters (needed to normalize input data)
            JSONArray meanArr = obj.getJSONArray("scaler_mean");
            scalerMean = new double[meanArr.length()];
            for (int i = 0; i < meanArr.length(); i++) {
                scalerMean[i] = meanArr.getDouble(i);
            }

            JSONArray scaleArr = obj.getJSONArray("scaler_scale");
            scalerScale = new double[scaleArr.length()];
            for (int i = 0; i < scaleArr.length(); i++) {
                scalerScale[i] = scaleArr.getDouble(i);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The sigmoid function — squishes any number into 0.0 to 1.0 range.
     * This is what makes logistic regression output a probability.
     *
     *          1
     * σ(z) = ———————
     *        1 + e^(-z)
     */
    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /**
     * Predicts the probability that a user is at risk of overspending.
     *
     * @param monthlyIncome   User's monthly income
     * @param financialAid    Financial aid received
     * @param food            Monthly food spending
     * @param transportation  Monthly transportation spending
     * @param entertainment   Monthly entertainment spending
     * @param personalCare    Monthly personal care spending
     * @param technology      Monthly technology spending
     * @param healthWellness  Monthly health & wellness spending
     * @param misc            Monthly miscellaneous spending
     * @return probability of overspending (0.0 = no risk, 1.0 = high risk)
     */
    public double predictRiskProbability(
            double monthlyIncome,
            double financialAid,
            double food,
            double transportation,
            double entertainment,
            double personalCare,
            double technology,
            double healthWellness,
            double misc
    ) {
        // Put the inputs into an array (same order as training features)
        double[] features = {
                monthlyIncome, financialAid, food, transportation,
                entertainment, personalCare, technology, healthWellness, misc
        };

        // STEP 1: Scale the features (same as StandardScaler in Python)
        // Formula: scaled = (value - mean) / std_dev
        double[] scaled = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            scaled[i] = (features[i] - scalerMean[i]) / scalerScale[i];
        }

        // STEP 2: Calculate weighted sum (dot product + intercept)
        // z = intercept + (coef[0] * scaled[0]) + (coef[1] * scaled[1]) + ...
        double z = intercept;
        for (int i = 0; i < coefficients.length; i++) {
            z += coefficients[i] * scaled[i];
        }

        // STEP 3: Apply sigmoid to get probability
        return sigmoid(z);
    }

    /**
     * Simple yes/no prediction: is the user at risk?
     *
     * @return true if at risk of overspending, false if not
     */
    public boolean isAtRisk(
            double monthlyIncome,
            double financialAid,
            double food,
            double transportation,
            double entertainment,
            double personalCare,
            double technology,
            double healthWellness,
            double misc
    ) {
        double probability = predictRiskProbability(
                monthlyIncome, financialAid, food, transportation,
                entertainment, personalCare, technology, healthWellness, misc
        );
        return probability >= RISK_THRESHOLD;
    }

    /**
     * Returns a human-readable risk level based on the probability.
     *
     * @return "Low Risk", "Medium Risk", or "High Risk"
     */
    public String getRiskLevel(
            double monthlyIncome,
            double financialAid,
            double food,
            double transportation,
            double entertainment,
            double personalCare,
            double technology,
            double healthWellness,
            double misc
    ) {
        double probability = predictRiskProbability(
                monthlyIncome, financialAid, food, transportation,
                entertainment, personalCare, technology, healthWellness, misc
        );

        if (probability < 0.3) {
            return "Low Risk";
        } else if (probability < 0.7) {
            return "Medium Risk";
        } else {
            return "High Risk";
        }
    }

    /**
     * Returns the feature names the model expects (for debugging/display).
     */
    public String[] getFeatureNames() {
        return featureNames;
    }
}

/**
 *
 * Example use of model
 *
 * // Create the model (loads weights from assets)
 * // Predict the risk for a user's spending data
 * double riskProbability = model.predictRiskProbability(
 *     1500,   // monthly income
 *     300,    // financial aid
 *     350,    // food
 *     100,    // transportation
 *     200,    // entertainment
 *     80,     // personal care
 *     150,    // technology
 *     100,    // health & wellness
 *     50      // misc
 * );
 *
 * // Use the results
 * boolean atRisk = model.isAtRisk(1500, 300, 350, 100, 200, 80, 150, 100, 50);
 * String level = model.getRiskLevel(1500, 300, 350, 100, 200, 80, 150, 100, 50);
 *
 * // Display it
 * riskText.setText(String.format("Risk: %.1f%% - %s", riskProbability * 100, level));
 *
 *
 *
 */
