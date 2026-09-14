import pandas as pd
import numpy as np
import os
import random
from datetime import datetime, timedelta

# Configuration
SEED = 42
np.random.seed(SEED)
random.seed(SEED)

OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../datasets/synthetic"))
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Scale Config (Triple scale approx) ──
NUM_ACCOUNTS = 30000
DAYS = 90
START_DATE = datetime(2026, 1, 1)

# Node lists
accounts = []
vpas = []
devices = []
phones = []

# Edge lists
# Static
account_owns_vpa = []
device_accesses_account = []
phone_associated_with_account = []
# Temporal
account_sends_to_account = []
vpa_receives_from_vpa = []

# Fraud Labels
fraud_labels = {} # account_id -> label (1 for fraud, 0 for legit)

def add_transaction(src_acc, dst_acc, amount, ts, is_fraud=False):
    account_sends_to_account.append({
        'src': src_acc, 'dst': dst_acc, 'amount': amount, 'timestamp': ts.timestamp(), 'is_fraud': int(is_fraud)
    })
    # For MVP, assume 1-to-1 mapping of account to VPA for simplicity in transactions, 
    # or just pick the first VPA of the account.
    src_vpa = src_acc.replace("acc_", "vpa_", 1)
    dst_vpa = dst_acc.replace("acc_", "vpa_", 1)
    vpa_receives_from_vpa.append({
        'src': src_vpa, 'dst': dst_vpa, 'amount': amount, 'timestamp': ts.timestamp(), 'is_fraud': int(is_fraud)
    })

def random_ts(start_day=0, end_day=DAYS):
    delta = timedelta(days=random.randint(start_day, end_day-1), 
                      hours=random.randint(0, 23), 
                      minutes=random.randint(0, 59), 
                      seconds=random.randint(0, 59))
    return START_DATE + delta

# ── Generate Base Nodes and Static Edges ──
print("Generating nodes and static edges...")
for i in range(NUM_ACCOUNTS):
    acc_id = f"acc_{i}"
    accounts.append({'id': acc_id})
    fraud_labels[acc_id] = 0
    
    # 1 to 3 VPAs per account (mostly 1)
    num_vpas = np.random.choice([1, 2, 3], p=[0.9, 0.08, 0.02])
    for j in range(num_vpas):
        vpa_id = f"vpa_{i}" if j == 0 else f"vpa_{i}_{j}"
        vpas.append({'id': vpa_id})
        account_owns_vpa.append({'src': acc_id, 'dst': vpa_id})
        
    # 1 to 2 Devices
    num_devices = np.random.choice([1, 2], p=[0.95, 0.05])
    for j in range(num_devices):
        dev_id = f"dev_{i}_{j}"
        devices.append({'id': dev_id})
        device_accesses_account.append({'src': dev_id, 'dst': acc_id})
        
    # 1 Phone
    phone_id = f"phone_{i}"
    phones.append({'id': phone_id})
    phone_associated_with_account.append({'src': phone_id, 'dst': acc_id})


# ── Pattern Generation ──

# Pool of accounts to use
available_accounts = [f"acc_{i}" for i in range(NUM_ACCOUNTS)]
random.shuffle(available_accounts)

def pop_accounts(n):
    res = available_accounts[:n]
    del available_accounts[:n]
    return res

print("Generating Pattern 1: Legitimate household patterns...")
# Dense subgraphs of 3-5 nodes, frequent transfers
for _ in range(1000): # 1000 households
    size = random.randint(3, 5)
    hh_accs = pop_accounts(size)
    for _ in range(random.randint(10, 30)):
        u, v = random.sample(hh_accs, 2)
        add_transaction(u, v, round(random.uniform(500, 5000), 2), random_ts())

print("Generating Pattern 2: Merchant patterns...")
# 1 merchant, many incoming, zero outgoing
for _ in range(500):
    merchant = pop_accounts(1)[0]
    customers = random.sample(available_accounts, random.randint(50, 200)) # Customers can be from anywhere
    for cust in customers:
        for _ in range(random.randint(1, 3)):
            add_transaction(cust, merchant, round(random.uniform(100, 2000), 2), random_ts())

print("Generating Pattern 3: Peer-transfer patterns...")
# Sparse, 1-to-1 occasional transfers (the bulk of the remaining accounts)
peer_pool = pop_accounts(15000)
for _ in range(30000):
    u, v = random.sample(peer_pool, 2)
    add_transaction(u, v, round(random.uniform(10, 10000), 2), random_ts())

print("Generating Pattern 4: High fan-in mule pattern...")
for _ in range(100):
    mule = pop_accounts(1)[0]
    fraud_labels[mule] = 1
    victims = pop_accounts(random.randint(20, 50))
    for v in victims:
        add_transaction(v, mule, round(random.uniform(10000, 50000), 2), random_ts(), is_fraud=True)

print("Generating Pattern 5: Rapid fan-out laundering pattern...")
for _ in range(100):
    source_mule = pop_accounts(1)[0]
    fraud_labels[source_mule] = 1
    receivers = pop_accounts(random.randint(10, 30))
    base_ts = random_ts(0, DAYS-2)
    for i, r in enumerate(receivers):
        fraud_labels[r] = 1
        ts = base_ts + timedelta(minutes=random.randint(1, 60))
        add_transaction(source_mule, r, round(random.uniform(5000, 20000), 2), ts, is_fraud=True)

print("Generating Pattern 6: Layered mule chains...")
# A -> B -> C -> D
for _ in range(100):
    chain = pop_accounts(random.randint(3, 6))
    for c in chain: fraud_labels[c] = 1
    base_ts = random_ts(0, DAYS-2)
    amt = round(random.uniform(20000, 80000), 2)
    for i in range(len(chain)-1):
        ts = base_ts + timedelta(hours=random.randint(1, 5) * i)
        add_transaction(chain[i], chain[i+1], amt, ts, is_fraud=True)
        amt = amt * random.uniform(0.9, 0.99) # slightly less each hop (fees/cuts)

print("Generating Pattern 7: Star-to-chain transition...")
# Many -> Mule 1 -> Mule 2 -> Mule 3
for _ in range(50):
    mules = pop_accounts(3)
    for m in mules: fraud_labels[m] = 1
    victims = pop_accounts(random.randint(10, 20))
    base_ts = random_ts(0, DAYS-3)
    total_amt = 0
    # Fan in
    for v in victims:
        amt = round(random.uniform(5000, 15000), 2)
        total_amt += amt
        add_transaction(v, mules[0], amt, base_ts + timedelta(minutes=random.randint(0, 120)), is_fraud=True)
    
    # Chain out
    ts2 = base_ts + timedelta(hours=3)
    add_transaction(mules[0], mules[1], total_amt * 0.95, ts2, is_fraud=True)
    ts3 = ts2 + timedelta(hours=2)
    add_transaction(mules[1], mules[2], total_amt * 0.90, ts3, is_fraud=True)

print("Generating Pattern 8: Smurfing...")
# Many small transactions below typical rule thresholds (e.g., < 49000 INR)
for _ in range(50):
    smurfs = pop_accounts(random.randint(5, 10))
    target = pop_accounts(1)[0]
    for s in smurfs: fraud_labels[s] = 1
    fraud_labels[target] = 1
    base_ts = random_ts(0, DAYS-5)
    for s in smurfs:
        for _ in range(random.randint(5, 15)):
            ts = base_ts + timedelta(days=random.randint(0, 4), hours=random.randint(0, 23))
            add_transaction(s, target, round(random.uniform(40000, 48000), 2), ts, is_fraud=True)

print("Generating Pattern 9: Burst transaction rings...")
# Cyclic A -> B -> C -> A rapidly
for _ in range(50):
    ring = pop_accounts(random.randint(3, 5))
    for r in ring: fraud_labels[r] = 1
    base_ts = random_ts(0, DAYS-1)
    amt = round(random.uniform(50000, 100000), 2)
    for i in range(len(ring)):
        src = ring[i]
        dst = ring[(i+1) % len(ring)]
        ts = base_ts + timedelta(minutes=random.randint(5, 30) * i)
        add_transaction(src, dst, amt, ts, is_fraud=True)

print("Generating Pattern 10: Camouflaged mule nodes...")
# Mules mixed with legitimate-looking peer transfers
for _ in range(100):
    mule = pop_accounts(1)[0]
    fraud_labels[mule] = 1
    
    # Illegitimate burst
    base_ts = random_ts(0, DAYS-2)
    victims = pop_accounts(5)
    for v in victims:
        add_transaction(v, mule, round(random.uniform(20000, 50000), 2), base_ts + timedelta(minutes=random.randint(0, 60)), is_fraud=True)
    
    # Legitimate camouflage transfers over time
    legit_peers = pop_accounts(5)
    for peer in legit_peers:
        for _ in range(random.randint(2, 5)):
            add_transaction(mule, peer, round(random.uniform(100, 1000), 2), random_ts(), is_fraud=False)
            add_transaction(peer, mule, round(random.uniform(100, 1000), 2), random_ts(), is_fraud=False)


# ── Save to CSV ──
print("Saving nodes...")
pd.DataFrame(accounts).to_csv(os.path.join(OUTPUT_DIR, "nodes_account.csv"), index=False)
pd.DataFrame(vpas).to_csv(os.path.join(OUTPUT_DIR, "nodes_vpa.csv"), index=False)
pd.DataFrame(devices).to_csv(os.path.join(OUTPUT_DIR, "nodes_device.csv"), index=False)
pd.DataFrame(phones).to_csv(os.path.join(OUTPUT_DIR, "nodes_phone.csv"), index=False)

print("Saving static edges...")
pd.DataFrame(account_owns_vpa).to_csv(os.path.join(OUTPUT_DIR, "edges_account_owns_vpa.csv"), index=False)
pd.DataFrame(device_accesses_account).to_csv(os.path.join(OUTPUT_DIR, "edges_device_accesses_account.csv"), index=False)
pd.DataFrame(phone_associated_with_account).to_csv(os.path.join(OUTPUT_DIR, "edges_phone_associated_with_account.csv"), index=False)

print("Saving temporal edges...")
pd.DataFrame(account_sends_to_account).to_csv(os.path.join(OUTPUT_DIR, "edges_account_sends_to_account.csv"), index=False)
pd.DataFrame(vpa_receives_from_vpa).to_csv(os.path.join(OUTPUT_DIR, "edges_vpa_receives_from_vpa.csv"), index=False)

print("Saving labels...")
labels_df = pd.DataFrame([{'account_id': k, 'is_fraud': v} for k, v in fraud_labels.items()])
labels_df.to_csv(os.path.join(OUTPUT_DIR, "account_labels.csv"), index=False)

print("Simulation complete. Data saved to:", OUTPUT_DIR)
