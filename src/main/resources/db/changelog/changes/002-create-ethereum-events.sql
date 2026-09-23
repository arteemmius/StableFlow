--liquibase formatted sql

--changeset blockchain-handler:002-create-ethereum-events
--comment: Journal of consumed contract events, partitioned by month of the block timestamp.
-- A sequence instead of an identity column: identity columns on partitioned tables require PostgreSQL 17.
CREATE SEQUENCE ethereum_events_id_seq AS BIGINT;

CREATE TABLE ethereum_events
(
    id               BIGINT      NOT NULL DEFAULT nextval('ethereum_events_id_seq'),
    tx_hash          VARCHAR(66) NOT NULL,
    log_index        INTEGER     NOT NULL,
    block_hash       VARCHAR(66) NOT NULL,
    block_number     BIGINT      NOT NULL,
    block_timestamp  TIMESTAMPTZ NOT NULL,
    event_type       VARCHAR(32) NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    removed          BOOLEAN     NOT NULL DEFAULT FALSE,
    payload          JSONB       NOT NULL,
    processed        BOOLEAN     NOT NULL DEFAULT FALSE,
    retry_count      INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Unique constraints of a partitioned table must contain the partition key.
    CONSTRAINT pk_ethereum_events PRIMARY KEY (id, block_timestamp),
    -- One row per observation of a log: the log itself and, after a reorg, its removal.
    CONSTRAINT uq_ethereum_events_log UNIQUE (block_hash, log_index, removed, block_timestamp)
) PARTITION BY RANGE (block_timestamp);

ALTER SEQUENCE ethereum_events_id_seq OWNED BY ethereum_events.id;

-- Lookup by block.
CREATE INDEX idx_ethereum_events_block_number ON ethereum_events (block_number);
-- Lookup of all events of a transaction.
CREATE INDEX idx_ethereum_events_tx_hash ON ethereum_events (tx_hash);
-- Projection retry worker. Partial: only unprocessed rows are indexed, so the index stays tiny.
CREATE INDEX idx_ethereum_events_processed_retry ON ethereum_events (processed, retry_count) WHERE processed = FALSE;

COMMENT ON TABLE ethereum_events IS 'Journal of consumed contract events (source of truth of the processing layer)';
COMMENT ON COLUMN ethereum_events.processed IS 'TRUE once the event has been projected into its read model';
COMMENT ON COLUMN ethereum_events.retry_count IS 'Failed projection attempts';
--rollback DROP TABLE ethereum_events;
