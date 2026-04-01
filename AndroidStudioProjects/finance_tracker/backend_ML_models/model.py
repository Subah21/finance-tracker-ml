"""
Trains and saves all 3 ML models for the Finance Tracker app.

  1. Calls train_model.py to load and prepares the data
  2. Trains each model on that data
  3. Saves each model as a .pkl file so FastAPI can load and use
     them without retraining every single time a request comes in

THE 3 MODELS:

  Model 1 — K-Means Clustering
    What it does: groups users into spending personality
    types: Saver, Balanced, or Spender
    Type: Unsupervised (no label needed — finds patterns)
    Output: "Saver" / "Balanced" / "Spender"

  Model 2 — Prophet (Time-Series Forecasting)
    What it does: predicts next month's spending and gives
    budget advice based on historical spending patterns
    Type: Time-series forecasting
    Output: predicted dollar amount + budget tip

  Model 3 — Logistic Regression
    What it does: determines if a user is at risk of
    overspending that day or week
    Type: Binary classification (0 = safe, 1 = at risk)
    Output: risk flag + probability percentage

    RUN THIS FILE ONCE TO TRAIN EVERYTHING

"""

import pandas as pd
import numpy as np
import joblib

from sklearn.cluster import KMeans
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score

from train_model import load_and_prepare_data


# MODEL 1 — K-MEANS CLUSTERING
# the below comment was AI generated to better
# help others understand the significance of K_Means
# and how it's being implemented in our case.
def train_kmeans(df):
    """
    K-Means groups users into clusters based on their spending patterns.
    We use 3 clusters = 3 user types.

    WHY WE SCALE THE DATA FIRST:
    K-Means calculates distance between data points.
    If income is $3000 and spend_ratio is 0.9, the income number
    dominates just because it's bigger — not because it matters more.
    StandardScaler brings all features to the same scale (mean=0, std=1)
    so every feature contributes equally.

    HOW IT ASSIGNS LABELS (Saver / Balanced / Spender):
    After training, we look at the average spend_ratio of each cluster.
    The cluster with the lowest avg spend_ratio = Saver.
    The middle one = Balanced.
    The highest = Spender.
    """
    print("\n" + "─"*50)
    print("MODEL 1 — K-Means Clustering")
    print("─"*50)

    # Features K-Means uses to group users
    # spend_ratio = how much of income is spent
    # discretionary_spend = entertainment + personal care + etc
    # entertainment = specifically fun spending
    # food = daily necessity spending
    features = ["spend_ratio", "discretionary_spend",
                "entertainment", "food"]

    X = df[features].copy()

    # Scale all features to the same range
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    # we train the K-Means with 3 clusters
    # n_init=10 means it tries 10 different starting points and
    # picks the best result. Also, avoids getting stuck in a bad solution
    model = KMeans(n_clusters=3, random_state=42, n_init=10)
    model.fit(X_scaled)

    # Assign cluster numbers back to the dataframe
    df["cluster"] = model.labels_

    # Figure out which cluster number = which label
    # Sort clusters by their average spend_ratio (low to high)
    cluster_avg_spend = (df.groupby("cluster")["spend_ratio"]
                           .mean()
                           .sort_values())

    label_map = {
        int(cluster_avg_spend.index[0]): "Saver",
        int(cluster_avg_spend.index[1]): "Balanced",
        int(cluster_avg_spend.index[2]): "Spender"
    }

    # Print a breakdown of each cluster
    print("\nCluster results:")
    for cluster_id, label in label_map.items():
        group     = df[df["cluster"] == cluster_id]
        count     = len(group)
        avg_ratio = group["spend_ratio"].mean()
        avg_disc  = group["discretionary_spend"].mean()
        print(f"  {label:10s} → {count:4d} users | "
              f"avg spend ratio: {avg_ratio:.2f} | "
              f"avg discretionary: ${avg_disc:.0f}")

    # Save everything needed to make predictions later
    joblib.dump(model,     "kmeans_model.pkl")
    joblib.dump(scaler,    "kmeans_scaler.pkl")
    joblib.dump(label_map, "kmeans_labels.pkl")
    print("\nSaved: kmeans_model.pkl, kmeans_scaler.pkl, kmeans_labels.pkl")

    return model, scaler, label_map


# MODEL 2 — PROPHET FORECASTING

# the below comment was AI generated
# to help explain what prophet is and how to use it.

def train_prophet(df):
    """
    Prophet is a forecasting library made by Facebook/Meta.
    It is designed for time-series data/data that changes over time.

    HOW IT WORKS:
    Prophet needs a DataFrame with exactly two columns:
      ds = date (the time point)
      y  = value (what we're measuring — total spending)

    It learns patterns like:
      - Is spending going up or down over time? (trend)
      - Does spending spike at certain times of year? (seasonality)
    Then it predicts future values.

    WHAT WE DO WITH OUR DATA:
    Our CSVs don't have dates — each row is just one person's spending.
    So we simulate a 24-month history by treating each row as one month.
    This is a reasonable approach for a class project.

    INSTALL PROPHET FIRST:
        pip install prophet

    If not installed, this function skips gracefully.
    """
    print("\n" + "─"*50)
    print("MODEL 2 — Prophet Forecasting")
    print("─"*50)

    try:
        from prophet import Prophet
    except ImportError:
        print("  Prophet not installed.")
        print("  Run:  pip install prophet")
        print("  Then run model.py again.")
        return None

    # Use first 24 rows as a simulated 24-month spending history
    # In production, this would be replaced with real user history
    # pulled from the transactions table in the database
    monthly_spending = df["total_spent"].values[:24]

    # Build the required Prophet DataFrame
    dates = pd.date_range(start="2024-01-01", periods=24, freq="MS")
    prophet_df = pd.DataFrame({
        "ds": dates,
        "y":  monthly_spending
    })

    # Train Prophet
    # changepoint_prior_scale controls flexibility of the trend line
    # Lower value = smoother, more conservative forecast
    model = Prophet(
        yearly_seasonality=True,
        weekly_seasonality=False,
        daily_seasonality=False,
        changepoint_prior_scale=0.05
    )
    model.fit(prophet_df)

    # Forecast 3 months into the future
    future   = model.make_future_dataframe(periods=3, freq="MS")
    forecast = model.predict(future)

    # Show the 3 predicted months
    print("\nForecast for next 3 months (based on training data):")
    for _, row in forecast.tail(3).iterrows():
        print(f"  {row['ds'].strftime('%B %Y')}: "
              f"${row['yhat']:.2f}  "
              f"(range: ${row['yhat_lower']:.2f} – ${row['yhat_upper']:.2f})")

    joblib.dump(model, "prophet_model.pkl")
    print("\nSaved: prophet_model.pkl")
    return model


# MODEL 3 — LOGISTIC REGRESSION

def train_logistic_regression(df):
    """
    Logistic Regression predicts a binary outcome — yes or no.
    In our case: is this user at risk of overspending? (1 = yes, 0 = no)

    WHY LOGISTIC REGRESSION (not Linear Regression)?
    Linear Regression predicts a continuous number (like $450).
    Logistic Regression predicts a probability between 0 and 1,
    which we convert to a percentage risk score for the user.

    HOW IT WORKS:
    It learns a boundary — a combination of the input features that
    separates "safe spenders" from "at risk spenders".
    Once trained, for a new user it calculates which side of that
    boundary they fall on, and how far (= the probability).

    FEATURES USED:
      income              — how much they earn
      total_spent         — how much they've spent
      spend_ratio         — total_spent / income (most predictive)
      discretionary_spend — entertainment + personal + misc
      entertainment       — fun spending specifically
      food                — daily necessity spending

    OUTPUT:
      flag = 0 (not at risk) or 1 (at risk)
      probability = 0–100% chance of overspending
    """
    print("\n" + "─"*50)
    print("MODEL 3 — Logistic Regression")
    print("─"*50)

    feature_cols = ["income", "total_spent", "spend_ratio",
                    "discretionary_spend", "entertainment", "food"]

    X = df[feature_cols]
    y = df["overspending"]   # 0 or 1

    # 80% for training, 20% for testing
    # stratify=y. ensures both splits have the same ratio of 0s and 1s
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # Scale features. same reason as K-Means
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled  = scaler.transform(X_test)

    # Train the model
    # max_iter=1000 gives it enough steps to fully converge
    # and 42 is the magic number
    model = LogisticRegression(max_iter=1000, random_state=42)
    model.fit(X_train_scaled, y_train)

    # Evaluate on the test set
    preds    = model.predict(X_test_scaled)
    accuracy = accuracy_score(y_test, preds)

    print(f"\nAccuracy: {accuracy*100:.1f}%")
    print("\nDetailed report:")
    print(classification_report(y_test, preds,
          target_names=["Not at risk (0)", "At risk (1)"]))

    # Explain what the accuracy means in plain terms
    correct = int(accuracy * len(y_test))
    print(f"In plain terms: correctly predicted {correct} out of "
          f"{len(y_test)} test users.")

    # Save model, scaler, and feature list
    # FastAPI needs all 3 to make a prediction
    joblib.dump(model,        "logistic_model.pkl")
    joblib.dump(scaler,       "logistic_scaler.pkl")
    joblib.dump(feature_cols, "logistic_features.pkl")
    print("\nSaved: logistic_model.pkl, logistic_scaler.pkl, "
          "logistic_features.pkl")

    return model, scaler

# PREDICTION HELPERS
# These functions are imported by main.py (FastAPI) to run predictions
# We pass a pd.DataFrame (not a raw list) into scaler.transform().
# This avoids the sklearn "feature names" warning.

def predict_user_type(income, total_spent, food,
                      entertainment, discretionary_spend):
    """
    Uses the trained K-Means model to classify a user.
    Returns: "Saver", "Balanced", or "Spender"
    """
    model     = joblib.load("kmeans_model.pkl")
    scaler    = joblib.load("kmeans_scaler.pkl")
    label_map = joblib.load("kmeans_labels.pkl")

    spend_ratio = total_spent / income if income > 0 else 1.0

    X = pd.DataFrame([{
        "spend_ratio":         spend_ratio,
        "discretionary_spend": discretionary_spend,
        "entertainment":       entertainment,
        "food":                food
    }])

    cluster = int(model.predict(scaler.transform(X))[0])
    return label_map.get(cluster, "Balanced")


def predict_overspending_risk(income, total_spent, food,
                               entertainment, discretionary_spend):
    """
    Uses Logistic Regression to determine overspending risk.
    Returns: (flag, probability)
      flag        = 0 (safe) or 1 (at risk)
      probability = 0.0–100.0 (risk percentage shown to user)
    """
    model  = joblib.load("logistic_model.pkl")
    scaler = joblib.load("logistic_scaler.pkl")

    spend_ratio = total_spent / income if income > 0 else 1.0

    X = pd.DataFrame([{
        "income":              income,
        "total_spent":         total_spent,
        "spend_ratio":         spend_ratio,
        "discretionary_spend": discretionary_spend,
        "entertainment":       entertainment,
        "food":                food
    }])

    X_scaled    = scaler.transform(X)
    flag        = int(model.predict(X_scaled)[0])
    probability = round(float(model.predict_proba(X_scaled)[0][1]) * 100, 1)
    return flag, probability


def predict_next_month_spending(monthly_totals: list):
    """
    Uses Prophet to forecast next month's spending.

    Args:
        monthly_totals: list of the user's past monthly spending totals
                        e.g. [850.0, 920.0, 780.0, 1100.0, 950.0]
                        Minimum 2 values required.

    Returns dict with predicted_spending, lower_bound, upper_bound,
    trend direction, and budget advice string.
    """
    try:
        from prophet import Prophet
    except ImportError:
        return {"error": "Prophet not installed. Run: pip install prophet"}

    try:
        import os
        if not os.path.exists("prophet_model.pkl"):
            return {"error": "Prophet model not trained yet. Run model.py first."}

        # Build history DataFrame from the user's actual past data
        dates = pd.date_range(
            end=pd.Timestamp.today().replace(day=1),
            periods=len(monthly_totals),
            freq="MS"
        )
        history_df = pd.DataFrame({"ds": dates, "y": monthly_totals})

        # Re-fit Prophet on this user's personal history
        # Prophet requires re-fitting per user for accurate predictions
        prophet_model = Prophet(
            yearly_seasonality=False,
            weekly_seasonality=False,
            daily_seasonality=False,
            changepoint_prior_scale=0.05
        )
        prophet_model.fit(history_df)

        future   = prophet_model.make_future_dataframe(periods=1, freq="MS")
        forecast = prophet_model.predict(future)
        row      = forecast.iloc[-1]

        predicted = round(float(row["yhat"]), 2)
        lower     = round(float(row["yhat_lower"]), 2)
        upper     = round(float(row["yhat_upper"]), 2)

        avg_recent = sum(monthly_totals[-3:]) / min(3, len(monthly_totals))
        trend      = "up" if predicted > avg_recent else "down"

        advice = (
            "Your spending is trending up. Consider cutting "
            "discretionary costs this month."
            if trend == "up" else
            "Your spending is trending down — great financial habits!"
        )

        return {
            "predicted_spending": predicted,
            "lower_bound":        lower,
            "upper_bound":        upper,
            "trend":              trend,
            "advice":             advice
        }

    except Exception as e:
        return {"error": str(e)}


# run this file to train all models

if __name__ == "__main__":
    print("=" * 50)
    print("  Finance Tracker — Training all 3 ML models")
    print("=" * 50)

    # Step 1: Load and prepare data
    df = load_and_prepare_data()

    # Step 2: Train each model
    train_kmeans(df)
    train_prophet(df)             # skips if prophet not installed
    train_logistic_regression(df)

    print("\n" + "=" * 50)
    print("  All done! .pkl files saved.")
    print("  Next: run  uvicorn main:app --reload --port 8001")
    print("=" * 50)