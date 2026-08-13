package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleAuditRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSalePublishRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleOffShelfRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketPurchaseRequest;
import com.oriole.wisepen.resource.domain.dto.res.MarketOrderResponse;

public interface IMarketService {

    void publishSaleInfo(MarketSalePublishRequest request);

    void offShelfSaleInfo(MarketSaleOffShelfRequest request);

    void auditSaleInfo(MarketSaleAuditRequest request, String operatorId);

    MarketOrderResponse purchaseResource(MarketPurchaseRequest request, String buyerId);

    void recoverPendingOrder(String orderId);

    PageR<MarketOrderResponse> listOrders(String buyerId, int page, int size);
}
