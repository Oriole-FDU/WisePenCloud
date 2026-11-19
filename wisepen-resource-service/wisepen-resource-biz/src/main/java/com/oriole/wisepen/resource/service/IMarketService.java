package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleAuditRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketPurchaseRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleOffShelfRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSalePublishRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleVersionCheckRequest;
import com.oriole.wisepen.resource.domain.dto.res.MarketOrderResponse;

public interface IMarketService {

    void publishSaleInfo(MarketSalePublishRequest request);

    void offShelfSaleInfo(MarketSaleOffShelfRequest request);

    void auditSaleInfo(MarketSaleAuditRequest request, String operatorId);

    void assertMarketSaleVersion(MarketSaleVersionCheckRequest request);

    MarketOrderResponse purchaseResource(MarketPurchaseRequest request, String buyerId);

    void recoverPendingOrder(String orderId);

    /** buyerId 为空时查询全部订单；仅管理员入口可以不传 buyerId。 */
    PageR<MarketOrderResponse> listOrders(String buyerId, int page, int size);
}
