package com.blockchainhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.blockchainhandler.common.ethereum.EthereumAddresses;
import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.storage.entity.TransferEntity;
import com.blockchainhandler.support.AbstractIntegrationTest;
import com.blockchainhandler.testsupport.UsdcTransfers;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end processing: Kafka record → decode → enrich → journal → projection → REST API.
 */
class EventPipelineIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("an event is enriched, journaled, projected and served by the API")
    void processesEventEndToEnd() throws Exception {
        long block = nextBlockNumber();
        Instant blockTimestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        NODE.blockTimestamp(block, blockTimestamp);
        String from = UsdcTransfers.randomAddress();
        String to = UsdcTransfers.randomAddress();
        EthereumEventMessage message = UsdcTransfers.randomTransfer(block)
                .from(from).to(to).value(BigInteger.valueOf(23_270_000L)).build();

        publish(message);

        TransferEntity transfer = awaitTransfers(message.transactionHash(), 1).get(0);
        assertThat(transfer.getFromAddress()).isEqualTo(EthereumAddresses.toChecksum(from));
        assertThat(transfer.getToAddress()).isEqualTo(EthereumAddresses.toChecksum(to));
        assertThat(transfer.getContractAddress()).isEqualTo(UsdcTransfers.USDC_CHECKSUM_ADDRESS);
        assertThat(transfer.getValueUsdc()).isEqualByComparingTo("23.27");
        assertThat(transfer.getValueRaw()).isEqualByComparingTo("23270000");
        assertThat(transfer.getBlockTimestamp()).isEqualTo(blockTimestamp);
        assertThat(transfer.getBlockNumber()).isEqualTo(block);

        Map<String, Object> journal = jdbcTemplate.queryForMap(
                "SELECT processed, retry_count, event_type, payload::text AS payload FROM ethereum_events WHERE tx_hash = ?",
                message.transactionHash());
        assertThat(journal.get("processed")).isEqualTo(true);
        assertThat(journal.get("retry_count")).isEqualTo(0);
        assertThat(journal.get("event_type")).isEqualTo("TRANSFER");
        assertThat((String) journal.get("payload")).contains(EthereumAddresses.toChecksum(from), "23.270000");

        ResponseEntity<JsonNode> page = restTemplate.getForEntity(
                "/api/v1/transfers?address={address}", JsonNode.class, from);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody().path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.getBody().path("content").get(0).path("value").asText()).isEqualTo("23.270000");

        ResponseEntity<JsonNode> details = restTemplate.getForEntity(
                "/api/v1/transfers/{txHash}", JsonNode.class, message.transactionHash());
        assertThat(details.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(details.getBody().path("transferCount").asInt()).isEqualTo(1);
        assertThat(details.getBody().path("blockTimestamp").asText()).isEqualTo(blockTimestamp.toString());
    }

    @Test
    @DisplayName("a redelivered event is stored only once")
    void redeliveryIsIdempotent() throws Exception {
        long block = nextBlockNumber();
        NODE.blockTimestamp(block, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        EthereumEventMessage message = UsdcTransfers.randomTransfer(block).build();
        EthereumEventMessage marker = UsdcTransfers.randomTransfer(block).transactionHash(message.transactionHash())
                .blockHash(message.blockHash()).logIndex(message.logIndex() + 1).build();

        publish(message);
        publish(message);
        publish(marker);

        // Records of one transaction share a partition and are processed in order: once the marker is stored,
        // the duplicate has been consumed as well.
        awaitTransfers(message.transactionHash(), 2);
        assertThat(journalRows(message.transactionHash())).isEqualTo(2);
    }

    @Test
    @DisplayName("the block timestamp is fetched from the node once per block thanks to the Redis cache")
    void blockTimestampIsCached() throws Exception {
        long block = nextBlockNumber();
        NODE.blockTimestamp(block, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        EthereumEventMessage first = UsdcTransfers.randomTransfer(block).build();
        EthereumEventMessage second = UsdcTransfers.randomTransfer(block).transactionHash(first.transactionHash())
                .blockHash(first.blockHash()).logIndex(first.logIndex() + 1).build();

        publish(first);
        publish(second);

        awaitTransfers(first.transactionHash(), 2);
        assertThat(NODE.requestsFor(block)).isEqualTo(1);
    }

    @Test
    @DisplayName("a new transfer invalidates the cached transfer list of its addresses")
    void newTransferInvalidatesCachedList() throws Exception {
        long block = nextBlockNumber();
        NODE.blockTimestamp(block, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String address = UsdcTransfers.randomAddress();
        EthereumEventMessage incoming = UsdcTransfers.randomTransfer(block).to(address).build();
        publish(incoming);
        awaitTransfers(incoming.transactionHash(), 1);
        assertThat(totalTransfersOf(address)).isEqualTo(1);
        assertThat(totalTransfersOf(address)).as("served from the cache").isEqualTo(1);

        EthereumEventMessage outgoing = UsdcTransfers.randomTransfer(block).from(address).build();
        publish(outgoing);
        awaitTransfers(outgoing.transactionHash(), 1);

        // The cached page would live for 60 s; the new generation makes it unreachable immediately.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(totalTransfersOf(address)).isEqualTo(2));
    }

    @Test
    @DisplayName("a log removed by a chain reorganization is compensated")
    void reorgRemovalDeletesTransfer() throws Exception {
        long block = nextBlockNumber();
        NODE.blockTimestamp(block, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        EthereumEventMessage transfer = UsdcTransfers.randomTransfer(block).build();
        publish(transfer);
        awaitTransfers(transfer.transactionHash(), 1);

        publish(UsdcTransfers.randomTransfer(block)
                .transactionHash(transfer.transactionHash())
                .blockHash(transfer.blockHash())
                .logIndex(transfer.logIndex())
                .topics(transfer.topics())
                .data(transfer.data())
                .removed(true)
                .build());

        await().untilAsserted(() -> assertThat(transferRepository.findByTxHashOrderByLogIndexAsc(transfer.transactionHash())).isEmpty());
        List<Map<String, Object>> journal = jdbcTemplate.queryForList(
                "SELECT removed, processed FROM ethereum_events WHERE tx_hash = ? ORDER BY removed", transfer.transactionHash());
        assertThat(journal).extracting(row -> row.get("removed")).containsExactly(false, true);
        assertThat(journal).allSatisfy(row -> assertThat(row.get("processed")).isEqualTo(true));
    }

    private long totalTransfersOf(String address) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/v1/transfers?address={address}", JsonNode.class, address);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("totalElements").asLong();
    }
}
