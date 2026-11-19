package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.MarketOrderEntity;
import com.oriole.wisepen.resource.enums.MarketOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketOrderRepository extends MongoRepository<MarketOrderEntity, String> {

    Optional<MarketOrderEntity> findByTraceId(String traceId);

    List<MarketOrderEntity> findTop100ByStatusOrderByUpdateTimeAsc(MarketOrderStatus status);

    @Query("{ '_id': ?0, 'status': ?1 }")
    @Update("{ '$set': { 'status': ?2, 'updateTime': ?3 } }")
    long updateStatusIfCurrent(String orderId, MarketOrderStatus currentStatus,
                               MarketOrderStatus targetStatus, LocalDateTime updateTime);

    @Query("{ '_id': ?0, 'status': ?1 }")
    @Update("{ '$set': { 'updateTime': ?2 } }")
    long deferPendingRetry(String orderId, MarketOrderStatus status, LocalDateTime updateTime);

    @Query("{ 'buyerId': ?0, '$or': [ { 'status': ?1 }, { 'status': null } ] }")
    Page<MarketOrderEntity> findCompletedOrLegacyByBuyerId(
            String buyerId, MarketOrderStatus status, Pageable pageable);

    @Query("{ '$or': [ { 'status': ?0 }, { 'status': null } ] }")
    Page<MarketOrderEntity> findCompletedOrLegacy(MarketOrderStatus status, Pageable pageable);
}
