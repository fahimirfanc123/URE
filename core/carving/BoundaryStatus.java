package core.carving;

/**
 * Boundary resolution status for a carved file candidate.
 */
public enum BoundaryStatus {

    /**
     * Exact file boundary identified and validated via format structure or footer.
     */
    EXACT,

    /**
     * File header found, but boundary search reached maximum scan limit or EOF without a valid end marker.
     */
    TRUNCATED,

    /**
     * Candidate header matched but internal structure is invalid or corrupt.
     */
    INVALID
}
