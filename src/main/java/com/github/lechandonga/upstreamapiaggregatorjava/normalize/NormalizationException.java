package com.github.lechandonga.upstreamapiaggregatorjava.normalize;

public class NormalizationException extends Exception {

    private final Reason reason;

    public NormalizationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        /** 不认识的版本号，明确拒绝，不按最新版处理。 */
        UNSUPPORTED_VERSION,
        /** 版本认识但缺失关键字段或字段类型不对。 */
        INVALID_PAYLOAD
    }
}
