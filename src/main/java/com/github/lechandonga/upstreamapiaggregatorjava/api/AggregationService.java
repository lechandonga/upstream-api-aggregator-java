package com.github.lechandonga.upstreamapiaggregatorjava.api;

import com.github.lechandonga.upstreamapiaggregatorjava.normalize.NormalizationException;
import com.github.lechandonga.upstreamapiaggregatorjava.normalize.UnifiedItem;
import com.github.lechandonga.upstreamapiaggregatorjava.normalize.VersionedNormalizer;
import com.github.lechandonga.upstreamapiaggregatorjava.ratelimit.CallerRateLimiter;
import com.github.lechandonga.upstreamapiaggregatorjava.upstream.UpstreamClient;
import com.github.lechandonga.upstreamapiaggregatorjava.upstream.UpstreamException;
import com.github.lechandonga.upstreamapiaggregatorjava.upstream.UpstreamSelector;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 聚合服务：限流 -> 选择上游 -> 拉取 -> 归一。
 * 任一上游失败自动切换下一个；全部失败时抛出带明细的 {@link AggregationException}，
 * 绝不返回空数据或旧数据。
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final UpstreamSelector selector;
    private final VersionedNormalizer normalizer;
    private final CallerRateLimiter rateLimiter;

    public AggregationService(UpstreamSelector selector, VersionedNormalizer normalizer,
            CallerRateLimiter rateLimiter) {
        this.selector = selector;
        this.normalizer = normalizer;
        this.rateLimiter = rateLimiter;
    }

    public AggregateResult aggregate(String caller, String query)
            throws AggregationException, RateLimitedException {
        if (!rateLimiter.tryAcquire(caller)) {
            throw new RateLimitedException(caller);
        }
        List<UpstreamClient> candidates = selector.candidates();
        if (candidates.isEmpty()) {
            throw new AggregationException(List.of("没有可用的上游（全部处于熔断冷却期）"));
        }
        List<String> errors = new ArrayList<>();
        for (UpstreamClient client : candidates) {
            try {
                UpstreamClient.RawUpstreamResponse raw = client.fetch(query);
                List<UnifiedItem> items = normalizer.normalize(raw.version(), raw.payload());
                selector.onSuccess(client.id());
                return new AggregateResult(client.id(), raw.version(), items);
            } catch (UpstreamException | NormalizationException e) {
                log.warn("上游 {} 失败，尝试下一个: {}", client.id(), e.getMessage());
                selector.onFailure(client.id(), e);
                errors.add(client.id() + ": " + e.getMessage());
            }
        }
        throw new AggregationException(errors);
    }

    public record AggregateResult(String upstreamId, String version, List<UnifiedItem> items) {
    }
}
