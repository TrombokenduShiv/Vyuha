// Presentation only. Scores, decisions and evidence remain in evaluation.json.
export const formatRisk = value => value == null ? 'Unknown' : `${(value * 100).toFixed(1)}%`;
export const riskTone = value => value == null ? 'unknown' : value >= .5 ? 'elevated' : 'low';
export const actionCopy = {
  PASS: { title: 'No extra check needed', message: 'The available evidence does not call for an additional check. Your bank still authorizes the payment.', label: 'Proceed to bank review', tone: 'low' },
  COUNTERPARTY_WARNING: { title: 'Verify the recipient before paying', message: 'This recipient has elevated network risk. Confirm the seller through an independent source. Your bank may require an extra check.', label: 'Verification needed', tone: 'elevated' },
  MERCHANT_VERIFICATION: { title: 'Get to know this seller first', message: 'There isn’t enough recipient history to assess this seller. Verify them through a trusted source outside the account requesting payment.', label: 'Check recipient', tone: 'unknown' },
  RECEIVER_CHECK: { title: 'Confirm your first payment', message: 'Recipient history is unavailable. Independently confirm who you’re paying before sending money.', label: 'Check recipient', tone: 'unknown' },
  ISOLATION_BREAK: { title: 'Step away from the call', message: 'End the call or screen sharing, then reconsider the payment on your own. Vyuha cannot end another app’s call for you.', label: 'Pause & reassess', tone: 'elevated' },
  REFLECTION: { title: 'Take a moment to review', message: 'Some evidence is incomplete or unusual. Check the payment details independently of the person requesting it.', label: 'Review needed', tone: 'unknown' },
};
export const reasonCopy = {
  LOW_OBSERVED_RISK: 'Low observed risk in the available evidence.',
  COUNTERPARTY_HIGH: 'The recipient has elevated network risk.',
  COUNTERPARTY_UNKNOWN: 'Recipient history is missing.',
  HIGH_AGENCY_RISK: 'Elevated influence risk during an active call or screen-capture condition.',
};
export const scenarioCopy = {
  'Known normal merchant': { title: 'Familiar merchant', category: 'Routine payment', description: 'A familiar merchant and an ordinary payment, with low observed risk.' },
  'Calm social-commerce buyer, risky receiver': { title: 'Online purchase', category: 'Recipient risk', description: 'The buyer shows no signs of pressure, but the recipient has elevated risk.' },
  'New online seller, no graph history': { title: 'New online seller', category: 'Missing history', description: 'A first purchase from an online seller whose recipient history is unavailable.' },
  'Digital arrest with suspicious receiver': { title: 'Digital arrest attempt', category: 'Influence + recipient risk', description: 'A payment under pressure, with an active call and a suspicious recipient.' },
  'Coercion with low-risk receiver': { title: 'Payment under pressure', category: 'Influence risk', description: 'The recipient has low observed risk, but the payer may be acting under pressure.' },
  'Benign call to known contact': { title: 'Familiar contact', category: 'Routine call', description: 'A routine payment during a call, with no elevated risk in the available evidence.' },
  'Offline first receiver': { title: 'First-time recipient', category: 'Offline · missing history', description: 'A first payment without recipient history. Confirm the recipient independently.' },
};
