--liquibase formatted sql

--changeset blockchain-handler:005-create-transactions-feed-index runInTransaction:false
--comment: Feed of the latest transfers of all addresses (GET /api/v1/transfers/latest), read backward with keyset
--comment: pagination. CONCURRENTLY keeps the projection writing while the index of a large table is built.
-- Ascending on purpose: new rows land on the rightmost leaf page (insertion fast path, splits leave pages
-- fillfactor-full), whereas a descending index would take every insert on its leftmost page and split it 50/50.
-- No IF NOT EXISTS: a failed concurrent build leaves an INVALID index that must not pass for a created one.
CREATE INDEX CONCURRENTLY idx_transactions_timestamp_log_id ON transactions (block_timestamp, log_index, id);
--rollback DROP INDEX CONCURRENTLY idx_transactions_timestamp_log_id;
