package com.apitesthub.service;

import com.apitesthub.dto.SuiteRequest;
import com.apitesthub.entity.TestCase;
import com.apitesthub.entity.TestSuite;
import com.apitesthub.exception.BizException;
import com.apitesthub.mapper.TestCaseMapper;
import com.apitesthub.mapper.TestSuiteMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class SuiteService {

    private final TestSuiteMapper suiteMapper;
    private final TestCaseMapper caseMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public SuiteService(TestSuiteMapper suiteMapper, TestCaseMapper caseMapper,
                        ProjectService projectService, ObjectMapper objectMapper) {
        this.suiteMapper = suiteMapper;
        this.caseMapper = caseMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    public TestSuite getOwnedSuite(Long suiteId, Long userId) {
        TestSuite suite = suiteMapper.selectById(suiteId);
        if (suite == null) {
            throw new BizException(404, "suite not found");
        }
        projectService.getOwnedProject(suite.getProjectId(), userId);
        return suite;
    }

    public List<Long> parseCaseIds(TestSuite suite) {
        try {
            return objectMapper.readValue(suite.getCaseIds(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BizException(500, "caseIds corrupted: " + suite.getCaseIds());
        }
    }

    public List<TestSuite> listByProject(Long projectId, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        return suiteMapper.selectList(new LambdaQueryWrapper<TestSuite>()
                .eq(TestSuite::getProjectId, projectId)
                .orderByDesc(TestSuite::getId));
    }

    public TestSuite create(Long projectId, SuiteRequest req, Long userId) {
        projectService.getOwnedProject(projectId, userId);
        validateCaseIds(projectId, req.getCaseIds());
        TestSuite suite = apply(new TestSuite(), req, projectId);
        suiteMapper.insert(suite);
        return suite;
    }

    public TestSuite update(Long suiteId, SuiteRequest req, Long userId) {
        TestSuite suite = getOwnedSuite(suiteId, userId);
        validateCaseIds(suite.getProjectId(), req.getCaseIds());
        apply(suite, req, suite.getProjectId());
        suiteMapper.updateById(suite);
        return suite;
    }

    public void delete(Long suiteId, Long userId) {
        getOwnedSuite(suiteId, userId);
        suiteMapper.deleteById(suiteId);
    }

    private TestSuite apply(TestSuite suite, SuiteRequest req, Long projectId) {
        suite.setProjectId(projectId);
        suite.setName(req.getName());
        try {
            suite.setCaseIds(objectMapper.writeValueAsString(req.getCaseIds()));
        } catch (IOException e) {
            throw new BizException(500, "serialize caseIds failed");
        }
        return suite;
    }

    /** 套件只能引用同项目的用例 */
    private void validateCaseIds(Long projectId, List<Long> caseIds) {
        for (Long caseId : caseIds) {
            TestCase testCase = caseMapper.selectById(caseId);
            if (testCase == null || !testCase.getProjectId().equals(projectId)) {
                throw new BizException(400, "caseId not in this project: " + caseId);
            }
        }
    }
}
