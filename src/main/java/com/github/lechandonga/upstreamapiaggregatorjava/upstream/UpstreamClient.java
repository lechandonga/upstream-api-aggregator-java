package com.github.lechandonga.upstreamapiaggregatorjava.upstream;

/**
 * 上游来源抽象。实现类可以是 HTTP 客户端，也可以是测试桩。
 */
public interface UpstreamClient {

    String id();

    /**
     * 拉取原始数据。
     *
     * @throws UpstreamException 超时、连接失败或上游返回错误时抛出
     */
    RawUpstreamResponse fetch(String query) throws UpstreamException;

    record RawUpstreamResponse(String version, String payload) {
    }
}
