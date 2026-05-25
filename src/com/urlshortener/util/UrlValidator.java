package com.urlshortener.util;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Validates URL format before URLs enter the processing pipeline.
 */
public final class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;

    private UrlValidator() { /* utility class */ }

    /**
     * Returns {@code true} when {@code url} is non-null, not blank,
     * within the length limit, and parseable as an HTTP/HTTPS URL.
     */
    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) return false;
        if (url.length() > MAX_URL_LENGTH)  return false;

        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return false;
        }

        try {
            new URL(trimmed);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Validates and throws a descriptive {@link IllegalArgumentException}
     * on failure — handy for service-layer callers.
     */
    public static void validateOrThrow(String url) {
        if (!isValid(url)) {
            throw new IllegalArgumentException(
                    "Invalid URL: '" + url + "'. Must start with http:// or https:// and be ≤ "
                            + MAX_URL_LENGTH + " characters.");
        }
    }
}
