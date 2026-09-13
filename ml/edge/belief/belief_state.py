class CoercionBeliefState:
    def __init__(self, initial_belief=0.11):
        self.posterior_coercion_probability = initial_belief
        self.confidence = 0.5
        self.reason_codes = []
        
        # Initial reason
        if initial_belief < 0.2:
            self.reason_codes.append("normal baseline")
            
    def update(self, observation_likelihood, graph_evidence_score, user_response_risk, reason=""):
        """
        Explicit additive/Bayesian-like update logic. Not hidden in neural weights.
        """
        previous_belief = self.posterior_coercion_probability
        
        # Simplified explicit update logic combining multiple independent risks
        # p(coercion | evidence) ~ p(coercion) + evidence_score * (1 - p(coercion))
        
        # Aggregate the new evidence (range 0 to 1)
        combined_evidence = observation_likelihood + graph_evidence_score + user_response_risk
        
        # Additive update pulling towards 1.0 based on evidence strength
        new_belief = previous_belief + (1.0 - previous_belief) * combined_evidence
        
        # Override rules (inspectable)
        if user_response_risk >= 0.5:
            new_belief = 0.94 # Explicit rule for user report
            
        self.posterior_coercion_probability = round(new_belief, 2)
        self.confidence = min(1.0, self.confidence + 0.1) # Confidence grows with observations
        
        if reason:
            self.reason_codes.append(reason)
            
        return self.posterior_coercion_probability
        
    def __str__(self):
        latest_reason = self.reason_codes[-1] if self.reason_codes else "None"
        return f"{self.posterior_coercion_probability:.2f}\n{latest_reason}\n"


if __name__ == "__main__":
    print("Tracking CoercionBeliefState Evolution...\n")
    
    # Init
    state = CoercionBeliefState(initial_belief=0.11)
    print(state)
    
    # 0.31 - unusual beneficiary
    state.update(observation_likelihood=0.2247, graph_evidence_score=0.0, user_response_risk=0.0, reason="unusual beneficiary")
    print(state)
    
    # 0.51 - active communication
    state.update(observation_likelihood=0.2898, graph_evidence_score=0.0, user_response_risk=0.0, reason="active communication")
    print(state)
    
    # 0.68 - remote-access indicator
    state.update(observation_likelihood=0.3469, graph_evidence_score=0.0, user_response_risk=0.0, reason="remote-access indicator")
    print(state)
    
    # 0.87 - beneficiary topology risk
    state.update(observation_likelihood=0.0, graph_evidence_score=0.59375, user_response_risk=0.0, reason="beneficiary topology risk")
    print(state)
    
    # 0.94 - user reports external instruction
    state.update(observation_likelihood=0.0, graph_evidence_score=0.0, user_response_risk=1.0, reason="user reports external instruction")
    print(state)
