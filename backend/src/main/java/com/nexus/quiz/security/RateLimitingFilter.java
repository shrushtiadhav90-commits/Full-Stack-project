package com.nexus.quiz.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final int IP_REQUESTS_PER_MINUTE = 300;
    private static final int USER_REQUESTS_PER_MINUTE = 5;
    private static final long BUCKET_IDLE_TIMEOUT_NANOS = Duration.ofMinutes(10).toNanos();

    private final ObjectMapper objectMapper;
    private final Map<String, BucketEntry> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> usernameBuckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LOGIN_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long now = System.nanoTime();
        ConsumptionProbe ipProbe = getBucket(ipBuckets, clientIp(request), IP_REQUESTS_PER_MINUTE, now)
                .tryConsumeAndReturnRemaining(1);
        if (!ipProbe.isConsumed()) {
            writeRateLimitResponse(response, ipProbe);
            return;
        }

        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        ConsumptionProbe usernameProbe = getBucket(
                usernameBuckets,
                loginIdentifier(cachedRequest),
                USER_REQUESTS_PER_MINUTE,
                now).tryConsumeAndReturnRemaining(1);
        if (!usernameProbe.isConsumed()) {
            writeRateLimitResponse(response, usernameProbe);
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private Bucket getBucket(
            Map<String, BucketEntry> buckets,
            String key,
            int capacity,
            long now) {
        BucketEntry entry = buckets.compute(key, (ignored, current) -> {
            if (current == null || now - current.lastAccessNanos > BUCKET_IDLE_TIMEOUT_NANOS) {
                current = new BucketEntry(createBucket(capacity));
            }
            current.lastAccessNanos = now;
            return current;
        });

        if (requestCount.incrementAndGet() % 256 == 0) {
            evictIdleBuckets(buckets, now);
        }
        return entry.bucket;
    }

    private Bucket createBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void evictIdleBuckets(Map<String, BucketEntry> buckets, long now) {
        buckets.entrySet().removeIf(entry ->
                now - entry.getValue().lastAccessNanos > BUCKET_IDLE_TIMEOUT_NANOS);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstAddress = forwardedFor.split(",", 2)[0].trim();
            if (!firstAddress.isEmpty()) {
                return firstAddress;
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private String loginIdentifier(CachedBodyRequest request) {
        try {
            JsonNode body = objectMapper.readTree(request.body);
            JsonNode identifier = body == null ? null : body.get("username");
            if (identifier == null || !identifier.isTextual() || identifier.asText().isBlank()) {
                identifier = body == null ? null : body.get("email");
            }
            if (identifier == null || !identifier.isTextual() || identifier.asText().isBlank()) {
                return "unknown";
            }
            return identifier.asText().trim().toLowerCase(java.util.Locale.ROOT);
        } catch (IOException exception) {
            return "unknown";
        }
    }

    private void writeRateLimitResponse(HttpServletResponse response, ConsumptionProbe probe)
            throws IOException {
        long retryAfterSeconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorEnvelope(false, "Too many login attempts", null));
    }

    private static final class BucketEntry {
        private final Bucket bucket;
        private volatile long lastAccessNanos;

        private BucketEntry(Bucket bucket) {
            this.bucket = bucket;
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new IllegalStateException("Asynchronous request reading is not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private record ErrorEnvelope(boolean success, String message, Object data) {}
}
