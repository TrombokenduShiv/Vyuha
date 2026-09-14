# Vyuha 2.1 deployment and verification runbook

## Model lifecycle

Use Python 3.12 in a virtual environment and install `ml/requirements-lock.txt` for the tested dependency versions. CPU PyTorch can be installed from the official PyTorch CPU wheel index. The native-PyTorch graph does not require torch-geometric, torch-scatter or torch-sparse.

`python -m ml.train_all` trains the graph for 120 epochs, agency MoE for 50 epochs, calibrates the models, fits the policy, exports artifacts, evaluates quality and runs benchmarks. `--quick` is a structural smoke run, not a model-quality release. Do not overwrite a reviewed deployment bundle with a quick run. Training is offline and uses synthetic data.

The graph generator repair is `python -m scripts.repair_synthetic_vpa_edges`. It is only for the original synthetic-ID defect. The generator itself has also been fixed. `python -m scripts.update_contracts` reconstructs v2 contracts and repairs the original empty schema-key error. `python -m scripts.export_sdk_test_vectors` generates cross-language model/policy and signature fixtures. Run it after exporting new weights.

Checkpoint writes use temporary files and atomic replacement, including short retries for transient Windows/OneDrive locks. Readers use `weights_only=True`; do not accept arbitrary uploaded checkpoint files. Artifact provenance must still be trusted. Policy artifacts contain the expected MoE hash, preventing accidental cross-version pairing. APK distribution is the current trusted route for edge weights; signed OTA model updates remain future work.

## Institutional API

The default service does not expose a public unauthenticated scoring endpoint. Configure:

| Variable | Meaning |
|---|---|
| `VYUHA_DEMO_MODE=1` | Explicitly permits the supplied synthetic model and an ephemeral in-memory demo signing key; localhost demonstration only |
| `VYUHA_API_KEY` | Institutional service-to-service secret, minimum 32 characters; send as `X-Vyuha-Api-Key` outside demo |
| `VYUHA_AUDIENCE` | Institution/application audience, distinct from the demo value |
| `VYUHA_SIGNING_KEY_FILE` | Trusted RSA private-key PEM file; production should use a managed signing boundary |
| `VYUHA_KEY_ID` | Identifier for selecting the corresponding trusted public key |
| `VYUHA_HGT_CHECKPOINT` | Optional checkpoint path |
| `VYUHA_GRAPH_DATA_DIR` | Optional graph-data directory |
| `VYUHA_TORCH_THREADS` | Positive CPU inference thread count; defaults to 2, matching the measured configuration |

Use `python services/graph-risk-api/main.py --port 8080`. The default bind address is `127.0.0.1`. Put a production instance behind the institution's TLS gateway. The gateway must authenticate the customer session, authorize recipient lookups, rate-limit abuse, bound request bodies and apply tenant isolation. Do not ship the bank's reusable API key inside Android. The handset should use its existing host-bank authenticated gateway, which calls this institutional service.

`POST /v1/graph-risk` accepts exactly `vpa_hash` (64 lowercase hex characters) and `session_id` (UUID). The v1 route is retained for host routing continuity, but the response is schema version 2. This is a wire migration: old HMAC clients must be upgraded. Risk can be null; null always means UNKNOWN, never zero risk.

`GET /health` reports process liveness. `GET /ready` returns 503 when the graph snapshot is unavailable or too old. `GET /v1/graph-risk/stats` is authenticated outside demo. `GET /v1/signing-key` exists only for demo setup; do not trust a key fetched over the same untrusted connection as the token in production. `POST /v1/telemetry` deliberately returns 501. There is no working audit ingestion/OTA-download service being misrepresented as complete.

The HGT snapshot refreshes every 60 seconds and expires after 300 seconds if refresh fails. Tokens expire after 120 seconds. Requests return UNKNOWN when evidence is absent or stale, including for receivers with fewer than three observed activities. The values are configuration choices needing institutional validation, not legal requirements or universal fraud thresholds.

## Android host integration

Initialize `VyuhaConfig` with the host gateway URL, trusted public keys indexed by key ID and the expected audience. Synthetic evidence is rejected unless `allowSyntheticEvidence` is enabled for a demonstration. Public keys should arrive through an authenticated release/provisioning channel. HMAC support exists only for explicit legacy unit tests; the main SDK path uses RSA verification.

Call `Vyuha.beginPayment(TransactionInput(...))` using a positive finite amount and institution-derived `beneficiaryNovelty`. Call `observe` with host-supported signals and `prefetchReceiverRisk` early in the session from a coroutine. Then call `evaluate` and render its title/message. The host should re-evaluate just before authorization using current signals. Payment data changes require a new session; the full session API also exposes recipient-reference invalidation.

Do not tell the SDK a call has ended simply because a user pressed a button. The demo's primary flow now uses the current simulation toggle values. On an actual phone the host must supply observable supported signals. Neither the SDK nor an A4 button can universally terminate another app's call.

A6 is a bank step-up request, not a Vyuha permanent block. A3 needs a host-enforced timer. A5 requires separately approved contact verification; it does not send messages in this build. User-supplied verification can reduce unknown-recipient friction but must not erase strong graph evidence. The host must provide cancel and official-bank assistance routes.

`scripts/test_sdk.ps1 -Download` downloads the pinned Kotlin/JVM dependencies to ignored `.tools`, compiles SDK sources except the Android-specific memory profiler, and runs the JUnit suite. On this machine it uses the available Java 21 runtime because Kotlin 1.9.22 cannot run under the installed Java 26. The application Gradle configuration still targets Java 17 bytecode. This harness does not compile Compose screens, package an APK, emulate the OS, or prove Android latency.

## Verification order

1. `python -m pytest tests -q`: graph math, causal/disjoint partitions, model independence, policy safety, malformed evidence, real graph serving, API requests and token tampering.
2. `python -m ml.graph.evaluation.evaluate`: receiver quality, calibration, selected threshold, baseline and graph ablation.
3. `python -m ml.evaluate_e2e`: agency metrics and scenario replay data.
4. `python -m ml.export.export_onnx`: PyTorch/ONNX export parity and native bundle export.
5. `python -m scripts.export_sdk_test_vectors` then `scripts/test_sdk.ps1`: Kotlin model/policy parity, token-byte interoperability, sessions and desktop latency.
6. `python -m ml.benchmark --iterations 500`: trained-component timing. Add `--api-url http://127.0.0.1:8080` for actual local HTTP requests. The HTTP option targets demo mode; institution credentials must come from a secure test client for production-like testing.
7. `python -m ml.benchmark --require-all`: fail until all declared budgets have evidence. Android budgets remain UNMEASURED without instrumented device results.
8. Build and inspect the dashboard; separately build the Android app and run physical-device instrumentation when its SDK/toolchain is available.

The benchmark intentionally does not label desktop timings as Android timings. Full-graph refresh duration is not divided by account count and presented as request latency. A simulator's artificial sleep or hard-coded number is not performance evidence.

## Release gates still requiring institutional work

Obtain and validate authorized real data, time-stamped relationships and fraud outcome labels. Test on unseen rings and later time periods. Measure threshold behavior, appeal outcomes, accessibility, different languages and device conditions. Define degradation under distribution shift and model failure. Provide tenant isolation, TLS, customer-session authorization, rate limits, security audit storage, key rotation, revocation and rollback. Exercise races at the bank's final authorization boundary.

Agree and document purposes, processing basis, notices, optional-signal choices, rights workflows, retention, breach response, processor agreements and any child-data or significant-fiduciary requirements. Use the official legal sources linked in the project guide. Synthetic training, disabled telemetry and local computation are useful controls; none replaces these deployment obligations.
