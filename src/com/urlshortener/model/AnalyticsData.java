package com.urlshortener.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds all analytics data for a single short URL.
 * All fields are thread-safe; no external synchronisation required.
 */
public class AnalyticsData {

    private final String shortCode;
    private final Instant createdAt;

    private final AtomicLong totalClicks = new AtomicLong(0);
    private volatile Instant lastAccessedAt;

    /** Per-day click counts keyed by ISO date string (yyyy-MM-dd). */
    private final ConcurrentHashMap<String, AtomicLong> dailyClicks = new ConcurrentHashMap<>();

    public AnalyticsData(String shortCode) {
        this.shortCode    = shortCode;
        this.createdAt    = Instant.now();
        this.lastAccessedAt = null;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /** Record one click; returns the new total. */
    public long recordClick() {
        lastAccessedAt = Instant.now();
        String today = LocalDate.now().toString();
        dailyClicks.computeIfAbsent(today, k -> new AtomicLong(0)).incrementAndGet();
        return totalClicks.incrementAndGet();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String  getShortCode()      { return shortCode; }
    public Instant getCreatedAt()      { return createdAt; }
    public long    getTotalClicks()    { return totalClicks.get(); }
    public Instant getLastAccessedAt() { return lastAccessedAt; }

    public ConcurrentHashMap<String, AtomicLong> getDailyClicks() {
        return dailyClicks;
    }

    @Override
    public String toString() {
        return String.format(
                "Analytics{code='%s', clicks=%d, lastAccessed=%s}",
                shortCode, totalClicks.get(),
                lastAccessedAt == null ? "never" : lastAccessedAt.toString());
    }
}
