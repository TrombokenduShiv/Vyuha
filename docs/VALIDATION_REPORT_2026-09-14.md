# Vyuha 2.1 validation record

Date: 14 September 2026. These results describe the supplied synthetic models on this development machine. They are engineering evidence, not certification, real-world fraud-prevention rates or physical Android measurements.

## What was executed

The temporal HGT was trained for 120 epochs with seed 42, learning rate 0.003, two layers, four heads and 65,313 parameters. The agency-only MoE was trained for 50 epochs and contains 954 parameters. The offline policy was fitted using that real MoE checkpoint, separate calibration examples and explicit simulated action-outcome assumptions. The final weights were exported and used by the service and native SDK.

| Verification | Result |
|---|---|
| Python model, graph, policy, signature and API tests | 14 passed |
| Kotlin/JVM SDK tests | 33 passed |
| Python/Kotlin model and policy comparison | 128 input cases included in the Kotlin suite |
| Python/Kotlin RSA interoperability | Signed bytes, null risk, tampering, session binding and expiry verified |
| PyTorch/ONNX export comparison | 291 input cases; maximum absolute logit difference 0.000001431 |
| JSON contract schemas | All 7 valid; actual known/unknown API responses validated against graph-token schema |
| Dashboard production build | Passed with Vite |
| Dashboard browser inspection | Calm risky-recipient and unknown-seller views checked; rendered layout inspected |
| Whitespace/error check | `git diff --check` passed; Windows line-ending notices only |

The Python suite emits upstream Starlette/httpx and AnyIO deprecation warnings. No test failed. The JVM harness excludes the Android-specific memory profiler and does not compile the Android application's Compose screens. An APK build and physical-device testing remain outstanding because a working Android SDK/toolchain is not available in this environment.

## Receiver-model quality

The test label partition contains 4,562 accounts, including 581 labeled risky accounts. All data is synthetic.

| Measure | Final result |
|---|---:|
| HGT PR-AUC | 0.9735 |
| HGT ROC-AUC | 0.9894 |
| Feature-only logistic baseline PR-AUC | 0.3279 |
| HGT without edges PR-AUC | 0.1984 |
| Brier score | 0.0341 |
| Expected calibration error | 0.0587 |
| Validation-selected score threshold | 0.7353 |
| Precision at that threshold | 88.69% |
| Recall at that threshold | 94.49% |
| False-positive rate at that threshold | 1.76% |

The threshold was chosen from validation legitimate scores, then evaluated on the test partition. Its validation target does not guarantee the same false-positive rate on later data. Runtime intervention thresholds are separate policy settings and require institutional validation.

Training, validation, calibration and test labels are disjoint. Transaction snapshots respect their respective time cutoffs. However, connected accounts can span partitions, static links have an explicit inception assumption, and the test partition was inspected during development before final tuning. This is not a locked external test, an unseen-ring benchmark or proof of generalization to real bank data. The baseline/ablation comparison supports the usefulness of graph relationships in this synthetic dataset only.

The independent synthetic agency evaluation used 2,000 cases: PR-AUC 0.9567, ROC-AUC 0.9907, precision 89.63%, recall 89.33%, and false-positive rate 1.82%. Conformal label coverage was 98.7% on this synthetic sample. Coverage does not survive arbitrary distribution changes by assumption.

## Measured latency

Environment: Windows 11, Intel Family 6 Model 186 CPU, Python 3.12.14, CPU PyTorch 2.14.0, two PyTorch threads. Each warm component measurement used 30 warm-up calls and 500 measured calls. The HTTP test used a separate local Uvicorn process at loopback with the supplied trained graph.

| Component | Median | 95th percentile | Declared budget |
|---|---:|---:|---:|
| Python feature packing | 0.006 ms | 0.009 ms | 2 ms |
| Python expert routing | 0.059 ms | 0.104 ms | 5 ms |
| Python MoE including packing | 0.354 ms | 1.153 ms | 20 ms |
| Python conformal lookup | 0.001 ms | 0.002 ms | 2 ms |
| Python policy | 0.002 ms | 0.003 ms | 2 ms |
| Python full local decision | 0.363 ms | 1.093 ms | 50 ms p95; 30 ms median |
| Graph snapshot lookup | 0.005 ms | 0.009 ms | No standalone budget |
| Graph lookup and RSA signing | 0.707 ms | 1.279 ms | 120 ms |
| In-process HTTP test client | 2.696 ms | 3.707 ms | 120 ms |
| Actual local HTTP round trip | 4.284 ms | 9.348 ms | 120 ms |

The 30,000-account graph required 1,395 ms for full inference; cold load plus scoring took 4,096 ms. This is refresh work, separate from request lookup. Cold agency-model loading took 9.15 ms. Single cold observations are not percentile estimates. Different component timings come from separate samples and should not be added together.

The standalone Kotlin/JVM run measured a desktop local-path median of 0.187 ms and p95 of 0.358 ms over 1,000 calls. That is not an Android claim. The benchmark JSON explicitly records all seven Android component budgets as UNMEASURED and `all_declared_budgets_validated: false`. Local HTTP excludes WAN delay, TLS gateways, load balancing and multi-tenant load. Concurrent load, long-duration stability, mobile battery/memory, and final bank authorization latency require separate tests.

## Behavioral checks

The calm social-commerce scenario produced agency risk approximately 0.0024. Supplied receiver evidence of 0.92 led to a counterparty warning and A6 bank step-up recommendation. Removing receiver history led to UNKNOWN and an A2 seller-verification challenge. Active coercion could still trigger an agency intervention even with a low-risk receiver. A normal call to a familiar receiver could pass.

Receiver values in the scenario dashboard are explicitly supplied scenario evidence. The separate API tests use actual HGT snapshot predictions. No scenario demonstration is being presented as a live bank feed.

Missing models, sparse/unknown receivers, stale snapshots, token tampering, cross-session tokens and repeated identical observations were addressed. The guide's case matrix explains both code-level protections and limits requiring real data, host-bank controls or operational processes.

## Reproducible artifacts

Final agency checkpoint SHA-256:
`b73f6ff16f412c13c752aea097c6be996f552adaa5dd18c71998e261189cdaee`

Final graph checkpoint SHA-256:
`62cd25f06cff0789e993637d5585b6588188e297570ebc90b77ba8798295b845`

Detailed machine-readable results are in `datasets/synthetic/MVP_REPORT.json`, `E2E_REPORT.json` and `BENCHMARK_REPORT.json`. Export metadata lives in `exports/*_card.json`. The policy records the agency-model checksum. `ml/requirements-lock.txt` pins tested direct packages; it is explicitly not a fully transitive environment lock. Follow `DEPLOYMENT_RUNBOOK.md` to reproduce training, export, tests and serving.

DPDP readiness requires the institutional controls listed in `VYUHA_PROJECT_GUIDE.md` and `privacy/signal-manifest.json`. This implementation minimizes content collection and keeps agency inference local, but it is not a legal compliance certificate or a production banking release.
