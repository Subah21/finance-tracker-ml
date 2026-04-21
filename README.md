# Finance Tracker ML

An AI-powered budget management Android app built for students. Tracks spending, predicts overspending risk using machine learning, and warns users when they are near a store while at financial risk using GPS location services.

Built by Team: Sabah Al-Haidari, Daniel Gekonde, Omar Abdelmoneim, Rahil Patel — MSU CSE 476, Spring 2026.

---

## Live Backend

The backend API is deployed and always running on Google Cloud Run:

```
https://finance-tracker-api-870049862947.us-central1.run.app
```

No local server setup is needed to run the Android app. The app points to this URL by default.

---

## Tech Stack

**Android**
- Java + XML Layouts
- Firebase Authentication
- OkHttp for REST API calls
- FusedLocationProviderClient (GPS)
- RecyclerView, Material Design components
- On-device Logistic Regression via `model_weights.json`

**Backend API** (port 8000)
- Python, FastAPI, SQLAlchemy
- PostgreSQL (production) / SQLite (local dev)
- Deployed on Google Cloud Run

**ML Server** (port 8001)
- scikit-learn — K-Means Clustering, Logistic Regression
- Prophet (Meta) — time-series spending forecasting
- Trained on 2,700 rows of student spending data

**Authentication**
- Firebase Authentication (email/password + email verification)

---

## Features

### Authentication
- Email and password login via Firebase
- Email verification required before first access
- Auto-login for returning verified users
- "Try Demo" button for instant dashboard access

### Overview Tab
- Safe to Spend — income minus all monthly spending
- Overspending Risk % — Logistic Regression model (98.9% accuracy)
- Spending Type — K-Means clustering: Saver / Balanced / Spender
- Prophet forecast for next month's predicted spending

### Transactions Tab
- Full scrollable list of all transactions
- Add Transaction dialog — amount, category dropdown, optional description
- Real-time refresh after adding a transaction
- Total spent this month shown at the top

### Budget Tab
- Per-category spending progress bars (Food, Transport, Entertainment)
- Monthly Budget, Spent, and Remaining calculated live
- Set Budget Limit dialog — updates limits instantly via the API

### GPS Budget Mode
- FusedLocationProviderClient polls location every 15 seconds
- Detects proximity to malls, Walmart, Target, Best Buy, Kroger, and more
- Runs on-device Logistic Regression model — no server call needed
- Fires a push notification and shows a red warning banner when overspending risk is high

### Machine Learning Models
| Model | Type | Purpose |
|-------|------|---------|
| K-Means Clustering | Unsupervised | Classifies user as Saver, Balanced, or Spender |
| Logistic Regression | Supervised | Predicts overspending risk 0–100% |
| Prophet | Time-series | Forecasts next month's spending |

---

## Project Structure

```
finance_tracker/
├── app/
│   └── src/main/
│       ├── java/edu/msu/cse476/haidaris/finance_tracker/
│       │   ├── LoginActivity.java
│       │   ├── SignupActivity.java
│       │   ├── DashboardActivity.java
│       │   ├── OverviewFragment.java
│       │   ├── TransactionsFragment.java
│       │   ├── TransactionAdapter.java
│       │   ├── BudgetFragment.java
│       │   ├── LocationHelper.java
│       │   ├── NotificationHelper.java
│       │   ├── OverspendingModel.java
│       │   └── ApiClient.java
│       ├── assets/
│       │   └── model_weights.json        ← on-device ML model weights
│       ├── res/
│       │   ├── layout/
│       │   └── values/strings.xml
│       └── AndroidManifest.xml
│
├── backend_API/
│   ├── main.py                           ← all REST endpoints
│   ├── database.py                       ← SQLAlchemy models + PostgreSQL/SQLite support
│   ├── schemas.py                        ← Pydantic request/response models
│   ├── requirements.txt
│   └── Procfile
│
├── backend_ML_models/
│   ├── main.py                           ← ML prediction endpoints
│   ├── model.py                          ← model training script
│   ├── train_model.py                    ← loads CSVs and trains all 3 models
│   ├── datasets/
│   │   ├── student_spending.csv
│   │   └── genz_money_spends.csv
│   └── requirements.txt
│
├── demo_seed.py                          ← seeds database with realistic demo data
└── README.md
```

---

## Setup Instructions

### 1 — Clone the Repository

```bash
git clone https://github.com/Subah21/finance-tracker-ml.git
cd finance-tracker-ml/AndroidStudioProjects/finance_tracker
```

### 2 — Add Firebase Configuration

Firebase requires a configuration file. To get it:

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Open the Finance Tracker project
3. Click the gear icon → Project Settings
4. Scroll to Your Apps → select the Android app
5. Click **Download google-services.json**
6. Place it at:

```
finance_tracker/app/google-services.json
```

### 3 — Open in Android Studio

Open the `finance_tracker` folder in Android Studio.

### 4 — Sync Gradle

```
File → Sync Project with Gradle Files
```

### 5 — Run the App

Run on an emulator or real Android device. The app connects to the deployed Cloud Run backend automatically — no local servers needed.

---

## Running the Backend Locally (Optional)

If you want to run the backend on your own machine instead of using Cloud Run:

### backend_API (port 8000)

```bash
cd backend_API
python -m venv venv
venv\Scripts\activate        # Windows
source venv/bin/activate     # Mac/Linux
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### backend_ML_models (port 8001)

```bash
cd backend_ML_models
pip install -r requirements.txt
python model.py              # train models first (generates .pkl files)
uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

Then update `ApiClient.java`:

```java
// For emulator
private static final String BASE_URL = "http://10.0.2.2:8000";

// For real device (use your laptop's IP)
private static final String BASE_URL = "http://YOUR_IP:8000";
```

---

## Seeding Demo Data

To populate the database with realistic fake transactions for testing:

```bash
pip install requests
python demo_seed.py
```

This creates a demo user and inserts 20 transactions across 8 spending categories, sets budget limits, and verifies the ML predictions are working.

Update `DEMO_UID` in `demo_seed.py` to match your Firebase UID before running.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/users` | Create or return existing user |
| GET | `/users/{uid}` | Get user profile |
| POST | `/transactions` | Log a new transaction |
| GET | `/transactions/{uid}` | Get all transactions for user |
| GET | `/transactions/{uid}/summary` | Monthly totals by category + safe to spend |
| POST | `/budget` | Set a category spending limit |
| GET | `/budget/{uid}` | Get all budget limits for user |
| POST | `/predict` | Run all 3 ML models and return results |
| POST | `/visits` | Log a GPS store visit |

Interactive API docs available at:
```
https://finance-tracker-api-870049862947.us-central1.run.app/docs
```

---

## Firebase Setup for Teammates

After cloning, each team member must add their own `google-services.json`:

1. Go to Firebase Console → Project Settings → Your Apps → Android app
2. Download `google-services.json`
3. Place it inside `finance_tracker/app/`
4. Sync Gradle

If you get a build error about Firebase not being configured, this file is missing.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Could not reach server` | Check `ApiClient.java` BASE_URL — should point to Cloud Run URL |
| `CLEARTEXT communication not permitted` | Add `android:usesCleartextTraffic="true"` to AndroidManifest.xml |
| Firebase skips login screen | Tap ⋮ menu → Logout to clear the cached session |
| ML shows N/A | Make sure backend_ML_models server is running on port 8001 |
| `google-services.json` missing | Download from Firebase Console and place in `app/` folder |
| Gradle sync fails | File → Sync Project with Gradle Files |

---

## Team Contributions

| Member | Role | Key Contributions |
|--------|------|-------------------|
| Sabah Al-Haidari | Team Lead / Backend | FastAPI backend, Firebase auth, GPS feature, K-Means Clustering Model, Cloud Run deployment, demo seed script |
| Daniel Gekonde | ML / Cloud | Logistics Regression Model, on-device model weights, Google Cloud Run + PostgreSQL, Add Transaction |
| Omar Abdelmoneim | Frontend UI | Budget & Transactions tab UI, fragment layouts, progress bars, navigation |
| Rahil Patel | Features / QA | Prophet Model, Budget fragment wiring, Set Budget dialog, strings.xml cleanup, UI polish |
