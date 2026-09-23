package com.blockchainhandler.processing.decoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blockchainhandler.processing.exception.EventDecodingException;
import com.blockchainhandler.testsupport.UsdcTransfers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferEventDecoderTest {

    private final TransferEventDecoder decoder = new TransferEventDecoder();

    @Test
    @DisplayName("decodes a real USDC Transfer log")
    void decodesRealTransfer() {
        DecodedTransfer transfer = decoder.decode(UsdcTransfers.realTransfer().build());

        assertThat(transfer.from()).isEqualTo(UsdcTransfers.FROM);
        assertThat(transfer.to()).isEqualTo(UsdcTransfers.TO);
        assertThat(transfer.value()).isEqualTo(UsdcTransfers.RAW_VALUE);
    }

    @Test
    @DisplayName("rejects ERC-721 transfers, which index the token id as a fourth topic")
    void rejectsErc721Transfer() {
        List<String> erc721Topics = List.of(UsdcTransfers.TRANSFER_TOPIC, UsdcTransfers.topic(UsdcTransfers.FROM),
                UsdcTransfers.topic(UsdcTransfers.TO), UsdcTransfers.randomHash());

        assertThatThrownBy(() -> decoder.decode(UsdcTransfers.realTransfer().topics(erc721Topics).data("0x").build()))
                .isInstanceOf(EventDecodingException.class)
                .hasMessageContaining("3 topics");
    }

    @Test
    @DisplayName("rejects logs with another event signature")
    void rejectsUnexpectedSignature() {
        List<String> topics = List.of(UsdcTransfers.randomHash(), UsdcTransfers.topic(UsdcTransfers.FROM),
                UsdcTransfers.topic(UsdcTransfers.TO));

        assertThatThrownBy(() -> decoder.decode(UsdcTransfers.realTransfer().topics(topics).build()))
                .isInstanceOf(EventDecodingException.class)
                .hasMessageContaining("Unexpected event signature");
    }

    @Test
    @DisplayName("rejects data that is not a single 32-byte word")
    void rejectsMalformedData() {
        assertThatThrownBy(() -> decoder.decode(UsdcTransfers.realTransfer().data("0x01").build()))
                .isInstanceOf(EventDecodingException.class)
                .hasMessageContaining("32-byte word");
    }
}
