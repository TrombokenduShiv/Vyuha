"""Repair the original generator's vpa_acc_* IDs through the OWNS mapping."""
from pathlib import Path
import pandas as pd

root = Path(__file__).resolve().parents[1] / "datasets/synthetic"
owns = pd.read_csv(root / "edges_account_owns_vpa.csv")
primary = owns.drop_duplicates("src").set_index("src").dst
for suffix in ("", "_train", "_val", "_test"):
    frame = pd.read_csv(root / f"edges_account_sends_to_account{suffix}.csv")
    frame["src"] = frame.src.map(primary)
    frame["dst"] = frame.dst.map(primary)
    if frame[["src", "dst"]].isna().any().any():
        raise ValueError("Missing VPA ownership")
    frame.to_csv(root / f"edges_vpa_receives_from_vpa{suffix}.csv", index=False)
print("Rebuilt VPA edges from explicit ownership relationships")
