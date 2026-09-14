"""
Vyuha 2.0 -- Master ML Pipeline Orchestrator
===============================================
Single-command entry point to run the entire ML pipeline:

    python -m ml.train_all

Stages:
    1. Generate synthetic graph data (if not exists)
    2. Train HGT model on graph data
    3. Train Edge MoE model on synthetic behavioral data
    4. Train frozen intervention policy
    5. Calibrate conformal predictor
    6. Export models for deployment
    7. Run evaluation harness
    8. Run end-to-end evaluation
    9. Run benchmarks
    10. Generate final report

Architecture ref: ARCHITECTURE_FREEZE_V1 (all sections).
"""
import os
import sys
import json
import time
import argparse
import subprocess

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ML_ROOT = os.path.dirname(__file__)
sys.path.insert(0, PROJECT_ROOT)


def run_stage(name, module, args=None):
    """Run a Python module as a subprocess and report status."""
    print(f"\n{'='*60}")
    print(f"STAGE: {name}")
    print(f"{'='*60}\n")

    cmd = [sys.executable, "-m", module]
    if args:
        cmd.extend(args)

    t0 = time.time()
    result = subprocess.run(cmd, cwd=PROJECT_ROOT, capture_output=False)
    elapsed = time.time() - t0

    status = "PASS" if result.returncode == 0 else "FAIL"
    print(f"\n[{status}] {name} completed in {elapsed:.1f}s")

    return {"stage": name, "status": status, "elapsed_s": round(elapsed, 1)}


def main(quick=False):
    """Run the entire ML pipeline."""
    print("=" * 60)
    print("Vyuha 2.0 -- MASTER ML PIPELINE ORCHESTRATOR")
    print("=" * 60)
    print(f"\nMode: {'QUICK (reduced epochs)' if quick else 'FULL'}")
    print(f"Project root: {PROJECT_ROOT}\n")

    t_total = time.time()
    results = []

    # Stage 1: Check if synthetic data exists
    data_dir = os.path.join(PROJECT_ROOT, "datasets", "synthetic")
    if os.path.exists(os.path.join(data_dir, "nodes_account.csv")):
        print("[OK] Synthetic graph data already exists")
    else:
        r = run_stage("Generate Synthetic Graph Data",
                      "ml.graph.generation.simulator")
        results.append(r)
        if r["status"] == "FAIL":
            print("\n[ABORT] Cannot proceed without graph data")
            return

    # Stage 2: Split data (if not done)
    if not os.path.exists(os.path.join(data_dir, "edges_account_sends_to_account_train.csv")):
        r = run_stage("Temporal Split", "ml.graph.generation.split_and_report")
        results.append(r)

    # Stage 3: Train HGT
    hgt_args = ["--epochs", "5" if quick else "30", "--quick-test"] if quick else ["--epochs", "30"]
    r = run_stage("Train HGT Graph Model", "ml.graph.training.train_hgt", hgt_args)
    results.append(r)

    # Stage 4: Train MoE
    moe_args = ["--epochs", "20" if quick else "50"]
    r = run_stage("Train Edge MoE", "ml.edge.training.train_moe", moe_args)
    results.append(r)

    # Stage 5: Train Policy
    policy_args = ["--n-scenarios", "1000" if quick else "5000"]
    r = run_stage("Train Frozen Policy", "ml.policy.training.train_policy", policy_args)
    results.append(r)

    # Stage 6: Export
    r = run_stage("Export Models", "ml.export.export_onnx")
    results.append(r)

    # Stage 7: Evaluate HGT
    r = run_stage("Evaluate HGT", "ml.graph.evaluation.evaluate")
    results.append(r)

    # Stage 8: E2E Evaluation
    e2e_args = ["--n-samples", "500" if quick else "2000"]
    r = run_stage("End-to-End Evaluation", "ml.evaluate_e2e", e2e_args)
    results.append(r)

    # Stage 9: Benchmarks
    r = run_stage("ML Benchmarks", "ml.benchmark")
    results.append(r)

    # Summary
    total_time = time.time() - t_total
    passed = sum(1 for r in results if r["status"] == "PASS")
    failed = sum(1 for r in results if r["status"] == "FAIL")

    print(f"\n{'='*60}")
    print(f"PIPELINE COMPLETE")
    print(f"{'='*60}")
    print(f"\n  Total time: {total_time:.1f}s ({total_time/60:.1f} min)")
    print(f"  Stages: {passed} passed, {failed} failed, {len(results)} total\n")

    for r in results:
        icon = "[OK]" if r["status"] == "PASS" else "[FAIL]"
        print(f"  {icon} {r['stage']:<40s} {r['elapsed_s']:>6.1f}s")

    print(f"\n{'='*60}")

    # Save pipeline report
    report_path = os.path.join(data_dir, "PIPELINE_REPORT.json")
    with open(report_path, "w") as f:
        json.dump({
            "total_time_s": round(total_time, 1),
            "mode": "quick" if quick else "full",
            "stages": results,
            "passed": passed,
            "failed": failed,
        }, f, indent=4)
    print(f"Pipeline report: {report_path}")

    return failed == 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run complete ML pipeline")
    parser.add_argument("--quick", action="store_true", help="Quick mode (reduced epochs)")
    args = parser.parse_args()

    success = main(quick=args.quick)
    sys.exit(0 if success else 1)
