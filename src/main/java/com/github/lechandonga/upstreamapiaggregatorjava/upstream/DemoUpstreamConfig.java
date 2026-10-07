package com.github.lechandonga.upstreamapiaggregatorjava.upstream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地演示用的内存上游。生产环境替换为真实的 HTTP 客户端实现即可，
 * 只要实现 {@link UpstreamClient} 并声明为 bean，选择器会自动接入。
 */
@Configuration
public class DemoUpstreamConfig {

    @Bean
    public UpstreamClient alphaUpstream() {
        return new InMemoryUpstream("alpha", "v1",
                "{\"items\":[{\"id\":\"1\",\"title\":\"apple\",\"price_cents\":100,\"labels\":[\"fruit\"]}]}");
    }

    @Bean
    public UpstreamClient betaUpstream() {
        return new InMemoryUpstream("beta", "v2",
                "{\"data\":{\"records\":[{\"uid\":\"1\",\"displayName\":\"apple\",\"amount\":100,\"tags\":[\"fruit\"]}]}}");
    }

    private record InMemoryUpstream(String id, String version, String payload)
            implements UpstreamClient {
        @Override
        public RawUpstreamResponse fetch(String query) {
            return new RawUpstreamResponse(version, payload);
        }
    }
}
