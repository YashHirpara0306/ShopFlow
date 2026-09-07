package com.shopflow.common.config;

import com.shopflow.common.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Registers {@link GlobalExceptionHandler} so every servlet service returns the same
 * {@code ErrorResponse} body without copying advice code. A service can still declare its own
 * {@code @RestControllerAdvice} for domain-specific exceptions - both apply.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestControllerAdvice.class)
public class WebErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler shopflowGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
