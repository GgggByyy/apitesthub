package com.apitesthub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的分布式锁（M4）——防同一套件被重复触发。
 *
 * 动机（必须会讲）："先查库 status 是否 RUNNING 再决定执行"在并发下有竞态——
 * 两个请求同时查到"没在跑"然后都开跑。锁把"检查+占位"变成一条原子命令。
 *
 * 实现：SET key value NX EX 30（NX=不存在才设置，EX=过期；两者必须一条命令，
 * 分两条会因第一条成功后崩溃没设过期而永久死锁）。
 *
 * 三个已知边界（面试延伸）：
 *  1. 锁过期但任务没跑完 → 别人可再次拿到锁。解法：Redisson 看门狗（默认 30s 锁、
 *     每 10s 自动续期直到任务结束）。当前以"锁 TTL ≥ 任务预期时长"缓解。
 *  2. 误删别人的锁 → value 存随机 UUID，释放用 Lua"比对再删"保证原子。
 *  3. Redis 宕机 → 降级为无锁执行（可用性优先）：记 warn 继续跑，
 *     退化为竞态风险但平台不不可用——与"缓存挂了直查库"同一容错思路。
 */
@Service
public class LockService {

    private static final Logger log = LoggerFactory.getLogger(LockService.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(300);

    /** 比对 value 再删除（Lua 保证"读+判断+删"原子执行） */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public LockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试拿锁。
     * @return 锁句柄（释放用）；Redis 不可用时返回 NO_LOCK（降级放行）
     */
    public String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("Redis unavailable, degrade to lock-free execution: {}", e.getMessage());
            return "NO_LOCK";
        }
    }

    /** 释放锁：Lua 比对 value 防误删；降级句柄直接忽略 */
    public void unlock(String key, String token) {
        if (token == null || "NO_LOCK".equals(token)) {
            return;
        }
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("unlock failed (redis down, lock will expire anyway): {}", e.getMessage());
        }
    }
}
