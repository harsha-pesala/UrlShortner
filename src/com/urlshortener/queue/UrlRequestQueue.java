package com.urlshortener.queue;

import com.urlshortener.model.UrlRequest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bounded blocking queue that decouples URL submission (producers)
 * from URL processing (consumers / workers).
 *
 * Capacity is capped at {@value #CAPACITY} to provide back-pressure:
 * {@link #enqueue} blocks when the queue is full.
 */
public class UrlRequestQueue {

    private static final int CAPACITY = 1_000;

    private final BlockingQueue<UrlRequest> queue =
            new LinkedBlockingQueue<>(CAPACITY);

    // ── Producer API ──────────────────────────────────────────────────────────

    /**
     * Inserts the request, blocking up to {@code timeoutMs} milliseconds.
     *
     * @return true if enqueued, false if timed out
     */
    public boolean enqueue(UrlRequest request, long timeoutMs)
            throws InterruptedException {
        return queue.offer(request, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Convenience overload — blocks for up to 2 seconds. */
    public boolean enqueue(UrlRequest request) throws InterruptedException {
        return enqueue(request, 2_000);
    }

    // ── Consumer API ─────────────────────────────────────────────────────────

    /**
     * Retrieves and removes the head of the queue, waiting up to
     * {@code timeoutMs} milliseconds for an element to become available.
     *
     * @return the next request, or {@code null} on timeout
     */
    public UrlRequest poll(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    // ── Monitoring ────────────────────────────────────────────────────────────

    public int size()     { return queue.size(); }
    public boolean isEmpty() { return queue.isEmpty(); }

    /** Remaining capacity before back-pressure kicks in. */
    public int remainingCapacity() { return queue.remainingCapacity(); }
}
