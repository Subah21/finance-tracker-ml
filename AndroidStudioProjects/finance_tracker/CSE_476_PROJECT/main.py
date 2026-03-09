from pathlib import Path
import pandas as pd
import urllib.request
import numpy as np
import matplotlib.pyplot as plt
import kagglehub

import kagglehub
import pandas as pd
from pathlib import Path

def load_transactions():
    path = kagglehub.dataset_download(
        "artemkabseu/financial-transactions-dataset-expenses-and-income"
    )

    csv_path = Path(path) / "transactions.csv"

    return pd.read_csv(csv_path)

data = load_transactions()

print(data.describe())