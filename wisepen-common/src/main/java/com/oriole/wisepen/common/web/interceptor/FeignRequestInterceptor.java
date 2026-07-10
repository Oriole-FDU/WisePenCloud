package com.oriole.wisepen.common.web.interceptor;

import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.gray.GrayFacade;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class FeignRequestInterceptor implements RequestInterceptor {

    private final String fromSource;

    public FeignRequestInterceptor(String fromSource) {
        this.fromSource = fromSource;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(SecurityConstants.HEADER_FROM_SOURCE, fromSource);
        GrayFacade.applyOutboundDeveloperHeader(template);
    }
}
