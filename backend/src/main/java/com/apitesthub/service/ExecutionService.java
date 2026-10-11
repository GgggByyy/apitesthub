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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行引擎 v2（M3：批次并行 + 变量体系）。
 *
 * 执行模型：StagePlanner 依赖分析把用例切批——批次内用例提交线程池并行（CountDownLatch 对齐），
 * 批次间串行（前批生产的变量解锁后才放行后批）。链式用例（登录→{{token}}→增删改查）天然按序，
 * 无依赖用例自动并行。
 *
 * 上下文：任务级 VariableContext（ConcurrentHashMap），作为参数在闭包内传递——
 * 不同任务互不可见，同一任务内并发工作线程安全读写。
 */
@Service
public class ExecutionService {

    private final ExecTaskMapper taskMapper;
    private final ExecResultMapper resultMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final SuiteService suiteService;
    private final TestCaseService caseService;
    private final ProjectService projectService;
    private final HttpExecutor httpExecutor;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor caseExecutor;

    public ExecutionService(ExecTaskMapper taskMapper, ExecResultMapper resultMapper,
                            ApiInfoMapper apiInfoMapper, SuiteService suiteService,
                            TestCaseService caseService, ProjectService projectService,
                            HttpExecutor httpExecutor, ObjectMapper objectMapper,
                            @Qualifier("caseExecutor") ThreadPoolExecutor caseExecutor) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.suiteService = suiteService;
        this.caseService = caseService;
        this.projectService = projectService;
        this.httpExecutor = httpExecutor;
        this.objectMapper = objectMapper;
        this.caseExecutor = caseExecutor;
    }

    /** 执行套件：批次并行；全部批次完成后返回任务（外部仍同步等待，M4 引入异步+推送） */
    public ExecTask executeSuite(Long suiteId, Long userId) {
        TestSuite suite = suiteService.getOwnedSuite(suiteId, userId);
        List<Long> caseIds = suiteService.parseCaseIds(suite);

        ExecTask task = new ExecTask();
        task.setSuiteId(suiteId);
        task.setTriggerType("MANUAL");
        task.setStatus("RUNNING");
        task.setTotalCount(caseIds.size());
        task.setPassCount(0);
        task.setFailCount(0);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.insert(task);

        AtomicInteger pass = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        VariableContext ctx = new VariableContext();
        long start = System.currentTimeMillis();

        try {
            List<StagePlanner.CaseWithApi> cases = loadCases(caseIds, userId);
            List<StagePlanner.Stage> stages = StagePlanner.plan(cases);

            for (StagePlanner.Stage stage : stages) {
                runStage(stage, task.getId(), ctx, userId, pass, fail);
            }
        } finally {
            task.setDurationMs(System.currentTimeMillis() - start);
            task.setPassCount(pass.get());
            task.setFailCount(fail.get());
            task.setStatus(fail.get() == 0 ? "SUCCESS" : "FAILED");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        return task;
    }

    /** 执行一个批次：单用例直接跑（省提交开销），多用例提交线程池并行，latch 对齐后进入下一批 */
    private void runStage(StagePlanner.Stage stage, Long taskId, VariableContext ctx,
                          Long userId, AtomicInteger pass, AtomicInteger fail) {
        List<StagePlanner.CaseWithApi> cases = stage.getCases();
        if (cases.size() == 1) {
            if (executeOneCase(taskId, cases.get(0), ctx, userId)) {
                pass.incrementAndGet();
            } else {
                fail.incrementAndGet();
            }
            return;
        }
        CountDownLatch latch = new CountDownLatch(cases.size());
        for (StagePlanner.CaseWithApi c : cases) {
            caseExecutor.submit(() -> {
                try {
                    if (executeOneCase(taskId, c, ctx, userId)) {
                        pass.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
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

    /** 单用例执行单元：渲染变量 → 发请求 → 断言 → 提取变量 → 明细落库 */
    private boolean executeOneCase(Long taskId, StagePlanner.CaseWithApi cwa,
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

            // 断言完成后提取变量（即使断言失败也提取：登录失败时 token 本就不该用）
            if (cwa.rules() != null && !cwa.rules().isEmpty()) {
                ctx.extract(tryResult.getBody(), cwa.rules());
            }
        } catch (Exception e) {
            result.setPassed(0);
            result.setFailReason(truncate("execution error: " + e.getMessage()));
        }
        resultMapper.insert(result);
        return result.getPassed() == 1;
    }

    /** 变量渲染：拷贝接口定义后替换 {{var}}（不污染原始定义），未定义变量抛 400 */
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

    /** 批量加载用例 + 接口定义 + 解析提取规则 */
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

    /** 任务列表：只能看到自己项目下套件产生的任务 */
    public List<ExecTask> listTasks(Long userId) {
        List<Long> suiteIds = new ArrayList<>();
        for (Project project : projectService.list(userId)) {
            suiteService.listByProject(project.getId(), userId)
                    .forEach(s -> suiteIds.add(s.getId()));
        }
        if (suiteIds.isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(new LambdaQueryWrapper<ExecTask>()
                .in(ExecTask::getSuiteId, suiteIds)
                .orderByDesc(ExecTask::getId)
                .last("limit 100"));
    }

    public ExecTask getTask(Long taskId, Long userId) {
        ExecTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "task not found");
        }
        suiteService.getOwnedSuite(task.getSuiteId(), userId);
        return task;
    }

    public List<ExecResult> listResults(Long taskId, Long userId) {
        getTask(taskId, userId);
        return resultMapper.selectList(new LambdaQueryWrapper<ExecResult>()
                .eq(ExecResult::getTaskId, taskId)
                .orderByAsc(ExecResult::getId));
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
