package com.github.lechandonga.upstreamapiaggregatorjava.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 版本归一器：按上游声明的版本选择对应适配器，归一为 {@link UnifiedItem} 列表。
 * 不认识的版本、缺失关键字段都会抛出带可区分原因的 {@link NormalizationException}。
 */
@Component
public class VersionedNormalizer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<UnifiedItem> normalize(String version, String payload) throws NormalizationException {
        JsonNode root = parse(payload);
        return switch (version == null ? "" : version) {
            case "v1" -> normalizeV1(root);
            case "v2" -> normalizeV2(root);
            default -> throw new NormalizationException(
                    NormalizationException.Reason.UNSUPPORTED_VERSION,
                    "不支持的上游版本: " + version);
        };
    }

    private JsonNode parse(String payload) throws NormalizationException {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new NormalizationException(
                    NormalizationException.Reason.INVALID_PAYLOAD, "上游返回不是合法 JSON");
        }
    }

    /**
     * v1 结构: {"items":[{"id":"1","title":"a","price_cents":100,"labels":["x"]}]}
     */
    private List<UnifiedItem> normalizeV1(JsonNode root) throws NormalizationException {
        JsonNode items = required(root, "items", "v1");
        if (!items.isArray()) {
            throw invalid("v1.items 必须是数组");
        }
        List<UnifiedItem> result = new ArrayList<>();
        for (JsonNode node : items) {
            String id = requiredText(node, "id", "v1");
            String name = requiredText(node, "title", "v1");
            JsonNode price = required(node, "price_cents", "v1");
            if (!price.isIntegralNumber()) {
                throw invalid("v1.price_cents 必须是整数");
            }
            result.add(new UnifiedItem(id, name, price.longValue(), textArray(node.get("labels"))));
        }
        return result;
    }

    /**
     * v2 结构: {"data":{"records":[{"uid":"1","displayName":"a","amount":100,"tags":["x"]}]}}
     */
    private List<UnifiedItem> normalizeV2(JsonNode root) throws NormalizationException {
        JsonNode data = required(root, "data", "v2");
        JsonNode records = required(data, "records", "v2");
        if (!records.isArray()) {
            throw invalid("v2.data.records 必须是数组");
        }
        List<UnifiedItem> result = new ArrayList<>();
        for (JsonNode node : records) {
            String id = requiredText(node, "uid", "v2");
            String name = requiredText(node, "displayName", "v2");
            JsonNode amount = required(node, "amount", "v2");
            if (!amount.isIntegralNumber()) {
                throw invalid("v2.amount 必须是整数");
            }
            result.add(new UnifiedItem(id, name, amount.longValue(), textArray(node.get("tags"))));
        }
        return result;
    }

    private JsonNode required(JsonNode node, String field, String version) throws NormalizationException {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            throw invalid(version + " 缺失关键字段: " + field);
        }
        return value;
    }

    private String requiredText(JsonNode node, String field, String version) throws NormalizationException {
        JsonNode value = required(node, field, version);
        if (!value.isTextual()) {
            throw invalid(version + "." + field + " 必须是字符串");
        }
        return value.asText();
    }

    private List<String> textArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    result.add(element.asText());
                }
            }
        }
        return List.copyOf(result);
    }

    private NormalizationException invalid(String message) {
        return new NormalizationException(NormalizationException.Reason.INVALID_PAYLOAD, message);
    }
}
