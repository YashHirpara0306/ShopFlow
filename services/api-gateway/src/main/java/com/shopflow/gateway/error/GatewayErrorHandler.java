package com.shopflow.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.error.ErrorCode;
import com.shopflow.common.error.ErrorResponse;
import com.shopflow.common.observability.CorrelationId;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Turns gateway-level failures into the standard {@link ErrorResponse} body: a downed downstream
 * (connection refused / timeout) -&gt; 503, a rate-limit rejection -&gt; 429, anything else -&gt; 500.
 * Ordered before the default handler.
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        ErrorCode code;
        String message;
        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                code = ErrorCode.RATE_LIMITED;
                message = "Too many requests - slow down";
            } else if (status.is5xxServerError()) {
                code = ErrorCode.DOWNSTREAM_UNAVAILABLE;
                message = "The requested service is temporarily unavailable";
            } else {
                code = ErrorCode.VALIDATION_ERROR;
                message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
            }
        } else if (ex instanceof java.net.ConnectException || ex instanceof java.util.concurrent.TimeoutException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = ErrorCode.DOWNSTREAM_UNAVAILABLE;
            message = "The requested service is temporarily unavailable";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = ErrorCode.INTERNAL_ERROR;
            message = "An unexpected error occurred";
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorResponse body = ErrorResponse.of(status.value(), code, message,
                exchange.getRequest().getPath().value(),
                exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER));
        try {
            DataBuffer buffer = exchange.getResponse().bufferFactory()
                    .wrap(objectMapper.writeValueAsBytes(body));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }
}
