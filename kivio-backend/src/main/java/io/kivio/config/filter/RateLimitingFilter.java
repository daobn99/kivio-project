package io.kivio.config.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.kivio.config.security.KivioUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String STRIPE_WEBHOOK_PATH = "/api/v1/webhooks/stripe";

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.auth.capacity:10}")
    private int authCapacity;

    @Value("${app.rate-limit.auth.refill-duration-seconds:60}")
    private int authDurationSeconds;

    @Value("${app.rate-limit.api.capacity:100}")
    private int apiCapacity;

    @Value("${app.rate-limit.api.refill-duration-seconds:60}")
    private int apiDurationSeconds;

    @Value("${app.rate-limit.public.capacity:30}")
    private int publicCapacity;

    @Value("${app.rate-limit.public.refill-duration-seconds:60}")
    private int publicDurationSeconds;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        String path = request.getRequestURI();

        if (path.equals(STRIPE_WEBHOOK_PATH)) {
            chain.doFilter(request, response);
            return;
        }

        String bucketKey = resolveBucketKey(request, path);
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(path));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("rate_limit_exceeded key={} uri={}", bucketKey, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write(rateLimitProblemDetail());
        }
    }

    private String resolveBucketKey(HttpServletRequest request, String path) {
        if (path.startsWith(AUTH_PATH_PREFIX)) {
            return "auth:" + request.getRemoteAddr();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof KivioUserDetails details) {
            return "user:" + details.getUserId();
        }
        return "public:" + request.getRemoteAddr();
    }

    private Bucket createBucket(String path) {
        if (path.startsWith(AUTH_PATH_PREFIX)) {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(authCapacity)
                            .refillGreedy(authCapacity, Duration.ofSeconds(authDurationSeconds))
                            .build())
                    .build();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(apiCapacity)
                            .refillGreedy(apiCapacity, Duration.ofSeconds(apiDurationSeconds))
                            .build())
                    .build();
        }
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(publicCapacity)
                        .refillGreedy(publicCapacity, Duration.ofSeconds(publicDurationSeconds))
                        .build())
                .build();
    }

    private String rateLimitProblemDetail() {
        return """
                {
                  "type": "https://kivio.example.com/problems/rate-limit-exceeded",
                  "title": "Rate Limit Exceeded",
                  "status": 429,
                  "code": "RATE_LIMIT_EXCEEDED",
                  "detail": "リクエスト数が上限に達しました。60秒後に再試行してください"
                }""";
    }
}
