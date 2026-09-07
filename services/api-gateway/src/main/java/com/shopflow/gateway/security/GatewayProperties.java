package com.shopflow.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** {@code shopflow.gateway.*} - routes reachable without a valid token, as {@code "METHOD /ant/pattern"}. */
@ConfigurationProperties(prefix = "shopflow.gateway")
public class GatewayProperties {

    private List<String> publicPaths = List.of();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
