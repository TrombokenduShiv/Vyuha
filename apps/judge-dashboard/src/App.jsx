import { useEffect, useState } from 'react';
import { Activity, ArrowRight, ArrowUpRight, Check, ChevronDown, CircleHelp, FlaskConical, LockKeyhole, Monitor, Phone, Shield, ShoppingBag } from 'lucide-react';
import evaluation from './data/evaluation.json';
import { actionCopy, formatRisk, reasonCopy, scenarioCopy, riskTone } from './presentation.js';
import './index.css';

function RiskCard({ title, subtitle, value, children, source, icon: Icon }) {
  const tone = riskTone(value);
  return <section className={`risk-card ${tone}`}>
    <div className="card-heading"><span className="icon-tile"><Icon size={19} /></span><span className="score-source">{source}</span></div>
    <h3>{title}</h3><p className="card-description">{subtitle}</p>
    <div className="score-line"><strong className={value == null ? 'unknown-score' : ''}>{formatRisk(value)}</strong><span className={`pill ${tone}`}>{value == null ? 'Missing evidence' : value >= .5 ? 'Elevated risk' : 'Low observed risk'}</span></div>
    <div className={`score-track ${value == null ? 'unknown-track' : ''}`} aria-hidden="true"><span style={{ transform: `scaleX(${value ?? 0})` }} /></div>
    <div className="card-facts">{children}</div>
  </section>;
}

export function ScenarioReview({ item }) {
  const recommendation = actionCopy[item.template_id] || actionCopy.REFLECTION;
  return <>
    <div className="risk-grid">
      <RiskCard title="Influence risk" subtitle="Could someone be pressuring the payer?" value={item.agency_risk} source="Agency model" icon={Activity}>
        <div><span><Phone size={15} />Active call</span><b>{item.context.communication_active ? 'Yes' : 'No'}</b></div>
        <div><span><Monitor size={15} />Screen-capture risk</span><b>{item.context.capture_risk ? 'Detected' : 'Not detected'}</b></div>
      </RiskCard>
      <RiskCard title="Recipient risk" subtitle="What does the recipient’s history indicate?" value={item.counterparty_risk} source="Scenario evidence" icon={Shield}>
        <div><span><ShoppingBag size={15} />Online purchase</span><b>{item.context.online_purchase ? 'User supplied' : 'Not supplied'}</b></div>
        <div><span><CircleHelp size={15} />Recipient history</span><b>{item.counterparty_risk == null ? 'Unavailable' : 'Available'}</b></div>
      </RiskCard>
    </div>
    <section className={`recommendation ${recommendation.tone}`} aria-labelledby="recommendation-title">
      <div className="recommendation-icon">{item.template_id === 'PASS' ? <Check size={23} /> : <Shield size={23} />}</div>
      <div className="recommendation-copy"><span className="section-label">Recommended next step</span><h3 id="recommendation-title">{recommendation.title}</h3><p>{recommendation.message}</p></div>
      <span className={`pill ${recommendation.tone}`}>{recommendation.label}</span>
    </section>
    <div className="context-strip"><LockKeyhole size={15} /><p>Influence and recipient risk are assessed separately. Your bank makes the final payment decision.</p></div>
    <details className="evidence-details">
      <summary><span>Evidence &amp; decision details</span><ChevronDown size={17} /></summary>
      <div className="evidence-body">
        <dl><div><dt>Why this action?</dt><dd>{item.reason_codes.map(reason => reasonCopy[reason] || reason.replaceAll('_', ' ').toLowerCase()).join(' ')}</dd></div><div><dt>Evidence status</dt><dd>{item.uncertain ? 'Incomplete — verify before relying on it.' : 'No uncertainty flagged in this scenario.'}</dd></div><div><dt>Policy reference</dt><dd className="policy-code">{item.action_id}</dd></div></dl>
        <p>These are saved results from synthetic scenarios, not a live bank or device connection. Influence scores come from the trained agency model; recipient scores are supplied scenario evidence. Missing history does not mean low risk.</p>
        <p>Only payment-session features enter the model. Call audio, messages and screen contents are not collected. These results do not establish real-world fraud accuracy.</p>
      </div>
    </details>
  </>;
}

export default function App() {
  const [selected, setSelected] = useState(1);
  const item = evaluation.scenarios[selected];
  const description = scenarioCopy[item.case];
  useEffect(() => {
    if (window.parent !== window) window.parent.postMessage({ type: 'vyuha:dashboard-ready' }, window.location.origin);
  }, []);
  return <div className="app-shell">
    <a className="skip-link" href="#review">Skip to payment review</a>
    <header className="app-header"><a className="wordmark" href="#review" aria-label="Vyuha payment review"><Shield size={29} strokeWidth={2.2} /><span>VYUHA</span></a><span className="header-divider" /><span className="product-name">Payment review</span><span className="demo-badge"><FlaskConical size={14} />Demo data</span></header>
    <div className="workspace">
      <aside className="scenario-sidebar"><div className="sidebar-title"><h2>Scenarios</h2><span>{evaluation.scenarios.length.toString().padStart(2,'0')}</span></div><p className="sidebar-description">Explore a payment situation.</p>
        <select className="mobile-scenarios" aria-label="Choose a payment scenario" value={selected} onChange={event => setSelected(Number(event.target.value))}>{evaluation.scenarios.map((scenario, index) => <option key={scenario.case} value={index}>{String(index + 1).padStart(2, '0')} · {scenarioCopy[scenario.case].title}</option>)}</select>
        <nav className="scenario-list" aria-label="Payment scenarios">{evaluation.scenarios.map((scenario, index) => <button type="button" key={scenario.case} onClick={() => setSelected(index)} aria-pressed={selected === index} className={`scenario-button ${selected === index ? 'selected' : ''}`}><span className="scenario-number">{String(index+1).padStart(2,'0')}</span><span><strong>{scenarioCopy[scenario.case].title}</strong><small>{scenarioCopy[scenario.case].category}</small></span><ArrowUpRight size={15} className="scenario-arrow" /></button>)}</nav>
      </aside>
      <main id="review" className="review" tabIndex={-1}>
        <div className="review-breadcrumb">Payment review<ArrowRight size={13} /><span>Scenario {String(selected+1).padStart(2,'0')}</span></div>
        <div className="review-heading"><div><h1>{description.title}</h1><p>{description.description}</p></div><span className="review-count">{String(selected+1).padStart(2,'0')}<span> / {String(evaluation.scenarios.length).padStart(2,'0')}</span></span></div>
        <div className="sr-only" role="status" aria-live="polite">{description.title}. {actionCopy[item.template_id]?.title}</div>
        <div key={item.case} className="review-content"><ScenarioReview item={item} /></div>
        <footer className="review-footer"><span><span className="quiet-dot" />Scenario replay</span><span>VYUHA · Payment integrity</span></footer>
      </main>
    </div>
  </div>;
}
