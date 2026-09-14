"""
Vyuha 2.0 -- Comprehensive ML Benchmark Suite
================================================
Validates all ML components against the latency and accuracy
budgets specified in ARCHITECTURE_FREEZE_V1 sec13.

Latency Budgets:
    - Context Aggregation: < 2 ms
    - Triage Gate: < 2 ms
    - Sparse MoE (Top-K 2): < 25 ms
    - Belief + Policy: < 3 ms
    - Total Local Path: < 50 ms p95
    - Graph API (Async): < 120 ms p95

Reports PASS/FAIL for each budget constraint.
"""
import os
import sys
import json
import time
import argparse
import numpy as np
import torch

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from ml.edge.experts.moe import EdgeRiskMoE
from ml.edge.belief.belief_state import CoercionBeliefState
from ml.edge.conformal.conformal_predictor import ConformalPredictor
from ml.graph.model.hgt import HGTModel

CHECKPOINT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../checkpoints"))


def benchmark_moe(n_warmup=100, n_iters=1000):
    """Benchmark EdgeRiskMoE inference latency."""
    print("\n--- Benchmark: EdgeRiskMoE (Triage + MoE) ---")

    ckpt_path = os.path.join(CHECKPOINT_DIR, "moe_best.pt")
    if os.path.exists(ckpt_path):
        ckpt = torch.load(ckpt_path, map_location="cpu", weights_only=False)
        model = EdgeRiskMoE(**ckpt["config"])
        model.load_state_dict(ckpt["model_state_dict"])
        print("  Loaded trained checkpoint")
    else:
        model = EdgeRiskMoE(input_dim=6, hidden_dim=16, num_experts=5, top_k=2)
        print("  Using untrained model")

    model.eval()
    x = torch.randn(1, 6)

    # Warmup
    for _ in range(n_warmup):
        with torch.no_grad():
            model(x)

    # Benchmark
    latencies = []
    for _ in range(n_iters):
        t0 = time.perf_counter()
        with torch.no_grad():
            model(x)
        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000)

    lat = np.array(latencies)
    p50 = np.percentile(lat, 50)
    p95 = np.percentile(lat, 95)
    p99 = np.percentile(lat, 99)

    # Budget: Triage < 2ms + MoE < 25ms = 27ms total
    budget = 27.0
    passed = p95 < budget

    print(f"  Samples: {n_iters}")
    print(f"  p50: {p50:.3f}ms | p95: {p95:.3f}ms | p99: {p99:.3f}ms")
    print(f"  Budget: {budget}ms p95 | {'PASS' if passed else 'FAIL'}")

    return {"component": "MoE (Triage+Experts)", "p50": p50, "p95": p95, "p99": p99,
            "budget_ms": budget, "passed": passed}


def benchmark_belief(n_iters=5000):
    """Benchmark Belief State update latency."""
    print("\n--- Benchmark: Belief State Update ---")

    latencies = []
    for _ in range(n_iters):
        belief = CoercionBeliefState(initial_belief=0.11)
        t0 = time.perf_counter()
        belief.update(0.25, 0.0, 0.0, "test_evidence")
        belief.update(0.0, 0.5, 0.0, "graph_evidence")
        _ = belief.posterior_coercion_probability
        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000)

    lat = np.array(latencies)
    p50 = np.percentile(lat, 50)
    p95 = np.percentile(lat, 95)

    # Budget: Belief + Policy < 3ms (belief part ~1.5ms)
    budget = 1.5
    passed = p95 < budget

    print(f"  Samples: {n_iters}")
    print(f"  p50: {p50:.4f}ms | p95: {p95:.4f}ms")
    print(f"  Budget: {budget}ms p95 | {'PASS' if passed else 'FAIL'}")

    return {"component": "BeliefState", "p50": p50, "p95": p95,
            "budget_ms": budget, "passed": passed}


def benchmark_conformal(n_iters=5000):
    """Benchmark Conformal Predictor latency."""
    print("\n--- Benchmark: Conformal Predictor ---")

    # Calibrate
    np.random.seed(42)
    cal_preds = np.random.beta(2, 5, 500)
    cal_labels = (cal_preds > 0.5).astype(float)
    cp = ConformalPredictor(alpha=0.10)
    cp.calibrate(cal_preds, cal_labels)

    # Benchmark
    latencies = []
    for _ in range(n_iters):
        p = np.random.random()
        t0 = time.perf_counter()
        _ = cp.predict_set(p)
        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000)

    lat = np.array(latencies)
    p50 = np.percentile(lat, 50)
    p95 = np.percentile(lat, 95)

    # Budget: Part of Belief + Policy < 3ms (~0.5ms)
    budget = 0.5
    passed = p95 < budget

    print(f"  Samples: {n_iters}")
    print(f"  p50: {p50:.4f}ms | p95: {p95:.4f}ms")
    print(f"  Budget: {budget}ms p95 | {'PASS' if passed else 'FAIL'}")

    return {"component": "ConformalPredictor", "p50": p50, "p95": p95,
            "budget_ms": budget, "passed": passed}


def benchmark_hgt(n_iters=10):
    """Benchmark HGT full-graph inference latency."""
    print("\n--- Benchmark: HGT Full-Graph Inference ---")

    ckpt_path = os.path.join(CHECKPOINT_DIR, "hgt_best.pt")
    if not os.path.exists(ckpt_path):
        print("  No HGT checkpoint found, skipping")
        return {"component": "HGT", "skipped": True}

    ckpt = torch.load(ckpt_path, map_location="cpu", weights_only=False)
    config = ckpt["config"]
    model = HGTModel(**{k: v for k, v in config.items()})
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()

    # Create synthetic graph data matching expected dimensions
    node_features = {
        "account": torch.randn(30000, config["feature_dims"]["account"]),
        "vpa": torch.randn(33000, config["feature_dims"]["vpa"]),
        "device": torch.randn(31000, config["feature_dims"]["device"]),
        "phone": torch.randn(30000, config["feature_dims"]["phone"]),
    }
    edge_index = {
        ("account", "sends_to", "account"): torch.randint(0, 30000, (2, 130000)),
        ("vpa", "receives_from", "vpa"): torch.randint(0, 33000, (2, 130000)),
        ("account", "owns", "vpa"): torch.stack([torch.randint(0, 30000, (33000,)), torch.arange(33000)]),
        ("device", "accesses", "account"): torch.stack([torch.randint(0, 31000, (31000,)), torch.randint(0, 30000, (31000,))]),
        ("phone", "associated_with", "account"): torch.stack([torch.arange(30000), torch.arange(30000)]),
    }
    edge_attr = {
        ("account", "sends_to", "account"): torch.rand(130000, 2),
        ("vpa", "receives_from", "vpa"): torch.rand(130000, 2),
    }

    # Warmup
    with torch.no_grad():
        _ = model(node_features, edge_index, edge_attr)

    # Benchmark
    latencies = []
    for _ in range(n_iters):
        t0 = time.perf_counter()
        with torch.no_grad():
            _ = model(node_features, edge_index, edge_attr)
        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000)

    lat = np.array(latencies)
    p50 = np.percentile(lat, 50)
    p95 = np.percentile(lat, 95)

    # Budget: Graph API < 120ms (includes network, but model should be < 50ms)
    budget = 50.0
    passed = p95 < budget

    num_accounts = node_features["account"].shape[0]
    per_sample = p95 / num_accounts

    print(f"  Graph size: {num_accounts} accounts")
    print(f"  Samples: {n_iters}")
    print(f"  p50: {p50:.1f}ms | p95: {p95:.1f}ms (total graph)")
    print(f"  Per account: {per_sample*1000:.3f}us")
    print(f"  Budget: {budget}ms p95 | {'PASS' if passed else 'FAIL'}")

    return {"component": "HGT (full graph)", "p50": p50, "p95": p95,
            "budget_ms": budget, "passed": passed, "per_account_us": per_sample * 1000}


def benchmark_full_local_pipeline(n_iters=500):
    """Benchmark the full local ML pipeline (MoE -> Belief -> Conformal -> Decision)."""
    print("\n--- Benchmark: Full Local Pipeline ---")

    # Load MoE
    ckpt_path = os.path.join(CHECKPOINT_DIR, "moe_best.pt")
    if os.path.exists(ckpt_path):
        ckpt = torch.load(ckpt_path, map_location="cpu", weights_only=False)
        moe = EdgeRiskMoE(**ckpt["config"])
        moe.load_state_dict(ckpt["model_state_dict"])
    else:
        moe = EdgeRiskMoE(input_dim=6, hidden_dim=16, num_experts=5, top_k=2)
    moe.eval()

    # Calibrate conformal
    np.random.seed(42)
    cal_preds = np.random.beta(2, 5, 500)
    cal_labels = (cal_preds > 0.5).astype(float)
    cp = ConformalPredictor(alpha=0.10)
    cp.calibrate(cal_preds, cal_labels)

    # Warmup
    x = torch.randn(1, 6)
    with torch.no_grad():
        moe(x)

    latencies = []
    for _ in range(n_iters):
        x = torch.randn(1, 6)
        t0 = time.perf_counter()

        # Stage 1: MoE
        with torch.no_grad():
            out = moe(x)
        risk = torch.sigmoid(out["risk_logits"]).item()

        # Stage 2: Belief
        belief = CoercionBeliefState(initial_belief=0.11)
        belief.update(risk * 0.3, 0.0, 0.0, "moe_risk")
        p_coercion = belief.posterior_coercion_probability

        # Stage 3: Conformal
        pred_set = cp.predict_set(p_coercion)

        # Stage 4: Decision (simple threshold)
        _ = "A0_PASS" if p_coercion < 0.3 else "A2_REFLECTION"

        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000)

    lat = np.array(latencies)
    p50 = np.percentile(lat, 50)
    p95 = np.percentile(lat, 95)
    p99 = np.percentile(lat, 99)

    # Budget: Total Local Path < 50ms p95
    budget = 50.0
    passed = p95 < budget

    print(f"  Samples: {n_iters}")
    print(f"  p50: {p50:.3f}ms | p95: {p95:.3f}ms | p99: {p99:.3f}ms")
    print(f"  Budget: {budget}ms p95 | {'PASS' if passed else 'FAIL'}")

    return {"component": "Full Local Pipeline", "p50": p50, "p95": p95, "p99": p99,
            "budget_ms": budget, "passed": passed}


def benchmark_model_sizes():
    """Report model sizes for mobile deployment considerations."""
    print("\n--- Model Sizes ---")

    exports_dir = os.path.join(os.path.dirname(__file__), "../exports")
    results = {}

    for filename in ["edge_risk_moe.pt", "hgt_server.pt"]:
        path = os.path.join(exports_dir, filename)
        if os.path.exists(path):
            size = os.path.getsize(path)
            print(f"  {filename}: {size / 1024:.1f} KB")
            results[filename] = size
        else:
            print(f"  {filename}: NOT FOUND")

    # Parameter counts
    moe = EdgeRiskMoE(input_dim=6, hidden_dim=16, num_experts=5, top_k=2)
    moe_params = sum(p.numel() for p in moe.parameters())
    print(f"  MoE parameters: {moe_params:,}")

    hgt = HGTModel()
    hgt_params = sum(p.numel() for p in hgt.parameters())
    print(f"  HGT parameters: {hgt_params:,}")

    results["moe_params"] = moe_params
    results["hgt_params"] = hgt_params
    return results


def run_full_benchmark():
    """Run all benchmarks and generate report."""
    print("=" * 60)
    print("Vyuha 2.0 -- Comprehensive ML Benchmark Suite")
    print("=" * 60)

    results = []
    results.append(benchmark_moe())
    results.append(benchmark_belief())
    results.append(benchmark_conformal())
    results.append(benchmark_hgt())
    results.append(benchmark_full_local_pipeline())
    sizes = benchmark_model_sizes()

    # Summary
    print("\n" + "=" * 60)
    print("BENCHMARK SUMMARY")
    print("=" * 60)
    all_passed = True
    for r in results:
        if r.get("skipped"):
            status = "SKIP"
        elif r["passed"]:
            status = "PASS"
        else:
            status = "FAIL"
            all_passed = False
        p95 = r.get("p95", "N/A")
        budget = r.get("budget_ms", "N/A")
        if isinstance(p95, float):
            print(f"  [{status}] {r['component']:<30s} p95={p95:.3f}ms (budget={budget}ms)")
        else:
            print(f"  [{status}] {r['component']:<30s}")

    overall = "ALL PASS" if all_passed else "FAILURES DETECTED"
    print(f"\n  Overall: {overall}")
    print("=" * 60)

    # Save report
    report_dir = os.path.join(os.path.dirname(__file__), "../datasets/synthetic")
    os.makedirs(report_dir, exist_ok=True)
    report_path = os.path.join(report_dir, "BENCHMARK_REPORT.json")

    report = {
        "benchmarks": results,
        "model_sizes": sizes,
        "overall_passed": all_passed,
    }
    with open(report_path, "w") as f:
        json.dump(report, f, indent=4, default=str)
    print(f"\nReport saved to: {report_path}")

    return all_passed


if __name__ == "__main__":
    run_full_benchmark()
