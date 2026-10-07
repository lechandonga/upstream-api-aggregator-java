package com.github.lechandonga.upstreamapiaggregatorjava.tests.stub;

import com.github.lechandonga.upstreamapiaggregatorjava.upstream.UpstreamClient;
import com.github.lechandonga.upstreamapiaggregatorjava.upstream.UpstreamException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地测试桩上游：可编程地返回指定版本/数据，或模拟超时、报错，不连外网。
 */
public class StubUpstreamClient implements UpstreamClient {

    public enum Mode {
        OK, TIMEOUT, ERROR
    }

    private final String id;
    private volatile Mode mode = Mode.OK;
    private volatile String version = "v1";
    private volatile String payload = "{\"items\":[]}";
    private final AtomicInteger callCount = new AtomicInteger();

    public StubUpstreamClient(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public RawUpstreamResponse fetch(String query) throws UpstreamException {
        callCount.incrementAndGet();
        return switch (mode) {
            case OK -> new RawUpstreamResponse(version, payload);
            case TIMEOUT -> throw new UpstreamException(id + " 超时");
            case ERROR -> throw new UpstreamException(id + " 返回 500");
        };
    }

    public void respond(String version, String payload) {
        this.version = version;
        this.payload = payload;
        this.mode = Mode.OK;
    }

    public void failWith(Mode mode) {
        this.mode = mode;
    }

    public int callCount() {
        return callCount.get();
    }
}
