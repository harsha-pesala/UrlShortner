package com.urlshortener.main;

import com.urlshortener.analytics.AnalyticsService;
import com.urlshortener.model.AnalyticsData;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.queue.UrlRequestQueue;
import com.urlshortener.service.RedirectService;
import com.urlshortener.service.UrlShortenerService;
import com.urlshortener.storage.InMemoryStorage;
import com.urlshortener.worker.WorkerManager;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLI entry point for the Queue-Based URL Shortener.
 *
 * Available commands
 * ──────────────────
 *   shorten  <url> [alias]   – shorten a URL (optional custom alias)
 *   redirect <code>          – resolve & "open" a short URL
 *   stats    <code>          – show analytics for a short code
 *   topUrls  [n]             – list top n most-clicked URLs (default 5)
 *   list                     – list all stored mappings
 *   help                     – show this help
 *   exit                     – shut down gracefully
 */
public class Application {

    // ── Configuration ─────────────────────────────────────────────────────────

    private static final int    WORKER_COUNT = 3;
    private static final String BASE_URL     = "http://sho.rt/";
    private static final String SEPARATOR    = "─".repeat(60);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    // ── Wiring ────────────────────────────────────────────────────────────────

    private final UrlRequestQueue    requestQueue;
    private final InMemoryStorage    storage;
    private final AnalyticsService   analyticsService;
    private final UrlShortenerService shortenerService;
    private final RedirectService    redirectService;
    private final WorkerManager      workerManager;

    private Application() {
        requestQueue      = new UrlRequestQueue();
        storage           = new InMemoryStorage();
        analyticsService  = new AnalyticsService();
        shortenerService  = new UrlShortenerService(requestQueue);
        redirectService   = new RedirectService(storage, analyticsService);
        workerManager     = new WorkerManager(WORKER_COUNT, requestQueue, storage, analyticsService);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private void start() {
        // Suppress verbose JUL output — keep console clean
        Logger.getLogger("").setLevel(Level.WARNING);
        Logger.getLogger("com.urlshortener").setLevel(Level.WARNING);

        workerManager.start();
        printBanner();
        runCommandLoop();
        shutdown();
    }

    private void shutdown() {
        println("\nShutting down workers…");
        workerManager.shutdown();
        analyticsService.shutdown();
        println("Goodbye!");
    }

    // ── Command loop ──────────────────────────────────────────────────────────

    private void runCommandLoop() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            print("\n» ");
            if (!scanner.hasNextLine()) break;

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts   = line.split("\\s+", 3);
            String   command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "shorten"  -> handleShorten(parts);
                    case "redirect" -> handleRedirect(parts);
                    case "stats"    -> handleStats(parts);
                    case "topurls"  -> handleTopUrls(parts);
                    case "list"     -> handleList();
                    case "help"     -> printHelp();
                    case "exit", "quit" -> { return; }
                    default -> println("Unknown command. Type 'help' for usage.");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                println("✗ Error: " + e.getMessage());
            } catch (Exception e) {
                println("✗ Unexpected error: " + e.getMessage());
            }
        }
    }

    // ── Command handlers ──────────────────────────────────────────────────────

    private void handleShorten(String[] parts) {
        if (parts.length < 2) {
            println("Usage: shorten <url> [alias]");
            return;
        }

        String url   = parts[1];
        String alias = parts.length >= 3 ? parts[2] : null;

        println("Submitting to queue…");
        String shortCode = shortenerService.shorten(url, alias);
        println(SEPARATOR);
        println("  Original : " + url);
        println("  Short URL: " + BASE_URL + shortCode);
        println("  Code     : " + shortCode);
        println(SEPARATOR);
    }

    private void handleRedirect(String[] parts) {
        if (parts.length < 2) {
            println("Usage: redirect <code>");
            return;
        }

        String shortCode  = parts[1];
        String originalUrl = redirectService.redirect(shortCode);

        println(SEPARATOR);
        println("  Redirecting…");
        println("  " + BASE_URL + shortCode + "  →  " + originalUrl);
        println("  (Click recorded asynchronously)");
        println(SEPARATOR);
    }

    private void handleStats(String[] parts) {
        if (parts.length < 2) {
            println("Usage: stats <code>");
            return;
        }

        String shortCode = parts[1];
        Optional<AnalyticsData> opt = analyticsService.getStats(shortCode);

        if (opt.isEmpty()) {
            println("No analytics found for '" + shortCode + "'.");
            return;
        }

        AnalyticsData a = opt.get();
        Optional<UrlMapping> mapping = storage.findByShortCode(shortCode);

        println(SEPARATOR);
        println("  Stats for: " + BASE_URL + shortCode);
        mapping.ifPresent(m -> println("  Original : " + m.getOriginalUrl()));
        println("  Created  : " + DT_FMT.format(a.getCreatedAt()));
        println("  Clicks   : " + a.getTotalClicks());
        println("  Last hit : " + (a.getLastAccessedAt() == null
                ? "never"
                : DT_FMT.format(a.getLastAccessedAt())));

        if (!a.getDailyClicks().isEmpty()) {
            println("  Daily    :");
            a.getDailyClicks().entrySet().stream()
                    .sorted((x, y) -> y.getKey().compareTo(x.getKey()))
                    .forEach(e -> println("    " + e.getKey() + " → " + e.getValue().get() + " click(s)"));
        }
        println(SEPARATOR);
    }

    private void handleTopUrls(String[] parts) {
        int n = 5;
        if (parts.length >= 2) {
            try { n = Integer.parseInt(parts[1]); }
            catch (NumberFormatException ignored) { }
        }

        List<AnalyticsData> top = analyticsService.topUrls(n);
        if (top.isEmpty()) {
            println("No URLs tracked yet.");
            return;
        }

        println(SEPARATOR);
        println(String.format("  Top %d URL(s) by clicks:", n));
        println(SEPARATOR);

        AtomicLong rank = new AtomicLong(1);
        top.forEach(a -> {
            Optional<UrlMapping> m = storage.findByShortCode(a.getShortCode());
            String original = m.map(UrlMapping::getOriginalUrl).orElse("(unknown)");
            println(String.format("  %2d. [%6d clicks]  %s%-12s  %s",
                    rank.getAndIncrement(),
                    a.getTotalClicks(),
                    BASE_URL,
                    a.getShortCode(),
                    truncate(original, 40)));
        });
        println(SEPARATOR);
    }

    private void handleList() {
        var mappings = storage.allMappings();
        if (mappings.isEmpty()) {
            println("No URLs stored yet.");
            return;
        }

        println(SEPARATOR);
        println(String.format("  %-12s  %-50s  %s", "Code", "Original URL", "Created"));
        println(SEPARATOR);
        mappings.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .forEach(m -> println(String.format("  %-12s  %-50s  %s",
                        m.getShortCode(),
                        truncate(m.getOriginalUrl(), 50),
                        DT_FMT.format(m.getCreatedAt()))));
        println(SEPARATOR);
        println("  Total: " + mappings.size() + " URL(s)");
        println(SEPARATOR);
    }

    // ── Banner & help ─────────────────────────────────────────────────────────

    private void printBanner() {
        println("""

                ╔══════════════════════════════════════════════════════════╗
                ║          Queue-Based URL Shortener  ·  Core Java        ║
                ║     Async pipeline  ·  BlockingQueue  ·  3 workers      ║
                ╚══════════════════════════════════════════════════════════╝
                  Type 'help' for available commands.
                """);
    }

    private void printHelp() {
        println("""

                Commands
                ────────────────────────────────────────────────────────────
                  shorten  <url> [alias]   Shorten a URL (custom alias opt.)
                  redirect <code>          Resolve and "open" a short URL
                  stats    <code>          Show click analytics for a code
                  topUrls  [n]             Top n most-clicked URLs (def. 5)
                  list                     List all stored URL mappings
                  help                     Show this help text
                  exit                     Quit the application
                ────────────────────────────────────────────────────────────
                Examples
                  shorten  https://www.example.com/some/very/long/path
                  shorten  https://github.com/user/repo  myrepo
                  redirect BwpqM1
                  stats    BwpqM1
                  topUrls  10
                """);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void print(String msg)   { System.out.print(msg); }
    private static void println(String msg) { System.out.println(msg); }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        new Application().start();
    }
}
