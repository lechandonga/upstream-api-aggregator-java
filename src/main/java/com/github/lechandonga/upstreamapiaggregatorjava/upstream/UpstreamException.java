package com.github.lechandonga.upstreamapiaggregatorjava.upstream;

public class UpstreamException extends Exception {
    public UpstreamException(String message) {
        super(message);
    }

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
