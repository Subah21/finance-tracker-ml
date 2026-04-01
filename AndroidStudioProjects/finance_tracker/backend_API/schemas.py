"""
Defines what JSON Android sends to the API and what it gets back.
Pydantic validates the data automatically — wrong types = 422 error.
"""

from pydantic import BaseModel
from typing import Optional
from datetime import datetime


# User schemas

class UserCreate(BaseModel):
    """Android sends this when a new user registers."""
    firebase_uid:   str
    email:          str
    username:       Optional[str] = None
    monthly_income: Optional[float] = 0.0

class UserUpdate(BaseModel):
    """Android sends this when user updates their profile."""
    username:       Optional[str] = None
    monthly_income: Optional[float] = None

class UserOut(BaseModel):
    """What the API sends back after creating/fetching a user."""
    id:             int
    firebase_uid:   str
    email:          str
    username:       Optional[str]
    monthly_income: float
    created_at:     datetime

    class Config:
        from_attributes = True


# Transaction schemas

class TransactionCreate(BaseModel):
    """Android sends this when user logs a new expense."""
    firebase_uid: str       # used to look up the user
    amount:       float
    category:     str       # food, housing, entertainment, etc.
    description:  Optional[str] = None
    is_recurring: bool = False

class TransactionOut(BaseModel):
    id:           int
    amount:       float
    category:     str
    description:  Optional[str]
    is_recurring: bool
    timestamp:    datetime

    class Config:
        from_attributes = True


# Location visit schemas

class LocationVisitCreate(BaseModel):
    """Android GPS sends this when user is near a store."""
    firebase_uid: str
    store_name:   Optional[str] = None
    category:     str           # grocery, clothing, restaurant…
    lat:          float
    lng:          float

class LocationVisitOut(BaseModel):
    id:         int
    store_name: Optional[str]
    category:   str
    lat:        float
    lng:        float
    visited_at: datetime

    class Config:
        from_attributes = True


# Budget limit schemas

class BudgetLimitCreate(BaseModel):
    firebase_uid: str
    category:     str
    limit_amount: float

class BudgetLimitOut(BaseModel):
    id:           int
    category:     str
    limit_amount: float
    ml_adjusted:  bool
    updated_at:   datetime

    class Config:
        from_attributes = True


# ML prediction schemas (forwarded to backend_ML_models)

class PredictionRequest(BaseModel):
    """Android sends this to get all 3 ML predictions at once."""
    firebase_uid:        str
    total_spent:         float
    food_spend:          float
    entertainment_spend: float
    discretionary_spend: float
    monthly_totals:      Optional[list[float]] = None  # for Prophet forecast