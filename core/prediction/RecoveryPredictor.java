package core.prediction;

/**
 * Calculates a recovery confidence score
 * from the available recovery features.
 */
public class RecoveryPredictor {

    public RecoveryPrediction predict(RecoveryFeatures features) {

        if (features == null) {
            throw new IllegalArgumentException(
                    "RecoveryFeatures cannot be null"
            );
        }

        double score = 0.0;

        if (features.hasValidHeader()) {
            score += 25;
        }

        if (features.hasValidFooter()) {
            score += 15;
        }

        if (features.hasMetadata()) {
            score += 15;
        }

        if (features.hasClustersAvailable()) {
            score += 25;
        }

        if (!features.isFragmented()) {
            score += 10;
        }

        double overwritePenalty =
                features.getOverwrittenPercentage() * 0.10;

        score += 10 - overwritePenalty;

        score = Math.max(0, Math.min(score, 100));

        String rating;

        if (score >= 80) {
            rating = "Excellent";
        } else if (score >= 60) {
            rating = "Good";
        } else if (score >= 40) {
            rating = "Moderate";
        } else if (score >= 20) {
            rating = "Poor";
        } else {
            rating = "Very Poor";
        }

        return new RecoveryPrediction(score, rating);
    }
}

