package com.github.lechandonga.upstreamapiaggregatorjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lechandonga.upstreamapiaggregatorjava.api.AggregationException;
import com.github.lechandonga.upstreamapiaggregatorjava.api.AggregationService;
import com.github.lechandonga.upstreamapiaggregatorjava.normalize.VersionedNormalizer;
import com.github.lechandonga.upstreamapiaggregatorjava.ratelimit.CallerRateLimiter;
import com.github.lechandonga.upstreamapiaggregatorjava.ratelimit.RateLimitProperties;
import com.github.lechandonga.upstreamapiaggregatorjava.tests.stub.StubUpstreamClient;
import com.github.lechandonga.upstreamapiaggregatorjava.upstream.UpstreamProperties;
import com.github.lechandonga.upstreamapiaggregatorjava.upstream.UpstreamSelector;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 场景一：故障转移与熔断摘除/恢复。
 */
@DisplayName("场景: 上游故障转移与熔断恢复")
class FailoverTest {

    private static final String V1_PAYLOAD =
            "{\"items\":[{\"id\":\"1\",\"title\":\"apple\",\"price_cents\":100,\"labels\":[\"fruit\"]}]}";

    private StubUpstreamClient alpha;
    private StubUpstreamClient beta;
    private UpstreamSelector selector;
    private AggregationService service;

    @BeforeEach
    void setUp() {
        alpha = new StubUpstreamClient("alpha");
        beta = new StubUpstreamClient("beta");
        alpha.respond("v1", V1_PAYLOAD);
        beta.respond("v1", V1_PAYLOAD);

        UpstreamProperties props = new UpstreamProperties();
        props.setFailureThreshold(2);
        props.setCooldownMillis(50);
        props.setInstances(List.of(def("alpha", 10), def("beta", 1)));
        selector = new UpstreamSelector(props, List.of(alpha, beta));

        RateLimitProperties rl = new RateLimitProperties();
        rl.setCapacity(1000);
        service = new AggregationService(selector, new VersionedNormalizer(),
                new CallerRateLimiter(rl));
    }

    @Test
    @DisplayName("用例: 高权重上游失败时自动切换到下一个上游")
    void failoverToNextUpstream() throws Exception {
        alpha.failWith(StubUpstreamClient.Mode.TIMEOUT);
        AggregationService.AggregateResult result = service.aggregate("c1", "q");
        assertEquals("beta", result.upstreamId());
        assertEquals(1, result.items().size());
        System.out.println("[PASS] 故障转移: alpha 超时 -> 由 beta 提供服务");
    }

    @Test
    @DisplayName("用例: 连续失败达到阈值后上游被摘除，不再参与请求")
    void circuitOpensAfterThreshold() throws Exception {
        alpha.failWith(StubUpstreamClient.Mode.ERROR);
        service.aggregate("c1", "q"); // alpha 失败 1 次
        service.aggregate("c1", "q"); // alpha 失败 2 次 -> 达到阈值被摘除
        int callsBefore = alpha.callCount();
        service.aggregate("c1", "q");
        service.aggregate("c1", "q");
        assertEquals(callsBefore, alpha.callCount(), "熔断期内 alpha 不应再被调用");
        System.out.println("[PASS] 熔断摘除: alpha 连续失败 2 次后被摘除");
    }

    @Test
    @DisplayName("用例: 冷却期结束后上游恢复并重新被选中")
    void circuitRecoversAfterCooldown() throws Exception {
        alpha.failWith(StubUpstreamClient.Mode.ERROR);
        service.aggregate("c1", "q");
        service.aggregate("c1", "q"); // 熔断
        Thread.sleep(80); // 超过冷却期 50ms
        alpha.respond("v1", V1_PAYLOAD); // 上游恢复
        AggregationService.AggregateResult result = service.aggregate("c1", "q");
        assertEquals("alpha", result.upstreamId(), "恢复后应重新选中高权重的 alpha");
        System.out.println("[PASS] 熔断恢复: 冷却期后 alpha 重新被选中");
    }

    @Test
    @DisplayName("用例: 所有上游不可用时返回明确失败原因，不挂起不返回空数据")
    void allUpstreamsDown() {
        alpha.failWith(StubUpstreamClient.Mode.TIMEOUT);
        beta.failWith(StubUpstreamClient.Mode.ERROR);
        AggregationException ex = assertThrows(AggregationException.class,
                () -> service.aggregate("c1", "q"));
        assertEquals(2, ex.upstreamErrors().size());
        assertTrue(ex.getMessage().contains("alpha"));
        assertTrue(ex.getMessage().contains("beta"));
        System.out.println("[PASS] 全部失败: 返回明确原因 " + ex.upstreamErrors());
    }

    private static UpstreamProperties.UpstreamDef def(String id, int weight) {
        UpstreamProperties.UpstreamDef def = new UpstreamProperties.UpstreamDef();
        def.setId(id);
        def.setWeight(weight);
        return def;
    }
}
