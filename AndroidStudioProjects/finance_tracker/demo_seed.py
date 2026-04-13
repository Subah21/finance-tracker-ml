"""
demo data for the Finance Tracker presentation.

It creates a demo user and inserts realistic fake transactions so the
dashboard shows real-looking data with ML predictions working.

"""

import requests
import json

API = "http://localhost:8000"

DEMO_UID      = "pr1V1BYxG4XZJsO4fmFptGJXQh22"   # fake Firebase UID for testing
DEMO_EMAIL    = "demo@financetracker.com"
DEMO_INCOME   = 1200.0               # realistic student monthly income

print("=" * 50)
print("  Finance Tracker — Demo Setup Script")
print("=" * 50)

# Create the demo user 
print("\n[1] Creating demo user...")
try:
    r = requests.post(f"{API}/users", json={
        "firebase_uid":   DEMO_UID,
        "email":          DEMO_EMAIL,
        "username":       "Demo Student",
        "monthly_income": DEMO_INCOME
    })
    if r.status_code in [200, 201]:
        user = r.json()
        print(f"    User created: {user['email']} (id={user['id']})")
    else:
        print(f"    User may already exist: {r.status_code} — continuing...")
except Exception as e:
    print(f"    Error: {e}")
    print("    Make sure backend_API is running on port 8000!")
    exit(1)

# Insert realistic transactions
print("\n[2] Inserting demo transactions...")

transactions = [
    # Housing / recurring bills
    {"amount": 450.00, "category": "housing",       "description": "Monthly rent",          "is_recurring": True},
    {"amount": 45.00,  "category": "technology",    "description": "Phone bill",             "is_recurring": True},
    {"amount": 15.00,  "category": "technology",    "description": "Netflix subscription",   "is_recurring": True},
    {"amount": 12.00,  "category": "technology",    "description": "Spotify subscription",   "is_recurring": True},

    # Food
    {"amount": 85.00,  "category": "food",          "description": "Kroger groceries",       "is_recurring": False},
    {"amount": 12.50,  "category": "food",          "description": "Chipotle",               "is_recurring": False},
    {"amount": 67.00,  "category": "food",          "description": "Meijer grocery run",     "is_recurring": False},
    {"amount": 8.75,   "category": "food",          "description": "Starbucks",              "is_recurring": False},
    {"amount": 22.00,  "category": "food",          "description": "Pizza delivery",         "is_recurring": False},

    # Transportation
    {"amount": 38.00,  "category": "transport",     "description": "Gas station",            "is_recurring": False},
    {"amount": 14.50,  "category": "transport",     "description": "Uber ride",              "is_recurring": False},
    {"amount": 14.50,  "category": "transport",     "description": "Uber ride",              "is_recurring": False},

    # Entertainment
    {"amount": 25.00,  "category": "entertainment", "description": "Movie tickets",          "is_recurring": False},
    {"amount": 35.00,  "category": "entertainment", "description": "Concert ticket",         "is_recurring": False},
    {"amount": 18.00,  "category": "entertainment", "description": "Video game purchase",    "is_recurring": False},

    # Personal care / health
    {"amount": 28.00,  "category": "personal_care", "description": "Haircut",               "is_recurring": False},
    {"amount": 45.00,  "category": "health",        "description": "Gym membership",         "is_recurring": True},
    {"amount": 15.00,  "category": "health",        "description": "Pharmacy",               "is_recurring": False},

    # Misc
    {"amount": 32.00,  "category": "misc",          "description": "Amazon order",           "is_recurring": False},
    {"amount": 19.00,  "category": "misc",          "description": "School supplies",        "is_recurring": False},
]

success = 0
for tx in transactions:
    try:
        r = requests.post(f"{API}/transactions", json={
            "firebase_uid": DEMO_UID,
            "amount":       tx["amount"],
            "category":     tx["category"],
            "description":  tx["description"],
            "is_recurring": tx["is_recurring"]
        })
        if r.status_code in [200, 201]:
            success += 1
            print(f"    + ${tx['amount']:.2f} {tx['category']:<15} — {tx['description']}")
        else:
            print(f"    Failed: {tx['description']} — {r.status_code}")
    except Exception as e:
        print(f"    Error on {tx['description']}: {e}")

print(f"\n    {success}/{len(transactions)} transactions inserted.")

# Set budget limits
print("\n[3] Setting budget limits...")

budgets = [
    {"category": "food",          "limit_amount": 250.0},
    {"category": "housing",       "limit_amount": 500.0},
    {"category": "transport",     "limit_amount": 80.0},
    {"category": "entertainment", "limit_amount": 60.0},
    {"category": "health",        "limit_amount": 70.0},
    {"category": "misc",          "limit_amount": 50.0},
]

for b in budgets:
    try:
        r = requests.post(f"{API}/budget", json={
            "firebase_uid": DEMO_UID,
            "category":     b["category"],
            "limit_amount": b["limit_amount"]
        })
        if r.status_code in [200, 201]:
            print(f"    Budget set: {b['category']:<15} = ${b['limit_amount']:.0f}")
    except Exception as e:
        print(f"    Error: {e}")

# Check spending summary
print("\n[4] Verifying spending summary...")
try:
    r = requests.get(f"{API}/transactions/{DEMO_UID}/summary")
    if r.status_code == 200:
        summary = r.json()
        print(f"    Month:          {summary.get('month', 'N/A')}")
        print(f"    Monthly income: ${summary.get('monthly_income', 0):.2f}")
        print(f"    Total spent:    ${summary.get('total_spent', 0):.2f}")
        print(f"    Safe to spend:  ${summary.get('safe_to_spend', 0):.2f}")
        print(f"    By category:")
        for cat, amt in summary.get("by_category", {}).items():
            print(f"      {cat:<18} ${amt:.2f}")
except Exception as e:
    print(f"    Error: {e}")

# Test ML predictions
print("\n[5] Testing ML predictions...")
try:
    r = requests.post(f"{API}/predict", json={
        "firebase_uid":        DEMO_UID,
        "total_spent":         900.25,
        "food_spend":          195.25,
        "entertainment_spend": 78.00,
        "discretionary_spend": 165.00,
        "monthly_totals":      []
    })
    if r.status_code == 200:
        result = r.json()
        if "user_type" in result:
            ut = result["user_type"]
            print(f"    User type:  {ut.get('label', 'N/A')}")
            print(f"    Tip:        {ut.get('tip', 'N/A')}")
        if "risk" in result:
            rk = result["risk"]
            print(f"    At risk:    {rk.get('at_risk', 'N/A')}")
            print(f"    Risk %:     {rk.get('risk_percent', 'N/A')}%")
            print(f"    Message:    {rk.get('message', 'N/A')}")
    else:
        print(f"    ML server response: {r.status_code} — {r.text[:100]}")
except Exception as e:
    print(f"    Error: {e}")

print("\n" + "=" * 50)
print("  Demo setup complete!")
print("=" * 50)
print(f"\n  Demo login details:")
print(f"  Firebase UID: {DEMO_UID}")
print(f"  Email:        {DEMO_EMAIL}")
print(f"  Income:       ${DEMO_INCOME:.0f}/month")
print(f"\n  NOTE: Create this Firebase account manually in")
print(f"  Firebase Console OR use your existing test account")
print(f"  and update DEMO_UID to match your real Firebase UID.")
print(f"\n  To get your real Firebase UID:")
print(f"  Firebase Console --> Authentication --> Users --> copy UID")
