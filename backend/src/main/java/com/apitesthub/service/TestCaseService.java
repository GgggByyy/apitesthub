package com.apitesthub.service;

import com.apitesthub.dto.TestCaseRequest;
import com.apitesthub.engine.Assertion;
import com.apitesthub.entity.TestCase;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.ApiInfoMapper;
import com.apitesthub.mapper.TestCaseMapper;
import com.apitesthub.entity.ApiInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
public class TestCaseService {

    private static final Set<String> TYPES = Set.of("status", "body_jsonpath", "response_time_ms");
    private static final Set<String> OPS = Set.of("eq", "ne", "gt", "lt", "contains");

    private final TestCaseMapper caseMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public TestCaseService(TestCaseMapper caseMapper, ApiInfoMapper apiInfoMapper,
                           ProjectService projectService, ObjectMapper objectMapper) {
        this.caseMapper = caseMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    public TestCase getOwnedCase(Long caseId, Long userId) {
        TestCase testCase = caseMapper.selectById(caseId);
        if (testCase == null) {
            throw new BizException(404, "test case not found");
        }
        projectService.getOwnedProject(testCase.getProjectId(), userId);
        return testCase;
    }

    public List<TestCase> listByProject(Long projectId, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        return caseMapper.selectList(new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProjectId, projectId)
                .orderByAsc(TestCase::getSortOrder).orderByAsc(TestCase::getId));
    }

    public TestCase create(Long projectId, TestCaseRequest req, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        ApiInfo api = apiInfoMapper.selectById(req.getApiId());
        if (api == null || !api.getProjectId().equals(projectId)) {
            throw new BizException(400, "apiId not in this project: " + req.getApiId());
        }
        validateAssertions(req.getAssertions());
        TestCase testCase = new TestCase();
        apply(testCase, req, projectId);
        caseMapper.insert(testCase);
        return testCase;
    }

    public TestCase update(Long caseId, TestCaseRequest req, Long userId) {
        TestCase testCase = getOwnedCase(caseId, userId);
        ApiInfo api = apiInfoMapper.selectById(req.getApiId());
        if (api == null || !api.getProjectId().equals(testCase.getProjectId())) {
            throw new BizException(400, "apiId not in this project: " + req.getApiId());
        }
        validateAssertions(req.getAssertions());
        apply(testCase, req, testCase.getProjectId());
        caseMapper.updateById(testCase);
        return testCase;
    }

    public void delete(Long caseId, Long userId) {
        getOwnedCase(caseId, userId);
        caseMapper.deleteById(caseId);
    }

    private void apply(TestCase testCase, TestCaseRequest req, Long projectId) {
        testCase.setProjectId(projectId);
        testCase.setApiId(req.getApiId());
        testCase.setName(req.getName());
        testCase.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        testCase.setAssertions(req.getAssertions());
        testCase.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
    }

    /** 保存前校验断言结构：可解析 JSON 数组 + 每条 type/op 合法 + jsonpath 必须带 path */
    private void validateAssertions(String assertions) {
        if (assertions == null || assertions.isBlank()) {
            return;
        }
        List<Assertion> list;
        try {
            list = objectMapper.readValue(assertions, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BizException(400, "assertions is not a valid JSON array: " + e.getMessage());
        }
        for (Assertion a : list) {
            if (a.getType() == null || !TYPES.contains(a.getType())) {
                throw new BizException(400, "assertion type must be one of " + TYPES);
            }
            if (a.getOp() == null || !OPS.contains(a.getOp())) {
                throw new BizException(400, "assertion op must be one of " + OPS);
            }
            if (a.getExpect() == null) {
                throw new BizException(400, "assertion expect must not be null");
            }
            if ("body_jsonpath".equals(a.getType()) && (a.getPath() == null || a.getPath().isBlank())) {
                throw new BizException(400, "body_jsonpath assertion requires path");
            }
        }
    }
}
