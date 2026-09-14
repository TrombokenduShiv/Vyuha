# Judge demonstration review — 14 September 2026

The user's request is for codebase analysis and a detailed setup/presentation guide. No core architecture or model changes were needed. Added a read-only rehearsal helper and a guide linked from README.

Code tracing established that App.jsx imports saved evaluation JSON and does not use the old simulation hook. MainActivity uses PaymentFlowViewModel. Its confirmPayment path never calls graph prefetch; DemoBankApplication still supplies legacy HMAC configuration without v2 public keys/synthetic opt-in. Therefore Android cannot currently be described as the complete live graph demonstration. Its intervention button also does not change the call/capture simulation flags, so the guide uses cancellation/separate scenarios rather than claiming a successful call-ending flow.

The primary demonstration is dashboard replay + live Python/HTTP integration + native JVM evidence. scripts/judge_demo.py recomputes saved scenarios without mutating artifacts, or selects actual low/high HGT predictions and queries the localhost demo service. HTTP mode verifies exact signed evidence, checks model/data agreement and demonstrates tamper/session/request-field rejection. Both modes completed successfully. Live receiver extremes were 0.000856 and 0.999401 on the supplied checkpoint; these are deliberately selected illustrations, not random accuracy samples.

This laptop has Node but no npm on PATH, with existing dashboard dependencies. Direct node invocation of the local Vite binary is the appropriate existing-machine setup. The root Android project lacks a Gradle wrapper. Official AGP 8.2 compatibility specifies Gradle 8.2/JDK 17/Build Tools 34.0.0; the prior Java 21 JVM-harness workaround does not imply Gradle 8.2 compatibility with Java 21. APK steps remain unexecuted.

The family-impersonation limitation is explicitly preserved in the judge Q&A. The guide does not claim voice authentication, live call-duration modeling, trusted-contact messages, or real bank authorization.
