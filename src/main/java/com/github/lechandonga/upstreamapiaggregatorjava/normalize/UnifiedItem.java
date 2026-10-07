package com.github.lechandonga.upstreamapiaggregatorjava.normalize;

import java.util.List;

/**
 * 对外统一的数据格式。无论来自哪个上游、哪个版本，归一后的结构一致。
 */
public record UnifiedItem(String id, String name, long amount, List<String> tags) {
}
