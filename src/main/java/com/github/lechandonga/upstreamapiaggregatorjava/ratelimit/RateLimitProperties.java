package com.github.lechandonga.upstreamapiaggregatorjava.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 按调用方限流配置（令牌桶）。
 */
@ConfigurationProperties(prefix = "aggregator.rate-limit")
public class RateLimitProperties {

    /** 每个调用方的桶容量（突发上限）。 */
    private int capacity = 10;

    /** 每秒补充的令牌数。 */
    private double refillPerSecond = 5.0;

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getRefillPerSecond() {
        return refillPerSecond;
    }

    public void setRefillPerSecond(double refillPerSecond) {
        this.refillPerSecond = refillPerSecond;
    }
}
