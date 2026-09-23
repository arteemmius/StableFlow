package com.blockchainhandler.processing.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.config.properties.UsdcProperties;
import com.blockchainhandler.processing.decoder.DecodedTransfer;
import com.blockchainhandler.processing.decoder.TransferEventDecoder;
import com.blockchainhandler.processing.exception.BlockTimestampUnavailableException;
import com.blockchainhandler.processing.exception.UnsupportedEventException;
import com.blockchainhandler.testsupport.UsdcTransfers;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventEnrichmentServiceTest {

    private static final String BINANCE_14 = "0x28C6c06298d514Db089934071355E5743bf21d60";
    private static final String VITALIK = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    @Mock
    private BlockTimestampProvider blockTimestampProvider;

    private final TransferEventDecoder decoder = new TransferEventDecoder();
    private EventEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new EventEnrichmentService(blockTimestampProvider, new UsdcProperties(UsdcTransfers.USDC_ADDRESS, 6));
    }

    @Test
    @DisplayName("adds the block timestamp, checksums addresses and converts the amount to USDC")
    void enrichesTransfer() {
        EthereumEventMessage message = UsdcTransfers.realTransfer()
                .from(BINANCE_14.toLowerCase(Locale.ROOT))
                .to(VITALIK.toLowerCase(Locale.ROOT))
                .build();
        given(blockTimestampProvider.getBlockTimestamp(UsdcTransfers.BLOCK_NUMBER)).willReturn(UsdcTransfers.BLOCK_TIMESTAMP);

        EnrichedTransfer transfer = service.enrich(message, decoder.decode(message));

        assertThat(transfer.blockTimestamp()).isEqualTo(UsdcTransfers.BLOCK_TIMESTAMP);
        assertThat(transfer.fromAddress()).isEqualTo(BINANCE_14);
        assertThat(transfer.toAddress()).isEqualTo(VITALIK);
        assertThat(transfer.contractAddress()).isEqualTo(UsdcTransfers.USDC_CHECKSUM_ADDRESS);
        assertThat(transfer.rawValue()).isEqualTo(UsdcTransfers.RAW_VALUE);
        assertThat(transfer.value()).hasToString("23.270000");
        assertThat(transfer.decimals()).isEqualTo(6);
        assertThat(transfer.transactionHash()).isEqualTo(UsdcTransfers.TX_HASH);
        assertThat(transfer.logIndex()).isEqualTo(UsdcTransfers.LOG_INDEX);
    }

    @Test
    @DisplayName("rejects transfers of contracts that are not tracked without calling the node")
    void rejectsUnknownContract() {
        EthereumEventMessage message = UsdcTransfers.realTransfer().contractAddress(UsdcTransfers.randomAddress()).build();
        DecodedTransfer decoded = decoder.decode(message);

        assertThatThrownBy(() -> service.enrich(message, decoded)).isInstanceOf(UnsupportedEventException.class);
        verifyNoInteractions(blockTimestampProvider);
    }

    @Test
    @DisplayName("propagates node failures so that Kafka retries the event")
    void propagatesTransientNodeFailures() {
        EthereumEventMessage message = UsdcTransfers.realTransfer().build();
        given(blockTimestampProvider.getBlockTimestamp(UsdcTransfers.BLOCK_NUMBER))
                .willThrow(new BlockTimestampUnavailableException("node is down"));

        assertThatThrownBy(() -> service.enrich(message, decoder.decode(message)))
                .isInstanceOf(BlockTimestampUnavailableException.class);
    }
}
