> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

# Vyuha 2.0: Agency Integrity Layer

**"Authentication protects identity. Vyuha protects intent."**

Vyuha is an **Edge-AI behavioral defense SDK** designed to detect when a legitimate, authenticated user is authorizing a transaction under live psychological coercion, social engineering, or remote influence (e.g., "digital arrest" scams). 

Instead of relying solely on post-transaction fraud detection, Vyuha operates as a lightweight OODA loop on the edge, interpreting the user's real-time behavioral state (coercion belief) combined with institutional graph intelligence (mule-ring detection) to introduce proportionate friction *before* the transaction is authorized.

## 🏗️ Architecture Overview

Vyuha uses a strict hybrid architecture, ensuring privacy, offline safety, and institutional data security.

1. **Edge Agency Runtime (On-Device SDK):**
   - **Triage Gate & Sparse Risk-MoE:** Evaluates local telemetry (communication state, device capture risk, baseline deviation, transaction novelty).
   - **Coercion Belief State:** Maintains a sequential $P(Coercion)$ log-odds score.
   - **Contextual Policy Bandit:** A frozen, offline-trained policy that maps the belief state and uncertainty to one of 7 intervention actions (A0-A6).
   
2. **Institutional Graph Intelligence (Server-Side):**
   - **Heterogeneous ST-GNN:** Evaluates beneficiary risk across a temporal graph of Accounts, VPAs, Devices, and Phones.
   - **Signed Risk Token:** Issues a privacy-safe `GraphRiskToken` to the device. The raw graph is *never* exposed to the handset.

> For deep architectural details, decisions, and system diagrams, please refer to [docs/ARCHITECTURE_FREEZE_V1.md](docs/ARCHITECTURE_FREEZE_V1.md).

## 🚦 Intervention Space (A0 - A6)

Vyuha applies the minimum effective friction needed to break the scammer's synchronous control loop:
- **A0 (PASS):** No friction (95%+ of normal transactions).
- **A1 (MICRO_PROMPT):** A quick, low-cost context question.
- **A2 (REFLECTION_CHALLENGE):** Re-anchors attention to the recipient.
- **A3 (COOLING_DELAY):** Intentional 15-30s pause.
- **A4 (ISOLATION_BREAK):** Requires active communication/screen-sharing to end.
- **A5 (TRUSTED_VERIFY):** Invokes the Safety Circle (minimal disclosure).
- **A6 (STEP_UP_REQUIRED):** Final denial delegated to the host bank.

## 🚀 Installation Instructions

*Note: As this repository is currently under active hackathon development, the following instructions reflect the intended build process for the prototype.*

### Prerequisites
- **Android:** Android Studio (Koala or later), JDK 17, Android SDK 34+
- **Backend:** Python 3.10+, PyTorch, PyTorch Geometric
- **Node:** (Optional) for the Judge Dashboard web interface.

### 1. Start the Institutional Graph Service (Simulator)
The graph backend simulates the bank's ST-GNN and issues signed risk tokens.
```bash
# Navigate to the graph API service
cd services/graph-risk-api

# Install dependencies
pip install -r requirements.txt

# Start the mock issuer service
python main.py --port 8080
```

### 2. Build the Android SDK and Host App
The Android project contains both the `vyuha-core.aar` SDK and the `VyuhaBank` host simulator.
```bash
# Navigate to the Android project root
cd apps/android-demo

# Build the SDK and the App
./gradlew assembleDebug
```
Deploy the resulting APK to a physical Android device or emulator running API 26+.

## 🎮 Usage Instructions (Demo)

1. **Launch VyuhaBank:** Open the host bank application on your device.
2. **Benign Flow:** Initiate a payment to a known contact under normal conditions. The transaction will clear instantly (A0_PASS).
3. **Coercion Flow:** 
   - Start an active phone call (or simulate one in the debug menu).
   - Attempt a high-value transfer to a novel VPA.
   - The graph service will flag the VPA, and the Edge SDK will calculate a high Coercion Belief.
   - Observe the **Isolation Break (A4)** or **Safety Circle (A5)** intervention taking over the screen.
4. **Offline Mode:** Disconnect the device from the internet. Try the flows again. Vyuha will fail-safe, gracefully substituting the graph signal with `UNKNOWN` and maintaining local behavioral protection.

---
**Team Syndicate:** Trombokendu, Aditya, Alaukik, Sneha
*(Built for ByteBuilt 1.0)*
