package com.shopflow.user;

import com.shopflow.common.observability.CorrelationIdFilter;
import com.shopflow.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that depending on {@code shopflow-common} is enough to get the shared cross-cutting
 * behaviour: the correlation-id filter and the global exception handler are auto-registered,
 * with no {@code @Import} or component scan in this service. If this fails, the auto-configuration
 * imports file or a {@code @Conditional} is wrong.
 */
@SpringBootTest
class SharedConventionsWiringTest {

    @Autowired
    ApplicationContext context;

    @Test
    void correlationIdFilterIsAutoConfigured() {
        assertThat(context.getBeansOfType(CorrelationIdFilter.class)).isNotEmpty();
    }

    @Test
    void globalExceptionHandlerIsAutoConfigured() {
        assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).isNotEmpty();
    }
}
