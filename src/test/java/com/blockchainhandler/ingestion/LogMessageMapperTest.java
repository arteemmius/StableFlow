package com.blockchainhandler.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.common.messaging.EventType;
import com.blockchainhandler.ingestion.client.RpcLog;
import com.blockchainhandler.testsupport.UsdcTransfers;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMessageMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:50:37Z");

    private final LogMessageMapper mapper = new LogMessageMapper(1L, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("maps a real subscription log: hex quantities become numbers")
    void mapsRealLog() {
        EthereumEventMessage message = mapper.toMessage(UsdcTransfers.realRpcLog(), EventSource.SUBSCRIPTION);

        assertThat(message.schemaVersion()).isEqualTo(EthereumEventMessage.CURRENT_SCHEMA_VERSION);
        assertThat(message.eventType()).isEqualTo(EventType.TRANSFER);
        assertThat(message.chainId()).isEqualTo(1L);
        assertThat(message.contractAddress()).isEqualTo(UsdcTransfers.USDC_ADDRESS);
        assertThat(message.transactionHash()).isEqualTo(UsdcTransfers.TX_HASH);
        assertThat(message.transactionIndex()).isEqualTo(16L);
        assertThat(message.blockHash()).isEqualTo(UsdcTransfers.BLOCK_HASH);
        assertThat(message.blockNumber()).isEqualTo(26_039_695L);
        assertThat(message.logIndex()).isEqualTo(13);
        assertThat(message.topics()).hasSize(3).first().isEqualTo(UsdcTransfers.TRANSFER_TOPIC);
        assertThat(message.data()).isEqualTo(UsdcTransfers.data(UsdcTransfers.RAW_VALUE));
        assertThat(message.removed()).isFalse();
        assertThat(message.source()).isEqualTo(EventSource.SUBSCRIPTION);
        assertThat(message.observedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("keeps the reorg removal flag and lower-cases hex strings")
    void keepsRemovedFlag() {
        RpcLog log = UsdcTransfers.realRpcLog();
        String upperCaseBlockHash = "0x" + log.blockHash().substring(2).toUpperCase(Locale.ROOT);
        RpcLog removed = new RpcLog(UsdcTransfers.USDC_CHECKSUM_ADDRESS, upperCaseBlockHash,
                log.blockNumber(), log.data(), log.logIndex(), log.topics(), log.transactionHash(), log.transactionIndex(), true);

        EthereumEventMessage message = mapper.toMessage(removed, EventSource.BACKFILL);

        assertThat(message.removed()).isTrue();
        assertThat(message.source()).isEqualTo(EventSource.BACKFILL);
        assertThat(message.contractAddress()).isEqualTo(UsdcTransfers.USDC_ADDRESS);
        assertThat(message.blockHash()).isEqualTo(UsdcTransfers.BLOCK_HASH);
    }

    @Test
    @DisplayName("rejects logs of unknown events and incomplete logs")
    void rejectsUnsupportedOrIncompleteLogs() {
        RpcLog log = UsdcTransfers.realRpcLog();
        RpcLog unknownEvent = new RpcLog(log.address(), log.blockHash(), log.blockNumber(), log.data(), log.logIndex(),
                List.of(UsdcTransfers.randomHash()), log.transactionHash(), log.transactionIndex(), false);
        RpcLog withoutBlockHash = new RpcLog(log.address(), null, log.blockNumber(), log.data(), log.logIndex(),
                log.topics(), log.transactionHash(), log.transactionIndex(), false);

        assertThatThrownBy(() -> mapper.toMessage(unknownEvent, EventSource.SUBSCRIPTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported event signature");
        assertThatThrownBy(() -> mapper.toMessage(withoutBlockHash, EventSource.SUBSCRIPTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blockHash");
    }
}
