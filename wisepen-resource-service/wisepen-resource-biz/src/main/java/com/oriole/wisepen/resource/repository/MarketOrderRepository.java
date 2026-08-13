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

    Page<MarketOrderEntity> findByBuyerIdAndStatus(
            String buyerId, MarketOrderStatus status, Pageable pageable);
}
