import pandas as pd
from sklearn.datasets import load_iris
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier

X, y = load_iris(return_X_y=True, as_frame=True)
print(X.describe().round(2))
Xtr, Xte, ytr, yte = train_test_split(X, y, test_size=0.3, random_state=0)
model = RandomForestClassifier(n_estimators=100).fit(Xtr, ytr)
print("Accuracy:", model.score(Xte, yte))
