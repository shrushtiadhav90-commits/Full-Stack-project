package com.nexus.quiz.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateTimeUtilTest {

    @Test
    void convertsUtcToIstAtExactOffsetAndRoundTrips() {
        Instant instant = Instant.parse("2026-10-20T08:30:45.123Z");
        var ist = DateTimeUtil.toIst(instant);

        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), ist.getOffset());
        assertEquals(instant, DateTimeUtil.fromIst(ist));
        assertEquals("2026-10-20T14:00:45.123+05:30", DateTimeUtil.toIstString(instant));
    }
}
