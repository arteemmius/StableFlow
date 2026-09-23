package com.blockchainhandler.processing.decoder;

import com.blockchainhandler.common.ethereum.Erc20Events;
import com.blockchainhandler.common.messaging.EthereumEventMessage;
import com.blockchainhandler.common.messaging.EventType;
import com.blockchainhandler.processing.exception.EventDecodingException;
import com.blockchainhandler.processing.exception.UnsupportedEventException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

/**
 * Decodes ERC-20 {@code Transfer} logs using the web3j ABI decoder.
 *
 * <p>ERC-721 uses the same event signature but indexes the token id as a third topic, so the topic count and
 * the data length are validated before decoding.
 */
@Component
public class TransferEventDecoder {

    private static final int TRANSFER_TOPIC_COUNT = 3;
    private static final int WORD_HEX_LENGTH = 64;

    /**
     * Decodes the {@code from}, {@code to} and {@code value} parameters of a Transfer log.
     *
     * @param message raw log
     * @return decoded parameters
     * @throws UnsupportedEventException if the message is not a Transfer event
     * @throws EventDecodingException    if the log does not match the ERC-20 Transfer ABI
     */
    public DecodedTransfer decode(EthereumEventMessage message) {
        if (message.eventType() != EventType.TRANSFER) {
            throw new UnsupportedEventException("Unsupported event type " + message.eventType());
        }
        List<String> topics = message.topics();
        if (topics.size() != TRANSFER_TOPIC_COUNT) {
            throw new EventDecodingException(
                    "ERC-20 Transfer log must have " + TRANSFER_TOPIC_COUNT + " topics, got " + topics.size());
        }
        if (!Erc20Events.TRANSFER_TOPIC.equalsIgnoreCase(topics.get(0))) {
            throw new EventDecodingException("Unexpected event signature " + topics.get(0));
        }
        if (Numeric.cleanHexPrefix(message.data()).length() != WORD_HEX_LENGTH) {
            throw new EventDecodingException("ERC-20 Transfer data must be a single 32-byte word");
        }
        try {
            List<TypeReference<Type>> indexed = Erc20Events.TRANSFER.getIndexedParameters();
            Address from = (Address) FunctionReturnDecoder.decodeIndexedValue(topics.get(1), indexed.get(0));
            Address to = (Address) FunctionReturnDecoder.decodeIndexedValue(topics.get(2), indexed.get(1));
            List<Type> values = FunctionReturnDecoder.decode(message.data(), Erc20Events.TRANSFER.getNonIndexedParameters());
            BigInteger value = ((Uint256) values.get(0)).getValue();
            return new DecodedTransfer(from.getValue(), to.getValue(), value);
        } catch (RuntimeException e) {
            throw new EventDecodingException("Malformed ERC-20 Transfer log: " + e.getMessage(), e);
        }
    }
}
