package com.blockchainhandler.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blockchainhandler.api.dto.TransferFeedRequest;
import com.blockchainhandler.api.dto.TransferSearchRequest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferFeedCriteriaTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    @DisplayName("the first page starts right before 'to'; bounds and size have defaults")
    void firstPageStartsBeforeTo() {
        assertThat(TransferFeedCriteria.of(new TransferFeedRequest(null, null, null, null)))
                .isEqualTo(new TransferFeedCriteria(TransferSearchCriteria.UNBOUNDED_FROM,
                        TransferCursor.before(TransferSearchCriteria.UNBOUNDED_TO), TransferSearchRequest.DEFAULT_SIZE));
        assertThat(TransferFeedCriteria.of(new TransferFeedRequest(FROM, TO, 50, null)))
                .isEqualTo(new TransferFeedCriteria(FROM, TransferCursor.before(TO), 50));
    }

    @Test
    @DisplayName("a cursor continues the listing but never goes past 'to'")
    void cursorIsCappedByTo() {
        TransferCursor inWindow = new TransferCursor(TO.minusSeconds(12), 5, 42);
        assertThat(TransferFeedCriteria.of(new TransferFeedRequest(FROM, TO, null, inWindow.encode())).before())
                .isEqualTo(inWindow);

        TransferCursor pastTo = new TransferCursor(TO, 5, 42);
        assertThat(TransferFeedCriteria.of(new TransferFeedRequest(FROM, TO, null, pastTo.encode())).before())
                .isEqualTo(TransferCursor.before(TO));
    }

    @Test
    @DisplayName("an empty cursor means the first page")
    void emptyCursorMeansFirstPage() {
        assertThat(TransferFeedCriteria.of(new TransferFeedRequest(FROM, TO, null, "")).before())
                .isEqualTo(TransferCursor.before(TO));
    }
}
