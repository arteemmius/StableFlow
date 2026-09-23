--liquibase formatted sql

--changeset blockchain-handler:004-create-initial-partitions
--comment: Partitions for the previous, the current and the next three months. Later months are created by
--comment: PartitionMaintenanceJob, older ones on demand by PartitionManager.
SELECT ensure_ethereum_events_partitions(now() - INTERVAL '1 month', 4);
--rollback SELECT 1;
