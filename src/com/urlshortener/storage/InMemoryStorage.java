package com.urlshortener.storage;

import com.urlshortener.model.UrlMapping;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory store for URL mappings.
 *
 * Two indices are maintained:
 *   shortCode  → UrlMapping   (primary lookup for redirects)
 *   originalUrl → shortCode   (deduplication on submission)
 */
public class InMemoryStorage {

    // shortCode → UrlMapping
    private final ConcurrentHashMap<String, UrlMapping> byShortCode =
            new ConcurrentHashMap<>();

    // originalUrl → shortCode  (first wins — dedup)
    private final ConcurrentHashMap<String, String> byOriginalUrl =
            new ConcurrentHashMap<>();

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Stores the mapping atomically.
     * If the short code already exists, the call is a no-op and returns false.
     */
    public boolean store(UrlMapping mapping) {
        UrlMapping existing = byShortCode.putIfAbsent(mapping.getShortCode(), mapping);
        if (existing != null) return false;                    // code collision
        byOriginalUrl.putIfAbsent(mapping.getOriginalUrl(), mapping.getShortCode());
        return true;
    }

    /** Removes a mapping by short code. Returns the removed mapping, or empty. */
    public Optional<UrlMapping> remove(String shortCode) {
        UrlMapping m = byShortCode.remove(shortCode);
        if (m != null) byOriginalUrl.remove(m.getOriginalUrl());
        return Optional.ofNullable(m);
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /** Look up the mapping for a given short code. Respects expiry. */
    public Optional<UrlMapping> findByShortCode(String shortCode) {
        UrlMapping m = byShortCode.get(shortCode);
        if (m == null) return Optional.empty();
        if (m.isExpired()) {
            remove(shortCode);      // lazy eviction
            return Optional.empty();
        }
        return Optional.of(m);
    }

    /** Returns the short code already registered for this URL, if any. */
    public Optional<String> findShortCodeByOriginalUrl(String originalUrl) {
        String code = byOriginalUrl.get(originalUrl);
        if (code == null) return Optional.empty();
        // Confirm the forward index still exists (may have been evicted)
        if (!byShortCode.containsKey(code)) {
            byOriginalUrl.remove(originalUrl);
            return Optional.empty();
        }
        return Optional.of(code);
    }

    /** Returns true if a short code already exists in the store. */
    public boolean existsByShortCode(String shortCode) {
        return byShortCode.containsKey(shortCode);
    }

    /** Snapshot of all current mappings (for listing / persistence). */
    public Collection<UrlMapping> allMappings() {
        return byShortCode.values();
    }

    /** Total number of active mappings. */
    public int size() {
        return byShortCode.size();
    }
}
