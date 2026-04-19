import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.ticker import FormatStrFormatter, ScalarFormatter

# ==============================
# LOAD DATA
# ==============================
woa = pd.read_csv("E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Result\\WOA\\hasil_20260419_155445_konvergensi.csv")
ga  = pd.read_csv("E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Result\\GA\\hasil_GA_20260419_155843_konvergensi.csv")
pso = pd.read_csv("E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Result\\PSO\\hasil_PSO_20260419_160034_konvergensi.csv")

# ==============================
# CONFIG
# ==============================
customers = [10, 20, 30, 40, 50]

# pilih skenario (HARUS SAMA untuk semua algoritma)
POP = "P100"
GEN = "G1000"

# jumlah digit desimal (biar ga jadi 0.000000 semua)
DECIMAL = 8

# ==============================
# LOOP PLOT PER CUSTOMER
# ==============================
for cust in customers:

    col_name = f"{POP}_{GEN}_{cust}_Customers"

    if col_name not in woa.columns:
        print(f"Kolom tidak ditemukan: {col_name}")
        continue

    plt.figure(figsize=(9,5))

    # ==============================
    # AMBIL DATA
    # ==============================
    x = woa["Iterasi"]

    woa_y = woa[col_name]
    ga_y  = ga[col_name]
    pso_y = pso[col_name]

    # ==============================
    # PLOT
    # ==============================
    plt.plot(x, woa_y, label="WOA", linewidth=2)
    plt.plot(x, ga_y,  label="GA",  linewidth=2)
    plt.plot(x, pso_y, label="PSO", linewidth=2)

    # ==============================
    # FORMAT AXIS (NO SCIENTIFIC)
    # ==============================
    ax = plt.gca()

    # hilangkan notasi e-5
    formatter = ScalarFormatter(useMathText=False)
    formatter.set_scientific(False)
    ax.yaxis.set_major_formatter(formatter)

    # format jumlah desimal
    ax.yaxis.set_major_formatter(FormatStrFormatter(f'%.{DECIMAL}f'))

    # ==============================
    # LABEL & STYLE
    # ==============================
    plt.xlabel("Iteration", fontsize=11)
    plt.ylabel("Fitness", fontsize=11)
    plt.title(f"Convergence Curve - {cust} Customers ({POP}, {GEN})", fontsize=13)

    plt.legend()
    plt.grid(alpha=0.3)

    plt.tight_layout()

    # ==============================
    # SAVE
    # ==============================
    plt.savefig(f"grafik_{cust}_customers_{POP}_{GEN}.png", dpi=300)

    plt.show()