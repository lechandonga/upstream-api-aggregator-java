package com.github.lechandonga.upstreamapiaggregatorjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.lechandonga.upstreamapiaggregatorjava.normalize.NormalizationException;
import com.github.lechandonga.upstreamapiaggregatorjava.normalize.UnifiedItem;
import com.github.lechandonga.upstreamapiaggregatorjava.normalize.VersionedNormalizer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 场景二：上游版本差异归一。
 */
@DisplayName("场景: 上游版本归一")
class NormalizationTest {

    private final VersionedNormalizer normalizer = new VersionedNormalizer();

    private static final String V1 =
            "{\"items\":[{\"id\":\"1\",\"title\":\"apple\",\"price_cents\":100,\"labels\":[\"fruit\",\"red\"]}]}";
    private static final String V2 =
            "{\"data\":{\"records\":[{\"uid\":\"1\",\"displayName\":\"apple\",\"amount\":100,\"tags\":[\"fruit\",\"red\"]}]}}";

    @Test
    @DisplayName("用例: 同一语义数据在 v1/v2 两个版本下归一结果一致且可复现")
    void sameSemanticDataNormalizesIdentically() throws Exception {
        List<UnifiedItem> fromV1 = normalizer.normalize("v1", V1);
        List<UnifiedItem> fromV2 = normalizer.normalize("v2", V2);
        assertEquals(fromV1, fromV2);
        assertEquals(new UnifiedItem("1", "apple", 100, List.of("fruit", "red")), fromV1.get(0));
        // 可复现：再次归一结果相同
        assertEquals(fromV1, normalizer.normalize("v1", V1));
        System.out.println("[PASS] 版本归一: v1 与 v2 归一结果一致 -> " + fromV1.get(0));
    }

    @Test
    @DisplayName("用例: 不认识的版本明确拒绝，不静默按最新版处理")
    void unknownVersionRejected() {
        NormalizationException ex = assertThrows(NormalizationException.class,
                () -> normalizer.normalize("v99", V1));
        assertEquals(NormalizationException.Reason.UNSUPPORTED_VERSION, ex.reason());
        System.out.println("[PASS] 未知版本拒绝: " + ex.getMessage());
    }

    @Test
    @DisplayName("用例: 缺失关键字段明确拒绝并给出可区分原因")
    void missingRequiredFieldRejected() {
        String broken = "{\"items\":[{\"id\":\"1\",\"price_cents\":100}]}";
        NormalizationException ex = assertThrows(NormalizationException.class,
                () -> normalizer.normalize("v1", broken));
        assertEquals(NormalizationException.Reason.INVALID_PAYLOAD, ex.reason());
        System.out.println("[PASS] 缺字段拒绝: " + ex.getMessage());
    }

    @Test
    @DisplayName("用例: 非法 JSON 明确拒绝")
    void invalidJsonRejected() {
        NormalizationException ex = assertThrows(NormalizationException.class,
                () -> normalizer.normalize("v1", "not-json"));
        assertEquals(NormalizationException.Reason.INVALID_PAYLOAD, ex.reason());
        System.out.println("[PASS] 非法 JSON 拒绝: " + ex.getMessage());
    }
}
