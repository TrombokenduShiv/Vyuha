import React from 'react';
import { useVyuhaSimulation } from './hooks/useVyuhaSimulation';
import { Shield, Activity, Network, Zap, Target } from 'lucide-react';
import './index.css';

function App() {
  const session = useVyuhaSimulation();

  const getBeliefColor = (val) => {
    if (val < 0.3) return 'var(--accent-green)';
    if (val < 0.7) return 'var(--accent-warning)';
    return 'var(--accent-red)';
  };

  const getActionClass = (action) => {
    if (action.includes('PASS')) return 'safe';
    if (action.includes('PROMPT') || action.includes('DELAY') || action.includes('REFLECTION')) return 'suspicious';
    return 'danger';
  };

  return (
    <div className="dashboard-container">
      <header className="header">
        <h1>Vyuha 2.0 // Judge Dashboard</h1>
        <div style={{ display: 'flex', gap: '2rem', alignItems: 'center' }}>
          <div className="value-block">
            <span className="value-label">Phase:</span>
            <span style={{ marginLeft: '1rem' }} className={`value-data ${session.phase === 'SAFE' ? 'green' : session.phase === 'HIGH_RISK' ? 'red' : 'warning'}`}>
              {session.phase}
            </span>
          </div>
          <div className="value-block">
            <span className="value-label">Latency:</span>
            <span style={{ marginLeft: '1rem', fontFamily: 'JetBrains Mono', fontWeight: 'bold' }}>
              {session.latency}ms
            </span>
          </div>
        </div>
      </header>

      {/* Left Column: Inputs & MoE */}
      <div className="center-column">
        <div className="glass-panel">
          <h2><Activity size={18} style={{marginRight: '8px', verticalAlign: 'middle'}}/> Input Telemetry</h2>
          <div className="value-block">
            <span className="value-label">Txn Amount</span>
            <span className={`value-data ${session.telemetry.amountBucket === 'CRITICAL' ? 'red' : ''}`}>{session.telemetry.amount}</span>
          </div>
          <div className="value-block">
            <span className="value-label">Beneficiary Novelty</span>
            <span className="value-data">{(session.telemetry.novelty * 100).toFixed(0)}%</span>
          </div>
          <div className="value-block">
            <span className="value-label">Active Call</span>
            <span className={`value-data ${session.telemetry.activeCall ? 'red' : 'green'}`}>
              {session.telemetry.activeCall ? 'DETECTED' : 'NONE'}
            </span>
          </div>
          <div className="value-block">
            <span className="value-label">Screen Share</span>
            <span className={`value-data ${session.telemetry.screenShare ? 'red' : 'green'}`}>
              {session.telemetry.screenShare ? 'DETECTED' : 'NONE'}
            </span>
          </div>
        </div>

        <div className="glass-panel" style={{ flex: 1 }}>
          <h2><Zap size={18} style={{marginRight: '8px', verticalAlign: 'middle'}}/> EdgeRiskMoE Router</h2>
          {['PersonalBaseline', 'TxnNovelty', 'CommContext', 'DeviceIntegrity', 'InstitutionalSignal'].map((expert, idx) => {
            const weight = session.moe.routingWeights[idx];
            return (
              <div key={expert} style={{ marginBottom: '0.5rem' }}>
                <div className="value-block" style={{ border: 'none', padding: '0.25rem 0' }}>
                  <span className="value-label">{expert}</span>
                  <span className="value-data">{(weight * 100).toFixed(0)}%</span>
                </div>
                <div className="progress-container">
                  <div 
                    className="progress-bar purple" 
                    style={{ width: `${weight * 100}%` }}
                  />
                </div>
              </div>
            );
          })}
          
          <div style={{ marginTop: 'auto', paddingTop: '1rem', borderTop: '1px solid var(--panel-border)' }}>
            <span className="value-label" style={{display: 'block', marginBottom: '0.5rem'}}>Active Experts (Top-2)</span>
            {session.moe.activeExperts.map((exp, i) => (
              <div key={exp} className="value-block" style={{ padding: '0.25rem 0', border: 'none' }}>
                <span className="value-data" style={{ color: 'var(--accent-purple)' }}>{exp}</span>
                <span className="value-data">{session.moe.expertScores[i]?.toFixed(2)}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Center Column: Graph & Belief */}
      <div className="center-column">
        <div className="glass-panel">
          <h2><Network size={18} style={{marginRight: '8px', verticalAlign: 'middle'}}/> Sub-Graph Topology</h2>
          <div className="node-container">
            {session.graph.neighborhood.map((node, i) => (
              <React.Fragment key={i}>
                <div className={`graph-node ${node.safe ? 'safe' : 'danger'}`}>
                  {node.id}
                </div>
                {i < session.graph.neighborhood.length - 1 && <div className="edge" />}
              </React.Fragment>
            ))}
          </div>
          <div className="value-block">
            <span className="value-label">Distance to known mule:</span>
            <span className={`value-data ${session.graph.riskScore > 0.5 ? 'red' : 'green'}`}>
              {session.graph.distanceToMule}
            </span>
          </div>
          <div className="value-block" style={{ border: 'none' }}>
            <span className="value-label">HGT Graph Risk Score:</span>
            <span className={`value-data ${session.graph.riskScore > 0.5 ? 'red' : 'green'}`}>
              {session.graph.riskScore.toFixed(2)}
            </span>
          </div>
        </div>

        <div className="glass-panel" style={{ flex: 1, position: 'relative' }}>
          <h2><Target size={18} style={{marginRight: '8px', verticalAlign: 'middle'}}/> Coercion Belief State</h2>
          <div className="belief-gauge-container">
            <div className="belief-value" style={{ color: getBeliefColor(session.belief.pCoercion) }}>
              {(session.belief.pCoercion * 100).toFixed(0)}%
            </div>
            <div className="value-label" style={{ marginTop: '1rem' }}>Probability of Coercion</div>
          </div>
          
          <div style={{ position: 'absolute', bottom: '1.5rem', left: '1.5rem', right: '1.5rem' }}>
            <div className="value-block" style={{ border: 'none' }}>
              <span className="value-label">Conformal Confidence:</span>
              <span className="value-data">{(session.belief.confidence * 100).toFixed(0)}%</span>
            </div>
            <div className="progress-container">
              <div 
                className={`progress-bar ${session.belief.confidence > 0.8 ? 'green' : 'warning'}`} 
                style={{ width: `${session.belief.confidence * 100}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Right Column: Reasoning & Output */}
      <div className="center-column">
        <div className="glass-panel">
          <h2><Shield size={18} style={{marginRight: '8px', verticalAlign: 'middle'}}/> Active Reason Codes</h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.5rem' }}>
            {session.belief.reasonCodes.map(code => (
              <div key={code} style={{
                background: 'rgba(255,255,255,0.05)',
                padding: '0.5rem 1rem',
                borderRadius: '4px',
                fontFamily: 'JetBrains Mono',
                fontSize: '0.85rem'
              }}>
                &gt; {code}
              </div>
            ))}
          </div>
        </div>

        <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <h2>Policy Bandit Decision</h2>
          <p className="value-label" style={{ marginTop: '1rem', lineHeight: '1.5' }}>
            The offline-trained policy engine maps the Coercion Belief State and Conformal Uncertainty to the minimum effective friction intervention.
          </p>
          
          <div className={`action-badge ${getActionClass(session.action)}`}>
            [{session.action}]
          </div>
        </div>
      </div>

    </div>
  );
}

export default App;
