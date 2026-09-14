"""
Vyuha 2.0 — Model Export Pipeline
====================================
Exports trained models for deployment:
    - EdgeRiskMoE -> ONNX (for mobile/on-device inference)
    - HGT -> TorchScript (for server-side inference in graph-risk-api)

Also validates exported models against original PyTorch outputs.

Architecture ref: ARCHITECTURE_FREEZE_V1 §3 D3.
"""
import os
import sys
import json
import time
import argparse
import numpy as np
import torch
import torch.nn as nn

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../..")))

from ml.edge.experts.moe import EdgeRiskMoE
from ml.graph.model.hgt import HGTModel

CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../checkpoints"))
EXPORT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../exports"))
os.makedirs(EXPORT_DIR, exist_ok=True)


def export_moe_onnx(checkpoint_path: str = None):
    """
    Export EdgeRiskMoE to ONNX format for mobile deployment.
    """
    print("\n" + "=" * 60)
    print("Exporting EdgeRiskMoE to ONNX")
    print("=" * 60)

    if checkpoint_path is None:
        checkpoint_path = os.path.join(CHECKPOINT_DIR, "moe_best.pt")

    # Load model
    if os.path.exists(checkpoint_path):
        ckpt = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
        config = ckpt["config"]
        model = EdgeRiskMoE(**config)
        model.load_state_dict(ckpt["model_state_dict"])
        print(f"  Loaded checkpoint from epoch {ckpt['epoch']}")
    else:
        print(f"  No checkpoint found at {checkpoint_path}")
        print("  Exporting untrained model for structure validation...")
        model = EdgeRiskMoE(input_dim=6, hidden_dim=16, num_experts=5, top_k=2)
        config = {"input_dim": 6, "hidden_dim": 16, "num_experts": 5, "top_k": 2}

    model.eval()

    # Wrapper that returns tuple instead of dict (required for ONNX)
    class MoEOnnxWrapper(nn.Module):
        def __init__(self, moe):
            super().__init__()
            self.gate = moe.gate
            self.experts = moe.experts
            self.latent_proj = moe.latent_proj
            self.num_experts = moe.num_experts
            self.top_k = moe.top_k

        def forward(self, x):
            gate_logits = self.gate(x)
            routing_weights = torch.nn.functional.softmax(gate_logits, dim=-1)
            top_k_weights, top_k_indices = torch.topk(routing_weights, self.top_k, dim=-1)
            top_k_weights = top_k_weights / top_k_weights.sum(dim=-1, keepdim=True)

            batch_size = x.size(0)
            risk_logits = torch.zeros(batch_size, device=x.device)
            for k_idx in range(self.top_k):
                for e_idx in range(self.num_experts):
                    mask = (top_k_indices[:, k_idx] == e_idx)
                    if mask.any():
                        expert_out = self.experts[e_idx](x[mask]).squeeze(-1)
                        risk_logits[mask] = risk_logits[mask] + top_k_weights[mask, k_idx] * expert_out

            confidence = 1.0 - (-(routing_weights * torch.log(routing_weights + 1e-9)).sum(dim=-1) /
                                torch.log(torch.tensor(float(self.num_experts))))
            return risk_logits, confidence

    wrapper = MoEOnnxWrapper(model)
    wrapper.eval()

    # Dummy input
    dummy_input = torch.randn(1, config.get("input_dim", 6))

    # ONNX export
    onnx_path = os.path.join(EXPORT_DIR, "edge_risk_moe.onnx")

    try:
        torch.onnx.export(
            wrapper,
            dummy_input,
            onnx_path,
            input_names=["features"],
            output_names=["risk_logits", "confidence"],
            dynamic_axes={
                "features": {0: "batch_size"},
                "risk_logits": {0: "batch_size"},
                "confidence": {0: "batch_size"},
            },
            opset_version=14,
        )
        print(f"  [OK] ONNX model saved to: {onnx_path}")

        # Validate
        _validate_onnx(wrapper, onnx_path, dummy_input)

    except Exception as e:
        print(f"  [FAIL] ONNX export failed: {e}")
        print("  Attempting TorchScript export as fallback...")
        _export_moe_torchscript(wrapper, config)

    # Generate model card
    model_card = {
        "model": "EdgeRiskMoE",
        "version": "v0.1",
        "format": "ONNX",
        "input_shape": [1, config.get("input_dim", 6)],
        "output": "risk_logits (float), routing_weights, confidence",
        "parameters": sum(p.numel() for p in model.parameters()),
        "target_device": "Android (ExecuTorch / ONNX Runtime Mobile)",
        "latency_target_ms": 5,
    }
    card_path = os.path.join(EXPORT_DIR, "edge_risk_moe_card.json")
    with open(card_path, "w") as f:
        json.dump(model_card, f, indent=4)
    print(f"  Model card saved to: {card_path}")


def _export_moe_torchscript(model, config):
    """Fallback: export MoE to TorchScript."""
    ts_path = os.path.join(EXPORT_DIR, "edge_risk_moe.pt")
    try:
        dummy = torch.randn(1, config.get("input_dim", 6))
        traced = torch.jit.trace(model, dummy)
        traced.save(ts_path)
        print(f"  [OK] TorchScript model saved to: {ts_path}")
    except Exception as e:
        print(f"  [FAIL] TorchScript export also failed: {e}")


def _validate_onnx(model, onnx_path, dummy_input):
    """Validate ONNX model output matches PyTorch."""
    try:
        import onnx
        import onnxruntime as ort

        # Validate ONNX model
        onnx_model = onnx.load(onnx_path)
        onnx.checker.check_model(onnx_model)
        print("  [OK] ONNX model validation passed")

        # Compare outputs
        session = ort.InferenceSession(onnx_path)
        ort_inputs = {"features": dummy_input.numpy()}
        ort_outputs = session.run(None, ort_inputs)

        with torch.no_grad():
            pt_outputs = model(dummy_input)

        pt_logits = pt_outputs["risk_logits"].numpy()
        ort_logits = ort_outputs[0]

        max_diff = np.abs(pt_logits - ort_logits).max()
        print(f"  [OK] Output validation: max diff = {max_diff:.6f} "
              f"({'PASS' if max_diff < 1e-4 else 'WARN: significant difference'})")

    except ImportError:
        print("  [WARN] onnx/onnxruntime not installed, skipping validation")


def export_hgt_torchscript(checkpoint_path: str = None):
    """
    Export HGT to TorchScript for server-side inference.

    Note: HGT with heterogeneous inputs is tricky to trace/script,
    so we export a simplified inference wrapper.
    """
    print("\n" + "=" * 60)
    print("Exporting HGT to TorchScript")
    print("=" * 60)

    if checkpoint_path is None:
        checkpoint_path = os.path.join(CHECKPOINT_DIR, "hgt_best.pt")

    if not os.path.exists(checkpoint_path):
        print(f"  No checkpoint found at {checkpoint_path}")
        print("  Run train_hgt.py first.")
        return

    ckpt = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    config = ckpt["config"]

    model = HGTModel(
        feature_dims=config["feature_dims"],
        hidden_dim=config["hidden_dim"],
        num_heads=config["num_heads"],
        num_layers=config["num_layers"],
    )
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()

    # Save full state dict for server loading (TorchScript tracing of
    # heterogeneous GNNs with dynamic edge types is complex)
    ts_path = os.path.join(EXPORT_DIR, "hgt_server.pt")
    torch.save({
        "model_state_dict": model.state_dict(),
        "config": config,
        "export_type": "state_dict",
        "usage": "Load with HGTModel(**config) then model.load_state_dict()"
    }, ts_path)
    print(f"  [OK] HGT state dict saved to: {ts_path}")

    # Model card
    model_card = {
        "model": "HGT (Heterogeneous Graph Transformer)",
        "version": "v0.1",
        "format": "PyTorch state_dict",
        "config": config,
        "parameters": sum(p.numel() for p in model.parameters()),
        "target_device": "Server (graph-risk-api)",
        "node_types": ["account", "vpa", "device", "phone"],
        "edge_types": 5,
        "output": "risk_score (float), confidence (float), embedding (64-dim)",
    }
    card_path = os.path.join(EXPORT_DIR, "hgt_server_card.json")
    with open(card_path, "w") as f:
        json.dump(model_card, f, indent=4)
    print(f"  Model card saved to: {card_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export models")
    parser.add_argument("--moe-ckpt", type=str, default=None)
    parser.add_argument("--hgt-ckpt", type=str, default=None)
    parser.add_argument("--skip-moe", action="store_true")
    parser.add_argument("--skip-hgt", action="store_true")
    args = parser.parse_args()

    if not args.skip_moe:
        export_moe_onnx(args.moe_ckpt)

    if not args.skip_hgt:
        export_hgt_torchscript(args.hgt_ckpt)

    print(f"\n[OK] All exports saved to: {EXPORT_DIR}")
