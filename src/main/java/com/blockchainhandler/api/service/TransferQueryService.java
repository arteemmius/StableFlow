package com.blockchainhandler.api.service;

import com.blockchainhandler.api.dto.CursorPageResponse;
import com.blockchainhandler.api.dto.PageResponse;
import com.blockchainhandler.api.dto.TransactionDetailsResponse;
import com.blockchainhandler.api.dto.TransferResponse;
import com.blockchainhandler.api.error.ResourceNotFoundException;
import com.blockchainhandler.api.mapper.TransferMapper;
import com.blockchainhandler.cache.AddressScopedKeyGenerator;
import com.blockchainhandler.cache.CacheNames;
import com.blockchainhandler.storage.entity.TransferEntity;
import com.blockchainhandler.storage.repository.TransferRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side queries over stored transfers. Transfer lists of an address and transaction details are cached in Redis.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransferQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("blockTimestamp"), Sort.Order.desc("logIndex"));

    private final TransferRepository repository;
    private final TransferMapper mapper;

    /**
     * Returns a page of transfers where the address is the sender or the receiver, newest first.
     *
     * <p>Cached for {@code app.cache.ttl.transfers}; a new transfer of the address invalidates all cached pages
     * of that address through its cache generation.
     *
     * @param criteria normalized search criteria
     * @return page of transfers
     */
    @Cacheable(cacheNames = CacheNames.TRANSFERS_BY_ADDRESS, keyGenerator = AddressScopedKeyGenerator.BEAN_NAME)
    public PageResponse<TransferResponse> findTransfers(TransferSearchCriteria criteria) {
        PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), NEWEST_FIRST);
        return PageResponse.of(repository
                .findByAddress(criteria.address(), criteria.from(), criteria.to(), pageRequest)
                .map(mapper::toResponse));
    }

    /**
     * Returns a page of the latest transfers of all addresses, newest first, using keyset pagination.
     *
     * <p>Not cached: the first page changes with every new transfer, which would keep the hit rate near zero, while
     * any page is a range scan of {@code size + 1} index entries.
     *
     * @param criteria normalized feed criteria
     * @return page of transfers with the cursor of the next page
     */
    public CursorPageResponse<TransferResponse> findLatest(TransferFeedCriteria criteria) {
        TransferCursor before = criteria.before();
        // One row more than requested tells whether a next page exists without counting.
        List<TransferEntity> rows = repository.findLatest(
                before.blockTimestamp(), before.logIndex(), before.id(), criteria.from(), criteria.size() + 1);
        boolean hasNext = rows.size() > criteria.size();
        List<TransferEntity> page = hasNext ? rows.subList(0, criteria.size()) : rows;
        String nextCursor = hasNext ? TransferCursor.of(page.getLast()).encode() : null;
        return new CursorPageResponse<>(mapper.toResponses(page), criteria.size(), hasNext, nextCursor);
    }

    /**
     * Returns a transaction with all USDC transfers it emitted.
     *
     * @param txHash lower-case transaction hash
     * @return transaction details
     * @throws ResourceNotFoundException if no transfer of the transaction is stored
     */
    @Cacheable(cacheNames = CacheNames.TRANSACTION_DETAILS, key = "#txHash")
    public TransactionDetailsResponse getTransaction(String txHash) {
        List<TransferEntity> transfers = repository.findByTxHashOrderByLogIndexAsc(txHash);
        if (transfers.isEmpty()) {
            throw new ResourceNotFoundException("No USDC transfers found for transaction " + txHash);
        }
        TransferEntity first = transfers.get(0);
        BigDecimal totalValue = transfers.stream()
                .map(TransferEntity::getValueUsdc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TransactionDetailsResponse(
                txHash,
                first.getBlockNumber(),
                first.getBlockHash(),
                first.getBlockTimestamp(),
                transfers.size(),
                totalValue,
                mapper.toResponses(transfers));
    }
}
