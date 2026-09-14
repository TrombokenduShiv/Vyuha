"""Export trained MoE with data-independent routing graph, then verify parity."""
from pathlib import Path
import hashlib
import json
import shutil
import io
import numpy as np
import torch
from torch import nn
from ml.policy.runtime import ROOT, PaymentRiskEngine
from ml.artifacts import write_bytes_atomic


class PortableMoE(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.gate, self.experts = model.gate, model.experts

    def forward(self, x):
        x = x * x.new_tensor([1, 1, 1, 1, 0, 1])
        weights, indices = self.gate(x).softmax(-1).topk(2, dim=-1)
        weights = weights / weights.sum(-1, keepdim=True)
        # Dense export avoids tracing input-dependent Python branches. Kotlin
        # runtime executes only selected experts; ONNX evaluates all five.
        outputs = torch.cat([expert(x) for expert in self.experts], dim=-1)
        return (outputs.gather(1, indices) * weights).sum(-1)


def export_moe_onnx(checkpoint_path=None):
    import onnx
    import onnxruntime as ort
    torch.set_num_threads(2)
    engine = PaymentRiskEngine(checkpoint_path)
    wrapper = PortableMoE(engine.moe).eval()
    root = ROOT / "exports"
    root.mkdir(exist_ok=True)
    path = root / "edge_risk_moe.onnx"
    torch.onnx.export(wrapper, torch.zeros(1, 6), str(path), input_names=["features"],
                      output_names=["risk_logits"], dynamic_axes={"features": {0: "batch"},
                      "risk_logits": {0: "batch"}}, opset_version=17, dynamo=False)
    onnx.checker.check_model(onnx.load(str(path)))
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])
    torch.manual_seed(12345)
    maximum = 0.0
    for x in [torch.zeros(1, 6), torch.ones(1, 6), torch.rand(32, 6), torch.rand(257, 6)]:
        with torch.inference_mode():
            expected = engine.moe(x)["risk_logits"].numpy()
        actual = session.run(None, {"features": x.numpy()})[0]
        np.testing.assert_allclose(actual, expected, atol=1e-5, rtol=1e-5)
        maximum = max(maximum, float(np.abs(actual - expected).max()))
    buffer = io.BytesIO()
    torch.jit.save(torch.jit.trace(wrapper, torch.rand(4, 6)), buffer)
    write_bytes_atomic(root / "edge_risk_moe.pt", buffer.getvalue())
    bundle = {"schema_version": 2, "objective": "agency_only", "checkpoint_sha256": engine.checksum,
              "weights": {k: v.tolist() for k, v in engine.moe.state_dict().items()}}
    resources = ROOT / "packages/android-sdk/src/main/resources"
    resources.mkdir(parents=True, exist_ok=True)
    (resources / "edge_moe.json").write_text(json.dumps(bundle, separators=(",", ":")))
    shutil.copyfile(ROOT / "checkpoints/frozen_policy.json", resources / "frozen_policy.json")
    card = {"version": "2.1", "data_kind": "synthetic", "objective": "agency_only",
            "parameters": sum(p.numel() for p in engine.moe.parameters()),
            "checkpoint_sha256": engine.checksum, "onnx_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "export_parity_max_logit_error": maximum, "parity_cases": 291,
            "onnx_routing": "dense evaluation, top-2 combination", "android_routing": "top-2 sparse",
            "device_latency": "requires physical Android measurement"}
    (root / "edge_risk_moe_card.json").write_text(json.dumps(card, indent=2))
    print(json.dumps(card, indent=2))


def export_hgt_torchscript(checkpoint_path=None):
    # Heterogeneous graph shapes vary. Ship versioned state/config, never a
    # single-graph trace falsely advertised as a general inference executable.
    src = Path(checkpoint_path or ROOT / "checkpoints/hgt_best.pt")
    checkpoint = torch.load(src, map_location="cpu", weights_only=True)
    if checkpoint.get("schema_version") != 2:
        raise ValueError("Retrain HGT before export")
    write_bytes_atomic(ROOT / "exports/hgt_server.pt", src.read_bytes())
    (ROOT / "exports/hgt_server_card.json").write_text(json.dumps({
        "version": "2.1", "format": "pytorch_state_dict_with_config", "data_kind": checkpoint["data_kind"],
        "checkpoint_sha256": hashlib.sha256(src.read_bytes()).hexdigest(), "config": checkpoint["config"],
        "feature_version": checkpoint["feature_version"], "calibration": checkpoint["calibration"]}, indent=2))


if __name__ == "__main__":
    export_moe_onnx()
    export_hgt_torchscript()
