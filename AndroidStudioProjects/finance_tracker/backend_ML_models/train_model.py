"""
Step 1 of the ML pipeline --> loads both CSV datasets, cleans them,
combines them into one unified DataFrame, and returns it ready for
all 3 models to use.
"""

import pandas as pd
import numpy as np


def load_and_prepare_data():
    """
    Loads both CSV files, standardizes their column names,
    calculates derived columns (total_spent, safe_to_spend,
    spend_ratio, overspending label), and combines them.

    Returns a clean DataFrame with these columns:
        income, housing, food, entertainment, personal_care,
        transport, technology, health, misc, total_spent,
        safe_to_spend, spend_ratio, discretionary_spend, overspending
    """

    # Load CSV 1: student_spending
    df1 = pd.read_csv("datasets/student_spending (1).csv")

    # Rename columns to standard names
    s1 = pd.DataFrame()
    s1["income"]        = df1["monthly_income"]
    s1["housing"]       = df1["housing"]
    s1["food"]          = df1["food"]
    s1["entertainment"] = df1["entertainment"]
    s1["personal_care"] = df1["personal_care"]
    s1["transport"]     = df1["transportation"]
    s1["technology"]    = df1["technology"]
    s1["health"]        = df1["health_wellness"]
    s1["misc"]          = df1["miscellaneous"]

    # Load CSV 2: genz_money_spends
    df2 = pd.read_csv("datasets/genz_money_spends.csv")

    s2 = pd.DataFrame()
    s2["income"]        = df2["Income (USD)"]
    s2["housing"]       = df2["Rent (USD)"]
    s2["food"]          = df2["Groceries (USD)"] + df2["Eating Out (USD)"]
    s2["entertainment"] = df2["Entertainment (USD)"] + df2["Online Shopping (USD)"]
    s2["personal_care"] = df2["Fitness (USD)"]
    s2["transport"]     = df2["Travel (USD)"]
    s2["technology"]    = df2["Subscription Services (USD)"]
    s2["health"]        = df2["Education (USD)"]
    s2["misc"]          = df2["Miscellaneous (USD)"]

    # Combine both datasets
    df = pd.concat([s1, s2], ignore_index=True)

    # Calculate derived columns

    # All spending categories combined
    spend_cols = ["housing", "food", "entertainment",
                  "personal_care", "transport", "technology",
                  "health", "misc"]
    df["total_spent"] = df[spend_cols].sum(axis=1)

    # How much is safely left after spending, and it's floored at zero
    df["safe_to_spend"] = (df["income"] - df["total_spent"]).clip(lower=0)

    # Ratio of income spent. 1.0 means spent everything, 2.0 means spent 2x income
    df["spend_ratio"] = df["total_spent"] / df["income"]

    # Discretionary means the "nice to have" spending (not rent or necessities/bills)
    df["discretionary_spend"] = (df["entertainment"] +
                                  df["personal_care"] +
                                  df["misc"])

    # Overspending label: 1 if spent more than 90% of income, else 0
    # Used by Logistic Regression as the thing to predict
    df["overspending"] = (df["total_spent"] > df["income"] * 0.9).astype(int)

    print(f"Data loaded: {len(df)} total rows")
    print(f"  — student_spending: {len(s1)} rows")
    print(f"  — genz_money_spends: {len(s2)} rows")
    print(f"  — Overspending cases: {df['overspending'].sum()} "
          f"({df['overspending'].mean()*100:.1f}%)")
    print(f"  — Avg income:  ${df['income'].mean():.0f}")
    print(f"  — Avg spent:   ${df['total_spent'].mean():.0f}")
    print(f"  — Avg safe:    ${df['safe_to_spend'].mean():.0f}")

    return df


# Run this file directly to preview the data
if __name__ == "__main__":
    df = load_and_prepare_data()
    print("\nFirst 3 rows:")
    print(df[["income", "total_spent", "safe_to_spend",
               "spend_ratio", "overspending"]].head(3))