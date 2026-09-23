package com.blockchainhandler.ingestion;

import java.util.OptionalLong;

/**
 * Tracks which blocks have been handed to Kafka, to know where a gap backfill has to start.
 *
 * <p>Three watermarks are kept:
 * <ul>
 *   <li>the highest block of a successfully published log;</li>
 *   <li>the lowest block of a log whose publication failed and that has not been handed to a backfill yet;</li>
 *   <li>the lowest start block of backfills that are requested but not finished.</li>
 * </ul>
 * Backfills start at their block inclusive: re-publishing a block is harmless because processing is idempotent,
 * while skipping one would lose events. Thread-safe.
 */
public final class PublishProgress {

    private static final long NONE = -1L;

    private long highestPublished = NONE;
    private long failedFloor = NONE;
    private long pendingBackfillFloor = NONE;
    private int pendingBackfills;

    /**
     * Restores the checkpoint persisted by a previous run.
     *
     * @param checkpoint persisted resume block
     */
    public synchronized void restore(long checkpoint) {
        highestPublished = Math.max(highestPublished, checkpoint);
    }

    /**
     * Records a successful publication.
     *
     * @param blockNumber block of the published log
     */
    public synchronized void onPublished(long blockNumber) {
        highestPublished = Math.max(highestPublished, blockNumber);
    }

    /**
     * Records a block that has to be re-published by a future backfill.
     *
     * @param blockNumber block of the log that could not be published
     */
    public synchronized void onFailed(long blockNumber) {
        failedFloor = min(failedFloor, blockNumber);
    }

    /**
     * Tells whether some blocks wait for a backfill.
     *
     * @return {@code true} if a publication failed and no backfill has claimed it yet
     */
    public synchronized boolean hasFailures() {
        return failedFloor != NONE;
    }

    /**
     * Claims a backfill: returns the block it has to start from and hands the recorded failures over to it.
     * Every successful claim must be followed by exactly one {@link #backfillFinished()}.
     *
     * @return start block of the backfill, empty if nothing has been published yet (nothing to catch up)
     */
    public synchronized OptionalLong claimBackfill() {
        long start = failedFloor != NONE ? failedFloor : highestPublished;
        if (start == NONE) {
            return OptionalLong.empty();
        }
        failedFloor = NONE;
        pendingBackfills++;
        pendingBackfillFloor = min(pendingBackfillFloor, start);
        return OptionalLong.of(start);
    }

    /**
     * Releases a claim made by {@link #claimBackfill()}. A backfill that could not complete must first report the
     * block it stopped at through {@link #onFailed(long)}.
     */
    public synchronized void backfillFinished() {
        if (pendingBackfills > 0 && --pendingBackfills == 0) {
            pendingBackfillFloor = NONE;
        }
    }

    /**
     * Returns the conservative block to resume from after a restart.
     *
     * @return resume block, empty if nothing has been published yet
     */
    public synchronized OptionalLong checkpoint() {
        long floor = min(failedFloor, pendingBackfillFloor);
        if (floor != NONE) {
            return OptionalLong.of(floor);
        }
        return highestPublished == NONE ? OptionalLong.empty() : OptionalLong.of(highestPublished);
    }

    private static long min(long current, long candidate) {
        if (current == NONE) {
            return candidate;
        }
        if (candidate == NONE) {
            return current;
        }
        return Math.min(current, candidate);
    }
}
