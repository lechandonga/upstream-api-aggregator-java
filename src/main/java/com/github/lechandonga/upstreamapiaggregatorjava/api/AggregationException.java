package com.github.lechandonga.upstreamapiaggregatorjava.api;

import java.util.List;

/**
 * 所有上游都不可用/都失败时抛出，携带每个上游的失败原因。
 */
public class AggregationException extends Exception {

    private final List<String> upstreamErrors;

    public AggregationException(List<String> upstreamErrors) {
        super("所有上游均不可用: " + String.join("; ", upstreamErrors));
        this.upstreamErrors = List.copyOf(upstreamErrors);
    }

    public List<String> upstreamErrors() {
        return upstreamErrors;
    }
}
