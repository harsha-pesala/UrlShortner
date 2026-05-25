package com.urlshortener.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Encodes a long counter into a Base62 short code.
 *
 * Character set: 0-9, a-z, A-Z  (62 chars)
 * A 6-character code covers ~56 billion combinations — plenty for an
 * in-memory demo and easily extended.
 */
public final class Base62Encoder {

    private static final String CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE      = 62;
    private static final int CODE_LEN  = 6;

    /** Monotonically increasing counter; thread-safe. */
    private static final AtomicLong COUNTER = new AtomicLong(1_000_000L);

    private Base62Encoder() { /* utility class */ }

    /**
     * Generates the next unique short code.
     * Thread-safe — each call gets a distinct counter value.
     */
    public static String nextCode() {
        return encode(COUNTER.getAndIncrement());
    }

    /**
     * Encodes {@code number} into a Base62 string of exactly {@link #CODE_LEN}
     * characters, left-padded with '0' if necessary.
     */
    public static String encode(long number) {
        StringBuilder sb = new StringBuilder();
        long n = number;
        while (n > 0) {
            sb.append(CHARS.charAt((int)(n % BASE)));
            n /= BASE;
        }
        // Pad to CODE_LEN
        while (sb.length() < CODE_LEN) {
            sb.append('0');
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to a long.
     * Useful for debugging or reconstructing counters.
     */
    public static long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            result = result * BASE + CHARS.indexOf(c);
        }
        return result;
    }
}
