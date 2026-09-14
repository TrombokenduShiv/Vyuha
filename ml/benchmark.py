"""Measured latency budgets; missing device/network evidence is UNMEASURED.

Exit 1 on a measured budget failure. --require-all additionally fails if any
declared budget has no measurement. Never divide full-graph time by node count
and call that request latency; never substitute random/untrained models.
"""
import argparse
import hashlib
import importlib.util
import json
import platform
import time
from uuid import uuid4
import numpy as np
import torch
from fastapi.testclient import TestClient
from ml.policy.runtime import ROOT, PaymentContext, PaymentRiskEngine, decide
from ml.graph.inference.serve import GraphInferenceEngine
from ml.graph.inference.tokens import TokenSigner


def measure(name, fn, budget, iterations, warmup=30):
    for _ in range(warmup):
        fn()
    timings = []
    for _ in range(iterations):
        start = time.perf_counter_ns()
        fn()
        timings.append((time.perf_counter_ns() - start) / 1e6)
    p50, p95, p99 = map(float, np.percentile(timings, [50, 95, 99]))
    return {"component": name, "status": "PASS" if budget is None or p95 < budget else "FAIL",
            "budget_p95_ms": budget, "p50_ms": p50, "p95_ms": p95, "p99_ms": p99,
            "max_ms": max(timings), "samples": iterations, "warmup": warmup}


def run(iterations=500, require_all=False, api_url=None):
    if iterations < 20:
        raise ValueError("At least 20 timing samples required")
    torch.set_num_threads(2)
    start = time.perf_counter()
    engine = PaymentRiskEngine(policy_path=ROOT / "checkpoints/frozen_policy.json")
    cold_moe_ms = (time.perf_counter() - start) * 1000
    contexts = [PaymentContext(), PaymentContext(True, True, 1, .9, .9),
                PaymentContext(amount=.3, novelty=.95, online_purchase=True)]
    index = 0
    def next_context():
        nonlocal index
        result = contexts[index % len(contexts)]
        index += 1
        return result
    def moe():
        with torch.inference_mode():
            return engine.moe(next_context().pack())
    def gate():
        with torch.inference_mode():
            return engine.moe.gate(next_context().pack()).softmax(-1).topk(2)
    results = [measure("feature_packing_python", lambda: next_context().pack(), 2, iterations),
               measure("triage_routing_python", gate, 5, iterations),
               measure("moe_including_packing_python", moe, 20, iterations),
               measure("belief_conformal_python", lambda: engine.conformal.predict_set(.45), 2, iterations),
               measure("policy_python", lambda: decide(.1, .92, False, contexts[2], engine.policy), 2, iterations),
               measure("local_path_python", lambda: engine.evaluate(next_context(), .92), 50, iterations)]
    local = results[-1]
    results.append({"component": "local_path_python_median", "budget_median_ms": 30,
                    "p50_ms": local["p50_ms"], "status": "PASS" if local["p50_ms"] < 30 else "FAIL"})
    graph = GraphInferenceEngine(allow_synthetic=True)
    started = time.perf_counter()
    graph.load()
    cold_graph_ms = (time.perf_counter() - started) * 1000
    # Query a real mapped receiver, not just the unknown branch.
    vpa = next(iter(graph._snapshot.by_vpa))
    signer = TokenSigner()
    sid = str(uuid4())
    results.append(measure("graph_snapshot_lookup", lambda: graph.query_risk(vpa_hash=vpa), None, iterations))
    results.append(measure("graph_lookup_and_sign", lambda: signer.issue(graph.query_risk(vpa_hash=vpa), vpa, sid, "vyuha-demo"), 120, iterations))
    spec = importlib.util.spec_from_file_location("vyuha_graph_api", ROOT / "services/graph-risk-api/main.py")
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    with TestClient(api.create_app(engine=graph, signer=signer, demo=True)) as client:
        def request():
            response = client.post("/v1/graph-risk", json={"vpa_hash": vpa, "session_id": sid})
            response.raise_for_status()
            if response.json()["risk_score"] is None:
                raise RuntimeError("Benchmark receiver must have measured risk")
        results.append(measure("graph_api_in_process", request, 120, iterations))
    if api_url:
        import httpx
        with httpx.Client(base_url=api_url, timeout=3) as client:
            def request_http():
                r = client.post("/v1/graph-risk", json={"vpa_hash": vpa, "session_id": sid})
                r.raise_for_status()
            results.append(measure("graph_api_http", request_http, 120, iterations))
    else:
        results.append({"component": "graph_api_network", "status": "UNMEASURED", "budget_p95_ms": 120,
                        "reason": "Pass --api-url for actual HTTP round trips; in-process is not network latency"})
    for name, budget in [("android_context_extraction", 5), ("android_feature_packing", 2),
                         ("android_triage", 5), ("android_moe", 20), ("android_belief", 2),
                         ("android_policy", 2), ("android_total_local", 50)]:
        results.append({"component": name, "status": "UNMEASURED", "budget_p95_ms": budget,
                        "reason": "Requires physical target Android device measurements"})
    report = {"environment": {"platform": platform.platform(), "python": platform.python_version(),
                              "torch": torch.__version__, "cpu": platform.processor(), "torch_threads": torch.get_num_threads()},
              "moe_sha256": engine.checksum, "graph_sha256": hashlib.sha256((ROOT / "checkpoints/hgt_best.pt").read_bytes()).hexdigest(),
              "cold_moe_load_ms": cold_moe_ms, "cold_graph_load_and_score_ms": cold_graph_ms,
              "graph": graph.get_stats(), "results": results,
              "all_declared_budgets_validated": all(r["status"] == "PASS" for r in results)}
    (ROOT / "datasets/synthetic/BENCHMARK_REPORT.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    return not any(r["status"] == "FAIL" or (require_all and r["status"] == "UNMEASURED") for r in results)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--iterations", type=int, default=500)
    parser.add_argument("--require-all", action="store_true")
    parser.add_argument("--api-url")
    args = parser.parse_args()
    raise SystemExit(0 if run(args.iterations, args.require_all, args.api_url) else 1)
