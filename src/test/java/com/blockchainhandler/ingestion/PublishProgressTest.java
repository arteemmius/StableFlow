package com.blockchainhandler.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublishProgressTest {

    private final PublishProgress progress = new PublishProgress();

    @Test
    @DisplayName("nothing to backfill before the first publication")
    void emptyInitially() {
        assertThat(progress.claimBackfill()).isEmpty();
        assertThat(progress.checkpoint()).isEmpty();
        assertThat(progress.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("a backfill resumes from the last published block")
    void resumesFromHighestPublishedBlock() {
        progress.onPublished(100);
        progress.onPublished(99);

        assertThat(progress.claimBackfill()).hasValue(100);
    }

    @Test
    @DisplayName("failed publications move the resume block back and are handed over to the backfill")
    void failuresMoveTheResumeBlockBack() {
        progress.onPublished(200);
        progress.onFailed(190);
        progress.onFailed(180);

        assertThat(progress.hasFailures()).isTrue();
        assertThat(progress.checkpoint()).hasValue(180);
        assertThat(progress.claimBackfill()).hasValue(180);
        assertThat(progress.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("the checkpoint does not advance past a backfill that has not finished")
    void checkpointWaitsForPendingBackfills() {
        progress.onPublished(100);
        assertThat(progress.claimBackfill()).hasValue(100);

        progress.onPublished(150);
        assertThat(progress.checkpoint()).hasValue(100);

        progress.backfillFinished();
        assertThat(progress.checkpoint()).hasValue(150);
    }

    @Test
    @DisplayName("an interrupted backfill reports where it stopped")
    void interruptedBackfillKeepsItsGap() {
        progress.onPublished(100);
        progress.claimBackfill();
        progress.onPublished(300);

        progress.onFailed(120);
        progress.backfillFinished();

        assertThat(progress.checkpoint()).hasValue(120);
        assertThat(progress.claimBackfill()).hasValue(120);
    }

    @Test
    @DisplayName("a restored checkpoint is used for the first backfill")
    void restoresCheckpoint() {
        progress.restore(500);

        assertThat(progress.claimBackfill()).hasValue(500);
    }
}
