"""Version the dual-integrity wire schema alongside Kotlin contract changes."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
folder = ROOT / "contracts"
for path in folder.glob("*.schema.json"):
    data = json.loads(path.read_text())
    data.pop("", None)
    data["$schema"] = "http://json-schema.org/draft-07/schema#"
    path.write_text(json.dumps(data, indent=2) + "\n")
properties = {
    "schema_version": {"const": 2}, "vpa_hash": {"type": "string", "pattern": "^[a-f0-9]{64}$"},
    "session_id": {"type": "string", "format": "uuid"}, "audience": {"type": "string", "minLength": 1},
    "risk_score": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
    "confidence": {"type": "number", "minimum": 0, "maximum": 1},
    "risk_class": {"enum": ["LOW", "ELEVATED", "HIGH", "UNKNOWN"]},
    "reason_codes": {"type": "array", "maxItems": 10, "items": {"type": "string", "maxLength": 80}},
    "issued_at": {"type": "integer", "minimum": 0}, "expires_at": {"type": "integer", "minimum": 0},
    "model_version": {"type": "string"}, "graph_as_of": {"type": "integer", "minimum": 0},
    "data_kind": {"enum": ["synthetic", "institutional", "unknown"]}, "key_id": {"type": "string"},
    "algorithm": {"const": "RS256"}, "signed_payload": {"type": "string"}, "signature": {"type": "string"}}
schema = {"$schema": "http://json-schema.org/draft-07/schema#", "title": "GraphRiskTokenV2", "type": "object",
          "additionalProperties": False, "properties": properties, "required": list(properties),
          "allOf": [{"if": {"properties": {"risk_class": {"const": "UNKNOWN"}}},
                     "then": {"properties": {"risk_score": {"type": "null"}}},
                     "else": {"properties": {"risk_score": {"type": "number"}}}}]}
(folder / "GraphRiskToken.schema.json").write_text(json.dumps(schema, indent=2) + "\n")
path = folder / "ContextSnapshot.schema.json"
data = json.loads(path.read_text())
data["properties"]["transaction"]["properties"].update({"online_purchase": {"type": "boolean"},
    "independently_verified": {"type": "boolean"}, "beneficiary_ref": {"type": ["string", "null"]}})
data["properties"]["graph_risk_token"] = {"anyOf": [{"$ref": "GraphRiskToken.schema.json"}, {"type": "null"}]}
for field in ("communication", "device", "baseline"):
    data["properties"][field]["type"] = ["object", "null"]
path.write_text(json.dumps(data, indent=2) + "\n")
path = folder / "InterventionDecision.schema.json"
data = json.loads(path.read_text())
data["properties"].update({"counterparty_risk": {"type": ["number", "null"], "minimum": 0, "maximum": 1},
                            "template_id": {"type": "string"}})
data["required"] = list(dict.fromkeys(data["required"] + ["counterparty_risk", "template_id"]))
path.write_text(json.dumps(data, indent=2) + "\n")
