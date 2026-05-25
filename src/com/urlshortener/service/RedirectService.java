package com.urlshortener.service;

import com.urlshortener.analytics.AnalyticsService;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.storage.InMemoryStorage;

import java.util.Optional;

/**
 * Resolves short codes to their original URLs and fires async analytics.
 */
public class RedirectService {

    private final InMemoryStorage  storage;
    private final AnalyticsService analyticsService;

    public RedirectService(InMemoryStorage storage, AnalyticsService analyticsService) {
        this.storage          = storage;
        this.analyticsService = analyticsService;
    }

    /**
     * Resolves {@code shortCode} to the original URL.
     *
     * Side-effect: fires an asynchronous click event so the redirect path
     * is not blocked by analytics writes.
     *
     * @return the original URL
     * @throws IllegalArgumentException if the code is unknown or expired
     */
    public String redirect(String shortCode) {
        Optional<UrlMapping> mapping = storage.findByShortCode(shortCode);

        if (mapping.isEmpty()) {
            throw new IllegalArgumentException(
                    "Short code not found or expired: '" + shortCode + "'");
        }

        String originalUrl = mapping.get().getOriginalUrl();

        // Fire-and-forget analytics update
        analyticsService.recordClickAsync(shortCode);

        return originalUrl;
    }
}
