import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns

x = np.linspace(0, 10, 200)
plt.plot(x, np.sin(x), label="sin")
plt.plot(x, np.cos(x), label="cos")
plt.legend()
plt.title("Hello plots")
plt.show()

sns.set_theme()
sns.histplot(np.random.randn(1000), kde=True)
plt.show()
