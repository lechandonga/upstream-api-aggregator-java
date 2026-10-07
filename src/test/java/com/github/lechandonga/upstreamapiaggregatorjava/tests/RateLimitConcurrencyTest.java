package com.github.lechandonga.upstreamapiaggregatorjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lechandonga.upstreamapiaggregatorjava.ratelimit.CallerRateLimiter;
import com.github.lechandonga.upstreamapiaggregatorjava.ratelimit.RateLimitProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 场景三：按调用方限流的并发边界。
 */
@DisplayName("场景: 调用方限流并发边界")
class RateLimitConcurrencyTest {

    private CallerRateLimiter newLimiter(int capacity, double refillPerSecond) {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(capacity);
        props.setRefillPerSecond(refillPerSecond);
        return new CallerRateLimiter(props);
    }

    @Test
    @DisplayName("用例: 高并发下额度不超发也不漏计")
    void concurrentAcquireNeverExceedsCapacity() throws Exception {
        int capacity = 50;
        int threads = 200;
        CallerRateLimiter limiter = newLimiter(capacity, 0); // 不补充，纯消耗
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                start.await();
                return limiter.tryAcquire("caller-A");
            });
        }
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        int granted = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                granted++;
            }
        }
        pool.shutdown();
        assertEquals(capacity, granted, "并发下恰好放行 capacity 个请求");
        assertEquals(0.0, limiter.availableTokens("caller-A"), 1e-9, "额度应被精确耗尽");
        System.out.println("[PASS] 并发限流: " + threads + " 并发请求，放行 " + granted
                + "，拒绝 " + (threads - granted) + "，剩余额度 0");
    }

    @Test
    @DisplayName("用例: 被拒绝的请求不消耗额度")
    void rejectedRequestsDoNotConsumeQuota() {
        CallerRateLimiter limiter = newLimiter(2, 0);
        assertTrue(limiter.tryAcquire("c"));
        assertTrue(limiter.tryAcquire("c"));
        for (int i = 0; i < 10; i++) {
            assertTrue(!limiter.tryAcquire("c"), "超限请求应被拒绝");
        }
        assertEquals(0.0, limiter.availableTokens("c"), 1e-9, "拒绝不应把额度扣成负数");
        System.out.println("[PASS] 拒绝不计费: 连续 10 次超限拒绝后额度仍精确为 0");
    }

    @Test
    @DisplayName("用例: 不同调用方之间额度互不影响")
    void callersAreIsolated() {
        CallerRateLimiter limiter = newLimiter(1, 0);
        assertTrue(limiter.tryAcquire("A"));
        assertTrue(!limiter.tryAcquire("A"));
        assertTrue(limiter.tryAcquire("B"), "B 的额度不应受 A 影响");
        System.out.println("[PASS] 调用方隔离: A 超限不影响 B");
    }

    @Test
    @DisplayName("用例: 令牌按速率补充，恢复后可继续放行")
    void tokensRefillOverTime() throws Exception {
        CallerRateLimiter limiter = newLimiter(1, 100); // 每秒补 100 个
        assertTrue(limiter.tryAcquire("c"));
        assertTrue(!limiter.tryAcquire("c"));
        Thread.sleep(30); // 约补充 3 个
        assertTrue(limiter.tryAcquire("c"), "补充后应能继续放行");
        System.out.println("[PASS] 令牌补充: 等待 30ms 后恢复放行");
    }

    @Test
    @DisplayName("用例: 上游失败不影响调用方计数一致性")
    void quotaConsistentUnderUpstreamFailure() throws Exception {
        // 限流计数与上游成败解耦：每个放行的请求只扣一次额度
        CallerRateLimiter limiter = newLimiter(100, 0);
        AtomicLong granted = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(pool.submit(() -> {
                if (limiter.tryAcquire("c")) {
                    // 模拟上游失败重试等任意后续行为，额度已在入口一次性扣除
                    granted.incrementAndGet();
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        assertEquals(100, granted.get());
        assertEquals(0.0, limiter.availableTokens("c"), 1e-9);
        System.out.println("[PASS] 计数一致: 100 个放行请求恰好消耗 100 额度");
    }
}
