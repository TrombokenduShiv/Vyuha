"""RSA signatures over exact payload bytes; public verification keys only on phones."""
import base64
import json
import time
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


class TokenSigner:
    def __init__(self, private_key=None, key_id="demo-ephemeral"):
        self.key = private_key or rsa.generate_private_key(public_exponent=65537, key_size=2048)
        self.key_id = key_id

    @classmethod
    def from_pem(cls, pem, key_id):
        key = serialization.load_pem_private_key(pem, password=None)
        if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 2048:
            raise ValueError("RSA key must be at least 2048 bits")
        return cls(key, key_id)

    def public_key_der(self):
        return base64.b64encode(self.key.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()

    def issue(self, evidence, vpa_hash, session_id, audience, ttl=120, now=None):
        now = int(time.time() if now is None else now)
        if not 1 <= ttl <= 300:
            raise ValueError("Token lifetime out of bounds")
        payload = {**evidence, "vpa_hash": vpa_hash, "session_id": session_id,
                   "audience": audience, "issued_at": now, "expires_at": now + ttl,
                   "schema_version": 2, "key_id": self.key_id}
        raw = json.dumps(payload, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()
        signature = self.key.sign(raw, padding.PKCS1v15(), hashes.SHA256())
        return {**payload, "algorithm": "RS256", "signed_payload": base64.b64encode(raw).decode(),
                "signature": base64.b64encode(signature).decode()}


def verify_token(token, public_key, vpa_hash, session_id, audience, now=None):
    now = int(time.time() if now is None else now)
    if token.get("algorithm") != "RS256":
        raise ValueError("Unsupported signing algorithm")
    raw = base64.b64decode(token["signed_payload"], validate=True)
    public_key.verify(base64.b64decode(token["signature"], validate=True), raw,
                      padding.PKCS1v15(), hashes.SHA256())
    payload = json.loads(raw)
    if {k: v for k, v in token.items() if k not in {"signature", "signed_payload", "algorithm"}} != payload:
        raise ValueError("Envelope does not match signed payload")
    if (payload["vpa_hash"], payload["session_id"], payload["audience"]) != (vpa_hash, session_id, audience):
        raise ValueError("Token binding mismatch")
    if not payload["issued_at"] <= now + 30 or not payload["issued_at"] < payload["expires_at"] <= payload["issued_at"] + 300 or now >= payload["expires_at"]:
        raise ValueError("Expired or invalid token time")
    return payload
