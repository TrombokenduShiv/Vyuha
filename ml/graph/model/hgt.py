"""
Vyuha 2.0 — Heterogeneous Graph Transformer (HGT)
====================================================
Core ST-GNN model for fraud detection on the heterogeneous
transaction graph.

Architecture:
    - 4 node types: account, vpa, device, phone
    - 5 edge types (see loader.py)
    - 2-layer HGT with 64-dim hidden, 4 attention heads
    - Sinusoidal temporal position encoding on edge timestamps
    - Binary classification head on account nodes (fraud/legitimate)
    - Output: (risk_score, confidence, node_embedding)

Architecture ref: ADR-003, ARCHITECTURE_FREEZE_V1 §5 D5.
Decision D5: "ST-GNN architecture (HGT + temporal encoding)".
"""
import torch
import torch.nn as nn
import torch.nn.functional as F
import math
from .temporal_encoder import TemporalEncoder


class HGTAttentionLayer(nn.Module):
    """
    Single Heterogeneous Graph Transformer attention layer.

    For each edge type (src_type, rel_type, dst_type), computes:
        Q = W_q[dst_type] * h_dst
        K = W_k[src_type, rel_type] * h_src
        V = W_v[src_type, rel_type] * h_src
        attention = softmax(Q * K^T / sqrt(d_k))
        output = attention * V

    This is a simplified version that operates on pre-built edge indices
    without requiring PyTorch Geometric's MessagePassing.
    """

    def __init__(self, in_dims: dict, hidden_dim: int, num_heads: int,
                 node_types: list, edge_types: list):
        super().__init__()
        self.hidden_dim = hidden_dim
        self.num_heads = num_heads
        self.d_k = hidden_dim // num_heads

        self.node_types = node_types
        self.edge_types = edge_types

        # Per-node-type input projections
        self.input_proj = nn.ModuleDict({
            ntype: nn.Linear(in_dims[ntype], hidden_dim)
            for ntype in node_types
        })

        # Per-edge-type key/value projections
        self.k_proj = nn.ModuleDict()
        self.v_proj = nn.ModuleDict()
        self.q_proj = nn.ModuleDict()
        self.edge_proj = nn.ModuleDict()

        for src, rel, dst in edge_types:
            key = f"{src}__{rel}__{dst}"
            self.k_proj[key] = nn.Linear(hidden_dim, hidden_dim)
            self.v_proj[key] = nn.Linear(hidden_dim, hidden_dim)
            self.q_proj[key] = nn.Linear(hidden_dim, hidden_dim)
            self.edge_proj[key] = nn.Linear(hidden_dim, num_heads)

        # Per-node-type output projections
        self.output_proj = nn.ModuleDict({
            ntype: nn.Linear(hidden_dim, hidden_dim)
            for ntype in node_types
        })

        # Layer norms
        self.layer_norms = nn.ModuleDict({
            ntype: nn.LayerNorm(hidden_dim)
            for ntype in node_types
        })

    def forward(self, node_features: dict, edge_index: dict,
                edge_temporal_emb: dict = None) -> dict:
        """
        Args:
            node_features: {node_type: Tensor[num_nodes, in_dim]}
            edge_index: {(src_type, rel_type, dst_type): Tensor[2, num_edges]}
            edge_temporal_emb: {(src, rel, dst): Tensor[num_edges, hidden_dim]} (optional)

        Returns:
            {node_type: Tensor[num_nodes, hidden_dim]}
        """
        # Project all node features to hidden_dim
        h = {}
        for ntype in self.node_types:
            if ntype in node_features:
                h[ntype] = self.input_proj[ntype](node_features[ntype])
            else:
                h[ntype] = node_features[ntype]  # Already projected

        # Aggregate messages per destination node type
        output_msgs = {ntype: [] for ntype in self.node_types}
        output_counts = {ntype: 0 for ntype in self.node_types}

        for (src_type, rel_type, dst_type) in self.edge_types:
            key = f"{src_type}__{rel_type}__{dst_type}"
            edge_key = (src_type, rel_type, dst_type)

            if edge_key not in edge_index:
                continue

            ei = edge_index[edge_key]
            if ei.shape[1] == 0:
                continue

            src_idx = ei[0]
            dst_idx = ei[1]

            h_src = h[src_type][src_idx]  # [E, hidden]
            h_dst = h[dst_type][dst_idx]  # [E, hidden]

            # Compute K, V from source, Q from destination
            K = self.k_proj[key](h_src)
            V = self.v_proj[key](h_src)
            Q = self.q_proj[key](h_dst)

            # Add temporal encoding to K if available
            if edge_temporal_emb and edge_key in edge_temporal_emb:
                te = edge_temporal_emb[edge_key]
                K = K + te

            # Scaled dot-product attention per edge
            # attention weight = (Q * K) / sqrt(d_k)
            attn_weights = (Q * K).sum(dim=-1) / math.sqrt(self.d_k)

            # Scatter softmax: normalize attention per destination node
            # Simple approach: use exp + scatter_add
            attn_exp = torch.exp(attn_weights - attn_weights.max())

            # Aggregate
            num_dst = h[dst_type].shape[0]
            attn_sum = torch.zeros(num_dst, device=attn_exp.device)
            attn_sum.scatter_add_(0, dst_idx, attn_exp)
            attn_norm = attn_exp / (attn_sum[dst_idx] + 1e-9)

            # Weighted messages
            msg = attn_norm.unsqueeze(-1) * V  # [E, hidden]

            # Scatter-add messages to destination nodes
            out = torch.zeros(num_dst, self.hidden_dim, device=msg.device)
            out.scatter_add_(0, dst_idx.unsqueeze(-1).expand_as(msg), msg)

            output_msgs[dst_type].append(out)
            output_counts[dst_type] += 1

        # Combine messages and apply output projection + residual + LayerNorm
        result = {}
        for ntype in self.node_types:
            if output_counts[ntype] > 0:
                aggregated = sum(output_msgs[ntype]) / output_counts[ntype]
                projected = self.output_proj[ntype](aggregated)
                # Residual + LayerNorm
                result[ntype] = self.layer_norms[ntype](h[ntype] + projected)
            else:
                result[ntype] = h[ntype]

        return result


class HGTModel(nn.Module):
    """
    Full Heterogeneous Graph Transformer for fraud detection.

    2-layer HGT -> binary classification head on account nodes.
    """

    NODE_TYPES = ["account", "vpa", "device", "phone"]
    EDGE_TYPES = [
        ("account", "sends_to", "account"),
        ("vpa", "receives_from", "vpa"),
        ("account", "owns", "vpa"),
        ("device", "accesses", "account"),
        ("phone", "associated_with", "account"),
    ]

    def __init__(self, feature_dims: dict = None, hidden_dim: int = 64,
                 num_heads: int = 4, num_layers: int = 2, dropout: float = 0.2,
                 temporal_dim: int = 16):
        super().__init__()

        if feature_dims is None:
            feature_dims = {"account": 8, "vpa": 4, "device": 4, "phone": 4}

        self.hidden_dim = hidden_dim
        self.num_layers = num_layers

        # Temporal encoder for edge timestamps
        self.temporal_encoder = TemporalEncoder(d_model=hidden_dim)

        # HGT layers
        self.layers = nn.ModuleList()
        for i in range(num_layers):
            in_dims = feature_dims if i == 0 else {nt: hidden_dim for nt in self.NODE_TYPES}
            self.layers.append(
                HGTAttentionLayer(
                    in_dims=in_dims,
                    hidden_dim=hidden_dim,
                    num_heads=num_heads,
                    node_types=self.NODE_TYPES,
                    edge_types=self.EDGE_TYPES
                )
            )

        self.dropout = nn.Dropout(dropout)

        # Classification head (account nodes only)
        self.classifier = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_dim // 2, 1)
        )

        # Confidence head (separate from risk)
        self.confidence_head = nn.Sequential(
            nn.Linear(hidden_dim, hidden_dim // 4),
            nn.ReLU(),
            nn.Linear(hidden_dim // 4, 1),
            nn.Sigmoid()
        )

    def forward(self, node_features: dict, edge_index: dict,
                edge_attr: dict = None) -> dict:
        """
        Args:
            node_features: {node_type: Tensor[N, feat_dim]}
            edge_index: {(src, rel, dst): Tensor[2, E]}
            edge_attr: {(src, rel, dst): Tensor[E, 2]} where col0=timestamp, col1=amount

        Returns:
            dict with:
                - risk_logits: Tensor[N_accounts] — raw risk logits
                - risk_scores: Tensor[N_accounts] — sigmoid(risk_logits)
                - confidence: Tensor[N_accounts] — model confidence [0, 1]
                - embeddings: Tensor[N_accounts, hidden_dim] — node embeddings
        """
        # Compute temporal edge embeddings
        edge_temporal_emb = {}
        if edge_attr:
            for edge_key, attr in edge_attr.items():
                if attr.shape[0] > 0:
                    timestamps = attr[:, 0]  # First column is normalized timestamp
                    edge_temporal_emb[edge_key] = self.temporal_encoder(timestamps)

        # Forward through HGT layers
        h = node_features
        for layer in self.layers:
            h = layer(h, edge_index, edge_temporal_emb)
            # Apply dropout between layers (not on last)
            h = {ntype: self.dropout(feat) for ntype, feat in h.items()}

        # Classification on account nodes
        account_emb = h["account"]
        risk_logits = self.classifier(account_emb).squeeze(-1)
        risk_scores = torch.sigmoid(risk_logits)
        confidence = self.confidence_head(account_emb).squeeze(-1)

        return {
            "risk_logits": risk_logits,
            "risk_scores": risk_scores,
            "confidence": confidence,
            "embeddings": account_emb,
        }


if __name__ == "__main__":
    print("=" * 60)
    print("Vyuha 2.0 — HGT Model Smoke Test")
    print("=" * 60)

    model = HGTModel()
    print(f"Model parameters: {sum(p.numel() for p in model.parameters()):,}")

    # Fake data
    node_features = {
        "account": torch.randn(100, 8),
        "vpa": torch.randn(120, 4),
        "device": torch.randn(105, 4),
        "phone": torch.randn(100, 4),
    }
    edge_index = {
        ("account", "sends_to", "account"): torch.randint(0, 100, (2, 500)),
        ("vpa", "receives_from", "vpa"): torch.randint(0, 120, (2, 500)),
        ("account", "owns", "vpa"): torch.randint(0, 100, (2, 120)).clamp(max=99),
        ("device", "accesses", "account"): torch.randint(0, 105, (2, 105)).clamp(max=99),
        ("phone", "associated_with", "account"): torch.randint(0, 100, (2, 100)),
    }
    # Fix destination indices
    edge_index[("account", "owns", "vpa")][1] = torch.randint(0, 120, (120,))
    edge_index[("device", "accesses", "account")][1] = torch.randint(0, 100, (105,))

    edge_attr = {
        ("account", "sends_to", "account"): torch.rand(500, 2),
        ("vpa", "receives_from", "vpa"): torch.rand(500, 2),
    }

    model.eval()
    with torch.no_grad():
        out = model(node_features, edge_index, edge_attr)

    print(f"\nRisk logits shape: {out['risk_logits'].shape}")
    print(f"Risk scores range: [{out['risk_scores'].min():.3f}, {out['risk_scores'].max():.3f}]")
    print(f"Confidence range: [{out['confidence'].min():.3f}, {out['confidence'].max():.3f}]")
    print(f"Embeddings shape: {out['embeddings'].shape}")
    print("\n[OK] HGT smoke test passed.")
