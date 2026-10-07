package com.github.lechandonga.upstreamapiaggregatorjava.api;

public class RateLimitedException extends Exception {
    public RateLimitedException(String caller) {
        super("调用方 " + caller + " 超出限流额度");
    }
}
