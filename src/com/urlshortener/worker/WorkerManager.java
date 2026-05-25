package com.urlshortener.worker;

import com.urlshortener.analytics.AnalyticsService;
import com.urlshortener.queue.UrlRequestQueue;
import com.urlshortener.storage.InMemoryStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Creates, starts, and shuts down the pool of {@link UrlProcessorWorker} threads.
 *
 * Uses a fixed-size {@link ExecutorService}. Each worker runs an infinite loop
 * that is broken by either an interrupt or by calling {@link #shutdown()}.
 */
public class WorkerManager {

    private static final Logger LOG = Logger.getLogger(WorkerManager.class.getName());

    private final int                  workerCount;
    private final UrlRequestQueue      queue;
    private final InMemoryStorage      storage;
    private final AnalyticsService     analyticsService;

    private final List<UrlProcessorWorker> workers  = new ArrayList<>();
    private ExecutorService                executor;

    public WorkerManager(int workerCount,
                         UrlRequestQueue queue,
                         InMemoryStorage storage,
                         AnalyticsService analyticsService) {
        this.workerCount      = workerCount;
        this.queue            = queue;
        this.storage          = storage;
        this.analyticsService = analyticsService;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Starts all worker threads. Safe to call only once. */
    public void start() {
        executor = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);   // won't block JVM shutdown
            return t;
        });

        for (int i = 1; i <= workerCount; i++) {
            UrlProcessorWorker worker = new UrlProcessorWorker(
                    "worker-" + i, queue, storage, analyticsService);
            workers.add(worker);
            executor.submit(worker);
        }

        LOG.info("WorkerManager started " + workerCount + " worker(s).");
    }

    /**
     * Signals all workers to stop, then waits up to 5 seconds for them
     * to drain in-flight work before forcing a shutdown.
     */
    public void shutdown() {
        workers.forEach(UrlProcessorWorker::stop);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("WorkerManager shut down.");
    }

    public int workerCount() { return workerCount; }
}
