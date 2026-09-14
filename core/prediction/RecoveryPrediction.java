package core.prediction;

/**
 * Stores the final recovery prediction result.
 */
public class RecoveryPrediction {

    private final double probability;
    private final String rating;

    public RecoveryPrediction(double probability, String rating) {

        if (probability < 0 || probability > 100) {
            throw new IllegalArgumentException(
                    "Probability must be between 0 and 100"
            );
        }

        this.probability = probability;
        this.rating = rating;
    }

    public double getProbability() {
        return probability;
    }

    public String getRating() {
        return rating;
    }

    @Override
    public String toString() {
        return String.format(
                "Recovery probability: %.1f%% (%s)",
                probability,
                rating
        );
    }
}
