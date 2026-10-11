package com.apitesthub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行引擎线程池（M3 核心，面试高频考点：七参数/执行流程/拒绝策略）。
 *
 * 参数取值理由：
 *  - core=8 / max=16：用例执行是 IO 密集型（发 HTTP 等响应），按 核数≈8 的 1~2 倍起步，
 *    后续以 500 条用例压测的实际表现微调；
 *  - keepAlive=60s：非核心线程空闲 1 分钟回收，闲时省资源；
 *  - 有界队列 200：防止任务堆积把内存打爆（Executors.newFixedThreadPool 的无界队列正是 OOM 元凶）；
 *  - CallerRunsPolicy：测试用例一条都不能丢——队列满时让提交线程亲自执行，
 *    提交速度被自然拖慢（天然背压），而不是抛异常丢任务；
 *  - 命名线程工厂：日志/线程 dump 里能认出 case-exec-*，排查问题不用猜。
 *
 * 为什么不用 Executors 快捷方法：newFixedThreadPool/newSingleThread 用无界队列（OOM 风险），
 * newCachedThreadPool 最大线程数无上限（线程爆炸）——阿里规约要求手动 new ThreadPoolExecutor。
 */
@Configuration
public class ExecutorConfig {

    private final AtomicInteger counter = new AtomicInteger();

    @Bean("caseExecutor")
    public ThreadPoolExecutor caseExecutor() {
        return new ThreadPoolExecutor(
                8, 16, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "case-exec-" + counter.incrementAndGet());
                    t.setDaemon(false);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private final AtomicInteger suiteCounter = new AtomicInteger();

    /**
     * 套件调度池：与用例执行池分离（M4 任务异步化）。
     * 调度线程负责"推进批次/汇总落库"，用例线程负责"发请求跑断言"——
     * 两类工作粒度不同，分池避免互相饥饿（套件调度不会被大量用例任务挤占）。
     */
    @Bean("suiteExecutor")
    public ThreadPoolExecutor suiteExecutor() {
        return new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "suite-exec-" + suiteCounter.incrementAndGet());
                    t.setDaemon(false);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
