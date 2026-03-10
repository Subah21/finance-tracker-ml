from fastapi import FastAPI

app = FastAPI()

@app.get("/")
def home():
    return {"message": "Finance backend running"}

@app.get("/spending")
def spending():

    data = {
        "rent": 1200,
        "groceries": 350,
        "entertainment": 200,
        "transport": 120
    }

    return data