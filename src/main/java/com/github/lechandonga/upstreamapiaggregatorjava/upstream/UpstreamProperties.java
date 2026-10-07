package com.github.lechandonga.upstreamapiaggregatorjava.upstream;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上游接入与熔断配置。
 */
@ConfigurationProperties(prefix = "aggregator.upstream")
public class UpstreamProperties {

    /** 连续失败多少次后摘除该上游。 */
    private int failureThreshold = 3;

    /** 被摘除后冷却多少毫秒再允许试探恢复。 */
    private long cooldownMillis = 30_000;

    private List<UpstreamDef> instances = new ArrayList<>();

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public void setCooldownMillis(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    public List<UpstreamDef> getInstances() {
        return instances;
    }

    public void setInstances(List<UpstreamDef> instances) {
        this.instances = instances;
    }

    public static class UpstreamDef {
        /** 上游 id，需与 Spring bean 名对应。 */
        private String id;
        /** 权重，越大被选中的概率越高；相同权重按配置顺序优先。 */
        private int weight = 1;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }
}
