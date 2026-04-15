"""
demo_seed.py — Seeds the Finance Tracker backend with a fake demo user
using realistic student spending data from student.csv.

Usage:
    1. Make sure the FastAPI backend is running:
           uvicorn main:app --reload --port 8000
    2. Run this script:
           python demo_seed.py

It will:
    - Create a fake demo user via POST /users
    - Pick a random student row from the CSV for realistic numbers
    - POST a month's worth of transactions across categories
    - POST budget limits for each spending category
"""

import csv
import random
import requests
import os
import sys
from datetime import datetime, timedelta

# ─── Config ───────────────────────────────────────────────────────────
BASE_URL = os.getenv("SEED_URL", "https://finance-tracker-api-870049862947.us-central1.run.app")

# Path to the CSV file (adjust if needed)
CSV_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "student.csv")
# Fallback: try Downloads folder
if not os.path.exists(CSV_PATH):
    CSV_PATH = os.path.expanduser("~/Downloads/student.csv")

# Demo user credentials (fake firebase UID — not a real Firebase account)
DEMO_USER = {
    "firebase_uid": "demo_user_12345",
    "email": "demo@financetracker.test",
    "username": "DemoStudent",
    "monthly_income": 0.0  # will be set from CSV data
}

# Maps CSV columns → our API transaction categories
CATEGORY_MAP = {
    "Housing":           "housing",
    "Food":              "food",
    "Transportation":    "transportation",
    "Books & Supplies":  "education",
    "Entertainment":     "entertainment",
    "Personal Care":     "personal",
    "Technology":        "technology",
    "Health & Wellness": "health",
    "Misc":              "other",
}

# Example descriptions for each category to make transactions look real
DESCRIPTIONS = {
    "housing":        ["Rent payment", "Utilities bill", "Internet bill", "Apartment deposit"],
    "food":           ["Kroger groceries", "Chipotle lunch", "Starbucks coffee", "Pizza delivery",
                       "Campus dining", "Trader Joe's run", "Late night snack"],
    "transportation": ["Gas station", "Uber ride", "Bus pass", "Lyft to campus", "Parking meter"],
    "education":      ["Textbook - Intro to Psych", "Lab notebook", "Graphing calculator",
                       "Online course materials", "Scantron sheets"],
    "entertainment":  ["Netflix subscription", "Movie tickets", "Spotify Premium",
                       "Concert tickets", "Video game"],
    "personal":       ["Haircut", "Toiletries", "Laundry detergent", "New shoes"],
    "technology":     ["Phone case", "USB-C cable", "Cloud storage plan", "App subscription"],
    "health":         ["Gym membership", "Pharmacy", "Doctor copay", "Vitamins", "First aid kit"],
    "other":          ["Amazon order", "Gift for friend", "Club dues", "Printing costs"],
}


def load_csv_data(csv_path: str) -> list[dict]:
    """Load all student rows from the CSV."""
    with open(csv_path, newline="") as f:
        reader = csv.DictReader(f)
        return list(reader)


def pick_student(rows: list[dict]) -> dict:
    """Pick a random student row from the CSV."""
    return random.choice(rows)


def split_into_transactions(category: str, total: float, num_txns: int) -> list[dict]:
    """
    Split a monthly category total into multiple individual transactions
    so it looks like real spending behavior instead of one lump sum.
    """
    if total <= 0 or num_txns <= 0:
        return []

    # Generate random split weights
    weights = [random.random() for _ in range(num_txns)]
    weight_sum = sum(weights)
    amounts = [round(total * w / weight_sum, 2) for w in weights]

    # Fix rounding so amounts sum exactly to total
    diff = round(total - sum(amounts), 2)
    amounts[0] = round(amounts[0] + diff, 2)

    descs = DESCRIPTIONS.get(category, ["Purchase"])
    transactions = []
    for amt in amounts:
        if amt > 0:
            transactions.append({
                "amount": amt,
                "category": category,
                "description": random.choice(descs),
                "is_recurring": category in ("housing", "entertainment") and random.random() < 0.3,
            })
    return transactions


def check_server():
    """Make sure the backend is actually running."""
    try:
        r = requests.get(f"{BASE_URL}/")
        r.raise_for_status()
        print(f"✅ Backend is running at {BASE_URL}")
    except requests.ConnectionError:
        print(f"❌ Cannot connect to {BASE_URL}")
        print("   Make sure the backend is running:")
        print("       cd backend_API && uvicorn main:app --reload --port 8000")
        sys.exit(1)


def create_demo_user(monthly_income: float) -> dict:
    """Create the demo user via POST /users."""
    payload = {**DEMO_USER, "monthly_income": monthly_income}
    r = requests.post(f"{BASE_URL}/users", json=payload)

    if r.status_code == 200:
        user = r.json()
        print(f"✅ User created: {user['username']} (uid: {user['firebase_uid']})")
        print(f"   Monthly income: ${user['monthly_income']:.2f}")

        # Update income if user already existed with different income
        if user["monthly_income"] != monthly_income:
            r2 = requests.put(
                f"{BASE_URL}/users/{DEMO_USER['firebase_uid']}",
                json={"monthly_income": monthly_income}
            )
            if r2.status_code == 200:
                print(f"   Updated income to ${monthly_income:.2f}")
        return user
    else:
        print(f"❌ Failed to create user: {r.status_code} — {r.text}")
        sys.exit(1)


def seed_transactions(firebase_uid: str, student: dict):
    """Create transactions from the student's spending data."""
    count = 0
    total_spent = 0.0

    for csv_col, api_category in CATEGORY_MAP.items():
        raw = student.get(csv_col, "0")
        monthly_total = float(raw)

        if monthly_total <= 0:
            continue

        # Decide how many individual transactions to split into
        if api_category == "housing":
            num_txns = random.randint(1, 3)   # rent is usually 1-3 payments
        elif api_category == "food":
            num_txns = random.randint(8, 20)  # students buy food frequently
        else:
            num_txns = random.randint(2, 7)

        txns = split_into_transactions(api_category, monthly_total, num_txns)

        for txn in txns:
            payload = {"firebase_uid": firebase_uid, **txn}
            r = requests.post(f"{BASE_URL}/transactions", json=payload)
            if r.status_code == 200:
                count += 1
                total_spent += txn["amount"]
            else:
                print(f"   ⚠️  Failed transaction: {r.status_code} — {r.text}")

    print(f"✅ Created {count} transactions (total: ${total_spent:.2f})")
    return total_spent


def seed_budgets(firebase_uid: str, student: dict):
    """Set budget limits based on student's actual spending (with some buffer)."""
    count = 0

    for csv_col, api_category in CATEGORY_MAP.items():
        raw = student.get(csv_col, "0")
        monthly_total = float(raw)

        if monthly_total <= 0:
            continue

        # Set budget limit slightly above actual spending (10-30% buffer)
        buffer = random.uniform(1.10, 1.30)
        limit_amount = round(monthly_total * buffer, 2)

        payload = {
            "firebase_uid": firebase_uid,
            "category": api_category,
            "limit_amount": limit_amount
        }
        r = requests.post(f"{BASE_URL}/budget", json=payload)
        if r.status_code == 200:
            count += 1
        else:
            print(f"   ⚠️  Failed budget: {r.status_code} — {r.text}")

    print(f"✅ Set {count} budget limits")


def seed_location_visits(firebase_uid: str):
    """Add a few fake location visits near East Lansing (MSU area)."""
    visits = [
        {"store_name": "Meijer",      "category": "grocery",    "lat": 42.7370, "lng": -84.4839},
        {"store_name": "Target",      "category": "grocery",    "lat": 42.7018, "lng": -84.4857},
        {"store_name": "Chipotle",    "category": "restaurant", "lat": 42.7340, "lng": -84.4810},
        {"store_name": "Starbucks",   "category": "restaurant", "lat": 42.7355, "lng": -84.4825},
        {"store_name": "Barnes & Noble", "category": "shopping", "lat": 42.7345, "lng": -84.4805},
    ]

    count = 0
    for visit in visits:
        # Add some randomness to visit frequency
        num_visits = random.randint(1, 4)
        for _ in range(num_visits):
            payload = {"firebase_uid": firebase_uid, **visit}
            r = requests.post(f"{BASE_URL}/visits", json=payload)
            if r.status_code == 200:
                count += 1

    print(f"✅ Logged {count} location visits")


def print_summary(firebase_uid: str):
    """Print the spending summary from the API to verify everything worked."""
    r = requests.get(f"{BASE_URL}/transactions/{firebase_uid}/summary")
    if r.status_code == 200:
        data = r.json()
        print("\n" + "=" * 50)
        print(f"📊  SPENDING SUMMARY — {data['month']}")
        print("=" * 50)
        print(f"  Monthly Income:  ${data['monthly_income']:>10.2f}")
        print(f"  Total Spent:     ${data['total_spent']:>10.2f}")
        print(f"  Safe to Spend:   ${data['safe_to_spend']:>10.2f}")
        print("-" * 50)
        for cat, amt in sorted(data["by_category"].items(), key=lambda x: -x[1]):
            print(f"  {cat:<20s} ${amt:>10.2f}")
        print("=" * 50)


def main():
    print("\n🚀 Finance Tracker — Demo Seed Script")
    print("=" * 50)

    # 1. Check server is running
    check_server()

    # 2. Load CSV data
    if not os.path.exists(CSV_PATH):
        print(f"❌ CSV not found at: {CSV_PATH}")
        print("   Place student.csv in the project root or ~/Downloads/")
        sys.exit(1)

    rows = load_csv_data(CSV_PATH)
    print(f"📄 Loaded {len(rows)} students from CSV")

    # 3. Pick a random student
    student = pick_student(rows)
    income = float(student.get("Monthly Income", 1500))
    print(f"🎲 Selected: {student.get('Year', '?')} {student.get('Major', '?')} student, "
          f"income=${income:.0f}")

    # 4. Create user
    create_demo_user(income)
    uid = DEMO_USER["firebase_uid"]

    # 5. Seed transactions
    seed_transactions(uid, student)

    # 6. Seed budgets
    seed_budgets(uid, student)

    # 7. Seed location visits
    seed_location_visits(uid)

    # 8. Print summary
    print_summary(uid)

    print(f"\n✅ Done! Use firebase_uid = '{uid}' to view this user in the app.")
    print(f"   API docs: {BASE_URL}/docs\n")


if __name__ == "__main__":
    main()
