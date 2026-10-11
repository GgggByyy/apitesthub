package com.apitesthub.service;

import com.apitesthub.dto.TryResult;
import com.apitesthub.engine.AssertEvaluator;
import com.apitesthub.engine.Assertion;
import com.apitesthub.engine.HttpExecutor;
import com.apitesthub.engine.StagePlanner;
import com.apitesthub.engine.VariableContext;
import com.apitesthub.entity.ApiInfo;
import com.apitesthub.entity.ExecResult;
import com.apitesthub.entity.ExecTask;
import com.apitesthub.entity.Project;
import com.apitesthub.entity.TestCase;
import com.apitesthub.entity.TestSuite;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.ApiInfoMapper;
import com.apitesthub.mapper.ExecResultMapper;
import com.apitesthub.mapper.ExecTaskMapper;
import com.apitesthub.websocket.TaskEventBroadcaster;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行引擎 v2.1（M4：任务异步化 + WebSocket 实时推送 + Redis）。
 *
 * executeSuite：拿分布式锁（防同套件重复触发）→ 建 RUNNING 任务 → 提交 suiteExecutor
 * 立即返回 taskId；前端连 WS 订阅任务，每条用例完成即推送日志事件（替代轮询）。
 *
 * Redis 三个用途：
 *  1. INCR task:progress:{id} —— 实时进度计数（天然原子）
 *  2. SET task:status:{id} —— 任务终态缓存 TTL 1h，详情页高频轮询不打数据库
 *  3. lock:suite:{id} —— SETNX 分布式锁（见 LockService）
 * Redis 宕机全线降级：进度不计数、缓存直查库、无锁执行——平台可用性优先。
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecTaskMapper taskMapper;
    private final ExecResultMapper resultMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final SuiteService suiteService;
    private final TestCaseService caseService;
    private final ProjectService projectService;
    private final HttpExecutor httpExecutor;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor caseExecutor;
    private final ThreadPoolExecutor suiteExecutor;
    private final LockService lockService;
    private final StringRedisTemplate redis;
    private final TaskEventBroadcaster broadcaster;

    public ExecutionService(ExecTaskMapper taskMapper, ExecResultMapper resultMapper,
                            ApiInfoMapper apiInfoMapper, SuiteService suiteService,
                            TestCaseService caseService, ProjectService projectService,
                            HttpExecutor httpExecutor, ObjectMapper objectMapper,
                            @Qualifier("caseExecutor") ThreadPoolExecutor caseExecutor,
                            @Qualifier("suiteExecutor") ThreadPoolExecutor suiteExecutor,
                            LockService lockService, StringRedisTemplate redis,
                            TaskEventBroadcaster broadcaster) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.suiteService = suiteService;
        this.caseService = caseService;
        this.projectService = projectService;
        this.httpExecutor = httpExecutor;
        this.objectMapper = objectMapper;
        this.caseExecutor = caseExecutor;
        this.suiteExecutor = suiteExecutor;
        this.lockService = lockService;
        this.redis = redis;
        this.broadcaster = broadcaster;
    }

    /**
     * 触发执行（异步）：锁 → 建任务 → 提交后台 → 立即返回。
     * 重复触发同一套件：拿锁失败返回 409"套件正在执行中"。
     */
    public ExecTask executeSuite(Long suiteId, Long userId) {
        return executeSuite(suiteId, userId, "MANUAL");
    }

    /** triggerType：MANUAL（前端）/ SCHEDULED（Quartz 定时）/ RETRY（失败自动重跑） */
    public ExecTask executeSuite(Long suiteId, Long userId, String triggerType) {
        TestSuite suite = suiteService.getOwnedSuite(suiteId, userId);
        List<Long> caseIds = suiteService.parseCaseIds(suite);

        String lockKey = "lock:suite:" + suiteId;
        String lockToken = lockService.tryLock(lockKey);
        if (lockToken == null) {
            throw new BizException(409, "suite is already running: " + suiteId);
        }

        ExecTask task = new ExecTask();
        task.setSuiteId(suiteId);
        task.setTriggerType(triggerType);
        task.setStatus("RUNNING");
        task.setTotalCount(caseIds.size());
        task.setPassCount(0);
        task.setFailCount(0);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.insert(task);

        Long taskId = task.getId();
        suiteExecutor.submit(() -> {
            try {
                runInternal(taskId, suiteId, caseIds, triggerType, userId, lockKey, lockToken);
            } catch (Exception e) {
                log.error("suite execution crashed: taskId={}", taskId, e);
                finishTask(taskId, 0, 0, 0, "FAILED");
            } finally {
                // 必须在主锁释放之后再触发重跑：runInternal 的 finally 已 unlock，
                // 否则 retry 与主任务自身竞争同一把锁被 skip（实测 1ms 内即撞车）
                maybeRetryFailed(taskId, suiteId, userId, triggerType);
            }
        });
        return task;
    }

    /** 后台执行主体：批次并行 + 每条用例完成即 Redis 计数 + WS 推送 */
    private void runInternal(Long taskId, Long suiteId, List<Long> caseIds, String triggerType,
                             Long userId, String lockKey, String lockToken) {
        AtomicInteger pass = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        VariableContext ctx = new VariableContext();
        long start = System.currentTimeMillis();
        try {
            List<StagePlanner.CaseWithApi> cases = loadCases(caseIds, userId);
            List<StagePlanner.Stage> stages = StagePlanner.plan(cases);

            for (StagePlanner.Stage stage : stages) {
                runStage(stage, taskId, ctx, userId, pass, fail, caseIds.size());
            }
            int duration = (int) (System.currentTimeMillis() - start);
            String status = fail.get() == 0 ? "SUCCESS" : "FAILED";
            finishTask(taskId, duration, pass.get(), fail.get(), status);
        } catch (Exception e) {
            log.error("runInternal failed: taskId={}", taskId, e);
            finishTask(taskId, (int) (System.currentTimeMillis() - start),
                    pass.get(), fail.get(), "FAILED");
        } finally {
            lockService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 失败自动重跑一次（M5）：非 RETRY 任务存在失败用例 → 只重跑失败用例，
     * trigger_type=RETRY 单独记录（报告可区分"首次失败"与"重跑结果"）。
     * 只重跑一层：RETRY 任务自身失败不再触发重跑，防止无限循环掩盖真失败。
     * 调用时机：主任务锁已释放后（见 executeSuite），重跑自己再拿锁防与手动触发撞车。
     */
    private void maybeRetryFailed(Long taskId, Long suiteId, Long userId, String triggerType) {
        if ("RETRY".equals(triggerType)) {
            return;
        }
        List<Long> failedCaseIds = resultMapper.selectList(new LambdaQueryWrapper<ExecResult>()
                        .eq(ExecResult::getTaskId, taskId)
                        .eq(ExecResult::getPassed, 0))
                .stream().map(ExecResult::getCaseId).distinct().toList();
        if (failedCaseIds.isEmpty()) {
            return;
        }
        log.info("task {} has {} failed cases, auto retry once", taskId, failedCaseIds.size());
        suiteExecutor.submit(() -> {
            String lockKey = "lock:suite:" + suiteId;
            String lockToken = lockService.tryLock(lockKey);
            if (lockToken == null) {
                log.info("retry skipped: suite {} is running", suiteId);
                return;
            }
            try {
                ExecTask retryTask = new ExecTask();
                retryTask.setSuiteId(suiteId);
                retryTask.setTriggerType("RETRY");
                retryTask.setStatus("RUNNING");
                retryTask.setTotalCount(failedCaseIds.size());
                retryTask.setStartedAt(LocalDateTime.now());
                taskMapper.insert(retryTask);

                AtomicInteger pass = new AtomicInteger();
                AtomicInteger fail = new AtomicInteger();
                long start = System.currentTimeMillis();
                VariableContext ctx = new VariableContext();
                List<StagePlanner.CaseWithApi> cases = loadCases(failedCaseIds, userId);
                for (StagePlanner.Stage stage : StagePlanner.plan(cases)) {
                    runStage(stage, retryTask.getId(), ctx, userId, pass, fail, failedCaseIds.size());
                }
                finishTask(retryTask.getId(), System.currentTimeMillis() - start,
                        pass.get(), fail.get(), fail.get() == 0 ? "SUCCESS" : "FAILED");
            } catch (Exception e) {
                log.error("retry failed: {}", e.getMessage());
            } finally {
                lockService.unlock(lockKey, lockToken);
            }
        });
    }

    /** 任务收尾：落库终态 + Redis 状态缓存 + finish 事件广播 */
    private void finishTask(Long taskId, long durationMs, int pass, int fail, String status) {
        ExecTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setDurationMs(durationMs);
            task.setPassCount(pass);
            task.setFailCount(fail);
            task.setStatus(status);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "finish");
            event.put("status", status);
            event.put("passCount", pass);
            event.put("failCount", fail);
            event.put("durationMs", durationMs);
            broadcaster.broadcast(taskId, event);

            try {
                Map<String, Object> cached = new LinkedHashMap<>();
                cached.put("status", status);
                cached.put("passCount", pass);
                cached.put("failCount", fail);
                cached.put("totalCount", task.getTotalCount());
                redis.opsForValue().set("task:status:" + taskId,
                        toJson(cached), Duration.ofHours(1));
            } catch (DataAccessException | IllegalStateException e) {
                log.warn("cache task status failed (redis down): {}", e.getMessage());
            }
        }
    }

    /** 执行一个批次：单用例直接跑，多用例提交用例池并行，latch 对齐 */
    private void runStage(StagePlanner.Stage stage, Long taskId, VariableContext ctx,
                          Long userId, AtomicInteger pass, AtomicInteger fail, int total) {
        List<StagePlanner.CaseWithApi> cases = stage.getCases();
        if (cases.size() == 1) {
            executeAndReport(taskId, cases.get(0), ctx, userId, pass, fail, total);
            return;
        }
        CountDownLatch latch = new CountDownLatch(cases.size());
        for (StagePlanner.CaseWithApi c : cases) {
            caseExecutor.submit(() -> {
                try {
                    executeAndReport(taskId, c, ctx, userId, pass, fail, total);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "stage interrupted");
        }
    }

    /** 单用例执行 + Redis 进度计数/实时统计 + WS 日志事件推送 */
    private void executeAndReport(Long taskId, StagePlanner.CaseWithApi cwa,
                                  VariableContext ctx, Long userId,
                                  AtomicInteger pass, AtomicInteger fail, int total) {
        ExecResult result = executeOneCase(taskId, cwa, ctx, userId);
        boolean ok = result.getPassed() == 1;
        if (ok) {
            pass.incrementAndGet();
        } else {
            fail.incrementAndGet();
        }
        int progress = incrProgress(taskId);
        setLive(taskId, pass.get(), fail.get());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "case");
        event.put("caseId", cwa.testCase().getId());
        event.put("caseName", cwa.testCase().getName());
        event.put("passed", ok);
        event.put("timeMs", result.getResponseTimeMs());
        event.put("progress", progress + "/" + total);
        broadcaster.broadcast(taskId, event);
    }

    private int incrProgress(Long taskId) {
        try {
            Long v = redis.opsForValue().increment("task:progress:" + taskId);
            return v == null ? 0 : v.intValue();
        } catch (DataAccessException | IllegalStateException e) {
            return -1; // Redis 宕机：进度不可用，事件里 progress 显示 -1（前端容错）
        }
    }

    /** 实时通过/失败数（列表页 RUNNING 行合并显示；库里的值是执行前的 0） */
    private void setLive(Long taskId, int pass, int fail) {
        try {
            redis.opsForValue().set("task:live:" + taskId, pass + ":" + fail, Duration.ofHours(1));
        } catch (DataAccessException | IllegalStateException ignored) {
            // Redis 宕机：列表显示库值，可用性优先
        }
    }

    /** 单用例执行单元：渲染变量 → 发请求 → 断言 → 提取变量 → 明细落库，返回明细 */
    private ExecResult executeOneCase(Long taskId, StagePlanner.CaseWithApi cwa,
                                      VariableContext ctx, Long userId) {
        TestCase testCase = cwa.testCase();
        ExecResult result = new ExecResult();
        result.setTaskId(taskId);
        result.setCaseId(testCase.getId());
        result.setCaseName(testCase.getName());

        try {
            ApiInfo rendered = renderApi(cwa.api(), ctx);
            result.setRequestSnapshot(httpExecutor.buildRequestSnapshot(rendered));

            TryResult tryResult = httpExecutor.execute(rendered);
            result.setResponseStatus(tryResult.getStatus());
            result.setResponseBody(tryResult.getBody());
            result.setResponseTimeMs((int) tryResult.getTimeMs());

            List<Assertion> assertions = parseAssertions(testCase.getAssertions());
            String failReason = AssertEvaluator.evaluate(tryResult, assertions);
            result.setPassed(failReason == null ? 1 : 0);
            result.setFailReason(truncate(failReason));

            if (cwa.rules() != null && !cwa.rules().isEmpty()) {
                ctx.extract(tryResult.getBody(), cwa.rules());
            }
        } catch (Exception e) {
            result.setPassed(0);
            result.setFailReason(truncate("execution error: " + e.getMessage()));
        }
        resultMapper.insert(result);
        return result;
    }

    private ApiInfo renderApi(ApiInfo api, VariableContext ctx) {
        ApiInfo rendered = new ApiInfo();
        rendered.setId(api.getId());
        rendered.setProjectId(api.getProjectId());
        rendered.setName(api.getName());
        rendered.setMethod(api.getMethod());
        rendered.setUrl(ctx.render(api.getUrl()));
        rendered.setHeaders(api.getHeaders() == null ? null : ctx.render(api.getHeaders()));
        rendered.setParams(api.getParams() == null ? null : ctx.render(api.getParams()));
        rendered.setBody(api.getBody() == null ? null : ctx.render(api.getBody()));
        rendered.setUpdatedAt(api.getUpdatedAt());
        return rendered;
    }

    private List<StagePlanner.CaseWithApi> loadCases(List<Long> caseIds, Long userId) {
        List<StagePlanner.CaseWithApi> list = new ArrayList<>();
        for (Long caseId : caseIds) {
            TestCase testCase = caseService.getOwnedCase(caseId, userId);
            ApiInfo api = apiInfoMapper.selectById(testCase.getApiId());
            if (api == null) {
                throw new BizException(404, "api definition missing: " + testCase.getApiId());
            }
            list.add(new StagePlanner.CaseWithApi(testCase, api, parseRules(testCase.getExtractRules())));
        }
        return list;
    }

    /** 任务列表：RUNNING 任务的实时进度从 Redis 合并（数据库值是执行前的 0） */
    public List<ExecTask> listTasks(Long userId) {
        List<Long> suiteIds = new ArrayList<>();
        for (Project project : projectService.list(userId)) {
            suiteService.listByProject(project.getId(), userId)
                    .forEach(s -> suiteIds.add(s.getId()));
        }
        if (suiteIds.isEmpty()) {
            return List.of();
        }
        List<ExecTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ExecTask>()
                .in(ExecTask::getSuiteId, suiteIds)
                .orderByDesc(ExecTask::getId)
                .last("limit 100"));
        for (ExecTask task : tasks) {
            if ("RUNNING".equals(task.getStatus())) {
                mergeLiveProgress(task);
            }
        }
        return tasks;
    }

    /** RUNNING 任务：从 Redis 拿实时 pass/fail（键不存在或 Redis 挂了则保持库值） */
    private void mergeLiveProgress(ExecTask task) {
        try {
            String progress = redis.opsForValue().get("task:live:" + task.getId());
            if (progress != null) {
                String[] parts = progress.split(":");
                task.setPassCount(Integer.parseInt(parts[0]));
                task.setFailCount(Integer.parseInt(parts[1]));
            }
        } catch (DataAccessException | IllegalStateException ignored) {
            // Redis 不可用：显示库里的值（可能是 0/0），可用性优先
        }
    }

    public ExecTask getTask(Long taskId, Long userId) {
        ExecTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "task not found");
        }
        suiteService.getOwnedSuite(task.getSuiteId(), userId);
        if ("RUNNING".equals(task.getStatus())) {
            mergeLiveProgress(task);
        }
        return task;
    }

    public List<ExecResult> listResults(Long taskId, Long userId) {
        getTask(taskId, userId);
        return resultMapper.selectList(new LambdaQueryWrapper<ExecResult>()
                .eq(ExecResult::getTaskId, taskId)
                .orderByAsc(ExecResult::getId));
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (IOException e) {
            return "{}";
        }
    }

    private List<VariableContext.ExtractRule> parseRules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BizException(400, "extract_rules corrupted: " + json);
        }
    }

    private List<Assertion> parseAssertions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BizException(400, "assertions corrupted: " + json);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 490 ? s.substring(0, 490) + "..." : s;
    }
}
