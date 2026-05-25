package com.urlshortener.worker;

import com.urlshortener.analytics.AnalyticsService;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.model.UrlRequest;
import com.urlshortener.model.UrlRequest.RequestStatus;
import com.urlshortener.queue.UrlRequestQueue;
import com.urlshortener.storage.InMemoryStorage;
import com.urlshortener.util.Base62Encoder;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker (consumer) that continuously polls the {@link UrlRequestQueue}
 * and processes each {@link UrlRequest}.
 *
 * One instance per thread — run via an {@code ExecutorService}.
 */
public class UrlProcessorWorker implements Runnable {

    private static final Logger LOG =
            Logger.getLogger(UrlProcessorWorker.class.getName());

    private static final long POLL_TIMEOUT_MS = 500;

    private final String           workerId;
    private final UrlRequestQueue  queue;
    private final InMemoryStorage  storage;
    private final AnalyticsService analyticsService;

    /** Set to false to stop the worker loop gracefully. */
    private volatile boolean running = true;

    public UrlProcessorWorker(String workerId,
                              UrlRequestQueue queue,
                              InMemoryStorage storage,
                              AnalyticsService analyticsService) {
        this.workerId         = workerId;
        this.queue            = queue;
        this.storage          = storage;
        this.analyticsService = analyticsService;
    }

    // ── Runnable ──────────────────────────────────────────────────────────────

    @Override
    public void run() {
        LOG.info("[" + workerId + "] started.");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                UrlRequest request = queue.poll(POLL_TIMEOUT_MS);
                if (request == null) continue;   // timeout — loop again

                processRequest(request);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.info("[" + workerId + "] interrupted — shutting down.");
                break;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[" + workerId + "] unexpected error", e);
            }
        }
        LOG.info("[" + workerId + "] stopped.");
    }

    public void stop() { running = false; }

    // ── Processing logic ─────────────────────────────────────────────────────

    private void processRequest(UrlRequest request) {
        request.setStatus(RequestStatus.PROCESSING);
        LOG.fine("[" + workerId + "] processing: " + request.getOriginalUrl());

        try {
            String shortCode = resolveShortCode(request);
            request.setShortCode(shortCode);
            request.setStatus(RequestStatus.COMPLETED);

            LOG.info(String.format("[%s] ✓ %s → %s",
                    workerId, request.getOriginalUrl(), shortCode));

        } catch (Exception e) {
            request.setStatus(RequestStatus.FAILED);
            LOG.log(Level.WARNING,
                    "[" + workerId + "] failed to process: " + request.getOriginalUrl(), e);
        }
    }

    /**
     * Returns the short code for the URL in {@code request}.
     *
     * Steps:
     * 1. If a custom alias was requested, try to claim it.
     * 2. If the URL was already shortened, return the existing code.
     * 3. Otherwise generate a new Base62 code, store the mapping.
     */
    private String resolveShortCode(UrlRequest request) {
        String originalUrl  = request.getOriginalUrl();
        String customAlias  = request.getCustomAlias();

        // ── Custom alias path ─────────────────────────────────────────────
        if (customAlias != null && !customAlias.isBlank()) {
            if (storage.existsByShortCode(customAlias)) {
                throw new IllegalStateException(
                        "Custom alias '" + customAlias + "' is already taken.");
            }
            UrlMapping mapping = new UrlMapping(customAlias, originalUrl);
            storage.store(mapping);
            analyticsService.register(customAlias);
            return customAlias;
        }

        // ── Deduplication ─────────────────────────────────────────────────
        Optional<String> existing =
                storage.findShortCodeByOriginalUrl(originalUrl);
        if (existing.isPresent()) {
            return existing.get();
        }

        // ── Generate new code ─────────────────────────────────────────────
        String shortCode;
        int attempts = 0;
        do {
            shortCode = Base62Encoder.nextCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException(
                        "Could not find a free short code after 10 attempts.");
            }
        } while (storage.existsByShortCode(shortCode));

        UrlMapping mapping = new UrlMapping(shortCode, originalUrl);
        storage.store(mapping);
        analyticsService.register(shortCode);

        return shortCode;
    }
}
