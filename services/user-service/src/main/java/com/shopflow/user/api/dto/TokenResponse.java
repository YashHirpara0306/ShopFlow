package com.shopflow.user.api.dto;

/**
 * Returned by login and refresh.
 *
 * @param accessToken      short-lived JWT for the {@code Authorization: Bearer} header
 * @param refreshToken     long-lived opaque token; the client stores it and sends it to {@code /api/auth/refresh}
 * @param tokenType        always {@code "Bearer"}
 * @param expiresInSeconds access-token lifetime in seconds
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
