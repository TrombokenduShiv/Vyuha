import torch
import torch.nn as nn
import torch.nn.functional as F
import time

class Expert(nn.Module):
    def __init__(self, input_dim=6, hidden_dim=16):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, 1) # Output a risk logit
        )
    def forward(self, x):
        return self.net(x)

class EdgeRiskMoE(nn.Module):
    def __init__(self, input_dim=6, hidden_dim=16, num_experts=5, top_k=2):
        super().__init__()
        self.input_dim = input_dim
        self.num_experts = num_experts
        self.top_k = top_k
        if not 1 <= top_k <= num_experts:
            raise ValueError("Invalid expert routing configuration")
        
        # Gate network
        self.gate = nn.Sequential(
            nn.Linear(input_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, num_experts)
        )
        
        # Experts
        self.experts = nn.ModuleList([Expert(input_dim, hidden_dim) for _ in range(num_experts)])
        
        # Latent representation projector
        self.latent_proj = nn.Linear(input_dim, hidden_dim)
        
    def forward(self, x):
        t0 = time.perf_counter()
        batch_size = x.size(0)
        
        # Deterministic Fast-Safe Path
        # Assume inputs are normalized [0, 1]. If sum is very low, it's safely benign.
        # We process this batched, but for edge deployment (batch=1), it bypasses everything.
        # Zero/missing features are not proof of safety. Always infer.
        fast_safe_mask = torch.zeros(batch_size, dtype=torch.bool, device=x.device)
        # Slot 4 is reserved: receiver evidence MUST NOT alter agency risk.
        x = x * x.new_tensor([1, 1, 1, 1, 0, 1])
        
        # Gate routing
        gate_logits = self.gate(x)
        routing_weights = F.softmax(gate_logits, dim=-1)
        
        # Top-K routing
        top_k_weights, top_k_indices = torch.topk(routing_weights, self.top_k, dim=-1)
        # Re-normalize top-k weights
        top_k_weights = top_k_weights / top_k_weights.sum(dim=-1, keepdim=True)
        
        expert_outputs = torch.zeros(batch_size, self.num_experts, device=x.device)
        for i, expert in enumerate(self.experts):
            # Evaluate expert only where it is in the top-k
            mask = (top_k_indices == i).any(dim=-1)
            if mask.any():
                expert_outputs[mask, i] = expert(x[mask]).squeeze(-1)
                
        # Combine expert outputs with top-k weights
        # expert_outputs: [batch, num_experts]
        risk_logits = torch.zeros(batch_size, device=x.device)
        for k in range(self.top_k):
            idx = top_k_indices[:, k]
            weight = top_k_weights[:, k]
            # Gather the expert output for this index
            exp_out = expert_outputs.gather(1, idx.unsqueeze(1)).squeeze(1)
            risk_logits += weight * exp_out
            
        # Fast safe override
        risk_logits = torch.where(fast_safe_mask, torch.tensor(-10.0, device=x.device), risk_logits)
            
        latent_representation = self.latent_proj(x)
        
        # Confidence proxy (entropy of gate)
        entropy = -(routing_weights * torch.log(routing_weights + 1e-9)).sum(dim=-1)
        confidence = 1.0 - (entropy / x.new_tensor(float(self.num_experts)).log())
        
        t1 = time.perf_counter()
        inference_timing_ms = (t1 - t0) * 1000.0
        
        return {
            "expert_routing_weights": routing_weights,
            "risk_logits": risk_logits,
            "latent_representation": latent_representation,
            "confidence": confidence,
            "inference_timing_ms": inference_timing_ms,
            "fast_safe_triggered": fast_safe_mask
        }

if __name__ == "__main__":
    print("Benchmarking EdgeRiskMoE...")
    model = EdgeRiskMoE()
    model.eval()
    
    # 1. Test deterministic fast safe
    x_safe = torch.zeros(1, 6)
    out_safe = model(x_safe)
    print("\n--- Fast Safe Path ---")
    print(f"Risk Logits: {out_safe['risk_logits'].item():.4f} (Expected highly negative)")
    print(f"Fast Safe Triggered: {out_safe['fast_safe_triggered'].item()}")
    print(f"Latency: {out_safe['inference_timing_ms']:.3f} ms")
    
    # 2. Test standard inference and routing
    x_risk = torch.tensor([[0.8, 0.9, 1.0, 0.2, 0.9, 0.5]])
    out_risk = model(x_risk)
    print("\n--- Standard Inference ---")
    print(f"Risk Logits: {out_risk['risk_logits'].item():.4f}")
    print(f"Confidence: {out_risk['confidence'].item():.4f}")
    print(f"Routing Weights: {out_risk['expert_routing_weights'].detach().numpy()}")
    print(f"Fast Safe Triggered: {out_risk['fast_safe_triggered'].item()}")
    
    # 3. Throughput Benchmark
    print("\n--- Throughput Benchmark (Independent of Network) ---")
    x_batch = torch.rand(1024, 6)
    
    # Warmup
    for _ in range(10): model(x_batch)
    
    start = time.perf_counter()
    iters = 100
    for _ in range(iters):
        model(x_batch)
    end = time.perf_counter()
    
    total_time = end - start
    throughput = (1024 * iters) / total_time
    print(f"Processed {1024 * iters} inferences in {total_time:.3f}s")
    print(f"Throughput: {throughput:.1f} inferences / sec")
