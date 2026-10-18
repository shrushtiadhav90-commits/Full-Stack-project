package com.nexus.quiz.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * All IST conversions go through this single class.
 * ZoneId.of("Asia/Kolkata") is always used explicitly — ZoneId.systemDefault() is NEVER called here.
 */
public final class DateTimeUtil {

    private DateTimeUtil() {}

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Convert a UTC Instant to IST ZonedDateTime.
     */
    public static ZonedDateTime toIst(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(IST);
    }

    /**
     * Format a UTC Instant as ISO 8601 with +05:30 offset, e.g. "2025-08-15T14:35:00+05:30".
     */
    public static String toIstString(Instant instant) {
        if (instant == null) return null;
        return toIst(instant).format(ISO_OFFSET);
    }

    /**
     * Convert an IST ZonedDateTime back to a UTC Instant.
     */
    public static Instant fromIst(ZonedDateTime istDateTime) {
        if (istDateTime == null) return null;
        return istDateTime.toInstant();
    }
}
