package com.urlshortener.model;

import java.time.Instant;

/**
 * Represents the mapping between a short code and the original URL,
 * along with metadata such as expiry and creation time.
 */
public class UrlMapping {

    private final String shortCode;
    private final String originalUrl;
    private final Instant createdAt;
    private final Instant expiresAt;   // null = never expires

    public UrlMapping(String shortCode, String originalUrl) {
        this(shortCode, originalUrl, null);
    }

    public UrlMapping(String shortCode, String originalUrl, Instant expiresAt) {
        this.shortCode   = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt   = Instant.now();
        this.expiresAt   = expiresAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String  getShortCode()   { return shortCode; }
    public String  getOriginalUrl() { return originalUrl; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getExpiresAt()   { return expiresAt; }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return String.format("UrlMapping{short='%s', original='%s', created=%s, expires=%s}",
                shortCode, originalUrl, createdAt,
                expiresAt == null ? "never" : expiresAt.toString());
    }
}
