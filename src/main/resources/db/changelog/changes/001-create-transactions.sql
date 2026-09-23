--liquibase formatted sql

--changeset blockchain-handler:001-create-transactions
--comment: Read model of USDC Transfer events queried by the REST API. One row per Transfer log.
CREATE TABLE transactions
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tx_hash          VARCHAR(66)    NOT NULL,
    log_index        INTEGER        NOT NULL,
    block_number     BIGINT         NOT NULL,
    block_hash       VARCHAR(66)    NOT NULL,
    block_timestamp  TIMESTAMPTZ    NOT NULL,
    contract_address VARCHAR(42)    NOT NULL,
    from_address     VARCHAR(42)    NOT NULL,
    to_address       VARCHAR(42)    NOT NULL,
    value_raw        NUMERIC(78, 0) NOT NULL,
    value_usdc       NUMERIC(38, 6) NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- (block_hash, log_index) identifies a log instance on-chain: idempotent upserts and reorg compensation.
    CONSTRAINT uq_transactions_block_log UNIQUE (block_hash, log_index)
);

-- Transfers of an address within a time range (the address may be the sender or the receiver).
CREATE INDEX idx_transactions_from_address_timestamp ON transactions (from_address, block_timestamp DESC);
CREATE INDEX idx_transactions_to_address_timestamp ON transactions (to_address, block_timestamp DESC);
-- Lookup by block.
CREATE INDEX idx_transactions_block_number ON transactions (block_number);
-- Transaction details.
CREATE INDEX idx_transactions_tx_hash ON transactions (tx_hash);
-- Daily statistics of a token.
CREATE INDEX idx_transactions_contract_timestamp ON transactions (contract_address, block_timestamp);

COMMENT ON TABLE transactions IS 'USDC Transfer events (read model of the REST API)';
COMMENT ON COLUMN transactions.value_raw IS 'Amount in the smallest token unit (uint256)';
COMMENT ON COLUMN transactions.value_usdc IS 'Amount in USDC (6 decimals)';
--rollback DROP TABLE transactions;
