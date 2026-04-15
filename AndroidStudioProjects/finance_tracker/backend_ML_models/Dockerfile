FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy all source code, trained models (.pkl), and training datasets
COPY main.py model.py train_model.py ./
COPY *.pkl ./
COPY datasets/ ./datasets/

# Cloud Run sets the PORT env variable
ENV PORT=8080
EXPOSE 8080

CMD uvicorn main:app --host 0.0.0.0 --port $PORT
