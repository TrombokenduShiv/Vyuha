"""
Vyuha 2.0 — Conformal Prediction Wrapper
==========================================
Split Conformal Prediction for uncertainty quantification.

Given a point prediction p_coercion (from the BeliefUpdater),
produces a prediction SET instead of a point estimate:
    {SAFE}              -> high confidence it's safe
    {RISK}              -> high confidence it's risky
    {SAFE, RISK}        -> uncertain — could be either
    {SAFE, RISK, ABSTAIN} -> very uncertain — model should abstain

Coverage guarantee: with alpha = 0.10, the true label is contained
in the prediction set at least 90% of the time (marginal guarantee).

Architecture ref: ARCHITECTURE_FREEZE_V1 §2 (Conformal Predictor), §7 Step 7.
"""
import numpy as np
from enum import Enum


class UncertaintyLabel(Enum):
    SAFE = "SAFE"
    RISK = "RISK"
    ABSTAIN = "ABSTAIN"


class ConformalPredictor:
    """
    Split Conformal Prediction for binary fraud detection.

    Calibration:
        1. On a calibration set, compute nonconformity scores
        2. Find the quantile threshold q_hat at level (1 - alpha)

    Prediction:
        1. Compute nonconformity score for new sample
        2. Include label in prediction set if score ≤ q_hat

    This ensures P(Y ∈ C(X)) ≥ 1 - alpha.
    """

    def __init__(self, alpha: float = 0.10):
        """
        Args:
            alpha: Miscoverage rate. alpha=0.10 -> 90% coverage guarantee.
        """
        self.alpha = alpha
        self.q_hat_safe = None
        self.q_hat_risk = None
        self.calibrated = False
        self._cal_scores = None

    def calibrate(self, cal_predictions: np.ndarray, cal_labels: np.ndarray):
        """
        Calibrate on a held-out calibration set.

        Args:
            cal_predictions: Array of shape (N,) — predicted P(coercion) for each calibration sample.
            cal_labels: Array of shape (N,) — true labels (0=legit, 1=fraud).
        """
        n = len(cal_predictions)
        assert n > 0, "Calibration set must not be empty"
        assert len(cal_labels) == n, "Predictions and labels must have same length"

        # Nonconformity score: |predicted - true|
        # For SAFE (label=0): score = p_coercion (how far from 0)
        # For RISK (label=1): score = 1 - p_coercion (how far from 1)
        safe_mask = cal_labels == 0
        risk_mask = cal_labels == 1

        safe_scores = cal_predictions[safe_mask]  # Should be low for correct safe predictions
        risk_scores = 1.0 - cal_predictions[risk_mask]  # Should be low for correct risk predictions

        # Compute quantiles at level ceil((n+1)(1-alpha))/n
        # This gives the finite-sample coverage guarantee
        level = np.ceil((n + 1) * (1 - self.alpha)) / n
        level = min(level, 1.0)

        if len(safe_scores) > 0:
            self.q_hat_safe = np.quantile(safe_scores, level)
        else:
            self.q_hat_safe = 1.0

        if len(risk_scores) > 0:
            self.q_hat_risk = np.quantile(risk_scores, level)
        else:
            self.q_hat_risk = 1.0

        self._cal_scores = {
            "safe_scores": safe_scores,
            "risk_scores": risk_scores,
            "q_hat_safe": self.q_hat_safe,
            "q_hat_risk": self.q_hat_risk,
            "n_calibration": n,
            "n_safe": int(safe_mask.sum()),
            "n_risk": int(risk_mask.sum()),
        }
        self.calibrated = True

        print(f"Conformal Predictor calibrated on {n} samples "
              f"(alpha={self.alpha}, q_safe={self.q_hat_safe:.4f}, q_risk={self.q_hat_risk:.4f})")

    def predict_set(self, p_coercion: float) -> list:
        """
        Produce a prediction set for a single sample.

        Args:
            p_coercion: Predicted P(coercion) ∈ [0, 1].

        Returns:
            List of UncertaintyLabel values in the prediction set.
        """
        if not self.calibrated:
            # Uncalibrated fallback: return ABSTAIN to be safe
            return [UncertaintyLabel.SAFE, UncertaintyLabel.RISK, UncertaintyLabel.ABSTAIN]

        prediction_set = []

        # Include SAFE if nonconformity score for safe ≤ threshold
        safe_score = p_coercion  # If truly safe, p_coercion should be low
        if safe_score <= self.q_hat_safe:
            prediction_set.append(UncertaintyLabel.SAFE)

        # Include RISK if nonconformity score for risk ≤ threshold
        risk_score = 1.0 - p_coercion  # If truly risky, p_coercion should be high
        if risk_score <= self.q_hat_risk:
            prediction_set.append(UncertaintyLabel.RISK)

        # Empty set -> ABSTAIN (extremely uncertain)
        if len(prediction_set) == 0:
            prediction_set = [UncertaintyLabel.SAFE, UncertaintyLabel.RISK, UncertaintyLabel.ABSTAIN]

        # Both SAFE and RISK -> add ABSTAIN flag
        if UncertaintyLabel.SAFE in prediction_set and UncertaintyLabel.RISK in prediction_set:
            prediction_set.append(UncertaintyLabel.ABSTAIN)

        return prediction_set

    def predict_sets_batch(self, p_coercion_array: np.ndarray) -> list:
        """Batch prediction."""
        return [self.predict_set(p) for p in p_coercion_array]

    def evaluate_coverage(self, test_predictions: np.ndarray, test_labels: np.ndarray) -> dict:
        """
        Evaluate coverage on a test set.

        Returns:
            dict with coverage rate, average set size, and distribution.
        """
        n = len(test_predictions)
        covered = 0
        set_sizes = []
        distribution = {"SAFE_only": 0, "RISK_only": 0, "SAFE_RISK": 0, "ABSTAIN": 0}

        for i in range(n):
            pred_set = self.predict_set(test_predictions[i])
            set_sizes.append(len(pred_set))

            # Check coverage: true label should be in prediction set
            true_label = UncertaintyLabel.RISK if test_labels[i] == 1 else UncertaintyLabel.SAFE
            if true_label in pred_set:
                covered += 1

            # Distribution
            labels_in_set = {l.value for l in pred_set}
            if labels_in_set == {"SAFE"}:
                distribution["SAFE_only"] += 1
            elif labels_in_set == {"RISK"}:
                distribution["RISK_only"] += 1
            elif "ABSTAIN" in labels_in_set:
                distribution["ABSTAIN"] += 1
            else:
                distribution["SAFE_RISK"] += 1

        coverage = covered / n
        return {
            "coverage": round(coverage, 4),
            "target_coverage": 1 - self.alpha,
            "coverage_met": coverage >= (1 - self.alpha),
            "avg_set_size": round(np.mean(set_sizes), 3),
            "distribution": {k: round(v / n, 4) for k, v in distribution.items()},
            "n_test": n,
        }

    def get_calibration_info(self) -> dict:
        """Return calibration metadata for audit."""
        if not self.calibrated:
            return {"calibrated": False}
        return {
            "calibrated": True,
            "alpha": self.alpha,
            **self._cal_scores,
        }


if __name__ == "__main__":
    print("=" * 60)
    print("Vyuha 2.0 — Conformal Predictor Test")
    print("=" * 60)

    np.random.seed(42)

    # Generate synthetic calibration data
    n_cal = 500
    n_test = 200
    fraud_rate = 0.1

    # Simulated model predictions (imperfect)
    cal_labels = (np.random.random(n_cal) < fraud_rate).astype(float)
    cal_preds = np.where(
        cal_labels == 1,
        np.random.beta(5, 2, n_cal),   # Fraud: predictions tend high
        np.random.beta(2, 5, n_cal)    # Legit: predictions tend low
    )

    test_labels = (np.random.random(n_test) < fraud_rate).astype(float)
    test_preds = np.where(
        test_labels == 1,
        np.random.beta(5, 2, n_test),
        np.random.beta(2, 5, n_test)
    )

    # Calibrate
    cp = ConformalPredictor(alpha=0.10)
    cp.calibrate(cal_preds, cal_labels)

    # Test individual predictions
    print("\nSample predictions:")
    for p in [0.05, 0.20, 0.45, 0.70, 0.92]:
        pred_set = cp.predict_set(p)
        labels = [l.value for l in pred_set]
        print(f"  p_coercion={p:.2f} -> {labels}")

    # Evaluate coverage
    print("\nCoverage evaluation:")
    coverage_result = cp.evaluate_coverage(test_preds, test_labels)
    for k, v in coverage_result.items():
        print(f"  {k}: {v}")

    target_met = "[OK]" if coverage_result["coverage_met"] else "[FAIL]"
    print(f"\n{target_met} Coverage guarantee {'met' if coverage_result['coverage_met'] else 'NOT met'}!")
