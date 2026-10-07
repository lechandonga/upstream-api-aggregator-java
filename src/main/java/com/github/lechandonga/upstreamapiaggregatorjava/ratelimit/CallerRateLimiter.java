package com.github.lechandonga.upstreamapiaggregatorjava.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 按调用方维度的令牌桶限流器。
 * 并发下通过桶级别的同步保证不超发、不漏计；被拒绝的请求不消耗令牌。
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class CallerRateLimiter {

    private final RateLimitProperties properties;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final NanoClock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CallerRateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    CallerRateLimiter(RateLimitProperties properties, NanoClock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 尝试为调用方获取一个令牌。
     *
     * @return true 表示放行（已消耗额度）；false 表示超限拒绝（未消耗额度）
     */
    public boolean tryAcquire(String caller) {
        TokenBucket bucket = buckets.computeIfAbsent(caller, key -> new TokenBucket());
        return bucket.tryAcquire();
    }

    /** 仅供测试/观测：当前剩余令牌数。 */
    public double availableTokens(String caller) {
        TokenBucket bucket = buckets.get(caller);
        return bucket == null ? properties.getCapacity() : bucket.available();
    }

    @FunctionalInterface
    interface NanoClock {
        long nanoTime();
    }

    private final class TokenBucket {
        private double tokens = properties.getCapacity();
        private long lastRefillNanos = clock.nanoTime();

        synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized double available() {
            refill();
            return tokens;
        }

        private void refill() {
            long now = clock.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            tokens = Math.min(properties.getCapacity(),
                    tokens + elapsed * properties.getRefillPerSecond() / 1_000_000_000.0);
            lastRefillNanos = now;
        }
    }
}
