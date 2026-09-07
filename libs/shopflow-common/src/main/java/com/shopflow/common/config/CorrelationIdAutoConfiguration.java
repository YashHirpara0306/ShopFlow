package com.shopflow.common.config;

import com.shopflow.common.observability.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registers {@link CorrelationIdFilter} in every servlet-based ShopFlow service automatically -
 * a service only needs a dependency on {@code shopflow-common}, no {@code @Import} or component scan.
 *
 * <p>Boot auto-registers any {@code Filter} bean as a servlet filter and honours its {@code Ordered}
 * value, so the filter runs before Spring Security and MVC.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
public class CorrelationIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter shopflowCorrelationIdFilter() {
        return new CorrelationIdFilter();
    }
}
