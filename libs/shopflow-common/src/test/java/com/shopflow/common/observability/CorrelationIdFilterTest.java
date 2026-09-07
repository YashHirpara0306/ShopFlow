package com.shopflow.common.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanMdc() {
        CorrelationId.clear();
    }

    @Test
    void generatesIdWhenAbsentAndEchoesItOnTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    }

    @Test
    void propagatesASafeInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        request.addHeader(CorrelationId.HEADER, "abcd1234efgh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("abcd1234efgh");
    }

    @Test
    void replacesAnUnsafeInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        request.addHeader(CorrelationId.HEADER, "not a valid id!!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(CorrelationId.HEADER);
        assertThat(id).doesNotContain(" ");
        assertThat(id).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void bindsTheIdInTheMdcForTheDurationOfTheChainThenClearsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInsideChain = new String[1];
        FilterChain chain = (req, res) -> seenInsideChain[0] = CorrelationId.current();

        filter.doFilter(request, response, chain);

        assertThat(seenInsideChain[0]).isNotBlank();
        assertThat(CorrelationId.current()).isNull(); // cleared in finally
    }
}
