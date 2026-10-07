# upstream-api-aggregator-java

Spring Boot 上游接口聚合服务：对外只暴露一个统一接口 `GET /api/aggregate`，背后接入多个上游来源，提供故障转移、版本归一和按调用方限流能力。

## 对外接口

```
GET /api/aggregate?caller=<调用方标识>&q=<查询串>
```

成功响应（200）：

```json
{
  "upstreamId": "alpha",
  "version": "v1",
  "items": [{"id": "1", "name": "apple", "amount": 100, "tags": ["fruit"]}]
}
```

失败响应（`code` 可区分原因，绝不返回空数据或旧数据）：

| HTTP | code | 含义 |
|------|------|------|
| 429 | `RATE_LIMITED` | 调用方超出限流额度，请求未消耗额度 |
| 502 | `ALL_UPSTREAMS_UNAVAILABLE` | 所有上游均不可用，`details` 列出每个上游的失败原因 |
| 500 | `INTERNAL_ERROR` | 未预期错误 |

## 上游选择与降级规则

- 上游实现 `upstream.UpstreamClient` 接口并注册为 Spring bean，即可被选择器自动接入（见 `upstream.DemoUpstreamConfig` 的内存示例，生产可替换为 HTTP 客户端）。
- 每个上游配置权重 `weight`，选择器在健康上游中按权重降序排列候选（同权重按配置顺序），请求从排首的上游开始，失败自动切换下一个。
- 连续失败（超时/报错/返回非法数据）达到 `aggregator.upstream.failure-threshold` 次后，该上游被熔断摘除，不再参与后续请求。
- 熔断冷却 `aggregator.upstream.cooldown-millis` 毫秒后允许一次试探请求，成功即恢复正常参与选择。
- 所有候选都失败时抛出 `AggregationException`，响应 502 并携带每个上游的失败明细；不会挂起、不返回空数据、不透出旧上游的脏数据。

## 版本兼容范围

归一器 `normalize.VersionedNormalizer` 按上游声明的版本选择适配器，归一为统一的 `UnifiedItem(id, name, amount, tags)`：

| 版本 | 结构 | 字段映射 |
|------|------|----------|
| `v1` | `{"items":[...]}` | `id`→id, `title`→name, `price_cents`→amount, `labels`→tags |
| `v2` | `{"data":{"records":[...]}}` | `uid`→id, `displayName`→name, `amount`→amount, `tags`→tags |

- 不认识的版本号：明确拒绝（`UNSUPPORTED_VERSION`），不静默按最新版处理。
- 缺失关键字段或字段类型错误：明确拒绝（`INVALID_PAYLOAD`），并触发故障转移到下一个上游。
- 同一语义的数据无论来自哪个上游/版本，归一结果一致且可复现。

## 限流配置

按调用方（`caller` 参数）维度的令牌桶限流，见 `ratelimit.CallerRateLimiter`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `aggregator.rate-limit.capacity` | 10 | 桶容量（突发上限） |
| `aggregator.rate-limit.refill-per-second` | 5 | 每秒补充的令牌数 |

并发下通过桶级同步保证不超发、不漏计；被拒绝的请求不消耗额度；不同调用方之间额度互相隔离。

## 上游配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `aggregator.upstream.instances[i].id` | - | 上游 id，对应 `UpstreamClient.id()` |
| `aggregator.upstream.instances[i].weight` | 1 | 权重，越大越优先 |
| `aggregator.upstream.failure-threshold` | 3 | 连续失败多少次后摘除 |
| `aggregator.upstream.cooldown-millis` | 30000 | 熔断冷却毫秒数 |

## 本地验证

测试全部使用本地桩上游（`tests/stub/StubUpstreamClient`），不连外网：

```bash
mvn test
```

输出中每个场景/用例均有 `[PASS]` 行说明覆盖内容：

- **故障转移与熔断恢复**（`FailoverTest`）：失败自动切换、连续失败熔断摘除、冷却期后恢复、全部失败返回明确原因。
- **版本归一**（`NormalizationTest`）：v1/v2 归一结果一致且可复现、未知版本拒绝、缺字段拒绝、非法 JSON 拒绝。
- **限流并发边界**（`RateLimitConcurrencyTest`）：200 并发下额度不超发不漏计、被拒请求不耗额度、调用方隔离、令牌补充、上游失败下计数一致。

本地启动并试调用：

```bash
mvn spring-boot:run
curl 'http://localhost:8080/api/aggregate?caller=demo&q=test'
```
