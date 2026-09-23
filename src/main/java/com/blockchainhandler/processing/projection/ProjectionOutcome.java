package com.blockchainhandler.processing.projection;

/**
 * Result of projecting a journal entry into the {@code transactions} read model.
 */
public enum ProjectionOutcome {

    /** A new transfer was inserted. */
    PROJECTED("projected"),

    /** A transfer was removed because its log was orphaned by a chain reorganization. */
    REVERTED("reverted"),

    /** The insert was suppressed: the transfer already exists or its log has already been reverted. */
    SKIPPED("skipped"),

    /** The journal entry had already been projected (duplicate delivery) or is being projected by a worker. */
    ALREADY_PROCESSED("duplicate"),

    /** The projection failed; the entry stays unprocessed and is retried by the worker. */
    DEFERRED("deferred");

    private final String metricTag;

    ProjectionOutcome(String metricTag) {
        this.metricTag = metricTag;
    }

    /**
     * Returns the value used for the {@code outcome} metric tag.
     *
     * @return metric tag value
     */
    public String metricTag() {
        return metricTag;
    }
}
