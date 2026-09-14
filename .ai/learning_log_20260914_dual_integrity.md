# Dual-integrity implementation findings — 14 September 2026

The user's attachment explicitly authorizes architecture/schema changes; no additional approval was inferred from the local architecture-lock directive.

Existing inference, benchmark and integrated policy files were present despite the request describing them as missing. They contained fallback/mock behavior and evaluation problems. Work corrected those implementations rather than simply adding empty modules.

All original VPA transfer edges referenced `vpa_acc_*` while nodes were named `vpa_*`. The new strict loader exposed this silent graph disconnection. Repair uses actual account ownership mapping. Shared label masks across temporal splits were also replaced with disjoint label partitions; graph-connected entities remain a disclosed evaluation limitation.

Receiver risk must not enter the agency MoE or be added as coercion log-odds. Unknown receiver history is null/UNKNOWN. Identical snapshot repetition must not manufacture independent evidence. The old test expecting higher coercion without graph evidence was invalid under the approved dual-integrity architecture.

HGT now has real head dimensions and per-destination softmax stabilization. Reverse relations allow VPA information to affect account predictions. Time and amount are both used. Untrained confidence heads and fabricated graph explanations were removed.

The system Python was 3.14 without torch. A project-local Python 3.12 virtual environment uses the bundled runtime and authorized downloaded ML dependencies. Windows/OneDrive sometimes held checkpoint/export files open (error 1224); atomic writes avoid replacing a file through an open writer.

The installed Java 26 cannot run this project's Kotlin 1.9.22 compiler. The existing Java 21 runtime works for the standalone JVM harness. Android SDK/Compose APK verification is a separate missing environment capability, not something a JVM test can claim to replace.

GraphRiskToken v2 signs exact JSON bytes with RSA, includes nullable risk, and binds recipient/session/audience. Production key distribution must be trusted independently. Demo SHA hashes are pseudonymous, not anonymous. Telemetry remains disabled until a governed sink is configured.

Final validation: 14 Python tests and 33 Kotlin/JVM tests passed; all seven contract schemas validated. Actual local HTTP p95 was 9.35 ms across 500 requests, while Python local decision p95 was 1.09 ms. The benchmark retains UNMEASURED Android entries. Browser inspection verified separate agency/receiver displays and the UNKNOWN-seller challenge. Full results and evaluation limitations are recorded in docs/VALIDATION_REPORT_2026-09-14.md.
