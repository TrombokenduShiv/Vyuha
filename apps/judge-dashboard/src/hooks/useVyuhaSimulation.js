import { useState, useEffect } from 'react';

export function useVyuhaSimulation() {
  const [sessionData, setSessionData] = useState({
    phase: "INITIALIZING", // INITIALIZING, SAFE, SUSPICIOUS, HIGH_RISK
    telemetry: {
      amount: "₹1,200",
      amountBucket: "LOW",
      novelty: 0.1,
      activeCall: false,
      screenShare: false
    },
    moe: {
      routingWeights: [0.8, 0.1, 0.05, 0.03, 0.02],
      activeExperts: ["PersonalBaseline", "TransactionNovelty"],
      expertScores: [-3.2, -1.5]
    },
    graph: {
      riskScore: 0.1,
      neighborhood: [
        { id: "A", type: "ACCOUNT", safe: true },
        { id: "B", type: "VPA", safe: true },
      ],
      distanceToMule: "Unreachable"
    },
    belief: {
      pCoercion: 0.11,
      confidence: 0.8,
      reasonCodes: ["NORMAL_BASELINE"]
    },
    action: "A0_PASS",
    latency: 12
  });

  useEffect(() => {
    // Stage 1: Safe baseline
    const timer1 = setTimeout(() => {
      setSessionData(prev => ({
        ...prev,
        phase: "SAFE",
        action: "A0_PASS",
        latency: 14
      }));
    }, 1000);

    // Stage 2: Suspicious Activity
    const timer2 = setTimeout(() => {
      setSessionData(prev => ({
        ...prev,
        phase: "SUSPICIOUS",
        telemetry: {
          ...prev.telemetry,
          amount: "₹85,000",
          amountBucket: "CRITICAL",
          novelty: 0.94
        },
        moe: {
          routingWeights: [0.1, 0.6, 0.1, 0.05, 0.15],
          activeExperts: ["TransactionNovelty", "InstitutionalSignal"],
          expertScores: [1.2, 0.5]
        },
        graph: {
          ...prev.graph,
          riskScore: 0.4
        },
        belief: {
          pCoercion: 0.31,
          confidence: 0.85,
          reasonCodes: ["UNUSUAL_BENEFICIARY"]
        },
        action: "A1_MICRO_PROMPT",
        latency: 28
      }));
    }, 4000);

    // Stage 3: High Risk (Coercion)
    const timer3 = setTimeout(() => {
      setSessionData(prev => ({
        ...prev,
        phase: "HIGH_RISK",
        telemetry: {
          ...prev.telemetry,
          activeCall: true,
          screenShare: true
        },
        moe: {
          routingWeights: [0.05, 0.1, 0.4, 0.4, 0.05],
          activeExperts: ["CommunicationContext", "DeviceIntegrity"],
          expertScores: [3.8, 4.2]
        },
        graph: {
          riskScore: 0.87,
          neighborhood: [
            { id: "A", type: "ACCOUNT", safe: true },
            { id: "VPA", type: "VPA", safe: false },
            { id: "MULE", type: "ACCOUNT", safe: false }
          ],
          distanceToMule: "2 Hops (High Risk)"
        },
        belief: {
          pCoercion: 0.94,
          confidence: 0.98,
          reasonCodes: ["ACTIVE_CALL", "SCREEN_SHARE", "HIGH_GRAPH_RISK"]
        },
        action: "A4_ISOLATION_BREAK",
        latency: 42
      }));
    }, 8000);

    return () => {
      clearTimeout(timer1);
      clearTimeout(timer2);
      clearTimeout(timer3);
    };
  }, []);

  return sessionData;
}
