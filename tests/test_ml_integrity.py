import copy
import hashlib
import importlib.util
import json
import time
from dataclasses import replace
from uuid import uuid4
import numpy as np
import pytest
import torch
from fastapi.testclient import TestClient
from ml.graph.data.loader import load_hetero_data, label_partition
from ml.graph.model.hgt import HGTModel
from ml.graph.inference.serve import GraphInferenceEngine
from ml.graph.inference.tokens import TokenSigner, verify_token
from ml.edge.conformal.conformal_predictor import ConformalPredictor
from ml.policy.runtime import ROOT, PaymentContext, PaymentRiskEngine, decide

torch.set_num_threads(2)


def tiny_graph():
    torch.manual_seed(10)
    features = {"account": torch.rand(4, 8), "vpa": torch.rand(4, 4),
                "device": torch.rand(2, 4), "phone": torch.rand(2, 4)}
    edges = {("vpa", "rev_owns", "account"): torch.tensor([[0, 1, 2, 3], [0, 1, 2, 3]]),
             ("account", "sends_to", "account"): torch.tensor([[0, 1, 2], [1, 2, 3]])}
    attrs = {("account", "sends_to", "account"): torch.tensor([[0., .1], [.5, .2], [1., .3]])}
    return features, edges, attrs


def test_temporal_amount_and_vpa_messages_affect_account_predictions():
    model = HGTModel().eval()
    features, edges, attrs = tiny_graph()
    original = model(features, edges, attrs)["risk_scores"]
    modified = copy.deepcopy(attrs)
    modified[next(iter(attrs))][:, 1] += 1
    assert not torch.allclose(original, model(features, edges, modified)["risk_scores"])
    modified = copy.deepcopy(features)
    modified["vpa"] *= 0
    assert not torch.allclose(original, model(modified, edges, attrs)["risk_scores"])
    loss = model(features, edges, attrs)["risk_logits"].sum()
    loss.backward()
    assert model.temporal_encoder.periodic_weight.weight.grad.abs().sum() > 0
    assert model.amount_encoder.weight.grad.abs().sum() > 0


def test_attention_is_edge_order_invariant_and_finite():
    model = HGTModel().eval()
    f, e, a = tiny_graph()
    one = model(f, e, a)["risk_scores"]
    shuffled_e = {k: v.flip(1) for k, v in e.items()}
    shuffled_a = {k: v.flip(0) for k, v in a.items()}
    torch.testing.assert_close(one, model(f, shuffled_e, shuffled_a)["risk_scores"])
    f = {k: v * 10000 for k, v in f.items()}
    assert torch.isfinite(model(f, e, a)["risk_scores"]).all()
    with pytest.raises(ValueError):
        HGTModel(hidden_dim=31, num_heads=4)


def test_disjoint_labels_and_causal_graph():
    train, val = load_hetero_data("train"), load_hetero_data("val")
    masks = train["label_masks"]
    assert torch.stack(list(masks.values())).sum(0).max() <= 1
    assert train["metadata"]["as_of"] < val["metadata"]["as_of"]
    assert train["edge_index"][("account", "sends_to", "account")].shape[1] < val["edge_index"][("account", "sends_to", "account")].shape[1]
    assert sum(len(x) for x in train["edge_attr"].values()) > 0
    for relation, attributes in train["edge_attr"].items():
        assert len(attributes) == train["edge_index"][relation].shape[1]
        assert (attributes[:, 0] >= 0).all()
    with pytest.raises(ValueError):
        load_hetero_data("typo")


def test_conformal_small_class_and_invalid_values():
    cp = ConformalPredictor(.1)
    cp.calibrate(np.array([.01, .02, .9]), np.array([0, 0, 1]))
    assert cp.q_hat_risk == 1.0
    assert len(cp.predict_set(float("nan"))) > 1
    with pytest.raises(ValueError):
        cp.calibrate(np.array([float("nan")]), np.array([0]))


@pytest.mark.parametrize("receiver", [.7, .9, 1.0])
def test_calm_social_scam_never_passes_or_isolates(receiver):
    result = decide(.05, receiver, False, PaymentContext(novelty=.95, online_purchase=True))
    assert result["action_id"] not in {"A0_PASS", "A4_ISOLATION_BREAK"}
    assert result["template_id"] == "COUNTERPARTY_WARNING"


def test_unknown_is_not_safe_or_an_allegation():
    result = decide(.05, None, False, PaymentContext(novelty=.95, online_purchase=True))
    assert result["counterparty_risk"] is None
    assert result["action_id"] == "A2_REFLECTION_CHALLENGE"
    assert result["template_id"] == "MERCHANT_VERIFICATION"
    assert result["uncertain"]


def test_malformed_policy_cannot_override_guards():
    from ml.policy.runtime import state_key
    context = PaymentContext(novelty=.95, online_purchase=True)
    key = state_key(.05, .95, False, False, True)
    assert decide(.05, .95, False, context, {key: "A0_PASS"})["action_id"] != "A0_PASS"
    assert decide(.95, .05, False, context)["action_id"] != "A4_ISOLATION_BREAK"
    assert decide(.95, .05, True, PaymentContext(True))["action_id"] != "A4_ISOLATION_BREAK"


@pytest.fixture
def signed():
    signer = TokenSigner()
    vpa, sid = hashlib.sha256(b"receiver").hexdigest(), str(uuid4())
    evidence = {"risk_score": .9, "confidence": .8, "risk_class": "HIGH", "reason_codes": ["MODEL_NETWORK_PATTERN"],
                "model_version": "test", "graph_as_of": 123, "data_kind": "synthetic"}
    token = signer.issue(evidence, vpa, sid, "bank", now=1000)
    return signer, token, vpa, sid


def test_signature_binding_tamper_expiry(signed):
    signer, token, vpa, sid = signed
    assert verify_token(token, signer.key.public_key(), vpa, sid, "bank", now=1001)["risk_score"] == .9
    for changed in [{**token, "risk_score": .01}, {**token, "risk_class": "LOW"}]:
        with pytest.raises(ValueError):
            verify_token(changed, signer.key.public_key(), vpa, sid, "bank", now=1001)
    for args in [(vpa, str(uuid4()), "bank", 1001), ("other", sid, "bank", 1001),
                 (vpa, sid, "other", 1001), (vpa, sid, "bank", 1120), (vpa, sid, "bank", 900)]:
        with pytest.raises(ValueError):
            verify_token(token, signer.key.public_key(), *args[:3], now=args[3])


def test_missing_model_does_not_fabricate_score():
    engine = GraphInferenceEngine()
    assert engine.query_risk(vpa_hash="missing")["risk_score"] is None
    with pytest.raises(FileNotFoundError):
        engine.load("absent.pt")
    assert not engine.loaded


@pytest.fixture(scope="module")
def trained_graph():
    engine = GraphInferenceEngine(allow_synthetic=True)
    engine.load()
    return engine


def test_real_graph_and_unknown_staleness(trained_graph):
    engine = trained_graph
    vpa = next(iter(engine._snapshot.by_vpa))
    account = engine._snapshot.by_vpa[vpa]
    assert engine.query_risk(vpa_hash=vpa)["risk_score"] == engine.query_risk(account_id=account)["risk_score"]
    assert engine.query_risk(vpa_hash="f" * 64)["risk_score"] is None
    old = engine._snapshot
    engine._snapshot = replace(old, refreshed_at=time.time() - 301)
    assert engine.query_risk(vpa_hash=vpa)["reason_codes"] == ["STALE_GRAPH"]
    engine._snapshot = old
    with pytest.raises(ValueError):
        GraphInferenceEngine().load()


def test_real_api_contract_and_no_content_collection(trained_graph):
    spec = importlib.util.spec_from_file_location("api_test", ROOT / "services/graph-risk-api/main.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    app = module.create_app(engine=trained_graph, demo=True)
    vpa, sid = next(iter(trained_graph._snapshot.by_vpa)), str(uuid4())
    with TestClient(app) as client:
        response = client.post("/v1/graph-risk", json={"vpa_hash": vpa, "session_id": sid})
        assert response.status_code == 200
        payload = response.json()
        import jsonschema
        import json
        schema = json.loads((ROOT / "contracts/GraphRiskToken.schema.json").read_text())
        jsonschema.validate(payload, schema)
        unknown = client.post("/v1/graph-risk", json={"vpa_hash": "f" * 64, "session_id": sid}).json()
        jsonschema.validate(unknown, schema)
        assert unknown["risk_score"] is None
        verify_token(payload, app.state.signer.key.public_key(), vpa, sid, "vyuha-demo")
        assert payload["risk_score"] == trained_graph.query_risk(vpa_hash=vpa)["risk_score"]
        assert client.post("/v1/graph-risk", json={"vpa_hash": vpa, "session_id": sid, "messages": "private"}).status_code == 422
        assert client.post("/v1/graph-risk", json={"vpa_hash": "raw@upi", "session_id": sid}).status_code == 422
        assert client.post("/v1/telemetry", json={"messages": "private"}).status_code == 501


def test_trained_agency_independent_of_receiver():
    engine = PaymentRiskEngine(policy_path=ROOT / "checkpoints/frozen_policy.json")
    x = torch.rand(32, 6)
    with torch.inference_mode():
        original = engine.moe(x)["risk_logits"]
        x[:, 4] = 1 - x[:, 4]
        torch.testing.assert_close(original, engine.moe(x)["risk_logits"])
    calm = PaymentContext(amount=.3, novelty=.95, online_purchase=True)
    assert engine.evaluate(calm, .95)["agency_risk"] < .2
