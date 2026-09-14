# Vyuha: judge demonstration and setup guide

Prepared against the repository on 14 September 2026. Primary environment: Windows PowerShell. This guide distinguishes code inspected, commands executed and Android steps that still need a rehearsal. It complements the [project explanation](VYUHA_PROJECT_GUIDE.md), [validation record](VALIDATION_REPORT_2026-09-14.md) and [deployment runbook](DEPLOYMENT_RUNBOOK.md).

## 1. Choose the demonstration you can substantiate

Use **the dashboard for the human story, the Python/HTTP rehearsal for live model proof, and the test reports for engineering evidence**. These are separate surfaces. Opening the dashboard does not connect it to the graph service.

| Surface | What it actually does | How to present it |
|---|---|---|
| React dashboard | Reads saved evaluation JSON; switches between seven scenarios | “This is our scenario replay, generated using the trained agency model and supplied receiver examples.” |
| Local Python rehearsal | Recomputes those scenarios using the trained MoE and frozen policy | “These are fresh local model decisions; receiver scores in this mode are scenario inputs.” |
| Python rehearsal with HTTP | Selects actual HGT predictions, requests signed API evidence, verifies it and evaluates the trained agency model/policy | “This is a live model-to-service-to-decision demonstration on synthetic graph data.” |
| API Swagger page | Executes graph requests and displays signed receiver tokens | “This is the institutional receiver-evidence interface.” |
| Kotlin/JVM harness | Compiles SDK logic and runs tests, including parity with Python | “This verifies the native SDK calculation; it is not a phone latency test.” |
| Android DemoBank | Simulates payment screens and call/capture inputs using the local SDK | Optional after a successful APK rehearsal; current host flow does not fetch graph evidence |
| Standalone Android warning prototype | Displays a separate warning UI | Design prototype only; not the integrated product |

The recommended primary route needs no Android device, GPU, paid inference API, real bank account, real payment or live phone call. Download dependencies before arriving. Once cached, the primary route runs locally without venue internet. Swagger's default page may still need its CDN assets; the command-line HTTP demonstration does not.

## 2. What the codebase review found

| Area | Entry point and role | Demo relevance |
|---|---|---|
| ML input and policy | `ml/policy/runtime.py`: `PaymentContext`, `PaymentRiskEngine`, constrained actions | Six local input positions; receiver risk kept separate |
| Agency ML | `ml/edge/experts/moe.py`, `ml/edge/training/train_moe.py` | Five experts, top-two routing, trained synthetic agency labels |
| Uncertainty | `ml/edge/conformal/conformal_predictor.py` | Calibrated label sets; ambiguity can remain explicit |
| Graph construction | `ml/graph/data/loader.py`, generation/split modules | Validates nodes/edges; fixed feature units; time cutoffs and disjoint labels |
| Temporal HGT | `ml/graph/model/hgt.py`, `temporal_encoder.py`, training module | Four entity types, reverse relations, time/amount encodings |
| Graph serving | `ml/graph/inference/serve.py` | Full graph refresh followed by fast snapshot lookup; UNKNOWN on insufficient evidence |
| Token protocol | `ml/graph/inference/tokens.py`, `contracts/GraphRiskToken.schema.json` | Exact-byte RSA signatures, recipient/session/audience/expiry binding |
| API | `services/graph-risk-api/main.py` | FastAPI; explicit synthetic demo mode; readiness and authenticated institutional mode |
| Policy training | `ml/policy/training/train_policy.py` | Real MoE plus `OfflinePaymentTwin` in this module; explicit simulated prevention/friction assumptions |
| Export | `ml/export/export_onnx.py`, `exports/`, SDK resources | ONNX/TorchScript/native weights and model metadata |
| Native SDK | `Vyuha.kt`, `VyuhaSession.kt`, `Session.kt`, pipeline/network/security/cache modules | Public API, local computation, token verification, lifecycle protection |
| Main Android app | `DemoBankApplication.kt`, `MainActivity.kt`, `viewmodel/PaymentFlowViewModel.kt` | Home → amount → recipient → confirm → SDK verdict → result |
| Replay dashboard | `apps/judge-dashboard/src/App.jsx` | Imports `src/data/evaluation.json`; no live backend fetch |
| Evidence | `tests/`, SDK JVM tests, `ml/benchmark.py`, graph and E2E evaluators | Correctness, quality, interoperability and measured timing |
| Privacy | `privacy/signal-manifest.json`, project guide | Declared signals, prohibited content and deployment responsibilities |

Older files are not necessarily active runtime paths. In particular, the dashboard's old `useVyuhaSimulation.js` hook is not imported by its current `App.jsx`. The primary Android activity uses `PaymentFlowViewModel`, not the older `PaymentViewModel`/`PaymentScreen` pair. The standalone warning prototype is a separate project. Old belief/simulation implementations and historical V1 documents should not be used to explain the current learned policy. The inspected `docker`, `infra`, `web` and `benchmarks` directories do not supply a working alternative deployment route; do not invent a Docker command for them.

Important Android gaps: `DemoBankApplication` still supplies a legacy shared secret rather than v2 public-key configuration. `confirmPayment()` observes signals and evaluates synchronously without calling `prefetchReceiverRisk()`. The primary app also does not expose the online-purchase context in its flow. Consequently, a working URL alone does not make Android a live graph demo. These are findings, not fixes made by this guide.

## 3. Setup on this existing laptop

Do this once in PowerShell. Every new terminal starts with its own working directory.

```powershell
Set-Location -LiteralPath "C:\Users\Trombokendu's Acer\OneDrive\Desktop\Projects\Vyuha"
.\.venv\Scripts\python.exe --version
node --version
Test-Path .\checkpoints\hgt_best.pt
Test-Path .\checkpoints\moe_best.pt
Test-Path .\checkpoints\frozen_policy.json
Test-Path .\apps\judge-dashboard\node_modules\vite\bin\vite.js
```

Expect Python 3.12 and `True` for each artifact/path check. The existing environment and trained models are sufficient; do not recreate the environment or retrain merely to show the project. Node is available here; `npm` is not on this machine's PATH, so the commands below call the existing local Vite executable through Node.

Open three PowerShell terminals and name them **Graph API**, **Dashboard**, and **Evidence**. Keep the first two running. The commands below run in the foreground, so logs and Ctrl+C remain easy to use.

### Terminal 1: start the graph service

```powershell
Set-Location -LiteralPath "C:\Users\Trombokendu's Acer\OneDrive\Desktop\Projects\Vyuha"
$env:VYUHA_DEMO_MODE = '1'
$env:VYUHA_TORCH_THREADS = '2'
.\.venv\Scripts\python.exe -u services/graph-risk-api/main.py --port 8080
```

Wait for application startup to complete. In Terminal 3, check actual readiness:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/ready
Invoke-RestMethod http://127.0.0.1:8080/v1/graph-risk/stats
```

Expect `status: ready`, `demo: True`, `loaded: True`, `mode: hgt-snapshot`, `num_accounts: 30000`, and `data_kind: synthetic`. `/health` alone is insufficient: it reports that the process is alive even if scoring is unavailable. If the service is already running and ready, reuse it; do not launch a duplicate on the same port.

The supplied checkpoint should report `hgt-v2-62cd25f06cff0789`. A deliberately retrained bundle may have a different version; its reports and SDK resources must be regenerated together. A demo signing key is created in memory on service startup. Restarting the service changes that key. The rehearsal helper fetches the current demo key on each run.

### Terminal 2: start the dashboard

```powershell
Set-Location -LiteralPath "C:\Users\Trombokendu's Acer\OneDrive\Desktop\Projects\Vyuha\apps\judge-dashboard"
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5173 --strictPort
```

Open [the dashboard](http://127.0.0.1:5173/). It initially selects “Calm social-commerce buyer, risky receiver.” The explicit port flag prevents Vite silently selecting another port. This is a local development server for the demo, not a public deployment.

For a production-bundle rehearsal, stop only this dashboard process using Ctrl+C, then run:

```powershell
node node_modules/vite/bin/vite.js build
node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5173 --strictPort
```

A preview build must be rebuilt after changing scenario JSON. A development server picks up the changed import. Do not open `dist/index.html` directly through a file URL.

### Terminal 3: verify local inference and live API integration

```powershell
Set-Location -LiteralPath "C:\Users\Trombokendu's Acer\OneDrive\Desktop\Projects\Vyuha"
.\.venv\Scripts\python.exe -m scripts.judge_demo
.\.venv\Scripts\python.exe -m scripts.judge_demo --api-url http://127.0.0.1:8080
```

The first command makes no graph API calls. It recomputes the seven saved scenarios using the trained agency model and checks that the saved actions/scores still agree. It ends with `PASS: recomputed decisions match the saved scenario report.`

The second command checks service readiness, loads the local HGT to select illustrative low/high recipients, checks model-version agreement, then gets actual evidence over HTTP. It verifies each token before using its receiver score in the decision. It prints four scenarios and the exact JSON request you can copy into Swagger. It finally demonstrates rejection of altered score fields, another session's token and an unexpected message-content request field.

Its final line of proof is `PASS: real HGT -> HTTP -> verified evidence -> real MoE/policy demonstration completed.` The example recipients are deliberately selected extremes, not a random test of fraud accuracy. The helper reads artifacts; it neither retrains nor replaces them. It sends only synthetic references to a local demo server.

## 4. Setup on a new Windows machine

This is a separate path. Do not run environment creation over the working laptop's existing `.venv`.

1. Copy or clone the complete project, including `checkpoints`, `datasets/synthetic`, `exports`, SDK `src/main/resources`, SDK test resources, and dashboard `src/data/evaluation.json`. A source-only copy without model/data files cannot demonstrate real graph scoring. Keep any unrelated credentials out of the demo copy.
2. Install Python 3.12. Use a Node version satisfying the installed Vite requirement `^20.19.0 || >=22.12.0`, with npm available. Internet access is needed for initial package downloads.
3. In PowerShell, enter the copied repository and create a clean environment:

```powershell
Set-Location -LiteralPath 'C:\Demo\Vyuha'
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install torch==2.14.0 --index-url https://download.pytorch.org/whl/cpu
.\.venv\Scripts\python.exe -m pip install -r ml/requirements-lock.txt
.\.venv\Scripts\python.exe -c "import torch, numpy, pandas, fastapi, cryptography; print(torch.__version__)"
```

If `py` is absent, invoke the installed Python 3.12 executable by its full path. Do not silently use the system Python 3.14. The lock file pins tested direct dependencies; it is not a complete transitive environment lock. Keep installation logs and verify the environment on the target laptop. CPU execution is sufficient.

4. Install dashboard dependencies and check its build:

```powershell
Set-Location -LiteralPath 'C:\Demo\Vyuha\apps\judge-dashboard'
npm ci
npm run build
npm run lint
```

5. Follow the three-terminal startup instructions with the new root path. Use `npm run dev -- --host 127.0.0.1 --port 5173 --strictPort` or the direct Node/Vite command.
6. Run the rehearsal and tests before copying the machine into the final presentation setup. Do not copy a `.venv` from another path and assume it is portable. After package installation, disconnect venue internet for one full rehearsal to confirm your cached local route.

## 5. Prepare evidence the day before

From the repository root, execute the correctness suite:

```powershell
.\.venv\Scripts\python.exe -m pytest tests -q
.\scripts\test_sdk.ps1
```

The recorded baseline is **14 Python tests and 33 Kotlin/JVM tests passing**. On a new machine, the SDK command needs a compatible Java runtime and downloaded jars; use `scripts/test_sdk.ps1 -Download` once while online. The existing harness selects this laptop's Java 21 runtime when present, otherwise `java` on PATH. The installed Java 26 failed to run Kotlin 1.9.22 previously. A JDK 17 on PATH is suitable for the JVM-target-17 harness and avoids relying on this laptop-specific path.

Generate fresh timings only when needed, with the API running:

```powershell
.\.venv\Scripts\python.exe -m ml.benchmark --iterations 500 --api-url http://127.0.0.1:8080
```

This overwrites `datasets/synthetic/BENCHMARK_REPORT.json` with the new machine's measurements. Do not quote the previous laptop's numbers as a measurement of a new laptop. `--require-all` intentionally exits unsuccessfully while Android budgets remain UNMEASURED; it is not the “make everything green” presentation command.

The recorded September 14 reference run measured Python local p95 1.09 ms and local HTTP p95 9.35 ms. Full-graph inference was about 1.40 seconds for 30,000 accounts and occurred during refresh, not on every request. Loopback timing excludes real mobile networks and bank gateways. Display the environment and report date beside the numbers.

Keep the project guide, validation report and three JSON reports open in editor tabs. Optional fresh quality/export checks, outside the presentation slot, are:

```powershell
.\.venv\Scripts\python.exe -m ml.graph.evaluation.evaluate
.\.venv\Scripts\python.exe -m ml.evaluate_e2e
.\.venv\Scripts\python.exe -m ml.export.export_onnx
.\.venv\Scripts\python.exe -m scripts.export_sdk_test_vectors
.\scripts\test_sdk.ps1
```

These commands update reports or exports. Keep generated artifacts together and rebuild the dashboard if serving a production preview. Repeatedly regenerating artifacts during judging adds no useful proof.

Full retraining is optional engineering work, not setup for an already trained demo: `python -m ml.train_all`. It overwrites the active model bundle. `--quick` also overwrites artifacts with reduced-training results. Rehearse full retraining in a separate project copy, inspect all stage outcomes, then export fixtures and rerun verification. Never start training just before judges arrive.

## 6. The eight-minute judge walkthrough

Assign roles by responsibility: one narrator controls the story, one operator controls the dashboard and terminal, one teammate answers ML/security questions. Agree transitions in advance. A solo presenter can follow the same sequence. Do not have teammates speak over each other.

### 0:00–0:40 — explain the problem

Say: “A bank can authenticate the genuine customer and still authorize a scam payment. Sometimes someone is controlling the decision. Sometimes the customer is calm, but the seller is dishonest. Vyuha examines these two risks separately before the host bank authorizes payment.”

Identify the three cards: agency risk, receiver risk and the suggested action. State once that the dashboard is a synthetic replay and the next terminal segment will execute actual model inference.

### 0:40–1:15 — establish normal behavior

Click **Known normal merchant**. Expect agency approximately **0.5%**, receiver **5.0%**, and **A0 PASS**. Explain that these scores are model/scenario values, not a guarantee of safety. The point is avoiding unnecessary friction when observed risk is low.

### 1:15–2:15 — demonstrate the important blind spot

Click **Calm social-commerce buyer, risky receiver**. Tell the shoes-from-an-online-seller story. Point out **Call active: No**, **Capture risk: No**, agency approximately **0.2%**, receiver **92.0%**, **Check this recipient**, and **A6 STEP UP REQUIRED**.

Say: “The customer can be acting independently and still be paying a risky receiver. We request a recipient check. We do not ask them to end a nonexistent call. A6 asks the bank for an additional check; Vyuha itself has not frozen a real account.”

### 2:15–3:00 — handle uncertainty fairly

Click **New online seller, no graph history**. Expect **Unknown**, **A2 REFLECTION CHALLENGE** and the merchant-verification wording. Explain that absence of history is neither proof of safety nor an accusation of fraud. This is also how the design avoids confidently labeling every new legitimate seller as malicious.

### 3:00–3:45 — show the other independent risk

Click **Coercion with low-risk receiver**. Expect agency approximately **99.7%**, receiver **5.0%**, and **A4 ISOLATION BREAK**. Explain that a low-risk receiver cannot erase evidence of external influence. The active call/capture inputs here are synthetic scenario features, not a secretly recorded conversation.

Optionally click **Benign call to known contact**: agency approximately **0.7%**, receiver **5.0%**, **A0 PASS**. This contrast illustrates why “a call exists” is not itself a fraud verdict.

### 3:45–5:15 — prove the model and service are real

Switch to Terminal 3 and run the HTTP rehearsal command. Narrate its four actual requests as the output appears. The rehearsed HGT extremes were **0.000856** and **0.999401**; these differ from the dashboard's supplied 0.05/0.92 examples, as expected. A different reviewed checkpoint may produce different values.

Say: “The server previously scored its graph using trained HGT weights. This request retrieves the account's current score, signs it and binds it to this recipient and session. Our client verifies those bytes, then feeds the receiver evidence alongside a freshly computed local agency score to the constrained policy.”

Point out the three PASS lines for tampering, session mismatch and extra request fields. Explain that cryptographic authenticity does not prove the model's prediction is correct; it proves which evidence was issued and what it was bound to.

### 5:15–6:00 — demonstrate degraded operation

Click **Offline first receiver** in the dashboard: **Unknown**, approximately **0.3%** agency, **A1 MICRO PROMPT**. Say clearly that this is a scenario replay. For a live local calculation without network dependence, run `python -m scripts.judge_demo` and point to its “no graph API requests” line. You need not stop the graph server to prove that this command makes no requests.

Do not claim that offline mode still knows a receiver's fresh bank history. The useful property is that local agency inference continues and missing receiver evidence remains explicit.

### 6:00–7:00 — explain ML and evidence simply

Show the architecture flow in the project guide. Say: “A small model on the user's device examines limited payment features. A temporal graph model inside the institution examines relationships and money movement. A calibrated uncertainty layer and constrained policy select a proportionate response.”

Open the validation record. Mention the tests, model parity and measured latency. If discussing graph PR-AUC **0.9735**, immediately identify it as synthetic-data performance with connected-partition and development-tuning limitations. It does not mean 97.35% of real fraud is prevented. Do not run a long training job or recite every metric.

### 7:00–8:00 — privacy, limits and close

Say: “We do not need message contents, call audio or screenshots. Agency inference stays local. The receiver interface accepts only a recipient reference and session identifier. We still require an institution's governed data, secure deployment and privacy procedures before real use.”

Explain that a hash is not anonymity and that DPDP readiness is not a compliance certificate. Then acknowledge the sudden-family-impersonation case: the system may flag the recipient/payment, but it cannot currently establish who is speaking. The dedicated family verification flow remains future work.

Close with the deliverable: “We have a trained local model, a trained graph service, an interoperable SDK and a constrained intervention system. The next validation step is a governed institutional pilot with real outcomes and device testing.”

## 7. Exact dashboard expectations

| Button text | Agency display | Receiver display | Action |
|---|---:|---:|---|
| Known normal merchant | 0.5% | 5.0% | A0 PASS |
| Calm social-commerce buyer, risky receiver | 0.2% | 92.0% | A6 STEP UP REQUIRED |
| New online seller, no graph history | 0.2% | Unknown | A2 REFLECTION CHALLENGE |
| Digital arrest with suspicious receiver | 99.7% | 92.0% | A4 ISOLATION BREAK |
| Coercion with low-risk receiver | 99.7% | 5.0% | A4 ISOLATION BREAK |
| Benign call to known contact | 0.7% | 5.0% | A0 PASS |
| Offline first receiver | 0.3% | Unknown | A1 MICRO PROMPT |

These are the current saved report, rounded for display. Rehearsal recomputes and checks the underlying agency/action values. They are not promises about arbitrary scenarios the judge invents.

## 8. Optional Swagger walkthrough

Open [API documentation](http://127.0.0.1:8080/docs). Expand `POST /v1/graph-risk`, click **Try it out**, paste a request printed by the live helper, then click **Execute**. The endpoint only returns risk evidence; it does not transfer money.

Inspect `risk_score`, `risk_class`, `reason_codes`, `model_version`, `data_kind`, `session_id`, `expires_at`, `signed_payload` and `signature`. Repeat with the helper's unknown-recipient request and show `risk_score: null`. Sending a raw string such as `someone@upi` in `vpa_hash` or adding `messages` must return HTTP 422.

Do not type a real judge's VPA: the graph contains synthetic node IDs such as `vpa_0`, not real-world beneficiary records. Hashing a random real VPA would ordinarily produce UNKNOWN and would prove nothing about that person's safety.

Swagger depends on browser assets that may fail to load without internet. The tested helper and PowerShell requests are the fallback; a blank Swagger page does not imply a dead model service.

## 9. Optional Android preparation and walkthrough

**Current status: source inspected; APK build, installation and phone execution have not been validated here.** Make Android part of the presentation only after a complete device rehearsal. The current host flow is local-SDK-only for the reasons in section 2.

The root build uses AGP 8.2.2, Kotlin 1.9.22, Compose compiler 1.5.8, compile/target SDK 34 and minimum SDK 26. For this AGP generation use JDK 17, Gradle 8.2 and SDK Build Tools 34.0.0, consistent with the [official Android compatibility table](https://developer.android.com/build/releases/agp-8-2-0-release-notes). This project's Kotlin bytecode target is 17. The desktop test harness's Java 21 workaround is not a recommendation to run Gradle 8.2 under Java 21.

1. Install Android Studio and the Android SDK platform 34, Build Tools 34.0.0, Platform Tools, and an emulator image if needed.
2. Open the **root Vyuha project**, not `apps/android-warning-prototype`. Configure the Gradle JDK to JDK 17.
3. The inspected root has no Gradle wrapper. Install a local Gradle 8.2 distribution and configure the IDE to use it, or generate the wrapper with that installed Gradle: `gradle wrapper --gradle-version 8.2`. Confirm `gradle --version` before using this command; do not assume an arbitrary installed Gradle is compatible.
4. Let Android Studio create `local.properties` with the SDK location. Do not copy another machine's SDK path.
5. From the root, build using the installed Gradle: `gradle :apps:android-demo:assembleDebug`. If a wrapper was generated, use `.\gradlew.bat :apps:android-demo:assembleDebug` instead. Treat any failure as unresolved; this guide does not claim the command already passed.
6. Start an API 26+ emulator or connect a development phone. Confirm `adb devices`, then install `apps/android-demo/build/outputs/apk/debug/android-demo-debug.apk` with `adb install -r` after the build succeeds.
7. Launch **DemoBank**. Tap **Send Money**, enter a positive amount, select a familiar demo contact and leave simulation toggles off. Confirm payment and record the actual verdict during rehearsal. A0 or proportionate friction depends on current policy and absent graph evidence; do not promise the dashboard's supplied low-receiver score here.
8. Repeat with a new synthetic payee, a large amount and both **On a phone call** and screen-share simulation toggles enabled. Demonstrate the actual intervention and **Cancel Transaction**. No real payment occurs.
9. Avoid a staged “I have ended the call” success: the intervention screen does not provide controls to change those simulation flags, and pressing its button does not clear them. Repeated presses should not be narrated as proof that a call ended. Cancel and begin a separate scenario with changed toggles when appropriate.

The emulator URL `10.0.2.2:8080` is present in app configuration, but networking alone will not close the missing prefetch/key integration. Live phone-to-graph integration needs v2 trusted public keys, explicit synthetic opt-in for a demo, asynchronous prefetch and a tested network route. Real deployment also needs a bank gateway, not a reusable service API key inside the APK. The separate prototype's “Send verification” label is not evidence of an implemented trusted-contact service.

## 10. Judge questions and accurate answers

| Question | Answer |
|---|---|
| “Is the dashboard running the model when I click?” | No. It replays recorded results. The command-line rehearsal recomputes them; HTTP mode uses actual server evidence. |
| “Where is the AI?” | The trained expert model, temporal HGT, calibration and model-informed offline policy. No paid LLM call is needed at payment time. |
| “Why not just block calls?” | Calls can be legitimate; a calm person can still pay a scammer. Separate dimensions support more relevant responses. |
| “Can you detect a caller pretending to be my relative?” | Not their identity. Payment/receiver evidence may trigger checks. Dedicated independent family-request verification is not implemented. |
| “Does it learn while customers pay?” | The demonstrated policy is frozen offline. It does not experiment with weaker interventions on live users. |
| “Is a high graph score proof of fraud?” | No. It is model evidence with limitations, not a legal finding. |
| “Can it stop a payment?” | It recommends an intervention. The host institution must enforce authorization, step-up and timers. |
| “What is ST-GNN here?” | A heterogeneous relationship model with transaction time and amount encoding. It is not a geographic map or a live audio sequence model. |
| “Does the fast API recompute 30,000 accounts?” | No. It retrieves from a refreshed model snapshot. Full inference duration is reported separately. |
| “Is this DPDP compliant?” | Technical minimization is implemented; deployment compliance needs institutional processing, notice, security, rights and retention controls. |
| “What remains?” | Governed real data, external validation, Android integration/device measurements, operational security, and trusted verification flows. |

## 11. Failure recovery

| Symptom | Recovery |
|---|---|
| Python import fails or torch is missing | Use the repository `.venv\Scripts\python.exe`; check Python 3.12 and dependency installation |
| `npm` not recognized on this laptop | Use the direct `node node_modules/vite/bin/vite.js` commands with existing node_modules |
| Port already in use | Check whether the existing local app is the intended one and ready; reuse it or Ctrl+C its own terminal; do not kill unrelated processes |
| `/ready` returns 503 | Inspect API startup logs, checkpoint/data paths and explicit demo mode; an alive process may still lack a usable snapshot |
| API returns only UNKNOWN | Use a helper-generated synthetic recipient hash; check model/data freshness and sparse-history status |
| Demo service fails at startup | Confirm `VYUHA_DEMO_MODE=1` in that terminal; a default institutional configuration intentionally requires credentials and rejects synthetic weights |
| Helper reports model/data disagreement | Restore a consistent checkpoint/data bundle and restart the demo service; do not edit scores to make it pass |
| Dashboard appears stale | Rebuild production preview or refresh the development view; check imported evaluation JSON matches the report |
| Token fails after server restart | Fetch the new demo key through a new helper run; do not disable signature verification |
| Java reports an unsupported version | Use the rehearsed Java runtime; Android Gradle and the standalone JVM harness have different environment considerations |
| Android build or screen flow fails | Use the verified laptop route; describe Android as an unverified host prototype |
| Venue internet fails | Use cached dependencies, local dashboard and CLI HTTP requests; no retraining or cloud service is needed |
| API cannot be restored during the slot | Run the local-only helper and clearly identify receiver values as scenario inputs; show prior measured API evidence as prior evidence |

## 12. Final checklist and shutdown

- Rehearse the entire sequence on the actual laptop/projector; verify text size and scroll position.
- Run both helper modes successfully. Confirm dashboard/report consistency and a ready 30,000-account graph.
- Cache dependencies and save test output beforehand. Keep the validated bundle unchanged during judging.
- Open the dashboard, project guide and validation record. Keep Swagger optional.
- Use the seven exact button names in this guide. Do not promise unimplemented caller identity checks, message access, real payment blocking or contact verification.
- If only three minutes are available: normal payment → calm risky seller → unknown seller → live HTTP helper → one-sentence privacy/limits statement.
- At the end, stop the Graph API and Dashboard processes with Ctrl+C in their own terminals. Do not terminate unrelated Node/Python processes.

Guide verification: both `scripts.judge_demo` modes were executed successfully against the existing trained bundle and localhost service. The seven replay scenarios matched freshly calculated agency/action outputs. The live path returned real low/high/unknown graph evidence and passed tamper/session/request-field checks. Android preparation is documented, not reported as executed.
