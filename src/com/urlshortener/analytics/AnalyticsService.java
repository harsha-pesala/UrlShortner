package com.urlshortener.analytics;

import com.urlshortener.model.AnalyticsData;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages per-URL analytics.
 *
 * Click recording is fully asynchronous — callers submit events to a single-
 * threaded executor so the redirect path is never blocked.
 */
public class AnalyticsService {

    private final ConcurrentHashMap<String, AnalyticsData> store =
            new ConcurrentHashMap<>();

    /**
     * Dedicated single-threaded executor for async click recording.
     * Using a bounded queue to avoid unbounded memory growth under heavy load.
     */
    private final ExecutorService analyticsExecutor =
            new ThreadPoolExecutor(
                    1, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(10_000),
                    r -> {
                        Thread t = new Thread(r, "analytics-worker");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.DiscardOldestPolicy()   // drop oldest on overflow
            );

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Call once when a new short code is created. */
    public void register(String shortCode) {
        store.putIfAbsent(shortCode, new AnalyticsData(shortCode));
    }

    /** Asynchronously records one click for the given short code. */
    public void recordClickAsync(String shortCode) {
        analyticsExecutor.submit(() -> {
            AnalyticsData data = store.get(shortCode);
            if (data != null) {
                data.recordClick();
            }
        });
    }

    /** Shuts down the async executor; waits up to 5 s for in-flight events. */
    public void shutdown() {
        analyticsExecutor.shutdown();
        try {
            if (!analyticsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                analyticsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            analyticsExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public Optional<AnalyticsData> getStats(String shortCode) {
        return Optional.ofNullable(store.get(shortCode));
    }

    /**
     * Returns the top {@code n} URLs ordered by total click count, descending.
     */
    public List<AnalyticsData> topUrls(int n) {
        return store.values().stream()
                .sorted(Comparator.comparingLong(AnalyticsData::getTotalClicks).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public int trackedCount() {
        return store.size();
    }
}
