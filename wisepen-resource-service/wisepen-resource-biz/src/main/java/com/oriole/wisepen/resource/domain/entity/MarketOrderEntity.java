package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.domain.base.MarketOrderBase;
import com.oriole.wisepen.resource.enums.MarketOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_market_orders")
@CompoundIndex(name = "idx_market_order_trace", def = "{'traceId': 1}", unique = true)
public class MarketOrderEntity extends MarketOrderBase {
    @Id
    private String orderId;

    private String purchasedOfferId;
    private MarketOrderStatus status;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
