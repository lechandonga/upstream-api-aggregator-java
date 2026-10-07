package com.github.lechandonga.upstreamapiaggregatorjava.upstream;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上游选择与健康熔断：
 * <ul>
 *   <li>按权重在健康上游中做加权随机选择，同权重按配置顺序优先；</li>
 *   <li>连续失败达到阈值即摘除，冷却期结束后允许一次试探，成功则恢复；</li>
 *   <li>所有上游不可用时返回空列表，由调用方给出明确失败。</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(UpstreamProperties.class)
public class UpstreamSelector {

    private static final Logger log = LoggerFactory.getLogger(UpstreamSelector.class);

    private final UpstreamProperties properties;
    private final Map<String, UpstreamClient> clientsById;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public UpstreamSelector(UpstreamProperties properties, List<UpstreamClient> clients) {
        this(properties, clients, Clock.systemUTC());
    }

    UpstreamSelector(UpstreamProperties properties, List<UpstreamClient> clients, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.clientsById = new ConcurrentHashMap<>();
        for (UpstreamClient client : clients) {
            this.clientsById.put(client.id(), client);
        }
        for (UpstreamProperties.UpstreamDef def : properties.getInstances()) {
            if (!clientsById.containsKey(def.getId())) {
                throw new IllegalStateException("配置了不存在的上游: " + def.getId());
            }
            circuits.put(def.getId(), new CircuitState());
        }
    }

    /**
     * 返回当前可参与请求的候选上游，按选中优先级排序（权重高者优先，同权重按配置顺序）。
     * 没有可用上游时返回空列表。
     */
    public List<UpstreamClient> candidates() {
        long now = clock.millis();
        List<WeightedCandidate> healthy = new ArrayList<>();
        List<UpstreamProperties.UpstreamDef> defs = properties.getInstances();
        for (int i = 0; i < defs.size(); i++) {
            UpstreamProperties.UpstreamDef def = defs.get(i);
            CircuitState state = circuits.get(def.getId());
            if (state == null) {
                continue;
            }
            if (state.isOpen()) {
                if (now - state.openedAtMillis >= properties.getCooldownMillis()) {
                    // 冷却结束，允许试探：半开状态先放行一次
                    healthy.add(new WeightedCandidate(def.getId(), def.getWeight(), i));
                }
                continue;
            }
            healthy.add(new WeightedCandidate(def.getId(), def.getWeight(), i));
        }
        // 加权排序：权重降序，权重相同按配置顺序，再加一点随机抖动避免热点
        healthy.sort(Comparator
                .comparingInt(WeightedCandidate::weight).reversed()
                .thenComparingInt(WeightedCandidate::order)
                .thenComparing(c -> ThreadLocalRandom.current().nextInt()));
        List<UpstreamClient> result = new ArrayList<>();
        for (WeightedCandidate candidate : healthy) {
            result.add(clientsById.get(candidate.id()));
        }
        return result;
    }

    public void onSuccess(String upstreamId) {
        CircuitState state = circuits.get(upstreamId);
        if (state != null) {
            state.recordSuccess();
        }
    }

    public void onFailure(String upstreamId, Throwable error) {
        CircuitState state = circuits.get(upstreamId);
        if (state == null) {
            return;
        }
        int failures = state.recordFailure(clock.millis(), properties.getFailureThreshold());
        if (failures >= properties.getFailureThreshold()) {
            log.warn("上游 {} 连续失败 {} 次，摘除冷却 {}ms: {}", upstreamId, failures,
                    properties.getCooldownMillis(), error == null ? "" : error.getMessage());
        }
    }

    public boolean isAvailable(String upstreamId) {
        CircuitState state = circuits.get(upstreamId);
        if (state == null) {
            return false;
        }
        if (!state.isOpen()) {
            return true;
        }
        return clock.millis() - state.openedAtMillis >= properties.getCooldownMillis();
    }

    private record WeightedCandidate(String id, int weight, int order) {
    }

    static final class CircuitState {
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private volatile boolean open;
        private volatile long openedAtMillis;

        synchronized boolean isOpen() {
            return open;
        }

        synchronized void recordSuccess() {
            consecutiveFailures.set(0);
            open = false;
        }

        synchronized int recordFailure(long nowMillis, int threshold) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= threshold) {
                open = true;
                openedAtMillis = nowMillis;
            }
            return failures;
        }
    }
}
