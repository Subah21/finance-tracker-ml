import pandas as pd
from pathlib import Path
def load_data():
    data = Path("datasets/genz_money_spends.csv")
    data1 = Path("datasets/student_spending (1).csv")
    A = pd.read_csv(data)
    B =  pd.read_csv(data1)
    return A,B

A,B = load_data()

print(A.head())
print(B.head())
