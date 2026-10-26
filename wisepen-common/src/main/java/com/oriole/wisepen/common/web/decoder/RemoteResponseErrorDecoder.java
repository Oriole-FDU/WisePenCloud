package com.oriole.wisepen.common.web.decoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.exception.ServiceException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class RemoteResponseErrorDecoder implements ErrorDecoder {

    private static final ErrorDecoder DEFAULT_DECODER = new ErrorDecoder.Default();

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = readErrorMessage(methodKey, response);
        if (StringUtils.hasText(message)) {
            IResult errorResult = response.status() >= 400 && response.status() < 500
                    ? CommonError.REQUEST_ERROR
                    : CommonError.INTERNAL_ERROR;
            return new ServiceException(errorResult, message);
        }
        return DEFAULT_DECODER.decode(methodKey, response);
    }

    private String readErrorMessage(String methodKey, Response response) {
        if (response.body() == null) {
            return null;
        }

        try {
            JsonNode body = objectMapper.readTree(response.body().asInputStream());
            return body == null ? null : body.path("msg").asText(null);
        } catch (IOException e) {
            log.warn("remote error response parse failed. remoteCall={} status={}",
                    methodKey, response.status(), e);
            return null;
        }
    }
}
