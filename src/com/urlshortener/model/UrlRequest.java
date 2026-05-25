package com.urlshortener.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a URL shortening request pushed into the processing queue.
 */
public class UrlRequest {

    private final String requestId;
    private final String originalUrl;
    private final String customAlias;   // optional
    private final Instant submittedAt;
    private volatile RequestStatus status;
    private volatile String shortCode;   // populated after processing

    public enum RequestStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    public UrlRequest(String originalUrl) {
        this(originalUrl, null);
    }

    public UrlRequest(String originalUrl, String customAlias) {
        this.requestId   = UUID.randomUUID().toString();
        this.originalUrl = originalUrl;
        this.customAlias = customAlias;
        this.submittedAt = Instant.now();
        this.status      = RequestStatus.PENDING;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String  getRequestId()   { return requestId; }
    public String  getOriginalUrl() { return originalUrl; }
    public String  getCustomAlias() { return customAlias; }
    public Instant getSubmittedAt() { return submittedAt; }

    public synchronized RequestStatus getStatus()    { return status; }
    public synchronized String        getShortCode() { return shortCode; }

    // ── Setters (thread-safe) ─────────────────────────────────────────────────

    public synchronized void setStatus(RequestStatus status) {
        this.status = status;
    }

    public synchronized void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    @Override
    public String toString() {
        return String.format("UrlRequest{id='%s', url='%s', alias='%s', status=%s}",
                requestId, originalUrl, customAlias, status);
    }
}
