package com.blockchainhandler.ingestion;

import com.blockchainhandler.common.ethereum.Erc20Events;
import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.common.messaging.EventSource;
import com.blockchainhandler.common.messaging.EventType;
import com.blockchainhandler.ingestion.client.RpcLog;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.web3j.utils.Numeric;

/**
 * Converts JSON-RPC logs into Kafka messages: hex quantities become numbers, hashes and addresses are
 * lower-cased and the event type is resolved from {@code topic0}.
 */
public class LogMessageMapper {

    private final long chainId;
    private final Clock clock;

    /**
     * Creates the mapper.
     *
     * @param chainId EIP-155 chain id stamped on every message
     * @param clock   clock used for {@code observedAt}
     */
    public LogMessageMapper(long chainId, Clock clock) {
        this.chainId = chainId;
        this.clock = clock;
    }

    /**
     * Maps a log to a message.
     *
     * @param log    log received from the node
     * @param source how the log was obtained
     * @return Kafka message
     * @throws IllegalArgumentException if the log is incomplete, malformed or of an unknown event type
     */
    public EthereumEventMessage toMessage(RpcLog log, EventSource source) {
        List<String> topics = Objects.requireNonNull(log.topics(), "Log has no topics");
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("Log has no topics");
        }
        return new EthereumEventMessage(
                EthereumEventMessage.CURRENT_SCHEMA_VERSION,
                resolveEventType(topics.get(0)),
                chainId,
                lowerCase(log.address(), "address"),
                lowerCase(log.transactionHash(), "transactionHash"),
                quantity(log.transactionIndex(), "transactionIndex"),
                lowerCase(log.blockHash(), "blockHash"),
                quantity(log.blockNumber(), "blockNumber"),
                Math.toIntExact(quantity(log.logIndex(), "logIndex")),
                topics.stream().map(topic -> lowerCase(topic, "topic")).toList(),
                lowerCase(log.data(), "data"),
                log.removed(),
                source,
                clock.instant());
    }

    private static EventType resolveEventType(String topic0) {
        if (Erc20Events.TRANSFER_TOPIC.equalsIgnoreCase(topic0)) {
            return EventType.TRANSFER;
        }
        throw new IllegalArgumentException("Unsupported event signature " + topic0);
    }

    private static long quantity(String hexQuantity, String field) {
        if (hexQuantity == null) {
            throw new IllegalArgumentException("Log field '" + field + "' is missing");
        }
        return Numeric.decodeQuantity(hexQuantity).longValueExact();
    }

    private static String lowerCase(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Log field '" + field + "' is missing");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
