"""
Vyuha 2.0 — Graph Data Loader
================================
Converts raw CSVs from the synthetic data generator into
PyTorch Geometric HeteroData objects for HGT training.

Node types: account, vpa, device, phone
Edge types: account_sends_to_account, vpa_receives_from_vpa,
            account_owns_vpa, device_accesses_account,
            phone_associated_with_account

Architecture ref: ADR-003, ARCHITECTURE_FREEZE_V1 §5 D5-D6.
"""
import os
import numpy as np
import pandas as pd
import torch
from collections import defaultdict

# ── Constants ──────────────────────────────────────────────────────
DATA_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../datasets/synthetic"))


def _build_node_id_map(csv_path: str) -> dict:
    """Build a mapping from string node ID to integer index."""
    df = pd.read_csv(csv_path)
    return {nid: idx for idx, nid in enumerate(df["id"].values)}


def _compute_degree_features(edges_df: pd.DataFrame, src_col: str, dst_col: str,
                              node_map: dict, num_nodes: int) -> np.ndarray:
    """Compute in-degree, out-degree, and fan-in/fan-out ratio for nodes."""
    out_deg = np.zeros(num_nodes, dtype=np.float32)
    in_deg = np.zeros(num_nodes, dtype=np.float32)

    for _, row in edges_df.iterrows():
        src = row[src_col]
        dst = row[dst_col]
        if src in node_map:
            out_deg[node_map[src]] += 1
        if dst in node_map:
            in_deg[node_map[dst]] += 1

    total_deg = in_deg + out_deg
    # Fan-in ratio: in_deg / total_deg (avoid div by zero)
    fan_in_ratio = np.divide(in_deg, total_deg, out=np.zeros_like(in_deg),
                             where=total_deg > 0)

    return np.stack([in_deg, out_deg, total_deg, fan_in_ratio], axis=1)


def _compute_temporal_features(edges_df: pd.DataFrame, src_col: str, dst_col: str,
                                node_map: dict, num_nodes: int) -> np.ndarray:
    """Compute temporal activity features: mean inter-arrival time, tx count, amount stats."""
    # Group by source node
    tx_counts = np.zeros(num_nodes, dtype=np.float32)
    amount_sums = np.zeros(num_nodes, dtype=np.float32)
    amount_maxs = np.zeros(num_nodes, dtype=np.float32)

    sorted_df = edges_df.sort_values("timestamp")

    # Per-node timestamps for inter-arrival
    node_timestamps = defaultdict(list)

    for _, row in sorted_df.iterrows():
        src = row[src_col]
        if src in node_map:
            idx = node_map[src]
            tx_counts[idx] += 1
            amt = row.get("amount", 0)
            amount_sums[idx] += amt
            amount_maxs[idx] = max(amount_maxs[idx], amt)
            node_timestamps[idx].append(row["timestamp"])

    # Mean inter-arrival time
    mean_iat = np.zeros(num_nodes, dtype=np.float32)
    for idx, ts_list in node_timestamps.items():
        if len(ts_list) > 1:
            diffs = np.diff(sorted(ts_list))
            mean_iat[idx] = np.mean(diffs)

    # Normalize
    if tx_counts.max() > 0:
        tx_counts_norm = tx_counts / tx_counts.max()
    else:
        tx_counts_norm = tx_counts
    if amount_sums.max() > 0:
        amount_sums_norm = amount_sums / amount_sums.max()
    else:
        amount_sums_norm = amount_sums
    if amount_maxs.max() > 0:
        amount_maxs_norm = amount_maxs / amount_maxs.max()
    else:
        amount_maxs_norm = amount_maxs
    if mean_iat.max() > 0:
        mean_iat_norm = mean_iat / mean_iat.max()
    else:
        mean_iat_norm = mean_iat

    return np.stack([tx_counts_norm, amount_sums_norm, amount_maxs_norm, mean_iat_norm], axis=1)


def load_hetero_data(split: str = "train"):
    """
    Load the synthetic graph data into a dictionary-based heterogeneous graph.

    Args:
        split: One of 'train', 'val', 'test', or 'full' (uses unsplit data).

    Returns:
        dict with keys:
            - node_features: {node_type: Tensor}
            - edge_index: {(src_type, edge_type, dst_type): Tensor}
            - edge_attr: {(src_type, edge_type, dst_type): Tensor}
            - labels: Tensor (for account nodes)
            - label_mask: Tensor (which account nodes have labels)
            - node_maps: {node_type: {str_id: int_idx}}
    """
    print(f"Loading heterogeneous graph data (split={split})...")

    # ── Load node ID maps ────────────────────────────────────────
    account_map = _build_node_id_map(os.path.join(DATA_DIR, "nodes_account.csv"))
    vpa_map = _build_node_id_map(os.path.join(DATA_DIR, "nodes_vpa.csv"))
    device_map = _build_node_id_map(os.path.join(DATA_DIR, "nodes_device.csv"))
    phone_map = _build_node_id_map(os.path.join(DATA_DIR, "nodes_phone.csv"))

    num_accounts = len(account_map)
    num_vpas = len(vpa_map)
    num_devices = len(device_map)
    num_phones = len(phone_map)

    print(f"  Nodes: {num_accounts} accounts, {num_vpas} VPAs, {num_devices} devices, {num_phones} phones")

    # ── Load temporal edges ──────────────────────────────────────
    suffix = f"_{split}" if split != "full" else ""
    acc_tx_file = f"edges_account_sends_to_account{suffix}.csv"
    vpa_tx_file = f"edges_vpa_receives_from_vpa{suffix}.csv"

    acc_tx_path = os.path.join(DATA_DIR, acc_tx_file)
    vpa_tx_path = os.path.join(DATA_DIR, vpa_tx_file)

    if not os.path.exists(acc_tx_path):
        print(f"  Warning: {acc_tx_file} not found, using full data")
        acc_tx_path = os.path.join(DATA_DIR, "edges_account_sends_to_account.csv")
        vpa_tx_path = os.path.join(DATA_DIR, "edges_vpa_receives_from_vpa.csv")

    acc_tx_df = pd.read_csv(acc_tx_path)
    vpa_tx_df = pd.read_csv(vpa_tx_path)

    # ── Load static edges ────────────────────────────────────────
    owns_vpa_df = pd.read_csv(os.path.join(DATA_DIR, "edges_account_owns_vpa.csv"))
    dev_acc_df = pd.read_csv(os.path.join(DATA_DIR, "edges_device_accesses_account.csv"))
    phone_acc_df = pd.read_csv(os.path.join(DATA_DIR, "edges_phone_associated_with_account.csv"))

    print(f"  Edges: {len(acc_tx_df)} account_sends, {len(vpa_tx_df)} vpa_receives, "
          f"{len(owns_vpa_df)} owns_vpa, {len(dev_acc_df)} device_access, {len(phone_acc_df)} phone_assoc")

    # ── Compute node features ────────────────────────────────────
    # Account features: degree + temporal
    acc_degree = _compute_degree_features(acc_tx_df, "src", "dst", account_map, num_accounts)
    acc_temporal = _compute_temporal_features(acc_tx_df, "src", "dst", account_map, num_accounts)
    account_features = np.concatenate([acc_degree, acc_temporal], axis=1)  # 8-dim

    # VPA features: degree from VPA transactions
    vpa_degree = _compute_degree_features(vpa_tx_df, "src", "dst", vpa_map, num_vpas)
    vpa_features = vpa_degree  # 4-dim

    # Device features: just degree from device-account edges
    dev_degree = np.zeros((num_devices, 4), dtype=np.float32)
    for _, row in dev_acc_df.iterrows():
        if row["src"] in device_map:
            dev_degree[device_map[row["src"]], 1] += 1  # out-degree
    device_features = dev_degree

    # Phone features: degree
    phone_degree = np.zeros((num_phones, 4), dtype=np.float32)
    for _, row in phone_acc_df.iterrows():
        if row["src"] in phone_map:
            phone_degree[phone_map[row["src"]], 1] += 1
    phone_features = phone_degree

    # ── Build edge indices ───────────────────────────────────────
    def build_edge_index(df, src_col, dst_col, src_map, dst_map):
        src_ids = []
        dst_ids = []
        for _, row in df.iterrows():
            s, d = row[src_col], row[dst_col]
            if s in src_map and d in dst_map:
                src_ids.append(src_map[s])
                dst_ids.append(dst_map[d])
        if len(src_ids) == 0:
            return torch.zeros((2, 0), dtype=torch.long)
        return torch.tensor([src_ids, dst_ids], dtype=torch.long)

    def build_edge_attr(df, timestamp_col="timestamp", amount_col="amount"):
        """Normalize timestamp and amount as edge features."""
        if len(df) == 0:
            return torch.zeros((0, 2), dtype=torch.float32)
        ts = df[timestamp_col].values.astype(np.float64)
        if ts.max() > ts.min():
            ts_norm = (ts - ts.min()) / (ts.max() - ts.min())
        else:
            ts_norm = np.zeros_like(ts)
        amt = df[amount_col].values.astype(np.float64) if amount_col in df.columns else np.zeros_like(ts)
        if amt.max() > 0:
            amt_norm = amt / amt.max()
        else:
            amt_norm = amt
        return torch.tensor(np.stack([ts_norm, amt_norm], axis=1), dtype=torch.float32)

    edge_index = {}
    edge_attr = {}

    # Temporal edges
    edge_index[("account", "sends_to", "account")] = build_edge_index(
        acc_tx_df, "src", "dst", account_map, account_map)
    edge_attr[("account", "sends_to", "account")] = build_edge_attr(acc_tx_df)

    edge_index[("vpa", "receives_from", "vpa")] = build_edge_index(
        vpa_tx_df, "src", "dst", vpa_map, vpa_map)
    edge_attr[("vpa", "receives_from", "vpa")] = build_edge_attr(vpa_tx_df)

    # Static edges (no temporal features)
    edge_index[("account", "owns", "vpa")] = build_edge_index(
        owns_vpa_df, "src", "dst", account_map, vpa_map)
    edge_index[("device", "accesses", "account")] = build_edge_index(
        dev_acc_df, "src", "dst", device_map, account_map)
    edge_index[("phone", "associated_with", "account")] = build_edge_index(
        phone_acc_df, "src", "dst", phone_map, account_map)

    # ── Load labels ──────────────────────────────────────────────
    labels_df = pd.read_csv(os.path.join(DATA_DIR, "account_labels.csv"))
    labels = np.zeros(num_accounts, dtype=np.float32)
    label_mask = np.zeros(num_accounts, dtype=bool)
    for _, row in labels_df.iterrows():
        acc_id = row["account_id"]
        if acc_id in account_map:
            idx = account_map[acc_id]
            labels[idx] = float(row["is_fraud"])
            label_mask[idx] = True

    fraud_count = int(labels.sum())
    legit_count = int(label_mask.sum()) - fraud_count
    print(f"  Labels: {fraud_count} fraud, {legit_count} legitimate "
          f"({fraud_count / max(1, fraud_count + legit_count) * 100:.1f}% fraud rate)")

    node_features = {
        "account": torch.tensor(account_features, dtype=torch.float32),
        "vpa": torch.tensor(vpa_features, dtype=torch.float32),
        "device": torch.tensor(device_features, dtype=torch.float32),
        "phone": torch.tensor(phone_features, dtype=torch.float32),
    }

    return {
        "node_features": node_features,
        "edge_index": edge_index,
        "edge_attr": edge_attr,
        "labels": torch.tensor(labels, dtype=torch.float32),
        "label_mask": torch.tensor(label_mask, dtype=torch.bool),
        "node_maps": {
            "account": account_map,
            "vpa": vpa_map,
            "device": device_map,
            "phone": phone_map,
        },
        "metadata": {
            "num_nodes": {"account": num_accounts, "vpa": num_vpas,
                         "device": num_devices, "phone": num_phones},
            "feature_dims": {"account": 8, "vpa": 4, "device": 4, "phone": 4},
        }
    }


if __name__ == "__main__":
    print("=" * 60)
    print("Vyuha 2.0 — Graph Data Loader Test")
    print("=" * 60)

    for split in ["train", "val", "test"]:
        data = load_hetero_data(split)
        print(f"\n[{split.upper()}]")
        for ntype, feat in data["node_features"].items():
            print(f"  {ntype}: {feat.shape}")
        for etype, idx in data["edge_index"].items():
            print(f"  {etype}: {idx.shape}")
        print(f"  Labels sum (fraud): {data['labels'].sum().item():.0f}")
        print()
