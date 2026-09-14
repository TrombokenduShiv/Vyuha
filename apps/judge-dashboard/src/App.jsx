import React, { useState } from 'react';
import evaluation from './data/evaluation.json';
import './index.css';

const copy = {
  PASS: ['No extra check', 'The current evidence does not require additional friction. The bank still authorizes the payment.'],
  COUNTERPARTY_WARNING: ['Check this recipient', 'This recipient shows elevated network risk. Verify the seller independently before paying. Your bank may require an extra check.'],
  MERCHANT_VERIFICATION: ['New online seller', 'There is not enough receiver history to assess this seller. Have you independently verified them outside the account that sent this payment request?'],
  RECEIVER_CHECK: ['Check the receiver', 'Receiver history is unavailable. Confirm the recipient independently before making a first payment.'],
  ISOLATION_BREAK: ['Take a moment away from the call', 'End the call or screen sharing, then reassess the payment. Vyuha cannot end another app’s call for you.'],
  REFLECTION: ['Review this payment', 'Some evidence is uncertain or unusual. Verify the payment details without relying on the person who requested it.'],
};

export default function App() {
  const [selected, setSelected] = useState(1);
  const item = evaluation.scenarios[selected];
  const [title, message] = copy[item.template_id] || copy.REFLECTION;
  const percent = value => value == null ? 'Unknown' : `${(value * 100).toFixed(1)}%`;
  return <main style={{ maxWidth: 1150, margin: 'auto', padding: '2rem', color: 'var(--text-primary)' }}>
    <header className="header" style={{ marginBottom: '1.5rem' }}>
      <div><h1>Vyuha · Payment integrity</h1><p>Independent decisions. Safer counterparties.</p></div>
      <span className="value-label">Engineering evaluation · 2.1</span>
    </header>
    <p style={{ padding: '1rem', border: '1px solid var(--panel-border)', borderRadius: 12 }}>
      Synthetic evaluation replay. Agency scores come from the trained MoE. Receiver scores are supplied scenario evidence.
      This view is not connected to a bank, a phone, or a live graph service. No performance timings are invented.
    </p>
    <nav aria-label="Payment scenarios" style={{ display: 'flex', flexWrap: 'wrap', gap: 8, margin: '1.5rem 0' }}>
      {evaluation.scenarios.map((scenario, i) => <button key={scenario.case} onClick={() => setSelected(i)}
        aria-pressed={selected === i} style={{ padding: '.7rem 1rem', borderRadius: 8, cursor: 'pointer',
          border: '1px solid var(--panel-border)', background: selected === i ? '#c7d2fe' : '#182135',
          color: selected === i ? '#111827' : '#f8fafc' }}>{scenario.case}</button>)}
    </nav>
    <h2 style={{ marginBottom: 20 }}>{item.case}</h2>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 20 }}>
      <section className="glass-panel"><h2>Agency integrity</h2><p>Could someone be influencing the decision?</p>
        <p style={{ fontSize: '3rem', margin: '1rem 0' }}>{percent(item.agency_risk)}</p>
        <p>Call active: {item.context.communication_active ? 'Yes' : 'No'}</p>
        <p>Capture risk: {item.context.capture_risk ? 'Yes' : 'No'}</p>
        <p>Only payment-session features enter the model.</p></section>
      <section className="glass-panel"><h2>Counterparty integrity</h2><p>What does receiver evidence indicate?</p>
        <p style={{ fontSize: '3rem', margin: '1rem 0' }}>{percent(item.counterparty_risk)}</p>
        <p>{item.counterparty_risk == null ? 'Missing history is not a safe verdict.' : 'Receiver risk remains separate from agency risk.'}</p>
        <p>Online purchase context: {item.context.online_purchase ? 'User supplied' : 'Not supplied'}</p>
        <p>Messages, call audio and screen contents are not collected.</p></section>
      <section className="glass-panel" aria-live="polite"><h2>{title}</h2><p style={{ lineHeight: 1.7 }}>{message}</p>
        <p style={{ marginTop: '1.5rem' }}><strong>{item.action_id.replaceAll('_', ' ')}</strong></p>
        <p>Evidence uncertain: {item.uncertain ? 'Yes' : 'No'}</p>
        <p>Reason: {item.reason_codes.join(', ')}</p></section>
    </div>
    <p style={{ marginTop: '2rem', lineHeight: 1.8 }}>
      The bank owns final payment authorization. An isolation break is reserved for agency risk with an active communication or capture condition.
      A calm buyer can still need receiver verification. These trained models use synthetic data and do not establish real-world fraud accuracy.
    </p>
  </main>;
}
