package core.prediction;

/**
 * Calculates a recovery confidence score
 * from the available recovery features.
 *
 * Unknown values are not rewarded or penalized.
 */
public class RecoveryPredictor {

    public RecoveryPrediction predict(RecoveryFeatures features) {

        if (features == null) {
            throw new IllegalArgumentException(
                    "RecoveryFeatures cannot be null"
            );
        }

        double score = 0.0;

        // Header validity: max +25
        if (features.hasValidHeader()) {
            score += 25;
        }

        // Footer validity: max +15
        if (features.hasValidFooter()) {
            score += 15;
        }

        // Metadata availability: max +15
        if (features.hasMetadata()) {
            score += 15;
        }

        // Cluster information: max +25
        if (features.hasClustersAvailable()) {
            score += 25;
        }

        /*
         * Fragmentation:
         *
         * false = confirmed not fragmented -> +10
         * true  = fragmented               -> +0
         * null  = unknown                  -> +0
         */
        Boolean fragmented = features.isFragmented();

        if (fragmented != null && !fragmented) {
            score += 10;
        }

        /*
         * Overwrite condition:
         *
         * 0% overwritten   -> +10
         * 50% overwritten  -> +5
         * 100% overwritten -> +0
         * null             -> unknown, +0
         */
        Double overwrittenPercentage =
                features.getOverwrittenPercentage();

        if (overwrittenPercentage != null) {

            double overwriteScore =
                    10 - (overwrittenPercentage * 0.10);

            score += overwriteScore;
        }

        // Safety clamp: score can never leave 0-100 range.
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
