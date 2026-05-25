package com.urlshortener.service;

import com.urlshortener.model.UrlRequest;
import com.urlshortener.queue.UrlRequestQueue;
import com.urlshortener.util.UrlValidator;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Primary entry point for URL shortening requests.
 *
 * Acts as the producer side of the producer-consumer pipeline:
 * validates the URL, wraps it in a {@link UrlRequest}, and pushes
 * it onto the {@link UrlRequestQueue}.
 */
public class UrlShortenerService {

    private static final Logger LOG =
            Logger.getLogger(UrlShortenerService.class.getName());

    /** Max time to wait for the queue to accept the request. */
    private static final long ENQUEUE_TIMEOUT_MS = 3_000;

    /** Max time to wait for a worker to complete the request. */
    private static final long PROCESS_TIMEOUT_MS = 5_000;

    private final UrlRequestQueue queue;

    public UrlShortenerService(UrlRequestQueue queue) {
        this.queue = queue;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submits a URL for shortening.
     *
     * The method blocks until a worker completes the request (up to
     * {@value #PROCESS_TIMEOUT_MS} ms) and then returns the short code.
     *
     * @param  originalUrl  the URL to shorten
     * @return the generated or existing short code
     * @throws IllegalArgumentException if the URL format is invalid
     * @throws IllegalStateException    if processing times out or fails
     */
    public String shorten(String originalUrl) {
        return shorten(originalUrl, null);
    }

    /**
     * Submits a URL for shortening with an optional custom alias.
     */
    public String shorten(String originalUrl, String customAlias) {
        UrlValidator.validateOrThrow(originalUrl);

        UrlRequest request = new UrlRequest(originalUrl.trim(), customAlias);
        LOG.fine("Submitting request: " + request.getRequestId());

        try {
            boolean accepted = queue.enqueue(request, ENQUEUE_TIMEOUT_MS);
            if (!accepted) {
                throw new IllegalStateException(
                        "Queue is full — please retry in a moment.");
            }

            return awaitCompletion(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for short URL.");
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String awaitCompletion(UrlRequest request) throws InterruptedException {
        long deadline = System.currentTimeMillis() + PROCESS_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            UrlRequest.RequestStatus status = request.getStatus();

            switch (status) {
                case COMPLETED:
                    return request.getShortCode();

                case FAILED:
                    throw new IllegalStateException(
                            "Worker failed to process URL: " + request.getOriginalUrl());

                default:
                    // PENDING or PROCESSING — keep polling
                    TimeUnit.MILLISECONDS.sleep(50);
            }
        }

        throw new IllegalStateException(
                "Timed out waiting for URL processing after "
                        + PROCESS_TIMEOUT_MS + " ms.");
    }
}
