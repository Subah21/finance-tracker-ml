"""
Handles the database connection and defines all 4 tables.

To create the DB, this is called automatically when main.py starts.

Supports both SQLite (local dev) and PostgreSQL (Cloud Run / production).
Set the DATABASE_URL environment variable to switch databases:
  - SQLite (default):  sqlite:///./finance_tracker.db
  - Postgres:          postgresql://user:password@host/dbname
"""

import os
from sqlalchemy import (create_engine, Column, String, Float,
                         Boolean, DateTime, Integer, ForeignKey, func)
from sqlalchemy.orm import sessionmaker, DeclarativeBase, relationship

# Connection — reads from env var for Cloud Run, falls back to SQLite for local dev
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./finance_tracker.db")

# SQLite needs check_same_thread=False; Postgres does not
connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}

engine = create_engine(DATABASE_URL, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


class Base(DeclarativeBase):
    pass


# TABLE 1: users
# Stores every registered user. Firebase handles the password
# we store the Firebase UID so we can link data across tables.
class User(Base):
    __tablename__ = "users"

    id             = Column(Integer, primary_key=True, index=True)
    firebase_uid   = Column(String, unique=True, index=True, nullable=False)
    email          = Column(String, unique=True, nullable=False)
    username       = Column(String, nullable=True)
    monthly_income = Column(Float, default=0.0)
    created_at     = Column(DateTime, default=func.now())

    # Relationships which lets us do user.transactions  in code
    transactions   = relationship("Transaction",   back_populates="user")
    location_visits= relationship("LocationVisit", back_populates="user")
    budget_limits  = relationship("BudgetLimit",   back_populates="user")



# TABLE 2: transactions
# Every time the user logs/makes a purchase, it goes here.

class Transaction(Base):
    __tablename__ = "transactions"

    id           = Column(Integer, primary_key=True, index=True)
    user_id      = Column(Integer, ForeignKey("users.id"), nullable=False)
    amount       = Column(Float, nullable=False)
    category     = Column(String, nullable=False)  # food, housing, entertainment…
    description  = Column(String, nullable=True)
    is_recurring = Column(Boolean, default=False)
    timestamp    = Column(DateTime, default=func.now())

    user = relationship("User", back_populates="transactions")


# TABLE 3: location_visits
# Android GPS sends a visit whenever user is near a store.
# ML budget mode reads this to detect overspending patterns.

class LocationVisit(Base):
    __tablename__ = "location_visits"

    id         = Column(Integer, primary_key=True, index=True)
    user_id    = Column(Integer, ForeignKey("users.id"), nullable=False)
    store_name = Column(String, nullable=True)
    category   = Column(String, nullable=False)  # grocery, clothing, restaurant, etc
    lat        = Column(Float, nullable=False)
    lng        = Column(Float, nullable=False)
    visited_at = Column(DateTime, default=func.now())

    user = relationship("User", back_populates="location_visits")


# TABLE 4: budget_limits
# Stores the user's budget per category.
# ml_adjusted = True, means the ML model changed this limit automatically.
class BudgetLimit(Base):
    __tablename__ = "budget_limits"

    id            = Column(Integer, primary_key=True, index=True)
    user_id       = Column(Integer, ForeignKey("users.id"), nullable=False)
    category      = Column(String, nullable=False)
    limit_amount  = Column(Float, nullable=False)
    ml_adjusted   = Column(Boolean, default=False)
    updated_at    = Column(DateTime, default=func.now(), onupdate=func.now())

    user = relationship("User", back_populates="budget_limits")


# DataBase session where it's injected into every FastAPI route via Depends(get_db)
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# Creates all the tables, and it's called once at startup from main.py
def init_db():
    Base.metadata.create_all(bind=engine)