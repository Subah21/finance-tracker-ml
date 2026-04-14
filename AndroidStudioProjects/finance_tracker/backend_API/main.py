"""
The main API gateway. Android talks ONLY to this server, port 8000.
This server talks to the ML server, port 8001, for predictions.

Run:  uvicorn main:app --reload --port 8000
Docs: http://localhost:8000/docs
"""

import httpx
from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy.orm import Session

from database import get_db, init_db, User, Transaction, LocationVisit, BudgetLimit
from schemas import (
    UserCreate, UserUpdate, UserOut,
    TransactionCreate, TransactionOut,
    LocationVisitCreate, LocationVisitOut,
    BudgetLimitCreate, BudgetLimitOut,
    PredictionRequest
)

app = FastAPI(title="Finance Tracker API", version="1.0")

# ML server URL — reads from environment variable in Cloud Run,
# falls back to localhost for local development
import os
ML_SERVER = os.getenv("ML_SERVER_URL", "http://localhost:8001")


# Create all DB tables when server starts
@app.on_event("startup")
def startup():
    init_db()
    print("Database tables created / verified.")


@app.get("/")
def root():
    return {"status": "Finance Tracker API running", "docs": "/docs"}


# USER ROUTES


@app.post("/users", response_model=UserOut)
def create_user(data: UserCreate, db: Session = Depends(get_db)):
    """
    Called right after Firebase signup succeeds on Android.
    Saves the user's profile to our SQL database.
    """
    existing = db.query(User).filter(User.firebase_uid == data.firebase_uid).first()
    if existing:
        return existing  # if the user is already registered, just return them

    user = User(
        firebase_uid   = data.firebase_uid,
        email          = data.email,
        username       = data.username,
        monthly_income = data.monthly_income or 0.0
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@app.get("/users/{firebase_uid}", response_model=UserOut)
def get_user(firebase_uid: str, db: Session = Depends(get_db)):
    """Fetch a user's profile. Android calls this on login."""
    user = db.query(User).filter(User.firebase_uid == firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user


@app.put("/users/{firebase_uid}", response_model=UserOut)
def update_user(firebase_uid: str, data: UserUpdate, db: Session = Depends(get_db)):
    """Update income or username. Android calls this from profile screen."""
    user = db.query(User).filter(User.firebase_uid == firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    if data.username       is not None: user.username       = data.username
    if data.monthly_income is not None: user.monthly_income = data.monthly_income
    db.commit()
    db.refresh(user)
    return user


# TRANSACTION ROUTES

@app.post("/transactions", response_model=TransactionOut)
def add_transaction(data: TransactionCreate, db: Session = Depends(get_db)):
    """Save a new expense. Android calls this when user logs/makes a purchase."""
    user = db.query(User).filter(User.firebase_uid == data.firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    tx = Transaction(
        user_id      = user.id,
        amount       = data.amount,
        category     = data.category,
        description  = data.description,
        is_recurring = data.is_recurring
    )
    db.add(tx)
    db.commit()
    db.refresh(tx)
    return tx


@app.get("/transactions/{firebase_uid}", response_model=list[TransactionOut])
def get_transactions(firebase_uid: str, db: Session = Depends(get_db)):
    """Get all transactions for a user. Android uses this for history screen."""
    user = db.query(User).filter(User.firebase_uid == firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user.transactions


@app.get("/transactions/{firebase_uid}/summary")
def get_spending_summary(firebase_uid: str, db: Session = Depends(get_db)):
    """
    Returns total spent per category this month.
    Android uses this to build the spending breakdown chart.
    """
    from sqlalchemy import extract, func
    from datetime import datetime

    user = db.query(User).filter(User.firebase_uid == firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    now = datetime.now()
    results = (
        db.query(Transaction.category, func.sum(Transaction.amount).label("total"))
        .filter(
            Transaction.user_id == user.id,
            extract("month", Transaction.timestamp) == now.month,
            extract("year",  Transaction.timestamp) == now.year
        )
        .group_by(Transaction.category)
        .all()
    )

    summary = {row.category: round(row.total, 2) for row in results}
    total   = round(sum(summary.values()), 2)
    safe    = round(max(0.0, user.monthly_income - total), 2)

    return {
        "month":          now.strftime("%B %Y"),
        "monthly_income": user.monthly_income,
        "total_spent":    total,
        "safe_to_spend":  safe,
        "by_category":    summary
    }


# LOCATION VISIT ROUTES  (GPS budget mode)

@app.post("/visits", response_model=LocationVisitOut)
def log_visit(data: LocationVisitCreate, db: Session = Depends(get_db)):
    """Android GPS sends this when user is detected near a store."""
    user = db.query(User).filter(User.firebase_uid == data.firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    visit = LocationVisit(
        user_id    = user.id,
        store_name = data.store_name,
        category   = data.category,
        lat        = data.lat,
        lng        = data.lng
    )
    db.add(visit)
    db.commit()
    db.refresh(visit)
    return visit


@app.get("/visits/{firebase_uid}")
def get_visits(firebase_uid: str, db: Session = Depends(get_db)):
    """Returns visit frequency per store category. Used by ML budget model."""
    user = db.query(User).filter(User.firebase_uid == firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    from sqlalchemy import func
    results = (
        db.query(LocationVisit.category, func.count().label("visits"))
        .filter(LocationVisit.user_id == user.id)
        .group_by(LocationVisit.category)
        .all()
    )
    return {"visit_counts": {row.category: row.visits for row in results}}


# BUDGET LIMIT ROUTES

@app.post("/budget", response_model=BudgetLimitOut)
def set_budget(data: BudgetLimitCreate, db: Session = Depends(get_db)):
    """User sets a spending limit for a category from the Android app."""
    user = db.query(User).filter(User.firebase_uid == data.firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    existing = (db.query(BudgetLimit)
                .filter(BudgetLimit.user_id == user.id,
                        BudgetLimit.category == data.category)
                .first())
    if existing:
        existing.limit_amount = data.limit_amount
        existing.ml_adjusted  = False
        db.commit()
        db.refresh(existing)
        return existing

    limit = BudgetLimit(
        user_id      = user.id,
        category     = data.category,
        limit_amount = data.limit_amount
    )
    db.add(limit)
    db.commit()
    db.refresh(limit)
    return limit


@app.get("/budget/{firebase_uid}", response_model=list[BudgetLimitOut])
def get_budgets(firebase_uid: str, db: Session = Depends(get_db)):
    """Returns all budget limits for a user."""
    user = db.query(User).filter(User.firebase_uid == firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user.budget_limits



# ML PREDICTION ROUTE
# Calls the ML server and returns all 3 predictions to Android

@app.post("/predict")
async def get_predictions(data: PredictionRequest, db: Session = Depends(get_db)):
    """
    One endpoint Android calls to get all ML results.
    This fetches the user's income from DB, then asks the ML server
    for: user type (K-Means) + risk (Logistic) + forecast (Prophet).
    """
    user = db.query(User).filter(User.firebase_uid == data.firebase_uid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    payload = {
        "firebase_uid":        data.firebase_uid,
        "income":              user.monthly_income,
        "total_spent":         data.total_spent,
        "food":                data.food_spend,
        "entertainment":       data.entertainment_spend,
        "discretionary_spend": data.discretionary_spend
    }

    results = {}
    async with httpx.AsyncClient() as client:
        try:
            r1 = await client.post(f"{ML_SERVER}/predict/user-type", json=payload)
            results["user_type"] = r1.json()
        except Exception:
            results["user_type"] = {"error": "ML server not reachable"}

        try:
            r2 = await client.post(f"{ML_SERVER}/predict/risk", json=payload)
            results["risk"] = r2.json()
        except Exception:
            results["risk"] = {"error": "ML server not reachable"}

        if data.monthly_totals:
            try:
                r3 = await client.post(f"{ML_SERVER}/predict/next-month",
                                       json={"user_id": data.firebase_uid,
                                             "monthly_totals": data.monthly_totals})
                results["forecast"] = r3.json()
            except Exception:
                results["forecast"] = {"error": "ML server not reachable"}

    return results