package com.apitesthub.service;

import com.apitesthub.dto.TryResult;
import com.apitesthub.engine.AssertEvaluator;
import com.apitesthub.engine.Assertion;
import com.apitesthub.engine.HttpExecutor;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行引擎 v1（同步）：遍历套件用例 → 逐条发请求 → 逐条断言 → 落库任务与明细。
 * M3 将把"逐条同步"升级为线程池并发 + 变量传递；此处先把"执行单元"的语义跑通。
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

    public ExecutionService(ExecTaskMapper taskMapper, ExecResultMapper resultMapper,
                            ApiInfoMapper apiInfoMapper, SuiteService suiteService,
                            TestCaseService caseService, ProjectService projectService,
                            HttpExecutor httpExecutor, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.suiteService = suiteService;
        this.caseService = caseService;
        this.projectService = projectService;
        this.httpExecutor = httpExecutor;
        this.objectMapper = objectMapper;
    }

    /** 执行套件（同步阻塞直至完成，返回任务号供前端跳转详情） */
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

        int pass = 0;
        int fail = 0;
        long start = System.currentTimeMillis();
        for (Long caseId : caseIds) {
            boolean ok = executeOneCase(task.getId(), caseId, userId);
            if (ok) {
                pass++;
            } else {
                fail++;
            }
        }
        task.setDurationMs(System.currentTimeMillis() - start);
        task.setPassCount(pass);
        task.setFailCount(fail);
        task.setStatus(fail == 0 ? "SUCCESS" : "FAILED");
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    /** 单用例执行单元：发请求 → 断言 → 明细落库。返回是否通过 */
    private boolean executeOneCase(Long taskId, Long caseId, Long userId) {
        TestCase testCase = caseService.getOwnedCase(caseId, userId);
        ExecResult result = new ExecResult();
        result.setTaskId(taskId);
        result.setCaseId(caseId);
        result.setCaseName(testCase.getName());

        try {
            ApiInfo api = apiInfoMapper.selectById(testCase.getApiId());
            if (api == null) {
                throw new BizException(404, "api definition missing: " + testCase.getApiId());
            }
            result.setRequestSnapshot(httpExecutor.buildRequestSnapshot(api));

            TryResult tryResult = httpExecutor.execute(api);
            result.setResponseStatus(tryResult.getStatus());
            result.setResponseBody(tryResult.getBody());
            result.setResponseTimeMs((int) tryResult.getTimeMs());

            List<Assertion> assertions = parseAssertions(testCase.getAssertions());
            String failReason = AssertEvaluator.evaluate(tryResult, assertions);
            result.setPassed(failReason == null ? 1 : 0);
            result.setFailReason(truncate(failReason));
        } catch (Exception e) {
            // 单用例失败不中断整个套件：记录原因继续跑（回归语义：一份报告要看到全部结果）
            result.setPassed(0);
            result.setFailReason(truncate("execution error: " + e.getMessage()));
        }
        resultMapper.insert(result);
        return result.getPassed() == 1;
    }

    /** 任务列表：只能看到自己项目下套件产生的任务（suite → project → owner 三级过滤） */
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
