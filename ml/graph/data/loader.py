"""Causal snapshots, fixed feature units, disjoint train/val/cal/test labels.

Synthetic static edges are assumed known at inception. Production ingestion
must supply as-of relationships. Labels never enter features.
"""
from pathlib import Path
import hashlib
import numpy as np
import pandas as pd
import torch

DATA_DIR = str(Path(__file__).resolve().parents[3] / "datasets/synthetic")
FEATURE_VERSION = "fixed-log-v2"
BASE_RELATIONS = {
    ("account", "sends_to", "account"): "edges_account_sends_to_account",
    ("vpa", "receives_from", "vpa"): "edges_vpa_receives_from_vpa",
    ("account", "owns", "vpa"): "edges_account_owns_vpa",
    ("device", "accesses", "account"): "edges_device_accesses_account",
    ("phone", "associated_with", "account"): "edges_phone_associated_with_account",
}


def _log(values, scale):
    return np.clip(np.log1p(values) / np.log1p(scale), 0, 2).astype(np.float32)


def label_partition(identifier):
    bucket = int(hashlib.sha256(identifier.encode()).hexdigest()[:8], 16) % 100
    return "train" if bucket < 60 else "val" if bucket < 75 else "cal" if bucket < 85 else "test"


def load_hetero_data(split="train", data_dir=None):
    if split not in {"train", "val", "cal", "test", "full"}:
        raise ValueError("Unknown graph split")
    root = Path(data_dir or DATA_DIR)
    maps = {}
    for kind in ("account", "vpa", "device", "phone"):
        ids = pd.read_csv(root / f"nodes_{kind}.csv")["id"].astype(str)
        if ids.duplicated().any():
            raise ValueError(f"Duplicate {kind} identifier")
        maps[kind] = {value: i for i, value in enumerate(ids)}
    # Both temporal relations use the same clock. Missing splits fail explicitly.
    train_tx = pd.read_csv(root / "edges_account_sends_to_account_train.csv")
    val_tx = pd.read_csv(root / "edges_account_sends_to_account_val.csv")
    train_cut, val_cut = float(train_tx.timestamp.max()), float(val_tx.timestamp.max())
    cutoff = train_cut if split == "train" else val_cut if split in {"val", "cal"} else float("inf")
    frames = {}
    for relation, filename in BASE_RELATIONS.items():
        df = pd.read_csv(root / f"{filename}.csv")
        if "timestamp" in df:
            for col in ("timestamp", "amount"):
                if col in df and (not np.isfinite(df[col]).all() or (df[col] < 0).any()):
                    raise ValueError(f"Invalid {col} in {filename}")
            df = df[df.timestamp <= cutoff].copy()
        src, _, dst = relation
        if not df.src.isin(maps[src]).all() or not df.dst.isin(maps[dst]).all():
            raise ValueError(f"Dangling edge in {filename}")
        frames[relation] = df
    tx = frames[("account", "sends_to", "account")]
    as_of = float(tx.timestamp.max()) if len(tx) else 0.0
    nodes, raw_stats = {}, {}
    for kind in ("account", "vpa"):
        rel = (kind, "sends_to" if kind == "account" else "receives_from", kind)
        df, size = frames[rel], len(maps[kind])
        src = df.src.map(maps[kind]).to_numpy(dtype=np.int64)
        dst = df.dst.map(maps[kind]).to_numpy(dtype=np.int64)
        incoming = np.bincount(dst, minlength=size).astype(float)
        outgoing = np.bincount(src, minlength=size).astype(float)
        total = incoming + outgoing
        feats = [_log(incoming, 100), _log(outgoing, 100), _log(total, 200),
                 np.divide(incoming, total, out=np.zeros(size), where=total > 0)]
        if kind == "account":
            sums = np.bincount(src, weights=df.amount, minlength=size)
            maxs = np.zeros(size)
            np.maximum.at(maxs, src, df.amount)
            iat = np.zeros(size)
            for account, values in df.sort_values("timestamp").groupby("src").timestamp:
                if len(values) > 1:
                    iat[maps[kind][account]] = np.diff(values).mean()
            burst = np.where(outgoing > 1, np.exp(-iat / 3600), 0)
            feats.extend([_log(outgoing, 100), _log(sums, 1e7), _log(maxs, 1e6), burst])
            raw_stats = {"in_count": incoming, "out_count": outgoing,
                         "mean_iat_seconds": iat, "activity_count": total}
        nodes[kind] = torch.tensor(np.stack(feats, axis=1), dtype=torch.float32)
    for kind, relation in [("device", "accesses"), ("phone", "associated_with")]:
        degree = frames[(kind, relation, "account")].groupby("src").dst.nunique()
        counts = np.array([degree.get(key, 0) for key in maps[kind]])
        nodes[kind] = torch.tensor(np.stack([_log(counts, 10), counts > 1,
                                            counts > 3, counts == 0], axis=1), dtype=torch.float32)
    edge_index, edge_attr = {}, {}
    for rel, df in frames.items():
        src, name, dst = rel
        indices = torch.tensor(np.stack([df.src.map(maps[src]), df.dst.map(maps[dst])]).astype(np.int64))
        reverse = (dst, "rev_" + name, src)
        edge_index[rel], edge_index[reverse] = indices, indices.flip(0)
        if "timestamp" in df:
            attrs = np.stack([(as_of - df.timestamp.to_numpy()) / 86400,
                              _log(df.amount.to_numpy(), 1e6)], axis=1)
            edge_attr[rel] = torch.tensor(attrs, dtype=torch.float32)
            edge_attr[reverse] = edge_attr[rel]
    labels = torch.zeros(len(maps["account"]))
    masks = {name: torch.zeros(len(labels), dtype=torch.bool) for name in ("train", "val", "cal", "test")}
    label_df = pd.read_csv(root / "account_labels.csv")
    if label_df.account_id.duplicated().any() or not label_df.is_fraud.isin([0, 1]).all():
        raise ValueError("Invalid account labels")
    for row in label_df.itertuples():
        if row.account_id not in maps["account"]:
            raise ValueError("Label references missing account")
        i = maps["account"][row.account_id]
        labels[i] = float(row.is_fraud)
        masks[label_partition(row.account_id)][i] = True
    active = torch.tensor(raw_stats["activity_count"] > 0)
    masks = {k: v & active for k, v in masks.items()}
    owns = frames[("account", "owns", "vpa")]
    if owns.dst.duplicated().any():
        raise ValueError("Ambiguous VPA ownership")
    return {"node_features": nodes, "edge_index": edge_index, "edge_attr": edge_attr,
            "labels": labels, "label_mask": masks[split] if split != "full" else active,
            "label_masks": masks, "node_maps": maps, "account_stats": raw_stats,
            "vpa_to_account": dict(zip(owns.dst, owns.src)),
            "metadata": {"feature_dims": {k: v.shape[1] for k, v in nodes.items()},
                         "feature_version": FEATURE_VERSION, "as_of": as_of,
                         "split": split, "static_edges_assumed_at_inception": True,
                         "num_nodes": {k: len(v) for k, v in maps.items()}}}
