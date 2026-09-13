import pandas as pd
import numpy as np
import os
import json

DATA_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../datasets/synthetic"))

def split_temporal_edges(filename, train_frac=0.70, val_frac=0.15):
    filepath = os.path.join(DATA_DIR, filename)
    if not os.path.exists(filepath):
        print(f"Error: {filepath} not found. Run simulator.py first.")
        return
        
    df = pd.read_csv(filepath)
    # Sort chronologically to strictly prevent temporal leakage
    df = df.sort_values(by="timestamp").reset_index(drop=True)
    
    n_total = len(df)
    n_train = int(n_total * train_frac)
    n_val = int(n_total * val_frac)
    
    train_df = df.iloc[:n_train]
    val_df = df.iloc[n_train:n_train+n_val]
    test_df = df.iloc[n_train+n_val:]
    
    base_name = filename.replace(".csv", "")
    train_df.to_csv(os.path.join(DATA_DIR, f"{base_name}_train.csv"), index=False)
    val_df.to_csv(os.path.join(DATA_DIR, f"{base_name}_val.csv"), index=False)
    test_df.to_csv(os.path.join(DATA_DIR, f"{base_name}_test.csv"), index=False)
    
    print(f"Split {filename}: Train ({len(train_df)}), Val ({len(val_df)}), Test ({len(test_df)})")
    
    # Assert no temporal leakage
    if len(train_df) > 0 and len(val_df) > 0:
        assert train_df['timestamp'].max() <= val_df['timestamp'].min(), "Temporal leakage detected between train and val!"
    if len(val_df) > 0 and len(test_df) > 0:
        assert val_df['timestamp'].max() <= test_df['timestamp'].min(), "Temporal leakage detected between val and test!"

def generate_report():
    print("Generating Evaluation Report...")
    report = {
        "model": "MVP Synthetic Heterogeneous Graph",
        "status": "Simulated Data Generated. No ST-GNN Model Trained Yet.",
        "metrics": {
            "AUROC": "UNMEASURED",
            "AUPRC": "UNMEASURED",
            "precision": "UNMEASURED",
            "recall": "UNMEASURED",
            "F1": "UNMEASURED",
            "calibration": "UNMEASURED",
            "inference_latency_ms": "UNMEASURED"
        }
    }
    
    report_path = os.path.join(DATA_DIR, "MVP_REPORT.json")
    with open(report_path, "w") as f:
        json.dump(report, f, indent=4)
        
    print("\n================ VYUHA 2.0 GRAPH MVP REPORT ================")
    for k, v in report["metrics"].items():
        print(f"{k.ljust(25)}: {v}")
    print("============================================================\n")
    print(f"Report saved to {report_path}")

if __name__ == "__main__":
    split_temporal_edges("edges_account_sends_to_account.csv")
    split_temporal_edges("edges_vpa_receives_from_vpa.csv")
    generate_report()
