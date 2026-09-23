--liquibase formatted sql

--changeset blockchain-handler:003-ensure-partition-function splitStatements:false runOnChange:true
--comment: Creates the monthly partition of ethereum_events that covers the given timestamp. Idempotent.
CREATE OR REPLACE FUNCTION ensure_ethereum_events_partition(p_ts TIMESTAMPTZ)
    RETURNS TEXT
    LANGUAGE plpgsql
AS
$$
DECLARE
    -- Month boundaries are computed in UTC, independently of the session time zone.
    v_month_start TIMESTAMP := date_trunc('month', p_ts AT TIME ZONE 'UTC');
    v_name        TEXT      := 'ethereum_events_' || to_char(v_month_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_name) IS NOT NULL THEN
        RETURN v_name;
    END IF;
    -- Serializes concurrent creators (consumers, instances, the maintenance job); re-checks under the lock.
    PERFORM pg_advisory_xact_lock(hashtext('ethereum_events_partitions'));
    IF to_regclass(v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I PARTITION OF ethereum_events FOR VALUES FROM (%L) TO (%L)',
                       v_name,
                       v_month_start AT TIME ZONE 'UTC',
                       (v_month_start + INTERVAL '1 month') AT TIME ZONE 'UTC');
    END IF;
    RETURN v_name;
END;
$$;
--rollback DROP FUNCTION IF EXISTS ensure_ethereum_events_partition(TIMESTAMPTZ);

--changeset blockchain-handler:003-ensure-partitions-function splitStatements:false runOnChange:true
--comment: Creates the partitions of the month of p_from and of the p_months_ahead following months.
CREATE OR REPLACE FUNCTION ensure_ethereum_events_partitions(p_from TIMESTAMPTZ, p_months_ahead INTEGER)
    RETURNS INTEGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_first_month TIMESTAMP := date_trunc('month', p_from AT TIME ZONE 'UTC');
    v_month       TIMESTAMP;
    v_created     INTEGER   := 0;
BEGIN
    FOR i IN 0..p_months_ahead
        LOOP
            v_month := v_first_month + make_interval(months => i);
            IF to_regclass('ethereum_events_' || to_char(v_month, 'YYYY_MM')) IS NULL THEN
                PERFORM ensure_ethereum_events_partition(v_month AT TIME ZONE 'UTC');
                v_created := v_created + 1;
            END IF;
        END LOOP;
    RETURN v_created;
END;
$$;
--rollback DROP FUNCTION IF EXISTS ensure_ethereum_events_partitions(TIMESTAMPTZ, INTEGER);
