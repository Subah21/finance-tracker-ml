"""
FastAPI server for all 3 ML model predictions.
Runs on port 8001. The backend_API server (port 8000) calls this
internally but Android never talks to this server directly.

ENDPOINTS:
  POST /predict/user-type    --> K-Means:  Saver / Balanced / Spender
  POST /predict/risk         --> Logistic: overspending risk %
  POST /predict/next-month   --> Prophet:  forecasted spending + advice
  POST /predict/all          --> calls all 3 at once which is used by backend_API

DATABASE CONNECTION:
  After a prediction is made, the result is saved to the
  ml_results table in the database so the user's history
  of predictions is kept.

RUN WITH:
    uvicorn main:app --reload --port 8001

TEST AT:
    http://localhost:8001/docs
"""

from fastapi import FastAPI, Depends
from pydantic import BaseModel
from typing import Optional
from sqlalchemy.orm import Session
from sqlalchemy import create_engine, Column, Integer, String, Float, DateTime, func
from sqlalchemy.orm import sessionmaker, DeclarativeBase

from model import (predict_user_type,
                   predict_overspending_risk,
                   predict_next_month_spending)

app = FastAPI(title="Finance Tracker — ML Models API", version="1.0")


# database which saves the prediction results so we have a history

DATABASE_URL = "sqlite:///./ml_results.db"

engine = create_engine(DATABASE_URL,
                        connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


class Base(DeclarativeBase):
    pass


class MLResult(Base):
    """
    Every time a prediction is made for a user, we save the result here.
    This lets us track how a user's spending type and risk changes over time.
    """
    __tablename__ = "ml_results"

    id               = Column(Integer, primary_key=True, index=True)
    firebase_uid     = Column(String, index=True)   # which user
    user_type        = Column(String)                # Saver/Balanced/Spender
    risk_flag        = Column(Integer)               # 0 or 1
    risk_percent     = Column(Float)                 # 0.0 – 100.0
    predicted_spend  = Column(Float, nullable=True)  # Prophet forecast
    created_at       = Column(DateTime, default=func.now())


# Creates the table when the server starts
@app.on_event("startup")
def startup():
    Base.metadata.create_all(bind=engine)
    print("ML results database ready.")


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# INPUT SCHEMAS which is what the backend_API sends to this server

class SpendingInput(BaseModel):
    """
    The spending data for one user at a point in time.
    backend_API sends this after calculating the user's
    current month totals from their transaction history.
    """
    firebase_uid:        str
    income:              float   # user's monthly income
    total_spent:         float   # total spent so far this month
    food:                float   # spent on food
    entertainment:       float   # spent on entertainment
    discretionary_spend: float   # entertainment + personal_care + misc
    monthly_totals:      list[float] = []  # optional past months for Prophet

class ForecastInput(BaseModel):
    """List of the user's past monthly spending totals for Prophet."""
    firebase_uid:   str
    monthly_totals: list[float]  # e.g. [850, 920, 780, 1100, 950]


# ENDPOINTS

@app.get("/")
def root():
    return {"status": "ML API running", "docs": "/docs"}


# Model 1: K-Means

@app.post("/predict/user-type")
def get_user_type(data: SpendingInput, db: Session = Depends(get_db)):
    """
    Classifies the user as Saver, Balanced, or Spender.

    What AndroidStudio will show:
      "You are a Saver! Great financial habits."
      "You are a Spender. Consider reviewing your budget."
    """
    user_type = predict_user_type(
        income              = data.income,
        total_spent         = data.total_spent,
        food                = data.food,
        entertainment       = data.entertainment,
        discretionary_spend = data.discretionary_spend
    )

    tips = {
        "Saver":    "Great job! You're well within your budget.",
        "Balanced": "You're spending moderately. Watch discretionary costs.",
        "Spender":  "You're spending heavily. Consider reviewing your budget."
    }

    return {
        "user_type": user_type,
        "tip":       tips.get(user_type, "")
    }


# Model 3: Logistic Regression

@app.post("/predict/risk")
def get_risk(data: SpendingInput, db: Session = Depends(get_db)):
    """
    Returns whether the user is at risk of overspending.

    What AndroidStudio will show:
      Warning banner: "73% chance of overspending this month"
      or
      Green badge: "You're on track! Only 12% risk."
    """
    flag, probability = predict_overspending_risk(
        income              = data.income,
        total_spent         = data.total_spent,
        food                = data.food,
        entertainment       = data.entertainment,
        discretionary_spend = data.discretionary_spend
    )

    return {
        "at_risk":      bool(flag),
        "risk_percent": probability,
        "message": (
            f"Warning: {probability}% chance of overspending this month."
            if flag else
            f"You're on track. Only {probability}% overspending risk."
        )
    }


# Model 2: Prophet

@app.post("/predict/next-month")
def forecast(data: ForecastInput):
    """
    Predicts next month's spending based on the user's history.
    Android sends the user's last 3-12 months of spending totals.

    What AndroidStudio will show:
      "Based on your history, you'll likely spend $1,240 next month."
      "Your spending is trending up. consider cutting back."
    """
    if len(data.monthly_totals) < 2:
        return {"error": "Need at least 2 months of spending history."}

    return predict_next_month_spending(data.monthly_totals)


# All 3 models at once

@app.post("/predict/all")
def predict_all(data: SpendingInput,
                db: Session = Depends(get_db)):
    """
    Runs all 3 models and returns combined results.
    This is the main endpoint backend_API calls.
    Also saves the result to the ml_results database.
    """
    # Model 1: K-Means
    user_type = predict_user_type(
        income              = data.income,
        total_spent         = data.total_spent,
        food                = data.food,
        entertainment       = data.entertainment,
        discretionary_spend = data.discretionary_spend
    )

    # Model 3: Logistic Regression
    flag, probability = predict_overspending_risk(
        income              = data.income,
        total_spent         = data.total_spent,
        food                = data.food,
        entertainment       = data.entertainment,
        discretionary_spend = data.discretionary_spend
    )

    # Model 2: Prophet (only if history is provided) but still needs a lot of adjustments.
    forecast_result = None
    predicted_spend = None
    if len(data.monthly_totals) >= 2:
        forecast_result = predict_next_month_spending(data.monthly_totals)
        if "predicted_spending" in forecast_result:
            predicted_spend = forecast_result["predicted_spending"]

    # Save result to database
    result_row = MLResult(
        firebase_uid    = data.firebase_uid,
        user_type       = user_type,
        risk_flag       = flag,
        risk_percent    = probability,
        predicted_spend = predicted_spend
    )
    db.add(result_row)
    db.commit()

    # Return combined response to backend_API
    tips = {
        "Saver":    "Great job! You're well within your budget.",
        "Balanced": "You're spending moderately. Watch discretionary costs.",
        "Spender":  "You're spending heavily. Consider reviewing your budget."
    }

    return {
        "user_type": {
            "label": user_type,
            "tip":   tips.get(user_type, "")
        },
        "risk": {
            "at_risk":      bool(flag),
            "risk_percent": probability,
            "message": (
                f"Warning: {probability}% chance of overspending."
                if flag else
                f"You're on track. Only {probability}% risk."
            )
        },
        "forecast": forecast_result
    }


# Get a user's prediction history

@app.get("/history/{firebase_uid}")
def get_prediction_history(firebase_uid: str,
                            db: Session = Depends(get_db)):
    """
    Returns all past ML predictions for a user.
    Android can use this to show a trend over time.
    """
    results = (db.query(MLResult)
                 .filter(MLResult.firebase_uid == firebase_uid)
                 .order_by(MLResult.created_at.desc())
                 .limit(10)
                 .all())

    return [
        {
            "user_type":       r.user_type,
            "risk_percent":    r.risk_percent,
            "predicted_spend": r.predicted_spend,
            "date":            str(r.created_at)
        }
        for r in results
    ]