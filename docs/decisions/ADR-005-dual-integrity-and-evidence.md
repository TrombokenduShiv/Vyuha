# ADR-005: Dual integrity, trained inference and evidence boundaries

Status: Implemented under the user's 14 September 2026 instruction to amend the architecture with the attached blind spot. This supersedes conflicting descriptions in the V1 freeze and earlier ADRs; it preserves institutional authority and the A0–A6 action space.

Vyuha evaluates agency risk and counterparty risk independently. Receiver fraud does not establish coercion. Missing receiver evidence is UNKNOWN, represented by a null score. It cannot trigger isolation by itself. A2 includes merchant-verification and receiver-warning templates; A6 requests institutional review. A4 requires high agency risk and a live communication/capture condition.

The trained agency MoE ignores the former graph feature slot. It uses five learned experts, selecting two per sample. Expert indices are learned components, not independently validated semantic specialists. The Kotlin runtime executes the exported dense layers and selected experts directly. ONNX export evaluates all experts before combining the selected two; this deployment distinction must be disclosed.

The SDK's current belief is the calibrated decision surface around a session observation, with repeated-observation deduplication. It is not a validated longitudinal Bayesian posterior. The old hand-added log-odds accumulated the same evidence repeatedly and conflated receiver fraud with coercion. Learned temporal agency transitions remain a research milestone requiring longitudinal data. Conformal calibration applies to the same agency score in training and serving.

The graph remains the four-node MVP requested in the attachment. Actual multi-head attention, reverse relations, relative time and amount encodings form the heterogeneous temporal GNN. MERCHANT and COMPLAINT nodes are a governed extension, not secretly fabricated training evidence. The graph loader enforces references, fixed feature units, common time cutoffs and disjoint account-label partitions. Static links without observation timestamps are explicitly assumed available at inception in the synthetic dataset.

Graph inference periodically builds an immutable score snapshot. Requests perform a lookup and sign the result; full-graph inference is measured separately. Failed refreshes preserve the old snapshot only until its freshness limit. No deterministic mock score replaces a missing model. Synthetic checkpoints require explicit demo mode.

Risk tokens use RSA/SHA-256 signatures over exact bytes with recipient, session, audience, key ID, schema and expiry binding. Handsets hold pinned public keys. Hashes remain pseudonymous identifiers, not anonymous data. Institutional deployments must replace the demo SHA lookup mapping with governed tokenization and supply an authenticated host gateway.

The offline policy trainer uses the actual agency checkpoint, separate calibration scenarios, counterparty scenarios and an explicit simulation reward model. Runtime safety constraints always override the table. Reward assumptions do not demonstrate human effectiveness. No live exploration is permitted.

The stricter budgets in PERFORMANCE_BUDGET.md are the benchmark source of truth. Desktop Python/JVM, full-graph refresh, in-process API and HTTP measurements are labeled separately. Missing Android evidence remains UNMEASURED and fails `--require-all`. Neither per-node amortized graph throughput nor scripted dashboard numbers count as request latency.

Privacy boundary: never collect call audio, message contents, screen images, notification contents, clipboard history or emotion estimates. Optional host signals need a purpose and lawful processing arrangement. Telemetry ingestion is disabled pending a governed sink. Local diagnostic audit files are opt-in, and do not contain snapshots. Production compliance, legal basis, notices, retention, rights and incident handling belong to a documented deployment review.

The V1 failure-policy statement that missing graph evidence always forces A6 is superseded. Unknown evidence increases payment uncertainty; the response remains proportionate. A new online seller receives verification, while an ordinary established payment can continue with explicit evidence limitations. Uncertainty is not an accusation.
